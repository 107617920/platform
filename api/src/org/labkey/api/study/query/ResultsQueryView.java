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

package org.labkey.api.study.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.security.ACL;
import org.labkey.api.study.actions.PublishStartAction;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

/**
 * User: brittp
 * Date: Jun 29, 2007
 * Time: 11:13:44 AM
 */
public class ResultsQueryView extends AssayBaseQueryView
{
    public ResultsQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
    {
        super(protocol, context, settings);
        setViewItemFilter(new ReportService.ItemFilter() {
            public boolean accept(String type, String label)
            {
                if (RReport.TYPE.equals(type)) return true;
                if (ChartQueryReport.TYPE.equals(type)) return true;
                return false;
            }
        });
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        view.getRenderContext().setBaseSort(new Sort(AssayService.get().getProvider(_protocol).getDataRowIdFieldKey().toString()));
        view.getDataRegion().addHiddenFormField("rowId", "" + _protocol.getRowId());
        String returnURL = getViewContext().getRequest().getParameter("returnURL");
        if (returnURL == null)
            returnURL = getViewContext().getActionURL().toString();
        view.getDataRegion().addHiddenFormField("returnURL", returnURL);
        if (showControls())
        {
            if (!AssayPublishService.get().getValidPublishTargets(getUser(), ACL.PERM_INSERT).isEmpty())
            {
                ButtonBar bbar = new ButtonBar(view.getDataRegion().getButtonBar(DataRegion.MODE_GRID));

                AssayProvider provider = AssayService.get().getProvider(_protocol);

                ActionURL publishURL = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(getContainer(), _protocol, PublishStartAction.class);
                for (Pair<String, String> param : publishURL.getParameters())
                {
                    if (!"rowId".equalsIgnoreCase(param.getKey()))
                        view.getDataRegion().addHiddenFormField(param.getKey(), param.getValue());
                }
                publishURL.deleteParameters();

                if (getTable().getContainerFilter() != null)
                    publishURL.addParameter("containerFilterName", getTable().getContainerFilter().getType().name());

                if (provider.canCopyToStudy())
                {
                    ActionButton publishButton = new ActionButton(publishURL.getLocalURIString(),
                            "Copy to Study", DataRegion.MODE_GRID, ActionButton.Action.POST);
                    publishButton.setDisplayPermission(ACL.PERM_INSERT);
                    publishButton.setRequiresSelection(true);
                    publishButton.setActionType(ActionButton.Action.POST);

                    bbar.add(publishButton);
                }

                bbar.addAll(AssayService.get().getImportButtons(_protocol, getViewContext().getUser(), getViewContext().getContainer(), false));

                view.getDataRegion().setButtonBar(bbar);
            }
        }
        else
            view.getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        return view;
    }

    protected TSVGridWriter.ColumnHeaderType getColumnHeaderType()
    {
        return TSVGridWriter.ColumnHeaderType.caption;
    }
}
