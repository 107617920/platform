/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.data.*;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.UnsetRootDirectoryException;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.view.HttpView;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.sql.SQLException;
import java.util.Map;
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

    enum ContentType {
        files,
        pipeline,
        assay,
    }

    public File getFileRoot(Container c)
    {
        if (c == null)
            return null;

        Container project = c.getProject();
        if (null == project)
            return null;

        Map<String,String> m = PropertyManager.getProperties(project.getId(), "staticFile", false);
        if (m == null || !m.containsKey(Props.root.name()))
        {
            // check for file sharing disabled at the project level
            if (m != null && m.containsKey(Props.rootDisabled.name()))
                return null;

            // check if there is a site wide file root
            File siteRoot = getSiteDefaultRoot();
            if (siteRoot != null)
            {
                File projRoot = new File(siteRoot, c.getProject().getName());

                // automatically create project roots if a site wide root is specified
                if (!projRoot.exists())
                    projRoot.mkdirs();

                return projRoot;
            }
            return null;
        }
        return null == m.get(Props.root.name()) ? null : new File(m.get(Props.root.name()));
    }

    public void setFileRoot(Container c, File root)
    {
        Map<String,String> m = PropertyManager.getWritableProperties(0, c.getProject().getId(), "staticFile", true);
        String oldValue = m.get(Props.root.name());
        try
        {
            m.remove(Props.rootDisabled.name());
            if (null == root)
                m.remove(Props.root.name());
            else
                m.put(Props.root.name(), root.getCanonicalPath());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        String newValue = m.get(Props.root.name());
        PropertyManager.saveProperties(m);

        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                c, ContainerManager.Property.WebRoot, oldValue, newValue);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    public void disableFileRoot(Container container)
    {
        if (container == null || container.isRoot())
            throw new IllegalArgumentException("Disabling either a null project or the root project is not allowed.");

        Map<String,String> m = PropertyManager.getWritableProperties(0, container.getProject().getId(), "staticFile", true);
        String oldValue = m.get(Props.root.name());
        m.remove(Props.root.name());
        m.put(Props.rootDisabled.name(), Boolean.toString(true));

        PropertyManager.saveProperties(m);

        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                container, ContainerManager.Property.WebRoot, oldValue, null);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    public boolean isFileRootDisabled(Container c)
    {
        if (c == null || c.isRoot())
            throw new IllegalArgumentException("The file root of either a null project or the root project cannot be disabled.");

        Container project = c.getProject();
        if (null == project)
            return false;

        Map<String,String> m = PropertyManager.getProperties(project.getId(), "staticFile", false);
        return m != null && m.containsKey(Props.rootDisabled.name());
    }

    public boolean hasSiteDefaultRoot(Container c)
    {
        Container project = c.getProject();
        if (null == project)
            return true;

        Map<String,String> m = PropertyManager.getProperties(project.getId(), "staticFile", false);
        return m == null || !m.containsKey(Props.root.name());
    }

    public File getSiteDefaultRoot()
    {
        return AppProps.getInstance().getFileSystemRoot();
    }

    public void setSiteDefaultRoot(File root)
    {
        if (root == null || !root.exists())
            throw new IllegalArgumentException("Invalid site root: does not exist");
        
        try {
            WriteableAppProps props = AppProps.getWriteableInstance();

            props.setFileSystemRoot(root.getAbsolutePath());
            props.save();
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

        FileSystemAttachmentParent parent;
        parent = new FileSystemAttachmentParent(c, ContentType.files);
        return parent;
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

    public void containerCreated(Container c)
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

}
