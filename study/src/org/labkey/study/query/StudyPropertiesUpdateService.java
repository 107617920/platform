/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.DateUtil;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.visitmanager.RelativeDateVisitManager;

import java.sql.SQLException;
import java.util.Date;
import java.util.Map;

/**
 * User: jgarms
 * Date: Aug 11, 2008
 * Time: 1:10:49 PM
 */
public class StudyPropertiesUpdateService extends AbstractQueryUpdateService
{
    public StudyPropertiesUpdateService(TableInfo table)
    {
        super(table);
    }


    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        StudyQuerySchema querySchema = new StudyQuerySchema(study, user, true);
        TableInfo queryTableInfo = querySchema.getTable("StudyProperties");
        Map<String,Object> result = Table.selectObject(queryTableInfo, container.getId(), Map.class);
        return result;
    }


    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        StudyPropertiesTable table = (StudyPropertiesTable)getQueryTable();

        // NOTE using updateTabDelimited() or updateStatement() is tricky, because they will NULL out all unspecified columns

        // save properties
        study.savePropertyBag(row);

        // update the base table, but only some columns StudyPropertiesTable
        CaseInsensitiveHashMap<Object> updateRow = new CaseInsensitiveHashMap<Object>();
        boolean recalculateVisits = false;
        for (Map.Entry<String,Object> entry : row.entrySet())
        {
            ColumnInfo c = table.getColumn(entry.getKey());
            if (null != c && c.isUserEditable() || (null != c && c.getName().equals("TimepointType") && study.isEmptyStudy()))
            {
                updateRow.put(entry.getKey(), entry.getValue());

                if (entry.getValue() != null && c.getName().equalsIgnoreCase("StartDate") && study.getTimepointType() == TimepointType.DATE)
                {
                    Date newDate = new Date(DateUtil.parseDateTime(entry.getValue().toString()));
                    recalculateVisits = !study.getStartDate().equals(newDate);
                }
            }
        }
        if (!updateRow.isEmpty())
            Table.update(user, table.getRealTable(), updateRow, study.getContainer().getId());

        if (recalculateVisits)
        {
            RelativeDateVisitManager visitManager = (RelativeDateVisitManager) StudyManager.getInstance().getVisitManager(study);
            visitManager.recomputeDates(study.getStartDate(), user);
        }
        StudyManager.getInstance().clearCaches(container, false);

        return getRow(user, container, null);
    }


    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("You cannot delete all of a Study's properties");
    }


    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("You cannot insert a new set of study properties");
    }
}
