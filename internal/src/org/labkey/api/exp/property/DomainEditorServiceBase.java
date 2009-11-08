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

package org.labkey.api.exp.property;

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryException;
import org.labkey.api.security.ACL;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ViewContext;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 4, 2007
 * Time: 10:55:27 AM
 * <p/>
 * Base class for building GWT editors that edit domains
 *
 * @see org.labkey.api.gwt.client.ui.PropertiesEditor in InternalGWT
 */
public class DomainEditorServiceBase extends BaseRemoteService
{
    public DomainEditorServiceBase(ViewContext context)
    {
        super(context);
    }


    // paths
    public List<String> getContainers()
    {
        try
        {
            Set<Container> set = ContainerManager.getAllChildren(ContainerManager.getRoot(), getUser());
            List<String> list = new ArrayList<String>();
            for (Container c : set)
            {
                if (c.isRoot())
                    continue;
                list.add(c.getPath());
            }
            Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
            return list;
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", x);
            throw x;
        }
    }


    public List<String> getSchemas(String containerId)
    {
        try
        {
            DefaultSchema defSchema = getSchemaForContainer(containerId);
            List<String> list = new ArrayList<String>();
            if (null != defSchema.getUserSchemaNames())
                for (String schemaName : defSchema.getUserSchemaNames())
                    list.add(schemaName);
            return list;
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", x);
            throw x;
        }
    }


    public Map<String,String> getTablesForLookup(String containerId, String schemaName)
    {
        try
        {
            DefaultSchema defSchema = getSchemaForContainer(containerId);
            QuerySchema qSchema = defSchema.getSchema(schemaName);
            if (qSchema == null || !(qSchema instanceof UserSchema))
                return null;

            UserSchema schema = (UserSchema) qSchema;
            Map<String, String> availableQueries = new HashMap<String, String>();  //  GWT: TreeMap does not work
            for (String name : schema.getTableAndQueryNames(false))
            {
                TableInfo table;
                try
                {
                    table = schema.getTable(name);
                }
                catch (QueryException x)
                {
                    continue;
                }
                if (table == null)
                    continue;
                List<ColumnInfo> pkColumns = table.getPkColumns();
                if (pkColumns.size() != 1)
                    continue;
                availableQueries.put(name, pkColumns.get(0).getName());
            }
            return availableQueries;
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", x);
            throw x;
        }
    }


    private DefaultSchema getSchemaForContainer(String containerId)
    {
        Container container = null;
        if (containerId == null || containerId.length() == 0)
            container = getContainer();
        else
        {
            if (GUID.isGUID(containerId))
                container = ContainerManager.getForId(containerId);
            if (null == container)
                container = ContainerManager.getForPath(containerId);
        }

        if (container == null)
        {
            throw new IllegalArgumentException(containerId);
        }
        else if (!container.hasPermission(getUser(), ACL.PERM_READ))
        {
            throw new IllegalStateException("You do not have permissions to see this folder.");
        }

        return DefaultSchema.get(getUser(), container);
    }

    public GWTDomain getDomainDescriptor(String typeURI)
    {
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, getContainer());
    }

    public GWTDomain getDomainDescriptor(String typeURI, String domainContainerId)
    {
        Container domainContainer = ContainerManager.getForId(domainContainerId);
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, domainContainer);
    }

    public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update) throws ChangePropertyDescriptorException
    {
        return DomainUtil.updateDomainDescriptor(orig, update, getContainer(), getUser());
    }

    protected GWTDomain getDomainDescriptor(String typeURI, Container domainContainer)
    {
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, domainContainer);
    }
}
