/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.HString;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.*;


/**
 * NavTree can be used three ways in different places in the product
 *
 * 1) as a single navigation element (no children)
 * 2) as a list of navigation elements (ignore the root node, use children as list of elements)
 * 3) as a tree, may be rendered as an tree, or menu
 */

public class NavTree extends Pair<String, String> implements Collapsible
{
    public static final NavTree MENU_SEPARATOR = new NavTree("-");

    static final List<NavTree> EMPTY_LIST = Collections.emptyList();

    String imageSrc = null;
    Integer imageHeight;
    Integer imageWidth;
    
    private boolean _selected = false;
    private boolean _collapsed = false;
    private boolean _canCollapse = true;
    private boolean _strong = false;
    private boolean _emphasis = false;
    private String _script;
    private String _id = "";
    private boolean _disabled;
    private String _display;
    private String _description;

    private ArrayList<NavTree> children = null;

    public static String escapeKey(String key)
    {
        return key == null ? null : key.replaceAll("%","%25").replaceAll("/","%2F");
    }

    public NavTree()
    {
        super(null, null);
    }


    public NavTree(String display, String href, boolean collapsed)
    {
        super(display, href);
        _collapsed = collapsed;
    }

    public NavTree(String display, String href)
    {
        this(display, href, false);
    }

    public NavTree(String display, String href, String imageSrc)
    {
        this(display, href, false);
        this.imageSrc = imageSrc;
    }


    public NavTree(String display, URLHelper urlhelp)
    {
        this(display, urlhelp, false);
    }


    public NavTree(String display, URLHelper urlhelp, boolean collapsed)
    {
        this(display, urlhelp != null ? urlhelp.getLocalURIString() : null, collapsed);
    }


    public NavTree(String display)
    {
        this(display, (String) null);
    }

    /**
     * Creates a new NavTree instance which is a deep copy of the source NavTree
     * @param source The source NavTree
     */
    public NavTree(NavTree source)
    {
        super(source.first, source.second);
        imageSrc = source.imageSrc;
        _selected = source._selected;
        _collapsed = source._collapsed;
        _canCollapse = source._canCollapse;
        _strong = source._strong;
        _emphasis = source._emphasis;
        _script = source._script;
        _id = source._id;
        _disabled = source._disabled;
        imageHeight = source.imageHeight;
        imageWidth = source.imageWidth;

        children = new ArrayList<NavTree>();
        if (source.children != null)
        {
            for (NavTree child : source.children)
            {
                children.add(new NavTree(child));
            }
        }
    }


    public String getEscapedKey()
    {
        return escapeKey(getKey());
    }


    public void addSeparator()
    {
        addChild(MENU_SEPARATOR);
    }

    public NavTree addChild(NavTree child)
    {
        if (null == children)
            children = new ArrayList<NavTree>();

        if (null != child.first)
            children.add(child);
        else if (null != child.children)
            addChildren(child.children);
        return child;
    }

    public NavTree addChild(int pos, NavTree child)
    {
        if (null == children)
            children = new ArrayList<NavTree>();
        children.add(pos, child);
        return child;
    }

    public NavTree addChild(HString display)
    {
        return addChild(display.getSource());
    }

    public NavTree addChild(String display)
    {
        return addChild(new NavTree(display));
    }


    public NavTree addChild(String display, String href)
    {
        return addChild(new NavTree(display, href));
    }


    public NavTree addChild(String display, String href, String imageSrc, int width, int height)
    {
        addChild(new NavTree(display, href, imageSrc));
        return this;
    }

    public NavTree addChild(String display, String href, String imageSrc)
    {
        addChild(new NavTree(display, href, imageSrc));
        return this;
    }

    public NavTree addChild(HString display, URLHelper urlhelp)
    {
        return addChild(display.getSource(), urlhelp);        
    }


    public NavTree addChild(String display, @NotNull URLHelper urlhelp)
    {
        addChild(display, urlhelp.getLocalURIString());
        return this;
    }


    public void addChildren(Collection<NavTree> list)
    {
        if (null == children)
            children = new ArrayList<NavTree>();
        children.addAll(list);
    }


    public void addChildren(NavTree[] list)
    {
        addChildren(Arrays.asList(list));
    }

    public NavTree[] getChildren()
    {
        if (null == children)
            return new NavTree[0];
        return children.toArray(new NavTree[children.size()]);
    }

    @NotNull
    public List<NavTree> getChildList()
    {
        if (null == children)
            //noinspection unchecked
            return EMPTY_LIST;
        return Collections.unmodifiableList(children);
    }

    public boolean hasChildren()
    {
        return null != children && !children.isEmpty();
    }

    // Sort children by name, case-insensitive
    public void sort()
    {
        if (null != children)
        {
            Collections.sort(children, new Comparator<NavTree>()
            {
                public int compare(NavTree a, NavTree b)
                {
                    return a.getKey().compareToIgnoreCase(b.getKey());
                }
            });
        }
    }


    /**
     * Walk path and return folder. If path starts with /, first element
     * of the path is expected to match key of this navTree. If not, first element is expected
     * to be key of a child. If path starts with / and key does NOT match act as though
     * path did not start with / and try the rest on children
     *
     * @param path Path of folders to expand
     * @return Named subtree
     */
    public NavTree findSubtree(@Nullable String path)
    {
        if (null == path || path.length() == 0 || "/".equals(path))
            return this;

        //use the escaped key for path matching, so that embedded / characters are escaped
        String key = getEscapedKey();
        if (key == null) key = "";

        if (path.charAt(0) == '/')
        {
            if (path.substring(1).equals(key))
                return this;
            else if (path.startsWith("/" + key + "/"))
                return findSubtree(path.substring(key.length() + 2));
            else // Maybe try the children as a last resort
                path = path.substring(1);
        }

        //Now expand a child...
        String childKey;
        int slash = path.indexOf("/");
        if (slash < 0)
            childKey = path;
        else
            childKey = path.substring(0, slash);

        for (NavTree childTree : getChildren())
        {
            if (childKey.equals(childTree.getEscapedKey()))
                if (slash < 0)
                    return childTree;
                else
                    return childTree.findSubtree(path.substring(slash + 1));
        }

        return null;
    }

    public int getChildCount()
    {
        return null == children ? 0 : children.size();
    }


    public void setImage(String src, int width, int height)
    {
        imageSrc = src;
        imageWidth = width;
        imageHeight = height;
    }


    public String getImageSrc()
    {
        return imageSrc;
    }

    public void setImageSrc(String imageSrc)
    {
        this.imageSrc = imageSrc;
    }

    public Integer getImageHeight()
    {
        return imageHeight;
    }

    public Integer getImageWidth()
    {
        return imageWidth;
    }

    public boolean isCollapsed()
    {
        return _collapsed;
    }

    public void setCollapsed(boolean collapsed)
    {
        _collapsed = collapsed;
    }

    public boolean getCanCollapse()
    {
        return _canCollapse;
    }

    public void setCanCollapse(boolean canCollapse)
    {
        _canCollapse = canCollapse;
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public void setSelected(boolean s)
    {
        _selected = s;
    }

    public boolean isSelected()
    {
        return _selected;
    }

    public boolean isStrong()
    {
        return _strong;
    }

    public void setStrong(boolean strong)
    {
        _strong = strong;
    }

    public boolean isEmphasis()
    {
        return _emphasis;
    }

    public void setEmphasis(boolean emphasis)
    {
        _emphasis = emphasis;
    }

    public String getScript()
    {
        return _script;
    }

    public void setScript(String script)
    {
        _script = script;
    }

    public boolean isDisabled()
    {
        return _disabled;
    }

    public void setDisabled(boolean disabled)
    {
        _disabled = disabled;
    }

    public String getDisplay()
    {
        return _display;
    }

    public void setDisplay(String display)
    {
        _display = display;
    }

    public void setKey(String key)
    {
        first = key;
    }

    /** Get the description text for this item.  Menu items will render this as a tooltip. */
    public String getDescription()
    {
        return _description;
    }

    /** Set the description text for this item.  Menu items will render this as a tooltip. */
    public void setDescription(String description)
    {
        _description = description;
    }

    public String childrenToJS()
    {
        if (null == children)
            return "[]";
        return toJS(children, new StringBuilder(), false).toString();
    }

    public String toJS()
    {
        return toJS(new StringBuilder(), false).toString();
    }


    public JSONObject toJSON()
    {
        return toJSON(true, "items");
    }

                                                                                
    public JSONObject toJSON(boolean recursive, String items)
    {
        JSONObject o = new JSONObject();
        o.put("text",getKey());
        if (StringUtils.isNotEmpty(getId()))
            o.put("id", getId());
        if (StringUtils.isNotEmpty(getDescription()))
            o.put("description", getDescription());
        if (isSelected())
            o.put("checked",true);
        if (null != getImageSrc())
            o.put("icon",getImageSrc());
        if (isDisabled())
            o.put("disabled", true);
        if (null != getValue())
            o.put("href",getValue());
        if (null != getScript())
            o.put("handler", "function(){" + getScript() + "}");
        if (null != getDisplay())
            o.put("display", getDisplay());
        if (recursive && null != getChildren() && getChildren().length > 0)
        {
            JSONArray a = new JSONArray();
            for (NavTree c : getChildren())
                a.put(c.toJSON(true, "menu"));
            o.put(items, a);
        }
        else
        {
            o.put("leaf",true);
        }
        return o;
    }

    
    StringBuilder toJS(StringBuilder sb, boolean asMenu)
    {
        String title = getKey();
        sb.append("{").append("text:").append(PageFlowUtil.jsString(title));
        if (StringUtils.isNotEmpty(getId()))
            sb.append(",id:").append(PageFlowUtil.jsString(getId()));
        if (StringUtils.isNotEmpty(getDescription()))
            sb.append(",description").append(PageFlowUtil.jsString(getDescription()));
        if (isSelected())
            sb.append(",checked:true");
        if (null != getImageSrc())
            sb.append(",icon:").append(PageFlowUtil.jsString(getImageSrc()));
        if (isDisabled())
            sb.append(",disabled:true");
        if (null != getValue())
            sb.append(",href:").append(PageFlowUtil.jsString(getValue()));
        if (null != getScript())
            sb.append(",handler:function(){").append(getScript()).append("}");
        if (null != getChildren() && getChildren().length > 0)
        {
            sb.append(",\n").append(asMenu ? "menu:" : "children:");
            toJS(children, sb, asMenu);
        }
        else
        {
            sb.append(",leaf:true");
        }
        sb.append("}");
        return sb;
    }

    public static StringBuilder toJS(Collection<NavTree> list, StringBuilder sb, boolean asMenu)
    {
        if (null == sb)
            sb = new StringBuilder();
        String sep = "";
        sb.append("[");
        for (NavTree tree : list)
        {
            sb.append(sep);
            if (tree == NavTree.MENU_SEPARATOR)
                sb.append("'-'");
            else
                tree.toJS(sb, asMenu);
            sep = ",\n";
        }
        sb.append("]");
        return sb;
    }
}
