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
<%@ page import="org.labkey.study.model.VisitImpl"%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<table>
<%
    if (getVisits().length > 0)
    {
%>
    <tr>
        <td>Visits can be displayed in any order.</td>
        <td><%= textLink("Change Display Order", "visitDisplayOrder.view")%></td>
    </tr>
    <tr>
        <td>Visit visibility and label can be changed.</td>
        <td><%= textLink("Change Properties", "visitVisibility.view")%></td>
    </tr>
<%
    }
%>
    <tr>
        <td>New visits can be defined for this study at any time.</td>
        <td><%= textLink("Create New Visit", "createVisit.view")%></td>
    </tr>
    <tr>
        <td>Recalculate visit dates</td>
        <td><%= textLink("Recalculate Visit Dates", "updateParticipantVisits.view")%></td>
    </tr>
    <tr>
        <td>Import a visit map to quickly define a study</td>
        <td><%= textLink("Import Visit Map", "uploadVisitMap.view") %></td>
    </tr>

</table>

<%
    if (getVisits().length > 0)
    {
%>
<p>
<table id="visits">
    <tr>
        <th>&nbsp;</th>
        <th>Label</th>
        <th>Sequence</th>
        <th>Cohort</th>
        <th>Type</th>
        <th>Show By Default</th>
        <th>&nbsp;</th>
    </tr>
    <%
        for (VisitImpl visit : getVisits())
        {
    %>
        <tr>
            <td><%= textLink("edit", "visitSummary.view?id=" + visit.getRowId()) %></td>
            <th align=left><%= visit.getDisplayString() %></th>
            <td><%= visit.getSequenceNumMin() %><%= visit.getSequenceNumMin()!= visit.getSequenceNumMax() ? "-" + visit.getSequenceNumMax() : ""%></td>
            <td><%= visit.getCohort() != null ? h(visit.getCohort().getLabel()) : "All"%></td>
            <td><%= visit.getType() != null ? visit.getType().getMeaning() : "[Not defined]"%></td>
            <td><%= visit.isShowByDefault()%></td>
        </tr>
    <%
        }
    %>
</table>
<%
    }
%>