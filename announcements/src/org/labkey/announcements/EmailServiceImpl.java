/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
package org.labkey.announcements;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailPref;
import org.labkey.api.notification.EmailPrefFilter;
import org.labkey.api.notification.EmailService;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.MailHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Apr 22, 2010
 * Time: 11:37:19 AM
 */
public class EmailServiceImpl implements EmailService.I
{
    private static final Logger _log = Logger.getLogger(EmailService.class);

    @Override
    public void sendMessage(EmailMessage msg, User user, Container c) throws MessagingException, ConfigurationException
    {
        MailHelper.send(msg.createMessage(), user, c);
    }

    @Override
    public void sendMessage(EmailMessage[] msgs, User user, Container c)
    {
        // send the email messages from a background thread
        BulkEmailer emailer = new BulkEmailer(user, c);
        for (EmailMessage msg : msgs)
            emailer.addMessage(msg);

        emailer.start();
    }

    @Override
    public EmailMessage createMessage(String from, String[] to, String subject)
    {
        return createMessage(from, to, subject, null);
    }

    @Override
    public EmailMessage createMessage(String from, String[] to, String subject, @Nullable String message)
    {
        EmailMessage msg = new EmailMessageImpl(from, to, subject);

        if (message != null)
            msg.addContent(message);

        return msg;
    }

    @Override
    public void setEmailPref(User user, Container container, EmailPref pref, String value)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(user.getUserId(), container.getId(), EmailService.EMAIL_PREF_CATEGORY, true);
        props.put(pref.getId(), value);

        PropertyManager.saveProperties(props);
    }

    @Override
    public String getEmailPref(User user, Container container, EmailPref pref, @Nullable EmailPref defaultPref)
    {
        String defaultValue = pref.getDefaultValue();

        if (defaultPref != null)
        {
            Map<String, String> defaultProps = PropertyManager.getProperties(container.getId(), EmailService.EMAIL_PREF_CATEGORY);
            if (defaultProps.containsKey(defaultPref.getId()))
                defaultValue = defaultProps.get(defaultPref.getId());
            else
                defaultValue = defaultPref.getDefaultValue();
        }

        Map<String, String> props = PropertyManager.getProperties(user.getUserId(), container.getId(), EmailService.EMAIL_PREF_CATEGORY);
        String value = defaultValue;

        if (props.containsKey(pref.getId()))
        {
            value = pref.getValue(props.get(pref.getId()), defaultValue);
        }
        return value;
    }

    @Override
    public String getEmailPref(User user, Container container, EmailPref pref)
    {
        return getEmailPref(user, container, pref, null);
    }

    @Override
    public String getDefaultEmailPref(Container container, EmailPref pref)
    {
        Map<String, String> props = PropertyManager.getProperties(container.getId(), EmailService.EMAIL_PREF_CATEGORY);
        String value = pref.getDefaultValue();

        if (props.containsKey(pref.getId()))
            value = props.get(pref.getId());

        return value;
    }

    @Override
    public void setDefaultEmailPref(Container container, EmailPref pref, String value)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(container.getId(), EmailService.EMAIL_PREF_CATEGORY, true);
        props.put(pref.getId(), value);

        PropertyManager.saveProperties(props);
    }

    @Override
    public User[] getUsersWithEmailPref(Container container, EmailPrefFilter filter)
    {
        return filter.filterUsers(container);
    }

    private static class EmailMessageImpl implements EmailMessage
    {
        private String _from;
        private String[] _to = new String[0];
        private String _subject;
        private Map<contentType, String> _contentMap = new HashMap<contentType, String>();
        private Map<String, String> _headers = new HashMap<String, String>();

        public EmailMessageImpl(String from, String[] to, String subject)
        {
            _from = from;
            _to = to;
            _subject = subject;
        }

        public String getFrom()
        {
            return _from;
        }

        public String[] getTo()
        {
            return _to;
        }

        public String getSubject()
        {
            return _subject;
        }

        @Override
        public void setHeader(String name, String value)
        {
            _headers.put(name, value);
        }

        @Override
        public void addContent(String content)
        {
            _contentMap.put(contentType.PLAIN, content);
        }

        @Override
        public void addContent(contentType type, String content)
        {
            _contentMap.put(type, content);
        }

        @Override
        public void addContent(contentType type, ViewContext context, HttpView view) throws Exception
        {
            addContent(type, context.getRequest(), view);
        }

        @Override
        public void addContent(contentType type, HttpServletRequest request, HttpView view) throws Exception
        {
            // set the frame type to none to remove the extra div that gets added otherwise.
            if (view instanceof JspView)
                ((JspView)view).setFrame(WebPartView.FrameType.NONE);

            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpView.include(view, request, response);

            addContent(type, response.getContentAsString());
        }

        @Override
        public MimeMessage createMessage() throws MessagingException
        {
            MimeMessage msg = new MimeMessage(MailHelper.getSession());

            msg.setFrom(new InternetAddress(_from));

            if (_to.length != 0)
            {
                InternetAddress[] recipients = new InternetAddress[_to.length];
                int i=0;

                for (String user : _to)
                    recipients[i++] = new InternetAddress(user);

                msg.setRecipients(Message.RecipientType.TO, recipients);
            }

            if (!_headers.isEmpty())
            {
                for (Map.Entry<String, String> entry : _headers.entrySet())
                    msg.setHeader(entry.getKey(), entry.getValue());
            }

            msg.setSubject(_subject);

            if (!_contentMap.isEmpty())
            {
                boolean multipart = _contentMap.size() > 1;
                MimeMultipart multiPartContent = null;

                if (multipart)
                {
                    multiPartContent = new MimeMultipart("alternative");
                    msg.setContent(multiPartContent);
                }

                for (Map.Entry<contentType, String> entry : _contentMap.entrySet())
                {
                    if (multipart && multiPartContent != null)
                    {
                        BodyPart body = new MimeBodyPart();
                        body.setContent(entry.getValue(), entry.getKey().getMimeType());

                        multiPartContent.addBodyPart(body);
                    }
                    else
                        msg.setContent(entry.getValue(), entry.getKey().getMimeType());
                }
            }

            return msg;
        }
    }

    // Sends one or more email messages in a background thread.  Add message(s) to the emailer, then call start().
    public static class BulkEmailer extends Thread
    {
        private List<EmailMessage> _messages = new ArrayList<EmailMessage>();
        private Container _container;
        private User _user;

        public BulkEmailer(User user, Container c)
        {
            _user = user;
            _container = c;
        }

        public void addMessage(EmailMessage msg)
        {
            _messages.add(msg);
        }

        public void run()
        {
            for (EmailMessage msg : _messages)
            {
                try {
                    Message m = msg.createMessage();
                    MailHelper.send(m, _user, _container);
                }
                catch (MessagingException e)
                {
                    _log.error("Failed to send message: " + msg.getSubject(), e);
                }
                catch (ConfigurationException ex)
                {
                    _log.error("Unable to send email.", ex);
                }
            }
        }
    }
}
