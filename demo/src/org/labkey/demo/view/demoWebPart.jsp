<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.demo.DemoController" %>
<%@ page import="org.labkey.demo.model.Person" %>
<%@ page import="org.labkey.demo.view.BulkUpdatePage" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = HttpView.currentContext();
    org.labkey.demo.view.BulkUpdatePage pageInfo = (BulkUpdatePage) (HttpView.currentModel());
    List<Person> people = pageInfo.getList();
%>
This container contains <%= people.size() %> people.<br>
<%= generateButton("View Grid", new ActionURL(DemoController.BeginAction.class, context.getContainer())) %>