/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.api.module;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.jsp.JspBase;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;

import java.util.Map;

/*
* User: Dave
* Date: Jan 23, 2009
* Time: 4:48:17 PM
*/

/**
 * Html view based on HTML source stored in a module
 */
public class ModuleHtmlView extends HtmlView
{
    private static final Cache<String, ModuleHtmlViewDefinition> VIEW_DEF_CACHE = CacheManager.getCache(1024, CacheManager.HOUR, "Module HTML view definition cache");

    private ModuleHtmlViewDefinition _viewdef = null;

    public ModuleHtmlView(Resource r)
    {
        this(r, null);
    }


    public ModuleHtmlView(Resource r, Portal.WebPart webpart)
    {
        super(null);
        _viewdef = getViewDef(r);
        setTitle(_viewdef.getTitle());
        setHtml(replaceTokens(_viewdef.getHtml(), getViewContext(), webpart));
        if(null != _viewdef.getFrameType())
            setFrame(_viewdef.getFrameType());
    }


    public String replaceTokens(String html, ViewContext context, @Nullable Portal.WebPart webpart)
    {
        if (null == html)
            return null;

        String wrapperDivId = "ModuleHtmlView_" + UniqueID.getRequestScopedUID(context.getRequest());
        int id = null == webpart ? 0 : webpart.getRowId();

        JSONObject config = new JSONObject();
        config.put("wrapperDivId", wrapperDivId);
        config.put("id", id);
        JSONObject properties = new JSONObject();
        config.put("properties", properties);
        if (null != webpart)
        {
            for (Map.Entry<String,String> e : webpart.getPropertyMap().entrySet())
                config.put(e.getKey(), e.getValue());
        }

        String contextPath = null != context.getContextPath() ? context.getContextPath() : "invalid context path";
        String containerPath = null != context.getContainer() ? context.getContainer().getPath() : "invalid container";
        String webpartContext = config.toString();

        String ret = html.replaceAll("<%=\\s*contextPath\\s*%>", contextPath);
        ret = ret.replaceAll("<%=\\s*containerPath\\s*%>", containerPath);
        ret = ret.replaceAll("<%=\\s*webpartContext\\s*%>", webpartContext);
        return "<div id=\"" + wrapperDivId + "\">" + ret + "</div>";
    }


    public static ModuleHtmlViewDefinition getViewDef(Resource r)
    {
        String cacheKey = r.toString();
        ModuleHtmlViewDefinition viewdef = VIEW_DEF_CACHE.get(cacheKey);
        if (null == viewdef || viewdef.isStale())
        {
            viewdef = new ModuleHtmlViewDefinition(r);
            VIEW_DEF_CACHE.put(cacheKey, viewdef);
        }
        return viewdef;
    }

    public PageConfig.Template getPageTemplate()
    {
        return _viewdef.getPageTemplate();
    }

    public boolean isRequiresLogin()
    {
        return _viewdef.isRequiresLogin();
    }

    public int getRequiredPerms()
    {
        return _viewdef.getRequiredPerms();
    }
}
