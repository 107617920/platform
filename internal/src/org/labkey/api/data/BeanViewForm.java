/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.ConvertUtils;

import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.InvocationTargetException;


public class BeanViewForm<K> extends TableViewForm implements DynaBean
{
    private Class<K> _wrappedClass = null;

    protected BeanViewForm(Class<K> clss)
    {
        this(clss, null, null);
    }

    public BeanViewForm(Class<K> clss, TableInfo tinfo)
    {
        this(clss, tinfo, null);
    }


    public BeanViewForm(Class<K> clss, TableInfo tinfo, String[] extraProps)
    {
        super(StringBeanDynaClass.createDynaClass(clss, extraProps), tinfo);
        _wrappedClass = clss;
    }


    public K getBean()
    {
        if (null != _oldValues)
        {
            try
            {
                K bean;
                if (_oldValues instanceof Map && !(_wrappedClass.isAssignableFrom(_oldValues.getClass())))
                {
                    ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(_wrappedClass);
                    bean = factory.fromMap((Map) _oldValues);
                }
                else
                    bean = (K) BeanUtils.cloneBean(_oldValues);

                BeanUtils.populate(bean, this.getTypedValues());
                return bean;
            }
            catch (IllegalAccessException x)
            {
                throw new RuntimeException(x);
            }
            catch (InvocationTargetException x)
            {
                throw new RuntimeException(x);
            }
            catch (InstantiationException x)
            {
                throw new RuntimeException(x);
            }
            catch (NoSuchMethodException x)
            {
                throw new RuntimeException(x);
            }
        }
        else
        {
            ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(_wrappedClass);
            return factory.fromMap(this.getTypedValues());
        }
    }


    public void setBean(K bean)
    {
        ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(_wrappedClass);
        this.setTypedValues(factory.toMap(bean, null), false);
    }

    public Map<String, String> getStrings()
    {
        //If we don't have strings and do have typed values then
        //make the strings match the typed values
        Map<String, String> strings = super.getStrings();
        if (null == strings || strings.size() == 0 && (null != _values && _values.size() > 0))
        {
            strings = new HashMap<String, String>();
            for (Map.Entry<String, Object> entry : _values.entrySet())
            {
                strings.put(entry.getKey(), ConvertUtils.convert(entry.getValue()));
            }
            _stringValues = strings;
        }

        return strings;
    }

    public void setOldValues(Object o)
    {
        if (o == null)
            _oldValues = null;
        else if (_wrappedClass.isAssignableFrom(o.getClass()))
            _oldValues = o;
        else if (o instanceof Map)
        {
            ObjectFactory factory = ObjectFactory.Registry.getFactory(_wrappedClass);
            _oldValues = factory.fromMap((Map<String, Object>) o);
        }
        else
        {
            throw new IllegalArgumentException("Type of old values is incompatible with wrapped class");
        }
    }
}
