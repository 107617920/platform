<%
    /*
     * Copyright (c) 2012-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.olap4j.metadata.Cube" %>
<%@ page import="org.olap4j.metadata.Dimension" %>
<%@ page import="org.olap4j.metadata.Hierarchy" %>
<%@ page import="org.olap4j.metadata.Level" %>
<%@ page import="org.olap4j.metadata.Member" %>
<%@ page import="org.labkey.query.olap.OlapSchemaDescriptor" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.labkey.query.olap.OlapSchemaCache" %>
<%@ page import="org.olap4j.metadata.Schema" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.controllers.OlapController" %>
<%@ page import="org.olap4j.OlapConnection" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<labkey:errors></labkey:errors>
<%
    Collection<OlapSchemaDescriptor> list = OlapSchemaCache.get().getDescriptors(getContainer());
    for (OlapSchemaDescriptor sd : list)
    {
        %><h3><%=h(sd.getName())%></h3><%
        try (OlapConnection conn = sd.getConnection(getContainer(), getUser()))
        {
            for (Schema s : sd.getSchemas(conn,getContainer(), getUser()))
            {
                %><ul><%
                for (Cube c : s.getCubes())
                {
                    ActionURL url = new ActionURL(OlapController.TestBrowserAction.class, getContainer());
                    url.addParameter("configId",sd.getId());
                    url.addParameter("schemaName",s.getName());
                    url.addParameter("cubeName",c.getName());
                    %><li><%=textLink(c.getName(),url)%></li><%
                }
                %></ul><%
            }
        }
    }

    Cube cube = (Cube)HttpView.currentModel();
    if (null == cube)
        return;
%>
<script>

    var cube = null;
    function Level_onClick(name)
    {
        var elems = Ext.DomQuery.select("#members div.membersList").forEach(function(d){d.style.display='none'});
        Ext.get(name).applyStyles({display:'block'});
    }
</script>
<labkey:errors></labkey:errors>
<%
    if (null != cube)
    {
%>
<table><tr><td valign=top style="padding:10px;">
    <div style="background-color:#eeeeee; padding:10px; min-width:400px; min-height:800px;">
        <ul style="margin:0; font-size:8pt;"><%
            for (Dimension d : cube.getDimensions())
            {
        %><li><%=h(d.getUniqueName())%><ul><%
            for (Hierarchy h : d.getHierarchies())
            {
        %><li><%=h(h.getUniqueName())%><ol start=0><%
            for (Level l : h.getLevels())
            {
        %><li><a href="#<%=h(l.getUniqueName())%>" onclick="Level_onClick(<%=q(l.getUniqueName())%>)"><%=h(l.getUniqueName())%></a></li><%
            }
        %></ol></li><%
            }
        %></ul></li><%
            }
        %></ul>
    </div></td>
    <td id=members valign=top style="padding:10px;">
        <div style="background-color:#eeeeee; padding:10px; min-width:400px; min-height:800px;">
            <%
                for (Dimension d : cube.getDimensions())
                {
                    for (Hierarchy h : d.getHierarchies())
                    {
                        for (Level l : h.getLevels())
                        {
            %><div class="membersList" style="display:none; font-size:8pt;" id="<%=h(l.getUniqueName())%>"><%
            for (Member m : l.getMembers())
            {
        %><%=h(m.getUniqueName())%><br><%
            }
        %></div><%
                    }
                }
            }
        %></div></td></tr></table>
<%
    }
%>