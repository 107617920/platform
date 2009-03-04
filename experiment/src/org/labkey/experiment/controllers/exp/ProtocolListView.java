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

package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.GridView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.List;

/**
 * User: jeckels
* Date: Dec 18, 2007
*/
public class ProtocolListView extends GridView
{
    public ProtocolListView(ExpProtocol protocol, Container c)
    {
        super(new DataRegion());
        TableInfo ti = ExperimentServiceImpl.get().getTinfoProtocolActionDetails();
        List<ColumnInfo> cols = ti.getColumns("RowId,Name,ActionSequence,ProtocolDescription");
        getDataRegion().setColumns(cols);
        getDataRegion().getDisplayColumn(0).setVisible(false);
        getDataRegion().getDisplayColumn(1).setURL(ActionURL.toPathString("Experiment", "protocolPredecessors", c) + "?ParentLSID=" + PageFlowUtil.encode(protocol.getLSID()) + "&Sequence=${ActionSequence}");
        getDataRegion().getDisplayColumn(2).setTextAlign("left");
        getDataRegion().setShadeAlternatingRows(true);
        getDataRegion().setShowBorders(true);

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("ParentProtocolLSID", protocol.getLSID(), CompareType.EQUAL);
        filter.addCondition("ChildProtocolLSID", protocol.getLSID(), CompareType.NEQ);
        setFilter(filter);

        setSort(new Sort("ActionSequence"));

        getDataRegion().setButtonBar(new ButtonBar());

        setTitle("Protocol Steps");
    }
}
