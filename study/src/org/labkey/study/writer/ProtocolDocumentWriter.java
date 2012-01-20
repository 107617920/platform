/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.ExportDirType;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 27, 2011
 * Time: 1:14:06 PM
 */
public class ProtocolDocumentWriter implements InternalStudyWriter
{
    public static final String DATA_TYPE = "Protocol Documents";
    public static final String DOCUMENT_FOLDER = "protocolDocs";

    @Override
    public String getSelectionText()
    {
        return DATA_TYPE;
    }

    @Override
    public void write(@NotNull StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        List<Attachment> documents = study.getProtocolDocuments();

        if (!documents.isEmpty())
        {
            VirtualFile folder = vf.getDir(DOCUMENT_FOLDER);
            AttachmentParent parent = new StudyImpl.ProtocolDocumentAttachmentParent(study);

            ExportDirType protocolXml = ctx.getXml().addNewProtocolDocs();
            protocolXml.setDir(DOCUMENT_FOLDER);

            for (Attachment doc : documents)
            {
                InputStream is = null;
                OutputStream os = null;

                try
                {
                    is = AttachmentService.get().getInputStream(parent, doc.getName());
                    os = folder.getOutputStream(doc.getName());
                    FileUtil.copyData(is, os);
                }
                finally
                {
                    IOUtils.closeQuietly(is);
                    IOUtils.closeQuietly(os);
                }
            }
        }
    }
}
