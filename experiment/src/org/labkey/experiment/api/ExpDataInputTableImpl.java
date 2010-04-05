/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.exp.query.ExpDataInputTable;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FieldKey;

import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Jan 4, 2010
 */
public class ExpDataInputTableImpl extends ExpInputTableImpl<ExpDataInputTable.Column> implements ExpDataInputTable
{
    public ExpDataInputTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoDataInput(), schema, null);
    }

    public ColumnInfo createColumn(String alias, ExpDataInputTable.Column column)
    {
        switch (column)
        {
            case Data:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("DataId"));
                result.setFk(getExpSchema().getDataIdForeignKey());
                return result;
            }
            case Role:
                return wrapColumn(alias, _rootTable.getColumn("Role"));
            case TargetProtocolApplication:
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("TargetApplicationId"));
                result.setFk(getExpSchema().getProtocolApplicationForeignKey());
                return result;
            default:
                throw new IllegalArgumentException("Unsupported column: " + column);
        }
    }

    public void populate()
    {
        addColumn(Column.Data);
        addColumn(Column.TargetProtocolApplication);
        addColumn(Column.Role);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(Column.Data));
        defaultCols.add(FieldKey.fromParts(Column.Role));
        defaultCols.add(FieldKey.fromParts(ExpMaterialInputTable.Column.TargetProtocolApplication, ExpProtocolApplicationTableImpl.Column.Run));
        defaultCols.add(FieldKey.fromParts(ExpMaterialInputTable.Column.TargetProtocolApplication, ExpProtocolApplicationTableImpl.Column.Type));
        setDefaultVisibleColumns(defaultCols);
    }

}
