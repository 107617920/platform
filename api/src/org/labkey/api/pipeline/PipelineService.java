/*
 * Copyright (c) 2005-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.io.File;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 */
abstract public class PipelineService
        implements PipelineStatusFile.StatusReader, PipelineStatusFile.StatusWriter
{
    public static final String MODULE_NAME = "Pipeline";

    static PipelineService instance;

    public static PipelineService get()
    {
        return instance;
    }

    static public void setInstance(PipelineService instance)
    {
        PipelineService.instance = instance;
    }

    abstract public void registerPipelineProvider(PipelineProvider provider, String... aliases);

    @Nullable
    abstract public PipeRoot findPipelineRoot(Container container);

    /** @return true if this container (or an inherited parent container) has a pipeline root that exists on disk */
    abstract public boolean hasValidPipelineRoot(Container container);

    @NotNull
    abstract public Map<Container, PipeRoot> getAllPipelineRoots();

    @Nullable
    abstract public PipeRoot getPipelineRootSetting(Container container);

    @Nullable
    abstract public PipeRoot getPipelineRootSetting(Container container, String type);

    abstract public void setPipelineRoot(User user, Container container, String type, GlobusKeyPair globusKeyPair, boolean searchable, URI... roots) throws SQLException;

    abstract public boolean canModifyPipelineRoot(User user, Container container);

    @NotNull
    abstract public List<PipelineProvider> getPipelineProviders();

    @Nullable
    abstract public PipelineProvider getPipelineProvider(String name);

    abstract public boolean isEnterprisePipeline();

    @NotNull
    abstract public PipelineQueue getPipelineQueue();

    abstract public void queueJob(PipelineJob job);

    abstract public void setPipelineProperty(Container container, String name, String value) throws SQLException;

    abstract public String getPipelineProperty(Container container, String name) throws SQLException;

    /** Configurations for the pipeline job webpart ButtonBar */
    public enum PipelineButtonOption { Minimal, Assay, Standard }

    abstract public QueryView getPipelineQueryView(ViewContext context, PipelineButtonOption buttonOption);

    abstract public HttpView getSetupView(SetupForm form);

    abstract public boolean savePipelineSetup(ViewContext context, SetupForm form, BindException errors) throws Exception;

    // TODO: This should be on PipelineProtocolFactory
    abstract public String getLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user);

    // TODO: This should be on PipelineProtocolFactory
    abstract public void rememberLastProtocolSetting(PipelineProtocolFactory factory, Container container,
                                                     User user, String protocolName);

    abstract public String getLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user);

    abstract public void rememberLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user,
                                                       String sequenceDbPath, String sequenceDb);

    abstract public List<String> getLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user);

    abstract public void rememberLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container,
                                                            User user, List<String> sequenceDbPaths);

    abstract public boolean hasSiteDefaultRoot(Container container);

    abstract public boolean importFolder(ViewContext context, BindException errors, File folderFile, String originalFilename) throws Exception;
}
