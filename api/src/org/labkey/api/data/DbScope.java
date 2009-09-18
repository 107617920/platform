/*
 * Copyright (c) 2005-2009 LabKey Corporation
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

import org.apache.log4j.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * Class that wraps a datasource and is shared amongst instances of the
 * DbSchema that need to share the datasource.
 * User: migra
 * Date: Nov 16, 2005
 * Time: 10:20:54 AM
 */
public class DbScope
{
    private static final Logger _log = Logger.getLogger(DbScope.class);
    private static final ConnectionMap _initializedConnections = newConnectionMap();
    private static final Map<String, DbScope> _scopes = new LinkedHashMap<String, DbScope>();

    private String _jndiName;
    private DataSource _dataSource;
    private SqlDialect _dialect;
    private String _databaseName;          // Possibly null, e.g., for SAS datasources

    private String _URL;
    private String _databaseProductName;
    private String _databaseProductVersion;
    private String _driverName;
    private String _driverVersion;


    private ThreadLocal<Connection> threadConnection = new ThreadLocal<Connection>();
    private ThreadLocal<LinkedList<Runnable>> _transactedRemovals = new ThreadLocal<LinkedList<Runnable>>()
    {
        protected LinkedList<Runnable> initialValue()
        {
            return new LinkedList<Runnable>();
        }
    };


    protected DbScope()
    {
    }


    DbScope(String jndiName) throws NamingException, ServletException, SQLException
    {
        this(jndiName, getDataSourceFromJNDI(jndiName));
    }


    public DbScope(String jndiName, DataSource dataSource) throws ServletException, SQLException
    {
        _jndiName = jndiName;
        _dataSource = dataSource;
        _dialect = SqlDialect.get(_dataSource);
        _databaseName = _dialect.getDatabaseName(_dataSource);

        Connection conn = null;

        try
        {
            conn = _dataSource.getConnection();
            DatabaseMetaData dbmd = conn.getMetaData();

            _log.info("Initializing DbScope with the following configuration:" +
                    "\n    Server URL:               " + dbmd.getURL() +
                    "\n    Database Product Name:    " + dbmd.getDatabaseProductName() +
                    "\n    Database Product Version: " + dbmd.getDatabaseProductVersion() +
                    "\n    JDBC Driver Name:         " + dbmd.getDriverName() +
                    "\n    JDBC Driver Version:      " + dbmd.getDriverVersion());

            _URL = dbmd.getURL();
            _databaseProductName = dbmd.getDatabaseProductName();
            _databaseProductVersion = dbmd.getDatabaseProductVersion();
            _driverName = dbmd.getDriverName();
            _driverVersion = dbmd.getDriverVersion();
        }
        finally
        {
            if (null != conn)
            {
                try
                {
                    conn.close();
                }
                catch (SQLException e)
                {
                    // ignore
                }
            }
        }
    }


    private static DataSource getDataSourceFromJNDI(String dsName) throws NamingException
    {
        InitialContext ctx = new InitialContext();
        Context envCtx = (Context) ctx.lookup("java:comp/env");

        return (DataSource) envCtx.lookup(dsName);
    }

    public String getJndiName()
    {
        return _jndiName;
    }

    public DataSource getDataSource()
    {
        return _dataSource;
    }

    public String getDatabaseName()
    {
        return _databaseName;
    }

    public String getURL()
    {
        return _URL;
    }

    public String getDatabaseProductName()
    {
        return _databaseProductName;
    }

    public String getDatabaseProductVersion()
    {
        return _databaseProductVersion;
    }

    public String getDriverName()
    {
        return _driverName;
    }

    public String getDriverVersion()
    {
        return _driverVersion;
    }

    public Connection beginTransaction() throws SQLException
    {
        if (null != threadConnection.get())
            throw new IllegalStateException("Existing transaction");

        Connection conn = _getConnection(null);
        conn.setAutoCommit(false);
        threadConnection.set(conn);
        return conn;
    }


    public void commitTransaction() throws SQLException
    {
        Connection conn = threadConnection.get();
        assert null != conn;
        conn.commit();
        conn.setAutoCommit(true);
        conn.close();
        threadConnection.remove();

        LinkedList<Runnable> removes = _transactedRemovals.get();
        while (!removes.isEmpty())
            removes.removeFirst().run();
    }


    public void rollbackTransaction()
    {
        Connection conn = threadConnection.get();
        assert null != conn;
        try
        {
            conn.rollback();
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
        try
        {
            conn.setAutoCommit(true);
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
        try
        {
            conn.close();
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
        threadConnection.remove();
        _transactedRemovals.get().clear();
    }


    public boolean isTransactionActive()
    {
        return threadConnection.get() != null;
    }

    public Connection getConnection() throws SQLException
    {
        return getConnection(null);
    }

    public Connection getConnection(Logger log) throws SQLException
    {
        Connection conn = threadConnection.get();
        if (null == conn)
            conn = _getConnection(log);
        return conn;
    }


    static Class classDelegatingConnection = null;
    static Method methodGetInnermostDelegate = null;
    static boolean isDelegating = false;
    static boolean isDelegationInitialized;
    private static final Object delegationLock = new Object();


    private static void ensureDelegation(Connection conn)
    {
        synchronized (delegationLock)
        {
            if (isDelegationInitialized)
                return;

            try
            {
                classDelegatingConnection = conn.getClass();
                methodGetInnermostDelegate = classDelegatingConnection.getMethod("getInnermostDelegate");

                while (true)
                {
                    try
                    {
                        // Test the method to make sure we can access it
                        Connection test = (Connection)methodGetInnermostDelegate.invoke(conn);
                        isDelegating = true;
                        return;
                    }
                    catch (Exception e)
                    {
                        // Probably an IllegalAccessViolation -- ignore
                    }

                    // Try the superclass
                    classDelegatingConnection = classDelegatingConnection.getSuperclass();
                    methodGetInnermostDelegate = classDelegatingConnection.getMethod("getInnermostDelegate");
                }
            }
            catch (Exception x)
            {
                _log.info("Could not find class DelegatingConnection", x);
            }
            finally
            {
                isDelegationInitialized = true;
            }
        }
    }


    private static Connection getDelegate(Connection conn)
    {
        Connection delegate = null;

        ensureDelegation(conn);

        if (isDelegating && classDelegatingConnection.isAssignableFrom(conn.getClass()))
        {
            try
            {
                delegate = (Connection)methodGetInnermostDelegate.invoke(conn);
            }
            catch (Exception x)
            {
                _log.error("Unexpected error", x);
            }
        }
        if (null == delegate)
            delegate = conn;
        return delegate;
    }

    Integer spidUnknown = -1;

    protected Connection _getConnection(Logger log) throws SQLException
    {
        Connection conn = _dataSource.getConnection();

        //
        // Handle one time per-connection setup
        // relies on pool implementation reusing same connection/wrapper instances
        //

        Connection delegate = getDelegate(conn);
        Integer spid = _initializedConnections.get(delegate);

        if (null == spid)
        {
            if (null != _dialect)
            {
                _dialect.initializeConnection(conn);
                spid = _dialect.getSPID(delegate);
            }

            _initializedConnections.put(delegate, spid == null ? spidUnknown : spid);
        }

        return new ConnectionWrapper(conn, _dialect, spid, log);
    }


    public void releaseConnection(Connection conn) throws SQLException
    {
        Connection threadConn = threadConnection.get();
        assert null == threadConn || conn == threadConn; //Should release same conn we handed out

        if (null == threadConn)
            conn.close();
    }


    public void closeConnection()
    {
        Connection conn = threadConnection.get();
        if (conn != null)
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                _log.error("Failed to close connection", e);
            }
            threadConnection.remove();
            _transactedRemovals.remove();
        }
    }


    public SqlDialect getSqlDialect()
    {
        return _dialect;
    }


    public void addCommitTask(Runnable task)
    {
        if (!isTransactionActive())
        {
            throw new IllegalStateException("Must be inside a transaction");
        }
        _transactedRemovals.get().add(task);
    }
    

    void addTransactedRemoval(final DatabaseCache cache, final String name, final boolean isPrefix)
    {
        addCommitTask(new Runnable() {
            public void run()
            {
                if (isPrefix)
                    cache.removeUsingPrefix(name);
                else
                    cache.remove(name);
            }
        });
    }

    void addTransactedRemoval(final DatabaseCache cache)
    {
        addCommitTask(new Runnable() {
            public void run()
            {
                cache.clear();
            }
        });
    }


    void addTransactedClear(final TableInfo tinfo)
    {
        addCommitTask(new Runnable() {
            public void run()
            {
                DbCache.clear(tinfo);
            }
        });
    }


    void addTransactedInvalidate(final TableInfo tinfo)
    {
        addCommitTask(new Runnable()
        {
            public void run()
            {
                DbCache.invalidateAll(tinfo);
            }
        });
    }

    // TODO: Make this package and pass datasource name into DbSchema.createFromMetaData()?
    public static DbScope getDbScope(String dsName) throws NamingException, ServletException, SQLException
    {
        DbScope scope;

        synchronized (_scopes)
        {
            scope = _scopes.get(dsName);
            if (null == scope)
            {
                scope = new DbScope(dsName);
                _scopes.put(dsName, scope);
            }
        }

        return scope;
    }

    public static Collection<DbScope> getDbScopes()
    {
        synchronized (_scopes)
        {
            return new ArrayList<DbScope>(_scopes.values());
        }
    }

    public static void rollbackAllTransactions()
    {
        synchronized (_scopes)
        {
            for (DbScope scope : _scopes.values())
            {
                if (scope.isTransactionActive())
                {
                    try
                    {
                        scope.rollbackTransaction();
                    }
                    catch (Exception x)
                    {
                        _log.error("Rollback All Transactions", x);
                    }
                }
            }
        }
    }


    interface ConnectionMap
    {
        Integer get(Connection c);
        Integer put(Connection c, Integer spid);
    }


    static ConnectionMap newConnectionMap()
    {
//        final HashMap<Connection,Integer> m = new HashMap<Connection,Integer>();
//        final HashMap<Connection,Integer> m = new WeakHashMap<Connection,Integer>();
        final _WeakestLinkMap<Connection,Integer> m = new _WeakestLinkMap<Connection,Integer>();
        return new ConnectionMap() {
            public synchronized Integer get(Connection c)
            {
                return m.get(c);
            }

            public synchronized Integer put(Connection c, Integer spid)
            {
                return m.put(c,spid);
            }
        };
    }


    /** weak identity hash map, could just subclass WeakHashMap but eq() is static (and not overridable) */
    private static class _WeakestLinkMap<K, V>
    {
        int _max = 1000;
        final ReferenceQueue<K> _q = new ReferenceQueue<K>();

        LinkedHashMap<_IdentityWrapper, V> _map = new LinkedHashMap<_IdentityWrapper, V>()
        {
            protected boolean removeEldestEntry(Map.Entry<_IdentityWrapper, V> eldest)
            {
                _purge();
                return size() > _max;
            }
        };

        private class _IdentityWrapper extends WeakReference<K>
        {
            int _hash;

            _IdentityWrapper(K o)
            {
                super(o, _q);
                _hash = System.identityHashCode(get());
            }

            public int hashCode()
            {
                return _hash;
            }

            public boolean equals(Object obj)
            {
                return obj instanceof Reference && get() == ((Reference)obj).get();
            }
        }

        _WeakestLinkMap()
        {
        }

        _WeakestLinkMap(int max)
        {
            _max = max;
        }

        public V put(K key, V value)
        {
            return _map.put(new _IdentityWrapper(key), value);
        }

        public V get(K key)
        {
            return _map.get(new _IdentityWrapper(key));
        }

        private void _purge()
        {
            _IdentityWrapper w;
            while (null != (w = (_IdentityWrapper)_q.poll()))
            {
                _map.remove(w);
            }
        }
    }

    public static void test()
    {
        _WeakestLinkMap<String,Integer> m = new _WeakestLinkMap<String,Integer>(10);
        //noinspection MismatchedQueryAndUpdateOfCollection
        Set<String> save = new HashSet<String>();
        
        for (int i=0 ; i<100000 ; i++)
        {
            if (i%1000 == 0) System.gc();
            String s = "" + i;
            if (i % 3 == 0)
                save.add(s);
            m.put(s,i);
        }
    }
}
