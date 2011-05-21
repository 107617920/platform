/*
 * Copyright (c) 2005-2011 Fred Hutchinson Cancer Research Center
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
package org.labkey.wiki;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerService;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.MacroProvider;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.wiki.model.RadeoxMacroProxy;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.wiki.model.WikiView;
import org.labkey.wiki.renderer.HtmlRenderer;
import org.labkey.wiki.renderer.PlainTextRenderer;
import org.labkey.wiki.renderer.RadeoxRenderer;
import org.radeox.macro.MacroRepository;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * User: mbellew
 * Date: Mar 10, 2005
 * Time: 1:27:36 PM
 */
public class WikiManager implements WikiService
{
    static final WikiManager _instance = new WikiManager();
    public static WikiManager get()
    {
        return _instance;
    }


    private static final Logger LOG = Logger.getLogger(WikiManager.class);

    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("wiki", "Wiki Pages");

    /* service/schema dependencies */
    private CommSchema comm = CommSchema.getInstance();
    private CoreSchema core = CoreSchema.getInstance();

    private final AttachmentService.Service _attachmentService = ServiceRegistry.get(AttachmentService.Service.class);
    private final SearchService _searchService = ServiceRegistry.get(SearchService.class);
    private final DiscussionService.Service _discussionService = ServiceRegistry.get(DiscussionService.Service.class);
    private final ContainerService _containerService = ServiceRegistry.get(ContainerService.class);

    private WikiManager()
    {
    }

    AttachmentService.Service getAttachmentService()
    {
        return _attachmentService;
    }

    SearchService getSearchService()
    {
        return _searchService;
    }

    DiscussionService.Service getDiscussionService()
    {
        return _discussionService;
    }

    ContainerService getContainerService()
    {
        return _containerService;
    }

    // Used to verify that entityId is a wiki and belongs in the specified container
    public Wiki getWikiByEntityId(Container c, String entityId) throws SQLException
    {
        if (null == c || c.getId().length() == 0 || null == entityId || entityId.length() == 0)
            return null;

        Wiki[] wikis = Table.select(comm.getTableInfoPages(),
                Table.ALL_COLUMNS,
                new SimpleFilter("Container", c.getId()).addCondition("EntityId", entityId),
                null, Wiki.class);

        if (0 == wikis.length)
            return null;
        else
            return wikis[0];
    }


    public boolean insertWiki(User user, Container c, Wiki wikiInsert, WikiVersion wikiversion, List<AttachmentFile> files)
            throws SQLException, IOException
    {
        DbScope scope = comm.getSchema().getScope();

        try
        {
            scope.ensureTransaction();

            //transact insert of wiki page, new version, and any attachments
            wikiInsert.beforeInsert(user, c.getId());
            wikiInsert.setPageVersionId(null);
            Table.insert(user, comm.getTableInfoPages(), wikiInsert);
            String entityId = wikiInsert.getEntityId();

            //insert initial version for this page
            wikiversion.setPageEntityId(entityId);
            wikiversion.setCreated(wikiInsert.getCreated());
            wikiversion.setCreatedBy(wikiInsert.getCreatedBy());
            wikiversion.setVersion(1);
            Table.insert(user, comm.getTableInfoPageVersions(), wikiversion);

            //get rowid for newly inserted version
            wikiversion = WikiSelectManager.getVersion(wikiInsert, 1);

            //store initial version reference in Pages table
            wikiInsert.setPageVersionId(wikiversion.getRowId());
            Table.update(user, comm.getTableInfoPages(), wikiInsert, wikiInsert.getEntityId());

            getAttachmentService().addAttachments(wikiInsert, files, user);

            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();

            WikiCache.uncache(c, wikiInsert, true);

            indexWiki(wikiInsert);
        }

        return true;
    }


    public boolean updateWiki(User user, Wiki wikiNew, WikiVersion versionNew)
            throws SQLException
    {
        DbScope scope = comm.getSchema().getScope();
        Container c = wikiNew.lookupContainer();
        boolean uncacheAllContent = true;
        Wiki wikiOld = null;

        try
        {
            //transact wiki update and version insert
            scope.ensureTransaction();

            //if name, title, parent, & sort order are all still the same,
            //we don't need to uncache all wikis, only the wiki being updated
            //NOTE: getWikiByEntityId does not use the cache, so we'll get a fresh copy from the database
            wikiOld = getWikiByEntityId(c, wikiNew.getEntityId());
            WikiVersion versionOld = wikiOld.getLatestVersion();
            String oldTitle = StringUtils.trimToEmpty(versionOld.getTitle().getSource());
            boolean rename = !wikiOld.getName().equals(wikiNew.getName());

            uncacheAllContent = rename
                    || wikiOld.getParent() != wikiNew.getParent()
                    || wikiOld.getDisplayOrder() != wikiNew.getDisplayOrder()
                    || (null != versionNew && !oldTitle.equals(versionNew.getTitle().getSource()));

            //update Pages table
            //UNDONE: should take RowId, not EntityId
            Table.update(user, comm.getTableInfoPages(), wikiNew, wikiNew.getEntityId());

            if (versionNew != null)
            {
                String entityId = wikiNew.getEntityId();
                versionNew.setPageEntityId(entityId);
                versionNew.setCreated(new Date(System.currentTimeMillis()));
                versionNew.setCreatedBy(user.getUserId());
                //get version number for new version
                versionNew.setVersion(WikiSelectManager.getNextVersionNumber(wikiNew));
                //insert initial version for this page
                versionNew = Table.insert(user, comm.getTableInfoPageVersions(), versionNew);

                //update version reference in Pages table.
                wikiNew.setPageVersionId(versionNew.getRowId());
                Table.update(user, comm.getTableInfoPages(), wikiNew, wikiNew.getEntityId());
            }

            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();

            // TODO: unindexWiki()... especially in rename case?

            if (null != wikiNew)
            {
                // Always uncache the new one (even in rename case -- we've probably cached a miss under the new name)
                WikiCache.uncache(c, wikiNew, false);
                indexWiki(wikiNew);
            }

            // Uncache the old wiki #12249
            if (null != wikiOld)
                WikiCache.uncache(c, wikiOld, uncacheAllContent);
        }

        return true;
    }



    public void deleteWiki(User user, Container c, Wiki wiki) throws SQLException
    {
        //shift children to new parent
        reparent(user, wiki);

        DbScope scope = comm.getSchema().getScope();

        try
        {
            //transact deletion of wiki, version, attachments, and discussions
            scope.ensureTransaction();

            wiki.setPageVersionId(null);
            Table.update(user, comm.getTableInfoPages(), wiki, wiki.getEntityId());
            Table.delete(comm.getTableInfoPageVersions(),
                    new SimpleFilter("pageentityId", wiki.getEntityId()));
            Table.delete(comm.getTableInfoPages(),
                    new SimpleFilter("entityId", wiki.getEntityId()));

            getAttachmentService().deleteAttachments(wiki);

            if (null != getDiscussionService())
                getDiscussionService().deleteDiscussions(c, wiki.getEntityId(), user);

            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();

            unindexWiki(wiki.getEntityId());
        }

        WikiCache.uncache(c, wiki, true);
    }


    private void reparent(User user, Wiki wiki) throws SQLException
    {
        //shift any children upward so they are not orphaned

        //get page's children
        List<Wiki> children = wiki.children();

        if (children.size() > 0)
        {
            Wiki parent = wiki.getParentWiki();
            int parentId = -1;
            float wikiDisplay = wiki.getDisplayOrder();
            Wiki nextWiki = null;

            //if page being deleted is not at root, get id and display order of its parent
            if (null != parent)
                parentId = parent.getRowId();

            //get page's siblings (children of its parent)
            List<Wiki> siblings = WikiSelectManager.getChildWikis(wiki.lookupContainer(), parentId);

            //find parent wiki page in sibling list, and determine its position (based on display order)
            int wikiPosition = 0;

            for (Wiki w : siblings)
            {
                //hack: make sure we are working with the right kind of wiki object for comparison
                if (w.getEntityId().equals(wiki.getEntityId()))
                {
                    wikiPosition = siblings.indexOf(w);
                    break;
                }
            }

            //get next sibling to parent
            if (wikiPosition < siblings.size() - 1)
                nextWiki = siblings.get(wikiPosition + 1);

            //children need to fit between parent wiki and next wiki
            //increment child's order, starting with deleted page's order
            float reorder = wikiDisplay;

            for (Wiki child : children)
            {
                child.setParent(parentId);
                child.setDisplayOrder(reorder++);
                updateWiki(user, child, null);
            }

            //if there are subsequent siblings, reorder them as well.
            if (null != nextWiki)
            {
                //walk through siblings starting with page following parent
                for (int i = wikiPosition + 1; i < siblings.size(); i++)
                {
                    Wiki lowerSib = siblings.get(i);
                    lowerSib.setDisplayOrder(reorder++);
                    updateWiki(user, lowerSib, null);
                }
            }
        }
    }


    public void purgeContainer(Container c) throws SQLException
    {
        WikiCache.uncache(c);

        DbScope scope = comm.getSchema().getScope();

        try
        {
            scope.ensureTransaction();

            Object[] params = { c.getId() };
            Table.execute(comm.getSchema(), "UPDATE " + comm.getTableInfoPages() + " SET PageVersionId = NULL WHERE Container = ?", params);
            Table.execute(comm.getSchema(), "DELETE FROM " + comm.getTableInfoPageVersions() + " WHERE PageEntityId IN (SELECT EntityId FROM " + comm.getTableInfoPages() + " WHERE Container = ?)", params);

            //delete stored web part information for this container (e.g., page to display in wiki web part)
            Table.execute(Portal.getSchema(),
                    "UPDATE " + Portal.getTableInfoPortalWebParts() + " SET Properties = NULL WHERE (Name LIKE '%Wiki%') AND Properties LIKE '%" + c.getId() + "%'",
                    null);

            ContainerUtil.purgeTable(comm.getTableInfoPages(), c, null);

            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }


    public int purge() throws SQLException
    {
        return ContainerUtil.purgeTable(comm.getTableInfoPages(), null);
    }


    public FormattedHtml formatWiki(Container c, Wiki wiki, WikiVersion wikiversion) throws SQLException
    {
        String hrefPrefix = wiki.getWikiURL(WikiController.PageAction.class, HString.EMPTY).toString();

        String attachPrefix = null;
        if (null != wiki.getEntityId())
            attachPrefix = wiki.getAttachmentLink("");

        Map<HString, HString> nameTitleMap = WikiSelectManager.getNameTitleMap(c);

        //get formatter specified for this version
        WikiRenderer w = wikiversion.getRenderer(hrefPrefix, attachPrefix, nameTitleMap, wiki.getAttachments());

        return w.format(wikiversion.getBody());
    }


    public static boolean wikiNameExists(Container c, HString wikiname)
    {
        return WikiSelectManager.getWiki(c, wikiname) != null;
    }


    //copies a single wiki page
    public Wiki copyPage(User user, Container cSrc, Wiki srcPage, Container cDest, List<HString> destPageNames,
                          Map<Integer, Integer> pageIdMap, boolean fOverwrite)
            throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        //get latest version
        WikiVersion srcLatestVersion = srcPage.getLatestVersion();

        //create new wiki page
        HString srcName = srcPage.getName();
        HString destName = srcName;
        Wiki destPage = WikiSelectManager.getWiki(cDest, destName);

        //check whether name exists in destination container
        //if not overwriting, generate new name
        if (fOverwrite)
        {
            //can't overwrite if page does not exist
            if (!destPageNames.contains(destName))
                fOverwrite = false;
        }
        else
        {
            int i = 1;

            while (destPageNames.contains(destName))
                destName = srcName.concat("" + i++);
        }

        //new wiki page
        Wiki newWikiPage = null;

        if (!fOverwrite)
        {
            newWikiPage = new Wiki(cDest, destName);
            newWikiPage.setDisplayOrder(srcPage.getDisplayOrder());
            newWikiPage.setShowAttachments(srcPage.isShowAttachments());

            //look up parent page via map
            if (pageIdMap != null)
            {
                Integer destParentId = pageIdMap.get(srcPage.getParent());

                if (destParentId != null)
                    newWikiPage.setParent(destParentId);
                else
                    newWikiPage.setParent(-1);
            }
        }

        //new wiki version
        WikiVersion newWikiVersion = new WikiVersion(destName);
        newWikiVersion.setTitle(srcLatestVersion.getTitle());
        newWikiVersion.setBody(srcLatestVersion.getBody());
        newWikiVersion.setRendererTypeEnum(srcLatestVersion.getRendererTypeEnum());

        //get wiki & attachments
        Wiki wiki = WikiSelectManager.getWiki(cSrc, srcName);
        Collection<Attachment> attachments = wiki.getAttachments();
        List<AttachmentFile> files = getAttachmentService().getAttachmentFiles(wiki, attachments);

        if (fOverwrite)
        {
            updateWiki(user, destPage, newWikiVersion);
            getAttachmentService().deleteAttachments(destPage);
            getAttachmentService().addAttachments(destPage, files, user);
            // NOTE indexWiki() gets called twice in this case
            touch(destPage);
            indexWiki(destPage);
        }
        else
        {
            //insert new wiki page in destination container
            insertWiki(user, cDest, newWikiPage, newWikiVersion, files);

            //map source row id to dest row id
            if (pageIdMap != null)
            {
                pageIdMap.put(srcPage.getRowId(), newWikiPage.getRowId());
            }
        }

        return newWikiPage;
    }


    public String updateAttachments(User user, Wiki wiki, List<String> deleteNames, List<AttachmentFile> files)
            throws IOException
    {
        AttachmentService.Service attsvc = getAttachmentService();
        boolean changes = false;
        String message = null;

        //delete the attachments requested
        if (null != deleteNames && !deleteNames.isEmpty())
        {
            for (String name : deleteNames)
            {
                attsvc.deleteAttachment(wiki, name, user);
            }
            changes = true;
        }

        //add any files as attachments
        if (null != files && files.size() > 0)
        {
            try
            {
                attsvc.addAttachments(wiki, files, user);
            }
            catch (AttachmentService.DuplicateFilenameException e)
            {
                //since this is now being called ajax style with just the files, we don't
                //really need to generate an error in this case. Just add a warning
                message = e.getMessage();
            }
            catch (IOException e)
            {
                message = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            changes = true;
        }

        if (changes)
        {
            touch(wiki);
            indexWiki(wiki);
        }

        return message;
    }


    //
    // Search
    //


    void unindexWiki(String entityId)
    {
        SearchService ss = getSearchService();
        String docid = "wiki:" + entityId;
        if (null != getSearchService())
            ss.deleteResource(docid);
        // UNDONE attachment
    }
    

    void indexWiki(Wiki page)
    {
        SearchService ss = getSearchService();
        Container c = getContainerService().getForId(page.getContainerId());
        if (null != ss && null != c)
            indexWikiContainerFast(ss.defaultTask(), c, null, page.getName().getSource());
    }


    private void touch(Wiki wiki)
    {
        try
        {
            // CONSIDER: Table.touch()?
            Table.execute(comm.getSchema(), "UPDATE " + comm.getTableInfoPages() + " SET lastIndexed=null, modified=? WHERE container=? AND name=?",
                    new Object[]{new Date(), wiki.getContainerId(), wiki.getName()});
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public void setLastIndexed(Container c, String name, long ms)
    {
        try
        {
            Table.execute(comm.getSchema(),
                    "UPDATE comm.pages SET lastIndexed=? WHERE container=? AND name=?",
                    new Object[] {new Timestamp(ms), c, name}
                    );
        }
        catch (SQLException sql)
        {
            throw new RuntimeSQLException(sql);
        }
    }

    
    public void indexWikis(@NotNull final SearchService.IndexTask task, @NotNull Container c, final Date modifiedSince)
    {
        assert null != c;
        final SearchService ss = getSearchService();
        if (null == ss || null == c)
            return;

        indexWikiContainerFast(task, c, modifiedSince, null);
    }


    public void indexWikiContainerSlow(@NotNull SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        ResultSet rs = null;
        ActionURL page = new ActionURL(WikiController.PageAction.class, null);

        try
        {
            SimpleFilter f = new SimpleFilter();
            f.addCondition("container", c);
            SearchService.LastIndexedClause clause = new SearchService.LastIndexedClause(comm.getTableInfoPages(), modifiedSince, null);
            f.addCondition(clause);
            if (null != modifiedSince)
                f.addCondition("modified", modifiedSince, CompareType.GTE);

            rs = Table.select(comm.getTableInfoPages(), PageFlowUtil.set("container", "name"), f, null);

            while (rs.next())
            {
                String id = rs.getString(1);
                String name = rs.getString(2);
                ActionURL url = page.clone().setExtraPath(id).replaceParameter("name", name);
                task.addResource(searchCategory, url, SearchService.PRIORITY.item);
            }
        }
        catch (SQLException x)
        {
            LOG.error(x);
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    public void indexWikiContainerFast(@NotNull SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince, String name)
    {
        ResultSet rs = null;
        try
        {
            SQLFragment f = new SQLFragment();
            f.append("SELECT P.entityid, P.container, P.name, owner$.searchterms as owner, createdby$.searchterms as createdby, P.created, modifiedby$.searchterms as modifiedby, P.modified,")
                .append("V.title, V.body, V.renderertype\n");
            f.append("FROM comm.pages P INNER JOIN comm.pageversions V ON P.entityid=V.pageentityid and P.pageversionid=V.rowid\n")
                .append("LEFT OUTER JOIN core.usersearchterms AS owner$ ON P.createdby = owner$.userid\n")
                .append("LEFT OUTER JOIN core.usersearchterms AS createdby$ ON P.createdby = createdby$.userid\n")
                .append("LEFT OUTER JOIN core.usersearchterms AS modifiedby$ ON P.createdby = modifiedby$.userid\n");
            f.append("WHERE P.container = ?");
            f.add(c);
            SQLFragment since = new SearchService.LastIndexedClause(comm.getTableInfoPages(), modifiedSince, "P").toSQLFragment(null, comm.getSqlDialect());
            if (!since.isEmpty())
            {
                f.append(" AND ").append(since);
            }
            if (null != name)
            {
                f.append(" AND P.name = ?");
                f.add(name);
            }
            rs = Table.executeQuery(comm.getSchema(), f, 0, false, false);

            HashMap<String, AttachmentParent> ids = new HashMap<String, AttachmentParent>();
            // AGGH wiki doesn't have a title!
            HashMap<String, String> titles = new HashMap<String,String>();
            
            while (rs.next())
            {
                name = rs.getString("name");
                assert null != name;

                if (SecurityManager.TERMS_OF_USE_WIKI_NAME.equals(name))
                    continue;

                String entityId = rs.getString("entityid");
                assert null != entityId;
                String wikiTitle = rs.getString("title");
                String searchTitle;
                if (null == wikiTitle)
                    searchTitle = wikiTitle = name;
                else
                    searchTitle = wikiTitle + " " + name;   // Always search on wiki title or name
                String body = rs.getString("body");
                if (null == body)
                    body = "";
                WikiRendererType rendererType = WikiRendererType.valueOf(rs.getString("renderertype"));

                Map<String, Object> props = new HashMap<String, Object>();
                props.put(SearchService.PROPERTY.displayTitle.toString(), wikiTitle);
                props.put(SearchService.PROPERTY.searchTitle.toString(), searchTitle);

                WikiWebdavProvider.WikiPageResource r = new RenderedWikiResource(c, name, entityId, body, rendererType, props);
                task.addResource(r, SearchService.PRIORITY.item);
                if (Thread.interrupted())
                    return;
                Wiki parent = new Wiki();
                parent.setContainer(c.getId());
                parent.setEntityId(entityId);
                parent.setName(new HString(name, false));
                ids.put(entityId, parent);
                titles.put(entityId, wikiTitle);
            }

            // now attachments
            ActionURL pageUrl = new ActionURL(WikiController.PageAction.class, c);
            ActionURL downloadUrl = new ActionURL(WikiController.DownloadAction.class, c);
            
            if (!ids.isEmpty())
            {
                List<Pair<String,String>> list = getAttachmentService().listAttachmentsForIndexing(ids.keySet(), modifiedSince);

                for (Pair<String,String> pair : list)
                {
                    String entityId = pair.first;
                    String documentName = pair.second;
                    Wiki parent = (Wiki)ids.get(entityId);

                    ActionURL wikiUrl = pageUrl.clone().addParameter("name", parent.getName());
                    ActionURL attachmentURL = downloadUrl.clone()
                            .replaceParameter("entityId",entityId)
                            .replaceParameter("name",documentName);
                    // UNDONE: set title to make LuceneSearchServiceImpl work
                    String displayTitle = "\"" + documentName + "\" attached to page \"" + titles.get(entityId) + "\"";
                    WebdavResource attachmentRes = getAttachmentService().getDocumentResource(
                            new Path(entityId,documentName),
                            attachmentURL, displayTitle,
                            parent,
                            documentName, searchCategory);

                    NavTree t = new NavTree("wiki page", wikiUrl);
                    String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
                    attachmentRes.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
                    task.addResource(attachmentRes, SearchService.PRIORITY.item);
                }
            }
        }
        catch (SQLException x)
        {
            LOG.error(x);
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    //
    // WikiService
    //

        public static WikiRendererType DEFAULT_WIKI_RENDERER_TYPE = WikiRendererType.HTML;
    public static WikiRendererType DEFAULT_MESSAGE_RENDERER_TYPE = WikiRendererType.TEXT_WITH_LINKS;

    private Map<String, MacroProvider> providers = new HashMap<String, MacroProvider>();

    public String getHtml(Container c, String name)
    {
        if (null == c || null == name)
            return null;

        try
        {
            Wiki wiki = WikiSelectManager.getWiki(c, new HString(name));
            if (null == wiki)
                return null;
            WikiVersion version = wiki.getLatestVersion();
            return version.getHtml(c, wiki);
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    @Deprecated
    public WebPartView getView(Container c, String name, boolean forceRefresh, boolean renderContentOnly)
    {
        return getView(c, name, renderContentOnly);
    }

    @Override
    @Deprecated
    public String getHtml(Container c, String name, boolean forceRefresh)
    {
        return getHtml(c, name);
    }

    public void insertWiki(User user, Container c, String name, String body, WikiRendererType renderType, String title)
    {
        Wiki wiki = new Wiki(c, new HString(name));
        WikiVersion wikiversion = new WikiVersion();
        wikiversion.setTitle(new HString(title));

        wikiversion.setBody(body);

        if (renderType == null)
            renderType = getDefaultWikiRendererType();

        wikiversion.setRendererTypeEnum(renderType);

        try
        {
            insertWiki(user, c, wiki, wikiversion, null);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (AttachmentService.DuplicateFilenameException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void registerMacroProvider(String name, MacroProvider provider)
    {
        providers.put(name, provider);
        MacroRepository repository = MacroRepository.getInstance();
        repository.put(name, new RadeoxMacroProxy(name, provider));

    }

    //Package
    MacroProvider getMacroProvider(String name)
    {
        return providers.get(name);
    }

    public WebPartView getView(Container c, String name, boolean contentOnly)
    {
        try
        {
            if (contentOnly)
            {
                String html = getHtml(c, name);
                return null == html ? null : new HtmlView(html);
            }
            Wiki wiki = WikiSelectManager.getWiki(c, new HString(name));
            if (null == wiki)
                return null;
            WikiVersion version = wiki.getLatestVersion();
            WikiView view = new WikiView(wiki, version, true);
            return view;
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    public WikiRendererType getDefaultWikiRendererType()
    {
        return DEFAULT_WIKI_RENDERER_TYPE;
    }

    public WikiRendererType getDefaultMessageRendererType()
    {
        return DEFAULT_MESSAGE_RENDERER_TYPE;
    }

    public WikiRenderer getRenderer(WikiRendererType rendererType)
    {
        return getRenderer(rendererType, null, null, null, null);
    }

    @Override
    public WikiRenderer getRenderer(WikiRendererType rendererType, String attachPrefix, Collection<? extends Attachment> attachments)
    {
        return getRenderer(rendererType, null, attachPrefix, null, attachments);
    }

    public WikiRenderer getRenderer(WikiRendererType rendererType, String hrefPrefix,
                                    String attachPrefix, Map<HString, HString> nameTitleMap,
                                    Collection<? extends Attachment> attachments)
    {
        WikiRenderer renderer;

        switch (rendererType)
        {
            case RADEOX:
                renderer = new RadeoxRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
                break;
            case HTML:
                renderer = new HtmlRenderer(hrefPrefix, attachPrefix, nameTitleMap, attachments);
                break;
            case TEXT_WITH_LINKS:
                renderer = new PlainTextRenderer();
                break;
            default:
                renderer = new RadeoxRenderer(null, attachPrefix, null, attachments);
        }

        return renderer;
    }


    public List<String> getNames(Container c)
    {
        List<HString> l = WikiSelectManager.getPageNames(c);
        ArrayList<String> ret = new ArrayList<String>();
        for (HString h : l)
            ret.add(h.getSource());
        return ret;
    }

    public static class TestCase extends Assert
    {
        WikiManager _m = null;

        @Before
        public void setup()
        {
            _m = new WikiManager();
        }
        
        @Test
        public void testSchema()
        {
            assertNotNull("couldn't find table Pages", _m.comm.getTableInfoPages());
            assertNotNull(_m.comm.getTableInfoPages().getColumn("Container"));
            assertNotNull(_m.comm.getTableInfoPages().getColumn("EntityId"));
            assertNotNull(_m.comm.getTableInfoPages().getColumn("Name"));


            assertNotNull("couldn't find table PageVersions", _m.comm.getTableInfoPageVersions());
            assertNotNull(_m.comm.getTableInfoPageVersions().getColumn("PageEntityId"));
            assertNotNull(_m.comm.getTableInfoPageVersions().getColumn("Title"));
            assertNotNull(_m.comm.getTableInfoPageVersions().getColumn("Body"));
            assertNotNull(_m.comm.getTableInfoPageVersions().getColumn("Version"));
        }


        private void purgePages(Container c, boolean verifyEmpty) throws SQLException
        {
            // TODO this belongs in attachment service!
            String deleteDocuments = "DELETE FROM " + _m.core.getTableInfoDocuments() + " WHERE Container = ? AND Parent IN (SELECT EntityId FROM " + _m.comm.getTableInfoPages() + " WHERE Container = ?)";
            int docs = Table.execute(_m.comm.getSchema(), deleteDocuments, new Object[]{c.getId(), c.getId()});

            String updatePages = "UPDATE " + _m.comm.getTableInfoPages() + " SET PageVersionId = null WHERE Container = ?";
            Table.execute(_m.comm.getSchema(), updatePages, new Object[]{c.getId()});

            String deletePageVersions = "DELETE FROM " + _m.comm.getTableInfoPageVersions() + " WHERE PageEntityId IN (SELECT EntityId FROM " + _m.comm.getTableInfoPages() + " WHERE Container = ?)";
            int pageVersions = Table.execute(_m.comm.getSchema(), deletePageVersions, new Object[]{c.getId()});

            String deletePages = "DELETE FROM " + _m.comm.getTableInfoPages() + " WHERE Container = ?";
            int pages = Table.execute(_m.comm.getSchema(), deletePages, new Object[]{c.getId()});

            if (verifyEmpty)
            {
                assertEquals(0, docs);
                assertEquals(0, pageVersions);
                assertEquals(0, pages);
            }
        }


        @Test
        public void testWiki()
                throws IOException, SQLException, ServletException, AttachmentService.DuplicateFilenameException
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();

            purgePages(c, false);

            //
            // CREATE
            //
            Wiki wikiA = new Wiki(c, new HString("pageA", false));
            WikiVersion wikiversion = new WikiVersion();
            wikiversion.setTitle(new HString("Topic A", false));
            wikiversion.setBody("[pageA]");

            _m.insertWiki(user, c, wikiA, wikiversion, null);

            // verify objects
            wikiA = WikiSelectManager.getWikiFromDatabase(c, new HString("pageA", false));
            wikiversion = WikiVersionCache.getVersion(c, wikiA.getPageVersionId());
            assertTrue(HString.eq("Topic A", wikiversion.getTitle()));

            assertNull(WikiSelectManager.getWikiFromDatabase(c, new HString("pageNA", false)));

            //
            // DELETE
            //
            _m.deleteWiki(user, c, wikiA);

            // verify
            assertNull(WikiSelectManager.getWikiFromDatabase(c, new HString("pageA", false)));

            purgePages(c, true);
        }
    }
}
