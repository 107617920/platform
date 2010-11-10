/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

package org.labkey.query;

import org.apache.log4j.Logger;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.*;
import org.labkey.data.xml.queryCustomView.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * User: adam
 * Date: Jun 4, 2009
 * Time: 10:56:23 AM
 */

/*
    Base reader for de-serializing query custom view XML files (creating using queryCustomView.xsd) into a form that's
    compatible with CustomView.  This class is used by study import and simple modules.  
 */
public class CustomViewXmlReader
{
    private static final Logger LOG = Logger.getLogger(CustomViewXmlReader.class);
    public static final String XML_FILE_EXTENSION = ".qview.xml";

    private String _schema;
    private String _query;
    private List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> _colList = new ArrayList<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>>();
    private boolean _hidden = false;
    private List<Pair<String,String>> _filters;
    private List<String> _sorts;
    private String _customIconUrl;

    protected String _name;


    public CustomViewXmlReader() throws XmlValidationException
    {
    }

    public String getName()
    {
        return _name;
    }

    public String getSchema()
    {
        return _schema;
    }

    public String getQuery()
    {
        return _query;
    }

    public List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> getColList()
    {
        return _colList;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public List<Pair<String, String>> getFilters()
    {
        return _filters;
    }

    // TODO: There should be a common util for filter/sort url handling.  Should use a proper URL class to do this, not create/encode the query string manually
    public String getFilterAndSortString()
    {
        String sort = getSortParamValue();

        if (null == getFilters() && null == sort)
            return null;

        StringBuilder ret = new StringBuilder("?");

        String sep = "";

        if (null != getFilters())
        {
            for (Pair<String, String> filter : getFilters())
            {
                ret.append(sep);
                ret.append("filter.");
                ret.append(PageFlowUtil.encode(filter.first));
                ret.append("=");
                ret.append(PageFlowUtil.encode(filter.second));
                sep = "&";
            }
        }

        if (null != sort)
        {
            ret.append(sep);
            ret.append("filter.sort=");
            ret.append(PageFlowUtil.encode(sort));
        }

        return ret.toString();
    }

    public String getSortParamValue()
    {
        if (null == getSorts())
            return null;

        StringBuilder sortParam = new StringBuilder();
        String sep = "";

        for (String sort : getSorts())
        {
            sortParam.append(sep);
            sortParam.append(sort);
            sep = ",";
        }

        return sortParam.toString();
    }

    public List<String> getSorts()
    {
        return _sorts;
    }

    public String getCustomIconUrl()
    {
        return _customIconUrl;
    }

    public static CustomViewXmlReader loadDefinition(Resource r) throws XmlValidationException
    {
        InputStream is = null;
        try
        {
            is = r.getInputStream();
            return loadDefinition(is, r.getPath().toString());
        }
        catch (IOException ioe)
        {
            UnexpectedException.rethrow(ioe);
        }
        finally
        {
            if (is != null) try { is.close(); } catch (IOException e) { }
        }

        return null;
    }

    public static CustomViewXmlReader loadDefinition(File f) throws XmlValidationException
    {
        InputStream is = null;
        try
        {
            is = new FileInputStream(f);
            return loadDefinition(is, f.toString());
        }
        catch (IOException ioe)
        {
            UnexpectedException.rethrow(ioe);
        }
        finally
        {
            if (is != null) try { is.close(); } catch (IOException e) { }
        }

        return null;
    }

    private static CustomViewXmlReader loadDefinition(InputStream is, String path) throws XmlValidationException
    {
        try
        {
            CustomViewDocument doc = CustomViewDocument.Factory.parse(is, XmlBeansUtil.getDefaultParseOptions());
            XmlBeansUtil.validateXmlDocument(doc);
            CustomViewType viewElement = doc.getCustomView();

            CustomViewXmlReader reader = new CustomViewXmlReader();
            reader._name = viewElement.getName();
            reader._schema = viewElement.getSchema();
            reader._query = viewElement.getQuery();
            reader._hidden = viewElement.isSetHidden() && viewElement.getHidden();
            reader._customIconUrl = viewElement.getCustomIconUrl();

            //load the columns
            reader._colList = loadColumns(viewElement.getColumns());

            //load the filters
            reader._filters = loadFilters(viewElement.getFilters());

            //load the sorts
            reader._sorts = loadSorts(viewElement.getSorts());

            return reader;
        }
        catch (XmlValidationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            LOG.warn("Unable to load custom view definition from file " + path, e);
        }

        return null;
    }

    protected static List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> loadColumns(ColumnsType columns)
    {
        List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> ret = new ArrayList<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>>();

        if(null == columns)
            return ret;

        for(ColumnType column : columns.getColumnArray())
        {
            FieldKey fieldKey = getFieldKey(column.getName());
            if(null == fieldKey)
                continue;

            //load any column properties that might be there
            Map<CustomView.ColumnProperty,String> props = new HashMap<CustomView.ColumnProperty,String>();

            PropertiesType propsList = column.getProperties();
            if(null != propsList)
            {
                for(PropertyType propDef : propsList.getPropertyArray())
                {
                    CustomView.ColumnProperty colProp = CustomViewInfo.ColumnProperty.getForXmlEnum(propDef.getName());

                    if(null == colProp)
                        continue;

                    props.put(colProp, propDef.getValue());
                }
            }

            ret.add(Pair.of(fieldKey, props));
        }

        return ret;
    }

    protected static List<Pair<String, String>> loadFilters(FiltersType filters)
    {
        if(null == filters)
            return null;

        List<Pair<String,String>> ret = new ArrayList<Pair<String,String>>();
        for(FilterType filter : filters.getFilterArray())
        {
            if(null == filter.getColumn() || null == filter.getOperator())
                continue;

            ret.add(new Pair<String,String>(filter.getColumn() + "~" + filter.getOperator().toString(), filter.getValue()));
        }

        return ret;
    }

    protected static FieldKey getFieldKey(String name)
    {
        return null == name ? null : FieldKey.fromString(name);
    }

    protected static List<String> loadSorts(SortsType sorts)
    {
        if(null == sorts)
            return null;

        List<String> ret = new ArrayList<String>();
        for(SortType sort : sorts.getSortArray())
        {
            if(null == sort.getColumn())
                continue;

            ret.add(sort.isSetDescending() && sort.getDescending() ? "-" + sort.getColumn() : sort.getColumn());
        }
        return ret;
    }
}
