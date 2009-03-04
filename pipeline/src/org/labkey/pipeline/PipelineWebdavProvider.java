/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.pipeline;

import org.labkey.api.webdav.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.security.User;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.net.URI;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 21, 2008
 * Time: 1:16:17 PM
 */
public class PipelineWebdavProvider implements WebdavService.Provider
{
    static final String PIPELINE_LINK = "@pipeline";

    // currently addChildren is called only for web folders
    @Nullable
    public Set<String> addChildren(@NotNull WebdavResolver.Resource target)
    {
        if (!(target instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) target;
        Container c = folder.getContainer();
        
        PipeRoot root = PipelineService.get().findPipelineRoot(c);
        return null != root ? PageFlowUtil.set(PIPELINE_LINK) : null;
    }

    public WebdavResolver.Resource resolve(@NotNull WebdavResolver.Resource parent, @NotNull String name)
    {
        if (!PIPELINE_LINK.equalsIgnoreCase(name))
            return null;
        if (!(parent instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) parent;
        Container c = folder.getContainer();
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
        if (null == root)
            return null;
        return new PipelineFolderResource(c, root);
    }

    private class PipelineFolderResource extends FileSystemResource
    {
        Container c;

        PipelineFolderResource(Container c, PipeRoot root)
        {
            super(c.getPath(), PIPELINE_LINK);

            this.c = c;
            URI uriRoot = (root != null) ? root.getUri(c) : null;
            if (uriRoot != null)
            {
                _acl = org.labkey.api.security.SecurityManager.getACL(c, root.getEntityId());
                _file = FileUtil.canonicalFile(uriRoot);
            }
        }

        @Override
        protected boolean hasAccess(User user)
        {
            return user.isAdministrator() || c.getAcl().getPermissions(user) != 0;
        }

        @Override
        public boolean canList(User user)
        {
            return hasAccess(user);
        }

        @Override
        public String getName()
        {
            return PIPELINE_LINK;
        }

        public FileSystemResource find(String name)
        {
            if (_file != null)
                return new FileSystemResource(this, name);
            return null;
        }
    }
}
