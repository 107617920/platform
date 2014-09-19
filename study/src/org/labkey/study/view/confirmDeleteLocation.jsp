<%
    /*
     * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<%
    StudyController.LocationForm form = (StudyController.LocationForm)getModelBean();
%>
<% if (form.getLabels().length == 0) { %>
<p>All Locations are in use</p>
<% }
else if (form.getLabels().length == 1) { %>
<p>Are you sure you want to delete the site: '<%=h(form.getLabels()[0])%>'?</p>
<% }
else if (form.getLabels().length > 1) { %>
<p>Are you sure you want to delete the following sites? </p>
<ul>
    <%for(String s : form.getLabels()) { %>
    <li>'<%=h(s)%>'</li>
    <% } %>
</ul>
<% } %>
