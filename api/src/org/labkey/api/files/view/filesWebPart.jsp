<%
/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.files.view.FilesWebPart" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("File");
        dependencies.add("Ext4ClientApi"); // LABKEY.ext4.Util.resizeToViewport
    }
%>
<%
    JspView<FilesWebPart.FilesForm> me = (JspView) HttpView.currentView();
    FilesWebPart.FilesForm bean = me.getModelBean();
    Container c = getContainer();

    ActionURL projConfig = urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
    int height = null == bean.getHeight() ? 350 : bean.getHeight();

    if (!bean.isEnabled())
    {
%>
    File sharing has been disabled for this project. Sharing can be configured from the <a href="<%=projConfig%>">project settings</a> view.
<%
    }
    else if (!bean.isRootValid())
    {
%>
    <span class="labkey-error">
        The file root for this folder is invalid. It may not exist or may have been configured incorrectly.<br>
        <%=text(c.hasPermission(getUser(), AdminPermission.class) ? "File roots can be configured from the <a href=\"" + projConfig + "\">project settings</a> view." : "Contact your administrator to address this problem.")%>
    </span>
<%
    }
    else
    {
%>
<!-- 19142: Browser action tooltips are clipped in IE -->
<style type="text/css">
    #ext-quicktips-tip-innerCt {
        white-space: nowrap;
    }
</style>
<div id="<%=h(bean.getContentId())%>"></div>
<script type="text/javascript">
    Ext4.onReady(function() {
        var buttonActions = [<% String sep = ""; for(FilesWebPart.FilesForm.actions action  : bean.getButtonConfig()) {%><%=text(sep)%>'<%=text(action.name())%>'<% sep = ","; }%>];
        var startDirectory;

        <%
            if (bean.getDirectory() != null)
            {
        %>
        startDirectory = <%=PageFlowUtil.jsString(bean.getDirectory().toString())%>;
        <%
            }
        %>

        var config = {
            renderTo: <%=q(bean.getContentId())%>,
            containerPath: <%=q(c.getPath())%>,
            fileSystem: Ext4.create('File.system.Webdav', {
                rootPath: <%=q(bean.getRootPath())%>,
                rootOffset: <%=q(bean.getRootOffset())%>,
                rootName: 'fileset'
            }),
            startDirectory: startDirectory,
            height: <%= height %>,
            folderTreeOptions: {
                hidden: <%=!bean.isShowFolderTree()%>,
                collapsed: <%=bean.isFolderTreeCollapsed()%>
            },
            disableGeneralAdminSettings: <%=bean.isDisableGeneralAdminSettings()%>,
            showDetails: <%=bean.isShowDetails()%>,
            expandUpload: <%=bean.isExpandFileUpload()%>,
            isPipelineRoot: <%=bean.isPipelineRoot()%>,
            adminUser: <%=c.hasPermission(getUser(), AdminPermission.class)%>,
            statePrefix: <%=q(bean.getStatePrefix())%>,
            actions: buttonActions
        };

        <%
            if (bean.isListing())
            {
        %>
        Ext4.apply(config, {
            allowSelection: false,
            expandUpload: false,
            minWidth: 250,
            useServerActions: false,
            useServerFileProperties: false,
            showColumnHeaders: false,
            showFolderTree: false,
            showToolbar: false,
            showUpload: false,
            listDirectories: false,
            actions: ['parentFolder', 'refresh'],
            columns: ['iconfacls', 'name'],
            folderTreeOptions: {
                hidden: true,
                collapsed: true
            }
        });
        <%
            }
        %>

        var fb = Ext4.create('File.panel.Browser', config);

        var _resize = function(w, h) {
            if (!fb || !fb.rendered)
                return;

            <% if (me.getFrame() == WebPartView.FrameType.PORTAL) {%>
                var paddingX = 26;
                var paddingY = 95;
            <%}else{%>
                var paddingX = 20;
                var paddingY = 35;
            <%}%>
            LABKEY.ext4.Util.resizeToViewport(fb, w, h, paddingX, paddingY);
            fb.detailCheck();
        };

        if (<%=bean.isAutoResize()%>) {
            Ext4.EventManager.onWindowResize(_resize);
            Ext4.defer(function(){
                var size = Ext4.getBody().getBox();
                _resize(size.width, size.height);
            }, 300);
        }
    });
</script>
<%
    }
%>
