/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ObjectUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.Map;

/**
 * User: jeckels
 * Date: 7/1/11
 */
public class AssayResultUpdateService extends DefaultQueryUpdateService
{
    private final AssaySchema _schema;

    public AssayResultUpdateService(AssaySchema schema, AssayResultTable table)
    {
        super(table, table.getRealTable());
        _schema = schema;
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        _schema.getProtocol();

        Map<String, Object> originalRow = getRow(user, container, oldRow);
        if (originalRow == null)
        {
            throw new InvalidKeyException("Could not find row");
        }

        ExpRun run = getRun(originalRow, user, UpdatePermission.class);

        Map<String, Object> result = super.updateRow(user, container, row, oldRow);

        Map<String, Object> updatedValues = getRow(user, container, oldRow);

        StringBuilder sb = new StringBuilder("Data row, id " + oldRow.get("RowId") + ", edited.");
        for (Map.Entry<String, Object> entry : updatedValues.entrySet())
        {
            // Also check for properties
            ColumnInfo col = getQueryTable().getColumn(entry.getKey());
            if (col != null)
            {
                Object oldValue = originalRow.get(entry.getKey());
                Object newValue = entry.getValue();

                TableInfo fkTableInfo = col.getFkTableInfo();
                // Don't follow the lookup for specimen IDs, since their FK is very special and based on target study, etc
                if (!ObjectUtils.equals(oldValue, newValue) && fkTableInfo != null && !AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equalsIgnoreCase(entry.getKey()))
                {
                    // Do type conversion in case there's a mismatch in the lookup source and target columns
                    if (newValue != null && !fkTableInfo.getPkColumns().get(0).getJavaClass().isAssignableFrom(newValue.getClass()))
                    {
                        newValue = ConvertUtils.convert(newValue.toString(), fkTableInfo.getPkColumns().get(0).getJavaClass());
                    }
                    if (oldValue != null && !fkTableInfo.getPkColumns().get(0).getJavaClass().isAssignableFrom(oldValue.getClass()))
                    {
                        oldValue = ConvertUtils.convert(oldValue.toString(), fkTableInfo.getPkColumns().get(0).getJavaClass());
                    }
                    Map<String, Object> oldLookupTarget = Table.selectObject(fkTableInfo, oldValue, Map.class);
                    if (oldLookupTarget != null)
                    {
                        oldValue = oldLookupTarget.get(fkTableInfo.getTitleColumn());
                    }
                    Map<String, Object> newLookupTarget = Table.selectObject(fkTableInfo, newValue, Map.class);
                    if (newLookupTarget != null)
                    {
                        newValue = newLookupTarget.get(fkTableInfo.getTitleColumn());
                    }
                }
                appendPropertyIfChanged(sb, col.getLabel(), oldValue, newValue);
            }
        }
        ExperimentService.get().auditRunEvent(user, run.getProtocol(), run, sb.toString());

        return result;
    }

    private ExpRun getRun(Map<String, Object> row, User user, Class<? extends Permission> perm) throws InvalidKeyException
    {
        int dataId = ((Number) row.get("DataId")).intValue();
        ExpData data = ExperimentService.get().getExpData(dataId);
        if (data == null)
        {
            throw new InvalidKeyException("Could not find data object");
        }
        ExpRun run = data.getRun();
        if (run == null)
        {
            throw new InvalidKeyException("Could not find run object");
        }
        if (!run.getContainer().hasPermission(user, perm))
        {
            throw new UnauthorizedException("User does not have " + perm.getSimpleName() + " result in " + run.getContainer());
        }
        return run;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        ExpRun run = getRun(oldRowMap, user, DeletePermission.class);
        Map<String, Object> result = super.deleteRow(user, container, oldRowMap);

        ExperimentService.get().auditRunEvent(user, run.getProtocol(), run, "Deleted data row.");

        return result;
    }

    private StringBuilder appendPropertyIfChanged(StringBuilder sb, String label, Object oldValue, Object newValue)
    {
        if (!ObjectUtils.equals(oldValue, newValue))
        {
            sb.append(" ");
            sb.append(label);
            sb.append(" changed from ");
            sb.append(oldValue == null ? "blank" : "'" + oldValue + "'");
            sb.append(" to ");
            sb.append(newValue == null ? "blank" : "'" + newValue + "'");
            sb.append(".");
        }
        return sb;
    }
}
