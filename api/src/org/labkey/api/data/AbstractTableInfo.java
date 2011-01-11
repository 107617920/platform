/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.module.Module;
import org.labkey.api.query.*;
import org.labkey.api.resource.Resource;
import org.labkey.api.script.ScriptReference;
import org.labkey.api.script.ScriptService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;

import javax.script.ScriptException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

abstract public class AbstractTableInfo implements TableInfo, ContainerContext
{
    protected Iterable<FieldKey> _defaultVisibleColumns;
    protected DbSchema _schema;
    private String _titleColumn;
    private int _cacheSize = DbCache.DEFAULT_CACHE_SIZE;

    protected final Map<String, ColumnInfo> _columnMap;
    private Map<String, MethodInfo> _methodMap;
    protected String _name;
    protected String _description;

    protected DetailsURL _gridURL;
    protected DetailsURL _insertURL;
    protected DetailsURL _updateURL;
    protected ButtonBarConfig _buttonBarConfig;
    private List<DetailsURL> _detailsURLs = new ArrayList<DetailsURL>(1);

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
         if (hasContainerContext())
         {
             for (ColumnInfo c : getColumns())
             {
                 if (c.getURL() instanceof DetailsURL)
                     ((DetailsURL)c.getURL()).setContainer(this);
             }
             for (DetailsURL detailsURL : _detailsURLs)
             {
                 detailsURL.setContainer(this);
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

    public int getTableType()
    {
        return TABLE_TYPE_NOT_IN_DB;
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

    public String getSequence()
    {
        return null;
    }

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
        _titleColumn = titleColumn;
    }

    public ColumnInfo getColumn(String name)
    {
        ColumnInfo ret = _columnMap.get(name);
        if (ret != null)
            return ret;
        return resolveColumn(name);
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
        _description = description;
    }

    public boolean removeColumn(ColumnInfo column)
    {
        return _columnMap.remove(column.getName()) != null;
    }

    public ColumnInfo addColumn(ColumnInfo column)
    {
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
        if (_insertURL != null)
        {
            return _insertURL.copy(container).getActionURL();
        }
        return null;
    }

    public StringExpression getUpdateURL(Set<FieldKey> columns, Container container)
    {
        if (_updateURL != null && _updateURL.validateFieldKeys(columns))
        {
            return _updateURL.copy(container);
        }
        return null;
    }

    public StringExpression getDetailsURL(Set<FieldKey> columns, Container container)
    {
        for (DetailsURL dUrl : _detailsURLs)
        {
            if (dUrl.validateFieldKeys(columns))
                return dUrl.copy(container);
        }
        return null;
    }

    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        return false;
    }

    public void setDetailsURL(DetailsURL detailsURL)
    {
        _detailsURLs.clear();
        _detailsURLs.add(detailsURL);
    }

    public void addDetailsURL(DetailsURL detailsURL)
    {
        _detailsURLs.add(detailsURL);
    }

    public void setGridURL(DetailsURL gridURL)
    {
        _gridURL = gridURL;
    }

    public void setInsertURL(DetailsURL insertURL)
    {
        _insertURL = insertURL;
    }
    
    public void setUpdateURL(DetailsURL updateURL)
    {
        _updateURL = updateURL;
    }

    public ButtonBarConfig getButtonBarConfig()
    {
        return _buttonBarConfig;
    }

    public void setDefaultVisibleColumns(Iterable<FieldKey> list)
    {
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
        column.loadFromXml(xbColumn, true);
        
        if (xbColumn.getFk() != null)
        {
            ForeignKey qfk = makeForeignKey(schema, xbColumn.getFk());
            if (qfk == null)
            {
                //noinspection ThrowableInstanceNeverThrown
                qpe.add(new MetadataException("Schema " + xbColumn.getFk().getFkDbSchema() + " not found."));
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
        if (xmlTable.getTitleColumn() != null)
            setTitleColumn(xmlTable.getTitleColumn());
        if (xmlTable.getDescription() != null)
            setDescription(xmlTable.getDescription());
        if (xmlTable.getGridUrl() != null)
            _gridURL = parseDetailsURL(schema.getContainer(), xmlTable.getGridUrl(), errors);
        if (xmlTable.getInsertUrl() != null)
            _insertURL = parseDetailsURL(schema.getContainer(), xmlTable.getInsertUrl(), errors);
        if (xmlTable.getUpdateUrl() != null)
            _updateURL = parseDetailsURL(schema.getContainer(), xmlTable.getUpdateUrl(), errors);
        if (xmlTable.getTableUrl() != null)
            setDetailsURL(parseDetailsURL(schema.getContainer(), xmlTable.getTableUrl(), errors));

        if (xmlTable.isSetCacheSize())
            _cacheSize = xmlTable.getCacheSize();

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
        if (isMetadataOverrideable())
        {
            String metadataXML = QueryService.get().findMetadataOverride(schema.getContainer(), schema.getSchemaName(), tableName, false);

            // Only bother parsing if there's some actual content, otherwise skip the override completely
            if (metadataXML != null && StringUtils.isNotBlank(metadataXML))
            {
                XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
                List<XmlError> xmlErrors = new ArrayList<XmlError>();
                options.setErrorListener(xmlErrors);
                try
                {
                    TablesDocument doc = TablesDocument.Factory.parse(metadataXML, options);
                    TablesDocument.Tables tables = doc.getTables();
                    if (tables != null && tables.sizeOfTableArray() > 0)
                        loadFromXML(schema, tables.getTableArray(0), errors);
                }
                catch (XmlException e)
                {
                    errors.add(new MetadataException(XmlBeansUtil.getErrorMessage(e)));
                }
                for (XmlError xmle : xmlErrors)
                {
                    errors.add(new MetadataException(XmlBeansUtil.getErrorMessage(xmle)));
                }
            }
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

    @Override
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        return Collections.EMPTY_LIST;
    }

    /**
     * return true if all rows from this table come from a single container.
     * if true, getContainer(Map) must return non-null value
     */
    public boolean hasContainerContext()
    {
        return false;
    }

    public Container getContainer(Map context)
    {
        return null;
    }


    ScriptReference tableScript;

    protected ScriptReference getTableScript(Container c) throws ScriptException
    {
        if (tableScript != null)
            return tableScript;

        ScriptService svc = ServiceRegistry.get().getService(ScriptService.class);
        assert svc != null;
        if (svc == null)
            return null;

        if (getPublicSchemaName() == null || getName() == null)
            return null;

        // Create legal path name
        Path pathNew = new Path("queries",
                FileUtil.makeLegalName(getPublicSchemaName()),
                FileUtil.makeLegalName(getName()) + ".js");

        // For backwards compat with 10.2
        Path pathOld = new Path("queries",
                getPublicSchemaName().replaceAll("\\W", "_"),
                getName().replaceAll("\\W", "_") + ".js");

        Path[] paths = new Path[] { pathNew, pathOld };

        // UNDONE: get all table scripts instead of just first found
        Resource r = null;
        OUTER: for (Module m : c.getActiveModules())
        {
            for (Path p : paths)
            {
                r = m.getModuleResource(p);
                if (r != null)
                    break OUTER;
            }
        }
        if (r == null)
            return null;
        
        tableScript = svc.compile(r);
        return tableScript;
    }

    protected <T> T invokeTableScript(Container c, Class<T> resultType, String methodName, Object... args)
    {
        try
        {
            ScriptReference script = getTableScript(c);
            if (script == null)
                return null;

            final Logger scriptLogger = Logger.getLogger(ScriptService.Console.class);

            if (scriptLogger.isEnabledFor(Level.DEBUG))
            {
                script.getContext().setWriter(new PrintWriter(new Writer(){
                    @Override
                    public void write(String str) throws IOException
                    {
                        scriptLogger.debug(str);
                    }

                    @Override
                    public void write(char[] cbuf, int off, int len) throws IOException
                    {
                        scriptLogger.debug(new String(cbuf, off, len));
                    }

                    @Override
                    public void flush() throws IOException
                    {
                    }

                    @Override
                    public void close() throws IOException
                    {
                    }
                }));
            }

            if (script.hasFn(methodName))
                return script.invokeFn(resultType, methodName, args);
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
    public void fireBatchTrigger(Container c, TriggerType type, boolean before, ValidationException errors)
            throws ValidationException
    {
        assert errors != null;
        List<Map<String, Object>> list = errors.toList();

        String triggerMethod = (before ? "init" : "complete");
        Boolean success = invokeTableScript(c, Boolean.class, triggerMethod, type.name().toLowerCase(), list);

        errors = ValidationException.fromList(list);
        if (success != null && !success.booleanValue())
                errors.addError(new SimpleValidationError(triggerMethod + " validation failed"));

        if (errors.hasErrors())
            throw errors;
    }

    @Override
    public void fireRowTrigger(Container c, TriggerType type, boolean before, int rowNumber,
                                  Map<String, Object> newRow, Map<String, Object> oldRow)
            throws ValidationException
    {
        Map<String, Object> errors = new LinkedHashMap<String, Object>();
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
        Boolean success = invokeTableScript(c, Boolean.class, triggerMethod, args);
        if (success != null && !success.booleanValue())
            errors.put(null, triggerMethod + " validation failed");

        if (!errors.isEmpty())
            throw new ValidationException(errors, rowNumber);
    }

}
