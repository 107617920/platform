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
package org.labkey.api.security;

import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.HasContextualRoles;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/*
* User: Dave
* Date: May 13, 2009
* Time: 4:04:49 PM
*/
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target({ElementType.METHOD,ElementType.TYPE})
public @interface RequiresOneOf
{
    Class<? extends Permission>[] value();

    Class<? extends HasContextualRoles>[] contextual() default { };
}