/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.Sort;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.issue.IssuesController;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public class IssuesQueryView extends QueryView
{
    private ViewContext _context;

    public IssuesQueryView(ViewContext context, UserSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);
        _context = context;
        setShowDetailsColumn(false);
        getSettings().getBaseSort().insertSortColumn("-IssueId");
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

        return view;
    }

    @NotNull
    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = super.getClientDependencies();
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("issues/detail.js"));
        return resources;
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            ActionURL viewDetailsURL = _context.cloneActionURL().setAction(IssuesController.DetailsListAction.class);
            ActionButton listDetailsButton = new ActionButton(viewDetailsURL, "View Details");
            listDetailsButton.setActionType(ActionButton.Action.POST);
            listDetailsButton.setRequiresSelection(true);
            listDetailsButton.setDisplayPermission(ReadPermission.class);
            listDetailsButton.setNoFollow(true);
            bar.add(listDetailsButton);

            ActionButton moveButton = new ActionButton("Move");
            moveButton.setRequiresSelection(true);
            moveButton.setDisplayPermission(AdminPermission.class);
            moveButton.setScript("Issues.window.MoveIssue.create(LABKEY.DataRegions['" + view.getDataRegion().getName() + "'].getChecked());");
            bar.add(moveButton);

            ActionButton adminButton = new ActionButton(IssuesController.AdminAction.class, "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            adminButton.setDisplayPermission(AdminPermission.class);
            bar.add(adminButton);

            if (!getUser().isGuest())
            {
                ActionButton prefsButton = new ActionButton(IssuesController.EmailPrefsAction.class, "Email Preferences", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                bar.add(prefsButton);
            }
        }
    }

    protected void addGridViews(MenuButton menu, URLHelper target, String currentView)
    {
        URLHelper url = target.clone().deleteParameters();
        NavTree item = new NavTree("all", url);
        if ("".equals(currentView))
            item.setStrong(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        url = target.clone().deleteParameters();
        url.addFilter(getDataRegionName(), FieldKey.fromString("Status"), CompareType.EQUAL, "open");
        Sort sort = new Sort("AssignedTo/DisplayName");
        sort.insertSortColumn("Milestone", true);
        sort.addURLSort(url, getDataRegionName());
        url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
        item = new NavTree("open", url);
        if ("".equals(currentView))
            item.setStrong(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        url = target.clone().deleteParameters();
        url.addFilter(getDataRegionName(), FieldKey.fromString("Status"), CompareType.EQUAL, "resolved");
        sort = new Sort("AssignedTo/DisplayName");
        sort.insertSortColumn("Milestone", true);
        sort.addURLSort(url, getDataRegionName());
        url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
        item = new NavTree("resolved", url);
        if ("".equals(currentView))
            item.setStrong(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        if (!getUser().isGuest())
        {
            url = target.clone().deleteParameters();
            url.addFilter(getDataRegionName(), FieldKey.fromString("AssignedTo/DisplayName"), CompareType.EQUAL, getUser().getDisplayName(getViewContext().getUser()));
            url.addFilter(getDataRegionName(), FieldKey.fromString("Status"), CompareType.NEQ_OR_NULL, "closed");
            sort = new Sort("-Milestone");
            sort.addURLSort(url, getDataRegionName());
            url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
            item = new NavTree("mine", url);
            if ("".equals(currentView))
                item.setStrong(target.toString().equals(url.toString()));
            menu.addMenuItem(item);
        }

        // sort the grid view alphabetically, with private views over public ones
        List<CustomView> views = new ArrayList<>(getQueryDef().getCustomViews(getViewContext().getUser(), getViewContext().getRequest(), false, false).values());
        Collections.sort(views, new Comparator<CustomView>() {
            public int compare(CustomView o1, CustomView o2)
            {
                if (!o1.isShared() && o2.isShared()) return -1;
                if (o1.isShared() && !o2.isShared()) return 1;
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
            String label = view.getName();
            if (label == null)
                continue;

            if (addSep)
            {
                menu.addSeparator();
                addSep = false;
            }
            item = new NavTree(label, target.clone().replaceParameter(param(QueryParam.viewName), label).getLocalURIString());
            item.setId("GridViews:" + PageFlowUtil.filter(label));
            if (label.equals(currentView))
                item.setStrong(true);

            StringBuilder description = new StringBuilder();
            if (view.isSession())
            {
                item.setEmphasis(true);
                description.append("Unsaved ");
            }
            if (view.isShared())
                description.append("Shared ");
            if (description.length() > 0)
                item.setDescription(description.toString());

            if (view.isShared())
                item.setImageSrc(getViewContext().getContextPath() + "/reports/grid_shared.gif");
            else
            {
                item.setImageSrc(getViewContext().getContextPath() + "/reports/grid.gif");
                item.setImageCls("fa fa-table");
            }
            menu.addMenuItem(item);
        }
    }

    protected void populateReportButtonBar(ButtonBar bar)
    {
        super.populateReportButtonBar(bar);

        ActionButton adminButton = new ActionButton(IssuesController.AdminAction.class, "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
        adminButton.setDisplayPermission(AdminPermission.class);
        bar.add(adminButton);

        if (!getUser().isGuest())
        {
            ActionButton prefsButton = new ActionButton(IssuesController.EmailPrefsAction.class, "Email Preferences", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            bar.add(prefsButton);
        }
    }

    protected ActionURL urlFor(QueryAction action)
    {
        switch (action)
        {
            case exportRowsTsv:
                final ActionURL url = _context.cloneActionURL().setAction(IssuesController.ExportTsvAction.class);
                for (Pair<String, String> param : super.urlFor(action).getParameters())
                {
                    url.addParameter(param.getKey(), param.getValue());
                }
                return url;
        }
        return super.urlFor(action);
    }
}
