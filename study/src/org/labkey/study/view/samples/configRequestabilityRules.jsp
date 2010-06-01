<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.importer.RequestabilityManager" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ViewContext context = HttpView.currentContext();
%>


<script type="text/javascript">
    window.onbeforeunload = LABKEY.beforeunload();

    function reorder(grid, moveUp)
    {
        var store = grid.store;
        var selectionModel = grid.getSelectionModel();
        var record = selectionModel.getSelected();
        if (record)
        {
            var index = store.indexOf(record);
            if (moveUp)
            {
                if (index > 0)
                {
                    store.removeAt(index);
                    store.insert(index - 1, record);
                    selectionModel.selectRow(index - 1);
                    grid.getView().refresh();
                }
            }
            else
            {
                if (index < store.getCount() - 1)
                {
                    store.removeAt(index);
                    store.insert(index + 1, record);
                    selectionModel.selectRow(index + 1);
                    grid.getView().refresh();
                }
            }
        }
    }

    function removeRule(grid)
    {
        var store = grid.store;
        var selectionModel = grid.getSelectionModel();
        var record = selectionModel.getSelected();
        if (record)
        {
            // Move the selection to the next item without preserving the current selection:
            selectionModel.selectNext(false);
            store.remove([ record ]);
            grid.getView().refresh();
        }
    }

    var dataFieldName = 'name';
    var dataUrlFieldName = 'viewDataUrl';

    function populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo)
    {
        schemaCombo.store.removeAll();
        schemaCombo.store.loadData(getArrayArray(schemasInfo.schemas));
        schemaCombo.on("select", function(combo, record, index)
        {
            queryCombo.clearValue();
            viewCombo.clearValue();
            LABKEY.Query.getQueries({
                schemaName: record.data[record.fields.first().name],
                successCallback: function(queriesInfo) { populateQueries(queryCombo, viewCombo, queriesInfo); }
            })
        });
    }

    var selectedQueryURL = "";
    var selectedViewURL = "";
    function populateQueries(queryCombo, viewCombo, queriesInfo)
    {
        var records = [];
        for (var i = 0; i < queriesInfo.queries.length; i++)
        {
            var queryInfo = queriesInfo.queries[i];
            records[i] = [queryInfo.name, queryInfo.viewDataUrl];
        }

        queryCombo.store.removeAll();
        queryCombo.store.loadData(records);
        queryCombo.on("select", function(combo, record, index)
        {
            viewCombo.clearValue();
            LABKEY.Query.getQueryViews({
                schemaName: queriesInfo.schemaName,
                queryName: record.data[record.fields.first().name],
                successCallback: function(queriesInfo) { populateViews(viewCombo, queriesInfo); }
            })
        });
    }

    var defaultViewLabel = "[default view]";

    function populateViews(viewCombo, queryViews)
    {
        var records = [[defaultViewLabel]];
        for (var i = 0; i < queryViews.views.length; i++)
        {
            var viewInfo = queryViews.views[i];
            var name =  viewInfo.name != null ? viewInfo.name : defaultViewLabel;
            records[records.length] = [name, viewInfo.viewDataUrl];
        }

        viewCombo.store.removeAll();
        viewCombo.store.loadData(records);
    }

    function getArrayArray(simpleArray)
    {
        var arrayArray = [];
        for (var i = 0; i < simpleArray.length; i++)
        {
            arrayArray[i] = [];
            arrayArray[i][0] = simpleArray[i];
        }
        return arrayArray;
    }

    function setupUserQuery(grid, type)
    {
        var schemaCombo = new Ext.form.ComboBox({
                typeAhead: false,
                store: new Ext.data.ArrayStore({
                    fields: [{
                        name: dataFieldName,
                        sortType: function(value) { return value.toLowerCase(); }
                    }],
                    sortInfo: { field: dataFieldName }
                }),
                valueField: dataFieldName,
                displayField: dataFieldName,
                fieldLabel: "Schema",
                name: 'schema',
                id: 'userQuery_schema',
                allowBlank:false,
                readOnly:false,
                editable:false,
                mode:'local',
                triggerAction: 'all'
            });

        var queryCombo = new Ext.form.ComboBox({
                typeAhead: false,
                store: new Ext.data.ArrayStore({
                    fields: [{
                        name: dataFieldName,
                        sortType: function(value) { return value.toLowerCase(); }
                    }, {
                        name: dataUrlFieldName
                    }],
                    sortInfo: { field: dataFieldName }
                }),
                valueField: dataFieldName,
                displayField: dataFieldName,
                fieldLabel: "Query",
                name: 'query',
                id: 'userQuery_query',
                allowBlank:false,
                readOnly:false,
                editable:false,
                mode:'local',
                triggerAction: 'all'
            });

        var viewCombo = new Ext.form.ComboBox({
                typeAhead: false,
                store: new Ext.data.ArrayStore({
                    fields: [{
                        name: dataFieldName,
                        sortType: function(value) { return value.toLowerCase(); }
                    }, {
                        name: dataUrlFieldName
                    }],
                    sortInfo: { field: dataFieldName }
                }),
                valueField: dataFieldName,
                displayField: dataFieldName,
                fieldLabel: "View",
                name: 'view',
                id: 'userQuery_view',
                allowBlank:true,
                readOnly:false,
                editable:false,
                mode:'local',
                triggerAction: 'all'
            });

        var actionCombo = new Ext.form.ComboBox({
                typeAhead: false,
                store: new Ext.data.ArrayStore(
                {
                    fields: ['option'],
                    data: [
                        ["<%= h(RequestabilityManager.MarkType.AVAILABLE.getLabel()) %>"],
                        ["<%= h(RequestabilityManager.MarkType.UNAVAILABLE.getLabel()) %>"]
                    ]
                }),
                valueField: 'option',
                displayField: 'option',
                fieldLabel: "Mark vials",
                name: 'action',
                id: 'userQuery_action',
                allowBlank:false,
                readOnly:false,
                editable:false,
                mode:'local',
                triggerAction: 'all'
            });

        LABKEY.Query.getSchemas({
            successCallback: function(schemasInfo) { populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo); }
        });

        var labelStyle = 'border-bottom:1px solid #AAAAAA;margin:3px';

        var queryHelpText = 'Select the query and view that identify vials affected by this rule.  The returned list must include a "GlobalUniqueId" column.';
        var queryLabel = new Ext.form.Label({
            html: '<div style="' + labelStyle +'">' + queryHelpText + '</div>'
        });

        var actionLabel = new Ext.form.Label({
            html: '<br><div style="' + labelStyle +'">Select whether vials identified by the query should be marked as available or unavailable.</div>'
        });

        var formPanel = new Ext.form.FormPanel({
            padding: 5,
            items: [queryLabel, schemaCombo, queryCombo, viewCombo, actionLabel, actionCombo]});
        
        var win = new Ext.Window({
            title: 'Add Custom Query Rule',
            layout:'fit',
            border: false,
            width: 475,
            height: 270,
            closeAction:'close',
            modal: true,
            items: formPanel,
            resizable: false,
            buttons: [{
                text: 'Submit',
                id: 'btn_submit',
                handler: function(){
                    var form = formPanel.getForm();
                    if (form && !form.isValid())
                    {
                        Ext.Msg.alert('Add Custon Query Rule', 'Please complete all required fields.');
                        return false;
                    }

                    var viewName = viewCombo.getValue();
                    if (viewName == defaultViewLabel)
                        viewName = "";
                    var ruleName = "<%= h(RequestabilityManager.RuleType.CUSTOM_QUERY.getName()) %>: " + schemaCombo.getValue() + "." + queryCombo.getValue();
                    if (viewName)
                        ruleName += ", view " + viewName;
                    var testUrl = getSelectedURL(viewName ? viewCombo : queryCombo);
                    var markRequestable = actionCombo.getValue() == '<%= h(RequestabilityManager.MarkType.AVAILABLE.getLabel()) %>';
                    var ruleData = schemaCombo.getValue() + "<%= RequestabilityManager.CUSTOM_QUERY_DATA_SEPARATOR %>" +
                                   queryCombo.getValue() + "<%= RequestabilityManager.CUSTOM_QUERY_DATA_SEPARATOR %>" +
                                   viewName + "<%= RequestabilityManager.CUSTOM_QUERY_DATA_SEPARATOR %>" +
                                   markRequestable;
                    if (addRule(grid, type, ruleName, actionCombo.getValue(), testUrl, ruleData))
                        win.close();
                }
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }],
            bbar: [{ xtype: 'tbtext', text: '',id:'statusTxt' }]
        });
        win.show();
    }

    function getSelectedURL(comboBox)
    {
        var value = comboBox.getValue();
        var record = comboBox.findRecord(comboBox.valueField, value);
        return record.data[dataUrlFieldName];
    }


    function addRule(grid, type, name, action, testURL, ruleData)
    {
        var store = grid.store;
        var data = store.data;

        if (type == 'CUSTOM_QUERY' && !ruleData)
        {
            setupUserQuery(grid, type);
            return false;
        }

        var ruleProperties = {
            type: type,
            ruleData: ruleData,
            name: name,
            action: action,
            testURL: testURL
        };

        // we never insert after the last rule, which should always be the locked-in-request check:
        var insertIndex = store.getCount() - 1;
        var selectionModel = grid.getSelectionModel();
        var record = selectionModel.getSelected();
        if (record)
            insertIndex = Math.min(insertIndex, store.indexOf(record) + 1);

        var newRule = new store.recordType(ruleProperties); // create new record
        store.insert(insertIndex, newRule); // insert a new record into the store
        grid.getView().refresh();
        
        return true;
    }

    function actionColumnRenderer(value, p, record)
    {
        return 'Mark ' + record.data.action.toLowerCase();
    }

    function testColumnRenderer(value, p, record)
    {
        var txt = '';

        if (record.data.testURL)
            txt = '[<a target=\"_blank\" href=\"' + record.data.testURL + '\">view affected vials</a>]';

        return txt;
    }

    function saveComplete()
    {
        Ext.Msg.hide();
        LABKEY.setDirty(false);
        document.location = '<%= new ActionURL(StudyController.ManageStudyAction.class, context.getContainer())%>';
    }

    function saveFailed(response, options)
    {
        Ext.Msg.hide();
        LABKEY.Utils.displayAjaxErrorResponse(response, options);
    }

    function save(rulesGrid)
    {
        if (!LABKEY.isDirty())
        {
            saveComplete();
            return;
        }

        var store = rulesGrid.store;
        var ruleTypes = [];
        var ruleDatas = [];
        for (var i = 0; i < store.getCount(); i++)
        {
            var record = store.getAt(i);
            ruleTypes[i] = record.data.type;
            ruleDatas[i] = record.data.ruleData ? record.data.ruleData : "";
        }

        Ext.Msg.wait("Saving...");
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("study-samples", "updateRequestabilityRules"),
            method : 'POST',
            success: saveComplete,
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.displayAjaxErrorResponse, this, true),
            jsonData : {
                ruleType : ruleTypes,
                ruleData : ruleDatas
            },
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function showRules()
    {
        Ext.QuickTips.init();

        var rulesGrid;

        var addMenuItems = [
            <%
                boolean first = true;
                for (RequestabilityManager.RuleType type : RequestabilityManager.RuleType.values())
                {
                    if (type != RequestabilityManager.RuleType.LOCKED_IN_REQUEST)
                    {
                        ActionURL defaultTestURL = type.getDefaultTestURL(context.getContainer());
                        RequestabilityManager.MarkType defaultMarkType = type.getDefaultMarkType();
                %>
                        <%= !first ? "," : "" %>new Ext.menu.Item({
                                    id: 'add_<%= type.name() %>',
                                    text:'<%= h(type.getName()) %>',
                                    listeners:{
                                        click:function(button, event) {
                                            LABKEY.setDirty(true);
                                            addRule(rulesGrid,
                                               '<%= type.name() %>', // type enum
                                               '<%= h(type.getName()) %>', // friendly name
                                               '<%= defaultMarkType != null ? h(defaultMarkType.getLabel()) : "" %>', // default mark type
                                               '<%= defaultTestURL != null ? h(defaultTestURL.getLocalURIString()) : "" %>'); // default mark type
                                        }
                                    }
                                })
                <%
                        first = false;
                    }
                }
            %>
        ];

        var addMenu = new Ext.menu.Menu({
            id: 'mainMenu',
            cls:'extContainer',
            items:addMenuItems
        });
        
        var addButton = new Ext.Button({
            text:'Add Rule',
            id: 'btn_addEngine',
            menu: addMenu,
            tooltip: {
                text:'Adds a new rule for determining requestability.',
                title:'Add Rule'
            }
        });

        var removeButton = new Ext.Button({
            text:'Remove Rule',
            id: 'btn_deleteEngine',
            tooltip: {
                text:'Delete the selected rule.',
                title:'Delete rule'
            },
            listeners: {
                click: function(button, event) { LABKEY.setDirty(true); removeRule(rulesGrid); }
            }
        });

        var moveUpButton = new Ext.Button({
            text:'Move Up',
            id: 'btn_moveUp',
            tooltip: {
                text:'Move the selected rule up.  Higher rules run earlier.',
                title:'Move Rule Up'
            },
            listeners: {
                click: function(button, event) { LABKEY.setDirty(true); reorder(rulesGrid, true); }
            }
        });

        var moveDownButton = new Ext.Button({
            text:'Move Down',
            id: 'btn_moveDown',
            tooltip: {
                text:'Move the selected rule down.  Lower rules run later.',
                title:'Move Rule Down'
            },
            listeners: {
                click: function(button, event) { LABKEY.setDirty(true); reorder(rulesGrid, false); }
            }
        });

        var saveButton = new Ext.Button({
            text:'Save',
            id: 'btn_save',
            listeners: {
                click: function(button, event) { save(rulesGrid); }
            }
        });

        var cancelButton = new Ext.Button({
            text:'Cancel',
            id: 'btn_cancel',
            listeners: {
                click: function(button, event) { document.location = '<%= new ActionURL(StudyController.ManageStudyAction.class, context.getContainer())%>'; }
            }
        });
        
        // shared reader
        var reader = new Ext.data.ArrayReader({}, [
            {name: 'type'},
            {name: 'ruleData'},
            {name: 'name'},
            {name: 'action'},
            {name: 'testURL'}
        ]);


        var initialData = [
        <%
        first = true;
        for (RequestabilityManager.RequestableRule rule : RequestabilityManager.getInstance().getRules(context.getContainer()))
        {
        %>
            <%= !first ? "," : "" %>
            ['<%= rule.getType().name() %>', '<%= h(rule.getRuleData()) %>', '<%= h(rule.getName()) %>',
                '<%= h(rule.getMarkType().getLabel()) %>',
            '<%= h(rule.getTestURL(context.getUser()).getLocalURIString()) %>'
            ]
        <%
            first = false;
        }
        %>
        ];

        var rowSelectionModel = new Ext.grid.RowSelectionModel({singleSelect: true});
        rowSelectionModel.on("rowselect", function(model, rowIndex, record)
        {
            var lockedInRequestSelected = record.data.type == '<%= RequestabilityManager.RuleType.LOCKED_IN_REQUEST.name() %>';
            if (lockedInRequestSelected)
            {
                moveUpButton.disable();
                moveDownButton.disable();
                removeButton.disable();
            }
            else if (rowIndex == rulesGrid.getStore().getCount() - 2)
            {
                moveUpButton.enable();
                moveDownButton.disable();
                removeButton.enable();
            }
            else if (rowIndex == 0)
            {
                moveUpButton.disable();
                moveDownButton.enable();
                removeButton.enable();
            }
            else
            {
                moveUpButton.enable();
                moveDownButton.enable();
                removeButton.enable();
            }
        });

        rowSelectionModel.on("rowdeselect", function(model, rowIndex, record)
        {
            moveUpButton.disable();
            moveDownButton.disable();
            removeButton.disable();
        });

        // Set inital button state (no selection by default):
        moveUpButton.disable();
        moveDownButton.disable();
        removeButton.disable();

        rulesGrid = new Ext.grid.GridPanel({
            store: new Ext.data.Store({
                reader: reader,
                data: initialData
            }),
            cm: new Ext.grid.ColumnModel({
                columns: [
                    new Ext.grid.RowNumberer({ header: 'Order', width: 50}),
                    {header:'Type', dataIndex:'type', hidden: true},
                    {header:'RuleData', dataIndex:'ruleData', hidden: true},
                    {header:'Rule', dataIndex:'name'},
                    {header:'Action', dataIndex:'action', renderer: actionColumnRenderer, width: 60},
                    {header:'Test Link', dataIndex:'testURL', renderer: testColumnRenderer, width: 55}
                ],
                sortable: false
            }),
            viewConfig: {
                forceFit:true
            },
            columnLines: true,
            enableDragDrop: true,
            width:700,
            height:300,
            title:'Active Rules',
            iconCls:'icon-grid',
            renderTo: 'rulesGrid',
            buttonAlign:'center',
            sm: rowSelectionModel,
            buttons: [addButton, removeButton, moveUpButton, moveDownButton, saveButton, cancelButton]
        });
    }

    Ext.onReady(function()
    {
        showRules();
    });
</script>

<labkey:errors/>

<table>
    <tr class="labkey-wp-header"><th colspan=2>Requestability Rule Configuration</th></tr>
    <tr><td><i>Whether a given vial is requestable is determined by running a series of configurable rules.  Each
    rule may change the requestability state of any vial(s).  Rules are run in order, so a vial's final state will be
        determined by the last rule to affect that vial.<br><br>
        Note: the <%= h(RequestabilityManager.RuleType.LOCKED_IN_REQUEST.getName()) %> cannot be removed,
        and must always be the last check performed.  This ensures that a single vial can never be part of two simultaneous requests.</i></td>
    </tr>
    <tr><td>&nbsp;</td></tr>
</table>

<div style="padding-left:10em" id="rulesGrid" class="extContainer"></div>
<table>
    <tr>
        <td class="labkey-announcement-title" colspan="2"><span>Available Rule Types</span></td>
    </tr>
    <tr>
        <td class="labkey-title-area-line" colspan="2"></td>
    </tr>
<%
    for (RequestabilityManager.RuleType type : RequestabilityManager.RuleType.values())
    {
%>
    <tr>
        <td><b><%= h(type.getName())%></b></td>
        <td><%= h(type.getDescription())%></td>
    </tr>
<%
    }
%>
</table>
