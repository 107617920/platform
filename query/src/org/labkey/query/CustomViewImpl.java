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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.queryCustomView.AggregateEnumType;
import org.labkey.data.xml.queryCustomView.AggregateType;
import org.labkey.data.xml.queryCustomView.AggregatesType;
import org.labkey.data.xml.queryCustomView.ColumnsType;
import org.labkey.data.xml.queryCustomView.ContainerFilterType;
import org.labkey.data.xml.queryCustomView.CustomViewDocument;
import org.labkey.data.xml.queryCustomView.CustomViewType;
import org.labkey.data.xml.queryCustomView.FilterType;
import org.labkey.data.xml.queryCustomView.FiltersType;
import org.labkey.data.xml.queryCustomView.OperatorType;
import org.labkey.data.xml.queryCustomView.PropertiesType;
import org.labkey.data.xml.queryCustomView.PropertyType;
import org.labkey.data.xml.queryCustomView.SortType;
import org.labkey.data.xml.queryCustomView.SortsType;
import org.labkey.query.design.DgColumn;
import org.labkey.query.design.DgCompare;
import org.labkey.query.design.DgOrderByString;
import org.labkey.query.design.DgQuery;
import org.labkey.query.design.ViewDocument;
import org.labkey.query.persist.CstmView;
import org.labkey.query.view.CustomViewSetKey;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class CustomViewImpl extends CustomViewInfoImpl implements CustomView
{
    private static final Logger _log = Logger.getLogger(CustomViewImpl.class);

    private final QueryDefinition _queryDef;
    private boolean _dirty;

    public CustomViewImpl(QueryDefinition queryDef, CstmView view)
    {
        super(view);
        _queryDef = queryDef;
        _dirty = false;
    }

    public CustomViewImpl(QueryDefinition queryDef, User user, String name)
    {
        super(new CstmView());
        _queryDef = queryDef;
        _dirty = true;
        _cstmView.setContainer(queryDef.getContainer().getId());
        _cstmView.setSchema(queryDef.getSchemaName());
        _cstmView.setQueryName(queryDef.getName());
        if (user != null)
        {
            _cstmView.setCustomViewOwner(user.getUserId());
        }
        else
        {
            _cstmView.setCustomViewOwner(null);
        }
        _cstmView.setName(name);
    }

    public void setContainer(Container container)
    {
        edit().setContainer(container.getId());
    }

    public QueryDefinition getQueryDefinition()
    {
        return _queryDef;
    }

    public void setName(String name)
    {
        edit().setName(name);
    }

    static String encodeProperties(List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> list)
    {
        StringBuilder ret = new StringBuilder();
        String strAnd = "";
        for (Map.Entry<FieldKey, Map<ColumnProperty, String>> entry : list)
        {
            ret.append(strAnd);
            ret.append(PageFlowUtil.encode(entry.getKey().toString()));
            if (!entry.getValue().isEmpty())
            {
                ret.append("=");
                ret.append(PageFlowUtil.encode(PageFlowUtil.toQueryString(entry.getValue().entrySet())));
            }
            strAnd = "&";
        }
        return ret.toString();
    }

    public void setColumnProperties(List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> map)
    {
        edit().setColumns(encodeProperties(map));
    }


    public void setColumns(List<FieldKey> columns)
    {
        edit().setColumns(StringUtils.join(columns.iterator(), "&"));
    }

    public boolean hasColumnList()
    {
        return StringUtils.trimToNull(_cstmView.getColumns()) != null;
    }

    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {
        if (!hasFilterOrSort())
            return;
        try
        {
            URLHelper src = new URLHelper(_cstmView.getFilter());
            for (String key : src.getKeysByPrefix(FILTER_PARAM_PREFIX + "."))
            {
                String newKey = dataRegionName + key.substring(FILTER_PARAM_PREFIX.length());
                for (String value : src.getParameters(key))
                {
                    url.addParameter(newKey, value);
                }
            }
        }
        catch (URISyntaxException use)
        {
            // do nothing
        }

    }

    public void setFilterAndSortFromURL(ActionURL url, String dataRegionName)
    {
        try
        {
            URLHelper dest = new URLHelper("");
            String[] keys = url.getKeysByPrefix(dataRegionName + ".");
            if (keys.length == 0)
            {
                edit().setFilter(null);
            }
            else
            {
                for (String key : keys)
                {
                    String newKey = FILTER_PARAM_PREFIX + key.substring(dataRegionName.length());
                    for (String value : url.getParameters(key))
                    {
                        dest.addParameter(newKey, value);
                    }
                }
                edit().setFilter(dest.toString());
            }
        }
        catch (URISyntaxException use)
        {
            throw UnexpectedException.wrap(use);
        }
    }

    public void setFilterAndSort(String filter)
    {
        edit().setFilter(filter);
    }

    public boolean saveInSession()
    {
        return getOwner() != null && (getOwner().isGuest() || isSession());
    }

    public void save(User user, HttpServletRequest request)
    {
        if (!_dirty)
            return;
        try
        {
            if (saveInSession())
            {
                _cstmView = CustomViewSetKey.saveCustomViewInSession(request, getQueryDefinition(), _cstmView);
            }
            else if (isNew())
            {
                _cstmView = _mgr.insert(user, _cstmView);
            }
            else
            {
                _cstmView = _mgr.update(user, _cstmView);
            }
            _mgr.fireViewChanged(this);
            _dirty = false;
        }
        catch (SQLException e)
        {
            throw new QueryException("Error", e);
        }
    }

    public void delete(User user, HttpServletRequest request) throws QueryException
    {
        try
        {
            if (saveInSession())
            {
                CustomViewSetKey.deleteCustomViewFromSession(request, getQueryDefinition(), getName());
            }
            else
            {
                if (isNew())
                    return;

                _mgr.delete(user, _cstmView);
                _mgr.fireViewDeleted(this);
                _cstmView = null;
            }
        }
        catch (SQLException e)
        {
            throw new QueryException("Error", e);
        }
    }

    public boolean serialize(VirtualFile dir) throws IOException
    {
        // Don't serialize session views
        if (isSession())
            return false;

        CustomViewDocument customViewDoc = CustomViewDocument.Factory.newInstance();
        CustomViewType customViewXml = customViewDoc.addNewCustomView();

        customViewXml.setName(getName());
        customViewXml.setSchema(getQueryDefinition().getSchemaName());
        customViewXml.setQuery(getQueryDefinition().getName());

        if (isHidden())
            customViewXml.setHidden(isHidden());

        // At the moment, CustomViewImpl always returns null.  If that changes, this will export it, though  it needs
        //  to be enabled on the import side.
        if (null != getCustomIconUrl())
            customViewXml.setCustomIconUrl(getCustomIconUrl());

        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> columns = getColumnProperties();

        if (!columns.isEmpty())
        {
            ColumnsType columnsXml = customViewXml.addNewColumns();

            for (Map.Entry<FieldKey, Map<ColumnProperty, String>> column : columns)
            {
                org.labkey.data.xml.queryCustomView.ColumnType columnXml = columnsXml.addNewColumn();

                columnXml.setName(column.getKey().toString());

                Map<ColumnProperty, String> props = column.getValue();

                if (!props.isEmpty())
                {
                    PropertiesType propsXml = columnXml.addNewProperties();

                    for (Map.Entry<ColumnProperty, String> propEntry : props.entrySet())
                    {
                        PropertyType propXml = propsXml.addNewProperty();
                        propXml.setName(propEntry.getKey().getXmlPropertyEnum());
                        propXml.setValue(propEntry.getValue());
                    }
                }
            }
        }

        try
        {
            FilterAndSort fas = FilterAndSort.fromString(_cstmView.getFilter());

            if (!fas.getFilter().isEmpty())
            {
                FiltersType filtersXml = customViewXml.addNewFilters();

                for (FilterInfo filter : fas.getFilter())
                {
                    FilterType filterXml = filtersXml.addNewFilter();

                    filterXml.setColumn(filter.getField().toString());

                    if (null != filter.getOp())
                    {
                        OperatorType.Enum opType = OperatorType.Enum.forString(filter.getOp().getPreferredUrlKey());

                        if (null != opType)
                        {
                            filterXml.setOperator(opType);
                            filterXml.setValue(filter.getValue());                            
                        }
                    }
                }
            }

            if (!fas.getSort().isEmpty())
            {
                SortsType sortsXml = customViewXml.addNewSorts();

                for (Sort.SortField sort : fas.getSort())
                {
                    SortType sortXml = sortsXml.addNewSort();

                    sortXml.setColumn(sort.getColumnName());
                    sortXml.setDescending(sort.getSortDirection() == Sort.SortDirection.DESC);
                }
            }

            if (!fas.getAggregates().isEmpty())
            {
                AggregatesType aggsXml = customViewXml.addNewAggregates();

                for (Aggregate agg : fas.getAggregates())
                {
                    AggregateEnumType.Enum aggEnum = AggregateEnumType.Enum.forString(agg.getType().name());
                    if (aggEnum != null)
                    {
                        AggregateType aggXml = aggsXml.addNewAggregate();
                        aggXml.setColumn(agg.getColumnName());
                        aggXml.setType(aggEnum);
                    }
                }
            }

            if (fas.getContainerFilterNames() != null && fas.getContainerFilterNames().length > 0)
            {
                String containerFilter = fas.getContainerFilterNames()[0];
                ContainerFilterType.Enum containerFilterType = ContainerFilterType.Enum.forString(containerFilter);
                if (containerFilterType != null)
                    customViewXml.setContainerFilter(containerFilterType);
            }
        }
        catch (URISyntaxException e)
        {
            _log.error("Bad filter/sort URL in custom view: " + _cstmView.getFilter());
        }

        String filename = (null != getName() ? getName() : "default") + ".db_" + getCstmView().getCustomViewId() + CustomViewXmlReader.XML_FILE_EXTENSION;

        dir.saveXmlBean(filename, customViewDoc);
        return true;
    }

    public boolean isNew()
    {
        return _cstmView.getCustomViewId() == 0;
    }

    public void setCanInherit(boolean f)
    {
        edit().setFlags(_mgr.setCanInherit(_cstmView.getFlags(), f));
    }

    @Override
    public boolean canEdit(Container c, org.springframework.validation.Errors errors)
    {
        if (!isEditable())
        {
            if (errors != null)
                errors.reject(null, "The view '" + (getName() == null ? "<default>" : getName()) + "' is read-only and cannot be edited.");
            return false;
        }
        return true;
    }

    public void setIsHidden(boolean b)
    {
        edit().setFlags(_mgr.setIsHidden(_cstmView.getFlags(), b));
    }

    public Map<String, Object> toMap()
    {
        return CustomViewUtil.toMap(this, null, true);
    }

    public void update(org.json.JSONObject jsonView, boolean saveFilterAndSort)
    {
        CustomViewUtil.update(this, jsonView, saveFilterAndSort);
    }

/*
    public ViewDocument getDesignDocument(UserSchema schema)
    {
        ViewDocument ret = ViewDocument.Factory.newInstance();
        DgQuery view = ret.addNewView();
        DgQuery.Select select = view.addNewSelect();
        DgQuery.From from = view.addNewFrom();
        TableInfo tinfo = getQueryDefinition().getTable(schema, null, true);
        if (tinfo == null)
        {
            return null;
        }
        DgTable dgTable = from.addNewTable();
        TableXML.initTable(dgTable.addNewMetadata(), tinfo, null);

        DgQuery.Where where = view.addNewWhere();
        DgQuery.OrderBy orderBy = view.addNewOrderBy();
        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> columns = getColumnProperties();
        if (columns.size() == 0)
        {
            columns = new ArrayList<Map.Entry<FieldKey, Map<ColumnProperty, String>>>();
            for (FieldKey key : tinfo.getDefaultVisibleColumns())
            {
                columns.add(Pair.of(key, Collections.<ColumnProperty, String>emptyMap()));
            }
        }
        Set<FieldKey> allKeys = new HashSet<FieldKey>();
        for (Map.Entry<FieldKey, Map<ColumnProperty, String>> entry : columns)
        {
            DgColumn column = select.addNewColumn();
            DgValue value = column.addNewValue();
            allKeys.add(entry.getKey());
            value.addNewField().setStringValue(entry.getKey().toString());
            if (!entry.getValue().isEmpty())
            {
                ColumnType metadata = column.addNewMetadata();
                String columnTitle = entry.getValue().get(ColumnProperty.columnTitle);
                if (columnTitle != null)
                {
                    metadata.setColumnTitle(columnTitle);
                }
            }
        }

        String strFilter = _cstmView.getFilter();
        if (strFilter != null)
        {
            try
            {
                URLHelper filterSort = new URLHelper(strFilter);
                for (String key : filterSort.getKeysByPrefix(FILTER_PARAM_PREFIX + "."))
                {
                    String param = key.substring(FILTER_PARAM_PREFIX.length() + 1);
                    String[] parts = StringUtils.splitPreserveAllTokens(param, '~');
                    if (parts.length != 2)
                        continue;
                    for (String value : filterSort.getParameters(key))
                    {
                        DgCompare compare = where.addNewCompare();
                        allKeys.add(FieldKey.fromString(parts[0]));
                        compare.setField(parts[0]);
                        compare.setOp(parts[1]);
                        compare.setLiteral(value);
                    }
                }
                Sort sort = new Sort(filterSort, FILTER_PARAM_PREFIX);
                for (Sort.SortField sf : sort.getSortList())
                {
                    DgOrderByString ob = orderBy.addNewField();
                    ob.setDir(sf.getSortDirection().toString());
                    ob.setStringValue(sf.getColumnName());
                    allKeys.add(FieldKey.fromString(sf.getColumnName()));
                }
                String[] containerFilterNames = filterSort.getParameters(FILTER_PARAM_PREFIX + "." + CONTAINER_FILTER_NAME);
                if (containerFilterNames.length > 0)
                    view.setContainerFilterName(containerFilterNames[containerFilterNames.length - 1]);
            }
            catch (URISyntaxException use)
            {
                _log.error("Error", use);
            }
        }
        DgTable tableOutput = from.addNewTable();
        tableOutput.setAlias("output");
        TableXML.initTable(tableOutput.addNewMetadata(), tinfo, null, getColumnInfos(tinfo, allKeys).values());
        return ret;
    }
*/

    static public Map<FieldKey, ColumnInfo> getColumnInfos(TableInfo table, Collection<FieldKey> fields)
    {
        Map<FieldKey, ColumnInfo> ret = QueryService.get().getColumns(table, fields);

        if (ret.size() != fields.size())
        {
            for (FieldKey field : fields)
            {
                if (ret.containsKey(field))
                {
                    continue;
                }
                ColumnInfo column = new ColumnInfo(field);
                column.setLabel(field.getDisplayString() + " (not found)");
                ret.put(field, column);
            }
        }

        return ret;
    }

    public void update(ViewDocument doc, boolean saveFilterAndSort)
    {
        DgQuery view = doc.getView();
        DgQuery.Select select = view.getSelect();

        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> fields = new ArrayList<Map.Entry<FieldKey, Map<ColumnProperty, String>>>();

        for (DgColumn column : select.getColumnArray())
        {
            FieldKey key = FieldKey.fromString(column.getValue().getField().getStringValue());
            Map<ColumnProperty, String> map = Collections.emptyMap();
            if (column.getMetadata() != null)
            {
                ColumnType metadata = column.getMetadata();
                map = new EnumMap<ColumnProperty,String>(ColumnProperty.class);
                if (metadata.getColumnTitle() != null)
                {
                    map.put(ColumnProperty.columnTitle, metadata.getColumnTitle());
                }
            }
            fields.add(Pair.of(key, map));
        }

        setColumnProperties(fields);
        if (!saveFilterAndSort)
            return;

        DgQuery.Where where = view.getWhere();
        DgQuery.OrderBy orderBy = view.getOrderBy();
        ActionURL url = new ActionURL();

        for (DgCompare compare : where.getCompareArray())
        {
            String op = compare.getOp();
            if (op == null)
                op = "";

            String value = compare.getLiteral();
            if (value == null)
            {
                value = "";
            }

            url.addParameter(FILTER_PARAM_PREFIX + "." + compare.getField() + "~" + op, value);
        }

        StringBuilder sort = new StringBuilder();
        String strComma = "";

        for (DgOrderByString obs : orderBy.getFieldArray())
        {
            sort.append(strComma);
            strComma = ",";
            if ("DESC".equals(obs.getDir()))
            {
                sort.append("-");
            }
            sort.append(obs.getStringValue());
        }

        if (sort.length() != 0)
        {
            url.addParameter(FILTER_PARAM_PREFIX + ".sort", sort.toString());
        }

        if (view.isSetContainerFilterName())
            url.addParameter(FILTER_PARAM_PREFIX + "." + CONTAINER_FILTER_NAME, view.getContainerFilterName());

        setFilterAndSortFromURL(url, FILTER_PARAM_PREFIX);
    }

    protected CstmView edit()
    {
        if (_dirty)
        {
            return _cstmView;
        }
        _cstmView = _cstmView.clone();
        _dirty = true;
        return _cstmView;
    }
}
