/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 9.3
 * @license Copyright (c) 2008-2009 LabKey Corporation
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
 * @namespace Assay static class to retrieve read-only assay definitions.
*/
LABKEY.Assay = new function()
{
    function getAssays(config)
    {
        //check for old-style separate arguments
        if(arguments.length > 1) {
            config = {
                successCallback: arguments[0],
                failureCallback: arguments[1],
                parameters: arguments[2],
                containerPath: arguments[3]
            };
        }

        moveParameter(config, "id");
        moveParameter(config, "type");
        moveParameter(config, "name");

        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("assay", "assayList", config.containerPath),
            method : 'POST',
            success: config.successCallback,
            failure: config.failureCallback,
            jsonData : config.parameters,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function getSuccessCallbackWrapper(successCallback)
    {
        return LABKEY.Utils.getCallbackWrapper(function(data, response){
            if(successCallback)
                successCallback(data.definitions, response);
        }, this);
    }

    function moveParameter(config, param)
    {
        if (!config.parameters) config.parameters = {};
        if (config[param])
        {
            config.parameters[param] = config[param];
            delete config[param];
        }
    }

    /** @scope LABKEY.Assay.prototype */
    return {

	/**
	* Gets all assays.
	* @param {Function} config.successCallback Required. Function called when the
			"getAll" function executes successfully.  Will be called with the argument: 
			{@link LABKEY.Assay.AssayDesign[]}.
	* @param {Function} [config.failureCallback] Function called when execution of the "getAll" function fails.
	* @param {String} [config.containerPath] The container path in which the requested Assays are defined.
	*       If not supplied, the current container path will be used.
	* @example Example:
<pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	function successHandler(assayArray) 
	{ 
		var html = ''; 
		for (var defIndex = 0; defIndex < assayArray.length; defIndex ++) 
		{ 
			var definition = assayArray[defIndex ]; 
			html += '&lt;b&gt;' + definition.type + '&lt;/b&gt;: ' 
				+ definition.name + '&lt;br&gt;'; 
			for (var domain in definition.domains) 
			{ 
				html += '&nbsp;&nbsp;&nbsp;' + domain + '&lt;br&gt;'; 
				var properties = definition.domains[domain]; 
				for (var propertyIndex = 0; propertyIndex 
					< properties.length; propertyIndex++) 
				{ 
					var property = properties[propertyIndex]; 
					html += '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;' + property.name + 
						' - ' + property.typeName + '&lt;br&gt;'; 
				} 
			} 
		} 
		document.getElementById('testDiv').innerHTML = html; 
	} 

	function errorHandler(error) 
	{ 
		alert('An error occurred retrieving data.'); 
	}
	
	LABKEY.Assay.getAll({successCallback: successHandler, failureCallback: errorHandler});
&lt;/script&gt;
&lt;div id='testDiv'&gt;Loading...&lt;/div&gt;
</pre>
	  * @see LABKEY.Assay.AssayDesign
	  */
        getAll : function(config)
        {
            if(arguments.length > 1) {
                config = {
                    successCallback: arguments[0],
                    failureCallback: arguments[1],
                    parameters: {},
                    containerPath: arguments[2]
                };
            }

            config.successCallback = getSuccessCallbackWrapper(config.successCallback);
            getAssays(config);
        },
	  /**
	  * Gets an assay by name.
	  * @param {Function(LABKEY.Assay.AssayDesign[])} config.successCallback Function called when the "getByName" function executes successfully.
	  * @param {Function} [config.failureCallback] Function called when execution of the "getByName" function fails.
	  * @param {String} config.name String name of the assay.
	  * @param {String} [config.containerPath] The container path in which the requested Assay is defined.
	  *       If not supplied, the current container path will be used.
	  * @see LABKEY.Assay.AssayDesign
	  */
        getByName : function(config)
        {
            if(arguments.length > 1) {
                config = {
                    successCallback: arguments[0],
                    failureCallback: arguments[1],
                    parameters: { name: arguments[2] },
                    containerPath: arguments[3]
                };
            }

            moveParameter(config, "name");
            config.successCallback = getSuccessCallbackWrapper(config.successCallback);
            getAssays(config);
        },

	  /**
	  * Gets an assay by type.
	  * @param {Function(LABKEY.Assay.AssayDesign[])} config.successCallback Function called
				when the "getByType" function executes successfully.
	  * @param {Function} [config.failureCallback] Function called when execution of the "getByType" function fails.
	  * @param {String} config.type String name of the assay type.  "ELISpot", for example.
	  * @param {String} [config.containerPath] The container path in which the requested Assays are defined.
	  *       If not supplied, the current container path will be used.
 	  * @see LABKEY.Assay.AssayDesign
	  */
        getByType : function(config)
        {
            if(arguments.length > 1) {
                config = {
                    successCallback: arguments[0],
                    failureCallback: arguments[1],
                    parameters: { type: arguments[2] },
                    containerPath: arguments[3]
                };
            }

            moveParameter(config, "type");
            config.successCallback = getSuccessCallbackWrapper(config.successCallback);
            getAssays(config);
        },

	 /**
	 * Gets an assay by its ID.
	 * @param {Function(LABKEY.Assay.AssayDesign[])} config.successCallback Function called
				when the "getById" function executes successfully.
	 * @param {Function} [config.failureCallback] Function called when execution of the "getById" function fails.
	 * @param {Integer} config.id Unique integer ID for the assay.
	  * @param {String} [config.containerPath] The container path in which the requested Assay is defined.
	  *       If not supplied, the current container path will be used.
	 * @see LABKEY.Assay.AssayDesign
	 */
        getById : function(config)
        {
            if(arguments.length > 1) {
                config = {
                    successCallback: arguments[0],
                    failureCallback: arguments[1],
                    parameters: { id: arguments[2] },
                    containerPath: arguments[3]
                };
            }

            moveParameter(config, "id");
            config.successCallback = getSuccessCallbackWrapper(config.successCallback);
            getAssays(config);
        },


        /**
        * Select NAb assay data.
        * @param {Object} config An object which contains the following configuration properties.
         * @param {String} config.assayName  The name of the NAb assay design for which runs are to be retrieved.
         * @param {Boolean} [config.includeStats]  Whether or not statistics (standard deviation, max, min, etc.) should
         * be returned with calculations and well data.
         * @param {Boolean} [config.includeWells]  Whether well-level data should be included in the response.
         * @param {Boolean} [config.calculateNeut]  Whether neutralization should be calculated on the server.
         * @param {Boolean} [config.includeFitParameters]  Whether the parameters used in the neutralization curve fitting calculation
         * should be included in the response.
        * @param {Function} config.successCallback
                Function called when the "getNAbRuns" function executes successfully.
                This function will be called with the following arguments:
                <ul>
                    <li>runs: an array of NAb run objects</li>
                    <li>options: the options used for the AJAX request</li>
                    <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
                </ul>
        * @param {Function} [config.errorCallback] Function called when execution of the "selectRows" function fails.
        *       This function will be called with the following arguments:
                <ul>
                    <li>responseObj: The XMLHttpRequest object containing the response data.</li>
                    <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
                </ul>
        *
        * @param {Array} [config.filterArray] Array of objects created by {@link LABKEY.Filter#create}.
        * @param {String} [config.sort]  String description of the sort.  It includes the column names
        *       listed in the URL of a sorted data region (with an optional minus prefix to indicate
        *       descending order). In the case of a multi-column sort, up to three column names can be
        *       included, separated by commas.
        * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
        *       if different than the current container. If not supplied, the current container's path will be used.
        * @param {Integer} [config.maxRows] The maximum number of runs to return from the server (defaults to 100).
        *        If you want to return all possible rows, set this config property to -1.
        * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
        *        Use this along with the maxRows config property to request pages of data.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        */
        getNAbRuns : function(config)
        {
            var dataObject = {};

            Ext.apply(dataObject, config);
            if (config.sort)
                dataObject['query.sort'] = config.sort;
            if(config.offset)
                dataObject['query.offset'] = config.offset;
            if(config.maxRows)
            {
                if(config.maxRows < 0)
                    dataObject['query.showRows'] = "all";
                else
                    dataObject['query.maxRows'] = config.maxRows;
            }

            if (config.filterArray)
            {
                for (var i = 0; i < config.filterArray.length; i++)
                {
                    var filter = config.filterArray[i];
                    dataObject[filter.getURLParameterName()] = filter.getURLParameterValue();
                }
            }

            if(config.timeout)
                Ext.Ajax.timeout = config.timeout;

            if (!config.errorCallback)
               config.errorCallback = LABKEY.Utils.displayAjaxErrorResponse;

            Ext.Ajax.request({
                url : LABKEY.ActionURL.buildURL('nabassay', 'getNabRuns', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(function(data, response){
                    if(config.successCallback)
                        config.successCallback.call(config.scope, data.runs);
                }, this),
                failure: LABKEY.Utils.getCallbackWrapper(config.errorCallback, config.scope, true),
                params : dataObject
            });
        }
    };
};


/**
* @name LABKEY.Assay.AssayDesign
* @class  Static class to describe the shape and fields of an assay.  Each of the {@link LABKEY.Assay}
            'get' methods passes its success callback function an array of AssayDesigns.
*/

/**#@+
* @memberOf LABKEY.Assay.AssayDesign#
* @field
*/

/**
* @name name
* @description   The name of the assay.
* @type String
*/

/**
* @name id
* @description The unique ID of the assay.
* @type Integer
*/

/**
* @name type
* @description The name of the assay type.  Example:  "ELISpot"
* @type String
*/

/**
* @name projectLevel
* @description  Indicates whether this is a project-level assay.
* @type Boolean
*/

/**
* @name description
* @description  Contains the assay description.
* @type String
*/

/**
* @name plateTemplate
* @description  Contains the plate template name if the assay is plate-based.  Undefined otherwise.
* @type String
*/

/**
* @name domains
* @description Map containing name/value pairs.  Typically contains three entries (batch, run and results).
  * Each one is associated with an array, where the elements in the array have the following properties
  *        <ul>
  *           <li><b>name: </b>The name of the domain field. (string)</li>
  *           <li><b>typeName: </b> The name of the type of the domain field. (Human readable.) (string)</li>
  *           <li><b>typeURI: </b> The URI that uniquely identifies the domain field type. (Not human readable.) (string)</li>
  *           <li><b>label: </b> The domain field label. (string)</li>
  *           <li><b>description: </b> The domain field description. (string)</li>
  *           <li><b>formatString: </b> The format string applied to the domain field. (string)</li>
  *           <li><b>required: </b> Indicates whether a value is required for this domain field. (boolean)</li>
  *           <li><b>lookup.container: </b> If this domain field is a lookup, lookupContainer holds the
             String path to the lookup container or null if the lookup in the
             same container.  Undefined otherwise.(string)</li>
  *           <li><b>lookup.schema: </b> If this domain field object is a lookup, lookupSchema holds the
            String name of the lookup schema.  Undefined otherwise.(string)</li>
  *           <li><b>lookup.table: </b> If this domain field object is a lookup, lookupTable holds the String
            name of the lookup query.  Undefined otherwise. (string)</li>
  *           <li><b>lookup.keyColumn: </b> The primary key field in target table (string)</li>
  *           <li><b>lookup.displayColumn: </b>The display column in target table (string)</li>
  *       </ul>
* @type Object
*/

/**#@-*/

