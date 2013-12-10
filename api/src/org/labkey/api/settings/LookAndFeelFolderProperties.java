/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.api.settings;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;

/**
 * User: adam
 * Date: Aug 1, 2008
 * Time: 9:35:40 PM
 */
public class LookAndFeelFolderProperties extends AbstractWriteableSettingsGroup
{
    static final String LOOK_AND_FEEL_SET_NAME = "LookAndFeel";

    protected static final String DEFAULT_DATE_FORMAT = "defaultDateFormatString";
    protected static final String DEFAULT_NUMBER_FORMAT = "defaultNumberFormatString";

    protected final Container _c;

    protected LookAndFeelFolderProperties(Container c)
    {
        _c = c;
    }

    protected String getType()
    {
        return "look and feel settings";
    }

    protected String getGroupName()
    {
        return LOOK_AND_FEEL_SET_NAME;
    }

    @Override
    protected String lookupStringValue(String name, @Nullable String defaultValue)
    {
        throw new IllegalStateException("Must provide a container");
    }

    protected String lookupStringValue(Container c, String name, @Nullable String defaultValue)
    {
        if (c.isRoot())
            return super.lookupStringValue(c, name, defaultValue);

        String value = super.lookupStringValue(c, name, null);

        if (null == value)
            value = lookupStringValue(c.getParent(), name, defaultValue);

        return value;
    }

    public String getDefaultDateFormat()
    {
        // Look up this value starting from the current container (unlike all the other look & feel settings)
        return lookupStringValue(_c, DEFAULT_DATE_FORMAT, null);
    }

    public String getDefaultNumberFormat()
    {
        // Look up this value starting from the current container (unlike all the other look & feel settings)
        return lookupStringValue(_c, DEFAULT_NUMBER_FORMAT, null);
    }
}