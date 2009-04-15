/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.experiment.defaults;

import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.*;

/*
 * User: brittp
 * Date: Jan 30, 2009
 * Time: 11:11:14 AM
 */

public class DefaultValueServiceImpl extends DefaultValueService
{

    private static final String DOMAIN_DEFAULT_VALUE_LSID_PREFIX = "DomainDefaultValue";
    private static final String USER_DEFAULT_VALUE_LSID_PREFIX = "UserDefaultValue";
    private static final String USER_DEFAULT_VALUE_DOMAIN_PARENT = "UserDefaultValueParent";


    private String getContainerDefaultsLSID(Container container, Domain domain)
    {
        String suffix = "Folder-" + container.getRowId();
        return (new Lsid(DOMAIN_DEFAULT_VALUE_LSID_PREFIX, suffix, domain.getName())).toString();
    }

    private String getUserDefaultsParentLSID(Container container, User user, Domain domain)
    {
        String suffix = "Folder-" + container.getRowId() + ".User-" + user.getUserId();
        return (new Lsid(USER_DEFAULT_VALUE_DOMAIN_PARENT, suffix, domain.getName())).toString();
    }

    private String getUserDefaultsLSID(Container container, User user, Domain domain, String scope)
    {
        String suffix = "Folder-" + container.getRowId() + ".User-" + user.getUserId();
        String objectId = domain.getName();
        if (scope != null)
            objectId += "." + scope;
        return (new Lsid(USER_DEFAULT_VALUE_LSID_PREFIX, suffix, objectId)).toString();
    }

    public void setDefaultValues(Container container, Map<DomainProperty, Object> values, User user) throws ExperimentException
    {
        setDefaultValues(container, values, user, null);
    }

    public void setDefaultValues(Container container, Map<DomainProperty, Object> values, User user, String scope) throws ExperimentException
    {
        if (values.isEmpty())
            return;

        // DomainProperty has a hashCode() based on its propertyId. If they were added to the map before they were
        // saved to the database, they'll be in the bucket for values with hashCode() 0 (their uninserted propertyId)
        // so we won't look them up correctly. Recreate the map to rebucket them appropriately.
        values = new HashMap<DomainProperty, Object>(values);

        assert getDomainCount(values) == 1 : "Default values must be saved one domain at a time.";
        Domain domain = values.keySet().iterator().next().getDomain();

        // we create a parent object for this domain; this allows us to delete all instances later, even if there are
        // multiple scopes under the parent.
        String parentLSID = getUserDefaultsParentLSID(container, user, domain);
        try
        {
            OntologyManager.ensureObject(container, parentLSID);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        String objectLSID = getUserDefaultsLSID(container, user, domain, scope);
        replaceObject(container, domain, objectLSID, parentLSID, values);
    }

    public void setDefaultValues(Container container, Map<DomainProperty, Object> values) throws ExperimentException
    {
        if (values.isEmpty())
            return;

        // DomainProperty has a hashCode() based on its propertyId. If they were added to the map before they were
        // saved to the database, they'll be in the bucket for values with hashCode() 0 (their uninserted propertyId)
        // so we won't look them up correctly. Recreate the map to rebucket them appropriately.
        values = new HashMap<DomainProperty, Object>(values);

        assert getDomainCount(values) == 1 : "Default values must be saved one domain at a time.";
        Domain domain = values.keySet().iterator().next().getDomain();
        // first, we validate the post:
        String objectLSID = getContainerDefaultsLSID(container, domain);
        replaceObject(container, domain, objectLSID, null, values);
    }

    private void replaceObject(Container container, Domain domain, String objectLSID, String parentLSID, Map<DomainProperty, Object> values) throws ExperimentException
    {
        try
        {
            OntologyManager.deleteOntologyObject(objectLSID, container, true);
            OntologyManager.ensureObject(container, objectLSID, parentLSID);
            List<ObjectProperty> objectProperties = new ArrayList<ObjectProperty>();

            for (DomainProperty property : domain.getProperties())
            {
                Object value = values.get(property);
                // Leave it out if it's null, which will prevent it from failing validators
                if (value != null)
                {
                    ObjectProperty prop = new ObjectProperty(objectLSID, container, property.getPropertyURI(), value,
                            property.getPropertyDescriptor().getPropertyType(), property.getName());
                    objectProperties.add(prop);
                }
            }
            OntologyManager.insertProperties(container, objectLSID, objectProperties.toArray(new ObjectProperty[objectProperties.size()]));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e);
        }
    }

    private Map<DomainProperty, Object> getObjectValues(Container container, Domain domain, String objectLSID)
    {
        try
        {
            Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(container, objectLSID);
            Map<String, DomainProperty> propertyURIToProperty = new HashMap<String, DomainProperty>();
            for (DomainProperty dp : domain.getProperties())
                propertyURIToProperty.put(dp.getPropertyDescriptor().getPropertyURI(), dp);

            Map<DomainProperty, Object> values = new HashMap<DomainProperty, Object>();
            for (Map.Entry<String, ObjectProperty> entry : properties.entrySet())
            {
                DomainProperty property = propertyURIToProperty.get(entry.getValue().getPropertyURI());
                // We won't find the domain property if it has been removed (via user edit) since we last saved default values
                if (property != null)
                {
                    Object value = entry.getValue().value();
                    if (value != null)
                        values.put(property, value);
                }
            }
            return values;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private Map<DomainProperty, Object> getMergedValues(Domain domain, Map<DomainProperty, Object> userValues, Map<DomainProperty, Object> globalValues)
    {
        if (userValues == null || userValues.isEmpty())
            return globalValues != null ? globalValues : Collections.<DomainProperty, Object>emptyMap();

        Map<DomainProperty, Object> result = new HashMap<DomainProperty, Object>();
        for (DomainProperty property : domain.getProperties())
        {
            if (property.getDefaultValueTypeEnum() == DefaultValueType.LAST_ENTERED && userValues.containsKey(property))
                result.put(property, userValues.get(property));
            else
            {
                Object value = globalValues.get(property);
                if (value != null)
                    result.put(property, value);
            }
        }
        return result;
    }

    public Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain, User user, String scope)
    {
        Map<DomainProperty, Object> userValues = null;
        Container checkContainer = container;
        if (user != null)
        {
            while (!checkContainer.isRoot() && (userValues == null || userValues.isEmpty()))
            {
                String userDefaultLSID = getUserDefaultsLSID(checkContainer, user, domain, scope);
                userValues = getObjectValues(checkContainer, domain, userDefaultLSID);
                checkContainer = checkContainer.getParent();
            }
        }

        Map<DomainProperty, Object> globalValues = null;
        checkContainer = container;
        while (!checkContainer.isRoot() && (globalValues == null || globalValues.isEmpty()))
        {
            String globalDefaultLSID = getContainerDefaultsLSID(checkContainer, domain);
            globalValues = getObjectValues(checkContainer, domain, globalDefaultLSID);
            checkContainer = checkContainer.getParent();
        }
        return getMergedValues(domain, userValues, globalValues);
    }

    public Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain, User user)
    {
        return getDefaultValues(container, domain, user, null);
    }

    public Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain)
    {
        return getDefaultValues(container, domain, null, null);
    }

    private void clearDefaultValues(Container container, String lsid)
    {
        try
        {
            OntologyManager.deleteOntologyObject(lsid, container, true);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void clearDefaultValues(Container container, Domain domain)
    {
        clearDefaultValues(container, getContainerDefaultsLSID(container, domain));
    }

    public void clearDefaultValues(Container container, Domain domain, User user)
    {
        clearDefaultValues(container, getUserDefaultsParentLSID(container, user, domain));
    }

    public void clearDefaultValues(Container container, Domain domain, User user, String scope)
    {
        clearDefaultValues(container, getUserDefaultsLSID(container, user, domain, scope));
    }

    protected int getDomainCount(Map<DomainProperty, Object> values)
    {
        Set<Domain> domains = new HashSet<Domain>();
        for (DomainProperty prop : values.keySet())
            domains.add(prop.getDomain());
        return domains.size();
    }

    private void getDefaultValueOverriders(Container currentContainer, Domain domain, List<Container> overriders)
    {
        for (Container child : currentContainer.getChildren())
        {
            String lsid = getContainerDefaultsLSID(child, domain);
            if (OntologyManager.checkObjectExistence(lsid))
                overriders.add(child);
            getDefaultValueOverriders(child, domain, overriders);
        }
    }

    public List<Container> getDefaultValueOverriders(Container currentContainer, Domain domain)
    {
        List<Container> overriders = new ArrayList<Container>();
        getDefaultValueOverriders(currentContainer, domain, overriders);
        return overriders;
    }

    private void getDefaultValueOverridees(Container currentContainer, Domain domain, List<Container> overridees)
    {
        if (currentContainer.isRoot())
            return;
        getDefaultValueOverridees(currentContainer.getParent(), domain, overridees);
        String lsid = getContainerDefaultsLSID(currentContainer, domain);
        if (OntologyManager.checkObjectExistence(lsid))
            overridees.add(currentContainer);
    }

    public List<Container> getDefaultValueOverridees(Container currentContainer, Domain domain)
    {
        List<Container> overridees = new ArrayList<Container>();
        getDefaultValueOverridees(currentContainer.getParent(), domain, overridees);
        return overridees;
    }

    public boolean hasDefaultValues(Container container, Domain domain, boolean inherit)
    {
        Container current = container;
        while ((current == container || inherit) && !current.isRoot())
        {
            String lsid = getContainerDefaultsLSID(current, domain);
            if (OntologyManager.checkObjectExistence(lsid))
                return true;
            current = current.getParent();
        }
        return false;
    }

    public boolean hasDefaultValues(Container container, Domain domain, User user, boolean inherit)
    {
        return hasDefaultValues(container, domain, user, null, inherit);
    }

    public boolean hasDefaultValues(Container container, Domain domain, User user, String scope, boolean inherit)
    {
        Container current = container;
        while ((current == container || inherit) && !current.isRoot())
        {
            String lsid = getUserDefaultsLSID(container, user, domain, scope);
            if (OntologyManager.checkObjectExistence(lsid))
                return true;
            current = current.getParent();
        }
        return false;
    }
}