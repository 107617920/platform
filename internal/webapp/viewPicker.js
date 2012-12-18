/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/*
    A pop-up, ext-style schema/query/view picker.  Just call chooseView(), specifying a dialog title, some help text,
    a separator character, and a function to call when the user clicks submit.  The function parameter is a String
    containing schema, query, and (optional) view separated by the separator character.

    Originally developed by britt.  Generalized into a reusable widget by adam.
*/
var dataFieldName = 'name';
var dataUrlFieldName = 'viewDataUrl';
                                    // schema, query, view, column, folder, rootFolder, folderTypes
var initialValues = new Array();        // TODO: Select these values in combos

function populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema, columnCombo, folderCombo, customizePanel)
{
    var schemas;

    if (includeSchema)
    {
        schemas = new Array();
        var len = schemasInfo.schemas.length;

        for (var i = 0; i < len; i++)
            if (includeSchema(schemasInfo.schemas[i]))
                schemas.push(schemasInfo.schemas[i]);
    }
    else
    {
        schemas = schemasInfo.schemas;
    }

    var savedValue = schemaCombo.getValue();
    schemaCombo.clearValue();

    schemaCombo.store.removeAll();
    schemaCombo.store.loadData(getArrayArray(schemas));
    schemaCombo.on("select", function(combo, record, index)
    {
        if (customizePanel)
            customizePanel.getEl().mask('Loading Queries...', 'loading-indicator indicator-helper');

        LABKEY.Query.getQueries({
            schemaName: record.data[record.fields.first().name],
            containerPath: folderCombo ? folderCombo.getValue() : LABKEY.Security.currentContainer.path,
            successCallback: function(details)
            {
                if (customizePanel)
                    customizePanel.getEl().unmask();
                populateQueries(schemaCombo, queryCombo, viewCombo, details, columnCombo, folderCombo, customizePanel);
            },
            failureCallback: function()
            {
                if (customizePanel)
                    customizePanel.getEl().unmask();
                queryCombo.clearValue();
                viewCombo.clearValue();
                if (columnCombo)
                    columnCombo.clearValue();
            }
        });
    });

    if (initialValues[0])
    {
        savedValue = initialValues[0];
        initialValues[0] = null;
    }

    if (savedValue)
    {
        var index = schemaCombo.getStore().findExact('name', savedValue);

        if (-1 != index)
        {
            var record = schemaCombo.getStore().getAt(index);
            schemaCombo.setValue(savedValue);
            schemaCombo.fireEvent('select', schemaCombo, record, index);
        }
        else
        {
            // If we're not going to fire event, clear subordinate combos
            queryCombo.clearValue();
            viewCombo.clearValue();
            if (columnCombo)
                columnCombo.clearValue();
        }
    }
}

function populateQueries(schemaCombo, queryCombo, viewCombo, queriesInfo, columnCombo, folderCombo, customizePanel)
{
    var records = [];
    for (var i = 0; i < queriesInfo.queries.length; i++)
    {
        var queryInfo = queriesInfo.queries[i];
        records[i] = [queryInfo.name, queryInfo.viewDataUrl];
    }

    var savedValue = queryCombo.getValue();
    queryCombo.clearValue();

    queryCombo.store.removeAll();
    queryCombo.store.loadData(records);
    queryCombo.on("select", function(combo, record, index)
    {
        if (customizePanel)
            customizePanel.getEl().mask('Loading Views...', 'loading-indicator indicator-helper');

        var queryName = record.data[record.fields.first().name];
        var schemaName = schemaCombo.getValue();
        LABKEY.Query.getQueryViews({
            containerPath: folderCombo ? folderCombo.getValue() : LABKEY.Security.currentContainer.path,
            schemaName: schemaName,
            queryName: queryName,
            successCallback: function(details)
            {
                if (customizePanel)
                    customizePanel.getEl().unmask();
                populateViews(schemaCombo, queryCombo, viewCombo, details, columnCombo, folderCombo, customizePanel);
                if (columnCombo)
                {
                    if (customizePanel)
                        customizePanel.getEl().mask('Loading Columns...', 'loading-indicator indicator-helper');
                    LABKEY.Query.getQueryDetails({
                        containerPath: folderCombo ? folderCombo.getValue() : LABKEY.Security.currentContainer.path,
                        schemaName: schemaName,
                        queryName: queryName,
                        initializeMissingView: true,
                        successCallback: function(details)
                        {
                            if (customizePanel)
                                customizePanel.getEl().unmask();
                            populateColumns(columnCombo, details);
                        },
                        failureCallback: function ()
                        {
                            if (customizePanel)
                                customizePanel.getEl().unmask();
                            columnCombo.clearValue();
                        }
                    });
                }
            },
            failureCallback: function ()
            {
                if (customizePanel)
                    customizePanel.getEl().unmask();
                viewCombo.clearValue();
                if (columnCombo)
                    columnCombo.clearValue();
            }
        })
    });

    if (initialValues[1])
    {
        savedValue = initialValues[1];
        initialValues[1] = null;
    }

    if (savedValue)
    {
        var queryComboIndex = queryCombo.getStore().findExact('name', savedValue);

        if (-1 != queryComboIndex)
        {
            var record = queryCombo.getStore().getAt(queryComboIndex);
            queryCombo.setValue(savedValue);
            queryCombo.fireEvent('select', queryCombo, record, queryComboIndex);
        }
        else
        {
            // If we're not going to fire event, clear subordinate combos
            viewCombo.clearValue();
            if (columnCombo)
                columnCombo.clearValue();
        }
    }
}

var defaultViewLabel = "[default view]";

function populateViews(schemaCombo, queryCombo, viewCombo, queryViews, columnCombo, folderCombo, customizePanel)
{
    var records = [[defaultViewLabel]];

    for (var i = 0; i < queryViews.views.length; i++)
    {
        var viewInfo = queryViews.views[i];
        if (!viewInfo.hidden && viewInfo.name != null && viewInfo.name != "")
            records[records.length] = [viewInfo.name, viewInfo.viewDataUrl];
    }

    var savedValue = viewCombo.getValue();
    viewCombo.clearValue();

    viewCombo.store.removeAll();
    viewCombo.store.loadData(records);

    if (columnCombo)
    {
        viewCombo.on("select", function(combo, record, index)
        {
            if (customizePanel)
                customizePanel.getEl().mask('Loading Columns...', 'loading-indicator indicator-helper');

            LABKEY.Query.getQueryDetails({
                containerPath: folderCombo ? folderCombo.getValue() : LABKEY.Security.currentContainer.path,
                schemaName: schemaCombo.getValue(),
                queryName: queryCombo.getValue(),
                initializeMissingView: true,
                successCallback: function(details)
                {
                    if (customizePanel)
                        customizePanel.getEl().unmask();
                    populateColumns(columnCombo, details);
                },
                failureCallback: function ()
                {
                    if (customizePanel)
                        customizePanel.getEl().unmask();
                    columnCombo.clearValue();
                }
            });
        });
    }

    var initialView = defaultViewLabel;
    if (initialValues[2])
    {
        savedValue = initialValues[2];
        initialValues[2] = null;
    }

    if (savedValue)
    {
        var viewComboIndex = viewCombo.getStore().findExact('name', savedValue);

        if (-1 != viewComboIndex)
        {
            initialView = savedValue;
        }
    }

    viewCombo.setValue(initialView);
}

function populateColumns(columnCombo, details)
{
    var records = [];

    var columns = details.columns;
    for (var i = 0; i < columns.length; i++)
    {
        var name = columns[i].name;
        records[records.length] = [name, columns[i].fieldKey];
    }

    var savedValue = columnCombo.getValue();
    columnCombo.clearValue();

    columnCombo.store.removeAll();
    columnCombo.store.loadData(records);

    if (initialValues[3])
    {
        savedValue = initialValues[3];
        initialValues[3] = null;
    }

    if (savedValue)
    {
        var queryColumnIndex = columnCombo.getStore().findExact('name', savedValue);

        if (-1 != queryColumnIndex)
        {
            var record = columnCombo.getStore().getAt(queryColumnIndex);
            columnCombo.setValue(savedValue);
            columnCombo.fireEvent('select', columnCombo, record, queryColumnIndex);
        }
    }
}

function populateFolders(schemaCombo, queryCombo, viewCombo, columnCombo, folderCombo, details, includeSchema, customizePanel)
{
    var records = [["[current project]", ""]];

    var folders = details.containers;
    if (folders && folders.length > 0)
        populateFoldersWithTree(folders[0], records);       // Just 1 at the root

    folderCombo.store.removeAll();
    folderCombo.store.loadData(records);
    folderCombo.on("select", function(combo, record, index)
    {
        if (customizePanel)
            customizePanel.getEl().mask('Loading Schemas...', 'loading-indicator indicator-helper');
        LABKEY.Query.getSchemas({
            containerPath: folderCombo.getValue(),
            successCallback: function(schemasInfo)
            {
                if (customizePanel)
                    customizePanel.getEl().unmask();
                populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema, columnCombo, folderCombo, customizePanel);
            },
            failureCallback: function()
            {
                if (customizePanel)
                    customizePanel.getEl().unmask();
                schemaCombo.clearValue();
                queryCombo.clearValue();
                viewCombo.clearValue();
                if (columnCombo)
                    columnCombo.clearValue();
            }
        });
    });

    var initialFolder = records[0].path;
    var folderComboIndex = 0;
    if (initialValues[4])
    {
        folderComboIndex = folderCombo.getStore().findExact('path', initialValues[4]);

        if (-1 != folderComboIndex)
        {
            initialFolder = initialValues[4];
        }

        initialValues[4] = null;
    }

    folderCombo.setValue(initialFolder);
    var record = folderCombo.getStore().getAt(folderComboIndex);
    folderCombo.fireEvent('select', folderCombo, record, folderComboIndex);
}

function populateFoldersWithTree(folder, records)
{
    var name = folder.name;
    if ("/" == folder.path)
        name = "[root]";
    records[records.length] = [name, folder.path];
    if (folder.children)
    {
        for (var i = 0; i < folder.children.length; i++)
        {
            if (folder.children[i])
                populateFoldersWithTree(folder.children[i], records);
        }
    }
}

function populateRootFolder(folderCombo, details)
{
    var records = [["[current folder]", ""]];

    var folders = details.containers;
    if (folders && folders.length > 0)
        populateFoldersWithTree(folders[0], records);       // Just 1 at the root

    folderCombo.store.removeAll();
    folderCombo.store.loadData(records);

    var initialFolder = records[0].path;
    var folderComboIndex = 0;
    if (initialValues[5])
    {
        folderComboIndex = folderCombo.getStore().findExact('path', initialValues[5]);

        if (-1 != folderComboIndex)
        {
            initialFolder = initialValues[5];
        }

        initialValues[5] = null;
    }

    folderCombo.setValue(initialFolder);
    var record = folderCombo.getStore().getAt(folderComboIndex);
    folderCombo.fireEvent('select', folderCombo, record, folderComboIndex);
}

function populateFolderTypes(details, folderTypesCombo, rootFolderCombo, schemaCombo, queryCombo, viewCombo, columnCombo,
                             folderCombo, includeSchema, customizePanel)
{
    var records = [["[all]",""]];

    for (var folderType in details)
        records[records.length] = [folderType, folderType];

    folderTypesCombo.store.removeAll();
    folderTypesCombo.store.loadData(records);

    if (customizePanel)
        customizePanel.getEl().mask('Loading Folders...', 'loading-indicator indicator-helper');
    LABKEY.Security.getContainers({
        container:["/"],
        includeSubfolders:true,
        successCallback:function (details)
        {
            if (customizePanel)
                customizePanel.getEl().unmask();
            populateRootFolder(rootFolderCombo, details);
            populateFolders(schemaCombo, queryCombo, viewCombo, columnCombo, folderCombo, details, includeSchema, customizePanel);
        },
        failureCallback:function ()
        {
            if (customizePanel)
                customizePanel.getEl().unmask();
        }
    });

    var initialFolder = records[0].name;
    var folderTypesComboIndex = 0;
    if (initialValues[6])
    {
        folderTypesComboIndex = folderTypesCombo.getStore().findExact('name', initialValues[6]);

        if (-1 != folderTypesComboIndex)
        {
            initialFolder = initialValues[6];
        }

        initialValues[6] = null;
    }

    folderTypesCombo.setValue(initialFolder);
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

var s;

function createCombo(fieldLabel, name, id, allowBlank, width)
{
    var combo = new Ext.form.ComboBox({
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
        fieldLabel: fieldLabel,
        name: name,
        id: id,
        allowBlank: allowBlank,
        readOnly:false,
        editable:false,
        mode:'local',
        triggerAction: 'all',
        lazyInit: false
    });

    if(width){
        combo.setWidth(width);
    }
    return combo;
}

function createFolderCombo(fieldLabel, name, id, allowBlank, width, usePath)
{
    var combo = new Ext.form.ComboBox({
        typeAhead: false,
        store: new Ext.data.ArrayStore({
            fields: [{
                name: dataFieldName,
                sortType: function(value) { return value.toLowerCase(); }
            },{
                name: 'path',
                sortType: function(value) { return value.toLowerCase(); }
            }],
            sortInfo: { field: usePath ? 'path' : dataFieldName, direction: 'ASC'}
        }),
        valueField: 'path',
        displayField: usePath ? 'path' : dataFieldName,
        fieldLabel: fieldLabel,
        name: name,
        id: id,
        allowBlank: allowBlank,
        readOnly:false,
        editable:false,
        mode:'local',
        triggerAction: 'all',
        lazyInit: false
    });

    if(width){
        combo.setWidth(width);
    }

    return combo;
}
function createSchemaCombo(width)
{
    return createCombo("Schema", "schema", "userQuery_schema", false, width);
}

function createQueryCombo(width)
{
    return createCombo("Query", 'query', 'userQuery_query', false, width);
}

function createViewCombo(width)
{
    return createCombo("View", "view", "userQuery_view", true, width);
}

function createBasicFolderCombo(width)
{
    return createFolderCombo("Folder", "folders", "userQuery_folders", true, width, false);
}

function createColumnCombo(width)
{
    return createCombo("Title Column", "column", "userQuery_Column", false, width);
}

function createRootFolderCombo(width)
{
    return createFolderCombo("Root Folder", "rootFolder", "userQuery_rootFolder", true, width, true);
}

function createFolderTypesCombo(width)
{
    return createCombo("Folder Types", "folderTypes", "userQuery_folderTypes", true, width);
}

// current value is an optional string parameter that provides string containing the current value.
// includeSchema is an optional function that determines if passed schema name should be included in the schema drop-down.
function chooseView(title, helpText, sep, submitFunction, currentValue, includeSchema)
{
    if (currentValue)
        initialValues = currentValue.split(sep);

    var schemaCombo = createSchemaCombo();
    s = schemaCombo;
    var queryCombo = createQueryCombo();
    var viewCombo = createViewCombo();

    var labelStyle = 'border-bottom:1px solid #AAAAAA;margin:3px';

    var queryLabel = new Ext.form.Label({
        html: '<div style="' + labelStyle +'">' + helpText + '<\/div>'
    });

    var formPanel = new Ext.form.FormPanel({
        padding: 5,
        timeout: Ext.Ajax.timeout,
        items: [queryLabel, schemaCombo, queryCombo, viewCombo]});

    var win = new Ext.Window({
        title: title,
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
                    Ext.Msg.alert(title, 'Please complete all required fields.');
                    return false;
                }

                var viewName = viewCombo.getValue();
                if (viewName == defaultViewLabel)
                    viewName = "";

                submitFunction(schemaCombo.getValue() + sep +
                               queryCombo.getValue() + sep +
                               viewName);
                win.close();
            }
        },{
            text: 'Cancel',
            id: 'btn_cancel',
            handler: function(){win.close();}
        }],
        bbar: [{ xtype: 'tbtext', text: '', id:'statusTxt'}]
    });

    win.show();
    win.getEl().mask('Loading Schemas...', 'loading-indicator indicator-helper');
    LABKEY.Query.getSchemas({
        successCallback: function(schemasInfo)
        {
            win.getEl().unmask();
            populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema, null, null, win);
        },
        failureCallback: function ()
        {
            win.getEl().unmask();
        }
    });

}

function customizeMenu(submitFunction, cancelFunction, renderToDiv, currentValue, includeSchema)
{
    var schemaCombo = createSchemaCombo(380);
    s = schemaCombo;
    var queryCombo = createQueryCombo(380);
    var viewCombo = createViewCombo(380);
    var columnCombo = createColumnCombo(380);
    var folderCombo = createBasicFolderCombo(380);

    var title = "";
    var schemaName = "";
    var queryName = "";
    var viewName = "";
    var columnName = "";
    var folderName = "";
    var url = "";
    var isChoiceListQuery = true;
    var includeAllDescendants = true;
    var rootFolder = "";
    var folderType = "";
    var pageId = null;
    var webPartIndex = 0;
//    var includeRootFolder = false;

    if (currentValue)
    {
        title = currentValue.title;
        isChoiceListQuery = currentValue.choiceListQuery;
        url = currentValue.url;
        includeAllDescendants = currentValue.includeAllDescendants;
        if (isChoiceListQuery)
        {   // Grab saved schema/query/etc if Query radio button will be set
            schemaName = currentValue.schemaName;
            queryName = currentValue.queryName;
            viewName = currentValue.viewName;
            columnName = currentValue.columnName;
            folderName = currentValue.folderName;
        }
        else
        {   // Otherwise grab root, folder type
            rootFolder = currentValue.rootFolder;
            folderType = currentValue.folderTypes;
        }
        initialValues = [schemaName, queryName, viewName, columnName, folderName, rootFolder, folderType];

        pageId = currentValue.pageId;
        webPartIndex = currentValue.webPartIndex;
//        includeRootFolder = currentValue.includeRootFolder;
    }

    var formSQV = new Ext.form.FormPanel({
        border: false,
        hidden: !isChoiceListQuery,
        layout: 'form',
        labelSeparator: '',
        items: [folderCombo, schemaCombo, queryCombo, viewCombo, columnCombo]
    });

    var titleField = new Ext.form.TextField({
        name: 'title',
        fieldLabel: 'Title',
        value: title,
        width : 380
    });

    var urlField = new Ext.form.TextField({
        name: 'url',
        fieldLabel: 'URL',
        value: url,
        tooltip: 'URL of the form \'controller/action.view?parameter={column}\' or regular URL',
        width : 380
    });

    var includeAllDescendantsCheckbox = new Ext.form.Checkbox({
        name: 'includeAllDescendants',
        boxLabel: 'Include All Descendants',
        height: 30,
        value: true,
        checked: includeAllDescendants,
        width: 380
    });

/*
    var includeRootFolderCheckbox = new Ext.form.Checkbox({
        name: 'includeRootFolder',
        boxLabel: 'Include Root Folder',
        height: 30,
        value: false,
        checked: includeRootFolder,
        width: 380
    });

    var folderCheckboxPanel = new Ext.Panel({
        borders: false,
        vertical: false,
        items: [includeAllDescendantsCheckbox, includeRootFolderCheckbox]
    });
*/
    var rootFolderCombo = createRootFolderCombo(380);
    var folderTypesCombo = createFolderTypesCombo(380);

    var formFolders = new Ext.form.FormPanel({
        border: false,
        labelSeparator: '',
        timeout: Ext.Ajax.timeout,
        hidden: isChoiceListQuery,
        items: [includeAllDescendantsCheckbox, rootFolderCombo, folderTypesCombo]
    });

    var queryRadio = new Ext.form.Radio({
        boxLabel: 'Create from List or Query',
        name: 'menuSelect',
        inputValue: 'list',
        width: 200,
        checked: isChoiceListQuery,
        listeners: {
            scope: this,
            check: function(checkbox, checked){
                if (checked){
                    formSQV.setVisible(true);
                } else {
                    formSQV.setVisible(false);
                }
            }
        },
        id: 'query-radio'
    });

    var folderRadio = new Ext.form.Radio({
        boxLabel: 'Folders',
        name: 'menuSelect',
        inputValue: 'folders',
        checked: !isChoiceListQuery,
        listeners: {
            scope: this,
            check: function(checkbox, checked){
                if (checked){
                    formFolders.setVisible(true);
                } else {
                    formFolders.setVisible(false);
                }
            }
        },
        id: 'folder-radio'
    });

    var menuRadioGroup = new Ext.form.RadioGroup({
        fieldLabel: 'Menu Items',
        width: 380,
        vertical: false,
        columns: [.75, .25],
        labelSeparator: '',
        items: [queryRadio, folderRadio]
    });

    var formMenuSelectPanel = new Ext.form.FormPanel({
        border: false,
        items: [menuRadioGroup]
    });

    var formWinPanel = new Ext.Panel({
        border: false,
        layout: 'form',
        timeout: Ext.Ajax.timeout,
        labelSeparator: '',
        items: [titleField, formMenuSelectPanel, formSQV, formFolders, urlField]
    });

    var customizePanel = new Ext.Panel({
        renderTo: renderToDiv,
        border: false,
        items: formWinPanel,
        resizable: true,
        buttons: [{
            text: 'Submit',
            id: 'btn_submit',
            handler: function(){
                var isChoiceListQuery = menuRadioGroup.getValue().getRawValue() === 'list';
                var form = null;
                if(isChoiceListQuery){
                    form = formSQV.getForm();
                } else {
                    form = formFolders.getForm();
                }

                if (form && !form.isValid()){
                    Ext.Msg.alert(title, 'Please complete all required fields.');
                    return false;
                }

                var viewName = "";
                var viewRecord = viewCombo.getValue();
                if (viewRecord != defaultViewLabel)
                    viewName = viewRecord;

                submitFunction({
                    schemaName : schemaCombo.getValue(),
                    queryName : queryCombo.getValue(),
                    viewName: viewName,
                    folderName: folderCombo.getValue(),
                    columnName: columnCombo.getValue(),
                    title: titleField.getValue(),
                    url: urlField.getValue(),
                    choiceListQuery: isChoiceListQuery,
                    rootFolder: rootFolderCombo.getValue(),
                    folderTypes: folderTypesCombo.getValue(),
                    includeAllDescendants: includeAllDescendantsCheckbox.checked,
                    pageId: pageId,
                    webPartIndex: webPartIndex
                });
            }
        },{
            text: 'Cancel',
            id: 'btn_cancel',
            handler: function(){cancelFunction(); formWinPanel.doLayout();}
        }]
    });

    LABKEY.Security.getFolderTypes({
        successCallback: function(details)
        {
            populateFolderTypes(details, folderTypesCombo, rootFolderCombo, schemaCombo, queryCombo, viewCombo,
                    columnCombo, folderCombo, includeSchema, customizePanel);
        }
    });

    return customizePanel;
}
