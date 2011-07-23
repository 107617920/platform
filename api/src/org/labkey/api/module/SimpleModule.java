/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import org.apache.commons.collections15.Closure;
import org.apache.commons.collections15.CollectionUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.*;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartFactory;
import org.labkey.data.xml.TablesDocument;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/*
* User: Dave
* Date: Dec 3, 2008
* Time: 4:08:20 PM
*/

/**
 * Used for simple, entirely file-based modules
 */
public class SimpleModule extends SpringModule implements ContainerManager.ContainerListener
{
    private static final Logger _log = Logger.getLogger(ModuleUpgrader.class);

    public static String NAMESPACE_PREFIX = "ExtensibleTable";
    public static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = NAMESPACE_PREFIX + "-${SchemaName}";
    public static String DOMAIN_LSID_TEMPLATE = "${FolderLSIDBase}:${TableName}";

    public static String PROPERTY_NAMESPACE_PREFIX_TEMPLATE = NAMESPACE_PREFIX + "-${SchemaName}-${TableName}";
    public static String PROPERTY_LSID_TEMPLATE = "${FolderLSIDBase}:${GUID}";

    int _factorySetHash = 0;
    private Set<String> _schemaNames;

    public SimpleModule(@NotNull String name)
    {
        setName(name);
        addController(name.toLowerCase(), SimpleController.class);
    }

    protected void init()
    {
        getSchemaNames();
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        List<WebPartFactory> factories = new ArrayList<WebPartFactory>();
        for(File webPartFile : getWebPartFiles())
        {
            factories.add(new SimpleWebPartFactory(this, webPartFile));
        }
        _factorySetHash = calcFactorySetHash();
        return factories;
    }

    @NotNull
    protected File[] getWebPartFiles()
    {
        File viewsDir = new File(getExplodedPath(), SimpleController.VIEWS_DIRECTORY);
        return viewsDir.exists() && viewsDir.isDirectory() ? viewsDir.listFiles(SimpleWebPartFactory.webPartFileFilter) : new File[0];
    }

    public boolean isWebPartFactorySetStale()
    {
        return _factorySetHash != calcFactorySetHash();
    }

    protected int calcFactorySetHash()
    {
        return Arrays.hashCode(getWebPartFiles());
    }

    public boolean hasScripts()
    {
        return getSqlScripts(null, CoreSchema.getInstance().getSqlDialect()).size() > 0;
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        if (_schemaNames == null)
        {
            Resource schemasDir = getModuleResource("schemas");
            if (schemasDir != null)
            {
                final Set<String> schemaNames = new LinkedHashSet<String>();
                CollectionUtils.forAllDo(schemasDir.list(), new Closure<Resource>() {
                    @Override
                    public void execute(Resource resource)
                    {
                        String name = resource.getName();
                        if (name.endsWith(".xml"))
                        {
                            try
                            {
                                TablesDocument.Factory.parse(resource.getInputStream());
                                String schemaName = name.substring(0, name.length() - ".xml".length());
                                schemaNames.add(schemaName);
                            }
                            catch (XmlException e)
                            {
                                _log.error("Skipping '" + name + "' schema file: " + e.getMessage());
                            }
                            catch (IOException e)
                            {
                                _log.error("Skipping '" + name + "' schema file: " + e.getMessage());
                            }
                        }
                    }
                });
                _schemaNames = Collections.unmodifiableSet(schemaNames);
            }
            else
            {
                _schemaNames = Collections.emptySet();
            }
        }
        return _schemaNames;
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        for (final String schemaName : getSchemaNames())
        {
            final DbSchema dbschema = DbSchema.get(schemaName);

            DefaultSchema.registerProvider(schemaName, new DefaultSchema.SchemaProvider()
            {
                public QuerySchema getSchema(final DefaultSchema schema)
                {
                    if (schema.getContainer().getActiveModules().contains(SimpleModule.this))
                    {
                        return QueryService.get().createSimpleUserSchema(schemaName, null, schema.getUser(), schema.getContainer(), dbschema);
                    }
                    return null;
                }
            });
        }
        ContainerManager.addContainerListener(this);
    }

    protected String getResourcePath()
    {
        return null;
    }

    public ActionURL getTabURL(Container c, User user)
    {
        return SimpleController.getBeginViewUrl(this, c);
    }

    public void containerCreated(Container c, User user)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        for (final String schemaName : getSchemaNames())
        {
            purgeSchema(schemaName, c, user);
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {        
    }

    private void purgeSchema(String schemaName, Container c, User user)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, c, schemaName);
        if (schema == null)
        {
            return;
        }
        
        try
        {
            List<TableInfo> sorted = schema.getSortedTables();
            Collections.reverse(sorted);
            for (TableInfo table : sorted)
            {
                ColumnInfo containerCol = null;
                for (ColumnInfo column : table.getColumns())
                {
                    if ("container".equalsIgnoreCase(column.getName()))
                    {
                        containerCol = column;
                        break;
                    }
                }

                if (containerCol != null)
                    purgeTable(table, c, user);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private void purgeTable(TableInfo table, Container c, User u)
            throws SQLException
    {
        if (table instanceof FilteredTable)
        {
            SimpleFilter filter = new SimpleFilter("Container", c);
            TableInfo realTable = ((FilteredTable)table).getRealTable();
            if (realTable.getTableType() == DatabaseTableType.TABLE)
            {
                Table.delete(realTable, filter);
            }
        }
        
        Domain domain = table.getDomain();
        if (domain != null)
        {
            SQLFragment objectIds = domain.getDomainKind().sqlObjectIdsInDomain(domain);

            Integer[] ids = Table.executeArray(table.getSchema(), objectIds, Integer.class);
            OntologyManager.deleteOntologyObjects(ids, c, true);
        }
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
