/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.AllowedBeforeInitialUserIsSet;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlViewDefinition;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SimpleAction;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.template.BodyTemplate;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.api.view.template.WizardTemplate;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * User: matthewb
 * Date: May 17, 2007
 * Time: 10:17:36 AM
 *
 * CONSIDER using DispatchServlet instead of Controller here, or perhaps make the Module expose a DispatchServlet
 *
 * This class acts pretty much as DispatchServlet.  However, it does not follow all the rules/conventions of DispatchServlet.
 * Whenever a discrepency is found that someone cares about, please go ahead and make a change in the direction of better
 * compatibility. 
 */
public abstract class SpringActionController implements Controller, HasViewContext, ViewResolver, ApplicationContextAware
{
    // This is a prefix to indicate that a field is present on a form
    // For instance for checkboxes (with checkbox name = 'myField' use hidden name="@myField"
    // if you change this, change labkey.js (LABKEY.fieldMarker) as well
    public static final String FIELD_MARKER = "@";

    // common error codes
    public static final String ERROR_MSG = null;
    /** Use this error code only when no further error message is available. */
    public static final String ERROR_GENERIC = "GenericError";
    public static final String ERROR_CONVERSION = "typeMismatch";
    public static final String ERROR_REQUIRED = "requiredError";
    public static final String ERROR_UNIQUE = "uniqueConstraint";

    private static final Map<Class<? extends Controller>, ActionDescriptor> _classToDescriptor = new HashMap<>();

    private static final Logger _log = Logger.getLogger(SpringActionController.class);

    public void setActionResolver(ActionResolver actionResolver)
    {
        _actionResolver = actionResolver;
    }

    public ActionResolver getActionResolver()
    {
        return _actionResolver;
    }

    private static void registerAction(ActionDescriptor ad)
    {
        _classToDescriptor.put(ad.getActionClass(), ad);
    }

    @NotNull
    private static ActionDescriptor getActionDescriptor(Class<? extends Controller> actionClass)
    {
        ActionDescriptor ad = _classToDescriptor.get(actionClass);

        if (null == ad)
            throw new IllegalStateException("Action class '" + actionClass + "' has not been registered with a controller");

        return ad;
    }

    public static String getControllerName(Class<? extends Controller> actionClass)
    {
        return getActionDescriptor(actionClass).getControllerName();
    }

    public static String getActionName(Class<? extends Controller> actionClass)
    {
        return getActionDescriptor(actionClass).getPrimaryName();
    }

    // I don't think there is an interface for this
    public interface ActionResolver
    {
        Controller resolveActionName(Controller actionController, String actionName);
        void addTime(Controller action, long elapsedTime);
        Collection<ActionDescriptor> getActionDescriptors();
    }

    public interface ActionDescriptor
    {
        String getControllerName();
        String getPrimaryName();
        List<String> getAllNames();
        Class<? extends Controller> getActionClass();
        Controller createController(Controller actionController);

        void addTime(long time);
        ActionStats getStats();
    }

    public interface ActionStats
    {
        long getCount();
        long getElapsedTime();
        long getMaxTime();
    }

    ApplicationContext _applicationContext = null;
    ActionResolver _actionResolver;
    ViewContext _viewContext;


    public SpringActionController()
    {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        _applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext()
    {
        return _applicationContext;
    }

    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
    }

    public ViewContext getViewContext()
    {
        return _viewContext;
    }

    public boolean isPost()
    {
        return "POST".equalsIgnoreCase(getViewContext().getRequest().getMethod());
    }

    protected Container getContainer()
    {
        return getViewContext().getContainer();
    }

    protected User getUser()
    {
        return getViewContext().getUser();
    }

    protected void requiresLogin() throws ServletException
    {
        if (getUser().isGuest())
        {
            throw new UnauthorizedException();
        }
    }
    
    protected ViewBackgroundInfo getViewBackgroundInfo()
    {
        ViewContext vc = getViewContext();
        return new ViewBackgroundInfo(vc.getContainer(), vc.getUser(), vc.getActionURL());
    }

    public PageConfig defaultPageConfig()
    {
        PageConfig page = new PageConfig();

        HttpServletRequest request = getViewContext().getRequest();
        if (null != StringUtils.trimToNull(request.getParameter("_print")) ||
            null != StringUtils.trimToNull(request.getParameter("_print.x")))
            page.setTemplate(PageConfig.Template.Print);
        if (null != StringUtils.trimToNull(request.getParameter("_frame")) ||
            null != StringUtils.trimToNull(request.getParameter("_frame.x")))
            page.setTemplate(PageConfig.Template.Framed);
        if (null != StringUtils.trimToNull(request.getParameter("_template")))
        {
            try
            {
                PageConfig.Template template =
                        PageConfig.Template.valueOf(StringUtils.trimToNull(request.getParameter("_template")));
                page.setTemplate(template);
            }
            catch (IllegalArgumentException ex)
            {
                _log.debug("Illegal page template type", ex);
            }
        }
        return page;
    }


    public View resolveViewName(String viewName, Locale locale) throws Exception
    {
        if (null != _applicationContext)
        {
            // WWSD (what would spring do)
        }

        return HttpView.viewFromString(viewName);
    }
    

    /** returns an uninitialized instance of the named action */
    public Controller resolveAction(String name)
    {
        name = StringUtils.trimToNull(name);
        if (null == name)
            return null;

        Controller c = null;
        if (null != _actionResolver)
            c = _actionResolver.resolveActionName(this, name);

        if (null != _applicationContext)
        {
            // WWSD (what would spring do)
        }
        return c;
   }


    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, getApplicationContext());
        _viewContext.setApplicationContext(_applicationContext);

        Throwable throwable = null;

        String contentType = request.getContentType();
        if (null != contentType && contentType.startsWith("multipart"))
        {
            request = (new CommonsMultipartResolver()).resolveMultipart(request);
            // ViewServlet doesn't check validChars for parameters in a multipart request, so check again
            if (!ViewServlet.validChars(request))
            {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Illegal characters in request body");
                return null;
            }
        }

        ViewContext context = getViewContext();
        context.setRequest(request);
        context.setResponse(response);

        ActionURL url = context.getActionURL();
        long startTime = System.currentTimeMillis();
        Controller action = null;

        try
        {
            action = resolveAction(url.getAction());
            if (null == action)
            {
                throw new NotFoundException("Unable to find action '" + url.getAction() + "' to handle request in controller '" + url.getController() + "'");
            }

            ActionURL redirectURL = getUpgradeMaintenanceRedirect(request, action);

            if (null != redirectURL)
            {
                _log.debug("URL " + url.toString() + " was redirected to " + redirectURL + " instead");
                response.sendRedirect(redirectURL.toString());
                return null;
            }

            PageConfig pageConfig = defaultPageConfig();

            if (action instanceof HasViewContext)
                ((HasViewContext)action).setViewContext(context);
            if (action instanceof HasPageConfig)
                ((HasPageConfig)action).setPageConfig(pageConfig);

            if (action instanceof PermissionCheckable)
            {
                ((PermissionCheckable)action).checkPermissions();
            }
            else
            {
                BaseViewAction.checkPermissionsAndTermsOfUse(action.getClass(), context, null);
            }

            beforeAction(action);
            ModelAndView mv = action.handleRequest(request, response);
            if (mv != null)
            {
                if (mv.getView() instanceof RedirectView)
                {
                    // treat same as a throw redirect
                    throw new RedirectException(((RedirectView)mv.getView()).getUrl());
                }
                renderInTemplate(context, action, pageConfig, mv);
            }
        }
        catch (HttpRequestMethodNotSupportedException x)
        {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            throwable = x;
        }
        catch (Throwable x)
        {
            handleException(request, response, x);
            throwable = x;
        }
        finally
        {
            afterAction(throwable);

            if (null != action)
                _actionResolver.addTime(action, System.currentTimeMillis() - startTime);
        }

        return null;
    }


    protected void handleException(HttpServletRequest request, HttpServletResponse response, Throwable x)
    {
        ActionURL errorURL = ExceptionUtil.handleException(request, response, x, null, false);
        if (null != errorURL)
            ExceptionUtil.doErrorRedirect(response, errorURL.toString());
    }


    public static ActionURL getUpgradeMaintenanceRedirect(HttpServletRequest request, Controller action)
    {
        if (UserManager.hasNoUsers())
        {
            // Let the "initial user" view & post, stylesheet & javascript actions, etc. through... otherwise redirect to initial user action
            if (action.getClass().isAnnotationPresent(AllowedBeforeInitialUserIsSet.class))
                return null;
            else
                return PageFlowUtil.urlProvider(LoginUrls.class).getInitialUserURL();
        }

        boolean upgradeRequired = ModuleLoader.getInstance().isUpgradeRequired();
        boolean startupComplete = ModuleLoader.getInstance().isStartupComplete();
        boolean maintenanceMode = AppProps.getInstance().isUserRequestedAdminOnlyMode();

        if (upgradeRequired || !startupComplete || maintenanceMode)
        {
            boolean actionIsAllowed = (null != action && action.getClass().isAnnotationPresent(AllowedDuringUpgrade.class));

            if (null != action)
                _log.debug("Action " + action.getClass() + " allowed: " + actionIsAllowed);

            if (!actionIsAllowed)
            {
                User user = (User)request.getUserPrincipal();

                // Don't redirect the indexer... let it get the page content, #12042 and #11345
                if (user.isSearchUser())
                    return null;

                URLHelper returnURL = null;
                try
                {
                    StringBuilder url = new StringBuilder(request.getRequestURL().toString());
                    if (request.getQueryString() != null)
                    {
                        url.append("?");
                        url.append(request.getQueryString());
                    }
                    returnURL = new URLHelper(url.toString());
                }
                catch (URISyntaxException e)
                {
                    // ignore
                }

                if (!user.isSiteAdmin())
                {
                    return PageFlowUtil.urlProvider(AdminUrls.class).getMaintenanceURL(returnURL);
                }
                else if (upgradeRequired || !startupComplete)
                {
                    return PageFlowUtil.urlProvider(AdminUrls.class).getModuleStatusURL(returnURL);
                }
            }
        }

        return null;
    }

    protected void renderInTemplate(ViewContext context, Controller action, PageConfig page, ModelAndView mv)
            throws Exception
    {
        View view = resolveView(mv);
        mv.setView(view);

        if (mv instanceof HttpView)
            page.addClientDependencies(((HttpView)mv).getClientDependencies());

        ModelAndView template = getTemplate(context, mv, action, page);

        ModelAndView render = template == null ? mv : template;
        render.getView().render(render.getModel(), context.getRequest(), context.getResponse());
    }


    protected ModelAndView getTemplate(ViewContext context, ModelAndView mv, Controller action, PageConfig page)
    {
        switch (page.getTemplate())
        {
            case None:
            {
                return null;
            }
            case Framed:
            case Print:
            {
                NavTree root = new NavTree();
                appendNavTrail(action, root);
                if (root.hasChildren() && page.getTitle() == null)
                {
                    NavTree[] children = root.getChildren();
                    page.setTitle(children[children.length-1].getText());
                }
                return new PrintTemplate(mv, page);
            }
            case Dialog:
            {
                return new DialogTemplate(mv, page);
            }
            case Wizard:
            {
                return new WizardTemplate(mv, page);
            }
            case Body:
            {
                return new BodyTemplate(mv, page);
            }
            case Home:
            default:
            {
                NavTree root = new NavTree();
                appendNavTrail(action, root);
                if (root.hasChildren() && page.getTitle() == null)
                {
                    NavTree[] children = root.getChildren();
                    page.setTitle(children[children.length-1].getText());
                }
                HomeTemplate template = new HomeTemplate(context, context.getContainer(), mv, page, root.getChildren());
                return template;
            }
        }
    }


    View resolveView(ModelAndView mv) throws Exception
    {
        View view;
        if (mv.isReference())
        {
            // We need to resolve the view name.
            view = resolveViewName(mv.getViewName(), Locale.getDefault());

            if (view == null)
            {
                throw new ServletException("Could not resolve view with name '" + mv.getViewName() + "' in controller " + this.getClass().getName());
            }
        }
        else
        {
            // No need to lookup: the ModelAndView object contains the actual View object.
            view = mv.getView();
            if (view == null)
            {
                throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a View object");
            }
        }
        return view;
    }



    protected void appendNavTrail(Controller action, NavTree root)
    {
        if (action instanceof NavTrailAction)
        {
            ((NavTrailAction)action).appendNavTrail(root);
        }
    }


    protected void beforeAction(Controller action)
    {
    }


    protected void afterAction(Throwable t)
    {
    }

    public static abstract class BaseActionDescriptor implements ActionDescriptor
    {
        private long _count = 0;
        private long _elapsedTime = 0;
        private long _maxTime = 0;

        synchronized public void addTime(long time)
        {
            _count++;
            _elapsedTime += time;

            if (time > _maxTime)
                _maxTime = time;
        }

        synchronized public ActionStats getStats()
        {
            return new BaseActionStats(_count, _elapsedTime, _maxTime);
        }

        // Immutable stats holder to eliminate external synchronization needs
        private class BaseActionStats implements ActionStats
        {
            private final long _count;
            private final long _elapsedTime;
            private final long _maxTime;

            private BaseActionStats(long count, long elapsedTime, long maxTime)
            {
                _count = count;
                _elapsedTime = elapsedTime;
                _maxTime = maxTime;
            }

            public long getCount()
            {
                return _count;
            }

            public long getElapsedTime()
            {
                return _elapsedTime;
            }

            public long getMaxTime()
            {
                return _maxTime;
            }
        }
    }

    public static class HTMLFileActionResolver implements ActionResolver
    {
        public static final String VIEWS_DIRECTORY = "views";

        private Map<String, ActionDescriptor> _nameToDescriptor;
        private final String _controllerName;

        public HTMLFileActionResolver(String controllerName)
        {
            _nameToDescriptor = new HashMap<>();
            _controllerName = controllerName;
        }

        public void addTime(Controller action, long elapsedTime)
        {
            /* Never called */
        }        

        public Controller resolveActionName(Controller actionController, String actionName)
        {
            if(_nameToDescriptor.get(actionName) != null)
            {
                return _nameToDescriptor.get(actionName).createController(actionController);
            }

            Module module = ModuleLoader.getInstance().getModuleForController(_controllerName);

            Resource r = (module == null) ? null : module.getModuleResource("/" + VIEWS_DIRECTORY + "/" + actionName + ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION);
            if (r == null || !r.isFile())
            {
                return null;
            }

            HTMLFileActionDescriptor htmlDescriptor = createFileActionDescriptor(actionName, r);
            _nameToDescriptor.put(actionName, htmlDescriptor);
            registerAction(htmlDescriptor);

            return htmlDescriptor.createController(actionController);
        }

        protected HTMLFileActionDescriptor createFileActionDescriptor(String actionName, Resource r)
        {
            return new HTMLFileActionDescriptor(actionName, r);
        }

        protected class HTMLFileActionDescriptor extends BaseActionDescriptor
        {
            private final String _primaryName;
            private final List<String> _allNames;
            protected final Resource _resource;

            protected HTMLFileActionDescriptor(String primaryName, Resource resource)
            {
                _primaryName = primaryName;
                _resource = resource;
                _allNames = Arrays.asList(_primaryName);
            }

            public String getControllerName()
            {
                return _controllerName;
            }

            public String getPrimaryName()
            {
                return _primaryName;
            }

            public List<String> getAllNames()
            {
                return _allNames;
            }

            public Class<? extends Controller> getActionClass()
            {
                // This is an HTML View so it does not have a class.
                //return null;
                return SimpleAction.class;
            }

            public Controller createController(Controller actionController)
            {
                return new SimpleAction(_resource);
            }
        }

        // WARNING: This might not be thread safe.
        public Collection<ActionDescriptor> getActionDescriptors()
        {
            return _nameToDescriptor.values();
        }
        
        public ActionDescriptor getActionDescriptor(String actionName)
        {
            return _nameToDescriptor.get(actionName);
        }
    }


    public static class DefaultActionResolver implements ActionResolver
    {
        private final Class<? extends Controller> _outerClass;
        private final String _controllerName;
        //private final Map<String, ActionDescriptor> _nameToDescriptor;
        private final Map<String, ActionDescriptor> _nameToDescriptor;
        private HTMLFileActionResolver _htmlResolver;

        public DefaultActionResolver(Class<? extends Controller> outerClass, Class<? extends Controller>... otherClasses)
        {
            _outerClass = outerClass;
            _controllerName = ViewServlet.getControllerName(_outerClass);
            _htmlResolver = null; // This gets loaded if file-based actions are used.

            Map<String, ActionDescriptor> nameToDescriptor = new CaseInsensitiveHashMap<>();

            // Add all concrete inner classes of this controller
            addInnerClassActions(nameToDescriptor, _outerClass);

            // Add all actions that were passed in
            for (Class<? extends Controller> actionClass : otherClasses)
                addAction(nameToDescriptor, actionClass);

            _nameToDescriptor = nameToDescriptor;
        }

        private void addInnerClassActions(Map<String, ActionDescriptor> nameToDescriptor, Class<? extends Controller> outerClass)
        {
            Class[] innerClasses = outerClass.getDeclaredClasses();

            for (Class innerClass : innerClasses)
                if (Controller.class.isAssignableFrom(innerClass) && !Modifier.isAbstract(innerClass.getModifiers()))
                    addAction(nameToDescriptor, innerClass);
        }

        private void addAction(Map<String, ActionDescriptor> nameToDescriptor, Class<? extends Controller> actionClass)
        {
            try
            {
                ActionDescriptor ad = new DefaultActionDescriptor(actionClass);

                for (String name : ad.getAllNames())
                {
                    ActionDescriptor existingDescriptor = nameToDescriptor.put(name, ad);
                    if (existingDescriptor != null)
                    {
                        throw new IllegalStateException("Duplicate action name " + name + " registered for " + ad.getActionClass() + " and " + existingDescriptor.getActionClass());
                    }
                }

                registerAction(ad);
            }
            catch (Exception e)
            {
                // Too early to log to mothership
                _log.error("Exception while registering action class", e);
            }
        }


        public Controller resolveActionName(Controller actionController, String name)
        {
            ActionDescriptor ad;
            synchronized (_nameToDescriptor)
            {
                ad = _nameToDescriptor.get(name);
            }

            if (ad == null)
            {
                // Check if this action is described in the file-based action directory
                return resolveHTMLActionName(actionController, name);
            }

            return ad.createController(actionController);
        }


        public void addTime(Controller action, long elapsedTime)
        {
            getActionDescriptor(action.getClass()).addTime(elapsedTime);
        }


        private class DefaultActionDescriptor extends BaseActionDescriptor
        {
            private final Class<? extends Controller> _actionClass;
            private final Constructor _con;
            private final String _primaryName;
            private final List<String> _allNames;

            private DefaultActionDescriptor(Class<? extends Controller> actionClass) throws ServletException
            {
                if (actionClass.getConstructors().length == 0)
                    throw new ServletException(actionClass.getName() + " has no public constructors");

                _actionClass = actionClass;

                // @ActionNames("name1, name2") annotation overrides default behavior of using class name to generate name
                ActionNames actionNames = actionClass.getAnnotation(ActionNames.class);

                _allNames = (null != actionNames ? initializeNames(actionNames.value().split(",")) : initializeNames(getDefaultActionName()));
                _primaryName = _allNames.get(0);

                Constructor con = null;

                if (_outerClass != null)
                {
                    try
                    {
                        con = actionClass.getConstructor(_outerClass);
                    }
                    catch (NoSuchMethodException x)
                    {
                        /* */
                    }
                }

                try
                {
                    _con = (null != con ? con : actionClass.getConstructor());
                }
                catch (NoSuchMethodException x)
                {
                    throw new RuntimeException("Zero-argument constructor not found for " + actionClass.getName(), x);
                }
            }

            private List<String> initializeNames(String... names)
            {
                List<String> list = new ArrayList<>(names.length);
                for (String name : names)
                    list.add(name.trim());
                return list;
            }

            public Class<? extends Controller> getActionClass()
            {
                return _actionClass;
            }

            public Controller createController(Controller actionController)
            {
                try
                {
                    if (_con.getParameterTypes().length == 1)
                        return (Controller)_con.newInstance(actionController);
                    else
                        return (Controller)_con.newInstance();
                }
                catch (IllegalAccessException e)
                {
                    _log.error("unexpected error", e);
                    throw new RuntimeException(e);
                }
                catch (InstantiationException e)
                {
                    _log.error("unexpected error", e);
                    throw new RuntimeException(e);
                }
                catch (InvocationTargetException e)
                {
                    _log.error("unexpected error", e);
                    throw new RuntimeException(e);
                }

            }

            public String getControllerName()
            {
                return _controllerName;
            }

            public String getPrimaryName()
            {
                return _primaryName;
            }

            public List<String> getAllNames()
            {
                return _allNames;
            }

            private String getDefaultActionName()
            {
                String name = _actionClass.getName();
                name = name.substring(name.lastIndexOf(".")+1);
                name = name.substring(name.lastIndexOf("$")+1);
                if (name.endsWith("Action"))
                    name = name.substring(0,name.length()-"Action".length());
                if (name.endsWith("Controller"))
                    name = name.substring(0,name.length()-"Controller".length());
                name = name.substring(0,1).toLowerCase() + name.substring(1);

                return name;
            }
        }


        private Controller resolveHTMLActionName(Controller actionController, String actionName)
        {
            if(_htmlResolver == null)
            {
                _htmlResolver = getHTMLFileActionResolver();
            }

            Controller thisActionsController = _htmlResolver.resolveActionName(actionController, actionName);

            if (thisActionsController != null)
            {
                synchronized (_nameToDescriptor)
                {
                    // The HTMLFileResolver registers the action
                    _nameToDescriptor.put(actionName, _htmlResolver.getActionDescriptor(actionName));
                }
            }

            return thisActionsController;
        }

        protected HTMLFileActionResolver getHTMLFileActionResolver()
        {
            return new HTMLFileActionResolver(_controllerName);
        }
        
        public Collection<ActionDescriptor> getActionDescriptors()
        {
            synchronized (_nameToDescriptor)
            {
                return new ArrayList<>(_nameToDescriptor.values());
            }
        }
    }
}
