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

package org.labkey.api.study.assay;

import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.study.actions.ShowSelectedDataAction;
import org.labkey.api.study.actions.PublishStartAction;
import org.labkey.api.security.permissions.InsertPermission;

import java.util.List;

/**
 * User: brittp
* Date: Oct 10, 2007
* Time: 2:24:42 PM
*/
public class AssayRunType extends ExperimentRunType
{
    public static final String SCHEMA_NAME = "assay";
    private final ExpProtocol _protocol;
    private final Container _container;

    public AssayRunType(ExpProtocol protocol, Container c)
    {
        super(protocol.getName(), SCHEMA_NAME, AssayService.get().getRunsTableName(protocol));
        _protocol = protocol;
        _container = c;
    }

    @Override
    public void populateButtonBar(ViewContext context, ButtonBar bar, DataView view, ContainerFilter filter)
    {
        TableInfo table = view.getDataRegion().getTable();
        ActionURL target = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(context.getContainer(), _protocol, ShowSelectedDataAction.class);
        if (table.getContainerFilter() != null)
            target.addParameter("containerFilterName", table.getContainerFilter().getType().name());
        ActionButton viewSelectedButton = new ActionButton(target, "Show Results");
        viewSelectedButton.setURL(target);
        viewSelectedButton.setRequiresSelection(true);
        viewSelectedButton.setActionType(ActionButton.Action.POST);
        bar.add(viewSelectedButton);

        if (AssayService.get().getProvider(_protocol).canCopyToStudy())
        {
            ActionURL copyURL = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(context.getContainer(), _protocol, PublishStartAction.class);
            copyURL.addParameter("runIds", true);
            if (table.getContainerFilter() != null)
                copyURL.addParameter("containerFilterName", table.getContainerFilter().getType().name());
            ActionButton copySelectedButton = new ActionButton(copyURL, "Copy to Study");
            copySelectedButton.setDisplayPermission(InsertPermission.class);
            copySelectedButton.setURL(copyURL);
            copySelectedButton.setRequiresSelection(true);
            copySelectedButton.setActionType(ActionButton.Action.POST);
            bar.add(copySelectedButton);
        }

        List<ActionButton> buttons = AssayService.get().getImportButtons(_protocol, context.getUser(), context.getContainer(), false);
        bar.addAll(buttons);
    }

    public Priority getPriority(ExpProtocol protocol)
    {
        if (_protocol.getLSID().equals(protocol.getLSID()))
        {
            return Priority.HIGH;
        }
        return null;
    }
}
