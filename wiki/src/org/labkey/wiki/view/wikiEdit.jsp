<%
/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.HString" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.wiki.WikiController.DownloadAction" %>
<%@ page import="org.labkey.wiki.model.WikiEditModel" %>
<%@ page import="org.labkey.wiki.model.WikiTree" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<WikiEditModel> me = (JspView<WikiEditModel>) HttpView.currentView();
    WikiEditModel model = me.getModelBean();
    final String ID_PREFIX = "wiki-input-";
    String sep;
    String saveButtonCaption = "Save";
%>

<script type="text/javascript">
    LABKEY.requiresScript('tiny_mce/tiny_mce.js');

</script>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>

<script type="text/javascript">
    //page-level variables defined by the server
    var _idPrefix = <%=PageFlowUtil.jsString(ID_PREFIX)%>;
    var _wikiProps = {
        entityId: <%=model.getEntityId()%>,
        rowId: <%=model.getRowId()%>,
        name: <%=model.getName()%>,
        title: <%=model.getTitle()%>,
        body: <%=model.getBody()%>,
        parent: <%=model.getParent()%>,
        rendererType: <%=model.getRendererType()%>,
        pageId: <%=model.getPageId()%>,
        index: <%=model.getIndex()%>,
        showAttachments: <%=model.isShowAttachments()%>,
        isDirty: false
    };
    var _editableProps = ['name', 'title', 'body', 'parent', 'showAttachments'];
    var _attachments = [
        <%
        if (model.hasAttachments())
        {
            sep = "";
            for (Attachment att : model.getWiki().getAttachments())
            {
                %>
                    <%=sep%>{name: <%=PageFlowUtil.jsString(att.getName())%>,
                             iconUrl: <%=PageFlowUtil.jsString(me.getViewContext().getContextPath() + att.getFileIcon())%>,
                             downloadUrl: <%=PageFlowUtil.jsString(att.getDownloadUrl(DownloadAction.class).toString())%>
                            }
                <%
                sep = ",";
            }
        }
        %>
    ];
    var _formats = {
        <%
            sep = "";
            for (WikiRendererType format : WikiRendererType.values())
            {
        %>
            <%=sep%>
            <%=format.name()%>: <%=PageFlowUtil.jsString(format.getDisplayName())%>
        <%
                sep = ",";
            }
        %>
    };
    var _useVisualEditor = <%=model.useVisualEditor()%>;
    var _redirUrl = <%=model.getRedir()%>;
    var _cancelUrl = <%=model.getCancelRedir()%>;
</script>
<script type="text/javascript">
    LABKEY.requiresScript("wiki/js/wikiEdit.js", true);
</script>

<div id="status" class="labkey-status-info" style="display:none;" width="99%">(status)</div>

<table width=99%;>
    <tr>
        <td width="50%" align="left"  nowrap="true">
            <%=PageFlowUtil.generateSubmitButton("Save & Close", "onFinish();", "id='wiki-button-finish'")%>
            <%=PageFlowUtil.generateSubmitButton(saveButtonCaption, "onSave();", "id='wiki-button-save'")%>
            <%=PageFlowUtil.generateSubmitButton("Cancel", "onCancel();")%>
        </td>
        <td width="50%" align="right" nowrap="true">
            <% if (model.canUserDelete()) { %>
                <%=PageFlowUtil.generateDisabledSubmitButton("Delete Page", "return false;", "id=\"" + ID_PREFIX + "button-delete\"")%>
            <% } %>
            <%=PageFlowUtil.generateSubmitButton("Convert To...", "showConvertWindow()", "id=\"" + ID_PREFIX + "button-change-format\"")%>
            <%=PageFlowUtil.generateSubmitButton("Show Page Tree", "showHideToc()", "id=\"" + ID_PREFIX + "button-toc\"")%>
        </td>
    </tr>
</table>
<table width="99%">
    <tr>
        <td width="99%" style="vertical-align:top;">
            <table width="99%">
                <tr>
                    <td class="labkey-form-label" nowrap="true">Name * <%= PageFlowUtil.helpPopup("Name", "This field is required") %></td>
                    <td width="99%">
                        <input type="text" name="name" id="<%=ID_PREFIX%>name" size="80" onkeypress="setWikiDirty()" onchange="onChangeName()"/>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label">Title</td>
                    <td width="99%">
                        <input type="text" name="title" id="<%=ID_PREFIX%>title" size="80" onkeypress="setWikiDirty()" onchange="setWikiDirty()"/>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label">Parent</td>
                    <td width="99%">
                        <select name="parent" id="<%=ID_PREFIX%>parent" onkeypress="setWikiDirty()" onchange="setWikiDirty()">
                            <option <%= model.getParent() == -1 ? "selected='1'" : "" %> value="-1">[none]</option>
                            <%
                                for (WikiTree possibleParent : model.getPossibleParents())
                                {
                                    String indent = "";
                                    int depth = possibleParent.getDepth();
                                    HString parentTitle = possibleParent.getTitle();
                                    while (depth-- > 0)
                                        indent = indent + "&nbsp;&nbsp;";
                                    %><option <%= possibleParent.getRowId() == model.getParent() ? "selected" : "" %> value="<%= possibleParent.getRowId() %>"><%= indent %><%= h(parentTitle) %> (<%= possibleParent.getName() %>)</option><%
                                }
                            %>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label">Body
                        <br/><span id="wiki-current-format"></span>
                    </td>
                    <td width="99%">
                        <div>
                          <!--
                             start the wiki-tab-strip out hidden, so that it does not appear and then disappear
                             when wiki editor loads and hides it with javascript.
                          -->
                            <ul id="wiki-tab-strip" class="labkey-tab-strip" style="display: none;">
                                <li id="wiki-tab-visual" class="labkey-tab-active"><a href="#" onclick="userSwitchToVisual()">Visual</a></li>
                                <li id="wiki-tab-source" class="labkey-tab-inactive"><a href="#" onclick="userSwitchToSource()">Source</a></li>
                                <div class="x-clear"></div>
                            </ul>
                            <div class="labkey-tab-strip-spacer"></div>
                            <div id="wiki-tab-content" class="labkey-tab-strip-content" style="padding: 0;">
                                <form action="">
                                <textarea rows="30" cols="80" style="width:100%; border:none;" id="<%=ID_PREFIX%>body"
                                          name="body" onkeypress="setWikiDirty()" onchange="setWikiDirty()"></textarea>
                                    <script type="text/javascript">
                                        Ext.EventManager.on('<%=ID_PREFIX%>body', 'keydown', handleTabsInTextArea);
                                    </script>
                                </form>
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label">Files</td>
                    <td width="99%">
                        <table>
                            <tr>
                                <td><input type="checkbox" id="<%=ID_PREFIX%>showAttachments" onchange="setWikiDirty();" onclick="setWikiDirty();"/>
                                    Show Attached Files</td>
                            </tr>
                        </table>
                        <form action="attachFiles.post" method="POST" enctype="multipart/form-data" id="form-files">
                            <table id="wiki-existing-attachments">
                            </table>
                            <table id="wiki-new-attachments">
                            </table>
                        </form>
                    </td>
                </tr>
            </table>
            <div id="wiki-help-HTML-visual" style="display:none">
                <table>
                    <tr>
                        <td colspan=2><b>HTML Formatting Guide:</b></td>
                    </tr>
                    <tr>
                        <td>Link to a wiki page</td>
                        <td>Select text and right click. Then select "Insert/edit link."
                         Type the name of the wiki page in "Link URL" textbox.</td>
                    </tr>
                    <tr>
                        <td>Link to an attachment</td>
                        <td>Select text and right click. Then select "Insert/edit link."
                         Type the name of the attachment with the file extension in "Link URL" textbox.</td>
                    </tr>
                </table>
            </div>
            <div id="wiki-help-HTML-source" style="display:none">
                <table>
                    <tr>
                        <td colspan=2><b>HTML Source Formatting Guide:</b></td>
                    </tr>
                    <tr>
                        <td>Link to a wiki page</td>
                        <td>&lt;a href="pageName"&gt;My Page&lt;/a&gt;</td>
                    </tr>
                    <tr>
                        <td>Link to an attachment</td>
                        <td>&lt;a href="attachment.doc"&gt;My Document&lt;/a&gt;</td>
                    </tr>
                    <tr>
                        <td>Show an attached image</td>
                        <td>&lt;img src="imageName.jpg"&gt;</td>
                    </tr>
                </table>
            </div>

            <div id="wiki-help-RADEOX-source" style="display:none">
                <table>
                    <tr>
                        <td colspan=2><b>Wiki Formatting Guide</b> (<a target="_new" href="<%=(new HelpTopic("wikiSyntax" )).getHelpTopicLink()%>">more help</a>):</td>
                    </tr>
                    <tr>
                        <td>link to page in this wiki&nbsp;&nbsp;</td>
                        <td>[pagename] or [Display text|pagename]</td>
                    </tr>
                    <tr>
                        <td>external link</td>
                        <td>http://www.google.com or {link:Display text|http://www.google.com}</td>
                    </tr>
                    <tr>
                        <td>picture</td>
                        <td>[attach.jpg] or {image:http://www.website.com/somepic.jpg}</td>
                    </tr>
                    <tr>
                        <td>bold</td>
                        <td>**like this**</td>
                    </tr>
                    <tr>
                        <td>italics</td>
                        <td>~~like this~~</td>
                    </tr>
                    <tr>
                        <td>bulleted list</td>
                        <td>- list item</td>
                    </tr>
                    <tr>
                        <td>numbered List</td>
                        <td>1. list item</td>
                    </tr>
                    <tr>
                        <td>line break (&lt;br&gt;)</td>
                        <td>\\</td>
                    </tr>
                </table>
            </div>
            <div id="wiki-help-TEXT_WITH_LINKS-source" style="display:none">
                <table>
                    <tr>
                        <td><b>Plain Text Formatting Guide:</b></td>
                    </tr>
                    <tr>
                        <td>In plain text format, web addresses (http://www.labkey.com) will be automatically converted
                        into active hyperlinks when the page is shown, but all other text will appear as typed.</td>
                    </tr>
                </table>
            </div>
        </td>
        <td width="1%" style="vertical-align:top;">
            <div id="wiki-toc-tree" class="extContainer" style="display:none"/>
        </td>
    </tr>
</table>
<div id="<%=ID_PREFIX%>window-change-format" class="x-hidden">
    <table>
        <tr>
            <td>
                <span class="labkey-error">WARNING:</span>
                Changing the format of your page will change the way
                your page is interpreted, causing it to appear at least differently,
                if not incorrectly. In most cases, manual adjustment to the
                page content will be necessary. You should not perform this
                operation unless you know what you are doing.
            </td>
        </tr>
        <tr>
            <td>
                Convert page format from
                <b id="<%=ID_PREFIX%>window-change-format-from">(from)</b>
                to
                <select id="<%=ID_PREFIX%>window-change-format-to">
                </select>
            </td>
        </tr>
        <tr>
            <td style="text-align: right">
                <%=PageFlowUtil.generateSubmitButton("Convert", "convertFormat()")%>
                <%=PageFlowUtil.generateSubmitButton("Cancel", "cancelConvertFormat()")%>
            </td>
        </tr>
    </table>
</div>
