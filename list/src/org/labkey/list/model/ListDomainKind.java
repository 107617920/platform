/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: Nick
 * Date: 5/8/13
 * Time: 4:12 PM
 */
public abstract class ListDomainKind extends AbstractDomainKind
{
    /*
     * the columns common to all lists
     */
    private final static Set<PropertyStorageSpec> BASE_PROPERTIES;
    private ListDefinitionImpl _list;

    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec("created", JdbcType.TIMESTAMP),
            new PropertyStorageSpec("modified", JdbcType.TIMESTAMP),
            new PropertyStorageSpec("createdBy", JdbcType.INTEGER),
            new PropertyStorageSpec("modifiedBy", JdbcType.INTEGER),
            new PropertyStorageSpec("entityId", JdbcType.VARCHAR).setEntityId(true).setNullable(false),
            new PropertyStorageSpec("lastIndexed", JdbcType.TIMESTAMP)
        };

        BASE_PROPERTIES = new HashSet<>(Arrays.asList(props));
    }

    public void setListDefinition(ListDefinitionImpl list)
    {
        _list = list;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return "List '" + domain.getName() + "'";
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        throw new UnsupportedOperationException("sqlObjectIdsInDomain NYI for ListDomainKind");
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (null == listDef)
            return null;
        return listDef.urlShowData();
    }

    @Nullable
    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (null == listDef)
            return null;
        return listDef.urlShowDefinition();
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return getKindName().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }


    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        Set<PropertyStorageSpec> specs = new HashSet<>(BASE_PROPERTIES);
        specs.addAll(super.getBaseProperties());
        return specs;
    }

    @Override
    public PropertyStorageSpec getPropertySpec(PropertyDescriptor pd, Domain domain)
    {
        ListDefinition list = ListService.get().getList(domain);
        if (null == list)
            list = _list;

        if (null != list)
        {
            if (pd.getName().equalsIgnoreCase(list.getKeyName()))
            {
                PropertyStorageSpec key = this.getKeyProperty(list);
                assert key.isPrimaryKey();
                _list = null;
                return key;
            }
        }
        return super.getPropertySpec(pd, domain);
    }

    abstract PropertyStorageSpec getKeyProperty(ListDefinition list);

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return PageFlowUtil.set("EntityId", "Created", "CreatedBy", "Modified", "ModifiedBy", "LastIndexed");
    }

    @Override
    public String getStorageSchemaName()
    {
        return ListSchema.getInstance().getSchemaName();
    }

    @Override
    public DbScope getScope()
    {
        return ListSchema.getInstance().getSchema().getScope();
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
    {
        // return PageFlowUtil.set(new PropertyStorageSpec.Index(false, ListDomainKind.KEY_FIELD));
        return Collections.emptySet(); // TODO: Allow this to return the Key Column
    }

    public static Lsid generateDomainURI(String name, Container c, ListDefinition.KeyType keyType)
    {
        String type = getType(keyType);
        StringBuilder typeURI = getBaseURI(name, type, c);

        // 13131: uniqueify the lsid for situations where a preexisting list was renamed
        int i = 1;
        String sTypeURI = typeURI.toString();
        String uniqueURI = sTypeURI;
        while (OntologyManager.getDomainDescriptor(uniqueURI, c) != null)
        {
            uniqueURI = sTypeURI + '-' + (i++);
        }
        return new Lsid(uniqueURI);
    }

    public static Lsid createPropertyURI(String listName, String columnName, Container c, ListDefinition.KeyType keyType)
    {
        StringBuilder typeURI = getBaseURI(listName, getType(keyType), c);
        typeURI.append(".").append(PageFlowUtil.encode(columnName));
        return new Lsid(typeURI.toString());
    }

    private static String getType(ListDefinition.KeyType keyType)
    {
        String type;
        switch (keyType)
        {
            case Integer:
            case AutoIncrementInteger:
                type = IntegerListDomainKind.NAMESPACE_PREFIX;
                break;
            case Varchar:
                type = VarcharListDomainKind.NAMESPACE_PREFIX;
                break;
            default:
                throw new IllegalStateException();
        }
        return type;
    }

    private static StringBuilder getBaseURI(String listName, String type, Container c)
    {
        return new StringBuilder("urn:lsid:")
                .append(PageFlowUtil.encode(AppProps.getInstance().getDefaultLsidAuthority()))
                .append(":").append(type).append(".Folder-").append(c.getRowId()).append(":")
                .append(PageFlowUtil.encode(listName));
    }
}
