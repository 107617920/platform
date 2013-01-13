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
import org.labkey.api.data.Container;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.security.User;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 8:44 AM
 */

/**
 * Experimental.  This describes
 */
public interface NavItem
{
    public static final String PROPERTY_CATEGORY = "ldk.navItem";

    public DataProvider getDataProvider();

    public String getName();

    public String getLabel();

    public String getCategory();

    public String getRendererName();

    public boolean isVisible(Container c, User u);

    public boolean getDefaultVisibility(Container c, User u);

    public JSONObject toJSON(Container c, User u);

    public String getPropertyManagerKey();

    public static enum Category
    {
        samples(),
        misc(),
        settings(),
        reports(),
        data();

        Category()
        {

        }
    }
}
