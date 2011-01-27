/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jun 11, 2007
 */
public class RReportJob extends PipelineJob implements Serializable
{
    private static final Logger _log = Logger.getLogger(RReportJob.class);
    public static final String PROCESSING_STATUS = "Processing";
    public static final String LOG_FILE_NAME = "report.log";
    private ReportIdentifier _reportId;
    private RReportBean _form;

    public RReportJob(String provider, ViewBackgroundInfo info, ReportIdentifier reportId, PipeRoot root) throws SQLException
    {
        super(provider, info, root);
        _reportId = reportId;
        init();
    }

    public RReportJob(String provider, ViewBackgroundInfo info, RReportBean form, PipeRoot root) throws Exception
    {
        super(provider, info, root);
        _form = form;
        init();
    }

    private void init()
    {
        Report report = getReport();

        if (report instanceof RReport)
        {
            File logFile = new File(((RReport)report).getReportDir(), LOG_FILE_NAME);
            this.setLogFile(logFile);
        }
    }

    public ActionURL getStatusHref()
    {
        File statusFile = getLogFile();
        if (statusFile != null && null != _reportId)
        {
            return PageFlowUtil.urlProvider(ReportUrls.class).urlRunReport(getContainer()).
                    addParameter(ReportDescriptor.Prop.reportId.name(), _reportId.toString());
        }
        return null;
    }

    public String getDescription()
    {
        String description = getReport().getDescriptor().getProperty("reportName");

        if (StringUtils.isEmpty(description))
            description = "Unknown R Report";

        return description;
    }

    private RReport getReport()
    {
        try
        {
            Report report = null;
            if (_reportId != null)
                report = _reportId.getReport();
            else if (_form != null)
                report = _form.getReport();
            if (report instanceof RReport)
                return (RReport)report;

            throw new RuntimeException("The report is not a valid instance of an RReport");
        }
        catch (Exception e)
        {
            throw new RuntimeException("Exception retrieving report", e);
        }
    }

    public void run()
    {
        setStatus(PROCESSING_STATUS, "Job started at: " + DateUtil.nowISO());
        RReport report = getReport();

        try
        {
            // get the input file which should have been previously created
            File inputFile = new File(report.getReportDir(), RReport.DATA_INPUT);
            ViewContext context = new ViewContext(getInfo());

            if (inputFile.exists())
            {
                List<ParamReplacement> outputSubst = new ArrayList<ParamReplacement>();

                String output = report.runScript(context, outputSubst, inputFile);
                if (!StringUtils.isEmpty(output))
                    info(output);

                if (outputSubst.size() > 0)
                {
                    // write the output substitution map to disk so we can render the view later
                    File file = new File(report.getReportDir(), RReport.SUBSTITUTION_MAP);
                    ParamReplacementSvc.get().toFile(outputSubst, file);
                }
                setStatus(PipelineJob.COMPLETE_STATUS, "Job finished at: " + DateUtil.nowISO());
            }
            else
            {
                setStatus(PipelineJob.ERROR_STATUS, "Job finished at: " + DateUtil.nowISO());
            }
        }
        catch (Exception e)
        {
            _log.error("Error occurred running the report background job", e);
            error("Error occurred running the report background job", e);
            setStatus(PipelineJob.ERROR_STATUS, "Job finished at: " + DateUtil.nowISO());
        }
    }
}
