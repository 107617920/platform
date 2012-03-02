/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.list.model;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.QueryService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.list.view.ListController;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ListManager implements SearchService.DocumentProvider
{
    static private ListManager instance;
    public static final String LIST_AUDIT_EVENT = "ListAuditEvent";

    synchronized static public ListManager get()
    {
        if (instance == null)
            instance = new ListManager();
        return instance;
    }

    public DbSchema getSchema()
    {
        return ExperimentService.get().getSchema();
    }

    public TableInfo getTinfoList()
    {
        return getSchema().getTable("list");
    }

    public ListDef[] getLists(Container container)
    {
        ListDef.Key key = new ListDef.Key(container);
        return key.select();
    }


    public ListDef[] getAllLists() throws SQLException
    {
        return Table.select(getTinfoList(), Table.ALL_COLUMNS, null, null, ListDef.class);
    }


    public ListDef getList(Container container, int id)
    {
        try
        {
            SimpleFilter filter = new PkFilter(getTinfoList(), id);
            filter.addCondition("Container", container);
            return Table.selectObject(getTinfoList(), filter, null, ListDef.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    
    public ListDef insert(User user, ListDef def) throws SQLException
    {
        ListDef ret = Table.insert(user, getTinfoList(), def);
        Container c = ContainerManager.getForId(def.getContainerId());
        if (null != c)
            enumerateDocuments(null, c, null);
        return ret;
    }


    public ListDef update(User user, ListDef def) throws SQLException
    {
        Container c = ContainerManager.getForId(def.getContainerId());
        if (null == c)
            throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);

        DbScope scope = getSchema().getScope();
        ListDef ret;
        try
        {
            scope.ensureTransaction();
            ListDef old = getList(c, def.getRowId());
            ret = Table.update(user, getTinfoList(), def, def.getRowId());
            if (!old.getName().equals(ret.getName()))
                QueryService.get().updateCustomViewsAfterRename(c, ListSchema.NAME, old.getName(), def.getName());

            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }

        // schedules a scan (doesn't touch db)
        enumerateDocuments(null, c, null);
        return ret;
    }


    // COMBINE WITH DATASET???
    public static final SearchService.SearchCategory listCategory = new SearchService.SearchCategory("list", "List");

    public void enumerateDocuments(SearchService.IndexTask t, final @NotNull Container c, Date since)
    {
        final SearchService.IndexTask task = null==t ? ServiceRegistry.get(SearchService.class).defaultTask() : t;
        
        Runnable r = new Runnable()
        {
            public void run()
            {
                Map<String, ListDefinition> lists = ListService.get().getLists(c);

                for (ListDefinition list : lists.values())
                {
                    String documentId = "list:" + ((ListDefinitionImpl)list).getEntityId();
                    Domain domain = list.getDomain();

                    // Delete from index if list has just been deleted or admin has chosen not to index it 
                    if (null == domain || !list.getIndexMetaData())
                    {
                        ServiceRegistry.get(SearchService.class).deleteResource(documentId);
                        continue;
                    }

                    StringBuilder body = new StringBuilder();
                    Map<String, Object> props = new HashMap<String, Object>();

                    props.put(SearchService.PROPERTY.categories.toString(), listCategory.toString());
                    props.put(SearchService.PROPERTY.displayTitle.toString(), "List " + list.getName());

                    if (!StringUtils.isEmpty(list.getDescription()))
                        body.append(list.getDescription()).append("\n");

                    ActionURL url = new ActionURL(ListController.GridAction.class, c);
                    url.setExtraPath(c.getId());
                    url.addParameter("listId",list.getListId());

                    String sep = "";

                    for (DomainProperty property : domain.getProperties())
                    {
                        String n = StringUtils.trimToEmpty(property.getName());
                        String l = StringUtils.trimToEmpty(property.getLabel());
                        if (n.equals(l))
                            l = "";
                        body.append(sep).append(StringUtilsLabKey.joinNonBlank(" ", n, l));
                        sep = ",\n";
                    }

                    SimpleDocumentResource r = new SimpleDocumentResource(
                            new Path(documentId),
                            documentId,
                            list.getContainer().getId(),
                            "text/plain",
                            body.toString().getBytes(),
                            url,
                            props);
                    task.addResource(r, SearchService.PRIORITY.item);
                }
            }
        };

        task.addRunnable(r, SearchService.PRIORITY.group);
    }

    
    public void indexDeleted() throws SQLException
    {
    }
}
