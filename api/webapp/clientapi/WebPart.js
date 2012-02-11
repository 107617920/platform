/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2012 LabKey Corporation
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
 * @description WebPart class to render a web part into an existing page element.  If you wish to render a Query web part, please
 * use the {@link LABKEY.QueryWebPart} class instead.
 * @class Web Part class to render a web part into an existing page element.  If you wish to render a Query web part, please
 * use the {@link LABKEY.QueryWebPart} class instead.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=webPartConfig">
					        Web Part Configuration Properties</a></li>
 *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=webPartInventoryWikiSimple">
					        List of LabKey Web Parts</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
 *
 * @constructor
 * @param {Object} config Describes configuration properties for this class.  
 * @param {String} config.partName Name of the web part ("Wiki", "Lists", etc.), as listed in the
					"Add Web Part" drop-down menu on the portal page of the container.
 * @param {String} config.renderTo ID of the element in which the web part should be rendered. 
					Typically this is a div.
 * @param {String} [config.frame] The type of frame to wrap around the web part's content. This can
                    be any one of the following:
                    <ul>
                        <li><b>portal</b>: (Default) Includes the standard portal frame around the web part content
                        <li><b>title</b>: Includes a lighter frame around the web part content (title with line underneath)
                        <li><b>left-nav</b>: Similar to the title frame, but enforces a thinner width.
                        <li><b>div</b>: Includes the content in a simple &lt;div&gt; tag. Use the config.bodyClass parameter to set a CSS class for the div.
                        <li><b>dialog</b>: Includes the content in a frame that looks similar to a dialog box.
                        <li><b>none</b>: Includes no frame around the web part content
                    </ul>
 * @param {String} [config.bodyClass] The name of a CSS class available in the current page. This class name will be applied to the tag that
					contains the web part's body content.
 * @param {String} [config.title] Overrides the web part's default title.
 *                  Note that titles are not displayed when config.frame is set to "none".
 * @param {String} [config.titleHref] Overrides the hyperlink href around the web part's title.
 *                  Note that titles are not displayed when config.frame is set to "none".
 * @param {Object} [config.partConfig] Object containing name/value pairs that will be sent to the server as configuration
 *               	parameters for the web part. Each web part defines its own set of config parameters. See the 
 * 					<a href= https://www.labkey.org/wiki/home/Documentation/page.view?name=webPartConfig>
					Web Part Configuration Properties</a> page for further information on these name/value pairs.
 * @param {Function} [config.success] Callback function that will be executed after the web part content as been inserted into the page.
 * @param {Function} [config.failure] Callback function that will be executed if an error occurs. This function
 *                  should have two parameters: response and partConfig. The response parameter is the XMLHttpResponse
 *                  object, which can be used to determine the error code and obtain the error text if desired.
 *                  The partConfig parameter will contain all the parameters sent to the server.
 * @param {String} [config.containerPath] The container path in which this web part is defined. If not supplied,
 *                  the current container path will be used.
 * @param {Object} [config.scope] A scope object to use when calling the successCallback or errorCallback functions (defaults to this).
 * @example Example for a Wiki web part:<pre name="code" class="xml">
 &lt;div id='wikiTestDiv'/&gt;
 &lt;script type="text/javascript"&gt;
	var wikiWebPartRenderer = new LABKEY.WebPart({
		partName: 'Wiki',
		renderTo: 'wikiTestDiv',
		partConfig: {name: 'home'}
		})
	wikiWebPartRenderer.render();
 &lt;/script&gt;  </pre></code>
 * @example Example for a Report web part, from the Reagent Request <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=reagentRequestConfirmation">Tutorial</a> and <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=Confirmation">Demo</a>: <pre name="code" class="xml">
  &lt;div id='reportDiv'&gt;Loading...&lt;/div&gt;
  &lt;script type="text/javascript"&gt;
     // This snippet draws a histogram of the current user's requests.
     // A partConfig parameter identifies the R report ('db:151')
     // used to draw the histogram.  partConfig also
     // supplies a filter ('query.UerID~eq') to ensure that
     // R uses data for only the current user.  Lastly, partConfig
     // provides R with an arbitrary URL parameter ('displayName')
     // to indicate the displayName of the user.

     var reportWebPartRenderer = new LABKEY.WebPart({
         partName: 'Report',
         renderTo: 'reportDiv',
         containerPath: '/home/Study/demo/guestaccess',
         frame: 'title',
         partConfig: {
                     title: 'Reagent Request Histogram',
                     reportId: 'db:151',
                     showSection: 'histogram',
                     'query.UserID~eq' : LABKEY.Security.currentUser.id,
                     displayName: LABKEY.Security.currentUser.displayName
     }});
     reportWebPartRenderer.render();
  &lt;/script&gt; </pre></code>
 */
LABKEY.WebPart = function(config)
{
    //private data
    var _renderTo = config.renderTo;
    var _partName = config.partName;
    var _frame = config.frame;
    var _bodyClass = config.bodyClass;
    var _title = config.title;
    var _titleHref = config.titleHref;
    var _partConfig = config.partConfig;
    var _errorCallback = LABKEY.Utils.getOnFailure(config);
    var _success = LABKEY.Utils.getOnSuccess(config);
    var _containerPath = config.containerPath;
    var _scope = config.scope || this;

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
    };

    var renderPart = function(response, partConfig)
    {
        // beforeRender callback to allow for editing of the response
        // if 'false' is returned then rendering will be stopped.
        if(partConfig.params.beforeRender)
        {
            var r = partConfig.params.beforeRender.call(_scope, response);
            if (r === false)
                return;
            else if (r)
                response = r;
        }

        // render the part inside the target element
        if(_renderTo)
        {
            var targetElem = Ext.get(_renderTo);
            if(targetElem)
            {
                targetElem.update(response.responseText, true); //execute scripts
                if(_success)
                    _success.call(_scope);
            }
            else
                Ext.Msg.alert("Rendering Error", "The element '" + _renderTo + "' does not exist in the document!");
        }
        else
            Ext.Msg.alert("Rendering Error", "The target element name was not set!");
    };

    // public methods:
    /** @scope LABKEY.WebPart.prototype */
    return {
	  /**
 	  *   Renders the WebPart to the div element specified in the configuration settings.
       * @returns A transaction id for the async request that can be used to cancel the request
       * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.Ajax" target="_blank">Ext.Ajax.abort()</a>).
	  */
        render : function()
        {
            if(!_partConfig)
                _partConfig = {};

            _partConfig["webpart.name"] = _partName;
            if(_frame)
                _partConfig["webpart.frame"] = _frame;
            if(_bodyClass)
                _partConfig["webpart.bodyClass"] = _bodyClass;
            if(_title)
                _partConfig["webpart.title"] = _title;
            if(_titleHref)
                _partConfig["webpart.titleHref"] = _titleHref;
            _partConfig.returnURL = encodeURI(_partConfig.returnURL || (window.location.pathname + window.location.search));

            if(!_errorCallback)
                _errorCallback = handleLoadError;

            //forward query string parameters
            //(for Query web parts)
            Ext.applyIf(_partConfig, LABKEY.ActionURL.getParameters());

            //Ext uses a param called _dc to defeat caching, and it may be
            //on the URL if the Query web part has done a sort or filter
            //strip it if it's there so it's not included twice (Ext always appends one)
            delete _partConfig["_dc"];

            return Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL("project", "getWebPart", _containerPath),
                success: renderPart,
                failure: _errorCallback,
                method: 'GET',
                params: _partConfig
            });
        }
    };
};

