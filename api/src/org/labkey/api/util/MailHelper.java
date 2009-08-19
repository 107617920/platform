/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.WebPartView;
import org.labkey.api.settings.AppProps;
import org.labkey.api.security.User;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.data.Container;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.mail.*;
import javax.mail.Message.RecipientType;
import javax.mail.internet.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Provides static functions for help with sending email.
 */
public class MailHelper
{
    private static Logger _log = Logger.getLogger(MailHelper.class);
    private static Session _session = null;
    public static final String MESSAGE_AUDIT_EVENT = "MessageAuditEvent";

    static
    {
        setSession(null);
    }

    public static void setSession(Session session)
    {
        if (session != null)
            _session = session;
        else
        {
            try
            {
                InitialContext ctx = new InitialContext();
                Context envCtx = (Context) ctx.lookup("java:comp/env");
                _session = (Session) envCtx.lookup("mail/Session");
            }
            catch (Exception e)
            {
                _log.log(Level.ERROR, "Exception loading mail session", e);
            }            
        }
    }

    /**
     * Creates a blank email message.  Caller must set all fields before sending.
     */
    public static ViewMessage createMessage()
    {
        return new ViewMessage(_session);
    }

    public static ViewMessage createMultipartViewMessage()
    {
        return new ViewMessage(_session, true);
    }

    public static MultipartMessage createMultipartMessage()
    {
        return new MultipartMessage(_session);
    }

    /**
     * Returns the session that will be used for all messages
     */
    public static Session getSession()
    {
        return _session;
    }

    /**
     * Creates an email message, and sets the "from" and "to" fields.
     *
     * @param from Semicolon separated list of senders.
     * @param to   Semicolon separated list of recipients.
     */
    public static ViewMessage createMessage(String from, String to) throws MessagingException
    {
        return _createMessage(createMessage(), from, to);
    }

    /**
     * Creates an email message, and sets the "from" and "to" fields.
     *
     * @param from Semicolon separated list of senders.
     * @param to   Semicolon separated list of recipients.
     */
    public static ViewMessage createMultipartViewMessage(String from, String to) throws MessagingException
    {
        return _createMessage(createMultipartViewMessage(), from, to);
    }

    private static ViewMessage _createMessage(ViewMessage m, String from, String to) throws MessagingException
    {
        m.addFrom(createAddressArray(from));
        if (null != to)
            m.addRecipients(RecipientType.TO, createAddressArray(to));
        return m;
    }

    /**
     * Creates an array of email addresses from a semicolon separated list in a string.
     */
    public static Address[] createAddressArray(String s) throws AddressException
    {
        List<InternetAddress> addrs = new ArrayList<InternetAddress>();
        StringTokenizer st = new StringTokenizer(s, ";");
        while (st.hasMoreTokens())
            addrs.add(new InternetAddress(st.nextToken()));

        return addrs.toArray(new Address[addrs.size()]);
    }

    /**
     * Sends an email message, using the system mail session, and SMTP transport.
     * This function logs a warning on a MessagingException, and then throws it to
     * the caller.  The caller should avoid double-logging the failure, but may want
     * to handle the exception in some other way, e.g. displaying a message to the
     * user.
     */
    public static void send(Message m) throws MessagingException
    {
        try
        {
            Transport.send(m);
        }
        catch (NoSuchProviderException e)
        {
            _log.log(Level.ERROR, "Error getting SMTP transport");
        }
        catch (MessagingException e)
        {
            logMessagingException(m, e);
            throw e;
        }
        catch (RuntimeException e)
        {
            logMessagingException(m, e);
            throw new MessagingException(ERROR_MESSAGE, e);
        }
    }

    public static void addAuditEvent(Message m) throws MessagingException
    {
        addAuditEvent(null, null, m);
    }

    public static void addAuditEvent(User user, Container c, Message m) throws MessagingException
    {
        AuditLogEvent event = new AuditLogEvent();

        try {
            event.setEventType(MESSAGE_AUDIT_EVENT);
            if (user != null)
                event.setCreatedBy(user);
            if (c != null)
                event.setContainerId(c.getId());
            event.setComment("The Email Message: (" + m.getSubject() + ") was sent");
            event.setKey1(getAddressStr(m.getFrom()));
            event.setKey2(getAddressStr(m.getAllRecipients()));
            event.setKey3(m.getContentType());

            AuditLogService.get().addEvent(event);
        }
        catch (MessagingException me)
        {
            logMessagingException(m, me);
        }
    }

    private static String getAddressStr(Address[] addresses)
    {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (Address a : addresses)
        {
            sb.append(sep);
            sb.append(a.toString());

            sep = ", ";
        }
        return sb.toString();
    }

    private static final String ERROR_MESSAGE = "Exception sending email; check your SMTP configuration in " + AppProps.getInstance().getWebappConfigurationFilename();

    private static void logMessagingException(Message m, Exception e) throws MessagingException
    {
        _log.log(Level.WARN, ERROR_MESSAGE +
                "\nfrom: " + StringUtils.join(m.getFrom(), "; ") + "\n" +
                "to: " + StringUtils.join(m.getRecipients(RecipientType.TO), "; ") + "\n" +
                "subject: " + m.getSubject(), e);
    }


    public static void renderHtml(Message m, String title, Writer out)
    {
        try
        {
            Address[] from = m.getFrom();
            Address[] to = m.getRecipients(Message.RecipientType.TO);
            Address[] cc = m.getRecipients(Message.RecipientType.CC);
            Address[] bcc = m.getRecipients(Message.RecipientType.BCC);
            String subject = m.getSubject();

            String body = null;
            Object content = m.getContent();
            if (content instanceof Multipart)
            {
                final Multipart mp = (Multipart)content;
                for (int i=0; i < mp.getCount(); i++)
                {
                    BodyPart part = mp.getBodyPart(i);
                    if ("text/html".equalsIgnoreCase(part.getContentType()))
                    {
                        body = part.getContent().toString();
                        break;
                    }
                }
                if (body == null)
                    body = mp.getBodyPart(mp.getCount()-1).getContent().toString();
            }
            else
                body = m.getContent().toString();

            out.write("<html><head><title>" + title + "</title></head><body>\n");
            if (null != from) out.write(PageFlowUtil.filter("From: " + StringUtils.join(from, "; ")) + "<br>\n");
            if (null != to) out.write(PageFlowUtil.filter("To: " + StringUtils.join(to, "; ")) + "<br>\n");
            if (null != cc) out.write(PageFlowUtil.filter("Cc: " + StringUtils.join(cc, "; ")) + "<br>\n");
            if (null != bcc) out.write(PageFlowUtil.filter("Bcc: " + StringUtils.join(bcc, "; ")) + "<br>\n");
            if (null != subject) out.write(PageFlowUtil.filter("Subject: " + subject) + "<br><br>\n");
            if (null != body) out.write(body + "<br>\n");
            out.write("</body></html>");
        }
        catch (IOException e)
        {
            _log.error("renderHtml", e);
        }
        catch (MessagingException e)
        {
            _log.error("renderHtml", e);
        }
    }
    
    /**
     * Message with support for a view for message body.
     */
    public static class ViewMessage extends MimeMessage
    {
        private boolean _isMultipart;

        public ViewMessage(Session session, boolean isMultipart)
        {
            super(session);
            _isMultipart = isMultipart;
        }

        public ViewMessage(Session session)
        {
            this(session, false);
        }

        public void setMultipart(boolean mp)
        {
            _isMultipart = mp;
        }

        public void setTemplateContent(HttpServletRequest request, HttpView view, String type) throws Exception
        {
            // set the frame type to none to remove the extra div that gets added otherwise.
            if (view instanceof JspView)
                ((JspView)view).setFrame(WebPartView.FrameType.NONE);

            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpView.include(view, request, response);

            if (_isMultipart)
            {
                Object content;
                try {
                    content = getContent();
                }
                catch (Exception e) {
                    // will get an IOException or MessagingException if no content exists
                    content = null;
                }

                if (content == null)
                {
                    content = new MimeMultipart("alternative");
                    setContent((Multipart)content);
                }
                BodyPart body = new MimeBodyPart();
                body.setContent(response.getContentAsString(), type);

                if (content instanceof Multipart)
                    ((Multipart)content).addBodyPart(body);
            }
            else
                setContent(response.getContentAsString(), type);
        }
    }

    public static class MultipartMessage extends MimeMessage
    {
        public MultipartMessage(Session session)
        {
            super(session);
        }

        public void setBodyContent(String message, String type) throws Exception
        {
            Object content;
            try {
                content = getContent();
            }
            catch (Exception e) {
                // will get an IOException or MessagingException if no content exists
                content = null;
            }

            if (content == null)
            {
                content = new MimeMultipart("alternative");
                setContent((Multipart)content);
            }
            BodyPart body = new MimeBodyPart();
            body.setContent(message, type);

            if (content instanceof Multipart)
                ((Multipart)content).addBodyPart(body);
        }
    }


    // Sends one or more email messages in a background thread.  Add message(s) to the emailer, then call start().
    public static class BulkEmailer extends Thread
    {
        private Map<Collection<String>, ViewMessage> _map = new HashMap<Collection<String>, ViewMessage>(10);
        private User _user;

        public BulkEmailer()
        {
        }

        public void setUser(User user)
        {
            _user = user;
        }

        // Send message to multiple recipients
        public void addMessage(Collection<String> emails, ViewMessage m)
        {
            _map.put(emails, m);
        }

        // Send message to single recipient
        public void addMessage(String email, ViewMessage m)
        {
            _map.put(PageFlowUtil.set(email), m);
        }

        public void run()
        {
            for (Map.Entry<Collection<String>, ViewMessage> entry : _map.entrySet())
            {
                Collection<String> emails = entry.getKey();
                ViewMessage m = entry.getValue();

                for (String email : emails)
                {
                    try
                    {
                        m.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
                        MailHelper.send(m);
                        MailHelper.addAuditEvent(_user, null, m);
                    }
                    catch(MessagingException e)
                    {
                        _log.error("Failed to send message to " + email, e);
                    }
                }
            }
        }
    }
}
