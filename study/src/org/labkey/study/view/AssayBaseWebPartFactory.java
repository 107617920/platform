/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.view;

import org.labkey.api.view.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;

public abstract class AssayBaseWebPartFactory extends BaseWebPartFactory
{
    public static final String SHOW_BUTTONS_KEY = "showButtons";
    public static final String PROTOCOL_ID_KEY = "viewProtocolId";

    public AssayBaseWebPartFactory(String name)
    {
        super(name, null, true, true);
    }

    protected static Integer getIntPropertry(Portal.WebPart webPart, String propertyName)
    {
        String value = webPart.getPropertyMap().get(propertyName);
        if (value != null)
        {
            try
            {
                return new Integer(value);
            }
            catch (NumberFormatException e)
            {
            }
        }
        return null;
    }

    public static Integer getProtocolId(Portal.WebPart webPart)
    {
        return getIntPropertry(webPart, PROTOCOL_ID_KEY);
    }

//    public static Integer getBatchId(Portal.WebPart webPart)
//    {
//        return getIntPropertry(webPart, BATCH_ID_KEY);
//    }
//
//    public static Integer getRunId(Portal.WebPart webPart)
//    {
//        return getIntPropertry(webPart, RUN_ID_KEY);
//    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        Integer protocolId = getProtocolId(webPart);
        boolean showButtons = Boolean.parseBoolean(webPart.getPropertyMap().get(SHOW_BUTTONS_KEY));
        ExpProtocol protocol = null;
        if (protocolId != null)
            protocol = ExperimentService.get().getExpProtocol(protocolId.intValue());
        WebPartView view;
        if (protocol == null)
        {
            view = new HtmlView("This webpart does not reference a valid assay.  Please customize the webpart.");
            view.setTitle(getName());
        }
        else
        {
            view = getWebPartView(portalCtx, webPart, protocol, showButtons);
        }
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }

    public abstract WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart, ExpProtocol protocol, boolean showButtons);

    public abstract String getDescription();

    public static class EditViewBean
    {
        public String description;
        public Portal.WebPart webPart;
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart)
    {
        EditViewBean bean = new EditViewBean();
        bean.description = getDescription();
        bean.webPart = webPart;
        return new JspView<EditViewBean>("/org/labkey/study/view/customizeAssayDetailsWebPart.jsp", bean);
    }
}