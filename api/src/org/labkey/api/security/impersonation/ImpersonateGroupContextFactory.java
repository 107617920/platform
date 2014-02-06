/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.api.security.impersonation;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupMembershipCache;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

/**
 * User: adam
 * Date: 11/9/11
 * Time: 10:23 PM
 */
public class ImpersonateGroupContextFactory implements ImpersonationContextFactory
{
    private final @Nullable GUID _projectId;
    private final int _groupId;
    private final URLHelper _returnURL;
    private final int _adminUserId;

    public ImpersonateGroupContextFactory(@Nullable Container project, User adminUser, Group group, URLHelper returnURL)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _adminUserId = adminUser.getUserId();
        _groupId = group.getUserId();
        _returnURL = returnURL;
    }

    @Override
    public ImpersonationContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);
        Group group = SecurityManager.getGroup(_groupId);

        return new ImpersonateGroupContext(project, getAdminUser(), group, _returnURL);
    }


    @Override
    public void startImpersonating(ViewContext context)
    {
        // Retrieving the context will get the groups and force permissions check
        getImpersonationContext();
        // TODO: Audit log?
    }

    @Override
    public void stopImpersonating(HttpServletRequest request)
    {
        // TODO: Audit log?
    }

    @Override
    public User getAdminUser()
    {
        return UserManager.getUser(_adminUserId);
    }

    private static boolean canImpersonateGroup(@Nullable Container project, User user, Group group)
    {
        // Site admin can impersonate any group
        if (user.isSiteAdmin())
            return true;

        // Project admin...
        if (null != project && project.hasPermission(user, AdminPermission.class))
        {
            // ...can impersonate any project group but must be a member of a site group to impersonate it
            if (group.isProjectGroup())
                return group.getContainer().equals(project.getId());
            else
                return user.isInGroup(group.getUserId());
        }

        return false;
    }

    static void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
        NavTree groupMenu = new NavTree("Group");
        UserUrls userURLs = PageFlowUtil.urlProvider(UserUrls.class);
        Collection<Group> groups = getValidImpersonationGroups(c, user);

        boolean addSeparator = false;

        // Site groups are always first, followed by project groups
        for (Group group : groups)
        {
            String display = group.getName();

            if (!group.isProjectGroup())
            {
                display = "Site: " + display;
                // We have at least one site group... so add a separator (if we also have project groups)
                addSeparator = true;
            }
            else if (addSeparator)
            {
                // Our first project group after site groups... add a separator
                groupMenu.addSeparator();
                addSeparator = false;
            }

            groupMenu.addChild(display, userURLs.getImpersonateGroupURL(c, group.getUserId(), currentURL));
        }

        menu.addChild(groupMenu);
    }

    public static Collection<Group> getValidImpersonationGroups(Container c, User user)
    {
        LinkedList<Group> validGroups = new LinkedList<>();
        Group[] groups = SecurityManager.getGroups(c.getProject(), true);
        Container project = c.getProject();

        // Site groups are always first, followed by project groups
        for (Group group : groups)
            if (canImpersonateGroup(project, user, group))
                validGroups.add(group);

        return validGroups;
    }

    public class ImpersonateGroupContext implements ImpersonationContext
    {
        private final Group _group;
        private final int[] _groups;
        private final URLHelper _returnURL;
        private final User _adminUser;

        private ImpersonateGroupContext(@Nullable Container project, User user, Group group, URLHelper returnURL)
        {
            if (!canImpersonateGroup(project, user, group))
                throw new UnauthorizedImpersonationException("You are not allowed to impersonate this group", getFactory());

            // Seed the group list with guests, site users, and the passed in group (as appropriate)
            LinkedList<Integer> seedGroups = new LinkedList<>();
            // Everyone always gets Guests
            seedGroups.add(Group.groupGuests);

            // Non-guest group gets site users
            if (!group.isGuests())
            {
                seedGroups.add(Group.groupUsers);

                // Non-site users group gets the requested group
                if (!group.isUsers())
                    seedGroups.add(group.getUserId());
            }

            // Now expand the list of groups to include all groups they belong to (see #13802)
            _groups = GroupMembershipCache.computeAllGroups(seedGroups);
            _returnURL = returnURL;
            _group = group;
            _adminUser = user;
        }

        @Override
        public boolean isImpersonating()
        {
            return true;
        }

        @Override
        public boolean isAllowedGlobalRoles()
        {
            return false;
        }

        @Override
        @Nullable
        public Container getImpersonationProject()
        {
            return null;
        }

        @Override
        public User getAdminUser()
        {
            return _adminUser;
        }

        @Override
        public String getNavTreeCacheKey()
        {
            // NavTree for user impersonating a group will be different for each group
            return "/impersonationGroup=" + _group.getUserId();
        }

        @Override
        public URLHelper getReturnURL()
        {
            return _returnURL;
        }

        @Override
        public ImpersonationContextFactory getFactory()
        {
            return ImpersonateGroupContextFactory.this;
        }

        @Override
        public int[] getGroups(User user)
        {
            return _groups;
        }

        @Override
        public Set<Role> getContextualRoles(User user, SecurityPolicy policy)
        {
            return user.getStandardContextualRoles();
        }

        @Override
        public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
        {
            // If impersonating a group, don't add an impersonation menu
        }
    }
}
