<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.PipelineService"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.security.permissions.Permission"%>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission"%>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitDataSet" %>
<%@ page import="org.labkey.study.model.VisitDataSetType" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataSetDefinition> me = (JspView<DataSetDefinition>) HttpView.currentView();
    DataSetDefinition dataset = me.getModelBean();

    ViewContext context = HttpView.currentContext();

    String queryName = dataset.getTableInfo(context.getUser()).getName();
    String schemaName = dataset.getTableInfo(context.getUser()).getSchema().getQuerySchemaName();

    Set<Class<? extends Permission>> permissions = context.getContainer().getPolicy().getPermissions(context.getUser());
    StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
    VisitManager visitManager = StudyManager.getInstance().getVisitManager(study);
    boolean pipelineSet = null != PipelineService.get().findPipelineRoot(HttpView.currentContext().getContainer());
%>
<% if (permissions.contains(AdminPermission.class))
{
    ActionURL viewDatasetURL = new ActionURL(StudyController.DatasetAction.class, context.getContainer());
    viewDatasetURL.addParameter("datasetId", dataset.getDataSetId());

    ActionURL updateDatasetURL = new ActionURL(StudyController.UpdateDatasetVisitMappingAction.class, context.getContainer());
    updateDatasetURL.addParameter("datasetId", dataset.getDataSetId());

    ActionURL manageTypesURL = new ActionURL(StudyController.ManageTypesAction.class, context.getContainer());

    ActionURL deleteDatasetURL = new ActionURL(StudyController.DeleteDatasetAction.class, context.getContainer());
    deleteDatasetURL.addParameter("id", dataset.getDataSetId());

    %>
    <br>
<%  if (dataset.getType().equals(org.labkey.api.study.DataSet.TYPE_STANDARD)) { %>
        <%=generateButton("View Data", viewDatasetURL)%>
<%  }
    if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
        &nbsp;<%=generateButton("Edit Associated " + visitManager.getPluralLabel(), updateDatasetURL)%>
<%  } %>
    &nbsp;<%=generateButton("Manage Datasets", manageTypesURL)%>
    &nbsp;<%=generateButton("Delete Dataset", deleteDatasetURL,
        "return confirm('Are you sure you want to delete this dataset?  All related data and visitmap entries will also be deleted.')")%><%
%>
&nbsp;<a class="labkey-button" onClick="if (this.className.indexOf('labkey-disabled-button') != -1) return false; truncateTable();"> <span>Delete All Rows</span></a>
<%
}
if (permissions.contains(UpdatePermission.class))
{
    ActionURL showHistoryURL = new ActionURL(StudyController.ShowUploadHistoryAction.class, context.getContainer());
    showHistoryURL.addParameter("id", dataset.getDataSetId());

    ActionURL editTypeURL = new ActionURL(StudyController.EditTypeAction.class, context.getContainer());
    editTypeURL.addParameter("datasetId", dataset.getDataSetId());

    %>&nbsp;<%=generateButton("Show Import History", showHistoryURL)%>
<%  if (dataset.getType().equals(org.labkey.api.study.DataSet.TYPE_STANDARD)) { %>
        &nbsp;<%=generateButton("Edit Definition", editTypeURL)%>
<%  }
    else if(dataset.getType().equals(org.labkey.api.study.DataSet.TYPE_PLACEHOLDER))
    {
%>
        <a class="labkey-button" href="#" onclick="showLinkDialog()"><span>Link or Define Dataset</span></a>
<%
    }
}
if (!pipelineSet)
{
    include(new StudyController.RequirePipelineView(study, false, (BindException) request.getAttribute("errors")), out);
}
%>

<% WebPartView.startTitleFrame(out, "Dataset Properties", null, "100%", null); %>
<table id="details" width="600px">
    <tr>
        <td class=labkey-form-label>Name</td>
        <th align=left><%= h(dataset.getName()) %></th>

        <td class=labkey-form-label>ID</td>
        <td align=left><%= dataset.getDataSetId() %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Label</td>
        <td><%= h(dataset.getLabel()) %></td>

        <td class=labkey-form-label>Category</td>
        <td><%= h(dataset.getViewCategory() != null ? dataset.getViewCategory().getLabel() : null) %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Cohort Association</td>
        <td><%= dataset.getCohort() != null ? h(dataset.getCohort().getLabel()) : "All" %></td>

        <td class=labkey-form-label><%=visitManager.getLabel()%> Date Column</td>
        <td><%= h(dataset.getVisitDateColumnName()) %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Additional Key Column</td>
        <td><%= dataset.getKeyPropertyName() != null ? h(dataset.getKeyPropertyName()) : "None" %></td>

        <td rowspan="3" class=labkey-form-label>Description</td>
        <td rowspan="3"><%= h(dataset.getDescription()) %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Demographic Data <%=helpPopup("Demographic Data", "Demographic data appears only once for each " +
        StudyService.get().getSubjectNounSingular(getViewContext().getContainer()).toLowerCase() + 
        " in the study.")%></td>
        <td><%= dataset.isDemographicData() ? "true" : "false" %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Show In Overview</td>
        <td><%= dataset.isShowByDefault() ? "true" : "false" %></td>
    </tr>
</table>
<% WebPartView.endTitleFrame(out); %>
<p>
<%
    JspView typeSummary = new StudyController.StudyJspView<>(study, "typeSummary.jsp", dataset, (BindException)me.getErrors());
    typeSummary.setTitle("Dataset Fields");
    typeSummary.setFrame(WebPartView.FrameType.TITLE);
    me.include(typeSummary, out);
%>

<% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
<% WebPartView.startTitleFrame(out, "Visit Associations", null, "100%", null); %>
<table><%
    List<VisitDataSet> visitList = StudyManager.getInstance().getMapping(dataset);
    HashMap<Integer,VisitDataSet> visitMap = new HashMap<>();
    for (VisitDataSet vds : visitList)
        visitMap.put(vds.getVisitRowId(), vds);
    boolean hasVisitAssociations = false;
    for (VisitImpl visit : study.getVisits(Visit.Order.DISPLAY))
    {
        VisitDataSet vm = visitMap.get(visit.getRowId());
        if (vm != null)
        {
            hasVisitAssociations = true;
            VisitDataSetType type = vm.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL;
            %><tr>
                <td><%= visit.getDisplayString() %></td>
                <td><%= type == VisitDataSetType.NOT_ASSOCIATED ? "&nbsp;" : type.getLabel() %></td>
            </tr><%
        }
    }
    if (!hasVisitAssociations)
    {
    %><tr><td><i>This dataset isn't explicitly associated with any visits.</i></td></tr><%
    }
%></table>
<% WebPartView.endTitleFrame(out); %>
<% } %>

<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
</script>

<script type="text/javascript">
    function truncateTable()
    {
        Ext4.Msg.confirm("Confirm Deletion",
                "Are you sure you wish to delete all rows for this dataset?  This action cannot be undone.",
                function(button){
                    if (button === 'yes') {
                        truncate();
                    }
                }
        );

        function truncate()
        {
            var waitMask = Ext4.Msg.wait('Deleting Rows...', 'Delete Rows');
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('query', 'truncateTable'),
                method  : 'POST',
                success: function(response){
                    waitMask.close();
                    var data = Ext4.JSON.decode(response.responseText);
                    Ext4.Msg.alert("Success", data.deletedRows + " rows deleted");
                },
                failure : function(response, opts)
                {
                    waitMask.close();
                    LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                },
                jsonData : {schemaName : <%=q(schemaName)%>, queryName : <%=q(queryName)%>},
                headers : {'Content-Type' : 'application/json'},
                scope : this
            });
        }

    }
    function showLinkDialog(){
        Ext4.onReady(function(){
            var datasets = [
<%
        for (DataSet def : study.getDataSetsByType(new String[]{DataSet.TYPE_STANDARD, DataSet.TYPE_PLACEHOLDER}))
        {
%>
                {label: "<%=def.getLabel()%>", id: <%=def.getDataSetId()%>},
<%
        }
%>
            ]

            var datasetStore = Ext4.create('Ext.data.Store', {
                fields: ['label', 'id'],
                data: datasets
            });

            var datasetCombo = Ext4.create('Ext.form.field.ComboBox', {
                disabled: true,
                width: 220,
                allowBlank: false,
                cls : 'existing-dataset-combo',             // test marker
                editable: false,
                forceSelection: true,
                value: 'asdf',
                store: datasetStore,
                queryMode: 'local',
                displayField: 'label',
                valueField: 'id',
                margin: '10 0 0 85',
                listeners      : {
                    render     : function(combo) {
                        var store = combo.getStore();
                        combo.setValue(store.getAt(0));
                    }
                }
            });

            var importRadio = {
                boxLabel: 'Import data from file',
                name: 'deftype',
                inputValue: 'linkImport',
                checked: 'true'
            };
            var manualRadio = {
                boxLabel: 'Define dataset manually',
                name:'deftype',
                inputValue:'linkManually'
            };

            var existingRadio = {
                boxLabel: 'Link to existing dataset',
                name: 'deftype',
                inputValue: 'linkToTarget'
            };

            var linkDatasetGroup = Ext4.create('Ext.form.RadioGroup', {
                columns: 1,
                vertical: true,
                margin: '10 0 0 45',
                items: [importRadio, manualRadio, existingRadio],
                listeners: {
                    scope: this,
                    change: function(rgroup, newValue){
                        if(newValue.deftype == 'linkToTarget'){
                            linkDoneButton.setText('Done');
                            datasetCombo.setDisabled(false);
                        } else {
                            linkDoneButton.setText('Next');
                            datasetCombo.setDisabled(true);
                        }
                    }
                }
            });

            var linkDoneButton = Ext4.create('Ext.Button', {
                text: 'Next',
                handler: linkDatasetHandler,
                scope: this
            });
            
            var dialogConfig = {
                title: 'Link or Define Dataset',
                height: 225,
                width: 400,
                layout: 'fit',
                bodyStyle : 'border: none;',
                modal: true,
                scope: this,
                buttons : [{
                    xtype: 'button',
                    align: 'right',
                    text: 'Cancel',
                    handler: function(){
                        linkDatasetWindow.close();
                    },
                    scope: this
                }, linkDoneButton],
                items: [{
                    xtype: 'form',
                    border: false,
                    title: '',
                    defaults: {
                        margin: '10 0 0 25'
                    },
                    items: [{
                        xtype: 'displayfield',
                        value: "Define <%=h(dataset.getLabel())%>",
                        width: 340
                    },linkDatasetGroup, datasetCombo]
                }]
            };

            function linkDatasetHandler(){
                var json = {};
                json.type = linkDatasetGroup.getValue().deftype;
                json.expectationDataset = <%= dataset.getDataSetId() %>;

                if(json.type == 'linkToTarget'){
                    json.targetDataset = datasetCombo.getValue();
                }

                Ext4.Ajax.request({
                    url     : LABKEY.ActionURL.buildURL('study', 'defineDataset.view'),
                    method  : 'POST',
                    jsonData : json,
                    success : function(response){
                        var resp = Ext4.decode(response.responseText);
                        if(json.type == 'placeHolder' || json.type == 'linkToTarget'){
                            // If placeHolder or linkToTarget, navigate to new page.
                                linkDatasetWindow.close();
                                window.location = LABKEY.ActionURL.buildURL('study', 'datasetDetails.view', null, {id: json.targetDataset});
                        } else {
                            // If manual/import navigate to manual/import page.
                            window.location = resp.redirectUrl;
                        }
                    },
                    failure : function(response){
                        var resp = Ext4.decode(response.responseText);
                        if(resp && resp.exception){
                            Ext4.Msg.alert('Failure', resp.exception);
                        } else {
                            Ext4.Msg.alert('Failure', 'An unknown failure has occurred');
                        }
                    },
                    scope   : this
                });
            }

            var linkDatasetWindow = Ext4.create('Ext.window.Window', dialogConfig);

            linkDatasetWindow.show();
        });
    }
</script>
