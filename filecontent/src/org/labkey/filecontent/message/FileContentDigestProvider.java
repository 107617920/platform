/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.filecontent.message;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.files.FileContentDefaultEmailPref;
import org.labkey.api.message.digest.MessageDigest;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.Path;
import org.labkey.api.view.JspView;
import org.labkey.api.webdav.FileSystemAuditViewFactory;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: Jan 14, 2011
 * Time: 12:17:20 PM
 */
public class FileContentDigestProvider implements MessageDigest.Provider
{
    private static final Logger _log = Logger.getLogger(FileContentDigestProvider.class);
    private int _notificationOption;    // the notification option to match : (short digest, daily digest)

    public FileContentDigestProvider(int notificationOption)
    {
        _notificationOption = notificationOption;
    }

    @Override
    public List<Container> getContainersWithNewMessages(Date start, Date end) throws Exception
    {
        List<AuditLogEvent> events = getAuditEvents(null, start, end);
        Set<Container> containers = new HashSet<Container>();

        for (AuditLogEvent event : events)
            containers.add(ContainerManager.getForId(event.getContainerId()));

        return Arrays.asList(containers.toArray(new Container[containers.size()]));
    }

    private List<AuditLogEvent> getAuditEvents(Container container, Date start, Date end)
    {
        SimpleFilter filter = new SimpleFilter("EventType", FileSystemAuditViewFactory.EVENT_TYPE);
        Sort sort = new Sort("Created");

        filter.addCondition("Created", start, CompareType.GTE);
        filter.addCondition("Created", end, CompareType.LT);


        if (container != null)
            filter.addCondition("ContainerId", container.getId());

        return AuditLogService.get().getEvents(filter, sort);
    }

    @Override
    public void sendDigest(Container c, Date min, Date max) throws Exception
    {
        List<AuditLogEvent> events = getAuditEvents(c, min, max);
        Map<Path, List<AuditLogEvent>> recordMap = new LinkedHashMap<Path, List<AuditLogEvent>>();

        // group audit events by webdav resource
        for (AuditLogEvent event : events)
        {
            String resourcePath = event.getKey3();
            if (resourcePath != null)
            {
                Path path = Path.parse(resourcePath);
                WebdavResource resource = WebdavService.get().getResolver().lookup(path);

                if (resource != null)
                {
                    if (!recordMap.containsKey(path))
                    {
                        recordMap.put(path, new ArrayList<AuditLogEvent>());
                    }
                    recordMap.get(path).add(event);
                }
            }
        }

        if (recordMap.isEmpty())
            return;

        try
        {
            EmailService.I svc = EmailService.get();
            User[] users = getUsersToEmail(c);
            HttpServletRequest request = AppProps.getInstance().createMockRequest();
            String subject = "File Management Notification";

            if (users != null && users.length > 0)
            {
                List<EmailMessage> messages = new ArrayList<EmailMessage>();

                for (User user : users)
                {
                    FileDigestForm form = new FileDigestForm(user, c, recordMap);
                    EmailMessage msg = svc.createMessage(LookAndFeelProperties.getInstance(c).getSystemEmailAddress(),
                            new String[]{user.getEmail()}, subject);

                    msg.addContent(EmailMessage.contentType.HTML, request,
                            new JspView<FileDigestForm>("/org/labkey/filecontent/view/fileDigestNotify.jsp", form));
                    msg.addContent(EmailMessage.contentType.PLAIN, request,
                            new JspView<FileDigestForm>("/org/labkey/filecontent/view/fileDigestNotifyPlain.jsp", form));

                    messages.add(msg);
                }
                // send messages in bulk
                svc.sendMessage(messages.toArray(new EmailMessage[messages.size()]), null, c);
             }
       }
        catch (Exception e)
        {
            // Don't fail the request because of this error
            _log.warn("Unable to send email for the file notification: " + e.getMessage());
        }
    }

    private User[] getUsersToEmail(Container c) throws Exception
    {
        List<User> users = new ArrayList<User>();
        String pref = EmailService.get().getDefaultEmailPref(c, new FileContentDefaultEmailPref());
        int folderDefault = NumberUtils.toInt(pref);

        MessageConfigService.ConfigTypeProvider provider = MessageConfigService.getInstance().getConfigType(FileEmailConfig.TYPE);

        // get all users who have read access to this container
        for (MessageConfigService.UserPreference ep : provider.getPreferences(c))
        {
            int emailOption = ep.getEmailOptionId() != null ? ep.getEmailOptionId() : -1;
            if ((emailOption == _notificationOption) ||
                (folderDefault == _notificationOption && emailOption == -1))
            {
                users.add(ep.getUser());
            }
        }
        return users.toArray(new User[users.size()]);
    }

    public static class FileDigestForm
    {
        Map<Path, List<AuditLogEvent>> _records;
        User _user;
        Container _container;

        public FileDigestForm(User user, Container container, Map<Path, List<AuditLogEvent>> records)
        {
            _user = user;
            _container = container;
            _records = records;
        }

        public Map<Path, List<AuditLogEvent>> getRecords()
        {
            return _records;
        }

        public User getUser()
        {
            return _user;
        }

        public Container getContainer()
        {
            return _container;
        }
    }
}
