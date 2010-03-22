/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.search.umls;

import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.util.ResultSetUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

/**
* Created by IntelliJ IDEA.
* User: matthewb
* Date: Mar 10, 2010
* Time: 10:45:44 AM
*/
public class Definition // MRDEF
{
    final String _type="DEF";
    String CUI;
    String AUI;
    String ATUI;
    String SATUI;
    public String SAB;
    public String DEF;
    String SUPPRESS; // O,E,Y,N
    String CVF;

    Definition(){}

    public Definition(Map<String,Object> map)
    {
        apply(map);
    }

    public Definition(String[] args)
    {
        try
        {
            int i=0;
            CUI=args[i++];
            AUI=args[i++];
            ATUI=args[i++];
            SATUI=args[i++];
            SAB=args[i++];
            DEF=args[i++];
            if (i==args.length) return;
            SUPPRESS=args[i++];
            if (i==args.length) return;
            CVF=args[i++];
        }
        catch (ArrayIndexOutOfBoundsException x)
        {
        }
    }


   void apply(Map<String,Object> map)
    {
        CUI=getString(map,"cui");
        AUI=getString(map,"aui");
        ATUI=getString(map,"atui");
        SATUI=getString(map,"satui");
        SAB=getString(map,"sab");
        DEF=getString(map,"def");
        SUPPRESS=getString(map,"suppress");
        CVF=getString(map,"cvf");
    }

    private String getString(Map map, String key)
    {
        Object v = map.get(key);
        return null==v ? null : v.toString();
    }


    @Override
    public String toString()
    {
        return _type + ": " + CUI + " " + DEF;
    }

    static
    {
        ObjectFactory.Registry.register(Definition.class, new BeanObjectFactory<Definition>()
        {
            public Definition fromMap(Map<String, ?> m)
            {
                return new Definition((Map)m);
            }
            public Definition fromMap(Definition bean, Map<String, ?> m)
            {
                bean.apply((Map)m);
                return bean;
            }
            @Override
            public Definition[] handleArray(ResultSet rs) throws SQLException
            {
                ArrayList<Definition> list = new ArrayList<Definition>();
                while (rs.next())
                    list.add(new Definition(ResultSetUtil.mapRow(rs)));
                return list.toArray(new Definition[list.size()]);
            }
        });
    }
}
