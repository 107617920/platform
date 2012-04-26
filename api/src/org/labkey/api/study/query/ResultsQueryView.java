/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.api.study.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jun 29, 2007
 * Time: 11:13:44 AM
 */
public class ResultsQueryView extends AssayBaseQueryView
{
    public ResultsQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
    {
        this(protocol, AssayService.get().createSchema(context.getUser(), context.getContainer()), settings);
    }

    public ResultsQueryView(ExpProtocol protocol, AssaySchema schema, QuerySettings settings)
    {
        super(protocol, schema, settings);
    }

    @Override
    protected DataRegion createDataRegion()
    {
        ResultsDataRegion rgn = new ResultsDataRegion(_provider);
        initializeDataRegion(rgn);
        return rgn;
    }

    protected void initializeDataRegion(DataRegion rgn)
    {
        configureDataRegion(rgn);
        rgn.setShowRecordSelectors(true);
        rgn.setShadeAlternatingRows(true);
        rgn.setShowBorders(true);
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        view.getRenderContext().setBaseSort(new Sort(AssayService.get().getProvider(_protocol).getTableMetadata().getResultRowIdFieldKey().toString()));
        view.getDataRegion().addHiddenFormField("rowId", "" + _protocol.getRowId());
        String returnURL = getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name());
        if (returnURL == null)
            returnURL = getViewContext().getActionURL().toString();
        view.getDataRegion().addHiddenFormField(ActionURL.Param.returnUrl.returnUrl, returnURL);
        if (showControls())
        {
            if (!AssayPublishService.get().getValidPublishTargets(getUser(), InsertPermission.class).isEmpty())
            {
                ButtonBar bbar = new ButtonBar(view.getDataRegion().getButtonBar(DataRegion.MODE_GRID));

                AssayProvider provider = AssayService.get().getProvider(_protocol);

                ActionURL publishURL = PageFlowUtil.urlProvider(AssayUrls.class).getCopyToStudyURL(getContainer(), _protocol);
                for (Pair<String, String> param : publishURL.getParameters())
                {
                    if (!"rowId".equalsIgnoreCase(param.getKey()))
                        view.getDataRegion().addHiddenFormField(param.getKey(), param.getValue());
                }
                publishURL.deleteParameters();

                if (getTable().getContainerFilter() != null)
                    publishURL.addParameter("containerFilterName", getTable().getContainerFilter().getType().name());

                ActionButton publishButton = new ActionButton(publishURL,
                        "Copy to Study", DataRegion.MODE_GRID, ActionButton.Action.POST);
                publishButton.setDisplayPermission(InsertPermission.class);
                publishButton.setRequiresSelection(true);
                publishButton.setActionType(ActionButton.Action.POST);

                bbar.add(publishButton);

                bbar.addAll(AssayService.get().getImportButtons(_protocol, getViewContext().getUser(), getViewContext().getContainer(), false));

                view.getDataRegion().setButtonBar(bbar);
            }
        }
        else
            view.getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        return view;
    }

    protected TSVGridWriter.ColumnHeaderType getColumnHeaderType()
    {
        return TSVGridWriter.ColumnHeaderType.caption;
    }

    public static class ResultsDataRegion extends DataRegion
    {
        private ColumnInfo _matchColumn;
        private final AssayProvider _provider;

        public ResultsDataRegion(AssayProvider provider)
        {
            _provider = provider;
        }

        @Override
        protected boolean isErrorRow(RenderContext ctx, int rowIndex)
        {
            // If we know that the specimen info doesn't match, flag the row as being problematic
            return _matchColumn != null && Boolean.FALSE.equals(_matchColumn.getValue(ctx));
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            super.addQueryColumns(columns);
            FieldKey fk = new FieldKey(_provider.getTableMetadata().getSpecimenIDFieldKey(), AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME);
            Map<FieldKey, ColumnInfo> newColumns = QueryService.get().getColumns(getTable(), Collections.singleton(fk), columns);
            _matchColumn = newColumns.get(fk);
            if (_matchColumn != null)
            {
                // Add the column that decides if the specimen info has changed on the study side
                // Don't add until perf problems are resolved
//                columns.add(_matchColumn);
            }
        }
    }
}
