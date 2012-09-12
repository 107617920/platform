/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Collections;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;


public class DetailsURL extends StringExpressionFactory.FieldKeyStringExpression implements HasViewContext
{
    public static Pattern actionPattern = Pattern.compile("/?[\\w\\-]+/[\\w\\-]+.view?.*");
    public static Pattern classPattern = Pattern.compile("[\\w\\.\\$]+\\.class?.*");

    private ContainerContext _containerContext;

    // constructor parameters
    ActionURL _url;
    String _urlSource;

    // parsed fields
    ActionURL _parsedUrl;
    // _source from AbstractStringExpression            


    public static String validateURL(String str)
    {
        if (DetailsURL.actionPattern.matcher(str).matches() || DetailsURL.classPattern.matcher(str).matches())
            return null;

        return "Invalid url pattern: " + str;
    }


    public static DetailsURL fromString(String str)
    {
        DetailsURL ret = new DetailsURL(str);
        try
        {
            ret.parse();
        }
        catch (IllegalStateException x)
        {
            // ignore during startup
        }
        return ret;
    }

    public static DetailsURL fromString(Container c, String str, Collection<QueryException> qpe)
    {
        try
        {
            return fromString(c, str);
        }
        catch (IllegalArgumentException iae)
        {
            if (qpe != null)
                qpe.add(new MetadataException(iae.getMessage(), iae));
            else
                throw iae;
        }
        return null;
    }

    public static DetailsURL fromString(Container c, String str)
    {
        DetailsURL ret = new DetailsURL(c, str);
        ret.parse();    // validate
        return ret;
    }


    protected DetailsURL(String str)
    {
        _urlSource = str;
    }


    protected DetailsURL(Container c, String str)
    {
        _urlSource = str;
        _containerContext = c;
    }


    public DetailsURL(ActionURL url)
    {
        _url = url.clone();
    }

    /**
     * @param url base URL to which parameters may be added
     * @param columnParams map from URL parameter name to source column identifier, which may be a String, FieldKey, or ColumnInfo
     */
    public DetailsURL(ActionURL url, Map<String, ?> columnParams)
    {
        url = url.clone();
        for (Map.Entry<String, ?> e : columnParams.entrySet())
        {
            Object v = e.getValue();
            String strValue;
            if (v instanceof String)
                strValue = (String)v;
            else if (v instanceof FieldKey)
                strValue = ((FieldKey)v).encode();
            else if (v instanceof ColumnInfo)
                strValue = ((ColumnInfo)v).getFieldKey().encode();
            else
                throw new IllegalArgumentException("Column param not supported: " + String.valueOf(v));
            url.addParameter(e.getKey(), "${" + strValue + "}");
        }
        _url = url;
    }

    public DetailsURL(ActionURL baseURL, String param, FieldKey subst)
    {
        this(baseURL, Collections.singletonMap(param,subst));
    }

    @Override
    protected void parse()
    {
        assert null == _url || null == _urlSource;

        if (null != _url)
        {
            _parsedUrl = _url;
        }
        else if (null != _urlSource)
        {
            String expr = _urlSource;
            int i = StringUtils.indexOfAny(expr,": /");
            String protocol = (i != -1 && expr.charAt(i) == ':') ? expr.substring(0,i) : "";

            if (protocol.contains("script"))
                throw new IllegalArgumentException("Script not allowed in urls: " + expr);

            if (actionPattern.matcher(expr).matches())
            {
                if (!expr.startsWith("/")) expr = "/" + expr;
                _parsedUrl = new ActionURL(expr);
            }
            else if (classPattern.matcher(expr).matches())
            {
                String className = expr.substring(0,expr.indexOf(".class?"));
                Class<Controller> cls;
                try { cls = (Class<Controller>)Class.forName(className); } catch (Exception x) {throw new IllegalArgumentException("action class '" + className + "' not found: " + expr);}
                _parsedUrl = new ActionURL(cls, null);
                _parsedUrl.setRawQuery(expr.substring(expr.indexOf('?')+1));
            }
            else
                throw new IllegalArgumentException(
                        "Failed to parse url '" + _urlSource + "'.\n" +
                        "Supported url formats:\n" +
                        "\t/controller/action.view?id=${RowId}\n" +
                        "\torg.labkey.package.MyController$ActionAction.class?id=${RowId}");
        }
        else
            throw new IllegalStateException();
            
        _source = StringUtils.trimToEmpty(_parsedUrl.getQueryString(true));

        super.parse();
    }


    @Override
    public Set<FieldKey> getFieldKeys()
    {
        Set<FieldKey> set = super.getFieldKeys();
        if (_containerContext instanceof ContainerContext.FieldKeyContext)
            set.add(((ContainerContext.FieldKeyContext) _containerContext).getFieldKey());
        return set;
    }

    public boolean hasContainerContext()
    {
        return _containerContext != null;
    }

    @Override
    public DetailsURL remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> remap)
    {
        DetailsURL copy = (DetailsURL)super.remapFieldKeys(parent, remap);
        if (copy._containerContext instanceof ContainerContext.FieldKeyContext)
        {
            FieldKey key = ((ContainerContext.FieldKeyContext)copy._containerContext).getFieldKey();
            FieldKey re = _remap(key, parent, remap);
            copy._containerContext = new ContainerContext.FieldKeyContext(re);
        }
        // copy changes backwards
        copy._parsedUrl.setRawQuery(copy._source);
        copy._url = copy._parsedUrl;
        copy._urlSource = null;
        return copy;
    }


    @Override
    public StringExpressionFactory.FieldKeyStringExpression dropParent(String parentName)
    {
        return super.dropParent(parentName);    //To change body of overridden methods use File | Settings | File Templates.
    }


    @Override
    public String eval(Map context)
    {
        String query = super.eval(context);
        if (query == null)
        {
            // Bail out if the context is missing one of the substitutions. Better to have no URL than a URL that's
            // missing parameters
            return null;
        }
        Container c = getContainer(context);
        if (null != c)
            _parsedUrl.setContainer(c);
        return _parsedUrl.getPath() + "?" + query;
    }


    public DetailsURL copy(ContainerContext cc)
    {
        return copy(cc, false);
    }


    public DetailsURL copy(ContainerContext cc, boolean overwrite)
    {
        DetailsURL ret = (DetailsURL)copy();
        ret.setContainerContext(cc, overwrite);
        return ret;
    }


    @Override
    public DetailsURL clone()
    {
        DetailsURL clone = (DetailsURL)super.clone();
        if (null != clone._url)
            clone._url = clone._url.clone();
        if (null != clone._parsedUrl)
            clone._parsedUrl = clone._parsedUrl.clone();
        return clone;
    }

    ViewContext _context;

    public void setViewContext(ViewContext context)
    {
        _context = context;
    }


    public ViewContext getViewContext()
    {
        return _context;
    }


    Container getContainer(Map context)
    {
        if (null != _containerContext)
            return _containerContext.getContainer(context);
        if (null != _context)
            return _context.getContainer();
        Object c = null==context ? null
                 : context.containsKey("container") ? context.get("container")
                 : context instanceof RenderContext ? ((RenderContext)context).getContainer()
                 : null;
        if (c instanceof Container)
            return (Container)c;
        return null;
    }


    public void setContainerContext(ContainerContext cc)
    {
        setContainerContext(cc, true);
    }

    public void setContainerContext(ContainerContext cc, boolean overwrite)
    {
        if (null == _containerContext || overwrite)
            _containerContext = cc;
    }

    public ActionURL getActionURL()
    {
        if (null == _parsedUrl)
            parse();
        ActionURL ret = null == _parsedUrl ? null : _parsedUrl.clone();
        if (null != ret)
        {
            Container c = getContainer(null);
            if (null != c)
                ret.setContainer(c);
        }
        return ret;
    }


    @Override
    public String getSource()
    {
        if (null != _urlSource)
            return _urlSource;
        String controller = _url.getController();
        String action = _url.getAction();
        if (!action.endsWith(".view"))
            action = action + ".view";
        String to = "/" + encode(controller) + "/" + encode(action) + "?" + _url.getQueryString(true);
        assert null == DetailsURL.validateURL(to);
        return to;
    }

    @Override
    public boolean canRender(Set<FieldKey> fieldKeys)
    {
        // Call super so that we don't consider the ContainerContext's column mandatory (we will default to the current
        // container if it's not present)
        return fieldKeys.containsAll(super.getFieldKeys());
    }


    private String encode(String s)
    {
        return PageFlowUtil.encode(s);
    }
}
