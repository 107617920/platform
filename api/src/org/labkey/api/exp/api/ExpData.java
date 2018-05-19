/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Represents a virtual experiment object. Typically a file on disk, but could be something pointed at by a URI.
 */
public interface ExpData extends ExpProtocolOutput
{
    String DEFAULT_CPAS_TYPE = "Data";
    String DATA_INPUT_PARENT = "DataInputs";
    String DATA_OUTPUT_CHILD = "DataOutputs";

    DataType getDataType();
    /** Strongly typed variant of getDataFileUrl() */
    URI getDataFileURI();

    void setDataFileURI(URI uri);

    ExperimentDataHandler findDataHandler();

    String getDataFileUrl();

    boolean hasFileScheme();

    /** @return the file if this data is backed by a 'file:'-style URI. */
    @Nullable
    File getFile();

    @Nullable
    Path getFilePath();

    /** @return if this represents an image that can be rendered directly by a web browser */
    boolean isInlineImage();

    /** @return true if this points at a file that's currently available to the server as a {@link java.io.File} */
    boolean isFileOnDisk();

    /**
     * @return true if this ExpData File was generated by a PipelineJob process.
     */
    boolean isGenerated();

    boolean isFinalRunOutput();

    @Nullable
    ExpDataClass getDataClass();

    void importDataFile(PipelineJob job, XarSource xarSource) throws ExperimentException, SQLException;

    /** @return the search document id for this material */
    String getDocumentId();

    /**
     * Delete the data, optionally including any runs using this Data as an input.
     */
    void delete(User user, boolean deleteRunsUsingData);

    /** Override to signal that we never throw BatchValidationExceptions */
    @Override
    void save(User user);
}
