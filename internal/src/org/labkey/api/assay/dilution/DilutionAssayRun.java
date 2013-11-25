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
package org.labkey.api.assay.dilution;

import org.labkey.api.assay.dilution.query.DilutionProviderSchema;
import org.labkey.api.assay.nab.Luc5Assay;
import org.labkey.api.assay.nab.NabSpecimen;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * User: klum
 * Date: 5/8/13
 */
public abstract class DilutionAssayRun extends Luc5Assay
{
    protected ExpProtocol _protocol;
    protected DilutionAssayProvider _provider;
    protected Map<PropertyDescriptor, Object> _runProperties;
    protected Map<PropertyDescriptor, Object> _runDisplayProperties;
    protected List<SampleResult> _sampleResults;
    protected ExpRun _run;
    // Be extremely careful to not leak this user out in any objects (e.g, via schemas or tables) as it may have elevated permissions.
    protected User _user;
    protected DilutionCurve.FitType _savedCurveFitType = null;
    protected Map<ExpMaterial, List<WellGroup>> _materialWellGroupMapping;
    protected Map<WellGroup, ExpMaterial> _wellGroupMaterialMapping;

    public DilutionAssayRun(DilutionAssayProvider provider, ExpRun run,
                       User user, List<Integer> cutoffs, DilutionCurve.FitType renderCurveFitType)
    {
        super(run.getRowId(), cutoffs, renderCurveFitType);
        _run = run;
        _user = user;
        _protocol = run.getProtocol();
        _provider = provider;

        for (Map.Entry<PropertyDescriptor, Object> property : getRunProperties().entrySet())
        {
            if (DilutionAssayProvider.CURVE_FIT_METHOD_PROPERTY_NAME.equals(property.getKey().getName()))
            {
                String fitTypeLabel = (String) property.getValue();
                _savedCurveFitType = DilutionCurve.FitType.fromLabel(fitTypeLabel);
            }
        }
    }

    public DilutionAssayProvider getProvider()
    {
        return _provider;
    }

    public DilutionDataHandler getDataHandler()
    {
        return _provider.getDataHandler();
    }

    @Override
    public String getRunName()
    {
        return _run.getName();
    }

    private Map<FieldKey, PropertyDescriptor> getFieldKeys()
    {
        Map<FieldKey, PropertyDescriptor> fieldKeys = new HashMap<>();
        for (DomainProperty property : _provider.getBatchDomain(_protocol).getProperties())
            fieldKeys.put(FieldKey.fromParts(AssayService.BATCH_COLUMN_NAME, property.getName()), property.getPropertyDescriptor());
        for (DomainProperty property : _provider.getRunDomain(_protocol).getProperties())
            fieldKeys.put(FieldKey.fromParts(property.getName()), property.getPropertyDescriptor());

        // Add all of the hard columns to the set of properties we can show
        TableInfo runTableInfo = AssayService.get().createRunTable(_protocol, _provider, _user, _run.getContainer());
        for (ColumnInfo runColumn : runTableInfo.getColumns())
        {
            // These columns cause an UnauthorizedException if the user has permission to see the dataset
            // this run has been copied to, but not the run folder, because the column joins to the exp.Data query
            // which doesn't know anything about the extra permission the user has been granted by the copy to study linkage.
            // We don't need to show it in the details view, so just skip it.
            if (!ExpRunTable.Column.DataOutputs.name().equalsIgnoreCase(runColumn.getName()) &&
                !ExpRunTable.Column.JobId.name().equalsIgnoreCase(runColumn.getName()) &&
                !ExpRunTable.Column.RunGroups.name().equalsIgnoreCase(runColumn.getName()))
            {
                // Fake up a property descriptor. Currently only name and label are actually used for rendering the page,
                // but set a few more so that toString() works for debugging purposes
                PropertyDescriptor pd = new PropertyDescriptor();
                pd.setName(runColumn.getName());
                pd.setLabel(runColumn.getLabel());
                pd.setPropertyURI(runColumn.getPropertyURI());
                pd.setContainer(_protocol.getContainer());
                pd.setProject(_protocol.getContainer().getProject());
                fieldKeys.put(FieldKey.fromParts(runColumn.getName()), pd);
            }
        }

        return fieldKeys;
    }

    public DilutionCurve.FitType getSavedCurveFitType()
    {
        return _savedCurveFitType;
    }

    private Map<PropertyDescriptor, Object> getRunProperties(TableInfo runTable, Map<FieldKey, PropertyDescriptor> fieldKeys, Map<FieldKey, ColumnInfo> selectCols)
    {
        SimpleFilter filter = new SimpleFilter("RowId", _run.getRowId());
        Map<PropertyDescriptor, Object> properties = new LinkedHashMap<>();

        try (ResultSet rs = new TableSelector(runTable, selectCols.values(), filter, null).setForDisplay(true).setMaxRows(1).getResultSet())
        {
            if (!rs.next())
            {
                throw new NotFoundException("Run " + _run.getRowId() + " was not found.");
            }

            for (Map.Entry<FieldKey, ColumnInfo> entry : selectCols.entrySet())
            {
                ColumnInfo column = entry.getValue();
                ColumnInfo displayField = column.getDisplayField();
                if (column.getDisplayField() != null)
                    column = displayField;
                if (fieldKeys.containsKey(entry.getKey()))
                    properties.put(fieldKeys.get(entry.getKey()), column.getValue(rs));
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return properties;
    }

    public Map<PropertyDescriptor, Object> getRunDisplayProperties(ViewContext context)
    {
        if (_runDisplayProperties == null)
        {
            Map<FieldKey, PropertyDescriptor> fieldKeys = getFieldKeys();
            TableInfo runTable = AssayService.get().createRunTable(_protocol, _provider, _user, _run.getContainer());

            CustomView runView = getRunsCustomView(context);
            Collection<FieldKey> fieldKeysToShow;
            if (runView != null)
            {
                // If we have a saved view to use for the column list, use it
                fieldKeysToShow = new ArrayList<>(runView.getColumns());
            }
            else
            {
                // Otherwise, use the default list of columns
                fieldKeysToShow = new ArrayList<>(runTable.getDefaultVisibleColumns());
            }
            // The list of available columns is reduced from the default set because the user may not have
            // permission to join to all of the lookups. Remove any columns that aren't part of the acceptable set,
            // which is built up by getFieldKeys()
            List<FieldKey> newFieldKeysToShow = new ArrayList<>();
            for (FieldKey fieldKey : fieldKeysToShow)
            {
                FieldKey parent = fieldKey.getParent();
                if (null != parent && null == parent.getParent() && parent.getLabel().equalsIgnoreCase("runproperties") && fieldKeys.containsKey(parent))
                {
                    fieldKey = FieldKey.fromString(fieldKey.getLabel());        // special case RunProperties for backwards compatibility; use fieldKey without "RunProperties" if it's there
                }

                if (fieldKeys.containsKey(fieldKey))
                {
                    newFieldKeysToShow.add(fieldKey);
                }
            }

            Map<FieldKey, ColumnInfo> selectCols = QueryService.get().getColumns(runTable, newFieldKeysToShow);
            _runDisplayProperties = getRunProperties(runTable, fieldKeys, selectCols);
        }
        return Collections.unmodifiableMap(_runDisplayProperties);
    }

    protected Map<String, DilutionResultProperties> getSampleProperties(ExpData outputData)
    {
        Map<String, DilutionResultProperties> samplePropertyMap = new HashMap<>();

        Collection<ExpMaterial> inputs = _run.getMaterialInputs().keySet();
        Domain sampleDomain = _provider.getSampleWellGroupDomain(_protocol);
        DomainProperty[] sampleDomainProperties = sampleDomain.getProperties();

        AssayProtocolSchema schema = _provider.createProtocolSchema(_user, _run.getContainer(), _protocol, null);
        // Do a query to get all the info we need to do the copy
        TableInfo resultTable = schema.createDataTable(false);
        DilutionManager mgr = new DilutionManager();

        for (ExpMaterial material : inputs)
        {
            Map<PropertyDescriptor, Object> sampleProperties = new TreeMap<>(new PropertyDescriptorComparator());
            for (DomainProperty dp : sampleDomainProperties)
            {
                PropertyDescriptor property = dp.getPropertyDescriptor();
                sampleProperties.put(property, material.getProperty(property));
            }

            // in addition to the properties saved on the sample object, we'll add the properties associated with each sample's
            // "output" data object.
            Map<PropertyDescriptor, Object> dataProperties = new TreeMap<>(new PropertyDescriptorComparator());
            String wellGroupName = getWellGroupName(material);
            String dataRowLsid = getDataHandler().getDataRowLSID(outputData, wellGroupName, sampleProperties).toString();
            Set<Double> cutoffValues = new HashSet<>();
            for (Integer value : DilutionDataHandler.getCutoffFormats(_protocol, _run).keySet())
                cutoffValues.add(value.doubleValue());
            List<PropertyDescriptor> propertyDescriptors = DilutionProviderSchema.getExistingDataProperties(_protocol, cutoffValues);
            mgr.getDataPropertiesFromRunData(resultTable, dataRowLsid, _run.getContainer(), propertyDescriptors, dataProperties);
            samplePropertyMap.put(getSampleKey(material), new DilutionResultProperties(sampleProperties,  dataProperties));
        }
        return samplePropertyMap;
    }

    protected CustomView getRunsCustomView(ViewContext context)
    {
        return null;
    }

    public Map<PropertyDescriptor, Object> getRunProperties()
    {
        if (_runProperties == null)
        {
            Map<FieldKey, PropertyDescriptor> fieldKeys = getFieldKeys();
            TableInfo runTable = AssayService.get().createRunTable(_protocol, _provider, _user, _run.getContainer());
            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(runTable, fieldKeys.keySet());
            _runProperties = new TreeMap<>(new PropertyDescriptorComparator());
            _runProperties.putAll(getRunProperties(runTable, fieldKeys, cols));
        }
        return Collections.unmodifiableMap(_runProperties);
    }

    public abstract List<SampleResult> getSampleResults();

    public static class DilutionResultProperties
    {
        private Map<PropertyDescriptor, Object> _sampleProperties;
        private Map<PropertyDescriptor, Object> _dataProperties;

        public DilutionResultProperties(Map<PropertyDescriptor, Object> sampleProperties, Map<PropertyDescriptor, Object> dataProperties)
        {
            _sampleProperties = sampleProperties;
            _dataProperties = dataProperties;
        }

        public Map<PropertyDescriptor, Object> getSampleProperties()
        {
            return _sampleProperties;
        }

        public Map<PropertyDescriptor, Object> getDataProperties()
        {
            return _dataProperties;
        }
    }

    public static class SampleResult
    {
        private String _dataRowLsid;
        private Container _dataContainer;
        private Integer _objectId;
        private DilutionSummary _dilutionSummary;
        private DilutionMaterialKey _materialKey;
        private Map<PropertyDescriptor, Object> _sampleProperties;
        private Map<PropertyDescriptor, Object> _dataProperties;
        private boolean _longCaptions = false;
        private DilutionManager _mgr = new DilutionManager();

        public SampleResult(DilutionAssayProvider provider, ExpData data, DilutionSummary dilutionSummary, DilutionMaterialKey materialKey,
                            Map<PropertyDescriptor, Object> sampleProperties, Map<PropertyDescriptor, Object> dataProperties)
        {
            _dilutionSummary = dilutionSummary;
            _materialKey = materialKey;
            _sampleProperties = sortProperties(sampleProperties);
            _dataProperties = sortProperties(dataProperties);
            _dataRowLsid = provider.getDataHandler().getDataRowLSID(data, dilutionSummary.getFirstWellGroup().getName(), sampleProperties).toString();
            _dataContainer = data.getContainer();
        }

        public Integer getObjectId()
        {
            if (null == _objectId)
            {
                NabSpecimen nabSpecimen = _mgr.getNabSpecimen(_dataRowLsid, _dataContainer);
                if (null != nabSpecimen)
                    _objectId = nabSpecimen.getRowId();
            }
            return _objectId;
        }

        public DilutionSummary getDilutionSummary()
        {
            return _dilutionSummary;
        }

        public String getCaption()
        {
            return _materialKey.getDisplayString(_longCaptions);
        }

        public Map<PropertyDescriptor, Object> getSampleProperties()
        {
            return _sampleProperties;
        }

        public Map<PropertyDescriptor, Object> getDataProperties()
        {
            return _dataProperties;
        }

        public String getDataRowLsid()
        {
            return _dataRowLsid;
        }

        public void setLongCaptions(boolean longCaptions)
        {
            _longCaptions = longCaptions;
        }

        private Map<PropertyDescriptor, Object> sortProperties(Map<PropertyDescriptor, Object> properties)
        {
            Map<PropertyDescriptor, Object> sortedProperties = new LinkedHashMap<>();
            Map.Entry<PropertyDescriptor, Object> sampleIdEntry =
                    findPropertyDescriptor(properties, AbstractAssayProvider.SPECIMENID_PROPERTY_NAME);
            Map.Entry<PropertyDescriptor, Object> ptidEntry =
                    findPropertyDescriptor(properties, AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME);
            Map.Entry<PropertyDescriptor, Object> visitEntry =
                    findPropertyDescriptor(properties, AbstractAssayProvider.VISITID_PROPERTY_NAME);
            if (sampleIdEntry != null)
                sortedProperties.put(sampleIdEntry.getKey(), sampleIdEntry.getValue());
            if (ptidEntry != null)
                sortedProperties.put(ptidEntry.getKey(), ptidEntry.getValue());
            if (visitEntry != null)
                sortedProperties.put(visitEntry.getKey(), visitEntry.getValue());
            for (Map.Entry<PropertyDescriptor, Object> entry : properties.entrySet())
            {
                if (sampleIdEntry != null && entry.getKey() == sampleIdEntry.getKey() ||
                    ptidEntry != null && entry.getKey() == ptidEntry.getKey() ||
                    visitEntry != null && entry.getKey() == visitEntry.getKey())
                {
                    continue;
                }
                sortedProperties.put(entry.getKey(), entry.getValue());
            }
            return sortedProperties;
        }

        private Map.Entry<PropertyDescriptor, Object> findPropertyDescriptor(Map<PropertyDescriptor, Object> properties, String propertyName)
        {
            for (Map.Entry<PropertyDescriptor, Object> entry : properties.entrySet())
            {
                if (entry.getKey().getName().equals(propertyName))
                    return entry;
            }
            return null;
        }

        public Object getDataProperty(String name)
        {
            // TODO: this is a shim because _dataProperties maps PropertyDescriptor; when old Nab is ripped, we'll map the name instead
            Map.Entry<PropertyDescriptor, Object> entry = findPropertyDescriptor(_dataProperties, name);
            if (null != entry)
                return entry.getValue();
            return null;
        }
    }

    public static class PropertyDescriptorComparator implements Comparator<PropertyDescriptor>
    {
        public int compare(PropertyDescriptor o1, PropertyDescriptor o2)
        {
            String o1Str = o1.getLabel();
            if (o1Str == null)
                o1Str = o1.getName();
            String o2Str = o2.getLabel();
            if (o2Str == null)
                o2Str = o2.getName();
            return o1Str.compareToIgnoreCase(o2Str);
        }
    }

    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    public ExpRun getRun()
    {
        return _run;
    }

    public void setMaterialWellGroupMapping(Map<ExpMaterial, List<WellGroup>> materialWellGroupMapping)
    {
        _materialWellGroupMapping = materialWellGroupMapping;
        _wellGroupMaterialMapping = new HashMap<>();
        for (Map.Entry<ExpMaterial, List<WellGroup>> entry : materialWellGroupMapping.entrySet())
        {
            for (WellGroup wellGroup : entry.getValue())
            {
                _wellGroupMaterialMapping.put(wellGroup, entry.getKey());
            }
        }
    }

    public ExpMaterial getMaterial(WellGroup wellgroup)
    {
        return _wellGroupMaterialMapping.get(wellgroup);
    }

    public List<WellGroup> getWellGroups(ExpMaterial material)
    {
        return _materialWellGroupMapping.get(material);
    }

    protected String getWellGroupName(ExpMaterial material)
    {
        List<WellGroup> groups = getWellGroups(material);
        // All current NAb assay types don't mix well groups for a single sample- there may be muliple
        // instances of the same well group on different plates, but they'll all have the same name.
        return groups != null ? groups.get(0).getName() : null;
    }

    /**
     * Generate a key for the sample level property map
     * @param material
     * @return
     */
    protected String getSampleKey(ExpMaterial material)
    {
        return getWellGroupName(material);
    }

    /**
     * Generate a key for the sample level property map
     * @param summary
     * @return
     */
    protected String getSampleKey(DilutionSummary summary)
    {
        return summary.getFirstWellGroup().getName();
    }

}
