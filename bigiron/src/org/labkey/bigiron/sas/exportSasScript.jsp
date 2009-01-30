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
%><%@ page import="org.labkey.api.view.JspView" %><%@ page import="org.labkey.api.view.HttpView" %><%@ page import="org.labkey.bigiron.sas.ExportSasScriptModel" %><%
    JspView<ExportSasScriptModel> me = (JspView<ExportSasScriptModel>) HttpView.currentView();
    ExportSasScriptModel model = me.getModelBean();
    me.getViewContext().getResponse().setContentType("text/plain");
%>/*
 * SAS Script generated by <%=model.getInstallationName()%> on <%=model.getCreatedOn()%>
 * This script makes use of the SAS/LabKey macros and jar files that must be configured
 * on your SAS installation.  SAS/LabKey can be obtained via the LabKey Software Foundation
 * distribution and support site, https://www.labkey.org.
 */

/*  Select rows into a data set called 'mydata' */

%selectRows(dsn=mydata,
            baseUrl="<%=model.getBaseUrl()%>",
            folderPath="<%=model.getFolderPath()%>",
            schemaName="<%=model.getSchemaName()%>",
            queryName="<%=model.getQueryName()%>"<% if (null != model.getViewName()) {%>,
            viewName="<%=model.getViewName()%>"<% } if (null != model.getSort()) {%>,
            colSort=<%=model.getSort()%><% } if (null != model.getFilters()) {%>,
            colFilter=<%=model.getFilters()%><% } %>);
