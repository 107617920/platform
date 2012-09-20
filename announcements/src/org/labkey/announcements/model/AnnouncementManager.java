/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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
package org.labkey.announcements.model;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.announcements.EmailNotificationPage;
import org.labkey.announcements.config.AnnouncementEmailConfig;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: mbellew
 * Date: Mar 11, 2005
 * Time: 10:08:26 AM
 */
public class AnnouncementManager
{
    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("message", "Messages");

    private static final CommSchema _comm = CommSchema.getInstance();
    private static final CoreSchema _core = CoreSchema.getInstance();

    public static final int EMAIL_PREFERENCE_DEFAULT = -1;
    public static final int EMAIL_PREFERENCE_NONE = 0;
    public static final int EMAIL_PREFERENCE_ALL = 1;
    public static final int EMAIL_PREFERENCE_MINE = 2;    //Only threads I've posted to or where I'm on the member list
    public static final int EMAIL_PREFERENCE_MASK = 255;

    public static final int EMAIL_NOTIFICATION_TYPE_DIGEST = 256; // If this bit is set, send daily digest instead of individual email for each post

    public static final int EMAIL_DEFAULT_OPTION = EMAIL_PREFERENCE_MINE;

    private static Logger _log = Logger.getLogger(AnnouncementManager.class);

    private AnnouncementManager()
    {
    }

    protected static void attachResponses(Container c, AnnouncementModel... announcementModels)
    {
        for (AnnouncementModel announcementModel : announcementModels)
        {
            AnnouncementModel[] res = getAnnouncements(c, announcementModel.getEntityId());
            announcementModel.setResponses(Arrays.asList(res));
        }
    }


    protected static void attachMemberLists(AnnouncementModel... announcementModels)
    {
        for (AnnouncementModel announcementModel : announcementModels)
            announcementModel.setMemberList(getMemberList(announcementModel));
    }


    // Get first rowlimit threads in this container, filtered using filter
    public static Pair<AnnouncementModel[], Boolean> getAnnouncements(Container c, SimpleFilter filter, Sort sort, int rowLimit)
    {
        filter.addCondition("Container", c.getId());
        AnnouncementModel[] recent = new TableSelector(_comm.getTableInfoThreads(), filter, sort).setMaxRows(rowLimit + 1).getArray(AnnouncementModel.class);

        Boolean limited = (recent.length > rowLimit);

        if (limited)
            recent = ArrayUtils.subarray(recent, 0, rowLimit);

        return new Pair<AnnouncementModel[], Boolean>(recent, limited);
    }

    // marker for non fully loaded announcementModel
    public static class BareAnnouncementModel extends AnnouncementModel
    {
    }


    // Get all threads in this container, filtered using filter, no attachments, no responses
    public static AnnouncementModel[] getBareAnnouncements(Container c, SimpleFilter filter, Sort sort)
    {
        filter.addCondition("Container", c.getId());

        return new TableSelector(_comm.getTableInfoThreads(), filter, sort).getArray(BareAnnouncementModel.class);
    }

    // Return a list of announcementModels from a set of containers sorted by date created (newest first).
    public static AnnouncementModel[] getAnnouncements(Container... containers)
    {
        List<String> ids = new ArrayList<String>();

        for (Container container : containers)
            ids.add(container.getId());

        SimpleFilter filter = new SimpleFilter("Container", ids, CompareType.IN);
        Sort sort = new Sort("-Created");

        return new TableSelector(_comm.getTableInfoAnnouncements(), filter, sort).getArray(AnnouncementModel.class);
    }
    
    public static AnnouncementModel[] getAnnouncements(@Nullable Container c, String parent)
    {
        SimpleFilter filter = new SimpleFilter();
        if (c != null)
        {
            filter.addCondition("container", c.getId());
        }

        if (null == parent)
            filter.addCondition("parent", null, CompareType.ISBLANK);
        else
            filter.addCondition("parent", parent);

        Sort sort = new Sort("Created");

        return new TableSelector(_comm.getTableInfoAnnouncements(), filter, sort).getArray(AnnouncementModel.class);
    }


    public static AnnouncementModel getAnnouncement(@Nullable Container c, String entityId)
    {
        return getAnnouncement(c, entityId, false);
    }


    public static AnnouncementModel getAnnouncement(@Nullable Container c, String entityId, boolean eager)
    {
        SimpleFilter filter = new SimpleFilter("EntityId", entityId);
        if (c != null)
        {
            filter.addCondition("Container", c.getId());            
        }
        Selector selector = new TableSelector(_comm.getTableInfoAnnouncements(), filter, null);
        AnnouncementModel[] ann = selector.getArray(AnnouncementModel.class);

        if (ann.length < 1)
            return null;

        if (eager)
        {
            attachResponses(c, ann);
            attachMemberLists(ann);
        }

        return ann[0];
    }

    private static MessageConfigService.ConfigTypeProvider _configProvider;
    public static MessageConfigService.ConfigTypeProvider getAnnouncementConfigProvider()
    {
        if (_configProvider == null)
        {
            _configProvider = MessageConfigService.getInstance().getConfigType(AnnouncementEmailConfig.TYPE);
            assert(_configProvider != null);
        }
        return _configProvider;
    }

    public static AnnouncementModel getAnnouncement(Container c, int rowId)
    {
        return getAnnouncement(c, rowId, INCLUDE_NOTHING);
    }

    public static void saveEmailPreference(User user, Container c, int emailPreference, String srcIdentifier) throws SQLException
    {
        saveEmailPreference(user, c, user, emailPreference, srcIdentifier);
    }

    public static synchronized void saveEmailPreference(User currentUser, Container c, User projectUser, int emailPreference, String srcIdentifier)
    {
        getAnnouncementConfigProvider().savePreference(currentUser, c, projectUser, emailPreference, srcIdentifier);
    }


    public static final int INCLUDE_NOTHING = 0;
    public static final int INCLUDE_RESPONSES = 2;
    public static final int INCLUDE_MEMBERLIST = 4;

    public static AnnouncementModel getAnnouncement(@Nullable Container c, int rowId, int mask)
    {
        SimpleFilter filter = new SimpleFilter("RowId", rowId);
        if (c != null)
        {
            filter.addCondition("Container", c.getId());
        }
        Selector selector = new TableSelector(_comm.getTableInfoAnnouncements(), filter, null);
        AnnouncementModel ann = selector.getObject(AnnouncementModel.class);

        if (null == ann)
            return null;

        // TODO: Eliminate bitmasks and proactive retrieval of responses and memberlists; replace with lazy loading (similar to wiki)
        if ((mask & INCLUDE_RESPONSES) != 0)
            attachResponses(c, ann);
        if ((mask & INCLUDE_MEMBERLIST) != 0)
            attachMemberLists(ann);

        return ann;
    }


    public static AnnouncementModel getLatestPost(Container c, AnnouncementModel parent)
    {
        SQLFragment sql = new SQLFragment( "SELECT LatestId FROM ");
        sql.append(_comm.getTableInfoThreads(), "t");
        sql.append(" WHERE RowId = ?");
        sql.add(parent.getRowId());
        Integer postId = new SqlSelector(_comm.getSchema(), sql).getObject(Integer.class);

        if (null == postId)
            throw new NotFoundException("Can't find most recent post");

        return getAnnouncement(c, postId, INCLUDE_MEMBERLIST);
    }


    public static AnnouncementModel insertAnnouncement(Container c, User user, AnnouncementModel insert, List<AttachmentFile> files) throws SQLException, IOException, MessagingException
    {
        // If no srcIdentifier is set and this is a parent message, set its source to the container
        if (insert.getDiscussionSrcIdentifier() == null && insert.getParent() == null)
        {
            insert.setDiscussionSrcIdentifier(c.getEntityId().toString());
        }
        insert.beforeInsert(user, c.getId());
        AnnouncementModel ann = Table.insert(user, _comm.getTableInfoAnnouncements(), insert);

        List<User> users = ann.getMemberList();

        // Always attach member list to initial message
        int first = (null == ann.getParent() ? ann.getRowId() : getParentRowId(ann));
        insertMemberList(user, users, first);

        AttachmentService.get().addAttachments(insert, files, user);
        indexThread(insert);

        // Send email if there's body text or an attachment.
        if (null != insert.getBody() || !insert.getAttachments().isEmpty())
        {
            String rendererTypeName = ann.getRendererType();
            WikiRendererType currentRendererType = (null == rendererTypeName ? null : WikiRendererType.valueOf(rendererTypeName));
            if (null == currentRendererType)
            {
                WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
                if (null != wikiService)
                    currentRendererType = wikiService.getDefaultMessageRendererType();
            }
            sendNotificationEmails(insert, currentRendererType, c, user);
        }
        
        return ann;
    }

    private static void sendNotificationEmails(AnnouncementModel a, WikiRendererType currentRendererType, Container c, User user) throws MessagingException
    {
        DiscussionService.Settings settings = DiscussionService.get().getSettings(c);

        boolean isResponse = null != a.getParent();
        AnnouncementModel parent = a;
        if (isResponse)
            parent = AnnouncementManager.getAnnouncement(c, a.getParent());

        //  See bug #6585 -- thread might have been deleted already
        if (null == parent)
            return;

        String messageId = "<" + a.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
        String references = messageId + " <" + parent.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";

        // Email all copies of this message in a background thread
        MailHelper.BulkEmailer emailer = new MailHelper.BulkEmailer();
        emailer.setUser(user);

        // Send a notification email to everyone on the member list.  This email will include a link that removes the user from the member list.
        IndividualEmailPrefsSelector sel = new IndividualEmailPrefsSelector(c);

        Set<User> users = sel.getNotificationUsers(a);

        if (!users.isEmpty())
        {
            List<User> memberList = getMemberList(a);

            for (User userToEmail : users)
            {
                // Make sure the user hasn't lost their permission to read in this container since they were
                // subscribed
                if (c.hasPermission(userToEmail, ReadPermission.class))
                {
                    MailHelper.ViewMessage m;
                    Permissions perm = AnnouncementsController.getPermissions(c, userToEmail, settings);

                    if (memberList.contains(userToEmail))
                    {
                        ActionURL removeMeURL = new ActionURL(AnnouncementsController.RemoveFromMemberListAction.class, c);
                        removeMeURL.addParameter("userId", String.valueOf(userToEmail.getUserId()));
                        removeMeURL.addParameter("messageId", String.valueOf(parent.getRowId()));
                        m = getMessage(c, settings, perm, parent, a, isResponse, removeMeURL.getURIString(), currentRendererType, EmailNotificationPage.Reason.memberList);
                    }
                    else
                    {
                        ActionURL changeEmailURL = AnnouncementsController.getEmailPreferencesURL(c, AnnouncementsController.getBeginURL(c), a.lookupSrcIdentifer());
                        m = getMessage(c, settings, perm, parent, a, isResponse, changeEmailURL.getURIString(), currentRendererType, EmailNotificationPage.Reason.signedUp);
                    }

                    m.setHeader("References", references);
                    m.setHeader("Message-ID", messageId);
                    emailer.addMessage(userToEmail.getEmail(), m);
                }
            }
        }

        emailer.start();
    }

    private static MailHelper.ViewMessage getMessage(Container c, DiscussionService.Settings settings, @NotNull Permissions perm, AnnouncementModel parent, AnnouncementModel a, boolean isResponse, String removeUrl, WikiRendererType currentRendererType, EmailNotificationPage.Reason reason) throws MessagingException
    {
        MailHelper.ViewMessage m = MailHelper.createMultipartViewMessage(LookAndFeelProperties.getInstance(c).getSystemEmailAddress(), null);
        m.setSubject(StringUtils.trimToEmpty(isResponse ? "RE: " + parent.getTitle() : a.getTitle()));
        HttpServletRequest request = AppProps.getInstance().createMockRequest();

        try
        {
            EmailNotificationPage page = createEmailNotificationTemplate("emailNotificationPlain.jsp", false, c, settings, perm, parent, a, removeUrl, currentRendererType, reason);
            JspView view = new JspView(page);
            view.setFrame(WebPartView.FrameType.NOT_HTML);
            m.setTemplateContent(request, view, "text/plain");

            page = createEmailNotificationTemplate("emailNotification.jsp", true, c, settings, perm, parent, a, removeUrl, currentRendererType, reason);
            view = new JspView(page);
            view.setFrame(WebPartView.FrameType.NONE);
            m.setTemplateContent(request, view, "text/html");

            return m;
        }
        catch (Exception e)
        {
            throw new MessagingException(e.getMessage(), e);
        }
    }

    private static EmailNotificationPage createEmailNotificationTemplate(String templateName, boolean includeBody, Container c, DiscussionService.Settings settings, @NotNull Permissions perm, AnnouncementModel parent,
            AnnouncementModel a, String removeUrl, WikiRendererType currentRendererType, EmailNotificationPage.Reason reason)
    {
        EmailNotificationPage page = (EmailNotificationPage) JspLoader.createPage(AnnouncementsController.class, templateName);

        page.settings = settings;
        page.threadURL = AnnouncementsController.getThreadURL(c, parent.getEntityId(), a.getRowId()).getURIString();
        page.boardPath = c.getPath();
        ActionURL boardURL = AnnouncementsController.getBeginURL(c);
        page.boardURL = boardURL.getURIString();
        page.removeUrl = removeUrl;
        page.siteURL = ActionURL.getBaseServerURL();
        page.announcementModel = a;
        page.reason = reason;
        page.includeGroups = perm.includeGroups();

        // for plain text email messages, we don't ever want to include the body since we can't translate HTML into
        // plain text
        if (includeBody && !settings.isSecure())
        {
            //format email using same renderer chosen for message
            //note that we still send all messages, including plain text, as html-formatted messages; only the inserted body text differs between renderers.
            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            if (null != wikiService)
            {
                page.body = wikiService.getFormattedHtml(currentRendererType, a.getBody());
            }
        }
        return page;
    }


    private static synchronized void insertMemberList(User user, List<User> users, int messageId) throws SQLException
    {
        // TODO: Should delete/insert only on diff
        if (null != users)
        {
            Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter("MessageId", messageId));

            for (User u : users)
                Table.insert(user, _comm.getTableInfoMemberList(), PageFlowUtil.map("MessageId", messageId, "UserId", u.getUserId()));
        }
    }


    private static int getParentRowId(AnnouncementModel ann) throws SQLException
    {
        return Table.executeSingleton(_comm.getSchema(), "SELECT RowId FROM " + _comm.getTableInfoAnnouncements() + " WHERE EntityId=?", new Object[]{ann.getParent()}, Integer.class);
    }


    private static List<User> getMemberList(AnnouncementModel ann)
    {
        SQLFragment sql;

        if (null == ann.getParent())
            sql = new SQLFragment("SELECT UserId FROM " + _comm.getTableInfoMemberList() + " WHERE MessageId = ?", ann.getRowId());
        else
            sql = new SQLFragment("SELECT UserId FROM " + _comm.getTableInfoMemberList() + " WHERE MessageId = (SELECT RowId FROM " + _comm.getTableInfoAnnouncements() + " WHERE EntityId = ?)", ann.getParent());

        Collection<Integer> userIds = new SqlSelector(_comm.getSchema(), sql).getCollection(Integer.class);
        List<User> users = new ArrayList<User>(userIds.size());

        for (int userId : userIds)
            users.add(UserManager.getUser(userId));

        return users;
    }


    public static AnnouncementModel updateAnnouncement(User user, AnnouncementModel update, List<AttachmentFile> files) throws SQLException, IOException
    {
        update.beforeUpdate(user);
        AnnouncementModel result = Table.update(user, _comm.getTableInfoAnnouncements(), update, update.getRowId());
        AttachmentService.get().addAttachments(update, files, user);
        indexThread(update);
        return result;
    }


    private static void deleteAnnouncement(AnnouncementModel ann) throws SQLException
    {
        Table.delete(_comm.getTableInfoAnnouncements(), ann.getRowId());
        AttachmentService.get().deleteAttachments(ann);
    }


    public static void deleteAnnouncement(Container c, int rowId) throws SQLException
    {
        DbSchema schema = _comm.getSchema();

        AnnouncementModel ann = null;

        try
        {
            schema.getScope().ensureTransaction();

            ann = getAnnouncement(c, rowId, INCLUDE_RESPONSES);
            if (ann != null)
            {
                deleteAnnouncement(ann);

                // Delete the member list associated with this thread
                Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter("MessageId", ann.getRowId()));

                Collection<AnnouncementModel> responses = ann.getResponses();
                if (null == responses)
                    return;
                for (AnnouncementModel response : responses)
                {
                    deleteAnnouncement(response);
                }
            }

            schema.getScope().commitTransaction();
        }
        finally
        {
            schema.getScope().closeConnection();
        }

       unindexThread(ann);
    }
    

    public static void deleteUserFromAllMemberLists(User user) throws SQLException
    {
        Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter("UserId", user.getUserId()));
    }

    public static void deleteUserFromMemberList(User user, int messageId) throws SQLException
    {
        Table.delete(_comm.getTableInfoMemberList(), new SimpleFilter("UserId", user.getUserId()).addCondition("MessageId", messageId));
    }

    public static int getUserEmailOption(Container c, User user, String srcIdentifier)
    {
        MessageConfigService.UserPreference emailPref = getAnnouncementConfigProvider().getPreference(c, user, srcIdentifier);

        //user has not yet defined email preference; return project default
        if (emailPref == null)
            return EMAIL_PREFERENCE_DEFAULT;
        else
            return emailPref.getEmailOptionId();
    }

    public static long getMessageCount(Container c) throws SQLException
    {
        return Table.executeSingleton(_comm.getSchema(), "SELECT COUNT(*) FROM " + _comm.getTableInfoAnnouncements() + " WHERE Container = ?", new Object[]{c.getId()}, Long.class);
    }

    public static MessageConfigService.NotificationOption[] getEmailOptions()
    {
        return getAnnouncementConfigProvider().getOptions();
    }

    public static void saveDefaultEmailOption(Container c, int emailOption)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(c, "defaultEmailSettings", true);
        props.put("defaultEmailOption", Integer.toString(emailOption));
        PropertyManager.saveProperties(props);
    }

    public static int getDefaultEmailOption(Container c)
    {
        Map<String, String> props = PropertyManager.getProperties(c, "defaultEmailSettings");

        if (props.isEmpty())
        {
            return EMAIL_DEFAULT_OPTION;
        }
        else
        {
            String option = props.get("defaultEmailOption");

            if (option != null)
                return Integer.parseInt(option);
            else
                throw new IllegalStateException("Invalid stored property value.");
        }
    }

    private static final String MESSAGE_BOARD_SETTINGS = "messageBoardSettings";

    public static void saveMessageBoardSettings(Container c, DiscussionService.Settings settings) throws SQLException, IllegalAccessException, InvocationTargetException
    {
        Map<String, String> props = PropertyManager.getWritableProperties(c, MESSAGE_BOARD_SETTINGS, true);
        props.clear();  // Get rid of old props (e.g., userList, see #13882)
        props.put("boardName", settings.getBoardName());
        props.put("conversationName", settings.getConversationName());
        props.put("secure", String.valueOf(settings.isSecure()));
        props.put("status", String.valueOf(settings.hasStatus()));
        props.put("expires", String.valueOf(settings.hasExpires()));
        props.put("assignedTo", String.valueOf(settings.hasAssignedTo()));
        props.put("formatPicker", String.valueOf(settings.hasFormatPicker()));
        props.put("memberList", String.valueOf(settings.hasMemberList()));
        props.put("sortOrderIndex", String.valueOf(settings.getSortOrderIndex()));
        props.put("defaultAssignedTo", null == settings.getDefaultAssignedTo() ? null : settings.getDefaultAssignedTo().toString());
        props.put("titleEditable", String.valueOf(settings.isTitleEditable()));
        props.put("includeGroups", String.valueOf(settings.includeGroups()));
        PropertyManager.saveProperties(props);
    }

    public static DiscussionService.Settings getMessageBoardSettings(Container c) throws SQLException, IllegalAccessException, InvocationTargetException
    {
        Map<String, String> props = PropertyManager.getProperties(0, c, MESSAGE_BOARD_SETTINGS);
        DiscussionService.Settings settings = new DiscussionService.Settings();
        settings.setDefaults();
        BeanUtils.populate(settings, props);
        return settings;
    }


    public static void purgeContainer(Container c)
    {
        try
        {
            // Attachments are handled by AttachmentServiceImpl
            ContainerUtil.purgeTable(_comm.getTableInfoEmailPrefs(), c, null);
            ContainerUtil.purgeTable(_comm.getTableInfoAnnouncements(), c, null);
        }
        catch (SQLException x)
        {
            _log.error("purgeContainer", x);
        }
    }

    public static void indexMessages(SearchService.IndexTask task, Container c, Date modifiedSince)
    {
        indexMessages(task, c.getId(), modifiedSince, null);
    }


    // TODO: Fix inconsistency -- cid is @NotNull and we check c != null, yet some code below allows for c == null
    public static void indexMessages(SearchService.IndexTask task, @NotNull String containerId, Date modifiedSince, String threadId)
    {
        assert null != containerId;
        if (null == containerId || (null != modifiedSince && null != threadId))
            throw new IllegalArgumentException();
        // make sure container still exists
        Container c = ContainerManager.getForId(containerId);
        if (null == c || isSecure(c))
            return;
        
        ResultSet rs = null;
        ResultSet rs2 = null;

        try
        {
            SQLFragment sql = new SQLFragment("SELECT entityId FROM " + _comm.getTableInfoThreads());
            sql.append(" WHERE container = ?");
            sql.add(containerId);
            String and = " AND ";

            if (null != threadId)
            {
                sql.append(and).append(" entityid = ?");
                sql.add(threadId);
            }
            else
            {
                SQLFragment modified = new SearchService.LastIndexedClause(_comm.getTableInfoThreads(), modifiedSince, null).toSQLFragment(null, null);
                if (!modified.isEmpty())
                    sql.append(and).append(modified);
            }

            rs = Table.executeQuery(_comm.getSchema(), sql.getSQL(), sql.getParamsArray());

            while (rs.next())
            {
                String entityId = rs.getString(1);
                _indexThread(task, containerId, entityId);
                if (Thread.interrupted())
                    return;
            }

            // Get the attachments... unfortunately, they're attached to individual announcementModels, not to the thread,
            // so we need a different query.
            // find all messages that have attachments
            sql = new SQLFragment("SELECT a.EntityId, MIN(CAST(a.Parent AS VARCHAR(36))) as parent, MIN(a.Title) AS title FROM " + _comm.getTableInfoAnnouncements() + " a INNER JOIN core.Documents d ON a.entityid = d.parent");
            sql.append("\nWHERE a.container = ?");
            sql.add(containerId);
            and = " AND ";
            if (null != threadId)
            {
                sql.append(and).append("(a.entityId = ? OR a.parent = ?)");
                sql.add(threadId);
                sql.add(threadId);
            }
            else
            {
                SQLFragment modified = new SearchService.LastIndexedClause(CoreSchema.getInstance().getTableInfoDocuments(), modifiedSince, "d").toSQLFragment(null, null);
                if (!modified.isEmpty())
                    sql.append(and).append(modified);
            }
            sql.append("\nGROUP BY a.EntityId");

            Collection<String> annIds = new HashSet<String>();
            Map<String, AnnouncementModel> map = new HashMap<String, AnnouncementModel>();

            rs2 = Table.executeQuery(_comm.getSchema(), sql.getSQL(), sql.getParamsArray());

            while (rs2.next())
            {
                String entityId = rs2.getString(1);
                String parent = rs2.getString(2);
                String title = rs2.getString(3);

                annIds.add(entityId);
                AnnouncementModel ann = new AnnouncementModel();
                ann.setEntityId(entityId);
                ann.setParent(parent);
                ann.setContainer(containerId);
                ann.setTitle(title);
                map.put(entityId, ann);
            }

            if (!annIds.isEmpty())
            {
                List<Pair<String, String>> list = AttachmentService.get().listAttachmentsForIndexing(annIds, modifiedSince);
                ActionURL url = new ActionURL(AnnouncementsController.DownloadAction.class, null);
                url.setExtraPath(containerId);
                ActionURL urlThread = new ActionURL(AnnouncementsController.ThreadAction.class, null);
                urlThread.setExtraPath(containerId);

                for (Pair<String, String> pair : list)
                {
                    String entityId = pair.first;
                    String documentName = pair.second;
                    AnnouncementModel ann = map.get(entityId);
                    ActionURL attachmentUrl = url.clone()
                            .replaceParameter("entityId", entityId)
                            .replaceParameter("name", documentName);
                    attachmentUrl.setExtraPath(ann.getContainerId());

                    String e = StringUtils.isEmpty(ann.getParent()) ? ann.getEntityId() : ann.getParent();
                    NavTree t = new NavTree("message", urlThread.clone().addParameter("entityId", e));
                    String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();

                    String displayTitle = "\"" + documentName + "\" attached to message \"" + ann.getTitle() + "\"";
                    WebdavResource attachmentRes = AttachmentService.get().getDocumentResource(
                            new Path(entityId, documentName),
                            attachmentUrl, displayTitle,
                            ann,
                            documentName, searchCategory);
                    attachmentRes.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
                    task.addResource(attachmentRes, SearchService.PRIORITY.item);
                }
            }
        }
        catch (SQLException x)
        {
            _log.error(x);
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
            ResultSetUtil.close(rs2);
        }
    }


    private static boolean isSecure(@NotNull Container c)
    {
        try
        {
            return AnnouncementManager.getMessageBoardSettings(c).isSecure();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }


    public static void setLastIndexed(String entityId, long ms)
    {
        try
        {
            Table.execute(_comm.getSchema(),
                    "UPDATE comm.announcements SET lastIndexed=? WHERE entityId=?",
                    new Timestamp(ms), entityId);
        }
        catch (SQLException sql)
        {
            throw new RuntimeSQLException(sql);
        }
    }


    private static void unindexThread(AnnouncementModel ann)
    {
        String docid = "thread:" + ann.getEntityId();
        SearchService ss = ServiceRegistry.get(SearchService.class);
        if (null != ss)
        {
            ss.deleteResource(docid);
        }
        // UNDONE attachments!
    }


    public static void _indexThread(SearchService.IndexTask task, String c, final String entityId)
    {
        String docid = "thread:" + entityId;
        ActionURL url = new ActionURL(AnnouncementsController.ThreadAction.class, null);
        url.setExtraPath(c);
        url.addParameter("entityId", entityId);
        ActionResource r = new ActionResource(searchCategory, docid, url)
        {
            @Override
            public void setLastIndexed(long ms, long modified)
            {
                AnnouncementManager.setLastIndexed(entityId, ms);
            }
        };
        task.addResource(r, SearchService.PRIORITY.item);
    }



    static void indexThread(AnnouncementModel ann)
    {
        String parent = null == ann.getParent() ? ann.getEntityId() : ann.getParent();
        String container = ann.getContainerId();
        SearchService.IndexTask task = ServiceRegistry.get().getService(SearchService.class).defaultTask();
        // indexMessages is overkill, but I don't want to duplicate the code
        indexMessages(task, container, null, parent);
    }

    
    public static class TestCase extends Assert
    {
        private void purgeAnnouncements(Container c, boolean verifyEmpty) throws SQLException
        {
            String deleteDocuments = "DELETE FROM " + _core.getTableInfoDocuments() + " WHERE Container = ? AND Parent IN (SELECT EntityId FROM " + _comm.getTableInfoAnnouncements() + " WHERE Container = ?)";
            int docs = Table.execute(_comm.getSchema(), deleteDocuments, c.getId(), c.getId());
            String deleteAnnouncements = "DELETE FROM " + _comm.getTableInfoAnnouncements() + " WHERE Container = ?";
            int pages = Table.execute(_comm.getSchema(), deleteAnnouncements, c.getId());

            if (verifyEmpty)
            {
                assertEquals(0, docs);
                assertEquals(0, pages);
            }
        }


        @Test
        public void testAnnouncements() throws Exception
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();

            purgeAnnouncements(c, false);

            int rowA;
            int rowResponse;
            {
                AnnouncementModel a = new AnnouncementModel();
                a.setTitle("new announcementModel");
                a.setBody("look at this");
                AnnouncementManager.insertAnnouncement(c, user, a, null);
                rowA = a.getRowId();
                assertTrue(0 != rowA);

                AnnouncementModel response = new AnnouncementModel();
                response.setParent(a.getEntityId());
                response.setTitle("response");
                response.setBody("bah");
                AnnouncementManager.insertAnnouncement(c, user, response, null);
                rowResponse = response.getRowId();
                assertTrue(0 != rowResponse);
            }

            {
                AnnouncementModel a = AnnouncementManager.getAnnouncement(c, rowA, INCLUDE_RESPONSES);
                assertNotNull(a);
                assertEquals("new announcementModel", a.getTitle());
                AnnouncementModel[] responses = a.getResponses().toArray(new AnnouncementModel[a.getResponses().size()]);
                assertEquals(1, responses.length);
                AnnouncementModel response = responses[0];
                assertEquals(a.getEntityId(), response.getParent());
                assertEquals("response", response.getTitle());
                assertEquals("bah", response.getBody());
            }

            {
                // this test makes more sense if/when getParent() return an AnnouncementModel
                AnnouncementModel response = AnnouncementManager.getAnnouncement(c, rowResponse);
                assertNotNull(response.getParent());
            }

            {
                AnnouncementManager.deleteAnnouncement(c, rowA);
                assertNull(AnnouncementManager.getAnnouncement(c, rowA));
            }

            // UNDONE: attachments, update, responses, ....

            purgeAnnouncements(c, true);
        }
    }
}
