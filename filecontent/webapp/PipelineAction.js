/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
Ext.namespace("LABKEY.util");

 Ext.ux.clone = function(o) {
    if(!o || 'object' !== typeof o) {
        return o;
    }
    var c = '[object Array]' === Object.prototype.toString.call(o) ? [] : {};
    var p, v;
    for(p in o) {
        if(o.hasOwnProperty(p)) {
            v = o[p];
            if(v && 'object' === typeof v) {
                c[p] = Ext.ux.clone(v);
            }
            else {
                c[p] = v;
            }
        }
    }
    return c;
};

LABKEY.PipelineActionUtil = function(config){
/*
    multiSelect     // does this action support handling more than one file
    emptySelect     // if this action can operate on an empty directory
    description

    links : {},
    files : [],
*/

    Ext.apply(this, config);

    // only a single target for the action (no submenus)
    if (!this.links.items && config.links.text && config.links.href)
    {
        this.links.items = [{text: config.links.text, id: config.links.id, display: config.links.display, href: config.links.href}];
    }
};

LABKEY.PipelineActionUtil.prototype = {

    getText : function() {
        return this.links.text;
    },

    getId : function() {
        return this.links.id;
    },
    
    getLinks : function() {
        return this.links.items;
    },

    getFiles : function() {
        return this.files;
    },

    clearLinks : function() {
        this.links.items = [];
    },

    addLink : function(link)
    {
        if (!this.links)
            this.links = {items:[]};

        link.files = this.files;
        this.links.items.push(link);
    },

    getShortMessage : function() {
        return this.msgShort ? this.msgShort : '';
    },

    getLongMessage : function() {
        return this.msgLong ? this.msgLong : '';
    },

    setMessage : function(short, long) {
        this.msgShort = short;
        this.msgLong = long;
    },

    setEnabled : function(enabled) {
        this.enabled = enabled;
    },

    getEnabled : function() {
        return this.enabled;
    },

    getLink : function(id) {

        var links = this.getLinks();
        if (links && links.length)
        {
            for (var i=0; i < links.length; i++)
            {
                var link = links[i];

                if (link && link.id == id)
                    return link;
            }
        }
    },
    
    clone : function()
    {
        var config = Ext.ux.clone(this);
        return new LABKEY.PipelineActionUtil(config);
    }
};

LABKEY.PipelineActionConfig = function(config){
/*
    id : undefined,
    display : undefined,
    label : undefined,

    links : [],
*/
    Ext.apply(this, config);
};

LABKEY.PipelineActionConfig.prototype = {

    getLink : function(id) {

        if (this.links && this.links.length)
        {
            for (var i=0; i < this.links.length; i++)
            {
                var link = this.links[i];

                if (link && link.id == id)
                    return link;
            }
        }
    },

    addLink : function(id, display, label) {

        if (!this.links)
            this.links = [];
        
        this.links.push({id: id, display: display, label: label});
    },

    isDisplayOnToolbar : function() {

        if (this.links && this.links.length)
        {
            for (var i=0; i < this.links.length; i++)
            {
                if (this.links[i].display == 'toolbar')
                    return true;
            }
        }
        return false;
    }
};

LABKEY.FileContentConfig = Ext.extend(Ext.util.Observable, {

    actionConfig : {},              // map of actionId to PipelineActionConfig instances
    fileFields : [],                // array of extra field information to collect/display for each file uploaded
    filePropConfig : 'useDefault',
    inheritedFileConfig : {},//'useDefault',
    tbarBtnConfig : [],             // array of toolbar btn configuration
    gridConfig : {},                // grid column model config

    constructor : function(config)
    {
        LABKEY.FileContentConfig.superclass.constructor.call(this, config);

        this.addEvents(
            /**
             * @event filePropConfigChanged
             * Fires after this object is updated with a new set of file properties.
             * @param {LABKEY.FileContentConfig} this
             */
            'filePropConfigChanged',
            /**
             * @event actionConfigChanged
             * Fires after this object is updated with a new set of action configurations.
             */
            'actionConfigChanged',
            /**
             * @event tbarConfigChanged
             * Fires after this object is updated with new toolbar button configurations.
             */
            'tbarConfigChanged',

            'gridConfigChanged'
        );
    },

    /**
     * Adds an array of action configuration objects
     * @param{Ext.Action[]} actions
     */
    setActionConfigs : function(actions)
    {
        if (actions && actions.length)
        {
            this.actionConfig = {};

            for (var i=0; i < actions.length; i++)
            {
                var action = actions[i];
                this.actionConfig[action.id] = new LABKEY.PipelineActionConfig(action);
            }
            this.fireEvent('actionConfigChanged');
        }
    },

    getActionConfig : function(id) {

        return this.actionConfig[id];
    },

    getActionConfigs : function()
    {
        var actions = [];

        for (var action in this.actionConfig)
        {
            var a = this.actionConfig[action];
            if ('object' == typeof a )
            {
                actions.push(a);
            }
        }
        return actions;
    },

    setFileFields : function(fields) {
        if (fields && fields.length)
            this.fileFields = fields;
        this.fireEvent('filePropConfigChanged', this);
    },

    setFilePropConfig : function(config) {
        this.filePropConfig = config;
    },

    setGridConfig : function(config) {
        this.gridConfig = config;
        this.fireEvent('gridConfigChanged', this);
    },

    getGridConfig : function() {
        return this.gridConfig;
    },

    isCustomFileProperties : function() {
        if (this.filePropConfig == 'useCustom')
            return true;
        else if (this.filePropConfig == 'useParent' && this.inheritedFileConfig.fileConfig == 'useCustom')
            return true;
        return false;
    },

    getFilePropContainerPath : function() {
        return this.inheritedFileConfig.containerPath;    
    },

    setTbarBtnConfig : function(config) {
        if (config && config.length)
            this.tbarBtnConfig = config;
        else
            this.tbarBtnConfig = [];
        this.fireEvent('tbarConfigChanged', this);
    },

    /**
     * Merges the default set of std toolbar buttons with any available button customizations and
     * returns an array of config objects.
     */
    createStandardButtons : function(defaultTbarItems)
    {
        var btnOptions = [];

        if (defaultTbarItems && defaultTbarItems.length)
        {
            var i;
            var item;

            if (this.tbarBtnConfig.length)
            {
                var baseTbarMap = {};       // map to help determine if customized button is in the set of default buttons
                for (i=0; i < defaultTbarItems.length; i++)
                {
                    item = defaultTbarItems[i];
                    if (typeof item == 'string')
                        baseTbarMap[item] = item;
                }

                for (i=0; i < this.tbarBtnConfig.length; i++)
                {
                    var config = this.tbarBtnConfig[i];
                    if (config.id in baseTbarMap)
                        btnOptions.push({id:config.id, hideText:config.hideText, hideIcon:config.hideIcon});
                }
            }
            else
            {
                for (i=0; i < defaultTbarItems.length; i++)
                {
                    item = defaultTbarItems[i];
                    if (typeof item == 'string')
                       btnOptions.push({id:item});
                }
            }
        }
        return btnOptions;
    },

    /**
     * Creates column model columns from the custom file properties
     */
    createColumnModelColumns : function()
    {
        var columns = [];
        var ttRenderer = function(value, p, record) {
            var msg = Ext.util.Format.htmlEncode(value);
            p.attr = 'ext:qtip="' + msg + '"';
            return msg;
        };
        var lookupRenderer = function(value, p, record, rIdx, cIdx) {
            var displayCol = this.dataIndex + '_displayValue';

            if (record.data[displayCol])
                return Ext.util.Format.htmlEncode(record.data[displayCol]);
            else
                return Ext.util.Format.htmlEncode(value);
        };

        if (this.fileFields)
        {
            for (var i=0; i < this.fileFields.length; i++)
            {
                var prop = this.fileFields[i];
                var idx = prop.rangeURI.indexOf('#multiLine');

                if (prop.lookupQuery && prop.lookupSchema)
                    columns.push({header: prop.label ? prop.label : prop.name, dataIndex: prop.name, renderer: lookupRenderer, sortable:true});
                else
                    // multiline fields will have a tooltip to display the entire contents
                    columns.push({header: prop.label ? prop.label : prop.name, dataIndex: prop.name, renderer: idx != -1 ? ttRenderer : Ext.util.Format.htmlEncode, sortable:true});
            }
        }
        return columns; 
    },

    /**
     * Creates files system config properties so that we can request and store
     * custom file properties through webdav
     */
    createFileSystemConfig : function(initialConfig)
    {
        var config = {extraPropNames:[], extraDataFields:[]};

        // if there were also extra file system fields specified in the initial configuration, fold these in as well
        if (initialConfig)
        {
            Ext.apply(config.extraPropNames, initialConfig.extraPropNames);
            Ext.apply(config.extraDataFields, initialConfig.extraDataFields);
        }

        if (this.fileFields)
        {
            config.extraPropNames.push('custom');
            for (var i=0; i < this.fileFields.length; i++)
            {
                var prop = this.fileFields[i];
                var mapName = prop.name;

                if (prop.lookupQuery && prop.lookupSchema){
                    // for lookup columns, show the display value
                    var displayValueName = mapName + '_displayValue';
                    config.extraDataFields.push({name: displayValueName, mapping: 'propstat/prop/custom/' + displayValueName});
                }
                config.extraDataFields.push({name: prop.name, mapping: 'propstat/prop/custom/' + mapName});
            }
        }
        return config;
    }
});

LABKEY.util.PipelineActionUtil = function() {
    return {

        /**
         * Parses a json array and returns an array of PipelineActions, the input config would take the form
         * returned by the server pipeline actions:
         *
         *  {
         *      files:[],
         *      multiSelect: boolean,
         *      emptySelect: boolean,
         *      description: '',
         *      links: {
         *          id: '',
         *          text: '',
         *          items: [{
         *              id: '',
         *              text: '',
         *              leaf: boolean,
         *              href: ''
         *          }]
         *      }
         *  }
         *
         * and create a PipelineAction for each object in the items array.
         */
        parseActions : function(actions) {

            var pipelineActions = [];
            if (actions && actions.length)
            {
                for (var i=0; i < actions.length; i++)
                {
                    var action = actions[i];
                    var config = {
                        files: action.files,
                        groupId: action.links.id,
                        groupLabel: action.links.text,
                        multiSelect: action.multiSelect,
                        emptySelect: action.emptySelect,
                        description: action.description
                    };

                    // only a single target for the action (no submenus)
                    if (!action.links.items && action.links.text && action.links.href)
                    {
                        config.id = action.links.id;
                        config.link = {text: action.links.text, id: action.links.id, href: action.links.href};

                        pipelineActions.push(new LABKEY.util.PipelineAction(config));
                    }
                    else
                    {
                        for (var j=0; j < action.links.items.length; j++)
                        {
                            var item = action.links.items[j];

                            config.id = item.id;
                            config.link = item;

                            pipelineActions.push(new LABKEY.util.PipelineAction(config));
                        }
                    }
                }
            }
            return pipelineActions;
        }
    };
}();

LABKEY.util.PipelineAction = Ext.extend(Object, {

    constructor : function(config) {

        Ext.apply(this, config);
    },

    getText : function() {
        return this.link.text;
    },

    getId : function() {
        return this.link.id;
    },

    getLink : function() {
        return this.link;
    },

    getFiles : function() {
        return this.files;
    },

    getShortMessage : function() {
        return this.msgShort ? this.msgShort : '';
    },

    getLongMessage : function() {
        return this.msgLong ? this.msgLong : '';
    },

    setMessage : function(short, long) {
        this.msgShort = short;
        this.msgLong = long;
    },

    setEnabled : function(enabled) {
        this.enabled = enabled;
    },

    getEnabled : function() {
        return this.enabled;
    }
});
