/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.SessionAppender;
import org.labkey.api.util.URLHelper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Must keep ActionURL in sync.
 */
public class ViewServlet extends HttpServlet
{
    private static final Logger _log = Logger.getLogger(ViewServlet.class);
    private static final boolean _debug = _log.isDebugEnabled();

    public static final String ORIGINAL_URL_STRING = "LABKEY.OriginalURL";           // String
    public static final String ORIGINAL_URL_URLHELPER = "LABKEY.OriginalURLHelper";  // URLHelper
    public static final String REQUEST_ACTION_URL = "LABKEY.RequestURL";             // ActionURL
    public static final String REQUEST_STARTTIME = "LABKEY.StartTime";
    public static final String REQUEST_UID_COUNTER = "LABKEY.Counter";                   // Incrementing counter scoped to a single request
    // useful for access log
    public static final String REQUEST_ACTION = "LABKEY.action";
    public static final String REQUEST_CONTROLLER = "LABKEY.controller";
    public static final String REQUEST_CONTAINER = "LABKEY.container";

    public static final String MOCK_REQUEST_HEADER = "X-Mock-Request";

    private static ServletContext _servletContext = null;
    private static String _serverHeader = null;

    private static final AtomicInteger _requestCount = new AtomicInteger();
    private static final ThreadLocal<Boolean> IS_REQUEST_THREAD = new ThreadLocal<Boolean>();

    private static Map<Class, String> _pageFlowClassToName = null;

    public static String getPageFlowName(Class controllerClass)
    {
        return _pageFlowClassToName.get(controllerClass);
    }

    public static int getRequestCount()
    {
        return _requestCount.get();
    }

    /**
     * Map our custom path names to ones that struts can deal with.
     * /<PathInfo>/<PageFlow>/<Action>.view --> /</PageFlow>/<Action>.do?_pathInfo=<PathInfo> .
     * NYI: special case /<Action>.view --> /action.do
     */

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        long startTime = System.currentTimeMillis();

        request.setAttribute(REQUEST_STARTTIME, startTime);
        request.setAttribute(REQUEST_UID_COUNTER, new AtomicInteger());  // Used to generate unique ids in the context of this request
        response.setHeader("Server", _serverHeader);
        HttpSession session = request.getSession(true);

        if (_debug)
        {
            User user = (User) request.getUserPrincipal();
            String description = request.getMethod() + " " + request.getRequestURI() + "?" + _toString(request.getQueryString()) + " (" + (user.isGuest() ? "guest" : user.getEmail()) + ";" + session.getId() + ")";
            _log.debug(">> " + description);
        }

        MemTracker.logMemoryUsage(_requestCount.incrementAndGet());

        SessionAppender.initThread(request);

        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && (userAgent.indexOf("Googlebot") != -1 || userAgent.indexOf("Yahoo! Slurp") != -1 || userAgent.indexOf("msnbot") != -1))
        {
            // Crawlers don't send additional requests with the same session so let them time out quickly
            session.setMaxInactiveInterval(10);
        }

        ActionURL url;
        response.setBufferSize(32768);

        try
        {
            url = requestActionURL(request);
            if (null == url)
            {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            request.setAttribute(REQUEST_ACTION, url.getAction());
            request.setAttribute(REQUEST_CONTROLLER, url.getPageFlow());
            request.setAttribute(REQUEST_CONTAINER, url.getExtraPath());
        }
        catch (RedirectException e)
        {
            ExceptionUtil.handleException(request, response, e, "Container redirect", false);
            return;
        }
        catch (Exception x)
        {
            log("ViewServlet unexpected ActionURL exception: " + request.getRequestURL(), x);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Module module = ModuleLoader.getInstance().getModuleForController(url.getPageFlow());
        if (module == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No LabKey Server module registered to handle request for controller: " + url.getPageFlow());
            return;
        }

        module.dispatch(request, response, url);

        if (_debug)
        {
            _log.debug("<< " + request.getMethod());
        }
    }


    /*
     *  Forward to the controller action defined by url
     *  acts like a GET on the provided URL
     */
    public static void forwardActionURL(HttpServletRequest request, HttpServletResponse response, ActionURL url)
            throws IOException, ServletException
    {
        Module module = ModuleLoader.getInstance().getModuleForController(url.getPageFlow());
        if (module == null)
        {
            throw new IllegalArgumentException(url.toString());
        }
        module.dispatch(new ForwardWrapper(request, url), response, url);
    }

    static class ForwardWrapper extends HttpServletRequestWrapper
    {
        final MockHttpServletRequest _mock;
        ForwardWrapper(HttpServletRequest request, ActionURL url)
        {
            super(request);
            _mock = new MockHttpServletRequest(ViewServlet.getViewServletContext(), "GET", url.getPath());
            for (Pair<String,String> p : url.getParameters())
                _mock.setParameter(p.getKey(), p.getValue());
        }
        @Override
        public String getQueryString()
        {
            return _mock.getQueryString();
        }
        public String getRequestURI()
        {
            return _mock.getRequestURI();
        }
        @Override
        public String getParameter(String s)
        {
            return _mock.getParameter(s);
        }
        @Override
        public Map getParameterMap()
        {
            return _mock.getParameterMap();
        }
        @Override
        public Enumeration getParameterNames()
        {
            return _mock.getParameterNames();
        }
        @Override
        public String[] getParameterValues(String s)
        {
            return _mock.getParameterValues(s);
        }
    }


    public static void initialize()
    {
        initializeControllerMaps();
        initializeAllSpringControllers();
        _serverHeader =  "Labkey/" + AppProps.getInstance().getLabkeyVersionString();
    }


    // Would rather let controllers initialize lazily, but this is problematic when code other than the controller refers
    // to an action class (e.g., new ActionURL(Class<? extends Controller>, Container)).  In particular, action classes
    // defined outside the controller have no idea who their controller is until the controller initializes.  Instead of
    // initializing everything at startup we could restrict the use of ActionURL(Class, Container), et al, to methods
    // within a controller... which would guarantee that the controller was initialized before resolving the action class.
    private static void initializeAllSpringControllers()
    {
        for (Module module : ModuleLoader.getInstance().getModules())
        {
            for (Class controllerClass : module.getPageFlowClassToName().keySet())
            {
                if (Controller.class.isAssignableFrom(controllerClass))
                {
                    try
                    {
                        getController(module, controllerClass);
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }


    private static void initializeControllerMaps()
    {
        _pageFlowClassToName = new HashMap<Class, String>();

        for (Module module : ModuleLoader.getInstance().getModules())
            _pageFlowClassToName.putAll(module.getPageFlowClassToName());
    }


    public static Controller getController(Module module, Class controllerClass) throws IllegalAccessException, InstantiationException
    {
        if (module instanceof SpringModule)
            return ((SpringModule)module).getController(null, controllerClass);
        else
            return (Controller)controllerClass.newInstance();
    }


    private ActionURL requestActionURL(HttpServletRequest request)
            throws ServletException
    {
        // does not reduce need to validate form parameters which may be posted
        if (!validChars(request))
            return null;

        ActionURL url = new ActionURL(request);
        assert validChars(url);

        // canonicalize container
        Path path = url.getParsedPath();
        Container c = ContainerManager.getForPath(path);

        // We support two types of permanent link encoding for containers:
        // 1) for backwards compatibility, long container ids (37 chars long, including the first "/"
        // 2) shorter row ids, starting with "/__r".
        if (null == c && path.size() == 1)
        {
            String name = path.getName();
            if (name.length()==36)
                c = ContainerManager.getForId(path.getName());
            if (null == c && name.startsWith("__r"))
            {
                try
                {
                    c = ContainerManager.getForRowId(Integer.parseInt(path.getName().substring(3)));
                }
                catch (NumberFormatException e)
                {
                    // Continue to try other ways to look up the container
                }
            }
        }
        if (null == c)
        {
            String strPath = path.toString();
            if (!strPath.startsWith("/"))
                strPath = "/" + strPath;
            if (strPath.endsWith("/"))
                strPath = strPath.substring(0, strPath.length() - 1);
            c = ContainerManager.getForPathAlias(strPath);
            if (c != null)
            {
                url.setContainer(c);
                if (request.getMethod().equals("GET"))
                    throw new RedirectException(url.getLocalURIString());
            }
        }

        if (null != c)
            url.setContainer(c);

        // lastfilter
        boolean expandLastFilter = ColumnInfo.booleanFromString(url.getParameter(DataRegion.LAST_FILTER_PARAM));

        if (expandLastFilter)
        {
            ActionURL expand = (ActionURL) request.getSession(true).getAttribute(url.getPath() + "#" + DataRegion.LAST_FILTER_PARAM);
            if (null != expand)
            {
                // any parameters on the URL override those stored in the last filter:
                for (Pair<String, String> parameter : url.getParameters())
                {
                    // don't add the .lastFilter parameter (or we'll have an infinite redirect):
                    if (!DataRegion.LAST_FILTER_PARAM.equals(parameter.getKey()))
                        expand.replaceParameter(parameter.getKey(), parameter.getValue());
                }
                if (request.getMethod().equals("GET"))
                    throw new RedirectException(expand.getLocalURIString());
                _log.error(DataRegion.LAST_FILTER_PARAM + " not supported for " + request.getMethod());
            }
        }

        url.setReadOnly();
        return url;
    }


    public static HttpServletRequest mockRequest(String method, ActionURL url, User user, Map<String, Object> headers, String postData)
    {
        MockRequest request = new MockRequest(getViewServletContext(), method, url);

        AppProps props = AppProps.getInstance();
        request.setContextPath(props.getContextPath());
        request.setServerPort(props.getServerPort());
        request.setServerName(props.getServerName());
        request.setScheme(props.getScheme());
        request.addHeader(MOCK_REQUEST_HEADER, true);

        if (user != null)
            request.setUserPrincipal(user);

        if (headers != null)
        {
            for (String header : headers.keySet())
            {
                if (header.equals("Content-Type"))
                    request.setContentType((String)headers.get(header));
                request.addHeader(header, headers.get(header));
            }
        }

        if (method.equals("POST") && postData != null)
            request.setContent(postData.getBytes(Charset.forName("UTF-8")));

        return request;

    }

    public static class MockRequest extends MockHttpServletRequest
    {
        private ActionURL _actionURL;

        public MockRequest()
        {
            super();
        }

        public MockRequest(ServletContext servletContext)
        {
            super(servletContext);
        }

        public MockRequest(String method, ActionURL actionURL)
        {
            super(method, actionURL.getURIString());
            _actionURL = actionURL;
        }

        public MockRequest(ServletContext servletContext, String method, ActionURL actionURL)
        {
            super(servletContext, method, actionURL.getURIString());
            _actionURL = actionURL;
        }

        public ActionURL getActionURL()
        {
            return _actionURL;
        }

        public void setActionURL(ActionURL actionURL)
        {
            setRequestURI(actionURL.getURIString());
            _actionURL = actionURL;
        }

        @Override
        public Map getParameterMap()
        {
            return _actionURL.getParameterMap();
        }

        @Override
        public String[] getParameterValues(String name)
        {
            return _actionURL.getParameters(name);
        }

        @Override
        public Enumeration getParameterNames()
        {
            return _actionURL.getParameterNames();
        }
    }


    public static MockHttpServletResponse GET(ActionURL url, User user, Map<String, Object> headers)
            throws Exception
    {
        HttpServletRequest request = mockRequest("GET", url, user, headers, null);
        return mockDispatch(request, null);
    }

    public static MockHttpServletResponse POST(ActionURL url, User user, Map<String, Object> headers, String postData)
            throws Exception
    {
        HttpServletRequest request = mockRequest("POST", url, user, headers, postData);
        return mockDispatch(request, null);
    }


    public static MockHttpServletResponse mockDispatch(HttpServletRequest request, final String requiredContentType)
            throws Exception
    {
        if (!("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())))
            throw new IllegalArgumentException(request.getMethod());

        ActionURL url;
        if (request instanceof MockRequest)
            url = ((MockRequest)request).getActionURL().clone();
        else
            url = new ActionURL(request.getRequestURI());

        String path = url.getExtraPath();
        Container c = ContainerManager.getForPath(path);
        if (null == c)
            c = ContainerManager.getForId(StringUtils.strip(path,"/"));
        if (null != c)
            url.setExtraPath(c.getPath());
        url.setReadOnly();

        MockHttpServletResponse mockResponse = new MockHttpServletResponse()
        {
            @Override
            public void setContentType(String s)
            {
                if (null != requiredContentType && !s.startsWith(requiredContentType))
                    throw new IllegalStateException(s);
                super.setContentType(s);
                setHeader("Content-Type", s);
            }
        };

        try
        {
            Module module = ModuleLoader.getInstance().getModuleForController(url.getPageFlow());
            if (module == null)
            {
                throw new NotFoundException("Unknown controller: " + url.getPageFlow());
            }
            module.dispatch(request, mockResponse, url);
            return mockResponse;
        }
        catch (ServletException x)
        {
            _logError("error", x);
            throw x;
        }
        catch (IOException x)
        {
            _logError("error", x);
            throw x;
        }
        catch (Exception x)
        {
            _logError("error", x);
            throw new ServletException(x);
        }
    }


    public class ViewResponseWrapper extends HttpServletResponseWrapper
    {
        String contentType = "text/html";

        ViewResponseWrapper(HttpServletResponse response)
        {
            super(response);
        }


        public void setContentType(String s)
        {
            contentType = s;
            super.setContentType(s);
        }
    }


    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        _servletContext = config.getServletContext();
        String realPath = config.getServletContext().getRealPath("/");
        _log.info("ViewServlet initialized");
        _log.info("   WEBAPP: " + realPath);
        _log.info("     PATH: " + System.getenv("PATH"));
    }


    public static String getOriginalURL()
    {
        // using HttpView to remember the root request, rather than have our own ThreadLocal
        HttpServletRequest request = HttpView.getRootContext().getRequest();
        if (null == request)
            return null;
        Object url = request.getAttribute(ORIGINAL_URL_STRING);
        if (null == url)
            url = request.getRequestURI() + "?" + StringUtils.trimToEmpty(request.getQueryString());
        return String.valueOf(url);
    }
    

    public static ActionURL getRequestURL()
    {
        // using HttpView to remember the root request, rather than have our own ThreadLocal
        ViewContext ctx = HttpView.getRootContext();
        if (ctx == null)
            return null;

        HttpServletRequest request = ctx.getRequest();
        if (request == null)
            return null;

        return (ActionURL) request.getAttribute(REQUEST_ACTION_URL);
    }


    public static void setAsRequestThread()
    {
        // Would rather not use thread local for this, but other HttpView/ViewServlet settings get set later and are
        //  awkward to use.
        IS_REQUEST_THREAD.set(true);
    }

    // Returns true if the current thread is a request thread, false if it's a background thread
    public static boolean isRequestThread()
    {
        return Boolean.TRUE.equals(IS_REQUEST_THREAD.get());
    }


    public static long getRequestStartTime(HttpServletRequest request)
    {
        Long ms = (Long)request.getAttribute(REQUEST_STARTTIME);
        return ms == null ? 0 : ms.longValue();
    }


    public static void ensureViewServlet(HttpServletResponse response)
    {
        try
        {
            HttpView.getRootContext();
        }
        catch (ArrayIndexOutOfBoundsException x)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            throw new NotFoundException();
        }
    }


    public static ServletContext getViewServletContext()
    {
        return _servletContext;
    }


    public static void _logError(String s, Throwable t)
    {
        _log.error(s, t);
        _servletContext.log(s, t);
    }


    public static void _log(String s, Throwable t)
    {
        _logError(s, t);
    }

    private String _toString(Object o)
    {
        return o == null ? "" : String.valueOf(o);
    }


    static boolean[] legal = new boolean[256];
    static
    {
        for (char ch=0 ; ch<256 ; ch++)
            legal[ch] = Character.isWhitespace(ch) || !Character.isISOControl(ch);
    }

    public static boolean validChar(int ch)
    {
        // 0xFFFD is sometimes used as a substitute for an illegal sequence
        return ch < 256 ? legal[ch] : ch != 0xFFFD && Character.isDefined(ch) && (Character.isWhitespace(ch) || !Character.isISOControl(ch));
    }

    public static boolean validChars(CharSequence s)
    {
        if (null == s)
            return true;
        for (int i=0 ; i<s.length() ; i++)
            if (!validChar(s.charAt(i)))
                return false;
        return true;
    }

    private boolean validChars(URLHelper url) throws ServletException
    {
        Path path = url.getParsedPath();
        for (int i=0 ; i<path.size() ; i++)
        {
            if (!validChars(path.get(i)))
                return false;
        }
        for (Pair<String,String> p : url.getParameters())
        {
            if (!validChars(p.getKey()))
                return false;
            if (!validChars(p.getValue()))
               return false;
        }
        return true;
    }


    private boolean validChars(HttpServletRequest r)
    {
        try
        {
            // validate original and decoded
            if (!validChars(r.getRequestURI()))
                return false;
            if (!validChars(r.getServletPath()))
                return false;
            if (!validChars(r.getQueryString()))
                return false;
            if (!validChars(PageFlowUtil.decode(r.getQueryString())))
                return false;
        }
        catch (IllegalArgumentException x)
        {
            return false;
        }
        return true;
    }

    /**
     * Replace any invalid characters with the unicode inverted question mark.
     * @param s Character sequence to be fixed.
     * @return The fixed String.
     */
    public static String replaceInvalid(CharSequence s)
    {
        int len = s.length();
        StringBuilder sb = new StringBuilder(len);

        for (int i = 0; i < len; i++)
        {
            char c = s.charAt(i);
            if (validChar(c))
                sb.append(c);
            else
                sb.append('\u00BF'); // inverted question mark
        }

        return sb.toString();
    }
}
