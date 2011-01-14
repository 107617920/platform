<%
/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.pipeline.PipeRoot"%>
<%@ page import="org.labkey.api.pipeline.PipelineService"%>
<%@ page import="org.labkey.api.reports.report.RReportDescriptor"%>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.view.RunRReportView" %>
<%@ page import="org.labkey.api.reports.report.view.RunReportView" %>
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.TabStripView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.reports.report.ScriptReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.ReportDesignerSessionCache" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ScriptReportBean> me = (JspView<ScriptReportBean>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    Container c = context.getContainer();
    ScriptReportBean bean = me.getModelBean();

    // the url for the execute script button
    ActionURL executeUrl = context.cloneActionURL().replaceParameter(TabStripView.TAB_PARAM, RunReportView.TAB_VIEW).
            replaceParameter(RunReportView.CACHE_PARAM, String.valueOf(bean.getReportId()));

    boolean readOnly = bean.isReadOnly();
    boolean isAdmin = context.getContainer().hasPermission(context.getUser(), AdminPermission.class);

    PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(c);
%>

<link rel="stylesheet" href="<%=request.getContextPath()%>/_yui/build/container/assets/container.css" type="text/css"/>
<link rel="stylesheet" href="<%=request.getContextPath()%>/utils/dialogBox.css" type="text/css"/>
<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dom");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dragdrop");</script>
<script type="text/javascript">LABKEY.requiresYahoo("animation");</script>
<script type="text/javascript">LABKEY.requiresYahoo("container");</script>
<script type="text/javascript">LABKEY.requiresScript("utils/dialogBox.js");</script>
<script type="text/javascript">LABKEY.requiresScript('completion.js');</script>
<script type="text/javascript">
    var dialogHelper;

    function init()
    {
        dialogHelper = new LABKEY.widget.DialogBox("saveDialog",{width:"375px", height:"120px"});
        dialogHelper.showEvent.subscribe(function(){YAHOO.util.Dom.get('reportName').focus()}, this, true);
<%
        if (pipelineRoot == null) {
%>
        var checkBox = YAHOO.util.Dom.get('runInBackground');
        checkBox.disabled = true;
<%
        }
%>
    }
    YAHOO.util.Event.addListener(window, "load", init);

    function saveReport()
    {
        LABKEY.setSubmit(true);
        var saveDiv = YAHOO.util.Dom.get('saveDialog');
        saveDiv.style.display = "";

        document.getElementById('renderReport').action = '<%=urlProvider(ReportUrls.class).urlSaveScriptReport(c)%>';

        var reportName = YAHOO.util.Dom.get('reportName');
        if (reportName.value == null || reportName.value.length == 0)
        {
            dialogHelper.render();
            dialogHelper.center();
            dialogHelper.show();
        }
        else
        {
            document.getElementById('renderReport').submit();
        }
    }

    function doSaveReport(save)
    {
        var name = YAHOO.util.Dom.get('reportName').value.trim();
        if (save && name.length == 0)
        {
            alert("The View name cannot be blank.");
        }
        else
        {
            dialogHelper.hide();
            if (save)
            {
                document.getElementById('renderReport').submit();
            }
            else
            {
                document.getElementById('renderReport').action = '<%=bean.getRenderURL()%>';
                name.value = "";
            }
        }
    }

    function runScript()
    {
        LABKEY.setSubmit(true);
        document.getElementById('renderReport').submit();
    }

    function downloadData()
    {
        LABKEY.setSubmit(true);
        window.location = '<%=bean.getReport().getDownloadDataURL(context)%>';
        LABKEY.setSubmit(false);
    }

</script>

<labkey:errors/>

<form id="renderReport" action="<%=bean.getRenderURL()%>" method="post">
    <table class="labkey-wp">
        <tr class="labkey-wp-header"><th align="left">Script View Builder</th></tr>
        <tr><td>Create a script to be executed on the server:<br/></td></tr>
        <tr><td><a href="javascript:void(0)" onclick="javascript:downloadData()">Download input data
            <%=PageFlowUtil.helpPopup("Download input data", "LabKey Server automatically exports your chosen dataset into " +
                    "a data frame called: labkey.data. You can download it to help with the development of your R script.")%></a> <br/><br/></td></tr>
        <tr><td>
            <textarea id="script"
                      name="script"
                      <% if (readOnly){ %>readonly="true"<% } %>
                      style="width: 100%;"
                      cols="120"
                      wrap="on"
                      rows="20"><%=StringUtils.trimToEmpty(bean.getScript())%></textarea>
        </td></tr>
        <tr><td>
<%          if (!readOnly)
            {
                if (bean.getRenderURL() == null)
                    out.println(PageFlowUtil.generateButton("Execute Script", "javascript:void(0)", "javascript:switchTab('" + executeUrl.getLocalURIString() + "', saveChanges)"));
                else
                    out.println(PageFlowUtil.generateButton("Execute Script", "javascript:void(0)", "javascript:runScript()"));
                if (!context.getUser().isGuest())
                    out.println(PageFlowUtil.generateButton("Save View", "javascript:void(0)", "javascript:saveReport()"));
            }
%>
        </td></tr>
<%
    if (!readOnly)
    {
        if (isAdmin)
            out.println("<tr><td><input type=\"checkbox\" name=\"shareReport\" " + (bean.isShareReport() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Make this view available to all users.</td></tr>");
        out.println("<tr><td><input type=\"checkbox\" id=\"runInBackground\" name=\"" + RReportDescriptor.Prop.runInBackground.name() + "\" " + (bean.isRunInBackground() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Run this view in the background as a pipeline job.</td></tr>");
        if (isAdmin)
        {
            out.print("<tr><td><input type=\"checkbox\" name=\"inheritable\" " + (bean.isInheritable() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Make this view available in child folders.");
            out.print(PageFlowUtil.helpPopup("Available in child folders", "If this check box is selected, this view will be available in data grids of child folders " +
                "where the schema and table are the same as this data grid."));
            out.println("</td></tr>");
        }
    }
%>
    </table>
    <input type="hidden" name="<%=ReportDescriptor.Prop.reportType%>" value="<%=bean.getReportType()%>">
    <input type="hidden" name="queryName" value="<%=StringUtils.trimToEmpty(bean.getQueryName())%>">
    <input type="hidden" name="viewName" value="<%=StringUtils.trimToEmpty(bean.getViewName())%>">
    <input type="hidden" name="schemaName" value="<%=StringUtils.trimToEmpty(bean.getSchemaName())%>">
    <input type="hidden" name="dataRegionName" value="<%=StringUtils.trimToEmpty(bean.getDataRegionName())%>">
    <input type="hidden" name="redirectUrl" value="<%=h(bean.getRedirectUrl())%>">
    <% if (null != bean.getReportId()) { %>
        <input type="hidden" name="reportId" value="<%=bean.getReportId()%>">
    <% } %>
    <input type="hidden" name="cacheKey" value="<%=org.labkey.api.reports.report.view.ReportDesignerSessionCache.getReportCacheKey(bean.getReportId(), c)%>">
    <input type="hidden" name="showDebug" value="true">
    <input type="hidden" name="<%=ScriptReportDescriptor.Prop.scriptExtension%>" value="<%=StringUtils.trimToEmpty(bean.getScriptExtension())%>">

    <div style="display:none;" id="saveDialog">
        <div class="hd">Save View</div>
        <div class="bd">
            <table>
                <tr><td>View name:</td></tr>
                <tr><td width="275"><input id="reportName" name="reportName" style="width: 100%;" value="<%=StringUtils.trimToEmpty(bean.getReportName())%>"></td></tr>
                <tr><td>&nbsp;</td></tr>
                <tr><td>
                    <%=PageFlowUtil.generateButton("Save", "javascript:void(0)", "javascript:doSaveReport(true)")%>
                    <%=PageFlowUtil.generateButton("Cancel", "javascript:void(0)", "javascript:doSaveReport(false)")%>
            </table>
        </div>
    </div>
<!--
</form>
-->

<%!
    public boolean isScriptIncluded(ReportIdentifier id, List<String> includedScripts) {
        return includedScripts.contains(String.valueOf(id));
    }
%>

<script type="text/javascript">
    // javascript to help manage report dirty state across tabs and across views.
    //
    function saveChanges(destinationURL)
    {
        LABKEY.setSubmit(true);
        if (LABKEY.isDirty() || pageDirty())
        {
            var form = document.getElementById('renderReport');
            var length = form.elements.length;
            var pairs = [];
            var regexp = /%20/g;

            // urlencode the form data for the post
            for (var i=0; i < length; i++)
            {
                var e = form.elements[i];
                if (e.name && !(e.type=="radio"&&e.selected==false) && !(e.type=="checkbox"&&e.checked==false))
                {
                    if (e.value)
                    {
                        var pair = encodeURIComponent(e.name).replace(regexp, "+") + '=' +
                                   encodeURIComponent(e.value).replace(regexp, "+");
                        pairs.push(pair);
                    }
                }
            }
            var ajax = new AJAXInteraction(pairs.join('&'), destinationURL);
            ajax.send();
        }
        else
        {
            if (destinationURL)
                window.location = destinationURL;
        }
    }

    function AJAXInteraction(url, redirectURL)
    {
        this.url = url;
        var redirectURL = redirectURL;
        var req = init();
        req.onreadystatechange = processRequest;

        function init()
        {
            if (window.XMLHttpRequest)
                return new XMLHttpRequest();
            else if (window.ActiveXObject)
                return new ActiveXObject("Microsoft.XMLHTTP");
        }

        this.send = function()
        {
            req.open("POST", "<%=urlProvider(ReportUrls.class).urlSaveScriptReportState(c).getLocalURIString()%>");
            req.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            req.send(url);
        };

        function processRequest()
        {
            if (req.readyState == 4 && req.status == 200)
            {
                if (redirectURL)
                    window.location = redirectURL;
            }
        }
    }

    var origScript = byId("script").value;
    function pageDirty()
    {
        var script = byId("script");
        if (script && origScript != script.value)
            return true;
        return false;
    }
</script>
