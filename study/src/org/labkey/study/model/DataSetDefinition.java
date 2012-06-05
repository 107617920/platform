/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.ScrollableDataIterator;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.etl.ErrorIterator;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.Pump;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.etl.StandardETL;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleUpgrader;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.query.DataSetTableImpl;
import org.labkey.study.query.StudyQuerySchema;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:29:31 AM
 */
public class DataSetDefinition extends AbstractStudyEntity<DataSetDefinition> implements Cloneable, DataSet<DataSetDefinition>
{
    // standard string to use in URLs etc.
    public static final String DATASETKEY = "datasetId";
//    static final Object MANAGED_KEY_LOCK = new Object();
    private static Logger _log = Logger.getLogger(DataSetDefinition.class);

    private StudyImpl _study;
    private int _dataSetId;
    private String _name;
    private String _typeURI;
    private String _category;
    private Integer _categoryId;
    private String _visitDatePropertyName;
    private String _keyPropertyName;
    private @NotNull KeyManagementType _keyManagementType = KeyManagementType.None;
    private String _description;
    private boolean _demographicData; //demographic information, sequenceNum
    private Integer _cohortId;
    private Integer _protocolId; // indicates that dataset came from an assay. Null indicates no source assay
    private String _fileName; // Filename from the original import  TODO: save this at import time and load it from db
    private Date _modified;
    private String _type = DataSet.TYPE_STANDARD;

    private static final String[] BASE_DEFAULT_FIELD_NAMES_ARRAY = new String[]
    {
        "ParticipantID",
        "ptid",
        "SequenceNum", // used in both date-based and visit-based studies
        "DatasetId",
        "SiteId",
        "Created",
        "CreatedBy",
        "Modified",
        "ModifiedBy",
        "sourcelsid",
        "QCState",
        "visitRowId",
        "lsid",
        "Dataset",
        "ParticipantSequenceNum",
        // The following columns names don't refer to actual built-in dataset columns, but
        // they're used by import ('replace') or are commonly used/confused synonyms for built-in column names
        "replace",
        "visit",
        "participant"
    };

    private static final String[] DEFAULT_ABSOLUTE_DATE_FIELD_NAMES_ARRAY = new String[]
    {
        "Date",
        "VisitDate",
    };

    private static final String[] DEFAULT_RELATIVE_DATE_FIELD_NAMES_ARRAY = new String[]
    {
        "Day"
    };

    private static final String[] DEFAULT_VISIT_FIELD_NAMES_ARRAY = new String[]
    {
        "VisitSequenceNum"
    };

    // fields to hide on the dataset schema view
    private static final String[] HIDDEN_DEFAULT_FIELD_NAMES_ARRAY = new String[]
    {
        "sourcelsid",
        "QCState",
        "visitRowId",
        "lsid",
        "Dataset",
        "ParticipantSequenceNum"
    };

    static final Set<String> DEFAULT_ABSOLUTE_DATE_FIELDS;
    static final Set<String> DEFAULT_RELATIVE_DATE_FIELDS;
    static final Set<String> DEFAULT_VISIT_FIELDS;
    private static final Set<String> HIDDEN_DEFAULT_FIELDS = Sets.newCaseInsensitiveHashSet(HIDDEN_DEFAULT_FIELD_NAMES_ARRAY);

    static
    {
        DEFAULT_ABSOLUTE_DATE_FIELDS = Sets.newCaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_ABSOLUTE_DATE_FIELDS.addAll(Sets.newCaseInsensitiveHashSet(DEFAULT_ABSOLUTE_DATE_FIELD_NAMES_ARRAY));

        DEFAULT_RELATIVE_DATE_FIELDS = Sets.newCaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_RELATIVE_DATE_FIELDS.addAll(Sets.newCaseInsensitiveHashSet(DEFAULT_ABSOLUTE_DATE_FIELD_NAMES_ARRAY));
        DEFAULT_RELATIVE_DATE_FIELDS.addAll(Sets.newCaseInsensitiveHashSet(DEFAULT_RELATIVE_DATE_FIELD_NAMES_ARRAY));

        DEFAULT_VISIT_FIELDS = Sets.newCaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_VISIT_FIELDS.addAll(Sets.newCaseInsensitiveHashSet(DEFAULT_VISIT_FIELD_NAMES_ARRAY));
    }

    public DataSetDefinition()
    {
    }


    public DataSetDefinition(StudyImpl study, int dataSetId)
    {
        _study = study;
        setContainer(_study.getContainer());
        _dataSetId = dataSetId;
        _name = String.valueOf(dataSetId);
        _label =  String.valueOf(dataSetId);
        _typeURI = null;
        _showByDefault = true;
    }


    public DataSetDefinition(StudyImpl study, int dataSetId, String name, String label, String category, @Nullable String typeURI)
    {
        _study = study;
        setContainer(_study.getContainer());
        _dataSetId = dataSetId;
        _name = name;
        _label = label;
        _category = category;
        _typeURI = null != typeURI ? typeURI : DatasetDomainKind.generateDomainURI(name,getContainer());
        _showByDefault = true;
    }


    public static boolean isDefaultFieldName(String fieldName, Study study)
    {
        String subjectCol = StudyService.get().getSubjectColumnName(study.getContainer());
        if (subjectCol.equalsIgnoreCase(fieldName))
            return true;
        
        switch (study.getTimepointType())
        {
            case VISIT:
                return DEFAULT_VISIT_FIELDS.contains(fieldName);
            case CONTINUOUS:
                return DEFAULT_ABSOLUTE_DATE_FIELDS.contains(fieldName);
            case DATE:
            default:
                return DEFAULT_RELATIVE_DATE_FIELDS.contains(fieldName);
        }
    }


    public static boolean showOnManageView(String fieldName, Study study)
    {
        return !HIDDEN_DEFAULT_FIELDS.contains(fieldName);
    }


    public Set<String> getDefaultFieldNames()
    {
        TimepointType timepointType = getStudy().getTimepointType();
        Set<String> fieldNames =
                timepointType == TimepointType.VISIT ? DEFAULT_VISIT_FIELDS :
                timepointType == TimepointType.CONTINUOUS ? DEFAULT_ABSOLUTE_DATE_FIELDS:
                DEFAULT_RELATIVE_DATE_FIELDS;

        return Collections.unmodifiableSet(fieldNames);
    }


    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getFileName()
    {
        if (null == _fileName)
        {
            NumberFormat dsf = new DecimalFormat("dataset000.tsv");

            return dsf.format(getDataSetId());
        }
        else
        {
            return _fileName;
        }
    }

    public void setFileName(String fileName)
    {
        _fileName = fileName;
    }

    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
    {
        verifyMutability();

        if (category != null)
            _category = category;
    }

    public Integer getCategoryId()
    {
        return _categoryId;
    }

    public void setCategoryId(Integer categoryId)
    {
        verifyMutability();
        _categoryId = categoryId;

        if (_categoryId != null)
        {
            ViewCategory category = ViewCategoryManager.getInstance().getCategory(_categoryId);
            if (category != null)
                _category = category.getLabel();
        }
    }

    public int getDataSetId()
    {
        return _dataSetId;
    }

    public void setDataSetId(int dataSetId)
    {
        verifyMutability();
        _dataSetId = dataSetId;
    }

    @Override
    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        verifyMutability();
        _modified = modified;
    }

    public String getTypeURI()
    {
        return _typeURI;
    }

    public void setTypeURI(String typeURI)
    {
        verifyMutability();
        if (StringUtils.equals(typeURI, _typeURI))
            return;
        if (null != _typeURI)
            throw new IllegalStateException("TypeURI is already set");
        _typeURI = typeURI;
    }


    public String getPropertyURI(String column)
    {
        PropertyDescriptor pd = DataSetDefinition.getStandardPropertiesMap().get(column);
        if (null != pd)
            return pd.getPropertyURI();
        return _typeURI + "." + column;
    }


    public VisitDataSetType getVisitType(int visitRowId)
    {
        VisitDataSet vds = getVisitDataSet(visitRowId);
        if (vds == null)
            return VisitDataSetType.NOT_ASSOCIATED;
        else if (vds.isRequired())
            return VisitDataSetType.REQUIRED;
        else
            return VisitDataSetType.OPTIONAL;
    }


    public List<VisitDataSet> getVisitDataSets()
    {
        return Collections.unmodifiableList(StudyManager.getInstance().getMapping(this));
    }


    public VisitDataSet getVisitDataSet(int visitRowId)
    {
        List<VisitDataSet> dataSets = getVisitDataSets();
        for (VisitDataSet vds : dataSets)
        {
            if (vds.getVisitRowId() == visitRowId)
                return vds;
        }
        return null;
    }


    public int getRowId()
    {
        return getDataSetId();
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }


    public static TableInfo getTemplateTableInfo()
    {
        return StudySchema.getInstance().getSchema().getTable("studydatatemplate");
    }
    

    /**
     * Get table info representing dataset.  This relies on the DataSetDefinition being removed from
     * the cache if the dataset type changes.  The temptable version also relies on the dataset being
     * uncached when data is updated.
     *
     * see StudyManager.importDatasetTSV()
     */
    public TableInfo getTableInfo(User user) throws UnauthorizedException
    {
        return getTableInfo(user, true);
    }


    public DatasetSchemaTableInfo getTableInfo(User user, boolean checkPermission) throws UnauthorizedException
    {
        //noinspection ConstantConditions
        if (user == null && checkPermission)
            throw new IllegalArgumentException("user cannot be null");

        if (checkPermission && !canRead(user))
        {
            throw new UnauthorizedException();
        }

        return new DatasetSchemaTableInfo(this, user);
    }


    /** why do some datasets have a typeURI, but no domain? */
    private synchronized Domain ensureDomain()
    {
        if (null == getTypeURI())
            throw new IllegalStateException();
        Domain d = getDomain();
        if (null == d)
        {
            _domain = PropertyService.get().createDomain(getContainer(), getTypeURI(), getName());
            try
            {
                _domain.save(null);
            }
            catch (ChangePropertyDescriptorException x)
            {
                throw new RuntimeException(x);
            }
        }
        return _domain;
    }


    private synchronized TableInfo loadStorageTableInfo()
    {
        if (null == getTypeURI())
            return null;

        Domain d = ensureDomain();

        // create table may set storageTableName() so uncache _domain
        if (null == d.getStorageTableName())
            _domain = null;

        TableInfo ti = StorageProvisioner.createTableInfo(d, StudySchema.getInstance().getSchema());

        TableInfo template = getTemplateTableInfo();

        for (PropertyStorageSpec pss : d.getDomainKind().getBaseProperties())
        {
            ColumnInfo c = ti.getColumn(pss.getName());
            ColumnInfo t = template.getColumn(pss.getName());
            // The column may be null if the dataset is being deleted in the background
            if (null != t && c != null)
            {
                c.setExtraAttributesFrom(t);

                // When copying a column, the hidden bit is not propagated, so we need to do it manually
                if (t.isHidden())
                    c.setHidden(true);
            }
        }

        return ti;
    }


    /**
     *  just a wrapper for StorageProvisioner.create()
     */
    public synchronized void provisionTable()
    {
        _domain = null;
        if (null == getTypeURI())
        {
            DataSetDefinition d = this.createMutable();
            d.setTypeURI(DatasetDomainKind.generateDomainURI(getName(),getContainer()));
            d.save(null);
        }
        ensureDomain();
        loadStorageTableInfo();
        StudyManager.getInstance().uncache(this);
    }


    TableInfo _storageTable = null;
    

    /** I think the caching semantics of the dataset are such that I can cache the StorageTableInfo in a member */
    public TableInfo getStorageTableInfo() throws UnauthorizedException
    {
        if (null == _storageTable)
            _storageTable = loadStorageTableInfo();
        return _storageTable;
    }


    public int deleteRows(User user, Date cutoff)
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();
        int count;

        TableInfo table = getStorageTableInfo();

        try
        {
            CPUTimer time = new CPUTimer("purge");
            time.start();

            SQLFragment studyDataFrag = new SQLFragment("DELETE FROM " + table + "\n");
            if (cutoff != null)
                studyDataFrag.append(" AND _VisitDate > ?").add(cutoff);
            count = Table.execute(StudySchema.getInstance().getSchema(), studyDataFrag);
            StudyManager.dataSetModified(this, user, true);

            time.stop();
            _log.debug("purgeDataset " + getDisplayString() + " " + DateUtil.formatDuration(time.getTotal()/1000));
        }
        catch (SQLException s)
        {
            String sqlState = StringUtils.defaultString("");
            if ("42P01".equals(sqlState) || "42S02".equals(sqlState)) // UNDEFINED TABLE
                return 0;
            throw new RuntimeSQLException(s);
        }
        return count;
    }


    public boolean isDemographicData()
    {
        return _demographicData;
    }

    @Override
    public boolean isAssayData()
    {
        return _protocolId != null;
    }

    public void setDemographicData(boolean demographicData)
    {
        verifyMutability();
        _demographicData = demographicData;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        verifyMutability();
        _type = type;
    }

    public StudyImpl getStudy()
    {
        if (null == _study)
            _study = StudyManager.getInstance().getStudy(getContainer());
        return _study;
    }

    public Set<Class<? extends Permission>> getPermissions(UserPrincipal user)
    {
        Set<Class<? extends Permission>> result = new HashSet<Class<? extends Permission>>();

        //if the study security type is basic read or basic write, use the container's policy instead of the
        //study's policy. This will enable us to "remember" the study-level role assignments in case we want
        //to switch back to them in the future
        SecurityType securityType = getStudy().getSecurityType();
        SecurityPolicy studyPolicy = (securityType == SecurityType.BASIC_READ || securityType == SecurityType.BASIC_WRITE) ?
                SecurityManager.getPolicy(getContainer()) : SecurityManager.getPolicy(getStudy());

        //need to check both the study's policy and the dataset's policy
        //users that have read permission on the study can read all datasets
        //users that have read-some permission on the study must also have read permission on this dataset
        if (studyPolicy.hasPermission(user, ReadPermission.class) ||
            (studyPolicy.hasPermission(user, ReadSomePermission.class) && SecurityManager.getPolicy(this).hasPermission(user, ReadPermission.class)))
        {
            result.add(ReadPermission.class);

            // Now check if they can write
            if (securityType == SecurityType.BASIC_WRITE)
            {
                if (user instanceof User && ((User)user).isAdministrator())
                {
                    result.addAll(RoleManager.getRole(SiteAdminRole.class).getPermissions());
                }
                else if (getStudy().getContainer().hasPermission(user, UpdatePermission.class))
                {
                    // Basic write access grants insert/update/delete for datasets to everyone who has update permission
                    // in the folder
                    result.add(UpdatePermission.class);
                    result.add(DeletePermission.class);
                    result.add(InsertPermission.class);
                }
            }
            else if (securityType == SecurityType.ADVANCED_WRITE)
            {
                if (user instanceof User && ((User)user).isAdministrator())
                {
                    result.addAll(RoleManager.getRole(SiteAdminRole.class).getPermissions());
                }
                else if (studyPolicy.hasPermission(user, UpdatePermission.class))
                {
                    result.add(UpdatePermission.class);
                    result.add(DeletePermission.class);
                    result.add(InsertPermission.class);
                }
                else if (studyPolicy.hasPermission(user, ReadSomePermission.class))
                {
                    // Advanced write grants dataset permissions based on the policy stored directly on the dataset
                    result.addAll(SecurityManager.getPolicy(this).getPermissions(user));
                }
            }
        }
        
        return result;
    }

    @Override
    public boolean canRead(UserPrincipal user)
    {
        return getPermissions(user).contains(ReadPermission.class);
    }

    @Override
    public boolean canWrite(UserPrincipal user)
    {
        if (getContainer().hasPermission(user, AdminPermission.class))
            return true;
        return getPermissions(user).contains(UpdatePermission.class);
    }

    @Override
    public KeyType getKeyType()
    {
        if (isDemographicData())
            return KeyType.SUBJECT;
        if (getKeyPropertyName() != null)
            return KeyType.SUBJECT_VISIT_OTHER;
        return KeyType.SUBJECT_VISIT;
    }

    /**
     * Construct a description of the key type for this dataset.
     * Participant/Visit/ExtraKey
     * @return Description of KeyType
     */
    @Override
    public String getKeyTypeDescription()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(StudyService.get().getSubjectNounSingular(getContainer()));
        if (!isDemographicData())
        {
            sb.append(getStudy().getTimepointType().isVisitBased() ? "/Visit" : "/Date");

            if (getKeyPropertyName() != null)
                sb.append("/").append(getKeyPropertyName());
        }
        else if (getKeyPropertyName() != null)
        {
            sb.append("/").append(getKeyPropertyName());
        }
        return sb.toString();
    }

    @Override
    public boolean hasMatchingExtraKey(DataSet other)
    {
        if (other == null)
            return false;

        if (isAssayData() || other.isAssayData() || getKeyPropertyName() == null || other.getKeyPropertyName() == null)
            return false;

        DomainProperty thisKeyDP = getDomain().getPropertyByName(getKeyPropertyName());
        DomainProperty otherKeyDP = other.getDomain().getPropertyByName(other.getKeyPropertyName());
        if (thisKeyDP == null || otherKeyDP == null)
            return false;

        // Key property types must match
        PropertyType thisKeyType = thisKeyDP.getPropertyDescriptor().getPropertyType();
        PropertyType otherKeyType = otherKeyDP.getPropertyDescriptor().getPropertyType();
        if (!LOOKUP_KEY_TYPES.contains(thisKeyType) || thisKeyType != otherKeyType)
            return false;

        // Either the lookups must match or the Key property name must match.
        Lookup thisKeyLookup = thisKeyDP.getLookup();
        Lookup otherKeyLookup = otherKeyDP.getLookup();
        if (thisKeyLookup != null && otherKeyLookup != null)
        {
            if (!thisKeyLookup.equals(otherKeyLookup))
                return false;
        }
        else
        {
            if (thisKeyLookup != null || otherKeyLookup != null)
                return false;

            if (!getKeyPropertyName().equalsIgnoreCase(other.getKeyPropertyName()))
                return false;
        }

        // NOTE: Also consider comparing ConceptURI of the properties

        return true;
    }

    // The set of allowed extra key lookup types that we can join across.
    private static final EnumSet<PropertyType> LOOKUP_KEY_TYPES = EnumSet.of(
            PropertyType.DATE_TIME,
            PropertyType.DOUBLE, // Attempting to allow this
            PropertyType.STRING,
            PropertyType.INTEGER);

    /** most external users should use this */
    public String getVisitDateColumnName()
    {
        if (null == _visitDatePropertyName && !getStudy().getTimepointType().isVisitBased())
            _visitDatePropertyName = "Date"; //Todo: Allow alternate names
        return _visitDatePropertyName;
    }


    public String getVisitDatePropertyName()
    {
        return _visitDatePropertyName;
    }


    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        _visitDatePropertyName = visitDatePropertyName;
    }

    
    /**
     * Returns the key names for display purposes.
     * If demographic data, visit keys are suppressed
     */
    public String[] getDisplayKeyNames()
    {
        List<String> keyNames = new ArrayList<String>();
        keyNames.add(StudyService.get().getSubjectColumnName(getContainer()));
        if (!isDemographicData())
        {
            keyNames.add(getStudy().getTimepointType().isVisitBased() ? "SequenceNum" : "Date");
        }
        if (getKeyPropertyName() != null)
            keyNames.add(getKeyPropertyName());

        return keyNames.toArray(new String[keyNames.size()]);
    }

    public String getKeyPropertyName()
    {
        return _keyPropertyName;
    }

    public void setKeyPropertyName(String keyPropertyName)
    {
        verifyMutability();
        _keyPropertyName = keyPropertyName;
    }

    @Override
    public void save(User user)
    {
        StudyManager.getInstance().updateDataSetDefinition(user, this);
    }

    public void setKeyManagementType(@NotNull KeyManagementType type)
    {
        _keyManagementType = type;
    }

    @NotNull
    public KeyManagementType getKeyManagementType()
    {
        return _keyManagementType;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    @NotNull
    @Override
    public String getResourceDescription()
    {
        return null == _description ? "The study dataset " + getName() : _description;
    }

    private static class AutoCompleteDisplayColumnFactory implements DisplayColumnFactory
    {
        private String _completionBase;

        public AutoCompleteDisplayColumnFactory(Container studyContainer, SpecimenService.CompletionType type)
        {
            _completionBase = SpecimenService.get().getCompletionURLBase(studyContainer, type);
        }

        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                @Override
                protected String getAutoCompleteURLPrefix()
                {
                    return _completionBase;
                }
            };
        }
    }


    /**
     * NOTE the constructor takes a USER in order that some lookup columns can be properly
     * verified/constructed
     *
     * CONSIDER: we could use a way to delay permission checking and final schema construction for lookups
     * so that this object can be cached...
     */

    public class DatasetSchemaTableInfo extends SchemaTableInfo
    {
        private Container _container;
        ColumnInfo _ptid;

        TableInfo _storage;
        TableInfo _template;


        private ColumnInfo getStorageColumn(String name)
        {
            if (null != _storage)
                return _storage.getColumn(name);
            else
                return _template.getColumn(name);
        }


        private ColumnInfo getStorageColumn(Domain d, DomainProperty p)
        {
            return _storage.getColumn(p.getName());
        }


        DatasetSchemaTableInfo(DataSetDefinition def, final User user)
        {
            super(StudySchema.getInstance().getSchema(), DatabaseTableType.TABLE, def.getLabel());
            _autoLoadMetaData = false;
            _container = def.getContainer();
            Study study = StudyManager.getInstance().getStudy(_container);

            _storage = def.getStorageTableInfo();
            _template = getTemplateTableInfo();
            
            // PartipantId

            {
                // StudyData columns
                // NOTE (MAB): I think it was probably wrong to alias participantid to subjectname here
                // That probably should have been done only in the StudyQuerySchema
                // CONSIDER: remove this aliased column
                ColumnInfo ptidCol = getStorageColumn("ParticipantId");
                if (null == ptidCol) // shouldn't happen! bug mothership says it did
                    throw new NullPointerException("ParticipantId column not found in dataset: " + (null != _container ? "(" + _container.getPath() + ") " : "") + getName());
                ColumnInfo wrapped = newDatasetColumnInfo(this, ptidCol, getParticipantIdURI());
                wrapped.setName("ParticipantId");
                String subject = StudyService.get().getSubjectColumnName(_container);

                if ("ParticipantId".equalsIgnoreCase(subject))
                    _ptid = wrapped;
                else
                    _ptid = new AliasedColumn(this, subject, wrapped);

                _ptid.setNullable(false);
                addColumn(_ptid);
            }

            // base columns

            for (String name : Arrays.asList("lsid","ParticipantSequenceNum","sourcelsid","Created","CreatedBy","Modified","ModifiedBy"))
            {
                ColumnInfo col = getStorageColumn(name);
                if (null == col) continue;
                ColumnInfo wrapped = newDatasetColumnInfo(this, col, uriForName(col.getName()));
                wrapped.setName(name);
                wrapped.setUserEditable(false);
                addColumn(wrapped);
            }

            // _Key

            if (def.getKeyPropertyName() != null)
            {
                ColumnInfo keyCol = newDatasetColumnInfo(this, getStorageColumn("_Key"), getKeyURI());
                keyCol.setUserEditable(false);
                addColumn(keyCol);
            }

            // SequenceNum

            ColumnInfo sequenceNumCol = newDatasetColumnInfo(this, getStorageColumn("SequenceNum"), getSequenceNumURI());
            sequenceNumCol.setName("SequenceNum");
            sequenceNumCol.setDisplayColumnFactory(new AutoCompleteDisplayColumnFactory(_container, SpecimenService.CompletionType.VisitId));
            sequenceNumCol.setMeasure(false);

            if (def.isDemographicData())
            {
                sequenceNumCol.setHidden(true);
                sequenceNumCol.setUserEditable(false);
            }

            if (!study.getTimepointType().isVisitBased())
            {
                sequenceNumCol.setNullable(true);
                sequenceNumCol.setHidden(true);
                sequenceNumCol.setUserEditable(false);
            }

            addColumn(sequenceNumCol);

            // Date

            if (!study.getTimepointType().isVisitBased())
            {
                ColumnInfo column = getStorageColumn("Date");
                if (column == null)
                {
                    // Template table calls the column VisitDate instead
                    column = getStorageColumn("VisitDate");
                }
                ColumnInfo visitDateCol = newDatasetColumnInfo(this, column, getVisitDateURI());
                visitDateCol.setNullable(false);
                addColumn(visitDateCol);
            }

            // QCState

            ColumnInfo qcStateCol = newDatasetColumnInfo(this, getStorageColumn(DataSetTableImpl.QCSTATE_ID_COLNAME), getQCStateURI());
            // UNDONE: make the QC column user editable.  This is turned off for now because DatasetSchemaTableInfo is not
            // a FilteredTable, so it doesn't know how to restrict QC options to those in the current container.
            // Note that QC state can still be modified via the standard update UI.
            qcStateCol.setUserEditable(false);
            addColumn(qcStateCol);

            // Property columns (see OntologyManager.getColumnsForType())

            Domain d = def.getDomain();
            DomainProperty[] properties = null==d ? new DomainProperty[0] : d.getProperties();

            for (DomainProperty p : properties)
            {
                ColumnInfo col = getStorageColumn(d, p);

                if (col == null)
                {
                    _log.error("didn't find column for property: " + p.getPropertyURI());
                    continue;
                }

                ColumnInfo wrapped = newDatasetColumnInfo(user, this, col, p.getPropertyDescriptor());
                addColumn(wrapped);

                // Set the FK if the property descriptor is configured as a lookup. DatasetSchemaTableInfos aren't
                // cached, so it's safe to include the current user 
                PropertyDescriptor pd = p.getPropertyDescriptor();
                if (null != pd && pd.getLookupQuery() != null)
                    wrapped.setFk(new PdLookupForeignKey(user, pd, getContainer()));

                if (p.isMvEnabled())
                {
                    ColumnInfo mvColumn = new ColumnInfo(wrapped.getName() + MvColumn.MV_INDICATOR_SUFFIX, this);
                    // MV indicators are strings
                    mvColumn.setSqlTypeName("VARCHAR");
                    mvColumn.setPropertyURI(wrapped.getPropertyURI());
                    mvColumn.setMetaDataName(col.getAlias() + "_" + MvColumn.MV_INDICATOR_SUFFIX);
                    mvColumn.setNullable(true);
                    mvColumn.setUserEditable(false);
                    mvColumn.setHidden(true);
                    mvColumn.setMvIndicatorColumn(true);

                    ColumnInfo rawValueCol = new AliasedColumn(wrapped.getName() + RawValueColumn.RAW_VALUE_SUFFIX, wrapped);
                    rawValueCol.setDisplayColumnFactory(ColumnInfo.DEFAULT_FACTORY);
                    rawValueCol.setLabel(getName());
                    rawValueCol.setUserEditable(false);
                    rawValueCol.setHidden(true);
                    rawValueCol.setMvColumnName(null); // This version of the column does not show missing values
                    rawValueCol.setNullable(true); // Otherwise we get complaints on import for required fields
                    rawValueCol.setRawValueColumn(true);

                    addColumn(mvColumn);
                    addColumn(rawValueCol);

                    wrapped.setMvColumnName(mvColumn.getFieldKey());
                }
            }

            // If we have an extra key, and it's server-managed, make it non-editable
            if (def.getKeyManagementType() != KeyManagementType.None)
            {
                for (ColumnInfo col : getColumns())
                {
                    if (col.getName().equals(def.getKeyPropertyName()))
                    {
                        col.setUserEditable(false);
                    }
                }
            }

            // Dataset

            ColumnInfo datasetColumn = new ExprColumn(this, "Dataset", new SQLFragment("CAST('" + def.getEntityId() + "' AS " + getSqlDialect().getGuidType() + ")"), JdbcType.VARCHAR);
            LookupForeignKey datasetFk = new LookupForeignKey("entityid")
            {
                public TableInfo getLookupTableInfo()
                {
                    StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(_container), user, true);
                    return schema.getTable("Datasets");
                }
            };
            datasetColumn.setFk(datasetFk);
            datasetColumn.setUserEditable(false);
            datasetColumn.setHidden(true);
            datasetColumn.setDimension(false);
            addColumn(datasetColumn);

            setPkColumnNames(Arrays.asList("LSID"));
        }


        public ColumnInfo getParticipantColumn()
        {
            return _ptid;
        }


        @Override
        public ColumnInfo getColumn(String name)
        {
            if ("ParticipantId".equalsIgnoreCase(name))
                return getParticipantColumn();
            return super.getColumn(name);
        }


        @Override
        public ButtonBarConfig getButtonBarConfig()
        {
            // Check first to see if this table has explicit button configuration.  This currently will
            // never be the case, since dataset tableinfo's don't have a place to declare button config,
            // but future changes to enable hard dataset tables may enable this.
            ButtonBarConfig config = super.getButtonBarConfig();
            if (config != null)
                return config;

//            // If no button config was found for this dataset, fall back to the button config on StudyData.  This
//            // lets users configure buttons that should appear on all datasets.
//            StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(_container), _user, true);
//            try
//            {
//                TableInfo studyData = schema.getTable(StudyQuerySchema.STUDY_DATA_TABLE_NAME);
//                return studyData.getButtonBarConfig();
//            }
//            catch (UnauthorizedException e)
//            {
//                return null;
//            }
            return null;
        }


        @Override
        public String getSelectName()
        {
            return null;
        }

        @Override
        @NotNull
        public SQLFragment getFromSQL(String alias)
        {
            if (null == _storage)
            {
                SqlDialect d = getSqlDialect();
                SQLFragment from = new SQLFragment();
                from.appendComment("<DataSetDefinition: " + getName() + ">", d); // UNDONE stash name
                String comma = " ";
                from.append("(SELECT ");
                for (ColumnInfo ci : _template.getColumns())
                {
                    from.append(comma).append(NullColumnInfo.nullValue(ci.getSqlTypeName())).append(" AS ").append(ci.getName());
                    comma = ", ";
                }
                from.append("\nWHERE 0=1) AS ").append(alias);
                from.appendComment("</DataSetDefinition>", d);
                return from;
            }
            else
            {
                return _storage.getFromSQL(alias);
            }
        }

        //
        // UpdateableTableInfo
        //

        @Override
        public Domain getDomain()
        {
            return DataSetDefinition.this.getDomain();
        }

        @Override
        public DomainKind getDomainKind()
        {
            return DataSetDefinition.this.getDomainKind();    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        public TableInfo getSchemaTableInfo()
        {
            return _storage;
        }

        @Override
        public CaseInsensitiveHashMap<String> remapSchemaColumns()
        {
             CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<String>();
            
            // why did I add an underscore to the stored mv indicators???
            for (ColumnInfo col : getColumns())
            {
                if (null == col.getMvColumnName())
                    continue;
                m.put(col.getName() + "_" + MvColumn.MV_INDICATOR_SUFFIX, col.getMvColumnName().getName());
            }

            // shouldn't getStorageTableInfo().getColumn("date").getPropertyURI() == getVisitDateURI()?
            if (!getStudy().getTimepointType().isVisitBased())
            {
                m.put(getStorageTableInfo().getColumn("date").getPropertyURI(), getVisitDateURI());
            }

            return m;
        }

        @Override
        public CaseInsensitiveHashSet skipProperties()
        {
            return null;
        }
    }


    static ColumnInfo newDatasetColumnInfo(TableInfo tinfo, final ColumnInfo from, final String propertyURI)
    {
        // TODO: Yuck
        ColumnInfo result = new ColumnInfo(from, tinfo)
        {
            @Override
            public String getPropertyURI()
            {
                return null != propertyURI ? propertyURI : super.getPropertyURI();
            }
        };
        // Hidden doesn't get copied with the default set of properties
        result.setHidden(from.isHidden());
        result.setMetaDataName(from.getMetaDataName());
        return result;
    }

    
    static ColumnInfo newDatasetColumnInfo(User user, TableInfo tinfo, ColumnInfo from, PropertyDescriptor p)
    {
        ColumnInfo ci = newDatasetColumnInfo(tinfo, from, p.getPropertyURI());
        // We are currently assuming the db column name is the same as the propertyname
        // I want to know if that changes
        assert ci.getName().equalsIgnoreCase(p.getName());
        ci.setName(p.getName());
        ci.setAlias(from.getAlias());
        return ci;
    }


    private static final Set<PropertyDescriptor> standardPropertySet = new HashSet<PropertyDescriptor>();
    private static final Map<String, PropertyDescriptor> standardPropertyMap = new CaseInsensitiveHashMap<PropertyDescriptor>();

    public static Set<PropertyDescriptor> getStandardPropertiesSet()
    {
        synchronized(standardPropertySet)
        {
            if (standardPropertySet.isEmpty())
            {
                TableInfo info = getTemplateTableInfo();
                for (ColumnInfo col : info.getColumns())
                {
                    String propertyURI = col.getPropertyURI();
                    if (propertyURI == null)
                        continue;
                    String name = col.getName();
                    PropertyType type = PropertyType.getFromClass(col.getJdbcType().getJavaClass());
                    PropertyDescriptor pd = new PropertyDescriptor(
                            propertyURI, type.getTypeUri(), name, ContainerManager.getSharedContainer());
                    standardPropertySet.add(pd);
                }
            }
            return standardPropertySet;
        }
    }


    Domain _domain = null;

    public synchronized Domain getDomain()
    {
        if (null == getTypeURI())
            return null;
        if (null == _domain)
            _domain = PropertyService.get().getDomain(getContainer(), getTypeURI());
        return _domain;
    }


    public DomainKind getDomainKind()
    {
        switch (getStudy().getTimepointType())
        {
            case VISIT:
                return new VisitDatasetDomainKind();
            case DATE:
            case CONTINUOUS:
                return new DateDatasetDomainKind();
            default:
                return null;
        }
    }
    

    public static Map<String,PropertyDescriptor> getStandardPropertiesMap()
    {
        synchronized(standardPropertyMap)
        {
            if (standardPropertyMap.isEmpty())
            {
                for (PropertyDescriptor pd : getStandardPropertiesSet())
                {
                    standardPropertyMap.put(pd.getName(), pd);
                }
            }
            return standardPropertyMap;
        }
    }


    private static String uriForName(String name)
    {
        final String StudyURI = "http://cpas.labkey.com/Study#";
        assert getStandardPropertiesMap().get(name).getPropertyURI().equalsIgnoreCase(StudyURI + name);
        return getStandardPropertiesMap().get(name).getPropertyURI();
    }


    public static String getKeyURI()
    {
        return uriForName("_key");
    }

    public static String getSequenceNumURI()
    {
        return uriForName("SequenceNum");
    }

    public static String getDatasetIdURI()
    {
        return uriForName("DatasetId");
    }

    public static String getParticipantIdURI()
    {
        return uriForName("ParticipantId");
    }

    public static String getVisitDateURI()
    {
        return uriForName("VisitDate");
    }

    public static String getSiteIdURI()
    {
        return uriForName("SiteId");
    }

    public static String getCreatedURI()
    {
        return uriForName("Created");
    }

    public static String getSourceLsidURI()
    {
        return uriForName("SourceLSID");
    }

    public static String getModifiedURI()
    {
        return uriForName("Modified");
    }

    public static String getQCStateURI()
    {
        return uriForName(DataSetTableImpl.QCSTATE_ID_COLNAME);
    }


    @Override
    protected boolean supportsPolicyUpdate()
    {
        return true;
    }

    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        _cohortId = cohortId;
    }

    @Nullable
    public CohortImpl getCohort()
    {
        if (_cohortId == null)
            return null;
        return Table.selectObject(StudySchema.getInstance().getTableInfoCohort(), _cohortId, CohortImpl.class);
    }

    public Integer getProtocolId()
    {
        return _protocolId;
    }

    public ExpProtocol getAssayProtocol()
    {
        return _protocolId == null ? null : ExperimentService.get().getExpProtocol(_protocolId.intValue());
    }

    public void setProtocolId(Integer protocolId)
    {
        _protocolId = protocolId;
    }

    @Override
    public String toString()
    {
        return "DataSetDefinition: " + getLabel() + " " + getDataSetId();
    }

    public void deleteRows(User user, Collection<String> rowLSIDs)
    {
        Container c = getContainer();

        TableInfo data = getStorageTableInfo();
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause("LSID", rowLSIDs);

        DbScope scope =  StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            char sep = ' ';
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<rowLSIDs.size(); i++)
            {
                sb.append(sep);
                sb.append('?');
                sep = ',';
            }
            List<Object> paramList = new ArrayList<Object>(rowLSIDs);
            OntologyManager.deleteOntologyObjects(StudySchema.getInstance().getSchema(), new SQLFragment(sb.toString(), paramList), c, false);
            Table.delete(data, filter);
            StudyManager.dataSetModified(this, user, true);

            scope.commitTransaction();
        }
        catch (SQLException s)
        {
            throw new RuntimeSQLException(s);
        }
        finally
        {
            scope.closeConnection();
        }
    }


    /**
     * dataMaps have keys which are property URIs, and values which have already been converted.
     */
    public List<String> importDatasetData(Study study, User user, DataIteratorBuilder in, BatchValidationException errors, boolean checkDuplicates, QCState defaultQCState, Logger logger
            , boolean forUpdate)
            throws SQLException
    {
        if (getKeyManagementType() == KeyManagementType.RowId)
        {
            // If additional keys are managed by the server, we need to synchronize around
            // increments, as we're imitating a sequence.
            synchronized (getManagedKeyLock())
            {
                return insertData(user, in, checkDuplicates, errors, defaultQCState, logger, forUpdate);
            }
        }
        else
        {
            return insertData(user, in, checkDuplicates, errors, defaultQCState, logger, forUpdate);
        }
    }



    private void checkForDuplicates(DataIterator data,
            int indexLSID, int indexPTID, int indexVisit, int indexKey, int indexReplace,
            BatchValidationException errors, Logger logger)
    {
        HashMap<String, Object[]> failedReplaceMap = checkAndDeleteDupes(
                data,
                indexLSID, indexPTID, indexVisit, indexKey, indexReplace,
                errors);

        if (null != failedReplaceMap && failedReplaceMap.size() > 0)
        {
            StringBuilder error = new StringBuilder();
            error.append("Only one row is allowed for each ").append(StudyService.get().getSubjectNounSingular(getContainer()));

            if (!isDemographicData())
            {
                error.append(_study.getTimepointType().isVisitBased() ? "/Visit" : "/Date");

                if (getKeyPropertyName() != null)
                    error.append("/").append(getKeyPropertyName()).append(" combination");
                else
                    error.append(" combination");
            }
            else if (getKeyPropertyName() != null)
            {
                error.append("/").append(getKeyPropertyName()).append(" combination");
            }
            error.append(".  ");

            error.append("Duplicates were found in the database or imported data.");
            errors.addRowError(new ValidationException(error.toString()));

            for (Map.Entry<String, Object[]> e : failedReplaceMap.entrySet())
            {
                Object[] keys = e.getValue();
                String err = "Duplicate: " + StudyService.get().getSubjectNounSingular(getContainer()) + " = " + keys[0];
                if (!isDemographicData())
                {
                    if (!_study.getTimepointType().isVisitBased())
                        err = err + ", Date = " + keys[1];
                    else
                        err = err + ", VisitSequenceNum = " + keys[1];
                }
                if (0 < indexKey)
                    err += ", " + data.getColumnInfo(indexKey).getName() + " = " + keys[2];
                errors.addRowError(new ValidationException(err));
            }
        }
        if (logger != null) logger.debug("checked for duplicates");
    }



    private class DatasetDataIteratorBuilder implements DataIteratorBuilder
    {
        User user;
        boolean needsQC;
        QCState defaultQC;
        List<String> lsids = null;
        boolean checkDuplicates = false;
        boolean isForUpdate = false;
        boolean useImportAliases = false;
        Logger logger = null;

        DataIteratorBuilder builder = null;
        DataIterator input = null;

        ValidationException setupError = null;

        DatasetDataIteratorBuilder(User user, boolean qc, QCState defaultQC)
        {
            this.user = user;
            this.needsQC = qc;
            this.defaultQC = defaultQC;
        }

        /**
         * StudyServiceImpl.updateDatasetRow() is implemented as a delete followed by insert.
         * This is very gross, and it causes a special case here, as we want to re-use any server
         * managed keys, instead of regenerating them.
         * 
         * @param update
         */
        void setForUpdate(boolean update)
        {
            this.isForUpdate = update;
        }

        void setUseImportAliases(boolean aliases)
        {
            this.useImportAliases = aliases;
        }

        void setCheckDuplicates(boolean check)
        {
            checkDuplicates = check;
        }

        void setInput(DataIteratorBuilder b)
        {
            builder = b;
        }

        void setInput(DataIterator it)
        {
            input = it;
        }

        void setLogger(Logger logger)
        {
            this.logger = logger;
        }

        void setKeyList(List<String> lsids)
        {
            this.lsids = lsids;
        }

        void setupError(String msg)
        {
            if (null == setupError)
                setupError = new ValidationException();
            setupError.addGlobalError(msg);
        }

        @Override
        public DataIterator getDataIterator(BatchValidationException errors)
        {
            // might want to make allow importManagedKey an explicit option, for now allow for GUID
            boolean allowImportManagedKey = isForUpdate || getKeyManagementType() == KeyManagementType.GUID;
            boolean isManagedKey = getKeyType() == KeyType.SUBJECT_VISIT_OTHER && getKeyManagementType() != KeyManagementType.None;
                    
            TimepointType timetype = getStudy().getTimepointType();
            TableInfo table = getTableInfo(user, false);

            String keyColumnName = getKeyPropertyName();
            ColumnInfo keyColumn = null == keyColumnName ? null : table.getColumn(keyColumnName);
            ColumnInfo lsidColumn = table.getColumn("lsid");

            if (null == input && null != builder)
                input = builder.getDataIterator(errors);

            _DatasetColumnsIterator it = new _DatasetColumnsIterator(input, errors, user);

            ValidationException matchError = new ValidationException();
            ArrayList<ColumnInfo> inputMatches = DataIteratorUtil.matchColumns(input,table,useImportAliases,matchError);
            if (matchError.hasErrors())
                setupError(matchError.getMessage());

            // select all columns except those we explicity calculate (e.g. lsid)
            for (int in=1 ; in<=input.getColumnCount() ; in++)
            {
                ColumnInfo match = inputMatches.get(in);

                if (null != match)
                {
                    if (match == lsidColumn)
                        continue;
                    if (match == keyColumn && isManagedKey && !allowImportManagedKey)
                    {
                        // TODO silently ignore or add error?
                            continue;
                    }
                }

                if (null != match)
                {
                    int out;
                    if (match == keyColumn && getKeyManagementType() == KeyManagementType.None)
                    {
                        // usually we let ETL handle convert, but we need to convert for consistent _key/lsid generation
                        out = it.addConvertColumn(match.getName(), in, match.getJdbcType(), null != match.getMvColumnName());
                    }
                    else if (match == keyColumn && getKeyManagementType() == KeyManagementType.GUID)
                    {
                        // make sure guid is not null (12884)
                        out = it.addCoaleseColumn(match.getName(), in, new SimpleTranslator.GuidColumn());
                    }
                    else
                    {
                        // to simplify a little, use matched name/propertyuri here (even though StandardETL would rematch using the same logic)
                        out = it.addColumn(match.getName(),in);
                    }
                    it.getColumnInfo(out).setPropertyURI(match.getPropertyURI());
                }
                else
                {
                    it.addColumn(in);
                }
            }

            Map<String,Integer> fromMap = DataIteratorUtil.createColumnAndPropertyMap(it);

            // find important columns in the input (CONSIDER: use standard etl alt
            Integer indexPTID = fromMap.get(DataSetDefinition.getParticipantIdURI());
            Integer indexKeyProperty = null==keyColumn ? null : fromMap.get(keyColumn.getPropertyURI());
            Integer indexVisitDate = fromMap.get(DataSetDefinition.getVisitDateURI());
            Integer indexReplace = fromMap.get("replace");

            // For now, just specify null for sequence num index... we'll add it below
            // TODO: clean this up
            it.setSpecialInputColumns(indexPTID, null, indexVisitDate, indexKeyProperty);
            it.setTimepointType(timetype);

            /* NOTE: these columns must be added in dependency order
             *
             * sequencenum -> date
             * participantsequence -> ptid, sequencenum
             * lsid -> ptid, sequencenum, key
             */

            //
            // date
            //

            if (!timetype.isVisitBased() && null == indexVisitDate && isDemographicData())
            {
                final Date start = _study.getStartDate();
                indexVisitDate = it.addColumn(new ColumnInfo("Date", JdbcType.TIMESTAMP), new Callable(){
                    @Override
                    public Object call() throws Exception
                    {
                        return start;
                    }
                });
                it.indexVisitDateOutput = indexVisitDate;
            }

            //
            // SequenceNum
            //

            Integer indexSequenceNum = fromMap.get(DataSetDefinition.getSequenceNumURI());

            if (null == indexSequenceNum)
            {
                if (!timetype.isVisitBased() && null != indexVisitDate)
                {
                    it.indexSequenceNumOutput = it.addSequenceNumFromDateColumn();
                }
                else if (isDemographicData())
                {
                    it.indexSequenceNumOutput = it.addColumn(new ColumnInfo("SequenceNum", JdbcType.DOUBLE),
                        new Callable()
                        {
                            @Override
                            public Object call() throws Exception
                            {
                                return VisitImpl.DEMOGRAPHICS_VISIT;
                            }
                        });
                }
            }
            else
            {
                Map<String, Double> map = StudyManager.getInstance().getVisitImportMap(getStudy(), true);
                Map<String, String> translateMap = new HashMap<String, String>(map.size() * 2);

                for (Map.Entry<String, Double> entry : map.entrySet())
                    translateMap.put(entry.getKey(), String.valueOf(entry.getValue()));

                it.indexSequenceNumOutput = it.translateColumn(indexSequenceNum, translateMap, false);
            }

            if (null == indexKeyProperty)
            {
                //
                // ROWID
                //

                if (getKeyManagementType() == KeyManagementType.RowId)
                {
                    ColumnInfo key = new ColumnInfo(keyColumn);
                    Callable call = new SimpleTranslator.AutoIncrementColumn()
                    {
                        @Override
                        protected int getFirstValue()
                        {
                            try
                            {
                                return getMaxKeyValue()+1;
                            }
                            catch (SQLException x)
                            {
                                throw new RuntimeSQLException(x);
                            }
                            }
                    };
                    indexKeyProperty = it.addColumn(key, call);
                }

                //
                // GUID
                //

                else if (getKeyManagementType() == KeyManagementType.GUID)
                {
                    ColumnInfo key = new ColumnInfo(keyColumn);
                    indexKeyProperty = it.addColumn(key, new SimpleTranslator.GuidColumn());
                }
            }


            //
            // _key
            //

            if (null != indexKeyProperty)
            {
                it.indexKeyPropertyOutput = it.addAliasColumn("_key", indexKeyProperty, JdbcType.VARCHAR);
            }

            //
            // ParticipantSequenceNum
            //

            it.addParticipantSequenceNum();
            
            // QCSTATE

            if (needsQC)
            {
                String qcStatePropertyURI = DataSetDefinition.getQCStateURI();
                Integer indexInputQCState = fromMap.get(qcStatePropertyURI);
                Integer indexInputQCText = fromMap.get(DataSetTableImpl.QCSTATE_LABEL_COLNAME);
                if (null == indexInputQCState)
                {
                    int indexText = null==indexInputQCText ? -1 : indexInputQCText;
                    it.addQCStateColumn(indexText, qcStatePropertyURI, defaultQC);
                }
            }


            //
            // LSID
            //

            // NOTE have to add LSID after columns it depends on
            int indexLSID = it.addLSID();


            //
            // check errors, misc
            //

            it.setKeyList(lsids);

            it.setDebugName(getName());

            // don't bother going on if we don't have these required columns
            if (null == indexPTID)
                setupError("All dataset rows must include a value for " + _study.getSubjectColumnName());

            if (!timetype.isVisitBased() && null == indexVisitDate)
                setupError("All dataset rows must include a value for Date");

            if (timetype.isVisitBased() && null == it.indexSequenceNumOutput)
                setupError("All dataset rows must include a value for SequenceNum");

            it.setInput(ErrorIterator.wrap(input, errors, false, setupError));
            DataIterator ret = LoggingDataIterator.wrap(it);

            //
            // Check Duplicates
            //

            boolean hasError = null != setupError && setupError.hasErrors();
            if (checkDuplicates && !hasError)
            {
                Integer indexVisit = timetype.isVisitBased() ? it.indexSequenceNumOutput : indexVisitDate;
                // no point if required columns are missing
                if (null != indexPTID && null != indexVisit)
                {
                    ScrollableDataIterator scrollable = DataIteratorUtil.wrapScrollable(ret);
                    checkForDuplicates(scrollable, indexLSID,
                            indexPTID, null == indexVisit ? -1 : indexVisit, null == indexKeyProperty ? -1 : indexKeyProperty, null == indexReplace ? -1 : indexReplace,
                            errors, logger);
                    scrollable.beforeFirst();
                    ret = scrollable;
                }
            }

            return ret;
        }
    }



    private class _DatasetColumnsIterator extends SimpleTranslator
    {
        DecimalFormat _seqenceFormat = new DecimalFormat("0.0000");
        Converter convertDate = ConvertUtils.lookup(Date.class);
        List<String> lsids;
        User user;
        private int _maxPTIDLength;
//        boolean requiresKeyLock = false;

        // these columns are used to compute derived columns, should occur early in the output list
        Integer indexPtidOutput, indexSequenceNumOutput, indexVisitDateOutput, indexKeyPropertyOutput;
        // for returning lsid list
        Integer indexLSIDOutput;

        TimepointType timetype;

        _DatasetColumnsIterator(DataIterator data, BatchValidationException errors, User user) // , String keyPropertyURI, boolean qc, QCState defaultQC, Map<String, QCState> qcLabels)
        {
            super(data, errors);
            this.user = user; 
            _maxPTIDLength = getTableInfo(this.user, false).getColumn("ParticipantID").getScale();
        }

        void setSpecialInputColumns(Integer indexPTID, Integer indexSequenceNum, Integer indexVisitDate, Integer indexKeyProperty)
        {
            this.indexPtidOutput = indexPTID;
            this.indexSequenceNumOutput = indexSequenceNum;
            this.indexVisitDateOutput = indexVisitDate;
            this.indexKeyPropertyOutput = indexKeyProperty;
        }

        void setTimepointType(TimepointType timetype)
        {
            this.timetype = timetype;
        }

        void setKeyList(List<String> lsids)
        {
            this.lsids = lsids;
        }


        @Override
        public void beforeFirst()
        {
            super.beforeFirst();
            if (null != lsids)
                lsids.clear();
        }


        @Override
        public boolean next() throws BatchValidationException
        {
//            assert getKeyManagementType() != KeyManagementType.RowId || Thread.holdsLock(getManagedKeyLock());
//            assert DbSchema.get("study").getScope().isTransactionActive();

            boolean hasNext = super.next();
            if (hasNext)
            {
                Object ptidObject = get(indexPtidOutput);
                if (ptidObject != null && ptidObject.toString().length() > _maxPTIDLength)
                {
                    throw new BatchValidationException(Collections.singletonList(new ValidationException(_study.getSubjectColumnName() + " value '" + ptidObject + "' is too long, maximum length is " + _maxPTIDLength + " characters")), Collections.<String, Object>emptyMap());
                }
                if (null != lsids && null != indexLSIDOutput)
                {
                    try
                    {
                        lsids.add((String)get(indexLSIDOutput));
                    }
                    catch (RuntimeException x)
                    {
                        throw x;
                    }
                    catch (Exception x)
                    {
                        throw new RuntimeException(x);
                    }
                }
            }
            return hasNext;
        }

        Double getOutputDouble(int i)
        {
            Object o = get(i);
            if (null == o)
                return null;
            if (o instanceof Number)
                return ((Number)o).doubleValue();
            if (o instanceof String)
            {
                try
                {
                    return Double.parseDouble((String)o);
                }
                catch (NumberFormatException x)
                {
                    ;
                }
            }
            return null;
        }

        Date getOutputDate(int i)
        {
            Object o = get(i);
            Date date = (Date)convertDate.convert(Date.class, o);
            return date;
        }

        String getInputString(int i)
        {
            Object o = getInput().get(i);
            return null==o ? "" : o.toString();
        }

        String getOutputString(int i)
        {
            Object o = this.get(i);
            return null==o ? "" : o.toString();
        }

        int addQCStateColumn(int index, String uri, QCState defaultQCState)
        {
            ColumnInfo qcCol = new ColumnInfo("QCState", JdbcType.INTEGER);
            qcCol.setPropertyURI(uri);
            Callable qcCall = new QCStateColumn(index, defaultQCState);
            return addColumn(qcCol, qcCall);
        }

        int addSequenceNumFromDateColumn()
        {
            return addColumn(new ColumnInfo("SequenceNum", JdbcType.DOUBLE), new SequenceNumFromDateColumn());
        }

        int translateColumn(final int index, Map<?, ?> map, boolean strict)
        {
            ColumnInfo existing = getColumnInfo(index);
            Callable origCallable = this._outputColumns.get(index).getValue();
            RemapColumn remapColumn = new RemapColumn(origCallable, map, strict);
            return replaceOrAddColumn(index, existing, remapColumn);
        }

        int addLSID()
        {
            ColumnInfo col = new ColumnInfo("lsid", JdbcType.VARCHAR);
            indexLSIDOutput = addColumn(col, new LSIDColumn());
            return indexLSIDOutput;
        }

        int addParticipantSequenceNum()
        {
            ColumnInfo col = new ColumnInfo("participantsequencenum", JdbcType.VARCHAR);
            return addColumn(col, new ParticipantSequenceNumColumn());
        }

        int replaceOrAddColumn(Integer index, ColumnInfo col, Callable call)
        {
            if (null == index || index <= 0)
                return addColumn(col, call);
            Pair p = new Pair(col,call);
            _outputColumns.set(index,p);
            return index;
        }

        String getFormattedSequenceNum()
        {
            assert null != indexSequenceNumOutput || hasErrors();
            if (null == indexSequenceNumOutput)
                return null;
            Double d = getOutputDouble(indexSequenceNumOutput);
            if (null == d)
                return null;
            return _seqenceFormat.format(d);
        }

        class SequenceNumFromDateColumn implements Callable
        {
            @Override
            public Object call() throws Exception
            {
                Date date = getOutputDate(indexVisitDateOutput);
                if (null != date)
                    return StudyManager.sequenceNumFromDate(date);
                else
                    return VisitImpl.DEMOGRAPHICS_VISIT;
            }
        }

        class LSIDColumn implements Callable
        {
            String _urnPrefix = getURNPrefix();

            @Override
            public Object call() throws Exception
            {
                StringBuilder sb = new StringBuilder(_urnPrefix);
                assert null!=indexPtidOutput || hasErrors();

                String ptid = null==indexPtidOutput ? "" : getOutputString(indexPtidOutput);
                sb.append(ptid);

                if (!isDemographicData())
                {
                    String seqnum = getFormattedSequenceNum();
                    sb.append(".").append(seqnum);

                    if (null != indexKeyPropertyOutput)
                    {
                        Object key = _DatasetColumnsIterator.this.get(indexKeyPropertyOutput);
                        if (null != key)
                            sb.append(".").append(key);
                    }
                }
                return sb.toString();
            }
        }

        class ParticipantSequenceNumColumn implements Callable
        {
            @Override
            public Object call() throws Exception
            {
                assert null!=indexPtidOutput || hasErrors();
                String ptid = null==indexPtidOutput ? "" : getOutputString(indexPtidOutput);
                String seqnum = getFormattedSequenceNum();
                return ptid + "|" + seqnum;
            }
        }

        class ParticipantSequenceNumKeyColumn implements Callable
        {
            @Override
            public Object call() throws Exception
            {
                assert (null!=indexPtidOutput && null!=indexKeyPropertyOutput) || hasErrors();
                String ptid = null==indexPtidOutput ? "" : getOutputString(indexPtidOutput);
                String seqnum = getFormattedSequenceNum();
                Object key = null==indexKeyPropertyOutput ? "" : String.valueOf(_DatasetColumnsIterator.this.get(indexKeyPropertyOutput));
                return ptid + "|" + seqnum + "|" + key;
            }
        }

        class QCStateColumn implements Callable
        {
            boolean _autoCreate = true;
            int _indexInputQCState = -1;
            QCState _defaultQCState;
            Map<String, QCState> _qcLabels;
            Set<String> notFound = new CaseInsensitiveHashSet();

            QCStateColumn(int index, QCState defaultQCState)
            {
                _indexInputQCState = index;
                _defaultQCState = defaultQCState;

                _qcLabels = new CaseInsensitiveHashMap<QCState>();
                for (QCState state : StudyManager.getInstance().getQCStates(getContainer()))
                    _qcLabels.put(state.getLabel(), state);
            }

            @Override
            public Object call() throws Exception
            {
                Object currentStateObj = _indexInputQCState<1 ? null : getInput().get(_indexInputQCState);
                String currentStateLabel = null==currentStateObj ? null : currentStateObj.toString();

                if (currentStateLabel != null)
                {
                    QCState state = _qcLabels.get(currentStateLabel);
                    if (null == state)
                    {
                        if (!_autoCreate)
                        {
                            if (notFound.add(currentStateLabel))
                                getRowError().addFieldError(DataSetTableImpl.QCSTATE_LABEL_COLNAME, "QC State not found: " + currentStateLabel);
                            return null;
                        }
                        else
                        {

                            QCState newState = new QCState();
                            // default to public data:
                            newState.setPublicData(true);
                            newState.setLabel(currentStateLabel);
                            newState.setContainer(getContainer());
                            newState = StudyManager.getInstance().insertQCState(user, newState);
                            _qcLabels.put(newState.getLabel(), newState);
                            return newState.getRowId();
                        }
                    }
                    return state.getRowId();
                }
                else if (_defaultQCState != null)
                {
                    return _defaultQCState.getRowId();
                }
                return null;
            }
        }
    }


    private List<String> insertData(User user, DataIteratorBuilder in,
            boolean checkDuplicates, BatchValidationException errors, QCState defaultQCState,
            Logger logger, boolean forUpdate)
            throws SQLException
    {
        ArrayList<String> lsids = new ArrayList<String>();
        DataIteratorBuilder insert = getInsertDataIterator(user, in, lsids, checkDuplicates, errors, defaultQCState, forUpdate);

        DbScope scope = ExperimentService.get().getSchema().getScope();

        try
        {
            scope.ensureTransaction();

            long start = System.currentTimeMillis();
            {
                Pump p = new Pump(insert.getDataIterator(errors), errors);
                p.run();
            }
            long end = System.currentTimeMillis();

            if (errors.hasErrors())
                throw errors;

            _log.info("imported " + getName() + " : " + DateUtil.formatDuration(end-start));
            StudyManager.dataSetModified(this, user, true);
            scope.commitTransaction();
            if (logger != null) logger.debug("commit complete");

            return lsids;
        }
        catch (BatchValidationException x)
        {
            assert x == errors;
            assert errors.hasErrors();
            for (ValidationException rowError : errors.getRowErrors())
            {
                for (int i=0 ; i<rowError.getGlobalErrorCount() ; i++)
                {
                    SimpleValidationError e = rowError.getGlobalError(i);
                    if (!(e.getCause() instanceof SQLException))
                        continue;
                    String msg = translateSQLException((SQLException)e.getCause());
                    if (null != msg)
                        rowError.getGlobalErrorStrings().set(i, msg);
                }
            }
            return Collections.emptyList();
        }
        catch (RuntimeSQLException e)
        {
            String translated = translateSQLException(e);
            if (translated != null)
            {
                errors.addRowError(new ValidationException(translated));
                return Collections.emptyList();
            }
            throw e;
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public String translateSQLException(RuntimeSQLException e)
    {
        return translateSQLException(e.getSQLException());
    }

    public String translateSQLException(SQLException e)
    {
        if (SqlDialect.isConstraintException(e) && e.getMessage() != null && e.getMessage().contains("_pk"))
        {
            StringBuilder sb = new StringBuilder("Duplicate dataset row. All rows must have unique ");
            sb.append(getStudy().getSubjectColumnName());
            if (!isDemographicData())
            {
                sb.append("/");
                if (getStudy().getTimepointType().isVisitBased())
                {
                    sb.append("SequenceNum");
                }
                else
                {
                    sb.append("Date");
                }
            }
            if (getKeyPropertyName() != null)
            {
                sb.append("/");
                sb.append(getKeyPropertyName());
            }
            sb.append(" values. (");
            sb.append(e.getMessage());
            sb.append(")");
            return sb.toString();
        }
        return null;
    }


    private String _managedKeyLock = null;

    public Object getManagedKeyLock()
    {
        if (null == _managedKeyLock)
            _managedKeyLock = (this.getEntityId() + ".MANAGED_KEY_LOCK").intern();
        return _managedKeyLock;
    }


    /**
     * NOTE Currently the caller is still responsible for locking MANAGED_KEY_LOCK while this
     * Iterator is running.  This is asserted in the code, but it would be nice to move the
     * locking into the iterator itself.
     */
    public DataIteratorBuilder getInsertDataIterator(User user, DataIteratorBuilder in,
        @Nullable List<String> lsids,
        boolean checkDuplicates, BatchValidationException errors, QCState defaultQCState,
        boolean forUpdate)
    {
        TableInfo table = getTableInfo(user, false);
        boolean needToHandleQCState = table.getColumn(DataSetTableImpl.QCSTATE_ID_COLNAME) != null;

        DatasetDataIteratorBuilder b = new DatasetDataIteratorBuilder(
                user,
                needToHandleQCState,
                defaultQCState);
        b.setInput(in);
        b.setCheckDuplicates(checkDuplicates);
        b.setForUpdate(forUpdate);
        b.setUseImportAliases(!forUpdate);
        b.setKeyList(lsids);

        StandardETL etl = StandardETL.forInsert(table, b, getContainer(), user, errors);
        DataIteratorBuilder insert = ((UpdateableTableInfo)table).persistRows(etl, errors);
        return insert;
    }


    /** @return the LSID prefix to be used for this dataset's rows */
    public String getURNPrefix()
    {
        return "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":Study.Data-" + getContainer().getRowId() + ":" + getDataSetId() + ".";
    }


     /** @return a SQL expression that generates the LSID for a dataset row */
    public SQLFragment generateLSIDSQL()
    {
        if (null == getStorageTableInfo())
            return new SQLFragment("''");

        ArrayList<SQLFragment> parts = new ArrayList<SQLFragment>();
        parts.add(new SQLFragment("?", getURNPrefix()));
        parts.add(new SQLFragment("participantid"));

        if (!isDemographicData())
        {
            parts.add(new SQLFragment("'.'"));
            if (!_study.getTimepointType().isVisitBased())
                parts.add(StudyManager.sequenceNumFromDateSQL("date"));
            else
                parts.add(new SQLFragment("CAST(CAST(sequencenum AS NUMERIC(15,4)) AS VARCHAR)"));

            if (getKeyPropertyName() != null)
            {
                ColumnInfo key = getStorageTableInfo().getColumn(getKeyPropertyName());
                if (null != key)
                {
                    // It's possible for the key value to be null. In SQL, NULL concatenated with any other value is NULL,
                    // so use COALESCE to get rid of NULLs
                    parts.add(new SQLFragment("'.'"));
                    parts.add(new SQLFragment("COALESCE(_key,'')"));
                }
            }
        }

        SQLFragment sql = StudyManager.getSchema().getSqlDialect().concatenate(parts.toArray(new SQLFragment[parts.size()]));
        return sql;
    }



    /**
     * If all the dupes can be replaced, delete them. If not return the ones that should NOT be replaced
     * and do not delete anything
     */
    private HashMap<String, Object[]> checkAndDeleteDupes(DataIterator rows,
            int indexLSID, int indexPTID, int indexDate, int indexKey, int indexReplace,
            BatchValidationException errors)
    {
        boolean isDemographic = isDemographicData();

        try
        {
            // duplicate keys found that should be deleted
            Set<String> deleteSet = new HashSet<String>();

            // duplicate keys found in error
            LinkedHashMap<String,Object[]> noDeleteMap = new LinkedHashMap<String,Object[]>();

            StringBuffer sbIn = new StringBuffer();
            String sep = "";
            Map<String, Object[]> uriMap = new HashMap<String, Object[]>();
            int count = 0;
            while (rows.next())
            {
                String uri = (String)rows.get(indexLSID);
                String ptid = ConvertUtils.convert(rows.get(indexPTID));
                Object[] key = new Object[4];
                key[0] = ptid;
                key[1] = rows.get(indexDate);
                if (indexKey > 0)
                    key[2] = rows.get(indexKey);
                if (indexReplace > 0)
                {
                    Object replaceStr = rows.get(indexReplace);
                    Boolean replace =
                            null==replaceStr ? Boolean.FALSE :
                            replaceStr instanceof Boolean ? (Boolean)replaceStr :
                            (Boolean)ConvertUtils.convert(String.valueOf(replaceStr),Boolean.class);
                    key[3] = replace;
                }

                String uniq = isDemographic ? ptid : uri;
                if (null != uriMap.put(uniq, key))
                    noDeleteMap.put(uniq,key);

                sbIn.append(sep).append("'").append(uniq).append("'");
                sep = ", ";
                count++;
            }
            if (0 == count)
                return null;

            TableInfo tinfo = getStorageTableInfo();
            SimpleFilter filter = new SimpleFilter();
            filter.addWhereClause((isDemographic?"ParticipantId":"LSID") + " IN (" + sbIn + ")", new Object[]{});

            Map[] results = Table.select(tinfo, Table.ALL_COLUMNS, filter, null, Map.class);
            for (Map orig : results)
            {
                String lsid = (String) orig.get("LSID");
                String uniq = isDemographic ? (String)orig.get("ParticipantID"): lsid;
                Object[] keys = uriMap.get(uniq);
                boolean replace = Boolean.TRUE.equals(keys[3]);
                if (replace)
                {
                    deleteSet.add(lsid);
                }
                else
                {
                    noDeleteMap.put(uniq, keys);
                }
            }

            // If we have duplicates, and we don't have an auto-keyed dataset,
            // then we cannot proceed.
            if (noDeleteMap.size() > 0 && getKeyManagementType() == KeyManagementType.None)
                return noDeleteMap;

            if (deleteSet.size() == 0)
                return null;

            SimpleFilter deleteFilter = new SimpleFilter();
            StringBuffer sbDelete = new StringBuffer();
            sep = "";
            for (String s : deleteSet)
            {
                sbDelete.append(sep).append("'").append(s).append("'");
                sep = ", ";
            }
            deleteFilter.addWhereClause("LSID IN (" + sbDelete + ")", new Object[]{});
            Table.delete(tinfo, deleteFilter);

            return null;
        }
        catch (BatchValidationException vex)
        {
            if (vex != errors)
            {
                for (ValidationException validationException : vex.getRowErrors())
                {
                    errors.addRowError(validationException);
                }
            }
            return null;
        }
        catch (SQLException sqlx)
        {
            throw new RuntimeSQLException(sqlx);
        }
    }


    /**
     * Gets the current highest key value for a server-managed key field.
     * If no data is returned, this method returns 0.
     */
    private int getMaxKeyValue() throws SQLException
    {
        TableInfo tInfo = getStorageTableInfo();
        Integer newKey = Table.executeSingleton(tInfo.getSchema(),
                "SELECT COALESCE(MAX(CAST(_key AS INTEGER)), 0) FROM " + tInfo,
                null,
                Integer.class
                );
        return newKey.intValue();
    }


/*    public boolean verifyUniqueKeys()
    {
        ResultSet rs = null;
        try
        {
            ColumnInfo colKey = getKeyPropertyName()==null ? null : getStorageTableInfo().getColumn(getKeyPropertyName());

            TableInfo tt = getStorageTableInfo();
            String cols = isDemographicData() ? "participantid" :
                    null == colKey ? "participantid, sequencenum" :
                    "participantid, sequencenum, " + colKey.getSelectName();

            rs = Table.executeQuery(DbSchema.get("study"), "SELECT " + cols + " FROM "+ tt.getFromSQL("ds") + " GROUP BY " + cols + " HAVING COUNT(*) > 1", null);
            if (rs.next())
                return false;
            return true;
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


    // WHEN isDemographic() or getKeyPropertyName() changes we need to regenerate the LSIDS for this dataset
    public void regenerateLSIDS()
    {
    }  */



    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataSetDefinition that = (DataSetDefinition) o;

        if (_dataSetId != that._dataSetId) return false;
        // The _studyDateBased member variable is populated lazily in the getter,
        // so go through the getter instead of relying on the variable to be populated
        if (getStudy() != null ? !getStudy().equals(that.getStudy()) : that.getStudy() != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _study != null ? _study.hashCode() : 0;
        result = 31 * result + _dataSetId;
        return result;
    }

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("c(\\d+)_(.+)");

    public static void purgeOrphanedDatasets()
    {
        Connection conn = null;
        try
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            conn = scope.getConnection();
            ResultSet tablesRS = conn.getMetaData().getTables(scope.getDatabaseName(), StudySchema.getInstance().getDatasetSchemaName(), null, new String[]{"TABLE"});
            while (tablesRS.next())
            {
                String tableName = tablesRS.getString("TABLE_NAME");
                boolean delete = true;
                Matcher matcher = TABLE_NAME_PATTERN.matcher(tableName);
                if (matcher.matches())
                {
                    int containerRowId = Integer.parseInt(matcher.group(1));
                    String datasetName = matcher.group(2);
                    Container c = ContainerManager.getForRowId(containerRowId);
                    if (c != null)
                    {
                        StudyImpl study = StudyManager.getInstance().getStudy(c);
                        if (study != null)
                        {
                            for (DataSetDefinition dataset : study.getDataSets())
                            {
                                if (dataset.getName().equalsIgnoreCase(datasetName))
                                {
                                    delete = false;
                                }
                            }
                        }
                    }
                }
                if (delete)
                {
                    Statement statement = null;
                    try
                    {
                        statement = conn.createStatement();
                        statement.execute("DROP TABLE " + StudySchema.getInstance().getDatasetSchemaName() + "." + scope.getSqlDialect().makeLegalIdentifier(tableName));
                    }
                    finally
                    {
                        if (statement != null) { try { statement.close(); } catch (SQLException e) {} }
                    }
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (conn != null)
            {
                try { conn.close(); } catch (SQLException e) {}
            }
        }
    }



    private static class DataSetDefObjectFactory extends BeanObjectFactory
    {
        DataSetDefObjectFactory()
        {
            super(DataSetDefinition.class);
            boolean found;
            found = _readableProperties.remove("storageTableInfo");
            assert found;
            found = _readableProperties.remove("domain");
            assert found;
        }
    }

    static
    {
        ObjectFactory.Registry.register(DataSetDefinition.class, new DataSetDefObjectFactory());        
    }

    /** UPGRADE
     *
     * upgrade happens in steps to maximize the chance that either no data is deleted unless the entire upgrade works
     *
     * 1) provision all tables
     * 2) copy all data into provisioned tables
     * 3) delete data from OntologyManager
     * 
     ****/

    public static void upgradeAll() throws SQLException
    {
        // find all containers with datasets
        List<Container> allContainers = new ArrayList<Container>();
        ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), new SQLFragment("SELECT DISTINCT container FROM study.dataset"));
        while (rs.next())
        {
            String id = rs.getString(1);
            Container c = ContainerManager.getForId(id);
            if (null == c)
            {
                ModuleUpgrader.getLogger().error("Found orphaned dataset with containerid=" + id);
                continue;
            }
            allContainers.add(c);
        }
        ResultSetUtil.close(rs);

        // find all datasets
        List<DataSetDefinition> defs = new ArrayList<DataSetDefinition>();
        for (Container c : allContainers)
        {
            DataSetDefinition[] arr = StudyManager.getInstance().getDataSetDefinitionsForUpgrade(c);
            if (null == arr) continue;
            defs.addAll(Arrays.asList(arr));
        }

        List<DataSetDefinition> defsVerify = new ArrayList<DataSetDefinition>();
        ModuleUpgrader.getLogger().info("STUDY UPGRADE drop old materialized tables");
        for (DataSetDefinition def : defs)
        {
            defsVerify.add(def.upgradeVerifyDomain());
        }
        defs = defsVerify;

        ModuleUpgrader.getLogger().info("STUDY UPGRADE drop old materialized tables");
        for (DataSetDefinition def : defs)
            def.dropOldMaterializedTable();

//        CPUTimer.dumpAllTimers(ModuleUpgrader.getLogger());
        ModuleUpgrader.getLogger().info("STUDY UPGRADE create new tables");
        for (DataSetDefinition def : defs)
            def.upgradeProvision();

//        CPUTimer.dumpAllTimers(ModuleUpgrader.getLogger());
        ModuleUpgrader.getLogger().info("STUDY UPGRADE copy data");
        for (DataSetDefinition def : defs)
            def.upgradeCopy();

//        CPUTimer.dumpAllTimers(ModuleUpgrader.getLogger());
        ModuleUpgrader.getLogger().info("STUDY UPGRADE resuming SQL script");
        // delete exp.objectproperty is in the upgrade script
    }


    private DataSetDefinition upgradeVerifyDomain() throws SQLException
    {
        String typeURI = getTypeURI();
        if (null != typeURI)
        {
            OntologyManager.ensureDomainDescriptor(typeURI, getName(), getContainer());
            return this;
        }
        else
        {
            DataSetDefinition def = this.createMutable();
            String domainURI = StudyManager.getInstance().getDomainURI(getContainer(), null, def);
            OntologyManager.ensureDomainDescriptor(domainURI, def.getName(), getContainer());
            def.setTypeURI(domainURI);
            StudyManager.getInstance().updateDataSetDefinition(null, def);
            return StudyManager.getInstance().getDataSetDefinition(getStudy(), getDataSetId());
        }
    }
    

    private void upgradeProvision() throws SQLException
    {
        if (null == getTypeURI())
            return;
        provisionTable();
    }


    private void upgradeCopy() throws SQLException
    {
        TableInfo fromTable = new StudyDataTableInfoUpgrade(this);
        TableInfo toTable = getStorageTableInfo();

        if (null == toTable)
        {
            throw new IllegalStateException("Unprovisioned dataset: " + getName());
        }

        Map<String,ColumnInfo> colMap = new CaseInsensitiveHashMap<ColumnInfo>();
        for (ColumnInfo c : fromTable.getColumns())
        {
            if (null != c.getPropertyURI())
                colMap.put(c.getPropertyURI(), c);
            colMap.put(c.getName(), c);
        }

        SQLFragment insertInto = new SQLFragment("INSERT INTO " + toTable.getSelectName() + " (");
        SQLFragment select = new SQLFragment("SELECT " );
        Map<String,SQLFragment> joinMap = new HashMap<String,SQLFragment>();
        String comma = "";
        for (ColumnInfo to : toTable.getColumns())
        {
            ColumnInfo from = colMap.get(to.getPropertyURI());
            if (null == from)
                from = colMap.get(to.getName());
            if (null == from)
            {
                String name = to.getName().toLowerCase();
                if ("modifiedby".equals(name) || "createdby".equals(name))
                {
                    continue;
                }
                else if (name.endsWith("_" + MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
                {
                    from = colMap.get(name.substring(0,name.length()-(MvColumn.MV_INDICATOR_SUFFIX.length()+1)) + MvColumn.MV_INDICATOR_SUFFIX);
                    if (null == from)
                        continue;
                }
                else
                {
                    ModuleUpgrader.getLogger().error("Could not copy column: " + getContainer().getId()+"-"+getContainer().getPath() + " " + getDataSetId() + "-" + getName() + " " + to.getName());
                    continue;
                }
            }
            insertInto.append(comma).append(to.getSelectName());
            select.append(comma).append(from.getValueSql("SD"));
            from.declareJoins("SD", joinMap);
            comma = ", ";
        }
        insertInto.append(")\n");
        insertInto.append(select);
        insertInto.append("\n FROM ").append(fromTable.getFromSQL("SD"));
        for (SQLFragment j : joinMap.values())
            insertInto.append(j);

        ModuleUpgrader.getLogger().info("Migrating data for [" + getContainer().getPath() + "]  '" + getName() + "'");
        ModuleUpgrader.getLogger().info(insertInto.toString());
        Table.execute(StudySchema.getInstance().getSchema(), insertInto);
    }


    @Deprecated
    private static class StudyDataTableInfoUpgrade extends SchemaTableInfo
    {
        private Container _container;
        int _datasetId;
        SQLFragment _fromSql;

        StudyDataTableInfoUpgrade(DataSetDefinition def)
        {
            super(StudySchema.getInstance().getSchema(), DatabaseTableType.TABLE, def.getLabel());
            _container = def.getContainer();
            _datasetId = def.getDataSetId();

            // getTableInfoStudyData is the new UNION version
            //TableInfo studyData = StudySchema.getInstance().getTableInfoStudyData(study, user);
            TableInfo studyData = StudySchema.getInstance().getSchema().getTable("StudyData");

            List<ColumnInfo> columnsBase = studyData.getColumns("_key","lsid","participantid","ParticipantSequenceNum","sourcelsid", "created","modified","sequenceNum","qcstate","participantsequencenum");
            for (ColumnInfo col : columnsBase)
            {
                ColumnInfo wrapped = new ColumnInfo(col, this);
                addColumn(wrapped);
            }

            if (!def.getStudy().getTimepointType().isVisitBased())
            {
                ColumnInfo wrapped = new AliasedColumn(this, "Date", studyData.getColumn("_VisitDate"));
                addColumn(wrapped);
            }

            // Property columns
            for (ColumnInfo c : def.getDomain().getColumns(this, getColumn("LSID"), _container, null))
                addColumn(c);

            _fromSql = new SQLFragment(
                    "SELECT *\n" +
                    "  FROM " + studyData.getSelectName() + "\n"+
                    "  WHERE container=? AND datasetid=?");
            _fromSql.add(_container);
            _fromSql.add(_datasetId);
        }

        @Override
        public String getSelectName()
        {
            return null;
        }


        @NotNull
        @Override
        public SQLFragment getFromSQL()
        {
            return _fromSql;
        }
    }

    private void dropOldMaterializedTable()
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        Connection conn=null;
        try
        {
            // avoid logging sql exception
            //Table.execute(schema, "DROP TABLE studydataset." + schema.getScope().getSqlDialect().makeLegalIdentifier(getCacheString()), new Object[0]);
            conn = schema.getScope().getConnection();
            conn.prepareStatement("DROP TABLE studydataset." + schema.getScope().getSqlDialect().makeLegalIdentifier(getCacheString())).execute();
        }
        catch (SQLException x)
        {
            /* */
        }
        finally
        {
            schema.getScope().releaseConnection(conn);
        }
    }

    private String getCacheString()
    {
        return "c" + getContainer().getRowId() + "_" + getName().toLowerCase();
    }


    public static void cleanupOrphanedDatasetDomains() throws SQLException
    {
        DbSchema s = StudySchema.getInstance().getSchema();
        if (null == s)
            return;

        s.getScope().ensureTransaction();

        ResultSet rs = null;
        try
        {
            rs = Table.executeQuery(s, "SELECT domainid FROM exp.domaindescriptor WHERE domainuri like '%:StudyDataset%Folder-%' and domainuri not in (SELECT typeuri from study.dataset)", new Object[0]);
            while (rs.next())
            {
                int domainid = rs.getInt(1);
                Domain domain = PropertyService.get().getDomain(domainid);
                try
                {
                    domain.delete(null);
                }
                catch (DomainNotFoundException x)
                {
                    //
                }
            }
            s.getScope().commitTransaction();
        }
        finally
        {
            ResultSetUtil.close(rs);
            s.getScope().closeConnection();
        }
    }


    public static class TestCleanupOrphanedDatasetDomains extends Assert
    {
        @Test
        public void test() throws Exception
        {
            cleanupOrphanedDatasetDomains();
        }
    }
}


/*** TODO
 [ ] verify synchronize/transact updates to domain/storage table
 [N] test column rename, name collisions
 [N] we seem to still be orphaning tables in the studydataset schema
 [ ] exp StudyDataSetColumn usage of getStudyDataTable()
 // FUTURE
 [ ] don't use subjectname alias at this level
 [ ] remove _Key columns
 [ ] make OntologyManager.insertTabDelimited could handle materialized domains (maybe two subclasses?)
 [ ] clean up architecture of import/queryupdateservice
 ***/
