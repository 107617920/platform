/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
* User: adam
* Date: Sep 14, 2010
* Time: 1:10:47 PM
*/
public class MultiValuedLookupColumn extends LookupColumn
{
    private final ColumnInfo _display;
    private final ForeignKey _rightFk;
    private final ColumnInfo _junctionKey;

    public MultiValuedLookupColumn(FieldKey fieldKey, ColumnInfo parentPkColumn, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk, ColumnInfo display)
    {
        super(parentPkColumn, childKey, display);
        _display = display;
        _rightFk = fk;
        _junctionKey = junctionKey;
        copyAttributesFrom(display);
        copyURLFrom(display, parentPkColumn.getFieldKey(), null);
        setFieldKey(fieldKey);
        setJdbcType(JdbcType.VARCHAR);
    }

    // We don't traverse FKs from a multi-valued column
    @Override
    public ForeignKey getFk()
    {
        return null;
    }

    @Override
    public DisplayColumn getRenderer()
    {
        return new MultiValuedDisplayColumn(super.getRenderer());
    }

    @Override
    public DisplayColumnFactory getDisplayColumnFactory()
    {
        return new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new MultiValuedDisplayColumn(MultiValuedLookupColumn.super.getDisplayColumnFactory().createRenderer(colInfo));
            }
        };
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(getTableAlias(tableAliasName) + "." + _display.getAlias());
    }


    protected void addLookupSql(SQLFragment strJoin, TableInfo lookupTable, String alias)
    {
        SqlDialect dialect = lookupTable.getSqlDialect();
        boolean groupConcat = dialect.supportsGroupConcat();

        strJoin.append("\n\t(\n\t\t");
        strJoin.append("SELECT ");
        strJoin.append(_lookupKey.getValueSql("child"));

        // In group_concat case, we always join to child.  In select_concat case, we need to re-join to junction on each
        // column select, so we need a unique alias ("c") for the inner join.
        String joinAlias = groupConcat ? "child" : "c";

        Map<String, SQLFragment> joins = new LinkedHashMap<String, SQLFragment>();
        _lookupColumn.declareJoins(joinAlias, joins);

        // Select and aggregate all columns in the far right table for now.  TODO: Select only required columns.
        for (ColumnInfo col : _rightFk.getLookupTableInfo().getColumns())
        {
            // Skip text and ntext columns -- aggregates don't work on them in some databases
            if (col.isLongTextType())
                continue;

            ColumnInfo lc = _rightFk.createLookupColumn(_junctionKey, col.getName());
            strJoin.append(", \n\t\t\t");
            SQLFragment valueSql = new SQLFragment();
            boolean needsCast = "entityid".equalsIgnoreCase(lc.getSqlTypeName()) || "lsidtype".equalsIgnoreCase(lc.getSqlTypeName());
            if (needsCast)
            {
                valueSql.append("CAST((");
            }
            valueSql.append(lc.getValueSql(joinAlias));
            if (needsCast)
            {
                valueSql.append(") AS VARCHAR)");
            }

            if (groupConcat)
            {
                strJoin.append(getAggregateFunction(valueSql));
            }
            else
            {
                SQLFragment select = new SQLFragment("SELECT ");
                select.append(valueSql);
                select.append(" FROM ");
                select.append(_lookupKey.getParentTable().getFromSQL("c"));

                for (SQLFragment fragment : joins.values())
                {
                    String join = StringUtils.replace(fragment.toString(), "\n\t", "\n\t\t\t\t");
                    join = join.replace("LEFT OUTER", "INNER");
                    select.append(join);
                }

                select.append(" WHERE ");
                select.append(_lookupKey.getValueSql("child"));
                select.append(" = ");
                select.append(_lookupKey.getValueSql("c"));

                // TODO: Always order by value

                strJoin.append(dialect.getSelectConcat(select));
            }

            strJoin.append(" AS ");
            strJoin.append(lc.getAlias());
        }

        strJoin.append("\n\t\tFROM ");
        strJoin.append(_lookupKey.getParentTable().getFromSQL("child"));

        if (groupConcat)
        {
            for (SQLFragment fragment : joins.values())
            {
                strJoin.append(StringUtils.replace(fragment.toString(), "\n\t", "\n\t\t"));
            }
        }

        // TODO: Add ORDER BY?

        strJoin.append("\n\t\tGROUP BY ");
        strJoin.append(_lookupKey.getValueSql("child"));
        strJoin.append("\n\t) ").append(alias);
    }
    

    @Override
    // The multivalued column joins take place within the aggregate function sub-select; we don't want super class
    // including these columns as top-level joins.
    protected boolean includeLookupJoins()
    {
        return false;
    }

    @Override
    public String getTableAlias(String baseAlias)
    {
        return AliasManager.makeLegalName(baseAlias + "$" + this.getName(), getSqlDialect());
    }

    // By default, use GROUP_CONCAT aggregate function, which returns a common-separated list of values.  Override this
    // and (for non-varchar aggregate function) getSqlTypeName() to apply a different aggregate.
    protected SQLFragment getAggregateFunction(SQLFragment sql)
    {
        // Can't sort because we need to make sure that all of the multi-value columns come back in the same order 
        return getSqlDialect().getGroupConcat(sql, false, false);
    }
}
