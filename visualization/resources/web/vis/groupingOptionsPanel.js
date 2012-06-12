/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.GroupingOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        Ext4.applyIf(config, {
            chartSubjectSelection: 'subjects',
            displayIndividual: true,
            displayAggregate: false,
            errorBars: "None"
        });

        this.callParent([config]);

        this.addEvents(
                'chartDefinitionChanged',
                'groupLayoutSelectionChanged',
                'closeOptionsWindow'
        );
    },

    initComponent : function(){
        // track if the panel has changed in a way that would require a chart/data refresh
        this.hasChanges = false;
        this.requireDataRefresh = false;

        var colOneItems = [];
        var colTwoItems = [];

        this.subjectRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'subject_selection',
            inputValue: 'subjects',
            labelAlign: 'top',
            fieldLabel: this.subjectNounSingular + ' Selection',
            boxLabel: this.subjectNounPlural,
            checked: this.chartSubjectSelection == 'subjects',
            listeners: {
                scope: this,
                'change': function(cmp, checked){
                    if(checked){
                        this.oneChartPerGroupRadio.setVisible(false);
                        this.oneChartPerSubjectRadio.setVisible(true);

                        if (this.oneChartPerGroupRadio.getValue())
                        {
                            this.oneChartPerSubjectRadio.setValue(true);
                        }
                        else
                        {
                            this.hasChanges = true;
                            this.requireDataRefresh = true;
                        }

                        this.displayIndividualCheckbox.disable();
                        this.displayIndividualCheckbox.setValue(true);
                        this.displayAggregateCheckbox.disable();
                        this.displayAggregateCheckbox.setValue(false);
                        this.displayErrorComboBox.setValue("None");
                    }
                }
            }
        });

        this.groupsRadio =  Ext4.create('Ext.form.field.Radio', {
            name: 'subject_selection',
            inputValue: 'groups',
            boxLabel: this.subjectNounSingular + ' Groups',
            checked: this.chartSubjectSelection == 'groups',
            listeners: {
                scope: this,
                'change': function(cmp, checked){
                    if (checked)
                    {
                        this.oneChartPerGroupRadio.setVisible(true);
                        this.oneChartPerSubjectRadio.setVisible(false);

                        if (this.oneChartPerSubjectRadio.getValue())
                        {
                            this.oneChartPerGroupRadio.setValue(true);
                        }
                        else
                        {
                            this.hasChanges = true;
                            this.requireDataRefresh = true;
                        }

                        this.displayIndividualCheckbox.enable();
                        this.displayAggregateCheckbox.enable();
                    }
                }
            }
        });
        this.subjectSelectionRadioGroup = new Ext.form.RadioGroup({
            border: false,
            columns: 1,
            items:[
                this.subjectRadio,
                this.groupsRadio
            ]
        });
        colOneItems.push(this.subjectSelectionRadioGroup);

        this.displayIndividualCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel  : 'Show Individual Lines',
            name      : 'Show Individual Lines',
            checked   : this.displayIndividual,
            value     : this.displayIndividual,
            style     : {marginLeft: '20px'}, // show indented
            disabled  : this.chartSubjectSelection != 'groups',
            listeners : {
                change : function(cmp, checked){
                    this.hasChanges = true;
                    this.requireDataRefresh = true;
                },
                scope : this
            }
        });

        this.displayAggregateCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel  : 'Show Mean',
            name      : 'Show Mean',
            checked   : this.displayAggregate,
            value     : this.displayAggregate,
            width     : 125,
            style     : {marginLeft: '20px'}, // show indented
            disabled  : this.chartSubjectSelection != 'groups',
            listeners : {
                change : function(cmp, checked){
                    // enable/disable the aggregate combo box accordingly
                    this.displayAggregateComboBox.setDisabled(!checked);
                    this.displayErrorComboBox.setDisabled(!checked);
                    if (!checked)
                        this.displayErrorComboBox.setValue("None");

                    this.hasChanges = true;
                    this.requireDataRefresh = true;
                },
                scope : this
            }
        });

        this.displayErrorComboBox = Ext4.create('Ext.form.field.ComboBox', {
            triggerAction : 'all',
            mode          : 'local',
            store         : Ext4.create('Ext.data.ArrayStore', {
                   fields : ['value'],
                   data   : [['None'], ['SD'], ['SEM']]
            }),
            disabled      : !this.displayAggregate,
            forceSelection: 'true',
            editable: false,
            valueField    : 'value',
            displayField  : 'value',
            value         : this.errorBars,
            width         : 75,
            listeners     : {
                select    : function(){
                    this.hasChanges = true;
                    this.requireDataRefresh = true;
                },
                scope     : this
            }
        });

        // combobox for selecting which aggregate to display when checkbox is selected
        this.displayAggregateComboBox = Ext4.create('Ext.form.field.ComboBox', {
            triggerAction : 'all',
            mode          : 'local',
            store         : Ext4.create('Ext.data.ArrayStore', {
                   fields : ['value'],
                   data   : [['Mean'], ['Count']]
            }),
            disabled      : !this.displayAggregate,
            hideLabel     : true,
            forceSelection: 'true',
            valueField    : 'value',
            displayField  : 'value',
            value         : 'Mean',
            width         : 75,
            listeners     : {
                select    : function(){
                    this.hasChanges = true;
                    this.requireDataRefresh = true;
                },
                scope     : this
            }
        });

        colOneItems.push(this.displayIndividualCheckbox);
        colOneItems.push({
            xtype: 'fieldcontainer',
            layout: 'hbox',
            items: [
                this.displayAggregateCheckbox,
                this.displayErrorComboBox
            ]
        });       

        this.oneChartRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'number_of_charts',
            labelAlign: 'top',
            fieldLabel: 'Number of Charts',
            labelWidth: 160,
            boxLabel: 'One Chart',
            inputValue: 'single',
            checked: this.chartLayout == 'single',
            listeners: {
                scope:this,
                'change': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        colTwoItems.push(this.oneChartRadio);

        this.oneChartPerGroupRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'number_of_charts',
            checked: this.chartLayout == 'per_group',
            inputValue: 'per_group',
            hidden: this.chartSubjectSelection == 'subjects',
            boxLabel: 'One Chart Per Group',
            listeners: {
                scope:this,
                'change': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        colTwoItems.push(this.oneChartPerGroupRadio);

        this.oneChartPerSubjectRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'number_of_charts',
            checked: this.chartLayout == 'per_subject',
            inputValue: 'per_subject',
            boxLabel: 'One Chart Per ' + this.subjectNounSingular,
            hidden: this.chartSubjectSelection ==  'groups',
            listeners: {
                scope:this,
                'change': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        colTwoItems.push(this.oneChartPerSubjectRadio);

        this.oneChartPerDimensionRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'number_of_charts',
            boxLabel: 'One Chart Per Measure/Dimension',
            checked: this.chartLayout == 'per_dimension',
            inputValue: 'per_dimension',
            listeners: {
                scope:this,
                'change': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        colTwoItems.push(this.oneChartPerDimensionRadio);

        this.items = [{
            xtype: 'panel',
            border: false,
            layout: 'column',
            items:[
                {
                    xtype: 'form',
                    columnWidth: 0.5,
                    border: false,
                    padding: 5,
                    items: colOneItems
                },
                {
                    xtype: 'form',
                    columnWidth: 0.5,
                    border: false,
                    padding: 5,
                    items: colTwoItems
                }
            ]
        }];

        this.buttons = [
            {
                text: 'Apply',
                handler: function(){
                    this.fireEvent('closeOptionsWindow');
                    this.checkForChangesAndFireEvents();
                },
                scope: this
            }
        ];

        this.callParent();
    },

    chartPerRadioChecked: function(field, checked){
        this.chartLayout = field.inputValue;
        this.hasChanges = true;
        this.requireDataRefresh = true;
    },

    getChartSubjectSelection: function(){
        if(this.groupsRadio.getValue()){
            return "groups";
        } else {
            return "subjects";
        }
    },

    getPanelOptionValues : function() {
        return {
            chartLayout: this.chartLayout,
            chartSubjectSelection: this.getChartSubjectSelection(),
            displayIndividual : this.displayIndividualCheckbox.disabled ? true : this.displayIndividualCheckbox.getValue(),
            displayAggregate : this.displayAggregateCheckbox.disabled ? false : this.displayAggregateCheckbox.getValue(),
            aggregateType : this.displayAggregateComboBox.getValue(),
            errorBars : this.displayErrorComboBox.getValue()
        };
    },

    checkForChangesAndFireEvents : function() {
        if (this.hasChanges)
        {
            this.fireEvent('groupLayoutSelectionChanged', this.getChartSubjectSelection() == "groups");
            this.fireEvent('chartDefinitionChanged', this.requireDataRefresh);
        }

        // reset the changes flags
        this.requireDataRefresh = false;
        this.hasChanges = false;
    }
});
