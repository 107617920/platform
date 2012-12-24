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

package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.util.HString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class QuerySettings
{
    public static final String URL_PARAMETER_PREFIX = "param.";
    private String _schemaName;
    private String _queryName;
    private String _viewName;
    private String _dataRegionName;
    private List<FieldKey> _fieldKeys;
    private ReportIdentifier _reportId;
    private boolean _allowChooseQuery = false;
    private boolean _allowChooseView = true;
    private boolean _allowCustomizeView = true;
    private boolean _ignoreUserFilter;
    private int _maxRows = 100;
    private long _offset = 0;
    private String _selectionKey = null;

    private ShowRows _showRows = ShowRows.PAGINATED;
    private boolean _showHiddenFieldsWhenCustomizing = false;

    PropertyValues _filterSort = null;
    private URLHelper _returnURL = null;

    private String _containerFilterName;
    private List<Aggregate> _aggregates = new ArrayList<Aggregate>();

    private SimpleFilter _baseFilter;
    private Sort _baseSort;
    private QueryDefinition _queryDef;

    protected QuerySettings(String dataRegionName)
    {
        _dataRegionName = dataRegionName;
    }

    /**
     * Init the QuerySettings using all the request parameters, from context.getPropertyValues().
     * @see UserSchema#getSettings(org.labkey.api.view.ViewContext, String)
     */
    public QuerySettings(ViewContext context, String dataRegionName)
    {
        _dataRegionName = dataRegionName;
        init(getPropertyValues(context));
    }


    /**
     * Init the QuerySettings using all the request parameters, from context.getPropertyValues().
     * @see UserSchema#getSettings(org.labkey.api.view.ViewContext, String, String)
     */
    public QuerySettings(ViewContext context, String dataRegionName, String queryName)
    {
        _dataRegionName = dataRegionName;
        init(context);
        setQueryName(queryName);
    }


    /**
     * @param params    all parameters from URL or POST, inluding dataregion.filter parameters
     * @param dataRegionName    prefix for filter params etc
     * @see UserSchema#getSettings(org.springframework.beans.PropertyValues, String) 
     */
    public QuerySettings(PropertyValues params, String dataRegionName)
    {
        _dataRegionName = dataRegionName;
        init(params);
    }


    private PropertyValues getPropertyValues(ViewContext context)
    {
        PropertyValues pvs = context.getBindPropertyValues();
        if (null == pvs)
        {
            Logger.getLogger(QuerySettings.class).warn("PropertyValues not set");
            pvs = context.getActionURL().getPropertyValues();
        }
        return pvs;
    }



    /**
     * @param url parameters for filter/sort
     */
    public void setSortFilterURL(ActionURL url)
    {
        setSortFilter(url.getPropertyValues());
    }


    public void setSortFilter(PropertyValues pvs)
    {
        _filterSort = pvs;
        String showRowsParam = _getParameter(param(QueryParam.showRows));
        if (showRowsParam != null)
        {
            try
            {
                _showRows = ShowRows.valueOf(showRowsParam.toUpperCase());
            }
            catch (IllegalArgumentException ex)
            {
                _showRows = ShowRows.PAGINATED;
            }
        }
    }

    public void setAggregates(PropertyValues pvs)
    {
        _aggregates.addAll(Aggregate.fromURL(pvs, getDataRegionName()));
    }

    public void addAggregates(Aggregate... aggregates)
    {
        _aggregates.addAll(Arrays.asList(aggregates));
    }


    protected String _getParameter(String param)
    {
        PropertyValue pv = _filterSort.getPropertyValue(param);
        if (pv == null)
            return null;
        Object v = pv.getValue();
        if (v == null)
            return null;
        if (v.getClass().isArray())
        {
            Object[] a = (Object[])v;
            v = a.length == 0 ? null : a[0];
        }
        return v == null ? null : String.valueOf(v);
    }

    public void init(ViewContext context)
    {
        init(getPropertyValues(context));    
    }


    /**
     * Initialize QuerySettings from the PropertyValues, binds all fields that are supported on the URL
     *. such as viewName.  Use setSortFilter() to provide sort filter parameters w/o affecting the other
     * properties.
     */
    public void init(PropertyValues pvs)
    {
        if (null == pvs)
            pvs = new MutablePropertyValues();
        setSortFilter(pvs);
        setAggregates(pvs);

        // Let URL parameter control which query we show, even if we don't show the Query drop-down menu to let the user choose
        String param = param(QueryParam.queryName);
        String queryName = StringUtils.trimToNull(_getParameter(param));
        if (queryName != null)
        {
            setQueryName(queryName);
        }

        if (getAllowChooseView())
        {
            String viewName = StringUtils.trimToNull(_getParameter(param(QueryParam.viewName)));
            if (viewName != null)
            {
                setViewName(viewName);
            }
            if (_getParameter(param(QueryParam.ignoreFilter)) != null)
            {
                _ignoreUserFilter = true;
            }

            setReportId(ReportService.get().getReportIdentifier(_getParameter(param(QueryParam.reportId))));
        }

        // Ignore maxRows and offset parameters when not PAGINATED.
        if (_showRows == ShowRows.PAGINATED)
        {
            String offsetParam = _getParameter(param(QueryParam.offset));
            if (offsetParam != null)
            {
                try
                {
                    long offset = Long.parseLong(offsetParam);
                    if (offset > 0)
                        _offset = offset;
                }
                catch (NumberFormatException e) { }
            }

            String maxRowsParam = _getParameter(param(QueryParam.maxRows));
            if (maxRowsParam != null)
            {
                try
                {
                    int maxRows = Integer.parseInt(maxRowsParam);
                    assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for maxRows; should be positive, Table.ALL_ROWS or Table.NO_ROWS";
                    if (maxRows >= 0)
                        _maxRows = maxRows;
                    if (_maxRows == Table.NO_ROWS)
                        _showRows = ShowRows.NONE;
                }
                catch (NumberFormatException e) { }
            }
        }

        String containerFilterNameParam = _getParameter(param(QueryParam.containerFilterName));
        if (containerFilterNameParam != null)
            setContainerFilterName(containerFilterNameParam);

        String returnURL = _getParameter(ActionURL.Param.returnUrl.name());
        if (returnURL == null)
            returnURL = _getParameter("returnURL");
        if (returnURL == null)
            returnURL = _getParameter(QueryParam.srcURL.toString());
        if (returnURL != null)
        {
            try
            {
                setReturnUrl(new URLHelper(returnURL));
            }
            catch (URISyntaxException _) { }
        }

        String columns = StringUtils.trimToNull(_getParameter(param(QueryParam.columns)));
        if (null != columns)
        {
            String[] colArray = columns.split(",");
            _fieldKeys = new ArrayList<FieldKey>();
            for (String key : colArray)
            {
                if (!(StringUtils.isEmpty(key)))
                {
                    _fieldKeys.add(FieldKey.fromString(StringUtils.trim(key)));

                }
            }
        }

        String selectionKey = StringUtils.trimToNull(_getParameter(param(QueryParam.selectionKey)));
        if (null != selectionKey)
            setSelectionKey(selectionKey);

        _parseQueryParameters(_filterSort);
    }


    Map<String,Object> _queryParameters = new CaseInsensitiveHashMap<Object>();

    public Map<String,Object> getQueryParameters()
    {
        return _queryParameters;
    }
    
    void _parseQueryParameters(PropertyValues pvs)
    {
        String paramPrefix = param(URL_PARAMETER_PREFIX).toLowerCase();
        for (PropertyValue pv : pvs.getPropertyValues())
        {
            if (!pv.getName().toLowerCase().startsWith(paramPrefix))
                continue;
            _queryParameters.put(pv.getName().substring(paramPrefix.length()),pv.getValue());
        }
    }

    void setQueryParameter(String name, Object value)
    {
        _queryParameters.put(name,value);
    }

	public void setSchemaName(HString schemaName)
	{
		_schemaName = schemaName.toString();
	}

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setViewName(String viewName)
    {
        _viewName = StringUtils.trimToNull(viewName);
    }

    public String getViewName()
    {
        return _viewName;
    }

    public ReportIdentifier getReportId()
    {
        return _reportId;
    }

    public void setReportId(ReportIdentifier reportId)
    {
        _reportId = reportId;
    }

    public void setDataRegionName(String name)
    {
        _dataRegionName = name;
    }

    public String getDataRegionName()
    {
        return _dataRegionName;
    }

    public void setSelectionKey(String selectionKey)
    {
        _selectionKey = selectionKey;
    }

    public String getSelectionKey()
    {
        if (_selectionKey != null)
            return _selectionKey;
        return DataRegionSelection.getSelectionKey(getSchemaName(), getQueryName(), getViewName(), getDataRegionName());
    }

    public void setAllowChooseQuery(boolean b)
    {
        _allowChooseQuery = b;
    }

    public boolean getAllowChooseQuery()
    {
        return _allowChooseQuery;
    }

    public void setAllowChooseView(boolean b)
    {
        _allowChooseView = b;
    }

    public boolean getAllowChooseView()
    {
        return _allowChooseView;
    }

    /**
     * Returns the "returnURL" parameter or null if none.
     * The url may not necessarily be an ActionURL, e.g. if served from a FileContent html page.
     */
    public URLHelper getReturnUrl()
    {
        return _returnURL;
    }

    public void setReturnUrl(URLHelper returnURL)
    {
        _returnURL = returnURL;
    }

    public String param(QueryParam param)
    {
        switch (param)
        {
            case schemaName:
                return param.toString();
            default:
                return param(param.toString());
        }
    }

    protected String param(String param)
    {
        if (getDataRegionName() == null)
            return param;
        return getDataRegionName() + "." + param;
    }

    public final QueryDefinition getQueryDef(UserSchema schema)
    {
        if (_queryDef == null)
        {
            _queryDef = createQueryDef(schema);
        }
        return _queryDef;
    }

    protected QueryDefinition createQueryDef(UserSchema schema)
    {
        String queryName = getQueryName();
        if (queryName == null)
            return null;
        QueryDefinition ret = QueryService.get().getQueryDef(schema.getUser(), schema.getContainer(), schema.getSchemaName(), queryName);
        if (ret != null && getContainerFilterName() != null)
            ret.setContainerFilter(ContainerFilter.getContainerFilterByName(getContainerFilterName(), schema.getUser()));
        if (ret == null)
        {
            ret = schema.getQueryDefForTable(queryName);
        }
        return ret;
    }

    public CustomView getCustomView(ViewContext context, QueryDefinition queryDef)
    {
        if (queryDef == null)
        {
            return null;
        }
        return queryDef.getCustomView(context.getUser(), context.getRequest(), getViewName());
    }

    public Report getReportView(ContainerUser cu)
    {
        try {
            if (getReportId() != null)
            {
                return getReportId().getReport(cu);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean getIgnoreUserFilter()
    {
        return _ignoreUserFilter;
    }

    public void setIgnoreUserFilter(boolean b)
    {
        _ignoreUserFilter = b;
    }

    /** @return The maxRows parameter when {@link ShowRows#PAGINATED}, otherwise ALL_ROWS. */
    public int getMaxRows()
    {
        if (_showRows == ShowRows.NONE)
            return Table.NO_ROWS;
        if (_showRows != ShowRows.PAGINATED)
            return Table.ALL_ROWS;
        return _maxRows;
    }

    public void setMaxRows(int maxRows)
    {
        assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for maxRows; should be positive, Table.ALL_ROWS or Table.NO_ROWS";
        assert (maxRows == Table.NO_ROWS && _showRows == ShowRows.NONE) || (maxRows == Table.ALL_ROWS && _showRows == ShowRows.ALL) || _showRows == ShowRows.PAGINATED : "Can't set maxRows when not paginated";
        _maxRows = maxRows;
    }

    /** @return The offset parameter when {@link ShowRows#PAGINATED}, otherwise 0. */
    public long getOffset()
    {
        if (_showRows != ShowRows.PAGINATED)
            return Table.NO_OFFSET;
        return _offset;
    }

    public void setOffset(long offset)
    {
        assert (offset == Table.NO_OFFSET && _showRows != ShowRows.PAGINATED) || _showRows == ShowRows.PAGINATED : "Can't set maxRows when not paginated";
        _offset = offset;
    }

    public ShowRows getShowRows()
    {
        return _showRows;
    }

    public void setShowRows(ShowRows showRows)
    {
        _showRows = showRows;
    }

    public boolean isShowHiddenFieldsWhenCustomizing()
    {
        return _showHiddenFieldsWhenCustomizing;
    }

    public void setShowHiddenFieldsWhenCustomizing(boolean showHiddenFieldsWhenCustomizing)
    {
        _showHiddenFieldsWhenCustomizing = showHiddenFieldsWhenCustomizing;
    }

    /**
     * Base filter is applied before the custom view's filters and before any filter set by the user on the sortFilterURL.
     * The returned SimpleFilter is not null and may be mutated in place without calling the setBaseFilter() method.
     */
    public @NotNull SimpleFilter getBaseFilter()
    {
        if (_baseFilter == null)
            _baseFilter = new SimpleFilter();
        return _baseFilter;
    }

    public void setBaseFilter(SimpleFilter filter)
    {
        _baseFilter = filter;
    }

    /**
     * Base sort is applied before the custom view's sorts and before any sorts set by the user on the sortFilterURL.
     * The returned Sort is not null and may be mutated in place without calling the setBaseSort() method.
     */
    public @NotNull Sort getBaseSort()
    {
        if (_baseSort == null)
            _baseSort = new Sort();
        return _baseSort;
    }

    public void setBaseSort(Sort baseSort)
    {
        _baseSort = baseSort;
    }

    public ActionURL getSortFilterURL()
    {
        ActionURL url = HttpView.getRootContext().cloneActionURL();
        url.deleteParameters();
        url.setPropertyValues(_filterSort);
        return url;
    }

    public boolean isAllowCustomizeView()
    {
        return _allowCustomizeView;
    }

    public void setAllowCustomizeView(boolean allowCustomizeView)
    {
        _allowCustomizeView = allowCustomizeView;
    }

    public String getContainerFilterName()
    {
        return _containerFilterName;
    }

    public void setContainerFilterName(String name)
    {
        _containerFilterName = name;
    }

    public List<Aggregate> getAggregates()
    {
        return _aggregates;
    }

    public void setAggregates(List<Aggregate> aggregates)
    {
        _aggregates = aggregates;
    }

    public List<FieldKey> getFieldKeys()
    {
        return _fieldKeys;
    }

    public void setFieldKeys(List<FieldKey> keys)
    {
        _fieldKeys = keys;
    }
}
