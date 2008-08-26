<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%=formatMissedErrors("form")%>
<%
    AdminController.LookAndFeelPropertiesBean bean = ((JspView<AdminController.LookAndFeelPropertiesBean>)HttpView.currentView()).getModelBean();
    Container c = getViewContext().getContainer();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
%>

<form name="preferences" enctype="multipart/form-data" method="post">

<table cellpadding=0>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize the look and feel of your LabKey Server installation (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">Header description (appears in every page header)</td>
    <td><input type="text" name="systemDescription" size="50" value="<%= h(laf.getDescription()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Header short name (appears in every page header and in emails)</td>
    <td><input type="text" name="systemShortName" size="50" value="<%= h(laf.getShortName()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Web theme (color scheme)</td>
    <td>
        <select name="themeName">
            <% for (WebTheme theme : bean.themes)
                {
                    String selected;

                    //if a new theme has just been defined
                    if (bean.newTheme != null)
                        selected = theme == bean.newTheme ? "selected" : "";
                    else
                        selected = theme == bean.currentTheme ? "selected" : "";
                    %>
                    <option value="<%=h(theme.toString())%>" <%=selected%>><%=h(theme.getFriendlyName())%></option>
                <%}
            %>
        </select><%

        if (c.isRoot())
        { %>

        [<a href="<%=AdminController.getDefineWebThemesURL(false)%>">define web themes</a>]<%

        }
        %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Font size</td>
    <td>
        <select name="themeFont">
            <% for (ThemeFont themeFont : bean.themeFonts)
                {
                out.print("<option value=\"" + themeFont.toString() + "\"" + (themeFont == bean.currentThemeFont ? " selected>" : ">") + themeFont.getFriendlyName() + "</option>\n");
                }
            %>
        </select>
        Font Size Samples:
            <% for (ThemeFont themeFont : bean.themeFonts)
                {
                out.print("<span style=\"font-size:" + themeFont.getNormalSize() + "\">&nbsp;&nbsp;" + themeFont.toString() + "</span>");
                }
            %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Show left navigation bar</td>
    <td>
        <%
            FolderDisplayMode currentMode = laf.getFolderDisplayMode();
            for (FolderDisplayMode mode : FolderDisplayMode.values())
        {%>
            <input type="radio" name="folderDisplayMode" value="<%=mode.toString()%>" <%=mode == currentMode ? "CHECKED" : "" %> >
            <%=mode.getDisplayString()%><br>
     <% } %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Left navigation bar width (pixels)</td>
    <td>
        <input name="navigationBarWidth" value="<%=laf.getNavigationBarWidth() %>" type="text" size="4">
    </td>
</tr>

<tr>
    <td class="labkey-form-label">Logo link (specifies page that header logo links to)</td>
    <td><input type="text" name="logoHref" size="50" value="<%= h(laf.getLogoHref()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Support link (specifies page where users can request support)</td>
    <td><input type="text" name="reportAProblemPath" size="50" value="<%= h(laf.getUnsubstitutedReportAProblemPath()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize settings used in system emails (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">System email address (<i>from</i> address for system notification emails)</td>
    <td><input type="text" name="systemEmailAddress" size="50" value="<%= h(laf.getSystemEmailAddress()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Organization name (appears in notification emails sent by system)</td>
    <td><input type="text" name="companyName" size="50" value="<%= h(laf.getCompanyName()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td><%=PageFlowUtil.generateSubmitButton("Save Properties")%>&nbsp;<%=PageFlowUtil.generateButton("Reset All Properties", new AdminController.AdminUrlsImpl().getResetLookAndFeelPropertiesURL(c), "return confirm('Are you sure you want to clear all look & feel properties?');")%></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

</table>
</form>
