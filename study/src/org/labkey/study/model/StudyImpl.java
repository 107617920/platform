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

package org.labkey.study.model;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.MvUtil;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Site;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.samples.settings.RepositorySettings;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:28:32 AM
 */
public class StudyImpl extends ExtensibleStudyEntity<StudyImpl> implements Study
{
    private static final String DOMAIN_URI_PREFIX = "Study";
    public static final DomainInfo DOMAIN_INFO = new StudyDomainInfo(DOMAIN_URI_PREFIX, true);

    private String _label;
    private TimepointType _timepointType;
    private Date _startDate;
    private SecurityType _securityType = SecurityType.BASIC_READ; // Default value. Not allowed to be null
    private String _participantCohortProperty;
    private Integer _participantCohortDataSetId;
    private boolean _manualCohortAssignment;
    private String _lsid;
    private Integer _defaultPipelineQCState;
    private Integer _defaultAssayQCState;
    private Integer _defaultDirectEntryQCState;
    private boolean _showPrivateDataByDefault = true;
    private boolean _blankQCStatePublic = false;
    private boolean _isAllowReload;
    private Integer _reloadInterval;
    private Date _lastReload;
    private Integer _reloadUser;
    private boolean _advancedCohorts;
    private Integer _participantCommentDataSetId;
    private String _participantCommentProperty;
    private Integer _participantVisitCommentDataSetId;
    private String _participantVisitCommentProperty;
    private String _subjectNounSingular;
    private String _subjectNounPlural;
    private String _subjectColumnName;
    private String _description;
    private String _descriptionRendererType = WikiRendererType.TEXT_WITH_LINKS.name();
    private String _protocolDocumentEntityId;
    private String _sourceStudyContainerId;
    private String _investigator;
    private String _grant;

    public StudyImpl()
    {
    }

    public StudyImpl(Container container, String label)
    {
        super(container);
        _label = label;
        _entityId = GUID.makeGUID();
    }

    @Override
    public String toString()
    {
        return getDisplayString();
    }

    @Override
    public SecurableResource getParentResource()
    {
        //overriden to return the container
        //all other study entities return the study,
        //but the study's parent is the container
        return getContainer();
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        List<DataSetDefinition> datasets = getDataSets();
        ArrayList<SecurableResource> readableDatasets = new ArrayList<SecurableResource>(datasets.size());
        for (DataSetDefinition ds: datasets)
            if (ds.canRead(user))
                readableDatasets.add(ds);
        
        return readableDatasets;
    }

    @NotNull
    @Override
    public String getResourceDescription()
    {
        return "The study " + _label;
    }

    public String getLabel()
    {
        return _label;
    }


    public void setLabel(String label)
    {
        verifyMutability();
        _label = label;
    }


    public VisitImpl[] getVisits(Visit.Order order)
    {
        return StudyManager.getInstance().getVisits(this, order);
    }


    public DataSetDefinition getDataSet(int id)
    {
        return StudyManager.getInstance().getDataSetDefinition(this, id);
    }


    public List<DataSetDefinition> getDataSets()
    {
        return Arrays.asList(StudyManager.getInstance().getDataSetDefinitions(this));
    }

    public PropertyDescriptor[] getSharedProperties()
    {
        return StudyManager.getInstance().getSharedProperties(this);
    }

    public SampleRequestActor[] getSampleRequestActors() throws SQLException
    {
        return SampleManager.getInstance().getRequirementsProvider().getActors(getContainer());
    }

    public Set<Integer> getSampleRequestActorsInUse() throws SQLException
    {
        Collection<SampleRequestActor> actors = SampleManager.getInstance().getRequirementsProvider().getActorsInUse(getContainer());
        Set<Integer> ids = new HashSet<Integer>();
        for (SampleRequestActor actor : actors)
            ids.add(actor.getRowId());
        return ids;
    }

    public SiteImpl[] getSites()
    {
        return StudyManager.getInstance().getSites(getContainer());
    }

    public CohortImpl[] getCohorts(User user)
    {
        return StudyManager.getInstance().getCohorts(getContainer(), user);
    }

    public SampleRequestStatus[] getSampleRequestStatuses(User user) throws SQLException
    {
        return SampleManager.getInstance().getRequestStatuses(getContainer(), user);
    }

    public Set<Integer> getSampleRequestStatusesInUse() throws SQLException
    {
        return SampleManager.getInstance().getRequestStatusIdsInUse(getContainer());
    }

    public RepositorySettings getRepositorySettings() throws SQLException
    {
        return SampleManager.getInstance().getRepositorySettings(getContainer());
    }
    
    public Object getPrimaryKey()
    {
        return getContainer();
    }

    public int getRowId()
    {
        return -1;
    }

    @Override
    public void savePolicy(MutableSecurityPolicy policy)
    {
        super.savePolicy(policy);
        StudyManager.getInstance().scrubDatasetAcls(this, policy);
    }

    @Override
    protected boolean supportsPolicyUpdate()
    {
        return true;
    }

    public TimepointType getTimepointType()
    {
        return _timepointType;
    }

    public void setTimepointType(TimepointType timepointType)
    {
        verifyMutability();
        _timepointType = timepointType;
    }

    public SecurityType getSecurityType()
    {
        return _securityType;
    }

    public void setSecurityType(SecurityType securityType)
    {
        verifyMutability();
        if (securityType == null)
            throw new IllegalArgumentException("securityType cannot be null");
        _securityType = securityType;
    }

    public Date getStartDate()
    {
        return _startDate;
    }

    public void setStartDate(Date startDate)
    {
        verifyMutability();
        _startDate = startDate;
    }

    public String getParticipantCohortProperty()
    {
        return _participantCohortProperty;
    }

    public void setParticipantCohortProperty(String participantCohortProperty)
    {
        _participantCohortProperty = participantCohortProperty;
    }

    public Integer getParticipantCohortDataSetId()
    {
        return _participantCohortDataSetId;
    }

    public void setParticipantCohortDataSetId(Integer participantCohortDataSetId)
    {
        _participantCohortDataSetId = participantCohortDataSetId;
    }

    public boolean isManualCohortAssignment()
    {
        return _manualCohortAssignment;
    }

    public void setManualCohortAssignment(boolean manualCohortAssignment)
    {
        _manualCohortAssignment = manualCohortAssignment;
    }

    @Override
    public String getDomainURIPrefix()
    {
        return DOMAIN_URI_PREFIX;
    }

    @Override
    protected boolean getUseSharedProjectDomain()
    {
        return true;
    }

    public void initLsid()
    {
        Lsid lsid = new Lsid(getDomainURIPrefix(), "Folder-" + getContainer().getRowId(), String.valueOf(getContainer().getRowId()));
        setLsid(lsid.toString());
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        verifyMutability();
        this._lsid = lsid;
    }

    public Integer getDefaultPipelineQCState()
    {
        return _defaultPipelineQCState;
    }

    public void setDefaultPipelineQCState(Integer defaultPipelineQCState)
    {
        _defaultPipelineQCState = defaultPipelineQCState;
    }

    public Integer getDefaultAssayQCState()
    {
        return _defaultAssayQCState;
    }

    public void setDefaultAssayQCState(Integer defaultAssayQCState)
    {
        _defaultAssayQCState = defaultAssayQCState;
    }

    public Integer getDefaultDirectEntryQCState()
    {
        return _defaultDirectEntryQCState;
    }

    public void setDefaultDirectEntryQCState(Integer defaultDirectEntryQCState)
    {
        _defaultDirectEntryQCState = defaultDirectEntryQCState;
    }

    /** Used to determine which QC states should be shown when viewing datasets */ 
    public boolean isShowPrivateDataByDefault()
    {
        return _showPrivateDataByDefault;
    }

    public void setShowPrivateDataByDefault(boolean showPrivateDataByDefault)
    {
        _showPrivateDataByDefault = showPrivateDataByDefault;
    }

    public boolean isBlankQCStatePublic()
    {
        return _blankQCStatePublic;
    }

    public void setBlankQCStatePublic(boolean blankQCStatePublic)
    {
        _blankQCStatePublic = blankQCStatePublic;
    }

    /** Used to determine whether records without an assigned QC state are considered 'public' data */

    public int getNumExtendedProperties(User user)
    {
        StudyQuerySchema schema = new StudyQuerySchema(this, user, true);
        String domainURI = DOMAIN_INFO.getDomainURI(schema.getContainer());
        Domain domain = PropertyService.get().getDomain(schema.getContainer(), domainURI);

        if (domain == null)
            return 0;

        return domain.getProperties().length;
    }

    public boolean isAllowReload()
    {
        return _isAllowReload;
    }

    public void setAllowReload(boolean allowReload)
    {
        _isAllowReload = allowReload;
    }

    // Study reload interval, specified in seconds
    public Integer getReloadInterval()
    {
        return _reloadInterval;
    }

    public void setReloadInterval(Integer reloadInterval)
    {
        _reloadInterval = reloadInterval;
    }

    public Date getLastReload()
    {
        return _lastReload;
    }

    public void setLastReload(Date lastReload)
    {
        _lastReload = lastReload;
    }

    public Integer getReloadUser()
    {
        return _reloadUser;
    }

    public void setReloadUser(Integer reloadUser)
    {
        _reloadUser = reloadUser;
    }

    public boolean isAdvancedCohorts()
    {
        return _advancedCohorts;
    }

    public void setAdvancedCohorts(boolean advancedCohorts)
    {
        _advancedCohorts = advancedCohorts;
    }

    public Integer getParticipantCommentDataSetId()
    {
        return _participantCommentDataSetId;
    }

    public void setParticipantCommentDataSetId(Integer participantCommentDataSetId)
    {
        _participantCommentDataSetId = participantCommentDataSetId;
    }

    public String getParticipantCommentProperty()
    {
        return _participantCommentProperty;
    }

    public void setParticipantCommentProperty(String participantCommentProperty)
    {
        _participantCommentProperty = participantCommentProperty;
    }

    public Integer getParticipantVisitCommentDataSetId()
    {
        return _participantVisitCommentDataSetId;
    }

    public void setParticipantVisitCommentDataSetId(Integer participantVisitCommentDataSetId)
    {
        _participantVisitCommentDataSetId = participantVisitCommentDataSetId;
    }

    public String getParticipantVisitCommentProperty()
    {
        return _participantVisitCommentProperty;
    }

    public void setParticipantVisitCommentProperty(String participantVisitCommentProperty)
    {
        _participantVisitCommentProperty = participantVisitCommentProperty;
    }

    public String getSubjectNounSingular()
    {
        return _subjectNounSingular;
    }

    public void setSubjectNounSingular(String subjectNounSingular)
    {
        _subjectNounSingular = subjectNounSingular;
    }

    public String getSubjectNounPlural()
    {
        return _subjectNounPlural;
    }

    public void setSubjectNounPlural(String subjectNounPlural)
    {
        _subjectNounPlural = subjectNounPlural;
    }

    public String getSubjectColumnName()
    {
        return _subjectColumnName;
    }

    public void setSubjectColumnName(String subjectColumnName)
    {
        _subjectColumnName = ColumnInfo.legalNameFromName(subjectColumnName);
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getDescriptionRendererType()
    {
        return _descriptionRendererType;
    }

    public void setDescriptionRendererType(String descriptionRendererType)
    {
        _descriptionRendererType = descriptionRendererType;
    }

    public String getDescriptionHtml()
    {
        String description = getDescription();

        if (description == null || description.length() == 0)
        {
            long count = StudyManager.getInstance().getParticipantCount(this);;
            String subjectNoun = (count == 1 ? this.getSubjectNounSingular() : this.getSubjectNounPlural());
            TimepointType timepointType = getTimepointType();
            return PageFlowUtil.filter(getLabel() + " tracks data in ") + "<a href=\"" + new ActionURL(StudyController.DatasetsAction.class, getContainer()) + "\">" + getDataSets().size() + " datasets</a>" + PageFlowUtil.filter(" over " + getVisits(Visit.Order.DISPLAY).length + " " + (timepointType.isVisitBased() ? "visits" : "time points") +
                ". Data is present for " + count + " " + PageFlowUtil.filter(subjectNoun) + ".");
        }
        else
        {
            WikiService ws = ServiceRegistry.get().getService(WikiService.class);

            if (null != ws)
            {
                return ws.getFormattedHtml(getDescriptionWikiRendererType(), description);
            }
            else
            {
                return PageFlowUtil.filter(description, true);
            }
        }
    }

    public WikiRendererType getDescriptionWikiRendererType()
    {
        return WikiRendererType.valueOf(_descriptionRendererType);
    }

    public String getProtocolDocumentEntityId()
    {
        return _protocolDocumentEntityId;
    }

    public void setProtocolDocumentEntityId(String protocolDocumentEntityId)
    {
        _protocolDocumentEntityId = protocolDocumentEntityId;
    }

    public void attachProtocolDocument(List<AttachmentFile> files , User user)  throws SQLException, IOException
    {
        AttachmentService.get().addAttachments(getProtocolDocumentAttachmentParent(), files, user);
    }

    public String getInvestigator()
    {
        return _investigator;
    }

    public void setInvestigator(String investigator)
    {
        _investigator = investigator;
    }

    public String getGrant()
    {
        return _grant;
    }

    public void setGrant(String grant)
    {
        _grant = grant;
    }

    public List<Attachment> getProtocolDocuments()
    {
        return new ArrayList<Attachment>(AttachmentService.get().getAttachments(getProtocolDocumentAttachmentParent()));
    }

    public String getSourceStudyContainerId()
    {
        return _sourceStudyContainerId;
    }

    public void setSourceStudyContainerId(String sourceStudyContainerId)
    {
        _sourceStudyContainerId = sourceStudyContainerId;
    }

    @Override
    public boolean isAncillaryStudy()
    {
        return getSourceStudy() != null;
    }

    @Nullable
    public StudyImpl getSourceStudy()
    {
        if (getSourceStudyContainerId() == null)
            return null;
        Container sourceContainer = ContainerManager.getForId(getSourceStudyContainerId());
        if (sourceContainer == null)
            return null;
        return StudyManager.getInstance().getStudy(sourceContainer);
    }

    public void removeProtocolDocument(String name, User user)
    {
        AttachmentService.get().deleteAttachment(getProtocolDocumentAttachmentParent(), name, user);
    }

    public static class ProtocolDocumentAttachmentParent extends AttachmentParentEntity
    {
        public ProtocolDocumentAttachmentParent(Container c, String entityId)
        {
            setContainer(c.getId());
            setEntityId(null==entityId?null:entityId);
        }
    }

    public AttachmentParent getProtocolDocumentAttachmentParent()
    {
        return new ProtocolDocumentAttachmentParent(getContainer(), getProtocolDocumentEntityId());
    }

    @Override
    public String getSearchDisplayTitle()
    {
        return "Study -- " + getLabel();
    }

    @Override
    public String getSearchKeywords()
    {
        StringBuilder sb = new StringBuilder();

        appendKeyword(sb, getLabel());
        appendKeyword(sb, getDescription());
        appendKeyword(sb, getSubjectNounSingular());
        appendKeyword(sb, getContainer().getName());
        appendKeyword(sb, getDescription());

        return sb.toString();
    }

    @Override
    public String getSearchBody()
    {
        Container c = getContainer();
        StringBuilder sb = new StringBuilder();

        if (c.isProject())
            appendKeyword(sb, "Study Project " + c.getName());
        else
            appendKeyword(sb, "Study Folder " + c.getName() + " in Project " + c.getProject().getName());

        appendKeyword(sb, getLabel());
        appendKeyword(sb, getDescription());
        appendKeyword(sb, getSubjectNounSingular());

        for (DataSetDefinition dataset : getDataSets())
        {
            appendKeyword(sb, dataset.getName());
            appendKeyword(sb, dataset.getLabel());
            appendKeyword(sb, dataset.getDescription());
        }

        /*
           Per Sarah, leave cohort labels out for now... to re-enable, uncomment and special case
           the search user in getCohorts()
        for (Cohort cohort : getCohorts(User.getSearchUser()))
            appendKeyword(sb, cohort.getLabel());
        */

        for (Site site : getSites())
            appendKeyword(sb, site.getLabel());

        return sb.toString();
    }

    private void appendKeyword(StringBuilder sb, String s)
    {
        if (!StringUtils.isBlank(s))
        {
            sb.append(s);
            sb.append(" ");
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StudyImpl study = (StudyImpl) o;

        return !(getContainer() != null ? !getContainer().equals(study.getContainer()) : study.getContainer() != null);
    }

    @Override
    public int hashCode()
    {
        return getContainer() != null ? getContainer().hashCode() : 0;
    }

    public static class ProtocolDocumentTestCase extends Assert
    {
        Study _testStudy = null;
        TestContext _context = null;

//        @BeforeClass
        public void createStudy() throws SQLException
        {
            _context = TestContext.get();
            Container junit = JunitUtil.getTestContainer();

            String name = GUID.makeHash();
            Container c = ContainerManager.createContainer(junit, name);
            StudyImpl s = new StudyImpl(c, "Junit Study");
            s.setTimepointType(TimepointType.DATE);
            s.setStartDate(new Date(DateUtil.parseDateTime("2001-01-01")));
            s.setSubjectColumnName("SubjectID");
            s.setSubjectNounPlural("Subjects");
            s.setSubjectNounSingular("Subject");
            s.setSecurityType(SecurityType.BASIC_WRITE);
            s.setStartDate(new Date(DateUtil.parseDateTime("1 Jan 2000")));
            _testStudy = StudyManager.getInstance().createStudy(_context.getUser(), s);

            MvUtil.assignMvIndicators(c,
                    new String[]{"X", "Y", "Z"},
                    new String[]{"XXX", "YYY", "ZZZ"});

        }

//        @AfterClass
        public void tearDown()
        {
            if (null != _testStudy)
            {
                ContainerManager.delete(_testStudy.getContainer(), _context.getUser());
            }
        }

        @Test
        public void test() throws Throwable
        {
            try
            {
                createStudy();
                _testAttachProtocolDoc(_testStudy);
                _testDeleteProtocolDoc(_testStudy);
            }
            catch (Throwable t)
            {
                throw t;
            }
            finally
            {
                tearDown();
            }
        }

        public void _testAttachProtocolDoc(Study testStudy) throws SQLException, IOException
        {
            List<Attachment> attachedFiles = testStudy.getProtocolDocuments();
            assertEquals("Expected 0 attached documents", 0, attachedFiles.size());

            AttachmentFile file = new FileAttachmentFile(new File(AppProps.getInstance().getProjectRoot() + "/sampledata/study/Protocol.txt"));
            testStudy.attachProtocolDocument(Collections.singletonList(file), _context.getUser());
            attachedFiles = testStudy.getProtocolDocuments();
            assertEquals("Expected 1 attached document", 1, attachedFiles.size());
            assertTrue("Expected filename to be \"Protocol.txt\", but it was \"" + attachedFiles.get(0).getName() + "\"", attachedFiles.get(0).getName().equals("Protocol.txt"));
        }

        public void _testDeleteProtocolDoc(Study testStudy) throws SQLException, IOException
        {
            List<Attachment> attachedFiles = testStudy.getProtocolDocuments();
            assertEquals("Expected 1 attached document", 1, attachedFiles.size());

            testStudy.removeProtocolDocument("Protocol.txt", _context.getUser());
            attachedFiles = testStudy.getProtocolDocuments();
            assertEquals("Expected 0 attached documents", 0, attachedFiles.size());
        }
    }
}
