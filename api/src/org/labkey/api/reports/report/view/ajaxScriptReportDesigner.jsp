<%
/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.report.JavaScriptReport" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.ScriptReport" %>
<%@ page import="org.labkey.api.reports.report.view.AjaxScriptReportView.Mode" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean" %>
<%@ page import="org.labkey.api.security.roles.ProjectAdminRole" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.thumbnail.DynamicThumbnailProvider" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("clientapi/ext4"));
      resources.add(ClientDependency.fromFilePath("scriptreporteditor"));
      return resources;
  }
%>
<%
    JspView<ScriptReportBean> me = (JspView<ScriptReportBean>)HttpView.currentView();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    User user = getUser();
    ScriptReportBean bean = me.getModelBean();
    ScriptReport report = (ScriptReport) bean.getReport(ctx);
//    List<Report> sharedReports = report.getAvailableSharedScripts(ctx, bean);
    List<String> includedReports = bean.getIncludedReports();
    String helpHtml = report.getDesignerHelpHtml();
    boolean readOnly = bean.isReadOnly() || !report.canEdit(user, c);
    Mode mode = bean.getMode();
//    boolean sourceAndHelp = mode.showSourceAndHelp(user) || bean.isSourceTabVisible();

    // a report is inherited if it has been shared from a parent (or shared) folder
    boolean inherited = report.getReportId() != null ? report.getDescriptor().isInherited(c) : false;
    StringBuilder inheritedWarningMsg = new StringBuilder();
    boolean isSharedFolder = c.equals(ContainerManager.getSharedContainer());

    if (inherited)
    {
        Container srcContainer = ContainerManager.getForId(report.getContainerId());
        ActionURL url = null;
        if (srcContainer != null)
            url = ctx.cloneActionURL().setContainer(srcContainer);

        inheritedWarningMsg.append("<div class='labkey-warning-messages'>This view has been made available from a different ");
        if (url != null)
            inheritedWarningMsg.append("<a href='").append(url.getLocalURIString()).append("'>folder</a>");
        else
            inheritedWarningMsg.append("folder");

        inheritedWarningMsg.append(" and cannot be edited here. The view can only be edited from the ");
        if (url != null)
            inheritedWarningMsg.append("<a href='").append(url.getLocalURIString()).append("'>folder</a>");
        else
            inheritedWarningMsg.append("folder");
        inheritedWarningMsg.append(" it was created in.</div>");
    }

    // Mode determines whether we need unique IDs on all the HTML elements
//    String uid = mode.getUniqueID();
//    String scriptId = "script" + uid;
//    String viewDivId = "viewDiv" + uid;

    String knitrFormat = bean.getKnitrFormat() != null ? bean.getKnitrFormat() : "None";
    boolean useGetDataApi = report.getReportId() == null || bean.isUseGetDataApi();

    ActionURL saveURL = urlProvider(ReportUrls.class).urlAjaxSaveScriptReport(c);
    ActionURL initialViewURL = urlProvider(ReportUrls.class).urlViewScriptReport(c);
    ActionURL baseViewURL = initialViewURL.clone();
    List<Pair<String, String>> params = getActionURL().getParameters();

    // Initial view URL uses all parameters
    initialViewURL.addParameters(params);

    // Base view URL strips off sort and filter parameters (we'll get them from the data tab)
    for (Pair<String, String> pair : params)
    {
        String name = pair.getKey();

        if (name.equals(bean.getDataRegionName() + ".sort"))
            continue;

        if (name.startsWith(bean.getDataRegionName() + ".") && name.contains("~"))
            continue;

        baseViewURL.addParameter(name, pair.getValue());
    }

    initialViewURL.replaceParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId()));
    baseViewURL.replaceParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId()));

    String renderId = "report-design-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    ObjectMapper jsonMapper = new ObjectMapper();

    List<Map<String, Object>> sharedScripts = new ArrayList<Map<String, Object>>();
    for (Report r : report.getAvailableSharedScripts(ctx, bean))
    {
        Map<String, Object> script = new HashMap<String, Object>();
        ReportDescriptor desc = r.getDescriptor();

        script.put("name", desc.getReportName());
        script.put("reportId", String.valueOf(desc.getReportId()));
        script.put("included", includedReports.contains(desc.getReportId().toString()));

        sharedScripts.add(script);
    }

    // TODO, add an action to get this information
    Map<String, Object> reportConfig = new HashMap<String, Object>();

    reportConfig.put("schemaName", bean.getSchemaName());
    reportConfig.put("queryName", bean.getQueryName());
    reportConfig.put("viewName", bean.getViewName());
    reportConfig.put("dataRegionName", bean.getDataRegionName());
    reportConfig.put("reportType", bean.getReportType());
    reportConfig.put("reportId", bean.getReportId() != null ? bean.getReportId().toString() : null);
    reportConfig.put("shareReport", bean.isShareReport());
    reportConfig.put("sourceTabVisible", bean.isSourceTabVisible());
    reportConfig.put("runInBackground", bean.isRunInBackground());
    reportConfig.put("supportsPipeline", report.supportsPipeline());

    // must be project admin (or above to to share a report to child folders
    reportConfig.put("allowInherit", user.isSiteAdmin() || ReportUtil.isInRole(user, c, ProjectAdminRole.class));
    reportConfig.put("inheritable", bean.isInheritable());
    reportConfig.put("editAreaSyntax", report.getEditAreaSyntax());

    reportConfig.put("knitrOptions", report instanceof RReport);
    reportConfig.put("knitrFormat", knitrFormat);
    reportConfig.put("scriptDependencies", bean.getScriptDependencies());

    reportConfig.put("javascriptOptions", report instanceof JavaScriptReport);
    reportConfig.put("useGetDataApi", useGetDataApi);

    reportConfig.put("thumbnailOptions", report instanceof DynamicThumbnailProvider);
    reportConfig.put("thumbnailType", bean.getThumbnailType());

    reportConfig.put("studyOptions", (report instanceof RReport) && (StudyService.get().getStudy(c) != null));
    reportConfig.put("filterParam", bean.getFilterParam());
    reportConfig.put("cached", bean.isCached());

    reportConfig.put("helpHtml", helpHtml);

%>
<labkey:scriptDependency/>

<script type="text/javascript">
    Ext4.onReady(function(){

        var panel = Ext4.create('LABKEY.ext4.ScriptReportPanel', {
            renderTo        : <%=q(renderId)%>,
            readOnly        : <%=readOnly%>,
            minHeight       : 500,
            dataRegionName  : <%=q(bean.getDataRegionName())%>,
            initialURL      : <%=q(initialViewURL.getLocalURIString())%>,
            saveURL         : <%=q(saveURL.getLocalURIString())%>,
            baseURL         : <%=q(baseViewURL.getLocalURIString())%>,
            preferSourceTab : <%=mode.preferSourceTab()%>,

            redirectUrl     : <%=q(bean.getRedirectUrl())%>,

            sharedScripts   : <%=text(jsonMapper.writeValueAsString(sharedScripts))%>,
            reportConfig    : <%=text(jsonMapper.writeValueAsString(reportConfig))%>,
            script          : <%=q(StringUtils.trimToEmpty(bean.getScript()))%>
        });

        var _resize = function(w,h) {
            LABKEY.Utils.ext4.resizeToViewport(panel, w, -1); // don't fit to height
        };

        Ext4.EventManager.onWindowResize(_resize);
    });
</script>

<div id="<%= h(renderId)%>" class="script-report-editor"></div>
