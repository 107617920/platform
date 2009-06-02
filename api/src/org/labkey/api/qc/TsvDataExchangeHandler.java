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
package org.labkey.api.qc;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts.upload.MultipartRequestHandler;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/*
* User: Karl Lum
* Date: Jan 7, 2009
* Time: 5:16:43 PM
*/
public class TsvDataExchangeHandler implements DataExchangeHandler
{
    public enum Props {
        assayId,                // the assay id from the run properties field
        runComments,            // run properties comments
        containerPath,
        assayType,              // assay definition name : general, nab, elispot etc.
        assayName,              // assay instance name
        userName,               // user email
        workingDir,             // temp directory that the script will be executed from
        protocolId,             // protocol row id
        protocolLsid,
        protocolDescription,

        runDataFile,
        runDataUploadedFile,
        errorsFile,
    }
    public static final String SAMPLE_DATA_PROP_NAME = "sampleData";
    public static final String VALIDATION_RUN_INFO_FILE = "runProperties.tsv";
    public static final String ERRORS_FILE = "validationErrors.tsv";
    public static final String RUN_DATA_FILE = "runData.tsv";

    private Map<String, String> _formFields = new HashMap<String, String>();
    private Map<String, List<Map<String, Object>>> _sampleProperties = new HashMap<String, List<Map<String, Object>>>();
    private Map<DataType, List<Map<String, Object>>> _dataMap;
    private static final Logger LOG = Logger.getLogger(TsvDataExchangeHandler.class);

    public File createValidationRunInfo(AssayRunUploadContext context, ExpRun run, File scriptDir) throws Exception
    {
        File runProps = new File(scriptDir, VALIDATION_RUN_INFO_FILE);
        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(runProps)));

        try {
            // serialize the run properties to a tsv
            writeRunProperties(context, scriptDir, pw);

            // add the run data entries
            writeRunData(context, run, scriptDir, pw);

            // any additional sample property sets
            for (Map.Entry<String, List<Map<String, Object>>> set : _sampleProperties.entrySet())
            {
                File sampleData = new File(scriptDir, set.getKey() + ".tsv");
                writeRunData(set.getValue(), sampleData);

                pw.append(set.getKey());
                pw.append('\t');
                pw.println(sampleData.getAbsolutePath());
            }

            // errors file location
            File errorFile = new File(scriptDir, ERRORS_FILE);
            pw.append(Props.errorsFile.name());
            pw.append('\t');
            pw.println(errorFile.getAbsolutePath());

            return runProps;
        }
        finally
        {
            pw.close();
        }
    }

    protected void writeDataMaps(Map<DataType, List<Map<String, Object>>> dataMap, File scriptDir, PrintWriter pw) throws Exception
    {
        for (Map.Entry<DataType, List<Map<String, Object>>> dataEntry : dataMap.entrySet())
        {
            //File runData = new File(scriptDir, RUN_DATA_FILE);
            File runData = File.createTempFile(Props.runDataFile.name(), ".tsv", scriptDir);
            writeRunData(dataEntry.getValue(), runData);

            pw.append(Props.runDataFile.name());
            pw.append('\t');
            pw.append(runData.getAbsolutePath());
            pw.append('\t');
            pw.println(dataEntry.getKey().getNamespacePrefix());
        }
    }

    /**
     * Writes out a tsv representation of the assay uploaded data.
     */
    protected void writeRunData(AssayRunUploadContext context, ExpRun run, File scriptDir, PrintWriter pw) throws Exception
    {
        TransformResult transform = context.getTransformResult();
        List<File> dataFiles = new ArrayList<File>();

        if (!transform.isEmpty())
            dataFiles.addAll(transform.getTransformedData().values());
        else
            dataFiles.addAll(context.getUploadedData().values());
        assert(dataFiles.size() <= 1);

        ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
        XarContext xarContext = new XarContext("Simple Run Creation", context.getContainer(), context.getUser());

        for (File data : dataFiles)
        {
            ExpData expData = ExperimentService.get().createData(context.getContainer(), context.getProvider().getDataType());
            expData.setRun(run);

            ExperimentDataHandler handler = expData.findDataHandler();
            if (handler instanceof ValidationDataHandler)
            {
                // original data file
                pw.append(Props.runDataUploadedFile.name());
                pw.append('\t');
                pw.append(data.getAbsolutePath());
                pw.append('\n');

                Map<DataType, List<Map<String, Object>>> dataMap = ((ValidationDataHandler)handler).getValidationDataMap(expData, data, info, LOG, xarContext);
                File dir = AssayFileWriter.ensureUploadDirectory(context.getContainer());

                for (Map.Entry<DataType, List<Map<String, Object>>> dataEntry : dataMap.entrySet())
                {
                    File runData = File.createTempFile(Props.runDataFile.name(), ".tsv", scriptDir);
                    writeRunData(dataEntry.getValue(), runData);

                    pw.append(Props.runDataFile.name());
                    pw.append('\t');
                    pw.append(runData.getAbsolutePath());
                    pw.append('\t');
                    pw.append(dataEntry.getKey().getNamespacePrefix());

                    if (handler instanceof TransformDataHandler)
                    {
                        // if the handler supports data transformation, we will include an additional column for the location of
                        // a transformed data file that a transform script may create.
                        File transformedData = AssayFileWriter.createFile(context.getProtocol(), dir, "tsv");

                        pw.append('\t');
                        pw.append(transformedData.getAbsolutePath());
                    }
                    pw.append('\n');
                }
            }
        }
    }

    /**
     *
     * @param propertyName
     * @param propertySet
     */
    public void addSampleProperties(String propertyName, String groupColumnName, Map<String, Map<DomainProperty, String>> propertySet)
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        // convert to a list of row maps

        for (Map.Entry<String, Map<DomainProperty, String>> entry : propertySet.entrySet())
        {
            Map<String, Object> row = new HashMap<String, Object>();

            row.put(groupColumnName, entry.getKey());
            for (Map.Entry<DomainProperty, String> colEntry : entry.getValue().entrySet())
            {
                row.put(colEntry.getKey().getLabel(), colEntry.getValue());
            }
            rows.add(row);
        }
        addSampleProperties(propertyName, rows);
    }

    protected void addSampleProperties(String propertyName, List<Map<String, Object>> rows)
    {
        _sampleProperties.put(propertyName, rows);
    }

    protected void writeRunProperties(AssayRunUploadContext context, File scriptDir, PrintWriter pw)
    {
        Map<DomainProperty, String> runProperties = new HashMap<DomainProperty, String>(context.getRunProperties());
        for (Map.Entry<DomainProperty, String> entry : runProperties.entrySet())
            _formFields.put(entry.getKey().getName(), entry.getValue());

        runProperties.putAll(context.getBatchProperties());

        // serialize the run properties to a tsv
        for (Map.Entry<DomainProperty, String> entry : runProperties.entrySet())
        {
            pw.append(entry.getKey().getName());
            pw.append('\t');
            pw.append(entry.getValue());
            pw.append('\t');
            pw.println(entry.getKey().getPropertyDescriptor().getPropertyType().getJavaType().getName());
        }

        // additional context properties
        for (Map.Entry<String, String> entry : getContextProperties(context, scriptDir).entrySet())
        {
            pw.append(entry.getKey());
            pw.append('\t');
            pw.append(entry.getValue());
            pw.append('\t');
            pw.println(String.class.getName());
        }
    }

    private Map<String, String> getContextProperties(AssayRunUploadContext context, File scriptDir)
    {
        Map<String, String> map = new HashMap<String, String>();

        map.put(Props.assayId.name(), context.getName());
        map.put(Props.runComments.name(), context.getComments());
        map.put(Props.containerPath.name(), context.getContainer().getPath());
        map.put(Props.assayType.name(), context.getProvider().getName());
        map.put(Props.assayName.name(), context.getProtocol().getName());
        map.put(Props.userName.name(), context.getUser().getEmail());
        map.put(Props.workingDir.name(), scriptDir.getAbsolutePath());
        map.put(Props.protocolId.name(), String.valueOf(context.getProtocol().getRowId()));
        map.put(Props.protocolDescription.name(), context.getProtocol().getDescription());
        map.put(Props.protocolLsid.name(), context.getProtocol().getLSID());

        return map;
    }

    public void processValidationOutput(File runInfo) throws ValidationException
    {
        if (runInfo.exists())
        {
            List<ValidationError> errors = new ArrayList<ValidationError>();
            Reader runInfoReader = null;
            Reader errorReader = null;

            try {
                runInfoReader = new BufferedReader(new FileReader(runInfo));
                TabLoader loader = new TabLoader(runInfoReader, false);
                loader.setColumns(new ColumnDescriptor[]{
                        new ColumnDescriptor("name", String.class),
                        new ColumnDescriptor("value", String.class),
                        new ColumnDescriptor("type", String.class)
                });

                File errorFile = null;
                for (Map<String, Object> row : loader)
                {
                    if (row.get("name").equals(Props.errorsFile.name()))
                    {
                        errorFile = new File(row.get("value").toString());
                        break;
                    }
                }

                if (errorFile != null && errorFile.exists())
                {
                    errorReader = new BufferedReader(new FileReader(errorFile));
                    TabLoader errorLoader = new TabLoader(errorReader, false);
                    errorLoader.setColumns(new ColumnDescriptor[]{
                            new ColumnDescriptor("type", String.class),
                            new ColumnDescriptor("property", String.class),
                            new ColumnDescriptor("message", String.class)
                    });

                    for (Map<String, Object> row : errorLoader)
                    {
                        if ("error".equalsIgnoreCase(row.get("type").toString()))
                        {
                            String propName = mapPropertyName(StringUtils.trimToNull((String)row.get("property")));

                            if (propName != null)
                                errors.add(new PropertyValidationError(row.get("message").toString(), propName));
                            else
                                errors.add(new SimpleValidationError(row.get("message").toString()));
                        }
                    }
                }
            }
            catch (Exception e)
            {
                throw new ValidationException(e.getMessage());
            }
            finally
            {
                IOUtils.closeQuietly(errorReader);
                IOUtils.closeQuietly(runInfoReader);
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);
        }
    }

    /**
     * Ensures the property name recorded maps to a valid form field name
     * @param name
     * @return
     */
    protected String mapPropertyName(String name)
    {
        if (Props.assayId.name().equals(name))
            return "name";
        if (Props.runComments.name().equals(name))
            return "comments";
        if (_formFields.containsKey(name))
            return name;

        return null;
    }

    protected void writeRunData(List<Map<String, Object>> data, File runDataFile) throws Exception
    {
        if (data.size() > 0)
        {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(runDataFile)));

            try {
                // write the column header
                List<String> columns = new ArrayList<String>(data.get(0).keySet());
                String sep = "";
                for (String name : columns)
                {
                    pw.append(sep);
                    pw.append(name);
                    sep = "\t";
                }
                pw.println();

                // write the rows
                for (Map<String, Object> row : data)
                {
                    sep = "";
                    for (String name : columns)
                    {
                        Object o = row.get(name);
                        pw.append(sep);
                        if (o != null)
                        {
                            if (Date.class.isAssignableFrom(o.getClass()))
                                pw.append(DateUtil.formatDateTime((Date)o));
                            else
                                pw.append(String.valueOf(o));
                        }
                        sep = "\t";
                    }
                    pw.println();
                }
            }
            finally
            {
                pw.close();
            }
        }
    }

    public void createSampleData(@NotNull ExpProtocol protocol, ViewContext viewContext, File scriptDir) throws Exception
    {
        final int SAMPLE_DATA_ROWS = 5;
        File runProps = new File(scriptDir, VALIDATION_RUN_INFO_FILE);
        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(runProps)));

        try {
            AssayRunUploadContext context = new SampleRunUploadContext(protocol, viewContext);

            writeRunProperties(context, scriptDir, pw);

            // create the sample run data
            AssayProvider provider = AssayService.get().getProvider(protocol);
            List<Map<String, Object>> dataRows = new ArrayList<Map<String, Object>>();

            Domain runDataDomain = provider.getResultsDomain(protocol);
            if (runDataDomain != null)
            {
                DomainProperty[] properties = runDataDomain.getProperties();
                for (int i=0; i < SAMPLE_DATA_ROWS; i++)
                {
                    Map<String, Object> row = new HashMap<String, Object>();
                    for (DomainProperty prop : properties)
                        row.put(prop.getName(), getSampleValue(prop));

                    dataRows.add(row);
                }
                File runData = new File(scriptDir, RUN_DATA_FILE);
                pw.append(Props.runDataFile.name());
                pw.append('\t');
                pw.println(runData.getAbsolutePath());

                writeRunData(new ArrayList<Map<String, Object>>(dataRows), runData);
            }

            // any additional sample property sets
            for (Map.Entry<String, List<Map<String, Object>>> set : _sampleProperties.entrySet())
            {
                File sampleData = new File(scriptDir, set.getKey() + ".tsv");
                writeRunData(set.getValue(), sampleData);

                pw.append(set.getKey());
                pw.append('\t');
                pw.println(sampleData.getAbsolutePath());
            }

            // errors file location
            File errorFile = new File(scriptDir, ERRORS_FILE);
            pw.append(Props.errorsFile.name());
            pw.append('\t');
            pw.println(errorFile.getAbsolutePath());
        }
        finally
        {
            pw.close();
        }
    }

    public File createTransformationRunInfo(AssayRunUploadContext context, ExpRun run, File scriptDir) throws Exception
    {
        // include all of the batch and run properties
        return createValidationRunInfo(context, run, scriptDir);
    }

    protected List<Map<String, Object>> parseRunInfo(File runInfo) throws IOException
    {
        Reader reader = null;
        try {
            reader = new BufferedReader(new FileReader(runInfo));
            TabLoader loader = new TabLoader(reader, false);
            loader.setColumns(new ColumnDescriptor[]{
                    new ColumnDescriptor("name", String.class),
                    new ColumnDescriptor("value", String.class),
                    new ColumnDescriptor("type", String.class),
                    new ColumnDescriptor("transformedData", String.class)
            });
            return loader.load();
        }
        finally
        {
            IOUtils.closeQuietly(reader);
        }
    }

    public TransformResult processTransformationOutput(AssayRunUploadContext context, File runInfo) throws ValidationException
    {
        TransformResult result = DefaultTransformResult.createEmptyResult();

        // check to see if any errors were generated
        processValidationOutput(runInfo);

        // if input data was transformed,
        if (runInfo.exists())
        {
            try {
                List<Map<String, Object>> maps = parseRunInfo(runInfo);
                Map<String, File> transformedData = new HashMap<String, File>();
                for (Map<String, Object> row : maps)
                {
                    Object data = row.get("transformedData");
                    if (data != null)
                    {
                        transformedData.put(String.valueOf(row.get("type")), new File(data.toString()));
                    }
                }

                if (!transformedData.isEmpty())
                {
                    Map<DataType, File> dataMap = new HashMap<DataType, File>();

                    for (Map.Entry<String, File> entry : transformedData.entrySet())
                    {
                        dataMap.put(new DataType(entry.getKey()), entry.getValue());
                    }
                    result = new DefaultTransformResult(dataMap);
                }
            }
            catch (Exception e)
            {
                throw new ValidationException(e.getMessage());
            }
        }
        return result;
    }

    protected static String getSampleValue(DomainProperty prop)
    {
        switch (prop.getPropertyDescriptor().getPropertyType().getSqlType())
        {
            case Types.BOOLEAN :
                return "true";
            case Types.TIMESTAMP:
                DateFormat format = new SimpleDateFormat("MM/dd/yyyy");
                return format.format(new Date());
            case Types.DOUBLE:
            case Types.INTEGER:
                return "1234";
            default:
                return "demo value";
        }
    }

    private static class SampleRunUploadContext implements AssayRunUploadContext
    {
        ExpProtocol _protocol;
        ViewContext _context;

        public SampleRunUploadContext(@NotNull ExpProtocol protocol, ViewContext context)
        {
            _protocol = protocol;
            _context = context;
        }

        @NotNull
        public ExpProtocol getProtocol()
        {
            return _protocol;
        }

        public Map<DomainProperty, String> getRunProperties()
        {
            AssayProvider provider = AssayService.get().getProvider(_protocol);
            Map<DomainProperty, String> runProperties = new HashMap<DomainProperty, String>();

            for (DomainProperty prop : provider.getRunDomain(_protocol).getProperties())
                runProperties.put(prop, getSampleValue(prop));

            return runProperties;
        }

        public Map<DomainProperty, String> getBatchProperties()
        {
            AssayProvider provider = AssayService.get().getProvider(_protocol);
            Map<DomainProperty, String> runProperties = new HashMap<DomainProperty, String>();

            for (DomainProperty prop : provider.getBatchDomain(_protocol).getProperties())
                runProperties.put(prop, getSampleValue(prop));

            return runProperties;
        }

        public String getComments()
        {
            return "sample upload comments";
        }

        public String getName()
        {
            return "sample upload name";
        }

        public User getUser()
        {
            return _context.getUser();
        }

        public Container getContainer()
        {
            return _context.getContainer();
        }

        public HttpServletRequest getRequest()
        {
            return _context.getRequest();
        }

        public ActionURL getActionURL()
        {
            return _context.getActionURL();
        }

        public Map<String, File> getUploadedData() throws IOException, ExperimentException
        {
            return Collections.emptyMap();
        }

        public AssayProvider getProvider()
        {
            return AssayService.get().getProvider(_protocol);
        }

        public MultipartRequestHandler getMultipartRequestHandler()
        {
            return null;
        }

        public Map<DomainProperty, Object> getDefaultValues(Domain domain, String scope) throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public Map<DomainProperty, Object> getDefaultValues(Domain domain) throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public void saveDefaultValues(Map<DomainProperty, String> values, String scope) throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public void saveDefaultValues(Map<DomainProperty, String> values) throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public void clearDefaultValues(Domain domain) throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public String getTargetStudy()
        {
            return null;
        }

        public TransformResult getTransformResult()
        {
            return null;
        }
    }
}