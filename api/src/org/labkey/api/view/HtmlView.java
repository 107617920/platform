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
package org.labkey.api.view;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;


public class HtmlView extends WebPartView
{
    private String _contentType = null;
    private Object[] _printfParams;
    private String _html = null;

    public HtmlView(String html)
    {
        setHtml(html);
    }

    public HtmlView(String title, String html)
    {
        this(html);
        setTitle(title);
    }

    public HtmlView(String title, String html, Object... params)
    {
        this(title, html);
        _printfParams = params;
    }

    public void setHtml(String html)
    {
        _html = html;
    }

    public void setPrintfParameters(Object... params)
    {
        _printfParams = params;
    }

    /**
     * if contentType is not null, we'd better not be in a template
     */
    public void setContentType(String contentType)
    {
        _contentType = contentType;
    }

    @Override
    public void renderView(Object model, PrintWriter out) throws IOException, ServletException
    {
        assert null == _contentType || getFrame() == FrameType.NONE;
        if (null != _contentType)
            getViewContext().getResponse().setContentType(_contentType);

        if (null != _printfParams)
            out.printf(_html, _printfParams);
        else
            out.print(_html);
    }
}
