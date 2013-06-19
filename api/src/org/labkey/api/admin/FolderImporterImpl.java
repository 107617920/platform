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
package org.labkey.api.admin;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: cnathe
 * Date: Apr 16, 2012
 */
public class FolderImporterImpl implements FolderImporter<FolderDocument.Folder>
{
    private Collection<FolderImporter> _importers;
    private PipelineJob _job;
    private boolean _usingVirtualFile = false;

    public FolderImporterImpl()
    {
        this(null);
        _usingVirtualFile = true;
    }

    public FolderImporterImpl(@Nullable PipelineJob job)
    {
        FolderSerializationRegistry registry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null == registry)
        {
            throw new RuntimeException();
        }

        _importers = registry.getRegisteredFolderImporters();

        _job = job;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public void process(@Nullable PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        for (FolderImporter importer : _importers)
        {
            if (!_usingVirtualFile || importer.supportsVirtualFile())
                importer.process(job, ctx, vf);
        }
    }

    @Override
    public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        List<PipelineJobWarning> warnings = new ArrayList<>();
        for (FolderImporter importer : _importers)
        {
            if (!_usingVirtualFile || importer.supportsVirtualFile())
            {
                if (null != _job)
                    _job.setStatus("POST-PROCESS " + importer.getDescription());

                Collection<PipelineJobWarning> importerWarnings = importer.postProcess(ctx, vf);

                if (null != importerWarnings)
                    warnings.addAll(importerWarnings);
            }
        }
        return warnings;
    }

    public void removeImporterByDescription(String removeDesc)
    {
        FolderImporter toRemove = null;
        for (FolderImporter importer : _importers)
        {
            if (importer.getDescription().equals(removeDesc))
            {
                toRemove = importer;
                break;
            }
        }

        if (toRemove != null)
            _importers.remove(toRemove);
    }

    @Override
    public boolean supportsVirtualFile()
    {
        return false;
    }
}
