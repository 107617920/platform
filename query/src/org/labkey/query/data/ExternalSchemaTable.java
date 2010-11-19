/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.query.data;

import org.labkey.api.data.*;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.data.xml.TableType;
import org.labkey.query.ExternalSchema;

import java.util.List;
import java.util.Map;

public class ExternalSchemaTable extends SimpleUserSchema.SimpleTable
{
    private Container _container;
    

    public ExternalSchemaTable(ExternalSchema schema, SchemaTableInfo table, TableType metadata)
    {
        super(schema, table);

        if (metadata != null)
        {
            loadFromXML(schema, metadata, null);
        }
    }

    @Override
    public ExternalSchema getUserSchema()
    {
        return (ExternalSchema)super.getUserSchema();
    }

    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        List<ColumnInfo> columns = getPkColumns();
        return (super.hasPermission(user, perm) && getUserSchema().areTablesEditable() && columns.size() > 0);
    }

    public void setContainer(String containerId)
    {
        if (containerId == null)
            return;

        ColumnInfo colContainer = getRealTable().getColumn("container");

        if (colContainer != null)
        {
            getColumn("container").setReadOnly(true);
            addCondition(colContainer, containerId);
            _container = ContainerManager.getForId(containerId);
        }
    }

    @Override
    public boolean hasContainerContext()
    {
        ColumnInfo colContainer = getRealTable().getColumn("container");
        return null == colContainer || null != _container;
    }
    

    @Override
    public Container getContainer(Map context)
    {
        assert null == getRealTable().getColumn("container") || null != _container;
        ExternalSchema s = getUserSchema();
        return null != _container ? _container : s.getContainer();
    }


    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (TableInfo.TABLE_TYPE_TABLE != table.getTableType())
            return null;
        if (null == table.getPkColumnNames() || table.getPkColumnNames().size() == 0)
            throw new RuntimeException("The table '" + getName() + "' does not have a primary key defined, and thus cannot be updated reliably. Please define a primary key for this table before attempting an update.");

        return new DefaultQueryUpdateService(this, table);
    }
}
