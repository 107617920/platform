/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.security.User;
import org.labkey.api.data.SimpleFilter;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.SQLException;
import java.util.*;

public class ExpMaterialImpl extends AbstractProtocolOutputImpl<Material> implements ExpMaterial
{
    static public ExpMaterialImpl[] fromMaterials(Material[] materials)
    {
        ExpMaterialImpl[] ret = new ExpMaterialImpl[materials.length];
        for (int i = 0; i < materials.length; i ++)
        {
            ret[i] = new ExpMaterialImpl(materials[i]);
        }
        return ret;
    }

    public ExpMaterialImpl(Material material)
    {
        super(material);
    }

    public URLHelper detailsURL()
    {
        ActionURL ret = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
    }

    public ExpSampleSet getSampleSet()
    {
        String type = _object.getCpasType();
        if (!"Material".equals(type) && !"Sample".equals(type))
        {
            return ExperimentService.get().getSampleSet(type);
        }
        else
        {
            return null;
        }
    }

    public void insert(User user) throws SQLException
    {
        ExperimentServiceImpl.get().insertMaterial(user, _object);
    }

    public Map<PropertyDescriptor, Object> getPropertyValues()
    {
        ExpSampleSet sampleSet = getSampleSet();
        if (sampleSet == null)
        {
            return Collections.emptyMap();
        }
        PropertyDescriptor[] pds = sampleSet.getPropertiesForType();
        Map<PropertyDescriptor, Object> values = new HashMap<PropertyDescriptor, Object>();
        for (PropertyDescriptor pd : pds)
        {
            values.put(pd, getProperty(pd));
        }
        return values;
    }

    public ExpProtocolApplication[] getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter("MaterialId", getRowId()), ExperimentServiceImpl.get().getTinfoMaterialInput());
    }

    public String getCpasType()
    {
        String result = _object.getCpasType();
        return result == null ? "Material" : result;
    }

    public void save(User user)
    {
        save(user, ExperimentServiceImpl.get().getTinfoMaterial());
    }

    public void delete(User user)
    {
        ExperimentService.get().deleteMaterialByRowIds(getContainer(), getRowId());
    }

    public ExpRun[] getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoMaterialInput(), "MaterialId");
    }
}
