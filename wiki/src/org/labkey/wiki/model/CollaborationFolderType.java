/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.wiki.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.wiki.WikiTOCFactory;
import org.labkey.wiki.WikiWebPartFactory;

import java.util.Arrays;

/**
 * User: Mark Igra
 * Date: Aug 4, 2006
 * Time: 3:41:26 PM
 */
public class CollaborationFolderType extends DefaultFolderType
{
    public static final String TYPE_NAME = "Collaboration";

    public CollaborationFolderType()
    {
        super(TYPE_NAME,
                "Build a web site for publishing and exchanging information. " +
                        "Your tools include Message Boards, Issue Trackers and Wikis. Share information within your own group, across groups or with the public by configuring user permissions.",
              null,
              Arrays.asList(new WikiWebPartFactory().createWebPart(),
                      new WikiTOCFactory().createWebPart(),
                      Portal.getPortalPart("Messages").createWebPart()),
                      getDefaultModuleSet(), null);
    }

    @Override
    public String getStartPageLabel(ViewContext ctx)
    {
       Container c = ctx.getContainer();
        if (c.equals(ContainerManager.getHomeContainer()))
            return LookAndFeelProperties.getInstance(c).getShortName();
        else
            return c.getName();
    }
}
