/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.*;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.HString;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.FieldKey;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:09:28 PM
*/
@RequiresPermissionClass(InsertPermission.class)
public class PublishStartAction extends BaseAssayAction<PublishStartAction.PublishForm>
{
    private ExpProtocol _protocol;

    public static class PublishForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _dataRegionSelectionKey;
        private String _containerFilterName;
        private boolean _runIds;

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public String getContainerFilterName()
        {
            return _containerFilterName;
        }

        public void setContainerFilterName(String containerFilterName)
        {
            _containerFilterName = containerFilterName;
        }

        public boolean isRunIds()
        {
            return _runIds;
        }

        public void setRunIds(boolean runIds)
        {
            _runIds = runIds;
        }
    }

    public class PublishBean
    {
        private List<Integer> _ids;
        private AssayProvider _provider;
        private ExpProtocol _protocol;
        private Set<Container> _studies;
        private boolean _nullStudies;
        private boolean _insufficientPermissions;
        private String _dataRegionSelectionKey;
        private final HString _returnURL;
        private final String _containerFilterName;

        public PublishBean(AssayProvider provider, ExpProtocol protocol,
                           List<Integer> ids, String dataRegionSelectionKey,
                           Set<Container> studies, boolean nullStudies, boolean insufficientPermissions, HString returnURL,
                           String containerFilterName)
        {
            _insufficientPermissions = insufficientPermissions;
            _provider = provider;
            _protocol = protocol;
            _studies = studies;
            _nullStudies = nullStudies;
            _ids = ids;
            _dataRegionSelectionKey = dataRegionSelectionKey;
            _returnURL = returnURL;
            _containerFilterName = containerFilterName;
        }

        public HString getReturnURL()
        {
            if (_returnURL != null)
            {
                return _returnURL;
            }
            return new HString(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getViewContext().getContainer(), getProtocol()).addParameter("clearDataRegionSelectionKey", getDataRegionSelectionKey()).toString());
        }

        public List<Integer> getIds()
        {
            return _ids;
        }
        
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public Set<Container> getStudies()
        {
            return _studies;
        }

        public boolean isNullStudies()
        {
            return _nullStudies;
        }

        public AssayProvider getProvider()
        {
            return _provider;
        }

        public ExpProtocol getProtocol()
        {
            return _protocol;
        }

        public boolean isInsufficientPermissions()
        {
            return _insufficientPermissions;
        }

        public String getContainerFilterName()
        {
            return _containerFilterName;
        }
    }

    public ModelAndView getView(PublishForm publishForm, BindException errors) throws Exception
    {
        _protocol = publishForm.getProtocol();
        AssayProvider provider = publishForm.getProvider();

        List<Integer> ids;
        AssayTableMetadata tableMetadata = provider.getTableMetadata(_protocol);
        if (publishForm.isRunIds())
        {
            // Need to convert the run ids into data row ids
            List<Integer> runIds = getCheckboxIds();
            DataRegionSelection.clearAll(getViewContext(), null);
            // Get the assay results table
            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getContainer(), AssaySchema.NAME);
            TableInfo table = schema.getTable(AssayService.get().getResultsTableName(_protocol));
            if (table.supportsContainerFilter() && publishForm.getContainerFilterName() != null)
            {
                ((ContainerFilterable)table).setContainerFilter(ContainerFilter.getContainerFilterByName(publishForm.getContainerFilterName(), getViewContext().getUser()));
            }
            ColumnInfo dataRowIdColumn = QueryService.get().getColumns(table, Collections.singleton(tableMetadata.getResultRowIdFieldKey())).get(tableMetadata.getResultRowIdFieldKey());
            assert dataRowIdColumn  != null : "Could not find dataRowId column in assay results table";
            FieldKey runFieldKey = tableMetadata.getRunRowIdFieldKeyFromResults();
            ColumnInfo runIdColumn = QueryService.get().getColumns(table, Collections.singleton(runFieldKey)).get(runFieldKey);
            assert runIdColumn  != null : "Could not find runId column in assay results table";

            // Filter it to get only the rows from this set of runs
            SimpleFilter filter = new SimpleFilter();
            filter.addClause(new SimpleFilter.InClause(runFieldKey.toString(), runIds, true));

            ResultSet rs = Table.selectForDisplay(table, Arrays.asList(dataRowIdColumn, runIdColumn), null, filter, new Sort(runFieldKey.toString()), Table.ALL_ROWS, Table.NO_OFFSET);
            try
            {
                // Pull out the data row ids
                ids = new ArrayList<Integer>();
                while (rs.next())
                {
                    ids.add(dataRowIdColumn.getIntValue(rs));
                }
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
            }
        }
        else
        {
            ids = getCheckboxIds();
        }

        Set<Container> containers = new HashSet<Container>();
        boolean nullsFound = false;
        boolean insufficientPermissions = false;
        for (Integer id : ids)
        {
            Container studyContainer = provider.getAssociatedStudyContainer(_protocol, id);
            if (studyContainer == null)
                nullsFound = true;
            else
            {
                if (!studyContainer.hasPermission(getViewContext().getUser(), InsertPermission.class))
                    insufficientPermissions = true;
                containers.add(studyContainer);
            }
        }

        return new JspView<PublishBean>("/org/labkey/study/assay/view/publishChooseStudy.jsp",
                new PublishBean(provider,
                    _protocol,
                    ids,
                    publishForm.getDataRegionSelectionKey(),
                    containers,
                    nullsFound,
                    insufficientPermissions,
                    publishForm.getReturnUrl(),
                    publishForm.getContainerFilterName()));
    }

    public NavTree appendNavTrail(NavTree root)
    {
        getPageConfig().setHelpTopic(new HelpTopic("publishAssayData"));
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        result.addChild("Copy to Study: Choose Target");
        return result;
    }
}
