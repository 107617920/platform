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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class BaseStudyTable extends FilteredTable
{
    protected StudyQuerySchema _schema;

    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable)
    {
        this(schema, realTable, false);
    }

    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable, boolean includeSourceStudyData)
    {
        this(schema, realTable, includeSourceStudyData, false);
    }

    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable, boolean includeSourceStudyData, boolean skipPermissionChecks)
    {
        super(realTable, schema.getContainer(), includeSourceStudyData ? new ContainerFilter.StudyAndSourceStudy(schema.getUser(), skipPermissionChecks) : null);
        if (!includeSourceStudyData && skipPermissionChecks)
            throw new IllegalArgumentException("Skipping permission checks only applies when including source study data");
        if (includeSourceStudyData && getParticipantColumnName() != null)
        {
            // If we're in an ancillary study, show the parent folder's specimens, but filter to include only those
            // that relate to subjects in the ancillary study.  This filter will have no effect on samples uploaded
            // directly in this this study folder (since all local specimens should have an associated subject ID
            // already in the participant table.
            Study currentStudy = StudyManager.getInstance().getStudy(schema.getContainer());
            if (currentStudy != null && currentStudy.isAncillaryStudy())
            {
                String[] ptids = ParticipantGroupManager.getInstance().getAllGroupedParticipants(schema.getContainer());
                if (ptids.length > 0)
                {
                    Study sourceStudy = currentStudy.getSourceStudy();
                    SQLFragment condition = new SQLFragment("(Container = ? AND " + getParticipantColumnName() + " IN (");
                    condition.add(sourceStudy.getContainer());
                    String comma = "";
                    for (String ptid : ptids)
                    {
                        condition.append(comma).append("?");
                        condition.add(ptid);
                        comma = ", ";
                    }
                    condition.append(")) OR Container = ?");
                    condition.add(currentStudy.getContainer());
                    addCondition(condition, "Container", getParticipantColumnName());
                }
            }
        }
        _schema = schema;
    }

    protected String getParticipantColumnName()
    {
        return null;
    }

    protected ColumnInfo addWrapParticipantColumn(String rootTableColumnName)
    {
        String subjectColName = StudyService.get().getSubjectColumnName(getContainer());
        ColumnInfo participantColumn = new AliasedColumn(this, subjectColName, _rootTable.getColumn(rootTableColumnName));
        participantColumn.setFk(new QueryForeignKey(_schema, StudyService.get().getSubjectTableName(getContainer()), subjectColName, null));
        participantColumn.setKeyField(true);

        if (DemoMode.isDemoMode(_schema.getContainer(), _schema.getUser()))
        {
            participantColumn.setDisplayColumnFactory(new DisplayColumnFactory() {
                @Override
                public DisplayColumn createRenderer(ColumnInfo column)
                {
                    return new PtidObfuscatingDisplayColumn(column);
                }
            });
        }

        return addColumn(participantColumn);
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        // The name of the subject ID column can now be customized by study.  Since there are some joins
        // from the assay side which join to cross-study data, we need a single column name that works
        // in all containers.  This is 'ParticipantId', since this was the column name before
        // customization was possible:
        if ("ParticipantId".equalsIgnoreCase(name))
            return getColumn(StudyService.get().getSubjectColumnName(getContainer()));
        return null;
    }

    protected ColumnInfo addWrapLocationColumn(String wrappedName, String rootTableColumnName)
    {
        ColumnInfo locationColumn = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        locationColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                SiteTable result = new SiteTable(_schema);
                result.setContainerFilter(ContainerFilter.EVERYTHING);
                return result;
            }
        });
        return addColumn(locationColumn);
    }

    protected ColumnInfo addWrapTypeColumn(String wrappedName, final String rootTableColumnName)
    {
        ColumnInfo typeColumn = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        LookupForeignKey fk = new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                BaseStudyTable result;
                if (rootTableColumnName.equals("PrimaryTypeId"))
                    result = new PrimaryTypeTable(_schema);
                else if (rootTableColumnName.equals("DerivativeTypeId") || rootTableColumnName.equals("DerivativeTypeId2"))
                    result = new DerivativeTypeTable(_schema);
                else if (rootTableColumnName.equals("AdditiveTypeId"))
                    result = new AdditiveTypeTable(_schema);
                else
                    throw new IllegalStateException(rootTableColumnName + " is not recognized as a valid specimen type column.");
                result.setContainerFilter(ContainerFilter.EVERYTHING);
                return result;
            }
        };
        typeColumn.setFk(fk);

        return addColumn(typeColumn);
    }


    protected ColumnInfo addSpecimenVisitColumn(TimepointType timepointType)
    {
        ColumnInfo visitColumn = null;
        ColumnInfo visitDescriptionColumn = addWrapColumn(_rootTable.getColumn("VisitDescription"));

        // add the sequenceNum column so we have it for later queries
        ColumnInfo sequenceNumColumn = addColumn(new AliasedColumn(this, "SequenceNum", _rootTable.getColumn("VisitValue")));
        sequenceNumColumn.setHidden(true);

        if (timepointType == TimepointType.DATE || timepointType == TimepointType.CONTINUOUS)
        {
            //consider:  use SequenceNumMin for visit-based studies too (in visit-based studies VisitValue == SequenceNumMin)
            // could change to visitrowid but that changes datatype and displays rowid
            // instead of sequencenum when label is null
            visitColumn = addColumn(new ParticipantVisitColumn(this));
            visitColumn.setLabel("Timepoint");

            visitDescriptionColumn.setHidden(true);
        }
        else if (timepointType == TimepointType.VISIT)
        {
            visitColumn = addColumn(new ParticipantVisitColumn(this));
        }

        LookupForeignKey visitFK = new LookupForeignKey(null, (String) null, "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                VisitTable visitTable = new VisitTable(_schema);
                visitTable.setContainerFilter(ContainerFilter.EVERYTHING);
                return visitTable;
            }
        };
        visitFK.addJoin(getColumn("Container"), "Folder");
        visitColumn.setFk(visitFK);
        visitColumn.setKeyField(true);

        visitColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo col)
            {
                return new VisitDisplayColumn(col, FieldKey.fromParts("Visit", "SequenceNumMin"));
            }
        });

        return visitColumn;
    }

    public static class VisitDisplayColumn extends DataColumn
    {
        private FieldKey _seqNumMinFieldKey;

        public VisitDisplayColumn(ColumnInfo col, FieldKey seqNumMinFieldKey)
        {
            super(col);
            _seqNumMinFieldKey = seqNumMinFieldKey;
        }

        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = ctx.get(getDisplayColumn().getFieldKey());
            if (value == null)
            {
                value = ctx.get(_seqNumMinFieldKey);

                if (value == null)
                    return super.getFormattedValue(ctx);
            }
            return PageFlowUtil.filter(value);
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(_seqNumMinFieldKey);
        }
    }

    private static class ParticipantVisitColumn extends ExprColumn
    {
        public ParticipantVisitColumn(TableInfo parent)
        {
            super(parent, "Visit", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "$PV" + ".VisitRowId"), JdbcType.INTEGER);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            String pvAlias = parentAlias + "$PV";
            SQLFragment join = new SQLFragment();

            join.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoParticipantVisit(), pvAlias).append(" ON\n");
            join.append(parentAlias).append(".ParticipantSequenceKey = ").append(pvAlias).append(".ParticipantSequenceKey AND\n");
            join.append(parentAlias).append(".Container = ").append(pvAlias).append(".Container");

            map.put(pvAlias, join);
        }
    }


/*
    private static class DateVisitColumn extends ExprColumn
    {
        private static final String DATE_VISIT_JOIN_ALIAS = "DateVisitJoin";
        public DateVisitColumn(TableInfo parent)
        {
            super(parent, "Visit", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "$" + DATE_VISIT_JOIN_ALIAS + ".SequenceNumMin"), JdbcType.VARCHAR);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            String pvAlias = parentAlias + "$PV";
            String dateVisitJoinAlias = parentAlias + "$" + DATE_VISIT_JOIN_ALIAS;
            SQLFragment join = new SQLFragment();
            join.append(" LEFT OUTER JOIN " + StudySchema.getInstance().getTableInfoParticipantVisit() + " " + pvAlias + " ON\n" +
                    parentAlias + ".ParticipantSequenceKey = " + pvAlias + ".ParticipantSequenceKey AND\n" +
                    parentAlias + ".Container = " + pvAlias + ".Container\n");
            join.append("LEFT OUTER JOIN " + StudySchema.getInstance().getTableInfoVisit() + " " + dateVisitJoinAlias +
                    " ON " + dateVisitJoinAlias + ".RowId = " + pvAlias + ".VisitRowId");
            map.put(DATE_VISIT_JOIN_ALIAS, join);
        }
    }
*/

    protected void addVialCommentsColumn(final boolean joinBackToSpecimens)
    {
        ColumnInfo commentsColumn = new AliasedColumn(this, "VialComments", _rootTable.getColumn("GlobalUniqueId"));
        LookupForeignKey commentsFK = new LookupForeignKey("GlobalUniqueId")
        {
            public TableInfo getLookupTableInfo()
            {
                SpecimenCommentTable result = new SpecimenCommentTable(_schema, joinBackToSpecimens);
                result.setContainerFilter(ContainerFilter.EVERYTHING);
                return result;
            }
        };
        commentsFK.addJoin(getColumn("Container"), "Folder");
        commentsColumn.setFk(commentsFK);
        commentsColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new CommentDisplayColumn(colInfo);
            }
        });
        addColumn(commentsColumn);
    }

    protected ColumnInfo createSpecimenCommentColumn(StudyQuerySchema schema, boolean includeVialComments)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(_schema.getContainer());

        DataSetDefinition defPtid = null;
        DataSetDefinition defPtidVisit = null;

        if (study.getParticipantCommentDataSetId() != null)
            defPtid = StudyManager.getInstance().getDataSetDefinition(study, study.getParticipantCommentDataSetId());
        if (study.getParticipantVisitCommentDataSetId() != null)
            defPtidVisit = StudyManager.getInstance().getDataSetDefinition(study, study.getParticipantVisitCommentDataSetId());

        boolean validParticipantCommentTable = defPtid != null && defPtid.canRead(schema.getUser()) && defPtid.isDemographicData();
        TableInfo participantCommentTable = validParticipantCommentTable ? defPtid.getTableInfo(schema.getUser()) : null;
        boolean validParticipantVisitCommentTable = defPtidVisit != null && defPtidVisit.canRead(schema.getUser()) && !defPtidVisit.isDemographicData();
        TableInfo participantVisitCommentTable = validParticipantVisitCommentTable ? defPtidVisit.getTableInfo(schema.getUser()) : null;

        return new SpecimenCommentColumn(this, participantCommentTable, study.getParticipantCommentProperty(),
                participantVisitCommentTable, study.getParticipantVisitCommentProperty(), includeVialComments);
    }

    public static class SpecimenCommentColumn extends ExprColumn
    {
        public static final String COLUMN_NAME = "SpecimenComment";
        protected static final String VIAL_COMMENT_ALIAS = "VialComment";
        protected static final String PARTICIPANT_COMMENT_JOIN = "ParticipantCommentJoin$";
        protected static final String PARTICIPANT_COMMENT_ALIAS = "ParticipantComment";
        protected static final String PARTICIPANTVISIT_COMMENT_JOIN = "ParticipantVisitCommentJoin$";
        protected static final String PARTICIPANTVISIT_COMMENT_ALIAS = "ParticipantVisitComment";
        protected static final String SPECIMEN_COMMENT_JOIN = "SpecimenCommentJoin$";

        private TableInfo _ptidCommentTable;
        private TableInfo _ptidVisitCommentTable;
        private boolean _includeVialComments;
        private Container _container;

        public SpecimenCommentColumn(FilteredTable parent, TableInfo ptidCommentTable, String ptidCommentProperty,
                                     TableInfo ptidVisitCommentTable, String ptidVisitCommentProperty, boolean includeVialComments)
        {
            super(parent, COLUMN_NAME, new SQLFragment(), JdbcType.VARCHAR);

            _container = parent.getContainer();
            _ptidCommentTable = ptidCommentTable;
            _ptidVisitCommentTable = ptidVisitCommentTable;
            _includeVialComments = includeVialComments;
            SQLFragment sql = new SQLFragment();
            String ptidCommentAlias = null;
            String ptidVisitCommentAlias = null;

            if (ptidCommentProperty != null && _ptidCommentTable != null)
            {
                if (_ptidCommentTable.getColumn(ptidCommentProperty) != null)
                    ptidCommentAlias = ColumnInfo.legalNameFromName(ptidCommentProperty);
            }

            if (ptidVisitCommentProperty != null && _ptidVisitCommentTable != null)
            {
                if (_ptidVisitCommentTable.getColumn(ptidVisitCommentProperty) != null)
                    ptidVisitCommentAlias = ColumnInfo.legalNameFromName(ptidVisitCommentProperty);
            }

            List<String> commentFields = new ArrayList();

            if (_includeVialComments)
            {
                String field = ExprColumn.STR_TABLE_ALIAS + "$" + SPECIMEN_COMMENT_JOIN + ".Comment";

                sql.append(field).append(" AS " + VIAL_COMMENT_ALIAS + ",\n");
                commentFields.add(field);
            }
            if (ptidCommentTable != null && ptidCommentAlias != null)
            {
                String field = ExprColumn.STR_TABLE_ALIAS + "$" + PARTICIPANT_COMMENT_JOIN + "." + ptidCommentAlias;

                sql.append(field).append(" AS " + PARTICIPANT_COMMENT_ALIAS + ",\n");
                commentFields.add(field);
            }
            if (ptidVisitCommentTable != null && ptidVisitCommentAlias != null)
            {
                String field = ExprColumn.STR_TABLE_ALIAS + "$" + PARTICIPANTVISIT_COMMENT_JOIN + "." + ptidVisitCommentAlias;

                sql.append(field).append(" AS " + PARTICIPANTVISIT_COMMENT_ALIAS + ",\n");
                commentFields.add(field);
            }

            StringBuilder sb = new StringBuilder();
            if (!commentFields.isEmpty())
            {
                switch (commentFields.size())
                {
                    case 1:
                        sb.append(commentFields.get(0));
                        break;
                    case 2:
                        sb.append("CASE");
                        appendCommentCaseSQL(sb, commentFields.get(0), commentFields.get(1));
                        sb.append(" ELSE CAST(COALESCE(").append(commentFields.get(0)).append(',').append(commentFields.get(1)).append(")AS VARCHAR) END");
                        break;
                    case 3:
                        sb.append("CASE");
                        appendCommentCaseSQL(sb, commentFields.get(0), commentFields.get(1), commentFields.get(2));
                        appendCommentCaseSQL(sb, commentFields.get(0), commentFields.get(1));
                        appendCommentCaseSQL(sb, commentFields.get(0), commentFields.get(2));
                        appendCommentCaseSQL(sb, commentFields.get(1), commentFields.get(2));
                        sb.append(" ELSE CAST(COALESCE(").append(commentFields.get(0)).append(',').append(commentFields.get(1)).append(',').append(commentFields.get(2)).append(")AS VARCHAR) END");
                        break;
                }
            }
            sql.append("(");
            if (sb.length() > 0)
                sql.append(sb.toString());
            else
                sql.append("' '");
            sql.append(")");
            setValueSQL(sql);
        }

        private void appendCommentCaseSQL(StringBuilder sb, String... fields)
        {
            String concat = "";

            sb.append(" WHEN ");
            for (String field : fields)
            {
                sb.append(concat).append(field).append(" IS NOT NULL ");
                concat = "AND ";
            }

            sb.append("THEN ");

            String[] castFields = new String[fields.length];

            for (int i = 0; i < fields.length; i++)
                castFields[i] = "CAST((" + fields[i] + ") AS VARCHAR)";

            sb.append(getSqlDialect().concatenate(castFields));
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + SPECIMEN_COMMENT_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            if (_includeVialComments)
            {
                joinSql.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoSpecimenComment().getFromSQL(tableAlias));
                joinSql.append(" ON ");
                joinSql.append(parentAlias).append(".GlobalUniqueId = ").append(tableAlias).append(".GlobalUniqueId AND ");
                joinSql.append(parentAlias).append(".Container = ").append(tableAlias).append(".Container\n");
            }

            if (_ptidCommentTable != null)
            {
                String ptidTableAlias = parentAlias + "$" + PARTICIPANT_COMMENT_JOIN;

                joinSql.append(" LEFT OUTER JOIN ").append(_ptidCommentTable.getFromSQL(ptidTableAlias));
                joinSql.append(" ON ");
                joinSql.append(parentAlias).append(".Ptid = ").append(
                        _ptidCommentTable.getColumn(StudyService.get().getSubjectColumnName(_container)).getValueSql(ptidTableAlias) + "\n");
            }

            if (_ptidVisitCommentTable != null)
            {
                String ptidTableAlias = parentAlias + "$" + PARTICIPANTVISIT_COMMENT_JOIN;

                joinSql.append(" LEFT OUTER JOIN ").append(_ptidVisitCommentTable.getFromSQL(ptidTableAlias));
                joinSql.append(" ON ");
                joinSql.append(parentAlias).append(".ParticipantSequenceKey = ").append(ptidTableAlias).append(".ParticipantSequenceKey");
            }
            map.put(tableAlias, joinSql);
        }
    }

    public static class CommentDisplayColumn extends DataColumn
    {
        public CommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value == null)
                return "";
            else
                return value;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value != null  && value instanceof String)
                out.write((String) value);
        }
    }

    public static class SpecimenCommentDisplayColumn extends DataColumn
    {
        public SpecimenCommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write(formatParticipantComments(ctx, "<br>"));
        }
    }

    protected static String formatParticipantComments(RenderContext ctx, String lineSeparator)
    {
        Map<String, Object> row = ctx.getRow();

        Object vialComment = row.get(SpecimenCommentColumn.VIAL_COMMENT_ALIAS);
        Object participantComment = row.get(SpecimenCommentColumn.PARTICIPANT_COMMENT_ALIAS);
        Object participantVisitComment = row.get(SpecimenCommentColumn.PARTICIPANTVISIT_COMMENT_ALIAS);

        StringBuilder sb = new StringBuilder();

        if (vialComment instanceof String)
        {
            sb.append("<i>Vial:&nbsp;</i>");
            sb.append(PageFlowUtil.filter(vialComment));
            sb.append(lineSeparator);
        }
        String subjectNoun = StudyService.get().getSubjectNounSingular(ctx.getContainer());
        if (participantComment instanceof String)
        {
            if (sb.length() > 0)
                sb.append(lineSeparator);
            sb.append("<i>").append(PageFlowUtil.filter(subjectNoun)).append(":&nbsp;</i>");
            sb.append(PageFlowUtil.filter(participantComment));
            sb.append(lineSeparator);
        }
        if (participantVisitComment instanceof String)
        {
            if (sb.length() > 0)
                sb.append(lineSeparator);
            sb.append("<i>").append(PageFlowUtil.filter(subjectNoun)).append("/Visit:&nbsp;</i>");
            sb.append(PageFlowUtil.filter(participantVisitComment));
            sb.append(lineSeparator);
        }
        return sb.toString();
    }


    /* NOTE getUpdateService() and hasPermission() should usually be overridden together */

    @Override
    public QueryUpdateService getUpdateService()
    {
        return null;
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return false;
    }

    protected boolean canReadOrIsAdminPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return ReadPermission.class == perm && _schema.getContainer().hasPermission(user, perm) ||
                _schema.getContainer().hasPermission(user, AdminPermission.class);
    }
}