/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.GUID;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class ImageOutput extends AbstractParamReplacement
{
    public static final String ID = "imgout:";

    public ImageOutput()
    {
        super(ID);
    }

    public File convertSubstitution(File directory) throws Exception
    {
        if (directory != null)
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.jpg", directory);
        else
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.jpg");

        return _file;
    }

    public HttpView render(ViewContext context)
    {
        Report report = getReport();
        boolean deleteFile = true;

        if (report != null)
        {
            if (BooleanUtils.toBoolean(report.getDescriptor().getProperty(ReportDescriptor.Prop.cached)) ||
                BooleanUtils.toBoolean(report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground)))
                deleteFile = false;
        }
        return new ImgReportView(this, deleteFile);
    }

    public static class ImgReportView extends ROutputView
    {
        private boolean _deleteFile;

        ImgReportView(ParamReplacement param, boolean deleteFile)
        {
            super(param);
            setLabel("Image output");
            _deleteFile = deleteFile;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (getFile() != null && getFile().exists())
            {
                if (getFile().length() > 0)
                {
                    File imgFile;
                    if (!_deleteFile)
                        imgFile = getFile();
                    else
                        imgFile = moveToTemp(getFile(), "RReportImg");

                    if (imgFile != null)
                    {
                        String key = "temp:" + GUID.makeGUID();
                        getViewContext().getRequest().getSession(true).setAttribute(key, imgFile);

                        out.write("<table class=\"labkey-output\">");
                        renderTitle(model, out);
                        if (isCollapse())
                            out.write("<tr style=\"display:none\"><td>");
                        else
                            out.write("<tr><td>");
                        out.write("<img id=\"resultImage\" src=\"");

                        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlStreamFile(getViewContext().getContainer());
                        url.addParameters(PageFlowUtil.map("sessionKey", key, "deleteFile", Boolean.toString(_deleteFile), "cacheFile", "true"));

                        out.write(url.getLocalURIString());
                        out.write("\">");
                        out.write("</td></tr>");
                        out.write("</table>");
                    }
                }
                else
                    getFile().delete();
            }
        }
    }

    @Override
    public Thumbnail renderThumbnail() throws IOException
    {
        return ImageUtil.renderThumbnail(ImageIO.read(getFile()));
    }
}
