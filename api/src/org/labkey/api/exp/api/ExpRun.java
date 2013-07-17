/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface ExpRun extends ExpObject
{
    public ExpExperiment[] getExperiments();
    public ExpProtocol getProtocol();

    /**
     * @param type an optional filter for the type of data
     */
    public ExpData[] getOutputDatas(@Nullable DataType type);
    /**
     * @param inputRole if null, don't filter by input role. If non-null, filter to only the specified role
     * @param appType if null, don't filter by type. If non-null, filter to only the specified type
     */
    public ExpData[] getInputDatas(@Nullable String inputRole, @Nullable ExpProtocol.ApplicationType appType);
    public File getFilePathRoot();
    public void setFilePathRoot(File filePathRoot);
    public void setProtocol(ExpProtocol protocol);
    public void setJobId(Integer jobId);
    public ExpProtocolApplication addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType type, String name);
    
    /** Stored in the exp.experimentrun table */
    // TODO - Merge this with getComment() (backed by ontology manager) on ExpObject
    String getComments();
    /** Stored in the exp.experiment table */
    void setComments(String comments);

    void setEntityId(String entityId);
    String getEntityId();

    Map<ExpMaterial, String> getMaterialInputs();

    Map<ExpData, String> getDataInputs();

    List<ExpMaterial> getMaterialOutputs();

    List<ExpData> getDataOutputs();
    ExpData[] getAllDataUsedByRun();
    public Integer getJobId();

    ExpProtocolApplication[] getProtocolApplications();

    /** Get the protocol application that marks all of the inputs to the run as a whole */
    ExpProtocolApplication getInputProtocolApplication();

    /** Get the protocol application that marks all of the outputs to the run as a whole */
    ExpProtocolApplication getOutputProtocolApplication();

    void deleteProtocolApplications(User user);

    void setReplacedByRun(ExpRun run);
    ExpRun getReplacedByRun();

    List<? extends ExpRun> getReplacesRuns();

    /** archive Data Files primarily used when a file is deleted. */
    public void archiveDataFiles(User user);

    void setCreated(Date created);
    void setCreatedBy(User user);
}
