<%--
/*
 * Copyright (c) 2007-2015 LabKey Corporation
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
--%>
<%@ page buffer="none" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.analytics.AnalyticsService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.template.PrintTemplate" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page session="true" %>
<%
    PrintTemplate me = (PrintTemplate) HttpView.currentView();
    PageConfig bean = me.getModelBean();
    ActionURL url = getActionURL();
    Container c = getContainer();
    User user = getUser();
    ThemeFont themeFont = ThemeFont.getThemeFont(c);
    boolean isPrint = bean.getTemplate() == PageConfig.Template.Print;
    String themeClass = themeFont.getClassName() + (isPrint ? " print": "");

    if (bean.getFrameOption() != PageConfig.FrameOption.ALLOW)
        response.setHeader("X-FRAME-OPTIONS", bean.getFrameOption().name());

    String onLoad = "";
    if (StringUtils.isNotEmpty(bean.getFocus()))
    {
        onLoad += "(document." + bean.getFocus() + "?document." + bean.getFocus() + ".focus():null);";
    }
    if (bean.getShowPrintDialog())
    {
        onLoad += "window.print(); ";
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="X-UA-Compatible" content="IE=Edge" />
    <%
        if (bean.getFrameOption() == PageConfig.FrameOption.DENY)
        {
    %>
    <script type="text/javascript">if (top != self) top.location.replace(self.location.href);</script>
    <%
        }
    %>
    <title><%=h(bean.getTitle()) %></title>
    <!-- <%=h(url.getURIString())%> -->
<%= text(bean.getMetaTags(url)) %>
<%= PageFlowUtil.getStandardIncludes(getViewContext(), bean.getClientDependencies()) %>
    <%
        if (null != bean.getRssUrl())
        {
    %>
    <link href="<%=bean.getRssUrl().getEncodedLocalURIString()%>" type="application/rss+xml" title="<%=h(bean.getRssTitle())%>" rel="alternate"/>
    <%
        }
    %>
    <% if (bean.getAllowTrackingScript())
    {
        String script = AnalyticsService.getTrackingScript();
        if (StringUtils.isNotEmpty(script))
        {
            if (user.isSiteAdmin())
            {
    %>      <!-- see <%=new ActionURL("analytics","begin",ContainerManager.getRoot()).getURIString()%> -->
    <%
            }
    %>
    <%=script%>
    <%
        }
    }
    %>
</head>

<body id="bodyElement" onload="<%=h(onLoad)%>" class="labkey-main <%= text(themeClass) %>">
<%
    if (me.includeGWT())
    {   //Only include first js file??
%>
<iframe id="__gwt_historyFrame" style="width:0;height:0;border:0"></iframe>
<%
    }
%>
<div class="labkey-main <%= text(themeClass) %>">
    <%
        if (bean.showHeader() != PageConfig.TrueFalse.False)
        {
            %><div class="header-block"><%
            me.include(me.getView("header"),out);

            if (null != me.getView("navigation"))
                me.include(me.getView("navigation"),out);

            if (null != me.getView("appbar"))
                me.include(me.getView("appbar"), out);
            %></div><%
        }
    %>
     <!--content area-->
    <div class="body-block push-block">
        <table class="labkey-proj">
            <tr>
                <%
                    if (null != me.getView("moduleNav"))
                    {
                %>
                <td align=left valign=top class=normal width="200px" height="100%" style="padding:5;">
                    <%
                        me.include(me.getView("moduleNav"), out);
                    %>
                </td>
                <%
                    }
                %>
                <td id="bodypanel" class="labkey-body-panel" style="min-width:<%=bean.getMinimumWidth()%>px;">
                    <img height=1 width=<%=bean.getMinimumWidth()%> style="height:1px;" src="<%=getWebappURL("/_.gif")%>">
                    <br />
                    <!-- BODY -->
                    <% me.include(me.getBody(),out); %>
                    <!-- /BODY -->
                </td>
                <%
                    if (me.getView("right") instanceof HttpView && ((HttpView)me.getView("right")).isVisible())
                    {
                %>
                <!-- RIGHT -->
                <td class="labkey-side-panel" style="min-width:240px;">
                    <img height=1 width=240 src="<%=getWebappURL("_.gif")%>"><br>
                    <% me.include(me.getView(WebPartFactory.LOCATION_RIGHT),out); %>
                </td>
                <!-- /RIGHT -->
                <%
                    }
                %>
            </tr>
        </table>
    </div>
    <!--/content area-->
        <%
            if (null != me.getView("footer"))
            {
                out.println("<div class='footer-block'>");
                me.include(me.getView("footer"),out);
                out.println("</div>");
            }
        %>
</div>
<%
    String anchor = bean.getAnchor();
    if (null == StringUtils.trimToNull(anchor))
        anchor = StringUtils.trimToNull(request.getParameter("_anchor"));

    if (null != anchor)
    {
%>
<script type="text/javascript" for="window" event="onload">window.location.href = "#<%=h(anchor)%>"</script>
<%
    }
%>

<script type="text/javascript">LABKEY.loadScripts(); LABKEY.showNavTrail();</script>
<!--
<%= h(request.getHeader("User-Agent")) %>-->
<a href="<%= me.getPermaLink() %>" id="permalink" style="display: none;"></a>
</body>
</html>
