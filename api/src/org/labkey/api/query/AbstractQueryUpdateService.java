/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.*;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.*;

/**
 * User: jeckels
 * Date: Apr 23, 2010
 */
public abstract class AbstractQueryUpdateService implements QueryUpdateService
{
    private TableInfo _queryTable = null;
    private boolean _bulkLoad = false;

    protected AbstractQueryUpdateService(TableInfo queryTable)
    {
        if (queryTable == null)
            throw new IllegalArgumentException();
        _queryTable = queryTable;
    }

    protected TableInfo getQueryTable()
    {
        return _queryTable;
    }

    protected boolean hasPermission(User user, Class<? extends Permission> acl)
    {
        return getQueryTable().hasPermission(user, acl);
    }

    protected abstract Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, ReadPermission.class) && !user.equals(User.getSearchUser()))
            throw new UnauthorizedException("You do not have permission to read data from this table.");

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> rowKeys : keys)
        {
            Map<String, Object> row = getRow(user, container, rowKeys);
            if (row != null)
                result.add(row);
        }
        return result;
    }

    protected abstract Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
        throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        BatchValidationException errors = new BatchValidationException();
        errors.setExtraContext(extraScriptContext);
        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.INSERT, true, errors, extraScriptContext);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            try
            {
                row = coerceTypes(row);
                getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.INSERT, true, i, row, null, extraScriptContext);
                row = insertRow(user, container, row);
                if (row == null)
                    continue;

                getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.INSERT, false, i, row, null, extraScriptContext);
                result.add(row);
            }
            catch (ValidationException vex)
            {
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i));
            }
        }

        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.INSERT, false, errors, extraScriptContext);

        return result;
    }

    /** Attempt to make the passed in types match the expected types so the script doesn't have to do the conversion */
    protected Map<String, Object> coerceTypes(Map<String, Object> row)
    {
        Map<String, Object> result = new CaseInsensitiveHashMap<Object>(row.size());
        Map<String, ColumnInfo> columnMap = ImportAliasable.Helper.createImportMap(_queryTable.getColumns(), true);
        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            ColumnInfo col = columnMap.get(entry.getKey());

            Object value = entry.getValue();
            if (col != null && value != null && !col.getJavaObjectClass().isInstance(value))
            {
                try
                {
                    value = ConvertUtils.convert(value.toString(), col.getJavaObjectClass());
                }
                catch (ConversionException e)
                {
                    // That's OK, the transformation script may be able to fix up the value before it gets inserted
                }
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }

    protected abstract Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, UpdatePermission.class))
            throw new UnauthorizedException("You do not have permission to update data in this table.");

        if (oldKeys != null && rows.size() != oldKeys.size())
            throw new IllegalArgumentException("rows and oldKeys are required to be the same length, but were " + rows.size() + " and " + oldKeys + " in length, respectively");

        BatchValidationException errors = new BatchValidationException();
        errors.setExtraContext(extraScriptContext);
        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.UPDATE, true, errors, extraScriptContext);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            row = coerceTypes(row);
            try
            {
                Map<String, Object> oldKey = oldKeys == null ? row : oldKeys.get(i);
                Map<String, Object> oldRow = getRow(user, container, oldKey);
                if (oldRow == null)
                    throw new NotFoundException("The existing row was not found.");

                getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.UPDATE, true, i, row, oldRow, extraScriptContext);
                Map<String, Object> updatedRow = updateRow(user, container, row, oldRow);
                if (updatedRow == null)
                    continue;

                getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.UPDATE, false, i, updatedRow, oldRow, extraScriptContext);
                result.add(updatedRow);
            }
            catch (ValidationException vex)
            {
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i));
            }
        }

        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.UPDATE, false, errors, extraScriptContext);

        return result;
    }

    protected abstract Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException;
    
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, DeletePermission.class))
            throw new UnauthorizedException("You do not have permission to delete data from this table.");

        BatchValidationException errors = new BatchValidationException();
        errors.setExtraContext(extraScriptContext);
        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.DELETE, true, errors, extraScriptContext);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(keys.size());
        for (int i = 0; i < keys.size(); i++)
        {
            Map<String, Object> key = keys.get(i);
            try
            {
                Map<String, Object> oldRow = getRow(user, container, key);
                // if row doesn't exist, bail early
                if (oldRow == null)
                    continue;

                getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.DELETE, true, i, null, oldRow, extraScriptContext);
                Map<String, Object> updatedRow = deleteRow(user, container, oldRow);
                if (updatedRow == null)
                    continue;

                getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.DELETE, false, i, null, updatedRow, extraScriptContext);
                result.add(updatedRow);
            }
            catch (ValidationException vex)
            {
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), key, i));
            }
        }

        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.DELETE, false, errors, extraScriptContext);

        return result;
    }

    public void setBulkLoad(boolean bulkLoad)
    {
        _bulkLoad = bulkLoad;
    }

    public boolean isBulkLoad()
    {
        return _bulkLoad;
    }
}
