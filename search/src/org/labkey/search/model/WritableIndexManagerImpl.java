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
package org.labkey.search.model;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Constants;
import org.labkey.api.util.ExceptionUtil;

import java.io.File;
import java.io.IOException;

/**
 * User: adam
 * Date: Apr 18, 2010
 * Time: 9:08:19 AM
 */

// Adds synchronization for writing, in addition to searching synchronization provided by IndexManager
class WritableIndexManagerImpl extends IndexManager implements WritableIndexManager
{
    private static final Logger _log = Logger.getLogger(WritableIndexManagerImpl.class);

    private final Object _writerLock = new Object();
    private final IndexWriter _iw;


    static WritableIndexManager get(File indexPath, Analyzer analyzer) throws IOException
    {
        SearcherFactory factory = new SearcherFactory()
        {
            @Override
            public IndexSearcher newSearcher(IndexReader reader) throws IOException
            {
                // TODO: Warm the new searcher before returning
                return super.newSearcher(reader);
            }
        };

        Directory directory = openDirectory(indexPath);
        IndexWriter iw = null;

        try
        {
            // Consider: wrap analyzer with LimitTokenCountAnalyzer to limit indexed content?
            iw = new IndexWriter(directory, new IndexWriterConfig(LuceneSearchServiceImpl.LUCENE_VERSION, analyzer));
        }
        finally
        {
            if (null == iw)
                directory.close();
        }

        return new WritableIndexManagerImpl(iw, factory, directory);
    }


    // We would like to call FSDirectory.open(indexPath) and let Lucene choose the best Directory implementation,
    // however, in 3.5.0 Lucene starting choosing memory mapped files, which seem to cause all kinds of problems
    // on atlas test.  This approach mimics what Lucene 3.0 did, which seemed to work just fine.
    private static Directory openDirectory(File path) throws IOException
    {
        if (Constants.WINDOWS)
            return new SimpleFSDirectory(path, null);
        else
            return new NIOFSDirectory(path, null);
    }


    private WritableIndexManagerImpl(IndexWriter iw, SearcherFactory factory, Directory directory) throws IOException
    {
        super(new SearcherManager(iw, true, factory), directory);
        _iw = iw;
    }


    IndexWriter getIndexWriter()
    {
        return _iw;
    }


    public void index(String id, Document doc) throws IOException
    {
        synchronized (_writerLock)
        {
            deleteDocument(id);
            getIndexWriter().addDocument(doc);
        }
    }


    public void deleteDocument(String id)
    {
        try
        {
            synchronized (_writerLock)
            {
                IndexWriter iw = getIndexWriter();
                iw.deleteDocuments(new Term(LuceneSearchServiceImpl.FIELD_NAMES.uniqueId.toString(), id));
            }
        }
        catch (Throwable e)
        {
            _log.error("Indexing error deleting " + id, e);
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }


    public void deleteQuery(Query query) throws IOException
    {
        synchronized (_writerLock)
        {
            IndexWriter w = getIndexWriter();
            w.deleteDocuments(query);
        }
    }


    public void clear()
    {
        try
        {
            synchronized (_writerLock)
            {
                getIndexWriter().deleteAll();
                commit();
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void close() throws IOException, InterruptedException
    {
        synchronized (_writerLock)
        {
            _iw.close();
            _directory.close();
            _manager.close();
        }
    }

    // If this throws then re-initialize the index manager
    public void commit()
    {
        synchronized (_writerLock)
        {
            try
            {
                _iw.commit();
                _manager.maybeRefresh();
            }
            catch (IOException e)
            {
                // Close IndexWriter here as well?
                ExceptionUtil.logExceptionToMothership(null, e);
            }
            catch (OutOfMemoryError e)
            {
                // JavaDoc strongly recommends closing the IndexWriter on OOM
                try
                {
                    try
                    {
                        _iw.close();
                    }
                    catch (IOException e1)
                    {
                        // Log it and try again (per Lucene JavaDoc)
                        ExceptionUtil.logExceptionToMothership(null, e1);

                        try
                        {
                            _iw.close();
                        }
                        catch (IOException e2)
                        {
                            ExceptionUtil.logExceptionToMothership(null, e2);
                        }
                    }
                }
                finally
                {
                    try
                    {
                        _directory.close();

                        if (IndexWriter.isLocked(_directory))
                            IndexWriter.unlock(_directory);
                    }
                    catch (IOException e1)
                    {
                        ExceptionUtil.logExceptionToMothership(null, e1);
                    }
                    finally
                    {
                        throw e;
                    }
                }
            }
        }
    }


    public void optimize()
    {
        // We removed the call to IndexWriter.optimize() in 12.1.  Lucene deprecated this method and strongly dis-recommends
        // calling its replacement (forceMerge()).  Let's see what happens if we just leave it out.
    }
}
