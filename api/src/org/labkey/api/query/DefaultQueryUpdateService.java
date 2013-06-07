/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.*;

/**
 * QueryUpdateService implementation that supports Query TableInfos that are backed by both a hard table and a Domain.
 * To update the Domain, a DomainUpdateHelper is required, otherwise the DefaultQueryUpdateService will only update the
 * hard table columns.
 *
 * User: Dave
 * Date: Jun 18, 2008
 * Time: 11:17:16 AM
 */
public class DefaultQueryUpdateService extends AbstractQueryUpdateService
{
    private TableInfo _dbTable = null;
    private DomainUpdateHelper _helper = null;
    /** Map from DbTable column names to QueryTable column names, if they have been aliased */
    private Map<String, String> _columnMapping = Collections.emptyMap();

    public DefaultQueryUpdateService(TableInfo queryTable, TableInfo dbTable)
    {
        super(queryTable);
        _dbTable = dbTable;
    }

    public DefaultQueryUpdateService(TableInfo queryTable, TableInfo dbTable, DomainUpdateHelper helper)
    {
        this(queryTable, dbTable);
        _helper = helper;
    }

    /** @param columnMapping Map from DbTable column names to QueryTable column names, if they have been aliased */
    public DefaultQueryUpdateService(TableInfo queryTable, TableInfo dbTable, Map<String, String> columnMapping)
    {
        this(queryTable, dbTable);
        _columnMapping = columnMapping;
    }

    protected TableInfo getDbTable()
    {
        return _dbTable;
    }

    protected Domain getDomain()
    {
        return _helper == null ? null : _helper.getDomain();
    }

    protected ColumnInfo getObjectUriColumn()
    {
        return _helper == null ? null : _helper.getObjectUriColumn();
    }

    protected String createObjectURI()
    {
        return _helper == null ? null : _helper.createObjectURI();
    }

    protected Iterable<PropertyColumn> getPropertyColumns()
    {
        return _helper == null ? Collections.<PropertyColumn>emptyList() : _helper.getPropertyColumns();
    }

    protected Container getDomainContainer(Container c)
    {
        return _helper == null ? c : _helper.getDomainContainer(c);
    }

    public interface DomainUpdateHelper
    {
        Domain getDomain();

        ColumnInfo getObjectUriColumn();

        String createObjectURI();

        // Could probably be just Iterable<PropertyDescriptor> or be removed and just get all PropertyDescriptors in the Domain.
        Iterable<PropertyColumn> getPropertyColumns();

        Container getDomainContainer(Container c);
    }

    public class ImportHelper implements OntologyManager.ImportHelper
    {
        ImportHelper()
        {
        }

        @Override
        public String beforeImportObject(Map<String, Object> map) throws SQLException
        {
            ColumnInfo objectUriCol = getObjectUriColumn();

            // Get existing Lsid
            String lsid = (String)map.get(objectUriCol.getName());
            if (lsid != null)
                return lsid;

            // Generate a new Lsid
            lsid = createObjectURI();
            map.put(objectUriCol.getName(), lsid);
            return lsid;
        }

        @Override
        public void afterBatchInsert(int currentRow) throws SQLException
        {
        }

        @Override
        public void updateStatistics(int currentRow) throws SQLException
        {
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        aliasColumns(keys);
        Map<String,Object> row = _select(container, getKeys(keys));

        //PostgreSQL includes a column named _row for the row index, but since this is selecting by
        //primary key, it will always be 1, which is not only unnecessary, but confusing, so strip it
        if (null != row)
        {
            if (row instanceof ArrayListMap)
                ((ArrayListMap)row).getFindMap().remove("_row");
            else
                row.remove("_row");
        }

        return row;
    }

    protected Map<String, Object> _select(Container container, Object[] keys) throws SQLException, ConversionException
    {
        TableInfo table = getDbTable();
        Object[] typedParameters = convertToTypedValues(keys, table.getPkColumns());

        Map<String, Object> row = new TableSelector(table).getObject(typedParameters, Map.class);

        ColumnInfo objectUriCol = getObjectUriColumn();
        Domain domain = getDomain();
        if (objectUriCol != null && domain != null && domain.getProperties().length > 0)
        {
            String lsid = (String)row.get(objectUriCol.getName());
            if (lsid != null)
            {
                Map<String, Object> propertyValues = OntologyManager.getProperties(getDomainContainer(container), lsid);
                if (propertyValues.size() > 0)
                {
                    // convert PropertyURI->value map into "Property name"->value map
                    Map<String, DomainProperty> propertyMap = domain.createImportMap(false);
                    for (Map.Entry<String, Object> entry : propertyValues.entrySet())
                    {
                        String propertyURI = entry.getKey();
                        DomainProperty dp = propertyMap.get(propertyURI);
                        PropertyDescriptor pd = dp != null ? dp.getPropertyDescriptor() : null;
                        if (pd != null)
                            row.put(pd.getName(), entry.getValue());
                    }
                }
            }
        }

        return row;
    }


    private Object[] convertToTypedValues(Object[] keys, List<ColumnInfo> cols)
    {
        Object[] typedParameters = new Object[keys.length];
        int t=0;
        for (int i=0 ; i<keys.length ; i++)
        {
            if (i >= cols.size() || keys[i] instanceof Parameter.TypedValue)
            {
                typedParameters[t++] = keys[i];
                continue;
            }
            Object v = keys[i];
            JdbcType type = cols.get(i).getJdbcType();
            if (v instanceof String)
                v = type.convert(v);
            Parameter.TypedValue tv = new Parameter.TypedValue(v,type);
            typedParameters[t++] = tv;
        }
        return typedParameters;
    }


/*
    @Override
    public List<Map<String, Object>> importRows(User user, Container container, DataIterator rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws SQLException
    {
        return super._importRowsUsingETL(user, container, rows, errors, extraScriptContext, true);
    }


    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        return super._insertRowsUsingETL(user, container, rows, errors, extraScriptContext);
    }
*/


    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        aliasColumns(row);
        convertTypes(row);
        setSpecialColumns(user, container, getDbTable(), row);
        return _insert(user, container, row);
    }

    /** Translate between the column name that query is exposing to the column name that actually lives in the database */
    private void aliasColumns(Map<String, Object> row)
    {
        for (Map.Entry<String, String> entry : _columnMapping.entrySet())
        {
            if (row.containsKey(entry.getValue()) && !row.containsKey(entry.getKey()))
            {
                row.put(entry.getKey(), row.get(entry.getValue()));
            }
        }
    }

    protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row)
            throws SQLException, ValidationException
    {
        ColumnInfo objectUriCol = getObjectUriColumn();
        Domain domain = getDomain();
        if (objectUriCol != null && domain != null && domain.getProperties().length > 0)
        {
            // convert "Property name"->value map into PropertyURI->value map
            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            Map<String, Object> values = new HashMap<String, Object>();
            for (PropertyColumn pc : getPropertyColumns())
            {
                PropertyDescriptor pd = pc.getPropertyDescriptor();
                pds.add(pd);
                Object value = getPropertyValue(row, pd);
                values.put(pd.getPropertyURI(), value);
            }

            PropertyDescriptor[] properties = pds.toArray(new PropertyDescriptor[pds.size()]);
            List<String> lsids = OntologyManager.insertTabDelimited(c, user, null, new ImportHelper(), properties, Collections.singletonList(values), true);
            String lsid = lsids.get(0);

            // Add the new lsid to the row map.
            row.put(objectUriCol.getName(), lsid);
        }

        return Table.insert(user, getDbTable(), row);
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Map<String,Object> rowStripped = new CaseInsensitiveHashMap<Object>(row.size());

        // Flip the key/value pairs around for easy lookup
        Map<String, String> queryToDb = new CaseInsensitiveHashMap<String>();
        for (Map.Entry<String, String> entry : _columnMapping.entrySet())
        {
            queryToDb.put(entry.getValue(), entry.getKey());
        }

        for (ColumnInfo col : getQueryTable().getColumns())
        {
            String name = col.getName();
            if (!row.containsKey(name))
                continue;

            // Skip readonly and wrapped columns.  The wrapped column is usually a pk column and can't be updated.
            if (col.isReadOnly() || col instanceof ExprColumn)
                continue;

            //when updating a row, we should strip the following fields, as they are
            //automagically maintained by the table layer, and should not be allowed
            //to change once the record exists.
            //unfortunately, the Table.update() method doesn't strip these, so we'll
            //do that here.
            // Owner, CreatedBy, Created, EntityId
            if (name.equalsIgnoreCase("Owner") ||
                    name.equalsIgnoreCase("CreatedBy") ||
                    name.equalsIgnoreCase("Created") ||
                    name.equalsIgnoreCase("EntityId"))
                continue;

            // We want a map using the DbTable column names as keys, so figure out the right name to use
            String dbName = queryToDb.containsKey(name) ? queryToDb.get(name) : name;
            rowStripped.put(dbName, row.get(name));
        }

        convertTypes(rowStripped);
        setSpecialColumns(user, container, getDbTable(), row);

        Object rowContainer = row.get("container");
        if (rowContainer != null)
        {
            if (oldRow == null)
                throw new UnauthorizedException("The existing row was not found");

            Object oldContainer = new CaseInsensitiveHashMap(oldRow).get("container");
            if (null != oldContainer && !rowContainer.equals(oldContainer))
                throw new UnauthorizedException("The row is from the wrong container.");
        }

        Map<String,Object> updatedRow = _update(user, container, rowStripped, oldRow, oldRow == null ? getKeys(row) : getKeys(oldRow));

        //when passing a map for the row, the Table layer returns the map of fields it updated, which excludes
        //the primary key columns as well as those marked read-only. So we can't simply return the map returned
        //from Table.update(). Instead, we need to copy values from updatedRow into row and return that.
        row.putAll(updatedRow);
        return row;
    }

    protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys)
            throws SQLException, ValidationException
    {
        ColumnInfo objectUriCol = getObjectUriColumn();
        Domain domain = getDomain();
        if (objectUriCol != null && domain != null && domain.getProperties().length > 0)
        {
            String lsid = (String)oldRow.get(objectUriCol.getName());

            // convert "Property name"->value map into PropertyURI->value map
            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            Map<String, Object> newValues = new HashMap<String, Object>();
            for (PropertyColumn pc : getPropertyColumns())
            {
                PropertyDescriptor pd = pc.getPropertyDescriptor();
                pds.add(pd);

                if (lsid != null && hasProperty(oldRow, pd))
                    OntologyManager.deleteProperty(lsid, pd.getPropertyURI(), getDomainContainer(c), getDomainContainer(c));

                Object value = getPropertyValue(row, pd);
                if (value != null)
                    newValues.put(pd.getPropertyURI(), value);
            }

            // Note: copy lsid into newValues map so it will be found by the ImportHelper.beforeImportObject()
            newValues.put(objectUriCol.getName(), lsid);
            PropertyDescriptor[] properties = pds.toArray(new PropertyDescriptor[pds.size()]);
            List<String> lsids = OntologyManager.insertTabDelimited(getDomainContainer(c), user, null, new ImportHelper(), properties, Collections.singletonList(newValues), true);

            // Update the lsid in the row: the lsid may have not existed in the row before the update.
            lsid = lsids.get(0);
            row.put(objectUriCol.getName(), lsid);
        }

        return Table.update(user, getDbTable(), row, keys);
    }

    // Get value from row map where the keys are column names.
    private Object getPropertyValue(Map<String, Object> row, PropertyDescriptor pd)
    {
        if (row.containsKey(pd.getName()))
            return row.get(pd.getName());

        if (row.containsKey(pd.getLabel()))
            return row.get(pd.getLabel());

        Set<String> aliases = pd.getImportAliasSet();
        if (aliases != null && aliases.size() > 0)
        {
            for (String alias : aliases)
            {
                if (row.containsKey(alias))
                    return row.get(alias);
            }
        }

        return null;
    }

    // Checks a value exists in the row map (value may be null)
    private boolean hasProperty(Map<String, Object> row, PropertyDescriptor pd)
    {
        if (row.containsKey(pd.getName()))
            return true;

        if (row.containsKey(pd.getLabel()))
            return true;

        Set<String> aliases = pd.getImportAliasSet();
        if (aliases != null && aliases.size() > 0)
        {
            for (String alias : aliases)
            {
                if (row.containsKey(alias))
                    return true;
            }
        }

        return false;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        if (oldRowMap == null)
            return null;

        aliasColumns(oldRowMap);

        if (container != null && getDbTable().getColumn("container") != null)
        {
            // UNDONE: 9077: check container permission on each row before delete
            Object oldContainer = new CaseInsensitiveHashMap(oldRowMap).get("container");
            if (null != oldContainer && !container.getId().equals(oldContainer))
            {
                //Issue 15301: allow workbooks records to be deleted/updated from the parent container
                Container rowContainer = ContainerManager.getForId((String)oldContainer);
                if (rowContainer != null && rowContainer.isWorkbook() && rowContainer.getParent().equals(container))
                    container = rowContainer;
                else
                    throw new UnauthorizedException("The row is from the wrong container.");
            }
        }

        _delete(container, oldRowMap);
        return oldRowMap;
    }

    protected void _delete(Container c, Map<String, Object> row) throws SQLException, InvalidKeyException
    {
        ColumnInfo objectUriCol = getObjectUriColumn();
        if (objectUriCol != null)
        {
            String lsid = (String)row.get(objectUriCol.getName());
            if (lsid != null)
            {
                OntologyObject oo = OntologyManager.getOntologyObject(c, lsid);
                if (oo != null)
                    OntologyManager.deleteProperties(c, oo.getObjectId());
            }
        }

        Table.delete(getDbTable(), getKeys(row));
    }

    protected Object[] getKeys(Map<String, Object> map) throws InvalidKeyException
    {
        //build an array of pk values based on the table info
        TableInfo table = getDbTable();
        List<ColumnInfo> pks = table.getPkColumns();
        Object[] pkVals = new Object[pks.size()];

        if (map == null || map.isEmpty())
            return pkVals;

        for (int idx = 0; idx < pks.size(); ++idx)
        {
            ColumnInfo pk = pks.get(idx);
            Object pkValue = map.get(pk.getName());
            // Check the type and coerce if needed
            if (pkValue != null && !pk.getJavaObjectClass().isInstance(pkValue))
            {
                try
                {
                    pkValue = ConvertUtils.convert(pkValue.toString(), pk.getJavaObjectClass());
                }
                catch (ConversionException ignored) { /* Maybe the database can do the conversion */ }
            }
            pkVals[idx] = pkValue;
            if (null == pkVals[idx])
                throw new InvalidKeyException("Value for key field '" + pk.getName() + "' was null or not supplied!", map);
        }
        return pkVals;
    }


    protected void convertTypes(Map<String,Object> row) throws ValidationException
    {
        for (ColumnInfo col : getDbTable().getColumns())
        {
            Object value = row.get(col.getName());
            if (null != value)
            {
                // Issue 13951: PSQLException from org.labkey.api.query.DefaultQueryUpdateService._update()
                // improve handling of conversion errors
                try
                {
                    switch (col.getSqlTypeInt())
                    {
                        case java.sql.Types.DATE:
                        case java.sql.Types.TIME:
                        case java.sql.Types.TIMESTAMP:
                            row.put(col.getName(), value instanceof Date ? value : ConvertUtils.convert(value.toString(), Date.class));
                            break;
                        default:
                            row.put(col.getName(), ConvertUtils.convert(value.toString(), col.getJdbcType().getJavaClass()));
                    }
                }
                catch (ConversionException e)
                {
                    String type = ColumnInfo.getFriendlyTypeName(col.getJdbcType().getJavaClass());
                    throw new ValidationException("Unable to convert value \'" + value.toString() + "\' to " + type, col.getName());
                }
            }
        }
    }


    /**
     * Override this method to alter the row before insert or update.
     * For example, you can automatically adjust certain column values based on context.
     * @param user The current user
     * @param container The current container
     * @param table The table to be updated
     * @param row The row data
     */
    protected void setSpecialColumns(User user, Container container, TableInfo table, Map<String,Object> row)
    {
        if (null != container)
        {
            //Issue 15301: allow workbooks records to be deleted/updated from the parent container
            if (row.get("container") != null)
            {
                Container rowContainer = ContainerManager.getForId((String)row.get("container"));
                if (rowContainer != null && rowContainer.isWorkbook() && rowContainer.getParent().equals(container))
                {
                    return;
                }

            }
            row.put("container", container.getId());
        }
    }
}
