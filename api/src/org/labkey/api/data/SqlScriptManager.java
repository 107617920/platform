/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * User: adam
 * Date: Sep 20, 2007
 * Time: 3:14:35 PM
 */
public abstract class SqlScriptManager
{
    protected final SqlScriptProvider _provider;
    protected final DbSchema _schema;

    protected abstract TableInfo getTableInfoSqlScripts();
    protected abstract @Nullable TableInfo getTableInfoSchemas();

    public static SqlScriptManager get(SqlScriptProvider provider, DbSchema schema)   // TODO: User? Module? Module Context?
    {
        if (schema.getScope().isLabKeyScope())
            return new CoreSqlScriptManager(provider, schema);
        else
            return new ExternalDataSourceSqlScriptManager(provider, schema);
    }


    private SqlScriptManager(SqlScriptProvider provider, DbSchema schema)
    {
        _provider = provider;
        _schema = schema;
    }


    // Returns all the scripts associated with schema that have not been run
    public List<SqlScript> getNewScripts() throws SQLException
    {
        List<SqlScript> allScripts;

        try
        {
            allScripts = _provider.getScripts(_schema);
        }
        catch(SqlScriptException e)
        {
            throw new RuntimeException(e);
        }

        List<SqlScript> newScripts = new ArrayList<>();
        Set<SqlScript> runScripts = getPreviouslyRunScripts();

        for (SqlScript script : allScripts)
            if (!runScripts.contains(script))
                newScripts.add(script);

        return newScripts;
    }


    public List<SqlScript> getRecommendedScripts(double from, double to) throws SQLException, SqlScriptException
    {
        List<SqlScript> newScripts = getNewScripts();
        return getRecommendedScripts(newScripts, from, to);
    }


    // Get the recommended scripts from a given collection of scripts
    public List<SqlScript> getRecommendedScripts(Collection<SqlScript> schemaScripts, double from, double to)
    {
        // Create a map of SqlScript objects.  For each fromVersion, store only the script with the highest toVersion
        Map<Double, SqlScript> m = new HashMap<>();

        for (SqlScript script : schemaScripts)
        {
            if (script.getFromVersion() >= from && script.getToVersion() <= to)
            {
                SqlScript current = m.get(script.getFromVersion());

                if (null == current || script.getToVersion() > current.getToVersion())
                    m.put(script.getFromVersion(), script);
            }
        }

        List<SqlScript> scripts = new ArrayList<>();

        while (true)
        {
            SqlScript nextScript = getNearestFrom(m, from);

            if (null == nextScript)
                break;

            from = nextScript.getToVersion();
            scripts.add(nextScript);
        }

        return scripts;
    }


    private static SqlScript getNearestFrom(Map<Double, SqlScript> m, double targetFrom)
    {
        SqlScript nearest = m.get(targetFrom);

        if (null == nearest)
        {
            double lowest = Double.MAX_VALUE;

            for (double from : m.keySet())
            {
                if (from >= targetFrom && from < lowest)
                    lowest = from;
            }

            nearest = m.get(lowest);
        }

        return nearest;
    }


    // Return all sql scripts that have been run by this provider
    public Set<SqlScript> getPreviouslyRunScripts() throws SQLException
    {
        Collection<String> runFilenames = getPreviouslyRunSqlScriptNames();
        Set<SqlScript> runScripts = new HashSet<>(runFilenames.size());

        for (String filename : runFilenames)
        {
            SqlScript script = _provider.getScript(_schema, filename);

            if (null != script)
                runScripts.add(script);
        }

        return runScripts;
    }


    public void runScript(@Nullable User user, SqlScript script, ModuleContext moduleContext, @Nullable Connection conn) throws SqlScriptException, SQLException
    {
        DbSchema schema = script.getSchema();
        SqlDialect dialect = schema.getSqlDialect();
        String contents = script.getContents();

        if (contents.isEmpty())
        {
            String error = script.getErrorMessage();

            if (null != error)
                throw new SqlScriptException(error, script.getDescription());

            return;
        }

        try
        {
            dialect.checkSqlScript(contents);
            Logger.getLogger(SqlScriptManager.class).info("start running script : " + script.getDescription());
            dialect.runSql(schema, contents, script.getProvider().getUpgradeCode(), moduleContext, conn);
            Logger.getLogger(SqlScriptManager.class).info("finished running script : " + script.getDescription());
        }
        catch(SQLException e)
        {
            throw new SqlScriptException(e, script.getDescription());
        }

        if (script.isValidName())
        {
            if (hasBeenRun(script))
                update(user, script);
            else
                insert(user, script);
        }
    }


    @NotNull
    public Collection<String> getPreviouslyRunSqlScriptNames()
    {
        TableInfo tinfo = getTableInfoSqlScripts();
        SimpleFilter filter = new SimpleFilter();
        ColumnInfo fileNameColumn = tinfo.getColumn("FileName");
        filter.addCondition(tinfo.getColumn("ModuleName"), _provider.getProviderName());
        filter.addCondition(tinfo.getColumn("FileName"), _schema.getDisplayName(), CompareType.STARTS_WITH);

        return new TableSelector(tinfo, Collections.singleton(fileNameColumn), filter, null).getCollection(String.class);
    }


    public boolean hasBeenRun(SqlScript script)
    {
        TableInfo tinfo = getTableInfoSqlScripts();
        PkFilter filter = new PkFilter(tinfo, new String[]{_provider.getProviderName(), script.getDescription()});

        return new TableSelector(getTableInfoSqlScripts(), filter, null).exists();
    }


    public void insert(@Nullable User user, SqlScript script) throws SQLException
    {
        SqlScriptBean ss = new SqlScriptBean(script.getProvider().getProviderName(), script.getDescription());

        Table.insert(user, getTableInfoSqlScripts(), ss);
    }


    public void update(@Nullable User user, SqlScript script) throws SQLException
    {
        Object[] pk = new Object[]{script.getProvider().getProviderName(), script.getDescription()};

        Table.update(user, getTableInfoSqlScripts(), new HashMap(), pk);  // Update user and modified date
    }


    public void updateSchemaVersion(double version)
    {
        TableInfo tinfo = getTableInfoSchemas();

        if (null != tinfo)
        {
            SchemaBean bean = getSchemaBean();

            try
            {
                if (null == bean)
                {
                    bean = new SchemaBean(_schema.getDisplayName(), _provider.getProviderName(), version);
                    Table.insert(ModuleLoader.getInstance().getUpgradeUser(), tinfo, bean);
                }
                else
                {
                    bean.setInstalledVersion(version);
                    Table.update(ModuleLoader.getInstance().getUpgradeUser(), tinfo, bean, bean.getName());
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    }


    public @NotNull SchemaBean ensureSchemaBean()
    {
        SchemaBean bean = getSchemaBean();

        return null != bean ? bean : new SchemaBean(_schema.getDisplayName(), _provider.getProviderName(), 0);
    }


    protected @Nullable SchemaBean getSchemaBean()
    {
        if (null == getTableInfoSchemas())
            return null;

        return new TableSelector(getTableInfoSchemas()).getObject(_schema.getDisplayName(), SchemaBean.class);
    }


    public static class SchemaBean extends Entity
    {
        private String _name;
        private String _moduleName;
        private double _installedVersion;

        @SuppressWarnings("UnusedDeclaration")  // Used by ObjectFactory reflection
        public SchemaBean()
        {
        }

        private SchemaBean(String name, String moduleName, double installedVersion)
        {
            setName(name);
            setModuleName(moduleName);
            setInstalledVersion(installedVersion);
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getModuleName()
        {
            return _moduleName;
        }

        public void setModuleName(String moduleName)
        {
            _moduleName = moduleName;
        }

        public double getInstalledVersion()
        {
            return _installedVersion;
        }

        public void setInstalledVersion(double installedVersion)
        {
            _installedVersion = installedVersion;
        }
    }


    private static class CoreSqlScriptManager extends SqlScriptManager
    {
        private CoreSqlScriptManager(SqlScriptProvider provider, DbSchema schema)
        {
            super(provider, schema);
        }

        @Override
        protected TableInfo getTableInfoSqlScripts()
        {
            return CoreSchema.getInstance().getTableInfoSqlScripts();
        }

        @Nullable
        @Override
        protected TableInfo getTableInfoSchemas()
        {
            // We don't version core schemas (only modules). We could: just add the table and return its TableInfo here.
            return null;
        }

        @NotNull
        @Override
        public Collection<String> getPreviouslyRunSqlScriptNames()
        {
            // Skip if the table hasn't been created yet (bootstrap case)
            if (getTableInfoSqlScripts().getTableType() == DatabaseTableType.NOT_IN_DB)
                return Collections.emptySet();

            return super.getPreviouslyRunSqlScriptNames();
        }

        @Override
        public boolean hasBeenRun(SqlScript script)
        {
            // Make sure DbSchema thinks SqlScript table is in the database.  If not, we're bootstrapping and it's either just before or just after the first
            // script is run.  In either case, invalidate to force reloading schema from database meta data.
            if (getTableInfoSqlScripts().getTableType() == DatabaseTableType.NOT_IN_DB)
            {
                CacheManager.clearAllKnownCaches();
                return false;
            }

            return super.hasBeenRun(script);
        }
    }


    private static class ExternalDataSourceSqlScriptManager extends SqlScriptManager
    {
        private DbSchema getLabKeySchema()
        {
            return _schema.getScope().getSchema("labkey");
        }

        private ExternalDataSourceSqlScriptManager(SqlScriptProvider provider, DbSchema schema)
        {
            super(provider, schema);
        }

        protected TableInfo getTableInfoSqlScripts()
        {
            TableInfo scripts = getLabKeySchema().getTable("SqlScripts");

            // New data source bootstrap case
            if (null == scripts)
            {
                CacheManager.clearAllKnownCaches();
                scripts = getLabKeySchema().getTable("SqlScripts");
            }

            return scripts;
        }

        @Nullable
        @Override
        protected TableInfo getTableInfoSchemas()
        {
            return getLabKeySchema().getTable("Schemas");
        }
    }
}
