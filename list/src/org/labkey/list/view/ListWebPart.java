/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.list.view;

import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;

import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeSet;

public class ListWebPart extends WebPartView<ViewContext>
{
    static public BaseWebPartFactory FACTORY = new AlwaysAvailableWebPartFactory("Lists")
    {
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new ListWebPart(portalCtx);
        }
    };
    public ListWebPart(ViewContext portalCtx)
    {
        super(new ViewContext(portalCtx));
        setTitle("Lists");
        if (getModelBean().hasPermission(UpdatePermission.class))
        {
            setTitleHref(ListController.getBeginURL(getViewContext().getContainer()));
        }
    }

    protected void renderView(ViewContext model, PrintWriter out) throws Exception
    {
        include(new JspView<Object>(this.getClass(), "begin.jsp", model));
    }
}
