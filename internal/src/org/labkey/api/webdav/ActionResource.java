/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.api.webdav;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 10:33:38 AM
 */

@Deprecated  // Avoid: yes, it's a simple way to index data, but it's inefficient, inflexible, and leads to poor search results.
public class ActionResource extends AbstractDocumentResource
{
    final private String _docid;
    final private ActionURL _url;
    final private ActionURL _indexUrl;

    private SimpleDocumentResource _sdr = null; // Can be set when transitioning from ActionResource to SimpleDocumentResource,
                                                // making it easier to compare the docs created by the old and new approaches

    public ActionResource(SearchService.SearchCategory category, String docid, ActionURL url, ActionURL source)
    {
        super(new Path("action",url.getLocalURIString()));
        _docid = docid;
        _url = url;
        _containerId = getContainerId(_url);
        _indexUrl = source;
        _indexUrl.replaceParameter("_print","1");
        _indexUrl.setScheme("http");
        _indexUrl.setHost("localhost");
        _properties = new HashMap<>();
        if (null != category)
            _properties.put(SearchService.PROPERTY.categories.toString(), category.getName());
    }


    @Override
    public String getDocumentId()
    {
        return null!=_docid ? _docid : super.getDocumentId();
    }
    

    String getContainerId(ActionURL url)
    {
        String path = url.getExtraPath();
        if (GUID.isGUID(path))
            return path;
        Container c = ContainerManager.getForPath(path);
        return null==c ? null : c.getId();
    }


    @Override
    public void setLastIndexed(long ms, long modified)
    {
        // UNDONE
    }


    public boolean exists()
    {
        return true;
    }                                                                                         


    @Override
    public String getContentType()
    {
        return "text/html";
    }

    
    public InputStream getInputStream(User user) throws IOException
    {
        return new ByteArrayInputStream(getResponse(user).getContentAsByteArray());
    }


    @Override
    public FileStream getFileStream(User user) throws IOException
    {
        final byte[] buf = getResponse(user).getContentAsByteArray();
        return new FileStream.ByteArrayFileStream(buf);
    }
    

    MockHttpServletResponse _response = null;
    
    MockHttpServletResponse getResponse(final User user) throws IOException
    {
        if (null == _response)
        {
            HttpServletRequest req = ViewServlet.mockRequest("GET", _indexUrl, user, null, null);
            try
            {
                _response = ViewServlet.mockDispatch(req, "text/html");
                return _response;
            }
            catch (IOException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                IOException io = new IOException();
                io.initCause(x);
                throw io;
            }

        }
        return _response;
    }

    public String getExecuteHref(ViewContext context)
    {
        return getExecuteHref();
    }

    public String getExecuteHref()
    {
        return _url.getLocalURIString();
    }

    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new IllegalStateException();
    }


    public long getContentLength() throws IOException
    {
        throw new IllegalStateException();
    }

    public @Nullable SimpleDocumentResource getSimpleDocumentResource()
    {
        return _sdr;
    }

    public void setSimpleDocumentResource(SimpleDocumentResource sdr)
    {
        _sdr = sdr;
    }
}
