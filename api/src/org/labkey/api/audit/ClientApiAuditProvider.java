/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.audit;

import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class ClientApiAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "Client API Actions";

    public static final String COLUMN_NAME_SUBTYPE = "SubType";
    public static final String COLUMN_NAME_STRING1 = "String1";
    public static final String COLUMN_NAME_STRING2 = "String2";
    public static final String COLUMN_NAME_STRING3 = "String3";
    public static final String COLUMN_NAME_INT1 = "Int1";
    public static final String COLUMN_NAME_INT2 = "Int2";
    public static final String COLUMN_NAME_INT3 = "Int3";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SUBTYPE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_STRING1));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_STRING2));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_STRING3));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_INT1));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_INT2));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_INT3));
    }


    @Override
    protected DomainKind getDomainKind()
    {
        return new ClientApiAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getDescription()
    {
        return "Information about audit events created through the client API.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        ClientApiAuditEvent bean = new ClientApiAuditEvent();
        copyStandardFields(bean, event);

        // 'key1' mapped to 'subtype' and other 'keyN' are mapped to 'stringN-1'
        bean.setSubType(event.getKey1());
        bean.setString1(event.getKey2());
        bean.setString2(event.getKey3());
        bean.setInt1(event.getIntKey1());
        bean.setInt2(event.getIntKey2());
        bean.setInt3(event.getIntKey3());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();

        // 'key1' mapped to 'subtype' and other 'keyN' are mapped to 'stringN-1'
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_SUBTYPE);
        legacyNames.put(FieldKey.fromParts("key2"), COLUMN_NAME_STRING1);
        legacyNames.put(FieldKey.fromParts("key3"), COLUMN_NAME_STRING2);
        legacyNames.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_INT1);
        legacyNames.put(FieldKey.fromParts("intKey2"), COLUMN_NAME_INT2);
        legacyNames.put(FieldKey.fromParts("intKey3"), COLUMN_NAME_INT3);
        return legacyNames;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, domain, dbSchema, userSchema)
        {
            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return defaultVisibleColumns;
            }

            @Override
            public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
            {
                // Don't allow deletes or updates for audit events, and don't let guests insert.
                // AuditQueryView disables the insert and import buttons in the html UI, but
                // this permission check allows the LABKEY.Query.insertRows() api to still work.
                return ((perm.equals(InsertPermission.class) && !isGuest(user)) || perm.equals(ReadPermission.class)) &&
                        getContainer().hasPermission(user, perm);
            }

            private boolean isGuest(UserPrincipal user)
            {
                return user instanceof User && user.isGuest();
            }
        };

        return table;
    }


    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)ClientApiAuditEvent.class;
    }

    public static class ClientApiAuditEvent extends AuditTypeEvent
    {
        private String _subType;
        private String _string1;
        private String _string2;
        private String _string3;
        private int _int1;
        private int _int2;
        private int _int3;

        public ClientApiAuditEvent()
        {
            super();
        }

        public ClientApiAuditEvent(String container, String comment)
        {
            super(EVENT_TYPE, container, comment);
        }

        public String getSubType()
        {
            return _subType;
        }

        public void setSubType(String subType)
        {
            _subType = subType;
        }

        public String getString1()
        {
            return _string1;
        }

        public void setString1(String string1)
        {
            _string1 = string1;
        }

        public String getString2()
        {
            return _string2;
        }

        public void setString2(String string2)
        {
            _string2 = string2;
        }

        public String getString3()
        {
            return _string3;
        }

        public void setString3(String string3)
        {
            _string3 = string3;
        }

        public int getInt1()
        {
            return _int1;
        }

        public void setInt1(int int1)
        {
            _int1 = int1;
        }

        public int getInt2()
        {
            return _int2;
        }

        public void setInt2(int int2)
        {
            _int2 = int2;
        }

        public int getInt3()
        {
            return _int3;
        }

        public void setInt3(int int3)
        {
            _int3 = int3;
        }
    }

    public static class ClientApiAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ClientApiAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_SUBTYPE, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_STRING1, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_STRING2, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_STRING3, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_INT1, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_INT2, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_INT3, JdbcType.INTEGER));
        }

        public ClientApiAuditDomainKind()
        {
            super(EVENT_TYPE);
        }

        @Override
        protected Set<PropertyStorageSpec> getColumns()
        {
            return _fields;
        }

        @Override
        public Set<Index> getPropertyIndices()
        {
            return PageFlowUtil.set(new Index(false, COLUMN_NAME_SUBTYPE));
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
