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

package org.labkey.search.model;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.DocIdBitSet;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/*
* User: adam
* Date: Dec 16, 2009
* Time: 1:36:39 PM
*/
class SecurityFilter extends Filter
{
    private static final ContainerFieldSelector CONTAINER_FIELD_SELECTOR = new ContainerFieldSelector();

    private final User user;
    private final HashMap<String, Container> containerIds;
    private final HashMap<String, Boolean> securableResourceIds = new HashMap<String,Boolean>();

    SecurityFilter(User user, Container searchRoot, Container currentContainer, boolean recursive)
    {
        this.user = user;

        if (recursive)
        {
            Set<Container> containers = ContainerManager.getAllChildren(searchRoot, user);
            containerIds = new HashMap<String, Container>(containers.size());

            for (Container c : containers)
            {
                if ((c.isSearchable() || (c.equals(currentContainer)) && (c.shouldDisplay(user) || c.isWorkbook())))
                    containerIds.put(c.getId(), c);
            }
        }
        else
        {
            containerIds = new HashMap<String, Container>();
            containerIds.put(searchRoot.getId(), searchRoot);
        }
    }


    @Override
    public DocIdSet getDocIdSet(IndexReader reader) throws IOException
    {
        int max = reader.maxDoc();
        BitSet bits = new BitSet(max);

        for (int i = 0; i < max; i++)
        {
            Document doc = reader.document(i, CONTAINER_FIELD_SELECTOR);

            String id = doc.get(LuceneSearchServiceImpl.FIELD_NAMES.container.name());
            String resourceId = doc.get(LuceneSearchServiceImpl.FIELD_NAMES.resourceId.name());

            if (null == id || !containerIds.containsKey(id))
                continue;
            
            if (null != resourceId && !resourceId.equals(id))
            {
                if (!containerIds.containsKey(resourceId))
                {
                    Boolean canRead = securableResourceIds.get(resourceId);
                    if (null == canRead)
                    {
                        SecurableResource sr = new _SecurableResource(resourceId, containerIds.get(id));
                        SecurityPolicy p = SecurityPolicyManager.getPolicy(sr);
                        canRead = p.hasPermission(user, ReadPermission.class);
                        securableResourceIds.put(resourceId, canRead);
                    }
                    if (!canRead.booleanValue())
                        continue;
                }
            }
            
            bits.set(i);
        }

        return new DocIdBitSet(bits);
    }


    private static class ContainerFieldSelector implements FieldSelector
    {
        public FieldSelectorResult accept(String fieldName)
        {
            if (LuceneSearchServiceImpl.FIELD_NAMES.container.name().equals(fieldName))
                return FieldSelectorResult.LOAD;
            if (LuceneSearchServiceImpl.FIELD_NAMES.resourceId.name().equals(fieldName))
                return FieldSelectorResult.LOAD;
            return FieldSelectorResult.NO_LOAD;
        }
    }


    static class _SecurableResource implements SecurableResource
    {
        final String _id;
        final Container _container;
        
        _SecurableResource(String resourceId, Container c)
        {
            _id = resourceId;
            _container = c;
        }

        @NotNull
        public String getResourceId()
        {
            return _id;
        }

        @NotNull
        public String getResourceName()
        {
            return _id;
        }

        @NotNull
        public String getResourceDescription()
        {
            return "";
        }

        @NotNull
        public Set<Class<? extends Permission>> getRelevantPermissions()
        {
            throw new UnsupportedOperationException();
        }

        @NotNull
        public Module getSourceModule()
        {
            throw new UnsupportedOperationException();
        }

        public SecurableResource getParentResource()
        {
            return null;
        }

        @NotNull
        public Container getResourceContainer()
        {
            return _container;
        }

        @NotNull
        public List<SecurableResource> getChildResources(User user)
        {
            throw new UnsupportedOperationException();
        }

        public boolean mayInheritPolicy()
        {
            return false;
        }
    }

}
