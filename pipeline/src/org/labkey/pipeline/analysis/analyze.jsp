<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    ActionURL cancelURL = (ActionURL)HttpView.currentModel();
%>

<labkey:errors />

<script type="text/javascript">

    var allProtocols;
    var selectedFileNames;
    var taskId = LABKEY.ActionURL.getParameter("taskId");
    var path = LABKEY.ActionURL.getParameter("path");

    function getProtocolsCallback(protocols, defaultProtocolName)
    {
        var selectElement = document.getElementById("protocolSelect");
        selectElement.options[0].text = "<New Protocol>";
        allProtocols = new Object();
        for (var i = 0; i < protocols.length; i++)
        {
            selectElement.options[i + 1] = new Option(protocols[i].name, protocols[i].name, protocols[i].name == defaultProtocolName);
            allProtocols[protocols[i].name] = protocols[i];
        }
        if (changeProtocol(defaultProtocolName))
        {
            selectElement.focus();
        }
        else
        {
            document.getElementById("protocolNameInput").focus();
        }
    }

    function startAnalysis()
    {
        var config =
        {
            taskId: taskId,
            path: path,
            files: selectedFileNames,
            saveProtocol: document.getElementById("saveProtocolInput").checked,
            protocolName: document.getElementById("protocolNameInput").value,
            successCallback: function() { window.location = LABKEY.ActionURL.buildURL("project", "start.view") }
        };
        if (document.getElementById("protocolSelect").selectedIndex == 0)
        {
            config.protocolDescription = document.getElementById("protocolDescriptionInput").value; 
            config.xmlParameters = document.getElementById("xmlParametersInput").value;
        }
        LABKEY.Pipeline.startAnalysis(config);
    }

    /** @param statusInfo is either a string to be shown for all files, or an array with status information for each file */
    function showFileStatus(statusInfo, submitType)
    {
        var globalStatus = "";
        var files = [];
        if (typeof statusInfo === 'string')
        {
            files = [];
            globalStatus = statusInfo;
        }
        else if (statusInfo && statusInfo.length)
        {
            // Assume it's an array
            files = statusInfo;
        }
        var status = "";
        for (var i = 0; i < selectedFileNames.length; i++)
        {
            status = status + selectedFileNames[i];
            for (var j = 0; j < files.length; j++)
            {
                if (selectedFileNames[i] == files[j].name)
                {
                    if (files[j].status)
                    {
                        status += " <b>(" + files[j].status + ")</b>";
                    }
                    break;
                }
            }
            status += " " + globalStatus + "<br/>";
        }
        document.getElementById("fileStatus").innerHTML = status;
        if (!submitType)
        {
            document.getElementById("submitButton").style.display = "none";
        }
        else
        {
            document.getElementById("submitButton").innerHTML = submitType;
            document.getElementById("submitButton").style.display = "";
        }
    }

    /** @return true if an existing, saved protocol is selected */
    function changeProtocol(selectedProtocolName)
    {
        var selectedProtocol = allProtocols[selectedProtocolName];
        var inputs = Ext.DomQuery.select("[@class=protocol-input]");
        var disabledState;
        if (selectedProtocol)
        {
            disabledState = true;
            document.getElementById("protocolNameInput").value = selectedProtocol.name;
            document.getElementById("protocolDescriptionInput").value = selectedProtocol.description;
            document.getElementById("xmlParametersInput").value = selectedProtocol.xmlParameters;
            showFileStatus("<em>(Refreshing status)</em>");
            LABKEY.Pipeline.getFileStatus(
            {
                taskId: taskId,
                path: path,
                files: selectedFileNames,
                successCallback: showFileStatus,
                protocolName: selectedProtocolName
            });
        }
        else
        {
            disabledState = false;
            document.getElementById("protocolNameInput").value = "";
            document.getElementById("protocolDescriptionInput").value = "";
            document.getElementById("xmlParametersInput").value = "<?xml version=\"1.0\"?>\n" +
                        "<bioml>\n" +
                        "<!-- Override default parameters here. -->\n" +
                        "</bioml>";
            showFileStatus("", "Analyze");
        }
        for (var i = 0; i < inputs.length; i++)
        {
            inputs[i].disabled = disabledState;
        }
        return disabledState;
    }

    Ext.onReady(function()
    {
        selectedFileNames = LABKEY.ActionURL.getParameterArray("file");
        if (!selectedFileNames || selectedFileNames.length == 0)
        {
            alert("No files have been selected for analysis. Return to the pipeline to select them.");
            window.location = "<%= PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(HttpView.currentContext().getContainer(), null) %>";
        }
        showFileStatus("<em>(Refreshing status)</em>");
        LABKEY.Pipeline.getProtocols({ taskId: taskId, successCallback: getProtocolsCallback });
    });
</script>

    Choose an existing protocol or define a new one.<br />

<form id="analysis_form">
<table>
    <tr>
        <td class='labkey-form-label'>Analysis Protocol:</td>
        <td>
            <select id="protocolSelect" name="protocol" onchange="changeProtocol(this.options[this.selectedIndex].value);">
                <option>&lt;Loading...&gt;</option>
            </select>
        </td>
    </tr>

    <tr>
        <td class='labkey-form-label'>Protocol Name:</td>
        <td>
            <input disabled="true" id="protocolNameInput" class="protocol-input" type="text" name="protocolName" size="40" />
        </td>
    </tr>
    <tr>
        <td class='labkey-form-label'>Protocol Description:</td>
        <td><textarea disabled="true" id="protocolDescriptionInput" class="protocol-input" style="width: 100%;" name="protocolDescription" cols="150" rows="4"></textarea></td>
    </tr>

    <tr>
        <td class='labkey-form-label'>File(s):</td>
        <td id="fileStatus" />
    </tr>
    <tr id="parametersRow">
        <td class='labkey-form-label'>Parameters:</td>
        <td>
            <textarea disabled="true" id="xmlParametersInput" class="protocol-input" style="width: 100%;" name="xmlParameters" cols="150" rows="15"></textarea>
        </td>
    </tr>
    <tr>
        <td/>
        <td>
            <input type="checkbox" class="protocol-input" disabled="true" id="saveProtocolInput" name="saveProtocol" checked="true" /> Save protocol for future use
        </td>
    </tr>
    <tr>
        <td/>
        <td>
            <labkey:button text="Analyze" id="submitButton" onclick="startAnalysis(); return false;" />
            <labkey:button text="Cancel" href="<%= cancelURL %>"/>
        </td>
    </tr>
</table>
</form>
