/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.sql.Query;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TableQueryDefinition extends QueryDefinitionImpl
{
    private String _sql;
    private String _title;

    public TableQueryDefinition(UserSchema schema, String tableName)
    {
        super(schema.getUser(), getQueryDef(schema, tableName));
        _schema = schema;
    }


    private static QueryDef getQueryDef(UserSchema schema, String tableName)
    {
        QueryDef result = QueryManager.get().getQueryDef(schema.getContainer(), schema.getSchemaName(), tableName, false);
        if (result == null)
        {
            result = new QueryDef();
            result.setName(tableName);
            result.setContainer(schema.getContainer().getId());
            result.setSchema(schema.getSchemaName());
        }
        return result;
    }


    public ActionURL urlFor(QueryAction action, Container container)
    {
        ActionURL url = null;
        List<QueryException> errors = new ArrayList<>();
        TableInfo table = getTable(errors, true);
        if (table != null)
        {
            switch (action)
            {
                case insertQueryRow:
                    url = table.getInsertURL(container);
                    break;
                case deleteQueryRows:
                    url = table.getDeleteURL(container);
                    break;
                case executeQuery:
                    url = table.getGridURL(container);
                    break;
                case importData:
                    url = table.getImportDataURL(container);
                    break;
            }
        }

        if (url == AbstractTableInfo.LINK_DISABLER_ACTION_URL)
            return null;

        return url != null ? url : super.urlFor(action, container);
    }


    public ActionURL urlFor(QueryAction action, Container container, Map<String, Object> pks)
    {
        ActionURL url = null;
        List<QueryException> errors = new ArrayList<>();
        TableInfo table = getTable(errors, true);
        if (table != null)
        {
            switch (action)
            {
                case detailsQueryRow:
                    Map<FieldKey, ColumnInfo> selectCols = QueryService.get().getColumns(table, Collections.<FieldKey>emptySet(), table.getColumns());
                    StringExpression expr = table.getDetailsURL(selectCols.keySet(), container);
                    // See if there's a details URL available with the set of columns that we can offer, and
                    // we have enough PK values to uniquely identify the row
                    if (expr != null && pks.keySet().containsAll(table.getPkColumnNames()))
                    {
                        SimpleFilter filter = new SimpleFilter();
                        for (Map.Entry<String, Object> pk : pks.entrySet())
                        {
                            filter.addCondition(pk.getKey(), pk.getValue());
                        }
                        try (Results rs = new TableSelector(table, selectCols.values(), filter, null).setForDisplay(true).setMaxRows(1).getResults())
                        {
                            if (rs.next())
                            {
                                RenderContext ctx = new RenderContext(null);
                                ctx.setResults(rs);
                                ctx.setRow(rs.getRowMap());
                                return new ActionURL(expr.eval(ctx));
                            }
                            else
                            {
                                return null;
                            }
                        }
                        catch (SQLException e)
                        {
                            throw new RuntimeSQLException(e);
                        }
                    }
                    break;
            }
        }

        return url != null ? url : super.urlFor(action, container, pks);
    }


    @Override
    public String getDescription()
    {
        TableInfo t = getTable(new ArrayList<QueryException>(), true);
        return t == null ? null : t.getDescription();
    }


    @Override
    protected TableInfo createTable(@NotNull UserSchema schema, List<QueryException> errors, boolean includeMetadata)
    {
        return schema.getTable(getName(), includeMetadata);
    }

    @Override
    public boolean canEdit(User user)
    {
        return super.canEdit(user);
    }


    public String getSql()
    {
        if (_sql != null)
            return _sql;

        UserSchema schema = getSchema();
        Query query = new Query(schema);
        query.setRootTable(FieldKey.fromParts(getName()));
        _sql = query.getQueryText();

        return _sql;
    }


    public void setSql(String sql)
    {
        // Can't change the SQL
    }


    public boolean isTableQueryDefinition()
    {
        return true;
    }


    @Override
    public boolean isSqlEditable()
    {
        return false;
    }


    public boolean isMetadataEditable()
    {
        TableInfo tableInfo = getTable(new ArrayList<QueryException>(), true);
        // Might have been deleted out from under us
        return tableInfo != null && tableInfo.isMetadataOverrideable();
    }

    @Override
    public String getTitle()
    {
        if (null == _title)
        {
            List<QueryException> errors = new ArrayList<>();
            TableInfo tableInfo = getTable(getSchema(), errors, false);
            if (null != tableInfo && null != tableInfo.getName())
                _title = tableInfo.getTitleField();
        }

        if (null == _title)
            _title = getName();
        return _title;
    }
}
