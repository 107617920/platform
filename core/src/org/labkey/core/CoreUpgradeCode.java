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
package org.labkey.core;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.Filter;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AbstractSettingsGroup;
import org.labkey.api.util.GUID;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.Portal;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.UsersDomainKind;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
public class CoreUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(CoreUpgradeCode.class);

    // We don't call ContainerManager.getRoot() during upgrade code since the container table may not yet match
    // ContainerManager's assumptions. For example, older installations don't have a description column until
    // the 10.1 scripts run (see #9927).
    private String getRootId()
    {
        return new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT EntityId FROM core.Containers WHERE Parent IS NULL").getObject(String.class);
    }

    // invoked by core-11.20-11.30.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void handleUnknownModules(ModuleContext context)
    {
        ModuleLoader.getInstance().handleUnkownModules();
    }

    static final String CURRENT_GROUP_CONCAT_VERSION = "1.00.23696";

    // invoked by core-13.22-13.23.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void installGroupConcat(ModuleContext context)
    {
        SqlDialect dialect = CoreSchema.getInstance().getSqlDialect();

        // Install only on SQL Server
        if (dialect.isSqlServer())
        {
            GroupConcatInstallationManager manager = new GroupConcatInstallationManager();

            // Just return if newest version is already present... probably installed by admin before upgrade
            if (manager.isInstalled(CURRENT_GROUP_CONCAT_VERSION))
                return;

            boolean success = manager.uninstallPrevious(context);

            // If we can't uninstall the old version then give up; GroupConcatInstallationManager already logged the error
            if (!success)
                return;

            // Attempt to install the new version
            manager.install(context, "group_concat_install_1.00.23696.sql");
        }
    }

    // invoked by core-12.21-12.22.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void ensureCoreUserPropertyDescriptors(ModuleContext context)
    {
        String domainURI = UsersDomainKind.getDomainURI("core", CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), context.getUpgradeUser());
        Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);

        if (domain == null)
            domain = PropertyService.get().createDomain(UsersDomainKind.getDomainContainer(), domainURI, CoreQuerySchema.USERS_TABLE_NAME);

        if (domain != null)
            UsersDomainKind.ensureDomainProperties(domain, context.getUpgradeUser(), context.isNewInstall());
    }

    // invoked by core-12.22-12.23.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade  // This needs to happen later, after all of the MaintenanceTasks have been registered
    public void migrateSystemMaintenanceSettings(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        Map<String, String> props = PropertyManager.getProperties(AbstractSettingsGroup.SITE_CONFIG_USER, ContainerManager.getRoot(), "SiteConfig");

        String interval = props.get("systemMaintenanceInterval");
        Set<String> enabled = new HashSet<>();

        for (SystemMaintenance.MaintenanceTask task : SystemMaintenance.getTasks())
            if (!task.canDisable() || !"never".equals(interval))
                enabled.add(task.getName());

        String time = props.get("systemMaintenanceTime");

        if (null == time)
            time = "2:00";

        SystemMaintenance.setProperties(enabled, time);
    }


    /* called at 12.2->12.3 */
    @SuppressWarnings({"UnusedDeclaration"})
    public void setPortalPageEntityId(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        DbSchema schema = CoreSchema.getInstance().getSchema();
        Collection<Portal.PortalPage> pages = new SqlSelector(schema, "SELECT * FROM core.PortalPages").getCollection(Portal.PortalPage.class);
        String updateSql = "UPDATE core.PortalPages SET EntityId=? WHERE Container=? AND PageId=?";

        SqlExecutor executor = new SqlExecutor(schema);

        for (Portal.PortalPage p : pages)
        {
            if (null != p.getEntityId())
                continue;
            executor.execute(updateSql, GUID.makeGUID(), p.getContainer().toString(), p.getPageId());
        }
    }


    // invoked by core-13.13-13.14.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void setPortalPageUniqueIndexes(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        DbSchema schema = CoreSchema.getInstance().getSchema();
        Collection<String> containers = new SqlSelector(schema, "SELECT EntityId FROM core.Containers").getCollection(String.class);
        for (String c : containers)
        {
            resetPageIndexes(schema, c);
        }
    }

    public static class PortalUpgradePage
    {
        private GUID containerId;
        private String pageId;
        private int index;

        public GUID getContainer()
        {
            return containerId;
        }

        public void setContainer(GUID containerId)
        {
            this.containerId = containerId;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }
    }

    private static void resetPageIndexes(DbSchema schema, String containerEntityId)
    {
        Filter filter = new SimpleFilter(FieldKey.fromString("Container"), containerEntityId);
        Sort sort = new Sort("Index");
        TableInfo portalTable = schema.getTable("PortalPages");
        Set<String> columnNames = new HashSet<>(3);
        columnNames.add("Container");
        columnNames.add("Index");
        columnNames.add("PageId");
        TableSelector ts = new TableSelector(portalTable, columnNames, filter, sort);
        Collection<PortalUpgradePage> pages = ts.getCollection(PortalUpgradePage.class);
        if (pages.size() > 0)
        {
            try
            {
                int validPageIndex = 1;
                for (PortalUpgradePage page : pages)
                {
                    if (validPageIndex != page.getIndex())
                    {
                        page.setIndex(validPageIndex);
                        Table.update(null, portalTable, page, new Object[] {page.getContainer(), page.getPageId()});
                    }
                    validPageIndex++;
                }
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
    }


    // invoked by core-13.14-13.15.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void populateWorkbookSortOrderAndName(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        final DbSchema schema = CoreSchema.getInstance().getSchema();
        final String updateSql = "UPDATE core.Containers SET SortOrder = ? WHERE Parent = ? AND RowId = ?";
        final SqlExecutor updateExecutor = new SqlExecutor(schema);

        String selectSql = "SELECT Parent, RowId FROM core.Containers WHERE Type = 'workbook' ORDER BY Parent, RowId";

        new SqlSelector(schema, selectSql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                Container parent = ContainerManager.getForId(rs.getString(1));

                if (null != parent)
                {
                    int sortOrder = DbSequenceManager.get(parent, ContainerManager.WORKBOOK_DBSEQUENCE_NAME).next();

                    int rowId = rs.getInt(2);
                    Container workbook = ContainerManager.getForRowId(rowId);
                    String oldName = workbook.getPath();
                    // Do a standard rename
                    ContainerManager.rename(workbook, moduleContext.getUpgradeUser(), Integer.toString(sortOrder));
                    // Add an alias so that old URLs continue to work
                    List<String> aliases = new ArrayList<>(Arrays.asList(ContainerManager.getAliasesForContainer(workbook)));
                    aliases.add(oldName);
                    ContainerManager.saveAliasesForContainer(workbook, aliases);
                    // Do a direct SQL update to set the sort order
                    updateExecutor.execute(updateSql, sortOrder, parent, rowId);
                }
            }
        });
    }

    // invoked by core-13.14-13.15.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void ensureCoreUserPropertyDescriptorScales(final ModuleContext context)
    {
        String domainURI = UsersDomainKind.getDomainURI("core", CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), context.getUpgradeUser());
        Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);

        if (domain == null)
            domain = PropertyService.get().createDomain(UsersDomainKind.getDomainContainer(), domainURI, CoreQuerySchema.USERS_TABLE_NAME);

        if (domain != null)
            UsersDomainKind.ensureDomainPropertyScales(domain, context.getUpgradeUser());
    }
}
