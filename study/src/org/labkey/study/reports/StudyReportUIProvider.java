/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.study.reports;

import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.JavaScriptReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.DefaultReportUIProvider;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.TimeChartReport;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.List;

/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 5:19:28 PM
 */
public class StudyReportUIProvider extends DefaultReportUIProvider
{
    private static final ReportService.ItemFilter _filter = new ReportService.ItemFilter(){
            public boolean accept(String reportType, String label)
            {
                if (StudyCrosstabReport.TYPE.equals(reportType)) return true;
                if (StudyChartQueryReport.TYPE.equals(reportType)) return true;
                if (ChartReportView.TYPE.equals(reportType)) return true;
                if (StudyRReport.TYPE.equals(reportType)) return true;
                if (ExternalReport.TYPE.equals(reportType)) return true;
                if (QuerySnapshotService.TYPE.equals(reportType)) return true;
                if (StudyQueryReport.TYPE.equals(reportType)) return true;
                if (TimeChartReport.TYPE.equals(reportType)) return true;
                if (JavaScriptReport.TYPE.equals(reportType)) return true;
                return false;
            }
        };

    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<ReportService.DesignerInfo>();

        // crosstab designer
        ActionURL crossTabURL = new ActionURL(ReportsController.ParticipantCrosstabAction.class, context.getContainer());
        crossTabURL.addParameter(QueryParam.schemaName, settings.getSchemaName());
        crossTabURL.addParameter(QueryParam.queryName, settings.getQueryName());
        crossTabURL.addParameter(QueryParam.viewName, settings.getViewName());
        crossTabURL.addParameter(QueryParam.dataRegionName, settings.getDataRegionName());

        URLHelper returnUrl = settings.getReturnUrl();
        if (returnUrl == null)
            returnUrl = context.getActionURL();

        crossTabURL.addParameter(ActionURL.Param.redirectUrl, returnUrl.getLocalURIString());

        if (StudyManager.getSchemaName().equals(settings.getSchemaName()))
        {
            // crosstab report
            crossTabURL.addParameter(ReportDescriptor.Prop.reportType, StudyCrosstabReport.TYPE);
            designers.add(new DesignerInfoImpl(StudyCrosstabReport.TYPE, "Crosstab View", crossTabURL));

            // chart designer
            ChartDesignerBean chartBean = new ChartDesignerBean(settings);
            chartBean.setReportType(StudyChartQueryReport.TYPE);

            ActionURL url = ReportUtil.getChartDesignerURL(context, chartBean);
            url.addParameter(DataSetDefinition.DATASETKEY, NumberUtils.toInt(context.getActionURL().getParameter(DataSetDefinition.DATASETKEY), 0));
            url.setAction(ReportsController.DesignChartAction.class);

            designers.add(new DesignerInfoImpl(StudyChartQueryReport.TYPE, "Chart View", url));

            // r report
            if (ReportUtil.canCreateScript(context) && RReport.isEnabled())
            {
                RReportBean rBean = new RReportBean(settings);
                rBean.setReportType(StudyRReport.TYPE);
                rBean.setRedirectUrl(returnUrl.getLocalURIString());

                designers.add(new DesignerInfoImpl(StudyRReport.TYPE, "R View", ReportUtil.getRReportDesignerURL(context, rBean)));
            }

            // external report
            if (context.getUser().isAdministrator())
            {
                ActionURL buttonURL = context.getActionURL().clone();
                buttonURL.setAction(ReportsController.ExternalReportAction.class);
                designers.add(new DesignerInfoImpl(ExternalReport.TYPE, "Advanced View", "An External Command Report", buttonURL));
            }
        }
        else
        {
            crossTabURL.addParameter(ReportDescriptor.Prop.reportType, CrosstabReport.TYPE);
            designers.add(new DesignerInfoImpl(CrosstabReport.TYPE, "Crosstab View", crossTabURL));
        }

        return designers;
    }

    public String getReportIcon(ViewContext context, String reportType)
    {
        if (StudyRReport.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/r.gif";
        if (ChartReportView.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/chart.gif";
        if (StudyQueryReport.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/grid.gif";
        if (StudyChartQueryReport.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/chart.gif";
        if (ExportExcelReport.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/xls.gif";

        return super.getReportIcon(context, reportType);
    }

    public static ReportService.ItemFilter getItemFilter()
    {
        return _filter;
    }
}