/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.view;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.DefaultViewInfo;
import org.labkey.api.query.*;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: 3/17/13
 */
public abstract class AbstractQueryDataViewProvider implements DataViewProvider
{
    @Override
    public void initialize(ContainerUser context)
    {
    }

    @Override
    public List<DataViewInfo> getViews(ViewContext context) throws Exception
    {
        List<DataViewInfo> dataViews = new ArrayList<>();

        for (CustomView view : getCustomViews(context))
        {
            DefaultViewInfo info = new DefaultViewInfo(getType(), view.getEntityId(), view.getName(), view.getContainer());

            info.setType("Query");

            ViewCategory vc = ReportUtil.getDefaultCategory(context.getContainer(), view.getSchemaName(), view.getQueryName());
            info.setCategory(vc);

            info.setCreatedBy(view.getCreatedBy());
            info.setModified(view.getModified());
            info.setShared(view.getOwner() == null);
            info.setAccess(view.isShared() ? "public" : "private");

            info.setSchemaName(view.getSchemaName());
            info.setQueryName(view.getQueryName());

            // run url and details url are the same for now
            ActionURL runUrl = getViewRunURL(context.getUser(), context.getContainer(), view);

            info.setRunUrl(runUrl);
            info.setDetailsUrl(runUrl);

            if (!StringUtils.isEmpty(view.getCustomIconUrl()))
            {
                URLHelper url = new URLHelper(view.getCustomIconUrl());
                url.setContextPath(AppProps.getInstance().getParsedContextPath());
                info.setIcon(url.toString());
            }

            info.setThumbnailUrl(PageFlowUtil.urlProvider(QueryUrls.class).urlThumbnail(context.getContainer()));

            dataViews.add(info);
        }
        return dataViews;
    }

    protected List<CustomView> getCustomViews(ViewContext context)
    {
        List<CustomView> views = new ArrayList<>();

        for (CustomView view : QueryService.get().getCustomViews(context.getUser(), context.getContainer(), context.getUser(), null, null, true))
        {
            if (view.isHidden())
                continue;

            if (view.getName() == null)
                continue;

            if (includeView(context, view))
                views.add(view);
        }
        return views;
    }

    protected abstract boolean includeView(ViewContext context, CustomView view);

    private ActionURL getViewRunURL(User user, Container c, CustomViewInfo view)
    {
        String dataregionName = QueryView.DATAREGIONNAME_DEFAULT;

        if (StudyService.get().getStudy(c) != null)
        {
            if (StudyService.get().getDatasetIdByQueryName(c, view.getQueryName()) != -1)
                dataregionName = "Dataset";
        }
        return QueryService.get().urlFor(user, c,
                QueryAction.executeQuery, view.getSchemaName(), view.getQueryName()).
                addParameter(dataregionName + "." + QueryParam.viewName.name(), view.getName());
    }

    @Override
    public DataViewProvider.EditInfo getEditInfo()
    {
        return null;
    }

    @Override
    public boolean isVisible(Container container, User user)
    {
        return true;
    }
}
