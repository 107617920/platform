/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.XAxisOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        //Set time to 'date' if not already set.
        Ext4.applyIf(config, {
            time: 'date'
        });

        // set axis defaults, if not a saved chart
        Ext4.applyIf(config.axis, {
            name: "x-axis",
            range: {type: "automatic"}
        });

        Ext4.define('DimensionValue', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'id'},
                {name:'name'},
                {name:'label'},
                {name:'longlabel'},
                {name:'description'},
                {name:'isUserDefined'},
                {name:'queryName'},
                {name:'schemaName'},
                {name:'type'}
            ]
        });

        // track if the axis label is something that has been set by the user
        Ext4.apply(config, {
            userEditedLabel: false
        });

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete',
            'noDemographicData',
            'closeOptionsWindow'
        );
    },

    initComponent : function() {
        // track if the panel has changed in a way that would require a chart/data refresh
        this.hasChanges = false;
        this.requireDataRefresh = false;

        // the x-axis editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        //Radio Buttons for Date/Visit based chart.
        this.dateChartRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'chartType',
            fieldLabel: 'Chart Type',
            labelAlign: 'top',
            inputValue: 'date',
            boxLabel: 'Date Based Chart',
            checked: this.time == "date", //|| !this.time, //For old charts we default to date based chart.
            flex: 1,
            listeners: {
                scope: this,
                'change': function(field, checked){
                    if(checked) {
                        this.zeroDateCombo.enable();
                        this.intervalCombo.enable();
                        this.rangeAutomaticRadio.enable();
                        this.rangeManualRadio.enable();
                        if(this.rangeManualRadio.getValue()){
                            this.rangeMaxNumberField.enable();
                            this.rangeMinNumberField.enable();
                        }

                        this.resetLabel();

                        this.hasChanges = true;
                        this.requireDataRefresh = true;
                    }
                }
            }
        });

        this.visitChartRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'chartType',
            inputValue: 'visit',
            boxLabel: 'Visit Based Chart',
            checked: this.time == "visit",
            disabled: this.timepointType == "date",
            flex: 1,
            listeners: {
                scope: this,
                'change': function(field, checked){
                    if(checked) {
                        this.zeroDateCombo.disable();
                        this.intervalCombo.disable();
                        this.rangeAutomaticRadio.disable();
                        this.rangeManualRadio.disable();
                        this.rangeMaxNumberField.disable();
                        this.rangeMaxNumberField.setValue('');
                        this.rangeMinNumberField.disable();
                        this.rangeMinNumberField.setValue('');
                        this.resetLabel();

                        if(!this.doNotRefreshChart){
                            this.hasChanges = true;
                            this.requireDataRefresh = true;
                        } else {
                            this.doNotRefreshChart = false;
                        }
                    }
                },
                'enable': function(cmp){
                    if(this.timepointType == "date"){
                        cmp.setDisabled(true);
                    }
                }
            }
        });


        columnOneItems.push({
            xtype: 'fieldcontainer',
            items: [
                this.dateChartRadio,
                this.visitChartRadio
            ]
        });

        // combobox for the selection of the date axis interval unit
        this.intervalCombo = Ext4.create('Ext.form.field.ComboBox', {
            cls: 'x-axis-interval-combo-test',
            disabled: this.time == 'visit', //disable combo if the chart is visit based.
            triggerAction: 'all',
            queryMode: 'local',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: ['value'],
                data: [['Days'], ['Weeks'], ['Months'], ['Years']],
                listeners: {
                    scope: this,
                    'load': function() {
                        // if the zerodatecol value has loaded, check if this is a saved chart or if we need to reset the label to the default
                        if (this.axis.label && this.zeroDateCombo && this.zeroDateCombo.getValue())
                        {
                            this.userEditedLabel = this.axis.label != this.getDefaultLabel();
                            this.labelResetButton.setDisabled(!this.userEditedLabel);
                        }
                        else if (!this.axis.label && this.zeroDateCombo && this.zeroDateCombo.getValue())
                            this.resetLabel();
                    }
                }
            }),
            value: this.interval,
            valueField: 'value',
            displayField: 'value',
            fieldLabel: 'Draw x-axis as',
            labelAlign: 'top',
            forceSelection: true,
            editable: false,
            listeners: {
                scope: this,
                'select': function(cmp, records) {
                    // change the axis label if it has not been customized by the user
                    this.resetLabel();

                    this.hasChanges = true;
                    this.requireDataRefresh = true;
                }
            }
        });
        columnOneItems.push(this.intervalCombo);

        // combobox to select the "starting date" to be used for the x-axis interval calculation
        this.zeroDateCombo = Ext4.create('Ext.form.field.ComboBox', {
            disabled: this.time == 'visit', //disable combo if the chart is visit based.
            fieldLabel: 'Calculate time interval(s) relative to',
            labelAlign: 'top',
            triggerAction: 'all',
            queryMode: 'local',
            store: Ext4.create('Ext.data.Store', {fields: [], data: []}),
            valueField: 'longlabel',
            displayField: 'longlabel',
            forceSelection: true,
            editable: false,
            width: 350,
            minListWidth : 350,
            listeners: {
                scope: this,
                'select': function(cmp, records) {
                    if (records.length > 0)
                    {
                        // change the axis label if it has not been customized by the user
                        this.resetLabel();

                        Ext4.apply(this.zeroDateCol, records[0].data);
                        this.hasChanges = true;
                        this.requireDataRefresh = true;
                    }
                }
            }
        });
        columnOneItems.push(this.zeroDateCombo);

        this.labelTextField = Ext4.create('Ext.form.field.Text', {
            cls: 'x-axis-label-textfield-test',
            name: 'x-axis-label-textfield',
            hideLabel: true,
            value: this.axis.label,
            flex: 1,
            enableKeyEvents: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.hasChanges = true;
                }
            }
        });
        this.labelTextField.addListener('keyUp', function(){
            this.userEditedLabel = true;
            this.hasChanges = true;
            this.labelResetButton.enable();
        }, this, {buffer: 500});

        // button to reset a user defined label to the default based on the selected measures
        this.labelResetButton = Ext4.create('Ext.Button', {
            disabled: true,
            cls: 'revertxAxisLabel',
            iconCls:'iconReload',
            tooltip: 'Reset the label to the default value based on the panel settings.',
            handler: function() {
                this.labelResetButton.disable();
                this.userEditedLabel = false;
                this.resetLabel();
            },
            scope: this
        });

        columnTwoItems.push(Ext4.create('Ext.form.Label', {text: 'Axis label'}));
        columnTwoItems.push({
            xtype: 'fieldcontainer',
            layout: 'hbox',
            anchor: '100%',
            style: {paddingTop: '5px'},
            items: [
                this.labelTextField,
                this.labelResetButton
            ]
        });

        this.rangeAutomaticRadio = Ext4.create('Ext.form.field.Radio', {
            id: 'xaxis_range_automatic', // for selenium testing
            name: 'xaxis_range',
            fieldLabel: 'Range',
            labelAlign: 'top',
            inputValue: 'automatic',
            disabled: this.time == "visit",
            boxLabel: 'Automatic',
            checked: this.axis.range.type == "automatic",
            listeners: {
                scope: this,
                'change': function(field, checked){
                    // if checked, remove any manual axis min/max values
                    if(checked) {
                        this.setRangeMinMaxDisplay('automatic');
                        this.hasChanges = true;
                    }
                }
            }
        });
        columnTwoItems.push(this.rangeAutomaticRadio);

        this.rangeManualRadio = Ext4.create('Ext.form.field.Radio', {
            id: 'xaxis_range_manual', // for selenium testing
            name: 'xaxis_range',
            inputValue: 'manual',
            disabled: this.time == "visit",
            boxLabel: 'Manual',
            width: 85,
            flex: 1,
            checked: this.axis.range.type == "manual",
            listeners: {
                scope: this,
                'change': function(field, checked){
                    // if checked, enable the min and max fields and give min focus
                    if(checked) {
                        this.setRangeMinMaxDisplay('manual');
                        this.hasChanges = true;
                    }
                }
            }
        });

        this.rangeMinNumberField = Ext4.create('Ext.form.field.Number', {
            name: 'xaxis_rangemin', // for selenium test usage
            emptyText: 'Min',
            selectOnFocus: true,
            enableKeyEvents: true,
            width: 75,
            flex: 1,
            disabled: this.axis.range.type == "automatic" || this.time == "visit",
            value: this.axis.range.min,
            hideTrigger: true,
            mouseWheelEnabled: false,
            listeners: {
                scope: this,
                'change': function(){
                    this.hasChanges = true;
                }
            }
        });

        this.rangeMaxNumberField = Ext4.create('Ext.form.field.Number', {
            name: 'xaxis_rangemax', // for selenium test usage
            emptyText: 'Max',
            selectOnFocus: true,
            enableKeyEvents: true,
            width: 75,
            flex: 1,
            disabled: this.axis.range.type == "automatic" || this.time == "visit",
            value: this.axis.range.max,
            hideTrigger: true,
            mouseWheelEnabled: false,
            listeners: {
                scope: this,
                'change': function(){
                    this.hasChanges = true;
                }
            }
        });

        this.rangeCompositeField = Ext4.create('Ext.form.FieldContainer', {
            layout: 'hbox',
            items: [
                this.rangeManualRadio,
                this.rangeMinNumberField,
                this.rangeMaxNumberField
            ]
        });

        columnTwoItems.push(this.rangeCompositeField);

        this.items = [{
            border: false,
            layout: 'column',
            items: [{
                columnWidth: .5,
                xtype: 'form',
                border: false,
                padding: 5,
                items: columnOneItems
            },{
                columnWidth: .5,
                xtype: 'form',
                border: false,
                padding: 5,
                items: columnTwoItems
            }]
        }];

        this.buttons = [
            {
                text: 'Apply',
                handler: function(){
                    // check to make sure that, if set, the max value is >= to min
                    var maxVal = this.rangeMaxNumberField.getValue();
                    var minVal = this.rangeMinNumberField.getValue();
                    if (this.rangeManualRadio.checked && typeof minVal == "number" && typeof maxVal == "number" && maxVal < minVal)
                    {
                        Ext4.Msg.alert("ERROR", "Range 'max' value must be greater than or equal to 'min' value.", function(){
                            this.rangeMaxNumberField.focus();
                        }, this);
                        return;
                    }
                    
                    this.fireEvent('closeOptionsWindow');
                    this.checkForChangesAndFireEvents();
                },
                scope: this
            }
        ];

        this.callParent();
    },

    newZeroDateStore: function() {
        return Ext4.create('Ext.data.Store', {
            model: 'DimensionValue',
            proxy: {
                type: 'ajax',
                url : LABKEY.ActionURL.buildURL('visualization', 'getZeroDate', LABKEY.ActionURL.getContainer(), {
                    filters: [LABKEY.Visualization.Filter.create({schemaName: 'study'})],
                    dateMeasures: false
                }),
                reader: {
                    type: 'json',
                    root: 'measures',
                    idProperty:'id'
                }
            },
            autoLoad: true,
            sorters: {property: 'longlabel', direction: 'ASC'},
            listeners: {
                scope: this,
                'load': function(store, records, options) {
                    // if there are no zero date option for this study, warn the user
                    if (store.getTotalCount() == 0 && this.timepointType == "date")
                    {
                        Ext4.Msg.alert("Error", "There are no demographic date options available in this study. "
                            + "Please contact an administrator to have them configure the study to work with the Time Chart wizard.", function(){this.fireEvent('noDemographicData');}, this);
                        return;
                    }

                    if(store.getTotalCount() == 0 && this.timepointType == "visit")
                    {
                        // If we don't have any zero dates and we have a visit based study we'll want to automatically
                        // set the chart up as a visit based chart since there are no other options.
                        this.doNotRefreshChart = true; // Set doNotRefreshChart to prevent chart from firing ChartDefinitionChanged event.
                        this.visitChartRadio.setValue(true);
                        this.resetLabel();
                        this.dateChartRadio.disable();
                        this.visitChartRadio.disable();
                    }

                    // if this is a saved report, we will have a zero date to select
                    var index = 0;
                    if(this.zeroDateCol.name){
                        // need to get the index by the variable name and query name
                        index = store.findBy(function(record, id){
                            return (record.get('name') == this.zeroDateCol.name
                               && record.get('queryName') == this.zeroDateCol.queryName)
                        }, this);
                    }
                    // otherwise, try a few hard-coded options
                    else if(store.find('name', 'StartDate') > -1) {
                        index = store.find('name', 'StartDate');
                    }
                    else if(store.find('name', 'EnrollmentDt') > -1) {
                        index = store.find('name', 'EnrollmentDt');
                    }
                    else if(store.find('name', 'Date') > -1) {
                        index = store.find('name', 'Date');
                    }

                    if(store.getAt(index)){
                        this.zeroDateCombo.setValue(store.getAt(index).get('longlabel'));
                        Ext4.apply(this.zeroDateCol, store.getAt(index).data);
                    }

                    // if the interval value has loaded, check if this is a saved chart or if we need to reset the label to the default
                    if (this.axis.label && this.intervalCombo && this.intervalCombo.getValue())
                    {
                        this.userEditedLabel = this.axis.label != this.getDefaultLabel();
                        this.labelResetButton.setDisabled(!this.userEditedLabel);
                    }
                    else if (!this.axis.label && this.intervalCombo && this.intervalCombo.getValue())
                        this.resetLabel();

                    // this is one of the requests being tracked, see if the rest are done
                    this.fireEvent('measureMetadataRequestComplete');
                }
            }
        })
    },

    setZeroDateStore: function(){
        this.fireEvent('measureMetadataRequestPending');
        var newZStore = this.newZeroDateStore();
        this.zeroDateCombo.bindStore(newZStore);
    },

    setRangeMinMaxDisplay: function(type){
        if (type == 'manual')
        {
            this.rangeMinNumberField.enable();
            this.rangeMaxNumberField.enable();
            this.rangeMinNumberField.focus();
        }
        else if (type == 'automatic')
        {
            this.rangeMinNumberField.disable();
            this.rangeMinNumberField.setValue("");

            this.rangeMaxNumberField.disable();
            this.rangeMaxNumberField.setValue("");
        }
    },

    resetLabel : function() {
        if (!this.userEditedLabel)
        {
            var newLabel = this.getDefaultLabel();
            if (newLabel != null)
                this.labelTextField.setValue(newLabel);
        }
    },

    getDefaultLabel : function () {
        if (this.visitChartRadio.checked && this.intervalCombo.disabled)
            return "Visit";
        else // date radio checked
        {
            var store = this.zeroDateCombo.getStore();
            if (this.intervalCombo.getValue())
            {
                var zeroDateLabel = null;
                if (this.zeroDateCombo.getValue() != "" && store.find('longlabel', this.zeroDateCombo.getValue()) > -1)
                {
                    zeroDateLabel = store.getAt(store.find('longlabel', this.zeroDateCombo.getValue())).data.label;
                }
                else if (this.zeroDateCol.label)
                {
                    //If the zeroDateCombo is blank then we try the zeroDateCol, this prevents errors if a
                    //dataset has been hidden after a chart has been made
                    // (Issue 13554: Time chart doesn't refresh when switching chart type)
                    // (Issue 13809: Saved timecharts don't refresh after changing x-axis duration)
                    zeroDateLabel = this.zeroDateCol.label;
                }

                return this.intervalCombo.getValue() + " Since " + zeroDateLabel;
            }
            return null;
        }
    },

    getTime : function() {
        return this.dateChartRadio.checked ? this.dateChartRadio.inputValue : this.visitChartRadio.inputValue;
    },

    getPanelOptionValues : function() {
        var axisValues = {
            name : "x-axis",
            range : {
                type : this.rangeAutomaticRadio.checked ? this.rangeAutomaticRadio.inputValue : this.rangeManualRadio.inputValue
            },
            label : this.labelTextField.getValue()
        };

        if (this.rangeManualRadio.checked)
        {
            axisValues.range.min = this.rangeMinNumberField.getValue();
            axisValues.range.max = this.rangeMaxNumberField.getValue();
        }

        return {
            time: this.dateChartRadio.checked ? this.dateChartRadio.inputValue : this.visitChartRadio.inputValue,
            zeroDateCol: this.zeroDateCol,
            interval: this.intervalCombo.getValue(),
            axis: axisValues
        };
    },

    checkForChangesAndFireEvents : function(){
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', this.requireDataRefresh);

        // reset the changes flags
        this.requireDataRefresh = false;
        this.hasChanges = false;
    }
});
