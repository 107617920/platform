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

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.labkey.api.util.URLHelper;

/**
 * User: matthewb
 * Date: May 17, 2007
 * Time: 12:52:55 PM
 */
public abstract class ConfirmAction<FORM> extends BaseViewAction
{
    public static final String CONFIRMACTION = ConfirmAction.class.getName();
    boolean includeHeader = true;

    protected ConfirmAction()
    {
    }

    public String getConfirmText()
    {
        return "OK";
    }

    public String getCancelText()
    {
        return "Cancel";
    }

    public URLHelper getCancelUrl()
    {
        return null;
    }

    public boolean isPopupConfirmation()
    {
        return false;
    }

    public final ModelAndView handleRequest() throws Exception
    {
        ViewContext context = HttpView.currentContext();

        FORM form = null;
        BindException errors = null;

        if (null != getCommandClass())
        {
            errors = bindParameters(getPropertyValues());
            form = (FORM)errors.getTarget();
        }

        boolean success = errors == null || !errors.hasErrors();

        if (success && null != form)
            validate(form, errors);
        success = errors == null || !errors.hasErrors();

        if (success)
        {
            if ("POST".equals(context.getRequest().getMethod()))
            {
                success = handlePost(form, errors);

                if (success)
                {
                    ModelAndView mv = getSuccessView(form);
                    if (null != mv)
                        return mv;
                    HttpView.throwRedirect(getSuccessURL(form));
                }
            }
            else
            {
                ModelAndView confirmView = getConfirmView(form, errors);
                JspView confirmWrapper = new JspView("/org/labkey/api/action/confirmWrapper.jsp");
                confirmWrapper.addObject(CONFIRMACTION, this);
                confirmWrapper.setBody(confirmView);
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return confirmWrapper;
            }
        }

        // We failed... redirect if fail URL is specified, otherwise return the error view
        ActionURL urlFail = getFailURL(form, errors);
        if (null != urlFail)
            HttpView.throwRedirect(urlFail);

        return getFailView(form, errors);
    }


    protected String getCommandClassMethodName()
    {
        return "handlePost";
    }

    public BindException bindParameters(PropertyValues m) throws Exception
    {
        return defaultBindParameters(getCommand(), m);
    }


    /**
     * View with text and buttons.  Should NOT include &lt;form&gt; 
     */
    public abstract ModelAndView getConfirmView(FORM form, BindException errors) throws Exception;


    /**
     * may call throwRedirect() on success
     *
     * handlePost() can call setReshow(false) to force record to be reselected
     * return a view to display or null to call getView(form,true);
     */
    public abstract boolean handlePost(FORM form, BindException errors) throws Exception;

    public void validate(Object form, Errors errors)
    {
        validateCommand((FORM)form, errors);
    }

    /* Generic version of validate */
    public abstract void validateCommand(FORM form, Errors errors);


    public abstract URLHelper getSuccessURL(FORM form);

    // not usually used but some actions return views that close the current window etc...
    public ModelAndView getSuccessView(FORM form)
    {
        return null;
    }

    public ActionURL getFailURL(FORM form, BindException errors)
    {
        return null;
    }

    public ModelAndView getFailView(FORM form, BindException errors)
    {
        getPageConfig().setTemplate(PageConfig.Template.Dialog);
        return new SimpleErrorView(errors);
    }

    // return false for no header (e.g. for a dialog)
    public boolean getIncludeHeader()
    {
        return includeHeader;
    }

    public void setIncludeHeader(boolean h)
    {
        includeHeader = h;
    }
}
