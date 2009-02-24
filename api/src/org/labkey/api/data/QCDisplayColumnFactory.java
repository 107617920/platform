/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.QcColumn;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.*;

import java.util.Collections;
import java.util.Map;
import java.sql.Types;

/**
 * User: jgarms
 * Date: Jan 8, 2009
 */
public class QCDisplayColumnFactory implements DisplayColumnFactory
{
    public static final String RAW_VALUE_SUFFIX = "RawValue";

    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        String qcColName = colInfo.getQcColumnName();
        assert qcColName != null : "Attempt to render QC state for a non-qc column";

        FieldKey key = FieldKey.fromString(colInfo.getName());
        FieldKey qcKey = new FieldKey(key.getParent(), qcColName);

        Map<FieldKey,ColumnInfo> map = QueryService.get().getColumns(colInfo.getParentTable(), Collections.singletonList(qcKey));

        ColumnInfo qcColumn = map.get(qcKey);
        if (qcColumn == null) // For a custom query, it's possible the user has excluded our QC column
            return new DataColumn(colInfo);

        return new QCDisplayColumn(colInfo, qcColumn);
    }

    public static ColumnInfo[] createQcColumns(ColumnInfo valueColumn, PropertyDescriptor pd, TableInfo table, String parentLsidColumn)
    {
        ColumnInfo qcColumn = new QcColumn(pd, table, parentLsidColumn);

        AliasedColumn rawValueCol = new AliasedColumn(table, valueColumn.getName() + RAW_VALUE_SUFFIX, valueColumn);
        rawValueCol.setIsHidden(true);
        rawValueCol.setUserEditable(false);

        valueColumn.setDisplayColumnFactory(new QCDisplayColumnFactory());

        ColumnInfo[] result = new ColumnInfo[2];
        result[0] = qcColumn;
        result[1] = rawValueCol;

        return result;
    }

    private static ColumnInfo[] createQCColumns(AbstractTableInfo table, ColumnInfo valueColumn, DomainProperty property, ColumnInfo colObjectId)
    {
        ColumnInfo qcColumn = new ExprColumn(table,
                property.getName() + QcColumn.QC_INDICATOR_SUFFIX,
                PropertyForeignKey.getValueSql(colObjectId.getValueSql(ExprColumn.STR_TABLE_ALIAS), property.getQCValueSQL(), property.getPropertyId(), false),
                Types.VARCHAR);

        qcColumn.setNullable(true);
        qcColumn.setUserEditable(false);
        qcColumn.setIsHidden(true);

        valueColumn.setQcColumnName(qcColumn.getName());

        AliasedColumn rawValueCol = new AliasedColumn(table, valueColumn.getName() + RAW_VALUE_SUFFIX, valueColumn);
        rawValueCol.setUserEditable(false);
        rawValueCol.setIsHidden(true);
        rawValueCol.setQcColumnName(null); // This column itself does not allow QC
        rawValueCol.setNullable(true); // Otherwise we get complaints on import for required fields

        valueColumn.setDisplayColumnFactory(new QCDisplayColumnFactory());

        ColumnInfo[] result = new ColumnInfo[2];
        result[0] = qcColumn;
        result[1] = rawValueCol;

        return result;
    }

    public static void addQCColumns(AbstractTableInfo table, ColumnInfo valueColumn, DomainProperty property, ColumnInfo colObjectId)
    {
        ColumnInfo[] newColumns = createQCColumns(table, valueColumn, property, colObjectId);
        for (ColumnInfo column : newColumns)
        {
            table.addColumn(column);
        }
    }

}