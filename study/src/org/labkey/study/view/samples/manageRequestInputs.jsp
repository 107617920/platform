<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.SampleManager"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.ManageRequestInputsBean> me = (JspView<SpringSpecimenController.ManageRequestInputsBean>) HttpView.currentView();
    SpringSpecimenController.ManageRequestInputsBean bean = me.getModelBean();
    String contextPath = bean.getContextPath();

    String tdButtons = "<a href=\"#\" onClick=\"return moveRow(this, true);\">" +
            "<img src=\"" + contextPath + "/_images/uparrow.gif\" alt=\"[Move Up]\"></a>\n" +
            "<a href=\"#\" onClick=\"return moveRow(this, false);\">" +
            "<img src=\"" + contextPath + "/_images/downarrow.gif\" alt=\"[Move Down]\"></a>\n" +
            "<a href=\"#\" onClick=\"return deleteRow(this);\">" +
            "<img src=\"" + contextPath + "/_images/delete.gif\" alt=\"[Delete]\"></a>\n";

    String tdTitle = "<input type=\"text\" name=\"title\" size=\"20\">";
    String tdHelpText = "<input type=\"text\" name=\"helpText\" size=\"50\">";
    String tdMultiline = "<input type=\"checkbox\" name=\"multiline\">";
    String tdRequired = "<input type=\"checkbox\" name=\"required\">";
    String tdRememberSiteValue = "<input type=\"checkbox\" name=\"rememberSiteValue\">";
%>
<script type="text/javascript">
    function moveRow(elem, up)
    {
        var table = document.getElementById("inputTable");
        var row = elem;
        while (row.tagName != 'TR')
            row = row.parentNode;
        // find our row, starting with 1 (0 is the header row):
        var rowIndex = 1;
        while (rowIndex < table.rows.length && table.rows[rowIndex] != row)
            rowIndex++;
        var otherIndex = (up == true ? rowIndex - 1 : rowIndex + 1);
        if (otherIndex < 1 || otherIndex >= table.rows.length)
            return false;
        var otherRow = table.rows[otherIndex];

        swapRowProperties(row, otherRow, "title", "value");
        swapRowProperties(row, otherRow, "helpText", "value");
        swapRowProperties(row, otherRow, "multiline", "checked");
        swapRowProperties(row, otherRow, "required", "checked");
        swapRowProperties(row, otherRow, "rememberSiteValue", "checked");
        return false;
    }

    function swapRowProperties(row1, row2, elemName, property)
    {
        var row1Elem = getNamedElemFromRow(row1, elemName);
        var row2Elem = getNamedElemFromRow(row2, elemName);
        var temp = row1Elem[property];
        row1Elem[property] = row2Elem[property];
        row2Elem[property] = temp;
    }

    function getNamedElemFromRow(elem, name)
    {
        var value;
        if (elem.name == name)
            value = elem;
        if (!value && elem.firstChild)
            value = getNamedElemFromRow(elem.firstChild, name);
        if (!value && elem.nextSibling)
            value = getNamedElemFromRow(elem.nextSibling, name);
        return value;
    }

    function deleteRow(elem)
    {
        var table = document.getElementById("inputTable");
        if (table.rows.length == 2)
        {
            alert("At least one input must be present.");
            return false;
        }
        var row = elem;
        while (row.tagName != 'TR')
            row = row.parentNode;
        var titleElem = getNamedElemFromRow(row, "title");
        if (titleElem.value && !confirm("Delete \"" + titleElem.value + "\"?"))
            return false;

        var rowIndex = 0;
        // find our row:
        while (rowIndex < table.rows.length && table.rows[rowIndex] != row)
            rowIndex++;
        table.deleteRow(rowIndex);
        return false;
    }

    function addRow()
    {
        var table = document.getElementById("inputTable");
        table.insertRow(table.rows.length);
        var newRow = table.rows[table.rows.length - 1];

        // create the new cells:
        var buttonCell = newRow.insertCell(newRow.cells.length);
        var titleCell = newRow.insertCell(newRow.cells.length);
        var helpTextCell = newRow.insertCell(newRow.cells.length);
        var multilineCell = newRow.insertCell(newRow.cells.length);
        var requiredCell = newRow.insertCell(newRow.cells.length);
        var rememberSiteValueCell = newRow.insertCell(newRow.cells.length);

        // set the HTML for the new cell:
        buttonCell.innerHTML = <%= PageFlowUtil.jsString(tdButtons)%>;
        titleCell.innerHTML = <%= PageFlowUtil.jsString(tdTitle)%>;
        helpTextCell.innerHTML = <%= PageFlowUtil.jsString(tdHelpText)%>;
        multilineCell.innerHTML = <%= PageFlowUtil.jsString(tdMultiline)%>;
        requiredCell.innerHTML = <%= PageFlowUtil.jsString(tdRequired)%>;
        rememberSiteValueCell.innerHTML = <%= PageFlowUtil.jsString(tdRememberSiteValue)%>;

        // align the checkboxes to center:
        multilineCell.align = "center";
        requiredCell.align = "center";
        rememberSiteValueCell.align = "center";

        // set our checkbox values to be the zero-indexed row number, excluding the header row (hence the "-2")
        getNamedElemFromRow(newRow, "multiline").value = (table.rows.length - 2);
        getNamedElemFromRow(newRow, "required").value = (table.rows.length - 2);
        getNamedElemFromRow(newRow, "rememberSiteValue").value = (table.rows.length - 2);
        return false;
    }

    function verifyForm()
    {
        var table = document.getElementById("inputTable");
        for (var i = 0; i < table.rows.length; i++)
        {
            var row = table.rows[i];
            var elem = getNamedElemFromRow(row, "title");
            if (!elem.value)
            {
                alert("A title is required for all inputs.");
                return false;
            }
        }
        return true;
    }
</script>
<form action="handleUpdateRequestInputs.post" method="POST" onSubmit="return verifyForm()">
    <table id="inputTable">
        <tr>
            <th valign="bottom">&nbsp;</th>
            <th valign="bottom">Title</th>
            <th valign="bottom">Help Text</th>
            <th valign="bottom">Multiline</th>
            <th valign="bottom">Required</th>
            <th valign="bottom">Remember by Site<%= helpPopup("Remember by Site",
                    "If checked, the input will be pre-populated with the previous value entered for the destination site.")%></th>
        </tr>
    <%
        SampleManager.SpecimenRequestInput[] inputs = bean.getInputs();
        for (int inputIndex = 0; inputIndex < inputs.length; inputIndex++)
        {
            SampleManager.SpecimenRequestInput input = inputs[inputIndex];
    %>
        <tr>
            <td><%= tdButtons %></td>
            <td><input type="text" name="title" size="20" value="<%= h(input.getTitle()) %>"></td>
            <td><input type="text" name="helpText" size="50" value="<%= h(input.getHelpText()) %>"></td>
            <td align="center"><input type="checkbox" value="<%= inputIndex %>" name="multiline" <%= input.isMultiLine() ? "CHECKED" : "" %>></td>
            <td align="center"><input type="checkbox" value="<%= inputIndex %>" name="required"<%= input.isRequired() ? "CHECKED" : "" %>></td>
            <td align="center"><input type="checkbox" value="<%= inputIndex %>" name="rememberSiteValue"<%= input.isRememberSiteValue() ? "CHECKED" : "" %>></td>
        </tr>
    <%
        }
    %>
    </table>
    <%= buttonImg("Add New Input", "return addRow()")%>
    <%= generateSubmitButton("Save")%>
    <%= generateButton("Cancel", new ActionURL(StudyController.ManageStudyAction.class, bean.getContainer()))%>
</form>