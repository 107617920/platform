/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.labkey.api.security.permissions.Permission;

/*
* User: Dave
* Date: Apr 22, 2009
* Time: 10:56:17 AM
*/
public abstract class AbstractContextualRole extends AbstractRole implements ContextualRole
{
    protected AbstractContextualRole(String name, String description, Class<? extends Permission>... perms)
    {
        super(name, description, perms);
    }

    @Override
    public boolean isAssignable()
    {
        return false;
    }
}