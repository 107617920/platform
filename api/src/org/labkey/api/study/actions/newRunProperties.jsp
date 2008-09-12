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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.study.assay.AssayRunUploadContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    org.labkey.api.study.actions.AssayRunUploadForm bean = me.getModelBean();
%>
<table>
<%
    if (bean.isSuccessfulUploadComplete())
    {
%>
    <tr>
        <td class="labkey-header-large" colspan="2">Upload successful.  Upload another run below, or click Cancel to view previously uploaded runs.</td>
    </tr>
    <tr>
        <td>&nbsp;</td>
    </tr>
<%
    }
%>
    <tr class="labkey-wp-header">
        <td colspan="2">Assay Properties</td>
    </tr>
    <tr>
        <td class="labkey-form-label" nowrap="true">Name</td>
        <td width="100%"><%= h(bean.getProtocol().getName()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label" nowrap="true">Description</td>
        <td><%= h(bean.getProtocol().getProtocolDescription()) %></td>
    </tr>
    <% if (!bean.getUploadSetProperties().isEmpty())
    { %>
        <tr><td>&nbsp;</td></tr>
        <tr class="labkey-wp-header">
            <td colspan="2">Run Set Properties</td>
        </tr>
        <%
            for (Map.Entry<PropertyDescriptor, String> entry : bean.getUploadSetProperties().entrySet())
            {
                %>
                <tr>
                    <td class="labkey-form-label" nowrap="true"><%= h(entry.getKey().getNonBlankLabel()) %></td>
                    <td>
                        <%= h(bean.getUploadSetPropertyValue(entry.getKey(), entry.getValue())) %>
                    </td>
                </tr>
                <%
            }
        }
    %>
    <tr><td>&nbsp;</td></tr>
</table>
