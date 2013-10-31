<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<h2>Create A Remote Connection</h2>
<p>
    Administrators can define external remote connections to alternate LabKey servers.
    This feature should be used with care since, depending
    on your configuration, any user with access to the remote site could view arbitrary data in your remote server.
</p>
<labkey:errors></labkey:errors>
<%
    Container c = getContainer();

    QueryController.RemoteConnectionForm remoteConnectionForm = ((JspView<QueryController.RemoteConnectionForm>) HttpView.currentView()).getModelBean();
    String name = remoteConnectionForm.getConnectionName();
    String url = remoteConnectionForm.getUrl();
    String user = remoteConnectionForm.getUser();
    String password = remoteConnectionForm.getPassword();
    String container = remoteConnectionForm.getContainer();
%>

<br>

<form name="editConnection" action="<%=QueryController.RemoteConnectionUrls.urlSaveRemoteConnection(c) %>" method="post">
<table>
    <tr>
        <td>Connection Name: </td>
        <td><input type="text" name="connectionName" size="50" value="<%=h(name)%>"><br></td>
    </tr>
    <tr>
        <td>Server URL: <%= PageFlowUtil.helpPopup("Server URL", "Enter in the server URL. Include both the protocol (http:// or https://) and a context path if necessary. As an example, http://localhost:8080/labkey would be a valid name.")%></td>
        <td><input id="url" type="text" name="url" size="50" value="<%= h(url) %>"><br></td>
    </tr>
    <tr>
        <td>User: </td>
        <td><input id="user" type="text" name="user" size="50" value="<%= h(user)%>"></td>
    </tr>
    <tr>
        <td>Password: </td>
        <td><input id="password" type="password" name="password" size="50" value="<%=h(password)%>"></td>
    </tr>
    <tr>
        <td>Folder Path: <%= PageFlowUtil.helpPopup("Folder Path", "Enter the folder path on the LabKey server. An example folder path is 'My Folder/My Subfolder'.")%></td>
        <td><input id="container" type="text" name="container" size="50" value="<%=h(container)%>"></td>
    </tr>
</table>
    <%= generateSubmitButton("save")%>
    <%= generateButton("cancel", QueryController.ManageRemoteConnectionsAction.class) %>
</form>