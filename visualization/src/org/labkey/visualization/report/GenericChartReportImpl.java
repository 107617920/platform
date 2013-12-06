/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.visualization.report;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ThumbnailUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.GenericChartReport;
import org.labkey.api.visualization.SvgThumbnailGenerator;
import org.labkey.visualization.VisualizationController;

import java.io.InputStream;

/**
 * User: klum
 * Date: May 31, 2012
 */
public class GenericChartReportImpl extends GenericChartReport implements SvgThumbnailGenerator
{
    private String _svg = null;

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        VisualizationController.GenericReportForm form = new VisualizationController.GenericReportForm();
        form.setAllowToggleMode(true);
        form.setReportId(getReportId());
        form.setComponentId("generic-report-panel-" + UniqueID.getRequestScopedUID(context.getRequest()));

        JspView view = new JspView<>(getDescriptor().getViewClass(), form);

        view.setTitle(getDescriptor().getReportName());
        view.setFrame(WebPartView.FrameType.PORTAL);

        if (canEdit(context.getUser(), context.getContainer()))
        {
            NavTree menu = new NavTree();
            menu.addChild("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(context.getContainer()));
            view.setNavMenu(menu);
        }

        return view;
    }

    @Override
    public void setSvg(String svg)
    {
        _svg = svg;
    }

    @Override
    public Thumbnail generateDynamicThumbnail(@Nullable ViewContext context)
    {
        // SVG is provided by the client code at save time and then stashed in the report by the save action. That's
        // the only way thumbnails can be generated from these reports.
        try
        {
            _svg = VisualizationController.filterSVGSource(_svg);
            return ThumbnailUtil.getThumbnailFromSvg(_svg);
        }
        catch (NotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDynamicThumbnailCacheKey()
    {
        return "Reports:" + getReportId();
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        RenderType type = getRenderType();
        String suffix = (null != type ? type.getId() : RenderType.AUTO_PLOT.getId());

        return "Reports:" + suffix;
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        RenderType type = getRenderType();
        String name = null != type ? type.getThumbnailName() : RenderType.AUTO_PLOT.getName();
        InputStream is = GenericChartReportImpl.class.getResourceAsStream(name);
        return new Thumbnail(is, "image/png");
    }
}
