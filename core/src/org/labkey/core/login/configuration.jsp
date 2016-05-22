<%
/*
 * Copyright (c) 2015 LabKey Corporation
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
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider.PrimaryAuthenticationProvider" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.Collection" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Collection<PrimaryAuthenticationProvider> primary = AuthenticationManager.getAllPrimaryProviders();
    Collection<SecondaryAuthenticationProvider> secondary = AuthenticationManager.getAllSecondaryProviders();
    boolean isExternalProviderEnabled = AuthenticationManager.isExternalProviderEnabled();

    LoginUrls urls = urlProvider(LoginUrls.class);
%>

<table>
    <tr><td colspan="5">These are the installed primary authentication providers:<br><br></td></tr>

    <% appendProviders(out, primary, urls); %>

    <tr><td colspan="5">&nbsp;</td></tr>
    <tr><td colspan="5">Other authentication options:</td></tr>
    <tr><td colspan="5">&nbsp;</td></tr>
    <tr>
        <td>&nbsp;&nbsp;</td>
        <td>Self sign-up</td>
        <% if (AuthenticationManager.isRegistrationEnabled()) { %>
        <td><%=PageFlowUtil.textLink("Disable", urls.getDisableConfigParameterURL(AuthenticationManager.SELF_REGISTRATION_KEY))%></td>
        <% } else { %>
        <td><%=PageFlowUtil.textLink("Enable", urls.getEnableConfigParameterURL(AuthenticationManager.SELF_REGISTRATION_KEY))%></td>
        <% } %>
        <td colspan="3">Users are able to register for accounts when using database authentication.  Use caution when enabling this if you have enabled sending email to non-users.</td>
    </tr>
    <tr>
        <td>&nbsp;&nbsp;</td>
        <td>Auto-create authenticated users</td>
        <% if (AuthenticationManager.isAutoCreateAccountsEnabled()) { %>
        <td><%=text(isExternalProviderEnabled ? textLink("Disable", urls.getDisableConfigParameterURL(AuthenticationManager.AUTO_CREATE_ACCOUNTS_KEY)) : "&nbsp;")%></td>
        <% } else { %>
        <td><%=text(isExternalProviderEnabled ? textLink("Enable", urls.getEnableConfigParameterURL(AuthenticationManager.AUTO_CREATE_ACCOUNTS_KEY)) : "&nbsp;")%></td>
        <% } %>
        <td colspan="3">Accounts are created automatically for users authenticated via LDAP, SSO, etc.</td>
    </tr>

    <%
    if (!secondary.isEmpty())
    {
%>
        <tr><td colspan="5">&nbsp;</td></tr>
        <tr><td colspan="5">These are the installed secondary authentication providers:<br><br></td></tr>

        <% appendProviders(out, secondary, urls); %>
<%
    }
%>
    <tr><td colspan="5">&nbsp;</td></tr>
    <tr><td colspan="5">Configure site-wide authentication options:<br><br></td></tr>
    <tr>
        <td>&nbsp;&nbsp;</td>
        <td>Self-service Email Changes</td>
        <% if (AuthenticationManager.isSelfServiceEmailChangesEnabled()) { %>
        <td><%=PageFlowUtil.textLink("Disable", urls.getDisableConfigParameterURL(AuthenticationManager.SELF_SERVICE_EMAIL_CHANGES_KEY))%></td>
        <% } else { %>
        <td><%=PageFlowUtil.textLink("Enable", urls.getEnableConfigParameterURL(AuthenticationManager.SELF_SERVICE_EMAIL_CHANGES_KEY))%></td>
        <% } %>
        <td colspan="3">Users can change their own email address if their password is managed by LabKey Server.</td>
    </tr>
    <tr><td colspan="5">&nbsp;</td></tr>
    <tr><td colspan="5">
    <%=button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL())%>
    </td></tr>
</table>

<%!
    private static void appendProviders(JspWriter out, Collection<? extends AuthenticationProvider> providers, LoginUrls urls) throws IOException
    {
        for (AuthenticationProvider authProvider : providers)
        {
            out.write("<tr><td>&nbsp;&nbsp;</td><td>");
            out.write(PageFlowUtil.filter(authProvider.getName()));
            out.write("</td>");

            out.write("<td>");
            if (authProvider.isPermanent())
            {
                out.write("&nbsp;");
            }
            else
            {
                if (AuthenticationManager.isActive(authProvider))
                    out.write(PageFlowUtil.textLink("disable", urls.getDisableProviderURL(authProvider)));
                else
                    out.write(PageFlowUtil.textLink("enable", urls.getEnableProviderURL(authProvider)));
            }
            out.write("</td>");

            ActionURL url = authProvider.getConfigurationLink();

            out.write("<td>");
            if (null == url)
                out.write("&nbsp;");
            else
                out.write(PageFlowUtil.textLink("configure", url));
            out.write("</td>");

            out.write("<td>");
            if (authProvider instanceof SSOAuthenticationProvider)
            {
                ActionURL pickLogoURL = urls.getPickLogosURL(authProvider);
                out.write(PageFlowUtil.textLink("pick logos", pickLogoURL));
            };
            out.write("</td>");

            out.write("<td>");
            out.write(authProvider.getDescription());
            out.write("</td>");

            out.write("</tr>\n");
        }
    }
%>
