<%
/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.WizardTemplate" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    WizardTemplate me = (WizardTemplate) HttpView.currentView();
    PageConfig pageConfig = me.getModelBean();
    Container c = me.getViewContext().getContainer();

    if (pageConfig.getFrameOption() != PageConfig.FrameOption.ALLOW)
        response.setHeader("X-FRAME-OPTIONS", pageConfig.getFrameOption().name());
%>
<!DOCTYPE html>
<html>
<head>
    <%if (pageConfig.getFrameOption() == PageConfig.FrameOption.DENY) {%> <script type="text/javascript">if (top != self) top.location.replace(self.location.href);</script><%}%>
    <title><%=h(pageConfig.getTitle())%></title>
    <%= pageConfig.getMetaTags(me.getViewContext().getActionURL()) %>
    <%= PageFlowUtil.getStandardIncludes(c,request.getHeader("User-Agent")) %>
</head>

<body<%= null != pageConfig.getFocus() ? " onload=\"document." + pageConfig.getFocus() + ".focus();\"" : "" %>>
    <table class="labkey-main"><%

    if (pageConfig.showHeader() != PageConfig.TrueFalse.False)
    { %>
    <tr id="headerpanel" class="labkey-header-panel">
        <td><% me.include(me.getView("header"), out); %></td>
    </tr>
    <tr>
        <td class="labkey-title-area-line"></td>
    </tr><%
    } %>
    <tr>
        <td class="labkey-full-screen-background">
            <table class="labkey-full-screen-table">
                <tr>
                    <td class="labkey-fullscreen-wizard-background">
                        <ol class="labkey-fullscreen-wizard-steps">
                            <% for (NavTree navTree : pageConfig.getNavTrail()) { %>
                                <li <%= navTree.getText().equals(pageConfig.getTitle()) ? "class=\"labkey-fullscreen-wizard-active-step\"" : ""%>><%= navTree.getText() %></li>
                            <% } %>
                        </ol>
                    </td>
                    <td id="wizardBody" class="labkey-fullscreen-wizard-body">
                        <div style="padding: 3em">
                            <span class="labkey-nav-page-header"><%= h(pageConfig.getTitle()) %></span>
                            <script type="text/javascript">
                                LABKEY.requiresClientAPI();
                            </script>
                            <% me.include(me.getBody(), out);%>
                        </div>
                    </td>
                </tr>
            </div>
        </td>
    </tr>
    </table>
<script type="text/javascript">
    LABKEY.loadScripts();
</script>
</body>
</html>
