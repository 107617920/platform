/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.WebPartView;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;
import org.json.JSONObject;

import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * User: adam
 * Date: Jan 27, 2009
 * Time: 3:51:42 PM
 */
public abstract class ExportScriptModel
{
    private QueryView _view;

    public ExportScriptModel(QueryView view)
    {
        assert view != null;
        _view = view;
    }

    public String getInstallationName()
    {
        LookAndFeelProperties props = LookAndFeelProperties.getInstance(_view.getViewContext().getContainer());
        return null == props ? "LabKey Server" : props.getShortName();
    }

    public String getCreatedOn()
    {
        SimpleDateFormat fmt = new SimpleDateFormat(JSONObject.JAVASCRIPT_DATE_FORMAT);
        return fmt.format(new Date());
    }

    public String getBaseUrl()
    {
        AppProps props = AppProps.getInstance();
        return props.getBaseServerUrl() + props.getContextPath();
    }

    public String getSchemaName()
    {
        return _view.getSchema().getSchemaName();
    }

    public String getQueryName()
    {
        return _view.getSettings().getQueryName();
    }

    public String getFolderPath()
    {
        return _view.getContainer().getPath();
    }

    @Nullable
    public String getViewName()
    {
        return _view.getSettings().getViewName();
    }

    public abstract String getFilters();
    protected abstract String makeFilterExpression(String name, CompareType operator, String value);

    protected List<String> getFilterExpressions()
    {
        //R package wants filters like this:
        //   makefilter(c(name, operator, value), c(name, operator, value))
        //where 'name' is the column name, 'operator' is the string version of the operator
        //as defined in the Rlabkey package, and 'value' is the filter value.

        //load the sort/filter url into a new SimpleFilter
        //and iterate the clauses
        QueryView view = getQueryView();
        ArrayList<String> makeFilterExprs = new ArrayList<String>();
        SimpleFilter filter = new SimpleFilter(view.getSettings().getSortFilterURL(), view.getDataRegionName());
        String name;
        CompareType operator;
        String value;
        for(SimpleFilter.FilterClause clause : filter.getClauses())
        {
            //all filter clauses can report col names and values,
            //each of which in this case should contain only one value
            name = clause.getColumnNames().get(0);
            value = getFilterValue(clause, clause.getParamVals());

            //two kinds of clauses can be used on URLs: CompareClause and InClause
            if(clause instanceof CompareType.CompareClause)
                operator = ((CompareType.CompareClause)clause).getComparison();
            else if(clause instanceof SimpleFilter.InClause)
                operator = CompareType.IN;
            else
                operator = CompareType.EQUAL;

            makeFilterExprs.add(makeFilterExpression(name, operator, value));
        }

        return makeFilterExprs;
    }

    protected String getFilterValue(SimpleFilter.FilterClause clause, Object[] values)
    {
        if(null == values || values.length == 0)
            return "";

        //in clause has multiple values, which are in semi-colon-delimited list on the URL
        if(clause instanceof SimpleFilter.InClause)
        {
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for(Object val : values)
            {
                sb.append(sep);
                sb.append(val.toString());
                sep = ";";
            }
            return sb.toString();
        }
        else
        {
            //should have only one value (convert null to empty string)
            return null == values[0] ? "" : values[0].toString();
        }
    }

    @Nullable
    public String getSort()
    {
        String sortParam = _view.getSettings().getSortFilterURL().getParameter(_view.getDataRegionName() +  ".sort");
        if(null == sortParam || sortParam.length() == 0)
            return null;
        else
            return "\"" + sortParam + "\"";
    }

    protected QueryView getQueryView()
    {
        return _view;
    }

    public static ModelAndView getExportScriptView(QueryView queryView, String scriptType, PageConfig pageConfig, HttpServletResponse response)
    {
        pageConfig.setTemplate(PageConfig.Template.None);
        response.setContentType("text/plain");
        ExportScriptFactory factory = QueryView.getExportScriptFactory(scriptType);
        WebPartView scriptView = factory.getView(queryView);
        scriptView.setFrame(WebPartView.FrameType.NONE);
        return scriptView;
    }

    public ContainerFilter getContainerFilter()
    {
        String containerFilterName = _view.getSettings().getSortFilterURL().getParameter(_view.getDataRegionName() +  ".containerFilterName");
        if (containerFilterName != null)
            return ContainerFilter.getContainerFilterByName(containerFilterName, _view.getUser());
        else
            return _view.getQueryDef().getContainerFilter();
    }
}
