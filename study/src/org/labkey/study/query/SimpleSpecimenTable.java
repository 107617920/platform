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
package org.labkey.study.query;

import org.labkey.api.study.TimepointType;
import org.labkey.study.StudySchema;
import org.labkey.api.study.StudyService;

/**
 * User: jeckels
 * Date: May 8, 2009
 */
public class SimpleSpecimenTable extends AbstractSpecimenTable
{
    public SimpleSpecimenTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimen());

        getColumn(StudyService.get().getSubjectColumnName(getContainer())).setFk(null);

        addSpecimenVisitColumn(TimepointType.RELATIVE_DATE);

        addSpecimenTypeColumns();
    }
}
