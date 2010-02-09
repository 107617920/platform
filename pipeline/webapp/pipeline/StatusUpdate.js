/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.1
 * @license Copyright (c) 2008-2009 LabKey Corporation
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

Ext.namespace('LABKEY', 'LABKEY.pipeline');

/**
  * @description Status class for retrieving pipeline status data region.
  * @class Status class for retrieving pipeline status data region.
  * @constructor
  * @param {String} url Supplies the URL for the data region update.
  */

LABKEY.pipeline.StatusUpdate = function(controller, action)
{
    //private data
    var _controller = controller;
    var _action = action;
    var _dt = null;
    var _lastUpdate = "";
    var _iDelay = 0;
    var _delays = new Array(15, 30, 60, 120);

    var shouldRefresh = function()
    {
        // Disable refresh if the user has toggled any checkboxes
        return LABKEY.DataRegions &&
               LABKEY.DataRegions["StatusFiles"] &&
               !LABKEY.DataRegions["StatusFiles"].selectionModified &&
               !LABKEY.DataRegions["StatusFiles"].isMessageShowing()
    };

    // private methods:
    var nextUpdate = function(iNext)
    {
        if (!shouldRefresh())
        {
            return;
        }
        
        _iDelay = (iNext < _delays.length ? iNext : _delays.length - 1);
        var sec = _delays[_iDelay];
        setStatusFailure(_iDelay > 0, 'Waiting ' + sec + 's...');
        _dt.delay(sec * 1000);
    };

    var setStatusFailure = function(b, msg)
    {
        var el = Ext.get('statusFailureDiv');
        if (el)
        {
            if (b)
                el.update('Failed to retrieve updated status. ' + msg);
            el.setDisplayed(b ? "" : "none");
        }
    };

    var update = function()
    {
        if (!shouldRefresh())
        {
            return;
        }

        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL(_controller, _action) + document.location.search,
            method : 'GET',
            success: onUpdateSuccess,
            failure: onUpdateFailure
        });
    };

    var onUpdateSuccess = function (response)
    {
        if (!shouldRefresh())
        {
            return;
        }

        // get div to update
        var el = Ext.get('statusRegionDiv');

        // fail if there were any problems
        if(el && response && response.responseText)
        {
            if (response.responseText.indexOf('dataregion_StatusFiles') < 0)
            {
                setStatusFailure(true, 'Refresh this page.')
            }
            else
            {
                var newText = response.responseText;
                if (_lastUpdate != newText)
                {
                    el.update(newText);
                    _lastUpdate = newText;
                    LABKEY.DataRegions["StatusFiles"]._initElements();
                }
                nextUpdate(0);
            }
        }
        else
        {
            onUpdateFailure(response);
        }
    };

    var onUpdateFailure = function(response)
    {
        // Don't show an error message if the request was canceled, as would happen if the user clicked on a link
        // while we were trying up refresh
        if (response.status != 0)
        {
            nextUpdate(_iDelay + 1);
        }
    };

    // public methods:
    /** @scope LABKEY.pipeline.StatusUpdate.prototype */
    return {
        start : function()
        {
            if (_dt == null)
                _dt = new Ext.util.DelayedTask(update);
            nextUpdate(0);
        }
    }
};

