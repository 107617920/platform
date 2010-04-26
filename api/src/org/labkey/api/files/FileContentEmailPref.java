/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.files;

import org.labkey.api.notification.EmailPref;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Apr 23, 2010
 * Time: 11:55:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class FileContentEmailPref extends EmailPref
{
    public static final int FOLDER_DEFAULT = -1;
    public static final int NONE = 0;
    public static final int INDIVIDUAL = 1;
    public static final int DAILY_DIGEST = 2;

    @Override
    public String getId()
    {
        return "FileContentEmailPref";
    }

    @Override
    public String getDefaultValue()
    {
        return String.valueOf(FOLDER_DEFAULT);
    }

    @Override
    public String getValue(String value, String defaultValue)
    {
        if (getDefaultValue().equals(value))
            return defaultValue;
        else
            return value;
    }
}
