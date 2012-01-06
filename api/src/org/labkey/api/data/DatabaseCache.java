/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheType;
import org.labkey.api.cache.Stats;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.cache.TransactionCache;
import org.labkey.api.util.Filter;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: matthewb
 * Date: Dec 12, 2006
 * Time: 9:54:06 AM
 *
 * Not a map, uses a StringKeyCache to implement a thread-safe, transaction-aware cache
 *
 * No synchronization is necessary in this class since the underlying shared cache and transaction cache are both
 * thread-safe, and the transaction cache creation is single-threaded since the Transaction is thread local.  
 *
 * @see org.labkey.api.data.DbScope
 */
public class DatabaseCache<ValueType> implements StringKeyCache<ValueType>
{
    protected final StringKeyCache<ValueType> _sharedCache;
    private final DbScope _scope;

    public DatabaseCache(DbScope scope, int maxSize, long defaultTimeToLive, String debugName)
    {
        _sharedCache = createSharedCache(maxSize, defaultTimeToLive, debugName);
        _scope = scope;
    }

    public DatabaseCache(DbScope scope, int maxSize, String debugName)
    {
        this(scope, maxSize, -1, debugName);
    }

    protected StringKeyCache<ValueType> createSharedCache(int maxSize, long defaultTimeToLive, String debugName)
    {
        return CacheManager.getStringKeyCache(maxSize, defaultTimeToLive, debugName);
    }

    protected StringKeyCache<ValueType> createTemporaryCache()
    {
        return CacheManager.getTemporaryCache(_sharedCache.getLimit(), _sharedCache.getDefaultExpires(), "transaction cache: ", _sharedCache);
    }

    private StringKeyCache<ValueType> getCache()
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            StringKeyCache<ValueType> transactionCache = t.getCache(this);

            if (null == transactionCache)
            {
                transactionCache = new TransactionCache<ValueType>(_sharedCache, createTemporaryCache());
                t.addCache(this, transactionCache);
            }

            return transactionCache;
        }
        else
        {
            return _sharedCache;
        }
    }

    public void put(String key, ValueType value)
    {
        getCache().put(key, value);
    }

    public void put(String key, ValueType value, long timeToLive)
    {
        getCache().put(key, value, timeToLive);
    }

    public ValueType get(String key)
    {
        return getCache().get(key);
    }

    @Override
    public ValueType get(String key, @Nullable Object arg, CacheLoader<String, ValueType> loader)
    {
        return getCache().get(key, arg, loader);
    }

    public void remove(final String key)
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            t.addCommitTask(new Runnable() {
                public void run()
                {
                    DatabaseCache.this.remove(key);
                }
            });
        }

        getCache().remove(key);
    }


    public int removeUsingPrefix(final String prefix)
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            t.addCommitTask(new Runnable() {
                 public void run()
                 {
                     DatabaseCache.this.removeUsingPrefix(prefix);
                 }
            });
        }

        return getCache().removeUsingPrefix(prefix);
    }


    public void clear()
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            t.addCommitTask(new Runnable() {
                public void run()
                {
                    DatabaseCache.this.clear();
                }
            });
        }

        getCache().clear();
    }


    @Override     // TODO: Is this right?
    public int removeUsingFilter(Filter<String> filter)
    {
        return _sharedCache.removeUsingFilter(filter);
    }

    @Override
    public int size()
    {
        return _sharedCache.size();
    }

    @Override
    public long getDefaultExpires()
    {
        return _sharedCache.getDefaultExpires();
    }

    @Override
    public int getLimit()
    {
        return _sharedCache.getLimit();
    }

    @Override
    public CacheType getCacheType()
    {
        return _sharedCache.getCacheType();
    }

    @Override
    public void close()
    {
        _sharedCache.close();
    }


    public static class TestCase extends Assert
    {
        @SuppressWarnings({"StringEquality"})
        @Test
        public void testDbCache() throws Exception
        {
            MyScope scope = new MyScope();

            // Shared cache needs to be a temporary cache, otherwise we'll leak a cache on every invocation because of KNOWN_CACHES
            DatabaseCache<String> cache = new DatabaseCache<String>(scope, 10, "Test Cache") {
                @Override
                protected StringKeyCache<String> createSharedCache(int maxSize, long defaultTimeToLive, String debugName)
                {
                    return CacheManager.getTemporaryCache(maxSize, defaultTimeToLive, debugName, (Stats)null);
                }
            };

            // basic cache testing

            // Hold values so we can test equality below
            String[] values = new String[40];

            for (int i = 0; i < values.length; i++)
                values[i] = "value_" + i;

            for (int i = 1; i <= 20; i++)
            {
                cache.put("key_" + i, values[i]);
                assertTrue(cache.getCache().size() <= 10);
            }

            int correctCount = 0;

            // access in reverse order
            for (int i = 10; i >= 1; i--)
            {
                if (null == cache.get("key_" + i))
                    correctCount++;

                if (cache.get("key_" + (i + 10)) == values[i + 10])
                    correctCount++;
            }

            // A DeterministicLRU cache guarantees that the least recently used element is always kicked out when capacity
            // is reached. A NonDeterministicLRU cache (e.g., an Ehcache implementation) attempts to kick out the least
            // recently used element, but provides no guarantee since it uses sampling for performance reasons. This test
            // is not very useful for a NonDeterministicLRU cache. Adjust the check below if the test fails.
            switch (cache.getCacheType())
            {
                case DeterministicLRU:
                    assertEquals("Count was " + correctCount, correctCount, 20);
                    break;
                case NonDeterministicLRU:
                    assertTrue("Count was " + correctCount, correctCount > 11);
                    break;
                default:
                    fail("Unknown cache type");
            }

            // add 5 more (if deterministic, should kick out 16-20 which are now LRU)
            for (int i = 21; i <= 25; i++)
                cache.put("key_" + i, values[i]);

            assertTrue(cache.getCache().size() == 10);
            correctCount = 0;

            for (int i = 11; i <= 15; i++)
            {
                if (cache.get("key_" + i) == values[i])
                    correctCount++;

                if (cache.get("key_" + (i + 10)) == values[i + 10])
                    correctCount++;
            }

            // As above, this test isn't very useful for a NonDeterministicLRU cache.
            switch (cache.getCacheType())
            {
                case DeterministicLRU:
                    assertEquals("Count was " + correctCount, correctCount, 10);
                    break;
                case NonDeterministicLRU:
                    assertTrue("Count was " + correctCount, correctCount > 4);

                    // Make sure key_11 is in the cache
                    cache.put("key_11", values[11]);
                    assertTrue(cache.get("key_11") == values[11]);
                    break;
                default:
                    fail("Unknown cache type");
            }

            // transaction testing
            scope.beginTransaction();
            assertTrue(scope.isTransactionActive());

            // Test read-through transaction cache
            assertTrue(cache.get("key_11") == values[11]);

            cache.remove("key_11");
            assertTrue(null == cache.get("key_11"));

            // imitate another thread: toggle transaction and test
            scope.setOverrideTransactionActive(Boolean.FALSE);
            assertTrue(cache.get("key_11") == values[11]);
            scope.setOverrideTransactionActive(null);

            // This should close the transaction caches
            scope.commitTransaction();
            // Test that remove got applied to shared cache
            assertTrue(null == cache.get("key_11"));

            cache.removeUsingPrefix("key");
            assert cache.getCache().size() == 0;

            // This should close the (temporary) shared cache
            cache.close();
            scope.closeConnection();
        }


        private static class MyScope extends DbScope
        {
            private Boolean overrideTransactionActive = null;
            private Transaction overrideTransaction = null;
            
            private MyScope()
            {
                super();
            }

            @Override
            protected Connection _getConnection(Logger log) throws SQLException
            {
                return new ConnectionWrapper(null, null, null, log)
                {
                    public void setAutoCommit(boolean autoCommit) throws SQLException
                    {
                    }

                    public void commit() throws SQLException
                    {
                    }

                    public void rollback() throws SQLException
                    {
                    }
                };
            }

            @Override
            public void releaseConnection(Connection conn)
            {
            }


            public boolean isTransactionActive()
            {
                if (null != overrideTransactionActive)
                    return overrideTransactionActive;
                return super.isTransactionActive();
            }

            @Override
            public Transaction getCurrentTransaction()
            {
                if (null != overrideTransactionActive)
                {
                    return overrideTransaction;
                }

                return super.getCurrentTransaction();
            }

            private void setOverrideTransactionActive(@Nullable Boolean override)
            {
                overrideTransactionActive = override;

                if (null == overrideTransactionActive || !overrideTransactionActive)
                {
                    overrideTransaction = null;
                }
                else
                {
                    overrideTransaction = new Transaction(null);
                }
            }
        }
    }
}
