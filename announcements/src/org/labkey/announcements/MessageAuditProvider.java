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
package org.labkey.announcements;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.MailHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class MessageAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String COLUMN_NAME_FROM = "From";
    public static final String COLUMN_NAME_TO = "To";
    public static final String COLUMN_NAME_CONTENT_TYPE = "ContentType";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_FROM));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TO));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected DomainKind getDomainKind()
    {
        return new MessageAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return MailHelper.MESSAGE_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Message events";
    }

    @Override
    public String getDescription()
    {
        return "Message events";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        MessageAuditEvent bean = new MessageAuditEvent();
        copyStandardFields(bean, event);

        bean.setFrom(event.getKey1());
        bean.setTo(event.getKey2());
        bean.setContentType(event.getKey3());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_FROM);
        legacyNames.put(FieldKey.fromParts("key2"), COLUMN_NAME_TO);
        legacyNames.put(FieldKey.fromParts("key3"), COLUMN_NAME_CONTENT_TYPE);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)MessageAuditEvent.class;
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
        };

        return table;
    }

    public static class MessageAuditEvent extends AuditTypeEvent
    {
        private String _from;
        private String _to;
        private String _contentType;

        public MessageAuditEvent()
        {
            super();
        }

        public MessageAuditEvent(String container, String comment)
        {
            super(MailHelper.MESSAGE_AUDIT_EVENT, container, comment);
        }

        public String getFrom()
        {
            return _from;
        }

        public void setFrom(String from)
        {
            _from = from;
        }

        public String getTo()
        {
            return _to;
        }

        public void setTo(String to)
        {
            _to = to;
        }

        public String getContentType()
        {
            return _contentType;
        }

        public void setContentType(String contentType)
        {
            _contentType = contentType;
        }
    }

    public static class MessageAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "MessageAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("From", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("To", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("ContentType", JdbcType.VARCHAR));
        }

        public MessageAuditDomainKind()
        {
            super(MailHelper.MESSAGE_AUDIT_EVENT);
        }

        @Override
        protected Set<PropertyStorageSpec> getColumns()
        {
            return _fields;
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
