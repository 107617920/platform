/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.ActionURL;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Nov 6, 2006
 * Time: 11:00:12 AM
 */
public class AssayPublishService
{
    private static AssayPublishService.Service _serviceImpl;

    public static final String PARTICIPANTID_PROPERTY_NAME = "ParticipantID";
    public static final String SEQUENCENUM_PROPERTY_NAME = "SequenceNum";
    public static final String DATE_PROPERTY_NAME = "Date";
    public static final String SOURCE_LSID_PROPERTY_NAME = "SourceLSID";
    public static final String TARGET_STUDY_PROPERTY_NAME = "TargetStudy";

    public static final String AUTO_COPY_TARGET_PROPERTY_URI = "terms.labkey.org#AutoCopyTargetContainer";

    public interface Service
    {
        ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                      List<Map<String,Object>> dataMaps, Map<String, PropertyType> propertyTypes, List<String> errors);

        ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                       List<Map<String, Object>> dataMaps, Map<String, PropertyType> propertyTypes, String keyPropertyName, List<String> errors);

        ActionURL publishAssayData(User user, Container sourceContainer, @Nullable Container targetContainer, String assayName, ExpProtocol protocol,
                                       List<Map<String, Object>> dataMaps, String keyPropertyName, List<String> errors);

        /**
         * Set of studies the user has permission to.
         */
        Set<Study> getValidPublishTargets(User user, Class<? extends Permission> permission);

        ActionURL getPublishHistory(Container container, ExpProtocol protocol);
        ActionURL getPublishHistory(Container container, ExpProtocol protocol, ContainerFilter containerFilter);

        TimepointType getTimepointType(Container container);

        /**
         * Automatically copy assay data to a study if the design is set up to do so
         * @return any errors that prevented the copy
         */
        public List<String> autoCopyResults(ExpProtocol protocol, ExpRun run, User user, Container container);

        /** Checks if the assay and specimen participant/visit/dates don't match based on the specimen id and target study */
        boolean hasMismatchedInfo(AssayProvider provider, ExpProtocol protocol, List<Integer> allObjects, AssaySchema schema);
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
