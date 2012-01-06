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
package org.labkey.api.cache;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.ehcache.EhCacheProvider;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 10:05:07 AM
 */

/*
    TODO:

    - Track expirations

 */
public class CacheManager
{
    private static final Logger LOG = Logger.getLogger(CacheManager.class);

    public static final long SECOND = DateUtils.MILLIS_PER_SECOND;
    public static final long MINUTE = DateUtils.MILLIS_PER_MINUTE;
    public static final long HOUR = DateUtils.MILLIS_PER_HOUR;
    public static final long DAY = DateUtils.MILLIS_PER_DAY;
    public static final long YEAR = DAY * 365;

    public static final long DEFAULT_TIMEOUT = HOUR;

    // Swap providers here (if we ever implement another cache provider)
    private static final CacheProvider PROVIDER = EhCacheProvider.getInstance();
    private static final List<TrackingCache> KNOWN_CACHES = new LinkedList<TrackingCache>();
    public static final int UNLIMITED = 0;

    private static <K, V> TrackingCache<K, V> createCache(int limit, long defaultTimeToLive, String debugName)
    {
        TrackingCache<K, V> cache = new CacheWrapper<K, V>(PROVIDER.<K, V>getSimpleCache(debugName, limit, defaultTimeToLive, false), debugName, null);
        addToKnownCaches(cache);  // Permanent cache -- hold onto it
        return cache;
    }

    public static <K, V> TrackingCache<K, V> getCache(int limit, long defaultTimeToLive, String debugName)
    {
        return createCache(limit, defaultTimeToLive, debugName);
    }

    public static <V> StringKeyCache<V> getStringKeyCache(int limit, long defaultTimeToLive, String debugName)
    {
        return new StringKeyCacheWrapper<V>(CacheManager.<String, V>createCache(limit, defaultTimeToLive, debugName));
    }

    public static <K, V> BlockingCache<K, V> getBlockingCache(int limit, long defaultTimeToLive, String debugName, @Nullable CacheLoader<K, V> loader)
    {
        TrackingCache<K, Object> cache = getCache(limit, defaultTimeToLive, debugName);
        return new BlockingCache<K, V>(cache, loader);
    }

    public static <V> BlockingStringKeyCache<V> getBlockingStringKeyCache(int limit, long defaultTimeToLive, String debugName, @Nullable CacheLoader<String, V> loader)
    {
        StringKeyCache<Object> cache = getStringKeyCache(limit, defaultTimeToLive, debugName);
        return new BlockingStringKeyCache<V>(cache, loader);
    }

    // Temporary caches must be closed when no longer needed.  Their statistics can accumulate to another cache's stats.
    public static <V> StringKeyCache<V> getTemporaryCache(int limit, long defaultTimeToLive, String debugName, @Nullable Stats stats)
    {
        TrackingCache<String, V> cache = new CacheWrapper<String, V>(PROVIDER.<String, V>getSimpleCache(debugName, limit, defaultTimeToLive, true), debugName, stats);
        return new StringKeyCacheWrapper<V>(cache);
    }

    // Temporary caches must be closed when no longer needed.  Their statistics can accumulate to another cache's stats.
    public static <V> StringKeyCache<V> getTemporaryCache(int limit, long defaultTimeToLive, String prefix, StringKeyCache<V> sharedCache)
    {
        Tracking tracking = (Tracking)sharedCache;
        return getTemporaryCache(limit, defaultTimeToLive, prefix + tracking.getDebugName(), tracking.getStats());
    }

    private static final StringKeyCache<Object> SHARED_CACHE = getStringKeyCache(10000, DEFAULT_TIMEOUT, "sharedCache");

    public static <V> StringKeyCache<V> getSharedCache()
    {
        return (StringKeyCache<V>)SHARED_CACHE;
    }

    // We hold onto "permanent" caches so memtracker can clear them and admin console can report statistics on them
    private static void addToKnownCaches(TrackingCache cache)
    {
        synchronized (KNOWN_CACHES)
        {
            KNOWN_CACHES.add(cache);

            LOG.debug("Known caches: " + KNOWN_CACHES.size());
        }
    }

    public static void clearAllKnownCaches()
    {
        synchronized (KNOWN_CACHES)
        {
            for (TrackingCache cache : KNOWN_CACHES)
                cache.clear();
        }
    }

    // Return a copy of KNOWN_CACHES for reporting statistics
    public static List<TrackingCache> getKnownCaches()
    {
        List<TrackingCache> copy = new ArrayList<TrackingCache>();

        synchronized (KNOWN_CACHES)
        {
            for (TrackingCache cache : KNOWN_CACHES)
                copy.add(cache);
        }

        return copy;
    }

    public static CacheStats getCacheStats(TrackingCache cache)
    {
        return new CacheStats(cache.getDebugName(), cache.getCreationStackTrace(), cache.getStats(), cache.size(), cache.getLimit());
    }

    public static CacheStats getTransactionCacheStats(TrackingCache cache)
    {
        return new CacheStats(cache.getDebugName(), cache.getCreationStackTrace(), cache.getTransactionStats(), cache.size(), cache.getLimit());
    }
}
