/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.data.ButtonBar;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.Extensible;
import org.labkey.study.model.Study;

/**
 * Query view for objects with extended properties using Ontology manager
 *
 * User: jgarms
 * Date: Jul 23, 2008
 * Time: 2:13:04 PM
 */
public class ExtensibleObjectQueryView extends QueryView
{
    private final boolean allowEditing;

    public ExtensibleObjectQueryView(
        User user,
        Study study,
        Class<? extends Extensible> extensibleClass,
        ViewContext context,
        boolean allowEditing)
    {
        super(new StudyQuerySchema(study, user, true));
        this.allowEditing = allowEditing;
        setShadeAlternatingRows(true);
        setShowBorders(true);
        QuerySettings settings = getSchema().getSettings(context, null);
        settings.setQueryName(extensibleClass.getSimpleName());
        settings.setAllowChooseQuery(false);
        setSettings(settings);
    }

    protected DataView createDataView()
    {
        DataView view = super.createDataView();
        return view;
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        bar.add(createExportMenuButton(false));
    }

    public boolean allowEditing()
    {
        return allowEditing;
    }
}
