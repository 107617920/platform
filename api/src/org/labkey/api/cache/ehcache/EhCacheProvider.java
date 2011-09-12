/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.cache.ehcache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.labkey.api.cache.BasicCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheProvider;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Filter;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.ShutdownListener;

import javax.servlet.ServletContextEvent;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 1:46:40 PM
 */
// Do not use CacheProvider implementations directly; use CacheManager.getCache() to get a cache
public class EhCacheProvider implements CacheProvider, ShutdownListener
{
    private static final Logger LOG = Logger.getLogger(EhCacheProvider.class);
    private static final EhCacheProvider INSTANCE = new EhCacheProvider();
    private static final CacheManager MANAGER;
    private static final AtomicLong cacheCount = new AtomicLong(0);
    private static final Object _nullMarker = BasicCache.NULL_MARKER;
    
    static
    {
        InputStream is = null;

        try
        {
            is = EhCacheProvider.class.getResourceAsStream("ehcache.xml");
            MANAGER = new CacheManager(is);
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }

        ContextListener.addShutdownListener(INSTANCE);
    }

    public static CacheProvider getInstance()
    {
        return INSTANCE;
    }

    private EhCacheProvider()
    {
    }

    @Override
    public void shutdownPre(ServletContextEvent servletContextEvent)
    {
    }

    @Override
    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        LOG.info("Shutting down Ehcache");
        MANAGER.shutdown();
    }

    @Override
    public <K, V> BasicCache<K, V> getBasicCache(String debugName, int limit, long defaultTimeToLive, boolean temporary)
    {
        // Every Ehcache requires a unique name.  We create many temporary caches with overlapping names, so append a unique counter.
        // Consider: a cache pool for temporary caches?
        CacheConfiguration config = new CacheConfiguration(debugName + "_" + cacheCount.incrementAndGet(), limit);
        config.setTimeToLiveSeconds(defaultTimeToLive / 1000);
        Cache ehCache = new Cache(config);
        MANAGER.addCache(ehCache);

        if (LOG.isDebugEnabled())
        {
            String[] names = MANAGER.getCacheNames();
            StringBuilder sb = new StringBuilder("Caches managed by Ehcache: " + names.length);

            for (String name : names)
            {
                sb.append("\n  ");
                sb.append(name);
            }

            LOG.debug(sb);
        }

        // Memtrack temporary caches to ensure they're destroyed
        if (temporary)
            assert MemTracker.put(ehCache);

        return new EhBasicCache<K, V>(ehCache);
    }

    private static class EhBasicCache<K, V> implements BasicCache<K, V>
    {
        private final Cache _cache;

        private EhBasicCache(Cache cache)
        {
            _cache = cache;
        }

        @Override
        public void put(K key, V value)
        {
            Element element = new Element(key, value);
            _cache.put(element);
        }

        @Override
        public void put(K key, V value, long timeToLive)
        {
            Element element = new Element(key, value);
            element.setTimeToLive((int)timeToLive / 1000);
            _cache.put(element);
        }

        @Override
        public V get(K key)
        {
            Element e = _cache.get(key);
            Object v = null==e ? null : e.getObjectValue();
            return v==_nullMarker ? null : (V)v;
        }

        @Override
        public V get(K key, Object arg, CacheLoader<K, V> loader)
        {
            Element e = _cache.get(key);
            Object v;
            if (null != e)
            {
                v = e.getObjectValue();
                return v==_nullMarker ? null : (V)v;
            }
            v = loader.load(key, arg);
            _cache.put(new Element(key, v==null?_nullMarker:v));
            return v==_nullMarker ? null : (V)v;
        }

        @Override
        public void remove(K key)
        {
            _cache.remove(key);
        }

        @Override
        public int removeUsingFilter(Filter<K> filter)
        {
            int removes = 0;
            List<K> keys = _cache.getKeys();

            for (K key : keys)
            {
                if (filter.accept(key))
                {
                    remove(key);
                    removes++;
                }
            }

            return removes;
        }

        @Override
        public void clear()
        {
            _cache.removeAll();
        }

        @Override
        public int getLimit()
        {
            return _cache.getCacheConfiguration().getMaxElementsInMemory();
        }

        @Override
        public int size()
        {
            return _cache.getSize();
        }

        @Override
        public long getDefaultExpires()
        {
            return _cache.getCacheConfiguration().getTimeToLiveSeconds() * 1000;
        }

        @Override
        public CacheType getCacheType()
        {
            return CacheType.NonDeterministicLRU;
        }

        @Override
        public void close()
        {
            MANAGER.removeCache(_cache.getName());

            LOG.debug("Closing \"" + _cache.getName() + "\".  Ehcaches: " + MANAGER.getCacheNames().length);
        }
    }
}
