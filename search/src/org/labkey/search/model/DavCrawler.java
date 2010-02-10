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

import org.apache.log4j.Category;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.Cache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.search.SearchService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavService;

import javax.servlet.ServletContextEvent;
import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 11:09:03 AM
 *
 * The Crawler has several components
 *
 * 1) DirectoryCrawler
 *  The directory crawler looks for new directories, and file updates.
 *  By default every known directory will be scanned for new folders every 12hrs
 *
 * 2) FileUpdater
 *  When a new directory or file is found it is queued up for indexing, this is where throttling 
 *  will occur (when implemented)
 *
 * The SearchService also has it's own thread pool we use when we find files to index, but the
 * background crawling is pretty different and needs its own scheduling behavior.
 */
public class DavCrawler implements ShutdownListener
{
//    SearchService.SearchCategory folderCategory = new SearchService.SearchCategory("Folder", "Folder");

    long _defaultWait = TimeUnit.SECONDS.toMillis(60);
    long _defaultBusyWait = TimeUnit.SECONDS.toMillis(1);

    // UNDONE: configurable
    // NOTE: we want to use these to control how fast we SUBMIT jobs to the indexer,
    // we don't want to hold up the actual indexer threads if possible

     // 10 directories/second
    final RateLimiter _listingRateLimiter = new RateLimiter("directory listing", 10, 1000);

    // 1 Mbyte/sec, this seems to be enough to use a LOT of tika cpu time
    final RateLimiter _fileIORateLimiter = new RateLimiter("file io", 1000000, 1000);

    // CONSIDER: file count limiter
    final RateLimiter _filesIndexRateLimiter = new RateLimiter("file index", 100, 1000);


    public static class ResourceInfo
    {
        ResourceInfo(Date indexed, Date modified)
        {
            this.lastIndexed = indexed;
            this.modified = modified;
        }
        
        Date lastIndexed;
        Date modified;
        //long length;
    }
    

    // to make testing easier, break out the interface for persisting crawl state
    // This is an awkward factoring.  Break out the "FileQueue" function instead
    public interface SavePaths
    {
        final static java.util.Date failDate = SearchService.failDate;
        final static java.util.Date nullDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("1899-12-31"));
        final static java.util.Date oldDate =  new java.sql.Timestamp(DateUtil.parseStringJDBC("1967-10-04"));

        // collections

        /** update path (optionally create) */
        boolean updatePath(Path path, java.util.Date lastIndexed, java.util.Date nextCrawl, boolean create);

        /** insert path if it does not exist */
        boolean insertPath(Path path, java.util.Date nextCrawl);
        void updatePrefix(Path path, Date next, boolean forceIndex);
        void deletePath(Path path);

        /** <lastCrawl, nextCrawl> */
        public Map<Path, Pair<Date,Date>> getPaths(int limit);
        public Date getNextCrawl();

        // files
        public Map<String,ResourceInfo> getFiles(Path path);
        public boolean updateFile(@NotNull Path path, @NotNull Date lastIndexed, @Nullable Date modified);

        public void clearFailedDocuments();
    }
    

    final static Category _log = Category.getInstance(DavCrawler.class);

    
    DavCrawler()
    {
        ContextListener.addShutdownListener(this);
        _crawlerThread.setDaemon(true);
    }


    static DavCrawler _instance = new DavCrawler();
    volatile boolean _shuttingDown = false;

    
    public static DavCrawler getInstance()
    {
        return _instance;
    }


    public void start()
    {
        if (!_shuttingDown && !_crawlerThread.isAlive())
            _crawlerThread.start();
    }


    public void shutdownPre(ServletContextEvent servletContextEvent)
    {
        _shuttingDown = true;
        if (null != _crawlerThread)
            _crawlerThread.interrupt();
    }


    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        if (null != _crawlerThread)
        try
        {
            _crawlerThread.join(1000);
        }
        catch (InterruptedException x)
        {
        }
    }

    
    /**
     * Aggressively scan the file system for new directories and new/updated files to index
     * 
     * @param path
     * @param force if (force==true) then don't check lastindexed and modified dates
     */


    public void startFull(Path path, boolean force)
    {
        _log.debug("START FULL: " + path);

        if (null == path)
            path = WebdavService.get().getResolver().getRootPath();

        // note use oldDate++ so that the crawler can schedule tasks ahead of these bulk updated colletions
        _paths.updatePrefix(path, new Date(SavePaths.oldDate.getTime() + 24*60*60*1000), force);

        addPathToCrawl(path, SavePaths.oldDate);
    }


    /**
     * start a background process to watch directories
     * optionally add a path at the same time
     */
    public void addPathToCrawl(Path start, Date nextCrawl)
    {
        _log.debug("START CONTINUOUS " + start.toString());

        if (null != start)
            _paths.updatePath(start, null, nextCrawl, true);
        pingCrawler();
    }


    LinkedList<Pair<String,Date>> _recent = new LinkedList<Pair<String,Date>>();

    
    class IndexDirectoryJob implements Runnable
    {
        SearchService.IndexTask _task;
        Path _path;
        boolean _full;
        Date _lastCrawl=null;
        Date _nextCrawl=null;
        Date _indexTime = null;
        
        /**
         * @param path
         */
        IndexDirectoryJob(Path path, Date last, Date next)
        {
            _path = path;
            _lastCrawl = last;
            _full = next.getTime() <= SavePaths.oldDate.getTime();
            _task = getSearchService().createTask("Index " + _path.toString());
        }


//        public void submit()
//        {
//            _listingRateLimiter.add(0,true);
//            _fileIORateLimiter.add(0,true);
//
//            _task.addRunnable(this, SearchService.PRIORITY.crawl);
//            _task.setReady();
//        }


        public void run()
        {
            boolean isCrawlerThread = Thread.currentThread() == _crawlerThread;
            
            _listingRateLimiter.add(1, isCrawlerThread);

            _log.debug("IndexDirectoryJob.run(" + _path + ")");

            final Resource r = getResolver().lookup(_path);

            // CONSIDER: delete previously indexed resources in child containers as well
            if (null == r || !r.isCollection() || !r.shouldIndex() || skipContainer(r))
            {
                if (_path.startsWith(getResolver().getRootPath()))
                    _paths.deletePath(_path);
                return;
            }

            _indexTime = new Date(System.currentTimeMillis());
            long changeInterval = (r instanceof WebdavResolver.WebFolder) ? Cache.DAY / 2 : Cache.DAY;
            long nextCrawl = _indexTime.getTime() + (long)(changeInterval * (0.5 + 0.5 * Math.random()));
            _nextCrawl = new Date(nextCrawl);

            _task.onSuccess(new Runnable() {
                public void run()
                {
                    _paths.updatePath(_path, _indexTime, _nextCrawl, true);
                    addRecent(r);
                }
            });

            // if this is a web folder, call enumerate documents
            if (r instanceof WebdavResolver.WebFolder)
            {
                Container c = ContainerManager.getForId(r.getContainerId());
                if (null == c)
                    return;
                getSearchService().indexContainer(_task, c,  _full ? null : _lastCrawl);
            }

            // get current index status for files
            // CONSIDER: store lastModifiedTime in crawlResources
            // CONSIDER: store documentId in crawlResources
            Map<String,ResourceInfo> map = _paths.getFiles(_path);

            for (Resource child : r.list())
            {
                if (_shuttingDown)
                    return;
                if (!child.exists()) // happens when pipeline is defined but directory doesn't exist
                    continue;

                if (child.isFile())
                {
                    ResourceInfo info =  map.remove(child.getName());
                    Date lastIndexed   = (null==info || null==info.lastIndexed) ? SavePaths.nullDate : info.lastIndexed;
                    Date savedModified = (null==info || null==info.modified) ? SavePaths.nullDate : info.modified;
                    long lastModified = child.getLastModified();

                    if (lastModified == savedModified.getTime() && (lastModified <= lastIndexed.getTime() || lastIndexed.getTime() == SavePaths.failDate.getTime()))
                        continue;

                    if (skipFile(child))
                    {
                        // just index the name and that's all
                        final Resource wrap = child;
                        ActionURL url = new ActionURL(r.getExecuteHref(null));
                        Map<String, Object> props = new HashMap<String, Object>();
                        props.put(SearchService.PROPERTY.categories.toString(), SearchService.fileCategory.toString());
                        props.put(SearchService.PROPERTY.displayTitle.toString(), wrap.getPath().getName());
                        child = new SimpleDocumentResource(wrap.getPath(), wrap.getDocumentId(), wrap.getContainerId(), wrap.getContentType(),
                                new byte[0], url, props){
                            @Override
                            public void setLastIndexed(long ms, long modified)
                            {
                                wrap.setLastIndexed(ms, modified);
                            }
                        };
                    }

                    File f = child.getFile();
                    if (null != f)
                    {
                        if (!f.isFile())
                            continue;
                        _fileIORateLimiter.add(f.length(), isCrawlerThread);
                    }

                    _task.addResource(child, SearchService.PRIORITY.background);
                    addRecent(child);
                }
                else if (!child.shouldIndex())
                {
                    continue;
                }
                else if (!skipContainer(child))
                {
                    long childCrawl = SavePaths.oldDate.getTime();
                    if (!(child instanceof WebdavResolver.WebFolder))
                        childCrawl += child.getPath().size()*1000; // bias toward breadth first
                    if (_full)
                    {
                        _paths.updatePath(child.getPath(), null, new Date(childCrawl), true);
                        pingCrawler();
                    }
                    else
                    {
                        _paths.insertPath(child.getPath(), new Date(childCrawl));
                    }
                }
            }

            // as for the missing
            SearchService ss = getSearchService();
            for (String missing : map.keySet())
            {
                Path missingPath = _path.append(missing);
                String docId =  "dav:" + missingPath.toString();
                ss.deleteResource(docId);
            }

            _task.setReady();
        }
    }


    void addRecent(Resource r)
    {
        synchronized (_recent)
        {
            Date d = new Date(System.currentTimeMillis());
            while (_recent.size() > 40)
                _recent.removeFirst();
            while (_recent.size() > 0 && _recent.getFirst().second.getTime() < d.getTime()-10*60000)
                _recent.removeFirst();
            String text = r.isCollection() ? r.getName() + "/" : r.getName();
            _recent.add(new Pair(text,d));
        }
    }


    final Object _crawlerEvent = new Object();

    void pingCrawler()
    {
        synchronized (_crawlerEvent)
        {
            _crawlerEvent.notifyAll();
        }
    }


    void _wait(Object event, long wait)
    {
        if (wait == 0 || _shuttingDown)
            return;
        try
        {
            synchronized (event)
            {
                event.wait(wait);
            }
        }
        catch (InterruptedException x)
        {
        }
    }


//    final Runnable pingJob = new Runnable()
//    {
//        public void run()
//        {
//            synchronized (_crawlerEvent)
//            {
//                _crawlerEvent.notifyAll();
//            }
//        }
//    };


    void waitForIndexerIdle() throws InterruptedException
    {
        SearchService ss = getSearchService();
        ((AbstractSearchService)ss).waitForRunning();

        // wait for indexer to have nothing else to do
        while (!_shuttingDown && ss.isBusy())
        {
            ss.waitForIdle();
        }
    }
    

    Thread _crawlerThread = new Thread("DavCrawler")
    {
        @Override
        public void run()
        {
            while (!_shuttingDown && null == getSearchService())
            {
                try { Thread.sleep(1000); } catch (InterruptedException x) {}
            }

            while (!_shuttingDown)
            {
                try
                {
                    waitForIndexerIdle();

                    IndexDirectoryJob j = findSomeWork();
                    if (null != j)
                    {
                        j.run();
                    }
                    else
                    {
                        _wait(_crawlerEvent, _defaultWait);                  
                    }
                }
                catch (InterruptedException x)
                {
                    continue;
                }
                catch (Throwable t)
                {
                    _log.error("Unexpected error", t);
                }
            }
        }
    };


    LinkedList<IndexDirectoryJob> crawlQueue = new LinkedList<IndexDirectoryJob>();

    IndexDirectoryJob findSomeWork()
    {
        if (_shuttingDown)
            return null;
        if (crawlQueue.isEmpty())
        {
            _log.debug("findSomeWork()");

            Map<Path,Pair<Date,Date>> map = _paths.getPaths(100);

            for (Map.Entry<Path,Pair<Date,Date>> e : map.entrySet())
            {
                Path path = e.getKey();
                Date lastCrawl = e.getValue().first;
                Date nextCrawl = e.getValue().second;

                _log.debug("add to queue: " + path.toString());
                crawlQueue.add(new IndexDirectoryJob(path, lastCrawl, nextCrawl));
            }
        }
        return crawlQueue.isEmpty() ? null : crawlQueue.removeFirst();
    }
    

    static boolean skipContainer(Resource r)
    {
        Path path = r.getPath();
        String name = path.getName();

        if ("@wiki".equals(name))
            return true;

        if (".svn".equals(name))
            return true;

        if (".Trash".equals(name))
            return true;

        // if symbolic link
        //  return true;
        
        if (name.startsWith("."))
            return true;

        // UNDONE: shouldn't be hard-coded
        if ("labkey_full_text_index".equals(name))
            return true;

        // google convention
        if (path.contains("no_crawl"))
            return true;

        File f = r.getFile();
        if (null != f)
        {
            // labkey convention
            if (new File(f,".nocrawl").exists())
                return true;
            // postgres
            if (new File(f,"PG_VERSION").exists())
                return true;
        }

        return false;
    }

    
    static boolean skipFile(Resource r)
    {
        // let's not index large files, or files that probably aren't useful to index
        String contentType = r.getContentType();
        if (contentType.startsWith("image/"))
            return true;
        String name = r.getName();
        String ext = "";
        int i = name.lastIndexOf(".");
        if (i != -1)
            ext = name.substring(i+1).toLowerCase();
        if (ext.equals("mzxml") || ext.equals("mzml"))
            return true;
        return false;
    }
    

    //
    // dependencies
    //

    SavePaths _paths = new org.labkey.search.model.SavePaths();
    WebdavResolver _resolver = null;
    SearchService _ss = null;

    void setSearchService(SearchService ss)
    {
        _ss = ss;
    }
    
    SearchService getSearchService()
    {
        if (null == _ss)
            _ss = ServiceRegistry.get().getService(SearchService.class);
        return _ss;
    }

    void setResolver(WebdavResolver resolver)
    {
        _resolver = resolver;
    }
    
    WebdavResolver getResolver()
    {
        if (_resolver == null)
            _resolver = WebdavService.get().getResolver();
        return _resolver;
    }


    public Map getStats()
    {
        SearchService ss = getSearchService();
        boolean paused = !ss.isRunning();
        long now = System.currentTimeMillis();

        Map m = new LinkedHashMap();
        try
        {
            DbSchema s = DbSchema.get("search");

            Integer uniqueCollections = Table.executeSingleton(s, "SELECT count(*) FROM search.crawlcollections", null, Integer.class);
            m.put("Number of unique folders/directories", uniqueCollections);
            
            if (!paused)
            {
                Date nextHour = new Date(now + TimeUnit.SECONDS.toMillis(60*60));
                Long countNext = Table.executeSingleton(s, "SELECT count(*) FROM search.crawlcollections where nextCrawl < ?", new Object[]{nextHour}, Long.class);
                double max = (60*60) * _listingRateLimiter.getTarget().getRate(TimeUnit.SECONDS);
                long scheduled = Math.min(countNext.longValue(), Math.round(max));
                m.put("Directories to scan in next 1 hr", scheduled);
            }

            m.put("Directory limiter", Math.round(_listingRateLimiter.getTarget().getRate(TimeUnit.SECONDS)) + "/sec");
            m.put("File I/O limiter", (_fileIORateLimiter.getTarget().getRate(TimeUnit.SECONDS)/1000000) + " MB/sec");

            String activity = getActivityHtml();
            m.put("Recent crawler activity", activity.toString());
        }
        catch (SQLException x)
        {
            _log.error("Unexpected error", x);
        }
        return m;
    }


    String getActivityHtml()
    {
        SearchService ss = getSearchService();
        boolean paused = !ss.isRunning();

        Pair<String,Date>[] recent;
        synchronized(_recent)
        {
            recent = new Pair[_recent.size()];
            _recent.toArray(recent);
            Arrays.sort(recent, new Comparator<Pair<String,Date>>(){
                public int compare(Pair<String,Date> o1, Pair<String,Date> o2)
                {
                    return o2.second.compareTo(o1.second);
                }
            });
        }

        StringBuilder activity = new StringBuilder("<table cellpadding=1 cellspacing=0>"); //<tr><td><img width=80 height=1 src='" + AppProps.getInstance().getContextPath() + "/_.gif'></td><td><img width=300 height=1 src='" + AppProps.getInstance().getContextPath() + "/_.gif'></td></tr>");
        String last = "";
        long now = System.currentTimeMillis();
        long cutoff = now - (paused ? 60000 : 5*60000);
        now = now - (now % 1000);
        long newest = 0;
        for (Pair<String,Date> p : recent)
        {
            String text  = p.first;
            long time = p.second.getTime();
            if (time < cutoff) continue;
            newest = Math.max(newest,time);
            long dur = Math.max(0,now - (time-(time%1000)));
            String ago = DateUtil.formatDuration(dur) + "&nbsp;ago";
            activity.append("<tr><td align=right color=#c0c0c0>" + (ago.equals(last)?"":ago) + "&nbsp;</td><td>" + PageFlowUtil.filter(text) + "</td></tr>\n");
            last = ago;
        }
        if (paused)
        {
            activity.append("<tr><td colspan=2>PAUSED");
            if (newest+60000>now)
                activity.append (" (queue may take a while to clear)");
            activity.append("</td></tr>");
        }
        activity.append("</table>");
        return activity.toString();
    }


    public void clearFailedDocuments()
    {
        _paths.clearFailedDocuments();
    }
}