/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
import org.labkey.api.study.StudyImportException;
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

    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            PipelineJob job = getJob();
            StudyJobSupport support = job.getJobSupport(StudyJobSupport.class);
            ImportContext ctx = support.getImportContext();
            StudyDocument.Study studyXml = ctx.getStudyXml();
            StudyImpl study = support.getStudy(true);

            // Create the study if it doesn't exist... otherwise, modify the existing properties
            if (null == study)
            {
                job.info("Loading study from " + support.getOriginalFilename());
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

                StudyController.createStudy(support.getStudy(true), ctx.getContainer(), ctx.getUser(), studyForm);
            }
            else
            {
                job.info("Reloading study from " + support.getOriginalFilename());
                job.info("Loading top-level study properties");

                TimepointType timepointType = study.getTimepointType();
                if (studyXml.isSetTimepointType())
                    timepointType = TimepointType.valueOf(studyXml.getTimepointType().toString());
                else if (studyXml.isSetDateBased())
                    timepointType = studyXml.getDateBased() ? TimepointType.DATE : TimepointType.VISIT;

                if (study.getTimepointType() != timepointType)
                    throw new StudyImportException("Can't change timepoint style from '" + study.getTimepointType() + "' to '" + timepointType + "' when reloading an existing study.");

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

            VirtualFile vf = new FileSystemFile(support.getRoot());

            new MissingValueImporter().process(support.getStudy(), ctx, vf, support.getSpringErrors());
            new QcStatesImporter().process(support.getStudy(), ctx, vf, support.getSpringErrors());

            new VisitImporter().process(support.getStudy(), ctx, vf, support.getSpringErrors());
            if (support.getSpringErrors().hasErrors())
                throwFirstErrorAsPiplineJobException(support.getSpringErrors());

            new DatasetImporter().process(support.getStudy(), ctx, vf, support.getSpringErrors());
            if (support.getSpringErrors().hasErrors())
                throwFirstErrorAsPiplineJobException(support.getSpringErrors());
        }
        catch (Throwable t)
        {
            throw new PipelineJobException(t) {};
        }

        return new RecordedActionSet();
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