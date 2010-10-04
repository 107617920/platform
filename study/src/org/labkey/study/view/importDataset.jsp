<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<StudyController.ImportDataSetForm> me = (JspView<StudyController.ImportDataSetForm>) HttpView.currentView();
    StudyController.ImportDataSetForm form = me.getModelBean();
%>

<labkey:errors/>

<form action="showImportDataset.post" method=POST>
    <%=PageFlowUtil.generateSubmitButton("Import Data")%>
    <%=PageFlowUtil.generateButton("Cancel", "javascript:{}", "window.history.back();")%>
    <table width="100%">
    <tr><td class=labkey-form-label width=150>Type URI</td><td><%=h(form.getTypeURI())%><input type=hidden name="typeURI" value="<%=h(form.getTypeURI())%>"></td></tr>
    <tr><td class=labkey-form-label width=150>Key Fields</td><td><%=h(form.getKeys())%><input type=hidden name="keys" value="<%=h(form.getKeys())%>"></td></tr>
        <tr><td class=labkey-form-label width=150 >Tab delimited data (TSV)</td>
        <td><%=textLink("template spreadsheet", "template.view?datasetId=" + form.getDatasetId())%>
        </td></tr>
        <tr><td colspan=2 width="100%">
            <textarea id=tsv name=tsv rows=25 cols=80 wrap=off style="width:100%;"><%=h(form.getTsv())%></textarea>
            <script type="text/javascript">
                Ext.EventManager.on('tsv', 'keydown', handleTabsInTextArea);
            </script>
        </td></tr>
    </table>
	<input type=hidden name=datasetId value="<%=form.getDatasetId()%>">


    <br>&nbsp;
    <p />
    <div id=columnMap>
    </div>
</form>
