/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.reports.ReportService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.DataView;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * View class for rendering CrosstabTableInfos.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Jan 25, 2008
 * Time: 9:49:51 AM
 */
public class CrosstabView extends QueryView
{
    private int _numRowAxisCols = 0;
    private int _numMeasures = 0;
    private int _numMemberMeasures = 0;
    protected List<FieldKey> _columns;

    /** Must call setSettings before using the view */
    public CrosstabView(UserSchema schema)
    {
        super(schema);
        setViewItemFilter(ReportService.EMPTY_ITEM_LIST);
    }

    public CrosstabView(UserSchema schema, QuerySettings settings, @Nullable Errors errors)
    {
        super(schema, settings, errors);
        setViewItemFilter(ReportService.EMPTY_ITEM_LIST);
    }

    // Collect the DisplayColumns by column member while keeping the column order the same.
    public static List<Pair<CrosstabMember, List<DisplayColumn>>> columnsByMember(Collection<DisplayColumn> renderers)
    {
        List<Pair<CrosstabMember, List<DisplayColumn>>> groupedByMember = new ArrayList<Pair<CrosstabMember, List<DisplayColumn>>>();

        CrosstabMember currentMember = null;
        List<DisplayColumn> currentMemberColumns = new ArrayList<DisplayColumn>();
        groupedByMember.add(Pair.of(currentMember, currentMemberColumns));

        for (DisplayColumn renderer : renderers)
        {
            ColumnInfo col = renderer.getColumnInfo();
            if (col.getCrosstabColumnMember() != null && !col.getCrosstabColumnMember().equals(currentMember))
            {
                currentMember = col.getCrosstabColumnMember();
                currentMemberColumns = new ArrayList<DisplayColumn>();
                groupedByMember.add(Pair.of(currentMember, currentMemberColumns));
            }

            currentMemberColumns.add(renderer);
        }
        return groupedByMember;
    }

    protected DataRegion createDataRegion()
    {
        if(getTable() instanceof CrosstabTableInfo)
        {
            CrosstabTableInfo table = (CrosstabTableInfo)getTable();

            //get the display columns
            //this will also adjust _numRowAxisCols and _numMeasures based on
            //the selected display columns
            List<DisplayColumn> displayCols = getDisplayColumns();

            CrosstabDataRegion rgn = new CrosstabDataRegion(table, _numRowAxisCols, _numMeasures, _numMemberMeasures);
            configureDataRegion(rgn);

            return rgn;
        }
        else
            return super.createDataRegion();
    }

    public List<DisplayColumn> getDisplayColumns()
    {
        assert getTable() instanceof CrosstabTableInfo;
        CrosstabTableInfo table = (CrosstabTableInfo)getTable();

        List<FieldKey> selectedFieldKeys = null;

        // First check if something has explictly overridden the columns
        if (_columns != null)
        {
            selectedFieldKeys = _columns;
        }
        else if (null != getCustomView())
        {
            selectedFieldKeys = getCustomView().getColumns();
        }

        if (selectedFieldKeys == null || selectedFieldKeys.isEmpty())
        {
            selectedFieldKeys = table.getDefaultVisibleColumns();
        }

        //separate the row dimension columns from the measure columns
        ArrayList<FieldKey> rowDimFieldKeys = new ArrayList<FieldKey>(selectedFieldKeys.size());
        ArrayList<FieldKey> measureFieldKeys = new ArrayList<FieldKey>(selectedFieldKeys.size());
        Map<CrosstabMember, List<FieldKey>> measureFieldKeysByMember = new LinkedHashMap<CrosstabMember, List<FieldKey>>();
        for (FieldKey col : selectedFieldKeys)
        {
            ColumnInfo column = table.getColumn(col);
            if (col.getParts().get(0).startsWith(AggregateColumnInfo.NAME_PREFIX))
                measureFieldKeys.add(col);
            else if (column != null && column.getCrosstabColumnMember() != null)
            {
                List<FieldKey> fieldKeys = measureFieldKeysByMember.get(column.getCrosstabColumnMember());
                if (fieldKeys == null)
                    measureFieldKeysByMember.put(column.getCrosstabColumnMember(), fieldKeys = new ArrayList<FieldKey>());
                fieldKeys.add(col);
            }
            else
                rowDimFieldKeys.add(col);
        }

        // For each of the table's column members (pivot values), expand the
        // selected measureCols that start with magic AggregateColumnInfo.NAME_PREFIX into
        // the set of member+measure columns.
        // NOTE: QueryPivot.PivotTable doesn't use the AggregateColumnInfo.NAME_PREFIX magic so will have no measureCols.
        if (!measureFieldKeys.isEmpty())
        {
            for (CrosstabMember member : table.getColMembers())
            {
                for (FieldKey col : measureFieldKeys)
                {
                    List<String> parts = new ArrayList<String>(col.getParts());
                    parts.set(0, AggregateColumnInfo.getColumnName(member, table.getMeasureFromKey(col.getParts().get(0))));

                    FieldKey measureMemberFieldKey = FieldKey.fromParts(parts);
                    List<FieldKey> fieldKeys = measureFieldKeysByMember.get(member);
                    if (fieldKeys == null)
                        measureFieldKeysByMember.put(member, fieldKeys = new ArrayList<FieldKey>());
                    fieldKeys.add(measureMemberFieldKey);
                }
            }
        }

        // Create and count the selected ColumnInfos
        Map<FieldKey, ColumnInfo> rowDimCols = QueryService.get().getColumns(table, rowDimFieldKeys);
        _numRowAxisCols = rowDimCols.size();
        _numMeasures = measureFieldKeysByMember.size();

        //put together the complete list of display columns by getting the
        //renderers from the ColumnInfos for the complete list of columns
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>();
        for (ColumnInfo cinfo : rowDimCols.values())
            displayColumns.add(cinfo.getRenderer());

        for (Map.Entry<CrosstabMember, List<FieldKey>> entry : measureFieldKeysByMember.entrySet())
        {
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(table, entry.getValue());
            _numMemberMeasures += columns.size();

            for (ColumnInfo cinfo : columns.values())
                displayColumns.add(cinfo.getRenderer());
        }

        return displayColumns;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();

        assert getTable() instanceof CrosstabTableInfo;
        CrosstabTableInfo table = (CrosstabTableInfo)getTable();

        //set the default base sort (remove non-existant sort columns)
        Sort sort = new Sort();
        for (Sort.SortField sortField : CrosstabTable.getDefaultSort().getSortList())
            if (getTable().getColumn(sortField.getColumnName()) != null)
                sort.appendSortColumn(sortField);
        view.getRenderContext().setBaseSort(sort);

        if(null != view.getRenderContext().getBaseFilter())
        {
            //separate the filter clauses that apply to aggregate columns
            //from those applying to the row dimensions and hand the agg
            //ones to the table info, and the other to the view's base filter
            SimpleFilter aggFilter = new SimpleFilter();
            SimpleFilter rowDimFilter = new SimpleFilter();
            assert view.getRenderContext().getBaseFilter() instanceof SimpleFilter;
            for(SimpleFilter.FilterClause clause : ((SimpleFilter)(view.getRenderContext().getBaseFilter())).getClauses())
            {
                boolean aggClause = false;
                for(String colName : clause.getColumnNames())
                    aggClause |= colName.startsWith(AggregateColumnInfo.NAME_PREFIX);

                if(aggClause)
                    aggFilter.addClause(clause);
                else
                    rowDimFilter.addClause(clause);
            }

            if(aggFilter.getClauses().size() > 0)
                table.setAggregateFilter(aggFilter);
            view.getRenderContext().setBaseFilter(rowDimFilter);
        }

        return view;
    }

    public ExcelWriter getExcelWriter(ExcelWriter.ExcelDocumentType docType) throws Exception
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setAllowAsync(false);
        Results rs = rgn.getResultSet(view.getRenderContext());

        CrosstabTableInfo table = (CrosstabTableInfo)getTable();
        List<DisplayColumn> displayColumns = rgn.getDisplayColumns();

        return new CrosstabExcelWriter(table, rs, getExportColumns(displayColumns), _numRowAxisCols, _numMeasures, _numMemberMeasures, docType);
    }
}
