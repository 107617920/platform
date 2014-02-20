/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * User: Rylan
 * Date: 10/31/12
 * Time: 10:51 AM
 */

Ext4.define('LABKEY.sqv.Model', {
    extend : 'Ext.Component',

    constructor: function (config) {
        this.containerCombo = config.containerCombo;
        this.schemaCombo = config.schemaCombo;
        this.queryCombo = config.queryCombo;
        this.viewCombo = config.viewCombo;

         //Store and model for the schemaCombo
        Ext4.define('schemaModel', {
            extend : 'Ext.data.Model',
            fields : [
                {name: 'schema', sortType: 'asUCString'}
            ],
            idProperty: 'schema'
        });

        //Store and model for the queryCombo
        Ext4.define('queryModel', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'isUserDefined', type : 'boolean'},
                {name : 'title',         type : 'string', sortType: 'asUCString'},
                {name : 'name',          type : 'string', sortType: 'asUCString'},
                {name : 'label',         type : 'string', sortType: 'asUCString', convert: function(value, record){
                    if (record.data.title != null && record.data.title != '')
                        return record.data.name != record.data.title ? record.data.name + ' (' + record.data.title + ')' : record.data.title;
                    else
                        return record.data.name;
                }},
                {name : 'viewDataURL',   type : 'string'},
                {name : 'listId',        type : 'string'}
            ],
            idProperty: 'name'
        });

        this.queryStore = Ext4.create('Ext.data.Store', {
            model : 'queryModel',
            sorters : {property: 'name', direction : 'ASC'}
        });

        //Store and model for the viewCombo
        Ext4.define('viewModel', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'default',      type : 'boolean'},
                {name : 'name',         type : 'string', sortType: 'asUCString'},
                // displayName is a client-side calculated column created in changeViewStore()
                {name : 'displayName',  type : 'string'},
                {name : 'viewDataUrl',  type : 'string'}
            ],
            idProperty: 'name'
        });

        this.viewStore = Ext4.create('Ext.data.Store', {
            model : 'viewModel',
            sorters : {property: 'name', direction : 'ASC'}
        });


        this.addEvents({
            beforeschemaload : true,
            schemaload : true,
            beforequeryload : true,
            queryload : true,
            beforeviewload : true,
            viewload : true
        });
        this.callParent([config]);
    },

    getContainerStore : function (config) {
        if (this.containerStore) {
            return this.containerStore;
        }

        // Store for the containerCombo
        this.containerStore = Ext4.create('LABKEY.ext4.data.Store', {
            schemaName: 'core',
            queryName: 'containers',
            columns: 'Name,Path,EntityId',
            // Server-side sorting on 'Path' isn't currently supported so sort in the store instead.
            //sort: 'Path',
            remoteSort: false,
            sorters: [{
                property: 'Path',
                root: 'data'
            }],
            containerFilter: LABKEY.Query.containerFilter.allFolders,
            filterArray: [
                LABKEY.Filter.create('ContainerType', 'workbook', LABKEY.Filter.Types.NEQ),
                LABKEY.Filter.create('Name', '/', LABKEY.Filter.Types.NEQ)
            ],
            autoLoad: true
        });

        return this.containerStore;
    },

    makeContainerComboConfig : function (config) {
        Ext4.applyIf(config, {
            xtype: 'labkey-combo',
            name: 'containerId',
            editable: true,
            fieldLabel: 'Container',
            queryMode: 'local',
            valueField: 'EntityId',
            displayField: 'Path',
            store: this.getContainerStore(config),
            listConfig : {
                getInnerTpl: function (displayField) {
                    return '{' + displayField + ':htmlEncode}';
                }
            }
        });

        // merge in listeners
        config.listeners = config.listeners || {};
        config.listeners['afterrender'] = {
            fn : function (cb) {
                this.containerCombo = cb;
            },
            scope : this
        };
        config.listeners['change'] = {
            fn: function (field, newValue, oldValue) {
                var record = field.store.getById(newValue);
                if (record) {
                    this.onContainerChange(newValue, oldValue);
                }
            },
            scope: this
        };
        config.listeners['boxready'] = {
            fn : function (cb, width, height) {
                cb.addCls('containers-loaded-marker');
            },
            scope : this
        };

        return config;
    },

    getSchemaStore : function (storeConfig) {
        if (this.schemaStore) {
            return this.schemaStore;
        }

        var storeConfig = Ext4.applyIf(storeConfig, {
            model : 'schemaModel',
            sorters : {property: 'schema', direction : 'ASC'}
        });

        this.schemaStore = Ext4.create('Ext.data.Store', storeConfig);

        return this.schemaStore;
    },

    //Makes the configuration for a schemaCombo.  NOTE:  Does not set its own value of SchemaCombo to this one,
    //so you still need to add it after at present.
    makeSchemaComboConfig : function (config) {
        Ext4.applyIf(config, {
            xtype : 'combo',
            name : 'schemaCombo',
            queryMode : 'local',
            store : this.getSchemaStore(config.storeConfig || {}),
            fieldLabel : 'Schema',
            valueField : 'schema',
            displayField : 'schema',
            editable : false,
            disabled : this.containerCombo,
            allowBlank : false,
            listConfig : {
                getInnerTpl: function (displayField) {
                    return '{' + displayField + ':htmlEncode}';
                }
            },
            scope : this
        });

        // merge in listeners
        config.listeners = config.listeners || {};
        config.listeners['afterrender'] = {
            fn : function (cb) {
                this.schemaCombo = cb;

                var containerId = null;
                if (this.containerCombo) {
                    containerId = this.containerCombo.getValue();
                }
                else if (this.schemaCombo && this.schemaCombo.defaultContainer) {
                    containerId = this.schemaCombo.defaultContainer;
                }

                this.onContainerChange(containerId);
            },
            scope : this
        };
        config.listeners['change'] = {
            fn : function (cb, newValue, oldValue) {
                var record = cb.store.getById(newValue);
                if (record) {
                    this.onSchemaChange(newValue, oldValue);
                }
            },
            scope : this
        };
        config.listeners['dataloaded'] = {
            fn : function (cb) {
                if (!cb.initiallyLoaded) {
                    cb.initiallyLoaded = true;
                    if (cb.initialValue !== undefined) {
                        this.setComboValues(cb, cb.initialValue);
                    }
                    cb.addCls('schema-loaded-marker');
                }
            },
            scope : this
        };

        return config;
    },

    makeQueryComboConfig : function (config) {
        if (config.multiSelect) {
            Ext4.applyIf(config, {
                xtype: 'checkcombo',
                delim: ','
            });
        }

        Ext4.applyIf(config, {
            xtype:'combo',
            name: 'queryCombo',
            queryMode:'local',
            fieldLabel: config.defaultSchema || 'Query',
            displayField:'label',
            valueField:'name',
            store:this.queryStore,
            editable:false,
            disabled:true,
            allowBlank:false,
            listConfig : {
                getInnerTpl: function (displayField) {
                    return '{' + displayField + ':htmlEncode}';
                }
            },
            scope:this
        });

        config.listeners = config.listeners || {};
        config.listeners['afterrender'] = {
            fn : function (cb) {
                this.queryCombo = cb;
                if (cb.defaultSchema) {
                    var selectedContainerId = null;
                    if (this.containerCombo)
                        selectedContainerId = this.containerCombo.getValue();

                    this.changeQueryStore(selectedContainerId, cb.defaultSchema);
                    cb.fieldLabel = cb.defaultSchema;
                }
            },
            scope : this
        };
        config.listeners['change'] = {
            fn :  function (cb, newValue, oldValue) {
                var record = cb.store.getById(newValue);
                if (record) {
                    this.onQueryChange(newValue, oldValue);
                }
            },
            scope : this
        };
        config.listeners['dataloaded'] = {
            fn : function (cb) {
                if (!cb.initiallyLoaded) {
                    cb.initiallyLoaded = true;
                    if (cb.initialValue !== undefined) {
                        this.setComboValues(cb, cb.initialValue);
                    }
                }
                cb.addCls('query-loaded-marker');
            },
            scope : this
        };
        return config;
    },

    makeViewComboConfig : function(config) {
        Ext4.applyIf(config, {
            xtype : 'combo',
            name : 'viewCombo',
            queryMode : 'local',
            fieldLabel : 'View',
            valueField : 'name',
            displayField : 'displayName',
            initialValue : '',
            disabled : true,
            editable : false,
            store : this.viewStore,
            listConfig : {
                getInnerTpl: function (displayField) {
                    return '{' + displayField + ':htmlEncode}';
                }
            },
            scope : this
        });
        if (config.initialValue == '[default view]')
            config.initialValue = '';

        config.listeners = {
            afterrender : function(cb) {
                this.viewCombo = cb;
            },
            change : function (cb, newValue, oldValue) {
                var record = cb.store.getById(newValue);
                if (record) {
                    this.onViewChange(newValue, oldValue);
                }
            },
            dataloaded : function(cb) {
                if (!cb.initiallyLoaded) {
                    cb.initiallyLoaded = true;
                    if (cb.initialValue !== undefined) {
                        this.setComboValues(cb, cb.initialValue);
                    }
                    cb.addCls('view-loaded-marker');
                }
            },
            scope : this
        };
        return config;
    },

    onContainerChange : function (newValue, oldValue) {
        this.changeSchemaStore(newValue);
    },

    onSchemaChange : function (newValue, oldValue) {
        var containerId = null;
        if (this.containerCombo) {
            containerId = this.containerCombo.getValue();
        }
        else if (this.schemaCombo && this.schemaCombo.defaultContainer) {
            containerId = this.schemaCombo.defaultContainer;
        }

        if (newValue) {
            this.changeQueryStore(containerId, newValue);
        }
    },

    onQueryChange : function (newValue, oldValue) {
        var containerId = null;
        if (this.containerCombo) {
            containerId = this.containerCombo.getValue();
        }
        else if (this.schemaCombo && this.schemaCombo.defaultContainer) {
            containerId = this.schemaCombo.defaultContainer;
        }

        var schema;
        if (this.queryCombo && this.queryCombo.defaultSchema) {
            schema = this.queryCombo.defaultSchema;
        }
        else if (this.schemaCombo) {
            schema = this.schemaCombo.getRawValue();
        }

        if (schema && newValue) {
            this.changeViewStore(containerId, schema, newValue);
        }
    },

    onViewChange : function (newValue, oldValue) {

    },

    changeSchemaStore : function (selectedContainerId) {
        if (this.schemaCombo) {
            if (false === this.fireEvent('beforeschemaload', this, selectedContainerId))
                return;

            this.schemaCombo.removeCls('schema-loaded-marker');

            var currentSchema = this.schemaCombo.getValue();
            this.schemaCombo.setDisabled(true);
            this.schemaCombo.clearValue();

            this.schemaCombo.setLoading(true);
            LABKEY.Query.getSchemas({
                containerPath: selectedContainerId,
                success : function (schemasInfo) {
                    this.schemaCombo.setLoading(false);
                    var schemaData = schemasInfo,
                        arrayedData = [];
                    for (var i = 0; i < schemaData.schemas.length; i++) {
                        arrayedData.push({"schema" : schemaData.schemas[i]});
                    }
                    schemaData = arrayedData;
                    this.schemaStore.loadData(arrayedData);
                    this.setComboValues(this.schemaCombo, currentSchema);
                    this.schemaCombo.setDisabled(false);
                    this.schemaCombo.fireEvent('dataloaded', this.schemaCombo);
                    this.fireEvent('schemaload', this, selectedContainerId);
                },
                scope: this
            });
        }
    },

    changeQueryStore : function (selectedContainerId, selectedSchema) {
        if (this.queryCombo) {
            if (false === this.fireEvent('beforequeryload', this, selectedContainerId, selectedSchema))
                return;

            this.queryCombo.removeCls('query-loaded-marker');

            var currentQuery = this.queryCombo.getValue();
            this.queryCombo.setDisabled(true);
            this.queryCombo.clearValue();

            var includeUserQueries = true;
            if (this.queryCombo.includeUserQueries === false)
                includeUserQueries = false;

            var includeSystemQueries = true;
            if (this.queryCombo.includeSystemQueries === false)
                includeSystemQueries = false;

            this.queryCombo.setLoading(true);
            LABKEY.Query.getQueries({
                containerPath: selectedContainerId,
                schemaName : selectedSchema,
                includeUserQueries: includeUserQueries,
                includeSystemQueries: includeSystemQueries,
                includeColumns: false,
                success : function (details) {
                    this.queryCombo.setLoading(false);
                    var newQueries = details.queries;
                    this.queryStore.loadData(newQueries);
                    this.setComboValues(this.queryCombo, currentQuery);
                    this.queryCombo.setDisabled(false);
                    this.queryCombo.fireEvent('dataloaded', this.queryCombo);
                    this.fireEvent('queryload', this, selectedContainerId, selectedSchema);
                },
                scope : this
            });
        }
    },

    changeViewStore : function (selectedContainerId, selectedSchema, selectedQuery) {
        if (this.viewCombo) {
            if (false === this.fireEvent('beforeviewload', this, selectedContainerId, selectedSchema, selectedQuery))
                return;

            this.viewCombo.removeCls('view-loaded-marker');

            var currentView = this.viewCombo.getValue();
            this.viewCombo.setDisabled(true);
            this.viewCombo.clearValue();

            this.viewCombo.setLoading(true);
            LABKEY.Query.getQueryViews({
                scope : this,
                containerPath: selectedContainerId,
                schemaName : selectedSchema,
                queryName : selectedQuery,
                successCallback : function (details) {
                    this.viewCombo.setLoading(false);
                    var filteredViews = [];
                    for (var i = 0; i < details.views.length; i++) {
                        var view = details.views[i];
                        if (!view.hidden) {
                            // CONSIDER: just use view.label instead of creating a calculated display value
                            var viewDisplayName = view.label;
                            if (view.name == "" && viewDisplayName == "default") {
                                viewDisplayName = "[default view]";
                            }
                            if (view["default"] && viewDisplayName != "[default view]") {
                                viewDisplayName += " [default]";
                            }
                            view.displayName = viewDisplayName;
                            filteredViews.push(view);
                        }
                    }
                    this.viewStore.loadData(filteredViews);
                    this.setComboValues(this.viewCombo, currentView);
                    this.viewCombo.setDisabled(false);
                    this.viewCombo.fireEvent('dataloaded', this.viewCombo);
                    this.fireEvent('viewload', this, selectedContainerId, selectedSchema, selectedQuery);
                }
            });
        }
    },

    // Sets the values on a combobox if a corresponding record is found in the store.
    setComboValues : function (combo, values) {
        if (values !== undefined) {
            if (combo.multiSelect) {
                if (Ext4.isString(values))
                    values = values.split(",");

                if (Ext4.isArray(values)) {
                    // Get the idProperty of the model.  I'm not sure how to get this other than constructing a new model instance.
                    var record = combo.store.model.create({});
                    if (record) {
                        var idProperty = record.idProperty;

                        var records = [];
                        for (var i = 0; i < values.length; i++) {
                            // This should be case-insensitive, which is important, #19440
                            var idx = combo.store.find(idProperty, values[i]);
                            if (-1 != idx)
                                records.push(combo.store.getAt(idx));
                        }
                        combo.setValue(records);
                        //combo.fireEvent('select', combo);
                    }
                }
            }
            else if (combo.store.getById(values)) {
                combo.setValue(values);
                //combo.fireEvent('select', combo);
            }
        }
    }

});

Ext4.define('LABKEY.sqv.Picker', {
    extend : 'Ext.Panel',

    constructor : function (config) {
        this.border = false;
        this.callParent([config]);
    },

    initComponent : function () {

        var sqvModel = Ext4.create('LABKEY.sqv.Model', {});

        var schemaCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeSchemaComboConfig({}));
        var queryCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeQueryComboConfig({}));
        var viewCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeViewComboConfig({}));

        this.items = [schemaCombo, queryCombo, viewCombo];

        this.callParent();
    }
});
