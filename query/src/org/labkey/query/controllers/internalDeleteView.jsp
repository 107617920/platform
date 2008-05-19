<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.query.controllers.InternalViewForm" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.persist.CstmView" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% InternalViewForm form = (InternalViewForm) __form;
    ActionURL urlCancel = new ActionURL("query", "manageViews", getContainer());
    CstmView view = form.getViewAndCheckPermission();
    ActionURL urlPost = new ActionURL("query", "internalDeleteView", getContainer());
    urlPost.addParameter("customViewId", Integer.toString(form.getCustomViewId()));
%>
<form method="POST" action="<%=h(urlPost)%>">
    <p>Are you sure you want to delete this view?</p>
    <p>Schema: <%=h(view.getSchema())%><br>
       Query: <%=h(view.getQueryName())%><br>
        View Name: <%=h(view.getName())%><br>
        Owner: <%=h(String.valueOf(view.getCustomViewOwner()))%>
    </p>
    <labkey:button text="OK" /> <labkey:button text="Cancel" href="<%=urlCancel%>" />
</form>