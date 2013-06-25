/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function() {
    /**
     * @private
     */
    var validateFilter = function(filter) {
        var filterObj = {};
        if (filter instanceof LABKEY.Query.Filter || filter.getColumnName) {
            filterObj.fieldKey = LABKEY.FieldKey.fromString(filter.getColumnName()).getParts();
            filterObj.value = filter.getValue();
            filterObj.type = filter.getFilterType().getURLSuffix();
            return filterObj;
        }

        //If filter isn't a LABKEY.Query.Filter or LABKEY.Filter, then it's probably a raw object.
        if (filter.fieldKey) {
            filter.fieldKey = validateFieldKey(filter.fieldKey);
        } else {
            throw new Error('All filters must have a "fieldKey" attribute.');
        }

        if (!filter.fieldKey) {
            throw new Error("Filter fieldKeys must be valid FieldKeys");
        }

        if (!filter.type) {
            throw new Error('All filters must have a "type" attribute.');
        }
        return filter;
    };

    /**
     * @private
     */
    var validateFieldKey = function(fieldKey) {
        if (fieldKey instanceof LABKEY.FieldKey) {
            return fieldKey.getParts();
        }

        if (fieldKey instanceof Array) {
            return fieldKey;
        }

        if (typeof fieldKey === 'string') {
            return LABKEY.FieldKey.fromString(fieldKey).getParts();
        }

        return false;
    };

    /**
     * @private
     */
    var validateSource = function(source) {
        if (!source || source == null) {
            throw new Error('A source is required for a GetData request.');
        }

        if (!source.type) {
            source.type = 'query';
        }

        if (!source.schemaName) {
            throw new Error('A schemaName is required.');
        }

        source.schemaName = validateFieldKey(source.schemaName);

        if (!source.schemaName) {
            throw new Error('schemaName must be a FieldKey');
        }

        if (source.type === 'query') {
            if (!source.queryName || source.queryName == null) {
                throw new Error('A queryName is required for getData requests with type = "query"');
            }
        } else if (source.type === 'sql') {
            if (!source.sql) {
                throw new Error('sql is required if source.type = "sql"');
            }
        } else {
            throw new Error('Unsupported source type.');
        }
    };

    /**
     * @private
     */
    var validatePivot = function(pivot) {
        if (!pivot.columns || pivot.columns == null) {
            throw new Error('pivot.columns is required.');
        }

        if (!pivot.columns instanceof Array) {
            throw new Error('pivot.columns must be an array of fieldKeys.');
        }

        for (var i = 0; i < pivot.columns.length; i++) {
            pivot.columns[i] = validateFieldKey(pivot.columns[i]);

            if (!pivot.columns[i]) {
                throw new Error('pivot.columns must be an array of fieldKeys.');
            }
        }

        if (!pivot.by || pivot.by ==  null) {
            throw new Error('pivot.by is required');
        }

        pivot.by = validateFieldKey(pivot.by);

        if (!pivot.by === false) {
            throw new Error('pivot.by must be a fieldKey.');
        }
    };

    /**
     * @private
     */
    var validateTransform = function(transform) {
        var i;

        if (!transform.type || transform.type === null || transform.type === undefined) {
            throw new Error('Transformer type is required.');
        }

        if (transform.groupBy && transform.groupBy != null) {
            if (!transform.groupBy instanceof Array) {
                throw new Error('groupBy must be an array.');
            }
        }


        if (transform.aggregates && transform.aggregates != null) {
            if (!transform.aggregates instanceof Array) {
                throw new Error('aggregates must be an array.');
            }

            for (i = 0; i < transform.aggregates.length; i++) {
                if (!transform.aggregates[i].fieldKey) {
                    throw new Error('All aggregates must include a fieldKey.');
                }

                transform.aggregates[i].fieldKey = validateFieldKey(transform.aggregates[i].fieldKey);

                if (!transform.aggregates[i].fieldKey) {
                    throw new Error('Aggregate fieldKeys must be valid fieldKeys');
                }

                if (!transform.aggregates[i].type) {
                    throw new Error('All aggregates must include a type.');
                }
            }
        }

        if (transform.filters && transform.filters != null) {
            if (!transform.filters instanceof Array) {
                throw new Error('The filters of a transform must be an array.');
            }

            for (i = 0; i < transform.filters.length; i++) {
                transform.filters[i] = validateFilter(transform.filters[i]);
            }
        }
    };

    /**
     * @namespace GetData static class to access javascript APIs related to our GetData API.
     */
    LABKEY.Query.GetData = {
        /**
         * @function
         * @param {Object} config Required. An object which contains the following configuration properties:
         * @param {Object} config.source Required. An object which contains parameters related to the source of the request.
         * @param {String} config.source.type Required. A string with value set to either "query" or "sql". Indicates if the value is
         *      "sql" then source.sql is required. If the value is "query" then source.queryName is required.
         * @param {*} config.source.schemaName Required. The schemaName to use in the request. Can be a string, array of strings, or LABKEY.FieldKey.
         * @param {String} config.source.queryName The queryName to use in the request. Required if source.type = "query"
         * @param {String} config.source.sql The LabKey SQL to use in the request. Required if source.type = "sql"
         * @param {String} config.source.containerFilter Optional. The container filter to use in the request. See {@link LABKEY.Query.containerFilter}
         *      for valid container filter types.
         * @param {Object[]} config.transforms An array of objects with the following properties:
         *              <ul>
         *                  <li><strong>type</strong>: {String} Required.</li>
         *                  <li><strong>label</strong>: {String} Optional. The label used when the transform is rendered.</li>
         *                  <li>
         *                      <strong>pivot</strong>: {Object} Optional. Array of groupBy objects with the following properties:
         *                      <ul>
         *                          <li>
         *                              <strong>columns</strong>:
         *                              {Array} The columns to pivot. Is an array containing strings, arrays of strings, and/or
         *                              {@link LABKEY.FieldKey} objects.
         *                          </li>
         *                          <li>
         *                              <strong>by</strong>:
         *                              The column to pivot by. Can be an array of strings, a string, or a {@link LABKEY.FieldKey}
         *                          </li>
         *                      </ul>
         *                  </li>
         *                  <li>
         *                      <strong>aggregates</strong>: {Object[]} Optional. An array of objects with the following properties:
         *                      <ul>
         *                          <li>
         *                              <strong>fieldKey</strong>:
         *                              Required. The target column. Can be an array of strings, a string, or a {@link LABKEY.FieldKey}
         *                          </li>
         *                          <li><strong>type</strong>: {String} Required. The type of aggregate.</li>
         *                          <li><strong>alias</strong>: {String} Required. The name to alias the aggregate as.</li>
         *                          <li>
         *                              <strong>metadata</strong>: {Object} An object containing the ColumnInfo metadata properties.
         *                          </li>
         *                      </ul>
         *                  </li>
         *                  <li>
         *                      <strong>filters</strong>: {Object[]} Optional. An array containing  objects created with
         *                  {@link LABKEY.Filter.create}, {@link LABKEY.Query.Filter} objects, or javascript objects with the following
         *                  properties:
         *                      <ul>
         *                          <li>
         *                              <strong>fieldKey</strong>: Required. Can be a string, array of strings, or a
         *                          {@link LABKEY.FieldKey}
         *                          </li>
         *                          <li>
         *                              <strong>type</strong>: Required. Can be a string or a type from {@link LABKEY.Filter#Types}
         *                          </li>
         *                          <li><strong>value</strong>: Optional depending on filter type. The value to filter on.</li>
         *                      </ul>
         *                  </li>
         *              </ul>
         * @param {Array} config.columns Optional. An array containing {@link LABKEY.FieldKey} objects, strings, or arrays of strings.
         *      Used to specify which columns the user wants. The columns must match those returned from the last transform.
         * @param {Function} config.success Required. A function to be executed when the GetData request completes successfully. The function will
         *      be passed a {@link LABKEY.Query.Response} object.
         * @param {Function} config.failure Optional. If no failure function is provided the reponse is sent to the console via console.error.
         *      If a function is provided the JSON response is passed to it as the only parameter.
         * @returns {LABKEY.Ajax.request}
         */
        rawData: function(config) {
            if (!config || config === null || config === undefined) {
                throw new Error('A config object is required for GetData');
            }

            var i;
            var jsonData = {
                renderer: {type: 'json'}
            };
            var requestConfig = {
                method: 'POST',
                url: LABKEY.ActionURL.buildURL('query', 'getData'),
                jsonData: jsonData
            };

            validateSource(config.source);

            // Shallow copy source so if the user adds unexpected properties to source the server doesn't throw errors.
            jsonData.source = {
                type: config.source.type,
                schemaName: config.source.schemaName
            };

            if (config.source.type === 'query') {
                jsonData.source.queryName = config.source.queryName;
            }

            if (config.source.type === 'sql') {
                jsonData.source.sql = config.source.sql;
            }

            if (config.transforms) {
                if (!(config.transforms instanceof Array)) {
                    throw new Error("transforms must be an array.");
                }

                jsonData.transforms = config.transforms;
                for (i = 0; i < jsonData.transforms.length; i++) {
                    validateTransform(jsonData.transforms[i]);
                }
            }

            if (config.pivot) {
                validatePivot(config.pivot);
            }

            if (config.columns) {
                if (!config.columns instanceof Array) {
                    throw new Error('columns must be an array of FieldKeys.');
                }

                for (var i = 0; i < config.columns.length; i++) {
                    config.columns[i] = validateFieldKey(config.columns[i]);

                    if (!config.columns[i]) {
                        throw new Error('columns must be an array of FieldKeys.');
                    }
                }

                jsonData.renderer.columns = config.columns;
            }

            if (!config.failure) {
                requestConfig.failure = function(response, options) {
                    var json = LABKEY.ExtAdapter.decode(response.responseText);
                    console.error('Failure occurred during getData', json);
                };
            } else {
                requestConfig.failure = function(response, options) {
                    var json = LABKEY.ExtAdapter.decode(response.responseText);
                    config.failure(json);
                };
            }

            if (!config.success) {
                throw new Error("A success callback is required.");
            }

            if (!config.scope) {
                config.scope = this;
            }

            requestConfig.success = function(response) {
                var json = LABKEY.ExtAdapter.decode(response.responseText);
                var wrappedResponse = new LABKEY.Query.GetData.Response(json);
                config.success.call(config.scope, wrappedResponse);
            };

            return new LABKEY.Ajax.request(requestConfig);
        }
    };
})();