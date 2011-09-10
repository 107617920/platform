package org.labkey.visualization.sql;

import org.labkey.api.data.ColumnInfo;

/**
 * Copyright (c) 2011 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Feb 4, 2011 4:52:11 PM
 */
public class VisualizationIntervalColumn
{
    private enum Interval
    {
        DAY("Days") {
            @Override
            public String getSQL(VisualizationSourceColumn startDate, VisualizationSourceColumn endDate)
            {
                return "TIMESTAMPDIFF(SQL_TSI_DAY, " + startDate.getAlias() + ", " + endDate.getOtherAlias() + ")";
            }},
        WEEK("Weeks") {
            @Override
            public String getSQL(VisualizationSourceColumn startDate, VisualizationSourceColumn endDate)
            {
                return "CAST(FLOOR((" + Interval.DAY.getSQL(startDate, endDate) + ")/7) AS Integer)";
            }},
        MONTH("Months") {
            @Override
            public String getSQL(VisualizationSourceColumn startDate, VisualizationSourceColumn endDate)
            {
                return "AGE(" + startDate.getAlias() + ", " + endDate.getOtherAlias() + ", SQL_TSI_MONTH)";
            }},
        YEAR("Years") {
            @Override
            public String getSQL(VisualizationSourceColumn startDate, VisualizationSourceColumn endDate)
            {
                return "AGE(" + startDate.getAlias() + ", " + endDate.getOtherAlias() + ", SQL_TSI_YEAR)";
            }};

        private String _label;
        Interval(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        public abstract String getSQL(VisualizationSourceColumn startDate, VisualizationSourceColumn endDate);
    }

    private VisualizationSourceColumn _startDate;
    private VisualizationSourceColumn _endDate;
    private Interval _interval;

    public VisualizationIntervalColumn(VisualizationSourceColumn startDate, VisualizationSourceColumn endDate, String interval)
    {
        _startDate = startDate;
        _endDate = endDate;
        // be fault-tolerant in case the user asked for 'days' instead of 'day'
        if (interval.endsWith("s"))
            interval = interval.substring(0, interval.length() - 1);
        _interval = Interval.valueOf(interval.toUpperCase());
    }

    public VisualizationSourceColumn getEndDate()
    {
        return _endDate;
    }

    public String getLabel()
    {
        return _interval.getLabel();
    }

    public Interval getInterval()
    {
        return _interval;
    }

    public VisualizationSourceColumn getStartDate()
    {
        return _startDate;
    }

    public String getSQL()
    {
        return _interval.getSQL(_startDate, _endDate);
    }

    public String getSimpleAlias()
    {
        return _interval.getLabel();
    }

    public String getFullAlias()
    {
        return ColumnInfo.legalNameFromName(_endDate.getSchemaName() + "_" + _endDate.getQueryName() + "_" + _endDate.getOriginalName() + "_" + getSimpleAlias());
    }
}
