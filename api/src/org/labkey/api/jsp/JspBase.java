/*
 * Copyright (c) 2005-2010 LabKey Corporation
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

package org.labkey.api.jsp;

import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.HStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for nearly all JSP pages that we use.
 * This is the place to put methods that will be useful to lots
 * of pages, regardless of what they do, or what module they are in.
 * <p/>
 * BE VERY CAREFUL NOT TO ADD POORLY NAMED METHODS TO THIS CLASS!!!!
 * <p/>
 * Do not add a method called "filter" to this class.
 */
abstract public class JspBase extends JspContext implements HasViewContext
{
    protected JspBase()
    {
        super();
    }

    ViewContext _viewContext;

    public ViewContext getViewContext()
    {
        return _viewContext;
    }

    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
    }

    /**
     * Html escape an object.toString().
     * The name comes from Embedded Ruby.
     */
    public String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public HString h(HString str)
    {
        return PageFlowUtil.filter(str);
    }

    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public HString h(HStringBuilder str)
    {
        return PageFlowUtil.filter(str);
    }

    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public String h(String str)
    {
        return PageFlowUtil.filter(str);
    }

    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public String h(String str, boolean encodeSpace)
    {
        return PageFlowUtil.filter(str, encodeSpace);
    }

    public String h(URLHelper url)
    {
        return PageFlowUtil.filter(url);
    }

    /**
     * Quotes a javascript string.
     * Returns a javascript string literal which is wrapped with ', and is properly escaped inside.
     * Note that if you think that you require double quotes (") to be escaped, then it probably means that you
     * need to HTML escape the quoted string (i.e. call "hq" instead of "q").
     * Javascript inside of element event attributes (e.g. onclick="dosomething") needs to be HTML escaped.
     * Javascript inside of &lt;script> tags should NEVER be HTML escaped.
     */
    final protected String q(String str)
    {
        if (null == str) return "null";
        return PageFlowUtil.jsString(str);
    }

    protected String hq(String str)
    {
        return h(q(str));
    }

    /**
     * URL encode a string.
     */
    public String u(String str)
    {
        return PageFlowUtil.encode(str);
    }


    /**
     * Given the Class of an action in a Spring controller, returns the view URL to the action.
     *
     * @param action Action in a Spring controller
     * @return view url
     */
    public ActionURL urlFor(Class<? extends Controller> action)
    {
        return new ActionURL(action, getViewContext().getContainer());
    }

    /**
     * Convenience function for getting a specified <code>UrlProvider</code> interface
     * implementation, for use in writing URLs implemented in other modules.
     *
     * @param inter interface extending UrlProvider
     * @return an implementation of the interface
     */
    public <P extends UrlProvider> P urlProvider(Class<P> inter)
    {
        return PageFlowUtil.urlProvider(inter);
    }

    public String textLink(String text, String href, String id)
    {
        return PageFlowUtil.textLink(text, href, id);
    }

    public String textLink(String text, String href)
    {
        return PageFlowUtil.textLink(text, href, null, null);
    }

    public String textLink(String text, HString href)
    {
        return PageFlowUtil.textLink(text, href, null, null);
    }

    public String textLink(String text, String href, String onClickScript, String id)
    {
        return PageFlowUtil.textLink(text, href, onClickScript, id);
    }

    public String textLink(String text, String href, String onClickScript, String id, Map<String, String> props)
    {
        return PageFlowUtil.textLink(text, href, onClickScript, id, props);
    }
    
    public String textLink(String text, ActionURL url, String onClickScript, String id)
    {
        return PageFlowUtil.textLink(text, url, onClickScript, id);
    }

    public String textLink(String text, ActionURL url)
    {
        return PageFlowUtil.textLink(text, url);
    }

    public String textLink(String text, ActionURL url, String id)
    {
        return PageFlowUtil.textLink(text, url, id);
    }

    public String generateBackButton()
    {
        return PageFlowUtil.generateBackButton();
    }

    public String generateBackButton(String text)
    {
        return PageFlowUtil.generateBackButton(text);
    }

    /**
     * Consider: is there any way to name this method in such a way as to
     * make the order of parameters unambiguous?
     */
    @Deprecated // use actionClass or ActionURL variants
    public String generateButton(String text, String href)
    {
        return PageFlowUtil.generateButton(text, href);
    }

    public String generateButton(String text, HString href)
    {
        return PageFlowUtil.generateButton(text, href.getSource());
    }

    public String generateButton(String text, Class<? extends Controller> actionClass)
    {
        return PageFlowUtil.generateButton(text, new ActionURL(actionClass, getViewContext().getContainer()));
    }

    /**
     * Consider: is there any way to name this method in such a way as to
     * make the order of parameters unambiguous?
     */
    public String generateButton(String text, String href, String onClickScript)
    {
        return PageFlowUtil.generateButton(text, href, onClickScript);
    }

    public String generateButton(String text, URLHelper href)
    {
        return PageFlowUtil.generateButton(text, href);
    }

    public String generateButton(String text, URLHelper href, String onClickScript)
    {
        return PageFlowUtil.generateButton(text, href, onClickScript);
    }

    public String generateSubmitButton(String text)
    {
        return PageFlowUtil.generateSubmitButton(text);
    }

    public String generateReturnUrlFormField(URLHelper returnURL)
    {
        return ReturnUrlForm.generateHiddenFormField(returnURL);
    }

    public String generateReturnUrlFormField(ReturnUrlForm form)
    {
        return ReturnUrlForm.generateHiddenFormField(form.getReturnURLHelper());
    }

    public void include(ModelAndView view, Writer writer) throws Exception
    {
        HttpView.currentView().include(view, writer);
    }

    public String buttonImg(String text, String onClickScript)
    {
        return PageFlowUtil.generateSubmitButton(text, onClickScript);
    }
    
    public String helpPopup(String helpText)
    {
        return helpPopup(null, helpText, false);
    }

    public String helpPopup(String title, String helpText)
    {
        return helpPopup(title, helpText, false);
    }

    public String helpPopup(String title, String helpText, boolean htmlHelpText)
    {
        return PageFlowUtil.helpPopup(title, helpText, htmlHelpText);
    }

    public String formatDate(Date date)
    {
        return DateUtil.formatDate(date);
    }

    public String formatDateTime(Date date)
    {
        return DateUtil.formatDateTime(date);
    }

    public String getMessage(ObjectError e)
    {
        if (e == null)
            return "";
        return getViewContext().getMessage(e);
    }

    JspView _me = null;
    
    JspView getView()
    {
        if (null == _me)
            _me = (JspView)HttpView.currentView();
        return _me;
    }



    //
    // Spring error handling helpers
    //
    // CONSIDER: move into PageFlowUtil
    //

    public Errors getErrors(String bean)
    {
        return (Errors)getViewContext().getRequest().getAttribute(BindingResult.MODEL_KEY_PREFIX + bean);
    }

    protected List<ObjectError> _getErrorsForPath(String path)
    {
        // determine name of the object and property
        String beanName;
        String field;

        int dotPos = path.indexOf('.');
        if (dotPos == -1)
        {
            beanName = path;
            field = null;
        }
        else
        {
            beanName = path.substring(0, dotPos);
            field = path.substring(dotPos + 1);
        }

        Errors errors = getErrors(beanName);
        List objectErrors = null;

        if (errors != null)
        {
            if (field != null)
            {
                if ("*".equals(field))
                {
                    objectErrors = errors.getAllErrors();
                }
                else if (field.endsWith("*"))
                {
                    objectErrors = errors.getFieldErrors(field);
                }
                else
                {
                    objectErrors = errors.getFieldErrors(field);
                }
            }

            else
            {
                objectErrors = errors.getGlobalErrors();
            }
        }
        return (List<ObjectError>)(null == objectErrors ? Collections.emptyList() : objectErrors);
    }

    public List<ObjectError> getErrorsForPath(String path)
    {
        List<ObjectError> l = _getErrorsForPath(path);
        // mark errors as displayed
        for (ObjectError e : l)
            _returnedErrors.put(e,path);
        return l;
    }
    
    public ObjectError getErrorForPath(String path)
    {
        List<ObjectError> l = _getErrorsForPath(path);
        ObjectError e = l.size() == 0 ? null : l.get(0);
        _returnedErrors.put(e,path);
        return e;
    }

    public String formatErrorsForPath(String path)
    {
        List<ObjectError> l = getErrorsForPath(path);
        return _formatErrorList(l, false);
    }

    //Set<String> _returnedErrors = new HashSet<String>();
    IdentityHashMap<ObjectError,String> _returnedErrors = new IdentityHashMap<ObjectError,String>();

    // For extra credit, return list of errors not returned by formatErrorsForPath() or formatErrorForPath()
    public List<ObjectError> getMissedErrors(String bean)
    {
        Errors errors = getErrors(bean);
        ArrayList<ObjectError> missed = new ArrayList<ObjectError>();

        if (null != errors)
        {
            for (ObjectError e : (List<ObjectError>)errors.getAllErrors())
            {
                if (!_returnedErrors.containsKey(e))
                {
                    missed.add(e);
                    _returnedErrors.put(e,"missed");
                }
            }
        }
        return missed;
    }

    public String formatMissedErrors(String bean)
    {
        List<ObjectError> l = getMissedErrors(bean);
        // fieldNames==true is ugly, but these errors are probably not displayed in the right place on the form
        return _formatErrorList(l, true);
    }

    // If errors exist, returns formatted errors in a <tr> with the specified colspan (or no colspan, for 0 or 1) followed by a blank line
    // If no errors, returns an empty string
    public String formatMissedErrorsInTable(String bean, int colspan)
    {
        String errorHTML = formatMissedErrors(bean);

        if (0 == errorHTML.length())
            return "";
        else
            return "\n<tr><td" + (colspan > 1 ? " colspan=" + colspan : "") + ">" + errorHTML + "</td></tr>\n<tr><td" + (colspan > 1 ? " colspan=" + colspan : "") + ">&nbsp;</td></tr>";
    }

    protected String _formatErrorList(List<ObjectError> l, boolean fieldNames)
    {
        if (l.size() == 0)
            return "";
        ViewContext context = getViewContext();
        StringBuilder message = new StringBuilder();
        String br = "";
        message.append("<div class=\"labkey-error\">");
        for (ObjectError e : l)
        {
            message.append(br);
            br = "<br>";
            if (fieldNames && e instanceof FieldError)
            {
                message.append("<b>").append(h(((FieldError) e).getField())).append(":</b>&nbsp;");
            }
            message.append(h(context.getMessage(e)));
        }
        message.append("</div>");
        return message.toString();
    }

    protected enum Method
    {
        Get("view"), Post("post");

        private final String _suffix;

        Method(String suffix)
        {
            _suffix = suffix;
        }

        public String getSuffix()
        {
            return _suffix;
        }

        public String getMethod()
        {
            return name().toLowerCase();
        }
    }

    protected String formAction(Class<? extends Controller> actionClass, Method method)
    {
        return "action=\"" + SpringActionController.getActionName(actionClass) + "." + method.getSuffix() + "\" method=\"" + method.getMethod() + "\"";
    }
}
