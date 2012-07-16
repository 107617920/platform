/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

LABKEY.requiresScript("vis/genericOptionsPanel.js");
LABKEY.requiresScript("vis/initialMeasurePanel.js");
LABKEY.requiresScript("vis/saveOptionsPanel.js");
LABKEY.requiresScript("vis/measureOptionsPanel.js");
LABKEY.requiresScript("vis/yAxisOptionsPanel.js");
LABKEY.requiresScript("vis/xAxisOptionsPanel.js");
LABKEY.requiresScript("vis/groupingOptionsPanel.js");
LABKEY.requiresScript("vis/aestheticOptionsPanel.js");
LABKEY.requiresScript("vis/developerOptionsPanel.js");
LABKEY.requiresScript("vis/mainTitleOptionsPanel.js");
LABKEY.requiresScript("vis/participantSelector.js");
LABKEY.requiresScript("vis/groupSelector.js");

LABKEY.requiresScript("study/ParticipantFilterPanel.js");
LABKEY.requiresScript("study/MeasurePicker.js");

LABKEY.requiresCss("_images/icons.css");

Ext4.tip.QuickTipManager.init();
$h = Ext4.util.Format.htmlEncode;

Ext4.define('LABKEY.vis.TimeChartPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){
        // properties for this panel
        Ext4.apply(config, {
            id: 'time-chart-outer-panel', // for selenium testing 
            layout: 'border',
            bodyStyle: 'background-color: white;',
            monitorResize: true,
            maxCharts: 30,
            dataLimit: 10000
        });

        // support backwards compatibility for charts saved prior to chartInfo reconfig (2011-08-31)
        if (config.chartInfo)
        {
            Ext4.applyIf(config.chartInfo, {
                axis: [],
                //This is for charts saved prior to 2011-10-07
                chartSubjectSelection: config.chartInfo.chartLayout == 'per_group' ? 'groups' : 'subjects',
                displayIndividual: true,
                displayAggregate: false
            });
            for (var i = 0; i < config.chartInfo.measures.length; i++)
            {
                var md = config.chartInfo.measures[i];

                Ext4.applyIf(md.measure, {yAxis: "left"});

                // if the axis info is in md, move it to the axis array
                if (md.axis)
                {
                    // default the y-axis to the left side if not specified
                    if (md.axis.name == "y-axis")
                            Ext4.applyIf(md.axis, {side: "left"});

                    // move the axis info to the axis array
                    if (this.getAxisIndex(config.chartInfo.axis, md.axis.name, md.axis.side) == -1)
                        config.chartInfo.axis.push(Ext4.apply({}, md.axis));

                    // if the chartInfo has an x-axis measure, move the date info it to the related y-axis measures
                    if (md.axis.name == "x-axis")
                    {
                        for (var j = 0; j < config.chartInfo.measures.length; j++)
                        {
                            var schema = md.measure.schemaName;
                            var query = md.measure.queryName;
                            if (config.chartInfo.measures[j].axis && config.chartInfo.measures[j].axis.name == "y-axis"
                                    && config.chartInfo.measures[j].measure.schemaName == schema
                                    && config.chartInfo.measures[j].measure.queryName == query)
                            {
                                config.chartInfo.measures[j].dateOptions = {
                                    dateCol: Ext4.apply({}, md.measure),
                                    zeroDateCol: Ext4.apply({}, md.dateOptions.zeroDateCol),
                                    interval: md.dateOptions.interval
                                };
                            }
                        }

                        // remove the x-axis date measure from the measures array
                        config.chartInfo.measures.splice(i, 1);
                        i--;
                    }
                    else
                    {
                        // remove the axis property from the measure
                        delete md.axis;
                    }
                }
            } // end of : for
        } // end of : if (config.chartInfo)

        // backwards compatibility for save thumbnail options (2012-06-19)
        if(typeof config.saveReportInfo == "object" && config.chartInfo.saveThumbnail != undefined)
        {
            if (config.saveReportInfo.reportProps == null)
                config.saveReportInfo.reportProps = {};

            Ext4.applyIf(config.saveReportInfo.reportProps, {
                thumbnailType: !config.chartInfo.saveThumbnail ? 'NONE' : 'AUTO'
            });
        }

        this.callParent([config]);
    },

    initComponent : function() {

        if(this.viewInfo.type != "line")
            return;

        // chartInfo will be all of the information needed to render the line chart (axis info and data)
        if(typeof this.chartInfo != "object") {
            this.chartInfo = this.getInitializedChartInfo();
            this.savedChartInfo = null;
        } else {
            // If we have a saved chart we want to save a copy of config.chartInfo so we know if any chart settings
            // get changed. This way we can set the dirty bit. Ext.encode gives us a copy not a reference.
            this.savedChartInfo = Ext4.encode(this.chartInfo);
        }

        // hold on to the x and y axis measure index
        var xAxisIndex = this.getAxisIndex(this.chartInfo.axis, "x-axis");
        var leftAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "left");
        var rightAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "right");

        // add a listener to call measureSelectionChange on render if this is a saved chart
        // otherwise add the overview panel to the chart area to select the initial measure
        this.listeners = {
            scope: this,
            'render': function(){
                if(typeof this.saveReportInfo == "object")
                {
                    this.measureSelectionChange(false);
                    this.editorMeasurePanel.initializeDimensionStores();
                }
                else
                {
                    this.toggleOptionButtons(true);
                    this.chart.add(Ext4.create('LABKEY.vis.InitialMeasurePanel', {
                        listeners: {
                            scope: this,
                            'initialMeasuresStoreLoaded': function(data) {
                                // pass the measure store JSON data object to the measures panel
                                this.editorMeasurePanel.setMeasuresStoreData(data);
                            },
                            'initialMeasureSelected': function(initMeasure) {
                                this.maskAndRemoveCharts();
                                this.editorMeasurePanel.addMeasure(initMeasure, true);
                                this.measureSelectionChange(false);
                            }
                        }
                    }));
                }
            }
        };

        // keep track of requests for measure metadata ajax calls to know when all are complete
        this.measureMetadataRequestCounter = 0;

        var items = [];

        this.participantSelector = Ext4.create('LABKEY.vis.ParticipantSelector', {
            subject: (this.chartInfo.chartSubjectSelection != "groups" ? this.chartInfo.subject : {}),
            subjectNounPlural: this.viewInfo.subjectNounPlural,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            subjectColumn: this.viewInfo.subjectColumn,
            collapsed: this.chartInfo.chartSubjectSelection != "subjects",
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete
            }
        });

        this.groupsSelector = Ext4.create('LABKEY.vis.GroupSelector', {
            subject: (this.chartInfo.chartSubjectSelection == "groups" ? this.chartInfo.subject : {}),
            subjectNounPlural: this.viewInfo.subjectNounPlural,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            subjectColumn: this.viewInfo.subjectColumn,
            collapsed: this.chartInfo.chartSubjectSelection != "groups",
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete
            }
        });

        this.filtersPanel = Ext4.create('Ext.panel.Panel', {
            region: 'east',
            layout: 'accordion',
            fill: false,
            width: 220,
            border: true,
            split: true,
            collapsible: true,
            floatable: false,
            title: 'Filters',
            titleCollapse: true,
            items: [
                this.participantSelector,
                this.groupsSelector
            ],
            listeners: {
                scope: this,
                'afterRender': function(){
                    this.setOptionsForGroupLayout(this.chartInfo.chartSubjectSelection == "groups");
                }
            }
        });
        items.push(this.filtersPanel);

        this.editorSavePanel = Ext4.create('LABKEY.vis.SaveOptionsPanel', {
            reportInfo: this.saveReportInfo,
            canEdit: this.canEdit,
            canShare: this.canShare,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'saveChart': this.saveChart
            }
        });

        this.editorMeasurePanel = Ext4.create('LABKEY.vis.MeasureOptionsPanel', {
            origMeasures: this.chartInfo.measures,
            filterUrl: this.chartInfo.filterUrl ? this.chartInfo.filterUrl : LABKEY.Visualization.getDataFilterFromURL(),
            filterQuery: this.chartInfo.filterQuery ? this.chartInfo.filterQuery : this.getFilterQuery(),
            viewInfo: this.viewInfo,
            filtersParentPanel: this.filtersPanel,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh) {
                    this.measureSelectionChange(true);
                    this.chartDefinitionChanged(requiresDataRefresh);
                },
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete
            }
        });
        // the measure panel will be handled separately because of issue 15410
        // once that issue is fixed, we can possibly merge this panel back with the others
        this.editorMeasureWindow = Ext4.create('Ext.window.Window', {
            cls: 'data-window',
            draggable : false,
            width: 860,
            autoHeight: true,
            modal: true,
            closable: false,
            closeAction: 'hide',
            layout: 'fit',
            items: this.editorMeasurePanel,
            listeners: {
                'closeOptionsWindow': function() {
                    this.editorMeasureWindow.hide();
                },
                scope: this
            }
        });

        this.editorXAxisPanel = Ext4.create('LABKEY.vis.XAxisOptionsPanel', {
            axis: this.chartInfo.axis[xAxisIndex] ? this.chartInfo.axis[xAxisIndex] : {},
            zeroDateCol: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.zeroDateCol : {},
            interval: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.interval : "Days",
            time: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].time ? this.chartInfo.measures[0].time : 'date',
            timepointType: this.viewInfo.TimepointType,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete,
                'noDemographicData': this.disableOptionElements
            }
        });

        this.editorYAxisLeftPanel = Ext4.create('LABKEY.vis.YAxisOptionsPanel', {
            axis: this.chartInfo.axis[leftAxisIndex] ? this.chartInfo.axis[leftAxisIndex] : {side: "left"},
            defaultLabel: this.editorMeasurePanel.getDefaultLabel("left"),
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'resetLabel': function() {
                    this.editorYAxisLeftPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("left"));
                }
            }
        });
        //Set radio/textfield names to aid with TimeChartTest.
        this.editorYAxisLeftPanel.labelResetButton.cls = "revertleftAxisLabel";
        this.editorYAxisLeftPanel.rangeManualRadio.id = "leftaxis_range_manual";
        this.editorYAxisLeftPanel.rangeManualRadio.name = "leftaxis_range";
        this.editorYAxisLeftPanel.rangeAutomaticRadio.id = "leftaxis_range_automatic";
        this.editorYAxisLeftPanel.rangeAutomaticRadio.name = "leftaxis_range";
        this.editorYAxisLeftPanel.scaleCombo.id = "leftaxis_scale";
        this.editorYAxisLeftPanel.rangeMinNumberField.name = "leftaxis_rangemin";
        this.editorYAxisLeftPanel.rangeMaxNumberField.name = "leftaxis_rangemax";
        this.editorYAxisLeftPanel.labelTextField.name = "left-axis-label-textfield";

        this.editorYAxisRightPanel = Ext4.create('LABKEY.vis.YAxisOptionsPanel', {
            axis: this.chartInfo.axis[rightAxisIndex] ? this.chartInfo.axis[rightAxisIndex] : {side: "right"},
            defaultLabel: this.editorMeasurePanel.getDefaultLabel("right"),
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'resetLabel': function() {
                    this.editorYAxisRightPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("right"));
                }
            }
        });
        //Set radio/textfield names to aid with TimeChartTest.
        this.editorYAxisRightPanel.labelResetButton.cls = "revertrightAxisLabel";
        this.editorYAxisRightPanel.rangeManualRadio.id = "rightaxis_range_manual";
        this.editorYAxisRightPanel.rangeManualRadio.name = "rightaxis_range";
        this.editorYAxisRightPanel.rangeAutomaticRadio.id = "rightaxis_range_automatic";
        this.editorYAxisRightPanel.rangeAutomaticRadio.name = "rightaxis_range";
        this.editorYAxisRightPanel.scaleCombo.id = "rightaxis_scale";
        this.editorYAxisRightPanel.rangeMinNumberField.name = "rightaxis_rangemin";
        this.editorYAxisRightPanel.rangeMaxNumberField.name = "rightaxis_rangemax";        
        this.editorYAxisRightPanel.labelTextField.name = "right-axis-label-textfield";

        this.editorGroupingPanel = Ext4.create('LABKEY.vis.GroupingOptionsPanel', {
            chartLayout: this.chartInfo.chartLayout,
            chartSubjectSelection: this.chartInfo.chartSubjectSelection,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            subjectNounPlural: this.viewInfo.subjectNounPlural,
            displayIndividual: this.chartInfo.displayIndividual != undefined ? this.chartInfo.displayIndividual : true,
            displayAggregate: this.chartInfo.displayAggregate != undefined ? this.chartInfo.displayAggregate : false,
            errorBars: this.chartInfo.errorBars != undefined ? this.chartInfo.errorBars : "None",
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh){
                    if(this.editorGroupingPanel.groupLayoutChanged == true){
                        this.editorGroupingPanel.groupLayoutChanged = false;
                    }
                    this.chartDefinitionChanged(requiresDataRefresh);
                },
                'groupLayoutSelectionChanged': this.setOptionsForGroupLayout
            }
        });

        this.editorAestheticsPanel = Ext4.create('LABKEY.vis.AestheticOptionsPanel', {
            lineWidth: this.chartInfo.lineWidth,
            hideDataPoints: this.chartInfo.hideDataPoints,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged
            }
        });

        this.editorMainTitlePanel = Ext4.create('LABKEY.vis.MainTitleOptionsPanel', {
            mainTitle: this.chartInfo.title,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'resetTitle': function() {
                    this.editorMainTitlePanel.setMainTitle(this.editorMeasurePanel.getDefaultTitle());
                }
            }
        });

        this.editorDeveloperPanel = Ext4.create('LABKEY.vis.DeveloperOptionsPanel', {
            isDeveloper: this.isDeveloper,
            pointClickFn: this.chartInfo.pointClickFn || null,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged
            }
        });

        // put the options panels in an array for easier access
        this.optionPanelsArray = [
            this.editorGroupingPanel,
            this.editorAestheticsPanel,
            this.editorDeveloperPanel,
            this.editorMainTitlePanel,
            this.editorXAxisPanel,
            this.editorYAxisLeftPanel,
            this.editorYAxisRightPanel,
            this.editorSavePanel
        ];

        this.loaderFn = this.renderLineChart;  // default is to show the chart
        this.loaderName = 'renderLineChart';
        this.viewGridBtn = Ext4.create('Ext.button.Button', {text: "View Data", handler: this.viewDataGrid, scope: this, disabled: true});
        this.viewChartBtn = Ext4.create('Ext.button.Button', {text: "View Chart(s)", handler: this.renderLineChart, scope: this, hidden: true});
        this.refreshChart = new Ext4.util.DelayedTask(function(){
            this.getChartData();
        }, this);

        // setup exportPDF button and menu (items to be added later)
        // the single button will be used for "single" chart layout
        // and the menu button will be used for multi-chart layouts
        this.exportPdfMenu = Ext4.create('Ext.menu.Menu', {cls: 'extContainer'});
        this.exportPdfMenuBtn = Ext4.create('Ext.button.Button', {
            text: 'Export PDF',
            menu: this.exportPdfMenu,
            hidden: true,
            scope: this
        });
        this.exportPdfSingleBtn = Ext4.create('Ext.button.Button', {
            text: 'Export PDF',
            disabled: true,
            scope: this
        });

        this.measuresButton = Ext4.create('Ext.button.Button', {text: 'Measures',
                                handler: function(btn){
                                    var pLeft = btn.getEl().getX() - btn.getWidth();
                                    var pTop = btn.getEl().getY() + 20;
                                    this.editorMeasureWindow.setPosition(pLeft, pTop, false);
                                    this.editorMeasureWindow.show();
                                }, scope: this});

        // setup buttons for the charting options panels (items to be added to the toolbar)
        this.groupingButton = Ext4.create('Ext.button.Button', {text: 'Grouping',
                                handler: function(btn){this.optionsButtonClicked(btn, this.editorGroupingPanel, 600, 210, 'left');}, scope: this});

        this.aestheticsButton = Ext4.create('Ext.button.Button', {text: 'Options',
                                handler: function(btn){this.optionsButtonClicked(btn, this.editorAestheticsPanel, 300, 125, 'center');}, scope: this});

        this.supportsDeveloper = !(Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8); // issue 15372
        this.developerButton = Ext4.create('Ext.button.Button', {text: 'Developer', hidden: !this.isDeveloper,
                                handler: function(btn){this.optionsButtonClicked(btn, this.editorDeveloperPanel, 800, 500, 'center');}, scope: this,
                                disabled: !this.supportsDeveloper});
        if (!this.supportsDeveloper) this.developerButton.setTooltip("Developer options not supported for IE6, IE7, or IE8."); 

        this.saveButton = Ext4.create('Ext.button.Button', {text: 'Save', hidden: !this.canEdit,
                        handler: function(btn){
                                this.editorSavePanel.setSaveAs(false);
                                this.optionsButtonClicked(btn, this.editorSavePanel, 850, 420, 'right');
                        }, scope: this});


        this.saveAsButton = Ext4.create('Ext.button.Button', {text: 'Save As', hidden: !this.editorSavePanel.isSavedReport() || LABKEY.Security.currentUser.isGuest,
                        handler: function(btn){
                                this.editorSavePanel.setSaveAs(true);
                                this.optionsButtonClicked(btn, this.editorSavePanel, 850, 420, 'right');
                        }, scope: this});

        // for selenium testing, add a funtion that can be evaluated by selenium to open the axis/title panels similar to clicking on the axis label or title
        // this function can be removed once we figure out how to click on text within the SVG chart
        window.showTimeChartAxisPanel = function(type){
            var scopedThis = Ext4.getCmp('time-chart-outer-panel');
            var height = 100;
            var width = 100;

            // reference the panel based on the passed in type parameter
            var panelRef = null;
            if (type == 'X-Axis')
            {
                panelRef = scopedThis.editorXAxisPanel;
                height = 250;
                width = 800;
            }
            else if (type == 'Left-Axis')
            {
                panelRef = scopedThis.editorYAxisLeftPanel;
                height = 220;
                width = 320;
            }
            else if (type == 'Right-Axis')
            {
                panelRef = scopedThis.editorYAxisRightPanel;
                height = 220;
                width = 320;
            }
            else if (type == 'Title')
            {
                panelRef = scopedThis.editorMainTitlePanel;
                height = 130;
                width = 300;
            }
            else
                return; // unknown type

            // place the panel by the aesthetics options panel
            scopedThis.optionsButtonClicked(scopedThis.aestheticsButton, panelRef, width, height, 'center');
        };

        this.chart = Ext4.create('Ext.panel.Panel', {
            region: 'center',
            border: true,
            autoScroll: true,
            frame: false,
            tbar: [
                    this.viewGridBtn,
                    this.viewChartBtn,
                    this.exportPdfSingleBtn,
                    this.exportPdfMenuBtn,
                    this.measuresButton,
                    this.groupingButton,
                    this.aestheticsButton,
                    this.developerButton,
                    '->',
                    this.saveButton,
                    this.saveAsButton
            ],
            items: [],
            listeners: {
                scope: this,
                'resize': this.resizeCharts
            }
        });
        items.push(this.chart);

        Ext4.applyIf(this, {
            autoResize: true
        });

        if (this.autoResize)
        {
            Ext4.EventManager.onWindowResize(function(w,h){
                this.resizeToViewport(w,h);
            }, this);
        }

        this.items = items;

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);

        this.callParent();
    },

    resizeCharts : function(){
        // only call loader if the data object is available and the loader equals renderLineChart
        if((this.individualData || this.aggregateData) && this.loaderName == 'renderLineChart') {
            this.loaderFn();
        }
    },

    optionsButtonClicked : function(button, panel, width, height, align) {
        var pos = button.getEl().getXY();
        var pLeft = pos[0];
        var pTop = pos[1] + 20;

        if (align == 'center')
            pLeft = pLeft - width/2 + button.getWidth()/2;
        else if (align == 'right')
            pLeft = pLeft - width + button.getWidth();
        else if (align == 'left')
            pLeft = pLeft - button.getWidth();        

        this.showOptionsWindow(panel, width, pLeft, pTop);
    },

    chartElementClicked : function(panel, clickXY, width, height, align) {
        var pLeft = clickXY[0];
        var pTop = clickXY[1];

        if (align == 'above')
        {
            pLeft = pLeft - width/2;
            pTop = pTop - height - 10;
        }
        else if (align == 'below')
        {
            pLeft = pLeft - width/2;
            pTop = pTop + 12;
        }
        else if (align == 'right')
        {
            pLeft = pLeft - width - 10;
            pTop = pTop - height/2;
        }
        else if (align == 'left')
        {
            pLeft = pLeft + 10;
            pTop = pTop - height/2;
        }

        this.showOptionsWindow(panel, width, pLeft, pTop);
    },

    showOptionsWindow : function(panel, width, positionLeft, positionTop) {
        if (!this.optionWindow)
        {
            this.optionWindow = Ext4.create('Ext.window.Window', {
                floating: true,
                cls: 'data-window',
                draggable : false,
                width: 860,
                autoHeight: true,
                modal: true,
                closeAction: 'hide',
                layout: 'card',
                items: this.optionPanelsArray,
                onEsc: function(){
                    this.fireEvent('beforeclose');
                    this.hide();
                },
                listeners: {
                    'closeOptionsWindow': function() {     
                        this.optionWindow.hide();
                    },
                    scope: this
                }
            });
        }

        this.optionWindowPanel = panel;
        this.initialPanelValues = panel.getPanelOptionValues();

        // TODO: currently not supported for measures panel (issue 15410)
        this.optionWindow.un('beforeclose', this.restorePanelValues, this);
        if (!this.optionWindowPanel.hasOwnProperty("origMeasures"))
            this.optionWindow.on('beforeclose', this.restorePanelValues, this);

        this.optionWindow.setWidth(width);
        if (positionLeft && positionTop)
            this.optionWindow.setPosition(positionLeft, positionTop, false);

        this.optionWindow.getLayout().setActiveItem(this.optionWindowPanel);
        this.optionWindow.show();
    },

    restorePanelValues : function()
    {
        // on close, we don't apply changes and let the panel restore its state
        this.optionWindowPanel.restoreValues(this.initialPanelValues);
    },

    setOptionsForGroupLayout : function(groupLayoutSelected){
        // if the filters panel is collapsed, first open it up so the user sees that the filter options have changed
        if (this.filtersPanel.collapsed)
            this.filtersPanel.expand();

        if (groupLayoutSelected)
        {
            this.participantSelector.hide();
            this.groupsSelector.show();
            this.groupsSelector.expand();
        }
        else
        {
            this.groupsSelector.hide();
            this.participantSelector.show();
            this.participantSelector.expand();
        }
    },

    resizeToViewport : function(w,h) {
        if (!this.rendered)
            return;

        var padding = [20,0];
        var xy = this.el.getXY();
        var width = Math.max(875,w-xy[0]-padding[0]);
        this.setWidth(width);
    },

    getFilterQuery :  function()
    {
        var schemaName = LABKEY.ActionURL.getParameter("schemaName");
        var queryName = LABKEY.ActionURL.getParameter("queryName");
        if (schemaName && queryName)
            return schemaName + "." + queryName;
        else
            return undefined;
    },

    measureSelectionChange: function(fromMeasurePanel) {
        // these method calls should only be made for chart initialization
        // (i.e. showing saved chart or first measure selected for new chart)
        if(!fromMeasurePanel){
            this.participantSelector.getSubjectValues();
            this.groupsSelector.getGroupValues();
            this.editorXAxisPanel.setZeroDateStore();
        }

        // these method calls should be made for all measure selections
        this.editorYAxisLeftPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("left"));
        this.editorYAxisRightPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("right"));
        this.editorMainTitlePanel.setMainTitle(this.editorMeasurePanel.getDefaultTitle());

        // if all of the measures have been removed, disable any non-relevant elements
        if (this.editorMeasurePanel.getNumMeasures() == 0)
            this.disableNonMeasureOptionButtons();
        else
            this.toggleOptionButtons(false);
    },

    toggleOptionButtons: function(disable){
        this.measuresButton.setDisabled(disable);
        this.groupingButton.setDisabled(disable);
        this.aestheticsButton.setDisabled(disable);
        this.developerButton.setDisabled(!this.supportsDeveloper || disable);
        this.saveButton.setDisabled(disable);
        this.saveAsButton.setDisabled(disable);
    },

    toggleSaveButtons: function(disable)
    {
        this.saveButton.setDisabled(disable);
        this.saveAsButton.setDisabled(disable);
    },

    disableNonMeasureOptionButtons: function(){
        this.groupingButton.disable();
        this.aestheticsButton.disable();
        this.developerButton.disable();

        this.disablePdfExportButtons();

        this.viewGridBtn.disable();
        this.viewChartBtn.disable();
    },

    disablePdfExportButtons: function() {
        this.exportPdfMenuBtn.disable();
        this.exportPdfSingleBtn.disable();
    },

    disableOptionElements: function(){
        this.toggleOptionButtons(true);
        this.filtersPanel.disable();
        this.clearChartPanel("There are no demographic date options available in this study.<br/>"
                            + "Please contact an administrator to have them configure the study to work with the Time Chart wizard.");
    },

    isDirty : function() {
        return this.dirty;
    },

    markDirty : function(value) {
        this.dirty = value;
    },

    chartDefinitionChanged: function(requiresDataRefresh) {
        if (requiresDataRefresh)
            this.refreshChart.delay(100);
        else
            this.loaderFn();
    },

    measureMetadataRequestPending:  function() {
        // increase the request counter
        this.measureMetadataRequestCounter++;
    },

    measureMetadataRequestComplete: function() {
        // decrease the request counter
        this.measureMetadataRequestCounter--;

        // if all requests are complete, call getChartData
        if(this.measureMetadataRequestCounter == 0)
            this.getChartData();
    },

    getChartData: function() {
        this.maskAndRemoveCharts();

        // Clear previous chart data.
        this.individualData = undefined;
        this.individualHasData = undefined;
        this.aggregateData = undefined;
        this.aggregateHasData = undefined;

        // get the updated chart information from the various options panels
        this.chartInfo = this.getChartInfoFromOptionPanels();
        this.numberFormats = {};

        this.loaderCount = 0; //Used to prevent the loader from running until we have recieved all necessary callbacks.
        if (this.chartInfo.displayIndividual)
            this.loaderCount++;

        if (this.chartInfo.displayAggregate)
            this.loaderCount++;

        if (this.loaderCount == 0)
        {
            this.clearChartPanel("Please select either \"Show Individual Lines\" or \"Show Mean\".");
            return;
        }

        if (this.chartInfo.measures.length == 0)
        {
           this.clearChartPanel("No measure selected. Please click the \"Measures\" button to add a measure.");
           return;
        }

        if (this.chartInfo.displayIndividual)
        {
            //Get data for individual lines.
            LABKEY.Visualization.getData({
                success: function(data){
                    // set the dirty state for non-saved time chart once data is requested
                    if (!this.editorSavePanel.isSavedReport())
                        this.markDirty(true);

                    // store the data in an object by subject for use later when it comes time to render the line chart
                    this.individualData = data;
                    var gridSortCols = [];

                    // make sure each measure/dimension has at least some data, and get a list of which visits are in the data response
                    var visitsInData = [];
                    var seriesList = this.getSeriesList();
                    this.individualHasData = {};
                    Ext4.each(seriesList, function(s) {
                        this.individualHasData[s.name] = false;
                        for (var i = 0; i < this.individualData.rows.length; i++)
                        {
                            var row = this.individualData.rows[i];
                            if (row[this.getColumnAlias(this.individualData.columnAliases, s.aliasLookupInfo)].value != null)
                                this.individualHasData[s.name] = true;

                            var visitMappedName = this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectNounSingular + "Visit/Visit");
                            if (this.editorXAxisPanel.getTime() == "visit" && row[visitMappedName])
                            {
                                var visitVal = row[visitMappedName].value;
                                if (visitsInData.indexOf(visitVal) == -1)
                                    visitsInData.push(visitVal.toString());
                            }
                        }
                    }, this);

                    this.getNumberFormats(this.individualData.metaData.fields);

                    // trim the visit map domain to just those visits in the response data
                    this.individualData.visitMap = this.trimVisitMapDomain(this.individualData.visitMap, visitsInData);

                    // store the temp schema name, query name, etc. for the data grid
                    this.tempGridInfo = {schema: this.individualData.schemaName, query: data.queryName,
                        subjectCol: this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectColumn),
                        sortCols: this.editorXAxisPanel.getTime() == "date" ? gridSortCols : [this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder")]
                    };

                    // now that we have the temp grid info, enable the View Data button
                    // and make sure that the view charts button is hidden
                    this.viewGridBtn.enable();
                    this.viewChartBtn.hide();

                    // ready to render the chart or grid
                    this.loaderCount--;
                    if(this.loaderCount == 0){
                        this.loaderFn();
                    }
                },
                failure : function(info, response, options) {
                    LABKEY.Utils.displayAjaxErrorResponse(response, options);
                    this.clearChartPanel("Error: " + info.exception);
                },
                measures: this.chartInfo.measures,
                viewInfo: this.viewInfo,
                sorts: this.getDataSortArray(),
                limit : this.dataLimit,
                filterUrl: this.chartInfo.filterUrl,
                filterQuery: this.chartInfo.filterQuery,
                scope: this
            });
        }

        if (this.chartInfo.displayAggregate)
        {
            //Get data for Aggregates.
            var groups = [];
            for(var i = 0; i < this.chartInfo.subject.groups.length; i++){
                groups.push(this.chartInfo.subject.groups[i].id);
            }

            LABKEY.Visualization.getData({
                success: function(data){
                    this.aggregateData = data;

                    // make sure each measure/dimension has at least some data, and get a list of which visits are in the data response
                    var visitsInData = [];
                    var seriesList = this.getSeriesList();
                    this.aggregateHasData = {};
                    Ext4.each(seriesList, function(s) {
                        this.aggregateHasData[s.name] = false;
                        for (var i = 0; i < this.aggregateData.rows.length; i++)
                        {
                            var row = this.aggregateData.rows[i];
                            if (row[this.getColumnAlias(this.aggregateData.columnAliases, s.aliasLookupInfo)].value != null)
                                this.aggregateHasData[s.name] = true;

                            var visitMappedName = this.getColumnAlias(this.aggregateData.columnAliases, this.viewInfo.subjectNounSingular + "Visit/Visit");
                            if (this.editorXAxisPanel.getTime() == "visit" && row[visitMappedName])
                            {
                                var visitVal = row[visitMappedName].value;
                                if (visitsInData.indexOf(visitVal) == -1)
                                    visitsInData.push(visitVal.toString());
                            }
                        }
                    }, this);

                    this.getNumberFormats(this.aggregateData.metaData.fields);

                    // trim the visit map domain to just those visits in the response data
                    this.aggregateData.visitMap = this.trimVisitMapDomain(this.aggregateData.visitMap, visitsInData);

                    // ready to render the chart or grid
                    this.loaderCount--;
                    if(this.loaderCount == 0){
                        this.loaderFn();
                    }
                },
                failure : function(info, response, options) {
                    LABKEY.Utils.displayAjaxErrorResponse(response, options);
                    this.clearChartPanel("Error: " + info.exception);
                },
                measures: this.chartInfo.measures,
                viewInfo: this.viewInfo,
                groupBys: [{schemaName: 'study', queryName: this.viewInfo.subjectNounSingular + 'GroupMap', name: 'GroupId', values: groups}],
                sorts: this.getDataSortArray(),
                limit : this.dataLimit,
                filterUrl: this.chartInfo.filterUrl,
                filterQuery: this.chartInfo.filterQuery,
                scope: this
            });
        }
    },

    getNumberFormats: function(fields) {
        for(var i = 0; i < this.chartInfo.axis.length; i++){
            var axis = this.chartInfo.axis[i];
            if(axis.side){
                // Find the first measure with the matching side that has a numberFormat.
                for(var j = 0; j < this.chartInfo.measures.length; j++){
                    var measure = this.chartInfo.measures[j].measure;

                    if(this.numberFormats[axis.side]){
                        break;
                    }

                    if(measure.yAxis == axis.side){
                        var metaDataName = measure.alias;
                        for(var k = 0; k < fields.length; k++){
                            var field = fields[k];
                            if(field.name == metaDataName){
                                if(field.extFormatFn){
                                    this.numberFormats[axis.side] = eval(field.extFormatFn);
                                    break;
                                }
                            }
                        }
                    }
                }

                if(!this.numberFormats[axis.side]){
                    // If after all the searching we still don't have a numberformat use the default number format.
                    this.numberFormats[axis.side] = this.defaultNumberFormat;
                }
            }
        }
    },

    trimVisitMapDomain: function(origVisitMap, visitsInDataArr) {
        // get the visit map info for those visits in the response data
        var trimmedVisits = [];
        for (var v in origVisitMap)
        {
            if (visitsInDataArr.indexOf(v) != -1)
                trimmedVisits.push(Ext4.apply({id: v}, origVisitMap[v]));
        }
        // sort the trimmed visit list by displayOrder and then reset displayOrder starting at 1
        trimmedVisits.sort(function(a,b){return a.displayOrder - b.displayOrder});
        var newVisitMap = {};
        for (var i = 0; i < trimmedVisits.length; i++)
        {
            trimmedVisits[i].displayOrder = i + 1;
            newVisitMap[trimmedVisits[i].id] = trimmedVisits[i];
        }

        return newVisitMap;
    },

    getSimplifiedConfig: function(config)
    {
        var defaultConfig = this.getInitializedChartInfo();

        // Here we generate a config that is similar, but strips out info that isnt neccessary.
        // We use this to compare two configs to see if the user made any changes to the chart.
        var simplified = {};
        simplified.chartLayout = config.chartLayout;
        simplified.chartSubjectSelection = config.chartSubjectSelection;
        simplified.displayAggregate = config.displayAggregate;
        simplified.displayIndividual = config.displayIndividual;
        simplified.errorBars = config.errorBars ? config.errorBars : defaultConfig.errorBars;
        simplified.aggregateType = config.aggregateType ? config.aggregateType : defaultConfig.aggregateType;
        simplified.filterUrl = config.filterUrl;
        simplified.title = config.title;
        simplified.hideDataPoints = config.hideDataPoints ? config.hideDataPoints : defaultConfig.hideDataPoints;
        simplified.lineWidth = config.lineWidth ? config.lineWidth : defaultConfig.lineWidth;
        simplified.pointClickFn = config.pointClickFn ? config.pointClickFn : defaultConfig.pointClickFn;

        // compare the relevant axis information
        simplified.axis = [];
        for (var i = 0; i < config.axis.length; i++)
        {
            var currentAxis = config.axis[i];
            var tempAxis = {label: currentAxis.label, name: currentAxis.name};
            tempAxis.range = {type: currentAxis.range.type};
            if (currentAxis.range.type == "manual")
            {
                tempAxis.range.min = currentAxis.range.min;
                tempAxis.range.max = currentAxis.range.max;
            }
            if (currentAxis.scale) tempAxis.scale = currentAxis.scale;
            if (currentAxis.side) tempAxis.side = currentAxis.side;
            simplified.axis.push(tempAxis);
        }

        // compare subject information (this is the standard set for both participant and group selections)
        simplified.subject = this.getSchemaQueryInfo(config.subject);
        simplified.subject.values = config.subject.values;
        // compare groups by labels and participantIds (not id and created date)
        if (config.subject.groups)
        {
            simplified.subject.groups = [];
            for(var i = 0; i < config.subject.groups.length; i++)
                simplified.subject.groups.push({label: config.subject.groups[i].label, participantIds: config.subject.groups[i].participantIds});
        }

        simplified.measures = [];
        for (var i = 0; i < config.measures.length; i++)
        {
            var currentMeasure = config.measures[i];
            var tempMeasure = {time: currentMeasure.time ? currentMeasure.time : "date"};

            tempMeasure.measure = this.getSchemaQueryInfo(currentMeasure.measure);
            if (currentMeasure.measure.aggregate) tempMeasure.measure.aggregate = currentMeasure.measure.aggregate;
            if (currentMeasure.measure.yAxis) tempMeasure.measure.yAxis = currentMeasure.measure.yAxis;

            if (currentMeasure.dimension)
            {
                tempMeasure.dimension = this.getSchemaQueryInfo(currentMeasure.dimension);
                tempMeasure.dimension.values = currentMeasure.dimension.values; 
            }

            if (currentMeasure.dateOptions)
            {
                tempMeasure.dateOptions = {interval: currentMeasure.dateOptions.interval};
                tempMeasure.dateOptions.dateCol = this.getSchemaQueryInfo(currentMeasure.dateOptions.dateCol);
                tempMeasure.dateOptions.zeroDateCol = this.getSchemaQueryInfo(currentMeasure.dateOptions.zeroDateCol);
            }

            simplified.measures.push(tempMeasure);
        }

        return Ext4.encode(simplified);
    },

    getSchemaQueryInfo: function(obj)
    {
        return {name: obj.name, queryName: obj.queryName, schemaName: obj.schemaName};
    },

    renderLineChart: function(force)
    {
        this.maskAndRemoveCharts();

        // get the updated chart information from the various options panels
        this.chartInfo = this.getChartInfoFromOptionPanels();

        if (this.savedChartInfo)
        {
            if (this.getSimplifiedConfig(Ext4.decode(this.savedChartInfo)) == this.getSimplifiedConfig(this.chartInfo))
            {
                this.markDirty(false);
            }
            else
            {
                //Don't mark dirty if the user can't edit the report, that's just mean.
                if (this.canEdit)
                {
                    this.markDirty(true);
                }
            }
        }

        var xAxisIndex = this.getAxisIndex(this.chartInfo.axis, "x-axis");
        var leftAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "left");
        var rightAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "right");
        if(xAxisIndex == -1){
           Ext4.Msg.alert("Error", "Could not find x-axis in chart measure information.");
           return;
        }

        if (this.individualData && this.individualData.filterDescription)
            this.editorMeasurePanel.setFilterWarningText(this.individualData.filterDescription);

        if (this.chartInfo.measures.length == 0)
        {
           this.clearChartPanel("No measure selected. Please click the \"Measures\" button to add a measure.");
           return;
        }

        // show the viewGrid button and hide the viewCharts button
        this.viewChartBtn.hide();
        this.viewGridBtn.show();
        this.loaderFn = this.renderLineChart;
        this.loaderName = 'renderLineChart';

        // warn the user if the data limit has been reached
        if ((this.individualData && this.individualData.rows.length == this.dataLimit) || (this.aggregateData && this.aggregateData.rows.length == this.dataLimit))
        {
            this.addWarningText("The data limit for plotting has been reached. Consider filtering your data.");
        }

	    // one series per y-axis subject/measure/dimensionvalue combination
	    var seriesList = this.getSeriesList();

        // check to see if any of the measures don't have data, and display a message accordingly
        if (force !== true) {
            var msg = ""; var sep = "";
            var noDataCounter = 0;
            Ext4.iterate(this.aggregateHasData ? this.aggregateHasData : this.individualHasData, function(key, value, obj){
                if (!value)
                {
                    noDataCounter++;
                    msg += sep + key;
                    sep = ", ";
                }
            }, this);
            if (msg.length > 0)
            {
                msg = "No data found for the following measures/dimensions: " + msg;

                // if there is no data for any series, error out completely
                if (noDataCounter == seriesList.length)
                {
                    this.clearChartPanel(msg);
                    this.disablePdfExportButtons();
                    return;
                }
                else
                    this.addWarningText(msg);
            }
        }

        // Use the same max/min for every chart if displaying more than one chart.
        if (this.chartInfo.chartLayout != "single")
        {
            //ISSUE In multi-chart case, we need to precompute the default axis ranges so that all charts share them.
            var leftMeasures = [];
            var rightMeasures = [];
            var min, max, tempMin, tempMax;
            var columnAliases = this.individualData ? this.individualData.columnAliases : this.aggregateData.columnAliases;

            for(var i = 0; i < seriesList.length; i++){
                var columnName = this.getColumnAlias(columnAliases, seriesList[i].aliasLookupInfo);
                if(seriesList[i].yAxisSide == "left"){
                    leftMeasures.push(columnName);
                } else if(seriesList[i].yAxisSide == "right"){
                    rightMeasures.push(columnName);
                }
            }

            var xName;
            var xFunc;
            if(this.editorXAxisPanel.getTime() == "date"){
                xName = this.chartInfo.measures[0].dateOptions.interval;
                xFunc = function(row){
                    return row[xName].value;
                };
            } else {
                var visitMap = this.individualData ? this.individualData.visitMap : this.aggregateData.visitMap;
                xName = this.getColumnAlias(columnAliases, this.viewInfo.subjectNounSingular + "Visit/Visit");
                xFunc = function(row){
                    return visitMap[row[xName].value].displayOrder;
                };
            }

            if(!this.chartInfo.axis[xAxisIndex].range.min){
                this.chartInfo.axis[xAxisIndex].range.min = d3.min(this.individualData ? this.individualData.rows : this.aggregateData.rows, xFunc);
            }
            if(!this.chartInfo.axis[xAxisIndex].range.max){
                this.chartInfo.axis[xAxisIndex].range.max = d3.max(this.individualData ? this.individualData.rows : this.aggregateData.rows, xFunc);
            }

            if (leftAxisIndex > -1) {
                // If we have a left axis then we need to find the min/max
                min = null, max = null, tempMin = null, tempMax = null;
                var leftAccessor = function(row){return row[leftMeasures[i]].value};

                if(!this.chartInfo.axis[leftAxisIndex].range.min){
                    for(var i = 0; i < leftMeasures.length; i++){
                        tempMin = d3.min(this.individualData ? this.individualData.rows : this.aggregateData.rows, leftAccessor);
                        min = min == null ? tempMin : tempMin < min ? tempMin : min;
                    }
                    this.chartInfo.axis[leftAxisIndex].range.min = min;
                }
                if(!this.chartInfo.axis[leftAxisIndex].range.max){
                    for(var i = 0; i < leftMeasures.length; i++){
                        tempMax = d3.max(this.individualData ? this.individualData.rows : this.aggregateData.rows, leftAccessor);
                        max = max == null ? tempMax : tempMax > max ? tempMax : max;
                    }
                    this.chartInfo.axis[leftAxisIndex].range.max = max;
                }
            }

            if (rightAxisIndex > -1) {
                // If we have a right axis then we need to find the min/max
                min = null, max = null, tempMin = null, tempMax = null;
                var rightAccessor = function(row){return row[rightMeasures[i]].value};

                if(!this.chartInfo.axis[rightAxisIndex].range.min){
                    for(var i = 0; i < rightMeasures.length; i++){
                        tempMin = d3.min(this.individualData ? this.individualData.rows : this.aggregateData.rows, rightAccessor);
                        min = min == null ? tempMin : tempMin < min ? tempMin : min;
                    }
                    this.chartInfo.axis[rightAxisIndex].range.min = min;
                }
                if(!this.chartInfo.axis[rightAxisIndex].range.max){
                    for(var i = 0; i < rightMeasures.length; i++){
                        tempMax = d3.max(this.individualData ? this.individualData.rows : this.aggregateData.rows, rightAccessor);
                        max = max == null ? tempMax : tempMax > max ? tempMax : max;
                    }
                    this.chartInfo.axis[rightAxisIndex].range.max = max;
                }
            }
        }

        // remove any existing charts, purge listeners from exportPdfSingleBtn, and remove items from the exportPdfMenu button
        this.chart.removeAll();
        this.firstChartComponent = null;
        this.exportPdfSingleBtn.removeListener('click');
        this.exportPdfMenu.removeAll();

        var charts = [];

        var generateGroupSeries = function(data, groups, subjectColumn){
            // subjectColumn is the aliasColumnName looked up from the getData response columnAliases array
            // groups is this.chartInfo.subject.groups
            var rows = data.rows;
            var dataByGroup = {};

            for(var i = 0; i < rows.length; i++){
                var rowSubject = rows[i][subjectColumn].value;
                for(var j = 0; j < groups.length; j++){
                    if(groups[j].participantIds.indexOf(rowSubject) > -1){
                        if(!dataByGroup[groups[j].label]){
                            dataByGroup[groups[j].label] = [];
                        }
                        dataByGroup[groups[j].label].push(rows[i]);
                    }
                }
            }

            return dataByGroup;
        };

        var createExportMenuHandler = function(id){
            return function(){
                LABKEY.vis.SVGConverter.convert(Ext4.get(id).child('svg').dom, 'pdf');
            }
        };

        // warn if user doesn't have an subjects, groups, or series selected
        if (this.chartInfo.chartSubjectSelection == "subjects" && this.chartInfo.subject.values.length == 0)
        {
            this.clearChartPanel("Please select at least one " + this.viewInfo.subjectNounSingular.toLowerCase() + '.');
            this.toggleSaveButtons(true);
        }
        else if (this.chartInfo.chartSubjectSelection == "groups" && this.chartInfo.subject.groups.length < 1)
        {
            this.clearChartPanel("Please select at least one group.");
            this.toggleSaveButtons(true);
        }
        else if (seriesList.length == 0)
        {
            this.clearChartPanel("Please select at least one series/dimension value.");
            this.toggleSaveButtons(true);
        }
        // four options: all series on one chart, one chart per subject, one chart per group, or one chart per measure/dimension
        else
        {
            this.toggleSaveButtons(false);

            if (this.chartInfo.chartLayout == "per_subject")
            {
                // warn if the max number of charts has been exceeded
                if (this.chartInfo.subject.values.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts.");
                }
                var accessor = this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectColumn);
                var dataPerParticipant = LABKEY.vis.groupData(this.individualData.rows, function(row){return row[accessor].value});
                for(var participant in dataPerParticipant){
                    var newChart = this.generatePlot(
                            this.chart,
                            this.editorXAxisPanel.getTime(),
                            this.viewInfo,
                            this.chartInfo,
                            this.chartInfo.title + ': ' + participant,
                            seriesList,
                            dataPerParticipant[participant],
                            this.individualData.columnAliases,
                            this.individualData.visitMap,
                            null,
                            null,
                            null,
                            this.chartInfo.subject.values.length > 1 ? 380 : 600,  // chart height
                            this.chartInfo.subject.values.length > 1 ? 'border-bottom: solid black 1px;' : null // chart style
                        );
                    charts.push(newChart);

                    if(!this.firstChartComponent){
                        this.firstChartComponent = newChart.renderTo;
                    }

                    this.exportPdfMenu.add({
                        text: this.chartInfo.title + ': ' + participant,
                        handler: createExportMenuHandler(newChart.renderTo),
                        scope: this
                    });
                    this.toggleExportPdfBtns(false);

                    if(charts.length > this.maxCharts){
                        break;
                    }
                }
            }
            else if (this.chartInfo.chartLayout == "per_group")
            {
                // warn if the max number of charts has been exceeded
                if (this.chartInfo.subject.groups.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts.");
                }

                //Display individual lines
                var groupedIndividualData;
                if(this.individualData){
                    groupedIndividualData = generateGroupSeries(this.individualData, this.chartInfo.subject.groups, this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectColumn));
                }
                // Display aggregate lines
                var groupedAggregateData;
                if(this.aggregateData){
                    var groupDataAggregate = LABKEY.vis.groupData(this.aggregateData.rows, function(row){return row.GroupId.displayValue});
                }

                for (var i = 0; i < (this.chartInfo.subject.groups.length > this.maxCharts ? this.maxCharts : this.chartInfo.subject.groups.length); i++)
                {
                    var group = this.chartInfo.subject.groups[i];
                    var newChart = this.generatePlot(
                            this.chart,
                            this.editorXAxisPanel.getTime(),
                            this.viewInfo,
                            this.chartInfo,
                            this.chartInfo.title + ': ' + group.label,
                            seriesList,
                            groupedIndividualData && groupedIndividualData[group.label] ? groupedIndividualData[group.label] : null,
                            this.individualData ? this.individualData.columnAliases : null,
                            this.individualData ? this.individualData.visitMap : null,
                            groupDataAggregate && groupDataAggregate[group.label] ? groupDataAggregate[group.label] : null,
                            this.aggregateData ? this.aggregateData.columnAliases : null,
                            this.aggregateData ? this.aggregateData.visitMap : null,
                            this.chartInfo.subject.groups.length > 1 ? 380 : 600, // chart height
                            this.chartInfo.subject.groups.length > 1 ? 'border-bottom: solid black 1px;' : null // chart style
                        );
                    charts.push(newChart);

                    if(!this.firstChartComponent){
                        this.firstChartComponent = newChart.renderTo;
                    }

                    this.exportPdfMenu.add({
                        text: this.chartInfo.title + ': ' + group.label,
                        handler: createExportMenuHandler(newChart.renderTo),
                        scope: this
                    });
                    this.toggleExportPdfBtns(false);

                    if(charts.length > this.maxCharts){
                        break;
                    }
                }
            }
            else if (this.chartInfo.chartLayout == "per_dimension")
            {
                // warn if the max number of charts has been exceeded
                if (seriesList.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts");
                }
                for (var i = 0; i < (seriesList.length > this.maxCharts ? this.maxCharts : seriesList.length); i++)
                {
                    var newChart = this.generatePlot(
                            this.chart,
                            this.editorXAxisPanel.getTime(),
                            this.viewInfo,
                            this.chartInfo,
                            this.chartInfo.title + ': ' + seriesList[i].name,
                            [seriesList[i]],
                            this.individualData ? this.individualData.rows : null,
                            this.individualData ? this.individualData.columnAliases : null,
                            this.individualData ? this.individualData.visitMap : null,
                            this.aggregateData ? this.aggregateData.rows : null,
                            this.aggregateData ? this.aggregateData.columnAliases : null,
                            this.aggregateData ? this.aggregateData.visitMap : null,
                            seriesList.length > 1 ? 380 : 600,  // chart height
                            seriesList.length > 1 ? 'border-bottom: solid black 1px;' : null // chart style
                        );
                    charts.push(newChart);

                    if(!this.firstChartComponent){
                        this.firstChartComponent = newChart.renderTo;
                    }

                    this.exportPdfMenu.add({
                        text: this.chartInfo.title + ': ' + seriesList[i].name,
                        handler: createExportMenuHandler(newChart.renderTo),
                        scope: this
                    });
                    this.toggleExportPdfBtns(false);

                    if(charts.length > this.maxCharts){
                        break;
                    }
                }
            }
            else if (this.chartInfo.chartLayout == "single")
            {
                //Single Line Chart, with all participants or groups.
                var newChart = this.generatePlot(
                        this.chart,
                        this.editorXAxisPanel.getTime(),
                        this.viewInfo,
                        this.chartInfo,
                        this.chartInfo.title,
                        seriesList,
                        this.individualData ? this.individualData.rows : null,
                        this.individualData ? this.individualData.columnAliases : null,
                        this.individualData ? this.individualData.visitMap : null,
                        this.aggregateData ? this.aggregateData.rows : null,
                        this.aggregateData ? this.aggregateData.columnAliases : null,
                        this.aggregateData ? this.aggregateData.visitMap : null,
                        610,    // chart height
                        null    // chart style
                );
                charts.push(newChart);

                this.firstChartComponent = newChart.renderTo;

                this.exportPdfSingleBtn.addListener('click', function(){
                    LABKEY.vis.SVGConverter.convert(Ext4.get(this.firstChartComponent).child('svg').dom, 'pdf');
                }, this);

                this.toggleExportPdfBtns(true);
            }
        }

        // show warning message, if there is one
        if (this.warningText.length > 0)
        {
            this.chart.insert(0, Ext4.create('Ext.panel.Panel', {
                border: false,
                padding: 10,
                html : "<table width='100%'><tr><td align='center' style='font-style:italic'>" + this.warningText + "</td></tr></table>"
            }));
        }

        if (this.firstChartComponent && Raphael.svg)
        {
            // pass the svg for the first chart component to the save options panel for use in the thumbnail preview
            this.editorSavePanel.updateCurrentChartThumbnail(LABKEY.vis.SVGConverter.svgToStr(Ext4.get(this.firstChartComponent).child('svg').dom), Ext4.get(this.firstChartComponent).getSize());
        }

        this.unmaskPanel();
    },

    generatePlot: function(chart, studyType, viewInfo, chartInfo, mainTitle, seriesList, individualData, individualColumnAliases, individualVisitMap, aggregateData, aggregateColumnAliases, aggregateVisitMap, chartHeight, chartStyle){
        // This function generates a plot config and renders a plot for given data.
        // Should be used in per_subject, single, per_measure, and per_group
        var generateLayerAes = function(name, yAxisSide, columnName){
            var yName = yAxisSide == "left" ? "yLeft" : "yRight";
            var aes = {};
            aes[yName] = function(row){return parseFloat(row[columnName].value)}; // Have to parseFlot because for some reason ObsCon from Luminex was returning strings not floats/ints.
            return aes;
        };

        var generateAggregateLayerAes = function(name, yAxisSide, columnName, intervalKey, subjectColumn, errorColumn){
            var yName = yAxisSide == "left" ? "yLeft" : "yRight";
            var aes = {};
            aes[yName] = function(row){return parseFloat(row[columnName].value)}; // Have to parseFloat because for some reason ObsCon from Luminex was returning strings not floats/ints.
            aes.group = aes.color = aes.shape = function(row){return row[subjectColumn].displayValue};
            aes.error = function(row){return row[errorColumn].value};
            return aes;
        };

        var hoverTextFn = function(subjectColumn, intervalKey, name, columnName, visitMap, errorColumn, errorType){
            if(visitMap){
                if(errorColumn){
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        var errorVal = row[errorColumn].value ? row[errorColumn].value : 'n/a';
                        return ' ' + subject + ',\n '+ visitMap[row[intervalKey].value].displayName + ',\n ' + name + ': ' + row[columnName].value +
                                ',\n ' + errorType + ': ' + errorVal;
                    }
                } else {
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        return ' ' + subject + ',\n '+ visitMap[row[intervalKey].value].displayName + ',\n ' + name + ': ' + row[columnName].value;
                    };
                }
            } else {
                if(errorColumn){
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        var errorVal = row[errorColumn].value ? row[errorColumn].value : 'n/a';
                        return ' ' + subject + ',\n ' + intervalKey + ': ' + row[intervalKey].value + ',\n ' + name + ': ' + row[columnName].value +
                                ',\n ' + errorType + ': ' + errorVal;
                    }
                } else {
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        return ' ' + subject + ',\n ' + intervalKey + ': ' + row[intervalKey].value + ',\n ' + name + ': ' + row[columnName].value;
                    };
                }
            }
        };

        // create a new function from the pointClickFn string provided by the developer
        if (chartInfo.pointClickFn)
        {
            // the developer is expected to return a function, so we encapalate it within the anonymous function
            // (note: the function should have already be validated in a try/catch when applied via the developerOptionsPanel)
            var devPointClickFn = new Function("", "return " + chartInfo.pointClickFn);
        }

        var pointClickFn = function(columnMap, measureInfo) {
            return function(clickEvent, data) {
                // call the developers function, within the anonymous function, with the params as defined for the developer                 
                devPointClickFn().call(this, data, columnMap, measureInfo, clickEvent);
            }
        };

        var layers = [];
        var xTitle = '', yLeftTitle = '', yRightTitle = '';
        var yLeftMin = null, yLeftMax = null, yLeftTrans = null, yLeftTickFormat;
        var yRightMin = null, yRightMax = null, yRightTrans = null, yRightTickFormat;
        var xMin = null, xMax = null, xTrans = null;
        var intervalKey = null;
        var individualSubjectColumn = individualColumnAliases ? this.getColumnAlias(individualColumnAliases, viewInfo.subjectColumn) : null;
        var aggregateSubjectColumn = "GroupId";
        var xAes, xTickFormat, tickMap = {};
        var visitMap = individualVisitMap ? individualVisitMap : aggregateVisitMap;

        for(var rowId in visitMap){
            tickMap[visitMap[rowId].displayOrder] = visitMap[rowId].displayName;
        }

        if(studyType == 'date'){
            intervalKey = chartInfo.measures[seriesList[0].measureIndex].dateOptions.interval;
            xAes = function(row){return row[intervalKey].value}
        } else {
            intervalKey = individualColumnAliases ?
                    this.getColumnAlias(individualColumnAliases, viewInfo.subjectNounSingular + "Visit/Visit") :
                    this.getColumnAlias(aggregateColumnAliases, viewInfo.subjectNounSingular + "Visit/Visit");
            xAes = function(row){
                return visitMap[row[intervalKey].value].displayOrder;
            };
            xTickFormat = function(value){
                return tickMap[value] ? tickMap[value] : "";
            }
        }

        var newChartDiv = Ext4.create('Ext.container.Container', {
            height: chartHeight,
            style: chartStyle ? chartStyle : 'border: none;',
            autoEl: {tag: 'div'}
        });
        chart.add(newChartDiv);

        for(var i = 0; i < chartInfo.axis.length; i++){
            var axis = chartInfo.axis[i];
            if(axis.name == "y-axis"){
                if(axis.side == "left"){
                    yLeftTitle = axis.label;
                    yLeftMin = typeof axis.range.min == "number" ? axis.range.min : null;
                    yLeftMax = typeof axis.range.max == "number" ? axis.range.max : null;
                    yLeftTrans = axis.scale ? axis.scale : "linear";
                    yLeftTickFormat = chartInfo.numberFormats.left ? chartInfo.numberFormats.left : null;
                } else {
                    yRightTitle = axis.label;
                    yRightMin = typeof axis.range.min == "number" ? axis.range.min : null;
                    yRightMax = typeof axis.range.max == "number" ? axis.range.max : null;
                    yRightTrans = axis.scale ? axis.scale : "linear";
                    yRightTickFormat = chartInfo.numberFormats.right ? chartInfo.numberFormats.right : null;
                }
            } else {
                xTitle = axis.label;
                xMin = typeof axis.range.min == "number" ? axis.range.min : null;
                xMax = typeof axis.range.max == "number" ? axis.range.max : null;
                xTrans = axis.scale ? axis.scale : "linear";
            }
        }

        // Issue 15369: if two measures have the same name, use the alias for the subsequent series names (which will be unique)
        // Issue 12369: if rendering two measures of the same pivoted value, use measure and pivot name for series names (which will be unique)
        var useUniqueSeriesNames = false;
        var uniqueChartSeriesNames = [];
        for (var i = 0; i < seriesList.length; i++)
        {
            if (uniqueChartSeriesNames.indexOf(seriesList[i].name) > -1)
            {
                useUniqueSeriesNames = true;
                break;
            }
            uniqueChartSeriesNames.push(seriesList[i].name);
        }

        for (var i = seriesList.length -1; i >= 0; i--)
        {
            var chartSeries = seriesList[i];

            var chartSeriesName = chartSeries.name;
            if (useUniqueSeriesNames)
            {
                if (chartSeries.aliasLookupInfo.pivotValue)
                    chartSeriesName = chartSeries.aliasLookupInfo.measureName + " " + chartSeries.aliasLookupInfo.pivotValue;    
                else
                    chartSeriesName = chartSeries.aliasLookupInfo.alias;
            }

            var columnName = individualColumnAliases ? this.getColumnAlias(individualColumnAliases, chartSeries.aliasLookupInfo) : this.getColumnAlias(aggregateColumnAliases, chartSeries.aliasLookupInfo);
            if(individualData && individualColumnAliases){
                var pathLayerConfig = {
                    name: chartSeriesName,
                    geom: new LABKEY.vis.Geom.Path({size: chartInfo.lineWidth}),
                    aes: generateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName)
                };
                layers.push(new LABKEY.vis.Layer(pathLayerConfig));

                if(!chartInfo.hideDataPoints){
                    var pointLayerConfig = {
                        name: chartSeriesName,
                        geom: new LABKEY.vis.Geom.Point(),
                        aes: generateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName)
                    };
                    if(studyType == 'date'){
                        pointLayerConfig.aes.hoverText = hoverTextFn(individualSubjectColumn, intervalKey, chartSeriesName, columnName, null, null, null);
                    } else {
                        pointLayerConfig.aes.hoverText = hoverTextFn(individualSubjectColumn, intervalKey, chartSeriesName, columnName, visitMap, null, null);
                    }

                    if (chartInfo.pointClickFn)
                    {
                        pointLayerConfig.aes.pointClickFn = pointClickFn(
                            {participant: individualSubjectColumn, interval: intervalKey, measure: columnName},
                            {schemaName: chartSeries.schemaName, queryName: chartSeries.queryName, name: chartSeriesName}
                        );
                    }

                    layers.push(new LABKEY.vis.Layer(pointLayerConfig));
                }
            }

            if(aggregateData && aggregateColumnAliases){
                var errorBarType = null;
                if(chartInfo.errorBars == 'SD'){
                    errorBarType = '_STDDEV';
                } else if(chartInfo.errorBars == 'SEM'){
                    errorBarType = '_STDERR';
                }
                var errorColumnName = errorBarType ? this.getColumnAlias(aggregateColumnAliases, chartSeries.aliasLookupInfo) + errorBarType : null;

                var aggregatePathLayerConfig = {
                    name: chartSeriesName,
                    data: aggregateData,
                    geom: new LABKEY.vis.Geom.Path({size: chartInfo.lineWidth}),
                    aes: generateAggregateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, errorColumnName)
                };
                layers.push(new LABKEY.vis.Layer(aggregatePathLayerConfig));

                if(errorColumnName){
                    var aggregateErrorLayerConfig = {
                        name: chartSeriesName,
                        data: aggregateData,
                        geom: new LABKEY.vis.Geom.ErrorBar(),
                        aes: generateAggregateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, errorColumnName)
                    };
                    layers.push(new LABKEY.vis.Layer(aggregateErrorLayerConfig));
                }

                if(!chartInfo.hideDataPoints){
                    var aggregatePointLayerConfig = {
                        name: chartSeriesName,
                        data: aggregateData,
                        geom: new LABKEY.vis.Geom.Point(),
                        aes: generateAggregateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, errorColumnName)
                    };
                    if(studyType == 'date'){
                        aggregatePointLayerConfig.aes.hoverText = hoverTextFn(aggregateSubjectColumn, intervalKey, chartSeriesName, columnName, null, errorColumnName, chartInfo.errorBars)
                    } else {
                        aggregatePointLayerConfig.aes.hoverText = hoverTextFn(aggregateSubjectColumn, intervalKey, chartSeriesName, columnName, visitMap, errorColumnName, chartInfo.errorBars);
                    }
                    if (chartInfo.pointClickFn)
                    {
                        aggregatePointLayerConfig.aes.pointClickFn = pointClickFn(
                            {group: aggregateSubjectColumn, interval: intervalKey, measure: columnName},
                            {schemaName: chartSeries.schemaName, queryName: chartSeries.queryName, name: chartSeriesName}
                        );
                    }
                    layers.push(new LABKEY.vis.Layer(aggregatePointLayerConfig));
                }
            }
        }

        // functions to call on click of axis labels to open the options panel (need to be closures to correctly handle scoping of this)
        var xAxisLabelClickFn = function(scopedThis){
            return function(event){
                scopedThis.chartElementClicked(scopedThis.editorXAxisPanel, [event.clientX, event.clientY], 800, 250, 'above');
            }
        };
        var yAxisLeftLabelClickFn = function(scopedThis){
            return function(event){
                scopedThis.chartElementClicked(scopedThis.editorYAxisLeftPanel, [event.clientX, event.clientY], 320, 220, 'left');
            }
        };
        var yAxisRightLabelClickFn = function(scopedThis){
            return function(event){
                scopedThis.chartElementClicked(scopedThis.editorYAxisRightPanel, [event.clientX, event.clientY], 320, 220, 'right');
            }
        };
        var mainTitleClickFn = function(scopedThis){
            return function(event){
                scopedThis.chartElementClicked(scopedThis.editorMainTitlePanel, [event.clientX, event.clientY], 300, 130, 'below');
            }
        };

        var plotConfig = {
            renderTo: newChartDiv.getId(),
            clipRect: true,
            labels: {
                main: {
                    value: mainTitle,
                    lookClickable: true,
                    listeners: {
                        click: mainTitleClickFn(this)
                    }
                },
                x: {
                    value: xTitle,
                    lookClickable: true,
                    listeners: {
                        click: xAxisLabelClickFn(this)
                    }
                },
                yLeft: {
                    value: yLeftTitle,
                    lookClickable: true,
                    listeners: {
                        click: yAxisLeftLabelClickFn(this)
                    }
                },
                yRight: {
                    value: yRightTitle,
                    lookClickable: true,
                    listeners: {
                        click: yAxisRightLabelClickFn(this)
                    }
                }
            },
            layers: layers,
            aes: {
                x: xAes,
                color: function(row){return row[individualSubjectColumn].value},
                group: function(row){return row[individualSubjectColumn].value},
                shape: function(row){
                    return row[individualSubjectColumn].value
                }
            },
            scales: {
                x: {
                    scaleType: 'continuous',
                    trans: xTrans,
                    min: xMin,
                    max: xMax,
                    tickFormat: xTickFormat ? xTickFormat : null
                },
                yLeft: {scaleType: 'continuous',
                    trans: yLeftTrans,
                    min: yLeftMin,
                    max: yLeftMax,
                    tickFormat: yLeftTickFormat ? yLeftTickFormat : null
                },
                yRight: {
                    scaleType: 'continuous',
                    trans: yRightTrans,
                    min: yRightMin,
                    max: yRightMax,
                    tickFormat: yRightTickFormat ? yRightTickFormat : null
                },
                shape: {
                    scaleType: 'discrete'
                }
            },
            width: newChartDiv.getWidth() - 20, // -20 prevents horizontal scrollbars in cases with multiple charts.
            height: newChartDiv.getHeight() - 20, // -20 prevents vertical scrollbars in cases with one chart.
            data: individualData ? individualData : aggregateData
        };

        var plot = new LABKEY.vis.Plot(plotConfig);
        plot.render();
        return plot;
    },

    getDataSortArray: function(){
        var arr = [this.chartInfo.subject]
        var md = this.chartInfo.measures[0];

        var sort1 = {
            schemaName: md.dateOptions? md.dateOptions.dateCol.schemaName : md.measure.schemaName,
            queryName: md.dateOptions ? md.dateOptions.dateCol.queryName : md.measure.queryName,
            name: this.editorXAxisPanel.getTime() == "date" ? md.dateOptions.dateCol.name : this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"
        };
        arr.push(sort1);

        var sort2 = {
            schemaName: md.dateOptions? md.dateOptions.dateCol.schemaName : md.measure.schemaName,
            queryName: md.dateOptions ? md.dateOptions.dateCol.queryName : md.measure.queryName,
            name: this.viewInfo.subjectNounSingular + "Visit/Visit"
        };
        arr.push(sort2);

        return arr;
    },

    getSeriesList: function(){
        var arr = [];
        for (var i = 0; i < this.chartInfo.measures.length; i++)
        {
            var md = this.chartInfo.measures[i];

            if(md.dimension && md.dimension.values) {
                Ext4.each(md.dimension.values, function(val) {
                    arr.push({
                        schemaName: md.dimension.schemaName,
                        queryName: md.dimension.queryName,
                        name: val,
                        measureIndex: i,
                        yAxisSide: md.measure.yAxis,
                        aliasLookupInfo: {measureName: md.measure.name, pivotValue: val}
                    });
                });
            }
            else {
                arr.push({
                    schemaName: md.measure.schemaName,
                    queryName: md.measure.queryName,
                    name: md.measure.name,
                    measureIndex: i,
                    yAxisSide: md.measure.yAxis,
                    aliasLookupInfo: md.measure.alias ? {alias: md.measure.alias} : {measureName: md.measure.name}
                });
            }
        }
        return arr;
    },

    /*
    * Lookup the column alias (from the getData response) by the specified measure information
    * aliasArray: columnAlias array from the getData API response
    * measureInfo: 1. a string with the name of the column to lookup
    *              2. an object with a measure alias OR measureName
     *             3. an object with both measureName AND pivotValue
     */
    getColumnAlias: function(aliasArray, measureInfo) {
        if (typeof measureInfo != "object")
            measureInfo = {measureName: measureInfo};
        for (var i = 0; i < aliasArray.length; i++)
        {
            var arrVal = aliasArray[i];

            if (measureInfo.measureName && measureInfo.pivotValue)
            {
                if (arrVal.measureName == measureInfo.measureName && arrVal.pivotValue == measureInfo.pivotValue)
                    return arrVal.columnName;
            }
            else if (measureInfo.alias)
            {
                if (arrVal.alias == measureInfo.alias)
                    return arrVal.columnName;
            }
            else if (measureInfo.measureName && arrVal.measureName == measureInfo.measureName)
                return arrVal.columnName;
        }
        return null;
    },

    toggleExportPdfBtns: function(showSingle) {
        if(showSingle){
            this.exportPdfSingleBtn.show();
            this.exportPdfSingleBtn.setDisabled(false);
            this.exportPdfMenuBtn.hide();
            this.exportPdfMenuBtn.setDisabled(true);
        }
        else{
            this.exportPdfSingleBtn.hide();
            this.exportPdfSingleBtn.setDisabled(true);
            this.exportPdfMenuBtn.show();
            this.exportPdfMenuBtn.setDisabled(false);
        }
    },

    viewDataGrid: function() {
        // make sure the tempGridInfo is available
        if(typeof this.tempGridInfo == "object") {
            this.maskAndRemoveCharts();
            this.loaderFn = this.viewDataGrid;
            this.loaderName = 'viewDataGrid';

            // hide the viewGrid button and show the viewCharts button
            this.viewChartBtn.disable();
            this.viewChartBtn.show();
            this.viewGridBtn.hide();

            // add a panel to put the queryWebpart in
            var qwpPanelDiv = Ext4.create('Ext.container.Container', {
                autoHeight: true,
                anchor: '100%',
                autoEl: {tag: 'div'}
            });
            var dataGridPanel = Ext4.create('Ext.panel.Panel', {
                autoScroll: true,
                border: false,
                padding: 10,
                items: [
                    {
                        xtype: 'displayfield',
                        value: 'Note: filters applied to the data grid will not be reflected in the chart view.',
                        style: 'font-style:italic;padding:10px'
                    },
                    qwpPanelDiv
                ]
            });

            // create the queryWebpart using the temp grid schema and query name
            var chartQueryWebPart = new LABKEY.QueryWebPart({
                renderTo: qwpPanelDiv.getId(),
                schemaName: this.tempGridInfo.schema,
                queryName: this.tempGridInfo.query,
                sort: this.tempGridInfo.subjectCol + ', ' + this.tempGridInfo.sortCols.join(", "),
                allowChooseQuery: false,
                allowChooseView: false,
                title: "",
                frame: "none"
            });

            // re-enable the View Charts button once the QWP has rendered
            chartQueryWebPart.on('render', function(){
                this.viewChartBtn.enable();

                // redo the layout of the qwp panel to set reset the auto height
                qwpPanelDiv.doLayout();

                this.unmaskPanel();
            }, this);

            this.chart.removeAll();
            this.chart.add(dataGridPanel);
        }
    },

    maskAndRemoveCharts: function() {
        // mask panel and remove the chart(s)
        if (!this.chart.getEl().isMasked())
        {
            this.chart.getEl().mask("loading...");
            this.clearChartPanel();
        }
    },

    unmaskPanel: function() {
        // unmask the panel if needed
        if (this.chart.getEl().isMasked())
            this.chart.getEl().unmask();
    },

    getInitializedChartInfo: function(){
        return {
            measures: [],
            axis: [],
            chartLayout: 'single',
            chartSubjectSelection: 'subjects',
            lineWidth: 3,
            hideDataPoints: false,
            pointClickFn: null,
            errorBars: "None",
            aggregateType: "Mean",
            subject: {},
            title: '',
            filterUrl: LABKEY.Visualization.getDataFilterFromURL(),
            filterQuery: this.getFilterQuery()
        }
    },

    getChartInfoFromOptionPanels: function(){
        var config = {};

        // get the chart grouping information
        Ext4.apply(config, this.editorGroupingPanel.getPanelOptionValues());

        // get the chart aesthetic options information
        Ext4.apply(config, this.editorAestheticsPanel.getPanelOptionValues());

        // get the developer options information
        Ext4.apply(config, this.editorDeveloperPanel.getPanelOptionValues());

        // get the main title from the option panel
        Ext4.apply(config, this.editorMainTitlePanel.getPanelOptionValues());

        // get the measure panel information
        var measurePanelValues = this.editorMeasurePanel.getPanelOptionValues();

        config.measures = [];
        config.axis = [];
        config.filterUrl = measurePanelValues.dataFilterUrl;
        config.filterQuery = measurePanelValues.dataFilterQuery;

        // get the subject info based on the selected chart layout
        if (config.chartSubjectSelection == 'groups')
            config.subject = this.groupsSelector.getSubject();
        else
            config.subject = this.participantSelector.getSubject();

        // get the x-axis information (including zero date column info)
        var xAxisValues = this.editorXAxisPanel.getPanelOptionValues();
        config.axis.push(xAxisValues.axis);

        // get the measure and dimension information for the y-axis (can be > 1 measure)
        var hasLeftAxis = false;
        var hasRightAxis = false;
        for(var i = 0; i < measurePanelValues.measuresAndDimensions.length; i++){
            var tempMD = {
                measure: measurePanelValues.measuresAndDimensions[i].measure,
                dimension: measurePanelValues.measuresAndDimensions[i].dimension,
                time: xAxisValues.time
            };

            if (tempMD.time == "date")
            {
                tempMD.dateOptions = {
                    dateCol: measurePanelValues.measuresAndDimensions[i].dateCol,
                    zeroDateCol: xAxisValues.zeroDateCol,
                    interval: xAxisValues.interval
                };
            }

            config.measures.push(tempMD);

            // add the left/right axis information to the config accordingly
            if (measurePanelValues.measuresAndDimensions[i].measure.yAxis == 'right' && !hasRightAxis)
            {
                config.axis.push(this.editorYAxisRightPanel.getPanelOptionValues());
                hasRightAxis = true;
            }
            else if (measurePanelValues.measuresAndDimensions[i].measure.yAxis == 'left' && !hasLeftAxis)
            {
                config.axis.push(this.editorYAxisLeftPanel.getPanelOptionValues());
                hasLeftAxis = true;
            }
        }

        // the subject column is used in the sort, so it needs to be applied to one of the measures
        if (config.measures.length > 0)
        {
            Ext4.apply(config.subject, {
                name: this.viewInfo.subjectColumn,
                schemaName: config.measures[0].measure.schemaName,
                queryName: config.measures[0].measure.queryName
            });
        }

        config.numberFormats = this.numberFormats;

        return config;
    },

    getAxisIndex: function(axes, axisName, side){
        var index = -1;
        for(var i = 0; i < axes.length; i++){
            if (!side && axes[i].name == axisName)
            {
                index = i;
                break;
            }
            else if (axes[i].name == axisName && axes[i].side == side)
            {
                index = i;
                break;
            }
        }
        return index;
    },

    saveChart: function(saveChartInfo) {
        // if queryName and schemaName are set on the URL then save them with the chart info
        var schema = this.saveReportInfo ? this.saveReportInfo.schemaName : (LABKEY.ActionURL.getParameter("schemaName") || null);
        var query = this.saveReportInfo ? this.saveReportInfo.queryName : (LABKEY.ActionURL.getParameter("queryName") || null);

        var reportSvg = (this.firstChartComponent && Raphael.svg ? LABKEY.vis.SVGConverter.svgToStr(Ext4.get(this.firstChartComponent).child('svg').dom) : null);

        var config = {
            replace: saveChartInfo.replace,
            reportName: saveChartInfo.reportName,
            reportDescription: saveChartInfo.reportDescription,
            reportShared: saveChartInfo.shared,
            reportThumbnailType: saveChartInfo.thumbnailType,
            reportSvg: saveChartInfo.thumbnailType == 'AUTO' ? reportSvg : null,
            createdBy: saveChartInfo.createdBy,
            query: query,
            schema: schema
        };

        // if user clicked save button to replace an existing report, execute the save chart call
        // otherwise, the user clicked save for a new report (or save as) so check if the report name already exists
        if(!saveChartInfo.isSaveAs && saveChartInfo.replace){
            this.executeSaveChart(config);
        }
        else{
            this.checkSaveChart(config);
        }
    },

    checkSaveChart: function(config){
        // see if a report by this name already exists within this container
        LABKEY.Visualization.get({
            name: config.reportName,
            success: function(result, request, options){
                // a report by that name already exists within the container, if the user can update, ask if they would like to replace
                if(this.canEdit && config.replace){
                    Ext4.Msg.show({
                        title:'Warning',
                        msg: 'A report by the name \'' + $h(config.reportName) + '\' already exists. Would you like to replace it?',
                        buttons: Ext4.Msg.YESNO,
                        fn: function(btnId, text, opt){
                            if(btnId == 'yes'){
                                config.replace = true;
                                this.executeSaveChart(config);
                            }
                        },
                        icon: Ext4.MessageBox.WARNING,
                        scope: this
                    });
                }
                else{
                    Ext4.Msg.show({
                        title:'Error',
                        msg: 'A report by the name \'' + $h(config.reportName) + '\' already exists.  Please choose a different name.',
                        buttons: Ext4.Msg.OK,
                        icon: Ext4.MessageBox.ERROR
                    });
                }
            },
            failure: function(errorInfo, response){
                // no report exists with that name
                this.executeSaveChart(config);
            },
            scope: this
        });
    },

    executeSaveChart: function(config){
        // get the chart info to be saved
        this.chartInfo = this.getChartInfoFromOptionPanels();

        LABKEY.Visualization.save({
            name: config.reportName,
            description: config.reportDescription,
            shared: config.reportShared,
            visualizationConfig: this.chartInfo,
            thumbnailType: config.reportThumbnailType,
            svg: config.reportSvg,
            replace: config.replace,
            type: LABKEY.Visualization.Type.TimeChart,
            success: this.saveChartSuccess(config.replace, config.reportName),
            schemaName: config.schema,
            queryName: config.query,
            scope: this
        });
    },

    saveChartSuccess: function (replace, reportName){
        return function(result, request, options) {
            this.markDirty(false);

            var msgBox = Ext4.create('Ext.window.Window', {
                title    : 'Success',
                html     : '<div style="margin-left: auto; margin-right: auto;"><span class="labkey-message">The chart has been successfully saved.</span></div>',
                modal    : false,
                closable : false,
                width    : 300,
                height   : 100
            });
            msgBox.show();
            msgBox.getEl().fadeOut({duration : 2250, callback : function(){
                msgBox.hide();
            }});

            // if a new chart was created, we need to refresh the page with the correct report name on the URL
            if (!this.editorSavePanel.isSavedReport() || !replace)
            {
                window.location = LABKEY.ActionURL.buildURL("visualization", "timeChartWizard",
                                    LABKEY.ActionURL.getContainer(),
                                    Ext4.apply(LABKEY.ActionURL.getParameters(), {
                                        reportId: result.visualizationId,
                                        name: reportName
                                    }));
            }
        }
    },

    // clear the chart panel of any messages, charts, or grids
    // if displaying a message, also make sure to unmask the time chart wizard element
    clearChartPanel: function(message){
        this.chart.removeAll();
        this.clearWarningText();
        if (message)
        {
            this.chart.add(Ext4.create('Ext.panel.Panel', {
                border: false,
                padding: 10,
                html : "<table width='100%'><tr><td align='center' style='font-style:italic'>" + message + "</td></tr></table>"
            }));
            this.unmaskPanel();
        }
    },

    clearWarningText: function(){
        this.warningText = "";
    },

    addWarningText: function(message){
        if (this.warningText.length > 0)
            this.warningText += "<BR/>";
        this.warningText += message;
    }
});
