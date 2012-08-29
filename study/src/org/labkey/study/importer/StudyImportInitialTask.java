/*
 * Copyright (c) 2009-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.importer;

import org.labkey.api.pipeline.*;
import org.labkey.api.admin.ImportException;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.util.Collections;
import java.util.List;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public class StudyImportInitialTask extends PipelineJob.Task<StudyImportInitialTask.Factory>
{
    private StudyImportInitialTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    private static final int DELAY_INCREMENT = 10;

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        StudyJobSupport support = job.getJobSupport(StudyJobSupport.class);
        StudyImportContext ctx = support.getImportContext();

        doImport(job, ctx, support.getSpringErrors(), support.getOriginalFilename());

        return new RecordedActionSet();
    }

    public static void doImport(PipelineJob job, StudyImportContext ctx, BindException errors, String originalFileName) throws PipelineJobException
    {
        try
        {
            StudyDocument.Study studyXml = ctx.getXml();

            // Check if a delay has been requested for testing purposes, to make it easier to cancel the job in a reliable way
            if (studyXml.isSetImportDelay() && studyXml.getImportDelay() > 0)
            {
                for (int i = 0; i < studyXml.getImportDelay(); i = i + DELAY_INCREMENT)
                {
                    job.setStatus("Delaying import, waited " + i + " out of "+ studyXml.getImportDelay() + " second delay");
                    try
                    {
                        Thread.sleep(1000 * DELAY_INCREMENT);
                    }
                    catch (InterruptedException e) {}
                }
            }
            
            StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer());

            // Create the study if it doesn't exist... otherwise, modify the existing properties
            if (null == study)
            {
                job.info("Loading study from " + originalFileName);
                job.info("Creating study");

                // Create study
                StudyController.StudyPropertiesForm studyForm = new StudyController.StudyPropertiesForm();

                if (studyXml.isSetLabel())
                    studyForm.setLabel(studyXml.getLabel());

                if (studyXml.isSetTimepointType())
                    studyForm.setTimepointType(TimepointType.valueOf(studyXml.getTimepointType().toString()));
                else if (studyXml.isSetDateBased())
                    studyForm.setTimepointType(studyXml.getDateBased() ? TimepointType.DATE : TimepointType.VISIT);

                if (studyXml.isSetStartDate())
                    studyForm.setStartDate(studyXml.getStartDate().getTime());

                if (studyXml.isSetDefaultTimepointDuration())
                    studyForm.setDefaultTimepointDuration(studyXml.getDefaultTimepointDuration());

                if (studyXml.isSetSecurityType())
                    studyForm.setSecurityType(SecurityType.valueOf(studyXml.getSecurityType().toString()));

                if (studyXml.getSubjectColumnName() != null)
                    studyForm.setSubjectColumnName(studyXml.getSubjectColumnName());

                if (studyXml.getSubjectNounSingular() != null)
                    studyForm.setSubjectNounSingular(studyXml.getSubjectNounSingular());

                if (studyXml.getSubjectNounPlural() != null)
                    studyForm.setSubjectNounPlural(studyXml.getSubjectNounPlural());

                if (studyXml.getDescription() != null)
                    studyForm.setDescription(studyXml.getDescription());
                // Issue 15789: Carriage returns in protocol description are not round-tripped
                else if (studyXml.getStudyDescription() != null && studyXml.getStudyDescription().getDescription() != null)
                    studyForm.setDescription(studyXml.getStudyDescription().getDescription());
                
                if (studyXml.getDescriptionRendererType() != null)
                    studyForm.setDescriptionRendererType(studyXml.getDescriptionRendererType());
                // Issue 15789: Carriage returns in protocol description are not round-tripped
                else if (studyXml.getStudyDescription() != null && studyXml.getStudyDescription().getRendererType() != null)
                    studyForm.setDescriptionRendererType(studyXml.getStudyDescription().getRendererType());

                if (studyXml.getInvestigator() != null)
                    studyForm.setInvestigator(studyXml.getInvestigator());

                if (studyXml.getGrant() != null)
                    studyForm.setGrant(studyXml.getGrant());

                if (studyXml.getAlternateIdPrefix() != null)
                    studyForm.setAlternateIdPrefix(studyXml.getAlternateIdPrefix());

                if (studyXml.isSetAlternateIdDigits())
                    studyForm.setAlternateIdDigits(studyXml.getAlternateIdDigits());

                StudyController.createStudy(study, ctx.getContainer(), ctx.getUser(), studyForm);
            }
            else
            {
                job.info("Reloading study from " + originalFileName);
                job.info("Loading top-level study properties");

                TimepointType timepointType = study.getTimepointType();
                if (studyXml.isSetTimepointType())
                    timepointType = TimepointType.valueOf(studyXml.getTimepointType().toString());
                else if (studyXml.isSetDateBased())
                    timepointType = studyXml.getDateBased() ? TimepointType.DATE : TimepointType.VISIT;

                if (study.getTimepointType() != timepointType)
                    throw new PipelineJobException("Can't change timepoint style from '" + study.getTimepointType() + "' to '" + timepointType + "' when reloading an existing study.");

                // TODO: Change these props and save only if values have changed
                study = study.createMutable();

                if (studyXml.isSetLabel())
                    study.setLabel(studyXml.getLabel());

                if (studyXml.isSetStartDate())
                    study.setStartDate(studyXml.getStartDate().getTime());

                if (studyXml.isSetSecurityType())
                    study.setSecurityType(SecurityType.valueOf(studyXml.getSecurityType().toString()));

                if (studyXml.getSubjectColumnName() != null)
                    study.setSubjectColumnName(studyXml.getSubjectColumnName());

                if (studyXml.getSubjectNounSingular() != null)
                    study.setSubjectNounSingular(studyXml.getSubjectNounSingular());

                if (studyXml.getSubjectNounPlural() != null)
                    study.setSubjectNounPlural(studyXml.getSubjectNounPlural());

                StudyManager.getInstance().updateStudy(ctx.getUser(), study);
            }

            VirtualFile vf = ctx.getRoot();

            new MissingValueImporterFactory().create().process(job, ctx, vf);
            new QcStatesImporter().process(ctx, vf, errors);

            new VisitImporter().process(ctx, vf, errors);
            if (errors.hasErrors())
                throwFirstErrorAsPiplineJobException(errors);

            new DatasetImporter().process(ctx, vf, errors);
            if (errors.hasErrors())
                throwFirstErrorAsPiplineJobException(errors);
        }
        catch (CancelledException e)
        {
            // Let this through without wrapping
            throw e;
        }
        catch (Throwable t)
        {
            throw new PipelineJobException(t) {};
        }
    }

    private static void throwFirstErrorAsPiplineJobException(BindException errors) throws PipelineJobException
    {
        ObjectError firstError = (ObjectError)errors.getAllErrors().get(0);
        throw new PipelineJobException("ERROR: " + firstError.getDefaultMessage());
    }


    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(StudyImportInitialTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyImportInitialTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "LOAD STUDY";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
