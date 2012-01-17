/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Deque;
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
    static int[] getGroupsForPrincipal(int groupId)
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


    private static int[] computeAllGroups(UserPrincipal principal)
    {
        int userId = principal.getUserId();

        LinkedList<Integer> principals = new LinkedList<Integer>();

        // All principals are themselves
        principals.add(userId);

        // Every user is a member of the guests group; logged in users are members of Site Users.
        if (principal.getPrincipalType() == PrincipalType.USER)
        {
            principals.add(Group.groupGuests);
            if (principal.getUserId() != User.guest.getUserId())
                principals.add(Group.groupUsers);
        }

        return computeAllGroups(principals);
    }


    // Return all the principals plus all the groups they belong to (plus all the groups those groups belong to, etc.)
    public static int[] computeAllGroups(Deque<Integer> principals)
    {
        HashSet<Integer> groupSet = new HashSet<Integer>();

        while (!principals.isEmpty())
        {
            int id = principals.removeFirst();
            groupSet.add(id);
            int[] groups = getGroupsForPrincipal(id);

            for (int g : groups)
                if (!groupSet.contains(g))
                    principals.addLast(g);
        }

        // Site administrators always get developer role as well
        if (groupSet.contains(Group.groupAdministrators))
            groupSet.add(Group.groupDevelopers);

        return _toIntArray(groupSet);
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
