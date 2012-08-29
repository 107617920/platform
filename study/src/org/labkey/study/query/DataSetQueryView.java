/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.CohortFilter;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.reports.StudyReportUIProvider;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Aug 25, 2006
 * Time: 4:03:59 PM
 */
public class DataSetQueryView extends QueryView
{
    private DataSetDefinition _dataset;
    private VisitImpl _visit;
    private CohortFilter _cohortFilter;
    private boolean _showSourceLinks;
    public static final String DATAREGION = "Dataset";
    private QCStateSet _qcStateSet;
    private ExpProtocol _protocol;
    private AssayProvider _provider;
    private Study _study;


    public DataSetQueryView(UserSchema schema, DataSetQuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);

        ViewContext context = getViewContext();
        DatasetFilterForm form = getForm(context);
        _study = StudyService.get().getStudy(getContainer());

        _dataset = StudyManager.getInstance().getDataSetDefinition(_study, settings.getQueryName());
        if (_dataset == null)
            throw new IllegalArgumentException("Unable to find the dataset specified");

        if (settings.isUseQCSet() && StudyManager.getInstance().showQCStates(getContainer()))
            _qcStateSet = QCStateSet.getSelectedStates(getContainer(), form.getQCState());

        _showSourceLinks = settings.isShowSourceLinks();

        // Only show link to edit if permission allows it
        setShowUpdateColumn(settings.isShowEditLinks() && !isExportView() && _dataset.canWrite(getUser()));

        getSettings().setAllowChooseQuery(false);
        getSettings().setAllowChooseView(false);

        if (form.getVisitRowId() != 0)
        {
            assert _study.getTimepointType() != TimepointType.CONTINUOUS;
            _visit = StudyManager.getInstance().getVisitForRowId(_study, form.getVisitRowId());
            if (null == _visit)
                throw new NotFoundException();
        }
        if (context.getActionURL() != null)
            _cohortFilter = CohortFilterFactory.getFromURL(getContainer(), getUser(), getViewContext().getActionURL());

        _protocol = _dataset.getAssayProtocol();
        if (_protocol != null)
            _provider = AssayService.get().getProvider(_protocol);

        setViewItemFilter(StudyReportUIProvider.getItemFilter());
        disableContainerFilterSelection();
    }

    private DatasetFilterForm getForm(ViewContext context)
    {
        DatasetFilterForm form = new DatasetFilterForm();

        form.bindParameters(context.getBindPropertyValues());
        return form;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        TableInfo table = view.getTable();

        view.getDataRegion().setShadeAlternatingRows(true);
        view.getDataRegion().setShowBorders(true);
        view.getDataRegion().setRecordSelectorValueColumns("lsid");

        if (null != _visit)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (null == filter)
            {
                filter = new SimpleFilter();
                view.getRenderContext().setBaseFilter(filter);
            }
            _visit.addVisitFilter(filter);
        }
        if (null != _cohortFilter)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (null == filter)
            {
                filter = new SimpleFilter();
                view.getRenderContext().setBaseFilter(filter);
            }
            _cohortFilter.addFilterCondition(table, getContainer(), filter);
        }
        if (null != _qcStateSet)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (null == filter)
            {
                filter = new SimpleFilter();
                view.getRenderContext().setBaseFilter(filter);
            }
            FieldKey qcStateKey = FieldKey.fromParts(DataSetTableImpl.QCSTATE_ID_COLNAME, "rowid");
            Map<FieldKey, ColumnInfo> qcStateColumnMap = QueryService.get().getColumns(table, Collections.singleton(qcStateKey));
            ColumnInfo qcStateColumn = qcStateColumnMap.get(qcStateKey);
            filter.addClause(new SimpleFilter.SQLClause(_qcStateSet.getStateInClause(qcStateColumn.getAlias()), null, qcStateColumn.getFieldKey()));
        }

        StudyManager.getInstance().applyDefaultFormats(getContainer(), view.getDataRegion().getDisplayColumns());
        ColumnInfo sourceLsidCol = table.getColumn("SourceLsid");
        DisplayColumn sourceLsidDisplayCol = view.getDataRegion().getDisplayColumn("SourceLsid");
        if (sourceLsidCol != null)
        {
            try {
                if (sourceLsidDisplayCol != null)
                    sourceLsidDisplayCol.setVisible(false);
                if (_showSourceLinks && hasSourceLsids() && hasUsefulDetailsPage())
                {
                    view.getDataRegion().addDisplayColumn(0, new DatasetDetailsColumn(sourceLsidCol, getUser()));
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }
        }

        // allow posts from dataset data regions to determine which dataset was being displayed:
        view.getDataRegion().addHiddenFormField(DataSetDefinition.DATASETKEY, "" + _dataset.getDataSetId());

        return view;
    }

    private boolean hasUsefulDetailsPage()
    {
        if (!_dataset.isAssayData())
            return true; // we don't have a protocol at all, so we don't know if we have useful details

        if (_protocol == null)
            return false; // We have a protocol, but it's been deleted

        if (_provider == null)
            return false; // Unlikely, but possible -- provider no longer available
        return _provider.hasUsefulDetailsPage();
    }

    private class DatasetDetailsColumn extends SimpleDisplayColumn
    {
        private final ColumnInfo _sourceLsidColumn;
        private final User _user;

        public DatasetDetailsColumn(ColumnInfo sourceLsidCol, User user)
        {
            super();
            _sourceLsidColumn = sourceLsidCol;
            _user = user;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object lsid = ctx.get(_sourceLsidColumn.getName());
            if (lsid != null)
            {
                if (LsidManager.get().hasPermission(lsid.toString(), _user, ReadPermission.class))
                {
                    ActionURL dataURL = new ActionURL(StudyController.DatasetItemDetailsAction.class, getContainer());
                    dataURL.addParameter("sourceLsid", lsid.toString());
                    out.write(PageFlowUtil.textLink("assay", dataURL));
                    return;
                }
            }
            out.write("&nbsp;");
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> set)
        {
            set.add(_sourceLsidColumn);
        }
    }

    @Override
    protected ActionURL urlFor(QueryAction action)
    {
        ActionURL url = super.urlFor(action);

        // need to add back the parameters that aren't added by the base urlFor, cohort and qc state
        // don't get automatically added because they lack the proper dataregion prefix
        //
        if (url != null)
        {
            if (_cohortFilter != null)
                _cohortFilter.addURLParameters(url);

            if (_qcStateSet != null)
                url.replaceParameter(BaseStudyController.SharedFormParameters.QCState, _qcStateSet.getFormValue());
        }
        return url;
    }

    protected void populateReportButtonBar(ButtonBar bar)
    {
        Report report = getSettings().getReportView(getViewContext());

        // query reports will render their own button bar
        if (!(report instanceof QueryReport))
        {
            MenuButton button = createViewButton(getViewItemFilter());
            button.addMenuItem("Set Default View", getViewContext().cloneActionURL().setAction(StudyController.ViewPreferencesAction.class));

            bar.add(button);
        }
    }

    public MenuButton createPageSizeMenuButton()
    {
        return super.createPageSizeMenuButton();
    }

    @Override
    public void addManageViewItems(MenuButton button, Map<String, String> params)
    {
        ActionURL url = new ActionURL(ReportsController.ManageReportsAction.class, getContainer());
        for (Map.Entry<String, String> entry : params.entrySet())
            url.addParameter(entry.getKey(), entry.getValue());

        button.addMenuItem("Manage Views", url);
    }

    @Override
    protected boolean canViewReport(User user, Container c, Report report)
    {
        return ReportManager.get().canReadReport(getUser(), getContainer(), report);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar, boolean exportAsWebPage)
    {
        bar.add(createViewButton(getItemFilter()));

        bar.add(ParticipantGroupManager.getInstance().createParticipantGroupButton(getViewContext(), getDataRegionName(), _cohortFilter, true));

        if (StudyManager.getInstance().showQCStates(getContainer()))
            bar.add(createQCStateButton(_qcStateSet));

        bar.add(createExportButton(false));
        bar.add(createPageSizeMenuButton());

        User user = getUser();
        boolean canWrite = canWrite(_dataset, user);
        boolean isSnapshot = QueryService.get().isQuerySnapshot(getContainer(), StudyManager.getSchemaName(), _dataset.getLabel());
        boolean isAssayDataset = _dataset.isAssayData();
        ExpProtocol protocol = null;

        if (isAssayDataset)
        {
            protocol = _dataset.getAssayProtocol();
            if (protocol == null)
                isAssayDataset = false;
        }

        if (!isSnapshot && canWrite && !isAssayDataset)
        {
            ActionButton insertButton = createInsertButton();
            if (insertButton != null)
            {
                bar.add(insertButton);
            }
        }

        if (!isSnapshot)
        {
            if (!isAssayDataset) // admins always get the import and manage buttons
            {
                if ((user.isAdministrator() || canWrite))
                {
                    // manage dataset
                    ActionButton manageButton = new ActionButton(new ActionURL(StudyController.DatasetDetailsAction.class, getContainer()).addParameter("id", _dataset.getDataSetId()), "Manage Dataset");
                    manageButton.setDisplayModes(DataRegion.MODE_GRID);
                    manageButton.setActionType(ActionButton.Action.LINK);
                    manageButton.setDisplayPermission(InsertPermission.class);
                    bar.add(manageButton);

                    // bulk import
                    if (view.getTable().getImportDataURL(getContainer()) != AbstractTableInfo.LINK_DISABLER_ACTION_URL)
                    {
                        ActionURL importURL = new ActionURL(StudyController.ImportAction.class, _dataset.getContainer());
                        importURL.addParameter(DataSetDefinition.DATASETKEY, _dataset.getDataSetId());
                        ActionButton uploadButton = new ActionButton(importURL, "Import Data", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                        uploadButton.setDisplayPermission(InsertPermission.class);
                        bar.add(uploadButton);
                    }
                }

                if (canWrite && _study instanceof StudyImpl)
                {
                    ActionURL deleteRowsURL = urlFor(QueryAction.deleteQueryRows);
                    if (deleteRowsURL != AbstractTableInfo.LINK_DISABLER_ACTION_URL)
                    {
                        if (deleteRowsURL == null)
                        {
                            deleteRowsURL = new ActionURL(StudyController.DeleteDatasetRowsAction.class, getContainer());
                        }
                        ActionButton deleteRows = new ActionButton(deleteRowsURL, "Delete");
                        deleteRows.setRequiresSelection(true, "Delete selected row from this dataset?", "Delete selected rows from this dataset?");
                        deleteRows.setActionType(ActionButton.Action.POST);
                        deleteRows.setDisplayPermission(DeletePermission.class);
                        bar.add(deleteRows);
                    }
                }
            }
            else
            {
                List<ActionButton> buttons = AssayService.get().getImportButtons(protocol, getUser(), getContainer(), true);
                bar.addAll(buttons);

                if (user.isAdministrator() || canWrite)
                {
                    ActionURL deleteRowsURL = new ActionURL(StudyController.DeletePublishedRowsAction.class, getContainer());
                    deleteRowsURL.addParameter("protocolId", protocol.getRowId());
                    ActionButton deleteRows = new ActionButton(deleteRowsURL, "Recall");
                    deleteRows.setRequiresSelection(true, "Recall selected row of this dataset?", "Recall selected rows of this dataset?");
                    deleteRows.setActionType(ActionButton.Action.POST);
                    deleteRows.setDisplayPermission(DeletePermission.class);
                    bar.add(deleteRows);
                }
            }
        }

        ActionURL viewSamplesURL = new ActionURL(SpecimenController.SelectedSamplesAction.class, getContainer());
        ActionButton viewSamples = new ActionButton(viewSamplesURL, "View Specimens");
        viewSamples.setRequiresSelection(true);
        viewSamples.setActionType(ActionButton.Action.POST);
        viewSamples.setDisplayPermission(ReadPermission.class);
        bar.add(viewSamples);

        if (isAssayDataset)
        {
            // provide a link to the source assay
            Container c = protocol.getContainer();
            if (c.hasPermission(getUser(), ReadPermission.class))
            {
                ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(
                        c,
                        protocol,
                        new ContainerFilter.CurrentAndSubfolders(getUser()));
                ActionButton viewAssayButton = new ActionButton("View Source Assay", url);
                bar.add(viewAssayButton);
            }
        }
    }

    @Override
    public MenuButton createViewButton(ReportService.ItemFilter filter)
    {
        MenuButton button =  super.createViewButton(filter);
        button.addMenuItem("Set Default View", getViewContext().cloneActionURL().setAction(StudyController.ViewPreferencesAction.class));

        return button;
    }

    private MenuButton createQCStateButton(QCStateSet currentSet)
    {
        List<QCStateSet> stateSets = QCStateSet.getSelectableSets(getContainer());
        MenuButton button = new MenuButton("QC State");

        for (QCStateSet set : stateSets)
        {
            NavTree setItem = new NavTree(set.getLabel(), getViewContext().cloneActionURL().replaceParameter(BaseStudyController.SharedFormParameters.QCState, set.getFormValue()).toString());
            setItem.setId("QCState:" + set.getLabel());
            if (set.equals(currentSet))
                setItem.setSelected(true);
            button.addMenuItem(setItem);
        }
        if (getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            button.addSeparator();
            ActionURL updateAction = new ActionURL(StudyController.UpdateQCStateAction.class, getContainer());
            NavTree updateItem = button.addMenuItem("Update state of selected rows", "#", "if (verifySelected(document.forms[\"" +
                    getDataRegionName() + "\"], \"" + updateAction.getLocalURIString() + "\", \"post\", \"rows\")) document.forms[\"" +
                    getDataRegionName() + "\"].submit()");
            updateItem.setId("QCState:updateSelected");

            button.addMenuItem("Manage states", new ActionURL(StudyController.ManageQCStatesAction.class,
                    getContainer()).addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString()));
        }
        return button;
    }

    private boolean canWrite(DataSetDefinition def, User user)
    {
        return def.canWrite(user) && def.getContainer().hasPermission(user, UpdatePermission.class);
    }

    private boolean hasSourceLsids() throws SQLException
    {
        TableInfo datasetTable = getTable();
        SimpleFilter sourceLsidFilter = new SimpleFilter();
        sourceLsidFilter.addCondition("SourceLsid", null, CompareType.NONBLANK);
        ResultSet rs = null;
        try
        {
            rs = Table.select(datasetTable, Collections.singleton("SourceLsid"), sourceLsidFilter, null);
            if (rs.next())
                return true;
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
        return false;
    }

    protected QCStateSet getQcStateSet()
    {
        return _qcStateSet;
    }

    protected void setQcStateSet(QCStateSet qcStateSet)
    {
        _qcStateSet = qcStateSet;
    }

    public static class DatasetFilterForm extends QueryViewAction.QueryExportForm
    {
        private String _cohortFilterType;
        private Integer _cohortId;
        private String _QCState;
        private int _datasetId;
        private int _visitRowId;

        public BindException bindParameters(PropertyValues params)
        {
            BindException errors = BaseViewAction.springBindParameters(this, "form", params);

            return errors;
        }

        public String getCohortFilterType()
        {
            return _cohortFilterType;
        }

        public void setCohortFilterType(String cohortFilterType)
        {
            _cohortFilterType = cohortFilterType;
        }

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }

        public String getQCState()
        {
            return _QCState;
        }

        public void setQCState(String QCState)
        {
            _QCState = QCState;
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public int getVisitRowId()
        {
            return _visitRowId;
        }

        public void setVisitRowId(int visitRowId)
        {
            _visitRowId = visitRowId;
        }
    }
}
