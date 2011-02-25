<%
/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("applet.js");
    LABKEY.requiresScript("StatusBar.js");
    LABKEY.requiresScript("fileBrowser.js");
    LABKEY.requiresScript("FileUploadField.js");
    LABKEY.requiresScript("ActionsAdmin.js");
    LABKEY.requiresScript("PipelineAction.js");
    LABKEY.requiresScript("FileProperties.js");
    LABKEY.requiresScript("FileContent.js");
    Ext.QuickTips.init();
</script>

<style type="text/css">
    .x-layout-mini
    {
        display: none;
    }
</style>


<%
    ViewContext context = HttpView.currentContext();
    FilesWebPart.FilesForm bean = (FilesWebPart.FilesForm)HttpView.currentModel();

    Container c = context.getContainer();

    ActionURL projConfig = urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
    int height = 350;
%>

<%  if (!bean.isEnabled()) { %>

    File sharing has been disabled for this project. Sharing can be configured from the <a href="<%=projConfig%>">project settings</a> view.    

<%  } else if (!bean.isRootValid()) { %>

    <span class="labkey-error">
        The file root for this folder is invalid. It may not exist or may have been configured incorrectly.<br>
        <%=c.hasPermission(context.getUser(), AdminPermission.class) ? "File roots can be configured from the <a href=\"" + projConfig + "\">project settings</a> view." : "Contact your administrator to address this problem."%>
    </span>

<%  } %>

<!-- Set a fixed height for this div so that the whole page doesn't relayout when the file browser renders into it -->
<div class="extContainer" style="height: <%= height %>px" id="<%=bean.getContentId()%>"></div>

<script type="text/javascript">

    /**
     * activate the Ext state manager (for directory persistence), but by default, make all components
     * not try to load state.
     */
    Ext.state.Manager.setProvider(new Ext.state.CookieProvider());
    Ext.override(Ext.Component,{
        stateful:false
    });

Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";
Ext.QuickTips.init();

var autoResize = <%=bean.isAutoResize()%>;
var actionsURL = <%=PageFlowUtil.jsString(urlProvider(PipelineUrls.class).urlActions(context.getContainer()).getLocalURIString() + "path=")%>;
var buttonActions = [];

<%
    for (FilesWebPart.FilesForm.actions action  : bean.getButtonConfig())
    {
%>
        buttonActions.push('<%=action.name()%>');
<%
    }
%>
function renderBrowser(rootPath, renderTo, isFolderTreeCollapsed, isPipelineRoot)
{
    var fileSystem = new LABKEY.WebdavFileSystem({
        extraPropNames: ['description', 'actions'],

        // extra props should model Ext.data.Field types
        extraDataFields: [
            {name: 'description', mapping: 'propstat/prop/description'},
            {name: 'actionHref', mapping: 'propstat/prop/actions', convert : function (v, rec)
                {
                    var result = [];
                    var actionsElements = Ext.DomQuery.compile('propstat/prop/actions').call(this, rec);
                    if (actionsElements.length > 0)
                    {
                        var actionElements = actionsElements[0].getElementsByTagName('action');
                        for (var i = 0; i < actionElements.length; i++)
                        {
                            var action = new Object();
                            var childNodes = actionElements[i].childNodes;
                            for (var n = 0; n < childNodes.length; n++)
                            {
                                var childNode = childNodes[n];
                                if (childNode.nodeName == 'message')
                                {
                                    action.message = childNode.textContent || childNode.text;
                                }
                                else if (childNode.nodeName == 'href')
                                {
                                    action.href = childNode.textContent || childNode.text;
                                }
                            }
                            result[result.length] = action;
                        }
                    }
                    return result;
                }}
        ],
        //extraPropNames: ["actions", "description"],
        baseUrl:rootPath,
        rootName:'fileset'
    });

    var prefix = undefined;

<%  if (bean.getStatePrefix() != null) { %>
    prefix = '<%=bean.getStatePrefix()%>';
<%  } %>

    var fileBrowser = new LABKEY.FilesWebPartPanel({
        fileSystem: fileSystem,
        helpEl:null,
        resizable: !Ext.isIE,
        showAddressBar: <%=bean.isShowAddressBar()%>,
        showFolderTree: <%=bean.isShowFolderTree()%>,
        folderTreeCollapsed: isFolderTreeCollapsed,
        expandFileUpload: <%=bean.isExpandFileUpload()%>,
        disableGeneralAdminSettings: <%=bean.isDisableGeneralAdminSettings()%>,
        showProperties: false,
        showDetails: <%=bean.isShowDetails()%>,
        allowChangeDirectory: true,
        tbarItems: buttonActions,
        isPipelineRoot: isPipelineRoot,
        adminUser : <%=getViewContext().getContainer().hasPermission(getViewContext().getUser(), AdminPermission.class)%>,
        statePrefix: prefix
    });

    //fileBrowser.height = 350;
    //fileBrowser.render(renderTo);

    var panel = new Ext.Panel({
        layout: 'fit',
        renderTo: renderTo,
        border: false,
/*
        layoutConfig: {
            columns: 1
        },
*/
        items: [fileBrowser],
        height: <%= height %>,
        boxMinHeight:<%= height %>,
        boxMinWidth: 650
    });

    var _resize = function(w,h)
    {
        if (!panel.rendered)
            return;
        var padding = [20,20];
        var xy = panel.el.getXY();
        var size = {
            width : Math.max(100,w-xy[0]-padding[0]),
            height : Math.max(100,h-xy[1]-padding[1])};
        panel.setSize(size);
        panel.doLayout();
    };

    if (autoResize)
    {
        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();
    }
    fileBrowser.start(<%=bean.getDirectory() != null ? q(bean.getDirectory().toString()) : ""%>);
}

<%  if (bean.isEnabled() && bean.isRootValid()) { %>
        Ext.onReady(function(){
            renderBrowser(<%=q(bean.getRootPath())%>, <%=q(bean.getContentId())%>, <%=bean.isFolderTreeCollapsed()%>, <%=bean.isPipelineRoot()%>);
        });
<%  } %>
</script>
