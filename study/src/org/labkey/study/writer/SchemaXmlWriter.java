/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.assay.SpecimenForeignKey;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetTableImpl;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.StudyDocument;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: May 27, 2009
 * Time: 11:12:33 AM
 */
public class SchemaXmlWriter implements Writer<List<DataSetDefinition>, ImportContext<StudyDocument.Study>>
{
    public static final String SCHEMA_FILENAME = "datasets_metadata.xml";

    private final String _defaultDateFormat;
    private final Set<String> _candidatePropertyURIs = new HashSet<String>();   // Allows nulls

    public SchemaXmlWriter(String defaultDateFormat)
    {
        _defaultDateFormat = defaultDateFormat;

        // We export only the standard study propertyURIs and the SystemProperty propertyURIs (special EHR properties,
        // etc.); see #12742.  We could have a registration mechanism for this... but this seems good enough for now.
        for (PropertyDescriptor pd : DataSetDefinition.getStandardPropertiesMap().values())
            _candidatePropertyURIs.add(pd.getPropertyURI());

        for (PropertyDescriptor pd: SystemProperty.getProperties())
            _candidatePropertyURIs.add(pd.getPropertyURI());
    }

    public String getSelectionText()
    {
        return "Dataset Schema Description";
    }

    public void write(List<DataSetDefinition> definitions, ImportContext<StudyDocument.Study> ctx, VirtualFile vf) throws IOException
    {
        // Create dataset metadata file
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        TablesType tablesXml = tablesDoc.addNewTables();

        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

        for (DataSetDefinition def : definitions)
        {
            TableInfo ti = schema.getTable(def.getName());
            TableType tableXml = tablesXml.addNewTable();
            DatasetTableInfoWriter w = new DatasetTableInfoWriter(ti, def, _defaultDateFormat, ctx.isRemoveProtected());
            w.writeTable(tableXml);
        }

        vf.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
    }


    private class DatasetTableInfoWriter extends TableInfoWriter
    {
        private final DataSetDefinition _def;

        private DatasetTableInfoWriter(TableInfo ti, DataSetDefinition def, String defaultDateFormat, boolean removeProtected)
        {
            super(ti, DatasetWriter.getColumnsToExport(ti, def, true, removeProtected), defaultDateFormat);
            _def = def;
        }

        @Override
        public void writeTable(TableType tableXml)
        {
            super.writeTable(tableXml);

            tableXml.setTableName(_def.getName());  // Use dataset name, not temp table name
            if (null != _def.getLabel())
                tableXml.setTableTitle(_def.getLabel());
            if (null != _def.getDescription())
                tableXml.setDescription(_def.getDescription());
        }

        @Override
        public void writeColumn(ColumnInfo column, ColumnType columnXml)
        {
            super.writeColumn(column, columnXml);

            if (column.getFk() instanceof SpecimenForeignKey)
            {
                // SpecimenForeignKey is a special FK implementation that won't be wired up correctly at import time, so
                // exclude it from the export
                columnXml.unsetFk();
            }
            else if (column.getFk() != null)
            {
                if (column.getFk().getLookupTableInfo() instanceof ExpRunTable)
                {
                    // We're not exporting assay runs, so it's useless to have a lookup that's looking for them
                    // after the dataset has been imported
                    columnXml.unsetFk();
                }
            }

            if (column.getName() != null && column.getName().contains(".") && columnXml.isSetNullable() && !columnXml.getNullable())
            {
                // Assay datasets contain columns that are flattened from lookups. The lookup target may not allow
                // nulls in its own table, but if the parent column is nullable, the joined result might be null.
                columnXml.unsetNullable();
            }
            
            if (column.getURL() != null && column.getURL().getSource().startsWith("/assay/assayDetailRedirect.view?"))
            {
                // We don't include assay runs in study exports, so the link target won't be available in the target system
                columnXml.unsetUrl();
            }

            if (column.isUnselectable())
            {
                // Still export the underlying value, but since we don't support unselectable as an attribute in
                // export/import, do the next best thing and just hide the column
                columnXml.setIsHidden(true);
            }

            String columnName = column.getName();
            if (columnName.equals(_def.getKeyPropertyName()))
            {
                columnXml.setIsKeyField(true);

                if (_def.getKeyManagementType() == DataSet.KeyManagementType.RowId)
                    columnXml.setIsAutoInc(true);
                else if (_def.getKeyManagementType() == DataSet.KeyManagementType.GUID)
                    columnXml.setDatatype("entityid");
            }
        }

        @Override
        protected String getPropertyURI(ColumnInfo column)
        {
            String propertyURI = column.getPropertyURI();

            // Only round-trip the special PropertyURIs.  See #12742.
            if (_candidatePropertyURIs.contains(propertyURI))
                return propertyURI;
            else
                return null;
        }

        @Override
        protected String getConceptURI(ColumnInfo column)
        {
            String conceptURI = super.getConceptURI(column);

            if (null != conceptURI)
                return conceptURI;

            // Proper ConceptURI support is not implemented, but we use the 'VisitDate' concept in this isolated spot
            // as a marker to indicate which dataset column should be tagged as the visit date column during import:
            if (column.getName().equalsIgnoreCase(_def.getVisitDateColumnName()))
                return DataSetDefinition.getVisitDateURI();
            return null;
        }
    }
}