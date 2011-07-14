/*
 * Copyright (c) 2005-2011 Fred Hutchinson Cancer Research Center
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlScriptParser;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.data.dialect.SqlDialect;
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
public class MicrosoftSqlServer2005Dialect extends SqlDialect
{
    private static final Logger _log = Logger.getLogger(MicrosoftSqlServer2005Dialect.class);

    @Override
    protected @NotNull Set<String> getReservedWords()
    {
        return Sets.newCaseInsensitiveHashSet(new CsvSet(
            "add, all, alter, and, any, as, asc, authorization, backup, begin, between, break, browse, bulk, by, cascade, " +
            "case, check, checkpoint, close, clustered, coalesce, collate, column, commit, compute, constraint, contains, " +
            "containstable, continue, convert, create, cross, current, current_date, current_time, current_timestamp, " +
            "current_user, cursor, database, dbcc, deallocate, declare, default, delete, deny, desc, distinct, distributed, " +
            "double, drop, else, end, end-exec, errlvl, escape, except, exec, execute, exists, exit, external, fetch, file, " +
            "fillfactor, for, foreign, freetext, freetexttable, from, full, function, goto, grant, group, having, holdlock, " +
            "identity, identity_insert, identitycol, if, in, index, inner, insert, intersect, into, is, join, key, kill, " +
            "left, like, lineno, merge, national, nocheck, nonclustered, not, null, nullif, of, off, offsets, on, open, " +
            "opendatasource, openquery, openrowset, openxml, option, or, order, outer, over, percent, pivot, plan, primary, " +
            "print, proc, procedure, public, raiserror, read, readtext, reconfigure, references, replication, restore, " +
            "restrict, return, revert, revoke, right, rollback, rowcount, rowguidcol, rule, save, schema, select, " +
            "session_user, set, setuser, shutdown, some, statistics, system_user, table, tablesample, textsize, then, to, " +
            "top, tran, transaction, trigger, truncate, tsequal, union, unique, unpivot, update, updatetext, use, user, " +
            "values, varying, view, waitfor, when, where, while, with, writetext" +

            // SQL Server 2005 only
            ", dump, load"
        ));
    }

    @Override
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

    @Override
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

    @Override
    protected String sqlTypeNameFromSqlType(PropertyStorageSpec prop)
    {
        if (prop.isAutoIncrement())
        {
            if (prop.getSqlTypeInt() == Types.INTEGER)
            {
                return "INT IDENTITY (1, 1)";
            }
            else
            {
                throw new IllegalArgumentException("AutoIncrement is not supported for SQL type " + prop.getSqlTypeInt() + " (" + sqlTypeNameFromSqlType(prop.getSqlTypeInt()) + ")");
            }
        }
        else
        {
            return sqlTypeNameFromSqlType(prop.getSqlTypeInt());
        }
    }

    @Override
    public boolean isSqlServer()
    {
        return true;
    }

    @Override
    public boolean isPostgreSQL()
    {
        return false;
    }

    @Override
    protected String getProductName()
    {
        return "Sql Server";
    }

    @Override
    public String getSQLScriptPath()
    {
        return "sqlserver";
    }

    @Override
    public String getDefaultDateTimeDataType()
    {
        return "DATETIME";
    }

    @Override
    public String getUniqueIdentType()
    {
        return "INT IDENTITY (1,1)";
    }

    @Override
    public String getGuidType()
    {
        return "UNIQUEIDENTIFIER";
    }

    @Override
    public void appendStatement(StringBuilder sql, String statement)
    {
        sql.append('\n');
        sql.append(statement);
    }


    @Override
    protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
    }


    @Override
    public void appendSelectAutoIncrement(StringBuilder sql, TableInfo tableName, String columnName)
    {
        appendStatement(sql, "SELECT @@IDENTITY");
    }


    @Override
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

    private SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int rowCount, long offset)
    {
        if (order == null || order.trim().length() == 0)
            throw new IllegalArgumentException("ERROR: ORDER BY clause required to limit");

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT * FROM (\n");
        sql.append(select);
        sql.append(",\nROW_NUMBER() OVER (\n");
        sql.append(order);
        sql.append(") AS _RowNum\n");
        sql.append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        sql.append("\n) AS z\n");
        sql.append("WHERE _RowNum BETWEEN ");
        sql.append(offset + 1);
        sql.append(" AND ");
        sql.append(offset + rowCount);
        return sql;
    }

    @Override
    public boolean supportsComments()
    {
        return true;
    }

    // Execute a stored procedure/function with the specified parameters

    @Override
    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        return "EXEC " + schema.getName() + "." + procedureName + " " + parameters;
    }

    @Override
    public SQLFragment execute(DbSchema schema, String procedureName, SQLFragment parameters)
    {
        SQLFragment exec = new SQLFragment("EXEC " + schema.getName() + "." + procedureName + " ");
        exec.append(parameters);
        return exec;
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


    @Override
    public String getCharClassLikeOperator()
    {
        return "LIKE";
    }

    @Override
    public String getCaseInsensitiveLikeOperator()
    {
        return "LIKE";
    }

    @Override
    public String getVarcharLengthFunction()
    {
        return "len";
    }

    @Override
    public String getStdDevFunction()
    {
        return "stdev";
    }

    @Override
    public String getClobLengthFunction()
    {
        return "datalength";
    }

    @Override
    public String getStringIndexOfFunction(String stringToFind, String stringToSearch)
    {
        return "patindex('%' + " + stringToFind + " + '%', " + stringToSearch + ")";
    }

    @Override
    public String getSubstringFunction(String s, String start, String length)
    {
        return "substring(" + s + ", " + start + ", " + length + ")";
    }

    @Override
    public boolean supportsGroupConcat()
    {
        return false;
    }

    @Override
    public boolean supportsSelectConcat()
    {
        return true;
    }

    @Override
    public boolean supportsOffset()
    {
        return true;
    }

    @Override
    public SQLFragment getSelectConcat(SQLFragment selectSql)
    {
        String sql = selectSql.getSQL().toUpperCase();

        // Use SQLServer's FOR XML syntax to concat multiple values together
        // We want the separated by commas, so prefix each value with a comma and then use SUBSTRING to strip
        // of the leading comma - this is easier than stripping a trailing comma because we don't have to determine
        // the length of the string

        // The goal is to get something of the form:
        // SUBSTRING((SELECT ',' + c$Titration$.Name AS [data()] FROM luminex.AnalyteTitration c
        // INNER JOIN luminex.Titration c$Titration$ ON (c.TitrationId = c$Titration$.RowId) WHERE child.AnalyteId = c.AnalyteId FOR XML PATH ('')), 2, 2147483647) AS Titration$Name

        // TODO - There is still an issue if the individual input values contain commas. We need to escape or otherwise handle that
        SQLFragment ret = new SQLFragment(selectSql);
        int fromIndex = sql.indexOf("FROM");
        ret.insert(fromIndex, "AS NVARCHAR) AS [text()] ");
        int selectIndex = sql.indexOf("SELECT");
        ret.insert(selectIndex + "SELECT".length(), "',' + CAST(");
        ret.insert(0, "SUBSTRING ((");
        // We want all the characters, so use a ridiculously long value to ensure that we don't truncate
        ret.append(" FOR XML PATH ('')), 2, 2147483647)");

        return ret;
    }

    @Override
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    public String getTempTableKeyword()
    {
        return "";
    }

    // UNDONE: why ## instead of #?

    @Override
    public String getTempTablePrefix()
    {
        return "##";
    }


    @Override
    public String getGlobalTempTablePrefix()
    {
        return "tempdb..";
    }


    @Override
    public boolean isNoDatabaseException(SQLException e)
    {
        return "S1000".equals(e.getSQLState());
    }

    @Override
    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return !("text".equalsIgnoreCase(sqlDataTypeName) ||
                "ntext".equalsIgnoreCase(sqlDataTypeName) ||
                "image".equalsIgnoreCase(sqlDataTypeName));
    }

    @Override
    public String getDropIndexCommand(String tableName, String indexName)
    {
        return "DROP INDEX " + tableName + "." + indexName;
    }

    @Override
    public String getCreateDatabaseSql(String dbName)
    {
        return "CREATE DATABASE " + dbName;
    }

    // Do nothing

    @Override
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

    @Override
    public String getDateDiff(int part, String value1, String value2)
    {
        String partName = getDatePartName(part);
        return "DATEDIFF(" + partName + ", " + value2 + ", " + value1 + ")";
    }

    @Override
    public String getDateTimeToDateCast(String expression)
    {
        return "convert(datetime, convert(varchar, (" + expression + "), 101))";
    }

    @Override
    public String getRoundFunction(String valueToRound)
    {
        return "ROUND(" + valueToRound + ", 0)";
    }

    @Override
    public boolean supportsRoundDouble()
    {
        return true;
    }

    @Override
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

    @Override
    public String sanitizeException(SQLException ex)
    {
        if ("01004".equals(ex.getSQLState()))
        {
            return INPUT_TOO_LONG_ERROR_MESSAGE;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    @Override
    public String getAnalyzeCommandForTable(String tableName)
    {
        return "UPDATE STATISTICS " + tableName + ";";
    }

    @Override
    public boolean treatCatalogsAsSchemas()
    {
        return false;
    }

    @Override
    protected String getSIDQuery()
    {
        return "SELECT @@spid";
    }

    @Override
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
    @Override
    public void overrideAutoIncrement(StringBuilder statements, TableInfo tinfo)
    {
        statements.insert(0, "SET IDENTITY_INSERT " + tinfo + " ON\n");
        statements.append("SET IDENTITY_INSERT ").append(tinfo).append(" OFF");
    }

    private static final Pattern GO_PATTERN = Pattern.compile("^\\s*GO\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern JAVA_CODE_PATTERN = Pattern.compile("^\\s*EXEC(?:UTE)*\\s+core\\.executeJavaUpgradeCode\\s*'(.+)'\\s*;?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Override
    public void runSql(DbSchema schema, String sql, UpgradeCode upgradeCode, ModuleContext moduleContext) throws SQLException
    {
        SqlScriptParser parser = new SqlScriptParser(sql, GO_PATTERN, JAVA_CODE_PATTERN, schema, upgradeCode, moduleContext);
        parser.execute();
    }

    @Override
    public String getMasterDataBaseName()
    {
        return "master";
    }


    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new JtdsJdbcHelper();
    }


    /*
        jTDS example connection URLs we need to parse:

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

    @Override
    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        SQLFragment ret = new SQLFragment("(CHARINDEX(");
        ret.append(littleString);
        ret.append(",");
        ret.append(bigString);
        ret.append("))");
        return ret;
    }

    @Override
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

    @Override
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
        String pkColumn = null;
        for (PropertyStorageSpec prop : change.getColumns())
        {
            createTableSqlParts.add(getSqlColumnSpec(prop));
            if (prop.isPrimaryKey())
            {
                assert null == pkColumn : "no more than one primary key defined";
                pkColumn = prop.getName();
            }

        }

        statements.add(String.format("CREATE TABLE %s (%s)", makeTableIdentifier(change), StringUtils.join(createTableSqlParts, ",\n")));

        if (null != pkColumn)
            statements.add(String.format("ALTER TABLE %s ADD CONSTRAINT %s PRIMARY KEY (%s)",
                    makeTableIdentifier(change),
                    change.getTableName() + "_pk",
                    pkColumn));


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
                    makeTableIdentifier(change) + "." + oldToNew.getKey(), oldToNew.getValue()));
        }

        return statements;
    }

    private String getDropColumnsStatement(TableChange change)
    {
        List<String> sqlParts = new ArrayList<String>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            sqlParts.add(makeLegalIdentifier(prop.getName()));
        }

        return String.format("ALTER TABLE %s DROP COLUMN %s", change.getSchemaName() + "." + change.getTableName(), StringUtils.join(sqlParts, ",\n"));
    }

    private String getAddColumnsStatement(TableChange change)
    {
        List<String> sqlParts = new ArrayList<String>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            sqlParts.add(getSqlColumnSpec(prop));
        }

        return String.format("ALTER TABLE %s ADD %s", change.getSchemaName() + "." + change.getTableName(), StringUtils.join(sqlParts, ",\n"));
    }

    private String getSqlColumnSpec(PropertyStorageSpec prop)
    {
        List<String> colSpec = new ArrayList<String>();
        colSpec.add(makeLegalIdentifier(prop.getName()));
        colSpec.add(sqlTypeNameFromSqlType(prop));

        if (prop.getSqlTypeInt() == Types.VARCHAR)
            colSpec.add("(" + prop.getSize() + ")");
        else if (prop.getSqlTypeInt() == Types.NUMERIC)
            colSpec.add("(15,4)");

        if (prop.isPrimaryKey())
            colSpec.add("NOT NULL");

        return StringUtils.join(colSpec, ' ');
    }

    @Override
    public void initializeConnection(Connection conn) throws SQLException
    {
        Statement stmt = conn.createStatement();
        stmt.execute("SET ARITHABORT ON");
        stmt.close();
    }

    @Override
    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        // Do nothing -- SQL Server cleans up temp tables automatically
    }

    @Override
    public boolean isCaseSensitive()
    {
        return false;
    }

    @Override
    public boolean isEditable()
    {
        return true;
    }

    @Override
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, DbSchema schema)
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

        @Override
        public boolean isAutoIncrement() throws SQLException
        {
            return getSqlTypeName().equalsIgnoreCase("int identity");
        }
    }


    @Override
    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ");
    }

    /**
     * @return any additional information that should be sent to the mothership in the case of a SQLException
     */
    @Override
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
}
