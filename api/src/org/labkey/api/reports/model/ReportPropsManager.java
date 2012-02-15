package org.labkey.api.reports.model;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Feb 13, 2012
 */
public class ReportPropsManager implements ContainerManager.ContainerListener
{
    private static final Logger _log = Logger.getLogger(ReportPropsManager.class);
    private static final String PROPERTIES_DOMAIN = "Report Properties";
    private static final String NAMESPACE_PREFIX = "ReportProperties";
    private static final String TYPE_PROPERTIES = "Properties";

    private static ReportPropsManager _instance = new ReportPropsManager();

    private ReportPropsManager()
    {
        ContainerManager.addContainerListener(this);
    }

    public static ReportPropsManager get()
    {
        return _instance;
    }

    public List<DomainProperty> getProperties(Container container)
    {
        List<DomainProperty> properties = new ArrayList<DomainProperty>();
        Domain domain = getDomain(container);

        if (domain != null)
        {
            properties.addAll(Arrays.asList(domain.getProperties()));
        }
        return properties;
    }

    public void createProperty(Container container, User user, String name, String label, PropertyType type) throws Exception
    {
        ensureProperty(container, user, name, label, type);
    }

    private Map<String, DomainProperty> getPropertyMap(Container container)
    {
        Domain domain = getDomain(container);
        Map<String, DomainProperty> propsMap = new HashMap<String, DomainProperty>();

        if (domain != null)
        {
            for (DomainProperty dp : domain.getProperties())
                propsMap.put(dp.getName(), dp);
        }
        return propsMap;
    }

    public void ensureProperty(Container container, User user, String name, String label, PropertyType type) throws ChangePropertyDescriptorException
    {
        Domain domain = getDomain(container);
        if (domain != null)
        {
            boolean dirty = false;
            Map<String, DomainProperty> existingProps = getPropertyMap(container);

            if (!existingProps.containsKey(name))
            {
                dirty = true;

                DomainProperty prop = domain.addProperty();
                prop.setName(name);
                prop.setLabel(label);
                prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
                prop.setPropertyURI(getPropertyURI(name, container));
            }

            if (dirty)
                domain.save(user);
        }
    }

    public void setPropertyValue(String entityId, Container container, User user, String propertyName, Object value) throws Exception
    {
        DbScope scope = CoreSchema.getInstance().getSchema().getScope();

        try
        {
            scope.ensureTransaction();
            Map<String, DomainProperty> propMap = getPropertyMap(container);

            if (propMap.containsKey(propertyName))
            {
                DomainProperty prop = propMap.get(propertyName);
                String rowLsid = makeLsid(entityId);

                OntologyManager.deleteProperty(rowLsid, prop.getPropertyURI(), container, container);

                if (value != null)
                {
                    ObjectProperty oProp = new ObjectProperty(rowLsid, container, prop.getPropertyURI(), value, prop.getPropertyDescriptor().getPropertyType());
                    oProp.setPropertyId(prop.getPropertyId());
                    OntologyManager.insertProperties(container, rowLsid, oProp);
                }
            }
            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private String makeLsid(String entityId)
    {
        return "urn:uuid:" + entityId;
    }

    public Object getPropertyValue(String entityId, Container container, User user, String propName) throws Exception
    {
        Map<String, DomainProperty> propMap = getPropertyMap(container);

        if (propMap.containsKey(propName))
        {
            String rowLsid = makeLsid(entityId);
            DomainProperty prop = propMap.get(propName);
            Map<String, ObjectProperty> props = OntologyManager.getPropertyObjects(container, rowLsid);
            if (props.containsKey(prop.getPropertyURI()))
            {
                return props.get(prop.getPropertyURI()).getObjectValue();
            }
        }
        return null;
    }

    @Nullable
    private Domain getDomain(Container container)
    {
        try {
            String uri = getDomainURI(container);
            DomainDescriptor dd = OntologyManager.ensureDomainDescriptor(uri, PROPERTIES_DOMAIN, container);

            return PropertyService.get().getDomain(dd.getDomainId());
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    private String getDomainURI(Container container)
    {
        return new Lsid("urn:lsid:labkey.com:" + NAMESPACE_PREFIX + ".Folder-" + container.getRowId() + ':' + TYPE_PROPERTIES).toString();
    }

    private String getPropertyURI(String propertyName, Container container)
    {
        return getDomainURI(container) + '#' + propertyName;
    }

    @Override
    public void containerCreated(Container c, User user)
    {
    }

    @Override
    public void containerDeleted(Container container, User user)
    {
        String uri = getDomainURI(container);
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, container);

        if (dd != null)
        {
            Domain domain = PropertyService.get().getDomain(dd.getDomainId());
            if (domain != null)
            {
                try {
                    domain.delete(user);
                }
                catch (Exception e)
                {
                    _log.error(e);
                }
            }
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
