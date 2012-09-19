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

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.pipeline.*;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.writer.StudySerializationRegistryImpl;
import org.springframework.validation.BindException;

import java.util.*;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public class StudyImportFinalTask extends PipelineJob.Task<StudyImportFinalTask.Factory>
{
    private StudyImportFinalTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        StudyJobSupport support = job.getJobSupport(StudyJobSupport.class);

        doImport(job, support.getImportContext(), support.getSpringErrors());

        return new RecordedActionSet();
    }

    public static void doImport(PipelineJob job, StudyImportContext ctx, BindException errors) throws PipelineJobException
    {
        try
        {
            // TODO: Pull these from the study serialization registry?
            Collection<InternalStudyImporter> internalImporters = new LinkedList<InternalStudyImporter>();

            // Dataset and Specimen upload jobs delete "unused" participants, so we need to defer setting participant
            // cohorts until the end of upload.
            internalImporters.add(new CohortImporter());

            // Can't assign visits to cohorts until the cohorts are created
            internalImporters.add(new VisitCohortAssigner());

            // Can't assign datasets to cohorts until the cohorts are created
            internalImporters.add(new DatasetCohortAssigner());
            internalImporters.add(new ParticipantCommentImporter());
            internalImporters.add(new ParticipantGroupImporter());
            internalImporters.add(new ProtocolDocumentImporter());
            internalImporters.add(new ViewCategoryImporter());

            VirtualFile vf = ctx.getRoot();
            for (InternalStudyImporter importer : internalImporters)
            {
                if (job != null)
                    job.setStatus("IMPORT " + importer.getDescription());
                ctx.getLogger().info("Importing " + importer.getDescription());
                importer.process(ctx, vf, errors);
                ctx.getLogger().info("Done importing " + importer.getDescription());
            }

            // the registered study importers only need to be called in the Import Study case (not for Import Folder)
            if (job != null && job instanceof StudyImportJob)
            {
                Collection<FolderImporter> externalStudyImporters = StudySerializationRegistryImpl.get().getRegisteredStudyImporters();
                for (FolderImporter importer : externalStudyImporters)
                {
                    importer.process(job, ctx, vf);
                }
                try
                {
                    // Retrieve userid for queries being validated through the pipeline (study import).
                    QueryService.get().setEnvironment(QueryService.Environment.USERID, null==job.getUser() ? User.guest.getUserId() : job.getUser().getUserId());

                    List<PipelineJobWarning> warnings = new ArrayList<PipelineJobWarning>();
                    for (FolderImporter importer : externalStudyImporters)
                    {
                        job.setStatus("POST-PROCESS " + importer.getDescription()); 
                        Collection<PipelineJobWarning> importerWarnings = importer.postProcess(ctx, vf);
                        if (null != importerWarnings)
                            warnings.addAll(importerWarnings);
                    }
                    //TODO: capture warnings in the pipeline job and make a distinction between success & success with warnings
                    //for now, just fail the job if there were any warnings. The warnings will
                    //have already been written to the log
                    if (warnings.size() > 0)
                        job.error("Warnings were generated by the study importers!");
                }
                finally
                {
                    QueryService.get().clearEnvironment();
                }
            }
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }
    }

    
    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(StudyImportFinalTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyImportFinalTask(this, job);
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
            return "STUDY IMPORT";    // TODO: RELOAD?
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}