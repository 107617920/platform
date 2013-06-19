/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.issue;

import org.labkey.api.view.*;

import java.lang.reflect.InvocationTargetException;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 3:38:10 PM
 */
class IssuesWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public IssuesWebPartFactory()
    {
        this("Issues Summary", null);
    }

    public IssuesWebPartFactory(String name, String location)
    {
        super(name, location, true, false);
        addLegacyNames(IssuesModule.NAME);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        WebPartView v = new IssuesController.SummaryWebPart();
        populateProperties(v, webPart.getPropertyMap());
        return v;
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<>("/org/labkey/issue/issuesCustomize.jsp", webPart);
    }
}
