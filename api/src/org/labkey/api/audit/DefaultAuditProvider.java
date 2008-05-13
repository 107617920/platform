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

package org.labkey.api.audit;

import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.audit.query.DefaultAuditQueryView;
import org.labkey.api.audit.query.DefaultAuditSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.exp.Lsid;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
/**
 * A no-op implementation of an audit log service
 */
public class DefaultAuditProvider implements AuditLogService.I, AuditLogService.Replaceable
{
    public boolean isViewable()
    {
        return false;
    }

    public AuditLogEvent addEvent(AuditLogEvent event){return new AuditLogEvent();}

    public AuditLogEvent addEvent(ViewContext context, String eventType, String key, String message){return new AuditLogEvent();}
    public AuditLogEvent addEvent(ViewContext context, String eventType, String key1, String key2, String message){return new AuditLogEvent();}
    public AuditLogEvent addEvent(ViewContext context, String eventType, int key, String message){return new AuditLogEvent();}
    public AuditLogEvent addEvent(User user, Container c, String eventType, String key, String message){return new AuditLogEvent();}
    public AuditLogEvent addEvent(User user, Container c, String eventType, String key1, String key2, String message){return new AuditLogEvent();}
    public AuditLogEvent addEvent(User user, Container c, String eventType, int key, String message){return new AuditLogEvent();}

    public List<AuditLogEvent> getEvents(String eventType, String key)
    {
        return Collections.emptyList();
    }

    public List<AuditLogEvent> getEvents(String eventType, int key)
    {
        return Collections.emptyList();
    }

    public List<AuditLogEvent> getEvents(SimpleFilter filter)
    {
        return Collections.emptyList();
    }

    public AuditLogEvent getEvent(int rowId)
    {
        return new AuditLogEvent();
    }

    public AuditLogQueryView createQueryView(ViewContext context, SimpleFilter filter)
    {
        UserSchema schema = createSchema(context.getUser(), context.getContainer());
        QuerySettings settings = new QuerySettings(context.getActionURL(), getTableName());
        settings.setSchemaName(schema.getSchemaName());
        settings.setQueryName(getTableName());

        return new DefaultAuditQueryView(schema, settings, filter);
    }

    public AuditLogQueryView createQueryView(ViewContext context, SimpleFilter filter, String propertyURI)
    {
        return createQueryView(context, filter);
    }

    public String getTableName()
    {
        return "default";
    }

    public TableInfo getTable(ViewContext context, String name)
    {
        return null;
    }

    public UserSchema createSchema(User user, Container container)
    {
        return new DefaultAuditSchema(user, container);
    }

    public void addAuditViewFactory(AuditLogService.AuditViewFactory factory)
    {
    }

    public AuditLogService.AuditViewFactory getAuditViewFactory(String eventType)
    {
        return null;
    }

    public AuditLogService.AuditViewFactory[] getAuditViewFactories()
    {
        return new AuditLogService.AuditViewFactory[0];
    }

    public AuditLogEvent addEvent(AuditLogEvent event, Map<String, Object> dataMap, String domainURI)
    {
        return new AuditLogEvent();
    }

    public String getDomainURI(String eventType)
    {
        return new Lsid("AuditLogService", eventType).toString();
    }

    public String getPropertyURI(String eventType, String propertyName)
    {
        return new Lsid("AuditLogService", eventType).toString() + '#' + propertyName;
    }
}
