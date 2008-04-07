/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:support@labkey.com">support@labkey.com</a>)
 * @version 8.1
 * @license Copyright (c) 2008 LabKey Software Foundation
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
 * @description WebPart class to render a web part into an existing page element.
 * @class Web Part class to render a web part into an existing page element.
 * @constructor
 * @param {Object} config Describes configuration properties for this class.  
 * @param {String} config.partName Name of the web part ("Wiki", "Lists", etc.), as listed in the
					"Add Web Part" drop-down menu on the portal page of the container.
 * @param {String} config.renderTo ID of the element in which the web part should be rendered. 
					Typically this is a div.
 * @param {Object} [config.partConfig] Object containing name/value pairs that will be sent to the server as configuration
 *               	parameters for the web part. Each web part defines its own set of config parameters. See the 
 * 					<a href= https://www.labkey.org/wiki/home/Documentation/page.view?name=webPartConfig>
					Web Part Configuration Properties</a> page for further information on these name/value pairs.
 * @param {Function} [config.errorCallback] Callback function that will be executed if an error occurs. This function
 *                  should have two parameters: response and partConfig. The response parameter is the XMLHttpResponse
 *                  object, which can be used to determine the error code and obtain the error text if desired.
 *                  The partConfig parameter will contain all the parameters sent to the server.
 * @example Examples: <pre name="code" class="xml">For a Wiki Web Part:
 &lt;script type="text/javascript"&gt;
	LABKEY.requiresClientAPI();
 &lt;/script&gt;
 &lt;div id='wikiTestDiv'/&gt;
 &lt;script type="text/javascript"&gt;
	var wikiWebPartRenderer = new LABKEY.WebPart({
		partName: 'Wiki',
		renderTo: 'wikiTestDiv',
		partConfig: {name: 'home'}
		})
	wikiWebPartRenderer.render();
 &lt;/script&gt;
 
 For a Query Web Part:
 &lt;script type="text/javascript"&gt;
	LABKEY.requiresClientAPI();
 &lt;/script&gt;
 &lt;div id='queryTestDiv'/&gt;
 &lt;script type="text/javascript"&gt;
	var queryWebPartRenderer = new LABKEY.WebPart({
		partName: 'Query', 
		renderTo: 'queryTestDiv', 
		partConfig: { 
				title: 'Webpart Title', 
				schemaName: 'lists', 
				queryName: 'People' 
		}}); 
	queryWebPartRenderer.render(); 
 &lt;/script&gt; </pre></code>
 */
LABKEY.WebPart = function(config)
{
    //private data
    var _renderTo = config.renderTo;
    var _partName = config.partName;
    var _partConfig = config.partConfig;
    var _errorCallback = config.errorCallback;

    //validate config
    if(!_partName)
    {
        Ext.Msg.alert("Configuration Error", "You must supply the name of the desired web part in the partName config property.");
        return;
    }
    if(!_renderTo)
    {
        Ext.Msg.alert("Configuration Error", "You must supply the name of the target element in the renderTo config property.");
        return;
    }

    // private methods:
    var handleLoadError = function(response, partConfig)
    {
        var msg = "Error getting the web part: ";
        if(response.status >= 500 && response.status < 600)
        {
            var json = Ext.util.JSON.decode(response.responseText);
            if(json)
                msg += json.exception;
        }
        else
            msg += response.statusText;

        Ext.Msg.alert("Error", msg);
    }

    var renderPart = function(response, partConfig)
    {
        // render the part inside the target element
        if(_renderTo)
        {
            var targetElem = document.getElementById(_renderTo);
            if(targetElem)
                targetElem.innerHTML = response.responseText;
            else
                Ext.Msg.alert("Rendering Error", "The element '" + _renderTo + "' does not exist in the document!");
        }
        else
            Ext.Msg.alert("Rendering Error", "The target element name was not set!");
    }

    // public methods:
    /** @scope LABKEY.WebPart.prototype */
    return {
	  /**
 	  *   Renders the WebPart to the div element specified in the configuration settings.
	  */
        render : function()
        {
            if(!_partConfig)
                _partConfig = {};

            _partConfig["webpart.name"] = _partName;
            
            if(!_errorCallback)
                _errorCallback = handleLoadError;

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL("project", "getWebPart"),
                success: renderPart,
                failure: _errorCallback,
                method: 'GET',
                params: _partConfig
            })
        }
    }
};
