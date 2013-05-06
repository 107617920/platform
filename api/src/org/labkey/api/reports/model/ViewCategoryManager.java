/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
package org.labkey.api.reports.model;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.TestContext;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Oct 12, 2011
 * Time: 7:13:20 PM
 */
public class ViewCategoryManager implements ContainerManager.ContainerListener
{
    private static final Logger _log = Logger.getLogger(ViewCategoryManager.class);
    private static final ViewCategoryManager _instance = new ViewCategoryManager();
    private static final List<ViewCategoryListener> _listeners = new CopyOnWriteArrayList<ViewCategoryListener>();

    private static DatabaseCache<ViewCategory> _viewCategoryCache;

    synchronized DatabaseCache<ViewCategory> getCategoryCache()
    {
        if (_viewCategoryCache == null)
        {
            _viewCategoryCache = new DatabaseCache<ViewCategory>(CoreSchema.getInstance().getSchema().getScope(), 300, "View Category");
        }
        return _viewCategoryCache;
    }

    private ViewCategoryManager()
    {
        ContainerManager.addContainerListener(this);
    }

    public static ViewCategoryManager getInstance()
    {
        return _instance;
    }

    public TableInfo getTableInfoCategories()
    {
        return CoreSchema.getInstance().getSchema().getTable("ViewCategory");
    }

    public ViewCategory[] getCategories(Container c, User user)
    {
        return getCategories(c, user, new SimpleFilter());
    }

    public ViewCategory[] getCategories(Container c, User user, SimpleFilter filter)
    {
        try {
            filter.addCondition("Container", c);
            ViewCategory[] categories = Table.select(getTableInfoCategories(), Table.ALL_COLUMNS, filter, null, ViewCategory.class);

            return categories;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public ViewCategory getCategory(int rowId)
    {
        try {
            String cacheKey = getCacheKey(rowId);
            ViewCategory category = getCategoryCache().get(cacheKey);

            if (category != null)
                return category;

            SimpleFilter filter = new SimpleFilter("rowId", rowId);
            ViewCategory[] categories = Table.select(getTableInfoCategories(), Table.ALL_COLUMNS, filter, null, ViewCategory.class);

            assert categories.length <= 1;

            if (categories.length == 1)
            {
                getCategoryCache().put(cacheKey, categories[0]);
                return categories[0];
            }
            return null;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * looks up a view category by it's path
     * @param c
     * @param parts An array representing a path heirarchy. For example ["reports"] would represent a top level
     *              category named 'reports' whereas ["reports", "R"] would represent a subcategory named 'R' with
     *              a parent named 'reports'. Current heirarchy depth is limited to one level deep.
     * @return
     */
    public ViewCategory getCategory(Container c, String... parts)
    {
        if (parts.length > 2)
            throw new IllegalArgumentException("Only one level of view category is supported at this time");

        String cacheKey = getCacheKey(c, parts);
        ViewCategory category = getCategoryCache().get(cacheKey);

        if (category != null)
            return category;

        ViewCategory parent = null;

        // a subcategory with a parent
        if (parts.length == 2)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), c);
            filter.addCondition(FieldKey.fromParts("label"), parts[0]);
            filter.addClause(new SimpleFilter.SQLClause("parent IS NULL", null));

            TableSelector selector = new TableSelector(getTableInfoCategories(), filter, null);
            ViewCategory[] categories = selector.getArray(ViewCategory.class);

            // should only be one as there is a unique constraint on the db
            assert categories.length <= 1;

            if (categories.length == 1)
                parent = categories[0];
            else
                // expected a parent but couldn't find one
                return null;
        }

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), c);
        filter.addCondition(FieldKey.fromParts("label"), parts[parts.length-1]);

        // a subcategory with a parent
        if (parent != null)
            filter.addCondition(FieldKey.fromParts("parent"), parent.getRowId());
        else
            filter.addClause(new SimpleFilter.SQLClause("parent IS NULL", null));

        TableSelector selector = new TableSelector(getTableInfoCategories(), filter, null);
        ViewCategory[] categories = selector.getArray(ViewCategory.class);

        // should only be one as there is a unique constraint on the db
        assert categories.length <= 1;

        if (categories.length == 1)
        {
            getCategoryCache().put(cacheKey, categories[0]);
            return categories[0];
        }
        return null;
    }

    public void deleteCategory(Container c, User user, ViewCategory category)
    {
        if (category.isNew())
            throw new IllegalArgumentException("View category has not been saved to the database yet");

        if (!category.canDelete(c, user))
            throw new RuntimeException("You must be an administrator to delete a view category");

        List<ViewCategory> categoriesToDelete = new ArrayList<ViewCategory>();
        try {
            category = getCategory(category.getRowId());
            if (category == null)
                throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);

            categoriesToDelete.add(category);
            categoriesToDelete.addAll(category.getSubcategories());

            // delete the category definition (plus any subcategories) and fire the deleted event
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(getTableInfoCategories(), "").append(" WHERE RowId = ?");
            sql.append(" OR Parent = ?");
            sql.addAll(new Object[]{category.getRowId(), category.getRowId()});

            SqlExecutor executor = new SqlExecutor(CoreSchema.getInstance().getSchema().getScope());
            executor.execute(sql);

            getCategoryCache().clear();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        List<Throwable> errors = new ArrayList<Throwable>();
        for (ViewCategory vc : categoriesToDelete)
            errors.addAll(fireDeleteCategory(user, vc));

        if (errors.size() != 0)
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
                throw new RuntimeException(first);
        }
    }

    public ViewCategory saveCategory(Container c, User user, ViewCategory category)
    {
        try {
            ViewCategory ret = null;
            List<Throwable> errors;

            if (category.isNew()) // insert
            {
                // check for duplicates
                SimpleFilter filter = new SimpleFilter("label", category.getLabel());
                if (category.getParent() != null)
                    filter.addCondition(FieldKey.fromParts("parent"), category.getParent().getRowId());
                else
                    filter.addClause(new SimpleFilter.SQLClause("parent IS NULL", null));

                if (getCategories(c, user, filter).length > 0)
                {
                    if (category.getParent() != null)
                        throw new IllegalArgumentException("There is already a subcategory attached to the same parent with the name: " + category.getLabel());
                    else
                        throw new IllegalArgumentException("There is already a category in this folder with the name: " + category.getLabel());
                }
                category.beforeInsert(user, c.getId());

                ret = Table.insert(user, getTableInfoCategories(), category);

                getCategoryCache().clear();

                errors = fireCreatedCategory(user, ret);
            }
            else // update
            {
                ViewCategory existing = getCategory(category.getRowId());
                if (existing != null)
                {
                    existing.setLabel(category.getLabel());
                    existing.setDisplayOrder(category.getDisplayOrder());

                    ret = Table.update(user, getTableInfoCategories(), existing, existing.getRowId());

                    getCategoryCache().clear();

                    errors = fireUpdateCategory(user, ret);
                }
                else
                    throw new RuntimeException("The specified category does not exist, rowid: " + category.getRowId());
            }
            
            if (errors.size() != 0)
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException)first;
                else
                    throw new RuntimeException(first);
            }
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public List<ViewCategory> getSubCategories(ViewCategory category)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("parent"), category.getRowId());
        filter.addCondition(FieldKey.fromParts("Container"), category.getContainerId());

        TableSelector selector = new TableSelector(getTableInfoCategories(), filter, null);
        ViewCategory[] categories = selector.getArray(ViewCategory.class);

        return Arrays.asList(categories);
    }

    private String getCacheKey(Container c, String[] parts)
    {
        StringBuilder sb = new StringBuilder("ViewCategory-" + c);

        if (parts != null)
        {
            for (String part : parts)
                sb.append('-').append(parts);
        }
        return sb.toString();
    }

    private String getCacheKey(ViewCategory category)
    {
        if (category == null)
            return null;

        if (category.getParent() != null)
            return getCacheKey(ContainerManager.getForId(category.getContainerId()), new String[]{category.getParent().getLabel(), category.getLabel()});
        else
            return getCacheKey(ContainerManager.getForId(category.getContainerId()), new String[]{category.getLabel()});
    }

    private String getCacheKey(int categoryId)
    {
        return "ViewCategory-" + categoryId;
    }

    public static void addCategoryListener(ViewCategoryListener listener)
    {
        _listeners.add(listener);
    }

    public static void removeCategoryListener(ViewCategoryListener listener)
    {
        _listeners.remove(listener);
    }

    private static List<Throwable> fireDeleteCategory(User user, ViewCategory category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ViewCategoryListener l : _listeners)
        {
            try {
                l.categoryDeleted(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    private static List<Throwable> fireUpdateCategory(User user, ViewCategory category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ViewCategoryListener l : _listeners)
        {
            try {
                l.categoryUpdated(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    private static List<Throwable> fireCreatedCategory(User user, ViewCategory category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ViewCategoryListener l : _listeners)
        {
            try {
                l.categoryCreated(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    /**
     * Returns an existing category or creates a new one.
     * @param parts An array representing a path heirarchy. For example ["reports"] would represent a top level
     *              category named 'reports' whereas ["reports", "R"] would represent a subcategory named 'R' with
     *              a parent named 'reports'. Current heirarchy depth is limited to one level deep.
     * @return
     */

    public ViewCategory ensureViewCategory(Container c, User user, String... parts)
    {
        if (parts.length > 2)
            throw new IllegalArgumentException("Only one level of view category is supported at this time");

        ViewCategory category = getCategory(c, parts);
        if (category == null)
        {
            ViewCategory parent = null;

            if (parts.length == 2)
            {
                parent = getCategory(c, parts[0]);
                if (parent == null)
                {
                    parent = new ViewCategory(parts[0], null);
                    parent = saveCategory(c, user, parent);
                }
            }
            category = new ViewCategory(parts[parts.length-1], parent);
            category = saveCategory(c, user, category);
        }
        return category;
    }

    public static final String ENCODED_AMP = "&amp;";
    /**
     * Used to encode a view category to a serializable form that can later be decoded by the decode(String)
     * method and then used in either getCategory or ensureViewCategory.
     *
     * Parent child heirarchy is encoded into the string.
     *
     * @return encoded String that can be serialized and then decoded using the decode(String) method.
     */
    public String encode(ViewCategory category)
    {
        StringBuilder sb = new StringBuilder();

        if (category.getParent() != null)
        {
            sb.append(category.getParent().getLabel().replaceAll("&", ENCODED_AMP)).append("&");
        }
        sb.append(category.getLabel().replaceAll("&", ENCODED_AMP));

        return sb.toString();
    }

    /**
     * Used to decode a view category from a serializable form and thenn used in either getCategory or ensureViewCategory.
     *
     * Parent child heirarchy is encoded into the string.
     *
     * @return String[] that represents the path heirarcy of the category.
     */
    public String[] decode(String category)
    {
        List<String> names = new ArrayList<String>();
        String[] originalAmps;

        StringBuffer sb = new StringBuffer();
        String delim = "";
        String trailing = "";

        // split on any original but encoded &'s
        if (category.contains(ENCODED_AMP))
        {
            // boundary cases
            if (category.startsWith(ENCODED_AMP))
                delim = "&";
            if (category.endsWith(ENCODED_AMP))
                trailing = "&";

            originalAmps = category.split(ENCODED_AMP);
        }
        else
            originalAmps = new String[]{category};

        for (String section : originalAmps)
        {
            // re-add the original &
            sb.append(delim);
            for (int i=0; i < section.length(); i++)
            {
                char c = section.charAt(i);

                switch (c)
                {
                    case '&' :
                        // this is the section delimiter
                        names.add(sb.toString());
                        sb = new StringBuffer();
                        break;
                    default :
                        sb.append(c);
                }
            }
            delim = "&";
        }
        sb.append(trailing);

        if (sb.length() > 0)
            names.add(sb.toString());

        return names.toArray(new String[names.size()]);
    }

    static
    {
        ObjectFactory.Registry.register(ViewCategory.class, new ViewCategoryFactory());
    }

    public static class ViewCategoryFactory extends BeanObjectFactory<ViewCategory>
    {
        ViewCategoryFactory()
        {
            super(ViewCategory.class);
        }

        @Override
        protected void fixupMap(Map<String, Object> m, ViewCategory bean)
        {
            if (null != bean.getParent())
            {
                m.put("Parent", bean.getParent().getRowId());
            }
        }
    }

    public static class TestCase extends Assert
    {
        private static final String[] labels = {"Demographics", "Exam", "Discharge", "Final Exam"};
        private static final String[] subLabels = {"sub1", "sub&2", "sub3&", "sub &label", "sub_label&amp;", "&sub&label&"};

        @Test
        public void test() throws Exception
        {
            ViewCategoryManager mgr = ViewCategoryManager.getInstance();
            Container c = ContainerManager.getSharedContainer();
            User user = TestContext.get().getUser();

            final List<String> notifications = new ArrayList<String>();
            for (String label : labels)
                notifications.add(label);

            ViewCategoryListener listener = new ViewCategoryListener(){
                @Override
                public void categoryDeleted(User user, ViewCategory category)
                {
                    notifications.remove(category.getLabel());
                }

                @Override
                public void categoryCreated(User user, ViewCategory category)
                {
                }

                @Override
                public void categoryUpdated(User user, ViewCategory category)
                {
                }
            };
            ViewCategoryManager.addCategoryListener(listener);

            // create some categories
            int i=0;
            for (String label : labels)
            {
                ViewCategory cat = new ViewCategory();

                cat.setLabel(label);
                cat.setDisplayOrder(i++);

                cat = mgr.saveCategory(c, user, cat);

                // test serialization encoding and decoding
                String encoded = mgr.encode(cat);
                String parts[] = mgr.decode(encoded);

                assertTrue(parts.length == 1);
                assertEquals(parts[0], cat.getLabel());

                // create sub categories
                for (String subLabel : subLabels)
                {
                    ViewCategory subcat = new ViewCategory();

                    subcat.setLabel(subLabel);
                    subcat.setDisplayOrder(i++);
                    subcat.setParent(cat);

                    subcat = mgr.saveCategory(c, user, subcat);

                    // test serialization encoding and decoding
                    encoded = mgr.encode(subcat);
                    parts = mgr.decode(encoded);

                    assertTrue(parts.length == 2);
                    assertEquals(parts[0], cat.getLabel());
                    assertEquals(parts[1], subLabel);
                }

                // verify we don't allow duplicate subcategory names
                boolean duplicate = false;
                try {

                    ViewCategory subcat = new ViewCategory();

                    subcat.setLabel(subLabels[0]);
                    subcat.setDisplayOrder(i++);
                    subcat.setParent(cat);

                    mgr.saveCategory(c, user, subcat);
                }
                catch (IllegalArgumentException e)
                {
                    duplicate = true;
                }

                assertTrue("Duplicate subcategory name was allowed", duplicate);
            }

            // get categories
            Map<String, ViewCategory> categoryMap = new HashMap<String, ViewCategory>();
            for (ViewCategory cat : mgr.getCategories(c, user))
            {
                categoryMap.put(cat.getLabel(), cat);
            }

            List<String> subCategoryNames = Arrays.asList(subLabels);
            for (String label : labels)
            {
                assertTrue(categoryMap.containsKey(label));

                // check for subcategories
                ViewCategory cat = categoryMap.get(label);
                for (ViewCategory subCategory : cat.getSubcategories())
                {
                    assertTrue(subCategoryNames.contains(subCategory.getLabel()));
                    assertTrue(subCategory.getParent().getRowId() == cat.getRowId());
                }
            }

            // delete the top level categories, make sure the subcategories get deleted as well
            for (String label : labels)
            {
                ViewCategory cat = categoryMap.get(label);

                mgr.deleteCategory(c, user, cat);
            }

            ViewCategory top = mgr.ensureViewCategory(c, user, "top");

            assertNotNull(top);
            assertTrue(top.getParent() == null);
            assertTrue(top.getLabel().equals("top"));

            ViewCategory subTop = mgr.ensureViewCategory(c, user, "top", "sub");
            // issue : 17123
            mgr.ensureViewCategory(c, user, "top", "top");
            mgr.ensureViewCategory(c, user, "top");

            assertNotNull(subTop);
            assertTrue(subTop.getParent().getLabel().equals("top"));
            assertTrue(subTop.getLabel().equals("sub"));

            ViewCategory subBottom = mgr.ensureViewCategory(c, user, "bottom", "sub");

            assertNotNull(subBottom);
            assertTrue(subBottom.getParent().getLabel().equals("bottom"));
            assertTrue(subBottom.getLabel().equals("sub"));

            mgr.deleteCategory(c, user, top);
            mgr.deleteCategory(c, user, subBottom.getParent());

            // make sure all the listeners were invoked correctly
            assertTrue(notifications.isEmpty());
            ViewCategoryManager.removeCategoryListener(listener);
        }
    }

    @Override
    public void containerCreated(Container c, User user){}
    @Override
    public void containerMoved(Container c, Container oldParent, User user){}
    @Override
    public void propertyChange(PropertyChangeEvent evt){}

    @Override
    public void containerDeleted(Container c, User user)
    {
        try
        {
            ContainerUtil.purgeTable(getTableInfoCategories(), c, "Container");
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }
}
