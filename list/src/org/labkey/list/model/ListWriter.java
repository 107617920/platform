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

package org.labkey.list.model;

import org.apache.log4j.Logger;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.list.view.ListItemAttachmentParent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* User: adam
* Date: Aug 25, 2009
* Time: 10:11:16 AM
*/
public class ListWriter
{
    private static final Logger LOG = Logger.getLogger(ListWriter.class);
    private static final String SCHEMA_FILENAME = "lists.xml";

    public boolean write(Container c, User user, VirtualFile listsDir) throws Exception
    {
        Map<String, ListDefinition> lists = ListService.get().getLists(c);

        if (!lists.isEmpty())
        {
            // Create meta data doc
            TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
            TablesType tablesXml = tablesDoc.addNewTables();

            // Insert standard comment explaining where the data lives, who exported it, and when
            XmlBeansUtil.addStandardExportComment(tablesXml, c, user);

            for (Map.Entry<String, ListDefinition> entry : lists.entrySet())
            {
                ListDefinition def = entry.getValue();
                TableInfo ti = def.getTable(user);

                // Write meta data
                TableType tableXml = tablesXml.addNewTable();
                ListTableInfoWriter xmlWriter = new ListTableInfoWriter(ti, def, getColumnsToExport(ti, true));
                xmlWriter.writeTable(tableXml);

                // Write data
                Collection<ColumnInfo> columns = getColumnsToExport(ti, false);

                if (!columns.isEmpty())
                {
                    List<DisplayColumn> displayColumns = new LinkedList<DisplayColumn>();

                    for (ColumnInfo col : columns)
                        displayColumns.add(new ListExportDataColumn(col));

                    // Sort the data rows by PK, #11261
                    Sort sort = ti.getPkColumnNames().size() != 1 ? null : new Sort(ti.getPkColumnNames().get(0));

                    Results rs = QueryService.get().select(ti, columns, null, sort);
                    TSVGridWriter tsvWriter = new TSVGridWriter(rs, displayColumns);
                    tsvWriter.setApplyFormats(false);
                    tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
                    PrintWriter out = listsDir.getPrintWriter(def.getName() + ".tsv");
                    tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet

                    writeAttachments(ti, def, c, listsDir);
                }
            }

            listsDir.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
            return true;
        }
        else
        {
            return false;
        }
    }

    private void writeAttachments(TableInfo ti, ListDefinition def, Container c, VirtualFile listsDir) throws SQLException, IOException
    {
        List<ColumnInfo> attachmentColumns = new ArrayList<ColumnInfo>();

        for (DomainProperty prop : def.getDomain().getProperties())
            if (prop.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
                attachmentColumns.add(ti.getColumn(prop.getName()));

        if (!attachmentColumns.isEmpty())
        {
            VirtualFile listDir = listsDir.getDir(def.getName());
            Map<String, FileNameUniquifier> uniquifiers = new HashMap<String, FileNameUniquifier>();

            for (ColumnInfo attachmentColumn : attachmentColumns)
                uniquifiers.put(attachmentColumn.getName(), new FileNameUniquifier());

            List<ColumnInfo> selectColumns = new ArrayList<ColumnInfo>(attachmentColumns);
            selectColumns.add(0, ti.getColumn("EntityId"));

            ResultSet rs = null; 

            try
            {
                rs = QueryService.get().select(ti, selectColumns, null, null);

                while(rs.next())
                {
                    String entityId = rs.getString(1);
                    AttachmentParent listItemParent = new ListItemAttachmentParent(entityId, c);

                    rs.getString(1);
                    int sqlColumn = 2;

                    for (ColumnInfo attachmentColumn : attachmentColumns)
                    {
                        String filename = rs.getString(sqlColumn++);

                        // Item might not have an attachment in this column
                        if (null == filename)
                            continue;

                        String columnName = attachmentColumn.getName();
                        VirtualFile columnDir = listDir.getDir(columnName);

                        InputStream is = null;
                        OutputStream os = null;

                        try
                        {
                            is = AttachmentService.get().getInputStream(listItemParent, filename);
                            FileNameUniquifier uniquifier = uniquifiers.get(columnName);
                            os = columnDir.getOutputStream(uniquifier.uniquify(filename));
                            FileUtil.copyData(is, os);
                        }
                        catch (FileNotFoundException e)
                        {
                            // Shouldn't happen... but just skip this file in production if it does
                            assert false;
                        }
                        finally
                        {
                            if (null != is)
                                is.close();
                            if (null != os)
                                os.close();
                        }
                    }
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }
    }

    private Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo, boolean metaData)
    {
        Collection<ColumnInfo> columns = new LinkedHashSet<ColumnInfo>();
        Set<ColumnInfo> pks = new HashSet<ColumnInfo>(tinfo.getPkColumns());

        assert pks.size() == 1;

        for (ColumnInfo column : tinfo.getColumns())
        {
            /*
                We export:

                - All user-editable columns (meta data & values)
                - All primary keys, including the values of auto-increment key columns (meta data & values)
                - Other key columns (meta data only) 
             */
            if (column.isUserEditable() || pks.contains(column) || (metaData && column.isKeyField()))
            {
                columns.add(column);

                // If the column is MV enabled, export the data in the indicator column as well
                if (!metaData && column.isMvEnabled())
                    columns.add(tinfo.getColumn(column.getMvColumnName()));
            }
        }

        return columns;
    }


    // We just want the underlying value, not the lookup
    private static class ListExportDataColumn extends DataColumn
    {
        private ListExportDataColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }
    }

    private static class ListTableInfoWriter extends TableInfoWriter
    {
        private final ListDefinition _def;
        private final Map<String, DomainProperty> _properties = new HashMap<String, DomainProperty>();
        private Domain _domain;

        protected ListTableInfoWriter(TableInfo ti, ListDefinition def, Collection<ColumnInfo> columns)
        {
            super(ti, columns, null);
            _def = def;
            _domain = _def.getDomain();

            for (DomainProperty prop : _domain.getProperties())
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

        @Override  // TODO: Update this to match Dataset version?
        protected String getPropertyURI(ColumnInfo column)
        {
            String propertyURI = column.getPropertyURI();
            if (propertyURI != null && !propertyURI.startsWith(_domain.getTypeURI()) && !propertyURI.startsWith(ColumnInfo.DEFAULT_PROPERTY_URI_PREFIX))
                return propertyURI;

            return null;
        }
    }
}
