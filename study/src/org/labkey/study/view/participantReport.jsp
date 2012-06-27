<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController.ParticipantReportForm" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.reports.permissions.ShareReportPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<org.labkey.study.controllers.reports.ReportsController.ParticipantReportForm> me = (JspView<ParticipantReportForm>) HttpView.currentView();
    ParticipantReportForm bean = me.getModelBean();
    String reportId = null;

    if (bean.getReportId() != null)
        reportId = bean.getReportId().toString();

    Container c = me.getViewContext().getContainer();
    User user = me.getViewContext().getUser();
    Study s = StudyManager.getInstance().getStudy(c);

    String renderId = "participant-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String filterRenderId = "participant-filter-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<style type="text/css" media="print">
    #headerpanel,
    div.labkey-app-bar,
    #discussionMenuToggle,
    .labkey-wp-title-left,
    .labkey-wp-title-right,
    .report-filter-window-outer,
    .report-config-panel,
    .report-toolbar
    {
        display: none;
    }

    td.lk-report-subjectid {
        padding-bottom  : 5px;
        font-weight     : bold;
        font-size       : 13pt;
        text-align      : left;
    }

    td.report-cell {font-size: 11pt;}
    td.lk-report-cell {font-size: 11pt;}
    td.lk-report-column-header, th.lk-report-column-header {font-size: 12pt;}

    table.labkey-wp {border: none !important;}
    .x4-reset .x4-panel-body-default {border:none !important;}

</style>

<style type="text/css" media="screen">

    td.lk-report-subjectid {
        padding-bottom  : 10px;
        font-weight     : bold;
        font-size       : 1.3em;
        text-align      : left;
    }
</style>

<script type="text/javascript">
    LABKEY.requiresClientAPI(); // required for LABKEY.Visualization
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresScript("TemplateHelper.js");
    LABKEY.requiresScript("study/ParticipantReport.js");
    LABKEY.requiresScript("study/MeasurePicker.js");
</script>

<script type="text/javascript">

    Ext4.onReady(function(){
        var panel = Ext4.create('LABKEY.ext4.ParticipantReport', {
            height          : 600,
            subjectColumn   : <%=q(org.labkey.api.study.StudyService.get().getSubjectColumnName(me.getViewContext().getContainer()))%>,
            subjectVisitColumn: <%=q(org.labkey.api.study.StudyService.get().getSubjectVisitColumnName(me.getViewContext().getContainer()))%>,
            subjectNoun     : {singular : <%=PageFlowUtil.jsString(s.getSubjectNounSingular())%>, plural : <%=PageFlowUtil.jsString(s.getSubjectNounPlural())%>, columnName: <%=PageFlowUtil.jsString(s.getSubjectColumnName())%>},
            visitBased      : <%=s.getTimepointType().isVisitBased()%>,
            renderTo        : '<%= renderId %>',
            filterDiv       : '<%=filterRenderId%>',
            id              : '<%= bean.getComponentId() %>',
            reportId        : <%=q(reportId)%>,
            allowCustomize  : true,
            allowShare      : <%=c.hasPermission(user, ShareReportPermission.class)%>,
            hideSave        : <%=user.isGuest()%>,
            fitted          : <%=bean.isExpanded()%>,
            openCustomize   : true,
            allowOverflow   : <%=bean.isAllowOverflow()%>
        });

        var _resize = function(w,h) {
            LABKEY.Utils.resizeToViewport(panel, w, -1); // don't fit to height
        }

        if (<%=!bean.isAllowOverflow()%>)
            Ext4.EventManager.onWindowResize(_resize);
    });

    function customizeParticipantReport(elementId) {

        function initPanel() {
            var panel = Ext4.getCmp(elementId);

            if (panel) { panel.customize(); }
        }
        Ext4.onReady(initPanel);
    }

</script>

<div id="<%=filterRenderId%>" class="report-filter-window-outer" style="position:<%=bean.isAllowOverflow() ? "absolute" : "absolute"%>;"></div>
<div id="<%= renderId%>" class="data-views-container" style="width:100%"></div>

