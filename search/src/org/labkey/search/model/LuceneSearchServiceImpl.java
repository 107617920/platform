/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.Version;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hpsf.NoPropertySetStreamException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchUtils;
import org.labkey.api.search.SearchUtils.*;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MultiPhaseCPUTimer;
import org.labkey.api.util.MultiPhaseCPUTimer.InvocationTimer;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.search.view.SearchWebPart;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Nov 18, 2009
 * Time: 1:14:44 PM
 */
public class LuceneSearchServiceImpl extends AbstractSearchService
{
    private static final Logger _log = Logger.getLogger(LuceneSearchServiceImpl.class);
    private static final MultiPhaseCPUTimer<SEARCH_PHASE> TIMER = new MultiPhaseCPUTimer<SEARCH_PHASE>(SEARCH_PHASE.class, SEARCH_PHASE.values());

    static final Version LUCENE_VERSION = Version.LUCENE_30;

    private final Analyzer _analyzer = ExternalAnalyzer.SnowballAnalyzer.getAnalyzer();

    // Changes to _index are rare (only when admin changes the index path), but we want any changes to be visible to
    // other threads immediately.
    private volatile WritableIndex _index;

    private static ExternalIndex _externalIndex;

    static enum FIELD_NAMES { body, displayTitle, title /* use "title" keyword for search title */, summary,
        url, container, resourceId, uniqueId, navtrail }

    private static enum SEARCH_PHASE {determineParticipantId, createQuery, buildSecurityFilter, search, processHits}

    private void initializeIndex()
    {
        try
        {
            File indexDir = SearchPropertyManager.getPrimaryIndexDirectory();
            _index = new WritableIndexImpl(indexDir, _analyzer);
            setConfigurationError(null);  // Clear out any previous error
        }
        catch (Throwable t)
        {
            _log.error("Error: Unable to initialize search index. Search will be disabled and new documents will not be indexed for searching until this is corrected and the server is restarted. See below for details about the cause.");
            setConfigurationError(t);
            _index = new NoopWritableIndex(_log);
            throw new RuntimeException("Error: Unable to initialize search index", t);
        }
    }


    @Override
    public void updatePrimaryIndex()
    {
        super.updatePrimaryIndex();
        initializeIndex();
        clearLastIndexed();
    }

    @Override
    public void start()
    {
        try
        {
            initializeIndex();
            resetExternalIndex();
        }
        catch (Exception e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        super.start();
    }


    public void resetExternalIndex() throws IOException, InterruptedException
    {
        if (null != _externalIndex)
        {
            _externalIndex.close();
            _externalIndex = null;
        }

        ExternalIndexProperties props = SearchPropertyManager.getExternalIndexProperties();

        if (props.hasExternalIndex())
        {
            File externalIndexFile = new File(props.getExternalIndexPath());
            Analyzer analyzer = ExternalAnalyzer.valueOf(props.getExternalIndexAnalyzer()).getAnalyzer();

            if (externalIndexFile.exists())
                _externalIndex = new ExternalIndex(externalIndexFile, analyzer);
        }
    }


    public void swapExternalIndex() throws IOException, InterruptedException
    {
        if (null != _externalIndex)
        {
            _externalIndex.swap();
        }
    }


    public String escapeTerm(String term)
    {
        if (StringUtils.isEmpty(term))
            return "";
        String illegal = "+-&|!(){}[]^\"~*?:\\";
        if (StringUtils.containsNone(term,illegal))
            return term;
        StringBuilder sb = new StringBuilder(term.length()*2);
        for (char ch : term.toCharArray())
        {
            if (illegal.indexOf(ch) != -1)
                sb.append('\\');
            sb.append(ch);
        }
        return sb.toString();
    }
    

    public void clearIndex()
    {
        _index.clear();
    }


    private static final Set<String> KNOWN_PROPERTIES = PageFlowUtil.set(
            PROPERTY.categories.toString(), PROPERTY.displayTitle.toString(), PROPERTY.searchTitle.toString(),
            PROPERTY.navtrail.toString(), PROPERTY.securableResourceId.toString());

    @Override
    Map<?, ?> preprocess(String id, WebdavResource r)
    {
        FileStream fs = null;

        try
        {
            if (null == r.getDocumentId())
                logBadDocument("Null document id", r);

            if (null == r.getContainerId())
                logBadDocument("Null container id", r);

            Container c = ContainerManager.getForId(r.getContainerId());

            if (null == c)
                return null;

            fs = r.getFileStream(User.getSearchUser());

            if (null == fs)
            {
                logAsWarning(r, "FileStream is null");
                return null;
            }
            
            Map<String, ?> props = r.getProperties();
            assert null != props;

            String categories = (String)props.get(PROPERTY.categories.toString());
            assert null != categories;

            String body = null;
            String displayTitle = (String)props.get(PROPERTY.displayTitle.toString());
            String searchTitle = (String)props.get(PROPERTY.searchTitle.toString());

            // Search title can be null
            if (null == searchTitle)
                searchTitle = "";

            try
            {
                Map<String, String> customProperties = r.getCustomProperties(User.getSearchUser());

                if (null != customProperties && !customProperties.isEmpty())
                {
                    for (String value : customProperties.values())
                        searchTitle += " " + value;
                }
            }
            catch (UnauthorizedException ue)
            {
                // Some QueryUpdateService implementations don't special case the search user.  Continue indexing in this
                // case, but skip the custom properties.
            }

            // Fix #11393.  Can't append description to searchTitle in FileSystemResource() because constructor is too
            // early to retrieve description.  TODO: Move description into properties, instead of exposing it as a
            // top-level getter.  This is a bigger change, so we'll wait for 11.2.
            String description = r.getDescription();

            if (null != description)
                searchTitle += " " + description;   

            String type = r.getContentType();

            // Don't load content of images or zip files (for now), but allow searching by name and properties
            if (isImage(type) || isZip(type))
            {
                body = "";
            }
            else
            {
                InputStream is = fs.openInputStream();

                if (null == is)
                {
                    logAsWarning(r, "InputStream is null");
                    return null;
                }

                if ("text/html".equals(type))
                {
                    String html;
                    if (isTooBig(fs, type))
                        html = "<html><body></body></html>";
                    else
                        html = PageFlowUtil.getStreamContentsAsString(is);

                    // TODO: Need better check for issue HTML vs. rendered page HTML
                    if (r instanceof ActionResource)
                    {
                        HTMLContentExtractor extractor = new HTMLContentExtractor.LabKeyPageHTMLExtractor(html);
                        body = extractor.extract();
                        String extractedTitle = extractor.getTitle();

                        if (StringUtils.isBlank(displayTitle))
                            displayTitle = extractedTitle;

                        searchTitle = searchTitle + " " + extractedTitle;
                    }

                    if (StringUtils.isEmpty(body))
                    {
                        body = new HTMLContentExtractor.GenericHTMLExtractor(html).extract();
                    }

                    if (null == displayTitle)
                        logBadDocument("Null display title", r);
                }
                else if (type.startsWith("text/") && !type.contains("xml"))
                {
                    if (isTooBig(fs, type))
                        body = "";
                    else
                        body = PageFlowUtil.getStreamContentsAsString(is);
                }
                else
                {
                    Metadata metadata = new Metadata();
                    metadata.add(Metadata.RESOURCE_NAME_KEY, PageFlowUtil.encode(r.getName()));
                    metadata.add(Metadata.CONTENT_TYPE, r.getContentType());
                    ContentHandler handler = new BodyContentHandler(-1);     // no write limit on the handler -- rely on file size check to limit content

                    parse(r, fs, is, handler, metadata);

                    body = handler.toString();

                    String extractedTitle = metadata.get(Metadata.TITLE);
                    if (StringUtils.isBlank(displayTitle))
                        displayTitle = extractedTitle;
                    searchTitle = searchTitle + getInterestingMetadataProperties(metadata);
                }

                fs.closeInputStream();
            }

            fs = null;

            String url = r.getExecuteHref(null);

            if (null == url)
                logBadDocument("Null url", r);

            if (null == displayTitle)
                logBadDocument("Null display title", r);

            _log.debug("parsed " + url);

            if (StringUtils.isBlank(searchTitle))
                searchTitle = displayTitle;

            // Add all container path parts to search keywords
            for (String part : c.getParsedPath())
                searchTitle = searchTitle + " " + part;

            String summary = extractSummary(body, displayTitle);
            // Split the category string by whitespace, index each without stemming
            String[] categoryArray = categories.split("\\s+");

            Document doc = new Document();

            // Index and store the unique document ID
            doc.add(new Field(FIELD_NAMES.uniqueId.toString(), r.getDocumentId(), Field.Store.YES, Field.Index.NOT_ANALYZED));

            // Index, but don't store
            doc.add(new Field(FIELD_NAMES.body.toString(), body, Field.Store.NO, Field.Index.ANALYZED));
            doc.add(new Field(FIELD_NAMES.title.toString(), searchTitle, Field.Store.NO, Field.Index.ANALYZED));
            for (String category : categoryArray)
                doc.add(new Field(PROPERTY.categories.toString(), category.toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));

            // Store, but don't index
            doc.add(new Field(FIELD_NAMES.displayTitle.toString(), displayTitle, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.summary.toString(), summary, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.url.toString(), url, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.container.toString(), r.getContainerId(), Field.Store.YES, Field.Index.NOT_ANALYZED));
            if (null != props.get(PROPERTY.navtrail.toString()))
                doc.add(new Field(FIELD_NAMES.navtrail.toString(), (String)props.get(PROPERTY.navtrail.toString()), Field.Store.YES, Field.Index.NO));
            String resourceId = (String)props.get(PROPERTY.securableResourceId.toString());
            if (null != resourceId && !resourceId.equals(r.getContainerId()))
                doc.add(new Field(FIELD_NAMES.resourceId.toString(), resourceId, Field.Store.YES, Field.Index.NO));
            // Index the remaining properties, but don't store
            for (Map.Entry<String, ?> entry : props.entrySet())
            {
                Object value = entry.getValue();

                if (null != value)
                {
                    String stringValue = value.toString().toLowerCase();

                    if (stringValue.length() > 0)
                    {
                        String key = entry.getKey();

                        // Skip known properties -- we added them above
                        if (!KNOWN_PROPERTIES.contains(key))
                            doc.add(new Field(key.toLowerCase(), stringValue, Field.Store.NO, Field.Index.ANALYZED));
                    }
                }
            }

            return Collections.singletonMap(Document.class, doc);
        }
        catch (NoClassDefFoundError err)
        {
            Throwable cause = err.getCause();
            // Suppress stack trace, etc., if Bouncy Castle isn't present.  Use cause since ClassNotFoundException's
            // message is consistent across JVMs; NoClassDefFoundError's is not.  Note: This shouldn't happen any more
            // since Bouncy Castle ships with Tika as of 0.7.
            if (cause != null && cause instanceof ClassNotFoundException && cause.getMessage().equals("org.bouncycastle.cms.CMSException"))
                _log.warn("Can't read encrypted document \"" + id + "\".  You must install the Bouncy Castle encryption libraries to index this document.  Refer to the LabKey Software documentation for instructions.");
            else
                logAsPreProcessingException(r, err);
        }
        catch (TikaException e)
        {
            String topMessage = (null != e.getMessage() ? e.getMessage() : "");
            Throwable cause = e.getCause();

            // Get the root cause
            Throwable rootCause = e;

            while (null != rootCause.getCause())
                rootCause = rootCause.getCause();

            // IndexOutOfBoundsException has a dumb message
            String rootMessage = (rootCause instanceof IndexOutOfBoundsException ? rootCause.getClass().getSimpleName() : rootCause.getMessage());

            if (topMessage.startsWith("TIKA-237: Illegal SAXException"))
            {
                // Malformed XML document -- CONSIDER: run XML tidy on the document and retry
                logAsWarning(r, "Malformed XML document");
            }
            else if (cause instanceof java.util.zip.ZipException)
            {
                // Malformed zip file
                logAsWarning(r, "Malformed zip file");
            }
            else if (cause instanceof EncryptedDocumentException)
            {
                // Encrypted office document
                logAsWarning(r, "Document is password protected");
            }
            else if (topMessage.startsWith("Error creating OOXML extractor"))
            {
                logAsWarning(r, "Can't parse this Office document [" + rootMessage + "]");
            }
            else if (topMessage.startsWith("TIKA-198: Illegal IOException from org.apache.tika.parser.microsoft.OfficeParser"))
            {
                // Document is currently open in Word
                logAsWarning(r, "Can't parse this Office document [" + rootMessage + "]");
            }
            else if (topMessage.startsWith("TIKA-198: Illegal IOException from org.apache.tika.parser.pdf.PDFParser") ||
                     topMessage.startsWith("Unable to extract PDF content"))
            {
                logAsWarning(r, "Can't parse this PDF document [" + rootMessage + "]");
            }
            else if (topMessage.startsWith("TIKA-198: Illegal IOException"))
            {
                logAsWarning(r, "Can't parse this document [" + rootMessage + "]");                
            }
            else if (topMessage.startsWith("Unexpected RuntimeException from org.apache.tika.parser"))
            {
                // Example: Support_Gunaretnam.pdf
                logAsWarning(r, "Can't parse this document [" + rootMessage + "]");
            }
            else if (topMessage.equals("Not a HPSF document") && cause instanceof NoPropertySetStreamException)
            {
                // XLS file generated by JavaExcel -- POI doesn't like some of them
                logAsWarning(r, "Can't parse this Excel document [POI can't read Java Excel spreadsheets]");
            }
            else if (topMessage.equals("Failed to parse a Java class"))
            {
                // Corrupt Java file -- see SearchModule.class, which was hand-mangled
                logAsWarning(r, "Can't parse this Java class file [" + rootMessage + "]");
            }
            else if (topMessage.equals("TIKA-418: RuntimeException while getting content for thmx and xps file types"))
            {
                // Tika doesn't support .thmx or .xps file types
                // Example: Extending LabKey.thmx
                logAsWarning(r, "Can't parse this document type [" + rootMessage + "]");
            }
            else
            {
                logAsPreProcessingException(r, e);
            }
        }
        catch (IOException e)
        {
            // Permissions problem, network drive disappeared, file disappeared, etc.
            logAsWarning(r, e);
        }
        catch (SAXException e)
        {
            // Malformed XML/HTML
            logAsWarning(r, e);
        }
        catch (Throwable e)
        {
            logAsPreProcessingException(r, e);
        }
        finally
        {
            if (null != fs)
            {
                try
                {
                    fs.closeInputStream();
                }
                catch (IOException x)
                {
                }
            }
        }

        return null;
    }


    private void logBadDocument(String problem, WebdavResource r)
    {
        String message = problem + ". Document creation stack trace:" + ExceptionUtil.renderStackTrace(r.getCreationStackTrace());
        _log.error(message);
        throw new IllegalStateException(problem);
    }


    /**
     * parse the document of the resource, not that parse() and accept() should agree on what is parsable
     * 
     * @param r
     * @param fs
     * @param is
     * @param handler
     * @param metadata
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     */
    void parse(WebdavResource r, FileStream fs, InputStream is, ContentHandler handler, Metadata metadata) throws IOException, SAXException, TikaException
    {
        if (!is.markSupported())
            is = new BufferedInputStream(is);

        DocumentParser p = detectParser(r, is);
        if (null != p)
        {
            metadata.add(Metadata.CONTENT_TYPE, p.getMediaType());
            p.parse(is, handler);
            return;
        }

        // Treat files over the size limit as empty files
        if (isTooBig(fs,r.getContentType()))
        {
            logAsWarning(r, "The document is too large");
            return;
        }

        Parser parser = getParser();
        parser.parse(is, handler, metadata);
    }

    
    /**
     * This method is used to indicate to the crawler (or any external process) which files
     * this indexer will not index.
     *
     * The caller may choose to skip the document, or substitute an alternate document.
     * e.g. file name only
     *
     * @param r
     * @return
     */
    @Override
    public boolean accept(WebdavResource r)
    {
        try
        {
            String contentType = r.getContentType();
            if (isImage(contentType) || isZip(contentType))
                return false;
            FileStream fs = r.getFileStream(User.getSearchUser());
            if (null == fs)
                return false;
            try
            {
                if (isTooBig(fs,contentType))
                {
                    // give labkey parsers a chance to accept the file
                    DocumentParser p = detectParser(r, null);
                    return p != null;
                }
                return true;
            }
            finally
            {
                fs.closeInputStream();
            }
        }
        catch (IOException x)
        {
            return false;
        }
    }


    private boolean isTooBig(FileStream fs, String contentType) throws IOException
    {
        long size = fs.getSize();

        // .xlsx files are zipped with about a 5:1 ratio -- they bloat in memory
        if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType))
            size = size * 5;

        return size > FILE_SIZE_LIMIT;
    }


    DocumentParser detectParser(WebdavResource r, InputStream in)
    {
        InputStream is = in;
        try
        {
            if (null == is)
            {
                is = r.getInputStream(User.getSearchUser());
                if (null == is)
                    return null;
            }
            is.skip(Long.MIN_VALUE);
            byte[] header = FileUtil.readHeader(is, 8*1024);
            for (DocumentParser p : _documentParsers)
            {
                if (p.detect(r, header))
                    return p;
            }
            return null;
        }
        catch (IOException x)
        {
            return null;
        }
        finally
        {
            if (is != in)
                IOUtils.closeQuietly(is);
        }
    }


    static final AutoDetectParser _parser = new AutoDetectParser();

    private Parser getParser()
    {
        return _parser;
    }

    
    // See https://issues.apache.org/jira/browse/TIKA-374 for status of a Tika concurrency problem that forces
    // us to use single-threaded pre-processing.
    @Override
    protected boolean isPreprocessThreadSafe()
    {
        return false;
    }
    

    private String getNameToLog(WebdavResource r)
    {
        File f = r.getFile();

        if (null != f)
            return f.getPath();

        // If it's not a file in the file system then return the resource path and the container path
        String name = r.getPath().toString();
        Container c = ContainerManager.getForId(r.getContainerId());
        String url = r.getExecuteHref(null);

        return name + (null != c ? " (folder: " + c.getPath() + ")" : "") + " (" + url + ")";
    }

    private void logAsPreProcessingException(WebdavResource r, Throwable e)
    {
        //noinspection ThrowableInstanceNeverThrown
        ExceptionUtil.logExceptionToMothership(null, new PreProcessingException(getNameToLog(r), e));
    }

    private void logAsWarning(WebdavResource r, Exception e)
    {
        logAsWarning(r, e.getMessage());
    }

    private void logAsWarning(WebdavResource r, String message)
    {
        _log.warn("Can't index file \"" + getNameToLog(r) + "\" due to: " + message);
    }

    private static class PreProcessingException extends Exception
    {
        private PreProcessingException(String name, Throwable cause)
        {
            super(name, cause);
        }
    }

    private static final String[] INTERESTING_PROP_NAMES = new String[] {
        Metadata.TITLE,
        Metadata.AUTHOR,
        Metadata.KEYWORDS,
        Metadata.COMMENTS,
        Metadata.NOTES,
        Metadata.COMPANY,
        Metadata.PUBLISHER
    };

    public String getInterestingMetadataProperties(Metadata metadata)
    {
        StringBuilder sb = new StringBuilder();

        for (String key : INTERESTING_PROP_NAMES)
        {
            String value = metadata.get(key);

            if (null != value)
            {
                sb.append(" ");
                sb.append(value);
            }
        }

        return sb.toString();
    }

    private static final int SUMMARY_LENGTH = 400;
    private static final Pattern TITLE_STRIPPING_PATTERN = Pattern.compile(": /" + GUID.guidRegEx);
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[\\s/]");  // Any whitespace character or slash

    private String extractSummary(String body, String title)
    {
        title = TITLE_STRIPPING_PATTERN.matcher(title).replaceAll("");

        if (body.startsWith(title))
        {
            body = body.substring(title.length());
            body = StringUtils.stripStart(body, "/. \n\r\t");
        }

        if (body.length() <= SUMMARY_LENGTH)
            return body;

        Matcher wordSplitter = SEPARATOR_PATTERN.matcher(body);

        if (!wordSplitter.find(SUMMARY_LENGTH - 1))
            return body.substring(0, SUMMARY_LENGTH) + "...";
        else
            return body.substring(0, wordSplitter.start()) + "...";
    }


    protected void deleteDocument(String id)
    {
        _index.deleteDocument(id);
    }


    @Override
    protected void deleteDocumentsForPrefix(String prefix)
    {
        Term term = new Term(FIELD_NAMES.uniqueId.toString(), prefix + "*");
        Query query = new WildcardQuery(term);

        try
        {
            // Run the query before delete, but only if Log4J debug level is set
            if (_log.isDebugEnabled())
            {
                _log.debug("Deleting " + getDocCount(query) + " docs with prefix \"" + prefix + "\"");
            }

            _index.deleteQuery(query);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private int getDocCount(Query query) throws IOException
    {
        LabKeyIndexSearcher searcher = _index.getSearcher();
        TopDocs docs = searcher.search(query, 1);
        _index.releaseSearcher(searcher);
        return docs.totalHits;
    }

    protected void index(String id, WebdavResource r, Map preprocessMap)
    {
        try
        {
            Document doc = (Document)preprocessMap.get(Document.class);
            _index.index(r.getDocumentId(), doc);
        }
        catch(Throwable e)
        {
            _log.error("Indexing error with " + id, e);
        }
    }


    @Override
    protected void deleteIndexedContainer(String id)
    {
        try
        {
            Query query = new TermQuery(new Term(FIELD_NAMES.container.toString(), id));

            // Run the query before delete, but only if Log4J debug level is set
            if (_log.isDebugEnabled())
            {
                _log.debug("Deleting " + getDocCount(query) + " docs from container " + id);
            }

            _index.deleteQuery(query);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    protected void commitIndex()
    {
        _index.commit();
    }


    public SearchHit find(String id) throws IOException
    {
        LabKeyIndexSearcher searcher = _index.getSearcher();

        try
        {
            TermQuery query = new TermQuery(new Term(FIELD_NAMES.uniqueId.toString(), id));
            TopDocs topDocs = searcher.search(query, null, 1);
            SearchResult result = createSearchResult(0, 1, topDocs, searcher);
            if (result.hits.size() != 1)
                return null;
            return result.hits.get(0);
        }
        finally
        {
            _index.releaseSearcher(searcher);
        }
    }
    

    // Always search title and body, but boost title
    private static final String[] standardFields = new String[]{FIELD_NAMES.title.toString(), FIELD_NAMES.body.toString()};
    private static final Float[] standardBoosts = new Float[]{2.0f, 1.0f};
    private static final Map<String, Float> boosts = new HashMap<String, Float>();

    static
    {
        for (int i = 0; i < standardFields.length; i++)
            boosts.put(standardFields[i], standardBoosts[i]);
    }

    public WebPartView getSearchView(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink, boolean isWebpart)
    {
        return new SearchWebPart(includeSubfolders, textBoxWidth, includeHelpLink, isWebpart);
    }

    public SearchResult search(String queryString, @Nullable List<SearchCategory> categories, User user, Container root, Container currentContainer, boolean recursive, int offset, int limit) throws IOException
    {
        InvocationTimer<SEARCH_PHASE> iTimer = TIMER.getInvocationTimer();

        String sort = null;  // TODO: add sort parameter
        int hitsToRetrieve = offset + limit;
        boolean requireCategories = (null != categories);

        if (!requireCategories)
        {
            iTimer.setPhase(SEARCH_PHASE.determineParticipantId);
            // Boost "subject" results if this is a participant id
            if (isParticipantId(user, StringUtils.strip(queryString, " +-")))
                categories = this.getCategory("subject");
        }

        iTimer.setPhase(SEARCH_PHASE.createQuery);

        Query query;

        try
        {
            QueryParser queryParser = new MultiFieldQueryParser(LUCENE_VERSION, standardFields, _analyzer, boosts);
            query = queryParser.parse(queryString);
        }
        catch (ParseException x)
        {
            // The default ParseException message is quite awful, not suitable for users.  Unfortunately, the exception
            // doesn't provide the useful bits individually, so we have to parse the message to get them. #10596
            LuceneMessageParser mp = new LuceneMessageParser(x.getMessage());

            if (mp.isParseable())
            {
                String message;
                int problemLocation;

                if ("<EOF>".equals(mp.getEncountered()))
                {
                    message = PageFlowUtil.filter("Query string is incomplete");
                    problemLocation = queryString.length();
                }
                else
                {
                    if (1 == mp.getLine())
                    {
                        message = "Problem character is <span " + SearchUtils.getHighlightStyle() + ">highlighted</span>";
                        problemLocation = mp.getColumn();
                    }
                    else
                    {
                        // Multiline query?!?  Don't try to highlight, just report the location (1-based)
                        message = PageFlowUtil.filter("Problem at line " + (mp.getLine() + 1) + ", character location " + (mp.getColumn() + 1));
                        problemLocation = -1;
                    }
                }

                throw new HtmlParseException(message, queryString, problemLocation);
            }
            else
            {
                throw new IOException(x.getMessage());  // Default message starts with "Cannot parse '<query string>':"
            }
        }
        catch (IllegalArgumentException x)
        {
            throw new IOException(SearchUtils.getStandardPrefix(queryString) + x.getMessage());
        }

        if (null != categories)
        {
            BooleanQuery bq = new BooleanQuery();
            bq.add(query, BooleanClause.Occur.MUST);
            Iterator itr = categories.iterator();

            if (requireCategories)
            {
                BooleanQuery requiresBQ = new BooleanQuery();
                
                while (itr.hasNext())
                {
                    Query categoryQuery = new TermQuery(new Term(SearchService.PROPERTY.categories.toString(), itr.next().toString().toLowerCase()));
                    requiresBQ.add(categoryQuery, BooleanClause.Occur.SHOULD);
                }

                bq.add(requiresBQ, BooleanClause.Occur.MUST);
            }
            else
            {
                while (itr.hasNext())
                {
                    Query categoryQuery = new TermQuery(new Term(SearchService.PROPERTY.categories.toString(), itr.next().toString().toLowerCase()));
                    categoryQuery.setBoost(3.0f);
                    bq.add(categoryQuery, BooleanClause.Occur.SHOULD);
                }
            }
            query = bq;
        }

        LabKeyIndexSearcher searcher = null;

        try
        {
            iTimer.setPhase(SEARCH_PHASE.buildSecurityFilter);
            Filter securityFilter = user==User.getSearchUser() ? null : new SecurityFilter(user, root, currentContainer, recursive);

            iTimer.setPhase(SEARCH_PHASE.search);
            searcher = _index.getSearcher();

            TopDocs topDocs;

            if (null == sort)
                topDocs = searcher.search(query, securityFilter, hitsToRetrieve);
            else
                topDocs = searcher.search(query, securityFilter, hitsToRetrieve, new Sort(new SortField(sort, SortField.STRING)));

            iTimer.setPhase(SEARCH_PHASE.processHits);
            return createSearchResult(offset, hitsToRetrieve, topDocs, searcher);
        }
        finally
        {
            TIMER.releaseInvocationTimer(iTimer);

            if (null != searcher)
                _index.releaseSearcher(searcher);
        }
    }


    private SearchResult createSearchResult(int offset, int hitsToRetrieve, TopDocs topDocs, IndexSearcher searcher)
            throws IOException
    {
        ScoreDoc[] hits = topDocs.scoreDocs;

        List<SearchHit> ret = new LinkedList<SearchHit>();

        for (int i = offset; i < Math.min(hitsToRetrieve, hits.length); i++)
        {
            ScoreDoc scoreDoc = hits[i];
            Document doc = searcher.doc(scoreDoc.doc);

            SearchHit hit = new SearchHit();
            hit.container = doc.get(FIELD_NAMES.container.toString());
            hit.docid = doc.get(FIELD_NAMES.uniqueId.toString());
            hit.summary = doc.get(FIELD_NAMES.summary.toString());
            hit.url = doc.get(FIELD_NAMES.url.toString());

            // BUG patch see 10734 : Bad URLs for files in search results
            // this is only a partial fix, need to rebuild index
            if (hit.url.contains("/%40files?renderAs=DEFAULT/"))
            {
                int in = hit.url.indexOf("?renderAs=DEFAULT/");
                hit.url = hit.url.substring(0,in) + hit.url.substring(in+"?renderAs=DEFAULT".length()) + "?renderAs=DEFAULT";
            }
            if (null != hit.docid)
            {
                String docid = "_docid=" + PageFlowUtil.encode(hit.docid);
                hit.url = hit.url + (-1 == hit.url.indexOf("?") ? "?" : "&") + docid;
            }

            hit.displayTitle = doc.get(FIELD_NAMES.displayTitle.toString());

            // No display title, try title
            if (StringUtils.isBlank(hit.displayTitle))
                hit.displayTitle = doc.get(FIELD_NAMES.title.toString());

            // No title at all... just use URL
            if (StringUtils.isBlank(hit.displayTitle))
                hit.displayTitle = hit.url;

            // UNDONE FIELD_NAMES.navtree
            hit.navtrail = doc.get(FIELD_NAMES.navtrail.toString());
            ret.add(hit);
        }

        SearchResult result = new SearchResult();
        result.totalHits = topDocs.totalHits;
        result.hits = ret;
        return result;
    }


    @Override
    public boolean hasExternalIndexPermission(User user)
    {
        if (null == _externalIndex)
            return false;

        SecurityPolicy policy = SecurityManager.getPolicy(_externalIndex);

        return policy.hasPermission(user, ReadPermission.class);
    }


    @Override
    public SearchResult searchExternal(String queryString, int offset, int limit) throws IOException
    {
        if (null == _externalIndex)
            throw new IllegalStateException("External index is not defined");

        int hitsToRetrieve = offset + limit;
        LabKeyIndexSearcher searcher = _externalIndex.getSearcher();

        try
        {
            QueryParser queryParser = new MultiFieldQueryParser(Version.LUCENE_30, new String[]{"content", "title"}, _externalIndex.getAnalyzer());
            Query query = queryParser.parse(queryString);
            TopDocs docs = searcher.search(query, hitsToRetrieve);
            return createSearchResult(offset, hitsToRetrieve, docs, searcher);
        }
        catch (ParseException x)
        {
            throw new IOException(x.getMessage());
        }
        finally
        {
            _externalIndex.releaseSearcher(searcher);
        }
    }


    protected void shutDown()
    {
        commit();

        try
        {
            _index.close();
        }
        catch (Exception e)
        {
            _log.error("Closing index", e);
        }

        try
        {
            if (null != _externalIndex)
                _externalIndex.close();
        }
        catch (Exception e)
        {
            _log.error("Closing external index", e);
        }
    }


    @Override
    public Map<String, Object> getIndexerStats()
    {
        Map<String, Object> map = new LinkedHashMap<String, Object>();

        try
        {
            LabKeyIndexSearcher is = null;

            try
            {
                is = _index.getSearcher();
                map.put("Indexed Documents", is.getIndexReader().numDocs());
            }
            finally
            {
                if (null != is)
                    _index.releaseSearcher(is);
            }
        }
        catch (IOException x)
        {

        }

        map.putAll(super.getIndexerStats());
        return map;
    }


    @Override
    public Map<String, Double> getSearchStats()
    {
        return TIMER.getTimes();
    }

    private boolean isImage(String contentType)
    {
        return contentType.startsWith("image/");
    }
    

    private boolean isZip(String contentType)
    {
        if (contentType.startsWith("application/x-"))
        {
            String type = contentType.substring("application/x-".length());
            if (type.contains("zip"))
                return true;
            if (type.contains("tar"))
                return true;
            if (type.contains("compress"))
                return true;
            if (type.contains("archive"))
                return true;
        }
        return false;
    }

    @Override
    public void maintenance()
    {
        super.maintenance();
        _index.optimize();
    }

    @Override
    public List<SecurableResource> getSecurableResources(User user)
    {
        if (null != _externalIndex)
        {
            SecurityPolicy policy = SecurityManager.getPolicy(_externalIndex);
            if (policy.hasPermission(user, AdminPermission.class))
                return Collections.singletonList((SecurableResource)_externalIndex);
        }

        return Collections.emptyList();
    }
}