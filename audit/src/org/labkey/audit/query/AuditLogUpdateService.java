/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.ClientAPIAuditViewFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.Table;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/**
 * User: jeckels
 * Date: Mar 6, 2011
 */
public class AuditLogUpdateService extends AbstractQueryUpdateService
{
    public AuditLogUpdateService(AuditLogTable table)
    {
        super(table);
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        return Table.selectObject(getQueryTable(), keys.get("RowId"), Map.class);
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setContainerId(container.getId());
        event.setCreatedBy(user);
        if (row.get("EventType") != null)
        {
            String eventType = row.get("EventType").toString();
            if (!ClientAPIAuditViewFactory.EVENT_TYPE.equalsIgnoreCase(eventType))
            {
                throw new ValidationException("Only audit entries with EventType '" + ClientAPIAuditViewFactory.EVENT_TYPE + "' can be inserted.");
            }
        }
        event.setComment(getString(row, "Comment"));
        event.setKey1(getString(row, "Key1"));
        event.setKey2(getString(row, "Key2"));
        event.setKey3(getString(row, "Key3"));
        Integer intKey1 = getInteger(row, "IntKey1");
        if (intKey1 != null)
        {
            event.setIntKey1(intKey1.intValue());
        }
        Integer intKey2 = getInteger(row, "IntKey2");
        if (intKey2 != null)
        {
            event.setIntKey2(intKey2.intValue());
        }
        Integer intKey3 = getInteger(row, "IntKey3");
        if (intKey3 != null)
        {
            event.setIntKey3(intKey3.intValue());
        }
        event.setEventType(ClientAPIAuditViewFactory.EVENT_TYPE);
        event = AuditLogService.get().addEvent(event);

        try
        {
            return getRow(user, container, Collections.<String, Object>singletonMap("RowId", event.getRowId()));
        }
        catch (InvalidKeyException e)
        {
            throw new QueryUpdateServiceException(e);
        }
    }

    private String getString(Map<String, Object> row, String propertyName)
    {
        return row.get(propertyName) == null ? null : row.get(propertyName).toString();
    }

    private Integer getInteger(Map<String, Object> row, String propertyName) throws ValidationException
    {
        try
        {
            return (Integer)ConvertUtils.convert(getString(row, propertyName), Integer.class);
        }
        catch (ConversionException e)
        {
            throw new ValidationException("Invalid value for integer field '" + propertyName + "': " + getString(row, propertyName));
        }
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("Audit records aren't editable");
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException, ValidationException
    {
        throw new UnsupportedOperationException("Audit records aren't deleteable");
    }
}
