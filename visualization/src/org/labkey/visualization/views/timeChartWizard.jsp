<%
/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.visualization.VisualizationController" %>
<%@ page import="org.labkey.api.reports.permissions.ShareReportPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<VisualizationController.GetVisualizationForm> me = (JspView<VisualizationController.GetVisualizationForm>) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    VisualizationController.GetVisualizationForm form = me.getModelBean();
    boolean canEdit = false;
    boolean canShare = ctx.hasPermission(ShareReportPermission.class);
    ReportIdentifier id = form.getReportId();
    Report report = null;

    if (id != null)
    {
        report = id.getReport();
        if (report != null)
            canEdit = report.canEdit(ctx.getUser(), ctx.getContainer());
    }
    else
    {
        canEdit = ctx.hasPermission(ReadPermission.class) && ! ctx.getUser().isGuest();
    }

    String elementId = "vis-wizard-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<script type="text/javascript">
    LABKEY.requiresExt4ClientAPI(true);
    LABKEY.requiresVisualization();
    LABKEY.requiresScript("vis/timeChartPanel.js");
</script>

<script type="text/javascript">
    Ext4.onReady(function(){
        // if the URL is requesting a report by Id, but it does not exist, display an error message
        if (<%= id != null && report == null %>)
        {
            Ext4.get('<%=elementId%>').update("<span class='labkey-error'>No report for id <%=id%>.</span>");
        }
        else
        {
            showTimeChartWizard({
                reportId: '<%=id != null ? id.toString() : ""%>',
                elementId: '<%=elementId%>',
                success: viewSavedChart
            });
        }
    });

    function showTimeChartWizard(config)
    {
        // get the type information from the server
        LABKEY.Visualization.getTypes({
            successCallback : function(types){storeVisualizationTypes(types, config);},
            failureCallback : function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            scope: this
        });
    }

    var viewTypes = {};
    function storeVisualizationTypes(types, config) {
        // store the type information
        for (var i=0; i < types.length; i++)
        {
            var type = types[i];
            viewTypes[type.type] = type;
        }

        // see if the wizard is being accessed with a saved visualization referenced on the URL
        if(LABKEY.Visualization.getFromUrl(config)) {
            // we have a saved chart being access, viewSavedChart will be called
        }
        else {
            // no saved visualization to show, so just initialize the wizard without a pre-selected measure
            initializeTimeChartPanel(config);
        }
    }

    function viewSavedChart(result, response, options){
        if(result.type == LABKEY.Visualization.Type.TimeChart){
            var saveReportInfo = {
                name: result.name,
                description: result.description,
                queryName: result.queryName,
                schemaName: result.schemaName,
                shared: result.shared,
                ownerId: result.ownerId,
                createdBy: result.createdBy
            };

            initializeTimeChartPanel(response.initialConfig, result.visualizationConfig, saveReportInfo);
        }
        else {
            Ext4.Msg.alert("Error", "The saved chart is not of type = TimeChart");
        }
    }

    function initializeTimeChartPanel(config, chartInfo, saveReportInfo) {
        // create a new chart panel and insert into the wizard
        var panel = Ext4.create('Ext.panel.Panel', {
            renderTo: config.elementId,
            height: 650,
            resizable: false,
            layout: 'border',
            frame: false,
            border: false,
            items: [
                new LABKEY.vis.TimeChartPanel({
                    region: 'center',
                    layout: 'border',
                    flex: 1,
                    border: false,
                    viewInfo: viewTypes['line'],
                    chartInfo: chartInfo,
                    saveReportInfo: saveReportInfo,
                    canEdit: <%=canEdit%>,
                    canShare: <%=canShare%>
                })
            ]
        });
    }
</script>

<div id="<%=elementId%>" class="extContainer" style="width:100%"></div>
