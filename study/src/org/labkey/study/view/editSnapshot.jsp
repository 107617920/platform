<%
/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.BooleanUtils" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotDefinition" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<QuerySnapshotForm> me = (JspView<QuerySnapshotForm>) HttpView.currentView();
    QuerySnapshotForm bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(context.getContainer(), bean.getSchemaName(), bean.getSnapshotName());

    boolean showHistory = BooleanUtils.toBoolean(context.getActionURL().getParameter("showHistory"));
    String historyLabel = showHistory ? "Hide History" : "Show History";

    boolean showDataset = BooleanUtils.toBoolean(context.getActionURL().getParameter("showDataset"));
    String datasetLabel = showDataset ? "Hide Dataset Definition" : "Edit Dataset Definition";

    final Study study = StudyManager.getInstance().getStudy(context.getContainer());
    final DataSet dsDef = StudyManager.getInstance().getDataSetDefinitionByName(study, bean.getSnapshotName());
    ActionURL deleteSnapshotURL = new ActionURL(StudyController.DeleteDatasetAction.class, context.getContainer());
%>

<%  if (def != null) { %>
<table>
    <tr><td class="labkey-form-label">Name</td><td><%=h(def.getName())%></td>
    <tr><td class="labkey-form-label">Created By</td><td><%=h(def.getCreatedBy())%></td>
    <tr><td class="labkey-form-label">Modified By</td><td><%=h(def.getModifiedBy())%></td>
    <tr><td class="labkey-form-label">Created</td><td><%=h(def.getCreated())%></td>
    <tr><td class="labkey-form-label">Last Updated</td><td><%=StringUtils.trimToEmpty(DateUtil.formatDateTime(def.getLastUpdated()))%></td>
    <tr><td class="labkey-form-label">Query Source</td><td><textarea rows="20" cols="65" readonly="true"><%=def.getQueryDefinition(context.getUser()).getSql()%></textarea></td>
</table>
<%  } %>

<form action="" method="post" onsubmit="return confirm('Updating will replace all existing data with a new set of data. Continue?');">
    <input type="hidden" name="updateSnapshot" value="true">
    <table>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td><%=generateSubmitButton("Update Snapshot")%></td>
<%      if (def != null && dsDef != null) { %>
<%--
            <td><%=generateButton("Edit Query Source", bean.getSchema().urlFor(QueryAction.sourceQuery, def.getQueryDefinition()))%></td>
--%>
            <td><%=generateButton(historyLabel, context.cloneActionURL().replaceParameter("showHistory", String.valueOf(!showHistory)))%></td>
            <td><%=generateButton(datasetLabel, context.cloneActionURL().replaceParameter("showDataset", String.valueOf(!showDataset)))%></td>
            <td><%=generateButton("Delete Snapshot", deleteSnapshotURL.addParameter("id", dsDef.getDataSetId()),
                    "return confirm('Are you sure you want to delete this snapshot?  All related data will also be deleted.')")%></td>
<%      } %>
        </tr>
    </table>
</form>
