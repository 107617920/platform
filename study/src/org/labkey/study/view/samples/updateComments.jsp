<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page import="org.labkey.study.model.Specimen" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.UpdateSpecimenCommentsBean> me = (JspView<SpringSpecimenController.UpdateSpecimenCommentsBean>) HttpView.currentView();
    SpringSpecimenController.UpdateSpecimenCommentsBean bean = me.getModelBean();
    Container container = me.getViewContext().getContainer();
%>
<%
    WebPartView.startTitleFrame(out, "Vial Comments", null, null, null);
%>
<labkey:errors/>
<form action="updateComments.post" id="updateCommentForm" method="POST">
    <input type="hidden" name="referrer" value="<%= bean.getReferrer() %>" />
    <input type="hidden" name="saveCommentsPost" value="<%= Boolean.TRUE.toString() %>" />
    <%
        for (Specimen vial : bean.getSamples())
        {
    %>
        <input type="hidden" name="rowId" value="<%= vial.getRowId() %>">
    <%
        }
    %>
    <table>
        <tr>
            <td>
                <textarea rows="10" cols="60" name="comments"><%= h(bean.getCurrentComment()) %></textarea><br>

            </td>
        </tr>
        <tr>
            <td>
                <%= generateSubmitButton("Save Changes") %>
                <%= generateButton("Cancel", new ActionURL(bean.getReferrer()))%>
            </td>
        </tr>
    </table>
</form>
<%
    WebPartView.endTitleFrame(out);
    WebPartView.startTitleFrame(out, "Selected Vials", null, null, null);
%>
<% me.include(bean.getSpecimenQueryView(), out); %>
<%
    WebPartView.endTitleFrame(out);
%>
