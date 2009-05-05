/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.core.attachment;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.util.ResultSetUtil;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Fed 22, 2008
 * Time: 8:57:17 PM
 */
public class DatabaseAttachmentFile implements AttachmentFile
{
    private Attachment _attachment;
    private String _contentType;
    private int _fileSize;
    private ResultSet _rs = null;
    private InputStream _is = null;

    private static CoreSchema core = CoreSchema.getInstance();
    private static final String _sqlDocumentTypeAndSize = "SELECT DocumentType, DocumentSize FROM " + core.getTableInfoDocuments() + " WHERE Parent = ? AND DocumentName = ?";
    private static final String _sqlDocument = "SELECT Document FROM " + core.getTableInfoDocuments() + " WHERE Parent = ? AND DocumentName = ?";

    public DatabaseAttachmentFile(Attachment attachment) throws SQLException, IOException
    {
        _attachment = attachment;
        ResultSet rs = null;

        try
        {
            rs = Table.executeQuery(core.getSchema(), _sqlDocumentTypeAndSize, new Object[]{attachment.getParent(), attachment.getName()});

            if (!rs.next())
                throw new IllegalStateException("Attachment could not be retrieved from database");

            setContentType(rs.getString("DocumentType"));

            int size = rs.getInt("DocumentSize");
            if (size > 0)
                setSize(size);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    public String getContentType()
    {
        return _contentType;
    }

    public void setContentType(String string)
    {
        _contentType = string;
    }

    public long getSize()
    {
        return _fileSize;
    }

    private void setSize(int fileSize)
    {
        _fileSize = fileSize;
    }

    public byte[] getBytes() throws IOException
    {
        throw new UnsupportedOperationException();
    }

    public String getError()
    {
        return null;
    }

    public String getFilename()
    {
        return _attachment.getName();
    }

    public void setFilename(String filename)
    {
        _attachment.setName(filename);
    }

    // NOTE: ResultSet is left open to allow streaming attachment contents from the database.  closeInputStream() must be called when through.
    public InputStream openInputStream() throws IOException
    {
        if (!(_rs == null && _is == null))
            throw new IllegalStateException("InputStream has already been opened");

        try
        {
            _rs = Table.executeQuery(core.getSchema(), _sqlDocument, new Object[]{_attachment.getParent(), _attachment.getName()}, 0, false);

            if (!_rs.next())
                throw new IllegalStateException("Attachment could not be retrieved from database");

            _is = _rs.getBinaryStream("document");
            return _is;
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (null == _is)
                _rs = ResultSetUtil.close(_rs);
        }
    }

    public void closeInputStream() throws IOException
    {
        IOUtils.closeQuietly(_is);
        _is = null;
        _rs = ResultSetUtil.close(_rs);
    }
}