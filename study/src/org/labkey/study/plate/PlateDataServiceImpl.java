/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.study.plate;

import gwt.client.org.labkey.plate.designer.client.PlateDataService;
import gwt.client.org.labkey.plate.designer.client.model.GWTPlate;
import gwt.client.org.labkey.plate.designer.client.model.GWTPosition;
import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import org.labkey.api.study.*;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.view.ViewContext;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jan 31, 2007
 * Time: 2:38:17 PM
 */
public class PlateDataServiceImpl extends BaseRemoteService implements PlateDataService
{
    public PlateDataServiceImpl(ViewContext context)
    {
        super(context);
    }

    public GWTPlate getTemplateDefinition(String templateName, String assayTypeName, String templateTypeName, int rowCount, int columnCount) throws Exception
    {
        try
        {
            PlateTemplate template;
            if (templateName == null)  // If new PlateTemplate, get default
            {
                PlateTypeHandler handler = PlateManager.get().getPlateTypeHandler(assayTypeName);
                if (handler == null)
                {
                    throw new Exception("Plate template type " + assayTypeName + " does not exist.");
                }
                template = handler.createPlate(templateTypeName, getContainer(), rowCount, columnCount);
            }
            else  // If its already created, get PlateTemplate from database
            {
                template = PlateService.get().getPlateTemplate(getContainer(), templateName);
                if (template == null)
                    throw new Exception("Plate " + templateName + " does not exist.");
            }

            // Translate PlateTemplate to GWTPlate
            List<? extends WellGroupTemplate> groups = template.getWellGroups();
            List<GWTWellGroup> translated = new ArrayList<GWTWellGroup>();
            for (WellGroupTemplate group : groups)
            {
                List<GWTPosition> positions = new ArrayList<GWTPosition>(group.getPositions().size());
                for (Position position : group.getPositions())
                    positions.add(new GWTPosition(position.getRow(), position.getColumn()));
                Map<String, Object> groupProperties = new HashMap<String, Object>();
                for (String propName : group.getPropertyNames())
                {
                    groupProperties.put(propName, group.getProperty(propName));
                }
                translated.add(new GWTWellGroup(group.getType().name(), group.getName(), positions, groupProperties));
            }
            GWTPlate plate = new GWTPlate(template.getName(), template.getType(), template.getRows(),
                    template.getColumns(), getTypeList(template));
            plate.setGroups(translated);
            Map<String, Object> templateProperties = new HashMap<String, Object>();
            for (String propName : template.getPropertyNames())
            {
                templateProperties.put(propName, template.getProperty(propName) == null ? null : template.getProperty(propName).toString());
            }
            plate.setPlateProperties(templateProperties);
            return plate;
        }
        catch (SQLException e)
        {
            throw new Exception(e);
        }
    }

    private List<String> getTypeList(PlateTemplate template)
    {
        List<String> types = new ArrayList<String>();
        WellGroup.Type[] wellTypes = new WellGroup.Type[]{
                WellGroup.Type.CONTROL, WellGroup.Type.SPECIMEN,
                WellGroup.Type.REPLICATE, WellGroup.Type.OTHER};

        PlateTypeHandler handler = PlateManager.get().getPlateTypeHandler(template.getType());
        if (handler != null)
            wellTypes = handler.getWellGroupTypes();

        for (WellGroup.Type type : wellTypes)
            types.add(type.name());
        return types;
    }

    public void saveChanges(GWTPlate gwtPlate, boolean replaceIfExisting) throws Exception
    {
        try
        {
            PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), gwtPlate.getName());
            if (template != null)
            {
                if (replaceIfExisting)
                    PlateService.get().deletePlate(getContainer(), template.getRowId());
                else
                    throw new Exception("A plate with name '" + gwtPlate.getName() + "' already exists.");
            }

            template = PlateService.get().createPlateTemplate(getContainer(), gwtPlate.getType(), gwtPlate.getRows(), gwtPlate.getCols());
            template.setName(gwtPlate.getName());
            for (Map.Entry<String, Object> entry : gwtPlate.getPlateProperties().entrySet())
                template.setProperty(entry.getKey(), entry.getValue());

            List<GWTWellGroup> groups = gwtPlate.getGroups();
            for (GWTWellGroup gwtGroup : groups)
            {
                List<Position> positions = new ArrayList<Position>();
                for (GWTPosition gwtPosition : gwtGroup.getPositions())
                    positions.add(template.getPosition(gwtPosition.getRow(), gwtPosition.getCol()));

                if (!positions.isEmpty())
                {
                    WellGroupTemplate group = template.addWellGroup(gwtGroup.getName(), WellGroup.Type.valueOf(gwtGroup.getType()), positions);

                    for (Map.Entry<String, Object> entry : gwtGroup.getProperties().entrySet())
                        group.setProperty(entry.getKey(), entry.getValue());
                }
            }
            PlateService.get().save(getContainer(), getUser(), template);
        }
        catch (SQLException e)
        {
            throw new Exception(e);
        }
    }
}
