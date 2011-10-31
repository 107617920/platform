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

package org.labkey.api.files.view;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileUrls;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavService;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Jul 9, 2007
 * Time: 2:13:18 PM
 */
public class FilesWebPart extends JspView<FilesWebPart.FilesForm>
{
    public static final String PART_NAME = "Files";
    private static final Logger _log = Logger.getLogger(FilesWebPart.class);

    private boolean wide = true;
    private boolean showAdmin = false;
    private String fileSet;
    private Container container;
    private boolean _isPipelineFiles;       // viewing @pipeline files

    private static final String JSP = "/org/labkey/api/files/view/fileContent.jsp";
    private static final String JSP_RIGHT = "/org/labkey/filecontent/view/files.jsp";


    public FilesWebPart(Container c)
    {
        super(JSP);
        container = c;
        setModelBean(new FilesForm());
        setFileSet(null);
        setTitle(PART_NAME);
        setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c));

        init();
    }

    protected void init()
    {
        createConfig();
    }

    protected FilesWebPart(Container c, String fileSet)
    {
        this(c);

        if (fileSet != null)
        {
            if (fileSet.equals(FileContentService.PIPELINE_LINK))
            {
                _isPipelineFiles = true;
                PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
                if (root != null)
                {
                    getModelBean().setRootPath(root.getWebdavURL());
                    getModelBean().setRootDirectory(root.getRootPath());
                }
                setTitle("Pipeline Files");
            }
            else
            {
                FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
                AttachmentDirectory dir = svc.getRegisteredDirectory(c, fileSet);

                if (dir != null)
                {
                    try
                    {
                        getModelBean().setRoot(dir);
                        getModelBean().setRootDirectory(dir.getFileSystemDirectory());
                    }
                    catch (MissingRootDirectoryException e)
                    {
                        // this should never happen
                        throw new RuntimeException(e);
                    }
                }
                getModelBean().setRootPath(getRootPath(c, FileContentService.FILE_SETS_LINK, fileSet));
                setTitle(fileSet);
                setFileSet(fileSet);
                setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c).addParameter("fileSetName", fileSet));
            }
        }
        init();
    }

    protected FilesForm createConfig()
    {
        FilesForm form = getModelBean();
        ViewContext context = getViewContext();

        if (form == null)
        {
            form = new FilesForm();
            setModelBean(form);
        }
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);

        form.setShowAddressBar(true);
        form.setShowDetails(false);
        form.setShowFolderTree(true);
        form.setFolderTreeCollapsed(true);

        if (form.getRootPath() == null)
            form.setRootPath(getRootPath(getRootContext().getContainer(), FileContentService.FILES_LINK));

        form.setEnabled(!svc.isFileRootDisabled(getRootContext().getContainer()));
        form.setContentId("fileContent" + System.identityHashCode(this));

        List<FilesForm.actions> actions = new ArrayList<FilesForm.actions>();

        // Navigation actions
        actions.add(FilesForm.actions.folderTreeToggle);
        actions.add(FilesForm.actions.parentFolder);
        actions.add(FilesForm.actions.refresh);

        // Actions not based on the current selection
        SecurityPolicy policy = SecurityManager.getPolicy(getSecurableResource());
        if (policy.hasPermission(getViewContext().getUser(), InsertPermission.class))
            actions.add(FilesForm.actions.createDirectory);

        // Actions based on the current selection
        actions.add(FilesForm.actions.download);
        if (policy.hasPermission(getViewContext().getUser(), DeletePermission.class))
        {
            actions.add(FilesForm.actions.deletePath);
        }

        if (policy.hasPermission(getViewContext().getUser(), InsertPermission.class))
        {
            actions.add(FilesForm.actions.editFileProps);
            actions.add(FilesForm.actions.upload);

            FilesAdminOptions options = svc.getAdminOptions(context.getContainer());
            boolean expandUpload = BooleanUtils.toBooleanDefaultIfNull(options.getExpandFileUpload(), true);

            form.setExpandFileUpload(expandUpload);
        }

        if (canDisplayPipelineActions())
        {
            form.setPipelineRoot(true);
            if (context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            {
                actions.add(FilesForm.actions.importData);
            }
        }
        // users can manage their email notification settings
        if (!context.getUser().isGuest())
            actions.add(FilesForm.actions.emailPreferences);

        if (context.getContainer().hasPermission(context.getUser(), AdminPermission.class))
        {
            actions.add(FilesForm.actions.auditLog);
            actions.add(FilesForm.actions.customize);
        }

        form.setButtonConfig(actions.toArray(new FilesForm.actions[actions.size()]));

        return form;
    }

    public FilesWebPart(ViewContext ctx, Portal.WebPart webPartDescriptor)
    {
        this(ctx.getContainer(), StringUtils.trimToNull(webPartDescriptor.getPropertyMap().get("fileSet")));

        CustomizeFilesWebPartView.CustomizeWebPartForm form = new CustomizeFilesWebPartView.CustomizeWebPartForm(webPartDescriptor);
        getModelBean().setFolderTreeCollapsed(!form.isFolderTreeVisible());

        setWide(null == webPartDescriptor.getLocation() || HttpView.BODY.equals(webPartDescriptor.getLocation()));
        setShowAdmin(container.hasPermission(ctx.getUser(), AdminPermission.class));

        // apply any other client specified overrides
        FilesForm bean = getModelBean();
        if (bean != null)
        {
            try
            {
                BeanUtils.copyProperties(bean, ctx);
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
            catch (InvocationTargetException e)
            {
                throw new RuntimeException(e);
            }
        }

        if (!isWide())
        {
            _path = JSP_RIGHT;
            _page = JspLoader.createPage((String)null, _path);
        }
    }

    @Override
    public String getLocation()
    {
        if (!this.isWide())
            return WebPartFactory.LOCATION_RIGHT;
        return WebPartFactory.LOCATION_BODY;
    }

    public static String getRootPath(Container c, String davName)
    {
        return getRootPath(c, davName, null);
    }

    public static String getRootPath(Container c, String davName, String fileset)
    {
        String webdavPrefix = AppProps.getInstance().getContextPath() + "/" + WebdavService.getServletPath();
        String rootPath;

        if (davName != null)
        {
            if (fileset != null)
                rootPath = webdavPrefix + c.getEncodedPath() + URLEncoder.encode(davName) + "/" + fileset;
            else
                rootPath = webdavPrefix + c.getEncodedPath() + URLEncoder.encode(davName);
        }
        else
            rootPath = webdavPrefix + c.getEncodedPath();

        if (!rootPath.endsWith("/"))
            rootPath += "/";

        return rootPath;
    }

    protected boolean canDisplayPipelineActions()
    {
        try {
            if (_isPipelineFiles)
                return true;

            // since pipeline actions operate on the pipeline root, if the file content and pipeline roots do not
            // reference the same location, then import and customize actions should be disabled

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            AttachmentDirectory dir = svc.getMappedAttachmentDirectory(getViewContext().getContainer(), false);
            PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());

            if (null != root && null != dir && root.getRootPath().equals(dir.getFileSystemDirectory()))
            {
                return true;
            }
        }
        catch (MissingRootDirectoryException e)
        {
            _log.error("Error determining whether pipeline actions can be shown", e);
        }
        return false;
    }

    protected SecurableResource getSecurableResource()
    {
        return getViewContext().getContainer();
    }

    public boolean isWide()
    {
        return wide;
    }

    public void setWide(boolean wide)
    {
        this.wide = wide;
    }

    public boolean isShowAdmin()
    {
        return showAdmin;
    }

    public void setShowAdmin(boolean showAdmin)
    {
        this.showAdmin = showAdmin;
    }

    public String getFileSet()
    {
        return fileSet;
    }

    public void setFileSet(String fileSet)
    {
        this.fileSet = fileSet;
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        try {
            AttachmentDirectory dir;
            if (null == fileSet)
                dir = svc.getMappedAttachmentDirectory(container, false);
            else
                dir = svc.getRegisteredDirectory(container, fileSet);

            if (dir != null)
            {
                getModelBean().setRoot(dir);
                getModelBean().setRootDirectory(dir.getFileSystemDirectory());
            }
        }
        catch (MissingRootDirectoryException ex)
        {
            setModelBean(null);
        }
    }

    public static class Factory extends AlwaysAvailableWebPartFactory
    {
        public Factory(String location)
        {
            super(PART_NAME, location, true, false);
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new FilesWebPart(portalCtx, webPart);
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
        {
            return new CustomizeFilesWebPartView(webPart);
        }
    }

    public static class FilesForm
    {
        private AttachmentDirectory _root;
        private boolean _showAddressBar;
        private boolean _showFolderTree;
        private boolean _folderTreeCollapsed;
        private boolean _showDetails;
        private boolean _autoResize;
        private boolean _enabled;
        private actions[] _buttonConfig;
        private String _rootPath;
        private Path _directory;
        private String _contentId;
        private boolean _isPipelineRoot;
        private String _statePrefix;
        private File _rootDirectory;
        private boolean _expandFileUpload;
        private boolean _disableGeneralAdminSettings;

        public enum actions {
            download,
            deletePath,
            refresh,
            uploadTool,
            configure,
            createDirectory,
            parentFolder,
            upload,
            importData,
            customize,
            folderTreeToggle,
            editFileProps,
            emailPreferences,
            auditLog,
        }

        public boolean isAutoResize()
        {
            return _autoResize;
        }

        public void setAutoResize(boolean autoResize)
        {
            _autoResize = autoResize;
        }

        public AttachmentDirectory getRoot()
        {
            return _root;
        }

        public void setRoot(AttachmentDirectory root)
        {
            _root = root;
        }

        public boolean isShowAddressBar()
        {
            return _showAddressBar;
        }

        public void setShowAddressBar(boolean showAddressBar)
        {
            _showAddressBar = showAddressBar;
        }

        public boolean isShowFolderTree()
        {
            return _showFolderTree;
        }

        public void setShowFolderTree(boolean showFolderTree)
        {
            _showFolderTree = showFolderTree;
        }

        public boolean isShowDetails()
        {
            return _showDetails;
        }

        public void setShowDetails(boolean showDetails)
        {
            _showDetails = showDetails;
        }

        public actions[] getButtonConfig()
        {
            return _buttonConfig;
        }

        public void setButtonConfig(actions[] buttonConfig)
        {
            _buttonConfig = buttonConfig;
        }

        public String getRootPath()
        {
            return _rootPath;
        }

        public void setRootPath(String rootPath)
        {
            _rootPath = rootPath;
        }

        public void setDirectory(Path path)
        {
            _directory = path;
        }

        public Path getDirectory()
        {
            return _directory;
        }

        public boolean isEnabled()
        {
            return _enabled;
        }

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }

        public String getContentId()
        {
            return _contentId;
        }

        public void setContentId(String contentId)
        {
            _contentId = contentId;
        }

        public boolean isPipelineRoot()
        {
            return _isPipelineRoot;
        }

        public void setPipelineRoot(boolean pipelineRoot)
        {
            _isPipelineRoot = pipelineRoot;
        }

        public boolean isFolderTreeCollapsed()
        {
            return _folderTreeCollapsed;
        }

        public void setFolderTreeCollapsed(boolean folderTreeCollapsed)
        {
            _folderTreeCollapsed = folderTreeCollapsed;
        }

        public String getStatePrefix()
        {
            return _statePrefix;
        }

        public void setStatePrefix(String statePrefix)
        {
            _statePrefix = statePrefix;
        }

        public boolean isRootValid()
        {
            return (_rootDirectory != null && _rootDirectory.exists());
        }

        public File getRootDirectory()
        {
            return _rootDirectory;
        }

        public void setRootDirectory(File rootDirectory)
        {
            _rootDirectory = rootDirectory;
        }

        public boolean isExpandFileUpload()
        {
            return _expandFileUpload;
        }

        public void setExpandFileUpload(boolean expandFileUpload)
        {
            _expandFileUpload = expandFileUpload;
        }

        public boolean isDisableGeneralAdminSettings()
        {
            return _disableGeneralAdminSettings;
        }

        public void setDisableGeneralAdminSettings(boolean disableGeneralAdminSettings)
        {
            _disableGeneralAdminSettings = disableGeneralAdminSettings;
        }
    }
}
