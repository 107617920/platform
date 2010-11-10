<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerFilter" %>
<%@ page import="org.labkey.api.query.CustomView" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.ChooseColumnsForm" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors />
<%
    ChooseColumnsForm form = (ChooseColumnsForm) HttpView.currentModel();
    CustomView view = form.getCustomView();
    ActionURL urlTableInfo = form.getSchema().urlFor(QueryAction.tableInfo);
    urlTableInfo.addParameter(QueryParam.queryName.toString(), form.getQueryName());

    boolean canEdit = form.canEdit();
%>
<script type="text/javascript">
    LABKEY.requiresScript("designer/designer.js", true);
    LABKEY.requiresScript("query/columnPicker.js", true);
    LABKEY.requiresScript("query/queryDesigner.js", true);
</script>
<% if (form.ff_designXML == null) {
    return;
}%>
<script type="text/javascript">
    var designer = null;
    function init()
    {
        designer = new ViewDesigner(new TableInfoService(<%=q(urlTableInfo.toString())%>));
        <% if (form.getDefaultTab() != null)
        { %>
            designer.defaultTab = <%=PageFlowUtil.jsString(form.getDefaultTab())%>;
        <% } %>
        designer.setShowHiddenFields(<%= form.getQuerySettings().isShowHiddenFieldsWhenCustomizing() %>);
        designer.init();
    }

    Ext.onReady(function () {
        LABKEY.Utils.onTrue({
            testCallback: function () { return window.ViewDesigner != undefined; },
            successCallback: init,
            errorCallback: function (e, args) { console.error("Error loading designer: " + e); }
        });
    });

    function updateViewNameDescription(elCheckbox)
    {
        var elShared = document.getElementById("sharedViewNameDescription");
        var elPersonal = document.getElementById("personalViewNameDescription");
        if (elCheckbox.checked)
        {
            elShared.style.display = "";
            elPersonal.style.display = "none";
        }
        else
        {
            elShared.style.display = "none";
            elPersonal.style.display = "";
        }
    }

    function designerSaveSuccessful(json)
    {
        if (json.redirect)
        {
            designer.uninit();
            window.location = json.redirect;
        }
    }

    function onSave()
    {
        if (designer.validate())
        {
            var msgbox = null;
            var timeout = function () { msgbox = Ext.MessageBox.progress("Saving...", "Saving custom view..."); }.defer(200);
            Ext.Ajax.request({
                url: '<%=form.urlFor(QueryAction.saveColumns)%>',
                method: "POST",
                form: "saveColumns",
                success: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                    if (msgbox) msgbox.hide();
                    window.clearTimeout(timeout);
                    designerSaveSuccessful(json);
                }),
                failure: LABKEY.Utils.getCallbackWrapper(function (json, response, options) {
                    if (msgbox) msgbox.hide();
                    window.clearTimeout(timeout);
                    Ext.Msg.alert("Error saving", json.exception);
                }, null, true)
            });
        }
    }
</script>
<% if (form.isSaveInSession()) { %>
    <p><b>
    <% if (getViewContext().getUser().isGuest()) out.print("You are not currently logged in."); %>
    Changes you make here will only persist for the duration of your session.
    </b></p>
<% } %>
<table class="labkey-customize-view">
    <tr>
        <th>
            <table style="width:100%">
                <tr>
                    <td class="labkey-tab-space">
                        <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
                    </td>
                    <td class="labkey-tab-selected" style="cursor:pointer">
                        Available&nbsp;Fields
                    </td>
                    <td class="labkey-tab-space" style="text-align:right;width:100%">
                        <labkey:helpPopup title="Available Fields">
                            <p>Click on the available fields to select them.  Click the 'Add' button to add selected fields to the grid view.</p>
                            <p>Expand elements of the tree to add related fields from other tables.</p>
                        </labkey:helpPopup>
                    </td>
                </tr>
            </table>
        </th>
        <th></th>
        <th colspan="2" align="left">
            <table style="width:100%">
                <tr>
                    <td class="labkey-tab-space">
                        <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
                    </td>
                    <td style="cursor:pointer" class="labkey-tab" id="columns.tab"
                        onclick="designer.setActiveTab(designer.tabs.columns)">
                        Fields&nbsp;In&nbsp;Grid
                    </td>
                    <td class="labkey-tab-space" style="padding-left:0px;padding-right:0px;">
                        <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
                    </td>
                    <td style="cursor:pointer" id="filter.tab" class="labkey-tab"
                        onclick="designer.setActiveTab(designer.tabs.filter)">
                        Filter
                    </td>
                    <td class="labkey-tab-space" style="padding-left:0px;padding-right:0px;">
                        <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
                    </td>
                    <td style="cursor:pointer" id="sort.tab" class="labkey-tab"
                        onclick="designer.setActiveTab(designer.tabs.sort)">
                        Sort
                    </td>
                    <td class="labkey-tab-space" style="text-align:right;width:100%">
                        <labkey:helpPopup title="Fields In Grid / Filter / Sort">
                            <p>There are three tabs for choosing which fields are to be displayed in the grid, and setting the filter and sort.</p>
                            <p>Add fields from the Available Fields</p>
                            <p>Use the arrows to move elements up and down, or remove them.</p>
                        </labkey:helpPopup>
                    </td>
                </tr>
            </table>
        </th>
    </tr>
    <tr><td class="labkey-tab-strip-spacer"></td><td style="background-color: transparent;"></td><td colspan=2 class="labkey-tab-strip-spacer"></td></tr>
    <tr>
        <td onSelectStart="return false;" onMouseDown="return false;" class="labkey-tab" style="border-top:none;vertical-align:top;">
            <div style="height:400px;width:300px;overflow:auto;position:relative" id="columnPicker">
            </div>
        </td>
        <td style="background-color: transparent;">
            <div style="margin: 140px 0px 0px 5px;">
                <%=generateButton("Add >>", "#", "designer.add();return false;")%>
            </div>
        </td>
        <td id="columns.list" style="display:none;border-top:none;border-right:none;vertical-align:top;" class="labkey-tab">
            <div id="columns.list.div" style="height:400px;width:500px;overflow:auto;"></div>
        </td>
    <td valign="top" id="columns.controls" style="display:none;border-top:none;border-left:none;vertical-align:top;" class="labkey-tab">
        <br>

        <p><a href="#" onclick="designer.moveUp();return false"><img src="<%=request.getContextPath()%>/query/moveup.gif"
                                                       alt="Move Up" title="Move Up"></a></p>

        <p><a href="#" onclick="designer.moveDown();return false"><img src="<%=request.getContextPath()%>/query/movedown.gif"
                                                         alt="Move Down" title="Move Down"></a></p>

        <p><a href="#" onclick="designer.remove();return false"><img src="<%=request.getContextPath()%>/query/delete.gif" alt="Delete"
                                                       title="Delete"></a></p>
        <p><a href="#" onclick="designer.showColumnProperties();return false;"><img src="<%=request.getContextPath()%>/query/columnProperties.gif" alt="Set Field Caption" title="Set Field Caption"></a></p>
    </td>
    <td id="filter.list" style="display:none;border-top:none;border-right:none;vertical-align:top;" class="labkey-tab">
        <div id="filter.list.div" style="height:400px;width:600px;overflow:auto;"></div>
    </td>
    <td id="filter.controls" valign="top" style="display:none;border-top:none;border-left:none;vertical-align:top;" class="labkey-tab">
        <br>

        <p><a href="#" onclick="designer.tabs.filter.moveUp();return false"><img src="<%=request.getContextPath()%>/query/moveup.gif"
                                                                   alt="Move Up" title="Move Up"></a></p>

        <p><a href="#" onclick="designer.tabs.filter.moveDown();return false"><img
                src="<%=request.getContextPath()%>/query/movedown.gif"
                alt="Move Down" title="Move Down"></a></p>

        <p><a href="#" onclick="designer.tabs.filter.remove();return false"><img src="<%=request.getContextPath()%>/query/delete.gif"
                                                                   alt="Delete" title="Delete"></a></p>
    </td>
    <td id="sort.list" style="display:none;border-top:none;border-right:none;vertical-align:top;" class="labkey-tab">
        <div id="sort.list.div" style="height:400px;width:500px;overflow:auto;"></div>
    </td>
    <td id="sort.controls" valign="top" style="display:none;border-top:none;border-left:none;vertical-align:top;" class="labkey-tab">
        <br>

        <p><a href="#" onclick="designer.tabs.sort.moveUp();return false"><img src="<%=request.getContextPath()%>/query/moveup.gif"
                                                                 alt="Move Up" title="Move Up"></a></p>

        <p><a href="#" onclick="designer.tabs.sort.moveDown();return false"><img
                src="<%=request.getContextPath()%>/query/movedown.gif"
                alt="Move Down"></a></p>

        <p><a href="#" onclick="designer.tabs.sort.remove();return false"><img src="<%=request.getContextPath()%>/query/delete.gif"
                                                                 alt="Delete" title="Delete"></a></p>
    </td>
</table>
<form id="saveColumns" <%--method="POST" action="<%=form.urlFor(QueryAction.chooseColumns)%>" onsubmit="return onSubmit();"> --%>
    <span title="Some fields may be hidden by default from the list of available fields by default.">
        <input id="showHiddenFields" type="checkbox"<% if (form.getQuerySettings().isShowHiddenFieldsWhenCustomizing()) { %> checked <% } %> onclick="designer.setShowHiddenFields(this.checked)" onchange="designer.setShowHiddenFields(this.checked)">
        <label for="showHiddenFields">Show hidden fields</label>
    </span><br>
    <input type="hidden" name="ff_designXML" id="ff_designXML" value="<%=h(form.ff_designXML)%>">
    <input type="hidden" name="ff_dirty" id="ff_dirty" value="<%=form.ff_dirty%>">
    <input type="hidden" name="saveInSession" id="saveInSession" value="<%=form.isSaveInSession()%>">
    <input type="hidden" name="lastModified" id="lastModified" value="<%=view != null ? view.getModified() : false%>">
    <p>
    <% boolean isHidden = view != null && view.isHidden(); %>
    <% if (isHidden) { %>
        <input type="hidden" name="ff_columnListName" value="<%=h(view.getName())%>">
        <% if (view.isShared()) { %>
            <input type="hidden" name="ff_saveForAllUsers" value="true">
        <% } %>
    <% } else { %>
        <b>View Name:</b> <input type="text" name="ff_columnListName" maxlength="50" value="<%= canEdit ? h(form.ff_columnListName) : (form.ff_columnListName == null ? "CustomizedView" : form.ff_columnListName + "Copy") %>">
        <span id="personalViewNameDescription" <%=!form.canSaveForAllUsers() || view == null || !view.isShared() || form.isSaveInSession() ? "" : " style=\"display:none"%>>(Leave blank to save as your default grid view for '<%=h(form.getQueryName())%>')</span>
        <span id="sharedViewNameDescription" <%=!form.canSaveForAllUsers() || view == null || !view.isShared() || form.isSaveInSession() ? " style=\"display:none\"" : ""%>>(Leave blank to save as the default grid view for '<%=h(form.getQueryName())%>' for all users)</span>
        <% if (!canEdit) { %><br/>You must save this view with an alternate name.<% } %>
        <br>
        <% if (form.canSaveForAllUsers() && !form.isSaveInSession()) { %>
            <input type="checkbox" id="ff_saveForAllUsers" name="ff_saveForAllUsers" value="true"<%=view != null && view.isShared() ? " checked" : ""%> onclick="updateViewNameDescription(this)">
            <label for="ff_saveForAllUsers">Make this grid view available to all users</label><br>
            <labkey:checkbox id="ff_inheritable" name="ff_inheritable" value="<%=true%>" checkedSet="<%=Collections.singleton(form.ff_inheritable)%>"/>
            <label for="ff_inheritable">Make this grid view available in child folders</label><br>
        <% } %>
    <% } %>

    <% if (form.hasFilterOrSort()) { %>
        <input id="ff_saveFilterCbx" type="checkbox" name="ff_saveFilter" value="true">
        <label for="ff_saveFilterCbx">Remember grid filters and sorts:</label>
        <ul>
    <%
        List<String> filterColumns = form.getFilterColumnNamesFromURL();
        if (filterColumns.size() > 0)
        {
            out.write("<li>Filter: ");
            boolean needComma = false;
            for (String filterColumn : filterColumns)
            {
                if (needComma)
                    out.write(", ");
                else
                    needComma = true;
                out.write(filterColumn);
            }
            out.write("</li>\n");
        }

        List<String> sortColumns = form.getSortColumnNamesFromURL();
        if (sortColumns.size() > 0)
        {
            out.write("<li>Sort: ");
            boolean needComma = false;
            for (String sortColumn : sortColumns)
            {
                if (needComma)
                    out.write(", ");
                else
                    needComma = true;
                out.write(sortColumn);
            }
            out.write("</li>\n");
        }

        if (form.getContainerFilterName() != null)
        {
            out.write("<li>Folder Filter: ");
            String containerFilter = ContainerFilter.Type.valueOf(form.getContainerFilterName()).toString();
            out.write(containerFilter);
            out.write("</li>");
        }

        %></ul><%

        } else { %>
        <input type="hidden" name="ff_saveFilter" value="true">
    <% } %>
    </p>
    <labkey:button text="<%=form.isSaveInSession() ? \"Save Temporary View\" : \"Save\"%>" href="javascript:void(0);" onclick="designer.needToPrompt = false; return onSave();" />
    <br>
</form>
