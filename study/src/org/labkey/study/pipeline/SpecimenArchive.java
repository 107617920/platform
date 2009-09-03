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

package org.labkey.study.pipeline;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.io.IOException;
import java.io.File;

/*
* User: adam
* Date: Sep 1, 2009
* Time: 2:32:53 PM
*/
public class SpecimenArchive
{
    private final File _definitionFile;

    public SpecimenArchive(File definitionFile)
    {
        _definitionFile = definitionFile;
    }

    public File getDefinitionFile()
    {
        return _definitionFile;
    }

    // Move to ZipUtil?
    public List<EntryDescription> getEntryDescriptions() throws IOException
    {
        List<SpecimenArchive.EntryDescription> entryList = new ArrayList<EntryDescription>();
        ZipFile zip = null;
        try
        {
            zip = new ZipFile(_definitionFile);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory())
                    continue;
                entryList.add(new SpecimenArchive.EntryDescription(entry.getName(), entry.getSize(), new Date(entry.getTime())));
            }
        }
        finally
        {
            if (zip != null) try { zip.close(); } catch (IOException e) {}
        }
        return entryList;
    }

    public static class EntryDescription
    {
        private final String _name;
        private final long _size;
        private final Date _date;

        public EntryDescription(String name, long size, Date date)
        {
            _name = name;
            _size = size;
            _date = date;
        }

        public Date getDate()
        {
            return _date;
        }

        public String getName()
        {
            return _name;
        }

        public long getSize()
        {
            return _size;
        }
    }
}
