<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.ProjectSettingsForm> me = (JspView<AdminController.ProjectSettingsForm>) HttpView.currentView();
    AdminController.ProjectSettingsForm form = me.getModelBean();
    Container c = me.getViewContext().getContainer();

    String contextPath = me.getViewContext().getContextPath();
    String projectDefaultRoot = "";

    File siteRoot = ServiceRegistry.get().getService(FileContentService.class).getSiteDefaultRoot();
    if (siteRoot != null)
    {
        File projRoot = new File(siteRoot, getViewContext().getContainer().getProject().getName());
        if (projRoot != null)
        {
            // Show the user the path that we'd point to if using the default location
            projectDefaultRoot = projRoot.getAbsolutePath();
        }
    }
%>
<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresCss('/extWidgets/ext4.css');
    LABKEY.requiresCss('/createFolder.css');
</script>
<script type="text/javascript">
    Ext4.onReady(function(){
        Ext4.FocusManager.enable(false);
        var projectDefault = "<%=h(projectDefaultRoot.replace("\\", "\\\\"))%>";

        var panel = Ext4.create('Ext.form.Panel', {
            border: false,
            autoHeight: true,
            defaults: {
                border: false
            },
            url: 'setInitialFolderSettings.view',
            method: 'POST',
            standardSubmit: true,
            items: [{
                html: 'Choose File Location:',
                style: 'padding-bottom: 5px;'
            },{
                xtype: 'radiogroup',
                //name: 'fileLocation',
                width: 400,
                columns: 2,
                listeners: {
                    change: {
                        fn: function(btn, newVal, oldVal){
                            var form = btn.up('form');
                            var field = form.child('#fileLocation');
                            field.setDisabled(newVal.fileRootOption=='default');
                            field.reset();
                        },
                        buffer: 20,
                        scope: this
                    },
                    render: function(field){
                        field.focus('', 10);
                    }
                },
                defaults: {
                    border: false,
                    width: 175
                },
                items: [{
                    boxLabel: 'Use Default',
                    checked: true,
                    name: 'fileRootOption',
                    inputValue: 'default'
                },{
                    xtype: 'radio',
                    boxLabel: 'Custom Location',
                    checked: false,
                    name: 'fileRootOption',
                    inputValue: 'custom'
                }]
            },{
                xtype: 'textfield',
                cls: 'labkey-wizard-input',
                itemId: 'fileLocation',
                name: 'projectRootPath',
                disabled: true,
                width: 400,
                fieldCls: 'labkey-wizard-input',
                value: projectDefault
            },{
                style: 'padding-bottom: 20px;'
            },{
                html: 'Advanced Settings:',
                style: 'padding-bottom: 5px;'
            },{
                defaults: {
                    border: false,
                    style: 'padding: 5px 5px 5px 10px;'
                },
                items: [{
                    html: '<li><a href="'+LABKEY.ActionURL.buildURL('admin', 'projectSettings.view') + '" target="_blank">Properties</a></li>'
                },{
                    html: '<li><a href="'+LABKEY.ActionURL.buildURL('admin', 'projectSettings.view', null, {tabId: 'resources'}) + '" target="_blank">Resources</a></li>'
                },{
                    html: '<li><a href="'+LABKEY.ActionURL.buildURL('admin', 'projectSettings.view', null, {tabId: 'menubar'}) + '" target="_blank">Menu Bar</a></li>'
                },{
                    html: '<li><a href="'+LABKEY.ActionURL.buildURL('admin', 'projectSettings.view', null, {tabId: 'files'}) + '" target="_blank">Folder Settings</a></li>'
                },{
                    html: '<li><a href="'+LABKEY.ActionURL.buildURL('security', 'project.view') + '" target="_blank">Configure Permissions</a></li>'
                }]
            }],
            buttons: [{
                xtype: 'button',
                cls: 'labkey-button',
                text: 'Finish',
                handler: function(btn){
                    var f = btn.up('form').getForm();
                    f.submit();
                }
            }]
        }).render('setInitialFolderSettingsDiv');

        Ext4.create('Ext.util.KeyNav', Ext4.getBody(), {
            scope: this,
            enter: function(){
                var f = panel.getForm();
                f.submit();
            }
        });

    });
</script>

<%=formatMissedErrors("form")%>
<p></p>
<div id="setInitialFolderSettingsDiv"></div>

