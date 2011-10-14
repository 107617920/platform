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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.study.StudyService;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.model.SpecimenComment;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SpecimenSummaryTable extends BaseStudyTable
{
    public SpecimenSummaryTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenSummary(), true);
        addWrapParticipantColumn("PTID").setKeyField(true);
        addWrapColumn(_rootTable.getColumn("Container")).setFk(new ContainerForeignKey());

        addSpecimenVisitColumn(_schema.getStudy().getTimepointType());

        ColumnInfo pvColumn = new AliasedColumn(this, StudyService.get().getSubjectVisitColumnName(schema.getContainer()),
                _rootTable.getColumn("ParticipantSequenceKey"));//addWrapColumn(baseColumn);
        pvColumn.setFk(new LookupForeignKey("ParticipantSequenceKey")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ParticipantVisitTable(_schema);
            }
        });
        pvColumn.setIsUnselectable(true);
        addColumn(pvColumn);

        addWrapColumn(_rootTable.getColumn("TotalVolume"));
        addWrapColumn(_rootTable.getColumn("AvailableVolume"));
        addWrapColumn(_rootTable.getColumn("VolumeUnits"));
        addWrapTypeColumn("PrimaryType", "PrimaryTypeId");
        addWrapTypeColumn("DerivativeType", "DerivativeTypeId");
        addWrapTypeColumn("AdditiveType", "AdditiveTypeId");
        addWrapTypeColumn("DerivativeType2", "DerivativeTypeId2");
        addWrapLocationColumn("Clinic", "OriginatingLocationId");
        addWrapLocationColumn("ProcessingLocation", "ProcessingLocation");
        addWrapColumn(_rootTable.getColumn("FirstProcessedByInitials"));
        addWrapColumn(_rootTable.getColumn("SubAdditiveDerivative"));
        addWrapColumn(_rootTable.getColumn("VialCount"));
        addWrapColumn(_rootTable.getColumn("LockedInRequestCount"));
        addWrapColumn(_rootTable.getColumn("AtRepositoryCount"));
        addWrapColumn(_rootTable.getColumn("AvailableCount"));
        addWrapColumn(_rootTable.getColumn("ExpectedAvailableCount"));
        addWrapColumn(_rootTable.getColumn("DrawTimestamp"));
        addWrapColumn(_rootTable.getColumn("SalReceiptDate"));
        addWrapColumn(_rootTable.getColumn("ClassId"));
        addWrapColumn(_rootTable.getColumn("ProtocolNumber"));
        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setHidden(true);

        // Create an ExprColumn to get the max *possible* comments for each specimen.  It's only the possible number
        // (rather than the actual number), because a specimennumber isn't sufficient to identify a row in the specimen
        // summary table; derivative and additive types are required as well.  We use this number so we know if additional
        // (more expensive) queries are required to check for actual comments in the DB for each row.
        SQLFragment sqlFragComments = new SQLFragment("(SELECT CAST(COUNT(*) AS VARCHAR(5)) FROM " +
                StudySchema.getInstance().getTableInfoSpecimenComment() +
                " WHERE SpecimenHash = " + ExprColumn.STR_TABLE_ALIAS + ".SpecimenHash" +
                " AND Container = ?)");
        sqlFragComments.add(getContainer().getId());
        //  Set this column type to string so that exports to excel correctly set the column type as string.
        // (We're using a custom display column to output the text of the comment in this col, even though
        // the SQL expression returns an integer.)
        ColumnInfo commentsCol = addColumn(new ExprColumn(this, "Comments", sqlFragComments, JdbcType.VARCHAR));
        commentsCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new CommentDisplayColumn(colInfo);
            }
        });

        ColumnInfo specimenComment = createSpecimenCommentColumn(_schema, false);
        specimenComment.setHidden(true);
        specimenComment.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new SpecimenCommentDisplayColumn(colInfo);
            }
        });
        addColumn(specimenComment);
        
        // use sql aggregates to 'OR' together the conflict bits of the vials associated with this specimen hash:
        SQLFragment sqlFragConflicts = new SQLFragment("(SELECT CASE WHEN COUNT(QualityControlFlag) = 0 OR " +
                "SUM(CAST(QualityControlFlag AS INT)) = 0 THEN ? ELSE ? END FROM " +
                StudySchema.getInstance().getTableInfoSpecimenComment() +
                " WHERE SpecimenHash = " + ExprColumn.STR_TABLE_ALIAS + ".SpecimenHash" +
                " AND Container = ?)");
        sqlFragConflicts.add(Boolean.FALSE);
        sqlFragConflicts.add(Boolean.TRUE);
        sqlFragConflicts.add(getContainer().getId());
        //  Set this column type to string so that exports to excel correctly set the column type as string.
        // (We're using a custom display column to output the text of the comment in this col, even though
        // the SQL expression returns an integer.)
        addColumn(new ExprColumn(this, "QualityControlFlag", sqlFragConflicts, JdbcType.BOOLEAN));
    }

    @Override
    protected String getParticipantColumnName()
    {
        return "PTID";
    }

    public static class CommentDisplayColumn extends DataColumn
    {
        private ColumnInfo _specimenHashColumn;

        public CommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
            setWidth("200px");
        }

        public boolean isFilterable()
        {
            return false;
        }

        public boolean isSortable()
        {
            return false;
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            FieldKey me = getBoundColumn().getFieldKey();
            FieldKey specimenHashKey = new FieldKey(me.getParent(), "SpecimenHash");
            // select the base 'comments' column (our bound column is an exprcolumn that doesn't simply select the base value):
            FieldKey commentsHashKey = new FieldKey(me.getParent(), "Comments");
            FieldKey participantCommentKey = new FieldKey(me.getParent(), SpecimenCommentColumn.COLUMN_NAME);
            Set<FieldKey> fieldKeys = new HashSet<FieldKey>();
            fieldKeys.add(specimenHashKey);
            fieldKeys.add(commentsHashKey);
            fieldKeys.add(participantCommentKey);
            Map<FieldKey, ColumnInfo> requiredColumns = QueryService.get().getColumns(getBoundColumn().getParentTable(), fieldKeys);
            _specimenHashColumn = requiredColumns.get(specimenHashKey);
            if (_specimenHashColumn != null)
                columns.add(_specimenHashColumn);
            ColumnInfo col = requiredColumns.get(commentsHashKey);
            if (col != null)
                columns.add(col);
            ColumnInfo participantCommentCol = requiredColumns.get(participantCommentKey);
            if (participantCommentCol != null)
                columns.add(participantCommentCol);
        }

        private Map<String, String> _commentCache;

        private void addComments(Container container, Set<String> hashes, Map<String, List<SpecimenComment>> hashToComments) throws SQLException
        {
            SpecimenComment[] comments = SampleManager.getInstance().getSpecimenCommentForSpecimens(container, hashes);
            for (SpecimenComment comment : comments)
            {
                List<SpecimenComment> commentList = hashToComments.get(comment.getSpecimenHash());
                if (commentList == null)
                {
                    commentList = new ArrayList<SpecimenComment>();
                    hashToComments.put(comment.getSpecimenHash(), commentList);
                }
                commentList.add(comment);
            }

        }

        private Map<String, String> getCommentCache(RenderContext ctx, String lineSeparator) throws SQLException
        {
            ResultSet rs = ctx.getResultSet();
            if (_commentCache == null && rs instanceof Table.TableResultSet)
            {
                Table.TableResultSet tableRs = (Table.TableResultSet) rs;
                Set<String> hashes = new HashSet<String>();
                Map<String, List<SpecimenComment>> hashToComments = new HashMap<String, List<SpecimenComment>>();
                for (Iterator<Map<String, Object>> it = tableRs.iterator(); it.hasNext(); )
                {
                    Map<String, Object> row = it.next();
                    String maxPossibleCount = (String) row.get("Comments");
                    if (maxPossibleCount != null && !"0".equals(maxPossibleCount))
                        hashes.add((String) row.get("SpecimenHash"));

                    if (hashes.size() >= 1000)
                    {
                        addComments(ctx.getContainer(), hashes, hashToComments);
                        hashes.clear();
                    }
                }
                addComments(ctx.getContainer(), hashes, hashToComments);

                _commentCache = new HashMap<String, String>();
                for (Map.Entry<String, List<SpecimenComment>> entry : hashToComments.entrySet())
                {
                    List<SpecimenComment> commentList = entry.getValue();
                    String formatted = formatCommentText(commentList.toArray(new SpecimenComment[commentList.size()]), lineSeparator);
                    _commentCache.put(entry.getKey(), formatted);
                }
            }
            return _commentCache;
        }

        private String formatCommentText(SpecimenComment[] comments, String lineSeparator)
        {
            StringBuilder builder = new StringBuilder();
            if (comments != null && comments.length > 0)
            {
                Map<String, List<String>> commentToIds = new TreeMap<String, List<String>>();
                for (SpecimenComment comment : comments)
                {
                    if (comment.getComment() != null)
                    {
                        List<String> ids = commentToIds.get(comment.getComment());
                        if (ids == null)
                        {
                            ids = new ArrayList<String>();
                            commentToIds.put(comment.getComment(), ids);
                        }
                        ids.add(comment.getGlobalUniqueId());
                    }
                }
                String tempSep = "";
                for (Map.Entry<String, List<String>> entry : commentToIds.entrySet())
                {
                    builder.append(tempSep);
                    builder.append(entry.getValue().size()).append(" vial");
                    if (entry.getValue().size() > 1)
                        builder.append("s");
                    builder.append(": ").append(entry.getKey());
                    tempSep = lineSeparator;
                }
            }
            return builder.toString();
        }

        private String getCommentText(RenderContext ctx, String specimenHash, String lineSeparator) throws SQLException
        {
            Map<String, String> commentCache = getCommentCache(ctx, lineSeparator);
            if (commentCache != null)
                return commentCache.get(specimenHash);
            else
            {
                // we must not have a cached resultset, so we couldn't get the full set of comments efficiently; we'll select
                // comments for each row:
                SpecimenComment[] comments = SampleManager.getInstance().getSpecimenCommentForSpecimen(ctx.getContainer(), specimenHash);
                return formatCommentText(comments, lineSeparator);
            }
        }

        private String getDisplayText(RenderContext ctx, String lineSeparator)
        {
            if (_specimenHashColumn == null)
                return "ERROR: SpecimenHash column must be added to query to retrive comment information.";

            String maxPossibleCount = (String) getValue(ctx);
            StringBuilder sb = new StringBuilder();
            // the string compare below is a big of a hack, but it's cheaper than converting the string to a number and
            // equally effective.  The column type is string so that exports to excel correctly set the column type as string.

            if (maxPossibleCount != null && !"0".equals(maxPossibleCount))
            {
                try
                {
                    sb.append(getCommentText(ctx, ctx.getResultSet().getString("SpecimenHash"), lineSeparator));
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
            if (sb.length() > 0)
                sb.append(lineSeparator);
            sb.append(formatParticipantComments(ctx, lineSeparator));

            return sb.toString();
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            return getDisplayText(ctx, ", ");
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write(getDisplayText(ctx, "<br>"));
        }
    }
}
