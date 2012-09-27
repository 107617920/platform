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
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.*" %>
<%@ page import="org.labkey.study.controllers.StudyDefinitionController" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.importer.StudyReload" %>
<%@ page import="org.labkey.study.model.ParticipantCategoryImpl" %>
<%@ page import="org.labkey.study.model.ParticipantGroupManager" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.query.StudyPropertiesQueryView" %>
<%@ page import="org.labkey.study.security.permissions.ManageRequestSettingsPermission" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!

  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
      resources.add(ClientDependency.fromFilePath("clientapi/ext3"));
      resources.add(ClientDependency.fromFilePath("reports/rowExpander.js"));
      resources.add(ClientDependency.fromFilePath("study/ParticipantGroup.js"));
      resources.add(ClientDependency.fromFilePath("FileUploadField.js"));
      resources.add(ClientDependency.fromFilePath("study/StudyWizard.js"));
      return resources;
  }
%>
<%
    JspView<StudyPropertiesQueryView> me = (JspView<StudyPropertiesQueryView>) HttpView.currentView();
    StudyImpl study = getStudy();
    Container c = me.getViewContext().getContainer();

    String visitLabel = StudyManager.getInstance().getVisitManager(study).getPluralLabel();
    ActionURL manageCohortsURL = new ActionURL(CohortController.ManageCohortsAction.class, c);
    User user = HttpView.currentContext().getUser();
    int numProperties = study.getNumExtendedProperties(user);
    String propString = numProperties == 1 ? "property" : "properties";

    StudyReload.ReloadInterval currentInterval = StudyReload.ReloadInterval.getForSeconds(study.getReloadInterval());
    String intervalLabel;

    String subjectNounSingle = StudyService.get().getSubjectNounSingular(getViewContext().getContainer());
    ParticipantCategoryImpl[] categories = ParticipantGroupManager.getInstance().getParticipantCategories(c, user);

    if (!study.isAllowReload())
        intervalLabel = "This study is set to not reload";
    else if (null == study.getReloadInterval() || 0 == study.getReloadInterval())
        intervalLabel = "This study is set for manual reloading";
    else
        intervalLabel = "This study is scheduled to check for reload " + (StudyReload.ReloadInterval.Never != currentInterval ? currentInterval.getDescription() : "every " + study.getReloadInterval() + " seconds");

    Map<String, Container> folders = new HashMap<String, Container>();
    for (Container child : ContainerManager.getChildren(c))
        folders.put(child.getName(), child);

    String ancillaryStudyName = "New Study";
    int i = 1;
    while (folders.containsKey(ancillaryStudyName))
    {
        ancillaryStudyName = "New Study " + i++;
    }

    int numDatasets = study.getDataSetsByType(new String[]{org.labkey.api.study.DataSet.TYPE_STANDARD, org.labkey.api.study.DataSet.TYPE_PLACEHOLDER}).size();
%>
<table>
    <%
        if (c.hasPermission(user, AdminPermission.class))
        {
    %>
    <tr>
        <td colspan="3" class="labkey-announcement-title"><span>General Study Settings</span></td>
    </tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr>
        <th align="left">Study Properties</th>
        <td>Study label, investigator, grant, description, etc.</td>
        <td><%= textLink("Change Study Properties", ManageStudyPropertiesAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Additional Properties</th>
        <td>This study has <%=numProperties%> additional <%=propString%></td>
        <td><%
            Container p = c.getProject();
            if (p.hasPermission(user,AdminPermission.class))
            {
                ActionURL returnURL = getViewContext().getActionURL();
                ActionURL editDefinition = new ActionURL(StudyDefinitionController.EditStudyDefinitionAction.class, p);
                editDefinition.addReturnURL(returnURL);
                %><%= textLink("Edit Definition", editDefinition) %><%
            }
            else
            {
                %>&nbsp;<%
            }
        %></td>
    </tr>
    <tr>
        <th align="left">Reloading</th>
        <td><%= h(intervalLabel) %></td>
        <td><%= textLink("Manage Reloading", ManageReloadAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Datasets</th>
        <td>This study defines <%= numDatasets %> Datasets</td>
        <td><%= textLink("Manage Datasets", ManageTypesAction.class) %></td>
    </tr>
    <% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
    <tr>
        <th align="left"><%= visitLabel %></th>
        <td>This study defines <%= getVisits(Visit.Order.DISPLAY).length %> <%=visitLabel%></td>
        <td><%= textLink("Manage " + visitLabel, ManageVisitsAction.class) %></td>
    </tr>
    <% } %>
     <tr>
        <th align="left">Study Schedule</th>
         <td>This study defines <%= numDatasets %> Datasets
             <% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
             and <%= getVisits(Visit.Order.DISPLAY).length %> <%=visitLabel%>
             <% } %>
         </td>
        <td><%= textLink("Study Schedule", StudyController.StudyScheduleAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Locations</th>
        <td>This study references <%= getSites().length %> labs/sites/repositories</td>
        <td><%= textLink("Manage Labs/Sites", ManageSitesAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Cohorts</th>
        <td>This study defines <%= getCohorts(getViewContext().getUser()).length %> cohorts</td>
        <td><%= textLink("Manage Cohorts", manageCohortsURL) %></td>
    </tr>
    <tr>
        <th align="left"><%= h(subjectNounSingle) %> Groups</th>
        <td>This study defines <%=categories.length%> <%= h(subjectNounSingle.toLowerCase()) %> groups</td>
        <td><%= textLink("Manage " + h(subjectNounSingle) + " Groups", new ActionURL(StudyController.ManageParticipantCategoriesAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Alternate <%= h(subjectNounSingle) %> IDs</th>
        <td>Configure how alternate <%= h(subjectNounSingle.toLowerCase()) %> ids are generated</td>
        <td><%= textLink("Manage Alternate " + h(subjectNounSingle) + " IDs", new ActionURL(StudyController.ManageAlternateIdsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Security</th>
        <td>Manage access to Study datasets and samples</td>
        <% ActionURL url = new ActionURL(SecurityController.BeginAction.class, c);%>
        <td><%= textLink("Manage Security", url) %></td>
    </tr>
    <tr>
        <th align="left">Reports/Views</th>
        <td>Manage views for this Study</td>
        <td><%=textLink("Manage Views", new ActionURL(ReportsController.ManageReportsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Quality Control States</th>
        <td>Manage QC states for datasets in this Study</td>
        <td><%=textLink("Manage Dataset QC States", new ActionURL(StudyController.ManageQCStatesAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Comments</th>
        <td>Manage <%= h(subjectNounSingle.toLowerCase()) %> and  <%= h(subjectNounSingle.toLowerCase()) %>/visit comments</td>
        <td><%= textLink("Manage Comments",
                new ActionURL(SpecimenController.ManageSpecimenCommentsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Demo Mode</th>
        <td>Demo mode obscures <%=h(subjectNounSingle.toLowerCase())%> IDs on many pages</td>
        <td><%=textLink("Demo Mode",
                new ActionURL(StudyController.DemoModeAction.class, c)) %></td>
    </tr>
<%
        if (!study.isAncillaryStudy() && !study.isSnapshotStudy())
        {
%>

    <tr>
        <td colspan="3" class="labkey-announcement-title"><span>Specimen Repository Settings</span></td>
    </tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr>
        <th align="left">Repository Type</th>
        <td>This study uses the <%=study.getRepositorySettings().isSimple() ? "standard" : "advanced"%> specimen repository</td>
        <td><%=textLink("Change Repository Type", new ActionURL(SpecimenController.ShowManageRepositorySettingsAction.class, c))%></td>
    </tr>
    <tr>
        <th align="left">Display and Behavior</th>
        <td>Manage warnings, comments, and workflow</td>
        <td><%= textLink("Manage Display and Behavior",
                new ActionURL(SpecimenController.ManageDisplaySettingsAction.class, c)) %></td>
    </tr>
<%
        }
        else
        {
%>
    <tr>
        <td colspan="3">
            <p><em>NOTE: specimen repository and request settings are not available for ancillary or published studies.</em></p>
        </td>
    </tr>
<%
        }
    } // admin permission

    if (c.hasPermission(user, ManageRequestSettingsPermission.class))
    {
%>
    <%
        if (study.getRepositorySettings().isEnableRequests())
        {
    %>
    <tr>
        <td colspan="3" class="labkey-announcement-title"><span>Specimen Request Settings</span></td>
    </tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr>
        <th align="left">Statuses</th>
        <td>This study defines <%= study.getSampleRequestStatuses(HttpView.currentContext().getUser()).length %> specimen request
            statuses</td>
        <td><%= textLink("Manage Request Statuses",
                new ActionURL(SpecimenController.ManageStatusesAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Actors</th>
        <td>This study defines <%= study.getSampleRequestActors().length %> specimen request
            actors</td>
        <td><%= textLink("Manage Actors and Groups",
                new ActionURL(SpecimenController.ManageActorsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Request Requirements</th>
        <td>Manage default requirements for new requests</td>
        <td><%= textLink("Manage Default Requirements",
                new ActionURL(SpecimenController.ManageDefaultReqsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Request Form</th>
        <td>Manage inputs required for a new specimen request </td>
        <td><%= textLink("Manage New Request Form",
                new ActionURL(SpecimenController.ManageRequestInputsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Notifications</th>
        <td>Manage specimen request notifications</td>
        <td><%= textLink("Manage Notifications",
                new ActionURL(SpecimenController.ManageNotificationsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Requestability Rules</th>
        <td>Manage the rules used to determine specimen availability for request</td>
        <td><%= textLink("Manage Requestability Rules",
                new ActionURL(SpecimenController.ConfigureRequestabilityRulesAction.class, c)) %></td>
    </tr>
    <%
        }
        }
    %>
</table><br>
<%
    if (c.hasPermission(user, AdminPermission.class))
    {
        ActionURL exportURL = PageFlowUtil.urlProvider(AdminUrls.class).getExportFolderURL(c);
        exportURL.addParameter("exportType", "study");
%>
<%=generateButton("Export Study", exportURL.getURIString())%>
<%=generateButton("Reload Study", StudyController.ImportStudyAction.class)%>
<%=generateButton("Delete Study", StudyController.DeleteStudyAction.class)%>
<%=generateButton("Create Ancillary Study", "javascript:void(0)", "showNewStudyWizard()")%>
<%=generateButton("Publish Study", "javascript:void(0)", "showPublishStudyWizard()")%>
<%
    }
%>
<script type="text/javascript">

    function showNewStudyWizard()
    {
        var init = function(){
            var wizard = new LABKEY.study.CreateStudyWizard({
                mode: 'ancillary',
                studyName : <%=q(ancillaryStudyName)%>,
                studyType: "<%=study.getTimepointType().toString()%>",
                subject: {
                    nounSingular: <%=q(study.getSubjectNounSingular())%>,
                    nounPlural: <%=q(study.getSubjectNounPlural())%>,
                    nounColumnName: <%=q(study.getSubjectColumnName())%>,
                }
            });

            wizard.on('success', function(info){}, this);

            // run the wizard
            wizard.show();
        };
        Ext.onReady(init);
    }
</script>

<script>
    function showPublishStudyWizard()
    {
        var init = function(){
            var wizard = new LABKEY.study.CreateStudyWizard({
                mode: 'publish',
                studyName : <%=q(ancillaryStudyName)%>,
                studyType: "<%=study.getTimepointType().toString()%>",
                subject: {
                    nounSingular: <%=q(study.getSubjectNounSingular())%>,
                    nounPlural: <%=q(study.getSubjectNounPlural())%>,
                    nounColumnName: <%=q(study.getSubjectColumnName())%>
                }
            });

            wizard.on('success', function(info){}, this);

            // run the wizard
            wizard.show();
        };

        Ext.onReady(init);
    }
</script>
