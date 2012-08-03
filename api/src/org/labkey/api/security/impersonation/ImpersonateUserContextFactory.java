/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.GroupManager;
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
import javax.servlet.http.HttpSession;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
* User: adam
* Date: 11/9/11
*/

// We stash simple properties (container and user id) in session and turn them into a context with objects on each request
public class ImpersonateUserContextFactory implements ImpersonationContextFactory
{
    private final @Nullable GUID _projectId;
    private final int _adminUserId;
    private final int _impersonatedUserId;
    private final URLHelper _returnURL;
    /** Don't remove/rename without updating PipelineStatusManager.getXStream() */
    private final Map<String, Object> _adminSessionAttributes = new HashMap<String, Object>();

    public ImpersonateUserContextFactory(@Nullable Container project, User adminUser, User impersonatedUser, URLHelper returnURL)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _adminUserId = adminUser.getUserId();
        _impersonatedUserId = impersonatedUser.getUserId();
        _returnURL = returnURL;
    }


    public User getAdminUser()
    {
        return UserManager.getUser(_adminUserId);
    }


    @Override
    public ImpersonationContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);

        return new ImpersonateUserContext(project, getAdminUser(), UserManager.getUser(_impersonatedUserId), _returnURL);
    }


    @Override
    public void startImpersonating(ViewContext context)
    {
        getImpersonationContext();
        HttpServletRequest request = context.getRequest();
        User impersonatedUser = UserManager.getUser(_impersonatedUserId);
        User adminUser = getAdminUser();

        // We clear the session when we impersonate a user; we stash all the admin's session attributes in the factory
        // (which gets put into session) so we can reinstate them after impersonation is over.
        _adminSessionAttributes.clear();
        HttpSession adminSession = request.getSession(true);
        Enumeration names = adminSession.getAttributeNames();

        while (names.hasMoreElements())
        {
            String name = (String) names.nextElement();
            _adminSessionAttributes.put(name, adminSession.getAttribute(name));
        }

        // This clears the session; caller will add the factory (including the admin's session attributes)
        SecurityManager.setAuthenticatedUser(request, impersonatedUser);

        AuditLogService.get().addEvent(adminUser, context.getContainer(), UserManager.USER_AUDIT_EVENT, adminUser.getUserId(),
                adminUser.getEmail() + " impersonated " + impersonatedUser.getEmail());
        AuditLogService.get().addEvent(adminUser, context.getContainer(), UserManager.USER_AUDIT_EVENT, impersonatedUser.getUserId(),
                impersonatedUser.getEmail() + " was impersonated by " + adminUser.getEmail());
    }


    @Override
    public void stopImpersonating(HttpServletRequest request)
    {
        SecurityManager.invalidateSession(request);
        HttpSession adminSession = request.getSession(true);

        for (Map.Entry<String, Object> entry : _adminSessionAttributes.entrySet())
            adminSession.setAttribute(entry.getKey(), entry.getValue());

        User impersonatedUser = UserManager.getUser(_impersonatedUserId);

        if (null != impersonatedUser)
        {
            User adminUser = getAdminUser();
            Container project = null == _projectId ? ContainerManager.getRoot() : ContainerManager.getForId(_projectId);

            AuditLogService.get().addEvent(adminUser, project, UserManager.USER_AUDIT_EVENT, impersonatedUser.getUserId(),
                impersonatedUser.getEmail() + " was no longer impersonated by " + adminUser.getEmail());
            AuditLogService.get().addEvent(adminUser, project, UserManager.USER_AUDIT_EVENT, adminUser.getUserId(),
                adminUser.getEmail() + " stopped impersonating " + impersonatedUser.getEmail());
        }
    }


    static void addMenu(NavTree menu, Container c, User user)
    {
        UserUrls userURLs = PageFlowUtil.urlProvider(UserUrls.class);
        AdminUrls adminURLs = PageFlowUtil.urlProvider(AdminUrls.class);
        ActionURL impersonateURL = user.isAdministrator() ? adminURLs.getAdminConsoleURL() : userURLs.getProjectUsersURL(c.getProject());
        NavTree userMenu = new NavTree("User", impersonateURL);
        menu.addChild(userMenu);
    }


    private class ImpersonateUserContext implements ImpersonationContext
    {
        private final @Nullable Container _project;
        private final User _adminUser;
        private final URLHelper _returnURL;

        private ImpersonateUserContext(@Nullable Container project, User adminUser, User impersonatedUser, URLHelper returnURL)
        {
            verifyPermissions(project, impersonatedUser, adminUser);
            _project = project;
            _adminUser = adminUser;
            _returnURL = returnURL;
        }

        private void verifyPermissions(@Nullable Container project, User impersonatedUser, User adminUser)
        {
            if (impersonatedUser.equals(adminUser))
                throw new UnauthorizedImpersonationException("Can't impersonate yourself", getFactory());

            if (adminUser.isAdministrator())
                return;

            // Project admin...
            if (null != project && project.hasPermission(adminUser, AdminPermission.class) && SecurityManager.getProjectUsersIds(project).contains(impersonatedUser.getUserId()))
                return;

            throw new UnauthorizedImpersonationException("Can't impersonate this user", getFactory());
        }

        @Override
        public boolean isImpersonating()
        {
            return true;
        }

        @Override
        public @Nullable Container getImpersonationProject()
        {
            return _project;
        }

        @Override
        public boolean isAllowedGlobalRoles()
        {
            // Don't allow global roles (site admin, developer, etc.) if user is being impersonated within a project
            return null == _project;
        }

        @Override
        public User getAdminUser()
        {
            return _adminUser;
        }

        @Override
        public String getNavTreeCacheKey()
        {
            // NavTree for user being impersonated will be different per impersonating user per project
            String suffix = "/impersonatingUser=" + getAdminUser().getUserId();

            if (null != _project)
                suffix += "/impersonationProject=" + _project.getId();

            return suffix;
        }

        @Override
        public URLHelper getReturnURL()
        {
            return _returnURL;
        }

        @Override
        public ImpersonationContextFactory getFactory()
        {
            return ImpersonateUserContextFactory.this;
        }

        @Override
        public int[] getGroups(User user)
        {
            return GroupManager.getAllGroupsForPrincipal(user);
        }

        @Override
        public Set<Role> getContextualRoles(User user, SecurityPolicy policy)
        {
            return user.getStandardContextualRoles();
        }

        @Override
        public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
        {
            // If impersonating a user, don't add an impersonation menu
        }
    }
}
