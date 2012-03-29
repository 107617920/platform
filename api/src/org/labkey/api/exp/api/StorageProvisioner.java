/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
package org.labkey.api.exp.api;

import com.google.gwt.storage.client.Storage;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MVDisplayColumnFactory;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.AliasManager;
import org.labkey.api.security.User;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: newton
 * Date: Aug 11, 2010
 * Time: 2:52:33 PM
 */

public class StorageProvisioner
{
    private static final Logger log = Logger.getLogger(StorageProvisioner.class);
    private static final CPUTimer create = new CPUTimer("StorageProvisioner.create");


    private static String _create(DbScope scope, DomainKind kind, Domain domain) throws SQLException
    {
        assert create.start();
        Connection conn;

        try
        {
            scope.ensureTransaction();

            // reselect in a transaction
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(domain.getTypeId(), true);
            if (null == dd)
            {
                Logger.getLogger(StorageProvisioner.class).warn("Can't find domain desciptor: " + domain.getTypeId() + " " + domain.getTypeURI());
                return null;
            }
            String tableName = dd.getStorageTableName();
            if (null != tableName)
                return tableName;

            tableName = makeTableName(kind, domain);

            TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.CreateTable);

            Set<String> base = Sets.newCaseInsensitiveHashSet();

            for (PropertyStorageSpec spec : kind.getBaseProperties())
            {
                change.addColumn(spec);
                base.add(spec.getName());
            }

            for (DomainProperty property : domain.getProperties())
            {
                if (base.contains(property.getName()))
                {
                    // apparently this is a case where the domain allows a propertydescriptor to be defined with the same
                    // name as a built-in column. e.g. to allow setting overrides?
                    log.info("StorageProvisioner ignored property with name of build-in column: " + property.getPropertyURI());
                    continue;
                }

                change.addColumn(property.getPropertyDescriptor());
                if (property.isMvEnabled())
                {
                    change.addColumn(makeMvColumn(property));
                }
            }

            change.setIndexedColumns(kind.getPropertyIndices());

            conn = scope.getConnection();

            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.debug("Will issue: " + sql);
                conn.prepareStatement(sql).execute();
            }

            dd.setStorageTableName(tableName);
            dd.setStorageSchemaName(kind.getStorageSchemaName());
            OntologyManager.updateDomainDescriptor(dd);

            scope.releaseConnection(conn);
            conn = null;

            scope.commitTransaction();
            return tableName;
        }
        finally
        {
            scope.closeConnection();
            assert create.stop();
        }
    }

    private static PropertyStorageSpec makeMvColumn(DomainProperty property)
    {
        return new PropertyStorageSpec(property.getName() + "_" + MvColumn.MV_INDICATOR_SUFFIX, Types.VARCHAR, 50);
    }


    public static void drop(Domain domain)
    {
        if (null == domain)
            return;
        DomainKind kind = domain.getDomainKind();
        if (kind == null)
        {
            log.warn("domain " + domain.getName() + " has no DomainKind");
            return;
        }

        DbScope scope = kind.getScope();
        String schemaName = kind.getStorageSchemaName();
        if (scope == null || schemaName == null)
            return;

        String tableName = domain.getStorageTableName();
        if (null == tableName)
        {
            return;
        }

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.DropTable);

        Connection con = null;
        try
        {
            con = scope.getConnection();
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.debug("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        catch (SQLException e)
        {
            log.warn(String.format("Failed to drop table in schema %s for domain %s - %s",
                    schemaName, domain.getName(), e.getMessage()), e);
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }


    public static void addProperties(Domain domain, Collection<DomainProperty> properties) throws SQLException
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();
        if (null == tableName)
        {
            log.warn("addProperties() called before table is provisioned: " + domain.getTypeURI());
            tableName = _create(scope, kind, domain);
            return;
        }

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.AddColumns);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties())
            base.add(s.getName());

        for (DomainProperty prop : properties)
        {
            if (prop.getName() == null || prop.getName().length() == 0)
                throw new IllegalArgumentException("Can't add property with no name: " + prop.getPropertyURI());

            if (base.contains(prop.getName()))
            {
                // apparently this is a case where the domain allows a propertydescriptor to be defined with the same
                // name as a built-in column. e.g. to allow setting overrides?
                log.warn("StorageProvisioner ignored property with name of build-in column: " + prop.getPropertyURI());
                continue;
            }
            change.addColumn(prop.getPropertyDescriptor());
            if (prop.isMvEnabled())
            {
                change.addColumn(makeMvColumn(prop));
            }
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.debug("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    public static void dropMvIndicator(DomainProperty... props) throws SQLException
    {
        assert (props.length > 0);
        Domain domain = props[0].getDomain();
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a trasaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.DropColumns);
        for (DomainProperty prop : props)
        {
            change.addColumn(makeMvColumn(prop));
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.debug("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    public static void addMvIndicator(DomainProperty... props) throws SQLException
    {
        assert (props.length > 0);
        Domain domain = props[0].getDomain();
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();
        if (null == tableName)
            tableName = makeTableName(kind, domain);

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.AddColumns);
        for (DomainProperty prop : props)
        {
            change.addColumn(makeMvColumn(prop));
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.debug("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }


    public static void dropProperties(Domain domain, Collection<DomainProperty> properties) throws SQLException
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a trasaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties())
            base.add(s.getName());

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.DropColumns);
        for (DomainProperty prop : properties)
        {
            if (base.contains(prop.getName()))
                continue;
            change.addColumn(prop.getPropertyDescriptor());
            if (prop.isMvEnabled())
            {
                change.addColumn(makeMvColumn(prop));
            }
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.debug("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    /**
     * @param propsRenamed map where keys are the current properties including the new names, values are the old column names.
     */
    public static void renameProperties(Domain domain, Map<DomainProperty, String> propsRenamed) throws SQLException
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a trasaction with propertydescriptor changes
        assert scope.isTransactionActive();

        Connection con = null;

        try
        {
            con = scope.getConnection();
            TableChange renamePropChange = new TableChange(kind.getStorageSchemaName(), domain.getStorageTableName(), TableChange.ChangeType.RenameColumns);

            Set<String> base = Sets.newCaseInsensitiveHashSet();
            for (PropertyStorageSpec s : kind.getBaseProperties())
                base.add(s.getName());

            for (Map.Entry<DomainProperty, String> rename : propsRenamed.entrySet())
            {
                PropertyStorageSpec prop = new PropertyStorageSpec(rename.getKey().getPropertyDescriptor());
                String oldPropName = rename.getValue();
                renamePropChange.addColumnRename(oldPropName, prop.getName());

                if (base.contains(oldPropName))
                {
                    throw new IllegalArgumentException("Cannot rename built-in column " + oldPropName);
                }
                else if (base.contains(prop.getName()))
                {
                    throw new IllegalArgumentException("Cannot rename " + oldPropName + " to built-in column name " + prop.getName());
                }

                if (prop.isMvEnabled())
                {
                    renamePropChange.addColumnRename(PropertyStorageSpec.getMvIndicatorColumnName(oldPropName), prop.getMvIndicatorColumnName());
                }

            }

            for (String sql : scope.getSqlDialect().getChangeStatements(renamePropChange))
            {
                log.debug("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }

        }
        finally
        {
            scope.releaseConnection(con);
        }
    }


    public static String makeTableName(DomainKind kind, Domain domain)
    {
        String rawTableName = String.format("c%sd%s_%s", domain.getContainer().getRowId(), domain.getTypeId(), domain.getName());
        SqlDialect dialect = kind.getScope().getSqlDialect();
        String alias = AliasManager.makeLegalName(rawTableName.toLowerCase(), dialect);
        alias = alias.replaceAll("_+", "_");
        return alias;
    }


    /**
     * return a TableInfo for this domain, creating if necessary
     * this method DOES NOT cache
     *
     * @param parentSchema Schema to attach table to, should NOT be the physical db schema of the storage provider
     */

    public static SchemaTableInfo createTableInfo(Domain domain, DbSchema parentSchema)
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();
        String schemaName = kind.getStorageSchemaName();

        if (null == scope || null == schemaName)
            throw new IllegalArgumentException();

        Connection conn = null;
        try
        {
            String tableName = domain.getStorageTableName();

            if (null == tableName)
                tableName = _create(scope, kind, domain);

            SchemaTableInfo ti = new SchemaTableInfo(parentSchema, DatabaseTableType.TABLE, tableName, tableName, schemaName + ".\"" + tableName + "\"");
            ti.setMetaDataSchemaName(schemaName);

            conn = scope.getConnection();
//            ti.loadFromMetaData(conn.getMetaData(), scope.getDatabaseName(), schemaName);

            int index = 0;

            for (DomainProperty p : domain.getProperties())
            {
                ColumnInfo c = ti.getColumn(p.getName());

                if (null == c)
                {
                    Logger.getLogger(StorageProvisioner.class).info("Column not found in storage table: " + tableName + "." + p.getName());
                    continue;
                }

                // The columns coming back from JDBC metadata aren't necessarily in the same order that the domain
                // wants them based on its current property order
                ti.setColumnIndex(c, index++);
                PropertyColumn.copyAttributes(null, c, p.getPropertyDescriptor(), p.getContainer());

                if (p.isMvEnabled())
                {
                    c.setDisplayColumnFactory(new MVDisplayColumnFactory());

                    ColumnInfo mvColumn = ti.getColumn(PropertyStorageSpec.getMvIndicatorColumnName(p.getName()));
                    assert mvColumn != null : "No MV column found for " + p.getName();
                    if (mvColumn != null)
                    {
                        c.setMvColumnName(mvColumn.getName());
                        mvColumn.setMvIndicatorColumn(true);
                        // The UI for the main column will include MV input as well, so no need for another column in insert/update views
                        mvColumn.setShownInUpdateView(false);
                        mvColumn.setShownInInsertView(false);
                    }
                }
                c.setScale(p.getScale());
            }

            return ti;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            if (null != conn)
                scope.releaseConnection(conn);
        }
    }


    /**
     * We are mostly makeing the storage table match the existing property descriptors, because that is easiest.
     * Sometimes it would be better or more conservative to update the property descriptors instead
     */

    public static boolean repairDomain(Container c, String domainUri, BindException errors)
    {
        SqlDialect dialect = DbSchema.get("core").getSqlDialect();
        DbScope scope = DbSchema.get("core").getScope();

        try
        {
            scope.ensureTransaction();
            Connection conn = scope.getConnection();
            Domain domain = PropertyService.get().getDomain(c, domainUri);
            if (null == domain)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Could not find domain: " + domainUri);
                return false;
            }
            DomainKind kind = domain.getDomainKind();
            if (null == kind)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Could not find domain kind: " + domainUri);
                return false;
            }
            ProvisioningReport preport = getProvisioningReport(domainUri);
            if (preport.getProvisionedDomains().size() != 1)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Could not generate report.");
                return false;
            }
            ProvisioningReport.DomainReport report = preport.getProvisionedDomains().iterator().next();

            TableChange drops = new TableChange(kind.getStorageSchemaName(), domain.getStorageTableName(), TableChange.ChangeType.DropColumns);
            boolean hasDrops = false;
            TableChange adds = new TableChange(kind.getStorageSchemaName(), domain.getStorageTableName(), TableChange.ChangeType.AddColumns);
            boolean hasAdds = false;

            for (ProvisioningReport.ColumnStatus st : report.getColumns())
            {
                if (!st.hasProblem)
                    continue;
                if (st.spec == null && st.prop == null)
                {
                    if (null != st.colName)
                    {
                        drops.dropColumnExactName(st.colName);
                        hasDrops = true;
                    }
                    if (null != st.mvColName)
                    {
                        drops.dropColumnExactName(st.mvColName);
                        hasDrops = true;
                    }
                }
                else if (st.prop != null)
                {
                    if (st.colName == null)
                    {
                        adds.addColumn(st.prop.getPropertyDescriptor());
                        hasAdds = true;
                    }
                    if (st.mvColName == null && st.prop.isMvEnabled())
                    {
                        adds.addColumn(makeMvColumn(st.prop));
                        hasAdds = true;
                    }
                    if (st.mvColName != null && !st.prop.isMvEnabled())
                    {
                        drops.dropColumnExactName(st.mvColName);
                        hasDrops = true;
                    }
                }
            }

            if (hasDrops)
                for (String sql : scope.getSqlDialect().getChangeStatements(drops))
                {
                    log.debug("Will issue: " + sql);
                    conn.prepareStatement(sql).execute();
                }
            if (hasAdds)
                for (String sql : scope.getSqlDialect().getChangeStatements(adds))
                {
                    log.debug("Will issue: " + sql);
                    conn.prepareStatement(sql).execute();
                }
            kind.invalidate(domain);
            scope.commitTransaction();
            return !errors.hasErrors();
        }
        catch (Exception x)
        {
            errors.reject(SpringActionController.ERROR_MSG, x.getMessage());
            return false;
        }
        finally
        {
            scope.closeConnection();
        }
    }


    public static ProvisioningReport getProvisioningReport() throws SQLException
    {
        return getProvisioningReport(null);
    }

    public static ProvisioningReport getProvisioningReport(@Nullable String domainuri) throws SQLException
    {
        ProvisioningReport report = new ProvisioningReport();
        SQLFragment sql = new SQLFragment("SELECT domainid, name, storageschemaname, storagetablename FROM " +
                OntologyManager.getTinfoDomainDescriptor().getFromSQL("dd"));
        if (null != domainuri)
        {
            sql.append(" WHERE domainuri=?");
            sql.add(domainuri);
        }
        ResultSet rs = null;

        try
        {
            rs = Table.executeQuery(OntologyManager.getExpSchema(), sql);
            while (rs.next())
            {
                ProvisioningReport.DomainReport domain = new ProvisioningReport.DomainReport();
                domain.setId(rs.getInt("domainid"));
                domain.setName(rs.getString("name"));
                if (rs.getString("storagetablename") == null)
                {
                    report.addUnprovisioned(domain);
                }
                else
                {
                    domain.setSchemaName(rs.getString("storageschemaname"));
                    domain.setTableName(rs.getString("storagetablename"));
                    report.addProvisioned(domain);
                }

            }
        }
        finally
        {
            rs.close();
        }
        
        // TODO: Switch to normal schema/table cache (now that we actually use a cache for them)
        Map<String,DbSchema> schemas = new HashMap<String, DbSchema>();

        for (ProvisioningReport.DomainReport domainReport : report.getProvisionedDomains())
        {
            DbSchema schema = schemas.get(domainReport.getSchemaName());
            if (schema == null)
            {
                try
                {
                    schema = DbSchema.createFromMetaData(domainReport.getSchemaName());
                    schemas.put(domainReport.getSchemaName(), schema);
                }
                catch (Exception e)
                {
                    domainReport.addError("error resolving schema " + domainReport.getSchemaName() + " - " + e.getMessage());
                    continue;
                }
            }

            TableInfo table = schema.getTable(domainReport.getTableName());
            if (table == null)
            {
                domainReport.addError(String.format("metadata for domain %s specifies a database table at %s.%s but that table is not present",
                        domainReport.getName(), domainReport.getSchemaName(), domainReport.getTableName()));
                continue;
            }
            Set<String> hardColumnNames = Sets.newCaseInsensitiveHashSet(table.getColumnNameSet());
            Domain domain = PropertyService.get().getDomain(domainReport.getId());
            if (domain == null)
            {
                domainReport.addError(String.format("Could not find a domain for %s.%s",
                        domainReport.getSchemaName(), domainReport.getTableName()));
                continue;
            }
            DomainKind kind = domain.getDomainKind();
            if (kind == null)
            {
                domainReport.addError(String.format("Could not find a domain kind for %s.%s",
                        domainReport.getSchemaName(), domainReport.getTableName()));
                continue;
            }
            for (DomainProperty domainProp : domain.getProperties())
            {
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.columns.add(status);
                status.prop = domainProp;
                if (hardColumnNames.remove(domainProp.getName()))
                    status.colName = domainProp.getName();
                else
                {
                    domainReport.addError(String.format("database table %s.%s did not contain expected column '%s'", domainReport.getSchemaName(), domainReport.getTableName(), domainProp.getName()));
                    status.fix = "Create column '" + domainProp.getName() + "'";
                    status.hasProblem = true;
                }
                if (hardColumnNames.remove(PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName())))
                    status.mvColName = PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName());
                if (null == status.mvColName && domainProp.isMvEnabled())
                {
                    domainReport.addError(String.format("database table %s.%s has mvindicator enabled but expected '%s' column wasn't present",
                            domainReport.getSchemaName(), domainReport.getTableName(), PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName())));
                    status.fix += (status.fix.isEmpty() ? "C" : " and c") + "reate column '" + PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName()) + "'";
                    status.hasProblem = true;
                }
                if (null != status.mvColName && !domainProp.isMvEnabled())
                {
                    domainReport.addError(String.format("database table %s.%s has mvindicator disabled but '%s' column is present",
                            domainReport.getSchemaName(), domainReport.getTableName(), PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName())));
                    status.fix += (status.fix.isEmpty() ? "D" : " and d") +  "rop column '" + status.mvColName + "'";
                    status.hasProblem = true;
                }
            }
            for (PropertyStorageSpec spec : kind.getBaseProperties())
            {
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.columns.add(status);
                status.spec = spec;
                if (hardColumnNames.remove(spec.getName()))
                    status.colName = spec.getName();
                else
                {
                    domainReport.addError(String.format("database table %s.%s did not contain expected column '%s'", domainReport.getSchemaName(), domainReport.getTableName(), spec.getName()));
                    status.fix = "'" + spec.getName() + "' is a built-in column.  Contact LabKey support.";
                    status.hasProblem = true;
                }
                if (hardColumnNames.remove(spec.getMvIndicatorColumnName()))
                    status.mvColName = spec.getMvIndicatorColumnName();
                if (null == status.mvColName && spec.isMvEnabled())
                {
                        domainReport.addError(String.format("database table %s.%s has mvindicator enabled but expected '%s' column wasn't present",
                                domainReport.getSchemaName(), domainReport.getTableName(), spec.getMvIndicatorColumnName()));
                        status.fix = "'" + spec.getName() + "' is a built-in column.  Contact LabKey support.";
                        status.hasProblem = true;
                }
                if (null != status.mvColName && !spec.isMvEnabled())
                {
                        domainReport.addError(String.format("database table %s.%s has mvindicator disabled but '%s' column is present",
                                domainReport.getSchemaName(), domainReport.getTableName(), spec.getMvIndicatorColumnName()));
                        status.fix = "'" + spec.getName() + "' is a built-in column.  Contact LabKey support.";
                        status.hasProblem = true;
                }
            }
            for (String name : hardColumnNames.toArray(new String[0]))
            {
                if (name.endsWith("_" + MvColumn.MV_INDICATOR_SUFFIX))
                    continue;
                domainReport.addError(String.format("database table %s.%s has column '%s' without a property descriptor",
                        domainReport.getSchemaName(), domainReport.getTableName(), name));
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.columns.add(status);

                hardColumnNames.remove(name);
                status.colName = name;
                if (hardColumnNames.remove(PropertyStorageSpec.getMvIndicatorColumnName(name)))
                    status.mvColName = PropertyStorageSpec.getMvIndicatorColumnName(name);
                status.fix = "Delete column '" + name + "'" + (null == status.mvColName ? "" : " and column '" + status.mvColName + "'");
                status.hasProblem = true;
            }
            for (String name : hardColumnNames)
            {
                domainReport.addError(String.format("database table %s.%s has column '%s' without a property descriptor",
                        domainReport.getSchemaName(), domainReport.getTableName(), name));
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.columns.add(status);
                status.mvColName = name;
                status.fix = "Delete column '" + name + "'";
                status.hasProblem = true;
            }
            if (!domainReport.errors.isEmpty())
            {
                ActionURL fix = new ActionURL("experiment-types", "repair", domain.getContainer());
                fix.addParameter("domainUri", domain.getTypeURI());
                domainReport.addError("See this page for more info: " + fix.getURIString());
            }
        }

        return report;
    }


    public static class ProvisioningReport
    {
        private Set<DomainReport> unprovisionedDomains = new HashSet<DomainReport>();
        private Set<DomainReport> provisionedDomains = new HashSet<DomainReport>();

        public void addUnprovisioned(DomainReport domain)
        {
            unprovisionedDomains.add(domain);
        }

        public void addProvisioned(DomainReport domain)
        {
            provisionedDomains.add(domain);
        }

        public Set<DomainReport> getUnprovisionedDomains()
        {
            return unprovisionedDomains;
        }

        public Set<DomainReport> getProvisionedDomains()
        {
            return provisionedDomains;
        }

        public int getErrorCount()
        {
            int errors = 0;
            for (DomainReport d : getProvisionedDomains())
            {
                errors += d.getErrors().size();
            }

            return errors;
        }

        public static class ColumnStatus
        {
            public String colName, mvColName;
            public DomainProperty prop;            // propertydescriptor column
            public PropertyStorageSpec spec;       // domainkind/reserved column
            public boolean hasProblem;
            public String fix = "";
            public String getName()
            {
                if (null != prop) return prop.getName();
                if (null != spec) return spec.getName();
                return null;
            }
            public boolean hasMv()
            {
                if (null != prop) return prop.isMvEnabled();
                if (null != spec) return spec.isMvEnabled();
                return false;
            }
        }

        public static class DomainReport
        {
            Integer id;
            String name;
            String schemaName;
            String tableName;
            List<String> errors = new ArrayList<String>();
            List<ColumnStatus> columns = new ArrayList<ColumnStatus>();

            public Integer getId()
            {
                return id;
            }

            public void setId(Integer id)
            {
                this.id = id;
            }

            public String getName()
            {
                return name;
            }

            public void setName(String name)
            {
                this.name = name;
            }

            public String getSchemaName()
            {
                return schemaName;
            }

            public void setSchemaName(String schemaName)
            {
                this.schemaName = schemaName;
            }

            public String getTableName()
            {
                return tableName;
            }

            public void setTableName(String tableName)
            {
                this.tableName = tableName;
            }

            public void addError(String message)
            {
                errors.add(message);
            }

            public List<String> getErrors()
            {
                return errors;
            }

            public List<ColumnStatus> getColumns()
            {
                return columns;
            }
        }
    }

    public static class TestCase extends Assert
    {
        Container container = JunitUtil.getTestContainer();
        Domain domain;
        final String notNullPropName = "a_" + System.currentTimeMillis();
        final String propNameB = "b_" + System.currentTimeMillis();
        final String propBMvColumnName = PropertyStorageSpec.getMvIndicatorColumnName(propNameB).toLowerCase();

        @Before
        public void before() throws Exception
        {
            String domainName = "testdomain_" + System.currentTimeMillis();

            Lsid lsid = new Lsid("TestDatasetDomainKind", "Folder-" + container.getRowId(), domainName);
            domain = PropertyService.get().createDomain(container, lsid.toString(), domainName);
            domain.save(new User());
            StorageProvisioner.createTableInfo(domain, DbSchema.get(domain.getDomainKind().getStorageSchemaName()));
            domain = PropertyService.get().getDomain(domain.getTypeId());
        }

        @After
        public void after() throws Exception
        {
            if (domain != null)
            {
                StorageProvisioner.drop(domain);
                OntologyManager.deleteDomain(domain.getTypeURI(), container);
                domain = null;
            }
        }


        @Test
        public void testAddProperty() throws Exception
        {
            addPropertyB();
            Assert.assertNotNull("adding a property added a new column to the hard table",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propNameB));
        }

        @Test
        public void testDropProperty() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            propB.delete();
            domain.save(new User());
            Assert.assertNull("column for dropped property is gone", getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                    domain.getStorageTableName(), propNameB));
        }

        @Test
        public void testRenameProperty() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            String newName = "new_" + propNameB;
            propB.setName(newName);
            domain.save(new User());
            Assert.assertNull("renamed column is not present in old name",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propNameB));
            Assert.assertNotNull("renamed column is provisioned in new name",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), newName));
        }
/*

    is it actually a functional requirement that isRequired on a prop makes a not null constraint on its column?

        @Test
        public void testNotNullableProperty() throws Exception
        {
            addNotNullProperty();
            ColumnMetadata col = getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), notNullPropName);
            Assert.assertFalse("required property is NOT NULL in db", col.nullable);
        }
*/

        @Test
        public void testEnableMv() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            propB.setMvEnabled(true);
            domain.save(new User());
            ColumnMetadata col = getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propBMvColumnName);
            Assert.assertNotNull("enabled mvindicator causes mvindicator column to be provisioned",
                    col);
        }


        @Test
        public void testDisableMv() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            propB.setMvEnabled(true);
            domain.save(new User());
            propB = domain.getPropertyByName(propNameB);
            propB.setMvEnabled(false);
            domain.save(new User());
            Assert.assertNull("property with disabled mvindicator has no mvindicator column",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propBMvColumnName));
        }

/*

XXX FIXME UNDONE TODO This is a valid test and it fails because we don't handle
renaming a property AND toggling mvindicator on in the same change.

        @Test
        public void testRenameAndEnableMvAtOnce () throws Exception
        {
            // should fail, known problem
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            String newName = "new_" + propNameB;
            String newMvName = PropertyStorageSpec.getMvIndicatorColumnName(newName);
            propB.setName(newName);
            propB.setMvEnabled(true);
            domain.save(new User());
            Assert.assertNull("renamed column is not present in old name",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propNameB));
            Assert.assertNotNull("renamed column is provisioned in new name",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), newName));
            Assert.assertNotNull("enabled mvindicator causes mvindicator column to be provisioned",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), newMvName));
        }

*/


        @Test
        public void testProvisioningReport() throws Exception
        {
            ProvisioningReport report = StorageProvisioner.getProvisioningReport();
            Assert.assertNotNull(report);
            boolean success = true;
            StringBuilder sb = new StringBuilder();
            for (ProvisioningReport.DomainReport dr : report.getProvisionedDomains())
            {
                if (!dr.getErrors().isEmpty())
                {
                    success = false;
                    sb.append(dr.getErrors().toString());
                }
            }
            Assert.assertTrue(sb.toString(), success);
        }

        private void addPropertyB() throws Exception
        {
            DomainProperty dp = domain.addProperty();
            dp.setPropertyURI(propNameB + "#" + propNameB);
            dp.setName(propNameB);
            domain.save(new User());
        }

        private void addNotNullProperty() throws Exception
        {
            DomainProperty dp = domain.addProperty();
            dp.setPropertyURI(notNullPropName + "#" + notNullPropName);
            dp.setName(notNullPropName);
            dp.setRequired(true);
            domain.save(new User());
        }


        // TODO: We have this (or something much like it) in the SqlDialect -- merge!
        private ColumnMetadata getJdbcColumnMetadata(String schema, String table, String column) throws Exception
        {
            Connection con = null;
            ResultSet rs = null;

            try
            {
                con = DbScope.getLabkeyScope().getConnection();
                DatabaseMetaData dbmd = con.getMetaData();
                rs = dbmd.getColumns(null, schema, table, column);

                if (!rs.next())
                    // no column matched the column, table and schema given
                    return null;

                return new ColumnMetadata(rs.getString("IS_NULLABLE"), rs.getInt("COLUMN_SIZE"), rs.getInt("DATA_TYPE"), rs.getString("COLUMN_NAME"));
            }
            finally
            {
                ResultSetUtil.close(rs);
                if (con != null) { con.close(); }
            }
        }

        class ColumnMetadata
        {
            final public boolean nullable;
            final public int size;
            final public int sqlTypeInt;
            final public String name;

            ColumnMetadata(String nullable, int size, int sqlTypeInt, String name)
            {
                this.nullable = !nullable.equals("NO"); // spec claims it can be empty string, which we'd count as nullable.
                this.size = size;
                this.sqlTypeInt = sqlTypeInt;
                this.name = name;
            }

        }
    }
}
