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

package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.*;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.DataSetTable;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyManager;

import java.util.*;

/** Wraps a DatasetSchemaTableInfo and makes it Query-ized. Represents a single dataset's data */
public class DataSetTableImpl extends FilteredTable implements DataSetTable
{
    public static final String QCSTATE_ID_COLNAME = "QCState";
    public static final String QCSTATE_LABEL_COLNAME = "QCStateLabel";
    StudyQuerySchema _schema;
    DataSetDefinition _dsd;
    TableInfo _fromTable;

    public DataSetTableImpl(StudyQuerySchema schema, DataSetDefinition dsd)
    {
        super(dsd.getTableInfo(schema.getUser(), schema.getMustCheckPermissions()));
        setDescription("Contains up to one row of " + dsd.getLabel() + " data for each " +
                StudyService.get().getSubjectNounSingular(dsd.getContainer()) + (!dsd.isDemographicData() ? "/visit" : "") +
            (dsd.getKeyPropertyName() != null ? "/" + dsd.getKeyPropertyName() : "") + " combination.");
        _schema = schema;
        _dsd = dsd;

        List<FieldKey> defaultVisibleCols = new ArrayList<FieldKey>();

        HashSet<String> standardURIs = new HashSet<String>();
        for (PropertyDescriptor pd :  DataSetDefinition.getStandardPropertiesSet())
            standardURIs.add(pd.getPropertyURI());

        ActionURL updateURL = new ActionURL(DatasetController.UpdateAction.class, dsd.getContainer());
        updateURL.addParameter("datasetId", dsd.getDataSetId());
        setUpdateURL(new DetailsURL(updateURL, Collections.singletonMap("lsid", "lsid")));

        ActionURL insertURL = new ActionURL(DatasetController.InsertAction.class, getContainer());
        insertURL.addParameter(DataSetDefinition.DATASETKEY, dsd.getDataSetId());
        setInsertURL(new DetailsURL(insertURL));

        ActionURL gridURL = new ActionURL(StudyController.DatasetAction.class, dsd.getContainer());
        gridURL.addParameter(DataSetDefinition.DATASETKEY, dsd.getDataSetId());
        setGridURL(new DetailsURL(gridURL));

//        ActionURL importURL = new ActionURL(StudyController.ShowImportDatasetAction.class, dsd.getContainer());
        ActionURL importURL = new ActionURL(StudyController.ImportAction.class, dsd.getContainer());
        importURL.addParameter(DataSetDefinition.DATASETKEY, dsd.getDataSetId());
        setImportURL(new DetailsURL(importURL));

        String subjectColName = StudyService.get().getSubjectColumnName(dsd.getContainer());
        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            String name = baseColumn.getName();
            if (subjectColName.equalsIgnoreCase(name))
            {
                ColumnInfo column = new AliasedColumn(this, subjectColName, baseColumn);
                column.setFk(new QueryForeignKey(_schema,StudyService.get().getSubjectTableName(dsd.getContainer()),
                        subjectColName, subjectColName)
                {
                    public StringExpression getURL(ColumnInfo parent) {
                        ActionURL base = new ActionURL(StudyController.ParticipantAction.class, _schema.getContainer());
                        base.addParameter(DataSetDefinition.DATASETKEY, Integer.toString(_dsd.getDataSetId()));
                        return new DetailsURL(base, "participantId", parent.getFieldKey());
                    }
                });

                if (DemoMode.isDemoMode(schema.getContainer(), schema.getUser()))
                {
                    column.setDisplayColumnFactory(new DisplayColumnFactory() {
                        @Override
                        public DisplayColumn createRenderer(ColumnInfo column)
                        {
                            return new PtidObfuscatingDisplayColumn(column);
                        }
                    });
                }

                addColumn(column);
                if (isVisibleByDefault(column))
                    defaultVisibleCols.add(FieldKey.fromParts(column.getName()));
            }
            else if (getRealTable().getColumn(baseColumn.getName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX) != null)
            {
                // If this is the value column that goes with an OORIndicator, add the special OOR options
                OORDisplayColumnFactory.addOORColumns(this, baseColumn, getRealTable().getColumn(baseColumn.getName() +
                        OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX));
                if (isVisibleByDefault(baseColumn))
                    defaultVisibleCols.add(FieldKey.fromParts(baseColumn.getName()));
            }
            else if (baseColumn.getName().toLowerCase().endsWith(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.toLowerCase()) &&
                    getRealTable().getColumn(baseColumn.getName().substring(0, baseColumn.getName().length() - OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.length())) != null)
            {
                // If this is an OORIndicator and there's a matching value column in the same table, don't add this column
            }
            else if (baseColumn.getName().equalsIgnoreCase("SequenceNum") && _schema.getStudy().getTimepointType() != TimepointType.VISIT)
            {
                // wrap the sequencenum column and set a format to prevent scientific notation, since the sequencenum values
                // for date-based studies can be quite large (e.g., 20091014).
                addWrapColumn(baseColumn).setFormat("#");
                //Don't add to visible cols...
            }
            else if (baseColumn.getName().equalsIgnoreCase("VisitRowId")||baseColumn.getName().equalsIgnoreCase("Dataset"))
            {
                addWrapColumn(baseColumn);
            }
            else if (baseColumn.getName().equalsIgnoreCase(QCSTATE_ID_COLNAME))
            {
                ColumnInfo qcStateColumn = new AliasedColumn(this, QCSTATE_ID_COLNAME, baseColumn);
                qcStateColumn.setFk(new LookupForeignKey("RowId")
                    {
                        public TableInfo getLookupTableInfo()
                        {
                            // Go through the schema so that metadata is overlaid
                            return _schema.getTable(StudyQuerySchema.QCSTATE_TABLE_NAME);
                        }
                    });

                qcStateColumn.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new QCStateDisplayColumn(colInfo);
                    }
                });

                qcStateColumn.setDimension(false);

                addColumn(qcStateColumn);
                // Hide the QCState column if the study doesn't have QC states defined. Otherwise, don't hide it
                // but don't include it in the default set of columns either
                if (!StudyManager.getInstance().showQCStates(_schema.getContainer()))
                    qcStateColumn.setHidden(true);
            }
            else if ("ParticipantSequenceKey".equalsIgnoreCase(name))
            {
                // Add a copy of the sequence key column without the FK so we can get the value easily when materializing to temp tables:
                addWrapColumn(baseColumn).setHidden(true);
                ColumnInfo pvColumn = new AliasedColumn(this, StudyService.get().getSubjectVisitColumnName(dsd.getContainer()), baseColumn);//addWrapColumn(baseColumn);
                pvColumn.setFk(new LookupForeignKey("ParticipantSequenceKey")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new ParticipantVisitTable(_schema);
                    }
                });
                pvColumn.setIsUnselectable(true);
                pvColumn.setDimension(false);
                addColumn(pvColumn);
            }
            else
            {
                ColumnInfo col = addWrapColumn(baseColumn);

                // When copying a column, the hidden bit is not propagated, so we need to do it manually
                if (baseColumn.isHidden() || !isVisibleByDefault(baseColumn))
                    col.setHidden(true);
                
                String propertyURI = col.getPropertyURI();
                if (null != propertyURI && !standardURIs.contains(propertyURI))
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyURI, schema.getContainer());
                    if (null != pd && pd.getLookupQuery() != null)
                        col.setFk(new PdLookupForeignKey(schema.getUser(), pd));
                    
                    if (pd != null && pd.getPropertyType() == PropertyType.MULTI_LINE)
                    {
                        col.setDisplayColumnFactory(new DisplayColumnFactory() {
                            public DisplayColumn createRenderer(ColumnInfo colInfo)
                            {
                                DataColumn dc = new DataColumn(colInfo);
                                dc.setPreserveNewlines(true);
                                return dc;
                            }
                        });
                    }
                }
                if (isVisibleByDefault(col))
                    defaultVisibleCols.add(FieldKey.fromParts(col.getName()));
            }
        }
        ColumnInfo lsidColumn = getColumn("LSID");
        lsidColumn.setHidden(true);
        lsidColumn.setKeyField(true);
        getColumn("SourceLSID").setHidden(true);
        setDefaultVisibleColumns(defaultVisibleCols);

        // Don't show sequence num for date-based studies
        if (!dsd.getStudy().getTimepointType().isVisitBased())
        {
            getColumn("SequenceNum").setHidden(true);
            getColumn("SequenceNum").setShownInInsertView(false);
            getColumn("SequenceNum").setShownInDetailsView(false);
            getColumn("SequenceNum").setShownInUpdateView(false);
        }

        // columns from the ParticipantVisit table

        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        if (_schema.getStudy().getTimepointType() == TimepointType.DATE)
        {
            ColumnInfo dayColumn = new AliasedColumn(this, "Day", participantVisit.getColumn("Day"));
            dayColumn.setUserEditable(false);
            dayColumn.setDimension(false);
            dayColumn.setMeasure(false);
            addColumn(dayColumn);
        }

        ColumnInfo visitRowId = new AliasedColumn(this, "VisitRowId", participantVisit.getColumn("VisitRowId"));
        visitRowId.setName("VisitRowId");
        visitRowId.setHidden(true);
        visitRowId.setUserEditable(false);
        visitRowId.setMeasure(false);
        addColumn(visitRowId);

        if (_dsd.isAssayData())
        {
            TableInfo assayResultTable = createAssayResultTable();
            if (assayResultTable != null)
            {
                for (final ColumnInfo columnInfo : assayResultTable.getColumns())
                {
                    if (getColumn(columnInfo.getName()) == null)
                    {
                        ExprColumn wrappedColumn = new ExprColumn(this, columnInfo.getName(), columnInfo.getValueSql(ExprColumn.STR_TABLE_ALIAS + "_AR"), columnInfo.getJdbcType())
                        {
                            @Override
                            public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
                            {
                                super.declareJoins(parentAlias, map);
                                columnInfo.declareJoins(getAssayResultAlias(parentAlias), map);
                            }
                        };
                        wrappedColumn.copyAttributesFrom(columnInfo);
                        addColumn(wrappedColumn);
                    }
                }

                for (FieldKey fieldKey : assayResultTable.getDefaultVisibleColumns())
                {
                    if (!defaultVisibleCols.contains(fieldKey) && !defaultVisibleCols.contains(FieldKey.fromParts(fieldKey.getName())))
                    {
                        defaultVisibleCols.add(fieldKey);
                    }
                }

                // Remove the target study column from the dataset version of the table - it's already scoped to the
                // relevant study so don't clutter the UI with it
                for (FieldKey fieldKey : defaultVisibleCols)
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(fieldKey.getName()))
                    {
                        defaultVisibleCols.remove(fieldKey);
                        break;
                    }
                }
                ExpProtocol protocol = _dsd.getAssayProtocol();
                AssayProvider provider = AssayService.get().getProvider(protocol);
                defaultVisibleCols.add(new FieldKey(provider.getTableMetadata().getRunFieldKeyFromResults(), ExpRunTable.Column.Name.toString()));
                defaultVisibleCols.add(new FieldKey(provider.getTableMetadata().getRunFieldKeyFromResults(), ExpRunTable.Column.Comments.toString()));
            }
        }
    }

    @Override
    public DataSet getDataSet()
    {
        return _dsd;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result != null)
        {
            return result;
        }

        FieldKey fieldKey = null;

        // Be backwards compatible with the old field keys for these properties.
        // We used to flatten all of the different domains/tables on the assay side into a row in the dataset,
        // so transform to do a lookup instead
        ExpProtocol protocol = _dsd.getAssayProtocol();
        if (protocol != null)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            FieldKey runFieldKey = provider == null ? null : provider.getTableMetadata().getRunFieldKeyFromResults();
            if (name.toLowerCase().startsWith("run"))
            {
                String runProperty = name.substring("run".length()).trim();
                if (runProperty.length() > 0 && runFieldKey != null)
                {
                    fieldKey = new FieldKey(runFieldKey, runProperty);
                }
            }
            else if (name.toLowerCase().startsWith("batch"))
            {
                String batchPropertyName = name.substring("batch".length()).trim();
                if (batchPropertyName.length() > 0 && runFieldKey != null)
                {
                    fieldKey = new FieldKey(new FieldKey(runFieldKey, "Batch"), batchPropertyName);
                }
            }
            else if (name.toLowerCase().startsWith("analyte"))
            {
                String analytePropertyName = name.substring("analyte".length()).trim();
                if (analytePropertyName.length() > 0)
                {
                    fieldKey = FieldKey.fromParts("Analyte", analytePropertyName);
                    Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
                    result = columns.get(fieldKey);
                    if (result != null)
                    {
                        return result;
                    }
                    fieldKey = FieldKey.fromParts("Analyte", "Properties", analytePropertyName);
                }
            }
            else
            {
                // Check the name to prevent infinite recursion
                if (!name.equalsIgnoreCase("Properties"))
                {
                    // Try looking for it as a NAb specimen property
                    fieldKey = FieldKey.fromParts("Properties", "SpecimenLsid", "Property", name);
                    Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
                    result = columns.get(fieldKey);
                    if (result != null)
                    {
                        return result;
                    }
                }
            }
        }
        if (fieldKey == null && !name.equalsIgnoreCase("Properties"))
        {
            fieldKey = FieldKey.fromParts("Properties", name);
        }

        if (fieldKey != null)
        {
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
            result = columns.get(fieldKey);
        }
        return result;
    }

    @Override
    public Domain getDomain()
    {
        if (_dsd != null)
            return _dsd.getDomain();
        return null;
    }

    private String getAssayResultAlias(String mainAlias)
    {
        return mainAlias + "_AR";
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();

        SQLFragment from = new SQLFragment();
        from.append("(SELECT DS.*, PV.VisitRowId");
        if (_schema.getStudy().getTimepointType() == TimepointType.DATE)
            from.append(", PV.Day");
        from.append("\nFROM ").append(super.getFromSQL("DS")).append(" LEFT OUTER JOIN ").append(participantVisit.getFromSQL("PV")).append("\n" +
                " ON DS.ParticipantId=PV.ParticipantId AND DS.SequenceNum=PV.SequenceNum AND PV.Container = '" + _schema.getContainer().getId() + "') AS ").append(alias);

        if (_dsd.isAssayData())
        {
            // Join in Assay-side data to make it appear as if it's in the dataset table itself 
            String assayResultAlias = getAssayResultAlias(alias);
            TableInfo assayResultTable = createAssayResultTable();
            // Check if assay design has been deleted
            if (assayResultTable != null)
            {
                from.append(" LEFT OUTER JOIN ").append(assayResultTable.getFromSQL(assayResultAlias)).append("\n");
                from.append(" ON ").append(assayResultAlias).append(".").append(assayResultTable.getPkColumnNames().get(0)).append(" = ");
                from.append(alias).append(".").append(getSqlDialect().getColumnSelectName(_dsd.getKeyPropertyName()));
            }
        }

        return from;
    }

    private TableInfo createAssayResultTable()
    {
        ExpProtocol protocol = _dsd.getAssayProtocol();
        if (protocol == null)
        {
            return null;
        }
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
        {
            // Provider must have been in a module that's no longer available
            return null;
        }
        AssaySchema schema = AssayService.get().createSchema(_schema.getUser(), protocol.getContainer());
        ContainerFilterable result = provider.createDataTable(schema, protocol, false);
        if (result != null)
        {
            result.setContainerFilter(ContainerFilter.EVERYTHING);
        }
        return result;
    }

    @Override
    public boolean hasContainerContext()
    {
        return null != _dsd && null != _dsd.getContainer();
    }

    @Override
    public Container getContainer(Map m)
    {
        return _dsd.getContainer();
    }

    @Override
    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        // First apply all the metadata from study.StudyData so that it doesn't have to be duplicated for
        // every dataset
        super.overlayMetadata(StudyQuerySchema.STUDY_DATA_TABLE_NAME, schema, errors);

        if (!_dsd.getName().equalsIgnoreCase(tableName))
        {
            super.overlayMetadata(_dsd.getName(), schema, errors);
        }

        if (_dsd.getLabel() != null && !_dsd.getLabel().equalsIgnoreCase(tableName))
        {
            super.overlayMetadata(_dsd.getLabel(), schema, errors);
        }

        // Then include the specific overrides for this dataset
        super.overlayMetadata(tableName, schema, errors);
    }

    private class QCStateDisplayColumn extends DataColumn
    {
        private Map<Integer, QCState> _qcStateCache;
        public QCStateDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        public String getFormattedValue(RenderContext ctx)
        {
            Object value = getValue(ctx);
            StringBuilder formattedValue = new StringBuilder(super.getFormattedValue(ctx));
            if (value != null && value instanceof Integer)
            {
                QCState state = getStateCache(ctx).get((Integer) value);
                if (state != null && state.getDescription() != null)
                    formattedValue.append(PageFlowUtil.helpPopup("QC State " + state.getLabel(), state.getDescription()));
            }
            return formattedValue.toString();
        }

        private Map<Integer, QCState> getStateCache(RenderContext ctx)
        {
            if (_qcStateCache == null)
            {
                _qcStateCache = new HashMap<Integer, QCState>();
                QCState[] states = StudyManager.getInstance().getQCStates(ctx.getContainer());
                for (QCState state : states)
                    _qcStateCache.put(state.getRowId(), state);
            }
            return _qcStateCache;
        }
    }

    private static final Set<String> defaultHiddenCols = new CaseInsensitiveHashSet("VisitRowId", "Created", "CreatedBy", "ModifiedBy", "Modified", "lsid", "SourceLsid");
    private boolean isVisibleByDefault(ColumnInfo col)
    {
        // If this is a server-managed key, or an assay-backed dataset, don't include the key column in the default
        // set of visible columns
        if ((_dsd.getKeyManagementType() != DataSet.KeyManagementType.None || _dsd.isAssayData()) &&
                col.getName().equals(_dsd.getKeyPropertyName()))
            return false;
        return (!col.isHidden() && !col.isUnselectable() && !defaultHiddenCols.contains(col.getName()));
    }


    protected TableInfo getFromTable()
    {
        if (_fromTable == null)
        {
            _fromTable = _dsd.getTableInfo(_schema.getUser(), _schema.getMustCheckPermissions());
        }
        return _fromTable;
    }

    public DataSetDefinition getDatasetDefinition()
    {
        return _dsd;
    }

    /**
     * In order to discourage the user from selecting data from deeply nested datasets, we hide
     * the "ParticipantID" and "ParticipantVisit" columns when the user could just as easily find
     * the same data further up the tree.
     */
    public void hideParticipantLookups()
    {
        getColumn(StudyService.get().getSubjectColumnName(_dsd.getContainer())).setHidden(true);
        getColumn(StudyService.get().getSubjectVisitColumnName(_dsd.getContainer())).setHidden(true);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        User user = _schema.getUser();
        DataSet def = getDatasetDefinition();
        if (!def.canWrite(user))
            return null;
        return new DatasetUpdateService(this);
    }

    @Override
    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        DataSet def = getDatasetDefinition();
        if (ReadPermission.class.isAssignableFrom(perm))
            return def.canRead(user);
        if (InsertPermission.class.isAssignableFrom(perm) || UpdatePermission.class.isAssignableFrom(perm) || DeletePermission.class.isAssignableFrom(perm))
            return def.canWrite(user);
        return false;
    }
}
