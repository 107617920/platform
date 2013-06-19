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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="org.labkey.core.webdav.DavController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("File"));
      return resources;
  }
%>
<%
    DavController.ListPage listpage = (DavController.ListPage) HttpView.currentModel();
    WebdavResource resource = listpage.resource;
    AppProps.Interface app = AppProps.getInstance();
%>
<script type="text/javascript">

    Ext4.onReady(function() {

        var htmlViewAction = new Ext4.Action({
            text : 'HTML View',
            handler : function() {
                window.location = <%=q(h(resource.getLocalHref(getViewContext())+"?listing=html"))%>;
            }
        });

        var fileSystem = Ext4.create('File.system.Webdav', {
            baseUrl  : <%=q(Path.parse(request.getContextPath()).append(listpage.root).encode("/",null))%>,
            offsetUrl: <%=q(listpage.resource.getPath().toString())%>,
            rootName : <%=q(app.getServerName())%>
        });

        Ext4.create('Ext.container.Viewport', {
            layout : 'fit',
            items : [{
                xtype : 'filebrowser',
                border : false,
                isWebDav  : true,
                fileSystem : fileSystem,
                gridConfig : {
                    selType : 'rowmodel'
                },
                tbarItems : ['->', htmlViewAction]
            }]
        });

    });
</script>
