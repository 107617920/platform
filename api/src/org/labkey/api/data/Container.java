/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartFactory;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * see ContainerManager for more info
 *
 * CONSIDER: extend org.labkey.api.data.Entity
 */
public class Container implements Serializable, Comparable<Container>, SecurableResource, ContainerContext
{
    private GUID _id;
    private Path _path;
    private Date _created;
    private int _rowId; //Unique for this installation

    /** Used to arbitrarily reorder siblings within a container. */
    private int _sortOrder;
    private String _description;

    private transient Module _defaultModule;
    private transient Set<Module> _activeModules;

    private transient WeakReference<Container> _parent;

    public static final String DEFAULT_SUPPORT_PROJECT_PATH = ContainerManager.HOME_PROJECT_PATH + "/support";

    //is this container a workbook?
    private boolean _workbook;

    // include in results from searches outside this container?
    private final boolean _searchable;

    //optional non-unique title for the container
    private String _title;

    // UNDONE: BeanFactory for Container

    protected Container(Container dirParent, String name, String id, int rowId, int sortOrder, Date created, boolean searchable)
    {
        _path = null == dirParent && StringUtils.isEmpty(name) ? Path.rootPath : ContainerManager.makePath(dirParent, name);
        _id = new GUID(id);
        _parent = new WeakReference<Container>(dirParent);
        _rowId = rowId;
        _sortOrder = sortOrder;
        _created = created;
        _searchable = searchable;
    }


    public Container getContainer(Map context)
    {
        return this;
    }


    @NotNull
    public String getName()
    {
        return _path.getName();
    }

    @NotNull
    public String getResourceName()
    {
        return _path.getName();
    }

    public Date getCreated()
    {
        return _created;
    }


    public boolean isInheritedAcl()
    {
        return !(getPolicy().getResourceId().equals(getId()));
    }

    /**
     * @return the parent container, or the root container (with path "/") if called on the root
     */
    public Container getParent()
    {
        Container parent = _parent == null ? null : _parent.get();
        if (null == parent && _path.size() > 0)
        {
            parent = ContainerManager.getForPath(_path.getParent());
            _parent = new WeakReference<Container>(parent);
        }
        return parent;
    }


    public String getPath()
    {
        if (_path.size() == 0)
            return "/";
        String path = _path.toString();
        if (path.length() > 1 && path.endsWith("/"))
            path = path.substring(0, path.length()-1);
        return path;
    }


    public Path getParsedPath()
    {
        return _path;
    }


    /**
     * returned string begins and ends with "/" so you can slap it between
     * getContextPath() and action.view
     */
    public String getEncodedPath()
    {
        String enc = _path.encode();
        if (!enc.startsWith("/"))
            enc = "/" + enc;
        if (!enc.endsWith("/"))
            enc = enc + "/";
        return enc;
    }


    public String getId()
    {
        return _id.toString();
    }

    public GUID getEntityId()
    {
        return _id;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getSortOrder()
    {
        return _sortOrder;
    }

    public void setSortOrder(int sortOrder)
    {
        _sortOrder = sortOrder;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    /**
     * Get the project Container or null if isRoot().
     * @return The project Container or null if isRoot().
     */
    public @Nullable Container getProject()
    {
        // Root has no project
        if (isRoot())
            return null;

        Container project = this;
        while (!project.isProject())
        {
            project = project.getParent();
            if (null == project)        // deleted container?
                return null;
        }
        return project;
    }

    public SecurityPolicy getPolicy()
    {
        return SecurityManager.getPolicy(this);
    }

    public boolean hasPermission(@NotNull User user, @NotNull Class<? extends Permission> perm)
    {
        return getPolicy().hasPermission(user, perm);
    }

    public boolean hasPermission(@NotNull User user, @NotNull Class<? extends Permission> perm, @Nullable Set<Role> contextualRoles)
    {
        return getPolicy().hasPermission(user, perm, contextualRoles);
    }

    public boolean hasOneOf(@NotNull User user, @NotNull Set<Class<? extends Permission>> perms)
    {
        return getPolicy().hasOneOf(user, perms, null);
    }

    public boolean hasOneOf(@NotNull User user, @NotNull Class<? extends Permission>... perms)
    {
        return getPolicy().hasOneOf(user, new HashSet<Class<? extends Permission>>(Arrays.asList(perms)), null);
    }

    /**
     * Don't use this anymore
     * @param user the user
     * @param perm the old nasty integer permission
     * @return something you don't want anymore
     * @deprecated Use hasPermission(User user, Class&lt;? extends Permission&gt; perm) instead
     */
    @Deprecated
    public boolean hasPermission(User user, int perm)
    {
        if (isForbiddenProject(user))
            return false;

        SecurityPolicy policy = getPolicy();
        return policy.hasPermissions(user, getPermissionsForIntPerm(perm));
    }

    @Deprecated
    private Set<Class<? extends Permission>> getPermissionsForIntPerm(int perm)
    {
        Set<Class<? extends Permission>> perms = new HashSet<Class<? extends Permission>>();
        if ((perm & ACL.PERM_READ) > 0 || (perm & ACL.PERM_READOWN) > 0)
            perms.add(ReadPermission.class);
        if ((perm & ACL.PERM_INSERT) > 0)
            perms.add(InsertPermission.class);
        if ((perm & ACL.PERM_UPDATE) > 0 || (perm & ACL.PERM_UPDATEOWN) > 0)
            perms.add(UpdatePermission.class);
        if ((perm & ACL.PERM_DELETE) > 0 || (perm & ACL.PERM_DELETEOWN) > 0)
            perms.add(DeletePermission.class);
        if ((perm & ACL.PERM_ADMIN) > 0)
            perms.add(AdminPermission.class);

        return perms;
    }

    public boolean isForbiddenProject(User user)
    {
        // If user is being impersonated within a project
        if (null != user && user.isImpersonated() && null != user.getImpersonationProject())
        {
            // Can't visit the root and current project must match impersonation project
            if (isRoot() || !getProject().equals(user.getImpersonationProject()))
                return true;
        }

        return false;
    }


    public boolean isProject()
    {
        return _path.size() == 1;
    }


    public boolean isRoot()
    {
        return _path.size() == 0;
    }


    public boolean shouldDisplay()
    {
        User user = HttpView.currentContext().getUser();
        return shouldDisplay(user);
    }

    
    public boolean shouldDisplay(User user)
    {
        if (isWorkbook())
            return false;

        String name = _path.getName();
        if (name.length() == 0)
            return true; // Um, I guess we should display it?
        char c = name.charAt(0);
        if (c == '_' || c == '.')
        {
            return user != null && (user.isAdministrator() || hasPermission(user, AdminPermission.class));
        }
        else
        {
            return true;
        }
    }


    public boolean isWorkbook()
    {
        return _workbook;
    }

    public void setWorkbook(boolean workbook)
    {
        _workbook = workbook;
    }

    public boolean isSearchable()
    {
        return _searchable;
    }

    /**
     * Returns true if possibleAncestor is a parent of this container,
     * or a parent-of-a-parent, etc.
     */
    public boolean hasAncestor(Container possibleAncestor)
    {
        if (isRoot())
            return false;
        if (getParent().equals(possibleAncestor))
            return true;
        return getParent().hasAncestor(possibleAncestor);
    }

    public Container getChild(String folderName)
    {
        return ContainerManager.getChild(this,folderName);
    }

    public boolean hasChild(String folderName)
    {
        return getChild(folderName) != null;
    }

    public boolean hasChildren()
    {
        return getChildren().size() > 0;
    }

    public List<Container> getChildren()
    {
        return ContainerManager.getChildren(this);
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        List<SecurableResource> ret = new ArrayList<SecurableResource>();

        //add all sub-containers the user is allowed to read
        ret.addAll(ContainerManager.getChildren(this, user, ReadPermission.class));

        //add resources from study
        StudyService.Service sts = ServiceRegistry.get().getService(StudyService.Service.class);
        if (null != sts)
            ret.addAll(sts.getSecurableResources(this, user));

        //add report descriptors
        //this seems much more cumbersome than it should be
        try
        {
            Report[] reports = ReportService.get().getReports(user, this);
            for(Report report : reports)
            {
                SecurityPolicy policy = SecurityManager.getPolicy(report.getDescriptor());
                if (policy.hasPermission(user, AdminPermission.class))
                    ret.add(report.getDescriptor());
            }
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        //add pipeline root
        PipeRoot root = PipelineService.get().findPipelineRoot(this);
        if (null != root)
        {
            SecurityPolicy policy = SecurityManager.getPolicy(root);
            if (policy.hasPermission(user, AdminPermission.class))
                ret.add(root);
        }

        if (isRoot())
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null != ss)
            {
                ret.addAll(ss.getSecurableResources(user));
            }
        }

        return ret;
    }

    /**
     * Finds a securable resource within this container or child containers with the same id
     * as the given resource id.
     * @param resourceId The resource id to find
     * @param user The current user (searches only resources that user can see)
     * @return The resource or null if not found
     */
    @Nullable
    public SecurableResource findSecurableResource(String resourceId, User user)
    {
        if (null == resourceId)
            return null;

        if (getResourceId().equals(resourceId))
            return this;

        //recurse down all non-container resources
        SecurableResource resource = findSecurableResourceInContainer(resourceId, user, this);
        if (null != resource)
            return resource;

        //recurse down child containers
        for(Container child : getChildren())
        {
            //only look in child containers where the user has read perm
            if (child.hasPermission(user, ReadPermission.class))
            {
                resource = child.findSecurableResource(resourceId, user);

                if (null != resource)
                    return resource;
            }
        }

        return null;
    }

    protected SecurableResource findSecurableResourceInContainer(String resourceId, User user, SecurableResource parent)
    {
        SecurableResource resource = null;
        for(SecurableResource child : parent.getChildResources(user))
        {
            if (child instanceof Container)
                continue;

            if (child.getResourceId().equals(resourceId))
                return child;

            resource = findSecurableResourceInContainer(resourceId, user, child);
            if (null != resource)
                return resource;
        }

        return null;
    }


    public String toString()
    {
        return getClass().getName() + "@" + System.identityHashCode(this) + " " + _path + " " + _id;
    }


    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Container container = (Container) o;

        return !(_id != null ? !_id.equals(container._id) : container._id != null);
    }


    public int hashCode()
    {
        return _id.hashCode();
    }


    public static boolean isLegalName(String name, StringBuilder error)
    {
        if (null == name || 0 == name.trim().length())
        {
            error.append("Blank names are not allowed.");
            return false;
        }

        if (-1 != name.indexOf('/') || -1 != name.indexOf(';') || -1 != name.indexOf('\\'))
        {
            error.append("Slashes, semicolons, and backslashes are not allowed in folder names.");
            return false;
        }
        else if (name.startsWith("@"))
        {
            error.append("Folder name may not begin with '@'.");
            return false;
        }

        //Don't allow ISOControl characters as they are not handled well by the databases
        for( int i = 0; i < name.length(); ++i)
        {
            if (Character.isISOControl(name.charAt(i)))
            {
                error.append("Non-printable characters are not allowed in folder names.");
                return false;
            }
        }

        return true;
    }

    public ActionURL getStartURL(User user)
    {
        FolderType ft = getFolderType();
        if (!FolderType.NONE.equals(ft))
            return ft.getStartURL(this, user);

        Module module = getDefaultModule();
        if (module != null)
        {
            ActionURL helper = module.getTabURL(this, user);
            if (helper != null)
                return helper;
        }

        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(this);
    }

    public Module getDefaultModule()
    {
        if (isRoot())
            return null;

        try
        {
            if (_defaultModule == null)
            {
                Map props = PropertyManager.getProperties(getId(), "defaultModules");
                String defaultModuleName = (String) props.get("name");

                boolean initRequired = false;
                if (null == defaultModuleName || null == ModuleLoader.getInstance().getModule(defaultModuleName))
                {
                    defaultModuleName = "Portal";
                    initRequired = true;
                }
                Module defaultModule = ModuleLoader.getInstance().getModule(defaultModuleName);

                //set default module
                if (initRequired)
                    setDefaultModule(defaultModule);

                //ensure that default module is included in active module set
                //should be there already if it's not portal, but if it is portal, we have to add it for upgrade
                if (defaultModuleName.compareToIgnoreCase("Portal") == 0)
                {
                    Set<Module> modules = new HashSet<Module>(getActiveModules());
                    if (!modules.contains(defaultModule))
                    {
                        modules.add(defaultModule);
                        setActiveModules(modules);
                    }
                }

                _defaultModule = defaultModule;
            }
            return _defaultModule;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void setFolderType(FolderType folderType, Set<Module> ensureModules) throws SQLException
    {
        setFolderType(folderType);
        Set<Module> modules = new HashSet<Module>(folderType.getActiveModules());
        modules.addAll(ensureModules);
        setActiveModules(modules);
    }

    public void setFolderType(FolderType folderType) throws SQLException
    {
        ContainerManager.setFolderType(this, folderType);
    }

    public FolderType getFolderType()
    {
        return ContainerManager.getFolderType(this);
    }

    /**
     * Sets the default module for a "mixed" type folder. We try not to create
     * these any more. Instead each folder is "owned" by a module
     */
    public void setDefaultModule(Module module)
    {
        if (module == null)
            return;
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(getId(), "defaultModules", true);
        props.put("name", module.getName());

        PropertyManager.saveProperties(props);
        ContainerManager.notifyContainerChange(getId());
        _defaultModule = null;
    }


    public void setActiveModules(Set<Module> modules) throws SQLException
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(getId(), "activeModules", true);
        props.clear();
        for (Module module : modules)
        {
            if (null != module)
                props.put(module.getName(), Boolean.TRUE.toString());
        }
        PropertyManager.saveProperties(props);
        ContainerManager.notifyContainerChange(getId());
        _activeModules = null;
    }


    private void removeForward(final Portal.WebPart[] activeWebparts)
    {
        // we no longer use forwards: this concept has been replaced by the default-tab setting.
        // So, remove the setting here:
        boolean hasForward = false;
        List<Portal.WebPart> partList = new ArrayList<Portal.WebPart>(activeWebparts.length);
        for (Portal.WebPart part : activeWebparts)
        {
            if (!"forward".equals(part.getLocation()))
                partList.add(part);
            else
                hasForward = true;
        }
        if (hasForward)
        {
            Portal.saveParts(this, partList.toArray(new Portal.WebPart[partList.size()]));
        }
    }

    // UNDONE: (MAB) getActiveModules() and setActiveModules()
    // UNDONE: these don't feel like they belong on this class
    // UNDONE: move to ModuleLoader? 
    public Set<Module> getActiveModules()
    {
        return getActiveModules(false);
    }

    public Set<Module> getActiveModules(boolean init)
    {
        if (_activeModules == null)
        {
            //Short-circuit for root module
            if (isRoot())
            {
                //get active modules from database
                Set<Module> modules = new HashSet<Module>();
               _activeModules = Collections.unmodifiableSet(modules);
               return _activeModules;
            }

            Map<String, String> props = PropertyManager.getProperties(getId(), "activeModules");
            //get set of all modules
            List<Module> allModules = ModuleLoader.getInstance().getModules();
            //get active web parts for this container
            Portal.WebPart[] activeWebparts = Portal.getParts(this);
            //remove forward from active web parts
            removeForward(activeWebparts);

            // store active modules, checking first that the container still exists -- junit test creates and deletes
            // containers quickly and this check helps keep the search indexer from creating orphaned property sets.
            if (props.isEmpty() && init && null != ContainerManager.getForId(getId()))
            {
                //initialize properties cache
                PropertyManager.PropertyMap propsWritable = PropertyManager.getWritableProperties(getId(), "activeModules", true);
                props = propsWritable;

                if (isProject())
                {
                    // first time in this project: initialize active modules now, based on the active webparts
                    Map<String, Module> mapWebPartModule = new HashMap<String, Module>();
                    //get set of all web parts for all modules
                    for (Module module : allModules)
                    {
                        Collection<WebPartFactory> factories = module.getWebPartFactories();
                        if (factories != null)
                        {
                            for (WebPartFactory desc : factories)
                                mapWebPartModule.put(desc.getName(), module);
                        }
                    }

                    //get active modules based on which web parts are active
                    for (Portal.WebPart activeWebPart : activeWebparts)
                    {
                        if (!"forward".equals(activeWebPart.getLocation()))
                        {
                            //get module associated with this web part & add to props
                            Module activeModule = mapWebPartModule.get(activeWebPart.getName());
                            if (activeModule != null)
                                propsWritable.put(activeModule.getName(), Boolean.TRUE.toString());
                        }
                    }

                    // enable 'default' tabs:
                    for (Module module : allModules)
                    {
                        if (module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT)
                            propsWritable.put(module.getName(), Boolean.TRUE.toString());
                    }
                }
                else
                {
                    //if this is a subfolder, set active modules to inherit from parent
                    Set<Module> parentModules = getParent().getActiveModules();
                    for (Module module : parentModules)
                    {
                        //set the default module for the subfolder to be the default module of the parent.
                        Module parentDefault = getParent().getDefaultModule();

                        if (module.equals(parentDefault))
                            setDefaultModule(module);

                        propsWritable.put(module.getName(), Boolean.TRUE.toString());
                    }
                }
                PropertyManager.saveProperties(propsWritable);
            }

            Set<Module> modules = new HashSet<Module>();
            // add all modules found in user preferences:
            if (null != props)
            {
                for (String moduleName : props.keySet())
                {
                    Module module = ModuleLoader.getInstance().getModule(moduleName);
                    if (module != null)
                        modules.add(module);
                }
            }

           // ensure all modules for folder type are added (may have been added after save
            if (!getFolderType().equals(FolderType.NONE))
            {
                for (Module module : getFolderType().getActiveModules())
                {
                    // check for null, since there's no guarantee that a third-party folder type has all its
                    // active modules installed on this system (so nulls may end up in the list- bug 6757):
                    if (module != null)
                        modules.add(module);
                }
            }

            // add all 'always display' modules, remove all 'never display' modules:
            for (Module module : allModules)
            {
                if (module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_NEVER)
                    modules.remove(module);
            }

            _activeModules = Collections.unmodifiableSet(modules);
        }

        return _activeModules;
    }

    public boolean isDescendant(Container container)
    {
        if (null == container)
            return false;

        Container cur = getParent();
        while (null != cur)
        {
            if (cur.equals(container))
                return true;
            cur = cur.getParent();
        }
        return false;
    }

    /**
     * Searches descendants of this container recursively until it finds
     * one that has a name matching the name provided. Search is done
     * breadth-first (optimize for immediate child), and name matching is
     * case-insensitive.
     * @param name The name to find
     * @return Matching Container or null if not found.
     */
    @Nullable
    public Container findDescendant(String name)
    {
        return findDescendant(this, name);
    }

    private Container findDescendant(Container parent, String name)
    {
        for (Container child : parent.getChildren())
        {
            if (child.getName().equalsIgnoreCase(name))
                return child;
        }

        Container ret = null;
        for (Container child : parent.getChildren())
        {
            ret = findDescendant(child, name);
            if (null == ret)
                return ret;
        }
        return null;
    }

    public Map<String, Object> toJSON(User user)
    {
        Map<String, Object> containerProps = new HashMap<String,Object>();
        containerProps.put("name", getName());
        containerProps.put("id", getId());
        containerProps.put("path", getPath());
        containerProps.put("sortOrder", getSortOrder());
        containerProps.put("userPermissions", getPolicy().getPermsAsOldBitMask(user));
        containerProps.put("effectivePermissions", getPolicy().getPermissionNames(user));
        if (null != getDescription())
            containerProps.put("description", getDescription());
        containerProps.put("isWorkbook", isWorkbook());
        if (null != getTitle())
            containerProps.put("title", getTitle());

        return containerProps;
    }

    public static class ContainerException extends Exception
    {
        public ContainerException(String message)
        {
            super(message);
        }

        public ContainerException(String message, Throwable t)
        {
            super(message, t);
        }
    }

    private int compareSiblings(Container c1, Container c2)
    {
        int result = c1.getSortOrder() - c2.getSortOrder();
        if (result != 0)
            return result;
        return c1.getName().compareToIgnoreCase(c2.getName());
    }

    // returns in order from the root (e.g. /project/folder/)
    private List<Container> getPathAsList()
    {
        List<Container> containerList = new ArrayList<Container>();
        Container current = this;
        while (!current.isRoot())
        {
            containerList.add(current);
            current = current.getParent();
        }
        Collections.reverse(containerList);
        return containerList;
    }

    public int compareTo(Container other)
    {
        // Container returns itself as a parent if it's root, so we need to special case that
        if (isRoot())
        {
            if (other.isRoot())
                return 0;
            else
                return -1;
        }
        if (other.isRoot())
        {
            return 1;
        }

        // Special case siblings which is common
        if (getParent().equals(other.getParent()))
        {
            return compareSiblings(this, other);
        }

        List<Container> myPath = getPathAsList();
        List<Container> otherPath = other.getPathAsList();
        for (int i=0; i<Math.min(myPath.size(), otherPath.size()); i++)
        {
            Container myContainer = myPath.get(i);
            Container otherContainer = otherPath.get(i);
            if (myContainer.equals(otherContainer))
                continue;
            return compareSiblings(myContainer, otherContainer);
        }

        // They're equal up to the end, but one is longer. E.g. /a/b/c vs /a/b
        return myPath.size() - otherPath.size();
    }

    @NotNull
    public String getResourceId()
    {
        return _id.toString();
    }

    @NotNull
    public String getResourceDescription()
    {
        return "The folder " + getPath();
    }

    @NotNull
    public Set<Class<? extends Permission>> getRelevantPermissions()
    {
        return RoleManager.BasicPermissions;
    }

    @NotNull
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getCoreModule();
    }

    @NotNull
    public Container getResourceContainer()
    {
        return this;
    }

    public SecurableResource getParentResource()
    {
        SecurableResource parent = getParent();
        return this.equals(getParent()) ? null : parent;
    }

    public boolean mayInheritPolicy()
    {
        return true;
    }

    /**
     * Returns the non-unique title for this Container, or the Container's name if a title is not set
     * @return the title
     */
    public String getTitle()
    {
        return null != _title ? _title : getName();
    }

    public void setTitle(String title)
    {
        _title = title;
    }
}
