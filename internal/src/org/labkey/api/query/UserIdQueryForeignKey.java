/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.data.*;
import org.labkey.api.security.User;

/*
* User: Dave
* Date: Jul 28, 2008
* Time: 4:57:59 PM
*/

/**
 * Foreign key class for use with Query and the 'core'
 * User Schema. Use this when setting FKs on AbstractTables
 */
public class UserIdQueryForeignKey extends LookupForeignKey
{
    private User _user;
    private Container _container;
    private UserSchema _coreSchema;

    public UserIdQueryForeignKey(User user, Container container)
    {
        super("UserId", "DisplayName");
        _user = user;
        _container = container;
    }

    public TableInfo getLookupTableInfo()
    {
        if (_coreSchema == null)
        {
            _coreSchema = QueryService.get().getUserSchema(_user, _container, "core");
        }
        return _coreSchema.getTable("users", null);
    }
}