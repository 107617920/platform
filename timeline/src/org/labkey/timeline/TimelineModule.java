/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

package org.labkey.timeline;

import org.apache.commons.beanutils.BeanUtils;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.*;
import org.labkey.timeline.view.TimelineView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class TimelineModule extends DefaultModule
{
    public static final String NAME = "Timeline";

    public String getName()
    {
        return "Timeline";
    }

    public double getVersion()
    {
        return 10.39;
    }

    protected void init()
    {
        addController("timeline", TimelineController.class);
    }

    public void startup(ModuleContext moduleContext)
    {
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(new BaseWebPartFactory(NAME, null, true, true){

            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
            {
                TimelineSettings settings = new TimelineSettings();
                BeanUtils.populate(settings, webPart.getPropertyMap());
                settings.setDivId("TimelineWebPart." + webPart.getIndex());
                return new TimelineView(settings);
            }

            public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
            {
                return new JspView<Portal.WebPart>(TimelineView.class, "customizeTimeline.jsp", webPart);
            }
        }));
    }

    public boolean hasScripts()
    {
        return false;
    }
}