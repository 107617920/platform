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

package org.labkey.study.query;

import org.labkey.api.data.*;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.model.SampleRequestStatus;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.security.permissions.ManageRequestsPermission;
import org.labkey.study.security.permissions.RequestSpecimensPermission;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Set;

/**
 * User: brittp
 * Date: Apr 20, 2007
 * Time: 2:49:42 PM
 */
public class SpecimenRequestQueryView extends BaseStudyQueryView
{
    private NavTree[] _extraLinks;
    private boolean _allowSortAndFilter = true;
    private boolean _showOptionLinks = true;

    private static class RequestOptionDisplayColumn extends SimpleDisplayColumn
    {
        private ColumnInfo _colCreatedBy;
        private ColumnInfo _colStatus;

        private boolean _showOptionLinks;
        private NavTree[] _extraLinks;
        private Integer _shoppingCartStatusRowId;
        private boolean _userIsSpecimenManager;
        private boolean _userCanRequest;
        private int _userId;
        private boolean _cartEnabled;

        public RequestOptionDisplayColumn(ViewContext context, boolean showOptionLinks, ColumnInfo colCreatedBy, ColumnInfo colStatus, NavTree... extraLinks)
        {
            _colStatus = colStatus;
            _colCreatedBy = colCreatedBy;
            User user = context.getUser();
            _userId = user.getUserId();
            _userIsSpecimenManager = context.getContainer().hasPermission(user, ManageRequestsPermission.class);
            _userCanRequest = context.getContainer().hasPermission(user, RequestSpecimensPermission.class);
            _showOptionLinks = showOptionLinks;
            _extraLinks = extraLinks;
            setTextAlign("right");
            setNoWrap(true);
            setWidth("175em");
            try
            {
                _cartEnabled = SampleManager.getInstance().isSpecimenShoppingCartEnabled(context.getContainer());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> set)
        {
            set.add(_colCreatedBy);
            set.add(_colStatus);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            StringBuilder content = new StringBuilder();

            if (_extraLinks != null)
            {
                for (NavTree link : _extraLinks)
                    content.append(PageFlowUtil.generateButton(link.getKey(), link.getValue())).append(" ");
            }

            if (_showOptionLinks)
            {
                if (_cartEnabled)
                {
                    if (_shoppingCartStatusRowId == null)
                    {
                        try
                        {
                            SampleRequestStatus cartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(ctx.getContainer(),
                                    ctx.getViewContext().getUser());
                            _shoppingCartStatusRowId = cartStatus.getRowId();
                        }
                        catch (SQLException e)
                        {
                            throw new RuntimeSQLException(e);
                        }
                    }

                    Integer statusId = (Integer) _colStatus.getValue(ctx);
                    Integer ownerId = (Integer) _colCreatedBy.getValue(ctx);
                    boolean canEdit = _userIsSpecimenManager || (_userCanRequest && ownerId == _userId);

                    if (statusId.intValue() == _shoppingCartStatusRowId.intValue() && canEdit)
                    {
                        String submitLink = (new ActionURL(SpecimenController.SubmitRequestAction.class, ctx.getContainer())).toString() + "id=${requestId}";
                        String cancelLink = (new ActionURL(SpecimenController.DeleteRequestAction.class, ctx.getContainer())).toString() + "id=${requestId}";

                        content.append(PageFlowUtil.generateButton("Submit", submitLink,
                                "return confirm('" + SpecimenController.ManageRequestBean.SUBMISSION_WARNING + "')")).append(" ");
                        content.append(PageFlowUtil.generateButton("Cancel", cancelLink,
                                "return confirm('" + SpecimenController.ManageRequestBean.CANCELLATION_WARNING + "')")).append(" ");
                    }
                }

                String detailsLink = (new ActionURL(SpecimenController.ManageRequestAction.class, ctx.getContainer())).toString() + "id=${requestId}";
                content.append(PageFlowUtil.generateButton("Details", detailsLink));
            }

            setDisplayHtml(content.toString());
            super.renderGridCellContents(ctx, out);
        }
    }

    protected SpecimenRequestQueryView(ViewContext context, UserSchema schema, QuerySettings settings, SimpleFilter filter, Sort sort, NavTree... extraLinks)
    {
        super(context, schema, settings, filter, sort);
        _extraLinks = extraLinks;
    }

    public static SpecimenRequestQueryView createView(ViewContext context)
    {
        return createView(context, null);
    }

    public static SpecimenRequestQueryView createView(ViewContext context, SimpleFilter filter)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
        StudyQuerySchema schema = new StudyQuerySchema(study, context.getUser(), true);
        String queryName = "SpecimenRequest";
        QuerySettings qs = schema.getSettings(context, queryName, queryName);
        return new SpecimenRequestQueryView(context, schema, qs, addFilterClauses(filter), createDefaultSort());
    }
    
    private static Sort createDefaultSort()
    {
        return new Sort("-Created");
    }

    private static SimpleFilter addFilterClauses(SimpleFilter filter)
    {
        if (filter == null)
            filter = new SimpleFilter();
        filter.addCondition("Hidden", Boolean.FALSE);
        return filter;
    }

    @Override
    protected DataRegion.ButtonBarPosition getButtonBarPosition()
    {
        return DataRegion.ButtonBarPosition.TOP;
    }

    @Override
    protected DataRegion createDataRegion()
    {
        DataRegion rgn = super.createDataRegion();
        rgn.setShadeAlternatingRows(true);
        rgn.setShowBorders(true);
        DataColumn commentsDC = (DataColumn) rgn.getDisplayColumn("Comments");
        if (commentsDC != null)
        {
            commentsDC.setWidth("300px");
            commentsDC.setPreserveNewlines(true);
        }

        if (_showOptionLinks || (_extraLinks != null && _extraLinks.length > 0))
        {
            RequestOptionDisplayColumn optionCol =
                    new RequestOptionDisplayColumn(getViewContext(), _showOptionLinks, getTable().getColumn("CreatedBy"),
                            getTable().getColumn("Status"), _extraLinks);
            rgn.addDisplayColumn(0, optionCol);
        }
        rgn.setSortable(_allowSortAndFilter);
        rgn.setShowFilters(_allowSortAndFilter);
        return rgn;
    }

    public void setExtraLinks(boolean showOptionLinks, NavTree... extraLinks)
    {
        _showOptionLinks = showOptionLinks;
        _extraLinks = extraLinks;
    }

    public void setAllowSortAndFilter(boolean allowSortAndFilter)
    {
        _allowSortAndFilter = allowSortAndFilter;
    }

    public void setShowCustomizeLink(boolean showCustomizeLink)
    {
        getSettings().setAllowCustomizeView(showCustomizeLink);
    }
}
