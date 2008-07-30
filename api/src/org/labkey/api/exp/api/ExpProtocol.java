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

package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.ProtocolParameter;

import java.util.Map;
import java.util.Date;
import java.util.List;
import java.sql.SQLException;

public interface ExpProtocol extends ExpObject
{
    Map<String, ObjectProperty> retrieveObjectProperties();

    public static final String ASSAY_DOMAIN_PREFIX = "AssayDomain-";
    public static final String ASSAY_DOMAIN_RUN = ASSAY_DOMAIN_PREFIX + "Run";
    public static final String ASSAY_DOMAIN_UPLOAD_SET = ASSAY_DOMAIN_PREFIX + "Batch";
    public static final String ASSAY_DOMAIN_DATA = ASSAY_DOMAIN_PREFIX + "Data";

    void storeObjectProperties(Map<String, ObjectProperty> props);

    Map<String, ProtocolParameter> retrieveProtocolParameters() throws SQLException;

    String getInstrument();

    String getSoftware();

    String getContact();

    List<ExpProtocol> getChildProtocols();

    enum ApplicationType
    {
        ExperimentRun,
        ProtocolApplication,
        ExperimentRunOutput,
    }

    public List<ExpProtocolAction> getSteps();
    public ApplicationType getApplicationType();
    public ProtocolImplementation getImplementation();
    public String getDescription();
    Integer getMaxInputMaterialPerInstance();
    String getProtocolDescription();
    void setProtocolDescription(String description);
    void setMaxInputMaterialPerInstance(Integer maxMaterials);
    void setMaxInputDataPerInstance(Integer i);

    /**
     * Adds a step and persists it to the database
     */
    public ExpProtocolAction addStep(User user, ExpProtocol childProtocol, int actionSequence);

    public ExpProtocol[] getParentProtocols();

    public void setApplicationType(ApplicationType type);
    public void setDescription(String description);
    public void save(User user);
}
