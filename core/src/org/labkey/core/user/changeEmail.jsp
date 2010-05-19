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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.user.UserController.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ChangeEmailBean> me = (JspView<ChangeEmailBean>) HttpView.currentView();
    ChangeEmailBean bean = me.getModelBean();
%>
<form method="post" action="showChangeEmail.post"><labkey:csrf/>
<input type="hidden" name="userId" value="<%=bean.userId%>">
<table><%=formatMissedErrorsInTable("form", 2)%>
    <tr>
        <td>Current Email:</td>
        <td><%=h(bean.currentEmail)%></td>
    </tr>
    <tr>
        <td>New Email:</td>
        <td><input type="text" name="newEmail" id="newEmail" value=""></td>
    </tr>
    <tr>
        <td colspan=2><%=PageFlowUtil.generateSubmitButton("Submit")%></td>
    </tr>
</table>
</form>
