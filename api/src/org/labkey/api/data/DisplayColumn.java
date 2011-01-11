/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.collections.NullPreventingSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.PopupMenu;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public abstract class DisplayColumn extends RenderColumn
{
    protected String _textAlign = null;
    protected boolean _nowrap = false;
    protected String _width = "60";
    protected String _linkTarget = null;
    protected String _excelFormatString = null;
    protected Format _format = null;
    protected Format _tsvFormat = null;
    protected String _gridHeaderClass = "labkey-col-header-filter";
    private String _inputPrefix = "";
    private String _description = null;
    protected boolean _htmlFiltered = true;
    private String _displayClass;

    // for URL generation
    private String _url;
    private StringExpression _urlExpression;
    private StringExpression _compiled;

    public abstract void renderGridCellContents(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderTitle(RenderContext ctx, Writer out) throws IOException;

    public abstract boolean isSortable();

    public abstract boolean isFilterable();

    public abstract boolean isEditable();

    public abstract void renderSortHandler(RenderContext ctx, Writer out, Sort.SortDirection sort) throws IOException;

    public abstract void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException;

    // Do nothing by default
    public void renderGridEnd(RenderContext ctx, Writer out) throws IOException
    {
    }

    public String renderURL(RenderContext ctx)
    {
        StringExpression s = compileExpression(ctx.getViewContext());
        return null == s ? null : s.eval(ctx);
    }

    public String getURL()
    {
        return _url != null ? _url : _urlExpression != null ? _urlExpression.getSource() : null;
    }

    public void setURL(String url)
    {
        _url = url;
        _urlExpression = null;
    }

    public void setURLExpression(StringExpression se)
    {
        _urlExpression = se;
        _url = null;
    }


    public StringExpression getURLExpression()
    {
        return _urlExpression;
    }

    public StringExpression compileExpression(ViewContext context)
    {
        if (null == _compiled)
        {
            if (null != _urlExpression)
                _compiled = _urlExpression.copy();
            else if (null != _url)
                _compiled = StringExpressionFactory.createURL(_url);
            if (_compiled instanceof HasViewContext)
                ((HasViewContext)_compiled).setViewContext(context);
        }
        return _compiled;
    }

    public abstract boolean isQueryColumn();


    /** return a set of FieldKeys that this DisplayColumn depends on */
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        ColumnInfo col = getColumnInfo();
        if (col != null)
            keys.add(col.getFieldKey());

        StringExpression se = null;
        if (null != _urlExpression)
            se = _urlExpression;
        else if (null != _url)
            se = StringExpressionFactory.createURL(_url);

        if (se instanceof StringExpressionFactory.FieldKeyStringExpression)
        {
            Set<FieldKey> fields = ((StringExpressionFactory.FieldKeyStringExpression)se).getFieldKeys();
            keys.addAll(fields);
        }
    }


    /** implement addQueryFieldKeys() instead */
    @Deprecated
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
    }

    public abstract ColumnInfo getColumnInfo();

    public abstract Object getValue(RenderContext ctx);

    public abstract Class getValueClass();

    public Object getJsonValue(RenderContext ctx)
    {
        return getValue(ctx);
    }

    public String getName()
    {
        if (null != getColumnInfo())
            return getColumnInfo().getPropertyName();
        else
            return super.getName();
    }

    protected String getInputPrefix()
    {
        return _inputPrefix;
    }

    protected void setInputPrefix(String inputPrefix)
    {
        _inputPrefix = inputPrefix;
    }

    /** If width is null, no width will be requested in the HTML table */
    public void setWidth(String width)
    {
        _width = width;
    }

    public String getWidth()
    {
        return _width;
    }

    public void setNoWrap(boolean nowrap)
    {
        _nowrap = nowrap;
    }

    public void setFormatString(String formatString)
    {
        super.setFormatString(formatString);
        _format = createFormat(formatString);
    }

    public void setTsvFormatString(String formatString)
    {
        super.setTsvFormatString(formatString);
        _tsvFormat = createFormat(formatString);
    }

    private Format createFormat(String formatString)
    {
        if (null != formatString)
        {
            Class valueClass = getDisplayValueClass();

            if (Boolean.class.isAssignableFrom(valueClass) || boolean.class.isAssignableFrom(valueClass))
                return new BooleanFormat(formatString);
            if (valueClass.isPrimitive() || Number.class.isAssignableFrom(valueClass))
                return new DecimalFormat(formatString);
            else if (Date.class.isAssignableFrom(valueClass))
                return FastDateFormat.getInstance(formatString);
        }

        return null;
    }

    public Format getFormat()
    {
        return _format;
    }

    public Format getTsvFormat()
    {
        return _tsvFormat;
    }

    public String getFormattedValue(RenderContext ctx)
    {
        Format format = getFormat();
        return formatValue(ctx, format);
    }

    private String formatValue(RenderContext ctx, Format format)
    {
        Object value = getDisplayValue(ctx);

        if (null == value)
            return "";

        if (null != format)
            return format.format(value);
        else if (value instanceof String)
            return (String)value;
        return ConvertUtils.convert(value);
    }

    public String getTsvFormattedValue(RenderContext ctx)
    {
        Format format = getTsvFormat();
        if (format == null)
        {
            format = getFormat();
        }
        return formatValue(ctx, format);
    }

    /**
     * Returns the JSON type name for the column's display value,
     * which might be different than its value (e.g., lookup column)
     * @return JSON type name
     */
    public String getDisplayJsonTypeName()
    {
        return getJsonTypeName(getDisplayValueClass());
    }

    /**
     * Returns the JSON type name for the column's value
     * @return JSON type name
     */
    public String getJsonTypeName()
    {
        return getJsonTypeName(getValueClass());
    }

    public static String getJsonTypeName(Class valueClass)
    {
        if(String.class.isAssignableFrom(valueClass))
            return "string";
        else if(Boolean.class.isAssignableFrom(valueClass) || boolean.class.isAssignableFrom(valueClass))
            return "boolean";
        else if(Integer.class.isAssignableFrom(valueClass) || int.class.isAssignableFrom(valueClass))
            return "int";
        else if(Double.class.isAssignableFrom(valueClass) || double.class.isAssignableFrom(valueClass)
                || Float.class.isAssignableFrom(valueClass) || float.class.isAssignableFrom(valueClass))
            return "float";
        else if(Date.class.isAssignableFrom(valueClass))
            return "date";
        else
            return "string";

    }

    public Object getDisplayValue(RenderContext ctx)
    {
        return getValue(ctx);
    }

    public Class getDisplayValueClass()
    {
        return getValueClass();
    }

    public void setTextAlign(String textAlign)
    {
        _textAlign = textAlign;
    }

    public String getTextAlign()
    {
        return _textAlign;
    }

    public void setGridHeaderClass(String headerClass)
    {
        _gridHeaderClass = headerClass;
    }

    public void addGridHeaderClass(String headerClass)
    {
        if (_gridHeaderClass == null)
        {
            _gridHeaderClass = headerClass;
        }
        else if (!_gridHeaderClass.contains(headerClass))
        {
            _gridHeaderClass = _gridHeaderClass + " " + headerClass;
        }
    }

    public String getGridHeaderClass()
    {
        return _gridHeaderClass;
    }

    public void renderColTag(Writer out, boolean isLast) throws IOException
    {
        out.write("<col ");
        if (_width != null)
        {
            out.write("width=\"");
            out.write(isLast ? "*" : _width);
            out.write("\"");
        }
        out.write(" style=\"text-align:");
        out.write(_textAlign == null ? "left" : _textAlign);
        out.write("\"/>");
    }

    public String getGridHeaderCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderGridHeaderCell(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public String getDefaultHeaderStyle()
    {
        return (_nowrap ? "white-space:nowrap;" : "") + "text-align:" + (getTextAlign() != null ? getTextAlign() : "left");
    }

    public void renderGridHeaderCell(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        renderGridHeaderCell(ctx, out, null);
    }

    private Sort.SortField getSortColumn(Sort sort)
    {
        if (sort != null)
        {
            Set<ColumnInfo> ret = new NullPreventingSet<ColumnInfo>(new HashSet<ColumnInfo>());
            addQueryColumns(ret);
            for (ColumnInfo info : ret)
            {
                Sort.SortField sortField = sort.getSortColumn(info.getName());
                if (sortField != null)
                    return sortField;
            }
        }
        return null;
    }

    public void renderGridHeaderCell(RenderContext ctx, Writer out, String headerClass) throws IOException, SQLException
    {
        Sort sort = getSort(ctx);
        Sort.SortField sortField = getSortColumn(sort);
        boolean filtered = isFiltered(ctx);
        String baseId = ctx.getCurrentRegion().getName() + ":" + (getColumnInfo() != null ? getColumnInfo().getName() : super.getName());

        out.write("\n<td class='labkey-column-header ");
        out.write(getGridHeaderClass());
        if (sortField != null)
        {
            if (sortField.getSortDirection() == Sort.SortDirection.ASC)
                out.write(" labkey-sort-asc");
            else
                out.write(" labkey-sort-desc");
        }
        if (filtered)
            out.write(" labkey-filtered");
        if (headerClass != null)
        {
            out.write(" " + headerClass);
        }
        if (_displayClass != null)
        {
            out.write(" " + _displayClass);
        }
        out.write("'");

        out.write(" style='");
        out.write(getDefaultHeaderStyle());
        out.write("'");

        if (null != getDescription())
        {
            out.write(" title=\"");
            out.write(PageFlowUtil.filter(getDescription()));
            out.write("\"");
        }

        out.write(" id='");
        out.write(PageFlowUtil.filter(baseId + ":header"));
        out.write("'");

        NavTree navtree = getPopupNavTree(ctx, baseId, sort, filtered);
        if (navtree != null)
        {
            out.write(" onclick=\"showMenu(this, ");
            out.write(PageFlowUtil.jsString(navtree.getId()));
            out.write(", null);\"");
        }
        out.write(">\n");
        out.write("<div>");

        renderTitle(ctx, out);

        out.write("<img src=\"" + ctx.getRequest().getContextPath() + "/_.gif\" class=\"labkey-grid-filter-icon\"/>");
        out.write("<img src=\"" + ctx.getRequest().getContextPath() + "/_.gif\" class=\"x-grid3-sort-icon\"/>");

        out.write("</div>");

        if (navtree != null)
        {
            PopupMenu popup = new PopupMenu(navtree, PopupMenu.Align.LEFT, PopupMenu.ButtonStyle.TEXTBUTTON);
            popup.renderMenuScript(out, null);
        }

        out.write("</td>");
    }

    private Sort getSort(RenderContext ctx)
    {
        DataRegion rgn = ctx.getCurrentRegion();
        assert null != rgn;

        if (isSortable() && rgn.isSortable())
        {
            Sort sort = (Sort)ctx.get(rgn.getName() + ".sort");
            if (null == sort)
            {
                sort = ctx.getBaseSort();
                if (sort == null)
                    sort = new Sort();
                ActionURL url = ctx.getViewContext().getActionURL();
                sort.addURLSort(url, rgn.getName());
                ctx.put(rgn.getName() + ".sort", sort);
            }

            return sort;
        }
        return null;
    }

    private boolean isFiltered(RenderContext ctx)
    {
        DataRegion rgn = ctx.getCurrentRegion();
        assert null != rgn;

        if (isFilterable() && rgn.getShowFilters())
        {
            Set<String> filteredColSet = (Set<String>) ctx.get(rgn.getName() + ".filteredCols");

            if (null == filteredColSet)
            {
                TableInfo tinfo = rgn.getTable();
                assert null != tinfo;
                ActionURL url = ctx.getSortFilterURLHelper();
                SimpleFilter filter = new SimpleFilter(url, rgn.getName());

                filteredColSet = new HashSet<String>();
                for (String s : filter.getWhereParamNames())
                {
                    filteredColSet.add(s.toLowerCase());
                }
                ctx.put(rgn.getName() + ".filteredCols", filteredColSet);
            }

            return (null != this.getColumnInfo() &&
                    (filteredColSet.contains(this.getColumnInfo().getName().toLowerCase())) ||
                        (this.getColumnInfo().getDisplayField() != null &&
                        filteredColSet.contains(this.getColumnInfo().getDisplayField().getName().toLowerCase())));
        }
        return false;
    }

    private NavTree getPopupNavTree(RenderContext ctx, String baseId, Sort sort, boolean filtered) throws IOException
    {
        DataRegion rgn = ctx.getCurrentRegion();
        NavTree navtree = null;
        boolean addSortItems = isSortable() &&  rgn.isSortable();
        boolean addFilterItems = isFilterable() && rgn.getShowFilters();
        if (addSortItems || addFilterItems)
        {
            navtree = new NavTree();
            navtree.setId(baseId + ":menu"); //PageFlowUtil.filter(baseId + ":menu"));

            if (addSortItems)
            {
                Sort.SortField sortField = null;
                boolean primarySort = false;
                boolean isRemoveableSort = false;
                if (sort != null)
                {
                    sortField = getSortColumn(sort);
                    primarySort = sort.indexOf(getColumnInfo().getName()) == 0;
                    isRemoveableSort = isUserSort(ctx);
                }

                boolean selected = sortField != null && sortField.getSortDirection() == Sort.SortDirection.ASC;
                NavTree asc = new NavTree("Sort Ascending");
                asc.setId(baseId + ":asc"); //PageFlowUtil.filter(baseId + ":asc"));
                asc.setScript(getSortHandler(ctx, Sort.SortDirection.ASC));
                asc.setSelected(selected);
                asc.setDisabled(primarySort && selected);
                navtree.addChild(asc);

                selected = sortField != null && sortField.getSortDirection() == Sort.SortDirection.DESC;
                NavTree desc = new NavTree("Sort Descending");
                desc.setId(baseId + ":desc"); //PageFlowUtil.filter(baseId + ":desc"));
                desc.setScript(getSortHandler(ctx, Sort.SortDirection.DESC));
                desc.setSelected(selected);
                desc.setDisabled(primarySort && selected);
                navtree.addChild(desc);

                NavTree clearSort = new NavTree("Clear Sort");
                clearSort.setDisabled(null == sortField || !isRemoveableSort);
                if(null != sortField)
                    clearSort.setScript(getClearSortScript(ctx));
                navtree.addChild(clearSort);
            }

            if (addFilterItems)
            {
                if (navtree.hasChildren())
                    navtree.addSeparator();

                NavTree filterItem = new NavTree("Filter...");
                filterItem.setId(baseId + ":filter");//PageFlowUtil.filter(baseId + ":filter"));
                filterItem.setScript(getFilterOnClick(ctx));
                //filterItem.setImageSrc(ctx.getRequest().getContextPath() + "/_images/filter" + (filtered ? "_on" : "") + ".png");
                navtree.addChild(filterItem);
                
                NavTree clearFilterItem = new NavTree("Clear Filter");
                clearFilterItem.setId(baseId + ":clear-filter");//PageFlowUtil.filter(baseId + ":clear-filter"));
                clearFilterItem.setDisabled(!filtered);
                clearFilterItem.setScript(getClearFilter(ctx));
                navtree.addChild(clearFilterItem);
            }

        }
        return navtree;
    }

    public boolean isUserSort(RenderContext ctx)
    {
        Sort userSort = new Sort(ctx.getViewContext().getActionURL(), ctx.getCurrentRegion().getName());
        return null != getSortColumn(userSort);
    }

    public String getGridDataCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderGridDataCell(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public final void renderGridDataCell(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        out.write("<td");
        if (_displayClass != null)
        {
            out.write(" class=' " + _displayClass + "'");
        }
        if (_textAlign != null)
        {
            out.write(" align=");
            out.write(_textAlign);
        }
        String style = getCssStyle(ctx);
        if (!style.isEmpty())
        {
            out.write(" style='");
            out.write(style);
            out.write("'");
        }
        out.write(">");
        renderGridCellContents(ctx, out);
        out.write("</td>");
    }

    @NotNull /** Always return a non-null string to make it easy to concatenate values */
    protected String getCssStyle(RenderContext ctx)
    {
        if (_nowrap)
        {
            return "white-space:nowrap;";
        }
        return "";
    }

    public String getCaption()
    {
        return getCaption(null);
    }

    public String getCaption(RenderContext ctx)
    {
        return getCaption(ctx, true);
    }
    
    public String getCaption(RenderContext ctx, boolean htmlEncode)
    {
        if(!htmlEncode)
            return _caption.eval(ctx);
        else
        {
            StringWriter writer = new StringWriter();
            try
            {
                renderTitle(ctx, writer);
            }
            catch (Exception e)
            {
                writer.write(e.getMessage());
            }
            return writer.toString();
        }
    }


    public String getDetailsCaptionCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderDetailsCaptionCell(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }


    public void renderDetailsCaptionCell(RenderContext ctx, Writer out) throws IOException
    {
        if (null == _caption)
            return;

        out.write("<td class='labkey-form-label'>");
        renderTitle(ctx, out);
        out.write("</td>");
    }

    public String getDetailsData(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderDetailsData(ctx, writer, 1);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public void renderDetailsData(RenderContext ctx, Writer out, int span) throws IOException
    {
        if (null == _caption)
            out.write("<td colspan=" + (span + 1) + ">");
        else
            out.write("<td colspan=" + span + ">");
        renderDetailsCellContents(ctx, out);
        out.write("</td>");
    }

    public String getInputCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderInputCell(ctx, writer, 1);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    /** Get typed value or string value if form type conversion failed. */
    protected Object getInputValue(RenderContext ctx)
    {
        ColumnInfo col = getColumnInfo();
        Object val = null;
        TableViewForm viewForm = ctx.getForm();

        if (col != null)
        {
            if (null != viewForm && viewForm.contains(col))
            {
                String formFieldName = viewForm.getFormFieldName(col);
                if (viewForm.hasTypedValue(formFieldName))
                    val = viewForm.getTypedValue(formFieldName);
                else
                    val = viewForm.get(formFieldName);
            }
            else if (ctx.getRow() != null)
                val = col.getValue(ctx);
        }

        return val;
    }

    public String getFormFieldName(RenderContext ctx)
    {
        if (ctx.getForm() != null && getColumnInfo() != null)
            return ctx.getForm().getFormFieldName(getColumnInfo());
        return getName();
    }

    protected void outputName(RenderContext ctx, Writer out, String formFieldName) throws IOException
    {
        out.write(" name='");
        out.write(PageFlowUtil.filter(getInputPrefix() + formFieldName));
        out.write("'");

        String setFocusId = (String)ctx.get("setFocusId");
        if (null != setFocusId)
        {
            out.write(" id='" + setFocusId + "'");
            ctx.remove("setFocusId");
        }
    }

    public void renderHiddenFormInput(RenderContext ctx, Writer out) throws IOException
    {
        renderHiddenFormInput(ctx, out, getFormFieldName(ctx), getInputValue(ctx));
    }

    protected void renderHiddenFormInput(RenderContext ctx, Writer out, String formFieldName, Object value) throws IOException
    {
        out.write("<input type=hidden");
        outputName(ctx, out, formFieldName);
        out.write(" value=\"");
        if (null != value)
        {
            // it's important to use ConvertUtils here, since 'value' might be a string (if populated via
            // an initial values map), or it might be an array containing a single string (if populated via
            // request.getParameterMap() during an error reshow).  ConvertUtils normalizes these values.
            out.write(PageFlowUtil.filter(ConvertUtils.convert(value)));
        }
        out.write("\">");
    }

    public void renderInputCell(RenderContext ctx, Writer out, int span) throws IOException
    {
        out.write("<td colspan=" + span + ">");
        renderInputHtml(ctx, out, getInputValue(ctx));
        out.write("</td>");
    }

    public String getSortHandler(RenderContext ctx, Sort.SortDirection sort)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderSortHandler(ctx, writer, sort);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public String getFilterOnClick(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderFilterOnClick(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public String getClearFilter(RenderContext ctx)
    {
        return "";
    }

    public String getClearSortScript(RenderContext ctx)
    {
        return "";
    }

    public String getInputHtml(RenderContext ctx)
    {
        Object value = getInputValue(ctx);
        StringWriter writer = new StringWriter();
        try
        {
            renderInputHtml(ctx, writer, value);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public boolean isHtmlFiltered()
    {
        return _htmlFiltered;
    }

    public void setHtmlFiltered(boolean htmlFiltered)
    {
        _htmlFiltered = htmlFiltered;
    }

    public void setLinkTarget(String linkTarget)
    {
        _linkTarget = linkTarget;
    }

    public String getLinkTarget()
    {
        return _linkTarget;
    }

    public String getExcelFormatString()
    {
        return _excelFormatString;
    }

    public void setExcelFormatString(String excelFormatString)
    {
        _excelFormatString = excelFormatString;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String _description)
    {
        this._description = _description;
    }

    public void addDisplayClass(String className)
    {
        if (_displayClass == null)
        {
            _displayClass = className;
        }
        else if (!_displayClass.contains(className))
        {
            _displayClass = _displayClass + " " + className;
        }
    }
}
