/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

package org.labkey.filecontent;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.files.UnsetRootDirectoryException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.view.HttpView;
import org.labkey.api.webdav.WebdavResource;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Dec 9, 2009
 */
public class FileContentServiceImpl implements FileContentService, ContainerManager.ContainerListener
{
    static Logger _log = Logger.getLogger(FileContentServiceImpl.class);
    private static final String UPLOAD_LOG = ".upload.log";

    enum Props {
        root,
        rootDisabled,
    }

    enum FileAction
    {
        UPLOAD,
        DELETE
    }

    public FileContentServiceImpl()
    {
        ContainerManager.addContainerListener(this);
    }

    public File getFileRoot(Container c)
    {
        if (c == null)
            return null;

        Container project = c.getProject();
        if (null == project)
            return null;

        FileRoot root = FileRootManager.get().getFileRoot(project);
        if (root.isEnabled())
        {
            // check if there is a site wide file root
            if (root.getPath() == null || root.isUseDefault())
            {
                return getProjectDefaultRoot(c, true);
            }
            else
                return new File(root.getPath());
        }
        return null;
    }

    public File getProjectDefaultRoot(Container c, boolean createDir)
    {
        File siteRoot = getSiteDefaultRoot();
        if (siteRoot != null && c != null && c.getProject() != null)
        {
            File projRoot = new File(siteRoot, c.getProject().getName());

            if (!projRoot.exists() && createDir)
                projRoot.mkdirs();

            return projRoot;
        }
        return null;
    }

    public void setFileRoot(Container c, File path)
    {
        if (!c.isProject())
            throw new IllegalArgumentException("File roots are only currently supported at the project level");
        
        FileRoot root = FileRootManager.get().getFileRoot(c);
        root.setEnabled(true);

        String oldValue = root.getPath();
        String newValue = null;

        try {
            // clear out the root
            if (path == null)
                root.setPath(null);
            else
            {
                root.setPath(path.getCanonicalPath());
                newValue = root.getPath();
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        FileRootManager.get().saveFileRoot(null, root);
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                c, ContainerManager.Property.WebRoot, oldValue, newValue);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    public void disableFileRoot(Container container)
    {
        if (container == null || container.isRoot())
            throw new IllegalArgumentException("Disabling either a null project or the root project is not allowed.");

        Container project = container.getProject();
        if (project != null)
        {
            FileRoot root = FileRootManager.get().getFileRoot(project);
            String oldValue = root.getPath();
            root.setEnabled(false);
            FileRootManager.get().saveFileRoot(null, root);

            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    container, ContainerManager.Property.WebRoot, oldValue, null);
            ContainerManager.firePropertyChangeEvent(evt);
        }
    }

    public boolean isFileRootDisabled(Container c)
    {
        if (c == null || c.isRoot())
            throw new IllegalArgumentException("The file root of either a null project or the root project cannot be disabled.");

        Container project = c.getProject();
        if (null == project)
            return false;

        FileRoot root = FileRootManager.get().getFileRoot(project);
        return !root.isEnabled();
    }

    public boolean isUseDefaultRoot(Container c)
    {
        if (c == null)
            return true;
        
        Container project = c.getProject();
        if (null == project)
            return true;

        FileRoot root = FileRootManager.get().getFileRoot(project);
        return root.isUseDefault() || StringUtils.isEmpty(root.getPath());
    }

    public void setIsUseDefaultRoot(Container c, boolean useDefaultRoot)
    {
        Container project = c.getProject();
        if (project != null)
        {
            FileRoot root = FileRootManager.get().getFileRoot(project);
            String oldValue = root.getPath();
            root.setEnabled(true);
            root.setUseDefault(useDefaultRoot);
            FileRootManager.get().saveFileRoot(null, root);

            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    project, ContainerManager.Property.WebRoot, oldValue, null);
            ContainerManager.firePropertyChangeEvent(evt);
        }
    }

    public File getSiteDefaultRoot()
    {
        File root = AppProps.getInstance().getFileSystemRoot();

        if (root == null || !root.exists())
            root = getDefaultRoot();

        if (root != null && !root.exists())
            root.mkdirs();

        return root;
    }

    private File getDefaultRoot()
    {
        File explodedPath = ModuleLoader.getInstance().getCoreModule().getExplodedPath();

        File root = explodedPath.getParentFile();
        if (root != null)
        {
            if (root.getParentFile() != null)
                root = root.getParentFile();
        }
        File defaultRoot = new File(root, "files");
        if (!defaultRoot.exists())
            defaultRoot.mkdirs();

        return defaultRoot;
    }

    public void setSiteDefaultRoot(File root)
    {
        if (root == null || !root.exists())
            throw new IllegalArgumentException("Invalid site root: does not exist");
        
        try {
            WriteableAppProps props = AppProps.getWriteableInstance();

            props.setFileSystemRoot(root.getAbsolutePath());
            props.save();

            FileRootManager.get().clearCache();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public FileSystemAttachmentParent registerDirectory(Container c, String name, String path, boolean relative)
    {
        FileSystemAttachmentParent parent = new FileSystemAttachmentParent();
        parent.setContainer(c);
        if (null == name)
            name = path;
        parent.setName(name);
        parent.setPath(path);
        parent.setRelative(relative);
        //We do this because insert does not return new fields
        parent.setEntityid(GUID.makeGUID());

        try
        {
            FileSystemAttachmentParent ret = Table.insert(HttpView.currentContext().getUser(), CoreSchema.getInstance().getMappedDirectories(), parent);
            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    c, ContainerManager.Property.AttachmentDirectory, null, ret);
            ContainerManager.firePropertyChangeEvent(evt);
            return ret;
        } catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void unregisterDirectory(Container c, String name)
    {
        FileSystemAttachmentParent parent = getRegisteredDirectory(c, name);
        SimpleFilter filter = new SimpleFilter("Container", c);
        filter.addCondition("Name", name);
        try
        {
            Table.delete(CoreSchema.getInstance().getMappedDirectories(), filter);
            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    c, ContainerManager.Property.AttachmentDirectory, parent, null);
            ContainerManager.firePropertyChangeEvent(evt);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public AttachmentDirectory getMappedAttachmentDirectory(Container c, boolean createDir) throws UnsetRootDirectoryException, MissingRootDirectoryException
    {
        if (createDir) //force create
            getMappedDirectory(c, true);
        else if (null == getMappedDirectory(c, false))
            return null;

        return new FileSystemAttachmentParent(c, ContentType.files);
    }

    File getMappedDirectory(Container c, boolean create) throws UnsetRootDirectoryException, MissingRootDirectoryException
    {
        File root = getFileRoot(c);
        if (null == root)
        {
            if (create)
                throw new UnsetRootDirectoryException(c.isRoot() ? c : c.getProject());
            else
                return null;
        }

        if (!root.exists())
        {
            if (create)
                throw new MissingRootDirectoryException(c.isRoot() ? c : c.getProject(), root);
            else
                return null;
        }

        File dir;
        //Don't want the Project part of the path.
        if (c.isProject())
            dir = root;
        else
        {
            //Cut off the project name
            String extraPath = c.getPath();
            extraPath = extraPath.substring(c.getProject().getName().length() + 2);
            dir = new File(root, extraPath);
        }

        if (!dir.exists() && create)
            dir.mkdirs();

        return dir;
    }

    public FileSystemAttachmentParent getRegisteredDirectory(Container c, String name)
    {
        SimpleFilter filter = new SimpleFilter("Container", c);
        filter.addCondition("Name", name);
        try
        {
            return Table.selectObject(CoreSchema.getInstance().getMappedDirectories(), filter, null, FileSystemAttachmentParent.class);
        } catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public FileSystemAttachmentParent getRegisteredDirectoryFromEntityId(Container c, String entityId)
    {
        SimpleFilter filter = new SimpleFilter("Container", c);
        filter.addCondition("EntityId", entityId);
        try
        {
            return Table.selectObject(CoreSchema.getInstance().getMappedDirectories(), filter, null, FileSystemAttachmentParent.class);
        } catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public FileSystemAttachmentParent[] getRegisteredDirectories(Container c)
    {
        SimpleFilter filter = new SimpleFilter("Container", c);
        try
        {
            return Table.select(CoreSchema.getInstance().getMappedDirectories(), Table.ALL_COLUMNS, filter, null, FileSystemAttachmentParent.class);
        } catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void containerCreated(Container c, User user)
    {
        try
        {
            File dir = getMappedDirectory(c, false);
            //Don't try to create dir if root not configured.
            //But if we should have a directory, create it
            if (null != dir && !dir.exists())
                getMappedDirectory(c, true);
        }
        catch (MissingRootDirectoryException ex)
        {
            /* */
        }
    }

    public void containerDeleted(Container c, User user)
    {
        File dir = null;
        try
        {
            // don't delete the file contents if they have a project override
            if (isUseDefaultRoot(c))
                dir = getMappedDirectory(c, false);
        }
        catch (Exception e)
        {
            _log.error("containerDeleted", e);
        }

        if (null != dir && dir.exists())
        {
            moveToDeleted(dir);
        }

        try
        {
            ContainerUtil.purgeTable(CoreSchema.getInstance().getMappedDirectories(), c, null);
        }
        catch (SQLException x)
        {
            _log.error("Purging attachments", x);
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {               
    }

    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
        ContainerManager.ContainerPropertyChangeEvent evt = (ContainerManager.ContainerPropertyChangeEvent)propertyChangeEvent;
        Container c = evt.container;

        switch (evt.property)
        {
            case Name:
            {
                //We don't rename webroots for a project. They are set manually per-project
                //If we move to a site-wide webroot option, projects below that root should be renamed
                if (c.isProject())
                    return;

                String oldValue = (String) propertyChangeEvent.getOldValue();
                String newValue = (String) propertyChangeEvent.getNewValue();

                File location = null;
                try
                {
                    location = getMappedDirectory(c, false);
                }
                catch (MissingRootDirectoryException ex)
                {
                    /* */
                }
                if (location == null)
                    return;
                //Don't rely on container object. Seems not to point to the
                //new location even AFTER rename. Just construct new file paths
                File parentDir = location.getParentFile();
                File oldLocation = new File(parentDir, oldValue);
                File newLocation = new File(parentDir, newValue);
                if (newLocation.exists())
                    moveToDeleted(newLocation);

                if (oldLocation.exists())
                    oldLocation.renameTo(newLocation);
                break;
            }
            case Parent:
            {
                Container oldParent = (Container) propertyChangeEvent.getOldValue();
                File oldParentFile = null;
                try
                {
                    oldParentFile = getMappedDirectory(oldParent, false);
                }
                catch (MissingRootDirectoryException ex)
                {
                    /* */
                }
                if (null == oldParentFile)
                    return;
                File oldDir = new File(oldParentFile, c.getName());
                if (!oldDir.exists())
                    return;

                File newDir = null;
                try
                {
                    newDir = getMappedDirectory(c, false);
                }
                catch (MissingRootDirectoryException ex)
                {
                }
                //Move stray content out of the way
                if (null != newDir && newDir.exists())
                   moveToDeleted(newDir);

                oldDir.renameTo(newDir);
                break;
            }
        }
    }

    public AttachmentParent[] getNamedAttachmentDirectories(Container c) throws SQLException
    {
        return Table.select(CoreSchema.getInstance().getMappedDirectories(), Table.ALL_COLUMNS, new SimpleFilter("Container", c), null, FileSystemAttachmentParent.class);
    }

    public @Nullable String getFolderName(FileContentService.ContentType type)
    {
        if (type != null)
            return "@" + type.name();
        return null;
    }

    static void moveToDeleted(File fileToMove)
    {
        if (!fileToMove.exists())
            return;
        File parent = fileToMove.getParentFile();

        File deletedDir = new File(parent, ".deleted");
        if (!deletedDir.exists())
            deletedDir.mkdir();

        File newLocation = new File(deletedDir, fileToMove.getName());
        if (newLocation.exists())
            recursiveDelete(newLocation);

        fileToMove.renameTo(newLocation);
    }

    static void recursiveDelete(File file)
    {
        if (file.isDirectory())
        {
            File[] files = file.listFiles();
            if (null != files)
                for (File child : files)
                    recursiveDelete(child);
        }

        file.delete();
    }

    static void logFileAction(File directory, String fileName, FileAction action, User user)
    {
        FileWriter fw = null;
        try
        {
            fw = new FileWriter(new File(directory, UPLOAD_LOG), true);
            fw.write(action.toString()  + "\t" + fileName + "\t" + new Date() + "\t" + (user == null ? "(unknown)" : user.getEmail()) + "\n");
        }
        catch (Exception x)
        {
            //Just log it.
            _log.error(x);
        }
        finally
        {
            if (null != fw)
            {
                try
                {
                    fw.close();
                }
                catch (Exception x)
                {

                }
            }
        }
    }

    public FilesAdminOptions getAdminOptions(Container c)
    {
        FileRoot root = FileRootManager.get().getFileRoot(c);
        String xml = null;

        if (!StringUtils.isBlank(root.getProperties()))
        {
            xml = root.getProperties();
        }
        return new FilesAdminOptions(c, xml);
    }

    public void setAdminOptions(Container c, FilesAdminOptions options)
    {
        if (options != null)
        {
            FileRoot root = FileRootManager.get().getFileRoot(c);

            root.setProperties(options.serialize());
            FileRootManager.get().saveFileRoot(null, root);
        }
    }

    public static final String NAMESPACE_PREFIX = "FileProperties";
    public static final String PROPERTIES_DOMAIN = "File Properties";
    public static final String TYPE_PROPERTIES = "FileProperties";

    public String getDomainURI(Container container)
    {
        return getDomainURI(container, getAdminOptions(container).getFileConfig());
    }

    public String getDomainURI(Container container, FilesAdminOptions.fileConfig config)
    {
        while (config == FilesAdminOptions.fileConfig.useParent && container != container.getParent())
        {
            container = container.getParent();
            config = getAdminOptions(container).getFileConfig();
        }

        //String typeURI = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":List" + ".Folder-" + container.getRowId() + ":" + name;

        return new Lsid("urn:lsid:labkey.com:" + NAMESPACE_PREFIX + ".Folder-" + container.getRowId() + ':' + TYPE_PROPERTIES).toString();
    }

    public ExpData getDataObject(WebdavResource resource, Container c)
    {
        return getDataObject(resource, c, null, false);
    }

    public static ExpData getDataObject(WebdavResource resource, Container c, User user, boolean create)
    {
        if (resource != null)
        {
            File file = resource.getFile();
            ExpData data = ExperimentService.get().getExpDataByURL(file, c);

            if (data == null && create)
            {
                data = ExperimentService.get().createData(c, new DataType("UploadedFile"));
                data.setName(file.getName());
                data.setDataFileURI(file.toURI());
                data.save(user);
            }
            return data;
        }
        return null;
    }

    @Override
    public QueryUpdateService getFilePropsUpdateService(TableInfo tinfo, Container container)
    {
        return new FileQueryUpdateService(tinfo, container);
    }
}
