/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.data.Container;

import java.sql.SQLException;
import java.io.IOException;
import java.util.Date;

/**
 * <code>PipelineStatusFile</code>
 *
 * @author brendanx
 */
public interface PipelineStatusFile
{
    public interface StatusReader
    {
        PipelineStatusFile getStatusFile(String path) throws SQLException;

        PipelineStatusFile[] getQueuedStatusFiles() throws SQLException;

        PipelineStatusFile[] getQueuedStatusFiles(Container c) throws SQLException;
    }

    public interface StatusWriter
    {
        void setStatusFile(PipelineJob job, String status, String statusInfo) throws Exception;

        void ensureError(PipelineJob job) throws Exception;
    }

    public interface JobStore
    {
        String toXML(PipelineJob job);

        PipelineJob fromXML(String xml);

        void storeJob(PipelineJob job) throws SQLException;

        PipelineJob getJob(String jobId) throws SQLException;

        void retry(String jobId) throws IOException, SQLException;

        void retry(PipelineStatusFile sf) throws IOException;

        void split(PipelineJob job) throws IOException, SQLException;

        void join(PipelineJob job) throws IOException, SQLException;
    }

    Container lookupContainer();

    boolean isActive();

    Date getCreated();

    Date getModified();

    int getRowId();

    String getJobId();

    String getJobParentId();

    String getProvider();

    String getStatus();

    void setStatus(String status);

    String getInfo();

    void setInfo(String info);

    String getFilePath();

    String getDataUrl();

    String getDescription();

    String getEmail();

    boolean isHadError();

    String getJobStore();

    PipelineJob createJobInstance();

    @Deprecated
    void synchDiskStatus() throws IOException;
}

