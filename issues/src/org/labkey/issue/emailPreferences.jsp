<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuesController.EmailPrefsBean> me = (JspView<IssuesController.EmailPrefsBean>)HttpView.currentView();
    ViewContext context = me.getViewContext();
    IssuesController.EmailPrefsBean bean = me.getModelBean();
    int emailPrefs = bean.getEmailPreference();
    BindException errors = bean.getErrors();
    String message = bean.getMessage();
    int issueId = bean.getIssueId();

    if (message != null)
    {
        %><b><%=h(message)%></b><p/><%
    }

    if (null != errors && errors.getErrorCount() > 0)
    {
        for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
        {
            %><span class=labkey-error><%=h(context.getMessage(e))%></span><br><%
        }
    }
%>
<form action="emailPrefs.post" method="post">
    <input type="checkbox" value="1" name="emailPreference" <%=(emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0 ? " checked" : ""%>>
    Send me email when an issue is opened and assigned to me<br>
    <input type="checkbox" value="2" name="emailPreference" <%=(emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0 ? " checked" : ""%>>
    Send me email when an issue that's assigned to me is modified<br>
    <input type="checkbox" value="4" name="emailPreference" <%=(emailPrefs & IssueManager.NOTIFY_CREATED_UPDATE) != 0 ? " checked" : ""%>>
    Send me email when an issue I opened is modified<br>
    <hr/>
    <input type="checkbox" value="8" name="emailPreference" <%=(emailPrefs & IssueManager.NOTIFY_SELF_SPAM) != 0 ? " checked" : ""%>>
    Send me email notifications when I enter/edit an issue<br>
    <br>
    <%=PageFlowUtil.generateSubmitButton("Update")%><%
    if (issueId > 0)
    {
        %><%= generateButton("Back to Issue", IssuesController.issueURL(context.getContainer(), "details").addParameter("issueId", bean.getIssueId())) %><%
    }
    else
    {
        %><%= generateButton("View Grid", IssuesController.issueURL(context.getContainer(), "list").addParameter(DataRegion.LAST_FILTER_PARAM, "true")) %><%
    }
%>
</form>