/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view.template;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.FooterProperties;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.wiki.WikiService;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;


public class HomeTemplate extends PrintTemplate
{
    protected HomeTemplate(String template, PageConfig page)
    {
        super(template, page);
    }

    public HomeTemplate(ViewContext context, Container c, ModelAndView body)
    {
        this(context, c, body, new PageConfig(context.getActionURL().getController()));
    }


    public HomeTemplate(ViewContext context, Container c, ModelAndView body, PageConfig page)
    {
        this("/org/labkey/api/view/template/HomeTemplate.jsp", context, c, body, page);
    }

    protected HomeTemplate(String template, ViewContext context, Container c, ModelAndView body, PageConfig page)
    {
        super(template, page);

        if (null == page.getNavTrail())
            page.setNavTrail(Collections.emptyList());

        setUserMetaTag(context, page);
        //show the header on the home template
        page.setShowHeader(true);

        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);

        WebPartView header = null;
        if (ModuleLoader.getInstance().isStartupComplete() && null != wikiService && null != c && null != c.getProject())
        {
            header = wikiService.getView(c.getProject(), "_header", false);
            if (null != header)
                header.setFrame(FrameType.NONE); // 12336: Explicitly don't frame the _header override.
        }

        if (null != header)
            setView("header", header);
        else
            setView("header", getHeaderView(page));

        // TODO: This is being side-effected by isHidePageTitle() check. That setting should be moved to PageConfig
        setBody(body);

        page.setAppBar(generateAppBarModel(context, page));

        setView("navigation", getNavigationView(context, page));

        setView("appbar", getAppBarView(context, page));
        setView("footer", getFooterView());
    }

    protected void setUserMetaTag(ViewContext context, PageConfig page)
    {
        // for testing add meta tags
        User user = context.getUser();
        User authenticatedUser = user;
        User impersonatedUser = null;
        if (authenticatedUser.isImpersonated())
        {
            impersonatedUser = user;
            authenticatedUser = user.getImpersonatingUser();
        }
        page.setMetaTag("authenticatedUser",null==authenticatedUser?"-":StringUtils.defaultString(authenticatedUser.getEmail(),user.getDisplayName(user)));
        page.setMetaTag("impersonatedUser", null==impersonatedUser?"-":StringUtils.defaultString(impersonatedUser.getEmail(),user.getDisplayName(user)));

    }

    private AppBar generateAppBarModel(ViewContext context, PageConfig page)
    {
        AppBar appBar;
        if (context.getContainer().isWorkbookOrTab())
        {
            ViewContext parentContext = new ViewContext(context);
            parentContext.setContainer(context.getContainer().getParent());
            FolderType folderType =  parentContext.getContainer().getFolderType();
            if (folderType instanceof MultiPortalFolderType)
                appBar = ((MultiPortalFolderType)folderType).getAppBar(parentContext, page, context.getContainer());
            else
                appBar = folderType.getAppBar(parentContext, page);
        }
        else
        {
            appBar = context.getContainer().getFolderType().getAppBar(context, page);
        }

        //HACK to fix up navTrail to delete navBar items
        List<NavTree> navTrail = page.getNavTrail();
        if (context.getContainer().isWorkbook())
        {
            // Add the main page for the workbook to the nav trail
            navTrail = new ArrayList<>(navTrail);
            navTrail.add(0, new NavTree(context.getContainer().getTitle(), context.getContainer().getStartURL(context.getUser())));
        }

        List<NavTree> appNavTrail = appBar.setNavTrail(navTrail, context);
        if (page.getTemplate() != PageConfig.Template.Wizard)
            page.setNavTrail(appNavTrail);

        //allow views to have flag to hide title
        if (getBody() instanceof WebPartView && ((WebPartView) getBody()).isHidePageTitle())
            appBar.setPageTitle(null);

        return appBar;
    }

    protected HttpView getAppBarView(ViewContext context, PageConfig page)
    {
        return new AppBarView(page);
    }

    protected HttpView getHeaderView(PageConfig page)
    {
        String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
        Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
        return new TemplateHeaderView(upgradeMessage, moduleFailures, page);
    }

    protected HttpView getFooterView()
    {
        WebPartView view = null;
        if (FooterProperties.isShowFooter())
        {
            Module coreModule = ModuleLoader.getInstance().getCoreModule();
            List<Module> modules = new ArrayList<>(ModuleLoader.getInstance().getModules());
            if (null != ModuleLoader.getInstance().getModule(FooterProperties.getFooterModule()))
            {
                modules.add(ModuleLoader.getInstance().getModule(FooterProperties.getFooterModule()));
            }
            ListIterator<Module> i = modules.listIterator(modules.size());
            while (i.hasPrevious())
            {
                view = ModuleHtmlView.get(i.previous(), "_footer");
                if (null != view)
                    break;
            }
            if (null == view)
            {
                view = ModuleHtmlView.get(coreModule, "_footer");
            }
            if (null != view)
            {
                view.setFrame(FrameType.NONE);
            }
        }
        return view;
    }

    protected HttpView getNavigationView(ViewContext context, PageConfig page)
    {
        return new MenuBarView(context, page);
    }

    @Override
    public void prepareWebPart(PageConfig page)
    {
        if (page.shouldAppendPathToTitle())
        {
            String extraPath = getRootContext().getActionURL().getExtraPath();
            if (extraPath.length() > 0)
                page.setTitle(page.getTitle() + (page.getTitle() != null && !page.getTitle().isEmpty() ? ": " : "") + extraPath);
        }

        if (null == getView("header"))
            setView("header", getHeaderView(page));
    }
}
