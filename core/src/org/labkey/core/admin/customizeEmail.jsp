<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplate" %>
<%@ page import="org.labkey.api.util.emailTemplate.EmailTemplateService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.CustomEmailForm> me = (JspView<AdminController.CustomEmailForm>) HttpView.currentView();
    AdminController.CustomEmailForm bean = me.getModelBean();
    Container c = getViewContext().getContainer();

    List<EmailTemplate> emailTemplates = EmailTemplateService.get().getEditableEmailTemplates(c);
    String errorHTML = formatMissedErrors("form");
%>
<%=errorHTML%>

<form action="customizeEmail.view" method="post">
    <% if (bean.getReturnUrl() != null) { %>
        <input type="hidden" name="returnUrl" value="<%= bean.getReturnUrl()%>" />
    <% } %>
    <table>
        <tr class="labkey-wp-header"><th colspan=2>Custom Emails</th></tr>
        <tr><td></td></tr>
        <tr><td class="labkey-form-label">Email Type:</td>
            <td><select id="templateClass" name="templateClass" onchange="changeEmailTemplate();">
<%
        for (EmailTemplate et : emailTemplates)
        {
%>
            <option value="<%=et.getClass().getName()%>" <%=et.getClass().getName().equals(bean.getTemplateClass()) ? "selected" : ""%>><%=et.getName()%></option>
<%
        }
%>
        </select></td></tr>
        <tr><td class="labkey-form-label">Description:</td><td width="600"><div id="emailDescription"></div></td><td></td></tr>
        <tr><td class="labkey-form-label">Subject:</td><td width="600"><input id="emailSubject" name="emailSubject" style="width:100%" value="<%=h(bean.getEmailSubject())%>"></td><td></td></tr>
        <tr><td class="labkey-form-label">Message:</td><td><textarea id="emailMessage" name="emailMessage" style="width:100%" rows="20"><%=h(bean.getEmailMessage())%></textarea></td></tr>
        <tr>
            <td></td><td>
            <%=generateSubmitButton("Save")%>
            <%=generateButton("Cancel", bean.getReturnURLHelper(urlProvider(AdminUrls.class).getAdminConsoleURL()))%>
            <%=PageFlowUtil.generateSubmitButton("Reset to Default Template", "this.form.action='deleteCustomEmail.view'", "id='siteResetButton' style='display: none;'")%>
            <%=PageFlowUtil.generateSubmitButton("Delete Folder-Level Template", "this.form.action='deleteCustomEmail.view'", "id='folderResetButton' style='display: none;'")%>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td></td>
            <td>
                An email subject or message can contain a mix of static text and substitution parameters.
                A substitution parameter is inserted into the text when the email is generated. The syntax is:
                <pre>^&lt;param name&gt;^</pre>
                where &lt;param name&gt; is the name of the substitution parameter shown below. For example:
                <pre>^systemDescription^</pre>

                You may also supply an optional format string. If the value of the parameter is not blank, it
                will be used to format the value in the outgoing email. For the full set of format options available,
                see the <a target="_blank" href="http://download-llnw.oracle.com/javase/6/docs/api/java/util/Formatter.html">documentation for java.util.Formatter</a>. The syntax is:
                <pre>^&lt;param name&gt;|&lt;format string&gt;^</pre>
                For example:
                <pre>^currentDateTime|The current date is: %1$tb %1$te, %1$tY^
^siteShortName|The site short name is not blank and its value is: %s^</pre>
                <br/>
            </td>
        </tr>
        <tr><td></td><td><table id="validSubstitutions"></table></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td></td><td><i>The values of many of these parameters can be configured on
            the <a href="<%=urlProvider(AdminUrls.class).getProjectSettingsURL(c)%>">Look and Feel Settings page</a> and on the Project Settings page for each project.</i>
        </tr>
    </table>
</form><br/><br/>

<script type="text/javascript">

<%
    // create the map of email template names to template properties
    out.write("var emailTemplates = [");
    String sep = "{";
    for (EmailTemplate et : emailTemplates)
    {
        out.write(sep);
        out.write("\t\"name\":\"" + et.getClass().getName() + "\",\n");
        out.write("\t\"description\":" + PageFlowUtil.jsString(et.getDescription()) + ",\n");
        out.write("\t\"subject\":" + PageFlowUtil.jsString(et.getSubject()) + ",\n");
        out.write("\t\"message\":" + PageFlowUtil.jsString(et.getBody()) + ",\n");
        // Let users delete the folder-scoped template only if it's been stored in the same folder they're in, and they're
        // not in the root where they'd be doing a site-level template
        out.write("\t\"showFolderReset\":" + (c.equals(et.getContainer()) && !c.isRoot()) + ",\n");
        // Let users delete a site-scoped template if they're in the root and the template is stored in the root
        out.write("\t\"showSiteReset\":" + (c.isRoot() && c.equals(et.getContainer())) + ",\n");
        out.write("\t\"replacements\":[\n");

        String innerSep = "\t{";
        for (EmailTemplate.ReplacementParam param : et.getValidReplacements())
        {
            out.write(innerSep);
            out.write("\t\t\"paramName\":" + PageFlowUtil.jsString(param.getName()) + ",\n");
            out.write("\t\t\"paramDesc\":" + PageFlowUtil.jsString(param.getDescription()) + ",\n");
            Object value=param.getValue(c);
            out.write("\t\t\"paramValue\":" + PageFlowUtil.jsString(value == null ? null : value.toString()) + "\n");
            out.write("}");

            innerSep = "\t,{";
        }
        out.write("]}");

        sep = ",{";
    }
    out.write("];");

%>
    function changeEmailTemplate()
    {
        var selection = Ext.get('templateClass').dom;
        var subject = Ext.get('emailSubject').dom;
        var message = Ext.get('emailMessage').dom;
        var description = Ext.get('emailDescription').dom;

        for (var i=0; i < this.emailTemplates.length; i++)
        {
            if (this.emailTemplates[i].name == selection.value)
            {
                subject.value = this.emailTemplates[i].subject;
                description.innerHTML = this.emailTemplates[i].description;
                message.value = this.emailTemplates[i].message;
                Ext.get("siteResetButton").dom.style.display = this.emailTemplates[i].showSiteReset ? "" : "none";
                Ext.get("folderResetButton").dom.style.display = this.emailTemplates[i].showFolderReset ? "" : "none";

                changeValidSubstitutions(this.emailTemplates[i]);
                return;
            }
        }
        subject.value = "";
        message.value = "";
        clearValidSubstitutions();
    }

    function clearValidSubstitutions()
    {
        var table = Ext.get('validSubstitutions').dom;
        if (table != undefined)
        {
            // delete all rows first
            var count = table.rows.length;
            for (var i = 0; i < count; i++)
            {
                table.deleteRow(0);
            }
        }
    }

    function changeValidSubstitutions(record)
    {
        // delete all rows first
        clearValidSubstitutions();

        if (record.replacements == undefined)
        {
            var selection = Ext.get('templateClass').dom;
            for (var i=0; i < this.emailTemplates.length; i++)
            {
                if (this.emailTemplates[i].name == selection.value)
                {
                    record = this.emailTemplates[i];
                    break;
                }
            }
        }
        var table = Ext.get('validSubstitutions').dom;
        var row;
        var cell;

        row = table.insertRow(table.rows.length);
        cell = row.insertCell(0);
        cell.className = "labkey-form-label";
        cell.innerHTML = '<strong>Parameter Name</strong>';

        cell = row.insertCell(1);
        cell.className = "labkey-form-label";
        cell.innerHTML = "<strong>Description</strong>";

        cell = row.insertCell(2);
        cell.className = "labkey-form-label";
        cell.innerHTML = "<strong>Current Value</strong>";

        if (record.replacements != undefined)
        {
            for (var i = 0; i < record.replacements.length; i++)
            {
                row = table.insertRow(table.rows.length);
                cell = row.insertCell(0);
                cell.className = "labkey-form-label";
                cell.innerHTML = record.replacements[i].paramName;

                cell = row.insertCell(1);
                cell.innerHTML = record.replacements[i].paramDesc;

                cell = row.insertCell(2);
                var paramValue = record.replacements[i].paramValue;
                cell.innerHTML = paramValue != '' ? paramValue : "<em>not available in designer</em>";
            }
        }
    }
<%
    if (StringUtils.isEmpty(errorHTML)) { %>
    Ext.onReady(function()
        {
            if (LABKEY.ActionURL.getParameter('templateClass'))
            {
                Ext.get('templateClass').dom.value = LABKEY.ActionURL.getParameter('templateClassName');
            }
            changeEmailTemplate();
        });
<% } else { %>
        Ext.onReady(changeValidSubstitutions);
<% } %>
</script>


