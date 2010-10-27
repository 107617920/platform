/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2010 LabKey Corporation
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
 * @namespace Query static class to programmatically retrieve, insert, update and
 *		delete data from LabKey public queries. <p/>
 *		{@link LABKEY.Query.selectRows} works for all LabKey public queries.  However,
 *		{@link LABKEY.Query.updateRows}, {@link LABKEY.Query.insertRows} and
 *		{@link LABKEY.Query.deleteRows} are not available for all tables and queries.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
 *                      LabKey SQL Reference</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
 */
LABKEY.Query = new function()
{
    function sendJsonQueryRequest(config)
    {
        if(config.timeout)
            Ext.Ajax.timeout = config.timeout;

        var dataObject = {
            schemaName : config.schemaName,
            queryName : config.queryName,
            rows : config.rowDataArray,
            transacted : config.transacted
        };

        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("query", config.action, config.containerPath),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
            jsonData : dataObject,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function getContentType(response)
    {
        //The response object is not actually the XMLHttpRequest object
        //it's a 'synthesized' object provided by Ext to help on IE 6
        //http://extjs.com/forum/showthread.php?t=27190&highlight=getResponseHeader
        return response && response.getResponseHeader ? response.getResponseHeader('Content-Type') : null;
    }

    function getSuccessCallbackWrapper(callbackFn, stripHiddenCols, scope)
    {
        if(!callbackFn)
            Ext.Msg.alert("Coding Error!", "You must supply a successCallback function in your configuration object!");

        return LABKEY.Utils.getCallbackWrapper(function(data, response, options){
            if(data && data.rows && stripHiddenCols)
                stripHiddenColData(data);
            if(callbackFn)
                callbackFn.call(scope || this, data, options, response);
        }, this);
    }

    function stripHiddenColData(data)
    {
        //gather the set of hidden columns
        var hiddenCols = [];
        var newColModel = [];
        var newMetaFields = [];
        var colModel = data.columnModel;
        for(var idx = 0; idx < colModel.length; ++idx)
        {
            if(colModel[idx].hidden)
                hiddenCols.push(colModel[idx].dataIndex);
            else
            {
                newColModel.push(colModel[idx]);
                newMetaFields.push(data.metaData.fields[idx]);
            }
        }

        //reset the columnModel and metaData.fields to include only the non-hidden items
        data.columnModel = newColModel;
        data.metaData.fields = newMetaFields;

        //delete column values for any columns in the hiddenCols array
        var row;
        for(idx = 0; idx < data.rows.length; ++idx)
        {
            row = data.rows[idx];
            for(var idxHidden = 0; idxHidden < hiddenCols.length; ++idxHidden)
            {
                delete row[hiddenCols[idxHidden]];
                delete row[LABKEY.Query.URL_COLUMN_PREFIX + hiddenCols[idxHidden]];
            }
        }
    }

    function configFromArgs(args)
    {
        return {
            schemaName: args[0],
            queryName: args[1],
            rowDataArray: args[2],
            successCallback: args[3],
            errorCallback: args[4]
        };
    }

    // public methods:
    /** @scope LABKEY.Query */
    return {

        /**
         * An enumeration of the various container filters available.
         * The options are as follows:
         * <ul>
         * <li><b>current:</b> Include the current folder only</li>
         * <li><b>currentAndSubfolders:</b> Include the current folder and all subfolders</li>
         * <li><b>currentPlusProject:</b> Include the current folder and the project that contains it</li>
         * <li><b>currentAndParents:</b> Include the current folder and its parent folders</li>
         * <li><b>currentPlusProjectAndShared:</b> Include the current folder plus its project plus any shared folders</li>
         * <li><b>allFolders:</b> Include all folders for which the user has read permission</li>
         * </ul>
         */
        containerFilter : {
            current: "Current",
            currentAndSubfolders: "CurrentAndSubfolders",
            currentPlusProject: "CurrentPlusProject",
            currentAndParents: "CurrentAndParents",
            currentPlusProjectAndShared: "CurrentPlusProjectAndShared",
            allFolders: "AllFolders"
        },


        /**
         * Execute arbitrary LabKey SQL. For more information, see the
         * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
         * LabKey SQL Reference</a>.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.schemaName name of the schema to query.
         * @param {String} config.sql The LabKey SQL to execute.
         * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
         *       if different than the current container. If not supplied, the current container's path will be used.
         * @param {String} [config.containerFilter] One of the values of {@link LABKEY.Query.containerFilter} that sets
         *       the scope of this query
         * @param {Function} config.success
				Function called when the "selectRows" function executes successfully. Will be called with three arguments:
				the parsed response data ({@link LABKEY.Query.SelectRowsResults}), the XMLHttpRequest object and
                (optionally) the "options" object ({@link LABKEY.Query.SelectRowsOptions}).
         * @param {Function} [config.failure] Function called when execution of the "executeSql" function fails.
         *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
         * @param {Integer} [config.maxRows] The maximum number of rows to return from the server (defaults to returning all rows).
         * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
         *        Use this along with the maxRows config property to request pages of data.
         * @param {String} [config.sort] A sort specification to apply over the rows returned by the SQL. In general,
         * you should either include an ORDER BY clause in your SQL, or specific a sort specification in this config property,
         * but not both. The value of this property should be a comma-delimited list of column names you want to sort by. Use
         * a - prefix to sort a column in descending order (e.g., 'LastName,-Age' to sort first by LastName, then by Age descending).
         * @param {Double} [config.requiredVersion] Set this field to "9.1" to receive the {@link LABKEY.Query.ExtendedSelectRowsResults} format
                   instead of the SelectRowsResults format. The main difference is that in the
                   ExtendedSelectRowsResults format each column in each row
                   will be another object (not just a scalar value) with a "value" property as well as other
                   related properties (url, mvValue, mvIndicator, etc.)
         * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
         *       generating a timeout error (defaults to 30000).
         * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
         * @returns {Number} transactionId The id of the request. This may be used to cancel the request.
         * @example Example, from the Reagent Request Confirmation <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=reagentRequestConfirmation">Tutorial</a> and <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=Confirmation">Demo</a>: <pre name="code" class="xml">
         // This snippet extracts a table of UserID, TotalRequests and
         // TotalQuantity from the "Reagent Requests" list.
         // Upon success, the writeTotals function (not included here) uses the
         // returned data object to display total requests and total quantities.

             LABKEY.Query.executeSql({
                     containerPath: 'home/Study/demo/guestaccess',
                     schemaName: 'lists',
                     sql: 'SELECT "Reagent Requests".UserID AS UserID, \
                         Count("Reagent Requests".UserID) AS TotalRequests, \
                         Sum("Reagent Requests".Quantity) AS TotalQuantity \
                         FROM "Reagent Requests" Group BY "Reagent Requests".UserID',
                     successCallback: writeTotals
             });  </pre>
         */
        executeSql : function(config)
        {
            if(config.timeout)
                Ext.Ajax.timeout = config.timeout;

            var dataObject = {
                schemaName: config.schemaName,
                sql: config.sql
            };

            //set optional parameters
            if(config.maxRows && config.maxRows >= 0)
                dataObject.maxRows = config.maxRows;
            if(config.offset && config.offset > 0)
                dataObject.offset = config.offset;

            if(config.containerFilter)
                dataObject.containerFilter = config.containerFilter;

            if(config.requiredVersion)
                dataObject.apiVersion = config.requiredVersion;

            var qsParams;
            if (config.sort)
                qsParams = {"query.sort": config.sort};

            return Ext.Ajax.request({
                url : LABKEY.ActionURL.buildURL("query", "executeSql", config.containerPath, qsParams),
                method : 'POST',
                success: getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.stripHiddenColumns, config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : dataObject,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Execute arbitrary LabKey SQL and export the results to Excel or TSV. After this method is
         * called, the user will be prompted to accept a file from the server, and most browsers will allow
         * the user to either save it or open it in an apporpriate application.
         * For more information, see the
         * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
         * LabKey SQL Reference</a>.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.schemaName name of the schema to query.
         * @param {String} config.sql The LabKey SQL to execute.
         * @param {String} config.format The desired export format. May be either 'excel' or 'tsv'.
         * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
         *       if different than the current container. If not supplied, the current container's path will be used.
         * @param {String} [config.containerFilter] The container filter to use for this query (defaults to null).
         *       Supported values include:
                <ul>
                    <li><b>"Current":</b> Include the current folder only</li>
                    <li><b>"CurrentAndSubfolders":</b> Include the current folder and all subfolders</li>
                    <li><b>"CurrentPlusProject":</b> Include the current folder and the project that contains it</li>
                    <li><b>"CurrentAndParents":</b> Include the current folder and its parent folders</li>
                    <li><b>"CurrentPlusProjectAndShared":</b> Include the current folder plus its project plus any shared folders</li>
                    <li><b>"AllFolders":</b> Include all folders for which the user has read permission</li>
                </ul>
         */
        exportSql : function(config)
        {
            // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response
            // will make the browser pop up a dialog
            var html = '<form method="POST" action="' + LABKEY.ActionURL.buildURL("query", "exportSql", config.containerPath) + '">' +
            '<input type="hidden" name="sql" value="' + Ext.util.Format.htmlEncode(config.sql) + '" />' +
            '<input type="hidden" name="schemaName" value="' + Ext.util.Format.htmlEncode(config.schemaName) + '" />';
            if (undefined != config.format)
                html += '<input type="hidden" name="format" value="' + Ext.util.Format.htmlEncode(config.format) + '" />';
            if (undefined != config.containerFilter)
                html += '<input type="hidden" name="containerFilter" value="' + Ext.util.Format.htmlEncode(config.containerFilter) + '" />';
            html += "</form>";
            var newForm = Ext.DomHelper.append(document.getElementsByTagName('body')[0], html);
            newForm.submit();
        },

        /**
        * Select rows.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {String} config.schemaName Name of a schema defined within the current container. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query table associated with the chosen schema. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Function} config.success
				Function called when the "selectRows" function executes successfully.
				This function will be called with the following arguments:
				<ul>
				    <li><b>data:</b> Typically, an instance of {@link LABKEY.Query.SelectRowsResults}.  Alternatively, an instance of
                        {@link LABKEY.Query.ExtendedSelectRowsResults} if config.requiredVersion
                        is set to "9.1".</li>
                    <li><b>responseObj:</b> The XMLHttpResponseObject instance used to make the AJAX request</li>
				    <li><b>options:</b> The options used for the AJAX request</li>
				</ul>
        * @param {Function} [config.failure] Function called when execution of the "selectRows" function fails.
        *       This function will be called with the following arguments:
				<ul>
				    <li><b>errorInfo:</b> an object describing the error with the following fields:
                        <ul>
                            <li><b>exception:</b> the exception message</li>
                            <li><b>exceptionClass:</b> the Java class of the exception thrown on the server</li>
                            <li><b>stackTrace:</b> the Java stack trace at the point when the exception occurred</li>
                        </ul>
                    </li>
                    <li><b>responseObj:</b> the XMLHttpResponseObject instance used to make the AJAX request</li>
				    <li><b>options:</b> the options used for the AJAX request</li>
				</ul>
        * @param {Array} [config.filterArray] Array of objects created by {@link LABKEY.Filter.create}.
        * @param {String} [config.sort]  String description of the sort.  It includes the column names
        *       listed in the URL of a sorted data region (with an optional minus prefix to indicate
        *       descending order). In the case of a multi-column sort, up to three column names can be
        *       included, separated by commas.
        * @param {String} [config.viewName] The name of a custom view saved on the server for the specified query.
        * @param {String} [config.columns] An Array of columns or a comma-delimited list of column names you wish to select from the specified
        *       query. By default, selectRows will return the set of columns defined in the default value for this query,
        *       as defined via the Customize View user interface on the server. You can override this by specifying a list
        *       of column names in this parameter, separated by commas. The names can also include references to related
        *       tables (e.g., 'RelatedPeptide/Peptide' where 'RelatedPeptide is the name of a foreign key column in the
        *       base query, and 'Peptide' is the name of a column in the related table).
        * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
        *       if different than the current container. If not supplied, the current container's path will be used.
        * @param {String} [config.containerFilter] The container filter to use for this query (defaults to null).
        *       Supported values include:
                <ul>
                    <li><b>"Current":</b> Include the current folder only</li>
                    <li><b>"CurrentAndSubfolders":</b> Include the current folder and all subfolders</li>
                    <li><b>"CurrentPlusProject":</b> Include the current folder and the project that contains it</li>
                    <li><b>"CurrentAndParents":</b> Include the current folder and its parent folders</li>
                    <li><b>"CurrentPlusProjectAndShared":</b> Include the current folder plus its project plus any shared folders</li>
                    <li><b>"AllFolders":</b> Include all folders for which the user has read permission</li>
                </ul>
        * @param {String} [config.showRows] Either 'paginated' (the default) 'selected', 'unselected' or 'all'.
        *        When 'paginated', the maxRows and offset parameters can be used to page through the query's result set rows.
        *        When 'selected' or 'unselected' the set of rows have been selected or unselected by the user in the grid view will be returned.
        *        You can programatically get and set the selection using the {@link LABKEY.DataRegion.setSelected} APIs.
        *        Setting <code>config.maxRows</code> to -1 is the same as setting <code>config.showRows</code> to 'all'.
        * @param {Integer} [config.maxRows] The maximum number of rows to return from the server (defaults to 100).
        *        If you want to return all possible rows, set this config property to -1.
        * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
        *        Use this along with the maxRows config property to request pages of data.
        * @param {Boolean} [config.includeTotalCount] Include the total number of rows available (defaults to true).
        *       If false totalCount will equal number of rows returned (equal to maxRows unless maxRows == 0).
        * @param {String} [config.selectionKey] Unique string used by selection APIs as a key when storing or retrieving the selected items for a grid.
        *         Not used unless <code>config.showRows</code> is 'selected' or 'unselected'.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {Double} [config.requiredVersion] Set this field to "9.1" to receive the {@link LABKEY.Query.ExtendedSelectRowsResults} format
                  instead of the SelectRowsResults format. The main difference is that in the
                  ExtendedSelectRowsResults format each column in each row
                  will be another object (not just a scalar value) with a "value" property as well as other
                  related properties (url, mvValue, mvIndicator, etc.)
        * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
        * @example Example: <pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	function onFailure(errorInfo, options, responseObj)
	{
	    if(errorInfo && errorInfo.exception)
	        alert("Failure: " + errorInfo.exception);
	    else
	        alert("Failure: " + responseObj.statusText);
	}

	function onSuccess(data)
	{
	    alert("Success! " + data.rowCount + " rows returned.");
	}

	LABKEY.Query.selectRows({
            schemaName: 'lists',
            queryName: 'People',
            columns: ['Name', 'Age'],
            successCallback: onSuccess,
            errorCallback: onFailure,
        });
&lt;/script&gt; </pre>
		* @see LABKEY.Query.SelectRowsOptions
		* @see LABKEY.Query.SelectRowsResults
        * @see LABKEY.Query.ExtendedSelectRowsResults
		*/
        selectRows : function(config)
        {
            //check for old-style separate arguments
            if(arguments.length > 1)
            {
                config = {
                    schemaName: arguments[0],
                    queryName: arguments[1],
                    success: arguments[2],
                    errorCallback: arguments[3],
                    filterArray: arguments[4],
                    sort: arguments[5],
                    viewName: arguments[6]
                };
            }

            if(!config.schemaName)
                throw "You must specify a schemaName!";
            if(!config.queryName)
                throw "You must specify a queryName!";

            var dataObject = this.buildQueryParams(
                    config.schemaName,
                    config.queryName,
                    config.filterArray,
                    config.sort
                    );

            if (!config.showRows || config.showRows == 'paginated')
            {
                if(config.offset)
                    dataObject['query.offset'] = config.offset;

                if(config.maxRows != undefined)
                {
                    if(config.maxRows < 0)
                        dataObject['query.showRows'] = "all";
                    else
                        dataObject['query.maxRows'] = config.maxRows;
                }
            }
            else if (config.showRows in {'all':true, 'selected':true, 'unselected':true})
            {
                dataObject['query.showRows'] = config.showRows;
            }


            if(config.viewName)
                dataObject['query.viewName'] = config.viewName;

            if(config.columns)
                dataObject['query.columns'] = Ext.isArray(config.columns) ? config.columns.join(",") : config.columns;

            if (config.selectionKey)
                dataObject['query.selectionKey'] = config.selectionKey;

            if(config.timeout)
                Ext.Ajax.timeout = config.timeout;

            if(config.requiredVersion)
                dataObject.apiVersion = config.requiredVersion;

            if(config.containerFilter)
                dataObject.containerFilter = config.containerFilter;

            if(config.includeTotalCount)
                dataObject.includeTotalCount = config.includeTotalCount;

            Ext.Ajax.request({
                url : LABKEY.ActionURL.buildURL('query', 'getQuery', config.containerPath),
                method : 'GET',
                success: getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.stripHiddenColumns, config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params : dataObject
            });
        },

        /**
        * Update rows.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {String} config.schemaName Name of a schema defined within the current container. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query table associated with the chosen schema.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Function} config.success Function called when the "updateRows" function executes successfully.
        	    Will be called with arguments:
                the parsed response data ({@link LABKEY.Query.ModifyRowsResults}), the XMLHttpRequest object and
                (optionally) the "options" object ({@link LABKEY.Query.ModifyRowsOptions}).
        * @param {Function} [config.failure] Function called when execution of the "updateRows" function fails.
        *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
        * @param {Array} config.rowDataArray Array of record objects in which each object has a property for each field.
        *               The row array must include the primary key column values and values for
        *               other columns you wish to update.
        * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
        *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {boolean} [config.transacted] Whether all of the updates should be done in a single transaction, so they all succeed or all fail. Defaults to true
        * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        updateRows : function(config)
        {
            if(arguments.length > 1)
                config = configFromArgs(arguments);
            config.action = "updateRows";
            sendJsonQueryRequest(config);
        },

        /**
        * Save inserts, updates, and/or deletes to potentially multiple tables with a single request.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {Array} config.commands An array of all of the update/insert/delete operations to be performed.
        * Each command has the following structure:
        * @param {String} config.commands[].schemaName Name of a schema defined within the current container. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.commands[].queryName Name of a query table associated with the chosen schema.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.commands[].command Name of the command to be performed. Must be one of "insert", "update", or "delete".
        * @param {Array} config.commands[].rows An array of data for each row to be changed. See {@link LABKEY.Query.insertRows},
        * {@link LABKEY.Query.updateRows}, or {@link LABKEY.Query.deleteRows} for requirements of what data must be included for each row.
        * @param {Function} config.success Function called when the "saveRows" function executes successfully.
        	    Will be called with arguments:
                an object with a single "result" property - an array of parsed response data ({@link LABKEY.Query.ModifyRowsResults}) (one for each command in the request),
                the XMLHttpRequest object and (optionally) the "options" object ({@link LABKEY.Query.ModifyRowsOptions}).
        * @param {Function} [config.failure] Function called if execution of the "saveRows" function fails.
        *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
        * @param {String} [config.containerPath] The container path in which the changes are to be performed.
        *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {boolean} [config.transacted] Whether all of the row changes for all of the tables
        * should be done in a single transaction, so they all succeed or all fail. Defaults to true
        * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        saveRows : function(config)
        {
            if(config.length > 1)
                config = configFromArgs(arguments);
            if(config.timeout)
                Ext.Ajax.timeout = config.timeout;

            Ext.Ajax.request({
                url : LABKEY.ActionURL.buildURL("query", "saveRows", config.containerPath),
                method : 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : config,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
        * Insert rows.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {String} config.schemaName Name of a schema defined within the current container.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query table associated with the chosen schema. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Function} config.success Function called when the "insertRows" function executes successfully.
						Will be called with the following arguments:
                        the parsed response data ({@link LABKEY.Query.ModifyRowsResults}), the XMLHttpRequest object and
                        (optionally) the "options" object ({@link LABKEY.Query.ModifyRowsOptions}).
		* @param {Function} [config.failure]  Function called when execution of the "insertRows" function fails.
        *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
        * @param {Array} config.rowDataArray Array of record objects in which each object has a property for each field.
        *                  The row data array must include all column values except for the primary key column.
        *                  However, you will need to include the primary key column values if you defined
        *                  them yourself instead of relying on auto-number.
        * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
        *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {boolean} [config.transacted] Whether all of the inserts should be done in a single transaction, so they all succeed or all fail. Defaults to true
        * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
        * @example Example, from the Reagent Request <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=reagentRequestForm">Tutorial</a> and <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a>: <pre name="code" class="xml">
         // This snippet inserts data from the ReagentReqForm into a list.
         // Upon success, it moves the user to the confirmation page and
         // passes the current user's ID to that page.
         LABKEY.Query.insertRows({
                 containerPath: '/home/Study/demo/guestaccess',
                 schemaName: 'lists',
                 queryName: 'Reagent Requests',
             rowDataArray: [
                {"Name":  ReagentReqForm.DisplayName.value,
                "Email": ReagentReqForm.Email.value,
                "UserID": ReagentReqForm.UserID.value,
                "Reagent": ReagentReqForm.Reagent.value,
                "Quantity": parseInt(ReagentReqForm.Quantity.value),
                "Date": new Date(),
                "Comments": ReagentReqForm.Comments.value,
                "Fulfilled": 'false'}],
             successCallback: function(data){
                     window.location =
                        '/wiki/home/Study/demo/page.view?name=confirmation&userid='
                        + LABKEY.Security.currentUser.id;
             },
         });  </pre>
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        insertRows : function(config)
        {
            if(arguments.length > 1)
                config = configFromArgs(arguments);
            config.action = "insertRows";
            sendJsonQueryRequest(config);
        },

        /**
        * Delete rows.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {String} config.schemaName Name of a schema defined within the current container. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query table associated with the chosen schema. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Function} config.success Function called when the "deleteRows" function executes successfully.
                     Will be called with the following arguments:
                     the parsed response data ({@link LABKEY.Query.ModifyRowsResults}), the XMLHttpRequest object and
                     (optionally) the "options" object ({@link LABKEY.Query.ModifyRowsOptions}).
		* @param {Function} [config.failure] Function called when execution of the "deleteRows" function fails.
         *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
        * @param {Array} config.rowDataArray Array of record objects in which each object has a property for each field.
        *                  The row data array needs to include only the primary key column value, not all columns.
        * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
        *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {boolean} [config.transacted] Whether all of the deletes should be done in a single transaction, so they all succeed or all fail. Defaults to true
        * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        deleteRows : function(config)
        {
            if(arguments.length > 1)
                config = configFromArgs(arguments);
            config.action = "deleteRows";
            sendJsonQueryRequest(config);
        },

        /**
        * Build and return an object suitable for passing to the
        * <a href = "http://www.extjs.com/deploy/dev/docs/?class=Ext.Ajax">Ext.Ajax</a> 'params' configuration property.
        * @param {String} schemaName Name of a schema defined within the current container.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} queryName Name of a query table associated with the chosen schema.   See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Array} [filterArray] Array of objects created by {@link LABKEY.Filter.create}.
        * @param {String} [sort]  String description of the sort.  It includes the column names
        *       listed in the URL of a sorted data region (with an optional minus prefix to indicate
        *       descending order). In the case of a multi-column sort, up to three column names can be
        *       included, separated by commas.
        * @returns {Object} Object suitable for passing to the
        * <a href = "http://extjs.com/deploy/ext-2.2.1/docs/?class=Ext.Ajax">Ext.Ajax</a> 'params' configuration property.
        */

        buildQueryParams: function(schemaName, queryName, filterArray, sort)
        {
            var params = {};
            params['query.queryName'] = queryName;
            params['schemaName'] = schemaName;
            if (sort)
                params['query.sort'] = sort;

            LABKEY.Filter.appendFilterParams(params, filterArray);

            return params;
        },

        /**
         * Returns the set of schemas available in the specified container.
         * @param config An object that contains the following configuration parameters
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>schemasInfo:</b> An object with a property called "schemas," which contains an array of schema names.</li>
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
         */
        getSchemas : function(config)
        {
            var params = {};
            if (config.apiVersion)
                params.apiVersion = config.apiVersion;

            Ext.Ajax.request({
                url : LABKEY.ActionURL.buildURL('query', 'getSchemas', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Returns the set of queries available in a given schema.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>queriesInfo:</b> An object with the following properties
         *  <ul>
         *      <li><b>schemaName:</b> the name of the requested schema</li>
         *      <li><b>queries:</b> an array of objects, each of which has the following properties
         *          <ul>
         *              <li><b>name:</b> the name of the query</li>
         *              <li><b>viewDataUrl:</b> the server-relative URL where this query's data can be viewed.
         *                  Available in LabKey Server version 10.2 and later.</li>
         *              <li><b>columns:</b> if config.includeColumns is not false, this will contain an array of
         *                 objects with the following properties
         *                  <ul>
         *                      <li><b>name:</b> the name of the column</li>
         *                      <li><b>caption:</b> the caption of the column (may be undefined)</li>
         *                      <li><b>description:</b> the description of the column (may be undefined)</li>
         *                  </ul>
          *             </li>
         *          </ul>
         *      </li>
         *  </ul>
         * </li>
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {Boolean} [config.includeUserQueries] If set to false, user-defined queries will not be included in
         * the results. Default is true.
         * @param {Boolean} [config.includeColumns] If set to false, information about the available columns in this
         * query will not be included in the results. Default is true.
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
         */
        getQueries : function(config)
        {
            var params = {};
            // Only pass the parameters that the server supports, and exclude ones like successCallback
            LABKEY.Utils.applyTranslated(params, config,
            {
                schemaName: 'schemaName',
                includeColumns: 'includeColumns',
                includeUserQueries: 'includeUserQueries'
            }, false, false);
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'getQueries', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Returns the set of views available for a given query in a given schema.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {String} config.queryName the name of the query.
         * @param {String} [config.viewName] An optional view name (empty string for the default view), otherwise return all views for the query.
         * @param {Boolean} [config.metadata] Optionally include view column field metadata.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>viewsInfo:</b> An object with the following properties
         *  <ul>
         *      <li><b>schemaName:</b> the name of the requested schema</li>
         *      <li><b>queryName:</b> the name of the requested query</li>
         *      <li><b>views:</b> an array of objects, each of which has the following properties
         *          <ul>
         *              <li><b>name:</b> the name of the view</li>
         *              <li><b>viewDataUrl:</b> the server-relative URL where this view's data can be viewed.
         *                  Available in LabKey Server version 10.2 and later.</li>
         *              <li><b>columns:</b> this will contain an array of objects with the following properties
         *                  <ul>
         *                      <li><b>name:</b> the name of the column</li>
         *                      <li><b>fieldKey:</b> the field key for the column (may include join column names, e.g. 'State/Population')</li>
         *                  </ul>
         *              </li>
         *              <li><b>filter:</b> TBD
         *                  Available in LabKey Server version 10.3 and later.</li>
         *              <li><b>sort:</b> TBD
         *                  Available in LabKey Server version 10.3 and later.</li>
         *              <li><b>fields:</b> TBD if metadata
         *                  Available in LabKey Server version 10.3 and later.</li>
         *          </ul>
         *      </li>
         *  </ul>
         * </li>
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
         */
        getQueryViews : function(config)
        {
            var params = {};
            if(config.schemaName)
                params.schemaName = config.schemaName;
            if(config.queryName)
                params.queryName = config.queryName;
            if(config.viewName)
                params.viewName = config.viewName;
            if(config.metadata)
                params.metadata = config.metadata;
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'getQueryViews', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Creates or updates a custom view or views for a given query in a given schema.  The config
         * object matches the viewInfos parameter of the getQueryViews.successCallback.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {String} config.queryName The name of the query.
         * @param {String} config.views The updated view definitions.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the ssame parameters as getQueryViews.successCallback.
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
         */
        saveQueryViews : function (config)
        {
            var params = {};
            if (config.schemaName)
                params.schemaName = config.schemaName;
            if (config.queryName)
                params.queryName = config.queryName;
            if (config.views)
                params.views = config.views;
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'saveQueryViews', config.containerPath),
                method: 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Returns details about a given query including detailed information about result columns
         * @param {Object} config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {String} config.queryName The name of the query.
         * @param {String} [config.viewName] An optional view name or Array of view names to include custom view details.
         * @param {String} [config.fields] An optional field key or Array of field keys to include in the metadata.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>queryInfo:</b> An object with the following properties
         *  <ul>
         *      <li><b>schemaName:</b> the name of the requested schema</li>
         *      <li><b>name:</b> the name of the requested query</li>
         *      <li><b>isUserDefined:</b> true if this is a user-defined query</li>
         *      <li><b>canEdit:</b> true if the current user can edit this query</li>
         *      <li><b>isMetadataOverrideable:</b> true if the current user may override the query's metadata</li>
         *      <li><b>viewDataUrl:</b> The URL to navigate to for viewing the data returned from this query</li>
         *      <li><b>description:</b> A description for this query (if provided)</li>
         *      <li><b>columns:</b> Information about all columns in this query. This is an array of LABKEY.Query.FieldMetaData objects.</li>
         *      <li><b>defaultView:</b> An array of column information for the columns in the current user's default view of this query.
         *      The shape of each column info is the same as in the columns array.</li>
         *      <li><b>views:</b> An array of view info (XXX: same as views.getQueryViews()
         *  </ul>
         * </li>
         * </ul>
         * @see LABKEY.Query.FieldMetaData
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
         */
        getQueryDetails : function(config)
        {
            var params = {};
            if (config.schemaName)
                params.schemaName = config.schemaName;

            if (config.queryName)
                params.queryName = config.queryName;

            if (Ext.isArray(config.viewName))
                params.viewName = config.viewName.join(",");
            else
                params.viewName = config.viewName;

            if (Ext.isArray(config.fields))
                params.fields = config.fields.join(",");
            else
                params.fields = config.fields;

            if (config.fk)
                params.fk = config.fk;

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'getQueryDetails', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Validates the specified query by ensuring that it parses and executes without an exception.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {String} config.queryName the name of the query.
         * @param {Boolean} config.includeAllColumns If set to false, only the columns in the user's default view
         * of the specific query will be tested (defaults to true).
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with a simple object with one property named "valid" set to true.
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] An optional scope for the callback functions. Defaults to "this"
         */
        validateQuery : function(config)
        {
            var params = {};

            LABKEY.Utils.applyTranslated(params, config, {
                successCallback: false,
                errorCallback: false,
                scope: false
            });

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'validateQuery', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Returns the current date/time on the LabKey server.
         * @param config An object that contains the following configuration parameters
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with a single parameter of type Date.
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         */
        getServerDate : function(config)
        {
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'getServerDate'),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope),
                success: LABKEY.Utils.getCallbackWrapper(function(json){
                    var d;
                    var onSuccess = LABKEY.Utils.getOnSuccess(config);
                    if(json && json.date && onSuccess)
                    {
                        d = new Date(json.date);
                        onSuccess(d);
                    }
                }, this)
            });
        },

        URL_COLUMN_PREFIX: "_labkeyurl_"
    };
};

/**
* @name LABKEY.Query.ModifyRowsOptions
* @class   ModifyRowsOptions class to describe
            the third object passed to the successCallback function
            by {@link LABKEY.Query.updateRows}, {@link LABKEY.Query.insertRows} or
            {@link LABKEY.Query.deleteRows}. This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *              </ul>
 *           </p>
  * @see LABKEY.Query.updateRows
  * @see LABKEY.Query.insertRows
  * @see LABKEY.Query.deleteRows
*/

/**#@+
* @memberOf LABKEY.Query.ModifyRowsOptions#
* @field
*/

/**
* @name headers
* @description  An object containing one property for each HTTP header sent to the server.
* @type Object
*/

/**
* @name method
* @description The HTTP method used for the request (typically 'GET' or 'POST').
* @type String
 */

/**
* @name url
* @description  The URL that was requested.
* @type String
*/

/**
* @name jsonData
* @description  The data object sent to the server. This will contain the following properties:
          <ul>
            <li><b>schemaName</b>: String. The schema name being modified.  This is the same schemaName
                the client passed to the calling function. </li>
            <li><b>queryName</b>: String. The query name being modified. This is the same queryName
                the client passed to the calling function.  </li>
            <li><b>rows</b>: Object[]. Array of row objects that map the names of the row fields to their values.
            The fields required for inclusion for each row depend on the which LABKEY.Query method you are
            using (updateRows, insertRows or deleteRows). </li>
         </ul>
 <p/>
 <b>For {@link LABKEY.Query.updateRows}:</b> <p/>
 For the 'updateRows' method, each row in the rows array must include its primary key value
 as one of its fields.
 <p/>
 An example of a ModifyRowsOptions object for the 'updateRows' successCallback:
 <pre name="code" class="xml">
 {"schemaName": "lists",
  "queryName": "API Test List",
  "rows": [
 {"Key": 1,
 "FirstName": "Z",
 "Age": "100"}]
 } </pre></code>

 <p/>
 <b>For {@link LABKEY.Query.insertRows}:</b> <p/>
 For the 'insertRows' method, the fields of the rows should look the same as
 they do for the 'updateRows' method, except that primary key values for new rows
 need not be supplied if the primary key columns are auto-increment.
 <p/>
 An example of a ModifyRowsOptions object for the 'insertRows' successCallback:
 <pre name="code" class="xml">
 {"schemaName": "lists",
  "queryName": "API Test List",
  "rows": [
  {"FirstName": "C",
 "Age": "30"}]
 } </pre></code>

 <p/>
 <b>For {@link LABKEY.Query.deleteRows}:</b> <p/>
 For the 'deleteRows' method, the fields of the rows should look the
 same as they do for the 'updateRows' method, except that the 'deleteRows'
 method needs to supply only the primary key values for the rows. All
 other row data will be ignored.
 <p/>
 An example of a ModifyRowsOptions object for the 'deleteRows' successCallback:
 <pre name="code" class="xml">
 {"schemaName": "lists",
  "queryName": "API Test List",
  "rows": [
{"Key": 3}]
 } </pre></code>
* @type  Object
*/

/**#@-*/

 /**
* @name LABKEY.Query.ModifyRowsResults
* @class  ModifyRowsResults class to describe
            the first object passed to the successCallback function
            by {@link LABKEY.Query.updateRows}, {@link LABKEY.Query.insertRows} or
            {@link LABKEY.Query.deleteRows}. This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
  *            <p>Additional Documentation:
  *              <ul>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
  *                      How To Find schemaName, queryName &amp; viewName</a></li>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
  *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
  *              </ul>
  *           </p>
* @example For example:
        <pre name="code" class="xml">
        {  "schemaName": "lists",
           "queryName": "API Test List"
           "rowsAffected": 1,
           "command": "insert",
           "rows": [{ Key: 3, StringField: 'NewValue'}]
        } </pre></code>
* @see LABKEY.Query.updateRows
* @see LABKEY.Query.insertRows
* @see LABKEY.Query.deleteRows
*/

/**#@+
* @memberOf LABKEY.Query.ModifyRowsResults#
* @field
*/

/**
* @name LABKEY.Query.ModifyRowsResults#schemaName
* @description  Contains the same schemaName the client passed to the calling function.
* @type  String
*/

/**
* @name     LABKEY.Query.ModifyRowsResults#queryName
* @description  Contains the same queryName the client passed to the calling function.
* @type     String
*/

/**
* @name    command
* @description   Will be "update", "insert", or "delete" depending on the API called.
* @type    String
*/

/**
* @name  rowsAffected
* @description  Indicates the number of rows affected by the API action.
            This will typically be the same number of rows passed in to the calling function.
* @type        Integer
*/

/**
* @name LABKEY.Query.ModifyRowsResults#rows
* @description   Array of rows with field values for the rows updated, inserted,
            or deleted, in the same order as the rows supplied in the request. For insert, the
            new key value for an auto-increment key will be in the returned row's field values.
            For insert or update, the other field values may also be different than those supplied
            as a result of database default expressions, triggers, or LabKey's automatic tracking
            feature, which automatically adjusts columns of certain names (e.g., Created, CreatedBy,
            Modified, ModifiedBy, etc.).
* @type    Object[]
*/

/**#@-*/

/**
* @name LABKEY.Query.SelectRowsOptions
* @class  SelectRowsOptions class to describe
           the third object passed to the successCallback function by
           {@link LABKEY.Query.selectRows}.  This object's properties are useful for
           matching requests to responses, as HTTP requests are typically
           processed asynchronously.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *              </ul>
 *           </p>
* @see LABKEY.Query.selectRows
*/

/**#@+
* @memberOf LABKEY.Query.SelectRowsOptions#
* @field
*/

/**
* @name   query.schemaName
* @description   Contains the same schemaName the client passed to the
            calling function. See <a class="link"
            href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
            How To Find schemaName, queryName &amp; viewName</a>.
* @type   String
*/

/**
* @name   query.queryName
* @description   Contains the same queryName the client passed
            to the calling function. See
            <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
            How To Find schemaName, queryName &amp; viewName</a>.
* @type   String
*/

/**
* @name    query.viewName
* @description 	Name of a valid custom view for the chosen queryName. See
            <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
            How To Find schemaName, queryName &amp; viewName</a>.
* @type    String
*/

/**
* @name    query.offset
* @description  Row number at which results should begin.
            Use this with maxRows to get pages of results.
* @type   Integer
*/

/**
* @name   query.sort
* @description	Sort specification. This can be a comma-delimited list of
            column names, where each column may have an optional dash (-) before the name
            to indicate a descending sort.
* @type  String
*/

/**
* @name   maxRows
* @description   Maximum number of rows to return
* @type     Integer
*/

/**
* @name   filters
* @description   An object whose properties are filter specifications, one for each filter you supplied.
 *          All filters are combined using AND logic. Each one is of type {@link LABKEY.Filter.FilterDefinition}.
 *          The list of valid operators:
            <ul><li><b>eq</b> = equals</li>
            <li><b>neq</b> = not equals</li>
            <li><b>gt</b> = greater-than</li>
            <li><b>gte</b> = greater-than or equal-to</li>
            <li><b>lt</b> = less-than</li>
            <li><b>lte</b> = less-than or equal-to</li>
            <li><b>dateeq</b> = date equal</li>
            <li><b>dateneq</b> = date not equal</li>
            <li><b>neqornull</b> = not equal or null</li>
            <li><b>isblank</b> = is null</li>
            <li><b>isnonblank</b> = is not null</li>
            <li><b>contains</b> = contains</li>
            <li><b>doesnotcontain</b> = does not contain</li>
            <li><b>startswith</b> = starts with</li>
            <li><b>doesnotstartwith</b> = does not start with</li>
            <li><b>in</b> = equals one of</li></ul>
* @type    Object
*/

/**#@-*/

/**
* @name LABKEY.Query.FieldMetaDataLookup
* @class  Lookup metadata about a single field.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
 * @see LABKEY.Query.FieldMetaData
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name    containerPath
* @description The path to the container that this lookup points to, if not the current container
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name    public
* @description Whether the target of this lookup is exposed as a top-level query
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name queryName
* @description The name of the query that this lookup targets
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name schemaName
* @description The name of the schema that this lookup targets
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name keyColumn
* @description The name of column in the lookup's target that will be joined to the value in the local field
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name displayColumn
* @description The name of the column in the lookup's target that will be shown as its value, instead of the raw key value
* @type    String
*/

/**#@-*/

/**
* @name LABKEY.Query.FieldMetaData
* @class  Metadata about a single field.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
 * @see LABKEY.Query.selectRows
 * @see LABKEY.Query.getQueryDetails
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    name
* @description The name of the field
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    friendlyType
* @description A friendlier, more verbose description of the type, like "Text (String)" or "Date and time"
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    shownInInsertView
* @description Whether this field is intended to be displayed in insert UIs
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    shownInDetailView
* @description Whether this field is intended to be displayed in detail UIs
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    shownInUpdateView
* @description Whether this field is intended to be displayed in update UIs
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    versionField
* @description Whether this field's value stores version information for the row
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name userEditable
* @description Whether this field is intended to be edited directly by the user, or managed by the system
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name readOnly
* @description Whether the field's value can be modified
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name nullable
* @description Whether the field's value is allowed to be null
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name mvEnabled
* @description Whether this field supports missing value indicators instead of or addition to its standard value
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name keyField
* @description Whether this field is part of the row's primary key
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name hidden
* @description Whether this value is intended to be hidden from the user, especially for grid views
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name autoIncrement
* @description Whether this field's value is automatically assigned by the server, like a RowId whose value is determined by a database sequence
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name jsonType
* @description The type of JSON object that will represent this field's value: string, boolean, date, int, or float
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name importAliases
* @description Alternate names for this field that may appear in data when importing, whose values should be mapped to this field
* @type    String[]
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name tsvFormat
* @description The format string to be used for TSV exports
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name format
* @description The format string to be used for generating HTML
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name excelFormat
* @description The format string to be used for Excel exports
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name caption
* @description The caption to be shown for this field, typically in a column header, which may differ from its name
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name description
* @description The description for this field
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name inputType
* @description The type of form input to be used when editing this field, such as select, text, textarea, checkbox, or file
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name lookup
* @description Information about this field's lookup configuration
* @type    LABKEY.Query.FieldMetaDataLookup
*/

/**#@-*/

/**
* @name LABKEY.Query.SelectRowsResults
* @class  SelectRowsResults class to describe the first
            object passed to the successCallback function by
            {@link LABKEY.Query.selectRows}. This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
* @see LABKEY.Query.selectRows
*/

/**#@+
* @memberOf LABKEY.Query.SelectRowsResults#
* @field
*/

/**
* @name    metaData
* @description Contains type and lookup information about the columns in the resultset.
* @type    Object
*/

/**
* @name    metaData.root
* @description 	Name of the property containing rows ("rows"). This is mainly for the Ext grid component.
* @type     String
*/

/**
* @name    metaData.totalProperty
* @description 	Name of the top-level property
            containing the row count ("rowCount") in our case. This is mainly
            for the Ext grid component.
* @type   String
*/

/**
* @name   metaData.sortInfo
* @description  Sort specification in Ext grid terms.
            This contains two sub-properties, field and direction, which indicate
            the sort field and direction ("ASC" or "DESC") respectively.
* @type   Object
*/

/**
* @name    metaData.id
* @description  Name of the primary key column.
* @type     String
*/

/**
* @name    metaData.fields
* @description	Array of field information.
* @type    LABKEY.Query.FieldMetaData[]
*/

/**
* @name    columnModel
* @description   Contains information about how one may interact
            with the columns within a user interface. This format is generated
            to match the requirements of the Ext grid component. See
            <a href="http://extjs.com/deploy/ext-2.2.1/docs/?class=Ext.grid.ColumnModel">
            Ext.grid.ColumnModel</a> for further information.
* @type  String
*/

/**
* @name   rows
* @description    An array of rows, each of which is a
            sub-element/object containing a property per column.
* @type   Object[]
*/

/**
* @name rowCount
* @description Indicates the number of total rows that could be
            returned by the query, which may be more than the number of objects
            in the rows array if the client supplied a value for the query.maxRows
            or query.offset parameters. This value is useful for clients that wish
            to display paging UI, such as the Ext grid.
* @type   Integer
*/

/**#@-*/

 /**
* @name LABKEY.Query.ExtendedSelectRowsResults
* @class  ExtendedSelectRowsResults class to describe the first
            object passed to the successCallback function by
            {@link LABKEY.Query.selectRows} if config.requiredVersion is set to "9.1".
  *            <p>Additional Documentation:
  *              <ul>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
  *                      How To Find schemaName, queryName &amp; viewName</a></li>
  *              </ul>
  *           </p>
* @see LABKEY.Query.selectRows
 */

/**#@+
* @memberOf LABKEY.Query.ExtendedSelectRowsResults#
* @field
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData
* @description Contains type and lookup information about the columns in the resultset.
* @type    Object
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData.root
* @description 	Name of the property containing rows ("rows"). This is mainly for the Ext grid component.
* @type     String
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData.totalProperty
* @description 	Name of the top-level property
            containing the row count ("rowCount") in our case. This is mainly
            for the Ext grid component.
* @type   String
*/

/**
* @name   LABKEY.Query.ExtendedSelectRowsResults#metaData.sortInfo
* @description  Sort specification in Ext grid terms.
            This contains two sub-properties, field and direction, which indicate
            the sort field and direction ("ASC" or "DESC") respectively.
* @type   Object
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData.id
* @description  Name of the primary key column.
* @type     String
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData.fields
* @description	Array of field information.
            Each field has the following properties:
            <ul><li><b>name</b> -- The name of the field</li>
            <li><b>type</b> -- JavaScript type name of the field</li>
            <li><b>shownInInsertView</b> -- whether this field is intended to be shown in insert views</li>
            <li><b>shownInUpdateView</b> -- whether this field is intended to be shown in update views</li>
            <li><b>shownInDetailsView</b> -- whether this field is intended to be shown in details views</li>
            <li><b>measure</b> -- whether this field is a measure.  Measures are fields that contain data subject to charting and other analysis.</li>
            <li><b>dimension</b> -- whether this field is a dimension.  Data dimensions define logical groupings of measures.</li>
            <li><b>hidden</b> -- whether this field is hidden and not normally shown in grid views</li>
            <li><b>lookup</b> -- If the field is a lookup, there will
                be four sub-properties listed under this property:
                schema, table, displayColumn, and keyColumn, which describe the schema, table, and
                display column, and key column of the lookup table (query).</li></ul>
* @type    Object[]
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#columnModel
* @description   Contains information about how one may interact
            with the columns within a user interface. This format is generated
            to match the requirements of the Ext grid component. See
            <a href="http://extjs.com/deploy/ext-2.2.1/docs/?class=Ext.grid.ColumnModel">
            Ext.grid.ColumnModel</a> for further information.
* @type  String
*/

/**
* @name   LABKEY.Query.ExtendedSelectRowsResults#rows
* @description    An array of rows, each of which is a
            sub-element/object containing an object per column. The
            object will always contain a property named "value" that is the
            column's value, but it may also contain other properties about
            that column's value. For example, if the column was setup to track
            missing value information, it will also contain a property named mvValue (which
            is the raw value that is considered suspect), and a property named
            mvIndicator, which will be the string MV indicator (e.g., "Q").
* @type   Object[]
*/

/**
* @name LABKEY.Query.ExtendedSelectRowsResults#rowCount
* @description Indicates the number of total rows that could be
            returned by the query, which may be more than the number of objects
            in the rows array if the client supplied a value for the query.maxRows
            or query.offset parameters. This value is useful for clients who wish
            to display paging UI, such as the Ext grid.
* @type   Integer
*/

/**#@-*/
