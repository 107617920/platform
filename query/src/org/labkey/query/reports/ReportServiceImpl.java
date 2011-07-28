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

package org.labkey.query.reports;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.AbstractReportIdentifier;
import org.labkey.api.reports.report.DbReportIdentifier;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportIdentifierConverter;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * User: Karl Lum
 * Date: Dec 21, 2007
 */
public class ReportServiceImpl implements ReportService.I, ContainerManager.ContainerListener, QueryService.QueryListener
{
    private static final String SCHEMA_NAME = "core";
    private static final String TABLE_NAME = "Report";
    private static final Logger _log = Logger.getLogger(ReportService.class);
    private static List<ReportService.ViewFactory> _viewFactories = new ArrayList<ReportService.ViewFactory>();
    private static List<ReportService.UIProvider> _uiProviders = new ArrayList<ReportService.UIProvider>();
    private static Map<String, String> _reportIcons = new HashMap<String, String>();

    /** maps descriptor types to providers */
    private final ConcurrentMap<String, Class> _descriptors = new ConcurrentHashMap<String, Class>();

    /** maps report types to implementations */
    private final ConcurrentMap<String, Class> _reports = new ConcurrentHashMap<String, Class>();

    public ReportServiceImpl()
    {
        ContainerManager.addContainerListener(this);
        ConvertUtils.register(new ReportIdentifierConverter(), ReportIdentifier.class);
        QueryService.get().addQueryListener(this);
        SystemMaintenance.addTask(new ReportServiceMaintenanceTask());
    }

    private DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public void registerDescriptor(ReportDescriptor descriptor)
    {
        if (descriptor == null)
            throw new IllegalArgumentException("Invalid descriptor instance");

        if (null != _descriptors.putIfAbsent(descriptor.getDescriptorType(), descriptor.getClass()))
            _log.warn("Descriptor type : " + descriptor.getDescriptorType() + " has previously been registered.");
           //throw new IllegalStateException("Descriptor type : " + descriptor.getDescriptorType() + " has previously been registered.");
    }

    public ReportDescriptor createDescriptorInstance(String typeName)
    {
        if (typeName == null)
        {
            _log.error("createDescriptorInstace : typeName cannot be null");
            return null;
        }
        Class clazz = _descriptors.get(typeName);

        if (null == clazz)
            return null;

        try
        {
            if (ReportDescriptor.class.isAssignableFrom(clazz))
            {
                return (ReportDescriptor)clazz.newInstance();
            }

            throw new IllegalArgumentException("The specified class: " + clazz.getName() + " is not an instance of ReportDescriptor");
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be created: " + clazz.getName());
        }
    }

    public void registerReport(Report report)
    {
        if (report == null)
            throw new IllegalArgumentException("Invalid report instance");

        if (null != _reports.putIfAbsent(report.getType(), report.getClass()))
            _log.warn("Report type : " + report.getType() + " has previously been registered.");
            //throw new IllegalStateException("Report type : " + report.getType() + " has previously been registered.");
    }

    public Report createReportInstance(String typeName)
    {
        Class clazz = _reports.get(typeName);

        if (null == clazz)
            return null;

        try
        {
            if (Report.class.isAssignableFrom(clazz))
            {
                Report report = (Report)clazz.newInstance();
                report.getDescriptor().setReportType(typeName);

                return report;
            }

            throw new IllegalArgumentException("The specified class: " + clazz.getName() + " is not an instance of Report");
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be created: " + clazz.getName());
        }
    }

    public Report createReportInstance(ReportDescriptor descriptor)
    {
        Report report = createReportInstance(descriptor.getReportType());
        report.setDescriptor(descriptor);
        return report;
    }

    public TableInfo getTable()
    {
        return getSchema().getTable(TABLE_NAME);
    }

    public void containerCreated(Container c, User user) {}
    public void propertyChange(PropertyChangeEvent evt) {}

    public void containerDeleted(Container c, User user)
    {
        try {
            ContainerUtil.purgeTable(getTable(), c, "ContainerId");
        }
        catch (SQLException x)
        {
            _log.error("Error occurred deleting reports for container", x);
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {        
    }

    public Report createFromQueryString(String queryString) throws Exception
    {
        for (Pair<String, String> param : PageFlowUtil.fromQueryString(queryString))
        {
            if (ReportDescriptor.Prop.reportType.toString().equals(param.getKey()))
            {
                if (param.getValue() != null)
                {
                    Report report = createReportInstance(param.getValue());
                    report.getDescriptor().initFromQueryString(queryString);
                    return report;
                }
            }
        }
        return null;
    }

    public void deleteReport(ContainerUser context, Report report) throws SQLException
    {
        //ensure that descriptor id is a DbReportIdentifier
        DbReportIdentifier reportId;

        if (report.getDescriptor().getReportId() instanceof DbReportIdentifier)
            reportId = (DbReportIdentifier)(report.getDescriptor().getReportId());
        else
            throw new RuntimeException("Can't delete a report that is not stored in the database!");

        DbScope scope = getTable().getSchema().getScope();

        try
        {
            scope.ensureTransaction();
            report.beforeDelete(context);

            final ReportDescriptor descriptor = report.getDescriptor();
            _deleteReport(context.getContainer(), reportId.getRowId());
            org.labkey.api.security.SecurityManager.deletePolicy(descriptor);
            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private void _deleteReport(Container c, int reportId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());
        filter.addCondition("RowId", reportId);
        Table.delete(getTable(), filter);
    }


    private Report _createReport(Class reportClass) throws Exception
    {
        if (Report.class.isAssignableFrom(reportClass))
        {
            return (Report)reportClass.newInstance();
        }
        throw new IllegalArgumentException("The specified class: " + reportClass.getName() + " does not implement the org.labkey.api.reports.Report interface");
    }

    public int saveReport(ContainerUser context, String key, Report report) throws SQLException
    {
        return _saveReport(context, key, report).getRowId();
    }

    private ReportDB _saveReport(ContainerUser context, String key, Report report) throws SQLException
    {
        DbScope scope = getTable().getSchema().getScope();
        try
        {
            scope.ensureTransaction();
            report.getDescriptor().setContainer(context.getContainer().getId());
            report.beforeSave(context);

            final ReportDescriptor descriptor = report.getDescriptor();
            final ReportDB r = _saveReport(context.getUser(), context.getContainer(), key, descriptor);
            scope.commitTransaction();
            return r;
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private ReportDB _saveReport(User user, Container c, String key, ReportDescriptor descriptor) throws SQLException
    {
        ReportDB reportDB = new ReportDB(c, user.getUserId(), key, descriptor);

        //ensure that descriptor id is a DbReportIdentifier
        DbReportIdentifier reportId;
        if (null == descriptor.getReportId() || descriptor.getReportId() instanceof DbReportIdentifier)
        {
            reportId = (DbReportIdentifier)(descriptor.getReportId());
            if (reportId != null)
                reportDB.setRowId(reportId.getRowId());
        }
        else
            throw new RuntimeException("Can't save a report that is not stored in the database!");


        if (null != reportId && reportExists(reportId.getRowId()))
            reportDB = Table.update(user, getTable(), reportDB, reportId.getRowId());
        else
            reportDB = Table.insert(user, getTable(), reportDB);

        return reportDB;
    }

    private Report _getInstance(ReportDB r)
    {
        if (r != null)
        {
            try
            {
                ReportDescriptor descriptor = ReportDescriptor.createFromXML(r.getDescriptorXML());

                if (descriptor != null)
                {
                    BeanUtils.copyProperties(descriptor, r);
                    descriptor.setReportId(new DbReportIdentifier(r.getRowId()));
                    descriptor.setOwner(r.getReportOwner());

                    String type = descriptor.getReportType();
                    Report report = createReportInstance(type);
                    if (report != null)
                        report.setDescriptor(descriptor);
                    return report;
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    public Report getReportByEntityId(Container c, String entityId) throws Exception
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());
        filter.addCondition("EntityId", entityId);

        ReportDB report = Table.selectObject(getTable(), filter, null, ReportDB.class);
        return _getInstance(report);
    }

    public Report getReport(int reportId) throws SQLException
    {
        ReportDB report = Table.selectObject(getTable(), new SimpleFilter("RowId", reportId), null, ReportDB.class);
        return _getInstance(report);
    }

    public ReportIdentifier getReportIdentifier(String reportId)
    {
        return AbstractReportIdentifier.fromString(reportId);
    }

    private Report[] _getReports(User user, SimpleFilter filter) throws SQLException
    {
        if (filter != null && user != null)
            filter.addWhereClause("ReportOwner IS NULL OR ReportOwner = ?", new Object[]{user.getUserId()});

        return getReports(filter);
    }

    private Report[] _createReports(ReportDB[] rawReports)
    {
        if (rawReports.length > 0)
        {
            List<Report> descriptors = new ArrayList<Report>();
            for (ReportDB r : rawReports)
            {
                Report report = _getInstance(r);
                if (report != null)
                    descriptors.add(report);
            }
            if (descriptors.size() > 0)
                Collections.sort(descriptors, ReportComparator.getInstance());
            return descriptors.toArray(new Report[descriptors.size()]);
        }
        return EMPTY_REPORT;
    }

    public Report[] getReports(User user) throws SQLException
    {
        return _getReports(user, new SimpleFilter());
    }

    public Report[] getReports(User user, Container c) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());
        return _getReports(user, filter);
    }

    public Report[] getReports(User user, Container c, String key) throws SQLException
    {
        List<ReportDescriptor> moduleReportDescriptors = new ArrayList<ReportDescriptor>();

        for (Module module : c.getActiveModules())
        {
            List<ReportDescriptor> descriptors = module.getReportDescriptors(key);
            if (null != descriptors)
                moduleReportDescriptors.addAll(descriptors);
        }

        List<Report> reports = new ArrayList<Report>();

        for (ReportDescriptor descriptor : moduleReportDescriptors)
        {
            String type = descriptor.getReportType();
            Report report = createReportInstance(type);

            if (report != null)
            {
                // the descriptor is a securable resource, so it must have a non-null container id, since file-based
                // module reports don't have a container associated, default to the current container security
                if (descriptor.getContainerId() == null)
                    descriptor.setContainerId(c.getId());

                report.setDescriptor(descriptor);
                reports.add(report);
            }
        }

        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());

        if (key != null)
            filter.addCondition("ReportKey", key);

        reports.addAll(Arrays.asList(_getReports(user, filter)));
        return reports.toArray(new Report[reports.size()]);
    }

    public Report[] getReports(User user, Container c, String key, int flagMask, int flagValue) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());

        if (key != null)
            filter.addCondition("ReportKey", key);

        SQLFragment ret = new SQLFragment("(((Flags");
        ret.append(") &");
        ret.append(flagMask);
        ret.append(") = ");
        ret.append(flagValue);
        ret.append(")");
        filter.addWhereClause(ret.getSQL(), ret.getParams().toArray(), "Flag");

        return _getReports(user, filter);
    }

    public Report[] getReports(Filter filter) throws SQLException
    {
        ReportDB[] reports = Table.select(getTable(), Table.ALL_COLUMNS, filter, null, ReportDB.class);
        return _createReports(reports);
    }

    /**
     * Provides a module specific way to add ui to the report designers.
     */
    public void addViewFactory(ReportService.ViewFactory vf)
    {
        _viewFactories.add(vf);
    }

    public List<ReportService.ViewFactory> getViewFactories()
    {
        return _viewFactories;
    }

    public void addUIProvider(ReportService.UIProvider provider)
    {
        _uiProviders.add(provider);
    }

    public List<ReportService.UIProvider> getUIProviders()
    {
        return Collections.unmodifiableList(_uiProviders);
    }

    public String getReportIcon(ViewContext context, String reportType)
    {
        if (_reportIcons.containsKey(reportType))
            return _reportIcons.get(reportType);

        for (ReportService.UIProvider provider : _uiProviders)
        {
            String iconPath = provider.getReportIcon(context, reportType);

            if (iconPath != null)
            {
                _reportIcons.put(reportType, iconPath);
                return iconPath;
            }
        }

        return null;
    }

    private static final Report[] EMPTY_REPORT = new Report[0];

    private boolean reportExists(int reportId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("RowId", reportId);
        ReportDB report = Table.selectObject(getTable(), filter, null, ReportDB.class);

        return (report != null);
    }

    public void viewChanged(CustomView view)
    {
        _uncacheDependent(view);
    }

    public void viewDeleted(CustomView view)
    {
        _uncacheDependent(view);
    }

    public Report _deserialize(File file) throws IOException, XmlValidationException
    {
        ReportDescriptor descriptor = ReportDescriptor.createFromXML(file);

        if (descriptor != null)
        {
            //descriptor.setReportId(new DbReportIdentifier(r.getRowId()));
            //descriptor.setOwner(r.getReportOwner());

            String type = descriptor.getReportType();
            Report report = createReportInstance(type);

            if (report != null)
                report.setDescriptor(descriptor);

            return report;
        }

        return null;
    }

    public Report deserialize(File reportFile) throws IOException, XmlValidationException
    {
        if (reportFile.exists())
        {
            Report report = _deserialize(reportFile);

            // reset any report identifier, we want to treat an imported report as a new
            // report instance
            report.getDescriptor().setReportId(new DbReportIdentifier(-1));

            return report;
        }

        throw new IllegalArgumentException("Specified file does not exist: " + reportFile.getAbsolutePath());
    }

    public Report importReport(final User user, final Container container, File reportFile) throws IOException, SQLException, XmlValidationException
    {
        Report report = deserialize(reportFile);
        ReportDescriptor descriptor = report.getDescriptor();
        String key = descriptor.getReportKey();

        if (StringUtils.isBlank(key))
        {
            // use the default key used by query views
            key = ReportUtil.getReportKey(descriptor.getProperty(ReportDescriptor.Prop.schemaName), descriptor.getProperty(ReportDescriptor.Prop.queryName));
        }

        Report[] existingReports = getReports(user, container, key);

        for (Report existingReport : existingReports)
        {
            if (StringUtils.equalsIgnoreCase(existingReport.getDescriptor().getReportName(), descriptor.getReportName()))
            {
                deleteReport(new ContainerUser(){
                    public User getUser() {return user;}
                    public Container getContainer() {return container;}
                }, existingReport);
            }
        }

        int rowId = _saveReport(user, container, key, descriptor).getRowId();
        descriptor.setReportId(new DbReportIdentifier(rowId));

        return report;
    }

    private void _uncacheDependent(CustomView view)
    {
        try
        {
            QueryDefinition def = view.getQueryDefinition();
            String key = ReportUtil.getReportKey(def.getSchemaName(), def.getName());

            for (Report report : getReports(null, view.getContainer(), key))
            {
                if (StringUtils.equals(view.getName(), report.getDescriptor().getProperty(ReportDescriptor.Prop.viewName)))
                    report.clearCache();
            }
        }
        catch (Exception e)
        {
            _log.error("An error occurred uncaching dependent reports", e);
        }
    }

    @Override
    public void maintenance()
    {
        ScriptEngineReport.scheduledFileCleanup();
    }

    private static class ReportComparator implements Comparator<Report>
    {
        private static final ReportComparator _instance = new ReportComparator();

        private ReportComparator(){}

        public static ReportComparator getInstance()
        {
            return _instance;
        }

        public int compare(Report o1, Report o2)
        {

            return o1.getDescriptor().getReportId().toString().compareToIgnoreCase(o2.getDescriptor().getReportId().toString());
        }
    }

    private static class ReportServiceMaintenanceTask implements SystemMaintenance.MaintenanceTask
    {
        public String getMaintenanceTaskName()
        {
            return "Report Service Maintenance";
        }

        public void run()
        {
            ReportService.get().maintenance();
        }
    }
}
