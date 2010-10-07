/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.action;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.URLException;

import java.net.URISyntaxException;


/**
* User: adam
* Date: Nov 22, 2007
* Time: 1:27:34 PM
*/
public class ReturnUrlForm
{
    public enum Params
    {
        returnUrl
    }

    private ReturnURLString _returnUrl;

    // Generate a hidden form field to post a return URL with the standard name used by this form
    public static String generateHiddenFormField(URLHelper returnUrl)
    {
        return "<input type=\"hidden\" name=\"" + Params.returnUrl + "\" value=\"" + PageFlowUtil.filter(returnUrl) + "\">";
    }

    public ReturnURLString getReturnUrl()
    {
        return _returnUrl;
    }

    public void setReturnUrl(ReturnURLString returnUrl)
    {
        _returnUrl = returnUrl;
    }

    @Nullable
    public URLHelper getReturnURLHelper()
    {
        try
        {
            return (null == _returnUrl ? null : new URLHelper(_returnUrl));
        }
        catch (URISyntaxException e)
        {
            throw new URLException(_returnUrl.getSource(), "returnUrl parameter", e);
        }
    }

    @Nullable
    public ActionURL getReturnActionURL()
    {
        return (null == _returnUrl ? null : new ActionURL(_returnUrl));
    }

    // Return the passed-in default URL if returnURL param is missing or unparseable
    public URLHelper getReturnURLHelper(URLHelper defaultURL)
    {
        try
        {
            URLHelper url = getReturnURLHelper();
            if (null != url)
                return url;
        }
        catch (URLException e)
        {
        }
        return defaultURL;
    }
}
