/*
 * Copyright (c) 2005-2011 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.security;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.Project;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.impersonation.ImpersonateUserContext;
import org.labkey.api.security.impersonation.ImpersonationContextFactory;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.wiki.WikiService;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Note should consider implementing a Tomcat REALM, but we've tried to avoid
 * being tomcat specific.
 */

public class SecurityManager
{
    private static final Logger _log = Logger.getLogger(SecurityManager.class);
    private static final CoreSchema core = CoreSchema.getInstance();
    private static final List<ViewFactory> _viewFactories = new ArrayList<ViewFactory>();

    static final String NULL_GROUP_ERROR_MESSAGE = "Null group not allowed";
    static final String NULL_PRINCIPAL_ERROR_MESSAGE = "Null principal not allowed";
    static final String ALREADY_A_MEMBER_ERROR_MESSAGE = "Principal is already a member of this group";
    static final String ADD_GROUP_TO_ITSELF_ERROR_MESSAGE = "Can't add a group to itself";
    static final String ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE = "Can't add a group to a system group";
    static final String ADD_SYSTEM_GROUP_ERROR_MESSAGE = "Can't add a system group to another group";
    static final String DIFFERENT_PROJECTS_ERROR_MESSAGE =  "Can't add a project group to a group in a different project";
    static final String PROJECT_TO_SITE_ERROR_MESSAGE =  "Can't add a project group to a site group";
    static final String CIRCULAR_GROUP_ERROR_MESSAGE = "Can't add a group that results in a circular group relation";

    public static final String TERMS_OF_USE_WIKI_NAME = "_termsOfUse";

    static
    {
        EmailTemplateService.get().registerTemplate(RegistrationEmailTemplate.class);
        EmailTemplateService.get().registerTemplate(RegistrationAdminEmailTemplate.class);
        EmailTemplateService.get().registerTemplate(PasswordResetEmailTemplate.class);
        EmailTemplateService.get().registerTemplate(PasswordResetAdminEmailTemplate.class);
    }

    public enum PermissionSet
    {
        ADMIN("Admin (all permissions)", ACL.PERM_ALLOWALL),
        EDITOR("Editor", ACL.PERM_READ | ACL.PERM_DELETE | ACL.PERM_UPDATE | ACL.PERM_INSERT),
        AUTHOR("Author", ACL.PERM_READ | ACL.PERM_DELETEOWN | ACL.PERM_UPDATEOWN | ACL.PERM_INSERT),
        READER("Reader", ACL.PERM_READ),
        RESTRICTED_READER("Restricted Reader", ACL.PERM_READOWN),
        SUBMITTER("Submitter", ACL.PERM_INSERT),
        NO_PERMISSIONS("No Permissions", 0);

        private int _permissions;
        private String _label;

        private PermissionSet(String label, int permissions)
        {
            // the following must be true for normalization to work:
            assert ACL.PERM_READOWN == ACL.PERM_READ << 4;
            assert ACL.PERM_UPDATEOWN == ACL.PERM_UPDATE << 4;
            assert ACL.PERM_DELETEOWN == ACL.PERM_DELETE << 4;
            _permissions = permissions;
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        public int getPermissions()
        {
            return _permissions;
        }

        private static int normalizePermissions(int permissions)
        {
            permissions |= (permissions & (ACL.PERM_READ | ACL.PERM_UPDATE | ACL.PERM_DELETE)) << 4;
            return permissions;
        }

        public static PermissionSet findPermissionSet(int permissions)
        {
            for (PermissionSet set : values())
            {
                // we try normalizing because a permissions value with just reader set is equivalent
                // to a permissions value with reader and read_own set.
                if (set.getPermissions() == permissions || normalizePermissions(set.getPermissions()) == permissions)
                    return set;
            }
            return null;
        }
    }


    private SecurityManager()
    {
    }


    private static boolean init = false;

    public static void init()
    {
        if (init)
            return;
        init = true;

        // HACK: I really want to make sure we don't have orphaned Groups.typeProject groups
        //
        // either because
        //  a) the container is non-existant or
        //  b) the container is not longer a project

        scrubTables();

        ContainerManager.addContainerListener(new SecurityContainerListener());
    }


    //
    // GroupListener
    //

    public interface GroupListener extends PropertyChangeListener
    {
        void principalAddedToGroup(Group group, UserPrincipal principal);

        void principalDeletedFromGroup(Group group, UserPrincipal principal);
    }

    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<GroupListener> _listeners = new CopyOnWriteArrayList<GroupListener>();

    public static void addGroupListener(GroupListener listener)
    {
        _listeners.add(listener);
    }

    private static List<GroupListener> getListeners()
    {
        return _listeners;
    }

    private static void fireAddPrincipalToGroup(Group group, UserPrincipal user)
    {
        if (user == null)
            return;
        List<GroupListener> list = getListeners();
        for (GroupListener GroupListener : list)
        {
            try
            {
                GroupListener.principalAddedToGroup(group, user);
            }
            catch (Throwable t)
            {
                _log.error("fireAddPrincipalToGroup", t);
            }
        }
    }

    protected static List<Throwable> fireDeletePrincipalFromGroup(int groupId, UserPrincipal user)
    {
        List<Throwable> errors = new ArrayList<Throwable>();
        if (user == null)
            return errors;

        Group group = getGroup(groupId);

        List<GroupListener> list = getListeners();
        for (GroupListener gl : list)
        {
            try
            {
                gl.principalDeletedFromGroup(group, user);
            }
            catch (Throwable t)
            {
                _log.error("fireDeletePrincipalFromGroup", t);
                errors.add(t);
            }
        }
        return errors;
    }

    private static void scrubTables()
    {
        try
        {
            Container root = ContainerManager.getRoot();

            // missing container
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoPrincipals() + "\n" +
                    "WHERE Container NOT IN (SELECT EntityId FROM " + core.getTableInfoContainers() + ")");

            // container is not a project (but should be)
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoPrincipals() + "\n" +
                    "WHERE Type='g' AND Container NOT IN (SELECT EntityId FROM " + core.getTableInfoContainers() + "\n" +
                    "\tWHERE Parent=? OR Parent IS NULL)", root);

            // missing group
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoMembers() + "\n" +
                    "WHERE GroupId NOT IN (SELECT UserId FROM " + core.getTableInfoPrincipals() + ")");

            // missing user
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoMembers() + "\n" +
                    "WHERE UserId NOT IN (SELECT UserId FROM " + core.getTableInfoPrincipals() + ")");
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
    }


    private static class SecurityContainerListener implements ContainerManager.ContainerListener
    {
        //void wantsToDelete(Container c, List<String> messages);
        public void containerCreated(Container c, User user)
        {
        }

        public void containerDeleted(Container c, User user)
        {
            deleteGroups(c, null);
        }

        @Override
        public void containerMoved(Container c, Container oldParent, User user)
        {            
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
            /* NOTE move is handled by direct call from ContainerManager into SecurityManager */
        }
    }



    // Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
    public static User authenticateBasic(String basic)
    {
        try
        {
            byte[] decode = Base64.decodeBase64(basic.getBytes());
            String auth = new String(decode);
            int colon = auth.indexOf(':');
            if (-1 == colon)
                return null;
            String rawEmail = auth.substring(0, colon);
            String password = auth.substring(colon+1);
            if (rawEmail.toLowerCase().equals("guest"))
                return User.guest;
            new ValidEmail(rawEmail);  // validate email address

            return AuthenticationManager.authenticate(rawEmail, password);
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            return null;  // Invalid email means failed auth
        }
    }


    // This user has been authenticated, but may not exist (if user was added to the database and is visiting for the first
    //  time or user authenticated using LDAP, SSO, etc.)
    public static User afterAuthenticate(ValidEmail email)
    {
        User u = UserManager.getUser(email);

        // If user is authenticated but doesn't exist in our system then
        // add user to the database... must be an LDAP or SSO user's first visit
        if (null == u)
        {
            try
            {
                NewUserStatus bean = addUser(email, false);
                u = bean.getUser();
                UserManager.addToUserHistory(u, u.getEmail() + " authenticated successfully and was added to the system automatically.");
            }
            catch (UserManagementException e)
            {
                // do nothing; we'll fall through and return null.
            }
        }

        if (null != u)
            UserManager.updateLogin(u);

        return u;
    }


    public static final String AUTHENTICATION_METHOD = "SecurityManager.authenticationMethod";

    public static User getAuthenticatedUser(HttpServletRequest request)
    {
        User u = (User) request.getUserPrincipal();

        if (null == u)
        {
            User sessionUser = null;
            HttpSession session = request.getSession(true);

            Integer userId = (Integer) session.getAttribute(USER_ID_KEY);

            if (null != userId)
                sessionUser = UserManager.getUser(userId);

            if (null != sessionUser)
            {
                ImpersonationContextFactory factory = (ImpersonationContextFactory)session.getAttribute(IMPERSONATION_CONTEXT_FACTORY_KEY);

                if (null != factory)
                {
                    sessionUser.setImpersonationContext(factory.getImpersonationContext());
                }

                // We want groups membership to be calculated on every request (but just once)
                // the cloned User will calculate groups exactly once
                // NOTE: getUser() returns a cloned object
                // u = sessionUser.cloneUser();
                assert sessionUser._groups == null;
                sessionUser._groups = null;

                // Is user impersonating a group?
                Group group = (Group)session.getAttribute(IMPERSONATE_GROUP_KEY);

                if (null != group)
                {
                    // TODO: Redo this
                    u = new LimitedUser(sessionUser, getImpersonationGroups(sessionUser, group));
                    //u.setImpersonatingGroup(group);
                }
                else
                {
                    u = sessionUser;
                }
            }
        }

        if (null == u)
        {
            // Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
            String authorization = request.getHeader("Authorization");
            if (null != authorization && authorization.startsWith("Basic"))
            {
                u = authenticateBasic(authorization.substring("Basic".length()).trim());
                if (null != u)
                {
                    request.setAttribute(AUTHENTICATION_METHOD, "Basic");
                    SecurityManager.setAuthenticatedUser(request, u);
                    // accept Guest as valid credentials from authenticateBasic()
                    return u;
                }
            }
        }

        return null == u || u.isGuest() ? null : u;
    }

    private static final String USER_ID_KEY = User.class.getName() + "$userId";

    private static final String IMPERSONATORS_SESSION_MAP_KEY = "ImpersonatorsSessionMapKey";
    private static final String IMPERSONATE_GROUP_KEY = "ImpersonateGroupKey";
    private static final String IMPERSONATION_CONTEXT_FACTORY_KEY = User.class.getName() + "$ImpersonationContextFactoryKey";

    public static HttpSession setAuthenticatedUser(HttpServletRequest request, User user)
    {
        invalidateSession(request);      // Clear out terms-of-use and other session info that guest / previous user may have

        HttpSession newSession = request.getSession(true);
        newSession.setAttribute(USER_ID_KEY, user.getUserId());
        newSession.setAttribute("LABKEY.username", user.getName());

        return newSession;
    }


    public static void setAuthenticatedUser(HttpServletRequest request, User user, @Nullable User impersonatingUser, @Nullable Container project, @Nullable URLHelper returnURL)
    {
        HttpSession session = setAuthenticatedUser(request, user);

        if (null != impersonatingUser)
        {
            ImpersonationContextFactory factory = new ImpersonateUserContext.ImpersonateUserContextFactory(project, impersonatingUser, returnURL);
            session.setAttribute(IMPERSONATION_CONTEXT_FACTORY_KEY, factory);
        }
    }


    public static void logoutUser(HttpServletRequest request, User user)
    {
        AuthenticationManager.logout(user, request);   // Let AuthenticationProvider clean up auth-specific cookies, etc.
        invalidateSession(request);
    }


    public static void impersonateUser(ViewContext viewContext, User impersonatedUser, @Nullable Container project, URLHelper returnURL)
    {
        HttpServletRequest request = viewContext.getRequest();
        User adminUser = viewContext.getUser();

        // We clear the session when we impersonate; we stash the admin's session attributes in the new
        // session so we can reinstate them after impersonation is over.
        Map<String, Object> impersonatorSessionAttributes = new HashMap<String, Object>();
        HttpSession impersonatorSession = request.getSession(true);
        Enumeration names = impersonatorSession.getAttributeNames();

        while (names.hasMoreElements())
        {
            String name = (String) names.nextElement();
            impersonatorSessionAttributes.put(name, impersonatorSession.getAttribute(name));
        }

        SecurityManager.setAuthenticatedUser(request, impersonatedUser, adminUser, project, returnURL);
        HttpSession userSession = request.getSession(true);
        userSession.setAttribute(IMPERSONATORS_SESSION_MAP_KEY, impersonatorSessionAttributes);

        AuditLogService.get().addEvent(viewContext, UserManager.USER_AUDIT_EVENT, adminUser.getUserId(),
                adminUser.getEmail() + " impersonated " + impersonatedUser.getEmail());
        AuditLogService.get().addEvent(viewContext, UserManager.USER_AUDIT_EVENT, impersonatedUser.getUserId(),
                impersonatedUser.getEmail() + " was impersonated by " + adminUser.getEmail());
    }


    public static void impersonateGroup(ViewContext viewContext, Group group, URLHelper returnURL)
    {
        // Validate that user can impersonate this group
        getImpersonationGroups(viewContext.getUser(), group);

        HttpServletRequest request = viewContext.getRequest();
        HttpSession session = request.getSession(true);

        session.setAttribute(IMPERSONATE_GROUP_KEY, group);

        // TODO: Save resturnURL
        // TODO: Need to audit?
    }


    public static int[] getImpersonationGroups(User user, Group group)
    {
        String id = group.getContainer();
        Container c = (null == id ? ContainerManager.getRoot() : ContainerManager.getForId(id));

        if (!canImpersonateGroup(c, user, group))
            throw new IllegalStateException("You are not allowed to impersonate this group");

        if (group.isGuests())
            return groups(Group.groupGuests);
        else if (group.isUsers())
            return groups(Group.groupGuests, Group.groupUsers);
        else
            return groups(Group.groupGuests, Group.groupUsers, group.getUserId());
    }


    public static boolean canImpersonateGroup(Container c, User user, Group group)
    {
        // Site admin can impersonate any group
        if (user.isAdministrator())
            return true;

        // TODO: c.getProject()?  Need to be project admin?

        // Project admin...
        if (c.hasPermission(user, AdminPermission.class))
        {
            // ...can impersonate any project group but must be a member of a site group to impersonate it
            if (group.isProjectGroup())
                return group.getContainer().equals(c.getId());
            else
                return user.isInGroup(group.getUserId());
        }

        return false;
    }


    // Ensure they are sorted
    private static int[] groups(int... ids)
    {
        Arrays.sort(ids);
        return ids;
    }


    public static void stopImpersonating(ViewContext viewContext, User impersonatedUser)
    {
        assert impersonatedUser.isImpersonated();
        HttpServletRequest request = viewContext.getRequest();

        if (impersonatedUser.isImpersonated())
        {
            User adminUser = impersonatedUser.getImpersonatingUser();

            HttpSession userSession = request.getSession(true);
            Map<String, Object> impersonatorSessionAttributes = (Map<String, Object>)userSession.getAttribute(IMPERSONATORS_SESSION_MAP_KEY);

            assert null != impersonatorSessionAttributes;

            if (null != impersonatorSessionAttributes)
            {
                invalidateSession(request);
                HttpSession impersonatorSession = request.getSession(true);

                for (Map.Entry<String, Object> entry : impersonatorSessionAttributes.entrySet())
                    impersonatorSession.setAttribute(entry.getKey(), entry.getValue());
            }
            else
            {
                // Just in case
                setAuthenticatedUser(request, adminUser);
            }

            AuditLogService.get().addEvent(viewContext, UserManager.USER_AUDIT_EVENT, impersonatedUser.getUserId(),
                impersonatedUser.getEmail() + " was no longer impersonated by " + adminUser.getEmail());
            AuditLogService.get().addEvent(viewContext, UserManager.USER_AUDIT_EVENT, adminUser.getUserId(),
                adminUser.getEmail() + " stopped impersonating " + impersonatedUser.getEmail());
        }
        else
        {
            invalidateSession(request);
        }
    }


    private static void invalidateSession(HttpServletRequest request)
    {
        HttpSession s = request.getSession();
        if (null != s)
            s.invalidate();
    }


    private static final String passwordChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static final int tempPasswordLength = 32;

    public static String createTempPassword()
    {
        StringBuilder tempPassword = new StringBuilder(tempPasswordLength);

        for (int i = 0; i < tempPasswordLength; i++)
            tempPassword.append(passwordChars.charAt((int) Math.floor((Math.random() * passwordChars.length()))));

        return tempPassword.toString();
    }


    public static ActionURL createVerificationURL(Container c, ValidEmail email, String verification, @Nullable Pair<String, String>[] extraParameters)
    {
        return PageFlowUtil.urlProvider(LoginUrls.class).getVerificationURL(c, email, verification, extraParameters);
    }


    // Test if non-LDAP email has been verified
    public static boolean isVerified(ValidEmail email) throws UserManagementException
    {
        return (null == getVerification(email));
    }


    public static boolean verify(ValidEmail email, String verification) throws UserManagementException
    {
        String dbVerification = getVerification(email);
        return (dbVerification != null && dbVerification.equals(verification));
    }


    public static void setVerification(ValidEmail email, @Nullable String verification) throws UserManagementException
    {
        try
        {
            int rows = Table.execute(core.getSchema(), "UPDATE " + core.getTableInfoLogins() + " SET Verification=? WHERE email=?", verification, email.getEmailAddress());
            if (1 != rows)
                throw new UserManagementException(email, "Unexpected number of rows returned when setting verification: " + rows);
        }
        catch (SQLException e)
        {
            _log.error("setVerification: ", e);
            throw new UserManagementException(email, e);
        }
    }


    public static String getVerification(ValidEmail email) throws UserManagementException
    {
        try
        {
            return Table.executeSingleton(core.getSchema(), "SELECT Verification FROM " + core.getTableInfoLogins() + " WHERE email=?", new Object[]{email.getEmailAddress()}, String.class);
        }
        catch (SQLException e)
        {
            _log.error("verify: ", e);
            throw new UserManagementException(email, e);
        }
    }


    public static class NewUserStatus
    {
        private final ValidEmail _email;

        private String _verification;
        private User _user;

        public NewUserStatus(ValidEmail email)
        {
            _email = email;
        }

        public ValidEmail getEmail()
        {
            return _email;
        }

        public boolean isLdapEmail()
        {
            return SecurityManager.isLdapEmail(_email);
        }

        public String getVerification()
        {
            return _verification;
        }

        public void setVerification(String verification)
        {
            _verification = verification;
        }

        public User getUser()
        {
            return _user;
        }

        public void setUser(User user)
        {
            _user = user;
        }

        public boolean getHasLogin()
        {
            return null != _verification;
        }
    }


    public static class UserManagementException extends Exception
    {
        private final String _email;

        public UserManagementException(ValidEmail email, String message)
        {
            super(message);
            _email = email.getEmailAddress();
        }

        public UserManagementException(ValidEmail email, String message, Exception cause)
        {
            super(message, cause);
            _email = email.getEmailAddress();
        }

        public UserManagementException(ValidEmail email, Exception cause)
        {
            super(cause);
            _email = email.getEmailAddress();
        }

        public UserManagementException(String email, Exception cause)
        {
            super(cause);
            _email = email;
        }

        public String getEmail()
        {
            return _email;
        }
    }

    public static class UserAlreadyExistsException extends UserManagementException
    {
        public UserAlreadyExistsException(ValidEmail email)
        {
            this(email, "User already exists");
        }

        public UserAlreadyExistsException(ValidEmail email, String message)
        {
            super(email, message);
        }
    }

    public static NewUserStatus addUser(ValidEmail email) throws UserManagementException
    {
        return addUser(email, true);
    }

    // createLogin == false in the case of a new LDAP or SSO user authenticating for the first time.
    // createLogin == true in any other case (e.g., manually added LDAP user), so we need an additional check below
    // to avoid sending verification emails to an LDAP user.
    public static NewUserStatus addUser(ValidEmail email, boolean createLogin) throws UserManagementException
    {
        NewUserStatus status = new NewUserStatus(email);

        if (UserManager.userExists(email))
            throw new UserAlreadyExistsException(email);

        if (null != UserManager.getUserByDisplayName(email.getEmailAddress()))
            throw new UserAlreadyExistsException(email, "Display name is already in use");

        User newUser;
        DbScope scope = core.getSchema().getScope();

        try
        {
            scope.ensureTransaction();

            if (createLogin && !status.isLdapEmail())
            {
                String verification = SecurityManager.createLogin(email);
                status.setVerification(verification);
            }

            try
            {
                Integer userId = null;

                // Add row to Principals
                Map<String, Object> fieldsIn = new HashMap<String, Object>();
                fieldsIn.put("Name", email.getEmailAddress());
                fieldsIn.put("Type", GroupManager.PrincipalType.USER.typeChar);
                try
                {
                    Map returnMap = Table.insert(null, core.getTableInfoPrincipals(), fieldsIn);
                    userId = (Integer) returnMap.get("UserId");
                }
                catch (SQLException e)
                {
                    if (!"23000".equals(e.getSQLState()))
                    {
                        _log.debug("createUser: Something failed user: " + email, e);
                        throw e;
                    }
                }

                try
                {
                    // If insert didn't return an id it must already exist... select it
                    if (null == userId)
                        userId = Table.executeSingleton(core.getSchema(),
                                "SELECT UserId FROM " + core.getTableInfoPrincipals() + " WHERE Name = ?",
                                new Object[]{email.getEmailAddress()}, Integer.class);
                }
                catch (SQLException x)
                {
                    _log.debug("createUser: Something failed user: " + email, x);
                    throw x;
                }

                if (null == userId)
                {
                    assert false : "User should either exist or not; synchronization problem?";
                    _log.debug("createUser: Something failed user: " + email);
                    return null;
                }

                //
                // Add row to UsersData table
                //
                try
                {
                    Map<String, Object> m = new HashMap<String, Object>();
                    m.put("UserId", userId);
                    m.put("DisplayName", null == email.getPersonal() ? email.getEmailAddress() : email.getPersonal());
                    Table.insert(null, core.getTableInfoUsersData(), m);
                }
                catch (SQLException x)
                {
                    if (!"23000".equals(x.getSQLState()))
                    {
                        _log.debug("createUser: Something failed user: " + email, x);
                        throw x;
                    }
                }

                UserManager.clearUserList(userId);

                newUser = UserManager.getUser(userId);
                UserManager.fireAddUser(newUser);
            }
            catch (SQLException e)
            {
                throw new UserManagementException(email, "Unable to create user.", e);
            }

            if (null == newUser)
                throw new UserManagementException(email, "Couldn't create user.");

            scope.commitTransaction();

            status.setUser(newUser);

            return status;
        }
        catch (SQLException e)
        {
            throw new UserManagementException(email, "Unable to create user.", e);
        }
        finally
        {
            scope.closeConnection();
        }
    }


    public static void sendEmail(Container c, User user, SecurityMessage message, String to, ActionURL verificationURL) throws MessagingException
    {
        MimeMessage m = createMessage(c, user, message, to, verificationURL);
        MailHelper.send(m, user, c);
    }

    public static void renderEmail(Container c, User user, SecurityMessage message, String to, ActionURL verificationURL, Writer out) throws MessagingException
    {
        MimeMessage m = createMessage(c, user, message, to, verificationURL);
        MailHelper.renderHtml(m, message.getType(), out);
    }

    private static MimeMessage createMessage(Container c, User user, SecurityMessage message, String to, ActionURL verificationURL) throws MessagingException
    {
        try
        {
            message.setVerificationURL(verificationURL.getURIString());
            message.setOriginatingUser(user.getEmail());
            if (message.getTo() == null)
                message.setTo(to);

            MimeMessage m = message.createMailMessage(c);

            LookAndFeelProperties properties = LookAndFeelProperties.getInstance(c);
            m.addFrom(new Address[]{new InternetAddress(properties.getSystemEmailAddress(), properties.getShortName())});
            m.addRecipients(Message.RecipientType.TO, to);

            return m;
        }
        catch (UnsupportedEncodingException e)
        {
            throw new MessagingException("Failed to create InternetAddress.", e);
        }
        catch (Exception e)
        {
            throw new MessagingException("Failed to set template context.", e);
        }
    }

    // Create record for non-LDAP login, saving email address and hashed password.  Return verification token.
    public static String createLogin(ValidEmail email) throws UserManagementException
    {
        // Create a placeholder password hash and a separate email verification key that will get emailed to the new user
        String tempPassword = SecurityManager.createTempPassword();
        String verification = SecurityManager.createTempPassword();

        try
        {
            String crypt = "md5:" + Crypt.MD5.digest(tempPassword);

            // Don't need to set LastChanged -- it defaults to current date/time.
            int rowCount = Table.execute(core.getSchema(), "INSERT INTO " + core.getTableInfoLogins() +
                    " (Email, Crypt, LastChanged, Verification, PreviousCrypts) VALUES (?, ?, ?, ?, ?)",
                    email.getEmailAddress(), crypt, new Date(), verification, crypt);
            if (1 != rowCount)
                throw new UserManagementException(email, "Login creation statement affected " + rowCount + " rows.");

            return verification;
        }
        catch (SQLException e)
        {
            _log.error("createLogin", e);
            throw new UserManagementException(email, e);
        }
    }


    public static void setPassword(ValidEmail email, String password) throws UserManagementException
    {
        try
        {
            String crypt = "salt:" + Crypt.SaltMD5.digest(password);
            List<String> history = new ArrayList<String>(getCryptHistory(email.getEmailAddress()));
            history.add(crypt);

            // Remember only the last 10 password hashes
            int itemsToDelete = Math.max(0, history.size() - MAX_HISTORY);
            for (int i = 0; i < itemsToDelete; i++)
                history.remove(i);
            String cryptHistory = StringUtils.join(history, ",");

            int rows = Table.execute(core.getSchema(), "UPDATE " + core.getTableInfoLogins() + " SET Crypt=?, LastChanged=?, PreviousCrypts=? WHERE Email=?", crypt, new Date(), cryptHistory, email.getEmailAddress());
            if (1 != rows)
                throw new UserManagementException(email, "Password update statement affected " + rows + " rows.");
        }
        catch (SQLException e)
        {
            _log.error("setPassword", e);
            throw new UserManagementException(email, e);
        }
    }


    private static final int MAX_HISTORY = 10;

    private static List<String> getCryptHistory(String email)
    {
        Selector selector = new SqlSelector(core.getSchema(), new SQLFragment("SELECT PreviousCrypts FROM " + core.getTableInfoLogins() + " WHERE Email=?", email));
        String cryptHistory = selector.getObject(String.class);
        List<String> fixedList = Arrays.asList(cryptHistory.split(","));
        assert fixedList.size() <= MAX_HISTORY;

        return fixedList;
    }


    public static boolean matchesPreviousPassword(String password, User user)
    {
        List<String> history = getCryptHistory(user.getEmail());

        for (String hash : history)
        {
            if (SecurityManager.matchPassword(password, hash))
                return true;
        }

        return false;
    }


    public static Date getLastChanged(User user)
    {
        SqlSelector selector = new SqlSelector(core.getSchema(), new SQLFragment("SELECT LastChanged FROM " + core.getTableInfoLogins() + " WHERE Email=?", user.getEmail()));
        return selector.getObject(Date.class);
    }


    // Look up email in Logins table and return the corresponding password hash
    public static String getPasswordHash(ValidEmail email)
    {
        SqlSelector selector = new SqlSelector(core.getSchema(), new SQLFragment("SELECT Crypt FROM " + core.getTableInfoLogins() + " WHERE Email = ?", email.getEmailAddress()));
        return selector.getObject(String.class);
    }


    public static boolean matchPassword(String password, String hash)
    {
        if (hash.startsWith("salt:"))
            return Crypt.SaltMD5.matches(password, hash.substring(5));
        else if (hash.startsWith("md5:"))
            return Crypt.MD5.matches(password, hash.substring(4));
        else
            return Crypt.MD5.matches(password, hash);
    }


    public static boolean loginExists(ValidEmail email)
    {
        return (null != getPasswordHash(email));
    }


    public static Group createGroup(Container c, String name)
    {
        return createGroup(c, name, Group.typeProject);
    }


    public static Group createGroup(Container c, String name, String type)
    {
        // TODO: UserPrincipal.Type enum and add validation rules there
        if (!type.equals(Group.typeProject) && !type.equals(Group.typeModule))
            throw new IllegalStateException("Can't create a group with type \"" + type + "\"");

        String ownerId;

        if (null == c || c.isRoot())
        {
            ownerId = null;
        }
        else
        {
            ownerId = c.getId();

            // Module groups can be associated with any folder; security groups must be associated with a project
            if (!type.equals(Group.typeModule) && !c.isProject())
                throw new IllegalStateException("Security groups can only be associated with a project or the root");
        }

        return createGroup(c, name, type, ownerId);
    }


    public static Group createGroup(Container c, String name, String type, String ownerId)
    {
        String containerId = (null == c || c.isRoot()) ? null : c.getId();
        Group group = new Group();
        group.setName(StringUtils.trimToNull(name));
        group.setOwnerId(ownerId);
        group.setContainer(containerId);
        group.setType(type);

        if (null == group.getName())
            throw new IllegalArgumentException("Group can not have blank name");

        String valid = UserManager.validGroupName(group.getName(), group.getType());
        if (null != valid)
            throw new IllegalArgumentException(valid);

        if (groupExists(c, group.getName(), group.getOwnerId()))
            throw new IllegalArgumentException("Group '" + group.getName() + "' already exists");

        try
        {
            Table.insert(null, core.getTableInfoPrincipals(), group);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return group;
    }


    // Case-insensitive existence check -- disallows groups that differ only by case
    private static boolean groupExists(Container c, String groupName, String ownerId)
    {
        return null != getGroupId(c, groupName, ownerId, false, true);
    }


    public static void renameGroup(Group group, String newName, User currentUser)
    {
        if (group.isSystemGroup())
            throw new IllegalArgumentException("System groups may not be renamed!");
        Container c = null == group.getContainer() ? null : ContainerManager.getForId(group.getContainer());
        if (StringUtils.isEmpty(newName))
            throw new IllegalArgumentException("Name is required (may not be blank)");
        String valid = UserManager.validGroupName(newName, group.getType());
        if (null != valid)
            throw new IllegalArgumentException(valid);
        if (null != getGroupId(c, newName, false))
            throw new IllegalArgumentException("Cannot rename group '" + group.getName() + "' to '" + newName + "' because that name is already used by another group!");

        try
        {
            Table.update(currentUser, core.getTableInfoPrincipals(), Collections.singletonMap("name", newName), group.getUserId());
            GroupCache.uncache(group.getUserId());
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static void deleteGroup(Group group)
    {
        deleteGroup(group.getUserId());
    }


    static void deleteGroup(int groupId)
    {
        if (groupId == Group.groupAdministrators ||
                groupId == Group.groupGuests ||
                groupId == Group.groupUsers)
            throw new IllegalArgumentException("The global groups cannot be deleted.");

        try
        {
            GroupCache.uncache(groupId);

            Table.delete(core.getTableInfoRoleAssignments(), new SimpleFilter("UserId", groupId));

            Filter groupFilter = new SimpleFilter("GroupId", groupId);
            Table.delete(core.getTableInfoMembers(), groupFilter);

            Filter principalsFilter = new SimpleFilter("UserId", groupId);
            Table.delete(core.getTableInfoPrincipals(), principalsFilter);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static void deleteGroups(Container c, @Nullable String type)
    {
        if (!(null == type || type.equals(Group.typeProject) || type.equals(Group.typeModule) ))
            throw new IllegalArgumentException("Illegal group type: " + type);

        if (null == type)
            type = "%";

        try
        {
            // Consider: query for groups in this container and uncache just those.
            GroupCache.uncacheAll();

            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoRoleAssignments() + "\n"+
                    "WHERE UserId in (SELECT UserId FROM " + core.getTableInfoPrincipals() +
                    "\tWHERE Container=? and Type LIKE ?)", c, type);
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoMembers() + "\n"+
                    "WHERE GroupId in (SELECT UserId FROM " + core.getTableInfoPrincipals() +
                    "\tWHERE Container=? and Type LIKE ?)", c, type);
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoPrincipals() +
                    "\tWHERE Container=? AND Type LIKE ?", c, type);
        }
        catch (SQLException x)
        {
            _log.error("Delete group", x);
            throw new RuntimeSQLException(x);
        }
    }


    public static void deleteMembers(Group group, List<UserPrincipal> membersToDelete)
    {
        int groupId = group.getUserId();

        if (membersToDelete != null && !membersToDelete.isEmpty())
        {
            SQLFragment sql = new SQLFragment(
            "DELETE FROM " + core.getTableInfoMembers() + "\n" +
                    "WHERE GroupId = ? AND UserId IN(");
            sql.add(groupId);
            Iterator<UserPrincipal> it = membersToDelete.iterator();
            String comma = "";
            while (it.hasNext())
            {
                UserPrincipal member = it.next();
                sql.append(comma).append("?");
                comma = ",";
                sql.add(member.getUserId());
            }
            sql.append(")");

            new SqlExecutor(core.getSchema(), sql).execute();

            for (UserPrincipal member : membersToDelete)
                fireDeletePrincipalFromGroup(groupId, member);
        }
    }


    public static void deleteMember(Group group, UserPrincipal principal)
    {
        int groupId = group.getUserId();
        SqlExecutor executor = new SqlExecutor(core.getSchema(), new SQLFragment("DELETE FROM " + core.getTableInfoMembers() + "\n" +
            "WHERE GroupId = ? AND UserId = ?", groupId, principal.getUserId()));
        executor.execute();
        fireDeletePrincipalFromGroup(groupId, principal);
    }

    public static void addMembers(Group group, Collection<? extends UserPrincipal> principals)
    {
        for (UserPrincipal principal : principals)
        {
            try
            {
                addMember(group, principal);
            }
            catch (IllegalStateException e)
            {
                _log.error("Error adding principal to a group", e);
            }
        }
    }


    // Add a single user/group to a single group
    public static void addMember(Group group, UserPrincipal principal)
    {
        addMember(group, principal, null);
    }


    // Internal only; used by junit test
    static void addMember(Group group, UserPrincipal principal, @Nullable Runnable afterAddRunnable)
    {
        String errorMessage = getAddMemberError(group, principal);

        if (null != errorMessage)
            throw new IllegalStateException(errorMessage);

        addMemberWithoutValidation(group, principal);

        if (null != afterAddRunnable)
            afterAddRunnable.run();

        // If we added a group then check for circular relationship... we check this above, but it's possible that
        // another thread concurrently made a change that introduced a cycle.
        if (principal instanceof Group && hasCycle(group))
        {
            deleteMember(group, principal);
            throw new IllegalStateException(CIRCULAR_GROUP_ERROR_MESSAGE);
        }
    }


    // Internal only; used by junit test
    static void addMemberWithoutValidation(Group group, UserPrincipal principal)
    {
        SqlExecutor executor = new SqlExecutor(core.getSchema(), new SQLFragment("INSERT INTO " + core.getTableInfoMembers() +
            " (UserId, GroupId) VALUES (?, ?)", principal.getUserId(), group.getUserId()));

        executor.execute();
        fireAddPrincipalToGroup(group, principal);
    }


    // True if current group relationships cycle through root. Does NOT detect all cycles in the group graph nor even
    // in the subgraph represented by root, just the ones that that link back to root itself.
    private static boolean hasCycle(Group root)
    {
        HashSet<Integer> groupSet = new HashSet<Integer>();
        LinkedList<Integer> recurse = new LinkedList<Integer>();
        recurse.add(root.getUserId());

        while (!recurse.isEmpty())
        {
            int id = recurse.removeFirst();
            groupSet.add(id);
            int[] groups = GroupMembershipCache.getGroupsForPrincipal(id);

            for (int g : groups)
            {
                if (g == root.getUserId())
                    return true;

                if (!groupSet.contains(g))
                    recurse.addLast(g);
            }
        }

        return false;
    }


    // Return an error message if principal can't be added to the group, otherwise return null
    public static String getAddMemberError(Group group, UserPrincipal principal)
    {
        if (null == group)
            return NULL_GROUP_ERROR_MESSAGE;

        if (null == principal)
            return NULL_PRINCIPAL_ERROR_MESSAGE;

        if (group.isGuests() || group.isUsers())
            return "Can't add a member to the " + group.getName() + " group";

        Set<UserPrincipal> members = getGroupMembers(group, GroupMemberType.Both);

        if (members.contains(principal))
            return ALREADY_A_MEMBER_ERROR_MESSAGE;

        // End of checks for a user
        if (principal instanceof User)
            return null;

        // We're adding a group, so do some additional validation checks

        Group newMember = (Group)principal;

        if (group.equals(newMember))
            return ADD_GROUP_TO_ITSELF_ERROR_MESSAGE;

        if (group.isSystemGroup())
            return ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE;

        if (newMember.isSystemGroup())
            return ADD_SYSTEM_GROUP_ERROR_MESSAGE;

        if (group.isProjectGroup())
        {
            if (newMember.isProjectGroup() && !group.getContainer().equals(newMember.getContainer()))
                return DIFFERENT_PROJECTS_ERROR_MESSAGE;
        }
        else
        {
            if (newMember.isProjectGroup())
                return PROJECT_TO_SITE_ERROR_MESSAGE;
        }

        for (int id : group.getGroups())
            if (newMember.getUserId() == id)
                return CIRCULAR_GROUP_ERROR_MESSAGE;

        return null;
    }


    public static Group[] getGroups(@Nullable Container project, boolean includeGlobalGroups)
    {
        SQLFragment sql;

        if (null == project)
        {
            sql = new SQLFragment("SELECT Name, UserId, Container FROM " + core.getTableInfoPrincipals() + " WHERE Type='g' AND Container IS NULL ORDER BY LOWER(Name)");
        }
        else
        {
            String projectClause = (includeGlobalGroups ? "(Container = ? OR Container IS NULL)" : "Container = ?");

            // PostgreSQL and SQLServer disagree on how to sort null, so we need to handle
            // null Container values as the first ORDER BY criteria
            sql = new SQLFragment(
                    "SELECT Name, UserId, Container FROM " + core.getTableInfoPrincipals() + "\n" +
                            "WHERE Type='g' AND " + projectClause + "\n" +
                            "ORDER BY CASE WHEN ( Container IS NULL ) THEN 1 ELSE 2 END, Container, LOWER(Name)");  // Force case-insensitve order for consistency

            sql.add(project.getId());
        }

        return new SqlSelector(core.getSchema(), sql).getArray(Group.class);
    }


    public static Group getGroup(int groupId)
    {
        return GroupCache.get(groupId);
    }

    public static UserPrincipal getPrincipal(int id)
    {
        UserPrincipal principal = UserManager.getUser(id);
        return null != principal ? principal : getGroup(id);
    }

    public static UserPrincipal getPrincipal(String name, Container container)
    {
        Integer id = getGroupId(container, name, false);

        if (null != id)
        {
            return getGroup(id);
        }
        else
        {
            try
            {
                return UserManager.getUser(new ValidEmail(name));
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                return null;
            }
        }
    }

    public static List<User> getProjectUsers(Container c)
    {
        return getProjectUsers(c, false);
    }

    /**
     * Returns a list of Group objects to which the user belongs in the specified container.
     * @param c The container
     * @param u The user
     * @return The list of groups that u belong to in container c
     */
    public static List<Group> getGroups(Container c, User u)
    {
        Container proj = null != c.getProject() ? c.getProject() : c;
        int[] groupIds = u.getGroups();
        List<Group> groupList = new ArrayList<Group>();

        for (int groupId : groupIds)
        {
            //ignore user as group
            if (groupId != u.getUserId())
            {
                Group g = SecurityManager.getGroup(groupId);

                // Only global groups or groups in this project
                if (null == g.getContainer() || g.getContainer().equals(proj.getId()))
                    groupList.add(g);
            }
        }

        return groupList;
    }

    // Returns comma-separated list of group names this user belongs to in this container
    public static String getGroupList(Container c, User u)
    {
        Container proj = c.getProject();

        if (null == proj)
            return "";

        int[] groupIds = u.getGroups();

        StringBuilder groupList = new StringBuilder();
        String sep = "";

        for (int groupId : groupIds)
        {
            // Ignore Guest, Users, Admins, and user's own id
            if (groupId > 0 && groupId != u.getUserId())
            {
                Group g = SecurityManager.getGroup(groupId);

                if (null == g)
                    continue;

                // Only groups in this project
                if (g.isProjectGroup() && g.getContainer().equals(proj.getId()))
                {
                    groupList.append(sep);
                    groupList.append(g.getName());
                    sep = ", ";
                }
            }
        }

        return groupList.toString();
    }

    public static List<User> getGroupMembers(Group group) throws SQLException, ValidEmail.InvalidEmailException
    {
        String[] emails = getGroupMemberNames(group.getUserId());
        List<User> users = new ArrayList<User>(emails.length);
        for (String email : emails)
            users.add(UserManager.getUser(new ValidEmail(email)));
        return users;
    }

    public static enum GroupMemberType
    {
        Users,
        Groups,
        Both
    }

    @NotNull
    public static Set<UserPrincipal> getGroupMembers(Group group, GroupMemberType memberType)
    {
        Set<UserPrincipal> principals = new LinkedHashSet<UserPrincipal>();
        Integer[] ids = getGroupMemberIds(group);

        for (Integer id : ids)
        {
            UserPrincipal principal = getPrincipal(id);
            if (null != principal && (GroupMemberType.Both == memberType
                    || (GroupMemberType.Users == memberType && principal instanceof User)
                    || (GroupMemberType.Groups == memberType && principal instanceof Group)))
                principals.add(principal);
        }

        return principals;
    }

    // get the list of group members that do not need to be direct members because they are a member of a member group (i.e. groups-in-groups)
    public static Map<UserPrincipal, String> getRedundantGroupMembers(Group group)
    {
        Map<UserPrincipal, String> redundantMembers = new HashMap<UserPrincipal, String>();
        Set<UserPrincipal> allMembers = getGroupMembers(group, GroupMemberType.Both);
        Set<UserPrincipal> memberGroups = getGroupMembers(group, GroupMemberType.Groups);
        for (UserPrincipal g : memberGroups)
        {
            for (UserPrincipal member : getGroupMembers((Group)g, GroupMemberType.Both))
            {
                if (allMembers.contains(member))
                {
                    redundantMembers.put(member, "\"" + member.getName() + "\" can be removed because it already belongs to \"" + g.getName() + "\".");
                }
            }
        }
        return redundantMembers;
    }


    // TODO: Redundant with getProjectUsers() -- this approach should be more efficient for simple cases
    // TODO: Also redundant with getFolderUserids()
    // TODO: Cache this set
    public static Set<Integer> getProjectUsersIds(Container c)
    {
        SQLFragment sql = SecurityManager.getProjectUsersSQL(c.getProject());
        sql.insert(0, "SELECT DISTINCT members.UserId ");

        Selector selector = new SqlSelector(core.getSchema(), sql);
        return new HashSet<Integer>(selector.getCollection(Integer.class));
    }


    // True fragment -- need to prepend SELECT DISTINCT() or IN () for this to be valid SQL
    public static SQLFragment getProjectUsersSQL(Container c)
    {
        return new SQLFragment("FROM " + core.getTableInfoMembers() + " members INNER JOIN " + core.getTableInfoUsers() + " users ON members.UserId = users.UserId\n" +
                                    "INNER JOIN " + core.getTableInfoPrincipals() + " groups ON members.GroupId = groups.UserId\n" +
                                    "WHERE (groups.Container = ?)", c);
    }

    // TODO: Should return a set
    public static List<User> getProjectUsers(Container c, boolean includeGlobal)
    {
        if (c != null && !c.isProject())
            c = c.getProject();

        Group[] groups = getGroups(c, includeGlobal);
        Set<String> emails = new HashSet<String>();

       //get members for each group
        ArrayList<User> projectUsers = new ArrayList<User>();
        Set<UserPrincipal> members;

        for (Group g : groups)
        {
            if (g.isGuests() || g.isUsers())
                continue;

            // TODO: currently only getting members that are users (no groups). should this be changed to get users of member groups?
            members = getGroupMembers(g, GroupMemberType.Users);

            //add this group's members to hashset
            if (!members.isEmpty())
            {
                //get list of users from email
                for (UserPrincipal member : members)
                {
                    User user = UserManager.getUser(member.getUserId());
                    if (emails.add(user.getEmail()))
                        projectUsers.add(user);
                }
            }
        }

        return projectUsers;
    }


    public static Collection<Integer> getFolderUserids(Container c)
    {
        Container project = (c.isProject() || c.isRoot()) ? c : c.getProject();

        //users "in the project" consists of:
        // - users who are members of a project group
        // - users who belong to a site group that has a role assignment in the policy for the specified folder
        // - users who have a direct role assignment in the policy for the specified folder
        //And if the All Site Users group is playing a role, then all users are "in the project"

        SQLFragment sql = new SQLFragment("SELECT u.UserId FROM ");
        sql.append(core.getTableInfoPrincipals(), "u");
        sql.append(" WHERE u.type='u'");

        //don't filter if all site users is playing a role
        SecurityPolicy policy = c.getPolicy();
        Group allSiteUsers = SecurityManager.getGroup(Group.groupUsers);

        if (policy.getAssignedRoles(allSiteUsers).size() == 0)
        {
            String resId = c.getPolicy().getResourceId();

            sql.append(" AND (");

            //all users who are members of a project group
            sql.append("u.UserId IN (SELECT m.UserId FROM ");
            sql.append(core.getTableInfoMembers(), "m");
            sql.append(" INNER JOIN ");
            sql.append(core.getTableInfoPrincipals(), "g");
            sql.append(" ON m.GroupId = g.UserId WHERE g.Type='g' AND g.Container=?)");
            sql.add(project);

            //all users who belong to a site group that has a role assignment in the policy for the specified folder
            sql.append(" OR u.UserId IN (SELECT m.UserId FROM ");
            sql.append(core.getTableInfoMembers(), "m");
            sql.append(" INNER JOIN ");
            sql.append(core.getTableInfoPrincipals(), "g");
            sql.append(" ON m.GroupId = g.UserId WHERE g.Type='g' AND g.Container IS NULL AND g.UserId IN (SELECT a.UserId FROM ");
            sql.append(core.getTableInfoRoleAssignments(), "a");
            sql.append(" WHERE a.ResourceId=? AND a.role != 'org.labkey.api.security.roles.NoPermissionsRole'))");
            sql.add(resId);

            //users who have a direct role assignment in the policy for the specified folder
            sql.append(" OR u.UserId IN (SELECT a.UserId FROM ");
            sql.append(core.getTableInfoRoleAssignments(), "a");
            sql.append(" WHERE a.ResourceId=? AND a.role != 'org.labkey.api.security.roles.NoPermissionsRole')");
            sql.add(resId);

            sql.append(")"); //close of "AND ("
        }

        return new SqlSelector(core.getSchema(), sql).getCollection(Integer.class);
    }


    public static List<User> getUsersWithPermissions(Container c, Set<Class<? extends Permission>> perms) throws SQLException
    {
        // No cache right now, but performance seems fine.  After the user list and acl is cached, no other queries occur.
        User[] allUsers = UserManager.getActiveUsers();
        List<User> users = new ArrayList<User>(allUsers.length);
        SecurityPolicy policy = c.getPolicy();

        for (User user : allUsers)
            if (policy.hasPermissions(user, perms))
                users.add(user);

        return users;
    }


    public static List<Pair<Integer, String>> getGroupMemberNamesAndIds(String path)
    {
        Integer groupId = SecurityManager.getGroupId(path);
        if (groupId == null)
            return Collections.emptyList();
        else
            return getGroupMemberNamesAndIds(groupId);
    }
    

    public static List<Pair<Integer, String>> getGroupMemberNamesAndIds(Integer groupId)
    {
        ResultSet rs = null;

        try
        {
            final List<Pair<Integer, String>> members = new ArrayList<Pair<Integer, String>>();

            if (groupId != null && groupId == Group.groupUsers)
            {
                // Special-case site users group, which isn't maintained in the database
                for (User user : UserManager.getActiveUsers())
                {
                    members.add(new Pair<Integer, String>(user.getUserId(), user.getEmail()));
                }
            }
            else
            {
                Selector selector = new SqlSelector(
                        core.getSchema(),
                        new SQLFragment("SELECT Users.UserId, Users.Name\n" +
                                "FROM " + core.getTableInfoMembers() + " JOIN " + core.getTableInfoPrincipals() + " Users ON " + core.getTableInfoMembers() + ".UserId = Users.UserId\n" +
                                "WHERE GroupId = ? AND Active=?\n" +
                                "ORDER BY Users.Name",
                        groupId, true));

                selector.forEach(new Selector.ForEachBlock<ResultSet>() {
                    @Override
                    public void exec(ResultSet rs) throws SQLException
                    {
                        members.add(new Pair<Integer, String>(rs.getInt(1), rs.getString(2)));
                    }
                });
            }

            return members;
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    public static String[] getGroupMemberNames(Integer groupId) throws SQLException
    {
        List<Pair<Integer, String>> members = getGroupMemberNamesAndIds(groupId);
        String[] names = new String[members.size()];
        int i = 0;
        for (Pair<Integer, String> member : members)
            names[i++] = member.getValue();
        return names;
    }


    private static Integer[] getGroupMemberIds(Group group)
    {
        Selector selector = new SqlSelector(core.getSchema(), new SQLFragment(
            "SELECT Members.UserId FROM " + core.getTableInfoMembers() + " Members" +
            " JOIN " + core.getTableInfoPrincipals() + " Users ON Members.UserId = Users.UserId\n" +
            " WHERE Members.GroupId = ?" +
            " ORDER BY Users.Type, Users.Name", group.getUserId()));

        return selector.getArray(Integer.class);
    }


    // Takes string such as "/test/subfolder/Users" and returns groupId
    public static Integer getGroupId(String extraPath)
    {
        if (null == extraPath)
            return null;
        
        if (extraPath.startsWith("/"))
            extraPath = extraPath.substring(1);

        int slash = extraPath.lastIndexOf('/');
        String group = extraPath.substring(slash + 1);
        Container c = null;
        if (slash != -1)
        {
            String path = extraPath.substring(0, slash);
            c = ContainerManager.getForPath(path);
            if (null == c)
            {
                throw new NotFoundException();
            }
        }

        return getGroupId(c, group);
    }


    // Takes Container (or null for root) and group name; returns groupId
    public static Integer getGroupId(@Nullable Container c, String group)
    {
        return getGroupId(c, group, null, true);
    }


    // Takes Container (or null for root) and group name; returns groupId
    public static Integer getGroupId(@Nullable Container c, String group, boolean throwOnFailure)
    {
        return getGroupId(c, group, null, throwOnFailure);
    }


    public static Integer getGroupId(@Nullable Container c, String groupName, @Nullable String ownerId, boolean throwOnFailure)
    {
        return getGroupId(c, groupName, ownerId, throwOnFailure, false);
    }


    // This is temporary... in CPAS 1.5 on PostgreSQL it was possible to create two groups in the same container that differed only
    // by case (this was not possible on SQL Server).  In CPAS 1.6 we disallow this on PostgreSQL... but we still need to be able to
    // retrieve group IDs in a case-sensitive manner.
    // TODO: For CPAS 1.7: this should always be case-insensitive (we will clean up the database by renaming duplicate groups)
    private static Integer getGroupId(@Nullable Container c, String groupName, @Nullable String ownerId, boolean throwOnFailure, boolean caseInsensitive)
    {
        SQLFragment sql = new SQLFragment("SELECT UserId FROM " + core.getTableInfoPrincipals() + " WHERE Type!='u' AND " + (caseInsensitive ? "LOWER(Name)" : "Name") + " = ? AND Container ");
        sql.add(caseInsensitive ? groupName.toLowerCase() : groupName);

        if (c == null || c.isRoot())
        {
            sql.append("IS NULL");
        }
        else
        {
            sql.append("= ?");
            sql.add(c.getId());
            if (ownerId == null)
                ownerId = c.isRoot() ? null : c.getId();
        }

        if (ownerId == null)
        {
            sql.append(" AND OwnerId IS NULL");
        }
        else
        {
            sql.append(" AND OwnerId = ?");
            sql.add(ownerId);
        }

        Integer groupId = new SqlSelector(core.getSchema(), sql).getObject(Integer.class);

        if (groupId == null && throwOnFailure)
        {
            throw new NotFoundException("Group not found: " + groupName);
        }

        return groupId;
    }


    public static boolean isTermsOfUseRequired(Project project)
    {
        //TODO: Should do this more efficiently, but no efficient public wiki api for this yet
        return null != getTermsOfUseHtml(project);
    }


    public static String getTermsOfUseHtml(Project project)
    {
        if (null == project)
            return null;

        if (!ModuleLoader.getInstance().isStartupComplete())
            return null;

        WikiService service = ServiceRegistry.get().getService(WikiService.class);
        //No wiki service. Must be in weird state. Don't do terms here...
        if (null == service)
            return null;

        return service.getHtml(project.getContainer(), TERMS_OF_USE_WIKI_NAME);
    }


    public static boolean isTermsOfUseRequired(ViewContext ctx)
    {
        if (User.getSearchUser() == ctx.getUser())  // TODO: Should be property of user
            return false;

        Container c = ctx.getContainer();
        if (null == c)
            return false;

        Container proj = c.getProject();
        if (null == proj)
            return false;

        Project project = new Project(proj);

        if ("Basic".equals(ctx.getRequest().getAttribute(AUTHENTICATION_METHOD)) || isTermsOfUseApproved(ctx, project))
            return false;

        boolean required = isTermsOfUseRequired(project);

        //stash result so that this is faster next time.
        if (!required)
            setTermsOfUseApproved(ctx, project, true);

        return required;
    }


    private static final String TERMS_APPROVED_KEY = "TERMS_APPROVED_KEY";
    private static final Object TERMS_APPROVED_LOCK = new Object();

    public static boolean isTermsOfUseApproved(ViewContext ctx, Project project)
    {
        if (null == project)
            return true;

        synchronized (TERMS_APPROVED_LOCK)
        {
            HttpSession session = ctx.getRequest().getSession(true);
            Set<Project> termsApproved = (Set<Project>) session.getAttribute(TERMS_APPROVED_KEY);
            return null != termsApproved && termsApproved.contains(project);
        }
    }


    public static void setTermsOfUseApproved(ViewContext ctx, Project project, boolean approved)
    {
        if (null == project)
            return;

        synchronized (TERMS_APPROVED_LOCK)
        {
            HttpSession session = ctx.getRequest().getSession(true);
            Set<Project> termsApproved = (Set<Project>) session.getAttribute(TERMS_APPROVED_KEY);
            if (null == termsApproved)
            {
                termsApproved = new HashSet<Project>();
                session.setAttribute(TERMS_APPROVED_KEY, termsApproved);
            }
            if (approved)
                termsApproved.add(project);
            else
                termsApproved.remove(project);
        }
    }


    // CONSIDER: Support multiple LDAP domains?
    public static boolean isLdapEmail(ValidEmail email)
    {
        String ldapDomain = AuthenticationManager.getLdapDomain();
        return ldapDomain != null && email.getEmailAddress().endsWith("@" + ldapDomain.toLowerCase());
    }


    //
    // Permissions, ACL cache and Permission testing
    //

    //manage SecurityPolicy
    private static final String _policyPrefix = "Policy/";

    @NotNull
    public static SecurityPolicy getPolicy(@NotNull SecurableResource resource)
    {
        return getPolicy(resource, true);
    }

    @NotNull
    public static SecurityPolicy getPolicy(@NotNull SecurableResource resource, boolean findNearest)
    {
        String cacheName = cacheName(resource);
        SecurityPolicy policy = (SecurityPolicy) DbCache.get(core.getTableInfoRoleAssignments(), cacheName);

        if (null == policy)
        {
            SimpleFilter filter = new SimpleFilter("ResourceId", resource.getResourceId());

            SecurityPolicyBean policyBean = Table.selectObject(core.getTableInfoPolicies(), resource.getResourceId(),
                    SecurityPolicyBean.class);

            TableInfo table = core.getTableInfoRoleAssignments();

            Selector selector = new TableSelector(table, filter, new Sort("UserId"));
            RoleAssignment[] assignments = selector.getArray(RoleAssignment.class);

            policy = new SecurityPolicy(resource, assignments, null != policyBean ? policyBean.getModified() : new Date());
            DbCache.put(table, cacheName, policy);
        }

        if (findNearest && policy.isEmpty() && resource.mayInheritPolicy())
        {
            SecurableResource parent = resource.getParentResource();
            if (null != parent)
                return getPolicy(parent, findNearest);
        }
        
        return policy;
    }


    public static void savePolicy(@NotNull MutableSecurityPolicy policy)
    {
        DbScope scope = core.getSchema().getScope();

        try
        {
            scope.ensureTransaction();

            //if the policy to save has a version, check to see if it's the current one
            //(note that this may be a new policy so there might not be an existing one)
            SecurityPolicyBean currentPolicyBean = Table.selectObject(core.getTableInfoPolicies(),
                    policy.getResourceId(), SecurityPolicyBean.class);

            if (null != currentPolicyBean && null != policy.getModified() &&
                    0 != policy.getModified().compareTo(currentPolicyBean.getModified()))
            {
                throw new Table.OptimisticConflictException("The security policy you are attempting to save" +
                " has been altered by someone else since you selected it.", Table.SQLSTATE_TRANSACTION_STATE, 0);
            }

            //normalize the policy to get rid of extraneous no perms role assignments
            policy.normalize();

            //save to policies table
            if (null == currentPolicyBean)
                Table.insert(null, core.getTableInfoPolicies(), policy.getBean());
            else
                Table.update(null, core.getTableInfoPolicies(), policy.getBean(), policy.getResourceId());

            TableInfo table = core.getTableInfoRoleAssignments();

            //delete all rows where resourceid = resource.getId()
            Table.delete(table, new SimpleFilter("ResourceId", policy.getResourceId()));

            //insert rows for the policy entries
            for (RoleAssignment assignment : policy.getAssignments())
            {
                Table.insert(null, table, assignment);
            }

            //commit transaction
            scope.commitTransaction();

            //remove the resource-oriented policy from cache
            DbCache.remove(table, cacheNameForResourceId(policy.getResourceId()));
            notifyPolicyChange(policy.getResourceId());
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public static void deletePolicy(@NotNull SecurableResource resource)
    {
        DbScope scope = core.getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            TableInfo table = core.getTableInfoRoleAssignments();

            //delete all rows where resourceid = resource.getResourceId()
            SimpleFilter filter = new SimpleFilter("ResourceId", resource.getResourceId());
            Table.delete(table, filter);
            Table.delete(core.getTableInfoPolicies(), filter);

            //commit transaction
            scope.commitTransaction();

            //remove the resource-oriented policy from cache
            DbCache.remove(table, cacheName(resource));

            notifyPolicyChange(resource.getResourceId());
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    /**
     * Clears all role assignments for the specified principals for the specified resources.
     * After this call completes, all the specified principals will no longer have any role
     * assignments for the specified resources.
     * @param resources The resources
     * @param principals The principals
     */
    public static void clearRoleAssignments(@NotNull Set<SecurableResource> resources, @NotNull Set<UserPrincipal> principals)
    {
        if (resources.size() == 0 || principals.size() == 0)
            return;

        TableInfo table = core.getTableInfoRoleAssignments();

        SQLFragment sql = new SQLFragment("DELETE FROM ");
        sql.append(core.getTableInfoRoleAssignments());
        sql.append(" WHERE ResourceId IN (");

        String sep = "";
        SQLFragment resourcesList = new SQLFragment();

        for (SecurableResource resource : resources)
        {
            resourcesList.append(sep);
            resourcesList.append("?");
            resourcesList.add(resource.getResourceId());
            sep = ",";
        }

        sql.append(resourcesList);
        sql.append(") AND UserId IN (");
        sep = "";
        SQLFragment principalsList = new SQLFragment();

        for (UserPrincipal principal : principals)
        {
            principalsList.append(sep);
            principalsList.append("?");
            principalsList.add(principal.getUserId());
            sep = ",";
        }

        sql.append(principalsList);
        sql.append(")");

        new SqlExecutor(core.getSchema(), sql).execute();

        DbCache.clear(table);

        for (SecurableResource resource : resources)
        {
            notifyPolicyChange(resource.getResourceId());
        }
    }

    // Modules register a factory to add module-specific ui to the permissions page
    public static void addViewFactory(ViewFactory vf)
    {
        _viewFactories.add(vf);
    }


    public static List<ViewFactory> getViewFactories()
    {
        return _viewFactories;
    }


    public interface ViewFactory
    {
        public HttpView createView(ViewContext context);
    }


    private static String cacheName(SecurableResource resource)
    {
        return cacheNameForResourceId(resource.getResourceId());
    }

    private static String cacheNameForResourceId(String resourceId)
    {
        return _policyPrefix + "resource/" + resourceId;
    }

    private static String cacheNameForUserId(int userId)
    {
        return _policyPrefix + "principal/" + userId;
    }


    public static void removeAll(Container c)
    {
        try
        {
            Table.execute(
                    core.getSchema(),
                    "DELETE FROM " + core.getTableInfoRoleAssignments() + " WHERE ResourceId IN(SELECT ResourceId FROM " +
                    core.getTableInfoPolicies() + " WHERE Container=?)",
                    c.getId());
            Table.execute(
                    core.getSchema(),
                    "DELETE FROM " + core.getTableInfoPolicies() + " WHERE Container=?",
                    c.getId());
            DbCache.clear(core.getTableInfoRoleAssignments());
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testCreateUser() throws Exception
        {
            ValidEmail email;
            String rawEmail;

            // Just in case, loop until we find one that doesn't exist already
            while (true)
            {
                rawEmail = "test_" + Math.round(Math.random() * 10000) + "@localhost.xyz";
                email = new ValidEmail(rawEmail);
                if (!SecurityManager.loginExists(email)) break;
            }

            User user = null;

            // Test create user, verify, login, and delete
            try
            {
                NewUserStatus status = addUser(email);
                user = status.getUser();
                assertTrue("addUser", user.getUserId() != 0);

                boolean success = SecurityManager.verify(email, status.getVerification());
                assertTrue("verify", success);

                SecurityManager.setVerification(email, null);

                String password = createTempPassword();
                SecurityManager.setPassword(email, password);

                User user2 = AuthenticationManager.authenticate(rawEmail, password);
                assertNotNull("login", user2);
                assertEquals("login", user, user2);
            }
            finally
            {
                UserManager.deleteUser(user.getUserId());
            }
        }


        @Test
        public void testACLS() throws NamingException
        {
            ACL acl = new ACL();

            // User,Guest
            User user = TestContext.get().getUser();
            assertFalse("no permission check", acl.hasPermission(user, ACL.PERM_READ));

            acl.setPermission(user.getUserId(), ACL.PERM_READ);
            assertTrue("read permission", acl.hasPermission(user, ACL.PERM_READ));
            assertFalse("no write permission", acl.hasPermission(user, ACL.PERM_UPDATE));

            acl = new ACL();
            acl.setPermission(Group.groupGuests, ACL.PERM_READ);
            assertTrue("read permission", acl.hasPermission(user, ACL.PERM_READ));
            assertFalse("no write permission", acl.hasPermission(user, ACL.PERM_UPDATE));

            acl.setPermission(Group.groupUsers, ACL.PERM_UPDATE);
            assertTrue("write permission", acl.hasPermission(user, ACL.PERM_UPDATE));
            assertEquals(acl.getPermissions(user), ACL.PERM_READ | ACL.PERM_READOWN | ACL.PERM_UPDATE | ACL.PERM_UPDATEOWN );

            // Guest
            assertTrue("read permission", acl.hasPermission(User.guest, ACL.PERM_READ));
            assertFalse("no write permission", acl.hasPermission(User.guest, ACL.PERM_UPDATE));
            assertEquals(acl.getPermissions(User.guest), ACL.PERM_READ | ACL.PERM_READOWN);
        }


//        @Test
//        public void testEmailValidation()
//        {
//            testEmail("this@that.com", true);
//            testEmail("foo@fhcrc.org", true);
//            testEmail("dots.dots@dots.co.uk", true);
//            testEmail("funny_chars#that%are^allowed&in*email!addresses@that.com", true);
//
//            String displayName = "Personal Name";
//            ValidEmail email = testEmail(displayName + " <personal@name.com>", true);
//            assertTrue("Display name: expected '" + displayName + "' but was '" + email.getPersonal() + "'", displayName.equals(email.getPersonal()));
//
//            String defaultDomain = ValidEmail.getDefaultDomain();
//            // If default domain is defined this should succeed; if it's not defined, this should fail.
//            testEmail("foo", defaultDomain != null && defaultDomain.length() > 0);
//
//            testEmail("~()@bar.com", false);
//            testEmail("this@that.com@con", false);
//            testEmail(null, false);
//            testEmail("", false);
//            testEmail("<@bar.com", false);
//            testEmail(displayName + " <personal>", false);  // Can't combine personal name with default domain
//        }


        private ValidEmail testEmail(String rawEmail, boolean valid)
        {
            ValidEmail email = null;

            try
            {
                email = new ValidEmail(rawEmail);
                assertTrue(rawEmail, valid);
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                assertFalse(rawEmail, valid);
            }

            return email;
        }
    }


    protected static void notifyPolicyChange(String objectID)
    {
        // UNDONE: generalize cross manager/module notifications
        ContainerManager.notifyContainerChange(objectID);
    }

    public static List<ValidEmail> normalizeEmails(String[] rawEmails, List<String> invalidEmails)
    {
        if (rawEmails == null || rawEmails.length == 0)
            return Collections.emptyList();
        return normalizeEmails(Arrays.asList(rawEmails), invalidEmails);
    }

    public static List<ValidEmail> normalizeEmails(List<String> rawEmails, List<String> invalidEmails)
    {
        if (rawEmails == null || rawEmails.size() == 0)
            return Collections.emptyList();

        List<ValidEmail> emails = new ArrayList<ValidEmail>(rawEmails.size());

        for (String rawEmail : rawEmails)
        {
            try
            {
                emails.add(new ValidEmail(rawEmail));
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                invalidEmails.add(rawEmail);
            }
        }

        return emails;
    }

    public static SecurityMessage getRegistrationMessage(String mailPrefix, boolean isAdminCopy) throws Exception
    {
        SecurityMessage sm = new SecurityMessage();

        EmailTemplate et = EmailTemplateService.get().getEmailTemplate(
                isAdminCopy ? RegistrationAdminEmailTemplate.class
                            : RegistrationEmailTemplate.class);
        sm.setMessagePrefix(mailPrefix);
        sm.setEmailTemplate((SecurityEmailTemplate)et);
        sm.setType("User Registration Email");

        return sm;
    }

    public static SecurityMessage getResetMessage(boolean isAdminCopy) throws Exception
    {
        SecurityMessage sm = new SecurityMessage();

        EmailTemplate et = EmailTemplateService.get().getEmailTemplate(
                isAdminCopy ? PasswordResetAdminEmailTemplate.class
                            : PasswordResetEmailTemplate.class);
        sm.setEmailTemplate((SecurityEmailTemplate)et);
        sm.setType("Reset Password Email");
        return sm;
    }

    /**
     * @return null if the user already exists, or a message indicating success/failure
     */
    public static String addUser(ViewContext context, ValidEmail email, boolean sendMail, String mailPrefix, Pair<String, String>[] extraParameters) throws Exception
    {
        if (UserManager.userExists(email))
        {
            return null;
        }

        StringBuilder message = new StringBuilder();
        NewUserStatus newUserStatus;

        ActionURL messageContentsURL = null;
        boolean appendClickToSeeMail = false;
        User currentUser = context.getUser();

        try
        {
            newUserStatus = SecurityManager.addUser(email);

            if (newUserStatus.getHasLogin() && sendMail)
            {
                Container c = context.getContainer();
                messageContentsURL = PageFlowUtil.urlProvider(SecurityUrls.class).getShowRegistrationEmailURL(c, email, mailPrefix);

                ActionURL verificationURL = createVerificationURL(context.getContainer(), email, newUserStatus.getVerification(), extraParameters);

                SecurityManager.sendEmail(c, currentUser, getRegistrationMessage(mailPrefix, false), email.getEmailAddress(), verificationURL);
                if (!currentUser.getEmail().equals(email.getEmailAddress()))
                {
                    SecurityMessage msg = getRegistrationMessage(mailPrefix, true);
                    msg.setTo(email.getEmailAddress());
                    SecurityManager.sendEmail(c, currentUser, msg, currentUser.getEmail(), verificationURL);
                }
                appendClickToSeeMail = currentUser.isAdministrator();
            }

            User newUser = newUserStatus.getUser();

            if (newUserStatus.isLdapEmail())
            {
                message.append(newUser.getEmail()).append(" added as a new user to the system.  This user will be authenticated via LDAP.");
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system.  This user will be authenticated via LDAP.");
            }
            else if (sendMail)
            {
                message.append(email.getEmailAddress()).append(" added as a new user to the system and emailed successfully.");
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system.  Verification email was sent successfully.");
            }
            else
            {
                message.append(email.getEmailAddress()).append(" added as a new user to the system, but no email was sent.");

                if (appendClickToSeeMail)
                {
                    message.append("  Click ");
                    String href = "<a href=\"" + PageFlowUtil.filter(createVerificationURL(context.getContainer(),
                            email, newUserStatus.getVerification(), extraParameters)) + "\" target=\"" + email.getEmailAddress() + "\">here</a>";
                    message.append(href).append(" to change the password from the random one that was assigned.");
                }

                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system and the administrator chose not to send a verification email.");
            }
        }
        catch (MessagingException e)
        {
            message.append("<br>");
            message.append(email.getEmailAddress());
            message.append(" was added successfully, but could not be emailed due to a failure:<br><pre>");
            message.append(e.getMessage());
            message.append("</pre>");
            appendMailHelpText(message, messageContentsURL, currentUser.isAdministrator());

            User newUser = UserManager.getUser(email);

            if (null != newUser)
                UserManager.addToUserHistory(newUser, newUser.getEmail() + " was added to the system.  Sending the verification email failed.");
        }
        catch (SecurityManager.UserManagementException e)
        {
            message.append("Failed to create user ").append(email).append(": ").append(e.getMessage());
        }

        if (appendClickToSeeMail && messageContentsURL != null)
        {
            String href = "<a href=" + PageFlowUtil.filter(messageContentsURL) + " target=\"_blank\">here</a>";
            message.append(" Click ").append(href).append(" to see the email.");
        }

        return message.toString();
    }

    private static void appendMailHelpText(StringBuilder sb, ActionURL messageContentsURL, boolean isAdmin)
    {
        if (isAdmin)
        {
            sb.append("You can attempt to resend this mail later by going to the Site Users link, clicking on the appropriate user from the list, and resetting their password.");

            if (messageContentsURL != null)
            {
                sb.append(" Alternatively, you can copy the <a href=\"");
                sb.append(PageFlowUtil.filter(messageContentsURL));
                sb.append("\" target=\"_blank\">contents of the message</a> into an email client and send it to the user manually.");
            }

            sb.append("</p>");
            sb.append("<p>For help on fixing your mail server settings, please consult the SMTP section of the <a href=\"");
            sb.append((new HelpTopic("cpasxml")).getHelpTopicLink());
            sb.append("\" target=\"_new\">LabKey documentation on modifying your configuration file</a>.<br>");
        }
        else
        {
            sb.append("Please contact your site administrator.");
        }
    }


    public static void createNewProjectGroups(Container project)
    {
        /*
        this check doesn't work well when moving container
        if (!project.isProject())
            throw new IllegalArgumentException("Must be a top level container");
        */

        // Create default groups
        //Note: we are no longer creating the project-level Administrators group
        Group userGroup = SecurityManager.createGroup(project, "Users");

        // Set default permissions
        // CONSIDER: get/set permissions on Container, rather than going behind its back
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        MutableSecurityPolicy policy = new MutableSecurityPolicy(project);
        policy.addRoleAssignment(userGroup, noPermsRole);

        //users and guests have no perms by default
        policy.addRoleAssignment(getGroup(Group.groupUsers), noPermsRole);
        policy.addRoleAssignment(getGroup(Group.groupGuests), noPermsRole);
        
        savePolicy(policy);
    }

    public static void setAdminOnlyPermissions(Container c)
    {
        MutableSecurityPolicy policy = new MutableSecurityPolicy(c);

        if (!c.isProject())
        {
            //assign all principals who are project admins at the project level to the folder admin role in the container
            SecurityPolicy projectPolicy = c.getProject().getPolicy();
            Role projAdminRole = RoleManager.getRole(ProjectAdminRole.class);
            Role folderAdminRole = RoleManager.getRole(FolderAdminRole.class);
            for (RoleAssignment ra : projectPolicy.getAssignments())
            {
                if (ra.getRole().equals(projAdminRole))
                {
                    UserPrincipal principal = getPrincipal(ra.getUserId());
                    if (null != principal)
                        policy.addRoleAssignment(principal, folderAdminRole);
                }
            }
        }

        //if policy is still empty, add the guests group to the no perms role so that
        //we don't end up with an empty (i.e., inheriting policy)
        if (policy.isEmpty())
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), NoPermissionsRole.class);

        savePolicy(policy);
    }

    public static boolean isAdminOnlyPermissions(Container c)
    {
        Set<Role> adminRoles = new HashSet<Role>();
        adminRoles.add(RoleManager.getRole(SiteAdminRole.class));
        adminRoles.add(RoleManager.getRole(ProjectAdminRole.class));
        adminRoles.add(RoleManager.getRole(FolderAdminRole.class));
        SecurityPolicy policy = c.getPolicy();

        for (RoleAssignment ra : policy.getAssignments())
        {
            if (!adminRoles.contains(ra.getRole()))
                return false;
        }

        return true;
    }

    public static void setInheritPermissions(Container c)
    {
        deletePolicy(c);
    }

    private static final String SUBFOLDERS_INHERIT_PERMISSIONS_NAME = "SubfoldersInheritPermissions";
    
    public static boolean shouldNewSubfoldersInheritPermissions(Container project)
    {
        Map<String, String> props = PropertyManager.getProperties(project.getId(), SUBFOLDERS_INHERIT_PERMISSIONS_NAME);
        return "true".equals(props.get(SUBFOLDERS_INHERIT_PERMISSIONS_NAME));
    }

    public static void setNewSubfoldersInheritPermissions(Container project, User user, boolean inherit)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(project.getId(), SUBFOLDERS_INHERIT_PERMISSIONS_NAME, true);
        props.put(SUBFOLDERS_INHERIT_PERMISSIONS_NAME, Boolean.toString(inherit));
        PropertyManager.saveProperties(props);
        addAuditEvent(project, user, String.format("Container %s was updated so that new subfolders would " + (inherit ? "" : "not ") + "inherit security permissions", project.getName()), 0);
    }

    public static void addAuditEvent(Container c, User user, String comment, int groupId)
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setComment(comment);

            event.setContainerId(c.getId());
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            event.setIntKey2(groupId);
            event.setEventType(GroupManager.GROUP_AUDIT_EVENT);
            AuditLogService.get().addEvent(event);
        }
    }


    public static void changeProject(Container c, Container oldProject, Container newProject)
            throws SQLException
    {
        assert core.getSchema().getScope().isTransactionActive();

        if (oldProject.getId().equals(newProject.getId()))
            return;

        /* when demoting a project to a regular folder, delete the project groups */
        if (oldProject == c)
        {
            SecurityManager.deleteGroups(c,Group.typeProject);
        }

        /*
         * Clear all ACLS for folders that changed project!
         */
        Container[] subtrees = ContainerManager.getAllChildren(c);
        StringBuilder sb = new StringBuilder();
        String comma = "";

        for (Container sub : subtrees)
        {
            sb.append(comma);
            sb.append("'");
            sb.append(sub.getId());
            sb.append("'");
            comma = ",";
        }

        Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoRoleAssignments() + "\n" +
            "WHERE ResourceId IN (SELECT ResourceId FROM " + core.getTableInfoPolicies() + " WHERE Container IN (" +
                sb.toString() + "))");
        Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoPolicies() + "\n" +
            "WHERE Container IN (" + sb.toString() + ")");
        DbCache.clear(core.getTableInfoRoleAssignments());

        /* when promoting a folder to a project, create default project groups */
        if (newProject == c)
        {
            createNewProjectGroups(c);
        }
    }

    public abstract static class SecurityEmailTemplate extends EmailTemplate
    {
        protected String _optionalPrefix;
        private String _verificationUrl = "";
        private String _emailAddress = "";
        private String _recipient = "";
        protected boolean _verificationUrlRequired = true;
        protected final List<ReplacementParam> _replacements = new ArrayList<ReplacementParam>();

        protected SecurityEmailTemplate(String name)
        {
            super(name);

            _replacements.add(new ReplacementParam("verificationURL", "Link for a user to set a password"){
                public String getValue(Container c) {return _verificationUrl;}
            });
            _replacements.add(new ReplacementParam("emailAddress", "The email address of the user performing the operation"){
                public String getValue(Container c) {return _emailAddress;}
            });
            _replacements.add(new ReplacementParam("recipient", "The email address on the 'to:' line"){
                public String getValue(Container c) {return _recipient;}
            });
            _replacements.addAll(super.getValidReplacements());
        }

        public void setOptionPrefix(String optionalPrefix){_optionalPrefix = optionalPrefix;}
        public void setVerificationUrl(String verificationUrl){_verificationUrl = verificationUrl;}
        public void setEmailAddress(String emailAddress){_emailAddress = emailAddress;}
        public void setRecipient(String recipient){_recipient = recipient;}
        public List<ReplacementParam> getValidReplacements(){return _replacements;}

        public boolean isValid(String[] error)
        {
            if (super.isValid(error))
            {
                // add an additional requirement for the verification url
                if (!_verificationUrlRequired || getBody().contains("^verificationURL^"))
                {
                    return true;
                }
                error[0] = "The substitution param: ^verificationURL^ is required to be somewhere in the body of the message";
            }
            return false;
        }
    }

    public static class RegistrationEmailTemplate extends SecurityEmailTemplate
    {
        protected static final String DEFAULT_SUBJECT =
                "Welcome to the ^organizationName^ ^siteShortName^ Web Site new user registration";
        protected static final String DEFAULT_BODY =
                "^optionalMessage^\n\n" +
                "You now have an account on the ^organizationName^ ^siteShortName^ web site.  We are sending " +
                "you this message to verify your email address and to allow you to create a password that will provide secure " +
                "access to your data on the web site.  To complete the registration process, simply click the link below or " +
                "copy it to your browser's address bar.  You will then be asked to choose a password.\n\n" +
                "^verificationURL^\n\n" +
                "Note: The link above should appear on one line, starting with 'http' and ending with your email address.  Some " +
                "email systems may break this link into multiple lines, causing the verification to fail.  If this happens, " +
                "you'll need to paste the parts into the address bar of your browser to form the full link.\n\n" +
                "The ^siteShortName^ home page is ^homePageURL^.  When you visit the home page " +
                "and log in with your new password you will see a list of projects on the left side of the page.  Click those " +
                "links to visit your projects.\n\n" +
                "If you have any questions don't hesitate to contact the ^siteShortName^ team at ^systemEmail^.";

        public RegistrationEmailTemplate()
        {
            super("Register new user");
            setSubject(DEFAULT_SUBJECT);
            setBody(DEFAULT_BODY);
            setDescription("Sent to the new user and administrator when a user is added to the site.");
            setPriority(1);

            _replacements.add(new ReplacementParam("optionalMessage", "An optional message to include with the new user email"){
                public String getValue(Container c) {return _optionalPrefix;}
            });
        }
    }

    public static class RegistrationAdminEmailTemplate extends RegistrationEmailTemplate
    {
        public RegistrationAdminEmailTemplate()
        {
            super();
            setName("Register new user (bcc to admin)");
            setSubject("^recipient^ : " + DEFAULT_SUBJECT);
            setBody("The following message was sent to ^recipient^ :\n\n" + DEFAULT_BODY);
            setPriority(2);
            _verificationUrlRequired = false;
        }
    }

    public static class PasswordResetEmailTemplate extends SecurityEmailTemplate
    {
        protected static final String DEFAULT_SUBJECT =
                "Reset Password Notification from the ^siteShortName^ Web Site";
        protected static final String DEFAULT_BODY =
                "We have reset your password on the ^organizationName^ ^siteShortName^ web site. " +
                "To sign in to the system you will need " +
                "to specify a new password.  Click the link below or copy it to your browser's address bar.  You will then be " +
                "asked to enter a new password.\n\n" +
                "^verificationURL^\n\n" +
                "The ^siteShortName^ home page is ^homePageURL^.  When you visit the home page and log " +
                "in with your new password you will see a list of projects on the left side of the page.  Click those links to " +
                "visit your projects.";

        public PasswordResetEmailTemplate()
        {
            super("Reset password");
            setSubject(DEFAULT_SUBJECT);
            setBody(DEFAULT_BODY);
            setDescription("Sent to the user and administrator when the password of a user is reset.");
            setPriority(3);
        }
    }

    public static class PasswordResetAdminEmailTemplate extends PasswordResetEmailTemplate
    {
        public PasswordResetAdminEmailTemplate()
        {
            super();
            setName("Reset password (bcc to admin)");
            setSubject("^recipient^ : " + DEFAULT_SUBJECT);
            setBody("The following message was sent to ^recipient^ :\n\n" + DEFAULT_BODY);
            setPriority(4);
            _verificationUrlRequired = false;
        }
    }

    /**
     * Returns a group name that disambiguates between site/project Administrators
     * and site/project Users
     * @param group the group
     * @return The disambiguated name of the group
     */
    public static String getDisambiguatedGroupName(Group group)
    {
        int id = group.getUserId();
        switch(id)
        {
            case Group.groupAdministrators:
                return "Site Administrators";
            case Group.groupUsers:
                return "All Site Users";
            default:
                return group.getName();
        }
    }
}
