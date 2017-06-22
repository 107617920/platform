<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.dilution.DilutionAssayRun" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("announcements/discuss.js");
        dependencies.add("nabqc");
    }
%>
<labkey:errors/>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
    DilutionAssayRun assay = bean.getAssay();

    boolean writer = getContainer().hasPermission(getUser(), InsertPermission.class);
%>
<table>
<%
    if (bean.needsNotesView())
    {
%>
    <tr class="labkey-wp-header">
        <th align="left">Notes</th>
    </tr>
    <% me.include(bean.getRunNotesView(), out); %>
<%
    }
%>
    <tr class="labkey-wp-header">
        <th>Run Summary<%= h(assay.getRunName() != null ? ": " + assay.getRunName() : "") %></th>
    </tr>
    <tr>
        <td><% me.include(bean.getRunPropertiesView(), out); %></td>
    </tr>
    <tr>
        <td>
            <table>
                <tr>
                    <td valign="top"><% me.include(bean.getGraphView(), out); %></td>
                    <td valign="top"><% include(bean.getControlsView(), out); %><br>
                        <% me.include(bean.getCutoffsView(), out); %>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td><% me.include(bean.getSamplePropertiesView(), out); %></td>
    </tr>
</table>
<table>
    <tr class="labkey-wp-header">
        <th><%=h(bean.getSampleNoun())%> Information</th>
    </tr>
    <tr>
        <td><% me.include(bean.getSampleDilutionsView(), out); %></td>
    </tr>
</table>
<table>
    <tr class="labkey-wp-header">
        <th>Plate Data</th>
    </tr>
    <tr>
        <td><% me.include(bean.getPlateDataView(), out); %></td>
    </tr>
    <%
    if (!bean.isPrintView() && writer)
    {
%>
    <tr class="labkey-wp-header">
        <th>Discussions</th>
    </tr>
    <tr>
        <td><% me.include(bean.getDiscussionView(getViewContext()), out); %></td>
    </tr>
<%
    }
%>
</table>