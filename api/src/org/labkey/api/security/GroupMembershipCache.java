package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * User: adam
 * Date: 10/11/11
 * Time: 9:55 PM
 */
public class GroupMembershipCache
{
    private static final String ALL_GROUPS_PREFIX = "AllGroups=";
    private static final String IMMEDIATE_GROUPS_PREFIX = "ImmGroups=";
    private static final CoreSchema CORE = CoreSchema.getInstance();
    private static final StringKeyCache<int[]> CACHE = CacheManager.getStringKeyCache(10000, CacheManager.DAY, "Group Memberships");

    static
    {
        UserManager.addUserListener(new GroupMembershipUserListener());
    }


    private final static CacheLoader<String, int[]> ALL_GROUPS_LOADER = new CacheLoader<String, int[]>()
    {
        @Override
        public int[] load(String key, Object argument)
        {
            UserPrincipal user = (UserPrincipal)argument;
            return computeAllGroups(user);
        }
    };


    private final static CacheLoader<String, int[]> IMMEDIATE_GROUPS_LOADER = new CacheLoader<String, int[]>()
    {
        @Override
        public int[] load(String key, Object argument)
        {
            int groupId = (Integer)argument;
            SqlSelector selector = new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT GroupId FROM " + CORE.getTableInfoMembers() + " WHERE UserId = ?", groupId));
            Integer[] groupsInt = selector.getArray(Integer.class);
            return _toIntArray(groupsInt);
        }
    };


    // Returns the FLATTENED group list for this principal
    static int[] getAllGroupsForPrincipal(@NotNull UserPrincipal user)
    {
        return CACHE.get(ALL_GROUPS_PREFIX + user.getUserId(), user, ALL_GROUPS_LOADER);
    }


    // Returns the immediate group membership for this principal (non-recursive)
    static int[] getGroupsForPrincipal(int groupId) throws SQLException
    {
        return CACHE.get(IMMEDIATE_GROUPS_PREFIX + groupId, groupId, IMMEDIATE_GROUPS_LOADER);
    }


    static void handleGroupChange(Group group, UserPrincipal principal)
    {
        // very slight overkill
        uncache(group);
        uncache(principal);

        // invalidate all computed group lists (getAllGroups())
        if (principal instanceof Group)
            CACHE.removeUsingPrefix(ALL_GROUPS_PREFIX);
    }


    private static void uncache(UserPrincipal principal)
    {
        CACHE.remove(ALL_GROUPS_PREFIX + principal.getUserId());
        CACHE.remove(IMMEDIATE_GROUPS_PREFIX + principal.getUserId());
    }


    private static int[] _toIntArray(Integer[] groupsInt)
    {
        int[] arr = new int[groupsInt.length];
        for (int i=0 ; i<groupsInt.length ; i++)
            arr[i] = groupsInt[i];
        return arr;
    }


    private static int[] _toIntArray(Set<Integer> groupsInt)
    {
        int[] arr = new int[groupsInt.size()];
        int i = 0;
        for (int group : groupsInt)
            arr[i++] = group;
        Arrays.sort(arr);
        return arr;
    }


    private static int[] computeAllGroups(UserPrincipal user)
    {
        int userId = user.getUserId();

        try
        {
            HashSet<Integer> groupSet = new HashSet<Integer>();
            LinkedList<Integer> recurse = new LinkedList<Integer>();
            recurse.add(Group.groupGuests);
            if (user.getUserId() != User.guest.getUserId())
                recurse.add(Group.groupUsers);
            recurse.add(userId);

            while (!recurse.isEmpty())
            {
                int id = recurse.removeFirst();
                groupSet.add(id);
                int[] groups = getGroupsForPrincipal(id);

                for (int g : groups)
                    if (!groupSet.contains(g))
                        recurse.addLast(g);
            }

            // Site administrators always get developer role as well
            if (groupSet.contains(Group.groupAdministrators))
                groupSet.add(Group.groupDevelopers);

            return _toIntArray(groupSet);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public static class GroupMembershipUserListener implements UserManager.UserListener
    {
        public void userAddedToSite(User user)
        {
        }

        public void userDeletedFromSite(User user)
        {
            // Blow away groups immediately after user is deleted (otherwise this user's groups, and therefore permissions, will remain active
            // until the user choses to sign out.
            uncache(user);
        }

        public void userAccountDisabled(User user)
        {
            uncache(user);

        }

        public void userAccountEnabled(User user)
        {
            uncache(user);
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
        }
    }
}
