<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.admin.AdminController.ManageFoldersForm" %>
<%
    HttpView<ManageFoldersForm> me = (HttpView<ManageFoldersForm>) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
%>
<div id="someUniqueElement"></div>
<script type="text/javascript">
    LABKEY.requiresExt4Sandbox();
    LABKEY.requiresCss('study/DataViewsPanel.css');
    LABKEY.requiresScript("admin/FolderManagementPanel.js");
</script>
<script type="text/javascript">

    Ext4.onReady(function() {

        var folderPanel = Ext4.create('LABKEY.ext.panel.FolderManagementPanel', {
            renderTo : 'someUniqueElement',
            height   : 700,
            selected : <%= c.getRowId() %>,
            requiredPermission : <%=PageFlowUtil.jsString(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>
        });

        var _resize = function(w, h) {
            if (!folderPanel.rendered)
                return;

            LABKEY.Utils.resizeToViewport(folderPanel, w, h);
        };

        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();

    });
</script>