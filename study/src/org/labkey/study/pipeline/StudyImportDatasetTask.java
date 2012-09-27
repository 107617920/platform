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

package org.labkey.study.pipeline;

import org.labkey.api.admin.ImportException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.DatasetImporter;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.importer.StudyJobSupport;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;

/*
* User: adam
* Date: Sep 1, 2009
* Time: 3:17:44 PM
*/

// This task is used to import datasets as part of study import/reload.  StudyImportJob is the associcated pipeline job.
public class StudyImportDatasetTask extends AbstractDatasetImportTask<StudyImportDatasetTask.Factory>
{
    private StudyImportDatasetTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    protected String getDatasetsFileName() throws ImportException
    {
        StudyJobSupport support = getJob().getJobSupport(StudyJobSupport.class);
        StudyImportContext ctx = support.getImportContext();

        return StudyImportDatasetTask.getDatasetsFileName(ctx);
    }

    @Override
    protected VirtualFile getDatasetsDirectory() throws ImportException
    {
        StudyJobSupport support = getJob().getJobSupport(StudyJobSupport.class);
        StudyImportContext ctx = support.getImportContext();
        VirtualFile root = support.getRoot();

        return StudyImportDatasetTask.getDatasetsDirectory(ctx, root);
    }

    public static String getDatasetsFileName(StudyImportContext ctx) throws ImportException
    {
        StudyDocument.Study.Datasets datasetsXml = ctx.getXml().getDatasets();

        if (null != datasetsXml)
            return datasetsXml.getDefinition().getFile();

        return null;
    }

    public static VirtualFile getDatasetsDirectory(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return DatasetImporter.getDatasetDirectory(ctx, root);
    }

    public StudyImpl getStudy()
    {
        return getJob().getJobSupport(StudyJobSupport.class).getStudy();
    }


    public static class Factory extends AbstractDatasetImportTaskFactory<Factory>
    {
        public Factory()
        {
            super(StudyImportDatasetTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyImportDatasetTask(this, job);
        }
    }
}