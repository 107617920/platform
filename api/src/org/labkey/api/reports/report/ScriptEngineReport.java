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
package org.labkey.api.reports.report;

import org.apache.commons.collections15.map.CaseInsensitiveMap;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.Table;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.ConsoleOutput;
import org.labkey.api.reports.report.r.view.FileOutput;
import org.labkey.api.reports.report.r.view.HtmlOutput;
import org.labkey.api.reports.report.r.view.ImageOutput;
import org.labkey.api.reports.report.r.view.PdfOutput;
import org.labkey.api.reports.report.r.view.PostscriptOutput;
import org.labkey.api.reports.report.r.view.ROutputView;
import org.labkey.api.reports.report.r.view.TextOutput;
import org.labkey.api.reports.report.r.view.TsvOutput;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.VirtualFile;

import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * User: Karl Lum
 * Date: Dec 3, 2008
 * Time: 2:45:37 PM
 *
 * A Report implementation that uses a ScriptEngine instance to execute the associated script.
*/
public abstract class ScriptEngineReport extends ScriptReport implements Report.ResultSetGenerator
{
    public static final String INPUT_FILE_TSV = "input_data";
    public static Pattern scriptPattern = Pattern.compile("\\$\\{(.*?)\\}");

    public static final String TYPE = "ReportService.scriptEngineReport";
    public static final String DATA_INPUT = "input_data.tsv";
    public static final String REPORT_DIR = "reports_temp";
    public static final String FILE_PREFIX = "rpt";
    public static final String SUBSTITUTION_MAP = "substitutionMap.txt";
    public static final String CONSOLE_OUTPUT = "console.txt";

    private File _tempFolder;
    private boolean _tempFolderPipeline;
    private static Logger _log = Logger.getLogger(ScriptEngineReport.class);

    static
    {
        ParamReplacementSvc.get().registerHandler(new ConsoleOutput());
        ParamReplacementSvc.get().registerHandler(new TextOutput());
        ParamReplacementSvc.get().registerHandler(new HtmlOutput());
        ParamReplacementSvc.get().registerHandler(new TsvOutput());
        ParamReplacementSvc.get().registerHandler(new ImageOutput());
        ParamReplacementSvc.get().registerHandler(new PdfOutput());
        ParamReplacementSvc.get().registerHandler(new FileOutput());
        ParamReplacementSvc.get().registerHandler(new PostscriptOutput());
    }

    public String getType()
    {
        return TYPE;
    }

    public String getDescriptorType()
    {
        return RReportDescriptor.TYPE;
    }

    public ScriptEngine getScriptEngine()
    {
        String extension = getDescriptor().getProperty(ScriptReportDescriptor.Prop.scriptExtension);
        ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);

        return mgr.getEngineByExtension(extension);
    }

    public String getTypeDescription()
    {
        ScriptEngine engine = getScriptEngine();

        if (engine != null)
        {
            return engine.getFactory().getLanguageName();
        }

        return "Script Engine Report";        
        //throw new RuntimeException("No Script Engine is available for this Report");
    }

    @Override
    public boolean supportsPipeline()
    {
        return true;
    }

    public Results generateResults(ViewContext context) throws Exception
    {
        ReportDescriptor descriptor = getDescriptor();
        QueryView view = createQueryView(context, descriptor);

        if (view != null)
        {
            view.getSettings().setMaxRows(Table.ALL_ROWS);
            DataView dataView = view.createDataView();
            DataRegion rgn = dataView.getDataRegion();
            RenderContext ctx = dataView.getRenderContext();
            rgn.setAllowAsync(false);

            // temporary code until we add a more generic way to specify a filter or grouping on the chart
            final String filterParam = descriptor.getProperty(ReportDescriptor.Prop.filterParam);

            if (!StringUtils.isEmpty(filterParam))
            {
                final String filterValue = (String)context.get(filterParam);

                if (filterValue != null)
                {
                    SimpleFilter filter = new SimpleFilter();
                    filter.addCondition(filterParam, filterValue, CompareType.EQUAL);

                    ctx.setBaseFilter(filter);
                }
            }

            if (null == rgn.getResultSet(ctx))
                return null;

            return new ResultsImpl(ctx);
        }

        return null;
    }

    protected boolean validateScript(String text, List<String> errors)
    {
        if (StringUtils.isEmpty(text))
        {
            errors.add("Empty script, a script must be provided.");
            return false;
        }

        Matcher m = scriptPattern.matcher(text);

        while (m.find())
        {
            String value = m.group(1);

            if (!isValidReplacement(value))
            {
                errors.add("Invalid template, the replacement parameter: " + value + " is unknown.");
                return false;
            }
        }

        return true;
    }

    protected boolean isValidReplacement(String value)
    {
        if (INPUT_FILE_TSV.equals(value)) return true;

        return ParamReplacementSvc.get().getHandler(value) != null;
    }

    /*
     * Create the .tsv associated with the data grid for this report.
     */
    public File createInputDataFile(ViewContext context) throws Exception
    {
        File resultFile = new File(getReportDir(), DATA_INPUT);

        if (context != null)
        {
            Results r = null;
            try
            {
                r = generateResults(context);
                if (r != null && r.getResultSet() != null)
                {
                    TSVGridWriter tsv = createGridWriter(r);
                    tsv.write(resultFile);
                }
            }
            finally
            {
                ResultSetUtil.close(r);
            }
        }

        return resultFile;
    }

    public File getReportDir()
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground));

        if (_tempFolder == null || _tempFolderPipeline != isPipeline)
        {
            File tempRoot = getTempRoot(getDescriptor());
            String reportId = FileUtil.makeLegalName(String.valueOf(getDescriptor().getReportId())).replaceAll(" ", "_");

            if (isPipeline)
                _tempFolder = new File(tempRoot, "Report_" + reportId);
            else
                _tempFolder = new File(tempRoot.getAbsolutePath() + File.separator + "Report_" + reportId, String.valueOf(Thread.currentThread().getId()));

            _tempFolderPipeline = isPipeline;

            if (!_tempFolder.exists())
                _tempFolder.mkdirs();
        }

        return _tempFolder;
    }

    public void deleteReportDir()
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground));

        try
        {
            File dir = getReportDir();

            if (!isPipeline)
                dir = dir.getParentFile();

            FileUtil.deleteDir(dir);
        }
        finally
        {
            _tempFolder = null;
        }
    }

    /**
     * Invoked from a maintenance task, clean up temporary report files and folders that are of a
     * certain age.
     */
    public static void scheduledFileCleanup()
    {
        final long cutoff = System.currentTimeMillis() - (1000 * 3600 * 24);
        File root = getTempRoot(ReportService.get().createDescriptorInstance(RReportDescriptor.TYPE));

        for (File file : root.listFiles())
        {
            if (file.isDirectory())
            {
                _log.info("Deleting temporary report folder: " + file.getPath());
                deleteReportDir(file, cutoff);
            }
            else
            {
                // shouldn't be loose files here, so delete anyway
                file.delete();
            }
        }

        // now delete any downloadable files (images and pdf's) that are moved up into the temp folder
        ROutputView.cleanUpTemp(cutoff);
    }

    /**
     * Delete any thread specific subfolders if they are older than the
     * specified cutoff, and if there are no thread subfolders, delete the parent.
     * @param dir
     * @param cutoff
     */
    protected static void deleteReportDir(File dir, long cutoff)
    {
        if (dir.isDirectory())
        {
            boolean empty = true;
            for (File child : dir.listFiles())
            {
                if (child.lastModified() < cutoff)
                {
                    FileUtil.deleteDir(child);
                }
                else
                    empty = false;
            }
            // delete the parent if there are no subfolders
            if (empty)
                FileUtil.deleteDir(dir);
        }
    }
    
    public static File getTempRoot(ReportDescriptor descriptor)
    {
        File tempRoot;
        String tempFolderName = null;// = getTempFolder();
        boolean isPipeline = BooleanUtils.toBoolean(descriptor.getProperty(ScriptReportDescriptor.Prop.runInBackground));

        if (StringUtils.isEmpty(tempFolderName))
        {
            try
            {
                if (isPipeline && descriptor.getContainerId() != null)
                {
                    Container c = ContainerManager.getForId(descriptor.getContainerId());
                    PipeRoot root = PipelineService.get().findPipelineRoot(c);
                    tempRoot = root.resolvePath(REPORT_DIR);

                    if (!tempRoot.exists())
                        tempRoot.mkdirs();
                }
                else
                {
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    tempRoot = new File(tempDir, REPORT_DIR);

                    if (!tempRoot.exists())
                        tempRoot.mkdirs();
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error setting up temp directory", e);
            }
        }
        else
        {
            tempRoot = new File(tempFolderName);
        }

        return tempRoot;
    }


    protected TSVGridWriter createGridWriter(Results r) throws SQLException
    {
        ResultSetMetaData md = r.getMetaData();
        ColumnInfo cols[] = new ColumnInfo[md.getColumnCount()];
        List<String> outputColumnNames = outputColumnNames(r);
        List<DisplayColumn> dataColumns = new ArrayList<DisplayColumn>();

        for (int i = 0; i < cols.length; i++)
        {
            int sqlColumn = i + 1;
            dataColumns.add(new NADisplayColumn(outputColumnNames.get(i), new ColumnInfo(md, sqlColumn)));
        }

        TSVGridWriter tsv = new TSVGridWriter(r, dataColumns);
        tsv.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);

        return tsv;
    }


    protected List<String> outputColumnNames(Results r) throws SQLException
    {
        assert null != r.getResultSet();
        CaseInsensitiveHashSet aliases = new CaseInsensitiveHashSet(); // output names
        Map<String,String> remap = new CaseInsensitiveMap<String>();       // resultset name to output name
                
        // process the FieldKeys in order to be backward compatible
        for (Map.Entry<FieldKey,ColumnInfo> e : r.getFieldMap().entrySet())
        {
            ColumnInfo col = e.getValue();
            FieldKey fkey = e.getKey();
            assert fkey.equals(col.getFieldKey());

            String alias = oldLegalName(fkey);

            if (!aliases.add(alias))
            {
                int i;

                for (i=1; !aliases.add(alias+i); i++)
                    ;

                alias = alias + i;
            }

            remap.put(col.getAlias(), alias);
        }

        ResultSetMetaData md = r.getResultSet().getMetaData();
        ArrayList<String> ret = new ArrayList<String>(md.getColumnCount());
        // now go through the resultset

        for (int col=1, count=md.getColumnCount(); col<=count; col++)
        {
            String name = md.getColumnName(col);
            String alias = remap.get(name);

            if (null != alias)
            {
                ret.add(alias);
                continue;
            }

            alias = ColumnInfo.propNameFromName(name).toLowerCase();

            if (!aliases.add(alias))
            {
                int i;
                for (i=1; !aliases.add(alias+i); i++)
                    ;
                alias = alias + i;
            }

            ret.add(alias);
        }

        return ret;
    }
    

    private String oldLegalName(FieldKey fkey)
    {
        String r = AliasManager.makeLegalName(StringUtils.join(fkey.getParts(),"_"), null, false);
//        if (r.length() > 40)
//            r = r.substring(0,40);
        return ColumnInfo.propNameFromName(r).toLowerCase();
    }


    public static HttpView renderViews(ScriptEngineReport report, final VBox view, List<ParamReplacement> parameters, boolean deleteTempFiles) throws IOException
    {
        return handleParameters(report, parameters, new ParameterHandler<HttpView>()
        {
            @Override
            public boolean handleParameter(ViewContext context, Report report, ParamReplacement param, List<String> sectionNames)
            {
                // don't show headers if not all sections are being rendered
                if (!sectionNames.isEmpty())
                    param.setHeaderVisible(false);

                param.setReport(report);
                view.addView(param.render(context));

                return true;
            }

            @Override
            public HttpView cleanup(ScriptEngineReport report)
            {
                if (!BooleanUtils.toBoolean(report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground)))
                    view.addView(new TempFileCleanup(report.getReportDir().getAbsolutePath()));

                return view;
            }
        });
    }


    public Thumbnail getThumbnail(List<ParamReplacement> parameters) throws IOException
    {
        return handleParameters(this, parameters, new ParameterHandler<Thumbnail>(){
            private Thumbnail _thumbnail = null;

            @Override
            public boolean handleParameter(ViewContext context, Report report, ParamReplacement param, List<String> sectionNames) throws IOException
            {
                File file = param.getFile();
                MimeMap mm = new MimeMap();
                String contentType = mm.getContentTypeFor(file.getName());

                if (contentType.startsWith("image/"))
                {
                    _thumbnail = ImageUtil.renderThumbnail(ImageIO.read(file));

                    return false;
                }
                else if ("text/html".equals(contentType))
                {
                    // TODO: check if we have an SVG and render a thumbnail
                }
                else if ("application/pdf".equals(contentType))
                {
                    DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

                    if (null != svc)
                    {
                        InputStream pdfStream = new FileInputStream(file);
                        BufferedImage image = svc.pdfToImage(pdfStream, 0);

                        _thumbnail = ImageUtil.renderThumbnail(image);
                    }

                    return false;
                }

                return true;
            }

            @Override
            public Thumbnail cleanup(ScriptEngineReport report)
            {
                // TODO: Delete file?
                return _thumbnail;
            }
        });
    }


    private static <K> K handleParameters(ScriptEngineReport report, List<ParamReplacement> parameters, ParameterHandler<K> handler) throws IOException
    {
        String sections = (String)HttpView.currentContext().get(renderParam.showSection.name());
        List<String> sectionNames = Collections.emptyList();

        if (sections != null)
            sectionNames = Arrays.asList(sections.split("&"));

        ViewContext context = HttpView.currentContext();

        for (ParamReplacement param : parameters)
        {
            if (isViewable(param, sectionNames))
            {
                boolean keepGoing = handler.handleParameter(context, report, param, sectionNames);

                if (!keepGoing)
                    break;
            }
        }

        return handler.cleanup(report);
    }


    private interface ParameterHandler<K>
    {
        boolean handleParameter(ViewContext context, Report report, ParamReplacement param, List<String> sectionNames) throws IOException;
        K cleanup(ScriptEngineReport report);
    }


    protected static boolean isViewable(ParamReplacement param, List<String> sectionNames)
    {
        File data = param.getFile();

        if (data.exists())
        {
            if (!sectionNames.isEmpty())
                return sectionNames.contains(param.getName());
            return true;
        }

        return false;
    }

    /**
     * Create the script to be executed by the scripting engine
     * @param outputSubst
     * @return
     * @throws Exception
     */
    protected String createScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv) throws Exception
    {
        return processScript(context, getDescriptor().getProperty(ScriptReportDescriptor.Prop.script), inputDataTsv, outputSubst);
    }

    public abstract String runScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv) throws ScriptException;

    /**
     * Takes a script source, adds a prolog, processes any input and output replacement parameters
     * @param script
     * @param inputFile
     * @param outputSubst
     * @throws Exception
     */
    protected String processScript(ViewContext context, String script, File inputFile, List<ParamReplacement> outputSubst) throws Exception
    {
        if (!StringUtils.isEmpty(script))
        {
            script = StringUtils.defaultString(getScriptProlog(context, inputFile)) + script;

            if (inputFile != null)
                script = processInputReplacement(script, inputFile);
            script = processOutputReplacements(script, outputSubst);
        }
        return script;
    }

    protected String getScriptProlog(ViewContext context, File inputFile)
    {
        return null;
    }

    protected String processInputReplacement(String script, File inputFile) throws Exception
    {
        return ParamReplacementSvc.get().processInputReplacement(script, INPUT_FILE_TSV, inputFile.getAbsolutePath().replaceAll("\\\\", "/"));
    }

    protected String processOutputReplacements(String script, List<ParamReplacement> replacements) throws Exception
    {
        return ParamReplacementSvc.get().processParamReplacement(script, getReportDir(), replacements);
    }

    @Override
    public void serializeToFolder(VirtualFile directory) throws IOException
    {
        ReportDescriptor descriptor = getDescriptor();

        if (descriptor.getReportId() != null)
        {
            // for script based reports, write the script portion to a separate file to facilitate script modifications
            String scriptFileName = getSerializedScriptFileName();
            PrintWriter writer = null;

            try
            {
                writer = directory.getPrintWriter(scriptFileName);
                writer.write(descriptor.getProperty(ScriptReportDescriptor.Prop.script));
            }
            finally
            {
                if (writer != null)
                    writer.close();
            }

            super.serializeToFolder(directory);
        }
        else
            throw new IllegalArgumentException("Cannot serialize a report that hasn't been saved yet");
    }

    protected String getSerializedScriptFileName()
    {
        ScriptEngine engine = getScriptEngine();
        String extension = "script";
        ReportDescriptor descriptor = getDescriptor();

        if (engine != null)
            extension = engine.getFactory().getExtensions().get(0);

        if (descriptor.getReportId() != null)
            return FileUtil.makeLegalName(String.format("%s.%s.%s", descriptor.getReportName(), descriptor.getReportId(), extension));
        else
            return FileUtil.makeLegalName(String.format("%s.%s", descriptor.getReportName(), extension));
    }

    @Override
    public void afterDeserializeFromFile(File reportFile) throws IOException
    {
        if (reportFile.exists())
        {
            // check to see if there is a separate script file on the disk, a separate
            // script file takes precedence over any meta-data based script.

            File scriptFile = new File(reportFile.getParent(), getSerializedScriptFileName());

            if (scriptFile.exists())
            {
                BufferedReader br = null;

                try
                {
                    StringBuilder sb = new StringBuilder();
                    br = new BufferedReader(new FileReader(scriptFile));
                    String l;

                    while ((l = br.readLine()) != null)
                    {
                        sb.append(l);
                        sb.append('\n');
                    }

                    getDescriptor().setProperty(ScriptReportDescriptor.Prop.script, sb.toString());
                }
                finally
                {
                    if (br != null)
                        try {br.close();} catch(IOException ioe) {}
                }
            }
        }
    }

    public static class NADisplayColumn extends DataColumn
    {
        public NADisplayColumn(ColumnInfo col)
        {
            super(col);
            this.setName(col.getPropertyName());
        }

        public NADisplayColumn(String name, ColumnInfo col)
        {
            super(col);
            this.setName(name);
        }

        @Override
        public String getName()
        {
            return _name;
        }

        public String getTsvFormattedValue(RenderContext ctx)
        {
            String value = super.getTsvFormattedValue(ctx);

            if (StringUtils.isEmpty(value))
                return "NA";

            return value;
        }
    }

    protected static class TempFileCleanup extends HttpView
    {
        private String _path;

        public TempFileCleanup(String path)
        {
            _path = path;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            FileUtil.deleteDir(new File(_path));
        }
    }
}
