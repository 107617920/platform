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

Ext.namespace("LABKEY", "LABKEY.ext");

/**
 * Constructs a new LabKey Store using the supplied configuration.
 * @class LabKey extension to the <a href="http://extjs.com/deploy/dev/docs/?class=Ext.data.Store">Ext.data.Store</a> class,
 * which can retrieve data from a LabKey server, track changes, and update the server upon demand. This is most typically
 * used with data-bound user interface widgets, such as the LABKEY.ext.Grid.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 * @constructor
 * @augments Ext.data.Store
 * @param config Configuration properties.
 * @param {String} config.schemaName The LabKey schema to query.
 * @param {String} config.queryName The query name within the schema to fetch.
 * @param {String} [config.sql] A LabKey SQL statement to execute to fetch the data. You may specify either a queryName or sql,
 * but not both. Note that when using sql, the store becomes read-only, as it has no way to know how to update/insert/delete the rows.
 * @param {String} [config.viewName] A saved custom view of the specified query to use if desired.
 * @param {String} [config.columns] A comma-delimeted list of column names to fetch from the specified query. Note
 *  that the names may refer to columns in related tables using the form 'column/column/column' (e.g., 'RelatedPeptide/TrimmedPeptide').
 * @param {String} [config.sort] A base sort specification in the form of '[-]column,[-]column' ('-' is used for descending sort).
 * @param {Array} [config.filterArray] An array of LABKEY.Filter.FilterDefinition objects to use as the base filters.
 * @param {Boolean} [config.updatable] Defaults to true. Set to false to prohibit updates to this store.
 * @param {String} [config.containerPath] The container path from which to get the data. If not specified, the current container is used.
 * @param {String} [config.containerFilter] The container filter to use for this query (defaults to null).
 *      Supported values include:
 *       <ul>
 *           <li>"Current": Include the current folder only</li>
 *           <li>"CurrentAndSubfolders": Include the current folder and all subfolders</li>
 *           <li>"CurrentPlusProject": Include the current folder and the project that contains it</li>
 *           <li>"CurrentAndParents": Include the current folder and its parent folders</li>
 *           <li>"CurrentPlusProjectAndShared": Include the current folder plus its project plus any shared folders</li>
 *           <li>"AllFolders": Include all folders for which the user has read permission</li>
 *       </ul>
 * @example &lt;script type="text/javascript"&gt;
    var _grid, _store;
    Ext.onReady(function(){

        //create a Store bound to the 'People' list in the 'lists' schema
        _store = new LABKEY.ext.Store({
            schemaName: 'lists',
            queryName: 'People'
        });

        //create a grid using that store as the data source
        _grid = new LABKEY.ext.EditorGridPanel({
            store: _store,
            renderTo: 'grid',
            width: 800,
            autoHeight: true,
            title: 'Example',
            editable: true
        });
    });
&lt;/script&gt;
&lt;div id='grid'/&gt;
 */
LABKEY.ext.Store = Ext.extend(Ext.data.Store, {
    constructor: function(config) {

        var baseParams = {schemaName: config.schemaName};
        var qsParams = {};

        if (config.queryName && !config.sql)
            baseParams['query.queryName'] = config.queryName;
        if (config.sql)
        {
            baseParams.sql = config.sql;
            config.updatable = false;
        }
        if (config.sort)
        {
            if (config.sql)
                qsParams['query.sort'] = config.sort;
            else
                baseParams['query.sort'] = config.sort;
        }
        delete config.sort; //important...otherwise the base Ext.data.Store interprets it

        if (config.filterArray)
        {
            for (var i = 0; i < config.filterArray.length; i++)
            {
                var filter = config.filterArray[i];
                if (config.sql)
                    qsParams[filter.getURLParameterName()] = filter.getURLParameterValue();
                else
                    baseParams[filter.getURLParameterName()] = filter.getURLParameterValue();
            }
        }

        if(config.viewName && !config.sql)
            baseParams['query.viewName'] = config.viewName;

        if(config.columns && !config.sql)
            baseParams['query.columns'] = config.columns;

        if(config.containerFilter)
            baseParams.containerFilter = config.containerFilter;

        baseParams.apiVersion = 9.1;

        Ext.apply(this, config, {
            remoteSort: true,
            updatable: true
        });

        this.isLoading = false;

        LABKEY.ext.Store.superclass.constructor.call(this, {
            reader: new LABKEY.ext.ExtendedJsonReader(),
            proxy : new Ext.data.HttpProxy(new Ext.data.Connection({
                method: (config.sql ? 'POST' : 'GET'),
                url: (config.sql ? LABKEY.ActionURL.buildURL("query", "executeSql", config.containerPath, qsParams)
                        : LABKEY.ActionURL.buildURL("query", "selectRows", config.containerPath)),
                listeners: {
                    beforerequest: {fn: this.onBeforeRequest, scope: this}
                }
            })),
            baseParams: baseParams,
            listeners: {
                'beforeload': {fn: this.onBeforeLoad, scope: this},
                'load': {fn: this.onLoad, scope: this},
                'loadexception' : {fn: this.onLoadException, scope: this},
                'update' : {fn: this.onUpdate, scope: this}
            }
        });

        /**
         * @memberOf LABKEY.ext.Store#
         * @name beforecommit
         * @event
         * @description Fired just before the store sends updated records to the server for saving. Return
         * false from this event to stop the save operation.
         * @param {array} records An array of Ext.data.Record objects that will be saved.
         * @param {array} rows An array of simple row-data objects from those records. These are the actual
         * data objects that will be sent to the server.
         */
        /**
         * @memberOf LABKEY.ext.Store#
         * @name commitcomplete
         * @event
         * @description Fired after all modified records have been saved on the server.
         */
        /**
         * @memberOf LABKEY.ext.Store#
         * @name commitexception
         * @event
         * @description Fired if there was an exception during the save process.
         * @param {String} message The exception message.
         */
        this.addEvents("beforecommit", "commitcomplete", "commitexception");

        //subscribe to the proxy's beforeload event so that we can map parameter names
        this.proxy.on("beforeload", this.onBeforeProxyLoad, this);
    },

    /**
     * Adds a new record to the store based upon a raw data object.
     * @name addRecord
     * @function
     * @memberOf LABKEY.ext.Store#
     * @param {Object} data The raw data object containing a properties for each field.
     * @param {integer} [index] The index at which to insert the record. If not supplied, the new
     * record will be added to the end of the store.
     * @returns {Ext.data.Record} The new Ext.data.Record object.
     */
    addRecord : function(data, index) {
        if (!this.updatable)
            throw "this LABKEY.ext.Store is not updatable!";

        if(undefined == index)
            index = this.getCount();

        var fields = this.reader.meta.fields;

        //if no data was passed, create a new object with
        //all nulls for the field values
        if(!data)
            data = {};

        //set any non-specified field to null
        //some bound control (like the grid) need a property
        //defined for each field
        var field;
        for(var idx = 0; idx < fields.length; ++idx)
        {
            field = fields[idx];
            if(!data[field.name])
                data[field.name] = null;
        }

        var recordConstructor = Ext.data.Record.create(fields);
        var record = new recordConstructor(data);

        //add an isNew property so we know that this is a new record
        record.isNew = true;
        this.insert(index, record);
        return record;
    },

    /**
     * Deletes a set of records from the store as well as the server. This cannot be undone.
     * @name deleteRecords
     * @function
     * @memberOf LABKEY.ext.Store#
     * @param {Array of Ext.data.Record objects} records The records to delete.
     */
    deleteRecords : function(records) {
        if (!this.updatable)
            throw "this LABKEY.ext.Store is not updatable!";

        if(!records || records.length == 0)
            return;

        var deleteRowsKeys = [];
        var key;
        for(var idx = 0; idx < records.length; ++idx)
        {
            key = {};
            key[this.idName] = records[idx].id;
            deleteRowsKeys[idx] = key;
        }

        //send the delete
        LABKEY.Query.deleteRows({
            schemaName: this.schemaName,
            queryName: this.queryName,
            containerPath: this.containerPath,
            rowDataArray: deleteRowsKeys,
            successCallback: this.getDeleteSuccessHandler(),
            action: "deleteRows" //hack for Query.js bug
        });
    },

    /**
     * Commits all changes made locally to the server. This method executes the updates asynchronously,
     * so it will return before the changes are fully made on the server. Records that are being saved
     * will have a property called 'saveOperationInProgress' set to true, and you can test if a Record
     * is currently being saved using the isUpdateInProgress method. Once the record has been updated
     * on the server, it's properties may change to reflect server-modified values such as Modified and ModifiedBy.
     * <p>
     * Before records are sent to the server, the "beforecommit" event will fire. Return false from your event
     * handler to prohibit the commit. The beforecommit event handler will be passed the following parameters:
     * <ul>
     * <li><b>records</b>: An array of Ext.data.Record objects that will be sent to the server.</li>
     * <li><b>rows</b>: An array of row data objects from those records.</li>
     * </ul>
     * <p>
     * The "commitcomplete" or "commitexception" event will be fired when the server responds. The former
     * is fired if all records are successfully saved, and the latter if an exception occurred. All modifications
     * to the server are transacted together, so all records will be saved or none will be saved. The "commitcomplete"
     * event is passed no parameters. The "commitexception" even is passed the error message as the only parameter.
     * You may return false form the "commitexception" event to supress the default display of the error message.
     * <p>
     * For information on the Ext event model, see the
     * <a href="http://extjs.com/deploy/dev/docs/?class=Ext.util.Observable">Ext API documentation</a>.
     * @name commitChanges
     * @function
     * @memberOf LABKEY.ext.Store#
     */
    commitChanges : function() {
        var records = this.getModifiedRecords();
        if(!records || records.length == 0)
            return;

        if (!this.updatable)
            throw "this LABKEY.ext.Store is not updatable!";

        //build the json to send to the server
        var record;
        var rows = [];
        for(var idx = 0; idx < records.length; ++idx)
        {
            record = records[idx];

            //if we are already in the process of saving this record, just continue
            if(record.saveOperationInProgress)
                continue;

            if(!this.readyForSave(record))
                continue;

            record.saveOperationInProgress = true;
            rows.push({
                command: (record.isNew ? "insert" : "update"),
                values: this.getRowData(record),
                oldKeys : this.getOldKeys(record)
            });
        }

        if(false === this.fireEvent("beforecommit", records, rows))
            return;

        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("query", "saveRows", this.containerPath),
            method : 'POST',
            success: this.onCommitSuccess,
            failure: this.getOnCommitFailure(records),
            scope: this,
            jsonData : {
                schemaName: this.schemaName,
                queryName: this.queryName,
                containerPath: this.containerPath,
                rows: rows
            },
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    },

    /**
     * Returns true if the given record is currently being updated on the server, false if not.
     * @param {Ext.data.Record} record The record.
     * @returns {boolea} true if the record is currently being updated, false if not.
     * @name isUpdateInProgress
     * @function
     * @memberOf LABKEY.ext.Store#
     */
    isUpdateInProgress : function(record) {
        return record.saveOperationInProgress;
    },

    /**
     * Returns a LabKey.ext.Store filled with the lookup values for a given
     * column name if that column exists, and if it is a lookup column (i.e.,
     * lookup meta-data was supplied by the server).
     * @param columnName The column name
     * @param includeNullRecord Pass true to include a null record at the top
     * so that the user can set the column value back to null. Set the
     * lookupNullCaption property in this Store's config to override the default
     * caption of "[none]".
     */
    getLookupStore : function(columnName, includeNullRecord)
    {
        if(!this.lookupStores)
            this.lookupStores = {};

        var store = this.lookupStores[columnName];
        if(!store)
        {
            //find the column metadata
            var fieldMeta = this.findFieldMeta(columnName);
            if(!fieldMeta)
                return null;

            //create the lookup store and kick off a load
            var config = {
                schemaName: fieldMeta.lookup.schema,
                queryName: fieldMeta.lookup.table,
                containerPath: fieldMeta.lookup.containerPath || this.containerPath
            };
            if(includeNullRecord)
                config.nullRecord = {
                    displayColumn: fieldMeta.lookup.displayColumn,
                    nullCaption: this.lookupNullCaption || "[none]"
                };

            store = new LABKEY.ext.Store(config);
            this.lookupStores[columnName] = store;
        }
        return store;
    },

    exportData : function(format) {
        format = format || "excel";
        if (this.sql)
        {
            LABKEY.Query.exportSql({
                schemaName: this.schemaName,
                sql: this.sql,
                format: format,
                containerPath: this.containerPath,
                containerFilter: this.containerFilter
            });
        }
        else
        {
            var params = {schemaName: this.schemaName, "query.queryName": this.queryName};

            if(this.sortInfo)
                params['query.sort'] = "DESC" == this.sortInfo.direction
                        ? "-" + this.sortInfo.field
                        : this.sortInfo.field;

            var userFilters = this.getUserFilters();
            if (userFilters)
            {
                for (var i = 0; i < userFilters.length; i++)
                {
                    var filter = userFilters[i];
                    params[filter.getURLParameterName()] = filter.getURLParameterValue();
                }
            }

            var action = ("tsv" == format) ? "exportRowsTsv" : "exportRowsExcel";
            window.location = LABKEY.ActionURL.buildURL("query", action, this.containerPath, params);
        }
    },

    /*-- Private Methods --*/

    onCommitSuccess : function(response, options) {
        var json = this.getJson(response);
        if(!json || !json.rows)
            return;

        var idCol = this.reader.jsonData.metaData.id;
        var row;
        var record;
        for(var idx = 0; idx < json.rows.length; ++idx)
        {
            row = json.rows[idx];
            if(!row || !row.values)
                continue;

            //find the record using the id sent to the server
            record = this.getById(row.oldKeys[this.reader.meta.id]);
            if(!record)
                continue;

            //apply values from the result row to the sent record
            for(var col in record.data)
            {
                //since the sent record might contain columns form a related table,
                //ensure that a value was actually returned for that column before trying to set it
                if(undefined !== row.values[col])
                    record.set(col, record.fields.get(col).convert(row.values[col], row.values));

                //clear any displayValue there might be in the extended info
                if(record.json && record.json[col])
                    delete record.json[col].displayValue;
            }

            //if the id changed, fixup the keys and map of the store's base collection
            //HACK: this is using private data members of the base Store class. Unfortunately
            //Ext Store does not have a public API for updating the key value of a record
            //after it has been added to the store. This might break in future versions of Ext.
            if(record.id != row.values[idCol])
            {
                record.id = row.values[idCol];
                this.data.keys[this.data.indexOf(record)] = row.values[idCol];

                delete this.data.map[record.id];
                this.data.map[row.values[idCol]] = record;
            }

            //reset transitory flags and commit the record to let
            //bound controls know that it's now clean
            delete record.saveOperationInProgress;
            delete record.isNew;
            record.commit();
        }
        this.fireEvent("commitcomplete");
    },

    getOnCommitFailure : function(records) {
        return function(response, options) {
            
            for(var idx = 0; idx < records.length; ++idx)
                delete records[idx].saveOperationInProgress;

            var json = this.getJson(response);
            var message = (json && json.exception) ? json.exception : response.statusText;

            if(false !== this.fireEvent("commitexception", message))
                Ext.Msg.alert("Error During Save", "Could not save changes due to the following error:\n" + message);
        };
    },

    getJson : function(response) {
        return (response && undefined != response.getResponseHeader && undefined != response.getResponseHeader['Content-Type']
                && response.getResponseHeader['Content-Type'].indexOf('application/json') >= 0) 
                ? Ext.util.JSON.decode(response.responseText)
                : null;
    },

    findFieldMeta : function(columnName)
    {
        var fields = this.reader.meta.fields;
        for(var idx = 0; idx < fields.length; ++idx)
        {
            if(fields[idx].name == columnName)
                return fields[idx];
        }
        return null;
    },

    onBeforeRequest : function(connection, options) {
        if (this.sql)
        {
            //need to adjust url
            var qsParams = {};
            if (options.params['query.sort'])
                qsParams['query.sort'] = options.params['query.sort'];
            options.url = LABKEY.ActionURL.buildURL("query", "executeSql.api", this.containerPath, qsParams);
        }
    },

    onBeforeProxyLoad: function(proxy, options) {
        //the selectRows.api can't handle the 'sort' and 'dir' params
        //sent by Ext, so translate them into the expected form
        if(options.sort)
            options['query.sort'] = "DESC" == options.dir
                    ? "-" + options.sort
                    : options.sort;

        var userFilters = this.getUserFilters();
        if (userFilters)
        {
            for (var i = 0; i < userFilters.length; i++)
            {
                var filter = userFilters[i];
                options[filter.getURLParameterName()] = filter.getURLParameterValue();
            }
        }
        delete options.dir;
        delete options.sort;
    },

    onBeforeLoad : function() {
        this.isLoading = true;
    },

    onLoad : function(store, records, options) {
        this.isLoading = false;

        //remeber the name of the id column
        this.idName = this.reader.meta.id;

        if(this.nullRecord)
        {
            //create an extra record with a blank id column
            //and the null caption in the display column
            var data = {};
            data[this.reader.meta.id] = "";
            data[this.nullRecord.displayColumn] = this.nullCaption || "[none]";

            var recordConstructor = Ext.data.Record.create(this.reader.meta.fields);
            var record = new recordConstructor(data, -1);
            this.insert(0, record);
        }
    },

    onLoadException : function(proxy, options, response, error)
    {
        this.isLoading = false;
        var loadError = {message: error};

        if(response && response.getResponseHeader
                && response.getResponseHeader["Content-Type"].indexOf("application/json") >= 0)
        {
            var errorJson = Ext.util.JSON.decode(response.responseText);
            if(errorJson && errorJson.exception)
                loadError.message = errorJson.exception;
        }

        this.loadError = loadError;
    },

    onUpdate : function(store, record, operation)
    {
        for(var field  in record.getChanges())
        {
            if(record.json && record.json[field])
            {
                delete record.json[field].displayValue;
                delete record.json[field].qcValue;
            }
        }
    },

    getDeleteSuccessHandler : function() {
        var store = this;
        return function(results) {
            store.reload();
        };
    },

    getRowData : function(record) {
        //need to convert empty strings to null before posting
        //Ext components will typically set a cleared field to
        //empty string, but this messes-up Lists in LabKey 8.2 and earlier
        var data = {};
        Ext.apply(data, record.data);
        for(var field in data)
        {
            if(null != data[field] && data[field].toString().length == 0)
                data[field] = null;
        }
        return data;
    },

    getOldKeys : function(record) {
        var oldKeys = {};
        oldKeys[this.reader.meta.id] = record.id;
        return oldKeys;
    },

    readyForSave : function(record) {
        //this is kind of hacky, but it seems that checking
        //for required columns is the job of the store, not
        //the bound control. Since the required prop is in the
        //column model, go get that from the reader
        var colmodel = this.reader.jsonData.columnModel;
        if(!colmodel)
            return true;

        var col;
        for(var idx = 0; idx < colmodel.length; ++idx)
        {
            col = colmodel[idx];

            if(col.dataIndex != this.reader.meta.id && col.required && !record.data[col.dataIndex])
                return false;
        }

        return true;
    },

    getUserFilters: function()
    {
        return this.userFilters || [];
    },

    setUserFilters: function(filters)
    {
        this.userFilters = filters;
    }
});
