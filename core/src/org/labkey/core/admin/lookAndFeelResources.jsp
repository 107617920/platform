<%
/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.core.admin.ProjectSettingsAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%=formatMissedErrors("form")%>
<%
    ProjectSettingsAction.LookAndFeelResourcesBean bean = ((JspView<ProjectSettingsAction.LookAndFeelResourcesBean>)HttpView.currentView()).getModelBean();
    Container c = getViewContext().getContainer();
%>
<form name="preferences" enctype="multipart/form-data" method="post" id="form-preferences">

<table cellpadding=0>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize the logo, icon, and stylesheets used <%=c.isRoot() ? "throughout the site" : "in this project"%> (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<tr>
    <td class="labkey-form-label">Header logo (appears in every page header; 147 x 56 pixels)</td>
    <td><input type="file" name="logoImage" size="50"></td>
</tr>
<tr>
    <td></td>
    <td>
        <% if (null != bean.customLogo)
        { %>
            Currently using a custom logo. <%=textLink("reset logo to default", "resetLogo.view")%>
        <% } else { %>
            Currently using the default logo.
        <% } %>
    </td>
</tr>

<tr>
    <td class="labkey-form-label">Favorite icon (displayed in user's favorites or bookmarks, .ico file only)</td>
    <td><input type="file" name="iconImage" size="50"></td>
</tr>
<tr>
    <td></td>
    <td>
        <% if (null != bean.customFavIcon)
        { %>
            Currently using a custom favorite icon. <%=textLink("reset favorite icon to default", "resetFavicon.view")%>
        <% } else { %>
            Currently using the default favorite icon.
        <% } %>
    </td>
</tr>

<tr>
    <td class="labkey-form-label">Custom stylesheet</td>
    <td><input type="file" name="customStylesheet" size="50"></td>
</tr>
<tr>
    <td></td>
    <td>
        <% if (null != bean.customStylesheet)
        { %>
            Currently using a custom stylesheet. <%=textLink("delete custom stylesheet", "deleteCustomStylesheet.view")%>
        <% } else { %>
            No custom stylesheet.
        <% } %>
    </td>
</tr>
<tr>
    <td><%=PageFlowUtil.generateSubmitButton("Save Resources", "_form.setClean();")%></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

</table>
</form>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>
<script type="text/javascript">
    var _form = new LABKEY.Form({
        formElement: 'form-preferences'
    });
</script>
