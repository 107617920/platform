/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.query;

import org.labkey.api.data.Container;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: kevink
 * Date: Jun 21, 2010
 * Time: 1:17:33 PM
 */
public class ModuleCustomViewInfo implements CustomViewInfo
{
    protected ModuleCustomViewDef _customViewDef;

    public ModuleCustomViewInfo(ModuleCustomViewDef customViewDef)
    {
        _customViewDef = customViewDef;
    }

    @Override
    public String getName()
    {
        return _customViewDef.getName();
    }

    @Override
    public User getOwner()
    {
        //module-based reports have no owner
        return null;
    }

    @Override
    public boolean isShared()
    {
        return true;
    }

    @Override
    public User getCreatedBy()
    {
        return null;
    }

    @Override
    public Date getModified()
    {
        return _customViewDef.getLastModified();
    }

    @Override
    public String getSchemaName()
    {
        return _customViewDef.getSchema();
    }

    @Override
    public String getQueryName()
    {
        return _customViewDef.getQuery();
    }

    @Override
    public Container getContainer()
    {
        //module-based reports have no explicit container
        return null;
    }

    @Override
    public boolean canInherit()
    {
        return false;
    }

    @Override
    public boolean isHidden()
    {
        return _customViewDef.isHidden();
    }

    @Override
    public boolean isEditable()
    {
        //module custom views are not updatable
        return false;
    }

    @Override
    public boolean isSession()
    {
        // module custom views are never in session
        return false;
    }

    @Override
    public String getCustomIconUrl()
    {
        return _customViewDef.getCustomIconUrl();
    }

    @Override
    public List<FieldKey> getColumns()
    {
        List<FieldKey> ret = new ArrayList<FieldKey>();
        for(Map.Entry<FieldKey, Map<CustomView.ColumnProperty,String>> entry : getColumnProperties())
        {
            ret.add(entry.getKey());
        }
        return ret;
    }

    @Override
    public List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty,String>>> getColumnProperties()
    {
        return _customViewDef.getColList();
    }

    @Override
    public String getFilterAndSort()
    {
        return _customViewDef.getFilterAndSortString();
    }

    @Override
    public String getContainerFilterName()
    {
        if (null == _customViewDef.getFilters())
            return null;

        for(Pair<String, String> filter : _customViewDef.getFilters())
        {
            if (filter.first.startsWith("containerFilterName~"))
                return filter.second;
        }

        return null;
    }


    @Override
    public boolean hasFilterOrSort()
    {
        return (null != _customViewDef.getFilters() || null != _customViewDef.getSorts());
    }

}
