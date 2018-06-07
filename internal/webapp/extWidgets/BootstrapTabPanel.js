/*
 * Copyright (c) 2010-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('LABKEY.ext4');

/**
 * Constructs a new LabKey tab panel that uses bootstrap nav tabs/pills and can support Ext4 components within the tabs.
 * @param config Configuration properties.
 * @param {String} [config.title] A title to display above the tab panel.
 * @param {String} [config.description] A description to display above the tab panel.
 * @param {String} config.items Array of tab panel items. Each item must have a title and an array of Ext4 items. Use active: true for the initial active tab.
 * @param {String} [config.tabPadding] Padding to apply to each tab content div. Defaults to '10px 10px 0 0'.
 * @param {String} [config.usePills] True to use pills instead of tabs. Defaults to false.
 * @param {String} [config.stacked] True to stack the tabs/pills vertically. Defaults to false.
 * @param {String} [config.justified] True to horizontally justify the tabs/pills. Defaults to false.
 * @example &lt;script type="text/javascript"&gt;
    Ext4.onReady(function(){

        Ext4.create('LABKEY.ext4.BootstrapTabPanel', {
            renderTo: 'lk-tabpanel-1',
            title: 'LABKEY.ext4.BootstrapTabPanel Example',
            description: 'This is a bootstrap styled tab panel that allows for Ext4 components to live within it.',
            items: [{
                title: 'Tab 1',
                active: true,
                items: [{
                    xtype: 'box',
                    html: 'content for tab 1'
                }]
            },{
                title: 'Tab 2',
                items: [
                    Ext4.create('LABKEY.ext4.BootstrapTabPanel', {
                        usePills: true,
                        // justified: true,
                        //stacked: true,
                        items: [{
                            title: 'Inner Tab A',
                            active: true,
                            items: [{
                                xtype: 'box',
                                html: 'content for inner tab A'
                            }]
                        },{
                            title: 'Inner Tab B',
                            items: [{
                                xtype: 'box',
                                html: 'content for inner tab B'
                            }]
                        }]
                    })
                ]
            }]
        });

    });
 &lt;/script&gt;
 &lt;div id='lk-tabpanel-1'/&gt;
 */
Ext4.define("LABKEY.ext4.BootstrapTabPanel", {
    extend: 'Ext.Component',
    alias: 'widget.labkey-bootstraptabpanel',

    title: null, // title to be shown above the tab panel
    description: null, // description text to be shown above the tab panel
    tabPadding: '10px 10px 0 0', // default per tab padding
    items: null, // array of tab element items
    tabTokenPrefix: 'tab=',

    usePills: false, // change from bootstrap nav-tabs to nav-pills
    stacked: false, // vertically stack the tabs/pills
    justified: false, // horizontally justify the tabs/pills

    initComponent: function(){

        if (this.items == null || !Ext4.isArray(this.items) || this.items.length < 1) {
            console.error('Invalid items array configuration.');
            return;
        }

        // get the hash to see if there is an active tab we are supposed to set
        var tokens = Ext4.util.History.getToken();
        tokens = tokens ? tokens.split('&') : [];

        this.activeTabId = null;
        Ext4.each(this.items, function(item) {
            // add a generated id to each item to be used in the tpl
            item.id = LABKEY.Utils.id();

            // generate an id, based on the title, for the tab <a> tag to be used by automated tests
            item.tabId = item.title ? item.title.replace(/\s/g, '') + 'Tab' : LABKEY.Utils.id();
            var elIndex = 1, origId = item.tabId;
            while (Ext4.get(item.tabId) !== null) {
                item.tabId = origId + '-' + elIndex;
                elIndex++;
            }

            // set the tabCls for the active tab, if any
            if (this.activeTabId == null) {
                if (item.active) {
                    item.tabCls = 'active';
                    this.activeTabId = item.id;
                }
                else if (item.itemId && Ext4.Array.contains(tokens, this.tabTokenPrefix + item.itemId)) {
                    item.tabCls = 'active';
                    this.activeTabId = item.id;
                }
            }
        }, this);

        // if no active tab, use the first one
        if (!this.activeTabId) {
            this.items[0].tabCls = 'active';
            this.activeTabId = this.items[0].id;
        }

        var navCls = 'nav', sep = ' ';
        navCls += sep + (this.usePills ? 'nav-pills' : 'nav-tabs');
        if (this.stacked) {
            navCls += sep + 'nav-stacked';
        }
        if (this.justified) {
            navCls += sep + 'nav-justified';
        }

        this.tpl = [
            '<div>',
                this.title ? '<h3>' + LABKEY.Utils.encodeHtml(this.title) + '</h3>' : '',
                this.description ? '<p>' + LABKEY.Utils.encodeHtml(this.description) + '</p>' : '',
                '<ul class="' + navCls + '">',
                    '<tpl for=".">',
                        '<li class="{tabCls}"><a id="{tabId}" data-toggle="' + (this.usePills ? 'pill' : 'tab') + '" href="#{id}">{title}</a></li>',
                    '</tpl>',
                '</ul>',
                '<div class="tab-content">',
                    '<tpl for=".">',
                        '<div id="{id}" class="tab-pane {tabCls}"></div>',
                    '</tpl>',
                '</div>',
            '</div>'
        ];

        this.data = this.items;

        this.on('boxready', this.onReady, this);

        this.callParent(arguments);

        // delayed task to help with layout / resizing of components as tabs change
        this.delayedLayout = new Ext.util.DelayedTask(function() {
            var item = this.findItemForActiveTabId();
            if (item && item.container) {
                item.container.doLayout();
            }
        }, this);
    },

    onReady: function() {
        // on first activate for a tab, initialize it with the items provided
        this.ensureContainerForActiveTab();

        // add listener for other tab activate/show changes
        Ext4.each(Ext4.query('a[data-toggle="' + (this.usePills ? 'pill' : 'tab') + '"]'), function(el) {
            Ext4.get(el).on('click', function(evt, target) {
                this.activeTabId = target.href.substring(target.href.lastIndexOf('#')+1);
                this.ensureContainerForActiveTab();
            }, this);
        }, this);
    },

    ensureContainerForActiveTab: function() {
        var item = this.findItemForActiveTabId();
        if (item != null && !item.container) {
            item.container = Ext4.create('Ext.container.Container', {
                renderTo: item.id,
                padding: this.tabPadding,
                defaults: {
                    border: false,
                    bodyStyle: 'background-color: transparent;'
                },
                listeners: {
                    boxready: function(cmp) {
                        if (item.items) {
                            cmp.add(item.items);
                        }
                        if (Ext4.isFunction(item.onReady)) {
                            item.onReady.call(item.scope || this, cmp);
                        }
                    }
                }
            });
        }

        // if the item has an itemId, add to the hash
        if (item.itemId) {
            Ext4.util.History.add(this.tabTokenPrefix + item.itemId);
        }

        this.delayedLayout.delay(500);
    },

    findItemForActiveTabId: function() {
        var index = Ext4.Array.pluck(this.items, 'id').indexOf(this.activeTabId);
        return index > -1 ? this.items[index] : null;
    }
});