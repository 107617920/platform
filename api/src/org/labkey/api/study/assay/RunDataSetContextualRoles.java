/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.security.roles.HasContextualRoles;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.StudyService;
import org.jetbrains.annotations.Nullable;
import org.apache.commons.lang.math.NumberUtils;

import java.util.*;
import java.sql.SQLException;

/**
 * User: kevink
 * Date: Jun 1, 2009 1:02:56 PM
 */
public class RunDataSetContextualRoles implements HasContextualRoles
{
    /**
     * Returns a contextual ReaderRole if the user has permission to
     * <b>at least one of</b> the study datasets that the run results have
     * been copied to.
     *
     * @return a singleton ReaderRole set or null
     */
    @Nullable
    public Set<Role> getContextualRoles(ViewContext context)
    {
        String rowIdStr = context.getRequest().getParameter("rowId");
        if (rowIdStr != null)
        {
            int runRowId = NumberUtils.toInt(rowIdStr);
            return RunDataSetContextualRoles.getContextualRolesForRun(context.getContainer(), context.getUser(), runRowId);
        }
        return null;
    }

    /**
     * Returns a contextual ReaderRole if the user has permission to
     * <b>at least one of</b> the study datasets that the run results have
     * been copied to.
     *
     * @param container the container
     * @param user the user
     * @param runId the run to check
     * @return a singleton ReaderRole set or null
     */
    @Nullable
    public static Set<Role> getContextualRolesForRun(Container container, User user, int runId)
    {
        ExpRun run = ExperimentService.get().getExpRun(runId);
        if (run == null)
            return null;

        return getContextualRolesForRun(container, user, run);
    }

    @Nullable
    public static Set<Role> getContextualRolesForRun(Container container, User user, ExpRun run)
    {
        if (container == null || user == null)
            return null;

        // skip the check if the user has ReadPermission to the container
        if (container.hasPermission(user, ReadPermission.class))
            return null;

        ExpProtocol protocol = run.getProtocol();
        if (protocol == null)
            return null;

        AssayProvider provider = AssayService.get().getProvider(protocol);
        AssaySchema schema = AssayService.get().createSchema(user, container);

        // get the results table and the set of dataset columns
        TableInfo resultsTable = provider.createDataTable(schema, protocol);
        Set<String> columnNames = resultsTable.getColumnNameSet();
        Set<String> datasetColumnNames = new LinkedHashSet<String>();
        for (String columnName : columnNames)
        {
            if (columnName.startsWith("dataset"))
                datasetColumnNames.add(columnName);
        }

        // table contains no dataset columns if results haven't been copied
        if (datasetColumnNames.size() == 0)
            return null;

        Map<String, Object>[] results = null;
        try
        {
            results = Table.selectMaps(resultsTable, datasetColumnNames, new SimpleFilter("runid", run.getRowId()), null);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (results == null || results.length == 0)
            return null;

        List<ColumnInfo> datasetColumns = resultsTable.getColumns(datasetColumnNames.toArray(new String[datasetColumnNames.size()]));
        for (Map<String, Object> result : results)
        {
            for (ColumnInfo datasetColumn : datasetColumns)
            {
                if (!(datasetColumn instanceof StudyDataSetColumn))
                    continue;
                Integer datasetId = (Integer)result.get(datasetColumn.getName());
                if (datasetId == null)
                    continue;

                Container studyContainer = ((StudyDataSetColumn)datasetColumn).getStudyContainer();
                DataSet dataset = StudyService.get().getDataSet(studyContainer, datasetId.intValue());
                SecurityPolicy policy = dataset.getPolicy();
                if (policy.hasPermission(user, ReadPermission.class))
                    return Collections.<Role>singleton(new ReaderRole());
            }
        }

        return null;
    }
}
