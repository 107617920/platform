<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AuthenticationManager.AuthLogoBean> me = (HttpView<AuthenticationManager.AuthLogoBean>) HttpView.currentView();
    AuthenticationManager.AuthLogoBean bean = me.getModelBean();
%><form action="<%=h(bean.postURL)%>" enctype="multipart/form-data" method="post">
<table>
<%=formatMissedErrorsInTable("form", 3)%>
<tr>
    <td colspan="3"><input type="hidden" name="name" value="<%=h(bean.name)%>"></td>
</tr>
<tr id="auth_header_logo_row">
    <td class="labkey-form-label" nowrap>Page header logo</td>
    <%=text(bean.headerLogo)%>
</tr>
<tr id="auth_login_page_logo_row">
    <td class="labkey-form-label" nowrap>Login page logo</td>
    <%=text(bean.loginPageLogo)%>
</tr>
<tr>
    <td class="labkey-form-label" nowrap>Enter a URL<%=PageFlowUtil.helpPopup("URL Instructions", "Include <code>%returnURL%</code> as the redirect parameter within the URL.  <code>%returnURL%</code> will be replaced with a link to the login page including the current page as a redirect parameter.  Examples:<br><br>http://localhost:8080/openfm/UI/Login?service=adminconsoleservice&goto=%returnURL%<br>https://machine.domain.org:8443/openfm/WSFederationServlet/metaAlias/wsfedsp?wreply=%returnURL%", true, 700)%></td>
    <td colspan="2"><input type="text" name="url" size="130" value="<%=h(bean.url)%>"></td>
</tr>
<tr>
    <td colspan="3">&nbsp;</td>
</tr>
<tr>
    <td colspan="3"><%= button("Save").submit(true) %>&nbsp;
        <%= button(bean.reshow ? "Done" : "Cancel").href(bean.returnURL) %></td>
</tr>
</table>
</form>
<script type="text/javascript">
    function deleteLogo(prefix)
    {
        var td1 = document.getElementById(prefix + 'td1');
        var td2 = document.getElementById(prefix + 'td2');
        var tr = document.getElementById(prefix + 'row');
        tr.removeChild(td1);
        tr.removeChild(td2);

        var newTd = document.createElement('td');
        newTd.setAttribute('colspan', '2');

        var fb = document.createElement('input');
        fb.setAttribute('name', prefix + 'file');
        fb.setAttribute('type', 'file');
        fb.setAttribute('size', '60');

        var hidden = document.createElement('input');
        hidden.setAttribute('type', 'hidden');
        hidden.setAttribute("name", "deletedLogos");
        hidden.setAttribute("value", prefix + '<%=bean.name%>');

        newTd.appendChild(fb);
        newTd.appendChild(hidden);

        tr.appendChild(newTd);        
    }
</script>