/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

package org.labkey.api.gwt.client.ui;

import java.util.HashMap;
import java.util.Map;

/**
* Created by IntelliJ IDEA.
* User: matthewb
* Date: Apr 22, 2010
* Time: 11:33:56 AM
*/
public enum PropertyType
{
    // Treat expMultiLine the same as xsdString for lookup purposes, since it's really just a formatting distiction 
    expMultiLine("http://www.w3.org/2001/XMLSchema#multiLine", true, "Multi-Line Text", "VARCHAR", "String", "string"),
    xsdString("http://www.w3.org/2001/XMLSchema#string", true, "Text (String)", "VARCHAR", "String", "string"),
    xsdBoolean("http://www.w3.org/2001/XMLSchema#boolean", false, "Boolean", "BOOLEAN", null, "boolean"),
    xsdInt("http://www.w3.org/2001/XMLSchema#int", true, "Integer", "INT", null, "int"),
    xsdDouble("http://www.w3.org/2001/XMLSchema#double", true, "Number (Double)", "DOUBLE", "Double", "float"),
    xsdDateTime("http://www.w3.org/2001/XMLSchema#dateTime", true, "DateTime", "TIMESTAMP", null, "date"),
    expFileLink("http://cpas.fhcrc.org/exp/xml#fileLink", false, "File", "CLOB"),
    expAttachment("http://www.labkey.org/exp/xml#attachment", false, "Attachment", "VARCHAR"),
    expFlag("http://www.labkey.org/exp/xml#flag", false, "Flag", "VARCHAR");

    public static final String PARTICIPANT_CONCEPT_URI = "http://cpas.labkey.com/Study#ParticipantId";

    private final String _uri;
    private final String _display;
    private final String _sqlName;
    private final String _jsonType;
    private final String _short;
    private final boolean _lookup;

    PropertyType(String uri, boolean lookup, String display, String sqlName)
    {
        this(uri,lookup,display, sqlName, display, null);
    }

    PropertyType(String uri, boolean lookup, String display, String sqlName, String shortName, String jsonType)
    {
        this._uri = uri;
        this._lookup = lookup;
        this._display = display;
        this._sqlName = sqlName;
        this._jsonType = jsonType;
        this._short = shortName == null ? display : shortName;
    }

    public String getURI()
    {
        return _uri;
    }

    public String getSqlName()
    {
        return _sqlName;
    }

    @Override
    public String toString()
    {
        return _uri;
    }

    public String getDisplay()
    {
        return _display;
    }

    public String getShortName()
    {
        return _short;
    }

    public String getJsonType()
    {
        return _jsonType;
    }

    public boolean isLookupType()
    {
        return _lookup;
    }

    public static PropertyType fromURI(String uri)
    {
        for (PropertyType propertyType : values())
        {
            if (propertyType.getURI().equals(uri))
            {
                return propertyType;
            }
        }
        return null;
    }

    public static PropertyType fromName(String type)
    {
        PropertyType t = synonyms.get(type);
        if (null == t)
            t = synonyms.get(type.toLowerCase());
        return t;
    }


    private static Map<String,PropertyType> synonyms = new HashMap<String,PropertyType>();
    private static void _put(PropertyType t)
    {
        synonyms.put(t.toString().toLowerCase(), t);
        synonyms.put(t.getDisplay().toLowerCase(), t);
        synonyms.put(t.getShortName().toLowerCase(), t);
    }
    static
    {
        for (PropertyType t : PropertyType.values())
            _put(t);

        synonyms.put("text", xsdString);
        synonyms.put("xsd:string", xsdString);

        synonyms.put("int", xsdInt);
        synonyms.put("integer", xsdInt);
        synonyms.put("xsd:int", xsdInt);

        synonyms.put("number", xsdDouble);
        synonyms.put("real", xsdDouble);
        synonyms.put("float", xsdDouble);
        synonyms.put("xsd:double", xsdDouble);

        synonyms.put("date", xsdDateTime);
        synonyms.put("xsd:datetime", xsdDateTime);

        synonyms.put("bool", xsdBoolean);
        synonyms.put("xsd:boolean", xsdBoolean);
    }
}
