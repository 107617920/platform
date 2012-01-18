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

package org.labkey.query.persist;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.query.ExternalSchema;
import org.labkey.query.ExternalSchemaDocumentProvider;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;


public class QueryManager
{
    static final private Logger _log = Logger.getLogger(QueryManager.class);
    static private QueryManager instance;
    static final private String SCHEMA_NAME = "query";
    final static public int FLAG_INHERITABLE = 0x01;
    final static public int FLAG_HIDDEN = 0x02;
    final static public int FLAG_SNAPSHOT = 0x04;
    private static final List<QueryService.QueryListener> _listeners = new CopyOnWriteArrayList<QueryService.QueryListener>();

    synchronized static public QueryManager get()
    {
        if (instance == null)
            instance = new QueryManager();
        return instance;
    }

    public QueryDef getQueryDef(int id)
    {
        QueryDef def = Table.selectObject(getTableInfoQueryDef(), id, QueryDef.class);
        _log.debug(def.toString());
        return def;
    }

    /**
     * @param customQuery whether to look for custom queries or modified metadata on built-in tables
     */
    public QueryDef getQueryDef(Container container, String schema, String name, boolean customQuery)
    {
        // Metadata for built-in tables is stored with a NULL value for the SQL
        QueryDef.Key key = new QueryDef.Key(container, customQuery);
        key.setSchema(schema);
        key.setQueryName(name);

        return key.selectObject();
    }

    /**
     * @param customQuery whether to look for custom queries or modified metadata on built-in tables
     */
    public QueryDef[] getQueryDefs(Container container, String schema, boolean inheritableOnly, boolean includeSnapshots, boolean customQuery)
    {
        // Metadata for built-in tables is stored with a NULL value for the SQL
        QueryDef.Key key = new QueryDef.Key(container, customQuery);
        if (schema != null)
        {
            key.setSchema(schema);
        }

        int mask = 0;
        int value = 0;

        if (inheritableOnly)
        {
            mask |= FLAG_INHERITABLE;
            value |= FLAG_INHERITABLE;
        }

        if (!includeSnapshots)
            mask |= FLAG_SNAPSHOT;

        if (mask != 0 || value != 0)
            key.setFlagMask(mask, value);

        return key.select();
    }

    public QuerySnapshotDef[] getQuerySnapshots(Container container, String schema)
    {
        QuerySnapshotDef.Key key = new QuerySnapshotDef.Key(container);
        if (schema != null)
        {
            key.setSchema(schema);
        }
        return key.select();
    }

    public QueryDef insert(User user, QueryDef queryDef) throws SQLException
    {
        return Table.insert(user, getTableInfoQueryDef(), queryDef);
    }

    public QueryDef update(User user, QueryDef queryDef) throws SQLException
    {
        return Table.update(user, getTableInfoQueryDef(), queryDef, queryDef.getQueryDefId());
    }

    public void delete(User user, QueryDef queryDef) throws SQLException
    {
        Table.delete(getTableInfoQueryDef(), queryDef.getQueryDefId());
    }

    public void delete(User user, QuerySnapshotDef querySnapshotDef) throws SQLException
    {
        Table.delete(getTableInfoQuerySnapshotDef(), querySnapshotDef.getRowId());
        if (querySnapshotDef.getQueryDefId() != null)
            Table.delete(getTableInfoQueryDef(), querySnapshotDef.getQueryDefId());
    }

    public QuerySnapshotDef insert(User user, QueryDef queryDef, QuerySnapshotDef snapshotDef) throws SQLException
    {
        if (queryDef != null && snapshotDef.getQueryTableName() == null)
        {
            QueryDef def = insert(user, queryDef);
            snapshotDef.setQueryDefId(def.getQueryDefId());
        }
        return Table.insert(user, getTableInfoQuerySnapshotDef(), snapshotDef);
    }

    public QuerySnapshotDef update(User user, QueryDef queryDef, QuerySnapshotDef snapshotDef) throws SQLException
    {
        if (queryDef != null && snapshotDef.getQueryTableName() == null)
            update(user, queryDef);
        return Table.update(user, getTableInfoQuerySnapshotDef(), snapshotDef, snapshotDef.getRowId());
    }

    public QuerySnapshotDef getQuerySnapshotDef(int id)
    {
        return Table.selectObject(getTableInfoQuerySnapshotDef(), id, QuerySnapshotDef.class);
    }

    public CstmView getCustomView(int id) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(getTableInfoCustomView().getColumn("CustomViewId"), id);
        CstmView view = Table.selectObject(getTableInfoCustomView(), id, CstmView.class);
        _log.debug(view);
        return view;
    }

    public List<CstmView> getAllCstmViews(Container container, String schemaName, String queryName, User user, boolean inheritable) throws SQLException
    {
        List<CstmView> views = new ArrayList<CstmView>();

        views.addAll(Arrays.asList(getCstmViews(container, schemaName, queryName, null, user, false)));
        if (user != null)
            views.addAll(Arrays.asList(getCstmViews(container, schemaName, queryName, null, null, false)));

        if (!inheritable)
            return views;

        Container containerCur = container.getParent();
        while (containerCur != null && !containerCur.isRoot())
        {
            views.addAll(Arrays.asList(getCstmViews(containerCur, schemaName, queryName, null, user, true)));
            if (user != null)
                views.addAll(Arrays.asList(getCstmViews(containerCur, schemaName, queryName, null, null, true)));
            containerCur = containerCur.getParent();
        }

        // look in the shared project
        views.addAll(Arrays.asList(getCstmViews(ContainerManager.getSharedContainer(), schemaName, queryName, null, user, true)));
        if (user != null)
            views.addAll(Arrays.asList(getCstmViews(ContainerManager.getSharedContainer(), schemaName, queryName, null, null, true)));

        return views;
    }

    public CstmView[] getCstmViews(Container container, @Nullable String schemaName, @Nullable String queryName, @Nullable String viewName, @Nullable User user, boolean inheritableOnly) throws SQLException
    {
        CstmView.Key key = new CstmView.Key(container);
        if (schemaName != null)
            key.setSchema(schemaName);
        if (queryName != null)
            key.setQueryName(queryName);
        if (viewName != null)
            key.setName(viewName);
        if (inheritableOnly)
        {
            key.setFlagMask(FLAG_INHERITABLE, FLAG_INHERITABLE);
        }

        key.setUser(user);
        return key.select();
    }

    public CstmView update(User user, CstmView view) throws SQLException
    {
        return Table.update(user, getTableInfoCustomView(), view, view.getCustomViewId());
    }

    public CstmView insert(User user, CstmView view) throws SQLException
    {
        return Table.insert(user, getTableInfoCustomView(), view);
    }

    public void delete(User user, CstmView view) throws SQLException
    {
        Table.delete(getTableInfoCustomView(), view.getCustomViewId());
    }


    public void updateViewsAfterRename(@NotNull Container c, @NotNull String schema,
            @NotNull String oldQueryName, @NotNull String newQueryName)
    {
        try
        {
            Table.execute(getDbSchema(), "UPDATE " + getTableInfoCustomView() + " SET queryname=? WHERE container=? AND \"schema\"=? AND queryname=?",
                    newQueryName, c, schema, oldQueryName);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    
    public boolean canInherit(int flag)
    {
        return (flag & FLAG_INHERITABLE) != 0;
    }

    public ExternalSchemaDef getExternalSchemaDef(int id)
    {
        return Table.selectObject(getTableInfoExternalSchema(), id, ExternalSchemaDef.class);
    }

    public ExternalSchemaDef[] getExternalSchemaDefs(@Nullable Container container)
    {
        ExternalSchemaDef.Key key = new ExternalSchemaDef.Key(container);
        return key.select();
    }

    public ExternalSchemaDef getExternalSchemaDef(Container container, String userSchemaName)
    {
        if (userSchemaName == null)
            return null;

        ExternalSchemaDef.Key key = new ExternalSchemaDef.Key(container);
        key.setUserSchemaName(userSchemaName);
        return key.selectObject();
    }

    public ExternalSchemaDef insert(User user, ExternalSchemaDef def) throws Exception
    {
        ExternalSchemaDef ret = Table.insert(user, getTableInfoExternalSchema(), def);
        ExternalSchemaDocumentProvider.getInstance().enumerateDocuments(null, def.lookupContainer(), null);
        return ret;
    }

    public ExternalSchemaDef update(User user, ExternalSchemaDef def) throws Exception
    {
        ExternalSchemaDef ret = Table.update(user, getTableInfoExternalSchema(), def, def.getExternalSchemaId());
        ExternalSchemaDocumentProvider.getInstance().enumerateDocuments(null, def.lookupContainer(), null);
        return ret;
    }

    public void delete(User user, ExternalSchemaDef def) throws Exception
    {
        Table.delete(getTableInfoExternalSchema(), def.getExternalSchemaId());
        ExternalSchemaDocumentProvider.getInstance().enumerateDocuments(null, def.lookupContainer(), null);
    }


    public void reloadAllExternalSchemas(Container c)
    {
        ExternalSchemaDef[] defs = getExternalSchemaDefs(c);

        for (ExternalSchemaDef def : defs)
            reloadExternalSchema(def);
    }


    public void reloadExternalSchema(ExternalSchemaDef def)
    {
        ExternalSchema.uncache(def);
    }


    public int setCanInherit(int flag, boolean f)
    {
        if (f)
        {
            return flag | FLAG_INHERITABLE;
        }
        else
        {
            return flag & ~FLAG_INHERITABLE;
        }
    }

    public boolean isHidden(int flag)
    {
        return (flag & FLAG_HIDDEN) != 0;
    }

    public int setIsHidden(int flag, boolean f)
    {
        if (f)
        {
            return flag | FLAG_HIDDEN;
        }
        else
        {
            return flag & ~FLAG_HIDDEN;
        }
    }

    public boolean isSnapshot(int flag)
    {
        return (flag & FLAG_SNAPSHOT) != 0;
    }

    public int setIsSnapshot(int flag, boolean f)
    {
        if (f)
        {
            return flag | FLAG_SNAPSHOT;
        }
        else
        {
            return flag & ~FLAG_SNAPSHOT;
        }
    }
    
    public String getDbSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getDbSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public TableInfo getTableInfoQueryDef()
    {
        return getDbSchema().getTable("QueryDef");
    }

    public TableInfo getTableInfoQuerySnapshotDef()
    {
        return getDbSchema().getTable("QuerySnapshotDef");
    }

    public TableInfo getTableInfoCustomView()
    {
        return getDbSchema().getTable("CustomView");
    }

    public TableInfo getTableInfoExternalSchema()
    {
        return getDbSchema().getTable("ExternalSchema");
    }

    public void containerDeleted(Container c) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Container", c.getId());
        Table.delete(getTableInfoQuerySnapshotDef(), filter);
        Table.delete(getTableInfoCustomView(), filter);
        Table.delete(getTableInfoQueryDef(), filter);
        Table.delete(getTableInfoExternalSchema(), filter);
    }

    public void addQueryListener(QueryService.QueryListener listener)
    {
        _listeners.add(listener);
    }

    public void fireViewChanged(CustomView view)
    {
        for (QueryService.QueryListener l : _listeners)
            l.viewChanged(view);
    }

    public void fireViewDeleted(CustomView view)
    {
        for (QueryService.QueryListener l : _listeners)
            l.viewDeleted(view);
    }

    static public final ContainerManager.ContainerListener CONTAINER_LISTENER = new ContainerManager.ContainerListener()
    {
        public void containerCreated(Container c, User user)
        {

        }

        @Override
        public void containerMoved(Container c, Container oldParent, User user)
        {
            
        }

        public void containerDeleted(Container c, User user)
        {
            try
            {
                QueryManager.get().containerDeleted(c);
            }
            catch (SQLException e)
            {
                _log.error("Error", e);
            }
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
        }

    };

    public void validateQuery(String schemaName, String queryName, User user, Container container) throws SQLException, QueryParseException
    {
        validateQuery(schemaName, queryName, user, container, true);
    }

    public void validateQuery(String schemaName, String queryName, User user, Container container, boolean testAllColumns) throws SQLException, QueryParseException
    {
        UserSchema schema = (UserSchema) DefaultSchema.get(user, container).getSchema(schemaName);
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + schemaName + "'!");

        TableInfo table = schema.getTable(queryName);
        if (null == table)
            throw new IllegalArgumentException("The query '" + queryName + "' was not found in the schema '" + schemaName + "'!");

        Collection<QueryService.ParameterDecl> params = table.getNamedParameters();
        Map<String,Object> parameters = new HashMap<String,Object>();
        for (QueryService.ParameterDecl p : params)
        {
            if (!p.isRequired())
                continue;
            parameters.put(p.getName(), null);
        }

        //get the set of columns
        List<ColumnInfo> cols = null;
        if (testAllColumns)
        {
            List<FieldKey> defVisCols = table.getDefaultVisibleColumns();
            Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(table, defVisCols);
            cols = new ArrayList<ColumnInfo>(colMap.values());
        }

        //try to execute it with a rowcount of 0 (will throw SQLException to client if it fails
        Table.TableResultSet results = null;
        try
        {
            //use selectForDisplay to mimic the behavior one would get in the UI
            if (testAllColumns)
                results = Table.selectForDisplay(table, Table.ALL_COLUMNS, parameters, null, null, Table.NO_ROWS, 0);
            else
                results = Table.selectForDisplay(table, cols, parameters, null, null, Table.NO_ROWS, 0);
        }
        finally
        {
            ResultSetUtil.close(results);
        }
    }
}
