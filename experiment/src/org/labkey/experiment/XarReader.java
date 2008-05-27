/*
* Copyright (c) 2005-2008 LabKey Corporation
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

package org.labkey.experiment;

import org.apache.commons.beanutils.ConversionException;
import org.apache.xmlbeans.*;
import org.fhcrc.cpas.exp.xml.*;
import org.fhcrc.cpas.exp.xml.DataType;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.api.*;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.experiment.api.*;
import org.labkey.experiment.api.property.DomainImpl;
import org.labkey.experiment.xar.AutoFileLSIDReplacer;
import org.labkey.experiment.xar.XarExpander;
import org.labkey.experiment.xar.AbstractXarImporter;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.*;

public class XarReader extends AbstractXarImporter
{
    private Set<String> _experimentLSIDs = new HashSet<String>();
    private Map<String, Integer> _propertyIdMap = new HashMap<String, Integer>();
    private Map<String, PropertyDescriptor> _dataInputRoleMap = new HashMap<String, PropertyDescriptor>();
    private Map<String, PropertyDescriptor> _materialInputRoleMap = new HashMap<String, PropertyDescriptor>();

    private List<DeferredDataLoad> _deferredDataLoads = new ArrayList<DeferredDataLoad>();

    private final XarContext _xarContext;

    private List<ExpRun> _loadedRuns = new ArrayList<ExpRun>();

    protected final PipelineJob _job;
    public static final String CONTACT_PROPERTY = "terms.fhcrc.org#Contact";
    public static final String CONTACT_ID_PROPERTY = "terms.fhcrc.org#ContactId";
    public static final String CONTACT_EMAIL_PROPERTY = "terms.fhcrc.org#Email";
    public static final String CONTACT_FIRST_NAME_PROPERTY = "terms.fhcrc.org#FirstName";
    public static final String CONTACT_LAST_NAME_PROPERTY = "terms.fhcrc.org#LastName";

    public static final String ORIGINAL_URL_PROPERTY = "terms.fhcrc.org#Data.OriginalURL";
    public static final String ORIGINAL_URL_PROPERTY_NAME = "OriginalURL";
    private List<String> _processedRunsLSIDs = new ArrayList<String>();

    public XarReader(XarSource source, PipelineJob job)
    {
        super(source, job.getContainer(), job.getLogger(), job.getUser());
        _xarContext = new XarContext(job.getDescription(), job.getContainer(), job.getUser());
        _job = job;
    }

    private User getUser() { return _user; }

    public void parseAndLoad(boolean reloadExistingRuns) throws ExperimentException
    {
        FileOutputStream fos = null;

        try
        {
            ExperimentArchiveDocument document = _xarSource.getDocument();

            // Create an XmlOptions instance and set the error listener.
            XmlOptions validateOptions = new XmlOptions();
            ArrayList<XmlError> errorList = new ArrayList<XmlError>();
            validateOptions.setErrorListener(errorList);

            // Validate the XML.
            if (!document.validate(validateOptions))
                checkValidationErrors(document, errorList);

            _experimentArchive = document.getExperimentArchive();
            loadDoc(reloadExistingRuns);

            File expDir = new File(_xarSource.getRoot(), "export");
            if (expDir.exists() && expDir.isDirectory())
            {
                ExperimentRunType a = _experimentArchive.getExperimentRuns().getExperimentRunArray(0);
                a.setCreateNewIfDuplicate(false);
                a.setGenerateDataFromStepRecord(false);
                for (int i = a.getExperimentLog().getExperimentLogEntryArray().length - 1; i >= 0; i--)
                    a.getExperimentLog().removeExperimentLogEntry(i);

                fos = new FileOutputStream(expDir + "/experiment.xml");
                XmlOptions xOpt = new XmlOptions().setSavePrettyPrint();
                document.save(fos, xOpt);
            }
        }
        catch (IOException e)
        {
            throw new XarFormatException(e);
        }
        catch (XmlException e)
        {
            throw new XarFormatException(e);
        }
        finally
        {
            try
            {
                if (null != fos)
                    fos.close();
            }
            catch (IOException ioe) {}
        }
    }

    private void checkValidationErrors(ExperimentArchiveDocument xd, ArrayList<XmlError> errorList) throws XarFormatException
    {
        StringBuffer errorSB = new StringBuffer();

        boolean bHasDerivedTypeErrorsOnly = true;
        for (XmlError error : errorList)
        {
            // for one particular error type, try a fallback strategy
            if (bHasDerivedTypeErrorsOnly)
            {
                try {
                    XmlObject xObj = error.getObjectLocation();
                    XmlObject xObjWild;
                    String typeName;
                    if (null != xObj)
                    {
                        QName qName = xObj.schemaType().getName();
                        if (qName != null)
                        {
                            typeName = qName.getLocalPart();
                            if (typeName.endsWith("BaseType"))
                            {
                                String wType = "org.fhcrc.cpas.exp.xml." + typeName.substring(0, typeName.indexOf("BaseType")) + "Type";
                                SchemaType swType = xd.schemaType().getTypeSystem().typeForClassname(wType);
                                if (null != swType)
                                {
                                    _log.warn("Schema validation error: " + error.getMessage());
                                    xObjWild = xObj.changeType(swType);
                                    bHasDerivedTypeErrorsOnly = xObjWild.validate();
                                    if (bHasDerivedTypeErrorsOnly)
                                        _log.warn("Fixed by change to wildcard type");
                                    continue;
                                }
                            }
                        }
                    }
               // if failback strategy throws, just report original error
                } catch (Exception e) {   }
            }
            bHasDerivedTypeErrorsOnly = false;
            errorSB.append("Schema validation error: ");
            errorSB.append(error.getMessage());
            errorSB.append("\n");
            errorSB.append("Location of invalid XML: ");
            errorSB.append(error.getCursorLocation().xmlText());
            errorSB.append("\n");
        }

        if (!bHasDerivedTypeErrorsOnly)
        {
            throw new XarFormatException("Document failed schema validation\n"
                    + "The current schema for this _document can be found at " + ExperimentService.SCHEMA_LOCATION + " \n"
                    + "Validation errors found: \n"
                    + errorSB.toString());
        }
    }

    private void loadDoc(boolean deleteExistingRuns) throws ExperimentException
    {
        boolean existingTransaction = ExperimentService.get().getSchema().getScope().isTransactionActive();
        try
        {
            if (!existingTransaction)
            {
                ExperimentService.get().getSchema().getScope().beginTransaction();
            }

            ExperimentArchiveType.ExperimentRuns experimentRuns = _experimentArchive.getExperimentRuns();
            // Start by clearing out existing things that we're going to be importing
            if (experimentRuns != null)
            {
                deleteExistingExperimentRuns(experimentRuns, deleteExistingRuns);
            }

            ExperimentArchiveType.ProtocolActionDefinitions actionDefs = _experimentArchive.getProtocolActionDefinitions();
            if (actionDefs != null)
            {
                deleteUniqueActions(actionDefs.getProtocolActionSetArray());
            }

            ExperimentArchiveType.ProtocolDefinitions protocolDefs = _experimentArchive.getProtocolDefinitions();
            if (protocolDefs != null)
            {
                deleteUniqueProtocols(protocolDefs.getProtocolArray());
            }

            ExperimentArchiveType.DomainDefinitions domainDefs = _experimentArchive.getDomainDefinitions();
            if (domainDefs != null)
            {
                for (DomainDescriptorType domain : _experimentArchive.getDomainDefinitions().getDomainArray())
                {
                    loadDomain(domain);
                }
            }
            
            ExperimentArchiveType.SampleSets sampleSets = _experimentArchive.getSampleSets();
            if (sampleSets != null)
            {
                for (SampleSetType sampleSet : sampleSets.getSampleSetArray())
                {
                    loadSampleSet(sampleSet);
                }
            }

            // Then start loading
            for (ExperimentType exp : _experimentArchive.getExperimentArray())
            {
                loadExperiment(exp);
                _log.info("Experiment/Run group import complete");
                _log.info("");
            }

            if (protocolDefs != null)
            {
                for (ProtocolBaseType p : protocolDefs.getProtocolArray())
                {
                    loadProtocol(p);
                }
                _log.info("Protocol import complete");
                _log.info("");
            }

            if (actionDefs != null)
            {
                for (ProtocolActionSetType actionSet : actionDefs.getProtocolActionSetArray())
                {
                    loadActionSet(actionSet);
                }

                _log.info("Protocol action set import complete");
                _log.info("");
            }

            List<ExpMaterial> startingMaterials = new ArrayList<ExpMaterial>();
            List<Data> startingData = new ArrayList<Data>();

            if (_experimentArchive.getStartingInputDefinitions() != null)
            {
                for (MaterialBaseType material : _experimentArchive.getStartingInputDefinitions().getMaterialArray())
                {
                    // ignore dups of starting inputs
                    startingMaterials.add(loadMaterial(material, null, null, _xarContext));
                }
                for (DataBaseType data : _experimentArchive.getStartingInputDefinitions().getDataArray())
                {
                    startingData.add(loadData(data, null, null, _xarContext));
                }

                _log.info("Starting input import complete");
                _log.info("");
            }

            if (experimentRuns != null)
            {
                for (ExperimentRunType experimentRun : experimentRuns.getExperimentRunArray())
                {
                    loadExperimentRun(experimentRun, startingMaterials, startingData);
                }
            }

            if (!existingTransaction)
            {
                ExperimentService.get().getSchema().getScope().commitTransaction();
            }

            try
            {
                FileUtil.deleteDir(ExperimentRunGraph.getFolderDirectory(_container.getRowId()));
            }
            catch (IOException e)
            {
                // Non-fatal
                _log.error("Failed to clear cached experiment run graphs for container " + _container, e);
            }

            for (DeferredDataLoad deferredDataLoad : _deferredDataLoads)
            {
                loadDataFile(deferredDataLoad.getData());
            }
        }
        catch (SQLException e)
        {
            throw new XarFormatException(e);
        }
        finally
        {
            if (!existingTransaction)
            {
                ExperimentService.get().getSchema().getScope().closeConnection();
            }
        }
    }

    private MaterialSource loadSampleSet(SampleSetType sampleSet) throws XarFormatException, SQLException
    {
        String lsid = LsidUtils.resolveLsidFromTemplate(sampleSet.getAbout(), _xarContext, "SampleSet");
        MaterialSource existingMaterialSource = ExperimentServiceImpl.get().getMaterialSource(lsid);

        _log.info("Importing SampleSet with LSID '" + lsid + "'");
        MaterialSource materialSource = new MaterialSource();
        materialSource.setDescription(sampleSet.getDescription());
        materialSource.setName(sampleSet.getName());
        materialSource.setLSID(lsid);
        materialSource.setContainer(_container.getId());
        materialSource.setMaterialLSIDPrefix(LsidUtils.resolveLsidFromTemplate(sampleSet.getMaterialLSIDPrefix(), _xarContext, "Material"));

        if (existingMaterialSource != null)
        {
            List<IdentifiableEntity.Difference> diffs = new ArrayList<IdentifiableEntity.Difference>();
            IdentifiableEntity.diff(materialSource.getName(), existingMaterialSource.getName(), "Name", diffs);
            IdentifiableEntity.diff(materialSource.getDescription(), existingMaterialSource.getDescription(), "Description", diffs);
            IdentifiableEntity.diff(materialSource.getMaterialLSIDPrefix(), existingMaterialSource.getMaterialLSIDPrefix(), "Material LSID prefix", diffs);

            if (!diffs.isEmpty())
            {
                _log.error("The SampleSet specified with LSID '" + lsid + "' has " + diffs.size() + " differences from the one that has already been loaded");
                for (IdentifiableEntity.Difference diff : diffs)
                {
                    _log.error(diff.toString());
                }
                throw new XarFormatException("SampleSet with LSID '" + lsid + "' does not match existing SampleSet");
            }

            return existingMaterialSource;
        }

        materialSource = ExperimentServiceImpl.get().insertMaterialSource(_user, materialSource, null);
        if (ExperimentService.get().lookupActiveSampleSet(_container) == null)
        {
            ExperimentService.get().setActiveSampleSet(_container, new ExpSampleSetImpl(materialSource));
        }

        return materialSource;
    }

    private Domain loadDomain(DomainDescriptorType xDomain) throws SQLException, XarFormatException
    {
        String lsid = LsidUtils.resolveLsidFromTemplate(xDomain.getDomainURI(), _xarContext, "Domain");
        DomainDescriptor existingDomainDescriptor = OntologyManager.getDomainDescriptor(lsid, _container);
        DomainImpl existingDomain = existingDomainDescriptor == null ? null : new DomainImpl(existingDomainDescriptor);

        DomainImpl domain = new DomainImpl(_container, lsid, xDomain.getName());
        domain.setDescription(xDomain.getDescription());

        Map<String, DomainProperty> newProps = new HashMap<String, DomainProperty>();
        if (xDomain.getPropertyDescriptorArray() != null)
        {
            for (PropertyDescriptorType xProp : xDomain.getPropertyDescriptorArray())
            {
                DomainProperty prop = domain.addProperty();
                prop.setDescription(xProp.getDescription());
                prop.setFormat(xProp.getFormat());
                prop.setLabel(xProp.getLabel());
                prop.setName(xProp.getName());
                prop.setRangeURI(xProp.getRangeURI());
                String propertyURI = xProp.getPropertyURI();
                if (propertyURI != null && propertyURI.indexOf("${") != -1)
                {
                    propertyURI = LsidUtils.resolveLsidFromTemplate(propertyURI, _xarContext);
                }
                prop.setPropertyURI(propertyURI);
                prop.getPropertyDescriptor().setConceptURI(xProp.getConceptURI());
                if (xProp.isSetOntologyURI())
                {
                    String uri = xProp.getOntologyURI().trim();
                    if (uri.indexOf("${") != -1)
                    {
                        uri = LsidUtils.resolveLsidFromTemplate(xProp.getOntologyURI(), _xarContext);
                    }
                    prop.getPropertyDescriptor().setOntologyURI(uri);
                }
                prop.getPropertyDescriptor().setSearchTerms(xProp.getSearchTerms());
                prop.getPropertyDescriptor().setSemanticType(xProp.getSemanticType());
                newProps.put(prop.getPropertyURI(), prop);
            }
        }

        if (existingDomain != null)
        {
            List<IdentifiableEntity.Difference> diffs = new ArrayList<IdentifiableEntity.Difference>();
            IdentifiableEntity.diff(existingDomain.getName(), domain.getName(), "Name", diffs);
            Map<String, DomainProperty> oldProps = new HashMap<String, DomainProperty>();
            for (DomainProperty oldProp : existingDomain.getProperties())
            {
                oldProps.put(oldProp.getPropertyURI(), oldProp);
            }

            if (!IdentifiableEntity.diff(oldProps.keySet(), newProps.keySet(), "Domain Properties", diffs))
            {
                for (String key : oldProps.keySet())
                {
                    DomainProperty oldProp = oldProps.get(key);
                    DomainProperty newProp = newProps.get(key);

                    IdentifiableEntity.diff(oldProp.getDescription(), newProp.getDescription(), key + " description", diffs);
                    IdentifiableEntity.diff(oldProp.getFormatString(), newProp.getFormatString(), key + " format string", diffs);
                    IdentifiableEntity.diff(oldProp.getLabel(), newProp.getLabel(), key + " label", diffs);
                    IdentifiableEntity.diff(oldProp.getName(), newProp.getName(), key + " name", diffs);
                    IdentifiableEntity.diff(oldProp.getPropertyURI(), newProp.getPropertyURI(), key + " property URI", diffs);
                    IdentifiableEntity.diff(oldProp.getPropertyDescriptor().getSearchTerms(), newProp.getPropertyDescriptor().getSearchTerms(), key + " search terms", diffs);
                    IdentifiableEntity.diff(oldProp.getPropertyDescriptor().getSemanticType(), newProp.getPropertyDescriptor().getSemanticType(), key + " semantic type", diffs);
                    IdentifiableEntity.diff(oldProp.getPropertyDescriptor().getRangeURI(), newProp.getPropertyDescriptor().getRangeURI(), key + " range URI", diffs);
                    IdentifiableEntity.diff(oldProp.getPropertyDescriptor().getConceptURI(), newProp.getPropertyDescriptor().getConceptURI(), key + " concept URI", diffs);
                }
            }

            if (!diffs.isEmpty())
            {
                _log.error("The domain specified with LSID '" + lsid + "' has " + diffs.size() + " differences from the domain that has already been loaded");
                for (IdentifiableEntity.Difference diff : diffs)
                {
                    _log.error(diff.toString());
                }
                throw new XarFormatException("Domain with LSID '" + lsid + "' does not match existing domain");
            }

            return new DomainImpl(existingDomainDescriptor);
        }

        try
        {
            domain.save(_user);
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw new XarFormatException(e);
        }

        return domain;
    }

    private void deleteUniqueActions(ProtocolActionSetType[] actionDefs) throws ExperimentException, SQLException
    {
        for (ProtocolActionSetType actionDef : actionDefs)
        {
            String protocolLSID = LsidUtils.resolveLsidFromTemplate(actionDef.getParentProtocolLSID(), _xarContext, "Protocol");
            Protocol existingProtocol = ExperimentServiceImpl.get().getProtocol(protocolLSID);

            if (existingProtocol != null)
            {
                // First make sure it isn't in use by some run that's not part of this file
                if (ExperimentService.get().getExpProtocolApplicationsForProtocolLSID(protocolLSID).length == 0 &&
                    ExperimentService.get().getExpRunsForProtocolIds(false, existingProtocol.getRowId()).isEmpty())
                {
                    _log.info("Deleting existing action set with parent protocol LSID '" + protocolLSID + "' so that the protocol specified in the file can be uploaded");
                    ExperimentService.get().deleteProtocolByRowIds(_container, getUser(), existingProtocol.getRowId());
                }
                else
                {
                    _log.info("Existing action set with parent protocol LSID '" + protocolLSID + "' is referenced by other experiment runs, so it cannot be updated");
                }
            }
        }
    }

    private void deleteUniqueProtocols(ProtocolBaseType[] protocolDefs) throws SQLException, ExperimentException
    {
        for (ProtocolBaseType protocol : protocolDefs)
        {
            String protocolLSID = LsidUtils.resolveLsidFromTemplate(protocol.getAbout(), _xarContext, "Protocol");
            ExpProtocol existingProtocol = ExperimentService.get().getExpProtocol(protocolLSID);

            if (existingProtocol != null)
            {
                // Delete any protocols from the XAR that are in the database but aren't referenced as part of a ProtocolActionSet
                if (existingProtocol.getParentProtocols().length == 0)
                {
                    _log.info("Deleting existing protocol with LSID '" + protocolLSID + "' so that the protocol specified in the file can be uploaded");
                    ExperimentService.get().deleteProtocolByRowIds(_container, getUser(), existingProtocol.getRowId());
                }
                else
                {
                    _log.info("Existing protocol with LSID '" + protocolLSID + "' is referenced by other experiment runs, so it cannot be updated");
                }
            }
        }
    }

    private void deleteExistingExperimentRuns(ExperimentArchiveType.ExperimentRuns experimentRuns, boolean deleteExistingRuns) throws SQLException, ExperimentException
    {
        for (ExperimentRunType experimentRun : experimentRuns.getExperimentRunArray())
        {
            String runLSID = LsidUtils.resolveLsidFromTemplate(experimentRun.getAbout(), _xarContext, "ExperimentRun", "ExperimentRun");

            // Clear out any existing runs with the same LSID
            ExpRun existingRun = ExperimentService.get().getExpRun(runLSID);
            if (existingRun != null && (deleteExistingRuns || !PageFlowUtil.nullSafeEquals(existingRun.getFilePathRoot(), _xarSource.getRoot().getPath())))
            {
                _log.info("Deleting existing experiment run with LSID'" + runLSID + "' so that the run specified in the file can be uploaded");
                ExperimentService.get().deleteExperimentRunsByRowIds(_container, getUser(), existingRun.getRowId());
            }
        }
    }


    private void loadExperiment(ExperimentType exp) throws SQLException, XarFormatException
    {
        if (exp == null)
        {
            throw new XarFormatException("No experiment found");
        }

        PropertyCollectionType xbProps = exp.getProperties();
        if (null != xbProps)
        {
            Map<String, Object> mProps = getSimplePropertiesMap(xbProps);
            Object lsidAuthorityTemplate = mProps.get("terms.fhcrc.org#XarTemplate.LSIDAuthority");
            if (lsidAuthorityTemplate instanceof String)
            {
                _xarContext.addSubstitution("LSIDAuthority", (String)lsidAuthorityTemplate);
            }
            Object lsidNamespaceSuffixTemplate = mProps.get("terms.fhcrc.org#XarTemplate.LSIDNamespaceSuffix");
            if (lsidNamespaceSuffixTemplate instanceof String)
            {
                _xarContext.addSubstitution("LSIDNamespace.Suffix", (String)lsidNamespaceSuffixTemplate);
            }
        }

        String experimentLSID = LsidUtils.resolveLsidFromTemplate(exp.getAbout(), _xarContext, "Experiment");
        _experimentLSIDs.add(experimentLSID);

        TableInfo tiExperiment = ExperimentServiceImpl.get().getTinfoExperiment();

        Experiment experiment = ExperimentServiceImpl.get().getExperiment(experimentLSID);
        if (null == experiment)
        {
            experiment = new Experiment();

            experiment.setLSID(experimentLSID);
            experiment.setHypothesis(trimString(exp.getHypothesis()));
            experiment.setName(trimString(exp.getName()));
            if (null != exp.getContact())
                experiment.setContactId(exp.getContact().getContactId());
            experiment.setExperimentDescriptionURL(trimString(exp.getExperimentDescriptionURL()));
            experiment.setComments(trimString(exp.getComments()));
            experiment.setContainer(_container.getId());

            experiment = Table.insert(getUser(), tiExperiment, experiment);

            ObjectProperty contactProperty = null;
            if (null != exp.getContact())
                contactProperty = readContact(exp.getContact(), experimentLSID);

            loadPropertyCollection(xbProps, experimentLSID, experimentLSID, contactProperty);
        }
        else
        {
            if (!experiment.getContainer().equals(_container.getId()))
            {
                Container container = ContainerManager.getForId(experiment.getContainer());
                throw new XarFormatException("This experiment already exists in another folder, " + (container == null ? "" : container.getPath()));
            }
        }

        if (null == experiment)
            throw new XarFormatException("Experiment insertion failed");

        if (null != exp.getContact())
            _xarContext.addSubstitution("ContactId", exp.getContact().getContactId());

        _log.info("Finished loading Experiment with LSID '" + experimentLSID + "'");
    }

    public List<ExpRun> getExperimentRuns()
    {
        return _loadedRuns;
    }

    private void loadExperimentRun(ExperimentRunType a, List<ExpMaterial> startingMaterials, List<Data> startingData) throws SQLException, ExperimentException
    {
        XarContext runContext = new XarContext(_xarContext);

        String experimentLSID = null;
        if (a.isSetExperimentLSID())
        {
            experimentLSID = LsidUtils.resolveLsidFromTemplate(a.getExperimentLSID(), runContext, "Experiment");
        }
        else
        {
            if (_experimentLSIDs.size() == 1)
            {
                experimentLSID = _experimentLSIDs.iterator().next();
            }
        }

        runContext.addSubstitution("ExperimentLSID", experimentLSID);

        String runLSID = LsidUtils.resolveLsidFromTemplate(a.getAbout(), runContext, "ExperimentRun", "ExperimentRun");

        // First check if the run has already been deleted
        ExpRun existingRun = ExperimentService.get().getExpRun(runLSID);
        if (existingRun != null)
        {
            _log.warn("Experiment run already exists, it will NOT be reimported, LSID '" + runLSID + "'");
            for (ExpData d : ExperimentServiceImpl.get().getAllDataUsedByRun(existingRun.getRowId()))
            {
                _deferredDataLoads.add(new DeferredDataLoad(d, existingRun));
            }
            _log.info("Experiment run import complete, LSID '" + runLSID + "'");
            _log.info("");
            return;
        }

        Lsid pRunLSID = new Lsid(runLSID);
        String runProtocolLSID = LsidUtils.resolveLsidFromTemplate(a.getProtocolLSID(), _xarContext, "Protocol");
        Protocol protocol = ExperimentServiceImpl.get().getProtocol(runProtocolLSID);
        if (protocol == null)
        {
            throw new XarFormatException("Unknown protocol " + runProtocolLSID + " referenced by ExperimentRun " + pRunLSID);
        }

        TableInfo tiExperimentRun = ExperimentServiceImpl.get().getTinfoExperimentRun();

        ExperimentRun run = ExperimentServiceImpl.get().getExperimentRun(pRunLSID.toString());

        if (null != run)
        {
            if (a.getCreateNewIfDuplicate())
            {
                while (null != run)
                {
                    //make the lsid unique and retry
                    String suffix = Long.toString(Math.round(Math.random() * 100));
                    pRunLSID.setObjectId(pRunLSID.getObjectId() + "." + suffix);
                    run = ExperimentServiceImpl.get().getExperimentRun(pRunLSID.toString());
                }
            }
            else
            {
                throw new XarFormatException("An ExperimentRun with LSID " + pRunLSID.toString() + " already exists");
            }
        }

        if (run == null)
        {
            ExperimentRun vals = new ExperimentRun();
            // todo not sure about having roots stored in database
            // todo support substitutions here?

            vals.setLSID(pRunLSID.toString());

            String name = trimString(a.getName());
            vals.setName(name);
            vals.setProtocolLSID(runProtocolLSID);
            vals.setComments(trimString(a.getComments()));

            vals.setFilePathRoot(_xarSource.getRoot().getPath());
            vals.setContainer(_container.getId());

            run = Table.insert(getUser(), tiExperimentRun, vals);
        }

        int runId = run.getRowId();

        if (experimentLSID != null)
        {
            ExpExperiment e = ExperimentService.get().getExpExperiment(experimentLSID);
            ExperimentService.get().addRunsToExperiment(e.getRowId(), runId);
        }


        // now get substitution strings that are themselves templates
        runContext.addSubstitution("ExperimentRun.RowId", Integer.toString(runId));
        runContext.addSubstitution("ExperimentRun.LSID", pRunLSID.toString());
        runContext.addSubstitution("ExperimentRun.Name", trimString(a.getName()));

        PropertyCollectionType xbProps = a.getProperties();

        loadPropertyCollection(xbProps, run.getLSID(), run.getLSID(), null);


        // if ExperimentLog is present and ProtocolApps section is not, generate from log
        // if both are present, look for generatedata attribute

        if ((a.getProtocolApplications().getProtocolApplicationArray().length == 0) || a.getGenerateDataFromStepRecord())
        {
            ExperimentLogEntryType [] steps = a.getExperimentLog().getExperimentLogEntryArray();

            if ((null != steps) && (steps.length > 0))
            {
                ProtocolActionStepDetail stepProtocol = ExperimentServiceImpl.get().getProtocolActionStepDetail(runProtocolLSID, steps[0].getActionSequenceRef());
                if (stepProtocol == null)
                {
                    throw new XarFormatException("Protocol Not Found for Action Sequence =" + steps[0].getActionSequenceRef() + " in parent protocol " + runProtocolLSID);
                }
                String stepProtocolLSID = trimString(stepProtocol.getLSID());
                if (!stepProtocolLSID.equals(runProtocolLSID))
                {
                    throw new XarFormatException("Invalid ExperimentRun start action: " + stepProtocolLSID);
                }

                XarExpander expander = new XarExpander(_log, _xarSource, _container, run, startingData, startingMaterials, _experimentArchive, _user);

                expander.expandSteps(steps, runContext, a);
                loadProtocolApplications(a, run, runContext);
            }
        }
        else
        {
            loadProtocolApplications(a, run, runContext);
        }
        _processedRunsLSIDs.add(runLSID);
        ExpRun loadedRun = ExperimentService.get().getExpRun(runLSID);
        assert loadedRun != null;
        _loadedRuns.add(loadedRun);
        _log.info("Finished loading ExperimentRun with LSID '" + runLSID + "'");
        _log.info("");
    }


    public List<String> getProcessedRunsLSIDs()
    {
        return _processedRunsLSIDs;
    }

    private void loadProtocolApplications(ExperimentRunType a, ExperimentRun run, XarContext context)
            throws SQLException, ExperimentException
    {
        ProtocolApplicationBaseType[] protApps = a.getProtocolApplications().getProtocolApplicationArray();
        boolean firstApp = true;
        for (ProtocolApplicationBaseType protApp : protApps)
        {
            loadProtocolApplication(protApp, run, context, firstApp);
            firstApp = false;
        }
    }

    private void loadProtocolApplication(ProtocolApplicationBaseType xmlProtocolApp,
                                         ExperimentRun experimentRun,
                                         XarContext context, boolean firstApp) throws SQLException, ExperimentException
    {
        InputOutputRefsType.MaterialLSID[] inputMaterialLSIDs = xmlProtocolApp.getInputRefs().getMaterialLSIDArray();
        InputOutputRefsType.DataLSID[] inputDataLSIDs = xmlProtocolApp.getInputRefs().getDataLSIDArray();

        TableInfo tiProtApp = ExperimentServiceImpl.get().getTinfoProtocolApplication();
        TableInfo tiMaterialInput = ExperimentServiceImpl.get().getTinfoMaterialInput();
        TableInfo tiDataInput = ExperimentServiceImpl.get().getTinfoDataInput();

        Long dateval = xmlProtocolApp.getActivityDate().getTimeInMillis();
        java.sql.Timestamp sqlDateTime;
        if (null != dateval)
            sqlDateTime = new java.sql.Timestamp(dateval.longValue());
        else
            sqlDateTime = new java.sql.Timestamp(System.currentTimeMillis());

        String protAppLSID = LsidUtils.resolveLsidFromTemplate(xmlProtocolApp.getAbout(), context, "ProtocolApplication");

        String protocolLSID = LsidUtils.resolveLsidFromTemplate(xmlProtocolApp.getProtocolLSID(), context, "Protocol");
        if (ExperimentService.get().getExpProtocol(protocolLSID) == null)
        {
            throw new XarFormatException("Unknown protocol " + xmlProtocolApp.getProtocolLSID() + " referenced by protocol application " + protAppLSID);
        }

        int runId = experimentRun.getRowId();

        ProtocolApplication protocolApp = ExperimentServiceImpl.get().getProtocolApplication(protAppLSID);
        if (protocolApp == null)
        {
            protocolApp = new ProtocolApplication();

            protocolApp.setLSID(protAppLSID);
            protocolApp.setName(trimString(xmlProtocolApp.getName()));
            String cpasType = trimString(xmlProtocolApp.getCpasType());
            checkProtocolApplicationCpasType(cpasType, _log);
            protocolApp.setCpasType(cpasType);
            protocolApp.setProtocolLSID(protocolLSID);
            protocolApp.setActivityDate(sqlDateTime);
            protocolApp.setActionSequence(xmlProtocolApp.getActionSequence());
            protocolApp.setRunId(runId);
            protocolApp.setComments(trimString(xmlProtocolApp.getComments()));

            protocolApp = Table.insert(getUser(), tiProtApp, protocolApp);
        }

        if (null == protocolApp)
            throw new XarFormatException("No row found");

        int protAppId = protocolApp.getRowId();

        PropertyCollectionType xbProps = xmlProtocolApp.getProperties();

        loadPropertyCollection(xbProps, protAppLSID, experimentRun.getLSID(), null);

        SimpleValueCollectionType xbParams = xmlProtocolApp.getProtocolApplicationParameters();
        if (xbParams != null)
            loadProtocolApplicationParameters(xbParams, protAppId);

        //todo  extended protocolApp types??

        for (InputOutputRefsType.MaterialLSID inputMaterialLSID : inputMaterialLSIDs)
        {
            String declaredType = (inputMaterialLSID.isSetCpasType() ? inputMaterialLSID.getCpasType() : "Material");
            checkMaterialCpasType(declaredType);
            String lsid = LsidUtils.resolveLsidFromTemplate(inputMaterialLSID.getStringValue(), context, declaredType, "Material");

            ExpMaterial inputRow = _xarSource.getMaterial(firstApp ? null : new ExpRunImpl(experimentRun), new ExpProtocolApplicationImpl(protocolApp), lsid);
            if (firstApp)
            {
                _xarSource.addMaterial(experimentRun.getLSID(), inputRow);
            }
            SimpleFilter filter = new SimpleFilter("MaterialId", inputRow.getRowId());
            filter = filter.addCondition("TargetApplicationId", protAppId);
            if (Table.selectObject(tiMaterialInput, filter, null, MaterialInput.class) == null)
            {
                MaterialInput mi = new MaterialInput();
                mi.setMaterialId(inputRow.getRowId());
                mi.setTargetApplicationId(protAppId);
                PropertyDescriptor pdRole = null;
                String roleName = inputMaterialLSID.getRoleName();
                if (roleName != null)
                {
                    pdRole = _materialInputRoleMap.get(inputMaterialLSID.getRoleName());
                    if (pdRole == null)
                    {
                        try
                        {
                            // Consider (nicksh): we're passing null for the material here.
                            // Should we pass something so that the property descriptor gets created with the right
                            // rangeURI, or should it just be the active sample set?
                            pdRole = ExperimentService.get().ensureMaterialInputRole(_container, roleName, null);
                            _materialInputRoleMap.put(roleName, pdRole);
                        }
                        catch (SQLException e)
                        {
                            throw new ExperimentException(e);
                        }
                    }
                }
                if (pdRole != null)
                {
                    mi.setPropertyId(pdRole.getPropertyId());
                }
                Table.insert(getUser(), tiMaterialInput, mi);
            }
        }

        for (InputOutputRefsType.DataLSID inputDataLSID : inputDataLSIDs)
        {
            String declaredType = (inputDataLSID.isSetCpasType() ? inputDataLSID.getCpasType() : "Data");
            checkDataCpasType(declaredType);
            String lsid = LsidUtils.resolveLsidFromTemplate(inputDataLSID.getStringValue(), context, declaredType, new AutoFileLSIDReplacer(inputDataLSID.getDataFileUrl(), _container, _xarSource));

            ExpData data = _xarSource.getData(firstApp ? null : new ExpRunImpl(experimentRun), new ExpProtocolApplicationImpl(protocolApp), lsid);
            if (firstApp)
            {
                _xarSource.addData(experimentRun.getLSID(), data);
            }

            SimpleFilter filter = new SimpleFilter("DataId", data.getRowId());
            filter = filter.addCondition("TargetApplicationId", protAppId);
            if (Table.selectObject(tiDataInput, filter, null, DataInput.class) == null)
            {
                DataInput input = new DataInput();
                input.setDataId(data.getRowId());
                input.setTargetApplicationId(protAppId);
                String roleName = inputDataLSID.getRoleName();
                PropertyDescriptor pdRole = null;
                if (roleName != null)
                {
                    pdRole = _dataInputRoleMap.get(roleName);
                    if (pdRole == null)
                    {
                        try
                        {
                            pdRole = ExperimentService.get().ensureDataInputRole(getUser(), _container, roleName, null);
                            _dataInputRoleMap.put(roleName, pdRole);
                        }
                        catch (SQLException e)
                        {
                            throw new ExperimentException(e);
                        }
                    }
                }
                if (pdRole != null)
                {
                    input.setPropertyId(pdRole.getPropertyId());
                }
                Table.insert(getUser(), tiDataInput, input);
            }
        }

        MaterialBaseType [] outputMaterials = xmlProtocolApp.getOutputMaterials().getMaterialArray();
        for (MaterialBaseType outputMaterial : outputMaterials)
        {
            loadMaterial(outputMaterial, experimentRun, protAppId, context);
        }

        DataBaseType [] outputData = xmlProtocolApp.getOutputDataObjects().getDataArray();
        for (DataBaseType d : outputData)
        {
            loadData(d, experimentRun, protAppId, context);
        }
        _log.info("Finished loading ProtocolApplication with LSID '" + protocolLSID + "'");
    }

    private ExpMaterial loadMaterial(MaterialBaseType xbMaterial,
                                  ExperimentRun run,
                                  Integer sourceApplicationId,
                                  XarContext context) throws SQLException, XarFormatException
    {
        TableInfo tiMaterial = ExperimentServiceImpl.get().getTinfoMaterial();

        String declaredType = xbMaterial.getCpasType();
        if (null == declaredType)
            declaredType = "Material";
        if (declaredType.indexOf("${") != -1)
        {
            declaredType = LsidUtils.resolveLsidFromTemplate(declaredType, context, "SampleSet");
        }
        checkMaterialCpasType(declaredType);

        String materialLSID = LsidUtils.resolveLsidFromTemplate(xbMaterial.getAbout(), context, declaredType, "Material");

        Material material = ExperimentServiceImpl.get().getMaterial(materialLSID);
        if (material == null)
        {
            material = new Material();
            material.setLSID(materialLSID);
            material.setName(trimString(xbMaterial.getName()));
            material.setCpasType(declaredType);
            if (xbMaterial.isSetSourceProtocolLSID())
            {
                String sourceProtocolLSID = xbMaterial.getSourceProtocolLSID();
                if (sourceProtocolLSID != null && !"".equals(sourceProtocolLSID))
                {
                    sourceProtocolLSID = LsidUtils.resolveLsidFromTemplate(sourceProtocolLSID, context, "Protocol");
                    material.setSourceProtocolLSID(sourceProtocolLSID);
                }
            }

            if (null != run)
                material.setRunId(run.getRowId());

            material.setContainer(_container.getId());

            if (null != sourceApplicationId)
                material.setSourceApplicationId(sourceApplicationId);
            material = Table.insert(getUser(), tiMaterial, material);

            PropertyCollectionType xbProps = xbMaterial.getProperties();
            if (null == xbProps)
                xbProps = xbMaterial.addNewProperties();
            loadPropertyCollection(xbProps, materialLSID, run == null ? null : run.getLSID(), null);

            loadExtendedMaterialType(xbMaterial, materialLSID, run);
        }
        else
        {
            updateSourceInfo(material, xbMaterial.getSourceProtocolLSID(), sourceApplicationId, run == null ? null : run.getRowId(), context, tiMaterial);
        }

        ExpMaterial expMaterial = new ExpMaterialImpl(material);
        _xarSource.addMaterial(run == null ? null : run.getLSID(), expMaterial);

        _log.info("Finished loading material with LSID '" + materialLSID + "'");
        return expMaterial;
    }

    private void updateSourceInfo(ProtocolOutput output, String sourceProtocolLSID, Integer sourceApplicationId,
                                  Integer runId, XarContext context, TableInfo tableInfo)
            throws XarFormatException, SQLException
    {
        String description = output.getClass().getSimpleName();
        String lsid = output.getLSID();
        boolean changed = false;

        _log.info("Found an existing entry for " + description + " LSID " + lsid + ", not reloading its values from scratch");

        if (sourceProtocolLSID != null && !"".equals(sourceProtocolLSID))
        {
            String newSourceProtocolLSID = LsidUtils.resolveLsidFromTemplate(sourceProtocolLSID, context, "Protocol");
            if (output.getSourceProtocolLSID() == null)
            {
                _log.info("Updating  " + description + " with LSID '" + lsid + "', setting SourceProtocolLSID");
                output.setSourceProtocolLSID(newSourceProtocolLSID);
                changed = true;
            }
            else if (output.getSourceProtocolLSID().equals(newSourceProtocolLSID))
            {
                _log.info(description + " with LSID '" + lsid + "' already has a SourceProtocolLSID of '" + output.getSourceProtocolLSID() + "'");
            }
            else
            {
                throw new XarFormatException(description + " with LSID '" + lsid + "' already has a source protocol LSID of " + output.getSourceProtocolLSID() + ", cannot set it to " + sourceProtocolLSID);
            }
        }
        if (sourceApplicationId != null)
        {
            if (output.getSourceApplicationId() == null)
            {
                _log.info("Updating " + description + " with LSID '" + lsid + "', setting SourceApplicationId");
                output.setSourceApplicationId(sourceApplicationId);
                changed = true;
            }
            else
            {
                throw new XarFormatException(description + " with LSID '" + lsid + "' already has a source application of " + output.getSourceApplicationId() + ", cannot set it to " + sourceApplicationId);
            }
        }
        if (runId != null)
        {
            if (output.getRunId() == null)
            {
                _log.info("Updating " + description + " with LSID '" + lsid + "', setting its RunId");
                output.setRunId(runId);
                changed = true;
            }
            else
            {
                throw new XarFormatException(description + " with LSID '" + lsid + "' already has an experiment run id of " + output.getRunId() + ", cannot set it to " + runId);
            }
        }

        if (changed)
        {
            Table.update(getUser(), tableInfo, output, output.getRowId(), null);
        }
    }

    private void loadDataFile(ExpData data) throws ExperimentException, SQLException
    {
        String dataFileURL = data.getDataFileUrl();

        if (dataFileURL == null)
        {
            return;
        }

        if (_xarSource.shouldIgnoreDataFiles())
        {
            _log.info("Skipping load of data file " + dataFileURL + " based on the XAR source");
            return;
        }

        try
        {
            _log.info("Trying to load data file " + dataFileURL + " into the system");

            File file = new File(new URI(dataFileURL));

            if (!file.exists())
            {
                _log.warn("Unable to find the data file " + file.getPath() + " on disk.");
                return;
            }

            // Check that the file is under the pipeline root
            PipeRoot pr = PipelineService.get().findPipelineRoot(_container);
            try
            {
                if (!_xarSource.isUnderPipelineRoot(pr, _container, file))
                {
                    if (pr == null)
                    {
                        _log.warn("No pipeline root was set, skipping load of file " + file.getPath());
                        return;
                    }
                    _log.warn("The data file " + file.getCanonicalPath() + " is not under the folder's pipeline root, " + pr.getUri() + ". It will not be loaded directly, but may be loaded if referenced from other files that are under the pipeline root.");
                    return;
                }
            }
            catch (Exception e)
            {
                throw new XarFormatException("Error checking if file was under Pipeline Root", e);
            }

            ExperimentDataHandler handler = data.findDataHandler();
            try
            {
                handler.importFile(ExperimentService.get().getExpData(data.getRowId()), file, _job.getInfo(), _job.getLogger(), _xarContext);
            }
            catch (ExperimentException e)
            {
                throw new XarFormatException(e);
            }

            _log.info("Finished trying to load data file " + dataFileURL + " into the system");
        }
        catch (URISyntaxException e)
        {
            throw new XarFormatException(e);
        }
    }

    private Data loadData(DataBaseType xbData,
                          ExperimentRun experimentRun,
                          Integer sourceApplicationId,
                          XarContext context) throws SQLException, ExperimentException
    {
        TableInfo tiData = ExperimentServiceImpl.get().getTinfoData();

        String declaredType = xbData.getCpasType();
        if (null == declaredType)
            declaredType = "Data";
        if (declaredType.indexOf("${") != -1)
        {
            declaredType = LsidUtils.resolveLsidFromTemplate(declaredType, context, "Data");
        }
        checkDataCpasType(declaredType);

        String dataLSID = LsidUtils.resolveLsidFromTemplate(xbData.getAbout(), context, declaredType, new AutoFileLSIDReplacer(xbData.getDataFileUrl(), _container, _xarSource));

        ExpDataImpl databaseData = ExperimentServiceImpl.get().getExpData(dataLSID);
        if (databaseData != null)
        {
            File existingFile = databaseData.getFile();
            String uri = _xarSource.getCanonicalDataFileURL(trimString(xbData.getDataFileUrl()));
            if (uri != null && existingFile != null && existingFile.isFile())
            {
                try
                {
                    File newFile = new File(new URI(uri));
                    if (newFile.isFile() && !newFile.equals(existingFile))
                    {
                        byte[] existingHash = hashFile(existingFile);
                        byte[] newHash = hashFile(newFile);
                        if (!Arrays.equals(existingHash, newHash))
                        {
                            throw new ExperimentException("The data file with LSID " + dataLSID + " (referenced as "
                                    + xbData.getAbout() + " in the xar.xml, does not have the same contents as the " +
                                    "existing data file that has already been loaded.");
                        }
                    }
                }
                catch (URISyntaxException e)
                {
                    throw new ExperimentException(e);
                }
            }

            if (!databaseData.getContainer().equals(_container))
            {
                Container otherContainer = databaseData.getContainer();
                String containerDesc = otherContainer == null ? databaseData.getContainer().getPath() : otherContainer.getPath();
                throw new XarFormatException("Cannot reference a data file (" + databaseData.getDataFileUrl() + ") that has already been loaded into another container, " + containerDesc);
            }
            Integer runId = experimentRun == null || sourceApplicationId == null ? null : experimentRun.getRowId();
            updateSourceInfo(databaseData.getDataObject(), trimString(xbData.getSourceProtocolLSID()), sourceApplicationId, runId, context, tiData);
        }
        else
        {
            Data data = new Data();
            data.setLSID(dataLSID);
            data.setName(trimString(xbData.getName()));
            data.setCpasType(declaredType);
            String sourceProtocolLSID = trimString(xbData.getSourceProtocolLSID());
            if (sourceProtocolLSID != null && !"".equals(sourceProtocolLSID))
            {
                sourceProtocolLSID = LsidUtils.resolveLsidFromTemplate(sourceProtocolLSID, context, "Protocol");
                data.setSourceProtocolLSID(sourceProtocolLSID);
            }
            data.setContainer(_container.getId());

            if (null != sourceApplicationId)
            {
                data.setSourceApplicationId(sourceApplicationId);
                data.setRunId(experimentRun.getRowId());
            }

            if (null != trimString(xbData.getDataFileUrl()))
            {
                data.setDataFileUrl(_xarSource.getCanonicalDataFileURL(trimString(xbData.getDataFileUrl())));

                _deferredDataLoads.add(new DeferredDataLoad(new ExpDataImpl(data), new ExpRunImpl(experimentRun)));
            }

            Data insertedData = Table.insert(getUser(), tiData, data);
            // Pull from the database so we get the magically filled-in fields,
            // like Created, populated correctly
            databaseData = new ExpDataImpl(Table.selectObject(tiData, insertedData.getRowId(), Data.class));

            PropertyCollectionType xbProps = xbData.getProperties();
            if (null == xbProps)
                xbProps = xbData.addNewProperties();

            loadPropertyCollection(xbProps, dataLSID, null, null);

            if (xbProps.getSimpleValArray() != null)
            {
                for (SimpleValueType simpleValue : xbProps.getSimpleValArray())
                {
                    if (ORIGINAL_URL_PROPERTY.equals(simpleValue.getOntologyEntryURI()) && ORIGINAL_URL_PROPERTY_NAME.equals(simpleValue.getName()))
                    {
                        File f = databaseData.getFile();
                        if (f != null)
                        {
                            _xarContext.addData(new ExpDataImpl(data), simpleValue.getStringValue());
                        }
                        break;
                    }
                }
            }

            loadExtendedDataType(xbData, dataLSID, experimentRun);
        }

        _xarSource.addData(experimentRun == null ? null : experimentRun.getLSID(), databaseData);
        _log.info("Finished loading Data with LSID '" + dataLSID + "'");
        return databaseData.getDataObject();
    }

    private byte[] hashFile(File existingFile) throws ExperimentException
    {
        FileInputStream fIn = null;
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            fIn = new FileInputStream(existingFile);
            DigestInputStream dIn = new DigestInputStream(fIn, digest);
            byte[] b = new byte[4096];
            while (dIn.read(b) != -1) {};
            return digest.digest();
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ExperimentException(e);
        }
        finally
        {
            if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
        }
    }

    private void loadProtocolApplicationParameters(SimpleValueCollectionType xbParams,
                                                   int protAppId) throws SQLException
    {
        TableInfo tiValueTable = ExperimentServiceImpl.get().getTinfoProtocolApplicationParameter();
        for (SimpleValueType simple : xbParams.getSimpleValArray())
        {
            ProtocolApplicationParameter param = new ProtocolApplicationParameter();
            param.setProtocolApplicationId(protAppId);
            param.setRunId(protAppId);
            param.setXMLBeanValue(simple, _log);
            ExperimentServiceImpl.get().loadParameter(getUser(), param, tiValueTable, "ProtocolApplicationId", protAppId);
        }
    }


    private List<ProtocolParameter> readProtocolParameters(SimpleValueCollectionType xbParams)
    {
        List<ProtocolParameter> result = new ArrayList<ProtocolParameter>();
        for (SimpleValueType simple : xbParams.getSimpleValArray())
        {
            ProtocolParameter param = new ProtocolParameter();
            param.setXMLBeanValue(simple, _log);
            result.add(param);
        }
        return result;
    }

    private ObjectProperty readContact(ContactType contact,
                                       String parentLSID)
    {
        String propertyURI = GUID.makeURN();

        Map<String, ObjectProperty> childProps = new HashMap<String, ObjectProperty>();

        if (null != contact.getContactId() && !contact.getContactId().equals(""))
        {
            childProps.put(CONTACT_ID_PROPERTY, new ObjectProperty(propertyURI, _container.getId(), CONTACT_ID_PROPERTY, trimString(contact.getContactId()), "Contact Id"));
        }
        if (null != contact.getEmail() && !contact.getEmail().equals(""))
        {
            childProps.put(CONTACT_EMAIL_PROPERTY, new ObjectProperty(propertyURI, _container.getId(), CONTACT_EMAIL_PROPERTY, trimString(contact.getEmail()), "Contact Email"));
        }
        if (null != contact.getFirstName() && !contact.getFirstName().equals(""))
        {
            childProps.put(CONTACT_FIRST_NAME_PROPERTY, new ObjectProperty(propertyURI, _container.getId(), CONTACT_FIRST_NAME_PROPERTY, trimString(contact.getFirstName()), "Contact First Name"));
        }
        if (null != contact.getLastName() && !contact.getLastName().equals(""))
        {
            childProps.put(CONTACT_LAST_NAME_PROPERTY, new ObjectProperty(propertyURI, _container.getId(), CONTACT_LAST_NAME_PROPERTY, trimString(contact.getLastName()), "Contact Last Name"));
        }

        if (childProps.isEmpty())
        {
            return null;
        }

        ObjectProperty contactProperty = new ObjectProperty(parentLSID, _container.getId(), CONTACT_PROPERTY, new IdentifiableBase(propertyURI), "Contact");
        contactProperty.setChildProperties(childProps);

        return contactProperty;
    }

    private void setPropertyId(ObjectProperty objectProperty)
    {
        Integer id = _propertyIdMap.get(objectProperty.getPropertyURI());
        if (id != null)
        {
            objectProperty.setPropertyId(id);
            return;
        }
        PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(objectProperty.getPropertyURI(), _container);
        if (pd != null)
        {
            objectProperty.setPropertyId(pd.getPropertyId());
            _propertyIdMap.put(objectProperty.getPropertyURI(), pd.getPropertyId());
        }
    }

    private Map<String, ObjectProperty> readPropertyCollection(PropertyCollectionType xbValues,
                                                               String parentLSID, boolean checkForDuplicates) throws SQLException, XarFormatException
    {
        Map<String, ObjectProperty> existingProps = OntologyManager.getPropertyObjects(_container.getId(), parentLSID);
        Map<String, ObjectProperty> result = new HashMap<String, ObjectProperty>();

        for (SimpleValueType simpleProp : xbValues.getSimpleValArray())
        {
            PropertyType propType = PropertyType.getFromXarName(simpleProp.getValueType().toString());
            String value = trimString(simpleProp.getStringValue());
            if (value != null && value.startsWith("urn:lsid:"))
            {
                value = LsidUtils.resolveLsidFromTemplate(value, _xarContext);
            }

            String ontologyEntryURI = trimString(simpleProp.getOntologyEntryURI());
            if (ontologyEntryURI != null && ontologyEntryURI.indexOf("${") != -1)
            {
                ontologyEntryURI = LsidUtils.resolveLsidFromTemplate(simpleProp.getOntologyEntryURI(), _xarContext);
            }
            ObjectProperty objectProp = new ObjectProperty(parentLSID, _container.getId(), ontologyEntryURI, value, propType, simpleProp.getName());
            setPropertyId(objectProp);

            if (ExternalDocsURLCustomPropertyRenderer.URI.equals(trimString(objectProp.getPropertyURI())))
            {
                String relativePath = trimString(objectProp.getStringValue());
                if (relativePath != null)
                {
                    try
                    {
                        String fullPath = _xarSource.getCanonicalDataFileURL(relativePath);
                        File file = new File(new URI(fullPath));
                        if (file.exists())
                        {
                            objectProp.setStringValue(fullPath);
                        }
                    }
                    catch (URISyntaxException e)
                    {
                        // That's OK, don't treat the value as a relative path to the file
                    }
                    catch (XarFormatException e)
                    {
                        // That's OK, don't treat the value as a relative path to the file
                    }
                }
            }

            if (checkForDuplicates && existingProps.containsKey(ontologyEntryURI))
            {
                ObjectProperty existingProp = existingProps.get(ontologyEntryURI);
                Object existingValue = existingProp.value();
                Object newValue = objectProp.value();
                if ((existingValue != null && !existingValue.equals(newValue)) || (existingValue == null && newValue != null) )
                {
                    throw new XarFormatException("Property: " + ontologyEntryURI + " of object " + parentLSID + " already exists with value " + existingProp);
                }
            }
            // Set the child properties to an empty map to prevent ExperimentService.savePropertyCollection from having to query to retrieve child props.
            objectProp.setChildProperties(Collections.<String, ObjectProperty>emptyMap());
            result.put(ontologyEntryURI, objectProp);
        }

        for (PropertyObjectType xbPropObject : xbValues.getPropertyObjectArray())
        {
            PropertyObjectDeclarationType propObjDecl = xbPropObject.getPropertyObjectDeclaration();
            String ontologyEntryURI = trimString(propObjDecl.getOntologyEntryURI());
            if (ontologyEntryURI != null && ontologyEntryURI.indexOf("${") != -1)
            {
                ontologyEntryURI = LsidUtils.resolveLsidFromTemplate(ontologyEntryURI, _xarContext);
            }
            if (checkForDuplicates && existingProps.containsKey(ontologyEntryURI))
            {
                throw new XarFormatException("Duplicate nested property for ParentURI " + parentLSID + ", OntologyEntryURI = " + propObjDecl.getOntologyEntryURI());
            }

            String containerId = _container.getId();
            assert null != containerId;
            String uri = GUID.makeURN();
            ObjectProperty childProperty = new ObjectProperty(parentLSID, containerId, ontologyEntryURI, new IdentifiableBase(uri), propObjDecl.getName());
            setPropertyId(childProperty);
            childProperty.setChildProperties(readPropertyCollection(xbPropObject.getChildProperties(), uri, checkForDuplicates));
            result.put(ontologyEntryURI, childProperty);
        }

        return result;
    }

    private void loadPropertyCollection(PropertyCollectionType xbProps,
                                        String parentLSID,
                                        String ownerLSID,
                                        ObjectProperty additionalProperty) throws SQLException, XarFormatException
    {
        Map<String, ObjectProperty> propsToInsert = new HashMap<String, ObjectProperty>();
        if (xbProps != null)
        {
            propsToInsert.putAll(readPropertyCollection(xbProps, parentLSID, true));
        }
        if (additionalProperty != null)
        {
            propsToInsert.put(additionalProperty.getPropertyURI(), additionalProperty);
        }

        if (!propsToInsert.isEmpty())
        {
            ExperimentServiceImpl.get().savePropertyCollection(propsToInsert, ownerLSID, _container.getId(), false);
        }
    }

    private void loadExtendedMaterialType(MaterialBaseType xbMaterial,
                                          String materialLSID,
                                          ExperimentRun run)
    {
        String cpasTypeName = xbMaterial.getCpasType();

        if (null != cpasTypeName)
        {
            MaterialType extMaterial = (MaterialType) xbMaterial.changeType(MaterialType.type);
            loadMaterialWildcardProperties(extMaterial, materialLSID, run);
        }
    }

    private void loadExtendedDataType(DataBaseType xbData,
                                      String dataLSID,
                                      ExperimentRun run)
    {
        String cpasTypeName = trimString(xbData.getCpasType());

        if (null != cpasTypeName)
        {
            // todo here's where we dynamically load a Data derived type
            DataType extData = (DataType) xbData.changeType(DataType.type);
            loadDataWildcardProperties(extData, dataLSID, run);
        }
    }

    private void loadProtocol(ProtocolBaseType p) throws SQLException, ExperimentException
    {
        String protocolLSID = LsidUtils.resolveLsidFromTemplate(p.getAbout(), _xarContext, "Protocol");
        Protocol existingProtocol = ExperimentServiceImpl.get().getProtocol(protocolLSID);

        Protocol xarProtocol = readProtocol(p);

        Protocol protocol;

        if (existingProtocol != null)
        {
            List<IdentifiableEntity.Difference> diffs = existingProtocol.diff(xarProtocol);
            if (!diffs.isEmpty())
            {
                _log.error("The protocol specified in the file with LSID '" + protocolLSID + "' has " + diffs.size() + " differences from the protocol that has already been loaded");
                for (IdentifiableEntity.Difference diff : diffs)
                {
                    _log.error(diff.toString());
                }
                throw new XarFormatException("Protocol with LSID '" + protocolLSID + "' does not match existing protocol");
            }
            protocol = existingProtocol;
            _log.info("Protocol with LSID '" + protocolLSID + "' matches a protocol with the same LSID that has already been loaded.");
        }
        else
        {
            protocol = ExperimentServiceImpl.get().saveProtocol(getUser(), xarProtocol);
            _log.info("Finished loading Protocol with LSID '" + protocolLSID + "'");
        }

        _xarSource.addProtocol(new ExpProtocolImpl(protocol));
    }

    private void loadActionSet(ProtocolActionSetType actionSet) throws SQLException, XarFormatException
    {
        TableInfo tiAction = ExperimentServiceImpl.get().getTinfoProtocolAction();

        //Check that the parent is defined already
        String parentLSID = LsidUtils.resolveLsidFromTemplate(actionSet.getParentProtocolLSID(), _xarContext, "Protocol");
        ExpProtocol parentProtocol = _xarSource.getProtocol(parentLSID, "Parent");

        ProtocolActionType[] xActions = actionSet.getProtocolActionArray();

        ProtocolAction[] existingActions = ExperimentServiceImpl.get().getProtocolActions(parentProtocol.getRowId());
        boolean alreadyLoaded = existingActions.length != 0;
        if (alreadyLoaded && existingActions.length != xActions.length)
        {
            throw new XarFormatException("Protocol actions for protocol " + parentLSID + " do not match those that have " +
                    "already been loaded. The existing protocol has " + existingActions.length +
                    " actions but the file contains " + xActions.length + " actions.");
        }

        int priorSeq = -1;

        for (ProtocolActionType xAction : xActions)
        {
            String childLSID = LsidUtils.resolveLsidFromTemplate(xAction.getChildProtocolLSID(), _xarContext, "Protocol");

            int currentSeq = xAction.getActionSequence();
            if (currentSeq <= priorSeq)
            {
                throw new XarFormatException("Sequence number under parent protocol '" + parentLSID + "' not unique and ascending: " + currentSeq);
            }
            priorSeq = currentSeq;

            int parentProtocolRowId = parentProtocol.getRowId();
            int childProtocolRowId = _xarSource.getProtocol(childLSID, "ActionSet child").getRowId();

            ProtocolAction action = null;
            // Look for an existing action that matches
            for (ProtocolAction existingAction : existingActions)
            {
                if (existingAction.getChildProtocolId() == childProtocolRowId &&
                    existingAction.getParentProtocolId() == parentProtocolRowId &&
                    existingAction.getSequence() == currentSeq)
                {
                    action = existingAction;
                }
            }

            if (action == null)
            {
                if (alreadyLoaded)
                {
                    throw new XarFormatException("Protocol actions for protocol " + parentLSID + " do not match the ones " +
                            "that have already been loaded - no match found for action defined in file with child protocol " +
                            childLSID + " and action sequence " + currentSeq);
                }
                action = new ProtocolAction();
                action.setParentProtocolId(parentProtocolRowId);
                action.setChildProtocolId(childProtocolRowId);
                action.setSequence(currentSeq);
                action = Table.insert(getUser(), tiAction, action);
            }

            int actionRowId = action.getRowId();

            ProtocolActionType.PredecessorAction[] predecessors = xAction.getPredecessorActionArray();
            ProtocolActionPredecessor[] existingPredecessors = ExperimentServiceImpl.get().getProtocolActionPredecessors(parentLSID, childLSID);

            if (alreadyLoaded && predecessors.length != existingPredecessors.length)
            {
                throw new XarFormatException("Predecessors for child protocol " + childLSID + " do not match those " +
                        "that have already been loaded. The existing protocol has " + existingPredecessors.length +
                        " predecessors but the file contains " + predecessors.length + " predecessors.");
            }

            for (ProtocolActionType.PredecessorAction xPredecessor : predecessors)
            {
                int predecessorActionSequence = xPredecessor.getActionSequenceRef();
                ProtocolActionStepDetail predecessorRow = ExperimentServiceImpl.get().getProtocolActionStepDetail(parentLSID, predecessorActionSequence);
                if (predecessorRow == null)
                {
                    throw new XarFormatException("Protocol Not Found for Action Sequence =" + predecessorActionSequence + " in parent protocol " + parentLSID);
                }
                int predecessorRowId = predecessorRow.getActionId();

                ProtocolActionPredecessor predecessor = null;
                for (ProtocolActionPredecessor existingPredecessor : existingPredecessors)
                {
                    if (predecessorActionSequence == existingPredecessor.getPredecessorSequence())
                    {
                        predecessor = existingPredecessor;
                    }
                }

                if (predecessor == null)
                {
                    if (alreadyLoaded)
                    {
                        throw new XarFormatException("Predecessors for child protocol " + childLSID + " do not match the ones " +
                                "that have already been loaded - no match found for predecessor defined in file with predecessor " +
                                "sequence " + predecessorActionSequence);
                    }

                    ExperimentService.get().insertProtocolPredecessor(getUser(), actionRowId, predecessorRowId);
                }
            }
        }
    }

    private String trimString(String s)
    {
        return s == null ? null : s.trim();
    }

    private Protocol readProtocol(ProtocolBaseType p) throws XarFormatException, SQLException
    {
        Protocol protocol = new Protocol();
        protocol.setLSID(LsidUtils.resolveLsidFromTemplate(p.getAbout(), _xarContext, "Protocol"));
        protocol.setName(trimString(p.getName()));
        protocol.setProtocolDescription(trimString(p.getProtocolDescription()));
        String applicationType = trimString(p.getApplicationType());
        if (applicationType == null)
        {
            applicationType = "ProtocolApplication";
        }
        protocol.setApplicationType(applicationType);

        if ((!p.isSetMaxInputMaterialPerInstance()) || p.isNilMaxInputMaterialPerInstance())
            protocol.setMaxInputMaterialPerInstance(null);
        else
            protocol.setMaxInputMaterialPerInstance(new Integer(p.getMaxInputMaterialPerInstance()));

        if ((!p.isSetMaxInputDataPerInstance()) || p.isNilMaxInputDataPerInstance())
            protocol.setMaxInputDataPerInstance(null);
        else
            protocol.setMaxInputDataPerInstance(new Integer(p.getMaxInputDataPerInstance()));

        if ((!p.isSetOutputMaterialPerInstance()) || p.isNilOutputMaterialPerInstance())
            protocol.setOutputMaterialPerInstance(null);
        else
            protocol.setOutputMaterialPerInstance(new Integer(p.getOutputMaterialPerInstance()));

        if ((!p.isSetOutputDataPerInstance()) || p.isNilOutputDataPerInstance())
            protocol.setOutputDataPerInstance(null);
        else
            protocol.setOutputDataPerInstance(new Integer(p.getOutputDataPerInstance()));

        String materialType = trimString(p.getOutputMaterialType());
        protocol.setOutputMaterialType(materialType == null ? "Material" : materialType);
        String dataType = trimString(p.getOutputDataType());
        protocol.setOutputDataType(dataType == null ? "Data" : dataType);

        protocol.setInstrument(trimString(p.getInstrument()));
        protocol.setSoftware(trimString(p.getSoftware()));
        if (null != p.getContact())
            protocol.setContactId(p.getContact().getContactId());

        protocol.setContainer(_container.getId());

        // Protocol parameters
        List<ProtocolParameter> params = Collections.emptyList();
        SimpleValueCollectionType xbParams = p.getParameterDeclarations();
        if (null != xbParams)
        {
            params = readProtocolParameters(xbParams);
        }
        protocol.storeProtocolParameters(params);

        Map<String, ObjectProperty> properties = new HashMap<String, ObjectProperty>();

        // Protocol properties
        PropertyCollectionType xbProps = p.getProperties();
        if (null != xbProps)
        {
            // now save the properties
            properties = readPropertyCollection(xbProps, protocol.getLSID(), false);
        }

        if (p.getContact() != null)
        {
            ObjectProperty contactProperty = readContact(p.getContact(), protocol.getLSID());
            if (contactProperty != null)
            {
                properties.put(contactProperty.getPropertyURI(), contactProperty);
            }
        }
        protocol.storeObjectProperties(properties);

        return protocol;
    }

    private Map<String, Object> getSimplePropertiesMap(PropertyCollectionType xbProps)
    {
        Map<String, Object> mSimpleProperties = new HashMap<String, Object>();
        SimpleValueType[] aSVals = xbProps.getSimpleValArray();
        for (SimpleValueType sVal : aSVals)
        {
            String key = sVal.getOntologyEntryURI();
            Object val;
            SimpleTypeNames.Enum valType = sVal.getValueType();
            try
            {
                switch (valType.intValue())
                {
                    case (SimpleTypeNames.INT_INTEGER):
                        val = new Integer(sVal.getStringValue());
                        break;
                    case (SimpleTypeNames.INT_DOUBLE):
                        val = new Double(sVal.getStringValue());
                        break;
                    case (SimpleTypeNames.INT_DATE_TIME):
                        val = new Date(DateUtil.parseDateTime(sVal.getStringValue()));
                        break;
                    default:
                        val = sVal.getStringValue();
                }
            }
            catch (ConversionException e)
            {
                val = sVal.getStringValue();
                _log.error("Failed to parse value " + val
                        + ":   Declared as type " + valType + " ; returned as string instead", e);
            }
            mSimpleProperties.put(key, val);
        }
        return mSimpleProperties;
    }

    private void loadMaterialWildcardProperties(MaterialType xbOriginal,
                                                String parentLSID,
                                                ExperimentRun run)
    {
        PropertyCollectionType xbProps = xbOriginal.getProperties();
        if (null == xbProps)
            xbProps = xbOriginal.addNewProperties();
        loadWildcardProperties(xbOriginal, parentLSID, run);
        xbOriginal.setProperties(xbProps);
    }

    private void loadDataWildcardProperties(DataType xbOriginal,
                                            String parentLSID,
                                            ExperimentRun run)
    {
        PropertyCollectionType xbProps = xbOriginal.getProperties();
        if (null == xbProps)
            xbProps = xbOriginal.addNewProperties();
        loadWildcardProperties(xbOriginal, parentLSID, run);
        xbOriginal.setProperties(xbProps);
    }

    private void loadWildcardProperties(XmlObject xObj,
                                        String parentLSID,
                                        ExperimentRun run)
    {
        XmlObject xElem;
        String key = null;

        XmlCursor c = xObj.newCursor();
        XmlCursor cTest;
        Map m = new HashMap();
        c.getAllNamespaces(m);
        String nsObject = c.getName().getNamespaceURI();

        c.selectPath("./*");

        while (c.hasNextSelection())
        {
            try
            {
                c.toNextSelection();
                String nsChild = c.getName().getNamespaceURI();

                // skip elements in the object's own namespace
                if (nsChild.equals(nsObject))
                    continue;

                key = c.getName().getLocalPart();
                xElem = c.getObject();
                cTest = c.newCursor();
                String stringValue = null;
                String propertyURI = nsChild + "." + key;

                PropertyType propertyType = PropertyType.XML_TEXT;
                if (!xElem.isNil())
                {
                    stringValue = c.xmlText();

                    // clone the cursor and check for complex elements
                    // if none, also load value as string for usability.
                    if (!cTest.toFirstChild() && !cTest.toFirstAttribute())
                    {
                        propertyType = PropertyType.STRING;
                        stringValue = trimString(c.getTextValue());
                    }
                }

                OntologyManager.insertProperty(_container.getId(), new ObjectProperty(parentLSID, propertyURI, stringValue, propertyType), run.getLSID());
            }
            catch (Exception e)
            {
                _log.debug("Skipped element " + key + " exception " + e.getMessage(), e);
            }

        }
        c.dispose();
    }

    private static class DeferredDataLoad
    {
        private final ExpData _data;
        private final ExpRun _run;

        public DeferredDataLoad(ExpData data, ExpRun run)
        {
            _data = data;
            _run = run;
        }

        public ExpData getData()
        {
            return _data;
        }

        public ExpRun getRun()
        {
            return _run;
        }
    }
}
