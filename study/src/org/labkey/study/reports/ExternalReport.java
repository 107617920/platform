/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.common.tools.TabLoader;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.query.StudyQuerySchema;

import javax.servlet.ServletException;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * User: migra
 * Date: Mar 9, 2006
 * Time: 11:21:04 AM
 */
public class ExternalReport extends AbstractReport
{
    public static final String TYPE = "Study.externalReport";
    private static Logger _log = Logger.getLogger(ExternalReport.class);

    private Container _container;
    private String queryString;
    private RecomputeWhen recomputeWhen = RecomputeWhen.Always;
    public static final String REPORT_DIR = "Reports";
    public static final String DATA_FILE_SUBST = "${DATA_FILE}";
    public static final String REPORT_FILE_SUBST = "${REPORT_FILE}";
    private static final String DATA_FILE_SUFFIX = "Data.tsv";
    private static final MimeMap mimeMap = new MimeMap();

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Advanced View";
    }

    public void setContainer(Container c)
    {
        _container = c;
    }

    public String getCommandLine()
    {
        return getDescriptor().getProperty("commandLine");
    }

    public void setCommandLine(String commandLine)
    {
        getDescriptor().setProperty("commandLine", commandLine);
    }

    public String getFileExtension()
    {
        return getDescriptor().getProperty("fileExtension");
    }

    public void setFileExtension(String fileExtension)
    {
        getDescriptor().setProperty("fileExtension", fileExtension);
    }

    public Integer getDatasetId()
    {
        return NumberUtils.toInt(getDescriptor().getProperty("datasetId"));
    }

    public void setDatasetId(Integer datasetId)
    {
        getDescriptor().setProperty("datasetId", String.valueOf(datasetId));
    }

    public int getVisitRowId()
    {
        return NumberUtils.toInt(getDescriptor().getProperty("visitRowId"));
    }

    public void setVisitId(int visitRowId)
    {
        getDescriptor().setProperty("visitRowId", String.valueOf(visitRowId));
    }

    public String getQueryName()
    {
        return getDescriptor().getProperty("queryName");
    }

    public void setQueryName(String queryName)
    {
        getDescriptor().setProperty("queryName", queryName);
    }

    public static enum RecomputeWhen
    {
        Always,
        Hourly,
        Daily,
        Never
    }

/*
    @Override
    public boolean canHavePermissions()
    {
        return true;
    }

*/
    public HttpView renderReport(ViewContext viewContext)
    {
        String ext = getFileExtension() == null ? "txt" : getFileExtension();
        if (ext.charAt(0) == '.')
            ext = ext.substring(1);

        if (null == StringUtils.trimToNull(getCommandLine()) || ((null == getDatasetId() || 0 == getDatasetId()) && null == getQueryName()))
            return new HtmlView("Command line and datasetId must be provided");

        File resultFile = null;
        File outFile = null;
        File dataFile = null;
        try
        {
            dataFile = File.createTempFile(getFilePrefix(), DATA_FILE_SUFFIX, getReportDir(viewContext));
            String dataFileName = dataFile.getName();

            ResultSet rs;
            if (null == getQueryName())
                rs = ReportManager.get().getReportResultSet(viewContext, getDatasetId(), getVisitRowId());
            else
            {
//                QueryDefinition def = QueryService.get().getQueryDef(viewContext.getContainer(), "study", queryName);
//                QuerySchema schema = QueryService.get().getUserSchema(viewContext.getUser(), viewContext.getContainer(),  "study");
                UserSchema schema = getStudyQuerySchema(viewContext.getUser(), ACL.PERM_READ, viewContext);
                TableInfo mainTable = schema.getTable(getQueryName(), "Dataset");
                rs = Table.select(mainTable, mainTable.getColumns(), null, null);
            }

            TSVGridWriter tsv = new TSVGridWriter(rs);
            tsv.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
            tsv.write(dataFile);

            String[] params = getCommandStrings();
            for (int i = 0; i < params.length; i++)
            {
                String param = params[i];
                if (DATA_FILE_SUBST.equalsIgnoreCase(param))
                    params[i] = dataFile.getName();
                else if (REPORT_FILE_SUBST.equalsIgnoreCase(param))
                {
                    String resultFileName = dataFile.getName();
                    resultFileName = resultFileName.substring(0, resultFileName.length() - DATA_FILE_SUFFIX.length()) + "Result." + ext;
                    resultFile = new File(getReportDir(viewContext), resultFileName);
                    params[i] = resultFileName;
                }
            }

            //outFile is stdout + stdErr. If proc writes to stdout use file extension hint
            String outFileExt = resultFile == null ? ext : "out";
            outFile = new File(getReportDir(viewContext), dataFileName.substring(0, dataFileName.length() - DATA_FILE_SUFFIX.length()) + "." + outFileExt);

            ProcessBuilder pb = new ProcessBuilder(params);
            pb = pb.directory(getReportDir(viewContext));

            int resultCode = runProcess(pb, outFile);
            if (resultCode != 0)
            {
                String err = "<font color='red'>Error " + resultCode + " executing command</font> " +
                        PageFlowUtil.filter(getCommandLine()) + "<br><pre>" +
                        PageFlowUtil.filter(PageFlowUtil.getFileContentsAsString(outFile)) + "</pre>";
                HttpView errView = new HtmlView(err);
                return errView;
            }
            else
            {
                File reportFile = null == resultFile ? outFile : resultFile;
                if (ext.equalsIgnoreCase("tsv"))
                    return new TabReportView(reportFile);
                else if (mimeMap.getContentType(ext) != null && mimeMap.getContentType(ext).startsWith("image/"))
                    return new ImgReportView(reportFile);
                else
                    return new InlineReportView(reportFile);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not create file.", e);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (ServletException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (null != dataFile && dataFile.exists())
                dataFile.delete();
            //If for some reason file never gets rendered, mark for delete on exit.
            if (null != outFile && outFile.exists())
                outFile.deleteOnExit();
            if (null != resultFile && resultFile.exists())
                resultFile.deleteOnExit();
        }
    }

    protected StudyQuerySchema getStudyQuerySchema(User user, int perm, ViewContext context) throws ServletException
    {
        if (perm != ACL.PERM_READ)
            throw new IllegalArgumentException("only PERM_READ supported");
        Study study = StudyManager.getInstance().getStudy(context.getContainer());
        //boolean mustCheckUserPermissions = mustCheckDatasetPermissions(user, perm);
        return new StudyQuerySchema(study, user, false);
    }

    private String getFilePrefix()
    {
        if (null != getDescriptor().getReportName())
            return getDescriptor().getReportName();

        return "rpt";
    }

    private int runProcess(ProcessBuilder pb, File outFile)
    {
        Process proc;
        try
        {
            pb.redirectErrorStream(true);
            proc = pb.start();
        }
        catch (SecurityException se)
        {
            throw new RuntimeException(se);
        }
        catch (IOException eio)
        {
            Map<String, String> env = pb.environment();
            throw new RuntimeException("Failed starting process '" + pb.command() + "'. " +
                    "Must be on server path. (PATH=" + env.get("PATH") + ")", eio);
        }

        BufferedReader procReader = null;
        FileWriter writer = null;
        try
        {
            writer = new FileWriter(outFile);
            procReader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = procReader.readLine()) != null)
            {
                writer.write(line);
                writer.write("\n");
            }
        }
        catch (IOException eio)
        {
            throw new RuntimeException("Failed writing output for process in '" + pb.directory().getPath() + "'.", eio);
        }
        finally
        {
            if (procReader != null)
            {
                try
                {
                    procReader.close();
                }
                catch (IOException eio)
                {
                    _log.error("unexpected error", eio);
                }
            }
            if (writer != null)
            {
                try
                {
                    writer.close();
                }
                catch (IOException eio)
                {
                    _log.error("unexpected error", eio);
                }
            }
        }

        try
        {
            return proc.waitFor();
        }
        catch (InterruptedException ei)
        {
            throw new RuntimeException("Interrupted process for '" + pb.command() + " in " + pb.directory() + "'.", ei);
        }

    }


    private String[] getCommandStrings()
    {
        StringTokenizer tokenizer = new StringTokenizer(getCommandLine(), " ");
        List<String> tokens = new ArrayList<String>();

        while (tokenizer.hasMoreTokens())
            tokens.add(tokenizer.nextToken());

        return tokens.toArray(new String[tokens.size()]);
    }

    private File getReportDir(ViewContext viewContext)
    {
        PipeRoot pipelineRoot = null;
        try
        {
            pipelineRoot = PipelineService.get().findPipelineRoot(viewContext.getContainer());
            if (null == pipelineRoot)
                throw new IllegalStateException("Pipeline root has not been set. Please ask an administrator to set one up for you");

            File reportDir = pipelineRoot.resolvePath(REPORT_DIR);
            if (!reportDir.exists())
                reportDir.mkdirs();

            return reportDir;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public File getOutputFile(ViewContext viewContext)
    {
        File reportDir = getReportDir(viewContext);
        return new File(reportDir, getFilePrefix());
    }

    public String getParams()
    {
        ActionURL url = new ActionURL();
        if (null != queryString)
            url.setRawQuery(queryString);

        replaceOrDelete(url, "commandLine", getCommandLine());
        replaceOrDelete(url, "fileExtension", getFileExtension());
        replaceOrDelete(url, Visit.VISITKEY, getVisitRowId());
        replaceOrDelete(url, DataSetDefinition.DATASETKEY, getDatasetId());
        replaceOrDelete(url, "queryName", getQueryName());
        url.replaceParameter("recomputeWhen", recomputeWhen.toString());

        return url.getRawQuery();
    }

    ActionURL replaceOrDelete(ActionURL url, String paramName, Object paramVal)
    {
        if (null == paramVal)
            url.deleteParameter(paramName);
        else
            url.replaceParameter(paramName, paramVal.toString());

        return url;

    }

    public void setParams(String params)
    {
        if (null == params)
        {
            queryString = null;
            setCommandLine(null);
            setFileExtension(null);
            setVisitId(0);
            setDatasetId(null);
            recomputeWhen = RecomputeWhen.Always;
            return;
        }

        Map m = PageFlowUtil.mapFromQueryString(params);

        setCommandLine((String)m.get("commandLine"));
        setFileExtension((String)m.get("fileExtension"));
        setQueryName((String) m.get("queryName"));
        String recomputeString = StringUtils.trimToNull((String)m.get("recomputeWhen"));
        recomputeWhen = null == recomputeString ? RecomputeWhen.Always : RecomputeWhen.valueOf(recomputeString);

        setVisitId(visitRowIdParameter(m));
        String datasetIdStr = StringUtils.trimToNull((String)m.get(DataSetDefinition.DATASETKEY));
        setDatasetId(NumberUtils.toInt(datasetIdStr));
    }

    /** Backwards compatibility hack */
    protected int visitRowIdParameter(Map m)
    {
        int visitRowId = 0;

        String visitRowIdStr = StringUtils.trimToNull((String)m.get("visitRowId"));
        if (null != visitRowIdStr)
        {
            visitRowId = Integer.parseInt(visitRowIdStr);
        }
        else
        {
            String visitIdStr = StringUtils.trimToNull((String)m.get("sequenceNum"));
            if (null != visitIdStr)
            {
                double visitId = NumberUtils.toDouble(visitIdStr);
                Visit v = StudyManager.getInstance().getVisitForSequence(StudyManager.getInstance().getStudy(_container),visitId);
                if (null != v)
                    visitRowId = v.getRowId();
            }
        }
        return visitRowId;
    }

    public void setRecomputeWhen(RecomputeWhen when)
    {
        this.recomputeWhen = when;
    }

    public RecomputeWhen getRecomputeWhen()
    {
        return recomputeWhen;
    }

    public class InlineReportView extends HttpView
    {
        File file;

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            out.write("<pre>");
            out.write(PageFlowUtil.filter(PageFlowUtil.getFileContentsAsString(file)));
            out.write("</pre>");

            if (recomputeWhen == RecomputeWhen.Always)
                file.delete();
        }

        public InlineReportView(File file)
        {
            this.file = file;
        }

    }

    public class TabReportView extends HttpView
    {
        File file;

        TabReportView(File file)
        {
            this.file = file;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            TabLoader tabLoader = new TabLoader(file);
            TabLoader.ColumnDescriptor[] cols = tabLoader.getColumns();
            Map[] data = (Map[]) tabLoader.load();
            out.write("<table><tr>");
            for (TabLoader.ColumnDescriptor col : cols)
            {
                out.write("<td class='labkey-header'>");
                out.write(PageFlowUtil.filter(col.name));
                out.write("</td>");
            }
            out.write("</tr>");
            for (Map m : data)
            {
                out.write("<tr>");
                for (TabLoader.ColumnDescriptor col : cols)
                {
                    out.write("<td");
                    if (Number.class.isAssignableFrom(col.clazz))
                        out.write(" align='right'");
                    out.write(">");
                    Object colVal = m.get(col.name);
                    if (null != colVal)
                        out.write(PageFlowUtil.filter(ConvertUtils.convert(colVal)));
                    else
                        out.write("&nbsp");
                    out.write("</td>");
                }
                out.write("</tr>");
            }
            out.write("</table>");

            if (recomputeWhen == RecomputeWhen.Always)
                file.delete();
        }
    }

    public class ImgReportView extends HttpView
    {
        File file;

        ImgReportView(File file)
        {
            this.file = file;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out)
        {
            String key = "temp:" + GUID.makeGUID();
            getViewContext().getRequest().getSession(true).setAttribute(key, file);
            out.write("<img src=\"");
            out.write(getViewContext().getActionURL().relativeUrl("streamFile", PageFlowUtil.map("sessionKey", key), "Study-Reports", true));
            out.write("\">");
        }

    }
}

