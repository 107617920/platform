/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.etl.ListofMapsDataIterator;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.MapDataIterator;
import org.labkey.api.etl.Pump;
import org.labkey.api.etl.StandardETL;
import org.labkey.api.etl.TriggerDataBuilderHelper;
import org.labkey.api.etl.WrapperDataIterator;
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
    private CaseInsensitiveHashMap<ColumnInfo> _columnImportMap = null;

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
        if (!hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException("You do not have permission to read data from this table.");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> rowKeys : keys)
        {
            Map<String, Object> row = getRow(user, container, rowKeys);
            if (row != null)
                result.add(row);
        }
        return result;
    }


    protected DataIteratorContext getDataIteratorContext(BatchValidationException errors, InsertOption forImport)
    {
        if (null == errors)
            errors = new BatchValidationException();
        DataIteratorContext context = new DataIteratorContext(errors);
        context.setInsertOption(forImport);
        return context;
    }


    /*
     * construct the core ETL tranformation pipeline for this table, may be just StandardETL.
     * does NOT handle triggers or the insert/update iterator.
     */
    public DataIteratorBuilder createImportETL(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
        StandardETL etl = StandardETL.forInsert(getQueryTable(), data, container, user, context);
        return ((UpdateableTableInfo)getQueryTable()).persistRows(etl, context);
    }


    /**
     * Implementation to use insertRows() while we migrate to using ETL for all code paths
     *
     * DataIterator should/must use same error collection as passed in
     */
    @Deprecated
    protected int _importRowsUsingInsertRows(User user, Container container, DataIterator rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws SQLException
    {
        MapDataIterator mapIterator = DataIteratorUtil.wrapMap(rows, true);
        List<Map<String, Object>> list = new ArrayList<>();
        List<Map<String, Object>> ret;
        Exception rowException;

        try
        {
            while (mapIterator.next())
                list.add(mapIterator.getMap());
            ret = insertRows(user, container, list, errors, extraScriptContext);
            if (errors.hasErrors())
                return 0;
            return ret.size();
        }
        catch (BatchValidationException x)
        {
            assert x == errors;
            assert x.hasErrors();
            return 0;
        }
        catch (QueryUpdateServiceException x)
        {
            rowException = x;
        }
        catch (DuplicateKeyException x)
        {
            rowException = x;
        }
        errors.addRowError(new ValidationException(rowException.getMessage()));
        return 0;
    }


    protected int _importRowsUsingETL(User user, Container container, DataIteratorBuilder in, @Nullable final ArrayList<Map<String, Object>> outputRows, DataIteratorContext context, Map<String, Object> extraScriptContext)
            throws SQLException
    {
        if (!hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        context.getErrors().setExtraContext(extraScriptContext);

        boolean hasTableScript = hasTableScript(container);
        TriggerDataBuilderHelper helper = new TriggerDataBuilderHelper(getQueryTable(), container, extraScriptContext, context.getInsertOption().useImportAliases);
        if (hasTableScript)
            in = helper.before(in);
        DataIteratorBuilder importETL = createImportETL(user, container, in, context);
        DataIteratorBuilder out = importETL;
        if (hasTableScript)
            out = helper.after(importETL);

        if (hasTableScript)
        {
            context.setFailFast(false);
            context.setMaxRowErrors(Math.max(context.getMaxRowErrors(),1000));
        }
        int count = _pump(out, outputRows, context);
        return context.getErrors().hasErrors() ? 0 : count;
    }


    /** this is extracted so subclasses can add wrap */
    protected int _pump(DataIteratorBuilder etl, final @Nullable ArrayList<Map<String, Object>> rows,  DataIteratorContext context)
    {
        DataIterator it = etl.getDataIterator(context);

        try
        {
            if (null != rows)
            {
                MapDataIterator maps = DataIteratorUtil.wrapMap(etl.getDataIterator(context), false);
                it = new WrapperDataIterator(maps)
                {
                    @Override
                    public boolean next() throws BatchValidationException
                    {
                        boolean ret = super.next();
                        if (ret)
                            rows.add(((MapDataIterator)_delegate).getMap());
                        return ret;
                    }
                };
            }

            Pump pump = new Pump(it, context);
            pump.run();

            return pump.getRowCount();
        }
        finally
        {
            DataIteratorUtil.closeQuietly(it);
        }
    }


    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<String, Object> extraScriptContext)
            throws SQLException
    {
        return _importRowsUsingInsertRows(user,container,rows.getDataIterator(new DataIteratorContext(errors)),errors,extraScriptContext);
    }


    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws SQLException
    {
        throw new UnsupportedOperationException("merge is not supported for all tables");
    }

    @Override
    public void truncateRows(User user, Container container, Map<String, Object> extraScriptContext)
            throws BatchValidationException,  QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("truncate is not supported for all tables");
    }

    private boolean hasTableScript(Container container)
    {
        return getQueryTable().hasTriggers(container);
    }


    protected abstract Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
        throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException;


    protected @Nullable List<Map<String, Object>> _insertRowsUsingETL(User user, Container container, List<Map<String, Object>> rows,
          DataIteratorContext context, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        DataIterator di = _toDataIterator(getClass().getSimpleName() + ".insertRows()", rows);
        DataIteratorBuilder dib = new DataIteratorBuilder.Wrapper(di);
        ArrayList<Map<String,Object>> outputRows = new ArrayList<>();
        int count = _importRowsUsingETL(user, container, dib, outputRows, context, extraScriptContext);

        if (context.getErrors().hasErrors())
            return null;

        QueryService.get().addAuditEvent(user, container, getQueryTable(), QueryService.AuditAction.INSERT, outputRows);

        return outputRows;
    }


    private DataIterator _toDataIterator(String debugName, List<Map<String, Object>> rows)
    {
        // TODO probably can't assume all rows have all columsn
        // TODO can we assume that all rows refer to columns consistently? (not PTID and MouseId for the same column)
        // TODO optimize ArrayListMap?
        Set<String> colNames;

        if (rows.size() > 0 && rows.get(0) instanceof ArrayListMap)
        {
            colNames = ((ArrayListMap)rows.get(0)).getFindMap().keySet();
        }
        else
        {
            colNames = new CaseInsensitiveHashSet();
            for (Map<String,Object> row : rows)
                colNames.addAll(row.keySet());
        }

        ListofMapsDataIterator maps = new ListofMapsDataIterator(colNames, rows);
        maps.setDebugName(debugName);
        return LoggingDataIterator.wrap(maps);
    }


    /** @deprecated switch to using ETL based method */
    @Deprecated
    protected List<Map<String, Object>> _insertRowsUsingInsertRow(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        boolean hasTableScript = hasTableScript(container);

        errors.setExtraContext(extraScriptContext);
        if (hasTableScript)
            getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.INSERT, true, errors, extraScriptContext);

        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            row = normalizeColumnNames(row);
            try
            {
                row = coerceTypes(row);
                if (hasTableScript)
                {
                    getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.INSERT, true, i, row, null, extraScriptContext);
                }
                row = insertRow(user, container, row);
                if (row == null)
                    continue;

                if (hasTableScript)
                    getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.INSERT, false, i, row, null, extraScriptContext);
                result.add(row);
            }
            catch (SQLException sqlx)
            {
                if (StringUtils.startsWith(sqlx.getSQLState(), "22") || SqlDialect.isConstraintException(sqlx))
                {
                    ValidationException vex = new ValidationException(sqlx.getMessage());
                    vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i+1);
                    errors.addRowError(vex);
                }
                else if (SqlDialect.isTransactionException(sqlx) && errors.hasErrors())
                {
                    // if we already have some errors, just break
                    break;
                }
                else
                {
                    throw sqlx;
                }
            }
            catch (ValidationException vex)
            {
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i));
            }
        }

        if (hasTableScript)
            getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.INSERT, false, errors, extraScriptContext);

        if (!isBulkLoad())
            QueryService.get().addAuditEvent(user, container, getQueryTable(), QueryService.AuditAction.INSERT, result);

        return result;
    }

    private Map<String, Object> normalizeColumnNames(Map<String, Object> row) throws QueryUpdateServiceException
    {
        if(_columnImportMap == null)
        {
            _columnImportMap = (CaseInsensitiveHashMap<ColumnInfo>)ImportAliasable.Helper.createImportMap(getQueryTable().getColumns(), false);
        }

        Map<String, Object> newRow = new CaseInsensitiveHashMap<>();
        CaseInsensitiveHashSet columns = new CaseInsensitiveHashSet();
        columns.addAll(row.keySet());

        String newName;
        for(String key : row.keySet())
        {
            if(_columnImportMap.containsKey(key))
            {
                //it is possible for a normalized name to conflict with an existing property.  if so, defer to the original
                newName = _columnImportMap.get(key).getName();
                if(!columns.contains(newName)){
                    newRow.put(newName, row.get(key));
                    continue;
                }
            }
            newRow.put(key, row.get(key));
        }

        return newRow;
    }

    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        try
        {
            List<Map<String,Object>> ret = _insertRowsUsingInsertRow(user, container, rows, errors, extraScriptContext);
            if (errors.hasErrors())
                return null;
            return ret;
        }
        catch (BatchValidationException x)
        {
            assert x == errors;
            assert x.hasErrors();
        }
        return null;
    }


    /** Attempt to make the passed in types match the expected types so the script doesn't have to do the conversion */
    @Deprecated
    protected Map<String, Object> coerceTypes(Map<String, Object> row)
    {
        Map<String, Object> result = new CaseInsensitiveHashMap<>(row.size());
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

        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        List<Map<String, Object>> oldRows = new ArrayList<>(rows.size());

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
                oldRows.add(oldRow);
            }
            catch (ValidationException vex)
            {
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i));
            }
        }

        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.UPDATE, false, errors, extraScriptContext);

        if (!isBulkLoad())
            QueryService.get().addAuditEvent(user, container, getQueryTable(), QueryService.AuditAction.UPDATE, oldRows, result);

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

        List<Map<String, Object>> result = new ArrayList<>(keys.size());
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
            catch (InvalidKeyException ex)
            {
                ValidationException vex = new ValidationException(ex.getMessage());
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), key, i));
            }
            catch (ValidationException vex)
            {
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), key, i));
            }
        }

        getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.DELETE, false, errors, extraScriptContext);

        if (!isBulkLoad())
            QueryService.get().addAuditEvent(user, container, getQueryTable(), QueryService.AuditAction.DELETE, result);

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
