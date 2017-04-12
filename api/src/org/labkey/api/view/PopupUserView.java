/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.PageConfig;

/**
 * Popup menu for upper-right corner of main frame
 * User: jeckels
 * Date: Oct 20, 2011
 */
public class PopupUserView extends PopupMenuView
{
    public PopupUserView(ViewContext context)
    {
        setNavTree(createNavTree(context));
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);

        getModelBean().setIsSingletonMenu(true);
    }

    public static NavTree createNavTree(ViewContext context)
    {
        return createNavTree(context, null);
    }

    public static NavTree createNavTree(ViewContext context, PageConfig pageConfig)
    {
        User user = context.getUser();
        Container c = context.getContainer();
        ActionURL currentURL = context.getActionURL();
        NavTree tree;

        if (PageFlowUtil.useExperimentalCoreUI())
        {
            tree = new NavTree();

            if (null != user && !user.isGuest())
            {
                NavTree signedIn = new NavTree("Signed in as " + user.getFriendlyName());
                tree.addChild(signedIn);
                tree.addSeparator();
            }
        }
        else
        {
            tree = new NavTree(user.getFriendlyName());
        }

        tree.setId("userMenu");

        NavTree myaccount = new NavTree("My Account", PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId(), currentURL));
        myaccount.setId("__lk-usermenu-myaccount");
        tree.addChild(myaccount);

        if (AppProps.getInstance().isAllowSessionKeys())
        {
            NavTree apikey = new NavTree("API Key", PageFlowUtil.urlProvider(SecurityUrls.class).getApiKeyURL(currentURL));
            myaccount.setId("__lk-usermenu-apikey");
            tree.addChild(apikey);
        }

        if (user.isImpersonated())
        {
            NavTree stop = new NavTree("Stop Impersonating", PageFlowUtil.urlProvider(LoginUrls.class).getStopImpersonatingURL(c, user.getImpersonationContext().getReturnURL()));
            stop.setId("__lk-usermenu-stopimpersonating");
            tree.addChild(stop);
        }
        else
        {
            ImpersonationContext impersonationContext = user.getImpersonationContext();
            @Nullable Container project = c.getProject();

            // If user is already impersonating then we need to check permissions on the actual admin user
            User adminUser = impersonationContext.isImpersonating() ? impersonationContext.getAdminUser() : user;

            // Must be site or project admin (folder admins can't impersonate)
            if (adminUser.hasRootAdminPermission() || (null != project && project.hasPermission(adminUser, AdminPermission.class)))
            {
                NavTree impersonateMenu = new NavTree("Impersonate");
                impersonateMenu.setId("__lk-usermenu-impersonate");
                impersonationContext.addMenu(impersonateMenu, c, user, currentURL);

                if (impersonateMenu.hasChildren())
                    tree.addChild(impersonateMenu);
            }

            NavTree out = new NavTree("Sign Out", PageFlowUtil.urlProvider(LoginUrls.class).getLogoutURL(c));
            out.setId("__lk-usermenu-signout");
            tree.addChild(out);
        }

        if (PageFlowUtil.useExperimentalCoreUI() && pageConfig != null)
        {
            NavTree help = PopupHelpView.createNavTree(context, pageConfig.getHelpTopic());

            if (help.hasChildren())
            {
                tree.addSeparator();

                for (NavTree child : help.getChildren())
                    tree.addChild(child);
            }
        }

        return tree;
    }
}
