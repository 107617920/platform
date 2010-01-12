/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.ViewContext;
import org.labkey.study.CohortFilter;

import java.util.List;

/**
 * User: brittp
 * Date: Jan 26, 2007
 * Time: 10:03:34 AM
 */
public abstract class BaseStudyQueryView extends QueryView
{
    protected SimpleFilter _filter;
    protected CohortFilter _cohortFilter;
    protected Sort _sort;
    private List<DisplayElement> _buttons;

    public BaseStudyQueryView(ViewContext context, UserSchema schema, QuerySettings settings,
                          SimpleFilter filter, Sort sort)
    {
        super(schema, settings);
        getSettings().setAllowChooseQuery(false);
        _filter = filter;
        _sort = sort;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
        if (filter != null)
            filter.addAllClauses(_filter);
        else
            filter = _filter;

        if (_cohortFilter != null)
            _cohortFilter.addFilterCondition(view.getTable(), getContainer(), filter);

        view.getRenderContext().setBaseFilter(filter);
        if (view.getRenderContext().getBaseSort() == null)
            view.getRenderContext().setBaseSort(_sort);
        if (_buttons != null)
        {
            ButtonBar bbar = view.getDataRegion().getButtonBar(DataRegion.MODE_GRID);
            for (DisplayElement button : _buttons)
            {
                bbar.add(button);
                if (button == ActionButton.BUTTON_SELECT_ALL || button == ActionButton.BUTTON_CLEAR_ALL)
                    view.getDataRegion().setShowRecordSelectors(true);
            }
            view.getDataRegion().setButtonBar(bbar);
        }
        //else
        //    view.getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        view.getDataRegion().setButtonBarPosition(getButtonBarPosition());
        return view;
    }

    protected DataRegion.ButtonBarPosition getButtonBarPosition()
    {
        return DataRegion.ButtonBarPosition.BOTH;
    }

    protected boolean showCustomizeLinks()
    {
        return false;
    }

    public void setButtons(List<DisplayElement> buttons)
    {
        _buttons = buttons;
    }

    public ActionURL getCustomizeURL()
    {
        return urlFor(QueryAction.chooseColumns);
    }

    public ActionURL getBaseViewURL()
    {
        return urlBaseView();
    }
}
