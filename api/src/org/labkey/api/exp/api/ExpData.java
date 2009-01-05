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
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.XarSource;
import org.labkey.api.pipeline.PipelineJob;

import java.net.URI;
import java.util.Date;
import java.io.File;
import java.sql.SQLException;

public interface ExpData extends ExpProtocolOutput
{
    DataType getDataType();
    URI getDataFileURI();
    File getDataFile();

    void setDataFileURI(URI uri);

    ExperimentDataHandler findDataHandler();

    String getDataFileUrl();

    File getFile();

    boolean isInlineImage();

    boolean isFileOnDisk();

    void setDataFileUrl(String s);

    void importDataFile(PipelineJob job, XarSource xarSource) throws ExperimentException, SQLException;
}
