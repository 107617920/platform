/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/*
* User: Dave
* Date: Apr 13, 2009
* Time: 10:31:50 AM
*/

/**
 * Represents a security role, which is a set of permissions with a name and description
 */
public interface Role
{
    /**
     * Returns a unique name for this role. Typically, this is the
     * fully qualified class name of the role, but it could also be
     * some other name that is unique within the web application.
     * @return A unique name for this role
     */
    @NotNull
    public String getUniqueName();

    /**
     * Returns a short friendly name suitable for display in a user interface.
     * @return The role's name.
     */
    @NotNull
    public String getName();

    /**
     * Returns a description of the role for display in a user interface.
     * @return The role's description.
     */
    @NotNull
    public String getDescription();

    /**
     * Returns the set of permission classes the role grants.
     * @return The permissions granted by the role.
     */
    @NotNull
    public Set<Class<? extends Permission>> getPermissions();

    /**
     * Adds a new permission to this role. Modules may use this method
     * to add permissions defined by the module to system-defined roles
     * @param perm The new permission
     */
    public void addPermission(@NotNull Class<? extends Permission> perm);

    /**
     * Returns a reference to the module in which this role was defined.
     * @return The source module.
     */
    @NotNull
    public Module getSourceModule();

    /**
     * Returns true if this role may be assigned to groups/users.
     * If this returns false, the role should not be displayed in a
     * security management user interface. An example where this would
     * return true are contextual roles, which are dynamically assigned
     * by the system based on context (e.g., Owner).
     * @return True if this role is assignable.
     */
    public boolean isAssignable();

}