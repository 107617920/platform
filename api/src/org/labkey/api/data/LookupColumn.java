/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.RowIdForeignKey;

import java.util.Map;

public class LookupColumn extends ColumnInfo
{
    /**
     * Create a lookup column to a "real" table - one that actually exists
     * in the underlying database
     */
    static public LookupColumn create(ColumnInfo foreignKey, ColumnInfo lookupKey, ColumnInfo lookupColumn, boolean prefixColumnCaption)
    {
        if (lookupKey == null || lookupColumn == null)
            return null;
        LookupColumn ret = new LookupColumn(foreignKey, lookupKey, lookupColumn);
        ret.copyAttributesFrom(lookupColumn);
        ret.copyURLFrom(lookupColumn, foreignKey.getFieldKey(), null);
        if (prefixColumnCaption)
        {
            ret.setLabel(foreignKey.getLabel() + " " + lookupColumn.getLabel());
        }
        else
        {
            ret.setLabel(lookupColumn.getLabel());
        }
        if (ret.getFk() instanceof RowIdForeignKey)
        {
            ret.setFk(null);
        }
        return ret;
    }

    protected ColumnInfo _foreignKey;
    protected ColumnInfo _lookupKey;
    protected ColumnInfo _lookupColumn;
    protected boolean _joinOnContainer;

    public LookupColumn(ColumnInfo foreignKey, ColumnInfo lookupKey, ColumnInfo lookupColumn, boolean joinOnContainer)
    {
        // Bug 1166: always report that our parent table is the leftmost table, so the dataregion knows which
        // table to select from
        super(new FieldKey(foreignKey.getFieldKey(), lookupColumn.getName()), foreignKey.getParentTable());
        _foreignKey = foreignKey;
        _lookupKey = lookupKey;
        assert lookupKey.getValueSql("test") != null;
        _lookupColumn = lookupColumn;
        setSqlTypeName(lookupColumn.getSqlTypeName());
        setAlias(foreignKey.getAlias() + "$" + lookupColumn.getAlias());
        _joinOnContainer = joinOnContainer;
    }

    public LookupColumn(ColumnInfo foreignKey, ColumnInfo lookupKey, ColumnInfo lookupColumn)
    {
        this(foreignKey, lookupKey, lookupColumn, false);
    }


    public SQLFragment getValueSql(String tableAliasName)
    {
        return _lookupColumn.getValueSql(getTableAlias(tableAliasName));
    }


    public SQLFragment getJoinCondition(String tableAliasName)
    {
        return getJoinConditionHelper(
            _foreignKey.getTableAlias(tableAliasName), _foreignKey.getValueSql(tableAliasName), _foreignKey.getSqlTypeInt(),
            getTableAlias(tableAliasName), _lookupKey.getValueSql(getTableAlias(tableAliasName)), _lookupKey.getSqlTypeInt(),
                _joinOnContainer, getSqlDialect().isPostgreSQL()
        );
    }


    public static SQLFragment getJoinConditionHelper(String tableAliasName, SQLFragment fkSql, int fkType,
            String joinAlias, SQLFragment pkSql, int pkType,
            boolean joinOnContainer, boolean isPostgreSQL)
    {
        SQLFragment condition = new SQLFragment();
        boolean typeMismatch = fkType != pkType;
        condition.append("(");
        if (isPostgreSQL && typeMismatch)
            condition.append("CAST((").append(fkSql).append(") AS VARCHAR)");
        else
            condition.append(fkSql);
        condition.append(" = ");

        if (isPostgreSQL && typeMismatch)
            condition.append("CAST((").append(pkSql).append(") AS VARCHAR)");
        else
            condition.append(pkSql);

        if (joinOnContainer)
        {
            condition.append(" AND ").append(tableAliasName);
            condition.append(".Container = ").append(joinAlias).append(".Container");
        }
        condition.append(")");
        return condition;
    }

    
    @SuppressWarnings({"ConstantConditions"})
    public void declareJoins(String baseAlias, Map<String, SQLFragment> map)
    {
        boolean assertEnabled = false; // needed to generate SQL for logging
        assert assertEnabled = true;

        String colTableAlias = getTableAlias(baseAlias);

        if (assertEnabled || !map.containsKey(colTableAlias))
        {
            _foreignKey.declareJoins(baseAlias, map);
            SQLFragment strJoin = new SQLFragment("\n\tLEFT OUTER JOIN ");

            addLookupSql(strJoin, _lookupKey.getParentTable(), colTableAlias);
            strJoin.append(" ON ");
            strJoin.append(getJoinCondition(baseAlias));
            assert null == map.get(colTableAlias) || map.get(colTableAlias).getSQL().equals(strJoin.getSQL());
            map.put(colTableAlias, strJoin);
        }

        if (includeLookupJoins())
            _lookupColumn.declareJoins(colTableAlias, map);
    }


    protected void addLookupSql(SQLFragment strJoin, TableInfo lookupTable, String alias)
    {
        strJoin.append(lookupTable.getFromSQL(alias));
    }


    protected boolean includeLookupJoins()
    {
        return true;
    }


    /**
     * generate a unique table name for the joined in table
     * NOTE: postgres may ignore characters past 64 resulting in spurious duplicate alias errors
     * ref 10493
     * @param baseAlias alias of table on "left hand side" of the lookup
     * @return
     */
    public String getTableAlias(String baseAlias)
    {
        String alias = baseAlias + (baseAlias.endsWith("$")?"":"$") + _foreignKey.getAlias() + "$";
        alias = AliasManager.truncate(alias, 64);
        return alias;
    }

    
    public void setJoinOnContainer(boolean joinOnContainer)
    {
        _joinOnContainer = joinOnContainer;
    }

    public String getColumnName()
    {
        return _lookupColumn.getName();
    }
}
