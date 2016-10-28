/*
 * Copyright (c) 2013-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.ScriptReportPanel', {

    extend: 'Ext.tab.Panel',

    border: false,

    frame: false,

    sourceAndHelp: true,

    /**
     * {boolean} reportConfig properties will be html decoded upon construction
     */
    htmlEncodedProps: false,

    initComponent : function() {

        Ext4.QuickTips.init();

        this.reportConfig.script = this.script;

        if (this.htmlEncodedProps) {
            Ext4.apply(this.reportConfig, {
                dataRegionName: Ext4.htmlDecode(this.reportConfig.dataRegionName),
                schemaName: Ext4.htmlDecode(this.reportConfig.schemaName),
                queryName: Ext4.htmlDecode(this.reportConfig.queryName),
                viewName: Ext4.htmlDecode(this.reportConfig.viewName)
            });
        }

        this.items = [this.createViewPanel()];

        if (this.reportConfig.schemaName && this.reportConfig.queryName) {
            this.items.push(this.createDataPanel());
        }

        this.items.push(this.createSourcePanel());
        this.items.push(this.createHelpPanel());
        if (this.sourceAndHelp) {
            if (this.preferSourceTab)
                this.activeTab = this.items.length-2;
        }
        this.callParent();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    markDirty : function(dirty) {
        this.dirty = dirty;
    },

    resetDirty : function() {
        this.dirty = false;
        if (this.codeMirror)
            this.reportConfig.script = this.codeMirror.getValue();
    },

    isDirty : function() {
        return this.dirty || (this.codeMirror && this.codeMirror.getValue() != this.reportConfig.script);
    },

    beforeUnload : function() {
        if (this.isDirty()) {
            return 'please save your changes';
        }
    },

    createViewPanel : function() {

        var panelId = Ext4.id();

        return {
            title   : 'Report',
            html    : {
                tag : 'div',
                children : [
                    {tag : 'div', cls : 'reportView', id : panelId},
                    {tag : 'div', cls : 'reportView', id : 'backgroundReportDiv'}
                ]
            },
            bodyPadding : 10,
            autoScroll  : true,
            listeners : {
                activate : {fn : function(cmp){

                    var url = this.getViewURL();
                    if (this.isViewConfigChanged(url))
                    {
                        var config = this.getViewConfig(url);

                        cmp.getEl().mask("Loading report results...");
                        Ext4.Ajax.request({
                            url : this.getViewURL(),
                            method: 'POST',
                            success: function(resp){
                                // issue 18430, unmask before we load the HTML, just in case there is a javascript error
                                // in the rendered content
                                cmp.getEl().unmask();
                                // Update the view div with the returned HTML, and make sure scripts are run
                                LABKEY.Utils.loadAjaxContent(resp, panelId, function() {
                                    cmp.doLayout();
                                });
                            },
                            failure : function(resp) {this.viewFailure(cmp);},
                            jsonData: config.parameters,
                            scope   : this
                        });
                    }
                }, scope : this}
            }
        };
    },

    viewFailure : function(cmp) {

        Ext4.get(cmp.getEl()).update('<span class="labkey-error" style="width: 600px; height: 400px; display: block; margin-left: 250px;">Failed to retrieve report results</span>');
        cmp.getEl().unmask();
        this.prevScriptSource = null;
        this.prevViewURL = null;
    },

    isViewConfigChanged : function(viewURL) {

        if (!this.prevScriptSource)
            return true;
        if (this.codeMirror && this.codeMirror.getValue() != this.prevScriptSource)
            return true;
        if (viewURL != this.prevViewURL)
            return true;

        var form = this.formPanel.getForm();
        if (form && form.isValid())
        {
            if (this.prevFormValues != form.getValues(true))
                return true;
        }
        return false;
    },

    getViewConfig : function(viewURL) {

        var form = this.formPanel.getForm();
        var data = {};

        if (form && form.isValid())
        {
            data = form.getValues();
            this.prevFormValues = form.getValues(true);
        }

        var script = this.codeMirror ? this.codeMirror.getValue() : this.reportConfig.script;

        data.script = script;
        data.dataRegionName = this.reportConfig.dataRegionName;

        // save previous values
        this.prevScriptSource = script;
        this.prevViewURL = viewURL;

        return {parameters : data};
    },

    createDataPanel : function() {

        return {
            xtype       : 'box',
            title       : 'Data',
            autoScroll  : true,
            cls         : 'iScroll reportData',
            bodyPadding : 10,
            listeners   : {
                render : {fn : function(cmp){this.renderDataGrid(cmp);}, scope : this}
            }
        };

    },

    renderDataGrid : function(cmp) {
        LABKEY.requiresExt3ClientAPI(function() { this._doRenderGrid(cmp); }, this);
    },

    _doRenderGrid : function(cmp) {
        var filters = LABKEY.Filter.getFiltersFromUrl(this.initialURL, this.reportConfig.dataRegionName);
        var sort = LABKEY.Filter.getSortFromUrl(this.initialURL, this.reportConfig.dataRegionName);
        var params = LABKEY.Filter.getQueryParamsFromUrl(this.initialURL, this.reportConfig.dataRegionName);

        var config = {
            schemaName  : this.reportConfig.schemaName,
            queryName   : this.reportConfig.queryName,
            viewName    : this.reportConfig.viewName,
            parameters  : params,
            removeableFilters   : filters,
            removeableSort      : sort,
            dataRegionName      : this.reportConfig.dataRegionName + '_report',
            frame       : 'none',
            showBorders : false,
            showDetailsColumn       : false,
            showUpdateColumn        : false,
            showRecordSelectors     : false,
            showSurroundingBorder   : false,
            showPagination          : true,
            disableAnalytics        : true,
            allowHeaderLock         : false,
            buttonBar   : {
                includeStandardButton: false,
                items: [LABKEY.QueryWebPart.standardButtons.exportRows, LABKEY.QueryWebPart.standardButtons.pageSize]
            },
            success : this.onDataSuccess,
            failure : this.onDataFailure,
            scope   : this
        };

        if (this.viewName)
            config.viewName = this.viewName;

        var wp = new LABKEY.QueryWebPart(config);

        wp.render(cmp.getId());
    },

    onDataSuccess : function(dr) {

        if (dr)
        {
            this.dataRegion = dr;

            // On first load of the QWP, initialize the "previous view URL" to match the current dataregion state.  This
            // prevents an unnecessary refresh of the report in scenarios like "Source" -> "View" -> "Data" -> "View".
            if (!this.initialLoad)
            {
                this.initialLoad = true;
                this.prevViewURL = this.getViewURL();
            }

            // show any filter messages on the enclosing dataregion's msg area
            var outerRegion = LABKEY.DataRegions[this.reportConfig.dataRegionName];
            var msgPart = 'filter';

            if (outerRegion)
            {
                if (dr.hasMessage(msgPart))
                {
                    outerRegion.addMessage(dr.getMessage(msgPart), msgPart);
                }
                else
                {
                    outerRegion.removeMessage(msgPart);
                }
            }
        }
    },

    onDataFailure : function(cmp) {

        Ext4.get(cmp.getEl()).update('<span class="labkey-error" style="width: 600px; height: 400px; display: block; margin-left: 250px;">Failed to retrieve data grid.</span>');
    },

    createSourcePanel : function() {

        var items = [];
        var id = Ext4.id();

        items.push({
            xtype : 'fieldset',
            title : 'Script Source',
            items : [{
                xtype   : 'box',
                frame   : false,
                html    : '<textarea rows="25" name="metadata" id="' + id + '"></textarea>',
                listeners : {
                    render : {fn : function(cmp){

                        var code = Ext4.get(id);
                        var size = cmp.getSize();

                        if (code) {

                            this.codeMirror = CodeMirror.fromTextArea(code.dom, {
                                mode            : this.reportConfig.editAreaSyntax ? this.reportConfig.editAreaSyntax : 'text/plain',
                                lineNumbers     : true,
                                lineWrapping    : true,
                                readOnly        : this.readOnly,
                                indentUnit      : 3
                            });

                            this.codeMirror.setSize(null, size.height + 'px');
                            this.codeMirror.setValue(this.reportConfig.script);

                            LABKEY.codemirror.RegisterEditorInstance('script-report-editor', this.codeMirror);
                        }
                    }, scope : this}
                }
            }]
        });

        items.push({
            xtype : 'fieldset',
            title : 'Options',
            hidden: this.readOnly,
            defaults    : {xtype : 'checkbox', labelWidth : 12},
            items : [
                {name : 'shareReport',
                    boxLabel : 'Make this report available to all users',
                    checked : this.reportConfig.shareReport,
                    listeners : {
                        scope: this,
                        'change': function(cb, value) {
                            this.down('checkbox[name=sourceTabVisible]').setDisabled(!value);
                            if (!value)
                                this.down('checkbox[name=sourceTabVisible]').setValue(null);
                        }
                    }
                },
                {name : 'sourceTabVisible', boxLabel : 'Show source tab to all users', fieldLabel : ' ', checked : this.reportConfig.sourceTabVisible, disabled : !this.reportConfig.shareReport},
                {name : 'inheritable',
                        hidden : !this.reportConfig.allowInherit,
                        boxLabel : 'Make this report available in child folders&nbsp;' +
                                '<span data-qtip="If this check box is selected, this report will be available in data grids of child folders where the schema and table are the same as this data grid."><img src="' + LABKEY.contextPath + '/_images/question.png"/></span>',
                        checked : this.reportConfig.inheritable},
                {name : 'runInBackground',
                    hidden : !this.reportConfig.supportsPipeline,
                    boxLabel : 'Run this report in the background as a pipeline job',
                    checked : this.reportConfig.runInBackground,
                    listeners : {
                        scope: this,
                        'change': function(cb, value) {this.runInBackground = value;}
                    }
                }
            ]
        });

        if (this.reportConfig.knitrOptions)
        {
            items.push({
                xtype : 'fieldset',
                title : 'Knitr Options',
                collapsible : true,
                collapsed   : true,
                defaults    : {xtype : 'radio', labelWidth : 12},
                hidden      : this.readOnly,
                items : [
                    {name : 'knitrFormat',
                        inputValue : 'None',
                        boxLabel : 'None&nbsp;' +
                                '<span data-qtip="The source is run without going through knitr."><img src="' + LABKEY.contextPath + '/_images/question.png"/></span>',
                        checked : this.reportConfig.knitrFormat == 'None'},
                    {name : 'knitrFormat',
                        inputValue : 'Html',
                        boxLabel : 'Html&nbsp;' +
                                '<span data-qtip="Use knitr to process html source"><img src="' + LABKEY.contextPath + '/_images/question.png"/></span>',
                        checked : this.reportConfig.knitrFormat == 'Html'},
                    {name : 'knitrFormat',
                        inputValue : 'Markdown',
                        boxLabel : 'Markdown&nbsp;' +
                                '<span data-qtip="Use knitr to process markdown source"><img src="' + LABKEY.contextPath + '/_images/question.png"/></span>',
                        checked : this.reportConfig.knitrFormat == 'Markdown'},
                    { xtype : 'label',
                      html : 'Dependencies&nbsp;' +
                              '<span data-qtip="Add a semi-colon delimited list of javascript, CSS, or library dependencies here."><img src="' + LABKEY.contextPath + '/_images/question.png"/></span>'},
                    { name : 'scriptDependencies',
                        xtype : 'textarea',
                        value : this.reportConfig.scriptDependencies,
                        margin : '5 0 0 0',
                        grow : true,
                        width : 450,
                        hideLabel : true
                    }
                ]
            });
        }

        if (this.reportConfig.javascriptOptions)
        {
            items.push({
                xtype : 'fieldset',
                title : 'JavaScript Options',
                collapsible : true,
                collapsed   : true,
                hidden      : this.readOnly,
                defaults    : {xtype : 'checkbox', labelWidth : 12},
                items : [
                    {name : 'useGetDataApi',
                        boxLabel : 'Use GetData API&nbsp;' +
                        '<span data-qtip="Uses the GetData API to retrieve data. Allows you to pass the data through one or more transforms before retrieving it. ' +
                                    'See the documentation at : www.labkey.org/download/clientapi_docs/javascript-api/symbols/LABKEY.Query.GetData.html"><img src="' + LABKEY.contextPath + '/_images/question.png"/></span>',
                        checked : this.reportConfig.useGetDataApi,
                        uncheckedValue : false}
                ]
            });
        }

        if (this.reportConfig.thumbnailOptions)
        {
            // thumbnail options
            var thumbnails = [];

            thumbnails.push({name : 'thumbnailType',
                boxLabel : 'Auto-generate&nbsp;' +
                        '<span data-qtip="Auto-generate a new thumbnail based on the first available output from this report (i.e. image, pdf, etc.)"><img src="' + LABKEY.contextPath + '/_images/question.png"/></span>',
                inputValue : 'AUTO', checked : this.reportConfig.thumbnailType == 'AUTO'});
            thumbnails.push({name : 'thumbnailType',
                boxLabel : 'None&nbsp;' +
                        '<span data-qtip="Use the default static image for this report"><img src="' + LABKEY.contextPath + '/_images/question.png"/></span>',
                inputValue : 'NONE', checked : this.reportConfig.thumbnailType == 'NONE'});

            if (this.reportConfig.thumbnailType == 'CUSTOM')
                thumbnails.push({name : 'thumbnailType', boxLabel : 'Keep existing', inputValue : 'CUSTOM', checked : true});

            items.push({
                xtype : 'fieldset',
                title : 'Report Thumbnail',
                defaults    : {xtype : 'radio'},
                collapsible : true,
                collapsed   : true,
                hidden      : this.readOnly,
                items       : thumbnails
            });
        }

        if (this.sharedScripts && this.sharedScripts.length > 0)
        {
            var scripts = [];

            for (var i=0; i < this.sharedScripts.length; i++)
            {
                var script = this.sharedScripts[i];
                scripts.push({name : 'includedReports', boxLabel : script.name, inputValue : script.reportId, checked : script.included});
            }

            items.push({
                xtype : 'fieldset',
                title : 'Shared Scripts',
                collapsible : true,
                collapsed   : true,
                defaults    : {xtype : 'checkbox'},
                hidden      : this.readOnly,
                items : scripts
            });
        }

        if (this.reportConfig.studyOptions && LABKEY.moduleContext.study)
        {
            var subjectNoun = LABKEY.moduleContext.study.subject.nounSingular;
            items.push({
                xtype : 'fieldset',
                title : 'Study Options',
                collapsible : true,
                collapsed   : true,
                hidden      : this.readOnly,
                defaults    : {xtype : 'checkbox', labelWidth : 12},
                items : [
                    {name : 'filterParam',
                        inputValue : 'participantId',
                        boxLabel : subjectNoun + ' chart&nbsp;' +
                        '<span data-qtip="' + subjectNoun + ' chart views show measures for only one ' + subjectNoun + ' at a time. ' + subjectNoun +
                                                ' chart views allow the user to step through charts for each ' + subjectNoun + ' shown in any dataset grid."><img src="' + LABKEY.contextPath + '/_images/question.png"/></span>',
                        checked : this.reportConfig.filterParam == 'participantId'},
                    {name : 'cached',
                        boxLabel : 'Automatically cache this report for faster reloading',
                        checked : this.reportConfig.cached}
                ]
            });
        }

        items.push({
            xtype   : 'button',
            text    : 'Save',
            hidden  : this.readOnly,
            handler : function() {

                if (this.reportConfig.reportId)
                    this.save();
                else
                {
                    Ext4.MessageBox.show({
                        title   : 'Save Report',
                        msg     : 'Please enter a report name:',
                        buttons : Ext4.MessageBox.OKCANCEL,
                        fn      : function(btnId, name) {
                            if (btnId == 'ok')
                                this.save(name);
                        },
                        prompt  : true,
                        scope   : this
                    });
                }
            },
            scope : this
        });

        // hidden elements
        items.push({xtype : 'hidden', name : 'reportType', value : this.reportConfig.reportType});
        items.push({xtype : 'hidden', name : 'schemaName', value : this.reportConfig.schemaName});
        items.push({xtype : 'hidden', name : 'queryName', value : this.reportConfig.queryName});
        if (this.reportConfig.viewName)
            items.push({xtype : 'hidden', name : 'viewName', value : this.reportConfig.viewName});

        items.push({xtype : 'hidden', name : 'dataRegionName', value : this.reportConfig.dataRegionName});
        items.push({xtype : 'hidden', name : 'redirectUrl', value : this.redirectUrl});

        this.formPanel = Ext4.create('Ext.form.Panel', {
            title   : 'Source',
            cls     : 'reportSource',
            hidden  : !this.sourceAndHelp,
            frame   : false,
            bodyPadding : 10,
            fieldDefaults  : {
                labelWidth : 100,
                style      : 'padding: 4px 0',
                labelSeparator : ''
            },
            items   : items,
            listeners : {
                dirtychange : {fn : function(cmp, dirty){this.markDirty(dirty);}, scope : this}
            }
        });

        return this.formPanel;
    },

    createHelpPanel : function() {

        return {
            title   : 'Help',
            frame   : false,
            hidden  : !this.sourceAndHelp,
            bodyPadding : 10,
            html    : this.reportConfig.helpHtml
        };
    },

    // Build up an AJAX url that includes all the report rendering parameters.  Ideally, we would AJAX post
    // the entire form instead of creating a custom URL this way, but this lets us track changes more easily.
    // CONSIDER: AJAX post the form and track dirty on the form, data filters, data sorts, etc.
    //
    getViewURL : function() {

        var url = this.dataRegion ? this.baseURL : this.initialURL;

        if (this.dataRegion)
        {
            var prefix = this.dataRegion.name + '.';
            var params = LABKEY.ActionURL.getParameters(this.dataRegion.requestURL);

            for (var name in params)
            {
                if (name.substr(0, prefix.length) == prefix)
                {
                    // Translate "Data" tab dataregion params to use view tab dataregion name
                    var newName = this.reportConfig.dataRegionName + '.' + name.substr(prefix.length);
                    url = url + '&' + encodeURIComponent(newName) + '=' + encodeURIComponent(params[name]);
                }
            }
        }

        return url;
    },

    save : function(name) {

        var form = this.formPanel.getForm();

        if (form && form.isValid())
        {
            var data = form.getValues();

            if (this.reportConfig.reportId)
                data.reportId = this.reportConfig.reportId;
            else
                data.reportName = name;
            data.script = this.codeMirror.getValue();

            Ext4.Ajax.request({
                url : this.saveURL,
                method  : 'POST',
                success : function(resp, opt) {
                    var o = Ext4.decode(resp.responseText);

                    if (o.success) {
                        this.resetDirty();
                        window.location = o.redirect;
                    }
                    else
                        LABKEY.Utils.displayAjaxErrorResponse(resp, opt);
                },
                failure : LABKEY.Utils.displayAjaxErrorResponse,
                jsonData: data,
                scope   : this
            });
        }
    }
});
