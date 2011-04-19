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

package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.HString;
import org.labkey.api.util.IdentifierString;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;


/**
 * QueryForm is basically a wrapper for QuerySettings and related helper for the query subsystem.
 *
 * Since this is being bound from request variables all parameters may be overridden.  For more control,
 * use QuerySettings directly.
 *
 * Note, that the QuerySettings require a schemaName and dataRegionName before being constructed.
 */
public class QueryForm extends ReturnUrlForm implements HasViewContext, HasBindParameters
{
    public static final String PARAMVAL_NOFILTER = "NONE";
    private ViewContext _context;

    private IdentifierString _schemaName = null;
    private UserSchema _schema;

    private String _queryName;
    private QueryDefinition _queryDef;

    private String _viewName;
    private CustomView _customView;
    private QuerySettings _querySettings;
    private boolean _exportAsWebPage = false;
    private String _queryViewActionURL;
    private String _dataRegionName = QueryView.DATAREGIONNAME_DEFAULT;

    protected PropertyValues _initParameters = null;

    public QueryForm()
    {
    }

    public QueryForm(String dataRegionName)
    {
        _dataRegionName = dataRegionName;
    }

    protected QueryForm(String schemaName, String queryName)
    {
        _schemaName = new IdentifierString(schemaName);
        _queryName = queryName;
    }

    protected QueryForm(String schemaName, String queryName, String viewName)
    {
        _schemaName = new IdentifierString(schemaName);
        _queryName = queryName;
        _viewName = viewName;
    }

    public void setViewContext(ViewContext context)
    {
        _context = context;
    }

    public ViewContext getViewContext()
    {
        return _context;
    }

    protected User getUser()
    {
        return getViewContext().getUser();
    }


    protected Container getContainer()
    {
        return getViewContext().getContainer();
    }


    public BindException bindParameters(PropertyValues params)
    {
        return doBindParameters(params);
    }


    protected BindException doBindParameters(PropertyValues params)
    {
        _initParameters = params;
        String commandName = getDataRegionName() == null ? "form" : getDataRegionName();

        // Delete parameters we don't want to bind or that we want QuerySettings.init() to handle
        MutablePropertyValues bindParams = new MutablePropertyValues(params);
        bindParams.removePropertyValue(QueryParam.dataRegionName.name());
        bindParams.removePropertyValue(QueryParam.queryName.name());
        bindParams.removePropertyValue(QueryParam.viewName.name());
        // don't override preset schemaName
        IdentifierString schemaName = _schemaName;
        
        BindException errors = BaseViewAction.springBindParameters(this, commandName, bindParams);
        
        if (schemaName != null && !schemaName.isEmpty())
            _schemaName = schemaName;

        return errors;
    }

    protected String getValue(Enum key, PropertyValues... pvss)
    {
        return getValue(key.name(), pvss);
    }

    protected String getValue(String key, PropertyValues... pvss)
    {
        for (PropertyValues pvs : pvss)
        {
            if (pvs == null) continue;
            PropertyValue pv = pvs.getPropertyValue(key);
            if (pv == null) continue;
            Object value = pv.getValue();
            if (value == null) continue;
            return value instanceof  String ? (String)value : ((String[])value)[0];
        }
        return null;
    }

    protected String[] getValues(String key, PropertyValues... pvss)
    {
        for (PropertyValues pvs : pvss)
        {
            if (pvs == null) continue;
            PropertyValue pv = pvs.getPropertyValue(key);
            if (pv == null) continue;
            Object value = pv.getValue();
            if (value == null) continue;
            return value instanceof String ? new String[] {(String)value} : ((String[])value);
        }
        return null;
    }

    protected UserSchema createSchema()
    {
        UserSchema ret = null;
        HString schemaName = getSchemaName();

        if (null != schemaName && !schemaName.isEmpty())
        {
            UserSchema baseSchema = (UserSchema) DefaultSchema.get(getUser(), getContainer()).getSchema(schemaName.toString());
            if (baseSchema == null)
            {
                return null;
            }
            QuerySettings settings = createQuerySettings(baseSchema);
            try
            {
                return baseSchema.createView(getViewContext(), settings).getSchema();
            }
            catch (ServletException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        return ret;
    }


    final public QuerySettings getQuerySettings()
    {
        if (_querySettings == null)
        {
            UserSchema schema = getSchema();
            if (schema != null)
                _querySettings = createQuerySettings(schema);
        }
        return _querySettings;
    }


    protected QuerySettings createQuerySettings(UserSchema schema)
    {
        QuerySettings settings = schema.getSettings(_initParameters, getDataRegionName());
        if (null != _queryName)
            settings.setQueryName(_queryName);
        if (null != _viewName)
            settings.setViewName(_viewName);
        return settings;
    }


    protected void setDataRegionName(String name)
    {
        if (_querySettings != null)
            throw new IllegalStateException();
        _dataRegionName = name;
    }


    public String getDataRegionName()
    {
        return _dataRegionName;
    }


    public void setSchemaName(IdentifierString name)
    {
        if (_querySettings != null)
            throw new IllegalStateException();
        _schemaName = name;
    }

    @NotNull
    public IdentifierString getSchemaName()
    {
        return _schemaName == null ? new IdentifierString("", false) : _schemaName;
    }

    public UserSchema getSchema()
    {
        if (_schema == null)
        {
            _schema = createSchema();
        }
        return _schema;
    }

    public void setQueryName(String name)
    {
        if (_queryDef != null)
            throw new IllegalStateException();
        _queryName = name;
    }

    public String getQueryName()
    {
        return getQuerySettings() != null ? getQuerySettings().getQueryName() : _queryName;
    }
    
    public QueryDefinition getQueryDef()
    {
        if (getQueryName() == null)
            return null;
        if (_queryDef == null)
        {
            _queryDef = QueryService.get().getQueryDef(getUser(), getContainer(), getSchemaName().toString(), getQueryName());
        }
        if (_queryDef == null)
        {
            _queryDef = getSchema().getQueryDefForTable(getQueryName());
        }
        return _queryDef;
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL ret = getSchema().urlFor(action, getQueryDef());
        if (_customView != null && _customView.getName() != null)
        {
            ret.replaceParameter(QueryParam.viewName.toString(), _customView.getName());
        }
        return ret;
    }

    public boolean isExportAsWebPage()
    {
        return _exportAsWebPage;
    }

    public void setExportAsWebPage(boolean exportAsWebPage)
    {
        _exportAsWebPage = exportAsWebPage;
    }

    public void setViewName(String name)
    {
        if (null != _customView || null != _querySettings)
            throw new IllegalStateException();
        _viewName = name;
    }

    public String getViewName()
    {
        return getQuerySettings() != null ? getQuerySettings().getViewName() : _viewName;
    }
    
    public CustomView getCustomView()
    {
        if (_customView != null)
            return _customView;
        if (getQuerySettings() == null)
            return null;
        String columnListName = getViewName();
        QueryDefinition querydef = getQueryDef();
        if (null == querydef)
        {
            throw new NotFoundException();
        }
        _customView = querydef.getCustomView(getUser(), getViewContext().getRequest(), columnListName);
        return _customView;
    }

    public void setCustomView(CustomView customView)
    {
        _customView = customView;
    }

    public boolean canEdit()
    {
        return null != getQueryDef() && getQueryDef().canEdit(getUser());
    }

    public String getQueryViewActionURL()
    {
        return _queryViewActionURL;
    }

    public void setQueryViewActionURL(String queryViewActionURL)
    {
        _queryViewActionURL = queryViewActionURL;
    }
}
