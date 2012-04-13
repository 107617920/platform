/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
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
import org.labkey.api.study.assay.*;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
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
        transformedRunPropertiesFile,
    }
    public static final String SAMPLE_DATA_PROP_NAME = "sampleData";
    public static final String VALIDATION_RUN_INFO_FILE = "runProperties.tsv";
    public static final String ERRORS_FILE = "validationErrors.tsv";
    public static final String RUN_DATA_FILE = "runData.tsv";
    public static final String TRANSFORMED_RUN_INFO_FILE = "transformedRunProperties.tsv";

    private Map<String, String> _formFields = new HashMap<String, String>();
    private Map<String, List<Map<String, Object>>> _sampleProperties = new HashMap<String, List<Map<String, Object>>>();
    private static final Logger LOG = Logger.getLogger(TsvDataExchangeHandler.class);
    private DataSerializer _serializer = new TsvDataSerializer();

    /** Files that shouldn't be considered part of the run's output, such as the transform script itself */
    private Set<File> _filesToIgnore = new HashSet<File>();

    public DataSerializer getDataSerializer()
    {
        return _serializer;
    }

    public Pair<File, Set<File>> createTransformationRunInfo(AssayRunUploadContext<? extends AssayProvider> context, ExpRun run, File scriptDir, Map<DomainProperty, String> runProperties, Map<DomainProperty, String> batchProperties) throws Exception
    {
        File runProps = new File(scriptDir, VALIDATION_RUN_INFO_FILE);
        _filesToIgnore.add(runProps);
        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(runProps)));

        // Hack to get TSV values to be properly quoted if they include tabs
        TSVWriter writer = createTSVWriter();

        try
        {
            // serialize the run properties to a tsv
            writeRunProperties(context, runProperties, scriptDir, pw, writer);

            // add the run data entries
            Set<File> dataFiles = writeRunData(context, run, scriptDir, pw);

            // any additional sample property sets
            for (Map.Entry<String, List<Map<String, Object>>> set : _sampleProperties.entrySet())
            {
                File sampleData = new File(scriptDir, set.getKey() + ".tsv");
                getDataSerializer().exportRunData(context.getProtocol(), set.getValue(), sampleData);

                pw.append(set.getKey());
                pw.append('\t');
                pw.println(sampleData.getAbsolutePath());
                _filesToIgnore.add(sampleData);
            }

            // errors file location
            File errorFile = new File(scriptDir, ERRORS_FILE);
            pw.append(Props.errorsFile.name());
            pw.append('\t');
            pw.println(errorFile.getAbsolutePath());

            _filesToIgnore.add(errorFile);

            // transformed run properties file location
            File transformedRunPropsFile = new File(scriptDir, TRANSFORMED_RUN_INFO_FILE);
            pw.append(Props.transformedRunPropertiesFile.name());
            pw.append('\t');
            pw.println(transformedRunPropsFile.getAbsolutePath());
            _filesToIgnore.add(transformedRunPropsFile);

            return new Pair<File, Set<File>>(runProps, dataFiles);
        }
        finally
        {
            pw.close();
        }
    }

    /**
     * Writes out a tsv representation of the assay uploaded data.
     */
    protected Set<File> writeRunData(AssayRunUploadContext context, ExpRun run, File scriptDir, PrintWriter pw) throws Exception
    {
        TransformResult transform = context.getTransformResult();
        if (!transform.getTransformedData().isEmpty())
            return _writeTransformedRunData(context, transform, run, scriptDir, pw);
        else
            return _writeRunData(context, run, scriptDir, pw);
    }

    /**
     * Called to write out any uploaded run data in preparation for a validation or transform script.
     */
    private Set<File> _writeRunData(AssayRunUploadContext context, ExpRun run, File scriptDir, PrintWriter pw) throws Exception
    {
        List<File> dataFiles = new ArrayList<File>();

        dataFiles.addAll(context.getUploadedData().values());

        ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
        XarContext xarContext = new AssayUploadXarContext("Simple Run Creation", context);

        Map<DataType, List<Map<String, Object>>> mergedDataMap = new HashMap<DataType, List<Map<String, Object>>>();

        // All of the DataTypes that support
        Set<DataType> transformDataTypes = new HashSet<DataType>();

        for (File data : dataFiles)
        {
            ExpData expData = ExperimentService.get().createData(context.getContainer(), context.getProvider().getDataType(), data.getName());
            expData.setRun(run);

            ExperimentDataHandler handler = expData.findDataHandler();
            if (handler instanceof ValidationDataHandler)
            {
                // original data file
                pw.append(Props.runDataUploadedFile.name());
                pw.append('\t');
                pw.append(data.getAbsolutePath());
                pw.append('\n');
                _filesToIgnore.add(data);

                // for the data map sent to validation or transform scripts, we want to attempt type conversion, but if it fails, return
                // the original field value so the transform script can attempt to clean it up.
                DataLoaderSettings settings = new DataLoaderSettings();
                settings.setBestEffortConversion(true);
                settings.setAllowEmptyData(true);
                settings.setThrowOnErrors(false);

                Map<DataType, List<Map<String, Object>>> dataMap = ((ValidationDataHandler)handler).getValidationDataMap(expData, data, info, LOG, xarContext, settings);

                // Combine the rows of any of the same DataTypes into a single entry
                for (Map.Entry<DataType, List<Map<String, Object>>> entry : dataMap.entrySet())
                {
                    if (mergedDataMap.containsKey(entry.getKey()))
                    {
                        mergedDataMap.get(entry.getKey()).addAll(entry.getValue());
                    }
                    else
                    {
                        mergedDataMap.put(entry.getKey(), entry.getValue());
                    }

                    if (handler instanceof TransformDataHandler)
                    {
                        transformDataTypes.add(entry.getKey());
                    }
                }
            }
        }

        File dir = AssayFileWriter.ensureUploadDirectory(context.getContainer());

        assert mergedDataMap.size() <= 1 : "Multiple input files are only supported if they are of the same type";

        for (Map.Entry<DataType, List<Map<String, Object>>> dataEntry : mergedDataMap.entrySet())
        {
            File runData = new File(scriptDir, Props.runDataFile + ".tsv");
            getDataSerializer().exportRunData(context.getProtocol(), dataEntry.getValue(), runData);
            _filesToIgnore.add(runData);

            pw.append(Props.runDataFile.name());
            pw.append('\t');
            pw.append(runData.getAbsolutePath());
            pw.append('\t');
            pw.append(dataEntry.getKey().getNamespacePrefix());

            if (transformDataTypes.contains(dataEntry.getKey()))
            {
                // if the handler supports data transformation, we will include an additional column for the location of
                // a transformed data file that a transform script may create.
                File transformedData = AssayFileWriter.createFile(context.getProtocol(), dir, "tsv");

                pw.append('\t');
                pw.append(transformedData.getAbsolutePath());
            }
            pw.append('\n');
        }
        return new HashSet<File>(dataFiles);
    }

    /**
     * Called to write out uploaded run data that has been previously transformed.
     */
    private Set<File> _writeTransformedRunData(AssayRunUploadContext context, TransformResult transformResult, ExpRun run, File scriptDir, PrintWriter pw) throws Exception
    {
        assert (!transformResult.getTransformedData().isEmpty());

        // the original uploaded data file
        pw.append(Props.runDataUploadedFile.name());
        pw.append('\t');
        pw.append(transformResult.getUploadedFile().getAbsolutePath());
        pw.append('\n');

        Set<File> result = new HashSet<File>();
        result.add(transformResult.getUploadedFile());

        AssayFileWriter.ensureUploadDirectory(context.getContainer());
        for (Map.Entry<ExpData, List<Map<String, Object>>> entry : transformResult.getTransformedData().entrySet())
        {
            ExpData data = entry.getKey();
            File runData = new File(scriptDir, Props.runDataFile + ".tsv");
            // ask the data serializer to write the data map out to the temp file
            getDataSerializer().exportRunData(context.getProtocol(), entry.getValue(), runData);

            pw.append(Props.runDataFile.name());
            pw.append('\t');
            pw.append(data.getFile().getAbsolutePath());
            result.add(data.getFile());
            pw.append('\t');
            pw.append(data.getLSIDNamespacePrefix());

            // Include an additional column for the location of a transformed data file that a transform script may create.
            File transformedData = AssayFileWriter.createFile(context.getProtocol(), scriptDir, "tsv");

            pw.append('\t');
            pw.append(transformedData.getAbsolutePath());

            pw.append('\n');
        }
        return result;
    }

    protected void addSampleProperties(String propertyName, List<Map<String, Object>> rows)
    {
        _sampleProperties.put(propertyName, rows);
    }

    protected void writeRunProperties(AssayRunUploadContext context, Map<DomainProperty, String> runProperties, File scriptDir, PrintWriter pw, TSVWriter writer) throws ValidationException
    {
        // serialize the run properties to a tsv
        for (Map.Entry<DomainProperty, String> entry : runProperties.entrySet())
        {
            pw.append(writer.quoteValue(entry.getKey().getName()));
            pw.append('\t');
            pw.append(writer.quoteValue(StringUtils.defaultString(entry.getValue())));
            pw.append('\t');
            pw.println(writer.quoteValue(entry.getKey().getPropertyDescriptor().getPropertyType().getJavaType().getName()));
        }

        // additional context properties
        for (Map.Entry<String, String> entry : getContextProperties(context, scriptDir).entrySet())
        {
            pw.append(writer.quoteValue(entry.getKey()));
            pw.append('\t');
            pw.append(writer.quoteValue(entry.getValue()));
            pw.append('\t');
            pw.println(writer.quoteValue(String.class.getName()));
        }
    }

    public Map<DomainProperty, String> getRunProperties(AssayRunUploadContext context) throws ExperimentException
    {
        Map<DomainProperty, String> runProperties = new HashMap<DomainProperty, String>(context.getRunProperties());
        for (Map.Entry<DomainProperty, String> entry : runProperties.entrySet())
            _formFields.put(entry.getKey().getName(), entry.getValue());

        runProperties.putAll(context.getBatchProperties());
        return runProperties;
    }

    private Map<String, String> getContextProperties(AssayRunUploadContext context, File scriptDir)
    {
        Map<String, String> map = new HashMap<String, String>();

        map.put(Props.assayId.name(), StringUtils.defaultString(context.getName()));
        map.put(Props.runComments.name(), StringUtils.defaultString(context.getComments()));
        map.put(Props.containerPath.name(), context.getContainer().getPath());
        map.put(Props.assayType.name(), context.getProvider().getName());
        map.put(Props.assayName.name(), context.getProtocol().getName());
        map.put(Props.userName.name(), StringUtils.defaultString(context.getUser().getEmail()));
        map.put(Props.workingDir.name(), scriptDir.getAbsolutePath());
        map.put(Props.protocolId.name(), String.valueOf(context.getProtocol().getRowId()));
        map.put(Props.protocolDescription.name(), StringUtils.defaultString(context.getProtocol().getDescription()));
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
                // Don't unescape file path names on windows (C:\foo\bar.tsv)
                loader.setUnescapeBackslashes(false);
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

    public void createSampleData(@NotNull ExpProtocol protocol, ViewContext viewContext, File scriptDir) throws Exception
    {
        final int SAMPLE_DATA_ROWS = 5;
        File runProps = new File(scriptDir, VALIDATION_RUN_INFO_FILE);
        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(runProps)));

        // Hack to get TSV values to be properly quoted if they include tabs
        TSVWriter writer = createTSVWriter();

        try
        {
            AssayRunUploadContext<? extends AssayProvider> context = new SampleRunUploadContext(protocol, viewContext);

            writeRunProperties(context, context.getRunProperties(), scriptDir, pw, writer);

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

                getDataSerializer().exportRunData(protocol, new ArrayList<Map<String, Object>>(dataRows), runData);
            }

            // any additional sample property sets
            for (Map.Entry<String, List<Map<String, Object>>> set : _sampleProperties.entrySet())
            {
                File sampleData = new File(scriptDir, set.getKey() + ".tsv");
                getDataSerializer().exportRunData(protocol, set.getValue(), sampleData);

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

    /** Hack to get output TSV to properly quote values with tabs in them */
    private TSVWriter createTSVWriter()
    {
        return new TSVWriter()
        {
            @Override
            protected void write()
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    protected List<Map<String, Object>> parseRunInfo(File runInfo) throws IOException
    {
        Reader reader = null;
        try {
            reader = new BufferedReader(new FileReader(runInfo));
            TabLoader loader = new TabLoader(reader, false);
            // Don't unescape file path names on windows (C:\foo\bar.tsv)
            loader.setUnescapeBackslashes(false);
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

    protected boolean isIgnorableOutput(File file)
    {
        return _filesToIgnore.contains(file);
    }

    public TransformResult processTransformationOutput(AssayRunUploadContext<? extends AssayProvider> context, File runInfo, ExpRun run, File scriptFile, TransformResult mergeResult, Set<File> inputDataFiles) throws ValidationException
    {
        DefaultTransformResult result = new DefaultTransformResult(mergeResult);
        _filesToIgnore.add(scriptFile);

        // check to see if any errors were generated
        processValidationOutput(runInfo);

        // Find the output step for the run
        ExpProtocolApplication outputProtocolApplication = null;
        for (ExpProtocolApplication protocolApplication : run.getProtocolApplications())
        {
            if (protocolApplication.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput)
            {
                outputProtocolApplication = protocolApplication;
            }
        }

        // Create an extra ProtocolApplication that represents the script invocation
        ExpProtocolApplication scriptPA = ExperimentService.get().createSimpleRunExtraProtocolApplication(run, scriptFile.getName());
        scriptPA.save(context.getUser());

        // Wire up the script's inputs 
        for (File dataFile : inputDataFiles)
        {
            ExpData data = ExperimentService.get().getExpDataByURL(dataFile, context.getContainer());
            if (data == null)
            {
                data = ExperimentService.get().createData(context.getContainer(), context.getProvider().getDataType(), dataFile.getName());
                data.setLSID(new Lsid(ExpData.DEFAULT_CPAS_TYPE, GUID.makeGUID()));
                data.setDataFileURI(dataFile.toURI());
                data.save(context.getUser());
            }
            scriptPA.addDataInput(context.getUser(), data, "Data");
        }

        // if input data was transformed,
        if (runInfo.exists())
        {
            Reader runPropsReader = null;
            try {
                List<Map<String, Object>> maps = parseRunInfo(runInfo);
                Map<String, File> transformedData = new HashMap<String, File>();
                File transformedRunProps = null;
                File runDataUploadedFile = null;

                for (Map<String, Object> row : maps)
                {
                    Object data = row.get("transformedData");
                    if (data != null)
                    {
                        File transformedFile = new File(data.toString());
                        if (transformedFile.exists())
                        {
                            transformedData.put(String.valueOf(row.get("type")), transformedFile);
                            _filesToIgnore.add(transformedFile);
                        }
                    }
                    else if (String.valueOf(row.get("name")).equalsIgnoreCase(Props.transformedRunPropertiesFile.name()))
                    {
                        transformedRunProps = new File(row.get("value").toString());
                    }
                    else if (String.valueOf(row.get("name")).equalsIgnoreCase(Props.runDataUploadedFile.name()))
                    {
                        runDataUploadedFile = new File(row.get("value").toString());
                    }
                }

                // Look through all of the files that are left after running the transform script
                for (File file : runInfo.getParentFile().listFiles())
                {
                    if (!isIgnorableOutput(file) && runDataUploadedFile != null)
                    {
                        int extensionIndex = runDataUploadedFile.getName().lastIndexOf(".");
                        String baseName = extensionIndex >= 0 ? runDataUploadedFile.getName().substring(0, extensionIndex) : runDataUploadedFile.getName();

                        // Figure out a unique file name
                        File targetFile;
                        int index = 0;
                        do
                        {
                            targetFile = new File(runDataUploadedFile.getParentFile(), baseName + (index == 0 ? "" : ("-" + index)) + "." + file.getName());
                            index++;
                        }
                        while (targetFile.exists());

                        // Copy the file to the same directory as the original data file
                        FileUtils.moveFile(file, targetFile);

                        // Add the file as an output to the run, and as being created by the script
                        Pair<ExpData,String> outputData = DefaultAssayRunCreator.createdRelatedOutputData(context.getContainer(), Collections.<AssayDataType>emptyList(), baseName, targetFile);
                        if (outputData != null)
                        {
                            outputData.getKey().setSourceApplication(scriptPA);
                            outputData.getKey().save(context.getUser());

                            outputProtocolApplication.addDataInput(context.getUser(), outputData.getKey(), outputData.getValue());
                        }
                    }
                }

                if (!transformedData.isEmpty())
                {
                    // found some transformed data, create the ExpData objects and return in the transform result
                    Map<ExpData, List<Map<String, Object>>> dataMap = new HashMap<ExpData, List<Map<String, Object>>>();

                    for (Map.Entry<String, File> entry : transformedData.entrySet())
                    {
                        ExpData data = ExperimentService.get().getExpDataByURL(entry.getValue(), context.getContainer());
                        if (data == null)
                        {
                            data = DefaultAssayRunCreator.createData(context.getContainer(), entry.getValue(), "transformed output", new DataType(entry.getKey()));
                            data.setName(entry.getValue().getName());
                        }

                        dataMap.put(data, getDataSerializer().importRunData(context.getProtocol(), entry.getValue()));

                        data.setSourceApplication(scriptPA);
                        data.save(context.getUser());
                    }
                    result = new DefaultTransformResult(dataMap);
                }

                if (transformedRunProps != null && transformedRunProps.exists())
                {
                    Map<String, String> transformedProps = new HashMap<String, String>();
                    for (Map<String, Object> row : parseRunInfo(transformedRunProps))
                    {
                        String name = String.valueOf(row.get("name"));
                        String value = String.valueOf(row.get("value"));

                        if (name != null && value != null)
                            transformedProps.put(name, value);
                    }

                    // merge the transformed props with the props in the upload form
                    Map<DomainProperty, String> runProps = new HashMap<DomainProperty, String>();
                    Map<DomainProperty, String> batchProps = new HashMap<DomainProperty, String>();
                    boolean runPropTransformed = false;
                    boolean batchPropTransformed = false;
                    for (Map.Entry<DomainProperty, String> entry : getRunProperties(context).entrySet())
                    {
                        String propName = entry.getKey().getName();
                        if (transformedProps.containsKey(propName))
                        {
                            runProps.put(entry.getKey(), transformedProps.get(propName));
                            runPropTransformed = true;
                        }
                        else
                            runProps.put(entry.getKey(), entry.getValue());
                    }

                    for (Map.Entry<DomainProperty, String> entry : context.getBatchProperties().entrySet())
                    {
                        String propName = entry.getKey().getName();
                        if (transformedProps.containsKey(propName))
                        {
                            batchProps.put(entry.getKey(), transformedProps.get(propName));
                            batchPropTransformed = true;
                        }
                        else
                            batchProps.put(entry.getKey(), entry.getValue());
                    }

                    if (runPropTransformed)
                        result.setRunProperties(runProps);
                    if (batchPropTransformed)
                        result.setBatchProperties(batchProps);
                }
                if (runDataUploadedFile != null)
                    result.setUploadedFile(runDataUploadedFile);
            }
            catch (Exception e)
            {
                throw new ValidationException(e.getMessage());
            }
            finally
            {
                IOUtils.closeQuietly(runPropsReader);
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

    private static class SampleRunUploadContext implements AssayRunUploadContext<AssayProvider>
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

        public Map<DomainProperty, String> getRunProperties() throws ExperimentException
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

        @NotNull
        public Map<String, File> getUploadedData() throws IOException, ExperimentException
        {
            return Collections.emptyMap();
        }

        public AssayProvider getProvider()
        {
            return AssayService.get().getProvider(_protocol);
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

        public void saveDefaultBatchValues() throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public void saveDefaultRunValues() throws ExperimentException
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

        @Override
        public void setTransformResult(TransformResult result)
        {
            throw new UnsupportedOperationException();
        }
    }
}