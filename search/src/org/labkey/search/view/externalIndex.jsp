<%
/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.search.SearchController.ClearExternalIndexAction" %>
<%@ page import="org.labkey.search.SearchController.PermissionsAction" %>
<%@ page import="org.labkey.search.SearchController.SwapExternalIndexAction" %>
<%@ page import="org.labkey.search.model.ExternalAnalyzer" %>
<%@ page import="org.labkey.search.model.ExternalIndexProperties" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<ExternalIndexProperties> me = (JspView<ExternalIndexProperties>) HttpView.currentView();
User user = me.getViewContext().getUser();
ExternalIndexProperties props = me.getModelBean();
SearchService ss = ServiceRegistry.get().getService(SearchService.class);
String message = me.getViewContext().getActionURL().getParameter("externalMessage");

if (null != ss)
{
    %><p><form method="POST" action="setExternalIndex.post">
        <table><%
        if (!StringUtils.isEmpty(message))
        { %>
            <tr><td colspan="2"><span style="color:green;"><br><%=h(message)%><br></span><br></td></tr><%
        } %>
            <tr><td colspan="2" width="500">
                You can (optionally) integrate searching of other web sites (e.g., your organization's intranet) with LabKey
                Server's search functionality by configuring an external index.  For example, you could generate a Lucene
                index using Nutch (an open-source web crawler), copy the index to a location accessible to your LabKey
                Server, and configure searching of that index below.<br><br>
            </td></tr>
            <tr><td colspan="2" width="500">
                See the <%=helpLink("searchAdmin", "Search Administration documentation")%> for more details about configuring and updating an external index.<br><br>
            </td></tr>
            <tr><td>External index description:</td><td><input name="externalIndexDescription" size="60" value="<%=h(props.getExternalIndexDescription())%>"/></td></tr>
            <tr><td>Path to external index directory:</td><td><input name="externalIndexPath" size="60" value="<%=h(props.getExternalIndexPath())%>"/></td></tr>
            <tr><td>Analyzer:</td><td>
                <select name="externalIndexAnalyzer"><%
                    String currentAnalyzer = props.getExternalIndexAnalyzer();

                    for (ExternalAnalyzer a : ExternalAnalyzer.values())
                    { %>
                        <option<%=(a.toString().equals(currentAnalyzer) ? " selected" : "")%>><%=a.toString()%></option><%
                    }
                %>
                </select>
            </td></tr>
            <%

            if (user.isAdministrator())
            {
            %>
            <tr><td colspan="2">
                <%=generateSubmitButton("Set")%>
                <% if (props.hasExternalIndex())
                { %>
                <%=generateButton("Clear", ClearExternalIndexAction.class)%>
                <%=generateButton("Update Index", SwapExternalIndexAction.class)%>
                <%=generateButton("Set Permissions", PermissionsAction.class)%>
                <% } %>
            </td></tr><%
            }
            %>
        </table>
    </form>
<%
}
%>