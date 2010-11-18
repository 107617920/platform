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
package org.labkey.study;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
* User: jeckels
* Date: Oct 8, 2010
*/
public class StudyUnionTableInfo extends VirtualTable
{
    StudyImpl _study;

    final public static String[] COLUMN_NAMES = {
            "participantid",
            "lsid",
            "sequencenum",
            "modified",
            "created",
            "modifiedby",
            "createdby",
            "sourcelsid",
            "_key",
            "_visitdate",
            "qcstate",
            "participantsequencekey"
    };

    final Set<String> unionColumns = new HashSet<String>(Arrays.asList(COLUMN_NAMES));
    SQLFragment unionSql;
    private User _user;

    public StudyUnionTableInfo(StudyImpl study, Collection<DataSetDefinition> defs, User user)
    {
        super(StudySchema.getInstance().getSchema());
        setName("StudyData");
        _study = study;
        _user = user;
        init(defs);
    }

    public void init(Collection<DataSetDefinition> defs)
    {
        SQLFragment sqlf = new SQLFragment();
        int count = 0;
        String unionAll = "";

        PropertyDescriptor[] sharedProperties = _study.getSharedProperties();

        for (DataSetDefinition def : defs)
        {
            TableInfo ti = def.getStorageTableInfo();
            if (null == ti || (_user != null && !def.canRead(_user)))
                continue;
            count++;
            sqlf.append(unionAll);
            sqlf.append("SELECT CAST('" + def.getEntityId() + "' AS " + getSqlDialect().getGuidType() + ") AS dataset, " + def.getDataSetId() + " AS datasetid");

            String visitPropertyName = def.getVisitDatePropertyName();
            ColumnInfo visitColumn = null==visitPropertyName ? null : ti.getColumn(visitPropertyName);
            if (null != visitPropertyName && (null == visitColumn || visitColumn.getSqlTypeInt() != Types.TIMESTAMP))
                Logger.getLogger(StudySchema.class).info("Could not find visit column of correct type '" + visitPropertyName + "' in dataset '" + def.getName() + "'");
            if (null != visitColumn && visitColumn.getSqlTypeInt() == Types.TIMESTAMP)
                sqlf.append(", ").append(visitColumn.getValueSql("D")).append(" AS _visitdate");
            else
                sqlf.append(", ").append(NullColumnInfo.nullValue(getSqlDialect().getDefaultDateTimeDataType())).append(" AS _visitdate");

            // Add all of the standard dataset columns
            for (String column : unionColumns)
            {
                if ("_visitdate".equalsIgnoreCase(column))
                    continue;
                sqlf.append(", ").append(ti.getColumn(column).getValueSql("D"));
            }

            // Add all of the properties that are common to all datasets
            for (PropertyDescriptor pd : sharedProperties)
            {
                ColumnInfo col = ti.getColumn(pd.getName());
                if (col != null)
                {
                    sqlf.append(", ").append(col.getValueSql("D"));
                }
                else
                {
                    // in at least Postgres, it isn't legal to union two columns where one is NULL and the other is
                    // numeric. It's OK when the other column is text. It's presently unknown whether this is a problem if the
                    // missing column is something other than text or numeric.
                    //
                    // The following selects a 0 if the column is numeric and isn't present for one of the tables in
                    // the union.
                    //
                    // Too bad study.getSharedProperties returns properties that aren't present on all datasets.

                    String empty;
                    switch (pd.getSqlTypeInt())
                    {
                        case Types.BIGINT:
                        case Types.INTEGER:
                        case Types.NUMERIC:
                        case Types.DOUBLE:
                        case Types.DECIMAL:
                        case Types.FLOAT:
                            empty = "CAST(NULL AS " + getSqlDialect().sqlTypeNameFromSqlType(pd.getSqlTypeInt()) + ")";
                            break;
                        default:
                            empty = "NULL";
                    }

                    sqlf.append(String.format(", %s AS %s", empty, getSqlDialect().makeLegalIdentifier(pd.getName())));
                }
            }

            sqlf.append(" FROM " + ti.getSelectName() + " D");
            unionAll = ") UNION ALL\n(";
        }

        if (0==count)
        {
            sqlf.append("SELECT CAST(NULL AS " + getSqlDialect().getGuidType() + ") as dataset, 0 as datasetid");
            for (String column : unionColumns)
            {
                sqlf.append(", ");
                if ("qcstate".equalsIgnoreCase(column) || "sequencenum".equalsIgnoreCase(column) || "createdby".equalsIgnoreCase(column) || "modifiedby".equalsIgnoreCase(column))
                    sqlf.append("0");
                else if ("participantid".equalsIgnoreCase(column))
                    sqlf.append("CAST(NULL as VARCHAR)");
                else if ("_visitdate".equalsIgnoreCase(column) || "modified".equalsIgnoreCase(column) || "created".equalsIgnoreCase(column))
                    sqlf.append("CAST(NULL AS " + getSchema().getSqlDialect().getDefaultDateTimeDataType() + ")");
                else
                    sqlf.append(" NULL");
                sqlf.append(" AS " + column);
            }
            sqlf.append(" WHERE 0=1");
        }

        unionSql = new SQLFragment();
        unionSql.appendComment("<StudyUnionTableInfo>", getSchema().getSqlDialect());
        if (count > 1)
            unionSql.append("(");
        unionSql.append(sqlf);
        if (count > 1)
            unionSql.append(")");
        unionSql.appendComment("</StudyUnionTableInfo>", getSchema().getSqlDialect());
        makeColumnInfos(sharedProperties);
    }


    @Override
    public String getSelectName()
    {
        return null;
    }


    @NotNull
    @Override
    public SQLFragment getFromSQL()
    {
        return unionSql;
    }

    private void makeColumnInfos(PropertyDescriptor[] sharedProperties)
    {
        TableInfo template = DataSetDefinition.getTemplateTableInfo();

        for (String name : COLUMN_NAMES)
        {
            ColumnInfo ci = new ColumnInfo(name, this);
            ColumnInfo t = template.getColumn(name);
            if (null != t)
            {
                ci.setExtraAttributesFrom(t);
                ci.setSqlTypeName(t.getSqlTypeName());
            }
            addColumn(ci);
        }

        for (PropertyDescriptor pd : sharedProperties)
        {
            ColumnInfo ci = new ColumnInfo(pd.getName(), this);
            PropertyColumn.copyAttributes(_user, ci, pd);
            ci.setSqlTypeName(StudySchema.getInstance().getSqlDialect().sqlTypeNameFromSqlType(pd.getSqlTypeInt()));
            addColumn(ci);
        }

        ColumnInfo datasetColumn = new ColumnInfo("dataset", this);
        datasetColumn.setSqlTypeName("VARCHAR");
        addColumn(datasetColumn);
        ColumnInfo datasetIdColumn = new ColumnInfo("datasetid", this);
        datasetColumn.setSqlTypeName("INT");
        addColumn(datasetIdColumn);
    }

    @Override
    public String toString()
    {
        return "StudyData UNION table";
    }
}
