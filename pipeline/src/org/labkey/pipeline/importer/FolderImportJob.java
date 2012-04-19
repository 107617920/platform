/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.pipeline.importer;

import org.apache.log4j.Logger;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.validation.BindException;

import java.io.File;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderImportJob extends PipelineJob implements FolderJobSupport
{
    private static final Logger LOG = Logger.getLogger(FolderImportJob.class);

    private final FolderImportContext _ctx;
    private final File _root;
    private final String _originalFilename;
    private final BindException _errors;

    public FolderImportJob(Container c, User user, ActionURL url, File folderXml, String originalFilename, BindException errors, PipeRoot pipeRoot)
    {
        super(null, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _root = folderXml.getParentFile();
        _originalFilename = originalFilename;
        _errors = errors;
        setLogFile(FolderImportProvider.logForInputFile(new File(_root, "folder_load")));
        _ctx = new FolderImportContext(user, c, folderXml, getLogger(), _root);

        LOG.info("Pipeline job initialized for importing folder properties to folder " + c.getPath());
    }

    public FolderImportContext getImportContext()
    {
        return _ctx;
    }

    public File getRoot()
    {
        return _root;
    }

    public String getOriginalFilename()
    {
        return _originalFilename;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(FolderImportJob.class));
    }

    @Override
    public URLHelper getStatusHref()
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getInfo().getContainer());
    }

    public String getDescription()
    {
        return "Folder import";
    }
}