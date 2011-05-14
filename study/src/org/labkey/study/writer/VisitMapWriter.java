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
package org.labkey.study.writer;

import org.labkey.api.study.StudyImportException;
import org.labkey.api.study.TimepointType;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;

import java.io.IOException;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:52:38 AM
 */
class VisitMapWriter implements InternalStudyWriter
{
    public String getSelectionText()
    {
        return "Visit Map";
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws IOException, StudyImportException, SQLException
    {
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
            return;

        if (ctx.useOldFormats())
        {
            DataFaxVisitMapWriter writer = new DataFaxVisitMapWriter();
            writer.write(study, ctx, vf);
        }
        else
        {
            XmlVisitMapWriter writer = new XmlVisitMapWriter();
            writer.write(study, ctx, vf);
        }
    }
}
