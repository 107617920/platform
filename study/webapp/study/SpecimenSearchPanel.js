/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext.SampleSearchPanel', {
    extend: 'Ext.form.Panel',
    LABEL_WIDTH: 150,
    MAX_COMBO_ITEMS: 200,
    HIDE_EXTRA_FIELDS_TEXT: 'Hide Additional Fields',
    SHOW_EXTRA_FIELDS_TEXT: 'Show Additional Fields',
    showExtraFields: false,
    initComponent: function(){
        Ext4.QuickTips.init();
        Ext4.apply(this, {
            border: false,
            bodyStyle: 'background-color: transparent;padding: 5px;',
            defaults: {
                bodyStyle: 'background-color: transparent;',
                border: false
            },
            //width: 700,
            items: [{
                xtype: 'radiogroup',
                width: 420,
                itemId: 'searchType',
                fieldLabel: 'Search Type',
                labelWidth: this.LABEL_WIDTH,
                labelStyle: 'font-weight: bold;',
                afterLabelTextTpl: '<a href="#" data-qtip="Vial group search returns a single row per subject, time point, and sample type.  These results may be easier to read and navigate, but lack vial-level detail"><span class="labkey-help-pop-up">?</span></a>',
                items: [{
                    boxLabel: 'Individual Vials',
                    inputValue: 'individual',
                    name: 'groupType',
                    checked: true
                },{
                    boxLabel: 'Grouped Vials',
                    inputValue: 'grouped',
                    name: 'groupType',
                    checked: false
                }],
                listeners: {
                    buffer: 50,
                    change: function(rg, r){
                        var form = rg.up('form');
                        var panel = form.down('#searchFields');

                        var guidOp = panel.down('#guidOpField');
                        if(guidOp){
                            var guidField = panel.down('#guidField');
                            guidOp.setVisible(r.groupType != 'grouped');
                            if(r.groupType == 'grouped'){
                                guidOp.setVisible(false);
                                guidOp.reset();
                                guidField.reset();
                            }
                        }

                        form.toggleExtraFields(false);
                        form.down('#searchToggle').setText(form.SHOW_EXTRA_FIELDS_TEXT);
                    }
                }
            },{
                itemId: 'searchFields',
                bodyStyle: 'background-color: transparent;',
                width: 400,
                defaults: {
                    labelWidth: this.LABEL_WIDTH,
                    width: 400
                },
                items: [{
                    border: false,
                    bodyStyle: 'background-color: transparent;',
                    html: 'Loading...'
                }]
            },{
                xtype: 'container',
                style: 'padding-top: 15px;',
                items: [{
                    layout: 'hbox',
                    bodyStyle: 'background-color: transparent;',
                    border: false,
                    items: [{
                        xtype: 'labkey-linkbutton',
                        style: 'margin-right: 5px;',
                        HIDE_EXTRA_FIELDS_TEXT: this.HIDE_EXTRA_FIELDS_TEXT,
                        SHOW_EXTRA_FIELDS_TEXT: this.SHOW_EXTRA_FIELDS_TEXT,
                        text: this.SHOW_EXTRA_FIELDS_TEXT,
                        border: false,
                        itemId: 'searchToggle',
                        linkCls: 'labkey-text-link',
                        scope: this,
                        handler: function(btn){
                            var form = btn.up('form')
                            if(btn.getText() == form.HIDE_EXTRA_FIELDS_TEXT)
                                btn.setText(form.SHOW_EXTRA_FIELDS_TEXT);
                            else
                                btn.setText(form.HIDE_EXTRA_FIELDS_TEXT);

                            form.toggleExtraFields(btn.getText() == form.HIDE_EXTRA_FIELDS_TEXT);
                        }
                    }]
                },{
                    xtype: 'container',
                    bodyStyle: 'background-color: transparent;',
                    itemId: 'extraFieldsPanel',
                    style: 'padding-top: 10px;',
                    border: false
                }]
            }],
            buttonAlign: 'left',
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                style: 'background-color: transparent;padding-top: 5px;',
                ui: 'footer',
                items: [{
                    text: 'Search',
                    handler: this.onSubmit
                }]
            }]
        });

        this.callParent(arguments);

        this.studyProps = LABKEY.getModuleContext('Study');
        this.preloadStores();
    },

    preloadStores: function(){
        var toCreate = this.getGroupSearchItems();
        this.pendingStores = 0;
        Ext4.each(toCreate, function(args){
            var store = this.createStore.apply(this, args);
            this.pendingStores++;
            this.mon(store, 'load', this.onStoreLoad, this);
        }, this);

        this.pendingStores++;
        this.simpleSpecimenStore = Ext4.create('LABKEY.ext4.Store', {
            schemaName: 'study',
            queryName: 'SimpleSpecimen',
            maxRows: 0,
            autoLoad: true,
            listeners: {
                scope: this,
                load: function(store){
                    this.onStoreLoad();
                }
            }
        });

        this.pendingStores++;
        this.specimenSummaryStore = Ext4.create('LABKEY.ext4.Store', {
            schemaName: 'study',
            queryName: 'SpecimenSummary',
            maxRows: 0,
            autoLoad: true,
            listeners: {
                scope: this,
                load: function(store){
                    this.onStoreLoad();
                }
            }
        });
    },

    onStoreLoad: function(store){
        this.pendingStores--;

        if(this.pendingStores == 0){
            var val = this.down('#searchType').getValue().groupType;
            var panel = this.down('#searchFields');
            panel.removeAll();
            if(val == 'grouped'){
                panel.add(this.getGroupedSearchCfg());
            }
            else {
                panel.add(this.getIndividualSearchCfg());
            }

            var target = this.down('#extraFieldsPanel');
            target.removeAll();
        }
    },

    getIndividualSearchCfg: function(){
        var cfg = [{
            xtype: 'labkey-operatorcombo',
            itemId: 'guidOpField',
            jsonType: 'string',
            mvEnabled: false,
            //emptyText: 'Any Global Unique ID',
            includeHasAnyValue: true,
            value: null,
            fieldLabel: 'Global Unique ID',
            listeners: {
                scope: this,
                change: function(field, val){
                    this.down('#guidField').setVisible(val);
                }
            }
        },{
            xtype: 'textfield',
            itemId: 'guidField',
            filterParam: 'GlobalUniqueId',
            fieldLabel: '&nbsp;',
            labelSeparator: '',
            hidden: true
        }].concat(this.getGroupedSearchCfg());

        cfg = cfg.concat()

        return cfg;
    },

    getGroupedSearchCfg: function(){
        var cfg = [];
        Ext4.each(this.getGroupSearchItems(), function(item){
            cfg.push(this.getComboCfg.apply(this, item));
        }, this);

        return cfg;
    },

    getGroupSearchItems: function(){
        return [
            [this.studyProps.ParticipantNounSingular, 'study', this.studyProps.ParticipantNounSingular, this.studyProps.ParticipantColumnName, this.studyProps.ParticipantColumnName, this.studyProps.ParticipantColumnName, 'Any ' + this.studyProps.ParticipantNounSingular, null],
            ['Visit', 'study', 'Visit', 'Visit/SequenceNumMin', 'Label', 'SequenceNumMin', 'Any Visit', null, 'DisplayOrder,Label'],
            ['Primary Type', 'study', 'SpecimenPrimaryType', 'PrimaryType/Description', 'Description', 'Description', 'Any Primary Type', null],
            ['Derivative Type', 'study', 'SpecimenDerivative', 'DerivativeType/Description', 'Description', 'Description', 'Any Derivative Type', null],
            ['Additive Type', 'study', 'SpecimenAdditive', 'AdditiveType/Description', 'Description', 'Description', 'Any Additive Type', null]
        ]
    },

    getComboCfg: function(label, schemaName, queryName, filterParam, displayColumn, valueColumn, defaultOptionText, defaultOptionValue, sort){
        var store = this.createStore.apply(this, arguments);
        if(store.getCount() != 0 && store.getCount() >= this.MAX_COMBO_ITEMS){
            return {
                xtype: 'textfield',
                itemId: queryName,
                fieldLabel: label,
                filterParam: displayColumn,
                //emptyText: defaultOptionText,
                value: defaultOptionValue
            }
        }
        else {
            return {
                xtype: 'checkcombo',
                //editable: false,
                itemId: queryName,
                queryMode: 'local',
                //multiSelect: true,
                fieldLabel: label,
                filterParam: filterParam,
                displayField: 'displayValue',
                valueField: valueColumn,
                //emptyText: defaultOptionText,
                value: defaultOptionValue,
                store: store,
                addAllSelector: true,
                expandToFitContent: true
            }
        }
    },

    createStore: function(label, schemaName, queryName, filterParam, displayColumn, valueColumn, defaultOptionText, defaultOptionValue, sort){
        //only create stores once
        var storeId = ['specimen-search', schemaName, queryName, displayColumn, valueColumn].join('||');
        var store = Ext4.StoreMgr.get(storeId);
        if(!store){
            var columns = displayColumn;
            if(valueColumn != displayColumn){
                columns += ','+valueColumn;
            }

            var storeCfg = {
                type: 'labkey-store',
                storeId: storeId,
                schemaName: schemaName,
                //queryName: queryName,
                sql: 'select distinct(' + displayColumn + ') as ' + displayColumn + (displayColumn == valueColumn ? '' : ', ' + valueColumn) + ' from ' + schemaName + '.' + queryName + ' WHERE ' + displayColumn + ' IS NOT NULL AND ' + displayColumn + ' != \'\'',
                columns: columns,
                sort: sort || displayColumn,
                autoLoad: true,
                maxRows: this.MAX_COMBO_ITEMS,
                metadata: {
                    displayValue: {
                        createIfDoesNotExist: true,
                        setValueOnLoad: true,
                        getInitialValue: function(val, rec, meta){
                            if(displayColumn == valueColumn)
                                return rec.get(displayColumn);
                            else {
                                return rec.get(displayColumn) + ' (' + rec.get(valueColumn) + ')';
                            }
                        }
                    }
                }
            };

            //special case participant
            if(LABKEY.demoMode && queryName == this.studyProps.ParticipantNounSingular){
                storeCfg.listeners = {
                    load: function(store){
                        store.each(function(rec){
                            rec.set(displayColumn, LABKEY.id(valueColumn))
                        }, this);
                    },
                    scope: this
                };
            }
            store = Ext4.create('LABKEY.ext4.Store', storeCfg);
        }

        return store;
    },

    onSubmit: function(btn){
        var form = btn.up('form')
        var panel = form.down('#searchFields');
        var vialSearch = form.down('#searchType').getValue().groupType != 'grouped';

        var dataRegionName = vialSearch ? "SpecimenDetail" : "SpecimenSummary";
        var params = {
            showVials: vialSearch
        };

        panel.items.each(function(item){
            var op, val;
            if(item.filterParam){
                //special case GUID:
                if(item.itemId == 'guidField'){
                    op = panel.down('#guidOpField').getValue();
                    val = item.getValue();
                }
                else {
                    op = 'eq';
                    val = item.getValue();
                }

                if(Ext4.isArray(val)){
                    if(val.length > 1)
                        op = 'in';

                    var optimized = form.optimizeFilter(op, val, item);
                    if(optimized){
                        op = optimized[0];
                        val = optimized[1];
                    }

                    val = val.join(';');
                }

                if (op){
                    var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);
                    if(!Ext4.isEmpty(val) || (filterType && !filterType.isDataValueRequired())){
                        params[dataRegionName + '.' + item.filterParam + '~' + op] = val;
                    }
                }
            }
        }, this);

        //then inspect the search panel
        if (form.down('#searchPanel'))
            Ext4.apply(params, form.down('#searchPanel').getFilterParams(dataRegionName));

        window.location = LABKEY.ActionURL.buildURL('study-samples', 'samples', null, params);
    },

    optimizeFilter: function(op, values, field){
        if(field && field.store){
            if(values.length > (field.store.getCount() / 2)){
                op = LABKEY.Filter.getFilterTypeForURLSuffix(op).getOpposite().getURLSuffix();

                var newValues = [];
                field.store.each(function(rec){
                    var v = rec.get(field.displayField)
                    if(values.indexOf(v) == -1){
                        newValues.push(v);
                    }
                }, this);
                values = newValues;
            }
        }
        values = Ext4.unique(values);
        return [op, values];
    },

    toggleExtraFields: function(show){
        var target = this.down('#extraFieldsPanel');
        if(!show){
            target.removeAll();

        }
        else {
            var searchType = this.down('#searchType').getValue().groupType;
            var store;
            if (searchType == 'grouped')
                store = this.specimenSummaryStore;
            else
                store = this.simpleSpecimenStore;

            var metadata = {};
            Ext4.each(this.getExistingFieldNames(), function(col){
                metadata[col] = {hidden: true}
            }, this);
            target.add({
                xtype: 'labkey-searchpanel',
                itemId: 'searchPanel',
                bodyStyle: 'background-color: transparent;padding: 0px;',
                metadata: metadata,
                store: store,
                buttons: [],
                border: false,
                allowSelectView: false
            });
            this.showExtraFields = !this.showExtraFields;
        }
    },

    getExistingFieldNames: function(){
        var fields = [];
        this.down('#searchFields').items.each(function(item){
            if(item.filterParam){
                var param = item.filterParam.split('/');
                fields.push(param[0]);
            }
        }, this);
        return fields;
    }
});
