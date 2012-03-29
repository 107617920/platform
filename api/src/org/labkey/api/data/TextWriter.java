/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.labkey.api.util.ExceptionUtil;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/*
* User: adam
* Date: Dec 30, 2010
* Time: 9:20:44 AM
*/
public abstract class TextWriter
{
    private static final Logger LOG = Logger.getLogger(TextWriter.class);

    // Stashing the OutputStream and the PrintWriter allows multiple writes (e.g., Export Runs to TSV)
    // TODO: Would be nice to remove this
    private ServletOutputStream _outputStream = null;
    protected PrintWriter _pw = null;
    private boolean _exportAsWebPage = false;

    protected abstract String getFilename();
    protected abstract void write();

    /**
     * Override to return a different content type
     * @return The content type
     */
    protected String getContentType()
    {
        return "text/plain";
    }

    public void setPrintWriter(PrintWriter pw)
    {
        _pw = pw;
    }


    public PrintWriter getPrintWriter()
    {
        return _pw;
    }


    public boolean isExportAsWebPage()
    {
        return _exportAsWebPage;
    }

    public void setExportAsWebPage(boolean exportAsWebPage)
    {
        _exportAsWebPage = exportAsWebPage;
    }

    // Prepare the writer to write to the file system
    public void prepare(File file) throws IOException
    {
        _pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
    }

    public void prepare(StringBuilder builder)
    {
        _pw = new PrintWriter(new StringBuilderWriter(builder));
    }

    // Prepare the writer to stream a file to the browser
    public void prepare(HttpServletResponse response) throws IOException
    {
        // NOTE: reset() ALSO CLEAR HEADERS! such as cache pragmas
        boolean noindex = response.containsHeader("X-Robots-Tag");

        // Flush any extraneous output (e.g., <CR><LF> from JSPs)
        response.reset();

        if (noindex)
            response.setHeader("X-Robots-Tag", "noindex");

        // Specify attachment and foil caching
        if (_exportAsWebPage)
        {
           response.setHeader("Content-disposition", "inline; filename=\"" + getFilename() + "\"");
        }
        else
        {
            // Set the content-type so the browser knows which application to launch
            response.setContentType(getContentType());
            response.setHeader("Content-disposition", "attachment; filename=\"" + getFilename() + "\"");
        }

        // Get the outputstream of the servlet (BTW, always get the outputstream AFTER you've
        // set the content-disposition and content-type)
        _outputStream = response.getOutputStream();
        _pw = new PrintWriter(_outputStream);
    }

    // Create a file and stream it to the browser.
    public void write(HttpServletResponse response) throws IOException
    {
        try
        {
            prepare(response);
            write();
        }
        finally
        {
            close();
        }
    }

    // Write a newly created file to the file system.
    public void write(File file) throws IOException
    {
        prepare(file);
        write();
        close();
    }

    // Write a newly created file to the PrintWriter.
    public void write(PrintWriter pw) throws IOException
    {
        _pw = pw;
        write();
        close();
    }

    // Write content to a string builder.
    public void write(StringBuilder builder) throws IOException
    {
        prepare(builder);
        write();
        close();
    }

    public void close() throws IOException
    {
        if (_pw == null)
        {
            return;
        }

        _pw.close();

        if (_pw.checkError())
            LOG.error("PrintWriter error");

        if (null == _outputStream)
            return;

        try
        {
            // Flush the outputstream
            _outputStream.flush();
            // Finally, close the outputstream
            _outputStream.close();
        }
        catch(IOException e)
        {
            if (!ExceptionUtil.isClientAbortException(e))
            {
                throw e;
            }
        }
    }
}
