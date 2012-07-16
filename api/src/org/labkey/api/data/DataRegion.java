/*
 * Copyright (c) 2004-2012 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.collections.RowMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.query.AggregateRowConfig;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.HasPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.Format;
import java.text.NumberFormat;
import java.util.*;

// TODO: it seems to me that data region and its views (ParameterView) don't belong in this package (matt)

public class DataRegion extends AbstractDataRegion
{
    private static final Logger _log = Logger.getLogger(DataRegion.class);
    private List<DisplayColumn> _displayColumns = new ArrayList<DisplayColumn>();
    private List<DisplayColumnGroup> _groups = new ArrayList<DisplayColumnGroup>();
    private Map<String, List<Aggregate.Result>> _aggregateResults = null;
    private AggregateRowConfig _aggregateRowConfig = new AggregateRowConfig(false, true);
    private TableInfo _table = null;
    private boolean _showRecordSelectors = false;
    protected boolean _showSelectMessage = true;
    private boolean _showFilters = true;
    private boolean _sortable = true;
    private boolean _showFilterDescription = true;
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
    private ButtonBarPosition _buttonBarPosition = ButtonBarPosition.TOP;
    private boolean allowAsync = false;
    private ActionURL _formActionUrl = null;

    private String _noRowsMessage = "No data to show.";

    private boolean _shadeAlternatingRows = true;
    private boolean _showBorders = true;
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


    @SuppressWarnings({"AssertWithSideEffects"})
    public LinkedHashMap<FieldKey, ColumnInfo> getSelectColumns()
    {
        TableInfo table = getTable();
        List<DisplayColumn> displayCols = getDisplayColumns();

        // includes old DisplayColumn.addQueryColumns()
        List<ColumnInfo> originalColumns = RenderContext.getSelectColumns(displayCols, table);

        assert Table.checkAllColumns(table, originalColumns, "DataRegion.getSelectColumns() originalColumns");

        // allow DataRegion subclass to add columns (yuck)
        LinkedHashSet<ColumnInfo> columns = new LinkedHashSet<ColumnInfo>(originalColumns);
        addQueryColumns(columns);

        assert Table.checkAllColumns(table, columns, "DataRegion.getSelectColumns() columns");

        LinkedHashMap<FieldKey, ColumnInfo> ret = QueryService.get().getColumns(table, Collections.<FieldKey>emptySet(), columns);

        assert Table.checkAllColumns(table, columns, "DataRegion.getSelectColumns()");

        return ret;
    }


    public void setShowRecordSelectors(boolean show)
    {
        _showRecordSelectors = show;
    }

    /** Called after configuring the button bar, check if any buttons require selection (e.g., "Delete"). */
    public boolean getShowRecordSelectors(RenderContext ctx)
    {
        // Issue 11569: QueryView.showRecordSelectors should take metadata override buttons into account
        return _showRecordSelectors || (_buttonBarPosition != ButtonBarPosition.NONE && _gridButtonBar.hasRequiresSelectionButton(ctx));
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

    public int getMaxRows()
    {
        return getSettings() != null ? getSettings().getMaxRows() : _maxRows;
    }

    /** Use {@link QuerySettings#setMaxRows(int)}. */
    @Deprecated
    public void setMaxRows(int maxRows)
    {
        if (getSettings() != null)
            getSettings().setMaxRows(maxRows);
        else
            _maxRows = maxRows;
    }

    public long getOffset()
    {
        return getSettings() != null ? getSettings().getOffset() : _offset;
    }

    /** Use {@link QuerySettings#setOffset(long)}. */
    @Deprecated
    public void setOffset(long offset)
    {
        if (getSettings() != null)
            getSettings().setOffset(offset);
        else
            _offset = offset;
    }

    public ShowRows getShowRows()
    {
        return getSettings() != null ? getSettings().getShowRows() : ShowRows.PAGINATED;
    }

    public String getSelectionKey()
    {
        if (getSettings() != null && getSettings().getSelectionKey() != null)
            return getSettings().getSelectionKey();
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
                    throw new SQLException("Table or query not found: " + getSettings().getQueryName());
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
        return ctx.getResultSet(selectKeyMap, getTable(), getQueryParameters(), getMaxRows(), getOffset(), getName(), async);
    }


    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        // no extra query columns added by default
    }

    public Map<String, List<Aggregate.Result>> getAggregateResults(RenderContext ctx) throws SQLException, IOException
    {
        ResultSet rs = ctx.getResults();
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
            _aggregateResults = ctx.getAggregates(_displayColumns, getTable(), getName(), newAggregates, getQueryParameters(), isAllowAsync());
            List<Aggregate.Result> result = _aggregateResults.remove(Aggregate.STAR);

            //Issue 14863: add null check
            if (result != null && result.size() > 0)
                _totalRows = (Long)(result.get(0)).getValue();
        }
        else
        {
            _aggregateResults =  ctx.getAggregates(_displayColumns, getTable(), getName(), ctx.getBaseAggregates(), getQueryParameters(), isAllowAsync());
        }

        // TODO: Move this into RenderContext?
        ActionURL url = ctx.getSortFilterURLHelper();
        PageFlowUtil.saveLastFilter(ctx.getViewContext(), url, "");
        return _aggregateResults;
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

    public class ParameterViewBean
    {
        public String dataRegionName;
        public Collection<QueryService.ParameterDecl> params;
        public Map<String,Object> values;
        ParameterViewBean(String dataRegionName, Collection<QueryService.ParameterDecl> params, Map<String,Object> values)
        {
            this.dataRegionName = dataRegionName;
            this.params = params;
            this.values = values;
        }
    }

    public class ParameterView extends JspView<ParameterViewBean>
    {
        ParameterView(Collection<QueryService.ParameterDecl> params, Map<String,Object> defaults)
        {
            super(DataRegion.class, "parameterForm.jsp", new ParameterViewBean(DataRegion.this.getName(), params, defaults));
        }
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
            boolean errorCreatingResults = false;

            boolean showParameterForm = false;
            try
            {
                TableInfo t = getTable();
                if (null != t && !t.getNamedParameters().isEmpty() && getQueryParameters().isEmpty())
                    showParameterForm = true;
                else
                    rs = getResultSet(ctx);
            }
            catch (QueryService.NamedParameterNotProvided x)
            {
                showParameterForm = true;
            }
            catch (SQLException x)
            {
                errorCreatingResults = true;
                headerMessage.append("<span class=error>").append(PageFlowUtil.filter(x.getMessage())).append("</span><br>");
            }
            catch (RuntimeSQLException x)
            {
                errorCreatingResults = true;
                headerMessage.append("<span class=error>").append(PageFlowUtil.filter(x.getMessage())).append("</span><br>");
            }
            catch (ConversionException x)
            {
                errorCreatingResults = true;
                headerMessage.append("<span class=error>").append(PageFlowUtil.filter(x.getMessage())).append("</span><br>");
            }


            if (showParameterForm)
            {
                _renderParameterForm(ctx, out);
            }
            else
            {
                _renderTable(ctx, out, rs, headerMessage, errorCreatingResults);
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    private void _renderParameterForm(RenderContext ctx, Writer out) throws IOException
    {
        boolean showRecordSelectors = false;
        _allowHeaderLock = false;

        try
        {
            Collection<QueryService.ParameterDecl> params = getTable().getNamedParameters();
            (new ParameterView(params, null)).render(ctx.getViewContext().getRequest(), ctx.getViewContext().getResponse());
            renderHeaderScript(ctx, out, Collections.<String, String>emptyMap(), showRecordSelectors);
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private void _renderTable(RenderContext ctx, Writer out, ResultSet rs, StringBuilder headerMessage, boolean errorCreatingResults) throws IOException, SQLException
    {
        boolean renderButtons = _gridButtonBar.shouldRender(ctx);
        if (renderButtons && _buttonBarConfigs != null && !_buttonBarConfigs.isEmpty())
        {
            if (_gridButtonBar.isLocked())
                _gridButtonBar = new ButtonBar(_gridButtonBar);
            _gridButtonBar.setConfigs(ctx, _buttonBarConfigs);
        }

        boolean showRecordSelectors = getShowRecordSelectors(ctx);

        List<DisplayColumn> renderers = getDisplayColumns();
        Map<FieldKey,ColumnInfo> fieldMap = ctx.getFieldMap();
        Set<FieldKey> fieldKeys = null==fieldMap ? null : fieldMap.keySet();

        //determine number of HTML table columns...watch out for hidden display columns
        //and include one extra if showing record selectors
        int colCount = 0;
        for (DisplayColumn col : renderers)
        {
            // check that detail columns have valid urls
            if (null != fieldKeys && col instanceof DetailsColumn)
            {
                DetailsColumn details = (DetailsColumn)col;
                Container c = ctx.getContainer();
                if (!details.isValid(fieldKeys, c))
                    details.setVisible(false);
            }
            if (col.isVisible(ctx))
                colCount++;
        }
        if (showRecordSelectors)
            colCount++;


        if (rs instanceof Table.TableResultSet && ((Table.TableResultSet)rs).getSize() != -1)
        {
            _rowCount = ((Table.TableResultSet)rs).getSize();
            if (_complete && _totalRows == null)
                _totalRows = getOffset() + _rowCount.intValue();
        }

        StringBuilder viewMsg = new StringBuilder();
        StringBuilder filterMsg = new StringBuilder();

        Map<String, String> messages = new LinkedHashMap<String, String>();

        if (errorCreatingResults)
        {
            _showPagination = false;
            _allowHeaderLock = false;
        }
        else
        {
            //issue 13538: do not try to display filters if error, since this could result in a ConversionException
            addFilterMessage(filterMsg, ctx, isShowFilterDescription());
        }

        // don't generate a view message if this is the default view and the filter is empty
        if (!isDefaultView(ctx) || filterMsg.length() > 0)
            addViewMessage(viewMsg, ctx);

        if (headerMessage.length() > 0)
            messages.put(MessagePart.header.name(), headerMessage.toString());
        if (viewMsg.length() > 0)
            messages.put(MessagePart.view.name(), viewMsg.toString());
        if (filterMsg.length() > 0)
            messages.put(MessagePart.filter.name(), filterMsg.toString());

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

        renderRegionStart(ctx, out, renderButtons, showRecordSelectors, renderers);

        renderHeader(ctx, out, renderButtons, colCount);

        if (!errorCreatingResults)
        {
            renderGridHeaderColumns(ctx, out, showRecordSelectors, renderers);

            if (_aggregateRowConfig.getAggregateRowFirst())
                renderAggregatesTableRow(ctx, out, showRecordSelectors, renderers);

            int rows = renderTableContents(ctx, out, showRecordSelectors, renderers);
            if (rows == 0)
            {
                renderNoRowsMessage(ctx, out, colCount);
            }

            if (_aggregateRowConfig.getAggregateRowLast())
                renderAggregatesTableRow(ctx, out, showRecordSelectors, renderers);

        }

        renderFooter(ctx, out, renderButtons, colCount);

        renderRegionEnd(ctx, out, renderButtons, renderers);

        renderHeaderScript(ctx, out, messages, showRecordSelectors);
    }

    protected void renderRegionStart(RenderContext ctx, Writer out, boolean renderButtons, boolean showRecordSelectors, List<DisplayColumn> renderers) throws IOException
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
        if (showRecordSelectors)
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

    protected boolean shouldRenderHeader(boolean renderButtons)
    {
        return ((renderButtons && _buttonBarPosition.atTop() && _gridButtonBar.getList().size() > 0)
                || (_showPagination && _buttonBarPosition.atTop() && !isSmallResultSet()));
    }

    @Override
    protected void renderButtons(RenderContext ctx, Writer out) throws IOException
    {
      //adjust position if bbar supplies a position value
        if (_gridButtonBar.getConfiguredPosition() != null)
            setButtonBarPosition(_gridButtonBar.getConfiguredPosition());

        if (_buttonBarPosition.atTop())
            _gridButtonBar.render(ctx, out);
    }

    /**
     * In almost all cases this is just the standard list of DisplayColumns, but some special cases
     * like the MS2 nested grids may have more columns that get rendered by a nested DataRegion
     */
    protected List<DisplayColumn> getColumnsForMetadata()
    {
        return getDisplayColumns();
    }

    @Override
    protected JSONObject getDataRegionJSON(RenderContext ctx, boolean showRecordSelectors)
    {
        JSONObject dataRegionJSON = super.getDataRegionJSON(ctx, showRecordSelectors);

        if (ctx.getView() != null)
        {
            dataRegionJSON.put("view", QueryService.get().getCustomViewProperties(ctx.getView(), ctx.getViewContext().getUser()));
        }

        //permissions
        JSONObject permissionJSON = new JSONObject();
        TableInfo table = getTable();
        if(table != null)
        {
            permissionJSON.put("insert", table.hasPermission(ctx.getViewContext().getUser(), InsertPermission.class));
            permissionJSON.put("update", table.hasPermission(ctx.getViewContext().getUser(), UpdatePermission.class));
            permissionJSON.put("delete", table.hasPermission(ctx.getViewContext().getUser(), DeletePermission.class));
            permissionJSON.put("admin", table.hasPermission(ctx.getViewContext().getUser(), AdminPermission.class));
        }
        dataRegionJSON.put("permissions", permissionJSON);

        dataRegionJSON.put("complete", _complete);
        dataRegionJSON.put("offset", getOffset());
        dataRegionJSON.put("maxRows", getMaxRows());
        dataRegionJSON.put("totalRows", _totalRows);
        dataRegionJSON.put("rowCount", _rowCount);
        dataRegionJSON.put("showRows", getShowRows().toString().toLowerCase());
        dataRegionJSON.put("showRecordSelectors", showRecordSelectors);
        dataRegionJSON.put("showSelectMessage", _showSelectMessage);
        dataRegionJSON.put("selectionKey", getSelectionKey());
        dataRegionJSON.put("requestURL", ctx.getViewContext().getActionURL().toString());
        dataRegionJSON.put("selectorCols", _recordSelectorValueColumns == null ? null : _recordSelectorValueColumns.toString());
        JSONArray columnsJSON = new JSONArray((List)JsonWriter.getNativeColProps(getColumnsForMetadata(), null, false));
        // Write out a pretty-printed version in dev mode
        dataRegionJSON.put("columns", columnsJSON);

        // TODO: Don't get available container filters from render context.
        // 11082: Populate customize view with list of allowable container filters from the QueryView
        List<ContainerFilter.Type> allowableContainerFilterTypes = (List<ContainerFilter.Type>)ctx.get("allowableContainerFilterTypes");
        if (allowableContainerFilterTypes != null && allowableContainerFilterTypes.size() > 0)
        {
            JSONArray containerFiltersJSON = new JSONArray();
            dataRegionJSON.put("allowableContainerFilters", containerFiltersJSON);
            for (ContainerFilter.Type type : allowableContainerFilterTypes)
            {
                JSONArray containerFilterJSON = new JSONArray();
                containerFiltersJSON.put(containerFilterJSON);
                containerFilterJSON.put(type.name());
                containerFilterJSON.put(type.toString());
            }
        }
        return dataRegionJSON;
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
                renderPagination(ctx, out, PaginationLocation.BOTTOM);
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
                }
                // We need to give any included scripts time to load, so wait for our desired function to available
                // before invoking it.
                //NOTE: because the onRender function could be part of a namespace not yet defined, we split the string and test
                //whether each node exists
                out.write("var tester = function() {  \n" +
                "\tvar name = '" + buttonBarConfig.getOnRenderScript() + "'.split('.'); \n" +
                "\tvar obj = this; \n" +
                "\tfor(var i=0;i<name.length;i++){\n" +
                "\tobj = obj[name[i]];\n" +
                "\tif(undefined === obj) return false;\n" +
                "\tif(!LABKEY || !LABKEY.DataRegions || !LABKEY.DataRegions[" + PageFlowUtil.jsString(getName()) + "]) return false;\n" +
                "\treturn true;\n" +
                "\t}\n" +
                "}\n" +
                "var onTrue = function() { " +
                "\t" + buttonBarConfig.getOnRenderScript() + "(LABKEY.DataRegions[" + PageFlowUtil.jsString(getName()) + "]); \n" +
                "}\n" +
                "LABKEY.Utils.onTrue( { testCallback: tester, scope: this, success: onTrue, failure: function(e){console.log('Error calling dataregion onRender function');console.log(e);} });\n");
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
        if (_totalRows != null && _totalRows < 5)
            return true;
        if (_complete && getOffset() == 0 && _rowCount != null && _rowCount.intValue() < 5)
            return true;
        return false;
    }

    protected void renderPagination(RenderContext ctx, Writer out, PaginationLocation location) throws IOException
    {
        if (_showPagination)
        {
            if ((_buttonBarPosition.atTop() && location == PaginationLocation.TOP) ||
                (_buttonBarPosition._atBottom && location == PaginationLocation.BOTTOM))
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
        }
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

    protected void renderGridHeaderColumns(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers)
            throws IOException, SQLException
    {
        out.write("\n<tr id=\"" + PageFlowUtil.filter("dataregion_column_header_row_" + getName()) + "\">");

        if (showRecordSelectors)
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

        if (this.getAllowHeaderLock())
        {
            out.write("\n<tr class=\"dataregion_column_header_row_spacer\" style=\"display: none; text-colo\" id=\"" + PageFlowUtil.filter("dataregion_column_header_row_spacer_" + getName()) +"\">");

            if (showRecordSelectors)
            {
                out.write("<td valign=\"top\" class=\"labkey-column-header labkey-selectors");
                out.write("\">");

                out.write("<input type=checkbox title='Select/unselect all on current page' ");
                out.write(" onClick='LABKEY.DataRegions[" + PageFlowUtil.filterQuote(getName()) + "].selectPage(this.checked);'");
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
    }

    protected void renderAggregatesTableRow(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers) throws IOException
    {
        if (_aggregateResults != null && !_aggregateResults.isEmpty())
        {
            // determine if all our aggregates are the same type.  If so, we don't have to
            // clutter our aggregate table row by outputting the type repeatedly (bug 1755):

            //we also find the set of distinct aggregate labels and output 1 row per label
            Iterator<Map.Entry<String, List<Aggregate.Result>>> it = _aggregateResults.entrySet().iterator();
            Set<String> aggregateLabels = new HashSet<String>();

            while (it.hasNext())
            {
                Map.Entry<String, List<Aggregate.Result>> result = it.next();
                for(Aggregate.Result r : result.getValue())
                {
                    aggregateLabels.add(r.getAggregate().getDisplayString());
                }
            }

            out.write("<tr class=\"labkey-col-total labkey-row\">");
            if (showRecordSelectors)
            {
                out.write("<td nowrap class='labkey-selectors'>");
                if (aggregateLabels.size() == 1)
                    out.write(aggregateLabels.toArray()[0] + ":");
                else
                    out.write("&nbsp;");
                out.write("</td>");
            }

            boolean first = true;
            for (DisplayColumn renderer : renderers)
            {
                if (renderer.isVisible(ctx))
                {
                    out.write("<td nowrap ");
                    if (renderer.getTextAlign() != null)
                        out.write(" align='" + renderer.getTextAlign() + "'");
                    out.write(">");
                    // if we aren't showing record selectors, output our aggregate type
                    // at the beginning of the first row, regardless of whether or not its
                    // an aggregate col itself.  This way, the agg type always shows up far-left.
                    if (first)
                    {
                        if (!showRecordSelectors && aggregateLabels.size() == 1)
                        {
                            out.write(aggregateLabels.toArray()[0].toString());
                            out.write(":&nbsp;");
                        }
                        first = false;
                    }
                    ColumnInfo col = renderer.getColumnInfo();

                    List<Aggregate.Result> result = null;
                    if (col != null)
                    {
                        result = _aggregateResults.get(renderer.getColumnInfo().getName());
                        if (result == null)
                            _aggregateResults.get(renderer.getColumnInfo().getAlias());
                    }
                    if (result != null)
                    {
                        out.write("<table class='labkey-noborder'>");
                        String delim = "";
                        for(Aggregate.Result r : result)
                        {
                            out.write(delim);
                            Format formatter = renderer.getFormat();
                            if (aggregateLabels.size() > 1)
                            {
                                out.write("<tr><td>" + r.getAggregate().getDisplayString());
                                out.write(":&nbsp;</td>");
                            }
                            out.write("<td>");
                            if (formatter != null)
                                out.write(formatter.format(r.getValue()));
                            else
                                out.write(r.getValue().toString());
                            out.write("</td></tr>");

                            delim = "";
                        }

                        out.write("</table>");
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
    protected int renderTableContents(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers) throws SQLException, IOException
    {
        Results results = ctx.getResults();
        // unwrap for efficient use of ResultSetRowMapFactory
        ResultSet rs = results.getResultSet();
        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
        int rowIndex = 0;

        while (rs.next())
        {
            ctx.setRow(factory.getRowMap(rs));
            renderTableRow(ctx, out, showRecordSelectors, renderers, rowIndex++);
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
    protected void renderTableRow(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers, int rowIndex) throws SQLException, IOException
    {
        out.write("<tr");
        String rowClass = getRowClass(ctx, rowIndex);
        if (rowClass != null)
            out.write(" class=\"" + rowClass + "\"");
        out.write(">");

        if (showRecordSelectors)
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
        out.write("<input type=\"hidden\" name=\"" + CSRFUtil.csrfName + "\" value=\"" + CSRFUtil.getExpectedToken(ctx.getViewContext()) + "\">");
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
            rs = Table.selectForDisplay(tinfoMain, selectKeyMap.values(), getQueryParameters(), ctx.getBaseFilter(), ctx.getBaseSort(), getMaxRows(), getOffset());
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
            //For updates, the valueMap is the OLD version of the data.
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
        json.initialize(ctx, this, t, _displayColumns);

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
                group.writeCopyableExtJavaScript(ctx, out);
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
            List<ColumnInfo> pkCols = getTable().getPkColumns();
            for (ColumnInfo pkCol : pkCols)
            {
                String pkColName = pkCol.getName();
                if (!renderedColumns.contains(pkColName)) {
                    Object pkVal = null;
                    //UNDONE: Should we require a viewForm whenever someone
                    //posts? I tend to think so.
                    if (null != viewForm)
                        pkVal = viewForm.getPkVal();        //TODO: Support multiple PKs?

                    if (pkVal == null && valueMap != null)
                        pkVal = valueMap.get(pkColName);

                    if (null != pkVal) {
                        out.write("<input type='hidden' name='");
                        if (viewForm != null)
                            out.write(viewForm.getFormFieldName(pkCol));
                        else
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
        User user = viewContext.getUser();
        HasPermission p = viewForm.getTable();
        // TODO : tables need to accurately represent their own permissions
        // TODO : or maybe we need DataRegion.setPermissionToCheck(HasPermissions)
        if (null == p || p instanceof SchemaTableInfo)
            p = viewContext;
        if ((action == MODE_INSERT && !p.hasPermission(user, InsertPermission.class)) || (action == MODE_UPDATE && !p.hasPermission(user, UpdatePermission.class)))
        {
            out.write("You do not have permission to " +
                    (action == MODE_INSERT ? "Insert" : "Update") +
                    " data in this container.");
            return;
        }

        // Check if we have any value to update
        if (action == MODE_UPDATE && valueMap == null)
        {
            out.write("Could not find row in current project or folder.");
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
                group.writeCopyableJavaScript(ctx, out);
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
            List<ColumnInfo> pkCols = getTable().getPkColumns();
            for (ColumnInfo pkCol : pkCols)
            {
                String pkColName = pkCol.getName();
                if (!renderedColumns.contains(pkColName)) {
                    Object pkVal = null;
                    //UNDONE: Should we require a viewForm whenever someone
                    //posts? I tend to think so.
                    if (null != viewForm)
                        pkVal = viewForm.get(pkColName);

                    if (pkVal == null && valueMap != null)
                        pkVal = valueMap.get(pkColName);

                    if (null != pkVal) {
                        out.write("<input type='hidden' name='");
                        if (viewForm != null)
                            out.write(viewForm.getFormFieldName(pkCol));
                        else
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
    }

    
    private void writeSameHeader(RenderContext ctx, Writer out)
            throws IOException
    {
        out.write("<td class='labkey-form-label'>");
        out.write("<input type='checkbox' name='~~SELECTALL~~' onchange=\"");
        for (DisplayColumnGroup group : _groups)
        {
            group.writeCopyableOnChangeHandler(ctx, out);
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
        int mode = MODE_GRID;
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
            throw new RuntimeSQLException(x);
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

    public void setAggregateRowConfig(AggregateRowConfig config)
    {
        _aggregateRowConfig = config;
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
