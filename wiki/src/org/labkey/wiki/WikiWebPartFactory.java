/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.wiki;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.view.*;
import org.labkey.wiki.model.WikiWebPart;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 10:50:37 AM
 */
public class WikiWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public WikiWebPartFactory()
    {
        this(WikiModule.WEB_PART_NAME, null);
    }

    public WikiWebPartFactory(String name, @Nullable String location)
    {
        super(name, location, true, false);
        addLegacyNames("Narrow Wiki");
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        Map<String, String> props = webPart.getPropertyMap();
        return new WikiWebPart(webPart.getRowId(), props);
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new WikiController.CustomizeWikiPartView(webPart);
    }

    @Override
    public Map<String, String> serializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> serializedPropertyMap = new HashMap<String, String>(propertyMap);

        // for the webPartContainer property, use the container path instead of container id
        // omit the container path in the common case where a wiki webpart is pointed at a wiki in its own container
        if (serializedPropertyMap.containsKey("webPartContainer"))
        {
            Container webPartContainer = ContainerManager.getForId(serializedPropertyMap.get("webPartContainer"));
            if (null != webPartContainer && !webPartContainer.equals(ctx.getContainer()))
            {
                serializedPropertyMap.put("webPartContainer", webPartContainer.getPath());
            }
            else
            {
                serializedPropertyMap.remove("webPartContainer");
            }
        }

        return serializedPropertyMap;
    }

    @Override
    public Map<String, String> deserializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> deserializedPropertyMap = new HashMap<String, String>(propertyMap);

        // for the webPartContainer property, try to get the container ID from the specified path
        // if a container does not exist for the given path, use the current container ID
        if (deserializedPropertyMap.size() > 0)
        {
            String containerId = ctx.getContainer().getId();
            if (deserializedPropertyMap.containsKey("webPartContainer"))
            {
                Container webPartContainer = ContainerManager.getForPath(deserializedPropertyMap.get("webPartContainer"));
                if (null != webPartContainer)
                {
                    containerId = webPartContainer.getId();
                }
            }
            deserializedPropertyMap.put("webPartContainer", containerId);
        }

        return deserializedPropertyMap;
    }
}
