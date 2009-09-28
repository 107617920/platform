/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.experiment.list;

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.QueryService;
import org.labkey.api.study.ExternalStudyWriter;
import org.labkey.api.study.ExternalStudyWriterFactory;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/*
* User: adam
* Date: Aug 25, 2009
* Time: 10:11:16 AM
*/
public class ListWriter implements ExternalStudyWriter
{
    private static final Logger LOG = Logger.getLogger(ListWriter.class);
    private static final String DEFAULT_DIRECTORY = "lists";
    public static final String SCHEMA_FILENAME = "lists.xml";

    public String getSelectionText()
    {
        return "Lists";
    }

    public void write(Study study, StudyContext ctx, VirtualFile root) throws Exception
    {
        Container c = ctx.getContainer();
        Map<String, ListDefinition> lists = ListService.get().getLists(c);

        if (!lists.isEmpty())
        {
            ctx.getStudyXml().addNewLists().setDir(DEFAULT_DIRECTORY);
            VirtualFile listsDir = root.getDir(DEFAULT_DIRECTORY);

            // Create meta data doc
            TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
            TablesDocument.Tables tablesXml = tablesDoc.addNewTables();

            for (Map.Entry<String, ListDefinition> entry : lists.entrySet())
            {
                ListDefinition def = entry.getValue();
                TableInfo tinfo = def.getTable(ctx.getUser());

                // Write meta data
                TableType tableXml = tablesXml.addNewTable();
                ListTableInfoWriter xmlWriter = new ListTableInfoWriter(tinfo, def, getColumnsToExport(tinfo, true));
                xmlWriter.writeTable(tableXml);

                // Write data
                Collection<ColumnInfo> columns = getColumnsToExport(tinfo, false);

                if (!columns.isEmpty())
                {
                    ResultSet rs = QueryService.get().select(tinfo, columns, null, null);
                    TSVGridWriter tsvWriter = new TSVGridWriter(rs);
                    tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
                    PrintWriter out = listsDir.getPrintWriter(def.getName() + ".tsv");
                    tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
                }
            }

            listsDir.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
        }
    }

    private Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo, boolean metadata)
    {
        Collection<ColumnInfo> columns = new LinkedList<ColumnInfo>();

        for (ColumnInfo column : tinfo.getColumns())
            if (column.isUserEditable() || (metadata && column.isKeyField()))
                columns.add(column);

        return columns;
    }

    private static class ListTableInfoWriter extends TableInfoWriter
    {
        private final ListDefinition _def;
        private final Map<String, DomainProperty> _properties = new HashMap<String, DomainProperty>();

        protected ListTableInfoWriter(TableInfo ti, ListDefinition def, Collection<ColumnInfo> columns)
        {
            super(ti, columns, null);
            _def = def;

            for (DomainProperty prop : _def.getDomain().getProperties())
                _properties.put(prop.getName(), prop);
        }

        @Override
        public void writeTable(TableType tableXml)
        {
            super.writeTable(tableXml);
            tableXml.setPkColumnName(_def.getKeyName());
        }

        @Override
        public void writeColumn(ColumnInfo column, ColumnType columnXml)
        {
            super.writeColumn(column, columnXml);

            String columnName = column.getName();

            if (columnName.equals(_def.getKeyName()))
            {
                columnXml.setIsKeyField(true);

                if (column.isAutoIncrement())
                    columnXml.setIsAutoInc(true);
            }
            else
            {
                PropertyType propType = _properties.get(columnName).getPropertyDescriptor().getPropertyType();

                if (propType == PropertyType.ATTACHMENT)
                    columnXml.setDatatype(propType.getXmlName());
            }
        }
    }

    public static class Factory implements ExternalStudyWriterFactory
    {
        public ExternalStudyWriter create()
        {
            return new ListWriter();
        }
    }
}
