/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/*
* User: Dave
* Date: Apr 27, 2009
* Time: 10:58:34 AM
*/

/**
 * Represents a security policy for a securable resource. You can get a security policy for a resource
 * using SecurityMananger.getPolicy(). Note that this class is immutable once constructed, so it may
 * be used by multiple threads at the same time. To make changes to an existing policy, construct a new
 * {@link MutableSecurityPolicy} passing the existing SecurityPolicy instance in the constructor.
 */
public class SecurityPolicy implements HasPermission
{
    protected final SortedSet<RoleAssignment> _assignments = new TreeSet<RoleAssignment>();
    protected String _resourceId;
    protected String _containerId;
    protected String _resourceClass;
    protected Date _modified;

    public SecurityPolicy(@NotNull SecurableResource resource)
    {
        _resourceId = resource.getResourceId();
        _resourceClass = resource.getClass().getName();
        _containerId = resource.getResourceContainer().getId();
    }

    public SecurityPolicy(@NotNull SecurableResource resource, @NotNull RoleAssignment[] assignments)
    {
        this(resource);
        _assignments.addAll(Arrays.asList(assignments));
    }

    public SecurityPolicy(@NotNull SecurableResource resource, @NotNull RoleAssignment[] assignments, Date lastModified)
    {
        this(resource, assignments);
        _modified = lastModified;
    }

    /**
     * Creates a new policy for the given securable resource, using the other policy's role assignments
     * as a template.
     * @param resource The resource for this policy
     * @param otherPolicy Another policy to use as a template
     */
    public SecurityPolicy(@NotNull SecurableResource resource, @NotNull SecurityPolicy otherPolicy)
    {
        this(resource);
        for (RoleAssignment assignment : otherPolicy.getAssignments())
        {
            RoleAssignment newAssignment = new RoleAssignment();
            newAssignment.setResourceId(resource.getResourceId());
            newAssignment.setUserId(assignment.getUserId());
            newAssignment.setRole(assignment.getRole());
            _assignments.add(newAssignment);
        }
    }

    /**
     * Creates a new policy for the same resource as the other policy, with the same role assignments
     * @param otherPolicy A template policy
     */
    public SecurityPolicy(@NotNull SecurityPolicy otherPolicy)
    {
        _resourceId = otherPolicy._resourceId;
        _resourceClass = otherPolicy._resourceClass;
        _containerId = otherPolicy._containerId;
        _modified = otherPolicy._modified;
        for (RoleAssignment assignment : otherPolicy.getAssignments())
        {
            RoleAssignment newAssignment = new RoleAssignment();
            newAssignment.setResourceId(_resourceId);
            newAssignment.setUserId(assignment.getUserId());
            newAssignment.setRole(assignment.getRole());
            _assignments.add(newAssignment);
        }
    }

    @NotNull
    public String getResourceId()
    {
        return _resourceId;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public String getResourceClass()
    {
        return _resourceClass;
    }

    @NotNull
    public SortedSet<RoleAssignment> getAssignments()
    {
        return Collections.unmodifiableSortedSet(_assignments);
    }

    /**
     * Returns only the roles directly assigned to this principal
     * (not other roles the principal is playing due to group
     * memberships).
     * @param principal The principal
     * @return The roles this principal is directly assigned
     */
    @NotNull
    public List<Role> getAssignedRoles(@NotNull UserPrincipal principal)
    {
        List<Role> roles = new ArrayList<Role>();
        for (RoleAssignment assignment : _assignments)
        {
            if (assignment.getUserId() == principal.getUserId())
                roles.add(assignment.getRole());
        }
        return roles;
    }

    /**
     * Returns the roles the principal is playing, either due to
     * direct assignment, or due to membership in a group that is
     * assigned the role.
     * @param principal The principal
     * @return The roles this principal is playing
     */
    @NotNull
    public Set<Role> getEffectiveRoles(@NotNull UserPrincipal principal)
    {
        Set<Role> roles = getRoles(principal.getGroups());
        roles.addAll(getContextualRoles(principal));
        return roles;
    }

    @NotNull
    public Set<Class<? extends Permission>> getPermissions(@NotNull UserPrincipal principal)
    {
        return getPermissions(principal, null);
    }

    @NotNull
    public List<String> getPermissionNames(@NotNull UserPrincipal principal)
    {
        Set<Class<? extends Permission>> perms = getPermissions(principal);
        List<String> names = new ArrayList<String>(perms.size());
        for (Class<? extends Permission> perm : perms)
        {
            Permission permInst = RoleManager.getPermission(perm);
            if (null != permInst)
                names.add(permInst.getUniqueName());
        }
        return names;
    }

    @NotNull
    public Set<Class<? extends Permission>> getPermissions(@NotNull UserPrincipal principal, @Nullable Set<Role> contextualRoles)
    {
        // TODO: Should we be mutating the result of getContextualRoles()?  Some implementations would like to return unmodifiable collections...
        Set<Role> allContextualRoles = getContextualRoles(principal);
        if (contextualRoles != null)
            allContextualRoles.addAll(contextualRoles);

        return getPermissions(principal.getGroups(), allContextualRoles);
    }

    /**
     * Returns true if this policy is empty (i.e., no role assignments).
     * This method is useful for distinguishing between a policy that has
     * been established for a SecurableResource and a cached "miss"
     * (i.e., no explicit policy defined).
     * @return True if this policy is empty
     */
    public boolean isEmpty()
    {
        return _assignments.size() == 0;
    }

    public boolean hasPermission(@NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission)
    {
        return hasPermission(principal, permission, null);
    }

    public boolean hasPermission(@NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission, @Nullable Set<Role> contextualRoles)
    {
        return getPermissions(principal, contextualRoles).contains(permission);
    }

    public boolean hasPermissions(@NotNull UserPrincipal principal, Class<? extends Permission>... permissions)
    {
        Set<Class<? extends Permission>> permsSet = new HashSet<Class<? extends Permission>>();
        permsSet.addAll(Arrays.asList(permissions));
        return hasPermissions(principal, permsSet);
    }

    public boolean hasPermissions(@NotNull UserPrincipal principal, @NotNull Set<Class<? extends Permission>> permissions)
    {
        return hasPermissions(principal, permissions, null);
    }

    public boolean hasPermissions(@NotNull UserPrincipal principal, @NotNull Set<Class<? extends Permission>> permissions, @Nullable Set<Role> contextualRoles)
    {
        return getPermissions(principal, contextualRoles).containsAll(permissions);
    }

    /**
     * Returns true if the principal has at least one of the required permissions.
     * @param principal The principal.
     * @param permissions The set of required permissions.
     * @param contextualRoles An optional set of contextual roles (or null)
     * @return True if the principal has at least one of the required permissions.
     */
    public boolean hasOneOf(@NotNull UserPrincipal principal, @NotNull Collection<Class<? extends Permission>> permissions, @Nullable Set<Role> contextualRoles)
    {
        Set<Class<? extends Permission>> grantedPerms = getPermissions(principal, contextualRoles);
        for (Class<? extends Permission> requiredPerm : permissions)
        {
            if (grantedPerms.contains(requiredPerm))
                return true;
        }
        return false;
    }


    protected Set<Class<? extends Permission>> getPermissions(@NotNull int[] principals, @Nullable Set<Role> contextualRoles)
    {
        Set<Class<? extends Permission>> perms = new HashSet<Class<? extends Permission>>();

        //role assignments are sorted by user id,
        //as are the principal ids,
        //so iterrate over both of them in one pass
        Iterator<RoleAssignment> assignmentIter = getAssignments().iterator();
        RoleAssignment assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
        int principalsIdx = 0;

        while (null != assignment && principalsIdx < principals.length)
        {
            if (assignment.getUserId() == principals[principalsIdx])
            {
                if (null != assignment.getRole())
                    perms.addAll(assignment.getRole().getPermissions());

                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            }
            else if (assignment.getUserId() < principals[principalsIdx])
                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            else
                ++principalsIdx;
        }

        //apply contextual roles if any
        if (null != contextualRoles)
        {
            for (Role role : contextualRoles)
            {
                perms.addAll(role.getPermissions());
            }
        }

        return perms;
    }


    @NotNull
    protected Set<Role> getRoles(@NotNull int[] principals)
    {
        Set<Role> roles = new HashSet<Role>();

        //role assignments are sorted by user id,
        //as are the principal ids,
        //so iterate over both of them in one pass
        Iterator<RoleAssignment> assignmentIter = getAssignments().iterator();
        RoleAssignment assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
        int principalsIdx = 0;

        while (null != assignment && principalsIdx < principals.length)
        {
            if (assignment.getUserId() == principals[principalsIdx])
            {
                if (null != assignment.getRole())
                    roles.add(assignment.getRole());

                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            }
            else if (assignment.getUserId() < principals[principalsIdx])
                assignment = assignmentIter.hasNext() ? assignmentIter.next() : null;
            else
                ++principalsIdx;
        }

        return roles;
    }

    /**
     * This is purely for backwards compatibility with HTTP APIs--Do not use for new code!
     * @param principal the user/group
     * @return old-style bitmask for basic permissions
     * @deprecated Use getPermissions() instead.
     */
    public int getPermsAsOldBitMask(UserPrincipal principal)
    {
        int perms = 0;
        Set<Class<? extends Permission>> permClasses = getPermissions(principal);
        if (permClasses.contains(ReadPermission.class))
            perms |= ACL.PERM_READ;
        if (permClasses.contains(InsertPermission.class))
            perms |= ACL.PERM_INSERT;
        if (permClasses.contains(UpdatePermission.class))
            perms |= ACL.PERM_UPDATE;
        if (permClasses.contains(DeletePermission.class))
            perms |= ACL.PERM_DELETE;
        if (permClasses.contains(AdminPermission.class))
            perms |= ACL.PERM_ADMIN;

        return perms;
    }

    @Nullable
    public Date getModified()
    {
        return _modified;
    }

    @NotNull
    public SecurityPolicyBean getBean()
    {
        return new SecurityPolicyBean(_resourceId, _resourceClass, ContainerManager.getForId(_containerId), _modified);
    }

    /**
     * Serializes this policy into a map suitable for returning via an API action
     * @return The serialized policy
     */
    @NotNull
    public Map<String, Object> toMap()
    {
        Map<String, Object> props = new HashMap<String, Object>();

        //modified
        props.put("modified", getModified());

        //resource id
        props.put("resourceId", getResourceId());

        //role assignments
        List<Map<String, Object>> assignments = new ArrayList<Map<String, Object>>();
        for (RoleAssignment assignment : getAssignments())
        {
            Map<String, Object> assignmentProps = new HashMap<String, Object>();
            assignmentProps.put("userId", assignment.getUserId());
            assignmentProps.put("role", assignment.getRole().getUniqueName());
            assignments.add(assignmentProps);
        }
        props.put("assignments", assignments);
        return props;
    }

    @NotNull
    protected Set<Role> getContextualRoles(UserPrincipal principal)
    {
        return principal.getContextualRoles(this);
    }
}
