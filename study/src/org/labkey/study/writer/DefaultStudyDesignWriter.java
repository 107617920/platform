/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.Results;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.query.StudyQuerySchema;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 1/24/14.
 */
public abstract class DefaultStudyDesignWriter
{
    protected void writeTableData(StudyExportContext ctx, VirtualFile vf, Set<String> tableNames, StudyQuerySchema schema, StudyQuerySchema projectSchema) throws SQLException, IOException
    {
        for (String tableName : tableNames)
        {
            StudyQuerySchema.TablePackage tableAndContainer = schema.getTablePackage(ctx, projectSchema, tableName);
            writeTableData(ctx, vf, tableAndContainer.getTableInfo(), getDefaultColumns(tableAndContainer.getTableInfo()), null);
        }
    }

    protected void writeTableData(StudyExportContext ctx, VirtualFile vf, TableInfo table, List<ColumnInfo> columns,
                                @Nullable ContainerFilter containerFilter) throws SQLException, IOException
    {
        // Write each table as a separate .tsv
        if (table != null)
        {
            if (containerFilter != null)
            {
                if (table instanceof ContainerFilterable)
                {
                    ((ContainerFilterable)table).setContainerFilter(containerFilter);
                }
            }
//            createExtraForeignKeyColumns(table, columns);                             // TODO: QueryService gets unhappy and seems unnecessary
            Results rs = QueryService.get().select(table, columns, null, null);
            writeResultsToTSV(rs, vf, getFileName(table));
        }
    }

    protected String getFileName(TableInfo tableInfo)
    {
        return tableInfo.getName().toLowerCase() + ".tsv";
    }

    protected void writeResultsToTSV(Results rs, VirtualFile vf, String fileName) throws SQLException, IOException
    {
        TSVGridWriter tsvWriter = new TSVGridWriter(rs);
        tsvWriter.setApplyFormats(false);
        tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
        PrintWriter out = vf.getPrintWriter(fileName);
        tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
    }

    /**
     * Returns the default visible columns for a table but ignores the standard columns
     */
    protected List<ColumnInfo> getDefaultColumns(TableInfo tableInfo)
    {
        List<ColumnInfo> columns = new ArrayList<>();

        for (ColumnInfo col : tableInfo.getColumns())
        {
            if (FieldKey.fromParts("Container").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("Created").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("CreatedBy").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("Modified").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("ModifiedBy").equals(col.getFieldKey()))
                continue;

            columns.add(col);
        }
        return columns;
    }

    protected void writeTableInfos(StudyExportContext ctx, VirtualFile vf, Set<String> tableNames, StudyQuerySchema schema, StudyQuerySchema projectSchema, String schemaFileName) throws IOException
    {
        // Create dataset metadata file
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        TablesType tablesXml = tablesDoc.addNewTables();

        for (String tableName : tableNames)
        {
            StudyQuerySchema.TablePackage tablePackage = schema.getTablePackage(ctx, projectSchema, tableName);
            TableInfo tinfo = tablePackage.getTableInfo();
            TableType tableXml = tablesXml.addNewTable();

            Domain domain = tinfo.getDomain();
            List<ColumnInfo> columns = new ArrayList();
            Map<String, DomainProperty> propertyMap = new CaseInsensitiveHashMap<>();

            for (DomainProperty prop : domain.getProperties())
                propertyMap.put(prop.getName(), prop);

            for (ColumnInfo col : tinfo.getColumns())
            {
                if (!col.isKeyField() && propertyMap.containsKey(col.getName()))
                    columns.add(col);
            }
            TableInfoWriter writer = new TreatementTableWriter(tablePackage.getContainer(), tinfo, domain, columns);        // TODO: container correct?
            writer.writeTable(tableXml);
        }
        vf.saveXmlBean(schemaFileName, tablesDoc);
    }

    private static class TreatementTableWriter extends TableInfoWriter
    {
        private final Map<String, DomainProperty> _properties = new CaseInsensitiveHashMap<>();
        private Domain _domain;

        protected TreatementTableWriter(Container c, TableInfo ti, Domain domain, Collection<ColumnInfo> columns)
        {
            super(c, ti, columns);
            _domain = domain;

            for (DomainProperty prop : _domain.getProperties())
                _properties.put(prop.getName(), prop);
        }

        @Override  // No reason to ever export list PropertyURIs
        protected String getPropertyURI(ColumnInfo column)
        {
            return null;
        }

        @Override
        public void writeColumn(ColumnInfo column, ColumnType columnXml)
        {
            super.writeColumn(column, columnXml);

            // Since the specimen tables only return a SchemaTableInfo, the column names will be decapitalized,
            // to preserve casing we defer to the DomainProperty names. This is mostly cosmetic
            if (_properties.containsKey(column.getName()))
            {
                DomainProperty dp = _properties.get(column.getName());
                if (dp.getName() != null)
                    columnXml.setColumnName(dp.getName());
            }
        }
    }

    public static void createExtraForeignKeyColumns(TableInfo table, Collection<ColumnInfo> columns)
    {
        // Add extra column for lookup from study table to project table, based on numeric key
        if (!StudyQuerySchema.isDataspaceProjectTable(table.getName()))
        {
            List<FieldKey> fieldKeys = new ArrayList<>();
            for (ColumnInfo column : columns)
            {
                if (null != column.getFk())
                {
                    ForeignKey fk = column.getFk();
                    String lookupColumnName = fk.getLookupColumnName();
                    TableInfo lookupTableInfo = fk.getLookupTableInfo();
                    if (null != lookupTableInfo && StudyQuerySchema.isDataspaceProjectTable(lookupTableInfo.getName()) &&
                            null != lookupColumnName && lookupTableInfo.getColumn(lookupColumnName).getJdbcType().isNumeric())
                    {
                        // Add extra column to tsv for numeric foreign key
                        String displayName = fk.getLookupDisplayName();
                        if (null == displayName)
                            displayName = lookupTableInfo.getTitleColumn();
                        fieldKeys.add(FieldKey.fromParts(column.getName(), displayName));   // TODO: push into ForeignKey
                    }
                }
            }
            Map<FieldKey, ColumnInfo> newColumnMap = QueryService.get().getColumns(table, fieldKeys);
            columns.addAll(newColumnMap.values());
        }
    }
}
