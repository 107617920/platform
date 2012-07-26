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

import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;

import java.io.File;
import java.io.FileFilter;
import java.io.PrintWriter;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: May 5, 2008
 */
public class ROutputView extends HttpView
{
    private String _label;
    private boolean _collapse;
    private boolean _showHeader = true;
    private File _file;
    private Map<String, String> _properties;

    public ROutputView(ParamReplacement param)
    {
        _file = param.getFile();
        _showHeader = param.getHeaderVisible();
        _properties = param.getProperties();
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public boolean isCollapse()
    {
        return _collapse;
    }

    public void setCollapse(boolean collapse)
    {
        _collapse = collapse;
    }

    public boolean isShowHeader()
    {
        return _showHeader;
    }

    public void setShowHeader(boolean showHeader)
    {
        _showHeader = showHeader;
    }

    public File getFile()
    {
        return _file;
    }

    public void setFile(File file)
    {
        _file = file;
    }

    public Map<String, String> getProperties()
    {
        return _properties;
    }

    public void setProperties(Map<String, String> properties)
    {
        _properties = properties;
    }

    protected void renderTitle(Object model, PrintWriter out) throws Exception
    {
        StringBuffer sb = new StringBuffer();

        if (_showHeader)
        {
            sb.append("<tr class=\"labkey-wp-header\"><th colspan=2 align=left>");
            sb.append("   <a href=\"#\" onclick=\"return toggleLink(this, false);\">");
            sb.append("   <img src=\"");
            sb.append(getViewContext().getContextPath());
            sb.append("/_images/");
            sb.append(_collapse ? "plus.gif" : "minus.gif");
            sb.append("\"></a>&nbsp;");
            sb.append(PageFlowUtil.filter(_label));
            sb.append("</th></tr>");
        }
        out.write(sb.toString());
    }

    protected File moveToTemp(File file, String prefix)
    {
        File root = ScriptEngineReport.getTempRoot(ReportService.get().createDescriptorInstance(RReportDescriptor.TYPE));

        File newFile = new File(root, FileUtil.makeFileNameWithTimestamp(FileUtil.getBaseName(file.getName()), FileUtil.getExtension(file)));
        newFile.delete();

        if (file.renameTo(newFile))
            return newFile;

        return null;
    }

    static final String PREFIX = "RReport";

    public static void cleanUpTemp(final long cutoff)
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        if (tempDir.exists())
        {
            File[] filesToDelete = tempDir.listFiles(new FileFilter(){
                @Override
                public boolean accept(File file)
                {
                    if (!file.isDirectory() && file.getName().startsWith(PREFIX))
                    {
                        return file.lastModified() < cutoff;
                    }
                    return false;
                }
            });
            
            for (File file : filesToDelete)
            {
                file.delete();
            }
        }
    }
}
