/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.Button;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A menu which is not fully rendered/visible at original page render time, but instead
 * is shown in response to show user action, like clicking on a button.
 *
 * User: Mark Igra
 * Date: May 13, 2008
 */
public class PopupMenu extends DisplayElement
{
    // a menu that appears on the page only once, can use id's,
    // however a menu that can appear on the page multiple times
    // should not use id's
    private boolean _singletonMenu = false;
    private NavTree _navTree;
    private Align _align = Align.LEFT;
    private ButtonStyle _buttonStyle = ButtonStyle.MENUBUTTON;
    private String _imageId = "";
    private String _offset = "-1";
    private String _safeID = "lk-menu-" + UniqueID.getServerSessionScopedUID();

    public PopupMenu()
    {
        this(new NavTree());
    }

    public PopupMenu(NavTree navTree)
    {
        _navTree = navTree;
    }

    public PopupMenu(NavTree navTree, Align align, ButtonStyle buttonStyle)
    {
        _navTree = navTree;
        _align = align;
        _buttonStyle = buttonStyle;
    }

    public void setOffset(String offset)
    {
        _offset = offset;
    }
    
    public NavTree getNavTree()
    {
        return _navTree;
    }

    public void setNavTree(NavTree navTree)
    {
        _navTree = navTree;
    }

    public void setImageId(String imageId)
    {
        _imageId = imageId;
    }

    public String getImageId()
    {
        return _imageId;
    }

    public String getSafeID()
    {
        return _safeID;
    }

    public void setIsSingletonMenu(boolean singletonMenu)
    {
        // basically indicates that is OK to render id's on menu items (useful for testing)
        _singletonMenu = singletonMenu;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        render(out);
    }

    public void render(Writer out) throws IOException
    {
        renderMenuButton(out);
        renderMenuScript(out);
    }

    public void renderMenuButton(Writer out) throws IOException
    {
        renderMenuButton(null, out, false, null);
    }

    public void renderMenuButton(@Nullable RenderContext ctx, Writer out, boolean requiresSelection, @Nullable ActionButton button) throws IOException
    {
        boolean usingNewUI = PageFlowUtil.useExperimentalCoreUI();

        if (null == _navTree.getText())
            return;

        if (_singletonMenu && StringUtils.isNotEmpty(_navTree.getId()))
            _safeID = _navTree.getId();

        Map<String, String> attributes = new HashMap<>();
        String onClickScript = null;

        if (usingNewUI)
        {
            out.append("<span class=\"lk-menu-drop dropdown\" >");
            attributes.put("data-toggle", "dropdown");
        }
        else
        {
            onClickScript = "showMenu(this, " + PageFlowUtil.qh(_safeID) + ",'" + _align.getExtPosition() + "');";
            attributes.put("lk-menu-id", _safeID);
        }

        String dataRegionName = null;

        if (ctx != null && ctx.getCurrentRegion() != null)
            dataRegionName = ctx.getCurrentRegion().getName();

        if (_buttonStyle == ButtonStyle.TEXTBUTTON)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            out.append(PageFlowUtil.textLink(_navTree.getText(), "javascript:void(0)", onClickScript, "", attributes));
        }
        else if (_buttonStyle == ButtonStyle.MENUBUTTON)
        {
            if (requiresSelection)
                attributes.put("labkey-requires-selection", dataRegionName);

            Button.ButtonBuilder bldr = PageFlowUtil.button(_navTree.getText())
                    .dropdown(true)
                    .href("javascript:void(0)")
                    .onClick(onClickScript)
                    .attributes(attributes);

            if (button != null)
            {
                // set additional properties from the button
                bldr.iconCls(button.getIconCls());
                bldr.tooltip(button.getTooltip());
            }

            out.append(bldr.toString());
        }
        else if (_buttonStyle == ButtonStyle.TEXT || _buttonStyle == ButtonStyle.BOLDTEXT)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            out.append(PageFlowUtil.generateDropDownTextLink(_navTree.getText(), "javascript:void(0)",
                    onClickScript, _buttonStyle == ButtonStyle.BOLDTEXT, _offset, _navTree.getId()));
        }
        else if (_buttonStyle == ButtonStyle.IMAGE)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            if (_navTree.getImageCls() != null && _navTree.getImageCls().length() > 0)
            {
                out.append(PageFlowUtil.generateDropDownFontIconImage(_navTree.getText(), "javascript:void(0)",
                        onClickScript, _navTree.getImageCls(), _imageId));
            }
            else
            {
                assert _navTree.getImageSrc() != null && _navTree.getImageSrc().length() > 0 : "Must provide an image source or image cls for image based popups.";
                out.append(PageFlowUtil.generateDropDownImage(_navTree.getText(), "javascript:void(0)",
                        onClickScript, _navTree.getImageSrc(), _imageId, _navTree.getImageHeight(), _navTree.getImageWidth()));
            }
        }

        if (usingNewUI)
        {
            out.append("<ul class=\"dropdown-menu dropdown-menu-left \" >");
            try
            {
                PopupMenuView.renderTree(_navTree, out);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            out.append("</ul>");
            out.append("</span>");
        }
    }


    public void renderMenuScript(Writer out) throws IOException
    {
        out.append("<script type=\"text/javascript\">\n");
        renderExtMenu(out);
        out.append("\n</script>");
    }

    private void renderExtMenu(Writer out) throws IOException
    {
        out.append("if (typeof(Ext4) != 'undefined') { Ext4.onReady(function() {");
        out.append(renderUnregScript());
        out.append(" var m = Ext4.create('Ext.menu.Menu',");
        out.append(renderMenuModel(_navTree.getChildList(), _safeID, _singletonMenu));
        out.append("); }); } else { console.error('Unable to render menu. Ext4 is not available.'); }");
    }

    private String renderUnregScript()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("    var oldMenu = Ext4.menu.Manager.get(");
        sb.append(PageFlowUtil.qh(_safeID));
        sb.append(");\n");
        sb.append("    if(oldMenu)\n");
        sb.append("    {\n");
        sb.append("        oldMenu.removeAll();\n");
        sb.append("        Ext4.menu.Manager.unregister(oldMenu);\n");
        sb.append("    }\n");
        return sb.toString();
    }

    private static String renderMenuModel(Collection<NavTree> trees, String id, boolean singletonMenu)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("{showSeparator: false, ");
        sb.append("id:").append(PageFlowUtil.qh(id)).append(",\n");
        sb.append("items:");
        boolean useIds = singletonMenu;
        NavTree.toJS(trees, sb, true, useIds);
        sb.append("}");

        return sb.toString();
    }

    public Align getAlign()
    {
        return _align;
    }

    public void setAlign(Align align)
    {
        _align = align;
    }

    public ButtonStyle getButtonStyle()
    {
        return _buttonStyle;
    }

    public void setButtonStyle(ButtonStyle buttonStyle)
    {
        _buttonStyle = buttonStyle;
    }

    public String getId(String dataRegionName)
    {
        if (null != StringUtils.trimToNull(_navTree.getId()))
        {
            return _navTree.getId();
        }
        if (dataRegionName != null)
        {
            return dataRegionName + ".Menu." + _navTree.getText();
        }
        return String.valueOf(System.identityHashCode(this));
    }

    public enum Align
    {
        LEFT("tl-bl?"),
        RIGHT("tr-br?");

        String extPosition;
        Align(String position)
        {
            extPosition = position;
        }

        public String getExtPosition()
        {
            return extPosition;
        }
    }

    public enum ButtonStyle
    {
        MENUBUTTON,
        BOLDTEXT,
        TEXT,
        TEXTBUTTON,
        IMAGE
    }
}
