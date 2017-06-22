/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2010-2017 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */
(function($) {

    /**
     * @description Portal class to allow programmatic administration of portal pages.
     * @class Portal class to allow programmatic administration of portal pages.
     *            <p>Additional Documentation:
     *              <ul>
     *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=projects">Project and Folder Administration</a></li>
     *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=addModule">Add Web Parts</a></li>
     *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=manageWebParts">Manage Web Parts</a></li>
     *              </ul>
     *           </p>
     */
    LABKEY.Portal = new function()
    {
        // private methods:
        var MOVE_ACTION = 'move';
        var REMOVE_ACTION = 'remove';
        var MOVE_UP = 0;
        var MOVE_DOWN = 1;
        var MOVE_LEFT = 0;
        var MOVE_RIGHT = 1;

        function wrapSuccessCallback(userSuccessCallback, action, webPartId, direction)
        {
            return function(webparts, responseObj, options)
            {
                updateDOM(webparts, action, webPartId, direction);
                // after update, call the user's success function:
                if (userSuccessCallback)
                    userSuccessCallback(webparts, responseObj, options);
            }
        }

        function updateDOM(webparts, action, webPartId, direction)
        {
            // First build up a list of valid webpart table DOM IDs.  This allows us to skip webpart tables that are embedded
            // within others (as in the case of using the APIs to asynchronously render nested webparts).  This ensures that
            // we only rearrange top-level webparts.
            var validWebpartTableIds = {}, regionParts, i;
            for (var region in webparts)
            {
                if (webparts.hasOwnProperty(region))
                {
                    regionParts = webparts[region];
                    for (i = 0; i < regionParts.length; i++)
                    {
                        validWebpartTableIds['webpart_' + regionParts[i].webPartId] = true;
                    }
                }
            }

            // would be nice to use getElementsByName('webpart') here, but this isn't supported in IE.
            var tables;
            if (LABKEY.experimental.useExperimentalCoreUI)
                tables = document.getElementsByClassName('labkey-portal-container');
            else
                tables = document.getElementsByTagName('table');
            var webpartTables = [];
            var targetTable;
            var targetTableIndex;
            for (var tableIndex = 0; tableIndex < tables.length; tableIndex++)
            {
                var table = tables[tableIndex];
                // a table is possibly affected by a delete action if it's of type 'webpart' (whether it's in the current set
                // of active webparts or not). It's possibly affected by a move action only if it's in the active set of webparts.
                var possiblyAffected = ((action == REMOVE_ACTION && table.getAttribute('name') == 'webpart') || validWebpartTableIds[table.id]);
                if (possiblyAffected)
                {
                    webpartTables[webpartTables.length] = table;
                    if (table.id == 'webpart_' + webPartId)
                    {
                        targetTableIndex = webpartTables.length - 1;
                        targetTable = table;
                    }
                }
            }

            if (targetTable)
            {
                if (action == MOVE_ACTION)
                {
                    var swapTable = webpartTables[direction == MOVE_UP ? targetTableIndex - 1 : targetTableIndex + 1];
                    if (swapTable)
                    {
                        var parentEl = targetTable.parentNode;
                        var insertPoint = swapTable.nextSibling;
                        var swapPoint = targetTable.nextSibling;

                        // Need to make sure the element is actually a child before trying to remove
                        for (var node = 0; node < parentEl.childNodes.length; node++) {
                            if (parentEl.childNodes[node] === swapTable) {
                                parentEl.removeChild(targetTable);
                                parentEl.removeChild(swapTable);
                                parentEl.insertBefore(targetTable, insertPoint);
                                parentEl.insertBefore(swapTable, swapPoint);
                                break;
                            }
                        }
                    }
                }
                else if (action == REMOVE_ACTION)
                {
                    var breakEl   = targetTable.previousElementSibling;
                    var breakNode = targetTable.previousSibling;
                    targetTable.parentNode.removeChild(breakEl || breakNode); // TODO: Does not properly remove in IE7
                    targetTable.parentNode.removeChild(targetTable);
                }
            }
            updateButtons(webparts);
        }

        function updateButtons(webparts)
        {
            var moveUpImage = 'fa fa-caret-square-o-up labkey-fa-portal-nav';
            var moveUpDisabledImage = 'fa fa-caret-square-o-up x4-btn-default-toolbar-small-disabled labkey-fa-portal-nav';
            var moveDownImage = 'fa fa-caret-square-o-down labkey-fa-portal-nav';
            var moveDownDisabledImage = 'fa fa-caret-square-o-down x4-btn-default-toolbar-small-disabled labkey-fa-portal-nav';
            for (var region in webparts)
            {
                if (!webparts.hasOwnProperty(region))
                    continue;

                var regionParts = webparts[region];

                // get the webpart table elements from the DOM here; it's possible that some configured webparts may
                // not actually be in the document (if the webpartfactory returns null for security reasons, for example.)
                var confirmedWebparts = [];
                var confirmedWebpartTables = [];
                var index;
                for (index = 0; index < regionParts.length; index++)
                {
                    var testWebpart = regionParts[index];
                    var testTable = document.getElementById('webpart_' + testWebpart.webPartId);
                    if (testTable)
                    {
                        confirmedWebparts[confirmedWebparts.length] = testWebpart;
                        confirmedWebpartTables[confirmedWebpartTables.length] = testTable;
                    }
                }

                for (index = 0; index < confirmedWebpartTables.length; index++)
                {
                    var webpartTable = confirmedWebpartTables[index];
                    var disableUp = index == 0;
                    var disableDown = index == confirmedWebparts.length - 1;
                    var imgChildren = webpartTable.getElementsByClassName('labkey-fa-portal-nav');

                    for (var imageIndex = 0; imageIndex < imgChildren.length; imageIndex++)
                    {
                        var imageEl = imgChildren[imageIndex];

                        if (imageEl.className.indexOf(moveUpImage) >= 0 && disableUp)
                            imageEl.className = moveUpDisabledImage;
                        else if (imageEl.className.indexOf(moveUpDisabledImage) >= 0 && !disableUp)
                            imageEl.className = moveUpImage;

                        if (imageEl.className.indexOf(moveDownImage) >= 0 && disableDown)
                            imageEl.className = moveDownDisabledImage;
                        else if (imageEl.className.indexOf(moveDownDisabledImage) >= 0 && !disableDown)
                            imageEl.className = moveDownImage;
                    }
                }
            }
        }

        function wrapErrorCallback(userErrorCallback)
        {
            return function(exceptionObj, responseObj, options)
            {
                // after update, call the user's success function:
                return userErrorCallback(exceptionObj, responseObj, options);
            }
        }

        function defaultErrorHandler(exceptionObj, responseObj, options)
        {
            LABKEY.Utils.displayAjaxErrorResponse(responseObj, exceptionObj);
        }

        function mapIndexConfigParameters(config, action, direction)
        {
            var params = {};

            LABKEY.Utils.applyTranslated(params, config, {
                success: false,
                failure: false,
                scope: false
            });

            if (direction == MOVE_UP || direction == MOVE_DOWN)
                params.direction = direction;

            // These layered callbacks are confusing.  The outermost (second wrapper, below) de-JSONs the response, passing
            // native javascript objects to the success wrapper function defined by wrapErrorCallback (wrapSuccessCallback
            // below).  The wrapErrorCallback/wrapSuccessCallback function is responsible for updating the DOM, if necessary,
            // closing the wait dialog, and then calling the API developer's success callback function, if one exists.  If
            // no DOM update is requested, we skip the middle callback layer.
            var errorCallback = LABKEY.Utils.getOnFailure(config) || defaultErrorHandler;

            if (config.updateDOM)
                errorCallback = wrapErrorCallback(errorCallback);
            errorCallback = LABKEY.Utils.getCallbackWrapper(errorCallback, config.scope, true);

            // do the same double-wrap with the success callback as with the error callback:
            var successCallback = config.success;
            if (config.updateDOM)
                successCallback = wrapSuccessCallback(LABKEY.Utils.getOnSuccess(config), action, config.webPartId, direction);
            successCallback = LABKEY.Utils.getCallbackWrapper(successCallback, config.scope);

            return {
                params: params,
                success: successCallback,
                error: errorCallback
            };
        }

        // TODO: This should be considered 'Native UI' and be migrated away from ExtJS
        var showEditTabWindow = function(title, handler, name)
        {
            LABKEY.requiresExt4Sandbox(function() {
                Ext4.onReady(function() {
                    var nameTextField = Ext4.create('Ext.form.field.Text', {
                        xtype: 'textfield',
                        fieldLabel: 'Name',
                        labelWidth: 50,
                        width: 250,
                        name: 'tabName',
                        value: name ? name : '',
                        maxLength: 64,
                        enforceMaxLength: true,
                        enableKeyEvents: true,
                        labelSeparator: '',
                        selectOnFocus: true,
                        listeners: {
                            scope: this,
                            keypress: function(field, event){
                                if (event.getKey() == event.ENTER) {
                                    handler(nameTextField.getValue(), editTabWindow);
                                }
                            }
                        }
                    });

                    var editTabWindow = Ext4.create('Ext.window.Window', {
                        title: title,
                        closeAction: 'destroy',
                        modal: true,
                        border: false,
                        items: [{
                            xtype: 'panel',
                            border: false,
                            frame: false,
                            bodyPadding: 5,
                            items: [nameTextField]
                        }],
                        buttons: [{
                            text: 'Ok',
                            scope: this,
                            handler: function(){handler(nameTextField.getValue(), editTabWindow);}
                        },{
                            text: 'Cancel',
                            scope: this,
                            handler: function(){
                                editTabWindow.close();
                            }
                        }]
                    });

                    editTabWindow.show(false, function(){nameTextField.focus();}, this);
                });
            });
        };

        var showPermissions = function(webpartID, permission, containerPath) {

            var display = function() {
                Ext4.onReady(function() {
                    Ext4.create('LABKEY.Portal.WebPartPermissionsPanel', {
                        webPartId: webpartID,
                        permission: permission,
                        containerPath: containerPath,
                        autoShow: true
                    });
                });
            };

            var loader = function() {
                LABKEY.requiresExt4Sandbox(function() {
                    LABKEY.requiresScript('WebPartPermissionsPanel.js', display, this);
                }, this);
            };

            // Require a webpartID for any action
            if (webpartID) {
                if (LABKEY.Portal.WebPartPermissionsPanel) {
                    display();
                }
                else {
                    loader();
                }
            }
        };

        // public methods:
        /** @scope LABKEY.Portal.prototype */
        return {

            /**
             * Move an existing web part up within its portal page, identifying the web part by its unique web part ID.
             * @param config An object which contains the following configuration properties.
             * @param {String} [config.pageId] Reserved for a time when multiple portal pages are allowed per container.
             * If not provided, main portal page for the container will be queried.
             * @param {String} [config.containerPath] Specifies the container in which the web part query should be performed.
             * If not provided, the method will operate on the current container.
             * @param {Function} config.success
             Function called when the this function completes successfully.
             This function will be called with the following arguments:
             <ul>
             <li>webparts: an object with one property for each page region, generally 'body' and 'right'.  The value
             of each property is an ordered array of objects indicating the current web part configuration
             on the page.  Each object has the following properties:
             <ul>
             <li>name: the name of the web part</li>
             <li>index: the index of the web part</li>
             <li>webPartId: the unique integer ID of this web part.</li>
             </ul>
             </li>
             <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             * @param {Function} [config.failure] Function called when execution fails.
             *       This function will be called with the following arguments:
             <ul>
             <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
             <li>responseObj: The XMLHttpRequest object containing the response data.</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             */
            getWebParts : function(config)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('project', 'getWebParts', config.containerPath),
                    method : 'GET',
                    success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                    failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                    params: config
                });
            },

            /**
             * Move an existing web part up within its portal page, identifying the web part by index.
             * @param config An object which contains the following configuration properties.
             * @param {String} [config.pageId] Reserved for a time when multiple portal pages are allowed per container.
             * If not provided, main portal page for the container will be modified.
             * @param {String} [config.containerPath] Specifies the container in which the web part modification should be performed.
             * If not provided, the method will operate on the current container.
             * @param {String} config.webPartId The unique integer ID of the web part to be moved.
             * @param {Boolean} [config.updateDOM] Indicates whether the current page's DOM should be updated to reflect changes to web part layout.
             * Defaults to false.
             * @param {Function} config.success
             Function called when the this function completes successfully.
             This function will be called with the following arguments:
             <ul>
             <li>webparts: an object with one property for each page region, generally 'body' and 'right'.  The value
             of each property is an ordered array of objects indicating the current web part configuration
             on the page.  Each object has the following properties:
             <ul>
             <li>name: the name of the web part</li>
             <li>index: the index of the web part</li>
             <li>webPartId: the unique integer ID of this web part.</li>
             </ul>
             </li>
             <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             * @param {Function} [config.failure] Function called when execution fails.
             *       This function will be called with the following arguments:
             <ul>
             <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
             <li>responseObj: The XMLHttpRequest object containing the response data.</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             */
            moveWebPartUp : function(config)
            {
                var callConfig = mapIndexConfigParameters(config, MOVE_ACTION, MOVE_UP);
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('project', 'moveWebPartAsync', config.containerPath),
                    method : 'GET',
                    success: LABKEY.Utils.getOnSuccess(callConfig),
                    failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(callConfig), callConfig.scope, true),
                    params: callConfig.params
                });
            },


            /**
             * Move an existing web part down within its portal page, identifying the web part by the unique ID of the containing span.
             * This span will have name 'webpart'.
             * @param config An object which contains the following configuration properties.
             * @param {String} [config.pageId] Reserved for a time when multiple portal pages are allowed per container.
             * If not provided, main portal page for the container will be modified.
             * @param {String} [config.containerPath] Specifies the container in which the web part modification should be performed.
             * If not provided, the method will operate on the current container.
             * @param {String} config.webPartId The unique integer ID of the web part to be moved.
             * @param {Boolean} [config.updateDOM] Indicates whether the current page's DOM should be updated to reflect changes to web part layout.
             * Defaults to false.
             * @param {Function} config.success
             Function called when the this function completes successfully.
             This function will be called with the following arguments:
             <ul>
             <li>webparts: an object with one property for each page region, generally 'body' and 'right'.  The value
             of each property is an ordered array of objects indicating the current web part configuration
             on the page.  Each object has the following properties:
             <ul>
             <li>name: the name of the web part</li>
             <li>index: the index of the web part</li>
             <li>webPartId: the unique integer ID of this web part.</li>
             </ul>
             </li>
             <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             * @param {Function} [config.failure] Function called when execution fails.
             *       This function will be called with the following arguments:
             <ul>
             <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
             <li>responseObj: The XMLHttpRequest object containing the response data.</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             */
            moveWebPartDown : function(config)
            {
                var callConfig = mapIndexConfigParameters(config, MOVE_ACTION, MOVE_DOWN);
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('project', 'moveWebPartAsync', config.containerPath),
                    method : 'GET',
                    success: LABKEY.Utils.getOnSuccess(callConfig),
                    failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(callConfig), callConfig.scope, true),
                    params: callConfig.params
                });
            },
            /**
             * Remove an existing web part within its portal page.
             * @param config An object which contains the following configuration properties.
             * @param {String} [config.pageId] Reserved for a time when multiple portal pages are allowed per container.
             * If not provided, main portal page for the container will be modified.
             * @param {String} [config.containerPath] Specifies the container in which the web part modification should be performed.
             * If not provided, the method will operate on the current container.
             * @param {String} config.webPartId The unique integer ID of the web part to be moved.
             * @param {Boolean} [config.updateDOM] Indicates whether the current page's DOM should be updated to reflect changes to web part layout.
             * Defaults to false.
             * @param {Function} config.success
             Function called when the this function completes successfully.
             This function will be called with the following arguments:
             <ul>
             <li>webparts: an object with one property for each page region, generally 'body' and 'right'.  The value
             of each property is an ordered array of objects indicating the current web part configuration
             on the page.  Each object has the following properties:
             <ul>
             <li>name: the name of the web part</li>
             <li>index: the index of the web part</li>
             <li>webPartId: the unique integer ID of this web part.</li>
             </ul>
             </li>
             <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             * @param {Function} [config.failure] Function called when execution fails.
             *       This function will be called with the following arguments:
             <ul>
             <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
             <li>responseObj: The XMLHttpRequest object containing the response data.</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             */
            removeWebPart : function(config)
            {
                var callConfig = mapIndexConfigParameters(config, REMOVE_ACTION, undefined);
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('project', 'deleteWebPartAsync', config.containerPath),
                    method : 'GET',
                    success: LABKEY.Utils.getOnSuccess(callConfig),
                    failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(callConfig), callConfig.scope, true),
                    params: callConfig.params
                });
            },

            /**
             * Move a folder tab to the left.
             * @param pageId the pageId of the tab.
             * @param domId the id of the anchor tag of the tab.
             */
            moveTabLeft : function(pageId, domId)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'moveTab', LABKEY.container.path),
                    method: 'GET',
                    params: {
                        pageId: pageId,
                        direction: MOVE_LEFT
                    },
                    success: LABKEY.Utils.getCallbackWrapper(function(response, options) {
                        if(domId && response.pageIdToSwap && response.pageIdToSwap !== response.pageId) {
                            var tabAnchor = $('#' + domId)[0];
                            if (tabAnchor) {
                                $(tabAnchor.parentElement).insertBefore(tabAnchor.parentNode.previousElementSibling);
                            }
                        }
                    }, this, false),
                    failure: function(response){
                        // Currently no-op when failure occurs.
                    }
                });
            },

            /**
             * Move a folder tab to the right.
             * @param pageId the pageId of the tab.
             * @param domId the id of the anchor tag of the tab.
             */
            moveTabRight : function(pageId, domId)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'moveTab', LABKEY.container.path),
                    method: 'GET',
                    params: {
                        pageId: pageId,
                        direction: MOVE_RIGHT
                    },
                    success: LABKEY.Utils.getCallbackWrapper(function(response, options) {
                        if(domId && response.pageIdToSwap && response.pageIdToSwap !== response.pageId) {
                            var tabAnchor = $('#' + domId)[0];
                            if (tabAnchor) {
                                $(tabAnchor.parentElement).insertAfter(tabAnchor.parentNode.nextElementSibling);
                            }
                        }
                    }, this, false),
                    failure: function(response, options){
                        // Currently no-op when failure occurs.
                    }
                });
            },

            /**
             * Toggle tab edit mode. Enables or disables tab edit mode. When in tab edit mode an administrator
             * can manage tabs (i.e. change order, add, remove, etc.)
             * TODO this can be removed when we switch to useExperimentalCoreUI()
             */
            toggleTabEditMode : function()
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'toggleTabEditMode', LABKEY.container.path),
                    method: 'GET',
                    success: LABKEY.Utils.getCallbackWrapper(function(response, options){
                        var classToSearchFor = response.tabEditMode ? 'tab-edit-mode-disabled' : 'tab-edit-mode-enabled';
                        var classToReplaceWith = response.tabEditMode ? 'tab-edit-mode-enabled' : 'tab-edit-mode-disabled';
                        var tabDiv = document.getElementsByClassName(classToSearchFor)[0];

                        if (tabDiv) {
                            // Navigate to the start URL if the current active tab is also hidden.
                            if (response.startURL && tabDiv.querySelector('li.tab-nav-active.tab-nav-hidden'))
                                window.location = response.startURL;
                            else
                                tabDiv.setAttribute('class', tabDiv.getAttribute('class').replace(classToSearchFor, classToReplaceWith));
                        }
                    })
                });
            },

            /**
             * Allows an administrator to add a new portal page tab.
             */
            addTab : function()
            {
                var addTabHandler = function(name, editWindow)
                {
                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('admin', 'addTab'),
                        method: 'POST',
                        jsonData: {tabName: name},
                        success: function(response)
                        {
                            var jsonResp = LABKEY.Utils.decode(response.responseText);
                            if (jsonResp && jsonResp.success)
                            {
                                if (jsonResp.url)
                                    window.location = jsonResp.url;
                            }
                        },
                        failure: function(response)
                        {
                            var jsonResp = LABKEY.Utils.decode(response.responseText);
                            var errorMsg;
                            if (jsonResp && jsonResp.errors)
                                errorMsg = jsonResp.errors[0].message;
                            else
                                errorMsg = 'An unknown error occured. Please contact your administrator.';
                            alert(errorMsg);
                        }
                    });
                };

                if (LABKEY.experimental.isPageAdminMode) {
                    showEditTabWindow("Add Tab", addTabHandler, null);
                }
            },

            /**
             * Shows a hidden tab.
             * @param pageId the pageId of the tab.
             */
            showTab : function(pageId)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'showTab'),
                    method: 'POST',
                    jsonData: {tabPageId: pageId},
                    success: function(response)
                    {
                        var jsonResp = LABKEY.Utils.decode(response.responseText);
                        if (jsonResp && jsonResp.success)
                        {
                            if (jsonResp.url)
                                window.location = jsonResp.url;
                        }
                    },
                    failure: function(response)
                    {
                        var jsonResp = LABKEY.Utils.decode(response.responseText);
                        if (jsonResp && jsonResp.errors)
                        {
                            alert(jsonResp.errors[0].message);
                        }
                    }
                });
            },

            /**
             * Allows an administrator to rename a tab.
             * @param pageId the pageId of the tab.
             * @param domId the id of the anchor tag of the tab.
             * @param currentLabel the current label of the tab.
             */
            renameTab : function(pageId, domId, currentLabel)
            {
                var tabLinkEl = document.getElementById(domId);

                if (tabLinkEl)
                {
                    var renameHandler = function(name, editWindow)
                    {
                        LABKEY.Ajax.request({
                            url: LABKEY.ActionURL.buildURL('admin', 'renameTab'),
                            method: 'POST',
                            jsonData: {
                                tabPageId: pageId,
                                tabName: name
                            },
                            success: function(response)
                            {
                                var jsonResp = LABKEY.Utils.decode(response.responseText);
                                if (jsonResp.success)
                                    tabLinkEl.textContent = name;
                                editWindow.close();
                            },
                            failure: function(response)
                            {
                                var jsonResp = LABKEY.Utils.decode(response.responseText);
                                var errorMsg;
                                if (jsonResp.errors)
                                    errorMsg = jsonResp.errors[0].message;
                                else
                                    errorMsg = 'An unknown error occured. Please contact your administrator.';
                                LABKEY.Utils.alert('Oops', errorMsg);
                            }
                        });
                    };

                    if (LABKEY.experimental.isPageAdminMode) {
                        showEditTabWindow("Rename Tab", renameHandler, currentLabel);
                    }
                }
            },

            _showPermissions : showPermissions
        };
    };

})(jQuery);

