/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.list.view.ListController;
import org.labkey.list.view.ListImportHelper;
import org.springframework.web.servlet.mvc.Controller;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class ListDefinitionImpl implements ListDefinition
{
    protected static final String NAMESPACE_PREFIX = "List";

    static public ListDefinitionImpl of(ListDef def)
    {
        if (def == null)
            return null;
        return new ListDefinitionImpl(def);
    }

    boolean _new;
    ListDef _defOld;
    ListDef _def;
    Domain _domain;
    public ListDefinitionImpl(ListDef def)
    {
        _def = def;
    }

    public ListDefinitionImpl(Container container, String name)
    {
        _new = true;
        _def = new ListDef();
        _def.setContainer(container.getId());
        _def.setName(name);
        Lsid lsid = ListDomainType.generateDomainURI(name, container);
        _domain = PropertyService.get().createDomain(container, lsid.toString(), name);
    }

    public int getListId()
    {
        return _def.getRowId();
    }

    public String getEntityId()
    {
        return _def.getEntityId();
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_def.getContainerId());
    }

    public Domain getDomain()
    {
        if (_domain == null)
        {
            _domain = PropertyService.get().getDomain(_def.getDomainId());
        }
        return _domain;
    }

    public String getName()
    {
        return _def.getName();
    }

    public String getKeyName()
    {
        return _def.getKeyName();
    }

    public void setKeyName(String name)
    {
        if (_def.getTitleColumn() != null && _def.getTitleColumn().equals(getKeyName()))
        {
            edit().setTitleColumn(name);
        }
        edit().setKeyName(name);
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public KeyType getKeyType()
    {
        return KeyType.valueOf(_def.getKeyType());
    }

    public void setKeyType(KeyType type)
    {
        _def.setKeyType(type.toString());
    }

    public DiscussionSetting getDiscussionSetting()
    {
        return _def.getDiscussionSettingEnum();
    }

    public void setDiscussionSetting(DiscussionSetting discussionSetting)
    {
        _def.setDiscussionSettingEnum(discussionSetting);
    }

    public boolean getAllowDelete()
    {
        return _def.getAllowDelete();
    }

    public void setAllowDelete(boolean allowDelete)
    {
        _def.setAllowDelete(allowDelete);
    }

    public boolean getAllowUpload()
    {
        return _def.getAllowUpload();
    }

    public void setAllowUpload(boolean allowUpload)
    {
        _def.setAllowUpload(allowUpload);
    }

    public boolean getAllowExport()
    {
        return _def.getAllowExport();
    }

    public void setAllowExport(boolean allowExport)
    {
        _def.setAllowExport(allowExport);
    }

    @Override
    public boolean getIndexMetaData()
    {
        return _def.getIndexMetaData();
    }

    @Override
    public void setIndexMetaData(boolean indexMetaData)
    {
        _def.setIndexMetaData(indexMetaData);
    }

    public void save(User user) throws Exception
    {
        try
        {
            ExperimentService.get().ensureTransaction();

            if (_new)
            {
                _domain.save(user);
                _def.setDomainId(_domain.getTypeId());
                _def = ListManager.get().insert(user, _def);
                _new = false;
            }
            else
            {
                _def = ListManager.get().update(user, _def);
                _defOld = null;
                addAuditEvent(user, String.format("The definition of the list %s was modified", _def.getName()));
            }

            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
        ListManager.get().enumerateDocuments(null, getContainer(), null);
    }

    public ListItem createListItem()
    {
        return new ListItemImpl(this);
    }

    public ListItem getListItem(Object key)
    {
        // Convert key value to the proper type, since PostgreSQL 8.3 requires that key parameter types match their column types.
        Object typedKey = getKeyType().convertKey(key);

        return getListItem(new SimpleFilter("Key", typedKey));
    }

    public ListItem getListItemForEntityId(String entityId)
    {
        return getListItem(new SimpleFilter("EntityId", entityId));
    }

    private ListItem getListItem(SimpleFilter filter)
    {
        try
        {
            filter.addCondition("ListId", getListId());
            ListItm itm = Table.selectObject(getIndexTable(), Table.ALL_COLUMNS, filter, null, ListItm.class);
            if (itm == null)
            {
                return null;
            }
            return new ListItemImpl(this, itm);
        }
        catch (SQLException e)
        {
            return null;
        }
    }

    public void deleteListItems(User user, Collection keys) throws SQLException
    {
        try
        {
            ExperimentService.get().ensureTransaction();
            for (Object key : keys)
            {
                ListItem item = getListItem(key);
                if (item != null)
                {
                    item.delete(user, getContainer());
                }
            }
            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
    }

    public void delete(User user) throws SQLException, DomainNotFoundException
    {
        try
        {
            ExperimentService.get().ensureTransaction();

            SimpleFilter lstItemFilter = new SimpleFilter("ListId", getListId());
            ListItm[] itms = Table.select(getIndexTable(), Table.ALL_COLUMNS, lstItemFilter, null, ListItm.class);
            Table.delete(getIndexTable(), lstItemFilter);
            for (ListItm itm : itms)
            {
                if (itm.getObjectId() == null)
                    continue;
                ListItemImpl.deleteListItemContents(itm, getContainer(), user);
            }
            Table.delete(ListManager.get().getTinfoList(), getListId());
            ServiceRegistry.get(SearchService.class).deleteResource("list:" + _def.getEntityId());
            Domain domain = getDomain();
            domain.delete(user);

            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
    }

    public List<String> insertListItems(User user, DataLoader loader, @Nullable File attachmentDir, @Nullable ListImportProgress progress) throws IOException
    {
        List<String> errors = new LinkedList<String>();
        Set<String> mvIndicatorColumnNames = new CaseInsensitiveHashSet();

        for (DomainProperty property : getDomain().getProperties())
            if (property.isMvEnabled())
                mvIndicatorColumnNames.add(property.getName() + MvColumn.MV_INDICATOR_SUFFIX);

        Map<String, DomainProperty> propertiesByName = getDomain().createImportMap(true);
        Map<String, DomainProperty> foundProperties = new CaseInsensitiveHashMap<DomainProperty>();
        ColumnDescriptor cdKey = null;
        DomainProperty dpKey = null;

        Object errorValue = new Object(){@Override public String toString(){return "~ERROR VALUE~";}};

        // We know the types, so don't infer them.  Instead, read the header line and create a ColumnDescriptor array
        // that tells the loader all the types.
        String[][] firstLine = loader.getFirstNLines(1);

        ArrayList<ColumnDescriptor> columnList = new ArrayList<ColumnDescriptor>(firstLine.length);

        for (String columnHeader : firstLine[0])
        {
            ColumnDescriptor descriptor;

            if (getKeyName().equalsIgnoreCase(columnHeader))
            {
                descriptor = new ColumnDescriptor(columnHeader, getKeyType().getPropertyType().getJavaType());
            }
            else
            {
                DomainProperty prop = propertiesByName.get(columnHeader);

                if (null == prop)
                    descriptor = new ColumnDescriptor(columnHeader, String.class); // We're going to ignore this column... just claim it's a string
                else
                    descriptor = new ColumnDescriptor(columnHeader, prop.getPropertyDescriptor().getPropertyType().getJavaType());
            }

            columnList.add(descriptor);
        }

        ColumnDescriptor[] columns = columnList.toArray((new ColumnDescriptor[columnList.size()]));
        loader.setColumns(columns);

        for (ColumnDescriptor cd : columns)
        {
            String columnName = cd.name;
            DomainProperty property = propertiesByName.get(columnName);
            cd.errorValues = errorValue;

            boolean isKeyField = getKeyName().equalsIgnoreCase(cd.name) || null != property && getKeyName().equalsIgnoreCase(property.getName());

            if (property == null && !isKeyField)
            {
                errors.add("The field '" + columnName + "' could not be matched to a field in this list.");
                continue;
            }

            if (isKeyField)
            {
                if (cdKey != null)
                {
                    errors.add("The field '" + getKeyName() + "' appears more than once.");
                }
                else
                {
                    cdKey = cd;
                    dpKey = property;
                }
            }
            else
            {
                // Special handling for MV indicators -- they don't have real property descriptors.
                if (mvIndicatorColumnNames.contains(columnName))
                {
                    cd.name = property.getPropertyURI();
                    cd.clazz = String.class;
                    cd.setMvIndicator(getContainer());
                }
                else
                {
                    cd.clazz = property.getPropertyDescriptor().getPropertyType().getJavaType();

                    if (foundProperties.containsKey(columnName))
                    {
                        errors.add("The field '" + property.getName() + "' appears more than once.");
                    }
                    if (foundProperties.containsValue(property) && !property.isMvEnabled())
                    {
                        errors.add("The fields '" + property.getName() + "' and '" + property.getPropertyDescriptor().getNonBlankCaption() + "' refer to the same property.");
                    }
                    foundProperties.put(columnName, property);
                    cd.name = property.getPropertyURI();
                    if (property.isMvEnabled())
                    {
                        cd.setMvEnabled(getContainer());
                    }
                }
            }
        }

        if (cdKey == null && getKeyType() != ListDefinition.KeyType.AutoIncrementInteger)
        {
            errors.add("There must be a field with the name '" + getKeyName() + "'");
        }

        if (!errors.isEmpty())
            return errors;

        List<Map<String, Object>> rows = loader.load();

        if (null != progress)
            progress.setTotalRows(rows.size());

        Set<Object> keyValues = new HashSet<Object>();
        Set<String> missingValues = new HashSet<String>();
        Set<String> wrongTypes = new HashSet<String>();
        Set<String> noUpload = new HashSet<String>();

		DomainProperty[] domainProperties = getDomain().getProperties();
		
        for (Map<String, Object> row : rows)
        {
            row = new CaseInsensitiveHashMap<Object>(row);
            for (DomainProperty domainProperty : domainProperties)
            {
                if (dpKey == domainProperty)
                    continue;
                Object o = row.get(domainProperty.getPropertyURI());
                boolean valueMissing;
                if (o == null)
                {
                    valueMissing = true;
                }
                else if (o instanceof MvFieldWrapper)
                {
                    MvFieldWrapper mvWrapper = (MvFieldWrapper)o;
                    if (mvWrapper.isEmpty())
                        valueMissing = true;
                    else
                    {
                        valueMissing = false;
                        if (!MvUtil.isValidMvIndicator(mvWrapper.getMvIndicator(), getContainer()))
                        {
                            String columnName = domainProperty.getName() + MvColumn.MV_INDICATOR_SUFFIX;
                            wrongTypes.add(columnName);
                            errors.add(columnName + " must be a valid MV indicator.");
                        }
                    }
                }
                else
                {
                    valueMissing = o.toString().length() == 0;
                }
                
                if (domainProperty.isRequired() && valueMissing && !missingValues.contains(domainProperty.getName()))
                {
                    missingValues.add(domainProperty.getName());
                    errors.add(domainProperty.getName() + " is required.");
                }
                else if (domainProperty.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT && null == attachmentDir && !valueMissing && !noUpload.contains(domainProperty.getName()))
                {
                    noUpload.add(domainProperty.getName());
                    errors.add("Can't upload to field " + domainProperty.getName() + " with type " + domainProperty.getType().getLabel() + ".");
                }
                else if (!valueMissing && o == errorValue && !wrongTypes.contains(domainProperty.getName()))
                {
                    wrongTypes.add(domainProperty.getName());
                    errors.add(domainProperty.getName() + " must be of type " + domainProperty.getType().getLabel() + ".");
                }
            }

            if (cdKey != null)
            {
                Object key = row.get(cdKey.name);
                if (null == key)
                {
                    errors.add("Blank values are not allowed in field " + cdKey.name);
                    return errors;
                }
                else if (!getKeyType().isValidKey(key))
                {
                    // Ideally, we'd display the value we failed to convert and/or the row... but key.toString() is currently "~ERROR VALUE~".  See #10475.
                    // TODO: Fix this
                    errors.add("Could not convert values in key field \"" + cdKey.name + "\" to type " + getKeyType().getLabel());
                    return errors;
                }
                else if (!keyValues.add(key))
                {
                    errors.add("There are multiple rows with key value " + row.get(cdKey.name));
                    return errors;
                }
            }
        }

        if (!errors.isEmpty())
            return errors;

        doBulkInsert(user, cdKey, getDomain(), foundProperties, rows, attachmentDir, errors, progress);

        return errors;
    }

    private void doBulkInsert(User user, ColumnDescriptor cdKey, Domain domain, Map<String, DomainProperty> properties, List<Map<String, Object>> rows, @Nullable File attachmentDir, List<String> errors, @Nullable ListImportProgress progress)
    {
        try
        {
            ExperimentService.get().ensureTransaction();

            // There's a disconnect here between the PropertyService api and OntologyManager...
            ArrayList<DomainProperty> used = new ArrayList<DomainProperty>(properties.size());
            for (DomainProperty dp : domain.getProperties())
                if (properties.containsKey(dp.getPropertyURI()))
                    used.add(dp);
            ListImportHelper helper = new ListImportHelper(user, this, used.toArray(new DomainProperty[used.size()]), cdKey, attachmentDir, progress);

            // our map of properties can have duplicates due to MV indicator columns (different columns, same URI)
            Set<PropertyDescriptor> propSet = new HashSet<PropertyDescriptor>();
            for (DomainProperty domainProperty : properties.values())
            {
                propSet.add(domainProperty.getPropertyDescriptor());
            }

            PropertyDescriptor[] pds = propSet.toArray(new PropertyDescriptor[propSet.size()]);
            List<String> inserted = OntologyManager.insertTabDelimited(getContainer(), user, null, helper, pds, rows, true);
            addAuditEvent(user, "Bulk inserted " + inserted.size() + " rows to list.");

            ExperimentService.get().commitTransaction();
        }
        catch (ValidationException ve)
        {
            for (ValidationError error : ve.getErrors())
                errors.add(error.getMessage());
        }
        catch (SQLException se)
        {
            errors.add(se.getMessage());
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
    }

    private void addAuditEvent(User user, String comment)
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setComment(comment);

            Container c = getContainer();
            event.setContainerId(c.getId());
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
            event.setKey1(getDomain().getTypeURI());

            event.setEventType(ListManager.LIST_AUDIT_EVENT);
            event.setIntKey1(getListId());
            event.setKey3(getName());

            AuditLogService.get().addEvent(event);
        }
    }


    public int getRowCount()
    {
        return 0;
    }

    public String getDescription()
    {
        return _def.getDescription();
    }

    public String getTitleColumn()
    {
        return _def.getTitleColumn();
    }

    public void setTitleColumn(String titleColumn)
    {
        edit().setTitleColumn(titleColumn);
    }

    public TableInfo getTable(User user)
    {
        ListTable ret = new ListTable(user, this);
        return ret;
    }

    public ActionURL urlShowDefinition()
    {
        return urlFor(ListController.EditListDefinitionAction.class);
    }

    public ActionURL urlShowData()
    {
        return urlFor(ListController.GridAction.class);
    }

    public ActionURL urlUpdate(Object pk, URLHelper returnUrl)
    {
        ActionURL url = urlFor(ListController.UpdateAction.class);

        // Can be null if caller will be filling in pk (e.g., grid edit column)
        if (null != pk)
            url.addParameter("pk", pk.toString());

        if (returnUrl != null)
            url.addParameter("returnUrl", returnUrl.getLocalURIString());

        return url;
    }

    public ActionURL urlDetails(Object pk)
    {
        ActionURL url = urlFor(ListController.DetailsAction.class);
        // Can be null if caller will be filling in pk (e.g., grid edit column)

        if (null != pk)
            url.addParameter("pk", pk.toString());

        return url;
    }

    public ActionURL urlShowHistory()
    {
        return urlFor(ListController.HistoryAction.class);
    }

    public ActionURL urlFor(Class<? extends Controller> actionClass)
    {
        ActionURL ret = new ActionURL(actionClass, getContainer());
        ret.addParameter("listId", Integer.toString(getListId()));
        return ret;
    }

    private ListDef edit()
    {
        if (_new)
        {
            return _def;
        }
        if (_defOld == null)
        {
            _defOld = _def;
            _def = _defOld.clone();
        }
        return _def;

    }

    public TableInfo getIndexTable()
    {
        switch (getKeyType())
        {
            case Integer:
            case AutoIncrementInteger:
                return ListManager.get().getTinfoIndexInteger();
            case Varchar:
                return ListManager.get().getTinfoIndexVarchar();
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public String toString()
    {
        return getName() + ", id: " + getListId();
    }

    public int compareTo(ListDefinition l)
    {
        return getName().compareToIgnoreCase(l.getName());
    }
}
