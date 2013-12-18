/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.experiment.api.property;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.MVDisplayColumnFactory;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditViewFactory;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DomainImpl implements Domain
{
    boolean _new;
    boolean _enforceStorageProperties = true;
    DomainDescriptor _ddOld;
    DomainDescriptor _dd;
    List<DomainPropertyImpl> _properties;
    private Set<PropertyStorageSpec.ForeignKey> _propertyForeignKeys = Collections.emptySet();

    public DomainImpl(DomainDescriptor dd)
    {
        _dd = dd;
        PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(getTypeURI(), getContainer());
        _properties = new ArrayList<>(pds.length);
        List<DomainPropertyManager.ConditionalFormatWithPropertyId> allFormats = DomainPropertyManager.get().getConditionalFormats(getContainer());
        for (PropertyDescriptor pd : pds)
        {
            List<ConditionalFormat> formats = new ArrayList<>();
            for (DomainPropertyManager.ConditionalFormatWithPropertyId format : allFormats)
            {
                if (format.getPropertyId() == pd.getPropertyId())
                {
                    formats.add(format);
                }
            }
            DomainPropertyImpl property = new DomainPropertyImpl(this, pd, formats);
            _properties.add(property);
        }
    }
    public DomainImpl(Container container, String uri, String name)
    {
        _new = true;
        _dd = new DomainDescriptor();
        _dd.setContainer(container);
        _dd.setProject(container.getProject());
        _dd.setDomainURI(uri);
        _dd.setName(name);
        _properties = new ArrayList<>();
    }

    public Container getContainer()
    {
        return _dd.getContainer();
    }

    private DomainKind _kind = null;

    public synchronized DomainKind getDomainKind()
    {
        if (null == _kind)
            _kind = PropertyService.get().getDomainKind(getTypeURI());
        return _kind;
    }

    public String getName()
    {
        return _dd.getName();
    }

    public String getLabel()
    {
        DomainKind kind = getDomainKind();
        if (kind == null)
        {
            return "Domain '" + getName() + "'";
        }
        else
        {
            return getDomainKind().getTypeLabel(this);
        }
    }

    public String getLabel(Container container)
    {
        String ret = getLabel();
        if (!getContainer().equals(container))
        {
            ret += "(" + getContainer().getPath() + ")";
        }
        return ret;
    }


    @Nullable   // null if not provisioned
    public String getStorageTableName()
    {
        return _dd.getStorageTableName();
    }

    @Override
    public void setEnforceStorageProperties(boolean enforceStorageProperties)
    {
        _enforceStorageProperties = enforceStorageProperties;
    }

    /**
     * @return all containers that contain at least one row of this domain's data.
     * Only works for domains that are persisted in exp.object, not those with their own provisioned hard tables
     */
    public Set<Container> getInstanceContainers()
    {
        assert getStorageTableName() == null : "This method only works on domains persisted in exp.object";
        SQLFragment sqlObjectIds = getDomainKind().sqlObjectIdsInDomain(this);
        if (sqlObjectIds == null)
            return Collections.emptySet();
        SQLFragment sql = new SQLFragment("SELECT DISTINCT exp.object.container FROM exp.object WHERE exp.object.objectid IN ");
        sql.append(sqlObjectIds);
        Set<Container> ret = new HashSet<>();
        for (String id : new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(String.class))
        {
            ret.add(ContainerManager.getForId(id));
        }
        return ret;
    }

    /**
     * @return all containers that contain at least one row of this domain's data, and where the user has the specified permission
     * Only works for domains that are persisted in exp.object, not those with their own provisioned hard tables
     */
    public Set<Container> getInstanceContainers(User user, Class<? extends Permission> perm)
    {
        Set<Container> ret = new HashSet<>();
        for (Container c : getInstanceContainers())
        {
            if (c.hasPermission(user, perm))
            {
                ret.add(c);
            }
        }
        return ret;
    }

    public String getDescription()
    {
        return _dd.getDescription();
    }

    public int getTypeId()
    {
        return _dd.getDomainId();
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public DomainPropertyImpl[] getProperties()
    {
        return _properties.toArray(new DomainPropertyImpl[_properties.size()]);
    }

    public void setPropertyIndex(DomainProperty prop, int index)
    {
        if (index < 0 || index >= _properties.size())
        {
            throw new IndexOutOfBoundsException();
        }
        if (!_properties.remove(prop))
        {
            throw new IllegalArgumentException("The property is not part of this domain");
        }
        _properties.add(index, (DomainPropertyImpl) prop);
    }

    public ActionURL urlShowData(ContainerUser context)
    {
        return getDomainKind().urlShowData(this, context);
    }

    public void delete(@Nullable User user) throws DomainNotFoundException
    {
        DefaultValueService.get().clearDefaultValues(getContainer(), this);
        OntologyManager.deleteDomain(getTypeURI(), getContainer());
        StorageProvisioner.drop(this);
        addAuditEvent(user, String.format("The domain %s was deleted", _dd.getName()));
    }

    private boolean isNew()
    {
        return _new;
    }

    // TODO: throws SQLException instead of RuntimeSQLException (e.g., constraint violation due to duplicate domain name) 
    public void save(User user) throws ChangePropertyDescriptorException
    {
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            List<DomainProperty> checkRequiredStatus = new ArrayList<>();
            try
            {
                if (isNew())
                {
                    _dd = Table.insert(user, OntologyManager.getTinfoDomainDescriptor(), _dd);
                    // CONSIDER put back if we want automatic provisioning for serveral DomainKinds
                    // StorageProvisioner.create(this);
                    addAuditEvent(user, String.format("The domain %s was created", _dd.getName()));
                }
                else if (_ddOld != null)
                {
                    _dd = Table.update(user, OntologyManager.getTinfoDomainDescriptor(), _dd, _dd.getDomainId());
                    addAuditEvent(user, String.format("The descriptor of domain %s was updated", _dd.getName()));
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            boolean propChanged = false;
            int sortOrder = 0;

            List<DomainProperty> propsDropped = new ArrayList<>();
            List<DomainProperty> propsAdded = new ArrayList<>();

            DomainKind kind = getDomainKind();
            boolean hasProvisioner = null != kind && null != kind.getStorageSchemaName();

            // Delete first #8978
            for (DomainPropertyImpl impl : _properties)
            {
                if (impl._deleted || (impl.isRecreateRequired()))
                {
                    impl.delete(user);
                    propsDropped.add(impl);
                    propChanged = true;
                }
            }

            if (hasProvisioner && _enforceStorageProperties)
            {
                if (!propsDropped.isEmpty())
                {
                    StorageProvisioner.dropProperties(this, propsDropped);
                }
            }

            // Keep track of the intended final name for each updated property, and its sort order
            Map<DomainPropertyImpl, Pair<String, Integer>> finalNames = new HashMap<>();

            // Now add and update #8978
            for (DomainPropertyImpl impl : _properties)
            {
                if (!impl._deleted)
                {
                    if (impl.isRecreateRequired())
                    {
                        impl.markAsNew();
                    }

                    if (impl.isNew())
                    {
                        if (impl._pd.isRequired())
                            checkRequiredStatus.add(impl);
                        impl.save(user, _dd, sortOrder++);
                        propsAdded.add(impl);
                        propChanged = true;
                    }
                    else
                    {
                        propChanged |= impl.isDirty();
                        if (impl._pdOld != null)
                        {
                            // If this field is newly required, or it's required and we're disabling MV indicators on
                            // it, make sure that all of the rows have values for it
                            if ((!impl._pdOld.isRequired() && impl._pd.isRequired()) ||
                                    (impl._pd.isRequired() && !impl._pd.isMvEnabled() && impl._pdOld.isMvEnabled()))
                            {
                                checkRequiredStatus.add(impl);
                            }
                        }

                        finalNames.put(impl, new Pair<>(impl.getName(), sortOrder));
                        // Same any changes with a temp, guaranteed unique name. This is important in case a single save
                        // is renaming "Field1"->"Field2" and "Field2"->"Field1". See issue 17020
                        impl.setName(new GUID().toStringNoDashes());
                        impl.save(user, _dd, sortOrder++);
                    }
                }
            }

            // Then rename them all to their final name
            for (Map.Entry<DomainPropertyImpl, Pair<String, Integer>> entry : finalNames.entrySet())
            {
                DomainPropertyImpl domainProperty = entry.getKey();
                String name = entry.getValue().getKey();
                int order = entry.getValue().getValue().intValue();
                domainProperty.setName(name);
                domainProperty.save(user, _dd, order);
            }

            _new = false;

            // Do the call to add the new properties last, after deletes and renames of existing properties
            if (propChanged && hasProvisioner && _enforceStorageProperties)
            {
                if (!propsAdded.isEmpty())
                {
                    StorageProvisioner.addProperties(this, propsAdded);
                }

                addAuditEvent(user, String.format("The column(s) of domain %s were modified", _dd.getName()));
            }

            if (!checkRequiredStatus.isEmpty() && null != kind)
            {
                for (DomainProperty prop : checkRequiredStatus)
                {
                    boolean hasRows = kind.hasNullValues(this, prop);
                    if (hasRows)
                    {
                        throw new IllegalStateException("The property \"" + prop.getName() + "\" cannot be required when it contains rows with blank values.");
                    }
                }
            }

            transaction.commit();
        }
    }

    private void addAuditEvent(@Nullable User user, String comment)
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setComment(comment);

            Container c = getContainer();
            event.setContainerId(c.getId());
            if (_dd.getProject() != null)
                event.setProjectId(_dd.getProject().getId());

            event.setKey1(getTypeURI());
            event.setKey3(getName());
            event.setEventType(DomainAuditViewFactory.DOMAIN_AUDIT_EVENT);

            AuditLogService.get().addEvent(event);
        }
    }

    public Map<String, DomainProperty> createImportMap(boolean includeMVIndicators)
    {
        List<DomainProperty> properties = new ArrayList<DomainProperty>(_properties);
        return ImportAliasable.Helper.createImportMap(properties, includeMVIndicators);
    }

    public DomainProperty addPropertyOfPropertyDescriptor(PropertyDescriptor pd)
    {
        assert pd.getPropertyId() == 0;
        assert pd.getContainer().equals(getContainer());

        // Warning: Shallow copy
        DomainPropertyImpl ret = new DomainPropertyImpl(this, pd.clone());
        _properties.add(ret);
        return ret;
    }

    public DomainProperty addProperty()
    {
        PropertyDescriptor pd = new PropertyDescriptor();
        pd.setContainer(getContainer());
        pd.setRangeURI(PropertyType.STRING.getTypeUri());
        pd.setScale(PropertyType.STRING.getScale());
        DomainPropertyImpl ret = new DomainPropertyImpl(this, pd);
        _properties.add(ret);
        return ret;
    }

    public DomainProperty addProperty(PropertyStorageSpec spec)
    {
        PropertyDescriptor pd = new PropertyDescriptor();
        pd.setContainer(getContainer());
        pd.setName(spec.getName());
        pd.setJdbcType(spec.getJdbcType(), spec.getSize());
        pd.setNullable(spec.isNullable());
//        pd.setAutoIncrement(spec.isAutoIncrement());      // always false in PropertyDescriptor
        pd.setMvEnabled(spec.isMvEnabled());
        pd.setPropertyURI(getTypeURI() + ":field-" + spec.getName());
        pd.setDescription(spec.getDescription());
        pd.setImportAliases(spec.getImportAliases());
        DomainPropertyImpl ret = new DomainPropertyImpl(this, pd);
        _properties.add(ret);
        return ret;
    }

    public String getTypeURI()
    {
        return _dd.getDomainURI();
    }

    public DomainPropertyImpl getProperty(int id)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getPropertyId() == id)
                return prop;
        }
        return null;
    }

    public DomainPropertyImpl getPropertyByURI(String uri)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getPropertyURI().equalsIgnoreCase(uri))
            {
                return prop;
            }
        }
        return null;
    }

    public DomainPropertyImpl getPropertyByName(String name)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getName().equalsIgnoreCase(name))
                return prop;
        }
        return null;
    }

    public List<ColumnInfo> getColumns(TableInfo sourceTable, ColumnInfo lsidColumn, Container container, User user)
    {
        List<ColumnInfo> result = new ArrayList<>();
        for (DomainProperty property : getProperties())
        {
            ColumnInfo column = new PropertyColumn(property.getPropertyDescriptor(), lsidColumn, container, user, false);
            result.add(column);
            if (property.isMvEnabled())
            {
                column.setMvColumnName(new FieldKey(null, column.getName() + MvColumn.MV_INDICATOR_SUFFIX));
                result.addAll(MVDisplayColumnFactory.createMvColumns(column, property.getPropertyDescriptor(), lsidColumn));
            }
        }
        return result;
    }

    private DomainDescriptor edit()
    {
        if (_new)
        {
            return _dd;
        }
        if (_ddOld == null)
        {
            _ddOld = _dd;
            _dd = _ddOld.clone();
        }
        return _dd;
    }

    @Override
    public int hashCode()
    {
        return _dd.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof DomainImpl))
            return false;
        // once a domain has been edited, it no longer equals any other domain:
        if (_ddOld != null || ((DomainImpl) obj)._ddOld != null)
            return false;
        return (_dd.equals(((DomainImpl) obj)._dd));
    }

    @Override
    public String toString()
    {
        return getTypeURI();
    }

    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys()
    {
        return _propertyForeignKeys;
    }

    public void setPropertyForeignKeys(Set<PropertyStorageSpec.ForeignKey> propertyForeignKeys)
    {
        _propertyForeignKeys = propertyForeignKeys;
    }
}
