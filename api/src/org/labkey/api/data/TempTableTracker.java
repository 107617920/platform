/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.ShutdownListener;

import javax.servlet.ServletContextEvent;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: Matthew
 * Date: May 4, 2006
 * Time: 7:27:50 PM
 */
public class TempTableTracker extends WeakReference<Object>
{
    static Logger _log = Logger.getLogger(TempTableTracker.class);
    static final String LOGFILE = "CPAS_sqlTempTables.log";
    static final Map<String, TempTableTracker> createdTableNames = new TreeMap<>();
    static final ReferenceQueue<Object> cleanupQueue = new ReferenceQueue<>();
    static RandomAccessFile tempTableLog = null;

    DbSchema schema;
    String schemaName;
    String tableName;
    boolean deleted = false;


    private TempTableTracker(DbSchema schema, String name, Object ref)
    {
        this(schema.getName(), name, ref);
        this.schema = schema;
    }


    private TempTableTracker(String schema, String name, Object ref)
    {
        super(ref, cleanupQueue);
        this.schemaName = schema;
        this.tableName = name;
    } 

    static final Object initlock = new Object();
    static boolean initialized=false;

    // make sure temp table tracker is initialized
    public static void init()
    {
        synchronized(initlock)
        {
            if (!initialized)
            {
                initialized = true;
                synchronizeLog(true);
                purgeTempSchema();
                tempTableThread.setDaemon(true);
                tempTableThread.start();
            }
        }
    }


    public static TempTableTracker track(DbSchema schema, String name, Object ref)
    {
        init();
        return track(new TempTableTracker(schema, name, ref));
    }


    private static TempTableTracker track(String schema, String name, Object ref)
    {
        return track(new TempTableTracker(schema, name, ref));
    }


    private static TempTableTracker track(TempTableTracker ttt)
    {
        _log.debug("track(" + ttt.tableName + ")");

        synchronized(createdTableNames)
        {
            TempTableTracker old = createdTableNames.get(ttt.tableName);
            if (old != null)
                return old;
            createdTableNames.put(ttt.tableName, ttt);
            appendToLog("+" + ttt.schemaName + "\t" + ttt.tableName + "\n");
            return ttt;
        }
    }


    private DbSchema getSchema()
    {
        if (null == schema)
            schema = DbSchema.get(schemaName);
        return schema;
    }


    public synchronized void delete()
    {
        if (deleted)
            return;

        sqlDelete();

        deleted = true;
        untrack();
    }


    private boolean sqlDelete()
    {
        DbSchema schema = getSchema();
        Connection conn = null;
        Statement stmt = null;
        boolean success = false;
        try
        {
            // if we use Table.execute() it will log errors (HMMM, need to fix that...)
            //Table.execute(schema, "DROP TABLE " + tableName, null);
            conn = schema.getScope().getConnection();
            assert MemTracker.remove(conn);         // we're a bg thread, so we can cause memTracker.view to fail
            stmt = conn.createStatement();
            assert MemTracker.remove(stmt);
            stmt.execute("DROP TABLE " + tableName);
            success = true;
        }
        catch (SQLException x)
        {
            // 42P01    postgres: UNDEFINED TABLE
            // 42S02    sqlserver: Base table or view not found
            if (x.getSQLState().startsWith("42"))
                success = true;
            else
                _log.warn("Error deleting temp table '" + tableName + "'", x);
        }
        finally
        {
            if (null != stmt)
            {
                try { stmt.close(); } catch (Exception x) {_log.error("unexpected error", x);}
            }
            if (null != conn)
            {
                try { schema.getScope().releaseConnection(conn); } catch (Exception x) {_log.error("unexpected error", x);}
            }
        }
        return success;
    }


    @Override
    protected void finalize() throws Throwable
    {
        if (!deleted)
            _log.error("finalizing undeleted TempTableTracker: " + tableName);
        super.finalize();
    }


    private void untrack()
    {
        _log.debug("untrack(" + tableName + ")");

        synchronized(createdTableNames)
        {
            createdTableNames.remove(tableName);
            appendToLog("-" + schemaName + "\t" + tableName + "\n");

            if (createdTableNames.isEmpty() || System.currentTimeMillis() > lastSync + CacheManager.DAY)
                synchronizeLog(false);
        }
    }


    private static void appendToLog(String str)
    {
        if (null != tempTableLog)
        {
            try
            {
                tempTableLog.writeUTF(str);
            }
            catch (IOException x)
            {
             _log.error("could not write to " + LOGFILE, x);
            }
        }
    }


    static long lastSync = System.currentTimeMillis();


    private static void purgeTempSchema()
    {
        // consider: test to see if any of the schemas have different scope (dbName) than core schema
        synchronized(createdTableNames)
        {
            SqlDialect dialect = CoreSchema.getInstance().getSchema().getSqlDialect();
            dialect.purgeTempSchema(createdTableNames);
        }
    }


    static void synchronizeLog(boolean loadFile)
    {
        synchronized(createdTableNames)
        {
            try
            {
                if (null == tempTableLog)
                    tempTableLog = new RandomAccessFile(new File(FileUtil.getTempDirectory(), LOGFILE), "rwd");

                if (loadFile)
                {
                    TreeSet<String> logEntries = new TreeSet<>();

                    try
                    {
                        tempTableLog.seek(0);
                        while (tempTableLog.getFilePointer() < tempTableLog.length())
                        {
                            String s = tempTableLog.readUTF().trim();
                            if (s.charAt(0) == '+')
                                logEntries.add(s.substring(1));
                            else if (s.charAt(0) == '-')
                                logEntries.remove(s.substring(1));
                        }
                    }
                    catch (Exception x)
                    {
                        _log.error("error reading file '" + LOGFILE + "'", x);
                    }

                    Object noref = new Object();
                    for (String s : logEntries)
                    {
                        try
                        {
                            String[] parts = s.split("\t");
                            if (parts.length != 2)
                                continue;
                            String schemaName = parts[0].trim();
                            String tableName = parts[1].trim();
                            if (schemaName.length() == 0 || tableName.length() == 0)
                                continue;
                            track(schemaName, tableName, noref);
                        }
                        catch (IllegalArgumentException x)
                        {
                            _log.error("bad log entry", x);
                        }
                    }
                }

                //
                // Rewrite file
                //
                // we could create new file and rename, but this doesn't have to be that robust
                //
                tempTableLog.seek(0);
                tempTableLog.setLength(0);
                for (TempTableTracker ttt : createdTableNames.values())
                {
                    if (!ttt.deleted)
                        appendToLog("+" + ttt.tableName + "\n");
                }
            }
            catch (IOException x)
            {
                _log.error("could not create " + LOGFILE, x);
            }
            finally
            {
                lastSync = System.currentTimeMillis();
            }
        }
    }


    static final TempTableThread tempTableThread = new TempTableThread();

    static class TempTableThread extends Thread  implements ShutdownListener
    {
        AtomicBoolean _shutdown = new AtomicBoolean(false);
        
        TempTableThread()
        {
            super("Temp table cleanup");
            setDaemon(true);
        }

        public void run()
        {
            while (true)
            {
                try
                {
                    Reference<?> r = _shutdown.get() ? cleanupQueue.poll() : cleanupQueue.remove();
                    if (_shutdown.get() && r == null)
                        return;
                    //noinspection RedundantCast
                    TempTableTracker t = (TempTableTracker)(Object)r;
                    t.delete();
                }
                catch (InterruptedException x)
                {
                    _log.debug("interrupted");
                }
                catch (Throwable x)
                {
                    _log.error("unexpected error", x);
                }
            }
        }


        public void shutdownPre(ServletContextEvent servletContextEvent)
        {
            _shutdown.set(true);
            interrupt();
        }

        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
            synchronized(createdTableNames)
            {
                for (TempTableTracker ttt : createdTableNames.values())
                {
                    ttt.sqlDelete();
                    ttt.deleted = true;
                }
            }

            try
            {
                join(5000);
            }
            catch (InterruptedException e) {}
            synchronizeLog(false);
        }
    }


    public static ShutdownListener getShutdownListener()
    {
        return tempTableThread;
    }


    public static Logger getLogger()
    {
        return _log;
    }    
}
