/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.core.notification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.AbstractContainerListener;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.TestContext;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: cnathe
 * Date: 9/14/2015
 */
public class NotificationServiceImpl extends AbstractContainerListener implements NotificationService.Service
{
    private final static NotificationServiceImpl INSTANCE = new NotificationServiceImpl();
    private final Map<String, String> _typeLabelMap = new ConcurrentHashMap<>();

    public static NotificationServiceImpl getInstance()
    {
        return INSTANCE;
    }

    private NotificationServiceImpl()
    {
        ContainerManager.addContainerListener(this);
    }


    /* for compatibility with code that uses/used MailHelper directly */
    @Override
    public Notification sendMessage(Container c, User createdByUser, User notifyUser, MailHelper.MultipartMessage m,
            String linkText, String linkURL, String id, String type, boolean useSubjectAsContent
            ) throws IOException, MessagingException, ValidationException
    {
        MailHelper.send(m, createdByUser, c);

        if (!AppProps.getInstance().isExperimentalFeatureEnabled(NotificationMenuView.EXPERIMENTAL_NOTIFICATIONMENU))
            return null;

        Notification notification = new Notification();
        notification.setActionLinkText(linkText);
        notification.setActionLinkURL(linkURL);
        notification.setObjectId(id);
        notification.setType(type);
        notification.setUserId(notifyUser.getUserId());
        if (useSubjectAsContent)
        {
            notification.setContent(m.getSubject());
        }
        else
        {
            String contentType = m.getContentType();
            notification.setContentType(contentType);
            Object contentObject = m.getContent();
            if (contentObject instanceof MimeMultipart)
            {
                MimeMultipart mm = (MimeMultipart) contentObject;
                notification.setContent(mm.getBodyPart(0).getContent().toString(), mm.getBodyPart(0).getContentType());
            }
            else if (null != contentObject)
            {
                notification.setContent(contentObject.toString(), "text/plain");
            }
        }

        return NotificationService.get().addNotification(c, createdByUser, notification);
    }


    @Override
    public Notification addNotification(Container container, User user, @NotNull Notification notification) throws ValidationException
    {
        if (notification.getObjectId() == null || notification.getType() == null || notification.getUserId() <= 0)
        {
            throw new ValidationException("Notification missing one of the required fields: objectId, type, or notifyUserId.");
        }
        if (getNotification(container, notification) != null)
        {
            throw new ValidationException("Notification already exists for the specified user, object, and type.");
        }

        notification.setContainer(container.getEntityId());
        return Table.insert(user, getTable(), notification);
    }

    @Override
    public List<Notification> getNotificationsByUser(Container container, int notifyUserId, boolean unreadOnly)
    {
        return getNotificationsByUserOrType(container, null, notifyUserId, unreadOnly);
    }

    @Override
    public List<Notification> getNotificationsByType(Container container, @NotNull String type, int notifyUserId, boolean unreadOnly)
    {
        return getNotificationsByUserOrType(container, type, notifyUserId, unreadOnly);
    }

    private List<Notification> getNotificationsByUserOrType(Container container, String type, int notifyUserId, boolean unreadOnly)
    {
        SimpleFilter filter = new SimpleFilter();
        if (null != container)
            filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("UserID"), notifyUserId);
        if (type != null)
            filter.addCondition(FieldKey.fromParts("Type"), type);
        if (unreadOnly)
            filter.addCondition(FieldKey.fromParts("ReadOn"), null, CompareType.ISBLANK);

        TableSelector selector = new TableSelector(getTable(), filter, null);
        return selector.getArrayList(Notification.class);
    }

    @Override
    public Notification getNotification(int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        TableSelector selector = new TableSelector(getTable(), filter, null);
        return selector.getObject(Notification.class);
    }

    @Override
    public Notification getNotification(Container container, @NotNull Notification notification)
    {
        return getNotification(container, notification.getObjectId(), notification.getType(), notification.getUserId());
    }

    @Override
    public Notification getNotification(Container container, @NotNull String objectId, @NotNull String type, int notifyUserId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("ObjectId"), objectId);
        filter.addCondition(FieldKey.fromParts("Type"), type);
        filter.addCondition(FieldKey.fromParts("UserID"), notifyUserId);
        TableSelector selector = new TableSelector(getTable(), filter, null);
        return selector.getObject(Notification.class);
    }

    @Override
    public int markAsRead(Container container, User user, @Nullable String objectId, @NotNull List<String> types, int notifyUserId)
    {
        SimpleFilter filter = getNotificationUpdateFilter(container, objectId, types, notifyUserId);
        filter.addCondition(FieldKey.fromParts("ReadOn"), null, CompareType.ISBLANK);

        TableSelector selector = new TableSelector(getTable(), filter, null);
        List<Notification> notifications = selector.getArrayList(Notification.class);

        // update the ReadOn date to current date
        Map<String, Object> fields = new HashMap<>();
        fields.put("ReadOn", new Date());

        try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            for (Notification notification : notifications)
            {
                Table.update(user, getTable(), fields, notification.getRowId());
            }
            transaction.commit();
        }

        return notifications.size();
    }

    @Override
    public int removeNotifications(Container container, @Nullable String objectId, @NotNull List<String> types, int notifyUserId)
    {
        SimpleFilter filter = getNotificationUpdateFilter(container, objectId, types, notifyUserId);
        return Table.delete(getTable(), filter);
    }

    @Override
    public int removeNotificationsByType(Container container, @Nullable String objectId, @NotNull List<String> types)
    {
        SimpleFilter filter = getNotificationUpdateFilter(container, objectId, types, null);
        return Table.delete(getTable(), filter);
    }

    private SimpleFilter getNotificationUpdateFilter(Container container, @Nullable String objectId, @NotNull List<String> types, @Nullable Integer notifyUserId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause(FieldKey.fromParts("Type"), types);
        if (notifyUserId != null)
            filter.addCondition(FieldKey.fromParts("UserID"), notifyUserId);
        if (objectId != null)
            filter.addCondition(FieldKey.fromParts("ObjectId"), objectId);
        return filter;
    }

    @Override
    public void registerNotificationTypeLabel(@NotNull String type, String label)
    {
        if (!_typeLabelMap.containsKey(type))
            _typeLabelMap.put(type, label);
        else
            throw new IllegalStateException("A label has already been registered for this type: " + type);
    }

    @Override
    public String getNotificationTypeLabel(@NotNull String type)
    {
        if (_typeLabelMap.containsKey(type))
            return _typeLabelMap.get(type);
        else
            return "Other";
    }

    private TableInfo getTable()
    {
        return CoreSchema.getInstance().getTableInfoNotifications();
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(getTable(), c, "Container");
    }

    //
    //JUnit TestCase
    //
    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        private static final String _testDirName = "/_jUnitNotifications";
        private static final NotificationService.Service _service = NotificationService.get();
        private static User _user;
        private static Container _container;
        private static Notification _notif1;
        private static Notification _notif2;

        @BeforeClass
        public static void setup()
        {
            _user = TestContext.get().getUser();
            assertNotNull("Should have access to a user", _user);

            deleteTestContainer();
            _container = ContainerManager.ensureContainer(_testDirName);

            _notif1 = new Notification("objectId1", "type1", _user);
            _notif1.setActionLinkText("actionLinkText1");
            _notif1.setActionLinkURL("actionLinkURL1");
            _notif1.setContent("description1", "text/plain");

            _notif2 = new Notification("objectId2", "type2", _user);
            _notif2.setActionLinkText("actionLinkText2");
            _notif2.setActionLinkURL("actionLinkURL2");
            _notif2.setContent("description2", "text/plain");
        }

        @AfterClass
        public static void cleanup()
        {
            deleteTestContainer();
        }

        @Before
        public void ensureNotifications() throws ValidationException
        {
            // if either of the two notifications aren't present in the DB, create them now
            if (_service.getNotification(_container, _notif1) == null)
                addNotification(_container, _user, _notif1, null);
            if (_service.getNotification(_container, _notif2) == null)
                addNotification(_container, _user, _notif2, null);
        }

        @Test
        public void verifyInsert() throws ValidationException
        {
            // verify can't insert notification without required fields
            addNotification(_container, _user, new Notification(), "Notification missing one of the required fields");

            // verify can't insert the same notification twice
            addNotification(_container, _user, _notif1, "Notification already exists");

            // verify notification using the getNotification APIs
            verifyNotificationDetails(_notif1, _service.getNotification(_container, _notif1));
            verifyNotificationDetails(_notif2, _service.getNotification(_container, "objectId2", "type2", _user.getUserId()));

            // verify notification query by type
            assertEquals("Unexpected number of notifications for type 'type1'", 1, _service.getNotificationsByType(_container, "type1", _user.getUserId(), false).size());
            assertEquals("Unexpected number of notifications for type 'type2'", 1, _service.getNotificationsByType(_container, "type2", _user.getUserId(), false).size());
            assertEquals("Unexpected number of notifications for type 'type3'", 0, _service.getNotificationsByType(_container, "type3", _user.getUserId(), false).size());
        }

        @Test
        public void verifyMarkAsRead() throws Exception
        {
            // no records updated when we give it a mis-matching objectId
            int count = _service.markAsRead(_container, _user, "objectId0", Collections.singletonList("type1"), _user.getUserId());
            assertEquals("Unexpected number of notifications updated", 0, count);

            // mark one record as read and verify that it isn't deleted or changed in any other way
            count = _service.markAsRead(_container, _user, "objectId1", Collections.singletonList("type1"), _user.getUserId());
            assertEquals("Unexpected number of notifications updated", 1, count);
            assertEquals("Unexpected number of unread notifications", 0, _service.getNotificationsByType(_container, "type1", _user.getUserId(), true).size());
            verifyNotificationDetails(_notif1, _service.getNotification(_container, _notif1));
        }

        @Test
        public void verifyRemove() throws Exception
        {
            // no records removed when we give it a mis-matching objectId
            int count = _service.removeNotifications(_container, "objectId0", Collections.singletonList("type2"), _user.getUserId());
            assertEquals("Unexpected number of notifications removed", 0, count);

            // remove a record and verify it has been deleted using the type query
            count = _service.removeNotifications(_container, "objectId2", Collections.singletonList("type2"), _user.getUserId());
            assertEquals("Unexpected number of notifications removed", 1, count);
            assertEquals("Unexpected number of notifications for type 'type2'", 0, _service.getNotificationsByType(_container, "type2", _user.getUserId(), false).size());
        }

        private Notification addNotification(Container container, User user, Notification notification, String expectedErrorPrefix) throws ValidationException
        {
            try
            {
                return _service.addNotification(container, user, notification);
            }
            catch(ValidationException e)
            {
                if (expectedErrorPrefix != null)
                    assertTrue(e.getMessage().startsWith(expectedErrorPrefix));
                else
                    throw e;

                return null;
            }
        }

        private void verifyNotificationDetails(Notification before, Notification after)
        {
            assertEquals("Unexpected notification container value", before.getContainer(), after.getContainer());
            assertEquals("Unexpected notification userId value", before.getUserId(), after.getUserId());
            assertEquals("Unexpected notification objectId value", before.getObjectId(), after.getObjectId());
            assertEquals("Unexpected notification type value", before.getType(), after.getType());
            assertEquals("Unexpected notification content value", before.getContent(), after.getContent());
            assertEquals("Unexpected notification actionLinkText value", before.getActionLinkText(), after.getActionLinkText());
            assertEquals("Unexpected notification actionLinkURL value", before.getActionLinkURL(), after.getActionLinkURL());
        }

        private static void deleteTestContainer()
        {
            if (null != ContainerManager.getForPath(_testDirName))
                ContainerManager.deleteAll(ContainerManager.getForPath(_testDirName), _user);
        }
    }
}
