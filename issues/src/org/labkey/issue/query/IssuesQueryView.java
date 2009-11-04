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

package org.labkey.issue.query;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class IssuesQueryView extends QueryView
{
    private ViewContext _context;

    public IssuesQueryView(ViewContext context, UserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
        _context = context;
        setShowDetailsColumn(false);
        setShowRecordSelectors(true);
        getSettings().setAllowChooseQuery(false);
    }

    // MAB: I just want a resultset....
    public ResultSet getResultSet() throws SQLException, IOException
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        return rgn.getResultSet(view.getRenderContext());
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();

        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
            view.getDataRegion().setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        view.getDataRegion().setRecordSelectorValueColumns("IssueId");
        view.getDataRegion().setShadeAlternatingRows(true);
        view.getDataRegion().setShowBorders(true);

//        DisplayColumn issueid = view.getDataRegion().getDisplayColumn("IssueId");
//        if (null != issueid)
//            issueid.setURL(new ActionURL("issues", "details", getContainer()).toString() + "issueId=${IssueId}");

        //ensureDefaultCustomViews();
        return view;
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            String viewDetailsURL = _context.cloneActionURL().setAction("detailsList.view").getEncodedLocalURIString();
            ActionButton listDetailsButton = new ActionButton("button", "View Details");
            listDetailsButton.setURL(viewDetailsURL);
            listDetailsButton.setActionType(ActionButton.Action.POST);
            listDetailsButton.setRequiresSelection(true);
            listDetailsButton.setDisplayPermission(ACL.PERM_READ);
            bar.add(listDetailsButton);

            ActionButton adminButton = new ActionButton(_context.cloneActionURL().setAction("admin.view").getEncodedLocalURIString(), "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            adminButton.setDisplayPermission(ACL.PERM_ADMIN);
            bar.add(adminButton);

            ActionButton prefsButton = new ActionButton(_context.cloneActionURL().setAction("emailPrefs.view").getEncodedLocalURIString(), "Email Preferences", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            bar.add(prefsButton);
        }
    }

    protected void addGridViews(MenuButton menu, URLHelper target, String currentView)
    {
        URLHelper url = target.clone().deleteParameters();
        NavTree item = new NavTree("all", url);
        if (currentView == "")
            item.setHighlighted(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        url = target.clone().deleteParameters();
        url.addFilter("Issues", FieldKey.fromString("Status"), CompareType.EQUAL, "open");
        Sort sort = new Sort("AssignedTo/DisplayName");
        sort.insertSortColumn("Milestone", true);
        sort.applyURLSort(url, getDataRegionName());
        url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());        
        item = new NavTree("open", url);
        if (currentView == "")
            item.setHighlighted(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        url = target.clone().deleteParameters();
        url.addFilter("Issues", FieldKey.fromString("Status"), CompareType.EQUAL, "resolved");
        sort = new Sort("AssignedTo/DisplayName");
        sort.insertSortColumn("Milestone", true);
        sort.applyURLSort(url, getDataRegionName());
        url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
        item = new NavTree("resolved", url);
        if (currentView == "")
            item.setHighlighted(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        if (!getUser().isGuest())
        {
            url = target.clone().deleteParameters();
            url.addFilter("Issues", FieldKey.fromString("AssignedTo/DisplayName"), CompareType.EQUAL, getUser().getDisplayName(getViewContext()));
            url.addFilter("Issues", FieldKey.fromString("Status"), CompareType.NEQ_OR_NULL, "closed");
            sort = new Sort("-Milestone");
            sort.applyURLSort(url, getDataRegionName());
            url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
            item = new NavTree("mine", url);
            if (currentView == "")
                item.setHighlighted(target.toString().equals(url.toString()));
            menu.addMenuItem(item);
        }

        // sort the grid view alphabetically, with private views over public ones
        List<CustomView> views = new ArrayList<CustomView>(getQueryDef().getCustomViews(getViewContext().getUser(), getViewContext().getRequest()).values());
        Collections.sort(views, new Comparator<CustomView>() {
            public int compare(CustomView o1, CustomView o2)
            {
                if (o1.getOwner() != null && o2.getOwner() == null) return -1;
                if (o1.getOwner() == null && o2.getOwner() != null) return 1;
                if (o1.getName() == null) return -1;
                if (o2.getName() == null) return 1;

                return o1.getName().compareTo(o2.getName());
            }
        });

        boolean addSep = true;

        // issues doesn't preserve any URL sorts or filters because they may have been introduced by
        // the built in filter views.
        // TODO: replace these views with programatically filtered ones so we can leave URL filters on
        target.deleteParameters();

        for (CustomView view : views)
        {
            if (view.isHidden())
                continue;
            String label = view.getName();
            if (label == null)
                continue;

            if (addSep)
            {
                menu.addSeparator();
                addSep = false;
            }
            item = new NavTree(label, target.clone().replaceParameter(param(QueryParam.viewName), label).getLocalURIString());
            item.setId("Views:" + label);
            if (label.equals(currentView))
                item.setHighlighted(true);

            if (view.getOwner() == null)
                item.setImageSrc(getViewContext().getContextPath() + "/reports/grid_shared.gif");
            else
                item.setImageSrc(getViewContext().getContextPath() + "/reports/grid.gif");

            menu.addMenuItem(item);
        }
    }

    protected void populateReportButtonBar(ButtonBar bar)
    {
        super.populateReportButtonBar(bar);

        ActionButton adminButton = new ActionButton(_context.cloneActionURL().setAction("admin.view").getEncodedLocalURIString(), "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
        adminButton.setDisplayPermission(ACL.PERM_ADMIN);
        bar.add(adminButton);

        ActionButton prefsButton = new ActionButton(_context.cloneActionURL().setAction("emailPrefs.view").getEncodedLocalURIString(), "Email Preferences", DataRegion.MODE_GRID, ActionButton.Action.LINK);
        bar.add(prefsButton);
    }

    protected void setupDataView(DataView view)
    {
        // We need to set the base sort _before_ calling super.setupDataView.  If the user
        // has set a sort on their custom view, we want their sort to take precedence.
        view.getRenderContext().setBaseSort(new Sort("-IssueId"));
        super.setupDataView(view);
    }

    public ActionURL getCustomizeURL()
    {
        return urlFor(QueryAction.chooseColumns);
    }

    protected ActionURL urlFor(QueryAction action)
    {
        switch (action)
        {
            case exportRowsTsv:
                final ActionURL url =  _context.cloneActionURL().setAction("exportTsv.view");
                for (Pair<String, String> param : super.urlFor(action).getParameters())
                {
                    url.addParameter(param.getKey(), param.getValue());
                }
                return url;
        }
        return super.urlFor(action);
    }
}
