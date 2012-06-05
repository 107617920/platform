/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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
package org.labkey.issue;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.query.IssuesQuerySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 18, 2005
 * Time: 3:48:21 PM
 */
public class IssuesModule extends DefaultModule implements SearchService.DocumentProvider
{
    public static final String NAME = "Issues";
    public static final String EXPERIMENTAL_ISSUES_ACTIVITY = "issuesactivity";

    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 12.10;
    }

    protected void init()
    {
        addController("issues", IssuesController.class);
        IssuesQuerySchema.register();

        EmailTemplateService.get().registerTemplate(IssueUpdateEmailTemplate.class);
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        ArrayList<WebPartFactory> result = new ArrayList<WebPartFactory>();
        result.add(new IssuesWebPartFactory());
        result.add(new AlwaysAvailableWebPartFactory("Issues List")
        {
            @Override
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
            {
                IssuesListView result = new IssuesListView();
                result.setTitle("Issues List");
                result.setFrame(WebPartView.FrameType.PORTAL);
                return result;
            }
        });
        result.add(new IssuesActivityWebPartFactory());
        return result;
    }

    public boolean hasScripts()
    {
        return true;
    }


    public void startup(ModuleContext moduleContext)
    {
        ContainerManager.addContainerListener(new IssueContainerListener());
        SecurityManager.addGroupListener(new IssueGroupListener());
        UserManager.addUserListener(new IssueUserListener());

        SearchService ss = ServiceRegistry.get().getService(SearchService.class);

        if (null != ss)
        {
            ss.addSearchCategory(IssueManager.searchCategory);
            ss.addResourceResolver("issue",IssueManager.getSearchResolver());
            ss.addDocumentProvider(this);
            ss.addSearchResultTemplate(new IssuesController.IssueSearchResultTemplate());
        }

        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_ISSUES_ACTIVITY, "Issues Activity", "Adds an Issues Activity webpart to show recent changes to issues in the folder.", false);
    }


    @Override
    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<String>();
        long count = IssueManager.getIssueCount(c);
        if (count > 0)
            list.add("" + count + " Issue" + (count > 1 ? "s" : ""));
        return list;
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_USER_PREFERENCE;
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        return IssuesController.getListURL(c);
    }

    @NotNull
    public Set<Class> getJUnitTests()
    {
        return new HashSet<Class>(Arrays.asList(
            org.labkey.issue.model.IssueManager.TestCase.class ));
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(IssuesSchema.getInstance().getSchema());
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(IssuesSchema.getInstance().getSchemaName());
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new IssueUpgradeCode();
    }


    public void enumerateDocuments(final SearchService.IndexTask task, final Container c, final Date modifiedSince)
    {
        Runnable r = new Runnable()
            {
                public void run()
                {
                    IssueManager.indexIssues(task, c, modifiedSince);
                }
            };
        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }

    public void indexDeleted() throws SQLException
    {
        new SqlExecutor(IssuesSchema.getInstance().getSchema(), new SQLFragment(
            "UPDATE issues.issues SET lastIndexed=NULL")).execute();
    }
}
