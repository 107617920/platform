/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.resource;

import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Filter;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: kevink
 * Date: Mar 12, 2010 2:43:56 PM
 */
public abstract class AbstractResourceCollection extends AbstractResource
{
    protected AbstractResourceCollection(Path path)
    {
        super(path);
    }

    protected AbstractResourceCollection(Path parent, String name)
    {
        super(parent, name);
    }

    public abstract Resource parent();

    public boolean isCollection()
    {
        return exists();
    }

    public boolean isFile()
    {
        return false;
    }

    public InputStream getInputStream() throws IOException
    {
        throw new IllegalStateException("Can't read from a collection");
    }

    public long getContentLength()
    {
        return 0;
    }

    public Collection<? extends Resource> list()
    {
        Collection<String> names = listNames();
        Collection<Resource> list = new ArrayList<Resource>(names.size());
        for (String name : names)
        {
            Resource r = find(name);
            if (r != null)
                list.add(r);
        }
        return list;
    }
}
