/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.api.security;

import org.apache.log4j.Category;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HString;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * User: matthewb
 * Date: Feb 5, 2009
 * Time: 8:51:07 AM
 */
public class AuthenticatedRequest extends HttpServletRequestWrapper
{
    private static Category _log = Logger.getInstance(AuthenticatedRequest.class);
    private final User _user;
    boolean _loggedIn = false;
    private boolean _forceRealSession = false;
    private HttpSession _session = null;


    public AuthenticatedRequest(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull User user)
    {
        super(request instanceof AuthenticatedRequest ? (HttpServletRequest)((AuthenticatedRequest)request).getRequest() : request);
        _user = null == user ? User.guest : user;
        _loggedIn = !_user.isGuest();
    }


    public Principal getUserPrincipal()
    {
        return _user;
    }


    /*
     * for login.post the session belongs to guest, but after login
     * we no longer want to use a guest session.  setAuthenticatedUsers() uses
     * this as a side channel to help make this work property
     *
     * CONSIDER implement copySessionAttributes parameter, currently the session is always
     * invalidated on login anyway
     */
    public void convertToLoggedInSession()
    {
        removeAttribute(GuestSession.class.getName());
        // don't treat like guest on next getSession call
        _loggedIn = true;
    }


    @Override
    public HttpSession getSession()
    {
        return this.getSession(true);
    }


    /**
     * We always return a wrapped session of some sort, makes it easier to set breakpionts
     * or add add logging.
     *
     * @param create
     * @return
     */
    @Override
    public HttpSession getSession(boolean create)
    {
        if (null != _session && isValid(_session))
            return _session;

        if (!create)
        {
            HttpSession servletContainerSession = super.getSession(false);
            if (null == servletContainerSession)
                return null;
        }

        HttpSession s = !isGuest() ? makeRealSession() : isRobot() ? robotSession() : guestSession();
        if (_log.isDebugEnabled() && !(s instanceof SessionWrapper))
            s = new SessionWrapper(s);
        _session = s;
        return _session;
    }


    public static boolean isValid(HttpSession s)
    {
        try
        {
            if (s instanceof SessionWrapper && !((SessionWrapper) s).isValid())
                return false;
            s.getCreationTime();
            return true;
        }
        catch (IllegalStateException x)
        {
            return false;
        }
    }


    private HttpSession makeRealSession()
    {
        try
        {
            HttpSession s = AuthenticatedRequest.super.getSession(true);
            if (null == s)
                return null;
            if (s.isNew())
            {
                _log.debug("Created HttpSession: " + s.getId() + " " + _user.getEmail());
            }
            return s;
        }
        catch (Exception x)
        {
            ExceptionUtil.logExceptionToMothership(AuthenticatedRequest.this, x);
            return null;
        }
    }


    private boolean isGuest()
    {
        return _user.isGuest() && !_loggedIn;
    }

    private boolean isRobot()
    {
        return  PageFlowUtil.isRobotUserAgent(getHeader("User-Agent"));
    }


    // TODO: Delete
    public HString getParameter(HString s)
    {
        return new HString(super.getParameter(s.getSource()), true);
    }


    // TODO: Delete
    public HString[] getParameterValues(HString s)
    {
        String[] values = getParameterValues(s.getSource());
        if (values == null)
            return null;
        HString[] t  = new HString[values.length];
        for (int i=0 ; i<values.length ; i++)
            t[i] = new HString(values[i], true);
        return t;
    }

    // Methods below use reflection to pull Tomcat-specific implementation bits out of the request.  This can be helpful
    // for low-level, temporary debugging, but it's not portable across servlet containers or versions.

    public HttpServletRequest getInnermostRequest() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException
    {
        Object innerRequest = invokeMethod(this, HttpServletRequestWrapper.class, "_getHttpServletRequest");
        return (HttpServletRequest)getFieldValue(innerRequest, "request");
    }

    public Map getAttributeMap() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        return (Map)getFieldValue(getInnermostRequest(), "attributes");
    }

    // Uses reflection to access public or private fields by name.
    private Object getFieldValue(Object o, String fieldName) throws NoSuchFieldException, IllegalAccessException
    {
        Field field = o.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(o);
    }

    // Uses reflection to invoke public or private methods by name.
    private Object invokeMethod(Object o, Class clazz, String methodName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        Method method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(o);
    }



    private static class SessionWrapper implements HttpSession
    {
        HttpSession _real = null;
        final long _creationTime = HeartBeat.currentTimeMillis();
        long _accessedTime = _creationTime;
        boolean _valid = true;
        final String id = GUID.makeHash();
        final Hashtable<String,Object> _attributes = new Hashtable<String,Object>();

        SessionWrapper(HttpSession s)
        {
            _real = s;
        }

        @Override
        public String getId()
        {
            return null==_real ? id : _real.getId();
        }

        @Override
        public long getCreationTime()
        {
            return null==_real ? _creationTime : _real.getCreationTime();
        }

        @Override
        public long getLastAccessedTime()
        {
            return null==_real ? _accessedTime : _real.getLastAccessedTime();
        }

        void access()
        {
            _accessedTime = HeartBeat.currentTimeMillis();
        }

        boolean isValid()
        {
            return _valid;
        }

        @Override
        public ServletContext getServletContext()
        {
            return null==_real ? null : _real.getServletContext();
        }

        @Override
        public void setMaxInactiveInterval(int i)
        {
            if (null != _real)
                _real.setMaxInactiveInterval(i);
        }

        @Override
        public int getMaxInactiveInterval()
        {
            return null == _real ? 60*60 :_real.getMaxInactiveInterval();
        }

        @Override
        public HttpSessionContext getSessionContext()
        {
            return null==_real ? null : _real.getSessionContext();
        }

        @Override
        public void putValue(String s, Object o)
        {
            setAttribute(s,o);
        }

        @Override
        public void setAttribute(String s, Object o)
        {
            if (_log.isDebugEnabled())
                _log.debug("Session.setAttribute(" + s + ", " + String.valueOf(o) + ")");
            if (null==_real)
                _attributes.put(s, o);
            else
                _real.setAttribute(s, o);
        }

        @Override
        public Object getAttribute(String s)
        {
            return null==_real ? _attributes.get(s) : _real.getAttribute(s);
        }

        @Override
        public Object getValue(String s)
        {
            return null==_real ? _attributes.get(s) : _real.getValue(s);
        }

        @Override
        public Enumeration getAttributeNames()
        {
            return null==_real ? _attributes.keys() : _real.getAttributeNames();
        }

        @Override
        public String[] getValueNames()
        {
            return null==_real ? _attributes.keySet().toArray(new String[0]) : _real.getValueNames();
        }

        @Override
        public void removeAttribute(String s)
        {
            if (null != _real)
                _real.removeAttribute(s);
        }

        @Override
        public void removeValue(String s)
        {
            if (null != _real)
                _real.removeAttribute(s);
        }

        @Override
        public void invalidate()
        {
            _valid = false;
            _attributes.clear();
            if (null != _real)
            {
                _real.invalidate();
                _real = null;
            }
        }

        @Override
        public boolean isNew()
        {
            return null==_real ? true : _real.isNew();
        }
    }


    /* Robots never get a real tomcat session */

    HttpSession robotSession()
    {
        return new SessionWrapper(null);
    }


    /*
     * GUEST sessions are highly suspect
     *
     * Keep new references to new guest sessions in a 'nursery'.  When the nursery is full
     * start expiring sessions that appear inactive.  Active sessions get handled by tomcat.
     */
    static final int NURSERY_SIZE = AppProps.getInstance().isDevMode() ? 100 : 1000;
    static final Map<String,GuestSession> _nursery = Collections.synchronizedMap(new LinkedHashMap<String, GuestSession>(2000, 0.5f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GuestSession> e)
        {
            try
            {
                GuestSession s = e.getValue();
                if (s.isActive() || null == s._session || !isValid(s._session))
                    return true;
                boolean remove = size() > NURSERY_SIZE;
                if (remove)
                    s.expire();
                return remove;
            }
            catch (IllegalStateException x)
            {
                return true;    // inactive session
            }
            catch (Exception x)
            {
                ExceptionUtil.logExceptionToMothership(null, x);
                return true;
            }
        }
    });


    HttpSession guestSession()
    {
        HttpSession containerSession = makeRealSession();
        if (null == containerSession)
            return null;

        GuestSession guestSession;
        synchronized (_nursery)
        {
            guestSession = _nursery.get(containerSession.getId());
            if (null == guestSession  && containerSession.isNew())
            {
                guestSession = new GuestSession(getRemoteAddr());
                containerSession.setAttribute(GuestSession.class.getName(), guestSession);
                _nursery.put(containerSession.getId(), guestSession);
            }
        }
        if (null != guestSession)
            guestSession.access();
        return containerSession;
    }


    private static class GuestSession implements HttpSessionBindingListener
    {
        HttpSession _session = null;
        final String _ip;
        final long[] _accessedTime = new long[5];

        GuestSession(String ip)
        {
            _ip = ip;   // for debugging
        }

        @Override
        public void valueBound(HttpSessionBindingEvent e)
        {
            _session = e.getSession();
        }

        @Override
        public void valueUnbound(HttpSessionBindingEvent e)
        {
            if (null != _session)
                _nursery.remove(_session.getId());
            _session = null;
        }

        void expire()
        {
            HttpSession s = _session;
            if (null == s)
                return;
            _session = null;

            try
            {
                long age = HeartBeat.currentTimeMillis() - s.getCreationTime();
                if (age < TimeUnit.HOURS.toMillis(1))
                    _log.warn("Due to server load, guest session was forced to expire, remoteAddr=" + _ip);
                s.setMaxInactiveInterval(0);
            }
            catch (IllegalStateException x)
            {
                ;
            }
        }

        synchronized void access()
        {
            long t = HeartBeat.currentTimeMillis();
            if (t != _accessedTime[_accessedTime.length-1])
                System.arraycopy(_accessedTime,1,_accessedTime,0,_accessedTime.length-1);
            _accessedTime[_accessedTime.length-1] = t;
        }

        synchronized boolean isActive()
        {
            return _accessedTime[0] != 0;
        }
    }
}
