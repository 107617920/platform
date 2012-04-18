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

package org.labkey.study.query;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;

import java.util.Map;

public class ParticipantVisitTable extends FilteredTable
{
    StudyQuerySchema _schema;
    Map<String, ColumnInfo> _demographicsColumns;

    public ParticipantVisitTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoParticipantVisit(), schema.getContainer());
        setName(StudyService.get().getSubjectVisitTableName(schema.getContainer()));
        _schema = schema;
        _demographicsColumns = new CaseInsensitiveHashMap<ColumnInfo>();
        Study study = StudyService.get().getStudy(schema.getContainer());

        ColumnInfo participantSequenceNumColumn = null;
        for (ColumnInfo col : _rootTable.getColumns())
        {
            if ("Container".equalsIgnoreCase(col.getName()))
                continue;
            else if ("VisitRowId".equalsIgnoreCase(col.getName()))
            {
                ColumnInfo visitColumn = new AliasedColumn(this, "Visit", col);
                LookupForeignKey visitFK = new LookupForeignKey("RowId")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new VisitTable(_schema);
                    }
                };
                visitColumn.setFk(visitFK);
                visitColumn.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
                    public DisplayColumn createRenderer(ColumnInfo col)
                    {
                        return new BaseStudyTable.VisitDisplayColumn(col, FieldKey.fromParts("SequenceNum"));
                    }
                });
                addColumn(visitColumn);
            }
            else if ("CohortID".equalsIgnoreCase(col.getName()))
            {
                if (StudyManager.getInstance().showCohorts(getContainer(), schema.getUser()))
                {
                    ColumnInfo cohortColumn = new AliasedColumn(this, "Cohort", col);
                    cohortColumn.setFk(new LookupForeignKey("RowId")
                    {
                        public TableInfo getLookupTableInfo()
                        {
                            return new CohortTable(_schema);
                        }
                    });
                    addColumn(cohortColumn);
                }
            }
            else if ("ParticipantSequenceNum".equalsIgnoreCase(col.getName()))
            {
                participantSequenceNumColumn = addWrapColumn(col);
                participantSequenceNumColumn.setHidden(true);
            }
            else if ("ParticipantId".equalsIgnoreCase(col.getName()))
            {
                ColumnInfo subjectColumn = wrapColumn(StudyService.get().getSubjectColumnName(getContainer()), col);
                addColumn(subjectColumn);
            }
            else if (study != null && study.getTimepointType() != TimepointType.VISIT && "SequenceNum".equalsIgnoreCase(col.getName()))
            {
                ColumnInfo sequenceNumCol = addWrapColumn(col);
                sequenceNumCol.setHidden(true);
            }
            else
                addWrapColumn(col);
        }

        for (DataSetDefinition dataset : _schema.getStudy().getDataSets())
        {
            // verify that the current user has permission to read this dataset (they may not if
            // advanced study security is enabled).
            if (!dataset.canRead(schema.getUser()))
                continue;

            String name = _schema.decideTableName(dataset);
            if (name == null)
                continue;

            // duplicate labels! see BUG 2206
            if (getColumn(name) != null)
                continue;

            // if not keyed by Participant/SequenceNum it is not a lookup
            if (dataset.getKeyPropertyName() != null)
                continue;

            ColumnInfo datasetColumn = createDataSetColumn(name, dataset, participantSequenceNumColumn);

            // Don't add demographics datasets, but stash it for backwards compatibility with <11.3 queries if needed.
            if (dataset.isDemographicData())
                _demographicsColumns.put(name, datasetColumn);
            else
                addColumn(datasetColumn);
        }
    }


    protected ColumnInfo createDataSetColumn(String name, final DataSetDefinition dsd, ColumnInfo participantSequenceNumColumn)
    {
        ColumnInfo ret = new AliasedColumn(name, participantSequenceNumColumn);
        ret.setFk(new PVForeignKey(dsd));
        ret.setLabel(dsd.getLabel());
        ret.setIsUnselectable(true);
        return ret;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo col = super.resolveColumn(name);
        if (col != null)
            return col;

        col = _demographicsColumns.get(name);
        if (col != null)
            return addColumn(col);

        // Resolve 'ParticipantSequenceKey' to 'ParticipantSequenceNum' for compatibility with versions <12.2.
        if ("ParticipantSequenceKey".equalsIgnoreCase(name))
            return getColumn("ParticipantSequenceNum");

        return null;
    }

    private class PVForeignKey extends LookupForeignKey
    {
        private final DataSetDefinition dsd;

        public PVForeignKey(DataSetDefinition dsd)
        {
            super(StudyService.get().getSubjectVisitColumnName(dsd.getContainer()));
            this.dsd = dsd;
        }
        
        public DataSetTableImpl getLookupTableInfo()
        {
            try
            {
                DataSetTableImpl ret = new DataSetTableImpl(_schema, dsd);
                ret.hideParticipantLookups();
                return ret;
            }
            catch (UnauthorizedException e)
            {
                return null;
            }
        }

        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }
}

