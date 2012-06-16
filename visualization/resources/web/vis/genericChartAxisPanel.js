/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();
$h = Ext4.util.Format.htmlEncode;

Ext4.define('LABKEY.vis.GenericChartAxisPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){

        config.userEditedLabel = (config.label ? true : false);

        this.callParent([config]);

        this.addEvents(
                'chartDefinitionChanged',
                'okClicked',
                'cancelClicked'
        );
    },

    initComponent : function() {

        this.hasChanges = false;

        this.axisLabelField =  Ext4.create('Ext.form.field.Text', {
            name: 'label',
            fieldLabel: 'Label',
            enableKeyEvents: true,
            width: 300,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.hasChanges = true;
                },
                'specialkey': this.specialKeyPressed
            }
        });

        this.axisLabelField.addListener('keyup', function(){
            this.userEditedLabel = true;
            this.hasChanges = true;
        }, this, {buffer: 500});

        this.scaleTypeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            fieldLabel: 'Scale Type',
            items: [
                Ext4.create('Ext.form.field.Radio', {
                    width: 100,
                    boxLabel: 'linear',
                    inputValue: 'linear',
                    name: 'scaleType',
                    checked: 'true'
                }),
                Ext4.create('Ext.form.field.Radio', {
                    boxLabel: 'log',
                    inputValue: 'log',
                    name: 'scaleType'
                })
            ],
            listeners: {
                change: function(){
                    if(!this.suppressEvents){
                        this.fireEvent('chartDefinitionChanged');
                    }
                },
                scope: this
            }
        });

        this.items = [
            this.axisLabelField,
            this.scaleTypeRadioGroup,
            this.measureGrid
        ];
        
        this.callParent();
    },

    disableScaleAndRange: function() {
        var measure = this.measureGrid.getSelectionModel().getSelection();
        var disable = true;
        if(measure.length > 0){
            measure = measure[0];
            if(measure.data.type == 'int' || measure.data.type == 'float'){
                disable = false;
            }
        }
        
        this.scaleTypeRadioGroup.setDisabled(disable);
    },

    selectionChange: function(){
        this.disableScaleAndRange();
        if(!this.userEditedLabel){
            this.axisLabelField.setValue(this.getDefaultLabel());
        }
    },

    getDefaultLabel: function(){
        var measure = this.measureGrid.getSelectionModel().getSelection();
        var label = this.queryName;
        if(measure.length > 0){
            label = measure[0].data.label;
        }
        return label;
    },

    getPanelOptionValues: function() {
        return {
            label: this.getAxisLabel(),
            scaleType: this.getScaleType()
        };
    },

    setPanelOptionValues: function(config) {
        this.suppressEvents = true;

        if(config.label){
            this.setAxisLabel(config.label);
        }

        if(config.scaleType){
            this.setScaleType(config.scaleType);
        }

        this.suppressEvents = false;
    },

    getAxisLabel: function(){
        return this.axisLabelField.getValue();
    },

    setAxisLabel: function(value){
        this.axisLabelField.setValue(value);
    },

    getScaleType: function(){
        return this.scaleTypeRadioGroup.getValue().scaleType;
    },

    setScaleType: function(value){
        this.scaleTypeRadioGroup.setValue(value);
    },

    hideNonMeasureElements: function(){
        this.axisLabelField.hide();
        this.scaleTypeRadioGroup.hide();
    },

    showNonMeasureElements: function(){
        this.axisLabelField.show();
        this.scaleTypeRadioGroup.show();
    },

    specialKeyPressed: function(f, e) {
        if(e.getKey() == e.ENTER){
            console.log("enter hit");
        }
    }

});
