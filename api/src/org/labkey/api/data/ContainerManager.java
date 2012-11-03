/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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

import junit.framework.Assert;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.Container.ContainerException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.AuthorRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class manages a hierarchy of collections, backed by a database table called Containers.
 * Containers are named using filesystem-like paths e.g. /proteomics/comet/.  Each path
 * maps to a UID and set of permissions.  The current security scheme allows ACLs
 * to be specified explicitly on the directory or completely inherited.  ACLs are not combined.
 * <p/>
 * NOTE: we act like java.io.File().  Paths start with forward-slash, but do not end with forward-slash.
 * The root container's name is '/'.  This means that it is not always the case that
 * me.getPath() == me.getParent().getPath() + "/" + me.getName()
 *
 * The synchronization goals are to keep invalid containers from creeping into the cache. For example, once
 * a container is deleted, it should never get put back in the cache. We accomplish this by synchronizing on
 * the removal from the cache, and the database lookup/cache insertion. While a container is in the middle
 * of being deleted, it's OK for other clients to see it because FKs enforce that it's always internally
 * consistent, even if some of the data has already been deleted.
 */
public class ContainerManager
{
    private static final Logger LOG = Logger.getLogger(ContainerManager.class);
    private static final CoreSchema CORE = CoreSchema.getInstance();

    private static final String CONTAINER_PREFIX = ContainerManager.class.getName() + "/";
    private static final String CONTAINER_CHILDREN_PREFIX = ContainerManager.class.getName() + "/children/";
    private static final String CONTAINER_ALL_CHILDREN_PREFIX = ContainerManager.class.getName() + "/children/*/";
    private static final String PROJECT_LIST_ID = "Projects";

    public static final String HOME_PROJECT_PATH = "/home";
    public static final String CONTAINER_AUDIT_EVENT = "ContainerAuditEvent";

    private static final StringKeyCache<Object> CACHE = CacheManager.getStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "Containers");
    private static final Object DATABASE_QUERY_LOCK = new Object();
    public static final String FOLDER_TYPE_PROPERTY_SET_NAME = "folderType";
    public static final String FOLDER_TYPE_PROPERTY_NAME = "name";

    // enum of properties you can see in property change events
    public enum Property
    {
        Name,
        Parent,
        Policy,
        WebRoot,
        AttachmentDirectory,
        PipelineRoot,
        Title,
        Description,
        SiteRoot
    }

    static Path makePath(Container parent, String name)
    {
        if (null == parent)
            return new Path(name);
        return parent.getParsedPath().append(name, true);
    }

    public static Container createMockContainer()
    {
        return new Container(null, "MockContainer", "01234567-8901-2345-6789-012345678901", 99999999, 0, new Date(), true);
    }

    private static Container createRoot()
    {
        try
        {
            HashMap<String, Object> m = new HashMap<String, Object>();
            m.put("Parent", null);
            m.put("Name", "");
            Table.insert(null, CORE.getTableInfoContainers(), m);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        return getRoot();
    }


    private static int getNewChildSortOrder(Container parent)
    {
        List<Container> children = parent.getChildren();
        if (children != null)
        {
            for (Container child : children)
            {
                if (child.getSortOrder() != 0)
                {
                    // custom sorting applies: put new container at the end.
                    return children.size();
                }
            }
        }
        // we're sorted alphabetically
        return 0;
    }

    // TODO: Make private and force callers to use ensureContainer instead?
    // TODO: Handle root creation here?
    public static Container createContainer(Container parent, String name)
    {
        return createContainer(parent, name, null, null, Container.TYPE.normal, null);
    }

    // TODO: Pass in folder type and transact it with container creation?
    public static Container createContainer(Container parent, String name, String title, String description, Container.TYPE type, User user)
    {
        if (CORE.getSchema().getScope().isTransactionActive())
            throw new IllegalStateException("Transaction should not be active");

        boolean createWorkbookName = false;
        if (Container.TYPE.workbook == type)
        {
            //parent must be normal
            if (Container.TYPE.normal != parent.getType())
                throw new IllegalArgumentException("Parent of a workbook must be a non-workbook container!");

            if (name == null)
            {
                //default workbook names are simply "workbook-<rowid>" but since we can't know the rowid until
                //we create the container, use a GUID for the name during the initial create
                //and then set the name and title
                createWorkbookName = true;
                name = GUID.makeGUID();
            }
        }

        if (Container.TYPE.tab == type)
        {
            //parent must be normal
            if (Container.TYPE.normal != parent.getType())
                throw new IllegalArgumentException("Parent of a container tab must not be a container tab container!");
        }

        StringBuilder error = new StringBuilder();
        if (!Container.isLegalName(name, error))
            throw new IllegalArgumentException(error.toString());

        if (!Container.isLegalTitle(title, error))
            throw new IllegalArgumentException(error.toString());

        Path path = makePath(parent, name);
        SQLException sqlx = null;
        Map<String, Object> insertMap = null;

        try
        {
            HashMap<String, Object> m = new HashMap<String, Object>();
            m.put("Parent", parent.getId());
            m.put("Name", name);
            m.put("Title", title);
            m.put("SortOrder", getNewChildSortOrder(parent));
            if (null != description)
                m.put("Description", description);
            m.put("Type", type);
            insertMap = Table.insert(user, CORE.getTableInfoContainers(), m);
        }
        catch (SQLException x)
        {
            if (!SqlDialect.isConstraintException(x))
                throw new RuntimeSQLException(x);
            sqlx = x;
        }

        _clearChildrenFromCache(parent);

        Container c = insertMap == null ? null  : getForId((String) insertMap.get("EntityId"));
        if (null == c)
        {
            if (null != sqlx)
                throw new RuntimeSQLException(sqlx);
            else
                throw new RuntimeException("Container for path '" + path + "' was not created properly.");
        }

        if (createWorkbookName)
        {
            name = "workbook-" + c.getRowId();

            try
            {
                StringBuilder sql = new StringBuilder("UPDATE ");
                sql.append(CORE.getTableInfoContainers());
                sql.append(" SET Name=? WHERE RowID=?");
                Table.execute(CORE.getSchema(), sql.toString(), name, c.getRowId());

                _removeFromCache(c); // seems odd, but it removes c.getProject() which clears other things from the cache
                path = makePath(parent, name);
                c = getForPath(path);
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }

        // Maybe this will help track down issues like #13813
        if (null == c)
            throw new RuntimeException("Container for path '" + path + "' was not created properly.");

        //workbooks  inherit perms from their parent so don't create a policy if this is a workbook     TODO; and container tabs???
        if (Container.TYPE.normal == type)
            SecurityManager.setAdminOnlyPermissions(c);

        _removeFromCache(c); // seems odd, but it removes c.getProject() which clears other things from the cache

        // init the list of active modules in the Container
        c.getActiveModules(true);

        if (c.isProject())
        {
            SecurityManager.createNewProjectGroups(c);
        }
        else
        {
            //If current user is NOT a site or folder admin, or the project has been explicitly set to have
            // new subfolders inherit permissions, we'll inherit permissions (otherwise they would not be able to see the folder)
            Integer adminGroupId = null;
            if (null != c.getProject())
                adminGroupId = SecurityManager.getGroupId(c.getProject(), "Administrators", false);
            boolean isProjectAdmin = (null != adminGroupId) && user != null && user.isInGroup(adminGroupId.intValue());
            if (!isProjectAdmin && user != null && !user.isAdministrator() || SecurityManager.shouldNewSubfoldersInheritPermissions(c.getProject()))
                SecurityManager.setInheritPermissions(c);
        }

        fireCreateContainer(c, user);
        return c;
    }


    public static void setFolderType(Container c, FolderType folderType, User user)
    {
        FolderType oldType = c.getFolderType();

        if (folderType.equals(oldType))
            return;

        //only toggle the menu bar if it was not already set
        if (folderType.isMenubarEnabled() && !LookAndFeelProperties.getInstance(c).isShowMenuBar())
        {
            setMenuEnabled(c, user, true);
        }

        oldType.unconfigureContainer(c, user);
        folderType.configureContainer(c, user);
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, FOLDER_TYPE_PROPERTY_SET_NAME, true);
        props.put(FOLDER_TYPE_PROPERTY_NAME, folderType.getName());
        PropertyManager.saveProperties(props);

        // TODO: Not needed? I don't think we've changed the container's state.
        _removeFromCache(c);
    }

    public static boolean setMenuEnabled(Container c, User u, boolean enabled)
    {
        //currently we also allow setting the menu at the project level
        if (!c.isProject())
            return false;

        try
        {
            WriteableLookAndFeelProperties props = LookAndFeelProperties.getWriteableInstance(c);

            props.setMenuUIEnabled(enabled);
            props.writeAuditLogEvent(u, props.getOldProperties());
            props.save();
            return true;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private static final Set<Container> containersWithBadFolderTypes = new ConcurrentHashSet<Container>();

    @NotNull
    public static FolderType getFolderType(Container c)
    {
        Map props = PropertyManager.getProperties(0, c, ContainerManager.FOLDER_TYPE_PROPERTY_SET_NAME);
        String name = (String) props.get(ContainerManager.FOLDER_TYPE_PROPERTY_NAME);
        FolderType folderType;

        if (null != name)
        {
            folderType = ModuleLoader.getInstance().getFolderType(name);

            if (null == folderType)
            {
                // If we're upgrading then folder types won't be defined yet... don't warn in that case.
                if (!ModuleLoader.getInstance().isUpgradeInProgress() &&
                    !ModuleLoader.getInstance().isUpgradeRequired() &&
                    !containersWithBadFolderTypes.contains(c))
                {
                    LOG.warn("No such folder type " + name + " for folder " + c.toString());
                    containersWithBadFolderTypes.add(c);
                }

                folderType = FolderType.NONE;
            }
        }
        else
            folderType = FolderType.NONE;

        return folderType;
    }


    public static Container ensureContainer(String path)
    {
        return ensureContainer(Path.parse(path));
    }


    public static Container ensureContainer(Path path)
    {
        if (CORE.getSchema().getScope().isTransactionActive())
            throw new IllegalStateException("Transaction should not be active");

        Container c = null;

        try
        {
            c = getForPath(path);
        }
        catch (RootContainerException e)
        {
            // Ignore this -- root doesn't exist yet
        }

        if (null == c)
        {
            if (0 == path.size())
                c = createRoot();
            else
            {
                Path parentPath = path.getParent();
                c = ensureContainer(parentPath);
                if (null == c)
                    return null;
                c = createContainer(c, path.getName());
            }
        }
        return c;
    }


    public static Container ensureContainer(Container parent, String name)
    {
        if (CORE.getSchema().getScope().isTransactionActive())
            throw new IllegalStateException("Transaction should not be active");

        Container c = null;

        try
        {
            c = getForPath(makePath(parent,name));
        }
        catch (RootContainerException e)
        {
            // Ignore this -- root doesn't exist yet
        }

        if (null == c)
        {
            c = createContainer(parent, name);
        }
        return c;
    }

    public static void updateDescription(Container container, String description, User user)
    {
        //For some reason there is no primary key defined on core.containers
        //so we can't use Table.update here
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(CORE.getTableInfoContainers());
        sql.append(" SET Description=? WHERE RowID=?");
        new SqlExecutor(CORE.getSchema(), new SQLFragment(sql.toString(), description, container.getRowId())).execute();
        
        String oldValue = container.getDescription();
        _removeFromCache(container);
        container = getForRowId(container.getRowId());
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(container, user, Property.Description, oldValue, description);
        firePropertyChangeEvent(evt);
    }

    public static void updateSearchable(Container container, boolean searchable, User user)
    {
        //For some reason there is no primary key defined on core.containers
        //so we can't use Table.update here
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(CORE.getTableInfoContainers());
        sql.append(" SET Searchable=? WHERE RowID=?");
        new SqlExecutor(CORE.getSchema(), new SQLFragment(sql.toString(), searchable, container.getRowId())).execute();

        _removeFromCache(container);
    }

    public static void updateTitle(Container container, String title, User user)
    {
        //For some reason there is no primary key defined on core.containers
        //so we can't use Table.update here
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(CORE.getTableInfoContainers());
        sql.append(" SET Title=? WHERE RowID=?");
        new SqlExecutor(CORE.getSchema(), new SQLFragment(sql.toString(), title, container.getRowId())).execute();

        _removeFromCache(container);
        String oldValue = container.getTitle();
        container = getForRowId(container.getRowId());
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(container, user, Property.Title, oldValue, title);
        firePropertyChangeEvent(evt);
    }

    public static final String SHARED_CONTAINER_PATH = "/Shared";

    @NotNull
    public static Container getSharedContainer()
    {
        return getForPath(SHARED_CONTAINER_PATH);
    }

    public static List<Container> getChildren(Container parent)
    {
        return new ArrayList<Container>(getChildrenMap(parent).values());
    }


    public static List<Container> getChildren(Container parent, User u, Class<? extends Permission> perm)
    {
        return getChildren(parent, u, perm, true);
    }

    public static List<Container> getChildren(Container parent, User u, Class<? extends Permission> perm, boolean includeWorkbooksAndTabs)
    {
        List<Container> children = new ArrayList<Container>();
        for (Container child : getChildrenMap(parent).values())
            if (child.hasPermission(u, perm) && (includeWorkbooksAndTabs || !child.isWorkbookOrTab()))
                children.add(child);

        return children;
    }


    public static List<Container> getAllChildren(Container parent, User u)
    {
        return getAllChildren(parent, u, ReadPermission.class);
    }

    public static List<Container> getAllChildren(Container parent, User u, Class<? extends Permission> perm)
    {
        return getAllChildren(parent, u, perm, true);
    }

    public static List<Container> getAllChildren(Container parent, User u, Class<? extends Permission> perm, boolean includeWorkbooksAndTabs)
    {
        List<Container> result = new ArrayList<Container>();
        Container[] containers = getAllChildren(parent);

        for (Container container : containers)
        {
            if (container.hasPermission(u, perm) && (includeWorkbooksAndTabs || !container.isWorkbookOrTab()))
            {
                result.add(container);
            }
        }

        return result;
    }


    // Returns true only if user has the specified permission in the entire container tree starting at root
    public static boolean hasTreePermission(Container root, User u,  Class<? extends Permission> perm)
    {
        Container[] all = getAllChildren(root);

        for (Container c : all)
            if (!c.hasPermission(u, perm))
                return false;

        return true;
    }


    private static final String[] emptyStringArray = new String[0];

    public static Map<String, Container> getChildrenMap(Container parent)
    {
        if (parent.getType() != Container.TYPE.normal)
        {
            // Optimization to avoid database query (important because some installs have tens of thousands of
            // workbooks) when the container is a workbook, which is not allowed to have children
            return Collections.emptyMap();
        }

        String[] childIds = (String[]) CACHE.get(CONTAINER_CHILDREN_PREFIX + parent.getId());
        if (null == childIds)
        {
            try
            {
                CORE.getSchema().getScope().ensureTransaction();

                synchronized (DATABASE_QUERY_LOCK)
                {
                    Container[] children = Table.executeQuery(CORE.getSchema(),
                            "SELECT * FROM " + CORE.getTableInfoContainers() + " WHERE Parent = ? ORDER BY SortOrder, LOWER(Name)",
                            new Object[]{parent.getId()},
                            Container.class);
                    if (children.length == 0)
                    {
                        CACHE.put(CONTAINER_CHILDREN_PREFIX + parent.getId(), emptyStringArray);
                        // No database changes to commit, but need to decrement the counter
                        CORE.getSchema().getScope().commitTransaction();
                        return Collections.emptyMap();
                    }
                    childIds = new String[children.length];
                    for (int i=0 ; i<children.length ; i++)
                    {
                        Container c = children[i];
                        childIds[i] = c.getId();
                        _addToCache(c);
                    }
                    CACHE.put(CONTAINER_CHILDREN_PREFIX + parent.getId(), childIds);
                    // No database changes to commit, but need to decrement the counter
                    CORE.getSchema().getScope().commitTransaction();
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            finally
            {
                CORE.getSchema().getScope().closeConnection();
            }
        }

        if (childIds == emptyStringArray)
            return Collections.emptyMap();

        // Use a LinkedHashMap to preserve the order defined by the user - they're not necessarily alphabetical
        Map<String, Container> ret = new LinkedHashMap<String, Container>();
        for (String id : childIds)
        {
            Container c = ContainerManager.getForId(id);
            if (null != c)
                ret.put(c.getName(), c);
        }
        assert null != (ret = Collections.unmodifiableMap(ret));
        return ret;
    }


    public static Container getForRowId(int id)
    {
        Selector selector = new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT * FROM " + CORE.getTableInfoContainers() + " WHERE RowId = ?", id));
        return selector.getObject(Container.class);
    }

    public static Container getForId(@NotNull GUID id)
    {
        return getForId(id.toString());
    }


    public static Container getForId(String id)
    {
        Container d = _getFromCacheId(id);
        if (null != d)
            return d;

        //if the input string is not a GUID, just return null,
        //so that we don't get a SQLException when the database
        //tries to convert it to a unique identifier.
        if (null != id && !GUID.isGUID(id))
            return null;

        try
        {
            CORE.getSchema().getScope().ensureTransaction();

            synchronized (DATABASE_QUERY_LOCK)
            {
                Container[] ret = Table.executeQuery(
                        CORE.getSchema(),
                        "SELECT * FROM " + CORE.getTableInfoContainers() + " WHERE EntityId = ?",
                        new Object[]{id}, Container.class);
                // No database changes to commit, but need to decrement the counter
                CORE.getSchema().getScope().commitTransaction();

                if (ret == null || ret.length == 0)
                    return null;
                return _addToCache(ret[0]);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            CORE.getSchema().getScope().closeConnection();
        }
    }


    public static Container getChild(Container c, String name)
    {
        Path path = c.getParsedPath().append(name);

        Container d = _getFromCachePath(path);
        if (null != d)
            return d;

        Map<String, Container> map = ContainerManager.getChildrenMap(c);
        return map.get(name);
    }


    public static Container getForPath(String path)
    {
        Path p = Path.parse(path);
        return getForPath(p);
    }


    public static Container getForPath(Path path)
    {
        Container d = _getFromCachePath(path);
        if (null != d)
            return d;

        try
        {
            if (path.equals(Path.rootPath))
            {
                try
                {
                    CORE.getSchema().getScope().ensureTransaction();

                    synchronized (DATABASE_QUERY_LOCK)
                    {
                        // Special case for ROOT.  Never return null -- either database error or corrupt database
                        Container[] ret = Table.executeQuery(CORE.getSchema(),
                                "SELECT * FROM " + CORE.getTableInfoContainers() + " WHERE Parent IS NULL",
                                null, Container.class);

                        if (null == ret || ret.length == 0)
                            throw new RootContainerException("Root container does not exist");

                        if (ret.length > 1)
                            throw new RootContainerException("More than one root container was found");

                        if (null == ret[0])
                            throw new RootContainerException("Root container is NULL");

                        _addToCache(ret[0]);
                        // No database changes to commit, but need to decrement the counter
                        CORE.getSchema().getScope().commitTransaction();
                        return ret[0];
                    }
                }
                finally
                {
                    CORE.getSchema().getScope().closeConnection();
                }
            }
            else
            {
                Path parent = path.getParent();
                String name = path.getName();
                Container dirParent = getForPath(parent);

                if (null == dirParent)
                    return null;

                Map<String, Container> map = ContainerManager.getChildrenMap(dirParent);
                return map.get(name);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    @SuppressWarnings({"serial"})
    public static class RootContainerException extends RuntimeException
    {
        private RootContainerException(String message)
        {
            super(message);
        }
    }


    public static Container getRoot()
    {
        return getForPath("/");
    }


    public static void saveAliasesForContainer(Container container, List<String> aliases) throws SQLException
    {
        CORE.getSchema().getScope().ensureTransaction();

        try
        {
            SQLFragment deleteSQL = new SQLFragment();
            deleteSQL.append("DELETE FROM ");
            deleteSQL.append(CORE.getTableInfoContainerAliases());
            deleteSQL.append(" WHERE ContainerId = ? ");
            deleteSQL.add(container.getId());
            if (!aliases.isEmpty())
            {
                deleteSQL.append(" OR LOWER(Path) IN (");
                String separator = "";
                for (String alias : aliases)
                {
                    deleteSQL.append(separator);
                    separator = ", ";
                    deleteSQL.append("LOWER(?)");
                    deleteSQL.add(alias);
                }
                deleteSQL.append(")");
            }
            Table.execute(CORE.getSchema(), deleteSQL);

            Set<String> caseInsensitiveAliases = new CaseInsensitiveHashSet(aliases);

            for (String alias : caseInsensitiveAliases)
            {
                SQLFragment insertSQL = new SQLFragment();
                insertSQL.append("INSERT INTO ");
                insertSQL.append(CORE.getTableInfoContainerAliases());
                insertSQL.append(" (Path, ContainerId) VALUES (?, ?)");
                insertSQL.add(alias);
                insertSQL.add(container.getId());
                Table.execute(CORE.getSchema(), insertSQL);
            }

            CORE.getSchema().getScope().commitTransaction();
        }
        finally
        {
            CORE.getSchema().getScope().closeConnection();
        }
    }


    // Used for attaching system resources (favorite icon, logo) to the root container
    public static class ContainerParent implements AttachmentParent
    {
        Container _c;

        public ContainerParent(Container c)
        {
            _c = c;
        }

        public String getEntityId()
        {
            return _c.getId();
        }

        public String getContainerId()
        {
            return _c.getId();
        }

        public Container getContainer()
        {
            return _c;
        }

        @Override
        public String getDownloadURL(ViewContext context, String name)
        {
            return null;
        }
    }


    // Used for attaching system resources (favorite icon, logo) to the root container
    public static class RootContainer extends ContainerParent
    {
        private RootContainer(Container c)
        {
            super(c);
        }

        public static RootContainer get()
        {
            Container root = getRoot();

            if (null == root)
                return null;
            else
                return new RootContainer(root);
        }
    }


    public static Container getHomeContainer()
    {
        return getForPath(HOME_PROJECT_PATH);
    }

    public static List<Container> getProjects()
    {
        return ContainerManager.getChildren(ContainerManager.getRoot());
    }


    public static NavTree getProjectList(ViewContext context)
    {
        NavTree navTree = (NavTree) NavTreeManager.getFromCache(PROJECT_LIST_ID, context);

        if (null != navTree)
            return navTree;

        User user = context.getUser();
        NavTree list = new NavTree("Projects");
        List<Container> projects = ContainerManager.getProjects();

        for (Container project : projects)
        {
            if (project.shouldDisplay(user) && project.hasPermission(user, ReadPermission.class))
            {
                ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(project);

                if (project.equals(getHomeContainer()))
                    list.addChild(0, new NavTree("Home", startURL));
                else
                    list.addChild(project.getName(), startURL);
            }
        }

        list.setId(PROJECT_LIST_ID);
        NavTreeManager.cacheTree(list, context.getUser());

        return list;
    }


    public static NavTree getFolderListForUser(Container project, ViewContext viewContext)
    {
        Container c = viewContext.getContainer();

        NavTree tree = (NavTree) NavTreeManager.getFromCache(project.getId(), viewContext);
        if (null != tree)
            return tree;
        User user = viewContext.getUser();
        String projectId = project.getId();

        Container[] folders = ContainerManager.getAllChildren(project);

        Arrays.sort(folders);

        Set<Container> containersInTree = new HashSet<Container>();

        Map<String, NavTree> m = new HashMap<String, NavTree>();
        for (Container f : folders)
        {
            if (f.isWorkbookOrTab())
                continue;

            SecurityPolicy policy = f.getPolicy();
            boolean skip = (!policy.hasPermission(user, ReadPermission.class) || (!f.shouldDisplay(user)));
            //Always put the project and current container in...
            if (skip && !f.equals(project) && !f.equals(c))
                continue;

            //HACK to make home link consistent...
            String name = f.getName();
            if (name.equals("home") && f.equals(getHomeContainer()))
                name = "Home";

            NavTree t = new NavTree(name);
            if (policy.hasPermission(user, ReadPermission.class))
            {
                ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(f);
                t.setHref(url.getEncodedLocalURIString());
            }
            containersInTree.add(f);
            m.put(f.getId(), t);
        }

        //Ensure parents of any accessible folder are in the tree. If not add them with no link.
        for (Container treeContainer : containersInTree)
        {
            if (!treeContainer.equals(project) && !containersInTree.contains(treeContainer.getParent()))
            {
                Set<Container> containersToRoot = containersToRoot(treeContainer);
                //Possible will be added more than once, if several children are accessible, but that's OK...
                for (Container missing : containersToRoot)
                    if (!m.containsKey(missing.getId()))
                    {
                        NavTree noLinkTree = new NavTree(missing.getName());
                        m.put(missing.getId(), noLinkTree);
                    }
            }
        }

        for (Container f : folders)
        {
            if (f.getId().equals(projectId))
                continue;

            NavTree child = m.get(f.getId());
            if (null == child)
                continue;

            NavTree parent = m.get(f.getParent().getId());
            assert null != parent; //This should not happen anymore, we assure all parents are in tree.
            if (null != parent)
                parent.addChild(child);
        }

        NavTree projectTree = m.get(projectId);

        projectTree.setId(project.getId());

        NavTreeManager.cacheTree(projectTree, user);
        return projectTree;
    }

    public static Set<Container> containersToRoot(Container child)
    {
        Set<Container> containersOnPath = new HashSet<Container>();
        Container current = child;
        while (current != null && !current.isRoot())
        {
            containersOnPath.add(current);
            current = current.getParent();
        }

        return containersOnPath;
    }


    // Move a container to another part of the container tree.  Careful: this method DOES NOT prevent you from orphaning
    // an entire tree (e.g., by setting a container's parent to one of its children); the UI in AdminController does this.
    //
    // NOTE: Beware side-effect of changing ACLs and GROUPS if a container changes projects
    //
    // @return true if project has changed (should probably redirect to security page)
    public static boolean move(Container c, Container newParent, User user)
    {
        if (c.isRoot())
            throw new IllegalArgumentException("can't move root container");

        if (c.getParent().getId().equals(newParent.getId()))
            return false;

        Container oldParent = c.getParent();
        Container oldProject = c.getProject();
        Container newProject = newParent.isRoot() ? c : newParent.getProject();

        boolean changedProjects = !oldProject.getId().equals(newProject.getId());

        try
        {
            // Synchronize the transaction, but not the listeners -- see #9901
            CORE.getSchema().getScope().ensureTransaction();

            synchronized (DATABASE_QUERY_LOCK)
            {
                Table.execute(CORE.getSchema(), "UPDATE " + CORE.getTableInfoContainers() + " SET Parent = ? WHERE EntityId = ?", newParent.getId(), c.getId());

                // Refresh the container directly from the database so the container reflects the new parent, isProject(), etc.
                c = ContainerManager.getForRowId(c.getRowId());

                // this could be done in the trigger, but I prefer to put it in the transaction
                if (changedProjects)
                    SecurityManager.changeProject(c, oldProject, newProject);

                CORE.getSchema().getScope().commitTransaction();

                clearCache();  // Clear the entire cache, since containers cache their full paths
                getChildrenMap(newParent); // reload the cache
            }

        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            CORE.getSchema().getScope().closeConnection();
        }

        Container newContainer = getForId(c.getId());
        fireMoveContainer(newContainer, oldParent, user);

        return changedProjects;
    }


    // Rename a container in the table.  Will fail if the new name already exists in the parent container.
    // Lock the class to ensure the old version of this container doesn't sneak into the cache after clearing
    public static void rename(Container c, User user, String name)
    {
        name = StringUtils.trimToNull(name);
        if (null == name)
            throw new NullPointerException();
        String oldName = c.getName();
        if (oldName.equals(name))
            return;

        try
        {
            CORE.getSchema().getScope().ensureTransaction();

            synchronized (DATABASE_QUERY_LOCK)
            {
                Table.execute(CORE.getSchema(), "UPDATE " + CORE.getTableInfoContainers() + " SET Name=? WHERE EntityId=?", name, c.getId());
                clearCache();  // Clear the entire cache, since containers cache their full paths
                //Get new version since name has changed.
                c = getForId(c.getId());
                fireRenameContainer(c, user, oldName);
            }
            CORE.getSchema().getScope().commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            CORE.getSchema().getScope().closeConnection();
        }
    }

    public static void setChildOrderToAlphabetical(Container parent)
    {
        setChildOrder(parent.getChildren(), true);
    }

    public static void setChildOrder(Container parent, List<Container> orderedChildren) throws ContainerException
    {
        for (Container child : orderedChildren)
        {
            if (child == null || child.getParent() == null || !child.getParent().equals(parent)) // #13481
                throw new ContainerException("Invalid parent container of " + (child == null ? "null child container" : child.getPath()));
        }
        setChildOrder(orderedChildren, false);
    }

    private static void setChildOrder(List<Container> siblings, boolean resetToAlphabetical)
    {
        DbSchema schema = CORE.getSchema();
        try
        {
            schema.getScope().ensureTransaction();
            synchronized (DATABASE_QUERY_LOCK)
            {
                for (int index = 0; index < siblings.size(); index++)
                {
                    Container current = siblings.get(index);
                    Table.execute(schema, "UPDATE " + CORE.getTableInfoContainers() + " SET SortOrder = ? WHERE EntityId = ?",
                            resetToAlphabetical ? 0 : index, current.getId());
                }
                schema.getScope().commitTransaction();
                clearCache();  // Clear the entire cache, since container lists are cached in order
            }
        }
        catch (SQLException e)
        {
            LOG.error(e);
            throw new RuntimeSQLException(e);
        }
        finally
        {
            schema.getScope().closeConnection();
        }
    }

    // Delete a container from the database.
    public static boolean delete(Container c, User user)
    {
        try
        {
            CORE.getSchema().getScope().ensureTransaction();
            ResultSet rs = null;

            try
            {
                // check to ensure no children exist
                rs = Table.executeQuery(CORE.getSchema(), "SELECT EntityId FROM " + CORE.getTableInfoContainers() +
                        " WHERE Parent = ?", new Object[]{c.getId()});
                if (rs.next())
                {
                    _removeFromCache(c);
                    return false;
                }
            }
            finally
            {
               ResultSetUtil.close(rs);
            }

            List<Throwable> errors = fireDeleteContainer(c, user);

            if (errors.size() != 0)
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException)first;
                else
                    throw new RuntimeException(first);
            }

            synchronized (DATABASE_QUERY_LOCK)
            {
                try
                {
                    Table.execute(CORE.getSchema(), "DELETE FROM " + CORE.getTableInfoContainerAliases() + " WHERE ContainerId=?", c.getId());
                    Table.execute(CORE.getSchema(), "DELETE FROM " + CORE.getTableInfoContainers() + " WHERE EntityId=?", c.getId());
                    // now that the container is actually gone, delete all ACLs (better to have an ACL w/o object than object w/o ACL)
                    SecurityPolicyManager.removeAll(c);
                }
                finally
                {
                    _removeFromCache(c);
                }
                CORE.getSchema().getScope().commitTransaction();
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            CORE.getSchema().getScope().closeConnection();
        }
        return true;
    }

    public static boolean isDeletable(Container c)
    {
        return !c.equals(getRoot()) && !c.equals(getHomeContainer()) && !c.equals(getSharedContainer());
    }

    public static void deleteAll(Container root, User user) throws UnauthorizedException
    {
        if (!hasTreePermission(root, user, DeletePermission.class))
            throw new UnauthorizedException("You don't have delete permissions to all folders");

        LinkedHashSet<Container> depthFirst = getAllChildrenDepthFirst(root);
        depthFirst.add(root);

        for (Container c : depthFirst)
            delete(c, user);
    }


    private static LinkedHashSet<Container> getAllChildrenDepthFirst(Container c)
    {
        LinkedHashSet<Container> set = new LinkedHashSet<Container>();
        getAllChildrenDepthFirst(c, set);
        return set;
    }


    private static void getAllChildrenDepthFirst(Container c, Collection<Container> list)
    {
        for (Container child : c.getChildren())
        {
            getAllChildrenDepthFirst(child, list);
            list.add(child);
        }
    }


    private static Container[] _getAllChildrenFromCache(Container c)
    {
        return (Container[]) CACHE.get(CONTAINER_ALL_CHILDREN_PREFIX + c.getId());
    }

    private static void _addAllChildrenToCache(Container c, Container[] children)
    {
        CACHE.put(CONTAINER_ALL_CHILDREN_PREFIX + c.getId(), children);
    }


    private static Container _getFromCacheId(String id)
    {
        return (Container) CACHE.get(CONTAINER_PREFIX + id);
    }


    private static Container _getFromCachePath(Path path)
    {
        return (Container) CACHE.get(CONTAINER_PREFIX + toString(path));
    }


    // UNDONE: use Path directly instead of toString()
    private static String toString(Container c)
    {
        return StringUtils.strip(c.getPath(), "/").toLowerCase();
    }

    private static String toString(Path p)
    {
        return StringUtils.strip(p.toString(), "/").toLowerCase();
    }


    private static Container _addToCache(Container c)
    {
        assert Thread.holdsLock(DATABASE_QUERY_LOCK) : "Any cache modifications must be synchronized at a " +
                "higher level so that we ensure that the container to be inserted still exists and hasn't been deleted";
        CACHE.put(CONTAINER_PREFIX + toString(c), c);
        CACHE.put(CONTAINER_PREFIX + c.getId(), c);
        return c;
    }


    private static void _clearChildrenFromCache(Container c)
    {
        CACHE.remove(CONTAINER_CHILDREN_PREFIX + c.getId());

        // UNDONE: NavTreeManager should register a ContainerListener
        Container project = c.getProject();
        NavTreeManager.uncacheTree(PROJECT_LIST_ID);
        if (project != null)
            NavTreeManager.uncacheTree(project.getId());
    }


    private static void _removeFromCache(Container c)
    {
        Container project = c.getProject();
        Container parent = c.getParent();

        CACHE.remove(CONTAINER_PREFIX + toString(c));
        CACHE.remove(CONTAINER_PREFIX + c.getId());
        CACHE.remove(CONTAINER_CHILDREN_PREFIX + c.getId());

        if (null != parent)
            CACHE.remove(CONTAINER_CHILDREN_PREFIX + parent.getId());

        // blow away the all children caches
        CACHE.removeUsingPrefix(CONTAINER_CHILDREN_PREFIX);

        // UNDONE: NavTreeManager should register a ContainerListener
        NavTreeManager.uncacheTree(PROJECT_LIST_ID);
        if (project != null)
            NavTreeManager.uncacheTree(project.getId());
    }


    public static void clearCache()
    {
        CACHE.clear();

        // UNDONE: NavTreeManager should register a ContainerListener
        NavTreeManager.uncacheAll();
    }


    public static void notifyContainerChange(String id)
    {
        Container c = getForId(id);
        if (null != c)
        {
            _removeFromCache(c);
            c = getForId(id);  // load a fresh container since the original might be stale.
            if (null != c)
            {
                ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(c, Property.Policy, null, null);
                firePropertyChangeEvent(evt);
            }
        }
    }


    /** including root node */
    public static Container[] getAllChildren(Container root)
    {
        Container[] allChildren = _getAllChildrenFromCache(root);
        if (allChildren != null)
            return allChildren.clone(); // don't let callers modify the array in the cache

        LinkedHashSet<Container> containerList = getAllChildrenDepthFirst(root);
        containerList.add(root);

        allChildren = containerList.toArray(new Container[containerList.size()]);
        _addAllChildrenToCache(root, allChildren);
        return allChildren.clone(); // don't let callers modify the array in the cache
    }


    public static long getContainerCount()
    {
        return new TableSelector(CORE.getTableInfoContainers()).getRowCount();
    }


    // Retrieve entire container hierarchy
    public static MultiMap<Container, Container> getContainerTree()
    {
        MultiMap<Container, Container> mm = new MultiHashMap<Container, Container>();

        ResultSet rs = null;
        try
        {
            // Get all containers and parents
            rs = Table.executeQuery(CORE.getSchema(), "SELECT Parent, EntityId FROM " + CORE.getTableInfoContainers() + " ORDER BY SortOrder, LOWER(Name) ASC", null);

            while (rs.next())
            {
                String parentId = rs.getString(1);
                Container parent = (parentId != null ? getForId(parentId) : null);
                Container child = getForId(rs.getString(2));

                if (null != child)
                    mm.put(parent, child);
            }

            for (Object key : mm.keySet())
            {
                List<Container> siblings = new ArrayList<Container>(mm.get(key));
                Collections.sort(siblings);
            }
        }
        catch (SQLException x)
        {
            LOG.error("getContainerTree: ", x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        return mm;
    }

    /**
     * Returns a branch of the container tree including only the root and its descendants
     * @param root The root container
     * @return MultiMap of containers including root and its descendants
     */
    public static MultiMap<Container, Container> getContainerTree(Container root)
    {
        //build a multimap of only the container ids
        MultiMap<String, String> mmIds = new MultiHashMap<String, String>();
        ResultSet rs = null;

        try
        {
            // Get all containers and parents
            rs = Table.executeQuery(CORE.getSchema(), "SELECT Parent, EntityId FROM " + CORE.getTableInfoContainers() + " ORDER BY SortOrder, LOWER(Name) ASC", null);

            while (rs.next())
            {
                mmIds.put(rs.getString(1), rs.getString(2));
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        //now find the root and build a MultiMap of it and its descendants
        MultiMap<Container, Container> mm = new MultiHashMap<Container, Container>();
        mm.put(null, root);
        addChildren(root, mmIds, mm);
        for (Object key : mm.keySet())
        {
            List<Container> siblings = new ArrayList<Container>(mm.get(key));
            Collections.sort(siblings);
        }
        return mm;
    }

    private static void addChildren(Container c, MultiMap<String, String> mmIds, MultiMap<Container, Container> mm)
    {
        Collection<String> childIds = mmIds.get(c.getId());
        if (null != childIds)
        {
            for (String childId : childIds)
            {
                Container child = getForId(childId);
                if (null != child)
                {
                    mm.put(c, child);
                    addChildren(child, mmIds, mm);
                }
            }
        }
    }

    public static Set<Container> getContainerSet(MultiMap<Container, Container> mm, User user, Class<? extends Permission> perm)
    {
        //noinspection unchecked
        Collection<Container> containers = mm.values();
        if (null == containers)
            return new HashSet<Container>();

        Set<Container> set = new HashSet<Container>(containers.size());

        for (Container c : containers)
        {
            if (c.hasPermission(user, perm))
                set.add(c);
        }

        return set;
    }


    public static String getIdsAsCsvList(Set<Container> containers)
    {
        if (0 == containers.size())
            return "(NULL)";    // WHERE x IN (NULL) should match no rows

        StringBuilder csvList = new StringBuilder("(");

        for (Container container : containers)
            csvList.append("'").append(container.getId()).append("',");

        // Replace last comma with ending paren
        csvList.replace(csvList.length() - 1, csvList.length(), ")");

        return csvList.toString();
    }


    public static List<String> getIds(User user, Class<? extends Permission> perm)
    {
        Set<Container> containers = getContainerSet(getContainerTree(), user, perm);

        List<String> ids = new ArrayList<String>(containers.size());

        for (Container c : containers)
            ids.add(c.getId());

        return ids;
    }


    //
    // ContainerListener
    //

    public interface ContainerListener extends PropertyChangeListener
    {
        enum Order {First, Last}

        void containerCreated(Container c, User user);

        void containerDeleted(Container c, User user);

        void containerMoved(Container c, Container oldParent, User user);
    }


    public static class ContainerPropertyChangeEvent extends PropertyChangeEvent
    {
        public final Property property;
        public final Container container;
        public User user;
        
        public ContainerPropertyChangeEvent(Container c, @Nullable User user, Property p, Object oldValue, Object newValue)
        {
            super(c, p.name(), oldValue, newValue);
            container = c;
            this.user = user;
            property = p;
        }

        public ContainerPropertyChangeEvent(Container c, Property p, Object oldValue, Object newValue)
        {
            this(c, null, p, oldValue, newValue);
        }
    }


    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<ContainerListener> _listeners = new CopyOnWriteArrayList<ContainerListener>();
    private static final List<ContainerListener> _laterListeners = new CopyOnWriteArrayList<ContainerListener>();

    // These listeners are executed in the order they are registered, before the "Last" listeners
    public static void addContainerListener(ContainerListener listener)
    {
        addContainerListener(listener, ContainerListener.Order.First);
    }


    // Explicitly request "Last" ordering via this method.  "Last" listeners execute after all "First" listeners.
    public static void addContainerListener(ContainerListener listener, ContainerListener.Order order)
    {
        if (ContainerListener.Order.First == order)
            _listeners.add(listener);
        else
            _laterListeners.add(listener);
    }


    public static void removeContainerListener(ContainerListener listener)
    {
        _listeners.remove(listener);
        _laterListeners.remove(listener);
    }


    private static List<ContainerListener> getListeners()
    {
        List<ContainerListener> combined = new ArrayList<ContainerListener>(_listeners.size() + _laterListeners.size());
        combined.addAll(_listeners);
        combined.addAll(_laterListeners);

        return combined;
    }


    protected static void fireCreateContainer(Container c, User user)
    {
        List<ContainerListener> list = getListeners();

        for (ContainerListener cl : list)
            try
            {
                cl.containerCreated(c, user);
            }
            catch (Throwable t)
            {
                LOG.error("fireCreateContainer for " + cl.getClass().getName(), t);
            }
    }


    protected static List<Throwable> fireDeleteContainer(Container c, User user)
    {
        List<ContainerListener> list = getListeners();
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ContainerListener l : list)
        {
            try
            {
                l.containerDeleted(c, user);
            }
            catch (Throwable t)
            {
                LOG.error("fireDeleteContainer for " + l.getClass().getName(), t);
                errors.add(t);
            }
        }
        return errors;
    }


    protected static void fireRenameContainer(Container c, User user, String oldValue)
    {
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(c, user, Property.Name, oldValue, c.getName());
        firePropertyChangeEvent(evt);
    }


    protected static void fireMoveContainer(Container c, Container oldParent, User user)
    {
        List<ContainerListener> list = getListeners();

        for (ContainerListener cl : list)
        {
            try
            {
                cl.containerMoved(c, oldParent, user);
            }
            catch (Throwable t)
            {
                LOG.error("fireMoveContainer for " + cl.getClass().getName(), t);
            }
        }
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(c, user, Property.Parent, oldParent, c.getParent());
        firePropertyChangeEvent(evt);
    }


    public static void firePropertyChangeEvent(ContainerPropertyChangeEvent evt)
    {
        List<ContainerListener> list = getListeners();
        for (ContainerListener l : list)
        {
            try
            {
                l.propertyChange(evt);
            }
            catch (Throwable t)
            {
                LOG.error("firePropertyChangeEvent for " + l.getClass().getName(), t);
            }
        }
    }


    public static Container createDefaultSupportContainer()
    {
        // create a "support" container. Admins can do anything,
        // Users can read/write, Guests can read.
        return bootstrapContainer(Container.DEFAULT_SUPPORT_PROJECT_PATH,
                RoleManager.getRole(SiteAdminRole.class),
                RoleManager.getRole(AuthorRole.class),
                RoleManager.getRole(ReaderRole.class));
    }

    public static String[] getAliasesForContainer(Container c)
    {
        return new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT Path FROM " + CORE.getTableInfoContainerAliases() + " WHERE ContainerId = ? ORDER BY LOWER(Path)", c.getId())).getArray(String.class);
    }

    @Nullable
    public static Container resolveContainerPathAlias(String path)
    {
        return resolveContainerPathAlias(path, false);
    }

    @Nullable
    private static Container resolveContainerPathAlias(String path, boolean top)
    {
        // Simple case -- resolve directly (sans alias)
        Container aliased = getForPath(path);
        if (aliased != null)
            return aliased;

        // Simple case -- directly resolve from database
        aliased = getForPathAlias(path);
        if (aliased != null)
            return aliased;

        // At the leaf and the container was not found
        if (top)
            return null;

        List<String> splits = Arrays.asList(path.split("/"));
        String subPath = "";
        for (int i=0; i < splits.size()-1; i++) // minus 1 due to leaving off last container
        {
            if (splits.get(i).length() > 0)
                subPath += "/" + splits.get(i);
        }

        aliased = resolveContainerPathAlias(subPath, false);

        if (aliased == null)
            return null;

        String leafPath = aliased.getPath() + "/" + splits.get(splits.size()-1);
        return resolveContainerPathAlias(leafPath, true);
    }

    @Nullable
    private static Container getForPathAlias(String path)
    {
        try
        {
            Container[] ret = Table.executeQuery(CORE.getSchema(),
                    "SELECT * FROM " + CORE.getTableInfoContainers() + " c, " + CORE.getTableInfoContainerAliases() + " ca WHERE ca.ContainerId = c.EntityId AND LOWER(ca.path) = LOWER(?)",
                    new Object[]{path}, Container.class);
            if (null == ret || ret.length == 0)
                return null;
            return ret[0];
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /**
     * If a container at the given path does not exist create one
     * and set permissions. If the container does exist, permissions
     * are only set if there is no explicit ACL for the container.
     * This prevents us from resetting permissions if all users
     * are dropped.
     */
    @NotNull
    public static Container bootstrapContainer(String path, Role adminRole, Role userRole, Role guestRole)
    {
        Container c = null;

        try
        {
            try
            {
                c = getForPath(path);
            }
            catch (RootContainerException e)
            {
                // Ignore this -- root doesn't exist yet
            }
            boolean newContainer = false;

            if (c == null)
            {
                LOG.debug("Creating new container for path '" + path + "'");
                newContainer = true;
                c = ensureContainer(path);
            }

            if (c == null)
            {
                throw new IllegalStateException("Unable to ensure container for path '" + path + "'");
            }

            // Only set permissions if there are no explicit permissions
            // set for this object or we just created it
            Integer policyCount = null;
            if (!newContainer)
            {
                policyCount = Table.executeSingleton(CORE.getSchema(),
                    "SELECT COUNT(*) FROM " + CORE.getTableInfoPolicies() + " WHERE ResourceId = ?",
                    new Object[]{c.getId()}, Integer.class);
            }

            if (newContainer || 0 == policyCount.intValue())
            {
                LOG.debug("Setting permissions for '" + path + "'");
                MutableSecurityPolicy policy = new MutableSecurityPolicy(c);
                policy.addRoleAssignment(SecurityManager.getGroup(Group.groupAdministrators), adminRole);
                policy.addRoleAssignment(SecurityManager.getGroup(Group.groupUsers), userRole);
                policy.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), guestRole);
                SecurityPolicyManager.savePolicy(policy);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return c;
    }


    public static class TestCase extends Assert implements ContainerListener
    {
        Map<Path, Container> _containers = new HashMap<Path, Container>();
        Container _testRoot = null;

        @Before
        public void setUp() throws Exception
        {
            if (null == _testRoot)
            {
                Container junit = ContainerManager.ensureContainer("/Shared/_junit");
                _testRoot = ContainerManager.ensureContainer(junit, "ContainerManager$TestCase-" + GUID.makeGUID());
                ContainerManager.addContainerListener(this);
            }
        }


        @After
        public void tearDown() throws Exception
        {
            ContainerManager.removeContainerListener(this);
            if (null != _testRoot)
                ContainerManager.deleteAll(_testRoot, TestContext.get().getUser());
        }

        @Test
        public void testImproperFolderNamesBlocked() throws Exception
        {
            String[] badnames = {"", "f\\o", "f/o", "f\\\\o", "foo;", "@foo", "foo" + '\u001F', '\u0000' + "foo", "fo" + '\u007F' + "o", "" + '\u009F'};

            for(String name: badnames)
            {
                try
                {
                    Container c = createContainer(_testRoot, name);
                    try
                    {
                        assertTrue(delete(c, TestContext.get().getUser()));
                    }
                    catch(Exception e){}
                    fail("Should have thrown illegal argument when trying to create container with name: " + name);
                }
                catch(IllegalArgumentException e)
                {
                        //Do nothing, this is expected
                }
            }
        }




        @Test
        public void testCreateDeleteContainers() throws Exception
        {
            int count = 20;
            Random random = new Random();
            MultiMap<String, String> mm = new MultiHashMap<String, String>();

            for (int i = 1; i <= count; i++)
            {
                int parentId = random.nextInt(i);
                String parentName = 0 == parentId ? _testRoot.getName() : String.valueOf(parentId);
                String childName = String.valueOf(i);
                mm.put(parentName, childName);
            }

            logNode(mm, _testRoot.getName(), 0);
            for(int i=0; i<2; i++) //do this twice to make sure the containers were *really* deleted
            {
                createContainers(mm, _testRoot.getName(), _testRoot);
                assertEquals(count, _containers.size());
                cleanUpChildren(mm, _testRoot.getName(), _testRoot);
                assertEquals(0, _containers.size());
            }
        }


        @Test
        public void testCache() throws Exception
        {
            assertEquals(0, _containers.size());
            assertEquals(0, ContainerManager.getChildren(_testRoot).size());

            Container one = ContainerManager.createContainer(_testRoot, "one");
            assertEquals(1, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(0, ContainerManager.getChildren(one).size());

            Container oneA = ContainerManager.createContainer(one, "A");
            assertEquals(2, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(1, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(oneA).size());

            Container oneB = ContainerManager.createContainer(one, "B");
            assertEquals(3, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(2, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(oneB).size());

            Container deleteme = ContainerManager.createContainer(one, "deleteme");
            assertEquals(4, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(3, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(deleteme).size());

            assertTrue(ContainerManager.delete(deleteme, TestContext.get().getUser()));
            assertEquals(3, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(2, ContainerManager.getChildren(one).size());

            Container oneC = ContainerManager.createContainer(one, "C");
            assertEquals(4, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(3, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(oneC).size());

            assertTrue(ContainerManager.delete(oneC, TestContext.get().getUser()));
            assertTrue(ContainerManager.delete(oneB, TestContext.get().getUser()));
            assertEquals(1, ContainerManager.getChildren(one).size());

            assertTrue(ContainerManager.delete(oneA, TestContext.get().getUser()));
            assertEquals(0, ContainerManager.getChildren(one).size());

            assertTrue(ContainerManager.delete(one, TestContext.get().getUser()));
            assertEquals(0, ContainerManager.getChildren(_testRoot).size());
            assertEquals(0, _containers.size());
        }


        @Test
        public void testFolderType() throws SQLException
        {
            Container newFolder = createContainer(_testRoot, "folderTypeTest");
            FolderType ft = newFolder.getFolderType();
            assertEquals(ft, FolderType.NONE);

            Container newFolderFromCache = getForId(newFolder.getId());
            assertEquals(newFolderFromCache.getFolderType(), FolderType.NONE);

            FolderType randomType = getRandomFolderType();
            newFolder.setFolderType(randomType, TestContext.get().getUser());

            newFolderFromCache = getForId(newFolder.getId());
            assertEquals(newFolderFromCache.getFolderType(), randomType);

            assertTrue(delete(newFolder, TestContext.get().getUser()));
            Container deletedContainer = getForId(newFolder.getId());
            if (deletedContainer != null)
            {
                fail("Expected container with Id " + newFolder.getId() + " to be deleted, but found " + deletedContainer + ". Folder type was " + randomType);
            }
        }


        private FolderType getRandomFolderType()
        {
            List<FolderType> folderTypes = new ArrayList<FolderType>(ModuleLoader.getInstance().getFolderTypes());
            return folderTypes.get(new Random().nextInt(folderTypes.size()));
        }


        private static void createContainers(MultiMap<String, String> mm, String name, Container parent)
        {
            Collection<String> nodes = mm.get(name);

            if (null == nodes)
                return;

            for (String childName : nodes)
            {
                Container child = ContainerManager.createContainer(parent, childName);
                createContainers(mm, childName, child);
            }
        }


        private static void cleanUpChildren(MultiMap<String, String> mm, String name, Container parent)
        {
            Collection<String> nodes = mm.get(name);

            if (null == nodes)
                return;

            for (String childName : nodes)
            {
                Container child = getForPath(makePath(parent, childName));
                cleanUpChildren(mm, childName, child);
                assertTrue(ContainerManager.delete(child, TestContext.get().getUser()));
            }
        }


        private static void logNode(MultiMap<String, String> mm, String name, int offset)
        {
            Collection<String> nodes = mm.get(name);

            if (null == nodes)
                return;

            for (String childName : nodes)
            {
                LOG.debug(StringUtils.repeat("   ", offset) + childName);
                logNode(mm, childName, offset + 1);
            }
        }


        // ContainerListener
        public void propertyChange(PropertyChangeEvent evt)
        {
        }


        public void containerCreated(Container c, User user)
        {
            if (null == _testRoot || !c.getParsedPath().startsWith(_testRoot.getParsedPath()))
                return;
            _containers.put(c.getParsedPath(), c);
        }


        public void containerDeleted(Container c, User user)
        {
            _containers.remove(c.getParsedPath());
        }

        @Override
        public void containerMoved(Container c, Container oldParent, User user)
        {
        }
    }


    static
    {
        ObjectFactory.Registry.register(Container.class, new ContainerFactory());
    }


    public static class ContainerFactory implements ObjectFactory<Container>
    {
        public Container fromMap(Map<String, ?> m)
        {
            throw new UnsupportedOperationException();
        }

        public Container fromMap(Container bean, Map<String, ?> m)
        {
            throw new UnsupportedOperationException();
        }

        public Map<String, Object> toMap(Container bean, Map<String, Object> m)
        {
            throw new UnsupportedOperationException();
        }

        public Container handle(ResultSet rs) throws SQLException
        {
            String id;
            Container d;
            String parentId = rs.getString("Parent");
            String name = rs.getString("Name");
            id = rs.getString("EntityId");
            int rowId = rs.getInt("RowId");
            int sortOrder = rs.getInt("SortOrder");
            Date created = rs.getTimestamp("Created");
            // _ts, createdby
            String description = rs.getString("Description");
            String type = rs.getString("Type");
            String title = rs.getString("Title");
            boolean searchable = rs.getBoolean("Searchable");

            Container dirParent = null;
            if (null != parentId)
                dirParent = getForId(parentId);

            d = new Container(dirParent, name, id, rowId, sortOrder, created, searchable);
            d.setDescription(description);
            d.setType(type);
            d.setTitle(title);
            return d;
        }

        @Override
        public ArrayList<Container> handleArrayList(ResultSet rs) throws SQLException
        {
            ArrayList<Container> list = new ArrayList<Container>();
            while (rs.next())
            {
                list.add(handle(rs));
            }
            return list;
        }

        @Override
        public Container[] handleArray(ResultSet rs) throws SQLException
        {
            ArrayList<Container> list = handleArrayList(rs);
            return list.toArray(new Container[list.size()]);
        }
    }



    static final ContainerService _instance = new ContainerServiceImpl();

    public static ContainerService getContainerService()
    {
        return _instance;
    }

    private static class ContainerServiceImpl implements ContainerService
    {
        @Override
        public Container getForId(GUID id)
        {
            return ContainerManager.getForId(id);
        }

        @Override
        public Container getForId(String id)
        {
            return ContainerManager.getForId(id);
        }

        @Override
        public Container getForPath(Path path)
        {
            return ContainerManager.getForPath(path);
        }

        @Override
        public Container getForPath(String path)
        {
            return ContainerManager.getForPath(path);
        }
    }

    public static Container createFakeContainer(@Nullable String name, @Nullable Container parent)
    {
        return new Container(parent, name, GUID.makeGUID(), 1, 0, new Date(), false);
    }
}
