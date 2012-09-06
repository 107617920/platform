/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

abstract public class LookupForeignKey extends AbstractForeignKey implements Cloneable
{
    ActionURL _baseURL;
    Object _param;
    private boolean _prefixColumnCaption = false;
    String _titleColumn;

    private Map<FieldKey, Pair<String, Boolean>> _additionalJoins = new HashMap<FieldKey, Pair<String, Boolean>>();

    public LookupForeignKey(ActionURL baseURL, String paramName, String tableName, String pkColumnName, String titleColumn)
    {
        super(tableName, pkColumnName);
        _baseURL = baseURL;
        _param = paramName;
        _titleColumn = titleColumn;
    }

    public LookupForeignKey(ActionURL baseURL, String paramName, String pkColumnName, String titleColumn)
    {
        this(baseURL, paramName, null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(ActionURL baseURL, Enum paramName, String pkColumnName, String titleColumn)
    {
        this(pkColumnName);
        _baseURL = baseURL;
        _param = paramName;
        _titleColumn = titleColumn;
    }

    public LookupForeignKey(String tableName, @Nullable String pkColumnName, String titleColumn)
    {
         this(null, null, tableName, pkColumnName, titleColumn);
    }

    public LookupForeignKey(@Nullable String pkColumnName, @Nullable String titleColumn)
    {
         this(null, null, null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(@Nullable String pkColumnName)
    {
        this(null, null, null, pkColumnName, null);
    }

    /** Use the table's (single) PK column as the lookup target */
    public LookupForeignKey()
    {
        this(null);
    }

    public void setPrefixColumnCaption(boolean prefix)
    {
        _prefixColumnCaption = prefix;
    }

    /** Adds an extra pair of columns to the join. This doesn't affect how the lookup is presented through query,
     * but does change the SQL that we generate for the join criteria */
    public void addJoin(FieldKey fkColumn, String lookupColumnName, boolean equalOrIsNull)
    {
        assert fkColumn.getParent() == null : "ForeignKey must belong to this table";
        addSuggested(fkColumn);
        _additionalJoins.put(fkColumn, Pair.of(lookupColumnName, equalOrIsNull));
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        TableInfo table = getLookupTableInfo();
        if (displayField == null)
        {
            displayField = _titleColumn;
            if (displayField == null)
                displayField = table.getTitleColumn();
        }
        if (displayField == null)
            return null;
        if (table.supportsContainerFilter() && parent.getParentTable().getContainerFilter() != null)
        {
            ContainerFilterable newTable = (ContainerFilterable)table;

            // Only override if the new table doesn't already have some special filter
            if (newTable.hasDefaultContainerFilter())
                newTable.setContainerFilter(new DelegatingContainerFilter(parent.getParentTable()));
        }
        LookupColumn result = LookupColumn.create(parent, getPkColumn(table), table.getColumn(displayField), _prefixColumnCaption);
        if (result != null)
        {
            for (Map.Entry<FieldKey, Pair<String, Boolean>> entry : _additionalJoins.entrySet())
            {
                Pair<String, Boolean> pair = entry.getValue();
                ColumnInfo lookupColumn = table.getColumn(pair.first);
                assert lookupColumn != null : "Couldn't find additional lookup column of name '" + entry.getValue() + "' in " + table;

                // Get the possibly remapped foreign key column
                FieldKey foreignKey = getRemappedField(entry.getKey());
                result.addJoin(foreignKey, lookupColumn, pair.second);
            }
        }

        return result;
    }

    /**
     * Override this method if the primary key of the lookup table does not really exist.
     */
    protected ColumnInfo getPkColumn(TableInfo table)
    {
        return table.getColumn(getLookupColumnName());
    }


    public StringExpression getURL(ColumnInfo parent)
    {
        return getURL(parent, false);
    }


    protected StringExpression getURL(ColumnInfo parent, boolean useDetailsURL)
    {
        if (null != _baseURL)
        {
            // CONSIDER: set ContainerContext in AbstractForeignKey.getURL() so all subclasses can benefit
            DetailsURL url = new DetailsURL(_baseURL, _param.toString(), parent.getFieldKey());
            setURLContainerContext(url, getLookupTableInfo());
            return url;
        }

        if (!useDetailsURL)
            return null;

        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null || getLookupColumnName() == null)
            return null;

        return getDetailsURL(parent, lookupTable, getLookupColumnName());
    }


    public static StringExpression getDetailsURL(ColumnInfo parent, TableInfo lookupTable, String columnName)
    {
        FieldKey columnKey = new FieldKey(null,columnName);
        Set<FieldKey> keys = Collections.singleton(columnKey);

        StringExpression expr = lookupTable.getDetailsURL(null, null);
        if (expr instanceof StringExpressionFactory.FieldKeyStringExpression)
        {
            StringExpressionFactory.FieldKeyStringExpression f = (StringExpressionFactory.FieldKeyStringExpression)expr;
            StringExpressionFactory.FieldKeyStringExpression rewrite;

            // if the URL only substitutes the PK we can rewrite as FK (does the DisplayColumn handle when the join fails?)
            if (f.validateFieldKeys(keys))
                rewrite = f.remapFieldKeys(null, Collections.singletonMap(columnKey, parent.getFieldKey()));
            else
                rewrite = f.remapFieldKeys(parent.getFieldKey(), null);

            // CONSIDER: set ContainerContext in AbstractForeignKey.getURL() so all subclasses can benefit
            if (rewrite instanceof DetailsURL)
                setURLContainerContext((DetailsURL)rewrite, lookupTable);

            return rewrite;
        }
        return null;
    }

    protected static DetailsURL setURLContainerContext(DetailsURL url, TableInfo lookupTable)
    {
        ContainerContext cc = lookupTable.getContainerContext();
        if (cc != null)
            url.setContainerContext(cc, false);
        return url;
    }

}
