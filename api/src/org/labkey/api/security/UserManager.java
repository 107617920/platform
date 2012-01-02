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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserManager
{
    private static final Logger LOG = Logger.getLogger(UserManager.class);
    private static final CoreSchema CORE = CoreSchema.getInstance();

    // NOTE: This static map will slowly grow, since user IDs & timestamps are added and never removed.  It's a trivial amount of data, though.
    private static final Map<Integer, Long> RECENT_USERS = new HashMap<Integer, Long>(100);

    private static final String USER_PREF_MAP = "UserPreferencesMap";
    private static final String USER_REQUIRED_FIELDS = "UserInfoRequiredFields";

    public static final String USER_AUDIT_EVENT = "UserAuditEvent";

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
    private static final List<UserListener> _listeners = new CopyOnWriteArrayList<UserListener>();

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
        List<Throwable> errors = new ArrayList<Throwable>();

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
        List<Throwable> errors = new ArrayList<Throwable>();

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
        List<Throwable> errors = new ArrayList<Throwable>();

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
        return new TableSelector(CORE.getTableInfoUsers(), new SimpleFilter("DisplayName", displayName), null).getObject(User.class);
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
            List<Pair<String, Long>> recentUsers = new ArrayList<Pair<String, Long>>(RECENT_USERS.size());

            for (int id : RECENT_USERS.keySet())
            {
                long lastActivity = RECENT_USERS.get(id);

                if (lastActivity >= since)
                {
                    User user = getUser(id);
                    String display = user != null ? user.getEmail() : "" + id;
                    recentUsers.add(new Pair<String, Long>(display, (now - lastActivity)/60000));
                }
            }

            // Sort by number of minutes
            Collections.sort(recentUsers, new Comparator<Pair<String, Long>>()
                {
                    public int compare(Pair<String, Long> o1, Pair<String, Long> o2)
                    {
                        return (o1.second).compareTo(o2.second);
                    }
                }
            );

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


    public static Collection<User> getActiveUsers()
    {
        return UserCache.getActiveUsers();
    }


    // Returns a modifiable, sorted collection of email addresses for the active users
    public static List<String> getActiveUserEmails()
    {
        return UserCache.getActiveUserEmails();
    }


    static void clearUserList(int userId)
    {
        UserCache.remove(userId);
    }


    public static boolean userExists(ValidEmail email)
    {
        User user = getUser(email);
        return (null != user);
    }


    public static String sanitizeEmailAddress(String email)
    {
        if (email == null)
            return email;
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
        catch (RuntimeException e){}

        AuditLogService.get().addEvent(user, c, USER_AUDIT_EVENT, principal.getUserId(), message);
    }


    public static boolean hasNoUsers()
    {
        return 0 == getActiveUserCount();
    }

    public static int getUserCount(Date registeredBefore) throws SQLException
    {
        return Table.executeSingleton(CORE.getSchema(), "SELECT COUNT(*) FROM " + CORE.getTableInfoUsersData() + " WHERE Created <= ?", new Object[] { registeredBefore }, Integer.class);
    }

    public static int getActiveUserCount()
    {
        return UserCache.getActiveUserCount();
    }


    public static String validGroupName(String name, String type)
    {
        if (null == name || name.length() == 0)
            return "Name cannot be empty";
        if (!name.trim().equals(name))
            return "Name should not start or end with whitespace";

        GroupManager.PrincipalType pt = GroupManager.PrincipalType.forChar(type.charAt(0));
        if (null == pt)
            throw new IllegalArgumentException("Unknown principal type: '" + type + "'");
        
        switch (pt)
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
        new SqlExecutor(CORE.getSchema(), sql).execute();
    }


    public static void updateUser(User currentUser, Map<String, Object> typedValues, Object pkVal) throws SQLException
    {
        typedValues.put("phone", PageFlowUtil.formatPhoneNo((String) typedValues.get("phone")));
        typedValues.put("mobile", PageFlowUtil.formatPhoneNo((String) typedValues.get("mobile")));
        typedValues.put("pager", PageFlowUtil.formatPhoneNo((String) typedValues.get("pager")));
        Table.update(currentUser, CORE.getTableInfoUsers(), typedValues, pkVal);
        clearUserList(currentUser.getUserId());

        User principal = getUser((Integer)pkVal);

        if (principal != null)
        {
            addToUserHistory(principal, "Contact information for " + principal.getEmail() + " was updated");
        }
    }


    public static String changeEmail(int userId, ValidEmail oldEmail, ValidEmail newEmail, User currentUser)
    {
        if (null != getUser(newEmail))
            return newEmail + " already exists.";

        try
        {
            Table.execute(CORE.getSchema(), "UPDATE " + CORE.getTableInfoPrincipals() + " SET Name=? WHERE UserId=?", newEmail.getEmailAddress(), userId);

            if (SecurityManager.loginExists(oldEmail))
            {
                Table.execute(CORE.getSchema(), "UPDATE " + CORE.getTableInfoLogins() + " SET Email=? WHERE Email=?", newEmail.getEmailAddress(), oldEmail.getEmailAddress());
                addToUserHistory(getUser(userId), currentUser + " changed email from " + oldEmail.getEmailAddress() + " to " + newEmail.getEmailAddress() + ".");
                User userToBeEdited = getUser(userId);

                if (userToBeEdited.getDisplayName(userToBeEdited).equals(oldEmail.getEmailAddress()))
                {
                    Table.execute(CORE.getSchema(), "UPDATE " + CORE.getTableInfoUsersData() + " SET DisplayName=? WHERE UserId=?", newEmail.getEmailAddress(), userId);
                }
            }

            clearUserList(userId);
        }
        catch (SQLException e)
        {
            LOG.error("changeEmail: " + e);
            return (e.getMessage());
        }

        return null;
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
            Table.execute(CORE.getSchema(), "DELETE FROM " + CORE.getTableInfoRoleAssignments() + " WHERE UserId=?", userId);
            Table.execute(CORE.getSchema(), "DELETE FROM " + CORE.getTableInfoMembers() + " WHERE UserId=?", userId);
            addToUserHistory(user, user.getEmail() + " was deleted from the system");

            Table.execute(CORE.getSchema(), "DELETE FROM " + CORE.getTableInfoUsersData() + " WHERE UserId=?", userId);
            Table.execute(CORE.getSchema(), "DELETE FROM " + CORE.getTableInfoLogins() + " WHERE Email=?", user.getEmail());
            Table.execute(CORE.getSchema(), "DELETE FROM " + CORE.getTableInfoPrincipals() + " WHERE UserId=?", userId);
        }
        catch (SQLException e)
        {
            LOG.error("deleteUser: " + e);
            throw new SecurityManager.UserManagementException(user.getEmail(), e);
        }
        finally
        {
            clearUserList(user.getUserId());
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
            addToUserHistory(userToAdjust, "User account " + userToAdjust.getEmail() + " was " + 
                    (active ? "re-enabled" : "disabled"));
        }
        catch(SQLException e)
        {
            LOG.error("setUserActive: " + e);
            throw new SecurityManager.UserManagementException(userToAdjust.getEmail(), e);
        }
        finally
        {
            clearUserList(userId);
        }
    }

    public static String getRequiredUserFields()
    {
        Map<String, String> map = getUserPreferences(false);
        return map.get(USER_REQUIRED_FIELDS);
    }

    public static void setRequiredUserFields(String requiredFields) throws SQLException
    {
        Map<String, String> map = getUserPreferences(true);
        map.put(USER_REQUIRED_FIELDS, requiredFields);
        PropertyManager.saveProperties(map);
    }

    public static @NotNull Map<String, String> getUserPreferences(boolean writable)
    {
        if (writable)
            return PropertyManager.getWritableProperties(USER_PREF_MAP, true);
        else
            return PropertyManager.getProperties(USER_PREF_MAP);
    }

    // Get completions from list of all site users
    public static List<AjaxCompletion> getAjaxCompletions(String prefix, User currentUser) throws SQLException
    {
        return getAjaxCompletions(prefix, Collections.<Group>emptyList(), getActiveUsers(), currentUser);
    }

    public static List<AjaxCompletion> getAjaxCompletions(String prefix, Collection<User> users, User currentUser)
    {
        return getAjaxCompletions(prefix, Collections.<Group>emptyList(), users, currentUser);
    }

    // Get completions from specified list of groups and users
    public static List<AjaxCompletion> getAjaxCompletions(String prefix, Collection<Group> groups, Collection<User> users, User currentUser)
    {
        List<AjaxCompletion> completions = new ArrayList<AjaxCompletion>();

        if (prefix != null && prefix.length() != 0)
        {
            String lowerPrefix = prefix.toLowerCase();

            for (Group group : groups)
            {
                if (group.getName() != null && group.getName().toLowerCase().startsWith(lowerPrefix))
                    completions.add(new AjaxCompletion("<b>" + group.getName() + "</b>", group.getName()));
            }

            for (User user : users)
            {
                final String fullName = StringUtils.defaultString(user.getFirstName()) + " " + StringUtils.defaultString(user.getLastName());

                if (fullName.toLowerCase().startsWith(lowerPrefix) ||
                    user.getLastName() != null && user.getLastName().toLowerCase().startsWith(lowerPrefix))
                {
                    String display;
                    if (user.getFirstName() != null || user.getLastName() != null)
                    {
                        StringBuilder builder = new StringBuilder();
                        builder.append(StringUtils.trimToEmpty(user.getFirstName())).append(" ").
                                append(StringUtils.trimToEmpty(user.getLastName()));
                        builder.append(" (").append(user.getEmail()).append(")");
                        display = builder.toString();
                    }
                    else
                    {
                        display = user.getEmail();
                    }

                    completions.add(new AjaxCompletion(display, user.getEmail()));
                }
                else if (user.getDisplayName(currentUser).compareToIgnoreCase(user.getEmail()) != 0 &&
                        user.getDisplayName(currentUser).toLowerCase().startsWith(lowerPrefix))
                {
                    StringBuilder builder = new StringBuilder();
                    builder.append(user.getDisplayName(currentUser)).append(" ");
                    builder.append(" (").append(user.getEmail()).append(")");
                    completions.add(new AjaxCompletion(builder.toString(), user.getEmail()));
                }
                else if (user.getEmail() != null && user.getEmail().toLowerCase().startsWith(lowerPrefix))
                {
                    completions.add(new AjaxCompletion(user.getEmail(), user.getEmail()));
                }
            }
        }

        return completions;
    }
}
