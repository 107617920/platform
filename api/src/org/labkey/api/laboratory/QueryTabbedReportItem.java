/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 4/14/13
 * Time: 9:33 AM
 */
public class QueryTabbedReportItem extends TabbedReportItem
{
    private String _schemaName;
    private String _queryName;

    public QueryTabbedReportItem(DataProvider provider, String schemaName, String queryName, String label, String category)
    {
        super(provider, queryName, label, category);
        _schemaName = schemaName;
        _queryName = queryName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, getSchemaName());
        if (us == null)
            return null;

        QueryDefinition qd = us.getQueryDefForTable(getQueryName());
        if (qd == null)
            return null;

        List<QueryException> errors = new ArrayList<>();
        TableInfo ti = qd.getTable(errors, true);
        if (errors.size() > 0)
        {
            _log.error("Unable to create tabbed report item for query: " + getSchemaName() + "." + getQueryName());
            for (QueryException e : errors)
            {
                _log.error(e.getMessage(), e);
            }
            return null;
        }

        if (ti == null)
        {
            return null;
        }

        inferColumnsFromTable(ti);
        JSONObject json = super.toJSON(c, u);

        json.put("schemaName", getSchemaName());
        json.put("queryName", getQueryName());

        return json;
    }
}
