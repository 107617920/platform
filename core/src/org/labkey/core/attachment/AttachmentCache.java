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

package org.labkey.core.attachment;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * User: adam
 * Date: Nov 18, 2010
 * Time: 7:37:49 PM
 */
public class AttachmentCache
{
    private static final StringKeyCache<Map<String, Attachment>> _cache = CacheManager.getStringKeyCache(10000, CacheManager.DAY, "Attachments");

    private static final CacheLoader<String, Map<String, Attachment>> LOADER = new CacheLoader<String, Map<String, Attachment>>() {
        @Override
        public Map<String, Attachment> load(String key, Object attachmentParent)
        {
            AttachmentParent parent = (AttachmentParent)attachmentParent;

            Collection<Attachment> attachments = new TableSelector(CoreSchema.getInstance().getTableInfoDocuments(),
                    AttachmentServiceImpl.ATTACHMENT_COLUMNS,
                    new SimpleFilter("Parent", parent.getEntityId()),
                    new Sort("+RowId")).getCollection(Attachment.class);

            Map<String, Attachment> map = new LinkedHashMap<String, Attachment>(attachments.size());

            for (Attachment attachment : attachments)
                map.put(attachment.getName(), attachment);

            return Collections.unmodifiableMap(map);
        }
    };


    static @NotNull Map<String, Attachment> getAttachments(AttachmentParent parent)
    {
        return _cache.get(getKey(parent), parent, LOADER);
    }


    static void removeAttachments(AttachmentParent parent)
    {
        _cache.remove(getKey(parent));
    }


    static void removeAttachments(Container c)
    {
        _cache.removeUsingPrefix(c.getId());
    }


    private static String getKey(AttachmentParent parent)
    {
        return parent.getContainerId() + ":" + parent.getEntityId();
    }
}
