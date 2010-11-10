/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.data.RenderContext;
import org.apache.commons.lang.StringUtils;

import java.io.Writer;
import java.io.IOException;

/**
 * User: Mark Igra
 * Date: May 13, 2008
 * Time: 3:30:25 PM
 */
public class PopupMenu extends DisplayElement
{
    private NavTree _navTree;
    private Align _align = Align.LEFT;
    private ButtonStyle _buttonStyle = ButtonStyle.MENUBUTTON;

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

    public NavTree getNavTree()
    {
        return _navTree;
    }

    public void setNavTree(NavTree navTree)
    {
        _navTree = navTree;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        render(out);
    }

    public void render(Writer out) throws IOException
    {
        renderMenuButton(out);
        renderMenuScript(out, null);
    }

    public void renderMenuButton(Writer out) throws IOException
    {
        renderMenuButton(out, null, false);
    }

    public void renderMenuButton(Writer out, String dataRegionName, boolean requiresSelection) throws IOException
    {
        if (null == _navTree.getKey())
            return;

        if (_buttonStyle == ButtonStyle.TEXTBUTTON)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            String link = PageFlowUtil.textLink(_navTree.getKey().concat(" &gt;&gt;"), "javascript:void(0)", "showMenu(this, " + PageFlowUtil.jsString(getId(dataRegionName)) + ",'" + _align.getExtPosition() + "');", "");
            link = link.replace("class='", "class='no-arrow "); // Remove the arrow added by css
            out.append(link);
        }
        else if (_buttonStyle == ButtonStyle.MENUBUTTON)
        {
            String attributes = null;
            if (requiresSelection)
                attributes = "labkey-requires-selection=\"" + PageFlowUtil.filter(dataRegionName) + "\"";
            out.append(PageFlowUtil.generateDropDownButton(_navTree.getKey(), "javascript:void(0)",
                    "showMenu(this, " + PageFlowUtil.jsString(getId(dataRegionName)) + ",'" + _align.getExtPosition() + "');", attributes));
        }
        else if (_buttonStyle == ButtonStyle.TEXT || _buttonStyle == ButtonStyle.BOLDTEXT)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            out.append(PageFlowUtil.generateDropDownTextLink(_navTree.getKey(), "javascript:void(0)",
                    "showMenu(this, " + PageFlowUtil.jsString(getId(dataRegionName)) + ",'" + _align.getExtPosition() + "');", _buttonStyle == ButtonStyle.BOLDTEXT));
        }
    }

    public void renderMenuScript(Writer out, String dataRegionName) throws IOException
    {
        out.append("<script type=\"text/javascript\">\n");
        out.append("Ext.onReady(function() {\n");
        out.append(renderUnregScript(getId(dataRegionName)));
        out.append("        new Ext.menu.Menu(");
        out.append(renderMenuModel(_navTree.getChildren(), getId(dataRegionName)));
        out.append("         );});\n</script>");
    }

    private String renderUnregScript(String id)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("    var oldMenu = Ext.menu.MenuMgr.get(");
        sb.append(PageFlowUtil.jsString(id));
        sb.append(");\n");
        sb.append("    if(oldMenu)\n");
        sb.append("    {\n");
        sb.append("        oldMenu.removeAll();\n");
        sb.append("        Ext.menu.MenuMgr.unregister(oldMenu);\n");
        sb.append("    }\n");
        return sb.toString();
    }

    // UNDONE: use NavTree.toJS()
    private String renderMenuModel(NavTree[] trees, String id)
    {
        String sep = "";
        StringBuilder sb = new StringBuilder();

        sb.append("{cls:'extContainer',");
        sb.append("id:").append(PageFlowUtil.qh(id)).append(",\n");
        sb.append("items:[");
        for (NavTree tree : trees)
        {
            sb.append(sep);
            if (tree == NavTree.MENU_SEPARATOR)
            {
                sb.append("'-'");
                sep = ",";
                continue;
            }

            sb.append("{").append("text:").append(PageFlowUtil.qh(tree.getKey()));
            if (tree.isStrong() || tree.isEmphasis())
            {
                sb.append(", cls:'");
                if (tree.isStrong())
                    sb.append("labkey-strong");
                if (tree.isEmphasis())
                    sb.append(" labkey-emphasis");
                sb.append("'");
            }
            if (StringUtils.isNotEmpty(tree.getId()))
                sb.append(", id:").append(PageFlowUtil.qh(tree.getId()));
            if (StringUtils.isNotEmpty(tree.getDescription()))
                sb.append(", tooltip: ").append(PageFlowUtil.qh(tree.getDescription()));
            if (tree.isSelected())
                sb.append(", checked:true");
            if (null != tree.getImageSrc())
                sb.append(", icon:").append(PageFlowUtil.qh(tree.getImageSrc()));
            if (tree.isDisabled())
                sb.append(", disabled:true");
            if (null != tree.getValue())
                sb.append(",").append("href:").append(PageFlowUtil.qh(tree.getValue()));
            if (null != tree.getScript())
                sb.append(", handler:function(){").append(tree.getScript()).append("}");
            if (null != tree.getChildren() && tree.getChildren().length > 0)
            {
                sb.append(", hideOnClick:false");
                sb.append(",\n menu:").append(renderMenuModel(tree.getChildren(), null)).append("\n");
            }
            sb.append("}\n");
            sep = ",";
        }
        sb.append("]}");

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
            return dataRegionName + ".Menu." + _navTree.getKey();
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
        TEXTBUTTON
    }
}
