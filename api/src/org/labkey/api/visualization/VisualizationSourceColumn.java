/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
package org.labkey.api.visualization;

import org.json.JSONArray;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

import java.util.*;

/**
 * User: brittp
 * Date: Jan 27, 2011 11:13:33 AM
 */
public class VisualizationSourceColumn
{
    private String _queryName;
    private UserSchema _schema;
    private boolean _allowNullResults;
    private String _name;
    protected String _alias;
    protected String _clientAlias;
    private String _otherAlias;
    protected String _label;
    private JdbcType _type = null;
    private Set<Object> _values = new LinkedHashSet<>();

    public Map<String, String> toJSON(String measureName)
    {
        Map<String, String> info = new HashMap<>();
        info.put("measureName", getOriginalName());
        if (getClientAlias() != null)
        {
            info.put("alias", getClientAlias());
        }
        info.put("columnName", getAlias());
        return info;
    }

    public static class Factory
    {
        private Map<Path, VisualizationSourceColumn> _currentCols = new HashMap<>();
        private Map<String,VisualizationSourceColumn> _aliasMap = new CaseInsensitiveHashMap<>();

        private VisualizationSourceColumn findOrAdd(VisualizationSourceColumn col)
        {
            col.ensureColumn();

            Path key = new Path(col.getSchemaName(), col.getQueryName(), col.getOriginalName());
            VisualizationSourceColumn current = _currentCols.get(key);
            if (current != null)
            {
                // do any necessary merging:
                if (!col.isAllowNullResults())
                    current.setAllowNullResults(false);
                return current;
            }
            else
            {
                _currentCols.put(key, col);
                _aliasMap.put(col.getAlias(), col);
                return col;
            }
        }

        public VisualizationSourceColumn create(UserSchema schema, String queryName, String name, Boolean allowNullResults)
        {
            VisualizationSourceColumn col = new VisualizationSourceColumn(schema, queryName, name, allowNullResults);
            return findOrAdd(col);
        }

        public VisualizationSourceColumn create(UserSchema schema, String queryName, String name, String alias, Boolean allowNullResults)
        {
            VisualizationSourceColumn col = new VisualizationSourceColumn(schema, queryName, name, allowNullResults);
            col._alias = alias;
            return findOrAdd(col);
        }

        public VisualizationSourceColumn create(ViewContext context, Map<String, Object> properties)
        {
            VisualizationSourceColumn col = new VisualizationSourceColumn(context, properties);
            return findOrAdd(col);
        }

        public VisualizationSourceColumn getByAlias(String alias)
        {
            return _aliasMap.get(alias);
        }
    }

    protected VisualizationSourceColumn(UserSchema schema, String queryName, String name, Boolean allowNullResults)
    {
        _name = name;
        _queryName = queryName;
        _schema = schema;
        _allowNullResults = allowNullResults == null || allowNullResults;
    }

    protected VisualizationSourceColumn(ViewContext context, Map<String, Object> properties)
    {
        this(getUserSchema(context, (String) properties.get("schemaName")), (String) properties.get("queryName"), (String) properties.get("name"), (Boolean) properties.get("allowNullResults"));
        JSONArray values = (JSONArray) properties.get("values");
        _clientAlias = (String)properties.get("alias");
        if (values != null)
        {
            for (int i = 0; i < values.length(); i++)
                _values.add(values.get(i));
        }
    }

    private static UserSchema getUserSchema(ViewContext context, String schemaName)
    {
        if (schemaName == null)
        {
            throw new NullPointerException("No schema specified");
        }
        return QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);
    }

    public String getSchemaName()
    {
        return _schema.getName();
    }

    public UserSchema getSchema()
    {
        return _schema;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public String getOriginalName()
    {
        return _name;
    }

    public String getClientAlias()
    {
        return _clientAlias;
    }

    public boolean isAllowNullResults()
    {
        return _allowNullResults;
    }

    public void setAllowNullResults(boolean allowNullResults)
    {
        _allowNullResults = allowNullResults;
    }

    public void ensureColumn() throws IllegalArgumentException
    {
        if (getSchemaName() == null || getQueryName() == null || getOriginalName() == null)
        {
            throw new IllegalArgumentException("SchemaName, queryName, and name are all required for each measure, dimension, or sort.");
        }

        try
        {
            ColumnInfo columnInfo = findColumnInfo();
            if (columnInfo == null)
            {
                throw new NotFoundException("Unable to find field " + getOriginalName() + " in " + getSchemaName() + "." + getQueryName() +
                        ".  The field may have been deleted, renamed, or you may not have permissions to read the data.");
            }
        }
        catch (SQLGenerationException e)
        {
            throw new NotFoundException(e.getMessage(), e);
        }
    }

    public String getSelectName()
    {
        try
        {
            ColumnInfo columnInfo = findColumnInfo();
            List<String> parts;
            if (columnInfo != null)
            {
                // We found the column
                parts = columnInfo.getFieldKey().getParts();
            }
            else
            {
                throw new NotFoundException("Unable to find field " + _name + " in " + _schema.getName() + "." + _queryName +
                        ".  The field may have been deleted, renamed, or you may not have permissions to read the data.");
            }

            StringBuilder selectName = new StringBuilder();
            String sep = "";
            for (String part : parts)
            {
                selectName.append(sep);
                String identifier = _schema.getDbSchema().getSqlDialect().makeLegalIdentifier(part);
                if (identifier.charAt(0) == '"')
                    selectName.append(identifier);
                else
                    selectName.append("\"").append(identifier).append("\"");
                sep = ".";
            }
            return selectName.toString();
        }
        catch (SQLGenerationException e)
        {
            throw new UnexpectedException(e);
        }
    }

    public String getLabel()
    {
        try
        {
            _getTypeAndLabel();
            return _label;
        }
        catch (SQLGenerationException x)
        {
            return null;
        }
    }

    public JdbcType getType() throws SQLGenerationException
    {
        _getTypeAndLabel();
        return _type;
    }

    private void _getTypeAndLabel() throws SQLGenerationException
    {
        if (null == _label && null == _type)
        {
            try
            {
                ColumnInfo column = findColumnInfo();
                if (column == null)
                {
                    throw new SQLGenerationException("Unable to find field " + _name + " in " + _schema.getName() + "." + _queryName +
                            ".  The field may have been deleted, renamed, or you may not have permissions to read the data.");
                }

                _type = column.getJdbcType();
                _label = column.getLabel();
            }
            catch (QueryParseException e)
            {
                throw new SQLGenerationException("Unable to determine datatype for field " + _name + " in " + _schema.getName() + "." + _queryName + 
                        ".  The data may not exist, or you may not have permissions to read the data.", e);
            }
        }
    }

    ColumnInfo _columnInfo;

    private ColumnInfo findColumnInfo() throws SQLGenerationException
    {
        if (null == _columnInfo)
        {
            TableInfo tinfo = _schema.getTable(_queryName);
            if (tinfo == null)
            {
                throw new SQLGenerationException("Unable to find table " + _schema.getName() + "." + _queryName +
                        ".  The table may have been deleted, or you may not have permissions to read the data.");
            }

            FieldKey fieldKey = FieldKey.fromString(_name);
            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(tinfo, Collections.singleton(fieldKey));
            ColumnInfo column = cols.get(fieldKey);
            if (column == null && _name.contains("."))
            {
                fieldKey = FieldKey.fromParts(_name.split("[\\./]"));
                cols = QueryService.get().getColumns(tinfo, Collections.singleton(fieldKey));
                column = cols.get(fieldKey);
            }
            _columnInfo = column;
        }
        return _columnInfo;
    }

    public Set<Object> getValues()
    {
        return _values;
    }

    public void syncValues(VisualizationSourceColumn other)
    {
        _values.addAll(other.getValues());
        other._values.addAll(getValues());
    }

    public void setOtherAlias(String otherAlias)
    {
        _otherAlias = otherAlias;
    }


    public String getSQLAlias()
    {
        return "\"" + getAlias() + "\"";
    }

    public String getSQLOther()
    {
        return "\"" + getOtherAlias() + "\"";
    }

    public String getOtherAlias()
    {
        if (_otherAlias == null)
            return getAlias();
        else
            return _otherAlias;
    }
    
    public String getAlias()
    {
        if (null == _alias)
        {
            _alias = getAlias(getSchemaName(), _queryName, _name);
        }
        return _alias;
    }

    public static String getAlias(String schemaName, String queryName, String name)
    {
        return (schemaName + "_" + queryName + "_" + name).replaceAll("/","_");
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VisualizationSourceColumn that = (VisualizationSourceColumn) o;

        if (!_name.equals(that._name)) return false;
        if (!_queryName.equals(that._queryName)) return false;
        if (!getSchemaName().equals(that.getSchemaName())) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = getSchemaName().hashCode();
        result = 31 * result + _queryName.hashCode();
        result = 31 * result + _name.hashCode();
        return result;
    }
}
