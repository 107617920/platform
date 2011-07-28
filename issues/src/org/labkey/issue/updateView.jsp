<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegion"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.util.HString"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.issue.IssuePage" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    IssuePage bean = me.getModelBean();
    final Issue issue = bean.getIssue();
    final Container c = context.getContainer();
    final User user = context.getUser();
    final String focusId = (0 == issue.getIssueId() ? "title" : "comment");
    int emailPrefs = IssueManager.getUserEmailPreferences(context.getContainer(), user.getUserId());
    final String popup = getNotifyHelpPopup(emailPrefs, issue.getIssueId());

    BindException errors = bean.getErrors();
%>

<script type="text/javascript">
    var numberRe = /[0-9]/;
    function filterNumber(e, input)
    {
        if (e.isSpecialKey())
            return true;

        var cc = String.fromCharCode(e.getCharCode());
        if (!cc)
            return true;

        if (!numberRe.test(cc))
        {
            if (e.stopPropagation) {
                e.stopPropagation();
            } else {
                e.cancelBubble = true;
            }
            if (e.preventDefault) {
                e.preventDefault();
            } else {
                e.returnValue = false;
            }
            return false;
        }

        return true;
    }
</script>
<form method="POST" onsubmit="LABKEY.setSubmit(true); return true;" enctype="multipart/form-data" action="<%=IssuesController.issueURL(context.getContainer(), bean.getAction())%>">

    <table>
    <%
        if (null != errors && 0 != errors.getErrorCount())
        {
            for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
            {
                %><tr><td colspan=3><font class="labkey-error"><%=h(context.getMessage(e))%></font></td></tr><%
            }
        }
    if (!bean.getRequiredFields().isEmpty())
        out.print("<tr><td>Fields marked with an asterisk <span class=\"labkey-error\">*</span> are required.</td></tr>");
    %>
    </table>

    <table>
        <tr>
            <td align="right" valign="top"><%=PageFlowUtil.generateSubmitButton("Save", null, "name=\"" + bean.getAction() + "\"", true, true)%><%= generateButton("Cancel", IssuesController.issueURL(context.getContainer(), IssuesController.ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true"))%></td>
        </tr>
        <tr>
<%
            if (0 == issue.getIssueId())
            {
%>
                <td class="labkey-form-label"><%=bean.getLabel("Title")%></td>
<%
            } else {
%>
                <td class="labkey-form-label">Issue <%=issue.getIssueId()%></td>
<%
            }
%>
                <td colspan="3">
                <%=bean.writeInput(new HString("title", false), issue.getTitle(), new HString("id=title tabindex=0 style=\"width:100%;\"", false))%>
                </td></tr>
            <tr>
                <td class="labkey-form-label"><%=bean.getLabel("Status")%></td><td><%=h(issue.getStatus())%></td>
                <td rowspan="6" valign="top">
                    <table>
                        <tr><td class="labkey-form-label"><%=bean.getLabel("Opened")%></td><td nowrap="true"><%=bean.writeDate(issue.getCreated())%> by <%=h(issue.getCreatedByName(user))%></td></tr>
                        <tr><td class="labkey-form-label">Changed</td><td nowrap="true"><%=bean.writeDate(issue.getModified())%> by <%=h(issue.getModifiedByName(user))%></td></tr>
                        <tr><td class="labkey-form-label"><%=bean.getLabel("Resolved")%></td><td nowrap="true"><%=bean.writeDate(issue.getResolved())%><%= issue.getResolvedBy() != null ? " by " : ""%> <%=h(issue.getResolvedByName(user))%></td></tr>
                        <tr><td class="labkey-form-label"><%=bean.getLabel("Resolution")%></td><td><%=bean.writeSelect(new HString("resolution", false), issue.getResolution(), bean.getResolutionOptions(c), 10)%></td></tr>
        <% if (bean.isEditable("resolution") || !"open".equals(issue.getStatus().getSource()) && null != issue.getDuplicate()) { %>
                        <tr><td class="labkey-form-label">Duplicate</td><td>
                        <% if (bean.isEditable("duplicate")) { %>
                            <%=bean.writeInput(new HString("duplicate"), HString.valueOf(issue.getDuplicate()), new HString("tabindex=\"10\"" + issue.getResolution().getSource() != "Duplicate" ? " disabled" : ""))%>
                            <script type="text/javascript">
                                var duplicateInput = document.getElementsByName('duplicate')[0];
                                var duplicateOrig = duplicateInput.value;
                                var resolutionSelect = document.getElementById('resolution');
                                function updateDuplicateInput()
                                {
                                    // The options don't have an explicit value set, so look for the display text instead of
                                    // the value
                                    if (resolutionSelect.selectedIndex >= 0 &&
                                        resolutionSelect.options[resolutionSelect.selectedIndex].text == 'Duplicate')
                                    {
                                        duplicateInput.disabled = false;
                                    }
                                    else
                                    {
                                        duplicateInput.disabled = true;
                                        duplicateInput.value = duplicateOrig;
                                    }
                                }
                                if (window.addEventListener)
                                    resolutionSelect.addEventListener('change', updateDuplicateInput, false);
                                else if (window.attachEvent)
                                    resolutionSelect.attachEvent('onchange', updateDuplicateInput);
                                Ext.EventManager.on(duplicateInput, 'keypress', filterNumber);
                            </script>
                        <% } else { %>
                            <a href="<%=IssuesController.getDetailsURL(context.getContainer(), issue.getDuplicate(), false)%>"><%=issue.getDuplicate()%></a>
                        <% } %>
                        </td></tr>
        <% } %>
                        <%=bean.writeCustomColumn(c, new HString("int1", false), HString.valueOf(issue.getInt1()), IssuesController.ISSUE_NONE, 10)%>
                        <%=bean.writeCustomColumn(c, new HString("int2", false), HString.valueOf(issue.getInt2()), IssuesController.ISSUE_NONE, 10)%>
                        <%=bean.writeCustomColumn(c, new HString("string1", false), issue.getString1(), IssuesController.ISSUE_STRING1, 10)%>
                    </table>
                </td>
                <td valign="top" rowspan="6"><table>
                    <tr><td class="labkey-form-label">Closed</td><td><%=bean.writeDate(issue.getClosed())%><%= issue.getClosedBy() != null ? " by " : "" %><%=h(issue.getClosedByName(user))%></td></tr>
    <%
                if (bean.isEditable("notifyList"))
                {
    %>
                    <tr>
                        <td class="labkey-form-label-nowrap"><%=bean.getLabel("NotifyList") + popup%><br/><br/>
    <%
                        if (issue.getIssueId() == 0)
                        {
    %>
                            <%= textLink("email prefs", IssuesController.issueURL(context.getContainer(), IssuesController.EmailPrefsAction.class).getLocalURIString(), null, null, Collections.singletonMap("tabindex", "20"))%>
    <%
                        } else {
    %>
                            <%= textLink("email prefs", IssuesController.issueURL(context.getContainer(), IssuesController.EmailPrefsAction.class).addParameter("issueId", issue.getIssueId()).getLocalURIString(), null, null, Collections.singletonMap("tabindex", "20"))%>
    <%
                        }
    %>
                        </td>
                        <td><%=bean.getNotifyList(c, issue)%></td>
                    </tr>
    <%
                } else {
    %>
                    <tr><td class="labkey-form-label">Notify</td><td><%=bean.getNotifyList(c, issue)%></td></tr>
    <%
                }
    %>
                    <%=bean.writeCustomColumn(c, new HString("string2", false), issue.getString2(), IssuesController.ISSUE_STRING2, 20)%>
                    <%=bean.writeCustomColumn(c, new HString("string3", false), issue.getString3(), IssuesController.ISSUE_STRING3, 20)%>
                    <%=bean.writeCustomColumn(c, new HString("string4", false), issue.getString4(), IssuesController.ISSUE_STRING4, 20)%>
                    <%=bean.writeCustomColumn(c, new HString("string5", false), issue.getString5(), IssuesController.ISSUE_STRING5, 20)%>
                </table></td>
            </tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel("AssignedTo")%></td><td><%=bean.writeSelect(new HString("assignedTo", false), HString.valueOf(issue.getAssignedTo()), issue.getAssignedToName(user), bean.getUserOptions(c, issue, user), 0)%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel("Type")%></td><td><%=bean.writeSelect(new HString("type", false), issue.getType(), bean.getTypeOptions(c), 0)%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel("Area")%></td><td><%=bean.writeSelect(new HString("area", false), issue.getArea(), bean.getAreaOptions(c), 0)%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel("Priority")%></td><td><%=bean.writeSelect(new HString("priority", false), HString.valueOf(issue.getPriority()), bean.getPriorityOptions(c), 0)%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel("Milestone")%></td><td><%=bean.writeSelect(new HString("milestone", false), issue.getMilestone(), bean.getMilestoneOptions(c), 0)%></td></tr>
            <tr><td class="labkey-form-label">Comment</td>
                <td colspan="3">
<%
    if (bean.getBody() != null)
    {
%>
    <textarea id="comment" name="comment" cols="150" rows="20" style="width: 99%;" onchange="LABKEY.setDirty(true);return true;" tabindex="0"><%=h(bean.getBody())%></textarea>
<%
    } else {
%>
    <textarea id="comment" name="comment" cols="150" rows="20" style="width: 99%;" onchange="LABKEY.setDirty(true);return true;" tabindex="0"></textarea>
<% } %>
    </td></tr></table>
    <table>
        <tr><td><table id="filePickerTable"></table></td></tr>
        <tr><td><a href="javascript:addFilePicker('filePickerTable','filePickerLink')" id="filePickerLink"><img src="<%=context.getRequest().getContextPath()%>/_images/paperclip.gif">Attach a file</a></td></tr>
    </table>
    
<%
    if (bean.getCallbackURL() != null)
    {
%>
    <input type="hidden" name="callbackURL" value="<%=bean.getCallbackURL()%>"/>
<%
    }

    for (Issue.Comment comment : issue.getComments())
    {
%>
        <hr><table width="100%"><tr><td align="left"><b>
        <%=bean.writeDate(comment.getCreated())%>
        </b></td><td align="right"><b>
        <%=h(comment.getCreatedByName(user))%>
        </b></td></tr></table>
        <%=comment.getComment().getSource()%>
        <%=bean.renderAttachments(context, comment)%>   
<%
    }
%>
    <input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(bean.getPrevIssue())%>">
    <input type="hidden" name="action" value="<%=bean.getAction().getName()%>">
    <input type="hidden" name="issueId" value="<%=issue.getIssueId()%>">
</form>
<script type="text/javascript" for="window" event="onload">try {document.getElementById("<%=focusId%>").focus();} catch (x) {}</script>
<script type="text/javascript">

var origComment = byId("comment").value;
var origNotify = byId("notifyList").value;

function isDirty()
{
    var comment = byId("comment");
    if (comment && origComment != comment.value)
        return true;
    var notify = byId("notifyList");
    if (notify && origNotify != notify.value)
        return true;
    return false;
}

window.onbeforeunload = LABKEY.beforeunload(isDirty);
</script>

<%!
    String getNotifyHelpPopup(int emailPrefs, int issueId)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Email notifications can be controlled via either this notification list (one email address per line) ");
        sb.append("or your user <a href=\"emailPrefs.view");
        if (issueId != 0)
        {
            sb.append("?issueId=").append(issueId);
        }
        sb.append("\">email preferences</a>. ");
        if (emailPrefs != 0)
        {
            sb.append("Your current preferences to notify are:<br>");
            sb.append("<ul>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0)
                sb.append("<li>when an issue is opened and assigned to me</li>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                sb.append("<li>when an issue that's assigned to me is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_CREATED_UPDATE) != 0)
                sb.append("<li>when an issue I opened is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_SELF_SPAM) != 0)
                sb.append("<li>when I enter/edit an issue</li>");
            sb.append("</ul>");
        }
        return PageFlowUtil.helpPopup("Email Notifications", sb.toString(), true);
    }
%>
