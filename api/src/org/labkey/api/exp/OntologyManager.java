/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.exp;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.converters.BooleanConverter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.RowMap;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.gwt.client.ui.domain.CancellationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.labkey.api.search.SearchService.PROPERTY;

/**
 * User: migra
 * Date: Jun 14, 2005
 * Time: 3:23:15 PM
 */
public class OntologyManager
{
    private static final Logger _log = Logger.getLogger(OntologyManager.class);
	private static final DatabaseCache<Map<String, ObjectProperty>> mapCache = new DatabaseCache<Map<String, ObjectProperty>>(getExpSchema().getScope(), 5000, "Property maps");
	private static final DatabaseCache<Integer> objectIdCache = new DatabaseCache<Integer>(getExpSchema().getScope(), 1000, "ObjectIds");
    private static final DatabaseCache<PropertyDescriptor> propDescCache = new DatabaseCache<PropertyDescriptor>(getExpSchema().getScope(), 10000, "Property descriptors");
	private static final DatabaseCache<DomainDescriptor> domainDescCache = new DatabaseCache<DomainDescriptor>(getExpSchema().getScope(), 2000, "Domain descriptors");
	private static final DatabaseCache<List<Pair<String, Boolean>>> domainPropertiesCache = new DatabaseCache<List<Pair<String, Boolean>>>(getExpSchema().getScope(), 2000, "Domain properties");
    private static final Container _sharedContainer = ContainerManager.getSharedContainer();

    static
	{
		BeanObjectFactory.Registry.register(ObjectProperty.class, new ObjectProperty.ObjectPropertyObjectFactory());
    }


	private OntologyManager()
	{
	}


    /** @return map from PropertyURI to value */
    public static Map<String, Object> getProperties(Container container, String parentLSID) throws SQLException
	{
		Map<String, Object> m = new HashMap<String, Object>();
		Map<String, ObjectProperty> propVals = getPropertyObjects(container, parentLSID);
		if (null != propVals)
		{
			for (Map.Entry<String, ObjectProperty> entry : propVals.entrySet())
			{
				m.put(entry.getKey(), entry.getValue().value());
			}
		}

		return m;
	}

    public static final int MAX_PROPS_IN_BATCH = 1000;  // Keep this reasonably small so progress indicator is updated regularly
    public static final int UPDATE_STATS_BATCH_COUNT = 1000;

    public static List<String> insertTabDelimited(Container c, User user, @Nullable Integer ownerObjectId, ImportHelper helper, PropertyDescriptor[] descriptors, List<Map<String, Object>> rows, boolean ensureObjects) throws SQLException, ValidationException
    {
        return insertTabDelimited(c, user, ownerObjectId, helper, descriptors, rows, ensureObjects, null);
    }

    private static List<String> insertTabDelimited(Container c, User user, @Nullable Integer ownerObjectId, ImportHelper helper, PropertyDescriptor[] descriptors, List<Map<String, Object>> rows, boolean ensureObjects, Logger logger) throws SQLException, ValidationException
    {
		CPUTimer total  = new CPUTimer("insertTabDelimited");
		CPUTimer before = new CPUTimer("beforeImport");
		CPUTimer ensure = new CPUTimer("ensureObject");
		CPUTimer insert = new CPUTimer("insertProperties");

		assert total.start();
		assert getExpSchema().getScope().isTransactionActive();
		List<String> resultingLsids = new ArrayList<String>(rows.size());
        // Make sure we have enough rows to handle the overflow of the current row so we don't have to resize the list
        List<PropertyRow> propsToInsert = new ArrayList<PropertyRow>(MAX_PROPS_IN_BATCH + descriptors.length);

        ValidatorContext validatorCache = new ValidatorContext(c, user);

		try
		{
            PropertyType[] propertyTypes = new PropertyType[descriptors.length];
            for (int i = 0; i < descriptors.length; i++)
                propertyTypes[i] = descriptors[i].getPropertyType();

            OntologyObject objInsert = new OntologyObject();
			objInsert.setContainer(c);
			objInsert.setOwnerObjectId(ownerObjectId);

            List<ValidationError> errors = new ArrayList<ValidationError>();
            Map<Integer, List<? extends IPropertyValidator>> validatorMap = new HashMap<Integer, List<? extends IPropertyValidator>>();

            // cache all the property validators for this upload
            for (PropertyDescriptor pd : descriptors)
            {
                List<? extends IPropertyValidator> validators = PropertyService.get().getPropertyValidators(pd);
                if (!validators.isEmpty())
                    validatorMap.put(pd.getPropertyId(), validators);
            }

            int rowCount = 0;
            int batchCount = 0;

			for (Map<String, Object> map : rows)
			{
                // TODO: hack -- should exit and return cancellation status instead of throwing
                if (Thread.currentThread().isInterrupted())
                    throw new CancellationException();

				assert before.start();
				String lsid = helper.beforeImportObject(map);
				resultingLsids.add(lsid);
				assert before.stop();

				assert ensure.start();
				int objectId;
				if (ensureObjects)
					objectId = ensureObject(c, lsid, ownerObjectId);
				else
				{
					objInsert.setObjectURI(lsid);
					Table.insert(null, getTinfoObject(), objInsert);
					objectId = objInsert.getObjectId();
				}

				for (int i = 0; i < descriptors.length; i++)
				{
					PropertyDescriptor pd = descriptors[i];
					Object value = map.get(pd.getPropertyURI());
					if (null == value)
                    {
                        if (pd.isRequired())
                            throw new ValidationException("Missing value for required property " + pd.getName());
                        else
                        {
                            continue;
                        }
                    }
                    else
                    {
                        if (validatorMap.containsKey(pd.getPropertyId()))
                            validateProperty(validatorMap.get(pd.getPropertyId()), pd, value, errors, validatorCache);
                    }
                    try
                    {
                        PropertyRow row = new PropertyRow(objectId, pd, value, propertyTypes[i]);
                        propsToInsert.add(row);
                    }
                    catch (ConversionException e)
                    {
                        throw new ValidationException("Could not convert '" + value + "' for field " + pd.getName() + ", should be of type " + propertyTypes[i].getJavaType().getSimpleName());
                    }
				}
                assert ensure.stop();

                rowCount++;

                if (propsToInsert.size() > MAX_PROPS_IN_BATCH)
                {
                    assert insert.start();
                    insertPropertiesBulk(c, propsToInsert);
                    helper.afterBatchInsert(rowCount);
                    assert insert.stop();
                    propsToInsert = new ArrayList<PropertyRow>(MAX_PROPS_IN_BATCH + descriptors.length);

                    if (++batchCount % UPDATE_STATS_BATCH_COUNT == 0)
                    {
                        if (logger != null) logger.debug("inserted row " + rowCount + "...");
                        getExpSchema().getSqlDialect().updateStatistics(OntologyManager.getTinfoObject());
                        getExpSchema().getSqlDialect().updateStatistics(OntologyManager.getTinfoObjectProperty());
                        helper.updateStatistics(rowCount);
                        if (logger != null) logger.debug("updated statistics");
                    }
                }
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);

            assert insert.start();
			insertPropertiesBulk(c, propsToInsert);
            helper.afterBatchInsert(rowCount);
			assert insert.stop();
            if (logger != null) logger.debug("inserted row " + rowCount + ".");
		}
		catch (SQLException x)
		{
            SQLException next = x.getNextException();
            if (x instanceof java.sql.BatchUpdateException && null != next)
                x = next;
            _log.debug("Exception uploading: ", x);
			throw x;
		}

		assert total.stop();
		_log.debug("\t" + total.toString());
		_log.debug("\t" + before.toString());
		_log.debug("\t" + ensure.toString());
		_log.debug("\t" + insert.toString());

		return resultingLsids;
	}


    /**
     * As an incremental step of QueryUpdateService cleanup, this is a version of insertTabDelimited that works on a
     * tableInfo that implements UpdateableTableInfo.  Does not support ownerObjectid.
     *
     * This code is made complicated by the fact that while we are trying to move toward a TableInfo/ColumnInfo view
     * of the world, validators are attached to PropertyDescriptors.  Also, missing value handling is attached
     * to PropertyDescriptors.
     *
     * The original version of this method expects a data be be a map PropertyURI->value.  This version will also
     * accept Name->value.
     *
     * Name->Value is preferred, we are using TableInfo after all.
     */
    public static List<String> insertTabDelimited(TableInfo tableInsert, Container c, User user,
            UpdateableTableImportHelper helper,
            List<Map<String, Object>> rows,
            Logger logger)
        throws SQLException, ValidationException
    {
        return saveTabDelimited(tableInsert, c, user, helper, rows, logger, true);
    }

    public static List<String> updateTabDelimited(TableInfo tableInsert, Container c, User user,
            UpdateableTableImportHelper helper,
            List<Map<String, Object>> rows,
            Logger logger)
        throws SQLException, ValidationException
    {
        return saveTabDelimited(tableInsert, c, user, helper, rows, logger, false);
    }

    private static List<String> saveTabDelimited(TableInfo table, Container c, User user,
            UpdateableTableImportHelper helper,
            List<Map<String, Object>> rows,
            Logger logger,
            boolean insert)
        throws SQLException, ValidationException
    {
        if (!(table instanceof UpdateableTableInfo))
            throw new IllegalArgumentException();

        if (rows.isEmpty())
        {
            return Collections.emptyList();
        }

        DbScope scope = table.getSchema().getScope();
        
		assert scope.isTransactionActive();
		List<String> resultingLsids = new ArrayList<String>(rows.size());

        Domain d = table.getDomain();
        DomainProperty[] properties = null == d ? new DomainProperty[0] : d.getProperties();
        
        ValidatorContext validatorCache = new ValidatorContext(c, user);

        Connection conn = null;
        Parameter.ParameterMap parameterMap = null;

        Map<String, Object> currentRow = null;

		try
		{
            conn = scope.getConnection();
            if (insert)
            {
                parameterMap = ((UpdateableTableInfo)table).insertStatement(conn, user);
            }
            else
            {
                parameterMap = ((UpdateableTableInfo)table).updateStatement(conn, user, null);
            }
            List<ValidationError> errors = new ArrayList<ValidationError>();

            Map<String, List<? extends IPropertyValidator>> validatorMap = new HashMap<String, List<? extends IPropertyValidator>>();
            Map<String, DomainProperty> propertiesMap = new HashMap<String, DomainProperty>();

            // cache all the property validators for this upload
            for (DomainProperty dp : properties)
            {
                propertiesMap.put(dp.getPropertyURI(), dp);
                List<? extends IPropertyValidator> validators = dp.getValidators();
                if (!validators.isEmpty())
                    validatorMap.put(dp.getPropertyURI(), validators);
            }

            ColumnInfo[] columns = table.getColumns().toArray(new ColumnInfo[table.getColumns().size()]);
            PropertyType[] propertyTypes = new PropertyType[columns.length];
            for (int i = 0; i < columns.length; i++)
            {
                String propertyURI = columns[i].getPropertyURI();
                DomainProperty dp = null==propertyURI ? null : propertiesMap.get(propertyURI);
                PropertyDescriptor pd = null==dp ? null : dp.getPropertyDescriptor();
                if (null != pd)
                    propertyTypes[i] = pd.getPropertyType();
            }

            int rowCount = 0;

			for (Map<String, Object> map : rows)
			{
                currentRow = new CaseInsensitiveHashMap<Object>(map);
                
                // TODO: hack -- should exit and return cancellation status instead of throwing
                if (Thread.currentThread().isInterrupted())
                    throw new CancellationException();

                parameterMap.clearParameters();

				String lsid = helper.beforeImportObject(currentRow);
				resultingLsids.add(lsid);

                //
                // NOTE we validate based on columninfo/propertydescriptor
                // However, we bind by name, and there may be parameters that do not correspond to columninfo
                //

                for (int i = 0; i < columns.length; i++)
				{
                    ColumnInfo col = columns[i];
                    if (col.isMvIndicatorColumn() || col.isRawValueColumn()) //TODO col.isNotUpdatableForSomeReasonSoContinue()
                        continue;
                    String propertyURI = col.getPropertyURI();
                    DomainProperty dp = null==propertyURI ? null : propertiesMap.get(propertyURI);
                    PropertyDescriptor pd = null==dp ? null : dp.getPropertyDescriptor();

					Object value = currentRow.get(col.getName());
                    if (null == value)
                        value = currentRow.get(propertyURI);

					if (null == value)
                    {
                        // TODO col.isNullable() doesn't seem to work here
                        if (null != pd && pd.isRequired())
                            throw new ValidationException("Missing value for required property " + col.getName());
                    }
                    else
                    {
                        // TODO does validateProperty handle MvFieldWrapper?
                        if (null != pd && validatorMap.containsKey(propertyURI))
                        {
                            validateProperty(validatorMap.get(propertyURI), pd, value, errors, validatorCache);
                        }
                    }
                    try
                    {
                        String key = col.getName();
                        if (!parameterMap.containsKey(key))
                            key = propertyURI;
                        if (null == propertyTypes[i])
                        {
                            // some built-in columns won't have parameters (createdby, etc)
                            if (parameterMap.containsKey(key))
                            {
                                assert !(value instanceof MvFieldWrapper);
                                // Handle type coercion for these built-in columns as well, though we don't need to
                                // worry about missing values
                                value = PropertyType.getFromClass(col.getJavaObjectClass()).convert(value);
                                parameterMap.put(key, value);
                            }
                        }
                        else
                        {
                            Pair<Object,String> p = new Pair<Object,String>(value,null);
                            convertValuePair(pd, propertyTypes[i], p);
                            parameterMap.put(key, p.first);
                            if (null != p.second)
                            {
                                FieldKey mvName = col.getMvColumnName();
                                if (mvName != null)
                                {
                                    parameterMap.put(mvName.getName(), p.second);
                                }
                            }
                        }
                    }
                    catch (ConversionException e)
                    {
                        throw new ValidationException("Could not convert '" + value + "' for field " + pd.getName() + ", should be of type " + propertyTypes[i].getJavaType().getSimpleName());
                    }
				}
                
                helper.bindAdditionalParameters(currentRow, parameterMap);
                parameterMap.execute();
                helper.afterImportObject(currentRow);
                rowCount++;
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);

            helper.afterBatchInsert(rowCount);
            if (logger != null)
                logger.debug("inserted row " + rowCount + ".");
		}
		catch (SQLException x)
		{
            SQLException next = x.getNextException();
            if (x instanceof java.sql.BatchUpdateException && null != next)
                x = next;
            _log.debug("Exception uploading: ", x);
            if (null != currentRow)
                _log.debug(currentRow.toString());
			throw x;
		}
        finally
        {
            if (null != parameterMap)
                parameterMap.close();
            if (null != conn)
                scope.releaseConnection(conn);
        }

		return resultingLsids;
	}
    

    public static boolean validateProperty(List<? extends IPropertyValidator> validators, PropertyDescriptor prop, Object value,
            List<ValidationError> errors, ValidatorContext validatorCache)
    {
        boolean ret = true;

        if (prop.isRequired() && value == null)
        {
            errors.add(new PropertyValidationError("Field is required", prop.getName()));
            ret = false;
        }

        if (validators != null)
        {
            for (IPropertyValidator validator : validators)
                if (!validator.validate(prop, value, errors, validatorCache)) ret = false;
        }
        return ret;
    }

    public interface ImportHelper
	{
		/**
         * may modify map
         * @return LSID for new or existing Object
         */
		String beforeImportObject(Map<String, Object> map) throws SQLException;

		void afterBatchInsert(int currentRow) throws SQLException;

        void updateStatistics(int currentRow) throws SQLException;
    }


    public interface UpdateableTableImportHelper extends ImportHelper
    {
        /**
         * may be used to process attachments, for auditing, etc etc
         */
        void afterImportObject(Map<String, Object> map) throws SQLException;

        /** may set parameters directly for columns that are not exposed by tableinfo
         * e.g. "_key"
         * 
         * TODO maybe this can be handled declaratively? see UpdateableTableInfo
         */
        void bindAdditionalParameters(Map<String, Object> map, Parameter.ParameterMap target);
    }


	/**
	 * Get the name for a property. First check name attached to the property
	 * then see if we have a descriptor for the property and load that...
     *
     * Canonical propertyURI:   <vocabulary>#<domainName>.<propertyName>
     *
	 */
	public static String getPropertyName(String propertyURI, Container c)
	{
		PropertyDescriptor pd = getPropertyDescriptor(propertyURI, c);
		if (null != pd)
			return pd.getName();
		else
		{
            //TODO:   verify that this default property name scheme is consistent with major ontologies
            int i = Math.max(propertyURI.lastIndexOf(':'), propertyURI.lastIndexOf('#'));
            i = Math.max(propertyURI.lastIndexOf('.'), i);
            if (i != -1)
				return propertyURI.substring(i + 1);
			return propertyURI;
		}
	}

    /**
     * @return map from PropertyURI to ObjectProperty
     */
    public static Map<String, ObjectProperty> getPropertyObjects(Container container, String objectLSID) throws SQLException
	{
		Map<String, ObjectProperty> m = mapCache.get(objectLSID);
		if (null != m)
			return m;
		try
		{
			m = getObjectPropertiesFromDb(container, objectLSID);
			mapCache.put(objectLSID, m);
			return m;
		}
		catch (SQLException x)
		{
			_log.error("Loading property values for: " + objectLSID, x);
			throw x;
		}
	}

    
    private static Map<String, ObjectProperty> getObjectPropertiesFromDb(Container container, String parentURI) throws SQLException
	{
		return getObjectPropertiesFromDb(container, new SimpleFilter("ObjectURI", parentURI));
    }

    private static Map<String, ObjectProperty> getObjectPropertiesFromDb(Container container, SimpleFilter filter) throws SQLException
    {
        if (container != null)
        {
            filter.addCondition("Container", container.getId());
        }
        ObjectProperty[] pvals = Table.select(getTinfoObjectPropertiesView(), Table.ALL_COLUMNS, filter, null, ObjectProperty.class);
		Map<String, ObjectProperty> m = new HashMap<String, ObjectProperty>();
		for (ObjectProperty value : pvals)
		{
			m.put(value.getPropertyURI(), value);
		}

		return Collections.unmodifiableMap(m);
	}


	public static int ensureObject(Container container, String objectURI) throws SQLException
	{
		return ensureObject(container, objectURI, (Integer) null);
	}

	public static int ensureObject(Container container, String objectURI, String ownerURI) throws SQLException
	{
		Integer ownerId = null;
		if (null != ownerURI)
			ownerId = ensureObject(container, ownerURI, (Integer) null);
		return ensureObject(container, objectURI, ownerId);
	}

    public static int ensureObject(Container container, String objectURI, Integer ownerId) throws SQLException
	{
		//TODO: (marki) Transact?
		Integer i = objectIdCache.get(objectURI);
		if (null != i)
			return i.intValue();

		OntologyObject o = getOntologyObject(container, objectURI);
		if (null == o)
		{
			o = new OntologyObject();
			o.setContainer(container);
			o.setObjectURI(objectURI);
			o.setOwnerObjectId(ownerId);
			o = Table.insert(null, getTinfoObject(), o);
		}

		objectIdCache.put(objectURI, o.getObjectId());
		return o.getObjectId();
	}


	public static OntologyObject getOntologyObject(Container container, String uri) throws SQLException
	{
		SimpleFilter filter = new SimpleFilter("ObjectURI", uri);
        if (container != null)
        {
            filter.addCondition("Container", container.getId());
        }
        return Table.selectObject(getTinfoObject(), filter, null, OntologyObject.class);
	}


    // UNDONE: optimize (see deleteOntologyObjects(Integer[])
    public static void deleteOntologyObjects(Container c, String... uris) throws SQLException
    {
        if (uris.length == 0)
            return;

        try
        {
            DbSchema schema = getExpSchema();
            String sql = getSqlDialect().execute(getExpSchema(), "deleteObject", "?, ?");
            Object[] params = new Object[] {c.getId(), null};
            for (String uri : uris)
            {
                params[1] = uri;
                Table.execute(schema, sql, params);
            }
        }
        finally
        {
            mapCache.clear();
            objectIdCache.clear();
        }
    }


    public static void deleteOntologyObjects(DbSchema schema, SQLFragment sub, Container c, boolean deleteOwnedObjects) throws SQLException
    {
        // we have different levels of optimization possible here deleteOwned=true/false, scope=/<>exp

        // let's handle one case
        if (!schema.getScope().equals(getExpSchema().getScope()))
            throw new UnsupportedOperationException("can only use with same DbScope");

        // CONSIDER: use temp table for objectids?

        if (deleteOwnedObjects)
        {
            throw new UnsupportedOperationException("Don't do this yet either");
        }
        else
        {
            SQLFragment sqlDeleteProperties = new SQLFragment();
            sqlDeleteProperties.append("DELETE FROM " + getTinfoObjectProperty() + " WHERE ObjectId IN\n" +
                    "(SELECT ObjectId FROM " + getTinfoObject() + "\n" +
                    " WHERE Container = ? AND ObjectURI IN (");
            sqlDeleteProperties.add(c.getId());
            sqlDeleteProperties.append(sub);
            sqlDeleteProperties.append("))");
            Table.execute(getExpSchema(), sqlDeleteProperties);

            SQLFragment sqlDeleteObjects = new SQLFragment();
            sqlDeleteObjects.append("DELETE FROM " + getTinfoObject() + " WHERE Container = ? AND ObjectURI IN (");
            sqlDeleteObjects.add(c.getId());
            sqlDeleteObjects.append(sub);
            sqlDeleteObjects.append(")");
            Table.execute(getExpSchema(), sqlDeleteObjects);
        }

        // fall back implementation
//        SQLFragment selectObjectIds = new SQLFragment();
//        selectObjectIds.append("SELECT ObjectId FROM exp.Object WHERE ObjectURI IN (");
//        selectObjectIds.append(sub);
//        selectObjectIds.append(")");
//        Integer[] objectIds = Table.executeArray(schema, selectObjectIds, Integer.class);
//        deleteOntologyObjects(objectIds, c, deleteOwnedObjects);
    }


    public static void deleteOntologyObjects(Integer[] objectIds, Container c, boolean deleteOwnedObjects) throws SQLException
    {
        deleteOntologyObjects(objectIds, c, deleteOwnedObjects, true);
    }

    private static void deleteOntologyObjects(Integer[] objectIds, Container c, boolean deleteOwnedObjects, boolean deleteObjects) throws SQLException
    {
        if (objectIds.length == 0)
            return;

        try
        {
            // if uris is long, split it up
            if (objectIds.length > 1000)
            {
                int countBatches = objectIds.length/1000;
                int lenBatch = 1+objectIds.length/(countBatches+1);
                List<Integer> sub = new ArrayList<Integer>(lenBatch);

                for (int s = 0; s < objectIds.length; s += lenBatch)
                {
                    int end = Math.min(s + lenBatch, objectIds.length);
                    sub.clear();
                    sub.addAll(Arrays.asList(objectIds).subList(s, end));
                    deleteOntologyObjects(sub.toArray(new Integer[sub.size()]), c, deleteOwnedObjects, deleteObjects);
                }

                return;
            }

            StringBuilder in = new StringBuilder();

            for (Integer objectId : objectIds)
            {
                in.append(objectId);
                in.append(", ");
            }

            in.setLength(in.length() - 2);

            if (deleteOwnedObjects)
            {
                // NOTE: owned objects should never be in a different container than the owner, that would be a problem
                StringBuilder sqlDeleteOwnedProperties = new StringBuilder();
                sqlDeleteOwnedProperties.append("DELETE FROM ").append(getTinfoObjectProperty()).append(" WHERE ObjectId IN (SELECT ObjectId FROM ").append(getTinfoObject()).append(" WHERE Container = '").append(c.getId()).append("' AND OwnerObjectId IN (");
                sqlDeleteOwnedProperties.append(in);
                sqlDeleteOwnedProperties.append("))");
                Table.execute(getExpSchema(), sqlDeleteOwnedProperties.toString());

                StringBuilder sqlDeleteOwnedObjects = new StringBuilder();
                sqlDeleteOwnedObjects.append("DELETE FROM ").append(getTinfoObject()).append(" WHERE Container = '").append(c.getId()).append("' AND OwnerObjectId IN (");
                sqlDeleteOwnedObjects.append(in);
                sqlDeleteOwnedObjects.append(")");
                Table.execute(getExpSchema(), sqlDeleteOwnedObjects.toString());
            }

            if (deleteObjects)
            {
                deleteProperties(objectIds, c);

                StringBuilder sqlDeleteObjects = new StringBuilder();
                sqlDeleteObjects.append("DELETE FROM ").append(getTinfoObject()).append(" WHERE Container = '").append(c.getId()).append("' AND ObjectId IN (");
                sqlDeleteObjects.append(in);
                sqlDeleteObjects.append(")");
                Table.execute(getExpSchema(), sqlDeleteObjects.toString());
            }
        }
        finally
        {
            mapCache.clear();
            objectIdCache.clear();
        }
    }


    public static void deleteOntologyObject(String objectURI, Container container, boolean deleteOwnedObjects) throws SQLException
	{
        OntologyObject ontologyObject = getOntologyObject(container, objectURI);

        if (null != ontologyObject)
        {
            Integer objid = ontologyObject.getObjectId();
            deleteOntologyObjects(new Integer[]{objid}, container, deleteOwnedObjects, true);
        }
    }


    public static OntologyObject getOntologyObject(int id)
    {
        return Table.selectObject(getTinfoObject(), id, OntologyObject.class);
    }

    //todo:  review this.  this doesn't delete the underlying data objects.  should it?
    public static void deleteObjectsOfType(String domainURI, Container container) throws SQLException
    {
        DomainDescriptor dd = null;
        if (null!= domainURI)
            dd = getDomainDescriptor(domainURI, container);
        if (null==dd)
        {
            _log.debug("deleteObjectsOfType called on type not found in database:  " + domainURI );
            return;
        }

        try
        {
            getExpSchema().getScope().ensureTransaction();

            // until we set a domain on objects themselves, we need to create a list of objects to
            // delete based on existing entries in ObjectProperties before we delete the objectProperties
            // which we need to do before we delete the objects
            String selectObjectsToDelete = "SELECT DISTINCT O.ObjectId " +
                    " FROM " + getTinfoObject() + " O " +
                    " INNER JOIN " + getTinfoObjectProperty() + " OP ON(O.ObjectId = OP.ObjectId) " +
                    " INNER JOIN " + getTinfoPropertyDomain() + " PDM ON (OP.PropertyId = PDM.PropertyId) " +
                    " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (PDM.DomainId = DD.DomainId) " +
                    " INNER JOIN " + getTinfoPropertyDescriptor() + " PD ON (PD.PropertyId = PDM.PropertyId) " +
                    " WHERE DD.DomainId = " + dd.getDomainId() +
                    " AND PD.Container = DD.Container";
            Integer[] objIdsToDelete = Table.executeArray(getExpSchema(), selectObjectsToDelete, new Object[]{}, Integer.class);

            String sep;
            StringBuilder sqlIN=null;
            Integer[] ownerObjIds=null;

            if (objIdsToDelete.length > 0)
            {
                //also need list of owner objects whose subobjects are going to be deleted
                // Seems cheaper but less correct to delete the subobjects then cleanup any owner objects with no children
                sep = "";
                sqlIN = new StringBuilder();
                for (Integer id : objIdsToDelete)
                {
                    sqlIN.append(sep).append(id);
                    sep = ", ";
                }

                String selectOwnerObjects = "SELECT O.ObjectId FROM " + getTinfoObject() + " O " +
                        " WHERE ObjectId IN " +
                        " (SELECT DISTINCT SUBO.OwnerObjectId FROM " + getTinfoObject() + " SUBO " +
                        " WHERE SUBO.ObjectId IN ( " + sqlIN.toString() + " ) )";

                ownerObjIds = Table.executeArray(getExpSchema(), selectOwnerObjects, new Object[]{}, Integer.class);
            }

            String deleteTypePropsSql = "DELETE FROM " + getTinfoObjectProperty() +
                    " WHERE PropertyId IN " +
                    " (SELECT PDM.PropertyId FROM " + getTinfoPropertyDomain() + " PDM " +
                    " INNER JOIN " + getTinfoPropertyDescriptor() + " PD ON (PDM.PropertyId = PD.PropertyId) " +
                    " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (PDM.DomainId = DD.DomainId) " +
                    " WHERE DD.DomainId = " + dd.getDomainId() +
                    " AND PD.Container = DD.Container " +
                    " ) ";
            Table.execute(getExpSchema(), deleteTypePropsSql);

            if (objIdsToDelete.length > 0)
            {
                // now cleanup the object table entries from the list we made, but make sure they don't have
                // other properties attached to them
                String deleteObjSql = "DELETE FROM " + getTinfoObject() +
                        " WHERE ObjectId IN ( " + sqlIN.toString() + " ) " +
                        " AND NOT EXISTS (SELECT * FROM " + getTinfoObjectProperty() + " OP " +
                        " WHERE  OP.ObjectId = " + getTinfoObject() + ".ObjectId)";
                Table.execute(getExpSchema(), deleteObjSql);

                if (ownerObjIds.length>0)
                {
                    sep="";
                    sqlIN = new StringBuilder();
                    for (Integer id : ownerObjIds)
                    {
                        sqlIN.append(sep).append(id);
                        sep = ", ";
                    }
                    String deleteOwnerSql = "DELETE FROM " + getTinfoObject() +
                            " WHERE ObjectId IN ( " + sqlIN.toString() + " ) " +
                            " AND NOT EXISTS (SELECT * FROM " + getTinfoObject() + " SUBO " +
                            " WHERE SUBO.OwnerObjectId = " + getTinfoObject() + ".ObjectId)";
                    Table.execute(getExpSchema(), deleteOwnerSql);
                }
            }
            // whew!
            clearCaches();
            getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            getExpSchema().getScope().closeConnection();
        }
    }

    public static void deleteDomain(String domainURI, Container container) throws DomainNotFoundException
    {
        DomainDescriptor dd = getDomainDescriptor(domainURI, container);
        String msg;

        if (null == dd)
            throw new DomainNotFoundException(domainURI);
        
        if (!dd.getContainer().getId().equals(container.getId()))
        {
            // this domain was not created in this folder. Allow if in the project-level root
            if (!dd.getProject().getId().equals(container.getId()))
            {
                msg = "DeleteDomain: Domain can only be deleted in original container or from the project root "
                        + "\nDomain: " + domainURI + " project "+ dd.getProject().getName() + " original container " + dd.getContainer().getPath();
                _log.error(msg);
                throw new RuntimeException(msg);
            }
        }
        try
        {
            getExpSchema().getScope().ensureTransaction();

            String selectPDsToDelete = "SELECT DISTINCT PDM.PropertyId " +
                            " FROM " + getTinfoPropertyDomain() + " PDM " +
                            " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (PDM.DomainId = DD.DomainId) " +
                            " WHERE DD.DomainId = ? ";

            Integer[] pdIdsToDelete = Table.executeArray(getExpSchema(), selectPDsToDelete, new Object[]{dd.getDomainId()}, Integer.class);

            String deletePDMs = "DELETE FROM " + getTinfoPropertyDomain() +
                    " WHERE DomainId =  " +
                    " (SELECT DD.DomainId FROM " + getTinfoDomainDescriptor() + " DD "+
                    " WHERE DD.DomainId = ? )";
            Table.execute(getExpSchema(), deletePDMs, dd.getDomainId());

            if (pdIdsToDelete.length > 0)
            {
                String sep = "";
                StringBuilder sqlIN = new StringBuilder();
                for (Integer id : pdIdsToDelete)
                {
                    PropertyService.get().deleteValidatorsAndFormats(id);

                    sqlIN.append(sep);
                    sqlIN.append(id);
                    sep = ", ";
                }

                String deletePDs = "DELETE FROM " + getTinfoPropertyDescriptor() +
                            " WHERE PropertyId IN ( " + sqlIN.toString() + " ) " +
                            "AND Container = ? " +
                            "AND NOT EXISTS (SELECT * FROM " + getTinfoObjectProperty() + " OP " +
                                "WHERE OP.PropertyId = " + getTinfoPropertyDescriptor() + ".PropertyId) " +
                            "AND NOT EXISTS (SELECT * FROM " + getTinfoPropertyDomain() + " PDM " +
                                "WHERE PDM.PropertyId = " + getTinfoPropertyDescriptor() + ".PropertyId)";

                Table.execute(getExpSchema(), deletePDs, dd.getContainer().getId());
            }

            String deleteDD = "DELETE FROM " + getTinfoDomainDescriptor() +
                        " WHERE DomainId = ? " +
                        "AND NOT EXISTS (SELECT * FROM " + getTinfoPropertyDomain() + " PDM " +
                            "WHERE PDM.DomainId = " + getTinfoDomainDescriptor() + ".DomainId)";

            Table.execute(getExpSchema(), deleteDD, dd.getDomainId());
            clearCaches();

            getExpSchema().getScope().commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            getExpSchema().getScope().closeConnection();
        }
    }


    public static void deleteAllObjects(Container c, User user) throws SQLException
	{
        String containerid = c.getId();
        Container projectContainer = c.getProject();
        if (null==projectContainer)
                projectContainer = c;

        try
		{
            getExpSchema().getScope().ensureTransaction();
            if (!c.equals(projectContainer))
            {
                copyDescriptors(c, projectContainer);
            }

            // Owned objects should be in same container, so this should work
			String deleteObjPropSql = "DELETE FROM " + getTinfoObjectProperty() + " WHERE  ObjectId IN (SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = ?)";
			Table.execute(getExpSchema(), deleteObjPropSql, containerid);
			String deleteIndexIntegerSql = "DELETE FROM " + getTinfoIndexInteger() + " WHERE  ObjectId IN (SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = ?)";
			Table.execute(getExpSchema(), deleteIndexIntegerSql, containerid);
			String deleteIndexVarcharSql = "DELETE FROM " + getTinfoIndexVarchar() + " WHERE  ObjectId IN (SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = ?)";
			Table.execute(getExpSchema(), deleteIndexVarcharSql, containerid);
			String deleteObjSql = "DELETE FROM " + getTinfoObject() + " WHERE Container = ?";
			Table.execute(getExpSchema(), deleteObjSql, containerid);

            // delete property validator references on property descriptors
            PropertyService.get().deleteValidatorsAndFormats(c);

            // Drop tables directly and allow bulk delete calls below to clean up rows in exp.propertydescriptor,
            // exp.domaindescriptor, etc
            String selectSQL = "SELECT * FROM " + getTinfoDomainDescriptor() + " WHERE Container = ?";
            DomainDescriptor[] dds = Table.executeQuery(getExpSchema(), selectSQL, new Object[] { c.getId() }, DomainDescriptor.class);
            for (DomainDescriptor dd : dds)
            {
                StorageProvisioner.drop(PropertyService.get().getDomain(dd.getDomainId()));
            }

            String deletePropDomSqlPD = "DELETE FROM " + getTinfoPropertyDomain() + " WHERE PropertyId IN (SELECT PropertyId FROM " + getTinfoPropertyDescriptor() + " WHERE Container = ?)";
            Table.execute(getExpSchema(), deletePropDomSqlPD, containerid);
            String deletePropDomSqlDD = "DELETE FROM " + getTinfoPropertyDomain() + " WHERE DomainId IN (SELECT DomainId FROM " + getTinfoDomainDescriptor() + " WHERE Container = ?)";
            Table.execute(getExpSchema(), deletePropDomSqlDD, containerid);
            String deleteDomSql = "DELETE FROM " + getTinfoDomainDescriptor() + " WHERE Container = ?";
            Table.execute(getExpSchema(), deleteDomSql, containerid);
            // now delete the prop descriptors that are referenced in this container only
            String deletePropSql = "DELETE FROM " + getTinfoPropertyDescriptor() + " WHERE Container = ?";
            Table.execute(getExpSchema(), deletePropSql, containerid);

			clearCaches();
            getExpSchema().getScope().commitTransaction();
        }
		finally
		{
            getExpSchema().getScope().closeConnection();
		}
	}

    public static void copyDescriptors (Container c, Container project) throws SQLException
    {
        // if c is (was) a project, then nothing to do
        if (c.getId().equals(project.getId()))
            return;

        // check to see if any Properties defined in this folder are used in other folders.
        // if so we will make a copy of all PDs and DDs to ensure no orphans
        String sql = " SELECT O.ObjectURI, O.Container, PD.PropertyId, PD.PropertyURI  " +
                " FROM " + getTinfoPropertyDescriptor() + " PD " +
                " INNER JOIN " + getTinfoObjectProperty() + " OP ON PD.PropertyId = OP.PropertyId" +
                " INNER JOIN " + getTinfoObject() + " O ON (O.ObjectId = OP.ObjectId) " +
                " WHERE PD.Container = ? " +
                " AND O.Container <> PD.Container ";
//                " GROUP BY O.ObjectURI, O.Container, PD.PropertyId ";
        ResultSet rsObjsUsingMyProps = null;
        ResultSet rsMyProps=null;

        try {
            rsObjsUsingMyProps = Table.executeQuery(getExpSchema(), sql, new Object[]{c.getId()});
            Map<String, ObjectProperty> mObjsUsingMyProps = new HashMap<String, ObjectProperty>();
            String sqlIn="";
            String sep="";
            String objURI;
            String propURI;
            String objContainer;
            Integer propId;
            while (rsObjsUsingMyProps.next())
            {
                objURI = rsObjsUsingMyProps.getString(1);
                objContainer = rsObjsUsingMyProps.getString(2);
                propId = rsObjsUsingMyProps.getInt(3);
                propURI = rsObjsUsingMyProps.getString(4);

                sqlIn += sep + propId ;
                sep = ", ";
                Map<String,ObjectProperty> mtemp = getPropertyObjects(ContainerManager.getForId(objContainer), objURI);
                if (null != mtemp)
                {
                    for (Map.Entry<String, ObjectProperty> entry : mtemp.entrySet())
                    {
                        if (entry.getValue().getPropertyURI().equals(propURI))
                            mObjsUsingMyProps.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            //For each property that is referenced outside its container, get the
            // domains that it belongs to and the other properties in those domains
            // so we can make copies of those domains and properties
            // Restrict it to properties and domains also in the same container

            if (mObjsUsingMyProps.size() > 0)
            {
                sql = "SELECT PD.PropertyURI, DD.DomainURI " +
                        " FROM " + getTinfoPropertyDescriptor() + " PD " +
                        " LEFT JOIN (" + getTinfoPropertyDomain() + " PDM " +
                        " INNER JOIN " + getTinfoPropertyDomain() + " PDM2 ON (PDM.DomainId = PDM2.DomainId) " +
                        " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId)) "+
                        " ON (PD.PropertyId = PDM2.PropertyId) " +
                        " WHERE PDM.PropertyId IN (" + sqlIn + " ) " +
                        " OR PD.PropertyId IN (" + sqlIn + " ) ";

                rsMyProps = Table.executeQuery(getExpSchema(), sql, new Object[0]);
            }
            String propUri;
            String domUri;
            if (null!=rsMyProps)
            {
                clearCaches();
                while (rsMyProps.next())
                {
                    propUri = rsMyProps.getString(1);
                    domUri =  rsMyProps.getString(2);
                    PropertyDescriptor pd = getPropertyDescriptor(propUri, c);
                    if (pd.getContainer().getId().equals(c.getId()))
                    {
                        propDescCache.remove(getCacheKey(pd));
                        domainPropertiesCache.clear();
                        pd.setContainer(project);
                        pd.setProject(project);
                        pd.setPropertyId(0);
                        pd = ensurePropertyDescriptor(pd);
                    }
                    if (null !=domUri)
                    {
                        DomainDescriptor dd = getDomainDescriptor(domUri, c);
                        if (dd.getContainer().getId().equals(c.getId()))
                        {
                            domainDescCache.remove(getCacheKey(dd));
                            domainPropertiesCache.clear();
                            dd.setContainer(project);
                            dd.setProject(project);
                            dd.setDomainId(0);
                            dd = ensureDomainDescriptor(dd);
                            ensurePropertyDomain(pd, dd);
                        }
                    }
                }
                // now unhook the objects that refer to my properties and rehook them to the properties in their own project
                for (ObjectProperty op : mObjsUsingMyProps.values())
                {
                    deleteProperty(op.getObjectURI(), op.getPropertyURI(), op.getContainer(), c);
                    insertProperties(op.getContainer(), op.getObjectURI(), op);
                }
            }
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
        finally
        {
            if (null != rsObjsUsingMyProps) rsObjsUsingMyProps.close();
            if (null != rsMyProps) rsMyProps.close();
        }
    }


    public static void moveContainer(Container c, Container oldParent, Container newParent) throws SQLException
    {

        Container oldProject=c;
        Container newProject=c;

        if (null!=oldParent)
        {
            oldProject = oldParent.getProject();
        }
        if (null!=newParent)
        {
            newProject = newParent.getProject();
            if (null==newProject) // if container is promoted to a project
                newProject= c.getProject();
        }


        if ((null!=oldProject) && oldProject.getId().equals(newProject.getId()))
        {
            //the folder is being moved within the same project.  No problems here
            return;
        }

        String objURI;
        Integer propId;
        String propURI;
        String sql;
        ResultSet rsMyObjsThatRefProjProps=null;
        ResultSet rsPropsRefdByMe=null;

        try
        {
            getExpSchema().getScope().ensureTransaction();

            clearCaches();

            // update project of any descriptors in folder just moved
            sql = " UPDATE " + getTinfoPropertyDescriptor() + " SET Project = ? WHERE Container = ? ";
            Table.execute(getExpSchema(), sql , newProject.getId(), c.getId());
            sql = " UPDATE " + getTinfoDomainDescriptor() + " SET Project = ? WHERE Container = ? ";
            Table.execute(getExpSchema(), sql , newProject.getId(), c.getId());

            if (null==oldProject) // if container was a project & demoted I'm done
            {
                getExpSchema().getScope().commitTransaction();
                return;
            }

            // this method makes sure I'm not getting rid of descriptors used by another folder
            // it is shared by ContainerDelete
            copyDescriptors(c, oldProject);

            // if my objects refer to project-scoped properties I need a copy of those properties
            sql = " SELECT O.ObjectURI, PD.PropertyURI, PD.PropertyId  " +
                    " FROM " + getTinfoPropertyDescriptor() + " PD " +
                    " INNER JOIN " + getTinfoObjectProperty() + " OP ON PD.PropertyId = OP.PropertyId" +
                    " INNER JOIN " + getTinfoObject() + " O ON (O.ObjectId = OP.ObjectId) " +
                    " WHERE O.Container = ? " +
                    " AND O.Container <> PD.Container " +
                    " AND PD.Project <> ? ";
            rsMyObjsThatRefProjProps = Table.executeQuery(getExpSchema(), sql, new Object[]{c.getId(), _sharedContainer.getId()});

            Map<String, ObjectProperty> mMyObjsThatRefProjProps  = new HashMap<String, ObjectProperty>();
            String sqlIn="";
            String sep="";

            while (rsMyObjsThatRefProjProps.next())
            {
                objURI = rsMyObjsThatRefProjProps.getString(1);
                propURI = rsMyObjsThatRefProjProps.getString(2);
                    propId = rsMyObjsThatRefProjProps.getInt(3);

                sqlIn += sep + propId;
                sep = ", ";
                Map<String,ObjectProperty> mtemp = getPropertyObjects(c, objURI);
                if (null != mtemp)
                {
                    for (Map.Entry<String, ObjectProperty> entry : mtemp.entrySet())
                    {
                        if (entry.getValue().getPropertyURI().equals(propURI))
                            mMyObjsThatRefProjProps.put(entry.getKey(), entry.getValue());
                    }
                }

            }

            // this sql gets all properties i ref and the domains they belong to and the
            // other properties in those domains
            //todo  what about materialsource ?
            if (mMyObjsThatRefProjProps.size() > 0)
            {
                sql = "SELECT PD.PropertyURI, DD.DomainURI " +
                        " FROM " + getTinfoPropertyDescriptor() + " PD " +
                        " LEFT JOIN (" + getTinfoPropertyDomain() + " PDM " +
                        " INNER JOIN " + getTinfoPropertyDomain() + " PDM2 ON (PDM.DomainId = PDM2.DomainId) " +
                        " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId)) "+
                        " ON (PD.PropertyId = PDM2.PropertyId) " +
                        " WHERE PDM.PropertyId IN (" + sqlIn + " ) ";

                rsPropsRefdByMe = Table.executeQuery(getExpSchema(), sql, new Object[0]);
            }

            String propUri;
            String domUri;

            if (null !=rsPropsRefdByMe)
            {
                while (rsPropsRefdByMe.next())
                {
                    propUri = rsPropsRefdByMe.getString(1);
                    domUri =  rsPropsRefdByMe.getString(2);
                    PropertyDescriptor pd = getPropertyDescriptor(propUri,oldProject );
                    if (null != pd)
                    {
                        pd.setContainer(c);
                        pd.setProject(newProject);
                        pd.setPropertyId(0);
                        pd = ensurePropertyDescriptor(pd);
                    }
                    if (null != domUri)
                    {
                        DomainDescriptor dd = getDomainDescriptor(domUri, oldProject);
                        dd.setContainer(c);
                        dd.setProject(newProject);
                        dd.setDomainId(0);
                        ensureDomainDescriptor(dd);

                        ensurePropertyDomain(pd, dd);
                    }
                }

                for (ObjectProperty op : mMyObjsThatRefProjProps.values())
                {
                    deleteProperty(op.getObjectURI(), op.getPropertyURI(), op.getContainer(), oldProject);
                    insertProperties(op.getContainer(), op.getObjectURI(), op);
                }

            }

            getExpSchema().getScope().commitTransaction();
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
        finally
        {

            if (null != rsMyObjsThatRefProjProps)
                    rsMyObjsThatRefProjProps.close();
            if (null != rsPropsRefdByMe)
                    rsPropsRefdByMe.close();

            getExpSchema().getScope().closeConnection();
        }
    }

    private static PropertyDescriptor ensurePropertyDescriptor(String propertyURI, String dataTypeURI, String name, Container container) throws SQLException
	{
        PropertyDescriptor pdNew = new PropertyDescriptor(propertyURI, dataTypeURI, name, container);
        return ensurePropertyDescriptor (pdNew);
    }


    private static PropertyDescriptor ensurePropertyDescriptor(PropertyDescriptor pdIn) throws SQLException
    {
         if (null == pdIn.getContainer())
         {
             assert false : "Container should be set on PropertyDescriptor";
             pdIn.setContainer(_sharedContainer);
         }

        PropertyDescriptor pd = getPropertyDescriptor(pdIn.getPropertyURI(), pdIn.getContainer());
        if (null == pd)
        {
            assert pdIn.getPropertyId() == 0;
            pd = Table.insert(null, getTinfoPropertyDescriptor(), pdIn);
            propDescCache.put(getCacheKey(pd), pd);
        }
        else if (pd.equals(pdIn))
        {
            return pd;
        }
        else
        {
            List<String> colDiffs = comparePropertyDescriptors(pdIn, pd);

            if (colDiffs.size()==0)
            {
                // if the descriptor differs by container only and the requested descriptor is in the project fldr
                if (!pdIn.getContainer().getId().equals(pd.getContainer().getId()) &&
                        pdIn.getContainer().getId().equals(pdIn.getProject().getId()))
                {
                    pdIn.setPropertyId(pd.getPropertyId());
                    pd = updatePropertyDescriptor(pdIn);
                }
                return pd;
            }

            // you are allowed to update if you are coming from the project root, or if  you are in the container
            // in which the descriptor was created
            boolean fUpdateIfExists = false;
            if (pdIn.getContainer().getId().equals(pd.getContainer().getId())
                    || pdIn.getContainer().getId().equals(pdIn.getProject().getId()))
                    fUpdateIfExists = true;


            boolean fMajorDifference = false;
            if (colDiffs.toString().contains("RangeURI") || colDiffs.toString().contains("PropertyType"))
                fMajorDifference = true;

            String errmsg = "ensurePropertyDescriptor:  descriptor In different from Found for " + colDiffs.toString() +
                                    "\n\t Descriptor In: " + pdIn +
                                    "\n\t Descriptor Found: " + pd;

            if (fUpdateIfExists)
            {
                //todo:  pass list of cols to update
                pdIn.setPropertyId(pd.getPropertyId());
                pd = updatePropertyDescriptor(pdIn);
                if (fMajorDifference)
                    _log.debug(errmsg);
            }
            else
            {
                if (fMajorDifference)
                    _log.error(errmsg);
                else
                    _log.debug(errmsg);
            }
        }
        return pd;
	}


    private static List<String> comparePropertyDescriptors(PropertyDescriptor pdIn, PropertyDescriptor pd) throws SQLException
    {
        List<String> colDiffs = new ArrayList<String>();

        // if the returned pd is in a different project, it better be the shared project
        if (!pd.getProject().equals(pdIn.getProject()) && !pd.getProject().equals(_sharedContainer))
            colDiffs.add("Project");

        // check the pd values that can't change
        if (!pd.getRangeURI().equals(pdIn.getRangeURI()))
            colDiffs.add("RangeURI");
        if (!pd.getPropertyType().equals(pdIn.getPropertyType()))
            colDiffs.add("PropertyType");

        if (pdIn.getPropertyId() != 0 && !(pd.getPropertyId() == pdIn.getPropertyId()))
            colDiffs.add("PropertyId");

        if (ObjectUtils.equals(pdIn.getName(), pd.getName()))
            colDiffs.add("Name");

        if (ObjectUtils.equals(pdIn.getConceptURI(), pd.getConceptURI()))
            colDiffs.add("ConceptURI");

        if (ObjectUtils.equals(pdIn.getDescription(), pd.getDescription()))
            colDiffs.add("Description");

        if (ObjectUtils.equals(pdIn.getFormat(), pd.getFormat()))
            colDiffs.add("Format");

        if (ObjectUtils.equals(pdIn.getLabel(), pd.getLabel()))
            colDiffs.add("Label");

        if (ObjectUtils.equals(pdIn.getOntologyURI(), pd.getOntologyURI()))
            colDiffs.add("OntologyURI");

        if (ObjectUtils.equals(pdIn.getSearchTerms(), pd.getSearchTerms()))
            colDiffs.add("SearchTerms");

        if (ObjectUtils.equals(pdIn.getSemanticType(), pd.getSemanticType()))
            colDiffs.add("SemanticType");

        if (pdIn.isHidden() != pd.isHidden())
            colDiffs.add("IsHidden");

        if (pdIn.isMvEnabled() != pd.isMvEnabled())
            colDiffs.add("IsMvEnabled");

        if (ObjectUtils.equals(pdIn.getLookupContainer(), pd.getLookupContainer()))
            colDiffs.add("LookupContainer");

        if (ObjectUtils.equals(pdIn.getLookupSchema(), pd.getLookupSchema()))
            colDiffs.add("LookupSchema");

        if (ObjectUtils.equals(pdIn.getLookupQuery(), pd.getLookupQuery()))
            colDiffs.add("LookupQuery");

        return colDiffs;
    }

    public static DomainDescriptor ensureDomainDescriptor(String domainURI, String name, Container container) throws SQLException
    {
        DomainDescriptor ddIn = new DomainDescriptor(domainURI, container);
        ddIn.setName(name);
        return ensureDomainDescriptor(ddIn);
    }

    @NotNull
    private static DomainDescriptor ensureDomainDescriptor(DomainDescriptor ddIn) throws SQLException
     {
        DomainDescriptor dd = getDomainDescriptor(ddIn.getDomainURI(), ddIn.getContainer());
        if (null == dd)
        {
            dd = Table.insert(null, getTinfoDomainDescriptor(), ddIn);
            domainDescCache.put(getCacheKey(dd),dd);
        }
        else
        {
            List<String> colDiffs = compareDomainDescriptors(ddIn, dd);

            if (colDiffs.size()==0)
            {
                // if the descriptor differs by container only and the requested descriptor is in the project fldr
                if (!ddIn.getContainer().getId().equals(dd.getContainer().getId()) &&
                        ddIn.getContainer().getId().equals(ddIn.getProject().getId()))
                {
                    ddIn.setDomainId(dd.getDomainId());
                    dd = updateDomainDescriptor(ddIn);
                }
                return dd;
            }

            // you are allowed to update if you are coming from the project root, or if  you are in the container
            // in which the descriptor was created
            boolean fUpdateIfExists = false;
            if (ddIn.getContainer().getId().equals(dd.getContainer().getId())
                    || ddIn.getContainer().getId().equals(ddIn.getProject().getId()))
                fUpdateIfExists = true;

            boolean fMajorDifference = false;
            if (colDiffs.toString().contains("RangeURI") || colDiffs.toString().contains("PropertyType"))
                fMajorDifference = true;

            String errmsg = "ensureDomainDescriptor:  descriptor In different from Found for " + colDiffs.toString() +
                                    "\n\t Descriptor In: " + ddIn +
                                    "\n\t Descriptor Found: " + dd;


            if (fUpdateIfExists)
            {
                //todo:  pass list of cols to update
                ddIn.setDomainId(dd.getDomainId());
                dd = updateDomainDescriptor(ddIn);
                if (fMajorDifference)
                    _log.debug(errmsg);
            }
            else
            {
                if (fMajorDifference)
                    _log.error(errmsg);
                else
                    _log.debug(errmsg);
            }
        }
        return dd;
    }

    private static List<String>  compareDomainDescriptors(DomainDescriptor ddIn, DomainDescriptor dd) throws SQLException
    {
        List<String> colDiffs = new ArrayList<String>();
        String val;

        if (ddIn.getDomainId() !=0 && !(dd.getDomainId() == ddIn.getDomainId()))
            colDiffs.add("DomainId");

        val=ddIn.getName();
        if (null!= val && !dd.getName().equals(val))
            colDiffs.add("Name");

        return colDiffs;
    }

    private static void ensurePropertyDomain(PropertyDescriptor pd, DomainDescriptor dd) throws SQLException
    {
        ensurePropertyDomain(pd, dd, 0);
    }

    public static PropertyDescriptor ensurePropertyDomain(PropertyDescriptor pd, DomainDescriptor dd, int sortOrder) throws SQLException
    {
        if (null == pd)
            throw new IllegalArgumentException("Must supply a PropertyDescriptor");
        if (null == dd)
            throw new IllegalArgumentException("Must supply a DomainDescriptor");
        if (!pd.getContainer().equals(dd.getContainer())
                    &&  !pd.getProject().equals(_sharedContainer))
            throw new SQLException("ensurePropertyDomain:  property " + pd.getPropertyURI() + " not in same container as domain " + dd.getDomainURI());

        SQLFragment sqlInsert = new SQLFragment("INSERT INTO " + getTinfoPropertyDomain() + " ( PropertyId, DomainId, Required, SortOrder ) " +
                    " SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT * FROM " + getTinfoPropertyDomain() +
                    " WHERE PropertyId=? AND DomainId=?)");
        sqlInsert.add(pd.getPropertyId());
        sqlInsert.add(dd.getDomainId());
        sqlInsert.add(pd.isRequired());
        sqlInsert.add(sortOrder);
        sqlInsert.add(pd.getPropertyId());
        sqlInsert.add(dd.getDomainId());
        int count = Table.execute(getExpSchema(), sqlInsert);
        // if 0 rows affected, we should do an update to make sure required is correct
        if (count == 0)
        {
            SQLFragment sqlUpdate = new SQLFragment("UPDATE " + getTinfoPropertyDomain() + " SET Required = ?, SortOrder = ? WHERE PropertyId=? AND DomainId= ?");
            sqlUpdate.add(pd.isRequired());
            sqlUpdate.add(sortOrder);
            sqlUpdate.add(pd.getPropertyId());
            sqlUpdate.add(dd.getDomainId());
            Table.execute(getExpSchema(), sqlUpdate);
        }
        domainPropertiesCache.remove(getCacheKey(dd));
        return pd;
    }

    
	private static void insertProperties(Container c, ObjectProperty[] props) throws SQLException, ValidationException
	{
		HashMap<String,PropertyDescriptor> descriptors = new HashMap<String, PropertyDescriptor>();
		HashMap<String,Integer> objects = new HashMap<String, Integer>();
        List<ValidationError> errors = new ArrayList<ValidationError>();
        // assert !c.equals(ContainerManager.getRoot());
        // TODO - make user a parameter to this method 
        User user = HttpView.hasCurrentView() ? HttpView.currentContext().getUser() : null;
        ValidatorContext validatorCache = new ValidatorContext(c, user);

        for (ObjectProperty property : props)
		{
			if (null == property)
				continue;

            PropertyDescriptor pd = descriptors.get(property.getPropertyURI());
			if (0 == property.getPropertyId())
			{
				if (null == pd)
				{
                    PropertyDescriptor pdIn = new PropertyDescriptor(property.getPropertyURI(), property.getRangeURI(), property.getName(), c);
                    pdIn.setFormat(property.getFormat());
                    pd = getPropertyDescriptor(pdIn.getPropertyURI(), pdIn.getContainer());

                    if (null == pd)
                        pd = ensurePropertyDescriptor(pdIn);

					descriptors.put(property.getPropertyURI(), pd);
				}
				property.setPropertyId(pd.getPropertyId());
            }
			if (0 == property.getObjectId())
			{
				Integer objectId = objects.get(property.getObjectURI());
				if (null == objectId)
				{
					// I'm assuming all properties are in the same container
					objectId = ensureObject(property.getContainer(), property.getObjectURI(), property.getObjectOwnerId());
					objects.put(property.getObjectURI(), objectId);
				}
				property.setObjectId(objectId);
			}
            if (pd == null)
            {
                pd = getPropertyDescriptor(property.getPropertyId());
            }
            validateProperty(PropertyService.get().getPropertyValidators(pd), pd, property.value(), errors, validatorCache);
        }
        if (!errors.isEmpty())
            throw new ValidationException(errors);
        insertPropertiesBulk(c, Arrays.asList((PropertyRow[])props));
	}


	private static void insertPropertiesBulk(Container container, List<PropertyRow> props) throws SQLException
	{
        List<List<?>> floats = new ArrayList<List<?>>();
		List<List<?>> dates = new ArrayList<List<?>>();
		List<List<?>> strings = new ArrayList<List<?>>();
		List<List<?>> mvIndicators = new ArrayList<List<?>>();

		for (PropertyRow property : props)
		{
			if (null == property)
				continue;

            int objectId = property.getObjectId();
            int propertyId = property.getPropertyId();
            String mvIndicator = property.getMvIndicator();
            assert mvIndicator == null || MvUtil.isMvIndicator(mvIndicator, container) : "Attempt to insert an invalid missing value indicator: " + mvIndicator;

            if (null != property.getFloatValue())
                floats.add(Arrays.asList(objectId, propertyId, property.getFloatValue(), mvIndicator));
            else if (null != property.getDateTimeValue())
                dates.add(Arrays.asList(objectId, propertyId, new java.sql.Timestamp(property.getDateTimeValue().getTime()), mvIndicator));
            else if (null != property.getStringValue())
            {
                String string = property.getStringValue();
                // UNDONE - handle truncation in some other way?
                if (string.length() > ObjectProperty.STRING_LENGTH)
                {
                    throw new SQLException("String value too long in field " + OntologyManager.getPropertyDescriptor(propertyId).getName() + ": " + (string.length() < 150 ? string : string.substring(0, 149) + "..."));
                }
                strings.add(Arrays.asList(objectId, propertyId, string, mvIndicator));
            }
            else if (null != mvIndicator)
            {
                mvIndicators.add(Arrays.<Object>asList(objectId, propertyId, property.getTypeTag(), mvIndicator));
            }
		}

		assert getExpSchema().getScope().isTransactionActive();

		if (dates.size() > 0)
		{
			String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, DateTimeValue, MvIndicator) VALUES (?,?,'d',?, ?)";
            Table.batchExecute(getExpSchema(), sql, dates);
		}

		if (floats.size() > 0)
		{
			String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, FloatValue, MvIndicator) VALUES (?,?,'f',?, ?)";
            Table.batchExecute(getExpSchema(), sql, floats);
		}

		if (strings.size() > 0)
		{
			String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, StringValue, MvIndicator) VALUES (?,?,'s',?, ?)";
            Table.batchExecute(getExpSchema(), sql, strings);
		}

        if (mvIndicators.size() > 0)
        {
            String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, MvIndicator) VALUES (?,?,?,?)";
            Table.batchExecute(getExpSchema(), sql, mvIndicators);
        }

        clearPropertyCache();
    }

    public static void deleteProperty(String objectURI, String propertyURI, Container objContainer, Container propContainer)
    {
        try
        {
            OntologyObject o = getOntologyObject(objContainer, objectURI);
            if (o == null)
                return;

            PropertyDescriptor pd = getPropertyDescriptor(propertyURI, propContainer);
            if (pd == null)
                return;
            SimpleFilter filter = new SimpleFilter("ObjectId", o.getObjectId());
            filter.addCondition("PropertyId", pd.getPropertyId());
            Table.delete(getTinfoObjectProperty(), filter);
            clearPropertyCache(objectURI);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    /** Delete properties owned by the objects. */
    public static void deleteProperties(Integer objectIDs, Container objContainer) throws SQLException
    {
        deleteProperties(new Integer[] { objectIDs }, objContainer);
    }

    private static void deleteProperties(Integer[] objectIDs, Container objContainer) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.InClause("ObjectID", Arrays.asList(objectIDs)));
        String[] objectURIs = Table.executeArray(getTinfoObject(), "ObjectURI", filter, null, String.class);

        StringBuilder in = new StringBuilder();
        for (Integer objectID: objectIDs)
        {
            in.append(objectID);
            in.append(",");
        }
        in.setLength(in.length()-1);

        StringBuilder sqlDeleteProperties = new StringBuilder();
        sqlDeleteProperties.append("DELETE FROM " + getTinfoObjectProperty() + " WHERE  ObjectId IN (SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = '").append(objContainer.getId()).append("' AND ObjectId IN (");
        sqlDeleteProperties.append(in);
        sqlDeleteProperties.append("))");
        Table.execute(getExpSchema(), sqlDeleteProperties.toString());

        for (String uri : objectURIs)
        {
            clearPropertyCache(uri);
        }
    }

    public static void deletePropertyDescriptor(PropertyDescriptor pd) throws SQLException
    {
        int propId= pd.getPropertyId();
        String key = getCacheKey(pd);

        String deleteObjPropSql = "DELETE FROM " + getTinfoObjectProperty() + " WHERE PropertyId = ? ";
        String deletePropDomSql = "DELETE FROM " + getTinfoPropertyDomain() + " WHERE PropertyId = ? ";
        String deletePropSql = "DELETE FROM " + getTinfoPropertyDescriptor() + " WHERE PropertyId = ? ";

        try
        {
            getExpSchema().getScope().ensureTransaction();

            Table.execute(getExpSchema(), deleteObjPropSql, propId);
            Table.execute(getExpSchema(), deletePropDomSql, propId);
            Table.execute(getExpSchema(), deletePropSql, propId);
            propDescCache.remove(key);
            domainPropertiesCache.clear();
            getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            getExpSchema().getScope().closeConnection();
        }
    }

    public static void insertProperties(Container container, String ownerObjectLsid, ObjectProperty... properties) throws ValidationException
    {
        try
        {
            getExpSchema().getScope().ensureTransaction();

            Integer parentId = ownerObjectLsid == null ? null : ensureObject(container, ownerObjectLsid);
            for (ObjectProperty oprop : properties)
            {
                oprop.setObjectOwnerId(parentId);
            }
            insertProperties(container, properties);

            getExpSchema().getScope().commitTransaction();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            getExpSchema().getScope().closeConnection();
        }
    }


    public static PropertyDescriptor getPropertyDescriptor(int propertyId)
    {
        return Table.selectObject(getTinfoPropertyDescriptor(), propertyId, PropertyDescriptor.class);
    }

    
    public static PropertyDescriptor getPropertyDescriptor(String propertyURI, Container c)
	{
        try
        {
            // cache lookup by project. if not found at project level, check to see if global
            String key = getCacheKey(propertyURI , c);
            PropertyDescriptor pd = propDescCache.get(key);
            if (null != pd)
                return pd;

            key = getCacheKey(propertyURI, _sharedContainer);
            pd = propDescCache.get(key);
            if (null != pd)
                return pd;

            Container proj=c.getProject();
            if (null==proj)
                    proj=c;

            //TODO: Currently no way to edit these descriptors. But once
            //there is need to invalidate the cache.
		    String sql = " SELECT * FROM " + getTinfoPropertyDescriptor() + " WHERE PropertyURI = ? AND Project IN (?,?)";
            PropertyDescriptor[] pdArray = Table.executeQuery(getExpSchema(),  sql, new Object[]{propertyURI,
                                                                    proj.getId(),
                                                                    _sharedContainer.getId()},
                                                                    PropertyDescriptor.class);
            if (pdArray.length > 0)
			{
                pd = pdArray[0];

                // if someone has explicitly inserted a descriptor with the same URI as an existing one ,
                // and one of the two is in the shared project, use the project-level descriiptor.
                if (pdArray.length>1)
                {
                    _log.debug("Multiple PropertyDescriptors found for "+ propertyURI);
                    if (pd.getProject().equals(_sharedContainer))
                        pd=pdArray[1];
                }

                key = getCacheKey(pd);
                propDescCache.put(key, pd);
			}

            return pd;

		}
		catch (SQLException x)
		{
			throw new RuntimeSQLException(x);
		}
	}
    

    public static DomainDescriptor getDomainDescriptor(int id, boolean force)
    {
        return Table.selectObject(getTinfoDomainDescriptor(), id, DomainDescriptor.class);
    }


    public static DomainDescriptor getDomainDescriptor(int id)
    {
        return getDomainDescriptor(id, false);
    }


    private static DomainDescriptor EMPTY = new DomainDescriptor();
    static
    {
        assert MemTracker.remove(EMPTY);
    }
    

    public static DomainDescriptor getDomainDescriptor(String domainURI, Container c)
	{
        try
        {
            // cache lookup by project. if not found at project level, check to see if global
            String key = getCacheKey(domainURI , c);
            DomainDescriptor dd = domainDescCache.get(key);
            if (null != dd)
                return dd;

            key = getCacheKey(domainURI , _sharedContainer);
            dd = domainDescCache.get(key);
            if (null != dd)
                return dd;

            Container proj=c.getProject();
            if (null==proj)
                proj=c;

            String sql = " SELECT * FROM " + getTinfoDomainDescriptor() + " WHERE DomainURI = ? AND Project IN (?,?) ";
            DomainDescriptor[] ddArray = Table.executeQuery(getExpSchema(),  sql, new Object[]{domainURI,
                                                                    proj.getId(),
                                                                    _sharedContainer.getId()},
                                                                    DomainDescriptor.class);
            if (ddArray.length > 0)
            {
                dd = ddArray[0];

                // if someone has explicitly inserted a descriptor with the same URI as an existing one ,
                // and one of the two is in the shared project, use the project-level descriptor.
                if (ddArray.length>1)
                {
                    _log.debug("Multiple DomainDescriptors found for " + domainURI);
                    if (dd.getProject().equals(_sharedContainer))
                        dd = ddArray[1];
                }
                key = getCacheKey(dd);
                domainDescCache.put(key, dd);
            }
            return dd;
        }
        catch (SQLException x)
        {
            _log.error("Error getting domain descriptor: " + domainURI + " container: " + c, x);
            return null;
        }
        finally { _log.debug("getDomainDescriptor for "+ domainURI + " container= "+ c.getPath() ); }
    }


    public static Collection<DomainDescriptor> getDomainDescriptors(Container container) throws SQLException
    {
        Map<String, DomainDescriptor> ret = new LinkedHashMap<String, DomainDescriptor>();
        String sql = "SELECT * FROM " + getTinfoDomainDescriptor() + " WHERE Project = ?";
        Container project = container.getProject();

        if (null != project)
        {
            DomainDescriptor[] dds = Table.executeQuery(getExpSchema(), sql, new Object[] { project.getId() }, DomainDescriptor.class);
            for (DomainDescriptor dd : dds)
            {
                ret.put(dd.getDomainURI(), dd);
            }
            if (!project.equals(ContainerManager.getSharedContainer()))
            {
                DomainDescriptor[] projectDDs = Table.executeQuery(getExpSchema(), sql, new Object[] { ContainerManager.getSharedContainer().getId()}, DomainDescriptor.class);
                for (DomainDescriptor dd : projectDDs)
                {
                    if (!ret.containsKey(dd.getDomainURI()))
                    {
                        ret.put(dd.getDomainURI(), dd);
                    }
                }
            }
        }
        return ret.values();
    }
    
    public static String getCacheKey (DomainDescriptor dd)
    {
        return getCacheKey(dd.getDomainURI(), dd.getContainer());
    }


    public static String getCacheKey (PropertyDescriptor pd)
    {
        return getCacheKey(pd.getPropertyURI(), pd.getContainer());
    }


    public static String getCacheKey (String uri, Container c)
    {
        Container proj = c.getProject();
        String projId;

        if (null==proj)
            projId = c.getId();
        else
            projId = proj.getId();

        return uri + "|" + projId;
    }


    /*
    public static PropertyDescriptor[] getPropertiesForType(String typeURI)
     {
            return getPropertiesForType(typeURI, _sharedContainer);
     }
     */


    //TODO: DbCache semantics. This loads the cache but does not fetch cause need to get them all together
    //
    public static PropertyDescriptor[] getPropertiesForType(String typeURI, Container c)
	{
        PropertyDescriptor[] pdArray = getCachedPropertyDescriptorsForDomain(typeURI, c);
        if (pdArray != null)
        {
            return pdArray;
        }

        try
		{
            String sql = " SELECT PD.*,Required " +
                     " FROM " + getTinfoPropertyDescriptor() + " PD " +
                     "   INNER JOIN " + getTinfoPropertyDomain() + " PDM ON (PD.PropertyId = PDM.PropertyId) " +
                     "   INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId) " +
                     "  WHERE DD.DomainURI = ?  AND DD.Project IN (?,?) ORDER BY PDM.SortOrder, PD.PropertyId";

            Object[] params = new Object[]
            {
                typeURI,
                // If we're in the root, just double-up the shared project's id
                c.getProject() == null ? _sharedContainer.getProject().getId() : c.getProject().getId(),
                _sharedContainer.getProject().getId()
            };
            pdArray = Table.executeQuery(getExpSchema(), sql, params, PropertyDescriptor.class);
            //NOTE: cached descriptors may have differing values of isRequired() as that is a per-domain setting
            //Descriptors returned from this method come direct from DB and have correct values.
            List<Pair<String, Boolean>> propertyURIs = new ArrayList<Pair<String, Boolean>>(pdArray.length);
            for (PropertyDescriptor pd : pdArray) {
				propDescCache.put(getCacheKey(pd), pd);
                propertyURIs.add(new Pair<String, Boolean>(pd.getPropertyURI(), pd.isRequired()));
			}
            domainPropertiesCache.put(getCacheKey(typeURI, c), propertyURIs);

			return pdArray;
		}
		catch (SQLException x)
		{
			throw new RuntimeSQLException(x);
		}
	}

    private static PropertyDescriptor[] getCachedPropertyDescriptorsForDomain(String typeURI, Container c)
    {
        List<Pair<String, Boolean>> propertyURIs = domainPropertiesCache.get(getCacheKey(typeURI, c));
        if (propertyURIs != null)
        {
            PropertyDescriptor[] result = new PropertyDescriptor[propertyURIs.size()];
            int index = 0;
            for (Pair<String, Boolean> propertyURI : propertyURIs)
            {
                PropertyDescriptor pd = propDescCache.get(getCacheKey(propertyURI.getKey(), c));
                if (pd == null)
                {
                    return null;
                }
                // NOTE: cached descriptors may have differing values of isRequired() as that is a per-domain setting
                // Descriptors returned from this method will have their required bit set as appropriate for this domain 

                // Clone so we nobody else messes up our copy
                pd = pd.clone();
                pd.setRequired(propertyURI.getValue().booleanValue());
                result[index++] = pd;
            }
            return result;
        }
        return null;
    }

    public static void deleteType(String domainURI, Container c) throws DomainNotFoundException
	{
        if (null==domainURI)
            return;

		try
		{
            getExpSchema().getScope().ensureTransaction();

            try

            {
                deleteObjectsOfType(domainURI, c);
                deleteDomain(domainURI, c);
            }
            catch (DomainNotFoundException x)
            {
                // throw exception but do not kill enclosing transaction
                getExpSchema().getScope().commitTransaction();
                throw x;
            }

            getExpSchema().getScope().commitTransaction();
		}
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
		{
            getExpSchema().getScope().closeConnection();
		}
	}

	public static PropertyDescriptor insertOrUpdatePropertyDescriptor(PropertyDescriptor pd, DomainDescriptor dd, int sortOrder)
            throws SQLException, ChangePropertyDescriptorException
    {
        validatePropertyDescriptor(pd);
        DbScope scope = getExpSchema().getScope();
        scope.ensureTransaction();
        try
        {
            DomainDescriptor dexist = ensureDomainDescriptor(dd);

            if (!dexist.getContainer().equals(pd.getContainer())
                    &&  !pd.getProject().equals(_sharedContainer))
            {
                // domain is defined in a different container.
                //ToDO  define property in the domains container?  what security?
                throw new SQLException("Attempt to define property for a domain definition that exists in a different folder\n" +
                                        "domain folder = " + dexist.getContainer().getPath() + "\n" +
                                        "property folder = " + pd.getContainer().getPath());
            }

            PropertyDescriptor pexist = ensurePropertyDescriptor(pd);
            pexist.setRequired(pd.isRequired());

            ensurePropertyDomain(pexist, dexist, sortOrder);

            scope.commitTransaction();
            return pexist;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            getExpSchema().getScope().closeConnection();
        }
    }


    static final String parameters = "propertyuri,ontologyuri,name,description,rangeuri,concepturi,label,searchterms," +
            "semantictype,format,container,project,lookupcontainer,lookupschema,lookupquery,defaultvaluetype,hidden," +
            "mvenabled,importaliases,url,shownininsertview,showninupdateview,shownindetailsview,measure,dimension";
    static final String[] parametersArray = parameters.split(",");
    static final String insertSql;
    static final String updateSql;
    static
    {
        insertSql = "INSERT INTO exp.propertydescriptor (" + parameters + ")\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        StringBuilder sb = new StringBuilder("UPDATE exp.propertydescriptor SET");
        String comma = " ";
        for (String p : parametersArray)
        {
            sb.append(comma).append(p).append("=?");
            comma = ",";
        }
        sb.append("\nWHERE propertyid=?");
        updateSql = sb.toString();
    }



    public static void insertPropertyDescriptors(List<PropertyDescriptor> pds) throws SQLException
    {
        if (null == pds || 0 == pds.size())
            return;
        PreparedStatement stmt = getExpSchema().getScope().getConnection().prepareStatement(insertSql);
        ObjectFactory f = ObjectFactory.Registry.getFactory(PropertyDescriptor.class);
        Map m = null;
        for (PropertyDescriptor pd : pds)
        {
            m = f.toMap(pd, m);
            for (int i=0 ; i<parametersArray.length ; i++)
            {
                String p = parametersArray[i];
                Object o = m.get(p);
                if (o == null)
                    stmt.setNull(i+1, Types.VARCHAR);
                else if (o instanceof String)
                    stmt.setString(i+1, (String)o);
                else if (o instanceof Integer)
                    stmt.setInt(i+1, ((Integer)o).intValue());
                else if (o instanceof Container)
                    stmt.setString(i+1, ((Container)o).getId());
                else if (o instanceof Boolean)
                    stmt.setBoolean(i+1, ((Boolean)o).booleanValue());
                else
                    assert false : o.getClass().getName();
            }
            stmt.addBatch();
        }
        stmt.executeBatch();
    }



    public static void updatePropertyDescriptors(List<PropertyDescriptor> pds) throws SQLException
    {
        if (null == pds || 0 == pds.size())
            return;
        PreparedStatement stmt = getExpSchema().getScope().getConnection().prepareStatement(updateSql);
        ObjectFactory f = ObjectFactory.Registry.getFactory(PropertyDescriptor.class);
        Map m = null;
        for (PropertyDescriptor pd : pds)
        {
            m = f.toMap(pd, m);
            for (int i=0 ; i<parametersArray.length ; i++)
            {
                String p = parametersArray[i];
                Object o = m.get(p);
                if (o == null)
                    stmt.setNull(i+1, Types.VARCHAR);
                else if (o instanceof String)
                    stmt.setString(i+1, (String)o);
                else if (o instanceof Integer)
                    stmt.setInt(i+1, ((Integer)o).intValue());
                else if (o instanceof Container)
                    stmt.setString(i+1, ((Container)o).getId());
                else if (o instanceof Boolean)
                    stmt.setBoolean(i+1, ((Boolean)o).booleanValue());
                else
                    assert false : o.getClass().getName();
            }
            stmt.setInt(parametersArray.length+1, pd.getPropertyId());
            stmt.addBatch();
        }
        stmt.executeBatch();
    }


    public static PropertyDescriptor insertPropertyDescriptor(PropertyDescriptor pd) throws SQLException, ChangePropertyDescriptorException
    {
		assert pd.getPropertyId() == 0;
        validatePropertyDescriptor(pd);
		pd = Table.insert(null, getTinfoPropertyDescriptor(), pd);
		propDescCache.put(getCacheKey(pd), pd);
		return pd;
	}


    //todo:  we automatically update a pd to the last  one in?
	public static PropertyDescriptor updatePropertyDescriptor(PropertyDescriptor pd) throws SQLException
	{
		assert pd.getPropertyId() != 0;
		pd = Table.update(null, getTinfoPropertyDescriptor(), pd, pd.getPropertyId());
		propDescCache.put(getCacheKey(pd), pd);
        // It's possible that the propertyURI has changed, thus breaking our reference
        domainPropertiesCache.clear();
		return pd;
	}


    public static DomainDescriptor updateDomainDescriptor(DomainDescriptor dd) throws SQLException
    {
        assert dd.getDomainId() != 0;
        dd = Table.update(null, getTinfoDomainDescriptor(), dd, dd.getDomainId());
        domainDescCache.remove(getCacheKey(dd));
        domainPropertiesCache.remove(getCacheKey(dd));
        return dd;
    }

	public static void clearCaches()
	{
		ExperimentService.get().clearCaches();
        domainDescCache.clear();
        domainPropertiesCache.clear();
        propDescCache.clear();
		mapCache.clear();
		objectIdCache.clear();
	}


	public static void clearPropertyCache(String parentObjectURI)
	{
		mapCache.remove(parentObjectURI);
	}


	public static void clearPropertyCache()
	{
		mapCache.clear();
	}


    /**
     * @return whether the import was successful or not. Check the errors collection for details
     * @deprecated  use PropertyService
     */
    public static boolean importOneType(final String domainURI, List<Map<String, Object>> maps, Collection<String> errors, Container container, User user)
            throws SQLException, ChangePropertyDescriptorException
    {
        return importTypes(new DomainURIFactory()
            {
                public String getDomainURI(String name)
                {
                    return domainURI;
                }
            }, null, maps, errors, container, false, user);
    }


    /**
     * @return whether the import was successful or not. Check the errors collection for details
     * @deprecated  use PropertyService
     * TODO rewrite to use createPropertyDescriptors()
     */
    @Deprecated
    public static boolean importTypes(DomainURIFactory uriFactory, String typeColumn, List<Map<String, Object>> maps, Collection<String> errors, Container container, boolean ignoreDuplicates, User user)
            throws SQLException, ChangePropertyDescriptorException
    {
        //_log.debug("importTypes(" + vocabulary + "," + typeColumn + "," + maps.length + ")");
        LinkedHashMap<String, PropertyDescriptor> propsWithoutDomains = new LinkedHashMap<String, PropertyDescriptor>();
        LinkedHashMap<String, PropertyDescriptor> allProps = new LinkedHashMap<String, PropertyDescriptor>();
        Map<String, Map<String, PropertyDescriptor>> newPropsByDomain = new  TreeMap<String, Map<String, PropertyDescriptor>>();
        // Case insensitive set since we don't want property names that differ only by case
        Map<String, Set<String>> newPropertyURIsByDomain = new HashMap<String, Set<String>>();
        Map<String, PropertyDescriptor> pdNewMap;
        // propertyURI -> ConditionalFormats
        Map<String, List<ConditionalFormat>> allConditionalFormats = new HashMap<String, List<ConditionalFormat>>();

        Map<String, DomainDescriptor> domainMap = new HashMap<String, DomainDescriptor>();

        for (Map<String, Object> m : maps)
        {
            String domainName = typeColumn != null ? (String) m.get(typeColumn) : null;
            String domainURI = uriFactory.getDomainURI(domainName);

            String name = StringUtils.trimToEmpty(((String)m.get("property")));
            String propertyURI = StringUtils.trimToEmpty((String) m.get("propertyuri"));
            if (propertyURI.length() == 0)
                propertyURI = domainURI + "." + name;
            if (-1 != name.indexOf('#'))
            {
                propertyURI = name;
                name = name.substring(name.indexOf('#')+1);
            }
            if (name.length() == 0)
            {
                String e = "'property' field is required";
                if (!errors.contains(e))
                    errors.add(e);
                continue;
            }

            PropertyDescriptor pd = _propertyDescriptorFromRowMap(container, domainURI, propertyURI, name, m, errors);
            if (pd != null)
            {
                List<ConditionalFormat> conditionalFormats = (List<ConditionalFormat>) m.get("ConditionalFormats");
                if (conditionalFormats != null && !conditionalFormats.isEmpty())
                {
                    allConditionalFormats.put(pd.getPropertyURI(), conditionalFormats);
                }
                if (null != allProps.put(pd.getPropertyURI(), pd))
                {
                    if (!ignoreDuplicates)
                        errors.add("Duplicate definition of property: " + pd.getPropertyURI());
                }

                DomainDescriptor dd = null;
                if (null != domainURI)
                {
                    dd = domainMap.get(domainURI);
                    if (null == dd)
                    {
                        dd = ensureDomainDescriptor(domainURI, domainName, container);
                        domainMap.put(domainURI, dd);
                    }
                }

                if (null != dd)
                {
                    pdNewMap = newPropsByDomain.get(dd.getDomainURI());
                    if (null == pdNewMap)
                    {
                        pdNewMap = new LinkedHashMap<String, PropertyDescriptor>();
                        newPropsByDomain.put(dd.getDomainURI(), pdNewMap);
                    }
                    pdNewMap.put(pd.getPropertyURI(), pd);

                    // Need to do a case insensitive check for duplicate property names
                    Set<String> caseInsensitivePropertyNames = newPropertyURIsByDomain.get(dd.getDomainURI());
                    if (caseInsensitivePropertyNames == null)
                    {
                        caseInsensitivePropertyNames = new CaseInsensitiveHashSet();
                        newPropertyURIsByDomain.put(dd.getDomainURI(), caseInsensitivePropertyNames);
                    }
                    if (!caseInsensitivePropertyNames.add(pd.getPropertyURI()))
                    {
                        errors.add("'" + dd.getName() + "' has multiple fields named '" + pd.getName() + "'");
                    }
                }
                else
                {
                    // put only the domain-less allProps in this list
                    propsWithoutDomains.put(pd.getPropertyURI(), pd);
                }
            }
        }

        if (!errors.isEmpty())
            return false;

        //
        //  Find any PropertyDescriptors that exist already
        //
        ArrayList<PropertyDescriptor> list = new ArrayList<PropertyDescriptor>(allProps.size());

        for (String dURI : newPropsByDomain.keySet())
        {
            pdNewMap = newPropsByDomain.get(dURI);
            PropertyDescriptor[] domainProps = OntologyManager.getPropertiesForType(dURI, container);
            int sortOrder = domainProps.length;

            for (PropertyDescriptor pdToInsert : pdNewMap.values())
            {
                DomainDescriptor dd = domainMap.get(dURI);
                assert (null!= dd);
                PropertyDescriptor pdInserted = null;
                if (domainProps.length == 0)
                {
                    try
                    {
                        // this is much faster than insertOrUpdatePropertyDescriptor()
                        if (pdToInsert.getPropertyId() == 0)
                            insertPropertyDescriptor(pdToInsert);
                        pdInserted = ensurePropertyDomain(pdToInsert, dd, sortOrder++);
                    }
                    catch (SQLException x)
                    {
                        // it is possible that the property descriptor exists without being part of the domain
                        // fall through
                    }
                }
                if (null == pdInserted)
                    pdInserted = OntologyManager.insertOrUpdatePropertyDescriptor(pdToInsert, dd, sortOrder++);

                list.add(pdInserted);
            }
        }

        for (PropertyDescriptor pdToInsert : propsWithoutDomains.values())
        {
            PropertyDescriptor pdInserted = OntologyManager.ensurePropertyDescriptor(pdToInsert);
            list.add(pdInserted);
        }

        for (Map.Entry<String, List<ConditionalFormat>> entry : allConditionalFormats.entrySet())
        {
            PropertyService.get().saveConditionalFormats(user, getPropertyDescriptor(entry.getKey(), container), entry.getValue());
        }

        return true;
    }


    public static class ImportPropertyDescriptor
    {
        public String domainName;
        public String domainURI;
        public PropertyDescriptor pd;
        ImportPropertyDescriptor(String domainName, String domainURI, PropertyDescriptor pd)
        {
            this.domainName = domainName;
            this.domainURI = domainURI;
            this.pd = pd;
        }
    }


    public static class ListImportPropertyDescriptors
    {
        public ArrayList<ImportPropertyDescriptor> properties = new ArrayList<ImportPropertyDescriptor>();
        public Map<String, List<ConditionalFormat>> formats = new HashMap<String, List<ConditionalFormat>>();
        void add(String domainName, String domainURI, PropertyDescriptor pd)
        {
            properties.add(new ImportPropertyDescriptor(domainName, domainURI, pd));
        }
    }


    /**
     * Turns a list of maps into a list of PropertyDescriptors.  Does not save anything.
     *
     * Look for duplicates with in imported list, but does not verify against any existing PropertyDescriptors/Domains
     */
    public static ListImportPropertyDescriptors createPropertyDescriptors(DomainURIFactory uriFactory, String typeColumn, List<Map<String, Object>> maps, Collection<String> errors, Container container, boolean ignoreDuplicates)
    {
        ListImportPropertyDescriptors ret = new ListImportPropertyDescriptors();
        CaseInsensitiveHashSet all = new CaseInsensitiveHashSet();

        for (Map<String, Object> m : maps)
        {
            String domainName = typeColumn != null ? (String) m.get(typeColumn) : null;
            String domainURI = uriFactory.getDomainURI(domainName);

            String name = StringUtils.trimToEmpty(((String)m.get("property")));
            String propertyURI = StringUtils.trimToEmpty((String) m.get("propertyuri"));
            if (propertyURI.length() == 0)
                propertyURI = domainURI + "." + name;
            if (-1 != name.indexOf('#'))
            {
                propertyURI = name;
                name = name.substring(name.indexOf('#')+1);
            }
            if (name.length() == 0)
            {
                String e = "'property' field is required";
                if (!errors.contains(e))
                    errors.add(e);
                continue;
            }

            PropertyDescriptor pd = _propertyDescriptorFromRowMap(container, domainURI, propertyURI, name, m, errors);

            if (pd != null)
            {
                if (!all.add(pd.getPropertyURI()) && !ignoreDuplicates)
                {
                    if (null != domainName)
                        errors.add("'" + domainName + "' has multiple fields named '" + name + "'");
                    else
                        errors.add("field '" + name + "' is specified more than once.");
                }

                List<ConditionalFormat> conditionalFormats = (List<ConditionalFormat>) m.get("ConditionalFormats");
                if (conditionalFormats != null && !conditionalFormats.isEmpty())
                    ret.formats.put(pd.getPropertyURI(), conditionalFormats);

                ret.add(domainName, domainURI, pd);
            }
        }
        return ret;
    }

    private static PropertyDescriptor _propertyDescriptorFromRowMap(Container container, String domainURI, String propertyURI, String name,
                                                                    Map<String, Object> m, Collection<String> errors)
    {
        // try use existing SystemProperty PropertyDescriptor from Shared container.
        PropertyDescriptor pd = null;
        if (!propertyURI.startsWith(domainURI) && !propertyURI.startsWith(ColumnInfo.DEFAULT_PROPERTY_URI_PREFIX))
            pd = getPropertyDescriptor(propertyURI, _sharedContainer);

        if (pd == null)
        {
            String label = StringUtils.trimToNull((String) m.get("label"));
            if (null == label)
                label = name;
            String conceptURI = (String) m.get("conceptURI");
            String rangeURI = (String) m.get("rangeURI");

            BooleanConverter booleanConverter = new BooleanConverter(Boolean.FALSE);

            boolean required = ((Boolean)booleanConverter.convert(Boolean.class, m.get("NotNull"))).booleanValue();
            boolean hidden = ((Boolean)booleanConverter.convert(Boolean.class, m.get("HiddenColumn"))).booleanValue();
            boolean mvEnabled = ((Boolean)booleanConverter.convert(Boolean.class, m.get("MvEnabled"))).booleanValue();

            String description = (String) m.get("description");
            String format = StringUtils.trimToNull((String)m.get("format"));
            String url = (String) m.get("url");
            String importAliases = (String) m.get("importAliases");

            // Try to resolve folder path to a container... if this fails, just use current folder (which at least will preserve schema & query)
            String lookupContainerId = null;
            String lookupFolderPath = (String) m.get("LookupFolderPath");
            if (null != lookupFolderPath)
            {
                Container lookupContainer = ContainerManager.getForPath(lookupFolderPath);
                lookupContainerId = null != lookupContainer ? lookupContainer.getId() : null;
            }
            String lookupSchema = (String) m.get("LookupSchema");
            String lookupQuery = (String) m.get("LookupQuery");

            boolean shownInInsertView = m.get("ShownInInsertView") == null || ((Boolean)m.get("ShownInInsertView")).booleanValue();
            boolean shownInUpdateView = m.get("ShownInUpdateView") == null || ((Boolean)m.get("ShownInUpdateView")).booleanValue();
            boolean shownInDetailsView = m.get("ShownInDetailsView") == null || ((Boolean)m.get("ShownInDetailsView")).booleanValue();

            boolean dimension = m.get("Dimension") != null && ((Boolean)m.get("Dimension")).booleanValue();
            boolean measure = m.get("Measure") != null && ((Boolean)m.get("Measure")).booleanValue();

            FacetingBehaviorType facetingBehavior = FacetingBehaviorType.AUTOMATIC;
            if (m.get("FacetingBehaviorType") != null)
            {
                FacetingBehaviorType type = FacetingBehaviorType.valueOf(m.get("FacetingBehaviorType").toString());
                if (type != null)
                    facetingBehavior = type;
            }

            PropertyType pt = PropertyType.getFromURI(conceptURI, rangeURI, null);
            if (null == pt)
            {
                String e = "Unrecognized type URI : " + ((null==conceptURI)? rangeURI : conceptURI);
                if (!errors.contains(e))
                    errors.add(e);
                return null;
            }
            if (pt == PropertyType.STRING && "textarea".equals(m.get("InputType")))
            {
                pt = PropertyType.MULTI_LINE;
            }
            rangeURI = pt.getTypeUri();

            if (format != null)
            {
                try
                {
                    switch (pt)
                    {
                        case INTEGER:
                        case DOUBLE:
                            format = convertNumberFormatChars(format);
                            (new DecimalFormat(format)).format(1.0);
                            break;
                        case DATE_TIME:
                            format = convertDateFormatChars(format);
                            (new SimpleDateFormat(format)).format(new Date());
                            // UNDONE: don't import date format until we have default format for study
                            // UNDONE: it looks bad to have mixed formats
                            break;
                        case STRING:
                        case MULTI_LINE:
                        default:
                            format = null;
                    }
                }
                catch (Exception x)
                {
                    format = null;
                }
            }

            pd = new PropertyDescriptor();
            pd.setPropertyURI(propertyURI);
            pd.setName(name);
            pd.setLabel(label);
            pd.setConceptURI(conceptURI);
            pd.setRangeURI(rangeURI);
            pd.setContainer(container);
            pd.setDescription(description);
            pd.setURL(StringExpressionFactory.createURL(url));
            pd.setImportAliases(importAliases);
            pd.setRequired(required);
            pd.setHidden(hidden);
            pd.setShownInInsertView(shownInInsertView);
            pd.setShownInUpdateView(shownInUpdateView);
            pd.setShownInDetailsView(shownInDetailsView);
            pd.setDimension(dimension);
            pd.setMeasure(measure);
            pd.setFormat(format);
            pd.setMvEnabled(mvEnabled);
            pd.setLookupContainer(lookupContainerId);
            pd.setLookupSchema(lookupSchema);
            pd.setLookupQuery(lookupQuery);
            pd.setFacetingBehaviorType(facetingBehavior);
        }
        return pd;
    }

    private static String convertNumberFormatChars(String format)
    {
        int length = format.length();
        int decimal = format.indexOf('.');
        if (-1 == decimal)
            decimal = length;
        StringBuilder s = new StringBuilder(format);
        for (int i=0 ; i<s.length() ; i++)
        {
            if ('n' == s.charAt(i))
                s.setCharAt(i, i<decimal-1 ? '#' : '0');
        }
        return s.toString();
    }


    private static String convertDateFormatChars(String format)
    {
        if (format.toUpperCase().equals(format))
            return format.replace('Y','y').replace('D','d');
        return format;
    }


    public static class TestCase extends Assert
    {
        @Test
		public void testSchema()
		{
			assertNotNull(OntologyManager.getExpSchema());
			assertNotNull(getTinfoPropertyDescriptor());
			assertNotNull(ExperimentService.get().getTinfoMaterialSource());

			assertEquals(getTinfoPropertyDescriptor().getColumns("PropertyId,PropertyURI,OntologyURI,RangeURI,Name,Description").size(), 6);
			assertEquals(getTinfoObject().getColumns("ObjectId,ObjectURI,Container,OwnerObjectId").size(), 4);
			assertEquals(getTinfoObjectPropertiesView().getColumns("ObjectId,ObjectURI,Container,OwnerObjectId,Name,PropertyURI,RangeURI,TypeTag,StringValue,DateTimeValue,FloatValue").size(), 11);
			assertEquals(ExperimentService.get().getTinfoMaterialSource().getColumns("RowId,Name,LSID,MaterialLSIDPrefix,Description,Created,CreatedBy,Modified,ModifiedBy,Container").size(), 10);
		}


        @Test
        public void testBasicPropertiesObject() throws SQLException
		{
            try
            {
                Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
                String parentObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
                String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

                //First delete in case test case failed before
                OntologyManager.deleteOntologyObjects(c, parentObjectLsid);
                assertNull(OntologyManager.getOntologyObject(c, parentObjectLsid));
                assertNull(OntologyManager.getOntologyObject(c, childObjectLsid));
                OntologyManager.ensureObject(c, childObjectLsid, parentObjectLsid);
                OntologyObject oParent = OntologyManager.getOntologyObject(c, parentObjectLsid);
                assertNotNull(oParent);
                OntologyObject oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);
                assertNull(oParent.getOwnerObjectId());
                assertEquals(oChild.getContainer(), c);
                assertEquals(oParent.getContainer(), c);

                String strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                OntologyManager.insertProperties(c, parentObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                String intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                OntologyManager.insertProperties(c, parentObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.MILLISECOND, 0);
                String dateProp = new Lsid("Junit", "OntologyManager", "dateProp").toString();
                OntologyManager.insertProperties(c, parentObjectLsid, new ObjectProperty(childObjectLsid, c, dateProp, cal.getTime()));

                Map m = OntologyManager.getProperties(c, oChild.getObjectURI());
                assertNotNull(m);
                assertEquals(m.size(), 3);
                assertEquals(m.get(strProp), "The String");
                assertEquals(m.get(intProp), 5);
                assertEquals(m.get(dateProp), cal.getTime());


                OntologyManager.deleteOntologyObjects(c, parentObjectLsid);
                assertNull(OntologyManager.getOntologyObject(c, parentObjectLsid));
                assertNull(OntologyManager.getOntologyObject(c, childObjectLsid));

                m = OntologyManager.getProperties(c, oChild.getObjectURI());
                assertTrue(null == m || m.size() == 0);
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

        @Test
		public void testContainerDelete() throws SQLException
		{
            try
            {
                Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
                //Clean up last time's mess
                OntologyManager.deleteAllObjects(c, TestContext.get().getUser());
                assertEquals(0L, OntologyManager.getObjectCount(c));

                String ownerObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
                String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

                OntologyManager.ensureObject(c, childObjectLsid, ownerObjectLsid);
                OntologyObject oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
                assertNotNull(oParent);
                OntologyObject oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);

                String strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                String intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.MILLISECOND, 0);
                String dateProp = new Lsid("Junit", "OntologyManager", "dateProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, dateProp, cal.getTime()));

                OntologyManager.deleteAllObjects(c, TestContext.get().getUser());
                assertEquals(0L, OntologyManager.getObjectCount(c));
                assertTrue(ContainerManager.delete(c, TestContext.get().getUser()));
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

        private void defineCrossFolderProperties(Container fldr1a, Container fldr1b) throws SQLException
        {
            try
            {
                String fa = fldr1a.getPath();
                String fb = fldr1b.getPath();

                //object, prop descriptor in folder being moved
                String objP1Fa = new Lsid("OntologyObject", "JUnit", fa.replace('/','.')).toString();
                OntologyManager.ensureObject(fldr1a, objP1Fa);
                String propP1Fa = fa + "PD1";
                PropertyDescriptor pd1Fa = OntologyManager.ensurePropertyDescriptor(propP1Fa, PropertyType.STRING.getTypeUri(),"PropertyDescriptor 1"+ fa, fldr1a);
                OntologyManager.insertProperties(fldr1a, null, new ObjectProperty(objP1Fa, fldr1a, propP1Fa, "same fldr"));

                //object in folder not moving, prop desc in folder moving
                String objP2Fb = new Lsid("OntologyObject", "JUnit", fb.replace('/','.')).toString();
                OntologyManager.ensureObject(fldr1b, objP2Fb);
                OntologyManager.insertProperties(fldr1b, null, new ObjectProperty(objP2Fb, fldr1b, propP1Fa, "object in folder not moving, prop desc in folder moving"));

                //object in folder moving, prop desc in folder not moving
                String propP2Fb = fb + "PD1";
                OntologyManager.ensurePropertyDescriptor(propP2Fb, PropertyType.STRING.getTypeUri(),"PropertyDescriptor 1" + fb, fldr1b);
                OntologyManager.insertProperties(fldr1a, null, new ObjectProperty(objP1Fa, fldr1a, propP2Fb, "object in folder moving, prop desc in folder not moving"));

                // third prop desc in folder that is moving;  shares domain with first prop desc
                String propP1Fa3 = fa + "PD3";
                PropertyDescriptor pd1Fa3 = OntologyManager.ensurePropertyDescriptor(propP1Fa3, PropertyType.STRING.getTypeUri(),"PropertyDescriptor 3" + fa, fldr1a);
                String domP1Fa = fa + "DD1";
                DomainDescriptor dd1 = new DomainDescriptor(domP1Fa, fldr1a);
                dd1.setName("DomDesc 1" + fa);
                OntologyManager.ensureDomainDescriptor(dd1);
                OntologyManager.ensurePropertyDomain(pd1Fa, dd1);
                OntologyManager.ensurePropertyDomain(pd1Fa3, dd1);

                //second domain desc in folder that is moving
                // second prop desc in folder moving, belongs to 2nd domain
                String propP1Fa2 = fa + "PD2";
                PropertyDescriptor pd1Fa2 = OntologyManager.ensurePropertyDescriptor(propP1Fa2, PropertyType.STRING.getTypeUri(),"PropertyDescriptor 2" + fa, fldr1a);
                String domP1Fa2 = fa +  "DD2";
                DomainDescriptor dd2 = new DomainDescriptor(domP1Fa2, fldr1a);
                dd2.setName("DomDesc 2" + fa);
                OntologyManager.ensureDomainDescriptor(dd2);
                OntologyManager.ensurePropertyDomain(pd1Fa2, dd2);
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

        @Test
        public void testContainerMove() throws SQLException
        {
            deleteMoveTestContainers();

            Container proj1 = ContainerManager.ensureContainer("/_ontMgrTestP1");
            Container proj2 = ContainerManager.ensureContainer("/_ontMgrTestP2");
            doMoveTest(proj1, proj2);
            deleteMoveTestContainers();

            proj1 = ContainerManager.ensureContainer("/");
            proj2 = ContainerManager.ensureContainer("/_ontMgrTestP2");
            doMoveTest(proj1, proj2);
            deleteMoveTestContainers();

            proj1 = ContainerManager.ensureContainer("/_ontMgrTestP1");
            proj2 = ContainerManager.ensureContainer("/");
            doMoveTest(proj1, proj2);
            deleteMoveTestContainers();

        }

        private void doMoveTest(Container proj1, Container proj2) throws SQLException
        {
            String p1Path = proj1.getPath() + "/";
            String p2Path = proj2.getPath() + "/";
            if (p1Path.equals("//")) p1Path="/_ontMgrDemotePromote";
            if (p2Path.equals("//")) p2Path="/_ontMgrDemotePromote";

            Container fldr1a = ContainerManager.ensureContainer(p1Path + "Fa");
            Container fldr1b = ContainerManager.ensureContainer(p1Path + "Fb");
            ContainerManager.ensureContainer(p2Path + "Fc");
            Container fldr1aa = ContainerManager.ensureContainer(p1Path + "Fa/Faa");
            Container fldr1aaa = ContainerManager.ensureContainer(p1Path + "Fa/Faa/Faaa");

            defineCrossFolderProperties(fldr1a, fldr1b);
            //defineCrossFolderProperties(fldr1a, fldr2c);
            defineCrossFolderProperties(fldr1aa, fldr1b);
            defineCrossFolderProperties(fldr1aaa, fldr1b);

            fldr1a.getProject().getPath();
            String f = fldr1a.getPath();
            String propId = f + "PD1";
            assertNull(OntologyManager.getPropertyDescriptor(propId, proj2));
            ContainerManager.move(fldr1a, proj2, TestContext.get().getUser());

            // if demoting a folder
            if (proj1.isRoot())
            {
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                propId = f + "PD2";
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                propId = f + "PD3";
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                String domId = f + "DD1";
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj2));

                domId = f + "DD2";
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj2));
            }
            // if promoting a folder,
            else if (proj2.isRoot())
            {
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj1));

                propId = f + "PD2";
                assertNull(OntologyManager.getPropertyDescriptor(propId, proj1));

                propId = f + "PD3";
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj1));

                String domId = f + "DD1";
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj1));

                domId = f + "DD2";
                assertNull(OntologyManager.getDomainDescriptor(domId, proj1));
            }
            else
            {
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj1));
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                propId = f + "PD2";
                assertNull(OntologyManager.getPropertyDescriptor(propId, proj1));
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                propId = f + "PD3";
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj1));
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                String domId = f + "DD1";
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj1));
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj2));

                domId = f + "DD2";
                assertNull(OntologyManager.getDomainDescriptor(domId, proj1));
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj2));
            }
        }

        @Test
        public void testDeleteFoldersWithSharedProps() throws SQLException
        {
            deleteMoveTestContainers();

            Container proj1 = ContainerManager.ensureContainer("/_ontMgrTestP1");
            String p1Path = proj1.getPath() + "/";

            Container fldr1a = ContainerManager.ensureContainer(p1Path + "Fa");
            Container fldr1b = ContainerManager.ensureContainer(p1Path + "Fb");
            Container fldr1aa = ContainerManager.ensureContainer(p1Path + "Fa/Faa");
            Container fldr1aaa = ContainerManager.ensureContainer(p1Path + "Fa/Faa/Faaa");

            defineCrossFolderProperties(fldr1a, fldr1b);
            defineCrossFolderProperties(fldr1aa, fldr1b);
            defineCrossFolderProperties(fldr1aaa, fldr1b);

            Container c;
            c = ContainerManager.getForPath("/_ontMgrTestP1/Fb");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa/Faa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrTestP1");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());


        }

        private void deleteMoveTestContainers() throws SQLException
        {
            Container c;
            c = ContainerManager.getForPath("/_ontMgrTestP2/Fc");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrTestP1/Fb");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrTestP2/Fa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa/Faa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrTestP2/Fa/Faa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrTestP2/Fa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrTestP2/_ontMgrDemotePromoteFa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrTestP2/_ontMgrDemotePromoteFa/Faa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrTestP2/_ontMgrDemotePromoteFa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrTestP2");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrTestP1");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFc");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFb");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFa/Faa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/Fa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/Fa/Faa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());
            c = ContainerManager.getForPath("/Fa");
            if (null!= c)  ContainerManager.delete(c, TestContext.get().getUser());

        }

        @Test
        public void testTransactions() throws SQLException
		{
            try
            {
                Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
                //Clean up last time's mess
                OntologyManager.deleteAllObjects(c, TestContext.get().getUser());
                assertEquals(0L, OntologyManager.getObjectCount(c));

                String ownerObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
                String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

                //Create objects in a transaction & make sure they are all gone.
                OntologyObject oParent;
                OntologyObject oChild;
                String strProp;
                String intProp;

                try
                {
                    OntologyManager.getExpSchema().getScope().beginTransaction();
                    OntologyManager.ensureObject(c, childObjectLsid, ownerObjectLsid);
                    oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
                    assertNotNull(oParent);
                    oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                    assertNotNull(oChild);

                    strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                    OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                    intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                        OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));
                }
                finally
                {
                    OntologyManager.getExpSchema().getScope().closeConnection();
                }

                assertEquals(0L, OntologyManager.getObjectCount(c));
                oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
                assertNull(oParent);

                OntologyManager.ensureObject(c, childObjectLsid, ownerObjectLsid);
                oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
                assertNotNull(oParent);
                oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);

                strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                //Rollback transaction for one new property
                try
                {
                    OntologyManager.getExpSchema().getScope().beginTransaction();
                    intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                    OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));
                }
                finally
                {
                    OntologyManager.getExpSchema().getScope().closeConnection();
                }

                oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);
                Map<String, Object> m = OntologyManager.getProperties(c, childObjectLsid);
                assertNotNull(m.get(strProp));
                assertNull(m.get(intProp));

                try
                {
                    OntologyManager.getExpSchema().getScope().beginTransaction();
                    intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                    OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));
                }
                finally
                {
                    OntologyManager.getExpSchema().getScope().commitTransaction();
                }

                m = OntologyManager.getProperties(c, childObjectLsid);
                assertNotNull(m.get(strProp));
                assertNotNull(m.get(intProp));

                OntologyManager.deleteAllObjects(c, TestContext.get().getUser());
                assertEquals(0L, OntologyManager.getObjectCount(c));
                assertTrue(ContainerManager.delete(c, TestContext.get().getUser()));
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

        @Test
        public void testDomains() throws Exception
        {
            Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
            //Clean up last time's mess
            OntologyManager.deleteAllObjects(c, TestContext.get().getUser());
            assertEquals(0L, OntologyManager.getObjectCount(c));
            String ownerObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
            String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

            OntologyManager.ensureObject(c, childObjectLsid, ownerObjectLsid);
            OntologyObject oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
            assertNotNull(oParent);
            OntologyObject oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
            assertNotNull(oChild);

            String domURIa = new Lsid("Junit", "DD", "Domain1").toString();
            String strPropURI = new Lsid("Junit", "PD", "Domain1.stringProp").toString();
            String intPropURI = new Lsid("Junit", "PD", "Domain1.intProp").toString();

            DomainDescriptor dd = ensureDomainDescriptor(domURIa, "Domain1", c);
            assertNotNull(dd);

            PropertyDescriptor pdStr = new PropertyDescriptor();
            pdStr.setPropertyURI(strPropURI);
            pdStr.setRangeURI(PropertyType.STRING.getTypeUri());
            pdStr.setContainer(c);
            pdStr.setName("Domain1.stringProp");

            pdStr = ensurePropertyDescriptor(pdStr);
            assertNotNull(pdStr);

            PropertyDescriptor pdInt = ensurePropertyDescriptor(intPropURI, PropertyType.INTEGER.getTypeUri(), "Domain1.intProp", c);

            ensurePropertyDomain(pdStr, dd);
            ensurePropertyDomain(pdInt, dd);

            PropertyDescriptor[] pds = getPropertiesForType(domURIa, c);
            assertEquals(2, pds.length);
            Map<String, PropertyDescriptor>  mPds = new HashMap<String,PropertyDescriptor>();
            for(PropertyDescriptor pd1  : pds)
                mPds.put( pd1.getPropertyURI(), pd1);

            assertTrue(mPds.containsKey(strPropURI));
            assertTrue(mPds.containsKey(intPropURI));

            ObjectProperty strProp = new ObjectProperty(childObjectLsid, c, strPropURI, "String value");
            ObjectProperty intProp = new ObjectProperty(childObjectLsid, c, intPropURI, 42);
            OntologyManager.insertProperties(c, ownerObjectLsid, strProp);
            OntologyManager.insertProperties(c, ownerObjectLsid, intProp);

            OntologyManager.deleteType(domURIa, c);
            assertEquals(0L, OntologyManager.getObjectCount(c));
            assertTrue(ContainerManager.delete(c, TestContext.get().getUser()));
        }
	}


	private static long getObjectCount(Container container) throws SQLException
	{
		String sql = "SELECT COUNT(*) FROM " + getTinfoObject() + " WHERE Container = ?";
		return Table.executeSingleton(getExpSchema(), sql, new Object[]{container.getId()}, Long.class).longValue();
	}


    /**
     * v.first value IN/OUT parameter
     * v.second mvIndicator OUT parameter
     */
    public static void convertValuePair(PropertyDescriptor pd, PropertyType pt, Pair<Object,String> v)
    {
        if (v.first == null)
            return;

        // Handle field-level QC
        if (v.first instanceof MvFieldWrapper)
        {
            MvFieldWrapper mvWrapper = (MvFieldWrapper) v.first;
            v.second = mvWrapper.getMvIndicator();
            v.first = mvWrapper.getValue();
        }
        else if (pd.isMvEnabled())
        {
            // Not all callers will have wrapped an MV value if there isn't also
            // a real value
            if (MvUtil.isMvIndicator(v.first.toString(), pd.getContainer()))
            {
                v.second = v.first.toString();
                v.first = null;
            }
        }

        if (null != v.first && null != pt)
            v.first = pt.convert(v.first);
    }


    public static class PropertyRow
	{
		protected int objectId;
		protected int propertyId;
		protected char typeTag;
		protected Double floatValue;
		protected String stringValue;
		protected Date dateTimeValue;
        protected String mvIndicator;

        public PropertyRow()
		{
		}

		public PropertyRow(int objectId, PropertyDescriptor pd, Object value, PropertyType pt)
		{
			this.objectId = objectId;
			this.propertyId = pd.getPropertyId();
			this.typeTag = pt.getStorageType();

            Pair<Object,String> p = new Pair<Object,String>(value,null);
            convertValuePair(pd, pt, p);
            mvIndicator = p.second;
            
            switch (pt)
            {
                case STRING:
                case MULTI_LINE:
                case ATTACHMENT:
                case FILE_LINK:
                case RESOURCE:
                    this.stringValue = (String)p.first;
                    break;
                case DATE_TIME:
                    this.dateTimeValue = (java.util.Date)p.first;
                    break;
                case INTEGER:
                case DOUBLE:
                    Number n = (Number)p.first;
                    if (null != n)
                        this.floatValue = n.doubleValue();
                    break;
                case BOOLEAN:
                    Boolean b = (Boolean)p.first;
                    this.floatValue = b == Boolean.TRUE ? 1.0 : 0.0;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown property type: " + pt);
            }
		}

		public int getObjectId()
		{
			return objectId;
		}

		public void setObjectId(int objectId)
		{
			this.objectId = objectId;
		}

		public int getPropertyId()
		{
			return propertyId;
		}

		public void setPropertyId(int propertyId)
		{
			this.propertyId = propertyId;
		}

		public char getTypeTag()
		{
			return typeTag;
		}

		public void setTypeTag(char typeTag)
		{
			this.typeTag = typeTag;
		}

		public Double getFloatValue()
		{
			return floatValue;
		}

        public Boolean getBooleanValue()
        {
            if (floatValue == null)
            {
                return null;
            }
            return floatValue.doubleValue() == 1.0;
        }

		public void setFloatValue(Double floatValue)
		{
			this.floatValue = floatValue;
		}

		public String getStringValue()
		{
			return stringValue;
		}

		public void setStringValue(String stringValue)
		{
			this.stringValue = stringValue;
		}

		public Date getDateTimeValue()
		{
			return dateTimeValue;
		}

		public void setDateTimeValue(Date dateTimeValue)
		{
			this.dateTimeValue = dateTimeValue;
		}

        public String getMvIndicator()
        {
            return mvIndicator;
        }

        public void setMvIndicator(String mvIndicator)
        {
            this.mvIndicator = mvIndicator;
        }

        public Object getObjectValue()
        {
            return stringValue != null ? stringValue : floatValue != null ? floatValue : dateTimeValue;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("PropertyRow: ");

            sb.append("objectId=" + objectId);
            sb.append(", propertyId=" + propertyId);
            sb.append(", value=");

            if (stringValue != null)
                sb.append(stringValue);
            else if (floatValue != null)
                sb.append(floatValue);
            else if (dateTimeValue != null)
                sb.append(dateTimeValue);
            else
                sb.append("null");

            if (mvIndicator != null)
                sb.append(", mvIndicator=" + mvIndicator);

            return sb.toString();
        }
    }

    public static DbSchema getExpSchema() {
        return DbSchema.get("exp");
    }

    public static SqlDialect getSqlDialect() {
        return getExpSchema().getSqlDialect();
    }

     public static TableInfo getTinfoPropertyDomain() {
         return getExpSchema().getTable("PropertyDomain");
     }

    public static TableInfo getTinfoObject()
    {
        return getExpSchema().getTable("Object");
    }

    public static TableInfo getTinfoObjectProperty()
    {
        return getExpSchema().getTable("ObjectProperty");
    }

    public static TableInfo getTinfoIndexInteger()
    {
        return getExpSchema().getTable("IndexInteger");
    }

    public static TableInfo getTinfoIndexVarchar()
    {
        return getExpSchema().getTable("IndexVarchar");
    }

    public static TableInfo getTinfoPropertyDescriptor()
    {
        return getExpSchema().getTable("PropertyDescriptor");
    }

    public static TableInfo getTinfoDomainDescriptor()
    {
        return getExpSchema().getTable("DomainDescriptor");
    }

    public static TableInfo getTinfoObjectPropertiesView()
    {
        return getExpSchema().getTable("ObjectPropertiesView");
    }

    public static String doProjectColumnCheck(boolean bFix) throws SQLException
    {
        StringBuilder msgBuffer = new StringBuilder();
        String descriptorTable=getTinfoPropertyDescriptor().toString();
        String uriColumn = "PropertyURI";
        String idColumn = "PropertyID";
        doProjectColumnCheck(descriptorTable, uriColumn, idColumn, msgBuffer, bFix);

        descriptorTable=getTinfoDomainDescriptor().toString();
        uriColumn = "DomainURI";
        idColumn = "DomainID";
        doProjectColumnCheck(descriptorTable, uriColumn, idColumn, msgBuffer, bFix);

        return msgBuffer.toString();
    }

    private static void doProjectColumnCheck(String descriptorTable, String uriColumn, String idColumn, StringBuilder msgBuffer, boolean bFix) throws SQLException
    {
        ResultSet rs =null;
        String projectId;
        String containerId;
        String newProjectId;
        // get all unique combos of Container, project
        try {
            String sql = "SELECT Container, Project FROM " + descriptorTable + " GROUP BY Container, Project";
            rs = Table.executeQuery(getExpSchema(), sql, new Object[]{});
            while (rs.next())
            {
                containerId = rs.getString("Container");
                projectId = rs.getString("Project");
                Container container = ContainerManager.getForId(containerId);
                if (null==container)
                    continue;  // should be handled by container check
                newProjectId = container.getProject() == null ? container.getId() : container.getProject().getId();
                if (!projectId.equals(newProjectId))
                {
                   if  (bFix)
                   {
                       try {
                            fixProjectColumn(descriptorTable, uriColumn, idColumn, container, projectId, newProjectId);
                           msgBuffer.append("<br/>&nbsp;&nbsp;&nbsp;Fixed inconsistent project ids found for " + descriptorTable
                                   + " in folder " + ContainerManager.getForId(containerId).getPath());

                       } catch (SQLException se) {
                           msgBuffer.append("<br/>&nbsp;&nbsp;&nbsp;ERROR:  Failed to fix inconsistent project ids found for " + descriptorTable
                                    + " due to " + se.getMessage() );
                       }
                   }
                   else
                        msgBuffer.append("<br/>&nbsp;&nbsp;&nbsp;ERROR:  Inconsistent project ids found for " + descriptorTable + " in folder " +
                               container.getPath());
                }
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

    }
    private static void fixProjectColumn(String descriptorTable, String uriColumn, String idColumn, Container container, String projectId, String newProjId) throws SQLException
    {
        String sql =  "UPDATE " + descriptorTable + " SET Project= ? WHERE Project = ? AND Container=? AND " + uriColumn + " NOT IN " +
                "(SELECT " + uriColumn + " FROM " + descriptorTable + " WHERE Project = ?)" ;
        Table.execute(getExpSchema(), sql, newProjId, projectId, container.getId(), newProjId);

        // now check to see if there is already an existing descriptor in the target (correct) project.
        // this can happen if a folder containning a descriptor is moved to another project
        // and the OntologyManager's containerMoved handler fails to fire for some reason. (note not in transaction)
        //  If this is the case, the descriptor is redundant and it should be deleted, after we move the objects that depend on it.

        sql= " SELECT prev." + idColumn + " AS PrevIdCol, cur." + idColumn + " AS CurIdCol FROM " + descriptorTable + " prev "
                        + " INNER JOIN " + descriptorTable + " cur ON (prev." + uriColumn + "=  cur." + uriColumn + " ) "
                        + " WHERE cur.Project = ? AND prev.Project= ? AND prev.Container = ? ";
        String updsql1 = " UPDATE " + getTinfoObjectProperty() + " SET " + idColumn + " = ? WHERE " + idColumn + " = ? ";
        String updsql2 = " UPDATE " + getTinfoPropertyDomain() + " SET " + idColumn + " = ? WHERE " + idColumn + " = ? ";
        String delSql =   " DELETE FROM " + descriptorTable + " WHERE " + idColumn + " = ? ";
        ResultSet rs = null;
        try {
            rs = Table.executeQuery(getExpSchema(), sql, new Object[]{newProjId, projectId, container.getId()});
            while (rs.next())
            {
                int prevPropId=rs.getInt(1);
                int curPropId=rs.getInt(2);
                Table.execute(getExpSchema(), updsql1, curPropId, prevPropId);
                Table.execute(getExpSchema(), updsql2, curPropId, prevPropId);
                Table.execute(getExpSchema(), delSql, prevPropId);
            }
        } finally
        {
            if (null != rs)
                rs.close();
        }
    }

    static public PropertyDescriptor updatePropertyDescriptor(User user, DomainDescriptor dd, PropertyDescriptor pdOld, PropertyDescriptor pdNew, int sortOrder) throws ChangePropertyDescriptorException
    {
        try
        {
            PropertyType oldType = pdOld.getPropertyType();
            PropertyType newType = pdNew.getPropertyType();
            if (oldType.getStorageType() != newType.getStorageType())
            {
                int count = Table.executeSingleton(getExpSchema(), "SELECT COUNT(ObjectId) FROM exp.ObjectProperty WHERE PropertyId = ?", new Object[] { pdOld.getPropertyId() }, Integer.class).intValue();
                if (count != 0)
                {
                    throw new ChangePropertyDescriptorException("This property type cannot be changed because there are existing values.");
                }
            }
            validatePropertyDescriptor(pdNew);
            PropertyDescriptor update = Table.update(user, getTinfoPropertyDescriptor(), pdNew, pdOld.getPropertyId());

            ensurePropertyDomain(pdNew, dd, sortOrder);

            return update;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private static void validatePropertyDescriptor(PropertyDescriptor pd)
            throws ChangePropertyDescriptorException
    {
        validateValue(pd.getName(), "Name", null);
        validateValue(pd.getPropertyURI(), "PropertyURI", "Please use a shorter field name.");
        validateValue(pd.getLabel(), "Label", null);
        validateValue(pd.getImportAliases(), "ImportAliases", null);
        validateValue(pd.getURL() != null ? pd.getURL().getSource() : null, "URL", null);
        validateValue(pd.getOntologyURI(), "OntologyURI", null);
        validateValue(pd.getConceptURI(), "ConceptURI", null);
        validateValue(pd.getSemanticType(), "SemanticType", null);
        validateValue(pd.getRangeURI(), "RangeURI", null);
    }

    private static void validateValue(String value, String columnName, String extraMessage) throws ChangePropertyDescriptorException
    {
        int maxLength = getTinfoPropertyDescriptor().getColumn(columnName).getScale();
        if (value != null && value.length() > maxLength)
        {
            throw new ChangePropertyDescriptorException(columnName + " cannot exceed " + maxLength + " characters, but was " + value.length() + " characters long. " + (extraMessage == null ? "" : extraMessage));
        }
    }

    static public boolean checkObjectExistence(String lsid)
    {
        ResultSet rs = null;
        try
        {
            rs = Table.select(getTinfoObject(), Table.ALL_COLUMNS, new SimpleFilter("ObjectURI", lsid), null);
            return (rs.next());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }
    }



    static public void indexConcepts(SearchService.IndexTask task)
    {
        if (1==1)
            return;
        if (null == task)
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            task = null == ss ? null : ss.createTask("Index Concepts");
            if (null == task)
                return;
        }

        final SearchService.IndexTask t = task;
        task.addRunnable(new Runnable(){
            public void run()
            {
                _indexConcepts(t);
            }
        }, SearchService.PRIORITY.bulk);
    }


    final public static SearchService.SearchCategory conceptCategory = new SearchService.SearchCategory("concept", "Concepts and Property Descriptors");

    private static class ConceptMapFactory extends RowMapFactory
    {
        int rsName;
        int rsSearchTerms;
        int rsPropertyUri;
        int rsDescription;
        int rsSemanticType;
        int rsConceptUri;
        int rsLabel;
        int rsContainer;

        ConceptMapFactory(ResultSet rs) throws SQLException
        {
            Map<String, Integer> findMap = getFindMap();
            
            rsName = rs.findColumn("name");
            findMap.put("name",findMap.size());

            rsSearchTerms = rs.findColumn("searchTerms");
            findMap.put("searchTerms",findMap.size());

            rsPropertyUri = rs.findColumn("propertyUri");
            findMap.put("propertyUri",findMap.size());

            rsDescription = rs.findColumn("description");
            findMap.put("description", findMap.size());

            rsSemanticType = rs.findColumn("semanticType");
            findMap.put("semanticType", findMap.size());

            rsLabel = rs.findColumn("label");
            findMap.put("label", findMap.size());

            rsContainer = rs.findColumn("container");
            findMap.put("container", findMap.size());

            findMap.put(SearchService.PROPERTY.categories.toString(), findMap.size());
            findMap.put(SearchService.PROPERTY.displayTitle.toString(), findMap.size());
            findMap.put(SearchService.PROPERTY.securableResourceId.toString(), findMap.size());
        }

        Map<String, Object> getRowMap(ResultSet rs) throws SQLException
        {
            RowMap<Object> map = super.getRowMap();
            List<Object> list = map.getRow();
            list.add(rs.getString(rsName));
            list.add(rs.getString(rsSearchTerms));
            list.add(rs.getString(rsPropertyUri));
            list.add(rs.getString(rsDescription));
            list.add(rs.getString(rsSemanticType));
            list.add(rs.getString(rsLabel));
            list.add(rs.getString(rsContainer));
            list.add(conceptCategory.toString());
            list.add(null); // title
            list.add(null); // securableResourceId
            return map;
        }
    }

    static private void _indexConcepts(SearchService.IndexTask task)
    {
        ResultSet rs = null;
        Container shared = ContainerManager.getSharedContainer();

        try
        {
            rs = Table.executeQuery(getExpSchema(),
                    "SELECT * FROM exp.PropertyDescriptor WHERE Container=? AND rangeuri='xsd:nil'",
                    new Object[] {shared.getId()}, Table.ALL_ROWS, false); // new Object[] {shared});
            ConceptMapFactory f = new ConceptMapFactory(rs);
            while (rs.next())
            {
                Map<String,Object> m = f.getRowMap(rs);
                String propertyURI = (String)m.get("propertyUri");
                m.put(PROPERTY.displayTitle.toString(), propertyURI);

                String desc = (String)m.get("description");
                String label = (String)m.get("label");
                String name = (String)m.get("name");
                String body = StringUtils.trimToEmpty(name) + " " +
                        StringUtils.trimToEmpty(label) + " " +
                        StringUtils.trimToEmpty(desc);

                ActionURL url = new ActionURL("experiment-types", "findConcepts", shared);
                url.addParameter("concept",propertyURI);
                WebdavResource r = new SimpleDocumentResource(
                    new Path(propertyURI),
                    "concept:" + propertyURI,
                    shared.getId(),
                    "text/plain", body.getBytes(),
                    url,
                    m
                );
                task.addResource(r, SearchService.PRIORITY.item);
            }
        }
        catch (SQLException sqlx)
        {
            throw new RuntimeSQLException(sqlx);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }
}
