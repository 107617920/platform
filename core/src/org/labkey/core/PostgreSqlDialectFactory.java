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

package org.labkey.core;

import org.apache.log4j.Logger;
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
import org.labkey.api.util.VersionNumber;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 9:19:38 PM
*/
public class PostgreSqlDialectFactory extends SqlDialectFactory
{
    private static final Logger _log = Logger.getLogger(PostgreSqlDialectFactory.class);

    private String getProductName()
    {
        return "PostgreSQL";
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        if ("org.postgresql.Driver".equals(driverClassName))
            return new PostgreSql83Dialect();
        else
            return null;
    }

    private final String _recommended = getProductName() + " 9.0 is the recommended version.";

    @Override
    public @Nullable SqlDialect createFromProductNameAndVersion(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings) throws DatabaseNotSupportedException
    {
        if (!getProductName().equals(dataBaseProductName))
            return null;

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion);
        int version = versionNumber.getVersionInt();

        // Version 8.3 or greater is allowed...
        if (version >= 83)
        {
            if (83 == version)
            {
                // ...but warn for anything less than 8.3.7
                if (logWarnings && versionNumber.getRevisionAsInt() < 7)
                    _log.warn("LabKey Server has known issues with " + getProductName() + " version " + databaseProductVersion + ".  " + _recommended);

                return new PostgreSql83Dialect();
            }

            if (84 == version)
                return new PostgreSql84Dialect();

            if (90 == version)
                return new PostgreSql90Dialect();

            if (version > 90)
            {
                if (logWarnings)
                    _log.warn("LabKey Server has not been tested against " + getProductName() + " version " + databaseProductVersion + ".  " + _recommended);

                return new PostgreSql91Dialect();
            }
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported.  You must upgrade your database server installation; " + _recommended);
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
            badProductName("Gobbledygood", 8.0, 8.5, "");
            badProductName("Postgres", 8.0, 8.5, "");
            badProductName("postgresql", 8.0, 8.5, "");

            // 8.2 or lower should result in bad version number
            badVersion("PostgreSQL", 0.0, 8.2, null);

            // >= 8.3 should be good
            good("PostgreSQL", 8.3, 8.4, "", PostgreSql83Dialect.class);
            good("PostgreSQL", 8.4, 8.5, "", PostgreSql84Dialect.class);
            good("PostgreSQL", 9.0, 9.1, "", PostgreSql90Dialect.class);
            good("PostgreSQL", 9.1, 11.0, "", PostgreSql91Dialect.class);
        }
    }

    public static class JavaUpgradeCodeTestCase extends Assert
    {
        @Test
        public void testJavaUpgradeCode()
        {
            String goodSql =
                    "SELECT core.executeJavaUpgradeCode('upgradeCode');\n" +                       // Normal
                            "    SELECT     core.executeJavaUpgradeCode    ('upgradeCode')    ;     \n" +  // Lots of whitespace
                            "select CORE.EXECUTEJAVAUPGRADECODE('upgradeCode');\n" +                       // Case insensitive
                            "SELECT core.executeJavaUpgradeCode('upgradeCode');";                          // No line ending


            String badSql =
                    "/* SELECT core.executeJavaUpgradeCode('upgradeCode');\n" +       // Inside block comment
                            "   more comment\n" +
                            "*/" +
                            "    -- SELECT core.executeJavaUpgradeCode('upgradeCode');\n" +   // Inside single-line comment
                            "SELECTcore.executeJavaUpgradeCode('upgradeCode');\n" +           // Bad syntax
                            "SELECT core. executeJavaUpgradeCode('upgradeCode');\n" +         // Bad syntax
                            "SEECT core.executeJavaUpgradeCode('upgradeCode');\n" +           // Misspell SELECT
                            "SELECT core.executeJaavUpgradeCode('upgradeCode');\n" +          // Misspell function name
                            "SELECT core.executeJavaUpgradeCode('upgradeCode')\n";            // No semicolon

            try
            {
                SqlDialect dialect = new PostgreSql83Dialect();
                TestUpgradeCode good = new TestUpgradeCode();
                dialect.runSql(null, goodSql, good, null);
                assertEquals(4, good.getCounter());

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
                    return new PostgreSql83Dialect();
                }

                @NotNull
                @Override
                protected Set<String> getGoodUrls()
                {
                    return new CsvSet("jdbc:postgresql:database," +
                        "jdbc:postgresql://localhost/database," +
                        "jdbc:postgresql://localhost:8300/database," +
                        "jdbc:postgresql://www.host.com/database," +
                        "jdbc:postgresql://www.host.com:8499/database," +
                        "jdbc:postgresql:database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://localhost/database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://localhost:8672/database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://www.host.com/database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://www.host.com:8992/database?user=fred&password=secret&ssl=true");
                }

                @NotNull
                @Override
                protected Set<String> getBadUrls()
                {
                    return new CsvSet("jddc:postgresql:database," +
                            "jdbc:postgres://localhost/database," +
                            "jdbc:postgresql://www.host.comdatabase");
                }
            };

            test.test();
        }
    }
}
