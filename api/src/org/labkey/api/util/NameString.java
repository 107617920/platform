/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.util;

import org.labkey.api.data.Container;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang.StringUtils;

import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Feb 9, 2009
 * Time: 10:40:19 AM
 *
 * A Name string can contain alpha numeric characters, spaces, and limited punctuation.
 * Names might be used for table names (quoted sql identifiers), datasets names, samplesets,
 * path parts or other uses where arbitrary text is not required.
 */

public class NameString extends HString
{
	Pattern namePattern = Pattern.compile("(\\p{Alnum}|[\\Q$ _-:()\\E])*");

	public NameString(String s)
    {
        _source = StringUtils.trimToEmpty(s);
        _tainted = _source.length() != 0 && !namePattern.matcher(s).matches();
    }

	public NameString(HString s)
	{
		this(null == s ? "" : s._source);
	}

	public NameString(String s, boolean t)
	{
		_source = s;
		_tainted = _source.length() != 0 && t;
	}

	public NameString(Enum e)
	{
		_source = e.toString();
		_tainted = false;
	}

    public NameString(Container c)
    {
        _source = c.getId();
        _tainted = false;
    }

	public String toString()
    {
        return isTainted() ? "" : _source;
    }

    @Override
    public boolean equals(Object o)
    {
        return (o instanceof NameString) && _source.equalsIgnoreCase(((NameString)o)._source);
    }

    @Override
    public int compareTo(HString str)
    {
        return super.compareToIgnoreCase(str);
    }


    public static class Converter implements org.apache.commons.beanutils.Converter
    {
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;
            if (value instanceof NameString)
                return value;
            if (value instanceof HString)
                value = ((HString)value).getSource();
            NameString g = new NameString(String.valueOf(value));
            if (g.isTainted())
                throw new ConversionException("Invalid name, use only alphanumeric characters, spaces, and '-_:()$'.");
            return g;
        }
    }
}