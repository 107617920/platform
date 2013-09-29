<%
/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController"%>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementForm" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.BaseInsertView" %>
<%@ page import="org.labkey.api.announcements.DiscussionService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext3"));
        return resources;
    }
%>
<%
    HttpView<BaseInsertView.InsertBean> me = (HttpView<BaseInsertView.InsertBean>) HttpView.currentView();
    BaseInsertView.InsertBean bean = me.getModelBean();

    DiscussionService.Settings settings = bean.settings;
    AnnouncementForm form = bean.form;

    Container c = me.getViewContext().getContainer();

    String respondUrl = AnnouncementsController.getRespondURL(c).getEncodedLocalURIString();
%><%=formatMissedErrors("form")%>
<script type="text/javascript">
function validateForm(form)
{
    if(form.title){
        var trimmedTitle = form.title.value.trim();

        if (trimmedTitle.length > 0)
            return true;

        Ext.Msg.alert("Error", "Title must not be blank.");
        Ext.get('submitButton').replaceClass('labkey-disabled-button', 'labkey-button');
        return false;
    } else {
        return true;
    }
}
Ext.onReady(function(){
    new Ext.Resizable('body', { handles:'se', minWidth:200, minHeight:100, wrap:true });
});
</script>
<form method=post enctype="multipart/form-data" action="<%=respondUrl%>" onSubmit="return validateForm(this)">
<input type="hidden" name="cancelUrl" value="<%=h(bean.cancelURL)%>">
<%=generateReturnUrlFormField(bean.cancelURL)%>
<input type="hidden" name="fromDiscussion" value="<%=bean.fromDiscussion%>">
<div style="max-width: 1050px;"><table style="width: 100%;"><%

if (settings.isTitleEditable())
{
    %><tr><td class="labkey-form-label">Title * <%= PageFlowUtil.helpPopup("Title", "This field is required.") %></td><td colspan="2"><input type="text" size="60" name="title" value="<%=h(form.get("title"))%>"></td></tr><%
}
else
{
    %><tr><td colspan="2"><input type="hidden" name="title" value="<%=h(form.get("title"))%>"></td></tr><%
}

if (settings.hasStatus())
{
    %><tr><td class="labkey-form-label">Status</td><td colspan="2"><%=bean.statusSelect%></td></tr><%
}

if (settings.hasAssignedTo())
{
    %><tr><td class="labkey-form-label">Assigned&nbsp;To</td><td colspan="2"><%=bean.assignedToSelect%></td></tr><%
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
    %> receive email notifications of new posts to this <%=settings.getConversationName().toLowerCase()%>.<br><br>Enter one or more email addresses, each on its own line.</i></td></tr><%
}

if (settings.hasExpires())
{
    %><tr><td class="labkey-form-label">Expires</td><td><input type="text" size="23" name="expires" value="<%=h(form.get("expires"))%>" ></td><td width="100%"><i>Expired messages are not deleted, they are just no longer shown on the Portal page.</i></td></tr><%
}

%>
    <tr>
    <td class="labkey-form-label">Body</td><td colspan=2 width="100%"><textarea cols="60" rows="15" id="body" name="body" style="width:100%;"><%=h(form.get("body"))%></textarea>
        <input type="hidden" name="parentId" value="<%=h(bean.parentAnnouncementModel.getEntityId())%>"/>
    </td>
</tr><%
    
if (settings.hasFormatPicker())
{
%><tr>
    <td class="labkey-form-label">Render As</td>
    <td colspan="2">
        <select name="rendererType">
              <%
                  for (WikiRendererType type : bean.renderers)
                  {
                      String value = type.name();
                      String displayName = type.getDisplayName();
                      String selected = type == bean.currentRendererType ? "selected " : "";
                  %>
                      <option <%=selected%> value="<%=h(value)%>"><%=h(displayName)%></option>
                  <%
              }%>
        </select>
    </td>
</tr>
<%}%>
    <tr>
        <td class="labkey-form-label">Attachments</td>
        <td colspan="2">
            <table id="filePickerTable"></table>
            <table>
                <tbody>
                <tr><td><a href="javascript:addFilePicker('filePickerTable','filePickerLink')" id="filePickerLink"><img src="<%=request.getContextPath()%>/_images/paperclip.gif">&nbsp;Attach a file</a></td></tr>
                </tbody>
            </table>
        </td>
    </tr><%
        if (null != bean.emailUsers)
        {
    %>
    <tr><td colspan="3" style="padding-top: 8px;color:green;">An email notification of this post will be sent to <%=bean.emailUsers%> people.</td></tr><%
        }
    %>
</table></div>
<br>&nbsp;<%=PageFlowUtil.generateSubmitButton("Submit", null, "id=submitButton", true, true)%>&nbsp;<%
if (null != bean.cancelURL)
{
    %><%=generateButton("Cancel", bean.cancelURL)%><%
}
else
{
    %><%=PageFlowUtil.generateSubmitButton("Cancel", "javascript:window.history.back(); return false;")%>
    <%
}
%>
</form>
<br>
<% me.include(bean.currentRendererType.getSyntaxHelpView(), out); %>
