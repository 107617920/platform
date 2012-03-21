<%
/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
%>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.view.RReportBean" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("reports/rowExpander.js");
    LABKEY.requiresScript("reports/manageViews.js");
</script>

<%
    JspView<ReportsController.ViewsSummaryForm> me = (JspView<ReportsController.ViewsSummaryForm>) HttpView.currentView();
    ReportsController.ViewsSummaryForm form = me.getModelBean();

    ViewContext context = HttpView.currentContext();

    RReportBean bean = new RReportBean();
    bean.setReportType(RReport.TYPE);
    bean.setRedirectUrl(context.getActionURL().getLocalURIString());

    JSONArray reportButtons = ReportUtil.getCreateReportButtons(context);
%>

<script type="text/javascript">

    Ext.onReady(function()
    {
        var gridConfig = {
            renderTo: 'viewsGrid',
            border  : false,
            <% if (form.getSchemaName() != null && form.getQueryName() != null) { %>
                baseQuery: {
                    schemaName: <%=PageFlowUtil.jsString(form.getSchemaName())%>,
                    queryName: <%=PageFlowUtil.jsString(form.getQueryName())%>,
                    baseFilterItems: <%=PageFlowUtil.jsString(form.getBaseFilterItems())%>
                },
                filterDiv: 'filterMsg',
            <% } %>
            container : <%=PageFlowUtil.jsString(context.getContainer().getPath())%>,
            createMenu :<%=reportButtons%>
        };
        
        var panel = new LABKEY.ViewsPanel(gridConfig);
        var grid = panel.show();
        var _resize = function(w,h) {
            LABKEY.Utils.resizeToViewport(grid, w, -1); // don't fit to height
        };
        Ext.EventManager.onWindowResize(_resize);
    });
    
</script>

<labkey:errors/>

<i><p id="filterMsg"></p></i>
<div id="viewsGrid" class="extContainer"></div>
