<%
/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.BooleanUtils" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    Map<String, String> pm = webPart.getPropertyMap();

    Map<String, String> reportMap = new LinkedHashMap<>();
    ArrayList<String> reportNames = new ArrayList<>();

    ReportUtil.ReportFilter filter = new ReportUtil.DefaultReportFilter();
    Container c = context.getContainer();
    User u = context.getUser();

    for (Report report : ReportUtil.getReports(c, u, null, true))
    {
        if (!filter.accept(report, c, u) || report.getDescriptor().isHidden())
            continue;

        if (!StringUtils.isEmpty(report.getDescriptor().getReportName()))
        {
            reportMap.put(report.getDescriptor().getReportName(), report.getDescriptor().getReportId().toString());
            reportNames.add(report.getDescriptor().getReportName().toString());
        }
    }
    Collections.sort(reportNames, String.CASE_INSENSITIVE_ORDER);
%>

<form name="frmCustomize" method="post" action="<%=h(webPart.getCustomizePostURL(context))%>">
    <table>
        <tr>
            <td class="labkey-form-label">Web Part Title:</td>
            <td><input type="text" name="title" size="40" value="<%=h(pm.get("title"))%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Report or View:</td>
            <td>
                <select id="reportId" name="<%=Report.renderParam.reportId.name()%>" onchange="getSectionNames(this);">
                    <%
                        for (String reportName : reportNames)
                        {
                            if (reportMap.get(reportName).equals(pm.get(Report.renderParam.reportId.name())))
                            {
                                %>
                                    <option value="<%=reportMap.get(reportName)%>" selected><%=reportName%></option>
                                <%
                            }
                            else
                            {
                                %>
                                    <option value="<%=reportMap.get(reportName)%>"><%=reportName%></option>
                                <%
                            }
                        }

                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Show View Tabs:<%=PageFlowUtil.helpPopup("Show tabs",
                    "Some views may be rendered with multiple tabs showing. Select this option to only show the primary view.")%></td>
            <td><input id='showTabs' type="checkbox" name="<%=Report.renderParam.showTabs.name()%>" <%=BooleanUtils.toBoolean(pm.get(Report.renderParam.showTabs.name())) ? "checked" : ""%> onclick="onShowTabs(this.checked);"></td>
        </tr>
        <tr id="visibleSections">
            <td class="labkey-form-label">Visible Report Sections:<%=PageFlowUtil.helpPopup("Show Report sections",
                    "Some views contain mutiple sections such as: images, text, console output. For these types of views, you can select which section(s) to " +
                            "display by selecting them from the list.")%></td>
            <td><select id="showSection" multiple="true" onchange="selectSection()"></select></td>
        </tr>
        <tr>
            <td/>
            <td><labkey:button text="Submit" /></td>
        </tr>
    </table>
    <input type="hidden" name="<%=Report.renderParam.showSection.name()%>" id="showSectionHidden">
</form>

<script type="text/javascript">

    function getSectionNames()
    {
        var element = document.getElementById('reportId');
        // ajax call to get report section names
        if (element)
        {
            var url = "<%=urlProvider(ReportUrls.class).urlReportSections(context.getContainer())%>";

            url = url.concat("&<%=ReportDescriptor.Prop.reportId.name()%>=");
            url = url.concat(element.value);
            url = url.concat("&<%=Report.renderParam.showSection.name()%>=<%=PageFlowUtil.encode(pm.get(Report.renderParam.showSection.name()))%>");

            Ext.Ajax.request({
                method: "GET",
                url: url,
                success : handleSuccess
            });
        }
    }

    function handleSuccess(o)
    {
        if (o.responseText !== undefined)
        {
            var status = eval("(" + o.responseText + ')');
            var row = document.getElementById('visibleSections');
            row.deleteCell(1);
            var td = row.insertCell(1);

            if (status && status.sectionNames)
            {
                // build the selection for visible report sections
                var select = "<select id=\"showSection\" width=\"150px\" multiple=\"true\" onchange=\"selectSection()\">";
                select = select.concat(status.sectionNames);
                select = select.concat("</select>");

                td.innerHTML = select;

                var showTabs = document.getElementById("showTabs");
                if (showTabs)
                    onShowTabs(showTabs.checked);
            }
        }
    }

    function selectSection()
    {
        document.getElementById("showSectionHidden").value = "";
        var element = document.getElementById("showSection");
        if (element)
        {
            var length = element.options.length;
            var params = [];

            for (var i=0; i < length; i++)
            {
                var option = element.options[i];
                if (option.selected)
                    params.push(option.value);
            }

            if (params.length > 0)
                document.getElementById("showSectionHidden").value = params.join("&");
        }
    }

    function onShowTabs(checked)
    {
        var showSection = document.getElementById("showSection");
        if (showSection)
            showSection.disabled = checked;
    }

    Ext.onReady(getSectionNames);

</script>
