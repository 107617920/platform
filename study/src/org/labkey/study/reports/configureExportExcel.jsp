<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.model.SiteImpl" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.reports.ExportExcelReport" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyImpl> view = (JspView<StudyImpl>)HttpView.currentView();
    StudyImpl s = view.getModelBean();
    User user = (User)request.getUserPrincipal();
    SiteImpl[] sites = s.getSites();
    boolean isAdmin = user.isAdministrator() || s.getContainer().hasPermission(user, AdminPermission.class);
 %>

<div width="600px">
Spreadheet Export allows you to export data from one site exclusively, or to export all data from all sites simultaneously.
Before you export, you can select the source site or sites using the "Site" drop-down menu.
<ul>
<li><b>All Sites</b>. If you select "All Sites" from the dropdown "Site" menu, you will export all data for all
    <%= StudyService.get().getSubjectNounPlural(getViewContext().getContainer())%> across all sites.</li>
<li ><b>Single Site</b>. If you select a particular site from the "Site" menu, you will export only data associated with the chosen site.</li>
</ul>
</div>
<% if (isAdmin)
{ %><%    WebPartView.startTitleFrame(out, "Administrative Options", null, "600", null);%>
As an administrator you can export via the "Export" button or save a view definition to the server via the "Save" button.<br><br>
When you save the view definition, it will be listed in the reports and views web part. Each time a user clicks on the view, the current data will be downloaded.
The saved view can also be secured so that only a subset of users (e.g. users from the particular site) can see it.<br><br>

    Requirements for retrieving data for a single site:
    <ol>
    <li>You must have imported a Specimen Archive in order for the "Sites" dropdown to list sites. The Specimen Archive defines a list of sites for your Study. </li>
    <li>You must associate ParticipantIDs with CurrentSiteIds via a "Participant Dataset". This step allows participant data records to be mapped to sites.</li>
    </ol> See the <a href="<%=new HelpTopic("exportExcel", HelpTopic.Area.STUDY).getHelpTopicLink()%>">help page</a> for more information.
<% WebPartView.endTitleFrame(out);
}

%>
<%    WebPartView.startTitleFrame(out, "Configure", null, "600", null);%>
    <form action="exportExcel.view" method=GET>
    <table><tr><th class="labkey-form-label">Site</th><td><select <%= isAdmin ? "onChange='siteId_onChange(this)'" : ""%> id=siteId name=siteId><option value="0">ALL</option>
<%
for (SiteImpl site : sites)
{
    String label = site.getLabel();
    if (label == null || label.length() == 0)
        label = "" + site.getRowId();
    %><option value="<%=site.getRowId()%>"><%=h(label)%></option><%
}
%></select></td></tr>
<%  if (isAdmin)
    {
    %>
        <tr><th class="labkey-form-label">Report name</th><td><input style="width:250;" type=text id=label name=label value=""></td></tr><%
    } %>
</table>
<%=PageFlowUtil.generateSubmitButton("Export")%>
        <% if (isAdmin)
        {   %>
            <input type=hidden name=reportType value="<%=ExportExcelReport.TYPE%>">
            <input type=hidden id=params name=params value="siteId=-1">
            <%=PageFlowUtil.generateSubmitButton("Save", "this.form.action='saveReport.view'")%>

        <script type="text/javascript">
        var sites = {};
        sites['0'] = 'ALL';
            <%
            for (SiteImpl site : sites)
            {
                String label = site.getLabel();
                if (label == null || label.length() == 0)
                    label = "" + site.getRowId();
                %>sites['<%=site.getRowId()%>']=<%=PageFlowUtil.jsString(label)%>;<%
                out.print("\n");
            }%>

        siteId_onChange(document.getElementById("siteId"));

        function siteId_onChange(select)
        {
            var paramsInput = document.getElementById("params");
            paramsInput.value = "siteId=" + select.value;
            var labelInput = document.getElementById("label");
            labelInput.value = "Export to worksheet: " + sites[select.value];
        }
        </script>
        <%}%>

</form>
<%

    WebPartView.endTitleFrame(out);
%>





