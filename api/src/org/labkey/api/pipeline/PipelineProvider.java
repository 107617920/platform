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

import org.labkey.api.data.Container;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.module.Module;
import org.springframework.web.servlet.mvc.Controller;

import java.io.*;
import java.util.*;

/**
 */
abstract public class PipelineProvider
{
    public static final String CAPTION_RETRY_BUTTON = "Retry";

    public enum Params { path }

    private boolean _showActionsIfModuleInactive;

    public static abstract class FileEntryFilter implements FileFilter
    {
        private PipelineDirectory _entry;

        public void setFileEntry(PipelineDirectory entry)
        {
            _entry = entry;
        }

        public boolean fileExists(File f)
        {
            if (_entry == null)
                return f.exists();
            return _entry.fileExists(f);
        }
    }

    public static class FileTypesEntryFilter extends FileEntryFilter
    {
        private FileType[] _initialFileTypes;

        public FileTypesEntryFilter(List<FileType> initialFileTypes)
        {
            if (initialFileTypes == null)
            {
                _initialFileTypes = new FileType[0];
            }
            else
            {
                _initialFileTypes = initialFileTypes.toArray(new FileType[initialFileTypes.size()]);
            }
        }
        
        public FileTypesEntryFilter(FileType initialFileType, FileType... otherFileTypes)
        {
            this(appendToArray(otherFileTypes, initialFileType));
        }

        private static FileType[] appendToArray(FileType[] otherFileTypes, FileType initialFileType)
        {
            if (otherFileTypes == null)
            {
                return new FileType[] { initialFileType };
            }

            FileType[] result = new FileType[otherFileTypes.length + 1];
            result[0] = initialFileType;
            System.arraycopy(otherFileTypes, 0, result, 1, otherFileTypes.length);
            return result;
        }

        public FileTypesEntryFilter(FileType[] initialFileTypes)
        {
            _initialFileTypes = initialFileTypes;
        }

        public boolean accept(File f)
        {
            return accept(f, true);
        }

        public boolean accept(File f, boolean checkSiblings)
        {
            if (_initialFileTypes != null)
            {
                for (int i = 0; i < _initialFileTypes.length; i++)
                {
                    FileType ft = _initialFileTypes[i];
                    if (ft.isType(f))
                    {
                        File dir = f.getParentFile();
                        String basename = ft.getBaseName(f);

                        // If any of the preceding types exist, then don't include this one.
                        while (--i >= 0)
                        {
                            if (fileExists(_initialFileTypes[i].newFile(dir, basename)))
                                return false;
                        }

                        int indexMatch = ft.getIndexMatch(f);
                        if (checkSiblings && indexMatch > 0 && ft.isExtensionsMutuallyExclusive())
                        {
                            File[] siblings = dir.listFiles();
                            if (siblings != null)
                            {
                                for (File sibling : siblings)
                                {
                                    if (!sibling.equals(f) &&
                                        ft.getBaseName(sibling).equals(basename) &&
                                        ft.isType(sibling.getName()) &&
                                        ft.getIndexMatch(sibling) < indexMatch)
                                    {
                                        return false;
                                    }
                                }
                            }
                        }

                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static class StatusAction
    {
        String _label;

        public StatusAction(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        public boolean isVisible(PipelineStatusFile statusFile)
        {
            return true;
        }
    }

    protected String _name;
    private final Module _owningModule;

    public PipelineProvider(String name, Module owningModule)
    {
        _name = name;
        _owningModule = owningModule;
    }

    /**
     * Returns a string name associated with this provider, by which it can be
     * retrieved from the <code>PipelineService</code>.
     *
     * @return the name of the provider
     */
    public String getName()
    {
        return _name;
    }

    /**
     * Override to do an work necessary immediately after a new system
     * directory is created in a pipeline root.

     * @param rootDir the pipeline root directory on disk
     * @param systemDir the system directory itself
     */
    public void initSystemDirectory(File rootDir, File systemDir)
    {        
    }

    /**
     * Return true, if the file name should present a link for viewing its
     * contents on the details page for status associated with this provider.
     *
     * @param container the <code>Container</code> for the status entery
     * @param name the file name
     * @param basename the base name associated with the status @return true if link should be displayed
     */
    public boolean isStatusViewableFile(Container container, String name, String basename)
    {
        return PipelineJob.FT_LOG.isMatch(name, basename);
    }

    /**
     * Override to do any extra work necessary before deleting a status entry.
     *
     * @param sf the entry to delete
     */
    public void preDeleteStatusFile(PipelineStatusFile sf)
    {
    }

    public Module getOwningModule()
    {
        return _owningModule;
    }

    /**
     * @return Web part shown on the setup page.
     */
    public HttpView getSetupWebPart(Container container)
    {
        // No setup.
        return null;
    }

    /**
     * Allows the provider to add actions to files in the current directory
     * during pipeline root navigation by the user.
     *
     * @param context The ViewContext for the current request
     * @param pr the <code>PipeRoot</code> object for the current context
     * @param directory directory to scan for possible actions
     * @param includeAll add all actions from this provider even if there are no files of interest in the pipeline directory
     */
    public abstract void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll);

    /**
     * Allows the provider to add action buttons to the details page of one
     * of its status entries.
     *
     * @return List of actions to add to the details page
     */
    public List<StatusAction> addStatusActions()
    {
        List<PipelineProvider.StatusAction> actions = new ArrayList<>();
        actions.add(new PipelineProvider.StatusAction(CAPTION_RETRY_BUTTON)
        {
            @Override
            public boolean isVisible(PipelineStatusFile statusFile)
            {
                // We can retry if the job is in ERROR or CANCELLED and we still have the serialized job info
                return (PipelineJob.ERROR_STATUS.equals(statusFile.getStatus()) ||
                        PipelineJob.CANCELLED_STATUS.equals(statusFile.getStatus())) &&
                        statusFile.getJobStore() != null;
            }
        });
        return actions;
    }

    /**
     * Allows the provider to handle the user clicking on one of the action
     * buttons provided through getStatusActions.
     *
     * @param name The name of the action clicked
     * @param sf   The StatusFile object on which the action is to be performed
     */
    public ActionURL handleStatusAction(ViewContext ctx, String name, PipelineStatusFile sf)
            throws HandlerException
    {
        if (PipelineProvider.CAPTION_RETRY_BUTTON.equals(name))
        {
            if (!PipelineJob.ERROR_STATUS.equals(sf.getStatus()) && !PipelineJob.CANCELLED_STATUS.equals(sf.getStatus()))
            {
                throw new HandlerException("Unable to retry job that is not in the ERROR or CANCELLED state");
            }
            try
            {
                PipelineJobService.get().getJobStore().retry(sf);
            }
            // CONSIDER: Narrow this net further?
            catch (IOException e)
            {
                throw new HandlerException(e);
            }
            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(ctx.getContainer(), sf.getRowId());
        }
        return null;
    }

    /**
     * Local exception type to throw from handleStatusAction.
     */
    public static class HandlerException extends Exception
    {
        public HandlerException(Throwable cause)
        {
            super(cause);
        }

        public HandlerException(String message, Throwable cause)
        {
            super(message, cause);
        }

        public HandlerException(String message)
        {
            super(message);
        }
    }

    protected String createActionId(Class action, String description)
    {
        if (description != null)
            return action.getName() + ':' + description;
        else
            return action.getName();
    }

    protected void addAction(String actionId, URLHelper actionURL, String description, PipelineDirectory entry, File[] files,
                             boolean allowMultiSelect, boolean allowEmptySelect, boolean includeAll)
    {
        if (!includeAll && (files == null || files.length == 0))
            return;

        entry.addAction(new PipelineAction(actionId, description, actionURL, files, allowMultiSelect, allowEmptySelect));
    }

    protected void addAction(String actionId, Class<? extends Controller> action, String description, PipelineDirectory directory, File[] files,
                             boolean allowMultiSelect, boolean allowEmptySelect, boolean includeAll)
    {
        if (!includeAll && (files == null || files.length == 0))
            return;
        ActionURL actionURL = directory.cloneHref();
        actionURL.setAction(action);
//        Uncomment to debug GWT app - can't just edit the URL and reload because it's a POST
//        actionURL.addParameter("gwt.codesvr", "127.0.0.1:9997");
        directory.addAction(new PipelineAction(actionId, description, actionURL, files, allowMultiSelect, allowEmptySelect));
      }

    /**
     * Returns true if a provider wants to show file actions even if the provider module is not active
     * in a container.
     */
    public boolean isShowActionsIfModuleInactive()
    {
        return _showActionsIfModuleInactive;
    }

    protected void setShowActionsIfModuleInactive(boolean showActionsIfModuleInactive)
    {
        _showActionsIfModuleInactive = showActionsIfModuleInactive;
    }

    /**
     * Return true if this provider believes that it is in use in the container, and
     * also can handle overlapping pipeline roots.
     * A pipeline provider should not return "true" without first checking that it is active (i.e. that the
     * current container's folder type is one that makes use of this pipeline provider.
     * Many modules (e.g. MS2) have trouble with overlapping pipeline roots.  It is important that in MS2 folders
     * the user be shown the warning.
     */
    public boolean suppressOverlappingRootsWarning(ViewContext context)
    {
        return false;
    }

    /** Calculate the available set of actions */
    protected List<PipelineActionConfig> getDefaultActionConfig()
    {
        return Collections.emptyList();
    }

    /** Check if this provider should add actions based on the enabled modules, and then calculate the available set */
    public List<PipelineActionConfig> getDefaultActionConfig(Container container)
    {
        if (_showActionsIfModuleInactive || container.getActiveModules().contains(getOwningModule()))
        {
            return getDefaultActionConfig();
        }
        return Collections.emptyList();
    }
}
