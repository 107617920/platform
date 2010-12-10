<%
/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementWebPart" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementWebPart.MessagesBean" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.DownloadAction" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AnnouncementWebPart me = (AnnouncementWebPart) HttpView.currentView();
    MessagesBean bean = me.getModelBean();
    Container c = me.getViewContext().getContainer();
    User user = me.getViewContext().getUser();
%>
<!--ANNOUNCEMENTS-->
<table style="width:100%">
    <tr>
        <td>
            <div style="text-align: left"><%
            if (null != bean.insertURL)
            {
        %><%=textLink("new " + bean.settings.getConversationName().toLowerCase(), bean.insertURL)%>&nbsp;<%
            }
            if (null != bean.listURL)
            {
        %><%=textLink("view list", bean.listURL)%><%
    }
%></div>
        </td>
        <td style="text-align:center">
            <div style="text-align: center;"><%=bean.filterText.replace(" ", "&nbsp;")%></div>
        </td>
        <td>
            <div style="text-align: right"><%
                if (null != bean.emailPrefsURL)
                {
            %><%=textLink("email preferences", bean.emailPrefsURL)%><%
                }
                if (null != bean.emailManageURL)
                {
            %>&nbsp;<%=textLink("email admin", bean.emailManageURL)%><%
                }
                if (null != bean.customizeURL)
                {
            %>&nbsp;<%=textLink("customize", bean.customizeURL)%><%
                }
            %>
            </div>
        </td>
    </tr><%
    if (0 == bean.announcementModels.length)
    {%>
    <tr><td colspan=3 style="padding-top:4px;">No <%=bean.filterText.replace("all ", "")%></td></tr><%
    }
    for (AnnouncementModel a : bean.announcementModels)
    { %>
    <tr>
        <td class="labkey-announcement-title labkey-force-word-break" width="40%" align="left"><span><a href="<%=h(a.getThreadURL(c))%>rowId=<%=a.getRowId()%>"><%=h(a.getTitle())%></a></span><%
        if (a.getResponseCount() > 0)
            out.print(" (" + a.getResponseCount() + (a.getResponseCount() == 1 ? "&nbsp;response)" : "&nbsp;responses)"));
        %></td>
        <td class="labkey-announcement-title" width="20%" align="center"><%=h(a.getCreatedByName(bean.includeGroups, user))%></td>
        <td class="labkey-announcement-title" width="40%" align="right" nowrap><%=DateUtil.formatDateTime(a.getCreated())%></td>
    </tr>
    <tr><td colspan=3 class="labkey-title-area-line"></td></tr>
    <tr><td colspan=3 class="labkey-force-word-break"><%=a.translateBody(c)%></td></tr>
<%
    if (a.getAttachments().size() > 0)
        { %>
    <tr><td colspan=3><%
        for (Attachment d : a.getAttachments())
        {
    %>
        <a href="<%=h(d.getDownloadUrl(DownloadAction.class))%>"><img src="<%=request.getContextPath()%><%=d.getFileIcon()%>">&nbsp;<%=d.getName()%></a>&nbsp;<%
            }
        %>
    </td></tr>
<%      } %>    <tr><td style="padding-bottom:4px;" colspan=3 align="left"><%=textLink("view " + bean.settings.getConversationName().toLowerCase() + (null != bean.insertURL ? " or respond" : ""), a.getThreadURL(c) + "rowId=" + a.getRowId())%></td></tr>
<%
    }
%></table>
<!--/ANNOUNCEMENTS-->
