/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.FilteredTable;
import org.jetbrains.annotations.NotNull;
/*
 * User: brittp
 * Date: Mar 15, 2009
 * Time: 10:55:45 AM
 */

public class ProtocolFilteredObjectTable extends FilteredTable
{
    private String _protocolLsid;
    public ProtocolFilteredObjectTable(Container container, String protocolLsid)
    {
        super(OntologyManager.getTinfoObject(), container);
        wrapAllColumns(true);
        _protocolLsid = protocolLsid;
    }

    @Override @NotNull
    public SQLFragment getFromSQL()
    {
        SQLFragment fromSQL = new SQLFragment("(");
        fromSQL.append("SELECT o.*, d.RowID as DataID, r.RowID AS RunID FROM " + getFromTable() + " o\n" +
                "\tJOIN exp.Object parent ON \n" +
                "\t\to.OwnerObjectId = parent.ObjectId \n" +
                "\tJOIN exp.Data d ON \n" +
                "\t\tparent.ObjectURI = d.lsid \n" +
                "\tJOIN exp.ExperimentRun r ON \n" +
                "\t\td.RunId = r.RowId AND \n" +
                "\t\tr.ProtocolLSID = ?");
        fromSQL.add(_protocolLsid);
        fromSQL.append(")");
        return fromSQL;
    }
}