<%
/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.survey.SurveyForm" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.survey.model.Survey" %>
<%@ page import="org.labkey.survey.SurveyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        resources.add(ClientDependency.fromFilePath("/survey/BaseSurveyPanel.js"));
        resources.add(ClientDependency.fromFilePath("/survey/SurveyPanel.js"));
        return resources;
    }
%>
<%
    JspView<SurveyForm> me = (JspView<SurveyForm>) HttpView.currentView();
    SurveyForm bean = me.getModelBean();
    ViewContext ctx = me.getViewContext();

    Integer rowId = 0;
    Integer surveyDesignId = null;
    String responsesPk = null;
    String surveyLabel = null;
    boolean submitted = false;
    String returnURL = null;
    if (bean != null)
    {
        if (bean.getRowId() != null)
            rowId = bean.getRowId();

        surveyDesignId = bean.getSurveyDesignId();
        responsesPk = bean.getResponsesPk();
        surveyLabel = bean.getLabel();
        submitted = bean.isSubmitted();
        returnURL = bean.getSrcURL() != null ? bean.getSrcURL().toString() : null;
    }

    Survey survey = SurveyManager.get().getSurvey(ctx.getContainer(), ctx.getUser(), rowId);
    boolean locked = survey != null && SurveyManager.get().getSurveyLockedStates().indexOf(survey.getStatus()) > -1;

    // we allow editing for 1) non-submitted surveys 2) submitted surveys (that are not locked) if the user is a project or site admin
    Container project = ctx.getContainer().getProject();
    boolean isAdmin = (project != null && project.hasPermission(ctx.getUser(), AdminPermission.class)) || ctx.getUser().isAdministrator();
    boolean canEdit = !locked && ((!submitted && ctx.getContainer().hasPermission(ctx.getUser(), UpdatePermission.class)) || isAdmin);

    String headerRenderId = "survey-header-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String formRenderId = "survey-form-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String footerRenderId = "survey-footer-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<%
    if (getErrors("form").hasErrors())
    {
        %><%=formatMissedErrors("form")%><%
    }
    else
    {
%>
<div id=<%=q(headerRenderId)%>></div>
<div id=<%=q(formRenderId)%>></div>
<div id=<%=q(footerRenderId)%>></div>
<script type="text/javascript">

    Ext4.onReady(function(){

        var panel = Ext4.create('LABKEY.ext4.SurveyDisplayPanel', {
            cls             : 'lk-survey-panel themed-panel',
            rowId           : <%=rowId%>,
            surveyDesignId  : <%=surveyDesignId%>,
            responsesPk     : <%=q(responsesPk)%>,
            surveyLabel     : <%=q(surveyLabel)%>,
            isSubmitted     : <%=submitted%>,
            canEdit         : <%=canEdit%>,
            renderTo        : <%=q(formRenderId)%>,
            headerRenderTo  : <%=q(headerRenderId)%>,
            footerRenderTo  : <%=q(footerRenderId)%>,
            returnURL       : <%=q(returnURL)%>,
            autosaveInterval: 60000
        });

    });

</script>
<%
    }
%>