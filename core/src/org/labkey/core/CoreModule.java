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
package org.labkey.core;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.ClientAPIAuditViewFactory;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.etl.CachingDataIterator;
import org.labkey.api.etl.RemoveDuplicatesDataIterator;
import org.labkey.api.etl.ResultSetDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.module.FirstRequestHandler;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeResourceLoader;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleDependencySorter;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.module.ResourceFinder;
import org.labkey.api.module.SpringModule;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.script.RhinoService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.NestedGroupsTest;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.Compress;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ExtUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.ContactWebPart;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.menu.ContainerMenu;
import org.labkey.api.view.menu.ProjectsMenu;
import org.labkey.api.webdav.FileSystemAuditViewFactory;
import org.labkey.api.webdav.ModuleStaticResolverImpl;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.core.admin.ActionsTsvWriter;
import org.labkey.core.admin.AdminController;
import org.labkey.core.admin.importer.FolderTypeImporterFactory;
import org.labkey.core.admin.importer.PageImporterFactory;
import org.labkey.core.admin.importer.SearchSettingsImporterFactory;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.admin.writer.FolderSerializationRegistryImpl;
import org.labkey.core.admin.writer.FolderTypeWriterFactory;
import org.labkey.core.admin.writer.PageWriterFactory;
import org.labkey.core.admin.writer.SearchSettingsWriterFactory;
import org.labkey.core.analytics.AnalyticsController;
import org.labkey.core.analytics.AnalyticsServiceImpl;
import org.labkey.core.attachment.AttachmentServiceImpl;
import org.labkey.core.dialect.PostgreSqlDialectFactory;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.DbLoginAuthenticationProvider;
import org.labkey.core.login.LoginController;
import org.labkey.core.query.AttachmentAuditViewFactory;
import org.labkey.core.query.ContainerAuditViewFactory;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.query.UserAuditViewFactory;
import org.labkey.core.security.SecurityController;
import org.labkey.core.test.TestController;
import org.labkey.core.thumbnail.ThumbnailServiceImpl;
import org.labkey.core.user.UserController;
import org.labkey.core.webdav.DavController;
import org.labkey.core.workbook.WorkbookFolderType;
import org.labkey.core.workbook.WorkbookQueryView;
import org.labkey.core.workbook.WorkbookSearchView;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 25, 2005
 * Time: 2:54:30 PM
 */
public class CoreModule extends SpringModule implements SearchService.DocumentProvider
{
    public static final String EXPERIMENTAL_JSDOC = "experimental-jsdoc";

    @Override
    public String getName()
    {
        return CORE_MODULE_NAME;
    }

    @Override
    public double getVersion()
    {
        return 12.11;
    }

    @Override
    public int compareTo(Module m)
    {
        //core module always sorts first
        return (m instanceof CoreModule) ? 0 : -1;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void init()
    {
        ServiceRegistry.get().registerService(ContainerService.class, ContainerManager.getContainerService());
        ServiceRegistry.get().registerService(FolderSerializationRegistry.class, FolderSerializationRegistryImpl.get());
        SqlDialectManager.register(new PostgreSqlDialectFactory());

        addController("admin", AdminController.class);
        addController("admin-sql", SqlScriptController.class);
        addController("security", SecurityController.class);
        addController("user", UserController.class);
        addController("login", LoginController.class);
        addController("junit", JunitController.class);
        addController("core", CoreController.class);
        addController("test", TestController.class);
        addController("analytics", AnalyticsController.class);

        AuthenticationManager.registerProvider(new DbLoginAuthenticationProvider(), Priority.Low);
        AttachmentService.register(new AttachmentServiceImpl());
        AnalyticsServiceImpl.register();
        FirstRequestHandler.addFirstRequestListener(new CoreFirstRequestHandler());
        RhinoService.register();
        ServiceRegistry.get().registerService(ThumbnailService.class, new ThumbnailServiceImpl());

        ModuleStaticResolverImpl.get();

        DefaultSchema.registerProvider("core", new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new CoreQuerySchema(schema.getUser(), schema.getContainer());
            }
        });

        List<String> possibleRoots = new ArrayList<String>();
        if (null != getSourcePath())
            possibleRoots.add(getSourcePath() + "/../../..");
        if (null != System.getProperty("project.root"))
            possibleRoots.add(System.getProperty("project.root"));

        for (String root : possibleRoots)
        {
            File projectRoot = new File(root);
            if (projectRoot.exists())
            {
                AppProps.getInstance().setProjectRoot(FileUtil.getAbsoluteCaseSensitiveFile(projectRoot).toString());

                root = AppProps.getInstance().getProjectRoot();
                ResourceFinder api = new ResourceFinder("API", root + "/server/api", root + "/build/modules/api");
                ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", api);
                ResourceFinder internal = new ResourceFinder("Internal", root + "/server/internal", root + "/build/modules/internal");
                ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", internal);

                // set the root only once
                break;
            }
        }

        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_JSDOC, "Javascript Documentation", "Displays LabKey javascript API's from the Developer Links menu.", false);
    }

    @Override
    public Set<ModuleResourceLoader> getResourceLoaders()
    {
        return Collections.<ModuleResourceLoader>singleton(new FolderTypeResourceLoader());
    }

    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(
                new AlwaysAvailableWebPartFactory("Contacts")
                {
                    public WebPartView getWebPartView(ViewContext ctx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                    {
                        return new ContactWebPart();
                    }
                },
                new AlwaysAvailableWebPartFactory("Folders", WebPartFactory.LOCATION_MENUBAR, false, false) {
                    public WebPartView getWebPartView(final ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        final ProjectsMenu projectsMenu = new ProjectsMenu(portalCtx);
                        projectsMenu.setCollapsed(false);
                        WebPartView v = new WebPartView("Folders") {
                            @Override
                            protected void renderView(Object model, PrintWriter out) throws Exception
                            {
                                out.write("<table style='width:50'><tr><td style='vertical-align:top;padding:4px'>");
                                include(new ContainerMenu(portalCtx));
                                out.write("</td><td style='vertical-align:top;padding:4px'>");
                                include(projectsMenu);
                                out.write("</td></tr></table>");
                            }
                        };
                        v.setFrame(WebPartView.FrameType.PORTAL);
                        return v;
                    }
                },
                new BaseWebPartFactory("Workbooks")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        WorkbookQueryView wbqview = new WorkbookQueryView(portalCtx, new CoreQuerySchema(portalCtx.getUser(), portalCtx.getContainer()));
                        VBox box = new VBox(new WorkbookSearchView(wbqview), wbqview);
                        box.setFrame(WebPartView.FrameType.PORTAL);
                        box.setTitle("Workbooks");
                        return box;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return !c.isWorkbook() && location.equalsIgnoreCase(HttpView.BODY);
                    }
                },
                new BaseWebPartFactory("Workbook Description")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        JspView view = new JspView("/org/labkey/core/workbook/workbookDescription.jsp");
                        view.setTitle("Workbook Description");
                        view.setFrame(WebPartView.FrameType.NONE);
                        return view;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return false;
                    }
                },
                new AlwaysAvailableWebPartFactory("Projects")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        JspView<Portal.WebPart> view = new JspView<Portal.WebPart>("/org/labkey/core/project/projects.jsp", webPart);

                        String title = webPart.getPropertyMap().containsKey("title") ? webPart.getPropertyMap().get("title") : "Projects";
                        view.setTitle(title);

                        if (portalCtx.hasPermission(AdminPermission.class))
                        {
                            NavTree customize = new NavTree("");
                            customize.setScript("customizeProjectWebpart(" + webPart.getRowId() + ", \'" + webPart.getPageId() + "\', " + webPart.getIndex() + ");");
                            customize.setDisplay("Large Icons");
                            view.setCustomize(customize);
                        }
                        return view;
                    }
                }));
    }


    @Override
    public void beforeUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
        {
            CoreSchema core = CoreSchema.getInstance();

            try
            {
                core.getSqlDialect().prepareNewDatabase(core.getSchema());
            }
            catch(ServletException e)
            {
                throw new RuntimeException(e);
            }
        }

        super.beforeUpdate(moduleContext);
    }


    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        super.afterUpdate(moduleContext);

        if (moduleContext.isNewInstall())
            bootstrap();

        try
        {
            // Increment on every core module upgrade to defeat browser caching of static resources.
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    private void bootstrap()
    {
        // Create the initial groups
        GroupManager.bootstrapGroup(Group.groupAdministrators, "Administrators");
        GroupManager.bootstrapGroup(Group.groupUsers, "Users");
        GroupManager.bootstrapGroup(Group.groupGuests, "Guests");
        GroupManager.bootstrapGroup(Group.groupDevelopers, "Developers", PrincipalType.ROLE);

        // Other containers inherit permissions from root; admins get all permissions, users & guests none
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        Role siteAdminRole = RoleManager.getRole(SiteAdminRole.class);
        Role readerRole = RoleManager.getRole(ReaderRole.class);

        ContainerManager.bootstrapContainer("/", siteAdminRole, noPermsRole, noPermsRole);

        // Users & guests can read from /home
        ContainerManager.bootstrapContainer(ContainerManager.HOME_PROJECT_PATH, siteAdminRole, readerRole, readerRole);

        // Only users can read from /home/support
        ContainerManager.bootstrapContainer(ContainerManager.SHARED_CONTAINER_PATH, siteAdminRole, readerRole, noPermsRole);

        try
        {
            // Need to insert standard MV indicators for the root -- okay to call getRoot() since we just created it.
            Container rootContainer = ContainerManager.getRoot();
            String rootContainerId = rootContainer.getId();
            TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();

            for (Map.Entry<String,String> qcEntry : MvUtil.getDefaultMvIndicators().entrySet())
            {
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("Container", rootContainerId);
                params.put("MvIndicator", qcEntry.getKey());
                params.put("Label", qcEntry.getValue());

                Table.insert(null, mvTable, params);
            }
        }
        catch (Throwable t)
        {
            ExceptionUtil.logExceptionToMothership(null, t);
        }

    }



    @Override
    public CoreUpgradeCode getUpgradeCode()
    {
        return new CoreUpgradeCode();
    }


    @Override
    public void destroy()
    {
        super.destroy();
        UsageReportingLevel.cancelUpgradeCheck();
    }


    @Override
    public void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        // Any containers in the cache have bogus folder types since they aren't registered until startup().  See #10310
        ContainerManager.clearCache();

        // This listener deletes all properties; make sure it executes after most of the other listeners
        ContainerManager.addContainerListener(new CoreContainerListener(), ContainerManager.ContainerListener.Order.Last);
        SecurityManager.init();
        ModuleLoader.getInstance().registerFolderType(this, FolderType.NONE);
        AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();
        SystemMaintenance.setTimer();

        AuditLogService.get().addAuditViewFactory(UserAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(GroupAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(AttachmentAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ContainerAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(FileSystemAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ClientAPIAuditViewFactory.getInstance());

        TempTableTracker.init();
        ContextListener.addShutdownListener(TempTableTracker.getShutdownListener());
        ContextListener.addShutdownListener(DavController.getShutdownListener());

        // Export action stats on graceful shutdown
        ContextListener.addShutdownListener(new ShutdownListener() {
            public void shutdownPre(ServletContextEvent servletContextEvent)
            {
            }

            public void shutdownStarted(ServletContextEvent servletContextEvent)
            {
                Logger logger = Logger.getLogger(ActionsTsvWriter.class);

                if (null != logger)
                {
                    Appender appender = logger.getAppender("ACTION_STATS");

                    if (null != appender && appender instanceof RollingFileAppender)
                        ((RollingFileAppender)appender).rollOver();
                    else
                        Logger.getLogger(CoreModule.class).warn("Could not rollover the action stats tsv file--there was no appender named ACTION_STATS, or it is not a RollingFileAppender.");

                    TSVWriter writer = new ActionsTsvWriter();
                    StringBuilder buf = new StringBuilder();

                    try
                    {
                        writer.write(buf);
                    }
                    catch (IOException e)
                    {
                        Logger.getLogger(CoreModule.class).error("Exception exporting action stats", e);
                    }

                    logger.info(buf.toString());
                }
            }
        });

        AdminController.registerAdminConsoleLinks();
        AnalyticsController.registerAdminConsoleLinks();

        WebdavService.get().setResolver(WebdavResolverImpl.get());
        ModuleLoader.getInstance().registerFolderType(this, new WorkbookFolderType());

        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
        {
            ss.addDocumentProvider(this);
        }

        SecurityManager.addViewFactory(new SecurityController.GroupDiagramViewFactory());

        FolderSerializationRegistry fsr = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != fsr)
        {
            fsr.addFactories(new FolderTypeWriterFactory(), new FolderTypeImporterFactory());
            fsr.addFactories(new SearchSettingsWriterFactory(), new SearchSettingsImporterFactory());
            fsr.addFactories(new PageWriterFactory(), new PageImporterFactory());
        }

        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_CONTAINER_RELATIVE_URL,
                "Container Relative URL",
                "Use container relative URLs of the form /labkey/container/controller-action instead of the current /labkey/controller/container/action URLs.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP,
                "Client-side Exception Logging",
                "Report unhandled JavaScript exceptions to mothership.",
                false);
    }

    @Override
    public String getTabName(ViewContext context)
    {
        return "Admin";
    }


    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        if (user == null)
        {
            return AppProps.getInstance().getHomePageActionURL();
        }
        else if (c != null && "/".equals(c.getPath()) && user.isAdministrator())
        {
            return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
        }
        else if (c != null && c.hasPermission(user, AdminPermission.class))
        {
            return PageFlowUtil.urlProvider(SecurityUrls.class).getProjectURL(c);
        }
        else
        {
            return PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId(), AppProps.getInstance().getHomePageActionURL());
        }
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_NEVER;
    }


    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        @SuppressWarnings({"unchecked"})
        Set<Class> testClasses = new HashSet<Class>(Arrays.asList(
                Table.TestCase.class,
                Table.DataIteratorTestCase.class,
                DbSchema.TestCase.class,
                TableViewFormTestCase.class,
                ActionURL.TestCase.class,
                SecurityManager.TestCase.class,
                PropertyManager.TestCase.class,
                ContainerManager.TestCase.class,
                TabLoader.TabLoaderTestCase.class,
                MapLoader.MapLoaderTestCase.class,
                GroupManager.TestCase.class,
                SecurityController.TestCase.class,
                AttachmentServiceImpl.TestCase.class,
                WebdavResolverImpl.TestCase.class,
                Lsid.TestCase.class,
                MimeMap.TestCase.class,
                HString.TestCase.class,
                ModuleStaticResolverImpl.TestCase.class,
                StorageProvisioner.TestCase.class,
                RhinoService.TestCase.class,
                SimpleTranslator.TranslateTestCase.class,
                ResultSetDataIterator.TestCase.class,
                ExceptionUtil.TestCase.class,
                ViewCategoryManager.TestCase.class,
                TableSelectorTestCase.class,
                NestedGroupsTest.class,
                Compress.TestCase.class
                //,RateLimiter.TestCase.class
        ));

        testClasses.addAll(SqlDialectManager.getAllJUnitTests());

        return testClasses;
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return new HashSet<Class>(Arrays.asList(
                DateUtil.TestCase.class,
                TSVWriter.TestCase.class,
                ExcelLoader.ExcelLoaderTestCase.class,
                ExcelFactory.ExcelFactoryTestCase.class,
                ModuleDependencySorter.TestCase.class,
                DateUtil.TestCase.class,
                DatabaseCache.TestCase.class,
                PasswordExpiration.TestCase.class,
                BooleanFormat.TestCase.class,
                XMLWriterTest.TestCase.class,
                FileUtil.TestCase.class,
                FileType.TestCase.class,
                MemTracker.TestCase.class,
                StringExpressionFactory.TestCase.class,
                Path.TestCase.class,
                PageFlowUtil.TestCase.class,
                ResultSetUtil.TestCase.class,
                ArrayListMap.TestCase.class,
                DbScope.DialectTestCase.class,
                ValidEmail.TestCase.class,
                RemoveDuplicatesDataIterator.DeDuplicateTestCase.class,
                CachingDataIterator.ScrollTestCase.class,
                StringUtilsLabKey.TestCase.class,
                ExtUtil.TestCase.class
        ));
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set
        (
            CoreSchema.getInstance().getSchema(),       // core
            Portal.getSchema(),                         // portal
            PropertyManager.getSchema(),                // prop
            TestSchema.getInstance().getSchema()        // test
        );
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set
            (
                CoreSchema.getInstance().getSchemaName(),       // core
                Portal.getSchemaName(),                         // portal
                PropertyManager.getSchemaName(),                // prop
                TestSchema.getInstance().getSchemaName()        // test
            );
    }

    @Override
    public List<String> getAttributions()
    {
        return Arrays.asList(
            "<a href=\"http://www.apache.org\" target=\"top\"><img src=\"http://www.apache.org/images/asf_logo.gif\" alt=\"Apache\" width=\"185\" height=\"50\"></a>",
            "<a href=\"http://www.springframework.org\" target=\"top\"><img src=\"http://static.springframework.org/images/spring21.png\" alt=\"Spring\" width=\"100\" height=\"48\"></a>"
        );
    }

    @NotNull
    @Override
    public List<File> getStaticFileDirectories()
    {
        List<File> dirs = super.getStaticFileDirectories();
        if (AppProps.getInstance().isDevMode())
        {
            if (null != getSourcePath())
            {
                dirs.add(0, new File(getSourcePath(),"../../internal/webapp"));
                dirs.add(0, new File(getSourcePath(),"../../api/webapp"));
            }
        }
        return dirs;
    }

    @NotNull
    @Override
    protected List<File> getResourceDirectories()
    {
        List<File> resources = super.getResourceDirectories();

        String root = AppProps.getInstance().getProjectRoot();

        resources.add(new File(root + "/server/api"));
        resources.add(new File(root + "/server/internal"));
        if (AppProps.getInstance().isDevMode())
        {
            resources.add(new File(root + "/build/modules/api"));
            resources.add(new File(root + "/build/modules/internal"));
        }

        return resources;
    }

    @Override
    public void enumerateDocuments(SearchService.IndexTask task, @NotNull Container c, Date since)
    {
        if (null == task)
            task = ServiceRegistry.get(SearchService.class).defaultTask();

        if (c.isRoot())
            return;

        Container p = c.getProject();
        assert null != p;
        String displayTitle;
        String searchTitle;
        String body;

        // UNDONE: generalize to other folder types
        Study study = StudyService.get().getStudy(c);

        if (null != study)
        {
            displayTitle = study.getSearchDisplayTitle();
            searchTitle = study.getSearchKeywords();
            body = study.getSearchBody();
        }
        else
        {
            String type;

            if (c.isProject())
                type = "Project";
            else if (c.isWorkbook())
                type = "Workbook";
            else
                type = "Folder";

            String name = c.isWorkbook() ? c.getTitle() : c.getName();

            String description = StringUtils.trimToEmpty(c.getDescription());
            displayTitle = type + " -- " + name;
            searchTitle = name + " " + description + " " + type;
            body = type + " " + name + (c.isProject() ? "" : " in Project " + p.getName());
            body += "\n" + description;
        }

        Map<String, Object> properties = new HashMap<String, Object>();

        assert (null != searchTitle);
        properties.put(SearchService.PROPERTY.searchTitle.toString(), searchTitle);
        properties.put(SearchService.PROPERTY.displayTitle.toString(), displayTitle);
        properties.put(SearchService.PROPERTY.categories.toString(), SearchService.navigationCategory.getName());
        ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
        startURL.setExtraPath(c.getId());
        WebdavResource doc = new SimpleDocumentResource(c.getParsedPath(),
                "link:" + c.getId(),
                c.getId(),
                "text/plain",
                body.getBytes(),
                startURL,
                properties);
        task.addResource(doc, SearchService.PRIORITY.item);
    }

    
    @Override
    public void indexDeleted() throws SQLException
    {
        Table.execute(CoreSchema.getInstance().getSchema(), new SQLFragment(
            "UPDATE core.Documents SET lastIndexed=NULL"
        ));
    }
}
