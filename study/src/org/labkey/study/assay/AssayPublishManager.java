/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Study;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.reader.TabLoader;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.assay.AssayController;
import org.labkey.study.model.*;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 16, 2006
 * Time: 1:11:27 PM
 */
public class AssayPublishManager implements AssayPublishService.Service
{
    private TableInfo tinfoUpdateLog;
    private static final int MIN_ASSAY_ID = 5000;
    public static final String ASSAY_PUBLISH_AUDIT_EVENT = "AssayPublishAuditEvent";

    public synchronized static AssayPublishManager getInstance()
    {
        return (AssayPublishManager) AssayPublishService.get();
    }


    private TableInfo getTinfoUpdateLog()
    {
        if (tinfoUpdateLog == null)
            tinfoUpdateLog = StudySchema.getInstance().getTableInfoUploadLog();
        return tinfoUpdateLog;
    }

    /**
     * Studies that the user has permission to.
     */
    public Set<Study> getValidPublishTargets(User user, Class<? extends Permission> permission)
    {
        try
        {
            Study[] studies = StudyManager.getInstance().getAllStudies(ContainerManager.getRoot(), user, permission);

            Set<Study> result = new HashSet<Study>();
            result.addAll(Arrays.asList(studies));
            return result;
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                          List<Map<String, Object>> dataMaps, Map<String, PropertyType> types, String keyPropertyName, List<String> errors)
    {
        TimepointType timetype = StudyManager.getInstance().getStudy(targetContainer).getTimepointType();
        
        List<PropertyDescriptor> propertyDescriptors = new ArrayList<PropertyDescriptor>();
        for (Map.Entry<String, PropertyType> entry : types.entrySet())
        {
            String pdName = entry.getKey();
            if ("Date".equalsIgnoreCase(pdName) && TimepointType.VISIT != timetype)
                continue;
            PropertyType type = types.get(pdName);
            String typeURI = type.getTypeUri();
            PropertyDescriptor pd = new PropertyDescriptor(null,
                    typeURI, pdName, targetContainer);
            if (type.getJavaType() == Double.class)
                pd.setFormat("0.###");
            propertyDescriptors.add(pd);
        }
        return publishAssayData(user, sourceContainer, targetContainer, assayName, protocol, dataMaps, propertyDescriptors, keyPropertyName, errors);
    }

    public ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                         List<Map<String, Object>> dataMaps, Map<String, PropertyType> types, List<String> errors)
    {
        return publishAssayData(user, sourceContainer, targetContainer, assayName, protocol, dataMaps, types, null, errors);
    }

    private List<PropertyDescriptor> createTargetPropertyDescriptors(DataSet dataset, List<PropertyDescriptor> sourcePds, List<String> errors)
    {
        List<PropertyDescriptor> targetPds = new ArrayList<PropertyDescriptor>(sourcePds.size());
        Set<String> legalNames = new HashSet<String>();
        for (PropertyDescriptor sourcePd : sourcePds)
        {
            PropertyDescriptor targetPd = sourcePd.clone();

            // Deal with duplicate legal names.  It's too bad that we have to do so this late in the game
            // (rather than at assay design time), but for a long time there was no mechanism to
            // prevent assay designers from creating properties with names that are the same as hard columns.
            // There are also a few cases where an assay provider may add columns to the published set as
            // publish time; rather than reserve all these names at design time, we catch them here.
            String legalName = ColumnInfo.legalNameFromName(targetPd.getName()).toLowerCase();
            if (legalNames.contains(legalName))
            {
                errors.add("Unable to copy to study: duplicate column \"" + targetPd.getName() + "\" detected in the assay design.  Please contact an administrator.");
                return Collections.emptyList();
            }
            legalNames.add(legalName);

            targetPd.setPropertyURI(dataset.getTypeURI() + "#" + sourcePd.getName());
            targetPd.setContainer(dataset.getContainer());
            targetPd.setProject(dataset.getContainer().getProject());
            if (targetPd.getLookupQuery() != null)
                targetPd.setLookupContainer(sourcePd.getLookupContainer());
            // set the ID to zero so it's clear that this is a new property descriptor:
            targetPd.setPropertyId(0);
            targetPds.add(targetPd);
        }
        return targetPds;
    }

    public ActionURL publishAssayData(User user, Container sourceContainer, @Nullable Container targetContainer, String assayName, @Nullable ExpProtocol protocol,
                                          List<Map<String, Object>> dataMaps, List<PropertyDescriptor> columns, String keyPropertyName, List<String> errors)
    {
        // Partition dataMaps by targetStudy.
        Map<Container, List<Map<String, Object>>> partitionedDataMaps = new HashMap<Container, List<Map<String, Object>>>();
        for (Map<String, Object> dataMap : dataMaps)
        {
            Container targetStudy = targetContainer;
            if (dataMap.containsKey("TargetStudy"))
                targetStudy = (Container)dataMap.get("TargetStudy");
            assert targetStudy != null;

            List<Map<String, Object>> maps = partitionedDataMaps.get(targetStudy);
            if (maps == null)
            {
                maps = new ArrayList<Map<String, Object>>(dataMap.size());
                partitionedDataMaps.put(targetStudy, maps);
            }
            maps.add(dataMap);
        }

        // CONSIDER: transact all copies together
        // CONSIDER: returning list of URLS along with a count of rows copied to each study.
        ActionURL url = null;
        for (Map.Entry<Container, List<Map<String, Object>>> entry : partitionedDataMaps.entrySet())
        {
            Container targetStudy = entry.getKey();
            List<Map<String, Object>> maps = entry.getValue();
            url = _publishAssayData(user, sourceContainer, targetStudy, assayName, protocol, maps, columns, keyPropertyName, errors);
        }
        return url;
    }

    private ActionURL _publishAssayData(User user, Container sourceContainer, @NotNull Container targetContainer, String assayName, @Nullable ExpProtocol protocol,
                                      List<Map<String, Object>> dataMaps, List<PropertyDescriptor> columns, String keyPropertyName, List<String> errors)
    {

        StudyImpl targetStudy = StudyManager.getInstance().getStudy(targetContainer);
        assert verifyRequiredColumns(dataMaps, targetStudy.getTimepointType());
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean ownsTransaction = !scope.isTransactionActive();

        try
        {
            if (ownsTransaction)
                scope.beginTransaction();
            DataSetDefinition[] datasets = StudyManager.getInstance().getDataSetDefinitions(targetStudy);
            DataSetDefinition dataset = null;
            for (int i = 0; i < datasets.length && dataset == null; i++)
            {
                // If there's a dataset linked to our protocol, use it
                if (protocol != null &&
                        datasets[i].getProtocolId() != null &&
                        datasets[i].getProtocolId().equals(protocol.getRowId()))
                {
                    dataset = datasets[i];
                }
                else if (protocol == null &&
                        datasets[i].getTypeURI() != null &&
                        datasets[i].getTypeURI().equals(getDomainURIString(targetStudy, assayName)))
                {
                    // No protocol, but we've got a type uri match. This is used when creating a study
                    // from a study design
                    dataset = datasets[i];
                }
            }
            if (dataset == null)
                dataset = createAssayDataset(user, targetStudy, createUniqueDatasetName(targetStudy, assayName), keyPropertyName, null, false, protocol);
            else if (protocol != null)
            {
                Integer datasetProtocolId = dataset.getProtocolId();
                if (datasetProtocolId == null)
                {
                    dataset.setProtocolId(protocol.getRowId());
                    StudyManager.getInstance().updateDataSetDefinition(user, dataset);
                }
                else if (!datasetProtocolId.equals(protocol.getRowId()))
                {
                    errors.add("The destination dataset belongs to a different assay protocol");
                    return null;
                }

                // Make sure the key property matches,
                // or the dataset data row won't have a link back to the assay data row
                if (!keyPropertyName.equals(dataset.getKeyPropertyName()))
                {
                    dataset.setKeyPropertyName(keyPropertyName);
                    StudyManager.getInstance().updateDataSetDefinition(user, dataset);
                }
            }

            List<PropertyDescriptor> types = createTargetPropertyDescriptors(dataset, columns, errors);
            boolean schemaChanged = false;
            for (PropertyDescriptor type : types)
            {
                if (type.getPropertyId() == 0)
                {
                    schemaChanged = true;
                    break;
                }
            }
            if (!errors.isEmpty())
                return null;
            Map<String, String> propertyNamesToUris = ensurePropertyDescriptors(user, dataset, dataMaps, types);
            List<Map<String, Object>> convertedDataMaps = convertPropertyNamesToURIs(dataMaps, propertyNamesToUris);
            // re-retrieve the datasetdefinition: this is required to pick up any new columns that may have been created
            // in 'ensurePropertyDescriptors'.
            if (ownsTransaction)
                scope.commitTransaction();
            if (schemaChanged)
                StudyManager.getInstance().uncache(dataset);
            dataset = StudyManager.getInstance().getDataSetDefinition(targetStudy, dataset.getRowId());
            Integer defaultQCStateId = targetStudy.getDefaultAssayQCState();
            QCState defaultQCState = null;
            if (defaultQCStateId != null)
                defaultQCState = StudyManager.getInstance().getQCStateForRowId(targetContainer, defaultQCStateId.intValue());
            // unfortunately, the actual import cannot happen within our transaction: we eventually hit the
            // IllegalStateException in ContainerManager.ensureContainer.
            List<String> lsids = StudyManager.getInstance().importDatasetData(targetStudy, user, dataset, convertedDataMaps, new Date().getTime(), errors, true, true, defaultQCState, null);
            if (lsids.size() > 0 && protocol != null)
            {
                for (Map.Entry<String, int[]> entry : getSourceLSID(dataMaps).entrySet())
                {
                    AuditLogEvent event = new AuditLogEvent();

                    event.setCreatedBy(user);
                    event.setEventType(ASSAY_PUBLISH_AUDIT_EVENT);
                    event.setIntKey1(protocol.getRowId());
                    event.setComment(entry.getValue()[0] + " row(s) were copied to a study from the assay: " + protocol.getName());
                    event.setKey1(targetContainer.getId());
                    event.setContainerId(sourceContainer.getId());

                    Map<String, Object> dataMap = new HashMap<String, Object>();
                    dataMap.put("datasetId", dataset.getDataSetId());

                    dataMap.put("sourceLsid", entry.getKey());
                    dataMap.put("recordCount", entry.getValue()[0]);

                    AssayAuditViewFactory.getInstance().ensureDomain(user);
                    AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(ASSAY_PUBLISH_AUDIT_EVENT));
                }
            }
            //Make sure that the study is updated with the correct timepoints.
            StudyManager.getInstance().getVisitManager(targetStudy).updateParticipantVisits(user, Collections.singleton(dataset));

            ActionURL url = new ActionURL(StudyController.DatasetAction.class, targetContainer);
            url.addParameter(DataSetDefinition.DATASETKEY, dataset.getRowId());
            if (StudyManager.getInstance().showQCStates(targetStudy.getContainer()))
            {
                QCStateSet allStates = QCStateSet.getAllStates(targetStudy.getContainer());
                if (allStates != null)
                    url.addParameter(BaseStudyController.SharedFormParameters.QCState, allStates.getFormValue());
            }

            return url;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw new UnexpectedException(e);
        }
        finally
        {
            if (ownsTransaction)
                scope.closeConnection();
        }
    }

    private Map<String, int[]> getSourceLSID(List<Map<String, Object>> dataMaps)
    {
        Map<String, int[]> lsidMap = new HashMap<String, int[]>();

        for (Map<String, Object> map : dataMaps)
        {
            for (Map.Entry<String, Object> entry : map.entrySet())
            {
                if (entry.getKey().equalsIgnoreCase("sourcelsid"))
                {
                    String lsid = String.valueOf(entry.getValue());
                    int[] count = lsidMap.get(lsid);
                    if (count == null)
                    {
                        count = new int[1];
                        lsidMap.put(lsid, count);
                    }
                    count[0]++;
                    break;
                }
            }
        }
        return lsidMap;
    }

    private boolean verifyRequiredColumns(List<Map<String, Object>> dataMaps, TimepointType timepointType)
    {
        for (Map<String, Object> dataMap : dataMaps)
        {
            Set<String> lcaseSet = new HashSet<String>();
            for (String key : dataMap.keySet())
                lcaseSet.add(key.toLowerCase());
            assert lcaseSet.contains("participantid") : "Publishable assay results must include participantid, sequencenum, and sourcelsid columns.";
            assert timepointType != TimepointType.VISIT || lcaseSet.contains("sequencenum") : "Publishable assay results must include participantid, sequencenum, and sourcelsid columns.";
            assert timepointType != TimepointType.DATE || lcaseSet.contains("date") : "Publishable assay results must include participantid, date, and sourcelsid columns.";
            //assert lcaseSet.contains("sourcelsid") : "Publishable assay results must include participantid, sequencenum, and sourcelsid columns.";
        }
        return true;
    }

    private List<Map<String, Object>> convertPropertyNamesToURIs(List<Map<String, Object>> dataMaps, Map<String, String> propertyNamesToUris)
    {
        List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>(dataMaps.size());
        for (Map<String, Object> dataMap : dataMaps)
        {
            Map<String, Object> newMap = new CaseInsensitiveHashMap<Object>(dataMap.size());
            for (Map.Entry<String, Object> entry : dataMap.entrySet())
            {
                String uri = propertyNamesToUris.get(entry.getKey());
                if (null == uri)
                {
                    if ("Date".equalsIgnoreCase(entry.getKey()))
                        uri = DataSetDefinition.getVisitDateURI();

                    // Skip "TargetStudy"
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equalsIgnoreCase(entry.getKey()))
                        continue;
                }
                assert uri != null : "Expected all properties to already be present in assay type";
                newMap.put(uri, entry.getValue());
            }
            ret.add(newMap);
        }
        return ret;
    }


    private Map<String, String> ensurePropertyDescriptors(
            User user, DataSetDefinition dataset,
            List<Map<String, Object>> dataMaps, List<PropertyDescriptor> types) throws SQLException, UnauthorizedException, ChangePropertyDescriptorException
    {
        Domain domain = dataset.getDomain();
        if (domain == null)
        {
            domain = PropertyService.get().createDomain(dataset.getContainer(), dataset.getTypeURI(), dataset.getName());
            domain.save(user);
        }
        // Strip out any spaces from existing PropertyDescriptors in the dataset
        boolean propertyChanged = false;
        for (DomainProperty existingProperty : domain.getProperties())
        {
            if (existingProperty.getName().indexOf(" ") != -1)
            {
                existingProperty.setName(existingProperty.getName().replace(" ", ""));
                existingProperty.setPropertyURI(existingProperty.getPropertyURI().replace(" ", ""));
                propertyChanged = true;
            }
        }
        if (propertyChanged)
        {
            domain.save(user);
        }

        // Strip out spaces from any proposed PropertyDescriptor names
        for (PropertyDescriptor newPD : types)
        {
            if (newPD.getName().indexOf(" ") != -1)
            {
                String newName = newPD.getName().replace(" ", "");
                for (Map<String, Object> dataMap : dataMaps)
                {
                    Object value = dataMap.get(newPD.getName());
                    dataMap.remove(newPD.getName());
                    dataMap.put(newName, value);
                }
                newPD.setName(newName);
                if (newPD.getPropertyURI() != null)
                {
                    newPD.setPropertyURI(newPD.getPropertyURI().replace(" ", ""));
                }
            }
        }

        // we'll return a mapping from column name to column uri
        Map<String, String> propertyNamesToUris = new CaseInsensitiveHashMap<String>();

        // add ontology properties to our return map
        for (DomainProperty property : domain.getProperties())
            propertyNamesToUris.put(property.getName(), property.getPropertyURI());

        // add hard columns to our return map
        for (ColumnInfo col : DataSetDefinition.getTemplateTableInfo().getColumns())
        {
            // Swap out whatever subject column name is used in the target study for 'ParticipantID'.
            // This allows the assay side to use its column name (ParticipantID) to find the study-side
            // property URI:
            if (col.getName().equalsIgnoreCase(StudyService.get().getSubjectColumnName(dataset.getContainer())))
                propertyNamesToUris.put("ParticipantID", col.getPropertyURI());
            else
                propertyNamesToUris.put(col.getName(), col.getPropertyURI());
        }

        // create a set of all columns that will be required, so we can detect
        // if any of these are new
        Set<String> newPdNames = new TreeSet<String>();
        for (Map<String, Object> dataMap : dataMaps)
            newPdNames.addAll(dataMap.keySet());
        if (dataset.getStudy().getTimepointType() != TimepointType.VISIT)  // don't try to create a propertydescriptor for date
            newPdNames.remove("Date");
        newPdNames.remove(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);

        Map<String, PropertyDescriptor> typeMap = new HashMap<String, PropertyDescriptor>();
        for (PropertyDescriptor pd : types)
            typeMap.put(pd.getName(), pd);

        // loop through all new columns, and verify that we have a property already defined:
        int sortOrder = 0;
        boolean changed = false;
        for (String newPdName : newPdNames)
        {
            if (!propertyNamesToUris.containsKey(newPdName))
            {
                // We used to copy batch properties with the "Run" prefix - see if we need to rename the target column
                if (!renameRunPropertyToBatch(domain, propertyNamesToUris, newPdNames, newPdName, user))
                {
                    PropertyDescriptor pd = typeMap.get(newPdName);
                    DomainProperty newProperty = domain.addProperty();
                    pd.copyTo(newProperty.getPropertyDescriptor());
                    domain.setPropertyIndex(newProperty, sortOrder++);
                    changed = true;
                    propertyNamesToUris.put(newPdName, pd.getPropertyURI());
                }
            }
        }
        if (changed)
        {
            domain.save(user);
        }
        return propertyNamesToUris;
    }

    private boolean renameRunPropertyToBatch(Domain domain, Map<String, String> propertyNamesToUris, Set<String> newPdNames, String newPdName, User user)
            throws SQLException, ChangePropertyDescriptorException
    {
        if (newPdName.startsWith(AssayService.BATCH_COLUMN_NAME))
        {
            String oldName = "Run" + newPdName.substring(AssayService.BATCH_COLUMN_NAME.length());
            // Check if we don't have a different run-prefixed property to copy and and we do have a run-prefixed property in the target domain
            if (!newPdNames.contains(oldName) && propertyNamesToUris.containsKey(oldName))
            {
                String originalURI = propertyNamesToUris.get(oldName);

                for (DomainProperty property : domain.getProperties())
                {
                    if (property.getPropertyURI().equals(originalURI))
                    {
                        // Rename the property, including its URI
                        property.setName(newPdName);
                        property.setLabel(null);
                        property.setPropertyURI(property.getPropertyURI().replace("#Run", "#Batch"));
                        propertyNamesToUris.remove(oldName);
                        propertyNamesToUris.put(newPdName, property.getPropertyURI());
                        domain.save(user);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public DataSetDefinition createAssayDataset(User user, StudyImpl study, String name, String keyPropertyName, Integer datasetId, boolean isDemographicData, ExpProtocol protocol) throws SQLException
    {
        boolean ownTransaction = false;
        DbSchema schema = StudySchema.getInstance().getSchema();
        try
        {
            if (!schema.getScope().isTransactionActive())
            {
                schema.getScope().beginTransaction();
                ownTransaction = true;
            }

            if (null == datasetId)
                datasetId = Table.executeSingleton(schema, "SELECT MAX(n) + 1 AS id FROM (SELECT Max(datasetid) AS n FROM study.dataset WHERE container=? UNION SELECT ? As n) x", new Object[] {study.getContainer().getId(), MIN_ASSAY_ID}, Integer.class);
            DataSetDefinition newDataSet = new DataSetDefinition(study, datasetId.intValue(), name, name, null, getDomainURIString(study, name));
            newDataSet.setShowByDefault(true);
            if (keyPropertyName != null)
                newDataSet.setKeyPropertyName(keyPropertyName);
            newDataSet.setDemographicData(isDemographicData);
            if (protocol != null)
                newDataSet.setProtocolId(protocol.getRowId());

            StudyManager.getInstance().createDataSetDefinition(user, newDataSet);

            if (ownTransaction)
            {
                schema.getScope().commitTransaction();
                ownTransaction = false;
            }
            return newDataSet;
        }
        finally
        {
            if (ownTransaction)
                schema.getScope().rollbackTransaction();
        }
    }

    /**
     * Try to use the assay name for a dataset, but if it's already taken, add an integer suffix until it's unique
     */
    private static String createUniqueDatasetName(Study study, String assayName)
    {
        Set<String> inUseNames = new HashSet<String>();
        for (DataSet def : study.getDataSets())
            inUseNames.add(def.getName());

        int suffix = 1;
        String name = assayName;

        while (inUseNames.contains(name))
        {
            name = assayName + Integer.toString(suffix);
            suffix++;
        }

        return name;
    }

    static final String DIR_NAME = "assaydata";
    public UploadLog saveUploadData(User user, DataSet dsd, String tsv) throws IOException
    {
        PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(dsd.getContainer());
        if (null == pipelineRoot || !pipelineRoot.isValid())
            throw new IOException("Please have your administrator set up a pipeline root for this folder.");

        File dir = pipelineRoot.resolvePath(DIR_NAME);
        if (!dir.exists())
        {
            boolean success = dir.mkdir();
            if (!success)
                throw new IOException("Could not create directory: " + dir);
        }

        //File name is studyname_datasetname_date_hhmm.ss
        Date dateCreated = new Date();
        String dateString = DateUtil.formatDateTime(dateCreated, "yyy-MM-dd-HHmm");
        int id = 0;
        File file;
        do
        {
            String extra = id++ == 0 ? "" : String.valueOf(id);
            String fileName = dsd.getStudy().getLabel() + "-" + dsd.getLabel() + "-" + dateString + extra + ".tsv";
            fileName = fileName.replace('\\', '_').replace('/','_').replace(':','_');
            file = new File(dir, fileName);
        }
        while (file.exists());
        FileWriter writer = null;
        try
        {
            writer = new FileWriter(file);
            writer.append(tsv);
            writer.close();
            writer = null;
        }
        finally
        {
            if (null != writer)
                try { writer.close(); } catch (Exception x) {}
        }

        UploadLog ul = new UploadLog();
        ul.setContainer(dsd.getContainer());
        ul.setDatasetId(dsd.getDataSetId());
        ul.setCreated(dateCreated);
        ul.setUserId(user.getUserId());
        ul.setStatus("Initializing");
        ul.setFilePath(file.getPath());

        try
        {
            ul = Table.insert(user, getTinfoUpdateLog(), ul);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return ul;
    }

    public String getDomainURIString(Study study, String typeName)
    {
        Lsid domainLsid = new Lsid("StudyDataset", "Folder-" + study.getContainer().getRowId(), typeName);
        return domainLsid.toString();
    }

    /**
     * Return an array of LSIDs from the newly created dataset entries,
     * along with the upload log.
     */
    public Pair<List<String>, UploadLog> importDatasetTSV(User user, StudyImpl study, DataSetDefinition dsd, String tsv, Map<String, String> columnMap, List<String> errors) throws SQLException, ServletException
    {
        UploadLog ul = null;
        List<String> lsids = Collections.emptyList();
        try
        {
            ul = saveUploadData(user, dsd, tsv);
            Integer defaultQCStateId = study.getDefaultDirectEntryQCState();
            QCState defaultQCState = null;
            if (defaultQCStateId != null)
                defaultQCState = StudyManager.getInstance().getQCStateForRowId(study.getContainer(), defaultQCStateId.intValue());
            lsids = StudyManager.getInstance().importDatasetData(study, user, dsd, new TabLoader(tsv, true), ul.getCreated().getTime(), columnMap, errors, true, true, defaultQCState, null);
            if (errors.size() == 0)
                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.singleton(dsd));
        }
        catch (IOException x)
        {
            errors.add("Exception: " + x.getMessage());
            if (ul != null)
            {
                ul.setStatus("ERROR");
                String description = ul.getDescription();
                ul.setDescription(description == null ? "" : description + "\n" + new Date() + ":" + x.getMessage());
                try
                {
                    ul = Table.update(user, StudySchema.getInstance().getTableInfoUploadLog(), ul, ul.getRowId());
                    return Pair.of(lsids, ul);
                }
                catch (SQLException s)
                {
                    //throw original
                }
            }
        }

        if (errors.size() == 0)
        {
            //Update the status
            assert ul != null : "Upload log should always exist if no errors have occurred.";
            ul.setStatus("SUCCESS");
            ul = Table.update(user, getTinfoUpdateLog(), ul, ul.getRowId());
        }
        else if (ul != null)
        {
            ul.setStatus("ERROR");
            StringBuffer sb = new StringBuffer();
            String sep = "";
            for (String s : errors)
            {
                sb.append(sep).append(s);
                sep = "\n";
            }
            ul.setDescription(sb.toString());
            ul = Table.update(user, getTinfoUpdateLog(), ul, ul.getRowId());
        }
        return Pair.of(lsids,ul);
    }

    public UploadLog getUploadLog(Container c, int id) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("container", c.getId());
        filter.addCondition("rowId", id);

        return Table.selectObject(getTinfoUpdateLog(), filter, null, UploadLog.class);
    }
    
    public ActionURL getPublishHistory(Container c, ExpProtocol protocol)
    {
        return getPublishHistory(c, protocol, null);
    }

    public ActionURL getPublishHistory(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
    {
        if (protocol != null)
        {
            ActionURL url = new ActionURL(AssayController.PublishHistoryAction.class, container).addParameter("rowId", protocol.getRowId());
            if (containerFilter != null)
                url.addParameter("containerFilterName", containerFilter.getType().name());
            return url;
        }

        HttpView.throwNotFound("Specified protocol is invalid");
        return null;
    }

    public TimepointType getTimepointType(Container container)
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            throw new IllegalArgumentException("No study in container: " + container.getPath());

        return study.getTimepointType();
    }

    public String getStudyName(Container container)
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            throw new IllegalArgumentException("No study in container: " + container.getPath());

        return study.getLabel();
    }

    public boolean hasMismatchedInfo(AssayProvider provider, ExpProtocol protocol, List<Integer> allObjects, AssaySchema schema)
    {
        TableInfo tableInfo = provider.createDataTable(schema, protocol);

        AssayTableMetadata tableMetadata = provider.getTableMetadata();

        FieldKey matchFieldKey = new FieldKey(tableMetadata.getSpecimenIDFieldKey(), "AssayMatch");

        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, Collections.singleton(matchFieldKey));
        ColumnInfo matchColumn = columns.get(matchFieldKey);

        if (matchColumn == null)
        {
            return false;
        }

        SimpleFilter filter = new SimpleFilter();
        filter.addClause(new SimpleFilter.InClause(tableMetadata.getResultRowIdFieldKey().toString(), allObjects));

        try
        {
            Boolean[] matches = Table.executeArray(tableInfo, matchColumn, filter, null, Boolean.class);
            for (Boolean match : matches)
            {
                if (Boolean.FALSE.equals(match))
                {
                    return true;
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return false;
    }
}
