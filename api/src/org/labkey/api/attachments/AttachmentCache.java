/*
 * Copyright (c) 2005-2011 LabKey Corporation
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

package org.labkey.api.attachments;

import org.labkey.api.data.CacheableWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.ContainerManager.ContainerParent;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: jeckels
 * Date: Nov 18, 2005
 */
public class AttachmentCache
{
    public static final String LOGO_FILE_NAME_PREFIX = "labkey-logo";
    public static final String FAVICON_FILE_NAME = "labkey-favicon.ico";
    public static final String STYLESHEET_FILE_NAME = "labkey-stylesheet.css";

    private static Map<Container, CacheableWriter> _logoCache = new ConcurrentHashMap<Container, CacheableWriter>(100, 0.75f, 4);        // Site + project, so size to one per project
    private static Map<Container, CacheableWriter> _favIconCache = new ConcurrentHashMap<Container, CacheableWriter>(100, 0.75f, 4);     // Site + project, so size to one per project
    private static Map<String, CacheableWriter> _authLogoMap = new ConcurrentHashMap<String, CacheableWriter>(5, 0.75f, 4);           // Site-wide

    public static void clearLogoCache()
    {
        _logoCache.clear();
    }

    public static void clearFavIconCache()
    {
        _favIconCache.clear();
    }

    public static CacheableWriter getCachedLogo(Container c)
    {
        return _logoCache.get(c);
    }

    public static CacheableWriter getCachedFavIcon(Container c)
    {
        return _favIconCache.get(c);
    }

    public static void cacheLogo(Container c, CacheableWriter logo)
    {
        _logoCache.put(c, logo);
    }

    public static void cacheFavIcon(Container c, CacheableWriter favIcon)
    {
        _favIconCache.put(c, favIcon);
    }

    public static Attachment lookupFavIconAttachment(AttachmentParent parent) throws SQLException
    {
        return lookupAttachment(parent, FAVICON_FILE_NAME);
    }

    public static Attachment lookupLogoAttachment(Container c) throws SQLException
    {
        AttachmentParent parent = new ContainerParent(c);
        Collection<Attachment> attachments = AttachmentService.get().getAttachments(parent);
        for (Attachment attachment : attachments)
        {
            if (attachment.getName().startsWith(LOGO_FILE_NAME_PREFIX))
            {
                return attachment;
            }
        }
        return null;
    }

    // We don't cache custom stylesheets -- CoreController caches them in encoded form
    public static Attachment lookupCustomStylesheetAttachment(AttachmentParent parent)
    {
        return lookupAttachment(parent, STYLESHEET_FILE_NAME);
    }

    public static Attachment lookupAttachment(AttachmentParent parent, String name)
    {
        return AttachmentService.get().getAttachment(parent, name);
    }


    public static void cacheAuthLogo(String name, CacheableWriter writer)
    {
        _authLogoMap.put(name, writer);
    }


    public static CacheableWriter getAuthLogo(String name)
    {
        return _authLogoMap.get(name);
    }


    public static void clearAuthLogoCache()
    {
        _authLogoMap.clear();
    }
}
