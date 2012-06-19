/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.namespace('LABKEY.ext', 'LABKEY.ext4');

/**
 * A collection of static helper methods designed to interface between LabKey's metadata and Ext.
 * @class It is heavily used internally with LABKEY's Client API, such as LABKEY.ext4.FormPanel or LABKEY.ext4.Store;
 * however these methods can be called directly.  LABKEY.ext4.Store also contains convenience methods that wrap some of Ext4Helper's methods.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeyExt">Tips for Using Ext to Build LabKey Views</a></li>
 *              </ul>
 *           </p>
 *
 */

LABKEY.ext.Ext4Helper = new function(){
    return {
        /**
         * Constructs an ext field component based on the supplied metadata.  Same as getFormEditorConfig, but actually constructs the editor.
         * The resulting editor is tailored for usage in a form, as opposed to a grid. Unlike getGridEditorConfig or getEditorConfig, if the metadata
         * contains a formEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
         *
         * @name getFormEditor
         * @function
         * @returns {object} Returns an Ext field component
         * @memberOf LABKEY.ext.Ext4Helper#
         *
         */
        getFormEditor: function(meta, config){
            var editorConfig = LABKEY.ext.Ext4Helper.getFormEditorConfig(meta, config);
            return Ext4.ComponentMgr.create(editorConfig);
        },

        /**
         * Constructs an ext field component based on the supplied metadata.  Same as getFormEditorConfig, but actually constructs the editor.
         * The resulting editor is tailored for usage in a grid, as opposed to a form. Unlike getFormEditorConfig or getEditorConfig, if the metadata
         * contains a gridEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
         *
         * @name getGridEditor
         * @function
         * @returns {object} Returns an Ext field component
         * @memberOf LABKEY.ext.Ext4Helper#
         *
         */
        getGridEditor: function(meta, config){
            var editorConfig = LABKEY.ext.Ext4Helper.getGridEditorConfig(meta, config);
            return Ext4.ComponentMgr.create(editorConfig);
        },

        /**
         * Return an Ext config object to create an Ext field based on the supplied metadata.
         * The resulting config object is tailored for usage in a grid, as opposed to a form. Unlike getFormEditorConfig or getEditorConfig, if the metadata
         * contains a gridEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
         *
         * @name getGridEditorConfig
         * @function
         * @returns {object} Returns an Ext config object
         * @memberOf LABKEY.ext.Ext4Helper#
         *
         */
        getGridEditorConfig: function(meta, config){
            //this produces a generic editor
            var editor = LABKEY.ext.Ext4Helper.getDefaultEditorConfig(meta);

            //for multiline fields:
            if(editor.editable && meta.inputType == 'textarea'){
                editor = new LABKEY.ext.LongTextField({
                    columnName: editor.dataIndex
                });
            }

            //now we allow overrides of default behavior, in order of precedence
            if(meta.editorConfig)
                Ext4.Object.merge(editor, meta.editorConfig);

            //note: this will screw up cell editors
            delete editor.fieldLabel;

            if(meta.gridEditorConfig)
                Ext4.Object.merge(editor, meta.gridEditorConfig);
            if(config)
                Ext4.Object.merge(editor, config);

            return editor;
        },

        /**
         * Return an Ext config object to create an Ext field based on the supplied metadata.
         * The resulting config object is tailored for usage in a form, as opposed to a grid. Unlike getGridEditorConfig or getEditorConfig, if the metadata
         * contains a gridEditorConfig property, this config object will be applied to the resulting field.  See getDefaultEditorConfig for config options.
         *
         * @name getFormEditorConfig
         * @function
         * @returns {object} Returns an Ext config object
         * @memberOf LABKEY.ext.Ext4Helper#
         *
         */
        getFormEditorConfig: function(meta, config){
            var editor = LABKEY.ext.Ext4Helper.getDefaultEditorConfig(meta);

            //now we allow overrides of default behavior, in order of precedence
            if(meta.editorConfig)
                Ext4.Object.merge(editor, meta.editorConfig);
            if(meta.formEditorConfig)
                Ext4.Object.merge(editor, meta.formEditorConfig);
            if(config)
                Ext4.Object.merge(editor, config);

            return editor;
        },

        //this is designed to be called through either .getFormEditorConfig or .getGridEditorConfig
        /**
         * Uses the given meta-data to generate a field config object.
         *
         * This function accepts a mish-mash of config parameters to be easily adapted to
         * various different metadata formats.
         *
         * Note: you can provide any Ext config options using the editorConfig, formEditorConfig or gridEditorConfig objects
         * These config options can also be used to pass arbitrary config options used by your specific Ext component
         *
         * @param {string} [config.type] e.g. 'string','int','boolean','float', or 'date'. for consistency this will be translated into the property jsonType
         * @param {object} [config.editable]
         * @param {object} [config.required]
         * @param {string} [config.label] used to generate fieldLabel
         * @param {string} [config.name] used to generate fieldLabel (if header is null)
         * @param {string} [config.caption] used to generate fieldLabel (if label is null)
         * @param {integer} [config.cols] if input is a textarea, sets the width (style:width is better)
         * @param {integer} [config.rows] if input is a textarea, sets the height (style:height is better)
         * @param {string} [config.lookup.schemaName] the schema used for the lookup.  schemaName also supported
         * @param {string} [config.lookup.queryName] the query used for the lookup.  queryName also supported
         * @param {Array} [config.lookup.columns] The columns used by the lookup store.  If not set, the <code>[keyColumn, displayColumn]</code> will be used.
         * @param {string} [config.lookup.keyColumn]
         * @param {string} [config.lookup.displayColumn]
         * @param {string} [config.lookup.sort] The sort used by the lookup store.
         * @param {boolean} [config.lookups] use lookups=false to prevent creating default combobox for lookup columns
         * @param {object}  [config.editorConfig] is a standard Ext config object (although it can contain any properties) that will be merged with the computed field config
         *      e.g. editorConfig:{width:120, tpl:new Ext.Template(...), arbitraryOtherProperty: 'this will be applied to the editor'}
         *      this will be merged will all form or grid editors
         * @param {object}  [config.formEditorConfig] Similar to editorConfig; however, it will only be merged when getFormEditor() or getFormEditorConfig() are called.
         *      The intention is to provide a mechanism so the same metadata object can be used to generate editors in both a form or a grid (or other contexts).
         * @param {object}  [config.gridEditorConfig] similar to formEditorConfig; however, it will only be merged when getGridEditor() or getGridEditorConfig() are called.
         * @param {object}  [config.columnConfig] similar to formEditorConfig; however, it will only be merged when getColumnConfig() is getColumnsConfig() called.
         * @param {object} [config.lookup.store] advanced! Pass in your own custom store for a lookup field
         * @param {boolean} [config.lazyCreateStore] If false, the store will be created immediately.  If true, the store will be created when the component is created. (default true)
         * @param {boolean} [config.createIfDoesNotExist] If true, this field will be created in the store, even if it does not otherwise exist on the server. Can be used to force custom fields to appear in a grid or form or to pass additional information to the server at time of import
         * @param {function} [config.buildQtip] This function will be used to generate the qTip for the field when it appears in a grid instead of the default function.  It will be passed a single object as an argument.  This object has the following properties: qtip, data, cellMetaData, meta, record, store. Qtip is an array which will be merged to form the contents of the tooltip.  Your code should modify the array to alter the tooltip.  For example:
         * buildQtip: function(config){
         *      qtip.push('I have a tooltip!');
         *      qtip.push('This is my value: ' + config.value);
         * }
         * @param {function} [config.buildDisplayString] This function will be used to generate the display string for the field when it appears in a grid instead of the default function.  It will be passed the same argument as buildQtip()
         * @param {function} [config.buildUrl] This function will be used to generate the URL encapsulating the field
         * @param {string} [config.urlTarget] If the value is rendered in a LABKEY.ext4.EditorGridPanel (or any other component using this pathway), and it contains a URL, this will be used as the target of <a> tag.  For example, use _blank for a new window.
         * @param (boolean) [config.setValueOnLoad] If true, the store will attempt to set a value for this field on load.  This is determined by the defaultValue or getInitialValue function, if either is defined
         * @param {function} [config.getInitialValue] When a new record is added to this store, this function will be called on that field.  If setValueOnLoad is true, this will also occur on load.  It will be passed the record and metadata.  The advantage of using a function over defaultValue is that more complex and dynamic initial values can be created.  For example:
         *  //sets the value to the current date
         *  getInitialValue(val, rec, meta){
         *      return val || new Date()
         *  }
         * @param {boolean} [config.wordWrap] If true, when displayed in an Ext grid the contents of the cell will use word wrapping, as opposed to being forced to a single line
         *
         * Note: the follow Ext params are automatically defined based on the specified Labkey metadata property:
         * dataIndex -> name
         * editable -> userEditable && readOnly
         * header -> caption
         * xtype -> set within getDefaultEditorConfig() based on jsonType, unless otherwise provided

         *
         */
        getDefaultEditorConfig: function(meta){
            var field =
            {
                //added 'caption' for assay support
                fieldLabel: Ext4.util.Format.htmlEncode(meta.label || meta.caption || meta.caption || meta.header || meta.name),
                originalConfig: meta,
                //we assume the store's translateMeta() will handle this
                allowBlank: meta.allowBlank!==false,
                //disabled: meta.editable===false,
                name: meta.name,
                dataIndex: meta.dataIndex || meta.name,
                value: meta.value || meta.defaultValue,
                width: meta.width,
                height: meta.height,
                msgTarget: 'qtip',
                validateOnChange: true
            };

            var helpPopup = meta.helpPopup || [
                'Type: ' + (meta.friendlyType ? meta.friendlyType : ''),
                'Required: ' + !meta.allowBlank,
                'Description: ' + (meta.description || '')
            ];
            if(Ext4.isArray(helpPopup))
                helpPopup = helpPopup.join('<br>');
            field.helpPopup = helpPopup;

            if (meta.hidden)
            {
                field.xtype = 'hidden';
            }
            else if (meta.editable === false)
            {
                field.xtype = 'displayfield';
            }
            else if (meta.lookup && meta.lookup.public !== false && meta.lookups !== false)
            {
                var l = meta.lookup;

                //test whether the store has been created.  create if necessary
                if (Ext4.isObject(meta.store) && meta.store.events)
                    field.store = meta.store;
                else
    //                field.store = LABKEY.ext.Ext4Helper.getLookupStoreConfig(meta);
                    field.store = LABKEY.ext.Ext4Helper.getLookupStore(meta);

    //            if (field.store && meta.lazyCreateStore === false){
    //                field.store = LABKEY.ext.Ext4Helper.getLookupStore(field);
    //            }

                Ext4.apply(field, {
                    //this purpose of this is to allow other editors like multiselect, checkboxGroup, etc.
                    xtype: (meta.xtype || 'labkey-combo'),
                    forceSelection: true,
                    typeAhead: true,
                    queryMode: 'local',
                    displayField: l.displayColumn,
                    valueField: l.keyColumn,
                    //NOTE: supported for non-combo components
                    initialValue: field.value,
                    showValueInList: meta.showValueInList,
    //                listClass: 'labkey-grid-editor',
                    lookupNullCaption: meta.lookupNullCaption
                });
            }
            else
            {
                switch (meta.jsonType)
                {
                    case "boolean":
                        field.xtype = meta.xtype || 'checkbox';
                        break;
                    case "int":
                        field.xtype = meta.xtype || 'numberfield';
                        field.allowDecimals = false;
                        break;
                    case "float":
                        field.xtype = meta.xtype || 'numberfield';
                        field.allowDecimals = true;
                        break;
                    case "date":
                        field.xtype = meta.xtype || 'datefield';
                        field.format = meta.extFormat || Date.patterns.ISO8601Long;
                        field.altFormats = LABKEY.Utils.getDateAltFormats();
                        break;
                    case "string":
                        if (meta.inputType=='textarea')
                        {
                            field.xtype = meta.xtype || 'textarea';
                            field.width = meta.width;
                            field.height = meta.height;
                            if (!this._textMeasure)
                            {
                                this._textMeasure = {};
                                var ta = Ext4.DomHelper.append(document.body,{tag:'textarea', rows:10, cols:80, id:'_hiddenTextArea', style:{display:'none'}});
                                this._textMeasure.height = Math.ceil(Ext4.util.TextMetrics.measure(ta,"GgYyJjZ==").height * 1.2);
                                this._textMeasure.width  = Math.ceil(Ext4.util.TextMetrics.measure(ta,"ABCXYZ").width / 6.0);
                            }
                            if (meta.rows && !meta.height)
                            {
                                if (meta.rows == 1)
                                    field.height = undefined;
                                else
                                {
                                    // estimate at best!
                                    var textHeight =  this._textMeasure.height * meta.rows;
                                    if (textHeight)
                                        field.height = textHeight;
                                }
                            }
                            if (meta.cols && !meta.width)
                            {
                                var textWidth = this._textMeasure.width * meta.cols;
                                if (textWidth)
                                    field.width = textWidth;
                            }

                        }
                        else
                            field.xtype = meta.xtype || 'textfield';
                        break;
                    default:
                        field.xtype = meta.xtype || 'textfield';
                }
            }

            return field;
        },

        // private
        getLookupStore : function(storeId, c)
        {
            if (typeof(storeId) != 'string')
            {
                c = storeId;
                storeId = LABKEY.ext.Ext4Helper.getLookupStoreId(c);
            }

            // Check if store has already been created.
            if (Ext4.isObject(c.store) && c.store.events)
                return c.store;

            var store = Ext4.StoreMgr.lookup(storeId);
            if (!store)
            {
                var config = c.store || LABKEY.ext.Ext4Helper.getLookupStoreConfig(c);
                config.storeId = storeId;
                store = Ext4.create('LABKEY.ext4.Store', config);
            }
            return store;
        },

        // private
        getLookupStoreId : function (c)
        {
            if (c.store && c.store.storeId)
                return c.store.storeId;

            if (c.lookup)
                return [c.lookup.schemaName || c.lookup.schema , c.lookup.queryName || c.lookup.table, c.lookup.keyColumn, c.lookup.displayColumn].join('||');

            return c.name;
        },

        //private
        getLookupStoreConfig : function(c)
        {
            var l = c.lookup;

            // normalize lookup
            l.queryName = l.queryName || l.table;
            l.schemaName = l.schemaName || l.schema;

            if (l.schemaName == 'core' && l.queryName =='UsersData')
                l.queryName = 'Users';

            var config = {
                xtype: "labkey-store",
                storeId: LABKEY.ext.Ext4Helper.getLookupStoreId(c),
                containerFilter: 'CurrentOrParentAndWorkbooks',
                schemaName: l.schemaName,
                queryName: l.queryName,
                containerPath: l.container || l.containerPath || LABKEY.container.path,
                autoLoad: true
            };

            if (l.viewName)
                config.viewName = l.viewName;

            if (l.filterArray)
                config.filterArray = l.filterArray;

            if (l.columns)
                config.columns = l.columns;
            else
            {
                var columns = [];
                if (l.keyColumn)
                    columns.push(l.keyColumn);
                if (l.displayColumn && l.displayColumn != l.keyColumn)
                    columns.push(l.displayColumn);
                if (columns.length == 0){
                    columns = ['*'];
                }
                config.columns = columns;
            }

            if (l.sort)
                config.sort = l.sort;
            else if (l.sort !== false)
                config.sort = l.displayColumn;

            if (!c.required && c.includeNullRecord !== false)
            {
                config.nullRecord = c.nullRecord || {
                    displayColumn: l.displayColumn,
                    nullCaption: (l.displayColumn==l.keyColumn ? null : (c.lookupNullCaption!==undefined ? c.lookupNullCaption : '[none]'))
                };
            }

            return config;
        },

        //private
        getColumnsConfig: function(store, grid, config){
            config = config || {};

            var fields = store.getFields();
            var columns = store.getColumns();
            var cols = new Array();

            var col;
            fields.each(function(field, idx){
                var col;

                if(field.shownInGrid === false)
                    return;

                Ext4.each(columns, function(c){
                    if(c.dataIndex == field.dataIndex){
                        col = c;
                        return false;
                    }
                }, this);

                if(!col)
                    col = {dataIndex: field.dataIndex};

                cols.push(LABKEY.ext.Ext4Helper.getColumnConfig(store, col, config, grid));

            }, this);

            return cols;
        },

        //private
        getColumnConfig: function(store, col, config, grid){
            col = col || {};

            var meta = store.findFieldMetadata(col.dataIndex);
            col.customized = true;

            col.hidden = meta.hidden;
            col.format = meta.extFormat;


            //this.updatable can override col.editable
            col.editable = config.editable && col.editable && meta.userEditable;

    //        //will use custom renderer
    //        if(meta.lookup && meta.lookups!==false)
    //            delete col.xtype;

            if(col.editable && !col.editor)
                col.editor = LABKEY.ext.Ext4Helper.getGridEditorConfig(meta);

            col.renderer = LABKEY.ext.Ext4Helper.getDefaultRenderer(col, meta, grid);

            //HTML-encode the column header
            col.text = Ext4.util.Format.htmlEncode(meta.label || meta.name || col.header);

            if(meta.ignoreColWidths)
                delete col.width;

           //allow override of defaults
            if(meta.columnConfig)
                Ext4.Object.merge(col, meta.columnConfig);
            if(config && config[col.dataIndex])
                Ext4.Object.merge(col, config[col.dataIndex]);

            return col;

        },

        //private
        getDefaultRenderer : function(col, meta, grid) {
            return function(value, cellMetaData, record, rowIndex, colIndex, store)
            {
                var displayValue = value;
                var cellStyles = [];

                if(null === value || undefined === value || value.toString().length == 0)
                    return value;

                //format value into a string
                displayValue = LABKEY.ext.Ext4Helper.getDisplayString(value, meta, record, store);

                if(meta.buildDisplayString){
                    displayValue = meta.buildDisplayString({
                        displayValue: displayValue,
                        value: value,
                        col: col,
                        meta: meta,
                        cellMetaData: cellMetaData,
                        record: record,
                        store: store
                    });
                }

                displayValue = Ext4.util.Format.htmlEncode(displayValue);

                //if meta.file is true, add an <img> for the file icon
                if(meta.file){
                    displayValue = "<img src=\"" + LABKEY.Utils.getFileIconUrl(value) + "\" alt=\"icon\" title=\"Click to download file\"/>&nbsp;" + displayValue;
                    //since the icons are 16x16, cut the default padding down to just 1px
                    cellStyles.push('padding: 1px 1px 1px 1px');
                }

                //build the URL
                if(col.showLink !== false){
                    var url = LABKEY.ext.Ext4Helper.getColumnUrl(displayValue, value, col, meta, record);
                    if(url){
                        displayValue = "<a " + (meta.urlTarget ? "target=\""+meta.urlTarget+"\"" : "") + " href=\"" + url + "\">" + displayValue + "</a>";
                    }
                }

    //            //TODO: consider supporting other attributes like style, class, align, etc.
    //            //possibly allow a cellStyles object?
    //            Ext4.each(['style', 'className', 'align', 'rowspan', 'width'], function(attr){
    //
    //            }, this);

                if(meta.wordWrap){
                    cellStyles.push('white-space:normal !important');
                }

                if(record && record.errors && record.errors.length)
                    cellMetaData.css += ' x-grid3-cell-invalid';

                if(cellStyles.length){
                    cellMetaData.tdAttr = cellMetaData.tdAttr || '';
                    cellMetaData.tdAttr += ' style="'+(cellStyles.join(';'))+'"';
                }

                LABKEY.ext.Ext4Helper.buildQtip({
                    displayValue: displayValue,
                    value: value,
                    meta: meta,
                    col: col,
                    record: record,
                    store: store,
                    cellMetaData: cellMetaData
                });

                return displayValue;
            };
        },

        //private
        getDisplayString: function(value, meta, record, store){
            var displayType = Ext4.isObject(meta.type) ? meta.type.type : meta.type;
            var displayValue = value;
            var shouldCache;

            //NOTE: the labkey 9.1 API returns both the value of the field and the display value
            //the server is already doing the work, so we should rely on this
            //this does have a few problems:
            //if the displayValue equals the value, the API omits displayValue.  because we cant
            // count on the server returning the right value unless explicitly providing a displayValue,
            // we only attempt to use that
            if(record && record.raw && record.raw[meta.name]){
                if(Ext4.isDefined(record.raw[meta.name].displayValue))
                    return record.raw[meta.name].displayValue;
                //TODO: this needs testing before enabling.  would be nice if we could rely on this, but i dont think we will be able to (dates, for example)
                //perhaps only try this for lookups?
                //else if(Ext4.isDefined(record.raw[meta.name].value))
                //    return record.raw[meta.name].value;
            }

            //NOTE: this is substantially changed over LABKEY.ext.FormHelper
            if(meta.lookup && meta.lookup.public !== false && meta.lookups!==false){
                displayValue = LABKEY.ext.Ext4Helper.getLookupDisplayValue(meta, displayValue, record, store);
                meta.usingLookup = true;
                shouldCache = false;
                displayType = 'string';
            }

            if(meta.extFormatFn && Ext4.isFunction(meta.extFormatFn)){
                displayValue = meta.extFormatFn(displayValue);
            }
            else {
                if(!Ext4.isDefined(displayValue))
                    displayValue = '';

                switch (displayType){
                    case "date":
                        var date = new Date(displayValue);
                        //NOTE: java formats differ from ext
                        var format = meta.extFormat;
                        if(!format){
                            if (date.getHours() == 0 && date.getMinutes() == 0 && date.getSeconds() == 0)
                                format = "Y-m-d";
                            else
                                format = "Y-m-d H:i:s";
                        }
                        displayValue = date.format(format);
                        break;
                    case "int":
                        displayValue = (Ext4.util.Format.numberRenderer(this.format || '0'))(displayValue);
                        break;
                    case "boolean":
                        var t = this.trueText || 'true', f = this.falseText || 'false', u = this.undefinedText || ' ';
                        if(displayValue === undefined){
                            displayValue = u;
                        }
                        else if(!displayValue || displayValue === 'false'){
                            displayValue = f;
                        }
                        else {
                            displayValue = t;
                        }
                        break;
                    case "float":
                        displayValue = (Ext4.util.Format.numberRenderer(this.format || '0,000.00'))(displayValue);
                        break;
                    case "string":
                    default:
                        displayValue = displayValue.toString();
                }
            }

            //experimental.  cache the calculated value, so we dont need to recalculate each time.  this should get cleared by the store on update like any server-generated value
            if(shouldCache !== false){
                record.raw = record.raw || {};
                if(!record.raw[meta.name])
                    record.raw[meta.name] = {};
                record.raw[meta.name].displayValue = displayValue;
            }

            return displayValue;
        },

        //private
        getColumnUrl: function(displayValue, value, col, meta, record){
            //wrap in <a> if url is present in the record's original JSON
            var url;
            if(meta.buildUrl)
                url = meta.buildUrl({
                    displayValue: displayValue,
                    value: value,
                    col: col,
                    meta: meta,
                    record: record
                });
            else if(record.raw && record.raw[meta.name] && record.raw[meta.name].url)
                url = record.raw[meta.name].url;
            return Ext4.util.Format.htmlEncode(url);
        },

        //private
        buildQtip: function(config){
            var qtip = [];
            //NOTE: returned in the 9.1 API format
            if(config.record && config.record.raw && config.record.raw[config.meta.name] && config.record.raw[config.meta.name].mvValue){
                var mvValue = config.record.raw[config.meta.name].mvValue;

                //get corresponding message from qcInfo section of JSON and set up a qtip
                if(config.record.store && config.record.store.reader.rawData && config.record.store.reader.rawData.qcInfo && config.record.store.reader.rawData.qcInfo[mvValue])
                {
                    qtip.push(config.record.store.reader.rawData.qcInfo[mvValue]);
                    config.cellMetaData.css = "labkey-mv";
                }
                qtip.push(mvValue);
            }

            if(config.record.errors && config.record.getErrors().length){

                Ext4.each(config.record.getErrors(), function(e){
                    if(e.field==meta.name){
                        qtip.push((e.severity || 'ERROR') +': '+e.message);
                    }
                }, this);
            }

            //NOTE: the Ext3 API did this; however, i think a better solution is to support text wrapping in cells
    //        if(config.col.multiline || (undefined === config.col.multiline && config.col.scale > 255 && config.meta.jsonType === "string"))
    //        {
    //            //Ext3
    //            config.cellMetaData.tdAttr = "ext:qtip=\"" + Ext4.util.Format.htmlEncode(config.value || '') + "\"";
    //            //Ext4
    //            config.cellMetaData.tdAttr += " data-qtip=\"" + Ext4.util.Format.htmlEncode(config.value || '') + "\"";
    //        }

            if(config.meta.buildQtip){
                config.meta.buildQtip({
                    qtip: config.qtip,
                    value: config.value,
                    cellMetaData: config.cellMetaData,
                    meta: config.meta,
                    record: config.record
                });
            }

            if(qtip.length){
                //ext3
                config.cellMetaData.tdAttr = "ext:qtip=\"" + Ext4.util.Format.htmlEncode(qtip.join('<br>')) + "\"";
                //ext4
                config.cellMetaData.tdAttr += " data-qtip=\"" + Ext4.util.Format.htmlEncode(qtip.join('<br>')) + "\"";
            }
        },

        //private
        //NOTE: it would be far better if we did not need to pass the store.  this is done b/c we need to fire the 'datachanged' event
        //once the lookup store loads.  a better idea would be to force the store/grid to listen for event fired by the lookupStore or somehow get the
        //metadata to fire events itself
        getLookupDisplayValue : function(meta, data, record, store) {
            var lookupStore = LABKEY.ext.Ext4Helper.getLookupStore(meta);
            if(!lookupStore){
                return '';
            }

            meta.lookupStore = lookupStore;
            var lookupRecord;
            var recIdx = lookupStore.find(meta.lookup.keyColumn, data);
            if(recIdx != -1)
                lookupRecord = lookupStore.getAt(recIdx);

            if (lookupRecord)
                return lookupRecord.get(meta.lookup.displayColumn);
            else {
                //NOTE: shift this responsibility to the grid or other class consuming this
    //            //if store not loaded yet, retry rendering on store load
    //            if(store && !lookupStore.fields){
    //                this.lookupStoreLoadListeners = this.lookupStoreLoadListeners || [];
    //                if(Ext4.Array.indexOf(this.lookupStoreLoadListeners, lookupStore.storeId) == -1){
    //                    lookupStore.on('load', function(lookupStore){
    //                        this.lookupStoreLoadListeners.remove(lookupStore.storeId);
    //
    //                        //grid.getView().refresh();
    //                        store.fireEvent('datachanged', store);
    //
    //                    }, this, {single: true});
    //                    this.lookupStoreLoadListeners.push(lookupStore.storeId);
    //                }
    //            }
                if (data!==null){
                    return "[" + data + "]";
                }
                else {
                    return Ext4.isDefined(meta.lookupNullCaption) ? meta.lookupNullCaption : "[none]";
                }
            }
        },

        /**
         * Identify the proper name of a field using an input string such as an excel column label.  This helper will
         * perform a case-insensitive comparison of the field name, label, caption, shortCaption and aliases.
         *
         * @name resolveFieldNameFromLabel
         * @function
         * @param (string) fieldName The string to search
         * @param (array / Ext.util.MixedCollection) metadata The fields to search
         * @returns {string} Returns the normalized field name or null if not found
         * @memberOf LABKEY.ext.Ext4Helper#
         *
         */
        resolveFieldNameFromLabel: function(fieldName, meta){
            var fnMatch = [];
            var aliasMatch = [];
            if(meta.hasOwnProperty('each'))
                meta.each(testField, this);
            else
                Ext4.each(meta, testField, this);

            function testField(fieldMeta){
                if (LABKEY.Utils.caseInsensitiveEquals(fieldName, fieldMeta.name)
                    || LABKEY.Utils.caseInsensitiveEquals(fieldName, fieldMeta.caption)
                    || LABKEY.Utils.caseInsensitiveEquals(fieldName, fieldMeta.shortCaption)
                    || LABKEY.Utils.caseInsensitiveEquals(fieldName, fieldMeta.label)
                ){
                    fnMatch.push(fieldMeta.name);
                    return false;  //exit here because it should only match 1 name
                }

                if(fieldMeta.importAliases){
                    var aliases;
                    if(Ext4.isArray(fieldMeta.importAliases))
                        aliases = fieldMeta.importAliases;
                    else
                        aliases = fieldMeta.importAliases.split(',');

                    Ext4.each(aliases, function(alias){
                        if(LABKEY.Utils.caseInsensitiveEquals(fieldName, alias))
                            aliasMatch.push(fieldMeta.name);  //continue iterating over fields in case a fieldName matches
                    }, this);
                }
            }

            if(fnMatch.length==1)
                return fnMatch[0];
            else if (fnMatch.length > 1){
                //alert('Ambiguous Field Label: '+fieldName);
                return null;
            }
            else if (aliasMatch.length==1){
                return aliasMatch[0];
            }
            else {
                //alert('Unknown Field Label: '+fieldName);
                return null;
            }
        },

        //private
        findJsonType: function(fieldObj){
            var type = fieldObj.type || fieldObj.typeName;

            if (type=='DateTime')
                return 'date';
            else if (type=='Double')
                return 'float';
            else if (type=='Integer' || type=='int')
                return 'int';
            //if(type=='String')
            else
                return 'string';
        },

        /**
         * EXPERIMENTAL.  Provides a consistent implementation for determining whether a field should appear in a details view.
         * If any of the following are true, it will not appear: hidden, isHidden
         * If shownInDetailsView is defined, it will take priority
         *
         * @name shouldShowInDetailsView
         * @function
         * @param (object) metadata The field metadata object
         * @returns {boolean} Returns whether the field show appear in the default details view
         * @memberOf LABKEY.ext.Ext4Helper#
         *
         */
        shouldShowInDetailsView: function(meta){
            return Ext4.isDefined(meta.shownInDetailsView) ? meta.shownInDetailsView :
                (!meta.isHidden && !meta.hidden && meta.shownInDetailsView!==false);
        },

        /**
         * EXPERIMENTAL.  Provides a consistent implementation for determining whether a field should appear in an insert view.
         * If any of the following are false, it will not appear: userEditable and autoIncrement
         * If any of the follow are true, it will not appear: hidden, isHidden
         * If shownInInsertView is defined, this will take priority over all
         *
         * @name shouldShowInInsertView
         * @function
         * @param (object) metadata The field metadata object
         * @returns {boolean} Returns whether the field show appear in the default insert view
         * @memberOf LABKEY.ext.Ext4Helper#
         *
         */
        shouldShowInInsertView: function(meta){
            return Ext4.isDefined(meta.shownInInsertView) ?  meta.shownInInsertView :
                (!meta.isHidden && !meta.hidden && meta.userEditable!==false && !meta.autoIncrement);
        },

        /**
         * EXPERIMENTAL.  Provides a consistent implementation for determining whether a field should appear in an update view.
         * If any of the following are false, it will not appear: userEditable and autoIncrement
         * If any of the follow are true, it will not appear: hidden, isHidden, readOnly
         * If shownInUpdateView is defined, this will take priority over all
         *
         * @name shouldShowInUpdateView
         * @function
         * @param (object) metadata The field metadata object
         * @returns {boolean} Returns whether the field show appear
         * @memberOf LABKEY.ext.Ext4Helper#
         *
         */
        shouldShowInUpdateView: function(meta){
            return Ext4.isDefined(meta.shownInUpdateView) ? meta.shownInUpdateView :
                (!meta.isHidden && !meta.hidden && meta.userEditable!==false && !meta.autoIncrement && meta.readOnly!==false)
        },

        //private
        //a shortcut for LABKEY.ext.Ext4Helper.getLookupStore that doesnt require as complex a config object
        simpleLookupStore: function(c) {
            c.lookup = {
                containerPath: c.containerPath,
                schemaName: c.schemaName,
                queryName: c.queryName,
                viewName: c.viewName,
    //            sort: c.sort,
                displayColumn: c.displayColumn,
                keyColumn: c.keyColumn
            };

            return LABKEY.ext.Ext4Helper.getLookupStore(c);
        },

        /**
         * Experimental.  If a store has not yet loaded, the implicit model always has zero fields
         * @param store
         */
        hasStoreLoaded: function(store){
            return store.model &&
               store.model.prototype.fields &&
               store.model.prototype.fields.getCount() > 1 // TODO: why is there 1 field initially with Ext4.1.0?
        },

        /**
         * Experimental.  Returns the fields from the passed store
         * @param store
         * @returns {Ext.util.MixedCollection} The fields associated with this store
         */
        getStoreFields: function(store){
            return store.proxy.reader.model.prototype.fields;
        }
    }
};

