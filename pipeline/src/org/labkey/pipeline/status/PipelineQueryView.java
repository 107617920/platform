/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
package org.labkey.pipeline.status;

import org.labkey.api.action.ApiAction;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.pipeline.PipelineController;
import org.labkey.pipeline.api.PipelineQuerySchema;
import org.springframework.validation.BindException;

/**
 * User: jeckels
 * Date: Dec 21, 2009
 */
public class PipelineQueryView extends QueryView
{
    private final ViewContext _context;
    private final Class<? extends ApiAction> _apiAction;
    private final boolean _minimal;

    public PipelineQueryView(ViewContext context, BindException errors, Class<? extends ApiAction> apiAction, boolean minimal)
    {
        super(new PipelineQuerySchema(context.getUser(), context.getContainer()), null, errors);
        setSettings(createSettings(context));
        _minimal = minimal;
        getSettings().setAllowChooseQuery(false);
        _context = context;
        _apiAction = apiAction;

        setShadeAlternatingRows(true);
        setShowBorders(true);

        setButtonBarPosition(minimal ? DataRegion.ButtonBarPosition.TOP : DataRegion.ButtonBarPosition.BOTH);
    }

    private QuerySettings createSettings(ViewContext context)
    {
        return getSchema().getSettings(context, "StatusFiles", "job");
    }

    @Override
    protected DataRegion createDataRegion()
    {
        StatusDataRegion rgn = new StatusDataRegion();
        configureDataRegion(rgn);
        rgn.setApiAction(_apiAction);
        return rgn;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();

        if (_minimal)
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("Status", PipelineJob.COMPLETE_STATUS, CompareType.NEQ);
            view.getRenderContext().setBaseFilter(filter);
        }

        view.getRenderContext().setBaseSort(new Sort("-Created"));
        if (_context.getContainer() == null || _context.getContainer().isRoot())
        {
            view.getRenderContext().setUseContainerFilter(false);
        }
        return view;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar, boolean exportAsWebPage)
    {
        if (getContainer().hasPermission(getUser(), InsertPermission.class) && PipelineService.get().hasValidPipelineRoot(getContainer()))
        {
            ActionButton button = new ActionButton(PipelineController.BrowseAction.class, "Process and Import Data");
            button.setActionType(ActionButton.Action.LINK);
            button.setURL(PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(getContainer(), PipelineController.RefererValues.pipeline.toString()));
            bar.add(button);
        }

        if (PipelineService.get().canModifyPipelineRoot(getUser(), getContainer()))
        {
            ActionButton button = new ActionButton(PipelineController.SetupAction.class, "Setup");
            button.setActionType(ActionButton.Action.LINK);
            button.setURL(PipelineController.urlSetup(getContainer(), PipelineController.RefererValues.pipeline.toString()));
            bar.add(button);
        }

        if (!_minimal)
        {
            ActionURL retryURL = new ActionURL(StatusController.RunActionAction.class, getContainer());
            retryURL.addParameter("action", PipelineProvider.CAPTION_RETRY_BUTTON);
            ActionButton retryStatus = new ActionButton(retryURL, PipelineProvider.CAPTION_RETRY_BUTTON);
            retryStatus.setRequiresSelection(true);
            retryStatus.setActionType(ActionButton.Action.POST);
            retryStatus.setDisplayPermission(UpdatePermission.class);
            bar.add(retryStatus);

            ActionButton deleteStatus = new ActionButton(StatusController.DeleteStatusAction.class, "Delete");
            deleteStatus.setRequiresSelection(true);
            deleteStatus.setActionType(ActionButton.Action.POST);
            deleteStatus.setDisplayPermission(DeletePermission.class);
            bar.add(deleteStatus);

            ActionButton cancelButton = new ActionButton(StatusController.CancelStatusAction.class, "Cancel");
            cancelButton.setRequiresSelection(true);
            cancelButton.setActionType(ActionButton.Action.POST);
            cancelButton.setDisplayPermission(DeletePermission.class);
            bar.add(cancelButton);

            ActionButton completeStatus = new ActionButton(StatusController.CompleteStatusAction.class, "Complete");
            completeStatus.setRequiresSelection(true);
            completeStatus.setActionType(ActionButton.Action.POST);
            completeStatus.setDisplayPermission(UpdatePermission.class);
            bar.add(completeStatus);

            // Display the "Show Queue" button, if this is not the Enterprise Pipeline,
            // the user is an administrator, and this is the pipeline administration page.
            if (!PipelineService.get().isEnterprisePipeline() &&
                    getUser().isAdministrator() && getContainer().isRoot())
            {
                ActionButton showQueue = new ActionButton(PipelineController.urlStatus(getContainer(), true), "Show Queue");
                bar.add(showQueue);
            }

            // Add the view, export, etc buttons
            super.populateButtonBar(view, bar, exportAsWebPage);
        }
    }
}
