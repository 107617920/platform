<%
/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.exp.api.ExpSampleSet"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.samples.UploadMaterialSetForm" %>
<%@ page import="org.labkey.experiment.samples.UploadMaterialSetForm.InsertUpdateChoice" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.exp.query.ExpMaterialTable" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>

<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<UploadMaterialSetForm> view = (JspView<UploadMaterialSetForm>) HttpView.currentView();
    UploadMaterialSetForm form = view.getModelBean();
    ExpSampleSet sampleSet = form.getSampleSet();
%>

<%!
    public String getDisplayName(DomainProperty prop)
    {
        if (prop.getLabel() != null)
            return prop.getLabel();
        return ColumnInfo.labelFromName(prop.getName());
    }

    public String dumpDomainProperty(DomainProperty dp)
    {
        if (dp == null)
            return "null";

        String displayName = getDisplayName(dp);

        StringBuilder sb = new StringBuilder("{");
        sb.append("name : ").append(q(dp.getName())).append(",\n");
        sb.append("label : ").append(q(displayName)).append(",\n");

        sb.append("aliases : [");
        sb.append(q(dp.getName())).append(", ");
        sb.append(q(displayName)).append(", ");
        for (String alias : dp.getImportAliasSet())
            sb.append(alias).append(", ");
        sb.append(q(dp.getPropertyURI()));
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    public String dumpSampleSet(ExpSampleSet ss)
    {
        if (ss == null)
            return "null";

        StringBuilder sb = new StringBuilder("{");
        if (ss.hasNameAsIdCol())
        {
            sb.append("col1 : {");
            sb.append("name: '").append(ExpMaterialTable.Column.Name).append("',\n");
            sb.append("label: '").append(ExpMaterialTable.Column.Name).append("',\n");
            sb.append("aliases: [ '").append(ExpMaterialTable.Column.Name).append("' ]");
            sb.append("},\n");

            sb.append("col2 : null,\n");
            sb.append("col3 : null\n");
        }
        else
        {
            sb.append("col1 : ").append(dumpDomainProperty(ss.getIdCol1())).append(",\n");
            sb.append("col2 : ").append(dumpDomainProperty(ss.getIdCol2())).append(",\n");
            sb.append("col3 : ").append(dumpDomainProperty(ss.getIdCol3())).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
%>

<form onSubmit="return validateKey();" action="showUploadMaterials.view" method="post">
<labkey:errors />
<table>
    <tr>
        <td class="labkey-form-label">Name</td>
        <td>
            <% if (form.isImportMoreSamples() || form.getNameReadOnly()) {  %>
                <input type="hidden" name="importMoreSamples" value="<%=h(form.isImportMoreSamples())%>"/>
                <input type="hidden" name="nameReadOnly" value="<%=h(form.getNameReadOnly())%>"/>
                <input id="name" type="hidden" name="name" value="<%=h(form.getName())%>"><%= h(form.getName())%>
            <% }
            else
            { %>
                <input id="name" type="text" name="name" value="<%=h(form.getName())%>">
            <% }%>
        </td>
    </tr>
    <% if (form.isImportMoreSamples()) { %>
        <tr>
            <td class="labkey-form-label">Insert/Update Options</td>
            <td>This sample set already exists.  Please choose how the uploaded samples should be merged with the existing samples.<br>
                <labkey:radio name="insertUpdateChoice" id="insertOnlyChoice" value="<%=InsertUpdateChoice.insertOnly%>" currentValue="<%=form.getInsertUpdateChoice()%>" /> <label for="insertOnlyChoice">Insert only new samples; error if trying to update an existing sample.</label><br>
                <labkey:radio name="insertUpdateChoice" id="insertIgnoreChoice" value="<%=InsertUpdateChoice.insertIgnore%>" currentValue="<%=form.getInsertUpdateChoice()%>" /> <label for="insertIgnoreChoice">Insert only new samples; ignore any existing samples.</label><br>
                <labkey:radio name="insertUpdateChoice" id="insertOrUpdateChoice" value="<%=InsertUpdateChoice.insertOrUpdate%>" currentValue="<%=form.getInsertUpdateChoice()%>" /> <label for="insertOrUpdateChoice">Insert any new samples and update existing samples.</label><br>
                <labkey:radio name="insertUpdateChoice" id="updateOnlyChoice" value="<%=InsertUpdateChoice.updateOnly%>" currentValue="<%=form.getInsertUpdateChoice()%>" /> <label for="updateOnlyChoice">Update only existing samples with new values; error if sample doesn't already exist.</label><br>
            </td>
        </tr>
    <% } %>
    <tr>
        <td class="labkey-form-label">Sample Set Data</td>
        <td>
            Sample set uploads must formatted as tab separated values (TSV). Copy/paste from Microsoft Excel works well.<br>
            The first row should contain column names, and subsequent rows should contain the data.
            <br>
            <b>Note:</b> If there is a column <em>'Name'</em>, it will be chosen as the unique identifier for the sample set.
            <br>
            <textarea id="textbox" onchange="updateIds(this)" rows=25 cols="120" style="width: 100%;" name="data" wrap="off"><%=h(form.getData())%></textarea>
            <script type="text/javascript">
                Ext.EventManager.on('textbox', 'keydown', handleTabsInTextArea);
            </script>
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Id Columns<%= helpPopup("Id Columns", "Id columns must form a unique key for every row.")%></td>
        <td>
                <% if (form.isImportMoreSamples() && sampleSet != null && sampleSet.hasIdColumns())
                {
                    if (sampleSet.hasNameAsIdCol())
                    {
                        %><%=ExpMaterialTable.Column.Name%><%
                    }
                    else
                    {
                        %><%= h(getDisplayName(sampleSet.getIdCol1())) %><%
                        if (sampleSet.getIdCol2() != null)
                        {
                            %>, <%= h(getDisplayName(sampleSet.getIdCol2())) %><%
                        }
                        if (sampleSet.getIdCol3() != null)
                        {
                            %>, <%= h(getDisplayName(sampleSet.getIdCol3())) %><%
                        }
                    }
                }
                else
                { %>
                <table>
                    <tr>
                        <td align="right">#1:</td>
                        <td>
                            <select id="idCol1" name="idColumn1" >
                                <labkey:options value="<%=form.getIdColumn1()%>" map="<%=form.getKeyOptions(false)%>" />
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>#2 (if needed):</td>
                        <td>
                            <select id="idCol2" name="idColumn2">
                                <labkey:options value="<%=form.getIdColumn2()%>" map="<%=form.getKeyOptions(true)%>" />
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>#3 (if needed):</td>
                        <td>
                            <select id="idCol3" name="idColumn3">
                                <labkey:options value="<%=form.getIdColumn3()%>" map="<%=form.getKeyOptions(true)%>" />
                            </select>
                        </td>
                    </tr>
                </table>
            <% } %>
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Parent Column<%= helpPopup("Parent Column", "The column that name of a parent sample that is visible from this folder. Parent samples are automatically linked to child samples. You may comma separate the names if a sample has more than one parent.")%></td>
        <td>
            <% if (form.isImportMoreSamples() && sampleSet != null && sampleSet.getParentCol() != null)
            { %>
                <%= h(getDisplayName(sampleSet.getParentCol()))%>
            <% }
            else
            { %>
            <select id="parentCol" name="parentColumn">
                <labkey:options value="<%=form.getParentColumn()%>" map="<%=form.getKeyOptions(true)%>" />
            </select>
            <% } %>
        </td>
    </tr>
    <tr>
        <td/>
        <td>
            <%=PageFlowUtil.generateSubmitButton("Submit")%>
            <%=PageFlowUtil.generateButton("Clear", "", "javascript:clearValues()")%></td>
    </tr>

</table>

<div style="display:none" id="uploading"><blink>Please wait while data is uploaded.</blink></div>
</form>
<script type="text/javascript">
var fields = [];
var header = [];
var nameColIndex = -1;
var sampleSet = <%=dumpSampleSet(sampleSet)%>;

function updateIdSelect(select, header, allowBlank)
{
    if (select == null)
    {
        return;
    }
    var selectedIndex = select.selectedIndex;
    select.options.length = 0;
    if (header.length == 0)
    {
        var option = new Option("<Paste sample set data, then choose a field>", 0);
        select.options[select.options.length] = option;
        select.disabled = false;
        return;
    }
    if (allowBlank)
    {
        var option = new Option("", -1);
        select.options[select.options.length] = option;
    }

    for (var i = 0; i < header.length; i ++)
    {
        if (header[i].toLowerCase() == "name")
        {
            // Select the 'Name' column for idCol1, unselect for idCol2 and idCol3
            if (select.id == "idCol1")
                selectedIndex = select.options.length;
            else if (select.id == "idCol2" || select.name == "idCol3")
                selectedIndex = -1;
        }

        var option = new Option(header[i] == "" ? "column" + i : header[i], i);
        select.options[select.options.length] = option;
    }
    if (selectedIndex < select.options.length)
    {
        select.selectedIndex = selectedIndex;
    }

    // Enable/disable idCol1, idCol2, and idCol3 if "Name" column is present
    if (select.id in {idCol1:true, idCol2:true, idCol3:true})
        select.disabled = nameColIndex != -1;
}
function updateIds(textbox)
{
    var txt = textbox.value.trim();
    var rows = txt.split("\n");
    header = [];
    fields = new Array();
    if (rows.length >= 2)
    {
        for (var i = 0; i < rows.length; i++)
        {
            fields[i] = rows[i].split("\t");
        }
        header = fields[0];
    }

    nameColIndex = -1;
    for (var i = 0; i < header.length; i++)
    {
        if (header[i].toLowerCase() == "name")
        {
            nameColIndex = i;
            break;
        }
    }

    updateIdSelect(document.getElementById("idCol1"), header, false);
    updateIdSelect(document.getElementById("idCol2"), header, true);
    updateIdSelect(document.getElementById("idCol3"), header, true);
    updateIdSelect(document.getElementById("parentCol"), header, true);
}

function clearValues()
{
    var textbox = document.getElementById("textbox");
    textbox.value = "";
    updateIds(textbox);
}

function validateKey()
{
    var name = document.getElementById("name").value;
    if (!(name != null && name.trim().length > 0))
    {
        alert("Name is required");
        return false;
    }

    <% if (form.isImportMoreSamples()) { %>
        var insertOnlyChoice = document.getElementById("insertOnlyChoice");
        var insertIgnoreChoice = document.getElementById("insertIgnoreChoice");
        var insertOrUpdateChoice = document.getElementById("insertOrUpdateChoice");
        var updateOnlyChoice = document.getElementById("updateOnlyChoice");
        if (!(insertOnlyChoice.checked || insertIgnoreChoice.checked || insertOrUpdateChoice.checked || updateOnlyChoice.checked))
        {
            alert("Please select how to deal with duplicates by selecting one of the insert/update options.");
            return false;
        }
    <% } %>

    var text = document.getElementById("textbox");
    if (text.value.match("/^\\s*\$/"))
    {
        alert("Please paste data in text field.");
        return false;
    }
    if (fields == null || fields.length < 2)
    {
        alert("Please paste data with at least a header and one row of data.");
        return false;
    }
    var select1 = document.getElementById("idCol1");
    var select2 = document.getElementById("idCol2");
    var select3 = document.getElementById("idCol3");
    var colIndex = [ -1, -1, -1 ];
    var colNames = [ '', '', '' ];
    if (select1)
    {
        if (nameColIndex != -1)
        {
            colIndex[0] = nameColIndex;
            colNames[0] = header[nameColIndex];
        }
        else
        {
            colIndex[0] = parseInt(select1.options[select1.selectedIndex].value);
            colNames[0] = select1.options[select1.selectedIndex].text;

            colIndex[1] = parseInt(select2.options[select2.selectedIndex].value);
            colNames[1] = select2.options[select2.selectedIndex].text;

            colIndex[2] = parseInt(select3.options[select3.selectedIndex].value);
            colNames[2] = select3.options[select3.selectedIndex].text;
        }

        if (colIndex[1] != -1 && colIndex[0] == colIndex[1])
        {
            alert("You cannot use the same id column twice.");
            return false;
        }
        if (colIndex[2] != -1 && (colIndex[0] == colIndex[2] || colIndex[1] == colIndex[2]))
        {
            alert("You cannot use the same id column twice.");
            return false;
        }
        // Check if they selected a column 3 but not a column 2
        if (colIndex[2] != -1 && colIndex[1] == -1)
        {
            colIndex[1] = colIndex[2];
            colIndex[2] = -1;
        }
    }
    else
    {
        if (sampleSet != null)
        {
            for (var colNum = 0; colNum < 3; colNum++)
            {
                var sampleSetCol = sampleSet["col" + (colNum+1)];
                if (!sampleSetCol)
                    continue;

                colNames[colNum] = sampleSetCol.label;

                for (var col = 0; col < header.length; col++)
                {
                    var heading = header[col];
                    if (!heading)
                        continue;
                    heading = heading.toLowerCase();
                    for (var aliasIndex = 0; aliasIndex < sampleSetCol.aliases.length; aliasIndex++)
                    {
                        var alias = sampleSetCol.aliases[aliasIndex];
                        if (alias && alias.toLowerCase() == heading)
                        {
                            colIndex[colNum] = col;
                            break;
                        }
                    }
                }

                if (colIndex[colNum] == -1)
                {
                    alert("You must include the Id column '" + colNames[colNum] + "' in your data.");
                    return false;
                }
            }
        }
    }

    var hash = new Object();
    for (var i = 1; i < fields.length; i++)
    {
        var val = undefined;
        for (var colNum = 0; colNum < 3; colNum++)
        {
            var index = colIndex[colNum];
            if (colNum > 0 && index == -1)
                continue;
            var colVal = fields[i][index];
            if (!colVal || "" == colVal)
            {
                alert("All samples must include a value in the '" + colNames[colNum] + "' column.");
                return false;
            }

            val = (colNum == 0 ? colVal : val + "-" + colVal);
        }

        if (hash[val])
        {
            alert("The ID columns chosen do not form a unique key. The key " + val + " is on rows " + hash[val] + " and " + i + ".");
            return false;
        }
        hash[val] = i;
    }

    // As the last step, make sure we don't post the select2 and select3 values
    if (select1 && nameColIndex > -1)
    {
        select1.selectedIndex = nameColIndex;
        select1.disabled = false; // disabled inputs don't post values
        if (select2) select2.selectedIndex = -1;
        if (select3) select3.selectedIndex = -1;
    }

    document.getElementById("uploading").style.display = "";
    return true;
}
updateIds(document.getElementById("textbox"));
</script>
