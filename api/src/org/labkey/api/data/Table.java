/*
 * Copyright (c) 2004-2013 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.Join;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.etl.AbstractDataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.Pump;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.etl.TableInsertDataIterator;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;

import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Table manipulation methods
 * <p/>
 * select/insert/update/delete
 */

public class Table
{
    public static final String SQLSTATE_TRANSACTION_STATE = "25000";
    public static final int ERROR_ROWVERSION = 10001;
    public static final int ERROR_DELETED = 10002;
    public static final int ERROR_TABLEDELETED = 10003;

    private static final Logger _log = Logger.getLogger(Table.class);

    // Return all rows instead of limiting to the top n
    public static final int ALL_ROWS = -1;
    // Return no rows -- useful for query validation or when you need just metadata
    public static final int NO_ROWS = 0;

    // Makes long parameter lists easier to read
    public static final int NO_OFFSET = 0;

    private Table()
    {
    }

    public static boolean validMaxRows(int maxRows)
    {
        return NO_ROWS == maxRows | ALL_ROWS == maxRows | maxRows > 0;
    }

    // ================== These methods & members are no longer used by LabKey code ==================

    public static final Set<String> ALL_COLUMNS = TableSelector.ALL_COLUMNS;

    @Deprecated /** Use TableSelector */
    public static <K> K selectObject(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss) throws SQLException
    {
        return new LegacyTableSelector(table, select, filter, sort).getObject(clss);
    }

    @Deprecated /** Use TableSelector */
    @NotNull
    public static <K> K[] select(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss, int maxRows, long offset)
            throws SQLException
    {
        return new LegacyTableSelector(table, select, filter, sort).setMaxRows(maxRows).setOffset(offset).getArray(clss);
    }

    @NotNull
    @Deprecated /** Use TableSelector */
    public static <K> K[] selectForDisplay(TableInfo table, Collection<ColumnInfo> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss)
            throws SQLException
    {
        TableSelector selector = new TableSelector(table, select, filter, sort);
        selector.setForDisplay(true);

        return selector.getArray(clss);
    }

    // return a result from a one column resultset. K should be a string or number type
    @Deprecated /** Use TableSelector */
    public static <K> K[] executeArray(TableInfo table, ColumnInfo col, @Nullable Filter filter, @Nullable Sort sort, Class<K> c) throws SQLException
    {
        return new LegacyTableSelector(col, filter, sort).getArray(c);
    }

    @NotNull
    @Deprecated /** Use TableSelector */
    public static <K> K[] selectForDisplay(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss)
            throws SQLException
    {
        TableSelector selector = new TableSelector(table, select, filter, sort);
        selector.setForDisplay(true);

        return selector.getArray(clss);
    }

    @Deprecated /** Use TableSelector */
    public static <K> K selectObject(TableInfo table, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss) throws SQLException
    {
        return new LegacyTableSelector(table, filter, sort).getObject(clss);
    }

    @Deprecated /** Use TableSelector */
    public static <K> K selectObject(TableInfo table, @Nullable Container c, Object pk, Class<K> clss)
    {
        return new TableSelector(table).getObject(c, pk, clss);
    }

    @Deprecated /** Use TableSelector */
    public static <K> K selectObject(TableInfo table, Object pk, Class<K> clss)
    {
        return new TableSelector(table).getObject(pk, clss);
    }

    @Deprecated /** Use TableSelector */
    public static <K> K selectObject(TableInfo table, int pk, Class<K> clss)
    {
        return new TableSelector(table).getObject(pk, clss);
    }

    @NotNull
    @Deprecated /** Use TableSelector */
    public static <K> K[] select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss) throws SQLException
    {
        return new LegacyTableSelector(table, columns, filter, sort).getArray(clss);
    }

    // return a result from a one column resultset. K can be simple type (string, number, date), a map, or a bean
    @Deprecated /** Use TableSelector */
    public static <K> K[] executeArray(TableInfo table, String column, @Nullable Filter filter, @Nullable Sort sort, Class<K> c) throws SQLException
    {
        return new LegacyTableSelector(table.getColumn(column), filter, sort).getArray(c);
    }

    @Deprecated /** Use TableSelector */
    public static Map<String, Object>[] selectMaps(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort) throws SQLException
    {
        LegacyTableSelector selector = new LegacyTableSelector(table, select, filter, sort);

        return selector.getMapArray();
    }

    @Deprecated /** Use TableSelector */
    public static Results select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort) throws SQLException
    {
        return new LegacyTableSelector(table, columns, filter, sort).getResults();
    }

    @Deprecated /** Use TableSelector */
    public static ResultSet select(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort) throws SQLException
    {
        return new LegacyTableSelector(table, select, filter, sort).getResultSet();
    }

    @Deprecated /** Use TableSelector */
    public static Results selectForDisplay(TableInfo table, Set<String> select, @Nullable Map<String, Object> parameters, @Nullable Filter filter, @Nullable Sort sort, int maxRows, long offset)
            throws SQLException
    {
        LegacyTableSelector selector = new LegacyTableSelector(table, select, filter, sort).setForDisplay(true);
        selector.setMaxRows(maxRows).setOffset(offset).setNamedParamters(parameters);

        return selector.getResults();
    }

    @Deprecated /** Use TableSelector */
    public static Results selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Map<String, Object> parameters, @Nullable Filter filter, @Nullable Sort sort, int maxRows, long offset)
            throws SQLException
    {
        LegacyTableSelector selector = new LegacyTableSelector(table, select, filter, sort).setForDisplay(true);
        selector.setMaxRows(maxRows).setOffset(offset).setNamedParamters(parameters);

        return selector.getResults();
    }

    @NotNull
    @Deprecated /** Use TableSelector */
    public static <K> K[] select(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss) throws SQLException
    {
        return new LegacyTableSelector(table, select, filter, sort).getArray(clss);
    }

    /**
     * This is a shortcut method that can be used for two-column ResultSets
     * The first column is key, the second column is the value
     */
    @Deprecated /** Use SqlSelector */
    public static Map executeValueMap(DbSchema schema, String sql, Object[] parameters, @Nullable Map<Object, Object> m)
            throws SQLException
    {
        if (null == m)
            return new LegacySqlSelector(schema, fragment(sql, parameters)).getValueMap();
        else
            return new LegacySqlSelector(schema, fragment(sql, parameters)).fillValueMap(m);
    }

    // return a result from a one column resultset. K can be simple type (string, number, date), a map, or a bean
    @Deprecated /** Use SqlSelector */
    public static <K> K[] executeArray(DbSchema schema, String sql, Object[] parameters, Class<K> c) throws SQLException
    {
        return new LegacySqlSelector(schema, fragment(sql, parameters)).getArray(c);
    }

    // return an array from a one column resultset. K can be simple type (string, number, date), a map, or a bean
    @Deprecated /** Use SqlSelector */
    public static <K> K[] executeArray(DbSchema schema, SQLFragment sql, Class<K> c) throws SQLException
    {
        return new LegacySqlSelector(schema, sql).getArray(c);
    }

    @Deprecated /** Use SqlSelector */   // TODO: Note, maxRows is misleading... query still selects Table.ALL_ROWS
    public static ResultSet executeQuery(DbSchema schema, String sql, Object[] parameters, int maxRows, boolean cache)
            throws SQLException
    {
        return new LegacySqlSelector(schema, fragment(sql, parameters)).setMaxRows(maxRows).getResultSet(cache);
    }

    @Deprecated /** Use SqlSelector */    // TODO: Note, maxRows is misleading... query still selects Table.ALL_ROWS
    public static TableResultSet executeQuery(DbSchema schema, SQLFragment sql, int maxRows) throws SQLException
    {
        return new LegacySqlSelector(schema, sql).setMaxRows(maxRows).getResultSet();
    }

    @Deprecated /** Use SqlSelector */
    public static ResultSet executeQuery(DbSchema schema, String sql, Object[] parameters, boolean cache)
            throws SQLException
    {
        return new LegacySqlSelector(schema, fragment(sql, parameters)).getResultSet(cache);
    }

    @Deprecated /** Use SqlExecutor */
    public static int execute(DbSchema schema, SQLFragment f) throws SQLException
    {
        return new LegacySqlExecutor(schema).execute(f);
    }

    @Deprecated /** Use SqlSelector */
    public static ResultSet executeQuery(DbSchema schema, SQLFragment sql, boolean cache, boolean scrollable) throws SQLException
    {
        return new LegacySqlSelector(schema, sql).getResultSet(cache, scrollable);
    }

    @NotNull
    @Deprecated /** Use SqlSelector */
    public static <K> K[] executeQuery(DbSchema schema, SQLFragment sqlf, Class<K> clss) throws SQLException
    {
        return new LegacySqlSelector(schema, sqlf).getArray(clss);
    }

    @NotNull
    @Deprecated /** Use SqlSelector */
    public static <K> K[] executeQuery(DbSchema schema, String sql, @Nullable Object[] parameters, Class<K> clss) throws SQLException
    {
        return new LegacySqlSelector(schema, fragment(sql, parameters)).getArray(clss);
    }

    @Deprecated /** Use SqlSelector */
    public static TableResultSet executeQuery(DbSchema schema, SQLFragment sql) throws SQLException
    {
        return new LegacySqlSelector(schema, sql).getResultSet();
    }

    @Deprecated /** Use SqlSelector */
    public static TableResultSet executeQuery(DbSchema schema, String sql, Object[] parameters) throws SQLException
    {
        return new LegacySqlSelector(schema, fragment(sql, parameters)).getResultSet();
    }

    /** return a result from a one row one column resultset. does not distinguish between not found, and NULL value */
    @Deprecated /** Use SqlSelector */
    public static <K> K executeSingleton(DbSchema schema, String sql, @Nullable Object[] parameters, Class<K> c) throws SQLException
    {
        return new LegacySqlSelector(schema, fragment(sql, parameters)).getObject(c);
    }

    @Deprecated /** Use SqlExecutor */
    public static int execute(DbSchema schema, String sql, @NotNull Object... parameters) throws SQLException
    {
        return new LegacySqlExecutor(schema).execute(sql, parameters);
    }

    @Deprecated
    private static SQLFragment fragment(String sql, @Nullable Object[] parameters)
    {
        return new SQLFragment(sql, null == parameters ? new Object[0] : parameters);
    }

    // ================== These methods have not been converted to Selector/Executor ==================

    // Careful: caller must track and clean up parameters (e.g., close InputStreams) after execution is complete
    public static PreparedStatement prepareStatement(Connection conn, String sql, Object[] parameters) throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(sql);
        setParameters(stmt, parameters);
        MemTracker.getInstance().put(stmt);
        return stmt;
    }


    public static void setParameters(PreparedStatement stmt, Object[] parameters) throws SQLException
    {
        setParameters(stmt, Arrays.asList(parameters));
    }

    public static void setParameters(PreparedStatement stmt, Collection<?> parameters) throws SQLException
    {
        if (null == parameters)
            return;

        int i = 1;

        for (Object value : parameters)
        {
            // UNDONE: this code belongs in Parameter._bind()
            //Parameter validation
            //Bug 1996 - rossb:  Generally, we let JDBC validate the
            //parameters and throw exceptions, however, it doesn't recognize NaN
            //properly which can lead to database corruption.  Trap that here
            {
                //if the input parameter is NaN, throw a sql exception
                boolean isInvalid = false;
                if (value instanceof Float)
                {
                    isInvalid = value.equals(Float.NaN);
                }
                else if (value instanceof Double)
                {
                    isInvalid = value.equals(Double.NaN);
                }

                if (isInvalid)
                {
                    throw new SQLException("Illegal argument (" + Integer.toString(i) + ") to SQL Statement:  " + value.toString() + " is not a valid parameter");
                }
            }

            Parameter p = new Parameter(stmt, i);
            p.setValue(value);
            i++;
        }
    }

    public static void closeParameters(Collection<Object> parameters)
    {
        for (Object value : parameters)
        {
            if (value instanceof Parameter.TypedValue)
            {
                value = ((Parameter.TypedValue)value)._value;
            }
            if (value instanceof AttachmentFile)
            {
                try
                {
                    ((AttachmentFile)value).closeInputStream();
                }
                catch(IOException e)
                {
                    // Ignore... make sure we attempt to close all the parameters
                }
            }
        }
    }


    /** @return if this is a statement that starts with SELECT, ignoring comment lines that start with "--" */
    static boolean isSelect(String sql)
    {
        for (String sqlLine : sql.split("\\r?\\n"))
        {
            sqlLine = sqlLine.trim();
            if (!sqlLine.startsWith("--"))
            {
                return StringUtils.startsWithIgnoreCase(sqlLine, "SELECT");
            }
        }
        return false;
    }


    // Careful: Caller must track and clean up parameters (e.g., close InputStreams) after execution is complete
    public static void batchExecute(DbSchema schema, String sql, Iterable<? extends Collection<?>> paramList) throws SQLException
    {
        Connection conn = schema.getScope().getConnection();
        PreparedStatement stmt = null;

        try
        {
            stmt = conn.prepareStatement(sql);
            int paramCounter = 0;
            for (Collection<?> params : paramList)
            {
                setParameters(stmt, params);
                stmt.addBatch();

                paramCounter += params.size();
                if (paramCounter > 1000)
                {
                    paramCounter = 0;
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }
        catch (SQLException e)
        {
            if (e instanceof BatchUpdateException)
            {
                if (null != e.getNextException())
                    e = e.getNextException();
            }

            logException(new SQLFragment(sql), conn, e, Level.WARN);
            throw(e);
        }
        finally
        {
            doClose(null, stmt, conn, schema.getScope());
        }
    }


    private static Map<Class, Getter> _getterMap = new HashMap<>(10);

    static enum Getter
    {
        STRING(String.class) {
            String getObject(ResultSet rs, int i) throws SQLException { return rs.getString(i); }
            String getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getString(columnLabel); }
        },
        INTEGER(Integer.class) {
            Integer getObject(ResultSet rs, int i) throws SQLException { int n = rs.getInt(i); return rs.wasNull() ? null : n ; }
            Integer getObject(ResultSet rs, String columnLabel) throws SQLException { int n = rs.getInt(columnLabel); return rs.wasNull() ? null : n ; }
        },
        DOUBLE(Double.class) {
            Double getObject(ResultSet rs, int i) throws SQLException { double d = rs.getDouble(i); return rs.wasNull() ? null : d ; }
            Double getObject(ResultSet rs, String columnLabel) throws SQLException { double d = rs.getDouble(columnLabel); return rs.wasNull() ? null : d ; }
        },
        BOOLEAN(Boolean.class) {
            Boolean getObject(ResultSet rs, int i) throws SQLException { boolean f = rs.getBoolean(i); return rs.wasNull() ? null : f ; }
            Boolean getObject(ResultSet rs, String columnLabel) throws SQLException { boolean f = rs.getBoolean(columnLabel); return rs.wasNull() ? null : f ; }
        },
        LONG(Long.class) {
            Long getObject(ResultSet rs, int i) throws SQLException { long l = rs.getLong(i); return rs.wasNull() ? null : l; }
            Long getObject(ResultSet rs, String columnLabel) throws SQLException { long l = rs.getLong(columnLabel); return rs.wasNull() ? null : l; }
        },
        UTIL_DATE(Date.class) {
            Date getObject(ResultSet rs, int i) throws SQLException { return rs.getTimestamp(i); }
            Date getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getTimestamp(columnLabel); }
        },
        BYTES(byte[].class) {
            Object getObject(ResultSet rs, int i) throws SQLException { return rs.getBytes(i); }
            Object getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getBytes(columnLabel); }
        },
        TIMESTAMP(Timestamp.class) {
            Object getObject(ResultSet rs, int i) throws SQLException { return rs.getTimestamp(i); }
            Object getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getTimestamp(columnLabel); }
        },
        OBJECT(Object.class) {
            Object getObject(ResultSet rs, int i) throws SQLException { return rs.getObject(i); }
            Object getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getObject(columnLabel); }
        };

        abstract Object getObject(ResultSet rs, int i) throws SQLException;
        abstract Object getObject(ResultSet rs, String columnName) throws SQLException;

        Object getObject(ResultSet rs) throws SQLException
        {
            return getObject(rs, 1);
        }

        private Getter(Class c)
        {
            _getterMap.put(c, this);
        }

        public static <K> Getter forClass(Class<K> c)
        {
            return _getterMap.get(c);
        }
    }


    // Standard SQLException catch block: log exception, query SQL, and params
    static void logException(SQLFragment sql, @Nullable Connection conn, SQLException e, Level logLevel)
    {
        if (SqlDialect.isCancelException(e))
        {
            return;
        }

        String trim = sql.getSQL().trim();

        if (SqlDialect.isConstraintException(e) && (StringUtils.startsWithIgnoreCase(trim, "INSERT") || StringUtils.startsWithIgnoreCase(trim, "UPDATE")))
        {
            if (Level.WARN.isGreaterOrEqual(logLevel))
            {
                _log.warn("SQL Exception", e);
                _logQuery(Level.WARN, sql, conn);
            }
        }
        else
        {
            if (Level.ERROR.isGreaterOrEqual(logLevel))
            {
                _log.error("SQL Exception", e);
                _logQuery(Level.ERROR, sql, conn);
            }
        }
    }


    // Typical finally block cleanup
    static void doClose(@Nullable ResultSet rs, @Nullable Statement stmt, @Nullable Connection conn, @NotNull DbScope scope)
    {
        try
        {
            if (stmt == null && rs != null)
                stmt = rs.getStatement();
        }
        catch (SQLException x)
        {
            _log.error("doFinally", x);
        }

        try
        {
            if (null != rs) rs.close();
        }
        catch (SQLException x)
        {
            _log.error("doFinally", x);
        }
        try
        {
            if (null != stmt) stmt.close();
        }
        catch (SQLException x)
        {
            _log.error("doFinally", x);
        }

        if (null != conn) scope.releaseConnection(conn);
    }


    /**
     * return a 'clean' list of fields to update
     */
    protected static <K> Map<String, Object> _getTableData(TableInfo table, K from, boolean insert)
    {
        Map<String, Object> fields;
        //noinspection unchecked
        ObjectFactory<K> f = ObjectFactory.Registry.getFactory((Class<K>)from.getClass());
        if (null == f)
            throw new IllegalArgumentException("Cound not find a matching object factory.");
        fields = f.toMap(from, null);
        return _getTableData(table, fields, insert);
    }


    protected static Map<String, Object> _getTableData(TableInfo table, Map<String, Object> fields, boolean insert)
    {
        if (!(fields instanceof CaseInsensitiveHashMap))
            fields = new CaseInsensitiveHashMap<>(fields);

        // special rename case
        if (fields.containsKey("containerId"))
            fields.put("container", fields.get("containerId"));

        List<ColumnInfo> columns = table.getColumns();
        Map<String, Object> m = new CaseInsensitiveHashMap<>(columns.size() * 2);

        for (ColumnInfo column : columns)
        {
            String key = column.getName();

//            if (column.isReadOnly() && !(insert && key.equals("EntityId")))
            if (!insert && column.isReadOnly() || column.isAutoIncrement() || column.isVersionColumn())
                continue;

            if (!fields.containsKey(key))
            {
                if (Character.isUpperCase(key.charAt(0)))
                {
                    key = Character.toLowerCase(key.charAt(0)) + key.substring(1);
                    if (!fields.containsKey(key))
                        continue;
                }
                else
                    continue;
            }

            Object v = fields.get(key);
            if (v instanceof String)
                v = _trimRight((String) v);
            m.put(column.getName(), v);
        }
        return m;
    }


    static String _trimRight(String s)
    {
        if (null == s) return "";
        return StringUtils.stripEnd(s, "\t\r\n ");
    }


    protected static void _insertSpecialFields(User user, TableInfo table, Map<String, Object> fields, java.sql.Timestamp date)
    {
        ColumnInfo col = table.getColumn("Owner");
        if (null != col && null != user)
            fields.put("Owner", user.getUserId());
        col = table.getColumn("CreatedBy");
        if (null != col && null != user)
            fields.put("CreatedBy", user.getUserId());
        col = table.getColumn("Created");
        if (null != col)
        {
            Date dateCreated = (Date)fields.get("Created");
            if (null == dateCreated || 0 == dateCreated.getTime())
                fields.put("Created", date);
        }
        col = table.getColumn("EntityId");
        if (col != null && fields.get("EntityId") == null)
            fields.put("EntityId", GUID.makeGUID());
    }

    protected static void _copyInsertSpecialFields(Object returnObject, Map<String, Object> fields)
    {
        if (returnObject == fields)
            return;

        // make sure that any GUID generated in this routine is stored in the returned object
        if (fields.containsKey("EntityId"))
            _setProperty(returnObject, "EntityId", fields.get("EntityId"));
        if (fields.containsKey("Owner"))
            _setProperty(returnObject, "Owner", fields.get("Owner"));
        if (fields.containsKey("Created"))
            _setProperty(returnObject, "Created", fields.get("Created"));
        if (fields.containsKey("CreatedBy"))
            _setProperty(returnObject, "CreatedBy", fields.get("CreatedBy"));
    }

    protected static void _updateSpecialFields(@Nullable User user, TableInfo table, Map<String, Object> fields, java.sql.Timestamp date)
    {
        ColumnInfo colModifiedBy = table.getColumn("ModifiedBy");
        if (null != colModifiedBy && null != user)
            fields.put(colModifiedBy.getName(), user.getUserId());

        ColumnInfo colModified = table.getColumn("Modified");
        if (null != colModified)
            fields.put(colModified.getName(), date);

        ColumnInfo colVersion = table.getVersionColumn();
        if (null != colVersion && colVersion != colModified && colVersion.getJdbcType() == JdbcType.TIMESTAMP)
            fields.put(colVersion.getName(), date);
    }

    protected static void _copyUpdateSpecialFields(TableInfo table, Object returnObject, Map<String, Object> fields)
    {
        if (returnObject == fields)
            return;

        if (fields.containsKey("ModifiedBy"))
            _setProperty(returnObject, "ModifiedBy", fields.get("ModifiedBy"));

        if (fields.containsKey("Modified"))
            _setProperty(returnObject, "Modified", fields.get("Modified"));

        ColumnInfo colModified = table.getColumn("Modified");
        ColumnInfo colVersion = table.getVersionColumn();
        if (null != colVersion && colVersion != colModified && colVersion.getJdbcType() == JdbcType.TIMESTAMP)
            _setProperty(returnObject, colVersion.getName(), fields.get(colVersion.getName()));
    }


    static private void _setProperty(Object fields, String propName, Object value)
    {
        if (fields instanceof Map)
        {
            ((Map<String, Object>) fields).put(propName, value);
        }
        else
        {
            if (Character.isUpperCase(propName.charAt(0)))
                propName = Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
            try
            {
                if (PropertyUtils.isWriteable(fields, propName))
                {
                    // use BeanUtils instead of PropertyUtils because BeanUtils will use a registered Converter
                    BeanUtils.copyProperty(fields, propName, value);
                }
                // UNDONE: horrible postgres hack..., not general fix
                else if (propName.endsWith("id"))
                {
                    propName = propName.substring(0,propName.length()-2) + "Id";
                    if (PropertyUtils.isWriteable(fields, propName))
                        BeanUtils.copyProperty(fields, propName, value);
                }
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }
    }


    // Returns a new Map<String, Object> if fieldsIn is a Map, otherwise returns modified version of fieldsIn.
    public static <K> K insert(@Nullable User user, TableInfo table, K fieldsIn) throws SQLException
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): ("Table " + table.getSchema().getName() + "." + table.getName() + " is not in the physical database.");

        // _executeTriggers(table, fields);

        StringBuilder columnSQL = new StringBuilder();
        StringBuilder valueSQL = new StringBuilder();
        ArrayList<Object> parameters = new ArrayList<>();
        ColumnInfo autoIncColumn = null;
        String comma = "";

        //noinspection unchecked
        Map<String, Object> fields = fieldsIn instanceof Map ?
                _getTableData(table, (Map<String, Object>)fieldsIn, true) :
                _getTableData(table, fieldsIn, true);
        java.sql.Timestamp date = new java.sql.Timestamp(System.currentTimeMillis());
        _insertSpecialFields(user, table, fields, date);
        _updateSpecialFields(user, table, fields, date);

        List<ColumnInfo> columns = table.getColumns();

        for (ColumnInfo column : columns)
        {
            if (column.isAutoIncrement())
                autoIncColumn = column;

            if (!fields.containsKey(column.getName()))
                continue;

            Object value = fields.get(column.getName());
            columnSQL.append(comma);
            columnSQL.append(column.getSelectName());
            valueSQL.append(comma);
            if (null == value || value instanceof String && 0 == ((String) value).length())
                valueSQL.append("NULL");
            else
            {
                // Check if the value is too long for a VARCHAR column, and provide a better error message than the database does
                // Can't do this right now because various modules override the <scale> in their XML metadata so that
                // it doesn't match the actual column length in the database
//                if (column.getJdbcType() == JdbcType.VARCHAR && column.getScale() > 0 && value.toString().length() > column.getScale())
//                {
//                    throw new SQLException("The column '" + column.getName() + "' has a maximum length of " + column.getScale() + " but the value '" + value + "' is " + value.toString().length() + " characters long.");
//                }
                valueSQL.append('?');
                parameters.add(new Parameter.TypedValue(value, column.getJdbcType()));
            }
            comma = ", ";
        }

        if (comma.length() == 0)
        {
            // NO COLUMNS TO INSERT
            throw new IllegalArgumentException("Table.insert called with no column data. table=" + table + " object=" + String.valueOf(fieldsIn));
        }

        StringBuilder insertSQL = new StringBuilder("INSERT INTO ");
        insertSQL.append(table.getSelectName());
        insertSQL.append("\n\t(");
        insertSQL.append(columnSQL);
        insertSQL.append(")\n\t");
        insertSQL.append("VALUES (");
        insertSQL.append(valueSQL);
        insertSQL.append(')');

        if (null != autoIncColumn)
            table.getSqlDialect().appendSelectAutoIncrement(insertSQL, autoIncColumn);

        // If Map was handed in, then we hand back a Map
        // UNDONE: use Table.select() to reselect and return new Object

        //noinspection unchecked
        K returnObject = (K) (fieldsIn instanceof Map && !(fieldsIn instanceof BoundMap) ? fields : fieldsIn);

        DbSchema schema = table.getSchema();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try
        {
            conn = schema.getScope().getConnection();
            stmt = prepareStatement(conn, insertSQL.toString(), parameters.toArray());

            if (null == autoIncColumn)
            {
                stmt.execute();
            }
            else
            {
                rs = table.getSqlDialect().executeWithResults(stmt);

                if (null != rs)
                {
                    rs.next();

                    // Explicitly retrieve the new rowId based on the autoIncrement type.  We shouldn't use getObject()
                    // here because PostgreSQL sequences always return Long, and we expect Integer in many places.
                    if (autoIncColumn.getJavaClass().isAssignableFrom(Long.TYPE))
                        _setProperty(returnObject, autoIncColumn.getName(), rs.getLong(1));
                    else
                        _setProperty(returnObject, autoIncColumn.getName(), rs.getInt(1));
                }
            }

            _copyInsertSpecialFields(returnObject, fields);
            _copyUpdateSpecialFields(table, returnObject, fields);

            notifyTableUpdate(table);
        }
        catch(SQLException e)
        {
            logException(new SQLFragment(insertSQL, parameters), conn, e, Level.WARN);
            throw(e);
        }
        finally
        {
            doClose(rs, stmt, conn, schema.getScope());
            closeParameters(parameters);
        }

        return returnObject;
    }


    public static <K> K update(@Nullable User user, TableInfo table, K fieldsIn, Object pkVals) throws SQLException
    {
        return update(user, table, fieldsIn, pkVals, null, Level.WARN);
    }


    public static <K> K update(@Nullable User user, TableInfo table, K fieldsIn, Object pkVals, @Nullable Filter filter, Level level) throws SQLException
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");
        assert null != pkVals;

        // _executeTriggers(table, previous, fields);

        StringBuilder setSQL = new StringBuilder();
        StringBuilder whereSQL = new StringBuilder();
        ArrayList<Object> parametersSet = new ArrayList<>();
        ArrayList<Object> parametersWhere = new ArrayList<>();
        String comma = "";

        // UNDONE -- rowVersion
        List<ColumnInfo> columnPK = table.getPkColumns();

        // Name-value pairs for the PK columns for this row
        Map<String, Object> keys = new CaseInsensitiveHashMap<>();

        if (columnPK.size() == 1 && !pkVals.getClass().isArray())
            keys.put(columnPK.get(0).getName(), pkVals);
        else if (pkVals instanceof Map)
            keys.putAll((Map)pkVals);
        else
        {
            Object[] pkValueArray = (Object[]) pkVals;
            if (pkValueArray.length != columnPK.size())
            {
                throw new IllegalArgumentException("Expected to get " + columnPK.size() + " key values, but got " + pkValueArray.length);
            }
            // Assume that the key values are in the same order as the key columns returned from getPkColumns()
            for (int i = 0; i < columnPK.size(); i++)
            {
                keys.put(columnPK.get(i).getName(), pkValueArray[i]);
            }
        }

        String whereAND = "WHERE ";

        if (null != filter)
        {
            SQLFragment fragment = filter.getSQLFragment(table, null);
            whereSQL.append(fragment.getSQL());
            parametersWhere.addAll(fragment.getParams());
            whereAND = " AND ";
        }

        for (ColumnInfo col : columnPK)
        {
            whereSQL.append(whereAND);
            whereSQL.append(col.getSelectName());
            whereSQL.append("=?");
            parametersWhere.add(keys.get(col.getName()));
            whereAND = " AND ";
        }

        //noinspection unchecked
        Map<String, Object> fields = fieldsIn instanceof Map ?
            _getTableData(table, (Map<String,Object>)fieldsIn, true) :
            _getTableData(table, fieldsIn, true);
        java.sql.Timestamp date = new java.sql.Timestamp(System.currentTimeMillis());
        _updateSpecialFields(user, table, fields, date);

        List<ColumnInfo> columns = table.getColumns();

        for (ColumnInfo column : columns)
        {
            if (!fields.containsKey(column.getName()))
                continue;

            Object value = fields.get(column.getName());
            setSQL.append(comma);
            setSQL.append(column.getSelectName());

            if (null == value || value instanceof String && 0 == ((String) value).length())
            {
                setSQL.append("=NULL");
            }
            else
            {
                // Check if the value is too long for a VARCHAR column, and provide a better error message than the database does
                // Can't do this right now because various modules override the <scale> in their XML metadata so that
                // it doesn't match the actual column length in the database
//                if (column.getJdbcType() == JdbcType.VARCHAR && column.getScale() > 0 && value.toString().length() > column.getScale())
//                {
//                    throw new SQLException("The column '" + column.getName() + "' has a maximum length of " + column.getScale() + " but the value '" + value + "' is " + value.toString().length() + " characters long.");
//                }

                setSQL.append("=?");
                parametersSet.add(new Parameter.TypedValue(value, column.getJdbcType()));
            }

            comma = ", ";
        }

        // UNDONE: reselect
        SQLFragment updateSQL = new SQLFragment("UPDATE " + table.getSelectName() + "\n\t" +
                "SET " + setSQL + "\n\t" +
                whereSQL);

        updateSQL.addAll(parametersSet);
        updateSQL.addAll(parametersWhere);

        try
        {
            int count = new SqlExecutor(table.getSchema()).execute(updateSQL);

            // check for concurrency problem
            if (count == 0)
            {
                throw OptimisticConflictException.create(ERROR_DELETED);
            }

            _copyUpdateSpecialFields(table, fieldsIn, fields);
            notifyTableUpdate(table);
        }
        catch(OptimisticConflictException e)
        {
            logException(updateSQL, null, e, level);
            throw(e);
        }

        return (fieldsIn instanceof Map && !(fieldsIn instanceof BoundMap)) ? (K)fields : fieldsIn;
    }


    public static void delete(TableInfo table, Object rowId) throws SQLException
    {
        List<ColumnInfo> columnPK = table.getPkColumns();
        Object[] pkVals;

        assert columnPK.size() == 1 || ((Object[]) rowId).length == columnPK.size();

        if (columnPK.size() == 1 && !rowId.getClass().isArray())
            pkVals = new Object[]{rowId};
        else
            pkVals = (Object[]) rowId;

        SimpleFilter filter = new SimpleFilter();
        for (int i = 0; i < pkVals.length; i++)
            filter.addCondition(columnPK.get(i), pkVals[i]);

        // UNDONE -- rowVersion
        if (delete(table, filter) == 0)
        {
            throw OptimisticConflictException.create(ERROR_DELETED);
        }
    }

    public static int delete(TableInfo table)
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");
        SqlExecutor sqlExecutor = new SqlExecutor(table.getSchema());
        int result = sqlExecutor.execute("DELETE FROM " + table.getSelectName());
        notifyTableUpdate(table);
        return result;
    }

    public static int delete(TableInfo table, Filter filter) throws SQLException
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");

        SQLFragment where = filter.getSQLFragment(table, null);

        String deleteSQL = "DELETE FROM " + table.getSelectName() + "\n\t" + where.getSQL();
        int result = new SqlExecutor(table.getSchema()).execute(deleteSQL, where.getParams().toArray());

        notifyTableUpdate(table);
        return result;
    }

    public static void truncate(TableInfo table) throws SQLException
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");
        SqlExecutor sqlExecutor = new SqlExecutor(table.getSchema());
        sqlExecutor.execute(table.getSqlDialect().getTruncateSql(table.getSelectName()));
        notifyTableUpdate(table);
    }


    public static SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort)
    {
        return QueryService.get().getSelectSQL(table, columns, filter, sort, ALL_ROWS, NO_OFFSET, false);
    }


    public static void ensureRequiredColumns(TableInfo table, Map<String, ColumnInfo> cols, @Nullable Filter filter, @Nullable Sort sort, @Nullable List<Aggregate> aggregates)
    {
        List<ColumnInfo> allColumns = table.getColumns();
        Set<FieldKey> requiredColumns = new HashSet<>();

        if (null != filter)
            requiredColumns.addAll(filter.getWhereParamFieldKeys());

        if (null != sort)
        {
            requiredColumns.addAll(sort.getRequiredColumns(cols));
        }

        if (null != aggregates)
        {
            for (Aggregate agg : aggregates)
                requiredColumns.add(agg.getFieldKey());
        }

        // TODO: Ensure pk, filter & where columns in cases where caller is naive

        for (ColumnInfo column : allColumns)
        {
            if (cols.containsKey(column.getAlias()))
                continue;
            if (requiredColumns.contains(column.getFieldKey()) || requiredColumns.contains(new FieldKey(null,column.getAlias())) || requiredColumns.contains(new FieldKey(null,column.getPropertyName())))
                cols.put(column.getAlias(), column);
            else if (column.isKeyField())
                cols.put(column.getAlias(), column);
            else if (column.isVersionColumn())
                cols.put(column.getAlias(), column);
        }
    }


    public static void snapshot(TableInfo tinfo, String tableName) throws SQLException
    {
        SQLFragment sqlSelect = getSelectSQL(tinfo, null, null, null);
        SQLFragment sqlSelectInto = new SQLFragment();
        sqlSelectInto.append("SELECT * INTO ").append(tableName).append(" FROM (");
        sqlSelectInto.append(sqlSelect);
        sqlSelectInto.append(") _from_");

        new SqlExecutor(tinfo.getSchema()).execute(sqlSelectInto);
    }


    public static class OptimisticConflictException extends SQLException
    {
        public OptimisticConflictException(String errorMessage, String sqlState, int error)
        {
            super(errorMessage, sqlState, error);
        }


        public static OptimisticConflictException create(int error)
        {
            switch (error)
            {
                case ERROR_DELETED:
                    return new OptimisticConflictException("Optimistic concurrency exception: Row deleted",
                            SQLSTATE_TRANSACTION_STATE,
                            error);
                case ERROR_ROWVERSION:
                    return new OptimisticConflictException("Optimistic concurrency exception: Row updated",
                            SQLSTATE_TRANSACTION_STATE,
                            error);
                case ERROR_TABLEDELETED:
                    return new OptimisticConflictException("Optimistic concurrency exception: Table deleted",
                            SQLSTATE_TRANSACTION_STATE,
                            error);
            }
            assert false : "unexpected error code";
            return null;
        }
    }



    // Table modification

    public static void notifyTableUpdate(/*String operation,*/ TableInfo table/*, Container c*/)
    {
        DbCache.invalidateAll(table);
    }


    private static void _logQuery(Level level, SQLFragment sqlFragment, @Nullable Connection conn)
    {
        if (!_log.isEnabledFor(level))
            return;

        String sql = sqlFragment.getSQL();
        Object[] parameters = sqlFragment.getParamsArray();

        StringBuilder logEntry = new StringBuilder(sql.length() * 2);
        logEntry.append("SQL ");

        Integer sid = null;
        if (conn instanceof ConnectionWrapper)
            sid = ((ConnectionWrapper)conn).getSPID();
        if (sid != null)
            logEntry.append(" [").append(sid).append("]");

        String[] lines = sql.split("\n");
        for (String line : lines)
            logEntry.append("\n    ").append(line);

        for (int i = 0; null != parameters && i < parameters.length; i++)
            logEntry.append("\n    ?[").append(i + 1).append("] ").append(String.valueOf(parameters[i]));

        logEntry.append("\n");
        _appendTableStackTrace(logEntry, 5);
        _log.log(level, logEntry);
    }


    static void _appendTableStackTrace(StringBuilder sb, int count)
    {
        StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        int i=1;  // Always skip call to getStackTrace()
        for ( ; i<ste.length ; i++)
        {
            String line = ste[i].toString();
            if (!line.startsWith("org.labkey.api.data.Table."))
                break;
        }
        int last = Math.min(ste.length,i+count);
        for ( ; i<last ; i++)
        {
            String line = ste[i].toString();
            if (line.startsWith("javax.servelet.http.HttpServlet.service("))
                break;
            sb.append("\n\t").append(line);
        }
    }

    
    /**
     * perform an in-memory join between the provided collection and a SQL table.
     * This is designed to work efficiently when the number of unique values of 'key'
     * is relatively small compared to left.size()
     *
     * @param left is the input collection
     * @param key  is the name of the column to join in the input collection
     * @param sql  is a string of the form "SELECT key, a, b FROM table where col in (?)"
     */
    public static List<Map> join(List<Map> left, String key, DbSchema schema, String sql) // NYI , Map right)
            throws SQLException
    {
        TreeSet<Object> keys = new TreeSet<>();
        for (Map m : left)
            keys.add(m.get(key));
        int size = keys.size();
        if (size == 0)
            return Collections.unmodifiableList(left);

        int q = sql.indexOf('?');
        if (q == -1 || sql.indexOf('?', q + 1) != -1)
            throw new IllegalArgumentException("malformed SQL for join()");
        StringBuilder inSQL = new StringBuilder(sql.length() + size * 2);
        inSQL.append(sql.substring(0, q + 1));
        for (int i = 1; i < size; i++)
            inSQL.append(",?");
        inSQL.append(sql.substring(q + 1));

        Map[] right = new LegacySqlSelector(schema, new SQLFragment(inSQL, keys.toArray())).getMapArray();
        return Join.join(left, Arrays.asList(right), key);
    }


    public static class TestCase extends Assert
    {
        private static final CoreSchema _core = CoreSchema.getInstance();

        public static class Principal
        {
            private int _userId;
            private String _ownerId;
            private String _type;
            private String _name;


            public int getUserId()
            {
                return _userId;
            }


            public void setUserId(int userId)
            {
                _userId = userId;
            }


            public String getOwnerId()
            {
                return _ownerId;
            }


            public void setOwnerId(String ownerId)
            {
                _ownerId = ownerId;
            }


            public String getType()
            {
                return _type;
            }


            public void setType(String type)
            {
                _type = type;
            }


            public String getName()
            {
                return _name;
            }


            public void setName(String name)
            {
                _name = name;
            }
        }


        @Test
        public void testSelect() throws SQLException
        {
            TableInfo tinfo = _core.getTableInfoPrincipals();

            //noinspection EmptyTryBlock,UnusedDeclaration
            try (ResultSet rs = new TableSelector(tinfo).getResultSet()){}

            Map[] maps = new TableSelector(tinfo).getMapArray();
            assertNotNull(maps);

            Principal[] principals = new TableSelector(tinfo).getArray(Principal.class);
            assertNotNull(principals);
            assertTrue(principals.length > 0);
            assertTrue(principals[0]._userId != 0);
            assertNotNull(principals[0]._name);
            assertEquals(maps.length, principals.length);
        }


        @Test
        public void testMaxRows() throws SQLException
        {
            TableInfo tinfo = _core.getTableInfoPrincipals();

            int maxRows;

            try (Results rsAll = new TableSelector(tinfo).getResults())
            {
                rsAll.last();
                maxRows = rsAll.getRow();
                assertTrue(rsAll.isComplete());
            }

            maxRows -= 2;

            try (Results rs = new TableSelector(tinfo).setMaxRows(maxRows).getResults())
            {
                rs.last();
                int row = rs.getRow();
                assertTrue(row == maxRows);
                assertFalse(rs.isComplete());
            }
        }


        @Test
        public void testMapJoin()
        {
            ArrayList<Map> left = new ArrayList<>();
            left.add(_quickMap("id=1&A=1"));
            left.add(_quickMap("id=2&A=2"));
            left.add(_quickMap("id=3&A=3"));
            left.add(_quickMap("id=4&A=1"));
            left.add(_quickMap("id=5&A=2"));
            left.add(_quickMap("id=6&A=3"));
            ArrayList<Map> right = new ArrayList<>();
            right.add(_quickMap("id=HIDDEN&A=1&B=one"));
            right.add(_quickMap("id=HIDDEN&A=2&B=two"));
            right.add(_quickMap("id=HIDDEN&A=3&B=three"));

            Collection<Map> join = Join.join(left, right, "A");
            Set<String> idSet = new HashSet<>();
            for (Map m : join)
            {
                idSet.add((String)m.get("id"));
                assertNotSame(m.get("id"), "HIDDEN");
                assertTrue(!m.get("A").equals("1") || m.get("B").equals("one"));
                assertTrue(!m.get("A").equals("2") || m.get("B").equals("two"));
                assertTrue(!m.get("A").equals("3") || m.get("B").equals("three"));
                PageFlowUtil.toQueryString(m.entrySet());
            }
            assertEquals(idSet.size(), 6);
        }


        @Test
        public void testSqlJoin() throws SQLException
        {
            //UNDONE
            // SELECT MEMBERS
            // Join(MEMBERS, "SELECT * FROM Principals where UserId IN (?)"

            CoreSchema core = CoreSchema.getInstance();
            DbSchema schema = core.getSchema();
            TableInfo membersTable = core.getTableInfoMembers();
            TableInfo principalsTable = core.getTableInfoPrincipals();

            Map[] members = new SqlSelector(schema, "SELECT * FROM " + membersTable.getSelectName()).getArray(Map.class);
            List<Map> users = join(Arrays.asList(members), "UserId", schema,
                    "SELECT * FROM " + principalsTable.getSelectName() + " WHERE UserId IN (?)");
            for (Map m : users)
            {
                String s = PageFlowUtil.toQueryString(m.entrySet());
            }
        }


        enum MyEnum
        {
            FRED, BARNEY, WILMA, BETTY
        }

        @Test
        public void testParameter()
                throws Exception
        {
            DbSchema core = DbSchema.get("core");
            SqlDialect dialect = core.getScope().getSqlDialect();
            
            String name = dialect.getTempTablePrefix() + "_" + GUID.makeHash();
            Connection conn = core.getScope().getConnection();
            assertTrue(conn != null);
            
            try
            {
                PreparedStatement stmt = conn.prepareStatement("CREATE " + dialect.getTempTableKeyword() + " TABLE " + name +
                        "(s VARCHAR(36), d " + dialect.sqlTypeNameFromJdbcType(JdbcType.TIMESTAMP) + ")");
                stmt.execute();
                stmt.close();

                String sql = "INSERT INTO " + name + " VALUES (?, ?)";
                stmt = conn.prepareStatement(sql);
                Parameter s = new Parameter(stmt, 1);
                Parameter d = new Parameter(stmt, 2, JdbcType.TIMESTAMP);

                s.setValue(4);
                d.setValue(GregorianCalendar.getInstance());
                stmt.execute();
                s.setValue(1.234);
                d.setValue(new java.sql.Timestamp(System.currentTimeMillis()));
                stmt.execute();
                s.setValue("string");
                d.setValue(null);
                stmt.execute();
                s.setValue(ContainerManager.getRoot());
                d.setValue(new java.util.Date());
                stmt.execute();
                s.setValue(MyEnum.BETTY);
                d.setValue(null);
                stmt.execute();
            }
            finally
            {
                try
                {
                    PreparedStatement cleanup = conn.prepareStatement("DROP TABLE " + name);
                    cleanup.execute();
                }
                finally
                {
                    conn.close();
                }
            }
        }


        @Test
        public void testAggregates() throws SQLException
        {
            TableInfo tinfo = CoreSchema.getInstance().getTableInfoContainers();
            List<Aggregate> aggregates = new LinkedList<>();

            // Test no aggregates case
            Map<String, List<Aggregate.Result>> aggregateMap = new TableSelector(tinfo, Collections.<ColumnInfo>emptyList(), null, null).getAggregates(aggregates);
            assertTrue(aggregateMap.isEmpty());

            aggregates.add(Aggregate.createCountStar());
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.Type.COUNT));
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.Type.SUM));
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.Type.AVG));
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.Type.MIN));
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.Type.MAX));
            aggregates.add(new Aggregate(tinfo.getColumn("Parent"), Aggregate.Type.COUNT));
            aggregates.add(new Aggregate(tinfo.getColumn("Parent").getFieldKey(), Aggregate.Type.COUNT, null, true));
            aggregates.add(new Aggregate(FieldKey.fromParts("Parent", "Parent"), Aggregate.Type.COUNT));
            aggregates.add(new Aggregate(tinfo.getColumn("SortOrder"), Aggregate.Type.SUM));
            aggregates.add(new Aggregate(tinfo.getColumn("SortOrder").getFieldKey(), Aggregate.Type.SUM, null, true));
            aggregates.add(new Aggregate(tinfo.getColumn("CreatedBy"), Aggregate.Type.COUNT));
            aggregates.add(new Aggregate(tinfo.getColumn("Created"), Aggregate.Type.MIN));
            aggregates.add(new Aggregate(tinfo.getColumn("Name"), Aggregate.Type.MIN));

            aggregateMap = new TableSelector(tinfo, Collections.<ColumnInfo>emptyList(), null, null).getAggregates(aggregates);

            String sql =
                    "SELECT " +
                        "CAST(COUNT(*) AS BIGINT) AS CountStar,\n" +
                        "CAST(COUNT(C.RowId) AS BIGINT) AS CountRowId,\n" +
                        "CAST(SUM(C.RowId) AS BIGINT) AS SumRowId,\n" +
                        "AVG(C.RowId) AS AvgRowId,\n" +
                        "CAST(MIN(C.RowId) AS BIGINT) AS MinRowId,\n" +
                        "CAST(MAX(C.RowId) AS BIGINT) AS MaxRowId,\n" +
                        "CAST(COUNT(C.Parent) AS BIGINT) AS CountParent,\n" +
                        "CAST(COUNT(DISTINCT C.Parent) AS BIGINT) AS CountDistinctParent,\n" +
                        "CAST(COUNT(P.Parent) AS BIGINT) AS CountParent_fs_Parent,\n" +
                        "CAST(SUM(C.SortOrder) AS BIGINT) AS SumSortOrder,\n" +
                        "CAST(SUM(DISTINCT C.SortOrder) AS BIGINT) AS SumDistinctSortOrder,\n" +
                        "CAST(COUNT(C.CreatedBy) AS BIGINT) AS CountCreatedBy,\n" +
                        "MIN(C.Created) AS MinCreated,\n" +
                        "MIN(C.Name) AS MinName\n" +
                    "FROM core.Containers C\n" +
                    "LEFT OUTER JOIN core.Containers P ON C.parent = P.entityid\n";
            Map<String, Object> expected = new SqlSelector(tinfo.getSchema(), sql).getMap();

            verifyAggregates(expected, aggregateMap);
        }


        private void verifyAggregates(Map<String, Object> expected, Map<String, List<Aggregate.Result>> aggregateMap)
        {
            verifyAggregate(expected.get("CountStar"), aggregateMap.get("*").get(0).getValue());

            verifyAggregate(expected.get("CountRowId"), aggregateMap.get("RowId").get(0).getValue());
            verifyAggregate(expected.get("SumRowId"), aggregateMap.get("RowId").get(1).getValue());
            verifyAggregate(expected.get("AvgRowId"), aggregateMap.get("RowId").get(2).getValue());
            verifyAggregate(expected.get("MinRowId"), aggregateMap.get("RowId").get(3).getValue());
            verifyAggregate(expected.get("MaxRowId"), aggregateMap.get("RowId").get(4).getValue());

            verifyAggregate(expected.get("CountParent"), aggregateMap.get("Parent").get(0).getValue());
            verifyAggregate(expected.get("CountDistinctParent"), aggregateMap.get("Parent").get(1).getValue());

            verifyAggregate(expected.get("CountParent_fs_Parent"), aggregateMap.get("Parent/Parent").get(0).getValue());

            verifyAggregate(expected.get("SumSortOrder"), aggregateMap.get("SortOrder").get(0).getValue());
            verifyAggregate(expected.get("SumDistinctSortOrder"), aggregateMap.get("SortOrder").get(1).getValue());

            verifyAggregate(expected.get("CountCreatedBy"), aggregateMap.get("CreatedBy").get(0).getValue());
            verifyAggregate(expected.get("MinCreated"), aggregateMap.get("Created").get(0).getValue());
            verifyAggregate(expected.get("MinName"), aggregateMap.get("Name").get(0).getValue());
        }


        private void verifyAggregate(Object expected, Object actual)
        {
            // Address AVG on SQL Server... expected query returns Integer type but aggregate converts to Long
            if (expected.getClass() != actual.getClass())
                assertEquals(((Number)expected).longValue(), ((Number)actual).longValue());
            else
                assertEquals(expected, actual);
        }


        private Map<String, String> _quickMap(String q)
        {
            Map<String, String> m = new HashMap<>();
            for (Pair<String, String> p : PageFlowUtil.fromQueryString(q))
                m.put(p.first, p.second);
            return m;
        }
    }


    static public Map<FieldKey, ColumnInfo> createColumnMap(@Nullable TableInfo table, @Nullable Collection<ColumnInfo> columns)
    {
        Map<FieldKey, ColumnInfo> ret = new HashMap<>();
        if (columns != null)
        {
            for (ColumnInfo column : columns)
            {
                ret.put(column.getFieldKey(), column);
            }
        }
        if (table != null)
        {
            for (String name : table.getColumnNameSet())
            {
                FieldKey f = FieldKey.fromParts(name);
                if (ret.containsKey(f))
                    continue;
                ColumnInfo column = table.getColumn(name);
                if (column != null)
                {
                    ret.put(column.getFieldKey(), column);
                }
            }
        }
        return ret;
    }


    public static boolean checkAllColumns(TableInfo table, Collection<ColumnInfo> columns, String prefix)
    {
        return checkAllColumns(table, columns, prefix, false);
    }

    public static boolean checkAllColumns(TableInfo table, Collection<ColumnInfo> columns, String prefix, boolean enforceUnique)
    {
        int bad = 0;

//        Map<FieldKey, ColumnInfo> mapFK = new HashMap<>(columns.size()*2);
        Map<String, ColumnInfo> mapAlias = new HashMap<>(columns.size()*2);
        ColumnInfo prev;

        for (ColumnInfo column : columns)
        {
            if (!checkColumn(table, column, prefix))
                bad++;
//            if (enforceUnique && null != (prev=mapFK.put(column.getFieldKey(), column)) && prev != column)
//                bad++;
            if (enforceUnique && null != (prev=mapAlias.put(column.getAlias(),column)) && prev != column)
                bad++;
        }

        // Check all the columns in the TableInfo to determine if the TableInfo is corrupt
        for (ColumnInfo column : table.getColumns())
            if (!checkColumn(table, column, "TableInfo.getColumns() for " + prefix))
                bad++;

        // Check the pk columns in the TableInfo
        for (ColumnInfo column : table.getPkColumns())
            if (!checkColumn(table, column, "TableInfo.getPkColumns() for " + prefix))
                bad++;

        return 0 == bad;
    }


    public static boolean checkColumn(TableInfo table, ColumnInfo column, String prefix)
    {
        if (column.getParentTable() != table)
        {
            _log.warn(prefix + ": Column " + column + " is from the wrong table: " + column.getParentTable() + " instead of " + table);
            return false;
        }
        else
        {
            return true;
        }
    }


    public static Parameter.ParameterMap deleteStatement(Connection conn, TableInfo tableDelete /*, Set<String> columns */) throws SQLException
    {
        if (!(tableDelete instanceof UpdateableTableInfo))
            throw new IllegalArgumentException();

        if (null == conn)
            conn = tableDelete.getSchema().getScope().getConnection();

        UpdateableTableInfo updatable = (UpdateableTableInfo)tableDelete;
        TableInfo table = updatable.getSchemaTableInfo();

        if (!(table instanceof SchemaTableInfo))
            throw new IllegalArgumentException();
        if (null == ((SchemaTableInfo)table).getMetaDataName())
            throw new IllegalArgumentException();

        SqlDialect d = tableDelete.getSqlDialect();

        List<ColumnInfo> columnPK = table.getPkColumns();
        List<Parameter> paramPK = new ArrayList<>(columnPK.size());
        for (ColumnInfo pk : columnPK)
            paramPK.add(new Parameter(pk.getName(), null, pk.getJdbcType()));
        Parameter paramContainer = new Parameter("container", null, JdbcType.VARCHAR);

        SQLFragment sqlfWhere = new SQLFragment();
        sqlfWhere.append("\nWHERE " );
        String and = "";
        for (int i=0 ; i<columnPK.size() ; i++)
        {
            ColumnInfo pk = columnPK.get(i);
            Parameter p = paramPK.get(i);
            sqlfWhere.append(and); and = " AND ";
            sqlfWhere.append(pk.getSelectName()).append("=?");
            sqlfWhere.add(p);
        }
        if (null != table.getColumn("container"))
        {
            sqlfWhere.append(and);
            sqlfWhere.append("container=?");
            sqlfWhere.add(paramContainer);
        }

        SQLFragment sqlfDelete = new SQLFragment();
        SQLFragment sqlfDeleteObject = null;
        SQLFragment sqlfDeleteTable;

        //
        // exp.Objects delete
        //

        Domain domain = tableDelete.getDomain();
        DomainKind domainKind = tableDelete.getDomainKind();
        if (null != domain && null != domainKind && StringUtils.isEmpty(domainKind.getStorageSchemaName()))
        {
            if (!d.isPostgreSQL() && !d.isSqlServer())
                throw new IllegalArgumentException("Domains are only supported for sql server and postgres");

            String objectIdColumnName = updatable.getObjectIdColumnName();
            String objectURIColumnName = updatable.getObjectURIColumnName();

            if (null == objectIdColumnName && null == objectURIColumnName)
                throw new IllegalStateException("Join column for exp.Object must be defined");

            SQLFragment sqlfSelectKey = new SQLFragment();
            if (null != objectIdColumnName)
            {
                String keyName = StringUtils.defaultString(objectIdColumnName, objectURIColumnName);
                ColumnInfo keyCol = table.getColumn(keyName);
                sqlfSelectKey.append("SELECT ").append(keyCol.getSelectName());
                sqlfSelectKey.append("FROM ").append(table.getFromSQL("X"));
                sqlfSelectKey.append(sqlfWhere);
            }

            String fn = null==objectIdColumnName ? "deleteObject" : "deleteObjectByid";
            SQLFragment deleteArguments = new SQLFragment("?, ");
            deleteArguments.add(paramContainer);
            deleteArguments.append(sqlfSelectKey);
            sqlfDeleteObject = d.execute(ExperimentService.get().getSchema(), fn, deleteArguments);
        }

        //
        // BASE TABLE delete
        //

        sqlfDeleteTable = new SQLFragment("DELETE " + table.getSelectName());
        sqlfDelete.append(sqlfWhere);

        if (null != sqlfDeleteObject)
        {
            sqlfDelete.append(sqlfDeleteObject);
            sqlfDelete.append(";\n");
        }
        sqlfDelete.append(sqlfDeleteTable);

        return new Parameter.ParameterMap(tableDelete.getSchema().getScope(), conn, sqlfDelete, updatable.remapSchemaColumns());
    }


    public static class TestDataIterator extends AbstractDataIterator
    {
        private final String guid = GUID.makeGUID();
        private final Date date = new Date();

        // TODO: guid values are ignored, since guidCallable gets used instead
        private final Object[][] _data = new Object[][]
        {
            new Object[] {1, "One", 101, true, date, guid},
            new Object[] {2, "Two", 102, true, date, guid},
            new Object[] {3, "Three", 103, true, date, guid}
        };

        private final static class _ColumnInfo extends ColumnInfo
        {
            _ColumnInfo(String name, JdbcType type)
            {
                super(name, type);
                setReadOnly(true);
            }
        }

        private final static ColumnInfo[] _cols = new ColumnInfo[]
        {
            new _ColumnInfo("_row", JdbcType.INTEGER),
            new _ColumnInfo("Text", JdbcType.VARCHAR),
            new _ColumnInfo("IntNotNull", JdbcType.INTEGER),
            new _ColumnInfo("BitNotNull", JdbcType.BOOLEAN),
            new _ColumnInfo("DateTimeNotNull", JdbcType.TIMESTAMP),
            new _ColumnInfo("EntityId", JdbcType.VARCHAR)
        };

        int currentRow = -1;

        TestDataIterator()
        {
            super(null);
        }

        @Override
        public int getColumnCount()
        {
            return _cols.length-1;
        }

        @Override
        public ColumnInfo getColumnInfo(int i)
        {
            return _cols[i];
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            return ++currentRow < _data.length;
        }

        @Override
        public Object get(int i)
        {
            return _data[currentRow][i];
        }

        @Override
        public void close() throws IOException
        {
        }
    }


    public static class DataIteratorTestCase extends Assert
    {
        @Test
        public void test() throws Exception
        {
            TableInfo testTable = TestSchema.getInstance().getTableInfoTestTable();

            DataIteratorContext dic = new DataIteratorContext();
            TestDataIterator extract = new TestDataIterator();
            SimpleTranslator translate = new SimpleTranslator(extract, dic);
            translate.selectAll();
            translate.addBuiltInColumns(JunitUtil.getTestContainer(), TestContext.get().getUser(), testTable, false);

            DataIteratorBuilder load = TableInsertDataIterator.create(
                    translate,
                    testTable,
                    dic
            );
            new Pump(load, dic).run();

            assertFalse(dic.getErrors().hasErrors());

            new SqlExecutor(testTable.getSchema()).execute("DELETE FROM test.testtable WHERE EntityId = '" + extract.guid + "'");
        }
    }
}
