<%
/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    int webPartId = me.getModelBean().getRowId();
    JSONObject jsonProps = new JSONObject(me.getModelBean().getPropertyMap());
    String renderTarget = "project-" + me.getModelBean().getIndex();
    ViewContext ctx = me.getViewContext();
    boolean isAdmin = ctx.getUser().isAdministrator();
    boolean hasPermission;

    String containerPath = (String)jsonProps.get("containerPath");
    if(containerPath == null || "".equals(containerPath))
    {
        hasPermission = true; //this means current container
    }
    else
    {
        Container target = ContainerManager.getForPath(containerPath);
        hasPermission = target.hasPermission(ctx.getUser(), ReadPermission.class);
    }
%>
<div>
    <div id='<%=renderTarget%>'></div>
</div>
<script type="text/javascript">

    LABKEY.requiresExt4ClientAPI(true);
    LABKEY.requiresScript("extWidgets/IconPanel.js");

</script>
<script type="text/javascript">

Ext4.onReady(function(){
    //assume server-supplied webpart config
    var config = '<%=jsonProps%>';
    config = Ext4.decode(config);
    config.hideCreateButton = config.hideCreateButton === 'true';

    if(!<%=hasPermission%>){
        Ext.get('<%=renderTarget%>').update('You do not have permission to view this folder');
        return;
    }

    Ext4.applyIf(config, {
        containerTypes: 'project',
        containerFilter: 'CurrentAndSiblings',
        containerPath: LABKEY.Security.getHomeContainer(),
        hideCreateButton: false,
        iconSize: 'large',
        labelPosition: 'bottom',
        noun: 'Project'
    });

    function getFilterArray(panel){
        var filterArray = [];
        if(panel.containerTypes)
            filterArray.push(LABKEY.Filter.create('containerType', panel.containerTypes, LABKEY.Filter.Types.EQUALS_ONE_OF));

        //exclude system-generated containers
        if(LABKEY.Security.getHomeContainer())
            filterArray.push(LABKEY.Filter.create('name', LABKEY.Security.getHomeContainer(), LABKEY.Filter.Types.NOT_EQUAL));
        if(LABKEY.Security.getSharedContainer())
            filterArray.push(LABKEY.Filter.create('name', LABKEY.Security.getSharedContainer(), LABKEY.Filter.Types.NOT_EQUAL));

        return filterArray;
    }

    var panelCfg = {
        id: 'projects-panel-<%=webPartId%>',
        iconField: 'iconurl',
        labelField: 'Name',
        urlField: 'url',
        iconSize: config.iconSize,
        labelPosition: config.labelPosition,
        hideCreateButton: config.hideCreateButton,
        noun: config.noun,
        showMenu: false,
        width: '100%',
        border: false,
        frame: false,
        buttonAlign: 'left',
        emptyText: 'No folder to display',
        deferEmptyText: false,
        store: Ext4.create('LABKEY.ext4.Store', {
            containerPath: config.containerPath || LABKEY.Security.getHomeContainer(),
            schemaName: 'core',
            queryName: 'Containers',
            sort: 'Name',
            containerFilter: config.containerFilter,
            columns: 'Name,EntityId,Path,ContainerType',
            autoLoad: true,
//            maxRows: 0,
            filterArray: getFilterArray(config),
            metadata: {
                iconurl: {
                    createIfDoesNotExist: true,
                    setValueOnLoad: true,
                    getInitialValue: function(val, rec){
                        return LABKEY.ActionURL.buildURL('project', 'downloadProjectIcon', rec.get('EntityId'))
                    }
                },
                url: {
                    createIfDoesNotExist: true,
                    setValueOnLoad: true,
                    getInitialValue: function(val, rec){
                        return LABKEY.ActionURL.buildURL('project', 'start', rec.get('Path'))
                    }
                }
            }
        })
    }

    //NOTE: separated to differentiate site admins from those w/ admin permission in this container
    if(<%=isAdmin%>){
        panelCfg.buttons = [{
            text: 'Create New ' + config.noun,
            hidden: !LABKEY.Security.currentUser.isAdmin || config.hideCreateButton,
            target: '_self',
            href: LABKEY.ActionURL.buildURL('admin', 'createFolder', '/')
        }]
    }

    var panel = Ext4.create('LABKEY.ext.IconPanel', panelCfg);
    panel.render('<%=renderTarget%>');
    Ext4.apply(panel, config);
    panel.getFilterArray = getFilterArray;
});

    /**
     * Called by Server to handle cusomization actions.
     */
    function customizeProjectWebpart(webpartId, pageId, index) {

        Ext4.onReady(function(){
            var panel = Ext4.getCmp('projects-panel-' + webpartId);

            if (panel) {
                function shouldCheck(btn){
                    var data = panel.down('#dataView').renderData;
                    return (btn.iconSize==data.iconSize && btn.labelPosition==data.labelPosition)
                }
                Ext4.create('Ext.window.Window', {
                    title: 'Customize Webpart',
                    width: 400,
                    layout: 'fit',
                    items: [{
                        xtype: 'form',
                        bodyStyle: 'padding: 5px;',
                        items: [{
                            xtype: 'textfield',
                            name: 'title',
                            fieldLabel: 'Title',
                            itemId: 'title',
                            value: panel.title || 'Projects'
                        },{
                            xtype: 'radiogroup',
                            name: 'style',
                            itemId: 'style',
                            fieldLabel: 'Icon Style',
                            border: false,
                            columns: 1,
                            defaults: {
                                xtype: 'radio',
                                width: 300
                            },
                            items: [{
                                boxLabel: 'Details',
                                inputValue: {iconSize: 'small',labelPosition: 'side'},
                                checked: shouldCheck({iconSize: 'small',labelPosition: 'side'}),
                                name: 'style'
                            },{
                                boxLabel: 'Medium',
                                inputValue: {iconSize: 'medium',labelPosition: 'bottom'},
                                checked: shouldCheck({iconSize: 'medium',labelPosition: 'bottom'}),
                                name: 'style'
                            },{
                                boxLabel: 'Large',
                                inputValue: {iconSize: 'large',labelPosition: 'bottom'},
                                checked: shouldCheck({iconSize: 'large',labelPosition: 'bottom'}),
                                name: 'style'
                            }]
                        },{
                            xtype: 'radiogroup',
                            name: 'folderTypes',
                            itemId: 'folderTypes',
                            fieldLabel: 'Folders To Display',
                            border: false,
                            columns: 1,
                            defaults: {
                                xtype: 'radio',
                                width: 300
                            },
                            items: [{
                                boxLabel: 'All Projects',
                                inputValue: 'project',
                                checked: panel.containerTypes && panel.containerTypes.match(/project/),
                                name: 'folderTypes'
                            },{
                                boxLabel: 'Specific Folder',
                                inputValue: 'folder',
                                checked: panel.containerTypes && !panel.containerTypes.match(/project/),
                                name: 'folderTypes'
                            },{
                                xtype: 'labkey-combo',
                                itemId: 'containerPath',
                                width: 200,
                                disabled: panel.containerTypes && panel.containerTypes.match(/project/),
                                displayField: 'Path',
                                valueField: 'EntityId',
                                initialValue: panel.store.containerPath,
                                value: panel.store.containerPath,
                                store: Ext4.create('LABKEY.ext4.Store', {
                                    //containerPath: '/home',
                                    schemaName: 'core',
                                    queryName: 'Containers',
                                    containerFilter: 'AllFolders',
                                    columns: 'Name,Path,EntityId',
                                    autoLoad: true,
                                    sort: 'Name',
                                    filterArray: [
                                        LABKEY.Filter.create('workbook', false, LABKEY.Filter.Types.EQUAL),
                                        LABKEY.Filter.create('name', LABKEY.Security.getHomeContainer(), LABKEY.Filter.Types.NOT_EQUAL),
                                        LABKEY.Filter.create('name', LABKEY.Security.getSharedContainer(), LABKEY.Filter.Types.NOT_EQUAL)
                                    ]
                                })
                            },{
                                xtype: 'checkbox',
                                boxLabel: 'Include Workbooks',
                                disabled: panel.containerTypes && panel.containerTypes.match(/project/),
                                checked: (panel.containerTypes.match(/project/) || panel.containerTypes.match(/workbook/)),
                                itemId: 'includeWorkbooks'
                            },{
                                xtype: 'checkbox',
                                boxLabel: 'Hide Create Button',
                                checked: panel.hideCreateButton,
                                itemId: 'hideCreateButton'
                            }],
                            listeners: {
                                buffer: 20,
                                change: function(field, val){
                                    var window = field.up('form');
                                    window.down('#containerPath').setDisabled(val.folderTypes != 'folder');
                                    window.down('#includeWorkbooks').setDisabled(val.folderTypes != 'folder');
                                    window.doLayout();
                                    field.up('window').doLayout();

                                }
                            }
                        }]
                    }],
                    buttons: [{
                        text: 'Submit',
                        handler: function(btn){
                            var mode = btn.up('window').down('#folderTypes').getValue().folderTypes;

                            if(mode == 'project'){
                                panel.store.containerFilter = 'CurrentAndSiblings';
                                panel.containerTypes = 'project';
                                panel.store.containerPath = LABKEY.Security.getHomeContainer();
                                panel.store.filterArray = panel.getFilterArray(panel);
                            }
                            else {
                                var container = btn.up('window').down('#containerPath').getValue();
                                if(!container){
                                    alert('Must choose a folder');
                                    return;
                                }
                                panel.store.containerPath = container;

                                panel.store.containerFilter = 'Current'; //null;  //use default
                                panel.containerTypes = ['folder'];
                                if(btn.up('window').down('#includeWorkbooks').getValue())
                                    panel.containerTypes.push('workbook');
                                panel.containerTypes = panel.containerTypes.join(';');

                                panel.store.containerFilter = 'CurrentAndSubfolders';
                                panel.store.filterArray = panel.getFilterArray(panel);
                                panel.store.filterArray.push(LABKEY.Filter.create('EntityId', panel.store.containerPath, LABKEY.Filter.Types.NOT_EQUAL));
                            }

                            panel.store.load();

                            var hideCreateButton = btn.up('window').down('#hideCreateButton').getValue();
                            panel.hideCreateButton = hideCreateButton;

                            panel.getDockedItems()[0].down('button').setVisible(!hideCreateButton);
                            //panel.getDockedItems()[0].setVisible(!hideCreateButton);

                            var styleField = btn.up('window').down('#style').getValue().style;
                            panel.resizeIcons.call(panel, styleField);
                            btn.up('window').hide();

                            var title = btn.up('window').down('#title').getValue();
                            panel.title = title;
                            LABKEY.Utils.setWebpartTitle(title, webpartId);

                            var values = {
                                containerPath: panel.store.containerPath,
                                title: title,
                                containerTypes: panel.containerTypes,
                                containerFilter: panel.store.containerFilter,
                                webPartId: webpartId,
                                hideCreateButton: panel.hideCreateButton,
                                iconSize: panel.iconSize,
                                labelPosition: panel.labelPosition
                            };

                            Ext4.Ajax.request({
                                url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, values),
                                method : 'POST',
                                failure : LABKEY.Utils.onError,
                                scope : this
                            });
                        },
                        scope: this
                    },{
                        text: 'Cancel',
                        handler: function(btn){
                            btn.up('window').hide();
                        },
                        scope: this
                    }]
                }).show();
            }
        });
    }


</script>