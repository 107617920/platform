/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.data.*;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.SpecimenImporter.SpecimenColumn;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 3:49:32 PM
 */
class SpecimenWriter implements Writer<StudyImpl, StudyExportContext>
{
    public String getSelectionText()
    {
        return null;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        Collection<SpecimenColumn> columns = SpecimenImporter.SPECIMEN_COLUMNS;
        StudySchema schema = StudySchema.getInstance();
        StudyQuerySchema querySchema = new StudyQuerySchema(study, ctx.getUser(), true); // to use for checking overlayed XMl metadata
        Container c = ctx.getContainer();

        PrintWriter pw = vf.getPrintWriter("specimens.tsv");

        pw.println("# specimens");

        SQLFragment sql = new SQLFragment().append("\nSELECT ");
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>(columns.size());
        List<ColumnInfo> selectColumns = new ArrayList<ColumnInfo>(columns.size());
        String comma = "";

        for (SpecimenColumn column : columns)
        {
            SpecimenImporter.TargetTable tt = column.getTargetTable();
            TableInfo tinfo = tt.isEvents() ? schema.getTableInfoSpecimenEvent() : schema.getTableInfoSpecimenDetail();
            TableInfo queryTable = tt.isEvents() ? querySchema.getTable("SpecimenEvent") : querySchema.getTable("SpecimenDetail");
            ColumnInfo ci = tinfo.getColumn(column.getDbColumnName());
            DataColumn dc = new DataColumn(ci);
            selectColumns.add(dc.getDisplayColumn());
            dc.setCaption(column.getTsvColumnName());
            displayColumns.add(dc);

            sql.append(comma);

            // export alternate ID in place of Ptid if set in StudyExportContext
            if (ctx.isAlternateIds() && column.getDbColumnName().equals("Ptid"))
            {
                sql.append("ParticipantLookup.AlternateId AS Ptid");
            }
            else if (null == column.getFkColumn())
            {
                // Note that columns can be events, vials, or specimens (grouped vials); the SpecimenDetail view that's
                // used for export joins vials and specimens into a single view which we're calling 's'.  isEvents catches
                // those columns that are part of the events table, while !isEvents() catches the rest.  (equivalent to
                // isVials() || isSpecimens().)
                String col = (tt.isEvents() ? "se." : "s.") + column.getDbColumnName();

                // add expression to shift the date columns
                if (ctx.isShiftDates() && column.isDateType())
                {
                    col = "{fn timestampadd(SQL_TSI_DAY, -ParticipantLookup.DateOffset, " + col + ")} AS " + column.getDbColumnName();
                }

                // don't export values for columns set as Protected in the XML metadata override
                if (ctx.isRemoveProtected() && queryTable != null && queryTable.getColumn(column.getDbColumnName()) != null
                        && queryTable.getColumn(column.getDbColumnName()).isProtected())
                {
                    col = "NULL AS " + column.getDbColumnName();
                }

                sql.append(col);
            }
            else
            {
                sql.append(column.getFkTableAlias()).append(".").append(column.getFkColumn());
                sql.append(" AS ").append(dc.getDisplayColumn().getAlias());  // DisplayColumn will use getAlias() to retrieve the value from the map
            }

            comma = ", ";
        }

        sql.append("\nFROM ").append(schema.getTableInfoSpecimenEvent()).append(" se JOIN ").append(schema.getTableInfoSpecimenDetail()).append(" s ON se.VialId = s.RowId");

        for (SpecimenColumn column : columns)
        {
            if (null != column.getFkColumn())
            {
                assert column.getTargetTable().isEvents();

                sql.append("\n    ");
                if (column.getJoinType() != null)
                    sql.append(column.getJoinType()).append(" ");
                sql.append("JOIN study.").append(column.getFkTable()).append(" AS ").append(column.getFkTableAlias()).append(" ON ");
                sql.append("(se.");
                sql.append(column.getDbColumnName()).append(" = ").append(column.getFkTableAlias()).append(".RowId)");
            }
        }

        // add join to study.Participant table if we are using alternate IDs or shifting dates
        if (ctx.isAlternateIds() || ctx.isShiftDates())
        {
            sql.append("\n    LEFT JOIN study.Participant AS ParticipantLookup ON (se.Ptid = ParticipantLookup.ParticipantId AND se.Container = ParticipantLookup.Container)");
        }
        // add join to study.ParticipantVisit table if we are filtering by visit IDs
        if (ctx.getVisitIds() != null && !ctx.getVisitIds().isEmpty())
        {
            sql.append("\n    LEFT JOIN study.ParticipantVisit AS ParticipantVisitLookup ON (s.Ptid = ParticipantVisitLookup.ParticipantId AND s.ParticipantSequenceNum = ParticipantVisitLookup.ParticipantSequenceNum AND s.Container = ParticipantVisitLookup.Container)");
        }

        sql.append("\nWHERE se.Container = ? ");

        // add filter for selected participant IDs and Visits IDs
        if (ctx.getVisitIds() != null && !ctx.getVisitIds().isEmpty())
        {
            sql.append("\n AND ParticipantVisitLookup.VisitRowId IN (");
            sql.append(convertListToString(new ArrayList<Integer>(ctx.getVisitIds()), false));
            sql.append(")");
        }
        if (!ctx.getParticipants().isEmpty())
        {
            if (ctx.isAlternateIds())
                sql.append("\n AND ParticipantLookup.AlternateId IN (");
            else
                sql.append("\n AND se.Ptid IN (");

            sql.append(convertListToString(ctx.getParticipants(), true));
            sql.append(")");
        }

        sql.append("\nORDER BY se.ExternalId");
        sql.add(c);

        ResultSet rs = null;
        TSVGridWriter gridWriter = null;
        try
        {
            // Note: must be uncached result set -- this query can be very large
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql.getSQL(), sql.getParamsArray(), Table.ALL_ROWS, false);

            gridWriter = new TSVGridWriter(new ResultsImpl(rs, selectColumns), displayColumns);
            gridWriter.write(pw);
        }
        finally
        {
            if (gridWriter != null)
                gridWriter.close();  // Closes ResultSet and PrintWriter
            else if (rs != null)
                rs.close();
        }
    }

    private String convertListToString(List list, boolean withQuotes)
    {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (Object obj : list)
        {
            sb.append(sep);
            if (withQuotes) sb.append("'");
            sb.append(obj.toString());
            if (withQuotes) sb.append("'");
            sep = ",";
        }
        return sb.toString();
    }
}
