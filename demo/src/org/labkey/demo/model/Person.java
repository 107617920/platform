/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.demo.model;

import org.apache.commons.lang.ObjectUtils;
import org.labkey.api.data.Entity;
import org.labkey.api.util.PageFlowUtil;
import org.apache.commons.lang.StringUtils;

/**
 * User: brittp
 * Date: Jan 23, 2006
 * Time: 1:18:25 PM
 */
public class Person extends Entity
{
    private String _firstName;
    private String _lastName;
    private Integer _age;
    private Integer _rowId;

    public Person()
    {
    }

    public Person(String firstName, String lastName, Integer age)
    {
        _firstName = StringUtils.trimToEmpty(firstName);
        _lastName = StringUtils.trimToEmpty(lastName);
        _age = age;
    }

    public Integer getAge()
    {
        return _age;
    }

    public void setAge(Integer age)
    {
        _age = age;
    }

    public String getFirstName()
    {
        return _firstName;
    }

    public void setFirstName(String firstName)
    {
        _firstName = firstName;
    }

    public String getLastName()
    {
        return _lastName;
    }

    public void setLastName(String lastName)
    {
        _lastName = lastName;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }


    public boolean equals(Object obj)
    {
        if (!(obj instanceof Person))
            return false;
        Person p = (Person)obj;
        
        return StringUtils.equals(_firstName, p.getFirstName()) &&
                StringUtils.equals(_lastName, p.getLastName()) &&
                ObjectUtils.equals(_age, p.getAge());
    }
}
