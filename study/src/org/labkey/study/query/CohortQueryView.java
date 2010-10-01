/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.StudyDefinitionController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jgarms
 * Date: Jul 25, 2008
 * Time: 10:44:34 AM
 */
public class CohortQueryView extends ExtensibleObjectQueryView
{
    private final StudyImpl study;

    public CohortQueryView(User user, StudyImpl study, ViewContext viewContext, boolean allowEditing)
    {
        super(user, study, CohortImpl.DOMAIN_INFO, viewContext, allowEditing);
        this.study = study;
        QuerySettings settings = getSettings();
        // We don't have many cohorts typically. Let's cut down on the number of buttons,
        // as this isn't a complex view
        settings.setAllowChooseView(false);
        setShowPagination(false);
        setShowPaginationCount(false);
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (allowEditing())
        {
            ActionURL insertURL = new ActionURL(CohortController.InsertAction.class, getSchema().getContainer());
            ActionButton insertButton = new ActionButton(insertURL, "Insert New");
            insertButton.setActionType(ActionButton.Action.GET); // the target is a form handler, so we need to start with a GET
            bar.add(insertButton);

            ActionURL deleteUnusedURL = new ActionURL(CohortController.DeleteUnusedCohortsAction.class, getSchema().getContainer());
            bar.add(new ActionButton(deleteUnusedURL, "Delete Unused"));

            ActionURL editDefinitionURL = new ActionURL(StudyDefinitionController.EditCohortDefinitionAction.class, getSchema().getContainer());
            bar.add(new ActionButton(editDefinitionURL, "Edit Cohort Definition"));
        }
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        if (allowEditing() &&
                getUser().isAdministrator() &&
                StudyManager.getInstance().showCohorts(getContainer(), getUser()))
        {
            TableInfo tableInfo = view.getDataRegion().getTable();
            ColumnInfo rowIdColumn = tableInfo.getColumn("rowId");

            Container container = view.getRenderContext().getContainer();

            boolean hasDomain = false;
            String domainURI = CohortImpl.DOMAIN_INFO.getDomainURI(container);

            Domain domain = PropertyService.get().getDomain(container, domainURI);
            if (domain != null)
            {
                DomainProperty[] extraProperties = domain.getProperties();
                if (extraProperties.length > 0)
                {
                    hasDomain = true;
                }
            }

            boolean manualAssign = study.isManualCohortAssignment();

            view.getDataRegion().addDisplayColumn(0, new CohortEditColumn(container, rowIdColumn, hasDomain, manualAssign));
            view.getDataRegion().addDisplayColumn(1, new CohortDeleteColumn(container, rowIdColumn));
        }
        return view;
    }

    private class CohortEditColumn extends SimpleDisplayColumn
    {
        private final ColumnInfo rowIdColumn;
        private final Container container;
        private final boolean hasDomain;
        private final boolean manualAssign;

        public CohortEditColumn(Container container, ColumnInfo rowIdColumn, boolean hasDomain, boolean manualAssign)
        {
            this.container = container;
            this.rowIdColumn = rowIdColumn;
            this.hasDomain = hasDomain;
            this.manualAssign = manualAssign;
            setWidth(null);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Integer rowId = (Integer)rowIdColumn.getValue(ctx);

            if (manualAssign)
            {
                // If it's manual assignment, the user can always edit the cohort, no matter what
                renderEditLink(out, rowId.intValue());
                return;
            }

            if (hasDomain)
            {
                // There are properties to edit
                renderEditLink(out, rowId.intValue());
            }

            // Now we know we've got no properties, and we're using automatic assignment.
            // Don't show a link.
        }

        private void renderEditLink(Writer out, int rowId) throws IOException
        {
            ActionURL actionURL = new ActionURL(CohortController.UpdateAction.class, container);
            actionURL.addParameter("rowId", rowId);

            out.write(PageFlowUtil.textLink("edit", actionURL.getLocalURIString()));
        }
    }

    private class CohortDeleteColumn extends SimpleDisplayColumn
    {
        private final ColumnInfo rowIdColumn;
        private final Container container;

        public CohortDeleteColumn(Container container, ColumnInfo rowIdColumn)
        {
            this.container = container;
            this.rowIdColumn = rowIdColumn;
            setWidth(null);
            setTextAlign("center");
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Integer rowId = (Integer)rowIdColumn.getValue(ctx);
            boolean inUse = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), rowId.intValue()).isInUse();

            if (!inUse)
            {
                ActionURL actionURL = new ActionURL(CohortController.DeleteCohortAction.class, container);
                actionURL.addParameter("rowId", rowId.toString());

                out.write(PageFlowUtil.textLink("delete", actionURL.getLocalURIString()));
            }
            else
            {
                out.write("in use");
            }
        }
    }
}
