<%
/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<UserController.ImpersonateBean> me = (HttpView<UserController.ImpersonateBean>) HttpView.currentView();
    UserController.ImpersonateBean bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    User user = context.getUser();
    Container c = context.getContainer();
    ActionURL returnURL = context.getActionURL();

    if (bean.emails.isEmpty())
        return;
%>
    <form method="get" action="<%=new UserController.UserUrlsImpl().getImpersonateURL(c)%>"><labkey:csrf/>
        <table>
            <%

            if (bean.isAdminConsole)
            { %>
            <tr><td><%=bean.title%></td></tr><%
            }

            if (user.isImpersonated())
            {
        %>
            <tr><td>Already impersonating; click <a href="<%=h(urlProvider(LoginUrls.class).getStopImpersonatingURL(c, request))%>">here</a> to change back to <%=h(user.getImpersonatingUser().getDisplayName(user))%>.</td></tr><%
            }
            else
            {
                if (null != bean.message)
                { %>
            <tr><td><%=bean.message%></td></tr><%
                }
            %>
            <tr><td>
                <select id="email" name="email" style="width:200px;"><%
                    for (String email : bean.emails)
                    {%>
                    <option value="<%=h(email)%>"><%=h(email)%></option><%
                    }
                %>
                </select>
            <%=generateReturnUrlFormField(returnURL)%>
            <%=generateSubmitButton("Impersonate")%>
            </td></tr><%
            }
            %>
        </table>
    </form>
