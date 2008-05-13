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

package org.labkey.study.query;

import org.labkey.study.StudySchema;
import org.labkey.api.query.AliasedColumn;

public class AdditiveTypeTable extends StudyTable
{
    public AdditiveTypeTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenAdditive());
        addWrapColumn(_rootTable.getColumn("RowId"));
        addWrapColumn(_rootTable.getColumn("ScharpId"));
        addColumn(new AliasedColumn(this, "LdmsCode", _rootTable.getColumn("LdmsAdditiveCode")));
        addColumn(new AliasedColumn(this, "LabwareCode", _rootTable.getColumn("LabwareAdditiveCode")));
        addColumn(new AliasedColumn(this, "Description", _rootTable.getColumn("Additive")));
        setTitleColumn("Description");
    }
}
