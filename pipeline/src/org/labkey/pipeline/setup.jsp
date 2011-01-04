<%
/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.pipeline.PipelineJobService" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.pipeline.view.SetupForm" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.pipeline.mule.PipelineJobRunnerGlobus" %>
<%@ page import="java.io.File" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<SetupForm> thisView = (JspView<SetupForm>) HttpView.currentView();
    SetupForm bean = thisView.getModelBean();

    // the default project pipeline root based on the site default root
    String projectDefaultRoot = "";
    String folderRadioBtnLabel = "Set a pipeline override";
/*
    if (!getViewContext().getContainer().isProject())
        folderRadioBtnLabel = "Use a folder specific pipeline root";
*/

    File siteRoot = ServiceRegistry.get().getService(FileContentService.class).getSiteDefaultRoot();
    if (siteRoot != null)
    {
        File projRoot = new File(siteRoot, getViewContext().getContainer().getProject().getName());
        if (projRoot != null)
        {
            // Show the user the path that we'd point to if using the default location
            projectDefaultRoot = projRoot.getAbsolutePath();
        }
    }

    if (bean.getConfirmMessage() != null)
    { %>
            <p class="labkey-message"><%=h(bean.getConfirmMessage()) %></p>
    <% }
%>

<labkey:errors />

<script type="text/javascript">
    Ext.onReady(function()
    {
        updatePipelineSelection();
    });

    function updatePipelineSelection()
    {
        if (document.getElementById('pipeOptionSiteDefault').checked)
        {
            var permDiv = document.getElementById('pipelineFilesPermissions');
            if (permDiv) permDiv.style.display = 'none';

            document.getElementById('pipeProjectRootPath').style.display = 'none';
            document.getElementById('pipeRootPath').style.display = '';
            document.getElementById('pipeIndexTd').style.display = 'none';
            document.getElementById('pipeSupplementalPathTd').style.display = 'none';
        }
        if (document.getElementById('pipeOptionProjectSpecified').checked)
        {
            var permDiv = document.getElementById('pipelineFilesPermissions');
            if (permDiv) permDiv.style.display = '';

            document.getElementById('pipeProjectRootPath').style.display = '';
            document.getElementById('pipeRootPath').style.display = 'none';
            document.getElementById('pipeIndexTd').style.display = '';
            document.getElementById('pipeSupplementalPathTd').style.display = '';
        }
    }

    function toggleGlobusVisible()
    {
        var newDisplay;
        if (document.getElementById('keyFileRow').style.display == 'none')
        {
            newDisplay = '';
        }
        else
        {
            newDisplay = 'none';
        }
        document.getElementById('keyPasswordRow').style.display = newDisplay;
        document.getElementById('keyFileRow').style.display = newDisplay;
        document.getElementById('certFileRow').style.display = newDisplay;
    }
</script>

<form enctype="multipart/form-data" method="POST" action="">
    <table>
        <tr><td></td></tr>
        <tr><td colspan="10">
            The LabKey Data Processing Pipeline allows you to process and import data files with tools we supply, or
            with tools you build on your own. If you have a pre-existing directory that contains the files you want
            to process, you can set a pipeline override to allow the data processing pipeline to operate on the
            files in your preferred directory instead of the one that LabKey creates for each folder.
<%      if (bean.isShowAdditionalOptionsLink()) { %>
            For additional pipeline options, <a href="<%=urlProvider(PipelineUrls.class).urlSetup(getViewContext().getContainer()).getLocalURIString()%>">click here</a>.
<%      } %>
        </td></tr>
        <tr><td></td></tr>
        <tr>
<%--
            <td class="labkey-form-label">Pipeline root <%=PageFlowUtil.helpPopup("Pipeline root", "Set a project level pipeline root. " +
                "When a project level pipeline root is set, each folder for that project can share the same root or can override with a folder specific location.")%></td>
--%>
            <td colspan="10">
                <table>
                    <tr>
                        <td><input type="radio" name="pipelineRootOption" id="pipeOptionSiteDefault" value="siteDefault"
                            <%="siteDefault".equals(bean.getPipelineRootOption()) ? " checked" : ""%>
                                   onclick="updatePipelineSelection();">
                            <label for="pipeOptionSiteDefault">Use a default based on the site-level root</label></td>
                        <td><input type="text" id="pipeRootPath" size="50" disabled="true" value="<%=h(projectDefaultRoot)%>"></td>
                    </tr>
                    <tr>
                        <td><input type="radio" name="pipelineRootOption" id="pipeOptionProjectSpecified" value="projectSpecified"
                            <%="projectSpecified".equals(bean.getPipelineRootOption()) ? " checked" : ""%>
                                   onclick="updatePipelineSelection();">
                            <label for="pipeOptionProjectSpecified"><%=h(folderRadioBtnLabel)%></label></td>
                        <td><input type="text" id="pipeProjectRootPath" name="path" size="50" value="<%=h(bean.getPath())%>"></td>
                    </tr>
                    <tr>
                        <td></td>
                        <td id="pipeIndexTd"><input type="checkbox" name="searchable" id="pipeOptionIndexable" <%=bean.isSearchable() ? " checked" : ""%>>
                            <label for="pipeOptionIndexable">Allow these files to be searched</label>
                        </td>
                    </tr>
                    <tr>
                        <td></td>
                        <td id="pipeSupplementalPathTd"><input type="checkbox" id="pipeOptionSupplementalPath" <%=bean.getSupplementalPath() == null ? "" : " checked"%> onclick="Ext.get('supplementalPathDiv').dom.style.display = (Ext.get('pipeOptionSupplementalPath').dom.checked ? '' : 'none'); Ext.get('pipeProjectSupplementalPath').dom.disabled = (Ext.get('pipeOptionSupplementalPath').dom.checked ? false : true);">
                            <label for="pipeOptionSupplementalPath">Include a supplemental file location</label>
                            <div id="supplementalPathDiv" <% if (bean.getSupplementalPath() == null) { %>style="display:none"<% } %>>
                                <input type="text" id="pipeProjectSupplementalPath" <% if (bean.getSupplementalPath() == null) { %>disabled<% } %> name="supplementalPath" size="50" value="<%=h(bean.getSupplementalPath())%>">
                            </div>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <%
        if (PipelineService.get().isEnterprisePipeline() &&
                PipelineJobService.get().getGlobusClientProperties() != null)
        {
            List<String> warnings = PipelineJobRunnerGlobus.checkGlobusConfiguration(bean.getGlobusKeyPair());
            for (String warning : warnings)
            { %>
                <tr>
                    <td class="labkey-form-label">Warning:</td>
                    <td class="labkey-error"><%= warning %></td>
                </tr><%
            }
            boolean showConfig = true;
            if (bean.getGlobusKeyPair() != null)
            {
                showConfig = false; %>
                <tr>
                    <td class="labkey-form-label">Upload new Globus SSL configuration<labkey:helpPopup title="Use existing config">Check this box if you would like to replace the existing Globus configuration for this pipeline root.</labkey:helpPopup>:</td>
                    <td><input type="checkbox" name="uploadNewGlobusKeys" onclick="toggleGlobusVisible()">
                        <%
                            X509Certificate[] certs = bean.getGlobusKeyPair().getCertificates();
                            if (certs != null && certs.length > 0)
                            { %>
                                <br/>Current configuration expires <%= DateUtil.formatDate(certs[0].getNotAfter())%><%
                                if (certs[0].getSubjectX500Principal() != null)
                                {
                                    %>; Issued to <%=h(certs[0].getSubjectX500Principal().getName()) %>
                            <%  }
                            } %>
                    </td>
                </tr>
            <% }
            else
            {
                %><input type="hidden" name="uploadNewGlobusKeys" value="true" /><%
            } %>
            <tr id="keyFileRow" style="display: <%= showConfig ? "" : "none" %>">
                <td class="labkey-form-label">Globus SSL private key<labkey:helpPopup title="Globus SSL private key"><p>This is typically stored in a file with a .pem extension. It should be in a BASE64 encoded PKCS#8 file format, and may be encrypted.</p><p>If you open the file in a text editor, the first line should be:</p><pre>-----BEGIN RSA PRIVATE KEY-----</pre></labkey:helpPopup>:</td>
                <td><input type="file" size="70" name="keyFile"></td>
            </tr>
            <tr id="keyPasswordRow" style="display: <%= showConfig ? "" : "none" %>">
                <td class="labkey-form-label">Private key password<labkey:helpPopup title="Private key password">If your private key has been encrypted, you must specify the password so that it can be decrypted.</labkey:helpPopup>:</td>
                <td><input type="text" size="20" name="keyPassword" value=""></td>
            </tr>
            <tr id="certFileRow" style="display: <%= showConfig ? "" : "none" %>">
                <td class="labkey-form-label">Globus SSL certificate<labkey:helpPopup title="Globus SSL certificate"><p>This is typically stored in a file with a .pem extension. It should contain your BASE64 encoded X.509 certificatein a PKCS#8 file format.</p><p>If you open the file in a text editor, it should contain:</p><pre>-----BEGIN CERTIFICATE-----</pre></labkey:helpPopup>:</td>
                <td><input type="file" size="70" name="certFile"></td>
            </tr><%
        }
        %>
        <tr>
            <td colspan="2"><labkey:button text="Save"/></td>
        </tr>
    </table>
    <input type="hidden" name="pipelineRootForm" value="true">
</form>
