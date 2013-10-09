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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.exp.api.ExpExperiment" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.view.NotFoundException" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page import="org.labkey.study.controllers.assay.actions.AbstractAssayAPIAction" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.exp.api.AssayJSONConverter" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    protected ExpExperiment lookupBatch(int batchId)
    {
        if (batchId == 0)
            return null;
        ExpExperiment batch = ExperimentService.get().getExpExperiment(batchId);
        if (batch == null)
        {
            throw new NotFoundException("Could not find assay batch " + batchId);
        }
        if (!batch.getContainer().equals(getViewContext().getContainer()))
        {
            throw new NotFoundException("Could not find assay batch " + batchId + " in folder " + getViewContext().getContainer());
        }
        return batch;
    }
%>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm bean = me.getModelBean();
    AssayProvider provider = bean.getProvider();
    ExpProtocol protocol = bean.getProtocol();
    int batchId = bean.getBatchId() == null ? 0 : bean.getBatchId().intValue();

    Map<String, Object> assay = AssayController.serializeAssayDefinition(protocol, provider, getViewContext().getContainer(), getViewContext().getUser());
%>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>
<script type="text/javascript">
LABKEY.page = LABKEY.page || {};
LABKEY.page.assay = <%= new JSONObject(assay).toString(2) %>;
<%
 if (batchId > 0)
 {
    ExpExperiment batch = lookupBatch(batchId);
    JSONObject batchJson = AssayJSONConverter.serializeBatch(batch, provider, protocol, me.getViewContext().getUser());
    %>LABKEY.page.batch = new LABKEY.Exp.RunGroup(<%=batchJson.toString(2)%>);<%
 }
 else
 {
    %>LABKEY.page.batch = new LABKEY.Exp.RunGroup();<%
 }
%>
LABKEY.page.batch.batchProtocolId = <%= protocol.getRowId() %>;
LABKEY.page.batch.loaded = true;
</script>
<p>
<%
    if (me.getView("nested") == null)
        throw new IllegalStateException("expected nested view");
    me.include(me.getView("nested"), out);
%>
</p>
