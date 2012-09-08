/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.GenericChartGroupingPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        Ext4.applyIf(config, {
            width: 300
        });

        this.callParent([config]);

        this.addEvents(
                'chartDefinitionChanged',
                'closeOptionsWindow'
        );
    },

    initComponent : function() {
        var groupingItems = [];

        var singleRadio = {
            xtype: 'radio',
            name: 'colorType',
            inputValue: 'single',
            boxLabel: 'With a single color',
            checked: this.colorType ? this.colorType === 'single' : true,
            width: 150
        };

        var categoricalRadio = {
            xtype: 'radio',
            name: 'colorType',
            inputValue: 'measure',
            boxLabel: 'By a measure',
            checked: this.colorType ? this.colorType === 'measure' : false,
            width: 150
        };

        this.colorTypeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            xtype: 'radiogroup',
            fieldLabel: 'Color Points',
            vertical: false,
            width: 300,
            columns: 2,
            items: [singleRadio, categoricalRadio],
            value: this.colorType ? this.colorType : 'single',
            listeners: {
                change: function(radioGroup, newVal){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }

                    this.colorCombo.setDisabled(newVal.colorType === 'single');
                },
                scope: this
            }
        });
        
        groupingItems.push(this.colorTypeRadioGroup);

        this.colorCombo = Ext4.create('Ext.form.field.ComboBox', {
            xtype: 'combo',
            fieldLabel: 'Color Measure',
            name: 'colorMeasure',
            store: this.store,
            disabled: this.colorType ? this.colorType === 'single' : true,
            editable: false,
            allowBlank: false,
            valueField: 'name',
            displayField: 'label',
            value: this.colorMeasure ? this.colorMeasure : null,
            queryMode: 'local',
            width: 365,
            listeners: {
                change: function(combo, oldVal, newVal){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        groupingItems.push(this.colorCombo);

        // Point Items

        var singlePointRadio = {
            xtype: 'radio',
            name: 'pointType',
            inputValue: 'single',
            boxLabel: 'Single shape',
            checked: this.colorType ? this.colorType === 'single' : true,
            width: 150
        };

        var categoricalPointRadio = {
            xtype: 'radio',
            name: 'pointType',
            inputValue: 'measure',
            boxLabel: 'Per measure',
            checked: this.colorType ? this.colorType === 'measure' : false,
            width: 150
        };

        this.pointTypeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            xtype: 'radiogroup',
            fieldLabel: 'Point Shape',
            vertical: false,
            width: 300,
            columns: 2,
            items: [singlePointRadio, categoricalPointRadio],
            value: this.pointType ? this.pointType : 'single',
            listeners: {
                change: function(radioGroup, newVal){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }

                    this.pointCombo.setDisabled(newVal.pointType === 'single');
                },
                scope: this
            }
        });

        groupingItems.push(this.pointTypeRadioGroup);

        this.pointCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel: 'Point Measure',
            name: 'pointMeasure',
            store: this.store,
            disabled: this.pointType ? this.pointType === 'single' : true,
            editable: false,
            allowBlank: false,
            valueField: 'name',
            displayField: 'label',
            value: this.pointMeasure ? this.pointMeasure : null,
            queryMode: 'local',
            width: 365,
            listeners: {
                change: function(combo, oldVal, newVal){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        groupingItems.push(this.pointCombo);


        this.items = groupingItems;

        this.buttons = [{
            text: 'OK',
            handler: this.applyChangesButtonClicked,
             scope: this
        },{
            text: 'Cancel',
            handler: this.cancelChangesButtonClicked,
            scope: this
        }];

        this.callParent();
    },

    applyChangesButtonClicked: function() {
        this.fireEvent('closeOptionsWindow', false);
        this.checkForChangesAndFireEvents();
    },

    cancelChangesButtonClicked: function(){
        this.fireEvent('closeOptionsWindow', true);
    },

    checkForChangesAndFireEvents: function(){
        if(this.hasChanges){
            this.fireEvent('chartDefinitionChanged')
        }
        this.hasChanges = false;
    },

    getPanelOptionValues: function() {
        return {
            colorType: this.getColorType(),
            colorMeasure: this.getColorMeasure(),
            pointType: this.getPointType(),
            pointMeasure: this.getPointMeasure()
        }
    },

    restoreValues: function(initValues) {

        if(initValues.hasOwnProperty('colorType')){
            this.setColorType(initValues.colorType);
        }

        if(initValues.hasOwnProperty('colorMeasure')){
            this.setColorMeasure(initValues.colorMeasure);
        }

        if(initValues.hasOwnProperty('pointType')){
            this.setPointType(initValues.pointType);
        }

        if(initValues.hasOwnProperty('pointMeasure')){
            this.setPointMeasure(initValues.pointMeasure);
        }

        this.hasChanges = false;
    },

    setPanelOptionValues: function(config){
        this.suppressEvents = true;

        if(config.colorType){
            this.setColorType(config.colorType);
        }

        if(config.colorMeasure){
            this.setColorMeasure(config.colorMeasure);
        }

        if(config.pointType){
            this.setPointType(config.pointType);
        }

        if(config.pointMeasure){
            this.setPointMeasure(config.pointMeasure);
        }

        this.suppressEvents = false;
    },

    getStore: function(){
        return this.store;
    },

    getColorType: function(value){
        return this.colorTypeRadioGroup.getValue().colorType;
    },

    setColorType: function(value){
        this.colorTypeRadioGroup.setValue({colorType: value});
    },

    getColorMeasure: function(){
        return this.colorCombo.getValue();
    },

    setColorMeasure: function(value){
        this.colorCombo.setValue(value);
    },

    getPointType: function(){
        return this.pointTypeRadioGroup.getValue().pointType;
    },

    setPointType: function(value){
        this.pointTypeRadioGroup.setValue({pointType: value});
    },

    getPointMeasure: function(){
        return this.pointCombo.getValue();
    },

    setPointMeasure: function(value){
        this.pointCombo.setValue(value);
    }
});