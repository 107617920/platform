/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.Group;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.menu.FolderAdminMenu;
import org.labkey.api.view.menu.MenuService;
import org.labkey.api.view.menu.ProjectAdminMenu;
import org.labkey.api.view.menu.SiteAdminMenu;
import org.labkey.api.query.QueryUrls;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jun 21, 2007
 * Time: 10:48:42 AM
 */
public class PopupAdminView extends PopupMenuView
{
    private boolean canAdmin;

    protected void renderInternal(PopupMenu model, PrintWriter out) throws Exception
    {
        if (canAdmin)
            super.renderInternal(model, out);
        else
            out.write("&nbsp;");
    }

    public PopupAdminView(final ViewContext context)
    {
        canAdmin = context.hasPermission(ACL.PERM_ADMIN);
        if (!canAdmin)
            return;
        
        NavTree navTree = new NavTree("Admin");
        Container c = context.getContainer();
        User user = context.getUser();

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
        //Allow Admins to turn the folder bar on & off
        if (laf.getFolderDisplayMode() != FolderDisplayMode.ALWAYS && !"post".equals(getViewContext().getRequest().getMethod().toLowerCase()))
        {
            ActionURL adminURL = MenuService.get().getSwitchAdminModeURL(context);
            if (context.isAdminMode())
                navTree.addChild("Hide Navigation Bar", adminURL);
            else //
                navTree.addChild("Show Navigation Bar", adminURL);
        }

        if (user.isAdministrator())
        {
            NavTree siteAdmin = new NavTree("Manage Site");

            siteAdmin.addChildren(SiteAdminMenu.getNavTree(context));
            navTree.addChild(siteAdmin);
        }

        if (!c.isRoot())
        {
            NavTree projectAdmin = new NavTree("Manage Project");
            projectAdmin.addChildren(ProjectAdminMenu.getNavTree(context));
            navTree.addChild(projectAdmin);

            c.getFolderType().addManageLinks(navTree, c);

            Comparator<Module> moduleComparator = new Comparator<Module>()
            {
                public int compare(Module o1, Module o2)
                {
                    if (null == o1 && null == o2)
                        return 0;
                    if (null == o1 || null == o2)
                        return null == o1 ? -1 : 1;
                    return o1.getTabName(context).compareToIgnoreCase(o2.getTabName(context));
                }
            };
            SortedSet<Module> activeModules = new TreeSet<Module>(moduleComparator);
            activeModules.addAll(c.getActiveModules());
            SortedSet<Module> disabledModules = new TreeSet<Module>(moduleComparator);
            disabledModules.addAll(ModuleLoader.getInstance().getModules());
            disabledModules.removeAll(activeModules);

            NavTree goToModuleMenu = new NavTree("Go To Module");
            Module defaultModule = null;
            if (c.getFolderType() != FolderType.NONE)
            {
                defaultModule = c.getFolderType().getDefaultModule();
                goToModuleMenu.addChild(c.getName() + " Start Page", c.getFolderType().getStartURL(c, user));
            }

            addModulesToMenu(context, activeModules, defaultModule, goToModuleMenu);

            if (!disabledModules.isEmpty())
            {
                NavTree disabledModuleMenu = new NavTree("More Modules");
                addModulesToMenu(context, disabledModules, defaultModule, disabledModuleMenu);
                if (disabledModuleMenu.hasChildren())
                {
                    goToModuleMenu.addSeparator();
                    goToModuleMenu.addChild(disabledModuleMenu);
                }
            }

            if (goToModuleMenu.hasChildren())
                navTree.addChild(goToModuleMenu);
        }

        if (user.isDeveloper())
        {
            NavTree devMenu = new NavTree("Developer Links");
            devMenu.addChildren(PopupDeveloperView.getNavTree(context));
            navTree.addChild(devMenu);
        }

        navTree.setId("adminMenu");
        setNavTree(navTree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.BOLDTEXT);
    }

    private void addModulesToMenu(ViewContext context, SortedSet<Module> modules, Module defaultModule, NavTree menu)
    {
        for (Module module : modules)
        {
            if (null == module || module.equals(defaultModule))
                continue;

            ActionURL tabUrl = module.getTabURL(context.getContainer(), context.getUser());
            if(null != tabUrl)
                menu.addChild(module.getTabName(context), tabUrl);
        }
    }
}
