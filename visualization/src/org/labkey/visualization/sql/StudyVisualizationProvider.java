/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.visualization.sql;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.DataSetTable;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.visualization.VisualizationController;

import java.util.*;

/**
 * User: brittp
 * Date: Jan 26, 2011 5:10:03 PM
 */
public class StudyVisualizationProvider extends VisualizationProvider
{
    public StudyVisualizationProvider()
    {
        super("study");
    }

    @Override
    public void addExtraSelectColumns(VisualizationSourceColumn.Factory factory, VisualizationSourceQuery query)
    {
        if (getType() == VisualizationSQLGenerator.ChartType.TIME_VISITBASED)
        {
            // add the visit sequencenum, label, and display order to the select list
            String subjectNounSingular = StudyService.get().getSubjectNounSingular(query.getContainer());
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/sequencenum", true), false);
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/Visit/Label", true), false);
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/Visit/DisplayOrder", true), false);
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/VisitDate", true), false);
            query.addSelect(factory.create(query.getSchema(), query.getQueryName(), subjectNounSingular + "Visit/Visit", true), false);
        }
    }

    @Override
    public boolean isJoinColumn(VisualizationSourceColumn column, Container container)
    {
        String subjectColName = StudyService.get().getSubjectColumnName(container).toLowerCase();
        String subjectVisitName = StudyService.get().getSubjectVisitColumnName(container).toLowerCase() + "/";

        String name = column.getOriginalName().toLowerCase();

        if (subjectColName.equals(name) || name.startsWith(subjectVisitName))
            return true;

        return false;
    }

    @Override
    public void appendAggregates(StringBuilder sql, Map<String, Set<VisualizationSourceColumn>> columnAliases, Map<String, VisualizationIntervalColumn> intervals, String queryAlias, IVisualizationSourceQuery joinQuery)
    {
        for (Map.Entry<String, VisualizationIntervalColumn> entry : intervals.entrySet())
        {
            sql.append(", ");
            sql.append(queryAlias);
            sql.append(".");
            sql.append(entry.getValue().getSQLAlias(intervals.size()));
        }

        Container container = joinQuery.getContainer();
        String subjectColumnName = StudyService.get().getSubjectNounSingular(container);

        if (getType() == VisualizationSQLGenerator.ChartType.TIME_VISITBASED)
        {
            for (String s : Arrays.asList("Visit/Visit/DisplayOrder","Visit/sequencenum","Visit/Visit/Label","Visit/Visit"))
            {
                VisualizationSourceColumn col = columnAliases.get(subjectColumnName + s).iterator().next();
                sql.append(", ");
                sql.append(queryAlias);
                sql.append(".");
                sql.append(col.getSQLAlias());
                if (null != col.getLabel())
                    sql.append(" @title='" + StringUtils.replace(col.getLabel(),"'","''") + "'");
            }
        }
    }

    @Override
    public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinColumns(VisualizationSourceColumn.Factory factory, VisualizationSourceQuery first, IVisualizationSourceQuery second)
    {
        if (!first.getContainer().equals(second.getContainer()))
            throw new IllegalArgumentException("Can't yet join across containers.");

        List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinCols = new ArrayList<Pair<VisualizationSourceColumn, VisualizationSourceColumn>>();

        String firstSubjectColumnName = StudyService.get().getSubjectColumnName(first.getContainer());
        String firstSubjectNounSingular = StudyService.get().getSubjectNounSingular(first.getContainer());
        // allow null results for this column so as to follow the lead of the primary measure column for this query:
        VisualizationSourceColumn firstSubjectCol = factory.create(first.getSchema(), first.getQueryName(), firstSubjectColumnName, true);
        String secondSubjectColName = StudyService.get().getSubjectColumnName(second.getContainer());
        String secondSubjectNounSingular = StudyService.get().getSubjectNounSingular(second.getContainer());
        // allow null results for this column so as to follow the lead of the primary measure column for this query:
        VisualizationSourceColumn secondSubjectCol = factory.create(second.getSchema(), second.getQueryName(), secondSubjectColName, true);

        joinCols.add(new Pair<VisualizationSourceColumn, VisualizationSourceColumn>(firstSubjectCol, secondSubjectCol));

        // if either dataset is demographic, it's sufficient to join on subject only:
        int firstDatasetId  = StudyService.get().getDatasetId(first.getContainer(), first.getQueryName());
        int secondDatasetId  = StudyService.get().getDatasetId(second.getContainer(), second.getQueryName());
        if (firstDatasetId > -1 && secondDatasetId > -1)
        {
            DataSet firstDataSet = StudyService.get().getDataSet(first.getContainer(), firstDatasetId);
            DataSet secondDataSet = StudyService.get().getDataSet(second.getContainer(), secondDatasetId);

            if (firstDataSet.getKeyType() != DataSet.KeyType.SUBJECT && secondDataSet.getKeyType() != DataSet.KeyType.SUBJECT)
            {
                // for non-demographic datasets, join on subject/visit, allowing null results for this column so as to follow the lead of the primary measure column for this query:
                VisualizationSourceColumn firstSequenceCol;
                VisualizationSourceColumn secondSequenceCol;
                if (getType() == VisualizationSQLGenerator.ChartType.TIME_VISITBASED)
                {
                    firstSequenceCol = factory.create(first.getSchema(), first.getQueryName(), firstSubjectNounSingular + "Visit/sequencenum", true);
                    secondSequenceCol = factory.create(second.getSchema(), second.getQueryName(), secondSubjectNounSingular + "Visit/sequencenum", true);
                }
                else
                {
                    firstSequenceCol = factory.create(first.getSchema(), first.getQueryName(), firstSubjectNounSingular + "Visit/VisitDate", true);
                    secondSequenceCol = factory.create(second.getSchema(), second.getQueryName(), secondSubjectNounSingular + "Visit/VisitDate", true);
                }
                joinCols.add(new Pair<VisualizationSourceColumn, VisualizationSourceColumn>(firstSequenceCol, secondSequenceCol));

                if (firstDataSet.getKeyType() == DataSet.KeyType.SUBJECT_VISIT_OTHER && secondDataSet.getKeyType() == DataSet.KeyType.SUBJECT_VISIT_OTHER
                        && first.getPivot() == null && second.getPivot() == null && firstDataSet.hasMatchingExtraKey(secondDataSet)) 
                {
                    // for datasets with matching 3rd keys, join on subject/visit/key (if neither are pivoted), allowing null results for this column so as to follow the lead of the primary measure column for this query:
                    VisualizationSourceColumn firstKeyCol = factory.create(first.getSchema(), first.getQueryName(), firstDataSet.getKeyPropertyName(), true);
                    VisualizationSourceColumn secondKeyCol = factory.create(second.getSchema(), second.getQueryName(), secondDataSet.getKeyPropertyName(), true);
                    joinCols.add(new Pair<VisualizationSourceColumn, VisualizationSourceColumn>(firstKeyCol, secondKeyCol));
                }
            }
        }

        return joinCols;
    }

    @Override
    protected boolean isValid(TableInfo table, QueryDefinition query, ColumnMatchType type)
    {
        if (table instanceof DataSetTable)
        {if (!((DataSetTable) table).getDataSet().isShowByDefault())
                return false;
        }
        if (type == ColumnMatchType.CONFIGURED_MEASURES)
        {
            return table != null && table.getColumnNameSet().contains("ParticipantSequenceNum");
        }
        else
            return super.isValid(table, query, type);
    }

    protected Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMatchingColumns(Container container, Map<QueryDefinition, TableInfo> queries, ColumnMatchType type)
    {
        Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> matches = super.getMatchingColumns(container, queries, type);
        if (type == ColumnMatchType.DATETIME_COLS)
        {
            Study study = StudyService.get().getStudy(container);
            // for visit based studies, we will look for the participantVisit.VisitDate column and
            // if found, return that as a date measure
            if (study != null && study.getTimepointType().isVisitBased())
            {
                for (Map.Entry<QueryDefinition, TableInfo> entry : queries.entrySet())
                {
                    QueryDefinition queryDefinition = entry.getKey();
                    String visitColName = StudyService.get().getSubjectVisitColumnName(container);
                    ColumnInfo visitCol = entry.getValue().getColumn(visitColName);
                    if (visitCol != null)
                    {
                        TableInfo visitTable = visitCol.getFkTableInfo();
                        if (visitTable != null)
                        {
                            ColumnInfo visitDate = visitTable.getColumn("visitDate");
                            if (visitDate != null)
                            {
                                FieldKey fieldKey = FieldKey.fromParts(visitColName, visitDate.getName());
                                matches.put(Pair.of(fieldKey, visitDate), queryDefinition);
                            }
                        }
                    }
                }
            }
        }
        else if (type == ColumnMatchType.All_VISIBLE)
        {
            List<Pair<FieldKey, ColumnInfo>> colsToRemove = new ArrayList<Pair<FieldKey, ColumnInfo>>();
            String subjectColName = StudyService.get().getSubjectColumnName(container);
            String visitColName = StudyService.get().getSubjectVisitColumnName(container);

            // for studies we want to exclude the subject and visit columns
            for (Pair<FieldKey, ColumnInfo> pair : matches.keySet())
            {
                ColumnInfo col = pair.second;
                String columnName = col.getColumnName();
                if (subjectColName.equalsIgnoreCase(columnName) || visitColName.equalsIgnoreCase(columnName) || "DataSets".equals(columnName))
                    colsToRemove.add(pair);
            }

            for (Pair<FieldKey, ColumnInfo> pair : colsToRemove)
                matches.remove(pair);
        }
        return matches;
    }

    @Override
    protected Set<String> getTableNames(UserSchema schema)
    {
        Set<String> tables = new HashSet<String>(super.getTableNames(schema));
        tables.remove("StudyData");
        return tables;
    }

    @Override
    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getZeroDateMeasures(ViewContext context, VisualizationController.QueryType queryType)
    {
        // For studies, valid zero date columns are found in demographic datasets only:
        Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> measures = new HashMap<Pair<FieldKey, ColumnInfo>, QueryDefinition>();
        Study study = StudyService.get().getStudy(context.getContainer());
        if (study != null)
        {
            UserSchema schema = getUserSchema(context.getContainer(), context.getUser());
            for (DataSet ds : study.getDataSets())
            {
                if (ds.isDemographicData() && ds.isShowByDefault())
                {
                    Pair<QueryDefinition, TableInfo> entry = getTableAndQueryDef(context, schema, ds.getName(), ColumnMatchType.DATETIME_COLS, false);
                    if (entry != null)
                    {
                        QueryDefinition query = entry.getKey();
                        for (ColumnInfo col : query.getColumns(null, entry.getValue()))
                        {
                            if (col != null && ColumnMatchType.DATETIME_COLS.match(col))
                               measures.put(Pair.of(col.getFieldKey(), col), query);
                        }
                    }
                }
            }
        }
        return measures;
    }

    private static final boolean INCLUDE_DEMOGRAPHIC_DIMENSIONS = false;
    @Override
    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getDimensions(ViewContext context, String queryName)
    {
        Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> dimensions = super.getDimensions(context, queryName);
        if (INCLUDE_DEMOGRAPHIC_DIMENSIONS)
        {
            // include dimensions from demographic data sources
            Study study = StudyService.get().getStudy(context.getContainer());
            if (study != null)
            {
                for (DataSet ds : study.getDataSets())
                {
                    if (ds.isDemographicData())
                        dimensions.putAll(super.getDimensions(context, ds.getName()));
                }
            }
        }
        return dimensions;
    }

    @Override
    protected Map<QueryDefinition, TableInfo> getQueryDefinitions(ViewContext context, VisualizationController.QueryType queryType, ColumnMatchType matchType)
    {
        if (queryType == VisualizationController.QueryType.datasets)
        {
            Map<QueryDefinition, TableInfo> queries = new HashMap<QueryDefinition, TableInfo>();
            Study study = StudyService.get().getStudy(context.getContainer());
            UserSchema schema = getUserSchema(context.getContainer(), context.getUser());
            addDatasetQueryDefinitions(context, schema, study, queries);
            return queries;
        }
        else
            return super.getQueryDefinitions(context, queryType, matchType);
    }

    @Override
    /**
     * All columns for a study if builtIn types were requested would be constrained to datasets only
     */
    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getAllColumns(ViewContext context, VisualizationController.QueryType queryType, boolean showHidden)
    {
        if (queryType == VisualizationController.QueryType.builtIn || queryType == VisualizationController.QueryType.datasets)
        {
            Map<QueryDefinition, TableInfo> queries = new HashMap<QueryDefinition, TableInfo>();
            Study study = StudyService.get().getStudy(context.getContainer());
            UserSchema schema = getUserSchema(context.getContainer(), context.getUser());
            if (study != null)
            {
                addDatasetQueryDefinitions(context, schema, study, queries);

                if (queryType == VisualizationController.QueryType.builtIn)
                {
                    for (String name : schema.getTableAndQueryNames(true))
                    {
                        if (!StringUtils.startsWithIgnoreCase(name, "Primary Type Vial Counts") &&
                            !StringUtils.startsWithIgnoreCase(name, "Primary/Derivative Type Vial Counts") &&
                            !StringUtils.startsWithIgnoreCase(name, "Vial Counts by Requesting Location"))
                            continue;
                        Pair<QueryDefinition, TableInfo> entry = getTableAndQueryDef(context, schema, name, ColumnMatchType.All_VISIBLE, false);
                        if (entry != null)
                        {
                            queries.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
            return getMatchingColumns(context.getContainer(), queries, showHidden ? ColumnMatchType.All : ColumnMatchType.All_VISIBLE);
        }
        else
            return super.getAllColumns(context, queryType, showHidden);
    }

    private void addDatasetQueryDefinitions(ViewContext context, UserSchema schema, Study study, Map<QueryDefinition, TableInfo> queries)
    {
        if (study != null)
        {
            for (DataSet ds : study.getDataSets())
            {
                if (ds.isShowByDefault())
                {
                    Pair<QueryDefinition, TableInfo> entry = getTableAndQueryDef(context, schema, ds.getLabel(), ColumnMatchType.All_VISIBLE, false);
                    if (entry != null)
                    {
                        queries.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }
}
