/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryAction;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.SimpleFilter;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 6, 2006
 */
public class ReportQueryView extends QueryView
{
    protected SimpleFilter _filter;

    public ReportQueryView(UserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
        setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
    }

    public void setFilter(SimpleFilter filter)
    {
        _filter = filter;
    }

    public DataRegion createDataRegion()
    {
        DataRegion region = super.createDataRegion();
        region.setShadeAlternatingRows(true);
        region.setShowColumnSeparators(true);
        return region;
    }

    public ActionURL getCustomizeURL()
    {
        return urlFor(QueryAction.chooseColumns);
    }

    public ResultSet getResultSet(int maxRows) throws Exception
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setMaxRows(maxRows);
        return rgn.getResultSet(view.getRenderContext());
    }

    public Map<String, ColumnInfo> getColumnMap()
    {
        TableInfo table = getTable();
        if (table == null)
            return Collections.emptyMap();

        Map<String, ColumnInfo> ret = new LinkedHashMap<String, ColumnInfo>();
        List<ColumnInfo> columns = getQueryDef().getColumns(getCustomView(), table);
        for (ColumnInfo col : columns)
            ret.put(col.getAlias(), col);

        return ret;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();

        if (_filter != null)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (filter != null)
                filter.addAllClauses(_filter);
            else
                filter = _filter;
            view.getRenderContext().setBaseFilter(filter);
        }
        return view;
    }
}
