/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.experiment.api;

import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SampleSetDomainType extends AbstractDomainKind
{
    public SampleSetDomainType()
    {
    }

    public String getKindName()
    {
        return "SampleSet";
    }
    
    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return "SampleSet".equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        return ExperimentServiceImpl.get().generateLSID(container, ExpSampleSet.class, queryName);
    }

    private ExpSampleSet getSampleSet(Domain domain)
    {
        return ExperimentService.get().getSampleSet(domain.getTypeURI());
    }

    public ActionURL urlShowData(Domain domain)
    {
        ExpSampleSet ss = getSampleSet(domain);
        if (ss == null)
        {
            return null;
        }
        return (ActionURL) ss.detailsURL();
    }

    public ActionURL urlEditDefinition(Domain domain)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(domain.getContainer(), domain.getTypeURI(), false, true, false);
    }

    public String getTypeLabel(Domain domain)
    {
        ExpSampleSet ss = getSampleSet(domain);
        if (null == ss)
            return "Sample Set '" + domain.getName() + "'";
        return ss.getName();
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        SQLFragment ret = new SQLFragment("SELECT exp.object.objectid FROM exp.object INNER JOIN exp.material ON exp.object.objecturi = exp.material.lsid WHERE exp.material.cpastype = ?");
        ret.add(domain.getTypeURI());
        return ret;
    }

    public Set<String> getReservedPropertyNames(Domain domain)
    {
        ExpMaterialTable.Column[] columns = ExpMaterialTable.Column.values();
        Set<String> reserved = new HashSet<String>(columns.length);
        for (ExpMaterialTable.Column column : columns)
            reserved.add(column.name());
        reserved.add("CpasType");
        return reserved;
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        // Cannot edit default sample set
        ExpSampleSet ss = getSampleSet(domain);
        if (ss == null || ExperimentService.get().ensureDefaultSampleSet().equals(ss))
        {
            return false;
        }
        return domain.getContainer().hasPermission(user, UpdatePermission.class);
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class);
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user)
    {
        String name = domain.getName();
        String description = domain.getDescription();
        List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();

        Object[] idCols = arguments.containsKey("idCols") ? (Object[])arguments.get("idCols") : new Object[0];
        int idCol1 = idCols.length > 0 ? ((Number)idCols[0]).intValue() : -1;
        int idCol2 = idCols.length > 1 ? ((Number)idCols[1]).intValue() : -1;
        int idCol3 = idCols.length > 2 ? ((Number)idCols[2]).intValue() : -1;
        int parentCol = arguments.get("parentCol") instanceof Number ? ((Number)arguments.get("parentCol")).intValue() : -1;

        ExpSampleSet ss;
        try
        {
            ss = ExperimentService.get().createSampleSet(container, user, name, description, properties, idCol1, idCol2, idCol3, parentCol);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (ExperimentException e)
        {
            throw new RuntimeException(e);
        }
        return ss.getType();
    }

}
