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
<%@ page import="org.labkey.api.security.ValidEmail"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<form action="updateMembers.post" method="POST">
<%
    SecurityController.UpdateMembersBean bean = ((JspView<SecurityController.UpdateMembersBean>)HttpView.currentView()).getModelBean();

//include hidden inputs for each name to be added.
for(ValidEmail email : bean.addnames)
{%>
    <input type="hidden" name="names" value="<%=email.toString()%>"><%
}

//include hidden inputs for each username to be deleted.
for(String username : bean.removenames)
{%>
    <input type="hidden" name="delete" value="<%=username%>"><%
}%>

<input type="hidden" name="group" value="<%= bean.groupName %>">
<input type="hidden" name="mailPrefix" value="<%= null != bean.mailPrefix ? h(bean.mailPrefix) : "" %>">
<input type="hidden" name="confirmed" value="1">

If you delete your own user account from the Administrators group, you will no longer <br>
have administrative privileges. Are you sure that you want to continue?
<br><br>
<%=generateSubmitButton("Delete This Account")%>&nbsp;
<%=PageFlowUtil.generateSubmitButton("Cancel", "javascript:window.history.back(); return false;")%>
</form>