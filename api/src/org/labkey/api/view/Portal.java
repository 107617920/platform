/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.view;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;


public class Portal
{
    private static final WebPartBeanLoader FACTORY = new WebPartBeanLoader();
    private static final StringKeyCache<WebPart[]> WEB_PART_CACHE = CacheManager.getSharedCache();

    private static final String PORTAL_PREFIX = "Portal/";
    private static final String SCHEMA_NAME = "portal";

    public static final int MOVE_UP = 0;
    public static final int MOVE_DOWN = 1;

    private static HashMap<String, WebPartFactory> _viewMap = null;
    private static MultiHashMap<String, String> _regionMap = null;


    public static String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public static SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public static TableInfo getTableInfoPortalWebParts()
    {
        return getSchema().getTable("PortalWebParts");
    }

    public static void containerDeleted(Container c)
    {
        WEB_PART_CACHE.removeUsingPrefix(getCacheKey(null));

        try
        {
            Table.delete(getTableInfoPortalWebParts(), new SimpleFilter("Container", c.getId()));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static class WebPart implements Serializable
    {
        Container container;
        String pageId;
        int rowId;
        int index = 999;
        String name;
        String location = HttpView.BODY;
        boolean permanent;
        Map<String, String> propertyMap = new HashMap<String, String>();
        String properties = null;
        Map<String,Object> extendedProperties = null;

        static
        {
            BeanObjectFactory.Registry.register(WebPart.class, new WebPartBeanLoader());
        }

        public WebPart()
        {
        }

        public WebPart(WebPart copyFrom)
        {
            pageId = copyFrom.pageId;
            container = copyFrom.container;
            index = copyFrom.index;
            rowId = copyFrom.rowId;
            name = copyFrom.name;
            location = copyFrom.location;
            permanent = copyFrom.permanent;
            setProperties(copyFrom.properties);
            this.extendedProperties = copyFrom.extendedProperties;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public Container getContainer()
        {
            return container;
        }

        public void setContainer(Container container)
        {
            this.container = container;
        }
        
        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getLocation()
        {
            return location;
        }

        public void setLocation(String location)
        {
            this.location = location;
        }

        public Map<String, String> getPropertyMap()
        {
            return propertyMap;
        }

        public void setProperty(String k, String v)
        {
            propertyMap.put(k, v);
        }

        public String getProperties()
        {
            return PageFlowUtil.toQueryString(propertyMap.entrySet());
        }

        public void setProperties(String query)
        {
            Pair<String, String>[] props = PageFlowUtil.fromQueryString(query);
            for (Pair<String, String> prop : props)
                setProperty(prop.first, prop.second);
        }

        public PropertyValues getPropertyValues()
        {
            MutablePropertyValues pvs = new MutablePropertyValues(getPropertyMap());
            return pvs;
        }

        public void setExtendedProperties(Map<String,Object> extendedProperties)
        {
            this.extendedProperties = extendedProperties;
        }

        public Map<String,Object> getExtendedProperties()
        {
            return this.extendedProperties;
        }

        public boolean isPermanent()
        {
            return permanent;
        }

        public void setPermanent(boolean permanent)
        {
            this.permanent = permanent;
        }

        public ActionURL getCustomizePostURL(ViewContext viewContext)
        {
            ActionURL current = viewContext.getActionURL();
            ActionURL ret = PageFlowUtil.urlProvider(ProjectUrls.class).getCustomizeWebPartURL(container);
            ret.addParameter("pageId", getPageId());
            ret.addParameter("index", Integer.toString(getIndex()));
            if (null != current.getReturnURL())
                ret.addReturnURL(current.getReturnURL());
            return ret;
        }

        public String getHiddenFieldsHtml(ViewContext viewContext)
        {
            ActionURL current = viewContext.getActionURL();
            return "<input type=\"hidden\" name=\"pageId\" value=\"" + getPageId() + "\">\n<input type=\"hidden\" name=\"index\" value=\"" + getIndex() + "\">" +
                    "<input type=\"hidden\" name=\"" + ActionURL.Param.returnUrl + "\" value=\"" + PageFlowUtil.filter(current.getReturnURL()) + "\">";
        }

        public int getRowId()
        {
            return rowId;
        }

        public void setRowId(int rowId)
        {
            this.rowId = rowId;
        }
    }

    public static class WebPartBeanLoader extends BeanObjectFactory<WebPart>
    {
        public WebPartBeanLoader()
        {
            super(WebPart.class);
        }

        protected void fixupBean(WebPart part)
        {
            if (null == part.location || part.location.length() == 0)
                part.location = HttpView.BODY;
        }
    }


    @Deprecated
    public static WebPart[] getParts(String id)
    {
        return getParts(id, false);
    }


    @Deprecated
    public static WebPart[] getParts(String id, boolean force)
    {
        return getParts(ContainerManager.getForId(id), id, force);
    }

    public static WebPart[] getParts(Container c)
    {
        return getParts(c, c.getId(), false);
    }

    public static WebPart[] getParts(Container c, String id)
    {
        return getParts(c, id, false);
    }

    public static WebPart[] getParts(Container c, String id, boolean force)
    {
        String key = getCacheKey(id);
        WebPart[] parts;

        if (!force)
        {
            parts = WEB_PART_CACHE.get(key);
            if (null != parts)
                return parts;
        }

        SimpleFilter filter = new SimpleFilter("PageId", id);
        filter.addCondition("Container", c.getId());
        try
        {
            parts = Table.select(getTableInfoPortalWebParts(), Table.ALL_COLUMNS, filter, new Sort("Index"), WebPart.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        // TODO: Really?!?  Caching for only one minute?
        WEB_PART_CACHE.put(key, parts, CacheManager.MINUTE);
        return parts;
    }

    public static WebPart getPart(int webPartRowId)
    {
        return Table.selectObject(getTableInfoPortalWebParts(), webPartRowId, WebPart.class);
    }

    @Nullable
    public static WebPart getPart(Container c, String pageId, int index)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("PageId", pageId);
            filter.addCondition("Container", c.getId());
            filter.addCondition("index", index);
            WebPart[] webParts = Table.select(getTableInfoPortalWebParts(), Table.ALL_COLUMNS, filter, null, WebPart.class);
            assert webParts.length == 0 || webParts.length == 1 : "Cannot have multiple web parts with the same page and index.";
            if (webParts.length == 1)
                return webParts[0];
            else
                return null;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public static void updatePart(User u, WebPart part) throws SQLException
    {
        Table.update(u, getTableInfoPortalWebParts(), part, new Object[]{part.getRowId()});
        _clearCache(part.getPageId());
    }

    /**
     * Add a web part to the container at the end of the list
     */
    public static WebPart addPart(Container c, WebPartFactory desc, String location)
            throws SQLException
    {
        return addPart(c, desc, location, -1);
    }

    /**
     * Add a web part to a particular page at the end of the list
     */
    public static WebPart addPart(Container c, String pageId, WebPartFactory desc, String location)
            throws SQLException
    {
        return addPart(c, pageId, desc, location, -1, null);
    }

    /**
     * Add a web part to the container at the end of the list, with properties
     */
    public static WebPart addPart(Container c, WebPartFactory desc, String location, Map<String, String> properties)
            throws SQLException
    {
        return addPart(c, desc, location, -1, properties);
    }

    /**
     * Add a web part to the container at the specified index
     */
    public static WebPart addPart(Container c, WebPartFactory desc, String location, int partIndex)
            throws SQLException
    {
        return addPart(c, desc, location, partIndex, null);
    }

    public static WebPart addPart(Container c, WebPartFactory desc, String location, int partIndex, Map<String, String> properties)
    {
        return addPart(c, c.getId(), desc, location, partIndex, properties);
    }

    /**
     * Add a web part to the container at the specified index, with properties
     */
    public static WebPart addPart(Container c, String pageId, WebPartFactory desc, String location, int partIndex, Map<String, String> properties)
    {
        WebPart[] parts = getParts(c, pageId, false);

        WebPart newPart = new Portal.WebPart();
        newPart.setContainer(c);
        newPart.setPageId(pageId);
        newPart.setName(desc.getName());
        newPart.setIndex(partIndex >= 0 ? partIndex : parts.length);
        if (location == null)
        {
            newPart.setLocation(desc.getDefaultLocation());
        }
        else
        {
            newPart.setLocation(location);
        }

        if (properties != null)
        {
            for (Map.Entry prop : properties.entrySet())
            {
                String propName = prop.getKey().toString();
                String propValue = prop.getValue().toString();
                newPart.setProperty(propName, propValue);
            }
        }

        if (parts == null)
            parts = new Portal.WebPart[]{newPart};
        else
        {
            Portal.WebPart[] partsNew = new Portal.WebPart[parts.length + 1];
            int iNext = 0;
            for (final WebPart currentPart : parts)
            {
                if (iNext == newPart.getIndex())
                    partsNew[iNext++] = newPart;
                final int iPart = currentPart.getIndex();
                if (iPart > newPart.getIndex())
                    currentPart.setIndex(iPart + 1);
                partsNew[iNext++] = currentPart;
            }
            if (iNext == newPart.getIndex())
                partsNew[iNext++] = newPart;
            parts = partsNew;
        }

        Portal.saveParts(c, pageId, parts);

//        Set<Module> activeModules = new HashSet<Module>(c.getActiveModules());
//        if (!activeModules.contains(desc.getModule()))
//        {
//            activeModules.add(desc.getModule());
//            c.setActiveModules(activeModules);
//        }
//
        return newPart;
    }

    public static void saveParts(Container c, WebPart[] newParts)
    {
        saveParts(c, c.getId(), newParts);
    }

    public static void saveParts(Container c, String id, WebPart[] newParts)
    {
        // make sure indexes are unique
        Arrays.sort(newParts, new Comparator<WebPart>()
        {
            public int compare(WebPart w1, WebPart w2)
            {
                return w1.index - w2.index;
            }
        });

        for (int i = 0; i < newParts.length; i++)
        {
            WebPart part = newParts[i];
            part.index = i + 1;
            part.pageId = id;
            part.container = c;
        }

        try
        {
            WebPart[] oldParts = getParts(c, id, false);
            Set<Integer> oldPartIds = new HashSet<Integer>();
            for (WebPart oldPart : oldParts)
                oldPartIds.add(oldPart.getRowId());
            Set<Integer> newPartIds = new HashSet<Integer>();
            for (WebPart newPart : newParts)
            {
                if (newPart.getRowId() >= 0)
                    newPartIds.add(newPart.getRowId());
            }

            getSchema().getScope().ensureTransaction();

            // delete any removed webparts:
            for (Integer oldId : oldPartIds)
            {
                if (!newPartIds.contains(oldId))
                    Table.delete(getTableInfoPortalWebParts(), oldId);
            }

            for (WebPart part1 : newParts) {
                Map m = FACTORY.toMap(part1, null);

                if (oldPartIds.contains(part1.getRowId()))
                    Table.update(null, getTableInfoPortalWebParts(), m, part1.getRowId());
                else
                    Table.insert(null, getTableInfoPortalWebParts(), m);
            }
            getSchema().getScope().commitTransaction();
        }
        catch (SQLException x)
        {
            if (!SqlDialect.isConstraintException(x))
                throw new RuntimeSQLException(x);
        }
        finally
        {
            getSchema().getScope().closeConnection();
        }
        _clearCache(id);
    }


    private static void _clearCache(String id)
    {
        WEB_PART_CACHE.remove(getCacheKey(id));
    }


    // CAREFUL: On SQL Server, this id could be all uppercase (when coming from an ENTITYID column) or all lower case
    // (when coming from a VARCHAR column), so we must normalize.
    private static String getCacheKey(@Nullable String id)
    {
        if (null != id)
            return PORTAL_PREFIX + id.toLowerCase();
        else
            return PORTAL_PREFIX;
    }


    public static class AddWebParts
    {
        public String pageId;
        public String location;
        public List<String> webPartNames;
        public List<String> rightWebPartNames;
    }

    private static void addCustomizeDropdowns(Container c, HttpView template, String id, Collection occupiedLocations)
    {
        Set<String> regionNames = getRegionMap().keySet();
        boolean rightEmpty = !occupiedLocations.contains("right");
        AddWebParts bodyAddPart = null;
        List<String> rightParts = null;
        
        for (String regionName : regionNames)
        {
            List<String> partsToAdd = Portal.getPartsToAdd(c, regionName);

            if ("right".equals(regionName) && rightEmpty)
                rightParts = partsToAdd;
            else
            {
                //TODO: Make addPartView a real class & move to ProjectController
                AddWebParts addPart = new AddWebParts();
                addPart.pageId = id;
                addPart.location = regionName;
                addPart.webPartNames = partsToAdd;
                WebPartView addPartView = new JspView<AddWebParts>("/org/labkey/api/view/addWebPart.jsp", addPart);
                addPartView.setFrame(WebPartView.FrameType.NONE);

                // save these off in case we have to re-shuffle due to an empty right region:
                if (HttpView.BODY.equals(regionName))
                    bodyAddPart = addPart;

                addViewToRegion(template, regionName, addPartView);
            }
        }
        if (rightEmpty && bodyAddPart != null && rightParts != null)
            bodyAddPart.rightWebPartNames = rightParts;
    }

    private static void addViewToRegion(HttpView template, String regionName, HttpView view)
    {
        //place
        ModelAndView region = template.getView(regionName);
        if (null == region)
        {
            region = new VBox();
            template.setView(regionName, region);
        }
        if (!(region instanceof VBox))
        {
            region = new VBox(region);
            template.setView(regionName, region);
        }
        ((VBox) region).addView(view);
    }

    public static void populatePortalView(ViewContext context, String id, HttpView template)
            throws Exception
    {
        populatePortalView(context, id, template, context.getContainer().hasPermission(context.getUser(), AdminPermission.class));
    }

    public static void populatePortalView(ViewContext context, String id, HttpView template, boolean canCustomize)
            throws Exception
    {
        String contextPath = context.getContextPath();
        WebPart[] parts = getParts(context.getContainer(), id, false);

        MultiMap<String, WebPart> locationMap = getPartsByLocation(parts);
        Collection<String> locations = locationMap.keySet();

        for (String location : locations)
        {
            Collection<WebPart> partsForLocation = locationMap.get(location);
            int i = 0;

            for (WebPart part : partsForLocation)
            {
                //instantiate
                WebPartFactory desc = Portal.getPortalPart(part.getName());
                if (null == desc)
                    continue;

                WebPartView view = getWebPartViewSafe(desc, context, part);
                if (null == view)
                    continue;
                view.prepare(view.getModelBean());

                NavTree navTree = view.getPortalLinks();
                if (canCustomize)
                {

                    if (desc.isEditable() && view.getCustomize() == null)
                        view.setCustomize(new NavTree("", getCustomizeURL(context, part)));

                    if (i > 0)
                        navTree.addChild("Move Up", getMoveURL(context, part, MOVE_UP), contextPath + "/_images/partup.png");
                    else if (part.getLocation().equals(WebPartFactory.LOCATION_RIGHT))
                        navTree.addChild("Move Up", getMoveURL(context, part, MOVE_UP), contextPath + "/_images/partupg.png");
                    else
                        navTree.addChild("", "", contextPath + "/_images/partupg.png");

                    if (i < partsForLocation.size() - 1)
                        navTree.addChild("Move Down", getMoveURL(context, part, MOVE_DOWN), contextPath + "/_images/partdown.png");
                    else if (part.getLocation().equals(WebPartFactory.LOCATION_RIGHT))
                        navTree.addChild("Move Down", getMoveURL(context, part, MOVE_DOWN), contextPath + "/_images/partdowng.png");
                    else
                        navTree.addChild("", "", contextPath + "/_images/partdowng.png");
                    
                    if (!part.isPermanent())
                        navTree.addChild("Remove From Page", getDeleteURL(context, part), contextPath + "/_images/partdelete.png");
                }

                addViewToRegion(template, location, view);
                i++;
            }
        }

        if (canCustomize)
            addCustomizeDropdowns(context.getContainer(), template, id, locations);
    }


    public static String getCustomizeURL(ViewContext context, Portal.WebPart webPart)
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getCustomizeWebPartURL(context.getContainer(), webPart, context.getActionURL()).getLocalURIString();
    }

    private static final boolean USE_ASYNC_PORTAL_ACTIONS = true;
    public static String getMoveURL(ViewContext context, Portal.WebPart webPart, int direction)
    {
        if (USE_ASYNC_PORTAL_ACTIONS)
        {
            String methodName = direction == MOVE_UP ? "moveWebPartUp" : "moveWebPartDown";
            return "javascript:LABKEY.Portal." + methodName + "({" +
                    "webPartId:" + webPart.getRowId() + "," +
                    "updateDOM:true" +
                    "})";
        }
        else
            return PageFlowUtil.urlProvider(ProjectUrls.class).getMoveWebPartURL(context.getContainer(), webPart, direction, context.getActionURL()).getLocalURIString();
    }


    public static String getDeleteURL(ViewContext context, Portal.WebPart webPart)
    {
        if (USE_ASYNC_PORTAL_ACTIONS)
        {
            return "javascript:LABKEY.Portal.removeWebPart({" +
                    "webPartId:" + webPart.getRowId() + "," +
                    "updateDOM:true" +
                    "})";
        }
        else
            return PageFlowUtil.urlProvider(ProjectUrls.class).getDeleteWebPartURL(context.getContainer(), webPart, context.getActionURL()).getLocalURIString();
    }


    public static MultiMap<String, WebPart> getPartsByLocation(WebPart[] parts)
    {
        MultiMap<String, WebPart> multiMap = new MultiHashMap<String, WebPart>();

        for (WebPart part : parts)
        {
            if (null == part.getName() || 0 == part.getName().length())
                continue;
            String location = part.getLocation();
            multiMap.put(location, part);
        }

        return multiMap;
    }


    public static WebPartFactory getPortalPart(String name)
    {
        return getViewMap().get(name);
    }

    public static WebPartFactory getPortalPartCaseInsensitive(String name)
    {
        CaseInsensitiveHashMap<WebPartFactory> viewMap = new CaseInsensitiveHashMap<WebPartFactory>(getViewMap());
        return viewMap.get(name);
    }

    private static synchronized HashMap<String, WebPartFactory> getViewMap()
    {
        if (null == _viewMap || areWebPartMapsStale())
            initMaps();

        return _viewMap;
    }


    private static synchronized MultiHashMap<String, String> getRegionMap()
    {
        if (null == _regionMap || areWebPartMapsStale())
            initMaps();

        return _regionMap;
    }

    private static boolean areWebPartMapsStale()
    {
        List<Module> modules = ModuleLoader.getInstance().getModules();
        for (Module module : modules)
        {
            if (module.isWebPartFactorySetStale())
                return true;
        }
        return false;
    }

    private synchronized static void initMaps()
    {
        _viewMap = new HashMap<String, WebPartFactory>(20);
        _regionMap = new MultiHashMap<String, String>();

        List<Module> modules = ModuleLoader.getInstance().getModules();
        for (Module module : modules)
        {
            Collection<WebPartFactory> factories = module.getWebPartFactories();
            if (null == factories)
                continue;
            for (WebPartFactory webpart : factories)
            {
                _viewMap.put(webpart.getName(), webpart);
                for (String legacyName : webpart.getLegacyNames())
                {
                    _viewMap.put(legacyName, webpart);
                }
                _regionMap.put(webpart.getDefaultLocation(), webpart.getName());
            }
        }

        //noinspection unchecked
        for (String key : _regionMap.keySet())
        {
            List<String> list = (List<String>)_regionMap.getCollection(key);
            Collections.sort(list);
        }
    }

    public static List<String> getPartsToAdd(Container c, String location)
    {
        //TODO: Cache these
        Set<String> webPartNames = new TreeSet<String>();

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            Collection<WebPartFactory> factories = module.getWebPartFactories();

            if (null != factories)
                for (WebPartFactory factory : factories)
                    if (factory.isAvailable(c, location))
                        webPartNames.add(factory.getName());
        }

        return new ArrayList<String>(webPartNames);
    }

    public static int purge() throws SQLException
    {
        return ContainerUtil.purgeTable(getTableInfoPortalWebParts(), "PageId");
    }

    public static WebPartView getWebPartViewSafe(WebPartFactory factory, ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        WebPartView view;

        try
        {
            view = factory.getWebPartView(portalCtx, webPart);
            if (view != null)
            {
                view.setWebPartRowId(webPart.getRowId());
                view.setLocation(webPart.getLocation());
            }
        }
        catch(Throwable t)
        {
            int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            String message = "An unexpected error occurred";
            view = ExceptionUtil.getErrorWebPartView(status, message, t, portalCtx.getRequest());
            view.setTitle(webPart.getName());
            view.setWebPartRowId(webPart.getRowId());
        }

        return view;
    }
}
