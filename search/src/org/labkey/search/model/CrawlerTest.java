/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.labkey.api.util.Path;
import org.labkey.api.util.Pair;
import org.labkey.api.util.GUID;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.FileSystemResource;
import org.labkey.api.security.*;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.data.Container;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.io.File;

/**
 * User: matthewb
 * Date: Dec 12, 2009
 * Time: 3:51:51 PM
 */
public class CrawlerTest extends junit.framework.TestCase
{
    public CrawlerTest()
    {
        super();
    }


    public CrawlerTest(String name)
    {
        super(name);
    }


    public static Test suite()
    {
        return new TestSuite(CrawlerTest.class);
    }

    
    public void test() throws Exception
    {
        DavCrawler cr = new DavCrawler();
        cr.setResolver(new TestResolver(ModuleLoader.getInstance().getCoreModule().getExplodedPath()));
        cr.startFull(Path.rootPath, true);

        long start = System.currentTimeMillis();
    }

    
    //
    // TEST
    //

    class TestResolver implements WebdavResolver, SecurableResource
    {
        final File _base;
        final SecurityPolicy _policy;

        TestResolver(File f)
        {
            _base = f;
            MutableSecurityPolicy policy = new MutableSecurityPolicy(this);
            policy.addRoleAssignment(UserManager.getGuestUser(), ReaderRole.class);
            policy.addRoleAssignment(User.getSearchUser(), ReaderRole.class);
            _policy = policy;
        }
        
        public boolean requiresLogin()
        {
            return false;
        }

        public Path getRootPath()
        {
            return Path.rootPath;
        }

        public WebdavResource lookup(Path path)
        {
            return new FileSystemResource(path, new File(_base, path.toString()), _policy);
        }

        public WebdavResource welcome()
        {
            return null;
        }

        // SecurableResource
        String _guid = GUID.makeGUID();

        @NotNull
        public String getResourceId()
        {
            return _guid;
        }

        @NotNull
        public String getResourceName()
        {
            return _base.getName();
        }

        @NotNull
        public String getResourceDescription()
        {
            return null;
        }

        @NotNull
        public Set<Class<? extends Permission>> getRelevantPermissions()
        {
            return null;
        }

        @NotNull
        public Module getSourceModule()
        {
            return null;
        }

        public SecurableResource getParentResource()
        {
            return null;
        }

        @NotNull
        public Container getResourceContainer()
        {
            return null;
        }

        @NotNull
        public List<SecurableResource> getChildResources(User user)
        {
            return null;
        }

        public boolean mayInheritPolicy()
        {
            return false;
        }
    }


    class TestSavePaths implements DavCrawler.SavePaths
    {
        Map<Path, Pair<Date,Date>> collections = new HashMap<Path,Pair<Date,Date>>();
        Map<Path, DavCrawler.ResourceInfo> files = new HashMap<Path, DavCrawler.ResourceInfo>();

        public boolean insertPath(Path path, Date nextCrawl)
        {
            if (collections.containsKey(path))
                return false;
            Pair p = collections.put(path, new Pair<Date,Date>(nullDate,nextCrawl));
            return true;
        }

        public synchronized boolean updatePath(Path path, Date lastIndexed, Date nextCrawl, boolean create)
        {
            collections.put(path, new Pair<Date,Date>(lastIndexed,nextCrawl));
            return true;
        }

        public synchronized void updatePrefix(Path path, Date nextCrawl, boolean force)
        {
            for (Map.Entry<Path,Pair<Date,Date>> e : collections.entrySet())
            {
                if (e.getKey().startsWith(path))
                    updatePath(e.getKey(), e.getValue().first, nextCrawl, false);
            }
        }

        public synchronized void deletePath(Path path)
        {
            collections.remove(path);
        }

        public synchronized Map<Path, Pair<Date,Date>> getPaths(int limit)
        {
            long now = System.currentTimeMillis();
            limit = Math.min(limit,5);
            Map<Path, Pair<Date,Date>> ret = new TreeMap<Path,Pair<Date,Date>>();
            for (Map.Entry<Path,Pair<Date,Date>> e : collections.entrySet())
            {
                Date nextCrawl = e.getValue().second;
                if (nextCrawl.getTime() < now)
                    ret.put(e.getKey(), new Pair(e.getValue().first, e.getValue().second));
                if (ret.size() == limit)
                    break;
            }
            return ret;
        }


        public Date getNextCrawl()
        {
            return new Date(System.currentTimeMillis());
        }
        

        public synchronized Map<String, DavCrawler.ResourceInfo> getFiles(Path path)
        {
            Map<String,DavCrawler.ResourceInfo> ret = new TreeMap<String,DavCrawler.ResourceInfo>();
            for (Map.Entry<Path,DavCrawler.ResourceInfo> e : files.entrySet())
            {
                if (e.getKey().startsWith(path))
                    ret.put(e.getKey().getName(), e.getValue());
            }
            return ret;
        }

        public synchronized boolean updateFile(Path path, Date lastIndexed, Date modified)
        {
            files.put(path, new DavCrawler.ResourceInfo(lastIndexed,modified));
            return true;
        }

        public void clearFailedDocuments()
        {
        }
    }
}
