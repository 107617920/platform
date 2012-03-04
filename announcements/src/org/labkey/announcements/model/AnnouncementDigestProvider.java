/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.announcements.model;

import org.apache.log4j.Logger;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.announcements.DailyDigestPage;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.message.digest.MessageDigest;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.MailHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.WebPartView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 13, 2011
 * Time: 5:29:04 PM
 */
public class AnnouncementDigestProvider implements MessageDigest.Provider
{
    private static CommSchema _comm = CommSchema.getInstance();
    private static CoreSchema _core = CoreSchema.getInstance();
    private static final Logger _log = Logger.getLogger(AnnouncementDigestProvider.class);

    // Retrieve from this container all messages with a body or attachments posted during the given timespan
    // Messages are grouped by thread and threads are sorted by earliest post within each thread
    private static final String RECENT_ANN_SQL = "SELECT annModel.* FROM\n" +
            "\t(\n" +
            "\tSELECT Thread, MIN(Created) AS Earliest FROM\n" +
            "\t\t(SELECT Created, CASE WHEN Parent IS NULL THEN EntityId ELSE Parent END AS Thread FROM " + _comm.getTableInfoAnnouncements() + " annModel LEFT OUTER JOIN\n" +
            "\t\t\t(SELECT DISTINCT(Parent) AS DocParent FROM " + _core.getTableInfoDocuments() + ") doc ON annModel.EntityId = DocParent\n" +
            "\t\t\tWHERE Container = ? AND Created >= ? AND Created < ? AND (Body IS NOT NULL OR DocParent IS NOT NULL)) x\n" +
            "\tGROUP BY Thread\n" +
            "\t) X LEFT OUTER JOIN " + _comm.getTableInfoAnnouncements() + " annModel ON Parent = Thread OR EntityId = Thread LEFT OUTER JOIN\n" +
            "\t\t(SELECT DISTINCT(Parent) AS DocParent FROM " + _core.getTableInfoDocuments() + ") doc ON annModel.EntityId = DocParent\n" +
            "WHERE Container = ? AND Created >= ? AND Created < ? AND (Body IS NOT NULL OR DocParent IS NOT NULL)\n" +
            "ORDER BY Earliest, Thread, Created";

    @Override
    public List<Container> getContainersWithNewMessages(Date start, Date end) throws SQLException
    {
        SQLFragment sql = new SQLFragment("SELECT DISTINCT(Container) FROM " + _comm.getTableInfoAnnouncements() + " WHERE Created >= ? and Created < ?", start, end);
        String[] containerIds = Table.executeArray(_comm.getSchema(), sql, String.class);

        List<Container> containers = new ArrayList<Container>(containerIds.length);

        for (String id : containerIds)
            containers.add(ContainerManager.getForId(id));

        return containers;
    }

    @Override
    public void sendDigest(Container c, Date min, Date max) throws Exception
    {
        DiscussionService.Settings settings = AnnouncementManager.getMessageBoardSettings(c);
        AnnouncementModel[] announcementModels = getRecentAnnouncementsInContainer(c, min, max);

        DailyDigestEmailPrefsSelector sel = new DailyDigestEmailPrefsSelector(c);

        for (User user : sel.getUsers())
        {
            List<AnnouncementModel> announcementModelList = new ArrayList<AnnouncementModel>(announcementModels.length);

            for (AnnouncementModel ann : announcementModels)
                if (sel.shouldSend(ann, user))
                    announcementModelList.add(ann);

            if (!announcementModelList.isEmpty())
            {
                Permissions perm = AnnouncementsController.getPermissions(c, user, settings);
                MailHelper.ViewMessage m = getDailyDigestMessage(c, settings, perm, announcementModelList, user);

                try
                {
                    MailHelper.send(m, null, c);
                }
                catch (ConfigurationException e)
                {
                    // Just record these exceptions to the local log (don't send to mothership)
                    _log.error(e.getMessage());
                }
            }
        }
    }

    private static AnnouncementModel[] getRecentAnnouncementsInContainer(Container c, Date min, Date max) throws SQLException
    {
        AnnouncementModel[] announcementModels = Table.executeQuery(_comm.getSchema(), RECENT_ANN_SQL, new Object[]{c, min, max, c, min, max}, AnnouncementManager.BareAnnouncementModel.class);
        AnnouncementManager.attachMemberLists(announcementModels);
        return announcementModels;
    }

    private static MailHelper.ViewMessage getDailyDigestMessage(Container c, DiscussionService.Settings settings, Permissions perm, List<AnnouncementModel> announcementModels, User user) throws Exception
    {
        MailHelper.ViewMessage m = MailHelper.createMultipartViewMessage(LookAndFeelProperties.getInstance(c).getSystemEmailAddress(), user.getEmail());
        m.setSubject("New posts to " + c.getPath());
        HttpServletRequest request = AppProps.getInstance().createMockRequest();

        DailyDigestPage page = createPage("dailyDigestPlain.jsp", c, settings, perm, announcementModels);
        JspView view = new JspView(page);
        view.setFrame(WebPartView.FrameType.NONE);
        m.setTemplateContent(request, view, "text/plain");

        page = createPage("dailyDigest.jsp", c, settings, perm, announcementModels);
        view = new JspView(page);
        view.setFrame(WebPartView.FrameType.NONE);
        m.setTemplateContent(request, view, "text/html");

        return m;
    }


    private static DailyDigestPage createPage(String templateName, Container c, DiscussionService.Settings settings, Permissions perm, List<AnnouncementModel> announcementModels) throws ServletException
    {
        DailyDigestPage page = (DailyDigestPage) JspLoader.createPage(AnnouncementsController.class, templateName);

        page.conversationName = settings.getConversationName().toLowerCase();
        page.settings = settings;
        page.c = c;
        page.announcementModels = announcementModels;
        page.boardPath = c.getPath();
        ActionURL boardUrl = AnnouncementsController.getBeginURL(c);
        page.boardUrl = boardUrl.getURIString();
        page.siteUrl = ActionURL.getBaseServerURL();
        page.removeUrl = new ActionURL(AnnouncementsController.EmailPreferencesAction.class, c).getURIString();
        page.includeGroups = perm.includeGroups();

        return page;
    }
}

