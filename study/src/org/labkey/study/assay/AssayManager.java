/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.study.assay;

import gwt.client.org.labkey.study.StudyApplication;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.*;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.study.assay.query.AssayListPortalView;
import org.labkey.study.assay.query.AssayListQueryView;
import org.labkey.study.assay.query.AssaySchemaImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.view.StudyGWTView;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jun 22, 2007
 * Time: 4:21:59 PM
 */
public class AssayManager implements AssayService.Interface
{
    private List<AssayProvider> _providers = new ArrayList<AssayProvider>();
    /** Synchronization lock object for ensuring that batch names are unique */
    private static final Object BATCH_NAME_LOCK = new Object();

    public AssayManager()
    {
    }

    public static synchronized AssayManager get()
    {
        return (AssayManager) AssayService.get();
    }

    public ExpProtocol createAssayDefinition(User user, Container container, GWTProtocol newProtocol)
            throws ExperimentException
    {
        return getProvider(newProtocol.getProviderName()).createAssayDefinition(user, container, newProtocol.getName(),
                newProtocol.getDescription());
    }

    public void registerAssayProvider(AssayProvider provider)
    {
        // Blow up if we've already added a provider with this name
        if (getProvider(provider.getName()) != null)
        {
            throw new IllegalArgumentException("A provider with the name " + provider.getName() + " has already been registered");
        }
        _providers.add(provider);
        PipelineProvider pipelineProvider = provider.getPipelineProvider();
        if (pipelineProvider != null)
        {
            PipelineService.get().registerPipelineProvider(pipelineProvider);
        }
    }

    public AssayProvider getProvider(String providerName)
    {
        for (AssayProvider potential : _providers)
        {
            if (potential.getName().equals(providerName))
            {
                return potential;
            }
        }
        return null;
    }

    public AssayProvider getProvider(ExpProtocol protocol)
    {
        return Handler.Priority.findBestHandler(_providers, protocol);
    }

    @Override
    public AssayProvider getProvider(ExpRun run)
    {
        ExpProtocol protocol = run.getProtocol();
        if (protocol == null)
        {
            return null;
        }
        return getProvider(protocol);
    }

    public List<AssayProvider> getAssayProviders()
    {
        return Collections.unmodifiableList(_providers);
    }

    public ExpRunTable createRunTable(ExpProtocol protocol, AssayProvider provider, User user, Container container)
    {
        return (ExpRunTable)new AssaySchemaImpl(user, container).getTable(getRunsTableName(protocol));
    }

    public AssaySchema createSchema(User user, Container container)
    {
        return new AssaySchemaImpl(user, container);
    }

    public String getBatchesTableName(ExpProtocol protocol)
    {
        return AssaySchemaImpl.getBatchesTableName(protocol);
    }

    public String getRunsTableName(ExpProtocol protocol)
    {
        return AssaySchemaImpl.getRunsTableName(protocol);
    }

    public String getResultsTableName(ExpProtocol protocol)
    {
        return AssaySchemaImpl.getResultsTableName(protocol);
    }

    public List<ExpProtocol> getAssayProtocols(Container container)
    {
        // Build up a set of containers so that we can query them all at once
        Set<Container> containers = new HashSet<Container>();
        containers.add(container);
        containers.add(ContainerManager.getSharedContainer());
        Container project = container.getProject();
        if (project != null)
        {
            containers.add(project);
        }
        if (container.isWorkbook())
        {
            containers.add(container.getParent());
        }

        ExpProtocol[] protocols = ExperimentService.get().getExpProtocols(containers.toArray(new Container[containers.size()]));
        List<ExpProtocol> result = new ArrayList<ExpProtocol>();

        // Filter to just the ones that have an AssayProvider associated with them
        for (ExpProtocol protocol : protocols)
        {
            if (AssayService.get().getProvider(protocol) != null)
                result.add(protocol);
        }

        return result;
    }

    public WebPartView createAssayListView(ViewContext context, boolean portalView)
    {
        String name = "AssayList";
        UserSchema schema = AssayService.get().createSchema(context.getUser(), context.getContainer());
        QuerySettings settings = schema.getSettings(context, name, name);
        QueryView queryView;
        if (portalView)
            queryView = new AssayListPortalView(context, settings);
        else
            queryView = new AssayListQueryView(context, settings);

        VBox vbox = new VBox();
        if (portalView)
            vbox.setFrame(WebPartView.FrameType.PORTAL);
        vbox.addView(new JspView("/org/labkey/study/assay/view/assaySetup.jsp"));
        vbox.addView(queryView);
        return vbox;
    }

    @Override
    public ModelAndView createAssayDesignerView(Map<String, String> properties)
    {
        return new StudyGWTView(StudyApplication.GWTModule.AssayDesigner, properties);
    }

    @Override
    public ModelAndView createAssayImportView(Map<String, String> properties)
    {
        return new StudyGWTView(StudyApplication.GWTModule.AssayImporter, properties);
    }

    @Override
    public ModelAndView createListChooserView(Map<String, String> properties)
    {
        GWTView listChooser = new StudyGWTView(StudyApplication.GWTModule.ListChooser, properties);
        listChooser.getModelBean().getProperties().put("pageFlow", "assay");
        return listChooser;
    }

    public List<ActionButton> getImportButtons(ExpProtocol protocol, User user, Container currentContainer, boolean isStudyView)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        assert provider != null : "Could not find a provider for protocol: " + protocol;

        // First find all the containers that have contributed data to this protocol
        Set<Container> containers = protocol.getExpRunContainers();

        Container protocolContainer = protocol.getContainer();

        // Always add the current container if we're looking at an assay and under the protocol
        if (!isStudyView &&
                (currentContainer.equals(protocolContainer) ||
                currentContainer.hasAncestor(protocolContainer) ||
                protocolContainer.equals(ContainerManager.getSharedContainer())))
            containers.add(currentContainer);


        // Check for write permission
        for (Iterator<Container> iter = containers.iterator(); iter.hasNext();)
        {
            Container container = iter.next();
            boolean hasPermission = container.hasPermission(user, InsertPermission.class);
            boolean hasPipeline = PipelineService.get().hasValidPipelineRoot(container);
            if (!hasPermission || !hasPipeline)
            {
                iter.remove();
            }
        }
        if (containers.size() == 0)
            return Collections.emptyList(); // Nowhere to upload to, no button

        List<ActionButton> result = new ArrayList<ActionButton>();

        if (containers.size() == 1 && containers.iterator().next().equals(currentContainer))
        {
            // Create one import button for each provider, using the current container
            ActionButton button = new ActionButton(provider.getImportURL(currentContainer, protocol), AbstractAssayProvider.IMPORT_DATA_LINK_NAME);
            button.setActionType(ActionButton.Action.LINK);
            result.add(button);
        }
        else
        {
            // It's not just the current container, so fall through to show a submenu even if there's
            // only one item, in order to indicate that the user is going to be redirected elsewhere
            MenuButton uploadButton = new MenuButton(AbstractAssayProvider.IMPORT_DATA_LINK_NAME);
            // If the current folder is in our list, put it first.
            if (containers.contains(currentContainer))
            {
                containers.remove(currentContainer);
                ActionURL url = provider.getImportURL(currentContainer, protocol);
                if (currentContainer.isWorkbook())
                {
                    uploadButton.addMenuItem("Current Workbook (" + currentContainer.getTitle() + ")", url);
                }
                else
                {
                    uploadButton.addMenuItem("Current Folder (" + currentContainer.getPath() + ")", url);
                }
            }
            for(Container container : containers)
            {
                ActionURL url = provider.getImportURL(container, protocol);
                uploadButton.addMenuItem(container.getPath(), url);
            }
            result.add(uploadButton);
        }

        return result;
    }

    public ExpExperiment createStandardBatch(Container container, String name, ExpProtocol protocol)
    {
        if (name == null)
        {
            name = DateUtil.formatDate() + " batch";
        }
        ExpExperiment batch = ExperimentService.get().createExpExperiment(container, name);
        // Make sure that our LSID is unique using a GUID.
        // Outside the main transaction, we'll separately give it a uinque name
        batch.setLSID(ExperimentService.get().generateLSID(container, ExpExperiment.class, GUID.makeGUID()));
        batch.setBatchProtocol(protocol);

        return batch;
    }

    public ExpExperiment ensureUniqueBatchName(ExpExperiment batch, ExpProtocol protocol, User user)
    {
        synchronized (BATCH_NAME_LOCK)
        {
            int suffix = 1;
            String originalName = batch.getName();
            ExpExperiment[] batches = ExperimentService.get().getExperiments(batch.getContainer(), user, false, true);
            while (batches.length > 1)
            {
                batch.setName(originalName + " " + (++suffix));
                batch.save(user);
                batches = ExperimentService.get().getMatchingBatches(batch.getName(), batch.getContainer(), protocol);
            }

            return batches[0];
        }
    }

    public ExpExperiment findBatch(ExpRun run)
    {
        int protocolId = run.getProtocol().getRowId();
        for (ExpExperiment potentialBatch : run.getExperiments())
        {
            ExpProtocol batchProtocol = potentialBatch.getBatchProtocol();
            if (batchProtocol != null && batchProtocol.getRowId() == protocolId)
            {
                return potentialBatch;
            }
        }
        return null;
    }

    public void indexAssays(SearchService.IndexTask task, Container c)
    {
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);

        if (null == ss)
            return;

        List<ExpProtocol> protocols = getAssayProtocols(c);

        for (ExpProtocol protocol : protocols)
        {
            AssayProvider provider = getProvider(protocol);

            if (null == provider)
                continue;

            ExpRun[] runs = ExperimentService.get().getExpRuns(c, protocol, null);

            if (0 == runs.length)
                continue;

            StringBuilder runKeywords = new StringBuilder();

            for (ExpRun run : runs)
            {
                runKeywords.append(" ");
                runKeywords.append(run.getName());

                if (null != run.getComments())
                {
                    runKeywords.append(" ");
                    runKeywords.append(run.getComments());
                }
            }

            String name = protocol.getName();
            String instrument = protocol.getInstrument();
            String description = protocol.getDescription();
            String comment = protocol.getComment();

            ActionURL assayRunsURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(c, protocol);

            String searchTitle = StringUtils.trimToEmpty(name) + " " + StringUtils.trimToEmpty(instrument) + " " + StringUtils.trimToEmpty(provider.getName());
            String body = StringUtils.trimToEmpty(provider.getName()) + " " + StringUtils.trimToEmpty(description) + " " + StringUtils.trimToEmpty(comment) + runKeywords.toString();
            Map<String, Object> m = new HashMap<String, Object>();
            m.put(SearchService.PROPERTY.displayTitle.toString(), name);
            m.put(SearchService.PROPERTY.searchTitle.toString(), searchTitle);
            m.put(SearchService.PROPERTY.categories.toString(), StudyManager.assayCategory.getName());

            String docId = "assay:" + c.getId() + ":" + protocol.getRowId();
            assayRunsURL.setExtraPath(c.getId());
            WebdavResource r = new SimpleDocumentResource(new Path(docId), docId, c.getId(), "text/plain", body.getBytes(), assayRunsURL, m);
            task.addResource(r, SearchService.PRIORITY.item);
        }
    }

    @Override
    /** Recurse through the container tree, upgrading any assay protocols that live there */
    public void upgradeAssayDefinitions(User user, double targetVersion)
    {
        upgradeAssayDefinitions(user, ContainerManager.getRoot(), targetVersion);
    }

    private void upgradeAssayDefinitions(User user, Container c, double targetVersion)
    {
        try
        {
            for (ExpProtocol protocol : ExperimentService.get().getExpProtocols(c))
            {
                AssayProvider provider = AssayManager.get().getProvider(protocol);
                if (provider != null)
                {
                    // Upgrade is AssayProvider dependent
                    provider.upgradeAssayDefinitions(user, protocol, targetVersion);
                }
            }

            // Recurse through the children
            for (Container child : c.getChildren())
            {
                upgradeAssayDefinitions(user, child, targetVersion);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpRun createExperimentRun(@Nullable String name, Container container, ExpProtocol protocol, @Nullable File file)
    {
        if (name == null)
        {
            // Check if we have a file to use
            if (file == null || !file.isFile())
            {
                name = "[Untitled]";
            }
            else
            {
                name = file.getName();
            }
        }

        String entityId = GUID.makeGUID();
        ExpRun run = ExperimentService.get().createExperimentRun(container, name);

        Lsid lsid = new Lsid(getProvider(protocol).getRunLSIDPrefix(), "Folder-" + container.getRowId(), entityId);
        run.setLSID(lsid.toString());
        run.setProtocol(ExperimentService.get().getExpProtocol(protocol.getRowId()));
        run.setEntityId(entityId);

        File runRoot;
        if (file == null)
        {
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(container);
            if (pipeRoot == null)
            {
                throw new NotFoundException("Pipeline root is not configured for folder " + container);
            }
            runRoot = pipeRoot.getRootPath();
        }
        else if (file.isFile())
        {
            runRoot = file.getParentFile();
        }
        else
        {
            runRoot = file;
        }
        run.setFilePathRoot(runRoot);

        return run;
    }
}
