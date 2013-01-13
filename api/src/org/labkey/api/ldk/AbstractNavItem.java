/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.ldk;

import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 9:33 AM
 */
abstract public class AbstractNavItem implements NavItem
{
    protected String _ownerKey;

    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = new JSONObject();
        ret.put("name", getName());
        ret.put("label", getLabel());
        ret.put("category", getCategory());
        ret.put("renderer", getRendererName());
        ret.put("visible", isVisible(c, u));
        ret.put("providerName", getDataProvider() == null ? null : getDataProvider().getName());
        ret.put("key", getPropertyManagerKey());
        ret.put("ownerKey", getOwnerKey());

        return ret;
    }

    protected JSONObject getUrlObject(ActionURL url)
    {
        JSONObject json = new JSONObject();
        if (url != null)
        {
            json.put("url", url);
            json.put("controller", url.getController());
            json.put("action", url.getAction());
            json.put("params", url.getParameterMap());
        }
        return json;
    }

    public boolean isVisible(Container c, User u)
    {
        Container targetContainer = c.isWorkbook() ? c.getParent() : c;
        if (getDataProvider() != null && getDataProvider().getOwningModule() != null)
        {
            if (!targetContainer.getActiveModules().contains(getDataProvider().getOwningModule()))
                return false;
        }

        Map<String, String> map = new CaseInsensitiveHashMap(PropertyManager.getProperties(targetContainer, NavItem.PROPERTY_CATEGORY));
        if (map.containsKey(getPropertyManagerKey()))
            return Boolean.parseBoolean(map.get(getPropertyManagerKey()));

        return getDefaultVisibility(targetContainer, u);
    }

    public String getPropertyManagerKey()
    {
        return getDataProvider().getKey() + "||" + getCategory() + "||" + getName() + "||" + getLabel();
    }

    public static String inferDataProviderNameFromKey(String key)
    {
        String[] tokens = key.split("\\|\\|");
        return tokens[0] + "||" + tokens[1] + "||" + tokens[2];
    }

    public String getOwnerKey()
    {
        return _ownerKey;
    }

    public void setOwnerKey(String ownerKey)
    {
        _ownerKey = ownerKey;
    }
}
