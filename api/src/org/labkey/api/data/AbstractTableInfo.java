/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.module.Module;
import org.labkey.api.query.AggregateRowConfig;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.MetadataException;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.resource.Resource;
import org.labkey.api.script.ScriptReference;
import org.labkey.api.script.ScriptService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.ImportTemplateType;
import org.labkey.data.xml.PositionTypeEnum;
import org.labkey.data.xml.TableType;

import javax.script.ScriptException;
import java.sql.ResultSet;
import java.sql.SQLException;
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

abstract public class AbstractTableInfo implements TableInfo
{
    protected Iterable<FieldKey> _defaultVisibleColumns;
    protected DbSchema _schema;
    protected String _titleColumn;
    protected boolean _hasDefaultTitleColumn = true;
    private int _cacheSize = DbCache.DEFAULT_CACHE_SIZE;

    protected final Map<String, ColumnInfo> _columnMap;
    private Map<String, MethodInfo> _methodMap;
    protected String _name;
    protected String _description;
    protected String _importMsg;
    protected List<Pair<String, DetailsURL>> _importTemplates;

    protected DetailsURL _gridURL;
    protected DetailsURL _insertURL;
    protected DetailsURL _updateURL;
    protected DetailsURL _deleteURL;
    protected DetailsURL _importURL;
    protected ButtonBarConfig _buttonBarConfig;
    protected AggregateRowConfig _aggregateRowConfig;

    private List<DetailsURL> _detailsURLs = new ArrayList<DetailsURL>(1);

    @NotNull
    public List<ColumnInfo> getPkColumns()
    {
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>();
        for (ColumnInfo column : getColumns())
        {
            if (column.isKeyField())
            {
                ret.add(column);
            }
        }
        return Collections.unmodifiableList(ret);
    }


    public AbstractTableInfo(DbSchema schema)
    {
        _schema = schema;
        _columnMap = constructColumnMap();
        assert MemTracker.put(this);
    }


    public void afterConstruct()
    {
        ContainerContext cc = getContainerContext();
        if (null != cc)
        {
            for (ColumnInfo c : getColumns())
            {
                StringExpression url = c.getURL();
                if (url instanceof DetailsURL)
                    ((DetailsURL)url).setContainerContext(cc, false);
            }
            for (DetailsURL detailsURL : _detailsURLs)
            {
                detailsURL.setContainerContext(cc);
            }
        }
    }


    protected Map<String, ColumnInfo> constructColumnMap()
    {
        if (isCaseSensitive())
        {
            return new LinkedHashMap<String, ColumnInfo>();
        }
        return new CaseInsensitiveMapWrapper<ColumnInfo>(new LinkedHashMap<String, ColumnInfo>());
    }

    protected boolean isCaseSensitive()
    {
        return false;
    }

    public DbSchema getSchema()
    {
        return _schema;
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public List<String> getPkColumnNames()
    {
        List<String> ret = new ArrayList<String>();
        for (ColumnInfo col : getPkColumns())
        {
            ret.add(col.getName());
        }
        return Collections.unmodifiableList(ret);
    }

    public ColumnInfo getVersionColumn()
    {
        return null;
    }

    public String getVersionColumnName()
    {
        return null;
    }

    @Override
    public DatabaseTableType getTableType()
    {
        return DatabaseTableType.NOT_IN_DB;
    }

    @NotNull
    public SQLFragment getFromSQL(String alias)
    {
        if (null != getSelectName())
            return new SQLFragment().append(getSelectName()).append(" ").append(alias);
        else
            return new SQLFragment().append("(").append(getFromSQL()).append(") ").append(alias);
    }


    // don't have to implement if you override getFromSql(String alias)
    abstract protected SQLFragment getFromSQL();


    public NamedObjectList getSelectList()
    {
        List<ColumnInfo> pkColumns = getPkColumns();
        if (pkColumns.size() != 1)
            return new NamedObjectList();

        return getSelectList(pkColumns.get(0));
    }


    public NamedObjectList getSelectList(String columnName)
    {
        if (columnName == null)
            return getSelectList();
        
        ColumnInfo column = getColumn(columnName);
        return getSelectList(column);
    }


    public NamedObjectList getSelectList(ColumnInfo firstColumn)
    {
        NamedObjectList ret = new NamedObjectList();
        if (firstColumn == null)
            return ret;
        ColumnInfo titleColumn = getColumn(getTitleColumn());
        if (titleColumn == null)
            return ret;
        try
        {
            List<ColumnInfo> cols;
            int titleIndex;
            if (firstColumn == titleColumn)
            {
                cols = Arrays.asList(firstColumn);
                titleIndex = 1;
            }
            else
            {
                cols = Arrays.asList(firstColumn, titleColumn);
                titleIndex = 2;
            }

            String sortStr = (titleColumn.getSortDirection() != null ? titleColumn.getSortDirection().getDir() : "") + titleColumn.getName();
            ResultSet rs = Table.select(this, cols, null, new Sort(sortStr));
            while (rs.next())
            {
                ret.put(new SimpleNamedObject(rs.getString(1), rs.getString(titleIndex)));
            }
            rs.close();
        }
        catch (SQLException e)
        {
            
        }
        return ret;
    }

    public List<ColumnInfo> getUserEditableColumns()
    {
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>();
        for (ColumnInfo col : getColumns())
        {
            if (col.isUserEditable())
            {
                ret.add(col);
            }
        }
        return Collections.unmodifiableList(ret);
    }

    public List<ColumnInfo> getColumns(String colNames)
    {
        String[] colNameArray = colNames.split(",");
        return getColumns(colNameArray);
    }

    public List<ColumnInfo> getColumns(String... colNameArray)
    {
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>(colNameArray.length);
        for (String name : colNameArray)
        {
            ret.add(getColumn(name.trim()));
        }
        return Collections.unmodifiableList(ret);
    }

    @Override
    public boolean hasDefaultTitleColumn()
    {
        return _hasDefaultTitleColumn;
    }

    @Override
    public String getTitleColumn()
    {
        if (null == _titleColumn)
        {
            for (ColumnInfo column : getColumns())
            {
                if (column.isStringType() && !column.getSqlTypeName().equalsIgnoreCase("entityid"))
                {
                    _titleColumn = column.getName();
                    break;
                }
            }
            if (null == _titleColumn && getColumns().size() > 0)
                _titleColumn = getColumns().get(0).getName();
        }

        return _titleColumn;
    }

    public void setTitleColumn(String titleColumn)
    {
        checkLocked();
        setTitleColumn(titleColumn, null == titleColumn);
    }

    // Passing in defaultTitleColumn helps with export & serialization
    public void setTitleColumn(String titleColumn, boolean defaultTitleColumn)
    {
        checkLocked();
        _titleColumn = titleColumn;
        _hasDefaultTitleColumn = defaultTitleColumn;
    }

    public ColumnInfo getColumn(String name)
    {
        ColumnInfo ret = _columnMap.get(name);
        if (ret != null)
            return ret;
        return resolveColumn(name);
    }

    @Override
    public ColumnInfo getColumn(FieldKey name)
    {
        if (null != name.getParent())
            return null;
        return getColumn(name.getName());
    }


    /**
     * If a column wasn't found in the standard column list, give the table a final chance to locate it.
     * Useful for preserving backwards compatibility with saved queries when a column is renamed.
     */
    protected ColumnInfo resolveColumn(String name)
    {
        for (ColumnInfo col : getColumns())
        {
            if (col.getPropertyName().equalsIgnoreCase(name))
                return col;
        }
        return null;
    }

    public List<ColumnInfo> getColumns()
    {
        return Collections.unmodifiableList(new ArrayList<ColumnInfo>(_columnMap.values()));
    }

    public Set<String> getColumnNameSet()
    {
        return Collections.unmodifiableSet(_columnMap.keySet());
    }

    public String getName()
    {
        return _name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        checkLocked();
        _description = description;
    }

    public boolean removeColumn(ColumnInfo column)
    {
        checkLocked();
        return _columnMap.remove(column.getName()) != null;
    }

    public ColumnInfo addColumn(ColumnInfo column)
    {
        checkLocked();
        // Not true if this is a VirtualTableInfo
        // assert column.getParentTable() == this;
        if (_columnMap.containsKey(column.getName()))
        {
            throw new IllegalArgumentException("Column " + column.getName() + " already exists.");
        }
        _columnMap.put(column.getName(), column);
        assert column.lockName();
        return column;
    }

    public void addMethod(String name, MethodInfo method)
    {
        if (_methodMap == null)
        {
            _methodMap = new HashMap<String, MethodInfo>();
        }
        _methodMap.put(name, method);
    }

    public MethodInfo getMethod(String name)
    {
        if (_methodMap == null)
            return null;
        return _methodMap.get(name);
    }

    public void setName(String name)
    {
        checkLocked();
        _name = name;
    }

    public ActionURL getGridURL(Container container)
    {
        if (_gridURL != null)
        {
            return _gridURL.copy(container).getActionURL();
        }
        return null;
    }

    public ActionURL getInsertURL(Container container)
    {
        if (_insertURL == LINK_DISABLER)
        {
            return LINK_DISABLER_ACTION_URL;
        }
        if (_insertURL != null)
        {
            return _insertURL.copy(container).getActionURL();
        }
        return null;
    }

    @Override
    public ActionURL getImportDataURL(Container container)
    {
        if (_importURL == LINK_DISABLER)
        {
            return LINK_DISABLER_ACTION_URL;
        }
        if (_importURL != null)
        {
            return _importURL.copy(container).getActionURL();
        }
        return null;
    }

    public ActionURL getDeleteURL(Container container)
    {
        if (_deleteURL == LINK_DISABLER)
        {
            return LINK_DISABLER_ACTION_URL;
        }
        if (_deleteURL != null)
        {
            return _deleteURL.copy(container).getActionURL();
        }
        return null;
    }

    public StringExpression getUpdateURL(Set<FieldKey> columns, Container container)
    {
        if (_updateURL == LINK_DISABLER)
        {
            return LINK_DISABLER;
        }
        if (_updateURL != null && _updateURL.validateFieldKeys(columns))
        {
            return _updateURL.copy(container);
        }
        return null;
    }

    public StringExpression getDetailsURL(Set<FieldKey> columns, Container container)
    {
        ContainerContext containerContext = getContainerContext();
        if (containerContext == null)
            containerContext = container;

        for (DetailsURL dUrl : _detailsURLs)
        {
            if (dUrl.validateFieldKeys(columns))
                return dUrl.copy(containerContext);
        }
        return null;
    }

    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return false;
    }

    public void setDetailsURL(DetailsURL detailsURL)
    {
        _detailsURLs.clear();
        addDetailsURL(detailsURL);
    }

    public void addDetailsURL(DetailsURL detailsURL)
    {
        if (detailsURL != null)
        {
            _detailsURLs.add(detailsURL);
        }
    }

    @Override
    public boolean hasDetailsURL()
    {
        return !_detailsURLs.isEmpty();
    }

    public Set<FieldKey> getDetailsURLKeys()
    {
        HashSet<FieldKey> set = new HashSet<FieldKey>();
        for (DetailsURL dUrl : _detailsURLs)
            set.addAll(dUrl.getFieldKeys());
        return set;
    }

    public void setGridURL(DetailsURL gridURL)
    {
        checkLocked();
        _gridURL = gridURL;
    }

    public void setInsertURL(DetailsURL insertURL)
    {
        checkLocked();
        _insertURL = insertURL;
    }

    public void setImportURL(DetailsURL importURL)
    {
        checkLocked();
        _importURL = importURL;
    }

    public void setDeleteURL(DetailsURL deleteURL)
    {
        checkLocked();
        _deleteURL = deleteURL;
    }

    public void setUpdateURL(DetailsURL updateURL)
    {
        checkLocked();
        _updateURL = updateURL;
    }

    public ButtonBarConfig getButtonBarConfig()
    {
        return _buttonBarConfig;
    }

    public AggregateRowConfig getAggregateRowConfig()
    {
        return _aggregateRowConfig;
    }

    public void setAggregateRowConfig(AggregateRowConfig config)
    {
        checkLocked();
        _aggregateRowConfig = config;
    }

    public void setDefaultVisibleColumns(Iterable<FieldKey> list)
    {
        checkLocked();
        _defaultVisibleColumns = list;
    }

    /** @return unmodifiable list of the columns that should be shown by default for this table */
    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (_defaultVisibleColumns instanceof List)
        {
            return Collections.unmodifiableList((List<FieldKey>) _defaultVisibleColumns);
        }
        if (_defaultVisibleColumns != null)
        {
            List<FieldKey> ret = new ArrayList<FieldKey>();
            for (FieldKey key : _defaultVisibleColumns)
            {
                ret.add(key);
            }
            return Collections.unmodifiableList(ret);
        }
        return Collections.unmodifiableList(QueryService.get().getDefaultVisibleColumns(getColumns()));
    }

    public boolean safeAddColumn(ColumnInfo column)
    {
        checkLocked();
        if (getColumn(column.getName()) != null)
            return false;
        addColumn(column);
        return true;
    }


    public static ForeignKey makeForeignKey(QuerySchema fromSchema, ColumnType.Fk fk)
    {
        QuerySchema fkSchema = fromSchema;
        if (fk.getFkDbSchema() != null)
        {
            Container targetContainer = fromSchema.getContainer();
            if (fk.getFkFolderPath() != null)
            {
                targetContainer = ContainerManager.getForPath(fk.getFkFolderPath());
                if (targetContainer == null || !targetContainer.hasPermission(fromSchema.getUser(), ReadPermission.class))
                {
                    return null;
                }
            }
            fkSchema = QueryService.get().getUserSchema(fromSchema.getUser(), targetContainer, fk.getFkDbSchema());
            if (fkSchema == null)
                return null;
        }
        return new QueryForeignKey(fkSchema, fk.getFkTable(), fk.getFkColumnName(), null);
    }


    protected void initColumnFromXml(QuerySchema schema, ColumnInfo column, ColumnType xbColumn, Collection<QueryException> qpe)
    {
        checkLocked();
        column.loadFromXml(xbColumn, true);
        
        if (xbColumn.getFk() != null)
        {
            ForeignKey qfk = makeForeignKey(schema, xbColumn.getFk());
            if (qfk == null)
            {
                //noinspection ThrowableInstanceNeverThrown
                String msgColumnName =
                        (column.getParentTable()==null?"":column.getParentTable().getName()) +
                        column.getName();
                qpe.add(new MetadataException("Schema " + xbColumn.getFk().getFkDbSchema() + " not found, in foreign key definition: " + msgColumnName));
                return;
            }
            column.setFk(qfk);
        }
    }


    DetailsURL parseDetailsURL(Container c, String url, Collection<QueryException> errors)
    {
        try
        {
            return DetailsURL.fromString(c, url);
        }
        catch (IllegalArgumentException x)
        {
            errors.add(new QueryException("Illegal URL expression: " + url, x));
            return null;
        }
    }
    

    public void loadFromXML(QuerySchema schema, TableType xmlTable, Collection<QueryException> errors)
    {
        checkLocked();
        if (xmlTable.getTitleColumn() != null)
            setTitleColumn(xmlTable.getTitleColumn());
        if (xmlTable.getDescription() != null)
            setDescription(xmlTable.getDescription());
        if (xmlTable.getGridUrl() != null)
            _gridURL = parseDetailsURL(schema.getContainer(), xmlTable.getGridUrl(), errors);

        if (xmlTable.isSetImportUrl())
        {
            if (StringUtils.isBlank(xmlTable.getImportUrl()))
            {
                _importURL = LINK_DISABLER;
            }
            else
            {
                _importURL = parseDetailsURL(schema.getContainer(), xmlTable.getImportUrl(), errors);
            }
        }
        if (xmlTable.isSetInsertUrl())
        {
            if (StringUtils.isBlank(xmlTable.getInsertUrl()))
            {
                _insertURL = LINK_DISABLER;
            }
            else
            {
                _insertURL = parseDetailsURL(schema.getContainer(), xmlTable.getInsertUrl(), errors);
            }
        }
        if (xmlTable.isSetUpdateUrl())
        {
            if (StringUtils.isBlank(xmlTable.getUpdateUrl()))
            {
                _updateURL = LINK_DISABLER;
            }
            else
            {
                _updateURL = parseDetailsURL(schema.getContainer(), xmlTable.getUpdateUrl(), errors);
            }
        }
        if (xmlTable.isSetDeleteUrl())
        {
            if (StringUtils.isBlank(xmlTable.getDeleteUrl()))
            {
                _deleteURL = LINK_DISABLER;
            }
            else
            {
                _deleteURL = parseDetailsURL(schema.getContainer(), xmlTable.getDeleteUrl(), errors);
            }
        }

        if (xmlTable.getTableUrl() != null)
            setDetailsURL(parseDetailsURL(schema.getContainer(), xmlTable.getTableUrl(), errors));

        if (xmlTable.isSetCacheSize())
            _cacheSize = xmlTable.getCacheSize();

        if(xmlTable.getImportMessage() != null)
            setImportMessage(xmlTable.getImportMessage());

        if(xmlTable.getImportTemplates() != null)
            setImportTemplates(xmlTable.getImportTemplates().getTemplateArray());

        if (xmlTable.getColumns() != null)
        {
            List<ColumnType> wrappedColumns = new ArrayList<ColumnType>();

            for (ColumnType xmlColumn : xmlTable.getColumns().getColumnArray())
            {
                if (xmlColumn.getWrappedColumnName() != null)
                {
                    wrappedColumns.add(xmlColumn);
                }
                else
                {
                    ColumnInfo column = getColumn(xmlColumn.getColumnName());

                    if (column != null)
                    {
                        initColumnFromXml(schema, column, xmlColumn, errors);
                    }
                }
            }

            for (ColumnType wrappedColumnXml : wrappedColumns)
            {
                ColumnInfo column = getColumn(wrappedColumnXml.getWrappedColumnName());

                if (column != null && getColumn(wrappedColumnXml.getColumnName()) == null)
                {
                    ColumnInfo wrappedColumn = new WrappedColumn(column, wrappedColumnXml.getColumnName());
                    initColumnFromXml(schema, wrappedColumn, wrappedColumnXml, errors);
                    addColumn(wrappedColumn);
                }
            }
        }

        if (xmlTable.getButtonBarOptions() != null)
            _buttonBarConfig = new ButtonBarConfig(xmlTable.getButtonBarOptions());

        if(xmlTable.getAggregateRowOptions() != null && xmlTable.getAggregateRowOptions().getPosition() != null)
        {
            setAggregateRowConfig(xmlTable);
        }
    }

    private void setAggregateRowConfig(TableType xmlTable)
    {
        checkLocked();
        _aggregateRowConfig = new AggregateRowConfig(false, false);

        PositionTypeEnum.Enum position = xmlTable.getAggregateRowOptions().getPosition();
        if(position.equals(PositionTypeEnum.BOTH) || position.equals(PositionTypeEnum.TOP))
        {
            _aggregateRowConfig.setAggregateRowFirst(true);
        }
        if(position.equals(PositionTypeEnum.BOTH) || position.equals(PositionTypeEnum.BOTTOM))
        {
            _aggregateRowConfig.setAggregateRowLast(true);
        }
    }

    /**
     * Returns true by default. Override if your derived class is not accessible through Query
     * @return Whether this table is public (i.e., accessible via Query)
     */
    public boolean isPublic()
    {
        //by default, all subclasses are public (i.e., accessible through Query)
        //override to change this
        return getPublicName() != null && getPublicSchemaName() != null;
    }

    public String getPublicName()
    {
        return getName();
    }

    public String getPublicSchemaName()
    {
        return getSchema().getName();
    }

    public boolean needsContainerClauseAdded()
    {
        return true;
    }

    public ContainerFilter getContainerFilter()
    {
        return null;
    }

    public boolean isMetadataOverrideable()
    {
        return true;
    }

    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        checkLocked();
        if (isMetadataOverrideable())
        {
            TableType metadata = QueryService.get().findMetadataOverride(schema, tableName, false, false, errors, null);
            overlayMetadata(metadata, schema, errors);
        }
    }

    public void overlayMetadata(TableType metadata, UserSchema schema, Collection<QueryException> errors)
    {
        checkLocked();
        if (metadata != null && isMetadataOverrideable())
        {
            loadFromXML(schema, metadata, errors);
        }
    }

    public String getSelectName()
    {
        return null;
    }


    public ColumnInfo getLookupColumn(ColumnInfo parent, String name)
    {
        ForeignKey fk = parent.getFk();
        if (fk == null)
            return null;
        return fk.createLookupColumn(parent, name);
    }

    public int getCacheSize()
    {
        return _cacheSize;
    }

    @Nullable
    public Domain getDomain()
    {
        return null;
    }

    @Nullable
    public DomainKind getDomainKind()
    {
        Domain domain = getDomain();
        if (domain != null)
            return domain.getDomainKind();
        return null;
    }

    @Nullable
    public QueryUpdateService getUpdateService()
    {
        // UNDONE: consider allowing all query tables to be updated via update service
        //if (getTableType() == TableInfo.TABLE_TYPE_TABLE)
        //    return new DefaultQueryUpdateService(this);
        return null;
    }

    @Override @NotNull
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public ContainerContext getContainerContext()
    {
        FieldKey fieldKey = getContainerFieldKey();
        if (fieldKey != null)
            return new ContainerContext.FieldKeyContext(fieldKey);

        return null;
    }

    /**
     * Return the FieldKey of the Container column for this table.
     * If the value is non-null then getContainerContext() will
     * return a FieldKeyContext using the container FieldKey.
     *
     * @return FieldKey of the Container column.
     */
    @Nullable
    public FieldKey getContainerFieldKey()
    {
        // UNDONE: Eventually this should default to 'if (getColumn("Container") != null) return getColumn("Container").getFieldKey();'
        return null;
    }

    public boolean hasTriggers(Container c)
    {
        try
        {
            return null != getTableScript(c);
        }
        catch (ScriptException x)
        {
            return true;
        }
    }


    boolean scriptLoaded = false;
    ScriptReference tableScript = null;

    protected ScriptReference getTableScript(Container c) throws ScriptException
    {
        if (!scriptLoaded)
        {
            ScriptReference script = loadScript(c);
            tableScript = script;
            scriptLoaded = true;
        }
        return tableScript;
    }


    private ScriptReference loadScript(Container c) throws ScriptException
    {
        ScriptService svc = ServiceRegistry.get().getService(ScriptService.class);
        assert svc != null;
        if (svc == null)
            return null;

        if (getPublicSchemaName() == null || getName() == null)
            return null;

        // Create legal path name
        Path pathNew = new Path(QueryService.MODULE_QUERIES_DIRECTORY,
                FileUtil.makeLegalName(getPublicSchemaName()),
                FileUtil.makeLegalName(getName()) + ".js");

        // For backwards compat with 10.2
        Path pathOld = new Path(QueryService.MODULE_QUERIES_DIRECTORY,
                getPublicSchemaName().replaceAll("\\W", "_"),
                getName().replaceAll("\\W", "_") + ".js");

        Path[] paths = pathNew.equals(pathOld) ? new Path[] {pathNew} : new Path[] { pathNew, pathOld };

        // UNDONE: get all table scripts instead of just first found
        Resource r = null;
        OUTER: for (Module m : c.getActiveModules())
        {
            for (Path p : paths)
            {
                r = m.getModuleResource(p);
                if (r != null && r.isFile())
                    break OUTER;
            }
        }
        if (r == null || !r.isFile())
            return null;

        return svc.compile(r);
    }


    protected <T> T invokeTableScript(Container c, Class<T> resultType, String methodName, Map<String, Object> extraContext, Object... args)
    {
        try
        {
            ScriptReference script = getTableScript(c);
            if (script == null)
                return null;

            if (!script.evaluated())
            {
                Map<String, Object> bindings = new HashMap<String, Object>();
                if (extraContext == null)
                    extraContext = new HashMap<String, Object>();
                bindings.put("extraContext", extraContext);
                bindings.put("schemaName", getPublicSchemaName());
                bindings.put("tableName", getPublicName());

                script.eval(bindings);
            }

            if (script.hasFn(methodName))
            {
                return script.invokeFn(resultType, methodName, args);
            }
        }
        catch (NoSuchMethodException e)
        {
            throw new UnexpectedException(e);
        }
        catch (ScriptException e)
        {
            throw new UnexpectedException(e);
        }

        return null;
    }

    private Object[] EMPTY_ARGS = new Object[0];

    @Override
    public void fireBatchTrigger(Container c, TriggerType type, boolean before, BatchValidationException batchErrors, Map<String, Object> extraContext)
            throws BatchValidationException
    {
        assert batchErrors != null;

        String triggerMethod = (before ? "init" : "complete");
        Boolean success = invokeTableScript(c, Boolean.class, triggerMethod, extraContext, type.name().toLowerCase(), batchErrors);
        if (success != null && !success.booleanValue())
            batchErrors.addRowError(new ValidationException(triggerMethod + " validation failed"));

        if (batchErrors.hasErrors())
            throw batchErrors;
    }

    @Override
    public void fireRowTrigger(Container c, TriggerType type, boolean before, int rowNumber,
                                  Map<String, Object> newRow, Map<String, Object> oldRow, Map<String, Object> extraContext)
            throws ValidationException
    {
        ValidationException errors = new ValidationException();
        errors.setSchemaName(getPublicSchemaName());
        errors.setQueryName(getName());
        errors.setRow(newRow);
        errors.setRowNumber(rowNumber);

        String triggerMethod = (before ? "before" : "after") + type.getMethodName();

        Object[] args = EMPTY_ARGS;
        if (before)
        {
            switch (type)
            {
                case SELECT: args = new Object[] { oldRow, errors };         break;
                case INSERT: args = new Object[] { newRow, errors };         break;
                case UPDATE: args = new Object[] { newRow, oldRow, errors }; break;
                case DELETE: args = new Object[] { oldRow, errors };         break;
            }
        }
        else
        {
            switch (type)
            {
                case SELECT: args = new Object[] { newRow, errors };         break;
                case INSERT: args = new Object[] { newRow, errors };         break;
                case UPDATE: args = new Object[] { newRow, oldRow, errors }; break;
                case DELETE: args = new Object[] { oldRow, errors };         break;
            }
        }
        Boolean success = invokeTableScript(c, Boolean.class, triggerMethod, extraContext, args);
        if (success != null && !success.booleanValue())
            errors.addGlobalError(triggerMethod + " validation failed");

        if (errors.hasErrors())
            throw errors;
    }


    /** TableInfo does not support DbCache by default */
    @Override
    public Path getNotificationKey()
    {
        return null;
    }

    @Override
    public String getDefaultDateFormat()
    {
        return null;
    }

    @Override
    public String getDefaultNumberFormat()
    {
        return null;
    }


    protected void checkLocked()
    {
        if (_locked)
            throw new IllegalStateException("TableInfo is locked: " + getName());
    }

    boolean _locked;

    @Override
    public void setLocked(boolean b)
    {
        if (_locked && !b)
            throw new IllegalStateException("Can't unlock table: " + getName());
        _locked = b;
        // set columns in the column list as locked, lookup columns created later are not locked
        for (ColumnInfo c : getColumns())
            c.setLocked(b);
    }

    @Override
    public boolean isLocked()
    {
        return _locked;
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return this instanceof ContainerFilterable;
    }

    @Override
    public String getImportMessage()
    {
        return _importMsg;
    }

    public void setImportMessage(String msg)
    {
        checkLocked();
        _importMsg = msg;
    }

    @Override
    public List<Pair<String, ActionURL>> getImportTemplates(Container c)
    {
        List<Pair<String, ActionURL>> templates = new ArrayList<Pair<String, ActionURL>>();
        if(_importTemplates != null)
        {
            for (Pair<String, DetailsURL> pair : _importTemplates)
            {
                templates.add(Pair.of(pair.first, pair.second.copy(c).getActionURL()));
            }
        }

        if (templates.size() == 0)
        {
            ActionURL url = PageFlowUtil.urlProvider(QueryUrls.class).urlCreateExcelTemplate(c, getPublicSchemaName(), getName());
            url.addParameter("captionType", ExcelWriter.CaptionType.Name.name());
            if(url != null)
                templates.add(Pair.of("Download Template", url));
        }

        return templates;
    }

    @Override
    public List<Pair<String, DetailsURL>> getRawImportTemplates()
    {
        return _importTemplates;
    }

    public void setImportTemplates(ImportTemplateType[] templates)
    {
        checkLocked();
        List<Pair<String, DetailsURL>> list = new ArrayList<Pair<String, DetailsURL>>();
        for (ImportTemplateType t : templates)
        {
            list.add(Pair.of(t.getLabel(), DetailsURL.fromString(t.getUrl()).fromString(t.getUrl())));
        }


        _importTemplates = list;
    }
}
