/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.collections.RowMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.Format;
import java.text.NumberFormat;
import java.util.*;


public class DataRegion extends DisplayElement
{
    private static final Logger _log = Logger.getLogger(DataRegion.class);
    private List<DisplayColumn> _displayColumns = new ArrayList<DisplayColumn>();
    private List<DisplayColumnGroup> _groups = new ArrayList<DisplayColumnGroup>();
    private QuerySettings _settings = null;
    private Map<String, Aggregate.Result> _aggregateResults = null;
    private boolean _aggregateRowFirst = false;
    private boolean _aggregateRowLast = true;
    private TableInfo _table = null;
    protected boolean _showRecordSelectors = false;
    protected boolean _showSelectMessage = true;
    private boolean _showFilters = true;
    private boolean _sortable = true;
    private boolean _showFilterDescription = true;
    private String _name = null;
    private ButtonBar _gridButtonBar = ButtonBar.BUTTON_BAR_GRID;
    private ButtonBar _insertButtonBar = ButtonBar.BUTTON_BAR_INSERT;
    private ButtonBar _updateButtonBar = ButtonBar.BUTTON_BAR_UPDATE;
    private ButtonBar _detailsButtonBar = ButtonBar.BUTTON_BAR_DETAILS;
    private String _inputPrefix = null;
    private List<String> _recordSelectorValueColumns;
    private boolean _fixedWidthColumns;
    private int _maxRows = Table.ALL_ROWS;   // Display all rows by default
    private long _offset = 0;
    private List<Pair<String, Object>> _hiddenFormFields = new ArrayList<Pair<String, Object>>();   // Hidden params to be posted (e.g., to pass a query string along with selected grid rows)
    private int _defaultMode = MODE_GRID;
    private ButtonBarPosition _buttonBarPosition = ButtonBarPosition.TOP;
    private boolean allowAsync = false;
    private ActionURL _formActionUrl = null;

    private String _noRowsMessage = "No data to show.";

    private boolean _shadeAlternatingRows = false;
    private boolean _showBorders = false;
    private boolean _showSurroundingBorder = true;
    private boolean _showPagination = true;
    private boolean _showPaginationCount = true;

    private List<String> _groupHeadings;
    private boolean _horizontalGroups = true;

    private Long _totalRows = null; // total rows in the query or null if unknown
    private Integer _rowCount = null; // number of rows in the result set or null if unknown
    private boolean _complete = false; // true if all rows are in the ResultSet
    private List<ButtonBarConfig> _buttonBarConfigs = new ArrayList<ButtonBarConfig>();

    public static final int MODE_NONE = 0;
    public static final int MODE_INSERT = 1;
    public static final int MODE_UPDATE = 2;
    public static final int MODE_GRID = 4;
    public static final int MODE_DETAILS = 8;
    public static final int MODE_ALL = MODE_INSERT + MODE_UPDATE + MODE_GRID + MODE_DETAILS;

    public static final String LAST_FILTER_PARAM = ".lastFilter";
    public static final String SELECT_CHECKBOX_NAME = ".select";
    protected static final String TOGGLE_CHECKBOX_NAME = ".toggle";

    public void addDisplayColumn(DisplayColumn col)
    {
        assert  null != col;
        if (null == col)
            return;
        _displayColumns.add(col);
        if (null != _inputPrefix)
            col.setInputPrefix(_inputPrefix);
    }

    public void addDisplayColumn(int index, DisplayColumn col)
    {
        assert  null != col;
        if (null == col)
            return;
        _displayColumns.add(index, col);
        if (null != _inputPrefix)
            col.setInputPrefix(_inputPrefix);
    }

    public void addDisplayColumns(List<DisplayColumn> displayColumns)
    {
        for (DisplayColumn displayColumn : displayColumns)
            addDisplayColumn(displayColumn);
    }

    public List<DisplayColumn> getDisplayColumns()
    {
        return _displayColumns;
    }

    public DisplayColumn getDisplayColumn(int i)
    {
        return _displayColumns.get(i);
    }

    public void clearColumns()
    {
        _displayColumns.clear();
    }

    public void addColumn(ColumnInfo col)
    {
        addDisplayColumn(col.getRenderer());
    }

    public void addColumn(int index, ColumnInfo col)
    {
        addDisplayColumn(index, col.getRenderer());
    }

    public void addColumns(List<ColumnInfo> cols)
    {
        for (ColumnInfo col : cols)
            addDisplayColumn(col.getRenderer());
    }

    public void addColumns(TableInfo tinfo, String colNames)
    {
        List<ColumnInfo> cols = tinfo.getColumns(colNames);
        addColumns(cols);
    }

    public List<String> getDisplayColumnNames()
    {
        List<String> list = new ArrayList<String>();

        for (DisplayColumn dc : getDisplayColumns())
            list.add(dc.getName());

        return list;
    }

    public void setDisplayColumns(List<DisplayColumn> displayColumns)
    {
        _displayColumns = displayColumns;
        if (null != _inputPrefix)
            for (DisplayColumn dc : _displayColumns)
                dc.setInputPrefix(_inputPrefix);
    }

    public void removeColumns(String... columns)
    {
        for (String column : columns)
        {
            String trimmedColName = column.trim();
            // go backwards through the list so we don't have to worry about a
            // removal changing our next index.
            for (int colIndex = _displayColumns.size() - 1; colIndex >= 0; colIndex--)
            {
                DisplayColumn dc = _displayColumns.get(colIndex);
                if (trimmedColName.equalsIgnoreCase(dc.getName()))
                    _displayColumns.remove(colIndex);
            }
        }
    }

    /* remove comma-separated string of column names from List */
    public void removeColumns(String columns)
    {
        String[] eachCol = columns.split(",");
        removeColumns(eachCol);
    }

    public void setColumns(List<ColumnInfo> cols)
    {
        clearColumns();

        for (ColumnInfo column : cols)
            addColumn(column);
    }

    // Return DisplayColumn by name (or null if no DisplayColumn has this name)
    // UNDONE: Create HashMap on first use?
    public DisplayColumn getDisplayColumn(String name)
    {
        for (DisplayColumn dc : getDisplayColumns())
        {
            if (name.equalsIgnoreCase(dc.getName()))
                return dc;
        }

        return null;
    }

    public void replaceDisplayColumn(String name, DisplayColumn replacement)
    {
        for (int i = 0; i < _displayColumns.size(); i++)
        {
            if (name.equalsIgnoreCase(_displayColumns.get(i).getName()))
            {
                _displayColumns.remove(i);
                addDisplayColumn(i, replacement);
                return;
            }
        }
    }

    public void setInputPrefix(String inputPrefix)
    {
        _inputPrefix = inputPrefix;
        for (DisplayColumn dc : _displayColumns)
            dc.setInputPrefix(_inputPrefix);
    }

    public void addButtonBarConfig(ButtonBarConfig buttonBarConfig)
    {
        assert buttonBarConfig != null : "Cannot add a null ButtonBarConfig";
        _buttonBarConfigs.add(buttonBarConfig);
    }

    public void addHiddenFormField(Enum name, String value)
    {
        addHiddenFormField(name.toString(), value);
    }


    public void addHiddenFormField(Enum name, HString value)
    {
        addHiddenFormField(name.toString(), value);
    }


    //TODO: Fix these up. They are just regular fields that are hidden (not rendered in grid mode)
    public void addHiddenFormField(String name, HString value)
    {
        if (null != value)
            _hiddenFormFields.add(new Pair<String, Object>(name, value));
    }


    public void addHiddenFormField(String name, String value)
    {
        if (null != value)
            _hiddenFormFields.add(new Pair<String, Object>(name, value));
    }

    public String getHiddenFormFieldValue(String name)
    {
        for (Pair<String, Object> hiddenFormField : _hiddenFormFields)
        {
            if (name.equals(hiddenFormField.getKey()))
            {
                if (hiddenFormField.getValue() instanceof HString)
                    return ((HString)hiddenFormField.getValue()).getSource();
                else
                    return (String)hiddenFormField.getValue();
            }
        }
        return null;
    }



    public LinkedHashMap<FieldKey, ColumnInfo> getSelectColumns()
    {
        List<DisplayColumn> displayCols = getDisplayColumns();

        // includes old DisplayColumn.addQueryColumns()
        List<ColumnInfo> originalColumns = RenderContext.getSelectColumns(displayCols, getTable());

        // allow DataRegion subclass to add columns (yuck)
        LinkedHashSet<ColumnInfo> columns = new LinkedHashSet<ColumnInfo>(originalColumns);
        addQueryColumns(columns);

        LinkedHashMap<FieldKey, ColumnInfo> ret = QueryService.get().getColumns(getTable(), Collections.<FieldKey>emptySet(), columns);

        return ret;
    }


    public void setShowRecordSelectors(boolean show)
    {
        _showRecordSelectors = show;
    }


    public boolean getShowRecordSelectors()
    {
        return _showRecordSelectors;
    }

    public boolean getShowSelectMessage()
    {
        return _showSelectMessage;
    }

    public void setShowSelectMessage(boolean showSelectMessage)
    {
        _showSelectMessage = showSelectMessage;
    }

    public boolean getShowFilters()
    {
        return _showFilters;
    }


    public void setShowFilters(boolean show)
    {
        _showFilters = show;
    }

    public boolean isSortable()
    {
        return _sortable;
    }

    public void setSortable(boolean sortable)
    {
        _sortable = sortable;
    }

    public boolean isShowFilterDescription()
    {
        return _showFilterDescription;
    }

    public void setShowFilterDescription(boolean showFilterDescription)
    {
        _showFilterDescription = showFilterDescription;
    }

    public ButtonBar getButtonBar(int mode)
    {
        switch (mode)
        {
            case MODE_INSERT:
                return _insertButtonBar;
            case MODE_UPDATE:
                return _updateButtonBar;
            case MODE_GRID:
                return _gridButtonBar;
            case MODE_DETAILS:
                return _detailsButtonBar;
            default:
            {
                _log.error("getting button bar for non existent mode");
                return null;
            }
        }
    }


    public void setButtonBar(ButtonBar buttonBar)
    {
        _insertButtonBar = _updateButtonBar = _gridButtonBar = _detailsButtonBar = buttonBar;
    }

    public void setButtonBar(ButtonBar buttonBar, int mode)
    {
        switch (mode)
        {
            case MODE_INSERT:
                _insertButtonBar = buttonBar;
                return;
            case MODE_UPDATE:
                _updateButtonBar = buttonBar;
                return;
            case MODE_GRID:
                _gridButtonBar = buttonBar;
                return;
            case MODE_DETAILS:
                _detailsButtonBar = buttonBar;
                return;
            default:
                _log.error("Setting button bar for non existent mode");
        }
    }


    public boolean getFixedWidthColumns()
    {
        return _fixedWidthColumns;
    }

    public void setFixedWidthColumns(boolean fixed)
    {
        _fixedWidthColumns = fixed;
    }

    public void setSettings(QuerySettings settings)
    {
        _settings = settings;
    }

    public QuerySettings getSettings()
    {
        return _settings;
    }

    public int getMaxRows()
    {
        return _settings != null ? _settings.getMaxRows() : _maxRows;
    }

    /** Use {@link QuerySettings#setMaxRows(int)}. */
    @Deprecated
    public void setMaxRows(int maxRows)
    {
        if (_settings != null)
            _settings.setMaxRows(maxRows);
        else
            _maxRows = maxRows;
    }

    public long getOffset()
    {
        return _settings != null ? _settings.getOffset() : _offset;
    }

    /** Use {@link QuerySettings#setOffset(long)}. */
    @Deprecated
    public void setOffset(long offset)
    {
        if (_settings != null)
            _settings.setOffset(offset);
        else
            _offset = offset;
    }

    public ShowRows getShowRows()
    {
        return _settings != null ? _settings.getShowRows() : ShowRows.PAGINATED;
    }

    public String getName()
    {
        if (null == _name)
        {
            if (null != _settings && null != _settings.getDataRegionName())
                _name = _settings.getDataRegionName();
            else
                _name = getTable().getName();
        }
        return _name;
    }

    /**
     * Use {@link DataRegion#setSettings(QuerySettings)} to set the name instead.
     */
    @Deprecated
    public void setName(String name)
    {
        _name = name;
    }

    public String getSelectionKey()
    {
        if (_settings != null && _settings.getSelectionKey() != null)
            return _settings.getSelectionKey();
        if (getTable() != null && getTable().getSchema() != null)
            DataRegionSelection.getSelectionKey(getTable().getSchema().getName(), getTable().getName(), null, getName());
        return null;
    }

    // TODO: Should get rid of getTable() & setTable() and just rely on the query columns associated with each display column.
    // Also, dc.isQueryColumn() is redundant with !queryColumns.isEmpty()
    public TableInfo getTable()
    {
        if (_table != null)
            return _table;

        for (DisplayColumn dc : _displayColumns)
        {
            if (dc.isQueryColumn())
            {
                _table = dc.getColumnInfo().getParentTable();
                break;
            }
        }

        if (_table != null)
            return _table;

        // Non-query display columns can still have query column dependencies (examples: ms2 DeltaScan and Hydrophobicity columns).
        // Last attempt at finding the table: iterate through the display columns and return the parent table of the first query column dependency.
        Set<ColumnInfo> queryColumns = new HashSet<ColumnInfo>();
        for (DisplayColumn dc : _displayColumns)
        {
            dc.addQueryColumns(queryColumns);
            if (queryColumns.contains(null))
            {
                // Catch this problem before it's too late to figure out who the culprit was
                throw new IllegalStateException("The display column " + dc + " added one or more null columns to the set of query columns");
            }

            if (!queryColumns.isEmpty())
            {
                Iterator<ColumnInfo> iter = queryColumns.iterator();
                ColumnInfo col = iter.next();
                _table = col.getParentTable();
                break;
            }
        }

        if (_table == null)
        {
            for (DisplayColumnGroup group : _groups)
            {
                for (DisplayColumn dc : group.getColumns())
                {
                    if (dc.isQueryColumn())
                    {
                        _table = dc.getColumnInfo().getParentTable();
                        break;
                    }
                }
                if (_table != null)
                {
                    break;
                }
            }
        }

        return _table;
    }

    public void setTable(TableInfo table)
    {
        _table = table;
    }

    public int getDefaultMode()
    {
        return _defaultMode;
    }

    public void setDefaultMode(int defaultMode)
    {
        _defaultMode = defaultMode;
    }

    /**
     * Get a ResultSet from the DataRegion.
     * Has the side-effect of setting the ResultSet and this DataRegion
     * on the RenderContext and selecting any aggregates
     * (including the row count aggregate, unless pagination or pagination count are false.)
     * Callers should check for ReadPermission before requesting a ResultSet.
     * @param ctx The RenderContext
     * @return A new ResultSet or the existing ResultSet in the RenderContext or null if no READ permission.
     * @throws SQLException SQLException
     * @throws IOException IOException
     */
    final public Results getResultSet(RenderContext ctx) throws SQLException, IOException
    {
        if (!ctx.getViewContext().hasPermission(ReadPermission.class))
            return null;

        DataRegion oldRegion = ctx.getCurrentRegion();
        if (oldRegion != this)
            ctx.setCurrentRegion(this);

        try
        {
            Results rs = ctx.getResults();
            if (null == rs)
            {
                TableInfo tinfoMain = getTable();
                if (null == tinfoMain)
                {
                    _log.info("DataRegion.getResultSet: Could not find table to query from");
                    throw new SQLException("No query table in DataRegion.getResultSet");
                }
                else
                {
                    rs = getResultSet(ctx, isAllowAsync());
                }
            }

            getAggregateResults(ctx);
            return rs;
        }
        finally
        {
            ctx.setCurrentRegion(oldRegion);
        }
    }


    protected Results getResultSet(RenderContext ctx, boolean async) throws SQLException, IOException
    {
        LinkedHashMap<FieldKey, ColumnInfo> selectKeyMap = getSelectColumns();
        return ctx.getResultSet(selectKeyMap, getTable(), getMaxRows(), getOffset(), getName(), async);
    }


    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        // no extra query columns added by default
    }

    private void getAggregateResults(RenderContext ctx) throws SQLException, IOException
    {
        ResultSet rs = ctx.getResultSet();
        assert rs != null;
        if (rs instanceof Table.TableResultSet)
        {
            Table.TableResultSet tableRS = (Table.TableResultSet) rs;
            _complete = tableRS.isComplete();
        }

        boolean countAggregate = getMaxRows() > 0 && !_complete && _showPagination && _showPaginationCount;
        if (countAggregate)
        {
            List<Aggregate> newAggregates = new LinkedList<Aggregate>();
            if (ctx.getBaseAggregates() != null)
                newAggregates.addAll(ctx.getBaseAggregates());
            newAggregates.add(Aggregate.createCountStar());

            _aggregateResults = ctx.getAggregates(_displayColumns, getTable(), getName(), newAggregates, isAllowAsync());

            Aggregate.Result result = _aggregateResults.remove(Aggregate.STAR);
            if (result != null)
                _totalRows = (Long)result.getValue();
            }
        else
        {
            _aggregateResults =  ctx.getAggregates(_displayColumns, getTable(), getName(), ctx.getBaseAggregates(), isAllowAsync());
        }

        // TODO: Move this into RenderContext?
        ActionURL url = ctx.getSortFilterURLHelper();
        PageFlowUtil.saveLastFilter(ctx.getViewContext(), url, "");
    }

    //TODO: total number of rows should be pushed down to a property of the TableResultSet
    //We need this temporarily for the QueryView.exportToApiResponse() method
    public Long getTotalRows()
    {
        return _totalRows;
    }

    /**
     * Sets ctx to MODE_GRID and renders as usual.
     * Sets ctx to MODE_GRID and renders as usual.
     */
    @Deprecated
    public void renderTable(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        ctx.setMode(MODE_GRID);
        //Force through bottleneck
        render(ctx, out);
    }

    private SimpleFilter getValidFilter(RenderContext ctx)
    {
        SimpleFilter urlFilter = new SimpleFilter(ctx.getViewContext().getActionURL(), getName());
        for (FieldKey fk : ctx.getIgnoredFilterColumns())
            urlFilter.deleteConditions(fk.toString());
        if (urlFilter.getClauses().isEmpty())
            return null;
        return urlFilter;
    }

    protected String getFilterErrorMessage(RenderContext ctx) throws IOException
    {
        StringBuilder buf = new StringBuilder();
        Set<FieldKey> ignoredColumns = ctx.getIgnoredFilterColumns();
        if (!ignoredColumns.isEmpty())
        {
            if (ignoredColumns.size() == 1)
            {
                FieldKey field = ignoredColumns.iterator().next();
                buf.append("Ignoring filter/sort on column '").append(field.getDisplayString()).append("' because it does not exist.");
            }
            else
            {
                String comma = "";
                buf.append("Ignoring filter/sort on columns ");
                for (FieldKey field : ignoredColumns)
                {
                    buf.append(comma);
                    comma = ", ";
                    buf.append("'");
                    buf.append(field.getDisplayString());
                    buf.append("'");
                }
                buf.append(" because they do not exist.");
            }
        }
        return buf.toString();
    }


    protected void _renderTable(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        if (!ctx.getViewContext().hasPermission(ReadPermission.class))
        {
            out.write("You do not have permission to read this data");
            return;
        }

        ResultSet rs = null;
        try
        {
            StringBuilder headerMessage = new StringBuilder();
            SQLException sqlx = null;

            try
            {
                rs = getResultSet(ctx);
            }
            catch (SQLException x)
            {
                sqlx = x;
                headerMessage.append("<span class=error>").append(PageFlowUtil.filter(x.getMessage())).append("</span><br>");
            }

            List<DisplayColumn> renderers = getDisplayColumns();

            //determine number of HTML table columns...watch out for hidden display columns
            //and include one extra if showing record selectors
            int colCount = 0;
            for (DisplayColumn col : renderers)
            {
                if (col.isVisible(ctx))
                    colCount++;
            }
            if (_showRecordSelectors)
                colCount++;


            if (rs instanceof Table.TableResultSet && ((Table.TableResultSet)rs).getSize() != -1)
            {
                _rowCount = ((Table.TableResultSet)rs).getSize();
                if (_complete && _totalRows == null)
                    _totalRows = getOffset() + _rowCount.intValue();
            }

            // If button bar is not visible, don't render form.  Important for nested regions (forms can't be nested)
            //TODO: Fix this so form is rendered AFTER all rows. (Does this change layout?)
            boolean renderButtons = _gridButtonBar.shouldRender(ctx);
            if (renderButtons)
                _gridButtonBar.setConfigs(_buttonBarConfigs);
            String filterErrorMsg = getFilterErrorMessage(ctx);
            String filterDescription = isShowFilterDescription() ? getFilterDescription(ctx) : null;
            if (filterErrorMsg != null && filterErrorMsg.length() > 0)
                headerMessage.append("<span class=\"error\">").append(PageFlowUtil.filter(filterErrorMsg)).append("</span>");

            if (filterDescription != null)
            {
                if (headerMessage.length() > 0)
                    headerMessage.append("<br>");
                headerMessage.append(PageFlowUtil.filter(filterDescription));
                headerMessage.append(" <a href=\"#\" onClick=\"javascript:LABKEY.DataRegions['");
                headerMessage.append(PageFlowUtil.filter(getName()));
                headerMessage.append("'].clearAllFilters(); return false;\">Clear all filters</a>");
            }

            renderHeaderScript(ctx, out, headerMessage.toString());

            if (!_showPagination && rs instanceof Table.TableResultSet)
            {
                Table.TableResultSet tableRS = (Table.TableResultSet) rs;
                if (!tableRS.isComplete())
                {
                    out.write("<span class=\"labkey-message\">");
                    out.write(tableRS.getTruncationMessage(getMaxRows()));
                    out.write("</span>");
                }
            }
            
            renderRegionStart(ctx, out, renderButtons, renderers);

            renderHeader(ctx, out, renderButtons, colCount);

            if (null == sqlx)
            {
                renderGridHeaderColumns(ctx, out, renderers);

                if (_aggregateRowFirst)
                    renderAggregatesTableRow(ctx, out, renderers, false, true);

                int rows = renderTableContents(ctx, out, renderers);
                //assert _rowCount != null && rows == _rowCount : "Row size mismatch: NYI";
                if (rows == 0)
                {
                    renderNoRowsMessage(ctx, out, colCount);
                }

                if (_aggregateRowLast)
                    renderAggregatesTableRow(ctx, out, renderers, true, false);

            }

            renderFooter(ctx, out, renderButtons, colCount);

            renderRegionEnd(ctx, out, renderButtons, renderers);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    protected void renderRegionStart(RenderContext ctx, Writer out, boolean renderButtons, List<DisplayColumn> renderers) throws IOException
    {
        if(renderButtons)
            renderFormHeader(ctx, out, MODE_GRID);
        out.write("\n<table class=\"labkey-data-region");

        if (isShowBorders())
             out.write(" labkey-show-borders");
        else if(isShowSurroundingBorder())
            out.write(" labkey-show-surrounding-border");

        if (_aggregateResults != null && !_aggregateResults.isEmpty())
            out.write(" labkey-has-col-totals");
        if (_fixedWidthColumns)
            out.write(" labkey-fixed-width-columns");
        out.write("\"");

        out.write(" id=\"");
        out.write(PageFlowUtil.filter("dataregion_" + getName()));
        out.write("\">\n");

        //colgroup
        out.write("\n<colgroup>");
        if (_showRecordSelectors)
            out.write("\n<col class=\"labkey-selectors\" width=\"35\"/>");
        Iterator<DisplayColumn> itr = renderers.iterator();
        DisplayColumn renderer;
        while (itr.hasNext())
        {
            renderer = itr.next();
            if (renderer.isVisible(ctx))
                renderer.renderColTag(out, !itr.hasNext());
        }
        out.write("\n</colgroup>");
    }

    protected void renderRegionEnd(RenderContext ctx, Writer out, boolean renderButtons, List<DisplayColumn> renderers) throws IOException
    {
        out.write("\n</table>");
        if (renderButtons)
            renderFormEnd(ctx, out);

        for (DisplayColumn renderer : renderers)
        {
            if (renderer.isVisible(ctx))
                renderer.renderGridEnd(ctx, out);
        }
    }

    protected void renderHeader(RenderContext ctx, Writer out, boolean renderButtons, int colCount) throws IOException
    {
        out.write("\n<tr");
        if (!shouldRenderHeader(renderButtons))
            out.write(" style=\"display:none\"");
        out.write(">");
        
        out.write("<td colspan=\"");
        out.write(String.valueOf(colCount));
        out.write("\" class=\"labkey-data-region-header-container\">\n");

        out.write("<table class=\"labkey-data-region-header\" id=\"" + PageFlowUtil.filter("dataregion_header_" + getName()) + "\">\n");
        out.write("<tr><td nowrap>\n");
        if (renderButtons)
        {
            //adjust position if bbar supplies a position value
            if (_gridButtonBar.getConfiguredPosition() != null)
                setButtonBarPosition(_gridButtonBar.getConfiguredPosition());

            if (_buttonBarPosition.atTop())
                _gridButtonBar.render(ctx, out);
        }
        out.write("</td>");

        out.write("<td align=\"right\" valign=\"top\" nowrap>\n");
        if (_showPagination && _buttonBarPosition.atTop())
            renderPagination(ctx, out);
        out.write("</td></tr>\n");

        renderRibbon(ctx, out);
        renderMessageBox(ctx, out, colCount);

        // end table.labkey-data-region-header
        out.write("</table>\n");

        out.write("\n</td></tr>");
    }

    protected boolean shouldRenderHeader(boolean renderButtons)
    {
        return ((renderButtons && _buttonBarPosition.atTop() && _gridButtonBar.getList().size() > 0)
                || (_showPagination && _buttonBarPosition.atTop() && !isSmallResultSet()));
    }


    protected void renderHeaderScript(RenderContext ctx, Writer out, String headerMessage) throws IOException
    {
        out.write("<script type=\"text/javascript\">\n");
        out.write("Ext.onReady(\n");
        out.write("function () {\n");
        out.write("new LABKEY.DataRegion({\n");
        out.write("'name' : " + PageFlowUtil.jsString(getName()) + "\n");

        if (getSettings() != null)
        {
            out.write(",'schemaName' : " + PageFlowUtil.jsString(getSettings().getSchemaName()) + "\n");
            out.write(",'queryName' : " + PageFlowUtil.jsString(getSettings().getQueryName()) + "\n");
            out.write(",'viewName' : " + PageFlowUtil.jsString(getSettings().getViewName()) + "\n");
        }
        if (ctx.getView() != null)
        {
            CustomView view = ctx.getView();
            // UNDONE: use CustomViewUtil.propertyMap()
            out.write(",'view' : {\n");
            out.write("'name' : " + PageFlowUtil.jsString(view.getName()));
            out.write(", 'default' : " + String.valueOf(view.getName() == null));
            if (view.getOwner() != null)
                out.write(", 'owner' : " + PageFlowUtil.jsString(view.getOwner().getDisplayName(ctx.getViewContext().getUser())));
            out.write(", 'shared' : " + view.isShared());
            out.write(", 'inherit' : " + view.canInherit());
            out.write(", 'session' : " + view.isSession());
            out.write(", 'editable' : " + view.isEditable());
            out.write(", 'hidden' : " + view.isHidden());
            // module custom views have no container
            out.write(", 'containerPath' : " + PageFlowUtil.jsString(view.getContainer() != null ? view.getContainer().getPath() : ""));
            out.write("}");
        }
        out.write(",'complete' : " + _complete + "\n");
        out.write(",'offset' : " + getOffset() + "\n");
        out.write(",'maxRows' : " + getMaxRows() + "\n");
        out.write(",'totalRows' : " + _totalRows + "\n");
        out.write(",'rowCount' : " + _rowCount + "\n");
        out.write(",'showRows' : '" + getShowRows().toString().toLowerCase() + "'\n");
        out.write(",'showRecordSelectors' : " + _showRecordSelectors + "\n");
        out.write(",'showSelectMessage' : " + _showSelectMessage + "\n");
        out.write(",'selectionKey' : " + PageFlowUtil.jsString(getSelectionKey()) + "\n");
        out.write(",'requestURL' : " + PageFlowUtil.jsString(ctx.getViewContext().getActionURL().toString()) + "\n");
        out.write(",'selectorCols' : " + PageFlowUtil.jsString(_recordSelectorValueColumns == null ? null : _recordSelectorValueColumns.toString()) + "\n");

        // TODO: Don't get available container filters from render context.
        // 11082: Populate customize view with list of allowable container filters from the QueryView
        List<ContainerFilter.Type> allowableContainerFilterTypes = (List<ContainerFilter.Type>)ctx.get("allowableContainerFilterTypes");
        if (allowableContainerFilterTypes != null && allowableContainerFilterTypes.size() > 0)
        {
            out.write(", 'allowableContainerFilters' : [");
            String sep = "";
            for (ContainerFilter.Type type : allowableContainerFilterTypes)
            {
                out.write(sep);
                sep = ", ";
                out.write("[ ");
                out.write(PageFlowUtil.jsString(type.name()));
                out.write(" , ");
                out.write(PageFlowUtil.jsString(type.toString()));
                out.write(" ]");
            }
            out.write("]\n");
        }

        out.write("});\n");
        if (headerMessage != null && headerMessage.length() > 0)
        {
            out.write("LABKEY.DataRegions[" + PageFlowUtil.jsString(getName()) + "].showMessage(" +
                    PageFlowUtil.jsString(headerMessage) + ");\n");
        }

        out.write("});\n");
        out.write("</script>\n");
    }

    protected void renderRibbon(RenderContext ctx, Writer out) throws IOException
    {
        out.write("<tr>");
        out.write("<td colspan=\"2\" class=\"labkey-ribbon extContainer\" style=\"display:none;\"></td>");
        out.write("</tr>\n");
    }

    protected void renderMessageBox(RenderContext ctx, Writer out, int colCount) throws IOException
    {
        out.write("<tr id=\"" + PageFlowUtil.filter("dataregion_msgbox_" + getName()) + "\" style=\"display:none\">");
        out.write("<td colspan=\"");
        out.write("2");
        out.write("\" class=\"labkey-dataregion-msgbox\">");
        out.write("<span class=\"labkey-tool labkey-tool-close\" style=\"float:right;\" onclick=\"LABKEY.DataRegions[" + PageFlowUtil.filterQuote(getName()) + "].hideMessage();\" title=\"Close this message\" alt=\"close\"></span>");
        out.write("<div></div>");
        out.write("</td>");
        out.write("</tr>\n");
    }

    protected void renderFooter(RenderContext ctx, Writer out, boolean renderButtons, int colCount) throws IOException
    {
        if (needToRenderFooter(renderButtons))
        {
            out.write("<tr><td colspan=\"");
            out.write(String.valueOf(colCount));
            out.write("\" class=\"labkey-data-region-header-container\">\n");
            out.write("<table class=\"labkey-data-region-header\" id=\"" + PageFlowUtil.filter("dataregion_footer_" + getName()) + "\">\n");
            out.write("<tr><td nowrap>\n");
            if (renderButtons && _buttonBarPosition.atBottom())
            {
                // 7024: don't render bottom buttons if the button bar already
                // appears at the top and it's a small result set
                if (!_buttonBarPosition.atTop() || !isSmallResultSet())
                    _gridButtonBar.render(ctx, out);
            }
            out.write("</td>");

            out.write("<td align=\"right\" valign=\"top\" nowrap>\n");
            if (_showPagination && _buttonBarPosition.atBottom())
                renderPagination(ctx, out);
            out.write("</td></tr>\n");

            renderRibbon(ctx, out);
            
            out.write("</table>");

            out.write("</td></tr>");
        }

        boolean firstScript = true;

        for (ButtonBarConfig buttonBarConfig : _buttonBarConfigs)
        {
            if (buttonBarConfig.getOnRenderScript() != null)
            {
                if (firstScript)
                {
                    firstScript = false;
                    out.write("<script type=\"text/javascript\">\n");
                    out.write("Ext.onReady(\n");
                    out.write("function () {\n");
                    out.write("\tvar dr = LABKEY.DataRegions[" + PageFlowUtil.jsString(getName()) + "];\n");
                }
                // We need to give any included scripts time to load, so wait for our desired function to available
                // before invoking it.
                out.write("\t var tester = function() { return undefined != " + buttonBarConfig.getOnRenderScript() + "; };\n");
                out.write("\t var onTrue = function() { " + buttonBarConfig.getOnRenderScript() + "(dr); };\n");
                out.write("\t LABKEY.Utils.onTrue( { testCallback: tester, successCallback: onTrue });\n");
            }
        }

        if (!firstScript)
        {
            out.write("});\n");
            out.write("</script>\n");
        }
    }

    protected boolean needToRenderFooter(boolean renderButtons)
    {
        return (renderButtons && _buttonBarPosition.atBottom() && (!_buttonBarPosition.atTop() || !isSmallResultSet()))
                || (_showPagination && !isSmallResultSet() && _buttonBarPosition.atBottom());
    }

    protected boolean isSmallResultSet()
    {
        if (_totalRows != null && _totalRows < 10)
            return true;
        if (_complete && getOffset() == 0 && _rowCount != null && _rowCount.intValue() < 10)
            return true;
        return false;
    }

    protected void renderPagination(RenderContext ctx, Writer out)
            throws IOException
    {
        if (isSmallResultSet())
            return;

        NumberFormat fmt = NumberFormat.getInstance();

        out.write("<div class=\"labkey-pagination\" style=\"visibility:hidden;\">");

        if (getMaxRows() > 0 && getOffset() >= 2*getMaxRows())
            paginateLink(out, "First Page", "<b>&laquo;</b> First", 0);

        if (getMaxRows() > 0 && getOffset() >= getMaxRows())
            paginateLink(out, "Previous Page", "<b>&lsaquo;</b> Prev", getOffset() - getMaxRows());

        if (_rowCount != null)
            out.write("<em>" + fmt.format(getOffset() + 1) + "</em> - <em>" + fmt.format(getOffset() + _rowCount.intValue()) + "</em> ");

        if (_totalRows != null)
        {
            if (_rowCount != null)
                out.write("of <em>" + fmt.format( _totalRows) + "</em> ");

            if (getMaxRows() > 0)
            {
                long remaining = _totalRows.longValue() - getOffset();
                long lastPageSize = _totalRows.longValue() % getMaxRows();
                if (lastPageSize == 0)
                    lastPageSize = getMaxRows();
                long lastPageOffset = _totalRows.longValue() - lastPageSize;

                if (remaining > getMaxRows())
                {
                    long nextOffset = getOffset() + getMaxRows();
                    if (nextOffset > _totalRows.longValue())
                        nextOffset = lastPageOffset;
                    paginateLink(out, "Next Page", "Next <b>&rsaquo;</b>", nextOffset);
                }

                if (remaining > 2*getMaxRows())
                    paginateLink(out, "Last Page", "Last <b>&raquo;</b>", lastPageOffset);
            }
        }
        else
        {
            if (!_complete)
                paginateLink(out, "Next Page", "Next <b>&rsaquo;</b>", getOffset() + getMaxRows());
        }

        out.write("</div>");
    }

    protected void paginateLink(Writer out, String title, String text, long newOffset) throws IOException
    {
        out.write("<a title=\"" + title + "\" href='javascript:LABKEY.DataRegions[" + PageFlowUtil.filterQuote(getName()) + "].setOffset(" + newOffset + ");'>" + text + "</a> ");
    }

    protected void renderNoRowsMessage(RenderContext ctx, Writer out, int colCount) throws IOException
    {
        out.write("<tr><td colspan=\"" + colCount + "\" nowrap=\"true\"><i>");
        out.write(getNoRowsMessage());
        out.write("</i></td></tr>\n");
    }

    protected String getNoRowsMessage()
    {
        return _noRowsMessage;
    }

    public void setNoRowsMessage(String noRowsMessage)
    {
        _noRowsMessage = noRowsMessage;
    }

    private static final String[] HIDDEN_FILTER_COLUMN_SUFFIXES = { "RowId", "DisplayName", "Description", "Label", "Caption", "Value" };
    protected String getFilterDescription(RenderContext ctx) throws IOException
    {
        SimpleFilter urlFilter = getValidFilter(ctx);
        if (urlFilter != null && !urlFilter.getWhereParamNames().isEmpty())
        {
            StringBuilder filterDesc = new StringBuilder();
            if (ctx.getView() != null && StringUtils.isNotEmpty(ctx.getView().getName()))
                filterDesc.append("View \"").append(ctx.getView().getName()).append("\"");
            else
                filterDesc.append("This view");
            filterDesc.append(" is filtered: ").append(urlFilter.getFilterText(new SimpleFilter.ColumnNameFormatter()
            {
                @Override
                public String format(String columnName)
                {
                    String formatted = super.format(columnName);
                    for (String hiddenFilter : HIDDEN_FILTER_COLUMN_SUFFIXES)
                    {
                        if (formatted.toLowerCase().endsWith("/" + hiddenFilter.toLowerCase()) ||
                            formatted.toLowerCase().endsWith("." + hiddenFilter.toLowerCase()))
                        {
                            formatted = formatted.substring(0, formatted.length() - (hiddenFilter.length() + 1));
                        }
                    }
                    int dotIndex = formatted.lastIndexOf('.');
                    if (dotIndex >= 0)
                        formatted = formatted.substring(dotIndex + 1);
                    int slashIndex = formatted.lastIndexOf('/');
                    if (slashIndex >= 0)
                        formatted = formatted.substring(slashIndex);
                    return formatted;
                }
            }));

            return filterDesc.toString();
        }

        return null;
    }

    protected void renderGridHeaderColumns(RenderContext ctx, Writer out, List<DisplayColumn> renderers)
            throws IOException, SQLException
    {
        out.write("\n<tr>");

        if (_showRecordSelectors)
        {
            out.write("<td valign=\"top\" class=\"labkey-column-header labkey-selectors");
            out.write("\">");

            out.write("<input type=checkbox title='Select/unselect all on current page' name='");
            out.write(TOGGLE_CHECKBOX_NAME);
            out.write("' onClick='LABKEY.DataRegions[" + PageFlowUtil.filterQuote(getName()) + "].selectPage(this.checked);'");
            out.write("></td>");
        }

        for (DisplayColumn renderer : renderers)
        {
            if (renderer.isVisible(ctx))
            {
                renderer.renderGridHeaderCell(ctx, out);
            }
        }

        out.write("</tr>\n");
    }

    protected void renderAggregatesTableRow(RenderContext ctx, Writer out, List<DisplayColumn> renderers, boolean borderTop, boolean borderBottom) throws IOException
    {
        if (_aggregateResults != null && !_aggregateResults.isEmpty())
        {
            // determine if all our aggregates are the same type.  If so, we don't have to
            // clutter our aggregate table row by outputting the type repeatedly (bug 1755):
            Iterator<Aggregate.Result> it = _aggregateResults.values().iterator();
            Aggregate.Type singleAggregateType = it.next().getAggregate().getType();
            while (it.hasNext() && singleAggregateType != null)
            {
                Aggregate.Result result = it.next();
                if (singleAggregateType != result.getAggregate().getType())
                    singleAggregateType = null;
            }

            out.write("<tr class=\"labkey-col-total\">");
            if (_showRecordSelectors)
            {
                out.write("<td class='labkey-selectors'>");
                if (singleAggregateType != null)
                    out.write(singleAggregateType.getFriendlyName() + ":");
                else
                    out.write("&nbsp;");
                out.write("</td>");
            }

            boolean first = true;
            for (DisplayColumn renderer : renderers)
            {
                if (renderer.isVisible(ctx))
                {
                    out.write("<td");
                    if (renderer.getTextAlign() != null)
                        out.write(" align='" + renderer.getTextAlign() + "'");
                    out.write(">");
                    // if we aren't showing record selectors, output our aggregate type
                    // at the beginning of the first row, regardless of whether or not its
                    // an aggregate col itself.  This way, the agg type always shows up far-left.
                    if (first)
                    {
                        if (!_showRecordSelectors && singleAggregateType != null)
                        {
                            out.write(singleAggregateType.getFriendlyName());
                            out.write(":&nbsp;");
                        }
                        first = false;
                    }
                    ColumnInfo col = renderer.getColumnInfo();

                    Aggregate.Result result = null;
                    if (col != null)
                    {
                        result = _aggregateResults.get(renderer.getColumnInfo().getName());
                        if (result == null)
                            _aggregateResults.get(renderer.getColumnInfo().getAlias());
                    }
                    if (result != null)
                    {
                        Format formatter = renderer.getFormat();
                        if (singleAggregateType == null)
                        {
                            out.write(result.getAggregate().getType().getFriendlyName());
                            out.write(":&nbsp;");
                        }
                        if (formatter != null)
                            out.write(formatter.format(result.getValue()));
                        else
                            out.write(result.getValue().toString());
                    }
                    else
                        out.write("&nbsp;");
                    out.write("</td>");
                }
            }
            out.write("</tr>");
        }

    }

    protected void renderFormEnd(RenderContext ctx, Writer out) throws IOException
    {
        out.write("</form>");
    }

    // Allows subclasses to add table rows at the beginning or end of the table
    /**
     * @return number of rows rendered
     */
    protected int renderTableContents(RenderContext ctx, Writer out, List<DisplayColumn> renderers) throws SQLException, IOException
    {
        Results results = ctx.getResults();
        // unwrap for efficient use of ResultSetRowMapFactory
        ResultSet rs = results.getResultSet();
        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
        int rowIndex = 0;

        while (rs.next())
        {
            ctx.setRow(factory.getRowMap(rs));
            renderTableRow(ctx, out, renderers, rowIndex++);
        }

        rs.close();
        return rowIndex;
    }

    protected String getRowClass(RenderContext ctx, int rowIndex)
    {
        boolean isErrorRow = isErrorRow(ctx, rowIndex);
        String rowClass = _shadeAlternatingRows && rowIndex % 2 == 0 ? "labkey-alternate-row" : "labkey-row";
        if (isErrorRow)
            return rowClass + " " + "labkey-error-row";
        return rowClass;
    }

    protected boolean isErrorRow(RenderContext ctx, int rowIndex)
    {
        return false;
    }

    // Allows subclasses to do pre-row and post-row processing
    // CONSIDER: Separate as renderTableRow and renderTableRowContents?
    protected void renderTableRow(RenderContext ctx, Writer out, List<DisplayColumn> renderers, int rowIndex) throws SQLException, IOException
    {
        out.write("<tr");
        String rowClass = getRowClass(ctx, rowIndex);
        if (rowClass != null)
            out.write(" class=\"" + rowClass + "\"");
        out.write(">");

        if (_showRecordSelectors)
            renderRecordSelector(ctx, out);

        for (DisplayColumn renderer : renderers)
            if (renderer.isVisible(ctx))
                renderer.renderGridDataCell(ctx, out);

        out.write("</tr>\n");
    }


    protected void renderFormHeader(RenderContext ctx, Writer out, int mode) throws IOException
    {
        out.write("<form method=\"post\" ");
        String name = getName();
        if (name != null)
        {
            out.write("id=\"" + PageFlowUtil.filter(name) + "\" ");
        }
        String actionAttr = null == getFormActionUrl() ? "" : getFormActionUrl().getLocalURIString();
        switch (mode)
        {
            case MODE_DETAILS:
                out.write("action=\"begin\">");
                break;
            case MODE_INSERT:
            case MODE_UPDATE:
                if (isFileUploadForm())
                {
                    out.write("enctype=\"multipart/form-data\" action=\"" + actionAttr + "\">");
                }
                else
                {
                    out.write("action=\"" + actionAttr + "\">");
                }
                break;
            case MODE_GRID:
                out.write("action=\"\">");
                break;
            default:
                out.write("action=\"\">");
        }

        renderHiddenFormFields(ctx, out, mode);
    }

    // Output hidden params to be posted
    protected void renderHiddenFormFields(RenderContext ctx, Writer out, int mode) throws IOException
    {
        if (mode == MODE_GRID)
            out.write("<input type=\"hidden\" name=\"" + DataRegionSelection.DATA_REGION_SELECTION_KEY + "\" value=\"" + PageFlowUtil.filter(getSelectionKey()) + "\">");
        out.write("<input type=\"hidden\" name=\"" + CSRFUtil.csrfName + "\" value=\"" + CSRFUtil.getExpectedToken(ctx.getRequest()) + "\">");
        for (Pair<String, Object> field : _hiddenFormFields)
        {
            if (field.second instanceof HString)
                out.write("<input type=\"hidden\" name=\"" + PageFlowUtil.filter(field.first) + "\" value=\"" + PageFlowUtil.filter((HString)field.second) + "\">");
            else
                out.write("<input type=\"hidden\" name=\"" + PageFlowUtil.filter(field.first) + "\" value=\"" + PageFlowUtil.filter((String)field.second) + "\">");
        }

    }

    public void setRecordSelectorValueColumns(String... columns)
    {
        _recordSelectorValueColumns = Arrays.asList(columns);
    }

    public List<String> getRecordSelectorValueColumns()
    {
        return _recordSelectorValueColumns;
    }

    protected void renderRecordSelector(RenderContext ctx, Writer out) throws IOException
    {
        Map rowMap = ctx.getRow();
        out.write("<td class='labkey-selectors' nowrap>");
        out.write("<input type=checkbox title='Select/unselect row' name='");
        out.write(SELECT_CHECKBOX_NAME);
        out.write("' ");
        String id = getRecordSelectorId(ctx);
        if (id != null)
        {
            out.write("id='");
            out.write(id);
            out.write("' ");
        }
        out.write("value=\"");
        String and = "";
        StringBuilder checkboxName = new StringBuilder();
        if (_recordSelectorValueColumns == null)
        {
            for (ColumnInfo column : getTable().getPkColumns())
            {
                Object v = column.getValue(ctx);
                // always append the comma, even if there's no value; we need to maintain the correct number
                // of values (even if they're empty) between commas for deterministic parsing (bug 6755)
                checkboxName.append(and);
                if (null != v)
                    checkboxName.append(PageFlowUtil.filter(v.toString()));
                and = ",";
            }
        }
        else
        {
            for (String valueColumnName : _recordSelectorValueColumns)
            {
                Object v = (null == rowMap ? null : rowMap.get(valueColumnName));
                // always append the comma, even if there's no value; we need to maintain the correct number
                // of values (even if they're empty) between commas for deterministic parsing (bug 6755)
                checkboxName.append(and);
                if (null != v)
                    checkboxName.append(PageFlowUtil.filter(v.toString()));
                and = ",";
            }
        }
        out.write(checkboxName.toString());
        out.write("\"");
        boolean enabled = isRecordSelectorEnabled(ctx);
        Set<String> selectedValues = ctx.getAllSelected();
        if (selectedValues.contains(checkboxName.toString()) && enabled)
        {
            out.write(" checked");
        }

        if (!enabled)
            out.write(" DISABLED");
        out.write(" onclick=\"LABKEY.DataRegions[" + PageFlowUtil.filterQuote(getName()) + "].selectRow(this);\"");
        out.write(">");
        renderExtraRecordSelectorContent(ctx, out);
        out.write("</td>");
    }

    protected boolean isRecordSelectorEnabled(RenderContext ctx)
    {
        return true;
    }

    protected void renderExtraRecordSelectorContent(RenderContext ctx, Writer out) throws IOException
    {
    }

    protected String getRecordSelectorId(RenderContext ctx)
    {
        return null;
    }

    @Deprecated
    public void renderDetails(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        ctx.setMode(MODE_DETAILS);
        render(ctx, out);
    }


    public void _renderDetails(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        if (!ctx.getViewContext().hasPermission(ReadPermission.class))
        {
            out.write("You do not have permission to read this data");
            return;
        }

        try
        {
            initDetailsResultSet(ctx);
            ResultSet rs = ctx.getResultSet();
            List<DisplayColumn> renderers = getDisplayColumns();

            renderFormHeader(ctx, out, MODE_DETAILS);

            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
            RowMap rowMap = null;

            while (rs.next())
            {
                rowMap = factory.getRowMap(rs);
                ctx.setRow(rowMap);
                out.write("<table>");

                for (DisplayColumn renderer : renderers)
                {
                    if (!renderer.isVisible(ctx) || (renderer.getDisplayModes() & MODE_DETAILS) == 0)
                        continue;
                    out.write("  <tr>\n    ");
                    renderer.renderDetailsCaptionCell(ctx, out);
                    renderer.renderDetailsData(ctx, out, 1);
                    out.write("  </tr>\n");
                }

                out.write("<tr><td style='font-size:1'>&nbsp;</td></tr>");
                out.write("</table>");
            }

            renderDetailsHiddenFields(out, rowMap);
            _detailsButtonBar.render(ctx, out);
            out.write("</form>");
        }
        finally
        {
            ResultSet rs = ctx.getResultSet();
            if (null != rs)
                rs.close();
        }
    }


    private void initDetailsResultSet(RenderContext ctx) throws SQLException
    {
        Results rs = ctx.getResults();
        if (null != rs)
            return;

        TableInfo tinfoMain = getTable();

        if (null == tinfoMain)
        {
            _log.info("DataRegion.Details: Could not find table to query from");
            throw new SQLException("No query table in DataRegion.renderTable");
        }
        else
        {
            LinkedHashMap<FieldKey,ColumnInfo> selectKeyMap = getSelectColumns();
            rs = Table.selectForDisplay(tinfoMain, selectKeyMap.values(), ctx.getBaseFilter(), ctx.getBaseSort(), getMaxRows(), getOffset());
            ctx.setResults(rs);
        }
    }

    public void renderDetailsHiddenFields(Writer out, Map rowMap) throws IOException
    {
        if (null != rowMap)
        {
            List<ColumnInfo> pkCols = getTable().getPkColumns();

            for (ColumnInfo pkCol : pkCols)
            {
                assert null != rowMap.get(pkCol.getAlias());
                out.write("<input type=hidden name=\"");
                out.write(pkCol.getName());
                out.write("\" value=\"");
                out.write(PageFlowUtil.filter(rowMap.get(pkCol.getAlias()).toString()));
                out.write("\">");
            }
        }
    }

    /**
     * Just sets a value in context and calls render
     */
    @Deprecated
    public void renderInputForm(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        ctx.setMode(MODE_INSERT);
        //Force through bottleneck
        render(ctx, out);
    }

    private void _renderInputForm(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        Map rowMap = ctx.getRow();
        //For inserts, just treat the posted strings as the rowmap
        if (null == rowMap)
        {
            TableViewForm form = ctx.getForm();
            if (null != form)
                ctx.setRow(form.getStrings());
        }
        renderForm(ctx, out);
    }

    /**
     * Just sets a value in context and calls render
     */
    public void renderUpdateForm(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        ctx.setMode(MODE_UPDATE);
        //Force through bottleneck
        render(ctx, out);
    }

    private void _renderUpdateForm(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        TableViewForm viewForm = ctx.getForm();
        Map valueMap = ctx.getRow();
        LinkedHashMap<FieldKey,ColumnInfo> selectKeyMap = getSelectColumns();
        ctx.setResults(new ResultsImpl(null, selectKeyMap));
        if (null == valueMap)
        {
            //For updates, the rowMap is the OLD version of the data.
            //If there is no old data, we reselect to get it
            if (null != viewForm.getOldValues())
            {
                //UNDONE: getOldValues() sometimes returns a map and sometimes a bean, this seems broken to me (MAB)
                Object old = viewForm.getOldValues();
                if (old instanceof Map)
                    valueMap = (Map)old;
                else
                    valueMap = new BoundMap(old);
            }
            else
            {
                Map[] maps = Table.select(getTable(), selectKeyMap.values(), new PkFilter(getTable(), viewForm.getPkVals()), null, Map.class);
                if (maps.length > 0)
                    valueMap = maps[0];
            }
            ctx.setRow(valueMap);
        }

        renderForm(ctx, out);
    }

    protected void renderMainErrors(RenderContext ctx, Writer out) throws IOException
    {
        String error = ctx.getErrors("main");
        if (null != error)
            out.write(error);
    }

    protected void renderFormField(RenderContext ctx, Writer out, DisplayColumn renderer) throws IOException
    {
        int span = _groups.isEmpty() ? 1 : (_horizontalGroups ? _groups.get(0).getColumns().size() + 1 : _groups.size());
        renderInputError(ctx, out, span, renderer);
        out.write("  <tr>\n    ");
        renderer.renderDetailsCaptionCell(ctx, out);
        if (!renderer.isEditable())
            renderer.renderDetailsData(ctx, out, span);
        else
            renderer.renderInputCell(ctx, out, span);
        out.write("  </tr>\n");
    }

    protected void renderInputError(RenderContext ctx, Writer out, int span, DisplayColumn... renderers)
            throws IOException
    {
        TableViewForm viewForm = ctx.getForm();
        Set<String> errors = new HashSet<String>();

        for (DisplayColumn renderer : renderers)
        {
            ColumnInfo col = null;
            if (renderer.isQueryColumn())
                col = renderer.getColumnInfo();

            String error = viewForm == null || col == null ? "" : ctx.getErrors(col);
            if (error != null && error.length() > 0)
            {
                errors.add(error);
            }
        }
        if (!errors.isEmpty())
        {
            out.write("  <tr><td colspan=");
            out.write(Integer.toString(span + 1));
            out.write(">");
            for (String error : errors)
            {
                out.write(error);
            }
            out.write("</td></tr>");
        }
    }

    private boolean renderExtForm(RenderContext ctx, Writer out) throws IOException
    {
        int action = ctx.getMode();
        Map valueMap = ctx.getRow();
        TableViewForm viewForm = ctx.getForm();

        List<DisplayColumn> renderers = getDisplayColumns();
        Set<String> renderedColumns = new HashSet<String>();

        //if user doesn't have read permissions, don't render anything
        if ((action == MODE_INSERT && !ctx.getViewContext().hasPermission(InsertPermission.class)) || (action == MODE_UPDATE && !ctx.getViewContext().hasPermission(UpdatePermission.class)))
        {
            out.write("You do not have permission to " +
                    (action == MODE_INSERT ? "Insert" : "Update") +
                    " data in this container.");                       
            return true;
        }

        TableInfo t = viewForm.getTable();
        ApiQueryResponse json = new ApiQueryResponse();
        ApiJsonWriter jsonOut = new ApiJsonWriter(out);
        json.initialize(new ResultsImpl(null, ctx.getFieldMap()), t, _displayColumns, null);

        out.write("<script type='text/javascript'>\n");
        out.write("Ext.namespace('DataRegionForm');\n");
        out.write("(function(){\n");
        out.write("var dr = DataRegionForm[" + PageFlowUtil.jsString(getName()) + "] = {config:{}};\n");
        out.write("dr.config.selectRowsReponse = ");
        jsonOut.write(json);
        out.write(";\n");

        out.write(")})();\n");
        out.write("</script>\n");

        if (1==1)
            return false;
        
        ButtonBar buttonBar;

        if (action == MODE_INSERT)
            buttonBar = _insertButtonBar;
        else
            buttonBar = _updateButtonBar;

        renderFormHeader(ctx, out, action);
        renderMainErrors(ctx, out);

        out.write("<table>");

        for (DisplayColumn renderer : renderers)
        {
            if (shouldRender(renderer, ctx) && null != renderer.getColumnInfo() && !renderer.getColumnInfo().isNullable())
            {
                out.write("<tr><td colspan=3>Fields marked with an asterisk * are required.</td></tr>");
                break;
            }
        }

        for (DisplayColumn renderer : renderers)
        {
            if (!shouldRender(renderer, ctx))
                continue;
                renderFormField(ctx, out, renderer);
                if (null != renderer.getColumnInfo())
                    renderedColumns.add(renderer.getColumnInfo().getPropertyName());
        }

        int span = _groups.isEmpty() ? 1 : (_horizontalGroups ? _groups.get(0).getColumns().size() + 1 : _groups.size()); // One extra one for the column to reuse the same value

        if (!_groups.isEmpty())
        {
            assert _groupHeadings != null : "Must set group headings before rendering";
            out.write("<tr><td/>");
            boolean hasCopyable = false;

            for (DisplayColumnGroup group : _groups)
            {
                if (group.isCopyable() && group.getColumns().size() > 1)
                {
                    hasCopyable = true;
                    break;
                }
            }

            if (_horizontalGroups)
            {
                if (hasCopyable)
                {
                    writeSameHeader(ctx, out);
                }
                else
                {
                    out.write("<td/>");
                }
                for (String heading : _groupHeadings)
                {
                    out.write("<td valign='bottom' class='labkey-form-label'>");
                    out.write(PageFlowUtil.filter(heading));
                    out.write("</td>");
                }
            }
            else
            {
                for (DisplayColumnGroup group : _groups)
                {
                    group.getColumns().get(0).renderDetailsCaptionCell(ctx, out);
                }
                out.write("</tr>\n<tr>");
                if (hasCopyable)
                {
                    writeSameHeader(ctx, out);
                    for (DisplayColumnGroup group : _groups)
                    {
                        if (group.isCopyable() && hasCopyable)
                        {
                            group.writeSameCheckboxCell(ctx, out);
                        }
                        else
                        {
                            out.write("<td/>");
                        }
                    }
                }
                else
                {
                    out.write("<td/>");
                }
            }
            out.write("</tr>");

            if (_horizontalGroups)
            {
                for (DisplayColumnGroup group : _groups)
                {
                    renderInputError(ctx, out, span, group.getColumns().toArray(new DisplayColumn[group.getColumns().size()]));
                    out.write("<tr>");
                    group.getColumns().get(0).renderDetailsCaptionCell(ctx, out);
                    if (group.isCopyable() && hasCopyable)
                    {
                        group.writeSameCheckboxCell(ctx, out);
                    }
                    else
                    {
                        out.write("<td/>");
                    }
                    for (DisplayColumn col : group.getColumns())
                    {
                        if (!shouldRender(col, ctx))
                            continue;
                        col.renderInputCell(ctx, out, 1);
                    }
                    out.write("\t</tr>");
                }
            }
            else
            {
                for (DisplayColumnGroup group : _groups)
                {
                    renderInputError(ctx, out, span, group.getColumns().toArray(new DisplayColumn[group.getColumns().size()]));
                }

                for (int i = 0; i < _groupHeadings.size(); i++)
                {
                    out.write("<tr>");
                    out.write("<td valign='bottom' class='labkey-form-label'>");
                    out.write(PageFlowUtil.filter(_groupHeadings.get(i)));
                    out.write("</td>");
                    for (DisplayColumnGroup group : _groups)
                    {
                        DisplayColumn col = group.getColumns().get(i);
                        if (!shouldRender(col, ctx))
                            continue;
                        col.renderInputCell(ctx, out, 1);
                    }
                    out.write("\t</tr>");
                }
            }

            out.write("<script language='javascript'>");
            for (DisplayColumnGroup group : _groups)
            {
                if (group.isCopyable())
                {
                    out.write("function " + ctx.getForm().getFormFieldName(group.getColumns().get(0).getColumnInfo()) + "Updated()\n{");
                    out.write("if (document.getElementById('" + ctx.getForm().getFormFieldName(group.getColumns().get(0).getColumnInfo()) + "CheckBox') != null && document.getElementById('" + ctx.getForm().getFormFieldName(group.getColumns().get(0).getColumnInfo()) + "CheckBox').checked) {");
                    out.write("v = document.getElementsByName('" + ctx.getForm().getFormFieldName(group.getColumns().get(0).getColumnInfo()) + "')[0].value;");
                    for (int i = 1; i < group.getColumns().size(); i++)
                    {
                        out.write("document.getElementsByName('" + ctx.getForm().getFormFieldName(group.getColumns().get(i).getColumnInfo()) + "')[0].value = v;");
                    }
                    out.write("}}\n");
                    out.write("e = document.getElementsByName('" + ctx.getForm().getFormFieldName(group.getColumns().get(0).getColumnInfo()) + "')[0];\n");
                    out.write("e.onchange=" +ctx.getForm().getFormFieldName(group.getColumns().get(0).getColumnInfo()) + "Updated;");
                    out.write("e.onkeyup=" + ctx.getForm().getFormFieldName(group.getColumns().get(0).getColumnInfo()) + "Updated;");
                }
            }
            out.write("</script>");
        }

        out.write("<tr><td colspan=" + (span + 1) + " align=left>");

        if (action == MODE_UPDATE && valueMap != null)
        {
            if (valueMap instanceof BoundMap)
                renderOldValues(out, ((BoundMap)valueMap).getBean());
            else
                renderOldValues(out, valueMap, ctx.getFieldMap());
        }

        //Make sure all pks are included
        if (action == MODE_UPDATE)
        {
            List<String> pkColNames = getTable().getPkColumnNames();
            for (String pkColName : pkColNames)
            {
                if (!renderedColumns.contains(pkColName)) {
                    Object pkVal = null;
                    //UNDONE: Should we require a viewForm whenver someone
                    //posts? I tend to think so.
                    if (null != viewForm)
                        pkVal = viewForm.getPkVal();        //TODO: Support multiple PKs?

                    if (pkVal == null && valueMap != null)
                        pkVal = valueMap.get(pkColName);

                    if (null != pkVal) {
                        out.write("<input type='hidden' name='");
                        out.write(pkColName);
                        out.write("' value=\"");
                        out.write(PageFlowUtil.filter(pkVal.toString()));
                        out.write("\">");
                    }
                    renderedColumns.add(pkColName);
                }
            }
        }

        buttonBar.render(ctx, out);
        out.write("</td></tr>");
        out.write("</table>");
        renderFormEnd(ctx, out);
        return true;
    }

    
    private void renderForm(RenderContext ctx, Writer out) throws IOException
    {
        if (false)
        {
            if (renderExtForm(ctx, out))
                return;
        }

        int action = ctx.getMode();
        Map valueMap = ctx.getRow();
        TableViewForm viewForm = ctx.getForm();

        List<DisplayColumn> renderers = getDisplayColumns();
        Set<String> renderedColumns = Sets.newCaseInsensitiveHashSet();
        ViewContext viewContext = ctx.getViewContext();

        //if user doesn't have read permissions, don't render anything
        if ((action == MODE_INSERT && !viewContext.hasPermission(InsertPermission.class)) || (action == MODE_UPDATE && !viewContext.hasPermission(UpdatePermission.class)))
        {
            out.write("You do not have permission to " +
                    (action == MODE_INSERT ? "Insert" : "Update") +
                    " data in this container.");
            return;
        }

        ButtonBar buttonBar;

        if (action == MODE_INSERT)
            buttonBar = _insertButtonBar;
        else
            buttonBar = _updateButtonBar;

        renderFormHeader(ctx, out, action);
        renderMainErrors(ctx, out);

        out.write("<table>");

        for (DisplayColumn renderer : renderers)
        {
            if (shouldRender(renderer, ctx) && null != renderer.getColumnInfo() && !renderer.getColumnInfo().isNullable())
            {
                out.write("<tr><td colspan=3>Fields marked with an asterisk * are required.</td></tr>");
                break;
            }
        }

        for (DisplayColumn renderer : renderers)
        {
            if (!shouldRender(renderer, ctx))
                continue;
            renderFormField(ctx, out, renderer);
            if (null != renderer.getColumnInfo())
                renderedColumns.add(renderer.getColumnInfo().getPropertyName());
        }

        int span = _groups.isEmpty() ? 1 : (_horizontalGroups ? _groups.get(0).getColumns().size() + 1 : _groups.size()); // One extra one for the column to reuse the same value

        if (!_groups.isEmpty())
        {
            assert _groupHeadings != null : "Must set group headings before rendering";
            out.write("<tr><td/>");
            boolean hasCopyable = false;

            for (DisplayColumnGroup group : _groups)
            {
                if (group.isCopyable() && group.getColumns().size() > 1)
                {
                    hasCopyable = true;
                    break;
                }
            }

            if (_horizontalGroups)
            {
                if (hasCopyable)
                {
                    writeSameHeader(ctx, out);
                }
                else
                {
                    out.write("<td/>");
                }
                for (String heading : _groupHeadings)
                {
                    out.write("<td valign='bottom' class='labkey-form-label'>");
                    out.write(PageFlowUtil.filter(heading));
                    out.write("</td>");
                }
            }
            else
            {
                for (DisplayColumnGroup group : _groups)
                {
                    group.getColumns().get(0).renderDetailsCaptionCell(ctx, out);
                }
                out.write("</tr>\n<tr>");
                if (hasCopyable)
                {
                    writeSameHeader(ctx, out);
                    for (DisplayColumnGroup group : _groups)
                    {
                        if (group.isCopyable() && hasCopyable)
                        {
                            group.writeSameCheckboxCell(ctx, out);
                        }
                        else
                        {
                            out.write("<td/>");
                        }
                    }
                }
                else
                {
                    out.write("<td/>");
                }
            }
            out.write("</tr>");

            if (_horizontalGroups)
            {
                for (DisplayColumnGroup group : _groups)
                {
                    renderInputError(ctx, out, span, group.getColumns().toArray(new DisplayColumn[group.getColumns().size()]));
                    out.write("<tr>");
                    group.getColumns().get(0).renderDetailsCaptionCell(ctx, out);
                    if (group.isCopyable() && hasCopyable)
                    {
                        group.writeSameCheckboxCell(ctx, out);
                    }
                    else
                    {
                        out.write("<td/>");
                    }
                    for (DisplayColumn col : group.getColumns())
                    {
                        if (!shouldRender(col, ctx))
                            continue;
                        col.renderInputCell(ctx, out, 1);
                    }
                    out.write("\t</tr>");
                }
            }
            else
            {
                for (DisplayColumnGroup group : _groups)
                {
                    renderInputError(ctx, out, span, group.getColumns().toArray(new DisplayColumn[group.getColumns().size()]));
                }

                for (int i = 0; i < _groupHeadings.size(); i++)
                {
                    out.write("<tr");
                    String rowClass = getRowClass(ctx, i);
                    if (rowClass != null)
                        out.write(" class=\"" + rowClass + "\"");
                    out.write(">");
                    out.write("<td class='labkey-form-label' nowrap>");
                    out.write(PageFlowUtil.filter(_groupHeadings.get(i)));
                    out.write("</td>");
                    for (DisplayColumnGroup group : _groups)
                    {
                        DisplayColumn col = group.getColumns().get(i);
                        if (!shouldRender(col, ctx))
                            continue;
                        col.renderInputCell(ctx, out, 1);
                    }
                    out.write("\t</tr>");
                }
            }

            out.write("<script language='javascript'>");
            for (DisplayColumnGroup group : _groups)
            {
                if (group.isCopyable())
                {
                    ColumnInfo col0 = group.getColumns().get(0).getColumnInfo();
                    String groupName = ctx.getForm().getFormFieldName(col0);
                    out.write("function " + groupName + "Updated() {\n");
                    out.write("  if (document.getElementById('" + groupName + "CheckBox') != null && document.getElementById('" + groupName + "CheckBox').checked) {\n");

                    String valueProperty = "value";
                    if (col0.inputType.equalsIgnoreCase("select"))
                    {
                        valueProperty = "selectedIndex";
                    }
                    else if (col0.inputType.equalsIgnoreCase("checkbox"))
                    {
                        valueProperty = "checked";
                    }
                    out.write("    var v = document.getElementsByName('" + groupName + "')[0]." + valueProperty + ";\n");
                    for (int i = 1; i < group.getColumns().size(); i++)
                    {
                        out.write("    document.getElementsByName('" + ctx.getForm().getFormFieldName(group.getColumns().get(i).getColumnInfo()) + "')[0]." + valueProperty + " = v;\n");
                    }
                    out.write("  }\n");
                    out.write("}\n");
                    out.write("var e = document.getElementsByName('" + groupName + "')[0];\n");
                    out.write("e.onchange=" + groupName + "Updated;\n");
                    out.write("e.onkeyup=" + groupName + "Updated;\n");
                    out.write("\n");
                }
            }
            out.write("</script>");
        }

        out.write("<tr><td colspan=" + (span + 1) + " align=left>");

        if (action == MODE_UPDATE && valueMap != null)
        {
            if (valueMap instanceof BoundMap)
                renderOldValues(out, ((BoundMap)valueMap).getBean());
            else
                renderOldValues(out, valueMap, ctx.getFieldMap());
        }

        //Make sure all pks are included
        if (action == MODE_UPDATE)
        {
            List<String> pkColNames = getTable().getPkColumnNames();
            for (String pkColName : pkColNames)
            {
                if (!renderedColumns.contains(pkColName)) {
                    Object pkVal = null;
                    //UNDONE: Should we require a viewForm whenver someone
                    //posts? I tend to think so.
                    if (null != viewForm)
                        pkVal = viewForm.getPkVal();        //TODO: Support multiple PKs?

                    if (pkVal == null && valueMap != null)
                        pkVal = valueMap.get(pkColName);

                    if (null != pkVal) {
                        out.write("<input type='hidden' name='");
                        out.write(pkColName);
                        out.write("' value=\"");
                        out.write(PageFlowUtil.filter(pkVal.toString()));
                        out.write("\">");
                    }
                }
            }
        }

        buttonBar.render(ctx, out);
        out.write("</td></tr>");
        out.write("</table>");
        renderFormEnd(ctx, out);
    }

    
    private void writeSameHeader(RenderContext ctx, Writer out)
            throws IOException
    {
        out.write("<td class='labkey-form-label'>");
        out.write("<input type='checkbox' name='~~SELECTALL~~' onchange=\"");
        for (DisplayColumnGroup group : _groups)
        {
            if (group.isCopyable())
            {
                out.write("document.getElementById('" + ctx.getForm().getFormFieldName(group.getColumns().get(0).getColumnInfo()) + "CheckBox').checked = this.checked; document.getElementById('" + ctx.getForm().getFormFieldName(group.getColumns().get(0).getColumnInfo()) + "CheckBox').onchange();");
            }
        }
        out.write("\" />");
        out.write("Same" + PageFlowUtil.helpPopup("Same", "If selected, all entries on this row will have the same value") + "</td>");
    }

    protected boolean shouldRender(DisplayColumn renderer, RenderContext ctx)
    {
        return (renderer.isVisible(ctx) && (renderer.getDisplayModes() & (MODE_UPDATE | MODE_INSERT)) != 0);
    }

    private Boolean _isFileUploadForm = null;

    private boolean isFileUploadForm()
    {
        boolean hasFileFields = false;
        if (null != _isFileUploadForm)
            return _isFileUploadForm.booleanValue();

        for (DisplayColumn dc : _displayColumns) {
            ColumnInfo col = dc.getColumnInfo();
            if (null != col && col.getInputType().equalsIgnoreCase("file")) {
                hasFileFields = true;
                break;
            }
        }

        _isFileUploadForm = Boolean.valueOf(hasFileFields);

        return hasFileFields;
    }


    private void renderOldValues(Writer out, Object values) throws IOException
    {
        out.write("<input name='.oldValues' type=hidden value=\"");
        out.write(PageFlowUtil.encodeObject(values));
        out.write("\">");
    }


    // RowMap keys are the ResultSet alias names, which might be completely mangled.  So, create a new map
    // that's column name -> value and pass it to renderOldValues
    private void renderOldValues(Writer out, Map<String, Object> valueMap, Map<FieldKey, ColumnInfo> fieldMap) throws IOException
    {
        Map<String, Object> map = new HashMap<String, Object>(valueMap.size());

        for (Map.Entry<FieldKey, ColumnInfo> entry : fieldMap.entrySet())
        {
            FieldKey fk = entry.getKey();

            if (1 == fk.getParts().size())
            {
                Object value;

                if (valueMap.containsKey(fk.getName()))
                {
                    value = valueMap.get(fk.getName());
                }
                else
                {
                    ColumnInfo info = entry.getValue();
                    value = info.getValue(valueMap);
                }

                map.put(fk.getName(), value);
            }
        }

        renderOldValues(out, map);
    }


    public static List<ColumnInfo> colInfosFromMetaData(ResultSetMetaData md) throws SQLException
    {
        int columnCount = md.getColumnCount();
        List<ColumnInfo> cols = new LinkedList<ColumnInfo>();

        for (int i = 1; i <= columnCount; i++)
            cols.add(new ColumnInfo(md, i));

        return cols;
    }


    /**
     * Render the data region. All rendering SHOULD go through this function
     * public renderForm, renderTable methods actually all go through here
     * after setting some state
     */
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        int mode = _defaultMode;
        if (ctx.getMode() != MODE_NONE)
            mode = ctx.getMode();
        else
            ctx.setMode(mode);

        DataRegion oldRegion = ctx.getCurrentRegion();
        ctx.setCurrentRegion(this);
        try
        {
            switch (mode)
            {
                case MODE_INSERT:
                    _renderInputForm(ctx, out);
                    return;
                case MODE_UPDATE:
                    _renderUpdateForm(ctx, out);
                    return;
                case MODE_DETAILS:
                    _renderDetails(ctx, out);
                    return;
                default:
                    _renderTable(ctx, out);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
        finally
        {
            ctx.setCurrentRegion(oldRegion);
        }
    }

    public void setShadeAlternatingRows(boolean shadeAlternatingRows)
    {
        _shadeAlternatingRows = shadeAlternatingRows;
    }

    public boolean isShadeAlternatingRows()
    {
        return _shadeAlternatingRows;
    }

    public void setAggregateRowPosition(boolean aggregateRowFirst, boolean aggregateRowLast)
    {
        _aggregateRowFirst = aggregateRowFirst;
        _aggregateRowLast = aggregateRowLast;
    }

    public Map<String, Aggregate.Result> getAggregateResults()
    {
        return _aggregateResults;
    }

    public void setGroupHeadings(List<String> headings)
    {
        _groupHeadings = headings;
    }

    public boolean getShowPagination()
    {
        return _showPagination;
    }

    public void setShowPagination(boolean showPagination)
    {
        _showPagination = showPagination;
    }

    public void setShowPaginationCount(boolean showPaginationCount)
    {
        _showPaginationCount = showPaginationCount;
    }

    public enum ButtonBarPosition
    {
        NONE(false, false),
        TOP(true, false),
        BOTTOM(false, true),
        BOTH(true, true);
        ButtonBarPosition(boolean atTop, boolean atBottom)
        {
            _atTop = atTop;
            _atBottom = atBottom;
        }
        final private boolean _atTop;
        final private boolean _atBottom;
        public boolean atTop()
        {
            return _atTop;
        }
        public boolean atBottom()
        {
            return _atBottom;
        }
        public boolean atBoth()
        {
            return _atTop && _atBottom;
        }
    }

    public void setButtonBarPosition(ButtonBarPosition p)
    {
        _buttonBarPosition = p;
    }

    public ButtonBarPosition getButtonBarPosition()
    {
        return _buttonBarPosition;
    }


    public boolean isAllowAsync()
    {
        return allowAsync;
    }

    public void setAllowAsync(boolean allowAsync)
    {
        this.allowAsync = allowAsync;
    }

    public ActionURL getFormActionUrl()
    {
        return _formActionUrl;
    }

    public void setFormActionUrl(ActionURL formActionUrl)
    {
        _formActionUrl = formActionUrl;
    }

    public void addGroup(DisplayColumnGroup group)
    {
        assert _groups.isEmpty() || _groups.get(0).getColumns().size() == group.getColumns().size() : "Must have matching column counts";
        _groups.add(group);
    }

    public boolean isHorizontalGroups()
    {
        return _horizontalGroups;
    }

    public void setHorizontalGroups(boolean horizontalGroups)
    {
        _horizontalGroups = horizontalGroups;
    }

    public String getJavascriptFormReference(boolean htmlEncode)
    {
        String name = htmlEncode ? PageFlowUtil.filterQuote(getName()) : PageFlowUtil.jsString(getName());
        return "document.forms[" + name + "]";
    }

    public boolean isShowBorders()
    {
        return _showBorders;
    }

    public void setShowBorders(boolean showBorders)
    {
        _showBorders = showBorders;
    }

    public boolean isShowSurroundingBorder()
    {
        return _showSurroundingBorder;
    }

    public void setShowSurroundingBorder(boolean showSurroundingBorder)
    {
        _showSurroundingBorder = showSurroundingBorder;
    }
}
