/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.HttpUtil;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

/**
 * User: matthewb
 * Date: Sep 28, 2009
 * Time: 4:09:42 PM
 *
 * This is a hybrid Api/Form action.
 *  GET is like SimpleViewForm
 *  POST is like BaseApiAction
 */
public abstract class FormApiAction<FORM> extends BaseApiAction<FORM> implements NavTrailAction
{
    @Override
    public void checkPermissions() throws UnauthorizedException
    {
        UnauthorizedException.Type type;
        // Issue 34825 - don't prompt for basic auth for browser requests
        if (HttpUtil.isBrowser(getViewContext().getRequest()))
        {
            type = isGet() ? UnauthorizedException.Type.redirectToLogin : UnauthorizedException.Type.sendUnauthorized;
        }
        else
        {
            type = UnauthorizedException.Type.sendBasicAuth;
        }
        setUnauthorizedType(type);
        super.checkPermissions();
    }

    @Override
    protected ModelAndView handleGet() throws Exception
    {
        BindException errors = bindParameters(getPropertyValues());
        FORM form = (FORM)errors.getTarget();

        validate(form, errors);

        ModelAndView v;

        if (null != StringUtils.trimToNull((String) getProperty("_print")) ||
            null != StringUtils.trimToNull((String) getProperty("_print.x")))
            v = getPrintView(form, errors);
        else
            v = getView(form, errors);
        return v;
    }

    public abstract ModelAndView getView(FORM form, BindException errors) throws Exception;

    protected ModelAndView getPrintView(FORM form, BindException errors) throws Exception
    {
        return getView(form, errors);
    }

    protected BindException bindParameters(PropertyValues pvs) throws Exception
    {
        return SimpleViewAction.defaultBindParameters(getCommand(), getCommandName(), pvs);
    }

    /**
     * Last vestige of ExtFormAction, which had the comment: ensures that the validation errors are reported back to
     * the form in the way that Ext forms require.
     */
    @Override
    protected ApiResponseWriter createResponseWriter() throws IOException
    {
        return new ExtFormResponseWriter(getViewContext().getRequest(), getViewContext().getResponse(), getContentTypeOverride());
    }
}
