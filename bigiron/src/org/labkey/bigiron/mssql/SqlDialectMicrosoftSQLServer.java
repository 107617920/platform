/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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

package org.labkey.bigiron.mssql;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.SqlScriptParser;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.ServletException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Dialect specifics for Microsoft SQL Server
public class SqlDialectMicrosoftSQLServer extends SqlDialect
{
    private static final SqlDialect INSTANCE = new SqlDialectMicrosoftSQLServer();

    public static SqlDialect get()
    {
        return INSTANCE;
    }

    protected SqlDialectMicrosoftSQLServer()
    {
        reservedWordSet = new CaseInsensitiveHashSet(PageFlowUtil.set(
                "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUTHORIZATION", "BACKUP", "BEGIN", "BETWEEN", "BREAK", "BROWSE", "BULK",
                "BY", "CASCADE", "CASE", "CHECK", "CHECKPOINT", "CLOSE", "CLUSTERED", "COALESCE", "COLLATE", "COLUMN", "COMMIT", "COMPUTE",
                "CONSTRAINT", "CONTAINS", "CONTAINSTABLE", "CONTINUE", "CONVERT", "CREATE", "CROSS", "CURRENT", "CURRENT_DATE",
                "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "CURSOR", "DATABASE", "DBCC", "DEALLOCATE", "DECLARE", "DEFAULT",
                "DELETE", "DENY", "DESC", "DISK", "DISTINCT", "DISTRIBUTED", "DOUBLE", "DROP", "DUMMY", "DUMP", "ELSE", "END", "ERRLVL",
                "ESCAPE", "EXCEPT", "EXEC", "EXECUTE", "EXISTS", "EXIT", "FETCH", "FILE", "FILLFACTOR", "FOR", "FOREIGN", "FREETEXT",
                "FREETEXTTABLE", "FROM", "FULL", "FUNCTION", "GOTO", "GRANT", "GROUP", "HAVING", "HOLDLOCK", "IDENTITY", "IDENTITY_INSERT",
                "IDENTITYCOL", "IF", "IN", "INDEX", "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN", "KEY", "KILL", "LEFT", "LIKE",
                "LINENO", "LOAD", "NATIONAL", "NOCHECK", "NONCLUSTERED", "NOT", "NULL", "NULLIF", "OF", "OFF", "OFFSETS", "ON", "OPEN",
                "OPENDATASOURCE", "OPENQUERY", "OPENROWSET", "OPENXML", "OPTION", "OR", "ORDER", "OUTER", "OVER", "PERCENT", "PLAN",
                "PRECISION", "PRIMARY", "PRINT", "PROC", "PROCEDURE", "PUBLIC", "RAISERROR", "READ", "READTEXT", "RECONFIGURE",
                "REFERENCES", "REPLICATION", "RESTORE", "RESTRICT", "RETURN", "REVOKE", "RIGHT", "ROLLBACK", "ROWCOUNT", "ROWGUIDCOL",
                "RULE", "SAVE", "SCHEMA", "SELECT", "SESSION_USER", "SET", "SETUSER", "SHUTDOWN", "SOME", "STATISTICS", "SYSTEM_USER",
                "TABLE", "TEXTSIZE", "THEN", "TO", "TOP", "TRAN", "TRANSACTION", "TRIGGER", "TRUNCATE", "TSEQUAL", "UNION", "UNIQUE",
                "UPDATE", "UPDATETEXT", "USE", "USER", "VALUES", "VARYING", "VIEW", "WAITFOR", "WHEN", "WHERE", "WHILE", "WITH",
                "WRITETEXT"
        ));
    }

    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        sqlTypeNameMap.put("FLOAT", Types.DOUBLE);
        sqlTypeNameMap.put("INT IDENTITY", Types.INTEGER);
        sqlTypeNameMap.put("DATETIME", Types.TIMESTAMP);
        sqlTypeNameMap.put("TEXT", Types.LONGVARCHAR);
        sqlTypeNameMap.put("NTEXT", Types.LONGVARCHAR);
        sqlTypeNameMap.put("NVARCHAR", Types.VARCHAR);
        sqlTypeNameMap.put("UNIQUEIDENTIFIER", Types.VARCHAR);
        sqlTypeNameMap.put("TIMESTAMP", Types.BINARY);
    }

    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
        sqlTypeIntMap.put(Types.BIT, "BIT");
        sqlTypeIntMap.put(Types.BOOLEAN, "BIT");
        sqlTypeIntMap.put(Types.CHAR, "NCHAR");
        sqlTypeIntMap.put(Types.LONGVARBINARY, "IMAGE");
        sqlTypeIntMap.put(Types.LONGVARCHAR, "NTEXT");
        sqlTypeIntMap.put(Types.VARCHAR, "NVARCHAR");
        sqlTypeIntMap.put(Types.TIMESTAMP, "DATETIME");
        sqlTypeIntMap.put(Types.DOUBLE, "FLOAT");
        sqlTypeIntMap.put(Types.FLOAT, "FLOAT");
    }

    protected boolean claimsDriverClassName(String driverClassName)
    {
        return "net.sourceforge.jtds.jdbc.Driver".equals(driverClassName);
    }

    protected boolean claimsProductNameAndVersion(String dataBaseProductName, int databaseMajorVersion, int databaseMinorVersion, String jdbcDriverVersion, boolean logWarnings)
    {
        boolean ret = dataBaseProductName.equals("Microsoft SQL Server") && (databaseMajorVersion < 9);
        if (ret && logWarnings)
            _log.warn("Support for Microsoft SQL Server 2000 has been deprecated. Please upgrade to version 2005 or later.");

        return ret;
    }

    public boolean isSqlServer()
    {
        return true;
    }

    public boolean isPostgreSQL()
    {
        return false;
    }

    protected String getProductName()
    {
        return "Sql Server";
    }

    public String getSQLScriptPath()
    {
        return "sqlserver";
    }

    public String getDefaultDateTimeDataType()
    {
        return "DATETIME";
    }

    public String getUniqueIdentType()
    {
        return "INT IDENTITY (1,1)";
    }


    @Override
    public void appendStatement(StringBuilder sql, String statement)
    {
        sql.append('\n');
        sql.append(statement);
    }


    protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
    }


    @Override
    public void appendSelectAutoIncrement(StringBuilder sql, TableInfo tableName, String columnName)
    {
        appendStatement(sql, "SELECT @@IDENTITY");
    }


    public boolean requiresStatementMaxRows()
    {
        return false;
    }

    @Override
    public SQLFragment limitRows(SQLFragment frag, int rowCount)
    {
        if (rowCount != Table.ALL_ROWS)
        {
            String sql = frag.getSQL();
            if (!sql.substring(0, 6).equalsIgnoreCase("SELECT"))
                throw new IllegalArgumentException("ERROR: Limit SQL Doesn't Start with SELECT: " + sql);

            int offset = 6;
            if (sql.substring(0, 15).equalsIgnoreCase("SELECT DISTINCT"))
                offset = 15;
            frag.insert(offset, " TOP " + (Table.NO_ROWS == rowCount ? 0 : rowCount));
        }
        return frag;
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int rowCount, long offset)
    {
        if (select == null)
            throw new IllegalArgumentException("select");
        if (from == null)
            throw new IllegalArgumentException("from");

        if (rowCount == Table.ALL_ROWS || rowCount == Table.NO_ROWS || (rowCount > 0 && offset == 0))
        {
            SQLFragment sql = new SQLFragment();
            sql.append(select);
            sql.append("\n").append(from);
            if (filter != null) sql.append("\n").append(filter);
            if (groupBy != null) sql.append("\n").append(groupBy);
            if (order != null) sql.append("\n").append(order);

            return limitRows(sql, rowCount);
        }
        else
        {
            return _limitRows(select, from, filter, order, groupBy, rowCount, offset);
        }
    }

    @Override
    public boolean supportsOffset()
    {
        return false;
    }

    public boolean supportsComments()
    {
        return true;
    }

    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int rowCount, long offset)
    {
        throw new UnsupportedOperationException("limitRows() with an offset not supported in SQLServer 2000");
    }


    // Execute a stored procedure/function with the specified parameters

    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        return "EXEC " + schema.getName() + "." + procedureName + " " + parameters;
    }


    public String getConcatenationOperator()
    {
        return "+";
    }


    @Override
    public String concatenate(String... args)
    {
        return StringUtils.join(args, " + ");
    }


    @Override
    public SQLFragment concatenate(SQLFragment... args)
    {
        SQLFragment ret = new SQLFragment();
        String op = "";
        for (SQLFragment arg : args)
        {
            ret.append(op).append(arg);
            op = " + ";
        }
        return ret;
    }


    public String getCharClassLikeOperator()
    {
        return "LIKE";
    }

    @Override
    public String getCaseInsensitiveLikeOperator()
    {
        return "LIKE";
    }

    public String getVarcharLengthFunction()
    {
        return "len";
    }

    public String getStdDevFunction()
    {
        return "stdev";
    }

    public String getClobLengthFunction()
    {
        return "datalength";
    }

    public String getStringIndexOfFunction(String stringToFind, String stringToSearch)
    {
        return "patindex('%' + " + stringToFind + " + '%', " + stringToSearch + ")";
    }

    public String getSubstringFunction(String s, String start, String length)
    {
        return "substring(" + s + ", " + start + ", " + length + ")";
    }

    @Override
    public SQLFragment getGroupConcatAggregateFunction(SQLFragment sql, boolean distinct, boolean sorted)
    {
        return new SQLFragment("MIN(" + sql + ")");    // No GROUP_CONCAT support for SQL Server 2000
    }

    public String getTempTableKeyword()
    {
        return "";
    }

    // UNDONE: why ## instead of #?

    public String getTempTablePrefix()
    {
        return "##";
    }


    public String getGlobalTempTablePrefix()
    {
        return "tempdb..";
    }


    public boolean isNoDatabaseException(SQLException e)
    {
        return "S1000".equals(e.getSQLState());
    }

    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return !("text".equalsIgnoreCase(sqlDataTypeName) ||
                "ntext".equalsIgnoreCase(sqlDataTypeName) ||
                "image".equalsIgnoreCase(sqlDataTypeName));
    }

    public String getDropIndexCommand(String tableName, String indexName)
    {
        return "DROP INDEX " + tableName + "." + indexName;
    }

    public String getCreateDatabaseSql(String dbName)
    {
        return "CREATE DATABASE " + dbName;
    }

    // Do nothing

    public void prepareNewDbSchema(DbSchema schema)
    {
    }

    public String getCreateSchemaSql(String schemaName)
    {
        return "EXEC sp_addapprole '" + schemaName + "', 'password'";
    }

    @Override
    public String getDatePart(int part, String value)
    {
        String partName = getDatePartName(part);
        return "DATEPART(" + partName + ", " + value + ")";
    }

    public String getDateDiff(int part, String value1, String value2)
    {
        String partName = getDatePartName(part);
        return "DATEDIFF(" + partName + ", " + value2 + ", " + value1 + ")";
    }

    public String getDateTimeToDateCast(String expression)
    {
        return "convert(datetime, convert(varchar, (" + expression + "), 101))";
    }

    public String getRoundFunction(String valueToRound)
    {
        return "ROUND(" + valueToRound + ", 0)";
    }

    public boolean supportsRoundDouble()
    {
        return true;
    }

    protected String getSystemTableNames()
    {
        return "dtproperties,sysconstraints,syssegments";
    }

    private static final Set<String> SYSTEM_SCHEMAS = PageFlowUtil.set("db_accessadmin", "db_backupoperator",
            "db_datareader", "db_datawriter", "db_ddladmin", "db_denydatareader", "db_denydatawriter", "db_owner",
            "db_securityadmin", "guest", "INFORMATION_SCHEMA", "sys");

    @Override
    public boolean isSystemSchema(String schemaName)
    {
        return SYSTEM_SCHEMAS.contains(schemaName);
    }

    public String sanitizeException(SQLException ex)
    {
        if ("01004".equals(ex.getSQLState()))
        {
            return INPUT_TOO_LONG_ERROR_MESSAGE;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    public String getAnalyzeCommandForTable(String tableName)
    {
        return "UPDATE STATISTICS " + tableName + ";";
    }

    @Override
    public boolean treatCatalogsAsSchemas()
    {
        return false;
    }

    protected String getSIDQuery()
    {
        return "SELECT @@spid";
    }

    public String getBooleanDataType()
    {
        return "BIT";
    }

    @Override
    public String getBooleanLiteral(boolean b)
    {
        return b ? "1" : "0";
    }

    /**
     * Wrap one or more INSERT statements to allow explicit specification
     * of values for autoincrementing columns (e.g. IDENTITY in SQL Server
     * or SERIAL in Postgres). The input StringBuilder is modified.
     *
     * @param statements the insert statements. If more than one,
     *                   they must have been joined by appendStatement
     *                   and must all refer to the same table.
     * @param tinfo      table used in the insert(s)
     */
    public void overrideAutoIncrement(StringBuilder statements, TableInfo tinfo)
    {
        statements.insert(0, "SET IDENTITY_INSERT " + tinfo + " ON\n");
        statements.append("SET IDENTITY_INSERT ").append(tinfo).append(" OFF");
    }

    private static final Pattern GO_PATTERN = Pattern.compile("^\\s*GO\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern JAVA_CODE_PATTERN = Pattern.compile("^\\s*EXEC(?:UTE)*\\s+core\\.executeJavaUpgradeCode\\s*'(.+)'\\s*;?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public void runSql(DbSchema schema, String sql, UpgradeCode upgradeCode, ModuleContext moduleContext) throws SQLException
    {
        SqlScriptParser parser = new SqlScriptParser(sql, GO_PATTERN, JAVA_CODE_PATTERN, schema, upgradeCode, moduleContext);
        parser.execute();
    }

    public String getMasterDataBaseName()
    {
        return "master";
    }


    public JdbcHelper getJdbcHelper()
    {
        return new JtdsJdbcHelper();
    }


    /*  jTDS example connection URLs we need to parse:

        jdbc:jtds:sqlserver://host:1433/database
        jdbc:jtds:sqlserver://host/database;SelectMethod=cursor

    */

    public static class JtdsJdbcHelper implements JdbcHelper
    {
        @Override
        public String getDatabase(String url) throws ServletException
        {
            if (!url.startsWith("jdbc:jtds:sqlserver"))
                throw new ServletException("Unsupported connection url: " + url);

            int dbEnd = url.indexOf(';');
            if (-1 == dbEnd)
                dbEnd = url.length();
            int dbDelimiter = url.lastIndexOf('/', dbEnd);
            if (-1 == dbDelimiter)
                throw new ServletException("Invalid jTDS connection url: " + url);
            return url.substring(dbDelimiter + 1, dbEnd);
        }
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        SQLFragment ret = new SQLFragment("(CHARINDEX(");
        ret.append(littleString);
        ret.append(",");
        ret.append(bigString);
        ret.append("))");
        return ret;
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex)
    {
        SQLFragment ret = new SQLFragment("(CHARINDEX(");
        ret.append(littleString);
        ret.append(",");
        ret.append(bigString);
        ret.append(",");
        ret.append(startIndex);
        ret.append("))");
        return ret;
    }

    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return false;
    }

    @Override
    public List<String> getChangeStatements(TableChange change)
    {
        List<String> sql = new ArrayList<String>();
        switch (change.getType())
        {
            case CreateTable:
                sql.addAll(getCreateTableStatements(change));
                break;
            case DropTable:
                sql.add("DROP TABLE " + change.getSchemaName() + "." + change.getTableName());
                break;
            case AddColumns:
                sql.add(getAddColumnsStatement(change));
                break;
            case DropColumns:
                sql.add(getDropColumnsStatement(change));
                break;
            case RenameColumns:
                sql.addAll(getRenameColumnsStatements(change));
                /*
                sql = String.format("EXEC sp_rename '%s.%s','%s','COLUMN'", change.getSchemaName()+"."+change.getTableName(),
                    change.getOldColumnName(), change.getNewColumnName());
                    */
        }

        return sql;
    }

    private List<String> getCreateTableStatements(TableChange change)
    {
        List<String> statements = new ArrayList<String>();
        List<String> createTableSqlParts = new ArrayList<String>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            createTableSqlParts.add(getSqlColumnSpec(prop));
            // if it has a qc note (aka "missing value indicator")
            // make an additional column for that
// UNDONE WE'RE NOT TRACKING UPDATES SO ALWAYS ADD/DROP NV COLUMN
//            if (prop.isMvEnabled())
            {
                createTableSqlParts.add(String.format("%s %s", prop.getMvIndicatorColumnName(), sqlTypeNameFromSqlType(Types.VARCHAR)));
            }
        }

        statements.add(String.format("CREATE TABLE %s (%s)", makeTableIdentifier(change), StringUtils.join(createTableSqlParts, ", ")));

        for (PropertyStorageSpec.Index index : change.getIndexedColumns())
        {
            statements.add(String.format("CREATE %s INDEX %s ON %s (%s)", 
                    index.isUnique ? "UNIQUE" : "",
                    nameIndex(change.getTableName(), index.columnNames),
                    makeTableIdentifier(change),
                    StringUtils.join(index.columnNames, ", ")));
        }

        return statements;
    }

    private String makeTableIdentifier(TableChange change)
    {
        assert AliasManager.isLegalName(change.getTableName());
        return change.getSchemaName() + "." + change.getTableName();
    }

    private String nameIndex(String tableName, String[] indexedColumns)
    {
        return AliasManager.makeLegalName(tableName + '_' + StringUtils.join(indexedColumns, "_"), this);
    }

    private List<String> getRenameColumnsStatements(TableChange change)
    {
        List<String> statements = new ArrayList<String>();
        for (Map.Entry<String, String> oldToNew : change.getColumnRenames().entrySet())
        {
            statements.add(String.format("EXEC sp_rename '%s','%s','COLUMN'",
                    makeTableIdentifier(change), oldToNew.getKey(), oldToNew.getValue()));
        }

        return statements;
    }

    private String getDropColumnsStatement(TableChange change)
    {
        List<String> sqlParts = new ArrayList<String>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            sqlParts.add("DROP " + prop.getName());
        }

        return String.format("ALTER TABLE %s %s", change.getSchemaName() + "." + change.getTableName(), StringUtils.join(sqlParts, ", "));
    }

    private String getAddColumnsStatement(TableChange change)
    {
        List<String> sqlParts = new ArrayList<String>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            sqlParts.add("ADD " + getSqlColumnSpec(prop));
        }

        return String.format("ALTER TABLE %s %s", change.getSchemaName() + "." + change.getTableName(), StringUtils.join(sqlParts, ", "));
    }

    private String getSqlColumnSpec(PropertyStorageSpec prop)
    {
        List<String> colSpec = new ArrayList<String>();
        colSpec.add(prop.getName());
        colSpec.add(sqlTypeNameFromSqlTypeInt(prop.getSqlTypeInt()));
        if (prop.isUnique()) colSpec.add("UNIQUE");
        if (!prop.isNullable()) colSpec.add("NOT NULL");
        // todo auto increment?

        return StringUtils.join(colSpec, ' ');
    }

    public void initializeConnection(Connection conn) throws SQLException
    {
        Statement stmt = conn.createStatement();
        stmt.execute("SET ARITHABORT ON");
        stmt.close();
    }

    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        // Do nothing -- SQL Server cleans up temp tables automatically
    }

    public boolean isCaseSensitive()
    {
        return false;
    }

    public boolean isEditable()
    {
        return true;
    }

    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, DbScope scope)
    {
        return new SqlServerColumnMetaDataReader(rsCols);
    }

    private static class SqlServerColumnMetaDataReader extends ColumnMetaDataReader
    {
        private SqlServerColumnMetaDataReader(ResultSet rsCols)
        {
            super(rsCols);

            _nameKey = "COLUMN_NAME";
            _sqlTypeKey = "DATA_TYPE";
            _sqlTypeNameKey = "TYPE_NAME";
            _scaleKey = "COLUMN_SIZE";
            _nullableKey = "NULLABLE";
            _postionKey = "ORDINAL_POSITION";
        }

        public boolean isAutoIncrement() throws SQLException
        {
            return getSqlTypeName().equalsIgnoreCase("int identity");
        }
    }


    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ");
    }

    /**
     * @return any additional information that should be sent to the mothership in the case of a SQLException
     */
    public String getExtraInfo(SQLException e)
    {
        // Deadlock between two different DB connections
        if ("40001".equals(e.getSQLState()))
        {
            return getOtherDatabaseThreads();
        }
        return null;
    }

    @Override
    protected String getDatabaseMaintenanceSql()
    {
        return "EXEC sp_updatestats;";
    }

    public Collection<? extends Class> getJUnitTests()
    {
        return Arrays.asList(DialectRetrievalTestCase.class, JavaUpgradeCodeTestCase.class, JdbcHelperTestCase.class);
    }


    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        public void testDialectRetrieval()
        {
            // These should result in bad database exception
            badProductName("Gobbledygood", 1.0, 12.0, "");
            badProductName("SQL Server", 1.0, 12.0, "");
            badProductName("sqlserver", 1.0, 12.0, "");

            // < 9.0 should result in SqlDialectMicrosoftSQLServer -- no bad versions at the moment
            good("Microsoft SQL Server", 0.0, 8.9, "", SqlDialectMicrosoftSQLServer.class);

            // >= 9.0 should result in SqlDialectMicrosoftSQLServer9
            good("Microsoft SQL Server", 9.0, 11.0, "", SqlDialectMicrosoftSQLServer9.class);
        }
    }


    public static class JavaUpgradeCodeTestCase extends Assert
    {
        @Test
        public void testJavaUpgradeCode()
        {
            String goodSql =
                    "EXEC core.executeJavaUpgradeCode 'upgradeCode'\n" +                       // Normal
                            "EXECUTE core.executeJavaUpgradeCode 'upgradeCode'\n" +                    // EXECUTE
                            "execute core.executeJavaUpgradeCode'upgradeCode'\n" +                     // execute
                            "    EXEC     core.executeJavaUpgradeCode    'upgradeCode'         \n" +   // Lots of whitespace
                            "exec CORE.EXECUTEJAVAUPGRADECODE 'upgradeCode'\n" +                       // Case insensitive
                            "execute core.executeJavaUpgradeCode'upgradeCode';\n" +                    // execute (with ;)
                            "    EXEC     core.executeJavaUpgradeCode    'upgradeCode'    ;     \n" +  // Lots of whitespace with ; in the middle
                            "exec CORE.EXECUTEJAVAUPGRADECODE 'upgradeCode';     \n" +                 // Case insensitive (with ;)
                            "EXEC core.executeJavaUpgradeCode 'upgradeCode'     ;\n" +                 // Lots of whitespace with ; at end
                            "EXEC core.executeJavaUpgradeCode 'upgradeCode'";                          // No line ending

            String badSql =
                    "/* EXEC core.executeJavaUpgradeCode 'upgradeCode'\n" +           // Inside block comment
                            "   more comment\n" +
                            "*/" +
                            "    -- EXEC core.executeJavaUpgradeCode 'upgradeCode'\n" +       // Inside single-line comment
                            "EXECcore.executeJavaUpgradeCode 'upgradeCode'\n" +               // Bad syntax: EXECcore
                            "EXEC core. executeJavaUpgradeCode 'upgradeCode'\n" +             // Bad syntax: core. execute...
                            "EXECUT core.executeJavaUpgradeCode 'upgradeCode'\n" +            // Misspell EXECUTE
                            "EXEC core.executeJaavUpgradeCode 'upgradeCode'\n" +              // Misspell executeJavaUpgradeCode
                            "EXEC core.executeJavaUpgradeCode 'upgradeCode';;\n" +            // Bad syntax: two semicolons
                            "EXEC core.executeJavaUpgradeCode('upgradeCode')\n";              // Bad syntax: Parentheses

            try
            {
                TestUpgradeCode good = new TestUpgradeCode();
                INSTANCE.runSql(null, goodSql, good, null);
                assertEquals(10, good.getCounter());

                TestUpgradeCode bad = new TestUpgradeCode();
                INSTANCE.runSql(null, badSql, bad, null);
                assertEquals(0, bad.getCounter());
            }
            catch (SQLException e)
            {
                fail("SQL Exception running test: " + e.getMessage());
            }
        }
    }


    public static class JdbcHelperTestCase extends Assert
    {
        @Test
        public void testJdbcHelper()
        {
            JdbcHelper helper = INSTANCE.getJdbcHelper();

            try
            {
                String goodUrls = "jdbc:jtds:sqlserver://localhost/database\n" +
                        "jdbc:jtds:sqlserver://localhost:1433/database\n" +
                        "jdbc:jtds:sqlserver://localhost/database;SelectMethod=cursor\n" +
                        "jdbc:jtds:sqlserver://localhost:1433/database;SelectMethod=cursor\n" +
                        "jdbc:jtds:sqlserver://www.host.com/database\n" +
                        "jdbc:jtds:sqlserver://www.host.com:1433/database\n" +
                        "jdbc:jtds:sqlserver://www.host.com/database;SelectMethod=cursor\n" +
                        "jdbc:jtds:sqlserver://www.host.com:1433/database;SelectMethod=cursor";

                for (String url : goodUrls.split("\n"))
                    assertEquals(helper.getDatabase(url), "database");
            }
            catch (Exception e)
            {
                fail("Exception running JdbcHelper test: " + e.getMessage());
            }

            String badUrls = "jdb:jtds:sqlserver://localhost/database\n" +
                    "jdbc:jts:sqlserver://localhost/database\n" +
                    "jdbc:jtds:sqlerver://localhost/database\n" +
                    "jdbc:jtds:sqlserver://localhostdatabase\n" +
                    "jdbc:jtds:sqlserver:database";

            for (String url : badUrls.split("\n"))
            {
                try
                {
                    if (helper.getDatabase(url).equals("database"))
                        fail("JdbcHelper test failed: database in " + url + " should not have resolved to 'database'");
                }
                catch (ServletException e)
                {
                    // Skip -- we expect to fail on some of these
                }
            }
        }
    }
}
