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
package org.labkey.api.query;


import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.AbstractDataRegion;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.RenderContext;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 23, 2011
 * Time: 7:27:25 PM
 */
public class ReportDataRegion extends AbstractDataRegion
{
    private HttpView _reportView;
    private Report _report;
    private ButtonBar _buttonBar;

    public ReportDataRegion(QuerySettings settings, ViewContext context, Report report)
    {
        setSettings(settings);

        try {
            // subtle, but we always want to use the data region specified from the enclosing query
            report.getDescriptor().setProperty(ReportDescriptor.Prop.dataRegionName, settings.getDataRegionName());
            _report = report;
            _reportView = report.getRunReportView(context);
            _allowHeaderLock = false;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void render(RenderContext ctx, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        try {
            StringBuilder viewmsg = new StringBuilder();
            StringBuilder filter = new StringBuilder();

            Writer out = response.getWriter();
            Map<String, String> messages = new LinkedHashMap<String, String>();

            addViewMessage(viewmsg, ctx);
            addFilterMessage(filter, ctx, true);

            messages.put(MessagePart.view.name(), viewmsg.toString());
            if (filter.length() > 0)
                messages.put(MessagePart.filter.name(), filter.toString());

            // for now set the width to 100%, but we want to be smarter about calculating the viewport width less scroll
            out.write("\n<table width=\"100%\" class=\"labkey-data-region");
            // Issue 12961: Extra lines at bottom of R report designer
            //out.write(" labkey-show-borders\"");
            out.write(" id=\"");
            out.write(PageFlowUtil.filter("dataregion_" + getName()));
            out.write("\">\n");

            renderHeader(ctx, out, true, 0);

            out.write("<tr><td>");
            _reportView.render(ctx, request, response);
            out.write("</td></tr>");

            out.write("</table>");

            renderHeaderScript(ctx, out, messages, false);
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
        //_report.getRunReportView(getViewContext()).render(model, request, response);

        //super.render(ctx, request, response);
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
/*
        StringBuilder sb = new StringBuilder();

        if (_buttonBar != null)
            _button
        addFilterMessage(sb, ctx, true);
        renderHeaderScript(ctx, out, sb.toString());

        out.write("<table class=\"labkey-data-region-header\" id=\"" + PageFlowUtil.filter("dataregion_header_" + getName()) + "\">\n");
        renderMessageBox(ctx, out, 0);
        out.write("</table>");
*/
    }

    @Override
    protected boolean shouldRenderHeader(boolean renderButtons)
    {
        return true;
    }

    @Override
    protected void renderButtons(RenderContext ctx, Writer out) throws IOException
    {
        if (_buttonBar != null)
            _buttonBar.render(ctx, out);
    }

    @Override
    protected void renderPagination(RenderContext ctx, Writer out, PaginationLocation location) throws IOException
    {
    }

    public ButtonBar getButtonBar()
    {
        return _buttonBar;
    }

    public void setButtonBar(ButtonBar buttonBar)
    {
        _buttonBar = buttonBar;
    }

    protected void addViewMessage(StringBuilder headerMessage, RenderContext ctx) throws IOException
    {
        // the name of the report
        headerMessage.append("<span class='labkey-strong'>View:</span>&nbsp;");
        headerMessage.append("<span style='padding:5px 10px 5px 0;'>");
        headerMessage.append(PageFlowUtil.filter(_report.getDescriptor().getReportName()));
        headerMessage.append("</span>&nbsp;");

        // the name of the view this report is built over
        headerMessage.append("<span class='labkey-strong'>Source:</span>&nbsp;");
        headerMessage.append("<span style='padding:5px 45px 5px 5px;'>");
        headerMessage.append(PageFlowUtil.filter(StringUtils.defaultIfEmpty(_report.getDescriptor().getProperty(ReportDescriptor.Prop.viewName), "default")));
        headerMessage.append("</span>&nbsp;");
    }

    @Override
    public boolean getAllowHeaderLock()
    {
        return false;
    }
}
