/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.reports.ReportService;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.SQLException;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:33:52 PM
 */
public class ReportImporter
{
    void process(ImportContext ctx, File root) throws IOException, SQLException, StudyImporter.StudyImportException
    {
        StudyDocument.Study.Reports reportsXml = ctx.getStudyXml().getReports();

        if (null != reportsXml)
        {
            File reportsDir = StudyImporter.getStudyDir(root, reportsXml.getDir(), "Study.xml");

            File[] reportsFiles = reportsDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name)
                {
                    return name.endsWith(".report.xml");
                }
            });

            for (File reportFile : reportsFiles)
            {
                ReportService.get().importReport(ctx.getUser(), ctx.getContainer(), reportFile);
            }
        }
    }
}
