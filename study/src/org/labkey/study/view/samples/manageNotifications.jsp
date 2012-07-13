<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.security.SecurityUrls"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.samples.settings.RequestNotificationSettings" %>
<%@ page import="org.labkey.study.samples.settings.RequestNotificationSettings.*" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<script type="text/javascript">LABKEY.requiresScript('completion.js');</script>
<script type="text/javascript">
function setElementDisplayByCheckbox(checkbox, element)
{
    var target = document.getElementById(element);
    if (document.getElementById(checkbox).checked)
        target.style.display = "";
    else
        target.style.display = "none";
  }
</script>

<%
    JspView<RequestNotificationSettings> me =
            (JspView<RequestNotificationSettings>) HttpView.currentView();
    RequestNotificationSettings bean = me.getModelBean();
    Container container = HttpView.getRootContext().getContainer();

    String completionURLPrefix = urlProvider(SecurityUrls.class).getCompleteUserURLPrefix(container);
    boolean newRequestNotifyChecked = ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()) ?
            bean.isNewRequestNotifyCheckbox() : (h(bean.getNewRequestNotify()) != null &&
            h(bean.getNewRequestNotify()).compareTo("") != 0));
    boolean ccChecked = ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()) ?
            bean.isCcCheckbox() : (h(bean.getCc()) != null && h(bean.getCc()).compareTo("") != 0));
    DefaultEmailNotifyEnum defaultEmailNotifyEnum = (bean.getDefaultEmailNotifyEnum());         // Checking getMethod bot needed because one of the radio buttons will always POST
%>

<style type="text/css">
    .local-text-block
    {
        border-top:1px solid;
        padding-top: 10px;
    }

    .local-left-label-width-th
    {
        width:175px;
    }
</style>

<labkey:errors/>

<form action="manageNotifications.view" method="POST">
    <table class="labkey-manage-display" width="90%">
        <tr>
            <td colspan="2" style="font-size: 14px;padding-bottom: 10px">The specimen request system sends emails as requested by the specimen administrator.
                Some properties of these email notifications can be configured here.</td>
        </tr>
        <tr>
            <td colspan="2" class="local-text-block">Notification emails will be sent from the specified reply-to address.
            This is the address that will receive replies and error messages, so it should be a monitored address.</td>
        </tr>
        <tr>
        <tr>
            <th align="right" rowspan="4" class="labkey-form-label local-left-label-width-th">Reply-to Address:</th>
            <td>Replies to specimen request notications should go to:</td>
                <%
                    boolean replyToCurrentUser = RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE.equals(bean.getReplyTo());
                %>
        </tr>
        <tr>
            <td>
                <input type='radio' id='replyToCurrentUser' name='replyToCurrentUser' value='true' <%= replyToCurrentUser ? "CHECKED" : "" %>
                        onclick="document.getElementById('replyTo').value = '<%= h(RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE) %>'; setElementDisplayByCheckbox('replyToFixedUser', 'replyTo');">
                The administrator who generated each notification
            </td>
        </tr>
        <tr>
            <td>
                <input type='radio' id='replyToFixedUser'  name='replyToCurrentUser'  value='false' <%= !replyToCurrentUser ? "CHECKED" : "" %>
                        onclick="setElementDisplayByCheckbox('replyToFixedUser', 'replyTo'); document.getElementById('replyTo').value = '<%= !replyToCurrentUser ? h(bean.getReplyTo()) : "" %>';">
                A fixed email address:
            </td>
        </tr>
        <tr>
            <td>
                <input type="text" size="40" name="replyTo"
                       id='replyTo' value="<%= h(bean.getReplyTo()) %>"
                       style="display:<%= replyToCurrentUser ? "none" : "" %>">
            </td>
        </tr>

        <tr>
            <td colspan="2"  class="local-text-block">All specimen request emails have the same subject line.  <b>%requestId%</b> may be used
                to insert the specimen request's study-specific ID number.  The format for the subject line is:
                <b><%= h(StudyManager.getInstance().getStudy(container).getLabel()) %>: [Subject Suffix]</b>
            </td>
        </tr>
        <tr>
            <th class="labkey-form-label local-left-label-width-th" align="right">Subject Suffix:</th>
            <td>
                <input type="text" size="40" name="subjectSuffix" value="<%= h(bean.getSubjectSuffix()) %>">
            </td>
        </tr>
        <tr>
            <td colspan="2" class="local-text-block">Notification can be sent whenever a new specimen request is submitted.</td>
        </tr>
        <tr>
            <td colspan="2"><input type='checkbox' value='true' id='newRequestNotifyCheckbox'
                        name='newRequestNotifyCheckbox'
                        onclick="setElementDisplayByCheckbox('newRequestNotifyCheckbox', 'newRequestNotifyArea');"
                        <%= newRequestNotifyChecked ? " checked" : ""%>>Send Notification of New Requests</td>
        </tr>
        <tr id="newRequestNotifyArea" style="display:<%= newRequestNotifyChecked ? "" : "none"%>">
            <th align="right" class="labkey-form-label local-left-label-width-th">Notify of new requests<br>(one per line):</th>
            <td>
                <textarea name="newRequestNotify" id="newRequestNotify" cols="30" rows="3"
                        onKeyDown="return ctrlKeyCheck(event);"
                        onBlur="hideCompletionDiv();"
                        autocomplete="off"
                        onKeyUp="return handleChange(this, event, '<%= completionURLPrefix %>');"><%= h(bean.getNewRequestNotify()) %></textarea>
            </td>
        </tr>
        <tr>
            <td colspan="2" class="local-text-block">Email addresses listed under "always CC" will receive a single copy of each email notification.
                Please keep security issues in mind when adding users to this list.</td>
        </tr>
        <tr>
            <td colspan="2"><input type='checkbox' value='true' id='ccCheckbox'
                        name='ccCheckbox'
                        onclick="setElementDisplayByCheckbox('ccCheckbox', 'ccArea');"
                        <%= ccChecked ? " checked" : ""%>>Always Send CC</td>
        </tr>

        <tr id="ccArea" style="display:<%= ccChecked ? "" : "none"%>">
            <th align="right"class="labkey-form-label local-left-label-width-th">Always CC<br>(one per line):</th>
            <td>
                <textarea name="cc" id="cc" cols="30" rows="3"
                        onKeyDown="return ctrlKeyCheck(event);"
                        onBlur="hideCompletionDiv();"
                        autocomplete="off"
                        onKeyUp="return handleChange(this, event, '<%= completionURLPrefix %>');"><%= h(bean.getCc() )%></textarea>
            </td>
        </tr>

        <tr>
            <td colspan="2" class="local-text-block">Each request requirement notification email allows you to specify which actors will receive the email.
                The selection below indicates which actors will receive the email if the coordinator does not explicitly override.</td>
        </tr>
        <tr>
            <th align="right" rowspan="3" class="labkey-form-label local-left-label-width-th">Default Email Recipients:</th>
            <td><input type='radio' value='<%=DefaultEmailNotifyEnum.All%>'
                       name='defaultEmailNotify'
                       <%= defaultEmailNotifyEnum == DefaultEmailNotifyEnum.All ? " checked" : ""%>>All</input>
            </td>
        </tr>
        <tr>
            <td><input type='radio' value='<%=DefaultEmailNotifyEnum.None%>'
                       name='defaultEmailNotify'
                       <%= defaultEmailNotifyEnum == DefaultEmailNotifyEnum.None ? " checked" : ""%>>None</input>
            </td>
        </tr>
        <tr>
            <td><input type='radio' value='<%=DefaultEmailNotifyEnum.ActorsInvolved%>'
                       name='defaultEmailNotify'
                       <%= defaultEmailNotifyEnum == DefaultEmailNotifyEnum.ActorsInvolved ? " checked" : ""%>>Notify Actors Involved</input>
            </td>
        </tr>
        <tr>
            <td colspan="2" class="local-text-block"></td>
        </tr>
        <tr>
            <th>&nbsp;</th>
            <td>
                <%= generateSubmitButton("Save") %>&nbsp;
                <%= generateButton("Cancel", new ActionURL(StudyController.ManageStudyAction.class, container))%>
            </td>
        </tr>

    </table>
</form>



