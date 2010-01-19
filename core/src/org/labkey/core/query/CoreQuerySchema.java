/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.core.query;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.core.user.UserController;
import org.labkey.core.workbook.WorkbooksTableInfo;

import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jul 16, 2008
 * Time: 4:11:34 PM
 */
public class CoreQuerySchema extends UserSchema
{
    private Set<Integer> _projectUserIds;

    public static final String USERS_TABLE_NAME = "Users";
    public static final String GROUPS_TABLE_NAME = "Groups";
    public static final String SITE_USERS_TABLE_NAME = "SiteUsers";
    public static final String PRINCIPALS_TABLE_NAME = "Principals";
    public static final String MEMBERS_TABLE_NAME = "Members";
    public static final String WORKBOOKS_TABLE_NAME = "Workbooks";
    public static final String SCHEMA_DESCR = "Contains data about the system users and groups.";

    public CoreQuerySchema(User user, Container c)
    {
        super("core", SCHEMA_DESCR, user, c, CoreSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        return PageFlowUtil.set(USERS_TABLE_NAME, SITE_USERS_TABLE_NAME, PRINCIPALS_TABLE_NAME, MEMBERS_TABLE_NAME, GROUPS_TABLE_NAME);
    }


    public TableInfo createTable(String name)
    {
        if (USERS_TABLE_NAME.equalsIgnoreCase(name))
            return getUsers();
        if (SITE_USERS_TABLE_NAME.equalsIgnoreCase(name))
            return getSiteUsers();
        if(PRINCIPALS_TABLE_NAME.equalsIgnoreCase(name))
            return getPrincipals();
        if(MEMBERS_TABLE_NAME.equalsIgnoreCase(name))
            return getMembers();
        if (GROUPS_TABLE_NAME.equalsIgnoreCase(name))
            return getGroups();
        if (WORKBOOKS_TABLE_NAME.equalsIgnoreCase(name))
            return getWorkbooks();
        return null;
    }

    public TableInfo getWorkbooks()
    {
        return new WorkbooksTableInfo(this);
    }

    public TableInfo getGroups()
    {
        TableInfo principalsBase = CoreSchema.getInstance().getTableInfoPrincipals();
        FilteredTable groups = new FilteredTable(principalsBase);

        //expose UserId, Name, Container, and Type
        ColumnInfo col = groups.wrapColumn(principalsBase.getColumn("UserId"));
        col.setKeyField(true);
        col.setReadOnly(true);
        groups.addColumn(col);

        col = groups.wrapColumn(principalsBase.getColumn("Name"));
        col.setReadOnly(true);
        groups.addColumn(col);

        col = groups.wrapColumn(principalsBase.getColumn("Type"));
        col.setReadOnly(true);
        groups.addColumn(col);

        col = groups.wrapColumn(principalsBase.getColumn("Container"));
        col.setReadOnly(true);
        groups.addColumn(col);

        List<FieldKey> defCols = new ArrayList<FieldKey>();
        defCols.add(FieldKey.fromParts("Name"));
        defCols.add(FieldKey.fromParts("Type"));
        defCols.add(FieldKey.fromParts("Container"));
        groups.setDefaultVisibleColumns(defCols);

        //filter for just groups
        groups.addCondition(new SQLFragment("Type IN ('g','r')"));
        
        //filter out inactive
        groups.addCondition(new SQLFragment("Active=?", true));

        //filter for container is null or container = current-container
        groups.addCondition(new SQLFragment("Container IS NULL or Container=?", getContainer().getProject()));

        //if guest add null filter
        if(getUser().isGuest())
            addNullSetFilter(groups);

        groups.setDescription("Contains all groups defined in the current project." +
        " The data in this table are available only to users who are signed-in (not guests). Guests will see no rows.");
        
        return groups;
    }

    public TableInfo getSiteUsers()
    {
        FilteredTable users = getUserTable();

        //only site admins are allowed to see all site users,
        //so if the user is not a site admin, add a filter that will
        //generate an empty set (CONSIDER: should we throw an exception here instead?)
        if(!getUser().isAdministrator())
            addNullSetFilter(users);
        users.setName("SiteUsers");
        users.setDescription("Contains all users who have accounts on the server regardless of whether they are members of the current project or not." +
        " The data in this table are available only to site administrators. All other users will see no rows.");

        return users;
    }

    public TableInfo getPrincipals()
    {
        TableInfo principalsBase = CoreSchema.getInstance().getTableInfoPrincipals();
        FilteredTable principals = new FilteredTable(principalsBase);

        //we expose userid, name and type via query
        ColumnInfo col = principals.wrapColumn(principalsBase.getColumn("UserId"));
        col.setKeyField(true);
        col.setHidden(true);
        col.setReadOnly(true);
        principals.addColumn(col);

        col = principals.wrapColumn(principalsBase.getColumn("Name"));
        col.setReadOnly(true);
        principals.addColumn(col);

        col = principals.wrapColumn(principalsBase.getColumn("Type"));
        col.setReadOnly(true);
        principals.addColumn(col);

        col = principals.wrapColumn(principalsBase.getColumn("Container"));
        col.setReadOnly(true);
        principals.addColumn(col);

        List<FieldKey> defCols = new ArrayList<FieldKey>();
        defCols.add(FieldKey.fromParts("Name"));
        defCols.add(FieldKey.fromParts("Type"));
        defCols.add(FieldKey.fromParts("Container"));
        principals.setDefaultVisibleColumns(defCols);

        //filter out inactive
        principals.addCondition(new SQLFragment("Active=?", true));

        //filter for container is null or container = current-container
        principals.addCondition(new SQLFragment("Container IS NULL or Container=?", getContainer().getProject()));

        //only users with admin perms in the container may see the principals
        if(!getContainer().hasPermission(getUser(), AdminPermission.class))
            addNullSetFilter(principals);

        principals.setDescription("Contains all principals (users and groups) who are members of the current project." +
        " The data in this table are available only to users with administrator permission in the current folder. All other users will see no rows.");

        return principals;
    }

    public TableInfo getMembers()
    {
        TableInfo membersBase = CoreSchema.getInstance().getTableInfoMembers();
        FilteredTable members = new FilteredTable(membersBase);

        ColumnInfo col = members.wrapColumn(membersBase.getColumn("UserId"));
        col.setKeyField(true);
        final boolean isSiteAdmin = getUser().isAdministrator();
        col.setFk(new LookupForeignKey("UserId", "DisplayName")
        {
            public TableInfo getLookupTableInfo()
            {
                return isSiteAdmin ? getSiteUsers() : getUsers();
            }
        });
        members.addColumn(col);

        col = members.wrapColumn(membersBase.getColumn("GroupId"));
        col.setKeyField(true);
        col.setFk(new LookupForeignKey("UserId", "Name")
        {
            public TableInfo getLookupTableInfo()
            {
                return getGroups();
            }
        });
        members.addColumn(col);

        //if user is guest, add a null-set filter
        if(getUser().isGuest())
            addNullSetFilter(members);
        else
        {
            Container container = getContainer();
            Container project = container.isRoot() ? container : container.getProject();
            CoreSchema coreSchema = CoreSchema.getInstance();

            members.addCondition(new SQLFragment("GroupId IN (SELECT UserId FROM " + coreSchema.getTableInfoPrincipals()
                    + " WHERE Container=? OR Container IS NULL)", project.getId()));
        }

        List<FieldKey> defCols = new ArrayList<FieldKey>();
        defCols.add(FieldKey.fromParts("UserId"));
        defCols.add(FieldKey.fromParts("GroupId"));
        members.setDefaultVisibleColumns(defCols);

        members.setDescription("Contains rows indicating which users are in which groups in the current project." +
        " The data in this talbe are available only to users who are signed in (not guests). Guests will see no rows.");

        return members;
    }

    public TableInfo getUsers()
    {
        if (getContainer().isRoot())
            return getSiteUsers();

        FilteredTable users = getUserTable();

        //if the user is a guest, add a filter to produce a null set
        if(getUser().isGuest())
            addNullSetFilter(users);
        else
        {
            if (_projectUserIds == null)
            {
                _projectUserIds = new HashSet<Integer>(org.labkey.api.security.SecurityManager.getFolderUserids(getContainer()));
                Group siteAdminGroup = org.labkey.api.security.SecurityManager.getGroup(Group.groupAdministrators);
                try
                {
                    for (User adminUser : org.labkey.api.security.SecurityManager.getGroupMembers(siteAdminGroup))
                    {
                        _projectUserIds.add(adminUser.getUserId());
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    throw new UnexpectedException(e);
                }
            }
            ColumnInfo userid = users.getRealTable().getColumn("userid");
            users.addInClause(userid, _projectUserIds);
        }

        users.setDescription("Contains all users who are members of the current project." +
        " The data in this table are available only to users who are signed-in (not guests). Guests will see no rows." +
        " All signed-in users will see the columns UserId, EntityId, DisplayName, Email, FirstName, LastName, Description, Created, Modified." +
        " Users with administrator permissions will also see the columns Phone, Mobile, Pager, IM, Active and LastLogin.");

        return users;
    }

    protected FilteredTable getUserTable()
    {
        TableInfo usersBase = CoreSchema.getInstance().getTableInfoUsers();
        FilteredTable users = new FilteredTable(usersBase);

        ColumnInfo userIdCol = users.addWrapColumn(usersBase.getColumn("UserId"));
        userIdCol.setKeyField(true);
        userIdCol.setReadOnly(true);

        ColumnInfo entityIdCol = users.addWrapColumn(usersBase.getColumn("EntityId"));
        entityIdCol.setHidden(true);

        ColumnInfo displayNameCol = users.addWrapColumn(usersBase.getColumn("DisplayName"));
        displayNameCol.setReadOnly(true);
        users.addWrapColumn(usersBase.getColumn("FirstName"));
        users.addWrapColumn(usersBase.getColumn("LastName"));
        users.addWrapColumn(usersBase.getColumn("Description"));
        users.addWrapColumn(usersBase.getColumn("Created"));
        users.addWrapColumn(usersBase.getColumn("Modified"));
        
        if (!getUser().isGuest())
        {
            users.addWrapColumn(usersBase.getColumn("Email"));
        }

        if (getUser().isAdministrator() || getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            users.addWrapColumn(usersBase.getColumn("Phone"));
            users.addWrapColumn(usersBase.getColumn("Mobile"));
            users.addWrapColumn(usersBase.getColumn("Pager"));
            users.addWrapColumn(usersBase.getColumn("IM"));
            users.addWrapColumn(usersBase.getColumn("Active"));
            users.addWrapColumn(usersBase.getColumn("LastLogin"));

            // The details action requires admin permission so don't offer the link if they can't see it
            users.setDetailsURL(new DetailsURL(new ActionURL(UserController.DetailsAction.class, getContainer()), Collections.singletonMap("userId", "UserId")));
        }


        List<FieldKey> defCols = new ArrayList<FieldKey>();
        for (ColumnInfo columnInfo : users.getColumns())
        {
            if (!columnInfo.isHidden() && !"Created".equalsIgnoreCase(columnInfo.getName()) && !"Modified".equalsIgnoreCase(columnInfo.getName()))
            {
                defCols.add(FieldKey.fromParts(columnInfo.getName()));
            }
        }
        users.setDefaultVisibleColumns(defCols);

        return users;
    }

    protected void addNullSetFilter(FilteredTable table)
    {
        table.addCondition(new SQLFragment("1=2"));
    }
}
