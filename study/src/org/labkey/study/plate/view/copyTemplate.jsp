<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.study.PlateTemplate" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.plate.PlateController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<PlateController.CopyTemplateBean> me = (JspView<PlateController.CopyTemplateBean>) HttpView.currentView();
    PlateController.CopyTemplateBean bean = me.getModelBean();
%>
<labkey:errors />
<table>
    <tr>
        <td>Copy <b><%= h(bean.getTemplateName()) %></b> to:</td>
    </tr>
    <%= bean.getTreeHtml() %>
    <tr>
        <td>
            <br>
            <form action="handleCopy.post" method="POST">
                <input type="hidden" name="destination" value="<%= h(bean.getSelectedDestination()) %>">
                <input type="hidden" name="templateName" value="<%= h(bean.getTemplateName()) %>">
                <%= buttonLink("Cancel", "plateTemplateList.view")%>
                <%= buttonImg("Copy", bean.getSelectedDestination() != null ? "" : "alert('Please select a destination folder.'); return false;") %>
            </form>
        </td>
    </tr>
<%
    PlateTemplate[] templates = bean.getDestinationTemplates();
    if (templates != null)
    {
%>
    <tr>
        <th align="left">Templates currently in <%= bean.getSelectedDestination() %>:</th>
    </tr>
<%
        if (templates.length == 0)
        {
%>
    <tr>
        <td class="labkey-indented">None</td>
    </tr>
<%
        }
        else
        {
            for (PlateTemplate template : templates)
            {
%>
    <tr>
        <td class="labkey-indented"><%= template.getName() %></td>
    </tr>
<%
            }
        }
    }
%>
</table>
