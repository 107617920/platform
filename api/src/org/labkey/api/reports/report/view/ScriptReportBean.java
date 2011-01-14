/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* User: Karl Lum
* Date: Dec 29, 2008
* Time: 3:35:00 PM
*/
public class ScriptReportBean extends ReportDesignBean
{
    protected String _script;
    protected boolean _runInBackground;
    protected boolean _isDirty;
    protected String _scriptExtension;
    private boolean _isReadOnly;
    private ActionURL _renderURL;
    private boolean _inherited;

    public ScriptReportBean(){}
    public ScriptReportBean(QuerySettings settings)
    {
        super(settings);
    }

    public String getScript()
    {
        return _script;
    }

    public void setScript(String script)
    {
        _script = script;
    }

    public boolean isRunInBackground()
    {
        return _runInBackground;
    }

    public void setRunInBackground(boolean runInBackground)
    {
        _runInBackground = runInBackground;
    }

    public boolean getIsDirty()
    {
        return _isDirty;
    }

    public void setIsDirty(boolean dirty)
    {
        _isDirty = dirty;
    }

    public Report getReport() throws Exception
    {
        Report report = super.getReport();

        if (report != null)
        {
            ReportDescriptor descriptor = report.getDescriptor();

            if (getScript() != null)
                descriptor.setProperty(ScriptReportDescriptor.Prop.script, getScript());

            descriptor.setProperty(ScriptReportDescriptor.Prop.scriptExtension, _scriptExtension);

            if (!isShareReport())
                descriptor.setOwner(getUser().getUserId());
            else
                descriptor.setOwner(null);

            if (getRedirectUrl() != null)
                descriptor.setProperty("redirectUrl", getRedirectUrl());

            // TODO: Refactor... this looks wrong
            if (RReportDescriptor.class.isAssignableFrom(descriptor.getClass()))
            {
                descriptor.setProperty(RReportDescriptor.Prop.runInBackground, _runInBackground);
            }
        }

        return report;
    }

    public List<Pair<String, String>> getParameters()
    {
        List<Pair<String, String>> list = super.getParameters();

        if (!StringUtils.isEmpty(_script))
            list.add(new Pair<String, String>(ScriptReportDescriptor.Prop.script.toString(), _script));
        if (_runInBackground)
            list.add(new Pair<String, String>(RReportDescriptor.Prop.runInBackground.toString(), String.valueOf(_runInBackground)));
        if (_isDirty)
            list.add(new Pair<String, String>("isDirty", String.valueOf(_isDirty)));

        list.add(new Pair<String, String>(ScriptReportDescriptor.Prop.scriptExtension.toString(), _scriptExtension));

        return list;
    }

    void populateFromDescriptor(ReportDescriptor descriptor)
    {
        super.populateFromDescriptor(descriptor);

        setScriptExtension(descriptor.getProperty(ScriptReportDescriptor.Prop.scriptExtension));
        setScript(descriptor.getProperty(ScriptReportDescriptor.Prop.script));

        // TODO: Move setRunInBackground() here?
    }

    Map<String, Object> getCacheableMap()
    {
        // saves report editing state in session
        Map<String, Object> map = new HashMap<String, Object>();

        for (Pair<String, String> param : getParameters())
            map.put(param.getKey(), param.getValue());

        return map;
    }

    public String getScriptExtension()
    {
        return _scriptExtension;
    }

    public void setScriptExtension(String scriptExtension)
    {
        _scriptExtension = scriptExtension;
    }

    public boolean isReadOnly()
    {
        return _isReadOnly;
    }

    public void setReadOnly(boolean readOnly)
    {
        _isReadOnly = readOnly;
    }

    public ActionURL getRenderURL()
    {
        return _renderURL;
    }

    public void setRenderURL(ActionURL renderURL)
    {
        _renderURL = renderURL;
    }

    public boolean isInherited()
    {
        return _inherited;
    }

    public void setInherited(boolean inherited)
    {
        _inherited = inherited;
    }
}