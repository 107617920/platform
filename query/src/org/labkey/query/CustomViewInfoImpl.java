/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.query;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.QueryManager;

import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 18, 2009
 */
public class CustomViewInfoImpl implements CustomViewInfo
{
    protected static final String FILTER_PARAM_PREFIX = "filter";
    protected static final String CONTAINER_FILTER_NAME = "containerFilterName";
    protected final QueryManager _mgr = QueryManager.get();
    protected CstmView _cstmView;

    public CustomViewInfoImpl(CstmView view)
    {
        _cstmView = view;
    }

    public String getName()
    {
        return null == _cstmView ? null : _cstmView.getName();
    }

    public User getOwner()
    {
        Integer userId = _cstmView.getCustomViewOwner();
        if (userId == null)
            return null;
        return UserManager.getUser(userId.intValue());
    }

    public User getCreatedBy()
    {
        return UserManager.getUser(_cstmView.getCreatedBy());
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_cstmView.getContainerId());
    }

    public List<FieldKey> getColumns()
    {
        List<FieldKey> ret = new ArrayList<FieldKey>();
        for (Map.Entry<FieldKey, Map<ColumnProperty, String>> entry : getColumnProperties())
        {
            ret.add(entry.getKey());
        }
        return ret;
    }

    static List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> decodeProperties(String value)
    {
        if (value == null)
        {
            return Collections.emptyList();
        }
        String[] values = StringUtils.split(value, "&");
        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> ret = new ArrayList<Map.Entry<FieldKey, Map<ColumnProperty, String>>>();
        for (String entry : values)
        {
            int ichEquals = entry.indexOf("=");
            Map<ColumnProperty,String> properties;
            FieldKey field;
            if (ichEquals < 0)
            {
                field = FieldKey.fromString(PageFlowUtil.decode(entry));
                properties = Collections.emptyMap();
            }
            else
            {
                properties = new EnumMap<ColumnProperty,String>(ColumnProperty.class);
                field = FieldKey.fromString(PageFlowUtil.decode(entry.substring(0, ichEquals)));
                for (Map.Entry<String, String> e : PageFlowUtil.fromQueryString(PageFlowUtil.decode(entry.substring(ichEquals + 1))))
                {
                    properties.put(ColumnProperty.valueOf(e.getKey()), e.getValue());
                }

            }
            ret.add(Pair.of(field, properties));
        }
        return Collections.unmodifiableList(ret);
    }

    public List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> getColumnProperties()
    {
        return decodeProperties(_cstmView.getColumns());
    }

    public String getFilterAndSort()
    {
        return _cstmView.getFilter();
    }

    public boolean canInherit()
    {
        return _mgr.canInherit(_cstmView.getFlags());
    }

    public boolean isHidden()
    {
        return _mgr.isHidden(_cstmView.getFlags());
    }

    public boolean isEditable()
    {
        return true;
    }

    public String getSchemaName()
    {
        return _cstmView.getSchema();
    }

    public String getQueryName()
    {
        return _cstmView.getQueryName();
    }

    public String getCustomIconUrl()
    {
        //might support this in the future
        return null;
    }

    public boolean hasFilterOrSort()
    {
        return StringUtils.trimToNull(_cstmView.getFilter()) != null;
    }

    public String getContainerFilterName()
    {
        if (!hasFilterOrSort())
            return null;
        try
        {
            URLHelper src = new URLHelper(_cstmView.getFilter());
            String[] containerFilterNames = src.getParameters(FILTER_PARAM_PREFIX + "." + CONTAINER_FILTER_NAME);
            if (containerFilterNames.length > 0)
                return containerFilterNames[containerFilterNames.length - 1];
            return null;
        }
        catch (URISyntaxException use)
        {
            return null;
        }
    }

    public CstmView getCstmView()
    {
        return _cstmView;
    }
}
