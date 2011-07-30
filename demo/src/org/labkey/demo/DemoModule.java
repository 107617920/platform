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

package org.labkey.demo;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.demo.model.DemoManager;
import org.labkey.demo.model.Person;
import org.labkey.demo.view.DemoWebPart;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;


public class DemoModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(DefaultModule.class);

    public String getName()
    {
        return "Demo";
    }

    public double getVersion()
    {
        return 11.19;
    }

    protected void init()
    {
        addController("demo", DemoController.class);
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(new BaseWebPartFactory("Demo Summary") {
                public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                {
                    return new DemoWebPart();
                }
            },
            new BaseWebPartFactory("Demo Summary", WebPartFactory.LOCATION_RIGHT) {
                {
                    addLegacyNames("Narrow Demo Summary");
                }

                public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                {
                    return new DemoWebPart();
                }
            }));
    }

    public boolean hasScripts()
    {
        return true;
    }

    public Collection<String> getSummary(Container c)
    {
        try
        {
            Person[] people = DemoManager.getInstance().getPeople(c);
            if (people != null && people.length > 0)
            {
                Collection<String> list = new LinkedList<String>();
                list.add("Demo Module: " + people.length + " person records.");
                return list;
            }
        }
        catch (SQLException e)
        {
            _log.error("Failure checking for demo data in container " + c.getPath(), e);
        }
        return Collections.emptyList();
    }

    public void startup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new DemoContainerListener());
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(DemoSchema.getInstance().getSchema());
    }


    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(DemoSchema.getInstance().getSchemaName());
    }
}