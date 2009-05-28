/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.DataSet;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.sql.SQLException;

/**
 * User: adam
 * Date: May 21, 2009
 * Time: 9:14:02 AM
 */
// Consider: generalize to StudyEntityReorderer?
public class DatasetReorderer
{
    private final Study _study;
    private final User _user;
    private int _i;

    public DatasetReorderer(Study study, User user)
    {
        _study = study;
        _user = user;
    }

    // Reorder all the datasets in the study.  Datasets specified in orderedIds will appear first, in order of the
    // list; any unspecified datasets will appear at the end of the list, maintaining their current order.
    public void reorderDatasets(List<Integer> orderedIds) throws SQLException
    {
        DataSetDefinition[] defs = StudyManager.getInstance().getDataSetDefinitions(_study);
        Map<Integer, DataSetDefinition> map = new LinkedHashMap<Integer, DataSetDefinition>(defs.length);

        for (DataSetDefinition def : defs)
            map.put(def.getDataSetId(), def);

        resetCounter();

        // Order the datasets specified by orderedIds
        for (Integer id : orderedIds)
        {
            DataSetDefinition def = map.get(id);
            updateDef(def);
            map.remove(id);
        }

        // Stick any unspecified datasets at the end of the list
        for (DataSetDefinition def : map.values())
            updateDef(def);
    }

    private void resetCounter()
    {
        _i = 0;
    }

    private void updateDef(DataSetDefinition def) throws SQLException
    {
        if (null != def)
        {
            if (def.getDisplayOrder() != _i)
            {
                def = def.createMutable();
                def.setDisplayOrder(_i);
                StudyManager.getInstance().updateDataSetDefinition(_user, def);
            }

            _i++;
        }
    }
}
