/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Entity;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserManager
{
    private static final Logger LOG = Logger.getLogger(UserManager.class);
    private static final CoreSchema CORE = CoreSchema.getInstance();

    // NOTE: This static map will slowly grow, since user IDs & timestamps are added and never removed. It's a trivial amount of data, though.
    private static final Map<Integer, Long> RECENT_USERS = new HashMap<>(100);

    public static final String USER_AUDIT_EVENT = "UserAuditEvent";

    public static final int VALID_GROUP_NAME_LENGTH = 64;

    public static final int VERIFICATION_EMAIL_TIMEOUT = 60 * 24;  // in minutes, one day currently

    //
    // UserListener
    //

    public interface UserListener extends PropertyChangeListener
    {
        void userAddedToSite(User user);

        void userDeletedFromSite(User user);

        void userAccountDisabled(User user);

        void userAccountEnabled(User user);
    }

    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<UserListener> _listeners = new CopyOnWriteArrayList<>();

    public static void addUserListener(UserListener listener)
    {
        _listeners.add(listener);
    }

    private static List<UserListener> getListeners()
    {
        return _listeners;
    }

    protected static void fireAddUser(User user)
    {
        List<UserListener> list = getListeners();
        for (UserListener userListener : list)
        {
            try
            {
                userListener.userAddedToSite(user);
            }
            catch (Throwable t)
            {
                LOG.error("fireAddPrincipalToGroup", t);
            }
        }
    }

    protected static List<Throwable> fireDeleteUser(User user)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userDeletedFromSite(user);
            }
            catch (Throwable t)
            {
                LOG.error("fireDeletePrincipalFromGroup", t);
                errors.add(t);
            }
        }
        return errors;
    }

    protected static List<Throwable> fireUserDisabled(User user)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userAccountDisabled(user);
            }
            catch (Throwable t)
            {
                LOG.error("fireUserDisabled", t);
                errors.add(t);
            }
        }
        return errors;
    }

    protected static List<Throwable> fireUserEnabled(User user)
    {
        List<UserListener> list = getListeners();
        List<Throwable> errors = new ArrayList<>();

        for (UserListener userListener : list)
        {
            try
            {
                userListener.userAccountEnabled(user);
            }
            catch (Throwable t)
            {
                LOG.error("fireUserEnabled", t);
                errors.add(t);
            }
        }
        return errors;
    }

    public static @Nullable User getUser(int userId)
    {
        if (userId == User.guest.getUserId())
            return User.guest;

        return UserCache.getUser(userId);
    }


    public static @Nullable User getUser(ValidEmail email)
    {
        return UserCache.getUser(email);
    }


    // Only used for creating/modifying a user, so no need to cache
    public static @Nullable User getUserByDisplayName(String displayName)
    {
        User[] users = new TableSelector(CORE.getTableInfoUsers(), new SimpleFilter(FieldKey.fromParts("DisplayName"), displayName), null).getArray(User.class);
        return users.length == 0 ? null : users[0];
    }


    public static void updateActiveUser(User user)
    {
        synchronized(RECENT_USERS)
        {
            RECENT_USERS.put(user.getUserId(), HeartBeat.currentTimeMillis());
        }
    }


    private static void removeActiveUser(User user)
    {
        synchronized(RECENT_USERS)
        {
            RECENT_USERS.remove(user.getUserId());
        }
    }


    // Includes users who have logged in during any server session
    public static int getRecentUserCount(Date since)
    {
        return new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT COUNT(*) FROM " + CORE.getTableInfoUsersData() + " WHERE LastLogin >= ?", since)).getObject(Integer.class);
    }


    /** Returns all users who have logged in during this server session since the specified interval */
    public static List<Pair<String, Long>> getRecentUsers(long since)
    {
        synchronized(RECENT_USERS)
        {
            long now = System.currentTimeMillis();
            List<Pair<String, Long>> recentUsers = new ArrayList<>(RECENT_USERS.size());

            for (int id : RECENT_USERS.keySet())
            {
                long lastActivity = RECENT_USERS.get(id);

                if (lastActivity >= since)
                {
                    User user = getUser(id);
                    String display = user != null ? user.getEmail() : "" + id;
                    recentUsers.add(new Pair<>(display, (now - lastActivity)/60000));
                }
            }

            // Sort by number of minutes
            Collections.sort(recentUsers, (o1, o2) -> (o1.second).compareTo(o2.second));

            return recentUsers;
        }
    }


    public static User getGuestUser()
    {
        return User.guest;
    }


    // Return display name if user id != null and user exists, otherwise return null
    public static String getDisplayName(Integer userId, User currentUser)
    {
        return getDisplayName(userId, false, currentUser);
    }


    // If userIdIfDeleted = true, then return "<userId>" if user doesn't exist
    public static String getDisplayNameOrUserId(Integer userId, User currentUser)
    {
        return getDisplayName(userId, true, currentUser);
    }


    private static String getDisplayName(Integer userId, boolean userIdIfDeleted, User currentUser)
    {
        if (userId == null)
            return null;

        if (User.guest.getUserId() == userId)
            return "Guest";

        User user = getUser(userId);

        if (user == null)
        {
            if (userIdIfDeleted)
                return "<" + userId + ">";
            else
                return null;
        }

        return user.getDisplayName(currentUser);
    }


    public static String getEmailForId(Integer userId)
    {
        if (userId == null)
            return null;

        if (User.guest.getUserId() == userId)
            return "Guest";

        User user = getUser(userId);
        return null != user ? user.getEmail() : null;
    }


    @NotNull
    public static Collection<User> getActiveUsers()
    {
        return UserCache.getActiveUsers();
    }


    public static List<Integer> getUserIds()
    {
        return UserCache.getUserIds();
    }

    public static void clearUserList()
    {
        UserCache.clear();
    }

    public static boolean userExists(ValidEmail email)
    {
        User user = getUser(email);
        return (null != user);
    }


    public static String sanitizeEmailAddress(String email)
    {
        if (email == null)
            return null;
        int index = email.indexOf('@');
        if (index != -1)
        {
            email = email.substring(0,index);
        }
        return email;
    }


    public static void addToUserHistory(User principal, String message)
    {
        User user = getGuestUser();
        Container c = ContainerManager.getRoot();

        try
        {
            ViewContext context = HttpView.currentContext();

            if (context != null)
            {
                user = context.getUser();
                c = context.getContainer();
            }
        }
        catch (RuntimeException ignored){}

        UserAuditEvent event = new UserAuditEvent(c.getId(), message, principal);
        AuditLogService.get().addEvent(user, event);
    }


    public static boolean hasNoUsers()
    {
        return 0 == getActiveUserCount();
    }

    public static int getUserCount(Date registeredBefore) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Created"), registeredBefore, CompareType.LTE);
        return (int)new TableSelector(CORE.getTableInfoUsersData(), filter, null).getRowCount();
    }

    public static int getActiveUserCount()
    {
        return UserCache.getActiveUserCount();
    }


    public static String validGroupName(String name, @NotNull PrincipalType type)
    {
        if (null == name || name.length() == 0)
            return "Name cannot be empty";
        if (!name.trim().equals(name))
            return "Name should not start or end with whitespace";

        switch (type)
        {
            // USER
            case USER:
                throw new IllegalArgumentException("User names are not allowed");

            // GROUP (regular project or global)
            case ROLE:
            case GROUP:
                // see renameGroup.jsp if you change this
                if (!StringUtils.containsNone(name, "@./\\-&~_"))
                    return "Group name should not contain punctuation.";
                if (name.length() > VALID_GROUP_NAME_LENGTH) // issue 14147
                    return "Name value is too long, maximum length is " + VALID_GROUP_NAME_LENGTH + " characters.";
                break;

            // MODULE MANAGED
            case MODULE:
                // no validation, HOWEVER must be UNIQUE
                // recommended start with @ or look like a GUID
                // must contain punctuation, but not look like email
                break;

            default:
                throw new IllegalArgumentException("Unknown principal type: '" + type + "'");
        }
        return null;
    }


    public static void updateLogin(User user)
    {
        SQLFragment sql = new SQLFragment("UPDATE " + CORE.getTableInfoUsersData() + " SET LastLogin = ? WHERE UserId = ?", new Date(), user.getUserId());
        new SqlExecutor(CORE.getSchema()).execute(sql);
    }


    public static void updateUser(User currentUser, User toUpdate) throws SQLException
    {
        Map<String, Object> typedValues = new HashMap<>();
        typedValues.put("phone", PageFlowUtil.formatPhoneNo(toUpdate.getPhone()));
        typedValues.put("mobile", PageFlowUtil.formatPhoneNo(toUpdate.getMobile()));
        typedValues.put("pager", PageFlowUtil.formatPhoneNo(toUpdate.getPager()));
        typedValues.put("im", toUpdate.getIM());

        if (!currentUser.isGuest())
            typedValues.put("displayName", toUpdate.getDisplayName(currentUser));

        typedValues.put("firstName", toUpdate.getFirstName());
        typedValues.put("lastName", toUpdate.getLastName());
        typedValues.put("description", toUpdate.getDescription());

        Table.update(currentUser, CORE.getTableInfoUsers(), typedValues, toUpdate.getUserId());
        clearUserList();

        addToUserHistory(toUpdate, "Contact information for " + toUpdate.getEmail() + " was updated");
    }

    public static void requestEmail(int userId, String currentEmail, ValidEmail requestedEmail, String verificationToken, User currentUser) throws SecurityManager.UserManagementException
    {
        if (SecurityManager.loginExists(currentEmail))
        {
            Instant timeoutDate = Instant.now().plus(VERIFICATION_EMAIL_TIMEOUT, ChronoUnit.MINUTES);
            SqlExecutor executor = new SqlExecutor(CORE.getSchema());
            int rows = executor.execute("UPDATE " + CORE.getTableInfoLogins() + " SET RequestedEmail=?, Verification=?, VerificationTimeout=? WHERE Email=?",
                    requestedEmail.getEmailAddress(), verificationToken, Date.from(timeoutDate), currentEmail);
            if (1 != rows)
                throw new SecurityManager.UserManagementException(requestedEmail, "Unexpected number of rows returned when setting verification: " + rows);
            addToUserHistory(getUser(userId), currentUser + " requested email address change from " + currentEmail + " to " + requestedEmail.getEmailAddress() +
                    " with token '" + verificationToken + "'.");
        }

        clearUserList();
    }

    public static void changeEmail(int userId, String oldEmail, ValidEmail newEmail, String verificationToken, User currentUser) throws SecurityManager.UserManagementException
    {
        changeEmail(userId, oldEmail, newEmail.getEmailAddress(), verificationToken, currentUser);
    }

    public static void changeEmail(int userId, String oldEmail, String newEmail, String verificationToken, User currentUser) throws SecurityManager.UserManagementException
    {
        if (SecurityManager.loginExists(oldEmail))
        {
            SqlExecutor executor = new SqlExecutor(CORE.getSchema());
            int rows = executor.execute("UPDATE " + CORE.getTableInfoPrincipals() + " SET Name=? WHERE UserId=?", newEmail, userId);
            if (1 != rows)
                throw new SecurityManager.UserManagementException(oldEmail, "Unexpected number of rows returned when setting new name: " + rows);
            rows = executor.execute("UPDATE " + CORE.getTableInfoLogins() + " SET Email=? WHERE Email=?", newEmail, oldEmail);
            if (1 != rows)
                throw new SecurityManager.UserManagementException(oldEmail, "Unexpected number of rows returned when setting new email: " + rows);
            addToUserHistory(getUser(userId), currentUser + " changed an email address from " + oldEmail + " to " + newEmail + " with token '" + verificationToken + "'.");
            User userToBeEdited = getUser(userId);

            if (userToBeEdited.getDisplayName(userToBeEdited).equals(oldEmail))
            {
                rows = executor.execute("UPDATE " + CORE.getTableInfoUsersData() + " SET DisplayName=? WHERE UserId=?", newEmail, userId);
                if (1 != rows)
                    throw new SecurityManager.UserManagementException(oldEmail, "Unexpected number of rows returned when setting new display name: " + rows);
            }
        }

        clearUserList();
    }

    public static void auditEmailTimeout(int userId, String oldEmail, String newEmail, String verificationToken, User currentUser)
    {
        addToUserHistory(getUser(userId), currentUser + " tried to change an email address from " + oldEmail + " to " + newEmail +
                " with token '" + verificationToken + "', but the verification link was timed out.");
    }

    public static Logger getLOG()
    {
        return LOG;
    }

    public static VerifyEmail getVerifyEmail(ValidEmail email)
    {
        SqlSelector sqlSelector = new SqlSelector(CORE.getSchema(), "SELECT RequestedEmail, Verification, VerificationTimeout FROM " + CORE.getTableInfoLogins()
                + " WHERE Email = ?", email.getEmailAddress());
        VerifyEmail verifyEmail = sqlSelector.getObject(VerifyEmail.class);
        return verifyEmail;
    }

    public static class VerifyEmail
    {
        private String _requestedEmail;
        private String _verification;
        private Date _verificationTimeout;

        public String getRequestedEmail()
        {
            return _requestedEmail;
        }

        public void setRequestedEmail(String requestedEmail)
        {
            this._requestedEmail = requestedEmail;
        }

        public String getVerification()
        {
            return _verification;
        }

        public void setVerification(String verification)
        {
            this._verification = verification;
        }

        public Date getVerificationTimeout()
        {
            return _verificationTimeout;
        }

        public void setVerificationTimeout(Date verificationTimeout)
        {
            this._verificationTimeout = verificationTimeout;
        }
    }

    public static void deleteUser(int userId) throws SecurityManager.UserManagementException
    {
        User user = getUser(userId);
        if (null == user)
            return;

        removeActiveUser(user);

        List<Throwable> errors = fireDeleteUser(user);

        if (errors.size() != 0)
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
                throw new RuntimeException(first);
        }

        try
        {
            SqlExecutor executor = new SqlExecutor(CORE.getSchema());
            executor.execute("DELETE FROM " + CORE.getTableInfoRoleAssignments() + " WHERE UserId=?", userId);
            executor.execute("DELETE FROM " + CORE.getTableInfoMembers() + " WHERE UserId=?", userId);
            addToUserHistory(user, user.getEmail() + " was deleted from the system");

            executor.execute("DELETE FROM " + CORE.getTableInfoUsersData() + " WHERE UserId=?", userId);
            executor.execute("DELETE FROM " + CORE.getTableInfoLogins() + " WHERE Email=?", user.getEmail());
            executor.execute("DELETE FROM " + CORE.getTableInfoPrincipals() + " WHERE UserId=?", userId);

            OntologyManager.deleteOntologyObject(user.getEntityId().toString(), ContainerManager.getSharedContainer(), true);
        }
        catch (Exception e)
        {
            LOG.error("deleteUser: " + e);
            throw new SecurityManager.UserManagementException(user.getEmail(), e);
        }
        finally
        {
            clearUserList();
        }
    }

    public static void setUserActive(User currentUser, int userIdToAdjust, boolean active) throws SecurityManager.UserManagementException
    {
        setUserActive(currentUser, getUser(userIdToAdjust), active);
    }

    public static void setUserActive(User currentUser, User userToAdjust, boolean active) throws SecurityManager.UserManagementException
    {
        if (null == userToAdjust)
            return;

        //no-op if active state is not actually changed
        if (userToAdjust.isActive() == active)
            return;

        removeActiveUser(userToAdjust);

        Integer userId = userToAdjust.getUserId();

        List<Throwable> errors = active ? fireUserEnabled(userToAdjust) : fireUserDisabled(userToAdjust);

        if (errors.size() != 0)
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
                throw new RuntimeException(first);
        }
        try
        {
            Table.update(currentUser, CoreSchema.getInstance().getTableInfoPrincipals(),
                    Collections.singletonMap("Active", active), userId);

            // update the modified bit on the users table
            Map<String, Object> map = new HashMap<>();
            map.put("Modified", new Timestamp(System.currentTimeMillis()));
            Table.update(currentUser, CoreSchema.getInstance().getTableInfoUsers(), map, userId);

            addToUserHistory(userToAdjust, "User account " + userToAdjust.getEmail() + " was " +
                    (active ? "re-enabled" : "disabled"));
        }
        catch(RuntimeSQLException e)
        {
            LOG.error("setUserActive: " + e);
            throw new SecurityManager.UserManagementException(userToAdjust.getEmail(), e);
        }
        finally
        {
            clearUserList();
        }
    }

    /**
     *  Get completions from list of all site users
     */
    public static List<AjaxCompletion> getAjaxCompletions(User currentUser, Container c) throws SQLException
    {
        return getAjaxCompletions(getActiveUsers(), currentUser, c);
    }

    /**
     * Returns the ajax completion objects for the specified groups and users.
     */
    public static List<AjaxCompletion> getAjaxCompletions(Collection<Group> groups, Collection<User> users, User currentUser, Container c)
    {
        List<AjaxCompletion> completions = new ArrayList<>();

        for (Group group : groups)
        {
            if (group.getName() != null)
            {
                String display = (group.getContainer() == null ? "Site: " : "") + group.getName();
                completions.add(new AjaxCompletion(display, group.getName()));
            }
        }

        if (!users.isEmpty())
            completions.addAll(getAjaxCompletions(users, currentUser, c));
        return completions;
    }

    /**
     * Returns the ajax completion objects for the specified users.
     */
    public static List<AjaxCompletion> getAjaxCompletions(Collection<User> users, User currentUser, Container c)
    {
        List<AjaxCompletion> completions = new ArrayList<>();

        boolean showEmailAddresses =  SecurityManager.canSeeEmailAddresses(c, currentUser);
        for (User user : users)
        {
            final String fullName = StringUtils.defaultString(user.getFirstName()) + " " + StringUtils.defaultString(user.getLastName());
            String completionValue = user.getAutocompleteName(c, currentUser);

            // Output the most human-friendly names possible for the pick list, and append the email addresses if the user has permission to see them

            if (!StringUtils.isBlank(fullName))
            {
                StringBuilder builder = new StringBuilder();
                builder.append(StringUtils.trimToEmpty(user.getFirstName())).append(" ").
                        append(StringUtils.trimToEmpty(user.getLastName()));

                if (showEmailAddresses)
                    builder.append(" (").append(user.getEmail()).append(")");

                completions.add(new AjaxCompletion(PageFlowUtil.filter(builder.toString()), completionValue));
            }
            else if (!user.getDisplayName(currentUser).equalsIgnoreCase(user.getEmail()))
            {
                StringBuilder builder = new StringBuilder();
                builder.append(user.getDisplayName(currentUser));

                if (showEmailAddresses)
                    builder.append(" (").append(user.getEmail()).append(")");

                completions.add(new AjaxCompletion(PageFlowUtil.filter(builder.toString()), completionValue));
            }
            else if (completionValue != null) // Note the only way to get here is if the displayName is the same as the email address
            {
                completions.add(new AjaxCompletion(completionValue));
            }
        }
        return completions;
    }

    public static class UserAuditEvent extends AuditTypeEvent
    {
        int _user;

        public UserAuditEvent()
        {
            super();
        }

        public UserAuditEvent(String container, String comment, User modifiedUser)
        {
            super(UserManager.USER_AUDIT_EVENT, container, comment);

            if (modifiedUser != null)
                _user = modifiedUser.getUserId();
        }

        public int getUser()
        {
            return _user;
        }

        public void setUser(int user)
        {
            _user = user;
        }
    }

    /**
     *
     * @param modifiedUser the user id of the principal being modified
     */
    public static void addAuditEvent(User user, Container c,  @Nullable User modifiedUser, String msg)
    {
        UserAuditEvent event = new UserAuditEvent(c.getId(), msg, modifiedUser);
        AuditLogService.get().addEvent(user, event);
    }

    /**
     * Parse an array of email addresses and/or display names into a list of corresponding userIds.
     * Inputs which did not resolve to a current active user are preserved in the output.
     * @param theList Any combination of email addresses and display names
     * @return List of corresponding userIds. Unresolvable inputs are preserved.
     */
    public static List<String> parseUserListInput(String[] theList)
    {
        return parseUserListInput(new LinkedHashSet<>(Arrays.asList(theList)));
    }

    /**
     * Parse a string of delimited email addresses and/or display names into a delimited string of
     * corresponding userIds. Inputs which did not resolve to a current active user are preserved in the output.
     * @param theList Any combination of email addresses and display names, delimited with semi-colons or new line characters
     * @return Semi-colon delimited string of corresponding userIds. Unresolvable inputs are preserved.
     */
    public static String parseUserListInput(String theList)
    {
        String[] names = StringUtils.split(StringUtils.trimToEmpty(theList), ";\n");
        return  StringUtils.join(parseUserListInput(names),";");
    }

    /**
     * Parse a set of email addresses and/or display names into a list of corresponding userIds.
     * Inputs which did not resolve to a current active user are preserved in the output.
     * @param theList Any combination of email addresses and display names
     * @return List of corresponding userIds. Unresolvable inputs are preserved.
     */
    public static List<String> parseUserListInput(Set<String> theList)
    {
        ArrayList<String> parsed = new ArrayList<>(theList.size());
        for (String name : theList)
        {
            if (null == (name = StringUtils.trimToNull(name)))
                continue;
            User u = null;
            try { u = getUser(new ValidEmail(name)); } catch (ValidEmail.InvalidEmailException ignored) {}
            if (null == u)
                u = getUserByDisplayName(name);
            parsed.add(null == u ? name : String.valueOf(u.getUserId()));
        }
        return parsed;
    }
}
