<%
/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.study.assay.ModuleAssayProvider.ResultDetailsBean" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.study.assay.ModuleAssayProvider" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.study.controllers.assay.actions.AbstractAssayAPIAction" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.json.JSONArray" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ModuleAssayProvider.ResultDetailsBean> me = (JspView<ModuleAssayProvider.ResultDetailsBean>) HttpView.currentView();
    ModuleAssayProvider.ResultDetailsBean bean = me.getModelBean();
    ModuleAssayProvider provider = bean.provider;
    ExpProtocol protocol = bean.expProtocol;
    ExpData data = bean.expData;

    Map<String, Object> assay = AssayController.serializeAssayDefinition(bean.expProtocol, bean.provider, getViewContext().getContainer(), getViewContext().getUser(), null);
    JSONArray dataRows = AbstractAssayAPIAction.serializeDataRows(data, provider, protocol, getViewContext().getUser(), bean.objectId);
    JSONObject result = dataRows.length() > 0 ? (JSONObject)dataRows.get(0) : new JSONObject();
%>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>
<script type="text/javascript">
LABKEY.page = LABKEY.page || {};
LABKEY.page.assay = <%= new JSONObject(assay).toString(2) %>;
LABKEY.page.result = <%= result.toString(2) %>;
</script>
<p>
<%
    if (me.getView("nested") == null)
        throw new IllegalStateException("expected nested view");
    me.include(me.getView("nested"), out);
%>
