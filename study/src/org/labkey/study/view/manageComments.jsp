<%
    /*
    * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.exp.PropertyType" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<SpringSpecimenController.ManageCommentsForm> me = (JspView<SpringSpecimenController.ManageCommentsForm>) HttpView.currentView();
    SpringSpecimenController.ManageCommentsForm bean = me.getModelBean();

    StudyImpl study = getStudy();
    StudyManager manager = StudyManager.getInstance();

    DataSet[] datasets = manager.getDataSetDefinitions(study);

    PropertyDescriptor[] ptidDescriptors = new PropertyDescriptor[0];
    PropertyDescriptor[] ptidVisitDescriptors = new PropertyDescriptor[0];
    Integer participantCommentDataSetId = bean.getParticipantCommentDataSetId();
    Integer participantVisitCommentDataSetId = bean.getParticipantVisitCommentDataSetId();

    if (participantCommentDataSetId != null && participantCommentDataSetId.intValue() >= 0)
    {
        DataSet dataset = StudyManager.getInstance().getDataSetDefinition(study, participantCommentDataSetId.intValue());
        if (dataset != null)
            ptidDescriptors = OntologyManager.getPropertiesForType(dataset.getTypeURI(), study.getContainer());
    }

    if (participantVisitCommentDataSetId != null && participantVisitCommentDataSetId.intValue() >= 0)
    {
        DataSet dataset = StudyManager.getInstance().getDataSetDefinition(study, participantVisitCommentDataSetId.intValue());
        if (dataset != null)
            ptidVisitDescriptors = OntologyManager.getPropertiesForType(dataset.getTypeURI(), study.getContainer());
    }
%>
<labkey:errors/>

<form action="manageSpecimenComments.post" name="manageComments" method="post">
    <table width="70%">
        <tr><td><b>Note:</b> Only users with read access to the selected dataset(s) will be able to view comment information.</td></tr>
        <tr><td/></tr>
        <tr><td>
            The comments associated with a Participant or Participant/Visit are saved as fields in datasets.
            Each of the datasets can contain multiple fields, but only one field can
            be designated to hold the comment text. Comment fields must be of type text or multi-line text.
            Comments will appear automatically in colums for the specimen and vial views.
        </td></tr>
    </table>


    <input type="hidden" name="reshow" value="true">

    <%
        WebPartView.startTitleFrame(out, "Participant Comment Assignment");
    %>
    <table>
        <tr>
            <th align="right">Comment Dataset<%= helpPopup("Participant/Comment Dataset", "Comments can be associated with on a participant basis. The dataset " +
                    "selected must be a demographics dataset.")%></th>
            <td>
                <select name="participantCommentDataSetId" id="participantCommentDataSetId" onchange="document.manageComments.participantCommentProperty.value=''; document.manageComments.method='get'; document.manageComments.submit()">
                    <option value="-1">[None]</option>
                    <%
                        for (DataSet dataset : datasets)
                        {
                            if (dataset.isDemographicData())
                            {
                                String selected = (bean.getParticipantCommentDataSetId() != null &&
                                        dataset.getDataSetId() == bean.getParticipantCommentDataSetId().intValue() ? "selected" : "");
                                %><option value="<%= dataset.getDataSetId() %>" <%= selected %>><%= h(dataset.getLabel()) %></option><%
                            }
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Comment Field Name</th>
            <td>
                <select name="participantCommentProperty" id="participantCommentProperty">
                    <option value="">[None]</option>
                <%
                for (PropertyDescriptor pd : ptidDescriptors)
                {
                    if (pd.getPropertyType() == PropertyType.STRING || pd.getPropertyType() == PropertyType.MULTI_LINE) 
                    {
                %>
                    <option value="<%= pd.getName() %>" <%= pd.getName().equals(bean.getParticipantCommentProperty()) ? "SELECTED" : "" %>>
                        <%= h(null == pd.getLabel() ? pd.getName() : pd.getLabel()) %>
                    </option>
                <%
                    }
                }
                %>
                </select>
            </td>
        </tr>
        </table>
    <%
        WebPartView.endTitleFrame(out);
        WebPartView.startTitleFrame(out, "Participant/Visit Comment Assignment");
    %>
    <table>
        <tr>
            <th align="right">Comment Dataset<%= helpPopup("Participant/Comment Dataset", "Comments can be associated with on a participant/visit basis. The dataset " +
                    "selected cannot be a demographics dataset.")%></th>
            <td>
                <select name="participantVisitCommentDataSetId" id="participantVisitCommentDataSetId" onchange="document.manageComments.participantVisitCommentProperty.value=''; document.manageComments.method='get'; document.manageComments.submit()">
                    <option value="-1">[None]</option>
                    <%
                        for (DataSet dataset : datasets)
                        {
                            if (!dataset.isDemographicData())
                            {
                                String selected = (bean.getParticipantVisitCommentDataSetId() != null &&
                                        dataset.getDataSetId() == bean.getParticipantVisitCommentDataSetId().intValue() ? "selected" : "");
                                %><option value="<%= dataset.getDataSetId() %>" <%= selected %>><%= h(dataset.getLabel()) %></option><%
                            }
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Comment Field Name</th>
            <td>
                <select name="participantVisitCommentProperty" id="participantVisitCommentProperty">
                    <option value="">[None]</option>
                <%
                for (PropertyDescriptor pd : ptidVisitDescriptors)
                {
                    if (pd.getPropertyType() == PropertyType.STRING || pd.getPropertyType() == PropertyType.MULTI_LINE)
                    {
                %>
                    <option value="<%= pd.getName() %>" <%= pd.getName().equals(bean.getParticipantVisitCommentProperty()) ? "SELECTED" : "" %>>
                        <%= h(null == pd.getLabel() ? pd.getName() : pd.getLabel()) %>
                    </option>
                <%
                    }
                }
                %>
                </select>
            </td>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <%=generateSubmitButton("Save")%>
                <%=generateButton("Cancel", new ActionURL(StudyController.ManageStudyAction.class, HttpView.currentContext().getContainer()))%>
            </td>
        </tr>
        </table>
    <%
        WebPartView.endTitleFrame(out);
    %>
</form>