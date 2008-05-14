/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.chart.client;

import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.ChartService;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 7, 2007
 */
public interface StudyChartService extends ChartService
{
    /**
     * Map of dataset names to ids
     *
     * @gwt.typeArgs <org.labkey.study.chart.client.model.GWTPair>
     */
    public List getStudyDatasets();
    
    /**
     * @return a list of errors
     *
     * @gwt.typeArgs <java.lang.String>
     */
    public List saveCharts(GWTChart[] chart, Map properties);
}
