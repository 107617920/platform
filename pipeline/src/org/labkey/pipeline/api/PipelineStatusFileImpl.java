/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

package org.labkey.pipeline.api;

import org.labkey.api.data.Entity;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;

/**
 * @author B. MacLean
 */
public class PipelineStatusFileImpl extends Entity implements Serializable, PipelineStatusFile
{
    private static HashSet<String> _inactiveStatuses = new HashSet<String>(Arrays.asList(
            PipelineJob.COMPLETE_STATUS,
            PipelineJob.CANCELLED_STATUS,
            PipelineJob.WAITING_FOR_FILES,
            PipelineJob.ERROR_STATUS,
            PipelineJob.SPLIT_STATUS    // Depends on status of split jobs
    ));

    private static HashSet<String> _emailStatuses = new HashSet<String>(Arrays.asList(
            PipelineJob.COMPLETE_STATUS,
            PipelineJob.ERROR_STATUS,
            PipelineJob.CANCELLED_STATUS
    ));

    protected int _rowId;
    protected String _job;
    protected String _jobParent;
    protected String _jobStore;
    protected String _activeTaskId;
    protected String _provider;
    protected String _status;
    protected String _info;
    protected String _dataUrl;
    protected String _description;
    protected String _filePath;
    protected String _email;
    private boolean _hadError;

    private int MAX_STATUS_LENGTH = 100;
    private int MAX_INFO_LEN = 1024;
    private int MAX_FILEPATH_LENGTH = 1024;
    private int MAX_DATAURL_LENGTH = 1024;
    private int MAX_DESCRIPTION_LENGTH = 255;
    private int MAX_EMAIL_LENGTH = 255;

    public PipelineStatusFileImpl()
    {
    }

    public PipelineStatusFileImpl(PipelineJob job, String status, String info)
    {
        assert(job.getLogFile() != null) : "Must have a status file to set status.";

        setJob(job.getJobGUID());
        setJobParent(job.getParentGUID());
        setProvider(job.getProvider());
        setEmail(job.getInfo().getUserEmail());
        setDescription(job.getDescription());
        setFilePath(job.getLogFile().getAbsolutePath());
        setStatus(status);
        setInfo(info);

        if (PipelineJob.COMPLETE_STATUS.equals(status))
        {
            URLHelper urlData = job.getStatusHref();
            if (urlData != null)
            {
                if (urlData instanceof ActionURL)
                    setDataUrl(urlData.getLocalURIString());
                else
                    setDataUrl(urlData.getURIString());
            }
        }
        // Store the job that was split, so we will have a place to merge
        // changes from the split-jobs.
        else if (PipelineJob.SPLIT_STATUS.equals(status))
        {
            // The taskId is not really valid for the joined job at this point,
            // so avoid asking the TaskFactory about it.
            setActiveTaskId(job.getActiveTaskId().toString());
            setJobStore(PipelineJobService.get().getJobStore().toXML(job));
        }
        // If there is an active task and this is waiting state, then checkpoint the
        // job to the database for retry.
        else if (PipelineJob.WAITING_STATUS.equals(status) ||
                (job.getActiveTaskFactory() != null &&
                    PipelineJob.TaskStatus.waiting.equals(job.getActiveTaskStatus())))
        {
            if (job.getActiveTaskFactory() != null)
                setActiveTaskId(job.getActiveTaskFactory().getActiveId(job).toString());
            setJobStore(PipelineJobService.get().getJobStore().toXML(job));
        }
    }

    public void beforeUpdate(User user, Entity cur)
    {
        super.beforeUpdate(user, cur);

        PipelineStatusFileImpl curSF = (PipelineStatusFileImpl) cur;

        _rowId = curSF._rowId;

        // Preserve original values across updates, if not explicitly changed.
        if (_email == null || _email.length() == 0)
            _email = curSF._email;
        if (_provider == null || _provider.length() == 0)
            _provider = curSF._provider;
        if (_description == null || _description.length() == 0)
            _description = curSF._description;
        if (_job == null || _job.length() == 0)
            _job = curSF._job;
        if (_dataUrl == null || _dataUrl.length() == 0)
            _dataUrl = curSF._dataUrl;
        // _hadError?

        if (curSF.getJobParent() != null && !curSF.getJobParent().equals(getJobParent()))
        {
            // If the job's parent has changed, check to make sure that the parent we're trying to point to
            // is still in the database. The "new" value may have been persisted through XML files for submitting
            // to the cluster. If the parent was reset, its GUID will have changed and we don't want to use the one
            // from the XML file anymore.
            try
            {
                PipelineStatusFileImpl parentStatusFile = PipelineStatusManager.getJobStatusFile(getJobParent());
                if (parentStatusFile == null)
                {
                    // Can't find that parent anymore, so revert the parent GUID that's currently in the database
                    setJobParent(curSF.getJobParent());
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }


        // Clear any stored job, if the status is complete.
        if (PipelineJob.COMPLETE_STATUS.equals(_status))
        {
            _jobStore = null;
            _activeTaskId = null;
        }
        // Otherwise preseve what is currently in the database.
        else
        {
            if (_jobStore == null || _jobStore.length() == 0)
                _jobStore = curSF._jobStore;
            if (_activeTaskId == null || _activeTaskId.length() == 0)
                _activeTaskId = curSF._activeTaskId;
        }
    }

    public boolean isActive()
    {
        return !_inactiveStatuses.contains(_status);
    }

    public boolean isEmailStatus()
    {
        return _jobParent == null && _emailStatuses.contains(_status);
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        this._rowId = rowId;
    }

    public String getJobId()
    {
        return getJob();
    }

    public String getJob()
    {
        return _job;
    }

    public void setJob(String job)
    {
        this._job = job;
    }

    public String getJobParentId()
    {
        return getJobParent();
    }

    public String getJobParent()
    {
        return _jobParent;
    }

    public void setJobParent(String jobParent)
    {
        _jobParent = jobParent;
    }

    public String getJobStore()
    {
        return _jobStore;
    }

    public void setJobStore(String jobStore)
    {
        _jobStore = jobStore;
    }

    public PipelineJob createJobInstance()
    {
        if (_jobStore == null)
            return null;

        return PipelineJobService.get().getJobStore().fromXML(_jobStore);
    }

    public String getActiveTaskId()
    {
        return _activeTaskId;
    }

    public void setActiveTaskId(String activeTaskId)
    {
        _activeTaskId = activeTaskId;
    }

    public String getProvider()
    {
        return _provider;
    }

    public void setProvider(String provider)
    {
        this._provider = provider;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        if (status != null && status.length() > MAX_STATUS_LENGTH)
            status = status.substring(0, MAX_STATUS_LENGTH);
        this._status = status;
        if (PipelineJob.ERROR_STATUS.equalsIgnoreCase(status))
            _hadError = true;
    }

    public String getInfo()
    {
        return _info;
    }

    public void setInfo(String info)
    {
        if (info != null && info.length() > MAX_INFO_LEN)
            info = info.substring(0, MAX_INFO_LEN);
        this._info = info;
    }

    public String getFilePath()
    {
        return _filePath;
    }

    public void setFilePath(String filePath)
    {
        if (filePath != null && filePath.length() > MAX_FILEPATH_LENGTH)
            filePath = filePath.substring(0, MAX_FILEPATH_LENGTH);
        this._filePath = PipelineJobService.statusPathOf(filePath);
    }

    public String getDataUrl()
    {
        return _dataUrl;
    }

    public void setDataUrl(String dataUrl)
    {
        if (dataUrl != null && dataUrl.length() > MAX_DATAURL_LENGTH)
            dataUrl = dataUrl.substring(0, MAX_DATAURL_LENGTH);
        this._dataUrl = dataUrl;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH)
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        this._description = description;
    }

    public String getEmail()
    {
        return _email;
    }

    public void setEmail(String email)
    {
        if (email != null && email.length() > MAX_EMAIL_LENGTH)
            email = email.substring(0, MAX_EMAIL_LENGTH);
        this._email = email;
    }

    public boolean isHadError()
    {
        return _hadError;
    }

    public void setHadError(boolean hadError)
    {
        this._hadError = hadError;
    }
}
