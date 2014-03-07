<%
/*
 * Copyright (c) 2005-2013 LabKey Corporation
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
%>
<%@ page import="org.labkey.announcements.model.AnnouncementManager" %>
<%@ page import="org.labkey.announcements.AnnouncementsController" %>
<%@ page extends="org.labkey.announcements.EmailPreferencesPage" %>
<b><%=h(message)%></b>
<form action="<%=h(buildURL(AnnouncementsController.EmailPreferencesAction.class))%>" method="post">
    <br>Send email notifications for these <%=h(conversationName)%>s<br>
    <input type="radio" value="<%=AnnouncementManager.EMAIL_PREFERENCE_NONE%>" name="emailPreference"<%=checked(emailPreference == AnnouncementManager.EMAIL_PREFERENCE_NONE)%>>
    <b>None</b> - Don't send me any email for this message board<br>
    <input type="radio" value="<%=AnnouncementManager.EMAIL_PREFERENCE_MINE%>" name="emailPreference"<%=checked(emailPreference == AnnouncementManager.EMAIL_PREFERENCE_MINE)%>>
    <b>Mine</b> - Send me email for posts to my <%=h(conversationName)%>s (I've posted to the <%=h(conversationName)%><% if (hasMemberList) { %> or I'm on its member list<% } %>)<br>
    <input type="radio" value="<%=AnnouncementManager.EMAIL_PREFERENCE_ALL%>" name="emailPreference"<%=checked(emailPreference == AnnouncementManager.EMAIL_PREFERENCE_ALL)%>>
    <b>All</b> - Send me email for all posts<br>

    <br>Notification type<br>
    <input type="radio" value="0" name="notificationType"<%=checked(notificationType == 0)%>>
    <b>Individual</b> - send a separate email after each post<br>
    <input type="radio" value="<%=AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST%>" name="notificationType"<%=checked(notificationType == AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST)%>>
    <b>Daily Digest</b> - send one email each day that summarizes all posts<br>

    <br><input type=hidden name="srcUrl" value="<%=h(srcURL)%>"/>
    <br><input type=hidden name="srcIdentifier" value="<%=h(srcIdentifier)%>"/>
    <%= button("Update").submit(true) %>
    <%= button((message == null ? "Cancel" : "Done")).href(srcURL) %>
</form>