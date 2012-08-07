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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.lists.permissions.DesignListPermission" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenu" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.list.view.ListController" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ViewContext> view = (JspView<ViewContext>) HttpView.currentView();
    ViewContext me = view.getModelBean();
    ViewContext ctx = HttpView.currentContext();
    Container c = ctx.getContainer();
    User user = ctx.getUser();
    Map<String, ListDefinition> lists = ListService.get().getLists(c);
    NavTree links;
    PopupMenuView pmw;
    boolean isBegin = true;

    if (me != null)
    {
        isBegin = false;
    }
%>
<table>
    <%
        if (lists.isEmpty())
        {
    %>
        <tr><td>There are no user-defined lists in this folder.</td></tr>
    <%
        }
        else
        {
            for (ListDefinition list : new TreeSet<ListDefinition>(lists.values()))
            {
    %>
        <tr><td>
            <a href="<%=PageFlowUtil.filter(list.urlShowData())%>"><%=PageFlowUtil.filter(list.getName())%></a>
            <%
                links = new NavTree("");

                links.addChild("View Data", list.urlShowData());
                if (c.hasPermission(user, DesignListPermission.class))
                {
                    if (!isBegin)
                    {
%>
                        <span style="margin-right: -8px;">|</span>
<%
                        links.addChild("View Design", list.urlShowDefinition());
                    }
                    else
                    {
%>
                        <td><labkey:link href="<%=list.urlShowDefinition()%>" text="view design" /></td>
<%
                    }
                }
                if (AuditLogService.get().isViewable())
                {
                    if (!isBegin)
                    {
                        links.addChild("View History", list.urlShowHistory());
                    }
                    else
                    {
%>
                        <td><labkey:link href="<%=list.urlShowHistory()%>" text="view history" /></td>
<%
                    }
                }
                if (c.hasPermission(user, DesignListPermission.class))
                {
                    if (!isBegin)
                    {
                        links.addChild("Delete List", list.urlFor(ListController.DeleteListDefinitionAction.class));
                    }
                    else
                    {
%>
                        <td><labkey:link href="<%=list.urlFor(ListController.DeleteListDefinitionAction.class)%>" text="delete list" /></td>
<%
                    }
                }

                if (!isBegin && links.getChildren().length > 1)
                {
                    pmw = new PopupMenuView(links);
                    pmw.setButtonStyle(PopupMenu.ButtonStyle.TEXT);
                    include(pmw, out);
                }
            %>
        </td></tr>
    <%
            }
        }
    %>
</table>
<%
    if (c.hasPermission(user, DesignListPermission.class))
    {
        if (isBegin)
        {
%>
        <br/>
        <labkey:button text="Create New List" href="<%=h(urlFor(ListController.EditListDefinitionAction.class))%>"/>
        <labkey:button text="Import List Archive" href="<%=h(urlFor(ListController.ImportListArchiveAction.class))%>"/>
<%
            if (!lists.isEmpty())
            {
%>
                <labkey:button text="Export List Archive" href="<%=h(urlFor(ListController.ExportListArchiveAction.class))%>"/>
<%
            }
        }
        else
        {
%>
    <%=PageFlowUtil.textLink("manage lists", ListController.getBeginURL(c))%>
<%
        }
    }
%>