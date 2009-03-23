/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.audit.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.audit.AuditSchema;
import org.labkey.audit.model.LogManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
public class AuditQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "auditLog";
    public static final String AUDIT_TABLE_NAME = "audit";

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new AuditQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public AuditQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, user, container, AuditSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        return new HashSet<String>(Arrays.asList(AUDIT_TABLE_NAME));
    }

    public TableInfo createTable(String name)
    {
        return new AuditLogTable(this, LogManager.get().getTinfoAuditLog(), name);
    }
}
