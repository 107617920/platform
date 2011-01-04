<%
/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.reports.ReportsController"%>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ReportsController.UploadForm> me = (JspView<ReportsController.UploadForm>) HttpView.currentView();
    ReportsController.UploadForm bean = me.getModelBean();

    boolean canUseDiskFile = HttpView.currentContext().getUser().isAdministrator() && bean.getReportId() == 0;
%>

<table>
<%
    for (ObjectError e : (List<ObjectError>) bean.getErrors().getAllErrors())
    {
%>      <tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<form method="post" action="" enctype="multipart/form-data">
    <table>
            <tr>
            <td class="labkey-form-label">
                Report Name
            </td>
            <td>
                <%
                if (null == bean.getLabel()) {
                %>
                    <input name="label">
                <%
                } else {
                %>
                    <input type="hidden" name="reportId" value="<%=h(bean.getReportId())%>"><%=h(bean.getLabel())%>
             <% } %>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">
                Report Date
            </td>
            <td>
                <input type="text" name="reportDateString">
            </td>
        </tr>
        <% if (canUseDiskFile)
        {%>
        <tr>
            <td colspan=2 class="labkey-form-label">
                <input type="radio" checked name="uploadType" onclick="showUpload()">Upload File &nbsp;&nbsp;
                <input type="radio" name="uploadType" onclick="showPath()">Use a file on server <%=request.getServerName()%>
            </td>
        </tr>
        <%}%>
        <tr>
            <td id="fileTitle" class="labkey-form-label">Choose file to upload</td>
            <td><input id=uploadFile type="file" name="formFiles[0]">
        <% if (canUseDiskFile)
        {%>
            <input size=50 id="filePath" style="display:none" name="filePath">
      <%}%>
            </td>
        </tr>
    </table>

<br>
    <%=generateSubmitButton("Submit")%>
    <%=generateBackButton("Cancel")%>
    </form>
<script type="text/javascript">
    function showUpload()
    {
        document.getElementById("uploadFile").style.display = "";
        document.getElementById("filePath").style.display = "none";
        document.getElementById("fileTitle").innerHTML = "Choose file to upload";
    }
    function showPath()
    {
        document.getElementById("uploadFile").style.display = "none";
        document.getElementById("filePath").style.display = "";
        document.getElementById("fileTitle").innerHTML = "Enter full path of file on server.";
    }
</script>
