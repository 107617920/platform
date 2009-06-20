/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.experiment.api;

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.*;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineJob;

import java.sql.SQLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.File;

public class ExpDataImpl extends AbstractProtocolOutputImpl<Data> implements ExpData
{

    /**
     * Temporary mapping until experiment.xml contains the mime type
     */
    private static MimeMap MIME_MAP = new MimeMap();

    static public ExpDataImpl[] fromDatas(Data[] datas)
    {
        ExpDataImpl[] ret = new ExpDataImpl[datas.length];
        for (int i = 0; i < datas.length; i ++)
        {
            ret[i] = new ExpDataImpl(datas[i]);
        }
        return ret;
    }

    public ExpDataImpl(Data data)
    {
        super(data);
    }

    public URLHelper detailsURL()
    {
        return getDataType().getDetailsURL(this);
    }

    public ExpProtocolApplication[] getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter("DataId", getRowId()), ExperimentServiceImpl.get().getTinfoDataInput());
    }

    public ExpRun[] getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoDataInput(), "DataId");
    }

    public DataType getDataType()
    {
        return ExperimentService.get().getDataType(getLSIDNamespacePrefix());
    }

    public void setDataFileURI(URI uri)
    {
        if (uri != null && !uri.isAbsolute())
        {
            throw new IllegalArgumentException("URI must be absolute.");
        }
        _object.setDataFileUrl(uri == null ? null : uri.toString());
    }

    public void save(User user)
    {
        try
        {
            if (getRowId() == 0)
            {
                _object = Table.insert(user, ExperimentServiceImpl.get().getTinfoData(), _object);
            }
            else
            {
                _object = Table.update(user, ExperimentServiceImpl.get().getTinfoData(), _object, getRowId());
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public URI getDataFileURI()
    {
        String url = _object.getDataFileUrl();
        if (url == null)
            return null;
        try
        {
            return new URI(_object.getDataFileUrl());
        }
        catch (URISyntaxException use)
        {
            return null;
        }
    }

    public File getDataFile()
    {
        return _object.getFile();
    }

    public ExperimentDataHandler findDataHandler()
    {
        return Handler.Priority.findBestHandler(ExperimentServiceImpl.get().getExperimentDataHandlers(), (ExpData)this);
    }

    public String getDataFileUrl()
    {
        return _object.getDataFileUrl();
    }

    public File getFile()
    {
        return _object.getFile();
    }

    public boolean isInlineImage()
    {
        return null != getDataFileUrl() && MIME_MAP.isInlineImageFor(getDataFileUrl());
    }

    public String urlFlag(boolean flagged)
    {
        String ret = null;
        DataType type = getDataType();
        if (type != null)
        {
            ret = type.urlFlag(flagged);
        }
        if (ret != null)
            return ret;
        return super.urlFlag(flagged);
    }

    public void delete(User user)
    {
        ExperimentServiceImpl.get().deleteDataByRowIds(getContainer(), getRowId());
    }
    
    public String getMimeType()
    {
        if (null != getDataFileUrl())
            return MIME_MAP.getContentTypeFor(getDataFileUrl());
        else
            return null;
    }

    public boolean isFileOnDisk()
    {
        File f = getFile();
        return f != null && NetworkDrive.exists(f) && f.isFile();
    }

    public void setDataFileUrl(String s)
    {
        _object.setDataFileUrl(s);
    }

    public String getCpasType()
    {
        String result = _object.getCpasType();
        return result == null ? "Data" : result;
    }

    public void importDataFile(PipelineJob job, XarSource xarSource) throws ExperimentException
    {
        String dataFileURL = getDataFileUrl();

        if (dataFileURL == null)
        {
            return;
        }

        if (xarSource.shouldIgnoreDataFiles())
        {
            job.info("Skipping load of data file " + dataFileURL + " based on the XAR source");
            return;
        }

        try
        {
            job.info("Trying to load data file " + dataFileURL + " into the system");

            File file = new File(new URI(dataFileURL));

            if (!file.exists())
            {
                job.warn("Unable to find the data file " + file.getPath() + " on disk.");
                return;
            }

            // Check that the file is under the pipeline root to prevent users from referencing a file that they
            // don't have permission to import
            PipeRoot pr = PipelineService.get().findPipelineRoot(job.getContainer());
            if (!xarSource.allowImport(pr, job.getContainer(), file))
            {
                if (pr == null)
                {
                    job.warn("No pipeline root was set, skipping load of file " + file.getPath());
                    return;
                }
                job.warn("The data file " + file.getAbsolutePath() + " is not under the folder's pipeline root, " + pr.getUri() + ". It will not be loaded directly, but may be loaded if referenced from other files that are under the pipeline root.");
                return;
            }

            ExperimentDataHandler handler = findDataHandler();
            try
            {
                handler.importFile(this, file, job.getInfo(), job.getLogger(), xarSource.getXarContext());
            }
            catch (ExperimentException e)
            {
                throw new XarFormatException(e);
            }

            job.info("Finished trying to load data file " + dataFileURL + " into the system");
        }
        catch (URISyntaxException e)
        {
            throw new XarFormatException(e);
        }
    }
}
