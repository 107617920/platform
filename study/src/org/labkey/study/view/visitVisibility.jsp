<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(getStudy().getContainer(), getViewContext().getUser());
%>
<form action="<%=h(buildURL(StudyController.VisitVisibilityAction.class))%>" method="POST">
    <table>
        <tr>
            <th align="left">ID</th>
            <th align="left">Label</th>
            <th align="left">Cohort</th>
            <th align="left">Type</th>
            <th align="left">Show By Default</th>
        </tr>
    <%
        for (VisitImpl visit : getVisits(Visit.Order.DISPLAY))
        {
    %>
        <tr>
            <td><%= visit.getRowId() %></td>
            <td>
                <input type="text" size="40" name="label" value="<%= h(visit.getLabel()) %>">
            </td>
            <td>
                <%
                    if (cohorts == null || cohorts.size() == 0)
                    {
                %>
                    <em>No cohorts defined</em>
                <%
                    }
                    else
                    {
                    %>
                    <select name="cohort">
                        <option value="-1">All</option>
                    <%

                        for (CohortImpl cohort : cohorts)
                        {
                    %>
                        <option value="<%= cohort.getRowId()%>" <%= text(visit.getCohortId() != null && visit.getCohortId() == cohort.getRowId() ? "SELECTED" : "") %>>
                            <%= h(cohort.getLabel())%>
                        </option>
                    <%
                        }
                    %>
                    </select>
                    <%
                    }
                %>
            </td>
            <td>
                <select name="extraData">
                    <option value="">[None]</option>
                    <%
                        for (Visit.Type type : Visit.Type.values())
                        {
                            String selected = (visit.getType() == type ? "selected" : "");
                            %>
                            <option value="<%= type.getCode() %>" <%= text(selected) %>><%= h(type.getMeaning()) %></option>
                            <%
                        }
                    %>
                </select>
            </td>
            <td>
                <input type="checkbox" name="visible" <%= text(visit.isShowByDefault() ? "Checked" : "") %> value="<%= visit.getRowId() %>">
                <input type="hidden" name="ids" value="<%= visit.getRowId() %>">
            </td>
        </tr>
    <%
        }
    %>
    </table>
    <%= generateSubmitButton("Save") %>&nbsp;<%= generateButton("Cancel", StudyController.ManageVisitsAction.class)%>
</form>
