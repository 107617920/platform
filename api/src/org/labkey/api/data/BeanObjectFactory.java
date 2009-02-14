/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.ConversionException;
import org.apache.log4j.Logger;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.UnexpectedException;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;


public class BeanObjectFactory<K> implements ObjectFactory<K> // implements ResultSetHandler
{
    private static Logger _log = Logger.getLogger(BeanObjectFactory.class);

    private Class<K> _class;

    // for performance pre-calculate readable/writeable properties
    private HashSet<String> _writeableProperties = null;
    private HashSet<String> _readableProperties = null;


    protected BeanObjectFactory()
    {
    }


    public BeanObjectFactory(Class<K> clss)
    {
        _class = clss;
        _writeableProperties = new HashSet<String>();
        _readableProperties = new HashSet<String>();

        K bean;
        try
        {
            bean = _class.newInstance();
        }
        catch (InstantiationException x)
        {
            throw new RuntimeException(x);
        }
        catch (IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }

        PropertyDescriptor origDescriptors[] = PropertyUtils.getPropertyDescriptors(bean);
        _writeableProperties = new HashSet<String>(origDescriptors.length * 2);
        _readableProperties = new HashSet<String>(origDescriptors.length * 2);

        for (int i = 0; i < origDescriptors.length; i++)
        {
            String name = origDescriptors[i].getName();
            if ("class".equals(name))
                continue;
            if (PropertyUtils.isReadable(bean, name))
            {
                Method readMethod = origDescriptors[i].getReadMethod();
                if (readMethod != null && readMethod.getParameterTypes().length == 0)
                    _readableProperties.add(name);
            }
            if (PropertyUtils.isWriteable(bean, name))
                _writeableProperties.add(name);
        }
    }


    // Implement "official" property name rule
    public String convertToPropertyName(String name)
    {
        if (1 == name.length())
            return name.toLowerCase();

        if (Character.isUpperCase(name.charAt(0)) && !Character.isUpperCase(name.charAt(1)))
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        else
            return name;
    }


    public K fromMap(Map<String,? extends Object> m)
    {
        try
        {
        K bean = _class.newInstance();
        fromMap(bean, m);
        return bean;
        }
        catch (IllegalAccessException x)
        {
            _log.error("unexpected error", x);
            throw new RuntimeException(x);
        }
        catch (InstantiationException x)
        {
            _log.error("unexpected error", x);
            throw new RuntimeException(x);
        }
    }
    

    public void fromMap(K bean, Map<String, ? extends Object> m)
    {
        for (String prop : _writeableProperties)
        {
            Object value = null;
            try
            {
                value = m.get(prop);
                //Assume nulls mean do not change value
                if (null != value)
                    BeanUtils.copyProperty(bean, prop, value);
            }
            catch (IllegalAccessException x)
            {
                assert null == "unexpected exception";
            }
            catch (InvocationTargetException x)
            {
                assert null == "unexpected exception";
            }
            catch (IllegalArgumentException x)
            {
                _log.error("could not set property: " + prop + "=" + String.valueOf(value), x);
            }
        }

        this.fixupBean(bean);
    }


    public Map<String, Object> toMap(K bean, Map<String, Object> m)
    {
        try
        {
            if (null == m)
                m = new CaseInsensitiveHashMap<Object>();
            for (String name : _readableProperties)
            {
                try
                {
                    Object value = PropertyUtils.getSimpleProperty(bean, name);
                    m.put(name, value);
                }
                catch (NoSuchMethodException e)
                {
                    assert false : e;
                    continue;
                }
            }

            return m;
        }
        catch (IllegalAccessException x)
        {
            assert false : x;
        }
        catch (InvocationTargetException x)
        {
            assert false : x;
            if (x.getTargetException() instanceof RuntimeException)
                throw (RuntimeException)x.getTargetException();
        }
        fixupMap(m, bean);
        return m;
    }


    public K handle(ResultSet rs) throws SQLException
    {
        Map<String, Object> map = ResultSetUtil.mapRow(rs, null);
        return fromMap(map);
    }

    public K[] handleArray(ResultSet rs) throws SQLException
    {
        ResultSetMetaData md = rs.getMetaData();
        int count = md.getColumnCount();
        CaseInsensitiveHashMap<String> propMap = new CaseInsensitiveHashMap<String>(count * 2);
        for (String prop : _writeableProperties)
            propMap.put(prop, prop);

        String[] properties = new String[count + 1];
        for (int i = 1; i <= count; i++)
        {
            String prop = md.getColumnName(i);

            prop = propMap.get(prop); //Map to correct casing...
            if (null != prop)
                properties[i] = prop;
        }

        ArrayList<K> list = new ArrayList<K>();

        try
        {
            while (rs.next())
            {
                K bean = _class.newInstance();

                for (int i = 1; i <= count; i++)
                {
                    String prop = properties[i];
                    if (null == prop)
                        continue;
                    try
                    {
                        Object value = rs.getObject(i);
                        if (value instanceof Double)
                            value = Double.valueOf(ResultSetUtil.mapDatabaseDoubleToJavaDouble((Double) value));
                        BeanUtils.copyProperty(bean, prop, value);
                    }
                    catch (ConversionException e)
                    {
                        throw new UnexpectedException(e, "Failed to copy property '" + prop + "' on class " + _class.getName());
                    }
                    catch (IllegalAccessException x)
                    {
                        throw new UnexpectedException(x, "Failed to copy property '" + prop + "' on class " + _class.getName());
                    }
                    catch (InvocationTargetException x)
                    {
                        throw new UnexpectedException(x, "Failed to copy property '" + prop + "' on class " + _class.getName());
                    }
                }

                fixupBean(bean);
                list.add(bean);
            }
        }
        catch (InstantiationException x)
        {
            assert false : "unexpected exception";
        }
        catch (IllegalAccessException x)
        {
            assert false : "unexpected exception";
        }

        K[] array = (K[]) Array.newInstance(_class, list.size());
        return list.toArray(array);
    }


    protected void fixupMap(Map<String, Object> m, K o)
    {
    }


    protected void fixupBean(K o)
    {
    }
}
