/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.view.template.PageConfig;

import java.sql.SQLException;
import java.util.*;

/**
 * User: Mark Igra
 * Date: Aug 2, 2006
 * Time: 8:44:04 PM
 */
public class DefaultFolderType implements FolderType
{
    protected List<WebPart> requiredParts;
    protected List<WebPart> preferredParts;
    protected Set<Module> activeModules;
    protected String description;
    protected String name;
    protected Module defaultModule;
    protected boolean workbookType = false;
    protected String folderIconPath = "_icons/icon_folder2.png";
    protected boolean forceAssayUploadIntoWorkbooks = false;

    public DefaultFolderType(String name, String description)
    {
        this.name = name;
        this.description = description;
    }

    public DefaultFolderType(String name, String description, @Nullable List<Portal.WebPart> requiredParts, @Nullable List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule)
    {
        this.name = name;
        this.description = description;
        this.requiredParts = requiredParts == null ? Collections.<WebPart>emptyList() : requiredParts;
        this.preferredParts = preferredParts == null ? Collections.<WebPart>emptyList() : preferredParts;
        this.activeModules = activeModules;
        this.defaultModule = defaultModule;
    }

    public DefaultFolderType(String name, String description, List<Portal.WebPart> requiredParts, List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule, boolean forceAssayUploadIntoWorkbooks, String folderIconPath)
    {
        this.name = name;
        this.description = description;
        this.requiredParts = requiredParts == null ? Collections.<WebPart>emptyList() : requiredParts;
        this.preferredParts = preferredParts == null ? Collections.<WebPart>emptyList() : preferredParts;
        this.activeModules = activeModules;
        this.defaultModule = defaultModule;
        this.forceAssayUploadIntoWorkbooks = forceAssayUploadIntoWorkbooks;
        this.folderIconPath = folderIconPath;
    }

    @Override
    public List<FolderTab> getDefaultTabs()
    {
        FolderTab tab = new FolderTab("DefaultDashboard")
        {
            @Override
            public boolean isSelectedPage(ViewContext viewContext)
            {
                return true;
            }

            @Override
            public ActionURL getURL(ViewContext context)
            {
                return getStartURL(context.getContainer(), context.getUser());
            }
        };
        return Collections.singletonList(tab);
    }

    public void configureContainer(Container c)
    {
        List<Portal.WebPart> required = getRequiredWebParts();
        List<Portal.WebPart> defaultParts = getPreferredWebParts();

        //Just to be sure, make sure required web parts are set correctly
        if (null != required)
            for (Portal.WebPart part : required)
                part.setPermanent(true);

        ArrayList<Portal.WebPart> all = new ArrayList<Portal.WebPart>();

        try
        {
            Portal.WebPart[] existingParts = Portal.getPartsOld(c);
            if (null == existingParts || existingParts.length == 0)
            {
                if (null != required)
                    all.addAll(required);
                if (null != defaultParts)
                    all.addAll(defaultParts);
            }
            else
            {
                //Order will be required,preferred,optional
                all.addAll(Arrays.asList(existingParts));
                for (WebPart p : all)
                    p.setIndex(2);

                if (null != required)
                    for (Portal.WebPart part: required)
                    {
                        Portal.WebPart foundPart = findPart(all, part);
                        if (null != foundPart)
                        {
                            foundPart.setPermanent(true);
                            foundPart.setIndex(0);
                        }
                        else
                        {
                            part.setIndex(0);
                            all.add(part);
                        }
                    }

                if (null != defaultParts)
                    for (Portal.WebPart part: defaultParts)
                    {
                        Portal.WebPart foundPart = findPart(all, part);
                        if (null == foundPart)
                        {
                            part.setIndex(1); //Should put these right after required parts
                            all.add(part);
                        }
                        else
                            foundPart.setIndex(1);
                    }
            }

            Set<Module> active = c.getActiveModules(false);
            Set<Module> requiredActive = getActiveModules();

            if (null == active)
                active = new HashSet<Module>();
            else
                active = new HashSet<Module>(active); //Need to copy since returned set is unmodifiable.

            active.addAll(requiredActive);
            c.setActiveModules(active);
            Portal.saveParts(c, all);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (hasConfigurableTabs())
        {
            resetDefaultTabs(c);
        }
        for (FolderTab folderTab : getDefaultTabs())
        {
            folderTab.initializeContent(c);
        }
    }

    public List<Portal.WebPart> resetDefaultTabs(Container c)
    {
        List<Portal.WebPart> tabs = new ArrayList<Portal.WebPart>();

        for (FolderTab p : getDefaultTabs())
        {
            Portal.WebPart tab = new Portal.WebPart();
            tab.setLocation(FolderTab.LOCATION);
            tab.setName(p.getName());
            tabs.add(tab);
        }

        Portal.saveParts(c, FolderTab.FOLDER_TAB_PAGE_ID, tabs);
        return Portal.getParts(c, FolderTab.FOLDER_TAB_PAGE_ID);
    }


    public void unconfigureContainer(Container c)
    {
        List<WebPart> parts = Portal.getParts(c);

        if (null != parts)
        {
            boolean saveRequired = false;

            for (WebPart part : parts)
            {
                if (part.isPermanent())
                {
                    part.setPermanent(false);
                    saveRequired = true;
                }
            }

            if (saveRequired)
                Portal.saveParts(c, parts);
        }
    }

    @NotNull
    public String getFolderIconPath()
    {
        return folderIconPath;
    }

    public void setFolderIconPath(String folderIconPath)
    {
        this.folderIconPath = folderIconPath;
    }

    /**
     * Find a web part. Don't use strict equality, just name and location
     * @return matchingPart
     */
    private Portal.WebPart findPart(List<Portal.WebPart> parts, Portal.WebPart partToFind)
    {
        String location = partToFind.getLocation();
        String name = partToFind.getName();
        for (Portal.WebPart part : parts)
            if (name.equals(part.getName()) && location.equals(part.getLocation()))
                return part;

        return null;
    }

    public boolean getForceAssayUploadIntoWorkbooks()
    {
        return forceAssayUploadIntoWorkbooks;
    }

    public void setForceAssayUploadIntoWorkbooks(boolean forceAssayUploadIntoWorkbooks)
    {
        this.forceAssayUploadIntoWorkbooks = forceAssayUploadIntoWorkbooks;
    }

    public ActionURL getStartURL(Container c, User user)
    {
        return ModuleLoader.getInstance().getModule("Portal").getTabURL(c, user);
    }

    public String getStartPageLabel(ViewContext ctx)
    {
        return getLabel() + " Dashboard";
    }

    public HelpTopic getHelpTopic()
    {
        return null;
    }

    public Module getDefaultModule()
    {
        return defaultModule;
    }

    public List<Portal.WebPart> getRequiredWebParts()
    {
        return requiredParts;
    }

    public List<Portal.WebPart> getPreferredWebParts()
    {
        return preferredParts;
    }

    public String getName()
    {
        return name;
    }

    @NotNull
    @Override
    public Set<String> getLegacyNames()
    {
        return Collections.emptySet();
    }

    @Override
    public boolean hasConfigurableTabs()
    {
        return false;
    }

    public String getDescription()
    {
        return description;
    }

    public String getLabel()
    {
        return name;
    }

    public Set<Module> getActiveModules()
    {
        return activeModules;
    }

    private static Set<Module> s_defaultModules = null;
    public static Set<Module> getDefaultModuleSet(Module...additionalModules)
    {
        //NOT thread safe, but worst thing that will happen is that it is set to the same thing twice
        if (null == s_defaultModules)
        {
            Set<Module> defaultModules = new HashSet<Module>();
            defaultModules.add(getModule("Announcements"));
            defaultModules.add(getModule("FileContent"));
            defaultModules.add(getModule("Wiki"));
            defaultModules.add(getModule("Query"));
            defaultModules.add(getModule("Portal"));
            defaultModules.add(getModule("Issues"));
            s_defaultModules = Collections.unmodifiableSet(defaultModules);
        }

        Set<Module> modules = new HashSet<Module>(s_defaultModules);
        modules.addAll(Arrays.asList(additionalModules));

        return modules;
    }

    protected static Module getModule(String moduleName)
    {
        Module m = ModuleLoader.getInstance().getModule(moduleName);
        assert null != m : "Failed to find module " + moduleName;
        return m;
    }

    public static void addStandardManageLinks(NavTree adminNavTree, Container container)
    {
        // make sure the modules are loaded first
        boolean hasStudyModule = null != ModuleLoader.getInstance().getModule("Study");
        if (hasStudyModule)
        {
            adminNavTree.addChild(new NavTree("Manage Assays", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(container)));
            if (container.getActiveModules().contains(ModuleLoader.getInstance().getModule("Study")))
            {
                adminNavTree.addChild(new NavTree("Manage Study", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(container)));
            }
        }
        if (null != ModuleLoader.getInstance().getModule("List"))
            adminNavTree.addChild(new NavTree("Manage Lists", ListService.get().getManageListsURL(container)));
        if (hasStudyModule)
            adminNavTree.addChild(new NavTree("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(container)));
    }

    public void addManageLinks(NavTree adminNavTree, Container container)
    {
        addStandardManageLinks(adminNavTree, container);
    }

    public AppBar getAppBar(ViewContext context, PageConfig pageConfig)
    {
        ActionURL startURL = getStartURL(context.getContainer(), context.getUser());
        NavTree startPage = new NavTree(getStartPageLabel(context), startURL);
        String controllerName = context.getActionURL().getPageFlow();
        Module currentModule = ModuleLoader.getInstance().getModuleForController(controllerName);
        startPage.setSelected(currentModule == getDefaultModule());
        String title = context.getContainer().isWorkbook() ? context.getContainer().getTitle() : context.getContainer().getName();
        return new AppBar(title, context.getContainer().getStartURL(context.getUser()), startPage);
    }

    public boolean isWorkbookType()
    {
        return workbookType;
    }

    public void setWorkbookType(boolean workbookType)
    {
        this.workbookType = workbookType;
    }

    @Nullable
    protected FolderTab findTab(String caption)
    {
        for (FolderTab tab : getDefaultTabs())
        {
            if (tab.getName().equalsIgnoreCase(caption))
            {
                return tab;
            }
        }
        return null;
    }
}
