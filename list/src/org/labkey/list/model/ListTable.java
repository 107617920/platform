/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MVDisplayColumnFactory;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.Pump;
import org.labkey.api.etl.TableInsertDataIterator;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.list.view.AttachmentDisplayColumn;
import org.labkey.list.view.ListController;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListTable extends FilteredTable implements UpdateableTableInfo
{
    static public TableInfo getIndexTable(ListDefinition.KeyType keyType)
    {
        switch (keyType)
        {
            case Integer:
            case AutoIncrementInteger:
                return ListManager.get().getTinfoIndexInteger();
            case Varchar:
                return ListManager.get().getTinfoIndexVarchar();
            default:
                return null;
        }
    }

    private ListDefinition _list;

    public ListTable(User user, ListDefinition listDef)
    {
        super(getIndexTable(listDef.getKeyType()));
        setName(listDef.getName());
        setDescription(listDef.getDescription());
        _list = listDef;
        addCondition(getRealTable().getColumn("ListId"), listDef.getListId());

        // All columns visible by default, except for auto-increment integer
        List<FieldKey> defaultVisible = new ArrayList<FieldKey>();
        ColumnInfo colKey = wrapColumn(listDef.getKeyName(), getRealTable().getColumn("Key"));
        colKey.setKeyField(true);
        colKey.setInputType("text");
        colKey.setInputLength(-1);
        colKey.setWidth("180");
        if (listDef.getKeyType().equals(ListDefinition.KeyType.AutoIncrementInteger))
        {
            colKey.setUserEditable(false);
            colKey.setAutoIncrement(true);
        }
        else
        {
            defaultVisible.add(FieldKey.fromParts(colKey.getName()));
        }
        addColumn(colKey);
        ColumnInfo colObjectId = wrapColumn(getRealTable().getColumn("ObjectId"));
        for (DomainProperty property : listDef.getDomain().getProperties())
        {
            PropertyColumn column = new PropertyColumn(property.getPropertyDescriptor(), colObjectId, null, user);

            if (property.getName().equalsIgnoreCase(colKey.getName()))
            {
                colKey.setExtraAttributesFrom(column);
                continue;
            }

            column.setParentIsObjectId(true);
            column.setReadOnly(false);
            column.setScale(property.getScale()); // UNDONE: PropertyDescriptor does not have getScale() so have to set here, move to PropertyColumn
            safeAddColumn(column);
            if (property.isMvEnabled())
            {
                MVDisplayColumnFactory.addMvColumns(this, column, property, colObjectId, user);
            }
            if (!property.isHidden())
            {
                defaultVisible.add(FieldKey.fromParts(column.getName()));
            }

            if (property.getPropertyDescriptor().getPropertyType() == PropertyType.MULTI_LINE)
            {
                column.setDisplayColumnFactory(new DisplayColumnFactory() {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        DataColumn dc = new DataColumn(colInfo);
                        dc.setPreserveNewlines(true);
                        return dc;
                    }
                });
            }
            else if (property.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
            {
                column.setDisplayColumnFactory(new DisplayColumnFactory() {
                    public DisplayColumn createRenderer(final ColumnInfo colInfo)
                    {
                        return new AttachmentDisplayColumn(colInfo);
                    }
                });
            }
        }

        setDefaultVisibleColumns(defaultVisible);

        boolean auto = (null == listDef.getTitleColumn());
        setTitleColumn(findTitleColumn(listDef, colKey), auto);

        // Make EntityId column available so AttachmentDisplayColumn can request it as a dependency
        // Do this last so the column doesn't get selected as title column, etc.
        ColumnInfo colEntityId = wrapColumn(getRealTable().getColumn("EntityId"));
        addColumn(colEntityId);

        DetailsURL gridURL = new DetailsURL(_list.urlShowData(), Collections.<String, String>emptyMap());
        setGridURL(gridURL);

        DetailsURL insertURL = new DetailsURL(_list.urlFor(ListController.InsertAction.class), Collections.<String, String>emptyMap());
        setInsertURL(insertURL);

        DetailsURL updateURL = new DetailsURL(_list.urlUpdate(null, null), Collections.singletonMap("pk", _list.getKeyName()));
        setUpdateURL(updateURL);

        DetailsURL detailsURL = new DetailsURL(_list.urlDetails(null), Collections.singletonMap("pk", _list.getKeyName()));
        setDetailsURL(detailsURL);

        // TODO: I don't see the point in using DetailsURL for constant URLs (insert, import, grid)
        if (!listDef.getAllowUpload())
            setImportURL(LINK_DISABLER);
        else
        {
            ActionURL importURL = listDef.urlFor(ListController.UploadListItemsAction.class);
            setImportURL(new DetailsURL(importURL, Collections.singletonMap("pk", _list.getKeyName())));
        }
    }
    

    @Override
    public Domain getDomain()
    {
        if (null != _list)
            return _list.getDomain();
        return null;
    }

    @Override
    public boolean hasContainerContext()
    {
        return null != _list && null != _list.getContainer();
    }

    @Override
    public Container getContainer(Map m)
    {
        return _list.getContainer();
    }

    private String findTitleColumn(ListDefinition listDef, ColumnInfo colKey)
    {
        if (listDef.getTitleColumn() != null)
        {
            ColumnInfo titleColumn = getColumn(listDef.getTitleColumn());

            if (titleColumn != null)
                return titleColumn.getName();
        }

        // Title column setting is <AUTO> -- select the first string column that's not a lookup (see #9114)
        for (ColumnInfo column : getColumns())
            if (column.isStringType() && null == column.getFk())
                return column.getName();

        // No non-FK string columns -- fall back to pk (see issue #5452)
        return colKey.getName();
    }

    public ListDefinition getList()
    {
        return _list;
    }

    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        return _list.getContainer().hasPermission(user, perm);
    }

    public String getPublicName()
    {
        return _list.getName();
    }

    public String getPublicSchemaName()
    {
        return ListSchema.NAME;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new ListQueryUpdateService(this, getList());
    }

    // UpdateableTableInfo

    @Override
    public boolean insertSupported()
    {
        return true;
    }

    @Override
    public boolean updateSupported()
    {
        return false;
    }

    @Override
    public boolean deleteSupported()
    {
        return false;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        return getRealTable();
    }

    public ObjectUriType getObjectUriType()
    {
        return ObjectUriType.generateUrn;
    }

    @Override
    public String getObjectURIColumnName()
    {
        return null;
    }

    @Override
    public String getObjectIdColumnName()
    {
        return "objectid";
    }

    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        if (!_list.getKeyName().isEmpty() && !_list.getKeyName().equalsIgnoreCase("key"))
        {
            CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<String>();
            m.put("key", _list.getKeyName());
            return m;
        }
        return null;
    }

    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        if (!_list.getKeyName().isEmpty())
            return new CaseInsensitiveHashSet(_list.getKeyName());
        return null;
    }

    @Override
    public int persistRows(DataIterator data, BatchValidationException errors)
    {
        TableInsertDataIterator insert = TableInsertDataIterator.create(data, this, errors);
        new Pump(insert, errors).run();
        return insert.getExecuteCount();
    }

    @Override
    public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
    {
        return Table.insertStatement(conn, this, getContainer(), user, false, true);
    }


    @Override
    public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
    {
        throw new UnsupportedOperationException();
    }
}
