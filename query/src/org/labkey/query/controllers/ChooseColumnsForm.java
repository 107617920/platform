/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.query.controllers;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.view.ActionURL;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.*;

public class ChooseColumnsForm extends DesignForm
{
    public LinkedHashSet<FieldKey> ff_selectedColumns = new LinkedHashSet<FieldKey>();
    public String ff_columnListName;
    public boolean ff_saveForAllUsers;
    public boolean ff_saveFilter;
    public boolean ff_inheritable;

    private ActionURL _sourceURL = null;
    private boolean _allowSaveWithSameName = true;
    private boolean _saveInSession;

    public BindException bindParameters(PropertyValues params)
    {
        BindException errors =  super.bindParameters(params);

        // Logged in users may save to session, but Guest always saves to session.
        if (getUser().isGuest())
            _saveInSession = true;

        //NOTE we want querySettings to be based on srcURL parameters
        // get queryName, viewName and replace _initParameters
        setDataRegionName(getValue(QueryParam.dataRegionName, params));
        setQueryName(getValue(QueryParam.queryName, params));
        setViewName(getValue(QueryParam.viewName, params));
        _initParameters = new MutablePropertyValues();
        String src = getValue(QueryParam.srcURL, params);
        if (src != null)
        {
            try
            {
                _sourceURL = new ActionURL(src);
                _sourceURL.setReadOnly();
                ((MutablePropertyValues)_initParameters).addPropertyValues(_sourceURL.getPropertyValues());
            }
            catch (IllegalArgumentException x)
            {
                _sourceURL = null;
            }
        }

        return errors;
    }

    
    public void initForView()
    {
        if (null == getQuerySettings())
            return;

        ff_columnListName = getQuerySettings().getViewName();
        CustomView cv = getCustomView();
        if (cv != null && cv.getColumns() != null)
        {
            ff_selectedColumns.addAll(cv.getColumns());
            ff_inheritable = cv.canInherit();
        }
        if (ff_selectedColumns.isEmpty())
        {
            TableInfo table = getQueryDef().getTable(getSchema(), null, true);
            if (table != null)
            {
                for (ColumnInfo column : table.getColumns())
                {
                    if (!column.isHidden() && !column.isUnselectable())
                    {
                        ff_selectedColumns.add(new FieldKey(null, column.getName()));
                    }
                }
            }
        }
    }


    public void setFf_selectedColumns(String columns)
    {
        ff_selectedColumns = new LinkedHashSet<FieldKey>();
        if (columns != null)
        {
            for (String column : StringUtils.split(columns, '&'))
            {
                ff_selectedColumns.add(FieldKey.fromString(column));
            }
        }
    }

    public void setFf_saveForAllUsers(boolean b)
    {
        ff_saveForAllUsers = b;
    }

    public void setFf_inheritable(boolean ff_inheritable)
    {
        this.ff_inheritable = ff_inheritable;
    }

    public void setFf_columnListName(String name)
    {
        ff_columnListName = name;
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL ret = super.urlFor(action);
        if (null != getSourceURL())
            ret.addParameter(QueryParam.srcURL.toString(), getSourceURL().toString());
        ret.addParameter(QueryParam.dataRegionName.toString(), getDataRegionName());
        ret.addParameter(QueryParam.queryName.toString(), getQueryName());
        return ret;
    }

    public static boolean isFilterOrSort(String dataRegionName, String param)
    {
        assert param.startsWith(dataRegionName + ".");
        String check = param.substring(dataRegionName.length() + 1);
        if (check.indexOf("~") >= 0)
            return true;
        if ("sort".equals(check))
            return true;
        if (check.equals("containerFilterName"))
            return true;
        return false;
    }

    public List<String> getFilterColumnNamesFromURL()
    {
        ActionURL url = getSourceURL();
        if (url == null)
            return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (String key : url.getKeysByPrefix(getDataRegionName() + "."))
        {
            String editedKey = key.substring(getDataRegionName().length() + 1);
            int tildeIndex = editedKey.indexOf("~");
            if (tildeIndex >= 0)
            {
                String colName = editedKey.substring(0, tildeIndex);
                result.add(getCaption(colName));
            }
        }
        return result;
    }

    private String getCaption(String colName)
    {
        FieldKey fKey = FieldKey.fromString(colName);
        String caption = fKey.getCaption();

        TableInfo table = getQueryDef().getTable(getSchema(), null, true);
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(table, Collections.singleton(fKey));

        ColumnInfo column = columns.get(fKey);
        if (column != null)
            caption = column.getLabel();
        return caption;
    }

    public List<String> getSortColumnNamesFromURL()
    {
        ActionURL url = getSourceURL();
        if (url == null)
            return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (String key : url.getKeysByPrefix(getDataRegionName() + "."))
        {
            String editedKey = key.substring(getDataRegionName().length() + 1);
            if ("sort".equals(editedKey))
            {
                String colName = url.getParameter(key);
                result.add(getCaption(colName));
            }
        }
        return result;
    }

    public String getContainerFilterName()
    {
        ActionURL url = getSourceURL();
        CustomView current = getCustomView();
        if (current != null && current.getContainerFilterName() != null)
        {
            if (url.getParameter(getDataRegionName() + "." + QueryParam.ignoreFilter.toString()) != null)
                return current.getContainerFilterName();
        }
        if (null == url)
            return null;
        return url.getParameter(getDataRegionName() + ".containerFilterName");
    }

    public boolean hasFilterOrSort()
    {
        CustomView current = getCustomView();
        ActionURL url = getSourceURL();
        if (null == url)
            return false;
        if (current != null && current.hasFilterOrSort())
        {
            if (url.getParameter(getDataRegionName() + "." + QueryParam.ignoreFilter.toString()) != null)
                return true; 
        }
        for (String key : url.getKeysByPrefix(getDataRegionName() + "."))
        {
            if (isFilterOrSort(getDataRegionName(), key))
                return true;
        }
        return false;
    }

    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {
        ActionURL src = getSourceURL();
        if (null == src)
            return;
        if (src.getParameter(getDataRegionName() + "." + QueryParam.ignoreFilter.toString()) == null)
        {
            CustomView current = getCustomView();
            if (current != null)
            {
                current.applyFilterAndSortToURL(url, dataRegionName);
            }
        }
        for (String key : src.getKeysByPrefix(getDataRegionName() + "."))
        {
            if (!isFilterOrSort(getDataRegionName(), key))
                continue;
            String newKey = dataRegionName + key.substring(getDataRegionName().length());
            for (String value : src.getParameters(key))
            {
                url.addParameter(newKey, value);
            }
        }

        Sort sort = new Sort();
        sort.addURLSort(url, dataRegionName);
        url.replaceParameter(dataRegionName+".sort",sort.getURLParamValue());
    }
    

    public ActionURL getSourceURL()
    {
        return _sourceURL;        
    }


    public void setFf_saveFilter(boolean b)
    {
        ff_saveFilter = b;
    }

    public void setSaveInSession(boolean b)
    {
        _saveInSession = b;
    }

    public boolean isSaveInSession()
    {
        return _saveInSession;
    }

    public boolean canSaveForAllUsers()
    {
        return getContainer().hasPermission(getUser(), EditSharedViewPermission.class);
    }

    public boolean canEdit()
    {
        return canEdit(null);
    }

    public boolean canEdit(Errors errors)
    {
        CustomView view = getQueryDef().getCustomView(getUser(), getViewContext().getRequest(), ff_columnListName);
        return canEdit(view, getContainer(), errors);
    }

    public static boolean canEdit(CustomView view, Container c, Errors errors)
    {
        if (view != null)
        {
            if (view.canInherit())
            {
                if (!c.getId().equals(view.getContainer().getId()))
                {
                    if (errors != null)
                        errors.reject(null, "Inherited view '" + (view.getName() == null ? "<default>" : view.getName()) + "' can only be edited from the folder in which it is defined, " + c.getPath());
                    return false;
                }
            }

            if (!view.isEditable())
            {
                if (errors != null)
                    errors.reject(null, "The view '" + (view.getName() == null ? "<default>" : view.getName()) + "' is read-only and cannot be edited");
                return false;
            }
        }
        return true;
    }

}
