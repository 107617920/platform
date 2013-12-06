<%
/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.model.VisitMapKey" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager.VisitStatistic" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Collections" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    BaseStudyController.StudyJspView<VisitImpl> me = (BaseStudyController.StudyJspView<VisitImpl>) HttpView.currentView();
    VisitImpl visit = me.getModelBean();
    StudyImpl study = getStudy();

    StudyManager manager = StudyManager.getInstance();
    VisitManager visitManager = manager.getVisitManager(study);
    Map<VisitMapKey, VisitManager.VisitStatistics> summaryMap = visitManager.getVisitSummary(null, null, Collections.singleton(VisitStatistic.RowCount), true);
    int datasetRowCount = 0;

    for (Map.Entry<VisitMapKey, VisitManager.VisitStatistics> e : summaryMap.entrySet())
    {
        VisitMapKey key = e.getKey();

        if (key.visitRowId == visit.getRowId())
            datasetRowCount += e.getValue().get(VisitStatistic.RowCount);
    }

    int vialCount = SampleManager.getInstance().getSampleCountForVisit(visit);
%>
<labkey:errors/>

<form action="<%=h(urlFor(StudyController.DeleteVisitAction.class))%>" method=POST>
    Do you want to delete <%=h(visitManager.getLabel())%> <b><%=h(visit.getDisplayString())%></b>?<p/>
    <%if (datasetRowCount != 0)
    {
        %>This <%=h(visitManager.getLabel())%> has <%=datasetRowCount%> dataset results
        <%
        if (vialCount > 0)
        {
            %> and <%= vialCount %> specimen vials<%
        }
        %>
        which will also be deleted.<p/><%
    }%>
    <%=generateSubmitButton("Delete")%>&nbsp;<%=PageFlowUtil.generateSubmitButton("Cancel", "javascript:window.history.back(); return false;")%>
    <input type=hidden name=id value="<%=visit.getRowId()%>">
</form>
