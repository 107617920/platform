/*
 * Copyright (c) 2010-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.filecontent;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.webdav.FileSystemResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.writer.ContainerUser;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: May 17, 2010
 * Time: 1:38:56 PM
 */
public class FileQueryUpdateService extends AbstractQueryUpdateService
{
    private static final Logger _log = Logger.getLogger(FileQueryUpdateService.class);
    private Container _container;
    private Set<String> _columns;
    private Domain _domain;

    public static final String KEY_COL_ID = "id";
    public static final String KEY_COL_DAV = "davUrl";
    public static final String KEY_COL_FILE = "filePath";
    public static final String COL_COMMENT = "Flag/Comment";


    public FileQueryUpdateService(TableInfo queryTable, Container container)
    {
        super(queryTable);
        _container = container;
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Filter filter = getQueryFilter(container, keys);
        Set<String> queryColumns = getQueryColumns(container);

        Map<String, Object>[] rows = new TableSelector(getQueryTable(), queryColumns, filter, null).getMapArray();
        Map<String, Object> rowMap = new HashMap<>();

        if (rows.length > 0)
        {
            Map<String, Object> selectMap = rows[0];

            if (rows.length > 1)
                _log.error("More than one row returned for data file: " + filter.toSQLString(getQueryTable().getSqlDialect()));

            Domain domain = getFileProperties(container);

            if (domain != null)
            {
                for (DomainProperty prop : domain.getProperties())
                {
                    Object o = selectMap.get(prop.getName());
                    String urlValue = getUrlValue(prop, selectMap);

                    if (o != null)
                    {
                        try
                        {
                            String fmt = DomainUtil.getFormattedDefaultValue(user, prop, o);
                            
                            if (!rowMap.containsKey(prop.getName() + "_displayValue"))
                                rowMap.put(prop.getName() + "_displayValue", fmt);

                            if (o instanceof Date)
                                rowMap.put(prop.getName(), fmt);
                            else
                                rowMap.put(prop.getName(), o);

                            if (urlValue != null)
                                rowMap.put(FileSystemResource.URL_COL_PREFIX + prop.getName(), urlValue);
                        }
                        catch (Exception e)
                        {
                            throw new QueryUpdateServiceException(e);
                        }
                    }
                }
            }

            // gather any other standard columns for this QUS 
            for (String colName : queryColumns)
            {
                if (!rowMap.containsKey(colName))
                {
                    rowMap.put(colName, selectMap.get(colName));
                }
            }
        }
        return rowMap;
    }

    private String getUrlValue(DomainProperty prop, Map<String, Object> selectMap)
    {
        String urlValue = null;
        ColumnInfo col = getQueryTable().getColumn(prop.getName());

        if (col != null && col.getDisplayColumnFactory() != null)
        {
            DisplayColumn dc = col.getDisplayColumnFactory().createRenderer(col);
            if (dc != null)
            {
                StringExpression url = dc.getURLExpression();
                if (url != null)
                    urlValue = url.eval(selectMap);
            }
        }
        return urlValue;
    }

    IntegerConverter _converter = new IntegerConverter();

    private Filter getQueryFilter(Container container, Map<String, Object> keys) throws QueryUpdateServiceException
    {
        SimpleFilter filter;

        if (keys.containsKey(ExpDataTable.Column.RowId.name()))
        {
            Object o = keys.get(ExpDataTable.Column.RowId.name());
            filter = new SimpleFilter(FieldKey.fromParts(ExpDataTable.Column.RowId.name()), _converter.convert(Integer.class, o));
        }
        else if (keys.containsKey(ExpDataTable.Column.LSID.name()))
        {
            Object o = keys.get(ExpDataTable.Column.LSID.name());
            filter = new SimpleFilter(FieldKey.fromParts(ExpDataTable.Column.LSID.name()), o);
        }
        else if (keys.containsKey(ExpDataTable.Column.DataFileUrl.name()))
        {
            Object o = keys.get(ExpDataTable.Column. DataFileUrl.name());
            filter = new SimpleFilter(FieldKey.fromParts("DataFileUrl"), o);
        }
        else
            throw new QueryUpdateServiceException("Either RowId, LSID, or DataFileURL is required to get ExpData.");

        // Just look in the current container
        filter.addCondition(FieldKey.fromParts(ExpDataTable.Column.Folder.name()), container.getId());

        return filter;
    }

    private Set<String> getQueryColumns(Container c)
    {
        if (_columns == null)
        {
            _columns = new HashSet<>();

            _columns.add(ExpDataTable.Column.Flag.name());
            _columns.add(COL_COMMENT);
            _columns.add(ExpDataTable.Column.DataFileUrl.name());

            Domain domain = getFileProperties(c);
            if (domain != null)
            {
                for (DomainProperty prop : domain.getProperties())
                    _columns.add(prop.getName());
            }
        }
        return _columns;
    }

    private Domain getFileProperties(Container container)
    {
        if (_domain == null)
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            String uri = svc.getDomainURI(container);
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, container);

            if (dd != null)
                _domain = PropertyService.get().getDomain(dd.getDomainId());
        }
        return _domain;
    }

    @Override
    protected boolean hasPermission(User user, Class<? extends Permission> acl)
    {
        return _container.hasPermission(user, acl);
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        return _setRow(user, container, row, false);
    }

    /** @return Pair of the data object (if found), and the requested dataFileUrl (if specified in the row) */
    private Pair<ExpData, String> findData(Container container, Map<String, Object> row) throws ValidationException
    {
        String dataFileUrl = null;

        if (!row.containsKey(ExpDataTable.Column.DataFileUrl.name()))
        {
            try
            {
                Filter filter = getQueryFilter(container, row);
                Map<String, Object>[] rows = new TableSelector(getQueryTable(), getQueryColumns(container), filter, null).getMapArray();

                if (rows.length > 0)
                {
                    Map<String, Object> rowMap = rows[0];

                    if (rows.length > 1)
                        _log.error("More than one row returned for data file: " + filter);

                    if (rowMap != null)
                        dataFileUrl = String.valueOf(rowMap.get(ExpDataTable.Column.DataFileUrl.name()));
                }
            }
            catch (Exception e)
            {
                throw new ValidationException("Unable to get the DataFileUrl: " + e.getMessage());
            }
        }
        else
            dataFileUrl = String.valueOf(row.get(ExpDataTable.Column.DataFileUrl.name()));

        ExpData data = ExperimentService.get().getExpDataByURL(dataFileUrl, container);
        return new Pair<>(data, dataFileUrl);
    }

    private Map<String, Object> _setRow(final User user, final Container container, Map<String, Object> row, boolean isUpdate) throws ValidationException
    {

        WebdavResource resource = davResourceFromKeys(row);

        Pair<ExpData, String> p = findData(container, row);
        ExpData data = p.getKey();
        String dataFileUrl = p.getValue();


        if (resource != null)
        {
            // issue 12820 : ensure that a data object exists so we can hang custom file properties off of it, this might
            // happen is if a file root were moved after files were uploaded, in the future we need to ensure the
            // exp.datas table gets updated when automatic moves happen, but for manually moved roots we will always need
            // to do this.

            if (data == null)
            {
                _log.warn("Unable to locate the ExpData object for : " + dataFileUrl + " one has been automatically created.");

                data = ExperimentService.get().createData(container, new DataType("UploadedFile"));
                data.setName(resource.getName());
                URI uri = null;
                try
                {
                    uri = dataFileUrl == null ? null : new URI(dataFileUrl);
                }
                catch (URISyntaxException ignored) {}
                data.setDataFileURI(uri);
                data.save(user);
            }

            // Get the Flag/Comment field
            if (row.containsKey(COL_COMMENT))
            {
                String comment = String.valueOf(row.get(COL_COMMENT));
                data.setComment(user, comment);
            }

            Domain domain = getFileProperties(container);

            if (domain != null)
            {
                StringBuilder sb = new StringBuilder("annotations updated: ");
                String delim = "";

                for (DomainProperty prop : domain.getProperties())
                {
                    if (row.containsKey(prop.getName()))
                    {
                        Object value = row.get(prop.getName());

                        data.setProperty(user, prop.getPropertyDescriptor(), value);

                        // format the value so we resolve lookups values
                        String displayValue = DomainUtil.getFormattedDefaultValue(user, prop, value);
                        sb.append(delim).append(prop.getLabel() != null ? prop.getLabel() : prop.getName()).append('=').append(displayValue);
                        delim = ",";
                    }
                }
                SearchService ss = ServiceRegistry.get().getService(SearchService.class);

                if (null != ss)
                    ss.defaultTask().addResource(resource, SearchService.PRIORITY.item);

                resource.notify(new ContainerUser(){
                    public User getUser(){
                        return user;
                    }
                    public Container getContainer(){
                        return container;
                    }
                }, sb.toString());
            }
            return row;
        }
        return null;
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        return _setRow(user, container, row, true);
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        ExpData data = null;
        if (oldRow.get("RowId") != null)
        {
            int rowId = ((Integer)ConvertUtils.convert(oldRow.get("RowId").toString(), Integer.class)).intValue();
            data = ExperimentService.get().getExpData(rowId);
        }
        if (data == null)
        {
            try
            {
                data = findData(container, oldRow).getKey();
            }
            catch (ValidationException e)
            {
                throw new QueryUpdateServiceException(e);
            }
        }
        if (data == null)
        {
            throw new InvalidKeyException("No matching data rows found");
        }
        if (!data.getContainer().hasPermission(user, DeletePermission.class))
        {
            throw new UnauthorizedException("You do not have permission to delete from " + data.getContainer().getPath());
        }
        data.delete(user);
        
        return oldRow;
    }

    private WebdavResource davResourceFromKeys(Map<String, Object> keys)
    {
        if (keys.containsKey(KEY_COL_DAV))
        {
            return getResource(String.valueOf(keys.get(KEY_COL_DAV)));
        }
        else if (keys.containsKey(KEY_COL_ID))
        {
            return getResource(String.valueOf(keys.get(KEY_COL_ID)));
        }
        return null;
    }

    private WebdavResource getResource(String uri)
    {
        Path path = Path.decode(uri);

        if (!path.startsWith(WebdavService.getPath()) && path.contains(WebdavService.getPath().getName()))
        {
            String newPath = path.toString();
            int idx = newPath.indexOf(WebdavService.getPath().toString());

            if (idx != -1)
            {
                newPath = newPath.substring(idx);
                path = Path.parse(newPath);
            }
        }
        return WebdavService.get().getResolver().lookup(path);
    }
}
