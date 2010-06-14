/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Type;
import org.labkey.api.util.DateUtil;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;

/*
* User: adam
* Date: Sep 24, 2009
* Time: 1:21:59 PM
*/
public class TableInfoWriter
{
    private final TableInfo _ti;
    private final Collection<ColumnInfo> _columns;
    private final String _defaultDateFormat;

    protected TableInfoWriter(TableInfo ti)
    {
        this(ti, ti.getColumns(), null);
    }

    protected TableInfoWriter(TableInfo ti, Collection<ColumnInfo> columns, String defaultDateFormat)
    {
        _ti = ti;
        _columns = columns;
        _defaultDateFormat = (null != defaultDateFormat ? defaultDateFormat : DateUtil.getStandardDateFormatString());
    }

    // Append a new table to the Tables document
    public void writeTable(TableType tableXml)
    {
        // Write metadata
        tableXml.setTableName(_ti.getName());
        tableXml.setTableDbType("TABLE");
        if (null != _ti.getDescription())
            tableXml.setDescription(_ti.getDescription());

        TableType.Columns columnsXml = tableXml.addNewColumns();

        for (ColumnInfo column : _columns)
        {
            ColumnType columnXml = columnsXml.addNewColumn();
            writeColumn(column, columnXml);
        }
    }

    public void writeColumn(ColumnInfo column, ColumnType columnXml)
    {
        String columnName = column.getName();
        columnXml.setColumnName(columnName);

        Class clazz = column.getJavaClass();
        Type t = Type.getTypeByClass(clazz);

        if (null == t)
            throw new IllegalStateException(columnName + " in table " + column.getParentTable().getName() + " has unknown java class " + clazz.getName());

        columnXml.setDatatype(t.getSqlTypeName());

        if (column.getInputType().equals("textarea"))
            columnXml.setInputType(column.getInputType());

        if (null != column.getLabel())
            columnXml.setColumnTitle(column.getLabel());

        if (null != column.getDescription())
            columnXml.setDescription(column.getDescription());

        String propertyURI = getPropertyURI(column);
        if (propertyURI != null)
            columnXml.setPropertyURI(propertyURI);

        String conceptURI = getConceptURI(column);
        if (conceptURI != null)
            columnXml.setConceptURI(conceptURI);

        if (!column.isNullable())
            columnXml.setNullable(false);

        if (column.isHidden())
            columnXml.setIsHidden(true);
        if (!column.isShownInInsertView())
            columnXml.setShownInInsertView(false);
        if (!column.isShownInUpdateView())
            columnXml.setShownInUpdateView(false);
        if (!column.isShownInDetailsView())
            columnXml.setShownInDetailsView(false);

        if (null != column.getURL())
            columnXml.setUrl(column.getURL().getSource());

        if (!column.getImportAliasesSet().isEmpty())
        {
            ColumnType.ImportAliases importAliasesXml = columnXml.addNewImportAliases();
            for (String importAlias : column.getImportAliasesSet())
            {
                importAliasesXml.addImportAlias(importAlias);
            }
        }

        String formatString = column.getFormat();

        // Write only if it's non-null (and in the case of dates, different from the global default)
        if (null != formatString && (!Date.class.isAssignableFrom(column.getJavaClass()) || !formatString.equals(_defaultDateFormat)))
            columnXml.setFormatString(formatString);

        if (column.isMvEnabled())
            columnXml.setIsMvEnabled(true);

        ForeignKey fk = column.getFk();

        if (null != fk && null != fk.getLookupColumnName())
        {
            ColumnType.Fk fkXml = columnXml.addNewFk();

            String fkContainerId = fk.getLookupContainerId();

            // Null means current container... which means don't set anything in the XML
            if (null != fkContainerId)
            {
                Container fkContainer = ContainerManager.getForId(fkContainerId);

                if (null != fkContainer)
                    fkXml.setFkFolderPath(fkContainer.getPath());
            }

            TableInfo tinfo = fk.getLookupTableInfo();
            fkXml.setFkDbSchema(tinfo.getPublicSchemaName());
            fkXml.setFkTable(tinfo.getPublicName());
            fkXml.setFkColumnName(fk.getLookupColumnName());
        }

        // TODO: Field validators?
        // TODO: Default values / Default value types
    }

    protected String getConceptURI(ColumnInfo column)
    {
        return column.getConceptURI();
    }

    /**
     * Get the propertyURI of the ColumnInfo or null if no uri should be written.
     * @param column
     * @return The propertyURI to be written or null.
     */
    @Nullable
    protected String getPropertyURI(ColumnInfo column)
    {
        String propertyURI = column.getPropertyURI();
        if (propertyURI != null && !propertyURI.startsWith(ColumnInfo.DEFAULT_PROPERTY_URI_PREFIX))
            return propertyURI;

        return null;
    }
}
