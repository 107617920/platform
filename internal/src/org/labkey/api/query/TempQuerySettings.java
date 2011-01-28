/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Jun 11, 2009
 * Time: 11:32:35 AM
 */

/**
 * This class may be used ot create a QuerySettings from a given SQL statement,
 * schema name, and container.
 */
public class TempQuerySettings extends QuerySettings
{
    private String _sql;
    private Container _container;

    public TempQuerySettings(ViewContext context, String sql)
    {
        this(context, sql, "query");
    }

    public TempQuerySettings(ViewContext context, String sql, String dataRegionName)
    {
        super(dataRegionName);
        _sql = sql;
        _container = context.getContainer();
        setQueryName("sql");
    }

    public QueryDefinition getQueryDef(UserSchema schema)
    {
        QueryDefinition qdef;
        qdef = QueryService.get().createQueryDef(schema.getUser(), _container, schema, getQueryName());
        qdef.setSql(_sql);
        if (getContainerFilterName() != null)
            qdef.setContainerFilter(ContainerFilter.getContainerFilterByName(getContainerFilterName(), schema.getUser()));
        return qdef;
    }
}