/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.query.xml.DependencyType;
import org.labkey.query.xml.JavaScriptReportDescriptorType;
import org.labkey.query.xml.ReportDescriptorType;
import org.labkey.query.xml.ReportType;

import java.util.LinkedHashSet;

/**
 * User: nick
 * Date: 3/14/14
 */
public class ModuleReportDependenciesResource extends ModuleReportResource
{
    private LinkedHashSet<ClientDependency> _dependencies;

    public ModuleReportDependenciesResource(ReportDescriptor reportDescriptor, Resource sourceFile)
    {
        super(reportDescriptor, sourceFile);
    }

    // Only JavaScript and Knitr R reports have dependency metadata
    private JavaScriptReportDescriptorType getJavaScriptReportDescriptorType(ReportType type)
    {
        if (type.isSetJavaScript())
            return type.getJavaScript();

        if (type.isSetR())
        {
            // make sure this is a Knitr report
            String value = _reportDescriptor.getProperty(ScriptReportDescriptor.Prop.knitrFormat);
            if (value != null &&
                (RReportDescriptor.KnitrFormat.None != RReportDescriptor.getKnitrFormatFromString(value)))
                return type.getR();
        }

        // Query Reports and non-Knitr R reports do not support metadata
        return null;
    }

    protected ReportDescriptorType loadMetaData(Container container, User user)
    {
        ReportDescriptorType d = super.loadMetaData(container, user);

        if (null != d)
        {
            try
            {
                if (d.getReportType() != null)
                {
                    JavaScriptReportDescriptorType js = getJavaScriptReportDescriptorType(d.getReportType());
                    if (js == null)
                    {
                        throw new XmlException("Metadata associated with a Report must have a ReportType of JavaScript or be a Knitr R report.");
                    }

                    _dependencies = new LinkedHashSet<>();
                    if (js.getDependencies() != null)
                    {
                        for (DependencyType depend : js.getDependencies().getDependencyArray())
                        {
                            String path = depend.getPath();
                            if (null != path)
                            {
                                if (ClientDependency.isExternalDependency(path))
                                    _dependencies.add(ClientDependency.fromURIPath(path));
                                else
                                    _dependencies.add(ClientDependency.fromFilePath(depend.getPath()));
                            }
                        }
                    }
                }
            }
            catch(XmlException e)
            {
                Logger.getLogger(ModuleReportDependenciesResource.class).warn("Unable to load report metadata from file "
                        + _metaDataFile.getPath(), e);
            }
        }

        return d;
    }

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        if (_dependencies == null)
            return new LinkedHashSet<>();

        return _dependencies;
    }
}
