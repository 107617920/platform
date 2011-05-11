<%
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
%>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<!-- saveReportView.jsp -->

<%
    JspView<ReportsController.SaveReportViewForm> me = (JspView<ReportsController.SaveReportViewForm>) HttpView.currentView();
    ReportsController.SaveReportViewForm bean = me.getModelBean();
    ViewContext context = me.getViewContext();

    Report report = bean.getReport();
    boolean confirm = bean.getConfirmed() != null ? Boolean.parseBoolean(bean.getConfirmed()) : false;
%>

<script type="text/javascript">

    function validateForm()
    {
        var reportName = Ext.DomQuery.selectNode('#reportName');
        if ((!reportName) || (reportName.value.length === 0))
        {
            alert("View name cannot be blank.");
            return false;
        }
        return true;
    }
</script>

<table>
<%
    if (bean.getErrors() != null)
    {
        for (ObjectError e : (List<ObjectError>) bean.getErrors().getAllErrors())
        {
            %><tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
        }
    }
%>
</table>

<form method="post" action="<%=h(new ActionURL(ReportsController.SaveReportViewAction.class, context.getContainer()))%>" onsubmit="return validateForm();">
    <input type="hidden" name="<%=QueryParam.schemaName%>" value="<%=StringUtils.trimToEmpty(bean.getSchemaName())%>">
    <input type="hidden" name="<%=QueryParam.queryName%>" value="<%=StringUtils.trimToEmpty(bean.getQueryName())%>">
    <input type="hidden" name="<%=QueryParam.viewName%>" value="<%=StringUtils.trimToEmpty(bean.getViewName())%>">
    <input type="hidden" name="redirectUrl" value="<%=bean.getRedirectUrl()%>">
    <table>
    <tr>
<%
    if (confirm)
    {
%>
        <td>There is already a view called: <i><%=report.getDescriptor().getReportName()%></i>.<br/>Overwrite the existing view?
        <input type=hidden name=confirmed value=1>
        <input type=hidden name=label value="<%=bean.getLabel()%>">
<%
    } else {
%>
        <td><b>Save View</b></td>
        <td>Name:&nbsp;<input id="reportName" name="label" value="<%=h(bean.getLabel())%>">
        <input type=hidden name=srcURL value="<%=context.getActionURL().getLocalURIString()%>">
<%
    }
%>
        <input type=hidden name=reportType value="<%=report.getDescriptor().getReportType()%>">
        <input type=hidden name=params value="<%=h(bean.getParams())%>"></td>

<%--
        <td>Add as Custom View For:
            <select id="datasetSelection" name="showWithDataset">
<%
        for (DataSet def : defs)
        {
            if (def.canRead(context.getUser())) %>
                <option <%=def.getDataSetId() == showWithDataset ? " selected" : ""%> value="<%=def.getDataSetId()%>"><%=h(def.getLabel())%></option>
<%
        }
%>
            </select>
        </td>
--%>
        <td>&nbsp;</td>
        <td><%=generateSubmitButton((confirm ? "Overwrite" : "Save"))%>
<%
    if (confirm)
    {
%>
        &nbsp;<%=generateButton("Cancel", bean.getSrcURL())%>
<%
    } 
%>
    </td>
    </tr>
<%
    if (context.hasPermission(AdminPermission.class)) {
%>
        <tr>
            <td><input type="checkbox" value="true" name="shareReport" <%=bean.getShareReport() ? "checked" : ""%>>Make this view available to all users.</td>
            <td colspan=2>description:<textarea name="description" style="width: 100%;" rows="2"><%=StringUtils.trimToEmpty(bean.getDescription())%></textarea></td>
        </tr>
<%
    } else {
%>
        <tr>
            <td></td>
            <td colspan=2>description:&nbsp;<textarea name="description" style="width: 100%;" rows="2"><%=StringUtils.trimToEmpty(bean.getDescription())%></textarea></td>
        </tr>
<%
    }
%>
    </table>
</form><hr/>
