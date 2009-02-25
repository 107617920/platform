/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.api.reports.report.r.view;

import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.tools.ColumnDescriptor;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class TsvOutput extends AbstractParamReplacement
{
    public static final String ID = "tsvout:";

    public TsvOutput()
    {
        super(ID);
    }

    public File convertSubstitution(File directory) throws Exception
    {
        if (directory != null)
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.tsv", directory);
        else
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.tsv");

        return _file;
    }

    public HttpView render(ViewContext context)
    {
        return new TabReportView(this);
    }

    public static class TabReportView extends ROutputView
    {
        TabReportView(ParamReplacement param)
        {
            super(param);
            setLabel("TSV output");
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (getFile() != null && getFile().exists() && (getFile().length() > 0))
            {
                TabLoader tabLoader = new TabLoader(getFile(), 1) {
                    protected String getDefaultColumnName(int col)
                    {
                        // a blank column name is okay...
                        return "";
                    }
                };
                tabLoader.setParseQuotes(true);
                ColumnDescriptor[] cols = tabLoader.getColumns();
                List<Map<String, Object>> data = tabLoader.load();

                List<ColumnDescriptor> display = new ArrayList<ColumnDescriptor>();
                HashMap<String, ColumnDescriptor> hrefs = new HashMap<String, ColumnDescriptor>(tabLoader.getColumns().length * 2);
                HashMap<String, ColumnDescriptor> styles = new HashMap<String, ColumnDescriptor>(tabLoader.getColumns().length * 2);

                for (ColumnDescriptor col : cols)
                    hrefs.put(col.name, null);

                for (ColumnDescriptor col : cols)
                {
                    if (col.name.endsWith(".href"))
                    {
                        String name = col.name.substring(0,col.name.length()-".href".length());
                        if (hrefs.containsKey(name))
                        {
                            hrefs.put(name,col);
                            continue;
                        }
                    }
                    if (col.name.endsWith(".style"))
                    {
                        String name = col.name.substring(0,col.name.length()-".style".length());
                        if (hrefs.containsKey(name))
                        {
                            styles.put(name,col);
                            continue;
                        }
                    }
                    display.add(col);
                }

                int row = 0;
                out.write("<table class=\"labkey-data-region\">");
                renderTitle(model, out);
                if (isCollapse())
                    out.write("<tr style=\"display:none\"><td><table>");
                else
                    out.write("<tr><td><table class=\"labkey-r-tsvout\">");
                out.write("<tr>");
                for (ColumnDescriptor col : display)
                {
                    if (Number.class.isAssignableFrom(col.getClass()))
                        out.write("<td class='labkey-header' align='right'>");
                    else
                        out.write("<td class='labkey-header'>");
                    out.write(PageFlowUtil.filter(col.name, true, true));
                    out.write("</td>");
                    row++;
                }
                out.write("</tr>");

                for (Map m : data)
                {
                    if (row % 2 == 0)
                        out.write("<tr class=\"labkey-alternate-row\">");
                    else
                        out.write("<tr class=\"labkey-row\">");
                    for (ColumnDescriptor col : display)
                    {
                        Object colVal = m.get(col.name);
                        if ("NA".equals(colVal))
                            colVal = null;
                        ColumnDescriptor hrefCol = hrefs.get(col.name);
                        String href = hrefCol == null ? null : ConvertUtils.convert((m.get(hrefCol.name)));
                        ColumnDescriptor styleCol = styles.get(col.name);
                        String style = styleCol == null ? null : ConvertUtils.convert((m.get(styleCol.name)));

                        out.write("<td");
                        if (Number.class.isAssignableFrom(col.clazz))
                            out.write(" align='right'");
                        if (null != style)
                        {
                            out.write(" style=\"");
                            out.write(PageFlowUtil.filter(style));
                            out.write("\"");
                        }
                        out.write(">");
                        if (null != href)
                        {
                            out.write("<a href=\"");
                            out.write(PageFlowUtil.filter(href));
                            out.write("\">");
                        }
                        if (null == colVal)
                            out.write("&nbsp;");
                        else
                            out.write(PageFlowUtil.filter(ConvertUtils.convert(colVal), true, true));
                        if (null != href)
                            out.write("</a>");
                        out.write("</td>");
                    }
                    out.write("</tr>");
                    row++;
                }
                out.write("</table></td></tr>");
                out.write("</table>");
            }
        }
    }
}
