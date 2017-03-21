/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext.ModulePropertiesAdminPanel', {
    extend: 'Ext.form.Panel',

    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.applyIf(this, {
            modules: []
        });

        Ext4.apply(this, {
            padding: 5,
            border: false,
            items:  [{
                html: this.modules.length > 0 ? 'Loading...' : 'No modules provided',
                border: false
            }],
            buttons: [{
                text: 'Save Changes',
                hidden: this.modules.length == 0,
                handler: this.onSubmit,
                scope: this
            }]
        });

        this.callParent(arguments);

        this.pendingRequests = this.modules.length;
        this.fetchProperties();
    },

    fetchProperties: function(){
        Ext4.each(this.modules, function(m){
            Ext4.Ajax.request({
                url : LABKEY.ActionURL.buildURL('core', 'getModuleProperties', null),
                method : 'POST',
                scope: this,
                success: this.onLoad,
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                jsonData: {
                    moduleName: m,
                    includePropertyDescriptors: true,
                    includePropertyValues: true
                },
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        }, this);
    },

    onLoad: function(result, opts){
        this.pendingRequests--;

        var module = opts.jsonData.moduleName;
        this.propertyMap = this.propertyMap || {};
        this.propertyMap[module] = Ext4.decode(result.responseText);

        if (this.pendingRequests == 0)
            this.renderForm();
    },

    renderForm: function(){
        var toAdd = [{
            html: 'This page allows you to view and set module properties. Below are the properties that can be set. '
                + 'If the property can have a separate value per folder, there will be a field for the current folder '
                + 'and each parent folder. If the property can only be set site-wide, there will only be a single '
                + 'field. If you do not have permission to edit this property in any of those folders, the property '
                + 'will appear read-only. To see more detail about each property, hover over the question mark next '
                + 'to the property name.',
            border: false,
            padding: '0 0 15px 0'
        }];

        for(var module in this.propertyMap){
            var childItems = []
            for(var p in this.propertyMap[module].properties){
                childItems.push(this.getEditorForProperty(module, p));
            }

            toAdd.push({
                title: '<b>Module: ' + module + '</b>',
                border: true,
                padding: '0 0 15px 0',
                bodyStyle: 'padding: 5px;',
                items: childItems
            });
        }
        this.removeAll();
        this.add(toAdd);
    },

    getEditorForProperty: function(module, name){
        var pd = this.propertyMap[module].properties[name];
        var values = this.propertyMap[module].values[name];

        var tooltip = [
            'Module: ' + pd.module,
            'Can Set Per Folder: ' + pd.canSetPerContainer
        ];

        if(!Ext4.isEmpty(pd.defaultValue))
            tooltip.push('Default Value: ' + pd.defaultValue);

        if(!pd.showDescriptionInline && !Ext4.isEmpty(pd.description))
            tooltip.push('Description: ' + pd.description);

        tooltip = tooltip.join('<br>');

        var cfg = {
            padding: 5,
            items: [{
                name: pd.name,
                html: '<b>Property: ' + Ext4.String.htmlEncode(pd.label) + '</b>'
                    + '<a data-qtip="' + tooltip + '"><span class="labkey-help-pop-up">?</span></a>',
                border: false,
                style: 'background-color: #eeeeee; border-bottom: solid 1px #b4b4b4;',
                bodyStyle: 'background-color: #eeeeee',
                padding: '4px 10px'
            },{
                xtype: 'container',
                padding: 8,
                defaults: {
                    labelWidth: 200,
                    width: 200 + Math.max(300, pd.inputFieldWidth),
                    padding: 3
                },
                items: [{
                    html: Ext4.String.htmlEncode(pd.description),
                    border: false,
                    width: '100%',
                    padding: '0 0 10px 2px',
                    hidden: !pd.showDescriptionInline || Ext4.isEmpty(pd.description)
                },{
                    xtype: 'displayfield',
                    fieldLabel: '<b>Folder</b>',
                    labelSeparator: '',
                    value: '<b>Value</b>'
                }]
            }]
        };

        //sort on containerPath
        var valueArray = values.siteValues;
        valueArray = valueArray.sort(function(o1, o2){
            var a = o1.container.path, b = o2.container.path;
            return a < b ? -1 :
                    a == b ? 0 :
                    1;
        });

        Ext4.each(valueArray, function(v){
            cfg.items[1].items.push({
                fieldLabel: v.container.name || "Site Default",
                moduleName: module,
                moduleProp: v,
                propName: name,
                containerPath: v.container.path,
                xtype: (v.canEdit ? 'textfield' : 'displayfield'),
                value: v.value
            });
        }, this);

        return cfg;
    },

    onSubmit: function(btn){
        var toSave = [];
        this.getForm().getFields().each(function(item){
            if(item.isDirty()){
                toSave.push({
                    container: item.moduleProp.container.id,
                    userId: 0, //currently do not support individualized properties
                    value: item.getValue(),
                    propName: item.propName,
                    moduleName: item.moduleName
                })
            }
        }, this);

        if(!toSave.length){
            Ext4.Msg.alert('No changes', 'There are no changes to save.');
            return;
        }

        Ext4.Ajax.request({
            url : LABKEY.ActionURL.buildURL('core', 'saveModuleProperties', null),
            method : 'POST',
            scope: this,
            success: function(response){
                var response = Ext4.decode(response.responseText);
                if(response.success){
                    Ext4.Msg.alert('Success', 'Properties saved');

                    this.getForm().getFields().each(function(item){
                        item.resetOriginalValue(item.getValue());
                    }, this);

                }
                else
                    Ext4.Msg.alert('Error', 'There was an error saving the properties');
            },
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            jsonData: {
                properties: toSave
            },
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }
});
