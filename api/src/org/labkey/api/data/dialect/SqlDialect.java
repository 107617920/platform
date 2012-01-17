/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data.dialect;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.SystemMaintenance;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Isolate the big SQL differences between database servers. A new SqlDialect instance is created for each DbScope; the
// dialect holds state specific to each database, for example, reserved words, user defined type information, etc.
public abstract class SqlDialect
{
    private static final Logger LOG = Logger.getLogger(SqlDialect.class);
    private DialectStringHandler _stringHandler = null;

    public static final String GENERIC_ERROR_MESSAGE = "The database experienced an unexpected problem. Please check your input and try again.";
    protected static final String INPUT_TOO_LONG_ERROR_MESSAGE = "The input you provided was too long.";

    private final Set<String> _reservedWordSet;
    private final Map<String, Integer> _sqlTypeNameMap = new CaseInsensitiveHashMap<Integer>();
    private final Map<Integer, String> _sqlTypeIntMap = new HashMap<Integer, String>();

    protected SqlDialect()
    {
        initializeSqlTypeNameMap();
        initializeSqlTypeIntMap();
        _reservedWordSet = getReservedWords();

        assert MemTracker.put(this);
    }


    protected abstract @NotNull Set<String> getReservedWords();

    protected String getOtherDatabaseThreads()
    {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet())
        {
            Thread thread = entry.getKey();
            // Dump out any thread that was talking to the database
            Set<Integer> spids = ConnectionWrapper.getSPIDsForThread(thread);

            if (!spids.isEmpty())
            {
                if (sb.length() == 0)
                {
                    sb.append("Other threads with active database connections:\n");
                }
                else
                {
                    sb.append("\n");
                }

                sb.append(thread.getName());
                sb.append(", SPIDs = ");
                sb.append(spids);
                sb.append("\n");

                for (StackTraceElement stackTraceElement : entry.getValue())
                {
                    sb.append("\t");
                    sb.append(stackTraceElement);
                    sb.append("\n");
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : "No other threads with active database connections to report.";
    }

    public abstract String getBooleanLiteral(boolean b);

    static
    {
        SystemMaintenance.addTask(new DatabaseMaintenanceTask());
    }


    private void initializeSqlTypeNameMap()
    {
        _sqlTypeNameMap.put("ARRAY", Types.ARRAY);
        _sqlTypeNameMap.put("BIGINT", Types.BIGINT);
        _sqlTypeNameMap.put("BINARY", Types.BINARY);
        _sqlTypeNameMap.put("BIT", Types.BIT);
        _sqlTypeNameMap.put("BLOB", Types.BLOB);
        _sqlTypeNameMap.put("BOOLEAN", Types.BOOLEAN);
        _sqlTypeNameMap.put("CHAR", Types.CHAR);
        _sqlTypeNameMap.put("CLOB", Types.CLOB);
        _sqlTypeNameMap.put("DATALINK", Types.DATALINK);
        _sqlTypeNameMap.put("DATE", Types.DATE);
        _sqlTypeNameMap.put("DECIMAL", Types.DECIMAL);
        _sqlTypeNameMap.put("DISTINCT", Types.DISTINCT);
        _sqlTypeNameMap.put("DOUBLE", Types.DOUBLE);
        _sqlTypeNameMap.put("DOUBLE PRECISION", Types.DOUBLE);
        _sqlTypeNameMap.put("INTEGER", Types.INTEGER);
        _sqlTypeNameMap.put("INT", Types.INTEGER);
        _sqlTypeNameMap.put("JAVA_OBJECT", Types.JAVA_OBJECT);
        _sqlTypeNameMap.put("LONGVARBINARY", Types.LONGVARBINARY);
        _sqlTypeNameMap.put("LONGVARCHAR", Types.LONGVARCHAR);
        _sqlTypeNameMap.put("NULL", Types.NULL);
        _sqlTypeNameMap.put("NUMERIC", Types.NUMERIC);
        _sqlTypeNameMap.put("OTHER", Types.OTHER);
        _sqlTypeNameMap.put("REAL", Types.REAL);
        _sqlTypeNameMap.put("REF", Types.REF);
        _sqlTypeNameMap.put("SMALLINT", Types.SMALLINT);
        _sqlTypeNameMap.put("STRUCT", Types.STRUCT);
        _sqlTypeNameMap.put("TIME", Types.TIME);
        _sqlTypeNameMap.put("TINYINT", Types.TINYINT);
        _sqlTypeNameMap.put("VARBINARY", Types.VARBINARY);
        _sqlTypeNameMap.put("VARCHAR", Types.VARCHAR);

        addSqlTypeNames(_sqlTypeNameMap);
    }


    private void initializeSqlTypeIntMap()
    {
        _sqlTypeIntMap.put(Types.ARRAY, "ARRAY");
        _sqlTypeIntMap.put(Types.BIGINT, "BIGINT");
        _sqlTypeIntMap.put(Types.BINARY, "BINARY");
        _sqlTypeIntMap.put(Types.BLOB, "BLOB");
        _sqlTypeIntMap.put(Types.CLOB, "CLOB");
        _sqlTypeIntMap.put(Types.DATALINK, "DATALINK");
        _sqlTypeIntMap.put(Types.DATE, "DATE");
        _sqlTypeIntMap.put(Types.DECIMAL, "DECIMAL");
        _sqlTypeIntMap.put(Types.DISTINCT, "DISTINCT");
        _sqlTypeIntMap.put(Types.INTEGER, "INTEGER");
        _sqlTypeIntMap.put(Types.JAVA_OBJECT, "JAVA_OBJECT");
        _sqlTypeIntMap.put(Types.NULL, "NULL");
        _sqlTypeIntMap.put(Types.NUMERIC, "NUMERIC");
        _sqlTypeIntMap.put(Types.OTHER, "OTHER");
        _sqlTypeIntMap.put(Types.REAL, "REAL");
        _sqlTypeIntMap.put(Types.REF, "REF");
        _sqlTypeIntMap.put(Types.SMALLINT, "SMALLINT");
        _sqlTypeIntMap.put(Types.STRUCT, "STRUCT");
        _sqlTypeIntMap.put(Types.TIME, "TIME");
        _sqlTypeIntMap.put(Types.TINYINT, "TINYINT");
        _sqlTypeIntMap.put(Types.VARBINARY, "VARBINARY");

        addSqlTypeInts(_sqlTypeIntMap);
    }


    protected abstract void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap);
    protected abstract void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap);

    public int sqlTypeIntFromSqlTypeName(String sqlTypeName)
    {
        Integer i = _sqlTypeNameMap.get(sqlTypeName);

        if (null != i)
            return i;
        else
        {
            LOG.info("Unknown SQL Type Name \"" + sqlTypeName + "\"; using String instead.");
            return Types.OTHER;
        }
    }


    public String sqlTypeNameFromSqlType(int sqlType)
    {
        String sqlTypeName = _sqlTypeIntMap.get(sqlType);

        return null != sqlTypeName ? sqlTypeName : "OTHER";
    }

    protected abstract String sqlTypeNameFromSqlType(PropertyStorageSpec prop);

    // Should we cache ResultSetMetaData for this dialect's ResultSets?  In other words, does the database server
    // prevent access to ResultSetMetaData after the underlying ResultSet has been closed?
    public boolean shouldCacheMetaData()
    {
        // Most database servers allow access to result set meta data
        return false;
    }

    protected String getDatabaseMaintenanceSql()
    {
        return null;
    }


    /**
     * @return any additional information that should be sent to the mothership in the case of a SQLException
     */
    public String getExtraInfo(SQLException e)
    {
        return null;
    }

    public static boolean isConstraintException(SQLException x)
    {
        String sqlState = x.getSQLState();
        if (null == sqlState || !sqlState.startsWith("23"))
            return false;
        return sqlState.equals("23000") || sqlState.equals("23505") || sqlState.equals("23503");
    }


    public static boolean isCancelException(SQLException x)
    {
        String sqlState = x.getSQLState();
        if (null == sqlState || !sqlState.startsWith("57"))
            return false;
        return sqlState.equals("57014"); // TODO verify SQL Server
    }


    public static boolean isTransactionException(SQLException x)
    {
        String msg = StringUtils.defaultString(x.getMessage(),"");
        String state = StringUtils.defaultString(((SQLException)x).getSQLState(),"");
        return -1 != msg.toLowerCase().indexOf("deadlock") || state.startsWith("25") || state.startsWith("40");
    }


    // Do dialect-specific work for this new data source.
    public void prepareNewDbScope(DbScope scope) throws SQLException, IOException
    {
        initialize();
    }

    // Post construction initiatization that doesn't require a scope
    void initialize()
    {
        _stringHandler = createStringHandler();
    }

    // Called once when new scope is being prepared
    protected DialectStringHandler createStringHandler()
    {
        return new StandardDialectStringHandler();
    }

    public DialectStringHandler getStringHandler()
    {
        return _stringHandler;
    }

    // Set of keywords returned by DatabaseMetaData.getMetaData() plus the SQL 2003 keywords
    protected Set<String> getJdbcKeywords(Connection conn) throws SQLException, IOException
    {
        Set<String> keywordSet = new CaseInsensitiveHashSet();
        keywordSet.addAll(KeywordCandidates.get().getSql2003Keywords());
        String keywords = conn.getMetaData().getSQLKeywords();
        keywordSet.addAll(new CsvSet(keywords));

        return keywordSet;
    }

    protected abstract String getProductName();

    public abstract String getSQLScriptPath();

    public abstract void appendStatement(StringBuilder sql, String statement);

    public abstract void appendSelectAutoIncrement(StringBuilder sql, TableInfo table, String columnName);

    // String version for convenience
    @Deprecated  // Most usages assume this is a standalone executable statement, which is a bad assumption (for PostgreSQL)
    public String appendSelectAutoIncrement(String sql, TableInfo tinfo, String columnName)
    {
        StringBuilder sbSql = new StringBuilder(sql);
        appendSelectAutoIncrement(sbSql, tinfo, columnName);
        return sbSql.toString();
    }

    public abstract @Nullable ResultSet executeInsertWithResults(@NotNull PreparedStatement stmt) throws SQLException;

    public abstract boolean requiresStatementMaxRows();

    /**
     * Limit a SELECT query to the specified number of rows (Table.ALL_ROWS == no limit).
     * @param sql a SELECT query
     * @param rowCount return the first rowCount number of rows (Table.ALL_ROWS == no limit).
     * @return the query
     */
    public abstract SQLFragment limitRows(SQLFragment sql, int rowCount);

    public void limitRows(StringBuilder builder, int rowCount)
    {
        SQLFragment frag = new SQLFragment();
        frag.append(builder);
        limitRows(frag, rowCount);
        builder.replace(0, builder.length(), frag.getSQL());
    }

    /**
     * Composes the fragments into a SQL query that will be limited by rowCount
     * starting at the given 0-based offset.
     * 
     * @param select must not be null
     * @param from must not be null
     * @param filter may be null
     * @param order may be null
     * @param groupBy may be null
     * @param rowCount Table.ALL_ROWS means all rows, 0 (Table.NO_ROWS) means no rows, > 0 limits result set
     * @param offset 0 based   @return the query
     */
    public abstract SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int rowCount, long offset);

    // Some databases lack true schema support; if true, we'll map the database's catalogs to schemas
    public abstract boolean treatCatalogsAsSchemas();

    /** Does the dialect support limitRows() with an offset? */
    public abstract boolean supportsOffset();

    public abstract boolean supportsComments();

    public abstract String execute(DbSchema schema, String procedureName, String parameters);

    public abstract SQLFragment execute(DbSchema schema, String procedureName, SQLFragment parameters);

    public abstract String concatenate(String... args);

    public abstract SQLFragment concatenate(SQLFragment... args);

    /**
     * Return the operator which, in addition to the usual LIKE things ('%' and '_'), also supports
     * character classes. (i.e. [abc] matching a,b or c)
     * If you do not need the functionality of character classes, then "LIKE" will work just fine with all SQL dialects.
     */
    public abstract String getCharClassLikeOperator();

    public abstract String getCaseInsensitiveLikeOperator();

    public abstract String getVarcharLengthFunction();

    public abstract String getStdDevFunction();

    public abstract String getClobLengthFunction();

    public abstract String getStringIndexOfFunction(String stringToFind, String stringToSearch);

    public abstract String getSubstringFunction(String s, String start, String length);

    public abstract boolean supportsGroupConcat();

    // GroupConcat is usable as an aggregate function within a GROUP BY
    public abstract SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted);

    public abstract boolean supportsSelectConcat();

    // SelectConcat returns SQL that will generate a comma separated list of the results from the passed in select SQL.
    // This is not generally usable within a GROUP BY.  Include distinct, order by, etc. in the selectSql if desired
    public abstract SQLFragment getSelectConcat(SQLFragment selectSql);

    public abstract void runSql(DbSchema schema, String sql, UpgradeCode upgradeCode, ModuleContext moduleContext) throws SQLException;

    public abstract String getMasterDataBaseName();

    public abstract String getDefaultDateTimeDataType();

    public abstract String getUniqueIdentType();

    public abstract String getGuidType();

    public abstract String getTempTableKeyword();

    public abstract String getTempTablePrefix();

    public abstract String getGlobalTempTablePrefix();

    public abstract boolean isNoDatabaseException(SQLException e);

    public abstract boolean isSortableDataType(String sqlDataTypeName);

    public abstract String getDropIndexCommand(String tableName, String indexName);

    public abstract String getCreateDatabaseSql(String dbName);

    public abstract String getCreateSchemaSql(String schemaName);

    /** @param part the java.util.Calendar field for the unit of time, such as Calendar.DATE or Calendar.MINUTE */
    public abstract String getDateDiff(int part, String value1, String value2);

    /** @param part the java.util.Calendar field for the unit of time, such as Calendar.DATE or Calendar.MINUTE */
    public abstract String getDatePart(int part, String value);

    /** @param expression The expression with datetime value for which a date value is desired */
    public abstract String getDateTimeToDateCast(String expression);

    public abstract String getRoundFunction(String valueToRound);


    // does provider support ROUND(double,x) where x != 0
    public abstract boolean supportsRoundDouble();

    // Do nothing by default
    public void prepareNewDatabase(DbSchema schema) throws ServletException
    {
    }

    public void handleCreateDatabaseException(SQLException e) throws ServletException
    {
        throw(new ServletException("Can't create database", e));
    }

    /**
     * Wrap one or more INSERT statements to allow explicit specification
     * of values for auto-incrementing columns (e.g. IDENTITY in SQL Server
     * or SERIAL in Postgres). The input StringBuffer is modified to
     * wrap the statements in dialect-specific code to allow this.
     *
     * @param statements the insert statements. If more than one,
     *                   they must have been joined by appendStatement
     *                   and must all refer to the same table.
     * @param tinfo      table used in the insert(s)
     */
    public abstract void overrideAutoIncrement(StringBuilder statements, TableInfo tinfo);

    protected String getSystemTableNames()
    {
        return "";
    }

    private Set<String> systemTableSet = Sets.newCaseInsensitiveHashSet(new CsvSet(getSystemTableNames()));

    public boolean isSystemTable(String tableName)
    {
        return systemTableSet.contains(tableName);
    }

    public abstract boolean isSystemSchema(String schemaName);

    public boolean isReserved(String word)
    {
        return _reservedWordSet.contains(word);
    }


    public String getColumnSelectName(String columnName)
    {
        // Special case "*"... otherwise, just makeLegalIdentifier()
        if ("*".equals(columnName))
            return columnName;
        else
            return makeLegalIdentifier(columnName);
    }


    // Translates database metadata name into a name that can be used in a select.  Most dialects simply turn them into
    // legal identifiers (e.g., adding quotes if special symbols are present).
    public String getSelectNameFromMetaDataName(String metaDataName)
    {
        return makeLegalIdentifier(metaDataName);
    }


    // If necessary, quote identifier
    public String makeLegalIdentifier(String id)
    {
        if (shouldQuoteIdentifier(id))
            return quoteIdentifier(id);
        else
            return id;
    }


    // Escape quotes and quote the identifier  // TODO: Move to DialectStringHandler?
    public String quoteIdentifier(String id)
    {
        return "\"" + id.replaceAll("\"", "\"\"") + "\"";
    }


    protected boolean shouldQuoteIdentifier(String id)
    {
        return isReserved(id) || !AliasManager.isLegalName(id);
    }


    public void testDialectKeywords(Connection conn)
    {
        Set<String> candidates = KeywordCandidates.get().getCandidates();
        Set<String> shouldAdd = new TreeSet<String>();
        Set<String> shouldRemove = new TreeSet<String>();

        // First, test the test: execute the test SQL with an identifier that definitely isn't a keyword.  If this
        // fails, there's a syntax issue with the test SQL.
        if (isKeyword(conn, "abcdefghi"))
            throw new IllegalStateException("Legitimate keyword generated an error on " + getProductName());

        for (String candidate : candidates)
        {
            boolean reserved = isKeyword(conn, candidate);

            if (isReserved(candidate) != reserved)
            {
                if (reserved)
                {
                    if (!_reservedWordSet.contains(candidate))
                        shouldAdd.add(candidate);
                }
                else
                {
                    if (_reservedWordSet.contains(candidate))
                        shouldRemove.add(candidate);
                }
            }
        }

        if (!shouldAdd.isEmpty())
            throw new IllegalStateException("Need to add " + shouldAdd.size() + " keywords to " + getProductName() + " reserved word list: " + shouldAdd);

        // Removing this check because reserved words differ between postgres versions.  For example, going from 8.4 to
        // 9.0 adds 'concurrently' but removes 'between', 'new', 'off', and 'old'.  With this check removed, the test
        // now checks to ensure that a superset of all required keywords is present, but no longer enforces that the
        // sets are a perfect match.
        //if (!shouldRemove.isEmpty())
        //    throw new IllegalStateException("Need to remove " + shouldRemove.size() + " keywords from " + getProductName() + " reserved word list: " + shouldRemove);

        if (!shouldRemove.isEmpty())
            LOG.info("Should remove " + shouldRemove.size() + " keywords from " + getClass().getName() + " reserved word list: " + shouldRemove);
    }


    protected boolean isKeyword(Connection conn, String candidate)
    {
        String sql = getIdentifierTestSql(candidate);

        try
        {
            Table.execute(conn, sql);
            return false;
        }
        catch (SQLException e)
        {
            return true;
        }
    }


    public void testKeywordCandidates(Connection conn) throws IOException, SQLException
    {
        Set<String> jdbcKeywords = getJdbcKeywords(conn);

        if (!KeywordCandidates.get().containsAll(jdbcKeywords, getProductName()))
            throw new IllegalStateException("JDBC keywords from " + getProductName() + " are not all in the keyword candidate list (sqlKeywords.txt)");

        if (!KeywordCandidates.get().containsAll(_reservedWordSet, getProductName()))
            throw new IllegalStateException(getProductName() + " reserved words are not all in the keyword candidate list (sqlKeywords.txt)");
    }


    protected String getIdentifierTestSql(String candidate)
    {
        String keyword = getTempTableKeyword();
        String name = getTempTablePrefix() + candidate;

        return "SELECT " + candidate + " FROM (SELECT 1 AS " + candidate + ") x ORDER BY " + candidate + ";\n" +
               "CREATE " + keyword + " TABLE " + name + " (" + candidate + " VARCHAR(50));\n" +
               "DROP TABLE " + name + ";";
    }


    public final void checkSqlScript(String sql) throws SQLSyntaxException
    {
        Collection<String> errors = new ArrayList<String>();
        String lower = sql.toLowerCase();
        String lowerNoWhiteSpace = lower.replaceAll("\\s", "");

        if (lowerNoWhiteSpace.contains("primarykey,"))
            errors.add("Do not designate PRIMARY KEY on the column definition line; this creates a PK with an arbitrary name, making it more difficult to change later.  Instead, create the PK as a named constraint (e.g., PK_MyTable).");

        checkSqlScript(lower, lowerNoWhiteSpace, errors);

        if (!errors.isEmpty())
            throw new SQLSyntaxException(errors);
    }


    abstract protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors);

    protected class SQLSyntaxException extends SQLException
    {
        private Collection<String> _errors;

        protected SQLSyntaxException(Collection<String> errors)
        {
            _errors = errors;
        }

        @Override
        public String getMessage()
        {
            return StringUtils.join(_errors.iterator(), '\n');
        }
    }

    /**
     * Transform the JDBC error message into something the user is more likely
     * to understand.
     */
    public abstract String sanitizeException(SQLException ex);

    public abstract String getAnalyzeCommandForTable(String tableName);

    protected abstract String getSIDQuery();

    public Integer getSPID(Connection conn) throws SQLException
    {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = conn.prepareStatement(getSIDQuery());
            rs = stmt.executeQuery();
            if (!rs.next())
            {
                throw new SQLException("SID query returned no results");
            }
            return rs.getInt(1);
        }
        finally
        {
            if (stmt != null) { try { stmt.close(); } catch (SQLException e) {} }
            if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
        }
    }

    public boolean updateStatistics(TableInfo table) throws SQLException
    {
        String sql = getAnalyzeCommandForTable(table.getSelectName());
        if (sql != null)
        {
            Table.execute(table.getSchema(), sql);
            return true;
        }
        else
            return false;
    }


    public abstract String getBooleanDataType();
    
    public String getBooleanTRUE()
    {
        return "CAST(1 AS " + getBooleanDataType() + ")";
    }

    public String getBooleanFALSE()
    {
        return "CAST(0 AS " + getBooleanDataType() + ")";
    }


    // We need to determine the database name from a data source, so we've implemented a helper that parses
    // the JDBC connection string for each driver we support.  This is necessary because, unfortunately, there
    // appears to be no standard, reliable way to ask a JDBC driver for individual components of the URL or
    // to programmatically assemble a new connection URL.  Driver.getPropertyInfo(), for example, doesn't
    // return the database name on PostgreSQL if it's specified as part of the URL.
    //
    // Currently, JdbcHelper only finds the database name.  It could be extended if we require querying
    // other components or if replacement/reassembly becomes necessary.
    public String getDatabaseName(String dsName, DataSource ds) throws ServletException
    {
        try
        {
            DataSourceProperties props = new DataSourceProperties(dsName, ds);
            String url = props.getUrl();
            return getDatabaseName(url);
        }
        catch (Exception e)
        {
            throw new ServletException("Error retrieving database name from DataSource", e);
        }
    }


    public String getDatabaseName(String url) throws ServletException
    {
        return getJdbcHelper().getDatabase(url);
    }


    public abstract JdbcHelper getJdbcHelper();

    /**
     * Drop a schema if it exists.
     * Throws an exception if schema exists, and could not be dropped. 
     */
    public void dropSchema(DbSchema schema, String schemaName) throws SQLException
    {
        Object[] parameters = new Object[]{"*", schemaName, "SCHEMA", null};
        String sql = schema.getSqlDialect().execute(CoreSchema.getInstance().getSchema(), "fn_dropifexists", "?, ?, ?, ?");
        Table.execute(schema, sql, parameters);
    }

    /**
     * Drop an object (table, view) or subobject (index) if it exists
     *
     * @param schema  dbSchema in which the object lives
     * @param objectName the name of the table or view to be dropped, or the table on which the index is defined
     * @param objectType "TABLE", "VIEW", "INDEX"
     * @param subObjectName index name;  ignored if not an index
     */
    public void dropIfExists(DbSchema schema, String objectName, String objectType, @Nullable String subObjectName) throws SQLException
    {
        Object[] parameters = new Object[]{objectName, schema.getName(), objectType, subObjectName};
        String sql = schema.getSqlDialect().execute(CoreSchema.getInstance().getSchema(), "fn_dropifexists", "?, ?, ?, ?");
        Table.execute(schema, sql, parameters);
    }

    /**
     * Returns a SQL fragment for the integer expression indicating the (1-based) first occurrence of littleString in bigString
     */
    abstract public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString);

    /**
     * Returns a SQL fragment for the integer expression indicating the (1-based) first occurrence of littleString in bigString starting at (1-based) startIndex.
     */
    abstract public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex);

    abstract public boolean allowSortOnSubqueryWithoutLimit();

    // Substitute the parameter values into the SQL statement.
    public String substituteParameters(SQLFragment frag)
    {
        return _stringHandler.substituteParameters(frag);
    }


    // Trying to be DataSource implementation agnostic here.  DataSource interface doesn't provide access to any of
    // these properties, but we don't want to cast to a specific implementation class, so use reflection to get them.
    public static class DataSourceProperties
    {
        private final String _dsName;
        private final DataSource _ds;

        public DataSourceProperties(String dsName, DataSource ds)
        {
            _dsName = dsName;
            _ds = ds;
        }

        private String getProperty(String methodName) throws ServletException
        {
            try
            {
                Method getUrl = _ds.getClass().getMethod(methodName);
                return (String)getUrl.invoke(_ds);
            }
            catch (Exception e)
            {
                throw new ServletException("Unable to retrieve DataSource property via " + methodName, e);
            }
        }

        public String getDataSourceName()
        {
            return _dsName;
        }

        public String getUrl() throws ServletException
        {
            return getProperty("getUrl");
        }


        public String getDriverClassName() throws ServletException
        {
            return getProperty("getDriverClassName");
        }


        public String getUsername() throws ServletException
        {
            return getProperty("getUsername");
        }


        public String getPassword() throws ServletException
        {
            return getProperty("getPassword");
        }
    }


    // All statement creation passes through these two methods.  We return our standard statement wrappers in most
    // cases, but dialects can return their own subclasses of StatementWrapper to work around JDBC driver bugs.
    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt)
    {
        return new StatementWrapper(conn, stmt);
    }


    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
    {
        return new StatementWrapper(conn, stmt, sql);
    }

    protected String getDatePartName(int part)
    {
        String partName;

        switch (part)
        {
            case Calendar.YEAR:
            {
                partName = "year";
                break;
            }
            case Calendar.MONTH:
            {
                partName = "month";
                break;
            }
            case Calendar.DAY_OF_MONTH:
            {
                partName = "day";
                break;
            }
            case Calendar.HOUR:
            {
                partName = "hour";
                break;
            }
            case Calendar.MINUTE:
            {
                partName = "minute";
                break;
            }
            case Calendar.SECOND:
            {
                partName = "second";
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unsupported time unit: " + part);
            }
        }

        return partName;
    }

    public abstract List<String> getChangeStatements(TableChange change);
    public abstract void initializeConnection(Connection conn) throws SQLException;
    public abstract void purgeTempSchema(Map<String, TempTableTracker> createdTableNames);
    public abstract boolean isCaseSensitive();
    public abstract boolean isEditable();
    public abstract boolean isSqlServer();
    public abstract boolean isPostgreSQL();
    public abstract boolean isOracle();
    public abstract ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, DbSchema schema);
    public abstract PkMetaDataReader getPkMetaDataReader(ResultSet rs);
}
