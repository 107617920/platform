/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.study;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections15.comparators.ComparableComparator;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.query.*;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyCachable;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.importer.RequestabilityManager;
import org.labkey.study.model.*;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.requirements.RequirementProvider;
import org.labkey.study.requirements.SpecimenRequestRequirementProvider;
import org.labkey.study.samples.SpecimenCommentAuditViewFactory;
import org.labkey.study.samples.report.SpecimenCountSummary;
import org.labkey.study.samples.settings.DisplaySettings;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.samples.settings.RequestNotificationSettings;
import org.labkey.study.samples.settings.StatusSettings;
import org.labkey.study.security.permissions.ManageRequestsPermission;
import org.labkey.study.security.permissions.RequestSpecimensPermission;

import javax.servlet.ServletException;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SampleManager implements ContainerManager.ContainerListener
{
    private final static SampleManager _instance = new SampleManager();

    private final QueryHelper<SampleRequestEvent> _requestEventHelper;
    private final QueryHelper<Specimen> _specimenDetailHelper;
    private final QueryHelper<SpecimenEvent> _specimenEventHelper;
    private final QueryHelper<AdditiveType> _additiveHelper;
    private final QueryHelper<DerivativeType> _derivativeHelper;
    private final QueryHelper<PrimaryType> _primaryTypeHelper;
    private final QueryHelper<SampleRequest> _requestHelper;
    private final QueryHelper<SampleRequestStatus> _requestStatusHelper;
    private final RequirementProvider<SampleRequestRequirement, SampleRequestActor> _requirementProvider =
            new SpecimenRequestRequirementProvider();
    private final Map<String, Resource> _moduleExtendedSpecimenRequestViews = new ConcurrentHashMap<>();

    private SampleManager()
    {
        _primaryTypeHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoPrimaryType();
            }
        }, PrimaryType.class);
        _derivativeHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoDerivativeType();
            }
        }, DerivativeType.class);
        _additiveHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoAdditiveType();
            }
        }, AdditiveType.class);

        _requestEventHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoSampleRequestEvent();
            }
        }, SampleRequestEvent.class);
        _specimenDetailHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoSpecimenDetail();
            }
        }, Specimen.class);
        _specimenEventHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoSpecimenEvent();
            }
        }, SpecimenEvent.class);
        _requestHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoSampleRequest();
            }
        }, SampleRequest.class);
        _requestStatusHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoSampleRequestStatus();
            }
        }, SampleRequestStatus.class);

        initGroupedValueAllowedColumnMap();

        ContainerManager.addContainerListener(this);
    }

    public static SampleManager getInstance()
    {
        return _instance;
    }

    public boolean isSpecimensEmpty(Container container)
    {
        return _specimenDetailHelper.get(container).isEmpty();
    }

    public List<Specimen> getSpecimens(Container container, String participantId, Double visit)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addClause(new SimpleFilter.SQLClause("LOWER(ptid) = LOWER(?)", new Object[] {participantId}, FieldKey.fromParts("ptid")));
        filter.addCondition("VisitValue", visit);
        return _specimenDetailHelper.get(container, filter);
    }

    public RequirementProvider<SampleRequestRequirement, SampleRequestActor> getRequirementsProvider()
    {
        return _requirementProvider;
    }

    @Override
    public void containerCreated(Container c, User user)
    {
        clearCaches(c);
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        clearCaches(c);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
        clearCaches(c);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        clearCaches((Container)evt.getSource());
    }

    public Specimen getSpecimen(Container container, int rowId)
    {
        return _specimenDetailHelper.get(container, rowId);
    }

    /** Looks for any specimens that have the given id as a globalUniqueId  */
    public Specimen getSpecimen(Container container, String globalUniqueId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addClause(new SimpleFilter.SQLClause("LOWER(GlobalUniqueId) = LOWER(?)", new Object[] { globalUniqueId }));
        List<Specimen> matches = _specimenDetailHelper.get(container, filter);
        if (matches == null || matches.isEmpty())
            return null;
        if (matches.size() > 1)
        {
            // we apparently have two specimens with IDs that differ only in case; do a case sensitive check
            // here to find the right one:
            for (Specimen specimen : matches)
            {
                if (specimen.getGlobalUniqueId().equals(globalUniqueId))
                    return specimen;
            }
            throw new IllegalStateException("Expected at least one vial to exactly match the specified global unique ID: " + globalUniqueId);
        }
        else
            return matches.get(0);
    }

    public List<Specimen> getSpecimens(Container container, String participantId, Date date)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addClause(new SimpleFilter.SQLClause("LOWER(ptid) = LOWER(?)", new Object[] {participantId}, FieldKey.fromParts("ptid")));
        Calendar endCal = DateUtil.newCalendar(date.getTime());
        endCal.add(Calendar.DATE, 1);
        filter.addClause(new SimpleFilter.SQLClause("DrawTimestamp >= ? AND DrawTimestamp < ?", new Object[] {date, endCal.getTime()}));
        return _specimenDetailHelper.get(container, filter);
    }

    public List<SpecimenEvent> getSpecimenEvents(Specimen sample)
    {
        SimpleFilter filter = new SimpleFilter("VialId", sample.getRowId());
        return _specimenEventHelper.get(sample.getContainer(), filter);
    }

    public List<SpecimenEvent> getSpecimenEvents(List<Specimen> samples, boolean includeObsolete)
    {
        if (samples == null || samples.size() == 0)
            return Collections.emptyList();
        Collection<Integer> vialIds = new HashSet<>();
        Container container = null;
        for (Specimen sample : samples)
        {
            vialIds.add(sample.getRowId());
            if (container == null)
                container = sample.getContainer();
            else if (!container.equals(sample.getContainer()))
                throw new IllegalArgumentException("All specimens must be from the same container");
        }
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromString("VialId"), vialIds);
        if (!includeObsolete)
            filter.addCondition(FieldKey.fromString("Obsolete"), false);
        return _specimenEventHelper.get(container, filter);
    }

    private static class SpecimenEventDateComparator implements Comparator<SpecimenEvent>
    {
        private Date convertToDate(String dateString)
        {
            if (dateString == null)
                return null;
            try
            {
                return DateUtil.parseDateTime(dateString, "yyyy-MM-dd");
            }
            catch (ParseException e)
            {
                return null;
            }
        }

        private Date getAnyDate(SpecimenEvent event)
        {
            if (event.getLabReceiptDate() != null)
                return event.getLabReceiptDate();
            else
            {
                Date storageDate = convertToDate(event.getStorageDate());
                if (storageDate != null)
                    return storageDate;
                else
                    return event.getShipDate();
            }
        }

        private int getTieBreakValue(SpecimenEvent event)
        {
            // our events have the same dates; in this case, we have to consider
            // the date type; a shipping date always comes after a storage date,
            // and a storage date always comes after a receipt date.
            if (event.getLabReceiptDate() != null)
                return 1;
            else if (convertToDate(event.getStorageDate()) != null)
                return 2;
            else if (event.getShipDate() != null)
                return 3;
            throw new IllegalStateException("Can only tiebreak events with at least one date present.");
        }

        public int compare(SpecimenEvent event1, SpecimenEvent event2)
        {
            // we use any date in the event, since we assume that no two events can have
            // overlapping date ranges:
            Date date1 = getAnyDate(event1);
            Date date2 = getAnyDate(event2);
            if (date1 == null && date2 == null)
                return 0;
            if (date1 == null)
                return -1;
            if (date2 == null)
                return 1;
            Long ms1 = date1.getTime();
            Long ms2 = date2.getTime();
            int comp = ms1.compareTo(ms2);
            if (comp == 0)
                return getTieBreakValue(event2) - getTieBreakValue(event1);
            else
                return comp;
        }
    }

    public List<SpecimenEvent> getDateOrderedEventList(Specimen specimen)
    {
        List<SpecimenEvent> eventList = new ArrayList<>();
        List<SpecimenEvent> events = getSpecimenEvents(specimen);
        if (events == null || events.isEmpty())
            return eventList;
        eventList.addAll(events);
        Collections.sort(eventList, new SpecimenEventDateComparator());
        return eventList;
    }

    public Map<Specimen, List<SpecimenEvent>> getDateOrderedEventLists(List<Specimen> specimens, boolean includeObsolete)
    {
        List<SpecimenEvent> allEvents = getSpecimenEvents(specimens, includeObsolete);
        Map<Integer, List<SpecimenEvent>> vialIdToEvents = new HashMap<>();
        for (SpecimenEvent event : allEvents)
        {
            List<SpecimenEvent> vialEvents = vialIdToEvents.get(event.getVialId());
            if (vialEvents == null)
            {
                vialEvents = new ArrayList<>();
                vialIdToEvents.put(event.getVialId(), vialEvents);
            }
            vialEvents.add(event);
        }

        Map<Specimen, List<SpecimenEvent>> results = new HashMap<>();
        for (Specimen specimen : specimens)
        {
            List<SpecimenEvent> events = vialIdToEvents.get(specimen.getRowId());
            if (events != null && events.size() > 0)
                Collections.sort(events, new SpecimenEventDateComparator());
            else
                events = Collections.EMPTY_LIST;
            results.put(specimen, events);
        }
        return results;
    }


    public LocationImpl getCurrentLocation(Specimen specimen)
    {
        Integer locationId = getCurrentLocationId(specimen);
        if (locationId != null)
            return StudyManager.getInstance().getLocation(specimen.getContainer(), locationId.intValue());
        return null;
    }

    public Integer getCurrentLocationId(Specimen specimen)
    {
        List<SpecimenEvent> events = getDateOrderedEventList(specimen);
        return getCurrentLocationId(events);
    }

    public Integer getCurrentLocationId(List<SpecimenEvent> dateOrderedEvents)
    {
        if (!dateOrderedEvents.isEmpty())
        {
            SpecimenEvent lastEvent = dateOrderedEvents.get(dateOrderedEvents.size() - 1);

            if (lastEvent.getShipDate() == null &&
                    (lastEvent.getShipBatchNumber() == null || lastEvent.getShipBatchNumber().intValue() == 0) &&
                    (lastEvent.getShipFlag() == null || lastEvent.getShipFlag().intValue() == 0))
            {
                return lastEvent.getLabId();
            }
        }
        return null;
    }

    private boolean skipAsProcessingLocation(SpecimenEvent event)
    {
        boolean allNullDates = event.getLabReceiptDate() == null && event.getStorageDate() == null && event.getShipDate() == null;
        //
        return allNullDates && !safeComp(event.getLabId(), event.getOriginatingLocationId());
    }

    private SpecimenEvent getFirstEvent(List<SpecimenEvent> dateOrderedEvents)
    {
        if (!dateOrderedEvents.isEmpty())
        {
            SpecimenEvent firstEvent = dateOrderedEvents.get(0);
            // walk backwards through the events until we find an event with at least one date field filled in that isn't
            // the first event.  Leaving all specimen event dates blank shouldn't make an event the processing location.
            for (int i = 1; i < dateOrderedEvents.size() - 1 && skipAsProcessingLocation(firstEvent); i++)
                firstEvent = dateOrderedEvents.get(i);
            return firstEvent;
        }
        return null;
    }

    public SpecimenEvent getLastEvent(List<SpecimenEvent> dateOrderedEvents)
    {
        if (dateOrderedEvents.isEmpty())
            return null;
        return dateOrderedEvents.get(dateOrderedEvents.size() - 1);
    }

    public Integer getProcessingLocationId(List<SpecimenEvent> dateOrderedEvents)
    {
        SpecimenEvent firstEvent = getFirstEvent(dateOrderedEvents);
        return firstEvent != null ? firstEvent.getLabId() : null;
    }

    public String getFirstProcessedByInitials(List<SpecimenEvent> dateOrderedEvents)
    {
        SpecimenEvent firstEvent = getFirstEvent(dateOrderedEvents);
        return firstEvent != null ? firstEvent.getProcessedByInitials() : null;
    }

    public LocationImpl getOriginatingLocation(Specimen specimen)
    {
        if (specimen.getOriginatingLocationId() != null)
        {
            LocationImpl location = StudyManager.getInstance().getLocation(specimen.getContainer(), specimen.getOriginatingLocationId().intValue());
            if (location != null)
                return location;
        }

        List<SpecimenEvent> events = getDateOrderedEventList(specimen);
        Integer firstLabId = getProcessingLocationId(events);
        if (firstLabId != null)
            return StudyManager.getInstance().getLocation(specimen.getContainer(), firstLabId.intValue());
        else
            return null;
    }

    public List<SampleRequest> getRequests(Container c, User user)
    {
        SimpleFilter filter = new SimpleFilter("Hidden", Boolean.FALSE);
        if (user != null)
            filter.addCondition("CreatedBy", user.getUserId());
        return _requestHelper.get(c, filter, "-Created");
    }

    public SampleRequest getRequest(Container c, int rowId)
    {
        return _requestHelper.get(c, rowId);
    }

    public SampleRequest createRequest(User user, SampleRequest request, boolean createEvent) throws SQLException, AttachmentService.DuplicateFilenameException
    {
        request = _requestHelper.create(user, request);
        if (createEvent)
            createRequestEvent(user, request, RequestEventType.REQUEST_CREATED, request.getRequestDescription(), null);
        return request;
    }

    public void updateRequest(User user, SampleRequest request) throws SQLException, RequestabilityManager.InvalidRuleException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            _requestHelper.update(user, request);

            // update specimen states
            List<Specimen> specimens = request.getSpecimens();
            if (specimens != null && specimens.size() > 0)
            {
                SampleRequestStatus status = getRequestStatus(request.getContainer(), request.getStatusId());
                updateSpecimenStatus(specimens, user, status.isSpecimensLocked());
            }
            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }

    /**
     * Update the lockedInRequest and available field states for the set of specimens.
     */
    private void updateSpecimenStatus(List<Specimen> specimens, User user, boolean lockedInRequest) throws SQLException, RequestabilityManager.InvalidRuleException
    {
        for (Specimen specimen : specimens)
        {
            specimen.setLockedInRequest(lockedInRequest);
            Table.update(user, StudySchema.getInstance().getTableInfoVial(), specimen, specimen.getRowId());
        }
        updateRequestabilityAndCounts(specimens, user);
        if (specimens.size() > 0)
            clearCaches(getContainer(specimens));
    }

    private Container getContainer(List<Specimen> specimens)
    {
        Container container = specimens.get(0).getContainer();
        if (AppProps.getInstance().isDevMode())
        {
            for (int i = 1; i < specimens.size(); i++)
            {
                if (!container.equals(specimens.get(i).getContainer()))
                    throw new IllegalStateException("All specimens must be from the same container");
            }
        }
        return container;
    }

    private static final String UPDATE_SPECIMEN_COUNT_SQL_PREFIX =
            "UPDATE study.Specimen SET\n" +
                    "    TotalVolume = VialCounts.TotalVolume,\n" +
                    "    AvailableVolume = VialCounts.AvailableVolume,\n" +
                    "    VialCount = VialCounts.VialCount,\n" +
                    "    LockedInRequestCount = VialCounts.LockedInRequestCount,\n" +
                    "    AtRepositoryCount = VialCounts.AtRepositoryCount,\n" +
                    "    AvailableCount = VialCounts.AvailableCount,\n" +
                    "    ExpectedAvailableCount = VialCounts.ExpectedAvailableCount\n" +
                    "FROM (\n" +
                    "\tSELECT SpecimenId,\n" +
                    "\t\tSUM(Volume) AS TotalVolume,\n" +
                    "\t\tSUM(CASE Available WHEN ? THEN Volume ELSE 0 END) AS AvailableVolume,\n" +
                    "\t\tCOUNT(GlobalUniqueId) AS VialCount,\n" +
                    "\t\tSUM(CASE LockedInRequest WHEN ? THEN 1 ELSE 0 END) AS LockedInRequestCount,\n" +
                    "\t\tSUM(CASE AtRepository WHEN ? THEN 1 ELSE 0 END) AS AtRepositoryCount,\n" +
                    "\t\tSUM(CASE Available WHEN ? THEN 1 ELSE 0 END) AS AvailableCount,\n" +
                    "\t\t(COUNT(GlobalUniqueId) - SUM(\n" +
                    "\t\tCASE\n" +
                    "\t\t\t(CASE LockedInRequest WHEN ? THEN 1 ELSE 0 END) -- Null is considered false for LockedInRequest\n" +
                    "\t\t\t| (CASE Requestable WHEN ? THEN 1 ELSE 0 END)-- Null is considered true for Requestable\n" +
                    "\t\t\tWHEN 1 THEN 1 ELSE 0 END)\n" +
                    "\t\t) AS ExpectedAvailableCount" +
                    "\tFROM study.Vial\n" +
                    "\tWHERE study.Vial.Container = ?\n";

    private static final String UPDATE_SPECIMEN_COUNT_SQL_SUFFIX =
                    "\tGROUP BY SpecimenId\n" +
                    ") VialCounts\n" +
                    "WHERE study.Specimen.RowId = VialCounts.SpecimenId";


    private void updateSpecimenCounts(Container container, User user, List<Specimen> specimens)
    {
        SQLFragment updateSql = new SQLFragment(UPDATE_SPECIMEN_COUNT_SQL_PREFIX,
                Boolean.TRUE, // AvailableVolume
                Boolean.TRUE, // LockedInRequestCount
                Boolean.TRUE, // AtRepositoryCount
                Boolean.TRUE, // AvailableCount
                Boolean.TRUE, // LockedInRequest case of ExpectedAvailableCount
                Boolean.FALSE, // Requestable case of ExpectedAvailableCount
                container.getId()); // container filter

        if (specimens != null && specimens.size() > 0)
        {
            Set<Integer> specimenIds = new HashSet<>();
            for (Specimen specimen : specimens)
                specimenIds.add(specimen.getSpecimenId());

            updateSql.append(" AND study.Vial.SpecimenId IN (");
            String sep = "";
            for (Integer id : specimenIds)
            {
                updateSql.append(sep).append("?");
                updateSql.add(id);
                sep = ", ";
            }
            updateSql.append(")\n");
        }
        updateSql.append(UPDATE_SPECIMEN_COUNT_SQL_SUFFIX);
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(updateSql);
    }

    public void updateSpecimenCounts(Container container, User user) throws SQLException
    {
        updateSpecimenCounts(container, user, null);
    }

    private void updateRequestabilityAndCounts(List<Specimen> specimens, User user) throws SQLException, RequestabilityManager.InvalidRuleException
    {
        if (specimens.size() == 0)
            return;
        Container container = getContainer(specimens);

        // update requestable flags before updating counts, since available count could change:
        for (int start = 0; start < specimens.size(); start += 1000)
        {
            List<Specimen> subset = specimens.subList(start, start + Math.min(1000, specimens.size() - start));
            RequestabilityManager.getInstance().updateRequestability(container, user, subset);
        }

        for (int start = 0; start < specimens.size(); start += 1000)
        {
            List<Specimen> subset = specimens.subList(start, start + Math.min(1000, specimens.size() - start));
            updateSpecimenCounts(container, user, subset);
        }
    }

    public SampleRequestRequirement[] getRequestRequirements(SampleRequest request)
    {
        if (request == null)
            return new SampleRequestRequirement[0];
        return request.getRequirements();
    }

    public void deleteRequestRequirement(User user, SampleRequestRequirement requirement) throws SQLException, AttachmentService.DuplicateFilenameException
    {
        deleteRequestRequirement(user, requirement, true);
    }

    public void deleteRequestRequirement(User user, SampleRequestRequirement requirement, boolean createEvent) throws SQLException, AttachmentService.DuplicateFilenameException
    {
        if (createEvent)
            createRequestEvent(user, requirement, RequestEventType.REQUIREMENT_REMOVED, requirement.getRequirementSummary(), null);
        requirement.delete();
    }

    public void createRequestRequirement(User user, SampleRequestRequirement requirement, boolean createEvent) throws SQLException, AttachmentService.DuplicateFilenameException
    {
        createRequestRequirement(user, requirement, createEvent, false);
    }

    public void createRequestRequirement(User user, SampleRequestRequirement requirement, boolean createEvent, boolean force) throws SQLException, AttachmentService.DuplicateFilenameException
    {
        SampleRequest request = getRequest(requirement.getContainer(), requirement.getRequestId());
        SampleRequestRequirement newRequirement = _requirementProvider.createRequirement(user, request, requirement, force);
        if (newRequirement != null && createEvent)
            createRequestEvent(user, requirement, RequestEventType.REQUIREMENT_ADDED, requirement.getRequirementSummary(), null);
    }

    public void updateRequestRequirement(User user, SampleRequestRequirement requirement)
    {
        requirement.update(user);
    }

    public boolean isInFinalState(SampleRequest request)
    {
        return getRequestStatus(request.getContainer(), request.getStatusId()).isFinalState();
    }

    public SampleRequestStatus getRequestStatus(Container c, int rowId)
    {
        return _requestStatusHelper.get(c, rowId);
    }

    public AdditiveType getAdditiveType(Container c, int rowId)
    {
        return _additiveHelper.get(c, rowId);
    }

    public List<AdditiveType> getAdditiveTypes(Container c)
    {
        return _additiveHelper.get(c, "ExternalId");
    }

    public DerivativeType getDerivativeType(Container c, int rowId)
    {
        return _derivativeHelper.get(c, rowId);
    }

    public List<DerivativeType> getDerivativeTypes(Container c)
    {
        return _derivativeHelper.get(c, "ExternalId");
    }

    public PrimaryType getPrimaryType(Container c, int rowId)
    {
        return _primaryTypeHelper.get(c, rowId);
    }

    public List<PrimaryType> getPrimaryTypes(Container c)
    {
        return _primaryTypeHelper.get(c, "ExternalId");
    }

    public List<SampleRequestStatus> getRequestStatuses(Container c, User user)
    {
        List<SampleRequestStatus> statuses = _requestStatusHelper.get(c, "SortOrder");
        // if the 'not-yet-submitted' status doesn't exist, create it here, with sort order -1,
        // so it's always first.
        if (statuses == null || statuses.isEmpty() || statuses.get(0).getSortOrder() != -1)
        {
            SampleRequestStatus notYetSubmittedStatus = new SampleRequestStatus();
            notYetSubmittedStatus.setContainer(c);
            notYetSubmittedStatus.setFinalState(false);
            notYetSubmittedStatus.setSpecimensLocked(true);
            notYetSubmittedStatus.setLabel("Not Yet Submitted");
            notYetSubmittedStatus.setSortOrder(-1);
            try
            {
                Table.insert(user, _requestStatusHelper.getTableInfo(), notYetSubmittedStatus);
                statuses = _requestStatusHelper.get(c, "SortOrder");
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return statuses;
    }

    public SampleRequestStatus getRequestShoppingCartStatus(Container c, User user)
    {
        List<SampleRequestStatus> statuses = getRequestStatuses(c, user);
        if (statuses.get(0).getSortOrder() != -1)
            throw new IllegalStateException("Shopping cart status should be created automatically.");
        return statuses.get(0);
    }

    public SampleRequestStatus getInitialRequestStatus(Container c, User user, boolean nonCart)
    {
        List<SampleRequestStatus> statuses = getRequestStatuses(c, user);
        if (!nonCart && isSpecimenShoppingCartEnabled(c))
            return statuses.get(0);
        else
            return statuses.get(1);
    }

    public boolean hasEditRequestPermissions(User user, SampleRequest request) throws ServletException
    {
        if (request == null)
            return false;
        Container container = request.getContainer();
        if (!container.hasPermission(user, RequestSpecimensPermission.class))
            return false;
        if (container.hasPermission(user, ManageRequestsPermission.class))
            return true;

        if (SampleManager.getInstance().isSpecimenShoppingCartEnabled(container))
        {
            SampleRequestStatus cartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(container, user);
            if (cartStatus.getRowId() == request.getStatusId() && request.getCreatedBy() == user.getUserId())
                return true;
        }
        return false;
    }

    public Set<Integer> getRequestStatusIdsInUse(Container c)
    {
        List<SampleRequest> requests = _requestHelper.get(c);
        Set<Integer> uniqueStatuses = new HashSet<>();
        for (SampleRequest request : requests)
            uniqueStatuses.add(request.getStatusId());
        return uniqueStatuses;
    }

    public void createRequestStatus(User user, SampleRequestStatus status) throws SQLException
    {
        _requestStatusHelper.create(user, status);
    }

    public void updateRequestStatus(User user, SampleRequestStatus status) throws SQLException
    {
        _requestStatusHelper.update(user, status);
    }

    public void deleteRequestStatus(User user, SampleRequestStatus status) throws SQLException
    {
        _requestStatusHelper.delete(status);
    }

    public List<SampleRequestEvent> getRequestEvents(Container c)
    {
        return _requestEventHelper.get(c);
    }

    public SampleRequestEvent getRequestEvent(Container c, int rowId)
    {
        return _requestEventHelper.get(c, rowId);
    }

    public enum RequestEventType
    {
        REQUEST_CREATED("Request Created"),
        REQUEST_STATUS_CHANGED("Request Status Changed"),
        REQUIREMENT_ADDED("Requirement Created"),
        REQUIREMENT_REMOVED("Requirement Removed"),
        REQUIREMENT_UPDATED("Requirement Updated"),
        REQUEST_UPDATED("Request Updated"),
        SPECIMEN_ADDED("Specimen Added"),
        SPECIMEN_REMOVED("Specimen Removed"),
        SPECIMEN_LIST_GENERATED("Specimen List Generated"),
        COMMENT_ADDED("Comment/Attachment(s) Added"),
        NOTIFICATION_SENT("Notification Sent");

        private String _displayText;

        RequestEventType(String displayText)
        {
            _displayText = displayText;
        }

        public String getDisplayText()
        {
            return _displayText;
        }
    }

    public SampleRequestEvent createRequestEvent(User user, SampleRequestRequirement requirement, RequestEventType type, String comments, List<AttachmentFile> attachments) throws SQLException, AttachmentService.DuplicateFilenameException
    {
        return createRequestEvent(user, requirement.getContainer(), requirement.getRequestId(), requirement.getRowId(), type, comments, attachments);
    }

    public SampleRequestEvent createRequestEvent(User user, SampleRequest request, RequestEventType type, String comments, List<AttachmentFile> attachments) throws SQLException, AttachmentService.DuplicateFilenameException
    {
        return createRequestEvent(user, request.getContainer(), request.getRowId(), -1, type, comments, attachments);
    }

    private SampleRequestEvent createRequestEvent(User user, Container container, int requestId, int requirementId, RequestEventType type, String comments, List<AttachmentFile> attachments) throws SQLException, AttachmentService.DuplicateFilenameException
    {
        SampleRequestEvent event = new SampleRequestEvent();
        event.setEntryType(type.getDisplayText());
        event.setComments(comments);
        event.setRequestId(requestId);
        event.setCreated(new Date(System.currentTimeMillis()));
        if (requirementId >= 0)
            event.setRequirementId(requirementId);
        event.setContainer(container);
        event.setEntityId(GUID.makeGUID());
        event = createRequestEvent(user, event);
        try
        {
            AttachmentService.get().addAttachments(event, attachments, user);
        }
        catch (AttachmentService.DuplicateFilenameException e)
        {
            // UI should (minimally) catch and display these errors nicely
            throw e;
        }
        catch (IOException e)
        {
            // this is unexpected, and indicative of a larger system problem; we'll convert to a runtime
            // exception, rather than requiring all event loggers to handle this unlikely scenario:
            throw new RuntimeException(e);
        }
        return event;
    }

    private void deleteRequestEvents(User user, SampleRequest request) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("RequestId", request.getRowId());
        List<SampleRequestEvent> events = _requestEventHelper.get(request.getContainer(), filter);
        for (SampleRequestEvent event : events)
        {
            AttachmentService.get().deleteAttachments(event);
            _requestEventHelper.delete(event);
        }
    }

    private SampleRequestEvent createRequestEvent(User user, SampleRequestEvent event) throws SQLException
    {
        return _requestEventHelper.create(user, event);
    }

    private static final String REQUEST_SPECIMEN_JOIN = "SELECT SpecimenDetail.* FROM study.SampleRequest AS request, " +
            "study.SampleRequestSpecimen AS map, study.SpecimenDetail AS SpecimenDetail\n" +
            "WHERE request.RowId = map.SampleRequestId AND SpecimenDetail.GlobalUniqueId = map.SpecimenGlobalUniqueId\n" +
            "AND request.Container = map.Container AND map.Container = SpecimenDetail.Container AND " +
            "request.RowId = ? AND request.Container = ?;";

    public List<Specimen> getRequestSpecimens(SampleRequest request)
    {
        return new SqlSelector(StudySchema.getInstance().getSchema(), REQUEST_SPECIMEN_JOIN,
                request.getRowId(), request.getContainer()).getArrayList(Specimen.class);
    }

    public RepositorySettings getRepositorySettings(Container container)
    {
        Map<String,String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(),
                container, "SpecimenRepositorySettings");
        if (settingsMap.isEmpty())
        {
            RepositorySettings defaults = RepositorySettings.getDefaultSettings(container);
            saveRepositorySettings(container, defaults);
            return defaults;
        }
        else
            return new RepositorySettings(container, settingsMap);
    }

    public void saveRepositorySettings(Container container, RepositorySettings settings)
    {
        Map<String, String> settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRepositorySettings", true);
        settings.populateMap(settingsMap);
        PropertyManager.saveProperties(settingsMap);
    }


    public RequestNotificationSettings getRequestNotificationSettings(Container container)
    {
        Map<String,String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestNotifications");
        if (settingsMap.get("ReplyTo") == null)
        {
            RequestNotificationSettings defaults = RequestNotificationSettings.getDefaultSettings(container);
            saveRequestNotificationSettings(container, defaults);
            return defaults;
        }
        else
            return new RequestNotificationSettings(settingsMap);
    }

    public void saveRequestNotificationSettings(Container container, RequestNotificationSettings settings)
    {
        Map<String, String> settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestNotifications", true);
        settings.populateMap(settingsMap);
        PropertyManager.saveProperties(settingsMap);
    }


    public DisplaySettings getDisplaySettings(Container container)
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestDisplay");
        if (settingsMap.get("OneAvailableVial") == null)
        {
            DisplaySettings defaults = DisplaySettings.getDefaultSettings();
            saveDisplaySettings(container, defaults);
            return defaults;
        }
        else
            return new DisplaySettings(settingsMap);
    }

    public void saveDisplaySettings(Container container, DisplaySettings settings)
    {
        Map<String, String> settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestDisplay", true);
        settings.populateMap(settingsMap);
        PropertyManager.saveProperties(settingsMap);
    }

    public StatusSettings getStatusSettings(Container container)
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestStatus");
        if (settingsMap.get(StatusSettings.KEY_USE_SHOPPING_CART) == null)
        {
            StatusSettings defaults = StatusSettings.getDefaultSettings();
            saveStatusSettings(container, defaults);
            return defaults;
        }
        else
            return new StatusSettings(settingsMap);
    }

    public void saveStatusSettings(Container container, StatusSettings settings)
    {
        Map<String, String> settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestStatus", true);
        settings.populateMap(settingsMap);
        PropertyManager.saveProperties(settingsMap);
    }

    public boolean isSpecimenShoppingCartEnabled(Container container)
    {
        return getStatusSettings(container).isUseShoppingCart();
    }

    public static class SpecimenRequestInput
    {
        private String _title;
        private String _helpText;
        private boolean _required;
        private boolean _rememberSiteValue;
        private boolean _multiLine;
        private int _displayOrder;
        private Map<Integer,String> _locationToDefaultValue;

        public SpecimenRequestInput(String title, String helpText, int displayOrder, boolean multiLine, boolean required, boolean rememberSiteValue)
        {
            _title = title;
            _required = required;
            _rememberSiteValue = rememberSiteValue;
            _helpText = helpText;
            _displayOrder = displayOrder;
            _multiLine = multiLine;
        }

        public SpecimenRequestInput(String title, String helpText, int displayOrder)
        {
            this(title, helpText, displayOrder, false, false, false);
        }

        public String getHelpText()
        {
            return _helpText;
        }

        public boolean isRememberSiteValue()
        {
            return _rememberSiteValue;
        }

        public boolean isRequired()
        {
            return _required;
        }

        public String getTitle()
        {
            return _title;
        }

        public int getDisplayOrder()
        {
            return _displayOrder;
        }

        public boolean isMultiLine()
        {
            return _multiLine;
        }

        public void setMultiLine(boolean multiLine)
        {
            _multiLine = multiLine;
        }

        public void setRememberSiteValue(boolean rememberSiteValue)
        {
            _rememberSiteValue = rememberSiteValue;
        }

        public void setRequired(boolean required)
        {
            _required = required;
        }

        public Map<Integer,String> getDefaultSiteValues(Container container) throws SQLException
        {
            if (!isRememberSiteValue())
                throw new UnsupportedOperationException("Only those inputs set to remember site values can be queried for a site default.");

            if (_locationToDefaultValue != null)
                return _locationToDefaultValue;
            String defaultObjectLsid = getRequestInputDefaultObjectLsid(container);
            String setItemLsid = ensureOntologyManagerSetItem(container, defaultObjectLsid, getTitle());
            Map<Integer, String> locationToValue = new HashMap<>();

            Map<String, ObjectProperty> defaultValueProperties = OntologyManager.getPropertyObjects(container, setItemLsid);
            if (defaultValueProperties != null)
            {
                for (Map.Entry<String, ObjectProperty> defaultValue : defaultValueProperties.entrySet())
                {
                    String locationIdString = defaultValue.getKey().substring(defaultValue.getKey().lastIndexOf(".") + 1);
                    int locationId = Integer.parseInt(locationIdString);
                    locationToValue.put(locationId, defaultValue.getValue().getStringValue());
                }
            }
            _locationToDefaultValue = locationToValue;
            return _locationToDefaultValue;
        }

        public void setDefaultSiteValue(Container container, int locationId, String value) throws SQLException
        {
            try {
                assert locationId > 0 : "Invalid site id: " + locationId;
                if (!isRememberSiteValue())
                    throw new UnsupportedOperationException("Only those inputs configured to remember site values can set a site default.");
                _locationToDefaultValue = null;
                String parentObjectLsid = getRequestInputDefaultObjectLsid(container);

                String setItemLsid = ensureOntologyManagerSetItem(container, parentObjectLsid, getTitle());
                String propertyId = parentObjectLsid + "." + locationId;
                ObjectProperty defaultValueProperty = new ObjectProperty(setItemLsid, container, propertyId, value);
                OntologyManager.deleteProperty(setItemLsid, propertyId, container, container);
                OntologyManager.insertProperties(container, setItemLsid, defaultValueProperty);
            }
            catch (ValidationException e)
            {
                throw new SQLException(e.getMessage());
            }
        }
    }

    public SpecimenRequestInput[] getNewSpecimenRequestInputs(Container container) throws SQLException
    {
        return getNewSpecimenRequestInputs(container, true);
    }

    private SpecimenRequestInput[] getNewSpecimenRequestInputs(Container container, boolean createIfMissing) throws SQLException
    {
        String parentObjectLsid = getRequestInputObjectLsid(container);
        Map<String,ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, parentObjectLsid);
        SpecimenRequestInput[] inputs = null;
        if (resourceProperties == null || resourceProperties.size() == 0)
        {
            if (createIfMissing)
            {
                inputs = new SpecimenRequestInput[] {
                        new SpecimenRequestInput("Assay Plan", "Please enter a description of or reference to the assay plan(s) that will be used for the requested specimens.", 0, true, true, false),
                        new SpecimenRequestInput("Shipping Information", "Please enter your shipping address along with any special instructions.", 1, true, true, true),
                        new SpecimenRequestInput("Comments", "Please enter any additional information regarding your request.", 2, true, false, false)
                };
                saveNewSpecimenRequestInputs(container, inputs);
            }
            return inputs;
        }
        else
        {
            inputs = new SpecimenRequestInput[resourceProperties.size()];
            for (Map.Entry<String, ObjectProperty> parentPropertyEntry : resourceProperties.entrySet())
            {
                String resourcePropertyLsid = parentPropertyEntry.getKey();
                int displayOrder = Integer.parseInt(resourcePropertyLsid.substring(resourcePropertyLsid.lastIndexOf('.') + 1));

                Map<String, ObjectProperty> inputProperties = parentPropertyEntry.getValue().retrieveChildProperties();
                String title = inputProperties.get(parentObjectLsid + ".Title").getStringValue();
                String helpText = null;
                if (inputProperties.get(parentObjectLsid + ".HelpText") != null)
                    helpText = inputProperties.get(parentObjectLsid + ".HelpText").getStringValue();
                boolean rememberSiteValue = inputProperties.get(parentObjectLsid + ".RememberSiteValue").getFloatValue() == 1;
                boolean required = inputProperties.get(parentObjectLsid + ".Required").getFloatValue() == 1;
                boolean multiLine = inputProperties.get(parentObjectLsid + ".MultiLine").getFloatValue() == 1;
                inputs[displayOrder] = new SpecimenRequestInput(title, helpText, displayOrder, multiLine, required, rememberSiteValue);
            }
        }
        return inputs;
    }

    private static String getRequestInputObjectLsid(Container container)
    {
        return new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + container.getRowId(), "RequestInput").toString();
    }

    private static String getRequestInputDefaultObjectLsid(Container container)
    {
        return new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + container.getRowId(), "RequestInputDefault").toString();
    }

    private static String ensureOntologyManagerSetItem(Container container, String lsidBase, String uniqueItemId) throws SQLException
    {
        try {
            Integer listParentObjectId = OntologyManager.ensureObject(container, lsidBase);
            String listItemReferenceLsidPrefix = lsidBase + "#objectResource.";
            String listItemObjectLsid = lsidBase + "#" + uniqueItemId;
            String listItemPropertyReferenceLsid = listItemReferenceLsidPrefix + uniqueItemId;

            // ensure the object that corresponds to a single list item:
            OntologyManager.ensureObject(container, listItemObjectLsid, listParentObjectId);

            // check to make sure that the list item is wired up to the top-level list object via a property:
            Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(container, lsidBase);
            if (!properties.containsKey(listItemPropertyReferenceLsid))
            {
                // create the resource property that links the parent object to the list item object:
                ObjectProperty resourceProperty = new ObjectProperty(lsidBase, container,
                        listItemPropertyReferenceLsid, listItemObjectLsid, PropertyType.RESOURCE);
                OntologyManager.insertProperties(container, lsidBase, resourceProperty);
            }
            return listItemObjectLsid;
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
    }

    public void saveNewSpecimenRequestInputs(Container container, SpecimenRequestInput[] inputs) throws SQLException
    {
        if (!requestInputsChanged(container, inputs))
            return;

        try {
            String parentObjectLsid = getRequestInputObjectLsid(container);
            String defaultValuesObjectLsid = getRequestInputDefaultObjectLsid(container);
            OntologyManager.deleteOntologyObject(parentObjectLsid, container, true);
            OntologyManager.deleteOntologyObject(defaultValuesObjectLsid, container, true);
            for (int i = 0; i < inputs.length; i++)
            {
                SpecimenRequestInput input = inputs[i];
                String setItemLsid = ensureOntologyManagerSetItem(container, parentObjectLsid, "" + i);
                ObjectProperty[] props = new ObjectProperty[5];
                props[0] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".HelpText", input.getHelpText());
                props[1] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".Required", input.isRequired() ? 1 : 0);
                props[2] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".RememberSiteValue", input.isRememberSiteValue() ? 1 : 0);
                props[3] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".Title", input.getTitle());
                props[4] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".MultiLine", input.isMultiLine() ? 1 : 0);
                OntologyManager.insertProperties(container, setItemLsid, props);
            }
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
    }

    private boolean requestInputsChanged(Container container, SpecimenRequestInput[] newInputs) throws SQLException
    {
        SpecimenRequestInput[] oldInputs = getNewSpecimenRequestInputs(container, false);
        if (oldInputs == null)
            return true;
        else if (oldInputs.length != newInputs.length)
            return true;
        else
        {
            for (int i = 0; i < oldInputs.length; i++)
            {
                if (oldInputs[i].isMultiLine() != newInputs[i].isMultiLine() ||
                    oldInputs[i].isRememberSiteValue() != newInputs[i].isRememberSiteValue() ||
                    oldInputs[i].isRequired() != newInputs[i].isRequired() ||
                    !oldInputs[i].getTitle().equals(newInputs[i].getTitle()) ||
                    !getSafeString(oldInputs[i].getHelpText()).equals(getSafeString(newInputs[i].getHelpText())))
                    return true;
            }
        }
        return false;
    }

    private String getSafeString(String str)
    {
        if (str == null)
            return "";
        else
            return str;
    }

    private static final Object REQUEST_ADDITION_LOCK = new Object();
    public void createRequestSampleMapping(User user, SampleRequest request, List<Specimen> specimens, boolean createEvents, boolean createRequirements)
            throws SQLException, RequestabilityManager.InvalidRuleException, AttachmentService.DuplicateFilenameException
    {
        if (specimens == null || specimens.size() == 0)
            return;

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            synchronized (REQUEST_ADDITION_LOCK)
            {
                for (Specimen specimen : specimens)
                {
                    if (!request.getContainer().getId().equals(specimen.getContainer().getId()))
                        throw new IllegalStateException("Mismatched containers.");

                    if (!specimen.isAvailable())
                        throw new IllegalStateException(RequestabilityManager.makeSpecimenUnavailableMessage(specimen, null));
                }

                for (Specimen specimen : specimens)
                {
                    Map<String, Object> fields = new HashMap<>();
                    fields.put("Container", request.getContainer().getId());
                    fields.put("SampleRequestId", request.getRowId());
                    fields.put("SpecimenGlobalUniqueId", specimen.getGlobalUniqueId());
                    Table.insert(user, StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), fields);
                    if (createEvents)
                        createRequestEvent(user, request, RequestEventType.SPECIMEN_ADDED, specimen.getSampleDescription(), null);
                }

                if (createRequirements)
                    getRequirementsProvider().generateDefaultRequirements(user, request);

                SampleRequestStatus status = getRequestStatus(request.getContainer(), request.getStatusId());
                updateSpecimenStatus(specimens, user, status.isSpecimensLocked());
            }

            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public List<Specimen> getSpecimens(Container container, int[] sampleRowIds)
    {
        Set<Long> uniqueRowIds = new HashSet<>(sampleRowIds.length);
        for (int sampleRowId : sampleRowIds)
            uniqueRowIds.add((long)sampleRowId);
        List<Long> rowIds = new ArrayList<>(uniqueRowIds);
        return getSpecimens(container, rowIds);
    }

    public List<Specimen> getSpecimens(Container container, List<Long> sampleRowIds)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause("RowId", sampleRowIds);
        List<Specimen> specimens = _specimenDetailHelper.get(container, filter);
        if (specimens.size() != sampleRowIds.size())
            throw new IllegalStateException("One or more specimen RowIds had no matching specimen.");
        return specimens;
    }

    public static class SpecimenRequestException extends Exception
    {
    }

    public List<Specimen> getSpecimens(Container container, String[] globalUniqueIds) throws SpecimenRequestException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        Set<String> uniqueRowIds = new HashSet<>(globalUniqueIds.length);
        for (String globalUniqueId : globalUniqueIds)
            uniqueRowIds.add(globalUniqueId);
        List<String> ids = new ArrayList<>(uniqueRowIds);
        filter.addInClause("GlobalUniqueId", ids);
        List<Specimen> specimens = _specimenDetailHelper.get(container, filter);
        if (specimens == null || specimens.size() != ids.size())
            throw new SpecimenRequestException();       // an id has no matching specimen, let caller determine what to report
        return specimens;
    }

    public void deleteRequest(User user, SampleRequest request) throws SQLException, RequestabilityManager.InvalidRuleException, AttachmentService.DuplicateFilenameException
    {
        DbScope scope = _requestHelper.getTableInfo().getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            List<Specimen> specimens = request.getSpecimens();
            int[] specimenIds = new int[specimens.size()];
            for (int i = 0; i < specimens.size(); i++)
                specimenIds[i] = specimens.get(i).getRowId();

            deleteRequestSampleMappings(user, request, specimenIds, false);

            deleteMissingSpecimens(request);

            _requirementProvider.deleteRequirements(request);

            deleteRequestEvents(user, request);
            _requestHelper.delete(request);

            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public void deleteRequestSampleMappings(User user, SampleRequest request, int[] sampleIds, boolean createEvents)
            throws SQLException, RequestabilityManager.InvalidRuleException, AttachmentService.DuplicateFilenameException
    {
        if (sampleIds == null || sampleIds.length == 0)
            return;
        List<Specimen> specimens = getSpecimens(request.getContainer(), sampleIds);
        List<String> globalUniqueIds = new ArrayList<>(specimens.size());
        List<String> descriptions = new ArrayList<>();
        for (Specimen specimen : specimens)
        {
            globalUniqueIds.add(specimen.getGlobalUniqueId());
            descriptions.add(specimen.getSampleDescription());
        }

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            SimpleFilter filter = SimpleFilter.createContainerFilter(request.getContainer());
            filter.addCondition("SampleRequestId", request.getRowId());
            filter.addInClause("SpecimenGlobalUniqueId", globalUniqueIds);
            Table.delete(StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), filter);
            if (createEvents)
            {
                for (String description : descriptions)
                    createRequestEvent(user, request, RequestEventType.SPECIMEN_REMOVED, description, null);
            }

            updateSpecimenStatus(specimens, user, false);

            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public Integer[] getRequestIdsForSpecimen(Specimen specimen) throws SQLException
    {
        return getRequestIdsForSpecimen(specimen, false);
    }

    public Integer[] getRequestIdsForSpecimen(Specimen specimen, boolean lockingRequestsOnly) throws SQLException
    {
        if (specimen == null)
            return new Integer[0];
        SQLFragment sql = new SQLFragment("SELECT SampleRequestId FROM " + StudySchema.getInstance().getTableInfoSampleRequestSpecimen() +
                " Map, " + StudySchema.getInstance().getTableInfoSampleRequest() + " Request, " +
                StudySchema.getInstance().getTableInfoSampleRequestStatus() + " Status WHERE SpecimenGlobalUniqueId = ? " +
                "AND Request.Container = ? AND Map.Container = Request.Container AND Status.Container = Request.Container " +
                "AND Map.SampleRequestId = Request.RowId AND Request.StatusId = Status.RowId");
        sql.add(specimen.getGlobalUniqueId());
        sql.add(specimen.getContainer().getId());
        if (lockingRequestsOnly)
        {
            sql.append(" AND Status.SpecimensLocked = ?");
            sql.add(Boolean.TRUE);
        }
        Table.TableResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql);
        List<Integer> rowIdList = new ArrayList<>();
        while (rs.next())
            rowIdList.add(rs.getInt(1));
        rs.close();
        return rowIdList.toArray(new Integer[rowIdList.size()]);
    }

    public SpecimenTypeSummary getSpecimenTypeSummary(Container container)
    {
        String cacheKey = container.getId() + "/SpecimenTypeSummary";
        SpecimenTypeSummary summary = (SpecimenTypeSummary) DbCache.get(StudySchema.getInstance().getTableInfoVial(), cacheKey);

        if (summary != null)
            return summary;

        try
        {
            StudyImpl study = StudyManager.getInstance().getStudy(container);
            if (study == null)
                return null;

            SQLFragment specimenTypeSummarySQL = new SQLFragment("SELECT\n" +
                "\tPrimaryType,\n" +
                "\tPrimaryTypeId,\n" +
                "\tDerivative,\n" +
                "\tDerivativeTypeId,\n" +
                "\tAdditive,\n" +
                "\tAdditiveTypeId,\n" +
                "\tSUM(VialCount) AS VialCount\n" +
                "FROM (\n" +
                "\tSELECT\n" +
                "\tstudy.SpecimenPrimaryType.PrimaryType AS PrimaryType,\n" +
                "\tPrimaryTypeId,\n" +
                "\tstudy.SpecimenDerivative.Derivative AS Derivative,\n" +
                "\tDerivativeTypeId,\n" +
                "\tstudy.SpecimenAdditive.Additive AS Additive,\n" +
                "\tAdditiveTypeId,\n" +
                "\tSpecimens.VialCount\n" +
                "\tFROM\n" +
                "\t\t(SELECT study.Specimen.PrimaryTypeId,\n" +
                "\t\t\tstudy.Specimen.DerivativeTypeId,\n" +
                "\t\t\tstudy.Specimen.AdditiveTypeId,\n" +
                "\t\t\tstudy.Specimen.Container,\n" +
                "\t\t\tSUM(study.Specimen.VialCount) AS VialCount\n" +
                "\t\tFROM study.Specimen\n");

            if (study.isAncillaryStudy())
            {
                specimenTypeSummarySQL.append("\t\tWHERE study.Specimen.Container IN (?, ?)\n");
                specimenTypeSummarySQL.add(study.getSourceStudy().getContainer().getId());
                specimenTypeSummarySQL.add(container.getId());
                String[] ptids = StudyManager.getInstance().getParticipantIds(study);
                specimenTypeSummarySQL.append("\t\t\tAND study.Specimen.PTID IN (");
                if (ptids == null || ptids.length == 0)
                    specimenTypeSummarySQL.append("NULL");
                else
                {
                    String comma = "";
                    for (String ptid : ptids)
                    {
                        specimenTypeSummarySQL.append(comma).append("?");
                        specimenTypeSummarySQL.add(ptid);
                        comma = ", ";
                    }
                }
                specimenTypeSummarySQL.append(")\n");
            }
            else
            {
                specimenTypeSummarySQL.append("\t\tWHERE study.Specimen.Container = ?\n");
                specimenTypeSummarySQL.add(container.getId());
            }

            specimenTypeSummarySQL.append("\t\tGROUP BY study.Specimen.PrimaryTypeId,\n" +
                "\t\t\tstudy.Specimen.DerivativeTypeId,\n" +
                "\t\t\tstudy.Specimen.AdditiveTypeId,\n" +
                "\t\t\tstudy.Specimen.Container) Specimens\n" +
                "\tLEFT OUTER JOIN study.SpecimenPrimaryType ON\n" +
                "\t\tstudy.SpecimenPrimaryType.RowId = Specimens.PrimaryTypeId AND\n" +
                "\t\tstudy.SpecimenPrimaryType.Container = Specimens.Container\n" +
                "\tLEFT OUTER JOIN study.SpecimenDerivative ON\n" +
                "\t\tstudy.SpecimenDerivative.RowId = Specimens.DerivativeTypeId AND\n" +
                "\t\tstudy.SpecimenDerivative.Container = Specimens.Container\n" +
                "\tLEFT OUTER JOIN study.SpecimenAdditive ON\n" +
                "\t\tstudy.SpecimenAdditive.RowId = Specimens.AdditiveTypeId AND\n" +
                "\t\tstudy.SpecimenAdditive.Container = Specimens.Container\n" +
                ") ContainerTotals\n" +
                "GROUP BY PrimaryType, PrimaryTypeId, Derivative, DerivativeTypeId, Additive, AdditiveTypeId\n" +
                "ORDER BY PrimaryType, Derivative, Additive");

            SpecimenTypeSummaryRow[] rows = Table.executeQuery(StudySchema.getInstance().getSchema(), specimenTypeSummarySQL, SpecimenTypeSummaryRow.class);

            summary = new SpecimenTypeSummary(container, rows);
            DbCache.put(StudySchema.getInstance().getTableInfoVial(), cacheKey, summary, 8 * CacheManager.HOUR);
            return summary;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private class DistinctValueList extends ArrayList<String> implements StudyCachable
    {
        private Container _container;
        private String _cacheKey;
        public DistinctValueList(Container container, String cacheKey)
        {
            super();
            _container = container;
            _cacheKey = cacheKey;
        }

        public StudyCachable createMutable()
        {
            throw new UnsupportedOperationException("DistinctValueList objects are never mutable.");
        }

        public Container getContainer()
        {
            return _container;
        }

        public Object getPrimaryKey()
        {
            return _cacheKey;
        }

        public void lock()
        {
        }
    }

    public List<String> getDistinctColumnValues(Container container, User user, ColumnInfo col, boolean forceDistinctQuery, String orderBy, TableInfo forcedTable) throws SQLException
    {
        String cacheKey = "DistinctColumnValues." + col.getColumnName();
        boolean isLookup = col.getFk() != null && !forceDistinctQuery;
        TableInfo tinfo = forcedTable;
        if (tinfo == null)
            tinfo = isLookup ? col.getFk().getLookupTableInfo() : col.getParentTable();
        TableInfo cachedTinfo = tinfo instanceof FilteredTable ? ((FilteredTable) tinfo).getRealTable() : tinfo;
        DistinctValueList distinctValues = (DistinctValueList) StudyCache.getCached(cachedTinfo, container, cacheKey);
        if (distinctValues == null)
        {
            distinctValues = new DistinctValueList(container, cacheKey);
            if (col.isBooleanType())
            {
                distinctValues.add("True");
                distinctValues.add("False");
            }
            else
            {
                ResultSet rs = null;
                try
                {
                    if (isLookup)
                    {
                        if (tinfo.supportsContainerFilter())
                        {
                            Set<Container> containers = new HashSet<>();
                            containers.add(container);
                            Study study = StudyManager.getInstance().getStudy(container);
                            if (study != null && study.isAncillaryStudy())
                            {
                                Container sourceStudy = study.getSourceStudy().getContainer();
                                if (sourceStudy != null && sourceStudy.hasPermission(user, ReadPermission.class))
                                    containers.add(sourceStudy);
                            }
                            ((ContainerFilterable)tinfo).setContainerFilter(new ContainerFilter.SimpleContainerFilter(containers));
                        }
                        rs = new TableSelector(tinfo.getColumn(tinfo.getTitleColumn()), null,
                                new Sort(orderBy != null ? orderBy : tinfo.getTitleColumn())).getResultSet();
                    }
                    else
                    {
                        SQLFragment sql = new SQLFragment("SELECT DISTINCT " + col.getValueSql("_distinct").getSQL() + " FROM ");
                        sql.append(tinfo.getFromSQL("_distinct"));
                        if (orderBy != null)
                            sql.append(" ORDER BY " + orderBy);
                        rs = Table.executeQuery(tinfo.getSchema(), sql);
                    }
                    while (rs.next())
                    {
                        Object value = rs.getObject(1);
                        if (value != null && value.toString().length() > 0)
                            distinctValues.add(value.toString());
                    }
                }
                finally
                {
                    if (rs != null) try { rs.close(); } catch (SQLException e) {}
                }
            }
            StudyCache.cache(cachedTinfo, container, cacheKey, distinctValues);
        }
        return distinctValues;
    }

    public void deleteMissingSpecimens(SampleRequest sampleRequest) throws SQLException
    {
        List<String> missingSpecimens = getMissingSpecimens(sampleRequest);
        if (missingSpecimens.isEmpty())
            return;
        SimpleFilter filter = SimpleFilter.createContainerFilter(sampleRequest.getContainer());
        filter.addCondition("SampleRequestId", sampleRequest.getRowId());
        filter.addInClause("SpecimenGlobalUniqueId", missingSpecimens);
        Table.delete(StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), filter);
    }

    public boolean isSampleRequestEnabled(Container container)
    {
        return isSampleRequestEnabled(container, true);
    }

    public boolean isSampleRequestEnabled(Container container, boolean checkExistingStatuses)
    {
        if (!checkExistingStatuses)
        {
            return getRepositorySettings(container).isEnableRequests();
        }
        else
        {
            if (!getRepositorySettings(container).isEnableRequests())
                return false;
            List<SampleRequestStatus> statuses = _requestStatusHelper.get(container, "SortOrder");
            return (statuses != null && statuses.size() > 1);
        }
    }

    public List<String> getMissingSpecimens(SampleRequest sampleRequest) throws SQLException
    {
        String sql = "SELECT SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen WHERE SampleRequestId = ? and Container = ? and \n" +
                "SpecimenGlobalUniqueId NOT IN (SELECT GlobalUniqueId FROM study.Vial WHERE Container = ?);";
        ResultSet rs = null;
        List<String> missingSpecimens = new ArrayList<>();
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql, new Object[] {
                sampleRequest.getRowId(), sampleRequest.getContainer().getId(), sampleRequest.getContainer().getId() });
            while (rs.next())
                missingSpecimens.add(rs.getString("SpecimenGlobalUniqueId"));
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {}
        }
        return missingSpecimens;
    }

    public Map<Specimen, SpecimenComment> getSpecimensWithComments(Container container) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        SpecimenComment[] allComments = Table.select(StudySchema.getInstance().getTableInfoSpecimenComment(),
                Table.ALL_COLUMNS, filter, null, SpecimenComment.class);

        Map<Specimen, SpecimenComment> result = new HashMap<>();
        if (allComments.length > 0)
        {
            Map<String, SpecimenComment> globalUniqueIds = new HashMap<>();
            for (SpecimenComment comment : allComments)
                globalUniqueIds.put(comment.getGlobalUniqueId(), comment);

            SQLFragment sql = new SQLFragment();
            sql.append("SELECT * FROM ").append(StudySchema.getInstance().getTableInfoSpecimenDetail()).append(" WHERE GlobalUniqueId IN (");
            sql.append("SELECT DISTINCT GlobalUniqueId FROM ").append(StudySchema.getInstance().getTableInfoSpecimenComment());
            sql.append(" WHERE Container = ?");
            sql.add(container.getId());
            sql.append(") AND Container = ?;");
            sql.add(container.getId());

            Specimen[] commented = Table.executeQuery(StudySchema.getInstance().getSchema(), sql.getSQL(), sql.getParamsArray(), Specimen.class);

            for (Specimen specimen : commented)
                result.put(specimen, globalUniqueIds.get(specimen.getGlobalUniqueId()));
        }
        return result;
    }

    public Specimen[] getSpecimensByAvailableVialCount(Container container, int count) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition("AvailableCount", count);

        return new TableSelector(StudySchema.getInstance().getTableInfoSpecimenSummary(), filter, null).getArray(Specimen.class);
    }

    public Map<String,List<Specimen>> getVialsForSampleHashes(Container container, Collection<String> hashes, boolean onlyAvailable)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause("SpecimenHash", hashes);
        if (onlyAvailable)
            filter.addCondition("Available", true);
        SQLFragment sql = new SQLFragment("SELECT * FROM " + StudySchema.getInstance().getTableInfoSpecimenDetail().toString() + " ");
        sql.append(filter.getSQLFragment(StudySchema.getInstance().getSqlDialect()));

        Map<String, List<Specimen>> map = new HashMap<>();
        Specimen[] specimens = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArray(Specimen.class);

        for (Specimen specimen : specimens)
        {
            String hash = specimen.getSpecimenHash();
            List<Specimen> keySpecimens = map.get(hash);
            if (null == keySpecimens)
            {
                keySpecimens = new ArrayList<>();
                map.put(hash, keySpecimens);
            }
            keySpecimens.add(specimen);
        }

        return map;
    }

    public Map<String, Integer> getSampleCounts(Container container, Collection<String> specimenHashes)
    {
        List<Object> params = new ArrayList<>();
        params.add(container.getId());
        StringBuilder extraClause = new StringBuilder();

        if (specimenHashes != null)
        {
            extraClause.append(" AND SpecimenHash IN(");
            String separator = "";
            for (String specimenNumber : specimenHashes)
            {
                extraClause.append(separator);
                separator = ", ";
                extraClause.append("?");
                params.add(specimenNumber);
            }
            extraClause.append(")");
        }

        final Map<String, Integer> map = new HashMap<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), new SQLFragment("SELECT " +
                "SpecimenHash, CAST(AvailableCount AS Integer) AS AvailableCount" +
                " FROM " + StudySchema.getInstance().getTableInfoSpecimenSummary().toString() +
                " WHERE Container = ?" + extraClause, params)).forEach(new Selector.ForEachBlock<ResultSet>() {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String specimenHash = rs.getString("SpecimenHash");
                map.put(specimenHash, rs.getInt("AvailableCount"));
            }
        });

        return map;
    }

    public int getSampleCountForVisit(VisitImpl visit) throws SQLException
    {
        TableInfo tinfoSpec = StudySchema.getInstance().getTableInfoSpecimen();
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) AS NumVials FROM " +
                StudySchema.getInstance().getTableInfoVial() +
                " AS Vial\n" +
                "LEFT OUTER JOIN " +
                tinfoSpec +
                " ON\n" +
                "\tVial.SpecimenId = " + tinfoSpec + ".RowId\n" +
                " WHERE ").append(getVisitRangeSql(visit));
        List<Integer> results = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArrayList(Integer.class);
        if (1 != results.size())
            throw new IllegalStateException("Expected value from Select Count(*)");
        return results.get(0);
    }


    public void deleteSamplesForVisit(VisitImpl visit)
    {
        TableInfo tinfoSpec = StudySchema.getInstance().getTableInfoSpecimen();
        SQLFragment specimenRowIdSelectSql = new SQLFragment("FROM " + tinfoSpec + " WHERE ").append(getVisitRangeSql(visit));

        SQLFragment deleteEventSql = new SQLFragment("DELETE FROM " +
                StudySchema.getInstance().getTableInfoSpecimenEvent() +
                " WHERE RowId IN (\n" +
                "SELECT Event.RowId FROM " +
                StudySchema.getInstance().getTableInfoSpecimenEvent() +
                " AS Event\n" +
                "LEFT OUTER JOIN study.Vial AS Vial ON\n" +
                "\tEvent.VialId = Vial.RowId\n" +
                "LEFT OUTER JOIN " +
                StudySchema.getInstance().getTableInfoSpecimen() +
                " AS Specimen ON\n" +
                "\tVial.SpecimenId = Specimen.RowId\n" +
                "WHERE Specimen.RowId IN (SELECT RowId ");
        deleteEventSql.append(specimenRowIdSelectSql);
        deleteEventSql.append("))");

        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(deleteEventSql);

        SQLFragment deleteVialSql = new SQLFragment("DELETE FROM " +
                StudySchema.getInstance().getTableInfoVial() +
                " WHERE RowId IN (\n" +
                "SELECT Vial.RowId FROM " +
                StudySchema.getInstance().getTableInfoVial() +
                " AS Vial\n" +
                "LEFT OUTER JOIN " +
                StudySchema.getInstance().getTableInfoSpecimen() +
                " AS Specimen ON\n" +
                "\tVial.SpecimenId = Specimen.RowId\n" +
                "WHERE Specimen.RowId IN (SELECT RowId ");
        deleteVialSql.append(specimenRowIdSelectSql);
        deleteVialSql.append("))");

        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(deleteVialSql);

        SQLFragment deleteSpecimenSql = new SQLFragment("DELETE ");
        deleteSpecimenSql.append(specimenRowIdSelectSql);

        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(deleteSpecimenSql);

        clearCaches(visit.getContainer());
    }

    private SQLFragment getVisitRangeSql(VisitImpl visit)
    {
        SQLFragment sql = new SQLFragment();
        TableInfo tinfoSpec = StudySchema.getInstance().getTableInfoSpecimen();
        Study study = StudyService.get().getStudy(visit.getContainer());
        if (null == study)
            throw new IllegalStateException("No study found.");

        if (TimepointType.VISIT == study.getTimepointType())
        {
            sql.append(tinfoSpec + ".VisitValue >= ? AND " + tinfoSpec + ".VisitValue <= ? AND " + tinfoSpec + ".Container = ?");
            sql.add(visit.getSequenceNumMin());
            sql.add(visit.getSequenceNumMax());
            sql.add(visit.getContainer());
        }
        else
        {
            // For date-based we need to get the range from ParticipantVisit
            ColumnInfo columnInfo = StudySchema.getInstance().getTableInfoParticipantVisit().getColumn("SequenceNum");
            Filter filter = new SimpleFilter(FieldKey.fromString("VisitRowId"), visit.getRowId());
            Sort sort = new Sort();
            sort.insertSortColumn(FieldKey.fromString("SequenceNum"), Sort.SortDirection.ASC);
            ArrayList<Double> visitValues = new TableSelector(columnInfo, filter, sort).getArrayList(Double.class);
            if (0 == visitValues.size())
                throw new IllegalStateException("Expected values from Select");

            sql.append(tinfoSpec + ".VisitValue >= ? AND " + tinfoSpec + ".VisitValue <= ? AND " + tinfoSpec + ".Container = ?");
            sql.add(visitValues.get(0));
            sql.add(visitValues.get(visitValues.size() - 1));
            sql.add(visit.getContainer());
        }
        return sql;
    }

    public void deleteSpecimen(@NotNull Specimen specimen) throws SQLException
    {
        SQLFragment sqlFragmentEvent = new SQLFragment("DELETE FROM " +
            StudySchema.getInstance().getTableInfoSpecimenEvent() + " WHERE VialId = ?");
        sqlFragmentEvent.add(specimen.getRowId());
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sqlFragmentEvent);

        SQLFragment sqlFragment = new SQLFragment("DELETE FROM " +
            StudySchema.getInstance().getTableInfoVial() + " WHERE RowId = ?");
        sqlFragment.add(specimen.getRowId());
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sqlFragment);

        clearCaches(specimen.getContainer());
    }

    public void deleteAllSampleData(Container c, Set<TableInfo> set) throws SQLException
    {
        // UNDONE: use transaction?
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(c);

        Table.delete(StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSampleRequestSpecimen());
        Table.delete(_requestEventHelper.getTableInfo(), containerFilter);
        assert set.add(_requestEventHelper.getTableInfo());
        Table.delete(_requestHelper.getTableInfo(), containerFilter);
        assert set.add(_requestHelper.getTableInfo());
        Table.delete(_requestStatusHelper.getTableInfo(), containerFilter);
        assert set.add(_requestStatusHelper.getTableInfo());
        Table.delete(_specimenEventHelper.getTableInfo(), containerFilter);
        assert set.add(_specimenEventHelper.getTableInfo());
        Table.delete(StudySchema.getInstance().getTableInfoVial(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoVial());
        Table.delete(StudySchema.getInstance().getTableInfoSpecimen(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimen());
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenAdditive(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimenAdditive());
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenDerivative(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimenDerivative());
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenPrimaryType(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimenPrimaryType());

        Table.delete(StudySchema.getInstance().getTableInfoSampleAvailabilityRule(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSampleAvailabilityRule());


        _requirementProvider.purgeContainer(c);
        assert set.add(StudySchema.getInstance().getTableInfoSampleRequestRequirement());
        assert set.add(StudySchema.getInstance().getTableInfoSampleRequestActor());

        DbSchema expSchema = ExperimentService.get().getSchema();
        TableInfo tinfoMaterial = expSchema.getTable("Material");
        containerFilter.addCondition("CpasType", StudyService.SPECIMEN_NAMESPACE_PREFIX);
        Table.delete(tinfoMaterial, containerFilter);

        // Views
        assert set.add(StudySchema.getInstance().getSchema().getTable("LockedSpecimens"));
        assert set.add(StudySchema.getInstance().getSchema().getTable("SpecimenSummary"));
        assert set.add(StudySchema.getInstance().getSchema().getTable("SpecimenDetail"));
        assert set.add(StudySchema.getInstance().getSchema().getTable("VialCounts"));
    }


    public void clearCaches(Container c)
    {
        _requestEventHelper.clearCache(c);
        _specimenDetailHelper.clearCache(c);
        _specimenEventHelper.clearCache(c);
        _requestHelper.clearCache(c);
        _requestStatusHelper.clearCache(c);
        DbCache.clear(StudySchema.getInstance().getTableInfoVial());
        StudyCache.clearCache(StudySchema.getInstance().getTableInfoSpecimenSummary(), c);
        StudyCache.clearCache(StudySchema.getInstance().getTableInfoSpecimenAdditive(), c);
        StudyCache.clearCache(StudySchema.getInstance().getTableInfoSpecimenDerivative(), c);
        StudyCache.clearCache(StudySchema.getInstance().getTableInfoSpecimenPrimaryType(), c);
        for (StudyImpl study : StudyManager.getInstance().getAncillaryStudies(c))
            clearCaches(study.getContainer());
    }

    public List<VisitImpl> getVisitsWithSpecimens(Container container, User user)
    {
        return getVisitsWithSpecimens(container, user, null);
    }

    public List<VisitImpl> getVisitsWithSpecimens(Container container, User user, CohortImpl cohort)
    {
        try
        {
            StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(container), user, true);
            TableInfo tinfo = schema.getTable(StudyQuerySchema.SIMPLE_SPECIMEN_TABLE_NAME);

            FieldKey visitKey = FieldKey.fromParts("Visit");
            Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(tinfo, Collections.singleton(visitKey));
            Collection<ColumnInfo> cols = new ArrayList<>();
            cols.add(colMap.get(visitKey));
            Set<FieldKey> unresolvedColumns = new HashSet<>();
            cols = QueryService.get().ensureRequiredColumns(tinfo, cols, null, null, unresolvedColumns);
            if (!unresolvedColumns.isEmpty())
                throw new IllegalStateException("Unable to resolve column(s): " + unresolvedColumns.toString());
            // generate our select SQL:
            SQLFragment specimenSql = Table.getSelectSQL(tinfo, cols, null, null);

            SQLFragment visitIdSQL = new SQLFragment("SELECT DISTINCT Visit FROM (" + specimenSql.getSQL() + ") SimpleSpecimenQuery");
            visitIdSQL.addAll(specimenSql.getParamsArray());

            List<Integer> visitIds = new ArrayList<>();
            ResultSet rs = null;
            try
            {
                rs = Table.executeQuery(StudySchema.getInstance().getSchema(), visitIdSQL);
                while (rs.next())
                    visitIds.add(rs.getInt(1));
            }
            finally
            {
                if (rs != null) try { rs.close(); } catch (SQLException e) { /* fall through */ }
            }

            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addInClause("RowId", visitIds);
            if (cohort != null)
                filter.addWhereClause("CohortId IS NULL OR CohortId = ?", new Object[] { cohort.getRowId() });
            return new TableSelector(StudySchema.getInstance().getTableInfoVisit(), filter, new Sort("DisplayOrder,SequenceNumMin")).getArrayList(VisitImpl.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static class SummaryByVisitType extends SpecimenCountSummary
    {
        private String _primaryType;
        private String _derivative;
        private String _additive;
        private Long _participantCount;
        private Set<String> _participantIds;

        public String getPrimaryType()
        {
            return _primaryType;
        }

        public void setPrimaryType(String primaryType)
        {
            _primaryType = primaryType;
        }

        public String getDerivative()
        {
            return _derivative;
        }

        public void setDerivative(String derivative)
        {
            _derivative = derivative;
        }

        public Long getParticipantCount()
        {
            return _participantCount;
        }

        public void setParticipantCount(Long participantCount)
        {
            _participantCount = participantCount;
        }

        public Set<String> getParticipantIds()
        {
            return _participantIds;
        }

        public void setParticipantIds(Set<String> participantIds)
        {
            _participantIds = participantIds;
        }

        public String getAdditive()
        {
            return _additive;
        }

        public void setAdditive(String additive)
        {
            _additive = additive;
        }
    }

    public static class RequestSummaryByVisitType extends SummaryByVisitType
    {
        private Integer _destinationSiteId;
        private String _siteLabel;

        public Integer getDestinationSiteId()
        {
            return _destinationSiteId;
        }

        public void setDestinationSiteId(Integer destinationSiteId)
        {
            _destinationSiteId = destinationSiteId;
        }

        public String getSiteLabel()
        {
            return _siteLabel;
        }

        public void setSiteLabel(String siteLabel)
        {
            _siteLabel = siteLabel;
        }
    }

    public SummaryByVisitType[] getSpecimenSummaryByVisitType(Container container, User user, boolean includeParticipantGroups, SpecimenTypeLevel level) throws SQLException
    {
        return getSpecimenSummaryByVisitType(container, user, null, includeParticipantGroups, level);
    }

    public static class SpecimenTypeBeanProperty
    {
        private FieldKey _typeKey;
        private String _beanProperty;
        private SpecimenTypeLevel _level;

        public SpecimenTypeBeanProperty(FieldKey typeKey, String beanProperty, SpecimenTypeLevel level)
        {
            _typeKey = typeKey;
            _beanProperty = beanProperty;
            _level = level;
        }

        public FieldKey getTypeKey()
        {
            return _typeKey;
        }

        public String getBeanProperty()
        {
            return _beanProperty;
        }

        public SpecimenTypeLevel getLevel()
        {
            return _level;
        }
    }

    public enum SpecimenTypeLevel
    {
        PrimaryType()
        {
            public List<SpecimenTypeBeanProperty> getGroupingColumns()
            {
                List<SpecimenTypeBeanProperty> list = new ArrayList<>();
                list.add(new SpecimenTypeBeanProperty(FieldKey.fromParts("PrimaryType", "Description"), "primaryType", this));
                return list;
            }

            public String[] getTitleHierarchy(SummaryByVisitType summary)
            {
                return new String[] { summary.getPrimaryType() };
            }

            public String getLabel()
            {
                return "Primary Type";
            }},
        Derivative()
        {
            public List<SpecimenTypeBeanProperty> getGroupingColumns()
            {
                List<SpecimenTypeBeanProperty> parent = SpecimenTypeLevel.PrimaryType.getGroupingColumns();
                parent.add(new SpecimenTypeBeanProperty(FieldKey.fromParts("DerivativeType", "Description"), "derivative", this));
                return parent;
            }

            public String[] getTitleHierarchy(SummaryByVisitType summary)
            {
                return new String[] { summary.getPrimaryType(), summary.getDerivative() };
            }
            public String getLabel()
            {
                return "Derivative";
            }},
        Additive()
        {
            public List<SpecimenTypeBeanProperty> getGroupingColumns()
            {
                List<SpecimenTypeBeanProperty> parent = SpecimenTypeLevel.Derivative.getGroupingColumns();
                parent.add(new SpecimenTypeBeanProperty(FieldKey.fromParts("AdditiveType", "Description"), "additive", this));
                return parent;
            }

            public String[] getTitleHierarchy(SummaryByVisitType summary)
            {
                return new String[] { summary.getPrimaryType(), summary.getDerivative(), summary.getAdditive() };
            }

            public String getLabel()
            {
                return "Additive";
            }};

        public abstract String[] getTitleHierarchy(SummaryByVisitType summary);
        public abstract List<SpecimenTypeBeanProperty> getGroupingColumns();
        public abstract String getLabel();
    }

    private class SpecimenDetailQueryHelper
    {
        private SQLFragment _viewSql;
        private String _typeGroupingColumns;
        private Map<String, SpecimenTypeBeanProperty> _aliasToTypePropertyMap;

        private SpecimenDetailQueryHelper(SQLFragment viewSql, String typeGroupingColumns, Map<String, SpecimenTypeBeanProperty> aliasToTypePropertyMap)
        {
            _viewSql = viewSql;
            _typeGroupingColumns = typeGroupingColumns;
            _aliasToTypePropertyMap = aliasToTypePropertyMap;
        }

        public SQLFragment getViewSql()
        {
            return _viewSql;
        }

        public String getTypeGroupingColumns()
        {
            return _typeGroupingColumns;
        }

        public Map<String, SpecimenTypeBeanProperty> getAliasToTypePropertyMap()
        {
            return _aliasToTypePropertyMap;
        }
    }

    private SpecimenDetailQueryHelper getSpecimenDetailQueryHelper(Container container, User user,
                                                                   CustomView baseView, SimpleFilter specimenDetailFilter,
                                                                   SpecimenTypeLevel level)
    {
        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(container), user, true);
        TableInfo tinfo = schema.getTable("SpecimenDetail");
        Map<String, SpecimenTypeBeanProperty> aliasToTypeProperty = new LinkedHashMap<>();

        Collection<FieldKey> columns = new HashSet<>();
        if (baseView != null)
        {
            // copy our saved view filter into our SimpleFilter via an ActionURL (yuck...)
            ActionURL url = new ActionURL();
            baseView.applyFilterAndSortToURL(url, "mockDataRegion");
            specimenDetailFilter.addUrlFilters(url, "mockDataRegion");
        }

        // Build a list fo FieldKeys for all the columns that we must select,
        // regardless of whether they're in the selected specimen view.  We need to ask the view which
        // columns are required in case there's a saved filter on a column outside the primary table:
        columns.add(FieldKey.fromParts("Container"));
        columns.add(FieldKey.fromParts("Visit"));
        columns.add(FieldKey.fromParts("SequenceNum"));
        columns.add(FieldKey.fromParts("LockedInRequest"));
        columns.add(FieldKey.fromParts("GlobalUniqueId"));
        columns.add(FieldKey.fromParts(StudyService.get().getSubjectColumnName(container)));
        if (StudyManager.getInstance().showCohorts(container, schema.getUser()))
            columns.add(FieldKey.fromParts("CollectionCohort"));
        columns.add(FieldKey.fromParts("Volume"));
        if (level != null)
        {
            for (SpecimenTypeBeanProperty typeProperty : level.getGroupingColumns())
                columns.add(typeProperty.getTypeKey());
        }

        // turn our fieldkeys into columns:
        Collection<ColumnInfo> cols = new ArrayList<>();
        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(tinfo, columns);
        Set<FieldKey> unresolvedColumns = new HashSet<>();
        cols.addAll(colMap.values());
        cols = QueryService.get().ensureRequiredColumns(tinfo, cols, specimenDetailFilter, null, unresolvedColumns);
        if (!unresolvedColumns.isEmpty())
            throw new IllegalStateException("Unable to resolve column(s): " + unresolvedColumns.toString());
        // generate our select SQL:
        SQLFragment viewSql = Table.getSelectSQL(tinfo, cols, specimenDetailFilter, null);

        // save off the aliases for our grouping columns, so we can group by them later:
        String groupingColSql = null;
        if (level != null)
        {
            StringBuilder builder = new StringBuilder();
            String sep = "";
            for (SpecimenTypeBeanProperty typeProperty : level.getGroupingColumns())
            {
                ColumnInfo col = colMap.get(typeProperty.getTypeKey());
                builder.append(sep).append(col.getAlias());
                sep = ", ";
                aliasToTypeProperty.put(col.getAlias(), typeProperty);
            }
            groupingColSql = builder.toString();
        }
        return new SpecimenDetailQueryHelper(viewSql, groupingColSql, aliasToTypeProperty);
    }


    public SummaryByVisitType[] getSpecimenSummaryByVisitType(Container container, User user, SimpleFilter specimenDetailFilter,
            boolean includeParticipantGroups, SpecimenTypeLevel level) throws SQLException
    {
        return getSpecimenSummaryByVisitType(container, user, specimenDetailFilter, includeParticipantGroups, level, null);
    }


    public SummaryByVisitType[] getSpecimenSummaryByVisitType(Container container, User user, SimpleFilter specimenDetailFilter,
        boolean includeParticipantGroups, SpecimenTypeLevel level,
        CustomView baseView) throws SQLException
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }

        SpecimenDetailQueryHelper viewSqlHelper = getSpecimenDetailQueryHelper(container, user, baseView, specimenDetailFilter, level);

        String perPtidSpecimenSQL = "\t-- Inner SELECT gets the number of vials per participant/visit/type:\n" +
            "\tSELECT InnerView.Container, InnerView.Visit, " + viewSqlHelper.getTypeGroupingColumns() + ",\n" +
            "\tInnerView." + StudyService.get().getSubjectColumnName(container) + ", COUNT(*) AS VialCount, SUM(InnerView.Volume) AS PtidVolume \n" +
            "FROM (\n" + viewSqlHelper.getViewSql().getSQL() + "\n) InnerView\n" +
            "\tGROUP BY InnerView.Container, InnerView." + StudyService.get().getSubjectColumnName(container) +
                ", InnerView.Visit, " + viewSqlHelper.getTypeGroupingColumns() + "\n";

        StringBuilder sql = new StringBuilder("-- Outer grouping allows us to count participants AND sum vial counts:\n" +
            "SELECT VialData.Visit AS Visit, " + viewSqlHelper.getTypeGroupingColumns() + ", COUNT(*) as ParticipantCount, \n" +
            "SUM(VialData.VialCount) AS VialCount, SUM(VialData.PtidVolume) AS TotalVolume FROM \n" +
            "(\n" + perPtidSpecimenSQL + ") AS VialData\n" +
            "GROUP BY Visit, " + viewSqlHelper.getTypeGroupingColumns() + "\n" +
            "ORDER BY " + viewSqlHelper.getTypeGroupingColumns() + ", Visit");

        ResultSet rs = null;
        List<SummaryByVisitType> ret;
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql.toString(), viewSqlHelper.getViewSql().getParamsArray());
            ret = new ArrayList<>();
            while (rs.next())
            {
                SummaryByVisitType summary = new SummaryByVisitType();
                if (rs.getObject("Visit") != null)
                    summary.setVisit(rs.getInt("Visit"));
                summary.setTotalVolume(rs.getDouble("TotalVolume"));
                Double vialCount = rs.getDouble("VialCount");
                summary.setVialCount(vialCount.longValue());
                Double participantCount = rs.getDouble("ParticipantCount");
                summary.setParticipantCount(participantCount.longValue());
                for (Map.Entry<String, SpecimenTypeBeanProperty> typeProperty : viewSqlHelper.getAliasToTypePropertyMap().entrySet())
                {
                    String value = rs.getString(typeProperty.getKey());
                    PropertyUtils.setProperty(summary, typeProperty.getValue().getBeanProperty(), value);
                }
                ret.add(summary);
            }
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }
        SummaryByVisitType[] summaries = ret.toArray(new SummaryByVisitType[ret.size()]);

        if (includeParticipantGroups)
            setSummaryParticipantGroups(perPtidSpecimenSQL, viewSqlHelper.getViewSql().getParamsArray(),
                    viewSqlHelper.getAliasToTypePropertyMap(), summaries, StudyService.get().getSubjectColumnName(container), "Visit");
        return summaries;
    }

    private String getPtidListKey(Integer visit, String primaryType, String derivativeType, String additiveType)
    {
        return visit + "/" + primaryType + "/" +
            (derivativeType != null ? derivativeType : "all") +
            (additiveType != null ? additiveType : "all");
    }

    public LocationImpl[] getSitesWithRequests(Container container)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM study.site WHERE rowid IN\n" +
                "(SELECT destinationsiteid FROM study.samplerequest WHERE container = ?)\n" +
                "AND container = ? ORDER BY label", container.getId(), container.getId());

        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArray(LocationImpl.class);
    }

    public LocationImpl[] getSites(Container container)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM study.site WHERE Container = ? ORDER BY label", container.getId());

        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArray(LocationImpl.class);
    }

    public Set<LocationImpl> getEnrollmentSitesWithRequests(Container container)
    {
        SQLFragment sql = new SQLFragment("SELECT Participant.EnrollmentSiteId FROM study.SpecimenDetail AS Specimen, " +
                "study.SampleRequestSpecimen AS RequestSpecimen, \n" +
                "study.SampleRequest AS Request, study.SampleRequestStatus AS Status,\n" +
                "study.Participant AS Participant\n" +
                "WHERE Request.Container = Status.Container AND\n" +
                "\tRequest.StatusId = Status.RowId AND\n" +
                "\tRequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                "\tRequestSpecimen.Container = Request.Container AND\n" +
                "\tSpecimen.Container = RequestSpecimen.Container AND\n" +
                "\tSpecimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\tParticipant.EnrollmentSiteId IS NOT NULL AND\n" +
                "\tParticipant.Container = Specimen.Container AND\n" +
                "\tParticipant.ParticipantId = Specimen.Ptid AND\n" +
                "\tStatus.SpecimensLocked = ? AND\n" +
                "\tRequest.Container = ?", Boolean.TRUE, container.getId());

        return getSitesWithIdSql(container, "EnrollmentSiteId", sql);
    }

    public Set<LocationImpl> getEnrollmentSitesWithSpecimens(Container container)
    {
        SQLFragment sql = new SQLFragment("SELECT EnrollmentSiteId FROM study.SpecimenDetail AS Specimen, study.Participant AS Participant\n" +
                "WHERE Specimen.Ptid = Participant.ParticipantId AND\n" +
                "\tParticipant.EnrollmentSiteId IS NOT NULL AND\n" +
                "\tSpecimen.Container = Participant.Container AND\n" +
                "\tSpecimen.Container = ?\n" +
                "GROUP BY EnrollmentSiteId", container.getId());

        return getSitesWithIdSql(container, "EnrollmentSiteId", sql);
    }

    private Set<LocationImpl> getSitesWithIdSql(Container container, String idColumnName, SQLFragment sql)
    {
        ResultSet rs = null;
        try
        {
            Set<LocationImpl> locations = new TreeSet<>(new Comparator<LocationImpl>()
            {
                public int compare(LocationImpl s1, LocationImpl s2)
                {
                    if (s1 == null && s2 == null)
                        return 0;
                    if (s1 == null)
                        return -1;
                    if (s2 == null)
                        return 1;
                    return s1.getLabel().compareTo(s2.getLabel());
                }
            });

            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql);
            while (rs.next())
            {
                // try getObject first to see if we have a value for our row; getInt will coerce the null to
                // zero, which could (theoretically) be a valid site ID.
                if (rs.getObject(idColumnName) == null)
                    locations.add(null);
                else
                    locations.add(StudyManager.getInstance().getLocation(container, rs.getInt(idColumnName)));
            }
            return locations;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }
    }


    public static class SummaryByVisitParticipant extends SpecimenCountSummary
    {
        private String _participantId;
        private String _cohort;

        public String getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String participantId)
        {
            _participantId = participantId;
        }

        public String getCohort()
        {
            return _cohort;
        }

        public void setCohort(String cohort)
        {
            _cohort = cohort;
        }
    }

    public SummaryByVisitParticipant[] getParticipantSummaryByVisitType(Container container, User user,
                                SimpleFilter specimenDetailFilter, CustomView baseView, CohortFilter.Type cohortType) throws SQLException
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }
        SpecimenDetailQueryHelper sqlHelper = getSpecimenDetailQueryHelper(container, user, baseView, specimenDetailFilter, null);
        String subjectCol = StudyService.get().getSubjectColumnName(container);
        SQLFragment cohortJoinClause = null;
        switch (cohortType)
        {
            case DATA_COLLECTION:
                cohortJoinClause = new SQLFragment("LEFT OUTER JOIN study.ParticipantVisit ON\n " +
                        "\tSpecimenQuery.SequenceNum = study.ParticipantVisit.SequenceNum AND\n" +
                        "\tSpecimenQuery." + subjectCol + " = study.ParticipantVisit.ParticipantId AND\n" +
                        "\tSpecimenQuery.Container = study.ParticipantVisit.Container\n" +
                        "LEFT OUTER JOIN study.Cohort ON \n" +
                        "\tstudy.ParticipantVisit.CohortId = study.Cohort.RowId AND\n" +
                        "\tstudy.ParticipantVisit.Container = study.Cohort.Container\n");
                break;
            case PTID_CURRENT:
                cohortJoinClause = new SQLFragment("LEFT OUTER JOIN study.Cohort ON \n" +
                        "\tstudy.Participant.CurrentCohortId = study.Cohort.RowId AND\n" +
                        "\tstudy.Participant.Container = study.Cohort.Container\n");
                break;
            case PTID_INITIAL:
                cohortJoinClause = new SQLFragment("LEFT OUTER JOIN study.Cohort ON \n" +
                        "\tstudy.Participant.InitialCohortId = study.Cohort.RowId AND\n" +
                        "\tstudy.Participant.Container = study.Cohort.Container\n");
                break;
        }

        SQLFragment ptidSpecimenSQL = new SQLFragment();
        ptidSpecimenSQL.append("SELECT SpecimenQuery.Visit AS Visit, SpecimenQuery." + subjectCol + " AS ParticipantId,\n" +
                "COUNT(*) AS VialCount, study.Cohort.Label AS Cohort, SUM(SpecimenQuery.Volume) AS TotalVolume\n" +
                "FROM (");
        ptidSpecimenSQL.append(sqlHelper.getViewSql());
        ptidSpecimenSQL.append(") AS SpecimenQuery\n" +
                "LEFT OUTER JOIN study.Participant ON\n" +
                "\tSpecimenQuery." + subjectCol + " = study.Participant.ParticipantId AND\n" +
                "\tSpecimenQuery.Container = study.Participant.Container\n");
        ptidSpecimenSQL.append(cohortJoinClause);
        ptidSpecimenSQL.append("GROUP BY study.Cohort.Label, SpecimenQuery." + subjectCol + ", Visit\n" +
                "ORDER BY study.Cohort.Label, SpecimenQuery." + subjectCol + ", Visit");

        return Table.executeQuery(StudySchema.getInstance().getSchema(),
                ptidSpecimenSQL, SummaryByVisitParticipant.class);
    }


    public RequestSummaryByVisitType[] getRequestSummaryBySite(Container container, User user, SimpleFilter specimenDetailFilter, boolean includeParticipantGroups, SpecimenTypeLevel level, CustomView baseView, boolean completeRequestsOnly) throws SQLException
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }
        SpecimenDetailQueryHelper sqlHelper = getSpecimenDetailQueryHelper(container, user, baseView, specimenDetailFilter, level);

        String subjectCol = StudyService.get().getSubjectColumnName(container);
        String sql = "SELECT Specimen.Container,\n" +
                "Specimen." + subjectCol + ",\n" +
                "Request.DestinationSiteId,\n" +
                "Site.Label AS SiteLabel,\n" +
                "Visit AS Visit,\n" +
                 sqlHelper.getTypeGroupingColumns() + ", COUNT(*) AS VialCount, SUM(Volume) AS TotalVolume\n" +
                "FROM (" + sqlHelper.getViewSql().getSQL() + ") AS Specimen\n" +
                "JOIN study.SampleRequestSpecimen AS RequestSpecimen ON \n" +
                "\tSpecimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\tSpecimen.Container = RequestSpecimen.Container\n" +
                "JOIN study.SampleRequest AS Request ON\n" +
                "\tRequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                "\tRequestSpecimen.Container = Request.Container\n" +
                "JOIN study.Site AS Site ON\n" +
                "\tSite.Container = Request.Container AND\n" +
                "\tSite.RowId = Request.DestinationSiteId\n" +
                "JOIN study.SampleRequestStatus AS Status ON\n" +
                "\tStatus.Container = Request.Container AND\n" +
                "\tStatus.RowId = Request.StatusId and Status.SpecimensLocked = ?\n" +
                (completeRequestsOnly ? "\tAND Status.FinalState = ?\n" : "") +
                "GROUP BY Specimen.Container, Specimen." + subjectCol + ", Site.Label, DestinationSiteId, " + sqlHelper.getTypeGroupingColumns() + ", Visit\n" +
                "ORDER BY Specimen.Container, Specimen." + subjectCol + ", Site.Label, DestinationSiteId, " + sqlHelper.getTypeGroupingColumns() + ", Visit";

        Object[] params = new Object[sqlHelper.getViewSql().getParamsArray().length + 1 + (completeRequestsOnly ? 1 : 0)];
        System.arraycopy(sqlHelper.getViewSql().getParamsArray(), 0, params, 0, sqlHelper.getViewSql().getParamsArray().length);
        params[params.length - 1] = Boolean.TRUE;
        if (completeRequestsOnly)
            params[params.length - 2] = Boolean.TRUE;
        ResultSet rs = null;
        List<SummaryByVisitType> ret;
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql, params);
            ret = new ArrayList<>();
            while (rs.next())
            {
                RequestSummaryByVisitType summary = new RequestSummaryByVisitType();
                summary.setDestinationSiteId(rs.getInt("DestinationSiteId"));
                summary.setSiteLabel(rs.getString("SiteLabel"));
                summary.setVisit(rs.getInt("Visit"));
                summary.setTotalVolume(rs.getDouble("TotalVolume"));
                Double vialCount = rs.getDouble("VialCount");
                summary.setVialCount(vialCount.longValue());
                for (Map.Entry<String, SpecimenTypeBeanProperty> typeProperty : sqlHelper.getAliasToTypePropertyMap().entrySet())
                {
                    String value = rs.getString(typeProperty.getKey());
                    PropertyUtils.setProperty(summary, typeProperty.getValue().getBeanProperty(), value);
                }
                ret.add(summary);
            }
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }
        RequestSummaryByVisitType[] summaries = ret.toArray(new RequestSummaryByVisitType[ret.size()]);

        if (includeParticipantGroups)
            setSummaryParticipantGroups(sql, params, null, summaries, subjectCol, "Visit");
        return summaries;
    }

    private static final int GET_COMMENT_BATCH_SIZE = 1000;
    public Map<Specimen, SpecimenComment> getSpecimenComments(List<Specimen> vials) throws SQLException
    {
        if (vials == null || vials.size() == 0)
            return Collections.emptyMap();

        Container container = vials.get(0).getContainer();
        Map<Specimen, SpecimenComment> result = new HashMap<>();
        int offset = 0;
        while (offset < vials.size())
        {
            Map<String, Specimen> idToVial = new HashMap<>();
            for (int current = offset; current < offset + GET_COMMENT_BATCH_SIZE && current < vials.size(); current++)
            {
                Specimen vial = vials.get(current);
                idToVial.put(vial.getGlobalUniqueId(), vial);
                if (!container.equals(vial.getContainer()))
                    throw new IllegalArgumentException("All specimens must be from the same container");
            }

            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addInClause("GlobalUniqueId", idToVial.keySet());
            SpecimenComment[]  comments = Table.select(StudySchema.getInstance().getTableInfoSpecimenComment(), Table.ALL_COLUMNS, filter, null, SpecimenComment.class);

            for (SpecimenComment comment : comments)
            {
                Specimen vial = idToVial.get(comment.getGlobalUniqueId());
                result.put(vial, comment);
            }
            offset += GET_COMMENT_BATCH_SIZE;
        }
        return result;
    }

    public SpecimenComment getSpecimenCommentForVial(Container container, String globalUniqueId) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition("GlobalUniqueId", globalUniqueId);

        return new TableSelector(StudySchema.getInstance().getTableInfoSpecimenComment(), filter, null).getObject(SpecimenComment.class);
    }

    public SpecimenComment getSpecimenCommentForVial(Specimen vial) throws SQLException
    {
        return getSpecimenCommentForVial(vial.getContainer(), vial.getGlobalUniqueId());
    }

    public SpecimenComment[] getSpecimenCommentForSpecimen(Container container, String specimenHash) throws SQLException
    {
        return getSpecimenCommentForSpecimens(container, Collections.singleton(specimenHash));
    }

    public SpecimenComment[] getSpecimenCommentForSpecimens(Container container, Collection<String> specimenHashes) throws SQLException
    {
        SimpleFilter hashFilter = SimpleFilter.createContainerFilter(container);
        hashFilter.addInClause("SpecimenHash", specimenHashes);
        return Table.select(StudySchema.getInstance().getTableInfoSpecimenComment(), Table.ALL_COLUMNS,
                hashFilter, new Sort("GlobalUniqueId"), SpecimenComment.class);
    }

    private boolean safeComp(Object a, Object b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }

    private void auditSpecimenComment(User user, Specimen vial, String oldComment, String newComment, boolean prevConflictState, boolean newConflictState)
    {
        String verb = "updated";
        if (oldComment == null)
            verb = "added";
        else if (newComment == null)
            verb = "deleted";
        String message = "";
        if (!safeComp(oldComment, newComment))
        {
            message += "Comment " + verb + ".\n";
            if (oldComment != null)
                message += "Previous value: " + oldComment + "\n";
            if (newComment != null)
                message += "New value: " + newComment + "\n";
        }

        if (!safeComp(prevConflictState, newConflictState))
        {
            message = "QC alert flag changed.\n";
            if (oldComment != null)
                message += "Previous value: " + prevConflictState + "\n";
            if (newComment != null)
                message += "New value: " + newConflictState + "\n";
        }

        AuditLogService.get().addEvent(user, vial.getContainer(),
                SpecimenCommentAuditViewFactory.SPECIMEN_COMMENT_EVENT, vial.getGlobalUniqueId(), message);
    }

    public SpecimenComment setSpecimenComment(User user, Specimen vial, String commentText, boolean qualityControlFlag, boolean qualityControlFlagForced) throws SQLException
    {
        TableInfo commentTable = StudySchema.getInstance().getTableInfoSpecimenComment();
        DbScope scope = commentTable.getSchema().getScope();
        SpecimenComment comment = getSpecimenCommentForVial(vial);
        boolean clearComment = commentText == null && !qualityControlFlag && !qualityControlFlagForced;
        try
        {
            scope.ensureTransaction();
            if (clearComment)
            {
                if (comment != null)
                {
                    Table.delete(commentTable, comment.getRowId());
                    auditSpecimenComment(user, vial, comment.getComment(), null, comment.isQualityControlFlag(), false);
                }
                scope.commitTransaction();
                return null;
            }
            else
            {
                if (comment != null)
                {
                    String prevComment = comment.getComment();
                    boolean prevConflictState = comment.isQualityControlFlag();
                    comment.setComment(commentText);
                    comment.setQualityControlFlag(qualityControlFlag);
                    comment.setQualityControlFlagForced(qualityControlFlagForced);
                    comment.beforeUpdate(user);
                    SpecimenComment updated = Table.update(user, commentTable, comment, comment.getRowId());
                    auditSpecimenComment(user, vial, prevComment, updated.getComment(), prevConflictState, updated.isQualityControlFlag());
                    scope.commitTransaction();
                    return updated;
                }
                else
                {
                    comment = new SpecimenComment();
                    comment.setGlobalUniqueId(vial.getGlobalUniqueId());
                    comment.setSpecimenHash(vial.getSpecimenHash());
                    comment.setComment(commentText);
                    comment.setQualityControlFlag(qualityControlFlag);
                    comment.setQualityControlFlagForced(qualityControlFlagForced);
                    comment.beforeInsert(user, vial.getContainer().getId());
                    SpecimenComment inserted = Table.insert(user, commentTable, comment);
                    auditSpecimenComment(user, vial, null, inserted.getComment(), false, comment.isQualityControlFlag());
                    scope.commitTransaction();
                    return inserted;
                }
            }
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private void setSummaryParticipantGroups(String sql, Object[] paramArray, Map<String, SpecimenTypeBeanProperty> aliasToTypeProperty,
                                           SummaryByVisitType[] summaries, String ptidColumnName, String visitValueColumnName) throws SQLException
    {
        Table.TableResultSet rs = null;
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql, paramArray);
            Map<String, Set<String>> cellToPtidSet = new HashMap<>();
            while (rs.next())
            {
                String ptid = rs.getString(ptidColumnName);
                Integer visit = rs.getInt(visitValueColumnName);
                String primaryType = null;
                String derivative = null;
                String additive = null;
                for (Map.Entry<String, SpecimenTypeBeanProperty> entry : aliasToTypeProperty.entrySet())
                {
                    switch (entry.getValue().getLevel())
                    {
                        case PrimaryType:
                            primaryType = rs.getString(entry.getKey());
                            break;
                        case Derivative:
                            derivative = rs.getString(entry.getKey());
                            break;
                        case Additive:
                            additive = rs.getString(entry.getKey());
                            break;
                    }
                }
                String key = getPtidListKey(visit, primaryType, derivative, additive);

                Set<String> ptids = cellToPtidSet.get(key);
                if (ptids == null)
                {
                    ptids = new TreeSet<>();
                    cellToPtidSet.put(key, ptids);
                }
                ptids.add(ptid != null ? ptid : "[unknown]");
            }

            for (SummaryByVisitType summary : summaries)
            {
                Integer visit = summary.getVisit();
                String key = getPtidListKey(visit, summary.getPrimaryType(), summary.getDerivative(), summary.getAdditive());
                Set<String> ptids = cellToPtidSet.get(key);
                summary.setParticipantIds(ptids);
            }
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }

    }

    private class GroupedValueColumnHelper
    {
        private String _viewColumnName;
        private String _sqlColumnName;
        private String _urlFilterName;
        private String _joinColumnName;

        public GroupedValueColumnHelper(String sqlColumnName, String viewColumnName, String urlFilterName, String joinColumnName)
        {
            _sqlColumnName = sqlColumnName;
            _viewColumnName = viewColumnName;
            _urlFilterName = urlFilterName;
            _joinColumnName = joinColumnName;
        }

        public String getViewColumnName()
        {
            return _viewColumnName;
        }

        public String getSqlColumnName()
        {
            return _sqlColumnName;
        }

        public String getUrlFilterName()
        {
            return _urlFilterName;
        }

        public String getJoinColumnName()
        {
            return _joinColumnName;
        }

        public FieldKey getFieldKey()
        {
            // constructs FieldKey whether it needs join or not
            if (null == _joinColumnName)
                return FieldKey.fromString(_viewColumnName);
            return FieldKey.fromParts(_sqlColumnName, _joinColumnName);
        }
    }

    // Map "ViewColumnName" name to object with sql column name and url filter name
    private final Map<String, GroupedValueColumnHelper> _groupedValueAllowedColumnMap = new HashMap<>();

    private void initGroupedValueAllowedColumnMap()
    {                                                                                       //    sqlColumnName    viewColumnName   urlFilterName          joinColumnName
        _groupedValueAllowedColumnMap.put("Primary Type",           new GroupedValueColumnHelper("PrimaryTypeId", "PrimaryType", "PrimaryType/Description", "PrimaryType"));
        _groupedValueAllowedColumnMap.put("Derivative Type",        new GroupedValueColumnHelper("DerivativeTypeId", "DerivativeType", "DerivativeType/Description",  "Derivative"));
        _groupedValueAllowedColumnMap.put("Additive Type",          new GroupedValueColumnHelper("AdditiveTypeId", "AdditiveType", "AdditiveType/Description",  "Additive"));
        _groupedValueAllowedColumnMap.put("Derivative Type2",       new GroupedValueColumnHelper("DerivativeTypeId2", "DerivativeType2", "DerivativeType2/Description",  "Derivative"));
        _groupedValueAllowedColumnMap.put("Sub Additive Derivative",new GroupedValueColumnHelper("SubAdditiveDerivative", "SubAdditiveDerivative", "SubAdditiveDerivative", null));
        _groupedValueAllowedColumnMap.put("Clinic",                 new GroupedValueColumnHelper("originatinglocationid", "Clinic", "Clinic/Label", "Label"));
        _groupedValueAllowedColumnMap.put("Processing Location",    new GroupedValueColumnHelper("ProcessingLocation", "ProcessingLocation", "ProcessingLocation/Label", "Label"));
        _groupedValueAllowedColumnMap.put("Protocol Number",        new GroupedValueColumnHelper("ProtocolNumber", "ProtocolNumber", "ProtocolNumber", null));
        _groupedValueAllowedColumnMap.put("Tube Type",              new GroupedValueColumnHelper("TubeType", "TubeType", "TubeType", null));
        _groupedValueAllowedColumnMap.put("Site Name",              new GroupedValueColumnHelper("CurrentLocation", "SiteName", "SiteName/Label", "Label"));
        _groupedValueAllowedColumnMap.put("Available",              new GroupedValueColumnHelper("Available", "Available", "Available", null));
        _groupedValueAllowedColumnMap.put("Freezer",                new GroupedValueColumnHelper("Freezer", "Freezer", "Freezer", null));
        _groupedValueAllowedColumnMap.put("Fr Container",           new GroupedValueColumnHelper("Fr_Container", "Fr_Container", "Fr_Container", null));
        _groupedValueAllowedColumnMap.put("Fr Position",            new GroupedValueColumnHelper("Fr_Position", "Fr_Position", "Fr_Position", null));
        _groupedValueAllowedColumnMap.put("Fr Level1",              new GroupedValueColumnHelper("Fr_Level1", "Fr_Level1", "Fr_Level1", null));
        _groupedValueAllowedColumnMap.put("Fr Level2",              new GroupedValueColumnHelper("Fr_Level2", "Fr_Level2", "Fr_Level2", null));
    }

    public Map<String, GroupedValueColumnHelper> getGroupedValueAllowedMap()
    {
        return _groupedValueAllowedColumnMap;
    }

    public String[] getGroupedValueAllowedColumns()
    {
        Set<String> keySet = _groupedValueAllowedColumnMap.keySet();
        String[] allowedColumns = keySet.toArray(new String[keySet.size()]);
        Arrays.sort(allowedColumns, new ComparableComparator<String>());
        return allowedColumns;
    }

    private class GroupedValueFilter
    {
        private String _viewColumnName;
        private String _filterValueName;

        public GroupedValueFilter()
        {
        }

        public String getViewColumnName()
        {
            return _viewColumnName;
        }

        public String getFilterValueName()
        {
            return _filterValueName;
        }

        public void setFilterValueName(String filterValueName)
        {
            _filterValueName = filterValueName;
        }

        public void setViewColumnName(String viewColumnName)
        {
            _viewColumnName = viewColumnName;
        }
    }

    private class GroupedResults
    {
        public String viewName;
        public String urlFilterName;
        public String labelValue;
        public long count;
        public Map<String, GroupedResults> childGroupedResultsMap;
    }

    private static String getGroupedValuesCacheKey(Container container)
    {
        return container.getId();
    }

    public Map<String, Object> getGroupedValuesForColumn(Container container, String[] grouping)
    {
        // ColumnName and filter names are "QueryView" names; map them to actual table names before building query
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study == null)
            return null;

        TableInfo tableInfo = StudySchema.getInstance().getTableInfoSpecimenDetail();
        String cacheKey = getGroupedValuesCacheKey(container);
        for (String g : grouping)
        {
            if (!StringUtils.isNotBlank(g))
                break;      // Grouping may have null entries for groupBys that are not chosen to be used
            cacheKey += "/" + g;
        }
        Map<String, Object> groupedValue = (Map<String, Object>) DbCache.get(tableInfo, cacheKey);
        if (null != groupedValue)
            return groupedValue;

        Table.TableResultSet resultSet = null;
        try
        {
            QueryService queryService = QueryService.get();
            List<FieldKey> fieldKeys = new ArrayList<>();
            for (String aGrouping : grouping)
            {
                if (!StringUtils.isNotBlank(aGrouping))
                    break;      // Grouping may have null/blank entries for groupBys that are not chosen to be used
                GroupedValueColumnHelper columnHelper = getGroupedValueAllowedMap().get(aGrouping);
                FieldKey fieldKey = columnHelper.getFieldKey();
                fieldKeys.add(fieldKey);
            }

            // Basic SQL with joins
            Map<FieldKey, ColumnInfo> columnMap = queryService.getColumns(tableInfo, fieldKeys);

            // Container filter
            Filter containerFilter = null;
            if (study.isAncillaryStudy())
            {
                List<String> containerIds = new ArrayList<>(2);
                containerIds.add(container.getId());
                containerIds.add(study.getSourceStudy().getContainer().getId());
                SimpleFilter.FilterClause inClause = new SimpleFilter.InClause(FieldKey.fromString("Container"), containerIds);

                String[] ptids = StudyManager.getInstance().getParticipantIds(study);
                List<String> participantIds = new ArrayList<>(ptids.length);
                if (ptids == null || ptids.length == 0)
                    participantIds.add("NULL");
                else
                {
                    Collections.addAll(participantIds, ptids);
                }
                SimpleFilter.FilterClause inClause1 = new SimpleFilter.InClause(FieldKey.fromString("PTID"), participantIds);
                SimpleFilter.FilterClause andClause = new SimpleFilter.AndClause(inClause, inClause1);
                containerFilter = new SimpleFilter(andClause);
            }
            else
            {
                containerFilter = new SimpleFilter(FieldKey.fromString("Container"), container.getId());
            }

            SQLFragment sql = queryService.getSelectSQL(tableInfo, columnMap.values(), containerFilter, null, -1, 0, false);

            // Insert COUNT
            String sampleCountName = StudySchema.getInstance().getSqlDialect().makeLegalIdentifier("SampleCount");
            String countStr = " COUNT(*) As " + sampleCountName + ",\n";
            int insertIndex = sql.indexOf("SELECT");
            sql.insert(insertIndex + 6, countStr);

            sql.append("GROUP BY ");
            boolean firstGroupBy = true;
            for (ColumnInfo columnInfo : columnMap.values())
            {
                if (!firstGroupBy)
                    sql.append(", ");
                firstGroupBy = false;
                String columnAlias = columnInfo.getAlias();
                sql.append(columnAlias);
            }

            sql.append("\nORDER BY ");
            boolean firstOrderBy = true;
            for (ColumnInfo columnInfo : columnMap.values())
            {
                if (!firstOrderBy)
                    sql.append(", ");
                firstOrderBy = false;
                String columnAlias = columnInfo.getAlias();
                sql.append(columnAlias);
            }

            SqlSelector selector = new SqlSelector(tableInfo.getSchema(), sql);

            resultSet = selector.getResultSet();
            try
            {
                if (null != resultSet)
                {
                    // The result set is grouped by all levels together, so at the upper levels, we have to group ourselves
                    // Build a tree of GroupedResultsMaps, one level for each grouping level
                    //
                    Map<String, GroupedResults> groupedResultsMap = new HashMap<>();
                    while (resultSet.next())
                    {
                        Map<String, Object> rowMap = resultSet.getRowMap();
                        long count = 0;
                        Object countObject = rowMap.get(sampleCountName);
                        if (countObject instanceof Long)
                            count = (Long)countObject;
                        else if (countObject instanceof Integer)
                            count = (Integer)countObject;

                        Map<String, GroupedResults> currentGroupedResultsMap = groupedResultsMap;

                        for (int i = 0; i < grouping.length; i += 1)
                        {
                            if (!StringUtils.isNotBlank(grouping[i]))
                                break;      // Grouping may have null entries for groupBys that are not chosen to be used

                            GroupedValueColumnHelper columnHelper = getGroupedValueAllowedMap().get(grouping[i]);
                            ColumnInfo columnInfo = columnMap.get(columnHelper.getFieldKey());
                            Object value = rowMap.get(columnInfo.getAlias());
                            String labelValue = (null != value) ? value.toString() : null;
                            GroupedResults groupedResults = currentGroupedResultsMap.get(labelValue);
                            if (null == groupedResults)
                            {
                                groupedResults = new GroupedResults();
                                groupedResults.viewName = grouping[i];
                                groupedResults.urlFilterName = columnHelper.getUrlFilterName();
                                groupedResults.labelValue = labelValue;
                                groupedResults.childGroupedResultsMap = new HashMap<>();
                                currentGroupedResultsMap.put(labelValue, groupedResults);
                            }
                            groupedResults.count += count;
                            currentGroupedResultsMap = groupedResults.childGroupedResultsMap;
                        }
                    }

                    if (!groupedResultsMap.isEmpty())
                    {
                        groupedValue = buildGroupedValue(groupedResultsMap, container, new ArrayList<GroupedValueFilter>());
                    }
                    else
                    {
                        groupedValue = new HashMap<>(2);
                        groupedValue.put("name", grouping[0]);
                        groupedValue.put("values", new ArrayList<Map<String, Object>>());
                    }
                }
            }
            finally
            {
                if (null != resultSet)
                    resultSet.close();
            }


            DbCache.put(tableInfo, cacheKey, groupedValue, 8 * CacheManager.HOUR);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return groupedValue;
    }

    private Map<String, Object> buildGroupedValue(Map<String, GroupedResults> groupedResultsMap, Container container, List<GroupedValueFilter> groupedValueFilters)
    {
        String viewName = null;
        ArrayList<Map<String, Object>> groupedValues = new ArrayList<>();
        for (GroupedResults groupedResults : groupedResultsMap.values())
        {
            viewName = groupedResults.viewName;             // They are all the same in this collection
            Map<String, Object> groupedValue = new HashMap<>(5);
            groupedValue.put("label", (null != groupedResults.labelValue) ? groupedResults.labelValue : "[empty]");
            groupedValue.put("count", groupedResults.count);
            groupedValue.put("url", getURL(container, groupedResults.urlFilterName, groupedValueFilters, groupedResults.labelValue));
            Map<String, GroupedResults> childGroupResultsMap = groupedResults.childGroupedResultsMap;
            if (null != childGroupResultsMap && !childGroupResultsMap.isEmpty())
            {
                GroupedValueFilter groupedValueFilter = new GroupedValueFilter();
                groupedValueFilter.setViewColumnName(groupedResults.viewName);
                groupedValueFilter.setFilterValueName(null != groupedResults.labelValue ? groupedResults.labelValue.toString() : null);
                List<GroupedValueFilter> groupedValueFiltersCopy = new ArrayList<>(groupedValueFilters); // Need copy because can't share across members of groupedResultsMap
                groupedValueFiltersCopy.add(groupedValueFilter);
                Map<String, Object> nextLevelGroup = buildGroupedValue(childGroupResultsMap, container, groupedValueFiltersCopy);
                groupedValue.put("group", nextLevelGroup);

            }
            groupedValues.add(groupedValue);
        }

        Collections.sort(groupedValues, new Comparator<Map<String, Object>>()
        {
            @Override
            public int compare(Map<String, Object> o, Map<String, Object> o1)
            {
                String str = (String)o.get("label");
                String str1 = (String)o1.get("label");
                if (null == str)
                {
                    if (null == str1)
                        return 0;
                    else
                        return 1;
                }
                else if (null == str1)
                    return -1;
                return (str.compareTo(str1));
            }
        });

        Map<String, Object> groupedValue = new HashMap<>(2);
        groupedValue.put("name", viewName);
        groupedValue.put("values", groupedValues);
        return groupedValue;
    }

    private ActionURL getURL(Container container, String groupColumnName, List<GroupedValueFilter> filterNamesAndValues, String label)
    {
        ActionURL url = new ActionURL(SpecimenController.SamplesAction.class, container);
        addFilterParameter(url, groupColumnName, label);
        for (GroupedValueFilter filterColumnAndValue : filterNamesAndValues)
            addFilterParameter(url, getGroupedValueAllowedMap().get(filterColumnAndValue.getViewColumnName()).getUrlFilterName(), filterColumnAndValue.getFilterValueName());
        url.addParameter("showVials", "true");
        return url;
    }

    private void addFilterParameter(ActionURL url, String urlColumnName, String label)
    {
        url.addParameter("SpecimenDetail." + urlColumnName + "~eq", label);
    }

    public void registerExtendedSpecimenRequestView(Module module, Resource requestView)
    {
        _moduleExtendedSpecimenRequestViews.put(module.getName(), requestView);
    }

    @Nullable
    public ExtendedSpecimenRequestView getExtendedSpecimenRequestView(ViewContext context)
    {
        if (context == null || context.getContainer() == null)
            return null;

        Set<String> activeModuleNames = new HashSet<>();
        for (Module module : context.getContainer().getActiveModules())
            activeModuleNames.add(module.getName());
        for (Map.Entry<String, Resource> entry : _moduleExtendedSpecimenRequestViews.entrySet())
        {
            if (activeModuleNames.contains(entry.getKey()) && entry.getValue().exists())
            {
                try
                {
                    String body = IOUtils.toString(entry.getValue().getInputStream());
                    body = ModuleHtmlView.replaceTokens(body, context);
                    return ExtendedSpecimenRequestView.createView(body);
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Unable to load extended specimen request view from " + entry.getValue().getPath(), e);
                }
            }
        }

        return null;
    }
}
