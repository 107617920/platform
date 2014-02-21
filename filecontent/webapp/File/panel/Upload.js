/*
 * Copyright (c) 2012-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Upload', {

    extend : 'Ext.panel.Panel',

    layout : 'fit',

    bodyPadding: 5,

    border : false,

    separator: Ext4.isWindows ? "\\" : "/",

    rootPath: "/",

    baseURL : LABKEY.contextPath + "/_webdav",

    allowFileDrop : true,

    lastSummary: {info:0, success:0, file:'', pct:0},

    header : false,

    bodyStyle: 'background-color:#f0f0f0;',

    constructor : function(config) {

        this.callParent([config]);

        this.addEvents('cwd', 'transferstarted', 'transfercomplete', 'closeUploadPanel');
    },

    initComponent : function() {

        this.dockedItems = this.getAppletStatusBar();
        this.items = this.getItems();

        this.callParent();
        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this, 'an operation is still pending, please wait until it is complete.');
    },

    beforeUnload : function() {
        if (this.isBusy()) {
            return 'an operation is still pending, please wait until it is complete.';
        }
    },

    isBusy : function() {
        return this.busy;
    },

    setBusy : function(busy) {
        this.busy = busy;
    },

    // From FileSystem.js
    getPrefixUrl: function() {
        var prefix = '';

        prefix = this.concatPaths(this.baseURL, this.rootPath);

        if (prefix.length > 0 && prefix.charAt(prefix.length-1) == this.separator){
            prefix = prefix.substring(0,prefix.length-1);
        }

        return prefix;
    },

    // From FileSystem.js
    concatPaths : function(a,b)
    {
        var c = 0;
        if (a.length > 0 && a.charAt(a.length-1)==this.separator) c++;
        if (b.length > 0 && b.charAt(0)==this.separator) c++;
        if (c == 0)
            return a + this.separator + b;
        else if (c == 1)
            return a + b;
        else
            return a + b.substring(1);
    },

    // From FileSystem.js
    getParentPath : function(p)
    {
        if (!p)
            p = this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.separator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.separator);
        return i == -1 ? this.rootPath : p.substring(0,i+1);
    },

    getItems : function() {
        if (this.allowFileDrop && LABKEY.experimental && LABKEY.experimental.dragDropUpload && window.Dropzone && window.Dropzone.isBrowserSupported())
        {
            return this.createDropPanel();
        }
        else
        {
            return this.getOuterPanel();
        }
    },

    createDropPanel : function () {
        var outer = Ext4.create('Ext.form.Panel', {
            border: false,
            bodyStyle: this.bodyStyle,
            height: 60,
            layout: 'fit',
            items: [{
                xtype: 'component',
                cls: 'dropzone dz-clickable',
                html: "<div class='dz-message' style='text-align:center'><b>Drop files here to upload</b><br><em>(or click to browse...)</em></div>",
                listeners: {
                    afterrender: function (panel) {
                        this.initDropzone(panel);
                    },
                    scope: this
                }
            }]
        });

        return [outer];
    },

    initDropzone : function (component) {
        var dom = component.getEl().dom;

        Dropzone.autoDiscover = false;

        this.dropzone = new Dropzone(dom, {
            url: 'bogus.view',
            clickable: true,
            createImageThumbnails: false,

            previewsContainer: false,

            maxFiles: 5000,

            // LabKey webdav only handles single POST per file
            uploadMultiple: false,

            // LabKey webdav PUT will create intermediate directories, while POST'ing into the parent collection won't.
            method: 'PUT',

            accept: function (file, done) {
                console.log("accpet event: ", file);
                console.log(" -> fullPath: " + file.fullPath);

                // UNDONE: If file.fullPath is not empty, check that the Upload panel is configured to allowsDirectoryUpload
                done();
            }
        });
        this.dropzone.uploadPanel = this;

        this.dropzone.on('drop', function (evt) {
            console.log("drop event");
        });

        this.dropzone.on('dragstart', function (evt) {
            console.log("dragstart event");
        });

        this.dropzone.on('dragend', function (evt) {
            console.log("dragend event");
        });

        this.dropzone.on('dragenter', function (evt) {
            console.log("dragenter event");
        });

        this.dropzone.on('dragover', function (evt) {
            this.uploadPanel.statusText.setText("Drop files to upload...");
            console.log("dragover event");
        });

        this.dropzone.on('dragleave', function (evt) {
            console.log("dragleave event");
            this.uploadPanel.statusText.setText("");
        });

        this.dropzone.on('addedfile', function (file) {
            console.log("addedfile event");
        });

        this.dropzone.on('processing', function (file) {
            console.log("processing event: ", file);
            var cwd = this.uploadPanel.getWorkingDirectory('cwd');
            if (cwd)
            {
                // Overwrite if explicitly set (in confirmation by user) or if we're uploading multiple files.
                var overwrite = file.overwrite || this.files.length > 1;

                var uri = this.uploadPanel.concatPaths(cwd, file.fullPath ? file.fullPath : file.name);

                // Save the original uri for use in the 'transfercomplete' event
                file.uri = this.uploadPanel.fileSystem.getURI(uri);
                this.options.url = file.uri + '?overwrite=' + (overwrite ? 'T' : 'F');
            }
        });

        this.dropzone.on('uploadprogress', function (file, progress, bytesSent) {
            console.log("uploadprogress event: ", file, progress);
        });

        this.dropzone.on('totaluploadprogress', function (progress, totalBytes, totalBytesSent) {
            console.log("totaluploadprogress event: ", progress, totalBytes, totalBytesSent);
            if (progress == 100 && totalBytes == 0 && totalBytesSent == 0) {
                // Dropzone is telling us all transfers are complete
                this.uploadPanel.hideProgressBar();
            } else {
                this.uploadPanel.showProgressBar();
                this.uploadPanel.progressBar.updateProgress(progress/100);
            }
        });

        this.dropzone.on('sending', function (file, xhr, formData) {
            console.log("sending event: ", file);
            this.uploadPanel.setBusy(true);
            this.uploadPanel.statusText.setText('Uploading ' + file.name + '...');
        });

        this.dropzone.on('success', function (file, response, evt) {
            console.log("success event: ", file, response);

            // success, bail early
            if (response === "")
            {
                this.uploadPanel.statusText.setText('Uploaded ' + file.name + ' successfully.');
                return;
            }

            if (response && Ext4.isString(response) && response.indexOf('<status>HTTP/1.1 200 OK</status>') > -1)
            {
//                // UNDONE: Should read status from the xml response instead of just looking for <status>
//                var xhr = evt.target;
//                var reader = new Ext4.data.reader.Xml({
//                    record : 'response',
//                    root : 'multistatus',
//                    model : 'File.data.webdav.XMLResponse'
//                });
//
//                var results = reader.read(xhr);
//                if (results.success && results.count == 1) {
//                    var record = results.records[0];
//                }

                this.uploadPanel.statusText.setText('Uploaded ' + file.name + ' successfully.');
                return;
            }

            if (response && !response.success)
            {
                if (response.status == 208)
                {
                    // File exists
                    Ext4.Msg.show({
                        title : "File Conflict:",
                        msg : "There is already a file named " + file.name + ' in this location. Would you like to replace it?',
                        cls : 'data-window',
                        icon : Ext4.Msg.QUESTION,
                        buttons : Ext4.Msg.YESNO,
                        fn : function(btn) {
                            if (btn == 'yes') {
                                file.overwrite = true;
                                file.status = Dropzone.ADDED;
                                this.processFile(file);
                            }
                        },
                        scope : this
                    });
                }
                else
                {
                    file.status = Dropzone.ERROR;
                    var xhr = evt.target;
                    this.emit('error', file, response.exception, xhr);
                }
            }
            else
            {
                this.uploadPanel.statusText.setText('Uploaded ' + file.name + ' successfully.');
            }
        });

        this.dropzone.on('error', function (file, message, xhr) {
            console.log("error event: ", file, message);

            this.uploadPanel.statusText.setText('Error uploading ' + file.name + (message ? (': ' + message) : ''));
            this.uploadPanel.showErrorMsg('Error', message);
        });

        this.dropzone.on('complete', function (file) {
            console.log("complete event: ", file);
        });

        this.dropzone.on('canceled', function (file) {
            console.log("canceled event: ", file);
            this.uploadPanel.statusText.setText('Canceled upload of ' + file.name);
            this.uploadPanel.setBusy(false);
        });

        this.dropzone.on('queuecomplete', function () {
            console.log("queuecomplete event");

            this.uploadPanel.setBusy(false);
            this.uploadPanel.hideProgressBar();

            var errorFiles = [];
            var fileRecords = [];
            for (var i = 0; i < this.files.length; i++) {
                var file = this.files[i];
                if (file.status == Dropzone.SUCCESS) {
                    fileRecords.push({data: {name:file.name, id:file.uri, href:file.uri}});
                } else if (file.status == Dropzone.ERROR) {
                    errorFiles.push(file);
                }
            }

            if (fileRecords.length && errorFiles.length == 0) {
                this.uploadPanel.fireEvent('transfercomplete', {fileRecords : fileRecords});
            }

            this.removeAllFiles();
        });

    },

    getOuterPanel : function() {
        /**
         * This panel contains the radio buttons to select single/multi
         */
        var radioPanel = {
            xtype   : 'panel',
            layout  : 'form',
            width: 140,
            border : false,
            margins: '0 0 0 30',
            bodyStyle: this.bodyStyle,
            items : [{
                xtype     : 'radiogroup',
                width     : 110,
                columns   : 1,
                hideLabel : true,
                items     : [{
                    boxLabel : 'Single file',
                    name     : 'rb-file-upload-type',
                    checked  : true,
                    handler  : function(cmp, checked) {
                        if(checked){
                            this.transferApplet.setEnabled(false);
                            uploadsPanel.getLayout().setActiveItem(this.getSingleUpload());
                        }
                    },
                    scope    : this
                },{
                    boxLabel : 'Multiple files',
                    name     : 'rb-file-upload-type',
                    handler  : function(cmp, checked) {
                        if(checked){
                            uploadsPanel.getLayout().setActiveItem(this.getMultiUpload());
                            this.onMultiUpload();
                        }
                    },
                    scope    : this
                }]
            }]
        };

        /**
         * This panel contains the single/multiple upload panels
         */
        var uploadsPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'card',
            deferredRender : true,
            activeItem : 0,
            border : false,
            cls : 'single-upload-panel',
            items : [this.getSingleUpload(), this.getMultiUpload()]
        });

        var uploadsContainer = Ext4.create('Ext.container.Container', {
            layout: 'hbox',
            height: 100,
            items: [radioPanel, uploadsPanel]
        });

        var outerContainer = Ext4.create('Ext.container.Container', {
            layout: 'vbox',
            height: 100,
            items: [uploadsContainer]
        });

        return [outerContainer];
    },

    getSingleUpload : function() {

        if (this.singleUpload) {
            return this.singleUpload;
        }

        var uploadId = Ext4.id();

        this.descriptionField = Ext4.create('Ext.form.field.Text', {
            name  : 'description',
            fieldLabel : 'Description',
            labelAlign : 'right',
            width : 382,
            margin: '5 0 0 0',
            disabled : true
        });

        this.singleUpload = Ext4.create('Ext.form.Panel', {
            border : false,
            frame : false,
            bodyStyle: this.bodyStyle,
            items  : [{
                xtype: 'container',
                width: 800,
                layout: 'hbox',
                items: [{
                    xtype: 'filefield',
                    name : 'file',
                    width: 452,
                    fieldLabel: 'Choose a File',
                    labelAlign: 'right',
                    buttonText: 'Browse',
                    clearOnSubmit: false, // allows form to be resubmitted in case of file overwrite
                    listeners: {
                        render: function(f) { this.fileField = f; },
                        change: function() {
                            this.descriptionField.setDisabled(false);
                            this.descriptionField.focus();
                            Ext4.getCmp(uploadId).setDisabled(false);
                        },
                        scope : this
                    }
                },{
                    xtype: 'button',
                    id: uploadId,
                    text: 'Upload',
                    cls: 'upload-button',
                    disabled: true,
                    handler: this.submitFileUploadForm,
                    scope : this
                }]
            }, this.descriptionField]
        });

        return this.singleUpload;
    },


    getMultiUpload: function() {
        if(this.multiUpload){
            return this.multiUpload;
        }

        var testJavaHtml =  '<span id="testJavaLink">[<a target=_blank href="http://www.java.com/en/download/testjava.jsp">test java plugin</a>]</span>';

        var helpLinkHtml =  '[<a class="help-link" href="javascript:void(0);">upload help</a>]';

        var loadingImageSrc = LABKEY.contextPath + "/" + LABKEY.extJsRoot + "/resources/images/default/shared/large-loading.gif";

        var buttonPanel = Ext4.create('Ext.panel.Panel', {
            border: false,
            layout: 'vbox',
            width: 135,
            bodyPadding: '0 5 0 5',
            bodyStyle: this.bodyStyle,
            defaults: {
                width: 125,
                margins: '0 0 3 0'
            },
            items: [
                {xtype:'button', text: 'Choose File', handler: function(){
                    if (this.transferApplet) {
                        var appletEl = this.transferApplet.getApplet();
                        if (appletEl){
                            appletEl.showFileChooser();
                        }
                    }
                }, scope: this},
                {xtype:'button', text:'Choose Folder', handler: function(){
                    if (this.transferApplet) {
                        var appletEl = this.transferApplet.getApplet();
                        if (appletEl){
                            appletEl.showDirectoryChooser();
                        }
                    }
                }, scope: this},
                {xtype:'button', text:'Drag and Drop', handler: function(){
                    if (this.transferApplet) {
                        var appletEl = this.transferApplet.getApplet();
                        if (appletEl){
                            appletEl.openDragAndDropWindow();
                        }
                    }
                }, scope: this}
            ]
        });

        this.appletPanel = Ext4.create('Ext.panel.Panel', {
            border: false,
            width: 90,
            height: 90,
            bodyStyle: this.bodyStyle,
            items: [{
                xtype: 'container',
                html:'<img src="' + loadingImageSrc + '"><br>Loading Java applet...'
            }]
        });

        this.appletContainer = Ext4.create('Ext.form.Panel', {
            border: false,
            margins: '0 0 0 10',
            buttonAlign: 'left',
            layout: 'hbox',
            width: 565,
            height: 90,
            bodyStyle: this.bodyStyle,
            items: [
                {
                    xtype: 'displayfield',
                    fieldLabel: 'Upload Tool' + '<p>' + testJavaHtml + '<p>' + helpLinkHtml,
                    labelSeparator: '',
                    labelWidth: 135
                },
                this.appletPanel,
                buttonPanel
            ],
            listeners: {
                afterrender: function (container) {
                    var helpLink = container.getEl().down('a.help-link');
                    if (helpLink) {
                        helpLink.on('click', this.showHelpMessage, this);
                    }
                },
                scope: this
            }
        });

        this.multiUpload = Ext4.create('Ext.panel.Panel', {
            border: false,
            items: [this.appletContainer]
        });

        return this.multiUpload;
    },

    onMultiUpload: function(){
        this.hideProgressBar();
        this.lastSummary= {info:0, success:0, file:'', pct:0};
        this.progressRecord = null;

        if (!this.transferApplet) {

            this.transferApplet = Ext4.create('File.panel.TransferApplet',{
                id: Ext4.id(),
                url: this.fileSystem.getAbsoluteBaseURL(),
                directory: '',
                text: 'initializing...',
                width: 90,
                height: 90
            });

            this.transferApplet.on('update', this.updateProgressBar, this);
            this.transferApplet.getTransfers().on("update", this.updateProgressBarRecord, this);

            // this event doesn't actually get fired
            //this.fileSystem.on('transferstarted', function(result) {this.showProgressBar();}, this);

            // Not calling fireUploadEvents as it does not seem to be necessary. It was used to detect duplicate files
            // and display warnings, but that doesn't seem to actually work anymore.
//            this.transferApplet.on('update', this.fireUploadEvents, this);

            this.transferApplet.onReady(function(){
                // Update applet state.
                this.updateAppletState(this.getWorkingDirectory('model'), this.getWorkingDirectory('path'));
                this.on('cwd', this.updateAppletState);

            }, this);

            this.appletPanel.removeAll();
            this.appletPanel.add(this.transferApplet);
        }
    },

    updateAppletState: function(record, folderLocation){

        if(!this.transferApplet || !record){
            return;
        }

        var canWrite = this.fileSystem.canWrite(record);
        var canMkdir = this.fileSystem.canMkdir(record);

        // Enable or disable applet buttons depending on permissions (canWrite and canMkDir)
        var appletFileActionButton = this.appletContainer.down('.button[text=Choose File]');
        if (appletFileActionButton)
            appletFileActionButton[canWrite?'enable':'disable']();
        appletFileActionButton = this.appletContainer.down('.button[text=Choose Folder]');
        if (appletFileActionButton)
            appletFileActionButton[canWrite?'enable':'disable']();
        appletFileActionButton = this.appletContainer.down('.button[text=Drag and Drop]');
        if (appletFileActionButton)
            appletFileActionButton[canMkdir?'enable':'disable']();

        try {
            this.transferApplet.changeWorkingDirectory(folderLocation);
            if (canWrite || canMkdir) {
                this.transferApplet.setEnabled(true);
                this.transferApplet.setAllowDirectoryUpload(canMkdir);
            } else {
                this.transferApplet.setEnabled(false);
            }
        } catch (e){
            console.error(e);
        }
    },

    getAppletStatusBar: function(){

        if (this.appletStatusBar)
            return this.appletStatusBar;

        this.progressBar = Ext4.create('Ext.ProgressBar', {
            width: 250,
            height: 25,
            border: false,
            autoRender : true,
            hidden: true
        });

        this.progressBarContainer = Ext4.create('Ext.container.Container', {
            width: 250,
            items: [this.progressBar]
        });

        this.statusText = Ext4.create('Ext.form.Label', {
            text: '',
            margins: '5 0 0 20',
            flex: 1,
            border: false
        });

        this.closeBtn = Ext4.create('Ext.button.Button', {
            iconCls: 'iconClose',
            tooltip: 'Close the file upload panel',
            style: 'background-color: transparent;',
            scope: this,
            border : false,
            handler: function() {
                this.fireEvent('closeUploadPanel');
            }
        });

        this.helpBtn = Ext4.create('Ext.button.Button', {
            iconCls: 'iconHelp',
            tooltip: 'File upload help',
            style: 'background-color: transparent;',
            allowDepress: false,
            scope: this,
            border : false,
            handler: this.showHelpMessage
        });

        this.appletStatusBar = Ext4.create('Ext.panel.Panel', {
              width: 500,
              border: false,
              height: 25,
              bodyStyle: this.bodyStyle,
              layout: 'hbox',
              items: [this.progressBarContainer, this.statusText, this.helpBtn, this.closeBtn]
        });

        return this.appletStatusBar;
    },

    showHelpMessage : function ()
    {
        var url = this.getCurrentWebdavURL();

        var msg = [
            '<p><b>TBD HELP TEXT</b></p>',
            '<p>This folder is accessible via the WebDAV URL:</p>',
            '<input style="font-family:monospace" type="text" readonly maxlength=10 size=' + url.length + ' value="' + (url) + '">',
            '<p>For more information on transferring files, please see the',
            '<a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=fileUpload">file upload</a>',
            'help documentation.</p>'
        ];

        if (!LABKEY.experimental.dragDropUpload) {
            if (LABKEY.user.isSystemAdmin) {
                msg.push('<p><b>NOTE:</b> Administrators may enable drag-and-drop file upload in the ');
                msg.push('<a target=_blank href="' + LABKEY.ActionURL.buildURL("admin", "experimentalFeatures.view") + '">experimental features</a> ');
                msg.push('page of the admin console.');
            } else {
                msg.push('<p><b>NOTE:</b> Contact your site administrator to enable experimental drag-and-drop file upload.');
            }
        }

        Ext4.Msg.show({
            title: "File Upload Help",
            msg: msg.join(' '),
            cls: "data-window",
            icon: Ext4.Msg.INFO,
            buttons: Ext4.Msg.OK
        });
    },

    updateProgressBarRecord: function(store, record){
        var state = record.get('state');
        var progress = record.get('percent' || 0) / 100;

        if(state === 0 && 0 < progress && progress < 1.0){
            this.progressRecord = record;
        } else if (state != 0 && this.progressRecord == record){
            this.progressRecord = null;
        }
    },

    updateProgressBar: function()
    {
        var record = this.progressRecord && this.progressRecord.get('state') == 0 ? this.progressRecord : null;
        var pct = record ? record.get('percent')/100 : 0;
        var file = record ? record.get('name') : '';

        var summary = this.transferApplet.getSummary();
        if (!this.isBusy()) {
            this.getEl().mask("Uploading files");
            this.setBusy(true);
        }

        if (summary.info != this.lastSummary.info)
        {
            if (summary.info == 0)
            {
                this.statusText.setText('Ready');
            }
            else
            {
                this.statusText.setText('Copying... ' + summary.info + ' file' + (summary.info > 1 ? 's' : ''));
            }
        }

        if (record)
        {
            this.showProgressBar();
            if (pct != this.lastSummary.pct || file != this.lastSummary.file)
                this.progressBar.updateProgress(pct, file);
        }
        else
        {
            this.progressBar.hide();
            if (summary.info == 0) {

                var fileRecords = [];
                Ext4.each(this.transferApplet.getTransfers().getRange(), function(rec){
                    // use the uri as the id and the href
                    fileRecords.push({data: {name: rec.get("name"), id: rec.get("uri"), href: rec.get("uri")}});
                });

                // todo : need to add file & folder conflict handling
                this.hideProgressBar();
                this.setBusy(false);
                this.fireEvent('transfercomplete', {fileRecords : fileRecords});
                this.getEl().unmask();
            }
        }

        // UNDONE: failed transfers
        this.lastSummary = summary;
        this.lastSummary.pct = pct;
        this.lastSummary.file = file;
    },

    showProgressBar : function()
    {
        if (this.progressBar)
            this.progressBar.setVisible(true);
    },

    hideProgressBar : function()
    {
        if (this.progressBar)
            this.progressBar.reset(true);
        if (this.statusText)
            this.statusText.setText('');
    },

    submitFileUploadForm : function(fb, v) {

        var cwd = this.getWorkingDirectory('cwd');

        if (cwd) {
            var form = this.singleUpload.getForm();
            var path = this.fileField.getValue();
            var i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            var name = path.substring(i+1);
            if (name.length == 0) {
                Ext4.Msg.alert('Error', 'No file selected. Please choose one or more files to upload.');
                return;
            }

            this.doPost = function(overwrite) {
                var options = {
                    method:'POST',
                    form : form,
                    url : this.fileSystem.getURI(cwd) + '?Accept=application/json&overwrite=' + (overwrite ? 'T' : 'F'),
                    name : name,
                    success : function(f, action, message) {
                        this.getEl().unmask();

                        var txt = (action.response.responseText || "").trim();
                        if (txt)
                        {
                            var response = Ext4.JSON.decode(action.response.responseText);
                            if (!response.success)
                            {
                                if (response.status == 208)
                                {
                                    Ext4.Msg.show({
                                        title : "File Conflict:",
                                        msg : "There is already a file named " + name + ' in this location. Would you like to replace it?',
                                        cls : 'data-window',
                                        icon : Ext4.Msg.QUESTION,
                                        buttons : Ext4.Msg.YESNO,
                                        fn : function(btn){
                                            if(btn == 'yes')
                                                this.doPost(true);
                                        },
                                        scope : this
                                    });
                                }
                                else
                                {
                                    this.showErrorMsg('Error', response.exception);
                                }

                                return;
                            }
                        }

                        this.singleUpload.getForm().reset();
                        this.fileField.setRawValue(null);
                        this.descriptionField.setDisabled(true);
                        this.singleUpload.down('.button[text=Upload]').setDisabled(true);

                        this.fireEvent('transfercomplete', {fileNames : [{name:name}]});
                    },
                    failure : LABKEY.Utils.displayAjaxErrorResponse,
                    scope : this
                };
                form.errorReader = new Ext4.data.reader.Xml({
                    record : 'response',
                    root : 'multistatus',
                    model : 'File.data.webdav.XMLResponse'
                });
                // set errorReader, so that handleResponse() doesn't try to eval() the XML response
                // assume that we've got a WebdavFileSystem
//                form.errorReader = this.fileSystem.transferReader;
                form.doAction(new Ext4.form.action.Submit(options));
                this.fireEvent('transferstarted');
                this.getEl().mask("Uploading " + name + '...');
            };

            this.doPost(false);
        }
    },

    changeWorkingDirectory : function(path, model, cwd) {
        this.workingDirectory = {path: path, model: model, cwd: cwd};
        this.fireEvent('cwd', model, path);
    },

    getWorkingDirectory : function(variable) {
        if (this.workingDirectory) {
            return this.workingDirectory[variable];
        }
        console.error('Upload: working directory not set.');
    },

    getCurrentWebdavURL : function () {
        var cwd = this.getWorkingDirectory('cwd');
        if (cwd)
            return this.fileSystem.getURI(cwd);
        else
            return this.fileSystem.getAbsoluteURL();
    },

    showErrorMsg : function(title, msg) {
        Ext4.Msg.show({
            title: title,
            msg: msg,
            cls : 'data-window',
            icon: Ext4.Msg.ERROR, buttons: Ext4.Msg.OK
        });
    }
});


