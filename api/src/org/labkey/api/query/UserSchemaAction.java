/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 9/29/12
 */
public abstract class UserSchemaAction extends FormViewAction<QueryUpdateForm>
{
    protected QueryForm _form;
    protected UserSchema _schema;
    protected TableInfo _table;

    public BindException bindParameters(PropertyValues m) throws Exception
    {
        _form = createQueryForm(getViewContext());
        _schema = _form.getSchema();
        if (null == _schema)
        {
            throw new NotFoundException("Schema not found");
        }
        _table = _schema.getTable(_form.getQueryName(), true, true);
        if (null == _table)
        {
            throw new NotFoundException("Query not found");
        }
        _table.overlayMetadata(_table.getName(), _schema, new ArrayList<QueryException>());
        QueryUpdateForm command = new QueryUpdateForm(_table, getViewContext(), null);
        BindException errors = new NullSafeBindException(new BeanUtilsPropertyBindingResult(command, "form"));
        command.validateBind(errors);
        return errors;
    }

    protected QueryForm createQueryForm(ViewContext context)
    {
        QueryForm form = new QueryForm();
        form.setViewContext(getViewContext());
        form.bindParameters(getViewContext().getBindPropertyValues());

        return form;
    }

    public void validateCommand(QueryUpdateForm target, Errors errors)
    {
    }

    protected ButtonBar createSubmitCancelButtonBar(QueryUpdateForm tableForm)
    {
        ButtonBar bb = new ButtonBar();
        bb.setStyle(ButtonBar.Style.separateButtons);
        String submitGUID = "submit-" + GUID.makeGUID();
        String cancelGUID = "cancel-" + GUID.makeGUID();
        String disableButtonScript = "Ext.get('" + submitGUID + "').replaceClass('labkey-button', 'labkey-disabled-button'); Ext.get('" + cancelGUID + "').replaceClass('labkey-button', 'labkey-disabled-button'); return true;";
        ActionButton btnSubmit = new ActionButton(getViewContext().getActionURL(), "Submit");
        btnSubmit.setScript(disableButtonScript);
        btnSubmit.setActionType(ActionButton.Action.POST);
        btnSubmit.setId(submitGUID);
        ActionButton btnCancel = new ActionButton(getCancelURL(tableForm), "Cancel");
        btnCancel.setId(cancelGUID);
        bb.add(btnSubmit);
        bb.add(btnCancel);
        return bb;
    }

    public ActionURL getSuccessURL(QueryUpdateForm form)
    {
        String returnURL = getViewContext().getRequest().getParameter(QueryParam.srcURL.toString());
        if (returnURL != null)
            return new ActionURL(returnURL);
        return _schema.urlFor(QueryAction.executeQuery, _form.getQueryDef());
    }

    public ActionURL getCancelURL(QueryUpdateForm form)
    {
        ActionURL cancelURL;
        if (getViewContext().getActionURL().getParameter(QueryParam.srcURL) != null)
        {
            cancelURL = new ActionURL(getViewContext().getActionURL().getParameter(QueryParam.srcURL));
        }
        else if (_schema != null && _table != null)
        {
            cancelURL = _schema.urlFor(QueryAction.executeQuery, _form.getQueryDef());
        }
        else
        {
            cancelURL = QueryService.get().urlDefault(form.getContainer(), QueryAction.executeQuery, null, null);
            //cancelURL = new ActionURL(ExecuteQueryAction.class, form.getContainer());
        }
        return cancelURL;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        if (_table != null)
            root.addChild(_table.getName(), getSuccessURL(null));
        return root;
    }

    protected void doInsertUpdate(QueryUpdateForm form, BindException errors, boolean insert) throws Exception
    {
        TableInfo table = form.getTable();
        if (!table.hasPermission(form.getUser(), insert ? InsertPermission.class : UpdatePermission.class))
        {
            throw new UnauthorizedException();
        }

        Map<String, Object> values = form.getTypedColumns();

        // Allow for attachment-based columns
        Map<String, MultipartFile> fileMap = getFileMap();
        if (null != fileMap)
        {
            for (String key : fileMap.keySet())
            {
                // Check if the column has already been processed
                if (!values.containsKey(key))
                {
                    SpringAttachmentFile file = new SpringAttachmentFile(fileMap.get(key));
                    form.setTypedValue(key, file.isEmpty() ? null : file);
                }
            }
        }

        QueryUpdateService qus = table.getUpdateService();
        if (qus == null)
            throw new IllegalArgumentException("The query '" + _table.getName() + "' in the schema '" + _schema.getName() + "' is not updatable.");

        DbSchema dbschema = table.getSchema();
        try
        {
            dbschema.getScope().ensureTransaction();

            if (insert)
            {
                BatchValidationException batchErrors = new BatchValidationException();
                qus.insertRows(form.getUser(), form.getContainer(), Collections.singletonList(values), batchErrors, null);
                if (batchErrors.hasErrors())
                    throw batchErrors;
            }
            else
            {
                Map<String, Object> oldValues = null;
                if (form.getOldValues() instanceof Map)
                {
                    oldValues = (Map<String, Object>)form.getOldValues();
                    if (!(oldValues instanceof CaseInsensitiveMapWrapper))
                        oldValues = new CaseInsensitiveMapWrapper<Object>(oldValues);
                }
                qus.updateRows(form.getUser(), form.getContainer(), Collections.singletonList(values), Collections.singletonList(oldValues), null);
            }

            dbschema.getScope().commitTransaction();
        }
        catch (SQLException x)
        {
            if (!SqlDialect.isConstraintException(x))
                throw x;
            errors.reject(SpringActionController.ERROR_MSG, x.getMessage());
        }
        catch (BatchValidationException x)
        {
            x.addToErrors(errors);
        }
        catch (Exception x)
        {
            errors.reject(SpringActionController.ERROR_MSG, null == x.getMessage() ? x.toString() : x.getMessage());
            ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), x);
        }
        finally
        {
            dbschema.getScope().closeConnection();
            UserManager.clearUserList(form.getUser().getUserId());
        }
    }
}
