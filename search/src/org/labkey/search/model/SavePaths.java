/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.search.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: matthewb
 * Date: Dec 12, 2009
 * Time: 9:38:46 AM
 */
public class SavePaths implements DavCrawler.SavePaths
{
    final private long _startupTime = System.currentTimeMillis();
    

    SQLFragment pathFilter(TableInfo ti, String path)
    {
        SQLFragment f = new SQLFragment(" Path=? ", path);
        if (null != ti.getColumn("csPath"))
        {
            f.append(" AND csPath=CHECKSUM(?)");
            f.add(path);
        }
        return f;
    }


    //
    // FOLDER/RESOURCES
    //

    private boolean _update(Path path, @Nullable java.util.Date last, @Nullable java.util.Date next) throws SQLException
    {
        String pathStr = toPathString(path);
        if (null == last) last = nullDate;
        if (null == next) next = oldDate;

        DbSchema search = getSearchSchema();
        SqlDialect d = search.getSqlDialect();

        SQLFragment upd = new SQLFragment(
                String.format("UPDATE search.CrawlCollections %s SET LastCrawled=?, NextCrawl=? ",
                        d.isSqlServer() ? "WITH (UPDLOCK)" : ""),
                last, next);
        upd.append(" WHERE " );
        SQLFragment f = pathFilter(getSearchSchema().getTable("CrawlCollections"), pathStr);
        upd.append(f);

        int count = new SqlExecutor(getSearchSchema()).execute(upd);
        return count > 0;
    }


    private static String toPathString(Path path)
    {
        return path.toString("/", "/");
    }


    // -1 if not exists
    private int getId(Path path) throws SQLException
    {
        // find parent
        String pathStr = toPathString(path);
        SQLFragment find = new SQLFragment("SELECT id FROM search.CrawlCollections WHERE ");
        find.append(pathFilter(getSearchSchema().getTable("CrawlCollections"), pathStr));
        Integer id = new SqlSelector(getSearchSchema(), find).getObject(Integer.class);

        return null != id ? id : -1;
    }


    // create if not exists
    private int _getParentId(Path path) throws SQLException
    {
        Path parent = path.getParent();
        if (null == parent)
            return 0;

        int id = getId(path.getParent());
        if (id != -1)
            return id;

        // insert parent
        return _ensure(parent);
    }


    private int _ensure(Path path) throws SQLException
    {
        // Mostly I don't care about Parent
        // However, we need this for the primary key
        int parent = _getParentId(path);

        Map<String, Object> map = new CaseInsensitiveHashMap<>();
        map.put("Path", toPathString(path));
        map.put("Name", path.equals(Path.rootPath) ? "/" : path.getName());   // "" is treated like NULL
        map.put("Parent", parent);
        map.put("NextCrawl", new Date());
        map.put("LastCrawled", nullDate);
        try
        {
            // UNDONE : this has side-effect of setting Modified field (which we're not using anyway)
            map = Table.insert(User.getSearchUser(), getSearchSchema().getTable("CrawlCollections"), map);
            return ((Integer)map.get("id"));
        }
        catch (SQLException x)
        {
            if (SqlDialect.isConstraintException(x))
            {
                return getId(path);
            }
            else
            {
                throw x;
            }
        }
    }


    public boolean insertPath(Path path, Date nextCrawl)
    {
        try
        {
            // Mostly I don't care about Parent
            // However, we need this for the primary key
            int parent = _getParentId(path);
            if (nextCrawl == null)
                nextCrawl = new Date(System.currentTimeMillis()+5*60000);

            String pathStr = toPathString(path);
            SQLFragment f = new SQLFragment(
                    "INSERT INTO search.crawlcollections (Path,Name,Parent,NextCrawl,LastCrawled) " +
                    "SELECT ?,?,?,?,? " +
                    "WHERE NOT EXISTS (SELECT Path FROM search.crawlcollections WHERE ");
            f.add(pathStr);
            f.add(path.equals(Path.rootPath) ? "/" : path.getName());   // "" is treated like NULL
            f.add(parent);
            f.add(nextCrawl);
            f.add(nullDate);
            f.append(pathFilter(getSearchSchema().getTable("CrawlCollections"), pathStr));
            f.append(")");
            int count = new SqlExecutor(getSearchSchema()).execute(f);
            return count==1;
        }
        catch (SQLException x)
        {
            if (SqlDialect.isConstraintException(x))
            {
                return false;
            }
            else
            {
                throw new RuntimeSQLException(x);
            }
        }
    }


    public boolean updatePath(Path path, java.util.Date last, java.util.Date next, boolean create)
    {
        try
        {
            boolean success = _update(path,last,next);
            if (!success && create)
            {
                _ensure(path);
                success = _update(path,last,next);
            }
            return success;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }
    

    public void updatePrefix(Path path, Date next, boolean forceIndex)
    {
        if (next == null)
            next = oldDate;

        SqlExecutor executor = new SqlExecutor(getSearchSchema());

        if (forceIndex)
        {
            executor.execute(
                    "UPDATE search.CrawlResources SET LastIndexed=NULL " +
                    "WHERE Parent IN (SELECT id FROM search.CrawlCollections " +
                    "  WHERE Path LIKE ?)",
                    toPathString(path) + "%");
        }

        executor.execute(
                "UPDATE search.CrawlCollections " +
                "SET LastCrawled=NULL, NextCrawl=? " +
                "WHERE Path LIKE ?",
                // UNDONE LIKE ESCAPE
                next, toPathString(path) + "%");
    }

    
    public void clearFailedDocuments()
    {
        assert failDate.getTime() < oldDate.getTime();
        new SqlExecutor(getSearchSchema()).execute(
                "UPDATE search.CrawlResources SET LastIndexed=NULL WHERE LastIndexed<?",
                oldDate);
    }


    public void deletePath(Path path)
    {
        // UNDONE LIKE ESCAPE
        new SqlExecutor(getSearchSchema()).execute("DELETE FROM search.CrawlResources WHERE Parent IN (SELECT id FROM search.CrawlCollections WHERE Path LIKE ?)", toPathString(path) + "%");
        new SqlExecutor(getSearchSchema()).execute("DELETE FROM search.CrawlCollections WHERE Path LIKE ?", toPathString(path) + "%");
    }


    public Date getNextCrawl()
    {
        SQLFragment f = new SQLFragment("SELECT MIN(NextCrawl) FROM search.CrawlCollections WHERE LastCrawled IS NULL OR LastCrawled < ?");
        f.add(System.currentTimeMillis() - 30*60000);
        return new SqlSelector(getSearchSchema(), f).getObject(Timestamp.class);
    }


    public Map<Path, Pair<Date,Date>> getPaths(int limit)
    {
        Date now = new Date(System.currentTimeMillis());
        Date awhileago = new Date(Math.max(_startupTime, now.getTime() - 30*60000));

        SqlDialect dialect = getSearchSchema().getSqlDialect();
        SQLFragment f = new SQLFragment(
                "SELECT Path, LastCrawled, NextCrawl\n" +
                "FROM search.CrawlCollections\n");
        f.append("WHERE NextCrawl < ? AND (LastCrawled IS NULL OR LastCrawled < ?) " +
                "ORDER BY NextCrawl");
        f.add(now);
        f.add(awhileago);
        SQLFragment sel = dialect.limitRows(f, limit);

        try
        {
            Map<Path,Pair<Date,Date>> map = new LinkedHashMap<>();
            ArrayList<String> paths = new ArrayList<>(limit);

            try (ResultSet rs = new SqlSelector(getSearchSchema(), sel).getResultSet())
            {
                while (rs.next())
                {
                    String path = rs.getString(1);
                    java.sql.Timestamp lastCrawl = rs.getTimestamp(2);
                    java.sql.Timestamp nextCrawl = rs.getTimestamp(3);
                    map.put(Path.parse(path), new Pair<Date, Date>(lastCrawl, nextCrawl));
                    paths.add(path);
                }
            }

            // UPDATE LastCrawled so we won't try to crawl for a while
            if (!paths.isEmpty())
            {
                SQLFragment upd = new SQLFragment(
                        "UPDATE search.CrawlCollections " + (getSearchSchema().getSqlDialect().isSqlServer() ? " WITH (UPDLOCK)" : "") + "\n" +
                        "SET LastCrawled=? WHERE Path IN ",
                        now);
                String comma = "(";
                for (String path : paths)
                {
                    upd.append(comma).append('?');
                    upd.add(path);
                    comma = ",";
                }
                upd.append(")");
                new SqlExecutor(getSearchSchema()).execute(upd);
            }
            return map;
        }
        catch (SQLException x)
        {
            if (!SqlDialect.isTransactionException(x))
                throw new RuntimeSQLException(x);
            return Collections.emptyMap();
        }
    }


    //
    // FILES/RESOURCES
    //

    public Map<String, DavCrawler.ResourceInfo> getFiles(Path path)
    {
        SQLFragment s = new SQLFragment(
                "SELECT D.ChangeInterval, D.Path, D.id, F.Name, F.Modified, F.LastIndexed\n" +
                "FROM search.CrawlCollections D LEFT OUTER JOIN search.CrawlResources F on D.id=F.parent\n" +
                "WHERE D.path = ?");
        s.add(toPathString(path));

        final Map<String,DavCrawler.ResourceInfo> map = new HashMap<>();

        new SqlSelector(getSearchSchema(), s).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String name = rs.getString("Name");
                if (null == name)
                    return;
                Date modified = rs.getTimestamp("Modified");
                Date lastIndex = rs.getTimestamp("LastIndexed");
                map.put(name, new DavCrawler.ResourceInfo(lastIndex, modified));
            }
        });

        return map;
    }

    String datetime = null;

    public boolean updateFile(@NotNull Path path, @NotNull Date lastIndexed, Date modified)
    {
        try
        {
            if (null == datetime)
                datetime = getSearchSchema().getSqlDialect().getDefaultDateTimeDataType();
            if (modified.getTime() == Long.MIN_VALUE)
                modified = null;
            int id = _getParentId(path);
            SQLFragment upd = new SQLFragment(
                    "UPDATE search.CrawlResources SET LastIndexed=?, Modified=CAST(? AS " + datetime + ") WHERE Parent=? AND Name=?",
                    lastIndexed, modified, id, path.getName());
            int count = new SqlExecutor(getSearchSchema()).execute(upd);
            if (count > 0)
                return true;
            SQLFragment ins = new SQLFragment(
                    "INSERT INTO search.CrawlResources(Parent,Name,LastIndexed, Modified) VALUES (?,?,?,CAST(? AS " + datetime + "))",
                    id, path.getName(), lastIndexed, modified);
            count = new SqlExecutor(getSearchSchema()).execute(ins);
            return count > 0;
        }
        catch (SQLException x)
        {

            if (SqlDialect.isConstraintException(x))
            {
                return false;
            }
            else
            {
                throw new RuntimeSQLException(x);
            }
        }
    }


    private static DbSchema getSearchSchema()
    {
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);

        if (null != ss)
            return ss.getSchema();
        else
            return null;
    }
}
