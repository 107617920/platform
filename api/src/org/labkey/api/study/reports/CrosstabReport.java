/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.api.study.reports;

import org.apache.log4j.Logger;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;

import javax.servlet.http.HttpServletResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Aug 4, 2009
 */
public class CrosstabReport extends AbstractReport implements Report.ResultSetGenerator
{
    public static final String TYPE = "ReportService.CrossTab";

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Crosstab View";
    }

    public String getDescriptorType()
    {
        return CrosstabReportDescriptor.TYPE;
    }

    protected ReportQueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String schemaName = descriptor.getProperty(QueryParam.schemaName.toString());
        final String dataRegionName = descriptor.getProperty(QueryParam.dataRegionName.toString());

        if (context != null && schemaName != null)
        {
            UserSchema base = (UserSchema) DefaultSchema.get(context.getUser(), context.getContainer()).getSchema(schemaName);
            if (base != null)
            {
                QuerySettings settings = base.getSettings(context, dataRegionName);
                settings.setSchemaName(schemaName);
                settings.setQueryName(queryName);
                settings.setViewName(viewName);

                UserSchema schema = base.createView(context, settings).getSchema();
                return new ReportQueryView(schema, settings);
            }
        }
        return null;
    }

    public HttpView renderReport(ViewContext context)
    {
        String errorMessage = null;
        ReportDescriptor reportDescriptor = getDescriptor();
        ResultSet rs = null;

        if (reportDescriptor instanceof CrosstabReportDescriptor)
        {
            CrosstabReportDescriptor descriptor = (CrosstabReportDescriptor)reportDescriptor;
            try {
                Crosstab crosstab = createCrosstab(context);
                if (crosstab != null)
                {
                    ActionURL exportAction = null;

                    if (descriptor.getReportId() != null)
                    {
                        exportAction = PageFlowUtil.urlProvider(ReportUrls.class).urlExportCrosstab(context.getContainer()).addParameter(ReportDescriptor.Prop.reportId, descriptor.getReportId().toString());
                    }
                    return new CrosstabView(crosstab, exportAction);
                }
            }
            catch (Exception e)
            {
                errorMessage = e.getMessage();
                if (errorMessage == null)
                    errorMessage = e.toString();
                Logger.getLogger(CrosstabReport.class).error("unexpected error in renderReport()", e);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
            }
        }
        else
        {
            errorMessage = "Invalid report params: The ReportDescriptor must be an instance of CrosstabReportDescriptor";
        }

        if (errorMessage != null)
        {
            return new VBox(ExceptionUtil.getErrorView(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage, null, context.getRequest(), false));
        }
        return null;
    }

    public Results generateResults(ViewContext context) throws Exception
    {
        ReportQueryView view = createQueryView(context, getDescriptor());
        if (view != null)
        {
            DataView dataView = view.createDataView();
            DataRegion rgn = dataView.getDataRegion();
            rgn.setMaxRows(0);
            RenderContext ctx = dataView.getRenderContext();

            if (null == rgn.getResultSet(ctx))
                return null;
            return new Results(ctx);
        }
        return null;
    }

    protected Crosstab createCrosstab(ViewContext context) throws Exception
    {
        CrosstabReportDescriptor descriptor = (CrosstabReportDescriptor)getDescriptor();
        Results results = generateResults(context);
        if (results != null)
        {
            FieldKey rowFieldKey = FieldKey.decode(descriptor.getProperty("rowField"));
            FieldKey colFieldKey = FieldKey.decode(descriptor.getProperty("colField"));
            FieldKey statFieldKey = FieldKey.decode(descriptor.getProperty("statField"));

            Set<Stats.StatDefinition> statSet = new LinkedHashSet<Stats.StatDefinition>();
            for (String stat : descriptor.getStats())
            {
                if ("Count".equals(stat))
                    statSet.add(Stats.COUNT);
                else if ("Sum".equals(stat))
                    statSet.add(Stats.SUM);
                else if ("Sum".equals(stat))
                    statSet.add(Stats.SUM);
                else if ("Mean".equals(stat))
                    statSet.add(Stats.MEAN);
                else if ("Min".equals(stat))
                    statSet.add(Stats.MIN);
                else if ("Max".equals(stat))
                    statSet.add(Stats.MAX);
                else if ("StdDev".equals(stat))
                    statSet.add(Stats.STDDEV);
                else if ("Var".equals(stat))
                    statSet.add(Stats.VAR);
                else if ("Median".equals(stat))
                    statSet.add(Stats.MEDIAN);
            }
            return new Crosstab(results, rowFieldKey, colFieldKey, statFieldKey, statSet);
        }
        return null;
    }

    public ExcelWriter getExcelWriter(ViewContext context) throws Exception
    {
        Crosstab crosstab = createCrosstab(context);
        if (crosstab != null)
        {
            return crosstab.getExcelWriter();
        }
        return null;
    }
}
