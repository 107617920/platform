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
package org.labkey.audit;

import org.jetbrains.annotations.Nullable;
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
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AbstractWriteableSettingsGroup;
import org.labkey.api.settings.WriteableAppProps;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class SiteSettingsAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String COLUMN_NAME_CHANGES = "Changes";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected DomainKind getDomainKind()
    {
        return new SiteSettingsAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return WriteableAppProps.AUDIT_EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Site Settings events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about modifications to the site settings.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        SiteSettingsAuditEvent bean = new SiteSettingsAuditEvent();
        copyStandardFields(bean, event);

        return (K)bean;
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event, @Nullable Map<String, Object> dataMap)
    {
        SiteSettingsAuditEvent bean = convertEvent(event);

        if (dataMap != null)
        {
            if (dataMap.containsKey(WriteableAppProps.AUDIT_PROP_DIFF))
                bean.setChanges(String.valueOf(dataMap.get(WriteableAppProps.AUDIT_PROP_DIFF)));
        }
        return (K)bean;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)SiteSettingsAuditEvent.class;
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
        DetailsURL url = DetailsURL.fromString("audit/showSiteSettingsAuditDetails.view?id=${rowId}");
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);

        return table;
    }

    public static class SiteSettingsAuditEvent extends AuditTypeEvent
    {
        private String _changes;

        public SiteSettingsAuditEvent()
        {
            super();
        }

        public SiteSettingsAuditEvent(String container, String comment)
        {
            super(WriteableAppProps.AUDIT_EVENT_TYPE, container, comment);
        }

        public String getChanges()
        {
            return _changes;
        }

        public void setChanges(String changes)
        {
            _changes = changes;
        }
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("Property", AbstractWriteableSettingsGroup.AUDIT_PROP_DIFF), COLUMN_NAME_CHANGES);
        return legacyNames;
    }

    public static class SiteSettingsAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SiteSettingsAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_CHANGES, JdbcType.VARCHAR));
        }

        public SiteSettingsAuditDomainKind()
        {
            super(WriteableAppProps.AUDIT_EVENT_TYPE);
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
