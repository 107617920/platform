<%
    /*
    * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
    JspView<StudyController.ManageLocationTypesForm> me = (JspView<StudyController.ManageLocationTypesForm>) HttpView.currentView();
    StudyController.ManageLocationTypesForm bean = me.getModelBean();
%>
<div style="max-width: 1000px">
    <p>For each location type you can allow or disallow location of that type to be requesting locations.
    </p>
</div>
<div id="locationTypesPanel"></div>

<script type="text/javascript">

    (function(){

        var init = function()
        {
            Ext4.QuickTips.init();

            var checkBoxRepository = Ext4.create('Ext.form.field.Checkbox', {
                fieldLabel: 'Repository',
                labelSeparator: '',
                value: true,
                width : 220,
                labelWidth: 130
            });
            var checkBoxClinic = Ext4.create('Ext.form.field.Checkbox', {
                fieldLabel: 'Clinic',
                labelSeparator: '',
                value: true,
                width : 220,
                labelWidth: 130
            });
            var checkBoxSal = Ext4.create('Ext.form.field.Checkbox', {
                fieldLabel: 'Site Affiliated Lab',
                labelSeparator: '',
                value: true,
                width : 220,
                labelWidth: 130
            });
            var checkBoxEndPoint = Ext4.create('Ext.form.field.Checkbox', {
                fieldLabel: 'Endpoint Lab',
                labelSeparator: '',
                value: true,
                width : 220,
                labelWidth: 130
            });

            var controls = [checkBoxRepository, checkBoxClinic, checkBoxSal, checkBoxEndPoint];
            // TODO: above controls are not currently used; could use as alternative to checkBoxGroup below

            var checkBoxGroup = Ext4.create('Ext.form.CheckboxGroup', {
                columns: 1,
                vertical: true,
                fieldLabel: 'Allow Type',
                labelSeparator: '',
                labelAlign: 'top',

                items: [
                    {boxLabelAlign: 'after', boxLabel: 'Repository',          checked: <%=bean.isRepository()%>},
                    {boxLabelAlign: 'after', boxLabel: 'Clinic',              checked: <%=bean.isClinic()%>},
                    {boxLabelAlign: 'after', boxLabel: 'Site Affiliated Lab', checked: <%=bean.isSal()%>},
                    {boxLabelAlign: 'after', boxLabel: 'Endpoint Lab',        checked: <%=bean.isEndpoint()%>}
                ]
            });

            var form = Ext4.create('Ext.form.FormPanel', {
                title: 'Location Types',
                renderTo: 'locationTypesPanel',
                bodyPadding: 10,
                bodyStyle: 'background: none',
                frame: false,
                border: false,
                width: 200,
                buttonAlign : 'left',
                items: [checkBoxGroup],
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui : 'footer',
                    style : 'background: none',
                    height : 30,
                    items: [{
                        xtype: 'button',
                        text: 'Save',
                        handler: function() {
                            saveLocationTypeInfo(checkBoxGroup.items);
                        }
                    },{
                        xtype: 'button',
                        text: 'Cancel',
                        handler: function() {window.location = LABKEY.ActionURL.buildURL('study', 'manageStudy.view', null, null);}
                    }]
                }]
            });

            var displayDoneChangingMessage = function() {
                Ext4.MessageBox.show({
                    title: "Change All Alternate IDs",
                    msg: "Changing Alternate IDs is complete.",
                    buttons: Ext4.MessageBox.OK,
                    icon: Ext4.MessageBox.INFO
                });
            };

            var saveLocationTypeInfo = function(checkBoxItems) {
                var data = {repository : checkBoxItems.items[0].getValue(),
                            clinic :     checkBoxItems.items[1].getValue(),
                            sal :        checkBoxItems.items[2].getValue(),
                            endpoint :   checkBoxItems.items[3].getValue()};
                Ext4.Ajax.request({
                    url : (LABKEY.ActionURL.buildURL('study', 'saveLocationsTypeSettings')),
                    method : 'POST',
                    success: function(){
                        window.location = LABKEY.ActionURL.buildURL("study", 'manageStudy.view', null, null);
                    },
                    failure: function(response, options){
                        LABKEY.Utils.displayAjaxErrorResponse(response, options, false, 'An error occurred:<br>');
                    },
                    jsonData : data,
                    headers : {'Content-Type' : 'application/json'},
                    scope: this
                });

            }
        };

        Ext4.onReady(init);

    })();

</script>
