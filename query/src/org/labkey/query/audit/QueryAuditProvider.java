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
package org.labkey.query.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.query.controllers.QueryController.QueryExportAuditRedirectAction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 *
 * UNDONE: Fancy QueryAuditViewFactory.QueryDetailsColumn
 */
public class QueryAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_AUDIT_EVENT = "QueryExportAuditEvent";
    public static final String COLUMN_NAME_SCHEMA_NAME = "SchemaName";
    public static final String COLUMN_NAME_QUERY_NAME = "QueryName";
    public static final String COLUMN_NAME_DETAILS_URL = "DetailsUrl";
    public static final String COLUMN_NAME_DATA_ROW_COUNT = "DataRowCount";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SCHEMA_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_QUERY_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_DATA_ROW_COUNT));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected DomainKind getDomainKind()
    {
        return new QueryAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return QUERY_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Query events";
    }

    @Override
    public String getDescription()
    {
        return "Query events";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        QueryAuditEvent bean = new QueryAuditEvent();
        copyStandardFields(bean, event);

        bean.setSchemaName(event.getKey1());
        bean.setQueryName(event.getKey2());
        bean.setDetailsUrl(event.getKey3());

        if (event.getIntKey1() != null)
            bean.setDataRowCount(event.getIntKey1());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("key1"), COLUMN_NAME_SCHEMA_NAME);
        legacyMap.put(FieldKey.fromParts("key2"), COLUMN_NAME_QUERY_NAME);
        legacyMap.put(FieldKey.fromParts("key3"), COLUMN_NAME_DETAILS_URL);
        legacyMap.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_DATA_ROW_COUNT);
        return legacyMap;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)QueryAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, domain, dbSchema, userSchema)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_DATA_ROW_COUNT.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Data Row Count");
                }
                else if (COLUMN_NAME_SCHEMA_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Schema Name");
                }
                else if (COLUMN_NAME_QUERY_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Query Name");
                }
            }

            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return defaultVisibleColumns;
            }
        };

        // Query details redirect action
        DetailsURL url = new DetailsURL(new ActionURL(QueryExportAuditRedirectAction.class, null), "rowId", FieldKey.fromParts(COLUMN_NAME_ROW_ID));
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);

        return table;
    }

    public static class QueryAuditEvent extends AuditTypeEvent
    {
        private String _schemaName;
        private String _queryName;
        private String _detailsUrl;
        private int _dataRowCount;

        public QueryAuditEvent()
        {
            super();
        }

        public QueryAuditEvent(String container, String comment)
        {
            super(QUERY_AUDIT_EVENT, container, comment);
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public String getDetailsUrl()
        {
            return _detailsUrl;
        }

        public void setDetailsUrl(String detailsUrl)
        {
            _detailsUrl = detailsUrl;
        }

        public int getDataRowCount()
        {
            return _dataRowCount;
        }

        public void setDataRowCount(int dataRowCount)
        {
            _dataRowCount = dataRowCount;
        }
    }

    public static class QueryAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "QueryAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_SCHEMA_NAME, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_QUERY_NAME, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_DETAILS_URL, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_DATA_ROW_COUNT, JdbcType.INTEGER));
        }

        public QueryAuditDomainKind()
        {
            super(QUERY_AUDIT_EVENT);
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
