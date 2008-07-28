<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController" %>
<%@ page import="org.labkey.announcements.model.Announcement" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.wiki.WikiRenderer" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.announcements.DailyDigestPage" %>
<html>
<head>
<link href="<%=cssURL%>" rel="stylesheet" type="text/css">
</head>

<body>
<table width="100%" border="0" cellspacing="0" cellpadding="4">
    <tr><td class="normal"><b>The following new posts were made yesterday in folder: <%=h(c.getPath())%></b></td></tr><%

    String previousThread = null;
    String threadUrl = null;

    for (Announcement ann : announcements)
    {
        if (null == ann.getParent() || !ann.getParent().equals(previousThread))
        {
            if (null == ann.getParent())
                previousThread = ann.getEntityId();
            else
                previousThread = ann.getParent();

            if (null != threadUrl)
            {
                %><tr><td><a href="<%=threadUrl%>">View this <%=conversationName%></a></td></tr><%
            }

            threadUrl = h(AnnouncementsController.getThreadURL(c, previousThread, ann.getRowId()).getURIString());%>
            <tr><td>&nbsp;</td></tr><tr><td class="normal" colspan="2" style="background-color: #dddddd"><%=ann.getTitle()%></td></tr><%
        }

        int attachmentCount = ann.getAttachments().size();

        %>
            <tr><td><%=ann.getCreatedByName(includeGroups, HttpView.currentContext())%><% if (null == ann.getParent()) { %> created this <%=conversationName%><% } else { %> responded <% } %> at <%=DateUtil.formatDateTime(ann.getCreated())%><%=(attachmentCount > 0 ? " and attached " + attachmentCount + " document" + (attachmentCount > 1 ? "s" : "") : "")%></td></tr><%

        if (!settings.isSecure())
        {
            WikiRenderer w = ann.getRenderer();
            String body = w.format(ann.getBody()).getHtml();
            %>
            <tr><td style="padding-left:35px;"><%=body%></td></tr><%
        }
    }

    if (null != threadUrl)
    {
        %><tr><td><a href="<%=threadUrl%>">View this <%=conversationName%></a></td></tr><%
    }

    %>
</table>

<br>
<br>
<hr size="1">

<table width="100%" border="0" cellspacing="0" cellpadding="4">
    <tr><td class="normal">You have received this email because you are signed up for a daily digest of new posts to <a href="<%=boardUrl%>"><%=boardPath%></a> at <a href="<%=siteUrl%>"><%=siteUrl%></a>.
  If you no longer wish to receive these notifications, please <a href="<%=removeUrl%>">change your email preferences</a>.</td></tr>
</table>    
</body>
</html>
