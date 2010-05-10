/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.filecontent;

import org.labkey.api.data.Container;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;
import org.labkey.filecontent.designer.client.FilePropertiesService;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 25, 2010
 * Time: 12:50:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class FilePropertiesServiceImpl extends DomainEditorServiceBase implements FilePropertiesService
{
    public FilePropertiesServiceImpl(ViewContext context)
    {
        super(context);
    }

    @Override
    public GWTDomain getDomainDescriptor(String typeURI)
    {
        GWTDomain domain = super.getDomainDescriptor(typeURI);
        if (domain != null)
            domain.setDefaultValueOptions(new DefaultValueType[]
                    { DefaultValueType.FIXED_EDITABLE, DefaultValueType.FIXED_NON_EDITABLE }, DefaultValueType.FIXED_EDITABLE);
        return domain;
    }

    @Override
    protected GWTDomain getDomainDescriptor(String typeURI, Container domainContainer)
    {
        GWTDomain domain = super.getDomainDescriptor(typeURI, domainContainer);
        if (domain != null)
            domain.setDefaultValueOptions(new DefaultValueType[]
                    { DefaultValueType.FIXED_EDITABLE, DefaultValueType.FIXED_NON_EDITABLE }, DefaultValueType.FIXED_EDITABLE);
        return domain;
    }

    public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update)
    {
        try {
            if (orig.getDomainURI() != null)
            {
                DomainDescriptor dd = OntologyManager.ensureDomainDescriptor(orig.getDomainURI(), orig.getName(), getContainer());
                orig.setDomainId(dd.getDomainId());
                orig.setContainer(getContainer().getId());
                
                return super.updateDomainDescriptor(orig, update);
            }
            else
                throw new IllegalArgumentException("DomainURI cannot be null");
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }
}
