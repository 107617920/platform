/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.ParticipantMapper;
import org.labkey.study.model.StudyImpl;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:43:07 PM
 */
public class StudyExportContext extends AbstractContext
{
    private final boolean _oldFormats;
    private final Set<String> _dataTypes;
    private final List<DataSetDefinition> _datasets = new LinkedList<DataSetDefinition>();
    private final Set<Integer> _datasetIds = new HashSet<Integer>();
    private final boolean _removeProtected;
    private final ParticipantMapper _participantMapper;

    public StudyExportContext(StudyImpl study, User user, Container c, boolean oldFormats, Set<String> dataTypes, Logger logger)
    {
        this(study, user, c, oldFormats, dataTypes, false, new ParticipantMapper(study, false, false), logger);
    }

    public StudyExportContext(StudyImpl study, User user, Container c, boolean oldFormats, Set<String> dataTypes, boolean removeProtected, ParticipantMapper participantMapper, Logger logger)
    {
        super(user, c, StudyXmlWriter.getStudyDocument(), logger, null);
        _oldFormats = oldFormats;
        _dataTypes = dataTypes;
        _removeProtected = removeProtected;
        _participantMapper = participantMapper;
        initializeDatasets(study);
    }

    public boolean useOldFormats()
    {
        return _oldFormats;
    }

    public Set<String> getDataTypes()
    {
        return _dataTypes;
    }

    @Override
    public boolean isRemoveProtected()
    {
        return _removeProtected;
    }

    @Override
    public boolean isShiftDates()
    {
        return getParticipantMapper().isShiftDates();
    }

    @Override
    public boolean isAlternateIds()
    {
        return getParticipantMapper().isAlternateIds();
    }

    private void initializeDatasets(StudyImpl study)
    {
        boolean includeCRF = _dataTypes.contains(DatasetWriter.SELECTION_TEXT);
        boolean includeAssay = _dataTypes.contains(AssayDatasetWriter.SELECTION_TEXT);

        for (DataSetDefinition dataset : study.getDataSetsByType(new String[]{DataSet.TYPE_STANDARD, DataSet.TYPE_PLACEHOLDER}))
        {
            if ((!dataset.isAssayData() && includeCRF) || (dataset.isAssayData() && includeAssay))
            {
                _datasets.add(dataset);
                _datasetIds.add(dataset.getDataSetId());
            }
        }
    }

    public boolean isExportedDataset(Integer datasetId)
    {
        return _datasetIds.contains(datasetId);
    }

    public List<DataSetDefinition> getDatasets()
    {
        return _datasets;
    }

    public ParticipantMapper getParticipantMapper()
    {
        return _participantMapper;
    }
}
