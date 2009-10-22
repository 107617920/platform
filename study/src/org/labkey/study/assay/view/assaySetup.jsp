<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.study.assay.AssayManager" %>
<%@ page import="org.labkey.api.study.permissions.DesignAssayPermission" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getViewContext().getContainer();
    User user = getViewContext().getUser();

    boolean hasAssayProtocols = AssayManager.get().hasAssayProtocols(c);
    boolean canDesignAssays = c.hasPermission(user, DesignAssayPermission.class);

    PipelineService pipeService = PipelineService.get();
    boolean hasPipelineRoot = pipeService.hasValidPipelineRoot(c);
    boolean canSetPipelineRoot = user.isAdministrator();
%>

<% if (!hasAssayProtocols) { %>
<em>No assay designs are available in this folder.</em>
<% if (canDesignAssays) { %>
    <labkey:link href="<%=new ActionURL(AssayController.ChooseAssayTypeAction.class, c)%>" text="New Assay Design"/>
<% } %>
<p class="labkey-indented">
    Each assay type provides pages to load and process data for a particular assay.
    Before using an assay type a new assay design must be created.
    The assay design specifies any custom information you would like to load with each assay batch, run, or result.
<p class="labkey-indented">
    <% if (canDesignAssays) { %>
        For more information about assays, please see the <a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=adminAssays">assay administrator</a> help topic.
    <% } else { %>
        Please ask an administrator for assistance in creating a new assay design.<br>
        For more information about assays, please see the <a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=assayUserGuide">assay user</a> help topic.
    <% } %>
<% } %>

<% if (!hasPipelineRoot) { %>
<p>
    <em>Pipeline root has not been set.</em>
    <% if (canSetPipelineRoot) { %>
        <labkey:link href="<%=PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c).getLocalURIString()%>" text="setup pipeline"/>
    <% } else { %>
        Please ask an administrator for assistance.
    <% } %>
<p class="labkey-indented">
    Assay data cannot be uploaded until you configure a <a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=pipelineSetup">pipeline root</a>
    directory that will contain the files uploaded as part of assays.
<% } %>
