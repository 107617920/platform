/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Filter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.IAssayDomainType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpQCFlagTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.ui.PropertiesEditorUtil;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.actions.AssayDetailRedirectAction;
import org.labkey.api.study.actions.AssayResultDetailsAction;
import org.labkey.api.study.actions.AssayRunDetailsAction;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.DesignerAction;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.assay.pipeline.AssayRunAsyncContext;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Sep 14, 2007
 */
public abstract class AbstractAssayProvider implements AssayProvider
{
    public static final String ASSAY_NAME_SUBSTITUTION = "${AssayName}";
    public static final String TARGET_STUDY_PROPERTY_NAME = "TargetStudy";
    public static final String TARGET_STUDY_PROPERTY_CAPTION = "Target Study";

    public static final String PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME = "ParticipantVisitResolver";
    public static final String PARTICIPANT_VISIT_RESOLVER_PROPERTY_CAPTION = "Participant Visit Resolver";

    public static final String PARTICIPANTID_PROPERTY_NAME = "ParticipantID";
    public static final String VISITID_PROPERTY_NAME = "VisitID";
    public static final String PARTICIPANTID_PROPERTY_CAPTION = "Participant ID";
    public static final String VISITID_PROPERTY_CAPTION = "Visit ID";
    public static final String SPECIMENID_PROPERTY_NAME = "SpecimenID";
    public static final String SPECIMENID_PROPERTY_CAPTION = "Specimen ID";
    public static final String DATE_PROPERTY_NAME = "Date";
    public static final String DATE_PROPERTY_CAPTION = "Date";

    public static final String ASSAY_SPECIMEN_MATCH_COLUMN_NAME = "AssayMatch";

    public static final String IMPORT_DATA_LINK_NAME = "Import Data";

    public static final FieldKey BATCH_ROWID_FROM_RUN = FieldKey.fromParts(AssayService.BATCH_COLUMN_NAME, "RowId");

    public static final DataType RELATED_FILE_DATA_TYPE = new DataType("RelatedFile");
    public static final String SAVE_SCRIPT_FILES_PROPERTY_SUFFIX = "SaveScriptFiles";
    public static final String EDITABLE_RUNS_PROPERTY_SUFFIX = "EditableRuns";
    public static final String EDITABLE_RESULTS_PROPERTY_SUFFIX = "EditableResults";
    public static final String BACKGROUND_UPLOAD_PROPERTY_SUFFIX = "BackgroundUpload";

    protected final String _protocolLSIDPrefix;
    protected final String _runLSIDPrefix;
    protected final AssayDataType _dataType;

    public int _maxFileInputs = 1;

    public AbstractAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, AssayDataType dataType)
    {
        _dataType = dataType;
        _protocolLSIDPrefix = protocolLSIDPrefix;
        _runLSIDPrefix = runLSIDPrefix;
        registerLsidHandler();
    }

    public AssaySchema getProviderSchema(User user, Container container, ExpProtocol protocol)
    {
        return null;
    }

    @Override
    public String getResourceName()
    {
        return getName();
    }

    public ActionURL copyToStudy(User user, Container assayDataContainer, ExpProtocol protocol, @Nullable Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addInClause(getTableMetadata(protocol).getResultRowIdFieldKey().toString(), dataKeys.keySet());

            AssaySchema schema = AssayService.get().createSchema(user, assayDataContainer);
            ContainerFilterable dataTable = createDataTable(schema, protocol, true);
            dataTable.setContainerFilter(new ContainerFilter.CurrentAndSubfolders(user));

            FieldKey objectIdFK = getTableMetadata(protocol).getResultRowIdFieldKey();
            FieldKey runLSIDFK = new FieldKey(getTableMetadata(protocol).getRunFieldKeyFromResults(), ExpRunTable.Column.LSID.toString());

            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(dataTable, Arrays.asList(objectIdFK, runLSIDFK));
            ColumnInfo rowIdColumn = columns.get(objectIdFK);
            ColumnInfo runLSIDColumn = columns.get(runLSIDFK);

            SQLFragment sql = QueryService.get().getSelectSQL(dataTable, columns.values(), filter, null, Table.ALL_ROWS, Table.NO_OFFSET, false);

            List<Map<String, Object>> dataMaps = new ArrayList<Map<String, Object>>();
            Container sourceContainer = null;
            ResultSet rs = null;

            Map<Container, Set<Integer>> rowIdsByTargetContainer = new HashMap<Container, Set<Integer>>();

            try
            {
                rs = Table.executeQuery(dataTable.getSchema(), sql);

                while (rs.next())
                {
                    AssayPublishKey publishKey = dataKeys.get(((Number)rowIdColumn.getValue(rs)).intValue());

                    Container targetStudyContainer = study;
                    if (publishKey.getTargetStudy() != null)
                        targetStudyContainer = publishKey.getTargetStudy();
                    assert targetStudyContainer != null;

                    TimepointType studyType = AssayPublishService.get().getTimepointType(targetStudyContainer);

                    Map<String, Object> dataMap = new HashMap<String, Object>();

                    String runLSID = (String)runLSIDColumn.getValue(rs);
                    String sourceLSID = getSourceLSID(runLSID, publishKey.getDataId());

                    if (sourceContainer == null)
                    {
                        sourceContainer = ExperimentService.get().getExpRun(runLSID).getContainer();
                    }

                    dataMap.put(AssayPublishService.PARTICIPANTID_PROPERTY_NAME, publishKey.getParticipantId());

                    if (!studyType.isVisitBased())
                    {
                        dataMap.put(AssayPublishService.DATE_PROPERTY_NAME, publishKey.getDate());
                    }
                    else
                    {
                        // add the sequencenum only for visit-based studies, a date based sequencenum will get calculated
                        // for date-based studies in the ETL layer
                        dataMap.put(AssayPublishService.SEQUENCENUM_PROPERTY_NAME, publishKey.getVisitId());
                    }

                    dataMap.put(AssayPublishService.SOURCE_LSID_PROPERTY_NAME, sourceLSID);
                    dataMap.put(getTableMetadata(protocol).getDatasetRowIdPropertyName(), publishKey.getDataId());
                    dataMap.put(AssayPublishService.TARGET_STUDY_PROPERTY_NAME, targetStudyContainer);

                    // Remember which rows we're planning to copy, partitioned by the target study
                    Set<Integer> rowIds = rowIdsByTargetContainer.get(targetStudyContainer);
                    if (rowIds == null)
                    {
                        rowIds = new HashSet<Integer>();
                        rowIdsByTargetContainer.put(targetStudyContainer, rowIds);
                    }
                    rowIds.add(publishKey.getDataId());

                    dataMaps.add(dataMap);
                }

                checkForAlreadyCopiedRows(user, protocol, study, errors, rowIdsByTargetContainer);

                if (!errors.isEmpty())
                {
                    return null;
                }
                
                return AssayPublishService.get().publishAssayData(user, sourceContainer, study, protocol.getName(), protocol,
                        dataMaps, getTableMetadata(protocol).getDatasetRowIdPropertyName(), errors);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private void checkForAlreadyCopiedRows(User user, ExpProtocol protocol, Container study, List<String> errors, Map<Container, Set<Integer>> rowIdsByTargetContainer)
    {
        for (Map.Entry<Container, Set<Integer>> entry : rowIdsByTargetContainer.entrySet())
        {
            Study targetStudy = StudyService.get().getStudy(entry.getKey());

            // Look for an existing dataset backed by this assay definition
            for (DataSet dataSet : targetStudy.getDataSets())
            {
                if (protocol.equals(dataSet.getAssayProtocol()) && dataSet.getKeyPropertyName() != null)
                {
                    // Check to see if it already has the data rows that are being copied
                    TableInfo tableInfo = dataSet.getTableInfo(user, false);
                    Filter datasetFilter = new SimpleFilter(new SimpleFilter.InClause(dataSet.getKeyPropertyName(), entry.getValue()));
                    long existingRowCount = new TableSelector(tableInfo, Table.ALL_COLUMNS, datasetFilter, null).getRowCount();
                    if (existingRowCount > 0)
                    {
                        // If so, don't let the user copy them again, even if they have different participant/visit/date info
                        String errorMessage = existingRowCount == 1 ? "One of the selected rows has" : (existingRowCount + " of the selected rows have");
                        errorMessage += " already been copied to the study \"" + study.getName() + "\" in " + entry.getKey().getPath();
                        errorMessage += " (RowIds: " + entry.getValue() + ")";
                        errors.add(errorMessage);
                    }
                }
            }
        }
    }

    protected String getSourceLSID(String runLSID, int dataId)
    {
        return runLSID;
    }

    protected void registerLsidHandler()
    {
        LsidManager.get().registerHandler(_runLSIDPrefix, new LsidManager.ExpRunLsidHandler());
    }

    public Priority getPriority(ExpProtocol protocol)
    {
        if (ExpProtocol.ApplicationType.ExperimentRun.equals(protocol.getApplicationType()))
        {
            Lsid lsid = new Lsid(protocol.getLSID());
            if (_protocolLSIDPrefix.equals(lsid.getNamespacePrefix()))
            {
                return Priority.HIGH;
            }
        }
        return null;
    }

    public abstract AssayTableMetadata getTableMetadata(ExpProtocol protocol);

    public static String getDomainURIForPrefix(ExpProtocol protocol, String domainPrefix)
    {
        String result = null;
        for (String uri : protocol.getObjectProperties().keySet())
        {
            Lsid uriLSID = new Lsid(uri);
            if (uriLSID.getNamespacePrefix() != null && uriLSID.getNamespacePrefix().startsWith(domainPrefix))
            {
                if (result == null)
                {
                    result = uri;
                }
                else
                {
                    throw new IllegalStateException("More than one domain matches for prefix '" + domainPrefix + "' in protocol with LSID '" + protocol.getLSID() + "'");
                }
            }
        }
        if (result == null)
        {
            throw new IllegalArgumentException("No domain match for prefix '" + domainPrefix + "' in protocol with LSID '" + protocol.getLSID() + "'");
        }
        return result;
    }

    public static Domain getDomainByPrefix(ExpProtocol protocol, String domainPrefix)
    {
        Container container = protocol.getContainer();
        return PropertyService.get().getDomain(container, getDomainURIForPrefix(protocol, domainPrefix));
    }

    public Domain getResultsDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);
    }

    public Domain getBatchDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_BATCH);
    }

    public Domain getRunDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_RUN);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Integer value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.INTEGER, dataMap, types);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Double value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.DOUBLE, dataMap, types);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Boolean value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.BOOLEAN, dataMap, types);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Date value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.DATE_TIME, dataMap, types);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, String value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.STRING, dataMap, types);
    }

    protected PropertyDescriptor addProperty(PropertyDescriptor pd, ObjectProperty value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(pd, value == null ? null : value.getValueMvAware(), dataMap, types);
    }

    protected PropertyDescriptor addProperty(PropertyDescriptor pd, Object value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        dataMap.put(pd.getName(), value);
        if (types != null)
            types.add(pd);
        return pd;
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Object value, PropertyType type, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(createPublishPropertyDescriptor(sourceContainer, name, type), value, dataMap, types);
    }

    protected PropertyDescriptor createPublishPropertyDescriptor(Container sourceContainer, String name, PropertyType type)
    {
        String label = name;
        if (name.contains(" "))
            name = name.replace(" ", "");
        PropertyDescriptor pd = new PropertyDescriptor(null, type.getTypeUri(), name, label, sourceContainer);
        if (type.getJavaType() == Double.class)
            pd.setFormat("0.###");
        return pd;
    }

    protected DomainProperty addProperty(Domain domain, String name, PropertyType type)
    {
        return addProperty(domain, name, name, type);
    }

    protected DomainProperty addProperty(Domain domain, String name, String label, PropertyType type)
    {
        return addProperty(domain, name, label, type, null);
    }

    protected DomainProperty addProperty(Domain domain, String name, String label, PropertyType type, String description)
    {
        DomainProperty prop = domain.addProperty();
        prop.setLabel(label);
        prop.setName(name);
        prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
        prop.setDescription(description);
        if (AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(prop.getName()))
            prop.setDimension(true);
        if (AbstractAssayProvider.VISITID_PROPERTY_NAME.equals(prop.getName()))
            prop.setMeasure(false);

        if (allowDefaultValues(domain))
        {
            if (AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(prop.getName()) ||
                AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(prop.getName()) ||
                AbstractAssayProvider.VISITID_PROPERTY_NAME.equals(prop.getName()) ||
                AbstractAssayProvider.DATE_PROPERTY_NAME.equals(prop.getName()))
            {
                prop.setDefaultValueTypeEnum(DefaultValueType.FIXED_EDITABLE);
            }
            else
            {
                prop.setDefaultValueTypeEnum(getDefaultValueDefault(domain));
            }
        }
        return prop;
    }

    public static String getPresubstitutionLsid(String prefix)
    {
        return "urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":" + ASSAY_NAME_SUBSTITUTION;
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Domain domain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_RUN), "Run Fields");
        domain.setDescription("The user is prompted to enter run level properties for each file they import.  This is the second step of the import process.");
        return new Pair<Domain, Map<DomainProperty, Object>>(domain, Collections.<DomainProperty, Object>emptyMap());
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createBatchDomain(Container c, User user)
    {
        Domain domain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_BATCH), "Batch Fields");
        List<ParticipantVisitResolverType> resolverTypes = getParticipantVisitResolverTypes();
        if (resolverTypes != null && resolverTypes.size() > 0)
        {
            DomainProperty resolverProperty = addProperty(domain, PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME, PARTICIPANT_VISIT_RESOLVER_PROPERTY_CAPTION, PropertyType.STRING);
            resolverProperty.setRequired(true);
            resolverProperty.setHidden(true);
        }

        DomainProperty studyProp = addProperty(domain, TARGET_STUDY_PROPERTY_NAME, TARGET_STUDY_PROPERTY_CAPTION, PropertyType.STRING);
        studyProp.setShownInInsertView(true);

        domain.setDescription("The user is prompted for batch properties once for each set of runs they import. The batch " +
                "is a convenience to let users set properties that seldom change in one place and import many runs " +
                "using them. This is the first step of the import process.");
        return new Pair<Domain, Map<DomainProperty, Object>>(domain, Collections.<DomainProperty, Object>emptyMap());
    }

    /**
     * @return domains and their default property values
     */
    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = new ArrayList<Pair<Domain, Map<DomainProperty, Object>>>();
        try
        {
            ExperimentService.get().ensureTransaction();

            result.add(createBatchDomain(c, user));
            result.add(createRunDomain(c, user));

            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }

        return result;
    }

    public List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles, AssayRunUploadForm context)
    {
        return getDataCollectors(uploadedFiles, context, true);
    }

    public List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles, AssayRunUploadForm context, boolean allowFileReuseOnReRun)
    {
        List<AssayDataCollector> result = new ArrayList<AssayDataCollector>();
        if (!PipelineDataCollector.getFileQueue(context).isEmpty())
        {
            result.add(new PipelineDataCollector());
        }
        else
        {
            if (allowFileReuseOnReRun && supportsReRun() && context.getReRun() != null)
            {
                if (uploadedFiles != null && !uploadedFiles.isEmpty())
                {
                    result.add(new PreviouslyUploadedDataCollector(uploadedFiles));
                }
                else
                {
                    ExpData[] inputDatas = context.getReRun().getInputDatas(ExpDataRunInput.DEFAULT_ROLE, ExpProtocol.ApplicationType.ExperimentRunOutput);
                    if (inputDatas.length == 1)
                    {
                        Map<String, File> oldFiles = new HashMap<String, File>();
                        oldFiles.put(AssayDataCollector.PRIMARY_FILE, inputDatas[0].getFile());
                        result.add(new PreviouslyUploadedDataCollector(oldFiles));
                    }
                    else if (inputDatas.length > 1)
                    {
                        throw new IllegalStateException("More than one primary data file associated with run: " + context.getReRun().getRowId());
                    }
                }
            }
            else if (uploadedFiles != null)
            {
                result.add(new PreviouslyUploadedDataCollector(uploadedFiles));
            }
            result.add(new FileUploadDataCollector(getMaxFileInputs()));
        }
        return result;
    }

    @Override
    public AssayRunCreator getRunCreator()
    {
        return new DefaultAssayRunCreator<AbstractAssayProvider>(this);
    }

    public ExpProtocol createAssayDefinition(User user, Container container, String name, String description)
            throws ExperimentException
    {
        String protocolLsid = new Lsid(_protocolLSIDPrefix, "Folder-" + container.getRowId(), name).toString();

        ExpProtocol protocol = ExperimentService.get().createExpProtocol(container, ExpProtocol.ApplicationType.ExperimentRun, name);
        protocol.setProtocolDescription(description);
        protocol.setLSID(protocolLsid);
        protocol.setMaxInputMaterialPerInstance(1);
        protocol.setMaxInputDataPerInstance(1);

        return ExperimentService.get().insertSimpleProtocol(protocol, user);
    }

    public Pair<ExpProtocol.AssayDomainTypes, DomainProperty> findTargetStudyProperty(ExpProtocol protocol)
    {
        DomainProperty targetStudyDP;

        Domain domain = getResultsDomain(protocol);
        if (domain != null && null != (targetStudyDP = domain.getPropertyByName(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME)))
            return new Pair<ExpProtocol.AssayDomainTypes, DomainProperty>(ExpProtocol.AssayDomainTypes.Result, targetStudyDP);

        domain = getRunDomain(protocol);
        if (domain != null && null != (targetStudyDP = domain.getPropertyByName(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME)))
            return new Pair<ExpProtocol.AssayDomainTypes, DomainProperty>(ExpProtocol.AssayDomainTypes.Run, targetStudyDP);

        domain = getBatchDomain(protocol);
        if (domain != null && null != (targetStudyDP = domain.getPropertyByName(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME)))
            return new Pair<ExpProtocol.AssayDomainTypes, DomainProperty>(ExpProtocol.AssayDomainTypes.Batch, targetStudyDP);

        return null;
    }

    // CONSIDER: combining with .getTargetStudy()
    // UNDONE: Doesn't look at TargetStudy in Results domain yet.
    public Container getAssociatedStudyContainer(ExpProtocol protocol, Object dataId)
    {
        Pair<ExpProtocol.AssayDomainTypes, DomainProperty> pair = findTargetStudyProperty(protocol);
        if (pair == null)
            return null;

        DomainProperty targetStudyColumn = pair.second;

        ExpData data = getDataForDataRow(dataId, protocol);
        if (data == null)
            return null;
        ExpRun run = data.getRun();
        if (run == null)
            return null;

        ExpObject source;
        switch (pair.first)
        {

            case Run:
                source = run;
                break;

            case Result:
                // Ignore Results domain TargetStudy for now.
                // The participant resolver will find the TargetStudy on the row.
            case Batch:
            default:
                source = AssayService.get().findBatch(run);
                break;
        }

        if (source != null)
        {
            Map<String, Object> properties = OntologyManager.getProperties(source.getContainer(), source.getLSID());
            String targetStudyId = (String) properties.get(targetStudyColumn.getPropertyURI());

            if (targetStudyId != null)
                return ContainerManager.getForId(targetStudyId);
        }
        
        return null;
    }

    public abstract ExpData getDataForDataRow(Object dataRowId, ExpProtocol protocol);


    public ActionURL getImportURL(Container container, ExpProtocol protocol)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(container, protocol, UploadWizardAction.class);
    }

    @Override
    public ExpQCFlagTable createQCFlagTable(AssaySchema schema, ExpProtocol protocol)
    {
        ExpQCFlagTable table = ExperimentService.get().createQCFlagsTable(AssaySchema.getQCFlagTableName(protocol), schema);
        table.populate();
        table.setAssayProtocol(protocol);
        
        return table;
    }

    public ExpRunTable createRunTable(AssaySchema schema, ExpProtocol protocol)
    {
        ExpRunTable runTable = ExperimentService.get().createRunTable(AssaySchema.getRunsTableName(protocol), schema);
        if (isEditableRuns(protocol))
        {
            runTable.addAllowablePermission(UpdatePermission.class);
        }
        runTable.populate();

        runTable.addColumn(new AssayQCFlagColumn(runTable, protocol.getName()));
        ColumnInfo qcEnabled = runTable.addColumn(new ExprColumn(runTable, "QCFlagsEnabled", AssayQCFlagColumn.createSQLFragment(runTable.getSqlDialect(), "Enabled"), JdbcType.VARCHAR));
        qcEnabled.setLabel("QC Flags Enabled State");
        qcEnabled.setHidden(true);

        ColumnInfo dataLinkColumn = runTable.getColumn(ExpRunTable.Column.Name);
        dataLinkColumn.setLabel("Assay Id");
        dataLinkColumn.setDescription("The assay/experiment ID that uniquely identifies this assay run.");
        dataLinkColumn.setURL(new DetailsURL(new ActionURL(AssayDetailRedirectAction.class, schema.getContainer()), Collections.singletonMap("runId", "rowId")));

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>(runTable.getDefaultVisibleColumns());
        visibleColumns.remove(FieldKey.fromParts(ExpRunTable.Column.Protocol));
        runTable.setDefaultVisibleColumns(visibleColumns);
        
        return runTable;
    }
    
    public static ParticipantVisitResolverType findType(String name, List<ParticipantVisitResolverType> types)
    {
        if (name == null)
        {
            return null;
        }
        for (ParticipantVisitResolverType type : types)
        {
            if (name.equals(type.getName()))
            {
                return type;
            }
        }
        throw new IllegalArgumentException("Unexpected resolver type: " + name);
    }

    private Set<String> getPropertyDomains(ExpProtocol protocol)
    {
        Set<String> result = new HashSet<String>();
        for (ObjectProperty prop : protocol.getObjectProperties().values())
        {
            Lsid lsid = new Lsid(prop.getPropertyURI());
            if (lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(ExpProtocol.ASSAY_DOMAIN_PREFIX))
            {
                result.add(prop.getPropertyURI());
            }
        }
        return result;
    }

    public List<Pair<Domain, Map<DomainProperty, Object>>> getDomains(ExpProtocol protocol)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> domains = new ArrayList<Pair<Domain, Map<DomainProperty, Object>>>();
        for (String uri : getPropertyDomains(protocol))
        {
            Domain domain = PropertyService.get().getDomain(protocol.getContainer(), uri);
            Map<DomainProperty, Object> values = DefaultValueService.get().getDefaultValues(domain.getContainer(), domain);
            domains.add(new Pair<Domain, Map<DomainProperty, Object>>(domain, values));
        }
        sortDomainList(domains);
        return domains;
    }

    public Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer)
    {
        ExpProtocol copy = ExperimentService.get().createExpProtocol(targetContainer, ExpProtocol.ApplicationType.ExperimentRun, "Unknown");
        copy.setName(null);
        return new Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>>(copy, createDefaultDomains(targetContainer, user));
    }

    protected void sortDomainList(List<Pair<Domain, Map<DomainProperty, Object>>> domains)
    {
        // Rely on the assay provider to return a list of default domains in the right order (Collections.sort() is
        // stable so that domains that haven't been inserted and have id 0 stay in the same order), and rely on the fact
        // that they get inserted in the same order, so they will have ascending ids.
        Collections.sort(domains, new Comparator<Pair<Domain, Map<DomainProperty, Object>>>(){

            public int compare(Pair<Domain, Map<DomainProperty, Object>> dom1, Pair<Domain, Map<DomainProperty, Object>> dom2)
            {
                return new Integer(dom1.getKey().getTypeId()).compareTo(dom2.getKey().getTypeId());
            }
        });
    }

    public Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer, ExpProtocol toCopy)
    {
        ExpProtocol copy = ExperimentService.get().createExpProtocol(targetContainer, toCopy.getApplicationType(), toCopy.getName());
        copy.setDescription(toCopy.getDescription());
        Map<String, ObjectProperty> copiedProps = new HashMap<String, ObjectProperty>();
        for (ObjectProperty prop : toCopy.getObjectProperties().values())
        {
            copiedProps.put(createPropertyURI(copy, prop.getName()), prop);
        }
        copy.setObjectProperties(copiedProps);

        List<Pair<Domain, Map<DomainProperty, Object>>> originalDomains = getDomains(toCopy);
        List<Pair<Domain, Map<DomainProperty, Object>>> copiedDomains = new ArrayList<Pair<Domain, Map<DomainProperty, Object>>>(originalDomains.size());
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : originalDomains)
        {
            Domain domain = domainInfo.getKey();
            Map<DomainProperty, Object> originalDefaults = domainInfo.getValue();
            Map<DomainProperty, Object> copiedDefaults = new HashMap<DomainProperty, Object>();

            String uri = domain.getTypeURI();
            Lsid domainLsid = new Lsid(uri);
            String name = domain.getName();
            String defaultPrefix = toCopy.getName() + " ";
            if (name.startsWith(defaultPrefix))
                name = name.substring(defaultPrefix.length());
            Domain domainCopy = PropertyService.get().createDomain(targetContainer, getPresubstitutionLsid(domainLsid.getNamespacePrefix()), name);
            for (DomainProperty propSrc : domain.getProperties())
            {
                DomainProperty propCopy = domainCopy.addProperty();
                copiedDefaults.put(propCopy, originalDefaults.get(propSrc));
                propCopy.setDescription(propSrc.getDescription());
                propCopy.setFormat(propSrc.getFormat());
                propCopy.setLabel(propSrc.getLabel());
                propCopy.setName(propSrc.getName());
                propCopy.setDescription(propSrc.getDescription());
                propCopy.setType(propSrc.getType());
                propCopy.setRequired(propSrc.isRequired());
                propCopy.setHidden(propSrc.isHidden());
                propCopy.setMvEnabled(propSrc.isMvEnabled());
                propCopy.setDefaultValueTypeEnum(propSrc.getDefaultValueTypeEnum());
                // check to see if we're moving a lookup column to another container:
                Lookup lookup = propSrc.getLookup();
                if (lookup != null && !toCopy.getContainer().equals(targetContainer))
                {
                    // we need to update the lookup properties if the lookup container is either the source or the destination container
                    if (lookup.getContainer() == null)
                        lookup.setContainer(propSrc.getContainer());
                    else if (lookup.getContainer().equals(targetContainer))
                        lookup.setContainer(null);
                }
                propCopy.setLookup(lookup);
            }
            copiedDomains.add(new Pair<Domain, Map<DomainProperty, Object>>(domainCopy, copiedDefaults));
        }
        return new Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>>(copy, copiedDomains);
    }

    public boolean isFileLinkPropertyAllowed(ExpProtocol protocol, Domain domain)
    {
        Lsid domainLsid = new Lsid(domain.getTypeURI());
        return domainLsid.getNamespacePrefix().equals(ExpProtocol.ASSAY_DOMAIN_BATCH) ||
                domainLsid.getNamespacePrefix().equals(ExpProtocol.ASSAY_DOMAIN_RUN);
    }

    // UNDONE: also look at result row for TargetStudy
    // CONSIDER: combine with .getAssociatedStudyContainer()
    public Container getTargetStudy(ExpRun run)
    {
        ExpProtocol protocol = run.getProtocol();
        Domain batchDomain = getBatchDomain(protocol);
        DomainProperty[] batchColumns = batchDomain.getProperties();
        Domain runDomain = getRunDomain(protocol);
        DomainProperty[] runColumns = runDomain.getProperties();

        List<DomainProperty> pds = new ArrayList<DomainProperty>();
        pds.addAll(Arrays.asList(runColumns));
        pds.addAll(Arrays.asList(batchColumns));

        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(run.getObjectProperties());
        ExpExperiment batch = AssayService.get().findBatch(run);
        if (batch != null)
        {
            props.putAll(batch.getObjectProperties());
        }

        for (DomainProperty pd : pds)
        {
            ObjectProperty prop = props.get(pd.getPropertyURI());
            if (prop != null && TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()))
            {
                return ContainerManager.getForId(prop.getStringValue());
            }
        }
        return null;
    }

    private Map<String, Set<String>> _requiredDomainProperties;
    public boolean isMandatoryDomainProperty(Domain domain, String propertyName)
    {
        if (_requiredDomainProperties == null)
            _requiredDomainProperties = getRequiredDomainProperties();

        Lsid domainLsid = new Lsid(domain.getTypeURI());
        String domainPrefix = domainLsid.getNamespacePrefix();

        Set<String> domainSet = _requiredDomainProperties.get(domainPrefix);
        return domainSet != null && domainSet.contains(propertyName);
    }

    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<String, Set<String>> domainMap = new HashMap<String, Set<String>>();
        Set<String> batchProperties = domainMap.get(ExpProtocol.ASSAY_DOMAIN_BATCH);
        if (batchProperties == null)
        {
            batchProperties = new HashSet<String>();
            domainMap.put(ExpProtocol.ASSAY_DOMAIN_BATCH, batchProperties);
        }
        batchProperties.add(PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        return domainMap;
    }

    public boolean allowDefaultValues(Domain domain)
    {
        Lsid domainLsid = new Lsid(domain.getTypeURI());
        return !ExpProtocol.ASSAY_DOMAIN_DATA.equals(domainLsid.getNamespacePrefix());
    }

    public DefaultValueType[] getDefaultValueOptions(Domain domain)
    {
        return DefaultValueType.values();
    }

    public DefaultValueType getDefaultValueDefault(Domain domain)
    {
        return DefaultValueType.LAST_ENTERED;
    }

    public RunListQueryView createRunQueryView(ViewContext context, ExpProtocol protocol)
    {
        RunListQueryView queryView = new RunListQueryView(protocol, context);

        if (hasCustomView(ExpProtocol.AssayDomainTypes.Run, true))
        {
            ActionURL runDetailsURL = new ActionURL(AssayRunDetailsAction.class, context.getContainer());
            runDetailsURL.addParameter("rowId", protocol.getRowId());
            Map<String, String> params = new HashMap<String, String>();
            params.put("runId", "RowId");

            AbstractTableInfo ati = (AbstractTableInfo)queryView.getTable();
            ati.setDetailsURL(new DetailsURL(runDetailsURL, params));
            queryView.setShowDetailsColumn(true);
        }

        return queryView;
    }

    public ResultsQueryView createResultsQueryView(ViewContext context, ExpProtocol protocol)
    {
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), AssaySchema.NAME);
        String name = AssayService.get().getResultsTableName(protocol);
        QuerySettings settings = schema.getSettings(context, name, name);
        ResultsQueryView queryView = new ResultsQueryView(protocol, context, settings);

        if (hasCustomView(ExpProtocol.AssayDomainTypes.Result, true))
        {
            ActionURL resultDetailsURL = new ActionURL(AssayResultDetailsAction.class, context.getContainer());
            resultDetailsURL.addParameter("rowId", protocol.getRowId());
            Map<String, String> params = new HashMap<String, String>();
            // map ObjectId to url parameter ResultDetailsForm.dataRowId
            params.put("dataRowId", AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME);

            AbstractTableInfo ati = (AbstractTableInfo)queryView.getTable();
            ati.setDetailsURL(new DetailsURL(resultDetailsURL, params));
            queryView.setShowDetailsColumn(true);
        }

        return queryView;
    }

    public boolean hasCustomView(IAssayDomainType domainType, boolean details)
    {
        return false;
    }

    public ModelAndView createBeginView(ViewContext context, ExpProtocol protocol)
    {
        return null;
    }

    public ModelAndView createBatchesView(ViewContext context, ExpProtocol protocol)
    {
        return null;
    }

    public ModelAndView createBatchDetailsView(ViewContext context, ExpProtocol protocol, ExpExperiment batch)
    {
        return null;
    }

    public ModelAndView createRunsView(ViewContext context, ExpProtocol protocol)
    {
        return null;
    }

    public ModelAndView createRunDetailsView(ViewContext context, ExpProtocol protocol, ExpRun run)
    {
        return null;
    }

    public ModelAndView createResultsView(ViewContext context, ExpProtocol protocol)
    {
        return null;
    }

    public ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object dataRowId)
    {
        QueryView queryView = createResultsQueryView(context, protocol);

        DataRegion region = new DataRegion();

        // remove the DetailsColumn from the column list
        List<DisplayColumn> columns = queryView.getDisplayColumns();
        ListIterator<DisplayColumn> iter = columns.listIterator();
        while (iter.hasNext())
        {
            DisplayColumn column = iter.next();
            if (column instanceof DetailsColumn)
                iter.remove();
        }
        region.setDisplayColumns(columns);

        ExpRun run = data.getRun();
        ActionURL runUrl = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(
            context.getContainer(), protocol,
            queryView.getTable().getContainerFilter(), run.getRowId());

        ButtonBar bb = new ButtonBar();
        bb.getList().add(new ActionButton("Show Run", runUrl));
        region.setButtonBar(bb, DataRegion.MODE_DETAILS);

        return new DetailsView(region, dataRowId);
    }

    public void deleteProtocol(ExpProtocol protocol, User user) throws ExperimentException
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> domainInfos =  getDomains(protocol);
        List<Domain> domains = new ArrayList<Domain>();
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : domainInfos)
            domains.add(domainInfo.getKey());

        Set<Container> defaultValueContainers = new HashSet<Container>();
        defaultValueContainers.add(protocol.getContainer());
        defaultValueContainers.addAll(protocol.getExpRunContainers());
        clearDefaultValues(defaultValueContainers, domains);
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : domainInfos)
        {
            Domain domain = domainInfo.getKey();
            for (DomainProperty prop : domain.getProperties())
            {
                prop.delete();
            }
            try
            {
                domain.delete(user);
            }
            catch (DomainNotFoundException e)
            {
                throw new ExperimentException(e);
            }

            // Make sure we kill the pointer to the domain as well
            PropertyDescriptor prop = OntologyManager.getPropertyDescriptor(domain.getTypeURI(), domain.getContainer());
            if (prop != null)
            {
                OntologyManager.deletePropertyDescriptor(prop);
            }
        }

        // Take care of a few extra settings, such as whether runs and data rows are editable
        for (Map.Entry<String, ObjectProperty> entry : protocol.getObjectProperties().entrySet())
        {
            if (entry.getKey().startsWith(protocol.getLSID() + "#"))
            {
                PropertyDescriptor prop = OntologyManager.getPropertyDescriptor(entry.getKey(), protocol.getContainer());
                if (prop != null)
                {
                    OntologyManager.deletePropertyDescriptor(prop);
                }
            }
        }
    }

    private void clearDefaultValues(Set<Container> containers, List<Domain> domains)
    {
        for (Domain domain : domains)
        {
            for (Container container : containers)
                DefaultValueService.get().clearDefaultValues(container, domain);
        }
    }


    public Class<? extends Controller> getDesignerAction()
    {
        return DesignerAction.class;
    }

    @Override
    public Class<? extends Controller> getDataImportAction()
    {
        // default to assay designer, except in the case of tsv where the assay can support inferring the data domain
        return DesignerAction.class;
        //return ImportAction.class;
    }

    /**
     * Adds columns to an assay data table, providing a link to any datasets that have
     * had data copied into them.
     * @return The names of the added columns that should be visible
     */
    protected Set<String> addCopiedToStudyColumns(AbstractTableInfo table, ExpProtocol protocol, User user, boolean setVisibleColumns)
    {
        Set<String> visibleColumnNames = new HashSet<String>();
        int datasetIndex = 0;
        Set<String> usedColumnNames = new HashSet<String>();
        for (final DataSet assayDataSet : StudyService.get().getDatasetsForAssayProtocol(protocol.getRowId()))
        {
            if (!assayDataSet.getContainer().hasPermission(user, ReadPermission.class) || !assayDataSet.canRead(user))
            {
                continue;
            }

            String datasetIdColumnName = "dataset" + datasetIndex++;
            final StudyDataSetColumn datasetColumn = new StudyDataSetColumn(table,
                datasetIdColumnName, this, assayDataSet, user);
            datasetColumn.setHidden(true);
            datasetColumn.setUserEditable(false);
            datasetColumn.setShownInInsertView(false);
            datasetColumn.setShownInUpdateView(false);
            datasetColumn.setReadOnly(true);
            table.addColumn(datasetColumn);

            String studyCopiedSql = "(SELECT CASE WHEN " + datasetColumn.getDatasetIdAlias() +
                "._key IS NOT NULL THEN 'copied' ELSE NULL END)";

            String studyName = assayDataSet.getStudy().getLabel();
            if (studyName == null)
                continue; // No study in that folder
            String studyColumnName = "copied_to_" + PropertiesEditorUtil.sanitizeName(studyName);

            // column names must be unique. Prevent collisions
            while (usedColumnNames.contains(studyColumnName))
                studyColumnName = studyColumnName + datasetIndex;
            usedColumnNames.add(studyColumnName);

            final ExprColumn studyCopiedColumn = new ExprColumn(table,
                studyColumnName,
                new SQLFragment(studyCopiedSql),
                JdbcType.VARCHAR,
                datasetColumn);
            final String copiedToStudyColumnCaption = "Copied to " + studyName;
            studyCopiedColumn.setLabel(copiedToStudyColumnCaption);
            studyCopiedColumn.setUserEditable(false);
            studyCopiedColumn.setReadOnly(true);
            studyCopiedColumn.setShownInInsertView(false);
            studyCopiedColumn.setShownInUpdateView(false);
            studyCopiedColumn.setURL(StringExpressionFactory.createURL(StudyService.get().getDatasetURL(assayDataSet.getContainer(), assayDataSet.getDataSetId())));

            table.addColumn(studyCopiedColumn);

            visibleColumnNames.add(studyCopiedColumn.getName());
        }
        if (setVisibleColumns)
        {
            List<FieldKey> visibleColumns = new ArrayList<FieldKey>();
            for (FieldKey key : table.getDefaultVisibleColumns())
            {
                visibleColumns.add(key);
            }
            for (String columnName : visibleColumnNames)
            {
                visibleColumns.add(new FieldKey(null, columnName));
            }
            table.setDefaultVisibleColumns(visibleColumns);
        }

        return visibleColumnNames;
    }

    /** Adds the materials as inputs to the run as a whole, plus as inputs for the "work" node for the run. */
    public static void addInputMaterials(ExpRun expRun, User user, Set<ExpMaterial> materialInputs)
    {
        for (ExpProtocolApplication protApp : expRun.getProtocolApplications())
        {
            if (!protApp.getApplicationType().equals(ExpProtocol.ApplicationType.ExperimentRunOutput))
            {
                Set<ExpMaterial> newInputs = new LinkedHashSet<ExpMaterial>();
                newInputs.addAll(materialInputs);
                newInputs.removeAll(protApp.getInputMaterials());
                int index = 1;
                for (ExpMaterial newInput : newInputs)
                {
                    protApp.addMaterialInput(user, newInput, "Sample" + (index == 1 ? "" : Integer.toString(index)));
                    index++;
                }
            }
        }
    }

    public boolean hasUsefulDetailsPage()
    {
        return true;
    }

    private static final String SCRIPT_PATH_DELIMETER = "|";

    @Override
    public void setValidationAndAnalysisScripts(ExpProtocol protocol, List<File> scripts) throws ExperimentException
    {
        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(protocol.getObjectProperties());
        String propertyURI = ScriptType.TRANSFORM.getPropertyURI(protocol);

        StringBuilder sb = new StringBuilder();
        String separator = "";
        for (File scriptFile : scripts)
        {
            if (scriptFile.isFile())
            {
                String ext = FileUtil.getExtension(scriptFile);
                ScriptEngine engine = ServiceRegistry.get().getService(ScriptEngineManager.class).getEngineByExtension(ext);
                if (engine != null)
                {
                    sb.append(separator);
                    sb.append(scriptFile.getAbsolutePath());
                    separator = SCRIPT_PATH_DELIMETER;
                }
                else
                    throw new ExperimentException("Script engine for the extension : " + ext + " has not been registered.\nFor documentation about how to configure a " +
                            "scripting engine, paste this link into your browser: \"https://www.labkey.org/wiki/home/Documentation/page.view?name=configureScripting\".");
            }
            else
                throw new ExperimentException("The transform script '" + scriptFile.getPath() + "' is invalid or does not exist");
        }
        if (sb.length() > 0)
        {
            ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(),
                    propertyURI, sb.toString());
            props.put(propertyURI, prop);
        }
        else
        {
            props.remove(propertyURI);
        }
        // Be sure to strip out any validation scripts that were stored with the legacy propertyURI. We merge and save
        // them as a single list in the TRANSFORM 
        props.remove(ScriptType.VALIDATION.getPropertyURI(protocol));
        protocol.setObjectProperties(props);
    }

    /** For migrating legacy assay designs that have separate transform and validation script properties */
    private enum ScriptType
    {
        VALIDATION("ValidationScript"),
        TRANSFORM("TransformScript");

        private final String _uriSuffix;

        ScriptType(String uriSuffix)
        {
            _uriSuffix = uriSuffix;
        }

        public String getPropertyURI(ExpProtocol protocol)
        {
            return protocol.getLSID() + "#" + _uriSuffix;
        }
    }

    @Override
    public List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope)
    {
        List<File> result = new ArrayList<File>();
        if (scope == Scope.ASSAY_DEF || scope == Scope.ALL)
        {
            ObjectProperty transformScripts = protocol.getObjectProperties().get(ScriptType.TRANSFORM.getPropertyURI(protocol));
            if (transformScripts != null)
            {
                for (String scriptPath : transformScripts.getStringValue().split("\\" + SCRIPT_PATH_DELIMETER))
                {
                    result.add(new File(scriptPath));
                }
            }
            ObjectProperty validationScripts = protocol.getObjectProperties().get(ScriptType.VALIDATION.getPropertyURI(protocol));
            if (validationScripts != null)
            {
                for (String scriptPath : validationScripts.getStringValue().split("\\" + SCRIPT_PATH_DELIMETER))
                {
                    result.add(new File(scriptPath));
                }
            }
        }
        return result;
    }

    @Override
    public void setSaveScriptFiles(ExpProtocol protocol, boolean save) throws ExperimentException
    {
        setBooleanProperty(protocol, SAVE_SCRIPT_FILES_PROPERTY_SUFFIX, save);
    }

    private void setBooleanProperty(ExpProtocol protocol, String propertySuffix, boolean value)
    {
        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(protocol.getObjectProperties());

        String propertyURI = createPropertyURI(protocol, propertySuffix);
        ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(), propertyURI, value);
        props.put(propertyURI, prop);

        protocol.setObjectProperties(props);
    }

    @Override
    public void setEditableResults(ExpProtocol protocol, boolean editable) throws ExperimentException
    {
        setBooleanProperty(protocol, EDITABLE_RESULTS_PROPERTY_SUFFIX, editable);
    }

    @Override
    public boolean supportsEditableResults()
    {
        return false;
    }

    @Override
    public boolean isEditableResults(ExpProtocol protocol)
    {
        return supportsEditableResults() && Boolean.TRUE.equals(getBooleanProperty(protocol, EDITABLE_RESULTS_PROPERTY_SUFFIX));
    }

    @Override
    public void setEditableRuns(ExpProtocol protocol, boolean editable) throws ExperimentException
    {
        setBooleanProperty(protocol, EDITABLE_RUNS_PROPERTY_SUFFIX, editable);
    }

    @Override
    public boolean isEditableRuns(ExpProtocol protocol)
    {
        return Boolean.TRUE.equals(getBooleanProperty(protocol, EDITABLE_RUNS_PROPERTY_SUFFIX));
    }

    @Override
    public boolean supportsBackgroundUpload()
    {
        return false;
    }

    @Override
    public boolean supportsReRun()
    {
        return false;
    }

    @Override
    public void setBackgroundUpload(ExpProtocol protocol, boolean background) throws ExperimentException
    {
        setBooleanProperty(protocol, BACKGROUND_UPLOAD_PROPERTY_SUFFIX, background);
    }

    @Override
    public boolean isBackgroundUpload(ExpProtocol protocol)
    {
        return supportsBackgroundUpload() && Boolean.TRUE.equals(getBooleanProperty(protocol, BACKGROUND_UPLOAD_PROPERTY_SUFFIX));
    }

    @Override
    public boolean isSaveScriptFiles(ExpProtocol protocol)
    {
        return Boolean.TRUE.equals(getBooleanProperty(protocol, SAVE_SCRIPT_FILES_PROPERTY_SUFFIX));
    }

    protected Boolean getBooleanProperty(ExpProtocol protocol, String propertySuffix)
    {
        ObjectProperty prop = protocol.getObjectProperties().get(createPropertyURI(protocol, propertySuffix));

        if (prop != null)
        {
            Object o = prop.value();
            if (o instanceof Boolean)
                return (Boolean)o;
        }
        return null;
    }

    private static String createPropertyURI(ExpProtocol protocol, String propertySuffix)
    {
        return protocol.getLSID() + "#" + propertySuffix;
    }

    public AssayDataType getDataType()
    {
        return _dataType;
    }

    public void setMaxFileInputs(int maxFileInputs)
    {
        _maxFileInputs = maxFileInputs;
    }

    public int getMaxFileInputs()
    {
        return _maxFileInputs;
    }

    /**
     * Return the helper to handle data exchange between the server and external scripts.
     */
    public DataExchangeHandler createDataExchangeHandler()
    {
        return null;
    }

    @Override
    public AssayRunDatabaseContext createRunDatabaseContext(ExpRun run, User user, HttpServletRequest request)
    {
        return new AssayRunDatabaseContext(run, user, request);
    }

    @Override
    public AssayRunAsyncContext createRunAsyncContext(AssayRunUploadContext context) throws IOException, ExperimentException
    {
        return new AssayRunAsyncContext(context);
    }

    public void upgradeAssayDefinitions(User user, ExpProtocol protocol, double targetVersion) throws SQLException {}

    @Override
    public List<NavTree> getHeaderLinks(ViewContext viewContext, ExpProtocol protocol, ContainerFilter containerFilter)
    {
        List<NavTree> result = new ArrayList<NavTree>();
        result.add(new NavTree("view results", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(viewContext.getContainer(), protocol, containerFilter))));
        if (isBackgroundUpload(protocol))
        {
            ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getShowUploadJobsURL(viewContext.getContainer(), protocol, containerFilter);
            result.add(new NavTree("view upload jobs", PageFlowUtil.addLastFilterParameter(url)));
        }
        return result;
    }

    public String getRunLSIDPrefix()
    {
        return _runLSIDPrefix;
    }
}
