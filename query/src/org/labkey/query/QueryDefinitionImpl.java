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
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.MetadataException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ViewOptions;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.design.DgColumn;
import org.labkey.query.design.DgQuery;
import org.labkey.query.design.DgTable;
import org.labkey.query.design.DgValue;
import org.labkey.query.design.QueryDocument;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.QueryTableInfo;
import org.labkey.query.view.CustomViewSetKey;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"ThrowableInstanceNeverThrown"})
public abstract class QueryDefinitionImpl implements QueryDefinition
{
    final static private QueryManager mgr = QueryManager.get();
    final static private Logger log = Logger.getLogger(QueryDefinitionImpl.class);
    protected User _user = null;
    protected UserSchema _schema = null;
    protected QueryDef _queryDef;
    private boolean _dirty;
    private ContainerFilter _containerFilter;
    private boolean _temporary = false;

    public QueryDefinitionImpl(User user, QueryDef queryDef)
    {
        _user = user;
        _queryDef = queryDef;
        _dirty = queryDef.getQueryDefId() == 0;
    }

    public QueryDefinitionImpl(User user, Container container, UserSchema schema, String name)
    {
        this(user, container, schema.getName(), name);
        _schema = schema;
    }

    public QueryDefinitionImpl(User user, Container container, String schema, String name)
    {
        _user = user;
        _queryDef = new QueryDef();
        _queryDef.setName(name);
        _queryDef.setSchema(schema);
        _queryDef.setContainer(container.getId());
        _dirty = true;
    }

    public boolean canInherit()
    {
        return (_queryDef.getFlags() & QueryManager.FLAG_INHERITABLE) != 0;
    }

    public void setContainerFilter(ContainerFilter containerFilter)
    {
        _containerFilter = containerFilter;
    }

    public ContainerFilter getContainerFilter()
    {
        return _containerFilter;
    }

    public void delete(User user) throws SQLException
    {
        if (!canEdit(user))
        {
            throw new IllegalArgumentException("Access denied");
        }
        QueryManager.get().delete(user, _queryDef);
        _queryDef = null;
    }

    protected boolean isNew()
    {
        return _queryDef.getQueryDefId() == 0;
    }

    public boolean canEdit(User user)
    {
        return getContainer().hasPermission(user, AdminPermission.class);
    }

    public CustomView getCustomView(User user, HttpServletRequest request, String name)
    {
        return getAllCustomViews(user, request, true, false).get(name);
    }

    public CustomView createCustomView(User user, String name)
    {
        return new CustomViewImpl(this, user, name);
    }

    public Map<String, CustomView> getCustomViews(User user, HttpServletRequest request)
    {
        return getAllCustomViews(user, request, true);
    }

    private Map<String, CustomView> getAllCustomViews(User user, HttpServletRequest request, boolean inheritable)
    {
        return getAllCustomViews(user, request, inheritable, false);
    }

    private Map<String, CustomView> getAllCustomViews(User user, HttpServletRequest request, boolean inheritable, boolean allModules)
    {
        Map<String, CustomView> ret = new LinkedHashMap<String, CustomView>();

        try
        {
            Container container = getContainer();

            // Database custom view and module custom views.
            ret.putAll(((QueryServiceImpl)QueryService.get()).getCustomViewMap(user, container, this, inheritable));

            // Session views have highest precedence.
            if (user != null && request != null)
            {
                for (CstmView view : CustomViewSetKey.getCustomViewsFromSession(request, this).values())
                {
                    CustomViewImpl v = new CustomViewImpl(this, view);
                    v.isSession(true);
                    ret.put(view.getName(), v);
                }
            }
        }
        catch (SQLException e)
        {
            log.error("Error", e);
        }

        return ret;
    }

    public User getUser()
    {
        return _user;
    }
    
    public Container getContainer()
    {
        return ContainerManager.getForId(_queryDef.getContainerId());
    }

    public String getName()
    {
        return _queryDef.getName();
    }

    public List<QueryParseException> getParseErrors(QuerySchema schema)
    {
        List<QueryParseException> ret = new ArrayList<QueryParseException>();

        for (QueryException e : getQuery(schema).getParseErrors())
            ret.add(wrapParseException(e));

        String metadata = StringUtils.trimToNull(getMetadataXml());
        if (metadata != null)
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            List<XmlError> errors = new ArrayList<XmlError>();
            options.setErrorListener(errors);
            try
            {
                TablesDocument table = TablesDocument.Factory.parse(metadata, options);
                table.validate(options);
            }
            catch (XmlException xmle)
            {
                ret.add(wrapParseException(new MetadataException("Metadata XML " + XmlBeansUtil.getErrorMessage(xmle))));
            }
            for (XmlError xmle : errors)
            {
                ret.add(new QueryParseException(xmle.getMessage(), null, xmle.getLine(), xmle.getColumn()));
            }
        }
        return ret;
    }

    static QueryParseException wrapParseException(Throwable e)
    {
        if (e instanceof QueryParseException)
        {
            return (QueryParseException) e;
        }
        if (e instanceof MetadataException)
        {
            return new QueryParseException(e.getMessage(), e, 0, 0);
        }
        return new QueryParseException("Unexpected exception", e, 0, 0);
    }

    public Query getQuery(@NotNull QuerySchema schema)
    {
        Query query = new Query(schema);
        String sql = getSql();
        if (sql != null)
        {
            query.parse(sql);
        }
        query.setName(getSchemaName() + "." + getName());
        return query;
    }


    public Query getQuery(@NotNull QuerySchema schema, List<QueryException> errors)
    {
        return getQuery(schema, errors, null);
    }

    /*
     * I find it very strange that only the xml errors get added to the "errors" list, while
     * the parse errors remain in the getParseErrors() list
     */
    public Query getQuery(@NotNull QuerySchema schema, List<QueryException> errors, Query parent)
    {
        Query query = new Query(schema, parent);
        query.setName(getSchemaName() + "." + getName());
        query.setContainerFilter(getContainerFilter());
        String sql = getSql();
        if (sql != null)
        {
            query.parse(sql);
        }
        TablesDocument doc = getTablesDocument(errors);
        query.setTablesDocument(doc);
        return query;
    }


    public UserSchema getSchema()
    {
        if (null == _schema)
            _schema = (UserSchema) DefaultSchema.get(getUser(), getContainer()).getSchema(getSchemaName());
        assert _schema.getSchemaName().equals(getSchemaName());
        return _schema;
    }


    public String getSchemaName()
    {
        return _queryDef.getSchema();
    }


    private TablesDocument getTablesDocument(List<QueryException> errors)
    {
        String xml = getMetadataXml();
        if (xml != null)
        {
            try
            {
                return TablesDocument.Factory.parse(xml);
            }
            catch (XmlException xmlException)
            {
                errors.add(new QueryParseException("Error in XML", xmlException, 0, 0));
            }
        }
        return null;
    }

    public TableInfo getTable(List<QueryException> errors, boolean includeMetadata)
    {
        return getTable(getSchema(), errors, includeMetadata);
    }

    public TableInfo getTable(@NotNull UserSchema schema, List<QueryException> errors, boolean includeMetadata)
    {
        if (errors == null)
        {
            errors = new ArrayList<QueryException>();
        }
        Query query = getQuery(schema, errors);
        if (!includeMetadata)
            query.setTablesDocument(null);
        TableInfo ret = query.getTableInfo();
        if (null != ret)
        {
            QueryTableInfo queryTable = (QueryTableInfo)ret;
            queryTable.setDescription(getDescription());
            queryTable.setName(getName());
        }

        if (null != ret && null != query.getTablesDocument())
            ((QueryTableInfo)ret).loadFromXML(schema, query.getTablesDocument().getTables().getTableArray(0), errors);

        if (!query.getParseErrors().isEmpty())
        {
            String resolveURL = null;
            if (null != ret)
            {
                ActionURL sourceURL = urlFor(QueryAction.sourceQuery);
                if (sourceURL != null)
                    resolveURL = sourceURL.getLocalURIString(false);
            }
            
            for (QueryException qe : query.getParseErrors())
            {
                if (ExceptionUtil.decorateException(qe, ExceptionUtil.ExceptionInfo.ResolveURL, resolveURL, false))
                    ExceptionUtil.decorateException(qe, ExceptionUtil.ExceptionInfo.ResolveText, "edit " + getName(), true);
                errors.add(qe);
            }
        }

        return ret;
    }

    public TableInfo getMainTable()
    {
        Query query = getQuery(getSchema());
        Set<FieldKey> tables = query.getFromTables();
        if (tables.size() != 1)
            return null;
        return query.getFromTable(tables.iterator().next());
    }

    public String getMetadataXml()
    {
        return _queryDef.getMetaData();
    }

    public void setContainer(Container container)
    {
        if (container.equals(getContainer()))
            return;
        QueryDef queryDefNew = new QueryDef();
        queryDefNew.setSchema(_queryDef.getSchema());
        queryDefNew.setName(getName());
        queryDefNew.setContainer(container.getId());
        queryDefNew.setSql(_queryDef.getSql());
        queryDefNew.setMetaData(_queryDef.getMetaData());
        queryDefNew.setDescription(_queryDef.getDescription());
        _queryDef = queryDefNew;
        _dirty = true;
    }

    public void save(User user, Container container) throws SQLException
    {
        setContainer(container);
        if (!_dirty)
            return;
        if (isNew())
        {
            _queryDef = QueryManager.get().insert(user, _queryDef);
        }
        else
        {
            _queryDef = QueryManager.get().update(user, _queryDef);
        }
        _dirty = false;
    }

    public void setCanInherit(boolean f)
    {
        edit().setFlags(mgr.setCanInherit(_queryDef.getFlags(), f));
    }

    public boolean isHidden()
    {
        return mgr.isHidden(_queryDef.getFlags());
    }

    public void setIsHidden(boolean f)
    {
        edit().setFlags(mgr.setIsHidden(_queryDef.getFlags(), f));
    }

    public boolean isSnapshot()
    {
        return mgr.isSnapshot(_queryDef.getFlags());
    }

    @Override
    public void setIsTemporary(boolean temporary)
    {
        _temporary = temporary;
    }

    @Override
    public boolean isTemporary()
    {
        return _temporary;
    }

    public void setIsSnapshot(boolean f)
    {
        edit().setFlags(mgr.setIsSnapshot(_queryDef.getFlags(), f));
    }

    public void setMetadataXml(String xml)
    {
        edit().setMetaData(StringUtils.trimToNull(xml));
    }

    public ActionURL urlFor(QueryAction action)
    {
        return urlFor(action, getContainer());
    }

    public ActionURL urlFor(QueryAction action, Container container)
    {
        if (action == QueryAction.schemaBrowser)
            action = QueryAction.begin;
        ActionURL url = new ActionURL("query", action.toString(), container);
        url.addParameter(QueryParam.schemaName.toString(), getSchemaName());
        url.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, getName());
        return url;
    }

    public ActionURL urlFor(QueryAction action, Container container, Map<String, Object> pks)
    {
        ActionURL url = urlFor(action, container);
        for (Map.Entry<String, Object> pk : pks.entrySet())
        {
            if (pk.getValue() != null)
            {
                url.addParameter(pk.getKey(), pk.getValue().toString());
            }
        }
        return url;
    }

    public StringExpression urlExpr(QueryAction action, Container container)
    {
        // UNDONE: use getMainTable().urlExpr(action, container) instead
        ActionURL url = urlFor(action, container);
        if (url != null)
            return StringExpressionFactory.create(url.getLocalURIString());
        return null;
    }

    public String getDescription()
    {
        return _queryDef.getDescription();
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public QueryDef getQueryDef()
    {
        return _queryDef;
    }

    public List<ColumnInfo> getColumns(CustomView view, TableInfo table)
    {
        if (table == null)
            throw new NullPointerException();

        if (view != null)
        {
            Map<FieldKey, ColumnInfo> map = QueryService.get().getColumns(table, view.getColumns());

            if (!map.isEmpty())
            {
                return new ArrayList<ColumnInfo>(map.values());
            }
        }

        return new ArrayList<ColumnInfo>(QueryService.get().getColumns(table, table.getDefaultVisibleColumns()).values());
    }

    public List<DisplayColumn> getDisplayColumns(CustomView view, TableInfo table)
    {
        if (table == null)
            throw new NullPointerException();
        List<DisplayColumn> ret;
        if (view != null)
        {
            ret = QueryService.get().getDisplayColumns(table, view.getColumnProperties());
            if (!ret.isEmpty())
            {
                return ret;
            }
        }
        ret = new ArrayList<DisplayColumn>();
        for (ColumnInfo column : QueryService.get().getColumns(table, table.getDefaultVisibleColumns()).values())
        {
            ret.add(column.getRenderer());
        }
        return ret;
    }

    public QueryDocument getDesignDocument(QuerySchema schema)
    {
        Query query = getQuery(schema); 
        QueryDocument ret = query.getDesignDocument();
        if (ret == null)
            return null;
        Map<String, DgColumn> columns = new HashMap<String, DgColumn>();
        for (DgColumn dgColumn : ret.getQuery().getSelect().getColumnArray())
        {
            columns.put(dgColumn.getAlias(), dgColumn);
        }
        String strMetadata = getMetadataXml();
        if (strMetadata != null)
        {
            try
            {
                TablesDocument doc = TablesDocument.Factory.parse(getMetadataXml());
                for (ColumnType column : doc.getTables().getTableArray()[0].getColumns().getColumnArray())
                {
                    DgColumn dgColumn = columns.get(column.getColumnName());
                    if (dgColumn != null)
                    {
                        dgColumn.setMetadata(column);
                    }
                }
            }
            catch (Exception e)
            {

            }
        }
        DgQuery.From from = ret.getQuery().addNewFrom();
        for (FieldKey key : query.getFromTables())
        {
            DgTable dgTable = from.addNewTable();
            dgTable.setAlias(key.toString());
            TableXML.initTable(dgTable.addNewMetadata(), query.getFromTable(key), key);
        }

        return ret;
    }

    public boolean updateDesignDocument(QuerySchema schema, QueryDocument doc, List<QueryException> errors)
    {
        Map<String, DgColumn> columns = new LinkedHashMap<String, DgColumn>();
        DgQuery dgQuery = doc.getQuery();
        DgQuery.Select select = dgQuery.getSelect();
        for (DgColumn column : select.getColumnArray())
        {
            String alias = column.getAlias();
            if (alias == null)
            {
                DgValue value = column.getValue();
                if (value.getField() == null)
                {
                    errors.add(new QueryException("Expression column '" + value.getSql() + "' requires an alias."));
                    return false;
                }
                FieldKey key = FieldKey.fromString(value.getField().getStringValue());
                alias = key.getName();
            }
            if (columns.containsKey(alias))
            {
                errors.add(new QueryException("There is more than one column with the alias '" + alias + "'."));
                return false;
            }
            columns.put(alias, column);
        }
        Query query = getQuery(schema);
        query.update(dgQuery, errors);
        setSql(query.getQueryText());
        String xml = getMetadataXml();
        TablesDocument tablesDoc;
        TableType xbTable;
        if (xml != null)
        {
            try
            {
                tablesDoc = TablesDocument.Factory.parse(xml);
            }
            catch (XmlException xmlException)
            {
                errors.add(new QueryException("There was an error parsing the query's original Metadata XML: " + xmlException.getMessage()));
                return false;
            }
        }
        else
        {
            tablesDoc = TablesDocument.Factory.newInstance();
        }
        TablesDocument.Tables tables = tablesDoc.getTables();
        if (tables == null)
        {
            tables = tablesDoc.addNewTables();
        }
        if (tables.getTableArray().length < 1)
        {
            xbTable = tables.addNewTable();
        }
        else
        {
            xbTable = tables.getTableArray()[0];
        }
        
        xbTable.setTableName(getName());
        xbTable.setTableDbType("NOT_IN_DB");
        TableType.Columns xbColumns = xbTable.getColumns();
        if (xbColumns == null)
        {
            xbColumns = xbTable.addNewColumns();
        }
        List<ColumnType> lstColumns = new ArrayList<ColumnType>();
        for (Map.Entry<String, DgColumn> entry : columns.entrySet())
        {
            ColumnType xbColumn = entry.getValue().getMetadata();
            if (xbColumn != null)
            {
                xbColumn.setColumnName(entry.getKey());
                lstColumns.add(xbColumn);
            }
        }
        xbColumns.setColumnArray(lstColumns.toArray(new ColumnType[lstColumns.size()]));
        setMetadataXml(tablesDoc.toString());
        return errors.size() == 0;
    }

    protected QueryDef edit()
    {
        if (_dirty)
            return _queryDef;
        _queryDef = _queryDef.clone();
        _dirty = true;
        return _queryDef;
    }

    public boolean isTableQueryDefinition()
    {
        return false;
    }

    @Override
    public boolean isSqlEditable()
    {
        return true;
    }

    public boolean isMetadataEditable()
    {
        return true;
    }

    public ViewOptions getViewOptions()
    {
        return new ViewOptionsImpl(getMetadataXml());
    }

    private class ViewOptionsImpl implements ViewOptions
    {
        private TablesDocument _document;

        public ViewOptionsImpl(String metadataXml)
        {
            if (!StringUtils.isBlank(metadataXml))
            {
                try
                {
                    _document = TablesDocument.Factory.parse(metadataXml);
                }
                catch (XmlException e)
                {
                    // Don't completely die if someone specified invalid metadata XML. Log a warning
                    // and render without the custom metadata.
                    log.warn("Unable to parse metadata XML for " + getSchemaName() + "." + getName() + " in " + getContainer(), e);
                }
            }
            if (_document == null)
            {
                _document = TablesDocument.Factory.newInstance();
                TableType table = _document.addNewTables().addNewTable();

                table.setTableName(getName());
                table.setTableDbType("NOT_IN_DB");
            }
        }

        public List<ViewFilterItem> getViewFilterItems()
        {
            List<ViewFilterItem> items = new ArrayList<ViewFilterItem>();

            org.labkey.data.xml.ViewOptions options = _document.getTables().getTableArray()[0].getViewOptions();
            if (options == null)
                options = _document.getTables().getTableArray()[0].addNewViewOptions();

            for (org.labkey.data.xml.ViewOptions.ViewFilterItem item : options.getViewFilterItemArray())
            {
                items.add(new ViewFilterItemImpl(item.getType(), item.getEnabled()));
            }
            return items;
        }

        public void setViewFilterItems(List<ViewFilterItem> items)
        {
            List<org.labkey.data.xml.ViewOptions.ViewFilterItem> filterItems = new ArrayList<org.labkey.data.xml.ViewOptions.ViewFilterItem>();

            for (ViewFilterItem item : items)
            {
                org.labkey.data.xml.ViewOptions.ViewFilterItem vfi = org.labkey.data.xml.ViewOptions.ViewFilterItem.Factory.newInstance();
                vfi.setType(item.getViewType());
                vfi.setEnabled(item.isEnabled());

                filterItems.add(vfi);
            }
            org.labkey.data.xml.ViewOptions options = _document.getTables().getTableArray()[0].getViewOptions();
            if (options == null)
                options = _document.getTables().getTableArray()[0].addNewViewOptions();

            options.setViewFilterItemArray(filterItems.toArray(new org.labkey.data.xml.ViewOptions.ViewFilterItem[filterItems.size()]));
        }

        public void save(User user) throws SQLException
        {
            setMetadataXml(_document.toString());
            QueryDefinitionImpl.this.save(user, getContainer());
        }

        public void delete(User user) throws SQLException
        {
            _document.getTables().getTableArray()[0].unsetViewOptions();
            save(user);
        }
    }
}
