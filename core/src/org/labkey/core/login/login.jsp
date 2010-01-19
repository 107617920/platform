<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.login.LoginController.LoginBean" %>
<%@ page import="org.labkey.core.login.LoginController.LoginForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<LoginBean> me = (HttpView<LoginBean>) HttpView.currentView();
    LoginBean bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    User user = context.getUser();
    LoginForm form = bean.form;
    URLHelper returnURL = form.getReturnURLHelper(AppProps.getInstance().getHomePageActionURL());
    boolean agreeOnly = bean.agreeOnly;

    // Next bit of code makes the enter button work on IE.
    %>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>
<script type="text/javascript">
    if (Ext.isIE)
    {
        Ext.onReady(function() {
            var forms = document.getElementsByTagName('form');

            for (var i=0;i < forms.length;i++) {
                var inputs = forms[i].getElementsByTagName('input');

                for (var j=0;j < inputs.length;j++)
                    addInputSubmitEvent(forms[i], inputs[j]);
            }
        });
    }
</script>
    <%


    if (agreeOnly)
    { %>
<form name="login" method="POST" action="agreeToTerms.post"><%
    }
    else
    { %>
<form name="login" method="POST" action="login.post"><%
    } %>
    <table><%
    if (null != form.getErrorHtml() && form.getErrorHtml().length() > 0)
    { %>
        <tr><td colspan=2><b><%=form.getErrorHtml()%></b></td></tr>
        <tr><td colspan=2>&nbsp;</td></tr><%
    }
    if (!user.isGuest())
    { %>
        <tr><td colspan=2>You are currently logged in as <%=h(user.getName())%>.</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr><%
    }
    if (!agreeOnly)
    {
        String logoHtml = AuthenticationManager.getLoginPageLogoHtml(context.getActionURL());
        if (null != logoHtml)
        { %>
        <tr><td colspan="2"><%=logoHtml%></td></tr>
        <tr><td colspan=2>&nbsp;</td></tr><%
        } %>
        <% if (ModuleLoader.getInstance().isUpgradeRequired() || ModuleLoader.getInstance().isUpgradeInProgress()) { %>
            <tr><td colspan=2>This server is being upgraded to a new version of LabKey Server.</td></tr>
            <tr><td colspan=2>&nbsp;</td></tr>
        <% } %>
        <tr><td colspan=2>Type in your email address and password and click the Sign In button.</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr>
        <tr><td>Email:</td><td><input id="email" type="text" name="email" value="<%=h(form.getEmail())%>" style="width:200px;"></td></tr>
        <tr><td>Password:</td><td><input id="password" type="password" name="password" style="width:200px;"></td></tr>
        <tr><td></td><td><input type=checkbox name="remember" id="remember" <%=bean.remember ? "checked" : ""%>><label for="remember">Remember my email address</label></td></tr>
        <tr><td></td><td><a href="resetPassword.view">Forgot your password?</a></td></tr><%
    }

    if (null != bean.termsOfUseHTML)
    { %>
        <tr><td colspan=2>&nbsp;</td></tr>
        <tr><td></td><td><strong>Terms of Use</strong></td></tr>
        <tr><td></td><td><%=bean.termsOfUseHTML%></td></tr>
        <tr><td></td><td><input type=checkbox name="approvedTermsOfUse" id="approvedTermsOfUse"<%=bean.termsOfUseChecked ? " checked" : ""%>><label for="approvedTermsOfUse">I agree to these terms</label></td></tr><%
    } %>
        <tr><td></td><td height="50px">
            <%=generateReturnUrlFormField(returnURL)%><%

            if (bean.form.getSkipProfile())
            { %>
            <input type=hidden name=skipProfile value="1"><%
            }
            %>
            <%=PageFlowUtil.generateSubmitButton((bean.agreeOnly ? "Agree" : "Sign In"), "", "name=\"SUBMIT\"")%>
        </td></tr>
    </table>
</form>
