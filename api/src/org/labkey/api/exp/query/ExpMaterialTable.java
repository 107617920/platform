/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.exp.query;

import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;

import java.util.Set;

public interface ExpMaterialTable extends ExpTable<ExpMaterialTable.Column>
{
    void setMaterials(Set<ExpMaterial> predecessorMaterials);

    enum Column
    {
        RowId,
        Name,
        LSID,
        Flag,
        Run,
        CpasType,
        SourceProtocolLSID,
        Property,
        Folder,
    }
    void populate(ExpSampleSet ss, boolean filterSampleSet);
    void setSampleSet(ExpSampleSet ss, boolean filter);
}
