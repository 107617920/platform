/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2010-2012 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

/**
 * @namespace Visualization static class to programmatically retrieve visualization-ready data.  Also allows
 * persistence of various visualization types.
 */
LABKEY.Visualization = new function() {

    function formatParams(config)
    {
        var params = {};

        if (config.filters && config.filters.length)
        {
            params['filters'] = config.filters;
        }

        if (config.dateMeasures !== undefined)
            params.dateMeasures = config.dateMeasures;

        if (config.allColumns !== undefined)
            params.allColumns = config.allColumns;
        
        return params;
    }

    // Create a thin wrapper around the success callback capable of converting the persisted (string/JSON) visualization
    // configuration into a native JavaScript object before handing it off to the caller:
    function getVisualizatonConfigConverterCallback(successCallback, scope)
    {
        return function(data, response, options)
        {
            //ensure data is JSON before trying to decode
            var json = null;
            if (data && data.getResponseHeader && data.getResponseHeader('Content-Type')
                    && data.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
            {
                json = Ext.util.JSON.decode(data.responseText);
                if (json.visualizationConfig)
                    json.visualizationConfig = Ext.util.JSON.decode(json.visualizationConfig);
            }

            if(successCallback)
                successCallback.call(scope || this, json, response, options);
        }
    }

    /**
     * This is used internally to automatically parse returned JSON and call another success function. It is based off of
     * LABKEY.Utils.getCallbackWrapper, however, it will call the createMeasureFn function before calling the success function.
     * @param createMeasureFn
     * @param fn
     * @param scope
     */
    function getSuccessCallbackWrapper(createMeasureFn, fn, scope)
    {
        return function(response, options)
        {
            //ensure response is JSON before trying to decode
            var json = null;
            var measures = null;
            if (response && response.getResponseHeader && response.getResponseHeader('Content-Type')
                    && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
            {
                json = Ext.util.JSON.decode(response.responseText);
                measures = createMeasureFn(json);
            }

            if(fn)
                fn.call(scope || this, measures, response);
        };
    }

    /*-- public methods --*/
    /** @scope LABKEY.Visualization */
    return {
        getTypes : function(config) {

            function createTypes(json)
            {
                if (json.types && json.types.length)
                {
                    // for now just return the raw object array
                    return json.types;
                }
                return [];
            }


            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("visualization", "getVisualizationTypes"),
                method : 'GET',
                success: getSuccessCallbackWrapper(createTypes, LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },

        /**
         * Returns the set of plottable measures found in the current container.
         * @param config An object which contains the following configuration properties.
         * @param {Array} config.filters An array of {@link LABKEY.Visualization.Filter} objects.
         * @param {Boolean} [config.dateMeasures] Indicates whether date measures should be returned instead of numeric measures.
         * Defaults to false.
         * @param {Function} config.success
				Function called when execution succeeds. Will be called with one argument:
				<ul><li><b>measures</b>: an array of {@link LABKEY.Visualization.Measure} objects.</li>
         * @param {Function} [config.failure] Function called when execution fails.  Called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         */
        getMeasures : function(config) {

            function createMeasures(json)
            {
                var measures = [];
                if (json.measures && json.measures.length)
                {
                    for (var i=0; i < json.measures.length; i++)
                        measures.push(new LABKEY.Visualization.Measure(json.measures[i]));
                }
                return measures;
            }

            var params = formatParams(config);

            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("visualization", "getMeasures"),
                method : 'GET',
                success: getSuccessCallbackWrapper(createMeasures, LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params : params
            });
        },

        /**
         * Returns a resultset suitable for visualization based on requested measures and dimensions.
         * @param config An object which contains the following configuration properties.
         * @param {Array} config.measures An array of objects with the following structure:
         * <ul>
         *      <li><b>measure</b>: Generally an augmented {@link LABKEY.Visualization.Measure}, but can be any object
         *          with the following properties:
         *          <ul>
         *              <li><b>name</b>: The name of this measure.</li>
         *              <li><b>schemaName</b>: The name of the schema containing the query that contains this measure.</li>
         *              <li><b>queryName</b>: The name of the query containing this measure.</li>
         *              <li><b>type</b>: The data type of this measure.</li>
         *              <li><b>values</b>: Optional.  If provided, results will be filtered to include only the provided values.</li>
         *              <li><b>allowNullResults</b>: Optional, defaults to true.  If true, this measure will be joined to other measures via an outer join, which will allow results
         *                  from other measures at timepoints not present for this measure (possibly resulting in null/blank values for this measure).  If false, other measures will be inner joined
         *                  to this measure, which will produce a dataset without null values for this measure, but which may not include all data from joined measures.</li>
         *              <li><b>aggregate</b>: See {@link LABKEY.Visualization.Aggregate}.  Required if a 'dimension' property is specified, ignored otherwise.  Indicates
         *                                    what data should be returned if pivoting by dimension results in multiple underlying values
         *                                    per series data point.</li>
         *          </ul>
         *      <li><b>dateOptions</b>: Optional if this measure's axis.timeAxis property is true, ignored otherwise.  Has two valid child properties:
         *          <ul>
         *              <li><b>zeroDateCol</b>: A measure object (with properties for name, queryName, and
         *                                              schemaName) of type date that will be used to align data points in terms of days, weeks, or months.</li>
         *              <li><b>interval</b>: See {@link LABKEY.Visualization.Interval}.  The type of interval that should be calculated between the measure date and the zero date.
         *          </ul>
         *      </li>
         *      <li><b>axis</b>:
         *          <ul><li><b>timeAxis</b>: Boolean.  Indicates whether this measure corresponds to a time axis.</li></ul>
         *      </li>
         *      <li><b>dimension</b>:  Used to pivot a resultset into multiple series.  Generally an augmented
         *          {@link LABKEY.Visualization.Dimension}, but can be any object with the following properties:
         *          <ul>
         *              <li><b>name</b>: The name of this dimension.</li>
         *              <li><b>schemaName</b>: The name of the schema containing the query that contains this dimension.</li>
         *              <li><b>queryName</b>: The name of the query containing this dimension.</li>
         *              <li><b>type</b>: The data type of this dimension.</li>
         *              <li><b>values</b>: Optional.  If provided, results will be filtered to include only the named series.</li>
         *          </ul>
         *      </li>
         * </ul>
         * @param {Array} [config.sorts] Generally an array of augmented {@link LABKEY.Visualization.Dimension} or {@link LABKEY.Visualization.Measure}
         * objects, but can be an array of any objects with the following properties:
         *          <ul>
         *              <li><b>name</b>: The name of this dimension.</li>
         *              <li><b>schemaName</b>: The name of the schema containing the query that contains this dimension.</li>
         *              <li><b>queryName</b>: The name of the query containing this dimension.</li>
         *              <li><b>values</b>: Optional.  If provided, results will be filtered to include only the specified values.</li>
         *          </ul>
         *      </li>

         * @param {Function} config.success Function called when execution succeeds. Will be called with three arguments:
				<ul>
                    <li><b>data</b>: the parsed response data ({@link LABKEY.Query.SelectRowsResults})</li>
                    <li><b>request</b>: the XMLHttpRequest object</li>
                    <li><b>options</b>: a request options object ({@link LABKEY.Query.SelectRowsOptions})</li>
                </ul>
         * @param {Function} [config.failure] Function called when execution fails.  Called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         */
        getData : function(config) {

            var params = {
                measures : config.measures,
                sorts : config.sorts,
                filterUrl: config.filterUrl,
                filterQuery: config.filterQuery,
                groupBys: config.groupBys
            };

            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("visualization", "getData"),
                method : 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope, false),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Saves a visualization for future use.  Saved visualizations appear in the study 'views' webpart.  If the
         * visualization is scoped to a specific query, it will also appear in the views menu for that query.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.name The name this visualization should be saved under.
         * @param {String} config.type The type of visualization being saved.  Should be an instance of {@link LABKEY.Visualization.Type}.
         * @param {Object} config.visualizationConfig An arbitrarily complex JavaScript object that contains all information required to
         * recreate the report.
         * @param {Boolean} [config.replace] Whether this 'save' call should replace an existing report with the same name.
         * If false, the call to 'save' will fail if another report with the same name exists.
         * @param {String} [config.description] A description of the saved report.
         * @param {String} [config.shared] Boolean indicating whether this report is viewable by all users with read
         * permissions to the visualization's folder.  If false, only the creating user can see the visualization.  Defaults to true.
         * @param {String} [config.svg] String svg to be used to generate a thumbnail
         * @param {String} [config.schemaName] Optional, but required if config.queryName is provided.  Allows the visualization to
         * be scoped to a particular query.  If scoped, this visualization will appear in the 'views' menu for that query.
         * @param {String} [config.queryName] Optional, but required if config.schemaName is provided.  Allows the visualization to
         * be scoped to a particular query.  If scoped, this visualization will appear in the 'views' menu for that query.
         * @param {Function} config.success Function called when execution succeeds. Will be called with one arguments:
				<ul>
                    <li><b>result</b>: an object with two properties:
                        <ul>
                            <li><b>name</b>: the name of the saved visualization</li>
                            <li><b>visualizationId</b>: a unique integer identifier for this saved visualization</li>
                        </ul>
                    <li><b>request</b>: the XMLHttpRequest object</li>
                    <li><b>options</b>: a request options object</li>
                </ul>
         * @param {Function} [config.failure] Function called when execution fails.  Called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         */
        save : function(config)
        {
            var params = {
                name : config.name,
                description : config.description,
                json : Ext.util.JSON.encode(config.visualizationConfig),
                replace: config.replace,
                shared: config.shared,
                saveThumbnail: config.saveThumbnail,
                svg : config.svg,
                type : config.type,
                schemaName: config.schemaName,
                queryName: config.queryName
            };

            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("visualization", "saveVisualization"),
                method : 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope, false),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Retrieves a saved visualization.  See {@link LABKEY.Visualization.save}.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.name The name this visualization to be retrieved.
         * @param {String} [config.schemaName] Optional, but required if config.queryName is provided.  Limits the search for
         * the visualization to a specific schema and query.  Note that visualization names are unique within a container
         * (regardless of schema and query), so these additional optional parameters are only useful in a small number of circumstances.
         * @param {String} [config.queryName] Optional, but required if config.schemaName is provided.  Limits the search for
         * the visualization to a specific schema and query.  Note that visualization names are unique within a container
         * (regardless of schema and query), so these additional optional parameters are only useful in a small number of circumstances.
         * @param {Function} config.success Function called when execution succeeds. Will be called with one arguments:
				<ul>
                    <li><b>result</b>: an object with two properties:
                        <ul>
                            <li><b>name</b>: The name of the saved visualization</li>
                            <li><b>description</b>: The description of the saved visualization</li>
                            <li><b>type</b>: The visualization type</li>
                            <li><b>schemaName</b>: The schema to which this visualization has been scoped, if any</li>
                            <li><b>queryName</b>: The query to which this visualization has been scoped, if any</li>
                            <li><b>visualizationConfig</b>: The configuration object provided to {@link LABKEY.Visualization.save}</li>
                        </ul>
                    </li>
                    <li><b>request</b>: the XMLHttpRequest object</li>
                    <li><b>options</b>: a request options object</li>
                </ul>
         * @param {Function} [config.failure] Function called when execution fails.  Called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         */
        get : function(config)
        {
            var params = {
                reportId: config.reportId,
                name : config.name,
                schemaName: config.schemaName,
                queryName: config.queryName
            };

            // Get the standard callback function (last in the success callback chain):
            var successCallback = LABKEY.Utils.getOnSuccess(config);

            // wrap the callback to convert the visualizationConfig property from a JSON string into a native javascript object:
            successCallback = getVisualizatonConfigConverterCallback(successCallback, config.scope);

            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("visualization", "getVisualization"),
                method : 'POST',
                initialConfig: config,
                success: successCallback,
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Retrieves a saved visualization based on identifying parameters found on the current URL.  Method returns true or false,
         * depending on whether the URL contains a saved visualization identifier.  If true, the success or failure callback
         * function will be called just as with {@link LABKEY.Visualization.get}.  If false, no callbacks will be called.
         * This method allows callers to use a single method to retrieve saved visualizations, regardless of how they are identified on the URL.
         * @param config An object which contains the following configuration properties.
         * @param {Function} config.success Function called when the saved visualization was successfully retrieved. See {@link LABKEY.Visualization.get} for details.
         * @param {Function} [config.failure] Function called when the saved visualization could not be retrieved.  See {@link LABKEY.Visualization.get} for details.
         * @return Boolean indicating whether the current URL includes parameters that identify a saved visualization.
         */
        getFromUrl : function(config)
        {
            var params = config || {};

            params.success = LABKEY.Utils.getOnSuccess(config);
            params.failure = LABKEY.Utils.getOnFailure(config);

            var urlParams = LABKEY.ActionURL.getParameters();
            var valid = false;
            if (params.reportId)
            {
                valid = true;
            }
            else
            {
                if (urlParams.name)
                {
                    params.name = urlParams.name;
                    params.schemaName = urlParams.schemaName;
                    params.queryName = urlParams.queryName;
                    valid = true;
                }
            }

            if (valid)
                LABKEY.Visualization.get(params);
            return valid;
        },

        getDataFilterFromURL : function()
        {
            var urlParams = LABKEY.ActionURL.getParameters();
            return urlParams['filterUrl'];
        }
    };
};

/**
 * @namespace Visualization Measures are plottable data elements (columns).  They may be of numeric or date types.
 */
LABKEY.Visualization.Measure = Ext.extend(Object,
    /** @scope LABKEY.Visualization.Measure */
{
    constructor : function(config)
    {
        LABKEY.Visualization.Measure.superclass.constructor.call(this, config);
        Ext.apply(this, config);
    },

    /**
     * Returns the name of the query associated with this dimension.
     */
    getQueryName : function() {
        return this.queryName;
    },

    /**
     * Returns the name of the schema assocated with this dimension.
     */
    getSchemaName : function() {
        return this.schemaName;
    },
    /**
     * Returns whether this dimension is part of a user-defined query (versus a built-in/system-provided query).
     */
    isUserDefined : function() {
        return this.isUserDefined;
    },

    /**
     * Returns the column name of this dimension.
     */
    getName : function() {
        return this.name;
    },

    /**
     * Returns the label of this dimension.
     */
    getLabel : function() {
        return this.label;
    },

    /**
     * Returns the data types of this dimension.
     */
    getType : function() {
        return this.type;
    },

    /**
     * Returns a description of this dimension.
     */
    getDescription : function() {
        return this.description;
    },

    /**
     * Returns the set of available {@link LABKEY.Visualization.Dimension} objects for this measure.
     * @param config An object which contains the following configuration properties.
     * @param config.includeDemographics {Boolean} Applies only to measures from study datsets.
     * Indicates whether dimensions from demographic datasets should be included
     * in the returned set.  If false, only dimensions from the measure's query will be returned.
     * @param {Function} config.success Function called when execution succeeds. Will be called with one argument:
            <ul>
                <li><b>values</b>: an array of unique dimension values</li>
            </ul>
     * @param {Function} [config.failure] Function called when execution fails.  Called with the following parameters:
     * <ul>
     * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     */
    getDimensions : function(config) {

        var params = {queryName: this.queryName, schemaName: this.schemaName};

        if (config.includeDemographics)
            params['includeDemographics'] = config.includeDemographics;

        function createDimensions(json)
        {
            var dimensions = [];
            if (json.dimensions && json.dimensions.length)
            {
                for (var i=0; i < json.dimensions.length; i++)
                    dimensions.push(new LABKEY.Visualization.Dimension(json.dimensions[i]));
            }
            return dimensions;
        }

        Ext.Ajax.request(
        {
            url : LABKEY.ActionURL.buildURL("visualization", "getDimensions"),
            method : 'GET',
            params : params,
            success: getSuccessCallbackWrapper(createDimensions, LABKEY.Utils.getOnSuccess(config), config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
        });
    }
});

/**
 * @namespace Visualization Dimensions are data elements (columns) on which {@link LABKEY.Visualization.Measure} objects
 *  can be pivoted or transformed.  For example, the 'Analyte Name' dimension may be used to pivit a single 'Result' measure
 * into one series per Analyte.
 */
LABKEY.Visualization.Dimension = Ext.extend(Object,
    /** @scope LABKEY.Visualization.Dimension */
    {
    constructor : function(config)
    {
        LABKEY.Visualization.Dimension.superclass.constructor.call(this, config);
        Ext.apply(this, config);
    },
    /**
     * Returns the name of the query associated with this dimension.
     */
    getQueryName : function() {
        return this.queryName;
    },

    /**
     * Returns the name of the schema assocated with this dimension.
     */
    getSchemaName : function() {
        return this.schemaName;
    },

    /**
     * Returns whether this dimension is part of a user-defined query (versus a built-in/system-provided query).
     */
    isUserDefined : function() {
        return this.isUserDefined;
    },

    /**
     * Returns the column name of this dimension.
     */
    getName : function() {
        return this.name;
    },

    /**
     * Returns the label of this dimension.
     */
    getLabel : function() {
        return this.label;
    },

    /**
     * Returns the data types of this dimension.
     */
    getType : function() {
        return this.type;
    },

    /**
     * Returns a description of this dimension.
     */
    getDescription : function() {
        return this.description;
    },

    /**
     * Returns the set of available unique values for this dimension.
     * @param config An object which contains the following configuration properties.
     * @param {Function} config.success Function called when execution succeeds. Will be called with one argument:
            <ul>
                <li><b>values</b>: an array of unique dimension values</li>
            </ul>
     * @param {Function} [config.failure] Function called when execution fails.  Called with the following parameters:
     * <ul>
     * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     */
    getValues : function(config) {

        var params = {queryName: this.queryName, schemaName: this.schemaName, name: this.name};
        function createValues(json)
        {
            if (json.success && json.values)
                return json.values;
            return [];
        }

        Ext.Ajax.request(
        {
            url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues"),
            method : 'GET',
            params : params,
            success: getSuccessCallbackWrapper(createValues, LABKEY.Utils.getOnSuccess(config), config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
        });
    }
});

/**
 * @namespace Visualization Helper class to allow filtering of the measures returned by the
 * {@link LABKEY.Visualization.getMeasures} method.
 */
LABKEY.Visualization.Filter = new function()
{
    function getURLParameterValue(config)
    {
        var params = [config.schemaName];

        if (config.queryName)
            params.push(config.queryName);
        else
            params.push('~');
        
        if (config.queryType)
            params.push(config.queryType);

        return params.join('|');
    }

    /** @scope LABKEY.Visualization.Filter */
    return {
        /**
         * @namespace Visualization Possible query types for measure filters.  See {@link LABKEY.Visualization.Filter}.
        */
        QueryType : {
            /** Return only queries that are built-in to the server */
            BUILT_IN : 'builtIn',
            /** Return only queries that are custom (user defined) */
            CUSTOM : 'custom',
            /** Return all queries (both built-in and custom) */
            ALL : 'all'
        },

        /**
         * Creates a new filter object for use in {@link LABKEY.Visualization.getMeasures}.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.schemaName Required.  Only measures from the specified schema will be returned.
         * @param {String} [config.queryName] If specified, only measures from the specified query will be returned.
         * @param {Object} [config.queryType] If specified, only measures from the specified query types will be returned
         * Valid values for queryType are:  {@link LABKEY.Visualization.Filter.QueryType}.ALL, {@link LABKEY.Visualization.Filter.QueryType}.BUILT_IN,
         * and {@link LABKEY.Visualization.Filter.QueryType}.CUSTOM.  By default, all queries will be returned.
         */
        create : function(config)
        {
            if (!config.schemaName)
                Ext.Msg.alert("Coding Error!", "You must supply a value for schemaName in your configuration object!");
            else
                return getURLParameterValue(config);
        }
    };
};

/**
 * @namespace Visualization Possible aggregates when pivoting a resultset by a dimension.  See  {@link LABKEY.Visualization.getData}.
 */
LABKEY.Visualization.Aggregate = {
    /** Calculates a sum/total. */
    SUM: "SUM",
    /** Calculates an average. */
    AVG: "AVG",
    /** Returns the total number of data points. */
    COUNT: "COUNT",
    /** Returns the minimum value. */
    MIN: "MIN",
    /** Returns the maximum value. */
    MAX: "MAX"
};

/**
 * @namespace Visualization Possible intervals for aligning series in time plots.  See  {@link LABKEY.Visualization.getData}.
 */
LABKEY.Visualization.Interval = {
    /** Align by the number of days since the zero date. */
    DAY : "DAY",
    /** Align by the number of weeks since the zero date. */
    WEEK : "WEEK",
    /** Align by the number of months since the zero date. */
    MONTH : "MONTH",
    /** Align by the number of years since the zero date. */
    YEAR: "YEAR"
};

/**
 * @namespace Visualization A predefined set of visualization types, for use in the config.type property in the
 * {@link LABKEY.Visualization.save} method.
 */
LABKEY.Visualization.Type = {
    /**
     * Plots data over time, aligning different series based on configurable start dates.
     */
        TimeChart : 'ReportService.TimeChartReport'
};
