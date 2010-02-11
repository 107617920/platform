/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.settings;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 19, 2007
 */
public class PreferenceService
{
    private static Logger _log = Logger.getLogger(PreferenceService.class);

    private static final String PREFERENCE_SERVICE_MAP_KEY = "PreferenceServiceMap";
    private Map<String, Map<String, String>> _nullPreferenceMap = new HashMap<String, Map<String, String>>();

    private static final PreferenceService _instance = new PreferenceService();

    public static PreferenceService get()
    {
        return _instance;
    }

    private PreferenceService()
    {
    }

    public Object getProperty(String name, Container container)
    {
        return getPreferences(container).get(name);
    }

    public String getProperty(String name, User user)
    {
        if (user == null || user.isGuest())
            return getSessionPreferences().get(name);

        return getPreferences(user).get(name);
    }

    /**
     * Returns the container that the specified property is set on. Will return the
     * first container matching the property name in the inheritance chain, from
     * most specific to least specific.
     *
     * @param initialScope - the container of interest (to start the search from)
     */
    public Container findContainerFor(String name, Container initialScope)
    {
        if (initialScope == null)
            throw new IllegalArgumentException("Initial container scope cannot be null");

        if(initialScope.isRoot())
            return initialScope;

        Container c = initialScope;
        while (c != null)
        {
            Map<String, String> p = getPreferences(c.getId(), false);
            if (p != null)
            {
                if (p.containsKey(name))
                    return c;
            }
            if (c.isRoot())
                break;
            c = c.getParent();
        }
        return null;
    }

    public boolean isInherited(String name, Container container)
    {
        if (container == null || container.isRoot())
            return false;

        Container owningContainer = findContainerFor(name, container);
        return (owningContainer != null && !container.equals(owningContainer));
    }

    public void setProperty(String name, String value, Container container)
    {
        Map<String, String> prefs = getPreferences(container.getId(), true);
        prefs.put(name, value);

        PropertyManager.saveProperties(prefs);
        synchronized (container.getId().intern())
        {
            _nullPreferenceMap.remove(container.getId());
        }
    }

    public void setProperty(String name, String value, User user)
    {
        if (user.isGuest())
        {
            Map<String, String> prefs = getSessionPreferences();
            prefs.put(name, value);
        }
        else
        {
            Map<String, String> prefs = getPreferences(user, true);
            prefs.put(name, value);
            PropertyManager.saveProperties(prefs);
        }
    }

    public void deleteProperty(String name, Container container)
    {
        Map<String, String> prefs = getPreferences(container.getId(), true);
        prefs.remove(name);

        PropertyManager.saveProperties(prefs);
        synchronized (container.getId().intern())
        {
            _nullPreferenceMap.remove(container.getId());
        }
    }

    public void deleteProperty(String name, User user)
    {
        if (user.isGuest())
        {
            Map<String, String> prefs = getSessionPreferences();
            prefs.remove(name);
        }
        else
        {
            Map<String, String> prefs = getPreferences(user, true);
            prefs.remove(name);
            PropertyManager.saveProperties(prefs);
        }
    }

    private Map<String, String> getPreferences(Container container)
    {
        Stack<Container> stack = new Stack<Container>();
        stack.push(container);
        while (!container.isRoot())
        {
            stack.push(container.getParent());
            container = container.getParent();
        }

        Map<String, String> prefs = new HashMap<String, String>();
        while (!stack.isEmpty())
        {
            Container c = stack.pop();
            Map<String, String> p = getPreferences(c.getId(), false);
            if (p != null)
            {
                prefs.putAll(p);
            }
        }
        return Collections.unmodifiableMap(prefs);
    }

    private Map<String, String> getSessionPreferences()
    {
        HttpSession session = HttpView.currentRequest().getSession(true);
        Map<String, String> prefs = (Map<String, String>) session.getAttribute(PREFERENCE_SERVICE_MAP_KEY);

        if (null == prefs)
        {
            prefs = new HashMap<String, String>();
            session.setAttribute(PREFERENCE_SERVICE_MAP_KEY, prefs);
        }

        return prefs;
    }

    private Map<String, String> getPreferences(User user)
    {
        return PropertyManager.getProperties(user.getUserId(), ContainerManager.getRoot().getId(), PREFERENCE_SERVICE_MAP_KEY);
    }

    private Map<String, String> getPreferences(String containerId, boolean writable)
    {
        if (writable)
            return PropertyManager.getWritableProperties(containerId, PREFERENCE_SERVICE_MAP_KEY, true);
        else
            return getReadOnlyPreferences(containerId);
    }

    private Map<String, String> getPreferences(User user, boolean writable)
    {
        if (writable)
            return PropertyManager.getWritableProperties(user.getUserId(), ContainerManager.getRoot().getId(), PREFERENCE_SERVICE_MAP_KEY, true);
        else
            return getPreferences(user);
    }

    private Map<String, String> getReadOnlyPreferences(String containerId)
    {
        synchronized (containerId.intern())
        {
            if (_nullPreferenceMap.containsKey(containerId))
                return null;
        }
        Map<String, String> prefs = PropertyManager.getProperties(0, containerId, PREFERENCE_SERVICE_MAP_KEY);
        if (prefs.isEmpty())
        {
            synchronized (containerId.intern())
            {
                _nullPreferenceMap.put(containerId, null);
            }
        }
        return prefs;
    }
}
