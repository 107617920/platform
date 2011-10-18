<%
/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.LoginController.Config" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="org.labkey.core.login.PasswordRule" %>
<%@ page import="org.labkey.api.security.PasswordExpiration" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<LoginController.Config> me = (JspView<Config>)HttpView.currentView();
    Config bean = me.getModelBean();
%>
<form action="configureDbLogin.post" method="post">
<table>
    <tr>
        <td class="labkey-form-label">Password Strength</td>
        <td><table class="labkey-data-region labkey-show-borders"><%
            for (PasswordRule rule : PasswordRule.values())
            { %>
            <tr valign="center">
                <td><input type="radio" name="strength" value="<%=rule.name()%>"<%=rule.equals(bean.currentRule) ? " checked" : ""%>><b><%=h(rule.name())%></b></td>
                <td><%=rule.getFullRuleHTML()%></td>
            </tr>
                <%
            }
        %></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Password Expiration</td>
        <td><select name="expiration"><%
            for (PasswordExpiration expiration : PasswordExpiration.displayValues())
            { %>
            <option value="<%=expiration.name()%>"<%=expiration.equals(bean.currentExpiration) ? " selected" : ""%>><%=h(expiration.getDescription())%></option>
                <%
            }
        %></select></td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td colspan=2>
            <%=generateSubmitButton("Save")%>
            <%=generateButton(bean.reshow ? "Done" : "Cancel", urlProvider(LoginUrls.class).getConfigureURL())%>
        </td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td colspan=2><%=bean.helpLink%></td>
    </tr>
</table>
</form>