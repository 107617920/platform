<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.Set" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.DataCheckForm> me = (JspView<AdminController.DataCheckForm>) HttpView.currentView();
    AdminController.DataCheckForm bean = me.getModelBean();
%>

<labkey:errors/>

<form action="getSchemaXmlDoc.view" method="get">
    <table>
        <tr class="labkey-wp-header"><th colspan=2 align=center>Database Tools</th></tr>
        <tr><td>Check table consistency:&nbsp;</td>
        <td> <%=generateButton("Do Database Check", new ActionURL(AdminController.DoCheckAction.class, ContainerManager.getRoot()))%>&nbsp;</td></tr>
        <tr><td>&nbsp;</td><td></td></tr>
        <tr><td>Validate domains match hard tables:&nbsp;<br/>
        (Runs in background as pipeline job)</td>
        <td> <%=generateButton("Validate", new ActionURL(AdminController.ValidateDomainsAction.class, ContainerManager.getRoot()))%>&nbsp;</td></tr>
        <tr><td>&nbsp;</td><td></td></tr>
        <tr><td>Get schema xml doc:&nbsp;</td>
            <td>

                <select id="dbSchema" name="dbSchema" style="width:250"><%
                    for (Module m : bean.getModules())
                    {
                        Set<String> schemaNames = m.getSchemaNames();
                        for (String sn : schemaNames)
                        {
                        %>
                        <option value="<%= sn %>"><%= m.getName() + " : " + sn %></option >
                        <%
                        }
                   }
                    %>
                </select><br>
            </td></tr>
        <tr><td></td><td><%=generateSubmitButton("Get Schema Xml") %>
        <%=generateButton("Cancel", urlProvider(AdminUrls.class).getAdminConsoleURL())%>  </td></tr>
        <tr><td></td><td></td></tr>
    </table>
</form><br/><br/>

