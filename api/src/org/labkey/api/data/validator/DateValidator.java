/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.data.validator;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.DateUtil;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Validate date is within min/max range on PostgreSQL.
 */
public class DateValidator extends AbstractColumnValidator
{
    // This is the SQLServer DATETIME range, Postgres supports a wide range, as do SQLServer DATETIME2 fields
    // https://msdn.microsoft.com/en-us/library/ms187819.aspx
    static final long MIN_TIMESTAMP =  DateUtil.parseISODateTime("1753-01-01");
    static final long MAX_TIMESTAMP = DateUtil.parseISODateTime("9999-12-31") + TimeUnit.DAYS.toMillis(1);

    public DateValidator(String columnName)
    {
        super(columnName);
    }

    @Override
    public String _validate(int rowNum, Object value)
    {
        if (!(value instanceof java.util.Date))
            return null;
        long t = ((java.util.Date)value).getTime();
        if (t >= MIN_TIMESTAMP && t < MAX_TIMESTAMP)
            return null;
        return "Only dates between January 1, 1753 and December 31, 9999 are accepted.";
    }
}
