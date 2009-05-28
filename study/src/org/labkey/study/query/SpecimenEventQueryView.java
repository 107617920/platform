/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.view.ViewContext;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.DataRegion;
import org.labkey.api.study.Study;
import org.labkey.study.model.Specimen;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudyImpl;

/**
 * User: brittp
 * Date: Jan 26, 2007
 * Time: 10:13:50 AM
 */
public class SpecimenEventQueryView extends BaseStudyQueryView
{
    protected SpecimenEventQueryView(ViewContext context, UserSchema schema,
                                     QuerySettings settings, SimpleFilter filter, Sort sort)
    {
        super(context, schema, settings, filter, sort);
    }

    public static SpecimenEventQueryView createView(ViewContext context, Specimen specimen)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
        StudyQuerySchema schema = new StudyQuerySchema(study, context.getUser(), true);
        String queryName = "SpecimenEvent";
        QuerySettings qs = new QuerySettings(context, queryName);
        qs.setSchemaName(schema.getSchemaName());
        qs.setQueryName(queryName);
        SimpleFilter filter = new SimpleFilter("SpecimenId", specimen.getRowId());
        Sort sort = new Sort("-ShipDate");
        return new SpecimenEventQueryView(context, schema, qs, filter, sort);
    }


    @Override
    protected DataRegion createDataRegion()
    {
        DataRegion rgn = super.createDataRegion();
        rgn.setShadeAlternatingRows(true);
        rgn.setShowBorders(true);
        return rgn;
    }
}
