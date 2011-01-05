<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AdminController.WebThemesBean> me = (HttpView<AdminController.WebThemesBean>) HttpView.currentView();
    AdminController.WebThemesBean bean = me.getModelBean();
    WebTheme selectedTheme = bean.selectedTheme;
%>
<link href="<%= request.getContextPath() %>/js_color_picker_v2.css" type="text/css" media="screen" rel="stylesheet">
<script type="text/javascript">
    LABKEY.requiresScript('color_functions.js');
    LABKEY.requiresScript('js_color_picker_v2.js');
</script>
<script type="text/javascript">
function isValidColor(color)
{
    if ("" == color) return false;
    if (6 != color.length) return false;
    //todo: check hex
    return true;
}

function getCssRules()
{
    // MSIE is from js_color_picker_v2.js
    var i;
    for (i = 0; i < document.styleSheets.length; i++)
    {
        if (document.styleSheets[i].href != null && document.styleSheets[i].href.indexOf("themeStylesheet.view") != -1)
        {
            if (MSIE)
                return document.styleSheets[i].rules;
            else
                return document.styleSheets[i].cssRules;
        }
    }
}

function updateLinkColor()
{
  var color=document.getElementsByName("linkColor")[0].value;
  if (!isValidColor(color)) return;

  var i;
  var cssRules=getCssRules();
  for ( i = 0; i < cssRules.length; i++ )
  {
    var cssName=cssRules[i].selectorText.toLowerCase();
    if ((cssName.indexOf('labkey-frame')!=-1)
      || (cssName.indexOf('labkey-site-nav-panel')!=-1)
      || (cssName.indexOf('labkey-tab-selected')!=-1)
      || (cssName.indexOf('labkey-nav-tree-row:hover')!=-1)
      )
    {
      cssRules[i].style.backgroundColor="#"+color;
    }
  }
}

function updateTextColor ()
{
  var color=document.getElementsByName("textColor")[0].value;
  if (!isValidColor(color)) return;

  var cssRules=getCssRules();
  for (var i = 0; i < cssRules.length; i++ )
  {
    // headerline
    var cssName=cssRules[i].selectorText.toLowerCase();
    if (cssName.indexOf('labkey-title-area-line')!=-1) {
      cssRules[i].style.backgroundColor="#"+color;
    } else if ((cssName.indexOf('labkey-nav-bordered')!=-1)
      || (cssName.indexOf('labkey-tab')!=-1 && cssName!='labkey-tab-selected')
      || (cssName.indexOf('labkey-tab-inactive')!=-1)){
      cssRules[i].style.border="1px solid #"+color;
    } else if (cssName.indexOf('labkey-site-nav-panel')!=-1){
      cssRules[i].style.borderTop="1px solid #"+color;
      cssRules[i].style.borderRight="1px solid #"+color;
    } else if (cssName.indexOf('labkey-expandable-nav')!=-1){
      cssRules[i].style.borderTop="1px solid #"+color;
      cssRules[i].style.borderRight="1px solid #"+color;
      cssRules[i].style.borderBottom="1px solid #"+color;
    } else if (cssName.indexOf('labkey-expandable-nav-body')!=-1){
      cssRules[i].style.borderTop="1px solid #"+color;
    } else if (cssName.indexOf('labkey-tab-selected')!=-1){
      cssRules[i].style.borderTop="1px solid #"+color;
      cssRules[i].style.borderRight="1px solid #"+color;
      cssRules[i].style.borderLeft="1px solid #"+color;
    } 

  }

  var panel=document.getElementById("leftmenupanel");

  //updateSrc("saveButton", "border", "%23" + color); just for testing
  updateSrc("navPortal", "border", "%23" + color)
  updateSrc("navAdmin", "border", "%23" + color)
}

function updateSrc(id, key, value)
{
    var el = document.getElementById(id);
    if (el)
    {
      var parts = el.src.split("?");
      var path = parts[0];
      var query = parts.length > 1 ? parts[1] : "";
      var queryObj = parseQuery(query);
      queryObj[key] = value;
      query = formatQuery(queryObj);
      el.src=path + "?" + query;
    }
}

function parseQuery(query)
{
    var o = {};
    var params=query.split("&");
    for (var i=0 ; i<params.length ; i++)
    {
        var s = params[i];
        var kv = s.split("=");
        var k = kv[0];
        var v = kv.length > 1 ? kv[1] : "";
        o[k] = v;
    }
    return o;
}

function formatQuery(o)
{
    var query = "";
    var and = "";
    for (var k in o)
    {
        if (o[k])
            query += and + k + "=" + o[k];
        else
            query += k;
        and = "&";
    }
    return query;
}


function updateGridColor()
{
    var color=document.getElementsByName("gridColor")[0].value;
    if (!isValidColor(color)) return;

    var i;
    var cssRules=getCssRules();
    for ( i = 0; i < cssRules.length; i++ )
    {
        // theme.getEditFormColor()
        var cssName=cssRules[i].selectorText.toLowerCase();
        if (cssName.indexOf('.labkey-form-label')!=-1) {
            cssRules[i].style.backgroundColor="#"+color;
        }
    }
}

function updatePrimaryBackgroundColor()
{
    var color=document.getElementsByName("primaryBackgroundColor")[0].value;
    if (!isValidColor(color)) return;

    var i;
    var cssRules=getCssRules();
    for ( i = 0; i < cssRules.length; i++ )
    {
        //theme.getFullScreenBorderColor()
        var cssName=cssRules[i].selectorText.toLowerCase();
        if (cssName.indexOf('labkey-full-screen-background')!=-1) {
            cssRules[i].style.backgroundColor="#"+color;
        }
    }
}

function updateSecondaryBackgroundColor()
{
    var backgroundColor=document.getElementsByName("secondaryBackgroundColor")[0].value;
    if (!isValidColor(backgroundColor)) return;
    var borderColor=document.getElementsByName("secondaryBackgroundColor")[0].value;
    if (!isValidColor(borderColor)) return;

    var i;
    var cssRules=getCssRules();
    for ( i = 0; i < cssRules.length; i++ )
    {
        var cssName=cssRules[i].selectorText.toLowerCase();
        if (cssName.indexOf('labkey-wp-header') != -1)
        {
            cssRules[i].style.backgroundColor = "#" + backgroundColor;
        } else if (cssName.indexOf('labkey-wp-title-left') != -1)
        {
            cssRules[i].style.borderTop = "1px solid #" + borderColor;
            cssRules[i].style.borderBottom = "1px solid #" + borderColor;
            cssRules[i].style.borderLeft = "1px solid #" + borderColor;
        } else if (cssName.indexOf('labkey-wp-title-right') != -1)
        {
            cssRules[i].style.borderTop = "1px solid #" + borderColor;
            cssRules[i].style.borderBottom = "1px solid #" + borderColor;
            cssRules[i].style.borderRight = "1px solid #" + borderColor;
        }
    }
}

function updateBorderTitleColor()
{

}

function updateWebPartColor()
{

}

function updateAll()
{
    updateLinkColor();
    updateTextColor();
    updateGridColor();
    updatePrimaryBackgroundColor();
    updateSecondaryBackgroundColor();
    updateBorderTitleColor();
    updateWebPartColor();
}

</script>

<form name="themeForm" action="saveWebTheme.view" enctype="multipart/form-data" method="post">
<input type="hidden" name="upgradeInProgress" value="<%=bean.form.isUpgradeInProgress()%>" />
<table width="100%">
<%
String webThemeErrors = formatMissedErrors("form");
if (null != webThemeErrors)
{
%>
<tr><td colspan=3><%=webThemeErrors%></td></tr>
<%
}
%>

<tr>
<td valign="top">

<!-- web theme definition -->

<table>
<%
if (null == webThemeErrors)
{
%><tr><td colspan=2>&nbsp;</td></tr><%
    }
    boolean isBuiltInTheme;
    if (bean.selectedTheme != null)
    {
        isBuiltInTheme = (bean.selectedTheme.getFriendlyName().compareToIgnoreCase("Seattle") == 0
                || bean.selectedTheme.getFriendlyName().compareToIgnoreCase("Brown") == 0);
    }
    else
        isBuiltInTheme = false;

    String disabled = isBuiltInTheme ? "disabled" : "";

    String helpLink = (new HelpTopic("customizeTheme")).getHelpTopicLink();
%>
<tr>
    <td colspan=2>Choose an existing web theme or define a new one. (<a href="<%=helpLink%>" target="_new">examples...</a>)</td>
</tr>
<tr><td colspan=3 class="labkey-title-area-line"></td></tr>
<tr>
    <td class="labkey-form-label">Web site theme (color scheme)</td>
    <td>
        <select name="themeName" onchange="changeTheme(this)">
            <option value="">&lt;New Theme&gt;</option>
            <%
              boolean themeFound = false;
              for (WebTheme theme : bean.themes)
                {
                    if (theme == bean.selectedTheme)
                        themeFound = true;
                    String selected = (theme == bean.selectedTheme ? "selected" : "");
                    %>
                    <option value="<%=h(theme.toString())%>" <%=selected%>><%=h(theme.getFriendlyName())%></option>
                <%}
            %>
        </select>
    </td>
</tr>
<%if (!themeFound)
{%>
<tr>
    <td class="labkey-form-label">Theme Name</td>
    <td><input type="text" name="friendlyName" size="16" maxlength="16" value="<%=((null != selectedTheme) ? selectedTheme.getFriendlyName() : StringUtils.trimToEmpty(bean.form.getFriendlyName()))%>"></td>
</tr>
<%}%>
<tr>
    <td class="labkey-form-label">Link Color</td>
    <td>
        <input type="text" name="linkColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getLinkColor() : StringUtils.trimToEmpty(bean.form.getLinkColor()))%>" <%=disabled%> onfocus="updateLinkColor()" onblur="updateLinkColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('linkColor')[0])"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Text Color</td>
    <td>
        <input type="text" name="textColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getTextColor() : StringUtils.trimToEmpty(bean.form.getTextColor()))%>" <%=disabled%> onfocus="updateTextColor()" onblur="updateTextColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('textColor')[0])"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Grid Color</td>
    <td>
        <input type="text" name="gridColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getGridColor() : StringUtils.trimToEmpty(bean.form.getGridColor()))%>" <%=disabled%> onfocus="updateGridColor()" onblur="updateGridColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('gridColor')[0])"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Primary Background Color</td>
    <td>
        <input type="text" name="primaryBackgroundColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getPrimaryBackgroundColor() : StringUtils.trimToEmpty(bean.form.getPrimaryBackgroundColor()))%>" <%=disabled%> onfocus="updatePrimaryBackgroundColor()" onblur="updatePrimaryBackgroundColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('primaryBackgroundColor')[0])"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Secondary Background Color</td>
    <td>
        <input type="text" name="secondaryBackgroundColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getSecondaryBackgroundColor() : StringUtils.trimToEmpty(bean.form.getSecondaryBackgroundColor()))%>" <%=disabled%> onfocus="updateSecondaryBackgroundColor()" onblur="updateSecondaryBackgroundColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('secondaryBackgroundColor')[0])"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Border & Title Color</td>
    <td>
        <input type="text" name="borderTitleColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getBorderTitleColor() : StringUtils.trimToEmpty(bean.form.getBorderTitleColor()))%>" <%=disabled%> onfocus="updateBorderTitleColor()" onblur="updateBorderTitleColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('borderTitleColor')[0])"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">WebPart Color</td>
    <td>
        <input type="text" name="webpartColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getWebPartColor() : StringUtils.trimToEmpty(bean.form.getWebpartColor()))%>" <%=disabled%> onfocus="updateWebPartColor()" onblur="updateWebPartColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('webpartColor')[0])"<%}%>>
    </td>
</tr>
<tr>
    <td colspan="2">&nbsp;</td>
</tr>

<tr>
    <td colspan="2">
        <%
        if (!isBuiltInTheme)
        {%>
        <%=PageFlowUtil.generateSubmitButton("Save", "", "id=\"saveButton\" name=\"Define\"")%>&nbsp;
            <%
            if (selectedTheme != null && bean.themes.size() > 1)
            {%>
                <%=PageFlowUtil.generateSubmitButton("Delete", "var sure = confirm('Are you sure you want to delete the theme named " + request.getParameter("themeName") + "?'); if (sure) document.themeForm.action = 'deleteWebTheme.view'; return sure;", "name=\"Delete\"")%>&nbsp;
            <%}
        }
        else
            {%>
            <%=generateButton("Done", urlProvider(AdminUrls.class).getProjectSettingsURL(HttpView.currentContext().getContainer()))%>
           <%}%>
    </td>
</tr>
</table>

</td>

<td>&nbsp;&nbsp;</td>

<td>
<!-- start of dialog preview -->

<table class="labkey-wp">
<tr class="labkey-wp-header">
  <th class="labkey-wp-title-left" title="Full Screen Border Preview">Full Screen Border Preview</th>
  <th class="labkey-wp-title-right"><a href="javascript:;"><img src="<%=request.getContextPath()%>/_images/partedit.gif" title="Customize Web Part"></a>&nbsp;<img src="<%=request.getContextPath()%>/_images/partupg.gif" title="">&nbsp;<img src="<%=request.getContextPath()%>/_images/partdowng.gif" title="">&nbsp;<a href="javascript:;"><img src="<%=request.getContextPath()%>/_images/partdelete.gif" title="Remove From Page"></a></th>
</tr>

<tr>
  <td colspan="2">&nbsp;</td>
</tr>

<tr>
  <td colspan="2" class="labkey-full-screen-background">
    <div class="labkey-full-screen-table">
      <div id="dialogBody" class="labkey-dialog-body">
          Changes to full-screen color preferences will be displayed here.<br>
      </div>
    </div>
  </td>
</tr>
</table>
<!-- end of dialog preview -->
</td>

</tr>

<tr>
<td colspan=3>
<%
if (!themeFound)
{%>
New themes will not be visible to other users until you save changes on the Look and Feel Settings page.
<%}%>
</td>
</tr>

</table>

</form>
<script type="text/javascript">
function changeTheme(sel)
{
    var search = document.location.search;
    if (search.indexOf("?") == 0)
    {
        search = search.substring(1);
    }
    var params = search.split('&');
    var searchNew = "";
    for (var i = 0; i < params.length; i++)
    {
        if (params[i].indexOf("themeName=") != 0)
        {
            if (searchNew != "")
                searchNew += "&";
            searchNew += params[i];
        }
    }
    var opt = sel.options[sel.selectedIndex];
    if (opt.text.indexOf("<") != 0)
        if (searchNew.length == 0)
        {
            searchNew = "themeName=" + escape(opt.text);
        }
        else
        {
            searchNew += "&themeName=" + escape(opt.text);
        }
    document.location.search = searchNew;
}

</script>
<script type="text/javascript">
    Ext.onReady(function() {
        try {document.getElementByName("themeName").focus();} catch(x){}
        updateAll();
    });
</script>
