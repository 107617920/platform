/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Folder type that results in an old style "tabbed" folder.
 */
public class CustomFolderType implements FolderType
{
    public CustomFolderType(){}
    public void configureContainer(Container c) {  }
    public void unconfigureContainer(Container c) {  }
    public String getName() { return "None"; }
    protected boolean forceAssayUploadIntoWorkbooks = false;

    public String getDescription()
    {
        return "Create a tab for each LabKey module you select. Used in older LabKey installations. Note that any LabKey module can also be enabled in any folder type via Folder Settings.";
    }

    public List<Portal.WebPart> getRequiredWebParts()
    {
        return Collections.emptyList();
    }

    public List<Portal.WebPart> getPreferredWebParts()
    {
        return Collections.emptyList();
    }

    public String getLabel() { return "Custom"; }
    public Module getDefaultModule() { return null; }
    public Set<Module> getActiveModules() { return Collections.emptySet(); }
    public String getStartPageLabel(ViewContext ctx) { return null; }
    public ActionURL getStartURL(Container c, User u)
    {
        if (null == c)
            return AppProps.getInstance().getHomePageActionURL();
        if (null == c.getDefaultModule())
            return PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
        return c.getDefaultModule().getTabURL(c, u);
    }
    public HelpTopic getHelpTopic() { return null; }

    public void addManageLinks(NavTree adminNavTree, Container container)
    {
        DefaultFolderType.addStandardManageLinks(adminNavTree, container);
    }

    @Override
    public List<FolderTab> getDefaultTabs()
    {
        throw new UnsupportedOperationException();
    }

    @NotNull
    public AppBar getAppBar(ViewContext context, PageConfig pageConfig)
    {
        List<NavTree> tabs = new ArrayList<NavTree>();

        Container container = context.getContainer();
        if (!container.isRoot())
        {

            Set<Module> containerModules = container.getActiveModules();
            Module activeModule = pageConfig.getModuleOwner();
            String currentPageflow = context.getActionURL().getPageFlow();
            if (activeModule == null)
            {
                activeModule = ModuleLoader.getInstance().getModuleForController(currentPageflow);
            }

            assert activeModule != null : "Pageflow '" + currentPageflow + "' is not claimed by any module.  " +
                    "This pageflow name must be added to the list of names returned by 'getPageFlowNameToClass' " +
                    "from at least one module.";
            List<Module> moduleList = getSortedModuleList();
            for (Module module : moduleList)
            {
                boolean selected = (module == activeModule);
                if (selected || (containerModules.contains(module)
                        && null != module.getTabURL(container, context.getUser())))
                {
                    NavTree navTree = new NavTree(module.getTabName(context), module.getTabURL(context.getContainer(), context.getUser()));
                    navTree.setSelected(selected);
                    tabs.add(navTree);
                }
            }
        }
        else if (context.getUser().isAdministrator())
        {
            tabs.add(new NavTree("Admin Console", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()));
        }

        return new AppBar(container.getName(), container.getStartURL(context.getUser()), tabs);
    }

    @NotNull
    public String getFolderIconPath()
    {
        return "_images/defaultlogosmall.gif";
    }

    public boolean getForceAssayUploadIntoWorkbooks()
    {
        return forceAssayUploadIntoWorkbooks;
    }

    public void setForceAssayUploadIntoWorkbooks(boolean forceAssayUploadIntoWorkbooks)
    {
        this.forceAssayUploadIntoWorkbooks = forceAssayUploadIntoWorkbooks;
    }

    private List<Module> getSortedModuleList()
    {
        List<Module> sortedModuleList = new ArrayList<Module>();
        // special-case the portal module: we want it to always be at the far left.
        Module portal = null;
        for (Module module : ModuleLoader.getInstance().getModules())
        {
            if ("Portal".equals(module.getName()))
                portal = module;
            else
                sortedModuleList.add(module);
        }
        Collections.sort(sortedModuleList, new Comparator<Module>()
        {
            public int compare(Module moduleOne, Module moduleTwo)
            {
                return moduleOne.getName().compareToIgnoreCase(moduleTwo.getName());
            }
        });
        if (portal != null)
            sortedModuleList.add(0, portal);

        return sortedModuleList;
    }


    @Override
    public boolean isWorkbookType()
    {
        return false;
    }

    @Override
    public boolean hasConfigurableTabs()
    {
        return false;
    }

    @Override
    public Portal.WebPart[] resetDefaultTabs(Container c)
    {
        throw new UnsupportedOperationException();
    }
}
