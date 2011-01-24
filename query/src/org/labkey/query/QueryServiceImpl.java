/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.query;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Category;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Filter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.MetadataException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.data.SimpleUserSchema;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.QuerySnapshotDef;
import org.labkey.query.sql.Query;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class QueryServiceImpl extends QueryService
{
    private static final Cache<String, Object> MODULE_RESOURCES_CACHE = CacheManager.getCache(1024, CacheManager.DAY, "Module resources cache");
    private static final String QUERYDEF_SET_CACHE_ENTRY = "QUERYDEFS:";
    private static final String QUERYDEF_METADATA_SET_CACHE_ENTRY = "QUERYDEFSMETADATA:";
    private static final String CUSTOMVIEW_SET_CACHE_ENTRY = "CUSTOMVIEW:";

    static public QueryServiceImpl get()
    {
        return (QueryServiceImpl)QueryService.get();
    }

    public UserSchema getUserSchema(User user, Container container, String schemaName)
    {
        QuerySchema ret = DefaultSchema.get(user, container).getSchema(schemaName);
        if (ret instanceof UserSchema)
        {
            return (UserSchema) ret;
        }
        return null;
    }

    public QueryDefinition createQueryDef(User user, Container container, String schema, String name)
    {
        return new CustomQueryDefinitionImpl(user, container, schema, name);
    }

    public QueryDefinition createQueryDef(User user, Container container, UserSchema schema, String name)
    {
        return new CustomQueryDefinitionImpl(user, container, schema, name);
    }

    public ActionURL urlQueryDesigner(User user, Container container, String schema)
    {
        return urlFor(user, container, QueryAction.begin, schema, null);
    }

    public ActionURL urlFor(User user, Container container, QueryAction action, String schema, String query)
    {
        ActionURL ret = null;

        if (schema != null && query != null)
        {
            UserSchema userschema = QueryService.get().getUserSchema(user, container, schema);
            if (userschema != null)
            {
                QueryDefinition queryDef = QueryService.get().getQueryDef(user, container, schema, query);
                if (queryDef == null)
                    queryDef = userschema.getQueryDefForTable(query);
                if (queryDef != null)
                    ret = userschema.urlFor(action, queryDef);
            }
        }

        if (ret == null)
        {
            // old behavior for backwards compatibility
            ret = new ActionURL("query", action.toString(), container);
            if (schema != null)
                ret.addParameter(QueryParam.schemaName.toString(), schema);
            if (query != null)
                ret.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, query);
        }

        return ret;
    }

    public QueryDefinition createQueryDefForTable(UserSchema schema, String tableName)
    {
        return new TableQueryDefinition(schema, tableName);
    }

    public Map<String, QueryDefinition> getQueryDefs(User user, Container container, String schemaName)
    {
        Map<String, QueryDefinition> ret = new LinkedHashMap<String,QueryDefinition>();

        for (QueryDefinition queryDef : getAllQueryDefs(user, container, schemaName, true, false).values())
            ret.put(queryDef.getName(), queryDef);

        return ret;
    }

    public List<QueryDefinition> getQueryDefs(User user, Container container)
    {
        return new ArrayList<QueryDefinition>(getAllQueryDefs(user, container, null, true, false).values());
    }

    private Map<Map.Entry<String, String>, QueryDefinition> getAllQueryDefs(User user, Container container, String schemaName, boolean inheritable, boolean includeSnapshots)
    {
        return getAllQueryDefs(user, container, schemaName, inheritable, includeSnapshots, false);
    }

    private Map<Map.Entry<String, String>, QueryDefinition> getAllQueryDefs(User user, Container container, String schemaName,
                                                                            boolean inheritable, boolean includeSnapshots, boolean allModules)
    {
        Map<Map.Entry<String, String>, QueryDefinition> ret = new LinkedHashMap<Map.Entry<String, String>, QueryDefinition>();

        //look in all the active modules in this container to see if they contain any query definitions
        if (null != schemaName)
        {
            Collection<Module> modules = allModules ? ModuleLoader.getInstance().getModules() : container.getActiveModules();

            for (Module module : modules)
            {
                Collection<? extends Resource> queries;

                //always scan the file system in dev mode
                if (AppProps.getInstance().isDevMode())
                {
                    Resource schemaDir = module.getModuleResolver().lookup(new Path(MODULE_QUERIES_DIRECTORY, schemaName));
                    queries = getModuleQueries(schemaDir, ModuleQueryDef.FILE_EXTENSION);
                }
                else
                {
                    //in production, cache the set of query defs for each module on first request
                    String fileSetCacheKey = QUERYDEF_SET_CACHE_ENTRY + module.toString() + "." + schemaName;
                    //noinspection unchecked
                    queries = (Collection<? extends Resource>) MODULE_RESOURCES_CACHE.get(fileSetCacheKey);

                    if (null == queries)
                    {
                        Resource schemaDir = module.getModuleResolver().lookup(new Path(MODULE_QUERIES_DIRECTORY, schemaName));
                        queries = getModuleQueries(schemaDir, ModuleQueryDef.FILE_EXTENSION);
                        MODULE_RESOURCES_CACHE.put(fileSetCacheKey, queries);
                    }
                }

                if (null != queries)
                {
                    for (Resource query : queries)
                    {
                        String cacheKey = query.getPath().toString();
                        ModuleQueryDef moduleQueryDef = (ModuleQueryDef) MODULE_RESOURCES_CACHE.get(cacheKey);
                        if (null == moduleQueryDef || moduleQueryDef.isStale())
                        {
                            moduleQueryDef = new ModuleQueryDef(query, schemaName);
                            MODULE_RESOURCES_CACHE.put(cacheKey, moduleQueryDef);
                        }

                        ret.put(new Pair<String,String>(schemaName, moduleQueryDef.getName()),
                                new ModuleCustomQueryDefinition(moduleQueryDef, user, container));
                    }
                }
            }
        }

        HttpServletRequest request = HttpView.currentRequest();
        if (request != null && schemaName != null)
        {
            for (QueryDefinition qdef : getAllSessionQueries(request, user, container, schemaName))
            {
                Map.Entry<String, String> key = new Pair<String,String>(schemaName, qdef.getName());
                ret.put(key, qdef);
            }
        }

        for (QueryDef queryDef : QueryManager.get().getQueryDefs(container, schemaName, false, includeSnapshots, true))
        {
            Map.Entry<String, String> key = new Pair<String,String>(queryDef.getSchema(), queryDef.getName());
            ret.put(key, new CustomQueryDefinitionImpl(user, queryDef));
        }

        if (!inheritable)
            return ret;

        Container containerCur = container;

        while (!containerCur.isRoot())
        {
            containerCur = containerCur.getParent();

            for (QueryDef queryDef : QueryManager.get().getQueryDefs(containerCur, schemaName, true, includeSnapshots, true))
            {
                Map.Entry<String, String> key = new Pair<String,String>(queryDef.getSchema(), queryDef.getName());

                if (!ret.containsKey(key))
                    ret.put(key, new CustomQueryDefinitionImpl(user, queryDef));
            }
        }

        // look in the Shared project
        for (QueryDef queryDef : QueryManager.get().getQueryDefs(ContainerManager.getSharedContainer(), schemaName, true, includeSnapshots, true))
        {
            Map.Entry<String, String> key = new Pair<String,String>(queryDef.getSchema(), queryDef.getName());

            if (!ret.containsKey(key))
                ret.put(key, new CustomQueryDefinitionImpl(user, queryDef));
        }

        return ret;
    }

    private Collection<? extends Resource> getModuleQueries(Resource schemaDir, String fileExtension)
    {
        if (schemaDir == null)
            return Collections.emptyList();

        Collection<? extends Resource> queries = schemaDir.list();
        List<Resource> result = new ArrayList<Resource>(queries.size());
        for (Resource query : queries)
            if (query.getName().endsWith(fileExtension))
                result.add(query);

        return result;
    }

    public QueryDefinition getQueryDef(User user, Container container, String schema, String name)
    {
        Map<String, QueryDefinition> ret = new LinkedHashMap<String, QueryDefinition>();

        for (QueryDefinition queryDef : getAllQueryDefs(user, container, schema, true, true, false).values())
            ret.put(queryDef.getName(), queryDef);

        return ret.get(name);
    }

    private Map<String, CustomView> getCustomViewMap(User user, Container container, String schema, String query) throws SQLException
    {
        Map<Map.Entry<String, String>, QueryDefinition> queryDefs = getAllQueryDefs(user, container, schema, false, true);
        QueryDefinition qd = queryDefs.get(new Pair<String, String>(schema, query));
        if (qd == null)
            qd = QueryService.get().getUserSchema(user, container, schema).getQueryDefForTable(query);

        return getCustomViewMap(user, container, qd, false);
    }

    protected Map<String, CustomView> getCustomViewMap(User user, Container container, QueryDefinition qd, boolean inheritable) throws SQLException
    {
        Map<String, CustomView> views = new HashMap<String, CustomView>();

        // custom views in the database get highest precedence
        for (CstmView cstmView : QueryManager.get().getAllCstmViews(container, qd.getSchema().getName(), qd.getName(), user, inheritable))
            addCustomView(views, cstmView, qd);

        // module query views have lower precedence
        for (ModuleCustomViewDef viewDef : getModuleCustomViewDefs(container, qd.getSchema().getName(), qd.getName(), false))
            addCustomView(views, viewDef, qd);

        return views;
    }

    private void addCustomView(Map<String, CustomView> views, CstmView cstmView, QueryDefinition qd)
    {
        if (qd instanceof QueryDefinitionImpl && !views.containsKey(cstmView.getName()))
            views.put(cstmView.getName(), new CustomViewImpl(qd, cstmView));
    }

    private void addCustomView(Map<String, CustomView> views, ModuleCustomViewDef viewDef, QueryDefinition qd)
    {
        if (qd instanceof QueryDefinitionImpl && !views.containsKey(viewDef.getName()))
            views.put(viewDef.getName(), new ModuleCustomView(qd, viewDef));
    }

    public CustomView getCustomView(User user, Container container, String schema, String query, String name)
    {
        try
        {
            Map<String, CustomView> views = getCustomViewMap(user, container, schema, query);
            return views.get(name);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public List<CustomView> getCustomViews(User user, Container container, String schema, String query)
    {
        try
        {
            Map<String, CustomView> views = getCustomViewMap(user, container, schema, query);
            return new ArrayList<CustomView>(views.values());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public List<CustomViewInfo> getCustomViewInfos(User user, Container container, String schema, String query)
    {
        try
        {
            Map<String, CustomViewInfo> views = new HashMap<String, CustomViewInfo>();
            String key = StringUtils.defaultString(schema, "") + "-" + StringUtils.defaultString(query, "");

            for (ModuleCustomViewDef viewDef : getModuleCustomViewDefs(container, schema, query, false))
                views.put(key + "-" + viewDef.getName(), new ModuleCustomViewInfo(viewDef));

            // custom views in the database get highest precedence
            for (CstmView cstmView : QueryManager.get().getAllCstmViews(container, schema, query, user, true))
                views.put(key + "-" + cstmView.getName(), new CustomViewInfoImpl(cstmView));

            return new ArrayList<CustomViewInfo>(views.values());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /**
     * Get a list of module custom view definitions for the schema/query from all modules or only the active modules in the container.
     * The list is cached in production mode.
     * @param container Used to determine active modules.
     * @param schema The schema name
     * @param query The query name
     * @param allModules Get custom views from all modules when true, or just active modules when false.
     * @return
     */
    private List<ModuleCustomViewDef> getModuleCustomViewDefs(Container container, String schema, String query, boolean allModules)
    {
        // XXX: null schema and query should search for all schema and query custom views to match .getAllCstmViews() behavior
        if (schema == null || query == null)
            return Collections.emptyList();

        List<ModuleCustomViewDef> customViews = new ArrayList<ModuleCustomViewDef>();

        Path path = new Path(MODULE_QUERIES_DIRECTORY, FileUtil.makeLegalName(schema), FileUtil.makeLegalName(query));
        Collection<Module> modules = allModules ? ModuleLoader.getInstance().getModules() : container.getActiveModules();
        for (Module module : modules)
        {
            Collection<? extends Resource> views;

            //always scan the file system in dev mode
            if (AppProps.getInstance().isDevMode())
            {
                Resource queryDir = module.getModuleResource(path);
                views = getModuleCustomViews(queryDir);
            }
            else
            {
                //in production, cache the set of custom view defs for each module on first request
                String fileSetCacheKey = CUSTOMVIEW_SET_CACHE_ENTRY + module.toString() + "." + schema + "." + query;
                views = (Collection<? extends Resource>)MODULE_RESOURCES_CACHE.get(fileSetCacheKey);

                if (null == views)
                {
                    Resource queryDir = module.getModuleResource(path);
                    views = getModuleCustomViews(queryDir);
                    MODULE_RESOURCES_CACHE.put(fileSetCacheKey, views);
                }
            }

            if (null != views)
            {
                for (Resource view : views)
                {
                    String cacheKey = view.getPath().toString();
                    ModuleCustomViewDef moduleCustomViewDef = (ModuleCustomViewDef)MODULE_RESOURCES_CACHE.get(cacheKey);
                    if (null == moduleCustomViewDef || moduleCustomViewDef.isStale())
                    {
                        try
                        {
                            moduleCustomViewDef = new ModuleCustomViewDef(view, schema, query);
                            MODULE_RESOURCES_CACHE.put(cacheKey, moduleCustomViewDef);
                        }
                        catch (XmlValidationException ex)
                        {
                            // XXX: log or throw?
                        }
                    }

                    if (moduleCustomViewDef != null)
                        customViews.add(moduleCustomViewDef);
                }
            }
        }

        return customViews;
    }

    /** Find any .qview.xml files under the given queryDir Resource. */
    private Collection<? extends Resource> getModuleCustomViews(Resource queryDir)
    {
        if (queryDir == null)
            return Collections.emptyList();

        List<Resource> ret = new ArrayList<Resource>();
        for (String name : queryDir.listNames())
        {
            if (name.toLowerCase().endsWith(CustomViewXmlReader.XML_FILE_EXTENSION))
            {
                Resource queryViewXml = queryDir.find(name);
                if (queryViewXml != null)
                    ret.add(queryViewXml);
            }
        }
        return ret;
    }

    public int importCustomViews(User user, Container container, File viewDir) throws XmlValidationException
    {
        File[] viewFiles = viewDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name)
            {
                return name.endsWith(CustomViewXmlReader.XML_FILE_EXTENSION);
            }
        });

        QueryManager mgr = QueryManager.get();
        HttpServletRequest request = new MockHttpServletRequest();

        for (File viewFile : viewFiles)
        {
            CustomViewXmlReader reader = CustomViewXmlReader.loadDefinition(viewFile);

            QueryDefinition qd = QueryService.get().createQueryDef(user, container, reader.getSchema(), reader.getQuery());
            String viewName = reader.getName();

            if (null == viewName)
                throw new IllegalStateException(viewFile.getName() + ": Must specify a view name");

            try
            {
                // Get all shared views on this query with the same name
                CstmView[] views = mgr.getCstmViews(container, qd.getSchemaName(), qd.getName(), viewName, null, false);

                // Delete them
                for (CstmView view : views)
                    mgr.delete(null, view);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            // owner == null since we're exporting/importing only shared views
            CustomView cv = qd.createCustomView(null, reader.getName());
            cv.setColumnProperties(reader.getColList());
            cv.setFilterAndSort(reader.getFilterAndSortString());
            cv.setIsHidden(reader.isHidden());
            cv.save(user, request);
        }

        return viewFiles.length;
    }

    
    public void updateCustomViewsAfterRename(@NotNull Container c, @NotNull String schema,
            @NotNull String oldQueryName, @NotNull String newQueryName)
    {
        QueryManager.get().updateViewsAfterRename(c,schema,oldQueryName,newQueryName);
    }


    private Map<String, QuerySnapshotDefinition> getAllQuerySnapshotDefs(Container container, String schemaName)
    {
        Map<String, QuerySnapshotDefinition> ret = new LinkedHashMap<String, QuerySnapshotDefinition>();

        for (QuerySnapshotDef queryDef : QueryManager.get().getQuerySnapshots(container, schemaName))
            ret.put(queryDef.getName(), new QuerySnapshotDefImpl(queryDef));

        return ret;
    }

    public QuerySnapshotDefinition getSnapshotDef(Container container, String schema, String name)
    {
        return getAllQuerySnapshotDefs(container, schema).get(name);
    }

    public boolean isQuerySnapshot(Container container, String schema, String name)
    {
        return QueryService.get().getSnapshotDef(container, schema, name) != null;
    }

    public List<QuerySnapshotDefinition> getQuerySnapshotDefs(Container container, String schema)
    {
        List<QuerySnapshotDefinition> ret = new ArrayList<QuerySnapshotDefinition>();

        for (QuerySnapshotDef queryDef : QueryManager.get().getQuerySnapshots(container, schema))
            ret.add(new QuerySnapshotDefImpl(queryDef));

        return ret;
    }

    private class ContainerSchemaKey implements Serializable
    {
        private Container _container;
        private String _schema;

        public ContainerSchemaKey(Container container, String schema)
        {
            _container = container;
            _schema = schema;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ContainerSchemaKey that = (ContainerSchemaKey) o;

            if (!_container.equals(that._container)) return false;
            if (!_schema.equals(that._schema)) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _container.hashCode();
            result = 31 * result + _schema.hashCode();
            return result;
        }
    }

    @Override
    public QueryDefinition saveSessionQuery(ViewContext context, Container container, String schemaName, String sql)
    {
        Map<String, String> queries = getSessionQueryMap(context.getRequest(), container, schemaName, true);
        String queryName = null;
        for (Map.Entry<String, String> query : queries.entrySet())
        {
            if (query.getValue().equals(sql))
            {
                queryName = query.getKey();
                break;
            }
        }
        if (queryName == null)
        {
            queryName = GUID.makeGUID();
            queries.put(queryName, sql);
        }
        return getSessionQuery(context, container, schemaName, queryName);
    }

    private static final String PERSISTED_TEMP_QUERIES_KEY = "LABKEY.PERSISTED_TEMP_QUERIES";
    private Map<String, String> getSessionQueryMap(HttpServletRequest request, Container container, String schemaName, boolean create)
    {
        HttpSession session = request.getSession(create);
        if (session == null)
            return Collections.emptyMap();
        Map<ContainerSchemaKey, Map<String, String>> containerQueries = (Map<ContainerSchemaKey, Map<String, String>>) session.getAttribute(PERSISTED_TEMP_QUERIES_KEY);
        if (containerQueries == null)
        {
            containerQueries = new HashMap<ContainerSchemaKey, Map<String, String>>();
            session.setAttribute(PERSISTED_TEMP_QUERIES_KEY, containerQueries);
        }
        ContainerSchemaKey key = new ContainerSchemaKey(container, schemaName);
        Map<String, String> queries = containerQueries.get(key);
        if (queries == null)
        {
            queries = new HashMap<String, String>();
            containerQueries.put(key, queries);
        }
        return queries;
    }

    private List<QueryDefinition> getAllSessionQueries(HttpServletRequest request, User user, Container container, String schemaName)
    {
        Map<String, String> sessionQueries = getSessionQueryMap(request, container, schemaName, false);
        List<QueryDefinition> ret = new ArrayList<QueryDefinition>();
        for (Map.Entry<String, String> entry : sessionQueries.entrySet())
            ret.add(createTempQueryDefinition(user, container, schemaName, entry.getKey(), entry.getValue()));
        return ret;
    }

    public QueryDefinition getSessionQuery(ViewContext context, Container container, String schemaName, String queryName)
    {
        String sql = getSessionQueryMap(context.getRequest(), container, schemaName, false).get(queryName);
        return createTempQueryDefinition(context.getUser(), container, schemaName, queryName, sql);
    }

    private QueryDefinition createTempQueryDefinition(User user, Container container, String schemaName, String queryName, String sql)
    {
        QueryDefinition qdef = QueryService.get().createQueryDef(user, container, schemaName, queryName);
        qdef.setSql(sql);
        qdef.setIsTemporary(true);
        qdef.setIsHidden(true);
        return qdef;
    }

    public QuerySnapshotDefinition createQuerySnapshotDef(QueryDefinition queryDef, String name)
    {
        // if this is a table based query view, we just need to save the table name, else create a copy of the query
        // definition for the snapshot to refer back to on updates.
        if (queryDef.isTableQueryDefinition())
        {
            return new QuerySnapshotDefImpl(queryDef.getContainer().getId(), queryDef.getSchemaName(), queryDef.getName(), name);
        }
        else
        {
            QueryDefinitionImpl qd = new CustomQueryDefinitionImpl(queryDef.getUser(), queryDef.getContainer(), queryDef.getSchemaName(), queryDef.getName() + "_" + name);

            qd.setMetadataXml(queryDef.getMetadataXml());
            qd.setSql(queryDef.getSql());
            qd.setDescription(queryDef.getDescription());
            qd.setIsHidden(true);
            qd.setIsSnapshot(true);

            return new QuerySnapshotDefImpl(qd.getQueryDef(), name);
        }
    }

    private ColumnInfo getColumn(AliasManager manager, TableInfo table, Map<FieldKey, ColumnInfo> columnMap, FieldKey key)
    {
        if (key != null && key.getTable() == null)
        {
            String name = key.getName();
            ColumnInfo ret = table.getColumn(name);

            if (ret != null && key.getName().equals(table.getTitleColumn()) && ret.getEffectiveURL() == null)
            {
                List<ColumnInfo> pkColumns = table.getPkColumns();
                Set<FieldKey> pkColumnMap = new HashSet<FieldKey>();

                for (ColumnInfo column : pkColumns)
                    pkColumnMap.add(column.getFieldKey());

                StringExpression url = table.getDetailsURL(pkColumnMap, null);

                if (url != null)
                    ret.setURL(url);
            }

            if (ret != null && !AliasManager.isLegalName(ret.getName()))
                ret = new QAliasedColumn(ret.getName(), manager.decideAlias(key.toString()), ret);

            return ret;
        }

        if (columnMap.containsKey(key))
            return columnMap.get(key);

        ColumnInfo parent = getColumn(manager, table, columnMap, key.getParent());

        if (parent == null)
            return null;

        ColumnInfo lookup = table.getLookupColumn(parent, StringUtils.trimToNull(key.getName()));

        if (lookup == null)
            return null;

        AliasedColumn ret = new QAliasedColumn(key, manager.decideAlias(key.toString()), lookup, true);
        columnMap.put(key, ret);

        return ret;
    }

    @NotNull
    public Map<FieldKey, ColumnInfo> getColumns(TableInfo table, Collection<FieldKey> fields)
    {
        return getColumns(table, fields, Collections.<ColumnInfo>emptySet());
    }


    @NotNull
    public LinkedHashMap<FieldKey, ColumnInfo> getColumns(TableInfo table, Collection<FieldKey> fields, Collection<ColumnInfo> existingColumns)
    {
        AliasManager manager = new AliasManager(table, existingColumns);
        LinkedHashMap<FieldKey, ColumnInfo> ret = new LinkedHashMap<FieldKey,ColumnInfo>();
        Map<FieldKey, ColumnInfo> columnMap = new HashMap<FieldKey,ColumnInfo>();

        for (ColumnInfo existingColumn : existingColumns)
        {
            columnMap.put(existingColumn.getFieldKey(), existingColumn);
            ret.put(existingColumn.getFieldKey(), existingColumn);
        }

        for (FieldKey field : fields)
        {
            if (!ret.containsKey(field))
            {
                ColumnInfo column = getColumn(manager, table, columnMap, field);
                if (column != null)
                    ret.put(field, column);
            }
        }

        return ret;
    }


    public List<DisplayColumn> getDisplayColumns(TableInfo table, Collection<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> fields)
    {
        List<DisplayColumn> ret = new ArrayList<DisplayColumn>();
        Set<FieldKey> fieldKeys = new HashSet<FieldKey>();

        for (Map.Entry<FieldKey, ?> entry : fields)
            fieldKeys.add(entry.getKey());

        Map<FieldKey, ColumnInfo> columns = getColumns(table, fieldKeys);

        for (Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>> entry : fields)
        {
            ColumnInfo column = columns.get(entry.getKey());

            if (column == null)
                continue;

            DisplayColumn displayColumn = column.getRenderer();
            String caption = entry.getValue().get(CustomViewInfo.ColumnProperty.columnTitle);

            if (caption != null)
                displayColumn.setCaption(caption);

            ret.add(displayColumn);
        }

        return ret;
    }


    public Collection<ColumnInfo> ensureRequiredColumns(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, Set<String> unresolvedColumns)
    {
        AliasManager manager = new AliasManager(table, columns);
        Set<FieldKey> selectedColumns = new HashSet<FieldKey>();
        Map<FieldKey, ColumnInfo> columnMap = new HashMap<FieldKey,ColumnInfo>();

        for (ColumnInfo column : columns)
        {
            FieldKey key = FieldKey.fromString(column.getName());
            selectedColumns.add(key);
            columnMap.put(key, column);
        }

        Set<String> names = new HashSet<String>();

        if (filter != null)
            names.addAll(filter.getWhereParamNames());

        if (sort != null)
            for (Sort.SortField field : sort.getSortList())
                names.add(field.getColumnName());

        ArrayList<ColumnInfo> ret = null;
        for (String name : names)
        {
            if (StringUtils.isEmpty(name))
                continue;

            FieldKey field = FieldKey.fromString(name);

            if (selectedColumns.contains(field))
                continue;

            ColumnInfo column = getColumn(manager, table, columnMap, field);

            if (column != null)
            {
                if (null == ret)
                    ret = new ArrayList<ColumnInfo>(columns);
                assert field.getTable() == null || columnMap.containsKey(field);
                ret.add(column);
            }
            else
            {
                if (unresolvedColumns != null)
                    unresolvedColumns.add(name);
            }
        }

        if (unresolvedColumns != null)
        {
            for (String columnName : unresolvedColumns)
            {
                if (filter instanceof SimpleFilter)
                {
                    SimpleFilter simpleFilter = (SimpleFilter) filter;
                    simpleFilter.deleteConditions(columnName);
                }

                if (sort != null)
                    sort.deleteSortColumn(columnName);
            }
        }
        assert null == ret || ret.size() > 0;
        return null == ret ? columns : ret;
    }


    public Map<String, UserSchema> getExternalSchemas(DefaultSchema folderSchema)
    {
        Map<String, UserSchema> ret = new HashMap<String, UserSchema>();
        ExternalSchemaDef[] defs = QueryManager.get().getExternalSchemaDefs(folderSchema.getContainer());

        for (ExternalSchemaDef def : defs)
        {
            try
            {
                UserSchema schema = new ExternalSchema(folderSchema.getUser(), folderSchema.getContainer(), def);
                ret.put(def.getUserSchemaName(), schema);
            }
            catch (Exception e)
            {
                Logger.getLogger(QueryServiceImpl.class).warn("Could not load schema " + def.getDbSchemaName() + " from " + def.getDataSource(), e);
            }
        }

        return ret;
    }

    @Override
    public UserSchema getExternalSchema(DefaultSchema folderSchema, String name)
    {
        ExternalSchemaDef[] defs = QueryManager.get().getExternalSchemaDefs(folderSchema.getContainer());

        for (ExternalSchemaDef def : defs)
        {
            if (name.equals(def.getUserSchemaName()))
            {
                try
                {
                    return new ExternalSchema(folderSchema.getUser(), folderSchema.getContainer(), def);
                }
                catch (Exception e)
                {
                    Logger.getLogger(QueryServiceImpl.class).warn("Could not load schema " + def.getDbSchemaName() + " from " + def.getDataSource(), e);
                    break;
                }
            }
        }

        return null;
    }

    @Override
    public UserSchema createSimpleUserSchema(String name, String description, User user, Container container, DbSchema schema)
    {
        return new SimpleUserSchema(name, description, user, container, schema);
    }

    public List<FieldKey> getDefaultVisibleColumns(List<ColumnInfo> columns)
    {
        List<FieldKey> ret = new ArrayList<FieldKey>();

        for (ColumnInfo column : columns)
        {
            if (column.isHidden())
                continue;

            if (column.isUnselectable())
                continue;

            ret.add(FieldKey.fromParts(column.getName()));
        }

        return ret;
    }

    public TableType findMetadataOverride(UserSchema schema, String tableName, boolean customQuery, Collection<QueryException> errors)
    {
        return findMetadataOverride(schema, tableName, customQuery, errors, null);
    }

    public TableType findMetadataOverride(UserSchema schema, String tableName, boolean customQuery, Collection<QueryException> errors, Path dir)
    {
        QueryDef queryDef = findMetadataOverrideImpl(schema, tableName, customQuery, dir);
        if (queryDef == null)
            return null;

        return parseMetadata(queryDef.getMetaData(), errors);
    }

    protected TableType parseMetadata(String metadataXML, Collection<QueryException> errors)
    {
        if (metadataXML == null || StringUtils.isBlank(metadataXML))
            return null;

        XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
        List<XmlError> xmlErrors = new ArrayList<XmlError>();
        options.setErrorListener(xmlErrors);
        try
        {
            TablesDocument doc = TablesDocument.Factory.parse(metadataXML, options);
            TablesDocument.Tables tables = doc.getTables();
            if (tables != null && tables.sizeOfTableArray() > 0)
                return tables.getTableArray(0);
        }
        catch (XmlException e)
        {
            errors.add(new MetadataException(XmlBeansUtil.getErrorMessage(e)));
        }
        for (XmlError xmle : xmlErrors)
        {
            errors.add(new MetadataException(XmlBeansUtil.getErrorMessage(xmle)));
        }

        return null;
    }


    /**
     * Looks in the current folder, parent folders up to and including the project, and the shared
     * container
     */
    public QueryDef findMetadataOverrideImpl(UserSchema schema, String tableName, boolean customQuery, Path dir)
    {
        if (dir == null)
            dir = new Path(QueryService.MODULE_QUERIES_DIRECTORY, FileUtil.makeLegalName(schema.getName()));

        String schemaName = schema.getName();
        Container container = schema.getContainer();
        QueryDef queryDef;
        do
        {
            // Look up the folder hierarchy to try to find an override
            queryDef = QueryManager.get().getQueryDef(container, schemaName, tableName, customQuery);
            if (queryDef != null && (customQuery || queryDef.getMetaData() != null))
            {
                return queryDef;
            }
            container = container.getParent();
        }
        while (null != container && !container.isRoot());

        // Try the shared container too
        queryDef = QueryManager.get().getQueryDef(ContainerManager.getSharedContainer(), schemaName, tableName, customQuery);
        if (queryDef != null && queryDef.getMetaData() != null)
        {
            return queryDef;
        }

        // Finally, look for file-based definitions in modules
        for (Module module : schema.getContainer().getActiveModules())
        {
            Collection<? extends Resource> queryMetadatas;

            //always scan the file system in dev mode
            if (AppProps.getInstance().isDevMode())
            {
                Resource schemaDir = module.getModuleResolver().lookup(dir);
                queryMetadatas = getModuleQueries(schemaDir, ModuleQueryDef.META_FILE_EXTENSION);
            }
            else
            {
                //in production, cache the set of query defs for each module on first request
                String fileSetCacheKey = QUERYDEF_METADATA_SET_CACHE_ENTRY + dir.toString();
                //noinspection unchecked
                queryMetadatas = (Collection<? extends Resource>) MODULE_RESOURCES_CACHE.get(fileSetCacheKey);

                if (null == queryMetadatas)
                {
                    Resource schemaDir = module.getModuleResolver().lookup(dir);
                    queryMetadatas = getModuleQueries(schemaDir, ModuleQueryDef.META_FILE_EXTENSION);
                    MODULE_RESOURCES_CACHE.put(fileSetCacheKey, queryMetadatas);
                }
            }

            if (null != queryMetadatas)
            {
                for (Resource query : queryMetadatas)
                {
                    String cacheKey = query.getPath().toString();
                    ModuleQueryMetadataDef metadataDef = (ModuleQueryMetadataDef) MODULE_RESOURCES_CACHE.get(cacheKey);
                    if (null == metadataDef || metadataDef.isStale())
                    {
                        metadataDef = new ModuleQueryMetadataDef(query);
                        MODULE_RESOURCES_CACHE.put(cacheKey, metadataDef);
                    }

                    if (metadataDef.getName().equalsIgnoreCase(tableName))
                    {
                        QueryDef result = metadataDef.toQueryDef(container);
                        result.setSchema(schemaName);
                        return result;
                    }
                }
            }
        }

        return null;
    }


    public ResultSet select(QuerySchema schema, String sql) throws SQLException
	{
		Query q = new Query(schema);
		q.parse(sql);

		if (q.getParseErrors().size() > 0)
			throw q.getParseErrors().get(0);

		TableInfo table = q.getTableInfo();

		if (q.getParseErrors().size() > 0)
			throw q.getParseErrors().get(0);

        SQLFragment sqlf = getSelectSQL(table, null, null, null, 0, 0);

		return Table.executeQuery(table.getSchema(), sqlf);
	}


    public void bindNamedParameters(SQLFragment frag, Map<String,Object> in)
    {
        Map<String,Object> params = null==in ? Collections.EMPTY_MAP :
                in instanceof CaseInsensitiveHashMap ? in :
                new CaseInsensitiveHashMap<Object>(in);

        List<Object> list = frag.getParams();
        for (int i=0 ; i<list.size() ; i++)
        {
            Object o = list.get(i);
            if (!(o instanceof ParameterDecl))
                continue;

            ParameterDecl p = (ParameterDecl)o;
            String name = p.getName();
            Object value = p.getDefault();
            boolean required = p.isRequired();
            boolean provided = null != value;

            if (params.containsKey(name))
            {
                value = params.get(p.getName());
                provided = true;
            }

            if (required && !provided)
            {
                continue; // maybe someone else will bind it....
            }

            Object converted = p.getType().convert(value);
            list.set(i, converted);
        }
    }


    // verify that named parameters have been bound
    public void validateNamedParameters(SQLFragment frag) throws SQLException
    {
        for (Object o : frag.getParams())
        {
            if (!(o instanceof ParameterDecl))
                continue;
            ParameterDecl p = (ParameterDecl)o;
            throw new NamedParameterNotProvided(p.getName());
        }
    }


    public Results select(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, Map<String,Object> parameters) throws SQLException
    {
        SQLFragment sql = getSelectSQL(table, columns, filter, sort, 0, 0);
        bindNamedParameters(sql, parameters);
        validateNamedParameters(sql);
		ResultSet rs = Table.executeQuery(table.getSchema(), sql);
        return new ResultsImpl(rs, columns);
    }


	public SQLFragment getSelectSQL(TableInfo table, Collection<ColumnInfo> selectColumns, Filter filter, Sort sort,
        int rowCount, long offset)
	{
        if (null == selectColumns)
            selectColumns = table.getColumns();

        SqlDialect dialect = table.getSqlDialect();
        Map<String, SQLFragment> joins = new LinkedHashMap<String, SQLFragment>();
        ArrayList<ColumnInfo> allColumns = new ArrayList<ColumnInfo>(selectColumns);
        allColumns = (ArrayList<ColumnInfo>)ensureRequiredColumns(table, allColumns, filter, sort, null);
        Map<String, ColumnInfo> columnMap = Table.createColumnMap(table, allColumns);
        boolean requiresExtraColumns = allColumns.size() > selectColumns.size();
        SQLFragment outerSelect = new SQLFragment("SELECT *");
        SQLFragment selectFrag = new SQLFragment("SELECT");
        String strComma = "\n";
        String tableName = table.getName();
        if (tableName == null)
        {
            // This shouldn't happen, but if it's null we'll blow up later without enough context to give a good error
            // message
            throw new NullPointerException("Null table name from " + table);
        }
        String tableAlias = AliasManager.makeLegalName(tableName, table.getSchema().getSqlDialect());

        if (allColumns.isEmpty())
        {
            selectFrag.append(" * ");
        }
        else
        {
            for (ColumnInfo column : allColumns)
            {
                assert column.getParentTable() == table : "Column " + column + " is from the wrong table: " + column.getParentTable() + " instead of " + table;
                column.declareJoins(tableAlias, joins);
                selectFrag.append(strComma);
                selectFrag.append(column.getValueSql(tableAlias));
                selectFrag.append(" AS " );
                selectFrag.append(dialect.makeLegalIdentifier(column.getAlias()));
                strComma = ",\n";
            }
        }

        if (requiresExtraColumns)
        {
            outerSelect = new SQLFragment("SELECT ");
            strComma = "";

            for (ColumnInfo column : selectColumns)
            {
                outerSelect.append(strComma);
                outerSelect.append(dialect.getColumnSelectName(column.getAlias()));
                strComma = ", ";
            }
        }

		SQLFragment fromFrag = new SQLFragment("FROM ");
        fromFrag.append(table.getFromSQL(tableAlias));
        fromFrag.append(" ");

		for (Map.Entry<String, SQLFragment> entry : joins.entrySet())
		{
			fromFrag.append("\n").append(entry.getValue());
		}

		SQLFragment filterFrag = null;

		if (filter != null)
		{
			filterFrag = filter.getSQLFragment(dialect, columnMap);
		}

		if ((sort == null || sort.getSortList().size() == 0) && (rowCount > 0 || offset > 0 || Table.NO_ROWS == rowCount))
		{
			sort = createDefaultSort(selectColumns);
		}

        String orderBy = null;

		if (sort != null)
		{
			orderBy = sort.getOrderByClause(dialect, columnMap);
		}

		if ((filterFrag == null || filterFrag.getSQL().length()==0) && sort == null && Table.ALL_ROWS == rowCount && offset == 0)
		{
			selectFrag.append("\n").append(fromFrag);
			return selectFrag;
		}

		SQLFragment nestedFrom = new SQLFragment();
		nestedFrom.append("FROM (\n").append(selectFrag).append("\n").append(fromFrag).append(") x\n");
		SQLFragment ret = dialect.limitRows(outerSelect, nestedFrom, filterFrag, orderBy, null, rowCount, offset);

        if (AppProps.getInstance().isDevMode())
        {
            SQLFragment t = new SQLFragment();
            t.appendComment("<QueryServiceImpl.getSelectSQL(" + table + ")>", dialect);
            t.append(ret);
            t.appendComment("</QueryServiceImpl.getSelectSQL()>", dialect);
            String s = _prettyPrint(t.getSQL());
            ret = new SQLFragment(s, ret.getParams());
        }

	    return ret;
    }


	private static Sort createDefaultSort(Collection<ColumnInfo> columns)
	{
		Sort sort = new Sort();
		addSortableColumns(sort, columns, true);

		if (sort.getSortList().size() == 0)
		{
			addSortableColumns(sort, columns, false);
		}

		return sort;
	}

	private static void addSortableColumns(Sort sort, Collection<ColumnInfo> columns, boolean usePrimaryKey)
	{
		for (ColumnInfo column : columns)
		{
			if (usePrimaryKey && !column.isKeyField())
				continue;
			ColumnInfo sortField = column.getSortField();
			if (sortField != null)
			{
				sort.getSortList().add(sort.new SortField(sortField.getName(), column.getSortDirection()));
				return;
			}
		}
	}

    public void addQueryListener(QueryListener listener)
    {
        QueryManager.get().addQueryListener(listener);
    }

    private String _prettyPrint(String s)
    {
        StringBuilder sb = new StringBuilder(s.length() + 200);
        String[] lines = StringUtils.split(s, '\n');
        int indent = 0;

        for (String line : lines)
        {
            String t = line.trim();

            if (t.length() == 0)
                continue;

            if (t.startsWith("-- </"))
                indent = Math.max(0,indent-1);

            for (int i=0 ; i<indent ; i++)
                sb.append('\t');

            sb.append(line);
            sb.append('\n');

            if (t.startsWith("-- <") && !t.startsWith("-- </"))
                indent++;
        }

        return sb.toString();
    }


    static private class QAliasedColumn extends AliasedColumn
    {
        public QAliasedColumn(FieldKey key, String alias, ColumnInfo column, boolean forceKeepLabel)
        {
            super(column.getParentTable(), key, column, forceKeepLabel);
            setAlias(alias);
        }

        public QAliasedColumn(String name, String alias, ColumnInfo column)
        {
            super(column.getParentTable(), new FieldKey(null, name), column, true);
            setAlias(alias);
        }
    }


    static ThreadLocal<HashMap<Environment,Object>> environments = new ThreadLocal<HashMap<Environment, Object>>()
    {
        @Override
        protected HashMap<Environment, Object> initialValue()
        {
            return new HashMap<Environment, Object>();
        }
    };


    @Override
    public void setEnvironment(QueryService.Environment e, Object value)
    {
        HashMap<Environment,Object> env = environments.get();
        env.put(e,e.type.convert(value));
    }

    public Object getEnvironment(QueryService.Environment e)
    {
        HashMap<Environment,Object> env = environments.get();
        return env.get(e);
    }

    @Override
    public Object cloneEnvironment()
    {
        HashMap<Environment,Object> env = environments.get();
        return new HashMap<Environment,Object>(env);
    }

    @Override
    public void copyEnvironment(Object o)
    {
        HashMap<Environment,Object> env = environments.get();
        env.clear();
        env.putAll((HashMap<Environment,Object>)o);
    }


    @Override
    public void clearEnvironment()
    {
        environments.get().clear();
    }


    public static class TestCase extends Assert
    {
        ResultSet rs = null;

	    void _close()
	    {
		    rs = ResultSetUtil.close(rs);
	    }

        @Test
        public void testSelect() throws SQLException
        {
            QueryService qs = ServiceRegistry.get().getService(QueryService.class);
            assertNotNull(qs);
            assertEquals(qs, QueryService.get());
            TableInfo issues = DbSchema.get("issues").getTable("issues");
            assertNotNull(issues);

            {
				List<ColumnInfo> l = Arrays.asList(
					issues.getColumn("issueid"),
					issues.getColumn("title"),
					issues.getColumn("status"));
				rs = qs.select(issues, l, null, null);
				assertEquals(rs.getMetaData().getColumnCount(),3);
				_close();
            }


	        {
				List<ColumnInfo> l = Arrays.asList(
					issues.getColumn("issueid"),
					issues.getColumn("title"),
					issues.getColumn("status"));
		        Sort sort = new Sort("+milestone");
				rs = qs.select(issues, l, null, sort);
				assertEquals(rs.getMetaData().getColumnCount(),3);
		        _close();
	        }

	        {
				List<ColumnInfo> l = Arrays.asList(
					issues.getColumn("issueid"),
					issues.getColumn("title"),
					issues.getColumn("status"));
				Filter f = new SimpleFilter("assignedto",1001);
				rs = qs.select(issues, l, f, null);
				assertEquals(rs.getMetaData().getColumnCount(),3);
		        _close();
	        }

	        {
		        Map<FieldKey,ColumnInfo> map = qs.getColumns(issues, Arrays.asList(
				        new FieldKey(null, "issueid"),
				        new FieldKey(null, "title"),
				        new FieldKey(null, "status"),
				        new FieldKey(new FieldKey(null, "createdby"), "email")));
		        Sort sort = new Sort("+milestone");
				Filter f = new SimpleFilter("assignedto",1001);
				rs = qs.select(issues, map.values(), f, sort);
				assertEquals(rs.getMetaData().getColumnCount(),4);
		        _close();
	        }
        }

	    @After
	    public void tearDown() throws Exception
	    {
		    _close();
	    }
    }


    // SQLFragment version for convenience
    private static void appendSelectAutoIncrement(SqlDialect d, SQLFragment sqlf, TableInfo tinfo, String columnName)
    {
        // TODO why does appendSelectAutoIncrement prepend a semi-colon?
        String t = d.appendSelectAutoIncrement("", tinfo, columnName);
        t = StringUtils.strip(t, ";\n\r");
        sqlf.append(t);
    }

    private static String p(SqlDialect d, boolean useVariable, int i)
    {
        return !useVariable ? "?" : d.isSqlServer() ? "@p" + i : "_$p" + i;
    }


    /**
     * Create a reusable SQL Statement for inserting rows into an labkey relationship.  The relationship
     * persisted directly in the database (SchemaTableInfo), or via the OnotologyManager tables.
     *
     * QueryService shouldn't really know about the internals of exp.Object and exp.ObjectProperty etc.
     * However, I can only keep so many levels of abstraction in my head at once.
     *
     * NOTE: this is currently fairly expensive for updating one row into an Ontology stored relationship on Postgres.
     * This shouldn't be a big problem since we don't usually need to optimize the one row case, and we're moving
     * to provisioned tables for major datatypes.
     *
     * NOTE: does not currently provide for manually setting built-in columns.  Wouldn't be hard to add.
     */
    public Parameter.ParameterMap insertStatement(Connection conn, User user, TableInfo tableInsert) throws SQLException
    {
        // TODO Does not provide for overriding the built-in fields (CreatedBy, ModifiedBy etc)
        if (!(tableInsert instanceof UpdateableTableInfo))
            throw new IllegalArgumentException();

        UpdateableTableInfo updatable = (UpdateableTableInfo)tableInsert;
        TableInfo table = updatable.getSchemaTableInfo();

        if (!(table instanceof SchemaTableInfo))
            throw new IllegalArgumentException();
        if (null == ((SchemaTableInfo)table).getMetaDataName())
            throw new IllegalArgumentException();

        SqlDialect d = tableInsert.getSqlDialect();
        boolean useVariables = false;

        ArrayList<Parameter> parameters = new ArrayList<Parameter>();
        Timestamp ts = new Timestamp(System.currentTimeMillis());

        String comma = "";
        Set done = Sets.newCaseInsensitiveHashSet();

        String objectIdVar = d.isPostgreSQL() ? "_$objectid$_" : "@_objectid_";
        String setKeyword = d.isPostgreSQL() ? "" : "SET ";

        //
        // exp.Objects INSERT
        //

        SQLFragment sqlfDeclare = new SQLFragment();
        SQLFragment sqlfObject = new SQLFragment();
        SQLFragment sqlfObjectProperty = new SQLFragment();

        Domain domain = tableInsert.getDomain();
        DomainKind domainKind = tableInsert.getDomainKind();
        DomainProperty[] properties = null;
        if (null != domain && null != domainKind && StringUtils.isEmpty(domainKind.getStorageSchemaName()))
        {
            properties = domain.getProperties();
            if (properties.length == 0)
                properties = null;
            if (null != properties)
            {
                useVariables = d.isPostgreSQL();
                sqlfDeclare.append("DECLARE " + objectIdVar + " INT;\n");
                Parameter c = new Parameter("container", parameters.size()+1, JdbcType.VARCHAR);
                parameters.add(c);
                String parameterName = updatable.getObjectUriType() == UpdateableTableInfo.ObjectUriType.schemaColumn
                        ? updatable.getObjectURIColumnName()
                        : "objecturi";
                Parameter u = new Parameter(parameterName, parameters.size()+1, JdbcType.VARCHAR);
                parameters.add(u);
                sqlfObject.append("INSERT INTO exp.Object (container, objecturi) ");
                sqlfObject.append("VALUES(" + p(d,useVariables,c.getIndex()) + "," + p(d,useVariables,u.getIndex()) + ");\n");
                sqlfObject.append(setKeyword + objectIdVar + " = (");
                appendSelectAutoIncrement(d, sqlfObject,DbSchema.get("exp").getTable("object"),"objectid");
                sqlfObject.append(");\n");
            }
        }

        //
        // BASE TABLE INSERT()
        //

        SQLFragment cols = new SQLFragment();
        SQLFragment values = new SQLFragment();

        ColumnInfo col = table.getColumn("Owner");
        if (null != col && null != user)
        {
            cols.append(comma).append("Owner");
            values.append(comma).append(user.getUserId());
            done.add("Owner");
            comma = ",";
        }
        col = table.getColumn("CreatedBy");
        if (null != col && null != user)
        {
            cols.append(comma).append("CreatedBy");
            values.append(comma).append(user.getUserId());
            done.add("CreatedBy");
            comma = ",";
        }
        col = table.getColumn("Created");
        if (null != col)
        {
            cols.append(comma).append("Created");
            values.append(comma).append("CAST('" + ts + "' AS " + d.getDefaultDateTimeDataType() + ")");
            done.add("Created");
            comma = ",";
        }
        ColumnInfo colModifiedBy = table.getColumn("Modified");
        if (null != colModifiedBy && null != user)
        {
            cols.append(comma).append("ModifiedBy");
            values.append(comma).append(user.getUserId());
            done.add("ModifiedBy");
            comma = ",";
        }
        ColumnInfo colModified = table.getColumn("Modified");
        if (null != colModified)
        {
            cols.append(comma).append("Modified");
            values.append(comma).append("CAST('" + ts + "' AS " + d.getDefaultDateTimeDataType() + ")");
            done.add("Modified");
            comma = ",";
        }
        ColumnInfo colVersion = table.getVersionColumn();
        if (null != colVersion && !done.contains(colVersion.getName()) && colVersion.getJdbcType() == JdbcType.TIMESTAMP)
        {
            cols.append(comma).append(colVersion.getSelectName());
            values.append(comma).append("CAST('" + ts + "' AS " + d.getDefaultDateTimeDataType() + ")");
            done.add(colVersion.getName());
            comma = ",";
        }

        String objectIdColumnName = StringUtils.trimToNull(updatable.getObjectIdColumnName());

        for (ColumnInfo column : table.getColumns())
        {
            if (column.isAutoIncrement())
                continue;
            if (done.contains(column.getName()))
                continue;
            done.add(column.getName());

            cols.append(comma).append(column.getSelectName());
            if (column.getName().equalsIgnoreCase(objectIdColumnName))
            {
                values.append(comma).append(objectIdVar);
            }
            else
            {
                Parameter p = new Parameter(column, parameters.size()+1);
                parameters.add(p);
                values.append(comma).append(p(d,useVariables,p.getIndex()));
            }
            comma = ", ";
        }

        SQLFragment sqlfInsertInto = new SQLFragment();
        sqlfInsertInto.append("INSERT INTO " + table + " (");
        sqlfInsertInto.append(cols).append(")\nVALUES (").append(values).append(");\n");

        //
        // ObjectProperty
        //

        if (null != properties)
        {
            for (DomainProperty dp : domain.getProperties())
            {
                // ignore property that 'wraps' a hard column
                if (done.contains(dp.getName()))
                    continue;
                // CONSIDER: IF (p IS NOT NULL) THEN ...
                sqlfObjectProperty.append("INSERT INTO exp.ObjectProperty (objectid, propertyid, typetag, mvindicator, ");
                PropertyType propertyType = dp.getPropertyDescriptor().getPropertyType();
                switch (propertyType.getStorageType())
                {
                    case 's':
                        sqlfObjectProperty.append("stringValue");
                        break;
                    case 'd':
                        sqlfObjectProperty.append("dateTimeValue");
                        break;
                    case 'f':
                        sqlfObjectProperty.append("doubleValue");
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown property type: " + propertyType);
                }
                sqlfObjectProperty.append(") VALUES (");
                sqlfObjectProperty.append(objectIdVar);
                sqlfObjectProperty.append(",").append(dp.getPropertyId());
                sqlfObjectProperty.append(",'").append(propertyType.getStorageType()).append("'");
                Parameter mv = new Parameter(dp.getName()+ MvColumn.MV_INDICATOR_SUFFIX, dp.getPropertyURI() + MvColumn.MV_INDICATOR_SUFFIX, parameters.size()+1, JdbcType.VARCHAR);
                parameters.add(mv);
                sqlfObjectProperty.append(",").append(p(d,useVariables,mv.getIndex()));
                Parameter v = new Parameter(dp.getName(), dp.getPropertyURI(), parameters.size()+1, propertyType.getJdbcType());
                parameters.add(v);
                sqlfObjectProperty.append(",").append(p(d,useVariables,v.getIndex()));
                sqlfObjectProperty.append(");\n");
            }
        }

        //
        // PREPARE
        //

        Parameter.ParameterMap ret;

        if (!useVariables)
        {
            SQLFragment script = new SQLFragment();
            script.append(sqlfDeclare);
            script.append(sqlfObject);
            script.append(sqlfInsertInto);
            script.append(sqlfObjectProperty);
            PreparedStatement stmt = conn.prepareStatement(script.getSQL());
            ret = new Parameter.ParameterMap(stmt, parameters, updatable.remapSchemaColumns());
        }
        else
        {
            // wrap in a function
            SQLFragment fn = new SQLFragment();
            String fnName = d.getGlobalTempTablePrefix() + "fn_" + GUID.makeHash();
            fn.append("CREATE FUNCTION " + fnName + "(");
            SQLFragment call = new SQLFragment("SELECT " + fnName + "(");
            final SQLFragment drop = new SQLFragment("DROP FUNCTION " + fnName + "(");
            comma = "";
            for (Parameter p : parameters)
            {
                String type = d.sqlTypeNameFromSqlType(p.getType().sqlType);
                fn.append("\n").append(comma);
                fn.append(p(d,useVariables,p.getIndex()));
                fn.append(" ");
                fn.append(type);
                fn.append(" -- " + p.getName());
                drop.append(comma).append(type);
                call.append(comma).append("?");
                comma = ",";
            }
            fn.append("\n) RETURNS void AS $$\n");
            drop.append(");");
            call.append(");");
            fn.append(sqlfDeclare);
            fn.append("BEGIN\n");
            fn.append(sqlfObject);
            fn.append(sqlfInsertInto);
            fn.append(sqlfObjectProperty);
            fn.append("\nEND;\n$$ LANGUAGE plpgsql;\n");

            Table.execute(table.getSchema(), fn);
            PreparedStatement stmt = conn.prepareStatement(call.getSQL());
            ret = new Parameter.ParameterMap(stmt, parameters, updatable.remapSchemaColumns());
            ret.onClose(new Runnable() { @Override public void run()
            {
                try
                {
                    Table.execute(ExperimentService.get().getSchema(),drop);
                }
                catch (SQLException x)
                {
                    Category.getInstance(QueryServiceImpl.class).error("Error dropping temp function", x);
                }
            }});
        }

//        if (null != constants)
//        {
//            for (Map.Entry e : constants.entrySet())
//            {
//                Parameter p = ret._map.get(e.getKey());
//                if (null != p)
//                    p.setValue(e.getValue(), true);
//            }
//        }

        return ret;
    }
}
