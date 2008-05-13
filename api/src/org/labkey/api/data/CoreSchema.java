/*
 * Copyright (c) 2005-2007 Fred Hutchinson Cancer Research Center
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

/**
 * User: arauch
 * Date: Sep 24, 2005
 * Time: 10:46:35 PM
 */
public class CoreSchema
{
    private static CoreSchema instance = null;
    private static final String SCHEMA_NAME = "core";

    public static CoreSchema getInstance()
    {
        if (null == instance)
            instance = new CoreSchema();

        return instance;
    }

    private CoreSchema()
    {
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoContainers()
    {
        return getSchema().getTable("Containers");
    }

    public TableInfo getTableInfoPrincipals()
    {
        return getSchema().getTable("Principals");
    }

    public TableInfo getTableInfoMembers()
    {
        return getSchema().getTable("Members");
    }

    public TableInfo getTableInfoACLs()
    {
        return getSchema().getTable("ACLs");
    }

    public TableInfo getTableInfoSqlScripts()
    {
        return getSchema().getTable("SqlScripts");
    }

    public TableInfo getTableInfoModules()
    {
        return getSchema().getTable("Modules");
    }

    public TableInfo getTableInfoUsersData()
    {
        return getSchema().getTable("UsersData");
    }

    public TableInfo getTableInfoLogins()
    {
        return getSchema().getTable("Logins");
    }

    public TableInfo getTableInfoDocuments()
    {
        return getSchema().getTable("Documents");
    }

    public TableInfo getTableInfoUsers()
    {
        return getSchema().getTable("Users");
    }

    public TableInfo getTableInfoContacts()
    {
        return getSchema().getTable("Contacts");
    }

    public TableInfo getTableInfoUserHistory()
    {
        return getSchema().getTable("UserHistory");
    }

    public TableInfo getTableInfoContainerAliases()
    {
        return getSchema().getTable("ContainerAliases");
    }

    public TableInfo getMappedDirectories()
    {
        return getSchema().getTable("MappedDirectories");
    }
}
