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
package org.labkey.api.search;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavResource;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:54:01 PM
 */
public interface SearchService
{
    static final Logger _log = Logger.getLogger(SearchService.class);

    public static final SearchCategory navigationCategory = new SearchCategory("navigation", "internal category", false);
    public static final SearchCategory fileCategory = new SearchCategory("file", "Files and Attachments", false);

    // marker value for documents with indexing errors
    public static final java.util.Date failDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("1899-12-30"));

    enum PRIORITY
    {
        commit,
        
        idle,       // only used to detect when there is no other work to do
        crawl,      // lowest work priority
        background, // crawler item

        bulk,       // all wikis
        group,      // one container
        item,       // one page/attachment
        delete
    }


    enum PROPERTY
    {
        title("title"),
        keywordsLo("keywordsLo"),
        keywordsMed("keywordsMed"),
        keywordsHi("keywordsHi"),
        indentifiersLo("indentifiersLo"),
        indentifiersMed("indentifiersMed"),
        indentifiersHi("indentifiersHi"),
        categories("searchCategories"),
        securableResourceId(SecurableResource.class.getName()),
        navtrail(NavTree.class.getName());  // as in NavTree.toJS()

        private final String _propName;

        PROPERTY(String name)
        {
            _propName = name;
        }

        @Override
        public String toString()
        {
            return _propName;
        }
    }

    static enum SEARCH_PHASE {createQuery, buildSecurityFilter, search, retrieveSecurityFields, applySecurityFilter, processHits}

    public interface TaskListener
    {
        void success();
        void indexError(Resource r, Throwable t);
    }


    public interface IndexTask extends Future<IndexTask>
    {
        String getDescription();

        int getDocumentCountEstimate();

        int getIndexedCount();

        int getFailedCount();

        long getStartTime();

        long getCompleteTime();

        void log(String message);

        Reader getLog();

        void addToEstimate(int i);// indicates that caller is done adding Resources to this task

        /**
         * indicates that we're done adding the initial set of resources/runnables to this task
         * the task be considered done after calling setReady() and there is no more work to do.
         */
        void setReady();

        void addRunnable(@NotNull Runnable r, @NotNull SearchService.PRIORITY pri);

        void addResource(@NotNull String identifier, SearchService.PRIORITY pri);

        void addResource(@NotNull WebdavResource r, SearchService.PRIORITY pri);
    }


    boolean accept(WebdavResource r);


    //
    // plug in interfaces
    //
    
    public interface ResourceResolver
    {
        WebdavResource resolve(@NotNull String resourceIdentifier);
    }


    public static class SearchCategory
    {
        private final String _name;
        private final String _description;
        private final boolean _showInDialog;

        public SearchCategory(@NotNull String name, @NotNull String description)
        {
            this(name,description,true);
        }
        
        public SearchCategory(@NotNull String name, @NotNull String description, boolean showInDialog)
        {
            _name = name;
            _description = description;
            _showInDialog = showInDialog;
        }

        public String getName()
        {
            return _name;
        }
        
        public String getDescription()
        {
            return _description;
        }

        @Override
        public String toString()
        {
            return _name;
        }
    }


    //
    // search
    //

    
    public static class SearchResult
    {
        public int totalHits;
        public List<SearchHit> hits;
    }

    public static class SearchHit
    {
        public String docid;
        public String container;
        public String title;
        public String summary;
        public String url;
        public String navtrail;
    }

    public String getIndexFormatDescription();

    public DbSchema getSchema();

    public WebPartView getSearchView(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink, boolean isWebpart);

    public SearchResult search(String queryString, @Nullable List<SearchCategory> categories, User user, Container current, SearchScope scope, int offset, int limit) throws IOException;

    // Search the external index.
    public SearchResult searchExternal(String queryString, int offset, int limit) throws IOException;

    public String escapeTerm(String term);
    
    public List<SearchCategory> getSearchCategories();

    //
    // index
    //

    void purgeQueues();
    void start();
    void startCrawler();
    void pauseCrawler();
    void updatePrimaryIndex();
    @Nullable Throwable getConfigurationError();
    boolean isRunning();
    boolean hasExternalIndexPermission(User user);

    List<SecurableResource> getSecurableResources(User user);    
    IndexTask defaultTask();
    IndexTask createTask(String description);
    IndexTask createTask(String description, TaskListener l);

    void deleteResource(String identifier);

    // Delete all resources whose documentIds starts with the given prefix
    void deleteResourcesForPrefix(String prefix);

    // helper to call when not found exception is detected
    void notFound(URLHelper url);

    List<IndexTask> getTasks();

    void addPathToCrawl(Path path, @Nullable Date nextCrawl);

    IndexTask indexContainer(@Nullable IndexTask task, Container c, Date since);
    IndexTask indexProject(@Nullable IndexTask task, Container project /*boolean incremental*/);
    void indexFull(boolean force);

    /** an indicator that there are a lot of things in the queue */
    boolean isBusy();
    void waitForIdle() throws InterruptedException;

    
    /** default implementation saving lastIndexed */
    void setLastIndexedForPath(Path path, long indexed, long modified);

    void deleteContainer(String id);

    public void clear();                // delete index and reset lastIndexed values
    public void clearLastIndexed();     // just reset lastIndexed values
    public void maintenance();

    //
    // configuration, plugins 
    //
    
    public void addSearchCategory(SearchCategory category);
    public List<SearchCategory> getCategories(String categories);
    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver);
    public WebdavResource resolveResource(@NotNull String resourceIdentifier);

    public void addSearchResultTemplate(@NotNull SearchResultTemplate template);
    public @Nullable SearchResultTemplate getSearchResultTemplate(@Nullable String name);

    public interface DocumentProvider
    {
        /**
         * enumerate documents for full text search
         *
         * modifiedSince == null -> full reindex
         * else incremental (either modified > modifiedSince, or modified > lastIndexed)
         */
        void enumerateDocuments(IndexTask task, @NotNull Container c, Date since);

        /**
         *if the full-text search is deleted, providers may need to clear
         * any stored lastIndexed values.
         */
        void indexDeleted() throws SQLException;
    }


    public interface DocumentParser
    {
        public String getMediaType();
        public boolean detect(WebdavResource resource, String contentType, byte[] buf) throws IOException;
        public void parse(InputStream stream, ContentHandler handler) throws IOException, SAXException;
    }
    

    // an interface that enumerates documents in a container (not recursive)
    public void addDocumentProvider(DocumentProvider provider);

    public void addDocumentParser(DocumentParser parser);

    
    //
    // helpers
    //
    

    /**
     * filter for documents modified since the provided date
     *
     * modifiedSince == null, means full search
     * otherwise incremental search which may mean either
     *      modified > lastIndexed
     * or
     *      modified > modifiedSince
     *
     * depending on whether lastIndexed is tracked

     * see Module.enumerateDocuments
     */

    public static class LastIndexedClause extends SimpleFilter.FilterClause
    {
        SQLFragment _sqlf = new SQLFragment();
        private Set<FieldKey> _fieldKeys = new HashSet<>();

        final static java.util.Date oldDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("1967-10-04"));

        public LastIndexedClause(TableInfo info, java.util.Date modifiedSince, String tableAlias)
        {
            boolean incremental = modifiedSince == null || modifiedSince.compareTo(oldDate) > 0;
            
            // no filter
            if (!incremental)
                return;

            ColumnInfo modified = info.getColumn("modified");
            ColumnInfo lastIndexed = info.getColumn("lastIndexed");
            String prefix = null == tableAlias ? " " : tableAlias + ".";

            String or = "";
            if (null != lastIndexed)
            {
                _sqlf.append(prefix).append(lastIndexed.getSelectName()).append(" IS NULL");
                _fieldKeys.add(lastIndexed.getFieldKey());
                or = " OR ";
            }

            if (null != modified && null != lastIndexed)
            {
                _sqlf.append(or);
                _sqlf.append(prefix).append(modified.getSelectName()).append(">").append(prefix).append(lastIndexed.getSelectName());
                _fieldKeys.add(modified.getFieldKey());
                _fieldKeys.add(lastIndexed.getFieldKey());
                or = " OR ";
            }

            if (null != modifiedSince && null != modified)
            {
                _sqlf.append(or);
                _sqlf.append(prefix).append(modified.getSelectName()).append("> ?");
                _sqlf.add(modifiedSince);
                _fieldKeys.add(modified.getFieldKey());
            }

            if (_sqlf.isEmpty())
            {
                _sqlf.append("1=1");
            }
            else
            {
                _sqlf.insert(0, "(");
                _sqlf.append(")");
            }
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            throw new UnsupportedOperationException();
        }

        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            return _sqlf;
        }

        public List<String> getColumnNames()
        {
            List<String> names = new ArrayList<>(_fieldKeys.size());
            for (FieldKey fieldKey : _fieldKeys)
                names.add(fieldKey.toString());
            return names;
        }

        public List<FieldKey> getFieldKeys()
        {
            return new ArrayList<>(_fieldKeys);
        }
    }
}
