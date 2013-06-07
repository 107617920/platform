<%
/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.util.DateUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ page import="org.labkey.query.reports.ReportsController.AttachmentReportForm" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        resources.add(ClientDependency.fromFilePath("study/DataViewsPanel.css"));
        resources.add(ClientDependency.fromFilePath("study/DataViewUtil.js"));
        resources.add(ClientDependency.fromFilePath("study/DataViewPropertiesPanel.js"));
        return resources;
    }
%>
<%
    JspView<ReportsController.AttachmentReportForm> me = (JspView<ReportsController.AttachmentReportForm>) HttpView.currentView();
    ReportsController.AttachmentReportForm form = me.getModelBean();
    boolean canUseDiskFile;

    if (form.isUpdate())
    {
        canUseDiskFile = HttpView.currentContext().getUser().isAdministrator();
    }
    else
    {
        canUseDiskFile = HttpView.currentContext().getUser().isAdministrator() && form.getReportId() == null;
    }

    String action = (form.isUpdate() ? "update" : "create") + "attachmentReport";
%>

<table>
    <%=formatMissedErrorsInTable("form", 1)%>
</table>

<div id="attachmentReportForm">
</div>

<script type="text/javascript">

    function selectFileUploadItem(fileUploadItems, attachmentType)
    {
        var item;

        for (var i =0; i < fileUploadItems.length; i++)
        {
            item = fileUploadItems[i];
            if (item.inputValue == attachmentType)
            {
                item.checked = true;
                item.inputField.setVisible(true);
                item.inputField.setDisabled(false);
            }
            else
            {
                // be sure to turn off the other items in the list
                item.checked = false;
                item.inputField.setVisible(false);
                item.inputField.setDisabled(true);
            }
        }
    }

    function getReturnUrl()
    {
        var returnUrl = LABKEY.ActionURL.getParameter('returnUrl');
        return (undefined == returnUrl ? "" : returnUrl);
    }

    Ext4.onReady(function(){

        var fileUploadButton = Ext4.create('Ext.form.field.File', {
            name: 'uploadFile',
            id: 'uploadFile',
            buttonOnly : true,
            buttonText: <%=q(form.isUpdate() ? "Edit..." : "Browse...")%>,
            hideEmptyLabel: false,
            listeners: {
                scope: this,
                change: function(field, fileName) {
                    fileUploadTextField.setValue(fileName);
                }
            }
        });

        var extraItems;
        var fileUploadTextField = Ext4.create('Ext.form.field.Text', {
            name: "localFilePath",
            fieldLabel: "Choose a file",
            allowBlank: false,
            readOnly: true,
            listeners: {
                scope: this,
                enable: function (field) {
                    fileUploadButton.setVisible(true);
                },
                disable: function (field) {
                    // disable the fileUploadButton as well
                    fileUploadButton.setVisible(false);
                }
            }
        });

        <% if (canUseDiskFile) { %>
        var serverFileTextField = Ext4.create('Ext.form.field.Text', {
            name: "filePath",
            hidden: true,
            disabled: true,
            fieldLabel: "Path on server",
            allowBlank: false
        });

        var fileUploadRadioGroup = {
            xtype: 'radiogroup',
            fieldLabel: 'Attachment Type',
            columns: 1,
            items: [{
                boxLabel: 'Upload file to server',
                name: 'attachmentType',
                inputValue: <%=q(AttachmentReportForm.AttachmentReportType.local.toString())%>,
                checked: true,
                inputField: fileUploadTextField
            },{
                boxLabel: 'Full file path on server',
                name: 'attachmentType',
                inputValue: <%=q(AttachmentReportForm.AttachmentReportType.server.toString())%>,
                inputField: serverFileTextField
            }],
            listeners: {
                scope: this,
                change: function (field, newVal, oldVal, opts) {
                    var value = newVal['attachmentType'];
                    field.items.each(function (item) {
                        if (item.inputValue === value) {
                            item.inputField.setVisible(true);
                            item.inputField.setDisabled(false);
                        } else {
                            item.inputField.setVisible(false);
                            item.inputField.setDisabled(true);
                        }
                    });
                }
            }
        };

        extraItems = [ fileUploadRadioGroup, fileUploadTextField, fileUploadButton, serverFileTextField ];
        <% } else { %>

        var attachmentTypeField = {
            xtype:'hidden',
            name:'attachmentType',
            value:<%=q(AttachmentReportForm.AttachmentReportType.local.toString())%>
        };
        extraItems = [ attachmentTypeField, fileUploadTextField, fileUploadButton ];
        <% } %>

       <% if (form.isUpdate()) { %>
            var attachmentType = <%=q(form.getAttachmentType().toString())%>;
            var serverFilePath = <%=q(form.getFilePath())%>;
            var uploadFileName = <%=q(form.getUploadFileName())%>;

            // if the user is an admin then they can either upload a file from their local machine
            // or reference a file that is on the server
            <% if (canUseDiskFile) { %>
                selectFileUploadItem(fileUploadRadioGroup.items, attachmentType);
            <% } else { %>
                fileUploadTextField.setVisible(true);
                fileUploadTextField.setDisabled(false);
            <% } %>

            // now set the data.  If it is a server attachment type then set the serverFilePath
            // outherwise just set the uploadFileName
            if (attachmentType == <%=q(form.getAttachmentType().server.toString())%>) {
                serverFileTextField.setValue(serverFilePath);
            } else {
                fileUploadTextField.setValue(uploadFileName);
            }

        extraItems.push({
            xtype: "hidden",
            name: "reportId",
            value: <%=q(form.getReportId().toString())%>
         });
    <% } %>

        var form = Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            url : LABKEY.ActionURL.buildURL('reports',  <%=q(action)%>, null, {returnUrl: getReturnUrl()}),
            standardSubmit  : true,
            bodyStyle       :'background-color: transparent;',
            bodyPadding     : 10,
            border          : false,
            buttonAlign     : "left",
            width           : 575,
            fieldDefaults: {
                width : 500,
                labelWidth : 125,
                msgTarget : 'side'
            },
            visibleFields   : {
                author  : true,
                status  : true,
                datacutdate : true,
                category    : true,
                description : true,
                shared      : true
            },
            disableShared   : <%=(form.getCanChangeSharing()==false)%>,
        <% if (form.isUpdate()) { %>
            record : {
                data : {
                    name: <%=q(form.getViewName())%>,
                    authorUserId: <%=form.getAuthor()%>,
                    status: <%=q(form.getStatus().name())%>,
                    refreshDate: <%=q(DateUtil.formatDate(form.getRefreshDate()))%>,
                    category: {rowid : <%=form.getViewCategory() != null ? form.getViewCategory().getRowId() : null%>},
                    description: <%=q(form.getDescription())%>,
                    shared: <%=form.getShared()%>
                }
            },
        <% } %>
            extraItems : extraItems,
            renderTo    : 'attachmentReportForm',
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [{
                    text : 'Save',
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid())
                            form.submit();
                    },
                    scope   : this
                },{
                    text: 'Cancel',
                    handler: function(){
                        if(LABKEY.ActionURL.getParameter('returnUrl')){
                            window.location = LABKEY.ActionURL.getParameter('returnUrl');
                        } else {
                            window.location = LABKEY.ActionURL.buildURL('reports', 'manageViews');
                        }
                    }
                }]
            }]
        });
    });
</script>
