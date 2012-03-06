<%
/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.reports.report.ExternalScriptEngineReport"%>
<%@ page import="org.labkey.api.reports.report.RReport"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.reports.ExternalScriptEngine" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ViewContext context = HttpView.currentContext();
%>

<style type="text/css">

    .bmenu {
        background-image: url(<%=context.getContextPath() + "/_icons/exe.png"%>) !important;
    }

</style>

<script type="text/javascript">
    Ext.QuickTips.init();
    var R_EXTENSIONS = 'R,r';
    var PERL_EXTENSIONS = 'pl';

    function renderNameColumn(value, p, record)
    {
        var txt;

        if (record.data.enabled)
            txt = '<b>' + value + '</b><br>';
        else
            txt = '<b><div class="labkey-disabled">' + value + '</div></b><br>';

        txt = txt.concat('<i>enabled : ' + record.data.enabled + '</i><br>');
        txt = txt.concat('<i>external : ' + record.data.external + '</i><br>');

        return txt;
    }

    function recordsLoaded(records)
    {
        testItem.enable();
        for (var i in records)
        {
            if (records[i].data)
            {
                if (records[i].data.extensions == 'R,r')
                {
                    testItem.disable();
                }
            }
        }

    }

    function showEngines()
    {
        // pre defined engine templates
        
        var rEngineItem = new Ext.menu.Item({
            id: 'add_rEngine',
            text:'New R Engine',
            listeners:{click:function(button, event) {editRecord(button, grid,{
                name:'R Scripting Engine',
                extensions: R_EXTENSIONS,
                exeCommand:'<%=RReport.DEFAULT_R_CMD%>',
                outputFileName: <%= org.labkey.api.util.PageFlowUtil.jsString(ExternalScriptEngine.SCRIPT_NAME_REPLACEMENT + ".Rout") %>,
                external: true,
                <% if (!StringUtils.isEmpty(RReport.getDefaultRPath())) { %>
                    exePath: '<%=RReport.getDefaultRPath()%>',
                <% } %>
                enabled: true,
                languageName:'R'});}}}

            );

        var perlEngineItem = new Ext.menu.Item({
            id: 'add_perlEngine',
            text:'New Perl Engine',
            listeners:{click:function(button, event) {editRecord(button, grid,{
                name:'Perl Scripting Engine',
                extensions: PERL_EXTENSIONS,
                external: true,
                <% if (!StringUtils.isEmpty(ExternalScriptEngineReport.getDefaultPerlPath())) { %>
                    exePath: '<%=ExternalScriptEngineReport.getDefaultPerlPath()%>',
                <% } %>
                enabled: true,
                languageName:'Perl'});}}}
            );


        var con = new Ext.data.HttpProxy(new Ext.data.Connection({
                url: LABKEY.ActionURL.buildURL("reports", "scriptEnginesSummary", '<%=context.getContainer().getPath()%>'),
                method: 'GET'
            }));

        var store = new Ext.data.Store({
            reader: new Ext.data.JsonReader({root:'views',id:'extensions'},
                    [
                        {name:'name'},
                        {name:'exePath'},
                        {name:'exeCommand'},
                        {name:'extensions'},
                        {name:'languageName'},
                        {name:'languageVersion'},
                        {name:'key'},
                        {name:'enabled', type:'boolean'},
                        {name:'external', type:'boolean'},
                        {name:'outputFileName'}]),
            proxy: con,
            autoLoad: true,
            listeners:{load:function(store, records, options) {
                rEngineItem.enable();
                perlEngineItem.enable();
                for (var i in records) {
                    if (records[i].data) {
                        if (records[i].data.extensions == R_EXTENSIONS)
                            rEngineItem.disable();
                        if (records[i].data.extensions == PERL_EXTENSIONS)
                            perlEngineItem.disable();
                    }
                }
            }},
            sortInfo: {field:'name', direction:"ASC"}});

        var newMenu = new Ext.menu.Menu({
            id: 'mainMenu',
            cls:'extContainer',
            items: [rEngineItem,
                    perlEngineItem,
                {
                id: 'add_externalEngine',
                text:'New External Engine',
                listeners:{click:function(button, event) {editRecord(button, grid, {
                    name:"External",
                    enabled:true,
                    external: true});}}
            }] });

        var grid = new Ext.grid.GridPanel({
            el:'enginesGrid',
            autoScroll:false,
            autoHeight:true,
            enableHdMenu: false,
            width:800,
            store: store,
            listeners: {
                rowdblclick: function(g, rowIndex, event) {
                    event.stopEvent();
                    editSelected(event, g);
                }
            },
            columns:[
                {header:'Name', dataIndex:'name', renderer:renderNameColumn},
                {header:'Language', dataIndex:'languageName'},
                {header:'Language Version', dataIndex:'languageVersion'},
                {header:'File Extensions', dataIndex:'extensions'},
                {header:'Application Location', dataIndex:'exePath', hidden:true},
                {header:'Run Command', dataIndex:'exeCommand', hidden:true},
                {header:'Output File Name', dataIndex:'outputFileName', hidden:true}
            ],
            view: new Ext.grid.GridView({
                forceFit:true
            }),
            buttons: [
                {text:'Add', id: 'btn_addEngine', menu: newMenu, tooltip: {text:'Configure a new external script engine', title:'Add Engine'}},
                {text:'Delete', id: 'btn_deleteEngine', tooltip: {text:'Delete the selected script engine', title:'Delete Engine'}, listeners:{click:function(button, event) {deleteSelected(grid);}}},
                {text:'Edit', id: 'btn_editEngine', tooltip: {text:'Edit an existing script engine', title:'Edit Engine'}, listeners:{click:function(button, event) {editSelected(button, grid);}}}
            ],
            buttonAlign:'center',
            selModel: new Ext.grid.RowSelectionModel({singleSelect: true})
        });

        grid.render();
    }

    function deleteSelected(grid)
    {
        var selections = grid.selModel.getSelections();

        if (selections.length == 0)
        {
            Ext.Msg.alert("Delete Engine Configuration", "There is no engine selected");
            return false;
        }

        var record = selections[0].data;
        var params = [];

        if (!record.external)
        {
            Ext.Msg.alert("Delete Engine Configuration", "Java 6 script engines cannot be deleted but you can disable them.");
            return false;
        }
        params.push("key=" + record.key);
        params.push("extensions=" + record.extensions);

        Ext.Msg.confirm('Delete Engine Configuration', "Are you sure you wish to delete the selected Configuration? : " + record.name, function(btn, text) {
            if (btn == 'yes')
            {
                Ext.Ajax.request({

                    url: LABKEY.ActionURL.buildURL("reports", "scriptEnginesDelete") + '?' + params.join('&'),
                    method: "POST",
                    success: function(){grid.store.load();},
                    failure: function(){Ext.Msg.alert("Delete Engine Configuration", "Deletion Failed");}
                });
            }
        });
    }

    function setFormFieldTooltip(component)
    {
        var label = Ext.get('x-form-el-' + component.id).prev('label');
        Ext.QuickTips.register({
            target: label,
            text: component.tooltip.text,
            title: ''
        });
    }

    function editSelected(button, grid)
    {
        var selections = grid.selModel.getSelections();
        if (selections.length == 0)
        {
            Ext.Msg.alert("Edit Engine Configuration", "There is no engine selected");
            return false;
        }

        editRecord(button, grid, selections[0].data);
    }

    function editRecord(button, grid, record)
    {
        var formPanel = new Ext.FormPanel({
            bodyStyle:'padding:5px 5px 0',
            defaultType: 'textfield',
            items: [{
                fieldLabel: "Name",
                name: 'name',
                id: 'editEngine_name',
                allowBlank:false,
                readOnly:!record.external,
                value: record.name
            },{
                fieldLabel: 'Language',
                name: 'languageName',
                id: 'editEngine_languageName',
                allowBlank:false,
                readOnly:!record.external,
                value: record.languageName
            },{
                fieldLabel: 'Language Version',
                name: 'languageVersion',
                id: 'editEngine_languageVersion',
                readOnly:!record.external,
                value: record.languageVersion
            },{
                fieldLabel: 'File Extensions',
                name: 'extensions',
                id: 'editEngine_extensions',
                allowBlank:false,
                tooltip: {text:'The list of file extensions (separated by commas) that this engine is associated with', title:'File Extensions'},
                listeners: {render: setFormFieldTooltip},
                readOnly:!record.external,
                value: record.extensions
            },{
                fieldLabel: 'Program Path',
                name: 'exePath',
                id: 'editEngine_exePath',
                allowBlank:false,
                value: record.exePath,
                tooltip: {text:'Specify the absolute path to the program including the program itself', title:'Program Path'},
                listeners: {render: setFormFieldTooltip},
                disabled:!record.external,
                width: 275
            },{
                fieldLabel: 'Program Command',
                name: 'exeCommand',
                id: 'editEngine_exeCommand',
                tooltip: {text:'The command used when the program is invoked', title:'Program Command'},
                listeners: {render: setFormFieldTooltip},
                disabled:!record.external,
                value: record.exeCommand,
                width: 275
            },{
                fieldLabel: 'Output File Name',
                name: 'outputFileName',
                id: 'editEngine_outputFileName',
                value: record.outputFileName,
                tooltip: {text:'If the console output is written to a file, the name should be specified here. The substitution syntax \\${scriptName} will be replaced with the name (minus the extension) of the script being executed.', title:'Output File Name'},
                disabled:!record.external,
                listeners: {render: setFormFieldTooltip}
            },{
                fieldLabel: 'Enabled',
                name: 'enabled',
                id: 'editEngine_enabled',
                xtype: 'checkbox',
                checked: record.enabled,
                tooltip: {text:'If a script engine is disabled, it cannot be used to run reports and scripts', title:'Enable Engine'},
                listeners: {render: setFormFieldTooltip}
            },{
                name: 'external',
                xtype: 'hidden',
                value: record.external
            },{
                name: 'key',
                xtype: 'hidden',
                value: record.key
            }]
        });
        var win = new Ext.Window({
            title: 'Edit Engine Configuration',
            layout:'form',
            border: false,
            width: 475,
            height: 345,
            closeAction:'close',
            modal: false,
            items: formPanel,
            resizable: false,
            buttons: [{
                text: 'Submit',
                id: 'btn_submit',
                handler: function(){submitForm(win, formPanel, grid);}
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }],
            bbar: [{ xtype: 'tbtext', text: '',id:'statusTxt' }]
        });

        win.show(button);
    }

    function submitForm(win, panel, grid)
    {
        var items = panel.items;

        // client side validation
        var form = panel.getForm();
        if (form && !form.isValid())
        {
            Ext.Msg.alert('Engine Definition', 'Not all fields have been properly completed');
            return false;
        }

        win.getBottomToolbar().get(0).setText("");
        form.submit({
            url: LABKEY.ActionURL.buildURL("reports", "scriptEnginesSave"),
            waitMsg:'Submiting Form...',
            method: 'POST',
            success: function(){
                win.close();
                grid.store.load();
            },
            failure: function(form, action){handleError(win, action);}
        });
    }

    function handleError(win, action)
    {
        var errorTxt = 'An error occurred saving the engine configuration.';

        if (action.failureType == Ext.form.Action.SERVER_INVALID)
        {
            errorTxt = 'An error occurred, move your mouse over the fields highlighted<br>in red to see detailed information.';
        }
        else if (action.failureType == Ext.form.Action.CONNECT_FAILURE)
        {
            var jsonResponse = Ext.util.JSON.decode(action.response.responseText);
            if (jsonResponse && jsonResponse.exception)
                errorTxt = jsonResponse.exception;
        }
        win.getBottomToolbar().get(0).setText(errorTxt);
    }

    Ext.onReady(function()
    {
        showEngines();
    });
</script>

<labkey:errors/>

<table>
    <tr class="labkey-wp-header"><th colspan=2>Scripting Engine Configurations</th></tr>
    <tr><td><i>A scripting engine enables the execution of scripting code in a report or a QC validation script.
        A scripting engine can be exposed as a <a href="https://scripting.dev.java.net/" target="_blank">Java 6 script engine implementation</a>,
        or as an external script engine. Java 6 script engine implementations are exposed by configuring the Java runtime the webserver is running
        against. External engine implementations are added in this view. For example, scripting languages like R and Perl
        can be configured here in order to create and run scripts and reports using these languages.</i></td>
    </tr>
    <tr><td>&nbsp;</td></tr>
</table>

<div id="enginesGrid" class="extContainer"></div>


