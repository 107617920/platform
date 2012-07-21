/*
 * Copyright (c) 2004-2012 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Join;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.etl.AbstractDataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;

import javax.servlet.http.HttpServletResponse;
import javax.sql.rowset.CachedRowSet;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
 * Table manipulation methods
 * <p/>
 * select/insert/update/delete
 */

public class Table
{
    public static final Set<String> ALL_COLUMNS = Collections.unmodifiableSet(Collections.<String>emptySet());

    public static final String SQLSTATE_TRANSACTION_STATE = "25000";
    public static final int ERROR_ROWVERSION = 10001;
    public static final int ERROR_DELETED = 10002;

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

    public static boolean validOffset(long offset)
    {
        return offset >= 0;
    }

    // Careful: caller must track and clean up parameters (e.g., close InputStreams) after execution is complete
    public static PreparedStatement prepareStatement(Connection conn, String sql, Object[] parameters) throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(sql);
        setParameters(stmt, parameters);
        assert MemTracker.put(stmt);
        return stmt;
    }


    static ResultSet _executeQuery(Connection conn, String sql, Object[] parameters, boolean scrollable, @Nullable AsyncQueryRequest asyncRequest, @Nullable Integer statementRowCount)
            throws SQLException
    {
        ResultSet rs;

        if (null == parameters || 0 == parameters.length)
        {
            Statement statement = conn.createStatement(scrollable ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            initializeStatement(statement, asyncRequest, statementRowCount);
            rs = statement.executeQuery(sql);
        }
        else
        {
            PreparedStatement stmt = conn.prepareStatement(sql, scrollable ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            initializeStatement(stmt, asyncRequest, statementRowCount);

            try
            {
                setParameters(stmt, parameters);
                rs = stmt.executeQuery();
            }
            finally
            {
                closeParameters(parameters);
            }
        }
        if (asyncRequest != null)
        {
            asyncRequest.setStatement(null);
        }

        assert MemTracker.put(rs);
        return rs;
    }


    private static void initializeStatement(Statement statement, @Nullable AsyncQueryRequest asyncRequest, @Nullable Integer statementRowCount) throws SQLException
    {
        // Don't set max rows if null or special ALL_ROWS value (we're assuming statement.getMaxRows() defaults to 0, though this isn't actually documented...)
        if (null != statementRowCount && ALL_ROWS != statementRowCount)
        {
            statement.setMaxRows(statementRowCount == NO_ROWS ? 1 : statementRowCount);
        }

        if (asyncRequest != null)
        {
            asyncRequest.setStatement(statement);
        }
    }


    public static int execute(Connection conn, String sql, @NotNull Object... parameters) throws SQLException
    {
        Statement stmt = null;

        try
        {
            if (0 == parameters.length)
            {
                stmt = conn.createStatement();
                if (stmt.execute(sql))
                    return -1;
                else
                    return stmt.getUpdateCount();
            }
            else
            {
                stmt = conn.prepareStatement(sql);
                setParameters((PreparedStatement)stmt, parameters);
                if (((PreparedStatement)stmt).execute())
                    return -1;
                else
                    return stmt.getUpdateCount();
            }
        }
        finally
        {
            if (null != stmt)
                stmt.close();

            closeParameters(parameters);
        }
    }

    private static void setParameters(PreparedStatement stmt, Object[] parameters) throws SQLException
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

    public static void closeParameters(Object[] parameters)
    {
        for (Object value : parameters)
        {
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

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public static Table.TableResultSet executeQuery(DbSchema schema, String sql, Object[] parameters) throws SQLException
    {
        return (Table.TableResultSet) executeQuery(schema, sql, parameters, ALL_ROWS, true);
    }

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public static Table.TableResultSet executeQuery(DbSchema schema, SQLFragment sql) throws SQLException
    {
        return executeQuery(schema, sql.getSQL(), sql.getParams().toArray());
    }


    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public static Table.TableResultSet executeQuery(DbSchema schema, SQLFragment sql, int rowCount)
            throws SQLException
    {
        return (Table.TableResultSet) executeQuery(schema, sql.getSQL(), sql.getParamsArray(), rowCount, NO_OFFSET, true, false, null, null, null);
    }

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public static ResultSet executeQuery(DbSchema schema, SQLFragment sql, int rowCount, boolean cache, boolean scrollable) throws SQLException
    {
        return executeQuery(schema, sql.getSQL(), sql.getParamsArray(), rowCount, cache, scrollable);
    }

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public static ResultSet executeQuery(DbSchema schema, String sql, Object[] parameters, int rowCount, boolean cache)
            throws SQLException
    {
        return executeQuery(schema, sql, parameters, rowCount, NO_OFFSET, cache, false, null, null, null);
    }

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public static ResultSet executeQuery(DbSchema schema, String sql, Object[] parameters, int rowCount, boolean cache, boolean scrollable)
            throws SQLException
    {
        return executeQuery(schema, sql, parameters, rowCount, NO_OFFSET, cache, scrollable, null, null, null);
    }

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    private static ResultSet executeQuery(DbSchema schema, String sql, Object[] parameters, int rowCount, long scrollOffset, boolean cache, boolean scrollable, @Nullable AsyncQueryRequest asyncRequest, @Nullable Logger log, @Nullable Integer statementRowCount)
            throws SQLException
    {
        if (log == null) log = _log;
        Connection conn = null;
        ResultSet rs = null;
        boolean queryFailed = false;

        try
        {
            conn = schema.getScope().getConnection(log);
            if (isSelect(sql) && !schema.getScope().isTransactionActive())
            {
                // Only fiddle with the Connection settings if we're not inside of a transaction so we won't mess
                // up any state the caller is relying on. Also, only do this when we're fairly certain that it's
                // a read-only statement (starting with SELECT)
                schema.getSqlDialect().configureToDisableJdbcCaching(conn);
            }

            rs = _executeQuery(conn, sql, parameters, scrollable, asyncRequest, statementRowCount);

            while (scrollOffset > 0 && rs.next())
                scrollOffset--;

            if (cache)
                return cacheResultSet(schema.getSqlDialect(), rs, rowCount, asyncRequest);
            else
                return new ResultSetImpl(conn, schema, rs, rowCount);
        }
        catch(SQLException e)
        {
            logException(sql, parameters, conn, e);
            queryFailed = true;
            throw(e);
        }
        finally
        {
            // Close everything for cached result sets and exceptions only
            if (cache || queryFailed)
                doFinally(rs, null, conn, schema.getScope());
        }
    }

    /** @return if this is a statement that starts with SELECT, ignoring comment lines that start with "--" */
    private static boolean isSelect(String sql)
    {
        for (String sqlLine : sql.split("\\r?\\n"))
        {
            sqlLine = sqlLine.trim();
            if (!sqlLine.startsWith("--"))
            {
                return sqlLine.toUpperCase().startsWith("SELECT");
            }
        }
        return false;
    }


    @NotNull
    public static <K> K[] executeQuery(DbSchema schema, SQLFragment sqlf, Class<K> clss) throws SQLException
    {
        return new LegacySqlSelector(schema, sqlf).getArray(clss);
    }


    @NotNull
    public static <K> K[] executeQuery(DbSchema schema, String sql, @Nullable Object[] parameters, Class<K> clss) throws SQLException
    {
        return new LegacySqlSelector(schema, fragment(sql, parameters)).getArray(clss);
    }


    public static int execute(DbSchema schema, SQLFragment f) throws SQLException
    {
        return new LegacySqlExecutor(schema, f).execute();
    }


    public static int execute(DbSchema schema, String sql, @NotNull Object... parameters) throws SQLException
    {
        return new LegacySqlExecutor(schema, new SQLFragment(sql, parameters)).execute();
    }


    // Careful: Caller must track and clean up parameters (e.g., close InputStreams) after execution is complete
    public static void batchExecute(DbSchema schema, String sql, Iterable<? extends Collection<?>> paramList)
            throws SQLException
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

            logException(sql, null, conn, e);
            throw(e);
        }
        finally
        {
            doFinally(null, stmt, conn, schema.getScope());
        }
    }


    /** return a result from a one row one column resultset. does not distinguish between not found, and NULL value */
    public static <K> K executeSingleton(DbSchema schema, String sql, @Nullable Object[] parameters, Class<K> c) throws SQLException
    {
        return new LegacySqlSelector(schema, fragment(sql, parameters)).getObject(c);
    }


    private static Map<Class, Getter> _getterMap = new HashMap<Class, Getter>(10);

    static enum Getter
    {
        STRING(String.class) { String getObject(ResultSet rs, int i) throws SQLException { return rs.getString(i); }},
        INTEGER(Integer.class) { Integer getObject(ResultSet rs, int i) throws SQLException { int n = rs.getInt(i); return rs.wasNull() ? null : n ; }},
        DOUBLE(Double.class) { Double getObject(ResultSet rs, int i) throws SQLException { double d = rs.getDouble(i); return rs.wasNull() ? null : d ; }},
        BOOLEAN(Boolean.class) { Boolean getObject(ResultSet rs, int i) throws SQLException { boolean f = rs.getBoolean(i); return rs.wasNull() ? null : f ; }},
        LONG(Long.class) { Long getObject(ResultSet rs, int i) throws SQLException { long l = rs.getLong(i); return rs.wasNull() ? null : l; }},
        UTIL_DATE(Date.class) { Date getObject(ResultSet rs, int i) throws SQLException { return rs.getTimestamp(i); }},
        BYTES(byte[].class) { Object getObject(ResultSet rs, int i) throws SQLException { return rs.getBytes(i); }},
        TIMESTAMP(Timestamp.class) { Object getObject(ResultSet rs, int i) throws SQLException { return rs.getTimestamp(i); }},
        OBJECT(Object.class) { Object getObject(ResultSet rs, int i) throws SQLException { return rs.getObject(i); }};

        abstract Object getObject(ResultSet rs, int i) throws SQLException;
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
    static void logException(String sql, @Nullable Object[] parameters, Connection conn, SQLException e)
    {
        logException(sql, parameters, conn, e, Level.WARN);    // Log all warnings and errors by default
    }


    // Standard SQLException catch block: log exception, query SQL, and params
    static void logException(String sql, @Nullable Object[] parameters, Connection conn, SQLException e, Level logLevel)
    {
        if (SqlDialect.isCancelException(e))
        {
            return;
        }
        else if (sql.startsWith("INSERT") && SqlDialect.isConstraintException(e))
        {
            if (Level.WARN.isGreaterOrEqual(logLevel))
            {
                _log.warn("SQL Exception", e);
                _logQuery(Level.WARN, sql, parameters, conn);
            }
        }
        else
        {
            if (Level.ERROR.isGreaterOrEqual(logLevel))
            {
                _log.error("SQL Exception", e);
                _logQuery(Level.ERROR, sql, parameters, conn);
            }
        }
    }


    // Typical finally block cleanup
    static void doFinally(@Nullable ResultSet rs, @Nullable Statement stmt, @Nullable Connection conn, @NotNull DbScope scope)
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


    // return a result from a one column resultset. K should be a string or number type
    public static <K> K[] executeArray(TableInfo table, String column, @Nullable Filter filter, @Nullable Sort sort, Class<K> c) throws SQLException
    {
        return new LegacyTableSelector(table.getColumn(column), filter, sort).getArray(c);
    }

    // return a result from a one column resultset. K should be a string or number type
    public static <K> K[] executeArray(TableInfo table, ColumnInfo col, @Nullable Filter filter, @Nullable Sort sort, Class<K> c) throws SQLException
    {
        return new LegacyTableSelector(col, filter, sort).getArray(c);
    }
    
    // return a result from a one column resultset. K should be a string or number type
    public static <K> K[] executeArray(DbSchema schema, SQLFragment sql, Class<K> c) throws SQLException
    {
        return new LegacySqlSelector(schema, sql).getArray(c);
    }

    // TODO: Matt: Table layer allows parameters == null, but SQLFragment doesn't... change SQLFragment?  Or change Table callers?
    private static SQLFragment fragment(String sql, @Nullable Object[] parameters)
    {
        return new SQLFragment(sql, null == parameters ? new Object[0] : parameters);
    }


    // return a result from a one column resultset. K should be a string or number type
    public static <K> K[] executeArray(DbSchema schema, String sql, Object[] parameters, Class<K> c) throws SQLException
    {
        return new LegacySqlSelector(schema, fragment(sql, parameters)).getArray(c);
    }


    /**
     * This is a shortcut method that can be used for two-column ResultSets
     * The first column is key, the second column is the value
     */
    public static Map executeValueMap(DbSchema schema, String sql, Object[] parameters, @Nullable Map<Object, Object> m)
            throws SQLException
    {
        if (null == m)
            return new LegacySqlSelector(schema, fragment(sql, parameters)).getValueMap();
        else
            return new LegacySqlSelector(schema, fragment(sql, parameters)).fillValueMap(m);
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
            fields = new CaseInsensitiveHashMap<Object>(fields);

        // special rename case
        if (fields.containsKey("containerId"))
            fields.put("container", fields.get("containerId"));

        List<ColumnInfo> columns = table.getColumns();
        Map<String, Object> m = new CaseInsensitiveHashMap<Object>(columns.size() * 2);

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
        ArrayList<Object> parameters = new ArrayList<Object>();
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
                parameters.add(value);
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
            table.getSqlDialect().appendSelectAutoIncrement(insertSQL, table, autoIncColumn.getSelectName());

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
                rs = table.getSqlDialect().executeInsertWithResults(stmt);

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
            logException(insertSQL.toString(), parameters.toArray(), conn, e);
            throw(e);
        }
        finally
        {
            doFinally(rs, stmt, conn, schema.getScope());
            closeParameters(parameters.toArray());
        }

        return returnObject;
    }


    public static <K> K update(@Nullable User user, TableInfo table, K fieldsIn, Object pkVals) throws SQLException
    {
        return update(user, table, fieldsIn, pkVals, null);
    }


    public static <K> K update(@Nullable User user, TableInfo table, K fieldsIn, Object pkVals, @Nullable Filter filter) throws SQLException
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");
        assert null != pkVals;

        // _executeTriggers(table, previous, fields);

        StringBuilder setSQL = new StringBuilder();
        StringBuilder whereSQL = new StringBuilder();
        ArrayList<Object> parametersSet = new ArrayList<Object>();
        ArrayList<Object> parametersWhere = new ArrayList<Object>();
        String comma = "";

        // UNDONE -- rowVersion
        List<ColumnInfo> columnPK = table.getPkColumns();

        // Name-value pairs for the PK columns for this row
        Map<String, Object> keys = new CaseInsensitiveHashMap<Object>();

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
                parametersSet.add(value);
            }

            comma = ", ";
        }

        // UNDONE: reselect
        SQLFragment updateSQL = new SQLFragment("UPDATE " + table.getSelectName() + "\n\t" +
                "SET " + setSQL + "\n\t" +
                whereSQL);

        DbSchema schema = table.getSchema();
        Connection conn = null;
        PreparedStatement stmt = null;

        updateSQL.addAll(parametersSet);
        updateSQL.addAll(parametersWhere);

        try
        {
            conn = schema.getScope().getConnection();
            int count = execute(table.getSchema(), updateSQL);

            // check for concurrency problem
            if (count == 0)
            {
                throw OptimisticConflictException.create(ERROR_DELETED);
            }

            _copyUpdateSpecialFields(table, fieldsIn, fields);
            notifyTableUpdate(table);
        }
        catch(SQLException e)
        {
            logException(updateSQL.getSQL(), updateSQL.getParamsArray(), conn, e);
            throw(e);
        }

        finally
        {
            doFinally(null, stmt, conn, schema.getScope());
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


    public static int delete(TableInfo table, Filter filter) throws SQLException
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");

        SQLFragment where = filter.getSQLFragment(table, null);

        String deleteSQL = "DELETE FROM " + table.getSelectName() + "\n\t" + where.getSQL();
        int result = Table.execute(table.getSchema(), deleteSQL, where.getParams().toArray());

        notifyTableUpdate(table);
        return result;
    }


    public static <K> K selectObject(TableInfo table, int pk, Class<K> clss)
    {
        return new TableSelector(table).getObject(pk, clss);
    }


    public static <K> K selectObject(TableInfo table, Object pk, Class<K> clss)
    {
        return new TableSelector(table).getObject(pk, clss);
    }


    public static <K> K selectObject(TableInfo table, @Nullable Container c, Object pk, Class<K> clss)
    {
        return new TableSelector(table).getObject(c, pk, clss);
    }


    public static Map<String, Object>[] selectMaps(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort) throws SQLException
    {
        LegacyTableSelector selector = new LegacyTableSelector(table, select, filter, sort);

        //noinspection unchecked
        return selector.getArray(Map.class);
    }


    public static <K> K selectObject(TableInfo table, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss) throws SQLException
    {
        return new LegacyTableSelector(table, filter, sort).getObject(clss);
    }


    @Deprecated
    public static <K> K selectObject(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss) throws SQLException
    {
        return new LegacyTableSelector(table, select, filter, sort).getObject(clss);
    }


    public static SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort)
    {
        return QueryService.get().getSelectSQL(table, columns, filter, sort, ALL_ROWS, NO_OFFSET, false);
    }


    public static ResultSet select(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort) throws SQLException
    {
        return select(table, columnInfosList(table, select), filter, sort);
    }


    public static Results select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort) throws SQLException
    {
        return QueryService.get().select(table, columns, filter, sort);
    }


    @NotNull
    public static <K> K[] select(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss) throws SQLException
    {
        return new LegacyTableSelector(table, select, filter, sort).getArray(clss);
    }


    @NotNull
    public static <K> K[] select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss) throws SQLException
    {
        return new LegacyTableSelector(table, columns, filter, sort).getArray(clss);
    }


    @Deprecated // Use TableSelector
    @NotNull
    public static <K> K[] select(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss, int rowCount, long offset)
            throws SQLException
    {
        return new LegacyTableSelector(table, select, filter, sort).setRowCount(rowCount).setOffset(offset).getArray(clss);
    }


    public static Results selectForDisplay(TableInfo table, Set<String> select, @Nullable Map<String, Object> parameters, @Nullable Filter filter, @Nullable Sort sort, int rowCount, long offset)
            throws SQLException
    {
        return selectForDisplay(table, columnInfosList(table, select), parameters, filter, sort, rowCount, offset);
    }


    public static Results selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Map<String, Object> parameters, @Nullable Filter filter, @Nullable Sort sort, int rowCount, long offset)
            throws SQLException
    {
        return selectForDisplay(table, select, parameters, filter, sort, rowCount, offset, true, false);
    }

    public static Map<String, List<Aggregate.Result>>selectAggregatesForDisplay(TableInfo table, List<Aggregate> aggregates,
            Collection<ColumnInfo> select, @Nullable Map<String, Object> parameters, Filter filter, boolean cache) throws SQLException
    {
        return selectAggregatesForDisplay(table, aggregates, select, parameters, filter, cache, null);
    }

    private static Map<String, List<Aggregate.Result>> selectAggregatesForDisplay(TableInfo table, List<Aggregate> aggregates,
            Collection<ColumnInfo> select, Map<String, Object> parameters, Filter filter, boolean cache, @Nullable AsyncQueryRequest asyncRequest)
            throws SQLException
    {
        Map<String, ColumnInfo> columns = getDisplayColumnsList(select);
        ensureRequiredColumns(table, columns, filter, null, aggregates);
        SQLFragment innerSql = QueryService.get().getSelectSQL(table, new ArrayList<ColumnInfo>(columns.values()), filter, null, ALL_ROWS, NO_OFFSET, false);
        QueryService.get().bindNamedParameters(innerSql, parameters);
        QueryService.get().validateNamedParameters(innerSql);

        Map<String, ColumnInfo> columnMap = Table.createColumnMap(table, columns.values());

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        boolean first = true;

        for (Aggregate agg : aggregates)
        {
            if (agg.isCountStar() || columnMap.containsKey(agg.getColumnName()))
            {
                if (first)
                    first = false;
                else
                    sql.append(", ");
                sql.append(agg.getSQL(table.getSqlDialect(), columnMap));
            }
        }

        Map<String, List<Aggregate.Result>> results = new HashMap<String, List<Aggregate.Result>>();

        // if we didn't find any columns, then skip the SQL call completely
        if (first)
            return results;

        sql.append(" FROM (").append(innerSql.getSQL()).append(") S");

        Table.TableResultSet rs = null;
        try
        {
            rs = (Table.TableResultSet) executeQuery(table.getSchema(), sql.toString(), innerSql.getParams().toArray(), ALL_ROWS, NO_OFFSET, cache, false, asyncRequest, null, null);
            boolean next = rs.next();
            if (!next)
                throw new IllegalStateException("Expected a non-empty resultset from aggregate query.");
            for (Aggregate agg : aggregates)
            {
                if(!results.containsKey(agg.getColumnName()))
                    results.put(agg.getColumnName(), new ArrayList<Aggregate.Result>());

                results.get(agg.getColumnName()).add(agg.getResult(rs));
            }
            return results;
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {_log.error("unexpected error", e);}
        }
    }

    public static Map<String, List<Aggregate.Result>> selectAggregatesForDisplayAsync(final TableInfo table, final List<Aggregate> aggregates,
            final Collection<ColumnInfo> select, final @Nullable Map<String,Object> parameters, final Filter filter, final boolean cache, HttpServletResponse response)
            throws SQLException, IOException
    {
        final AsyncQueryRequest<Map<String, List<Aggregate.Result>>> asyncRequest = new AsyncQueryRequest<Map<String, List<Aggregate.Result>>>(response);
        return asyncRequest.waitForResult(new Callable<Map<String, List<Aggregate.Result>>>() {
            public Map<String, List<Aggregate.Result>> call() throws Exception
            {
                return selectAggregatesForDisplay(table, aggregates, select, parameters, filter, cache, asyncRequest);
            }
        });
    }


    public static Results selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Map<String,Object> parameters, @Nullable Filter filter, @Nullable Sort sort, int rowCount, long offset, boolean cache, boolean scrollable)
            throws SQLException
    {
        return selectForDisplay(table, select, parameters, filter, sort, rowCount, offset, cache, scrollable, null, null);
    }


    private static Results selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Map<String, Object> parameters, @Nullable Filter filter, @Nullable Sort sort, int rowCount, long offset, boolean cache, boolean scrollable, @Nullable AsyncQueryRequest asyncRequest, @Nullable Logger log)
            throws SQLException
    {
        assert Table.checkAllColumns(table, select, "selectForDisplay() select columns");
        Map<String, ColumnInfo> columns = getDisplayColumnsList(select);
        assert Table.checkAllColumns(table, columns.values(), "selectForDisplay() results of getDisplayColumnsList()");
        ensureRequiredColumns(table, columns, filter, sort, null);
        assert Table.checkAllColumns(table, columns.values(), "selectForDisplay() after ensureRequiredColumns");

        long queryOffset = offset, scrollOffset = 0;
        int queryRowCount = rowCount;

        if (offset > 0 && !table.getSqlDialect().supportsOffset())
        {
            queryOffset = 0;
            scrollOffset = offset;
            queryRowCount = rowCount + (int)offset;
        }

        int decideRowCount = decideRowCount(queryRowCount, null);
        SQLFragment sql = QueryService.get().getSelectSQL(table, new ArrayList<ColumnInfo>(columns.values()), filter, sort, decideRowCount, queryOffset, true);
        QueryService.get().bindNamedParameters(sql, parameters);
        QueryService.get().validateNamedParameters(sql);
        Integer statementRowCount = (table.getSqlDialect().requiresStatementMaxRows() ? decideRowCount : null);  // TODO: clean this all up
        Table.TableResultSet rs = (Table.TableResultSet)executeQuery(table.getSchema(), sql.getSQL(), sql.getParams().toArray(), rowCount, scrollOffset, cache, scrollable, asyncRequest, log, statementRowCount);

        return new ResultsImpl(rs, columns.values());
    }


    private static int decideRowCount(int rowcount, @Nullable Class clazz)
    {
        if (ALL_ROWS == rowcount || NO_ROWS == rowcount)
            return rowcount;

        // add 1 to count so we can set isComplete()
        if (null == clazz || java.sql.ResultSet.class.isAssignableFrom(clazz))
            return rowcount + 1;

        return rowcount;
    }


    public static Results selectForDisplayAsync(final TableInfo table, final Collection<ColumnInfo> select, Map<String,Object> parameters, final @Nullable Filter filter, final @Nullable Sort sort, final int rowCount, final long offset, final boolean cache, final boolean scrollable, HttpServletResponse response) throws SQLException, IOException
    {
        final Logger log = ConnectionWrapper.getConnectionLogger();
        final AsyncQueryRequest<Results> asyncRequest = new AsyncQueryRequest<Results>(response);
        final Map<String,Object> parametersCopy = new CaseInsensitiveHashMap<Object>();
        if (null != parameters)
            parametersCopy.putAll(parameters);
        return asyncRequest.waitForResult(new Callable<Results>()
		{
            public Results call() throws Exception
            {
                return selectForDisplay(table, select, parametersCopy, filter, sort, rowCount, offset, cache, scrollable, asyncRequest, log);
            }
        });
    }


    public static <K> K[] selectForDisplay(TableInfo table, Set<String> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss)
            throws SQLException
    {
        return selectForDisplay(table, columnInfosList(table, select), filter, sort, clss);
    }


    public static <K> K[] selectForDisplay(TableInfo table, Collection<ColumnInfo> select, @Nullable Filter filter, @Nullable Sort sort, Class<K> clss)
            throws SQLException
    {
        Map<String, ColumnInfo> columns = getDisplayColumnsList(select);
        ensureRequiredColumns(table, columns, filter, sort, null);
        SQLFragment sql = QueryService.get().getSelectSQL(table, new ArrayList<ColumnInfo>(columns.values()), filter, sort, ALL_ROWS, NO_OFFSET, true);

        return new LegacySqlSelector(table.getSchema(), sql).getArray(clss);
    }


    static List<ColumnInfo> columnInfosList(TableInfo table, Set<String> select)
    {
        List<ColumnInfo> allColumns = table.getColumns();
        List<ColumnInfo> selectColumns;

        if (select == ALL_COLUMNS)
            selectColumns = allColumns;
        else
        {
            select = new CaseInsensitiveHashSet(select);
            List<ColumnInfo> selectList = new ArrayList<ColumnInfo>();      // TODO: Just use selectColumns
            for (ColumnInfo column : allColumns)
            {
                if (select != ALL_COLUMNS && !select.contains(column.getName()) && !select.contains(column.getPropertyName()))
                    continue;
                selectList.add(column);
            }
            selectColumns = selectList;
        }
        return selectColumns;
    }


    private static Map<String,ColumnInfo> getDisplayColumnsList(Collection<ColumnInfo> arrColumns)
    {
        Map<String, ColumnInfo> columns = new LinkedHashMap<String, ColumnInfo>();
        ColumnInfo existing;
        for (ColumnInfo column : arrColumns)
        {
            existing = columns.get(column.getAlias());
            assert null == existing || existing.getName().equals(column.getName()) : existing.getName() + " != " + column.getName();
            columns.put(column.getAlias(), column);
            ColumnInfo displayColumn = column.getDisplayField();
            if (displayColumn != null)
            {
                existing = columns.get(displayColumn.getAlias());
                assert null == existing || existing.getName().equals(displayColumn.getName());
                columns.put(displayColumn.getAlias(), displayColumn);
            }
        }
        return columns;
    }


    public static void ensureRequiredColumns(TableInfo table, Map<String, ColumnInfo> cols, @Nullable Filter filter, @Nullable Sort sort, @Nullable List<Aggregate> aggregates)
    {
        List<ColumnInfo> allColumns = table.getColumns();
        Set<String> requiredColumns = new HashSet();

        if (null != filter)
            requiredColumns.addAll(filter.getWhereParamNames());

        if (null != sort)
        {
            requiredColumns.addAll(sort.getRequiredColumnNames(cols));
        }

        if (null != aggregates)
        {
            // UNDONE: use fieldkeys
            for (Aggregate agg : aggregates)
                requiredColumns.add(agg.getColumnName());
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
        SQLFragment sqlSelect = Table.getSelectSQL(tinfo, null, null, null);
        SQLFragment sqlSelectInto = new SQLFragment();
        sqlSelectInto.append("SELECT * INTO ").append(tableName).append(" FROM (");
        sqlSelectInto.append(sqlSelect);
        sqlSelectInto.append(") _from_");

        Table.execute(tinfo.getSchema(), sqlSelectInto);
    }


    public static boolean isEmpty(TableInfo tinfo) throws SQLException
    {
        return rowCount(tinfo) == 0;
    }


    @Deprecated  // Use TableSelector.getRowCount() directly
    public static long rowCount(TableInfo tinfo) throws SQLException
    {
        return new LegacyTableSelector(tinfo).getRowCount();
    }


    static TableResultSet cacheResultSet(SqlDialect dialect, ResultSet rs, int rowCount, @Nullable AsyncQueryRequest asyncRequest) throws SQLException
    {
        CachedResultSet crs = new CachedResultSet(rs, dialect.shouldCacheMetaData(), rowCount);

        if (null != asyncRequest && AppProps.getInstance().isDevMode())
            crs.setStackTrace(asyncRequest.getCreationStackTrace());

        return crs;
    }


    public interface TableResultSet extends ResultSet, Iterable<Map<String, Object>>
    {
        public boolean isComplete();

//        public boolean supportsGetRowMap();
        
        public Map<String, Object> getRowMap() throws SQLException;

        public Iterator<Map<String, Object>> iterator();

        String getTruncationMessage(int maxRows);

        /** @return the number of rows in the result set. -1 if unknown */
        int getSize();
    }


    public static class ResultSetImpl extends ResultSetWrapper implements TableResultSet
    {
        private final @Nullable DbSchema _schema;
        private final @Nullable Connection _connection;
        private int _maxRows = ALL_ROWS;

        private boolean _isComplete = true;

        // for resource tracking
        private Throwable _debugCreated = null;
        protected boolean _wasClosed = false;


        public ResultSetImpl(ResultSet rs)
        {
            this(rs, ALL_ROWS);
        }


        public ResultSetImpl(ResultSet rs, int maxRows)
        {
            this(null, null, rs, maxRows);
        }


        public ResultSetImpl(Connection connection, DbSchema schema, ResultSet rs)
        {
            this(connection, schema, rs, ALL_ROWS);
        }

        public ResultSetImpl(@Nullable Connection connection, @Nullable DbSchema schema, ResultSet rs, int maxRows)
        {
            super(rs);
            assert MemTracker.put(this);
            //noinspection ConstantConditions
            assert null != (_debugCreated = new Throwable("created ResultSetImpl"));
            _maxRows = maxRows;
            _connection = connection;
            _schema = schema;
        }


        public void setMaxRows(int i)
        {
            _maxRows = i;
        }


        public boolean isComplete()
        {
            return _isComplete;
        }


        void setComplete(boolean isComplete)
        {
            _isComplete = isComplete;
        }

        @Override
        public int getSize()
        {
            return -1;
        }

        public boolean next() throws SQLException
        {
            boolean success = super.next();
            if (!success || ALL_ROWS == _maxRows)
                return success;
            if (getRow() == _maxRows + 1)
            {
                _isComplete = false;
            }
            return getRow() <= _maxRows;
        }


        public void close() throws SQLException
        {
            // Uncached case... close everything down
            if (null != _schema)
            {
                Statement stmt = getStatement();
                super.close();
                if (stmt != null)
                {
                    stmt.close();
                }
                _schema.getScope().releaseConnection(_connection);
            }
            else
                super.close();

            _wasClosed = true;
        }


        public int size() throws SQLException
        {
            if (resultset instanceof CachedRowSet)
                return ((CachedRowSet) resultset).size();
            return -1;
        }


        public Iterator<Map<String, Object>> iterator()
        {
            return new ResultSetIterator(this);
        }

        public String getTruncationMessage(int maxRows)
        {
            return "Displaying only the first " + maxRows + " rows.";
        }

        public Map<String, Object> getRowMap()
        {
            throw new UnsupportedOperationException("getRowMap()");
        }


        protected void finalize() throws Throwable
        {
            if (!_wasClosed)
            {
                close();
                if (null != _debugCreated)
                    _log.error("ResultSet was not closed", _debugCreated);
            }
            super.finalize();
        }
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


    private static void _logQuery(Level level, String sql, @Nullable Object[] parameters, Connection conn)
    {
        if (!_log.isEnabledFor(level))
            return;

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
        TreeSet<Object> keys = new TreeSet<Object>();
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

        Map[] right = new LegacySqlSelector(schema, new SQLFragment(inSQL, keys.toArray())).getArray(Map.class);
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

            ResultSet rs = select(tinfo, ALL_COLUMNS, null, null);
            rs.close();

            Map[] maps = select(tinfo, ALL_COLUMNS, null, null, Map.class);
            assertNotNull(maps);

            Principal[] principals = select(tinfo, ALL_COLUMNS, null, null, Principal.class);
            assertNotNull(principals);
            assertTrue(principals.length > 0);
            assertTrue(principals[0]._userId != 0);
            assertNotNull(principals[0]._name);
        }


        @Test
        public void testMaxRows() throws SQLException
        {
            TableInfo tinfo = _core.getTableInfoPrincipals();

            Results rsAll = Table.selectForDisplay(tinfo, ALL_COLUMNS, null, null, null, ALL_ROWS, NO_OFFSET);
            rsAll.last();
            int rowCount = rsAll.getRow();
            assertTrue(((Table.TableResultSet)rsAll.getResultSet()).isComplete());
            rsAll.close();

            rowCount -= 2;
            Results rs = Table.selectForDisplay(tinfo, ALL_COLUMNS, null, null, null, rowCount, NO_OFFSET);
            rs.last();
            int row = rs.getRow();
            assertTrue(row == rowCount);
            assertFalse(((Table.TableResultSet)rs.getResultSet()).isComplete());
            rs.close();
        }


        @Test
        public void testMapJoin()
        {
            ArrayList<Map> left = new ArrayList<Map>();
            left.add(_quickMap("id=1&A=1"));
            left.add(_quickMap("id=2&A=2"));
            left.add(_quickMap("id=3&A=3"));
            left.add(_quickMap("id=4&A=1"));
            left.add(_quickMap("id=5&A=2"));
            left.add(_quickMap("id=6&A=3"));
            ArrayList<Map> right = new ArrayList<Map>();
            right.add(_quickMap("id=HIDDEN&A=1&B=one"));
            right.add(_quickMap("id=HIDDEN&A=2&B=two"));
            right.add(_quickMap("id=HIDDEN&A=3&B=three"));

            Collection<Map> join = Join.join(left, right, "A");
            Set<String> idSet = new HashSet<String>();
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
        public void testSqlJoin()
                throws SQLException
        {
            //UNDONE
            // SELECT MEMBERS
            // Join(MEMBERS, "SELECT * FROM Principals where UserId IN (?)"

            CoreSchema core = CoreSchema.getInstance();
            DbSchema schema = core.getSchema();
            TableInfo membersTable = core.getTableInfoMembers();
            TableInfo principalsTable = core.getTableInfoPrincipals();

            Map[] members = executeQuery(schema, "SELECT * FROM " + membersTable.getSelectName(), null, Map.class);
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
                        "(s VARCHAR(36), d " + dialect.sqlTypeNameFromSqlType(JdbcType.TIMESTAMP.sqlType) + ")");
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


        private Map<String, String> _quickMap(String q)
        {
            Map<String, String> m = new HashMap<String, String>();
            Pair[] pairs = PageFlowUtil.fromQueryString(q);
            for (Pair p : pairs)
                m.put((String)p.first, (String)p.second);
            return m;
        }
    }


    static public LinkedHashMap<FieldKey, ColumnInfo> createFieldKeyMap(TableInfo table)
    {
        LinkedHashMap<FieldKey,ColumnInfo> ret = new LinkedHashMap<FieldKey,ColumnInfo>();
        for (ColumnInfo column : table.getColumns())
        {
            ret.put(column.getFieldKey(), column);
        }
        return ret;
    }
    

    static public Map<String, ColumnInfo> createColumnMap(TableInfo table, @Nullable Collection<ColumnInfo> columns)
    {
        CaseInsensitiveHashMap<ColumnInfo> ret = new CaseInsensitiveHashMap<ColumnInfo>();
        if (columns != null)
        {
            for (ColumnInfo column : columns)
            {
                ret.put(column.getName(), column);
            }
        }
        if (table != null)
        {
            for (String name : table.getColumnNameSet())
            {
                if (ret.containsKey(name))
                    continue;
                ColumnInfo column = table.getColumn(name);
                if (column != null)
                {
                    ret.put(name, column);
                }
            }
        }
        return ret;
    }


    public static boolean checkAllColumns(TableInfo table, Collection<ColumnInfo> columns, String prefix)
    {
        int bad = 0;

        for (ColumnInfo column : columns)
            if (!checkColumn(table, column, prefix))
                bad++;

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
        Logger log = Logger.getLogger(Table.class);

        if (column.getParentTable() != table)
        {
            log.warn(prefix + ": Column " + column + " is from the wrong table: " + column.getParentTable() + " instead of " + table);
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
        List<Parameter> paramPK = new ArrayList<Parameter>(columnPK.size());
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

        return new Parameter.ParameterMap(d, conn, sqlfDelete, updatable.remapSchemaColumns());
    }


    public static class TestDataIterator extends AbstractDataIterator
    {
        final String guid = GUID.makeGUID();
        final Date date = new Date();

        // TODO: guid values are ignored, since guidCallable gets used instead
        final Object[][] _data = new Object[][]
        {
            new Object[] {1, "One", 101, true, date, guid},
            new Object[] {2, "Two", 102, true, date, guid},
            new Object[] {3, "Three", 103, true, date, guid}
        };

        static class _ColumnInfo extends ColumnInfo
        {
            _ColumnInfo(String name, JdbcType type)
            {
                super(name, type);
                setReadOnly(true);
            }
        }

        static ColumnInfo[] _cols = new ColumnInfo[]
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

            BatchValidationException errors = new BatchValidationException();
            TestDataIterator extract = new TestDataIterator();
            SimpleTranslator translate = new SimpleTranslator(extract, errors);
            translate.selectAll();
            translate.addBuiltInColumns(JunitUtil.getTestContainer(), TestContext.get().getUser(), testTable, false);

            TableInsertDataIterator load = TableInsertDataIterator.create(
                    translate,
                    testTable,
                    errors
            );
            new Pump((DataIteratorBuilder)load, errors).run();

            assertFalse(errors.hasErrors());
            
            Table.execute(testTable.getSchema(), "DELETE FROM test.testtable WHERE EntityId = '" + extract.guid + "'");
        }
    }
}
