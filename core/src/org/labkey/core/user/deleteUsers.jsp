<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page import="org.labkey.core.user.DeactivateUsersBean" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.user.DeleteUsersBean" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%
    JspView<DeleteUsersBean> me = (JspView<DeleteUsersBean>) HttpView.currentView();
    DeleteUsersBean bean = me.getModelBean();
    ActionURL urlPost = me.getViewContext().cloneActionURL();
    urlPost.deleteParameters();

    ActionURL deactivateUsersUrl = new ActionURL(UserController.DeactivateUsersAction.class, me.getViewContext().getContainer());
%>
<p>Are sure you want to <span style="font-weight:bold;color: #FF0000">permanently delete</span>
the following <%=bean.getUsers().size() > 1 ? "users" : "user"%>?
This action cannot be undone.</p>
    <ul>
    <%
        for(User user : bean.getUsers())
        {
            %><li><%=PageFlowUtil.filter(user.getDisplayNameOld(me.getViewContext()))%></li><%
        }
    %>
    </ul>
<form action="<%=urlPost.getEncodedLocalURIString()%>" method="post" name="deleteUsersForm">
    <%
        for(User user : bean.getUsers())
        {
            %><input type="hidden" name="userId" value="<%=user.getUserId()%>"/><%
        }
    %>
    <%=PageFlowUtil.generateSubmitButton("Permanently Delete")%>
    <%=PageFlowUtil.generateButton("Cancel", bean.getCancelUrl())%>
</form>
<%
    boolean canDeactivate = false;
    for(User user : bean.getUsers())
    {
        if(user.isActive())
        {
            canDeactivate = true;
            break;
        }
    }
    if(canDeactivate) {
%>
<form action="<%=deactivateUsersUrl%>" method="post" name="deactivateUsersForm">
    <%
        for(User user : bean.getUsers())
        {
            %><input type="hidden" name="userId" value="<%=user.getUserId()%>"/><%
        }
    %>
    <p><span style="font-weight:bold">Note:</span> you may also
    <a href="#" onclick="document.deactivateUsersForm.submit();return false;">deactivate <%=bean.getUsers().size() > 1 ? "these users" : "this user"%></a>
    instead of deleting them.
    Deactivated users may not login, but their information will be preserved
    for display purposes, and their group memberships will be preserved in case
    they are re-activated at a later time.</p>
    <p><%=PageFlowUtil.generateSubmitButton(bean.getUsers().size() > 1 ? "Deactivate Users" : "Deactivate User")%></p>
</form>
<% } %>