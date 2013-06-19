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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
    JspView<StudyController.ChangeAlternateIdsForm> me = (JspView<StudyController.ChangeAlternateIdsForm>) HttpView.currentView();
    StudyController.ChangeAlternateIdsForm bean = me.getModelBean();
    Container c = me.getViewContext().getContainer();
    Study s = StudyManager.getInstance().getStudy(c);
    String subjectNounSingular = s.getSubjectNounSingular();
    String subjectNounPlural = s.getSubjectNounPlural();
    String subjectNounColName = s.getSubjectColumnName();
    int aliasDatasetId = bean.getAliasDatasetId();
    String aliasColumn = bean.getAliasColumn();
    String sourceColumn = bean.getSourceColumn();
    boolean isAdmin = c.hasPermission(getViewContext().getUser(), AdminPermission.class);
    Integer numberOfDigits = bean.getNumDigits() > 0 ? bean.getNumDigits() : 6;
%>
<div style="max-width: 1000px">
<h2>Alternate <%= PageFlowUtil.filter(subjectNounSingular)%> IDs</h2>
<p>Alternate <%= PageFlowUtil.filter(subjectNounSingular) %> IDs allow you to publish and export a study with all <%= PageFlowUtil.filter(subjectNounSingular.toLowerCase()) %> IDs
    replaced by randomly generated alternate IDs or by IDs you specify. Alternate IDs must be unique. The Change Alternate IDs button clears all alternate IDs, whether you had specified them or not.
    Random alternate IDs will then be generated automatically for all <%= PageFlowUtil.filter(subjectNounPlural.toLowerCase()) %> using
    the prefix and the number of digits specified below.
</p>
<p>
    Every <%= PageFlowUtil.filter(subjectNounSingular.toLowerCase()) %> is also given a date offset that is used when you publish or export a study, if you request date shifting. Date offsets never change.
    The Export button exports a TSV that contains the alternate ID and date offset for each <%= PageFlowUtil.filter(subjectNounSingular.toLowerCase()) %>.
    The Import button imports a TSV that contains an alternate ID and/or date offset for some or all <%= PageFlowUtil.filter(subjectNounPlural.toLowerCase()) %>.
</p>
</div>
<div id="alternateIdsPanel"></div>

<div style="max-width: 1000px">
<h2><%= PageFlowUtil.filter(subjectNounSingular)%> Aliases</h2>
<p>You may link <%= PageFlowUtil.filter(subjectNounPlural).toLowerCase()%> in this study with <%= PageFlowUtil.filter(subjectNounPlural).toLowerCase()%> from another source
    by specifying a dataset that contains aliases for each <%= PageFlowUtil.filter(subjectNounSingular).toLowerCase()%>.
    You must also specify which dataset columns contain the aliases and source organization names that use the aliases.
</p>
</div>
<div id="datasetMappingPanel"></div>

<div id="donePanel"></div>

<script type="text/javascript">

    (function(){

        var init = function()
        {
            Ext4.QuickTips.init();

            var prefixField = Ext4.create('Ext.form.field.Text', {
                fieldLabel: 'Prefix',
                labelSeparator: '',
                value: <%= q(bean.getPrefix())%>,
                width : 220,
                labelWidth: 130,
                maxLength: 20,
                enforceMaxLength: true
            });

            var digitsField = Ext4.create('Ext.form.field.Number', {
                fieldLabel: 'Number of Digits',
                name: 'numberOfDigits',
                labelSeparator: '',
                minValue: 6,
                maxValue: 10,
                value: <%= numberOfDigits%>,
                width : 220,
                labelWidth: 130,
                height: 28
            });

            var controls = [prefixField, digitsField];

            var form = Ext4.create('Ext.form.FormPanel', {
                renderTo: 'alternateIdsPanel',
                bodyPadding: 10,
                bodyStyle: 'background: none',
                frame: false,
                border: false,
                width: 600,
                buttonAlign : 'left',
                items: controls,
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui : 'footer',
                    style : 'background: none',
                    height : 30,
                    items: [{
                        xtype: 'button',
                        text: 'Change Alternate IDs',
                        handler: function() {changeAlternateIds(prefixField, digitsField);}
                    },{
                        xtype: 'button',
                        text: 'Export',
                        handler: function() {window.location = LABKEY.ActionURL.buildURL("study", "exportParticipantTransforms");}
                    },{
                        xtype: 'button',
                        text: 'Import',
                        handler: function() {window.location = LABKEY.ActionURL.buildURL("study", "importAlternateIdMapping.view");}
                    }]
                }]
            });

            var displayDoneChangingMessage = function(title, msg) {
                Ext4.MessageBox.show({
                    title: title,
                    msg: msg,
                    buttons: Ext4.MessageBox.OK,
                    icon: Ext4.MessageBox.INFO
                });
            };

            var changeAlternateIds = function(prefixField, digitsField) {
                Ext4.MessageBox.show({
                    title: "Change All Alternate IDs",
                    msg: "This action will change the Alternate IDs for all " + <%=q(subjectNounPlural)%> + " in this study. The Alternate IDs in future published studies will not match the Alternate IDs in previously published studies. Are you sure you want to change all Alternate IDs?",
                    buttons: Ext4.MessageBox.OKCANCEL,
                    icon: Ext4.MessageBox.WARNING,
                    fn : function(buttonID) {
                        if (buttonID == 'ok')
                        {
                            var preVal = prefixField.getValue();
                            var digVal = digitsField.getValue();
                            Ext4.Ajax.request({
                                url : (LABKEY.ActionURL.buildURL("study", "changeAlternateIds")),
                                method : 'POST',
                                success: function(){
                                    displayDoneChangingMessage("Change All Alternate IDs", "Changing Alternate IDs is complete.");
                                },
                                failure: function(response, options){
                                    LABKEY.Utils.displayAjaxErrorResponse(response, options, false, 'An error occurred:<br>');
                                },
                                jsonData : {prefix : preVal, numDigits : digVal},
                                headers : {'Content-Type' : 'application/json'},
                                scope: this
                            });
                        }
                    }
                });
            };

            var doneForm = Ext4.create('Ext.form.FormPanel', {
                renderTo: 'donePanel',
                bodyPadding: 10,
                bodyStyle: 'background: none',
                frame: false,
                border: false,
                width: 600,
                buttonAlign : 'left',
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui : 'footer',
                    style : 'background: none',
                    height : 30,
                    items: [{
                        xtype: 'button',
                        text: 'Done',
                        handler: function() {window.location = LABKEY.ActionURL.buildURL('study', 'manageStudy.view', null, null);}
                    }]
                }]
            });

            //Alias mapping components start here

            var aliasDataSetId = <%=aliasDatasetId%>;
            var previousValue = (aliasDataSetId != -1);
            Ext4.define('datasetModel',{
                extend : 'Ext.data.Model',
                fields : [
                    { name : 'Label', type : 'string' },
                    { name : 'DataSetId', type : 'int'}
                ]
            });

            this.dataStore = Ext4.create('Ext.data.Store', {
                model : 'datasetModel',
                sorters : {property : 'Label', direction : 'ASC'}
            });
//

            var dataCombo = Ext4.create('Ext.form.field.ComboBox',{
                name : 'datasetCombo',
                queryMode : 'local',
                store: this.dataStore,
                valueField : 'DataSetId',
                displayField : 'Label',
                labelWidth : 200,
                fieldLabel : 'Dataset Containing Aliases',
                editable: false,
                listeners : {
                     select : function(cb){
                            aliasCombo.clearValue();
                            sourceCombo.clearValue();
                            populateOtherBoxes(cb.getRawValue());
                            aliasCombo.setDisabled(false);
                            sourceCombo.setDisabled(false);

                     },
                     setup : function(cb){
                         aliasCombo.clearValue();
                         sourceCombo.clearValue();
                         populateOtherBoxes(cb.getRawValue(), true);
                         aliasCombo.setDisabled(false);
                         sourceCombo.setDisabled(false);
                     }
                }
            });

            Ext4.define('aliasModel',{
                extend : 'Ext.data.Model',
                fields : [
                    { name : 'header', type : 'string' }
                ]
            });

             this.aliasStore = Ext4.create('Ext.data.Store', {
                model : 'aliasModel',
                sorters : {property : 'header', direction : 'ASC'}
            });

            var aliasCombo = Ext4.create('Ext.form.field.ComboBox',{
                name : 'aliasCombo',
                queryMode : 'local',
                store: this.aliasStore,
                valueField : 'header',
                displayField : 'header',
                labelWidth : 200,
                fieldLabel : 'Alias Column',
                editable: false,
                disabled : true
            });

            var sourceCombo = Ext4.create('Ext.form.field.ComboBox',{
                name : 'sourceCombo',
                queryMode : 'local',
                store: this.aliasStore,
                valueField : 'header',
                displayField : 'header',
                labelWidth : 200,
                fieldLabel : 'Source Column',
                editable: false,
                disabled : true
            });

            LABKEY.Query.selectRows({
                schemaName : 'study',
                queryName : 'Datasets',
                success : function(details){
                    this.dataStore.loadData(details.rows);
                    if(previousValue)
                    {
                        dataCombo.select(dataCombo.findRecord('DataSetId', <%=aliasDatasetId%>));
                        dataCombo.fireEvent('setup', dataCombo);
                    }
                },
                scope : this
            });

            var populateOtherBoxes = function(datasetName, setup){
                LABKEY.Query.selectRows({
                    schemaName : 'study',
                    queryName : datasetName,

                    success : function(details)
                    {
                        this.aliasStore.loadData(details.columnModel);
                        aliasCombo.fireEvent('dataloaded', aliasCombo);
                        sourceCombo.fireEvent('dataloaded', sourceCombo);
                        if(setup){
                            if('<%=h(aliasColumn)%>' != "")
                            {
                                aliasCombo.select(aliasCombo.findRecord('header', '<%=h(aliasColumn)%>'));
                            }
                            if('<%=h(sourceColumn)%>' != "")
                            {
                                sourceCombo.select(sourceCombo.findRecord('header', '<%=h(sourceColumn)%>'));
                            }
                        }
                    },
                    scope : this
                });
            };

            var importButton = Ext4.create('Ext.button.Button', {
                text : 'Import Aliases',
                disabled : !previousValue,
                handler : function() {
                    document.location = LABKEY.ActionURL.buildURL('study', 'import', null, {datasetId : aliasDataSetId});
                }
            });
            var manageButton = Ext4.create('Ext.button.Button', {
                text : 'Manage Datasets',
                handler : function(){
                    document.location = LABKEY.ActionURL.buildURL('study', 'manageTypes');
                }
            });

            var displayBadFormMessage = function() {
                Ext4.MessageBox.show({
                    title: "Incomplete Fields",
                    msg: "You must provide a Dataset, alias column, and source column.",
                    buttons: Ext4.MessageBox.OK,
                    icon: Ext4.MessageBox.INFO
                });
            };

            var changeIdMapping = function(dataSetId, aliasColumn, sourceColumn) {
                if((sourceColumn != null) && (aliasColumn != null)){
                    Ext4.Ajax.request({
                        url : LABKEY.ActionURL.buildURL("study", "mapAliasIds"),
                        method : 'POST',

                        success: function(details){
                            if(dataSetId != -1)
                                displayDoneChangingMessage("Save Alias Settings", <%=q(subjectNounSingular)%> + " alias settings saved successfully.");
                            else
                                displayDoneChangingMessage("Clear Alias Settings", <%=q(subjectNounSingular)%> + " alias settings cleared.")
                            aliasDataSetId = dataSetId;
                            if(dataSetId != -1)
                                importButton.setDisabled(false);

                        },
                        failure: function(response, options){
                            LABKEY.Utils.displayAjaxErrorResponse(response, options, false, 'An error occurred:<br>');
                        },
                        jsonData : {dataSetId : dataSetId, aliasColumn : aliasColumn, sourceColumn : sourceColumn},
                        headers : {'Content-Type' : 'application/json'},
                        scope: this
                    });
                }
                else {
                        displayBadFormMessage();
                }

            };

            var saveButton = Ext4.create('Ext.button.Button', {
                text : 'Save Changes',
                handler :  function(){changeIdMapping(dataCombo.getValue(), aliasCombo.getValue(), sourceCombo.getValue());}
            });
            var clearButton = Ext4.create('Ext.button.Button', {
                text : 'Clear Alias Settings',
                handler :  function(){
                    changeIdMapping(-1, "", "");
                    aliasDataSetId = -1;
                    importButton.setDisabled(true);
                    dataCombo.setValue(null);
                    aliasCombo.setValue(null);
                    aliasCombo.setDisabled(true);
                    sourceCombo.setValue(null);
                    sourceCombo.setDisabled(true);
                }
            });

            var mappingPanel = Ext4.create('Ext.form.FormPanel', {
                renderTo : 'datasetMappingPanel',
                bodyPadding: 10,
                bodyStyle: 'background: none',
                frame: false,
                border: false,
                width: 600,
                buttonAlign : 'left',
                items: [dataCombo, aliasCombo, sourceCombo],
                dockedItems : {
                    xtype : 'toolbar',
                    style : 'background: none',
                    dock : 'bottom',
                    ui : 'footer',
                    items: [saveButton, clearButton, importButton, manageButton]
                 }

            });
        };



        Ext4.onReady(init);

    })();

</script>
