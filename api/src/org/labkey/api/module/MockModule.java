/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.template.ClientDependency;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* User: Dave
* Date: Dec 2, 2008
* Time: 9:58:18 AM
*/

/**
 * A module that does nothing. Used for automated tests.
 */
public class MockModule implements Module
{
    private final String _name;
    private final double _version;
    private final String[] _dependencies;

    public MockModule(String name, String... dependencies)
    {
        this(name, 0, dependencies);
    }

    public MockModule(String name, double version, String... dependencies)
    {
        _name = name;
        _version = version;
        _dependencies = dependencies;
    }

    @Override
    public int compareTo(@NotNull Module m)
    {
        return (m instanceof MockModule) ? 0 : 1;
    }

    @Override
    public void initialize()
    {
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public String getTabName(ViewContext context)
    {
        return _name;
    }

    @Override
    public double getVersion()
    {
        return _version;
    }

    @Override
    public String getFormattedVersion()
    {
        return ModuleContext.formatVersion(getVersion());
    }

    @Override
    public void beforeUpdate(ModuleContext moduleContext)
    {
    }

    @Override
    public void versionUpdate(ModuleContext moduleContext) throws Exception
    {
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
    }

    @Override
    public void startup(ModuleContext moduleContext)
    {
    }

    @Override
    public void destroy()
    {
    }

    @Override
    @NotNull
    public Collection<WebPartFactory> getWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Class<? extends Controller>> getControllerNameToClass()
    {
        return null;
    }

    @Override
    public Map<Class<? extends Controller>, String> getControllerClassToName()
    {
        return null;
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        return null;
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

    @Override
    public Resolver getModuleResolver()
    {
        return null;
    }
    
    @Override
    public Resource getModuleResource(Path path)
    {
        return null;
    }

    @Override
    public Resource getModuleResource(String path)
    {
        return null;
    }

    @Override
    public InputStream getResourceStream(String filename) throws FileNotFoundException
    {
        return null;
    }

    @Override
    public String getSourcePath()
    {
        return null;
    }

    @Override
    public String getBuildPath()
    {
        return null;
    }

    @Override
    public String getSvnRevision()
    {
        return null;
    }

    @Override
    public String getSvnUrl()
    {
        return null;
    }

    @Override
    public Map<String, String> getProperties()
    {
        return null;
    }

    @Override
    public Set<String> getModuleDependenciesAsSet()
    {
        return new HashSet<>(Arrays.asList(_dependencies));
    }

    @Override
    public void setExplodedPath(File path)
    {
    }

    @Override
    public Set<String> getSqlScripts(@Nullable DbSchema schema)
    {
        return null;
    }

    @Override
    public String getSqlScriptsPath(@NotNull SqlDialect dialect)
    {
        return null;
    }

    @Override
    @Nullable
    public ReportDescriptor getCachedReport(Path path)
    {
        return null;
    }

    @Override
    public void cacheReport(Path path, ReportDescriptor descriptor)
    {
    }

    @Override
    public Set<Resource> getReportFiles()
    {
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public Set<ModuleResourceLoader> getResourceLoaders()
    {
        return Collections.emptySet();
    }

    @Override
    public File getExplodedPath()
    {
        return null;
    }

    @Override
    public void dispatch(HttpServletRequest request, HttpServletResponse response, ActionURL url) throws ServletException, IOException
    {
    }

    @Override
    @NotNull
    public List<File> getStaticFileDirectories()
    {
        return Collections.emptyList();
    }

    @Override
    public @Nullable Collection<String> getJarFilenames()
    {
        return null;
    }

    @Override
    public boolean isAutoUninstall()
    {
        return false;
    }

    @Override
    public void addDeferredUpgradeTask(Method task)
    {
    }

    @Override
    public void runDeferredUpgradeTasks(ModuleContext context)
    {
    }

    @Override
    public Set<Module> getResolvedModuleDependencies()
    {
        Set<Module> modules = new HashSet<>();
        Module module;
        for(String m : _dependencies)
        {
            module = ModuleLoader.getInstance().getModule(m);
            if(module != null)
            {
                modules.add(module);
                modules.addAll(module.getResolvedModuleDependencies());
            }
        }
        return modules;
    }

    @Override
    public Controller getController(HttpServletRequest request, String name)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, ModuleProperty> getModuleProperties()
    {
        return new HashMap<>();
    }

    @Override
    public @NotNull JSONObject getPageContextJson(ViewContext context)
    {
        return new JSONObject();
    }

    @Override
    public @NotNull LinkedHashSet<ClientDependency> getClientDependencies(Container c, User u)
    {
        return new LinkedHashSet<>();
    }

    @NotNull
    @Override
    public Set<SupportedDatabase> getSupportedDatabasesSet()
    {
        return DefaultModule.ALL_DATABASES;
    }

    @Nullable
    @Override
    public UpgradeCode getUpgradeCode()
    {
        return null;
    }

    @Override
    public String getResourcePath()
    {
        return null;
    }

    @Override
    public boolean getRequireSitePermission()
    {
        return false;
    }
}
