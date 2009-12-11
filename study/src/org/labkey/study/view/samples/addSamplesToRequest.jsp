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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController"%>
<%@ page import="org.labkey.study.model.Specimen"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenController.AddToExistingRequestBean> me = (JspView<SpecimenController.AddToExistingRequestBean>) HttpView.currentView();
    SpecimenController.AddToExistingRequestBean bean = me.getModelBean();
%>
<%
    if (bean.getSpecimenQueryView() == null)
    {
%>
    <span class="labkey-error">ERROR: No samples were selected.  If you believe you've received this message in error,
    please contact your system administrator.</span><br>
<%
    }
    else
    {
%>
<form action="<%= "showCreateSampleRequest.post" %>" method="POST">
    Please select a request below to which to add the selected specimens.<br>
    Note that only the creator of a request or an administrator can add specimens to an existing request.<br>
    <br>
    Alternately, you may create a new request for the selected specimens.<br><br>
    <%= generateSubmitButton("Create New Specimen Request") %><br><br>
    <%
    for (Specimen specimen : bean.getSamples())
    {
    %><input type="hidden" name="sampleIds" value="<%= specimen.getRowId() %>"><%
    }
%>
    <table>
        <tr class="labkey-wp-header">
            <th>Available Specimen Requests</th>
        </tr>
        <tr>
            <td><% me.include(bean.getRequestsGridView(), out); %><br></td>
        </tr>
        <tr class="labkey-wp-header">
            <th>Selected Vials</th>
        </tr>
        <tr>
            <td><% if (bean.getSpecimenQueryView() != null)
                    me.include(bean.getSpecimenQueryView(), out); %></td>
        </tr>
    </table>
</form>
<%
    }
%>