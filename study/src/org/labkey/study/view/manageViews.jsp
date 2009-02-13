<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.controllers.reports.StudyManageReportsBean" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.api.reports.report.view.RReportBean" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.study.query.DataSetQueryView" %>
<%@ page import="org.labkey.study.reports.StudyQueryReport" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>

<script type="text/javascript">
    LABKEY.requiresExtJs(true);
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("reports/manageViews.js");
</script>

<%
    JspView<StudyManageReportsBean> me = (JspView<StudyManageReportsBean>) HttpView.currentView();
    StudyManageReportsBean bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();

    ActionURL permissionURL = new ActionURL(SecurityController.ReportPermissionsAction.class, context.getContainer());

    RReportBean reportBean = new RReportBean();
    reportBean.setReportType(RReport.TYPE);
    reportBean.setRedirectUrl(context.getActionURL().getLocalURIString());

    ActionURL newRView = ReportUtil.getRReportDesignerURL(context, reportBean);
%>

<script type="text/javascript">
    var panel;

    function securityRenderer(value, p, record)
    {
        var href = '<%=permissionURL.getLocalURIString()%>';

        href = href.concat('reportId=');
        href = href.concat(record.data.reportId);

        return "<a href='" + href + "'>" + value + "</a>";
    }

    function renderViews()
    {
        // subclass the views panel
        StudyViewsPanel = Ext.extend(LABKEY.ViewsPanel, {
            /**
             * Override and append a custom button
             */
            getButtons : function() {
                var buttons = StudyViewsPanel.superclass.getButtons.call(this);
                buttons.push('-', {text:'Customize Participant View', listeners:{click:function(button, event) {window.location = '<%=bean.getCustomizeParticipantViewURL()%>';}}}, '-');

                return buttons;
            },

            convertQuery : function(schema, query, view) {
                var params = [];

                params.push("schemaName=" + schema);
                params.push("queryName=" + query);
                params.push("viewName=" + view);
                params.push("<%=QueryParam.dataRegionName.toString()%>" + "=", "<%=DataSetQueryView.DATAREGION%>");
                params.push("reportType=", "<%=StudyQueryReport.TYPE%>");

                Ext.Msg.show({
                        title : 'Convert Query',
                        msg : "Are you sure you want to convert the query view into a report query view?",
                        buttons: Ext.Msg.YESNO,
                        icon: Ext.Msg.QUESTION,
                        fn: function(btn, text) {
                            if (btn == 'yes')
                            {
                                Ext.Ajax.request({

                                    url: LABKEY.ActionURL.buildURL("study-reports", "convertQueryToReport"), // + '?' + params.join('&'),
                                    method: "POST",
                                    scope: this,
                                    params: {
                                        schemaName: schema,
                                        queryName: query,
                                        viewName: view,
                                        reportType: '<%=StudyQueryReport.TYPE%>',
                                        '<%=QueryParam.dataRegionName%>': '<%=DataSetQueryView.DATAREGION%>'
                                    },
                                    success: function(){panel.grid.store.load();},
                                    failure: function(){Ext.Msg.alert("Convert Query", "Conversion Failed");}
                                });
                            }},
                        id: 'convert_queryView'
                });
            }
        });

        panel = new StudyViewsPanel({
            renderTo: 'viewsGrid',
            container: '<%=context.getContainer().getPath()%>',
            expander : new Ext.grid.RowExpander({
                tpl : new Ext.XTemplate(
                        '<table>',
                            '<tpl if="description != undefined"><tr><td><b>description</b></td><td>{description}</td></tr></tpl>',
                            '<tr><td><b>query name</b></td><td>{query}</td></tr>',
                            '<tpl if="schema != undefined"><tr><td><b>schema name</b></td><td>{schema}</td></tr></tpl>',
                            '<tr><td><b>permissions</b></td><td>{permissions}</td>',
                            '<tr><td><b>links</b></td><td>',
                                '<tpl if="runUrl != undefined">&nbsp;[<a href="{runUrl}">display</a>]</tpl>',
                                '<tpl if="editUrl != undefined">&nbsp;[<a href="{editUrl}">edit</a>]</tpl>',
                                '<tpl if="reportId != undefined">&nbsp;[<a href="<%=permissionURL.getLocalURIString()%>reportId={reportId}">permissions</a>]</tpl>',
                                '<tpl if="reportId == undefined">&nbsp;[<a href=\'#\' onclick=\'panel.convertQuery("{schema}","{query}","{name}");return false;\'>convert to report</a>]</tpl></td></tr>',
                        '</table>')
                }),
            createMenu :[{
                id: 'create_rView',
                text:'New R View',
                listeners:{click:function(button, event) {window.location = '<%=newRView.getLocalURIString()%>';}}
            },{
                id: 'create_gridView',
                text:'New Grid View',
                listeners:{click:function(button, event) {window.location = '<%=new ActionURL(ReportsController.CreateQueryReportAction.class, context.getContainer())%>';}}
            },{
                id: 'create_crosstabView',
                text:'New Crosstab View',
                listeners:{click:function(button, event) {window.location = '<%=new ActionURL(ReportsController.CreateCrosstabReportAction.class, context.getContainer())%>';}}
            },{
                id: 'create_exportXlsView',
                text:'New Export to Workbook (.xls)',
                listeners:{click:function(button, event) {window.location = '<%=new ActionURL(ReportsController.ExportExcelConfigureAction.class, context.getContainer())%>';}}
            },{
                id: 'create_staticView',
                text:'New Static View',
                listeners:{click:function(button, event) {window.location = '<%=new ActionURL(ReportsController.ShowUploadReportAction.class, context.getContainer())%>';}}
            }
            ]
        });
        panel.show();
    }

    Ext.onReady(function(){renderViews();});
</script>

<div id="viewsGrid" class="extContainer"></div>
