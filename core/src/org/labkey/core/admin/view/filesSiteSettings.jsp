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
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.FilesSiteSettingsAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("TreeGrid.js");
    Ext.QuickTips.init();
</script>

<%
    FilesSiteSettingsAction.FileSettingsForm bean = ((JspView<FilesSiteSettingsAction.FileSettingsForm>)HttpView.currentView()).getModelBean();
%>

<labkey:errors/>
<form action="filesSiteSettings.view" method="post">
    <input type="hidden" name="upgrade" value="<%=bean.isUpgrade()%>">
    <table width="80%">
        <tr><td colspan="2" class="labkey-announcement-title"><span>Site-Level File Root</span></td></tr>
        <tr><td colspan="2" class="labkey-title-area-line"></td></tr>

        <% if (bean.isUpgrade()) { %>
        <tr><td colspan="2">Set the site-level root for this server installation, or use the default provided. If the
            root does not exist, LabKey server will create it for you.<br/><br/>
            When a site-level file root is set, each folder for every project has a corresponding subdirectory in the file system.
            LabKey Server allows you to upload and process your data files, including flow, proteomics and
            study-related files. By default, LabKey stores your files in a standard directory structure. You can
            override this location for each project if you wish.
            </td></tr>
        <% } else { %>
        <tr><td colspan="2">When a site-level file root is set, each folder for every project has a corresponding subdirectory in the file system.
            A default root is provided or one can be specified.</td></tr>
        <% } %>
        <tr><td>&nbsp;</td></tr>
        
        <tr><td class="labkey-form-label">Site-Level&nbsp;File&nbsp;Root&nbsp;<%=helpPopup("File Root", "Set a site-level file root. " +
                "When a site-level file root is set, each folder for every project has a corresponding subdirectory in the file system." +
                " A site-level file root may be overridden at the project level from 'Project Settings'.")%></td>
            <td><input type="text" name="rootPath" size="64" value="<%=h(bean.getRootPath())%>"></td>
        </tr>

        <tr><td>&nbsp;</td></tr>
        <tr>
        <%
            if (bean.isUpgrade()) {
        %>
            <td><%=generateSubmitButton("Next")%></td>
        <%
            } else {
        %>
            <td><%=generateSubmitButton("Save")%>&nbsp;
                <%=generateButton("Cancel", urlProvider(AdminUrls.class).getAdminConsoleURL())%>
            </td>
        <%
            }
        %>
        </tr>
    </table>
</form>

<script type="text/javascript">
    var isUpgrade = <%=bean.isUpgrade()%>;

    function configSelected(node)
    {
        if (node != undefined && node.attributes.configureURL != undefined)
        {
            window.location = node.attributes.configureURL;
        }
    }

    function browseSelected(node)
    {
        if (node != undefined && node.attributes.browseURL != undefined)
        {
            window.location = node.attributes.browseURL;
        }
    }

    Ext.onReady(function(){

        // show the file root browser
        if (!isUpgrade)
        {
            var tree = new Ext.ux.tree.TreeGrid({
                //width: 800,
                //height: 500,
                rootVisible:false,
                autoScroll:true,
                //renderTo: 'viewsGrid',

                columns:[{
                    header:'Project',
                    width:330,
                    dataIndex:'name'
                },{
                    header:'Directory',
                    width:420,
                    dataIndex:'path'
                },{
                    header:'Default',
                    width:75,
                    dataIndex:'default'
                }],
                tbar: [
                    {text:'Expand All', tooltip: {text:'Expands all containers', title:'Expand All'}, listeners:{click:function(button, event) {tree.expandAll();}}},
                    {text:'Collapse All', tooltip: {text:'Collapses all containers', title:'Collapse All'}, listeners:{click:function(button, event) {tree.collapseAll();}, scope:this}},
                    {text:'Configure Selected', tooltip: {text:'Configure settings for the selected root', title:'Configure Selected'}, listeners:{click:function(button, event) {configSelected(tree.getSelectionModel().getSelectedNode());}, scope:this}},
                    {text:'Browse Selected', tooltip: {text:'Browse files from the selected root', title:'Browse Selected'}, listeners:{click:function(button, event) {browseSelected(tree.getSelectionModel().getSelectedNode());}, scope:this}},
                ],
                dataUrl: LABKEY.ActionURL.buildURL("filecontent", "fileContentSummary", this.container),
                listeners: {dblclick: function(node){browseSelected(node);}}
            });

            var panel = new Ext.Panel({
                layout: 'fit',
                renderTo: 'viewsGrid',
                items: [tree],
                height: 500
            });
        }
    });
</script>

<% if (!bean.isUpgrade()) { %>
    <table width="80%">
        <tr><td colspan="2" class="labkey-announcement-title"><span>Summary View for File Directories</span></td></tr>
        <tr><td colspan="2" class="labkey-title-area-line"></td></tr>
        <tr><td>File directories, named file sets and pipeline directories can be viewed on a project/folder basis. The 'Default' column
            indicates whether the directory is derived from the site-level file root (set above) or has been overriden. To view or
            manage files in a directory, double click on a row or click on the 'Browse Selected' button. To configure an
            @file or an @pipeline directory, select the directory and click on the 'Configure Selected' button in the toolbar.
        </td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td><div id="viewsGrid" class="extContainer"></div></td></tr>
    </table>
<% } %>


