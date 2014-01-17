/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

import org.apache.commons.collections15.map.CaseInsensitiveMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.data.DbScope.CommitTaskOption;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.etl.BeanDataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.etl.ListofMapsDataIterator;
import org.labkey.api.etl.Pump;
import org.labkey.api.etl.StandardETL;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.module.Module;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryListener;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RestrictedReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.AssaySpecimenConfig;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyCachable;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.study.QueryHelper;
import org.labkey.study.SampleManager;
import org.labkey.study.StudyCache;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.AssayManager;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.designer.StudyDesignManager;
import org.labkey.study.importer.SchemaReader;
import org.labkey.study.importer.StudyReload;
import org.labkey.study.query.DataSetTableImpl;
import org.labkey.study.query.StudyPersonnelDomainKind;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.studydesign.StudyProductAntigenDomainKind;
import org.labkey.study.query.studydesign.StudyProductDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentProductDomainKind;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.visitmanager.AbsoluteDateVisitManager;
import org.labkey.study.visitmanager.RelativeDateVisitManager;
import org.labkey.study.visitmanager.SequenceVisitManager;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.writer.DatasetWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class StudyManager
{
    public static final SearchService.SearchCategory datasetCategory = new SearchService.SearchCategory("dataset", "Study Dataset");
    public static final SearchService.SearchCategory subjectCategory = new SearchService.SearchCategory("subject", "Study Subject");
    public static final SearchService.SearchCategory assayCategory = new SearchService.SearchCategory("assay", "Study Assay");

    private static final Logger _log = Logger.getLogger(StudyManager.class);
    private static final StudyManager _instance = new StudyManager();
    private static final StudySchema SCHEMA = StudySchema.getInstance();

    private final QueryHelper<StudyImpl> _studyHelper;
    private final QueryHelper<VisitImpl> _visitHelper;
    private final QueryHelper<LocationImpl> _locationHelper;
    private final QueryHelper<AssaySpecimenConfigImpl> _assaySpecimenHelper;
    private final DatasetHelper _datasetHelper;
    private final QueryHelper<CohortImpl> _cohortHelper;
    private final BlockingCache<Container, Set<PropertyDescriptor>> _sharedProperties;
    private final Map<String, Resource> _moduleParticipantViews = new ConcurrentHashMap<>();

    private static final String LSID_REQUIRED = "LSID_REQUIRED";


    private StudyManager()
    {
        // prevent external construction with a private default constructor
        _studyHelper = new QueryHelper<StudyImpl>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoStudy();
                }
            }, StudyImpl.class)
        {
            public List<StudyImpl> get(final Container c, final SimpleFilter filterArg, final String sortString)
            {
                assert filterArg == null & sortString == null;
                String cacheId = getCacheId(filterArg);
                if (sortString != null)
                    cacheId += "; sort = " + sortString;

                CacheLoader<String,Object> loader = new CacheLoader<String,Object>()
                {
                    @Override
                    public Object load(String key, Object argument)
                    {
                        List<? extends StudyCachable> objs = new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT * FROM study.Study WHERE Container = ?", c).getArrayList(StudyImpl.class);
                        for (StudyCachable obj : objs)
                            obj.lock();
                        return objs;
                    }
                };
                return (List<StudyImpl>) StudyCache.get(getTableInfo(), c, cacheId, loader);
            }
        };

        _visitHelper = new QueryHelper<>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoVisit();
                }
            }, VisitImpl.class);

        _locationHelper = new QueryHelper<>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoSite();
                }
            }, LocationImpl.class);

        _assaySpecimenHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoAssaySpecimen();
            }
        }, AssaySpecimenConfigImpl.class);

        _cohortHelper = new QueryHelper<>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoCohort();
                }
            }, CohortImpl.class);

        TableInfoGetter dataSetGetter = new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoDataSet();
            }
        };

        /* Whenever we explicitly invalidate a dataset, unmaterialize it as well
         * this is probably a little overkill, e.g. name change doesn't need to unmaterialize
         * however, this is the best choke point
         */
        _datasetHelper = new DatasetHelper(dataSetGetter);

        // Cache of PropertyDescriptors found in the Shared container for datasets in the given study Container.
        // The shared properties cache will be cleared when the _datasetHelper cache is cleared.
        _sharedProperties = CacheManager.getBlockingCache(1000, CacheManager.UNLIMITED, "StudySharedProperties",
                new CacheLoader<Container, Set<PropertyDescriptor>>()
                {
                    @Override
                    public Set<PropertyDescriptor> load(Container key, @Nullable Object argument)
                    {
                        Container sharedContainer = ContainerManager.getSharedContainer();
                        assert key != sharedContainer;

                        List<DataSetDefinition> defs = _datasetHelper.get(key);
                        if (defs == null)
                            return Collections.emptySet();

                        Set<PropertyDescriptor> set = new LinkedHashSet<>();
                        for (DataSetDefinition def : defs)
                        {
                            Domain domain = def.getDomain();
                            if (domain == null)
                                continue;

                            for (DomainProperty dp : domain.getProperties())
                                if (dp.getContainer().equals(sharedContainer))
                                    set.add(dp.getPropertyDescriptor());
                        }
                        return Collections.unmodifiableSet(set);
                    }
                });

        ViewCategoryManager.addCategoryListener(new CategoryListener(this));
    }


    private class DatasetHelper extends QueryHelper<DataSetDefinition>
    {
        private DatasetHelper(TableInfoGetter tableGetter)
        {
            super(tableGetter, DataSetDefinition.class);
        }

        private void clearProperties(DataSetDefinition def)
        {
            StudyManager.this._sharedProperties.remove(def.getContainer());
        }

        @Override
        public void clearCache(DataSetDefinition def)
        {
            super.clearCache(def);
            clearProperties(def);
        }
    }


    public static StudyManager getInstance()
    {
        return _instance;
    }


    @Nullable
    public synchronized StudyImpl getStudy(Container c)
    {
        StudyImpl study;
        boolean retry = true;

        while (true)
        {
            List<StudyImpl> studies = _studyHelper.get(c);
            if (studies == null || studies.size() == 0)
                return null;
            else if (studies.size() > 1)
                throw new IllegalStateException("Only one study is allowed per container");
            else
                study = studies.get(0);

            // UNDONE: There is a subtle bug in QueryHelper caching, cached objects shouldn't hold onto Container objects
            assert(study.getContainer().getId().equals(c.getId()));
            if (study.getContainer() == c)
                break;

            if (!retry) // we only get one retry
                break;

            _studyHelper.clearCache(study);
            retry = false;
        }

        // upgrade checks
        if (null == study.getEntityId() || c.getId().equals(study.getEntityId()))
        {
            study.setEntityId(GUID.makeGUID());
            updateStudy(null, study);
        }

        return study;
    }

    @NotNull
    public StudyImpl[] getAllStudies()
    {
        return new TableSelector(StudySchema.getInstance().getTableInfoStudy(), null, new Sort("Label")).getArray(StudyImpl.class);
    }

    @NotNull
    public Study[] getAllStudies(Container root, User user)
    {
        return getAllStudies(root, user, ReadPermission.class);
    }

    @NotNull
    public Study[] getAllStudies(Container root, User user, Class<? extends Permission> perm)
    {
        StudyImpl[] studies = getAllStudies();
        List<Study> result = new ArrayList<>(studies.length);
        for (StudyImpl study : studies)
        {
            if (study.getContainer().hasPermission(user, perm) &&
                    (study.getContainer().equals(root) || study.getContainer().isDescendant(root)))
            {
                result.add(study);
            }
        }
        return result.toArray(new Study[result.size()]);
    }

    public StudyImpl createStudy(User user, StudyImpl study) throws SQLException
    {
        Container container = study.getContainer();
        assert null != container;
        assert null != user;
        if (study.getLsid() == null)
            study.initLsid();

        if (study.getProtocolDocumentEntityId() == null)
            study.setProtocolDocumentEntityId(GUID.makeGUID());

        if (study.getAlternateIdDigits() == 0)
           study.setAlternateIdDigits(StudyManager.ALTERNATEID_DEFAULT_NUM_DIGITS);

        study = _studyHelper.create(user, study);

        //note: we no longer copy the container's policy to the study upon creation
        //instead, we let it inherit the container's policy until the security type
        //is changed to one of the advanced options. 

        // Force provisioned specimen tables to be created
        StudySchema.getInstance().getTableInfoSpecimen(container, user);
        StudySchema.getInstance().getTableInfoVial(container, user);
        StudySchema.getInstance().getTableInfoSpecimenEvent(container, user);
        return study;
    }

    public void updateStudy(@Nullable User user, StudyImpl study)
    {
        Study oldStudy = getStudy(study.getContainer());
        Date oldStartDate = oldStudy.getStartDate();
        try
        {
            _studyHelper.update(user, study, new Object[] { study.getContainer() });
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (oldStudy.getTimepointType() == TimepointType.DATE && !Objects.equals(study.getStartDate(), oldStartDate))
        {
            // start date has changed, and datasets may use that value. Uncache.
            RelativeDateVisitManager visitManager = (RelativeDateVisitManager) getVisitManager(study);
            visitManager.recomputeDates(oldStartDate, user);
            clearCaches(study.getContainer(), true);
        }
        else
        {
            // Need to get rid of any old copies of the study
            clearCaches(study.getContainer(), false);
        }
    }

    public void createDataSetDefinition(User user, Container container, int dataSetId) throws SQLException
    {
        createDataSetDefinition(user, new DataSetDefinition(getStudy(container), dataSetId));
    }

    public void createDataSetDefinition(User user, DataSetDefinition dataSetDefinition)
    {
        if (dataSetDefinition.getDataSetId() <= 0)
            throw new IllegalArgumentException("datasetId must be greater than zero.");
        DbScope scope = StudySchema.getInstance().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            ensureViewCategory(user, dataSetDefinition);
            _datasetHelper.create(user, dataSetDefinition);
            // This method call has the side effect of ensuring that we have a domain. If we don't create it here,
            // we're open to a race condition if another thread tries to do something with the dataset's table
            // and ends up attempting to create the domain as well
            dataSetDefinition.getStorageTableInfo();

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        indexDataset(null, dataSetDefinition);
    }

    /**
     * Temporary shim until we can redo the dataset category UI
     */
    private void ensureViewCategory(User user, DataSetDefinition def)
    {
        ViewCategory category = null;

        if (def.getCategoryId() != null)
            category = ViewCategoryManager.getInstance().getCategory(def.getCategoryId());

        if (category == null && def.getCategory() != null)
        {
            // the imported category name may be encoded to contain subcategory info
            String[] parts = ViewCategoryManager.getInstance().decode(def.getCategory());
            category = ViewCategoryManager.getInstance().ensureViewCategory(def.getContainer(), user, parts);
        }

        if (category != null)
        {
            def.setCategoryId(category.getRowId());
            def.setCategory(category.getLabel());
        }
    }

    @Deprecated
    public void updateDataSetDefinition(User user, DataSetDefinition dataSetDefinition)
    {
        List<String> errors = new ArrayList<>();
        updateDataSetDefinition(user, dataSetDefinition, errors);
        if (!errors.isEmpty())
            throw new IllegalArgumentException(errors.get(0));
    }


    public boolean updateDataSetDefinition(User user, DataSetDefinition dataSetDefinition, List<String> errors)
    {
        DbScope scope = StudySchema.getInstance().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            DataSetDefinition old = getDataSetDefinition(dataSetDefinition.getStudy(), dataSetDefinition.getDataSetId());
            if (null == old)
                throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);

            // make sure we reload domain and tableinfo
            dataSetDefinition._domain = null;
            dataSetDefinition._storageTable = null;

            Domain domain = dataSetDefinition.getDomain();

            // Check if the extra key field has changed
            boolean isProvisioned = domain != null && domain.getStorageTableName() != null ;
            boolean isKeyChanged = old.isDemographicData() != dataSetDefinition.isDemographicData() || !StringUtils.equals(old.getKeyPropertyName(), dataSetDefinition.getKeyPropertyName());
            if (isProvisioned && isKeyChanged)
            {
                TableInfo storageTableInfo = dataSetDefinition.getStorageTableInfo();

                // If so, we need to update the _key column and the LSID

                // Set the _key column to be the value of the selected column
                // Change how we build up tableName
                String tableName = storageTableInfo.toString();
                SQLFragment updateKeySQL = new SQLFragment("UPDATE " + tableName + " SET _key = ");
                if (dataSetDefinition.getKeyPropertyName() == null)
                {
                    // No column selected, so set it to be null
                    updateKeySQL.append("NULL");
                }
                else
                {
                    ColumnInfo col = storageTableInfo.getColumn(dataSetDefinition.getKeyPropertyName());
                    if (null == col)
                    {
                        errors.add("Cannot find 'key' column: " + dataSetDefinition.getKeyPropertyName());
                        return false;
                    }
                    SQLFragment colFrag = col.getValueSql(tableName);
                    if (col.getJdbcType() == JdbcType.TIMESTAMP)
                        colFrag = storageTableInfo.getSqlDialect().getISOFormat(colFrag);
                    updateKeySQL.append(colFrag);
                }

                try
                {
                    new SqlExecutor(StudySchema.getInstance().getSchema()).execute(updateKeySQL);

                    // Now update the LSID column. Note - this needs to be the same as DatasetImportHelper.getURI()
                    SQLFragment updateLSIDSQL = new SQLFragment("UPDATE " + tableName + " SET lsid = ");
                    updateLSIDSQL.append(dataSetDefinition.generateLSIDSQL());
                    new SqlExecutor(StudySchema.getInstance().getSchema()).execute(updateLSIDSQL);
                }
                catch (DataIntegrityViolationException x)
                {
                    if (dataSetDefinition.isDemographicData())
                        errors.add("Can not change dataset type to demographic");
                    else
                        errors.add("Changing the dataset key would result in a duplicate keys");
                    return false;
                }
            }
            Object[] pk = new Object[]{dataSetDefinition.getContainer().getId(), dataSetDefinition.getDataSetId()};
            ensureViewCategory(user, dataSetDefinition);
            _datasetHelper.update(user, dataSetDefinition, pk);

            if (!old.getName().equals(dataSetDefinition.getName()))
            {
                QueryChangeListener.QueryPropertyChange change = new QueryChangeListener.QueryPropertyChange<>(
                    QueryService.get().getUserSchema(user, dataSetDefinition.getContainer(), StudyQuerySchema.SCHEMA_NAME).getQueryDefForTable(dataSetDefinition.getName()),
                    QueryChangeListener.QueryProperty.Name,
                    old.getName(),
                    dataSetDefinition.getName()
                );

                QueryService.get().fireQueryChanged(user, dataSetDefinition.getContainer(), null, new SchemaKey(null, StudyQuerySchema.SCHEMA_NAME),
                        QueryChangeListener.QueryProperty.Name, Collections.singleton(change));
            }
            transaction.commit();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            uncache(dataSetDefinition);
        }
        indexDataset(null, dataSetDefinition);
        return true;
    }


    public boolean isDataUniquePerParticipant(DataSetDefinition dataSet) throws SQLException
    {
        // don't use dataSet.getTableInfo() since this method is called during updateDatasetDefinition`() and may be in an inconsistent state
        TableInfo t = dataSet.getStorageTableInfo();
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT MAX(n) FROM (SELECT COUNT(*) AS n FROM ").append(t.getFromSQL("DS")).append(" GROUP BY ParticipantId) x");
        Integer maxCount = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getObject(Integer.class);
        return maxCount == null || maxCount <= 1;
    }


    public static class VisitCreationException extends RuntimeException
    {
        public VisitCreationException(String message)
        {
            super(message);
        }
    }


    public VisitImpl createVisit(Study study, User user, VisitImpl visit)
    {
        return createVisit(study, user, visit, null);
    }


    public VisitImpl createVisit(Study study, User user, VisitImpl visit, @Nullable List<VisitImpl> existingVisits)
    {
        if (visit.getContainer() != null && !visit.getContainer().getId().equals(study.getContainer().getId()))
            throw new VisitCreationException("Visit container does not match study");
        visit.setContainer(study.getContainer());

        if (visit.getSequenceNumMin() > visit.getSequenceNumMax())
            throw new VisitCreationException("SequenceNumMin must be less than or equal to SequenceNumMax");

        if (visit.getSequenceNumTarget() < visit.getSequenceNumMin() || visit.getSequenceNumTarget() > visit.getSequenceNumMax())
            throw new VisitCreationException("SequenceNumTarget must be within the min and max range");

        if (null == existingVisits)
            existingVisits = getVisits(study, Visit.Order.SEQUENCE_NUM);

        int prevDisplayOrder = 0;
        int prevChronologicalOrder = 0;

        for (VisitImpl existingVisit : existingVisits)
        {
            if (existingVisit.getSequenceNumMin() < visit.getSequenceNumMin())
            {
                prevChronologicalOrder = existingVisit.getChronologicalOrder();
                prevDisplayOrder = existingVisit.getDisplayOrder();
            }

            if (existingVisit.getSequenceNumMin() > existingVisit.getSequenceNumMax())
                throw new VisitCreationException("Corrupt existing visit " + existingVisit.getLabel() +
                        ": SequenceNumMin must be less than or equal to SequenceNumMax");
            boolean disjoint = visit.getSequenceNumMax() < existingVisit.getSequenceNumMin() ||
                               visit.getSequenceNumMin() > existingVisit.getSequenceNumMax();
            if (!disjoint)
                throw new VisitCreationException("New visit " + visit.getLabel() + " overlaps existing visit " + existingVisit.getLabel());
        }

        // if our visit doesn't have a display order or chronological order set, but the visit before our new visit
        // (based on sequencenum) does, then assign the previous visit's order info to our new visit.  This won't always
        // be exactly right, but it's better than having all newly created visits appear at the beginning of the display
        // and chronological lists:
        if (visit.getDisplayOrder() == 0 && prevDisplayOrder > 0)
            visit.setDisplayOrder(prevDisplayOrder);
        if (visit.getChronologicalOrder() == 0 && prevChronologicalOrder > 0)
            visit.setChronologicalOrder(prevChronologicalOrder);

        try
        {
            visit = _visitHelper.create(user, visit);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        
        if (visit.getRowId() == 0)
            throw new VisitCreationException("Visit rowId has not been set properly");

        return visit;
    }


    public VisitImpl ensureVisit(Study study, User user, double sequencenum, Visit.Type type, boolean saveIfNew)
    {
        List<VisitImpl> visits = getVisits(study, Visit.Order.SEQUENCE_NUM);
        VisitImpl result = ensureVisitWithoutSaving(study, sequencenum, type, visits);
        if (saveIfNew && result.getRowId() == 0)
        {
            // Insert it into the database if it's new
            return createVisit(study, user, result);
        }
        return result;
    }


    public boolean ensureVisits(Study study, User user, Set<Double> sequencenums, @Nullable Visit.Type type)
    {
        List<VisitImpl> visits = getVisits(study, Visit.Order.SEQUENCE_NUM);
        boolean created = false;
        for (double sequencenum : sequencenums)
        {
            VisitImpl result = ensureVisitWithoutSaving(study, sequencenum, type, visits);
            if (result.getRowId() == 0)
            {
                createVisit(study, user, result, visits);
                created = true;
            }
        }
        return created;
    }


    private VisitImpl ensureVisitWithoutSaving(Study study, double sequencenum, @Nullable Visit.Type type, List<VisitImpl> existingVisits)
    {
        // Remember the SequenceNums closest to the requested id in case we need to create one
        double nextVisit = Double.POSITIVE_INFINITY;
        double previousVisit = Double.NEGATIVE_INFINITY;
        for (VisitImpl visit : existingVisits)
        {
            if (visit.getSequenceNumMin() <= sequencenum && visit.getSequenceNumMax() >= sequencenum)
                return visit;
            // check to see if our new sequencenum is within the range of an existing visit:
            // Check if it's the closest to the requested id, either before or after
            if (visit.getSequenceNumMin() < nextVisit && visit.getSequenceNumMin() > sequencenum)
            {
                nextVisit = visit.getSequenceNumMin();
            }
            if (visit.getSequenceNumMax() > previousVisit && visit.getSequenceNumMax() < sequencenum)
            {
                previousVisit = visit.getSequenceNumMax();
            }
        }
        double visitIdMin = sequencenum;
        double visitIdMax = sequencenum;
        String label = null;
        if (!study.getTimepointType().isVisitBased())
        {
            // Do special handling for data-based studies
            if (study.getDefaultTimepointDuration() == 1 || sequencenum != Math.floor(sequencenum) || sequencenum < 0)
            {
                // See if there's a fractional part to the number
                if (sequencenum != Math.floor(sequencenum))
                {
                    label = "Day " + sequencenum;
                }
                else
                {
                    // If not, drop the decimal from the default name
                    label = "Day " + (int)sequencenum;
                }
            }
            else
            {
                // Try to create a timepoint that spans the default number of days
                // For example, if duration is 7 days, do timepoints for days 0-6, 7-13, 14-20, etc
                int intervalNumber = (int)sequencenum / study.getDefaultTimepointDuration();
                visitIdMin = intervalNumber * study.getDefaultTimepointDuration();
                visitIdMax = (intervalNumber + 1) * study.getDefaultTimepointDuration() - 1;

                // Scale the timepoint to be smaller if there are existing timepoints that overlap
                // on its desired day range
                if (previousVisit != Double.NEGATIVE_INFINITY)
                {
                    visitIdMin = Math.max(visitIdMin, previousVisit + 1);
                }
                if (nextVisit != Double.POSITIVE_INFINITY)
                {
                    visitIdMax = Math.min(visitIdMax, nextVisit - 1);
                }

                // Default label is "Day X"
                label = "Day " + (int)visitIdMin + " - " + (int)visitIdMax;
                if ((int)visitIdMin == (int)visitIdMax)
                {
                    // Single day timepoint, so don't use the range
                    label = "Day " + (int)visitIdMin;
                }
                else if (visitIdMin == intervalNumber * study.getDefaultTimepointDuration() &&
                        visitIdMax == (intervalNumber + 1) * study.getDefaultTimepointDuration() - 1)
                {
                    // The timepoint is the full span for the default duration, so see if we
                    // should call it "Week" or "Month"
                    if (study.getDefaultTimepointDuration() == 7)
                    {
                        label = "Week " + (intervalNumber + 1);
                    }
                    else if (study.getDefaultTimepointDuration() == 30 || study.getDefaultTimepointDuration() == 31)
                    {
                        label = "Month " + (intervalNumber + 1);
                    }
                }
            }

        }
        return new VisitImpl(study.getContainer(), visitIdMin, visitIdMax, label, type);
    }

    public void importVisitAliases(Study study, User user, List<VisitAlias> aliases) throws IOException, ValidationException, SQLException
    {
        DataIteratorBuilder it = new BeanDataIterator.Builder(VisitAlias.class, aliases);
        importVisitAliases(study, user, it);
    }


    public int importVisitAliases(final Study study, User user, DataIteratorBuilder loader) throws SQLException, IOException, ValidationException
    {
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitAliases();
        DbScope scope = tinfo.getSchema().getScope();

        // We want delete and bulk insert in the same transaction
        try (Transaction transaction = scope.ensureTransaction())
        {
            clearVisitAliases(study);

            DataIteratorContext context = new DataIteratorContext();
            context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
            StandardETL etl = StandardETL.forInsert(tinfo, loader, study.getContainer(), user, context);
            DataIteratorBuilder insert = ((UpdateableTableInfo)tinfo).persistRows(etl, context);
            Pump p = new Pump(insert, context);
            p.run();

            if (context.getErrors().hasErrors())
                throw context.getErrors().getRowErrors().get(0);

            transaction.commit();

            return p.getRowCount();
        }
    }


    public void clearVisitAliases(Study study) throws SQLException
    {
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitAliases();
        DbScope scope = tinfo.getSchema().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            Table.delete(tinfo, containerFilter);
            transaction.commit();
        }
    }


    public Map<String, Double> getVisitImportMap(Study study, boolean includeStandardMapping)
    {
        Collection<VisitAlias> customMapping = getCustomVisitImportMapping(study);
        List<VisitImpl> visits = includeStandardMapping ? StudyManager.getInstance().getVisits(study, Visit.Order.SEQUENCE_NUM) : Collections.<VisitImpl>emptyList();

        Map<String, Double> map = new CaseInsensitiveHashMap<>((customMapping.size() + visits.size()) * 3 / 4);

//        // allow prepended "visit"
//        for (Visit visit : visits)
//        {
//            if (null == visit.getLabel())
//                continue;
//            String label = "visit " + visit.getLabel();
//            // Use the **first** instance of each label
//            if (!map.containsKey(label))
//                map.put(label, visit.getSequenceNumMin());
//        }

        // Load up standard label -> min sequence number mapping first
        for (Visit visit : visits)
        {
            String label = visit.getLabel();

            // Use the **first** instance of each label
            if (null != label && !map.containsKey(label))
                map.put(label, visit.getSequenceNumMin());
        }

        // Now load custom mapping, overwriting any existing standard labels
        for (VisitAlias alias : customMapping)
            map.put(alias.getName(), alias.getSequenceNum());

        return map;
    }


    // Return the custom import mapping (optinally provided by the admin), ordered by sequence num then row id (which
    // maintains import order in the case where multiple names map to the same sequence number).
    public Collection<VisitAlias> getCustomVisitImportMapping(Study study)
    {
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitAliases();

        return new TableSelector(tinfo, tinfo.getColumns("Name, SequenceNum"), containerFilter, new Sort("SequenceNum,RowId")).getCollection(VisitAlias.class);
    }


    // Return the standard import mapping (generated from Visit.Label -> Visit.SequenceNumMin), ordered by sequence
    // num for display purposes.  Include VisitAliases that won't be used, but mark them as overridden.
    public Collection<VisitAlias> getStandardVisitImportMapping(Study study)
    {
        List<VisitAlias> list = new LinkedList<>();
        Set<String> labels = new CaseInsensitiveHashSet();
        Map<String, Double> customMap = getVisitImportMap(study, false);

        List<VisitImpl> visits = StudyManager.getInstance().getVisits(study, Visit.Order.SEQUENCE_NUM);

        for (Visit visit : visits)
        {
            String label = visit.getLabel();

            if (null != label)
            {
                boolean overridden = labels.contains(label) || customMap.containsKey(label);
                list.add(new VisitAlias(label, visit.getSequenceNumMin(), visit.getSequenceString(), overridden));

                if (!overridden)
                    labels.add(label);
            }
        }

        return list;
    }


    public static class VisitAlias
    {
        private String _name;
        private double _sequenceNum;
        private String _sequenceString;
        private boolean _overridden;  // For display purposes -- we show all visits and gray out the ones that are not used

        @SuppressWarnings({"UnusedDeclaration"}) // Constructed via reflection by the Table layer
        public VisitAlias()
        {
        }

        public VisitAlias(String name, double sequenceNum, @Nullable String sequenceString, boolean overridden)
        {
            _name = name;
            _sequenceNum = sequenceNum;
            _sequenceString = sequenceString;
            _overridden = overridden;
        }

        public VisitAlias(String name, double sequenceNum)
        {
            this(name, sequenceNum, null, false);
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public double getSequenceNum()
        {
            return _sequenceNum;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSequenceNum(double sequenceNum)
        {
            _sequenceNum = sequenceNum;
        }

        public boolean isOverridden()
        {
            return _overridden;
        }

        public String getSequenceNumString()
        {
            return VisitImpl.formatSequenceNum(_sequenceNum);
        }

        public String getSequenceString()
        {
            if (null == _sequenceString)
                return getSequenceNumString();
            else
                return _sequenceString;
        }
    }


    public void createCohort(Study study, User user, CohortImpl cohort) throws SQLException
    {
        if (cohort.getContainer() != null && !cohort.getContainer().getId().equals(study.getContainer().getId()))
            throw new IllegalArgumentException("Cohort container does not match study");
        cohort.setContainer(study.getContainer());

        // Lsid requires the row id, which does not get created until this object has been inserted into the db
        if (cohort.getLsid() != null)
            throw new IllegalStateException("Attempt to create a new cohort with lsid already set");
        cohort.setLsid(LSID_REQUIRED);
        cohort = _cohortHelper.create(user, cohort);

        if (cohort.getRowId() == 0)
            throw new IllegalStateException("Cohort rowId has not been set properly");

        cohort.initLsid();
        _cohortHelper.update(user, cohort);
    }


    public void deleteVisit(StudyImpl study, VisitImpl visit, User user) throws SQLException
    {
        StudySchema schema = StudySchema.getInstance();

        try (Transaction transaction = schema.getSchema().getScope().ensureTransaction())
        {
            for (DataSetDefinition def : study.getDataSets())
            {
                TableInfo t = def.getStorageTableInfo();
                if (null == t)
                    continue;

                SQLFragment sqlf = new SQLFragment();
                sqlf.append("DELETE FROM ");
                sqlf.append(t.getSelectName());
                sqlf.append(" WHERE LSID IN (SELECT LSID FROM ");
                sqlf.append(t.getSelectName());
                sqlf.append(" d, ");
                sqlf.append(StudySchema.getInstance().getTableInfoParticipantVisit(), "pv");
                sqlf.append(" WHERE d.ParticipantId = pv.ParticipantId AND d.SequenceNum = pv.SequenceNum AND pv.VisitRowId = ? AND pv.Container = ?)");
                sqlf.add(visit.getRowId());
                sqlf.add(study.getContainer());
                int count = new SqlExecutor(schema.getSchema()).execute(sqlf);
                if (count > 0)
                    StudyManager.dataSetModified(def, user, true);
            }

            // Delete samples first because we may need ParticipantVisit to figure out which samples
            SampleManager.getInstance().deleteSamplesForVisit(visit);

            deleteTreatmentVisitMapForVisit(study.getContainer(), visit.getRowId());
            deleteAssaySpecimenVisits(study.getContainer(), visit.getRowId());

            SQLFragment sqlFragParticipantVisit = new SQLFragment("DELETE FROM " + schema.getTableInfoParticipantVisit() + "\n" +
                    "WHERE Container = ? and VisitRowId = ?");
            sqlFragParticipantVisit.add(study.getContainer().getId());
            sqlFragParticipantVisit.add(visit.getRowId());
            new SqlExecutor(schema.getSchema()).execute(sqlFragParticipantVisit);

            SQLFragment sqlFragVisitMap = new SQLFragment("DELETE FROM " + schema.getTableInfoVisitMap() + "\n" +
                    "WHERE Container=? AND VisitRowId=?");
            sqlFragVisitMap.add(study.getContainer().getId());
            sqlFragVisitMap.add(visit.getRowId());
            new SqlExecutor(schema.getSchema()).execute(sqlFragVisitMap);

            // UNDONE broken _visitHelper.delete(visit);
            try
            {
                Table.delete(schema.getTableInfoVisit(), new Object[] {study.getContainer(), visit.getRowId()});
            }
            catch (Table.OptimisticConflictException  x)
            {
                /* ignore */
            }
            _visitHelper.clearCache(visit);

            transaction.commit();

            getVisitManager(study).updateParticipantVisits(user, study.getDataSets());
        }
    }


    public void updateVisit(User user, VisitImpl visit) throws SQLException
    {
        Object[] pk = new Object[]{visit.getContainer().getId(), visit.getRowId()};
        _visitHelper.update(user, visit, pk);
    }

    public void updateCohort(User user, CohortImpl cohort) throws SQLException
    {
        _cohortHelper.update(user, cohort);
    }

    public void updateParticipant(User user, Participant participant)
    {
        try
        {
            Table.update(user,
                    SCHEMA.getTableInfoParticipant(),
                    participant,
                    new Object[] {participant.getContainer().getId(), participant.getParticipantId()}
            );
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public List<LocationImpl> getSites(Container container)
    {
        return _locationHelper.get(container, "Label");
    }

    public List<LocationImpl> getValidRequestingLocations(Container container)
    {
        Study study = getStudy(container);
        List<LocationImpl> validLocations = new ArrayList<>();
        List<LocationImpl> locations = getSites(container);
        for (LocationImpl location : locations)
        {
            if (isSiteValidRequestingLocation(study, location))
            {
                validLocations.add(location);
            }
        }
        return validLocations;
    }

    public boolean isSiteValidRequestingLocation(Container container, int id)
    {
        Study study = getStudy(container);
        LocationImpl location = getLocation(container, id);
        return isSiteValidRequestingLocation(study, location);
    }

    private boolean isSiteValidRequestingLocation(Study study, LocationImpl location)
    {
        if (null == location)
            return false;

        if (location.isRepository() && study.isAllowReqLocRepository())
        {
            return true;
        }
        if (location.isClinic() && study.isAllowReqLocClinic())
        {
            return true;
        }
        if (location.isSal() && study.isAllowReqLocSal())
        {
            return true;
        }
        if (location.isEndpoint() && study.isAllowReqLocEndpoint())
        {
            return true;
        }
        if (!location.isRepository() && !location.isClinic() && !location.isSal() && !location.isEndpoint())
        {   // It has no location type, so allow it
            return true;
        }
        return false;
    }

    public LocationImpl getLocation(Container container, int id)
    {
        return _locationHelper.get(container, id);
    }

    public List<LocationImpl> getLocationsByLabel(Container container, String label)
    {
        return _locationHelper.get(container, new SimpleFilter(FieldKey.fromParts("Label"), label));
    }

    public void createSite(User user, LocationImpl location) throws SQLException
    {
        _locationHelper.create(user, location);
    }

    public void updateSite(User user, LocationImpl location) throws SQLException
    {
        _locationHelper.update(user, location);
    }

    public List<AssaySpecimenConfigImpl> getAssaySpecimenConfigs(Container container)
    {
        return _assaySpecimenHelper.get(container, "RowId");
    }

    public List<VisitImpl> getVisitsForImmunizationSchedule(Container container)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        List<Integer> visitRowIds = new TableSelector(StudySchema.getInstance().getTableInfoTreatmentVisitMap(),
                Collections.singleton("VisitId"), filter, new Sort("VisitId")).getArrayList(Integer.class);

        return getSortVisitsByRowIds(container, visitRowIds);
    }

    public List<VisitImpl> getVisitsForAssaySchedule(Container container)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        List<Integer> visitRowIds = new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(),
                Collections.singleton("VisitId"), filter, new Sort("VisitId")).getArrayList(Integer.class);

        return getSortVisitsByRowIds(container, visitRowIds);
    }

    private List<VisitImpl> getSortVisitsByRowIds(Container container, List<Integer> visitRowIds)
    {
        List<VisitImpl> visits = new ArrayList<>();
        Study study = getStudy(container);
        if (study != null)
        {
            for (VisitImpl v : getVisits(study, Visit.Order.DISPLAY))
            {
                if (visitRowIds.contains(v.getRowId()))
                    visits.add(v);
            }
        }
        return visits;
    }

    public List<Integer> getAssaySpecimenVisitIds(Container container, AssaySpecimenConfig assaySpecimenConfig)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("AssaySpecimenId"), assaySpecimenConfig.getRowId());

        return new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(),
                Collections.singleton("VisitId"), filter, new Sort("VisitId")).getArrayList(Integer.class);
    }

    public void deleteAssaySpecimenVisits(Container container, int rowId) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("VisitId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(), filter);
    }

    public List<ProductImpl> getStudyProducts(Container container, User user)
    {
        return getStudyProducts(container, user, null, null);
    }

    public List<ProductImpl> getStudyProducts(Container container, User user, @Nullable String role, @Nullable Integer rowId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        if (role != null)
            filter.addCondition(FieldKey.fromParts("Role"), role);
        if (rowId != null)
            filter.addCondition(FieldKey.fromParts("RowId"), rowId);

        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(ProductImpl.class);
    }

    public List<ProductAntigenImpl> getStudyProductAntigens(Container container, User user, int productId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("ProductId"), productId);

        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(ProductAntigenImpl.class);
    }

    public List<TreatmentImpl> getStudyTreatments(Container container, User user)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
        return new TableSelector(ti, filter, new Sort("RowId")).getArrayList(TreatmentImpl.class);
    }

    public TreatmentImpl getStudyTreatmentByRowId(Container container, User user, int rowId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("RowId"), rowId);
        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
        TreatmentImpl treatment = new TableSelector(ti, filter, null).getObject(TreatmentImpl.class);

        // attach the associated study products to the treatment object
        if (treatment != null)
        {
            // sort the product list to match the manage study products page
            Sort sort = new Sort();
            sort.appendSortColumn(FieldKey.fromParts("ProductId", "Role"), Sort.SortDirection.DESC, false);
            sort.appendSortColumn(FieldKey.fromParts("ProductId", "RowId"), Sort.SortDirection.ASC, false);

            List<TreatmentProductImpl> treatmentProducts = getStudyTreatmentProducts(container, user, treatment.getRowId(), sort);
            for (TreatmentProductImpl treatmentProduct : treatmentProducts)
            {
                List<ProductImpl> products = getStudyProducts(container, user, null, treatmentProduct.getProductId());
                for (ProductImpl product : products)
                    treatment.addProduct(product);
            }
        }

        return treatment;
    }

    public List<TreatmentProductImpl> getStudyTreatmentProducts(Container container, User user, int treatmentId)
    {
        return getStudyTreatmentProducts(container, user, treatmentId, new Sort("RowId"));
    }

    public List<TreatmentProductImpl> getStudyTreatmentProducts(Container container, User user, int treatmentId, Sort sort)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("TreatmentId"), treatmentId);

        TableInfo ti = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        return new TableSelector(ti, filter, sort).getArrayList(TreatmentProductImpl.class);
    }

    public List<TreatmentVisitMapImpl> getStudyTreatmentVisitMap(Container container, @Nullable Integer cohortId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        if (cohortId != null)
            filter.addCondition(FieldKey.fromParts("CohortId"), cohortId);

        TableInfo ti = StudySchema.getInstance().getTableInfoTreatmentVisitMap();
        return new TableSelector(ti, filter, new Sort("CohortId")).getArrayList(TreatmentVisitMapImpl.class);
    }

    public TreatmentVisitMapImpl insertTreatmentVisitMap(User user, Container container, int cohortId, int visitId, int treatmentId) throws SQLException
    {
        TreatmentVisitMapImpl newMapping = new TreatmentVisitMapImpl();
        newMapping.setContainer(container);
        newMapping.setCohortId(cohortId);
        newMapping.setVisitId(visitId);
        newMapping.setTreatmentId(treatmentId);

        return Table.insert(user, StudySchema.getInstance().getTableInfoTreatmentVisitMap(), newMapping);
    }

    public void deleteTreatmentVisitMapForCohort(Container container, int rowId) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("CohortId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoTreatmentVisitMap(), filter);
    }

    public void deleteTreatmentVisitMapForVisit(Container container, int rowId) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("VisitId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoTreatmentVisitMap(), filter);
    }

    public void deleteTreatment(Container container, User user, int rowId)
    {
        StudySchema schema = StudySchema.getInstance();

        try (Transaction transaction = schema.getSchema().getScope().ensureTransaction())
        {
            // delete the uages of this treatment in the TreatmentVisitMap
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("TreatmentId"), rowId);
            Table.delete(schema.getTableInfoTreatmentVisitMap(), filter);

            // delete the associated treatment study product mappings (provision table)
            filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("TreatmentId"), rowId);
            deleteTreatmentProductMap(container, user, filter);

            // finally delete the record from the Treatment  (provision table)
            TableInfo treatmentTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
            if (treatmentTable != null)
            {
                QueryUpdateService qus = treatmentTable.getUpdateService();
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo treatmentPk = treatmentTable.getColumn(FieldKey.fromParts("RowId"));
                keys.add(Collections.<String, Object>singletonMap(treatmentPk.getName(), rowId));
                qus.deleteRows(user, container, keys, null);
            }

            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public void deleteStudyProduct(Container container, User user, int rowId)
    {
        StudySchema schema = StudySchema.getInstance();

        try (Transaction transaction = schema.getSchema().getScope().ensureTransaction())
        {
            // delete the uages of this study procut in the ProductAntigen table (provision table)
            deleteProductAntigens(container, user, rowId);

            // delete the associated treatment study product mappings (provision table)
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("ProductId"), rowId);
            deleteTreatmentProductMap(container, user, filter);

            // finally delete the record from the Products  (provision table)
            TableInfo productTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
            if (productTable != null)
            {
                QueryUpdateService qus = productTable.getUpdateService();
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo productPk = productTable.getColumn(FieldKey.fromParts("RowId"));
                keys.add(Collections.<String, Object>singletonMap(productPk.getName(), rowId));
                qus.deleteRows(user, container, keys, null);
            }
            else
                throw new IllegalStateException("Could not find table: " + StudyQuerySchema.PRODUCT_TABLE_NAME);

            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public void deleteProductAntigens(Container container, User user, int rowId) throws Exception
    {
        TableInfo productAntigenTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        if (productAntigenTable != null)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("ProductId"), rowId);
            TableSelector selector = new TableSelector(productAntigenTable, Collections.singleton("RowId"), filter, null);
            Integer[] productAntigenIds = selector.getArray(Integer.class);

            QueryUpdateService qus = productAntigenTable.getUpdateService();
            if (qus != null)
            {
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo productAntigenPk = productAntigenTable.getColumn(FieldKey.fromParts("RowId"));
                for (Integer productAntigenId : productAntigenIds)
                {
                    keys.add(Collections.<String, Object>singletonMap(productAntigenPk.getName(), productAntigenId));
                }

                qus.deleteRows(user, container, keys, null);
            }
            else
                throw new IllegalStateException("Could not find query update service for table: " + StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
        }
        else
            throw new IllegalStateException("Could not find table: " + StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME);
    }

    public void deleteTreatmentProductMap(Container container, User user, SimpleFilter filter) throws Exception
    {
        TableInfo productMapTable = QueryService.get().getUserSchema(user, container, StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        if (productMapTable != null)
        {
            TableSelector selector = new TableSelector(productMapTable, Collections.singleton("RowId"), filter, null);
            Integer[] productMapIds = selector.getArray(Integer.class);

            QueryUpdateService qus = productMapTable.getUpdateService();
            if (qus != null)
            {
                List<Map<String, Object>> keys = new ArrayList<>();
                ColumnInfo productMapPk = productMapTable.getColumn(FieldKey.fromParts("RowId"));
                for (Integer productMapId : productMapIds)
                {
                    keys.add(Collections.<String, Object>singletonMap(productMapPk.getName(), productMapId));
                }

                qus.deleteRows(user, container, keys, null);
            }
            else
                throw new IllegalStateException("Could not find query update service for table: " + StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
        }
        else
            throw new IllegalStateException("Could not find table: " + StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
    }

    public void createVisitDataSetMapping(User user, Container container, int visitId,
                                          int dataSetId, boolean isRequired) throws SQLException
    {
        VisitDataSet vds = new VisitDataSet(container, dataSetId, visitId, isRequired);
        Table.insert(user, SCHEMA.getTableInfoVisitMap(), vds);
    }

    public VisitDataSet getVisitDataSetMapping(Container container, int visitRowId, int dataSetId) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("VisitRowId"), visitRowId);
        filter.addCondition(FieldKey.fromParts("DataSetId"), dataSetId);

        Boolean required = new TableSelector(SCHEMA.getTableInfoVisitMap().getColumn("Required"), filter, null).getObject(Boolean.class);

        return (null != required ? new VisitDataSet(container, dataSetId, visitRowId, required) : null);
    }


    public List<VisitImpl> getVisits(Study study, Visit.Order order)
    {
        return getVisits(study, null, null, order);
    }

    public List<VisitImpl> getVisits(Study study, @Nullable CohortImpl cohort, @Nullable User user, Visit.Order order)
    {
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
            return Collections.emptyList();

        SimpleFilter filter = null;

        if (cohort != null)
        {
            filter = SimpleFilter.createContainerFilter(study.getContainer());
            if (showCohorts(study.getContainer(), user))
                filter.addWhereClause("(CohortId IS NULL OR CohortId = ?)", new Object[] { cohort.getRowId() });
        }

        return _visitHelper.get(study.getContainer(), filter, order.getSortColumns());
    }

    public void clearParticipantVisitCaches(Study study)
    {
        _visitHelper.clearCache(study.getContainer());
        DbCache.clear(StudySchema.getInstance().getTableInfoParticipant());
        for (StudyImpl substudy : StudyManager.getInstance().getAncillaryStudies(study.getContainer()))
            clearParticipantVisitCaches(substudy);
    }


    public VisitImpl getVisitForRowId(Study study, int rowId)
    {
        return _visitHelper.get(study.getContainer(), rowId, "RowId");
    }

    private String getQCStateCacheName(Container container)
    {
        return container.getId() + "/" + QCState.class.toString();
    }
    
    public QCState[] getQCStates(Container container)
    {
        QCState[] states = (QCState[]) DbCache.get(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(container));

        if (states == null)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            states = new TableSelector(StudySchema.getInstance().getTableInfoQCState(), filter, new Sort("Label")).getArray(QCState.class);
            DbCache.put(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(container), states, CacheManager.HOUR);
        }
        return states;
    }

    public boolean showQCStates(Container container)
    {
        return getQCStates(container).length > 0;
    }

    public boolean isQCStateInUse(QCState state)
    {
        StudyImpl study = getStudy(state.getContainer());
        if (safeIntegersEqual(study.getDefaultAssayQCState(), state.getRowId()) ||
            safeIntegersEqual(study.getDefaultDirectEntryQCState(), state.getRowId() )||
            safeIntegersEqual(study.getDefaultPipelineQCState(), state.getRowId()))
        {
            return true;
        }
        SQLFragment f = new SQLFragment();
        f.append("SELECT * FROM ").append(
                StudySchema.getInstance().getTableInfoStudyData(study, null).getFromSQL("SD")).append(
                " WHERE QCState = ?");
        f.add(state.getRowId());

        return new SqlSelector(StudySchema.getInstance().getSchema(), f).exists();
    }

    public QCState insertQCState(User user, QCState state) throws SQLException
    {
        QCState[] preInsertStates = getQCStates(state.getContainer());
        DbCache.remove(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(state.getContainer()));
        QCState newState = Table.insert(user, StudySchema.getInstance().getTableInfoQCState(), state);
        // switching from zero to more than zero QC states affects the columns in our materialized datasets
        // (adding a QC State column), so we unmaterialize them here:
        if (preInsertStates == null || preInsertStates.length == 0)
            clearCaches(state.getContainer(), true);
        return newState;
    }

    public QCState updateQCState(User user, QCState state) throws SQLException
    {
        DbCache.remove(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(state.getContainer()));
        return Table.update(user, StudySchema.getInstance().getTableInfoQCState(), state, state.getRowId());
    }

    public void deleteQCState(QCState state) throws SQLException
    {
        QCState[] preDeleteStates = getQCStates(state.getContainer());
        DbCache.remove(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(state.getContainer()));
        Table.delete(StudySchema.getInstance().getTableInfoQCState(), state.getRowId());

        // removing our last QC state affects the columns in our materialized datasets
        // (removing a QC State column), so we unmaterialize them here:
        if (preDeleteStates.length == 1)
            clearCaches(state.getContainer(), true);

    }

    @Nullable
    public QCState getDefaultQCState(StudyImpl study)
    {
        Integer defaultQcStateId = study.getDefaultDirectEntryQCState();
        QCState defaultQCState = null;
        if (defaultQcStateId != null)
            defaultQCState = StudyManager.getInstance().getQCStateForRowId(
                study.getContainer(), defaultQcStateId.intValue());
        return defaultQCState;
    }

    public QCState getQCStateForRowId(Container container, int rowId)
    {
        QCState[] states = getQCStates(container);
        for (QCState state : states)
        {
            if (state.getRowId() == rowId && state.getContainer().equals(container))
                return state;
        }
        return null;
    }

    private Map<String, VisitImpl> getVisitsForDataRows(DataSetDefinition def, Collection<String> dataLsids)
    {
        final Map<String, VisitImpl> visits = new HashMap<>();

        if (dataLsids == null || dataLsids.isEmpty())
            return visits;

        TableInfo ds = def.getTableInfo(null, false);

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT sd.LSID AS LSID, v.RowId AS RowId FROM ").append(ds.getFromSQL("sd")).append("\n" +
                "JOIN study.ParticipantVisit pv ON \n" +
                "\tsd.SequenceNum = pv.SequenceNum AND\n" +
                "\tsd.ParticipantId = pv.ParticipantId\n" +
                "JOIN study.Visit v ON\n" +
                "\tpv.VisitRowId = v.RowId AND\n" +
                "\tpv.Container = ? AND v.Container = ?\n" +
                "WHERE sd.lsid IN(");
        sql.add(def.getContainer().getId());
        sql.add(def.getContainer().getId());
        boolean first = true;
        for (String dataLsid : dataLsids)
        {
            if (!first)
                sql.append(", ");
            sql.append("?");
            sql.add(dataLsid);
            first = false;
        }
        sql.append(")");

        final Study study = def.getStudy();

        new SqlSelector(StudySchema.getInstance().getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String lsid = rs.getString("LSID");
                int visitId = rs.getInt("RowId");
                visits.put(lsid, getVisitForRowId(study, visitId));
            }
        });

        return visits;
    }

    public List<VisitImpl> getVisitsForDataset(Container container, int datasetId)
    {
        List<VisitImpl> visits = new ArrayList<>();

        DataSetDefinition def = getDataSetDefinition(getStudy(container), datasetId);
        TableInfo ds = def.getTableInfo(null, false);

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT DISTINCT v.RowId AS RowId FROM ").append(ds.getFromSQL("sd")).append("\n" +
                "JOIN study.ParticipantVisit pv ON \n" +
                "\tsd.SequenceNum = pv.SequenceNum AND\n" +
                "\tsd.ParticipantId = pv.ParticipantId\n" +
                "JOIN study.Visit v ON\n" +
                "\tpv.VisitRowId = v.RowId AND\n" +
                "\tpv.Container = ? AND v.Container = ?\n");
        sql.add(container.getId());
        sql.add(container.getId());

        Study study = getStudy(container);
        SqlSelector selector = new SqlSelector(StudySchema.getInstance().getSchema(), sql);
        for (Integer rowId : selector.getArray(Integer.class))
        {
            visits.add(getVisitForRowId(study, rowId));
        }
        return visits;
    }

    public void updateDataQCState(Container container, User user, int datasetId, Collection<String> lsids, QCState newState, String comments)
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        Study study = getStudy(container);
        DataSetDefinition def = getDataSetDefinition(study, datasetId);

        Map<String, VisitImpl> lsidVisits = null;
        if (!def.isDemographicData())
            lsidVisits = getVisitsForDataRows(def, lsids);
        List<Map<String, Object>> rows = def.getDatasetRows(user, lsids);
        if (rows.isEmpty())
            return;

        Map<String, String> oldQCStates = new HashMap<>();
        Map<String, String> newQCStates = new HashMap<>();

        Set<String> updateLsids = new HashSet<>();
        for (Map<String, Object> row : rows)
        {
            String lsid = (String) row.get("lsid");

            Integer oldStateId = (Integer) row.get(DataSetTableImpl.QCSTATE_ID_COLNAME);
            QCState oldState = null;
            if (oldStateId != null)
                oldState = getQCStateForRowId(container, oldStateId);

            // check to see if we're actually changing state.  If not, no-op:
            if (safeIntegersEqual(newState != null ? newState.getRowId() : null, oldStateId))
                continue;

            updateLsids.add(lsid);

            StringBuilder auditKey = new StringBuilder(StudyService.get().getSubjectNounSingular(container) + " ");
            auditKey.append(row.get(StudyService.get().getSubjectColumnName(container)));
            if (!def.isDemographicData())
            {
                VisitImpl visit = lsidVisits.get(lsid);
                auditKey.append(", Visit ").append(visit != null ? visit.getLabel() : "unknown");
            }
            String keyProp = def.getKeyPropertyName();
            if (keyProp != null)
            {
                auditKey.append(", ").append(keyProp).append(" ").append(row.get(keyProp));
            }

            oldQCStates.put(auditKey.toString(), oldState != null ? oldState.getLabel() : "unspecified");
            newQCStates.put(auditKey.toString(), newState != null ? newState.getLabel() : "unspecified");
        }

        if (updateLsids.isEmpty())
            return;

        try (Transaction transction = scope.ensureTransaction())
        {
            // TODO fix updating across study data
            SQLFragment sql = new SQLFragment("UPDATE " + def.getStorageTableInfo().getSelectName() + "\n" +
                    "SET QCState = ");
            // do string concatenation, rather that using a parameter, for the new state id because Postgres null
            // parameters are typed which causes a cast exception trying to set the value back to null (bug 6370)
            sql.append(newState != null ? newState.getRowId() : "NULL");
            sql.append(", modified = ?");
            sql.add(new Date());
            sql.append("\nWHERE lsid IN (");
            boolean first = true;
            for (String dataLsid : updateLsids)
            {
                if (!first)
                    sql.append(", ");
                sql.append("?");
                sql.add(dataLsid);
                first = false;
            }
            sql.append(")");

            new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);

            //def.deleteFromMaterialized(user, updateLsids);
            //def.insertIntoMaterialized(user, updateLsids);

            String auditComment = "QC state was changed for " + updateLsids.size() + " record" +
                    (updateLsids.size() == 1 ? "" : "s") + ".  User comment: " + comments;

            AuditLogEvent event = new AuditLogEvent();
            event.setCreatedBy(user);

            event.setContainerId(container.getId());
            if (container.getProject() != null)
                event.setProjectId(container.getProject().getId());

            event.setIntKey1(datasetId);

            // IntKey2 is non-zero because we have details (a previous or new datamap)
            event.setIntKey2(1);

            event.setEventType(DatasetAuditViewFactory.DATASET_AUDIT_EVENT);
            event.setComment(auditComment);

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put(DatasetAuditViewFactory.OLD_RECORD_PROP_NAME, SimpleAuditViewFactory.encodeForDataMap(oldQCStates, false));
            dataMap.put(DatasetAuditViewFactory.NEW_RECORD_PROP_NAME, SimpleAuditViewFactory.encodeForDataMap(newQCStates, false));
            AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));

            clearCaches(container, false);

            transction.commit();
        }
    }
    
    private boolean safeIntegersEqual(Integer first, Integer second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }

    public boolean showCohorts(Container container, @Nullable User user)
    {
        if (user == null)
            return false;

        if (user.isSiteAdmin())
            return true;

        StudyImpl study = StudyManager.getInstance().getStudy(container);

        if (study == null)
            return false;

        Integer cohortDatasetId = study.getParticipantCohortDataSetId();
        if (study.isManualCohortAssignment() || null == cohortDatasetId || -1 == cohortDatasetId)
        {
            // If we're not reading from a dataset for cohort definition,
            // we use the container's permission
            return SecurityPolicyManager.getPolicy(container).hasPermission(user, ReadPermission.class);
        }

        // Automatic cohort assignment -- can the user read the source dataset?
        DataSetDefinition def = getDataSetDefinition(study, cohortDatasetId);

        if (def != null)
            return def.canRead(user);

        return false;
    }

    public void assertCohortsViewable(Container container, User user)
    {
        if (!showCohorts(container, user))
            throw new UnauthorizedException("User does not have permission to view cohort information");
    }

    public List<CohortImpl> getCohorts(Container container, User user)
    {
        assertCohortsViewable(container, user);
        return _cohortHelper.get(container,"Label");
    }

    public CohortImpl getCurrentCohortForParticipant(Container container, User user, String participantId)
    {
        assertCohortsViewable(container, user);
        Participant participant = getParticipant(getStudy(container), participantId);
        if (participant != null && participant.getCurrentCohortId() != null)
            return _cohortHelper.get(container, participant.getCurrentCohortId().intValue());
        return null;
    }

    public CohortImpl getCohortForRowId(Container container, User user, int rowId)
    {
        assertCohortsViewable(container, user);
        return _cohortHelper.get(container, rowId);
    }

    public CohortImpl getCohortByLabel(Container container, User user, String label)
    {
        assertCohortsViewable(container, user);
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Label"), label);

        List<CohortImpl> cohorts = _cohortHelper.get(container, filter);
        if (cohorts != null && cohorts.size() == 1)
            return cohorts.get(0);

        return null;
    }

    private boolean isCohortInUse(CohortImpl cohort, TableInfo table, String... columnNames)
    {
        List<Object> params = new ArrayList<>();
        params.add(cohort.getContainer().getId());

        StringBuilder cols = new StringBuilder("(");
        String or = "";
        for (String columnName : columnNames)
        {
            cols.append(or).append(columnName).append(" = ?");
            params.add(cohort.getRowId());
            or = " OR ";
        }
        cols.append(")");

        return new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT * FROM " +
                table + " WHERE Container = ? AND " + cols.toString(), params).exists();
    }

    public boolean isCohortInUse(CohortImpl cohort)
    {
        return isCohortInUse(cohort, StudySchema.getInstance().getTableInfoDataSet(), "CohortId") ||
                isCohortInUse(cohort, StudySchema.getInstance().getTableInfoParticipant(), "CurrentCohortId", "InitialCohortId") ||
                isCohortInUse(cohort, StudySchema.getInstance().getTableInfoParticipantVisit(), "CohortId") ||
                isCohortInUse(cohort, StudySchema.getInstance().getTableInfoVisit(), "CohortId");
    }

    public void deleteCohort(CohortImpl cohort) throws SQLException
    {
        StudySchema schema = StudySchema.getInstance();

        try (Transaction transaction = schema.getSchema().getScope().ensureTransaction())
        {
            Container container = cohort.getContainer();

            deleteTreatmentVisitMapForCohort(container, cohort.getRowId());

            _cohortHelper.delete(cohort);

            // delete extended properties
            String lsid = cohort.getLsid();
            Map<String, ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, lsid);
            if (resourceProperties != null && !resourceProperties.isEmpty())
            {
                OntologyManager.deleteOntologyObject(lsid, container, false);
            }

            transaction.commit();
        }
    }


    public VisitImpl getVisitForSequence(Study study, double seqNum)
    {
        List<VisitImpl> visits = getVisits(study, Visit.Order.SEQUENCE_NUM);
        for (VisitImpl v : visits)
        {
            if (seqNum >= v.getSequenceNumMin() && seqNum <= v.getSequenceNumMax())
                return v;
        }
        return null;
    }

    public List<DataSetDefinition> getDataSetDefinitions(Study study)
    {
        return getDataSetDefinitions(study, null);
    }

    public List<DataSetDefinition> getDataSetDefinitions(Study study, @Nullable CohortImpl cohort, String... types)
    {
        SimpleFilter filter = null;
        if (cohort != null)
        {
            filter = SimpleFilter.createContainerFilter(study.getContainer());
            filter.addWhereClause("(CohortId IS NULL OR CohortId = ?)", new Object[] { cohort.getRowId() });
        }

        if (types != null && types.length > 0)
        {
            // ignore during upgrade
            ColumnInfo typeCol = StudySchema.getInstance().getTableInfoDataSet().getColumn("Type");
            if (null != typeCol && !typeCol.isUnselectable())
            {
                if (filter == null)
                    filter = SimpleFilter.createContainerFilter(study.getContainer());
                filter.addInClause(FieldKey.fromParts("Type"), Arrays.asList(types));
            }
        }

        // Make a copy (it's immutable) so that we can sort it. See issue 17875
        List<DataSetDefinition> datasets = new ArrayList<>(_datasetHelper.get(study.getContainer(), filter, null));

        // sort by display order, category, and dataset ID
        Collections.sort(datasets, new Comparator<DataSetDefinition>(){
            @Override
            public int compare(DataSetDefinition o1, DataSetDefinition o2)
            {
                if (o1.getDisplayOrder() != 0 || o2.getDisplayOrder() != 0)
                    return o1.getDisplayOrder() - o2.getDisplayOrder();

                if (StringUtils.equals(o1.getCategory(), o2.getCategory()))
                    return o1.getDataSetId() - o2.getDataSetId();

                if (o1.getCategory() != null && o2.getCategory() == null)
                    return -1;
                if (o1.getCategory() == null && o2.getCategory() != null)
                    return 1;
                if (o1.getCategory() != null && o2.getCategory() != null)
                    return o1.getCategory().compareTo(o2.getCategory());

                return o1.getDataSetId() - o2.getDataSetId();
            }
        });

        return Collections.unmodifiableList(datasets);
    }


    public Set<PropertyDescriptor> getSharedProperties(Study study)
    {
        return _sharedProperties.get(study.getContainer());
    }

    @Nullable
    public DataSetDefinition getDataSetDefinition(Study s, int id)
    {
        DataSetDefinition ds = _datasetHelper.get(s.getContainer(), id, "DataSetId");
        // update old rows w/o entityid
        if (null != ds && null == ds.getEntityId())
        {
            ds.setEntityId(GUID.makeGUID());
            new SqlExecutor(StudySchema.getInstance().getSchema()).execute("UPDATE study.dataset SET entityId=? WHERE container=? and datasetid=? and entityid IS NULL", ds.getEntityId(), ds.getContainer().getId(), ds.getDataSetId());
            _datasetHelper.clearCache(ds);
            ds = _datasetHelper.get(s.getContainer(), id, "DataSetId");
            // calling updateDataSetDefinition() during load (getDatasetDefinition()) may cause recursion problems
            //updateDataSetDefinition(null, ds);
        }
        return ds;
    }

    @Nullable
    public DataSetDefinition getDataSetDefinitionByLabel(Study s, String label)
    {
        if (label == null)
        {
            return null;
        }
        
        SimpleFilter filter = SimpleFilter.createContainerFilter(s.getContainer());
        filter.addWhereClause("LOWER(Label) = ?", new Object[]{label.toLowerCase()}, FieldKey.fromParts("Label"));

        List<DataSetDefinition> defs = _datasetHelper.get(s.getContainer(), filter);
        if (defs != null && defs.size() == 1)
            return defs.get(0);

        return null;
    }


    @Nullable
    public DataSetDefinition getDataSetDefinitionByEntityId(Study s, String entityId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(s.getContainer());
        filter.addCondition(FieldKey.fromParts("EntityId"), entityId);

        List<DataSetDefinition> defs = _datasetHelper.get(s.getContainer(), filter);
        if (defs != null && defs.size() == 1)
            return defs.get(0);

        return null;
    }
    

    @Nullable
    public DataSetDefinition getDataSetDefinitionByName(Study s, String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(s.getContainer());
        filter.addWhereClause("LOWER(Name) = ?", new Object[]{name.toLowerCase()}, FieldKey.fromParts("Name"));

        List<DataSetDefinition> defs = _datasetHelper.get(s.getContainer(), filter);
        if (defs != null && defs.size() == 1)
            return defs.get(0);

        return null;
    }


    @Nullable
    public DataSetDefinition getDatasetDefinitionByQueryName(Study s, String queryName)
    {
        // Try getting by name first, then by label
        DataSetDefinition def = getDataSetDefinitionByName(s, queryName);
        if (null == def)
            def = getDataSetDefinitionByLabel(s, queryName);

        return def;
    }


    // domainURI -> <Container,DatasetId>
    private static Cache<String, Pair<String, Integer>> domainCache = CacheManager.getCache(1000, CacheManager.DAY, "Domain->Dataset map");

    private CacheLoader<String, Pair<String, Integer>> loader = new CacheLoader<String, Pair<String, Integer>>()
    {
        @Override
        public Pair<String, Integer> load(String domainURI, Object argument)
        {
            SQLFragment sql = new SQLFragment();
            sql.append("SELECT Container, DatasetId FROM study.Dataset WHERE TypeURI=?");
            sql.add(domainURI);

            Map<String, Object> map = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getMap();

            if (null == map)
                return null;
            else
                return new Pair<>((String)map.get("Container"), (Integer)map.get("DatasetId"));
        }
    };


    @Nullable
    DataSetDefinition getDatasetDefinition(String domainURI)
    {
        for (int retry=0 ; retry < 2 ; retry++)
        {
            Pair<String,Integer> p = domainCache.get(domainURI, null, loader);
            if (null == p)
                return null;

            Container c = ContainerManager.getForId(p.first);
            Study study = StudyManager.getInstance().getStudy(c);
            if (null != c && null != study)
            {
                DataSetDefinition ret = StudyManager.getInstance().getDataSetDefinition(study, p.second);
                if (null != ret && null != ret.getDomain() && StringUtils.equalsIgnoreCase(ret.getDomain().getTypeURI(), domainURI))
                    return ret;
            }
            domainCache.remove(domainURI);
        }
        return null;
    }


    public List<String> getDatasetLSIDs(User user, DataSetDefinition def) throws ServletException, SQLException
    {
        TableInfo tInfo = def.getTableInfo(user, true);
        return new TableSelector(tInfo.getColumn("lsid")).getArrayList(String.class);
    }


    public void uncache(DataSetDefinition def)
    {
        if (null == def)
            return;

        _datasetHelper.clearCache(def);
        String uri = def.getTypeURI();
        if (null != uri)
            domainCache.remove(uri);

        // Also clear caches of subjects and visits- changes to this dataset may have affected this data:
        clearParticipantVisitCaches(def.getStudy());
    }


    public Map<VisitMapKey,Boolean> getRequiredMap(Study study)
    {
        TableInfo tableVisitMap = StudySchema.getInstance().getTableInfoVisitMap();
        final HashMap<VisitMapKey,Boolean> map = new HashMap<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT DatasetId, VisitRowId, Required FROM " + tableVisitMap + " WHERE Container = ?",
                study.getContainer()).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                map.put(new VisitMapKey(rs.getInt(1), rs.getInt(2)), rs.getBoolean(3));
            }
        });

        return map;
    }



    private static final String VISITMAP_JOIN_BY_VISIT = "SELECT d.*, vm.Required\n" +
            "FROM study.Visit v, study.DataSet d, study.VisitMap vm\n" +
            "WHERE v.RowId = vm.VisitRowId and vm.DataSetId = d.DataSetId and " +
            "v.Container = vm.Container and vm.Container = d.Container " +
            "and v.Container = ? and v.RowId = ?\n" +
            "ORDER BY d.DisplayOrder,d.DataSetId;";

    private static final String VISITMAP_JOIN_BY_DATASET = "SELECT vm.VisitRowId, vm.Required\n" +
            "FROM study.VisitMap vm JOIN study.Visit v ON vm.VisitRowId = v.RowId\n" +
            "WHERE vm.Container = ? AND vm.DataSetId = ?\n" +
            "ORDER BY v.DisplayOrder, v.RowId;";

    List<VisitDataSet> getMapping(final VisitImpl visit)
    {
        if (visit.getContainer() == null)
            throw new IllegalStateException("Visit has no container");

        final List<VisitDataSet> visitDataSets = new ArrayList<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), VISITMAP_JOIN_BY_VISIT,
                visit.getContainer(), visit.getRowId()).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                int dataSetId = rs.getInt("DataSetId");
                boolean isRequired = rs.getBoolean("Required");
                visitDataSets.add(new VisitDataSet(visit.getContainer(), dataSetId, visit.getRowId(), isRequired));
            }
        });

        return visitDataSets;
    }


    public List<VisitDataSet> getMapping(final DataSet dataSet)
    {
        final List<VisitDataSet> visitDataSets = new ArrayList<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), VISITMAP_JOIN_BY_DATASET,
                dataSet.getContainer(), dataSet.getDataSetId()).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                int visitRowId = rs.getInt("VisitRowId");
                boolean isRequired = rs.getBoolean("Required");
                visitDataSets.add(new VisitDataSet(dataSet.getContainer(), dataSet.getDataSetId(), visitRowId, isRequired));

            }
        });

        return visitDataSets;
    }


    public void updateVisitDataSetMapping(User user, Container container, int visitId,
                                          int dataSetId, VisitDataSetType type) throws SQLException
    {
        VisitDataSet vds = getVisitDataSetMapping(container, visitId, dataSetId);
        if (vds == null)
        {
            if (type != VisitDataSetType.NOT_ASSOCIATED)
            {
                // need to insert a new VisitMap entry:
                createVisitDataSetMapping(user, container, visitId,
                        dataSetId, type == VisitDataSetType.REQUIRED);
            }
        }
        else if (type == VisitDataSetType.NOT_ASSOCIATED)
        {
            // need to remove an existing VisitMap entry:
            Table.delete(SCHEMA.getTableInfoVisitMap(),
                    new Object[] { container.getId(), visitId, dataSetId});
        }
        else if ((VisitDataSetType.OPTIONAL == type && vds.isRequired()) ||
                 (VisitDataSetType.REQUIRED == type && !vds.isRequired()))
        {
            Map<String,Object> required = new HashMap<>(1);
            required.put("Required", VisitDataSetType.REQUIRED == type ? Boolean.TRUE : Boolean.FALSE);
            Table.update(user, SCHEMA.getTableInfoVisitMap(), required,
                    new Object[]{container.getId(), visitId, dataSetId});
        }
    }

    public long getNumDatasetRows(User user, DataSet dataset)
    {
        TableInfo sdTable = dataset.getTableInfo(user, false);
        return new TableSelector(sdTable).getRowCount();
    }


    public int purgeDataset(DataSetDefinition dataset, User user)
    {
        return purgeDataset(dataset, null, user);
    }

    /**
     * Delete all rows from a dataset or just those newer than the cutoff date.
     */
    public int purgeDataset(DataSetDefinition dataset, Date cutoff, User user)
    {
        return dataset.deleteRows(user, cutoff);
    }

    /**
     * delete a dataset definition along with associated type, data, visitmap entries
     * @param performStudyResync whether or not to kick off our normal bookkeeping. If the whole study is being deleted,
     * we don't need to bother doing this, for example.
     */
    public void deleteDataset(StudyImpl study, User user, DataSetDefinition ds, boolean performStudyResync)
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();

        deleteDatasetType(study, user, ds);
        try {
            QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(study.getContainer(), 
                    StudySchema.getInstance().getSchemaName(), ds.getName());
            if (def != null)
                def.delete(user);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute("DELETE FROM " + SCHEMA.getTableInfoVisitMap() + "\n" +
                "WHERE Container=? AND DatasetId=?", study.getContainer(), ds.getDataSetId());

        // UNDONE: This is broken
        // this._dataSetHelper.delete(ds);
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute("DELETE FROM " + StudySchema.getInstance().getTableInfoDataSet() + "\n" +
                "WHERE Container=? AND DatasetId=?", study.getContainer(), ds.getDataSetId());
        _datasetHelper.clearCache(study.getContainer());

        SecurityPolicyManager.deletePolicy(ds);

        if (safeIntegersEqual(ds.getDataSetId(), study.getParticipantCohortDataSetId()))
            CohortManager.getInstance().setManualCohortAssignment(study, user, Collections.<String, Integer>emptyMap());

        if (performStudyResync)
        {
            // This dataset may have contained the only references to some subjects or visits; as a result, we need
            // to re-sync the participant and participant/visit tables.  (Issue 12447)
            // Don't provide the deleted dataset in the list of modified datasets- deletion doesn't count as a modification
            // within VisitManager, and passing in the empty set ensures that all subject/visit info will be recalculated.
            getVisitManager(study).updateParticipantVisits(user, Collections.<DataSetDefinition>emptySet());
        }

        unindexDataset(ds);
    }


    /** delete a dataset type and data
     *  does not clear typeURI as we're about to delete the dataset
     */
    private void deleteDatasetType(Study study, User user,  DataSetDefinition ds)
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();

        if (null == ds)
            return;

        StorageProvisioner.drop(ds.getDomain());

        if (ds.getTypeURI() != null)
        {
            try
            {
                OntologyManager.deleteType(ds.getTypeURI(), study.getContainer());
            }
            catch (DomainNotFoundException x)
            {
                // continue
            }

            /*
                ds = ds.createMutable();
                ds.setTypeURI(null);
                updateDataSetDefinition(user, ds);
            */
        }
    }


    // Any container can be passed here (whether it contains a study or not).
    public void clearCaches(Container c, boolean unmaterializeDatasets)
    {
        Study study = getStudy(c);
        _studyHelper.clearCache(c);
        _visitHelper.clearCache(c);
        _locationHelper.clearCache(c);
        AssayManager.get().clearProtocolCache();
        if (unmaterializeDatasets && null != study)
            for (DataSetDefinition def : getDataSetDefinitions(study))
                uncache(def);
        _datasetHelper.clearCache(c);

        DbCache.clear(StudySchema.getInstance().getTableInfoQCState());
        DbCache.clear(StudySchema.getInstance().getTableInfoParticipant());

        for (StudyImpl substudy : StudyManager.getInstance().getAncillaryStudies(c))
            clearCaches(substudy.getContainer(), unmaterializeDatasets);
    }

    public void deleteAllStudyData(Container c, User user) throws SQLException
    {
        // Cancel any reload timer
        StudyReload.cancelTimer(c);

        // No need to delete individual participants if the whole study is going away
        VisitManager.cancelParticipantPurge(c);

        // Before we delete any data, we need to go fetch the Dataset definitions.
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        List<DataSetDefinition> dsds;
        if (study == null) // no study in this folder
            dsds = Collections.emptyList();
        else
            dsds = study.getDataSets();

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        Set<TableInfo> deletedTables = new HashSet<>();
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(c);

        try (Transaction transaction = scope.ensureTransaction())
        {
            StudyDesignManager.get().deleteStudyDesigns(c, deletedTables);
            StudyDesignManager.get().deleteStudyDesignLookupValues(c, deletedTables);

            for (DataSetDefinition dsd : dsds)
                deleteDataset(study, user, dsd, false);

            //
            // samples
            //
            SampleManager.getInstance().deleteAllSampleData(c, deletedTables);

            //
            // assay schedule
            //
            Table.delete(SCHEMA.getTableInfoAssaySpecimenVisit(), containerFilter);
            assert deletedTables.add(SCHEMA.getTableInfoAssaySpecimenVisit());
            Table.delete(_assaySpecimenHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_assaySpecimenHelper.getTableInfo());

            //
            // metadata
            //
            Table.delete(SCHEMA.getTableInfoVisitMap(), containerFilter);
            assert deletedTables.add(SCHEMA.getTableInfoVisitMap());
            Table.delete(StudySchema.getInstance().getTableInfoUploadLog(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoUploadLog());
            Table.delete(_datasetHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_datasetHelper.getTableInfo());
            Table.delete(_locationHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_locationHelper.getTableInfo());
            Table.delete(_visitHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_visitHelper.getTableInfo());
            Table.delete(_studyHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_studyHelper.getTableInfo());

            // participant lists
            Table.delete(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), containerFilter);
            assert deletedTables.add(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap());
            Table.delete(ParticipantGroupManager.getInstance().getTableInfoParticipantGroup(), containerFilter);
            assert deletedTables.add(ParticipantGroupManager.getInstance().getTableInfoParticipantGroup());
            Table.delete(StudySchema.getInstance().getTableInfoParticipantCategory(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoParticipantCategory());

            //
            // participant and assay data (OntologyManager will take care of properties)
            //
            // Table.delete(StudySchema.getInstance().getTableInfoStudyData(null), containerFilter);
            //assert deletedTables.add(StudySchema.getInstance().getTableInfoStudyData(null));
            Table.delete(StudySchema.getInstance().getTableInfoParticipantVisit(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoParticipantVisit());
            Table.delete(StudySchema.getInstance().getTableInfoVisitAliases(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoVisitAliases());
            Table.delete(SCHEMA.getTableInfoParticipant(), containerFilter);
            assert deletedTables.add(SCHEMA.getTableInfoParticipant());
            Table.delete(StudySchema.getInstance().getTableInfoCohort(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoCohort());
            Table.delete(StudySchema.getInstance().getTableInfoParticipantView(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoParticipantView());

            // participant group cohort union view
            assert deletedTables.add(StudySchema.getInstance().getSchema().getTable(StudyQuerySchema.PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME));

            //
            // plate service
            //
            Table.delete(StudySchema.getInstance().getSchema().getTable("Well"), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getSchema().getTable("Well"));
            Table.delete(StudySchema.getInstance().getSchema().getTable("WellGroup"), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getSchema().getTable("WellGroup"));
            Table.delete(StudySchema.getInstance().getTableInfoPlate(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoPlate());

            //
            // reports
            //
            ReportManager.get().deleteReports(c, deletedTables);

            // QC States
            Table.delete(StudySchema.getInstance().getTableInfoQCState(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoQCState());

            // Specimen comments
            Table.delete(StudySchema.getInstance().getTableInfoSpecimenComment(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoSpecimenComment());

            // study data provisioned tables
            //deleteStudyDataProvisionedTables(c, user); // NOTE: this looks to be handled by the OntologyManager
            Table.delete(StudySchema.getInstance().getTableInfoTreatmentVisitMap(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoTreatmentVisitMap());
            Table.delete(StudySchema.getInstance().getTableInfoObjective(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoObjective());
            Table.delete(StudySchema.getInstance().getTableInfoVisitTag(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoVisitTag());

            // dataset tables
            for (DataSetDefinition dsd : dsds)
            {
                fireDatasetChanged(dsd);
            }

            // Clear this container ID from any source and destination columns of study snapshots. Then delete any
            // study snapshots that are orphaned (both source and destination are gone).
            SqlExecutor executor = new SqlExecutor(StudySchema.getInstance().getSchema());
            executor.execute(getStudySnapshotUpdateSql(c, "Source"));
            executor.execute(getStudySnapshotUpdateSql(c, "Destination"));

            Filter orphanedFilter = new SimpleFilter
            (
                new CompareType.CompareClause(FieldKey.fromParts("Source"), CompareType.ISBLANK, null),
                new CompareType.CompareClause(FieldKey.fromParts("Destination"), CompareType.ISBLANK, null)
            );
            Table.delete(StudySchema.getInstance().getTableInfoStudySnapshot(), orphanedFilter);

            assert deletedTables.add(StudySchema.getInstance().getTableInfoStudySnapshot());

            transaction.commit();
        }

        //
        // trust and verify... but only when asserts are on
        //

        assert verifyAllTablesWereDeleted(deletedTables);
    }

    /**
     * Drops the domains for the provisioned study data tables : Product, Treatment, ProductAntigen
     * TreatmentProductMap, TreatmentVisitMap...
     * @param c
     * @param user
     */
    private void deleteStudyDataProvisionedTables(Container c, User user)
    {
        StudyProductDomainKind productDomainKind = new StudyProductDomainKind();
        String productDomainURI = productDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.PRODUCT_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, productDomainURI));

        StudyProductAntigenDomainKind productAntigenDomainKind = new StudyProductAntigenDomainKind();
        String productAntigenDomainURI = productAntigenDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, productAntigenDomainURI));

        StudyTreatmentProductDomainKind studyTreatmentProductDomainKind = new StudyTreatmentProductDomainKind();
        String treatmentProductDomainURI = studyTreatmentProductDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, treatmentProductDomainURI));

        StudyTreatmentDomainKind studyTreatmentDomainKind = new StudyTreatmentDomainKind();
        String treatmentDomainURI = studyTreatmentDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.TREATMENT_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, treatmentDomainURI));

        StudyPersonnelDomainKind studyPersonnelDomainKind = new StudyPersonnelDomainKind();
        String personnelDomainURI = studyPersonnelDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.PERSONNEL_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, personnelDomainURI));
    }

    private SQLFragment getStudySnapshotUpdateSql(Container c, String columnName)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("UPDATE ");
        sql.append(StudySchema.getInstance().getTableInfoStudySnapshot().getSelectName());
        sql.append(" SET ");
        sql.append(columnName);
        sql.append(" = NULL WHERE ");
        sql.append(columnName);
        sql.append(" = ?");
        sql.add(c);

        return sql;
    }

    // TODO: Check that datasets are deleted as well?
    private boolean verifyAllTablesWereDeleted(Set<TableInfo> deletedTables)
    {
        // Pretend like we deleted from StudyData and StudyDataTemplate tables  TODO: why aren't we deleting from these?
        Set<String> deletedTableNames = new CaseInsensitiveHashSet("studydata", "studydatatemplate");

        for (TableInfo t : deletedTables)
        {
            deletedTableNames.add(t.getName());
        }

        StringBuilder missed = new StringBuilder();

        for (String tableName : StudySchema.getInstance().getSchema().getTableNames())
        {
            if (!deletedTableNames.contains(tableName) &&
                    !"specimen".equalsIgnoreCase(tableName) && !"vial".equalsIgnoreCase(tableName) && !"specimenevent".equalsIgnoreCase(tableName))
            {
                missed.append(" ");
                missed.append(tableName);
            }
        }

        if (missed.length() != 0)
            throw new IllegalStateException("Expected to delete from these tables:" + missed);

        return true;
    }

    public ParticipantDataset[] getParticipantDatasets(Container container, Collection<String> lsids) throws SQLException
    {
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("LSID IN (");
        Object[] params = new Object[lsids.size()];
        String comma = "";
        int i = 0;

        for (String lsid : lsids)
        {
            whereClause.append(comma);
            whereClause.append("?");
            params[i++] = lsid;
            comma = ",";
        }

        whereClause.append(")");
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause(whereClause.toString(), params);
        // We can't use the table layer to map results to our bean class because of the unfortunately named
        // "_VisitDate" column in study.StudyData.

        TableInfo sdti = StudySchema.getInstance().getTableInfoStudyData(StudyManager.getInstance().getStudy(container), null);
        List<ParticipantDataset> pds = new ArrayList<>();
        DataSetDefinition dataset = null;

        try (ResultSet rs = new TableSelector(sdti, filter, new Sort("DatasetId")).getResultSet())
        {
            while (rs.next())
            {
                ParticipantDataset pd = new ParticipantDataset();
                pd.setContainer(container);
                int datasetId = rs.getInt("DatasetId");
                if (dataset == null || datasetId != dataset.getDataSetId())
                    dataset = getDataSetDefinition(getStudy(container), datasetId);
                pd.setDataSetId(datasetId);
                pd.setLsid(rs.getString("LSID"));
                if (!dataset.isDemographicData())
                {
                    pd.setSequenceNum(rs.getDouble("SequenceNum"));
                    pd.setVisitDate(rs.getTimestamp("_VisitDate"));
                }
                pd.setParticipantId(rs.getString("ParticipantId"));
                pds.add(pd);
            }
        }

        return pds.toArray(new ParticipantDataset[pds.size()]);
    }


    /**
     * After changing permissions on the study, we have to scrub the dataset acls to
     * remove any groups that no longer have read permission.
     *
     * UNDONE: move StudyManager into model package (so we can have protected access)
     */
    protected void scrubDatasetAcls(Study study, SecurityPolicy newPolicy)
    {
        //for every principal that plays something other than the RestrictedReaderRole,
        //delete that group's role assignments in all dataset policies
        Role restrictedReader = RoleManager.getRole(RestrictedReaderRole.class);

        Set<SecurableResource> resources = new HashSet<>();
        resources.addAll(getDataSetDefinitions(study));

        Set<UserPrincipal> principals = new HashSet<>();

        for (RoleAssignment ra : newPolicy.getAssignments())
        {
            if (!(ra.getRole().equals(restrictedReader)))
                principals.add(SecurityManager.getPrincipal(ra.getUserId()));
        }

        SecurityPolicyManager.clearRoleAssignments(resources, principals);
    }


    public long getParticipantCount(Study study)
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(ParticipantId) FROM ");
        sql.append(SCHEMA.getTableInfoParticipant(), "p");
        sql.append(" WHERE Container = ?");
        sql.add(study.getContainer());
        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getObject(Long.class);
    }

    public String[] getParticipantIds(Study study)
    {
        return getParticipantIds(study, -1);
    }

    public String[] getParticipantIdsForGroup(Study study, int groupId)
    {
        return getParticipantIds(study, groupId, -1);
    }

    public String[] getParticipantIds(Study study, int rowLimit)
    {
        return getParticipantIds(study, -1, rowLimit);
    }

    private String[] getParticipantIds(Study study, int participantGroupId, int rowLimit)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SQLFragment sql = getSQLFragmentForParticipantIds(study, participantGroupId, rowLimit, schema, "ParticipantId");
        return new SqlSelector(schema, sql).getArray(String.class);
    }

    private static final String ALTERNATEID_COLUMN_NAME = "AlternateId";
    private static final String DATEOFFSET_COLUMN_NAME = "DateOffset";
    private static final String PTID_COLUMN_NAME = "ParticipantId";
    public class ParticipantInfo
    {
        private final String _alternateId;
        private final int _dateOffset;

        public ParticipantInfo(String alternateId, int dateOffset)
        {
            _alternateId = alternateId;
            _dateOffset = dateOffset;
        }
        public String getAlternateId()
        {
            return _alternateId;
        }
        public int getDateOffset()
        {
            return _dateOffset;
        }
    }

    public Map<String, ParticipantInfo> getParticipantInfos(Study study, final boolean isShiftDates, final boolean isAlternateIds)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SQLFragment sql = getSQLFragmentForParticipantIds(study, -1, -1, schema, PTID_COLUMN_NAME + ", " + ALTERNATEID_COLUMN_NAME + ", " + DATEOFFSET_COLUMN_NAME);
        final Map<String, ParticipantInfo> alternateIdMap = new HashMap<>();

        new SqlSelector(schema, sql).forEach(new Selector.ForEachBlock<ResultSet>(){
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String participantId = rs.getString(PTID_COLUMN_NAME);
                String alternateId = isAlternateIds ? rs.getString(ALTERNATEID_COLUMN_NAME) : participantId;     // if !isAlternateIds, use participantId
                int dateOffset = isShiftDates ? rs.getInt(DATEOFFSET_COLUMN_NAME) : 0;                            // if !isDateShift, use 0 shift
                alternateIdMap.put(participantId, new ParticipantInfo(alternateId, dateOffset));
            }
        });

        return alternateIdMap;
    }

    private SQLFragment getSQLFragmentForParticipantIds(Study study, int participantGroupId, int rowLimit, DbSchema schema, String columns)
    {
        SQLFragment sql;
        if (participantGroupId == -1)
            sql = new SQLFragment("SELECT " + columns + " FROM " + SCHEMA.getTableInfoParticipant() + " WHERE Container = ? ORDER BY ParticipantId", study.getContainer().getId());
        else
        {
            TableInfo table = StudySchema.getInstance().getTableInfoParticipantGroupMap();
            sql = new SQLFragment("SELECT " + columns + " FROM " + table + " WHERE Container = ? AND GroupId = ? ORDER BY ParticipantId", study.getContainer().getId(), participantGroupId);
        }
        if (rowLimit > 0)
            sql = schema.getSqlDialect().limitRows(sql, rowLimit);
        return sql;
    }

    public String[] getParticipantIdsForCohort(Study study, int currentCohortId, int rowLimit)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SQLFragment sql = new SQLFragment("SELECT ParticipantId FROM " + SCHEMA.getTableInfoParticipant() + " WHERE Container = ? AND CurrentCohortId = ? ORDER BY ParticipantId", study.getContainer().getId(), currentCohortId);

        if (rowLimit > 0)
            sql = schema.getSqlDialect().limitRows(sql, rowLimit);

        return new SqlSelector(schema, sql).getArray(String.class);
    }

    public String[] getParticipantIdsNotInCohorts(Study study)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SQLFragment sql = new SQLFragment("SELECT ParticipantId FROM " + SCHEMA.getTableInfoParticipant() + " WHERE Container = ? AND CurrentCohortId IS NULL",
                study.getContainer().getId());

        return new SqlSelector(schema, sql).getArray(String.class);
    }

    public String[] getParticipantIdsNotInGroupCategory(Study study, int categoryId)
    {
        TableInfo groupMapTable = StudySchema.getInstance().getTableInfoParticipantGroupMap();
        TableInfo tableInfoParticipantGroup = StudySchema.getInstance().getTableInfoParticipantGroup();

        DbSchema schema = StudySchema.getInstance().getSchema();
        SQLFragment sql = new SQLFragment("SELECT ParticipantId FROM " + SCHEMA.getTableInfoParticipant() + " WHERE Container = ? " +
                "AND ParticipantId NOT IN (SELECT DISTINCT ParticipantId FROM " + groupMapTable + " WHERE Container = ? AND " +
                "GroupId IN (SELECT RowId FROM " + tableInfoParticipantGroup + " WHERE CategoryId = ?))",
                study.getContainer().getId(), study.getContainer().getId(), categoryId);

        return new SqlSelector(schema.getScope(), sql).getArray(String.class);
    }

    public static final int ALTERNATEID_DEFAULT_NUM_DIGITS = 6;

    public void clearAlternateParticipantIds(Study study)
    {
        String [] participantIds = getParticipantIds(study);

        for (String participantId : participantIds)
            setAlternateId(study, participantId, null);
    }

    public void generateNeededAlternateParticipantIds(Study study)
    {
        Map<String, ParticipantInfo> participantInfos = getParticipantInfos(study, false, true);

        StudyController.ChangeAlternateIdsForm changeAlternateIdsForm = StudyController.getChangeAlternateIdForm((StudyImpl) study);
        String prefix = changeAlternateIdsForm.getPrefix();
        if (null == prefix)
            prefix = "";        // So we don't get the string "null" as the prefix
        int numDigits = changeAlternateIdsForm.getNumDigits();
        if (numDigits < ALTERNATEID_DEFAULT_NUM_DIGITS)
            numDigits = ALTERNATEID_DEFAULT_NUM_DIGITS;       // Should not happen, but be safe

        HashSet<Integer> usedNumbers = new HashSet<>();
        for (ParticipantInfo participantInfo : participantInfos.values())
        {
            String alternateId = participantInfo.getAlternateId();
            if (alternateId != null)
            {
                try
                {
                    if (0 == prefix.length() || alternateId.startsWith(prefix))
                    {
                        String alternateIdNoPrefix = alternateId.substring(prefix.length());
                        int alternateIdInt = Integer.valueOf(alternateIdNoPrefix);
                        usedNumbers.add(alternateIdInt);
                    }
                }
                catch (NumberFormatException x)
                {
                    // It's possible that the id is not an integer after stripping prefix, because it can be
                    // set explicitly. That's fine, because it won't conflict with what we might generate
                }
            }
        }

        Random random = new Random();
        int firstRandom = (int)Math.pow(10, (numDigits - 1));
        int maxRandom = (int)Math.pow(10, numDigits) - firstRandom;

        for (Map.Entry<String, ParticipantInfo> entry : participantInfos.entrySet())
        {
            ParticipantInfo participantInfo = entry.getValue();
            String alternateId = participantInfo.getAlternateId();

            if (null == alternateId)
            {
                String participantId = entry.getKey();
                int newId = nextRandom(random, usedNumbers, firstRandom, maxRandom);
                setAlternateId(study, participantId, prefix + String.valueOf(newId));
            }
        }
    }

    public int setImportedAlternateParticipantIds(Study study, DataLoader dl, BatchValidationException errors) throws IOException
    {
        // Use first line to determine order of columns we care about
        // The first columcn in the data must contain the ones we are seeking
        String[][] firstline = dl.getFirstNLines(1);
        if (null == firstline || 0 == firstline.length)
            return 0;       // Unexpected but just in case

        boolean seenParticipantId = false;
        boolean seenAlternateIdOrDateOffset = false;
        boolean headerError = false;
        ColumnDescriptor[] columnDescriptors = new ColumnDescriptor[3];
        for (int i = 0; i < 3 && i < firstline[0].length; i += 1)
        {
            String header = firstline[0][i];
            switch (header)
            {
                case PTID_COLUMN_NAME:
                    columnDescriptors[i] = new ColumnDescriptor(PTID_COLUMN_NAME, String.class);
                    seenParticipantId = true;
                    break;
                case ALTERNATEID_COLUMN_NAME:
                    columnDescriptors[i] = new ColumnDescriptor(ALTERNATEID_COLUMN_NAME, String.class);
                    seenAlternateIdOrDateOffset = true;
                    break;
                case DATEOFFSET_COLUMN_NAME:
                    columnDescriptors[i] = new ColumnDescriptor(DATEOFFSET_COLUMN_NAME, Integer.class);
                    seenAlternateIdOrDateOffset = true;
                    break;
                default:
                    if (i < 2)
                        headerError = true;
                    break;
            }
            if (headerError)
                break;
        }

        int rowCount = 0;
        if (!seenParticipantId || !seenAlternateIdOrDateOffset || headerError)
        {
            errors.addRowError(new ValidationException("The header row must contain " + PTID_COLUMN_NAME + " and either " +
                    ALTERNATEID_COLUMN_NAME + ", " + DATEOFFSET_COLUMN_NAME + " or both."));
        }
        else
        {
            assert null != columnDescriptors[0] && null != columnDescriptors[1];        // Since we've seen PTID and 1 other
            if (null == columnDescriptors[2])
                columnDescriptors = Arrays.copyOf(columnDescriptors, 2);    // Can't hand DataLoader a null column

            // Now get loader to load all rows with correct columns and types
            dl.setColumns(columnDescriptors);
            dl.setHasColumnHeaders(true);
            dl.setThrowOnErrors(true);
            dl.setInferTypes(false);

            // Note alternateIds that are already used
            Map<String, ParticipantInfo> participantInfos = getParticipantInfos(study, true, true);
            CaseInsensitiveHashSet usedIds = new CaseInsensitiveHashSet();
            for (ParticipantInfo participantInfo : participantInfos.values())
            {
                String alternateId = participantInfo.getAlternateId();
                if (alternateId != null)
                {
                    usedIds.add(alternateId);
                }
            }

            List<Map<String, Object>> rows = dl.load();
            rowCount = rows.size();

            // Remove used alternateIds for participantIds that are in the list to be changed
            for (Map<String, Object> row : rows)
            {
                String participantId = Objects.toString(row.get(PTID_COLUMN_NAME), null);
                String alternateId = Objects.toString(row.get(ALTERNATEID_COLUMN_NAME), null);
                if (null != participantId && null != alternateId)
                {
                    ParticipantInfo participantInfo = participantInfos.get(participantId);
                    if (null != participantInfo)
                    {
                        String currentAlternateId = participantInfo.getAlternateId();
                        if (null != currentAlternateId && !alternateId.equalsIgnoreCase(currentAlternateId))
                            usedIds.remove(currentAlternateId);     // remove as it will get replaced
                    }
                }
            }

            try (Transaction transaction = StudySchema.getInstance().getSchema().getScope().beginTransaction())
            {
                for (Map<String, Object> row : rows)
                {
                    String participantId = Objects.toString(row.get(PTID_COLUMN_NAME), null);
                    if (null == participantId)
                    {
                        // ParticipantId must be specified
                        errors.addRowError(new ValidationException("A ParticipantId must be specified."));
                        break;
                    }

                    String alternateId = Objects.toString(row.get(ALTERNATEID_COLUMN_NAME), null);
                    Integer dateOffset = (null != row.get(DATEOFFSET_COLUMN_NAME)) ? (Integer)row.get(DATEOFFSET_COLUMN_NAME) : null;

                    if (null == alternateId && null == dateOffset)
                    {
                        errors.addRowError(new ValidationException("Either " + ALTERNATEID_COLUMN_NAME + " or " + DATEOFFSET_COLUMN_NAME + " must be specified."));
                        break;
                    }

                    ParticipantInfo participantInfo = participantInfos.get(participantId);
                    if (null != participantInfo)
                    {
                        String currentAlternateId = participantInfo.getAlternateId();
                        if (null != alternateId && !alternateId.equalsIgnoreCase(currentAlternateId) && usedIds.contains(alternateId))
                        {
                            errors.addRowError(new ValidationException("Two participants may not share the same Alternate ID."));
                            break;
                        }

                        if ((null != alternateId && !alternateId.equalsIgnoreCase(currentAlternateId)) ||
                            (null != dateOffset && dateOffset != participantInfo.getDateOffset()))
                        {

                            setAlternateIdAndDateOffset(study, participantId, alternateId, dateOffset);
                            if (null != alternateId)
                                usedIds.add(alternateId);                 // Add new id
                        }
                    }
                    else
                    {
                        errors.addRowError(new ValidationException("ParticipantID " + participantId + " not found."));
                    }
                }

                if (!errors.hasErrors())
                    transaction.commit();
            }
        }

        if (errors.hasErrors())
            return 0;
        return rowCount;
    }

    private void setAlternateId(Study study, String participantId, @Nullable String alternateId)
    {
        // Set alternateId even if null, because that's how we clear it
        SQLFragment sql = new SQLFragment(String.format(
                "UPDATE %s SET AlternateId = ? WHERE Container = ? AND ParticipantId = ?", SCHEMA.getTableInfoParticipant().getSelectName()),
                alternateId, study.getContainer(), participantId);
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
    }

    private void setAlternateIdAndDateOffset(Study study, String participantId, @Nullable String alternateId, @Nullable Integer dateOffset)
    {
        // Only set alternateId and/or dateOffset if non-null
        assert null != participantId;
        if (null != alternateId || null != dateOffset)
        {
            SQLFragment sql = new SQLFragment("UPDATE " + SCHEMA.getTableInfoParticipant().getSelectName() + " SET ");
            boolean needComma = false;
            if (null != alternateId)
            {
                sql.append("AlternateId = ?").add(alternateId);
                needComma = true;
            }
            if (null != dateOffset)
            {
                if (needComma)
                    sql.append(", ");
                sql.append("DateOffset = ?").add(dateOffset);
            }
            sql.append(" WHERE Container = ? AND ParticipantId = ?");
            sql.add(study.getContainer());
            sql.add(participantId);
            new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
        }
    }

    private int nextRandom(Random random, HashSet<Integer> usedNumbers, int firstRandom, int maxRandom)
    {
        int newId;
        do
        {
            newId = random.nextInt(maxRandom) + firstRandom;
        } while (usedNumbers.contains(newId));
        usedNumbers.add(newId);
        return newId;
    }

    private void parseData(User user,
               DataSetDefinition def,
               DataLoader loader,
               Map<String, String> columnMap)
            throws ServletException, IOException
    {
        TableInfo tinfo = def.getTableInfo(user, false);

        // We're going to lower-case the keys ourselves later,
        // so this needs to be case-insensitive
        if (!(columnMap instanceof CaseInsensitiveHashMap))
        {
            columnMap = new CaseInsensitiveHashMap<>(columnMap);
        }

        // StandardETL will handle most aliasing, HOWEVER, ...
        // columnMap may contain propertyURIs (dataset import job) and labels (GWT import file)
        Map<String,ColumnInfo> nameMap = DataIteratorUtil.createTableMap(tinfo, true);

        //
        // create columns to properties map
        //
        loader.setInferTypes(false);
        ColumnDescriptor[] cols = loader.getColumns();
        for (ColumnDescriptor col : cols)
        {
            String name = col.name.toLowerCase();

            //Special column name
            if ("replace".equals(name))
            {
                col.clazz = Boolean.class;
                col.name = name; //Lower case
                continue;
            }

            // let ETL do conversions
            col.clazz = String.class;

            if (columnMap.containsKey(name))
                name = columnMap.get(name);

            col.name = name;

            ColumnInfo colinfo = nameMap.get(col.name);
            if (null != colinfo)
            {
                col.name = colinfo.getName();
                col.propertyURI = colinfo.getPropertyURI();
            }
        }
    }


    private void batchValidateExceptionToList(BatchValidationException errors, List<String> errorStrs)
    {
        for (ValidationException rowError : errors.getRowErrors())
        {
            String rowPrefix = "";
            if (rowError.getRowNumber() >= 0)
                rowPrefix = "Row " + rowError.getRowNumber() + " ";
            for (ValidationError e : rowError.getErrors())
                errorStrs.add(rowPrefix + e.getMessage());
        }
    }

    /** @deprecated pass in a BatchValidationException, not List<String>  */
    @Deprecated
    public List<String> importDatasetData(User user, DataSetDefinition def, DataLoader loader, Map<String, String> columnMap, List<String> errors, boolean checkDuplicates, QCState defaultQCState, Logger logger)
            throws IOException, ServletException, SQLException
    {
        parseData(user, def, loader, columnMap);
        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
        List<String> lsids = def.importDatasetData(user, loader, context, checkDuplicates, defaultQCState, logger, false);
        batchValidateExceptionToList(context.getErrors(),errors);
        return lsids;
    }

    public List<String> importDatasetData(User user, DataSetDefinition def, DataLoader loader, Map<String, String> columnMap, BatchValidationException errors, boolean checkDuplicates, QCState defaultQCState, Logger logger)
            throws IOException, ServletException, SQLException
    {
        parseData(user, def, loader, columnMap);
        DataIteratorContext context = new DataIteratorContext(errors);
        context.setInsertOption(QueryUpdateService.InsertOption.MERGE);
        return def.importDatasetData(user, loader, context, checkDuplicates, defaultQCState, logger, false);
    }
    

    /** @deprecated pass in a BatchValidationException, not List<String>  */
    @Deprecated
    public List<String> importDatasetData(User user, DataSetDefinition def, List<Map<String, Object>> data, List<String> errors, boolean checkDuplicates, QCState defaultQCState, Logger logger,
                                          boolean forUpdate)
    {
        if (data.isEmpty())
            return Collections.emptyList();

        DataIteratorBuilder it = new ListofMapsDataIterator.Builder(data.get(0).keySet(), data);
        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(forUpdate ? QueryUpdateService.InsertOption.INSERT : QueryUpdateService.InsertOption.IMPORT);
        List<String> lsids = def.importDatasetData(user, it, context, checkDuplicates, defaultQCState, logger, forUpdate);
        batchValidateExceptionToList(context.getErrors(),errors);
        return lsids;
    }

    public boolean importDatasetSchemas(StudyImpl study, final User user, SchemaReader reader, BindException errors) throws IOException, SQLException
    {
        if (errors.hasErrors())
            return false;

        List<Map<String, Object>> mapsImport = reader.getImportMaps();

        List<String> importErrors = new LinkedList<>();
        final Container c = study.getContainer();
        final Map<String, DataSetDefinitionEntry> dataSetDefEntryMap = new HashMap<>();

        // Use a factory to ensure domain URI consistency between imported properties and the dataset.  See #7944.
        DomainURIFactory factory = new DomainURIFactory() {
            public String getDomainURI(String name)
            {
                assert dataSetDefEntryMap.containsKey(name);
                DataSetDefinitionEntry defEntry = dataSetDefEntryMap.get(name);
                return StudyManager.getDomainURI(c, user, name, defEntry.dataSetDefinition.getEntityId());
            }
        };

        // We need to build the datasets (but not save) before we create the property descriptors so that
        // we can use the unique DomainURI for each dataset as part of the PropertyURI
        populateDataSetDefEntryMap(study, reader, user, errors, dataSetDefEntryMap);
        if (errors.hasErrors())
            return false;

        OntologyManager.ListImportPropertyDescriptors list = OntologyManager.createPropertyDescriptors(factory, reader.getTypeNameColumn(), mapsImport, importErrors, c, true);

        if (!importErrors.isEmpty())
        {
            for (String error : importErrors)
                errors.reject("importDatasetSchemas", error);
            return false;
        }

        for (OntologyManager.ImportPropertyDescriptor ipd : list.properties)
        {
            if (null == ipd.domainName || null == ipd.domainURI)
                errors.reject("importDatasetSchemas", "Dataset not specified for property: " + ipd.pd.getName());
        }
        if (errors.hasErrors())
            return false;

        StudyManager manager = StudyManager.getInstance();

        // now actually create the datasets
        for (Map.Entry<String, DataSetDefinitionEntry> entry : dataSetDefEntryMap.entrySet())
        {
            DataSetDefinitionEntry d = entry.getValue();
            DataSetDefinition def = d.dataSetDefinition;

            if (d.isNew)
                manager.createDataSetDefinition(user, def);
            else
                manager.updateDataSetDefinition(user, def);

            if (d.tags != null)
                ReportPropsManager.get().importProperties(def.getEntityId(), study.getContainer(), user, d.tags);
        }

        // now that we actually have datasets, create/update the domains
        Map<String, Domain> domainsMap = new CaseInsensitiveHashMap<>();
        Map<String, List<DomainProperty>> domainsPropertiesMap = new CaseInsensitiveHashMap<>();
        for (OntologyManager.ImportPropertyDescriptor ipd : list.properties)
        {
            Domain d = domainsMap.get(ipd.domainURI);
            if (null == d)
            {
                d = PropertyService.get().getDomain(study.getContainer(), ipd.domainURI);
                if (null == d)
                    d = PropertyService.get().createDomain(study.getContainer(), ipd.domainURI, ipd.domainName);
                domainsMap.put(d.getTypeURI(), d);
                // add all the properties that exist for the domain
                DomainProperty[] existingProperties = d.getProperties();
                List<DomainProperty> l = new ArrayList<>(existingProperties.length);
                Collections.addAll(l, existingProperties);
                domainsPropertiesMap.put(d.getTypeURI(), l);
            }
            // Issue 14569:  during study reimport be sure to look for a column has been deleted.
            // Look at the existing properties for this dataset's domain and
            // remove them as we find them in schema.  If there are any properties left after we've
            // iterated over all the import properties then we need to delete them
            List<DomainProperty> propertiesToDel = domainsPropertiesMap.get(d.getTypeURI());
            DomainProperty p = d.getPropertyByName(ipd.pd.getName());
            propertiesToDel.remove(p);

            if (null != p)
            {
                // Enable the domain to make schema changes for this property if required
                // by dropping/adding the property and its storage at domain save time
                p.setSchemaImport(true);
                OntologyManager.updateDomainPropertyFromDescriptor(p, ipd.pd);
            }
            else
            {
                p = d.addProperty();
                ipd.pd.copyTo(p.getPropertyDescriptor());
                p.setName(ipd.pd.getName());
                p.setRequired(ipd.pd.isRequired());
                p.setDescription(ipd.pd.getDescription());
            }
        }

        // see if we need to delete any columns from an existing domain
        for (Domain d : domainsMap.values())
        {
            List<DomainProperty> propertiesToDel = domainsPropertiesMap.get(d.getTypeURI());
            for (DomainProperty p : propertiesToDel)
            {
                p.delete();
            }

            try
            {
                d.save(user);
            }
            catch (ChangePropertyDescriptorException ex)
            {
                errors.reject("importDatasetSchemas", ex.getMessage() == null ? ex.toString() : ex.getMessage());
                return false;
            }
        }

        for (Map.Entry<String, List<ConditionalFormat>> entry : list.formats.entrySet())
        {
            PropertyService.get().saveConditionalFormats(user, OntologyManager.getPropertyDescriptor(entry.getKey(), study.getContainer()), entry.getValue());
        }

        return true;
    }


    public String getDomainURI(Container c, User u, DataSet def)
    {
        if (null == def)
            return getDomainURI(c, u, null, null);
        else
            return getDomainURI(c, u, def.getName(), def.getEntityId());
    }

    private boolean populateDataSetDefEntryMap(StudyImpl study, SchemaReader reader, User user, BindException errors, Map<String, DataSetDefinitionEntry> defEntryMap)
    {
        StudyManager manager = StudyManager.getInstance();
        Container c = study.getContainer();
        Map<Integer, SchemaReader.DatasetImportInfo> datasetInfoMap = reader.getDatasetInfo();

        for (Map.Entry<Integer, SchemaReader.DatasetImportInfo> entry : datasetInfoMap.entrySet())
        {
            int id = entry.getKey().intValue();
            SchemaReader.DatasetImportInfo info = entry.getValue();
            String name = info.name;
            String label = info.label;
            if (label == null)
            {
                // Default to using the name as the label if none was explicitly specified
                label = name;
            }

            // Check for name conflicts
            DataSet existingDef = manager.getDataSetDefinitionByLabel(study, label);

            if (existingDef != null && existingDef.getDataSetId() != id)
            {
                errors.reject("importDatasetSchemas", "Dataset '" + existingDef.getName() + "' is already using the label '" + label + "'");
                return false;
            }

            existingDef = manager.getDataSetDefinitionByName(study, name);

            if (existingDef != null && existingDef.getDataSetId() != id)
            {
                errors.reject("importDatasetSchemas", "A different dataset already exists with the name " + name);
                return false;
            }

            DataSetDefinition def = manager.getDataSetDefinition(study, id);

            if (def == null)
            {
                def = new DataSetDefinition(study, id, name, label, null, null, null);
                def.setDescription(info.description);
                def.setVisitDatePropertyName(info.visitDatePropertyName);
                def.setShowByDefault(!info.isHidden);
                def.setKeyPropertyName(info.keyPropertyName);
                def.setCategory(info.category);
                def.setKeyManagementType(info.keyManagementType);
                def.setDemographicData(info.demographicData);
                def.setType(info.type);
                defEntryMap.put(name, new DataSetDefinitionEntry(def, true, info.tags));
            }
            else
            {
                def = def.createMutable();
                def.setLabel(label);
                def.setName(name);
                def.setDescription(info.description);
                if (null == def.getTypeURI())
                {
                    def.setTypeURI(getDomainURI(c, user, def));
                }
                else
                {
                    upgradeDomainURI(c, user, def);
                }

                def.setVisitDatePropertyName(info.visitDatePropertyName);
                def.setShowByDefault(!info.isHidden);
                def.setKeyPropertyName(info.keyPropertyName);
                def.setCategory(info.category);
                def.setKeyManagementType(info.keyManagementType);
                def.setDemographicData(info.demographicData);
                defEntryMap.put(name, new DataSetDefinitionEntry(def, false, info.tags));
            }
        }

        return true;
    }

    // Detect if this dataset has an old-style URI without the entityid.  If so, assign a new type URI to this dataset
    // and update the domain descriptor URI
    // old:  urn:lsid:labkey.com:StudyDataset.Folder-6:DEM
    // new:  urn:lsid:labkey.com:StudyDataset.Folder-6:DEM-cbffdfa1-f19b-1030-90dd-bf4ca488b2d0
    private void upgradeDomainURI(Container c, User user, DataSetDefinition def)
    {
        String oldURI = def.getTypeURI();
        String newURI = getDomainURI(c, user, def);

        if (StringUtils.equals(oldURI, newURI))
            return;

        // This dataset has the old uri so upgrade it to use the new URI format
        def.setTypeURI(newURI, true /*upgrade*/);

        // fixup the domain
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(oldURI, c);
        if (null != dd)
        {
            dd.setDomainURI(newURI);
            OntologyManager.updateDomainDescriptor(dd);
        }
    }

    private static String getDomainURI(Container c, User u, String name, String id)
    {
        return DatasetDomainKind.generateDomainURI(name, id, c);
    }


    public VisitManager getVisitManager(StudyImpl study)
    {
        switch (study.getTimepointType())
        {
            case VISIT:
                return new SequenceVisitManager(study);
            case CONTINUOUS:
                return new AbsoluteDateVisitManager(study);
            case DATE:
            default:
                return new RelativeDateVisitManager(study);
        }
    }

    //Create a fixed point number encoding the date.
    public static double sequenceNumFromDate(Date d)
    {
        Calendar cal = DateUtil.newCalendar(d.getTime());
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    public static SQLFragment sequenceNumFromDateSQL(String dateColumnName)
    {
        // Returns a SQL statement that produces a single number from a date, in the form of YYYYMMDD.
        SqlDialect dialect = StudySchema.getInstance().getSqlDialect();
        SQLFragment sql = new SQLFragment();
        sql.append("(10000 * ").append(dialect.getDatePart(Calendar.YEAR, dateColumnName)).append(") + ");
        sql.append("(100 * ").append(dialect.getDatePart(Calendar.MONTH, dateColumnName)).append(") + ");
        sql.append("(").append(dialect.getDatePart(Calendar.DAY_OF_MONTH, dateColumnName)).append(")");
        return sql;
    }

    private String getParticipantCacheName(Container container)
    {
        return container.getId() + "/" + Participant.class.toString();
    }

    private Map<String, Participant> getParticipantMap(Study study)
    {
        Map<String, Participant> participantMap = (Map<String, Participant>) DbCache.get(StudySchema.getInstance().getTableInfoParticipant(), getParticipantCacheName(study.getContainer()));
        if (participantMap == null)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(study.getContainer());
            Participant[] participants = new TableSelector(StudySchema.getInstance().getTableInfoParticipant(),
                    filter, new Sort("ParticipantId")).getArray(Participant.class);
            participantMap = new LinkedHashMap<>();
            for (Participant participant : participants)
                participantMap.put(participant.getParticipantId(), participant);
            DbCache.put(StudySchema.getInstance().getTableInfoParticipant(), getParticipantCacheName(study.getContainer()), participantMap, CacheManager.HOUR);
        }
        return participantMap;
    }

    public void clearParticipantCache(Container container)
    {
        DbCache.remove(StudySchema.getInstance().getTableInfoParticipant(), getParticipantCacheName(container));
    }

    public Participant[] getParticipants(Study study)
    {
        Map<String, Participant> participantMap = getParticipantMap(study);
        Participant[] participants = new Participant[participantMap.size()];
        int i = 0;
        for (Map.Entry<String, Participant> entry : participantMap.entrySet())
            participants[i++] = entry.getValue();
        return participants;
    }

    public Participant getParticipant(Study study, String participantId)
    {
        Map<String, Participant> participantMap = getParticipantMap(study);
        return participantMap.get(participantId);
    }

    public CustomParticipantView getCustomParticipantView(Study study) throws SQLException
    {
        if (study == null)
            return null;

        Set<Module> activeModules = study.getContainer().getActiveModules();
        Set<String> activeModuleNames = new HashSet<>();
        for (Module module : activeModules)
            activeModuleNames.add(module.getName());
        for (Map.Entry<String, Resource> entry : _moduleParticipantViews.entrySet())
        {
            if (activeModuleNames.contains(entry.getKey()) && entry.getValue().exists())
            {
                try (InputStream is = entry.getValue().getInputStream())
                {
                    String body = IOUtils.toString(is);
                    return CustomParticipantView.createModulePtidView(body);
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Unable to load participant view from " + entry.getValue().getPath(), e);
                }
            }
        }

        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(study.getContainer());
        return new TableSelector(StudySchema.getInstance().getTableInfoParticipantView(), containerFilter, null).getObject(CustomParticipantView.class);
    }

    public CustomParticipantView saveCustomParticipantView(Study study, User user, CustomParticipantView view) throws SQLException
    {
        if (view.isModuleParticipantView())
            throw new IllegalArgumentException("Module-defined participant views should not be saved to the database.");
        if (view.getRowId() == null)
        {
            view.beforeInsert(user, study.getContainer().getId());
            return Table.insert(user, StudySchema.getInstance().getTableInfoParticipantView(), view);
        }
        else
        {
            view.beforeUpdate(user);
            return Table.update(user, StudySchema.getInstance().getTableInfoParticipantView(), view, view.getRowId());
        }
    }

    public interface ParticipantViewConfig
    {
        String getParticipantId();

        int getDatasetId();

        String getRedirectUrl();

        QCStateSet getQCStateSet();

        Map<String, String> getAliases();
    }

    public WebPartView<ParticipantViewConfig> getParticipantView(Container container, ParticipantViewConfig config)
    {
        return getParticipantView(container, config, null);
    }

    public WebPartView<ParticipantViewConfig> getParticipantView(Container container, ParticipantViewConfig config, BindException errors)
    {
        StudyImpl study = getStudy(container);
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
            return new BaseStudyController.StudyJspView<>(study, "participantData.jsp", config, errors);
        else
            return new BaseStudyController.StudyJspView<>(study, "participantAll.jsp", config, errors);
    }

    public WebPartView<ParticipantViewConfig> getParticipantDemographicsView(Container container, ParticipantViewConfig config, BindException errors)
    {
        return new BaseStudyController.StudyJspView<>(getStudy(container), "participantCharacteristics.jsp", config, errors);
    }

    /**
     * Called when a dataset has been modified in order to set the modified time, plus any other related actions.
     * @param fireNotification - true to fire the changed notification.
     */
    public static void dataSetModified(DataSetDefinition def, User user, boolean fireNotification)
    {
        // Issue 19285 - run this as a commit task.  This has the benefit of only running per set of batch changes
        // under the same transaction and only running if the transaction is committed.  If no transaction is active then
        // the code is run immediately
        DbScope scope = StudySchema.getInstance().getScope();
        scope.addCommitTask(getInstance().getDataSetModifiedRunnable(def, user, fireNotification), CommitTaskOption.POSTCOMMIT);
    }

    public Runnable getDataSetModifiedRunnable(DataSetDefinition def, User user, boolean fireNotification)
    {
        return new DataSetModifiedRunnable(def, user, fireNotification);
    }

    private class DataSetModifiedRunnable implements Runnable
    {
        private final @NotNull User _user;
        private final @NotNull DataSetDefinition _def;
        private final @NotNull boolean _fireNotification;

        private DataSetModifiedRunnable(@NotNull DataSetDefinition def, @NotNull User user, @NotNull boolean fireNotification)
        {
            _def = def;
            _user = user;
            _fireNotification = fireNotification;
        }

        private int getDatasetId()
        {
            return _def.getDataSetId();
        }

        private Container getContainer()
        {
            return _def.getContainer();
        }

        @Override
        public void run()
        {
            DataSetDefinition def = _def.createMutable();
            def.setModified(new Date());
            def.save(_user);
            if (_fireNotification)
                fireDatasetChanged(def);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            DataSetModifiedRunnable that = (DataSetModifiedRunnable) o;
            if (getDatasetId() != that.getDatasetId())
                return false;
            if (!getContainer().equals(that.getContainer()))
                return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = getContainer().hashCode();
            result = 31 * result + this.getDatasetId();
            return result;
        }
    }

    public static void fireDatasetChanged(DataSet def)
    {
        for (DatasetManager.DatasetListener l : DatasetManager.getListeners())
        {
            try
            {
                l.datasetChanged(def);
            }
            catch (Throwable t)
            {
                _log.error("fireDatasetChanged", t);
            }
        }
    }


    // Return a source->alias map for the specified participant
    public Map<String, String> getAliasMap(StudyImpl study, User user, String ptid)
    {
        @Nullable final TableInfo aliasTable = new StudyQuerySchema(study, user, true).getParticipantAliasesTable();

        if (null == aliasTable)
            return Collections.emptyMap();

        List<ColumnInfo> columns = aliasTable.getColumns();
        SimpleFilter filter = new SimpleFilter(columns.get(0).getFieldKey(), ptid);

        // Return source -> alias map
        return new TableSelector(aliasTable, Arrays.asList(columns.get(2), columns.get(1)), filter, null).getValueMap();
    }


    public void reindex(Container c)
    {
        _enumerateDocuments(null, c);
    }
    

    private void unindexDataset(DataSetDefinition ds)
    {
        String docid = "dataset:" + new Path(ds.getContainer().getId(),String.valueOf(ds.getDataSetId())).toString();
        SearchService ss = ServiceRegistry.get(SearchService.class);
        if (null != ss)
            ss.deleteResource(docid);
    }


    public static void indexDatasets(SearchService.IndexTask task, Container c, Date modifiedSince)
    {
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null == ss) return;
        ResultSet rs = null;
        try
        {
            SQLFragment f = new SQLFragment("SELECT container, datasetid FROM " + StudySchema.getInstance().getTableInfoDataSet());
            if (null != c)
            {
                f.append(" WHERE container = ?");
                f.add(c);
            }
            rs = new SqlSelector(StudySchema.getInstance().getSchema(), f).getResultSet(false, false);

            while (rs.next())
            {
                String container = rs.getString(1);
                int id = rs.getInt(2);

                c = ContainerManager.getForId(container);
                if (null == c) continue;
                Study study = StudyManager.getInstance().getStudy(c);
                if (null == study) continue;
                DataSetDefinition dsd = StudyManager.getInstance().getDataSetDefinition(study, id);
                if (null == dsd) continue;

                indexDataset(task, dsd);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    private static void indexDataset(@Nullable SearchService.IndexTask task, DataSetDefinition dsd)
    {
        if (dsd.getType().equals(DataSet.TYPE_PLACEHOLDER))
            return;
        if (null == dsd.getTypeURI() || null == dsd.getDomain())
            return;
        if (null == task)
            task = ServiceRegistry.get(SearchService.class).defaultTask();
        String docid = "dataset:" + new Path(dsd.getContainer().getId(), String.valueOf(dsd.getDataSetId())).toString();

        StringBuilder body = new StringBuilder();
        Map<String, Object> props = new HashMap<>();

        props.put(SearchService.PROPERTY.categories.toString(), datasetCategory.toString());
        props.put(SearchService.PROPERTY.title.toString(), StringUtils.defaultIfEmpty(dsd.getLabel(),dsd.getName()));
        String name = dsd.getName();
        String label = StringUtils.equals(dsd.getLabel(),name) ? null : dsd.getLabel();
        String description = dsd.getDescription();
        String searchTitle = StringUtilsLabKey.joinNonBlank(" ", name, label, description);
        props.put(SearchService.PROPERTY.keywordsMed.toString(), searchTitle);

        body.append(searchTitle).append("\n");

        StudyQuerySchema schema = new StudyQuerySchema(dsd.getStudy(), User.getSearchUser(), false);
        TableInfo tableInfo = schema.createDatasetTableInternal(dsd);
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, tableInfo.getDefaultVisibleColumns());
        String sep = "";
        for (ColumnInfo column : columns.values())
        {
            String n = StringUtils.trimToEmpty(column.getName());
            String l = StringUtils.trimToEmpty(column.getLabel());
            if (n.equals(l))
                l = "";
            body.append(sep).append(StringUtilsLabKey.joinNonBlank(" ", n, l));
            sep = ",\n";
        }

        ActionURL view = new ActionURL(StudyController.DatasetAction.class, null);
        view.replaceParameter("datasetId", String.valueOf(dsd.getDataSetId()));
        view.setExtraPath(dsd.getContainer().getId());

        SimpleDocumentResource r = new SimpleDocumentResource(new Path(docid), docid,
                "text/plain", body.toString().getBytes(),
                view, props);
        task.addResource(r, SearchService.PRIORITY.item);
    }

    public static void indexParticipants(final SearchService.IndexTask task, @NotNull final Container c, @Nullable List<String> ptids)
    {
        if (null != ptids && ptids.size() == 0)
            return;

        final int BATCH_SIZE = 500;
        if (null != ptids && ptids.size() > BATCH_SIZE)
        {
            ArrayList<String> list = new ArrayList<>(BATCH_SIZE);
            for (String ptid : ptids)
            {
                list.add(ptid);
                if (list.size() == BATCH_SIZE)
                {
                    final ArrayList<String> l = list;
                    Runnable r = new Runnable(){ @Override public void run() {
                        indexParticipants(task, c, l);
                    }};
                    task.addRunnable(r, SearchService.PRIORITY.bulk);
                    list = new ArrayList<>(BATCH_SIZE);
                }
            }
            indexParticipants(task, c, list);
            return;
        }

        final StudyImpl study = StudyManager.getInstance().getStudy(c);
        if (null == study)
            return;
        final String nav = NavTree.toJS(Collections.singleton(new NavTree("study", PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c))), null, false).toString();

        SQLFragment f = new SQLFragment("SELECT Container, ParticipantId FROM " + StudySchema.getInstance().getTableInfoParticipant().getSelectName());
        String prefix = " WHERE ";
        f.append(prefix).append(" Container = ?");
        f.add(c);
        prefix = " AND ";

        if (null != ptids)
        {
            f.append(prefix).append(" ParticipantId IN (");
            String marker="?";
            for (String ptid : ptids)
            {
                f.append(marker);
                f.add(ptid);
                marker = ", ?";
            }
            f.append(")");
        }

        final ActionURL indexURL = new ActionURL(StudyController.IndexParticipantAction.class, c);
        indexURL.setExtraPath(c.getId());
        final ActionURL executeURL = new ActionURL(StudyController.ParticipantAction.class, c);
        executeURL.setExtraPath(c.getId());

        new SqlSelector(StudySchema.getInstance().getSchema(), f).forEach(new Selector.ForEachBlock<ResultSet>(){
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                final String ptid = rs.getString(2);
                String displayTitle = "Study " + study.getLabel() + " -- " +
                        StudyService.get().getSubjectNounSingular(study.getContainer()) + " " + ptid;
                ActionURL execute = executeURL.clone().addParameter("participantId", String.valueOf(ptid));
                Path p = new Path(c.getId(), ptid);
                String docid = "participant:" + p.toString();

                String uniqueIds = ptid;

                // Add all participant alias as high priority uniqueIds
                Map<String, String> aliasMap = StudyManager.getInstance().getAliasMap(study, User.getSearchUser(), ptid);

                if (!aliasMap.isEmpty())
                    uniqueIds = uniqueIds + " " + StringUtils.join(aliasMap.values(), " ");

                Map<String, Object> props = new HashMap<>();
                props.put(SearchService.PROPERTY.categories.toString(), subjectCategory.getName());
                props.put(SearchService.PROPERTY.title.toString(), displayTitle);
                props.put(SearchService.PROPERTY.indentifiersHi.toString(), uniqueIds);
                props.put(SearchService.PROPERTY.navtrail.toString(), nav);

                // Index a barebones participant document for now TODO: Figure out if it's safe to include demographic data or not (can all study users see it?)

                // SimpleDocument
                SimpleDocumentResource r = new SimpleDocumentResource(
                        p, docid,
                        c.getId(),
                        "text/plain",
                        displayTitle.getBytes(),
                        execute, props
                )
                {
                    @Override
                    public void setLastIndexed(long ms, long modified)
                    {
                        StudySchema ss = StudySchema.getInstance();
                        new SqlExecutor(ss.getSchema()).execute("UPDATE " + ss.getTableInfoParticipant().getSelectName() +
                            " SET LastIndexed = ? WHERE Container = ? AND ParticipantId = ?", new Timestamp(ms), c, ptid);
                    }
                };
                task.addResource(r, SearchService.PRIORITY.item);
            }
        });
    }

    
    public void registerParticipantView(Module module, Resource ptidView)
    {
        _moduleParticipantViews.put(module.getName(), ptidView);
    }

    // make sure we don't over do it with multiple calls to reindex the same study (see reindex())
    // add a level of indirection
    // CONSIDER: add some facility like this to SearchService??
    // NOTE: this needs to be reviewed if we use modifiedSince

    final static WeakHashMap<Container,Runnable> _lastEnumerate = new WeakHashMap<>();

    public static void _enumerateDocuments(SearchService.IndexTask t, final Container c)
    {
        if (null == c)
            return;

        final SearchService.IndexTask defaultTask = ServiceRegistry.get(SearchService.class).defaultTask();
        final SearchService.IndexTask task = null==t ? defaultTask : t;
        final Study study = StudyManager.getInstance().getStudy(c);
        if (null == study)
            return;

        Runnable runEnumerate = new Runnable()
        {
            public void run()
            {
                if (task == defaultTask)
                {
                    synchronized (_lastEnumerate)
                    {
                        Runnable r = _lastEnumerate.get(c);
                        if (this != r)
                            return;
                        _lastEnumerate.remove(c);
                    }
                }
                StudyManager.indexDatasets(task, c, null);
                StudyManager.indexParticipants(task, c, null);
                AssayService.get().indexAssays(task, c);
            }
        };

        if (task == defaultTask)
        {
            synchronized (_lastEnumerate)
            {
                _lastEnumerate.put(c, runEnumerate);
            }
        }
        
        task.addRunnable(runEnumerate, SearchService.PRIORITY.crawl);

        // study protocol document
        _enumerateProtocolDocuments(task, study);
    }


    public static void _enumerateProtocolDocuments(SearchService.IndexTask task, @NotNull Study study)
    {
        AttachmentParent parent = ((StudyImpl)study).getProtocolDocumentAttachmentParent();
        if (null == parent)
            return;

        ActionURL begin = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(study.getContainer());
        String nav = NavTree.toJS(Collections.singleton(new NavTree("study", begin)), null, false).toString();
        ActionURL download = new ActionURL(StudyController.ProtocolDocumentDownloadAction.class, study.getContainer());
        AttachmentService.Service serv = AttachmentService.get();
        Path p = study.getContainer().getParsedPath().append("@study");

        for (Attachment att : serv.getAttachments(parent))
        {
            WebdavResource r = serv.getDocumentResource
            (
                    p.append(att.getName()),
                    download.clone().addParameter("name", att.getName()),
                    "\"" + att.getName() + "\" -- Protocol document attached to study " + study.getLabel(),
                    parent, att.getName(), SearchService.fileCategory
            );
            r.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
            task.addResource(r, SearchService.PRIORITY.item);
        }
    }


    public StudyImpl[] getAncillaryStudies(Container sourceStudyContainer)
    {
        // in the upgrade case there  may not be any ancillary studyies
        TableInfo t = StudySchema.getInstance().getTableInfoStudy();
        ColumnInfo ssci = t.getColumn("SourceStudyContainerId");
        if (null == ssci || ssci.isUnselectable())
            return new StudyImpl[0];
        return new TableSelector(StudySchema.getInstance().getTableInfoStudy(),
                new SimpleFilter(FieldKey.fromParts("SourceStudyContainerId"), sourceStudyContainer), null).getArray(StudyImpl.class);
    }

    // Return collection of current snapshots that are configured to refresh specimens
    public Collection<StudySnapshot> getRefreshStudySnapshots()
    {
        SQLFragment sql = new SQLFragment("SELECT ss.* FROM ");
        sql.append(StudySchema.getInstance().getTableInfoStudy(), "s");
        sql.append(" JOIN ");
        sql.append(StudySchema.getInstance().getTableInfoStudySnapshot(), "ss");
        sql.append(" ON s.StudySnapshot = ss.RowId AND Source IS NOT NULL AND Destination IS NOT NULL AND Refresh = ?");
        sql.add(Boolean.TRUE);

        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getCollection(StudySnapshot.class);
    }

    @Nullable
    public StudySnapshot getRefreshStudySnapshot(Integer snapshotId)
    {
        TableSelector selector = new TableSelector(StudySchema.getInstance().getTableInfoStudySnapshot(), new SimpleFilter(FieldKey.fromParts("RowId"), snapshotId), null);

        return selector.getObject(StudySnapshot.class);
    }

    /**
     * Convert a placeholder or 'ghost' dataset to an actual dataset by renaming the target dataset to the placeholder's name,
     * transferring all timepoint requirements from the placeholder to the target and deleting the placeholder dataset.
     */
    public DataSetDefinition linkPlaceHolderDataSet(StudyImpl study, User user, DataSetDefinition expectationDataset, DataSetDefinition targetDataset) throws SQLException
    {
        if (expectationDataset == null || targetDataset == null)
            throw new IllegalArgumentException("Both expectation DataSet and target DataSet must exist");

        if (!expectationDataset.getType().equals(DataSet.TYPE_PLACEHOLDER))
            throw new IllegalArgumentException("Only a DataSet of type : placeholder can be linked");

        if (!targetDataset.getType().equals(DataSet.TYPE_STANDARD))
            throw new IllegalArgumentException("Only a DataSet of type : standard can be linked to");

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            // transfer any timepoint requirements from the ghost to target
            for (VisitDataSet vds : expectationDataset.getVisitDataSets())
            {
                VisitDataSetType type = vds.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.NOT_ASSOCIATED;
                StudyManager.getInstance().updateVisitDataSetMapping(user, study.getContainer(), vds.getVisitRowId(), targetDataset.getDataSetId(), type);
            }

            String name = expectationDataset.getName();
            String label = expectationDataset.getLabel();

            // no need to resync the study, as there should be no data in the expectation dataset
            deleteDataset(study, user, expectationDataset, false);

            targetDataset = targetDataset.createMutable();
            targetDataset.setName(name);
            targetDataset.setLabel(label);
            targetDataset.save(user);

            transaction.commit();
        }

        return targetDataset;
    }
    
    public static class CategoryListener implements ViewCategoryListener
    {
        private StudyManager _instance;

        private CategoryListener(StudyManager instance)
        {
            _instance = instance;
        }

        @Override
        public void categoryDeleted(User user, ViewCategory category) throws Exception
        {
            for (DataSetDefinition def : getDatasetsForCategory(category))
            {
                def = def.createMutable();
                def.setCategoryId(0);
                def.save(user);
            }
        }

        @Override
        public void categoryCreated(User user, ViewCategory category) throws Exception {}

        @Override
        public void categoryUpdated(User user, ViewCategory category) throws Exception
        {
            for (DataSetDefinition def : getDatasetsForCategory(category))
            {
                _instance._datasetHelper.clearCache(def);
                _instance._datasetHelper.clearCache(def.getContainer());
            }
        }

        private List<DataSetDefinition> getDatasetsForCategory(ViewCategory category)
        {
            if (category != null)
            {
                Study study = _instance.getStudy(ContainerManager.getForId(category.getContainerId()));
                if (study != null)
                {
                    SimpleFilter filter = SimpleFilter.createContainerFilter(study.getContainer());
                    filter.addCondition(FieldKey.fromParts("CategoryId"), category.getRowId());
                    return _instance._datasetHelper.get(study.getContainer(), filter);
                }
            }

            return Collections.emptyList();
        }
    }



    public static class DatasetImportTestCase extends Assert
    {
        TestContext _context = null;
        StudyManager _manager = StudyManager.getInstance();

        StudyImpl _studyDateBased = null;
        StudyImpl _studyVisitBased = null;

//        @BeforeClass
        public void createStudy() throws SQLException
        {
            _context = TestContext.get();
            Container junit = JunitUtil.getTestContainer();

            {
                String name = GUID.makeHash();
                Container c = ContainerManager.createContainer(junit,name);
                StudyImpl s = new StudyImpl(c, "Junit Study");
                s.setTimepointType(TimepointType.DATE);
                s.setStartDate(new Date(DateUtil.parseDateTime("2001-01-01")));
                s.setSubjectColumnName("SubjectID");
                s.setSubjectNounPlural("Subjects");
                s.setSubjectNounSingular("Subject");
                s.setSecurityType(SecurityType.BASIC_WRITE);
                s.setStartDate(new Date(DateUtil.parseDateTime("1 Jan 2000")));
                _studyDateBased = StudyManager.getInstance().createStudy(_context.getUser(), s);

                MvUtil.assignMvIndicators(c,
                        new String[] {"X", "Y", "Z"},
                        new String[] {"XXX", "YYY", "ZZZ"});
            }

            {
                String name = GUID.makeHash();
                Container c = ContainerManager.createContainer(junit,name);
                StudyImpl s = new StudyImpl(c, "Junit Study");
                s.setTimepointType(TimepointType.VISIT);
                s.setStartDate(new Date(DateUtil.parseDateTime("2001-01-01")));
                s.setSubjectColumnName("SubjectID");
                s.setSubjectNounPlural("Subjects");
                s.setSubjectNounSingular("Subject");
                s.setSecurityType(SecurityType.BASIC_WRITE);
                _studyVisitBased = StudyManager.getInstance().createStudy(_context.getUser(), s);

                MvUtil.assignMvIndicators(c,
                        new String[] {"X", "Y", "Z"},
                        new String[] {"XXX", "YYY", "ZZZ"});
            }
        }


        int counterDatasetId = 100;
        int counterRow = 0;
        protected enum DatasetType
       {
           NORMAL
           {
               public void configureDataset(DataSetDefinition dd)
               {
                   dd.setKeyPropertyName("Measure");
               }
           },
           DEMOGRAPHIC
           {
               public void configureDataset(DataSetDefinition dd)
               {
                   dd.setDemographicData(true);
               }
           },
           OPTIONAL_GUID
           {
               public void configureDataset(DataSetDefinition dd)
               {
                   dd.setKeyPropertyName("GUID");
                   dd.setKeyManagementType(DataSet.KeyManagementType.GUID);
               }
           };

           public abstract void configureDataset(DataSetDefinition dd);
       }



        DataSet createDataset(Study study, String name, boolean demographic) throws Exception
        {
            if(demographic)
                return createDataset(study, name, DatasetType.DEMOGRAPHIC);
            else
                return createDataset(study, name, DatasetType.NORMAL);
        }


        DataSet createDataset(Study study, String name, DatasetType type) throws Exception
        {
            int id = counterDatasetId++;
            _manager.createDataSetDefinition(_context.getUser(), study.getContainer(), id);
            DataSetDefinition dd = _manager.getDataSetDefinition(study, id);
            dd = dd.createMutable();

            dd.setName(name);
            dd.setLabel(name);
            dd.setCategory("Category");

            type.configureDataset(dd);

            String domainURI = StudyManager.getInstance().getDomainURI(study.getContainer(), null, dd);
            dd.setTypeURI(domainURI);
            OntologyManager.ensureDomainDescriptor(domainURI, dd.getName(), study.getContainer());
            StudyManager.getInstance().updateDataSetDefinition(null, dd);

            // validator
            Lsid lsidValidator = DefaultPropertyValidator.createValidatorURI(PropertyValidatorType.Range);
            IPropertyValidator pvLessThan100 = PropertyService.get().createValidator(lsidValidator.toString());
            pvLessThan100.setName("lessThan100");
            pvLessThan100.setExpressionValue("~lte=100.0");

            // define columns
            Domain domain = dd.getDomain();

            DomainProperty measure = domain.addProperty();
            measure.setName("Measure");
            measure.setPropertyURI(domain.getTypeURI()+"#"+measure.getName());
            measure.setRangeURI(PropertyType.STRING.getTypeUri());
            measure.setRequired(true);

            if(type==DatasetType.OPTIONAL_GUID)
            {
                DomainProperty guid = domain.addProperty();
                guid.setName("GUID");
                guid.setPropertyURI(domain.getTypeURI()+"#"+guid.getName());
                guid.setRangeURI(PropertyType.STRING.getTypeUri());
                guid.setRequired(true);
            }

            DomainProperty value = domain.addProperty();
            value.setName("Value");
            value.setPropertyURI(domain.getTypeURI() + "#" + value.getName());
            value.setRangeURI(PropertyType.DOUBLE.getTypeUri());
            value.setMvEnabled(true);

            // Missing values and validators don't work together, so I need another column
            DomainProperty number = domain.addProperty();
            number.setName("Number");
            number.setPropertyURI(domain.getTypeURI() + "#" + number.getName());
            number.setRangeURI(PropertyType.DOUBLE.getTypeUri());
            number.addValidator(pvLessThan100);

            // save
            domain.save(_context.getUser());

            return study.getDataSet(id);
        }


        @Test
        public void testDateConversion()
        {
            Date d = new Date();
            String iso = DateUtil.toISO(d.getTime(), true);
            DbSchema core = DbSchema.get("core");
            SQLFragment select = new SQLFragment("SELECT ");
            select.append(core.getSqlDialect().getISOFormat(new SQLFragment("?",d)));
            String db = new SqlSelector(core, select).getObject(String.class);
            // SQL SERVER doesn't quite store millesecond precision
            assertEquals(23,iso.length());
            assertEquals(23,db.length());
            assertEquals(iso.substring(0,20), db.substring(0,20));
            String jdbc = (String)JdbcType.VARCHAR.convert(d);
            assertEquals(jdbc, iso);
        }


        private static final double DELTA = 1E-8;

        @Test
        public void test() throws Throwable
        {
            try
            {
                createStudy();
                _testImportDatasetData(_studyDateBased);
                _testDatsetUpdateService(_studyDateBased);
                _testImportDemographicDatasetData(_studyDateBased);
                _testImportDemographicDatasetData(_studyVisitBased);
                _testImportDatasetData(_studyVisitBased);
                _testImportDatasetDataAllowImportGuid(_studyDateBased);
                _testDatasetTransformExport(_studyDateBased);

// TODO
//                _testDatsetUpdateService(_studyVisitBased);
            }
            catch (BatchValidationException x)
            {
                List<ValidationException> l = x.getRowErrors();
                if (null != l && l.size() > 0)
                    throw l.get(0);
                throw x;
            }
            finally
            {
                tearDown();
            }
        }


        private void _testDatsetUpdateService(StudyImpl study) throws Throwable
        {
            StudyQuerySchema ss = new StudyQuerySchema(study, _context.getUser(), false);
            DataSet def = createDataset(study, "A", false);
            TableInfo tt = ss.getTable(def.getName());
            QueryUpdateService qus = tt.getUpdateService();
            BatchValidationException errors = new BatchValidationException();
            assertNotNull(qus);

            Date Jan1 = new Date(DateUtil.parseDateTime("1/1/2011"));
            Date Jan2 = new Date(DateUtil.parseDateTime("2/1/2011"));
            List<Map<String, Object>> rows = new ArrayList<>();

            // insert one row
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++this.counterRow), "Value", 1.0));
            List<Map<String,Object>> ret = qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            String msg = errors.getRowErrors().size() > 0 ? errors.getRowErrors().get(0).toString() : "no message";
            assertFalse(msg, errors.hasErrors());
            Map<String,Object> firstRowMap = ret.get(0);
            String lsidRet = (String)firstRowMap.get("lsid");
            assertNotNull(lsidRet);
            assertTrue("lsid should end with "+":101.A1.20110101.0000.Test"+counterRow + ".  Was: " + lsidRet, lsidRet.endsWith(":101.A1.20110101.0000.Test"+counterRow));

            String lsidFirstRow;

            try (ResultSet rs = new TableSelector(tt).getResultSet())
            {
                assertTrue(rs.next());
                lsidFirstRow = rs.getString("lsid");
                assertEquals(lsidFirstRow, lsidRet);
            }

            // duplicate row
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("Duplicates were found"));

            // different participant
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "B2", "Date", Jan1, "Measure", "Test" + (counterRow), "Value", 2.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            assertFalse(errors.hasErrors());

            // different date
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan2, "Measure", "Test" + (counterRow), "Value", "X"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            assertFalse(errors.hasErrors());

            // different measure
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "X"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            assertFalse(errors.hasErrors());

            // duplicates in batch
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0));
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (counterRow), "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test3
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("Duplicates were found in the database or imported data"));

            // missing participantid
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", null, "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            //study:Label: All dataset rows must include a value for SubjectID
            msg = errors.getRowErrors().get(0).getMessage();
            assertTrue(msg.contains("required") || msg.contains("must include"));
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("SubjectID"));

            // missing date
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", null, "Measure", "Test" + (++counterRow), "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            //study:Label: Row 1 does not contain required field date.
            assertTrue(errors.getRowErrors().get(0).getMessage().toLowerCase().contains("date"));

            // missing required property field (Measure in map)
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", null, "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            //study:Label: Row 1 does not contain required field Measure.
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("required"));
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("Measure"));

            // missing required property field (Measure not in map)
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            //study:Label: Row 1 does not contain required field Measure.
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("does not contain required field"));
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("Measure"));

            // legal MV indicator
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "X"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            assertFalse(errors.hasErrors());

            // illegal MV indicator
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "N/A"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            //study:Label: Could not convert 'N/A' for field Value, should be of type Double
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("should be of type Double"));

            // conversion test
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "100"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            assertFalse(errors.hasErrors());


            // validation test
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1, "Number", 101));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            //study:Label: Value '101.0' for field 'Number' is invalid.
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("is invalid"));

            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (counterRow), "Value", 1, "Number", 99));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            assertFalse(errors.hasErrors());

            // QCStateLabel
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("QCStateLabel", "dirty", "SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1, "Number", 5));
            QCState[] qcstates = StudyManager.getInstance().getQCStates(study.getContainer());
            assertEquals(0, qcstates.length);
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
            assertFalse(errors.hasErrors());
            qcstates = StudyManager.getInstance().getQCStates(study.getContainer());
            assertEquals(1, qcstates.length);
            assertEquals("dirty" , qcstates[0].getLabel());

            // let's try to update a row
            rows.clear(); errors.clear();
            assertTrue(firstRowMap.containsKey("Value"));
            CaseInsensitiveMap<Object> row = new CaseInsensitiveMap<>();
            row.putAll(firstRowMap);
            row.put("Value", 3.14159);
            // TODO why is Number==null OK on insert() but not update()?
            row.put("Number", 1.0);
            rows.add(row);
            List<Map<String, Object>> keys = new ArrayList<>();
            keys.add(PageFlowUtil.mapInsensitive("lsid", lsidFirstRow));
            ret = qus.updateRows(_context.getUser(), study.getContainer(), rows, keys, null);
            assert(ret.size() == 1);
        }


        private void _import(DataSet def, final List<Map<String, Object>> rows, List<String> errors) throws Exception
        {
            DataLoader dl = new MapLoader(rows);
            Map<String,String> columnMap = new CaseInsensitiveHashMap<>();

            StudyManager.getInstance().importDatasetData(
                    _context.getUser(),
                    (DataSetDefinition)def, dl, columnMap,
                    errors, true, null, null);
        }


        private void _testImportDatasetDataAllowImportGuid(Study study) throws Throwable
        {
            int sequenceNum = 0;

            StudyQuerySchema ss = new StudyQuerySchema((StudyImpl) study, _context.getUser(), false);
            DataSet def = createDataset(study, "GU", DatasetType.OPTIONAL_GUID);
            TableInfo tt = ss.getTable(def.getName());

            Date Jan1 = new Date(DateUtil.parseDateTime("1/1/2011"));
            Date Jan2 = new Date(DateUtil.parseDateTime("2/1/2011"));
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            String guid = "GUUUUID";
            Map map = PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++, "GUID", guid);
            importRowVerifyGuid(null, def,  map, tt);

            // duplicate row
            // Issue 12985
            rows.add(map);
            _import(def, rows, errors);
            assertTrue("Expected one error", errors.size() == 1);
            assertTrue("Unexpected error", errors.get(0).contains("duplicate key"));
            assertTrue("Unexpected error", errors.get(0).contains("All rows must have unique SubjectID/Date/GUID values."));

            //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
//                assertTrue(-1 != errors.get(0).indexOf("duplicate key value violates unique constraint"));

            //same participant, guid, different sequenceNum
            importRowVerifyGuid(null, def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++, "GUID", guid), tt);

            //  same GUID,sequenceNum, different different participant
            importRowVerifyGuid(null, def,PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum, "GUID", guid), tt);

            //same subject, sequenceNum, GUID not provided
            importRowVerifyGuid(null, def,PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum), tt);

            //repeat:  should still work
            importRowVerifyGuid(null, def,PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum), tt);
        }

        private void _testImportDatasetData(Study study) throws Throwable
        {
            int sequenceNum = 0;

            StudyQuerySchema ss = new StudyQuerySchema((StudyImpl) study, _context.getUser(), false);
            DataSet def = createDataset(study, "B", false);
            TableInfo tt = ss.getTable(def.getName());

            Date Jan1 = new Date(DateUtil.parseDateTime("1/1/2011"));
            Date Jan2 = new Date(DateUtil.parseDateTime("2/1/2011"));
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> errors = new ArrayList<String>(){
                @Override
                public boolean add(String s)
                {
                    return super.add(s);
                }
            };

            // insert one row
            rows.clear();
            errors.clear();
            rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++));
            _import(def, rows, errors);

            if (errors.size() != 0)
                fail(errors.get(0));
            assertEquals(0, errors.size());

            try (Results results = new TableSelector(tt).getResults())
            {
                assertTrue(results.next());
            }

            // duplicate row
            _import(def, rows, errors);
            //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
            assertTrue(errors.get(0).contains("Duplicates were found"));

            // different participant
            importRow( (String[]) null, def,PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum++));
            importRow( (String[]) null, def,PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum++));

            // different date
            importRow( (String[]) null, def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan2, "Measure", "Test"+(counterRow), "Value", "X", "SequenceNum", sequenceNum++));

            // different measure
            importRow( (String[]) null, def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "X", "SequenceNum", sequenceNum++));

            // duplicates in batch
            rows.clear();
            errors.clear();
            rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum));
            rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 1.0, "SequenceNum", sequenceNum++));
            _import(def, rows, errors);
            //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test3
            assertTrue(errors.get(0).contains("Duplicates were found in the database or imported data"));


            // missing participantid
            importRow( new String[] {"required", "SubjectID"}, def, PageFlowUtil.map("SubjectId", null, "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++));

            // missing date
            if(study==_studyDateBased) //irrelevant for sequential visits
                importRow( new String[] {"required", "date"}, def, PageFlowUtil.map("SubjectId", "A1", "Date", null, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++));

            // missing required property field
            importRow( new String[] {"required", "Measure"}, def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", null, "Value", 1.0, "SequenceNum", sequenceNum++));

            // legal MV indicator
            importRow((String[]) null, def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", "X", "SequenceNum", sequenceNum++));

            // count rows with "X"
            final MutableInt Xcount = new MutableInt(0);
            new TableSelector(tt).forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    if ("X".equals(rs.getString("ValueMVIndicator")))
                        Xcount.increment();
                }
            });

            // legal MV indicator
            importRow((String[]) null, def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", null, "ValueMVIndicator", "X", "SequenceNum", sequenceNum++));

            // should have two rows with "X"
            final MutableInt XcountAgain = new MutableInt(0);
            new TableSelector(tt).forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    if ("X".equals(rs.getString("ValueMVIndicator")))
                        XcountAgain.increment();
                }
            });
            assertEquals(Xcount.intValue() + 1, XcountAgain.intValue());

            // illegal MV indicator
            importRow("Value", def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", "N/A", "SequenceNum", sequenceNum++));

            // conversion test
            importRow((String[]) null, def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", "100", "SequenceNum", sequenceNum++));

            // validation test
            importRow("is invalid", def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1, "Number", 101, "SequenceNum", sequenceNum++));

            rows.clear();
            errors.clear();
            rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 1, "Number", 99, "SequenceNum", sequenceNum++));
            _import(def, rows, errors);
        }

        private void importRow(String expectedError, DataSet def, Map map)  throws Exception
        {
            importRow(new String[] {expectedError}, def, map);
        }

        private void importRowVerifyKey(String[] expectedErrors, DataSet def, Map map, TableInfo tt, String key)  throws Exception
        {
            importRow(expectedErrors, def, map);
            String expectedKey = (String) map.get(key);

            try (ResultSet rs = new TableSelector(tt).getResultSet())
            {
                rs.last();
                assertEquals(expectedKey, rs.getString(key));
            }
        }
        private void importRowVerifyGuid(String[] expectedErrors, DataSet def, Map map, TableInfo tt)  throws Exception
        {
            if(map.containsKey("GUID"))
                importRowVerifyKey(expectedErrors, def, map, tt, "GUID");
            else
            {
                importRow(expectedErrors, def, map);

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    rs.last();
                    String actualKey = rs.getString("GUID");
                    assertTrue("No GUID generated when null GUID provided", actualKey.length() > 0);
                }
            }
        }

        private void importRow(String[] expectedErrors, DataSet def, Map map)  throws Exception
        {
            List rows = new ArrayList();
            List<String> errors = new ArrayList<>();

            rows.add(map);
            _import(def, rows, errors);
            if(expectedErrors == null)
            {
                if(0!= errors.size())
                    fail(errors.get(0));
            }
            else
            {
                for(String expectedError : expectedErrors)
                    assertTrue(errors.get(0).contains(expectedError));
            }

        }

        private void _testImportDemographicDatasetData(Study study) throws Throwable
        {
            StudyQuerySchema ss = new StudyQuerySchema((StudyImpl) study, _context.getUser(), false);
            DataSet def = createDataset(study, "Dem", true);
            TableInfo tt = ss.getTable(def.getName());

            Date Jan1 = new Date(DateUtil.parseDateTime("1/1/2011"));
            Date Jan2 = new Date(DateUtil.parseDateTime("2/1/2011"));
            List rows = new ArrayList();
            List<String> errors = new ArrayList<>();

            TimepointType time = study.getTimepointType();

            if (time == TimepointType.VISIT)
            {
                // insert one row w/visit
                importRow((String[]) null, def,PageFlowUtil.map("SubjectId", "A1", "SequenceNum", 1.0, "Measure", "Test"+(++counterRow), "Value", 1.0));

                assertEquals(0, errors.size());

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    assertTrue(rs.next());
                    assertEquals(1.0, rs.getDouble("SequenceNum"), DELTA);
                }

                // insert one row w/o visit
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A2", "Measure", "Test"+(++counterRow), "Value", 1.0));
                _import(def, rows, errors);
                if (errors.size() != 0)
                    fail(errors.get(0));
                assertEquals(0, errors.size());

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    assertTrue(rs.next());
                    if ("A2".equals(rs.getString("SubjectId")))
                        assertEquals(VisitImpl.DEMOGRAPHICS_VISIT, rs.getDouble("SequenceNum"), DELTA);
                    assertTrue(rs.next());
                    if ("A2".equals(rs.getString("SubjectId")))
                        assertEquals(VisitImpl.DEMOGRAPHICS_VISIT, rs.getDouble("SequenceNum"), DELTA);
                }
            }
            else
            {
                // insert one row w/ date
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan2, "Measure", "Test"+(++counterRow), "Value", 1.0));
                _import(def, rows, errors);
                if (errors.size() != 0)
                    fail(errors.get(0));
                assertEquals(0, errors.size());

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    assertTrue(rs.next());
                    assertEquals(Jan2, new java.util.Date(rs.getTimestamp("date").getTime()));
                }

                importRow((String[]) null, def, PageFlowUtil.map("SubjectId", "A2", "Measure", "Test"+(++counterRow), "Value", 1.0));

                assertEquals(0, errors.size());

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    assertTrue(rs.next());
                    if ("A2".equals(rs.getString("SubjectId")))
                        assertEquals(study.getStartDate(), new java.util.Date(rs.getTimestamp("date").getTime()));
                    assertTrue(rs.next());
                    if ("A2".equals(rs.getString("SubjectId")))
                        assertEquals(study.getStartDate(), new java.util.Date(rs.getTimestamp("date").getTime()));
                }
            }
        }

        private void _testDatasetTransformExport(Study study) throws Throwable
        {
            ResultSet rs = null;

            try
            {
                // create a dataset
                StudyQuerySchema ss = new StudyQuerySchema((StudyImpl) study, _context.getUser(), false);
                DataSet def = createDataset(study, "DS", false);
                TableInfo datasetTI = ss.getTable(def.getName());
                QueryUpdateService qus = datasetTI.getUpdateService();
                BatchValidationException errors = new BatchValidationException();
                assertNotNull(qus);

                // insert one row
                List rows = new ArrayList();
                Date jan1 = new Date(DateUtil.parseDateTime("1/1/2012"));
                rows.add(PageFlowUtil.mapInsensitive("SubjectId", "DS1", "Date", jan1, "Measure", "Test" + (++this.counterRow), "Value", 0.0));
                List<Map<String,Object>> ret = qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null);
                assertFalse(errors.hasErrors());
                Map<String,Object> firstRowMap = ret.get(0);

                // Ensure alternateIds are generated for all participants
                StudyManager.getInstance().generateNeededAlternateParticipantIds(study);

                // query the study.participant table to verify that the dateoffset and alternateID were generated for the ptid row inserted into the dataset
                TableInfo participantTableInfo = StudySchema.getInstance().getTableInfoParticipant();
                List<ColumnInfo> cols = new ArrayList<>();
                cols.add(participantTableInfo.getColumn("participantid"));
                cols.add(participantTableInfo.getColumn("dateoffset"));
                cols.add(participantTableInfo.getColumn("alternateid"));
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition(participantTableInfo.getColumn("participantid"), "DS1");
                filter.addCondition(participantTableInfo.getColumn("container"), study.getContainer());
                rs = QueryService.get().select(participantTableInfo, cols, null, null);

                // store the ptid date offset and alternate ID for verification later
                int dateOffset = -1;
                String alternateId = null;
                while(rs.next())
                {
                    if ("DS1".equals(rs.getString("participantid")))
                    {
                        dateOffset = rs.getInt("dateoffset");
                        alternateId = rs.getString("alternateid");
                        break;
                    }
                }
                assertTrue("Date offset expected to be between 1 and 365", dateOffset > 0 && dateOffset < 366);
                assertNotNull(alternateId);
                rs.close(); rs = null;

                // test "exporting" the dataset data using the date shited values and alternate IDs
                Collection<ColumnInfo> datasetCols = new LinkedHashSet<>(datasetTI.getColumns());
                DatasetWriter.createDateShiftColumns(datasetTI, datasetCols, study.getContainer());
                DatasetWriter.createAlternateIdColumns(datasetTI, datasetCols, study.getContainer());
                rs = QueryService.get().select(datasetTI, datasetCols, null, null);

                // verify values from the transformed dataset
                assertTrue(rs.next());
                assertNotNull(rs.getString("SubjectId"));
                assertFalse("DS1".equals(rs.getString("SubjectId")));
                assertTrue(alternateId.equals(rs.getString("SubjectID")));
                assertTrue(rs.getDate("Date").before((Date)firstRowMap.get("Date")));
                // TODO: calculate the date offset and verify that it matches the firstRowMap.get("Date") value
                assertFalse(rs.next());

                rs.close(); rs = null;
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }

        // TODO
        @Test
        public void _testAutoIncrement()
        {

        }


        // TODO
        @Test
        public void _testGuid()
        {

        }


//        @AfterClass
        public void tearDown()
        {
            if (null != _studyDateBased)
            {
                assertTrue(ContainerManager.delete(_studyDateBased.getContainer(), _context.getUser()));
            }
            if (null != _studyVisitBased)
            {
                assertTrue(ContainerManager.delete(_studyVisitBased.getContainer(), _context.getUser()));
            }
        }
    }

    public static class VisitCreationTestCase extends Assert
    {
        private static final double DELTA = 1E-8;

        @Test
        public void testExistingVisitBased()
        {
            StudyImpl study = new StudyImpl();
            study.setTimepointType(TimepointType.VISIT);

            List<VisitImpl> existingVisits = new ArrayList<>(3);
            existingVisits.add(new VisitImpl(null, 1, 1, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 2, 2, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 2.5, 3.0, null, Visit.Type.BASELINE));

            assertEquals("Should return existing visit", existingVisits.get(0), getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits));
            assertEquals("Should return existing visit", existingVisits.get(1), getInstance().ensureVisitWithoutSaving(study, 2, Visit.Type.BASELINE, existingVisits));
            assertEquals("Should return existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 2.5, Visit.Type.BASELINE, existingVisits));
            assertEquals("Should return existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 3.0, Visit.Type.BASELINE, existingVisits));

            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.1, Visit.Type.BASELINE, existingVisits), existingVisits, 1.1, 1.1);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3.001, Visit.Type.BASELINE, existingVisits), existingVisits, 3.001, 3.001);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 4, Visit.Type.BASELINE, existingVisits), existingVisits, 4, 4);
        }

        @Test
        public void testEmptyVisitBased()
        {
            StudyImpl study = new StudyImpl();
            study.setTimepointType(TimepointType.VISIT);

            List<VisitImpl> existingVisits = new ArrayList<>();

            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.1, Visit.Type.BASELINE, existingVisits), existingVisits, 1.1, 1.1);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3.001, Visit.Type.BASELINE, existingVisits), existingVisits, 3.001, 3.001);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 4, Visit.Type.BASELINE, existingVisits), existingVisits, 4, 4);
        }

        @Test
        public void testEmptyDateBased()
        {
            StudyImpl study = new StudyImpl();
            study.setTimepointType(TimepointType.DATE);

            List<VisitImpl> existingVisits = new ArrayList<>();

            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits), existingVisits, 1, 1);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -10, Visit.Type.BASELINE, existingVisits), existingVisits, -10, -10);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5);

            study.setDefaultTimepointDuration(7);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 6);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 6);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 6, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 6);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 7, Visit.Type.BASELINE, existingVisits), existingVisits, 7, 13);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 10, Visit.Type.BASELINE, existingVisits), existingVisits, 7, 13);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 15, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 20);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -10, Visit.Type.BASELINE, existingVisits), existingVisits, -10, -10);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5);
        }

        @Test
        public void testExistingDateBased()
        {
            StudyImpl study = new StudyImpl();
            study.setTimepointType(TimepointType.DATE);

            List<VisitImpl> existingVisits = new ArrayList<>(3);
            existingVisits.add(new VisitImpl(null, 1, 1, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 2, 2, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 7, 13, null, Visit.Type.BASELINE));

            assertSame("Should be existing visit", existingVisits.get(0), getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(1), getInstance().ensureVisitWithoutSaving(study, 2, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 7, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 10, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 13, Visit.Type.BASELINE, existingVisits));

            study.setDefaultTimepointDuration(7);
            assertSame("Should be existing visit", existingVisits.get(0), getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(1), getInstance().ensureVisitWithoutSaving(study, 2, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 7, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 10, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 13, Visit.Type.BASELINE, existingVisits));
        }

        @Test
        public void testCreationDateBased()
        {
            StudyImpl study = new StudyImpl();
            study.setTimepointType(TimepointType.DATE);

            List<VisitImpl> existingVisits = new ArrayList<>(4);
            existingVisits.add(new VisitImpl(null, 1, 1, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 2, 2, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 7, 13, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 62, 64, null, Visit.Type.BASELINE));

            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 3);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 14, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 14);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -14, Visit.Type.BASELINE, existingVisits), existingVisits, -14, -14);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 0);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0.5, Visit.Type.BASELINE, existingVisits), existingVisits, 0.5, 0.5);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -5, Visit.Type.BASELINE, existingVisits), existingVisits, -5, -5);

            study.setDefaultTimepointDuration(7);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 4, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 5, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 6, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 14, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 20, "Week 3");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 21, Visit.Type.BASELINE, existingVisits), existingVisits, 21, 27, "Week 4");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 0, "Day 0");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0.5, Visit.Type.BASELINE, existingVisits), existingVisits, 0.5, 0.5, "Day 0.5");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5, "Day 1.5");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -5, Visit.Type.BASELINE, existingVisits), existingVisits, -5, -5, "Day -5");

            study.setDefaultTimepointDuration(30);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 4, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 5, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 6, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 14, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 29, "Day 14 - 29");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 21, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 29, "Day 14 - 29");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 29, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 29, "Day 14 - 29");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 30, Visit.Type.BASELINE, existingVisits), existingVisits, 30, 59, "Month 2");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 60, Visit.Type.BASELINE, existingVisits), existingVisits, 60, 61, "Day 60 - 61");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 61, Visit.Type.BASELINE, existingVisits), existingVisits, 60, 61, "Day 60 - 61");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 65, Visit.Type.BASELINE, existingVisits), existingVisits, 65, 89, "Day 65 - 89");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 100, Visit.Type.BASELINE, existingVisits), existingVisits, 90, 119, "Month 4");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 0, "Day 0");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0.5, Visit.Type.BASELINE, existingVisits), existingVisits, 0.5, 0.5, "Day 0.5");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5, "Day 1.5");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -5, Visit.Type.BASELINE, existingVisits), existingVisits, -5, -5, "Day -5");
        }

        @Test
        public void testVisitDescription()
        {
            StudyImpl study = new StudyImpl();
            study.setTimepointType(TimepointType.DATE);

            List<VisitImpl> existingVisits = new ArrayList<>();

            VisitImpl newVisit = getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits);
            newVisit.setDescription("My custom visit description");
            validateNewVisit(newVisit, existingVisits, 1, 1, "Day 1", "My custom visit description");
        }

        private void validateNewVisit(VisitImpl newVisit, List<VisitImpl> existingVisits, double seqNumMin, double seqNumMax, String label, String description)
        {
            validateNewVisit(newVisit, existingVisits, seqNumMin, seqNumMax, label);
            assertEquals("Descriptions don't match", description, newVisit.getDescription());
        }

        private void validateNewVisit(VisitImpl newVisit, List<VisitImpl> existingVisits, double seqNumMin, double seqNumMax, String label)
        {
            validateNewVisit(newVisit, existingVisits, seqNumMin, seqNumMax);
            assertEquals("Labels don't match", label, newVisit.getLabel());
        }
        private void validateNewVisit(VisitImpl newVisit, List<VisitImpl> existingVisits, double seqNumMin, double seqNumMax)
        {
            for (VisitImpl existingVisit : existingVisits)
            {
                assertNotSame("Should be a new visit", newVisit, existingVisit);
            }
            assertEquals("Shouldn't have a rowId yet", 0, newVisit.getRowId());
            assertEquals("Wrong sequenceNumMin", seqNumMin, newVisit.getSequenceNumMin(), DELTA);
            assertEquals("Wrong sequenceNumMax", seqNumMax, newVisit.getSequenceNumMax(), DELTA);
        }
    }
}
