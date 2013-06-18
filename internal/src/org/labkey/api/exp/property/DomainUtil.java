/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.gwt.client.model.GWTConditionalFormat;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jgarms
 * Date: Aug 12, 2008
 * Time: 3:44:30 PM
 */
public class DomainUtil
{
    private DomainUtil()
    {
    }

    public static String getFormattedDefaultValue(User user, DomainProperty property, Object defaultValue)
    {
        if (defaultValue == null)
            return "[none]";
        if (defaultValue instanceof Date)
        {
            Date defaultDate = (Date) defaultValue;
            if (property.getFormat() != null)
                return DateUtil.formatDateTime(defaultDate, property.getFormat());
            else
                return DateUtil.formatDate(defaultDate);
        }
        else if (property.getLookup() != null)
        {
            Container lookupContainer = property.getLookup().getContainer();
            if (lookupContainer == null)
                lookupContainer = property.getContainer();
            UserSchema schema = QueryService.get().getUserSchema(user, lookupContainer, property.getLookup().getSchemaName());
            if (schema != null)
            {
                TableInfo table = schema.getTable(property.getLookup().getQueryName());
                if (table != null)
                {
                    List<String> pks = table.getPkColumnNames();
                    String pkCol = pks.get(0);
                    if ((pkCol.equalsIgnoreCase("container") || pkCol.equalsIgnoreCase("containerid")) && pks.size() == 2)
                        pkCol = pks.get(1);
                    if (pkCol != null)
                    {
                        ColumnInfo pkColumnInfo = table.getColumn(pkCol);
                        if (!pkColumnInfo.getClass().equals(defaultValue.getClass()))
                            defaultValue = ConvertUtils.convert(defaultValue.toString(), pkColumnInfo.getJavaClass());
                        SimpleFilter filter = new SimpleFilter(pkCol, defaultValue);
                        ResultSet rs = null;
                        try
                        {
                            rs = Table.select(table, Table.ALL_COLUMNS, filter, null);
                            if (rs.next())
                            {
                                Object value = rs.getObject(table.getTitleColumn());
                                if (value != null)
                                    return value.toString();
                            }
                        }
                        catch (SQLException e)
                        {
                            throw new RuntimeSQLException(e);
                        }
                        finally
                        {
                            if (rs != null)
                                try { rs.close(); } catch (SQLException e) { }
                        }
                    }
                }
            }
        }
        return defaultValue.toString();
    }

    @Nullable
    public static GWTDomain<GWTPropertyDescriptor> getDomainDescriptor(User user, String typeURI, Container domainContainer)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(typeURI, domainContainer);
        if (null == dd)
            return null;
        Domain domain = PropertyService.get().getDomain(dd.getDomainId());
        return getDomainDescriptor(user, domain);
    }

    @NotNull
    public static GWTDomain<GWTPropertyDescriptor> getDomainDescriptor(User user, @NotNull Domain domain)
    {
        GWTDomain<GWTPropertyDescriptor> d = getDomain(domain);

        ArrayList<GWTPropertyDescriptor> list = new ArrayList<>();

        DomainProperty[] properties = domain.getProperties();
        Map<DomainProperty, Object> defaultValues = DefaultValueService.get().getDefaultValues(domain.getContainer(), domain);

        for (DomainProperty prop : properties)
        {
            GWTPropertyDescriptor p = getPropertyDescriptor(prop);
            Object defaultValue = defaultValues.get(prop);
            String formattedDefaultValue = getFormattedDefaultValue(user, prop, defaultValue);
            p.setDefaultDisplayValue(formattedDefaultValue);
            p.setDefaultValue(ConvertUtils.convert(defaultValue));
            list.add(p);
        }

        d.setFields(list);

        // Handle reserved property names
        DomainKind domainKind = domain.getDomainKind();
        if (domainKind == null)
        {
            throw new IllegalStateException("Could not find a DomainKind for " + domain.getTypeURI());
        }
        Set<String> reservedProperties = domainKind.getReservedPropertyNames(domain);
        d.setReservedFieldNames(new HashSet<>(reservedProperties));
        d.setMandatoryFieldNames(new HashSet<>(domainKind.getMandatoryPropertyNames(domain)));

        return d;
    }

    private static GWTDomain<GWTPropertyDescriptor> getDomain(Domain dd)
    {
        GWTDomain<GWTPropertyDescriptor> gwtDomain = new GWTDomain<>();

        gwtDomain.setDomainId(dd.getTypeId());
        gwtDomain.setDomainURI(dd.getTypeURI());
        gwtDomain.setName(dd.getName());
        gwtDomain.setDescription(dd.getDescription());
        gwtDomain.setContainer(dd.getContainer().getId());

        return gwtDomain;
    }

    public static GWTPropertyDescriptor getPropertyDescriptor(DomainProperty prop)
    {
        GWTPropertyDescriptor gwtProp = new GWTPropertyDescriptor();

        gwtProp.setPropertyId(prop.getPropertyId());
        gwtProp.setDescription(prop.getDescription());
        gwtProp.setFormat(prop.getFormat());
        gwtProp.setLabel(prop.getLabel());
        gwtProp.setConceptURI(prop.getConceptURI());
        gwtProp.setName(prop.getName());
        gwtProp.setPropertyURI(prop.getPropertyURI());
        gwtProp.setContainer(prop.getContainer().getId());
        gwtProp.setRangeURI(prop.getType().getTypeURI());
        gwtProp.setRequired(prop.isRequired());
        gwtProp.setHidden(prop.isHidden());
        gwtProp.setShownInInsertView(prop.isShownInInsertView());
        gwtProp.setShownInUpdateView(prop.isShownInUpdateView());
        gwtProp.setShownInDetailsView(prop.isShownInDetailsView());
        gwtProp.setDimension(prop.isDimension());
        gwtProp.setMeasure(prop.isMeasure());
        gwtProp.setMvEnabled(prop.isMvEnabled());
        gwtProp.setFacetingBehaviorType(prop.getFacetingBehavior().name());
        gwtProp.setProtected(prop.isProtected());
        gwtProp.setExcludeFromShifting(prop.isExcludeFromShifting());
        gwtProp.setDefaultValueType(prop.getDefaultValueTypeEnum());
        gwtProp.setImportAliases(prop.getPropertyDescriptor().getImportAliases());
        StringExpression url = prop.getPropertyDescriptor().getURL();
        gwtProp.setURL(url == null ? null : url.toString());

        List<GWTPropertyValidator> validators = new ArrayList<>();
        for (IPropertyValidator pv : prop.getValidators())
        {
            GWTPropertyValidator gpv = new GWTPropertyValidator();
            Lsid lsid = new Lsid(pv.getTypeURI());

            gpv.setName(pv.getName());
            gpv.setDescription(pv.getDescription());
            gpv.setExpression(pv.getExpressionValue());
            gpv.setRowId(pv.getRowId());
            gpv.setType(PropertyValidatorType.getType(lsid.getObjectId()));
            gpv.setErrorMessage(pv.getErrorMessage());
            gpv.setProperties(new HashMap<String,String>(pv.getProperties()));

            validators.add(gpv);
        }
        gwtProp.setPropertyValidators(validators);

        List<GWTConditionalFormat> formats = new ArrayList<>();
        for (ConditionalFormat format : prop.getConditionalFormats())
        {
            formats.add(new GWTConditionalFormat(format));
        }
        gwtProp.setConditionalFormats(formats);

        if (prop.getLookup() != null)
        {
            gwtProp.setLookupContainer(prop.getLookup().getContainer() == null ? null : prop.getLookup().getContainer().getPath());
            gwtProp.setLookupQuery(prop.getLookup().getQueryName());
            gwtProp.setLookupSchema(prop.getLookup().getSchemaName());
        }                                                   
        return gwtProp;
    }

    @SuppressWarnings("unchecked")
    public static List<String> updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update, Container container, User user)
    {
        assert orig.getDomainURI().equals(update.getDomainURI());
        List<String> errors = new ArrayList<>();

        Domain d = PropertyService.get().getDomain(container, update.getDomainURI());
        if (null == d)
        {
            errors.add("Domain not found: " + update.getDomainURI());
            return errors;
        }

        if (!d.getDomainKind().canEditDefinition(user, d))
        {
            errors.add("Unauthorized");
            return errors;
        }

        // validate names
        // look for swapped names

        // first delete properties
        Set<Integer> s = new HashSet<>();
        for (GWTPropertyDescriptor pd : orig.getFields())
            s.add(pd.getPropertyId());
        for (GWTPropertyDescriptor pd : update.getFields())
        {
            String format = pd.getFormat();
            String type = "";
            try {
                if (!StringUtils.isEmpty(format))
                {
                    String ptype = pd.getRangeURI();
                    if (ptype.equalsIgnoreCase(PropertyType.DATE_TIME.getTypeUri()))
                    {
                        type = " for type " + PropertyType.DATE_TIME.getXarName();
                        FastDateFormat.getInstance(format);
                    }
                    else if (ptype.equalsIgnoreCase(PropertyType.DOUBLE.getTypeUri()))
                    {
                        type = " for type " + PropertyType.DOUBLE.getXarName();
                        new DecimalFormat(format);
                    }
                    else if (ptype.equalsIgnoreCase(PropertyType.INTEGER.getTypeUri()))
                    {
                        type = " for type " + PropertyType.INTEGER.getXarName();
                        new DecimalFormat(format);
                    }
                }
            }
            catch (IllegalArgumentException e)
            {
                errors.add(format + " is an illegal format" + type);
            }

            String url = pd.getURL();
            if (null != url)
            {
                String message;
                try
                {
                    message = StringExpressionFactory.validateURL(url);
                    if (null == message && null == StringExpressionFactory.createURL(url))
                        message = "Can't parse url: " + url;    // unexpected parse problem   
                }
                catch (Exception x)
                {
                    message = x.getMessage();
                }
                if (null != message)
                {
                    errors.add(message);
                    pd.setURL(null);    // or else _copyProperties() will blow up
                }
            }

            //Issue 15484: because the server will auto-generate MV indicator columns, which can result in naming conflicts we disallow any user-defined field w/ this suffix
            String name = pd.getName();
            if (name != null && name.toLowerCase().endsWith(OntologyManager.MV_INDICATOR_SUFFIX))
            {
                errors.add("Field name cannot end with the suffix '" + OntologyManager.MV_INDICATOR_SUFFIX + "': " + pd.getName());
            }

            s.remove(pd.getPropertyId());
        }
        for (int id : s)
        {
            if (id <= 0)
                continue;
            DomainProperty p = d.getProperty(id);
            if (null == p)
                continue;
            p.delete();
        }

        Map<DomainProperty, Object> defaultValues = new HashMap<>();

        // and now update properties
        for (GWTPropertyDescriptor pd : update.getFields())
        {
            if (pd.getPropertyId() <= 0)
                continue;
            GWTPropertyDescriptor old = null;
            for (GWTPropertyDescriptor t : orig.getFields())
            {
                if (t.getPropertyId() == pd.getPropertyId())
                {
                    old = t;
                    break;
                }
            }
            // UNDONE: DomainProperty does not support all PropertyDescriptor fields
            DomainProperty p = d.getProperty(pd.getPropertyId());
            if(p == null)
            {
                throw new RuntimeException("Column " + pd.getName() + " not found");
            }

            defaultValues.put(p, pd.getDefaultValue());

            if (old == null)
                continue;
            updatePropertyValidators(p, old, pd);
            if (old.equals(pd))
                continue;

            _copyProperties(p, pd, errors);
        }

        // Need to ensure that any new properties are given a unique PropertyURI.  See #8329
        Set<String> propertyUrisInUse = new CaseInsensitiveHashSet();

        for (GWTPropertyDescriptor pd : update.getFields())
            if (!StringUtils.isEmpty(pd.getPropertyURI()))
                propertyUrisInUse.add(pd.getPropertyURI());

        // now add properties
        for (GWTPropertyDescriptor pd : update.getFields())
        {
            addProperty(d, pd, defaultValues, propertyUrisInUse, errors);
        }

        try
        {
            if (errors.size() == 0)
            {
                // Reorder the properties based on what we got from GWT
                Map<String, DomainProperty> dps = new HashMap<>();
                for (DomainProperty dp : d.getProperties())
                {
                    dps.put(dp.getPropertyURI(), dp);
                }
                int index = 0;
                for (GWTPropertyDescriptor pd : update.getFields())
                {
                    DomainProperty dp = dps.get(pd.getPropertyURI());
                    d.setPropertyIndex(dp, index++);
                }

                d.save(user);
                // Rebucket the hash map with the real property ids
                defaultValues = new HashMap<>(defaultValues);
                try
                {
                    DefaultValueService.get().setDefaultValues(d.getContainer(), defaultValues);
                }
                catch (ExperimentException e)
                {
                    errors.add(e.getMessage());
                }
            }
        }
        catch (IllegalStateException x)
        {
            errors.add(x.getMessage());
        }
        catch (ChangePropertyDescriptorException x)
        {
            errors.add(x.getMessage() == null ? x.toString() : x.getMessage());
        }

        return errors.size() > 0 ? errors : null;
    }

    public static DomainProperty addProperty(Domain domain, GWTPropertyDescriptor pd, Map<DomainProperty, Object> defaultValues, Set<String> propertyUrisInUse, List<String> errors)
    {
        if (pd.getPropertyId() > 0)
            return null;

        if (StringUtils.isEmpty(pd.getPropertyURI()))
        {
            String newPropertyURI = createUniquePropertyURI(domain.getTypeURI() + "#" + Lsid.encodePart(pd.getName()), propertyUrisInUse);
            assert !propertyUrisInUse.contains(newPropertyURI) : "Attempting to assign an existing PropertyURI to a new property";
            pd.setPropertyURI(newPropertyURI);
            propertyUrisInUse.add(newPropertyURI);
        }

        // UNDONE: DomainProperty does not support all PropertyDescriptor fields
        DomainProperty p = domain.addProperty();
        defaultValues.put(p, pd.getDefaultValue());
        _copyProperties(p, pd, errors);
        updatePropertyValidators(p, null, pd);
        return p;
    }

    private static String createUniquePropertyURI(String base, Set<String> propertyUrisInUse)
    {
        String candidateURI = base;
        int i = 0;

        while (propertyUrisInUse.contains(candidateURI))
        {
            i++;
            candidateURI = base + i;
        }

        return candidateURI;
    }

    private static void _copyProperties(DomainProperty to, GWTPropertyDescriptor from, List<String> errors)
    {
        // avoid problems with setters that depend on rangeURI being set
        to.setRangeURI(from.getRangeURI());

        try
        {
            BeanUtils.copyProperties(to, from);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }

        if (from.getLookupQuery() != null)
        {
            String container = from.getLookupContainer();
            Container c = null;
            if (container != null)
            {
                if (GUID.isGUID(container))
                    c = ContainerManager.getForId(container);
                if (null == c)
                    c = ContainerManager.getForPath(container);
                if (c == null)
                {
                    String msg = "Container not found: " + container;
                    if (errors == null)
                        throw new RuntimeException(msg);
                    errors.add(msg);
                }
            }
            Lookup lu = new Lookup(c, from.getLookupSchema(), from.getLookupQuery());
            to.setLookup(lu);
        }
        else
        {
            to.setLookup(null);
        }

        List<ConditionalFormat> formats = new ArrayList<ConditionalFormat>();
        for (GWTConditionalFormat format : from.getConditionalFormats())
        {
            formats.add(new ConditionalFormat(format));
        }
        to.setConditionalFormats(formats);
        // If the incoming DomainProperty specifies its dimension/measure state, respect that value.  Otherwise we need
        // to infer the correct value. This is necessary for code paths like the dataset creation wizard which does not
        // (and should not, for simplicity reasons) provide the user with the option to specify dimension/measure status
        // at the time that the property descriptors are created.
        if (from.isSetDimension())
            to.setDimension(from.isDimension());
        else
            to.setDimension(ColumnRenderProperties.inferIsDimension(from.getName(), from.getLookupQuery() != null, from.isHidden()));
        if (from.isSetMeasure())
            to.setMeasure(from.isMeasure());
        else
        {
            Type type = Type.getTypeByXsdType(from.getRangeURI());
            to.setMeasure(ColumnRenderProperties.inferIsMeasure(from.getName(), from.getLabel(), type != null && type.isNumeric(),
                                                                false, from.getLookupQuery() != null, from.isHidden()));
        }

        if (from.getFacetingBehaviorType() != null)
        {
            FacetingBehaviorType type = FacetingBehaviorType.valueOf(from.getFacetingBehaviorType());
            to.setFacetingBehavior(type);
        }

        if (from.isProtected())
            to.setProtected(from.isProtected());

        if (from.isExcludeFromShifting())
            to.setExcludeFromShifting(from.isExcludeFromShifting());
    }

    @SuppressWarnings("unchecked")
    private static void updatePropertyValidators(DomainProperty dp, GWTPropertyDescriptor oldPd, GWTPropertyDescriptor newPd)
    {
        Map<Integer, GWTPropertyValidator> newProps = new HashMap<Integer, GWTPropertyValidator>();
        for (GWTPropertyValidator v : newPd.getPropertyValidators())
        {
            if (v.getRowId() != 0)
                newProps.put(v.getRowId(), v);
            else
            {
                Lsid lsid = DefaultPropertyValidator.createValidatorURI(v.getType());
                IPropertyValidator pv = PropertyService.get().createValidator(lsid.toString());

                _copyValidator(pv, v);
                dp.addValidator(pv);
            }
        }

        if (oldPd != null)
        {
            List<GWTPropertyValidator> deleted = new ArrayList<GWTPropertyValidator>();
            for (GWTPropertyValidator v : oldPd.getPropertyValidators())
            {
                GWTPropertyValidator prop = newProps.get(v.getRowId());
                if (v.equals(prop))
                    newProps.remove(v.getRowId());
                else if (prop == null)
                    deleted.add(v);
            }

            // update any new or changed
            for (IPropertyValidator pv : dp.getValidators())
                _copyValidator(pv, newProps.get(pv.getRowId()));

            // deal with removed validators
            for (GWTPropertyValidator gpv : deleted)
                dp.removeValidator(gpv.getRowId());
        }
    }

    @SuppressWarnings("unchecked")
    private static void _copyValidator(IPropertyValidator pv, GWTPropertyValidator gpv)
    {
        if (pv != null && gpv != null)
        {
            pv.setName(gpv.getName());
            pv.setDescription(gpv.getDescription());
            pv.setExpressionValue(gpv.getExpression());
            pv.setErrorMessage(gpv.getErrorMessage());

            for (Map.Entry<String, String> entry : ((Map<String, String>)gpv.getProperties()).entrySet())
            {
                pv.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
