/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

LABKEY.requiresScript("vis/chartEditorOverviewPanel.js");
LABKEY.requiresScript("vis/chartEditorMeasurePanel.js");
LABKEY.requiresScript("vis/chartEditorYAxisPanel.js");
LABKEY.requiresScript("vis/chartEditorXAxisPanel.js");
LABKEY.requiresScript("vis/chartEditorChartsPanel.js");
LABKEY.requiresScript("vis/subjectSeriesSelector.js");
LABKEY.requiresCss("_images/icons.css");


Ext.QuickTips.init();

LABKEY.vis.TimeChartPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){
        Ext.apply(this, config);

        LABKEY.vis.TimeChartPanel.superclass.constructor.call(this);
    },

    initComponent : function() {
        // properties for this panel
        this.layout = 'border';
        this.isMasked = false;
        this.maxCharts = 30;

        // chartInfo will be all of the information needed to render the line chart (axis info and data)
        if(typeof this.chartInfo != "object") {
            this.chartInfo = this.getInitializedChartInfo();
        }

        // hold on to the x and y axis measure index
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");

        // add a listener to call measureSelected on render if this is a saved chart
        this.listeners = {
            scope: this,
            'render': function(){
                if(typeof this.saveReportInfo == "object") {
                    this.measureSelected(this.chartInfo.measures[yAxisMeasureIndex].measure, false);
                }
            }
        };

        // keep track of requests for measure metadata ajax calls to know when all are complete
        this.measureMetadataRequestCounter = 0;

        var items = [];

        if(this.viewInfo.type == "line") {
            this.editorOverviewPanel = new LABKEY.vis.ChartEditorOverviewPanel({
                reportInfo: this.saveReportInfo,
                listeners: {
                    scope: this,
                    'initialMeasureSelected': function(initMeasure) {
                        Ext.getCmp('chart-editor-tabpanel').activate(this.editorMeasurePanel.getId());
                        this.measureSelected(initMeasure, true);
                    },
                    'saveChart': this.saveChart
                }
            });

            this.editorMeasurePanel = new LABKEY.vis.ChartEditorMeasurePanel({
                disabled: true,
                origMeasures: this.chartInfo.measures, 
                filterUrl: this.chartInfo.filterUrl ? this.chartInfo.filterUrl : LABKEY.Visualization.getDataFilterFromURL(),
                viewInfo: this.viewInfo,
                listeners: {
                    scope: this,
                    'measureSelected': this.measureSelected,
                    'dimensionSelected': function(hasDimension){
                        this.editorChartsPanel.disableDimensionOption(hasDimension);
                    },
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    },
                    'filterCleared': function () {
                        //measureMetadataRequestComplete should ensure full refresh after subject list is regenerated.
                        var measure = this.editorMeasurePanel.measure;
                        this.chartInfo.filterUrl = null;
                        this.subjectSelector.getSubjectValues(measure.schemaName, measure.queryName, this.chartInfo.filterUrl);
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.editorXAxisPanel = new LABKEY.vis.ChartEditorXAxisPanel({
                disabled: true,
                axis: this.chartInfo.measures[xAxisMeasureIndex].axis,
                dateOptions: this.chartInfo.measures[xAxisMeasureIndex].dateOptions,
                measure: this.chartInfo.measures[xAxisMeasureIndex].measure,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.editorYAxisPanel = new LABKEY.vis.ChartEditorYAxisPanel({
                disabled: true,
                axis: this.chartInfo.measures[yAxisMeasureIndex].axis,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    }
                }
            });

            this.editorChartsPanel = new LABKEY.vis.ChartEditorChartsPanel({
                disabled: true,
                chartLayout: this.chartInfo.chartLayout,
                mainTitle: this.chartInfo.title,
                lineWidth: this.chartInfo.lineWidth,
                hideDataPoints: this.chartInfo.hideDataPoints,
                subjectNounSingular: this.viewInfo.subjectNounSingular,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    }
                }
            });

            this.chartEditor = new Ext.Panel({
                layout: 'fit',
                header: false,
                region: 'north',
                height: 210,
                border: false,
                split: true,
                collapsible: true,
                collapseMode: 'mini',
                hideCollapseTool: true,
                items: [
                    new Ext.TabPanel({
                        id: 'chart-editor-tabpanel',
                        padding: 4,
                        activeTab: 0,
                        items: [
                            this.editorOverviewPanel,
                            this.editorMeasurePanel,
                            this.editorXAxisPanel,
                            this.editorYAxisPanel,
                            this.editorChartsPanel
                        ]
                    })
                ],
                tbar: {
                    height: 25,
                    style:{backgroundColor :'#ffffff'},
                    items:[
                        '->',
                        {
                            iconCls:'iconClose',
                            tooltip:'Close the chart editor tab panel.',
                            handler: function(){
                                this.chartEditor.collapse();
                            },
                            scope: this
                        }
                    ]
                }
            });
            items.push(this.chartEditor);

            this.subjectSelector = new LABKEY.vis.SubjectSeriesSelector({
                subject: this.chartInfo.subject,
                subjectNounPlural: this.viewInfo.subjectNounPlural,
                subjectColumn: this.viewInfo.subjectColumn,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.seriesSelector = new Ext.Panel({
                region: 'east',
                layout: 'fit',
                width: 200,
                border: false,
                split: true,
                collapsible: true,
                collapseMode: 'mini',
                hideCollapseTool: true,
                header: false,
                items: [
                    new Ext.TabPanel({
                        id: 'series-selector-tabpanel',
                        activeTab: 0,
                        padding: 5,
                        enablePanelScroll: true,
                        items: [this.subjectSelector]
                    })
                ],
                tbar: {
                    height: 25,
                    style:{backgroundColor :'#ffffff'},
                    items:[
                        '->',
                        {
                            iconCls:'iconClose',
                            tooltip:'Close the series selector tab panel.',
                            handler: function(){
                                this.seriesSelector.collapse();
                            },
                            scope: this
                        }
                    ]
                }
            });
            items.push(this.seriesSelector);

            this.loader = this.renderLineChart;  // default is to show the chart
            this.viewGridBtn = new Ext.Button({text: "View Data", handler: this.viewDataGrid, scope: this, disabled: true});
            this.viewChartBtn = new Ext.Button({text: "View Chart(s)", handler: this.renderLineChart, scope: this, hidden: true});

            // setup exportPDF button and menu (items to be added later)
            // the single button will be used for "single" chart layout
            // and the menu button will be used for multi-chart layouts
            this.exportPdfMenu = new Ext.menu.Menu({cls: 'extContainer'});
            this.exportPdfMenuBtn = new Ext.Button({
                text: 'Export PDF',
                menu: this.exportPdfMenu,
                hidden: true,
                scope: this
            });
            this.exportPdfSingleBtn = new Ext.Button({
                text: 'Export PDF',
                disabled: true,
                scope: this
            });

            this.chart = new Ext.Panel({
                id: 'chart-tabpanel',
                region: 'center',
                layout: 'fit',
                frame: false,
                autoScroll: true,
                tbar: [this.viewGridBtn, this.viewChartBtn, '-', this.exportPdfSingleBtn, this.exportPdfMenuBtn],
                items: [],
                listeners: {
                    scope: this,
                    'resize': function(cmp){
                        // only call loader if the data object is available
                        if(this.chartSubjectData) {
                            this.loader();
                        }
                    }
                }
            });
            items.push(this.chart);
        }
        else
        {
            // other chart types
        }

        this.items = items;

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);

        LABKEY.vis.TimeChartPanel.superclass.initComponent.apply(this, arguments);
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

    measureSelected: function(measure, userSelectedMeasure) {
        // if this is a measure selection from the "Change" or "Choose a Measure" button, then initialize the chartInfo object
        if(userSelectedMeasure){ // todo: this reset needs to be removed when we start adding multiple measures
            this.chartInfo = this.getInitializedChartInfo();
        }

        // get the axis measure index for easier access
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");

        // update chart definition information based on measure selection
        if(userSelectedMeasure){ // todo: changes to calls that shoudl only be made for userSelectedMeasures
            this.editorMeasurePanel.addMeasure(measure);
        }

        // get the subject values for this measure's query
        // todo: should this be reloaded when we start adding multiple measures?
        this.subjectSelector.getSubjectValues(measure.schemaName, measure.queryName, this.chartInfo.filterUrl);

        // todo: these things shouldn't be changed for each added measure
        this.editorXAxisPanel.setZeroDateStore(measure.schemaName);
        this.editorChartsPanel.setChartLayout(this.chartInfo.chartLayout);
        this.editorChartsPanel.disableDimensionOption((this.chartInfo.measures[yAxisMeasureIndex].dimension ? true : false));

        // update chart editor form values based on selected measure
        this.editorMeasurePanel.setDimensionStore(this.chartInfo.measures[yAxisMeasureIndex].dimension);
        this.editorXAxisPanel.setMeasureDateStore(measure.schemaName, measure.queryName);
        this.editorXAxisPanel.setRange(this.chartInfo.measures[xAxisMeasureIndex].axis.range.type);
        this.editorYAxisPanel.setRange(this.chartInfo.measures[yAxisMeasureIndex].axis.range.type);
        this.editorYAxisPanel.setScale(this.chartInfo.measures[yAxisMeasureIndex].axis.scale);
        if(userSelectedMeasure){
            this.editorYAxisPanel.setLabel(measure.label);

            // todo: these things shouldn't be changed for each added measure
            this.editorOverviewPanel.updateOverview(this.saveReportInfo);
            this.editorChartsPanel.setMainTitle(measure.queryName);
            this.editorChartsPanel.setLineWidth(this.chartInfo.lineWidth);
            this.editorChartsPanel.setHideDataPoints(this.chartInfo.hideDataPoints);
        }

        this.enableTabPanels();
    },

    enableTabPanels: function(){
        // enable the charteditor tabs in the tabPanel
        this.editorMeasurePanel.enable();
        this.editorXAxisPanel.enable();
        this.editorYAxisPanel.enable();
        this.editorChartsPanel.enable();
    },

    isDirty : function() {
        return this.dirty;
    },

    markDirty : function(value) {
        this.dirty = value;
    },

    measureMetadataRequestPending:  function() {
        // mask panel and remove the chart(s)
        this.maskChartPanel();

        // increase the request counter
        this.measureMetadataRequestCounter++;
    },

    measureMetadataRequestComplete: function() {
        // decrease the request counter
        this.measureMetadataRequestCounter--;

        // if all requests are complete, call getChartData
        if(this.measureMetadataRequestCounter == 0) {
            this.getChartData();
        }
    },

    getChartData: function() {
        // mask panel and remove the chart(s)
        this.maskChartPanel();

        // get the updated chart information from the varios tabs of the chartEditor
        this.chartInfo = this.getChartInfoFromEditorTabs();
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        if(xAxisMeasureIndex == -1){
           Ext.Msg.alert("Error", "Could not find x-axis in chart measure information.");
           return;
        }

        LABKEY.Visualization.getData({
            success: function(data){
                // store the data in an object by subject for use later when it comes time to render the line chart
                this.chartSubjectData = new Object();
                this.chartSubjectData.filterDescription = data.filterDescription;
                this.markDirty(!this.editorOverviewPanel.isSavedReport()); // only mark when editing unsaved report

                // make sure each measure has at least some data
                this.hasData = {};
                Ext.iterate(data.measureToColumn, function(key, value, obj){
                    this.hasData[key] = false;
                }, this);
                
                Ext.each(data.rows, function(row){
                    // get the subject id from the data row
                    var rowSubject = row[data.measureToColumn[this.viewInfo.subjectColumn]];
                    if(rowSubject.value ) rowSubject = rowSubject.value;

                    // if this is a new subject to the chartSubjectData object, then initialize it
                    if(!this.chartSubjectData[rowSubject]) {
                        this.chartSubjectData[rowSubject] = new Object();

                        // initialize an array for each element in measureToColumn
                        Ext.iterate(data.measureToColumn, function(key, value, obj){
                            this.chartSubjectData[rowSubject][key] = new Array();
                        }, this);
                    }

                    // add the data value and interval value to the appropriate place in the chartSubjectData object
                    Ext.iterate(data.measureToColumn, function(key, value, obj){
                        var dataValue = row[value];
                        if(typeof dataValue != "object") {
                            dataValue = {value: dataValue};
                        }

                        // This measure has data
                        if (dataValue.value) this.hasData[key] = true;

                        this.chartSubjectData[rowSubject][key].push({
                            interval: row[this.chartInfo.measures[xAxisMeasureIndex].dateOptions.interval],
                            dataValue: dataValue
                        });
                    }, this);
                }, this);

                // store the temp schema name, query name, etc. for the data grid
                this.tempGridInfo = {schema: data.schemaName, query: data.queryName,
                        subjectCol: data.measureToColumn[this.viewInfo.subjectColumn],
                        intervalCol: this.chartInfo.measures[xAxisMeasureIndex].dateOptions.interval
                };

                // now that we have the temp grid info, enable the View Data button
                // and make sure that the view charts button is hidden
                this.viewGridBtn.setDisabled(false);
                this.viewChartBtn.hide();

                // ready to render the chart
                this.loader();              
            },
            failure : function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            measures: this.chartInfo.measures,
            viewInfo: this.viewInfo,
            sorts: [this.chartInfo.subject, this.chartInfo.measures[xAxisMeasureIndex].measure],
            filterUrl: this.chartInfo.filterUrl,
            filterQuery: this.getFilterQuery(),
            scope: this
        });
    },

    renderLineChart: function(force)
    {
        // mask panel and remove the chart(s)
        this.maskChartPanel();

        if (this.chartSubjectData.filterDescription)
            this.editorMeasurePanel.setFilterWarningText(this.chartSubjectData.filterDescription);

        // get the updated chart information from the varios tabs of the chartEditor
        this.chartInfo = this.getChartInfoFromEditorTabs();
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        if(yAxisMeasureIndex == -1){
           Ext.Msg.alert("Error", "Could not find y-axis in chart measure information.");
           return;
        }
        else if(yAxisMeasureIndex == -1){
           Ext.Msg.alert("Error", "Could not find x-axis in chart measure information.");
           return;
        }

        // show the viewGrid button and hide the viewCharts button
        this.viewChartBtn.hide();
        this.viewGridBtn.show();
        this.loader = this.renderLineChart;

        if (force !== true) {
            var msg = ""; var sep = "";
            Ext.iterate(this.hasData, function(key, value, obj){
                if (!value && key == this.chartInfo.measures[yAxisMeasureIndex].measure.name) {
                    msg += sep + this.chartInfo.measures[yAxisMeasureIndex].measure.label + " (Y-Axis)";
                    sep = ", ";
                }
                else if (!value && key == this.chartInfo.measures[xAxisMeasureIndex].measure.name) {
                    msg += sep + this.chartInfo.measures[xAxisMeasureIndex].measure.label + " (X-Axis)";
                    sep = ", ";
                }
            }, this);
            if (msg.length > 0) { this.maskChartPanel("No data found in: " + msg + " for the selected " + this.viewInfo.subjectNounPlural + "."); return; }
        }

	    // one series per subject/measure/dimensionvalue combination
	    var seriesList = [];
	    if(this.chartInfo.measures[yAxisMeasureIndex].dimension && this.chartInfo.measures[yAxisMeasureIndex].dimension.values) {
	        seriesList = this.chartInfo.measures[yAxisMeasureIndex].dimension.values;
	    }
	    else {
	        seriesList = [this.chartInfo.measures[yAxisMeasureIndex].measure.name];
	    }

        var series = [];
        for(var j = 0; j < this.chartInfo.subject.values.length; j++)
        {
        	for(var i = 0; i < seriesList.length; i++)
        	{
                var yAxisSeries = seriesList[i];
                var subject = this.chartInfo.subject.values[j];

                var caption = subject;
                if(this.chartInfo.measures[yAxisMeasureIndex].dimension || this.chartInfo.chartLayout != "single"){
                    caption += " " + yAxisSeries;
                }

                var style = {lineWidth: this.chartInfo.lineWidth};
                if(this.chartInfo.hideDataPoints){
                    style.shape = {name: "square", lineWidth: 1, markSize: 20, hidden: true};
                }

                series.push({
                    subject: subject,
                    yAxisSeries: yAxisSeries,
                    caption: caption,
                    data: this.chartSubjectData[subject] ? this.chartSubjectData[subject][yAxisSeries] : [],
                    xProperty:"interval",
                    yProperty: "dataValue",
                    style: style
                });
            }
        }

        var size = {width: (this.chart.getInnerWidth() * .95), height: (this.chart.getInnerHeight() * .97)};

        if (this.chartInfo.chartLayout != "single")
        {
            //ISSUE In multi-chart case, we need to precompute the default axis ranges so that all charts share them.
            //Should have more of this capability in ChartComponent (essentially need to build a single chart with all data)
            //but didn't want to refactor that code substantially..
            var allX = [];
            var allY = [];
            Ext.each(series, function (ser) {
                Ext.each(ser.data, function(row) {
                    var xValue = row.interval;
                    var yValue = row.dataValue;
                    if (xValue != null && typeof xValue == "object")
                        xValue = xValue.value;
                    if (yValue != null && typeof yValue == "object")
                        yValue = yValue.value;
                    if (xValue != null && xValue != null) {
                        allX.push(xValue);
                        allY.push(yValue);
                    }
                })
            });
            this.autoAxisRange = {
                x:LABKEY.vis.getAxisRange(allX, this.chartInfo.measures[xAxisMeasureIndex].axis.scale),
                y:LABKEY.vis.getAxisRange(allY, this.chartInfo.measures[yAxisMeasureIndex].axis.scale)
            };
        }
        else   //Use an undefined min & max so that chart computes it
            this.autoAxisRange = {x:{}, y:{}}; //Let the chart compute this

        // remove any existing charts, purge listeners from exportPdfSingleBtn, and remove items from the exportPdfMenu button
        this.chart.removeAll();
        this.exportPdfSingleBtn.purgeListeners();
        this.exportPdfMenu.removeAll();

	    // three options: all series on one chart, one chart per subject, or one chart per measure/dimension
        var charts = [];
        var warningText = null;
        if(this.chartInfo.chartLayout == "per_subject") {
        	for(var i = 0; i < (this.chartInfo.subject.values.length > this.maxCharts ? this.maxCharts : this.chartInfo.subject.values.length); i++){
        	    var subject = this.chartInfo.subject.values[i];
        		charts.push(this.newLineChart(size, series, {parameter: "subject", value: subject}, subject));
        	}

            // warn if user doesn't have an subjects selected or if the max number has been exceeded
            if(this.chartInfo.subject.values.length == 0){
                warningText = "Please select at least one subject.";
            }
        	else if(this.chartInfo.subject.values.length > this.maxCharts){
        	    warningText = "Only showing the first " + this.maxCharts + " charts.";
        	}
        }
        else if(this.chartInfo.chartLayout == "per_dimension") {
        	for(var i = 0; i < (seriesList.length > this.maxCharts ? this.maxCharts : seriesList.length); i++){
        	    var md = seriesList[i];
        		charts.push(this.newLineChart(size, series, {parameter: "yAxisSeries", value: md}, md));
        	}

            // warn if user doesn't have an dimension values selected or if the max number has been exceeded
            if(seriesList.length == 0){
                warningText = "Please select at least one dimension value.";
            }
        	else if(seriesList.length > this.maxCharts){
        	    warningText = "Only showing the first " + this.maxCharts + " charts";
        	}
        }
        else if(this.chartInfo.chartLayout == "single")
            charts.push(this.newLineChart(size, series, null, null));

        // if the user has selected more charts than the max allowed, show warning
        if(warningText){
            this.chart.add(new Ext.form.DisplayField({
                autoHeight: 25,
                autoWidth: true,
                value: warningText,
                style: "font-style:italic;width:100%;padding:5px;text-align:center;"
            }));
        };

        this.chart.add(charts);
        this.chart.doLayout();
    },

    newLineChart: function(size, series, seriesFilter, title)
    {
        // hold on to the x and y axis measure index
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");

    	// if seriesFilter is not null, then create a sub-array for that filter
    	var tempSeries = [];
    	if(seriesFilter) {
            for(var i = 0; i < series.length; i++) {
                if(series[i][seriesFilter.parameter] == seriesFilter.value) {
                    series[i].caption = series[i].caption.replace(seriesFilter.value, "");
                    tempSeries.push(series[i]);
                }
            }
    	}

    	// set the title for this chart based on the Chart Title entered by the user and the ptid/dimension layout option
    	var mainTitle = this.chartInfo.title + (this.chartInfo.title != "" && title != null ? ": " : "") + (title ? title : "");

        var chartComponent = new LABKEY.vis.LineChart({
            width: size.width,
            height: size.height - 25,
            axes: {
                y: {
                    min: (typeof this.chartInfo.measures[yAxisMeasureIndex].axis.range.min == "number"
                                ? this.chartInfo.measures[yAxisMeasureIndex].axis.range.min
                                : this.autoAxisRange.y.min),
                    max: (typeof this.chartInfo.measures[yAxisMeasureIndex].axis.range.max == "number"
                                ? this.chartInfo.measures[yAxisMeasureIndex].axis.range.max
                                : this.autoAxisRange.y.max),
                    caption: this.chartInfo.measures[yAxisMeasureIndex].axis.label,
                    scale: this.chartInfo.measures[yAxisMeasureIndex].axis.scale
                },
                x: {
                    min: (typeof this.chartInfo.measures[xAxisMeasureIndex].axis.range.min == "number"
                                ? this.chartInfo.measures[xAxisMeasureIndex].axis.range.min
                                : this.autoAxisRange.x.min),
                    max: (typeof this.chartInfo.measures[xAxisMeasureIndex].axis.range.max == "number"
                                ? this.chartInfo.measures[xAxisMeasureIndex].axis.range.max
                                : this.autoAxisRange.x.max),
                    caption: this.chartInfo.measures[xAxisMeasureIndex].axis.label
                }
            },
            series: tempSeries.length > 0 ? tempSeries : series,
            title: mainTitle
        });

        // if the chart component is exportable, either add a listener to the exportPdfSingleBtn or add an item to the exportPdfMenuBtn
        if (chartComponent.canExport()) {
            if(this.chartInfo.chartLayout == "single"){
                // for a single chart, just add a listener to the button
                this.exportPdfSingleBtn.addListener('click', function(){
                    chartComponent.exportImage("pdf");
                }, this);

                this.toggleExportPdfBtns(true);
            }
            else{
                // add an item to the export pdf menu
                this.exportPdfMenu.add({
                    text: mainTitle,
                    handler: function() {chartComponent.exportImage("pdf");},
                    scope: this
                });

                this.toggleExportPdfBtns(false);
            }
        }
        
        return new Ext.Panel({items: chartComponent});
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
            // mask panel and remove the chart(s)
            this.maskChartPanel();
            this.loader = this.viewDataGrid;

            // hide the viewGrid button and show the viewCharts button
            this.viewChartBtn.show();
            this.viewGridBtn.hide();

            // add a panel to put the queryWebpart in
            var gridPanelId = Ext.id();
            var dataGridPanel = new Ext.Panel({
                autoScroll: true,
                padding: 10,
                items: [{
                    xtype: 'displayfield',
                    value: 'Note: filters applied to the data grid will not be reflected in the chart view.',
                    style: 'font-style:italic;padding:10px'
                },
                {
                    xtype: 'panel',
                    id: gridPanelId
                }]
            });

            // create the queryWebpart using the temp grid schema and query name
            var chartQueryWebPart = new LABKEY.QueryWebPart({
                renderTo: gridPanelId,
                schemaName: this.tempGridInfo.schema,
                queryName: this.tempGridInfo.query,
                sort: this.tempGridInfo.subjectCol + ', ' + this.tempGridInfo.intervalCol,
                allowChooseQuery: false,
                allowChooseView: false,
                title: "",
                frame: "none"
            });

            this.chart.removeAll();
            this.chart.doLayout();
            this.chart.add(dataGridPanel);
            this.chart.doLayout();
        }
    },

    getInitializedChartInfo: function(){
        return {
            measures: [
                {axis: {multiSelect: "false", name: "x-axis", label: "", timeAxis: "true", range: {type: 'automatic'}}, dateOptions: {interval: 'Days'}, measure: {}},
                {axis: {multiSelect: "false", name: "y-axis", label: "", scale: "linear", range: {type: 'automatic'}}, measure: {}}
            ],
            chartLayout: 'single',
            lineWidth: 4,
            hideDataPoints: false,
            subject: {},
            title: '',
            filterUrl: LABKEY.Visualization.getDataFilterFromURL(),
            filterQuery: this.getFilterQuery()
        }
    },

    getChartInfoFromEditorTabs: function(){
        return {
            title: this.editorChartsPanel.getMainTitle(),
            chartLayout: this.editorChartsPanel.getChartLayout(),
            lineWidth: this.editorChartsPanel.getLineWidth(),
            hideDataPoints: this.editorChartsPanel.getHideDataPoints(),
            subject: this.subjectSelector.getSubject(),
            measures: [
                { // x-axis information
                    axis: this.editorXAxisPanel.getAxis(),
                    dateOptions: this.editorXAxisPanel.getDateOptions(),
                    measure: this.editorXAxisPanel.getMeasure()
                },
                { // y-axis information
                    axis: this.editorYAxisPanel.getAxis(),
                    measure: this.editorMeasurePanel.getMeasure(),
                    dimension: this.editorMeasurePanel.getDimension()
                }
            ],
            filterUrl: this.editorMeasurePanel.getDataFilterUrl(),
            filterQuery: this.getFilterQuery()
        }
    },

    getMeasureIndex: function(measures, axis){
        var index = -1;
        for(var i = 0; i < measures.length; i++){
            if(measures[i].axis.name == axis){
                index = i;
                break;
            }
        }
        return index;
    },

    saveChart: function(saveBtnName, replace, reportName, reportDescription, reportShared, canSaveSharedCharts, createdBy) {
        // if queryName and schemaName are set on the URL then save them with the chart info
        var schema = LABKEY.ActionURL.getParameter("schemaName") || null;
        var query = LABKEY.ActionURL.getParameter("queryName") || null;

        // if the Save button was clicked, save the report using the name and description provided
        if(saveBtnName == 'Save'){
            var config = {
                replace: replace,
                reportName: reportName,
                reportDescription: reportDescription,
                reportShared: reportShared,
                createdBy: createdBy,
                query: query,
                schema: schema
            };

            // if user clicked save button to replace an existing report, execute the save chart call
            // otherwise, the user clicked save for a new report so check if the report name already exists
            if(replace){
                this.executeSaveChart(config);
            }
            else{
                this.checkSaveChart(config);
            }
        }
        // if the Save As button was clicked, open a window for user to enter new report name and description
        else if(saveBtnName == 'Save As'){
            // basic form to get the name and description from the user
            var vizSaveForm = new Ext.FormPanel({
                monitorValid: true,
                border: false,
                frame: false,
                labelWidth: 125,
                items: [{
                    xtype: 'textfield',
                    fieldLabel: 'Report Name',
                    name: 'reportName',
                    id: 'reportNameSaveAs',
                    value: reportName || null,
                    width: 300,
                    allowBlank: false,
                    maxLength: 50
                },
                {
                    xtype: 'textarea',
                    fieldLabel: 'Report Description',
                    name: 'reportDescription',
                    id: 'reportDescriptionSaveAs',
                    value: reportDescription || null,
                    width: 300,
                    height: 70,
                    allowBlank: true
                },
                new Ext.form.RadioGroup({
                    name: 'reportShared',
                    id: 'reportSharedSaveAs',
                    fieldLabel: 'Viewable by',
                    anchor: '100%',
                    items : [
                            { name: 'reportShared', id: 'reportSharedAllSaveAs', boxLabel: 'All readers', inputValue: 'true', checked: reportShared, disabled: !canSaveSharedCharts },
                            { name: 'reportShared', id: 'reportSharedMeSaveAs', boxLabel: 'Only me', inputValue: 'false', checked: !reportShared, disabled: !canSaveSharedCharts }
                        ]
                })],
                buttons: [{
                    text: 'Save',
                    formBind: true,
                    handler: function(btn, evnt){
                        var formValues = vizSaveForm.getForm().getValues();
                        var shared = typeof formValues.reportShared == "string" ? 'true' == formValues.reportShared : new Boolean(formValues.reportShared).valueOf();

                        // call fnctn to check if a report of that name already exists
                        this.checkSaveChart({
                            replace: replace,
                            reportName: formValues.reportName,
                            reportDescription: formValues.reportDescription,
                            reportShared: shared,
                            query: query,
                            schema: schema
                        });

                        win.hide();
                    },
                    scope: this
                },
                {
                    text: 'Cancel',
                    handler: function(){
                        win.hide();
                    }
                }]
            });

            // pop-up window for user to enter viz name and description for saving
            var win = new Ext.Window({
                layout:'fit',
                width:475,
                height:230,
                closeAction:'hide',
                modal: true,
                padding: 15,
                title: saveBtnName,
                items: vizSaveForm
            });
            win.show(this);
        }
    },

    checkSaveChart: function(config){
        // see if a report by this name already exists within this container
        LABKEY.Visualization.get({
            name: config.reportName,
            success: function(result, request, options){
                // a report by that name already exists within the container, if the user can update, ask if they would like to replace
                if(this.editorOverviewPanel.canSaveChanges()){
                    Ext.Msg.show({
                        title:'Warning',
                        msg: 'A report by the name \'' + config.reportName + '\' already exists. Would you like to replace it?',
                        buttons: Ext.Msg.YESNO,
                        fn: function(btnId, text, opt){
                            if(btnId == 'yes'){
                                config.replace = true;
                                this.executeSaveChart(config);
                            }
                        },
                        icon: Ext.MessageBox.WARNING,
                        scope: this
                    });
                }
                else{
                    Ext.Msg.show({
                        title:'Error',
                        msg: 'A report by the name \'' + config.reportName + '\' already exists.  Please choose a different name.',
                        buttons: Ext.Msg.OK,
                        icon: Ext.MessageBox.ERROR
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
        LABKEY.Visualization.save({
            name: config.reportName,
            description: config.reportDescription,
            shared: config.reportShared,
            visualizationConfig: this.chartInfo,
            replace: config.replace,
            type: LABKEY.Visualization.Type.TimeChart,
            success: this.saveChartSuccess(config.reportName,
                                           config.reportDescription,
                                           config.reportShared,
                                           config.reportShared ? undefined : LABKEY.Security.currentUser.id,
                                           config.createdBy),
            schemaName: config.schema,
            queryName: config.query,
            scope: this
        });
    },

    saveChartSuccess: function (reportName, reportDescription, reportShared, ownerId, createdBy){
        return function(result, request, options) {
            this.markDirty(false);
            this.editorOverviewPanel.updateOverview({name: reportName, description: reportDescription, shared: reportShared, ownerId: ownerId, createdBy: createdBy});
            Ext.Msg.alert("Success", "The chart has been successfully saved.");
        }
    },

    // WORKAROUND: this is a workaround way of masking the chart panel since the ExtJS mask/unmask
    // results in the chart image being masked in IE (even after unmask is called)
    maskChartPanel: function(message){
        if(!this.isMasked){
            var msg = message || "Loading...";
            this.chart.removeAll();
            this.chart.add(new Ext.Panel({
                padding: 10,
                html : "<table width='100%'><tr><td align='center' style='font-style:italic'>" + msg + "</td></tr></table>"
            }));
            this.chart.doLayout();
        }
    }
});
