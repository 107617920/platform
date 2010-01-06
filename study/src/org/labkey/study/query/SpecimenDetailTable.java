/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.sql.Types;

public class SpecimenDetailTable extends AbstractSpecimenTable
{
    public SpecimenDetailTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenDetail());

        addWrapColumn(_rootTable.getColumn("GlobalUniqueId"));
        
        ColumnInfo pvColumn = new AliasedColumn(this, "ParticipantVisit", _rootTable.getColumn("ParticipantSequenceKey"));//addWrapColumn(baseColumn);
        pvColumn.setFk(new LookupForeignKey("ParticipantSequenceKey")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ParticipantVisitTable(_schema);
            }
        });
        pvColumn.setIsUnselectable(true);
        addColumn(pvColumn);

        addSpecimenVisitColumn(_schema.getStudy().isDateBased());
        addWrapColumn(_rootTable.getColumn("Volume"));
        addSpecimenTypeColumns();
        addWrapColumn(_rootTable.getColumn("PrimaryVolume"));
        addWrapColumn(_rootTable.getColumn("PrimaryVolumeUnits"));

        ColumnInfo specimenComment = createSpecimenCommentColumn(_schema, true);
        specimenComment.setName("Comments");
        specimenComment.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new SpecimenCommentDisplayColumn(colInfo);
            }
        });
        addColumn(specimenComment);

        addWrapColumn(_rootTable.getColumn("LockedInRequest"));
        addWrapColumn(_rootTable.getColumn("Requestable"));

        ColumnInfo siteNameColumn = wrapColumn("SiteName", getRealTable().getColumn("CurrentLocation"));
        siteNameColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new SiteTable(_schema);
            }
        });
        siteNameColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new SiteNameDisplayColumn(colInfo);
            }
        });
        addColumn(siteNameColumn);

        ColumnInfo siteLdmsCodeColumn = wrapColumn("SiteLdmsCode", getRealTable().getColumn("CurrentLocation"));
        siteLdmsCodeColumn.setFk(new LookupForeignKey("RowId", "LdmsLabCode")
        {
            public TableInfo getLookupTableInfo()
            {
                return new SiteTable(_schema);
            }
        });
        addColumn(siteLdmsCodeColumn);
        addWrapColumn(_rootTable.getColumn("AtRepository"));

        ColumnInfo availableColumn = wrapColumn("Available", getRealTable().getColumn("Available"));
        availableColumn.setKeyField(true);
        addColumn(availableColumn);

        addColumn(new QualityControlFlagColumn(this));
        addColumn(new QualityControlCommentsColumn(this));

        if (StudyManager.getInstance().showCohorts(getContainer(), schema.getUser()))
            addColumn(new CollectionCohortColumn(_schema, this));

        addWrapColumn(_rootTable.getColumn("VialCount"));
        addWrapColumn(_rootTable.getColumn("LockedInRequestCount"));
        addWrapColumn(_rootTable.getColumn("AtRepositoryCount"));
        addWrapColumn(_rootTable.getColumn("AvailableCount"));
        addWrapColumn(_rootTable.getColumn("ExpectedAvailableCount"));

        setDefaultVisibleColumns(QueryService.get().getDefaultVisibleColumns(getColumns()));

        // the old vial comments column
        boolean joinCommentsToSpecimens = true;
        addVialCommentsColumn(joinCommentsToSpecimens);
    }

    public static class QualityControlColumn extends ExprColumn
    {
        protected static final String QUALITY_CONTROL_JOIN = "QualityControlJoin$";

        public QualityControlColumn(TableInfo parent, String name, SQLFragment sql, int sqltype)
        {
            super(parent, name, sql, sqltype);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + QUALITY_CONTROL_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoSpecimenComment()).append(" AS ");
            joinSql.append(tableAlias).append(" ON ");
            joinSql.append(parentAlias).append(".GlobalUniqueId = ").append(tableAlias).append(".GlobalUniqueId AND ");
            joinSql.append(tableAlias).append(".Container = ").append(parentAlias).append(".Container");

            map.put(tableAlias, joinSql);
        }
    }

    public static class ParticipantVisitColumn extends ExprColumn
    {
        protected static final String PARTICIPANT_VISIT_JOIN = "ParticipantVisitJoin$";

        public ParticipantVisitColumn(final StudyQuerySchema schema, TableInfo parent)
        {
            super(parent, "ParticipantVisit",
                    new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "$" + PARTICIPANT_VISIT_JOIN + ".ParticipantSequenceKey"),
                    Types.INTEGER);

            setFk(new LookupForeignKey("ParticipantSequenceKey")
            {
                public TableInfo getLookupTableInfo()
                {
                    return new ParticipantVisitTable(schema);
                }
            });
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + PARTICIPANT_VISIT_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoParticipantVisit()).append(" AS ");
            joinSql.append(tableAlias).append(" ON ");
            joinSql.append(parentAlias).append(".ParticipantSequenceKey = ").append(tableAlias).append(".ParticipantSequenceKey");
            joinSql.append(" AND ").append(parentAlias).append(".Container = ").append(tableAlias).append(".Container");

            map.put(tableAlias, joinSql);
        }
    }

    public static class CollectionCohortColumn extends ExprColumn
    {
        protected static final String COLLECTION_COHORT_JOIN = "CollectionCohortJoin$";

        public CollectionCohortColumn(final StudyQuerySchema schema, TableInfo parent)
        {
            super(parent, "CollectionCohort",
                    new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "$" + COLLECTION_COHORT_JOIN + ".CohortId"),
                    Types.INTEGER);

            setFk(new LookupForeignKey("RowId")
            {
                public TableInfo getLookupTableInfo()
                {
                    return new CohortTable(schema);
                }
            });
            setDescription("The cohort of the participant at the time of specimen collection.");
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + COLLECTION_COHORT_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoParticipantVisit()).append(" AS ");
            joinSql.append(tableAlias).append(" ON ");
            joinSql.append(parentAlias).append(".ParticipantSequenceKey = ").append(tableAlias).append(".ParticipantSequenceKey");
            joinSql.append(" AND ").append(parentAlias).append(".Container = ").append(tableAlias).append(".Container");

            map.put(tableAlias, joinSql);
        }
    }

    public static class QualityControlFlagColumn extends QualityControlColumn
    {
        public QualityControlFlagColumn(BaseStudyTable parent)
        {
            super(parent,
                    "QualityControlFlag",
                    new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + "$" + QUALITY_CONTROL_JOIN + ".QualityControlFlag = ? THEN ? ELSE ? END)", Boolean.TRUE, Boolean.TRUE, Boolean.FALSE),
                    Types.BOOLEAN);
            // our column wrapping is too complex for the description to propagate through- set it here:
            setDescription("Whether this comment is associated with a quality control alert.");
        }
    }

    public static class QualityControlCommentsColumn extends QualityControlColumn
    {
        public QualityControlCommentsColumn(BaseStudyTable parent)
        {
            super(parent,
                    "QualityControlComments",
                    new SQLFragment("(" + ExprColumn.STR_TABLE_ALIAS + "$" + QUALITY_CONTROL_JOIN + ".QualityControlComments)"),
                    Types.VARCHAR);
            // our column wrapping is too complex for the description to propagate through- set it here:
            setDescription("Quality control-associated comments.  Set by the system to indicate which fields are causing quality control alerts.");
        }
    }

    public static class SiteNameDisplayColumn extends DataColumn
    {
        private static final String NO_SITE_DISPLAY_VALUE = "In Transit";
        public SiteNameDisplayColumn(ColumnInfo siteColumn)
        {
            super(siteColumn);
        }

        private ColumnInfo getInRequestColumn()
        {
            FieldKey me = getBoundColumn().getFieldKey();
            FieldKey inRequestKey = new FieldKey(me.getParent(), "LockedInRequest");
            Map<FieldKey, ColumnInfo> requiredColumns = QueryService.get().getColumns(getBoundColumn().getParentTable(), Collections.singleton(inRequestKey));
            return requiredColumns.get(inRequestKey);
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            super.addQueryColumns(columns);
            ColumnInfo inRequestCol = getInRequestColumn();
            if (inRequestCol != null)
                columns.add(inRequestCol);
        }

        private String getNoSiteText(RenderContext ctx)
        {
            ColumnInfo inRequestColumn = getInRequestColumn();
            boolean requested = false;
            if (inRequestColumn != null)
            {
                Object inRequest = inRequestColumn.getValue(ctx);
                requested = (inRequest instanceof Boolean && ((Boolean) inRequest).booleanValue()) ||
                    (inRequest instanceof Integer && ((Integer) inRequest).intValue() == 1);
                return NO_SITE_DISPLAY_VALUE + (requested ? ": Requested" : "");
            }
            else
                return NO_SITE_DISPLAY_VALUE + ": Request status unknown";
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = getBoundColumn().getValue(ctx);
            if (value == null)
                return getNoSiteText(ctx);
            else
                return super.getDisplayValue(ctx);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getBoundColumn().getValue(ctx);
            if (value == null)
                out.write(getNoSiteText(ctx));
            else
                super.renderGridCellContents(ctx, out);
        }
    }
}
