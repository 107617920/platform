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
package org.labkey.api.reports.report.view;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.List;

/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 4:11:33 PM
 */
public class DefaultReportUIProvider implements ReportService.UIProvider
{
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context)
    {
        return Collections.emptyList();
    }

    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings)
    {
        return Collections.emptyList();
    }

    public String getReportIcon(ViewContext context, String reportType)
    {
        return null;
    }

    protected ActionURL addForwardParams(ActionURL url, ViewContext context, String[] params)
    {
        for (String name : params)
        {
            String value = context.getActionURL().getParameter(name);
            if (null != value)
                url.replaceParameter(name, value);
        }
        return url;
    }

    protected void addDesignerURL(ViewContext context, QuerySettings settings, List<ReportService.DesignerInfo> designers, String type, String[] params)
    {
        RReportBean bean = new RReportBean(settings);
        bean.setReportType(type);
        bean.setRedirectUrl(context.getActionURL().toString());

        ActionURL designerURL = ReportUtil.getRReportDesignerURL(context, bean);
        designerURL = addForwardParams(designerURL, context, params);

        Report report = ReportService.get().createReportInstance(type);
        if (report != null)
            designers.add(new DesignerInfoImpl(type, report.getTypeDescription(), designerURL));
    }

    public static class DesignerInfoImpl implements ReportService.DesignerInfo
    {
        private String _reportType;
        private String _label;
        private ActionURL _designerURL;
        private String _description;
        private boolean _disabled;
        private String _id;

        public DesignerInfoImpl(String reportType, String label, ActionURL designerURL)
        {
            this(reportType, label, null, designerURL);
        }

        public DesignerInfoImpl(String reportType, String label, String description, ActionURL designerURL)
        {
            if (reportType == null)
                throw new IllegalArgumentException("The reportType param is required");
            
            _reportType = reportType;
            _label = label;
            _description = description;
            _designerURL = designerURL;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public void setDesignerURL(ActionURL designerURL)
        {
            _designerURL = designerURL;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public void setDisabled(boolean disabled)
        {
            _disabled = disabled;
        }

        public void setId(String id)
        {
            _id = id;
        }

        public String getReportType()
        {
            return _reportType;
        }

        public String getLabel()
        {
            return _label;
        }

        public ActionURL getDesignerURL()
        {
            return _designerURL;
        }

        public String getDescription()
        {
            return _description;
        }

        public boolean isDisabled()
        {
            return _disabled;
        }

        public String getId()
        {
            return _id;
        }
    }
}