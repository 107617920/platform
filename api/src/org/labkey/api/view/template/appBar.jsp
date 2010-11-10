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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.template.AppBar" %>
<%@ page import="org.labkey.api.view.template.AppBarView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    AppBar bean = ((AppBarView) HttpView.currentView()).getModelBean();
    if (null == bean)
        return;
%>
<table class="labkey-app-bar">
    <tr>
        <td ><%=h(bean.getAppTitle())%></td>
        <td>
            <table class="labkey-app-button-bar labkey-no-spacing">
                <td class="labkey-app-button-bar-left"><img alt="" src="<%=request.getContextPath()%>/_.gif" width="13"></td>
                <%
                    for (NavTree navTree : bean.getButtons())
                    {
                %>
                        <td class="labkey-app-button-bar-button"><a href="<%=h(navTree.getValue())%>"><%=h(navTree.getKey())%></a></td>
                <%
                    }
                %>
                <td class="labkey-app-button-bar-right"><img alt="" src="<%=request.getContextPath()%>/_.gif" width="13"></td>
            </table>
        </td>
    </tr>
</table>
