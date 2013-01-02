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
package org.labkey.api.writer;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.module.SafeFlushResponseWrapper;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * User: adam
 * Date: Apr 27, 2009
 * Time: 5:29:58 PM
 */
public class ZipFile extends AbstractVirtualFile
{
    private final ZipOutputStream _out;
    private final String _path;
    private final PrintWriter _pw;
    private final boolean _shouldCloseOutputStream;

    public ZipFile(File root, String name) throws FileNotFoundException
    {
        this(getOutputStream(root, name), true);
    }

    public ZipFile(HttpServletResponse response, String name) throws IOException
    {
        this(getOutputStream(response, name), true);
    }

    public ZipFile(OutputStream out, boolean shouldCloseOutputStream)
    {
        this(new ZipOutputStream(out), null, "", shouldCloseOutputStream);
    }

    private ZipFile(ZipOutputStream out, PrintWriter pw, String path, boolean shouldCloseOutputStream)
    {
        _out = out;
        _pw = null != pw ? pw : new NonCloseablePrintWriter(out);
        _path = path;
        _shouldCloseOutputStream = shouldCloseOutputStream;
    }

    private static OutputStream getOutputStream(File root, String name) throws FileNotFoundException
    {
        // Make sure directory exists, is writeable
        FileSystemFile.ensureWriteableDirectory(root);
        File zipFile = new File(root, _makeLegalName(name));
        FileOutputStream fos = new FileOutputStream(zipFile);

        return new BufferedOutputStream(fos);
    }

    private static OutputStream getOutputStream(HttpServletResponse response, String name) throws IOException
    {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + _makeLegalName(name) + "\";");

        return response.getOutputStream();
    }

    public String getLocation()
    {
        return "ZipFile stream.";
    }

    public PrintWriter getPrintWriter(String path) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(path));
        _out.putNextEntry(entry);

        return _pw;
    }

    public OutputStream getOutputStream(String path) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(path));
        _out.putNextEntry(entry);

        return new NonCloseableZipOutputStream(_out);
    }

    public void saveXmlBean(String filename, XmlObject doc) throws IOException
    {
        try
        {
            XmlBeansUtil.validateXmlDocument(doc, filename);
        }
        catch (XmlValidationException e)
        {
            throw new RuntimeException(e);
        }

        saveXmlBean(filename, doc, XmlBeansUtil.getDefaultSaveOptions());
    }

    // Expose this if/when some caller needs to customize the options
    private void saveXmlBean(String filename, XmlObject doc, XmlOptions options) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(filename));
        _out.putNextEntry(entry);
        doc.save(_out, options);
        _out.closeEntry();
    }

    public VirtualFile getDir(String path)
    {
        return new ZipFile(_out, _pw, _path + makeLegalName(path) + "/", false);
    }

    public VirtualFile createZipArchive(String name) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(name));
        _out.putNextEntry(entry);

        return new ZipFile(_out, false);
    }

    public String makeLegalName(String name)
    {
        return _makeLegalName(name);
    }

    private static String _makeLegalName(String name)
    {
        return FileSystemFile.makeLegal(name);
    }

    public void close() throws IOException
    {
        _out.finish();            

        if (_shouldCloseOutputStream)
        {
            _out.close();
        }
    }

    private static class NonCloseablePrintWriter extends PrintWriter
    {
        private final ZipOutputStream _out;

        private NonCloseablePrintWriter(ZipOutputStream out)
        {
            super(out);
            _out = out;
        }

        @Override
        public void close()
        {
            try
            {
                flush();
                _out.closeEntry();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private static class NonCloseableZipOutputStream extends SafeFlushResponseWrapper.OutputStreamWrapper
    {
        private final ZipOutputStream _out;

        private NonCloseableZipOutputStream(ZipOutputStream out)
        {
           super(out);
            _out = out;
        }

        public void close()
        {
            try
            {
                flush();
                _out.closeEntry();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public XmlObject getXmlBean(String filename) throws IOException
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public InputStream getInputStream(String filename) throws IOException
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public String getRelativePath(String filename)
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public String[] list()
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public String[] listDirs()
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public boolean delete(String filename)
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }
}
