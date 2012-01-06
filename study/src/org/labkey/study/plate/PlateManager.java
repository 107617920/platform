/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.study.plate;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.DownloadURL;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.Plate;
import org.labkey.api.study.PlateQueryView;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.PlateTypeHandler;
import org.labkey.api.study.Position;
import org.labkey.api.study.PositionImpl;
import org.labkey.api.study.Well;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.plate.query.PlateSchema;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:13:08 AM
 */
public class PlateManager implements PlateService.Service
{
    private List<PlateService.PlateDetailsResolver> _detailsLinkResolvers = new ArrayList<PlateService.PlateDetailsResolver>();
    private final Object TEMPLATE_NAME_SYNC_OBJ = new Object();
    private Set<String> _distinctTemplateNames;
    private boolean _lsidHandlersRegistered = false;


    private Map<String, PlateTypeHandler> _plateTypeHandlers = new HashMap<String, PlateTypeHandler>();

    public PlateManager()
    {
        registerPlateTypeHandler(new PlateTypeHandler()
        {
            public PlateTemplate createPlate(String templateTypeName, Container container, int rowCount, int colCount) throws SQLException
            {
                return PlateService.get().createPlateTemplate(container, getAssayType(), rowCount, colCount);
            }

            public String getAssayType()
            {
                return "blank";
            }

            public List<String> getTemplateTypes()
            {
                return new ArrayList<String>();
            }

            @Override
            public List<Pair<Integer, Integer>> getSupportedPlateSizes()
            {
                return Collections.singletonList(new Pair<Integer, Integer>(8, 12));
            }

            public WellGroup.Type[] getWellGroupTypes()
            {
                return new WellGroup.Type[]{
                        WellGroup.Type.CONTROL, WellGroup.Type.SPECIMEN,
                        WellGroup.Type.REPLICATE, WellGroup.Type.OTHER};
            }
        });
    }

    public Plate createPlate(PlateTemplate template, double[][] wellValues)
    {
        if (template == null)
            return null;
        if (!(template instanceof PlateTemplateImpl))
            throw new IllegalArgumentException("Only plate templates retrieved from the plate service can be used to create plate instances.");
        return new PlateImpl((PlateTemplateImpl) template, wellValues);
    }
    
    public Position createPosition(Container container, int row, int column)
    {
        return new PositionImpl(container, row, column);
    }

    public PlateTemplateImpl getPlateTemplate(Container container, String name) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Template", Boolean.TRUE);
        filter.addCondition("Name", name);
        filter.addCondition("Container", container);
        PlateTemplateImpl template = Table.selectObject(StudySchema.getInstance().getTableInfoPlate(), filter, null, PlateTemplateImpl.class);
        if (template != null)
        {
            populatePlate(template);
            cache(template);
        }
        return template;
    }

    public PlateTemplate[] getPlateTemplates(Container container) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Template", Boolean.TRUE);
        filter.addCondition("Container", container);
        PlateTemplateImpl[] templates = Table.select(StudySchema.getInstance().getTableInfoPlate(),
                Table.ALL_COLUMNS, filter, new Sort("Name"), PlateTemplateImpl.class);
        for (int i = 0; i < templates.length; i++)
        {
            PlateTemplateImpl template = templates[i];
            PlateTemplateImpl cached = getCachedPlateTemplate(container, template.getRowId().intValue());
            if (cached != null)
                templates[i] = cached;
            else
                populatePlate(template);
        }
        return templates;
    }

    public PlateTemplate createPlateTemplate(Container container, String templateType, int rowCount, int colCount) throws SQLException
    {
        synchronized (TEMPLATE_NAME_SYNC_OBJ)
        {
            _distinctTemplateNames = null;
        }
        return new PlateTemplateImpl(container, null, templateType, rowCount, colCount);
    }

    public PlateImpl getPlate(Container container, int rowid) throws SQLException
    {
        PlateImpl plate = (PlateImpl) getCachedPlateTemplate(container, rowid);
        if (plate != null)
            return plate;
        plate = Table.selectObject(StudySchema.getInstance().getTableInfoPlate(), rowid, PlateImpl.class);
        if (plate == null)
            return null;
        populatePlate(plate);
        cache(plate);
        return plate;
    }

    public PlateImpl getPlate(Container container, String entityId) throws SQLException
    {
        PlateImpl plate = (PlateImpl) getCachedPlateTemplate(container, entityId);
        if (plate != null)
            return plate;
        SimpleFilter filter = new SimpleFilter("Container", container).addCondition("DataFileId", entityId);
        plate = Table.selectObject(StudySchema.getInstance().getTableInfoPlate(), filter, null, PlateImpl.class);
        if (plate == null)
            return null;
        populatePlate(plate);
        cache(plate);
        return plate;
    }

    public PlateImpl getPlate(String lsid) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("lsid", lsid);
        PlateImpl plate = Table.selectObject(StudySchema.getInstance().getTableInfoPlate(), filter, PlateImpl.class);
        if (plate == null)
            return null;
        populatePlate(plate);
        return plate;
    }

    public WellGroup getWellGroup(Container container, int rowid) throws SQLException
    {
        WellGroupImpl unboundWellgroup = Table.selectObject(StudySchema.getInstance().getTableInfoWellGroup(), rowid, WellGroupImpl.class);
        if (unboundWellgroup == null || !unboundWellgroup.getContainer().equals(container))
            return null;
        Plate plate = getPlate(container, unboundWellgroup.getPlateId());
        for (WellGroup wellgroup : plate.getWellGroups())
        {
            if (wellgroup.getRowId().intValue() == rowid)
                return wellgroup;
        }
        assert false : "Unbound well group was found: bound group should always be present.";
        return null;
    }

    public WellGroup getWellGroup(String lsid) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("lsid", lsid);
        WellGroupImpl unboundWellgroup = Table.selectObject(StudySchema.getInstance().getTableInfoWellGroup(), filter, null, WellGroupImpl.class);
        if (unboundWellgroup == null)
            return null;
        Plate plate = getPlate(unboundWellgroup.getContainer(), unboundWellgroup.getPlateId());
        for (WellGroup wellgroup : plate.getWellGroups())
        {
            if (wellgroup.getRowId().intValue() == unboundWellgroup.getRowId().intValue())
                return wellgroup;
        }
        assert false : "Unbound well group was not found: bound group should always be present.";
        return null;
    }


    private void setProperties(Container container, PropertySetImpl propertySet) throws SQLException
    {
        Map<String, ObjectProperty> props = OntologyManager.getPropertyObjects(container, propertySet.getLSID());
        for (ObjectProperty prop : props.values())
            propertySet.setProperty(prop.getName(), prop.value());
    }

    public int save(Container container, User user, PlateTemplate plateObj) throws SQLException
    {
        if (!(plateObj instanceof PlateTemplateImpl))
            throw new IllegalArgumentException("Only plate instances created by the plate service can be saved.");
        PlateTemplateImpl plate = (PlateTemplateImpl) plateObj;
        return savePlateImpl(container, user, plate);
    }

    public static class WellGroupTemplateComparator implements Comparator<WellGroupTemplateImpl>
    {
        public int compare(WellGroupTemplateImpl first, WellGroupTemplateImpl second)
        {
            Position firstPos = first.getTopLeft();
            Position secondPos = second.getTopLeft();
            if (firstPos == null && secondPos == null)
                return 0;
            if (firstPos == null)
                return -1;
            if (secondPos == null)
                return 1;
            int comp = firstPos.getColumn() - secondPos.getColumn();
            if (comp == 0)
                comp = firstPos.getRow() - secondPos.getRow();
            return comp;
        }
    }

    private void populatePlate(PlateTemplateImpl plate) throws SQLException
    {
        // set plate properties:
        setProperties(plate.getContainer(), plate);

        // populate wells:
        Map<String, List<PositionImpl>> groupLsidToPositions = new HashMap<String, List<PositionImpl>>();
        Position[][] positionArray;
        if (plate.isTemplate())
            positionArray = new Position[plate.getRows()][plate.getColumns()];
        else
            positionArray = new WellImpl[plate.getRows()][plate.getColumns()];
        PositionImpl[] positions = getPositions(plate);
        for (PositionImpl position : positions)
        {
            positionArray[position.getRow()][position.getColumn()] = position;
            Map<String, Object> props = OntologyManager.getProperties(plate.getContainer(), position.getLsid());
            // this is a bit counter-intuitive: the groups to which a position belongs are indicated by the values of the properties
            // associated with the position:
            for (Map.Entry<String, Object> entry : props.entrySet())
            {
                String wellgroupLsid = (String) entry.getValue();
                List<PositionImpl> groupPositions = groupLsidToPositions.get(wellgroupLsid);
                if (groupPositions == null)
                {
                    groupPositions = new ArrayList<PositionImpl>();
                    groupLsidToPositions.put(wellgroupLsid, groupPositions);
                }
                groupPositions.add(position);
            }
        }

        if (plate instanceof PlateImpl)
            ((PlateImpl) plate).setWells((WellImpl[][]) positionArray);

        // populate well groups:
        WellGroupTemplateImpl[] wellgroups = getWellGroups(plate);
        List<WellGroupTemplateImpl> sortedGroups = new ArrayList<WellGroupTemplateImpl>();
        for (WellGroupTemplateImpl wellgroup : wellgroups)
        {
            setProperties(plate.getContainer(), wellgroup);
            List<PositionImpl> groupPositions = groupLsidToPositions.get(wellgroup.getLSID());
            wellgroup.setPositions(groupPositions != null ? groupPositions : Collections.<Position>emptyList());
            sortedGroups.add(wellgroup);
        }

        Collections.sort(sortedGroups, new WellGroupTemplateComparator());

        for (WellGroupTemplateImpl group : sortedGroups)
            plate.addWellGroup(group);

    }

    private PositionImpl[] getPositions(PlateTemplateImpl plate) throws SQLException
    {
        SimpleFilter plateFilter = new SimpleFilter("PlateId", plate.getRowId());
        Sort sort = new Sort("Col,Row");
        return Table.select(StudySchema.getInstance().getTableInfoWell(), Table.ALL_COLUMNS,
                plateFilter, sort, plate.isTemplate() ? PositionImpl.class : WellImpl.class);

    }

    private WellGroupTemplateImpl[] getWellGroups(PlateTemplateImpl plate) throws SQLException
    {
        SimpleFilter plateFilter = new SimpleFilter("PlateId", plate.getRowId());
        return Table.select(StudySchema.getInstance().getTableInfoWellGroup(), Table.ALL_COLUMNS,
                plateFilter, null, plate.isTemplate() ? WellGroupTemplateImpl.class : WellGroupImpl.class);
    }

    private String getLsid(PlateTemplateImpl plate, Class type, boolean instance)
    {
        String nameSpace;
        if (type == Plate.class)
            nameSpace = plate.isTemplate() ? "PlateTemplate" : "PlateInstance";
        else if (type == WellGroup.class)
            nameSpace = plate.isTemplate() ? "WellGroupTemplate" : "WellGroupInstance";
        else if (type == Well.class)
            nameSpace = plate.isTemplate() ? "WellTemplate" : "WellInstance";
        else
            throw new IllegalArgumentException("Unknown type " + type);

        String id;
        if (instance)
            id = GUID.makeGUID();
        else
            id = "objectType";
        return new Lsid(nameSpace, "Folder-" + plate.getContainer().getRowId(), id).toString();
    }

    private int savePlateImpl(Container container, User user, PlateTemplateImpl plate) throws SQLException
    {
        if (plate.getRowId() != null)
            throw new UnsupportedOperationException("Resaving of plate templates is not supported.");

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        scope.ensureTransaction();
        try
        {
            String plateInstanceLsid = getLsid(plate, Plate.class, true);
            String plateObjectLsid = getLsid(plate, Plate.class, false);
            plate.setLsid(plateInstanceLsid);
            plate.setContainer(container);
            plate.setCreatedBy(user.getUserId());
            plate.setCreated(new Date());
            PlateTemplateImpl newPlate = Table.insert(user, StudySchema.getInstance().getTableInfoPlate(), plate);
            savePropertyBag(container, plateInstanceLsid, plateObjectLsid, newPlate.getProperties());

            for (WellGroupTemplateImpl wellgroup : plate.getWellGroupTemplates())
            {
                String wellGroupInstanceLsid = getLsid(plate, WellGroup.class, true);
                String wellGroupObjectLsid = getLsid(plate, WellGroup.class, false);
                wellgroup.setLsid(wellGroupInstanceLsid);
                wellgroup.setPlateId(newPlate.getRowId());
                Table.insert(user, StudySchema.getInstance().getTableInfoWellGroup(), wellgroup);
                savePropertyBag(container, wellGroupInstanceLsid, wellGroupObjectLsid, wellgroup.getProperties());
            }

            String wellInstanceLsidPrefix = getLsid(plate, Well.class, true);
            String wellObjectLsid = getLsid(plate, Well.class, false);
            for (int row = 0; row < plate.getRows(); row++)
            {
                for (int col = 0; col < plate.getColumns(); col++)
                {
                    PositionImpl position = plate.getPosition(row, col);
                    String wellLsid = wellInstanceLsidPrefix + "-well-" + position.getRow() + "-" + position.getCol();
                    position.setLsid(wellLsid);
                    position.setPlateId(newPlate.getRowId());
                    Table.insert(user, StudySchema.getInstance().getTableInfoWell(), position);
                    savePropertyBag(container, wellLsid, wellObjectLsid, getPositionProperties(plate, position));
                }
            }
            scope.commitTransaction();
            clearCache();
            return newPlate.getRowId();
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private Map<String, Object> getPositionProperties(PlateTemplateImpl plate, PositionImpl position)
    {
        List<? extends WellGroupTemplateImpl> groups = plate.getWellGroups(position);
        Map<String, Object> properties = new HashMap<String, Object>();
        int index = 0;
        for (WellGroupTemplateImpl group : groups)
        {
            if (group.contains(position))
                properties.put("Group " + index++, group.getLSID());
        }
        return properties;
    }

    private void savePropertyBag(Container container, String ownerLsid,
                                 String classLsid, Map<String, Object> props) throws SQLException
    {
        Map<String, ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, ownerLsid);
        if (resourceProperties != null && !resourceProperties.isEmpty())
            throw new IllegalStateException("Did not expect to find property set for new plate.");
        ObjectProperty[] objectProperties = new ObjectProperty[props.size()];
        int idx = 0;
        for (Map.Entry<String, Object> entry : props.entrySet())
        {
            String propertyURI = Lsid.isLsid(entry.getKey()) ? entry.getKey() : classLsid + "#" + entry.getKey();
            if (entry.getValue() != null)
                objectProperties[idx++] = new ObjectProperty(ownerLsid, container, propertyURI, entry.getValue());
            else
                objectProperties[idx++] = new ObjectProperty(ownerLsid, container, propertyURI, entry.getValue(), PropertyType.STRING);
        }
        try {
            if (objectProperties.length > 0)
                OntologyManager.insertProperties(container, ownerLsid, objectProperties);
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
    }

    public PlateQueryView getPlateGridView(ViewContext context)
    {
        return getPlateGridView(context, null);
    }

    public PlateQueryView getPlateGridView(ViewContext context, SimpleFilter filter)
    {
        return PlateSchema.createPlateQueryView(context, filter);
    }

    public PlateQueryView getWellGroupGridView(ViewContext context)
    {
        return PlateSchema.createWellGroupQueryView(context, null);
    }

    public PlateQueryView getWellGroupGridView(ViewContext context, WellGroup.Type showOnlyType)
    {
        return PlateSchema.createWellGroupQueryView(context, null, showOnlyType);
    }

    public Set<String> getDistinctTemplateNames() throws SQLException
    {
        synchronized (TEMPLATE_NAME_SYNC_OBJ)
        {
            if (_distinctTemplateNames == null)
            {
                DbSchema schema = StudySchema.getInstance().getSchema();
                TableInfo plateTable = StudySchema.getInstance().getTableInfoPlate();
                ResultSet rs = null;
                try
                {
                    rs = Table.executeQuery(schema, "SELECT DISTINCT Name FROM " + schema.getName() + "." + plateTable.getName(), null);
                    _distinctTemplateNames = new HashSet<String>();
                    while (rs.next())
                        _distinctTemplateNames.add(rs.getString("Name"));
                }
                finally
                {
                    if (rs != null) try { rs.close(); } catch (SQLException e) {}
                }
            }
            return _distinctTemplateNames;
        }
    }

    public void deletePlate(Container container, int rowid) throws SQLException
    {
        SimpleFilter plateFilter = new SimpleFilter();
        plateFilter.addCondition("RowId", rowid);
        plateFilter.addCondition("Container", container.getId());
        PlateTemplateImpl plate = Table.selectObject(StudySchema.getInstance().getTableInfoPlate(),
                plateFilter, null, PlateTemplateImpl.class);
        WellGroupTemplateImpl[] wellgroups = getWellGroups(plate);
        PositionImpl[] positions = getPositions(plate);

        List<String> lsids = new ArrayList<String>();
        lsids.add(plate.getLSID());
        for (WellGroupTemplateImpl wellgroup : wellgroups)
            lsids.add(wellgroup.getLSID());
        for (PositionImpl position : positions)
            lsids.add(position.getLsid());

        SimpleFilter plateIdFilter = new SimpleFilter();
        plateIdFilter.addCondition("Container", container.getId());
        plateIdFilter.addCondition("PlateId", plate.getRowId());

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.ensureTransaction();
            AttachmentService.get().deleteAttachments(plate);
            OntologyManager.deleteOntologyObjects(container, lsids.toArray(new String[lsids.size()]));
            Table.delete(StudySchema.getInstance().getTableInfoWell(), plateIdFilter);
            Table.delete(StudySchema.getInstance().getTableInfoWellGroup(), plateIdFilter);
            Table.delete(StudySchema.getInstance().getTableInfoPlate(), plateFilter);
            scope.commitTransaction();
            clearCache();
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public void deleteAllPlateData(Container container) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Container", container.getId());
        Table.delete(StudySchema.getInstance().getTableInfoWell(), filter);
        Table.delete(StudySchema.getInstance().getTableInfoWellGroup(), filter);
        Table.delete(StudySchema.getInstance().getTableInfoPlate(), filter);
        clearCache();
    }

    public void registerDetailsLinkResolver(PlateService.PlateDetailsResolver resolver)
    {
        _detailsLinkResolvers.add(resolver);
    }

    public ActionURL getDetailsURL(Plate plate)
    {
        for (PlateService.PlateDetailsResolver resolver : _detailsLinkResolvers)
        {
            ActionURL detailsURL = resolver.getDetailsURL(plate);
            if (detailsURL != null)
                return detailsURL;
        }
        return null;
    }

    public List<PlateTypeHandler> getPlateTypeHandlers()
    {
        List<PlateTypeHandler> result = new ArrayList<PlateTypeHandler>(_plateTypeHandlers.values());
        Collections.sort(result, new Comparator<PlateTypeHandler>()
        {
            public int compare(PlateTypeHandler o1, PlateTypeHandler o2)
            {
                return o1.getAssayType().toLowerCase().compareTo(o2.getAssayType().toLowerCase());
            }
        });
        return result;
    }

    public PlateTypeHandler getPlateTypeHandler(String plateTypeName)
    {
        return _plateTypeHandlers.get(plateTypeName);
    }

    private static class PlateLsidHandler implements LsidManager.LsidHandler
    {
        protected PlateImpl getPlate(Lsid lsid)
        {
            try
            {
                return PlateManager.get().getPlate(lsid.toString());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        public String getDisplayURL(Lsid lsid)
        {
            PlateImpl plate = getPlate(lsid);
            if (plate == null)
                return null;
            return PlateManager.get().getDetailsURL(plate).getLocalURIString();
        }

        public ExpObject getObject(Lsid lsid)
        {
            throw new UnsupportedOperationException("Not Yet Implemented.");
        }

        public Container getContainer(Lsid lsid)
        {
            PlateImpl plate = getPlate(lsid);
            if (plate == null)
                return null;
            return plate.getContainer();
        }

        public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
        {
            Container c = getContainer(lsid);
            if (c != null)
                return c.hasPermission(user, perm);
            return false;
        }
    }

    private static class WellGroupLsidHandler implements LsidManager.LsidHandler
    {
        protected WellGroup getWellGroup(Lsid lsid)
        {
            try
            {
                return PlateManager.get().getWellGroup(lsid.toString());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        public String getDisplayURL(Lsid lsid)
        {
            WellGroup wellGroup = getWellGroup(lsid);
            if (lsid == null)
                return null;
            return PlateManager.get().getDetailsURL(wellGroup.getPlate()).getLocalURIString();
        }

        public ExpObject getObject(Lsid lsid)
        {
            throw new UnsupportedOperationException("Not Yet Implemented.");
        }

        public Container getContainer(Lsid lsid)
        {
            WellGroup wellGroup = getWellGroup(lsid);
            if (wellGroup == null)
                return null;
            return wellGroup.getContainer();
        }

        public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
        {
            Container c = getContainer(lsid);
            if (c != null)
                return c.hasPermission(user, perm);
            return false;
        }
    }
    
    public void registerLsidHandlers()
    {
        if (_lsidHandlersRegistered)
            throw new IllegalStateException("Cannot register lsid handlers twice.");
        PlateLsidHandler plateHandler = new PlateLsidHandler();
        WellGroupLsidHandler wellgroupHandler = new WellGroupLsidHandler();
        LsidManager.get().registerHandler("PlateTemplate", plateHandler);
        LsidManager.get().registerHandler("PlateInstance", plateHandler);
        LsidManager.get().registerHandler("WellGroupTemplate", wellgroupHandler);
        LsidManager.get().registerHandler("WellGroupInstance", wellgroupHandler);
        _lsidHandlersRegistered = true;
    }

    public static PlateManager get()
    {
        return (PlateManager) PlateService.get();
    }

    public void deleteDataFile(Plate plate) throws SQLException
    {
        String guid = ((PlateImpl) plate).getDataFileId();
        if (guid != null)
            AttachmentService.get().deleteAttachments(plate);
    }

    public void setDataFile(User user, Plate plate, AttachmentFile file) throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        AttachmentService.get().addAttachments(plate, Collections.singletonList(file), user);
    }

    public ActionURL getDataFileURL(Plate iplate, Class<? extends Controller> downloadClass)
    {
        PlateImpl plate = (PlateImpl) iplate;
        if (plate.getDataFileId() != null)
        {
            List<Attachment> attachments = AttachmentService.get().getAttachments(plate);
            if (!attachments.isEmpty())
            {
                assert attachments.size() == 1 : "Expected only one data file per plate";
                return new DownloadURL(downloadClass, plate.getContainer(), plate.getDataFileId(), attachments.get(0).getName());
            }
        }
        return null;
    }

    public PlateTemplate copyPlateTemplate(PlateTemplate source, User user, Container destContainer)
            throws SQLException, PlateService.NameConflictException
    {
        PlateTemplate destination = PlateService.get().getPlateTemplate(destContainer, source.getName());
        if (destination != null)
            throw new PlateService.NameConflictException(source.getName());
        destination = PlateService.get().createPlateTemplate(destContainer, source.getType(), source.getRows(), source.getColumns());
        destination.setName(source.getName());
        for (String property : source.getPropertyNames())
            destination.setProperty(property, source.getProperty(property));
        for (WellGroupTemplate originalGroup : source.getWellGroups())
        {
            List<Position> positions = new ArrayList<Position>();
            for (Position position : originalGroup.getPositions())
                positions.add(destination.getPosition(position.getRow(), position.getColumn()));
            WellGroupTemplate copyGroup = destination.addWellGroup(originalGroup.getName(), originalGroup.getType(), positions);
            for (String property : originalGroup.getPropertyNames())
                copyGroup.setProperty(property, originalGroup.getProperty(property));
        }
        PlateService.get().save(destContainer, user, destination);
        return getPlateTemplate(destContainer, destination.getName());
    }

    public void registerPlateTypeHandler(PlateTypeHandler handler)
    {
        if (_plateTypeHandlers.containsKey(handler.getAssayType()))
        {
            throw new IllegalArgumentException(handler.getAssayType());
        }
        _plateTypeHandlers.put(handler.getAssayType(), handler);
    }

    private String getPlateTemplateCacheKey(Container container, int rowId)
    {
        return PlateTemplateImpl.class.getName() + "/Folder-" + container.getRowId() + "-" + rowId;
    }

    private String getPlateTemplateCacheKey(Container container, String idString)
    {
        return PlateTemplateImpl.class.getName() + "/Folder-" + container.getRowId() + "-" + idString;
    }

    private static final StringKeyCache<PlateTemplateImpl> PLATE_TEMPLATE_CACHE = CacheManager.getSharedCache();

    private void cache(PlateTemplateImpl template)
    {
        if (template.getRowId() == null)
            return;
        PLATE_TEMPLATE_CACHE.put(getPlateTemplateCacheKey(template.getContainer(), template.getRowId().intValue()), template);
        PLATE_TEMPLATE_CACHE.put(getPlateTemplateCacheKey(template.getContainer(), template.getEntityId()), template);
    }

    private void clearCache()
    {
        PLATE_TEMPLATE_CACHE.removeUsingPrefix(PlateTemplateImpl.class.getName());
    }

    private PlateTemplateImpl getCachedPlateTemplate(Container container, int rowId)
    {
        return PLATE_TEMPLATE_CACHE.get(getPlateTemplateCacheKey(container, rowId));
    }

    private PlateTemplateImpl getCachedPlateTemplate(Container container, String idString)
    {
        return PLATE_TEMPLATE_CACHE.get(getPlateTemplateCacheKey(container, idString));
    }

    @Override
    public DilutionCurve getDilutionCurve(WellGroup wellGroup, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, DilutionCurve.FitType type) throws DilutionCurve.FitFailedException
    {
        return CurveFitFactory.getCurveImpl(wellGroup, assumeDecreasing, percentCalculator, type);
    }

    @Override
    public DilutionCurve getDilutionCurve(List<WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, DilutionCurve.FitType type) throws DilutionCurve.FitFailedException
    {
        return CurveFitFactory.getCurveImpl(wellGroups, assumeDecreasing, percentCalculator, type);
    }
}
