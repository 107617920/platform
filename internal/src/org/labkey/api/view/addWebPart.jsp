<%
/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Portal.AddWebParts bean = (Portal.AddWebParts)HttpView.currentModel();
    Container c = getContainer();
    ActionURL currentURL = getActionURL();
%>
<% if (PageFlowUtil.useExperimentalCoreUI()) { %>
<div>
    <form class="form-inline" action="<%=urlProvider(ProjectUrls.class).getAddWebPartURL(c)%>">
        <input type="hidden" name="pageId" value="<%=h(bean.pageId)%>"/>
        <input type="hidden" name="location" value="<%=h(bean.location)%>"/>
        <%=generateReturnUrlFormField(currentURL)%>
        <div class="input-group">
            <select name="name" class="form-control">
                <option value="">&lt;Select Web Part&gt;</option>
                <%          for (Map.Entry<String, String> entry : bean.webPartNames.entrySet())
                {
                %><option value="<%=h(entry.getKey())%>"><%=h(entry.getValue())%></option> <%
                } %>
            </select>
            <span class="input-group-btn">
                <%= button("Add").submit(true) %>
            </span>
        </div>
    </form>
</div>
<% } else {%>
<table width="100%">
<tr>
    <td align="left">
		<form action="<%=urlProvider(ProjectUrls.class).getAddWebPartURL(c)%>">
		<table><tr><td>
		<input type="hidden" name="pageId" value="<%=h(bean.pageId)%>"/>
		<input type="hidden" name="location" value="<%=h(bean.location)%>"/>
        <%=generateReturnUrlFormField(currentURL)%>
        <select name="name">
            <option value="">&lt;Select Web Part&gt;</option>
<%          for (Map.Entry<String, String> entry : bean.webPartNames.entrySet())
            {
                %><option value="<%=h(entry.getKey())%>"><%=h(entry.getValue())%></option> <%
            } %>
        </select>
        </td><td>
        <%= button("Add").submit(true) %>
        </td></tr></table>
       </form>
    </td>
<% if (bean.rightWebPartNames != null && !bean.rightWebPartNames.isEmpty())
    { %>
    <td align="right">
        <form action="<%=urlProvider(ProjectUrls.class).getAddWebPartURL(c)%>">
        <table><tr><td>
        <input type="hidden" name="pageId" value="<%=h(bean.pageId)%>"/>
        <input type="hidden" name="location" value="right"/>
        <%=generateReturnUrlFormField(currentURL)%>
        <select name="name">
            <option value="">&lt;Select Web Part&gt;</option>
<%          for (Map.Entry<String, String> entry : bean.rightWebPartNames.entrySet())
            {
                %><option value="<%=h(entry.getKey())%>"><%=h(entry.getValue())%></option> <%
            } %>
        </select>
        </td><td>
            <%= button("Add").submit(true) %>
        </td></tr></table>
        </form>
    </td>
<%  } %>
</tr>
</table>
<% } %>