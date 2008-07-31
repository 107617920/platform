<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.RenderContext" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    String contextPath = request.getContextPath();
    JspView<ReportsController.PlotForm> me = (JspView<ReportsController.PlotForm>) HttpView.currentView();
    ReportsController.PlotForm bean = me.getModelBean();

    User user = (User) request.getUserPrincipal();
    boolean updateAccess = bean.getContainer().hasPermission(user, org.labkey.api.security.ACL.PERM_READ);
    int columns = bean.getChartsPerRow();
    int columnCount = 0;
    String plotAction = bean.getAction() == null ? "plot" : bean.getAction();
    if ("datasetView.plot".equals(bean.getAction()))
    {
        updateAccess = false;
    }
%>

<table class="labkey-form">
<%
    for (Report report : bean.getReports())
    {
        if (columnCount == 0 || (columnCount % columns) == 0)
            out.print("<tr>");
%>
<%
        if (updateAccess)
        {
%>
            <td><a href="<%=getReportURL(report, bean).addParameter("action", "delete")%>">
            <img valign="top" src="<%=contextPath%>/_images/delete.gif" alt="Remove"></a></td>
<%
        }
%>
        <td><img src="<%=getReportURL(report, bean).addParameter("action", plotAction)%>"></td>
<%
        columnCount++;
        if ((columnCount % columns) == 0)
            out.print("</tr>");
    }

    // close table
    if ((columnCount % columns) != 0)
        out.print("</tr>");
%>
</table>

<%!
    ActionURL getReportURL(Report report, ReportsController.PlotForm bean)
    {
        ActionURL url = new ActionURL(ReportsController.PlotChartAction.class, bean.getContainer()).
                                addParameter("datasetId", bean.getDatasetId()).
                                addParameter("reportId", report.getDescriptor().getReportId()).
                                addParameter("chartsPerRow", bean.getChartsPerRow()).
                                addParameter("isPlotView", String.valueOf(bean.getIsPlotView())).
                                addParameter("participantId", bean.getParticipantId());

        ActionURL filterUrl = RenderContext.getSortFilterURLHelper(HttpView.currentContext());
        for (Pair<String, String> param : filterUrl.getParameters())
        {
            if (url.getParameter(param.getKey()) == null)
                url.addParameter(param.getKey(), param.getValue());
        }
        return url;
    }
%>
