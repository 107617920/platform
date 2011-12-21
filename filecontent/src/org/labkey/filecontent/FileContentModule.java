/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.filecontent;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavService;
import org.labkey.filecontent.message.FileContentDigestProvider;
import org.labkey.filecontent.message.FileEmailConfig;
import org.labkey.filecontent.message.ShortMessageDigest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;


public class FileContentModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(FileContentModule.class);

    public String getName()
    {
        return "FileContent";
    }

    public double getVersion()
    {
        return 11.30;
    }

    protected void init()
    {
        addController("filecontent", FileContentController.class);
        PropertyService.get().registerDomainKind(new FilePropertiesDomainKind());
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(
                new FilesWebPart.Factory(WebPartFactory.LOCATION_RIGHT),
                new FilesWebPart.Factory(WebPartFactory.LOCATION_BODY))); 
    }

    public boolean hasScripts()
    {
        return true;
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    public void startup(ModuleContext moduleContext)
    {
        WebdavService.get().addProvider(new FileWebdavProvider());
        ServiceRegistry.get().registerService(FileContentService.class, new FileContentServiceImpl());

        // initialize message digests
        ShortMessageDigest.getInstance().initializeTimer();
        ShortMessageDigest.getInstance().addProvider(new FileContentDigestProvider(FileEmailConfig.SHORT_DIGEST));
        DailyMessageDigest.getInstance().addProvider(new FileContentDigestProvider(FileEmailConfig.DAILY_DIGEST));

        // initialize message config provider
        MessageConfigService.getInstance().registerConfigType(new FileEmailConfig());
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(FileRootManager.getFileContentSchema());
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(FileRootManager.FILECONTENT_SCHEMA_NAME);
    }
}