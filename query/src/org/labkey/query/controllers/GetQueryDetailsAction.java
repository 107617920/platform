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
package org.labkey.query.controllers;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.query.CustomViewUtil;
import org.springframework.validation.BindException;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Sep 3, 2009
 * Time: 3:36:07 PM
 */
@RequiresPermissionClass(ReadPermission.class)
public class GetQueryDetailsAction extends ApiAction<GetQueryDetailsAction.Form>
{
    public ApiResponse execute(Form form, BindException errors) throws Exception
    {
        ApiSimpleResponse resp = new ApiSimpleResponse();

        Container container = getViewContext().getContainer();
        User user = getViewContext().getUser();
        QuerySchema schema = DefaultSchema.get(user, container).getSchema(form.getSchemaName());
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + form.getSchemaName() + "' in the folder '" + container.getPath() + "'!");

        //a few basic props about the query
        //this needs to be populated before attempting to get the table info
        //so that the client knows if this query is user defined or not
        //so it can display edit source, edit design links
        resp.put("name", form.getQueryName());
        resp.put("schemaName", form.getSchemaName());
        Map<String,QueryDefinition> queryDefs = QueryService.get().getQueryDefs(user, container, form.getSchemaName());
        boolean isUserDefined = (null != queryDefs && queryDefs.containsKey(form.getQueryName()));
        resp.put("isUserDefined", isUserDefined);
        boolean canEdit = (null != queryDefs && queryDefs.containsKey(form.getQueryName()) && queryDefs.get(form.getQueryName()).canEdit(user));
        resp.put("canEdit", canEdit);
        resp.put("canEditSharedViews", container.hasPermission(user, EditSharedViewPermission.class));
        resp.put("isMetadataOverrideable", canEdit); //for now, this is the same as canEdit(), but in the future we can support this for non-editable queries

        QueryDefinition querydef = (null == queryDefs ? null : queryDefs.get(form.getQueryName()));
        boolean isInherited = (null != querydef && querydef.canInherit() && !container.equals(querydef.getContainer()));
        resp.put("isInherited", isInherited);
        if (isInherited)
            resp.put("containerPath", querydef.getContainer().getPath());

        TableInfo tinfo;
        try
        {
            tinfo = schema.getTable(form.getQueryName());
        }
        catch(Exception e)
        {
            resp.put("exception", e.getMessage());
            return resp;
        }

        if (null == tinfo)
            throw new IllegalArgumentException("Could not find the query '" + form.getQueryName() + "' in the schema '" + form.getSchemaName() + "'!");

        if (!isUserDefined && tinfo.isMetadataOverrideable())
            resp.put("isMetadataOverrideable", true);
        
        //8649: let the table provide the view data url
        if (schema instanceof UserSchema)
        {
            UserSchema uschema = (UserSchema)schema;
            QueryDefinition qdef = QueryService.get().createQueryDefForTable(uschema, form.getQueryName());
            ActionURL viewDataUrl = qdef == null ? null : uschema.urlFor(QueryAction.executeQuery, qdef);
            if (null != viewDataUrl)
                resp.put("viewDataUrl", viewDataUrl);
        }

        //if the caller asked us to chase a foreign key, do that
        FieldKey fk = null;
        if (null != form.getFk())
        {
            fk = FieldKey.fromString(form.getFk());
            Map<FieldKey,ColumnInfo> colMap = QueryService.get().getColumns(tinfo, Collections.singletonList(fk));
            ColumnInfo cinfo = colMap.get(fk);
            if (null == cinfo)
                throw new IllegalArgumentException("Could not find the column '" + form.getFk() + "' starting from the query " + form.getSchemaName() + "." + form.getQueryName() + "!");
            if (null == cinfo.getFk() || null == cinfo.getFkTableInfo())
                throw new IllegalArgumentException("The column '" + form.getFk() + "' is not a foreign key!");
            tinfo = cinfo.getFkTableInfo();
        }
        
        if (null != tinfo.getDescription())
            resp.put("description", tinfo.getDescription());

        Collection<FieldKey> fields = Collections.emptyList();
        if (null != form.getAdditionalFields())
        {
            String[] additionalFields = form.getAdditionalFields().split(",");
            fields = new ArrayList<FieldKey>(additionalFields.length);
            for (int i = 0; i < additionalFields.length; i++)
                fields.add(FieldKey.fromString(additionalFields[i]));
        }

        //now the native columns plus any additional fields requested
        resp.put("columns", JsonWriter.getNativeColProps(tinfo, fields, fk));

        if (schema instanceof UserSchema && null == form.getFk())
        {
            //now the columns in the user's default view for this query
            resp.put("defaultView", getDefaultViewProps((UserSchema)schema, form.getQueryName()));

            List<Map<String, Object>> viewInfos = new ArrayList<Map<String, Object>>();
            // form.getViewName() is either null, a String, or a comma separated list of Strings.
            String[] viewNames;
            if (form.getViewName() != null)
                viewNames = form.getViewName().split(",");
            else
                viewNames = new String[] { form.getViewName() };
            for (String viewName : viewNames)
            {
                viewName = StringUtils.trimToNull(viewName);
                viewInfos.add(CustomViewUtil.toMap(getViewContext(), (UserSchema)schema, form.getQueryName(), viewName, true));
            }

            resp.put("views", viewInfos);

            // add a create or edit url for the associated domain.
            DomainKind kind = tinfo.getDomainKind();
            if (kind == null)
            {
                String domainURI = null;
                try
                {
                    domainURI = ((UserSchema) schema).getDomainURI(tinfo.getName());
                }
                catch (NotFoundException nfe) { }

                if (domainURI != null)
                    kind = PropertyService.get().getDomainKind(domainURI);
            }
            
            if (kind != null)
            {
                Domain domain = tinfo.getDomain();
                if (domain != null)
                {
                    if (kind.canEditDefinition(user, domain))
                        resp.put("editDefinitionUrl", kind.urlEditDefinition(domain));
                }
                else
                {
                    // Yes, some tables exist before their Domain does
                    if (kind.canCreateDefinition(user, container))
                        resp.put("createDefinitionUrl", kind.urlCreateDefinition(schema.getName(), tinfo.getName(), container, user));
                }
            }
        }

        return resp;
    }

    protected Map<String,Object> getDefaultViewProps(UserSchema schema, String queryName)
    {
        //build a query view
        QuerySettings settings = schema.getSettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, queryName);
        QueryView view = new QueryView(schema, settings, null);

        Map<String,Object> defViewProps = new HashMap<String,Object>();
        defViewProps.put("columns", getDefViewColProps(view));
        return defViewProps;
    }

    protected List<Map<String,Object>> getDefViewColProps(QueryView view)
    {
        List<Map<String,Object>> colProps = new ArrayList<Map<String,Object>>();
        for (DisplayColumn dc : view.getDisplayColumns())
        {
            if (dc.isQueryColumn() && null != dc.getColumnInfo())
                colProps.add(JsonWriter.getMetaData(dc, null, true, true));
        }
        return colProps;
    }

    public static class Form
    {
        private String _queryName;
        private String _schemaName;
        private String _viewName;
        private String _fk;
        private String _additionalFields; 

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public String getViewName()
        {
            return _viewName;
        }

        public void setViewName(String viewName)
        {
            _viewName = viewName;
        }

        public String getFk()
        {
            return _fk;
        }

        public void setFk(String fk)
        {
            _fk = fk;
        }

        public String getAdditionalFields()
        {
            return _additionalFields;
        }

        public void setFields(String fields)
        {
            _additionalFields = fields;
        }
    }
}
