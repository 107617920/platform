<%
/*
 * Copyright (c) 2005-2011 Fred Hutchinson Cancer Research Center
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.collections.NamedObject" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.DbLoginManager" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    LoginController.SetPasswordBean bean = ((JspView<LoginController.SetPasswordBean>)HttpView.currentView()).getModelBean();
    String errors = formatMissedErrors("form");
%>
<form method="POST" id="setPasswordForm" action="<%=bean.actionName%>.post"><labkey:csrf />
<%
    if (errors.length() > 0)
    { %>
    <div><%=errors%></div><%
    }

    if (!bean.unrecoverableError)
    {
        if (null != bean.email)
        { %>
    <div><%=h(bean.email)%>:</div><%
        } %>
    <div style="width: 50em;"><%=h(bean.message)%></div>
    <div><br/></div><%

    for (NamedObject input : bean.nonPasswordInputs)
    { %>
    <div style="padding-top: 1em;">
        <label for="<%=input.getObject()%>"><%=h(input.getName())%></label>
        <br/>
        <input id="<%=input.getObject()%>" type="text" name="<%=input.getObject()%>" value="<%= h(input.getDefaultValue()) %>" style="width:20em;">
    </div><%
    }

    for (NamedObject input : bean.passwordInputs)
    { %>
    <div style="padding-top: 1em;">
        <label for="<%=input.getObject()%>"><%=h(input.getName())%></label>
        <% if (LoginController.PASSWORD1_TEXT_FIELD_NAME.equals(input.getObject())) { %>
            <span style="font-size: smaller;">(<%=DbLoginManager.getPasswordRule().getSummaryRuleHTML()%>)</span>
        <% } %>

        <br/>
        <input id="<%=input.getObject()%>" type="password" name="<%=input.getObject()%>" style="width:20em;">
    </div><%
    }
    %>
    <div>
        <%
            if (null != bean.email)
            { %>
            <input type="hidden" name="email" value="<%=h(bean.email)%>"><%
            }

            if (null != bean.form.getVerification())
            { %>
            <input type="hidden" name="verification" value="<%=h(bean.form.getVerification())%>"><%
            }

            if (null != bean.form.getMessage())
            { %>
            <input type="hidden" name="message" value="<%=h(bean.form.getMessage())%>"><%
            }

            if (bean.form.getSkipProfile())
            { %>
            <input type="hidden" name="skipProfile" value="1"><%
            }

            if (null != bean.form.getReturnURLHelper())
            { %>
            <%=generateReturnUrlFormField(bean.form)%><%
            }
        %>
        </div>
    <div style="padding-top: 1em;"><%=PageFlowUtil.generateSubmitButton(bean.buttonText, "", "name=\"set\"")%><%=bean.cancellable ? generateButton("Cancel", bean.form.getReturnURLHelper()) : ""%></div><%
    } %>
</form>

<script type="text/javascript">
    Ext.onReady(function()
    {
        addInputSubmitEvent(document.getElementById("setPasswordForm"));
    });
</script>