/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('LABKEY.ext4');

/**
 * Constructs a new LabKey FormPanel using the supplied configuration.
 * @class LabKey extension to the <a href="http://docs.sencha.com/ext-js/4-0/#!/api/Ext.form.Panel">Ext.data.FormPanel</a> class,
 * which constructs a form panel, configured based on the query's metadata.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeyExt">Tips for Using Ext to Build LabKey Views</a></li>
 *              </ul>
 *           </p>
 * @constructor
 * @augments Ext.form.Panel
 * @param config Configuration properties.
 * @param {String} config.store A LABKEY.ext4.Store or a store config object which will be used to create a store.
 * @param {Object} [config.metadata] A metadata object that will be applied to the default metadata returned by the server.  This should be a map where the keys match field names (case-sensitive).  This will not modify the underlying store.  See example below for usage.
 * @param {Object} [config.metadataDefaults] A metadata object that will be applied to every field of the default metadata returned by the server.  Will be superceeded by the metadata object in case of conflicts. See example below for usage.
 * @param (boolean) [config.supressErrorAlert] If true, no dialog will appear on if the store fires a syncerror event.  Defaults to false.
 * @param {boolean} [config.supressSuccessAlert] If true, no alert will appear after a successful save event.  Defaults to false.
 * @param {object} config.bindConfig
 * @example &lt;script type="text/javascript"&gt;
    Ext4.onReady(function(){

        var store = new LABKEY.ext4.Store({
            schemaName: 'lists',
            queryName: 'myList'
        });

        //create a form panel using that store as the data source
        var formPanel1  = new LABKEY.ext4.FormPanel({
            store: store,
            renderTo: 'formPanel1',
            title: 'Example FormPanel 1',
            bindConfig: {
                autoCreateRecordOnChange: true,
                autoBindFirstRecord: true
            }
        });

        //create a formpanel using a store config object
        var formPanel2 = new LABKEY.ext4.FormPanel({
            store: {
                schemaName: 'lists',
                queryName: 'myList',
                viewName: 'view1',
                //this is an alternate method to supply metadata config.  see LABKEY.ext4.Store for more information
                metadata: {
                    field2: {
                        //this config will be applied to the Ext grid editor config object
                        formEditorConfig: {
                            xtype: 'datefield',
                            fieldLabel
                            width: 250
                        }
                    }
                }
            },
            title: 'Example FormPanel 2',
            bindConfig: {
                autoCreateRecordOnChange: true,
                autoBindFirstRecord: true
            },
            //this config will be applied to the Ext fields created in this FormPanel only.
            metadata: {
               field1: {
                   fieldLabel: 'Custom Label'
               }
            }
        }).render('formPanel2');
    });


&lt;/script&gt;
&lt;div id='formPanel1'/&gt;
&lt;div id='formPanel2'/&gt;
 */


Ext4.define('LABKEY.ext4.FormPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.labkey-formpanel',
    autoHeight: true,
    defaultFieldWidth: 350,
    defaultFieldLabelWidth: 150,
    initComponent: function(){
        Ext4.QuickTips.init();
//        Ext4.FocusManager.enable();

        this.initStore();

        Ext4.apply(this, {
            trackResetOnLoad: true
            ,autoHeight: true
            ,bubbleEvents: ['added']
            ,buttonAlign: 'left'
            ,monitorValid: false
        });

        this.plugins = this.plugins || [];
        this.plugins.push(Ext4.create('LABKEY.ext4.DatabindPlugin'));

        LABKEY.Utils.mergeIf(this, {
            items: [{xtype: 'displayfield', value: 'Loading...'}]
            ,bodyBorder: false
            ,bodyStyle: 'padding:5px'
            ,style: 'margin-bottom: 15px'
            ,buttons: [
                LABKEY.ext4.FORMBUTTONS.getButton('SUBMIT'),
                LABKEY.ext4.FORMBUTTONS.getButton('CANCEL')
            ]
        });

        this.mon(this.store, 'exception', this.onCommitException, this);

        if(Ext4.isString(this.errorEl))
            this.errorEl = Ext4.get(this.errorEl);

        this.callParent();

        if(!LABKEY.ext.Ext4Helper.hasStoreLoaded(this.store))
            this.mon(this.store, 'load', this.loadQuery, this, {single: true});
        else {
            //TODO: calling after callParent() may be forcing an unnecessary second layout
            this.loadQuery(this.store);
        }

        /**
         * @memberOf LABKEY.ext4.FormPanel#
         * @name formconfiguration
         * @event
         * @description Fired after the query metadata has been processed to produce an array of fields.  Provides an opportunity to edit the fields or change the layout.
         * @param {Array} fields The array of fields that will be added to the form panel.
         */
        /**
         * @memberOf LABKEY.ext4.FormPanel#
         * @name fieldvaluechange
         * @event
         * @description Fired when the value of any field in the panel changes
         */
        /**
         * @memberOf LABKEY.ext4.FormPanel#
         * @name recordchange
         * @event
         * @description Fired when the record bound to this panel changes
         */
        this.addEvents('formconfiguration', 'fieldvaluechange', 'recordchange');
        this.on('recordchange', this.markInvalid, this, {buffer: 50});
    },

    initStore: function(){
        if(!this.store){
            alert('Must provide a store or store config when creating a formpanel');
            return;
        }

        //allow creation of panel using store config object
        if(!this.store.events)
            this.store = Ext4.create('LABKEY.ext4.Store', this.store);

        this.store.supressErrorAlert = true;
    },

    onCommitException: function(store, msg, response, operation){
        if(!msg)
            msg = 'There was an error with the submission';

        //NOTE: in the case of trigger script errors, this will display the first error, even if many errors were generated
        if(!this.supressErrorAlert)
            Ext4.Msg.alert('Error', msg);

        this.getForm().isValid(); //triggers revalidation
    },

    loadQuery: function(store, records, success)
    {
        this.removeAll();

        if(success===false){
            this.add({html: 'The store did not load properly', border: false});
            return;
        }

        var fields = LABKEY.ext.Ext4Helper.getStoreFields(this.store);
        if(!fields){
            console.log('There are no fields in the store');
            return;
        }

        var toAdd = this.configureForm(store);

        this.fireEvent('formconfiguration', this, toAdd);

        //create a placeholder for error messages
        if(!this.errorEl){
            toAdd.push({
                tag: 'div',
                itemId: 'errorEl',
                border: false,
                style: 'padding:5px;text-align:center;'
            });
        }

        this.add(toAdd);
    },
    //NOTE: can be overridden for custom layouts
    configureForm: function(store){
        var toAdd = [];
        var compositeFields = {};
        LABKEY.ext.Ext4Helper.getStoreFields(store).each(function(c){
            var config = {
                queryName: store.queryName,
                schemaName: store.schemaName
            };

            if(this.metadataDefaults){
                Ext4.Object.merge(config, this.metadataDefaults);
            }
            if(this.metadata && this.metadata[c.name]){
                Ext4.Object.merge(config, this.metadata[c.name]);
            }

            if (LABKEY.ext.Ext4Helper.shouldShowInUpdateView(c)){
                var fields = LABKEY.ext.Ext4Helper.getStoreFields(this.store);
                var theField = LABKEY.ext.Ext4Helper.getFormEditorConfig(fields.get(c.name), config);

                if(!c.width)
                    theField.width = this.defaultFieldWidth;
                if(!c.width)
                    theField.labelWidth = this.defaultFieldLabelWidth;

                if (c.inputType == 'textarea' && !c.height){
                    Ext4.apply(theField, {height: 100});
                }

                if(theField.xtype == 'combo'){
                    theField.store.autoLoad = true;
                }

                if(c.isUserEditable===false || c.isAutoIncrement || c.isReadOnly){
                    theField.xtype = 'displayfield';
                }

                if(!c.compositeField)
                    toAdd.push(theField);
                else {
                    theField.fieldLabel = undefined;
                    if(!compositeFields[c.compositeField]){
                        compositeFields[c.compositeField] = {
                            xtype: 'panel',
                            autoHeight: true,
                            layout: 'hbox',
                            border: false,
                            fieldLabel: c.compositeField,
                            defaults: {
                                border: false,
                                margins: '0px 4px 0px 0px '
                            },
                            width: this.defaultFieldWidth,
                            items: [theField]
                        };
                        toAdd.push(compositeFields[c.compositeField]);

                        if(compositeFields[c.compositeField].msgTarget == 'below'){
                            //create a div to hold error messages
                            compositeFields[c.compositeField].msgTargetId = Ext4.id();
                            toAdd.push({
                                tag: 'div',
                                fieldLabel: null,
                                border: false,
                                id: compositeFields[c.compositeField].msgTargetId
                            });
                        }
                        else {
                            theField.msgTarget = 'qtip';
                        }
                    }
                    else {
                        compositeFields[c.compositeField].items.push(theField);
                    }
                }
            }
        }, this);

        //distribute width for compositeFields
        for (var i in compositeFields){
            var compositeField = compositeFields[i];
            var toResize = [];
            //this leaves a 2px buffer between each field
            var availableWidth = this.defaultFieldWidth - 4*(compositeFields[i].items.length-1);
            for (var j=0;j<compositeFields[i].items.length;j++){
                var field = compositeFields[i].items[j];
                //if the field isnt using the default width, we assume it was deliberately customized
                if(field.width && field.width!=this.defaultFieldWidth){
                    availableWidth = availableWidth - field.width;
                }
                else {
                    toResize.push(field)
                }
            }

            if(toResize.length){
                var newWidth = availableWidth/toResize.length;
                for (j=0;j<toResize.length;j++){
                    toResize[j].width = newWidth;
                }
            }
        }

        return toAdd;
    },

    doSubmit: function(btn){
        btn.setDisabled(true);

        if(!this.store.getNewRecords().length && !this.store.getUpdatedRecords().length && !this.store.getRemovedRecords().length){
            Ext4.Msg.alert('No changes', 'There are no changes, nothing to do');
            window.location = btn.successURL || LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: this.store.schemaName, 'query.queryName': this.store.queryName})
            return;
        }

        function onSuccess(store){
            this.mun(this.store, onError);
            btn.setDisabled(false);

            if(!this.supressSuccessAlert){
                Ext4.Msg.alert("Success", "Your upload was successful!", function(){
                    window.location = btn.successURL || LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: this.store.schemaName, 'query.queryName': this.store.queryName})
                }, this);
            }
        }

        function onError(store, msg, error){
            this.mun(this.store, onSuccess);
            btn.setDisabled(false);
        }

        this.mon(this.store, 'write', onSuccess, this, {single: true});
        this.mon(this.store, 'exception', onError, this, {single: true});
        this.store.sync();
    }
});


Ext4.define('LABKEY.ext4.FormPanelWin', {
    extend: 'Ext.Window',
    alias: 'widget.labkey-formpanelwin',
    initComponent: function(){
        Ext4.apply(this, {
            closeAction:'hide',
            title: 'Upload Data',
            width: 730,
            modal: true,
            items: [{
                xtype: 'labkey-formpanel',
                store: this.store,
                bubbleEvents: ['uploadexception', 'uploadcomplete'],
                itemId: 'theForm',
                title: null,
                buttons: null
            }],
            buttons: [{
                text: 'Upload'
                ,width: 50
                ,handler: function(){
                    var form = this.down('form');
                    form.formSubmit.call(form);
                }
                ,scope: this
                ,formBind: true
            },{
                text: 'Close'
                ,width: 50
                ,scope: this
                ,handler: function(btn){
                    this.hide();
                }
            }]
        });

        this.callParent();

        this.addEvents('uploadexception', 'uploadcomplete');
    }
});

LABKEY.ext4.FORMBUTTONS = {
    /**
     *
     * @param name
     * @param config
     */
    getButton: function(name, config){
        return LABKEY.ext4.FORMBUTTONS[name] ? LABKEY.ext4.FORMBUTTONS[name](config) : null;
    },

    //TODO: make these private?

    /**
     *
     * @cfg supressSuccessAlert
     * @cfg successURL
     */
    SUBMIT: function(config){
        return Ext4.Object.merge({
            text: 'Submit',
            formBind: true,
            successURL: LABKEY.ActionURL.getParameter('srcURL'),
            handler: function(btn){
                var panel = btn.up('form');
                panel.doSubmit(btn);
            }
        }, config);
    },
    NEXTRECORD: function(){
        return {
            text: 'Next Record',
            scope: this,
            handler: function(btn, key){
                var panel = btn.up('form');
                var rec = panel.getForm().getRecord();
                if(rec){
                    var idx = panel.store.indexOf(rec);
                    idx = (idx+1) % panel.store.getCount();
                    panel.bindRecord(panel.store.getAt(idx));
                }
            }
        }
    },
    PREVIOUSRECORD: function(){
        return {
            text: 'Previous Record',
            handler: function(btn, key){
                var panel = btn.up('form');
                var rec = panel.getForm().getRecord();
                if(rec){
                    var idx = panel.store.indexOf(rec);
                    if(idx==0)
                        idx = panel.store.getCount();

                    idx = (idx-1) % panel.store.getCount();
                    panel.bindRecord(panel.store.getAt(idx));
                }
            }
        }
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

/**
 * Params and default values:
 * disableUnlessBound: false
 * autoCreateRecordOnChange: true
 * autoBindFirstRecord: false
 * createRecordOnLoad: false
 * boundRecord
 */
Ext4.define('LABKEY.ext4.DatabindPlugin', {
    extend: 'Ext.AbstractPlugin',
    pluginId: 'labkey-databind',
    mixins: {
        observable: 'Ext.util.Observable'
    },
    init: function(panel){
        this.panel = panel;

        Ext4.apply(panel, {
            bindRecord: function(rec){
                var plugin = this.getPlugin('labkey-databind');
                plugin.bindRecord.call(plugin, rec);
            },
            unbindRecord: function(){
                var plugin = this.getPlugin('labkey-databind');
                plugin.unbindRecord.call(plugin);
            }
        });

        //set defaults
        panel.bindConfig = panel.bindConfig || {};
        panel.bindConfig = Ext4.Object.merge({
            disableUnlessBound: false,
            autoCreateRecordOnChange: true,
            autoBindFirstRecord: false,
            createRecordOnLoad: false
        }, panel.bindConfig);

        panel.addEvents('recordchange', 'fieldvaluechange');

        this.configureStore(panel.store);
        this.addFieldListeners();

        //we queue changes from all fields into a single event using buffer
        //this way batch updates of the form only trigger one record update/validation
        this.mon(panel, 'fieldvaluechange', this.updateRecord, this, {buffer: 50, delay: 10});
        this.mon(panel, 'add', function(o, c, idx){
            var findMatchingField = function(f) {
                if (f.isFormField) {
                    if (f.dataIndex) {
                        this.addFieldListener(c);
                    } else if (f.isComposite) {
                        f.items.each(findMatchingField, this);
                    }
                }
            };
            findMatchingField.call(this, c);
        }, this);

        if(panel.boundRecord)
            panel.bindRecord(panel.boundRecord);

        this.callParent(arguments);
    },

    configureStore: function(store){
        store = Ext4.StoreMgr.lookup(store);

        if(!store)
            return;

        this.mon(store, 'load', function(store, records, success, operation, options){
            // Can only contain one row of data.
            if (records.length == 0){
                if(this.panel.bindConfig.createRecordOnLoad){
                    var values = this.getForm.getFieldValues();
                    var record = this.panel.store.model.create();
                    record.set(values); //otherwise record will not be dirty
                    record.phantom = true;
                    this.store.add(record);
                    this.bindRecord(record);
                }
            }
            else {
                if(this.panel.bindConfig.autoBindFirstRecord){
                    this.bindRecord(records[0]);
                }
            }
        }, this);

        this.mon(store, 'remove', this.onRecordRemove, this);
        this.mon(this.panel.store, 'update', this.onRecordUpdate, this);
    },

    onRecordRemove: function(store, rec, idx){
        var boundRecord = this.panel.getForm().getRecord();
        if(boundRecord && rec == boundRecord){
            this.unbindRecord();
        }
    },

    onRecordUpdate: function(store, record, operation){
        var form = this.panel.getForm();
        if(form.getRecord() && record == form.getRecord()){
            form.suspendEvents();
            this.setValues(record.data);
            form.resumeEvents();
        }
    },

    bindRecord: function(record){
        var form = this.panel.getForm();

        if(form.getRecord())
            this.unbindRecord();

        form.suspendEvents();
        form.loadRecord(record);
        form.resumeEvents();
        form.isValid();
    },

    unbindRecord: function(){
        var form = this.panel.getForm();

        if(form.getRecord()){
            form.updateRecord(form.getRecord());
        }

        form._record = null;
        form.reset();
    },

    updateRecord: function(){
        var form = this.panel.getForm();
        //TODO: combos and other multi-valued fields represent data differently in the store vs the field.
        // need to reconcile here.  perhaps override getFieldValues()?
        if(form.getRecord()){
            form.updateRecord(form.getRecord());
        }
        else if (this.panel.bindConfig.autoCreateRecordOnChange){
            var values = form.getFieldValues();
            var record = this.panel.store.model.create();
            record.set(values); //otherwise record will not be dirty
            record.phantom = true;
            this.panel.store.add(record);
            this.bindRecord(record);
        }
    },

    addFieldListeners: function(){
        this.panel.getForm().getFields().each(this.addFieldListener, this);
    },

    addFieldListener: function(f){
        if(f.hasDatabindListener){
            console.log('field already has listener');
            return;
        }

        this.mon(f, 'check', this.onFieldChange, this);
        this.mon(f, 'change', this.onFieldChange, this);

        var form = f.up('form');
        if(form.getRecord() && this.panel.bindConfig.disableUnlessBound && !this.panel.bindConfig.autoCreateRecordOnChange)
            f.setDisabled(true);

        //TODO: in Ext4.1, we might be able to user override() and callOveridden()
        f.oldGetErrors = f.getErrors;
        f.getErrors = function(value){
            var errors = this.oldGetErrors(value);
            var record = this.up('form').getForm().getRecord();

            if(record){
                record.validate().each(function(e){
                    if(e.field == this.name)
                        errors.push(e.message);
                }, this);

                if(record.serverErrors && record.serverErrors[f.name]){
                    errors.push(record.serverErrors[f.name].join("<br>"));
                    delete record.serverErrors[f.name]; //only use it once
                }
            }

            errors = Ext4.Array.unique(errors);

            return errors;
        };
        f.hasDatabindListener = true;
    },

    //this is separated so that multiple fields in a single form are filtered into one event per panel
    onFieldChange: function(field){
        this.panel.fireEvent('fieldvaluechange', field);
    },

    //this is used instead of BasicForm's setValues in order to minimize event firing.  updating a field during editing has the
    //unfortunately consequence of moving the cursor to the end of the text, so we want to avoid this
    setValues: function(values) {
        var form = this.panel.getForm();

        function setVal(fieldId, val) {
            var field = form.findField(fieldId);
            if (field && field.getValue() !== val) {
                //TODO: combos and other multi-valued fields represent data differently in the store vs the field.  need to reconcile here
                field.setValue(val);
                if (form.trackResetOnLoad) {
                    field.resetOriginalValue();
                }
            }
        }

        if (Ext4.isArray(values)) {
            // array of objects
            Ext4.each(values, function(val) {
                setVal(val.id, val.value);
            });
        } else {
            // object hash
            Ext4.iterate(values, setVal);
        }
        return this;
    }
});