/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.dialect.AbstractDialectRetrievalTestCase;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.JdbcHelperTest;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.data.dialect.TestUpgradeCode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.VersionNumber;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 9:51:40 PM
*/
public class MicrosoftSqlServerDialectFactory extends SqlDialectFactory
{
    private String getProductName()
    {
        return "Microsoft SQL Server";
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        if ("net.sourceforge.jtds.jdbc.Driver".equals(driverClassName))
            return new MicrosoftSqlServer2005Dialect();
        else
            return null;
    }

    @Override
    public @Nullable SqlDialect createFromProductNameAndVersion(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings) throws DatabaseNotSupportedException
    {
        if (!dataBaseProductName.equals(getProductName()))
            return null;

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion);
        int version = versionNumber.getVersionInt();

        // Good resource for past & current SQL Server version numbers: http://www.sqlteam.com/article/sql-server-versions

        if (version >= 110)
            return new MicrosoftSqlServer2012Dialect();

        if (version >= 105)
            return new MicrosoftSqlServer2008R2Dialect();

        if (version >= 100)
            return new MicrosoftSqlServer2008Dialect();

        if (version >= 90)
            return new MicrosoftSqlServer2005Dialect();

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported.");
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Arrays.asList(DialectRetrievalTestCase.class, JavaUpgradeCodeTestCase.class, JdbcHelperTestCase.class);
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        return PageFlowUtil.set(new MicrosoftSqlServer2005Dialect(), new MicrosoftSqlServer2008Dialect(), new MicrosoftSqlServer2008R2Dialect());
    }

    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        public void testDialectRetrieval()
        {
            // These should result in bad database exception
            badProductName("Gobbledygood", 1.0, 12.0, "");
            badProductName("SQL Server", 1.0, 12.0, "");
            badProductName("sqlserver", 1.0, 12.0, "");

            // < 9.0 should result in bad version error
            badVersion("Microsoft SQL Server", 0.0, 8.9, null);

            // >= 9.0 and < 10.0 should result in MicrosoftSqlServer2005Dialect
            good("Microsoft SQL Server", 9.0, 9.9, "", MicrosoftSqlServer2005Dialect.class);

            // >= 10.0 and < 10.5 should result in MicrosoftSqlServer2008Dialect
            good("Microsoft SQL Server", 10.0, 10.4, "", MicrosoftSqlServer2008Dialect.class);

            // >= 10.5 and < 11.0 should result in MicrosoftSqlServer2008R2Dialect
            good("Microsoft SQL Server", 10.5, 10.9, "", MicrosoftSqlServer2008R2Dialect.class);

            // >= 11.0 should result in MicrosoftSqlServer2012Dialect
            good("Microsoft SQL Server", 11.0, 12.0, "", MicrosoftSqlServer2012Dialect.class);
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
                SqlDialect dialect = new MicrosoftSqlServer2005Dialect();
                TestUpgradeCode good = new TestUpgradeCode();
                dialect.runSql(null, goodSql, good, null);
                assertEquals(10, good.getCounter());

                TestUpgradeCode bad = new TestUpgradeCode();
                dialect.runSql(null, badSql, bad, null);
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
            JdbcHelperTest test = new JdbcHelperTest() {
                @NotNull
                @Override
                protected SqlDialect getDialect()
                {
                    return new MicrosoftSqlServer2005Dialect();
                }

                @NotNull
                @Override
                protected Set<String> getGoodUrls()
                {
                    return new CsvSet("jdbc:jtds:sqlserver://localhost/database," +
                        "jdbc:jtds:sqlserver://localhost:1433/database," +
                        "jdbc:jtds:sqlserver://localhost/database;SelectMethod=cursor," +
                        "jdbc:jtds:sqlserver://localhost:1433/database;SelectMethod=cursor," +
                        "jdbc:jtds:sqlserver://www.host.com/database," +
                        "jdbc:jtds:sqlserver://www.host.com:1433/database," +
                        "jdbc:jtds:sqlserver://www.host.com/database;SelectMethod=cursor," +
                        "jdbc:jtds:sqlserver://www.host.com:1433/database;SelectMethod=cursor");
                }

                @NotNull
                @Override
                protected Set<String> getBadUrls()
                {
                    return new CsvSet("jdb:jtds:sqlserver://localhost/database," +
                        "jdbc:jts:sqlserver://localhost/database," +
                        "jdbc:jtds:sqlerver://localhost/database," +
                        "jdbc:jtds:sqlserver://localhostdatabase," +
                        "jdbc:jtds:sqlserver:database");
                }
            };

            test.test();
        }
    }
}
