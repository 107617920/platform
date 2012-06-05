/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.GroupSelector', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){
        Ext4.apply(config, {
            title: 'Groups',
            border: false,
            cls: 'report-filter-panel',
            autoScroll: true,
            maxInitSelection: 5
        });

        Ext4.define('ParticipantCategory', {
            extend: 'Ext.data.Model',
            fields : [
                {name : 'id'},
                {name : 'categoryId'},
                {name : 'label'},
                {name : 'description'},
                {name : 'participantIds'},
                {name : 'type'}
            ]
        });

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete'
        );

        // fix the issue with the group list hidden for saved chart with a measure dimension panel
        this.on('expand', function(){
            if (!this.hidden)
                this.show();
        });
    },

    initComponent : function(){

        // add a text link to the manage participant groups page
        this.manageGroupsLink = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            width: 175,
            html: LABKEY.Utils.textLink({href: LABKEY.ActionURL.buildURL("study", "manageParticipantCategories"), text: 'Manage Groups'})
        });

        // add a hiden display field to show what is selected by default
        this.defaultDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            width: 210,
            html: '<span style="font-size:75%;color:red;">Selecting 5 values by default</span>'
        });

        // add a hidden display field for warning the user if a saved chart has a group that is no longer available
        this.groupsRemovedDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            padding: 3,
            width: 210,
            html: '<span style="font-size:90%;font-style:italic;">One or more of the participant groups originally saved with this chart are not currently visible. ' +
                    'The group(s) may have been deleted or you may not have permission to view them.</span><br> <br>'
        });

        this.selection = [];
        if (this.subject && this.subject.groups)
        {
            Ext4.each(this.subject.groups, function(group){
                this.selection.push({type:'participantGroup', label:group.label}); 
            }, this);
        }

        this.fireChangeTask = new Ext4.util.DelayedTask(function(){
            this.fireEvent('chartDefinitionChanged', true);            
        }, this);

        this.groupFilterList = Ext4.create('LABKEY.ext4.filter.SelectList', {
            itemId   : 'filterPanel',
            flex     : 1,
            allowAll : true,
            store : Ext4.create('Ext.data.Store', {
                model    : 'ParticipantCategory',
                autoLoad : true,
                proxy    : {
                    type   : 'ajax',
                    url    : LABKEY.ActionURL.buildURL('participant-group', 'browseParticipantGroups.api'),
                    extraParams : { type : 'participantGroup', includeParticipantIds: true },
                    reader : {
                        type : 'json',
                        root : 'groups'
                    }
                }
            }),
            maxInitSelection: this.maxInitSelection,
            selection : this.selection,
            //description : '<b class="filter-description">Groups</b>',
            listeners : {
                selectionchange : function(){
                    this.fireChangeTask.delay(1000);
                },
                beforerender : function(){
                    this.fireEvent('measureMetadataRequestPending');
                },
                initSelectionComplete : function(numSelected){
                    // if there were saved groups that are no longer availabe, display a message
                    if (this.selection.length > 0 && this.selection.length != numSelected)
                        this.groupsRemovedDisplayField.setVisible(true);

                    // if this is a new time chart, show the text indicating that we are selecting the first 5 by default
                    if (!this.subject.groups && numSelected == this.maxInitSelection)
                    {
                        this.hideDefaultDisplayField = new Ext4.util.DelayedTask(function(){
                            this.defaultDisplayField.hide();
                        }, this);

                        // show the display for 5 seconds before hiding it again
                        this.defaultDisplayField.show();
                        this.hideDefaultDisplayField.delay(5000);
                    }
                    
                    this.fireEvent('measureMetadataRequestComplete');
                },
                scope : this
            }
        });

        this.items = [
            this.groupsRemovedDisplayField,
            this.manageGroupsLink,
            this.defaultDisplayField,
            this.groupFilterList
        ];

        this.callParent();
    },

    getUniqueGroupSubjectValues: function(groups){
        var values = [];
        for (var i = 0; i < groups.length; i++)
        {
            values = Ext4.Array.unique(values.concat(groups[i].participantIds));
        }
        return values.sort();
    },

    getSubject: function(){
        var groups = [];
        var selected = this.groupFilterList.getSelection(false);
        for (var i = 0; i < selected.length; i++)
        {
            if (selected[i].get('type') == 'participantGroup')
            {
                groups.push({
                    id : selected[i].get("id"),
                    categoryId : selected[i].get("categoryId"),
                    label: selected[i].get("label"),
                    participantIds: selected[i].get("participantIds") 
                });
            }
        }

        // sort the selected groups array to match the selection list order
        function compareGroups(a, b) {
            if (a.id < b.id) {return -1}
            if (a.id > b.id) {return 1}
            return 0;
        }
        groups.sort(compareGroups);

        return {groups: groups, values: this.getUniqueGroupSubjectValues(groups)};
    }
});
