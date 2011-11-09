/*
 * Copyright (c) 2005-2011 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.PreferenceService;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.MemTracker;
import org.labkey.api.writer.ContainerUser;
import org.springframework.beans.PropertyValues;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.*;

/**
 * User: matthewb
 * Date: Mar 20, 2005
 * Time: 12:26:38 PM
 *
 * UNDONE: kill BoundMap functionality of ViewContext
 */
public class ViewContext extends BoundMap implements MessageSource, ContainerContext, ContainerUser, ApplicationContextAware
{
    private ApplicationContext _applicationContext;
    private HttpServletRequest _request;
    private HttpServletResponse _response;
    private User _user;

    private ActionURL _url;                     // path and parameters on the URL (does not include posted values)
    private String _scopePrefix = "";
    private Container _c = null;
    private Set<Role> _contextualRoles = new HashSet<Role>();

    PropertyValues _pvsBind = null;              // may be set by SpringActionController, representing values used to bind command object

    public ViewContext()
    {
        setBean(this);
        assert MemTracker.put(this);
    }


    /**
     * Copy constructor.
     *
     * @param copyFrom
     */
    public ViewContext(ViewContext copyFrom)
    {
        this();
//        _parentContext = copyFrom._parentContext;
        _request = copyFrom._request;
        _response = copyFrom._response;
        _url = copyFrom._url;
        _user = copyFrom._user;
        _scopePrefix = copyFrom._scopePrefix;
        _c = copyFrom._c;
        _applicationContext = copyFrom._applicationContext;
        _pvsBind = copyFrom.getBindPropertyValues();
        putAll(copyFrom.getExtendedProperties());
    }


    public ViewContext(ViewBackgroundInfo copyFrom)
    {
        _url = copyFrom.getURL();
        _user = copyFrom.getUser();
        _c = copyFrom.getContainer();
    }


    public ViewContext(HttpServletRequest request, HttpServletResponse response, ActionURL url)
    {
        this();
        setActionURL(url);
        setRequest(request);
        setResponse(response);

        for (Object o : _request.getParameterMap().entrySet())
        {
            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) o;
            String key = entry.getKey();
            String[] value = (String[]) entry.getValue();

            if (_properties.containsKey(key))
                continue;

            if (value.length == 1)
                put(key, value[0]);
            else
            {
                List list = new ArrayList<String>(Arrays.asList(value));
                put(key, list);
            }
        }
    }


    // Needed by background threads that call entrypoints that require ViewContexts
    // TODO: Well-behaved interfaces should not take ViewContexts -- clean up query, et al to remove ViewContext params
    @Deprecated
    public static ViewContext getMockViewContext(User user, Container c, ActionURL url, boolean pushViewContext)
    {
        ViewContext context = new ViewContext();
        context.setUser(user);
        context.setContainer(c);
        context.setActionURL(url);
        context.setBindPropertyValues(url.getPropertyValues());

        HttpServletRequest request = AppProps.getInstance().createMockRequest();
        if (request instanceof MockHttpServletRequest)
            ((MockHttpServletRequest)request).setUserPrincipal(user);
        context.setRequest(request);

        // Major hack -- QueryView needs the context pushed onto the ViewContext stack in thread local 
        if (pushViewContext)
            HttpView.initForRequest(context, request, null);

        return context;
    }


    public HttpServletRequest getRequest()
    {
        return _request;
    }


    public void setRequest(HttpServletRequest request)
    {
        _request = request;
    }


    public HttpSession getSession()
    {
        return getRequest().getSession(true);
    }


    public String getContextPath()
    {
        return null == _request ? AppProps.getInstance().getContextPath() : _request.getContextPath();
    }


    // Allows actions to override standard container permission checks; use with caution.
    public void addContextualRole(Class<? extends Role> role)
    {
        _contextualRoles.add(RoleManager.getRole(role));
    }


    public boolean hasPermission(Class<? extends Permission> perm) throws NotFoundException
    {
        User user = getUser();

        return null != user && getContainer().hasPermission(user, perm, _contextualRoles);
    }


    /**
     * True if user has agreed to terms of use, or such agreement is not required.
     * @return true if user has agreed to terms of use for this project
     */
    public boolean hasAgreedToTermsOfUse()
    {
        return !SecurityManager.isTermsOfUseRequired(this);
    }

    public HttpServletResponse getResponse()
    {
        return _response;
    }


    public void setResponse(HttpServletResponse response)
    {
        _response = response;
    }

    
    public ActionURL getActionURL()
    {
        return _url;
    }


    public AppProps getApp()
    {
        return AppProps.getInstance();
    }


    public ActionURL cloneActionURL()
    {
        return _url.clone();
    }


    public void setActionURL(ActionURL url)
    {
        _url = url;
    }

    public String getScopePrefix()
    {
        return _scopePrefix;
    }


    public User getUser()
    {
        if (_user == null)
            _user = null == _request ? null : (User) _request.getUserPrincipal();
        return _user;
    }


    public void setUser(User user)
    {
        _user = user;
    }


    public Container getContainer()
    {
        if (null == _c)
        {
            Container c = null;
            ActionURL url = getActionURL();
            if (null != url)
            {
                String path = url.getExtraPath();
                c = ContainerManager.getForPath(path);
                if (c == null)
                    c = ContainerManager.getForId(StringUtils.strip(path,"/"));
            }
            _c = c;
        }
        return _c;
    }


    /** ContainerContext */
    public Container getContainer(Map context)
    {
        return getContainer();
    }


    public void setContainer(Container c)
    {
        _c = c;
    }


    // Always returns a list, even for singleton.  Use for multiple values.
    // TODO: Return empty list instead of null
    public List<String> getList(Object key)
    {
        Object values = get(key);

        if (values == null || List.class.isAssignableFrom(values.getClass()))
            return (List<String>) values;

        if (values.getClass().isArray())
            return Arrays.asList((String[]) values);

        return Arrays.asList((String) values);
    }


    @Override
    public String toString()
    {
        return "ViewContext";
    }


    // 
    //  Error formatting (SPRING) should 
    //


    static final ResourceBundleMessageSource _defaultMessageSource = new ResourceBundleMessageSource();
    static
    {
        _defaultMessageSource.setBasenames(new String[] {
        "messages.Validation",
        "messages.Global"
        });
        _defaultMessageSource.setBundleClassLoader(ViewContext.class.getClassLoader());
    }
    List<String> _messageBundles = new ArrayList<String>();
    ResourceBundleMessageSource _messageSource = null;

    public void pushMessageBundle(String path)
    {
        _messageBundles.add(0,path);
        _messageSource = null;
    }


    public MessageSource getMessageSource()
    {
        if (_messageSource == null)
        {
            if (_messageBundles.size() == 0)
                _messageSource = _defaultMessageSource;
            else
            {
                _messageSource = new ResourceBundleMessageSource();
                _messageSource.setParentMessageSource(_defaultMessageSource);
                _messageSource.setBasenames(_messageBundles.toArray(new String[_messageBundles.size()]));
            }
        }
        return _messageSource;
    }

    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale)
    {
        return getMessageSource().getMessage(code, args, defaultMessage, locale);
    }

    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException
    {
        return getMessageSource().getMessage(code, args, locale);
    }

    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException
    {
        return getMessageSource().getMessage(resolvable, locale);
    }

    public String getMessage(MessageSourceResolvable resolvable) throws NoSuchMessageException
    {                    
        return getMessageSource().getMessage(resolvable, Locale.getDefault());
    }

    public boolean isAdminMode()
    {
        HttpSession s = getSession();
        if (null == s)
            return false;
        Boolean mode = (Boolean) getSession().getAttribute("adminMode");
        return null == mode ? false : mode.booleanValue();
    }

    public boolean isShowFolders()
    {
        String showFoldersStr = PreferenceService.get().getProperty("showFolders", getUser());
        Boolean showFolders = (Boolean) ConvertUtils.convert(showFoldersStr, Boolean.class);

        if (isAdminMode())
            return true;

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(getContainer());
        switch (laf.getFolderDisplayMode())
        {
            case ALWAYS:
                return true;
            case OPTIONAL_OFF:
            case OPTIONAL_ON:
            case IN_MENU:
                return false; //The menu bar takes care of this now...
            case ADMIN:
                return isAdminMode();
            default:
                return true;
        }
    }

    public ApplicationContext getApplicationContext()
    {
        return _applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext)
    {
        _applicationContext = applicationContext;
    }

    /* return PropertyValues object used to bind the current command object */
    public PropertyValues getBindPropertyValues()
    {
        if (null != _pvsBind)
            return _pvsBind;
        return HttpView.getBindPropertyValues();
    }

    public void setBindPropertyValues(PropertyValues pvs)
    {
        _pvsBind = pvs;
    }
}
