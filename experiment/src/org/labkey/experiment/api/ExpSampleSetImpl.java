/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ExpSampleSetImpl extends ExpIdentifiableEntityImpl<MaterialSource> implements ExpSampleSet
{
    private Domain _domain;

    public ExpSampleSetImpl(MaterialSource ms)
    {
        super(ms);
    }

    public URLHelper detailsURL()
    {
        ActionURL ret = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, getContainer());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
    }

    public Container getContainer()
    {
        return _object.getContainer();
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public String getMaterialLSIDPrefix()
    {
        return _object.getMaterialLSIDPrefix();
    }

    public DomainProperty[] getPropertiesForType()
    {
        Domain d = getType();
        if (d == null)
        {
            return new DomainProperty[0];
        }
        return d.getProperties();
    }

    public String getDescription()
    {
        return _object.getDescription();
    }

    public boolean canImportMoreSamples()
    {
        return hasNameAsIdCol() || getIdCol1() != null;        
    }

    private DomainProperty getDomainProperty(String uri)
    {
        if (uri == null)
        {
            return null;
        }

        for (DomainProperty property : getPropertiesForType())
        {
            if (uri.equals(property.getPropertyURI()))
            {
                return property;
            }
        }
        return null;
    }

    public List<DomainProperty> getIdCols()
    {
        List<DomainProperty> result = new ArrayList<>();
        if (hasNameAsIdCol())
            return result;

        result.add(getIdCol1());
        DomainProperty idCol2 = getIdCol2();
        if (idCol2 != null)
        {
            result.add(idCol2);
        }
        DomainProperty idCol3 = getIdCol3();
        if (idCol3 != null)
        {
            result.add(idCol3);
        }
        return result;
    }

    public boolean hasIdColumns()
    {
        return _object.getIdCol1() != null;
    }

    public boolean hasNameAsIdCol()
    {
        return ExpMaterialTable.Column.Name.name().equals(_object.getIdCol1());
    }

    @Nullable
    public DomainProperty getIdCol1()
    {
        if (hasNameAsIdCol())
            return null;

        DomainProperty result = getDomainProperty(_object.getIdCol1());
        if (result == null)
        {
            DomainProperty[] props = getPropertiesForType();
            if (props.length > 0)
            {
                result = props[0];
            }
        }
        return result;
    }

    public DomainProperty getIdCol2()
    {
        return getDomainProperty(_object.getIdCol2());
    }

    public DomainProperty getIdCol3()
    {
        return getDomainProperty(_object.getIdCol3());
    }

    public DomainProperty getParentCol()
    {
        return getDomainProperty(_object.getParentCol());
    }

    public void setDescription(String s)
    {
        ensureUnlocked();
        _object.setDescription(s);
    }

    public void setMaterialLSIDPrefix(String s)
    {
        ensureUnlocked();
        _object.setMaterialLSIDPrefix(s);
    }

    public ExpMaterialImpl[] getSamples()
    {
        try
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(getContainer());
            filter.addCondition("CpasType", getLSID());
            Sort sort = new Sort("Name");
            return ExpMaterialImpl.fromMaterials(Table.select(ExperimentServiceImpl.get().getTinfoMaterial(), Table.ALL_COLUMNS, filter, sort, Material.class));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpMaterialImpl getSample(String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(getContainer());
        filter.addCondition("CpasType", getLSID());
        filter.addCondition("Name", name);

        Material material = new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), filter, null).getObject(Material.class);
        if (material == null)
            return null;
        return new ExpMaterialImpl(material);
    }

    public Domain getType()
    {
        if (_domain == null)
        {
            _domain = PropertyService.get().getDomain(getContainer(), getLSID());
            if (_domain == null)
            {
                _domain = PropertyService.get().createDomain(getContainer(), getLSID(), getName());
                try
                {
                    _domain.save(null);
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw new UnexpectedException(e);
                }
            }
        }
        return _domain;
    }

    public ExpProtocol[] getProtocols(User user)
    {
        TableInfo tinfoProtocol = ExperimentServiceImpl.get().getTinfoProtocol();
        ColumnInfo colLSID = tinfoProtocol.getColumn("LSID");
        ColumnInfo colSampleLSID = new PropertyColumn(ExperimentProperty.SampleSetLSID.getPropertyDescriptor(), colLSID, getContainer(), user, false);
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(colSampleLSID, getLSID());
        List<ColumnInfo> selectColumns = new ArrayList<>();
        selectColumns.addAll(tinfoProtocol.getColumns());
        selectColumns.add(colSampleLSID);
        Protocol[] protocols = new TableSelector(tinfoProtocol, selectColumns, filter, null).getArray(Protocol.class);
        ExpProtocol[] ret = new ExpProtocol[protocols.length];
        for (int i = 0; i < protocols.length; i ++)
        {
            ret[i] = new ExpProtocolImpl(protocols[i]);
        }
        return ret;
    }

    public void onSamplesChanged(User user, List<Material> materials) throws SQLException
    {
        ExpProtocol[] protocols = getProtocols(user);
        if (protocols.length == 0)
            return;
        ExpMaterial[] expMaterials = null;

        if (materials != null)
        {
            expMaterials = new ExpMaterial[materials.size()];
            for (int i = 0; i < expMaterials.length; i ++)
            {
                expMaterials[i] = new ExpMaterialImpl(materials.get(i));
            }
        }
        for (ExpProtocol protocol : protocols)
        {
            ProtocolImplementation impl = protocol.getImplementation();
            if (impl == null)
                continue;
            impl.onSamplesChanged(user, protocol, expMaterials);
        }
    }


    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    public void save(User user)
    {
        boolean isNew = _object.getRowId() == 0;
        save(user, ExperimentServiceImpl.get().getTinfoMaterialSource());
        if (isNew)
        {
            Domain domain = PropertyService.get().getDomain(getContainer(), getLSID());
            if (domain == null)
            {
                domain = PropertyService.get().createDomain(getContainer(), getLSID(), getName());
                try
                {
                    domain.save(user);
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw new UnexpectedException(e);
                }
            }

            ExpSampleSet activeSampleSet = ExperimentServiceImpl.get().lookupActiveSampleSet(getContainer());
            if (activeSampleSet == null)
            {
                ExperimentServiceImpl.get().setActiveSampleSet(getContainer(), this);
            }
        }

        ExperimentServiceImpl.get().getMaterialSourceCache().put(String.valueOf(getRowId()), _object);
    }

    public void delete(User user)
    {
        try
        {
            ExperimentServiceImpl.get().deleteSampleSet(getRowId(), getContainer(), user);
        }
        catch (ExperimentException e)
        {
            throw new UnexpectedException(e);
        }
    }

    public static ExpSampleSetImpl[] fromMaterialSources(MaterialSource[] sources)
    {
        ExpSampleSetImpl[] ret = new ExpSampleSetImpl[sources.length];
        for (int i = 0; i < sources.length; i ++)
        {
            ret[i] = new ExpSampleSetImpl(sources[i]);
        }
        return ret;
    }

    @Override
    public String toString()
    {
        return "SampleSet " + getName() + " in " + getContainer().getPath();
    }
}
