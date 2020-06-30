/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Jan 4, 2010
 */
public class ExpMaterialInputTableImpl extends ExpInputTableImpl<ExpMaterialInputTable.Column> implements ExpMaterialInputTable
{
    public ExpMaterialInputTableImpl(String name, UserSchema schema, ContainerFilter cf)
    {
        super(name, ExperimentServiceImpl.get().getTinfoMaterialInput(), schema, null, cf);
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Material:
            {
                var result = wrapColumn(alias, _rootTable.getColumn("MaterialId"));
                result.setFk(getExpSchema().getMaterialIdForeignKey(null, null, getContainerFilter()));
                return result;
            }
            case Role:
                return wrapColumn(alias, _rootTable.getColumn("Role"));
            case TargetProtocolApplication:
                var result = wrapColumn(alias, _rootTable.getColumn("TargetApplicationId"));
                result.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                return result;

            case LSID:
            {
                final SqlDialect dialect = getSqlDialect();
                SQLFragment sql = new SQLFragment("" +
                        dialect.concatenate(
                                "'" + MaterialInput.lsidPrefix() + "'",
                                "CAST(" + ExprColumn.STR_TABLE_ALIAS + ".materialId AS VARCHAR)",
                                "'.'",
                                "CAST(" + ExprColumn.STR_TABLE_ALIAS + ".targetApplicationId AS VARCHAR)"));

                var col = new ExprColumn(this, alias, sql, JdbcType.VARCHAR);
                col.setHidden(true);
                col.setCalculated(true);
                col.setUserEditable(false);
                col.setReadOnly(true);
                return col;
            }

            case ProtocolInput:
            {
                var col = wrapColumn(alias, _rootTable.getColumn("ProtocolInputId"));
                col.setFk(getExpSchema().getMaterialProtocolInputForeignKey(getContainerFilter()));
                col.setHidden(true);
                return col;
            }

            default:
                throw new IllegalArgumentException("Unsupported column: " + column);
        }
    }

    @Override
    protected ColumnInfo getLSIDColumn()
    {
        return getColumn(Column.LSID);
    }

    @Override
    protected void populateColumns()
    {
        addColumn(Column.Material);
        addColumn(Column.TargetProtocolApplication);
        addColumn(Column.Role);
        addColumn(Column.LSID);
        addColumn(Column.ProtocolInput);

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts(Column.Material));
        defaultCols.add(FieldKey.fromParts(Column.Role));
        setDefaultVisibleColumns(defaultCols);
    }

}
