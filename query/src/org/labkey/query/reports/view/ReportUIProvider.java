/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.query.reports.view;

import org.labkey.api.admin.CoreUrls;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ExternalScriptEngineReport;
import org.labkey.api.reports.report.InternalScriptEngineReport;
import org.labkey.api.reports.report.JavaScriptReport;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.DefaultReportUIProvider;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewContext;
import org.labkey.query.reports.AttachmentReport;
import org.labkey.query.reports.LinkReport;
import org.labkey.query.reports.ReportsController;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 4:10:45 PM
 */
public class ReportUIProvider extends DefaultReportUIProvider
{
    private static Map<String, String> _typeToIconMap = new HashMap<String, String>();
    static {

        _typeToIconMap.put(RReport.TYPE, "/reports/r.gif");
        _typeToIconMap.put(ChartQueryReport.TYPE, "/reports/chart.gif");
        _typeToIconMap.put(JavaScriptReport.TYPE, "/reports/js.png");
        _typeToIconMap.put(AttachmentReport.TYPE, "/reports/attachment.png");
        _typeToIconMap.put(LinkReport.TYPE, "/reports/external-link.png");
        _typeToIconMap.put(QueryReport.TYPE, "/reports/grid.gif");
    }

    /**
     * Add report creation to UI's that aren't associated with a query (manage views, data views)
     */
    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<ReportService.DesignerInfo>();

        if (RReport.isEnabled())
        {
            RReportBean bean = new RReportBean();
            bean.setReportType(RReport.TYPE);
            bean.setRedirectUrl(context.getActionURL().getLocalURIString());

            DesignerInfoImpl di = new DesignerInfoImpl(RReport.TYPE, "R View", ReportUtil.getScriptReportDesignerURL(context, bean),
                    _getIconPath(RReport.TYPE));
            di.setId("create_rView");
            di.setDisabled(!ReportUtil.canCreateScript(context));

            designers.add(di);
        }

        DesignerInfoImpl attachmentDesigner = new DesignerInfoImpl(AttachmentReport.TYPE, "Attachment Report",
                ReportsController.getCreateAttachmentReportURL(context.getContainer(), context.getActionURL()),
                _getIconPath(AttachmentReport.TYPE));
        attachmentDesigner.setId("create_attachment_report");
        attachmentDesigner.setDisabled(!context.hasPermission(InsertPermission.class));
        designers.add(attachmentDesigner);

        DesignerInfoImpl linkDesigner = new DesignerInfoImpl(LinkReport.TYPE, "Link Report",
                ReportsController.getCreateLinkReportURL(context.getContainer(), context.getActionURL()),
                _getIconPath(LinkReport.TYPE));
        linkDesigner.setId("create_link_report");
        linkDesigner.setDisabled(!context.hasPermission(InsertPermission.class));
        designers.add(linkDesigner);

        DesignerInfoImpl queryDesigner = new DesignerInfoImpl(QueryReport.TYPE, "Query Report",
                ReportsController.getCreateQueryReportURL(context.getContainer(), context.getActionURL()),
                _getIconPath(QueryReport.TYPE));
        queryDesigner.setId("create_query_report");
        queryDesigner.setDisabled(!context.hasPermission(InsertPermission.class));
        designers.add(queryDesigner);

        return designers;
    }

    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<ReportService.DesignerInfo>();

        ChartDesignerBean chartBean = new ChartDesignerBean(settings);
        chartBean.setReportType(ChartQueryReport.TYPE);

        URLHelper returnUrl = settings.getReturnUrl();
        if (returnUrl == null)
            returnUrl = context.getActionURL();
        chartBean.setRedirectUrl(returnUrl.getLocalURIString());
        designers.add(new DesignerInfoImpl(ChartQueryReport.TYPE, "Chart View", "XY and Time Charts",
                ReportUtil.getChartDesignerURL(context, chartBean), _getIconPath(ChartQueryReport.TYPE), ReportService.DesignerType.VISUALIZATION));

        boolean canCreateScript = ReportUtil.canCreateScript(context);

        if (canCreateScript && RReport.isEnabled())
        {
            RReportBean rBean = new RReportBean(settings);
            rBean.setReportType(RReport.TYPE);
            rBean.setRedirectUrl(returnUrl.getLocalURIString());
            designers.add(new DesignerInfoImpl(RReport.TYPE, "R View", ReportUtil.getRReportDesignerURL(context, rBean),
                    _getIconPath(RReport.TYPE)));
        }

        ScriptEngineManager manager = ServiceRegistry.get().getService(ScriptEngineManager.class);

        for (ScriptEngineFactory factory : manager.getEngineFactories())
        {
            // don't add an entry for R, since we have a specific report type above.
            if (canCreateScript && LabkeyScriptEngineManager.isFactoryEnabled(factory) && !factory.getLanguageName().equalsIgnoreCase("R"))
            {
                ScriptReportBean bean = new ScriptReportBean(settings);
                bean.setRedirectUrl(returnUrl.getLocalURIString());
                bean.setScriptExtension(factory.getExtensions().get(0));

                if (factory instanceof ExternalScriptEngineFactory)
                {
                    bean.setReportType(ExternalScriptEngineReport.TYPE);
                    designers.add(new DesignerInfoImpl(ExternalScriptEngineReport.TYPE, factory.getLanguageName() + " View",
                            ReportUtil.getScriptReportDesignerURL(context, bean), _getIconPath(ExternalScriptEngineReport.TYPE)));
                }
                else
                {
                    bean.setReportType(InternalScriptEngineReport.TYPE);
                    designers.add(new DesignerInfoImpl(InternalScriptEngineReport.TYPE, factory.getLanguageName() + " View",
                            ReportUtil.getScriptReportDesignerURL(context, bean), _getIconPath(InternalScriptEngineReport.TYPE)));
                }
            }
        }

        // query snapshot
        if (context.hasPermission(AdminPermission.class))
        {
            QuerySnapshotService.I provider = QuerySnapshotService.get(settings.getSchemaName());
            if (provider != null && !QueryService.get().isQuerySnapshot(context.getContainer(), settings.getSchemaName(), settings.getQueryName()))
                designers.add(new DesignerInfoImpl(QuerySnapshotService.TYPE, "Query Snapshot",
                        provider.getCreateWizardURL(settings, context), _getIconPath(QuerySnapshotService.TYPE)));
        }

        if (canCreateScript)
        {
            ScriptReportBean bean = new ScriptReportBean(settings);
            bean.setRedirectUrl(returnUrl.getLocalURIString());
            bean.setScriptExtension(".js");
            bean.setReportType(JavaScriptReport.TYPE);
            designers.add(new DesignerInfoImpl(JavaScriptReport.TYPE, "JavaScript View", "JavaScript View",
                    ReportUtil.getScriptReportDesignerURL(context, bean), _getIconPath(JavaScriptReport.TYPE)));
        }

        return designers;
    }

    private String _getIconPath(String type)
    {
        if (_typeToIconMap.containsKey(type))
            return AppProps.getInstance().getContextPath() + _typeToIconMap.get(type);
        return null;
    }

    public String getIconPath(Report report)
    {
        if (report != null)
        {
            if (report instanceof AttachmentReport)
            {
                Container c = ContainerManager.getForId(report.getContainerId());
                AttachmentReport attachmentReport = (AttachmentReport)report;
                String filename = attachmentReport.getFilePath();

                if (null == filename)
                {
                    Attachment attachment = attachmentReport.getLatestVersion();
                    filename = attachment == null ? null : attachment.getName();
                }

                if (null != filename)
                {
                    return PageFlowUtil.urlProvider(CoreUrls.class).getAttachmentIconURL(c, filename).toString();
                }
            }

            if (report instanceof LinkReport)
            {
                Container c = ContainerManager.getForId(report.getContainerId());
                LinkReport linkReport = (LinkReport)report;
                // external link versus internal link
                String url = linkReport.getUrl(c);
                if (url != null)
                {
                    // XXX: Is there a better way to check if a link is local to this server?
                    if (linkReport.isInternalLink())
                        return AppProps.getInstance().getContextPath() + "/reports/internal-link.png";
                    else if (linkReport.isLocalLink())
                        return AppProps.getInstance().getContextPath() + "/reports/local-link.png";
                    else
                        return AppProps.getInstance().getContextPath() + "/reports/external-link.png";
                }
            }
            return _getIconPath(report.getType());
        }
        return super.getIconPath(report);
    }
}
