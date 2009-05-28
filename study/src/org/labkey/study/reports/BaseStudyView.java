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

package org.labkey.study.reports;

import org.labkey.study.model.StudyManager;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.VisitImpl;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.view.HttpView;
import org.labkey.api.study.Study;
import org.labkey.api.study.DataSet;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Feb 1, 2006
 * Time: 10:01:53 AM
 *
 * Useful helpers for any study view
 */
public class BaseStudyView<T> extends HttpView<T>
{
    Study _study;
    StudyManager _studyManager;
    DbSchema _schema;
    TableInfo _tableVisitMap;

    public BaseStudyView(Study study)
    {
        _study = study;

        _studyManager = StudyManager.getInstance();
        _schema = StudyManager.getSchema();
        _tableVisitMap = StudySchema.getInstance().getTableInfoVisitMap();
    }


    private VisitImpl[] _visits;            // display ordered
    private HashMap<Integer, VisitImpl> _visitMap = new HashMap<Integer, VisitImpl>();
    private DataSetDefinition[] _datasetDefs;
    private HashMap<Integer, DataSetDefinition> _datasetMap = new HashMap<Integer, DataSetDefinition>();

    protected VisitImpl[] getVisits()
    {
        if (null == _visits)
        {
            _visits =  _studyManager.getVisits(_study);
            for (VisitImpl v : _visits)
                _visitMap.put(v.getRowId(), v);
        }
        return _visits;
    }

    protected VisitImpl getVisit(int v)
    {
        getVisits();
        return _visitMap.get(v);
    }

    protected DataSet[] getDatasets()
    {
        if (null == _datasetDefs)
        {
            _datasetDefs = _studyManager.getDataSetDefinitions(_study);
            for (DataSetDefinition d : _datasetDefs)
                _datasetMap.put(d.getDataSetId(), d);
        }
        return _datasetDefs;
    }

    protected DataSet getDatasetDefinition(int d)
    {
        getDatasets();
        return _datasetMap.get(d);
    }
}