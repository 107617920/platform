/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;

import java.sql.SQLException;
import java.util.Date;
import java.util.Set;

/**
 * User: adam
 * Date: 7/6/12
 * Time: 6:39 PM
 */
public class SecurityPolicyManager
{
    private static final CoreSchema core = CoreSchema.getInstance();
    private static final Cache<String, SecurityPolicy> CACHE = new DatabaseCache<SecurityPolicy>(core.getSchema().getScope(), 20000, "SecurityPolicies");

    @NotNull
    public static SecurityPolicy getPolicy(@NotNull SecurableResource resource)
    {
        return getPolicy(resource, true);
    }

    @NotNull
    public static SecurityPolicy getPolicy(@NotNull final SecurableResource resource, boolean findNearest)
    {
        SecurityPolicy policy = CACHE.get(cacheKey(resource), resource, new CacheLoader<String, SecurityPolicy>()
        {
            @Override
            public SecurityPolicy load(String key, @Nullable Object argument)
            {
                SimpleFilter filter = new SimpleFilter("ResourceId", resource.getResourceId());

                SecurityPolicyBean policyBean = Table.selectObject(core.getTableInfoPolicies(), resource.getResourceId(),
                        SecurityPolicyBean.class);

                TableInfo table = core.getTableInfoRoleAssignments();

                Selector selector = new TableSelector(table, filter, new Sort("UserId"));
                RoleAssignment[] assignments = selector.getArray(RoleAssignment.class);

                return new SecurityPolicy(resource, assignments, null != policyBean ? policyBean.getModified() : new Date());
            }
        });

        if (findNearest && policy.isEmpty() && resource.mayInheritPolicy())
        {
            SecurableResource parent = resource.getParentResource();
            if (null != parent)
                return getPolicy(parent, findNearest);
        }

        return policy;
    }

    public static String cacheKey(SecurableResource resource)
    {
        return resource.getResourceId();
    }

    private static String cacheKey(SecurityPolicy resource)
    {
        return resource.getResourceId();
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
            remove(policy);
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

    public static void notifyPolicyChange(String objectID)
    {
        // UNDONE: generalize cross manager/module notifications
        ContainerManager.notifyContainerChange(objectID);
    }

    public static void deletePolicy(@NotNull SecurableResource resource)
    {
        DbScope scope = core.getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            //delete all rows where resourceid = resource.getResourceId()
            SimpleFilter filter = new SimpleFilter("ResourceId", resource.getResourceId());
            Table.delete(core.getTableInfoRoleAssignments(), filter);
            Table.delete(core.getTableInfoPolicies(), filter);

            //commit transaction
            scope.commitTransaction();

            //remove the resource-oriented policy from cache
            remove(resource);

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
            sep = ", ";
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
            sep = ", ";
        }

        sql.append(principalsList);
        sql.append(")");

        new SqlExecutor(core.getSchema()).execute(sql);

        removeAll();

        for (SecurableResource resource : resources)
        {
            notifyPolicyChange(resource.getResourceId());
        }
    }


    public static void removeAll(Container c)
    {
        try
        {
            Table.execute(
                    core.getSchema(),
                    "DELETE FROM " + core.getTableInfoRoleAssignments() + " WHERE ResourceId IN (SELECT ResourceId FROM " +
                    core.getTableInfoPolicies() + " WHERE Container = ?)",
                    c.getId());
            Table.execute(
                    core.getSchema(),
                    "DELETE FROM " + core.getTableInfoPolicies() + " WHERE Container = ?",
                    c.getId());

            removeAll();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    // Remove all assignments and policies on the Containers childre
    public static void removeAllChildren(Container c) throws SQLException
    {
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

        new SqlExecutor(core.getSchema()).execute("DELETE FROM " + core.getTableInfoRoleAssignments() + "\n" +
                "WHERE ResourceId IN (SELECT ResourceId FROM " + core.getTableInfoPolicies() + " WHERE Container IN (" +
                sb.toString() + "))");
        new SqlExecutor(core.getSchema()).execute("DELETE FROM " + core.getTableInfoPolicies() + "\n" +
                "WHERE Container IN (" + sb.toString() + ")");

        removeAll();
    }


    private static void remove(SecurableResource resource)
    {
        CACHE.remove(cacheKey(resource));
    }


    private static void remove(SecurityPolicy policy)
    {
        CACHE.remove(cacheKey(policy));
    }


    private static void removeAll()
    {
        CACHE.clear();
    }
}
