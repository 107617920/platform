/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.experiment;

import org.labkey.api.view.*;
import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.PrintWriter;
import java.util.List;

/**
 * User: jeckels
 * Date: Oct 20, 2005
 */
public class ProtocolWebPart extends WebPartView
{
    private static final String EXPERIMENT_RUN_TYPE = "ExperimentRun";
    private final boolean _narrow;

    public ProtocolWebPart(boolean narrow, ViewContext viewContext)
    {
        _narrow = narrow;
        setTitle("Protocols");
        setTitleHref(new ActionURL(ExperimentController.ShowProtocolGridAction.class, viewContext.getContainer()));
    }

    @Override
    public void renderView(Object model, PrintWriter out) throws Exception
    {
        Container c = getViewContext().getContainer(ACL.PERM_READ);

        DataRegion dr = new DataRegion();
        TableInfo ti = ExperimentServiceImpl.get().getTinfoProtocol();
        List<ColumnInfo> cols = ti.getColumns("RowId,Name,LSID");
        dr.setColumns(cols);
        dr.getDisplayColumn(0).setVisible(false);
        dr.getDisplayColumn(1).setURL(ActionURL.toPathString("Experiment", "protocolDetails", c) + "?rowId=${RowId}");
        dr.getDisplayColumn(2).setTextAlign("left");

        if (!_narrow)
        {
            ButtonBar bb = new ButtonBar();

            dr.setShowRecordSelectors(true);

            ActionURL deleteProtUrl = getViewContext().cloneActionURL();
            ActionButton deleteProtocol = new ActionButton("", "Delete Selected");
            deleteProtUrl.setAction("deleteProtocolByRowIds");
            deleteProtUrl.setPageFlow("Experiment");
            deleteProtocol.setScript("return verifySelected(this.form, \"" + deleteProtUrl.getLocalURIString() + "\", \"post\", \"Protocol\")");
            deleteProtocol.setActionType(ActionButton.Action.POST);
            deleteProtocol.setDisplayPermission(ACL.PERM_DELETE);
            bb.add(deleteProtocol);

            ActionURL exportURL = getViewContext().cloneActionURL();
            ActionButton exportProtocols = new ActionButton("", "Export...");
            exportURL.setAction("exportProtocolsOptions");
            exportURL.setPageFlow("Experiment");
            exportProtocols.setScript("return verifySelected(this.form, \"" + exportURL.getLocalURIString() + "\", \"post\", \"Protocol\")");
            exportProtocols.setActionType(ActionButton.Action.POST);
            exportProtocols.setDisplayPermission(ACL.PERM_DELETE);
            bb.add(exportProtocols);

            dr.setButtonBar(bb);
        }
        else
        {
            dr.setButtonBar(new ButtonBar());
            dr.getDisplayColumn("LSID").setVisible(false);
        }

        GridView gridView = new GridView(dr);
        gridView.getRenderContext().setBaseSort(new Sort("Name"));

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("ApplicationType", EXPERIMENT_RUN_TYPE, CompareType.EQUAL);
        gridView.setFilter(filter);
        gridView.setFrame(FrameType.DIV);

        include(gridView);
    }
}
