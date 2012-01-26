/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.ContainerUser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: migra
 * Date: Mar 6, 2006
 * Time: 7:55:56 PM
 */
public abstract class AbstractReport implements Report
{
    private ReportDescriptor _descriptor;

    public String getDescriptorType()
    {
        return ReportDescriptor.TYPE;
    }

    public ReportIdentifier getReportId()
    {
        return getDescriptor().getReportId();
    }

    public void setReportId(ReportIdentifier reportId)
    {
        getDescriptor().setReportId(reportId);
    }

    public void beforeSave(ContainerUser context){}
    public void beforeDelete(ContainerUser context){}

    public ReportDescriptor getDescriptor()
    {
        if (_descriptor == null)
        {
            _descriptor = ReportService.get().createDescriptorInstance(getDescriptorType());
            _descriptor.setReportType(getType());
        }
        return _descriptor;
    }

    public void setDescriptor(ReportDescriptor descriptor)
    {
        _descriptor = descriptor;
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        return ReportUtil.getRunReportURL(context, this);
    }

    public ActionURL getEditReportURL(ViewContext context)
    {
        return null;
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        return new HtmlView("No Data view available for this report");
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return renderReport(context);
    }

    public ActionURL getDownloadDataURL(ViewContext context)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlDownloadData(context.getContainer());

        for (Pair<String, String> param : context.getActionURL().getParameters())
        {
            url.replaceParameter(param.getKey(), param.getValue());
        }
        url.replaceParameter(ReportDescriptor.Prop.reportType.toString(), getDescriptor().getReportType());
        url.replaceParameter(ReportDescriptor.Prop.schemaName, getDescriptor().getProperty(ReportDescriptor.Prop.schemaName));
        url.replaceParameter(ReportDescriptor.Prop.queryName, getDescriptor().getProperty(ReportDescriptor.Prop.queryName));
        url.replaceParameter(ReportDescriptor.Prop.viewName, getDescriptor().getProperty(ReportDescriptor.Prop.viewName));
        url.replaceParameter(ReportDescriptor.Prop.dataRegionName, getDescriptor().getProperty(ReportDescriptor.Prop.dataRegionName));

        return url;
    }

    public void clearCache()
    {
    }

    public void serialize(VirtualFile dir, String filename) throws IOException
    {
        ReportDescriptor descriptor = getDescriptor();

        if (descriptor.getReportId() != null)
            descriptor.serialize(dir, filename);
        else
            throw new IllegalArgumentException("Cannot serialize a report that hasn't been saved yet");
    }

    public void serializeToFolder(VirtualFile dir) throws IOException
    {
        ReportDescriptor descriptor = getDescriptor();

        if (descriptor.getReportId() != null)
        {
            String filename = String.format("%s.%s.report.xml", descriptor.getReportName() != null ? descriptor.getReportName() : descriptor.getReportType(), descriptor.getReportId());
            serialize(dir, filename);
        }
        else
            throw new IllegalArgumentException("Cannot serialize a report that hasn't been saved yet");
    }

    public void afterDeserializeFromFile(File reportFile) throws IOException
    {
    }

    @Override
    public String getEntityId()
    {
        return getDescriptor().getEntityId();
    }

    @Override
    public String getContainerId()
    {
        return getDescriptor().getContainerId();
    }

    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return null;
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        InputStream is = AbstractReport.class.getResourceAsStream("report.jpg");
        return new Thumbnail(is, "image/jpeg");
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return "Reports:ReportStatic";
    }

}
