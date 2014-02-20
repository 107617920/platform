/*
 * Copyright (c) 2005-2014 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * SafeFileAppender class
 * <p/>
 * Created: Oct 18, 2005
 *
 * @author bmaclean
 */
public class SafeFileAppender extends AppenderSkeleton
{
    private static Logger _log = Logger.getLogger(SafeFileAppender.class);

    private File _file;

    public SafeFileAppender(File file)
    {
        this._file = file;

        // Make sure that we try to mount the drive (if needed) before using the file
        NetworkDrive.exists(_file);
    }

    public void append(LoggingEvent loggingEvent)
    {
        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter(new FileWriter(_file, true));
            writer.write(getLayout().format(loggingEvent));
            String[] exceptionStrings = loggingEvent.getThrowableStrRep();
            if (exceptionStrings != null)
            {
                for (String exceptionString : exceptionStrings)
                {
                    writer.write(exceptionString);
                    writer.write(Layout.LINE_SEP);
                }
            }
        }
        catch (IOException e)
        {
            File parentFile = _file.getParentFile();
            if (parentFile != null && !NetworkDrive.exists(parentFile) && parentFile.mkdirs())
                append(loggingEvent);
            else
                _log.error("Failed appending to file.", e);
        }
        finally
        {
            if (writer != null)
            {
                try
                {
                    writer.close();
                }
                catch (IOException e)
                {
                }
            }
        }
    }

    public void close()
    {
        // Nothing to do, since nothing stays open.
    }

    public boolean requiresLayout()
    {
        return true;
    }
}
