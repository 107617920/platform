<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementUpdateView"%>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementUpdateView.UpdateBean"%>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.api.announcements.DiscussionService" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AnnouncementUpdateView me = (AnnouncementUpdateView) HttpView.currentView();
    UpdateBean bean = me.getModelBean();

    AnnouncementModel ann = bean.annModel;
    DiscussionService.Settings settings = bean.settings;
%>
<%=formatMissedErrors("form")%>
<form method="post" action="update.post" enctype="multipart/form-data">
<input type="hidden" name="rowId" value="<%=ann.getRowId()%>">
<input type="hidden" name="entityId" value="<%=ann.getEntityId()%>">
<input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(ann)%>">
<%=generateReturnUrlFormField(bean.returnURL)%>
<table><%

if (settings.isTitleEditable())
{
    %><tr><td class='labkey-form-label'>Title</td><td colspan="2"><input name="title" size="60" value="<%=h(ann.getTitle())%>"></td></tr><%
}

if (settings.hasStatus())
{
    %><tr><td class='labkey-form-label'>Status</td><td colspan="2"><%=bean.statusSelect%></td></tr><%
}

if (settings.hasAssignedTo())
{
    %><tr><td class='labkey-form-label'>Assigned&nbsp;To</td><td colspan="2"><%=bean.assignedToSelect%></td></tr><%
}

if (settings.hasMemberList())
{
    %><tr><td class="labkey-form-label">Members</td><td><%=bean.memberList%></td><td width="100%"><i><%
    if (settings.isSecure())
    {
        %> This <%=settings.getConversationName().toLowerCase()%> is private; only editors and the users on this list can view it.  These users will also<%
    }
    else
    {
        %> The users on the member list<%
    }
    %> receive email notifications of new posts to this <%=h(settings.getConversationName().toLowerCase())%>.<br><br>Enter one or more email addresses, each on its own line.</i></td></tr><%
}

if (settings.hasExpires())
{
    %><tr><td class="labkey-form-label">Expires</td><td><input name="expires" size="23" value="<%=h(DateUtil.formatDate(ann.getExpires()))%>"></td><td width="100%"><i>Expired messages are not deleted, they are just no longer shown on the Portal page.</i></td></tr><%
}

%>
  <tr>
    <td class='labkey-form-label'>Body</td>
    <td width="100%" colspan="2">
        <textarea cols="120" rows ="15" name='body' style="width: 100%;"><%=h(ann.getBody())%></textarea>
    </td>
  </tr>
<%
    if (settings.hasFormatPicker())
    { %>
  <tr>
    <td class="labkey-form-label">Render As</td>
    <td colspan="2">
      <select name="rendererType"><%
          for (WikiRendererType type : bean.renderers)
          {
              String value = type.name();
              String displayName = type.getDisplayName();
              String selected = type == bean.currentRendererType ? "selected " : "";
      %>
        <option <%=selected%>value="<%=h(value)%>"><%=h(displayName)%></option><%
        } %>
      </select>
    </td>
  </tr>
<%  } %>
  <tr>
    <td class='labkey-form-label'>Attachments</td>
    <td colspan="2">
        <table id="filePickerTable">
            <tbody>
                <%
                    int x = -1;
                    for (Attachment att : ann.getAttachments())
                {
                    x++;
                    %><tr id="attach-<%=x%>">
                        <td><img src="<%=request.getContextPath() + att.getFileIcon()%>" alt="logo"/>&nbsp;<%= h(att.getName()) %></td>
                        <td><a onclick="removeAttachment(<%=PageFlowUtil.jsString(ann.getEntityId())%>, <%=PageFlowUtil.jsString(att.getName())%>, 'attach-<%=x%>'); ">remove</a></td>
                    </tr><%
                }
                %>
            </tbody>
        </table>
        <table>
            <tbody>
                <tr><td><a href="javascript:addFilePicker('filePickerTable','filePickerLink')" id="filePickerLink"><img src="<%=request.getContextPath()%>/_images/paperclip.gif">&nbsp;Attach a file</a></td></tr>
            </tbody>
        </table>
	</td>
  </tr>
  <tr>
    <td colspan=3 align=left>
      <table>
        <tr>
          <td><%=PageFlowUtil.generateSubmitButton("Submit", null, null, true, true)%>
             &nbsp;<%=generateBackButton("Cancel")%></td>
        </tr>
      </table>
    </td>
  </tr>
</table>
</form>
<p/>
<% me.include(bean.currentRendererType.getSyntaxHelpView(), out); %>
