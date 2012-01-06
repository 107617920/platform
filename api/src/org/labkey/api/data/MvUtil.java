/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for dealing with Missing Value Indicators
 *
 * User: jgarms
 * Date: Jan 14, 2009
 */
public class MvUtil
{
    private static final String CACHE_PREFIX = MvUtil.class.getName() + "/";

    // Sentinel for the cache: if a container has no mv indicators set, we use this to indicate,
    // as null means a cache miss.
    private static final Map<String,String> NO_VALUES = Collections.unmodifiableMap(new HashMap<String,String>());

    private MvUtil() {}

    public static Set<String> getMvIndicators(Container c)
    {
        assert c != null : "Attempt to get missing value indicators without a container";
        return getIndicatorsAndLabels(c).keySet();
    }

    /**
     * Allows nulls and ""
     */
    public static boolean isValidMvIndicator(String indicator, Container c)
    {
        if (indicator == null || "".equals(indicator))
            return true;
        return isMvIndicator(indicator, c);
    }

    public static boolean isMvIndicator(String indicator, Container c)
    {
        return getMvIndicators(c).contains(indicator);
    }

    public static String getMvLabel(String mvIndicator, Container c)
    {
        Map<String,String> map = getIndicatorsAndLabels(c);
        String label = map.get(mvIndicator);
        if (label != null)
            return label;
        return "";
    }

    /**
     * Given a container, this returns the container in which the MV indicators are defined.
     * It may be the container itself, or a parent container or project, or the root container.
     */
    public static Container getDefiningContainer(Container c)
    {
        return getIndicatorsAndLabelsWithContainer(c).getKey();
    }

    public static Map<String,String> getIndicatorsAndLabels(Container c)
    {
        return getIndicatorsAndLabelsWithContainer(c).getValue();
    }

    /**
     * Return the Container in which these indicators are defined, along with the indicators.
     */
    public static Pair<Container,Map<String,String>> getIndicatorsAndLabelsWithContainer(Container c)
    {
        String cacheKey = getCacheKey(c);

        //noinspection unchecked
        Map<String, String> result = getCache().get(cacheKey);
        if (result == null)
        {
            result = getFromDb(c);
            if (result.isEmpty())
            {
                result = NO_VALUES;
                getCache().put(cacheKey, NO_VALUES);
            }
            else
            {
                getCache().put(cacheKey, result);
                return new Pair<Container,Map<String,String>>(c, Collections.unmodifiableMap(Collections.unmodifiableMap(result)));
            }
        }
        if (result == NO_VALUES)
        {
            // recurse
            assert !c.isRoot() : "We have no MV indicators for the root container. This should never happen";
            return getIndicatorsAndLabelsWithContainer(c.getParent());
        }

        return new Pair<Container,Map<String,String>>(c, result);
    }

    /**
     * Sets the container given to inherit indicators from its
     * parent container, project, or site, whichever
     * in the hierarchy first has mv indicators.
     */
    public static void inheritMvIndicators(Container c) throws SQLException
    {
        deleteMvIndicators(c);
        clearCache(c);
    }

    private static void deleteMvIndicators(Container c) throws SQLException
    {
        TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();
        String sql = "DELETE FROM " + mvTable + " WHERE container = ?";
        Table.execute(CoreSchema.getInstance().getSchema(), sql, c.getId());
    }

    /**
     * Sets the indicators and labels for this container.
     * Map should be value -> label.
     */
    public static void assignMvIndicators(Container c, String[] indicators, String[] labels) throws SQLException
    {
        assert indicators.length > 0 : "No indicators provided";
        assert indicators.length == labels.length : "Different number of indicators and labels provided";
        deleteMvIndicators(c);
        TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();
        // Need a map to use for each row
        Map<String,String> toInsert = new HashMap<String,String>();
        toInsert.put("container", c.getId());
        for (int i=0; i<indicators.length; i++)
        {
            toInsert.put("mvIndicator", indicators[i]);
            toInsert.put("label", labels[i]);
            Table.insert(null, mvTable, toInsert);
        }
        clearCache(c);
    }

    private static Map<String,String> getFromDb(Container c)
    {
        Map<String,String> indicatorsAndLabels = new CaseInsensitiveHashMap<String>();
        try
        {
            TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();
            Set<String> selectColumns = new HashSet<String>();
            selectColumns.add("mvindicator");
            selectColumns.add("label");
            Filter filter = new SimpleFilter("container", c.getId());
            Map[] selectResults = Table.select(mvTable, selectColumns, filter, null, Map.class);

            //noinspection unchecked
            for (Map<String,String> m : selectResults)
            {
                indicatorsAndLabels.put(m.get("mvindicator"), m.get("label"));
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        return indicatorsAndLabels;
    }

    private static String getCacheKey(Container c)
    {
        return CACHE_PREFIX + c.getId();
    }

    private static StringKeyCache<Map<String, String>> getCache()
    {
        return CacheManager.getSharedCache();
    }

    public static void containerDeleted(Container c)
    {
        clearCache(c);
    }

    public static void clearCache(Container c)
    {
        getCache().removeUsingPrefix(getCacheKey(c));
    }

    /**
     * Returns the default indicators as originally implemented: "Q" and "N",
     * mapped to their labels.
     *
     * This should only be necessary at upgrade time.
     */
    public static Map<String, String> getDefaultMvIndicators()
    {
        Map<String,String> mvMap = new HashMap<String,String>();
        mvMap.put("Q", "Data currently under quality control review.");
        mvMap.put("N", "Required field marked by site as 'data not available'.");

        return mvMap;
    }
}
