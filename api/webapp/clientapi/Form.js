/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 9.1
 * @license Copyright (c) 2009 LabKey Corporation
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

/**
 * Constructs a LABKEY.Form object. This object may be used to track the dirty state of an HTML form
 * and warn the user about unsaved changes if the user leaves the page before saving.
 * @class A utility class for tracking HTML form dirty state and warning the user about unsaved changes.
 * @constructor
 * @param {Object} config A configuration ojbect containing the following properties:
 * @param {Element} config.formElement A reference to the HTML form element you want to track.
 * @param {String} [config.warningMessage] An optional warning message to display if the user
 * attempts to leave the page while the form is still dirty. If not supplied, a default message is used.
 * @param {Boolean} [config.showWarningMessage] Set to false to stop this from displaying the warning message
 * when the user attempts to leave the page while the form is still dirty. If you only want to use this class
 * to track the dirty state only and not to warn on unload, set this to false.
 * @example
 * &lt;form id='form-preferences'&gt; ... &lt;/form&gt;
 * &lt;script type="text/javascript"&gt;
    var _form = new LABKEY.Form({
        formElement: 'form-preferences'
    });
&lt;/script&gt;

 */
LABKEY.Form = function(config) {
    //if the config doesn't have a property named formElement
    //try treating it as a form element
    if(!config.formElement)
        config = {formElement: Ext.get(config)};

    if(!config.formElement)
        throw "Invalid config passed to LABKEY.Form constructor! Your config object should have a property named formElement which is the id of your form.";
    
    //save the form element
    this.formElement = Ext.get(config.formElement);
    this.warningMessage = config.warningMessage || "Your changes have not yet been saved. Choose Cancel to stay on the page and save your changes.";
    this._isDirty = false;

    //register for onchange events on all input elements
    var elems = this.formElement.dom.elements;
    for(var idx = 0; idx < elems.length; ++idx)
    {
        Ext.EventManager.on(elems[idx], "change", this.onElemChange, this);
    }

    Ext.EventManager.on(this.formElement, "submit", function(){
        this.setClean();
    }, this);

    if(false !== config.showWarningMessage)
    {
        Ext.EventManager.on(window, "beforeunload", function(evt){
            if(this.isDirty())
                evt.browserEvent.returnValue = this.warningMessage;
        }, this);
    }
};

/**
 * Returns true if the form is currently dirty, false otherwise.
 */
LABKEY.Form.prototype.isDirty = function() {
    return this._isDirty;
};

/**
 * Sets the form's dirty state to clean
 */
LABKEY.Form.prototype.setClean = function() {
    this._isDirty = false;
};

/**
 * Sets the form's dirty state to dirty
 */
LABKEY.Form.prototype.setDirty = function() {
    this._isDirty = true;
};

/**
 * @private
 */
LABKEY.Form.prototype.onElemChange = function() {
    this._isDirty = true;
};
