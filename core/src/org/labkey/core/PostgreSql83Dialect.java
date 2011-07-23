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

package org.labkey.core;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
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
import org.labkey.api.data.dialect.StandardJdbcHelper;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringUtilsLabKey;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.Connection;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Dialect specifics for PostgreSQL
class PostgreSql83Dialect extends SqlDialect
{
    private static final Logger _log = Logger.getLogger(PostgreSql83Dialect.class);

    private final Map<String, Integer> _userDefinedTypeScales = new ConcurrentHashMap<String, Integer>();

    @Override
    protected @NotNull Set<String> getReservedWords()
    {
        return Sets.newCaseInsensitiveHashSet(new CsvSet(
            "all, analyse, analyze, and, any, array, as, asc, asymmetric, authorization, binary, both, case, cast, " +
            "check, collate, column, concurrently, constraint, create, cross, current_catalog, current_date, " +
            "current_role, current_schema, current_time, current_timestamp, current_user, default, deferrable, desc, " +
            "distinct, do, else, end, end-exec, except, fetch, for, foreign, freeze, from, full, grant, group, having, " +
            "ilike, in, initially, inner, intersect, into, is, isnull, join, leading, left, like, limit, localtime, " +
            "localtimestamp, natural, not, notnull, null, offset, on, only, or, order, outer, over, overlaps, placing, " +
            "primary, references, returning, right, select, session_user, similar, some, symmetric, table, then, to, " +
            "trailing, union, unique, user, using, variadic, verbose, when, where, window, with" +

            // For <= PostgreSQL 8.4
            ", between, new, off, old" +

            // For = PostgreSQL 8.3
            ", false, true"));
    }

    @Override
    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
    {
        StatementWrapper statementWrapper = super.getStatementWrapper(conn, stmt, sql);

        try
        {
            //pgSQL JDBC driver will load all results locally unless this is set along with autoCommit=false on the connection
            statementWrapper.setFetchSize(1000);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return statementWrapper;
    }

    @Override
    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        //Added for PostgreSQL, which returns type names like "userid," not underlying type name
        sqlTypeNameMap.put("USERID", Types.INTEGER);
        sqlTypeNameMap.put("SERIAL", Types.INTEGER);
        sqlTypeNameMap.put("ENTITYID", Types.VARCHAR);
        sqlTypeNameMap.put("INT2", Types.INTEGER);
        sqlTypeNameMap.put("INT4", Types.INTEGER);
        sqlTypeNameMap.put("INT8", Types.BIGINT);
        sqlTypeNameMap.put("FLOAT4", Types.REAL);
        sqlTypeNameMap.put("FLOAT8", Types.DOUBLE);
        sqlTypeNameMap.put("BOOL", Types.BOOLEAN);
        sqlTypeNameMap.put("BPCHAR", Types.CHAR);
        sqlTypeNameMap.put("LSIDTYPE", Types.VARCHAR);
        sqlTypeNameMap.put("TIMESTAMP", Types.TIMESTAMP);
    }

    @Override
    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
        sqlTypeIntMap.put(Types.BIT, "BOOLEAN");
        sqlTypeIntMap.put(Types.BOOLEAN, "BOOLEAN");
        sqlTypeIntMap.put(Types.CHAR, "CHAR");
        sqlTypeIntMap.put(Types.LONGVARBINARY, "LONGVARBINARY");
        sqlTypeIntMap.put(Types.LONGVARCHAR, "LONGVARCHAR");
        sqlTypeIntMap.put(Types.VARCHAR, "VARCHAR");
        sqlTypeIntMap.put(Types.TIMESTAMP, "TIMESTAMP");
        sqlTypeIntMap.put(Types.DOUBLE, "DOUBLE PRECISION");
        sqlTypeIntMap.put(Types.FLOAT, "DOUBLE PRECISION");
    }

    @Override
    public boolean isSqlServer()
    {
        return false;
    }

    @Override
    public boolean isPostgreSQL()
    {
        return true;
    }

    @Override
    public boolean isOracle()
    {
        return false;
    }

    @Override
    protected String getProductName()
    {
        return "PostgreSQL";
    }

    @Override
    public String getSQLScriptPath()
    {
        return "postgresql";
    }

    @Override
    public String getDefaultDateTimeDataType()
    {
        return "TIMESTAMP";
    }

    @Override
    public String getUniqueIdentType()
    {
        return "SERIAL";
    }

    @Override
    public String getGuidType()
    {
        return "VARCHAR";
    }

    @Override
    public boolean treatCatalogsAsSchemas()
    {
        return false;
    }

    @Override
    public void appendStatement(StringBuilder sql, String statement)
    {
        sql.append(";\n");
        sql.append(statement);
    }


    @Override
    public void appendSelectAutoIncrement(StringBuilder sql, TableInfo table, String columnName)
    {
        if (null == table.getSequence())
            appendStatement(sql, "SELECT CURRVAL('" + table.toString() + "_" + columnName + "_seq')");
        else
            appendStatement(sql, "SELECT CURRVAL('" + table.getSequence() + "')");
    }

    public boolean requiresStatementMaxRows()
    {
        return false;
    }

    @Override
    public SQLFragment limitRows(SQLFragment frag, int rowCount)
    {
        return limitRows(frag, rowCount, 0);
    }

    private SQLFragment limitRows(SQLFragment frag, int rowCount, long offset)
    {
        if (rowCount != Table.ALL_ROWS)
        {
            frag.append("\nLIMIT ");
            frag.append(Integer.toString(Table.NO_ROWS == rowCount ? 0 : rowCount));

            if (offset > 0)
            {
                frag.append(" OFFSET ");
                frag.append(Long.toString(offset));
            }
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

        SQLFragment sql = new SQLFragment();
        sql.append(select);
        sql.append("\n").append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        if (order != null) sql.append("\n").append(order);

        return limitRows(sql, rowCount, offset);
    }

    @Override
    public boolean supportsOffset()
    {
        return true;
    }

    @Override
    public boolean supportsComments()
    {
        return true;
    }

    @Override
    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        return "SELECT " + schema.getName() + "." + procedureName + "(" + parameters + ")";
    }

    @Override
    public SQLFragment execute(DbSchema schema, String procedureName, SQLFragment parameters)
    {
        SQLFragment select = new SQLFragment("SELECT " + schema.getName() + "." + procedureName + "(");
        select.append(parameters);
        select.append(")");
        return select;
    }

    @Override
    public String concatenate(String... args)
    {
        return StringUtils.join(args, " || ");
    }


    @Override
    public SQLFragment concatenate(SQLFragment... args)
    {
        SQLFragment ret = new SQLFragment();
        String op = "";
        for (SQLFragment arg : args)
        {
            ret.append(op).append(arg);
            op = " || ";
        }
        return ret;
    }


    @Override
    public String getCharClassLikeOperator()
    {
        return "SIMILAR TO";
    }

    @Override
    public String getCaseInsensitiveLikeOperator()
    {
        return "ILIKE";
    }

    @Override
    public String getVarcharLengthFunction()
    {
        return "length";
    }

    @Override
    public String getStdDevFunction()
    {
        return "stddev";
    }

    @Override
    public String getClobLengthFunction()
    {
        return "length";
    }

    @Override
    public String getStringIndexOfFunction(String stringToFind, String stringToSearch)
    {
        return "position(" + stringToFind + " in " + stringToSearch + ")";
    }

    @Override
    public String getSubstringFunction(String s, String start, String length)
    {
        return "substr(" + s + ", " + start + ", " + length + ")";
    }

    @Override
    public boolean supportsGroupConcat()
    {
        return true;
    }

    @Override
    public boolean supportsSelectConcat()
    {
        return true;
    }

    @Override
    public SQLFragment getSelectConcat(SQLFragment selectSql)
    {
        SQLFragment result = new SQLFragment("array_to_string(array(");
        result.append(selectSql);
        result.append("), ',')");

        return result;
    }

    @Override
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted)
    {
        SQLFragment result = new SQLFragment("array_to_string(");
        if (sorted)
        {
            result.append("core.sort(");
        }
        result.append("core.array_accum(");
        if (distinct)
        {
            result.append("DISTINCT ");
        }
        result.append(sql);
        result.append(")");
        if (sorted)
        {
            result.append(")");
        }
        result.append(", ',')");
        return result;
    }

    @Override
    protected String getSystemTableNames()
    {
        return "pg_logdir_ls";
    }

    @Override
    public boolean isSystemSchema(String schemaName)
    {
        return schemaName.equals("information_schema") || schemaName.equals("pg_catalog") || schemaName.startsWith("pg_toast_temp_");
    }

    @Override
    public String sanitizeException(SQLException ex)
    {
        if ("22001".equals(ex.getSQLState()))
        {
            return INPUT_TOO_LONG_ERROR_MESSAGE;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    @Override
    public String getAnalyzeCommandForTable(String tableName)
    {
        return "ANALYZE " + tableName;
    }

    @Override
    protected String getSIDQuery()
    {
        return "SELECT pg_backend_pid();";
    }

    @Override
    public String getBooleanDataType()
    {
        return "BOOLEAN";
    }

    @Override
    public String getBooleanTRUE()
    {
        return "true";
    }

    @Override
    public String getBooleanFALSE()
    {
        return "false";
    }

    @Override
    public String getBooleanLiteral(boolean b)
    {
        return Boolean.toString(b);
    }

    @Override
    public String getTempTableKeyword()
    {
        return "TEMPORARY";
    }

    @Override
    public String getTempTablePrefix()
    {
        return "";
    }

    @Override
    public String getGlobalTempTablePrefix()
    {
        return "temp.";
    }

    @Override
    public boolean isNoDatabaseException(SQLException e)
    {
        return "3D000".equals(e.getSQLState());
    }

    @Override
    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return true;
    }

    @Override
    public String getDropIndexCommand(String tableName, String indexName)
    {
        return "DROP INDEX " + indexName;
    }

    @Override
    public String getCreateDatabaseSql(String dbName)
    {
        return "CREATE DATABASE \"" + dbName + "\" WITH ENCODING 'UTF8';\n" +
                "ALTER DATABASE \"" + dbName + "\" SET default_with_oids TO OFF";
    }

    @Override
    public String getCreateSchemaSql(String schemaName)
    {
        if (!AliasManager.isLegalName(schemaName) || isReserved(schemaName))
            throw new IllegalArgumentException("Not a legal schema name: " + schemaName);

        //Quoted schema names are bad news
        return "CREATE SCHEMA " + schemaName;
    }

    @Override
    public String getDateDiff(int part, String value1, String value2)
    {
        int divideBy;
        switch (part)
        {
            case Calendar.DATE:
            {
                divideBy = 60 * 60 * 24;
                break;
            }
            case Calendar.HOUR:
            {
                divideBy = 60 * 60;
                break;
            }
            case Calendar.MINUTE:
            {
                divideBy = 60;
                break;
            }
            case Calendar.SECOND:
            {
                divideBy = 1;
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unsupported time unit: " + part);
            }
        }
        return "(EXTRACT(EPOCH FROM (" + value1 + " - " + value2 + ")) / " + divideBy + ")::INT";
    }

    @Override
    public String getDatePart(int part, String value)
    {
        return "EXTRACT(" + getDatePartName(part) + " FROM " + value + ")";
    }

    @Override
    public String getDateTimeToDateCast(String columnName)
    {
        return "DATE(" + columnName + ")";
    }

    @Override
    public String getRoundFunction(String valueToRound)
    {
        return "ROUND(" + valueToRound + "::double precision)";
    }

    @Override
    public boolean supportsRoundDouble()
    {
        return false;
    }

    @Override
    public void handleCreateDatabaseException(SQLException e) throws ServletException
    {
        if ("55006".equals(e.getSQLState()))
        {
            _log.error("You must close down pgAdmin III and all other applications accessing PostgreSQL.");
            throw (new ServletException("Close down or disconnect pgAdmin III and all other applications accessing PostgreSQL", e));
        }
        else
        {
            super.handleCreateDatabaseException(e);
        }
    }


    // Make sure that the PL/pgSQL language is enabled in the associated database.  If not, throw.  It would be nice
    // to CREATE LANGUAGE at this point, however, that requires SUPERUSER permissions and takes us down the path of
    // creating call handlers and other complexities.  It looks like PostgreSQL 8.1 has a simpler form of CREATE LANGUAGE...
    // once we require 8.1 we should consider using it here.

    @Override
    public void prepareNewDatabase(DbSchema schema) throws ServletException
    {
        ResultSet rs = null;

        try
        {
            rs = Table.executeQuery(schema, "SELECT * FROM pg_language WHERE lanname = 'plpgsql'", null);

            if (rs.next())
                return;

            String dbName = schema.getScope().getDatabaseName();
            String message = "PL/pgSQL is not enabled in the \"" + dbName + "\" database because it is not enabled in your Template1 master database.";
            String advice = "Use PostgreSQL's 'createlang' command line utility to enable PL/pgSQL in the \"" + dbName + "\" database then restart Tomcat.";

            throw new ConfigurationException(message, advice);
        }
        catch (SQLException e)
        {
            throw new ServletException("Failure attempting to verify language PL/pgSQL", e);
        }
        finally
        {
            try
            {
                if (null != rs) rs.close();
            }
            catch (SQLException e)
            {
                _log.error("prepareNewDatabase", e);
            }
        }
    }


    @Override
    public void prepareNewDbScope(DbScope scope) throws SQLException, IOException
    {
        super.prepareNewDbScope(scope);

        initializeUserDefinedTypes(scope);
    }

    // When a new PostgreSQL DbScope is created, we enumerate the domains (user-defined types) in the public schema
    // of the datasource, determine their "scale," and stash that information in a map associated with the DbScope.
    // When the PostgreSQLColumnMetaDataReader reads meta data, it returns these scale values for all domains.

    private void initializeUserDefinedTypes(DbScope scope) throws SQLException
    {
        // No synchronization on scales, but there's no harm if we execute this query twice.
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try
        {
            conn = scope.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT * FROM information_schema.domains WHERE domain_schema = 'public'");

            while (rs.next())
            {
                String domainName = rs.getString("domain_name");

                if ("integer".equals(rs.getString("data_type")))
                    _userDefinedTypeScales.put(domainName, 4);
                else
                    _userDefinedTypeScales.put(domainName, Integer.parseInt(rs.getString("character_maximum_length")));
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
            ResultSetUtil.close(stmt);

            if (null != conn)
                scope.releaseConnection(conn);
        }
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
        // Nothing special to do for the PostgreSQL dialect
    }

    @Override
    public String getSelectNameFromMetaDataName(String metaDataName)
    {
        // In addition to quoting keywords and names with special characters, quote any name with an upper case
        // character. PostgreSQL normally stores column/table names in all lower case, so an upper case character
        // coming out of metadata means the name must have been quoted at creation time and needs to be quoted. #11181
        if (StringUtilsLabKey.containsUpperCase(metaDataName))
            return quoteIdentifier(metaDataName);
        else
            return super.getSelectNameFromMetaDataName(metaDataName);
    }

    private static final Pattern JAVA_CODE_PATTERN = Pattern.compile("^\\s*SELECT\\s+core\\.executeJavaUpgradeCode\\s*\\(\\s*'(.+)'\\s*\\)\\s*;\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Override
    public void runSql(DbSchema schema, String sql, UpgradeCode upgradeCode, ModuleContext moduleContext) throws SQLException
    {
        SqlScriptParser parser = new SqlScriptParser(sql, null, JAVA_CODE_PATTERN, schema, upgradeCode, moduleContext);

        try
        {
            parser.execute();
        }
        catch (SQLException e)
        {
            if ("55000".equals(e.getSQLState()))
                //noinspection ThrowableInstanceNeverThrown
                throw new RuntimeException(new ConfigurationException("", "See https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=12401 " +
                    "for information about this error and possible work-arounds. Once the configuration problem is fixed, you can restart the server and " +
                    "the upgrade will continue.", e));
            else
                throw e;
        }
    }

    @Override
    protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
        if (lowerNoWhiteSpace.contains("setsearch_pathto"))
            errors.add("Do not use \"SET search_path TO <schema>\".  Instead, schema-qualify references to all objects.");

        if (!lowerNoWhiteSpace.endsWith(";"))
            errors.add("Script must end with a semicolon");
    }


    @Override
    public String getMasterDataBaseName()
    {
        return "template1";
    }


    /*
        PostgreSQL example connection URLs we need to parse:

        jdbc:postgresql:database
        jdbc:postgresql://host/database
        jdbc:postgresql://host:port/database
        jdbc:postgresql:database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host/database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host:port/database?user=fred&password=secret&ssl=true
    */

    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new StandardJdbcHelper("jdbc:postgresql:");
    }

    @Override
    protected String getDatabaseMaintenanceSql()
    {
        return "VACUUM ANALYZE;";
    }

    @Override
    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        SQLFragment ret = new SQLFragment(" POSITION(");
        ret.append(littleString);
        ret.append(" IN ");
        ret.append(bigString);
        ret.append(") ");
        return ret;
    }

    @Override
    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex)
    {
        SQLFragment tmp = new SQLFragment("position(");
        tmp.append(littleString);
        tmp.append(" in substring(");
        tmp.append(bigString);
        tmp.append(" from ");
        tmp.append(startIndex);
        tmp.append("))");
        SQLFragment ret = new SQLFragment("((");
        ret.append(startIndex);
        // TODO: code review this: I believe that this -1 is necessary to produce the correct results.
        ret.append(" - 1)");
        ret.append(" * sign(");
        ret.append(tmp);
        ret.append(")+");
        ret.append(tmp);
        ret.append(")");
        return ret;
    }

    @Override
    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return true;
    }

    private static final Pattern s_patStringLiteral = Pattern.compile("\\'([^\\\\\\']|(\\'\\')|(\\\\.))*\\'");

    @Override
    protected Pattern patStringLiteral()
    {
        return s_patStringLiteral;
    }

    @Override
    protected String quoteStringLiteral(String str)
    {
        return "'" + StringUtils.replace(StringUtils.replace(str, "\\", "\\\\"), "'", "''") + "'";
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
                sql.add("DROP TABLE " + makeTableIdentifier(change));
                break;
            case AddColumns:
                sql.add(getAddColumnsStatement(change));
                break;
            case DropColumns:
                sql.add(getDropColumnsStatement(change));
                break;
            case RenameColumns:
                sql.addAll(getRenameColumnsStatement(change));
                break;
        }

        return sql;
    }

    private List<String> getRenameColumnsStatement(TableChange change)
    {

        List<String> statements = new ArrayList<String>();
        for (Map.Entry<String, String> oldToNew : change.getColumnRenames().entrySet())
        {
            statements.add(String.format("ALTER TABLE %s.%s RENAME COLUMN %s TO %s",
                    change.getSchemaName(), change.getTableName(),
                    makePropertyIdentifier(oldToNew.getKey()),
                    makePropertyIdentifier(oldToNew.getValue())));
        }

        return statements;
    }

    private String getDropColumnsStatement(TableChange change)
    {
        List<String> sqlParts = new ArrayList<String>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            sqlParts.add("DROP COLUMN " + makePropertyIdentifier(prop.getName()));
        }

        return String.format("ALTER TABLE %s %s", makeTableIdentifier(change), StringUtils.join(sqlParts, ", "));
    }

    // TODO if there are cases where user-defined columns need indices, this method will need to support
    // creating indices like getCreateTableStatement does.

    private String getAddColumnsStatement(TableChange change)
    {
        List<String> sqlParts = new ArrayList<String>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            sqlParts.add("ADD COLUMN " + getSqlColumnSpec(prop));
        }

        return String.format("ALTER TABLE %s %s", makeTableIdentifier(change), StringUtils.join(sqlParts, ", "));
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

        statements.add(String.format("CREATE TABLE %s (%s)", makeTableIdentifier(change), StringUtils.join(createTableSqlParts, ", ")));
        if (null != pkColumn)
            statements.add(String.format("ALTER TABLE %s ADD CONSTRAINT %s PRIMARY KEY(%s)",
                    makeTableIdentifier(change),
                    change.getTableName() + "_pk",
                    pkColumn));

        for (PropertyStorageSpec.Index index : change.getIndexedColumns())
        {
            statements.add(String.format("CREATE %s INDEX %s ON %s (%s)", index.isUnique ? "UNIQUE" : "", nameIndex(change.getTableName(), index.columnNames), makeTableIdentifier(change), StringUtils.join(index.columnNames, ", ")));
        }

        return statements;
    }

    private String nameIndex(String tableName, String[] indexedColumns)
    {
        return AliasManager.makeLegalName(tableName + '_' + StringUtils.join(indexedColumns, "_"), this);
    }

    private String getSqlColumnSpec(PropertyStorageSpec prop)
    {
        List<String> colSpec = new ArrayList<String>();
        colSpec.add(makePropertyIdentifier(prop.getName()));
        colSpec.add(sqlTypeNameFromSqlType(prop));
        if (prop.getSqlTypeInt() == Types.VARCHAR)
            colSpec.add("(" + prop.getSize() + ")");
        else if (prop.getSqlTypeInt() == Types.NUMERIC)
            colSpec.add("(15,4)");
        if (prop.isPrimaryKey())
            colSpec.add("NOT NULL");
        return StringUtils.join(colSpec, ' ');
    }

    private String makeTableIdentifier(TableChange change)
    {
        assert AliasManager.isLegalName(change.getTableName());
        return change.getSchemaName() + "." + change.getTableName();
    }

    @Override
    protected String sqlTypeNameFromSqlType(PropertyStorageSpec prop)
    {
        if (prop.isAutoIncrement())
        {
            if (prop.getSqlTypeInt() == Types.INTEGER)
            {
                return "SERIAL";
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

    private String makePropertyIdentifier(String name)
    {
        return "\"" + name.toLowerCase() + "\"";
    }


    @Override
    public void initializeConnection(Connection conn) throws SQLException
    {
    }

    @Override
    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        try
        {
            DbSchema coreSchema = CoreSchema.getInstance().getSchema();
            DbScope scope = coreSchema.getScope();
            String tempSchemaName = getGlobalTempTablePrefix();
            if (tempSchemaName.endsWith("."))
                tempSchemaName = tempSchemaName.substring(0, tempSchemaName.length() - 1);
            String dbName = getDatabaseName(scope.getDataSourceName(), scope.getDataSource());

            Connection conn = null;
            ResultSet rs = null;
            Object noref = new Object();
            try
            {
                conn = scope.getConnection();
                rs = conn.getMetaData().getTables(dbName, tempSchemaName, "%", new String[]{"TABLE"});
                while (rs.next())
                {
                    String table = rs.getString("TABLE_NAME");
                    String tempName = getGlobalTempTablePrefix() + table;
                    if (!createdTableNames.containsKey(tempName))
                        TempTableTracker.track(coreSchema, tempName, noref);
                }
                rs.close(); rs = null;

                //rs = conn.getMetaData().getFunctions(dbName, tempSchemaName, "%");
                Map types = null;
                rs = Table.executeQuery(coreSchema, "SELECT proname AS SPECIFIC_NAME, CAST(proargtypes AS VARCHAR) FROM pg_proc WHERE pronamespace=(select oid from pg_namespace where nspname = ?)", new Object[]{tempSchemaName});
                while (rs.next())
                {
                    if (null == types)
                        types = Table.executeValueMap(coreSchema, "SELECT CAST(oid AS VARCHAR), typname FROM pg_type", null, new HashMap());
                    String name = rs.getString(1);
                    String[] oids = StringUtils.split(rs.getString(2), ' ');
                    SQLFragment drop = new SQLFragment("DROP FUNCTION temp.").append(name);
                    drop.append("(");
                    String comma = "";
                    for (String oid : oids)
                    {
                        drop.append(comma).append(types.get(oid));
                        comma = ",";
                    }
                    drop.append(")");
                    Table.execute(coreSchema, drop);
                }
                rs.close(); rs = null;
            }
            finally
            {
                ResultSetUtil.close(rs);
                if (null != conn)
                    scope.releaseConnection(conn);
            }
        }
        catch (SQLException x)
        {
            _log.warn("error cleaning up temp schema", x);
        }
        catch (ServletException x)
        {
            _log.warn("error cleaning up temp schema", x);
        }
    }

    @Override
    public boolean isCaseSensitive()
    {
        return true;
    }

    @Override
    public boolean isEditable()
    {
        return true;
    }

    @Override
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, DbSchema schema)
    {
        // Retrieve and pass in the previously queried scale values for this scope.
        return new PostgreSQLColumnMetaDataReader(rsCols, schema);
    }


    private class PostgreSQLColumnMetaDataReader extends ColumnMetaDataReader
    {
        private final DbSchema _schema;

        public PostgreSQLColumnMetaDataReader(ResultSet rsCols, DbSchema schema)
        {
            super(rsCols);
            _schema = schema;
            assert null != _userDefinedTypeScales;

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
            String typeName = getSqlTypeName();
            return typeName.equalsIgnoreCase("serial") || typeName.equalsIgnoreCase("bigserial");
        }

        @Override
        public int getSqlType() throws SQLException
        {
            int sqlType = super.getSqlType();

            // PostgreSQL 8.3 returns DISTINCT for user-defined types
            if (Types.DISTINCT == sqlType)
                return _rsCols.getInt("SOURCE_DATA_TYPE");
            else
                return sqlType;
        }

        @Override
        public int getScale() throws SQLException
        {
            int sqlType = super.getSqlType();

            if (Types.DISTINCT == sqlType)
            {
                String typeName = getSqlTypeName();
                Integer scale = _userDefinedTypeScales.get(typeName);

                if (null == scale)
                {
                    // Some domain wasn't there when we initialized the datasource, so reload now.  This will happpen at bootstrap.
                    initializeUserDefinedTypes(_schema.getScope());
                    scale = _userDefinedTypeScales.get(typeName);
                }

                assert scale != null;

                if (null != scale)
                    return scale.intValue();
            }

            return super.getScale();
        }

        @Override
        public String getSequence() throws SQLException
        {
            String src = _rsCols.getString("COLUMN_DEF");

            int start = src.indexOf('\'');
            int end = src.lastIndexOf('\'');

            if (end > start)
            {
                String sequence = src.substring(start + 1, end);
                if (!sequence.toLowerCase().startsWith(_schema.getName().toLowerCase() + "."))
                    sequence = _schema.getName() + "." + sequence;

                return sequence;
            }

            return null;
        }
    }

    @Override
    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ");
    }


    @Override
    public String getExtraInfo(SQLException e)
    {
        // Deadlock between two different DB connections
        if ("40P01".equals(e.getSQLState()))
        {
            return getOtherDatabaseThreads();
        }
        return null;
    }
}
