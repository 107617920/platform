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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.reports.ReportsController"%>
<%@ page import="org.labkey.study.reports.ExternalReport"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ReportsController.ExternalReportBean> me = (JspView<ReportsController.ExternalReportBean>) HttpView.currentView();
    ReportsController.ExternalReportBean bean = me.getModelBean();
    ExternalReport report = bean.getExtReport();

%>
<form action="" method="GET">
    Design external view. You can invoke any command line to generate the view. You can use the following
    substitution strings in your command line to identify the source data file and the output file to be generated.
    <ul>
        <li><%=h(ExternalReport.DATA_FILE_SUBST)%> This is the file where the data will be provided in tab delimited format. LabKey Server will generate this file name.</li>
        <li><%=h(ExternalReport.REPORT_FILE_SUBST)%> If your process returns data in a file, it should use the file name substituted here. For text and tab-delimited data,
            your process may return data via stdout instead of via a file. You must specify a file extension for your output file even if the result is returned via stdout.
            This allows LabKey to format the result properly.</li>
    </ul>

    Your code will be invoked by the user who is running the LabKey Server installation. The current directory will be determined by LabKey Server.
    <table>
        <tr>
            <td>Dataset/Query</td>
            <td>            <select name="queryName">
                <%
                    for (String name : bean.getTableAndQueryNames())
                    {
                %>
                <option value="<%= h(name) %>" <%= name.equals(report.getQueryName()) ? "SELECTED" : "" %>><%= h(name) %></option>
                <%
                    }
                %>
            </select>
</td>
        </tr>
        <tr>
            <td>Command Line</td>
            <td><input name="commandLine" size="50" value="<%=h(report.getCommandLine())%>"></td>
        </tr>
        <tr>
            <td>Output File Type</td>
            <td >
                <%
                    String ext = report.getFileExtension();
                %>
                <select name="fileExtension">
                    <option value="txt" <%="txt".equals(ext) ? "SELECTED" : ""%>>txt (Plain Text)</option>
                    <option value="tsv" <%="tsv".equals(ext) ? "SELECTED" : ""%>>tsv (Tab Delimited)</option>
                    <option value="jpg" <%="jpg".equals(ext) ? "SELECTED" : ""%>>jpg (JPEG Image)</option>
                    <option value="gif" <%="gif".equals(ext) ? "SELECTED" : ""%>>gif (GIF Image)</option>
                    <option value="png" <%="png".equals(ext) ? "SELECTED" : ""%>>png (PNG Image)</option>
                </select>
            </td>
        </tr>

    </table>

    <input type="image" src="<%=PageFlowUtil.submitSrc()%>" >
</form>

