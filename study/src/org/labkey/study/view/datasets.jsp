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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="org.labkey.study.model.StudyImpl"%>
<%@ page import="org.labkey.study.model.StudyManager"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView me = (JspView)HttpView.currentView();
ViewContext ctx = me.getViewContext();
StudyManager manager = StudyManager.getInstance();
Container container = ctx.getContainer();
Study study = manager.getStudy(container);
User user = ctx.getUser();
DataSetDefinition[] datasets = manager.getDataSetDefinitions(study);

if (null == datasets || datasets.length == 0)
{
    out.print("No datasets defined<br><br>");
    if (container.hasPermission(user, AdminPermission.class))
    {
        out.print(textLink("Manage Datasets", ctx.getActionURL().relativeUrl("manageTypes.view", null, "Study")));
    }
    return;
}

List<DataSetDefinition> userDatasets = new ArrayList<DataSetDefinition>();
for (DataSetDefinition dataSet : datasets)
{
    if (!dataSet.isShowByDefault())
        continue;

    if (dataSet.canRead(ctx.getUser()))
        userDatasets.add(dataSet);
}

int datasetsPerCol = userDatasets.size() / 3;
%>
<table width="100%"><tr>
    <td valign=top><%=renderDatasets(ctx, userDatasets, 0, datasetsPerCol + 1)%></td>
    <td valign=top><%=renderDatasets(ctx, userDatasets, datasetsPerCol + 1, (2 * datasetsPerCol) + 1)%></td>
    <td valign=top><%=renderDatasets(ctx, userDatasets, (2 * datasetsPerCol) + 1, userDatasets.size())%></td>
</tr></table>
<%
    if (container.hasPermission(user, AdminPermission.class))
        out.print("<br>" + textLink("Manage Datasets", ctx.getActionURL().relativeUrl("manageTypes.view", null, "Study")));
%>
    <%!
        String renderDatasets(ViewContext ctx, List<DataSetDefinition> datasets, int startIndex, int endIndex)
        {
            StringBuffer sb = new StringBuffer();
            if (startIndex >= datasets.size() || startIndex >= endIndex)
                return "";

            String category = startIndex == 0 ? null : datasets.get(startIndex-1).getCategory();
            ActionURL datasetURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, ctx.getContainer());
            sb.append("<table>\n");
            //Print a column header if necessary
            DataSet firstDataset = datasets.get(startIndex);
            if (!equal(category, firstDataset.getCategory()))
            {
                category = firstDataset.getCategory();
                sb.append("<tr><td class=\"labkey-announcement-title\"><span>");
                sb.append(h(category == null ? "Uncategorized" : category));
                sb.append("</span></td></tr>\n");
                sb.append("<tr><td class=\"labkey-title-area-line\"><img height=\"1\" width=\"1\" src=\"/labkey/_.gif\"/></td></tr>\n");
            }
            else if (null != category)
            {
                sb.append("<tr><td class=\"labkey-announcement-title\"><span>");
                sb.append(h(category)).append(" (Continued)");
                sb.append("</span></td></tr>\n");
                sb.append("<tr><td class=\"labkey-title-area-line\"><img height=\"1\" width=\"1\" src=\"/labkey/_.gif\"/></td></tr>\n");
            }

            for (DataSet dataSet : datasets.subList(startIndex, endIndex))
            {
                if (!equal(category, dataSet.getCategory()))
                {
                    category = dataSet.getCategory();
                    sb.append("<tr><td class=\"labkey-announcement-title\"><span>").append(h(category == null ? "Uncategorized" : category)).append("</span></td></tr>\n");
                    sb.append("<tr><td class=\"labkey-title-area-line\"><img height=\"1\" width=\"1\" src=\"/labkey/_.gif\"/></td></tr>\n");
                }

                String dataSetLabel = (dataSet.getLabel() != null ? dataSet.getLabel() : "" + dataSet.getDataSetId());

                sb.append("        <tr><td>");
                sb.append("<a href=\"").append(datasetURL.replaceParameter("datasetId", String.valueOf(dataSet.getDataSetId())));
                sb.append("\">");
                sb.append(h(dataSetLabel));
                sb.append("</a></td></tr>\n");
            }
            sb.append("    </table>");

            return sb.toString();
        }

        boolean equal(String s1, String s2)
         {
            if ((s1 == null) != (s2 == null))
                return false;

            if (null == s1)
                return true;

            return s1.equals(s2);
         }
    %>
