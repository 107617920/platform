/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.data.StringWrapperDynaClass;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.apache.commons.beanutils.DynaBean;

import java.util.Map;

public class QueryWrapperDynaClass extends StringWrapperDynaClass
{
    QueryUpdateForm _form;
    public QueryWrapperDynaClass(QueryUpdateForm form)
    {
        _form = form;

        Map<String, Class> propMap = new CaseInsensitiveHashMap<Class>();
        for (ColumnInfo column : _form.getTable().getColumns())
        {
            propMap.put(_form.getFormFieldName(column), column.getJavaClass());
        }

        init("className", propMap);

    }

    

    public DynaBean newInstance() throws IllegalAccessException, InstantiationException
    {
        throw new UnsupportedOperationException();
    }

    public String getPropertyCaption(String propName)
    {
        ColumnInfo column = _form.getColumnByFormFieldName(propName);
        if (column == null)
            return propName;
        return column.getCaption();
    }
}
