/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.*;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.flag.FlagColumnRenderer;
import org.labkey.api.exp.flag.FlagForeignKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract public class ExpTableImpl<C extends Enum> extends FilteredTable implements ExpTable<C>
{
    protected final UserSchema _schema;
    private final ExpObjectImpl _objectType;
    private Set<Class<? extends Permission>> _allowablePermissions = new HashSet<Class<? extends Permission>>();
    private Domain _domain;

    protected ExpTableImpl(String name, TableInfo rootTable, UserSchema schema, ExpObjectImpl objectType)
    {
        super(rootTable, schema.getContainer());
        _objectType = objectType;
        setName(name);
        _schema = schema;
        _allowablePermissions.add(DeletePermission.class);
        _allowablePermissions.add(ReadPermission.class);
    }

    public void addAllowablePermission(Class<? extends Permission> permission)
    {
        _allowablePermissions.add(permission);
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null && "Container".equalsIgnoreCase(name))
        {
            return getColumn("Folder");
        }
        for (ColumnInfo columnInfo : getColumns())
        {
            if (name.equalsIgnoreCase(columnInfo.getLabel()))
            {
                return columnInfo;
            }
        }
        return result;
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return new FieldKey(null, ExpMaterialTable.Column.Folder.toString());
    }

    protected ColumnInfo addContainerColumn(C containerCol, ActionURL url)
    {
        ColumnInfo result = addColumn(containerCol);
        ContainerForeignKey.initColumn(result, _schema, url);
        return result;
    }

    final public ColumnInfo addColumn(C column)
    {
        return addColumn(column.toString(), column);
    }

    final public ColumnInfo addColumn(String alias, C column)
    {
        ColumnInfo ret = createColumn(alias, column);
        addColumn(ret);
        return ret;
    }

    public ColumnInfo getColumn(C column)
    {
        for (ColumnInfo info : getColumns())
        {
            if (info instanceof ExprColumn && info.getAlias().equals(column.toString()))
            {
                return info;
            }
        }
        return null;
    }

    protected ColumnInfo doAdd(ColumnInfo column)
    {
        addColumn(column);
        return column;
    }

    public ColumnInfo createPropertyColumn(String name)
    {
        return wrapColumn(name, getLSIDColumn());
    }

    public ColumnInfo createUserColumn(String name, ColumnInfo userIdColumn)
    {
        ColumnInfo ret = wrapColumn(name, userIdColumn);
        ret.setFk(new UserIdForeignKey());
        ret.setShownInInsertView(false);
        ret.setShownInUpdateView(false);
        ret.setUserEditable(false);
        return ret;
    }

    public String urlFlag(boolean flagged)
    {
        assert _objectType != null : "No ExpObject configured for ExpTable type: " + getClass();
        return _objectType.urlFlag(flagged);
    }

    protected ColumnInfo getLSIDColumn()
    {
        return _rootTable.getColumn("LSID");
    }

    // if you change this, see similiar AssayResultTable.createFlagColumn or TSVAssayProvider.createFlagColumn()
    protected ColumnInfo createFlagColumn(String alias)
    {
        ColumnInfo ret = wrapColumn(alias, getLSIDColumn());
        ret.setFk(new FlagForeignKey(urlFlag(true), urlFlag(false), _schema.getContainer(), _schema.getUser()));
        ret.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new FlagColumnRenderer(colInfo);
            }
        });
        ret.setDescription("Contains a reference to a user-editable comment about this row");
        ret.setNullable(true);
        ret.setInputType("text");
        return ret;
    }

    public void addRowIdCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("RowId ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    public void addLSIDCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("LSID ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        if (getUpdateService() != null)
            return _allowablePermissions.contains(perm) && _schema.getContainer().hasPermission(user, perm);
        return false;
    }

    /**
     * Add columns directly to the table itself, and optionally also as a single column that is a FK to the full set of properties
     * @param domain the domain from which to add all of the properties
     * @param legacyName if non-null, the name of a hidden node to be added as a FK for backwards compatibility
     */
    public ColumnInfo addColumns(Domain domain, String legacyName)
    {
        ColumnInfo colProperty = null;
        if (legacyName != null && domain.getProperties().length > 0)
        {
            colProperty = wrapColumn(legacyName, getLSIDColumn());
            colProperty.setFk(new PropertyForeignKey(domain, _schema));
            // Hide because the preferred way to get to these values is to add them directly to the table, instead of having
            // them under the legacyName node
            colProperty.setHidden(true);
            colProperty.setUserEditable(false);
            colProperty.setIsUnselectable(true);
            addColumn(colProperty);
        }

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>(getDefaultVisibleColumns());
        for (DomainProperty dp : domain.getProperties())
        {
            PropertyDescriptor pd = dp.getPropertyDescriptor();
            ColumnInfo propColumn = new PropertyColumn(pd, getColumn("LSID"), getContainer(), _schema.getUser(), false);
            if (getColumn(propColumn.getName()) == null)
            {
                addColumn(propColumn);
                if (!propColumn.isHidden())
                {
                    visibleColumns.add(FieldKey.fromParts(pd.getName()));
                }
            }
        }
        setDefaultVisibleColumns(visibleColumns);
        return colProperty;
    }

    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    public void setDomain(Domain domain)
    {
        assert _domain == null;
        _domain = domain;
    }

    public ExpSchema getExpSchema()
    {
        if (_schema instanceof ExpSchema)
        {
            return (ExpSchema)_schema;
        }
        ExpSchema schema = new ExpSchema(_schema.getUser(), _schema.getContainer());
        schema.setContainerFilter(getContainerFilter());
        return schema;
    }

    @Override
    public String getPublicSchemaName()
    {
        return _schema.getSchemaName();
    }

}
