/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.wiki.model;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.util.HString;
import org.labkey.api.util.MemTracker;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.wiki.WikiContentCache;
import org.labkey.wiki.WikiManager;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * User: Tamra Myers
 * Date: Feb 14, 2006
 * Time: 1:31:25 PM
 */
public class WikiVersion
{
    private int _rowId;
    private String _pageEntityId;
    private int _version;
    private HString _title;
    private String _body;
    private int _createdBy;
    private Date _created;
    private WikiRendererType _rendererType;
    private boolean _cache = true;

    private HString _wikiName;

    public WikiVersion()
    {
        MemTracker.getInstance().put(this);
    }

    public WikiVersion(HString wikiname)
    {
        _wikiName = wikiname;
        MemTracker.getInstance().put(this);
    }

    /**
     * Copy constructor used when creating a new version of a wiki
     * @param copy The current latest version
     */
    public WikiVersion(WikiVersion copy)
    {
        if (null == copy)
            return;
        _pageEntityId = copy._pageEntityId;
        _title = copy._title;
        _body = copy._body;
        _rendererType = copy._rendererType;
        _wikiName = copy._wikiName;
        MemTracker.getInstance().put(this);
    }

    public HString getName()
    {
        return _wikiName;
    }

    public void setName(HString wikiname)
    {
        _wikiName = wikiname;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public String getPageEntityId()
    {
        return _pageEntityId;
    }

    public void setPageEntityId(String pageEntityId)
    {
        _pageEntityId = pageEntityId;
    }

    // TODO: WikiVersion should know its wiki & container
    public String getHtml(Container c, Wiki wiki)
    {
        return "<div class=\"labkey-wiki\">" + getHtmlForConvert(c, wiki) + "</div>";
    }

    public String getHtmlForConvert(Container c, Wiki wiki)
    {
        return WikiContentCache.getHtml(c, wiki, this, _cache);
    }

    public HString getTitle()
    {
        if (null == _title)
            _title = (null == _wikiName || HString.eq(_wikiName, "default")) ? new HString("Wiki", false) : _wikiName;
        return _title;
    }

    public void setTitle(HString title)
    {
        _title = title;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getVersion()
    {
        return _version;
    }

    public void setVersion(int version)
    {
        _version = version;
    }

    public String getBody()
    {
        return _body;
    }

    public void setBody(String body)
    {
        _body = body;
    }

    @Deprecated
    // Only used for bean binding -- use getRenderTypeEnum() instead
    public String getRendererType()
    {
        return getRendererTypeEnum().name();
    }

    public WikiRendererType getRendererTypeEnum()
    {
        if (_rendererType == null)
            _rendererType = WikiManager.DEFAULT_WIKI_RENDERER_TYPE;

        return _rendererType;
    }

    public void setRendererType(String rendererTypeName)
    {
        _rendererType = WikiRendererType.valueOf(rendererTypeName);
    }

    public void setRendererTypeEnum(WikiRendererType rendererType)
    {
        _rendererType = rendererType;
    }

    public WikiRenderer getRenderer(String hrefPrefix,
                                    String attachPrefix,
                                    Map<HString, HString> nameTitleMap,
                                    Collection<? extends Attachment> attachments)
    {
        if (_rendererType == null)
            _rendererType = WikiManager.DEFAULT_WIKI_RENDERER_TYPE;

        return WikiManager.get().getRenderer(_rendererType, hrefPrefix, attachPrefix, nameTitleMap, attachments);
    }

    // Cache the rendered wiki content by default; set to false to avoid caching
    public void setCacheContent(boolean cache)
    {
        _cache = cache;
    }

    private static Pattern NON_VISUAL_RE = Pattern.compile("(<(script|form)[\\s>]|\\$\\{labkey\\.)");

    public boolean hasNonVisualElements()
    {
        // look for form, script tag and ${labkey...} expressions
        return _body != null && NON_VISUAL_RE.matcher(_body.toLowerCase()).find();
    }
}
