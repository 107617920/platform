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

package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.util.Pair;

import java.sql.SQLException;
import java.util.Date;
import java.util.Set;

/**
 * User: brittp
 * Date: Oct 2, 2007
 * Time: 3:35:45 PM
 */
public class SpecimenService
{
    private static SpecimenService.Service _serviceImpl;

    public interface SampleInfo
    {
        String getParticipantId();
        Double getSequenceNum();
        String getSampleId();
    }

    public enum CompletionType
    {
        SpecimenGlobalUniqueId,
        ParticipantId,
        VisitId
    }

    public interface Service
    {
        /** Does a search for matching GlobalUniqueIds  */
        ParticipantVisit getSampleInfo(Container studyContainer, String globalUniqueId) throws SQLException;

        Set<ParticipantVisit> getSampleInfo(Container studyContainer, String participantId, Date date) throws SQLException;

        Set<ParticipantVisit> getSampleInfo(Container studyContainer, String participantId, Double visit) throws SQLException;

        String getCompletionURLBase(Container studyContainer, CompletionType type);

        Set<Pair<String, Date>> getSampleInfo(Container studyContainer, boolean truncateTime) throws SQLException;

        Set<Pair<String, Double>> getSampleInfo(Container studyContainer) throws SQLException;

        Lsid getSpecimenMaterialLsid(Container studyContainer, String id);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}
