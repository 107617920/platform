/*
 * Copyright (c) 2004-2012 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.HString;
import org.labkey.api.util.SkipMothershipLogging;
import org.labkey.api.util.URLHelper;

import javax.servlet.ServletException;

public class RedirectException extends RuntimeException implements SkipMothershipLogging
{
    String _url;

    public RedirectException(URLHelper url)
    {
        this(url.getLocalURIString());
    }

    public RedirectException(String url)
    {
        _url = url;
    }

    public RedirectException(HString url)
    {
        _url = url.getSource();
    }

    public String getURL()
    {
        return _url;
    }
}
