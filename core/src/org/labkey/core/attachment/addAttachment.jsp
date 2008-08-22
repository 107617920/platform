<%
/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.attachment.AttachmentServiceImpl" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    String entityId = ((AttachmentServiceImpl.AddAttachmentView) HttpView.currentView()).getModelBean();
%>
<script type="text/javascript" language="javascript">
    LABKEY.requiresScript('util.js');
</script>
<labkey:errors />
Browse for file and then click submit<br>
<form method="POST" action="addAttachment.post" enctype="multipart/form-data">
  <input type="file" name="formFiles[0]" size="50" onChange="showPathname(this, 'filename')"><br>
  <input type="hidden" name="entityId" value="<%= entityId %>" /><br>
  <table>
    <tr>
      <td><%=PageFlowUtil.generateSubmitButton("Submit")%></td>
      <td><%=PageFlowUtil.generateButton("Cancel", "", "window.close();")%></td>
      <td style="padding-left:5px;"><label id="filename"></label></td>
    </tr>
  </table>
</form>
