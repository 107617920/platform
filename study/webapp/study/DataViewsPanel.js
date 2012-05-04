/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);
LABKEY.requiresScript("study/DataViewPropertiesPanel.js");

Ext4.define('LABKEY.ext4.DataViewsPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        // TODO : Make the following not required since it might not be its own webpart
        // REQUIRES:
        // pageId
        // index
        // webpartId

        Ext4.applyIf(config, {
            layout : 'border',
            minWidth: 625,
            frame  : false, border : false,
            allowCustomize : true
        });

        // The following default to type 'string'
        var fields = [
            {name : 'id'},
            {name : 'category'},
            {name : 'categoryDisplayOrder', type : 'int'},
            {name : 'created',              type : 'string'},
            {name : 'createdBy'},
            {name : 'createdByUserId',      type : 'int'},
            {name : 'authorUserId',
                convert : function(v, record){
                    if (record.raw.author)
                        return record.raw.author.userId;
                    else return 0;
                }
            },
            {name : 'authorDisplayName',
                convert : function(v, record){
                    if (record.raw.author)
                        return record.raw.author.displayName;
                    else return '';
                }
            },
            {name : 'container'},
            {name : 'dataType'},
            {name : 'editable',             type : 'boolean'},
            {name : 'editUrl'},
            {name : 'type'},
            {name : 'description'},
            {name : 'displayOrder',         type : 'int'},
            {name : 'shared',               type : 'boolean'},
            {name : 'visible',              type : 'boolean'},
            {name : 'icon'},
            {name : 'modified',             type : 'string'},
            {name : 'modifiedBy'},
            {name : 'refreshDate',          type : 'string'},
            {name : 'name'},
            {name : 'access'},
            {name : 'runUrl'},
            {name : 'detailsUrl'},
            {name : 'thumbnail'},
            {name : 'status'}
        ];

        // define Models
        Ext4.define('Dataset.Browser.View', {
            extend : 'Ext.data.Model',
            fields : fields
        });

        Ext4.define('Dataset.Browser.Category', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'created',      type : 'string'},
                {name : 'createdBy'                  },
                {name : 'displayOrder', type : 'int' },
                {name : 'label'                      },
                {name : 'modfied',      type : 'string'},
                {name : 'modifiedBy'                 },
                {name : 'rowid',        type : 'int' }
            ]
        });

        Ext4.define('LABKEY.data.User', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'userId',       type : 'int'},
                {name : 'displayName'               }
            ]
        });

        this.callParent([config]);

        if (this.isCustomizable())
            this.addEvents('enableCustomMode', 'disableCustomMode');
    },

    initComponent : function() {

        this.customMode = false;
        this.searchVal = "";
        this._height;

        this.items = [];
        this.editInfo = {};

        this.store = null;
        this.centerPanel = null;
        this.gridPanel = null;

        // primary display panels
        this.items = this.initializeBorderLayout();
        this.items.push(this.initCenterPanel());

        // secondary display panels
        this.customPanel = this.initCustomization();

        this.callParent();
    },

    initializeBorderLayout : function() {
        var regions = ['north']; // only need north at this time
        var items = [];

        for (var r=0; r < regions.length; r++) {
            this[regions[r]] = Ext4.create('Ext.panel.Panel', {
                layout : 'fit',
                region : regions[r],
                style : 'margin-bottom: 10px',
                hidden : true,
                preventHeader : true,
//                flex   : 1.2,
                border : false, frame : false
            });
            items.push(this[regions[r]]);
        }
        return items;
    },

    initializeViewStore : function(useGrouping) {

        if (this.store)
            return this.store;

        var config = {
            pageSize: 100,
            model   : 'Dataset.Browser.View',
            autoLoad: true,
            proxy   : {
                type   : 'ajax',
                url    : LABKEY.ActionURL.buildURL('study', 'browseData.api'),
                extraParams : {
                    // These parameters are required for specific webpart filtering
                    pageId      : this.pageId,
                    index       : this.index,
                    returnUrl   : this.returnUrl
                },
                reader : {
                    type : 'json',
                    root : 'data'
                }
            },
            listeners : {
                load : this.onViewLoad,
                scope: this
            },
            scope : this
        };

        if (useGrouping) {
            config["groupField"] = 'category';
        }

        this.store = Ext4.create('Ext.data.Store', config);
        return this.store;
    },

    initializeCategoriesStore : function(useGrouping) {

        var config = {
            pageSize: 100,
            model   : 'Dataset.Browser.Category',
            autoLoad: true,
            autoSync: false,
            proxy   : {
                type   : 'ajax',
                api    : {
                    create  : LABKEY.ActionURL.buildURL('study', 'saveCategories.api'),
                    read    : LABKEY.ActionURL.buildURL('study', 'getCategories.api'),
                    update  : LABKEY.ActionURL.buildURL('study', 'saveCategories.api'),
                    destroy : LABKEY.ActionURL.buildURL('study', 'deleteCategories.api')
                },
                extraParams : {
                    // These parameters are required for specific webpart filtering
                    pageId : this.pageId,
                    index  : this.index
                },
                reader : {
                    type : 'json',
                    root : 'categories'
                },
                writer: {
                    type : 'json',
                    root : 'categories',
                    allowSingle : false
                },
                listeners : {
                    exception : function(p, response, operations, eOpts)
                    {
                    }
                }
            },
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    s.sort('displayOrder', 'ASC');
                }
            }
        };

        if (useGrouping)
            config["groupField"] = 'category';

        return Ext4.create('Ext.data.Store', config);
    },

    initializeUserStore : function() {

        var config = {
            model   : 'LABKEY.data.User',
            autoLoad: true,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('user', 'getUsers.api'),          
                reader : {
                    type : 'json',
                    root : 'users'
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    },

    initCenterPanel : function() {

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            border   : false,
            frame    : false,
            layout   : 'fit'
//            height   : 575
        });

        this.centerPanel.on('render', this.configureGrid, this);

        var _panel = Ext4.create('Ext.panel.Panel', {
            border : false, frame : false,
            layout : 'fit',
            flex   : 4,
            region : 'center',
            items  : [this.centerPanel]
        });
        return _panel;
    },

    /**
     * Invoked once when the grid is initially setup
     */
    configureGrid : function() {

        var handler = function(json){
            this.centerPanel.getEl().unmask();

            if (json.webpart) {
                this._height = parseInt(json.webpart.height);
                this.setHeight(this._height);
            }
            this.editInfo = json.editInfo;
            this.initGrid(true, json.visibleColumns);
        };
        
        this.centerPanel.getEl().mask('Initializing...');
        this.getConfiguration(handler, this);
    },

    /**
     * Invoked each time the column model is modified from the customize view
     */
    updateConfiguration : function() {

        var handler = function(json){
            this.centerPanel.getEl().unmask();
            this.gridPanel.on('reconfigure', function() {
                if (this._height)
                    this.setHeight(this._height);
            }, this, {single: true});
            this.gridPanel.reconfigure(this.gridPanel.getStore(), this.initGridColumns(json.visibleColumns));
            this.store.load();
        };

        this.centerPanel.getEl().mask('Initializing...');
        this.getConfiguration(handler, this);
    },

    getConfiguration : function(handler, scope) {

        var extraParams = {
            // These parameters are required for specific webpart filtering
            includeData : false,
            pageId : this.pageId,
            index  : this.index
        };

        Ext4.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('study', 'browseData.api', null, extraParams),
            method : 'GET',
            success: function(response) {
                if (handler)
                {
                    var json = Ext4.decode(response.responseText);
                    handler.call(scope || this, json);
                }
            },
            failure : function() {
                Ext4.Msg.alert('Failure');
            },
            scope : this
        });
    },

    initGrid : function(useGrouping, visibleColumns) {

        /**
         * Enable Grouping by Category
         */
        var groupingFeature = Ext4.create('Ext4.grid.feature.Grouping', {
            groupHeaderTpl : '&nbsp;{name:htmlEncode}' // &nbsp; allows '+/-' to show up
        });

        /**
         * Tooltip Template
         */
        var tipTpl = new Ext4.XTemplate('<tpl>' +
                '<div class="data-views-tip-content">' +
                '<table cellpadding="20" cellspacing="100">' +
                '<tpl if="data.category != undefined && data.category.length">' +
                '<tr><td>Source:</td><td>{[fm.htmlEncode(values.data.category)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.createdBy != undefined && data.createdBy.length">' +
                '<tr><td>Created By:</td><td>{[fm.htmlEncode(values.data.createdBy)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.authorDisplayName != undefined && data.authorDisplayName.length">' +
                '<tr><td>Author:</td><td>{[fm.htmlEncode(values.data.authorDisplayName)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.type != undefined && data.type.length">' +
                '<tr><td>Type:</td><td>{[fm.htmlEncode(values.data.type)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.status != undefined && data.status.length">' +
                '<tr><td>Status:</td><td>{[fm.htmlEncode(values.data.status)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.refreshDate != undefined && data.refreshDate.length">' +
                '<tr><td valign="top">Data Cut Date:</td><td>{[fm.htmlEncode(values.data.refreshDate)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.description != undefined && data.description.length">' +
                '<tr><td valign="top">Description:</td><td>{[fm.htmlEncode(values.data.description)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.thumbnail != undefined && data.thumbnail.length">' +
                '</tpl>' +
                '</table>' +
                '<div class="thumbnail"><img src="{data.thumbnail}"/></div>' +
                '</div>' +
                '</tpl>').compile();

        this._tipID = Ext4.id();
        var _tipID = this._tipID;

        function getTipPanel()
        {
            var tipPanel = Ext4.create('Ext.panel.Panel', {
                id     : _tipID,
                layout : 'fit',
                border : false, frame : false,
                height : '100%',
                cls    : 'tip-panel',
                tpl    : tipTpl,
                renderTipRecord : function(rec){
                    tipPanel.update(rec);
                }
            });

            return tipPanel;
        }

        function initToolTip(view)
        {
            var _w = 500;
            var _h = 325;
            var _active;

            function renderToolTip(tip)
            {
                if (_active)
                    tip.setTitle(_active.get('name'));
                var content = Ext4.getCmp(_tipID);
                if (content)
                {
                    content.renderTipRecord(_active);
                }
            }

            function loadRecord(t) {
                var r = view.getRecord(t.triggerElement.parentNode);
                if (r) _active = r;
                else {
                    /* This usually occurs when mousing over grouping headers */
                    _active = null;
                    return false;
                }
                return true;
            }

            view.tip = Ext4.create('Ext.tip.ToolTip', {
                target   : view.el,
                delegate : '.x4-name-column-cell',
                trackMouse: false,
                width    : _w,
                height   : _h,
                html     : null,
                autoHide : true,
                anchorToTarget : true,
                anchorOffset : 100,
                showDelay: 1000,
                cls      : 'data-views-tip-panel',
                defaults : { border: false, frame: false},
                items    : [getTipPanel()],
                listeners: {
                    // Change content dynamically depending on which element triggered the show.
                    beforeshow: function(tip) {
                        var loaded = loadRecord(tip);
                        renderToolTip(tip);
                        return loaded; // return false to not show tip
                    },
                    scope : this
                },
                scope : this
            });
        }

        this.gridPanel = Ext4.create('Ext.grid.Panel', {
            id       : 'data-browser-grid-' + this.webpartId,
            store    : this.initializeViewStore(useGrouping),
            tbar     : this.initSearch(),
            border   : false, frame: false,
            layout   : 'fit',
            cls      : 'iScroll', // webkit custom scroll bars
            scroll   : 'vertical',
            columns  : this.initGridColumns(visibleColumns),
            multiSelect: true,
            region   : 'center',
            viewConfig : {
                stripeRows : true,
                listeners  : {
                    render : initToolTip,
                    scope  : this
                },
                emptyText : '0 Matching Results'
            },
            selType   : 'rowmodelfixed',
            features  : [groupingFeature],
            listeners : {
                itemclick : function(view, record, item, index, e, opts) {
                    // TODO: Need a better way to determine the clicked item
                    var cls = e.target.className;
                    if (cls.search(/edit-views-link/i) >= 0)
                        this.onEditClick(view, record);
                },
                afterlayout : function(p) {
                    p.setLoading(false);
                    /* Apply selector for tests */
                    var el = Ext4.query("*[class=x4-grid-table x4-grid-table-resizer]");
                    if (el && el.length == 1) {
                        el = el[0];
                        if (el && el.setAttribute) {
                            el.setAttribute('name', 'data-browser-table');
                        }
                    }
                },
                scope : this
            },
            scope     : this
        });

        this.gridPanel.getStore().on('groupchange', this.onGroupChange, this);

        this.centerPanel.add(this.gridPanel);
    },

    initGridColumns : function(visibleColumns) {

        var detailsTpl =
                '<tpl if="detailsUrl">' +
                    '<a data-qtip="Click to navigate to the Detail View" href="{detailsUrl}">' +
                        '<img data-qtip="Details" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/details.png" alt="Details...">' +
                   '</a>' +
                '</tpl>';

        var nameTpl =
                '<div height="16px" width="100%">' +
                    '<tpl if="icon != undefined && icon != \'\'">' +
                        '<img height="16px" width="16px" src="{icon}" alt="{type}" style="vertical-align: bottom;">' +
                    '</tpl>' +
                    '<a href="{runUrl}"> {name:htmlEncode}</a>' +
                '</div>';

        var _columns = [];

        _columns.push({
            id       : 'edit-column-' + this.webpartId,
            text     : '',
            width    : 40,
            sortable : false,
            renderer : function(view, meta, rec, idx, colIdx, store) {
                if (!this._inCustomMode())
                    meta.style = 'display: none;';  // what a nightmare

                // an item need an edit info interface to be editable
                if (!this.editInfo[rec.data.dataType]) {
                    return '<span height="16px" class="edit-link-cls-' + this.webpartId + '"></span>';
                }
                return '<span height="16px" class="edit-link-cls-' + this.webpartId + ' edit-views-link"></span>';
            },
            hidden   : true,
            scope    : this
        },{
            xtype    : 'templatecolumn',
            text     : 'Name',
            flex     : 1,
            sortable : true,
            dataIndex: 'name',
            tdCls    : 'x4-name-column-cell',
            tpl      :  nameTpl,
            scope    : this
        },{
            id       : 'category-column-' + this.webpartId,
            text     : 'Category',
            flex     : 1,
            sortable : true,
            dataIndex: 'category',
            renderer : Ext.util.Format.htmlEncode,
            hidden   : true
        });

        if (visibleColumns['Type'] && visibleColumns['Type'].checked)
        {
            _columns.push({
                xtype    : 'templatecolumn',
                text     : 'Type',
                width    : 100,
                sortable : true,
                dataIndex: 'type',
                tdCls    : 'type-column',
                tpl      : '<tpl>{type}</tpl>',
                scope    : this
            });
        }

        if (visibleColumns['Details'] && visibleColumns['Details'].checked)
        {
            _columns.push({
                xtype    : 'templatecolumn',
                text     : 'Details',
                width    : 100,
                sortable : true,
                dataIndex: 'detailsUrl',
                tdCls    : 'type-column',
                tpl      : detailsTpl,
                scope    : this
            });
        }

        if(visibleColumns['Data Cut Date'] && visibleColumns['Data Cut Date'].checked){
             _columns.push({
                 text     : 'Data Cut Date',
                 width    : 100,
                 sortable : true,
                 dataIndex: 'refreshDate',
                 scope    : this
             });
        }

        if (visibleColumns['Status'] && visibleColumns['Status'].checked)
        {
            var statusTpl = '<tpl if="status == \'Draft\'">' +
                    '<img data-qtip="Draft" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_draft.png" alt="Draft">' +
                    '</tpl>' +
                    '<tpl if="status == \'Final\'">' +
                    '<img data-qtip="Final" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_final.png" alt="Final">' +
                    '</tpl>' +
                    '<tpl if="status == \'Locked\'">' +
                    '<img data-qtip="Locked" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_locked.png" alt="Locked">' +
                    '</tpl>' +
                    '<tpl if="status == \'Unlocked\'">' +
                    '<img data-qtip="Unlocked" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_unlocked.png" alt="Unlocked">' +
                    '</tpl>';

            _columns.push({
                xtype    : 'templatecolumn',
                text     : 'Status',
                width    : 75,
                sortable : true,
                tdCls    : 'type-column',
                dataIndex: 'status',
                tpl      : statusTpl
            });
        }

        if (visibleColumns['Modified'] && visibleColumns['Modified'].checked)
        {
            _columns.push({
                text     : 'Modified',
                width    : 100,
                sortable : true,
                dataIndex: 'modified'
            });
        }

        if (visibleColumns['Author'] && visibleColumns['Author'].checked)
        {
            _columns.push({
                text     : 'Author',
                width    : 150,
                sortable : false,
                dataIndex: 'authorDisplayName',
                scope    : this
            });
        }

        if (visibleColumns['Access'] && visibleColumns['Access'].checked)
        {
            _columns.push({
                header   : 'Access',
                width    : 100,
                sortable : false,
                dataIndex: 'access'
            });
        }

        return _columns;
    },

    initSearch : function() {

        function filterSearch() {
            this.searchVal = searchField.getValue();
            this.hiddenFilter();
        }

        var filterTask = new Ext.util.DelayedTask(filterSearch, this);

        var searchField = Ext4.create('Ext.form.field.Text', {
            emptyText       : 'name, category, etc.',
            enableKeyEvents : true,
            cls             : 'dataset-search',
            size            : 57,
            height          : 25,
            border: false, frame : false,
            listeners       : {
                change       : function(cmp, e){
                    filterTask.delay(350);
                }
            }
        });

        // hahaha
        this.mineField = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel        : '<span data-qtip="Check to show only views that have either been created by me or that list me as the author.">&nbsp;Mine</span>',
            boxLabelAlign   : 'before',
            border          : false, frame : false,
            listeners       : {
                change : function(cmp, checked){
                    this.searchMine = checked;
                    filterTask.delay(100);
                },
                scope : this
            },
            scope : this
        });

        var tbar = {
            height  : 30,
            items   : [{
                xtype   : 'panel',
                border  : false,
                layout  : {type:'table'},
                items   : [searchField, {
                    xtype   : 'box',
                    border  : 0,
                    autoEl  : {
                        tag : 'img',
                        style : {
                            position : 'relative',
                            left     : '-20px'
                        },
                        src : LABKEY.ActionURL.getContextPath() + '/_images/search.png'
                    }}
                ]},
                '->', this.mineField
            ]
        };

        return tbar;
    },

    initCustomization : function() {

        var customPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'fit',
            border : false, frame : false,
            flex   : 1
        });

        this.north.setHeight(185);
        this.north.add(customPanel);

        this.on('enableCustomMode',  this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);

        return customPanel;
    },

    onViewLoad : function(s, recs, success, operation, ops) {
        this.hiddenFilter();
        // sorting 'groups' as opposed to the rows in a group on a grouping feature is not immediately present
        // TODO: Possible option (from Ext 3.4) : http://www.sencha.com/forum/showthread.php?109047-Grid-grouping-and-sorting&p=515917#post515917
//        for (var i = 0; i < s.groupers.items.length; i++) {
//            s.groupers.items[i].updateSortFunction(function(rec1, rec2){
//                var cdo1 = rec1.data.categoryDisplayOrder,
//                    cdo2 = rec2.data.categoryDisplayOrder;
//
//                if (cdo1 < cdo2)
//                    return -1;
//                else if (cdo1 == cdo2)
//                    return 0;
//                return 1;
//            });
//        }
    },

    onGroupChange : function(store, groupers) {

        // 13878: Reverting to default in data browser -- make sure category column is visible if it is not being grouped by
        if (groupers.items && groupers.items.length == 1) {

            var catColumn = Ext4.getCmp('category-column-' + this.webpartId);

            if (catColumn) {
                if (groupers.items[0].property == catColumn.dataIndex) {
                    catColumn.hide();
                }
                else {
                    if (!catColumn.isVisible())
                        catColumn.show();
                }
            }

        }
    },

    /**
     * Aggregates the filters applied by search and by custom mode.
     */
    hiddenFilter : function() {

        this.store.clearFilter();
        var _custom = this._inCustomMode();
        this.store.sort([
            {
                property : 'name',
                direction: 'ASC'
            }
        ]);
        this.store.filterBy(function(rec, id){

            var answer = true;
            if (rec.data && this.searchVal && this.searchVal != "")
            {
                var t = new RegExp(Ext4.escapeRe(this.searchVal), 'i');
                var s = '';
                if (rec.data.name)
                    s += rec.data.name;
                if (rec.data.category)
                    s += rec.data.category;
                if (rec.data.type)
                    s += rec.data.type;
                if (rec.data.modified)
                    s += rec.data.modified;
                if (rec.data.authorDisplayName)
                    s += rec.data.authorDisplayName;
                if (rec.data.status)
                    s += rec.data.status;
                answer = t.test(s);
            }

            // the show mine checkbox will match if the current user is either the author or the creator
            if (rec.data && answer && this.searchMine)
            {
                if ((rec.data.authorUserId != LABKEY.user.id) && (rec.data.createdByUserId != LABKEY.user.id))
                    return false;
            }

            // custom mode will show hidden
            if (_custom)
                return answer;

            // otherwise never show hidden records
            if (!rec.data.visible)
                return false;

            return answer;
        }, this);
    },

    isCustomizable : function() {
        return this.allowCustomize;
    },

    // private
    _inCustomMode : function() {
        return this.customMode;
    },

    /**
     * Takes the panel into/outof customize mode. Customize mode allows users to view edit links,
     * adminstrate view categories and determine what data types should be shown.
     */
    customize : function() {

        if (!this.isCustomizable())
            return false;

        this.fireEvent((this._inCustomMode() ? 'disableCustomMode' : 'enableCustomMode'), this);
    },

    onEnableCustomMode : function() {

        var handler = function(json) {
            this.north.getEl().unmask();
            this._displayCustomMode(json);
        };

        this.customMode = true;
        this.north.show(null, function(){
            this.north.getEl().mask('Loading Customize...');
        }, this);
        this.getConfiguration(handler, this);
    },

    onDisableCustomMode : function() {
        if (this.customPanel && this.customPanel.isVisible())
        {
            this.north.hide();
        }

        this.customMode = false;

        this.hiddenFilter();

        // hide edit column
        var editColumn = Ext4.getCmp('edit-column-' + this.webpartId);
        if (editColumn)
        {
            editColumn.hide();
        }
    },

    // private
    _displayCustomMode : function(data) {

        // reset the update config flag, we only want to re-fetch the configuration if the visible columns change
        this.updateConfig = false;

        // panel might already exist
        if (this.customPanel && this.customPanel.items.length > 0)
        {
            this.hiddenFilter();

            // show edit column
            var editColumn = Ext4.getCmp('edit-column-' + this.webpartId);
            if (editColumn)
            {
                editColumn.show();
            }

            this.north.getEl().unmask();
            return;
        }

        var cbItems = [],
                cbColumns = [],
                sizeItems = [];

        var heights = {
            450 : 'small',
            700 : 'medium',
            1000 : 'large'
        };

        if (data.types)
        {
            for (var type in data.types) {
                if(data.types.hasOwnProperty(type))
                {
                    cbItems.push({boxLabel : type, name : type, checked : data.types[type], width: 150, uncheckedValue : '0'});
                }
            }
        }

        if (data.visibleColumns)
        {
            for (var col in data.visibleColumns) {
                var prop = data.visibleColumns[col];
                cbColumns.push({boxLabel : col, name : col, checked : prop.checked, uncheckedValue : '0', width: 150, handler : function(){this.updateConfig = true;}, scope : this});
            }
        }

        for (var h in heights) {
            if (heights.hasOwnProperty(h)) {
                sizeItems.push({boxLabel : heights[h], name : 'webpart.height', inputValue : h, checked : false, handler : function(grp){
                    // this is called for each 'change' -- only bother with true case
                    if (grp.getValue()) {
                        this.updateConfig = true;
                        this._height = parseInt(grp.inputValue); // WATCH OUT : this MUST be an integer.
                    }
                }, scope : this});
                if (this._height == h)
                    sizeItems[sizeItems.length-1].checked = true;
            }
        }

        var namePanel = Ext4.create('Ext.form.Panel', {
            border : false,
            fieldDefaults  :{
                labelAlign : 'top',
                labelWidth : 130,
                labelSeparator : ''
            },
            items : [{
                xtype      : 'textfield',
                fieldLabel : 'Name',
                name       : 'webpart.title',
                allowBlank : false,
                width      : 225,
                value      : data.webpart.title ? data.webpart.title : data.webpart.name
            },{
                xtype      : 'radiogroup',
                fieldLabel : 'Display',
                columns    : 3,
                items      : sizeItems
            }]
        });
        
        var formPanel = Ext4.create('Ext.form.Panel',{
            border : false,
            flex   : 1,
            layout : 'hbox',
            fieldDefaults  :{
                labelAlign : 'top',
                labelWidth : 130,
                labelSeparator : ''
            },
            items : [namePanel,
            {
                xtype      : 'checkboxgroup',
                fieldLabel : 'Types (All Users)',
                colspan    : 1,
                columns    : 1,
                flex       : 1,
                style      : 'padding-left: 25px;',
                items      : cbItems
            },{
                xtype      : 'checkboxgroup',
                fieldLabel : 'Columns (All Users)',
                columns    : 2,
                flex       : 1.2,
                items      : cbColumns
            },{
                xtype   : 'hidden',
                name    : 'webPartId',
                value   : this.webpartId
            }],
            scope : this
        });

        var panel = Ext4.create('Ext.panel.Panel',{
            bodyPadding : 10,
            layout : {
                type:'vbox',
                align:'stretch'
            },
            items : [formPanel,{
                xtype   : 'panel',
                border  : false,
                items: [{
                    xtype   : 'button',
                    text    : 'Manage Categories',
                    handler : this.onManageCategories,
                    scope   : this
                }
            ]}],
            buttons : [{
                text    : 'Cancel',
                handler : function() {
                    this.fireEvent('disableCustomMode');
                },
                scope   : this
            },{
                text     : 'Save',
                formBind : true,
                handler  : function() {
                    var form = formPanel.getForm(); // this.up('form')
                    if (form.isValid())
                    {
                        this.north.getEl().mask('Saving...');
                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, form.getValues()),
                            method : 'POST',
                            success : function() {
                                this.north.getEl().unmask();
                                this.north.hide();

                                // Modify Title
                                var titleEl = Ext4.query('span[class=labkey-wp-title-text]:first', 'webpart_' + this.webpartId);
                                if (titleEl && (titleEl.length >= 1))
                                {
                                    titleEl[0].innerHTML = LABKEY.Utils.encodeHtml(form.findField('webpart.title').getValue());
                                }
                                // else it will get displayed on refresh

                                this.fireEvent('disableCustomMode');

                                if (this.updateConfig)
                                    this.updateConfiguration();
                                else
                                    this.store.load();
                            },
                            failure : function() {
                                Ext4.Msg.alert('Failure');
                            },
                            scope : this
                        });
                    }
                },
                scope : this
            }],
            scope : this
        });

        this.hiddenFilter();

        // show edit column
        var editColumn = Ext4.getCmp('edit-column-' + this.webpartId);
        if (editColumn)
        {
            editColumn.show();
        }

        this.customPanel.add(panel);
        this.north.getEl().unmask();
    },

    onEditClick : function(view, record) {

        var tip = Ext.getCmp(this._tipID);
        if (tip)
            tip.hide();

        // grab the map of available fields from the edit info for the view type
        var editInfo = this.editInfo[record.data.dataType] || {};
        var formItems = [];

        /* Record 'id' is required*/
        var editable = true;
        if (record.data.id == undefined || record.data.id == "")
        {
            console.warn('ID is required');
            editable = false;
        }

        // hidden items
        formItems.push({
            xtype : 'hidden',
            style : 'display:none;',
            name  : 'id',
            value : record.data.id
        },{
            xtype : 'hidden',
            style : 'display:none;',
            name  : 'dataType',
            value : record.data.dataType
        });

        var viewForm = Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            record          : record,
            extraItems      : formItems,
            visibleFields   : {
                author  : editInfo['author'],
                status  : editInfo['status'],
                datacutdate : editInfo['refreshDate'],
                category    : editInfo['category'],
                description : editInfo['description'],
                type        : true,
                visible     : editInfo['visible'],
                created     : true,
                shared      : editInfo['shared'],
                modified    : true
            },
            buttons     : [{
                text : 'Save',
                formBind: true,
                handler : function(btn) {
                    var form = btn.up('form').getForm();
                    if (form.isValid())
                    {
                        Ext4.Ajax.request({
                            url     : LABKEY.ActionURL.buildURL('study', 'editView.api'),
                            method  : 'POST',
                            params  : form.getValues(),
                            success : function(){
                                this.onEditSave(record, form.getValues());
                                editWindow.close();
                            },
                            failure : function(response){
                                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                            },
                            scope : this
                        });
                    }
                },
                scope   : this
            }]
        });

        var editWindow = Ext4.create('Ext.window.Window', {
            width  : 450,
            height : 510,
            layout : 'fit',
            cls    : 'data-window',
            draggable : false,
            modal  : true,
            title  : record.data.name,
            defaults: {
                border: false, frame: false
            },
            bodyPadding : 20,
            items : viewForm,
            scope : this
        });

        editWindow.show();
    },

    onEditSave : function(record, values) {
        this.store.load();
    },

    onManageCategories : function(btn) {

        var cellEditing = Ext4.create('Ext.grid.plugin.CellEditing', {
            clicksToEdit: 2
        });

        var confirm = false;
        var store = this.initializeCategoriesStore();

        var grid = Ext4.create('Ext.grid.Panel', {
            store    : store,
            border   : false, frame: false,
            scroll   : 'vertical',
            columns  : [{
                xtype    : 'templatecolumn',
                text     : 'Category',
                flex     : 1,
                sortable : true,
                dataIndex: 'label',
                tpl      : '{label:htmlEncode}',
                editor   : {
                    xtype:'textfield',
                    allowBlank:false
                }
            },{
                xtype    : 'actioncolumn',
                width    : 50,
                align    : 'center',
                sortable : false,
                items : [{
                    icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_40 + '/resources/themes/images/access/qtip/close.gif',
                    tooltip : 'Delete'
                }],
                listeners : {
                    click : function(col, grid, idx, evt, x, y, z)
                    {
                        store.sync();

                        var label = store.getAt(idx).data.label;
                        var id    = store.getAt(idx).data.rowid;

                        var cats = {
                            categories : [{label : label, rowid: id}]
                        };

                        Ext4.Msg.show({
                            title : 'Delete Category',
                            msg   : 'Please confirm you would like to <b>DELETE</b> \'' + Ext4.htmlEncode(label) + '\' from the set of categories.',
                            buttons : Ext4.MessageBox.OKCANCEL,
                            icon    : Ext4.MessageBox.WARNING,
                            fn      : function(btn){
                                if (btn == 'ok') {
                                    // TODO: This is deprected -- should use proxy/model 'destroy' api
                                    Ext4.Ajax.request({
                                        url    : LABKEY.ActionURL.buildURL('study', 'deleteCategories.api'),
                                        method : 'POST',
                                        jsonData : cats,
                                        success: function() {
                                            store.load();
                                        },
                                        failure: function(response) {
                                            Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                                        }
                                    });
                                }
                            },
                            scope  : this
                        });
                    },
                    scope : this
                }
            }],
            multiSelect : true,
            cls         : 'iScroll', // webkit custom scroll bars
            viewConfig : {
                stripRows : true,
                plugins   : [{
                    ptype : 'gridviewdragdrop',
                    dragText: 'Drag and drop to reorganize'
                }],
                listeners : {
                    drop : function(node, data, model, pos) {
                        var s = grid.getStore();
                        var display = 1;
                        s.each(function(rec){
                            rec.set('displayOrder', display);
                            display++;
                        }, this);
                    }
                }
            },
            plugins   : [cellEditing],
            selType   : 'rowmodelfixed',
            scope     : this
        });

        var categoryOrderWindow = Ext4.create('Ext.window.Window', {
            title  : 'Manage Categories',
            width  : 550,
            height : 400,
            layout : 'fit',
            cls    : 'data-window',
            modal  : true,
            defaults  : {
                frame : false
            },
            items   : [grid],
            buttons : [{
                text    : 'New Category',
                handler : function(btn) {
                    var r = Ext4.ModelManager.create({
                        label        : 'New Category',
                        displayOrder : 0
                    }, 'Dataset.Browser.Category');
                    store.insert(0, r);
                    cellEditing.startEditByPosition({row : 0, column : 0});
                }
            },{
                text    : 'Done',
                handler : function(btn) {
                    grid.getStore().sync();
                    categoryOrderWindow.close();
                }
            }],
            listeners : {
                beforeclose : function()
                {
                    if (confirm)
                        this.onEditSave();
                },
                scope : this
            },
            scope     : this
        });

        categoryOrderWindow.show();
    }
});
