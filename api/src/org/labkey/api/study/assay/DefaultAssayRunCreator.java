/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformDataHandler;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.assay.pipeline.AssayUploadPipelineJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Oct 12, 2011
 */
public class DefaultAssayRunCreator<ProviderType extends AbstractAssayProvider> implements AssayRunCreator<ProviderType>
{
    private static final Logger LOG = Logger.getLogger(DefaultAssayRunCreator.class);

    private final ProviderType _provider;

    public DefaultAssayRunCreator(ProviderType provider)
    {
        _provider = provider;
    }

    public TransformResult transform(AssayRunUploadContext context, ExpRun run) throws ValidationException
    {
        DataTransformer transformer = getDataTransformer();
        if (transformer != null)
            return transformer.transformAndValidate(context, run);

        return DefaultTransformResult.createEmptyResult();
    }

    /**
     * Create and save an experiment run synchronously or asynchronously in a background job depending upon the assay design.
     *
     * @param context The context used to create and save the batch and run.
     * @param batchId if not null, the run group that's already created for this batch. If null, a new one will be created.
     * @return Pair of batch and run that were inserted.  ExpBatch will not be null, but ExpRun may be null when inserting the run async.
     */
    @Override
    public Pair<ExpExperiment, ExpRun> saveExperimentRun(AssayRunUploadContext<ProviderType> context, @Nullable Integer batchId) throws ExperimentException, ValidationException
    {
        ExpExperiment exp = null;
        if (batchId != null)
        {
            exp = ExperimentService.get().getExpExperiment(batchId.intValue());
        }

        AssayProvider provider = context.getProvider();
        ExpProtocol protocol = context.getProtocol();
        ExpRun run = null;

        boolean background = provider.isBackgroundUpload(protocol);
        if (!background)
        {
            File primaryFile = context.getUploadedData().get(AssayDataCollector.PRIMARY_FILE);
            run = AssayService.get().createExperimentRun(context.getName(), context.getContainer(), protocol, primaryFile);
            run.setComments(context.getComments());

            exp = saveExperimentRun(context, exp, run, false);
            context.uploadComplete(run);
        }
        else
        {
            context.uploadComplete(null);
            exp = saveExperimentRunAsync(context, exp);
        }

        return Pair.of(exp, run);
    }

    private ExpExperiment saveExperimentRunAsync(AssayRunUploadContext<ProviderType> context, @Nullable ExpExperiment batch) throws ExperimentException
    {
        try
        {
            // Whether or not we need to save batch properties
            boolean forceSaveBatchProps = false;
            if (batch == null)
            {
                // No batch yet, so make one
                batch = AssayService.get().createStandardBatch(context.getContainer(), null, context.getProtocol());
                batch.save(context.getUser());
                // It's brand new, so we need to eventually set its properties
                forceSaveBatchProps = true;
            }

            // Queue up a pipeline job to do the actual import in the background
            ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());

            File primaryFile = context.getUploadedData().get(AssayDataCollector.PRIMARY_FILE);
            final AssayUploadPipelineJob<ProviderType> pipelineJob = new AssayUploadPipelineJob<ProviderType>(
                    context.getProvider().createRunAsyncContext(context),
                    info,
                    batch,
                    forceSaveBatchProps,
                    PipelineService.get().getPipelineRootSetting(context.getContainer()),
                    primaryFile);

            // Don't queue the job until the transaction is committed, since otherwise the thread
            // that's running the job might start before it can access the job's row in the database.
            ExperimentService.get().getSchema().getScope().addCommitTask(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        PipelineService.get().queueJob(pipelineJob);
                    }
                    catch (PipelineValidationException e)
                    {
                        throw new UnexpectedException(e);
                    }
                }
            });
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }

        return batch;
    }

    /**
     * @param batch if not null, the run group that's already created for this batch. If null, a new one needs to be created
     * @param run The run to save
     * @param forceSaveBatchProps
     * @return the run and batch that were inserted
     */
    public ExpExperiment saveExperimentRun(final AssayRunUploadContext<ProviderType> context, @Nullable ExpExperiment batch, @NotNull ExpRun run, boolean forceSaveBatchProps) throws ExperimentException, ValidationException
    {
        Map<ExpMaterial, String> inputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> inputDatas = new HashMap<ExpData, String>();
        Map<ExpMaterial, String> outputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> outputDatas = new HashMap<ExpData, String>();
        Map<ExpData, String> transformedDatas = new HashMap<ExpData, String>();

        Map<DomainProperty, String> runProperties = context.getRunProperties();
        Map<DomainProperty, String> batchProperties = context.getBatchProperties();

        Map<DomainProperty, String> allProperties = new HashMap<DomainProperty, String>();
        allProperties.putAll(runProperties);
        allProperties.putAll(batchProperties);

        ParticipantVisitResolverType resolverType = null;
        for (Map.Entry<DomainProperty, String> entry : allProperties.entrySet())
        {
            if (entry.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                resolverType = AbstractAssayProvider.findType(entry.getValue(), getProvider().getParticipantVisitResolverTypes());
                if (resolverType != null)
                {
                    resolverType.configureRun(context, run, inputDatas);
                }
                break;
            }
        }

        addInputMaterials(context, inputMaterials, resolverType);
        addInputDatas(context, inputDatas, resolverType);
        addOutputMaterials(context, outputMaterials, resolverType);
        addOutputDatas(context, outputDatas, resolverType);

        resolveParticipantVisits(context, inputMaterials, inputDatas, outputMaterials, outputDatas, allProperties, resolverType);

        DbScope scope = ExperimentService.get().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            boolean saveBatchProps = forceSaveBatchProps;

            // Save the batch first
            if (batch == null)
            {
                // Make sure that we have a batch to associate with this run
                batch = AssayService.get().createStandardBatch(run.getContainer(), null, context.getProtocol());
                batch.save(context.getUser());
                saveBatchProps = true;
            }
            run.save(context.getUser());
            // Add the run to the batch so that we can find it when we're loading the data files
            batch.addRuns(context.getUser(), run);

            ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
            XarContext xarContext = new AssayUploadXarContext("Simple Run Creation", context);

            run = ExperimentService.get().saveSimpleExperimentRun(run,
                    inputMaterials,
                    inputDatas,
                    outputMaterials,
                    outputDatas,
                    transformedDatas,
                    info,
                    LOG,
                    false);

            // handle data transformation
            TransformResult transformResult = transform(context, run);
            List<ExpData> insertedDatas = new ArrayList<ExpData>();

            if (saveBatchProps)
            {
                if (!transformResult.getBatchProperties().isEmpty())
                {
                    Map<DomainProperty, String> props = transformResult.getBatchProperties();
                    List<ValidationError> errors = validateProperties(props);
                    if (!errors.isEmpty())
                        throw new ValidationException(errors);
                    savePropertyObject(batch, props, context.getUser());
                }
                else
                    savePropertyObject(batch, batchProperties, context.getUser());
            }

            if (!transformResult.getRunProperties().isEmpty())
            {
                Map<DomainProperty, String> props = transformResult.getRunProperties();
                List<ValidationError> errors = validateProperties(props);
                if (!errors.isEmpty())
                    throw new ValidationException(errors);
                savePropertyObject(run, props, context.getUser());
            }
            else
                savePropertyObject(run, runProperties, context.getUser());

            importResultData(context, run, inputDatas, outputDatas, info, xarContext, transformResult, insertedDatas);

            if (context.getReRunId() != null && getProvider().supportsReRun())
            {
                final ExpRun replacedRun = ExperimentService.get().getExpRun(context.getReRunId().intValue());
                if (replacedRun != null && replacedRun.getContainer().hasPermission(context.getUser(), UpdatePermission.class))
                {
                    replacedRun.setReplacedByRun(run);
                    replacedRun.save(context.getUser());

                }
                ExperimentService.get().auditRunEvent(context.getUser(), context.getProtocol(), replacedRun, null, "Run id " + replacedRun.getRowId() + " was replaced by run id " + run.getRowId());

                scope.addCommitTask( new Runnable()
                {
                    @Override
                    public void run()
                    {
                        replacedRun.archiveDataFiles(context.getUser());
                    }
                });
            }

            transaction.commit();

            AssayService.get().ensureUniqueBatchName(batch, context.getProtocol(), context.getUser());

            List<String> copyErrors = AssayPublishService.get().autoCopyResults(context.getProtocol(), run, context.getUser(), context.getContainer());
            if (!copyErrors.isEmpty())
            {
                StringBuilder errorMessage = new StringBuilder();
                for (String copyError : copyErrors)
                {
                    errorMessage.append(copyError);
                    errorMessage.append("\n");
                }
                throw new ExperimentException(errorMessage.toString());
            }

            return batch;
        }
    }

    private void resolveParticipantVisits(AssayRunUploadContext<ProviderType> context, Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas, Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, Map<DomainProperty, String> allProperties, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        try
        {
            ParticipantVisitResolver resolver = null;
            if (resolverType != null)
            {
                String targetStudyId = null;
                for (Map.Entry<DomainProperty, String> property : allProperties.entrySet())
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(property.getKey().getName()))
                    {
                        targetStudyId = property.getValue();
                        break;
                    }
                }
                Container targetStudy = null;
                if (targetStudyId != null && targetStudyId.length() > 0)
                    targetStudy = ContainerManager.getForId(targetStudyId);

                resolver = resolverType.createResolver(Collections.unmodifiableCollection(inputMaterials.keySet()),
                        Collections.unmodifiableCollection(inputDatas.keySet()),
                        Collections.unmodifiableCollection(outputMaterials.keySet()),
                        Collections.unmodifiableCollection(outputDatas.keySet()),
                        context.getContainer(),
                        targetStudy, context.getUser());
            }
            resolveExtraRunData(resolver, context, inputMaterials, inputDatas, outputMaterials, outputDatas);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    private void importResultData(AssayRunUploadContext<ProviderType> context, ExpRun run, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, ViewBackgroundInfo info, XarContext xarContext, TransformResult transformResult, List<ExpData> insertedDatas) throws ExperimentException
    {
        if (transformResult.getTransformedData().isEmpty())
        {
            insertedDatas.addAll(inputDatas.keySet());
            insertedDatas.addAll(outputDatas.keySet());

            for (ExpData insertedData : insertedDatas)
            {
                insertedData.findDataHandler().importFile(insertedData, insertedData.getFile(), info, LOG, xarContext);
            }
        }
        else
        {
            ExpData data = ExperimentService.get().createData(context.getContainer(), getProvider().getDataType());
            ExperimentDataHandler handler = data.findDataHandler();

            // this should assert to always be true
            if (handler instanceof TransformDataHandler)
            {
                for (Map.Entry<ExpData, List<Map<String, Object>>> entry : transformResult.getTransformedData().entrySet())
                {
                    ExpData expData = entry.getKey();
                    // The object may have already been claimed by
                    if (expData.getSourceApplication() == null)
                    {
                        expData.setSourceApplication(run.getOutputProtocolApplication());
                    }
                    expData.save(context.getUser());

                    run.getOutputProtocolApplication().addDataInput(context.getUser(), expData, ExpDataRunInput.DEFAULT_ROLE);
                    // Add to the cached list of outputs
                    run.getDataOutputs().add(expData);

                    ((TransformDataHandler)handler).importTransformDataMap(expData, context, run, entry.getValue());
                }
            }
        }
    }

    protected void addInputMaterials(AssayRunUploadContext context, Map<ExpMaterial, String> inputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    protected void addInputDatas(AssayRunUploadContext context, Map<ExpData, String> inputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    public static ExpData createData(Container c, File file, String name, DataType dataType, boolean reuseExistingDatas)
    {
        ExpData data = null;
        if (file != null)
        {
            data = ExperimentService.get().getExpDataByURL(file, c);
        }
        if (!reuseExistingDatas && data != null && data.getRun() != null)
        {
            // There's an existing data, but it's already marked as being created by another run, so create a new one
            // for the same path so the new run claim it as its own
            data = null;
        }
        if (data == null)
        {
            data = ExperimentService.get().createData(c, dataType, name);
            data.setLSID(ExperimentService.get().generateGuidLSID(c, dataType));
            if (file != null)
            {
                data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
            }
        }
        else
        {
            if (!dataType.matches(new Lsid(data.getLSID())))
            {
                // Reset its LSID so that it's the correct type
                data.setLSID(ExperimentService.get().generateGuidLSID(c, dataType));
            }
        }
        return data;
    }

    protected void addOutputMaterials(AssayRunUploadContext context, Map<ExpMaterial, String> outputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    protected void addOutputDatas(AssayRunUploadContext context, Map<ExpData, String> outputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        Map<String, File> files = context.getUploadedData();

        for (Map.Entry<String, File> entry : files.entrySet())
        {
            ExpData data = DefaultAssayRunCreator.createData(context.getContainer(), entry.getValue(), entry.getValue().getName(), getProvider().getDataType(), context.getReRunId() == null);
            outputDatas.put(data, ExpDataRunInput.DEFAULT_ROLE);
        }

        File primaryFile = files.get(AssayDataCollector.PRIMARY_FILE);
        if (primaryFile != null)
        {
            addRelatedOutputDatas(context.getContainer(), outputDatas, primaryFile, Collections.<AssayDataType>emptyList());
        }
    }

    /**
     * Add files that follow the general naming convention (same basename) as the primary file
     * @param knownRelatedDataTypes data types that should be given a particular LSID or role, others file types
     * will have them auto-generated based on their extension
     */
    public void addRelatedOutputDatas(Container container, Map<ExpData, String> outputDatas, final File primaryFile, List<AssayDataType> knownRelatedDataTypes) throws ExperimentException
    {
        final String baseName = getProvider().getDataType().getFileType().getBaseName(primaryFile);
        if (baseName != null)
        {
            // Grab all the files that are related based on naming convention
            File[] relatedFiles = primaryFile.getParentFile().listFiles(getRelatedOutputDataFileFilter(primaryFile, baseName));
            if (relatedFiles != null)
            {
                for (File relatedFile : relatedFiles)
                {
                    Pair<ExpData, String> dataOutput = createdRelatedOutputData(container, knownRelatedDataTypes, baseName, relatedFile);
                    if (dataOutput != null)
                    {
                        outputDatas.put(dataOutput.getKey(), dataOutput.getValue());
                    }
                }
            }
        }
    }

    protected void resolveExtraRunData(ParticipantVisitResolver resolver,
                                  AssayRunUploadContext context,
                                  Map<ExpMaterial, String> inputMaterials,
                                  Map<ExpData, String> inputDatas,
                                  Map<ExpMaterial, String> outputMaterials,
                                  Map<ExpData, String> outputDatas) throws ExperimentException
    {

    }

    /**
     * Create an ExpData object for the file, and figure out what its role name should be
     * @return null if the file is already linked to another run 
     */
    @Nullable
    public static Pair<ExpData, String> createdRelatedOutputData(Container container, List<AssayDataType> knownRelatedDataTypes, String baseName, File relatedFile)
    {
        String roleName = null;
        DataType dataType = null;
        for (AssayDataType inputType : knownRelatedDataTypes)
        {
            // Check if we recognize it as a specially handled file type
            if (inputType.getFileType().isMatch(relatedFile.getName(), baseName))
            {
                roleName = inputType.getRole();
                dataType = inputType;
                break;
            }
        }
        // If not, make up a new type and role for it
        if (roleName == null)
        {
            roleName = relatedFile.getName().substring(baseName.length());
            while (roleName.length() > 0 && (roleName.startsWith(".") || roleName.startsWith("-") || roleName.startsWith("_") || roleName.startsWith(" ")))
            {
                roleName = roleName.substring(1);
            }
            if ("".equals(roleName))
            {
                roleName = null;
            }
            dataType = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;
        }
        ExpData data = createData(container, relatedFile, relatedFile.getName(), dataType, true);
        if (data.getSourceApplication() == null)
        {
            return new Pair<ExpData, String>(data, roleName);
        }

        // The file is already linked to another run, so this one must have not created it
        return null;
    }

    protected void savePropertyObject(ExpObject object, Map<DomainProperty, String> properties, User user) throws ExperimentException
    {
        try
        {
            for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
            {
                DomainProperty pd = entry.getKey();
                String value = entry.getValue();
                // Treat the empty string as a null in the database, which is our normal behavior when receiving data
                // from HTML forms.
                if ("".equals(value))
                {
                    value = null;
                }
                if (value != null)
                {
                    object.setProperty(user, pd.getPropertyDescriptor(), value);
                }
                else
                {
                    // We still need to validate blanks
                    List<ValidationError> errors = new ArrayList<ValidationError>();
                    OntologyManager.validateProperty(pd.getValidators(), pd.getPropertyDescriptor(), value, errors, new ValidatorContext(pd.getContainer(), user));
                    if (!errors.isEmpty())
                        throw new ValidationException(errors);
                }
            }
        }
        catch (ValidationException ve)
        {
            throw new ExperimentException(ve.getMessage(), ve);
        }
    }

    public static List<ValidationError> validateColumnProperties(Map<ColumnInfo, String> properties)
    {
        List<ValidationError> errors = new ArrayList<ValidationError>();
        for (Map.Entry<ColumnInfo, String> entry : properties.entrySet())
        {      
            validateProperty(entry.getValue(), entry.getKey().getName(), false, entry.getKey().getJavaClass(), errors);
        }
        return errors;
    }

    public static List<ValidationError> validateProperties(Map<DomainProperty, String> properties)
    {
        List<ValidationError> errors = new ArrayList<ValidationError>();

        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            DomainProperty dp = entry.getKey();
            String value = entry.getValue();
            String label = dp.getPropertyDescriptor().getNonBlankCaption();
            PropertyType type = dp.getPropertyDescriptor().getPropertyType();
            validateProperty(value, label, dp.isRequired(), type.getJavaType(), errors);
        }
        return errors;
    }

    private static void validateProperty(String value, String label, Boolean required, Class type, List<ValidationError> errors)
    {
        boolean missing = (value == null || value.length() == 0);
        if (required && missing)
        {
            errors.add(new SimpleValidationError(label + " is required and must be of type " + ColumnInfo.getFriendlyTypeName(type) + "."));
        }
        else if (!missing)
        {
            try
            {
                ConvertUtils.convert(value, type);
            }
            catch (ConversionException e)
            {
                String message = label + " must be of type " + ColumnInfo.getFriendlyTypeName(type) + ".";
                message +=  "  Value \"" + value + "\" could not be converted";
                if (e.getCause() instanceof ArithmeticException)
                    message +=  ": " + e.getCause().getLocalizedMessage();
                else
                    message += ".";

                errors.add(new SimpleValidationError(message));
            }
        }
    }

    protected FileFilter getRelatedOutputDataFileFilter(final File primaryFile, final String baseName)
    {
        return new FileFilter()
        {
            public boolean accept(File f)
            {
                // baseName doesn't include the trailing '.', so add it here.  We want to associate myRun.jpg
                // with myRun.xls, but we don't want to associate myRun2.xls with myRun.xls (which will happen without
                // the trailing dot in the check).
                return f.getName().startsWith(baseName + ".") && !primaryFile.equals(f);
            }
        };
    }

    protected ProviderType getProvider()
    {
        return _provider;
    }

    public DataTransformer getDataTransformer()
    {
        return new DefaultDataTransformer();
    }
}
