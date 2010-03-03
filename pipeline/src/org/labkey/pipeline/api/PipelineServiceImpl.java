/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.pipeline.PipelineController;
import org.labkey.pipeline.mule.EPipelineQueueImpl;
import org.labkey.pipeline.mule.ResumableDescriptor;
import org.mule.MuleManager;
import org.mule.umo.UMODescriptor;
import org.mule.umo.UMOException;
import org.mule.umo.model.UMOModel;
import org.springframework.validation.BindException;

import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

public class PipelineServiceImpl extends PipelineService
{
    public static String PARAM_Provider = "provider";
    public static String PARAM_Action = "action";
    public static String PROP_Mirror = "mirror-containers";
    public static String PREF_LASTPATH = "lastpath";
    public static String PREF_LASTPROTOCOL = "lastprotocol";
    public static String PREF_LASTSEQUENCEDB = "lastsequencedb";
    public static String PREF_LASTSEQUENCEDBPATHS = "lastsequencedbpaths";
    public static String KEY_PREFERENCES = "pipelinePreferences";

    private static Logger _log = Logger.getLogger(PipelineService.class);

    private Map<String, PipelineProvider> _mapPipelineProviders = new TreeMap<String, PipelineProvider>();
    private PipelineQueue _queue = null;

    public static PipelineServiceImpl get()
    {
        return (PipelineServiceImpl) PipelineService.get();
    }

    public void registerPipelineProvider(PipelineProvider provider, String... aliases)
    {
        _mapPipelineProviders.put(provider.getName(), provider);
        for (String alias : aliases)
            _mapPipelineProviders.put(alias, provider);
    }

    public GlobusKeyPair createGlobusKeyPair(byte[] keyBytes, String keyPassword, byte[] certBytes)
    {
        return new GlobusKeyPairImpl(keyBytes, keyPassword, certBytes);
    }


    public PipeRoot findPipelineRoot(Container container)
    {
        PipelineRoot pipelineRoot = PipelineManager.findPipelineRoot(container);
        if (null != pipelineRoot)
        {
            try
            {
                return new PipeRootImpl(pipelineRoot);
            }
            catch (URISyntaxException x)
            {
                _log.error("unexpected error", x);
            }
        }

        // if we haven't found a 'real' root, default to a root off the site wide default
        return getDefaultPipelineRoot(container, PipelineRoot.PRIMARY_ROOT);
    }

    /**
     * Try to locate a default pipeline root from the site file root. Default pipeline roots only
     * extend to the project level and are inherited by sub folders.
     *
     * @param container
     * @return
     */
    private PipeRoot getDefaultPipelineRoot(Container container, String type)
    {
        try {
            if (PipelineRoot.PRIMARY_ROOT.equals(type))
            {
                FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
                if (svc != null && container != null)
                {
                    if (svc.isUseDefaultRoot(container.getProject()))
                    {
                        File root = svc.getFileRoot(container.getProject());
                        if (root != null)
                        {
                            AttachmentDirectory dir = svc.getMappedAttachmentDirectory(container, true);
                            return createDefaultRoot(container.getProject(), dir.getFileSystemDirectory(), true);
                        }
                    }
                    else
                    {
                        File root = svc.getProjectDefaultRoot(container, true);
                        if (root != null)
                        {
                            File dir = new File(root, svc.getFolderName(FileContentService.ContentType.files));
                            if (!dir.exists())
                                dir.mkdirs();
                            return createDefaultRoot(container, dir, false);
                        }
                    }
                }
            }
        }
        catch (MissingRootDirectoryException e)
        {
            return null;
        }
        catch (Exception e)
        {
            _log.error("unexpected error", e);
        }
        return null;
    }

    private PipeRoot createDefaultRoot(Container container, File dir, boolean sameAsFilesRoot) throws URISyntaxException
    {
        PipelineRoot p = new PipelineRoot();

        p.setContainer(container.getId());
        p.setPath(dir.toURI().toString());
        p.setType(PipelineRoot.PRIMARY_ROOT);

        return new PipeRootImpl(p, sameAsFilesRoot);
    }

    public boolean hasSiteDefaultRoot(Container container)
    {
        PipelineRoot pipelineRoot = PipelineManager.findPipelineRoot(container);

        if (pipelineRoot == null)
            return getDefaultPipelineRoot(container, PipelineRoot.PRIMARY_ROOT) != null;
        return false;
    }

    @Override
    public boolean hasValidPipelineRoot(Container container)
    {
        PipelineService service = PipelineService.get();
        URI uriRoot = null;
        PipeRoot pr = service.findPipelineRoot(container);
        if (pr != null)
        {
            uriRoot = pr.getUri();
            if (uriRoot != null)
            {
                File f = new File(uriRoot);
                if (NetworkDrive.exists(f) && f.isDirectory())
                {
                    return true;
                }
            }
        }
        return false;
    }


    @NotNull
    public Map<Container, PipeRoot> getAllPipelineRoots()
    {
        PipelineRoot[] pipelines;
        try
        {
            pipelines = PipelineManager.getPipelineRoots(PipelineRoot.PRIMARY_ROOT);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        Map<Container, PipeRoot> result = new HashMap<Container, PipeRoot>();
        for (PipelineRoot pipeline : pipelines)
        {
            try
            {
                PipeRoot p = new PipeRootImpl(pipeline);
                if (p.getContainer() != null)
                    result.put(p.getContainer(), p);
            }
            catch (URISyntaxException x)
            {
                _log.error("unexpected error", x);
            }
        }
        return result;
    }


    public PipeRoot getPipelineRootSetting(Container container)
    {
        try
        {
            PipelineRoot r = PipelineManager.getPipelineRootObject(container, PipelineRoot.PRIMARY_ROOT);
            if (null == r)
            {
                if (container != null)
                    return getDefaultPipelineRoot(container, PipelineRoot.PRIMARY_ROOT);
                return null;
            }
            return new PipeRootImpl(r);
        }
        catch (URISyntaxException x)
        {
            _log.error("unexpected error", x);
        }

        return null;
    }



    public URI getPipelineRootSetting(Container container, final String type)
    {
        String root = PipelineManager.getPipelineRoot(container, type);
        if (root == null)
        {
            if (container != null)
            {
                PipeRoot pipeRoot = getDefaultPipelineRoot(container, type);
                if (pipeRoot != null)
                    return pipeRoot.getUri();
            }
            return null;
        }

        try
        {
            return new URI(root);
        }
        catch (URISyntaxException use)
        {
            _log.error("Invalid pipeline root '" + root + "'.", use);
            return null;
        }
    }

    @NotNull
    public PipeRoot[] getOverlappingRoots(Container c) throws SQLException
    {
        PipelineRoot[] roots = PipelineManager.getOverlappingRoots(c, PipelineRoot.PRIMARY_ROOT);
        List<PipeRoot> rootsList = new ArrayList<PipeRoot>();
        for (PipelineRoot root : roots)
        {
            Container container = ContainerManager.getForId(root.getContainerId());
            if (container == null)
                continue;

            try
            {
                rootsList.add(new PipeRootImpl(root));
            }
            catch (URISyntaxException e)
            {
                _log.error("Invalid pipeline root '" + root + "'.", e);
            }
        }
        return rootsList.toArray(new PipeRoot[rootsList.size()]);
    }

    public void setPipelineRoot(User user, Container container, URI root, String type,
                                GlobusKeyPair globusKeyPair, boolean searchable) throws SQLException
    {
        if (!canModifyPipelineRoot(user, container))
            throw new UnauthorizedException("You do not have sufficient permissions to set the pipeline root");
        
        PipelineManager.setPipelineRoot(user, container, root == null ? "" : root.toString(), type,
                globusKeyPair, searchable);
    }

    public boolean canModifyPipelineRoot(User user, Container container)
    {
        //per Britt--user must be site admin
        return container != null && !container.isRoot() && user.isAdministrator();
    }

    @NotNull
    public File ensureSystemDirectory(URI root)
    {
        return PipeRootImpl.ensureSystemDirectory(root);
    }

    @NotNull
    public List<PipelineProvider> getPipelineProviders()
    {
        // Get a list of unique providers
        return new ArrayList<PipelineProvider>(new HashSet<PipelineProvider>(_mapPipelineProviders.values()));
    }

    @Nullable
    public PipelineProvider getPipelineProvider(String name)
    {
        if (name == null)
            return null;
        return _mapPipelineProviders.get(name);
    }

    public String getButtonHtml(String text, ActionURL href)
    {
        return PageFlowUtil.generateButton(text, href);
    }

    public boolean isEnterprisePipeline()
    {
        return (getPipelineQueue() instanceof EPipelineQueueImpl);
    }

    @NotNull
    public synchronized PipelineQueue getPipelineQueue()
    {
        if (_queue == null)
        {
            ConnectionFactory factory = null;
            try
            {
                Context initCtx = new InitialContext();
                Context env = (Context) initCtx.lookup("java:comp/env");
                factory = (ConnectionFactory) env.lookup("jms/ConnectionFactory");
            }
            catch (NamingException e)
            {
            }

            if (factory == null)
                _queue = new PipelineQueueImpl();
            else
            {
                _log.info("Found JMS queue; running Enterprise Pipeline.");
                _queue = new EPipelineQueueImpl(factory);
            }
        }
        return _queue;
    }

    public void queueJob(PipelineJob job) throws IOException
    {
        getPipelineQueue().addJob(job);
    }

    public void setPipelineProperty(Container container, String name, String value) throws SQLException
    {
        PipelineManager.setPipelineProperty(container, name, value);
    }

    public String getPipelineProperty(Container container, String name) throws SQLException
    {
        return PipelineManager.getPipelineProperty(container, name);
    }

    public HttpView getSetupView(SetupForm form)
    {
        if (form.getErrors() != null)
            return new JspView<SetupForm>("/org/labkey/pipeline/setup.jsp", form, form.getErrors());
        else
            return new JspView<SetupForm>("/org/labkey/pipeline/setup.jsp", form);
    }

    public boolean savePipelineSetup(ViewContext context, SetupForm form, BindException errors) throws Exception
    {
        return PipelineController.savePipelineSetup(context, form, errors);
    }

    private String getLastProtocolKey(PipelineProtocolFactory factory)
    {
        return PREF_LASTPROTOCOL + "-" + factory.getName();
    }

    // TODO: This should be on PipelineProtocolFactory
    public String getLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        try
        {
            Map<String, String> props = PropertyManager.getProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES);
            String lastProtocolkey = props.get(getLastProtocolKey(factory));
            if (lastProtocolkey != null)
                return lastProtocolkey;
        }
        catch (Exception e)
        {
            _log.error("Error", e);
        }
        return "";
    }

    // TODO: This should be on PipelineProtocolFactory
    public void rememberLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user,
                                            String protocolName)
    {
        if (user.isGuest())
            return;
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES, true);
        map.put(getLastProtocolKey(factory), protocolName);
        PropertyManager.saveProperties(map);
    }


    public String getLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        try
        {
            Map<String, String> props = PropertyManager.getProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES);
            String lastSequenceDbSetting = props.get(PipelineServiceImpl.PREF_LASTSEQUENCEDB + "-" + factory.getName());
            if (lastSequenceDbSetting != null)
                return props.get(PipelineServiceImpl.PREF_LASTSEQUENCEDB + "-" + factory.getName());
        }
        catch (Exception e)
        {
            _log.error("Error", e);
        }
        return "";
    }

    public void rememberLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user,
                                              String sequenceDbPath,String sequenceDb)
    {
        if (user.isGuest())
            return;
        if (sequenceDbPath == null || sequenceDbPath.equals("/")) 
            sequenceDbPath = "";
        String fullPath = sequenceDbPath + sequenceDb;
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user.getUserId(), container.getId(),
                PipelineServiceImpl.KEY_PREFERENCES, true);
        map.put(PipelineServiceImpl.PREF_LASTSEQUENCEDB + "-" + factory.getName(), fullPath);
        PropertyManager.saveProperties(map);
    }

    public List<String> getLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        try
        {
            Map<String, String> props = PropertyManager.getProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES);
            String dbPaths = props.get(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName());

            if (null != dbPaths)
                return parseArray(dbPaths);
        }
        catch (Exception e)
        {
            _log.error("Error", e);
        }
        return null;
    }

    public void rememberLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user,
                                                   List<String> sequenceDbPathsList)
    {
        if (user.isGuest())
            return;
        String sequenceDbPathsString = list2String(sequenceDbPathsList);
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user.getUserId(), container.getId(),
                PipelineServiceImpl.KEY_PREFERENCES, true);
        if(sequenceDbPathsString == null || sequenceDbPathsString.length() == 0 || sequenceDbPathsString.length() >= 2000)
        {
            map.remove(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName());
        }
        else
        {
            map.put(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName(), sequenceDbPathsString);
        }
        PropertyManager.saveProperties(map);
    }



    public PipelineStatusFile getStatusFile(String path) throws SQLException
    {
        return PipelineStatusManager.getStatusFile(path);
    }

    public PipelineStatusFile[] getQueuedStatusFiles() throws SQLException
    {
        return PipelineStatusManager.getQueuedStatusFiles();
    }

    public PipelineStatusFile[] getQueuedStatusFiles(Container c) throws SQLException
    {
        return PipelineStatusManager.getQueuedStatusFilesForContainer(c);
    }

    public void setStatusFile(PipelineJob job, String status, String statusInfo) throws Exception
    {
        PipelineStatusManager.setStatusFile(job, new PipelineStatusFileImpl(job, status, statusInfo));
    }

    public void ensureError(PipelineJob job) throws Exception
    {
        PipelineStatusManager.ensureError(job);
    }

    private List<String> parseArray(String dbPaths)
    {
        if(dbPaths == null) return null;
        if(dbPaths.length() == 0) return new ArrayList<String>();
        String[] tokens = dbPaths.split("\\|");
        return new ArrayList<String>(Arrays.asList(tokens));
    }

    private String list2String(List<String> sequenceDbPathsList)
    {
        if(sequenceDbPathsList == null) return null;
        StringBuilder temp = new StringBuilder();
        for(String path:sequenceDbPathsList)
        {
            if(temp.length() > 0)
                temp.append("|");
            temp.append(path);
        }
        return temp.toString();
    }

    /**
     * Recheck the status of the jobs that may or may not have been started already
     */
    public void refreshLocalJobs()
    {
        // Spin through the Mule config to be sure that we get all the different descriptors that
        // have been registered
        for (UMOModel model : (Collection<UMOModel>) MuleManager.getInstance().getModels().values())
        {
            for (Iterator<String> i = model.getComponentNames(); i.hasNext(); )
            {
                String name = i.next();
                UMODescriptor descriptor = model.getDescriptor(name);

                try
                {
                    Class c = descriptor.getImplementationClass();
                    if (ResumableDescriptor.class.isAssignableFrom(c))
                    {
                        ResumableDescriptor resumable = ((Class<ResumableDescriptor>)c).newInstance();
                        resumable.resume(descriptor);
                    }
                }
                catch (UMOException e)
                {
                    _log.error("Failed to get implementation class from descriptor " + descriptor, e);
                }
                catch (IllegalAccessException e)
                {
                    _log.error("Failed to resume jobs for descriptor " + descriptor, e);
                }
                catch (InstantiationException e)
                {
                    _log.error("Failed to resume jobs for descriptor " + descriptor, e);
                }
            }
        }
    }
}
