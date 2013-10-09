/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.TimeChartScriptPanel', {
    extend: 'LABKEY.vis.BaseExportScriptPanel',

    SCRIPT_TEMPLATE:
            "<div id='exportedChart'></div>\n" +
            "<script type='text/javascript'>\n" +
            "// Wrap in function to prevent leaking variables into global namespace.\n" +
            "(function() {\n" +
            "    var CHART_ID = 'exportedChart';\n" +
            "    var DEFAULT_DATA_LIMIT = 10000;\n" +
            "    var DEFAULT_MAX_CHARTS = 20;\n" +
            "    var DEFAULT_NUMBER_FORMAT = function(v) { return v.toFixed(1); };\n" +
            "    var DEFAULT_WIDTH = 1075;\n" +
            "    var DEFAULT_SINGLE_CHART_HEIGHT = 600;\n" +
            "    var DEFAULT_MULTI_CHART_HEIGHT = 380;\n" +
            "\n" +
            "    var loadVisDependencies = function(callback, scope) {\n" +
            "        var devScripts = [\n" +
            "            '/vis/lib/d3-2.0.4.min.js',\n" +
            "            '/vis/lib/raphael-min-2.1.0.js',\n" +
            "            '/vis/lib/patches.js',\n" +
            "            '/vis/src/utils.js',\n" +
            "            '/vis/src/geom.js',\n" +
            "            '/vis/src/stat.js',\n" +
            "            '/vis/src/scale.js',\n" +
            "            '/vis/src/layer.js',\n" +
            "            '/vis/src/plot.js',\n" +
            "            '/vis/timeChart/timeChartHelper.js'\n" +
            "        ];\n" +
            "        var productionScripts = [\n" +
            "            '/vis/lib/d3-2.0.4.min.js',\n" +
            "            '/vis/lib/raphael-min-2.1.0.js',\n" +
            "            '/vis/vis.min.js',\n" +
            "            '/vis/timeChart/timeChartHelper.js'\n" +
            "        ];\n" +
            "        LABKEY.requiresScript((LABKEY.devMode ? devScripts : productionScripts), true, callback, scope, true);\n" +
            "    };\n" +
            "\n" +
            "    // chartConfig is the saved information about the chart (measures, dimensions, labels, scales, etc.)\n" +
            "    var chartConfig = {{chartConfig}};\n" +
            "\n" +
            "    var renderMessages = function(id, messages) {\n" +
            "        var messageDiv;\n" +
            "        var el = document.getElementById(id);\n" +
            "        var child;\n" +
            "        if (el && el.children.length > 0)\n" +
            "            child = el.children[0];\n" +
            "\n" +
            "        for (var i = 0; i < messages.length; i++)\n" +
            "        {\n" +
            "            messageDiv = document.createElement('div');\n" +
            "            messageDiv.setAttribute('style', 'font-style:italic');\n" +
            "            messageDiv.innerHTML = messages[i];\n" +
            "            if (child)\n" +
            "                el.insertBefore(messageDiv, child);\n" +
            "            else\n" +
            "                el.appendChild(messageDiv);\n" +
            "        }\n" +
            "    };\n" +
            "\n" +
            "    var selectRowsCallback = function(responseData) {\n" +
            "        // After the data is loaded we can render the chart(s).\n" +
            "        var TCH = LABKEY.vis.TimeChartHelper;\n" +
            "        var individualColumnAliases = responseData.individual ? responseData.individual.columnAliases : null;\n" +
            "        var aggregateColumnAliases = responseData.aggregate ? responseData.aggregate.columnAliases : null;\n" +
            "        var visitMap = responseData.individual ? responseData.individual.visitMap : responseData.aggregate.visitMap;\n" +
            "        var intervalKey = TCH.generateIntervalKey(chartConfig, individualColumnAliases, aggregateColumnAliases);\n" +
            "        var aes = TCH.generateAes(chartConfig, visitMap, individualColumnAliases, intervalKey);\n" +
            "        var tickMap = TCH.generateTickMap(visitMap);\n" +
            "        var seriesList = TCH.generateSeriesList(chartConfig.measures);\n" +
            "        var applyClipRect = TCH.generateApplyClipRect(chartConfig);\n" +
            "\n" +
            "        // Once we have the data, we can set all of the axis min/max range values\n" +
            "        TCH.generateAcrossChartAxisRanges(chartConfig, responseData, seriesList);\n" +
            "        var scales = TCH.generateScales(chartConfig, tickMap, responseData.numberFormats);\n" +
            "\n" +
            "        // Validate that the chart data has expected values and give warnings if certain elements are not present\n" +
            "        var messages = [];\n" +
            "        var validation = TCH.validateChartData(responseData, this.chartInfo, seriesList, DEFAULT_DATA_LIMIT, false);\n" +
            "        if (validation.message != null)\n" +
            "        {\n" +
            "            messages.push(validation.message);\n" +
            "        }\n" +
            "        if (!validation.success)\n" +
            "        {\n" +
            "            renderMessages(CHART_ID, messages);\n" +
            "            return;\n" +
            "        }\n" +
            "\n" +
            "        // For time charts, we allow multiple plots to be displayed by participant, group, or measure/dimension\n" +
            "        var plotConfigsArr = TCH.generatePlotConfigs(chartConfig, responseData, seriesList, applyClipRect, DEFAULT_MAX_CHARTS);\n" +
            "        for (var configIndex = plotConfigsArr.length - 1; configIndex >= 0; configIndex--)\n" +
            "        {\n" +
            "            var clipRect = plotConfigsArr[configIndex].applyClipRect;\n" +
            "            var series = plotConfigsArr[configIndex].series;\n" +
            "            var height = plotConfigsArr.length > 1 ? DEFAULT_MULTI_CHART_HEIGHT : DEFAULT_SINGLE_CHART_HEIGHT;\n" +
            "            var labels = TCH.generateLabels(plotConfigsArr[configIndex].title, chartConfig.axis);\n" +
            "            var layers = TCH.generateLayers(chartConfig, visitMap, individualColumnAliases, aggregateColumnAliases, plotConfigsArr[configIndex].aggregateData, series, intervalKey);\n" +
            "            var data = plotConfigsArr[configIndex].individualData ? plotConfigsArr[configIndex].individualData : plotConfigsArr[configIndex].aggregateData;\n" +
            "\n" +
            "            var plotConfig = {\n" +
            "                renderTo: CHART_ID,\n" +
            "                clipRect: clipRect,\n" +
            "                width: DEFAULT_WIDTH,\n" +
            "                height: height,\n" +
            "                labels: labels,\n" +
            "                aes: aes,\n" +
            "                scales: scales,\n" +
            "                layers: layers,\n" +
            "                data: data\n" +
            "            };\n" +
            "\n" +
            "            var plot = new LABKEY.vis.Plot(plotConfig);\n" +
            "            plot.render();\n" +
            "        }\n" +
            "\n" +
            "        // Give a warning if the max number of charts has been exceeded\n" +
            "        if (plotConfigsArr.length >= DEFAULT_MAX_CHARTS)\n" +
            "            messages.push('Only showing the first ' + DEFAULT_MAX_CHARTS + ' charts.');\n" +
            "\n" +
            "        renderMessages(CHART_ID, messages);\n" +
            "    };\n" +
            "\n" +
            "    var dependencyCallback = function() {\n" +
            "        var TCH = LABKEY.vis.TimeChartHelper;\n" +
            "\n" +
            "        // Before we load the data, validate some information about the chart config\n" +
            "        var messages = [];\n" +
            "        var validation = TCH.validateChartConfig(chartConfig);\n" +
            "        if (validation.message != null)\n" +
            "        {\n" +
            "            messages.push(validation.message);\n" +
            "        }\n" +
            "        if (!validation.success)\n" +
            "        {\n" +
            "            renderMessages(CHART_ID, messages);\n" +
            "            return;\n" +
            "        }\n" +
            "\n" +
            "        // When all the dependencies are loaded, we load the data using time chart helper getChartData\n" +
            "        var queryConfig = {\n" +
            "            chartInfo: chartConfig,\n" +
            "            dataLimit: DEFAULT_DATA_LIMIT,\n" +
            "            defaultNumberFormat: DEFAULT_NUMBER_FORMAT,\n" +
            "            success: selectRowsCallback,\n" +
            "            failure: function(info) {\n" +
            "                renderMessages(CHART_ID, ['Error: ' + info.exception]);\n" +
            "            }\n" +
            "        };\n" +
            "        TCH.getChartData(queryConfig);\n" +
            "    };\n" +
            "\n" +
            "    loadVisDependencies(dependencyCallback);\n" +
            "})();\n" +
            "</script>"
});