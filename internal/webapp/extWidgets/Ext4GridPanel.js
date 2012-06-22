/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('LABKEY.ext4');

/**
 * Constructs a new LabKey GridPanel using the supplied configuration.
 *
 * @param {LABKEY.ext4.Store} config.store A LABKEY.ext4.Store or a store config object which will be used to create a store.
 * @param (boolean) [config.supressErrorAlert] If true, no dialog will appear on if the store fires a syncerror event
 * @param {boolean} [config.hideNonEditableColumns] If true, columns that are non-editable will be hidden
 * @param {boolean} [config.showPagingToolbar] If true, an Ext PagingToolbar will be appended to the bottom of the grid
 * @param {boolean} [config.constraintColumnWidths] If true, the column widths will be resized to fit within the current width of the gridPanel.
 * @example &lt;script type="text/javascript"&gt;
    Ext4.onReady(function(){

        var store = new LABKEY.ext4.Store({
            schemaName: 'lists',
            queryName: 'myList'
        });

        //create a grid panel using that store as the data source
        var grid1  = new LABKEY.ext4.GridPanel({
            store: store,
            renderTo: 'grid1',
            title: 'Example GridPanel 1'
        });

        //create a formpanel using a store config object
        var formPanel2 = new LABKEY.ext4.GridPanel({
            store: {
                schemaName: 'lists',
                queryName: 'myList',
                metadata: {
                    field1: {
                       //this config will be applied to the Ext column config object only
                       columnConfig: {
                           text: 'Custom Caption
                       },
                       //this config will be applied to the Ext grid editor config object
                       gridEditorConfig: {
                           xtype: 'datefield',
                           width: 250
                       },
                       fieldLabel: 'Custom Label'
                   }
                }
            },
            title: 'Example GridPanel 2'
        }).render('grid2');
    });


&lt;/script&gt;
&lt;div id='grid1'/&gt;
&lt;div id='grid2'/&gt;
 */

Ext4.define('LABKEY.ext4.GridPanel', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.labkey-gridpanel',
    config: {
        defaultFieldWidth: 200,
        editable: true,
        pageSize: 200,
        autoSave: false,
        multiSelect: true
    },
    initComponent: function(){
        this.initStore();

        Ext4.QuickTips.init({
            constrainPosition: true
        });

        Ext4.applyIf(this, {
            columns: [],

            /*
             * The intent of these options is to infer column widths based on the data being shown
             */
            charWidth  : 6,  //TODO: this should be measured, but measuring is expensive so we only want to do it once
            colPadding : 10, //TODO: also should be calculated
            maxColWidth: 400
        });

        if(this.showPagingToolbar){
            this.dockedItems = this.dockedItems || [];
            this.dockedItems.push({
                xtype: 'pagingtoolbar',
                store: this.store,   // same store GridPanel is using
                dock: 'bottom',
                ui: this.ui,
                displayInfo: true
            });
        }

        this.configurePlugins();

        if(LABKEY.ext.Ext4Helper.hasStoreLoaded(this.store)){
            this.columns = this.getColumnsConfig();
        }

        this.callParent();

        this.configureHeaders();

        if(!this.columns.length){
            this.mon(this.store, 'load', this.setupColumnModel, this, {single: true});
            if(!this.store.isLoading()){
                this.store.load({ params : {
                    start: 0,
                    limit: this.pageSize
                }});
            }
        }

        this.mon(this.store, 'exception', this.onCommitException, this);
        /**
         * @event columnmodelcustomize
         */
        /**
         * Experimental.  Lookups sometimes create a separate store to find the display string for a field.  When this
         * store loads, it can cause the grid to refresh, which is expensive.  This event is used internally
         * to batch these events and minimze the grid refreshes.
         * @private
         * @event lookupstoreload
         */
        this.addEvents('columnmodelcustomize', 'lookupstoreload');

        this.on('lookupstoreload', this.onLookupStoreEventFired, this, {buffer: 200});
    }

    ,configureHeaders: function(){
        if(!this.headerCt)
            return;

        this.mon(this.headerCt, 'menucreate', this.onMenuCreate, this);
    }

    ,onMenuCreate: Ext4.emptyFn

    ,initStore: function(){
        if(!this.store){
            alert('Must provide a store or store config when creating a gridpanel');
            return;
        }

        //allow creation of panel using store config object
        if(!this.store.events)
            this.store = Ext4.create('LABKEY.ext4.Store', this.store);

        this.store.supressErrorAlert = true;

        //TODO: need a better solution to this problem.  maybe be smarter when processing load() in the store?
        //if we sort/filter remotely, we risk losing changes made on the client
        if(this.editable){
            this.store.remoteSort = false;
            this.store.remoteFilter = false;
        }

        if(this.autoSave)
            this.store.autoSync = true;  //could we just obligate users to put this on the store directly?
    }

    //separated to allow subclasses to override
    ,configurePlugins: function(){
        this.plugins = this.plugins || [];
        if(this.editable)
            this.plugins.push(Ext4.create('Ext.grid.plugin.CellEditing', {pluginId: 'cellediting', clicksToEdit: 2}));

        if(!this.selModel){
            //NOTE: this is overridden in order to prevent the grid from focusing to itself on mouseclick.  Ext has bugs on this and it may be fixed in versions above 4.0.7
            this.selModel = {
                xtype: 'rowmodel',
                //TODO: probably fixed in ext4.1 and can be removed then
                //@Override
                onRowMouseDown: function(view, record, item, index, e) {
                    //view.el.focus();
                    if (!this.allowRightMouseSelection(e)) {
                        return;
                    }
                    this.selectWithEvent(record, e);
                }
            }
        }
    }

    ,setupColumnModel : function() {
        var columns = this.getColumnsConfig();

        //TODO: make a map of columnNames -> positions like Ext3?
        this.fireEvent("columnmodelcustomize", this, columns);

        this.columns = columns;

        //reset the column model
        this.reconfigure(this.store, columns);

    }
    ,getColumnsConfig: function(){
        var config = {
            editable: this.editable,
            defaults: {
                sortable: false
            }
        };

        if(this.metadataDefaults){
            Ext4.Object.merge(config, this.metadataDefaults);
        }
        if(this.metadata && this.metadata[c.name]){
            Ext4.Object.merge(config, this.metadata[c.name]);
        }

        var columns = LABKEY.ext.Ext4Helper.getColumnsConfig(this.store, this, config);

        for (var idx=0;idx<columns.length;idx++){
            var col = columns[idx];
            var meta = this.store.findFieldMetadata(col.dataIndex);

            //remember the first editable column (used during add record)
            if(!this.firstEditableColumn && col.editable)
                this.firstEditableColumn = idx;

            if(meta.isAutoExpandColumn && !col.hidden){
                this.autoExpandColumn = idx;
            }

            //listen for changes in underlying data in lookup store
            if(meta.lookup && meta.lookups !== false){
                var lookupStore = LABKEY.ext.Ext4Helper.getLookupStore(meta);

                //this causes the whole grid to rerender, which is very expensive.  better solution?
                if(lookupStore){
                    this.mon(lookupStore, 'load', this.onLookupStoreLoad, this, {delay: 100});
                }
            }

            if (this.hideNonEditableColumns && !col.editable) {
                col.hidden = true;
            }
        }

        this.inferColumnWidths(columns);

        return columns;
    }

    //private.  separated to allow buffering, since refresh is expensive
    ,onLookupStoreLoad: function(lookupStore){
        if(!this.rendered || !this.getView()){
            return;
        }
        this.fireEvent('lookupstoreload');
    }

    //private
    ,onLookupStoreEventFired: function(){
        this.getView().refresh();
    }

    ,inferColumnWidths: function(columns){
        var col,
            meta,
            value,
            values,
            totalRequestedWidth = 0;

        for (var i=0;i<columns.length;i++){

            col = columns[i];
            meta = this.store.findFieldMetadata(col.dataIndex);

            if(!meta.fixedWidthCol){
                values = [];
                var records = this.store.getRange();
                for (var j=0;j<records.length;j++){
                    var rec = records[j];
                    value = LABKEY.ext.Ext4Helper.getDisplayString(rec.get(meta.name), meta, rec, rec.store);
                    if(!Ext4.isEmpty(value)) {
                        values.push(value.length);
                    }
                }

                //TODO: this should probably take into account mean vs max, and somehow account for line wrapping on really long text
                var avgLen = values.length ? (Ext4.Array.sum(values) / values.length) : 1;

                col.width = Math.max(avgLen, col.header.length) * this.charWidth + this.colPadding;
                col.width = Math.min(col.width, this.maxColWidth);
            }

            if (!col.hidden) {
                totalRequestedWidth += col.width || 0;
            }
        }

        if (this.constraintColumnWidths) {

            for (i=0;i<columns.length;i++){
                col = columns[i];
                if (!col.hidden) {
                    col.flex  = (col.width / totalRequestedWidth);
                    col.width = null;
                }
            }
        }
    }

    ,getColumnById: function(colName){
        return this.getColumnModel().getColumnById(colName);
    }

    ,onCommitException: function(store, message, response, operation){
        var msg = message || 'There was an error with the submission';

        if(!this.supressErrorAlert)
            Ext4.Msg.alert('Error', msg);
    }
});

LABKEY.ext4.GRIDBUTTONS = {
    /**
     *
     * @param name
     * @param config
     */
    getButton: function(name, config){
        return LABKEY.ext4.GRIDBUTTONS[name] ? LABKEY.ext4.GRIDBUTTONS[name](config) : null;
    },

    //TODO: make these private?
    ADDRECORD: function(config){
        return Ext4.Object.merge({
            text: 'Add Record',
            tooltip: 'Click to add a row',
            handler: function(btn){
                var grid = btn.up('gridpanel');
                if(!grid.store || !LABKEY.ext.Ext4Helper.hasStoreLoaded(grid.store))
                    return;

                grid.getPlugin('cellediting').completeEdit( );
                grid.store.insert(grid.store.createModel({}), 0); //add a blank record in the first position
                grid.getPlugin('cellediting').startEditByPosition({row: 0, column: this.firstEditableColumn || 0});
            }
        }, config);
    },
    DELETERECORD: function(config){
        return Ext4.Object.merge({
            text: 'Delete Records',
            tooltip: 'Click to delete selected rows',
            handler: function(btn){
                var grid = btn.up('gridpanel');
                var selections = grid.getSelectionModel().getSelection();

                if(!grid.store || !selections || !selections.length)
                    return;

                grid.store.remove(selections);
            }
        }, config);
    },
    SUBMIT: function(config){
        return Ext4.Object.merge({
            text: 'Submit',
            formBind: true,
            handler: function(btn, key){
                var panel = btn.up('gridpanel');
                panel.store.on('write', function(store, success){
                    Ext4.Msg.alert("Success", "Your upload was successful!", function(){
                        window.location = btn.successURL || LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: this.store.schemaName, 'query.queryName': this.store.queryName})
                    }, panel);
                }, this);
                panel.store.sync();
            }
        }, config);
    },
    CANCEL: function(config){
        return Ext4.Object.merge({
            text: 'Cancel',
            handler: function(btn, key){
                window.location = btn.returnURL || LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.buildURL('project', 'begin')
            }
        }, config)
    }
}
