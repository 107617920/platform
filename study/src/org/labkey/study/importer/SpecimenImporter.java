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

package org.labkey.study.importer;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.*;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;
import org.labkey.study.visitmanager.VisitManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.*;

/**
 * User: brittp
 * Date: Mar 13, 2006
 * Time: 2:18:48 PM
 */
public class SpecimenImporter
{
    private final CPUTimer cpuPopulateMaterials = new CPUTimer("populateMaterials");
    private final CPUTimer cpuUpdateSpecimens = new CPUTimer("updateSpecimens");
    private final CPUTimer cpuInsertSpecimens = new CPUTimer("insertSpecimens");
    private final CPUTimer cpuUpdateSpecimenEvents = new CPUTimer("updateSpecimenEvents");
    private final CPUTimer cpuInsertSpecimenEvents = new CPUTimer("insertSpecimenEvents");
    private final CPUTimer cpuMergeTable = new CPUTimer("mergeTable");
    private final CPUTimer cpuCreateTempTable = new CPUTimer("createTempTable");
    private final CPUTimer cpuPopulateTempTable = new CPUTimer("populateTempTable");
    private final CPUTimer cpuCurrentLocations = new CPUTimer("updateCurrentLocations");


    public static class ImportableColumn
    {
        private final String _tsvColumnName;
        protected final String _dbType;
        private final String _dbColumnName;
        private Class _javaType = null;
        private final boolean _unique;
        private int _size = -1;

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType)
        {
            this(tsvColumnName, dbColumnName, databaseType, false);
        }

        public ImportableColumn(String tsvColumnName, String dbColumnName, String databaseType, boolean unique)
        {
            _tsvColumnName = tsvColumnName;
            _dbColumnName = dbColumnName;
            _unique = unique;
            if (DURATION_TYPE.equals(databaseType))
            {
                _dbType = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDataType();
                _javaType = TimeOnlyDate.class;
            }
            else if (DATETIME_TYPE.equals(databaseType))
            {
                _dbType = StudySchema.getInstance().getSqlDialect().getDefaultDateTimeDataType();
                _javaType = java.util.Date.class;
            }
            else
                _dbType = databaseType.toUpperCase();
            if (_dbType.startsWith("VARCHAR("))
            {
                assert _dbType.charAt(_dbType.length() - 1) == ')' : "Unexpected VARCHAR type format: " + _dbType;
                String sizeStr = _dbType.substring(8, _dbType.length() - 1);
                _size = Integer.parseInt(sizeStr);
            }
        }


        public ColumnDescriptor getColumnDescriptor()
        {
            return new ColumnDescriptor(_tsvColumnName, getJavaType());
        }

        public String getDbColumnName()
        {
            return _dbColumnName;
        }

        public String getTsvColumnName()
        {
            return _tsvColumnName;
        }

        public boolean isUnique()
        {
            return _unique;
        }

        public Class getJavaType()
        {
            if (_javaType == null)
            {
                if (_dbType.indexOf("VARCHAR") >= 0)
                    _javaType = String.class;
                else if (_dbType.indexOf(DATETIME_TYPE) >= 0)
                    throw new IllegalStateException("Java types for DateTime/Timestamp columns should be previously initalized.");
                else if (_dbType.indexOf("FLOAT") >= 0 || _dbType.indexOf(NUMERIC_TYPE) >= 0)
                    _javaType = Float.class;
                else if (_dbType.indexOf("INT") >= 0)
                    _javaType = Integer.class;
                else if (_dbType.indexOf(BOOLEAN_TYPE) >= 0)
                    _javaType = Boolean.class;
                else
                    throw new UnsupportedOperationException("Unrecognized sql type: " + _dbType);
            }
            return _javaType;
        }

        public JdbcType getSQLType()
        {
            if (getJavaType() == String.class)
                return JdbcType.VARCHAR;
            else if (getJavaType() == java.util.Date.class)
                return JdbcType.TIMESTAMP;
            else if (getJavaType() == TimeOnlyDate.class)
                return JdbcType.TIMESTAMP;
            else if (getJavaType() == Float.class)
                return JdbcType.REAL;
            else if (getJavaType() == Integer.class)
                return JdbcType.INTEGER;
            else if (getJavaType() == Boolean.class)
                return JdbcType.BOOLEAN;
            else
                throw new UnsupportedOperationException("SQL type has not been defined for DB type " + _dbType + ", java type " + getJavaType());
        }

        public int getMaxSize()
        {
            return _size;
        }
    }

    public enum TargetTable
    {
        SPECIMEN_EVENTS
        {
            public String getName()
            {
                return "SpecimenEvent";
            }

            public boolean isEvents()
            {
                return true;
            }

            public boolean isSpecimens()
            {
                return false;
            }

            public boolean isVials()
            {
                return false;
            }},
        SPECIMENS
        {
            public String getName()
            {
                return "Specimen";
            }

            public boolean isEvents()
            {
                return false;
            }

            public boolean isSpecimens()
            {
                return true;
            }

            public boolean isVials()
            {
                return false;
            }},
        VIALS
        {
            public String getName()
            {
                return "Vial";
            }

            public boolean isEvents()
            {
                return false;
            }

            public boolean isSpecimens()
            {
                return false;
            }

            public boolean isVials()
            {
                return true;
            }
        },
        SPECIMENS_AND_SPECIMEN_EVENTS
        {
            public String getName()
            {
                return "Specimen";
            }

            public boolean isEvents()
            {
                return true;
            }

            public boolean isSpecimens()
            {
                return true;
            }

            public boolean isVials()
            {
                return false;
            }
        },
        VIALS_AND_SPECIMEN_EVENTS
        {
            public String getName()
            {
                return "Vials";
            }

            public boolean isEvents()
            {
                return true;
            }

            public boolean isSpecimens()
            {
                return false;
            }

            public boolean isVials()
            {
                return true;
            }};

        public abstract boolean isEvents();
        public abstract boolean isVials();
        public abstract boolean isSpecimens();
        public abstract String getName();
    }

    public static class SpecimenColumn extends ImportableColumn
    {
        private final TargetTable _targetTable;
        private String _fkTable;
        private String _joinType;
        private String _fkColumn;
        private String _aggregateEventFunction;

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn, boolean unique)
        {
            super(tsvColumnName, dbColumnName, databaseType, unique);
            _targetTable = eventColumn;
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn, String aggregateEventFunction)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
            _aggregateEventFunction = aggregateEventFunction;
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType,
                              TargetTable eventColumn, String fkTable, String fkColumn)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, fkTable, fkColumn, "INNER");
        }

        public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType,
                              TargetTable eventColumn, String fkTable, String fkColumn, String joinType)
        {
            this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
            _fkColumn = fkColumn;
            _fkTable = fkTable;
            _joinType = joinType;
        }

        public TargetTable getTargetTable()
        {
            return _targetTable;
        }

        public String getFkColumn()
        {
            return _fkColumn;
        }

        public String getFkTable()
        {
            return _fkTable;
        }

        public String getJoinType()
        {
            return _joinType;
        }

        public String getDbType()
        {
            return _dbType;
        }

        public String getAggregateEventFunction()
        {
            return _aggregateEventFunction;
        }

        public String getFkTableAlias()
        {
            return getDbColumnName() + "Lookup";
        }
    }

    private static class SpecimenLoadInfo
    {
        private final String _tempTableName;
        private final List<SpecimenColumn> _availableColumns;
        private final int _rowCount;
        private final Container _container;
        private final User _user;
        private final DbSchema _schema;

        public SpecimenLoadInfo(User user, Container container, DbSchema schema, List<SpecimenColumn> availableColumns, int rowCount, String tempTableName)
        {
            _user = user;
            _schema = schema;
            _container = container;
            _availableColumns = availableColumns;
            _rowCount = rowCount;
            _tempTableName = tempTableName;
        }

        /** Number of rows inserted into the temp table. */
        public int getRowCount()
        {
            return _rowCount;
        }

        public List<SpecimenColumn> getAvailableColumns()
        {
            return _availableColumns;
        }

        public String getTempTableName()
        {
            return _tempTableName;
        }

        public Container getContainer()
        {
            return _container;
        }

        public User getUser()
        {
            return _user;
        }

        public DbSchema getSchema()
        {
            return _schema;
        }
    }

    private static final String DATETIME_TYPE = "SpecimenImporter/DateTime";
    private static final String DURATION_TYPE = "SpecimenImporter/TimeOnlyDate";
    private static final String NUMERIC_TYPE = "NUMERIC(15,4)";
    private static final String BOOLEAN_TYPE = StudySchema.getInstance().getSqlDialect().getBooleanDataType();
    private static final String GLOBAL_UNIQUE_ID_TSV_COL = "global_unique_specimen_id";
    private static final String LAB_ID_TSV_COL = "lab_id";
    private static final String SPEC_NUMBER_TSV_COL = "specimen_number";
    private static final String EVENT_ID_COL = "record_id";

    // SpecimenEvent columns that form a psuedo-unqiue constraint
    private static final SpecimenColumn GLOBAL_UNIQUE_ID, LAB_ID, SHIP_DATE, STORAGE_DATE, LAB_RECEIPT_DATE;

    public static final Collection<SpecimenColumn> SPECIMEN_COLUMNS = Arrays.asList(
            new SpecimenColumn(EVENT_ID_COL, "ExternalId", "INT NOT NULL", TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("record_source", "RecordSource", "VARCHAR(10)", TargetTable.SPECIMEN_EVENTS),
            GLOBAL_UNIQUE_ID = new SpecimenColumn(GLOBAL_UNIQUE_ID_TSV_COL, "GlobalUniqueId", "VARCHAR(20)", TargetTable.VIALS, true),
            LAB_ID = new SpecimenColumn(LAB_ID_TSV_COL, "LabId", "INT", TargetTable.SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER") {
                public boolean isUnique() { return true; }
            },
            new SpecimenColumn("originating_location", "OriginatingLocationId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("unique_specimen_id", "UniqueSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ptid", "Ptid", "VARCHAR(32)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("parent_specimen_id", "ParentSpecimenId", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("draw_timestamp", "DrawTimestamp", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("sal_receipt_date", "SalReceiptDate", DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn(SPEC_NUMBER_TSV_COL, "SpecimenNumber", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("class_id", "ClassId", "VARCHAR(4)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("visit_value", "VisitValue", NUMERIC_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("protocol_number", "ProtocolNumber", "VARCHAR(10)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("visit_description", "VisitDescription", "VARCHAR(3)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("other_specimen_id", "OtherSpecimenId", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("volume", "Volume", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS, "MAX"),
            new SpecimenColumn("volume_units", "VolumeUnits", "VARCHAR(3)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("stored", "Stored", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("storage_flag", "storageFlag", "INT", TargetTable.SPECIMEN_EVENTS),
            STORAGE_DATE = new SpecimenColumn("storage_date", "StorageDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("ship_flag", "ShipFlag", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("ship_batch_number", "ShipBatchNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            SHIP_DATE = new SpecimenColumn("ship_date", "ShipDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("imported_batch_number", "ImportedBatchNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            LAB_RECEIPT_DATE = new SpecimenColumn("lab_receipt_date", "LabReceiptDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS, true),
            new SpecimenColumn("expected_time_value", "ExpectedTimeValue", "FLOAT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("expected_time_unit", "ExpectedTimeUnit", "VARCHAR(15)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("group_protocol", "GroupProtocol", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("sub_additive_derivative", "SubAdditiveDerivative", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("comments", "Comments", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("primary_specimen_type_id", "PrimaryTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenPrimaryType", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("derivative_type_id", "DerivativeTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenDerivative", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("derivative_type_id_2", "DerivativeTypeId2", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenDerivative", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("additive_type_id", "AdditiveTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenAdditive", "ExternalId", "LEFT OUTER"),
            new SpecimenColumn("specimen_condition", "SpecimenCondition", "VARCHAR(3)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("sample_number", "SampleNumber", "INT", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("x_sample_origin", "XSampleOrigin", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("external_location", "ExternalLocation", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("update_timestamp", "UpdateTimestamp", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("requestable", "Requestable", BOOLEAN_TYPE, TargetTable.VIALS),
            new SpecimenColumn("freezer", "freezer", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_level1", "fr_level1", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_level2", "fr_level2", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_container", "fr_container", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("fr_position", "fr_position", "VARCHAR(200)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("shipped_from_lab", "ShippedFromLab", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("shipped_to_lab", "ShippedtoLab", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("frozen_time", "FrozenTime", DURATION_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("primary_volume", "PrimaryVolume", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("primary_volume_units", "PrimaryVolumeUnits", "VARCHAR(20)", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
            new SpecimenColumn("processed_by_initials", "ProcessedByInitials", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("processing_date", "ProcessingDate", DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("processing_time", "ProcessingTime", DURATION_TYPE, TargetTable.SPECIMEN_EVENTS),
            new SpecimenColumn("total_cell_count", "TotalCellCount", "INT", TargetTable.VIALS_AND_SPECIMEN_EVENTS)
    );

    public static final Collection<ImportableColumn> ADDITIVE_COLUMNS = Arrays.asList(
            new ImportableColumn("additive_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_additive_code", "LdmsAdditiveCode", "VARCHAR(3)"),
            new ImportableColumn("labware_additive_code", "LabwareAdditiveCode", "VARCHAR(20)"),
            new ImportableColumn("additive", "Additive", "VARCHAR(100)")
    );

    public static final Collection<ImportableColumn> DERIVATIVE_COLUMNS = Arrays.asList(
            new ImportableColumn("derivative_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_derivative_code", "LdmsDerivativeCode", "VARCHAR(3)"),
            new ImportableColumn("labware_derivative_code", "LabwareDerivativeCode", "VARCHAR(20)"),
            new ImportableColumn("derivative", "Derivative", "VARCHAR(100)")
    );

    public static final Collection<ImportableColumn> SITE_COLUMNS = Arrays.asList(
            new ImportableColumn("lab_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("ldms_lab_code", "LdmsLabCode", "INT"),
            new ImportableColumn("labware_lab_code", "LabwareLabCode", "VARCHAR(20)"),
            new ImportableColumn("lab_name", "Label", "VARCHAR(200)"),
            new ImportableColumn("lab_upload_code", "LabUploadCode", "VARCHAR(2)"),
            new ImportableColumn("is_sal", "Sal", BOOLEAN_TYPE),
            new ImportableColumn("is_repository", "Repository", BOOLEAN_TYPE),
            new ImportableColumn("is_clinic", "Clinic", BOOLEAN_TYPE),
            new ImportableColumn("is_endpoint", "Endpoint", BOOLEAN_TYPE)
    );

    public static final Collection<ImportableColumn> PRIMARYTYPE_COLUMNS = Arrays.asList(
            new ImportableColumn("primary_type_id", "ExternalId", "INT NOT NULL", true),
            new ImportableColumn("primary_type_ldms_code", "PrimaryTypeLdmsCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type_labware_code", "PrimaryTypeLabwareCode", "VARCHAR(5)"),
            new ImportableColumn("primary_type", "PrimaryType", "VARCHAR(100)")
    );

    private List<SpecimenColumn> _specimenCols;
    private List<SpecimenColumn> _vialCols;
    private List<SpecimenColumn> _vialEventCols;
    private String _specimenColsSql;
    private String _vialColsSql;
    private String _vialEventColsSql;

    private Logger _logger;

    private static final int SQL_BATCH_SIZE = 100;

    public void process(User user, Container container, List<File> files, boolean merge, Logger logger) throws SQLException, IOException
    {
        Map<String, File> fileMap = createFilemap(files);

        // Create a map of Tables->TabLoaders
        Map<String, Iterable<Map<String, Object>>> iterMap = new HashMap<String, Iterable<Map<String, Object>>>();
        iterMap.put("labs", loadTsv(SITE_COLUMNS, fileMap.get("labs"), "study.Site"));
        iterMap.put("additives", loadTsv(ADDITIVE_COLUMNS, fileMap.get("additives"), "study.SpecimenAdditive"));
        iterMap.put("derivatives", loadTsv(DERIVATIVE_COLUMNS, fileMap.get("derivatives"), "study.SpecimenDerivative"));
        iterMap.put("primary_types", loadTsv(PRIMARYTYPE_COLUMNS, fileMap.get("primary_types"), "study.SpecimenPrimaryType"));
        iterMap.put("specimens", loadTsv(SPECIMEN_COLUMNS, fileMap.get("specimens"), "study.Specimen"));

        process(user, container, iterMap, merge, logger);
    }

    private void resyncStudy(User user, Container container) throws SQLException
    {
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        TableInfo tableSpecimen = StudySchema.getInstance().getTableInfoSpecimen();

        Table.execute(tableParticipant.getSchema(),
                "INSERT INTO " + tableParticipant + " (container, participantid)\n" +
                "SELECT DISTINCT ?, ptid AS participantid\n" +
                "FROM " + tableSpecimen + "\n"+
                "WHERE container = ? AND ptid IS NOT NULL AND " +
                "ptid NOT IN (select participantid from " + tableParticipant + " where container = ?)",
                new Object[] {container, container, container});

        StudyImpl study = StudyManager.getInstance().getStudy(container);
        info("Updating study-wide subject/visit information...");
        StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.<DataSetDefinition>emptyList());
        info("Subject/visit update complete.");
    }

    private void updateAllStatistics() throws SQLException
    {
        updateStatistics(ExperimentService.get().getTinfoMaterial());
        updateStatistics(StudySchema.getInstance().getTableInfoSpecimen());
        updateStatistics(StudySchema.getInstance().getTableInfoVial());
        updateStatistics(StudySchema.getInstance().getTableInfoSpecimenEvent());
    }

    private boolean updateStatistics(TableInfo tinfo) throws SQLException
    {
        info("Updating statistics for " + tinfo + "...");
        boolean updated = tinfo.getSqlDialect().updateStatistics(tinfo);
        if (updated)
            info("Statistics update " + tinfo + " complete.");
        else
            info("Statistics update not supported for this database type.");
        return updated;
    }

    protected void process(User user, Container container, Map<String, Iterable<Map<String, Object>>> iterMap, boolean merge, Logger logger) throws SQLException, IOException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        _logger = logger;

        try
        {
            DbScope scope = schema.getScope();
            if (!DEBUG)
                scope.beginTransaction();

            mergeTable(schema, container, "study.Site", SITE_COLUMNS, iterMap.get("labs"), true);
            if (merge)
            {
                mergeTable(schema, container, "study.SpecimenAdditive", ADDITIVE_COLUMNS, iterMap.get("additives"), false);
                mergeTable(schema, container, "study.SpecimenDerivative", DERIVATIVE_COLUMNS, iterMap.get("derivatives"), false);
                mergeTable(schema, container, "study.SpecimenPrimaryType", PRIMARYTYPE_COLUMNS, iterMap.get("primary_types"), false);
            }
            else
            {
                replaceTable(schema, container, "study.SpecimenAdditive", ADDITIVE_COLUMNS, iterMap.get("additives"), false);
                replaceTable(schema, container, "study.SpecimenDerivative", DERIVATIVE_COLUMNS, iterMap.get("derivatives"), false);
                replaceTable(schema, container, "study.SpecimenPrimaryType", PRIMARYTYPE_COLUMNS, iterMap.get("primary_types"), false);
            }
            
            // Specimen temp table must be populated AFTER the types tables have been reloaded, since the SpecimenHash
            // calculated in the temp table relies on the new RowIds for the types:
            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(user, schema, container, iterMap.get("specimens"));

            // NOTE: if no rows were loaded in the temp table, don't remove existing materials/specimens/vials/events.
            if (loadInfo.getRowCount() > 0)
                populateSpecimenTables(loadInfo, merge);
            else
                info("Specimens: 0 rows found in input");

            cpuCurrentLocations.start();
            updateCalculatedSpecimenData(container, user, _logger);
            cpuCurrentLocations.stop();
            info("Time to determine locations: " + cpuCurrentLocations.toString());

            resyncStudy(user, container);

            // Drop the temp table within the transaction; otherwise, we may get a different connection object,
            // where the table is no longer available.  Note that this means that the temp table will stick around
            // if an exception is throw during loading, but this is probably okay- the DB will clean it up eventually.
            Table.execute(schema, "DROP TABLE " + loadInfo.getTempTableName(), null);

            if (!DEBUG)
                scope.commitTransaction();

            updateAllStatistics();
        }
        finally
        {
            if (schema.getScope().isTransactionActive())
                schema.getScope().rollbackTransaction();
            StudyManager.getInstance().clearCaches(container, false);
            SampleManager.getInstance().clearCaches(container);
        }
        dumpTimers();
    }

    private void dumpTimers()
    {
        Logger logDebug = Logger.getLogger(SpecimenImporter.class);
        logDebug.debug("  cumulative\t     average\t       calls\ttimer");
        logDebug.debug(cpuPopulateMaterials);
        logDebug.debug(cpuInsertSpecimens);
        logDebug.debug(cpuUpdateSpecimens);
        logDebug.debug(cpuInsertSpecimenEvents);
        logDebug.debug(cpuUpdateSpecimenEvents);
        logDebug.debug(cpuMergeTable);
        logDebug.debug(cpuCreateTempTable);
        logDebug.debug(cpuPopulateTempTable);
    }

    private SpecimenLoadInfo populateTempSpecimensTable(User user, DbSchema schema, Container container, Iterable<Map<String, Object>> iter) throws SQLException, IOException
    {
        String tableName = createTempTable(schema);
        Pair<List<SpecimenColumn>, Integer> pair = populateTempTable(schema, container, tableName, iter);
        return new SpecimenLoadInfo(user, container, schema, pair.first, pair.second, tableName);
    }


    private void populateSpecimenTables(SpecimenLoadInfo info, boolean merge) throws SQLException, IOException
    {
        if (!merge)
        {
            SimpleFilter containerFilter = new SimpleFilter("Container", info.getContainer().getId());
            info("Deleting old data from Specimen Event table...");
            Table.delete(StudySchema.getInstance().getTableInfoSpecimenEvent(), containerFilter);
            info("Complete.");
            info("Deleting old data from Vial table...");
            Table.delete(StudySchema.getInstance().getTableInfoVial(), containerFilter);
            info("Complete.");
            info("Deleting old data from Specimen table...");
            Table.delete(StudySchema.getInstance().getTableInfoSpecimen(), containerFilter);
            info("Complete.");
        }

        populateMaterials(info, merge);
        populateSpecimens(info, merge);
        populateVials(info, merge);
        populateVialEvents(info, merge);

        if (merge)
        {
            // Delete any orphaned specimen rows without vials
            Table.execute(StudySchema.getInstance().getSchema(),
                    "DELETE FROM " + StudySchema.getInstance().getTableInfoSpecimen() +
                    " WHERE Container=? " +
                    " AND RowId NOT IN (SELECT SpecimenId FROM " + StudySchema.getInstance().getTableInfoVial() + ")",
                    new Object[] { info.getContainer() });
        }
    }

    private static final int CURRENT_SITE_UPDATE_SIZE = 1000;

    private static boolean safeIntegerEqual(Integer a, Integer b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.intValue() == b.intValue();
    }

    private static <T> boolean safeObjectEquals(T a, T b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }


    private static Set<String> getConflictingEventColumns(SpecimenEvent[] events)
    {
        if (events.length <= 1)
            return Collections.emptySet();
        Set<String> conflicts = new HashSet<String>();

        try
        {
            for (SpecimenColumn col :  SPECIMEN_COLUMNS)
            {
                if (col.getAggregateEventFunction() == null && col.getTargetTable() == TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS)
                {
                    // lower the case of the first character:
                    String propName = col.getDbColumnName().substring(0, 1).toLowerCase() + col.getDbColumnName().substring(1);
                    for (int i = 0; i < events.length - 1; i++)
                    {
                        SpecimenEvent event = events[i];
                        SpecimenEvent nextEvent = events[i + 1];
                        Object currentValue = PropertyUtils.getProperty(event, propName);
                        Object nextValue = PropertyUtils.getProperty(nextEvent, propName);
                        if (!safeObjectEquals(currentValue, nextValue))
                        {
                            conflicts.add(col.getDbColumnName());
                        }
                    }
                }
            }
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
        return conflicts;
    }

    private static void updateCommentSpecimenHashes(Container container, Logger logger) throws SQLException
    {
        SQLFragment sql = new SQLFragment();
        TableInfo commentTable = StudySchema.getInstance().getTableInfoSpecimenComment();
        TableInfo specimenTable = StudySchema.getInstance().getTableInfoSpecimenDetail();
        sql.append("UPDATE ").append(commentTable).append(" SET SpecimenHash = (\n");
        sql.append("SELECT SpecimenHash FROM ").append(specimenTable).append(" WHERE ").append(specimenTable);
        sql.append(".GlobalUniqueId = ").append(commentTable).append(".GlobalUniqueId AND ");
        sql.append(specimenTable).append(".Container = ?)\nWHERE ").append(commentTable).append(".Container = ?");
        sql.add(container.getId());
        sql.add(container.getId());
        logger.info("Updating hash codes for existing comments...");
        Table.execute(StudySchema.getInstance().getSchema(), sql);
        logger.info("Complete.");
    }

    private static void prepareQCComments(Container container, User user, Logger logger) throws SQLException
    {
        StringBuilder columnList = new StringBuilder();
        columnList.append("VialId");
        for (SpecimenColumn col : SPECIMEN_COLUMNS)
        {
            if (col.getTargetTable() == TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS && col.getAggregateEventFunction() == null)
            {
                columnList.append(",\n    ");
                columnList.append(col.getDbColumnName());
            }
        }

        // find the global unique ID for those vials with conflicts:
        SQLFragment conflictedGUIDs = new SQLFragment("SELECT GlobalUniqueId FROM ");
        conflictedGUIDs.append(StudySchema.getInstance().getTableInfoVial());
        conflictedGUIDs.append(" WHERE RowId IN (\n");
        conflictedGUIDs.append("SELECT VialId FROM\n");
        conflictedGUIDs.append("(SELECT DISTINCT\n").append(columnList).append("\nFROM ").append(StudySchema.getInstance().getTableInfoSpecimenEvent());
        conflictedGUIDs.append("\nWHERE Container = ?\nGROUP BY\n").append(columnList).append(") ");
        conflictedGUIDs.append("AS DupCheckView\nGROUP BY VialId HAVING Count(VialId) > 1");
        conflictedGUIDs.add(container.getId());
        conflictedGUIDs.append("\n)");

        // Delete comments that were holding QC state (and nothing more) for vials that do not currently have any conflicts
        SQLFragment deleteClearedVials = new SQLFragment("DELETE FROM study.SpecimenComment WHERE Container = ? ");
        deleteClearedVials.add(container.getId());
        deleteClearedVials.append("AND Comment IS NULL AND QualityControlFlag = ? ");
        deleteClearedVials.add(Boolean.TRUE);
        deleteClearedVials.append("AND QualityControlFlagForced = ? ");
        deleteClearedVials.add(Boolean.FALSE);
        deleteClearedVials.append("AND GlobalUniqueId NOT IN (").append(conflictedGUIDs).append(");");
        logger.info("Clearing QC flags for vials that no longer have history conflicts...");
        Table.execute(StudySchema.getInstance().getSchema(), deleteClearedVials);
        logger.info("Complete.");


        // Insert placeholder comments for newly discovered QC problems; SpecimenHash will be updated within updateCalculatedSpecimenData, so this
        // doesn't have to be set here.
        SQLFragment insertPlaceholderQCComments = new SQLFragment("INSERT INTO study.SpecimenComment ");
        insertPlaceholderQCComments.append("(GlobalUniqueId, Container, QualityControlFlag, QualityControlFlagForced, Created, CreatedBy, Modified, ModifiedBy) ");
        insertPlaceholderQCComments.append("SELECT GlobalUniqueId, ?, ?, ?, ?, ?, ?, ? ");
        insertPlaceholderQCComments.add(container.getId());
        insertPlaceholderQCComments.add(Boolean.TRUE);
        insertPlaceholderQCComments.add(Boolean.FALSE);
        insertPlaceholderQCComments.add(new Date());
        insertPlaceholderQCComments.add(user.getUserId());
        insertPlaceholderQCComments.add(new Date());
        insertPlaceholderQCComments.add(user.getUserId());
        insertPlaceholderQCComments.append(" FROM (\n").append(conflictedGUIDs).append(") ConflictedVials\n");
        insertPlaceholderQCComments.append("WHERE GlobalUniqueId NOT IN ");
        insertPlaceholderQCComments.append("(SELECT GlobalUniqueId FROM study.SpecimenComment WHERE Container = ?);");
        insertPlaceholderQCComments.add(container.getId());
        logger.info("Setting QC flags for vials that have new history conflicts...");
        Table.execute(StudySchema.getInstance().getSchema(), insertPlaceholderQCComments);
        logger.info("Complete.");
    }

    private static void markOrphanedRequestVials(Container container, User user, Logger logger) throws SQLException
    {
        // Mark those global unique IDs that are in requests but are no longer found in the vial table:
        SQLFragment orphanMarkerSql = new SQLFragment("UPDATE study.SampleRequestSpecimen SET Orphaned = ? WHERE RowId IN (\n" +
                "\tSELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen\n" +
                "\tLEFT OUTER JOIN study.Vial ON\n" +
                "\t\tstudy.Vial.GlobalUniqueId = study.SampleRequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\t\tstudy.Vial.Container = study.SampleRequestSpecimen.Container\n" +
                "\tWHERE study.Vial.GlobalUniqueId IS NULL AND\n" +
                "\t\tstudy.SampleRequestSpecimen.Container = ?);", Boolean.TRUE, container.getId());
        logger.info("Marking requested vials that have been orphaned...");
        Table.execute(StudySchema.getInstance().getSchema(), orphanMarkerSql);
        logger.info("Complete.");

        // un-mark those global unique IDs that were previously marked as orphaned but are now found in the vial table:
        SQLFragment deorphanMarkerSql = new SQLFragment("UPDATE study.SampleRequestSpecimen SET Orphaned = ? WHERE RowId IN (\n" +
                "\tSELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen\n" +
                "\tLEFT OUTER JOIN study.Vial ON\n" +
                "\t\tstudy.Vial.GlobalUniqueId = study.SampleRequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\t\tstudy.Vial.Container = study.SampleRequestSpecimen.Container\n" +
                "\tWHERE study.Vial.GlobalUniqueId IS NOT NULL AND\n" +
                "\t\tstudy.SampleRequestSpecimen.Orphaned = ? AND\n" +
                "\t\tstudy.SampleRequestSpecimen.Container = ?);", Boolean.FALSE, Boolean.TRUE, container.getId());
        logger.info("Marking requested vials that have been de-orphaned...");
        Table.execute(StudySchema.getInstance().getSchema(), deorphanMarkerSql);
        logger.info("Complete.");

    }

    private static void setLockedInRequestStatus(Container container, User user, Logger logger) throws SQLException
    {
        SQLFragment lockedInRequestSql = new SQLFragment("UPDATE " + StudySchema.getInstance().getTableInfoVial() +
                " SET LockedInRequest = ? WHERE RowId IN (SELECT study.Vial.RowId FROM study.Vial, study.LockedSpecimens " +
                "WHERE study.Vial.Container = ? AND study.LockedSpecimens.Container = ? AND " +
                "study.Vial.GlobalUniqueId = study.LockedSpecimens.GlobalUniqueId)");

        lockedInRequestSql.add(Boolean.TRUE);
        lockedInRequestSql.add(container.getId());
        lockedInRequestSql.add(container.getId());

        logger.info("Setting Specimen Locked in Request status...");
        Table.execute(StudySchema.getInstance().getSchema(), lockedInRequestSql);
        logger.info("Complete.");
    }

    private static void updateSpecimenProcessingInfo(Container container, Logger logger) throws SQLException
    {
        SQLFragment sql = new SQLFragment("UPDATE study.Specimen SET ProcessingLocation = (\n" +
                "\tSELECT MAX(ProcessingLocation) AS ProcessingLocation FROM \n" +
                "\t\t(SELECT DISTINCT SpecimenId, ProcessingLocation FROM study.Vial WHERE SpecimenId = study.Specimen.RowId AND Container = ?) Locations\n" +
                "\tGROUP BY SpecimenId\n" +
                "\tHAVING COUNT(ProcessingLocation) = 1\n" +
                ") WHERE Container = ?");
        sql.add(container.getId());
        sql.add(container.getId());
        logger.info("Updating processing locations on the specimen table...");
        Table.execute(StudySchema.getInstance().getSchema(), sql);
        logger.info("Complete.");

        sql = new SQLFragment("UPDATE study.Specimen SET FirstProcessedByInitials = (\n" +
                "\tSELECT MAX(FirstProcessedByInitials) AS FirstProcessedByInitials FROM \n" +
                "\t\t(SELECT DISTINCT SpecimenId, FirstProcessedByInitials FROM study.Vial WHERE SpecimenId = study.Specimen.RowId AND Container = ?) Locations\n" +
                "\tGROUP BY SpecimenId\n" +
                "\tHAVING COUNT(FirstProcessedByInitials) = 1\n" +
                ") WHERE Container = ?");
        sql.add(container.getId());
        sql.add(container.getId());
        logger.info("Updating first processed by initials on the specimen table...");
        Table.execute(StudySchema.getInstance().getSchema(), sql);
        logger.info("Complete.");

    }

    // UNDONE: add vials in-clause to only update data for rows that changed
    private static void updateCalculatedSpecimenData(Container container, User user, Logger logger) throws SQLException
    {
        // delete unnecessary comments and create placeholders for newly discovered errors:
        prepareQCComments(container, user, logger);

        updateCommentSpecimenHashes(container, logger);

        markOrphanedRequestVials(container, user, logger);
        setLockedInRequestStatus(container, user, logger);

        // clear caches before determining current sites:
        SimpleFilter containerFilter = new SimpleFilter("Container", container.getId());
        SampleManager.getInstance().clearCaches(container);
        Specimen[] specimens;
        int offset = 0;
        Map<Integer, SiteImpl> siteMap = new HashMap<Integer, SiteImpl>();
        String vialPropertiesSql = "UPDATE " + StudySchema.getInstance().getTableInfoVial() +
                " SET CurrentLocation = CAST(? AS INTEGER), ProcessingLocation = CAST(? AS INTEGER), " +
                "FirstProcessedByInitials = ?, AtRepository = ? WHERE RowId = ?";
        String commentSql = "UPDATE " + StudySchema.getInstance().getTableInfoSpecimenComment() +
                " SET QualityControlComments = ? WHERE GlobalUniqueId = ?";
        do
        {
            if (logger != null)
                logger.info("Determining current locations for vials " + (offset + 1) + " through " + (offset + CURRENT_SITE_UPDATE_SIZE) + ".");
            specimens = Table.select(StudySchema.getInstance().getTableInfoVial(), Table.ALL_COLUMNS,
                    containerFilter, null, Specimen.class, CURRENT_SITE_UPDATE_SIZE, offset);
            List<List<?>> vialPropertiesParams = new ArrayList<List<?>>();
            List<List<?>> commentParams = new ArrayList<List<?>>();

            Map<Specimen, List<SpecimenEvent>> specimenToOrderedEvents = SampleManager.getInstance().getDateOrderedEventLists(specimens);
            Map<Specimen, SpecimenComment> specimenComments = SampleManager.getInstance().getSpecimenComments(specimens);

            for (Map.Entry<Specimen, List<SpecimenEvent>> entry : specimenToOrderedEvents.entrySet())
            {
                Specimen specimen = entry.getKey();
                List<SpecimenEvent> dateOrderedEvents = entry.getValue();
                Integer processingLocation = SampleManager.getInstance().getProcessingSiteId(dateOrderedEvents);
                String firstProcessedByInitials = SampleManager.getInstance().getFirstProcessedByInitials(dateOrderedEvents);
                Integer currentLocation = SampleManager.getInstance().getCurrentSiteId(dateOrderedEvents);
                boolean atRepository = false;
                if (currentLocation != null)
                {
                    SiteImpl site;
                    if (!siteMap.containsKey(currentLocation))
                    {
                        site = StudyManager.getInstance().getSite(specimen.getContainer(), currentLocation.intValue());
                        if (site != null)
                            siteMap.put(currentLocation, site);
                    }
                    else
                        site = siteMap.get(currentLocation);

                    if (site != null)
                        atRepository = site.isRepository() != null && site.isRepository().booleanValue();
                }

                if (!safeIntegerEqual(currentLocation, specimen.getCurrentLocation()) ||
                    !safeIntegerEqual(processingLocation, specimen.getProcessingLocation()) ||
                    !safeObjectEquals(firstProcessedByInitials, specimen.getFirstProcessedByInitials()) ||
                    atRepository != specimen.isAtRepository())
                {
                    List<Object> params = new ArrayList<Object>();
                    params.add(currentLocation);
                    params.add(processingLocation);
                    params.add(firstProcessedByInitials);
                    params.add(atRepository);
                    params.add(specimen.getRowId());
                    vialPropertiesParams.add(params);
                }

                SpecimenComment comment = specimenComments.get(specimen);
                if (comment != null)
                {
                    // if we have a comment, it may be because we're in a bad QC state.  If so, we should update
                    // the reason for the QC problem.
                    String message = null;
                    if (comment.isQualityControlFlag() || comment.isQualityControlFlagForced())
                    {
                        SpecimenEvent[] events = SampleManager.getInstance().getSpecimenEvents(specimen);
                        Set<String> conflicts = getConflictingEventColumns(events);
                        if (!conflicts.isEmpty())
                        {
                            String sep = "";
                            message = "Conflicts found: ";
                            for (String conflict : conflicts)
                            {
                                message += sep + conflict;
                                sep = ", ";
                            }
                        }
                        commentParams.add(Arrays.asList(message, specimen.getGlobalUniqueId()));
                    }
                }
            }
            if (!vialPropertiesParams.isEmpty())
                Table.batchExecute(StudySchema.getInstance().getSchema(), vialPropertiesSql, vialPropertiesParams);
            if (!commentParams.isEmpty())
                Table.batchExecute(StudySchema.getInstance().getSchema(), commentSql, commentParams);
            offset += CURRENT_SITE_UPDATE_SIZE;
        }
        while (specimens.length > 0);

        // finally, after all other data has been updated, we can update our cached specimen counts and processing locations:
        updateSpecimenProcessingInfo(container, logger);

        try
        {
            RequestabilityManager.getInstance().updateRequestability(container, user, false, logger);
        }
        catch (RequestabilityManager.InvalidRuleException e)
        {
            throw new IllegalStateException("One or more requestability rules is invalid.  Please remove or correct the invalid rule.", e);
        }
        if (logger != null)
            logger.info("Updating cached vial counts...");
        SampleManager.getInstance().updateSpecimenCounts(container, user);
        if (logger != null)
            logger.info("Vial count update complete.");
    }
    
    private Map<String, File> createFilemap(List<File> files) throws IOException
    {
        Map<String, File> fileMap = new HashMap<String, File>(files.size());

        for (File file : files)
        {
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                line = line.trim();
                if (line.charAt(0) != '#')
                    throw new IllegalStateException("Import files are expected to start with a comment indicating table name");
                fileMap.put(line.substring(1).trim().toLowerCase(), file);
            }
            finally
            {
                if (reader != null) try { reader.close(); } catch (IOException e) {}
            }
        }
        return fileMap;
    }

    private void info(String message)
    {
        if (_logger != null)
            _logger.info(message);
    }

    private List<SpecimenColumn> getSpecimenCols(List<SpecimenColumn> availableColumns)
    {
        if (_specimenCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<SpecimenColumn>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isSpecimens())
                    cols.add(col);
            }
            _specimenCols = cols;
        }
        return _specimenCols;
    }

    private String getSpecimenColsSql(List<SpecimenColumn> availableColumns)
    {
        if (_specimenColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getSpecimenCols(availableColumns))
            {
                cols.append(sep).append(col.getDbColumnName());
                sep = ",\n   ";
            }
            _specimenColsSql = cols.toString();
        }
        return _specimenColsSql;
    }

    private List<SpecimenColumn> getVialCols(List<SpecimenColumn> availableColumns)
    {
        if (_vialCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<SpecimenColumn>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isVials())
                    cols.add(col);
            }
            _vialCols = cols;
        }
        return _vialCols;
    }

    private String getVialColsSql(List<SpecimenColumn> availableColumns)
    {
        if (_vialColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getVialCols(availableColumns))
            {
                cols.append(sep).append(col.getDbColumnName());
                sep = ",\n   ";
            }
            _vialColsSql = cols.toString();
        }
        return _vialColsSql;
    }

    private List<SpecimenColumn> getSpecimenEventCols(List<SpecimenColumn> availableColumns)
    {
        if (_vialEventCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<SpecimenColumn>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isEvents())
                    cols.add(col);
            }
            _vialEventCols = cols;
        }
        return _vialEventCols;
    }

    private String getSpecimenEventColsSql(List<SpecimenColumn> availableColumns)
    {
        if (_vialEventColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getSpecimenEventCols(availableColumns))
            {
                cols.append(sep).append(col.getDbColumnName());
                sep = ",\n    ";
            }
            _vialEventColsSql = cols.toString();
        }
        return _vialEventColsSql;
    }

    private void populateMaterials(SpecimenLoadInfo info, boolean merge) throws SQLException
    {
        assert cpuPopulateMaterials.start();

        String columnName = null;
        for (SpecimenColumn specimenColumn : info.getAvailableColumns())
        {
            if (GLOBAL_UNIQUE_ID_TSV_COL.equals(specimenColumn.getTsvColumnName()))
            {
                columnName = specimenColumn.getDbColumnName();
                break;
            }
        }
        if (columnName == null)
        {
            for (SpecimenColumn specimenColumn : info.getAvailableColumns())
            {
                if (SPEC_NUMBER_TSV_COL.equals(specimenColumn.getTsvColumnName()))
                {
                    columnName = specimenColumn.getDbColumnName();
                    break;
                }
            }
        }
        if (columnName == null)
        {
            throw new IllegalStateException("Could not find a unique specimen identifier column.  Either \"" + GLOBAL_UNIQUE_ID_TSV_COL
            + "\" or \"" + SPEC_NUMBER_TSV_COL + "\" must be present in the set of specimen columns.");
        }

        String insertSQL = "INSERT INTO exp.Material (LSID, Name, Container, CpasType, Created)  \n" +
                "SELECT " + info.getTempTableName() + ".LSID, " + info.getTempTableName() + "." + columnName +
                ", ?, ?, ? FROM " + info.getTempTableName() + "\nLEFT OUTER JOIN exp.Material ON\n" +
                info.getTempTableName() + ".LSID = exp.Material.LSID WHERE exp.Material.RowId IS NULL\n" +
                "GROUP BY " + info.getTempTableName() + ".LSID, " + info.getTempTableName() + "." + columnName;

        String deleteSQL = "DELETE FROM exp.Material WHERE RowId IN (SELECT exp.Material.RowId FROM exp.Material \n" +
                "LEFT OUTER JOIN " + info.getTempTableName() + " ON\n" +
                "\texp.Material.LSID = " + info.getTempTableName() + ".LSID\n" +
                "LEFT OUTER JOIN exp.MaterialInput ON\n" +
                "\texp.Material.RowId = exp.MaterialInput.MaterialId\n" +
                "WHERE " + info.getTempTableName() + ".LSID IS NULL\n" +
                "AND exp.MaterialInput.MaterialId IS NULL\n" +
                "AND (exp.Material.CpasType = ? OR exp.Material.CpasType = 'StudySpecimen') \n" +
                "AND exp.Material.Container = ?)";

        String prefix = new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + info.getContainer().getRowId(), "").toString();

        String cpasType;

        String name = "Study Specimens";
        ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(info.getContainer(), name);
        if (sampleSet == null)
        {
            ExpSampleSet source = ExperimentService.get().createSampleSet();
            source.setContainer(info.getContainer());
            source.setMaterialLSIDPrefix(prefix);
            source.setName(name);
            source.setLSID(ExperimentService.get().getSampleSetLsid(name, info.getContainer()).toString());
            source.setDescription("Study specimens for " + info.getContainer().getPath());
            source.save(null);
            cpasType = source.getLSID();
        }
        else
        {
            cpasType = sampleSet.getLSID();
        }

        Timestamp createdTimestamp = new Timestamp(System.currentTimeMillis());

        try
        {
            int affected;
            if (!merge)
            {
                info("exp.Material: Deleting entries for removed specimens...");
                SQLFragment deleteFragment = new SQLFragment(deleteSQL, cpasType, info.getContainer().getId());
                if (DEBUG)
                    logSQLFragment(deleteFragment);
                affected = Table.execute(info.getSchema(), deleteFragment);
                if (affected >= 0)
                    info("exp.Material: " + affected + " rows removed.");
            }

            // NOTE: No need to update existing Materials when merging -- just insert any new materials not found.
            info("exp.Material: Inserting new entries from temp table...");
            SQLFragment insertFragment = new SQLFragment(insertSQL, info.getContainer().getId(), cpasType, createdTimestamp);
            if (DEBUG)
                logSQLFragment(insertFragment);
            affected = Table.execute(info.getSchema(), insertFragment);
            if (affected >= 0)
                info("exp.Material: " + affected + " rows inserted.");
            info("exp.Material: Update complete.");
        }
        finally
        {
            assert cpuPopulateMaterials.stop();
        }
    }

    private String getSpecimenEventTempTableColumns(SpecimenLoadInfo info)
    {
        StringBuilder columnList = new StringBuilder();
        String prefix = "";
        for (SpecimenColumn col : getSpecimenEventCols(info.getAvailableColumns()))
        {
            columnList.append(prefix);
            prefix = ", ";
            columnList.append("\n    ").append(info.getTempTableName()).append(".").append(col.getDbColumnName());
        }
        return columnList.toString();
    }

    private void appendConflictResolvingSQL(SqlDialect dialect, SQLFragment sql, SpecimenColumn col, String tempTableName)
    {
        String selectCol = tempTableName + "." + col.getDbColumnName();

        if (col.getAggregateEventFunction() != null)
            sql.append(col.getAggregateEventFunction()).append("(").append(selectCol).append(")");
        else
        {
            String singletonAggregate;
            if (col.getJavaType().equals(Boolean.class))
            {
                // gross nested calls to cast the boolean to an int, get its min, then cast back to a boolean.
                // this is needed because most aggregates don't work on boolean values.
                singletonAggregate = "CAST(MIN(CAST(" + selectCol + " AS INTEGER)) AS " + dialect.getBooleanDataType()  + ")";
            }
            else
            {
                singletonAggregate = "MIN(" + selectCol + ")";
            }
            sql.append("CASE WHEN");
            sql.append(" COUNT(DISTINCT(").append(selectCol).append(")) = 1 THEN ");
            sql.append(singletonAggregate);
            sql.append(" ELSE NULL END");
        }
        sql.append(" AS ").append(col.getDbColumnName());
    }


    private void populateSpecimens(SpecimenLoadInfo info, boolean merge) throws IOException, SQLException
    {
        String participantSequenceKeyExpr = VisitManager.getParticipantSequenceKeyExpr(info._schema, "PTID", "VisitValue");

        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT ");
        insertSelectSql.append(participantSequenceKeyExpr).append(" AS ParticipantSequenceKey");
        insertSelectSql.append(", Container, SpecimenHash, ");
        insertSelectSql.append(getSpecimenColsSql(info.getAvailableColumns())).append(" FROM (\n");
        insertSelectSql.append(getVialListFromTempTableSql(info)).append(") VialList\n");
        insertSelectSql.append("GROUP BY ").append("Container, SpecimenHash, ");
        insertSelectSql.append(getSpecimenColsSql(info.getAvailableColumns()));

        if (merge)
        {
            Table.TableResultSet rs = null;
            try
            {
                // Create list of specimen columns, including unique columns not found in SPECIMEN_COLUMNS.
                Set<SpecimenColumn> cols = new LinkedHashSet<SpecimenColumn>();
                cols.add(new SpecimenColumn("SpecimenHash", "SpecimenHash", "VARCHAR(256)", TargetTable.SPECIMENS, true));
                cols.add(new SpecimenColumn("ParticipantSequenceKey", "ParticipantSequenceKey", "VARCHAR(200)", TargetTable.SPECIMENS, false));
                cols.addAll(getSpecimenCols(info.getAvailableColumns()));

                // Insert or update the specimens from in the temp table.
                rs = Table.executeQuery(info.getSchema(), insertSelectSql);
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                mergeTable(info.getSchema(), info.getContainer(), "study.Specimen", cols, rs, false);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException _) { } }
            }
        }
        else
        {
            // Insert all specimens from in the temp table.
            SQLFragment insertSql = new SQLFragment();
            insertSql.append("INSERT INTO study.Specimen \n(").append("ParticipantSequenceKey, Container, SpecimenHash, ");
            insertSql.append(getSpecimenColsSql(info.getAvailableColumns())).append(")\n");
            insertSql.append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            assert cpuInsertSpecimens.start();
            info("Specimens: Inserting new rows...");
            Table.execute(info.getSchema(), insertSql);
            info("Specimens: Insert complete.");
            assert cpuInsertSpecimens.stop();
        }
    }

    private SQLFragment getVialListFromTempTableSql(SpecimenLoadInfo info)
    {
        String prefix = ",\n    ";
        SQLFragment vialListSql = new SQLFragment();
        vialListSql.append("SELECT ").append(info.getTempTableName()).append(".LSID AS LSID");
        vialListSql.append(prefix).append("SpecimenHash");
        vialListSql.append(prefix).append("? AS Container");
        vialListSql.add(info.getContainer().getId());
        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (col.getTargetTable().isVials() || col.getTargetTable().isSpecimens())
            {
                vialListSql.append(prefix);
                appendConflictResolvingSQL(info.getSchema().getSqlDialect(), vialListSql, col, info.getTempTableName());
            }
        }
        vialListSql.append("\nFROM ").append(info.getTempTableName());
        vialListSql.append("\nGROUP BY\n");
        vialListSql.append(info.getTempTableName()).append(".LSID,\n    ");
        vialListSql.append(info.getTempTableName()).append(".Container,\n    ");
        vialListSql.append(info.getTempTableName()).append(".SpecimenHash,\n    ");
        vialListSql.append(info.getTempTableName()).append(".GlobalUniqueId");
        return vialListSql;
    }

    private void populateVials(SpecimenLoadInfo info, boolean merge) throws SQLException
    {
        String prefix = ",\n    ";
        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT exp.Material.RowId");
        insertSelectSql.append(prefix).append("study.Specimen.RowId AS SpecimenId");
        insertSelectSql.append(prefix).append("study.Specimen.SpecimenHash");
        insertSelectSql.append(prefix).append("VialList.Container");
        insertSelectSql.append(prefix).append("? AS Available");
        // Set a default value of true for the 'Available' column:
        insertSelectSql.add(Boolean.TRUE);

        for (SpecimenColumn col : getVialCols(info.getAvailableColumns()))
            insertSelectSql.append(prefix).append("VialList.").append(col.getDbColumnName());

        insertSelectSql.append(" FROM (").append(getVialListFromTempTableSql(info)).append(") VialList");

        // join to material:
        insertSelectSql.append("\n    JOIN exp.Material ON (");
        insertSelectSql.append("VialList.LSID = exp.Material.LSID");
        insertSelectSql.append(" AND exp.Material.Container = ?)");
        insertSelectSql.add(info.getContainer().getId());

        // join to specimen:
        insertSelectSql.append("\n    JOIN study.Specimen ON study.Specimen.Container = ? ");
        insertSelectSql.add(info.getContainer().getId());
        insertSelectSql.append("AND study.Specimen.SpecimenHash = VialList.SpecimenHash");


        if (merge)
        {
            Table.TableResultSet rs = null;
            try
            {
                // Create list of vial columns, including unique columns not found in SPECIMEN_COLUMNS.
                Set<SpecimenColumn> cols = new LinkedHashSet<SpecimenColumn>();
                // NOTE: study.Vial.RowId is actually an FK to exp.Material.RowId
                cols.add(GLOBAL_UNIQUE_ID);
                cols.add(new SpecimenColumn("RowId", "RowId", "INT NOT NULL", TargetTable.VIALS, false));
                cols.add(new SpecimenColumn("SpecimenId", "SpecimenId", "INT NOT NULL", TargetTable.VIALS, false));
                cols.add(new SpecimenColumn("SpecimenHash", "SpecimenHash", "VARCHAR(256)", TargetTable.VIALS, false));
                cols.add(new SpecimenColumn("Available", "Available", BOOLEAN_TYPE, TargetTable.VIALS, false));
                cols.addAll(getVialCols(info.getAvailableColumns()));

                // Insert or update the vials from in the temp table.
                rs = Table.executeQuery(info.getSchema(), insertSelectSql);
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                mergeTable(info.getSchema(), info.getContainer(), "study.Vial", cols, rs, false);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException _) { } }
            }
        }
        else
        {
            // Insert all vials from in the temp table.
            SQLFragment insertSql = new SQLFragment();
            insertSql.append("INSERT INTO study.Vial \n(RowId, SpecimenId, SpecimenHash, Container, Available, ");
            insertSql.append(getVialColsSql(info.getAvailableColumns())).append(")\n");
            insertSql.append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            assert cpuInsertSpecimens.start();
            info("Vials: Inserting new rows...");
            Table.execute(info.getSchema(), insertSql);
            info("Vials: Insert complete.");
            assert cpuInsertSpecimens.stop();
        }
    }

    private void logSQLFragment(SQLFragment sql)
    {
        info(sql.getSQL());
        info("Params: ");
        for (Object param : sql.getParams())
            info(param.toString());
    }

    private void populateVialEvents(SpecimenLoadInfo info, boolean merge) throws SQLException
    {
        SQLFragment insertSelectSql = new SQLFragment();
        insertSelectSql.append("SELECT study.Vial.Container, study.Vial.RowId AS VialId, \n");
        insertSelectSql.append(getSpecimenEventTempTableColumns(info));
        insertSelectSql.append(" FROM ");
        insertSelectSql.append(info.getTempTableName()).append("\nJOIN study.Vial ON ");
        insertSelectSql.append(info.getTempTableName()).append(".GlobalUniqueId = study.Vial.GlobalUniqueId AND study.Vial.Container = ?");
        insertSelectSql.add(info.getContainer().getId());

        if (merge)
        {
            Table.TableResultSet rs = null;
            try
            {
                // Create list of vial columns, including unique columns not found in SPECIMEN_COLUMNS.
                // Events are special in that we want to merge based on a pseudo-unique set of columns:
                //    Container, VialId (vial.GlobalUniqueId), LabId, StorageDate, ShipDate, LabReceiptDate
                // We need to always add these extra columns, even if they aren't in the list of available columns.
                Set<SpecimenColumn> cols = new LinkedHashSet<SpecimenColumn>();
                cols.add(new SpecimenColumn("VialId", "VialId", "INT NOT NULL", TargetTable.SPECIMEN_EVENTS, true));
                cols.add(LAB_ID);
                cols.add(SHIP_DATE);
                cols.add(STORAGE_DATE);
                cols.add(LAB_RECEIPT_DATE);
                for (SpecimenColumn col : getSpecimenEventCols(info.getAvailableColumns()))
                {
                    cols.add(col);
                }

                // Insert or update the vials from in the temp table.
                rs = Table.executeQuery(info.getSchema(), insertSelectSql);
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                mergeTable(info.getSchema(), info.getContainer(), "study.SpecimenEvent", cols, rs, false);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException _) { } }
            }
        }
        else
        {
            // Insert all events from the temp table
            SQLFragment insertSql = new SQLFragment();
            insertSql.append("INSERT INTO study.SpecimenEvent\n");
            insertSql.append("(Container, VialId, " + getSpecimenEventColsSql(info.getAvailableColumns()) + ")\n");
            insertSql.append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);
            assert cpuInsertSpecimenEvents.start();
            info("Specimen Events: Inserting new rows.");
            Table.execute(info.getSchema(), insertSql);
            info("Specimen Events: Insert complete.");
            assert cpuInsertSpecimenEvents.stop();
        }
    }

    private interface ComputedColumn
    {
        String getName();
        Object getValue(Map<String, Object> row);
    }

    private class EntityIdComputedColumn implements ComputedColumn
    {
        public String getName() { return "EntityId"; }
        public Object getValue(Map<String, Object> row) { return GUID.makeGUID(); }
    }

    private <T extends ImportableColumn> Pair<List<T>, Integer> mergeTable(
            DbSchema schema, Container container, String tableName,
            Collection<T> potentialColumns, Iterable<Map<String, Object>> values, boolean addEntityId)
        throws SQLException
    {
        ComputedColumn entityIdCol = null;
        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        return mergeTable(schema, container, tableName, potentialColumns, values, entityIdCol);
    }

    private void appendEqualCheck(DbSchema schema, StringBuilder sql, ImportableColumn col)
    {
        String dialectType = schema.getSqlDialect().sqlTypeNameFromSqlType(col.getSQLType().sqlType);
        String paramCast = "CAST(? AS " + dialectType + ")";
        // Each unique col has two parameters in the null-equals check.
        sql.append("(").append(col.getDbColumnName()).append(" IS NULL AND ").append(paramCast).append(" IS NULL)");
        sql.append(" OR ").append(col.getDbColumnName()).append(" = ").append(paramCast);
    }

    /**
     * Insert or update rows on the target table using the unique columns of <code>potentialColumns</code>
     * to identify the existing row.
     *
     * NOTE: The idCol is used only during insert -- the value won't be updated if the row already exists.
     *
     * @param schema The dbschema.
     * @param container The container.
     * @param tableName Fully qualified table name, e.g., "study.Vials"
     * @param potentialColumns List of columns to be inserted/updated on the table.
     * @param values The data values to be inserted or updated.
     * @param idCol The computed column.
     * @return A pair of the columns actually found in the data values and a total row count.
     * @throws SQLException
     */
    private <T extends ImportableColumn> Pair<List<T>, Integer> mergeTable(
            DbSchema schema, Container container, String tableName,
            Collection<T> potentialColumns, Iterable<Map<String, Object>> values,
            ComputedColumn idCol)
        throws SQLException
    {
        if (values == null)
        {
            info(tableName + ": No rows to merge");
            return new Pair<List<T>, Integer>(Collections.<T>emptyList(), 0);
        }
        Iterator<Map<String, Object>> iter = values.iterator();

        assert cpuMergeTable.start();
        info(tableName + ": Starting merge of data...");

        List<T> availableColumns = new ArrayList<T>();
        List<T> uniqueCols = new ArrayList<T>();

        StringBuilder selectSql = new StringBuilder();
        StringBuilder insertSql = new StringBuilder();
        List<Parameter> parametersInsert = new ArrayList<Parameter>();
        Parameter.ParameterMap parameterMapInsert = null;
        PreparedStatement stmtInsert = null;

        StringBuilder updateSql = new StringBuilder();
        List<Parameter> parametersUpdate = new ArrayList<Parameter>();
        Parameter.ParameterMap parameterMapUpdate = null;
        PreparedStatement stmtUpdate = null;

        int rowCount = 0;
        Connection conn = null;

        try
        {
            conn = schema.getScope().getConnection();
            int rowsAdded = 0;
            int rowsUpdated = 0;

            while (iter.hasNext())
            {
                Map<String, Object> row = iter.next();
                rowCount++;

                if (1 == rowCount)
                {
                    for (T column : potentialColumns)
                    {
                        if (row.containsKey(column.getTsvColumnName()) || row.containsKey(column.getDbColumnName()))
                            availableColumns.add(column);
                    }

                    for (T col : availableColumns)
                    {
                        if (col.isUnique())
                            uniqueCols.add(col);
                    }

                    selectSql.append("SELECT * FROM ").append(tableName).append(" WHERE Container = ? ");
                    for (ImportableColumn col : uniqueCols)
                    {
                        selectSql.append(" AND (");
                        appendEqualCheck(schema, selectSql, col);
                        selectSql.append(")\n");
                    }
                    if (DEBUG)
                    {
                        info(tableName + ": select sql:");
                        info(selectSql.toString());
                    }

                    int p = 1;
                    insertSql.append("INSERT INTO ").append(tableName).append(" (Container");
                    parametersInsert.add(new Parameter("Container", p++, JdbcType.VARCHAR));
                    if (idCol != null)
                    {
                        insertSql.append(", ").append(idCol.getName());
                        parametersInsert.add(new Parameter(idCol.getName(), p++, JdbcType.VARCHAR));
                    }
                    for (ImportableColumn col : availableColumns)
                    {
                        insertSql.append(", ").append(col.getDbColumnName());
                        parametersInsert.add(new Parameter(col.getDbColumnName(), p++, col.getSQLType()));
                    }
                    insertSql.append(") VALUES (?");
                    if (idCol != null)
                        insertSql.append(", ?");
                    insertSql.append(StringUtils.repeat(", ?", availableColumns.size()));
                    insertSql.append(")");
                    stmtInsert = conn.prepareStatement(insertSql.toString());
                    parameterMapInsert = new Parameter.ParameterMap(stmtInsert, parametersInsert);
                    if (DEBUG)
                    {
                        info(tableName + ": insert sql:");
                        info(insertSql.toString());
                    }

                    p = 1;
                    updateSql.append("UPDATE ").append(tableName).append(" SET ");
                    String separator = "";
                    for (ImportableColumn col : availableColumns)
                    {
                        if (!col.isUnique())
                        {
                            updateSql.append(separator).append(col.getDbColumnName()).append(" = ?");
                            separator = ", ";
                            parametersUpdate.add(new Parameter(col.getDbColumnName(), p++, col.getSQLType()));
                        }
                    }
                    updateSql.append(" WHERE Container = ?\n");
                    parametersUpdate.add(new Parameter("Container", p++, JdbcType.VARCHAR));
                    for (ImportableColumn col : availableColumns)
                    {
                        if (col.isUnique())
                        {
                            updateSql.append(" AND (");
                            appendEqualCheck(schema, updateSql, col);
                            updateSql.append(")\n");
                            parametersUpdate.add(new Parameter(col.getDbColumnName(), new int[] { p++, p++ }, col.getSQLType()));
                        }
                    }
                    stmtUpdate = conn.prepareStatement(updateSql.toString());
                    parameterMapUpdate = new Parameter.ParameterMap(stmtUpdate, parametersUpdate);
                    if (DEBUG)
                    {
                        info(tableName + ": update sql:");
                        info(updateSql.toString());
                    }
                }

                boolean rowExists = false;
                if (!uniqueCols.isEmpty())
                {
                    ResultSet rs = null;
                    try
                    {
                        Object[] params = new Object[(2*uniqueCols.size()) + 1];
                        int colIndex = 0;
                        params[colIndex++] = container.getId();
                        for (ImportableColumn col : uniqueCols)
                        {
                            // Each unique col has two parameters in the null-equals check.
                            Object value = getValueParameter(col, row);
                            params[colIndex++] = value;
                            params[colIndex++] = value;
                        }

                        rs = Table.executeQuery(schema, selectSql.toString(), params);
                        if (rs.next())
                            rowExists = true;
                        if (VERBOSE_DEBUG)
                            info((rowExists ? "Row exists" : "Row does NOT exist") + " matching:\n" + JdbcUtil.format(selectSql.toString(), Arrays.asList(params)));
                    }
                    finally
                    {
                        if (rs != null) try { rs.close(); } catch (SQLException e) {}
                    }
                }

                if (!rowExists)
                {
                    parameterMapInsert.clearParameters();
                    parameterMapInsert.put("Container", container.getId());
                    if (idCol != null)
                        parameterMapInsert.put(idCol.getName(), idCol.getValue(row));
                    for (ImportableColumn col : availableColumns)
                        parameterMapInsert.put(col.getDbColumnName(), getValueParameter(col, row));
                    if (VERBOSE_DEBUG)
                        info(stmtInsert.toString());
                    stmtInsert.execute();
                    rowsAdded++;
                }
                else
                {
                    parameterMapUpdate.clearParameters();
                    for (ImportableColumn col : availableColumns)
                    {
                        Object value = getValueParameter(col, row);
                        parameterMapUpdate.put(col.getDbColumnName(), value);
                    }
                    parameterMapUpdate.put("Container", container.getId());
                    if (VERBOSE_DEBUG)
                        info(stmtUpdate.toString());
                    stmtUpdate.execute();
                    rowsUpdated++;
                }
            }

            info(tableName + ": inserted " + rowsAdded + " new rows, updated " + rowsUpdated + " rows.  (" + rowCount + " rows found in input file.)");
        }
        finally
        {
            if (iter instanceof CloseableIterator) try { ((CloseableIterator)iter).close(); } catch (IOException ioe) { }
            if (null != conn)
                schema.getScope().releaseConnection(conn);    
        }
        assert cpuMergeTable.stop();

        return new Pair<List<T>, Integer>(availableColumns, rowCount);
    }


    private <T extends ImportableColumn> Pair<List<T>, Integer> replaceTable(
            DbSchema schema, Container container, String tableName,
            Collection<T> potentialColumns, Iterable<Map<String, Object>> values, boolean addEntityId)
        throws IOException, SQLException
    {
        ComputedColumn entityIdCol = null;
        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        return replaceTable(schema, container, tableName, potentialColumns, values, entityIdCol);
    }

    /**
     * Deletes the target table and inserts new rows.
     *
     * @param schema The dbschema.
     * @param container The container.
     * @param tableName Fully qualified table name, e.g., "study.Vials"
     * @param potentialColumns List of columns to be inserted/updated on the table.
     * @param values The data values to be inserted or updated.
     * @param idCol The computed column.
     * @return A pair of the columns actually found in the data values and a total row count.
     * @throws SQLException
     */
    private <T extends ImportableColumn> Pair<List<T>, Integer> replaceTable(
            DbSchema schema, Container container, String tableName,
            Collection<T> potentialColumns, Iterable<Map<String, Object>> values,
            ComputedColumn idCol)
        throws IOException, SQLException
    {
        if (values == null)
        {
            info(tableName + ": No rows to replace");
            return new Pair<List<T>, Integer>(Collections.<T>emptyList(), 0);
        }
        Iterator<Map<String, Object>> iter = values.iterator();

        assert cpuMergeTable.start();
        info(tableName + ": Starting replacement of all data...");

        Table.execute(schema, "DELETE FROM " + tableName + " WHERE Container = ?", new Object[] { container.getId() });
        List<T> availableColumns = new ArrayList<T>();
        StringBuilder insertSql = new StringBuilder();

        List<List<Object>> rows = new ArrayList<List<Object>>();
        int rowCount = 0;

        try
        {
            while (iter.hasNext())
            {
                Map<String, Object> row = iter.next();
                rowCount++;

                if (1 == rowCount)
                {
                    for (T column : potentialColumns)
                    {
                        if (row.containsKey(column.getTsvColumnName()) || row.containsKey(column.getDbColumnName()))
                            availableColumns.add(column);
                    }

                    insertSql.append("INSERT INTO ").append(tableName).append(" (Container");
                    if (idCol != null)
                        insertSql.append(", ").append(idCol.getName());
                    for (ImportableColumn col : availableColumns)
                        insertSql.append(", ").append(col.getDbColumnName());
                    insertSql.append(") VALUES (?");
                    if (idCol != null)
                        insertSql.append(", ?");
                    insertSql.append(StringUtils.repeat(", ?", availableColumns.size()));
                    insertSql.append(")");

                    if (DEBUG)
                        info(insertSql.toString());
                }

                List<Object> params = new ArrayList<Object>(availableColumns.size() + 1 + (idCol != null ? 1 : 0));
                params.add(container.getId());
                if (idCol != null)
                    params.add(idCol.getValue(row));

                for (ImportableColumn col : availableColumns)
                {
                    Object value = getValueParameter(col, row);
                    params.add(value);
                }

                rows.add(params);

                if (rows.size() == SQL_BATCH_SIZE)
                {
                    Table.batchExecute(schema, insertSql.toString(), rows);
                    rows = new ArrayList<List<Object>>(SQL_BATCH_SIZE);
                    // output a message every 100 batches (every 10,000 events, by default)
                    if (rowCount % (SQL_BATCH_SIZE*100) == 0)
                        info(rowCount + " rows loaded...");
                }
            }

            // No point in trying to insert zero rows.  Also, insertSql won't be set if no rows exist.
            if (!rows.isEmpty())
                Table.batchExecute(schema, insertSql.toString(), rows);

            info(tableName + ": Replaced all data with " + rowCount + " new rows.");
        }
        finally
        {
            if (iter instanceof CloseableIterator) try { ((CloseableIterator)iter).close(); } catch (IOException ioe) { }
        }
        assert cpuMergeTable.stop();

        return new Pair<List<T>, Integer>(availableColumns, rowCount);
    }

    private Iterable<Map<String, Object>> loadTsv(Collection<? extends ImportableColumn> columns, File tsvFile, String tableName) throws IOException
    {
        if (tsvFile == null || !NetworkDrive.exists(tsvFile))
        {
            info(tableName + ": no data to merge.");
            return null;
        }

        info(tableName + ": Parsing data file for table...");
        Map<String, ColumnDescriptor> expectedColumns = new HashMap<String, ColumnDescriptor>(columns.size());
        for (ImportableColumn col : columns)
            expectedColumns.put(col.getTsvColumnName().toLowerCase(), col.getColumnDescriptor());

        TabLoader loader = new TabLoader(tsvFile, true);
        for (ColumnDescriptor column : loader.getColumns())
        {
            ColumnDescriptor expectedColumnDescriptor = expectedColumns.get(column.name.toLowerCase());
            if (expectedColumnDescriptor != null)
                column.clazz = expectedColumnDescriptor.clazz;
            else
                column.load = false;
        }
        info(tableName + ": Parsing complete.");
        return loader;
    }

    private Pair<List<SpecimenColumn>, Integer> populateTempTable(
            DbSchema schema, final Container container, String tempTable,
            Iterable<Map<String, Object>> iter)
        throws SQLException, IOException
    {
        assert cpuPopulateTempTable.start();

        info("Populating specimen temp table...");
        int rowCount;
        List<SpecimenColumn> loadedColumns;

        ComputedColumn lsidCol = new ComputedColumn()
        {
            public String getName() { return "LSID"; }
            public Object getValue(Map<String, Object> row)
            {
                String id = (String) row.get(GLOBAL_UNIQUE_ID_TSV_COL);
                if (id == null)
                    id = (String) row.get(SPEC_NUMBER_TSV_COL);

                Lsid lsid = SpecimenService.get().getSpecimenMaterialLsid(container, id);
                return lsid.toString();
            }
        };

        Pair<List<SpecimenColumn>, Integer> pair = replaceTable(schema, container, tempTable, SPECIMEN_COLUMNS, iter, lsidCol);

        loadedColumns = pair.first;
        rowCount = pair.second;

        if (rowCount == 0)
        {
            info("Found no specimen columns to import. Temp table will not be loaded.");
            return pair;
        }

        remapTempTableLookupIndexes(schema, container, tempTable, loadedColumns);

        updateTempTableVisits(schema, container, tempTable);

        updateTempTableSpecimenHash(schema, container, tempTable, loadedColumns);

        info("Specimen temp table populated.");
        return pair;
    }

    private void remapTempTableLookupIndexes(DbSchema schema, Container container, String tempTable, List<SpecimenColumn> loadedColumns)
            throws SQLException
    {
        String sep = "";
        SQLFragment innerTableSelectSql = new SQLFragment("SELECT " + tempTable + ".RowId AS RowId");
        SQLFragment innerTableJoinSql = new SQLFragment();
        SQLFragment remapExternalIdsSql = new SQLFragment("UPDATE ").append(tempTable).append(" SET ");
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getFkTable() != null)
            {
                remapExternalIdsSql.append(sep).append(col.getDbColumnName()).append(" = InnerTable.").append(col.getDbColumnName());

                innerTableSelectSql.append(",\n\t").append(col.getFkTableAlias()).append(".RowId AS ").append(col.getDbColumnName());

                innerTableJoinSql.append("\nLEFT OUTER JOIN study.").append(col.getFkTable()).append(" AS ").append(col.getFkTableAlias()).append(" ON ");
                innerTableJoinSql.append("(" + tempTable + ".");
                innerTableJoinSql.append(col.getDbColumnName()).append(" = ").append(col.getFkTableAlias()).append(".").append(col.getFkColumn());
                innerTableJoinSql.append(" AND ").append(col.getFkTableAlias()).append(".Container").append(" = ?)");
                innerTableJoinSql.add(container.getId());

                sep = ",\n\t";
            }
        }
        remapExternalIdsSql.append(" FROM (").append(innerTableSelectSql).append(" FROM ").append(tempTable);
        remapExternalIdsSql.append(innerTableJoinSql).append(") InnerTable\nWHERE InnerTable.RowId = ").append(tempTable).append(".RowId;");

        info("Remapping lookup indexes in temp table...");
        if (DEBUG)
            info(remapExternalIdsSql.toString());
        Table.execute(schema, remapExternalIdsSql);
        info("Update complete.");
    }

    private void updateTempTableVisits(DbSchema schema, Container container, String tempTable)
            throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (study.getTimepointType() != TimepointType.VISIT)
        {
            info("Updating visit values to match draw timestamps (date-based studies only)...");
            SQLFragment visitValueSql = new SQLFragment();
            visitValueSql.append("UPDATE " + tempTable + " SET VisitValue = (");
            visitValueSql.append(StudyManager.sequenceNumFromDateSQL("DrawTimestamp"));
            visitValueSql.append(");");
            if (DEBUG)
                info(visitValueSql.toString());
            Table.execute(schema, visitValueSql);
            info("Update complete.");
        }
    }

    private void updateTempTableSpecimenHash(DbSchema schema, Container container, String tempTable, List<SpecimenColumn> loadedColumns)
            throws SQLException
    {
        SQLFragment conflictResolvingSubselect = new SQLFragment("SELECT GlobalUniqueId");
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getTargetTable().isSpecimens())
            {
                conflictResolvingSubselect.append(",\n\t");
                String selectCol = tempTable + "." + col.getDbColumnName();

                if (col.getAggregateEventFunction() != null)
                    conflictResolvingSubselect.append(col.getAggregateEventFunction()).append("(").append(selectCol).append(")");
                else
                {
                    String singletonAggregate;
                    if (col.getJavaType().equals(Boolean.class))
                    {
                        // gross nested calls to cast the boolean to an int, get its min, then cast back to a boolean.
                        // this is needed because most aggregates don't work on boolean values.
                        singletonAggregate = "CAST(MIN(CAST(" + selectCol + " AS INTEGER)) AS " + schema.getSqlDialect().getBooleanDataType()  + ")";
                    }
                    else
                    {
                        singletonAggregate = "MIN(" + selectCol + ")";
                    }
                    conflictResolvingSubselect.append("CASE WHEN");
                    conflictResolvingSubselect.append(" COUNT(DISTINCT(").append(selectCol).append(")) = 1 THEN ");
                    conflictResolvingSubselect.append(singletonAggregate);
                    conflictResolvingSubselect.append(" ELSE NULL END");
                }
                conflictResolvingSubselect.append(" AS ").append(col.getDbColumnName());
            }
        }
        conflictResolvingSubselect.append("\nFROM ").append(tempTable).append("\nGROUP BY GlobalUniqueId");

        SQLFragment updateHashSql = new SQLFragment();
        updateHashSql.append("UPDATE ").append(tempTable).append(" SET SpecimenHash = ");
        ArrayList<String> hash = new ArrayList<String>(loadedColumns.size());
        hash.add("?");
        updateHashSql.add("Fld-" + container.getRowId());
        String strType = schema.getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR);

        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getTargetTable().isSpecimens())
            {
                String columnName = "InnerTable." + col.getDbColumnName();
                hash.add("'~'");
                hash.add(" CASE WHEN " + columnName + " IS NOT NULL THEN CAST(" + columnName + " AS " + strType + ") ELSE '' END");
            }
        }

        updateHashSql.append(schema.getSqlDialect().concatenate(hash.toArray(new String[hash.size()])));
        updateHashSql.append("\nFROM (").append(conflictResolvingSubselect).append(") InnerTable WHERE ");
        updateHashSql.append(tempTable).append(".GlobalUniqueId = InnerTable.GlobalUniqueId");

        info("Updating specimen hash values in temp table...");
        if (DEBUG)
            info(updateHashSql.toString());
        Table.execute(schema, updateHashSql);
        info("Update complete.");
        info("Temp table populated.");
    }

    private Parameter.TypedValue getValueParameter(ImportableColumn col, Map tsvRow)
            throws SQLException
    {
        Object value = null;
        if (tsvRow.containsKey(col.getTsvColumnName()))
            value = tsvRow.get(col.getTsvColumnName());
        else if (tsvRow.containsKey(col.getDbColumnName()))
            value = tsvRow.get(col.getDbColumnName());

        if (value == null)
            return Parameter.nullParameter(col.getSQLType());
        Parameter.TypedValue typed = new Parameter.TypedValue(value, col.getSQLType());

        if (col.getMaxSize() >= 0)
        {
            Object valueToBind = Parameter.getValueToBind(typed);
            if (valueToBind != null)
            {
                if (valueToBind.toString().length() > col.getMaxSize())
                {
                    throw new SQLException("Value \"" + valueToBind.toString() + "\" is too long for column " +
                            col.getDbColumnName() + ".  The maximum allowable length is " + col.getMaxSize() + ".");
                }
            }
        }

        return typed;
    }

    private static final boolean DEBUG = false;
    private static final boolean VERBOSE_DEBUG = false;

    private String createTempTable(DbSchema schema) throws SQLException
    {
        assert cpuCreateTempTable.start();
        try
        {
            info("Creating temp table to hold archive data...");
            SqlDialect dialect = StudySchema.getInstance().getSqlDialect();
            String tableName;
            StringBuilder sql = new StringBuilder();
            int randomizer = (new Random().nextInt(900000000) + 100000000);  // Ensure 9-digit random number
            if (DEBUG)
            {
                tableName = dialect.getGlobalTempTablePrefix() + "SpecimenUpload" + randomizer;
                sql.append("CREATE TABLE ").append(tableName);
            }
            else
            {
                tableName = dialect.getTempTablePrefix() + "SpecimenUpload" + randomizer;
                sql.append("CREATE ").append(dialect.getTempTableKeyword()).append(" TABLE ").append(tableName);
            }
            String strType = schema.getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR);
            sql.append("\n(\n    RowId ").append(schema.getSqlDialect().getUniqueIdentType()).append(", ");
            sql.append("Container ").append(strType).append("(300) NOT NULL, ");
            sql.append("LSID ").append(strType).append("(300) NOT NULL, ");
            sql.append("SpecimenHash ").append(strType).append("(300)");
            for (SpecimenColumn col : SPECIMEN_COLUMNS)
                sql.append(",\n    ").append(col.getDbColumnName()).append(" ").append(col.getDbType());
            sql.append("\n);");
            if (DEBUG)
                info(sql.toString());
            Table.execute(schema, sql.toString(), null);

            String rowIdIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_RowId ON " + tableName + "(RowId)";
            String globalUniqueIdIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_GlobalUniqueId ON " + tableName + "(GlobalUniqueId)";
            String lsidIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_LSID ON " + tableName + "(LSID)";
            String hashIndexSql = "CREATE INDEX IX_SpecimenUpload" + randomizer + "_SpecimenHash ON " + tableName + "(SpecimenHash)";
            if (DEBUG)
            {
                info(globalUniqueIdIndexSql);
                info(rowIdIndexSql);
                info(lsidIndexSql);
                info(hashIndexSql);
            }
            Table.execute(schema, globalUniqueIdIndexSql, null);
            Table.execute(schema, rowIdIndexSql, null);
            Table.execute(schema, lsidIndexSql, null);
            Table.execute(schema, hashIndexSql, null);
            info("Created temporary table " + tableName);

            return tableName;
        }
        finally
        {
            assert cpuCreateTempTable.stop();
        }
    }

    public static class TestCase extends Assert
    {
        private DbSchema _schema;
        private String _tableName;
        private static final String TABLE = "SpecimenImporterTest";

        @Before
        public void createTable() throws SQLException
        {
            _schema = TestSchema.getInstance().getSchema();

            _tableName = _schema.getName() + "." + TABLE;
            dropTable();

            Table.execute(_schema,
                "CREATE TABLE " + _tableName +
                "(Container VARCHAR(255) NOT NULL, id VARCHAR(10), s VARCHAR(32), i INTEGER)", null);
        }

        @After
        public void dropTable() throws SQLException
        {
            _schema.dropTableIfExists(TABLE);
        }

        private Table.TableResultSet selectValues()
                throws SQLException
        {
            return Table.executeQuery(_schema, "SELECT Container,id,s,i FROM " + _tableName + " ORDER BY id", null);
        }

        private Map<String, Object> row(String s, Integer i)
        {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("s", s);
            map.put("i", i);
            return map;
        }

        @Test
        public void mergeTest() throws SQLException
        {
            Container c = JunitUtil.getTestContainer();

            Collection<ImportableColumn> cols = Arrays.asList(
                    new ImportableColumn("s", "s", "VARCHAR(32)", true),
                    new ImportableColumn("i", "i", "INTEGER", false)
            );

            Iterable<Map<String, Object>> values = Arrays.asList(
                    row("Bob", 100),
                    row("Sally", 200),
                    row(null, 300)
            );

            SpecimenImporter importer = new SpecimenImporter();
            final Integer[] counter = new Integer[] { 0 };
            ComputedColumn idCol = new ComputedColumn()
            {
                public String getName() { return "id"; }
                public Object getValue(Map<String, Object> row)
                {
                    return String.valueOf(++counter[0]);
                }
            };

            // Insert rows
            Pair<List<ImportableColumn>, Integer> pair = importer.mergeTable(_schema, c, _tableName, cols, values, idCol);
            assertNotNull(pair);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(3, pair.second.intValue());
            assertEquals(3, counter[0]);

            Table.TableResultSet rs = selectValues();
            try
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                assertEquals("Bob", row0.get("s"));
                assertEquals(100, row0.get("i"));
                assertEquals("1", row0.get("id"));

                Map<String, Object> row1 = iter.next();
                assertEquals("Sally", row1.get("s"));
                assertEquals(200, row1.get("i"));
                assertEquals("2", row1.get("id"));

                Map<String, Object> row2 = iter.next();
                assertEquals(null, row2.get("s"));
                assertEquals(300, row2.get("i"));
                assertEquals("3", row2.get("id"));
                assertFalse(iter.hasNext());
            }
            finally
            {
                if (rs != null) try { rs.close(); } catch (SQLException e) { }
            }

            // Add one new row, update one existing row.
            values = Arrays.asList(
                    row("Bob", 105),
                    row(null, 305),
                    row("Jimmy", 405)
            );
            pair = importer.mergeTable(_schema, c, _tableName, cols, values, idCol);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(3, pair.second.intValue());
            assertEquals(4, counter[0]);

            rs = selectValues();
            try
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                assertEquals("Bob", row0.get("s"));
                assertEquals(105, row0.get("i"));
                assertEquals("1", row0.get("id"));

                Map<String, Object> row1 = iter.next();
                assertEquals("Sally", row1.get("s"));
                assertEquals(200, row1.get("i"));
                assertEquals("2", row1.get("id"));

                Map<String, Object> row2 = iter.next();
                assertEquals(null, row2.get("s"));
                assertEquals(305, row2.get("i"));
                assertEquals("3", row2.get("id"));

                Map<String, Object> row3 = iter.next();
                assertEquals("Jimmy", row3.get("s"));
                assertEquals(405, row3.get("i"));
                assertEquals("4", row3.get("id"));
                assertFalse(iter.hasNext());
            }
            finally
            {
                if (rs != null) try { rs.close(); } catch (SQLException e) { }
            }
        }
    }
    
}
