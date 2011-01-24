/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: Dec 14, 2010
 */
public class AssayResultTable extends FilteredTable implements UpdateableTableInfo
{
    protected final AssaySchema _schema;
    private final ExpProtocol _protocol;
    private final AssayProvider _provider;
    private final Domain _resultsDomain;

    public AssayResultTable(AssaySchema schema, ExpProtocol protocol, AssayProvider assayProvider, boolean includeCopiedToStudyColumns)
    {
        super(StorageProvisioner.createTableInfo(assayProvider.getResultsDomain(protocol), schema.getDbSchema()));
        _schema = schema;
        _protocol = protocol;
        _provider = assayProvider;

        _resultsDomain = _provider.getResultsDomain(protocol);

        setDescription("Contains all of the results (and may contain raw data as well) for the " + protocol.getName() + " assay definition");

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();

        ColumnInfo specimenIdCol = null;
        boolean foundTargetStudyCol = false;

        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            ColumnInfo col;

            if (getRealTable().getColumn(baseColumn.getName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX) != null)
            {
                // If this is the value column that goes with an OORIndicator, add the special OOR options
                col = OORDisplayColumnFactory.addOORColumns(this, baseColumn, getRealTable().getColumn(baseColumn.getName() +
                        OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX));

            }
            else if (baseColumn.getName().toLowerCase().endsWith(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.toLowerCase()) &&
                    getRealTable().getColumn(baseColumn.getName().substring(0, baseColumn.getName().length() - OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.length())) != null)
            {
                // If this is an OORIndicator and there's a matching value column in the same table, don't add this column
                col = null;
            }
            else
            {
                col = wrapColumn(baseColumn);

                if (AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()) || AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setHidden(true);
                }
                if (AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setKeyField(true);
                }

                DomainProperty domainProperty = _resultsDomain.getPropertyByName(baseColumn.getName());
                if (domainProperty != null)
                {
                    col.setName(domainProperty.getName());
                    domainProperty.getPropertyDescriptor().copyTo(col);
                }
                addColumn(col);

                if (col.getMvColumnName() != null)
                {
                    ColumnInfo rawValueCol = createRawValueColumn(baseColumn, col, RawValueColumn.RAW_VALUE_SUFFIX, "Raw Value", "This column contains the raw value itself, regardless of any missing value indicators that may have been set.");
                    addColumn(rawValueCol);
                }

                if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(col.getName()))
                    foundTargetStudyCol = true;

                if (AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equalsIgnoreCase(col.getName()))
                    specimenIdCol = col;
            }

            if (col != null && !col.isHidden() && !col.isUnselectable())
                visibleColumns.add(col.getFieldKey());
        }

        // Add FK to specimens
        if (specimenIdCol != null && specimenIdCol.getFk() == null)
        {
            if (!foundTargetStudyCol)
            {
                for (DomainProperty runDP : _provider.getRunDomain(_protocol).getProperties())
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(runDP.getName()))
                    {
                        foundTargetStudyCol = true;
                        break;
                    }
                }
            }

            if (!foundTargetStudyCol)
            {
                for (DomainProperty batchDP : _provider.getBatchDomain(_protocol).getProperties())
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(batchDP.getName()))
                    {
                        foundTargetStudyCol = true;
                        break;
                    }
                }
            }

            if (foundTargetStudyCol)
                specimenIdCol.setFk(new SpecimenForeignKey(_schema, _provider, _protocol));
        }

        ExprColumn runColumn = new ExprColumn(this, "Run", new SQLFragment("(SELECT RunId FROM exp.Data WHERE RowId = " + ExprColumn.STR_TABLE_ALIAS + ".DataId)"), Types.INTEGER);
        runColumn.setFk(new LookupForeignKey("RowID")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpRunTable expRunTable = AssayService.get().createRunTable(_protocol, _provider, _schema.getUser(), _schema.getContainer());
                expRunTable.setContainerFilter(getContainerFilter());
                return expRunTable;
            }
        });
        addColumn(runColumn);

        Domain runDomain = _provider.getRunDomain(protocol);
        for (DomainProperty prop : runDomain.getProperties())
        {
            if (!prop.isHidden())
                visibleColumns.add(FieldKey.fromParts("Run", prop.getName()));
        }

        for (DomainProperty prop : _provider.getBatchDomain(protocol).getProperties())
        {
            if (!prop.isHidden())
                visibleColumns.add(FieldKey.fromParts("Run", AssayService.BATCH_COLUMN_NAME, prop.getName()));
        }

        if (includeCopiedToStudyColumns)
        {
            Set<String> studyColumnNames = ((AbstractAssayProvider)_provider).addCopiedToStudyColumns(this, protocol, schema.getUser(), false);
            for (String columnName : studyColumnNames)
            {
                visibleColumns.add(new FieldKey(null, columnName));
            }
        }

        setDefaultVisibleColumns(visibleColumns);
    }

    @Override
    public Domain getDomain()
    {
        return _resultsDomain;
    }

    private ColumnInfo createRawValueColumn(ColumnInfo baseColumn, ColumnInfo col, String nameSuffix, String labelSuffix, String descriptionSuffix)
    {
        ColumnInfo rawValueCol = new AliasedColumn(baseColumn.getName() + nameSuffix, col);
        rawValueCol.setDisplayColumnFactory(ColumnInfo.DEFAULT_FACTORY);
        rawValueCol.setLabel(baseColumn.getLabel() + " " + labelSuffix);
        String description = baseColumn.getDescription();
        if (description == null)
        {
            description = "";
        }
        else
        {
            description += " ";
        }
        description += descriptionSuffix;
        rawValueCol.setDescription(description);
        rawValueCol.setUserEditable(false);
        rawValueCol.setHidden(true);
        rawValueCol.setRawValueColumn(true);
        rawValueCol.setMvColumnName(null); // This column itself does not allow QC
        rawValueCol.setNullable(true); // Otherwise we get complaints on import for required fields
        return rawValueCol;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        
        if ("Properties".equalsIgnoreCase(name))
        {
            // Hook up a column that joins back to this table so that the columns formerly under the Properties
            // node when this was OntologyManager-backed can still be queried there
            result = wrapColumn("Properties", getRealTable().getColumn("RowId"));
            result.setIsUnselectable(true);
            LookupForeignKey fk = new LookupForeignKey("RowId")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return new AssayResultTable(_schema, _protocol, _provider, false);
                }
            };
            fk.setPrefixColumnCaption(false);
            result.setFk(fk);
        }

        return result;
    }

    @Override
    public boolean insertSupported()
    {
        return true;
    }

    @Override
    public boolean updateSupported()
    {
        return false;
    }

    @Override
    public boolean deleteSupported()
    {
        return false;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        return getRealTable();
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return ObjectUriType.schemaColumn;
    }

    @Override
    public String getObjectURIColumnName()
    {
        return null;
    }

    @Override
    public String getObjectIdColumnName()
    {
        return null;
    }

    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        return null;
    }

    @Override
    public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
    {
        return QueryService.get().insertStatement(conn, user, this);
    }
}
