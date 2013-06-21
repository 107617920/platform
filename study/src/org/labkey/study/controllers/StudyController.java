/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.study.controllers;

import gwt.client.org.labkey.study.StudyApplication;
import gwt.client.org.labkey.study.dataset.client.Designer;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.*;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.model.ViewInfo;
import org.labkey.api.reports.report.*;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchUrls;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.*;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.thumbnail.BaseThumbnailAction;
import org.labkey.api.thumbnail.StaticThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.CohortFilter;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.SampleManager;
import org.labkey.study.StudyFolderType;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.dataset.DatasetSnapshotProvider;
import org.labkey.study.dataset.DatasetViewProvider;
import org.labkey.study.designer.StudySchedule;
import org.labkey.study.importer.*;
import org.labkey.study.importer.StudyReload.ReloadStatus;
import org.labkey.study.importer.StudyReload.ReloadTask;
import org.labkey.study.model.*;
import org.labkey.study.pipeline.DatasetFileReader;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.query.*;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.security.permissions.ManageStudyPermission;
import org.labkey.study.view.StudyGWTView;
import org.labkey.study.view.SubjectsWebPart;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.visitmanager.VisitManager.VisitStatistic;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;

import static org.labkey.api.util.PageFlowUtil.filter;

/**
 * User: Karl Lum
 * Date: Nov 28, 2007
 */
public class StudyController extends BaseStudyController
{
    private static final Logger _log = Logger.getLogger(StudyController.class);

    private static final String PARTICIPANT_CACHE_PREFIX = "Study_participants/participantCache";
    private static final String EXPAND_CONTAINERS_KEY = StudyController.class.getName() + "/expandedContainers";

    private static final String DATASET_DATAREGION_NAME = "Dataset";
    public static final String DATASET_REPORT_ID_PARAMETER_NAME = "Dataset.reportId";
    public static final String DATASET_VIEW_NAME_PARAMETER_NAME = "Dataset.viewName";
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(
            StudyController.class,
            CreateChildStudyAction.class);

    public static class StudyUrlsImpl implements StudyUrls
    {
        @Override
        public ActionURL getCreateStudyURL(Container container)
        {
            return new ActionURL(CreateStudyAction.class, container);
        }

        @Override
        public ActionURL getManageStudyURL(Container container)
        {
            return new ActionURL(ManageStudyAction.class, container);
        }

        @Override
        public ActionURL getManageViewsURL(Container container)
        {
            return new ActionURL(ReportsController.ManageReportsAction.class, container);
        }

        @Override
        public ActionURL getStudyOverviewURL(Container container)
        {
            return new ActionURL(OverviewAction.class, container);
        }

        @Override
        public ActionURL getDatasetURL(Container container, int datasetId)
        {
            ActionURL url = new ActionURL(StudyController.DatasetAction.class, container);
            url.addParameter(DataSetDefinition.DATASETKEY, datasetId);
            if (StudyManager.getInstance().showQCStates(container))
            {
                QCStateSet allStates = QCStateSet.getAllStates(container);
                if (allStates != null)
                    url.addParameter(BaseStudyController.SharedFormParameters.QCState, allStates.getFormValue());
            }
            return url;
        }

        @Override
        public ActionURL getDatasetsURL(Container container)
        {
            return new ActionURL(StudyController.DatasetsAction.class, container);
        }

        @Override
        public ActionURL getManageReports(Container container)
        {
            return new ActionURL(ReportsController.ManageReportsAction.class, container);
        }
    }

    public StudyController()
    {
        setActionResolver(ACTION_RESOLVER);
    }

    protected NavTree _appendNavTrailVisitAdmin(NavTree root)
    {
        _appendManageStudy(root);
        return root.addChild("Manage " + getVisitLabelPlural(), new ActionURL(ManageVisitsAction.class, getContainer()));
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        private Study _study;
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            _study = getStudy();

            WebPartView overview = StudyModule.manageStudyPartFactory.getWebPartView(getViewContext(), null);
            WebPartView right = StudyModule.reportsPartFactory.getWebPartView(getViewContext(), null);
			return new SimpleTemplate(overview,right);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_study == null ? "No Study In Folder" : _study.getLabel());
        }
    }


	class SimpleTemplate extends HttpView
	{
		SimpleTemplate(HttpView body, HttpView right)
		{
			setBody(body);
			setView(WebPartFactory.LOCATION_RIGHT, right);
		}

		@Override
		protected void renderInternal(Object model, PrintWriter out) throws Exception
		{
			out.print("<table width=100%><tr><td align=left valign=top class=labkey-body-panel><img height=1 width=400 src=\"" + getViewContext().getContextPath() + "\"/_.gif\"><br>");
			include(getBody());
			out.print("</td><td align=left valign=top class=labkey-side-panel><img height=1 width=240 src=\"" + getViewContext().getContextPath() + "/_.gif\"><br>");
			include(getView(WebPartFactory.LOCATION_RIGHT));
			out.print("</td></tr></table>");
		}
	}


    @RequiresPermissionClass(AdminPermission.class)
    public class DefineDatasetTypeAction extends FormViewAction<ImportTypeForm>
    {
        private DataSet _def;
        public ModelAndView getView(ImportTypeForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "importDataType.jsp", form, errors);
        }

        public void validateCommand(ImportTypeForm form, Errors errors)
        {
            if (null == form.getDataSetId() && !form.isAutoDatasetId())
                errors.reject("defineDatasetType", "You must supply an integer Dataset Id");
            if (null != form.getDataSetId())
            {
                DataSet dsd = StudyManager.getInstance().getDataSetDefinition(StudyManager.getInstance().getStudy(getContainer()), form.getDataSetId());
                if (null != dsd)
                    errors.reject("defineDatasetType", "There is already a dataset with id " + form.getDataSetId());
            }
            if (null == StringUtils.trimToNull(form.getTypeName()))
                errors.reject("defineDatasetType", "Dataset must have a name.");
            else
            {
                // Check if a dataset, query or table exists with the same name
                StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                StudyQuerySchema studySchema = new StudyQuerySchema(study, getUser(), true);
                if (null != studySchema.getDataSetDefinitionByName(form.getTypeName())
                        || studySchema.getTableNames().contains(form.getTypeName())
                        || QueryService.get().getQueryDef(getUser(), getContainer(), "study", form.getTypeName()) != null)
                {
                    errors.reject("defineDatasetType", "There is a dataset or query named \"" + form.getTypeName() + "\" already defined in this folder.");
                }
            }
        }

        public boolean handlePost(ImportTypeForm form, BindException derrors) throws Exception
        {
            Integer datasetId = form.getDataSetId();

            if (form.autoDatasetId)
                _def = AssayPublishManager.getInstance().createAssayDataset(getUser(), getStudyThrowIfNull(), form.getTypeName(), null, null, false, null);
            else
                _def = AssayPublishManager.getInstance().createAssayDataset(getUser(), getStudyThrowIfNull(), form.getTypeName(), null, datasetId, false, null);


            if (_def != null)
            {
                ((DataSetDefinition)_def).provisionTable();
                return true;
            }
            return false;
        }

        public ActionURL getSuccessURL(ImportTypeForm form)
        {
            if (_def == null)
            {
                throw new NotFoundException();
            }
            if (!form.isFileImport())
            {
                return new ActionURL(EditTypeAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, _def.getDataSetId());
            }
            else
            {
                return new ActionURL(DatasetController.DefineAndImportDatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, _def.getDataSetId());
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            return root.addChild("Define Dataset");
        }
    }

    @RequiresSiteAdmin
    public class PurgeOrphanedDatasetsAction extends SimpleViewAction
    {
        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DataSetDefinition.purgeOrphanedDatasets();
            throw new RedirectException(PageFlowUtil.urlProvider(ProjectUrls.class).getHomeURL());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    @SuppressWarnings("unchecked")
    public class EditTypeAction extends SimpleViewAction<DatasetForm>
    {
        private DataSet _def;
        public ModelAndView getView(DatasetForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();
            DataSetDefinition def = study.getDataSet(form.getDatasetId());
            _def = def;
            if (null == def)
            {
                throw new NotFoundException();
            }
            if (null == def.getTypeURI())
            {
                def = def.createMutable();
                String domainURI = StudyManager.getInstance().getDomainURI(study.getContainer(), getUser(), def);
                OntologyManager.ensureDomainDescriptor(domainURI, def.getName(), study.getContainer());
                def.setTypeURI(domainURI);
            }
            Map<String,String> props = PageFlowUtil.map(
                    "studyId", ""+study.getRowId(),
                    "datasetId", ""+form.getDatasetId(),
                    "typeURI", def.getTypeURI(),
                    "timepointType", ""+study.getTimepointType(),
                    ActionURL.Param.returnUrl.name(), new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", form.getDatasetId()).toString());

            String cancelUrl = getViewContext().getActionURL().getParameter(ActionURL.Param.cancelUrl.name());
            if (cancelUrl != null)
                props.put(ActionURL.Param.cancelUrl.name(), cancelUrl);

            HtmlView text = new HtmlView("Modify the properties and schema (form fields/properties) for this dataset.");
            HttpView view = new StudyGWTView(Designer.class, props);

            // hack for 4404 : Lookup picker performance is terrible when there are many containers
            ContainerManager.getAllChildren(ContainerManager.getRoot());

            return new VBox(text, view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            root.addChild(_def.getName(), new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", _def.getDataSetId()));
            return root.addChild("Edit Dataset Definition");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DatasetDetailsAction extends SimpleViewAction<IdForm>
    {
        private DataSetDefinition _def;

        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            _def = StudyManager.getInstance().getDataSetDefinition(getStudyRedirectIfNull(), form.getId());
            if (_def == null)
            {
                throw new NotFoundException("Invalid Dataset ID");
            }
            return  new StudyJspView<>(StudyManager.getInstance().getStudy(getContainer()),
                    "datasetDetails.jsp", _def, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrailDatasetAdmin(root).addChild(_def.getLabel() + " Dataset Properties");
        }
    }

    public static class DatasetFilterForm extends QueryViewAction.QueryExportForm implements HasViewContext
    {
        private String _qcState;
        private ViewContext _viewContext;

        public String getQCState()
        {
            return _qcState;
        }

        public void setQCState(String qcState)
        {
            _qcState = qcState;
        }

        public void setViewContext(ViewContext context)
        {
            _viewContext = context;
        }

        public ViewContext getViewContext()
        {
            return _viewContext;
        }
    }


    public static class OverviewForm extends DatasetFilterForm
    {
        private String[] _visitStatistic = new String[0];

        public String[] getVisitStatistic()
        {
            return _visitStatistic;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setVisitStatistic(String[] visitStatistic)
        {
            _visitStatistic = visitStatistic;
        }

        private Set<VisitStatistic> getVisitStatistics()
        {
            Set<VisitStatistic> set = EnumSet.noneOf(VisitStatistic.class);

            for (String statName : _visitStatistic)
                set.add(VisitStatistic.valueOf(statName));

            if (set.isEmpty())
                set.add(VisitStatistic.values()[0]);

            return set;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class OverviewAction extends SimpleViewAction<OverviewForm>
    {
        private StudyImpl _study;

        public ModelAndView getView(OverviewForm form, BindException errors) throws Exception
        {
            _study = getStudyRedirectIfNull();
            OverviewBean bean = new OverviewBean();
            bean.study = _study;
            bean.showAll = "1".equals(getViewContext().get("showAll"));
            bean.canManage = getContainer().hasPermission(getUser(), ManageStudyPermission.class);
            bean.showCohorts = StudyManager.getInstance().showCohorts(getContainer(), getUser());
            bean.stats = form.getVisitStatistics();

            if (StudyManager.getInstance().showQCStates(getContainer()))
                bean.qcStates = QCStateSet.getSelectedStates(getContainer(), form.getQCState());

            if (!bean.showCohorts)
                bean.cohortFilter = null;
            else
                bean.cohortFilter = CohortFilterFactory.getFromURL(getContainer(), getUser(), getViewContext().getActionURL(), DataSetQueryView.DATAREGION);

            VisitManager visitManager = StudyManager.getInstance().getVisitManager(bean.study);
            bean.visitMapSummary = visitManager.getVisitSummary(bean.cohortFilter, bean.qcStates, bean.stats, bean.showAll);

            return new StudyJspView<>(_study, "overview.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Overview:" + _study.getLabel());
        }
    }


    static class QueryReportForm extends QueryViewAction.QueryExportForm
    {
        ReportIdentifier _reportId;

        public ReportIdentifier getReportId()
        {
            return _reportId;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            _reportId = reportId;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class QueryReportAction extends QueryViewAction<QueryReportForm, QueryView>
    {
        protected Report _report;

        public QueryReportAction()
        {
            super(QueryReportForm.class);
        }

        @Override
        protected ModelAndView getHtmlView(QueryReportForm form, BindException errors) throws Exception
        {
            Report report = getReport(form);

            if (report != null)
                return report.getRunReportView(getViewContext());
            else
                throw new NotFoundException("Unable to locate the requested report: " + form.getReportId());
        }

        @Override
        protected QueryView createQueryView(QueryReportForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            Report report = getReport(form);
            if (report instanceof QueryReport)
                return ((QueryReport)report).getQueryViewGenerator().generateQueryView(getViewContext(), report.getDescriptor());

            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            if (_report != null)
                return root.addChild(_report.getDescriptor().getReportName());
            return root.addChild("Study Query Report");
        }

        protected Report getReport(QueryReportForm form) throws Exception
        {
            if (_report == null)
            {
                ReportIdentifier identifier = form.getReportId();
                if (identifier != null)
                    _report = identifier.getReport(getViewContext());
            }
            return _report;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DatasetReportAction extends QueryReportAction
    {
        public DatasetReportAction()
        {
            super();
        }

        protected Report getReport(QueryReportForm form) throws Exception
        {
            if (_report == null)
            {
                String reportId = (String)getViewContext().get(DATASET_REPORT_ID_PARAMETER_NAME);

                ReportIdentifier identifier = ReportService.get().getReportIdentifier(reportId);
                if (identifier != null)
                    _report = identifier.getReport(getViewContext());
            }
            return _report;
        }

        protected ModelAndView getHtmlView(QueryReportForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();
            Report report = getReport(form);

            // is not a report (either the default grid view or a custom view)...
            if (report == null)
            {
                return HttpView.redirect(createRedirectURLfrom(DatasetAction.class, context));
            }

            int datasetId = NumberUtils.toInt((String)context.get(DataSetDefinition.DATASETKEY), -1);
            DataSet def = StudyManager.getInstance().getDataSetDefinition(getStudyRedirectIfNull(), datasetId);

            if (def != null)
            {
                ActionURL url = getViewContext().cloneActionURL().setAction(StudyController.DatasetAction.class).
                                        replaceParameter(DATASET_REPORT_ID_PARAMETER_NAME, report.getDescriptor().getReportId().toString()).
                                        replaceParameter(DataSetDefinition.DATASETKEY, String.valueOf(def.getDataSetId()));

                return HttpView.redirect(url);
            }
            else if (ReportManager.get().canReadReport(getUser(), getContainer(), report))
                return report.getRunReportView(getViewContext());
            else
                return new HtmlView("User does not have read permission on this report.");
        }
    }

    private ActionURL createRedirectURLfrom(Class<? extends Controller> action, ViewContext context)
    {
        ActionURL newUrl = new ActionURL(action, context.getContainer());
        return newUrl.addParameters(context.getActionURL().getParameters());
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteDatasetReportAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String viewName = (String) getViewContext().get(DATASET_REPORT_ID_PARAMETER_NAME);
            int datasetId = NumberUtils.toInt((String)getViewContext().get(DataSetDefinition.DATASETKEY));

            if (NumberUtils.isDigits(viewName))
            {
                Report report = ReportService.get().getReport(NumberUtils.toInt(viewName));
                if (report != null)
                    ReportService.get().deleteReport(getViewContext(), report);
            }
            throw new RedirectException(new ActionURL(DatasetAction.class, getContainer()).
                        addParameter(DataSetDefinition.DATASETKEY, datasetId));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    // TODO I don't think this is quite correct, however this is the check currently used by import and delete
    // moved here to call it out instead of embedding it
    private static boolean canWrite(DataSetDefinition def, User user)
    {
        return def.canWrite(user) && def.getContainer().hasPermission(user, UpdatePermission.class);
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DatasetAction extends QueryViewAction<DatasetFilterForm, QueryView>
    {
        private CohortFilter _cohortFilter;
        private int _visitId;
        private String _encodedQcState;
        private DataSetDefinition _def;

        public DatasetAction()
        {
            super(DatasetFilterForm.class);
        }

        private DataSetDefinition getDataSetDefinition() throws ServletException
        {
            if (null == _def)
            {
                Object datasetKeyObject = getViewContext().get(DataSetDefinition.DATASETKEY);
                if (datasetKeyObject instanceof List)
                {
                    // bug 7365: It's been specified twice -- once in the POST, once in the GET. Just need one of them.
                    List<?> list = (List<?>)datasetKeyObject;
                    datasetKeyObject = list.get(0);
                }
                if (null != datasetKeyObject)
                {
                    try
                    {
                        int id = NumberUtils.toInt(String.valueOf(datasetKeyObject), 0);
                        _def = StudyManager.getInstance().getDataSetDefinition(getStudyRedirectIfNull(), id);
                    }
                    catch (ConversionException x)
                    {
                        throw new NotFoundException();
                    }
                }
                else
                {
                    String entityId = (String)getViewContext().get("entityId");
                    if (null != entityId)
                        _def = StudyManager.getInstance().getDataSetDefinitionByEntityId(getStudyRedirectIfNull(), entityId);
                }
            }
            if (null == _def)
                throw new NotFoundException();
            return _def;
        }

        @Override
        public ModelAndView getView(DatasetFilterForm form, BindException errors) throws Exception
        {
            ActionURL url = getViewContext().getActionURL();
            String viewName = url.getParameter(DATASET_VIEW_NAME_PARAMETER_NAME);


            // if the view name refers to a report id (legacy style), redirect to use the newer report id parameter
            if (NumberUtils.isDigits(viewName))
            {
                // one last check to see if there is a view with that name before trying to redirect to the report
                DataSetDefinition def = getDataSetDefinition();

                if (def != null &&
                    QueryService.get().getCustomView(getUser(), getContainer(), getUser(), StudySchema.getInstance().getSchemaName(), def.getName(), viewName) == null)
                {
                    ReportIdentifier reportId = AbstractReportIdentifier.fromString(viewName);
                    if (reportId != null && reportId.getReport(getViewContext()) != null)
                    {
                        ActionURL newURL = url.clone().deleteParameter(DATASET_VIEW_NAME_PARAMETER_NAME).
                                addParameter(DATASET_REPORT_ID_PARAMETER_NAME, reportId.toString());
                        return HttpView.redirect(newURL);
                    }
                }
            }
            return super.getView(form, errors);
        }

        protected ModelAndView getHtmlView(DatasetFilterForm form, BindException errors) throws Exception
        {
            // the full resultset is a join of all datasets for each participant
            // each dataset is determined by a visitid/datasetid

            Study study = getStudyRedirectIfNull();
            _encodedQcState = form.getQCState();
            QCStateSet qcStateSet = null;
            if (StudyManager.getInstance().showQCStates(getContainer()))
                qcStateSet = QCStateSet.getSelectedStates(getContainer(), form.getQCState());
            ViewContext context = getViewContext();

            String export = StringUtils.trimToNull(context.getActionURL().getParameter("export"));

            String viewName = (String)context.get(DATASET_VIEW_NAME_PARAMETER_NAME);
            DataSetDefinition def = getDataSetDefinition();
            if (null == def)
                return new TypeNotFoundAction().getView(form, errors);
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                return new TypeNotFoundAction().getView(form, errors);

            _visitId = NumberUtils.toInt((String)context.get(VisitImpl.VISITKEY), 0);
            VisitImpl visit = null;
            if (_visitId != 0)
            {
                assert study.getTimepointType() != TimepointType.CONTINUOUS;
                visit = StudyManager.getInstance().getVisitForRowId(study, _visitId);
                if (null == visit)
                    throw new NotFoundException();
            }

            boolean showEditLinks = !QueryService.get().isQuerySnapshot(getContainer(), StudySchema.getInstance().getSchemaName(), def.getName()) &&
                !def.isAssayData();

            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), StudyQuerySchema.SCHEMA_NAME);
            DataSetQuerySettings settings = (DataSetQuerySettings)schema.getSettings(getViewContext(), DataSetQueryView.DATAREGION, def.getName());

            settings.setShowEditLinks(showEditLinks);
            settings.setShowSourceLinks(true);

            QueryView queryView = schema.createView(getViewContext(), settings, errors);
            if (queryView instanceof StudyQueryView)
                _cohortFilter = ((StudyQueryView)queryView).getCohortFilter();

            final ActionURL url = context.getActionURL();

            // clear the property map cache and the sort map cache
            getParticipantPropsMap(context).clear();
            getDatasetSortColumnMap(context).clear();

            final TableInfo table = queryView.getTable();
            if (table != null)
            {
                setColumnURL(url, queryView, schema, def);

                // Clear any cached participant lists, since the filter/sort may have changed
                removeParticipantListFromCache(context, def.getDataSetId(), viewName, _cohortFilter, form.getQCState());
                getExpandedState(context, def.getDataSetId()).clear();
            }

            if (null != export)
            {
                if ("tsv".equals(export))
                    queryView.exportToTsv(context.getResponse());
                else if ("xls".equals(export))
                    queryView.exportToExcel(context.getResponse());
                return null;
            }

            StringBuilder sb = new StringBuilder();
            if (def.getDescription() != null && def.getDescription().length() > 0)
                sb.append(PageFlowUtil.filter(def.getDescription(), true, true)).append("<br/>");
            if (_cohortFilter != null)
                sb.append("<br/><span><b>Cohort :</b> ").append(filter(_cohortFilter.getDescription(getContainer(), getUser()))).append("</span>");
            if (qcStateSet != null)
                sb.append("<br/><span><b>QC States:</b> ").append(filter(qcStateSet.getLabel())).append("</span>");
            if (ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "refreshDate") != null)
            {
                sb.append("<br/><span><b>Data Cut Date:</b> ");
                Object refreshDate = (ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "refreshDate"));
                if(refreshDate instanceof Date)
                {
                    if(StudyManager.getInstance().getDefaultDateFormatString(getContainer()) != null)
                    {
                        sb.append(DateUtil.formatDateTime((Date)refreshDate, StudyManager.getInstance().getDefaultDateFormatString(getContainer())));
                    }
                    else
                    {
                        sb.append(DateUtil.formatDateTime((Date)refreshDate, DateUtil.getStandardDateFormatString()));
                    }
                }
                else
                {
                    sb.append(ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "refreshDate").toString());
                }
            }
            HtmlView header = new HtmlView(sb.toString());

            LinkedHashSet<ClientDependency> dependencies = new LinkedHashSet<>();
            dependencies.add(ClientDependency.fromFilePath("study/ParticipantGroup.js"));

            header.addClientDependencies(dependencies);
            VBox view = new VBox(header, queryView);

            String status = (String)ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "status");
            if (status != null)
            {
                HtmlView scriptLock = new HtmlView("<script type=\"text/javascript\">Ext.onReady(function(){" +
                        "var dom = Ext.DomQuery.selectNode('td[class=labkey-proj]');" +
                        "if (dom) {" +
                            "var el = Ext.Element.fly(dom); " +
                            "if (el) " +
                                "el.addClass('labkey-dataset-status-" + PageFlowUtil.filter(status.toLowerCase()) + "');" +
                        "}" +
                    "});</script>");
                view.addView(scriptLock);
            }

            Report report = queryView.getSettings().getReportView(context);
            if (report != null && !ReportManager.get().canReadReport(getUser(), getContainer(), report))
            {
                return new HtmlView("User does not have read permission on this report.");
            }
            else if (report == null && !def.canRead(getUser()))
            {
                return new HtmlView("User does not have read permission on this dataset.");
            }
            else
            {
                // add discussions
                DiscussionService.Service service = DiscussionService.get();

                if (report != null)
                {
                    // discuss the report
                    String title = "Discuss report - " + report.getDescriptor().getReportName();
                    HttpView discussion = service.getDisussionArea(getViewContext(), report.getEntityId(), getViewContext().getActionURL(), title, true, false);
                    view.addView(discussion);
                }
                else
                {
                    // discuss the dataset
                    String title = "Discuss dataset - " + def.getLabel();
                    HttpView discussion = service.getDisussionArea(getViewContext(), def.getEntityId(), getViewContext().getActionURL(), title, true, false);
                    view.addView(discussion);
                }
            }
            return view;
        }

        protected QueryView createQueryView(DatasetFilterForm datasetFilterForm, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            QuerySettings qs = new QuerySettings(getViewContext(), DATASET_DATAREGION_NAME);
            Report report = qs.getReportView(getViewContext());
            if (report instanceof QueryReport)
            {
                return ((QueryReport)report).getQueryViewGenerator().generateQueryView(getViewContext(), report.getDescriptor());
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                return _appendNavTrail(root, getDataSetDefinition().getDataSetId(), _visitId, _cohortFilter, _encodedQcState);
            }
            catch (ServletException x)
            {
                return root;
            }
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ThumbnailAction extends BaseThumbnailAction
    {
        @Override
        public StaticThumbnailProvider getProvider(Object o) throws Exception
        {
            return new StaticThumbnailProvider()
            {
                @Override
                public Thumbnail getStaticThumbnail()
                {
                    InputStream is = StudyController.class.getResourceAsStream("dataset.png");
                    return new Thumbnail(is, "image/png");
                }

                @Override
                public String getStaticThumbnailCacheKey()
                {
                    return "Dataset";
                }
            };
        }
    }


    @RequiresNoPermission
    public class ExpandStateNotifyAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            final ActionURL url = getViewContext().getActionURL();
            final String collapse = url.getParameter("collapse");
            final int datasetId = NumberUtils.toInt(url.getParameter(DataSetDefinition.DATASETKEY), -1);
            final int id = NumberUtils.toInt(url.getParameter("id"), -1);

            if (datasetId != -1 && id != -1)
            {
                Map<Integer, String> expandedMap = getExpandedState(getViewContext(), id);
                // collapse param is only set on a collapse action
                if (collapse != null)
                    expandedMap.put(datasetId, "collapse");
                else
                    expandedMap.put(datasetId, "expand");
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class IndexParticipantAction extends ParticipantAction
    {
        ParticipantForm _form;
        Study _study;

        @Override
        public ModelAndView getView(ParticipantForm form, BindException errors) throws Exception
        {
            _form = form;
            _study = getStudyRedirectIfNull();
            if (null == _form.getParticipantId())
                throw new NotFoundException();
            getPageConfig().setTemplate(PageConfig.Template.Print);
            getPageConfig().setNoIndex();
            VBox box = new VBox();
            box.addView(new HtmlView(PageFlowUtil.filter(_study.getLabel() + ": " + _form.getParticipantId())));
            ModelAndView characteristicsView = StudyManager.getInstance().getParticipantDemographicsView(getContainer(), form, errors);
            box.addView(characteristicsView);
            return box;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild(_study.getLabel() + ": " + _form.getParticipantId());
            return root;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ParticipantAction extends SimpleViewAction<ParticipantForm>
    {
        private ParticipantForm _bean;
        private CohortFilter _cohortFilter;

        public ModelAndView getView(ParticipantForm form, BindException errors) throws Exception
        {
            Study study = getStudyRedirectIfNull();
            _bean = form;
            ActionURL previousParticipantURL = null;
            ActionURL nextParticipantURL = null;

            if (form.getParticipantId() == null)
            {
                throw new NotFoundException("No " + study.getSubjectNounSingular() + " specified");
            }

            Participant participant = StudyManager.getInstance().getParticipant(study, form.getParticipantId());
            if (participant == null)
            {
                throw new NotFoundException("Could not find " + study.getSubjectNounSingular() + " " + form.getParticipantId());
            }

            String viewName = (String) getViewContext().get(DATASET_VIEW_NAME_PARAMETER_NAME);

            _cohortFilter = CohortFilterFactory.getFromURL(getContainer(), getUser(), getViewContext().getActionURL(), DataSetQueryView.DATAREGION);
            // display the next and previous buttons only if we have a cached participant index
            if (_cohortFilter != null && !StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                throw new UnauthorizedException("User does not have permission to view cohort information");

            List<String> participants = getParticipantListFromCache(getViewContext(), form.getDatasetId(), viewName, _cohortFilter, form.getQCState());

            if (participants != null)
            {
                if (isDebug())
                {
                    _log.info("Cached participants: " + participants);
                }
                int idx = participants.indexOf(form.getParticipantId());
                if (idx != -1)
                {
                    if (idx > 0)
                    {
                        final String ptid = participants.get(idx-1);
                        previousParticipantURL = getViewContext().cloneActionURL();
                        previousParticipantURL.replaceParameter("participantId", ptid);
                    }

                    if (idx < participants.size()-1)
                    {
                        final String ptid = participants.get(idx+1);
                        nextParticipantURL = getViewContext().cloneActionURL();
                        nextParticipantURL.replaceParameter("participantId", ptid);
                    }
                }
            }

            VBox vbox = new VBox();
            ParticipantNavView navView = new ParticipantNavView(previousParticipantURL, nextParticipantURL, form.getParticipantId(), form.getQCState());
            vbox.addView(navView);

            CustomParticipantView customParticipantView = StudyManager.getInstance().getCustomParticipantView(study);
            if (customParticipantView != null && customParticipantView.isActive())
            {
                HtmlView participantView = new HtmlView(customParticipantView.getBody());
                vbox.addView(participantView);
            }
            else
            {
                ModelAndView characteristicsView = StudyManager.getInstance().getParticipantDemographicsView(getContainer(), form, errors);
                ModelAndView dataView = StudyManager.getInstance().getParticipantView(getContainer(), form, errors);
                vbox.addView(characteristicsView);
                vbox.addView(dataView);
            }

            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, _bean.getDatasetId(), 0, _cohortFilter, _bean.getQCState()).
                    addChild(StudyService.get().getSubjectNounSingular(getContainer()) + " - " + id(_bean.getParticipantId()));
        }
    }


    // Obfuscate the passed in test if this user is in "demo" mode in this container
    private String id(String id)
    {
        return id(id, getContainer(), getUser());
    }

    // Obfuscate the passed in test if this user is in "demo" mode in this container
    private static String id(String id, Container c, User user)
    {
        return DemoMode.id(id, c, user);
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class UploadVisitMapAction extends FormViewAction<TSVForm>
    {
        public ModelAndView getView(TSVForm tsvForm, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "uploadVisitMap.jsp", null, errors);
        }

        public void validateCommand(TSVForm target, Errors errors)
        {
        }

        public boolean handlePost(TSVForm form, BindException errors) throws Exception
        {
            VisitMapImporter importer = new VisitMapImporter();
            List<String> errorMsg = new LinkedList<>();
            if (!importer.process(getUser(), getStudyThrowIfNull(), form.getContent(), VisitMapImporter.Format.DataFax, errorMsg, _log))
            {
                for (String error : errorMsg)
                    errors.reject("uploadVisitMap", error);
                return false;
            }
            return true;
        }

        public ActionURL getSuccessURL(TSVForm tsvForm)
        {
            return new ActionURL(BeginAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            return root.addChild("Create New Study: Visit Map Upload");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CreateStudyAction extends FormViewAction<StudyPropertiesForm>
    {
        public ModelAndView getView(StudyPropertiesForm form, boolean reshow, BindException errors) throws Exception
        {
            if (null != getStudy())
            {
                BeginAction action = (BeginAction)initAction(this, new BeginAction());
                return action.getView(form, errors);
            }
            // Set default values for the form
            if (form.getLabel() == null)
            {
                form.setLabel(HttpView.currentContext().getContainer().getName() + " Study");
            }
            if (form.getStartDate() == null)
            {
                form.setStartDate(new Date());
            }
            if (form.getDefaultTimepointDuration() == 0)
            {
                form.setDefaultTimepointDuration(1);
            }
            return new StudyJspView<>(null, "createStudy.jsp", form, errors);
        }

        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.getTimepointType() == TimepointType.DATE && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");

            target.setLabel(StringUtils.trimToNull(target.getLabel()));
            if (null == target.getLabel())
                errors.reject(ERROR_MSG, "Please supply a label");

            if (!StudyService.get().isValidSubjectColumnName(getContainer(), target.getSubjectColumnName()))
                errors.reject(ERROR_MSG, "\"" + target.getSubjectColumnName() + "\" is not a valid subject column name.");

            if (!StudyService.get().isValidSubjectNounSingular(getContainer(), target.getSubjectNounSingular()))
                errors.reject(ERROR_MSG, "\"" + target.getSubjectNounSingular() + "\" is not a valid singular subject noun.");

            // For now, apply the same check to the plural noun as to the singular- there rules should be exactly the same.
            if (!StudyService.get().isValidSubjectNounSingular(getContainer(), target.getSubjectNounPlural()))
                errors.reject(ERROR_MSG, "\"" + target.getSubjectNounPlural() + "\" is not a valid plural subject noun.");
        }

        public boolean handlePost(StudyPropertiesForm form, BindException errors) throws Exception
        {
            createStudy(getStudy(), getContainer(), getUser(), form);
            updateRepositorySettings(getContainer(), form.isSimpleRepository());
            return true;
        }

        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            return new ActionURL(ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Study");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class OldCreateStudyAction extends FormHandlerAction<StudyPropertiesForm>
    {
        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.getTimepointType() == TimepointType.DATE && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");

            target.setLabel(StringUtils.trimToNull(target.getLabel()));
            if (null == target.getLabel())
                errors.reject(ERROR_MSG, "Please supply a label");

            if (!StudyService.get().isValidSubjectColumnName(getContainer(), target.getSubjectColumnName()))
                errors.reject(ERROR_MSG, target.getSubjectColumnName() + " is not a valid subject column name.");

            if (!StudyService.get().isValidSubjectNounSingular(getContainer(), target.getSubjectNounSingular()))
                errors.reject(ERROR_MSG, target.getSubjectNounSingular() + " is not a valid subject noun.");
        }

        public boolean handlePost(StudyPropertiesForm form, BindException errors) throws Exception
        {
            createStudy(getStudy(), getContainer(), getUser(), form);
            updateRepositorySettings(getContainer(), form.isSimpleRepository());
            return true;
        }

        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            return new ActionURL(ManageStudyAction.class, getContainer());
        }
    }

    public static StudyImpl createStudy(StudyImpl study, Container c, User user, StudyPropertiesForm form) throws SQLException, ServletException
    {
        if (null == study)
        {
            study = new StudyImpl(c, form.getLabel());
            study.setTimepointType(form.getTimepointType());
            study.setStartDate(form.getStartDate());
            study.setSecurityType(form.getSecurityType());
            study.setSubjectNounSingular(form.getSubjectNounSingular());
            study.setSubjectNounPlural(form.getSubjectNounPlural());
            study.setSubjectColumnName(form.getSubjectColumnName());
            study.setDescription(form.getDescription());
            study.setDefaultTimepointDuration(form.getDefaultTimepointDuration() < 1 ? 1 : form.getDefaultTimepointDuration());
            if(form.getDescriptionRendererType() != null)
                study.setDescriptionRendererType(form.getDescriptionRendererType());
            study.setGrant(form.getGrant());
            study.setInvestigator(form.getInvestigator());
            study.setAlternateIdPrefix(form.getAlternateIdPrefix());
            study.setAlternateIdDigits(form.getAlternateIdDigits());
            study.setAllowReqLocRepository(form.isAllowReqLocRepository());
            study.setAllowReqLocClinic(form.isAllowReqLocClinic());
            study.setAllowReqLocSal(form.isAllowReqLocSal());
            study.setAllowReqLocEndpoint(form.isAllowReqLocEndpoint());
            study = StudyManager.getInstance().createStudy(user, study);
            RequestabilityManager.getInstance().setDefaultRules(c, user);
        }
        return study;
    }

    private static void updateRepositorySettings(Container c, boolean simple)
    {
        RepositorySettings reposSettings = SampleManager.getInstance().getRepositorySettings(c);
        reposSettings.setSimple(simple);
        reposSettings.setEnableRequests(!simple);
        reposSettings.setSpecimenDataEditable(false);
        SampleManager.getInstance().saveRepositorySettings(c, reposSettings);
    }

    @RequiresPermissionClass(ManageStudyPermission.class)
    public class ManageStudyAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            StudyPropertiesQueryView propView = new StudyPropertiesQueryView(getUser(), getStudyRedirectIfNull(), HttpView.currentContext(), true);

            return new StudyJspView<>(getStudy(), "manageStudy.jsp", propView, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendManageStudy(root);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteStudyAction extends FormViewAction<DeleteStudyForm>
    {
        public void validateCommand(DeleteStudyForm form, Errors errors)
        {
            if (!form.isConfirm())
                errors.reject("deleteStudy", "Need to confirm Study deletion");
        }

        public ModelAndView getView(DeleteStudyForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "confirmDeleteStudy.jsp", null, errors);
        }

        public boolean handlePost(DeleteStudyForm form, BindException errors) throws Exception
        {
            StudyManager.getInstance().deleteAllStudyData(getContainer(), getUser());
            return true;
        }

        public ActionURL getSuccessURL(DeleteStudyForm deleteStudyForm)
        {
            return getContainer().getFolderType().getStartURL(getContainer(), getUser());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Confirm Delete Study");
        }
    }

    public static class DeleteStudyForm
    {
        private boolean confirm;

        public boolean isConfirm()
        {
            return confirm;
        }

        public void setConfirm(boolean confirm)
        {
            this.confirm = confirm;
        }
    }

    public static class RemoveProtocolDocumentForm
    {
        private String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class RemoveProtocolDocumentAction extends FormHandlerAction<RemoveProtocolDocumentForm>
    {
        @Override
        public void validateCommand(RemoveProtocolDocumentForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(RemoveProtocolDocumentForm removeProtocolDocumentForm, BindException errors) throws Exception
        {
            Study study = getStudyThrowIfNull();
            study.removeProtocolDocument(removeProtocolDocumentForm.getName(), getUser());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(RemoveProtocolDocumentForm removeProtocolDocumentForm)
        {
            return new ActionURL(ManageStudyPropertiesAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageStudyPropertiesAction extends FormApiAction<TableViewForm>
    {
        @Override
        protected TableViewForm getCommand(HttpServletRequest request) throws Exception
        {
            User user = getUser();
            QuerySchema schema = new StudySchemaProvider().getSchema(DefaultSchema.get(user, getViewContext().getContainer()));
            TableViewForm form = new TableViewForm(schema.getTable("StudyProperties"));
            form.setViewContext(getViewContext());
            return form;
        }

        @Override
        public ModelAndView getView(TableViewForm form, BindException errors) throws Exception
        {
            Study study = getStudy();
            if (null == study)
                throw new RedirectException(new ActionURL(CreateStudyAction.class, getContainer()));
            return new StudyJspView<>(getStudy(), "manageStudyPropertiesExt.jsp", study, null);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Study Properties");
        }

        @Override
        public ApiResponse execute(TableViewForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(),AdminPermission.class))
                throw new UnauthorizedException();

            Map<String,Object> values = form.getTypedValues();
            values.put("container", getContainer().getId());

            TableInfo studyProperties = form.getTable();
            QueryUpdateService qus = studyProperties.getUpdateService();
            if (null == qus)
                throw new UnauthorizedException();
            try
            {
                qus.updateRows(getUser(), getContainer(), Collections.singletonList(values), Collections.singletonList(values), null);
                List<AttachmentFile> files = getAttachmentFileList();
                getStudyThrowIfNull().attachProtocolDocument(files, getUser());
            }
            catch (BatchValidationException x)
            {
                x.addToErrors(errors);
                return null;
            }
            catch (AttachmentService.DuplicateFilenameException x)
            {
                JSONObject json = new JSONObject();
                json.put("failure", true);
                json.put("msg", x.getMessage());
                return new ApiSimpleResponse(json);
            }

            JSONObject json = new JSONObject();
            json.put("success", true);
            return new ApiSimpleResponse(json);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ManageVisitsAction extends FormViewAction<StudyPropertiesForm>
    {
        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.getTimepointType() == TimepointType.DATE && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");
            if (target.getDefaultTimepointDuration() < 1)
                errors.reject(ERROR_MSG, "Default timepoint duration must be a positive number.");
        }

        public ModelAndView getView(StudyPropertiesForm form, boolean reshow, BindException errors) throws Exception
        {
            StudyImpl study = getStudy();
            if (null == study)
            {
                CreateStudyAction action = (CreateStudyAction)initAction(this, new CreateStudyAction());
                return action.getView(form, false, errors);
            }
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                return new HtmlView("<span class='labkey-error'>Unsupported operation for continuous study</span>");

            return new StudyJspView<>(study, _jspName(study), form, errors);
        }

        public boolean handlePost(StudyPropertiesForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudyThrowIfNull().createMutable();
            study.setStartDate(form.getStartDate());
            study.setDefaultTimepointDuration(form.getDefaultTimepointDuration());
            StudyManager.getInstance().updateStudy(getUser(), study);

            return true;
        }

        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            return new ActionURL(ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            return root;
        }

        private String _jspName(Study study) throws ServletException
        {
            assert study.getTimepointType() != TimepointType.CONTINUOUS;
            return study.getTimepointType() == TimepointType.DATE ? "manageTimepoints.jsp" : "manageVisits.jsp";
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageTypesAction extends FormViewAction<ManageTypesForm>
    {
        public void validateCommand(ManageTypesForm target, Errors errors)
        {
        }

        public ModelAndView getView(ManageTypesForm manageTypesForm, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "manageTypes.jsp", this, errors);
        }

        public boolean handlePost(ManageTypesForm form, BindException errors) throws Exception
        {
            String dateFormat = form.getDateFormat();
            String numberFormat = form.getNumberFormat();

            try
            {
                if (!StringUtils.isEmpty(dateFormat))
                {
                    FastDateFormat.getInstance(dateFormat);
                    StudyManager.getInstance().setDefaultDateFormatString(getContainer(), dateFormat);
                }
                else
                    StudyManager.getInstance().setDefaultDateFormatString(getContainer(), null);

                if (!StringUtils.isEmpty(numberFormat))
                {
                    new DecimalFormat(numberFormat);
                    StudyManager.getInstance().setDefaultNumberFormatString(getContainer(), numberFormat);
                }
                else
                    StudyManager.getInstance().setDefaultNumberFormatString(getContainer(), null);

                return true;
            }
            catch (IllegalArgumentException e)
            {
                errors.reject("manageTypes", e.getMessage());
                return false;
            }
        }

        public ActionURL getSuccessURL(ManageTypesForm manageTypesForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage Datasets");
        }
    }

    public static class ManageTypesForm
    {
        private String _dateFormat;
        private String _numberFormat;

        public String getDateFormat()
        {
            return _dateFormat;
        }

        public void setDateFormat(String dateFormat)
        {
            _dateFormat = dateFormat;
        }

        public String getNumberFormat()
        {
            return _numberFormat;
        }

        public void setNumberFormat(String numberFormat)
        {
            _numberFormat = numberFormat;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageLocationsAction extends FormViewAction<BulkEditForm>
    {
        public void validateCommand(BulkEditForm target, Errors errors)
        {
        }

        public ModelAndView getView(BulkEditForm bulkEditForm, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<>(getStudyRedirectIfNull(), "manageLocations.jsp", getStudyRedirectIfNull(), errors);
            return view;
        }

        public boolean handlePost(BulkEditForm form, BindException errors) throws Exception
        {
            int[] ids = form.getIds();
            if (ids != null && ids.length > 0)
            {
                String[] labels = form.getLabels();
                Map<Integer, String> labelLookup = new HashMap<>();
                for (int i = 0; i < ids.length; i++)
                    labelLookup.put(ids[i], labels[i]);

                boolean emptyLabel = false;
                for (LocationImpl location : getStudyThrowIfNull().getLocations())
                {
                    String label = labelLookup.get(location.getRowId());
                    if (label == null)
                        emptyLabel = true;
                    else if (!label.equals(location.getLabel()))
                    {
                        location = location.createMutable();
                        location.setLabel(label);
                        StudyManager.getInstance().updateSite(getUser(), location);
                    }
                }
                if (emptyLabel)
                {
                    errors.reject("manageLocations", "Some location labels could not be updated: empty labels are not allowed.");
                }

            }
            if (form.getNewId() != null || form.getNewLabel() != null)
            {
                if (form.getNewId() == null)
                    errors.reject("manageLocations", "Unable to create location: an ID is required for all locations.");
                else if (form.getNewLabel() == null)
                    errors.reject("manageLocations", "Unable to create location: a label is required for all locations.");
                else
                {
                    try
                    {
                        LocationImpl location = new LocationImpl();
                        location.setLabel(form.getNewLabel());
                        location.setLdmsLabCode(Integer.parseInt(form.getNewId()));
                        location.setContainer(getContainer());
                        StudyManager.getInstance().createSite(getUser(), location);
                    }
                    catch (NumberFormatException e)
                    {
                        errors.reject("manageLocations", "Unable to create location: ID must be an integer.");
                    }
                }
            }
            return errors.getErrorCount() == 0;
        }

        public ActionURL getSuccessURL(BulkEditForm bulkEditForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage Locations");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class VisitSummaryAction extends FormViewAction<VisitForm>
    {
        private VisitImpl _v;

        public void validateCommand(VisitForm target, Errors errors)
        {

            try
            {
                StudyImpl study = getStudyRedirectIfNull();
                if (study.getTimepointType() == TimepointType.CONTINUOUS)
                    errors.reject(null, "Unsupported operation for continuous date study");

                target.validate(errors, study);
                if (errors.getErrorCount() > 0)
                    return;

                //check for overlapping visits

                VisitManager visitMgr = StudyManager.getInstance().getVisitManager(study);
                if (null != visitMgr)
                {
                    if (visitMgr.isVisitOverlapping(target.getBean()))
                        errors.reject(null, "Visit range overlaps an existing visit in this study. Please enter a different range.");
                }
            }
            catch(ServletException e)
            {
                errors.reject(null, e.getMessage());
            }
            catch(SQLException e)
            {
                errors.reject(null, e.getMessage());
            }
        }

        public ModelAndView getView(VisitForm form, boolean reshow, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                return new HtmlView("<span class='labkey-error'>Unsupported operation for continuous date study</span>");

            int id = NumberUtils.toInt((String)getViewContext().get("id"));
            _v = StudyManager.getInstance().getVisitForRowId(study, id);
            if (_v == null)
            {
                return HttpView.redirect(new ActionURL(BeginAction.class, getContainer()));
            }
            VisitSummaryBean visitSummary = new VisitSummaryBean();
            visitSummary.setVisit(_v);

            return new StudyJspView<>(study, getVisitJsp("edit", study), visitSummary, errors);
        }

        public boolean handlePost(VisitForm form, BindException errors) throws Exception
        {
            VisitImpl postedVisit = form.getBean();
            if (!getContainer().getId().equals(postedVisit.getContainer().getId()))
                throw new UnauthorizedException();

            // UNDONE: how do I get struts to handle this checkbox?
            postedVisit.setShowByDefault(null != StringUtils.trimToNull((String)getViewContext().get("showByDefault")));

            // UNDONE: reshow is broken for this form, but we have to validate
            TreeMap<Double, VisitImpl> visits = StudyManager.getInstance().getVisitManager(getStudyThrowIfNull()).getVisitSequenceMap();
            boolean validRange = true;
            // make sure there is no overlapping visit
            for (VisitImpl v : visits.values())
            {
                if (v.getRowId() == postedVisit.getRowId())
                    continue;
                double maxL = Math.max(v.getSequenceNumMin(), postedVisit.getSequenceNumMin());
                double minR = Math.min(v.getSequenceNumMax(), postedVisit.getSequenceNumMax());
                if (maxL<=minR)
                {
                    errors.reject("visitSummary", getVisitLabel() + " range overlaps with '" + v.getDisplayString() + "'");
                    validRange = false;
                }
            }

            if (!validRange)
            {
                return false;
            }

            StudyManager.getInstance().updateVisit(getUser(), postedVisit);

            HashMap<Integer,VisitDataSetType> visitTypeMap = new HashMap<>();
            for (VisitDataSet vds :  postedVisit.getVisitDataSets())
                visitTypeMap.put(vds.getDataSetId(), vds.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL);

            if (form.getDataSetIds() != null)
            {
                for (int i = 0; i < form.getDataSetIds().length; i++)
                {
                    int dataSetId = form.getDataSetIds()[i];
                    VisitDataSetType type = VisitDataSetType.valueOf(form.getDataSetStatus()[i]);
                    VisitDataSetType oldType = visitTypeMap.get(dataSetId);
                    if (oldType == null)
                        oldType = VisitDataSetType.NOT_ASSOCIATED;
                    if (type != oldType)
                    {
                        StudyManager.getInstance().updateVisitDataSetMapping(getUser(), getContainer(),
                                postedVisit.getRowId(), dataSetId, type);
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(VisitForm form)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            root.addChild(_v.getDisplayString());
            return root;
        }
    }

    public static class VisitSummaryBean
    {
        private VisitImpl visit;

        public VisitImpl getVisit()
        {
            return visit;
        }

        public void setVisit(VisitImpl visit)
        {
            this.visit = visit;
        }
    }

    @RequiresPermissionClass(ManageStudyPermission.class)
    public class StudyScheduleAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return StudyModule.studyScheduleWebPartFactory.getWebPartView(getViewContext(), null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Study Schedule");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteVisitAction extends FormHandlerAction<IdForm>
    {
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        public boolean handlePost(IdForm form, BindException errors) throws Exception
        {
            int visitId = form.getId();
            StudyImpl study = getStudyThrowIfNull();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            VisitImpl visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
            if (visit != null)
            {
                StudyManager.getInstance().deleteVisit(study, visit, getUser());
                return true;
            }
            throw new NotFoundException();
        }

        public ActionURL getSuccessURL(IdForm idForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ConfirmDeleteVisitAction extends SimpleViewAction<IdForm>
    {
        private VisitImpl _visit;
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            int visitId = form.getId();
            StudyImpl study = getStudyRedirectIfNull();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            _visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
            if (null == _visit)
                throw new NotFoundException();

            ModelAndView view = new StudyJspView<>(study, "confirmDeleteVisit.jsp", _visit, errors);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Delete visit -- " + _visit.getDisplayString());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CreateVisitAction extends FormViewAction<VisitForm>
    {
        public void validateCommand(VisitForm target, Errors errors)
        {

            try
            {
                StudyImpl study = getStudyRedirectIfNull();
                if (study.getTimepointType() == TimepointType.CONTINUOUS)
                    errors.reject(null, "Unsupported operation for continuous date study");

                target.validate(errors, study);
                if (errors.getErrorCount() > 0)
                    return;

                //check for overlapping visits
                VisitManager visitMgr = StudyManager.getInstance().getVisitManager(study);
                if (null != visitMgr)
                {
                    if (visitMgr.isVisitOverlapping(target.getBean()))
                        errors.reject(null, "Visit range overlaps an existing visit in this study. Please enter a different range.");
                }
            }
            catch(ServletException e)
            {
                errors.reject(null, e.getMessage());
            }
            catch(SQLException e)
            {
                errors.reject(null, e.getMessage());
            }
        }

        public ModelAndView getView(VisitForm form, boolean reshow, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();

            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            return new StudyJspView<>(study, getVisitJsp("create", study), form, errors);
        }

        public boolean handlePost(VisitForm form, BindException errors) throws Exception
        {
            VisitImpl visit = form.getBean();
            if (visit != null)
                StudyManager.getInstance().createVisit(getStudyThrowIfNull(), getUser(), visit);
            return true;
        }

        public ActionURL getSuccessURL(VisitForm visitForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            return root.addChild("Create New " + getVisitLabel());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateDatasetVisitMappingAction extends FormViewAction<DatasetForm>
    {
        DataSetDefinition _def;

        public void validateCommand(DatasetForm form, Errors errors)
        {
            if (form.getDatasetId() < 1)
                errors.reject(SpringActionController.ERROR_MSG, "DatasetId must be greater than zero.");
        }

        public ModelAndView getView(DatasetForm form, boolean reshow, BindException errors) throws Exception
        {
            _def = StudyManager.getInstance().getDataSetDefinition(getStudyRedirectIfNull(), form.getDatasetId());

            if (_def == null)
            {
                BeginAction action = (BeginAction)initAction(this, new BeginAction());
                return action.getView(form, errors);
            }

            return new JspView<>("/org/labkey/study/view/updateDatasetVisitMapping.jsp", _def, errors);
        }

        public boolean handlePost(DatasetForm form, BindException errors) throws Exception
        {
            DataSetDefinition original = StudyManager.getInstance().getDataSetDefinition(getStudyThrowIfNull(), form.getDatasetId());
            DataSetDefinition modified = original.createMutable();
            if (null != form.getVisitRowIds())
            {
                for (int i = 0; i < form.getVisitRowIds().length; i++)
                {
                    int visitRowId = form.getVisitRowIds()[i];
                    VisitDataSetType type = VisitDataSetType.valueOf(form.getVisitStatus()[i]);
                    if (modified.getVisitType(visitRowId) != type)
                    {
                        StudyManager.getInstance().updateVisitDataSetMapping(getUser(), getContainer(),
                                visitRowId, form.getDatasetId(), type);
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(DatasetForm dataSetForm)
        {
            return new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", dataSetForm.getDatasetId());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            if (_def != null)
            {
                VisitManager visitManager = StudyManager.getInstance().getVisitManager(getStudyThrowIfNull());
                return root.addChild("Edit " + _def.getLabel() + " " + visitManager.getPluralLabel());
            }
            return root;
        }
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class ImportAction extends AbstractQueryImportAction<ImportDataSetForm>
    {
        ImportDataSetForm _form = null;
        StudyImpl _study = null;
        DataSetDefinition _def = null;
        boolean isAliasImport = false;

        public ImportAction()
        {
            super(ImportDataSetForm.class);
        }

        @Override
        protected void initRequest(ImportDataSetForm form) throws ServletException
        {
            _form = form;
            _study = getStudyRedirectIfNull();

            if((_study.getParticipantAliasDatasetId() != null) && (_study.getParticipantAliasDatasetId() == form.getDatasetId())){
                super.setImportMessage("This is the Alias Dataset.  You do not need to include information for the date column.");
            }

            _def = StudyManager.getInstance().getDataSetDefinition(_study, form.getDatasetId());
            if (null == _def)
               throw new NotFoundException("Dataset not found");
            if (null == _def.getTypeURI())
                return;

            User user = getViewContext().getUser();
            TableInfo t = new StudyQuerySchema((StudyImpl)_study, user, true).createDatasetTableInternal(_def);
            setTarget(t);

            if (!t.hasPermission(user, InsertPermission.class) && getUser().isGuest())
                throw new UnauthorizedException();
        }

        @Override
        protected void validatePermission(User user, BindException errors)
        {
            if (user.isAdministrator() || canWrite(_def, user))
                return;
            throw new UnauthorizedException("Can't update dataset: " + _def.getName());
        }

        public ModelAndView getView(ImportDataSetForm form, BindException errors) throws Exception
        {
            initRequest(form);

            if (_def.getTypeURI() == null)
                return new HtmlView("Error", "Dataset is not yet defined. <a href=\"datasetDetails.view?id=%d\">Show Dataset Details</a>", form.getDatasetId());

            if (null == PipelineService.get().findPipelineRoot(getContainer()))
                return new RequirePipelineView((StudyImpl)_study, true, errors);

            return getDefaultImportView(form, errors);
        }


        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors) throws IOException
        {
            if (null == PipelineService.get().findPipelineRoot(getContainer()))
            {
                errors.addRowError(new ValidationException("Pipeline file system is not setup."));
                return -1;
            }

            Map<String,String> columnMap = new CaseInsensitiveHashMap<>();
            Pair<List<String>, UploadLog> result;
            try
            {
                result = AssayPublishManager.getInstance().importDatasetTSV(getUser(), (StudyImpl)_study, _def, dl, true, file, originalName, columnMap, errors);
            }
            catch (ServletException x)
            {
                errors.addRowError(new ValidationException(x.getMessage()));
                return -1;
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }

            if (!result.getKey().isEmpty())
            {
                // Log the import
                String comment = "Dataset data imported. " + result.getKey().size() + " rows imported";
                StudyServiceImpl.addDatasetAuditEvent(
                        getUser(), getContainer(), _def, comment, result.getValue());
            }

            return result.getKey().size();
        }

        @Override
        public ActionURL getSuccessURL(ImportDataSetForm form)
        {
            ActionURL url = new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
            if (StudyManager.getInstance().showQCStates(getContainer()))
                url.addParameter(SharedFormParameters.QCState, QCStateSet.getAllStates(getContainer()).getFormValue());
            return url;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild(_study.getLabel(), new ActionURL(BeginAction.class, getContainer()));
            ActionURL datasetURL = new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, _form.getDatasetId());
            root.addChild(_def.getName(), datasetURL);
            root.addChild("Import Data");
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class BulkImportDataTypesAction extends FormViewAction<BulkImportTypesForm>
    {
        public void validateCommand(BulkImportTypesForm target, Errors errors)
        {
        }

        public ModelAndView getView(BulkImportTypesForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "bulkImportDataTypes.jsp", form, errors);
        }

        @SuppressWarnings("unchecked")
        public boolean handlePost(BulkImportTypesForm form, BindException errors) throws Exception
        {
            if (form.getLabelColumn() == null)
                errors.reject(null, "Column containing dataset Label must be identified.");
            if (form.getTypeNameColumn() == null)
                errors.reject(null, "Column containing dataset Name must be identified.");
            if (form.getTypeIdColumn() == null)
                errors.reject(null, "Column containing dataset ID must be identified.");
            if (form.getTsv() == null)
                errors.reject(null, "Type definition is required.");

            if (errors.hasErrors())
                return false;

            SchemaReader reader = new SchemaTsvReader(getStudyThrowIfNull(), form.tsv, form.getLabelColumn(), form.getTypeNameColumn(), form.getTypeIdColumn(), errors);
            return StudyManager.getInstance().importDatasetSchemas(getStudyThrowIfNull(), getUser(), reader, errors);
        }

        public ActionURL getSuccessURL(BulkImportTypesForm bulkImportTypesForm)
        {
            return new ActionURL(ManageTypesAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("DatasetBulkDefinition"));
            _appendNavTrailDatasetAdmin(root);
            return root.addChild("Bulk Import");
        }
    }

    public static class BulkImportTypesForm
    {
        private String typeNameColumn;
        private String labelColumn;
        private String typeIdColumn;
        private String tsv;

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

        public String getTypeIdColumn()
        {
            return typeIdColumn;
        }

        public void setTypeIdColumn(String typeIdColumn)
        {
            this.typeIdColumn = typeIdColumn;
        }

        public String getTypeNameColumn()
        {
            return typeNameColumn;
        }

        public void setTypeNameColumn(String typeNameColumn)
        {
            this.typeNameColumn = typeNameColumn;
        }

        public String getLabelColumn()
        {
            return labelColumn;
        }

        public void setLabelColumn(String labelColumn)
        {
            this.labelColumn = labelColumn;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class ShowUploadHistoryAction extends SimpleViewAction<IdForm>
    {
        String _datasetLabel;

        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            TableInfo tInfo = StudySchema.getInstance().getTableInfoUploadLog();
            DataRegion dr = new DataRegion();
            dr.addColumns(tInfo, "RowId,Created,CreatedBy,Status,Description");
            GridView gv = new GridView(dr, errors);
            DisplayColumn dc = new SimpleDisplayColumn(null) {
                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    out.write(PageFlowUtil.textLink("Download Data File", "downloadTsv.view?id=" + ctx.get("RowId")));
                }
            };
            dr.addDisplayColumn(dc);
            dr.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

            SimpleFilter filter = SimpleFilter.createContainerFilter(getContainer());
            if (form.getId() != 0)
            {
                filter.addCondition(DataSetDefinition.DATASETKEY, form.getId());
                DataSetDefinition dsd = StudyManager.getInstance().getDataSetDefinition(getStudyRedirectIfNull(), form.getId());
                if (dsd != null)
                    _datasetLabel = dsd.getLabel();
            }

            gv.setFilter(filter);
            return gv;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Upload History" + (null != _datasetLabel ? " for " + _datasetLabel : ""));
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class DownloadTsvAction extends SimpleViewAction<IdForm>
    {
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            UploadLog ul = AssayPublishManager.getInstance().getUploadLog(getContainer(), form.getId());
            PageFlowUtil.streamFile(getViewContext().getResponse(), new File(ul.getFilePath()), true);

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DatasetItemDetailsAction extends SimpleViewAction<SourceLsidForm>
    {
        public ModelAndView getView(SourceLsidForm form, BindException errors) throws Exception
        {
            String url = LsidManager.get().getDisplayURL(form.getSourceLsid());
            if (url == null)
            {
                return new HtmlView("The assay run that produced the data has been deleted.");
            }
            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class PublishHistoryDetailsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            final StudyImpl study = getStudyRedirectIfNull();
            final ViewContext context = getViewContext();

            VBox view = new VBox();

            int datasetId = NumberUtils.toInt((String)context.get(DataSetDefinition.DATASETKEY), -1);
            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

            if (def != null)
            {
                final StudyQuerySchema querySchema = new StudyQuerySchema(study, getUser(), true);
                DataSetQuerySettings qs = (DataSetQuerySettings)querySchema.getSettings(context, DataSetQueryView.DATAREGION, def.getName());

                if (!def.canRead(getUser()))
                {
                    //requiresLogin();
                    view.addView(new HtmlView("User does not have read permission on this dataset."));
                }
                else
                {
                    String protocolId = (String)getViewContext().get("protocolId");
                    String sourceLsid = (String)getViewContext().get("sourceLsid");
                    String recordCount = (String)getViewContext().get("recordCount");

                    ActionURL deleteURL = new ActionURL(DeletePublishedRowsAction.class, getContainer());
                    deleteURL.addParameter("protocolId", protocolId);
                    deleteURL.addParameter("sourceLsid", sourceLsid);
                    final ActionButton deleteRows = new ActionButton(deleteURL, "Recall Rows");

                    deleteRows.setRequiresSelection(true, "Recall selected row of this dataset?", "Recall selected rows of this dataset?");
                    deleteRows.setActionType(ActionButton.Action.POST);
                    deleteRows.setDisplayPermission(DeletePermission.class);

                    PublishedRecordQueryView qv = new PublishedRecordQueryView(querySchema, qs, sourceLsid,
                            NumberUtils.toInt(protocolId), NumberUtils.toInt(recordCount)) {

                        @Override
                        protected void populateButtonBar(DataView view, ButtonBar bar, boolean exportAsWebPage)
                        {
                            bar.add(deleteRows);
                        }
                    };

                    view.addView(qv);
                }
            }
            else
                view.addView(new HtmlView("The Dataset does not exist."));
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Copy-to-Study History Details");
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeletePublishedRowsAction extends FormHandlerAction<DeleteDatasetRowsForm>
    {
        public void validateCommand(DeleteDatasetRowsForm target, Errors errors)
        {
        }

        public boolean handlePost(DeleteDatasetRowsForm form, BindException errors) throws Exception
        {
            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudyThrowIfNull(), form.getDatasetId());
            if (def == null)
                throw new IllegalArgumentException("Could not find a dataset definition for id: " + form.getDatasetId());

            Collection<String> allLsids;
            if (!form.isDeleteAllData())
            {
                allLsids = DataRegionSelection.getSelected(getViewContext(), true);

                if (allLsids.isEmpty())
                {
                    errors.reject("deletePublishedRows", "No rows were selected");
                    return false;
                }
            }
            else
            {
                allLsids = StudyManager.getInstance().getDatasetLSIDs(getUser(), def);
            }

            String protocolId = (String)getViewContext().get("protocolId");
            String originalSourceLsid = (String)getViewContext().get("sourceLsid");

            // Need to handle this by groups of source lsids -- each assay container needs logging
            MultiMap<String,String> sourceLsid2datasetLsid = new MultiHashMap<>();


            if (originalSourceLsid != null)
            {
                sourceLsid2datasetLsid.putAll(originalSourceLsid, allLsids);
            }
            else
            {
                Map<String,Object>[] data = StudyService.get().getDatasetRows(getUser(), getContainer(), form.getDatasetId(), allLsids);
                for (Map<String,Object> row : data)
                {
                    Object sourceLSID = row.get("sourcelsid");
                    Object lsid = row.get("lsid");
                    if (sourceLSID != null && lsid != null)
                    {
                        sourceLsid2datasetLsid.put(sourceLSID.toString(), lsid.toString());
                    }
                }
            }

            if (protocolId != null)
            {
                for (Map.Entry<String,Collection<String>> entry : sourceLsid2datasetLsid.entrySet())
                {
                    String sourceLsid = entry.getKey();
                    Container sourceContainer;
                    ExpRun expRun = ExperimentService.get().getExpRun(sourceLsid);
                    if (expRun != null && expRun.getContainer() != null)
                        sourceContainer = expRun.getContainer();
                    else
                        continue; // No logging if we can't find a matching run

                    StudyService.get().addAssayRecallAuditEvent(def, entry.getValue().size(), sourceContainer, getUser());
                }
            }
            def.deleteRows(getUser(), allLsids);

            ExpProtocol protocol = ExperimentService.get().getExpProtocol(NumberUtils.toInt(protocolId));
            if (protocol != null && originalSourceLsid != null)
            {
                ExpRun expRun = ExperimentService.get().getExpRun(originalSourceLsid);
                if (expRun != null && expRun.getContainer() != null)
                    throw new RedirectException(AssayPublishService.get().getPublishHistory(expRun.getContainer(), protocol));
            }
            return true;
        }

        public ActionURL getSuccessURL(DeleteDatasetRowsForm form)
        {
            return new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
        }
    }

    public static class DeleteDatasetRowsForm
    {
        private int datasetId;
        private boolean deleteAllData;

        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }

        public boolean isDeleteAllData()
        {
            return deleteAllData;
        }

        public void setDeleteAllData(boolean deleteAllData)
        {
            this.deleteAllData = deleteAllData;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteDatasetRowsAction extends FormHandlerAction<DeleteDatasetRowsForm>
    {
        public void validateCommand(DeleteDatasetRowsForm target, Errors errors)
        {
        }

        public boolean handlePost(DeleteDatasetRowsForm form, BindException errors) throws Exception
        {
            int datasetId = form.getDatasetId();
            StudyImpl study = getStudyThrowIfNull();
            DataSet dataset = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
            if (null == dataset)
                throw new NotFoundException();

            if (!dataset.canWrite(getUser()))
                throw new UnauthorizedException("User does not have permission to delete rows from this dataset");

            // Operate on each individually for audit logging purposes, but transact the whole thing
            DbScope scope =  StudySchema.getInstance().getSchema().getScope();
            scope.ensureTransaction();

            try
            {
                Set<String> lsids = DataRegionSelection.getSelected(getViewContext(), true);
                List<Map<String, Object>> keys = new ArrayList<>(lsids.size());
                for (String lsid : lsids)
                    keys.add(Collections.<String, Object>singletonMap("lsid", lsid));

                StudyQuerySchema schema = new StudyQuerySchema(study, getViewContext().getUser(), true);
                TableInfo datasetTable = schema.createDatasetTableInternal((DataSetDefinition) dataset);

                QueryUpdateService qus = datasetTable.getUpdateService();
                assert qus != null;

                qus.deleteRows(getViewContext().getUser(), getContainer(), keys, null);

                scope.commitTransaction();
                return true;
            }
            finally
            {
                scope.closeConnection();
            }
        }

        public ActionURL getSuccessURL(DeleteDatasetRowsForm form)
        {
            return new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
        }
    }

    public static class ImportTypeForm
    {
        private String typeName;
        private Integer dataSetId;
        private boolean autoDatasetId;
        private boolean fileImport;

        public String getTypeName()
        {
            return typeName;
        }

        public void setTypeName(String typeName)
        {
            this.typeName = typeName;
        }

        public Integer getDataSetId()
        {
            return dataSetId;
        }

        public void setDataSetId(Integer dataSetId)
        {
            this.dataSetId = dataSetId;
        }

        public boolean isAutoDatasetId()
        {
            return autoDatasetId;
        }

        public void setAutoDatasetId(boolean autoDatasetId)
        {
            this.autoDatasetId = autoDatasetId;
        }

        public boolean isFileImport()
        {
            return fileImport;
        }

        public void setFileImport(boolean fileImport)
        {
            this.fileImport = fileImport;
        }
    }

    public static class OverviewBean
    {
        public StudyImpl study;
        public Map<VisitMapKey, VisitManager.VisitStatistics> visitMapSummary;
        public boolean showAll;
        public boolean canManage;
        public CohortFilter cohortFilter;
        public boolean showCohorts;
        public QCStateSet qcStates;
        public Set<VisitStatistic> stats;
    }

    /**
     * Tweak the link url for participant view so that it contains enough information to regenerate
     * the cached list of participants.
     */
    private void setColumnURL(final ActionURL url, final QueryView queryView,
                              final UserSchema querySchema, final DataSet def) throws ServletException
    {
        List<DisplayColumn> columns;
        try
        {
            columns = queryView.getDisplayColumns();
        }
        catch (QueryParseException qpe)
        {
            return;
        }

        // push any filter, sort params, and viewname
        ActionURL base = new ActionURL(ParticipantAction.class, querySchema.getContainer());
        base.addParameter(DataSetDefinition.DATASETKEY, Integer.toString(def.getDataSetId()));
        for (Pair<String, String> param : url.getParameters())
        {
            if ((param.getKey().contains(".sort")) ||
                (param.getKey().contains("~")) ||
//                (CohortFilterFactory.isCohortFilterParameterName(param.getKey(), queryView.getDataRegionName())) ||
                (SharedFormParameters.QCState.name().equals(param.getKey())) ||
                (DATASET_VIEW_NAME_PARAMETER_NAME.equals(param.getKey())))
            {
                base.addParameter(param.getKey(), param.getValue());
            }
        }
        if (queryView instanceof StudyQueryView && null != ((StudyQueryView)queryView).getCohortFilter())
            ((StudyQueryView)queryView).getCohortFilter().addURLParameters(getStudyThrowIfNull(), base, null);

        for (DisplayColumn col : columns)
        {
            String subjectColName = StudyService.get().getSubjectColumnName(def.getContainer());
            if (subjectColName.equalsIgnoreCase(col.getName()))
                col.setURLExpression(new DetailsURL(base, "participantId", col.getColumnInfo().getFieldKey()));
        }
    }

    private boolean hasSourceLsids(TableInfo datasetTable) throws SQLException
    {
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

    @RequiresPermissionClass(ReadPermission.class)
    public class ProtocolDocumentDownloadAction extends SimpleViewAction<AttachmentForm>
    {
        public ModelAndView getView(AttachmentForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();
            AttachmentService.get().download(getViewContext().getResponse(), study.getProtocolDocumentAttachmentParent(), form.getName());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    private static final String PARTICIPANT_PROPS_CACHE = "Study_participants/propertyCache";
    private static final String DATASET_SORT_COLUMN_CACHE = "Study_participants/datasetSortColumnCache";
    @SuppressWarnings("unchecked")
    private static Map<String, PropertyDescriptor[]> getParticipantPropsMap(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, PropertyDescriptor[]> map = (Map<String, PropertyDescriptor[]>) session.getAttribute(PARTICIPANT_PROPS_CACHE);
        if (map == null)
        {
            map = new HashMap<>();
            session.setAttribute(PARTICIPANT_PROPS_CACHE, map);
        }
        return map;
    }

    public static PropertyDescriptor[] getParticipantPropsFromCache(ViewContext context, String typeURI)
    {
        Map<String, PropertyDescriptor[]> map = getParticipantPropsMap(context);
        PropertyDescriptor[] props = map.get(typeURI);
        if (props == null)
        {
            props = OntologyManager.getPropertiesForType(typeURI, context.getContainer());
            map.put(typeURI, props);
        }
        return props;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Integer>> getDatasetSortColumnMap(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, Map<String, Integer>> map = (Map<String, Map<String, Integer>>) session.getAttribute(DATASET_SORT_COLUMN_CACHE);
        if (map == null)
        {
            map = new HashMap<>();
            session.setAttribute(DATASET_SORT_COLUMN_CACHE, map);
        }
        return map;
    }

    public static Map<String, Integer> getSortedColumnList(ViewContext context, DataSet dsd)
    {
        Map<String, Map<String, Integer>> map = getDatasetSortColumnMap(context);
        Map<String, Integer> sortMap = map.get(dsd.getLabel());

        if (sortMap == null)
        {
            QueryDefinition qd = QueryService.get().getQueryDef(context.getUser(), dsd.getContainer(), "study", dsd.getName());
            if (qd == null)
            {
                UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
                qd = schema.getQueryDefForTable(dsd.getName());
            }
            CustomView cview = qd.getCustomView(context.getUser(), context.getRequest(), null);
            if (cview != null)
            {
                sortMap = new HashMap<>();
                int i = 0;
                for (FieldKey key : cview.getColumns())
                {
                    final String name = key.toString();
                    if (!sortMap.containsKey(name))
                        sortMap.put(name, i++);
                }
                map.put(dsd.getLabel(), sortMap);
            }
            else
            {
                // there is no custom view for this dataset
                sortMap = Collections.emptyMap();
                map.put(dsd.getLabel(), Collections.<String,Integer>emptyMap());
            }
        }
        return new CaseInsensitiveHashMap<>(sortMap);
    }

    private static String getParticipantListCacheKey(int dataset, String viewName, CohortFilter cohortFilter, String encodedQCState)
    {
        String key = Integer.toString(dataset);
        // if there is also a view associated with the dataset, incorporate it into the key as well
        if (viewName != null && !StringUtils.isEmpty(viewName))
            key = key + viewName;
        if (cohortFilter != null)
            key = key + "cohort" + cohortFilter.getCacheKey();
        if (encodedQCState != null)
            key = key + "qcState" + encodedQCState;
        return key;
    }

    public static void removeParticipantListFromCache(ViewContext context, int dataset, String viewName, CohortFilter cohortFilter, String encodedQCState)
    {
        Map<String, List<String>> map = getParticipantMapFromCache(context);
        map.remove(getParticipantListCacheKey(dataset, viewName, cohortFilter, encodedQCState));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> getParticipantMapFromCache(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, List<String>> map = (Map<String, List<String>>) session.getAttribute(PARTICIPANT_CACHE_PREFIX);
        if (map == null)
        {
            map = new HashMap<>();
            session.setAttribute(PARTICIPANT_CACHE_PREFIX, map);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Map<Integer, String> getExpandedState(ViewContext viewContext, int datasetId)
    {
        HttpSession session = viewContext.getRequest().getSession(true);
        Map<Integer, Map<Integer, String>> map = (Map<Integer, Map<Integer, String>>) session.getAttribute(EXPAND_CONTAINERS_KEY);
        if (map == null)
        {
            map = new HashMap<>();
            session.setAttribute(EXPAND_CONTAINERS_KEY, map);
        }

        Map<Integer, String> expandedMap = map.get(datasetId);
        if (expandedMap == null)
        {
            expandedMap = new HashMap<>();
            map.put(datasetId, expandedMap);
        }
        return expandedMap;
    }

    public static List<String> getParticipantListFromCache(ViewContext context, int dataset, String viewName, CohortFilter cohortFilter, String encodedQCState)
    {
        Map<String, List<String>> map = getParticipantMapFromCache(context);
        String key = getParticipantListCacheKey(dataset, viewName, cohortFilter, encodedQCState);
        List<String> plist = map.get(key);
        if (plist == null)
        {
            // not in cache, or session expired, try to regenerate the list
            plist = generateParticipantListFromURL(context, dataset, viewName, cohortFilter, encodedQCState);
            map.put(key, plist);
        }
        return plist;
    }

    private static List<String> generateParticipantListFromURL(ViewContext context, int dataset, String viewName, CohortFilter cohortFilter, String encodedQCState)
    {
        try
        {
            final StudyManager studyMgr = StudyManager.getInstance();
            final StudyImpl study = studyMgr.getStudy(context.getContainer());
            QCStateSet qcStateSet = null;
            if (StudyManager.getInstance().showQCStates(context.getContainer()))
            {
                qcStateSet = QCStateSet.getSelectedStates(context.getContainer(), encodedQCState);
            }

            DataSetDefinition def = studyMgr.getDataSetDefinition(study, dataset);
            if (null == def)
                return Collections.emptyList();
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                return Collections.emptyList();

            int visitRowId = null == context.get(VisitImpl.VISITKEY) ? 0 : Integer.parseInt((String) context.get(VisitImpl.VISITKEY));
            if (visitRowId != 0)
            {
                VisitImpl visit = studyMgr.getVisitForRowId(study, visitRowId);
                if (null == visit)
                    return Collections.emptyList();
            }
            StudyQuerySchema querySchema = new StudyQuerySchema(study, context.getUser(), true);
            QuerySettings qs = querySchema.getSettings(context, DataSetQueryView.DATAREGION, def.getName());
            qs.setViewName(viewName);

            QueryView queryView = querySchema.createView(context, qs, null);

            return generateParticipantList(queryView);
        }
        catch (Exception e)
        {
            _log.error(e);
        }
        return Collections.emptyList();
    }

    public static List<String> generateParticipantList(QueryView queryView)
    {
        final TableInfo table = queryView.getTable();

        if (table != null)
        {
            ResultSet rs = null;

            try
            {
                // Do a single-column query to get the list of participants that match the filter criteria for this
                // dataset
                FieldKey ptidKey = FieldKey.fromParts(StudyService.get().getSubjectColumnName(queryView.getContainer()));
                Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(table, Collections.singleton(ptidKey));
                ColumnInfo ptidColumnInfo = columns.get(ptidKey);
                // Don't bother unless we actually found the participant column (we always should)
                if (ptidColumnInfo != null)
                {
                    // Go through the RenderContext directly to get the ResultSet so that we don't also end up calculating
                    // row counts or other aggregates we don't care about
                    DataView dataView = queryView.createDataView();
                    RenderContext ctx = dataView.getRenderContext();
                    DataRegion dataRegion = dataView.getDataRegion();
                    queryView.getSettings().setShowRows(ShowRows.ALL);
                    rs = ctx.getResultSet(columns, table, queryView.getSettings(), dataRegion.getQueryParameters(), Table.ALL_ROWS, dataRegion.getOffset(), dataRegion.getName(), false);
                    int ptidIndex = (null != ptidColumnInfo) ? rs.findColumn(ptidColumnInfo.getAlias()) : 0;

                    Set<String> participantSet = new LinkedHashSet<>();
                    while (rs.next() && ptidIndex > 0)
                    {
                        String ptid = rs.getString(ptidIndex);
                        participantSet.add(ptid);
                    }
                    return new ArrayList<>(participantSet);
                }
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }
        return Collections.emptyList();
    }

    public static class VisitForm extends ViewForm
    {
        private int[] _dataSetIds;
        private String[] _dataSetStatus;
        private Double _sequenceNumMin;
        private Double _sequenceNumMax;
        private Character _typeCode;
        private boolean _showByDefault;
        private Integer _cohortId;
        private String _label;
        private VisitImpl _visit;
        private int _visitDateDatasetId;
        private String _sequenceNumHandling;

        public VisitForm()
        {
        }

        public void validate(Errors errors, Study study)
        {
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            HttpServletRequest request = getRequest();

            if (null != StringUtils.trimToNull(request.getParameter(".oldValues")))
            {
                try
                {
                    _visit = (VisitImpl) PageFlowUtil.decodeObject(request.getParameter(".oldValues"));
                }
                catch (IOException x)
                {
                    throw new RuntimeException(x);
                }
            }

            //check for null min/max sequence numbers
            if (null == getSequenceNumMax() && null == getSequenceNumMin())
                errors.reject(null, "You must specify at least a minimum or a maximum value for the visit range.");

            //if min is null but max is not, set min to max
            //and vice-versa
            if (null == getSequenceNumMin() && null != getSequenceNumMax())
                setSequenceNumMin(getSequenceNumMax());
            if (null == getSequenceNumMax() && null != getSequenceNumMin())
                setSequenceNumMax(getSequenceNumMin());

            VisitImpl visit = getBean();
            if (visit.getSequenceNumMin() > visit.getSequenceNumMax())
            {
                double min = visit.getSequenceNumMax();
                double max = visit.getSequenceNumMin();
                visit.setSequenceNumMax(max);
                visit.setSequenceNumMin(min);
            }
            setBean(visit);
        }

        public VisitImpl getBean()
        {
            if (null == _visit)
                _visit = new VisitImpl();

            _visit.setContainer(getContainer());

            if (getTypeCode() != null)
                _visit.setTypeCode(getTypeCode());

            _visit.setLabel(getLabel());

            if (null != getSequenceNumMax())
                _visit.setSequenceNumMax(getSequenceNumMax());
            if (null != getSequenceNumMin())
                _visit.setSequenceNumMin(getSequenceNumMin());

            _visit.setCohortId(getCohortId());
            _visit.setVisitDateDatasetId(getVisitDateDatasetId());

            _visit.setSequenceNumHandling(getSequenceNumHandling());
            return _visit;
        }

        public void setBean(VisitImpl bean)
        {
            if (0 != bean.getSequenceNumMax())
                setSequenceNumMax(bean.getSequenceNumMax());
            if (0 != bean.getSequenceNumMin())
                setSequenceNumMin(bean.getSequenceNumMin());
            if (null != bean.getType())
                setTypeCode(bean.getTypeCode());
            setLabel(bean.getLabel());
            setCohortId(bean.getCohortId());
            setSequenceNumHandling(bean.getSequenceNumHandling());
        }

        public String[] getDataSetStatus()
        {
            return _dataSetStatus;
        }

        public void setDataSetStatus(String[] dataSetStatus)
        {
            _dataSetStatus = dataSetStatus;
        }

        public int[] getDataSetIds()
        {
            return _dataSetIds;
        }

        public void setDataSetIds(int[] dataSetIds)
        {
            _dataSetIds = dataSetIds;
        }

        public Double getSequenceNumMin()
        {
            return _sequenceNumMin;
        }

        public void setSequenceNumMin(Double sequenceNumMin)
        {
            _sequenceNumMin = sequenceNumMin;
        }

        public Double getSequenceNumMax()
        {
            return _sequenceNumMax;
        }

        public void setSequenceNumMax(Double sequenceNumMax)
        {
            _sequenceNumMax = sequenceNumMax;
        }

        public Character getTypeCode()
        {
            return _typeCode;
        }

        public void setTypeCode(Character typeCode)
        {
            _typeCode = typeCode;
        }

        public boolean isShowByDefault()
        {
            return _showByDefault;
        }

        public void setShowByDefault(boolean showByDefault)
        {
            this._showByDefault = showByDefault;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            this._label = label;
        }

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }

        public int getVisitDateDatasetId()
        {
            return _visitDateDatasetId;
        }

        public void setVisitDateDatasetId(int visitDateDatasetId)
        {
            _visitDateDatasetId = visitDateDatasetId;
        }

        public String getSequenceNumHandling()
        {
            return _sequenceNumHandling;
        }

        public void setSequenceNumHandling(String sequenceNumHandling)
        {
            _sequenceNumHandling = sequenceNumHandling;
        }
    }

    public static class ManageQCStatesBean
    {
        private StudyImpl _study;
        private QCState[] _states;
        private ReturnURLString _returnUrl;

        public ManageQCStatesBean(StudyImpl study, ReturnURLString returnUrl)
        {
            _study = study;
            _returnUrl = returnUrl;
        }

        public QCState[] getQCStates()
        {
            if (_states == null)
                _states = StudyManager.getInstance().getQCStates(_study.getContainer());
            return _states;
        }

        public StudyImpl getStudy()
        {
            return _study;
        }

        public ReturnURLString getReturnUrl()
        {
            return _returnUrl;
        }
    }

    public static class ManageQCStatesForm
    {
        private int[] _ids;
        private String[] _labels;
        private String[] _descriptions;
        private int[] _publicData;
        private String _newLabel;
        private String _newDescription;
        private boolean _newPublicData;
        private boolean _reshowPage;
        private Integer _defaultPipelineQCState;
        private Integer _defaultAssayQCState;
        private Integer _defaultDirectEntryQCState;
        private boolean _showPrivateDataByDefault;
        private boolean _blankQCStatePublic;
        private ReturnURLString _returnUrl;

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public String[] getLabels()
        {
            return _labels;
        }

        public void setLabels(String[] labels)
        {
            _labels = labels;
        }

        public String[] getDescriptions()
        {
            return _descriptions;
        }

        public void setDescriptions(String[] descriptions)
        {
            _descriptions = descriptions;
        }

        public int[] getPublicData()
        {
            return _publicData;
        }

        public void setPublicData(int[] publicData)
        {
            _publicData = publicData;
        }

        public String getNewLabel()
        {
            return _newLabel;
        }

        public void setNewLabel(String newLabel)
        {
            _newLabel = newLabel;
        }

        public String getNewDescription()
        {
            return _newDescription;
        }

        public void setNewDescription(String newDescription)
        {
            _newDescription = newDescription;
        }

        public boolean isNewPublicData()
        {
            return _newPublicData;
        }

        public void setNewPublicData(boolean newPublicData)
        {
            _newPublicData = newPublicData;
        }

        public boolean isReshowPage()
        {
            return _reshowPage;
        }

        public void setReshowPage(boolean reshowPage)
        {
            _reshowPage = reshowPage;
        }

        public Integer getDefaultPipelineQCState()
        {
            return _defaultPipelineQCState;
        }

        public void setDefaultPipelineQCState(Integer defaultPipelineQCState)
        {
            _defaultPipelineQCState = defaultPipelineQCState;
        }

        public Integer getDefaultAssayQCState()
        {
            return _defaultAssayQCState;
        }

        public void setDefaultAssayQCState(Integer defaultAssayQCState)
        {
            _defaultAssayQCState = defaultAssayQCState;
        }

        public Integer getDefaultDirectEntryQCState()
        {
            return _defaultDirectEntryQCState;
        }

        public void setDefaultDirectEntryQCState(Integer defaultDirectEntryQCState)
        {
            _defaultDirectEntryQCState = defaultDirectEntryQCState;
        }

        public boolean isShowPrivateDataByDefault()
        {
            return _showPrivateDataByDefault;
        }

        public void setShowPrivateDataByDefault(boolean showPrivateDataByDefault)
        {
            _showPrivateDataByDefault = showPrivateDataByDefault;
        }

        public ReturnURLString getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(ReturnURLString returnUrl)
        {
            _returnUrl = returnUrl;
        }

        public boolean isBlankQCStatePublic()
        {
            return _blankQCStatePublic;
        }

        public void setBlankQCStatePublic(boolean blankQCStatePublic)
        {
            _blankQCStatePublic = blankQCStatePublic;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageQCStatesAction extends FormViewAction<ManageQCStatesForm>
    {
        public ModelAndView getView(ManageQCStatesForm manageQCStatesForm, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/study/view/manageQCStates.jsp",
                    new ManageQCStatesBean(getStudyRedirectIfNull(), manageQCStatesForm.getReturnUrl()), errors);
        }

        public void validateCommand(ManageQCStatesForm form, Errors errors)
        {
            Set<String> labels = new HashSet<>();
            if (form.getLabels() != null)
            {
                for (String label : form.getLabels())
                {
                    if (labels.contains(label))
                    {
                        errors.reject(null, "QC state \"" + label + "\" is defined more than once.");
                        return;
                    }
                    else
                        labels.add(label);
                }
            }
            if (labels.contains(form.getNewLabel()))
                errors.reject(null, "QC state \"" + form.getNewLabel() + "\" is defined more than once.");
        }

        public boolean handlePost(ManageQCStatesForm form, BindException errors) throws Exception
        {
            if (form.getNewLabel() != null && form.getNewLabel().length() > 0)
            {
                QCState newState = new QCState();
                newState.setContainer(getContainer());
                newState.setLabel(form.getNewLabel());
                newState.setDescription(form.getNewDescription());
                newState.setPublicData(form.isNewPublicData());
                StudyManager.getInstance().insertQCState(getUser(), newState);
            }
            if (form.getIds() != null)
            {
                // use a map to store the IDs of the public QC states; since checkboxes are
                // omitted from the request entirely if they aren't checked, we use a different
                // method for keeping track of the checked values (by posting the rowid of the item as the
                // checkbox value).
                Set<Integer> publicDataSet = new HashSet<>();
                if (form.getPublicData() != null)
                {
                    for (int i = 0; i < form.getPublicData().length; i++)
                        publicDataSet.add(form.getPublicData()[i]);
                }

                for (int i = 0; i < form.getIds().length; i++)
                {
                    int rowId = form.getIds()[i];
                    QCState state = new QCState();
                    state.setRowId(rowId);
                    state.setLabel(form.getLabels()[i]);
                    if (form.getDescriptions() != null)
                        state.setDescription(form.getDescriptions()[i]);
                    state.setPublicData(publicDataSet.contains(state.getRowId()));
                    state.setContainer(getContainer());
                    StudyManager.getInstance().updateQCState(getUser(), state);
                }
            }

            updateQcState(getStudyThrowIfNull(), getUser(), form);

            return true;
        }

        public ActionURL getSuccessURL(ManageQCStatesForm manageQCStatesForm)
        {
            if (manageQCStatesForm.isReshowPage())
            {
                ActionURL url = new ActionURL(ManageQCStatesAction.class, getContainer());
                if (manageQCStatesForm.getReturnUrl() != null)
                    url.addParameter(ActionURL.Param.returnUrl, manageQCStatesForm.getReturnUrl());
                return url;
            }
            else if (manageQCStatesForm.getReturnUrl() != null)
                return new ActionURL(manageQCStatesForm.getReturnUrl());
            else
                return new ActionURL(ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage Dataset QC States");
        }
    }

    // TODO: Move to StudyManager?
    public static void updateQcState(StudyImpl study, User user, ManageQCStatesForm form) throws SQLException
    {
        if (!nullSafeEqual(study.getDefaultAssayQCState(), form.getDefaultAssayQCState()) ||
            !nullSafeEqual(study.getDefaultPipelineQCState(), form.getDefaultPipelineQCState()) ||
            !nullSafeEqual(study.getDefaultDirectEntryQCState(), form.getDefaultDirectEntryQCState()) ||
            !nullSafeEqual(study.isBlankQCStatePublic(), form.isBlankQCStatePublic()) ||
            study.isShowPrivateDataByDefault() != form.isShowPrivateDataByDefault())
        {
            study = study.createMutable();
            study.setDefaultAssayQCState(form.getDefaultAssayQCState());
            study.setDefaultPipelineQCState(form.getDefaultPipelineQCState());
            study.setDefaultDirectEntryQCState(form.getDefaultDirectEntryQCState());
            study.setShowPrivateDataByDefault(form.isShowPrivateDataByDefault());
            study.setBlankQCStatePublic(form.isBlankQCStatePublic());
            StudyManager.getInstance().updateStudy(user, study);
        }
    }

    public static class DeleteQCStateForm extends IdForm
    {
        private boolean _all = false;
        private String _manageReturnUrl;

        public boolean isAll()
        {
            return _all;
        }

        public void setAll(boolean all)
        {
            _all = all;
        }

        public String getManageReturnUrl()
        {
            return _manageReturnUrl;
        }

        public void setManageReturnUrl(String manageReturnUrl)
        {
            _manageReturnUrl = manageReturnUrl;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteQCStateAction extends RedirectAction<DeleteQCStateForm>
    {
        public boolean doAction(DeleteQCStateForm form, BindException errors) throws Exception
        {
            if (form.isAll())
            {
                QCState[] states = StudyManager.getInstance().getQCStates(getContainer());
                for (QCState state : states)
                {
                    if (!StudyManager.getInstance().isQCStateInUse(state))
                        StudyManager.getInstance().deleteQCState(state);
                }
            }
            else
            {
                QCState state = StudyManager.getInstance().getQCStateForRowId(getContainer(), form.getId());
                if (state != null)
                    StudyManager.getInstance().deleteQCState(state);
            }
            return true;
        }

        public void validateCommand(DeleteQCStateForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(DeleteQCStateForm form)
        {
            ActionURL returnUrl = new ActionURL(ManageQCStatesAction.class, getContainer());
            if (form.getManageReturnUrl() != null)
                returnUrl.addParameter(ActionURL.Param.returnUrl, form.getManageReturnUrl());
            return returnUrl;
        }
    }

    public static class UpdateQCStateForm
    {
        private String _comments;
        private boolean _update;
        private int _datasetId;
        private String _dataRegionSelectionKey;
        private Integer _newState;
        private DataSetQueryView _queryView;

        public String getComments()
        {
            return _comments;
        }

        public void setComments(String comments)
        {
            _comments = comments;
        }

        public boolean isUpdate()
        {
            return _update;
        }

        public void setUpdate(boolean update)
        {
            _update = update;
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public Integer getNewState()
        {
            return _newState;
        }

        public void setNewState(Integer newState)
        {
            _newState = newState;
        }

        public void setQueryView(DataSetQueryView queryView)
        {
            _queryView = queryView;
        }

        public DataSetQueryView getQueryView()
        {
            return _queryView;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateQCStateAction extends FormViewAction<UpdateQCStateForm>
    {
        private int _datasetId;

        public void validateCommand(UpdateQCStateForm updateQCForm, Errors errors)
        {
            if (updateQCForm.isUpdate())
            {
                if (updateQCForm.getComments() == null || updateQCForm.getComments().length() == 0)
                    errors.reject(null, "Comments are required.");
            }
        }

        public ModelAndView getView(UpdateQCStateForm updateQCForm, boolean reshow, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();
            _datasetId = updateQCForm.getDatasetId();
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, _datasetId);
            if (def == null)
            {
                throw new NotFoundException("No dataset found for id: " + _datasetId);
            }
            Set<String> lsids = null;
            if ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
                lsids = DataRegionSelection.getSelected(getViewContext(), updateQCForm.getDataRegionSelectionKey(), true, false);
            if (lsids == null || lsids.isEmpty())
                return new HtmlView("No data rows selected.  " + PageFlowUtil.textLink("back", "javascript:back()"));
            StudyQuerySchema querySchema = new StudyQuerySchema(study, getUser(), true);
            DataSetQuerySettings qs = new DataSetQuerySettings(getViewContext().getBindPropertyValues(), DataSetQueryView.DATAREGION);

            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getName());
            qs.setMaxRows(Table.ALL_ROWS);
            qs.setShowSourceLinks(false);
            qs.setShowEditLinks(false);

            final Set<String> finalLsids = lsids;

            DataSetQueryView queryView = new DataSetQueryView(querySchema, qs, errors)
            {
                public DataView createDataView()
                {
                    DataView view = super.createDataView();
                    view.getDataRegion().setSortable(false);
                    view.getDataRegion().setShowFilters(false);
                    view.getDataRegion().setShowRecordSelectors(false);
                    view.getDataRegion().setShowPagination(false);
                    SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
                    if (null == filter)
                    {
                        filter = new SimpleFilter();
                        view.getRenderContext().setBaseFilter(filter);
                    }
                    filter.addInClause("lsid", new ArrayList<>(finalLsids));
                    return view;
                }
            };
            queryView.setShowDetailsColumn(false);
            updateQCForm.setQueryView(queryView);
            updateQCForm.setDataRegionSelectionKey(DataRegionSelection.getSelectionKeyFromRequest(getViewContext()));
            return new JspView<>("/org/labkey/study/view/updateQCState.jsp", updateQCForm, errors);
        }

        public boolean handlePost(UpdateQCStateForm updateQCForm, BindException errors) throws Exception
        {
            if (!updateQCForm.isUpdate())
                return false;
            Set<String> lsids = DataRegionSelection.getSelected(getViewContext(), updateQCForm.getDataRegionSelectionKey(), true, false);

            QCState newState = null;
            if (updateQCForm.getNewState() != null)
            {
                newState = StudyManager.getInstance().getQCStateForRowId(getContainer(), updateQCForm.getNewState().intValue());
                if (newState == null)
                {
                    errors.reject(null, "The selected state could not be found.  It may have been deleted from the database.");
                    return false;
                }
            }
            StudyManager.getInstance().updateDataQCState(getContainer(), getUser(),
                    updateQCForm.getDatasetId(), lsids, newState, updateQCForm.getComments());

            // if everything has succeeded, we can clear our saved checkbox state now:
            DataRegionSelection.clearAll(getViewContext(), updateQCForm.getDataRegionSelectionKey());
            return true;
        }

        public ActionURL getSuccessURL(UpdateQCStateForm updateQCForm)
        {
            ActionURL url = new ActionURL(DatasetAction.class, getContainer());
            url.addParameter(DataSetDefinition.DATASETKEY, updateQCForm.getDatasetId());
            if (updateQCForm.getNewState() != null)
                url.addParameter(SharedFormParameters.QCState, updateQCForm.getNewState().intValue());
            return url;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = _appendNavTrail(root, _datasetId, -1);
            return root.addChild("Change QC State");
        }
    }

    // GWT Action
    @RequiresPermissionClass(AdminPermission.class)
    public class DatasetServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService() throws IllegalStateException
        {
            return new DatasetServiceImpl(getViewContext(), getStudyThrowIfNull(), StudyManager.getInstance());
        }
    }

    public class ResetPipelinePathForm extends PipelinePathForm
    {
        private String _redirect;

        public String getRedirect()
        {
            return _redirect;
        }

        public void setRedirect(String redirect)
        {
            _redirect = redirect;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ResetPipelineAction extends SimpleRedirectAction<ResetPipelinePathForm>
    {
        public ActionURL getRedirectURL(ResetPipelinePathForm form) throws Exception
        {
            Container c = getContainer();

            for (File f : form.getValidatedFiles(c))
            {
                if (f.isFile() && f.getName().endsWith(".lock"))
                {
                    f.delete();
                }
            }

            String redirect = form.getRedirect();
            if (null != redirect)
            {
                throw new RedirectException(redirect);
            }
            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DefaultDatasetReportAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            ViewContext context = getViewContext();
            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));

            ActionURL url = getViewContext().cloneActionURL();
            url.setAction(DatasetReportAction.class);

            String defaultView = getDefaultView(context, datasetId);
            if (!StringUtils.isEmpty(defaultView))
            {
                ReportIdentifier reportId = ReportService.get().getReportIdentifier(defaultView);
                if (reportId != null)
                    url.addParameter(DATASET_REPORT_ID_PARAMETER_NAME, defaultView);
                else
                    url.addParameter(DATASET_VIEW_NAME_PARAMETER_NAME, defaultView);
            }
            return url;
        }
    }



    @RequiresPermissionClass(AdminPermission.class)
    public class ManageUndefinedTypesAction extends SimpleViewAction
    {
        StudyImpl study;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            study = getStudyRedirectIfNull();
            return new StudyJspView<>(study, "manageUndefinedTypes.jsp", o, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            return root.addChild("Define Dataset Schemas");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class TemplateAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            Study study = getStudyThrowIfNull();
            ViewContext context = getViewContext();

            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
            if (null == def)
            {
                redirectTypeNotFound(datasetId);
                return;
            }
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                redirectTypeNotFound(datasetId);

            TableInfo tinfo = def.getTableInfo(getUser(), true);

            DataRegion dr = new DataRegion();
            dr.setTable(tinfo);

            Set<String> ignoreColumns = new CaseInsensitiveHashSet("createdby", "modifiedby", "lsid", "_key", "participantsequencenum", "datasetid", "visitdate", "sourcelsid", "created", "modified", "visitrowid", "day", "qcstate", "dataset");
            if (study.getTimepointType() != TimepointType.VISIT)
                ignoreColumns.add("SequenceNum");

            // If this is demographic data, user doesn't need to enter visit info -- we have defaults.
            if (def.isDemographicData())
            {
                if (study.getTimepointType() == TimepointType.VISIT)
                    ignoreColumns.add("SequenceNum");
                else // DATE or NONE
                    ignoreColumns.add("Date");
            }
            if (def.getKeyManagementType() == DataSet.KeyManagementType.None)
            {
                // Do not include a server-managed key field
                ignoreColumns.add(def.getKeyPropertyName());
            }

            // Need to ignore field-level qc columns that are generated
            for (ColumnInfo col : tinfo.getColumns())
            {
                if (col.isMvEnabled())
                {
                    ignoreColumns.add(col.getMvColumnName().getName());
                    ignoreColumns.add(col.getName() + RawValueColumn.RAW_VALUE_SUFFIX);
                }
            }

            for (ColumnInfo col : tinfo.getColumns())
            {
                if (ignoreColumns.contains(col.getName()))
                    continue;

                DataColumn dc = new DataColumn(col);
                //DO NOT use friendly names. We will import this later.
                dc.setCaption(col.getAlias());
                dr.addDisplayColumn(dc);
            }
            DisplayColumn replaceColumn = new SimpleDisplayColumn();
            replaceColumn.setCaption("replace");
            dr.addDisplayColumn(replaceColumn);

            SimpleFilter filter = new SimpleFilter();
            filter.addWhereClause("0 = 1", new Object[]{});

            RenderContext ctx = new RenderContext(getViewContext());
            ctx.setContainer(getContainer());
            ctx.setBaseFilter(filter);

            Results rs = dr.getResultSet(ctx);
            List<DisplayColumn> cols = dr.getDisplayColumns();
            ExcelWriter xl = new ExcelWriter(rs, cols);
            xl.write(response);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ViewPreferencesAction extends SimpleViewAction
    {
        StudyImpl study;
        DataSet def;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            study = getStudyRedirectIfNull();


            String id = getViewContext().getRequest().getParameter(DataSetDefinition.DATASETKEY);
            String defaultView = getViewContext().getRequest().getParameter("defaultView");

            if (NumberUtils.isNumber(id))
            {
                int dsid = NumberUtils.toInt(id);
                def = StudyManager.getInstance().getDataSetDefinition(study, dsid);
                if (def != null)
                {
                    List<Pair<String, String>> views = ReportManager.get().getReportLabelsForDataset(getViewContext(), def);
                    if (defaultView != null)
                    {
                        setDefaultView(getViewContext(), dsid, defaultView);
                    }
                    else
                    {
                        defaultView = getDefaultView(getViewContext(), def.getDataSetId());
                        if (!StringUtils.isEmpty(defaultView))
                        {
                            boolean defaultExists = false;
                            for (Pair<String, String> view : views)
                            {
                                if (StringUtils.equals(view.getValue(), defaultView))
                                {
                                    defaultExists = true;
                                    break;
                                }
                            }
                            if (!defaultExists)
                                setDefaultView(getViewContext(), dsid, "");
                        }
                    }

                    ViewPrefsBean bean = new ViewPrefsBean(views, def);
                    return new StudyJspView<>(study, "viewPreferences.jsp", bean, errors);
                }
            }
            throw new NotFoundException("Invalid dataset ID");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("Set Default View"));

            root.addChild(study.getLabel(), new ActionURL(BeginAction.class, getContainer()));

            ActionURL datasetURL = getViewContext().getActionURL().clone();
            datasetURL.setAction(DatasetAction.class);

            String label = def.getLabel() != null ? def.getLabel() : "" + def.getDataSetId();
            root.addChild(new NavTree(label, datasetURL.getLocalURIString()));

            root.addChild(new NavTree("View Preferences"));
            return root;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ImportStudyBatchAction extends SimpleViewAction<PipelinePathForm>
    {
        private String path;

        public ModelAndView getView(PipelinePathForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            File definitionFile = form.getValidatedSingleFile(c);
            path = form.getPath();
            if (!path.endsWith("/"))
            {
                path += "/";
            }
            path += definitionFile.getName();

            if (!definitionFile.isFile())
            {
                throw new NotFoundException();
            }

            File lockFile = StudyPipeline.lockForDataset(getStudyRedirectIfNull(), definitionFile);

            if (!definitionFile.canRead())
                errors.reject("importStudyBatch", "Can't read dataset file: " + path);
            if (lockFile.exists())
                errors.reject("importStudyBatch", "Lock file exists.  Delete file before running import. " + lockFile.getName());

            VirtualFile datasetsDir = new FileSystemFile(definitionFile.getParentFile());
            DatasetFileReader reader = new DatasetFileReader(datasetsDir, definitionFile.getName(), getStudyRedirectIfNull());

            if (!errors.hasErrors())
            {
                List<String> parseErrors = new ArrayList<>();
                reader.validate(parseErrors);
                for (String error : parseErrors)
                    errors.reject("importStudyBatch", error);
            }

            return new StudyJspView<>(
                    getStudyRedirectIfNull(), "importStudyBatch.jsp", new ImportStudyBatchBean(reader, path), errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                root.addChild(getStudyRedirectIfNull().getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
                root.addChild("Import Study Batch - " + path);
                return root;
            }
            catch (ServletException se)
            {
                throw UnexpectedException.wrap(se);
            }
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SubmitStudyBatchAction extends SimpleRedirectAction<PipelinePathForm>
    {
        public ActionURL getRedirectURL(PipelinePathForm form) throws Exception
        {
            Study study = getStudyRedirectIfNull();
            Container c = getContainer();
            String path = form.getPath();
            File f = null;

            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (path != null)
            {
                if (root != null)
                    f = root.resolvePath(path);
            }

            try
            {
                if (f != null)
                {
                    VirtualFile datasetsDir = new FileSystemFile(f.getParentFile());
                    DatasetImportUtils.submitStudyBatch(study, datasetsDir, f.getName(), c, getUser(), getViewContext().getActionURL(), root);
                }
            }
            catch (DatasetImportUtils.DatasetLockExistsException e)
            {
                ActionURL importURL = new ActionURL(ImportStudyBatchAction.class, getContainer());
                importURL.addParameter("path", form.getPath());
                return importURL;
            }

            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ImportStudyFromPipelineAction extends SimpleRedirectAction<PipelinePathForm>
    {
        public ActionURL getRedirectURL(PipelinePathForm form) throws Exception
        {
            Container c = getContainer();

            File studyFile = form.getValidatedSingleFile(c);

            @SuppressWarnings({"ThrowableInstanceNeverThrown"})
            BindException errors = new NullSafeBindException(c, "import");

            boolean success = importStudy(getViewContext(), errors, studyFile, studyFile.getName());

            if (success && !errors.hasErrors())
            {
                return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);
            }
            else
            {
                ObjectError firstError = (ObjectError)errors.getAllErrors().get(0);
                throw new ImportException(firstError.getDefaultMessage());
            }
        }

        @Override
        protected ModelAndView getErrorView(Exception e, BindException errors) throws Exception
        {
            try
            {
                throw e;
            }
            catch (ImportException sie)
            {
                errors.reject("studyImport", e.getMessage());
                return new SimpleErrorView(errors);
            }
        }
    }


    public static boolean importStudy(ViewContext context, BindException errors, File studyFile, String originalFilename) throws ServletException, SQLException, IOException, ParserConfigurationException, SAXException, XmlException, InvalidFileException
    {
        Container c = context.getContainer();
        PipeRoot pipelineRoot = StudyReload.getPipelineRoot(c);

        File studyXml;

        if (studyFile.getName().endsWith(".zip"))
        {
            String dirName = "unzip";
            File importDir = pipelineRoot.resolvePath(dirName);

            if (importDir.exists() && !FileUtil.deleteDir(importDir))
            {
                errors.reject("studyImport", "Import failed: Could not delete the directory \"" + dirName + "\"");
                return false;
            }

            try
            {
                ZipUtil.unzipToDirectory(studyFile, importDir);

                // when importing a folder archive, the study.xml file may not be at the root
                if (originalFilename.endsWith(".folder.zip"))
                {
                    File folderXml = new File(importDir, "folder.xml");
                    FolderDocument folderDoc;
                    try
                    {
                        folderDoc = FolderDocument.Factory.parse(folderXml, XmlBeansUtil.getDefaultParseOptions());
                        XmlBeansUtil.validateXmlDocument(folderDoc);
                    }
                    catch (Exception e)
                    {
                        throw new InvalidFileException(folderXml.getParentFile(), folderXml, e);
                    }

                    if (folderDoc.getFolder().isSetStudy())
                    {
                        importDir = new File(importDir, folderDoc.getFolder().getStudy().getDir());
                    }
                }
                studyXml = new File(importDir, "study.xml");
            }
            catch (FileNotFoundException e)
            {
                errors.reject("studyImport", "File not found.");
                return false;
            }
            catch (IOException e)
            {
                errors.reject("studyImport", "This file does not appear to be a valid .zip file.");
                return false;
            }
        }
        else
        {
            studyXml = studyFile;
        }

        User user = context.getUser();
        ActionURL url = context.getActionURL();
        try
        {
            PipelineService.get().queueJob(new StudyImportJob(c, user, url, studyXml, originalFilename, errors, pipelineRoot));
        }
        catch (PipelineValidationException e)
        {
            throw new IOException(e);
        }

        return !errors.hasErrors();
    }


    private static class PipelineSetupView extends JspView<String>
    {
        private PipelineSetupView(String actionDescription)
        {
            super("/org/labkey/study/view/pipelineSetup.jsp", actionDescription);
        }
    }


    @RequiresPermissionClass(DeletePermission.class)
    public class PurgeDatasetAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            ViewContext context = getViewContext();
            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));

            if ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
            {
                DataSetDefinition dataset = StudyManager.getInstance().getDataSetDefinition(getStudyRedirectIfNull(), datasetId);
                if (null == dataset)
                {
                    throw new NotFoundException();
                }

                String typeURI = dataset.getTypeURI();
                if (typeURI == null)
                {
                    return new ActionURL(TypeNotFoundAction.class, getContainer());
                }

                DbScope scope = StudySchema.getInstance().getSchema().getScope();
                try
                {
                    scope.ensureTransaction();
                    int numRowsDeleted = StudyManager.getInstance().purgeDataset(dataset, getUser());
                    scope.commitTransaction();

                    // Log the purge
                    String comment = "Dataset purged. " + numRowsDeleted + " rows deleted";
                    StudyServiceImpl.addDatasetAuditEvent(getUser(), getContainer(), dataset, comment, null);
                }
                finally
                {
                    scope.closeConnection();
                }
                DataRegionSelection.clearAll(getViewContext());
            }
            ActionURL datasetURL = new ActionURL(DatasetAction.class, getContainer());
            datasetURL.addParameter(DataSetDefinition.DATASETKEY, datasetId);
            return datasetURL;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class TypeNotFoundAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new StudyJspView<StudyImpl>(getStudyRedirectIfNull(), "typeNotFound.jsp", null, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Type Not Found");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateParticipantVisitsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            StudyManager.getInstance().getVisitManager(getStudyRedirectIfNull()).updateParticipantVisits(getUser(), getStudyRedirectIfNull().getDataSets());

            TableInfo tinfoParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            Integer visitDates = Table.executeSingleton(StudySchema.getInstance().getSchema(),
                    "SELECT Count(VisitDate) FROM " + tinfoParticipantVisit + "\nWHERE Container = ?",
                    new Object[] {getContainer()}, Integer.class);
            int count = null == visitDates ? 0 : visitDates.intValue();

            return new HtmlView(
                    "<div>" + count + " rows were updated.<p/>" +
                    PageFlowUtil.generateButton("Done", "manageVisits.view") +
                    "</div>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            return root.addChild("Recalculate Visit Dates");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class VisitOrderAction extends FormViewAction<VisitReorderForm>
    {

        public ModelAndView getView(VisitReorderForm reorderForm, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudyRedirectIfNull(), "visitOrder.jsp", reorderForm, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            return root.addChild("Visit Order");
        }

        public void validateCommand(VisitReorderForm target, Errors errors) {}

        private Map<Integer, Integer> getVisitIdToOrderIndex(String orderedIds)
        {
            Map<Integer, Integer> order = null;
            if (orderedIds != null && orderedIds.length() > 0)
            {
                order = new HashMap<>();
                String[] idArray = orderedIds.split(",");
                for (int i = 0; i < idArray.length; i++)
                {
                    int id = Integer.parseInt(idArray[i]);
                    // 1-index display orders, since 0 is the database default, and we'd like to know
                    // that these were set explicitly for all visits:
                    order.put(id, i + 1);
                }
            }
            return order;
        }

        private Map<Integer, Integer> getVisitIdToZeroMap(List<VisitImpl> visits) throws ServletException
        {
            Map<Integer, Integer> order = new HashMap<>();
            for (VisitImpl visit : visits)
                order.put(visit.getRowId(), 0);
            return order;
        }

        public boolean handlePost(VisitReorderForm form, BindException errors) throws Exception
        {
            Map<Integer, Integer> displayOrder = null;
            Map<Integer, Integer> chronologicalOrder = null;
            List<VisitImpl> visits = StudyManager.getInstance().getVisits(getStudyThrowIfNull(), Visit.Order.SEQUENCE_NUM);

            if (form.isExplicitDisplayOrder())
                displayOrder = getVisitIdToOrderIndex(form.getDisplayOrder());
            if (displayOrder == null)
                displayOrder = getVisitIdToZeroMap(visits);

            if (form.isExplicitChronologicalOrder())
                chronologicalOrder = getVisitIdToOrderIndex(form.getChronologicalOrder());
            if (chronologicalOrder == null)
                chronologicalOrder = getVisitIdToZeroMap(visits);

            for (VisitImpl visit : visits)
            {
                // it's possible that a new visit has been created between when the update page was rendered
                // and posted.  This will result in a visit that isn't in our ID maps.  There's no great way
                // to handle this, so we'll just skip setting display/chronological order on these visits for now.
                if (displayOrder.containsKey(visit.getRowId()) && chronologicalOrder.containsKey(visit.getRowId()))
                {
                    int displayIndex = displayOrder.get(visit.getRowId()).intValue();
                    int chronologicalIndex = chronologicalOrder.get(visit.getRowId()).intValue();

                    if (visit.getDisplayOrder() != displayIndex || visit.getChronologicalOrder() != chronologicalIndex)
                    {
                        visit = visit.createMutable();
                        visit.setDisplayOrder(displayIndex);
                        visit.setChronologicalOrder(chronologicalIndex);
                        StudyManager.getInstance().updateVisit(getUser(), visit);
                    }
                }
            }

            // Changing visit order can cause cohort assignments to change when advanced cohort tracking is enabled:
            if (getStudyThrowIfNull().isAdvancedCohorts())
                CohortManager.getInstance().updateParticipantCohorts(getUser(), getStudyThrowIfNull());
            return true;
        }

        public ActionURL getSuccessURL(VisitReorderForm reorderForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class VisitVisibilityAction extends FormViewAction<VisitPropertyForm>
    {
        public ModelAndView getView(VisitPropertyForm visitPropertyForm, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudyRedirectIfNull(), "visitVisibility.jsp", visitPropertyForm, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            return root.addChild("Properties");
        }

        public void validateCommand(VisitPropertyForm target, Errors errors) {}

        public boolean handlePost(VisitPropertyForm form, BindException errors) throws Exception
        {
            int[] allIds = form.getIds() == null ? new int[0] : form.getIds();
            int[] visibleIds = form.getVisible() == null ? new int[0] : form.getVisible();
            String[] labels = form.getLabel() == null ? new String[0] : form.getLabel();
            String[] typeStrs = form.getExtraData()== null ? new String[0] : form.getExtraData();

            Set<Integer> visible = new HashSet<>(visibleIds.length);
            for (int id : visibleIds)
                visible.add(id);
            if (allIds.length != form.getLabel().length)
                throw new IllegalStateException("Arrays must be the same length.");
            for (int i = 0; i < allIds.length; i++)
            {
                VisitImpl def = StudyManager.getInstance().getVisitForRowId(getStudyThrowIfNull(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String label = (i < labels.length) ? labels[i] : null;
                String typeStr = (i < typeStrs.length) ? typeStrs[i] : null;

                Integer cohortId = null;
                if (form.getCohort() != null && form.getCohort()[i] != -1)
                    cohortId = form.getCohort()[i];
                Character type = typeStr != null && typeStr.length() > 0 ? typeStr.charAt(0) : null;
                if (def.isShowByDefault() != show || !nullSafeEqual(label, def.getLabel()) || type != def.getTypeCode() || !nullSafeEqual(cohortId, def.getCohortId()))
                {
                    def = def.createMutable();
                    def.setShowByDefault(show);
                    def.setLabel(label);
                    def.setCohortId(cohortId);
                    def.setTypeCode(type);
                    StudyManager.getInstance().updateVisit(getUser(), def);
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(VisitPropertyForm visitPropertyForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DatasetVisibilityAction extends FormViewAction<DatasetPropertyForm>
    {
        public ModelAndView getView(DatasetPropertyForm form, boolean reshow, BindException errors) throws Exception
        {
            Map<Integer, DatasetVisibilityData> bean = new HashMap<>();
            for (DataSet def : getStudyRedirectIfNull().getDataSets())
            {
                DatasetVisibilityData data = new DatasetVisibilityData();
                data.label = def.getLabel();
                data.viewCategory = def.getViewCategory();
                data.cohort = def.getCohortId();
                data.visible = def.isShowByDefault();
                data.status = (String)ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "status");
                bean.put(def.getDataSetId(), data);
            }

            // Merge with form data
            int[] ids = form.getIds();
            if (ids != null)
            {
                String[] labels = form.getLabel();
                int[] visibleIds = form.getVisible();
                if (visibleIds == null)
                    visibleIds = new int[0];
                Set<Integer> visible = new HashSet<>(visibleIds.length);
                for (int id : visibleIds)
                    visible.add(id);
                int[] cohorts = form.getCohort();
                if (cohorts == null)
                    cohorts = new int[ids.length];
                String[] categories = form.getExtraData();

                for (int i=0; i<ids.length; i++)
                {
                    int id = ids[i];
                    DatasetVisibilityData data = bean.get(id);
                    data.label = labels != null ? labels[i] : null;
                    if (categories != null && categories[i] != null)
                    {
                        Integer categoryId = null;
                        if (NumberUtils.isDigits(categories[i]))
                        {
                            categoryId = NumberUtils.toInt(categories[i]);
                            data.viewCategory = ViewCategoryManager.getInstance().getCategory(categoryId);
                        }
                    }
                    data.cohort = cohorts[i] == -1 ? null : cohorts[i];
                    data.visible = visible.contains(id);
                }
            }
            return new StudyJspView<>(
                    getStudyRedirectIfNull(), "dataSetVisibility.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Datasets", new ActionURL(ManageTypesAction.class, getContainer()));
            root.addChild("Properties");
            return root;
        }

        public void validateCommand(DatasetPropertyForm target, Errors errors) {}

        public boolean handlePost(DatasetPropertyForm form, BindException errors) throws Exception
        {
            // Check for bad labels, including the case where all labels are cleared out:
            if (form.getIds() != null && form.getIds().length > 0 && form.getLabel() == null)
            {
                errors.reject("datasetVisibility", "Label cannot be blank");
                return false;
            }

            Set<String> labels = new HashSet<>();
            for (String label : form.getLabel())
            {
                if (label == null)
                {
                    errors.reject("datasetVisibility", "Label cannot be blank");
                    return false;
                }
                if (labels.contains(label))
                {
                    errors.reject("datasetVisibility", "Labels must be unique. Found two or more labels called '" + label + "'.");
                    return false;
                }
                labels.add(label);
            }

            int[] allIds = form.getIds();
            if (allIds == null)
                allIds = new int[0];
            int[] visibleIds = form.getVisible();
            if (visibleIds == null)
                visibleIds = new int[0];
            Set<Integer> visible = new HashSet<>(visibleIds.length);
            for (int id : visibleIds)
                  visible.add(id);
            String[] statuses = form.getStatuses();
            for (int i = 0; i < allIds.length; i++)
            {
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudyThrowIfNull(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String[] extraData = form.getExtraData();
                String category = extraData == null ? null : extraData[i];
                Integer categoryId = null;
                if (NumberUtils.isDigits(category))
                    categoryId = NumberUtils.toInt(category);

                Integer cohortId = null;
                if (form.getCohort() != null)
                    cohortId = form.getCohort()[i];
                if (cohortId != null && cohortId.intValue() == -1)
                    cohortId = null;
                String label = form.getLabel()[i];
                if (def.isShowByDefault() != show || !nullSafeEqual(categoryId, def.getCategoryId()) || !nullSafeEqual(label, def.getLabel()) || !BaseStudyController.nullSafeEqual(cohortId, def.getCohortId()))
                {
                    def = def.createMutable();
                    def.setShowByDefault(show);
                    def.setCategoryId(categoryId);
                    def.setCohortId(cohortId);
                    def.setLabel(label);
                    StudyManager.getInstance().updateDataSetDefinition(getUser(), def);
                }
                ReportPropsManager.get().setPropertyValue(def.getEntityId(), getContainer(), "status", statuses[i]);
            }

            return true;
        }

        public ActionURL getSuccessURL(DatasetPropertyForm form)
        {
            return new ActionURL(ManageTypesAction.class, getContainer());
        }
    }

    // Bean will be an map of these
    public static class DatasetVisibilityData
    {
        public String label;
        public Integer cohort; // null for none
        public String status;
        public boolean visible;
        public ViewCategory viewCategory;
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DatasetDisplayOrderAction extends FormViewAction<DatasetReorderForm>
    {
        public ModelAndView getView(DatasetReorderForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudyRedirectIfNull(), "dataSetDisplayOrder.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Datasets", new ActionURL(ManageTypesAction.class, getContainer()));
            root.addChild("Display Order");
            return root;
        }

        public void validateCommand(DatasetReorderForm target, Errors errors) {}

        public boolean handlePost(DatasetReorderForm form, BindException errors) throws Exception
        {
            String order = form.getOrder();

            if (order != null && order.length() > 0 && !form.isResetOrder())
            {
                String[] ids = order.split(",");
                List<Integer> orderedIds = new ArrayList<>(ids.length);

                for (String id : ids)
                    orderedIds.add(Integer.parseInt(id));

                DatasetReorderer reorderer = new DatasetReorderer(getStudyThrowIfNull(), getUser());
                reorderer.reorderDatasets(orderedIds);
            }
            else if (form.isResetOrder())
            {
                DatasetReorderer reorderer = new DatasetReorderer(getStudyThrowIfNull(), getUser());
                reorderer.resetOrder();
            }

            return true;
        }

        public ActionURL getSuccessURL(DatasetReorderForm visitPropertyForm)
        {
            return new ActionURL(ManageTypesAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteDatasetAction extends SimpleViewAction<IdForm>
    {
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudyRedirectIfNull(), form.getId());
            if (null == ds)
                redirectTypeNotFound(form.getId());

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try
            {
                scope.ensureTransaction();
                StudyManager.getInstance().deleteDataset(getStudyRedirectIfNull(), getUser(), ds, true);
                scope.commitTransaction();
                throw new RedirectException(new ActionURL(ManageTypesAction.class, getContainer()));
            }
            finally
            {
                scope.closeConnection();
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    private static final String DEFAULT_PARTICIPANT_VIEW_SOURCE =
            "<script type=\"text/javascript\">\n" +
            "   /* Include all headers necessary for client API usage: */\n" +
            "   LABKEY.requiresClientAPI();\n" +
            "</script>\n" +
            "\n" +
            "<div id=\"participantData\">Loading...</div>\n" +
            "\n" +
            "<script type=\"text/javascript\">\n" +
            "    /* get the participant id from the request URL: this parameter is required. */\n" +
            "    var participantId = LABKEY.ActionURL.getParameter('participantId');\n" +
            "    /* get the dataset id from the request URL: this is used to remember expand/collapse\n" +
            "       state per-dataset.  This parameter is optional; we use -1 if it isn't provided. */\n" +
            "    var datasetId = LABKEY.ActionURL.getParameter('datasetId');\n" +
            "    if (!datasetId)\n" +
            "        datasetId = -1;\n" +
            "    var dataType = 'ALL';\n" +
            "    /* Additional options for dataType 'DEMOGRAPHIC' or 'NON_DEMOGRAPHIC'. */" +
            "\n" +
            "    var QCState = LABKEY.ActionURL.getParameter('QCState');\n" +
            "\n" +
            "    /* create the participant details webpart: */\n" +
            "    var participantWebPart = new LABKEY.WebPart({\n" +
            "    partName: 'Participant Details',\n" +
            "    renderTo: 'participantData',\n" +
            "    frame : 'false',\n" +
            "    partConfig: {\n" +
            "        participantId: participantId,\n" +
            "        datasetId: datasetId,\n" +
            "        dataType: dataType,\n" +
            "        QCState: QCState,\n" +
            "        currentUrl: '' + window.location\n" +
            "        }\n" +
            "    });\n" +
            "\n" +
            "    /* place the webpart into the 'participantData' div: */\n" +
            "    participantWebPart.render();\n" +
            "</script>";

    public static class CustomizeParticipantViewForm
    {
        private String _returnUrl;
        private String _customScript;
        private String _participantId;
        private boolean _useCustomView;
        private boolean _reshow;
        private boolean _editable = true;

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(String returnUrl)
        {
            _returnUrl = returnUrl;
        }

        public boolean isEditable()
        {
            return _editable;
        }

        public void setEditable(boolean editable)
        {
            _editable = editable;
        }

        public String getCustomScript()
        {
            return _customScript;
        }

        public String getDefaultScript()
        {
            return DEFAULT_PARTICIPANT_VIEW_SOURCE;
        }

        public void setCustomScript(String customScript)
        {
            _customScript = customScript;
        }

        public String getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String participantId)
        {
            _participantId = participantId;
        }

        public boolean isReshow()
        {
            return _reshow;
        }

        public void setReshow(boolean reshow)
        {
            _reshow = reshow;
        }

        public boolean isUseCustomView()
        {
            return _useCustomView;
        }

        public void setUseCustomView(boolean useCustomView)
        {
            _useCustomView = useCustomView;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CustomizeParticipantViewAction extends FormViewAction<CustomizeParticipantViewForm>
    {
        public void validateCommand(CustomizeParticipantViewForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomizeParticipantViewForm form, boolean reshow, BindException errors) throws Exception
        {
            // We know that the user is at least a folder admin - they must also be either a developer
            if (!(getUser().isDeveloper()))
                throw new UnauthorizedException();
            Study study = getStudyRedirectIfNull();
            CustomParticipantView view = StudyManager.getInstance().getCustomParticipantView(study);
            if (view != null)
            {
                form.setCustomScript(view.getBody());
                form.setUseCustomView(view.isActive());
                form.setEditable(!view.isModuleParticipantView());
            }

            return new JspView<>("/org/labkey/study/view/customizeParticipantView.jsp", form);
        }

        public boolean handlePost(CustomizeParticipantViewForm form, BindException errors) throws Exception
        {
            Study study = getStudyThrowIfNull();
            CustomParticipantView view = StudyManager.getInstance().getCustomParticipantView(study);
            if (view == null)
                view = new CustomParticipantView();
            view.setBody(form.getCustomScript());
            view.setActive(form.isUseCustomView());
            view = StudyManager.getInstance().saveCustomParticipantView(study, getUser(), view);
            return view != null;
        }

        public ActionURL getSuccessURL(CustomizeParticipantViewForm form)
        {
            if (form.isReshow())
            {
                ActionURL reshowURL = new ActionURL(CustomizeParticipantViewAction.class, getContainer());
                if (form.getParticipantId() != null && form.getParticipantId().length() > 0)
                    reshowURL.addParameter("participantId", form.getParticipantId());
                if (form.getReturnUrl() != null && form.getReturnUrl().length() > 0)
                    reshowURL.addParameter(ActionURL.Param.returnUrl, form.getReturnUrl());
                return reshowURL;
            }
            else if (form.getReturnUrl() != null && form.getReturnUrl().length() > 0)
                return new ActionURL(form.getReturnUrl());
            else
                return new ActionURL(ReportsController.ManageReportsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Views", new ActionURL(ReportsController.ManageReportsAction.class, getContainer()));
            return root.addChild("Customize " + StudyService.get().getSubjectNounSingular(getContainer()) + " View");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class QuickCreateStudyAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm simpleApiJsonForm, BindException errors) throws Exception
        {
            JSONObject json = simpleApiJsonForm.getJsonObject();
            if (!json.has("name"))
                throw new IllegalArgumentException("name is a required attribute.");

            String folderName  = json.getString("name");
            String startDateStr;
            if (json.has("startDate"))
                startDateStr = json.getString("startDate");
            else
                startDateStr = DateUtil.formatDate();
            Date startDate = new Date(DateUtil.parseDateTime(startDateStr));

            String cohortDatasetName = json.getString("cohortDataset");
            String cohortProperty = json.getString("cohortProperty");
            if (null != cohortDatasetName && null == cohortProperty)
                throw new IllegalArgumentException("Specified cohort dataset, but not property");

            JSONArray visits = null;
            if (json.has("visits"))
                visits = json.getJSONArray("visits");

            JSONArray jsonDatasets = null;
            if (json.has("dataSets"))
            {
                boolean hasCohortDataset = false;
                jsonDatasets = json.getJSONArray("dataSets");
                for (JSONObject jdataset : jsonDatasets.toJSONObjectArray())
                {
                    if (!jdataset.has("name"))
                        throw new IllegalArgumentException("Dataset name required.");

                    if (jdataset.get("name").equals(cohortDatasetName))
                        hasCohortDataset = true;
                }

                if (null != cohortDatasetName && !hasCohortDataset)
                    throw new IllegalArgumentException("Couldn't find cohort dataset");
            }


            JSONArray jsonWebParts = null;
            if (json.has("webParts"))
                jsonWebParts = json.getJSONArray("webParts");


            Container parent = getContainer();
            Container studyFolder = parent.getChild(folderName);
            if (null == studyFolder)
                studyFolder = ContainerManager.createContainer(parent, folderName);
            if (null != StudyManager.getInstance().getStudy(studyFolder))
                throw new IllegalStateException("Study already exists in folder");

            SecurityManager.setInheritPermissions(studyFolder);
            studyFolder.setFolderType(ModuleLoader.getInstance().getFolderType(StudyFolderType.NAME), getUser());

            StudyImpl study = new StudyImpl(studyFolder, folderName + " Study");
            study.setTimepointType(TimepointType.DATE);
            study.setStartDate(startDate);
            study = StudyManager.getInstance().createStudy(getUser(), study);

            if (null != visits)
            {
                for (JSONObject obj : visits.toJSONObjectArray())
                {
                    VisitImpl visit = new VisitImpl(studyFolder, obj.getDouble("minDays"), obj.getDouble("maxDays"), obj.getString("label"), Visit.Type.REQUIRED_BY_TERMINATION);
                    StudyManager.getInstance().createVisit(study, getUser(), visit);
                }
            }

            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            List<DataSetDefinition> datasets = new ArrayList<>();

            if (null != jsonDatasets)
            {
                try
                {
                    scope.ensureTransaction();

                    for (JSONObject jdataset : jsonDatasets.toJSONObjectArray())
                    {
                        DataSetDefinition dataset = AssayPublishManager.getInstance().createAssayDataset(getUser(), study, jdataset.getString("name"),
                                jdataset.getString("keyPropertyName"),
                                jdataset.has("id") ? jdataset.getInt("id") : null,
                                jdataset.has("demographicData") && jdataset.getBoolean("demographicData"),
                                null);

                        if (jdataset.has("keyPropertyManaged") && jdataset.getBoolean("keyPropertyManaged"))
                        {
                            dataset = dataset.createMutable();
                            dataset.setKeyManagementType(DataSet.KeyManagementType.RowId);
                            StudyManager.getInstance().updateDataSetDefinition(getUser(), dataset);
                        }

                        if (dataset.getName().equals(cohortDatasetName))
                        {
                            study = study.createMutable();
                            study.setParticipantCohortDataSetId(dataset.getDataSetId());
                            study.setParticipantCohortProperty(cohortProperty);
                            StudyManager.getInstance().updateStudy(getUser(), study);
                        }

                        OntologyManager.ensureDomainDescriptor(dataset.getTypeURI(), dataset.getName(), study.getContainer());
                        datasets.add(dataset);
                    }
                    scope.commitTransaction();
                }
                finally
                {
                    scope.closeConnection();
                }
            }

            if (null != jsonWebParts)
            {
                List<Portal.WebPart> webParts = new ArrayList<>();
                for (JSONObject obj : jsonWebParts.toJSONObjectArray())
                {
                    WebPartFactory factory = Portal.getPortalPartCaseInsensitive(obj.getString("partName"));
                    if (null == factory)
                        continue; //Silently ignore
                    String location = obj.getString("location");
                    if (null == location || "body".equals(location))
                        location = HttpView.BODY;
                    JSONObject partConfig = null;
                    if (obj.has("partConfig"))
                        partConfig = obj.getJSONObject("partConfig");

                    Portal.WebPart part = factory.createWebPart();
                    part.setLocation(location);
                    if (null != partConfig)
                    {
                        for (Map.Entry<String,Object> entry : partConfig.entrySet())
                            part.setProperty(entry.getKey(), entry.getValue().toString());
                    }

                    webParts.add(part);
                }
                Portal.saveParts(studyFolder, webParts);
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("label", study.getLabel());
            response.put("containerId", study.getContainer().getId());
            response.put("containerPath", study.getContainer().getPath());
            response.putBeanList("dataSets", datasets, "name", "typeURI", "dataSetId");


            return response;
        }
    }

    public static class StudySnapshotForm extends QuerySnapshotForm
    {
        private int _snapshotDatasetId = -1;
        private String _action;

        public static final String EDIT_DATASET = "editDataset";
        public static final String CREATE_SNAPSHOT = "createSnapshot";
        public static final String CANCEL = "cancel";

        public int getSnapshotDatasetId()
        {
            return _snapshotDatasetId;
        }

        public void setSnapshotDatasetId(int snapshotDatasetId)
        {
            _snapshotDatasetId = snapshotDatasetId;
        }

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CreateSnapshotAction extends FormViewAction<StudySnapshotForm>
    {
        ActionURL _successURL;

        public void validateCommand(StudySnapshotForm form, Errors errors)
        {
            if (StudySnapshotForm.CANCEL.equals(form.getAction()))
                return;

            String name = StringUtils.trimToNull(form.getSnapshotName());

            if (name != null)
            {
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName(), name);
                if (def != null)
                {
                    errors.reject("snapshotQuery.error", "A Snapshot with the same name already exists");
                    return;
                }

                // check for a dataset with the same label/name unless it's one that we created
                DataSet dataset = StudyManager.getInstance().getDatasetDefinitionByQueryName(StudyManager.getInstance().getStudy(getContainer()), name);
                if (dataset != null)
                {
                    if (dataset.getDataSetId() != form.getSnapshotDatasetId())
                        errors.reject("snapshotQuery.error", "A Dataset with the same name/label already exists");
                }
            }
            else
                errors.reject("snapshotQuery.error", "The Query Snapshot name cannot be blank");
        }

        public ModelAndView getView(StudySnapshotForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow || errors.hasErrors())
            {
                ActionURL url = getViewContext().getActionURL();

                if (StringUtils.isEmpty(form.getSnapshotName()))
                    form.setSnapshotName(url.getParameter("ff_snapshotName"));
                form.setUpdateDelay(NumberUtils.toInt(url.getParameter("ff_updateDelay")));
                form.setSnapshotDatasetId(NumberUtils.toInt(url.getParameter("ff_snapshotDatasetId"), -1));

                return new JspView<QueryForm>("/org/labkey/study/view/createDatasetSnapshot.jsp", form, errors);
            }
            else if (StudySnapshotForm.EDIT_DATASET.equals(form.getAction()))
            {
                StudyImpl study = getStudyRedirectIfNull();
                DataSet dsDef = StudyManager.getInstance().getDataSetDefinitionByName(study, form.getSnapshotName());

                ActionURL url = getViewContext().cloneActionURL().replaceParameter("ff_snapshotName", form.getSnapshotName()).
                        replaceParameter("ff_updateDelay", String.valueOf(form.getUpdateDelay())).
                        replaceParameter("ff_snapshotDatasetId", String.valueOf(form.getSnapshotDatasetId()));

                if (dsDef == null)
                    throw new NotFoundException("Unable to edit the created DataSet Definition");

                Map<String,String> props = PageFlowUtil.map(
                        "studyId", String.valueOf(study.getRowId()),
                        "datasetId", String.valueOf(dsDef.getDataSetId()),
                        "typeURI", dsDef.getTypeURI(),
                        "timepointType", String.valueOf(study.getTimepointType()),
                        ActionURL.Param.returnUrl.name(), url.getLocalURIString(),
                        ActionURL.Param.cancelUrl.name(), url.getLocalURIString(),
                        "create", "false");

                HtmlView text = new HtmlView("Modify the properties and schema (form fields/properties) for this dataset.");
                HttpView view = new StudyGWTView(gwt.client.org.labkey.study.dataset.client.Designer.class, props);

                // hack for 4404 : Lookup picker performance is terrible when there are many containers
                ContainerManager.getAllChildren(ContainerManager.getRoot());

                return new VBox(text, view);
            }
            return null;
        }

        private void deletePreviousDatasetDefinition(StudySnapshotForm form) throws SQLException
        {
            if (form.getSnapshotDatasetId() != -1)
            {
                StudyImpl study = StudyManager.getInstance().getStudy(getContainer());

                // a dataset definition was edited previously, but under a different name, need to delete the old one
                DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, form.getSnapshotDatasetId());
                if (dsDef != null)
                {
                    StudyManager.getInstance().deleteDataset(study, getUser(), dsDef, true);
                    form.setSnapshotDatasetId(-1);
                }
            }
        }

        private void createDataset(StudySnapshotForm form, BindException errors) throws Exception
        {
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            DataSet dsDef = StudyManager.getInstance().getDataSetDefinitionByName(study, form.getSnapshotName());

            if (dsDef == null)
            {
                deletePreviousDatasetDefinition(form);

                // if this snapshot is being created from an existing dataset, copy key field settings
                int datasetId = NumberUtils.toInt(getViewContext().getActionURL().getParameter(DataSetDefinition.DATASETKEY), -1);
                String additionalKey = null;
                DataSetDefinition.KeyManagementType keyManagementType = DataSet.KeyManagementType.None;
                boolean isDemographicData = false;

                if (datasetId != -1)
                {
                    DataSetDefinition sourceDef = study.getDataSet(datasetId);
                    if (sourceDef != null)
                    {
                        additionalKey = sourceDef.getKeyPropertyName();
                        keyManagementType = sourceDef.getKeyManagementType();
                        isDemographicData = sourceDef.isDemographicData();
                    }
                }
                DataSetDefinition def = AssayPublishManager.getInstance().createAssayDataset(getUser(),
                        study, form.getSnapshotName(), additionalKey, null, isDemographicData, null);

                if (def != null)
                {
                    form.setSnapshotDatasetId(def.getDataSetId());
                    if (keyManagementType != DataSet.KeyManagementType.None)
                    {
                        def = def.createMutable();
                        def.setKeyManagementType(keyManagementType);

                        StudyManager.getInstance().updateDataSetDefinition(getUser(), def);
                    }

                    // NOTE getDisplayColumns() indirectly causes a query of the datasets,
                    // Do this before provisionTable() so we don't query the dataset we are about to create
                    // causes a problem on postgres (bug 11153)
                    List<DisplayColumn> displayColumns = QuerySnapshotService.get(form.getSchemaName()).getDisplayColumns(form, errors);

                    // def may not be provisioned yet, create before we start adding properties
                    def.provisionTable();
                    Domain d = def.getDomain();

                    for (DisplayColumn dc : displayColumns)
                    {
                        ColumnInfo col = dc.getColumnInfo();
                        if (col != null && !DataSetDefinition.isDefaultFieldName(col.getName(), study))
                            DatasetSnapshotProvider.addAsDomainProperty(d, col);
                    }
                    d.save(getUser());
                    //def.saveDomain(d, getUser());
                }
            }
        }

        public boolean handlePost(StudySnapshotForm form, BindException errors) throws Exception
        {
            DbSchema schema = StudySchema.getInstance().getSchema();

            try {
                schema.getScope().ensureTransaction();

                if (StudySnapshotForm.EDIT_DATASET.equals(form.getAction()))
                {
                    createDataset(form, errors);
                }
                else if (StudySnapshotForm.CREATE_SNAPSHOT.equals(form.getAction()))
                {
                    createDataset(form, errors);
                    if (!errors.hasErrors())
                        _successURL = QuerySnapshotService.get(form.getSchemaName()).createSnapshot(form, errors);
                }
                else if (StudySnapshotForm.CANCEL.equals(form.getAction()))
                {
                    deletePreviousDatasetDefinition(form);
                    String redirect = getViewContext().getActionURL().getParameter(ActionURL.Param.redirectUrl);
                    if (redirect != null)
                        _successURL = new ActionURL(PageFlowUtil.decode(redirect));
                }

                if (!errors.hasErrors())
                    schema.getScope().commitTransaction();
            }
            finally
            {
                schema.getScope().closeConnection();
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(StudySnapshotForm queryForm)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Query Snapshot");
        }
    }

    /**
     * Provides a view to update study query snapshots. Since query snapshots are implemented as datasets, the
     * dataset properties editor can be shown in this view.
     */
    @RequiresPermissionClass(AdminPermission.class)
    public class EditSnapshotAction extends FormViewAction<StudySnapshotForm>
    {
        ActionURL _successURL;

        public void validateCommand(StudySnapshotForm form, Errors errors)
        {
        }

        public ModelAndView getView(StudySnapshotForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setEdit(true);
            if (!reshow)
                form.init(QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName(), form.getSnapshotName()), getUser());

            VBox box = new VBox();
            QuerySnapshotService.I provider = QuerySnapshotService.get(form.getSchemaName());

            if (provider != null)
            {
                boolean showHistory = BooleanUtils.toBoolean(getViewContext().getActionURL().getParameter("showHistory"));
                boolean showDataset = BooleanUtils.toBoolean(getViewContext().getActionURL().getParameter("showDataset"));

                box.addView(new JspView<QueryForm>("/org/labkey/study/view/editSnapshot.jsp", form));
                box.addView(new JspView<QueryForm>("/org/labkey/study/view/createDatasetSnapshot.jsp", form, errors));

                if (showHistory)
                {
                    HttpView historyView = provider.createAuditView(form);
                    if (historyView != null)
                        box.addView(historyView);
                }

                if (showDataset)
                {
                    // create the GWT dataset designer
                    StudyImpl study = getStudyRedirectIfNull();
                    DataSet dsDef = StudyManager.getInstance().getDataSetDefinitionByName(study, form.getSnapshotName());

                    if (dsDef == null)
                        throw new NotFoundException("Unable to edit the created DataSet Definition");

                    ActionURL returnURL = getViewContext().cloneActionURL().replaceParameter("showDataset", "0");
                    Map<String,String> props = PageFlowUtil.map(
                            "studyId", String.valueOf(study.getRowId()),
                            "datasetId", String.valueOf(dsDef.getDataSetId()),
                            "typeURI", dsDef.getTypeURI(),
                            "timepointType", String.valueOf(study.getTimepointType()), // XXX: should always be "VISIT" ?
                            ActionURL.Param.returnUrl.name(), returnURL.toString(),
                            ActionURL.Param.cancelUrl.name(), returnURL.toString(),
                            "create", "false");

                    HtmlView text = new HtmlView("Modify the properties and schema (form fields/properties) for this dataset.<br>Click the save button to " +
                            "save any changes, else click the cancel button to complete the snapshot.");
                    HttpView view = new StudyGWTView(new StudyApplication.DatasetDesigner(), props);

                    // hack for 4404 : Lookup picker performance is terrible when there are many containers
                    ContainerManager.getAllChildren(ContainerManager.getRoot());

                    box.addView(text);
                    box.addView(view);
                }
            }
            return box;
        }

        public boolean handlePost(StudySnapshotForm form, BindException errors) throws Exception
        {
            if (StudySnapshotForm.CANCEL.equals(form.getAction()))
            {
                String redirect = getViewContext().getActionURL().getParameter(ActionURL.Param.redirectUrl);
                if (redirect != null)
                    _successURL = new ActionURL(PageFlowUtil.decode(redirect));
            }
            else if (form.isUpdateSnapshot())
            {
                _successURL = QuerySnapshotService.get(form.getSchemaName()).updateSnapshot(form, errors);

                if (errors.hasErrors())
                    return false;
            }
            else
            {
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName(), form.getSnapshotName());
                if (def != null)
                {
                    def.setUpdateDelay(form.getUpdateDelay());
                    _successURL = QuerySnapshotService.get(form.getSchemaName()).updateSnapshotDefinition(getViewContext(), def, errors);
                    if (errors.hasErrors())
                        return false;
                }
                else
                {
                    errors.reject("snapshotQuery.error", "Unable to create QuerySnapshotDefinition");
                    return false;
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(StudySnapshotForm form)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Edit Query Snapshot");
        }
    }

    public static class DatasetPropertyForm extends PropertyForm
    {
        private int[] _ids;
        private int[] _visible;
        private String[] _statuses;

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public int[] getVisible()
        {
            return _visible;
        }

        public void setVisible(int[] visible)
        {
            _visible = visible;
        }

        public String[] getStatuses()
        {
            return _statuses;
        }

        public void setStatuses(String[] statuses)
        {
            _statuses = statuses;
        }
    }

    public static class RequirePipelineView extends StudyJspView<Boolean>
    {
        public RequirePipelineView(StudyImpl study, boolean showGoBack, BindException errors)
        {
            super(study, "requirePipeline.jsp", showGoBack, errors);
        }
    }

    public static class VisitPropertyForm extends PropertyForm
    {
        private int[] _ids;
        private int[] _visible;

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public int[] getVisible()
        {
            return _visible;
        }

        public void setVisible(int[] visible)
        {
            _visible = visible;
        }
    }

    public abstract static class PropertyForm
    {
        private String[] _label;
        private String[] _extraData;
        private int[] _cohort;

        public String[] getExtraData()
        {
            return _extraData;
        }

        public void setExtraData(String[] extraData)
        {
            _extraData = extraData;
        }

        public String[] getLabel()
        {
            return _label;
        }

        public void setLabel(String[] label)
        {
            _label = label;
        }

        public int[] getCohort()
        {
            return _cohort;
        }

        public void setCohort(int[] cohort)
        {
            _cohort = cohort;
        }
    }


    public static class DatasetReorderForm
    {
        private String order;
        private boolean resetOrder = false;

        public String getOrder() {return order;}

        public void setOrder(String order) {this.order = order;}

        public boolean isResetOrder()
        {
            return resetOrder;
        }

        public void setResetOrder(boolean resetOrder)
        {
            this.resetOrder = resetOrder;
        }
    }

    public static class VisitReorderForm
    {
        private boolean _explicitDisplayOrder;
        private boolean _explicitChronologicalOrder;
        private String _displayOrder;
        private String _chronologicalOrder;

        public String getDisplayOrder()
        {
            return _displayOrder;
        }

        public void setDisplayOrder(String displayOrder)
        {
            _displayOrder = displayOrder;
        }

        public String getChronologicalOrder()
        {
            return _chronologicalOrder;
        }

        public void setChronologicalOrder(String chronologicalOrder)
        {
            _chronologicalOrder = chronologicalOrder;
        }

        public boolean isExplicitDisplayOrder()
        {
            return _explicitDisplayOrder;
        }

        public void setExplicitDisplayOrder(boolean explicitDisplayOrder)
        {
            _explicitDisplayOrder = explicitDisplayOrder;
        }

        public boolean isExplicitChronologicalOrder()
        {
            return _explicitChronologicalOrder;
        }

        public void setExplicitChronologicalOrder(boolean explicitChronologicalOrder)
        {
            _explicitChronologicalOrder = explicitChronologicalOrder;
        }
    }

    public static class ImportStudyBatchBean
    {
        private final DatasetFileReader reader;
        private final String path;

        public ImportStudyBatchBean(DatasetFileReader reader, String path)
        {
            this.reader = reader;
            this.path = path;
        }

        public DatasetFileReader getReader()
        {
            return reader;
        }

        public String getPath()
        {
            return path;
        }
    }

    public static class ViewPrefsBean
    {
        private List<Pair<String, String>> _views;
        private DataSet _def;

        public ViewPrefsBean(List<Pair<String, String>> views, DataSet def)
        {
            _views = views;
            _def = def;
        }

        public List<Pair<String, String>> getViews(){return _views;}
        public DataSet getDataSetDefinition(){return _def;}
    }


    private static final String DEFAULT_DATASET_VIEW = "Study.defaultDatasetView";

    public static String getDefaultView(ViewContext context, int datasetId)
    {
        Map<String, String> viewMap = PropertyManager.getProperties(context.getUser(),
                context.getContainer(), DEFAULT_DATASET_VIEW);

        final String key = Integer.toString(datasetId);
        if (viewMap.containsKey(key))
        {
            return viewMap.get(key);
        }
        return "";
    }

    private void setDefaultView(ViewContext context, int datasetId, String view)
    {
        Map<String, String> viewMap = PropertyManager.getWritableProperties(context.getUser(),
                context.getContainer(), DEFAULT_DATASET_VIEW, true);

        viewMap.put(Integer.toString(datasetId), view);
        PropertyManager.saveProperties(viewMap);
    }

    private String getVisitLabel()
    {
        try
        {
            return StudyManager.getInstance().getVisitManager(getStudyRedirectIfNull()).getLabel();
        }
        catch (ServletException e)
        {
            return "Visit";
        }
    }


    private String getVisitLabelPlural()
    {
        try
        {
            return StudyManager.getInstance().getVisitManager(getStudyRedirectIfNull()).getPluralLabel();
        }
        catch (ServletException e)
        {
            return "Visits";
        }
    }

    private String getVisitJsp(String prefix, StudyImpl study) throws ServletException
    {
        assert study.getTimepointType() != TimepointType.CONTINUOUS;
        return prefix + (study.getTimepointType() == TimepointType.DATE ? "Timepoint" : "Visit") + ".jsp";
    }

    public static class ParticipantForm extends ViewForm implements StudyManager.ParticipantViewConfig
    {
        private String participantId;
        private int datasetId;
        private double sequenceNum;
        private String action;
        private int reportId;
        private String _qcState;
        private String _redirectUrl;
        private Map<String, String> aliases;

        public String getParticipantId(){return participantId;}

        public void setParticipantId(String participantId)
        {
            this.participantId = participantId;
            aliases = StudyManager.getInstance().getAliasMap(StudyManager.getInstance().getStudy(getContainer()), getUser(), participantId);
        }

        public Map<String, String> getAliases()
        {
            return aliases;
        }

        public int getDatasetId(){return datasetId;}
        public void setDatasetId(int datasetId){this.datasetId = datasetId;}

        public double getSequenceNum(){return sequenceNum;}
        public void setSequenceNum(double sequenceNum){this.sequenceNum = sequenceNum;}

        public String getAction(){return action;}
        public void setAction(String action){this.action = action;}

        public int getReportId(){return reportId;}
        public void setReportId(int reportId){this.reportId = reportId;}

        public String getRedirectUrl() { return _redirectUrl; }

        public QCStateSet getQCStateSet()
        {
            if (_qcState != null && StudyManager.getInstance().showQCStates(getContainer()))
                return QCStateSet.getSelectedStates(getContainer(), getQCState());
            return null;
        }

        public void setRedirectUrl(String redirectUrl) { _redirectUrl = redirectUrl; }

        public String getQCState() { return _qcState; }

        public void setQCState(String qcState) { _qcState = qcState; }
    }


    public static class StudyPropertiesForm
    {
        private String _label;
        private TimepointType _timepointType;
        private Date _startDate;
        private boolean _simpleRepository = true;
        private SecurityType _securityType;
        private String _subjectNounSingular = "Participant";
        private String _subjectNounPlural = "Participants";
        private String _subjectColumnName = "ParticipantId";
        private String _returnURL;
        private String _description;
        private String _descriptionRendererType;
        private String _grant;
        private String _investigator;
        private int _defaultTimepointDuration = 0;
        private String _alternateIdPrefix;
        private int _alternateIdDigits;
        private boolean _allowReqLocRepository = true;
        private boolean _allowReqLocClinic = true;
        private boolean _allowReqLocSal = true;
        private boolean _allowReqLocEndpoint = true;

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
        {
            _returnURL = returnURL;
        }

        public TimepointType getTimepointType()
        {
            return _timepointType;
        }

        public void setTimepointType(TimepointType timepointType)
        {
            _timepointType = timepointType;
        }

        public Date getStartDate()
        {
            return _startDate;
        }

        public void setStartDate(Date startDate)
        {
            _startDate = startDate;
        }

        public boolean isSimpleRepository()
        {
            return _simpleRepository;
        }

        public void setSimpleRepository(boolean simpleRepository)
        {
            _simpleRepository = simpleRepository;
        }

        public void setSecurityString(String security)
        {
            _securityType = SecurityType.valueOf(security);
        }

        public String getSecurityString()
        {
            return _securityType == null ? null : _securityType.name();
        }

        public void setSecurityType(SecurityType securityType)
        {
            _securityType = securityType;
        }

        public SecurityType getSecurityType()
        {
            return _securityType;
        }

        public String getSubjectNounSingular()
        {
            return _subjectNounSingular;
        }

        public void setSubjectNounSingular(String subjectNounSingular)
        {
            _subjectNounSingular = subjectNounSingular;
        }

        public String getSubjectNounPlural()
        {
            return _subjectNounPlural;
        }

        public void setSubjectNounPlural(String subjectNounPlural)
        {
            _subjectNounPlural = subjectNounPlural;
        }

        public String getSubjectColumnName()
        {
            return _subjectColumnName;
        }

        public void setSubjectColumnName(String subjectColumnName)
        {
            _subjectColumnName = subjectColumnName;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getDescriptionRendererType()
        {
            return _descriptionRendererType;
        }

        public void setDescriptionRendererType(String descriptionRendererType)
        {
            _descriptionRendererType = descriptionRendererType;
        }

        public String getInvestigator()
        {
            return _investigator;
        }

        public void setInvestigator(String investigator)
        {
            _investigator = investigator;
        }

        public String getGrant()
        {
            return _grant;
        }

        public void setGrant(String grant)
        {
            _grant = grant;
        }

        public int getDefaultTimepointDuration()
        {
            return _defaultTimepointDuration;
        }

        public void setDefaultTimepointDuration(int defaultTimepointDuration)
        {
            _defaultTimepointDuration = defaultTimepointDuration;
        }

        public String getAlternateIdPrefix()
        {
            return _alternateIdPrefix;
        }

        public void setAlternateIdPrefix(String alternateIdPrefix)
        {
            _alternateIdPrefix = alternateIdPrefix;
        }

        public int getAlternateIdDigits()
        {
            return _alternateIdDigits;
        }

        public void setAlternateIdDigits(int alternateIdDigits)
        {
            _alternateIdDigits = alternateIdDigits;
        }

        public boolean isAllowReqLocRepository()
        {
            return _allowReqLocRepository;
        }

        public void setAllowReqLocRepository(boolean allowReqLocRepository)
        {
            _allowReqLocRepository = allowReqLocRepository;
        }

        public boolean isAllowReqLocClinic()
        {
            return _allowReqLocClinic;
        }

        public void setAllowReqLocClinic(boolean allowReqLocClinic)
        {
            _allowReqLocClinic = allowReqLocClinic;
        }

        public boolean isAllowReqLocSal()
        {
            return _allowReqLocSal;
        }

        public void setAllowReqLocSal(boolean allowReqLocSal)
        {
            _allowReqLocSal = allowReqLocSal;
        }

        public boolean isAllowReqLocEndpoint()
        {
            return _allowReqLocEndpoint;
        }

        public void setAllowReqLocEndpoint(boolean allowReqLocEndpoint)
        {
            _allowReqLocEndpoint = allowReqLocEndpoint;
        }
    }

    public static class IdForm
    {
        private int _id;

        public int getId() {return _id;}

        public void setId(int id) {_id = id;}
    }

    public static class SourceLsidForm
    {
        private String _sourceLsid;

        public String getSourceLsid() {return _sourceLsid;}

        public void setSourceLsid(String sourceLsid) {_sourceLsid = sourceLsid;}
    }

    public static class ReportHeader extends HttpView
    {
        private Report _report;

        public ReportHeader(Report report)
        {
            _report = report;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (!StringUtils.isEmpty(_report.getDescriptor().getReportDescription()))
            {
                out.print("<table>");
                out.print("<tr><td><span class='navPageHeader'>Report Description:</span>&nbsp;</td>");
                out.print("<td>" + _report.getDescriptor().getReportDescription() + "</td></tr>");
                out.print("</table>");
            }
        }
    }

    public static class StudyChartReport extends ChartQueryReport
    {
        public static final String TYPE = "Study.chartReport";

        public String getType()
        {
            return TYPE;
        }

        private TableInfo getTable(ViewContext context, ReportDescriptor descriptor) throws Exception
        {
            final int datasetId = Integer.parseInt(descriptor.getProperty(DataSetDefinition.DATASETKEY));
            final Study study = StudyManager.getInstance().getStudy(context.getContainer());
            DataSet def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

            return def.getTableInfo(context.getUser());
        }

        public Results generateResults(ViewContext context) throws Exception
        {
            ReportDescriptor descriptor = getDescriptor();
            final String participantId = descriptor.getProperty("participantId");
            final TableInfo tableInfo = getTable(context, descriptor);
            DataRegion dr = new DataRegion();
            dr.setTable(tableInfo);

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(StudyService.get().getSubjectColumnName(context.getContainer()), participantId, CompareType.EQUAL);

            RenderContext ctx = new RenderContext(context);
            ctx.setContainer(context.getContainer());
            ctx.setBaseFilter(filter);

            if (null == dr.getResultSet(ctx))
                return null;
            return new ResultsImpl(ctx);
        }

        public ChartReportDescriptor.LegendItemLabelGenerator getLegendItemLabelGenerator()
        {
            return new ChartReportDescriptor.LegendItemLabelGenerator() {
                public String generateLabel(ViewContext context, ReportDescriptor descriptor, String itemName) throws Exception
                {
                    TableInfo table = getTable(context, descriptor);
                    if (table != null)
                    {
                        ColumnInfo info = table.getColumn(itemName);
                        return info != null ? info.getLabel() : itemName;
                    }
                    return itemName;
                }
            };
        }
    }

    /**
     * Adds next and prev buttons to the participant view
     */
    public static class ParticipantNavView extends HttpView
    {
        private ActionURL _prevURL;
        private ActionURL _nextURL;
        private String _display;
        private String _currentParticipantId;
        private String _encodedQcState;
        private boolean _showCustomizeLink = true;

        public ParticipantNavView(ActionURL prevURL, ActionURL nextURL, String currentPartitipantId, String encodedQCState, String display)
        {
            _prevURL = prevURL;
            _nextURL = nextURL;
            _display = display;
            _currentParticipantId = currentPartitipantId;
            _encodedQcState = encodedQCState;
        }

        public ParticipantNavView(ActionURL prevURL, ActionURL nextURL, String currentPartitipantId, String encodedQCState)
        {
            this(prevURL, nextURL, currentPartitipantId,  encodedQCState, null);
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            Container c = getViewContext().getContainer();
            User user = getViewContext().getUser();
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            String subjectNoun = PageFlowUtil.filter(StudyService.get().getSubjectNounSingular(getViewContext().getContainer()));
            out.print("<table><tr><td align=\"left\">");
            if (_prevURL != null)
            {
                out.print(PageFlowUtil.textLink("Previous " + subjectNoun, _prevURL));
                out.print("&nbsp;");
            }

            if (_nextURL != null)
            {
                out.print(PageFlowUtil.textLink("Next " + subjectNoun, _nextURL));
                out.print("&nbsp;");
            }

            if (null != _currentParticipantId && null != ss)
            {
                ActionURL search = PageFlowUtil.urlProvider(SearchUrls.class).getSearchURL(c, "+" + ss.escapeTerm(_currentParticipantId));
                out.print(PageFlowUtil.textLink("Search for '" + PageFlowUtil.filter(id(_currentParticipantId, c, user)) + "'", search));
                out.print("&nbsp;");
            }

            // Show customize link to site admins (who are always developers) and folder admins who are developers:
            if (_showCustomizeLink && (c.hasPermission(getViewContext().getUser(), AdminPermission.class) && getViewContext().getUser().isDeveloper()))
            {
                ActionURL customizeURL = new ActionURL(CustomizeParticipantViewAction.class, c);
                customizeURL.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString());
                customizeURL.addParameter("participantId", _currentParticipantId);
                customizeURL.addParameter(SharedFormParameters.QCState, _encodedQcState);
                out.print("</td><td>");
                out.print(PageFlowUtil.textLink("Customize View", customizeURL));
            }

            if (_display != null)
            {
                out.print("</td><td class=\"labkey-form-label\">");
                out.print(PageFlowUtil.filter(_display));
            }
            out.print("</td></tr></table>");
        }

        public void setShowCustomizeLink(boolean showCustomizeLink)
        {
            _showCustomizeLink = showCustomizeLink;
        }
    }

    public static class ImportDataSetForm
    {
        private int datasetId = 0;
        private String typeURI;
        private String tsv;
        private String keys;


        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

        public String getKeys()
        {
            return keys;
        }

        public void setKeys(String keys)
        {
            this.keys = keys;
        }

        public String getTypeURI()
        {
            return typeURI;
        }

        public void setTypeURI(String typeURI)
        {
            this.typeURI = typeURI;
        }
    }

    public static class DatasetForm
    {
        private String _name;
        private String _label;
        private int _datasetId;
        private String _category;
        private boolean _showByDefault;
        private String _visitDatePropertyName;
        private String[] _visitStatus;
        private int[] _visitRowIds;
        private String _description;
        private Integer _cohortId;
        private boolean _demographicData;
        private boolean _create;

        public boolean isShowByDefault()
        {
            return _showByDefault;
        }

        public void setShowByDefault(boolean showByDefault)
        {
            _showByDefault = showByDefault;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }

        public String getDatasetIdStr()
        {
            return _datasetId > 0 ? String.valueOf(_datasetId) : "";
        }

        /**
         * Don't blow up when posting bad value
         * @param dataSetIdStr
         */
        public void setDatasetIdStr(String dataSetIdStr)
        {
            try
            {
                if (null == StringUtils.trimToNull(dataSetIdStr))
                    _datasetId = 0;
                else
                    _datasetId = Integer.parseInt(dataSetIdStr);
            }
            catch (Exception x)
            {
                _datasetId = 0;
            }
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String[] getVisitStatus()
        {
            return _visitStatus;
        }

        public void setVisitStatus(String[] visitStatus)
        {
            _visitStatus = visitStatus;
        }

        public int[] getVisitRowIds()
        {
            return _visitRowIds;
        }

        public void setVisitRowIds(int[] visitIds)
        {
            _visitRowIds = visitIds;
        }

        public String getVisitDatePropertyName()
        {
            return _visitDatePropertyName;
        }

        public void setVisitDatePropertyName(String _visitDatePropertyName)
        {
            this._visitDatePropertyName = _visitDatePropertyName;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public boolean isDemographicData()
        {
            return _demographicData;
        }

        public void setDemographicData(boolean demographicData)
        {
            _demographicData = demographicData;
        }

        public boolean isCreate()
        {
            return _create;
        }

        public void setCreate(boolean create)
        {
            _create = create;
        }

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DatasetsAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return StudyModule.datasetsPartFactory.getWebPartView(getViewContext(), StudyModule.datasetsPartFactory.createWebPart());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrail(root);
            root.addChild("Datasets");
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SamplesAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return StudyModule.samplesWidePartFactory.getWebPartView(getViewContext(), StudyModule.samplesWidePartFactory.createWebPart());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            Study study = getStudy();
            if (study != null)
                root.addChild(study.getLabel(), new ActionURL(BeginAction.class, getContainer()));
            root.addChild("Samples");
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ViewDataAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new VBox(
                StudyModule.reportsWidePartFactory.getWebPartView(getViewContext(), StudyModule.reportsPartFactory.createWebPart()),
                StudyModule.datasetsPartFactory.getWebPartView(getViewContext(), StudyModule.datasetsPartFactory.createWebPart())
            );
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageReloadAction extends FormViewAction<ReloadForm>
    {
        public void validateCommand(ReloadForm target, Errors errors)
        {
        }

        public ModelAndView getView(ReloadForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "manageReload.jsp", form, errors);
        }

        public boolean handlePost(ReloadForm form, final BindException errors) throws Exception
        {
            StudyImpl study = getStudyThrowIfNull();

            // If the "allow reload" state or the interval changes then update the study and initialize the timer
            if (form.isAllowReload() != study.isAllowReload() || !nullSafeEqual(form.getInterval(), study.getReloadInterval()))
            {
                study = study.createMutable();
                study.setAllowReload(form.isAllowReload());
                study.setReloadInterval(0 != form.getInterval() ? form.getInterval() : null);
                study.setReloadUser(getUser().getUserId());
                study.setLastReload(new Date());
                StudyManager.getInstance().updateStudy(getUser(), study);
                StudyReload.initializeTimer(study);
            }

            return true;
        }

        public ActionURL getSuccessURL(ReloadForm reloadForm)
        {
            return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage Reloading");
        }
    }


    public static class ReloadForm
    {
        private boolean allowReload = false;
        private int interval = 0;
        private boolean _ui = false;

        public boolean isAllowReload()
        {
            return allowReload;
        }

        public void setAllowReload(boolean allowReload)
        {
            this.allowReload = allowReload;
        }

        public int getInterval()
        {
            return interval;
        }

        public void setInterval(int interval)
        {
            this.interval = interval;
        }

        public boolean isUi()
        {
            return _ui;
        }

        public void setUi(boolean ui)
        {
            _ui = ui;
        }
    }

    private static class DatasetDetailRedirectForm extends ReturnUrlForm
    {
        private String _datasetId;
        private String _lsid;

        public String getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(String datasetId)
        {
            _datasetId = datasetId;
        }

        public String getLsid()
        {
            return _lsid;
        }

        public void setLsid(String lsid)
        {
            _lsid = lsid;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DatasetDetailRedirectAction extends RedirectAction<DatasetDetailRedirectForm>
    {
        private ActionURL _url;

        @Override
        public URLHelper getSuccessURL(DatasetDetailRedirectForm form)
        {
            return _url;
        }

        @Override
        public boolean doAction(DatasetDetailRedirectForm datasetDetailRedirectForm, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public void validateCommand(DatasetDetailRedirectForm form, Errors errors)
        {
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study == null)
            {
                throw new NotFoundException("No study found");
            }
            // First try the dataset id as an entityid
            DataSetDefinition dataset = StudyManager.getInstance().getDataSetDefinitionByEntityId(study, form.getDatasetId());
            if (dataset == null)
            {
                try
                {
                    // Then try the dataset id as an integer
                    int id = Integer.parseInt(form.getDatasetId());
                    dataset = StudyManager.getInstance().getDataSetDefinition(study, id);
                }
                catch (NumberFormatException e) {}

                if (dataset == null)
                {
                    throw new NotFoundException("Could not find dataset " + form.getDatasetId());
                }
            }

            if (form.getLsid() == null)
            {
                throw new NotFoundException("No LSID specified");
            }

            StudyQuerySchema schema = new StudyQuerySchema(study, getUser(), true);

            QueryDefinition queryDef = QueryService.get().createQueryDefForTable(schema, dataset.getName());
            assert queryDef != null : "Dataset was found but couldn't get a corresponding TableInfo";
            _url = queryDef.urlFor(QueryAction.detailsQueryRow, getContainer(), Collections.singletonMap("lsid", (Object)form.getLsid()));
            String referrer = getViewContext().getRequest().getHeader("Referer");
            if (referrer != null)
            {
                _url.addParameter(ActionURL.Param.returnUrl, referrer);
            }
        }
    }

    @RequiresNoPermission
    public class CheckForReload extends ManageReloadAction    // Subclassing makes it easier to redisplay errors, etc.
    {
        @Override
        public ModelAndView getView(ReloadForm form, boolean reshow, BindException errors) throws Exception
        {
            ReloadTask task = new ReloadTask();
            String message;

            try
            {
                ReloadStatus status = task.attemptReload(getContainer().getId());

                if (status.isReloadQueued() && form.isUi())
                    return HttpView.redirect(PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer()));

                message = status.getMessage();
            }
            catch (ImportException e)
            {
                message = "Error: " + e.getMessage();
            }

            // If this was initiated from the UI and reload was not queued up then reshow the form and display the message
            if (form.isUi())
            {
                errors.reject(ERROR_MSG, message);
                return super.getView(form, false, errors);
            }
            else
            {
                // Plain text response for scripts
                HttpServletResponse response = getViewContext().getResponse();
                response.setContentType("text/plain");
                PrintWriter out = response.getWriter();
                out.print(message);
                out.close();
                response.flushBuffer();

                return null;
            }
        }
    }



    public static class TSVForm
    {
        private String _content;

        public String getContent()
        {
            return _content;
        }

        public void setContent(String content)
        {
            _content = content;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class DemoModeAction extends FormViewAction<DemoModeForm>
    {
        @Override
        public URLHelper getSuccessURL(DemoModeForm form)
        {
            return null;
        }

        @Override
        public void validateCommand(DemoModeForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(DemoModeForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/study/view/demoMode.jsp");
        }

        @Override
        public boolean handlePost(DemoModeForm form, BindException errors) throws Exception
        {
            DemoMode.setDemoMode(getContainer(), getUser(), form.getMode());
            return false;  // Reshow page
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Demo Mode");
        }
    }


    public static class DemoModeForm
    {
        private boolean mode;

        public boolean getMode()
        {
            return mode;
        }

        public void setMode(boolean mode)
        {
            this.mode = mode;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ShowVisitImportMappingAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/study/view/visitImportMapping.jsp", new ImportMappingBean(getStudyRedirectIfNull()));
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            return root.addChild("Visit Import Mapping");
        }
    }


    public static class ImportMappingBean
    {
        private final Collection<StudyManager.VisitAlias> _customMapping;
        private final Collection<StudyManager.VisitAlias> _standardMapping;

        public ImportMappingBean(Study study)
        {
            _customMapping = StudyManager.getInstance().getCustomVisitImportMapping(study);
            _standardMapping = StudyManager.getInstance().getStandardVisitImportMapping(study);
        }

        public Collection<StudyManager.VisitAlias> getCustomMapping()
        {
            return _customMapping;
        }

        public Collection<StudyManager.VisitAlias> getStandardMapping()
        {
            return _standardMapping;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ImportVisitAliasesAction extends FormViewAction<VisitAliasesForm>
    {
        @Override
        public URLHelper getSuccessURL(VisitAliasesForm form)
        {
            return new ActionURL(ShowVisitImportMappingAction.class, getContainer());
        }

        @Override
        public void validateCommand(VisitAliasesForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(VisitAliasesForm form, boolean reshow, BindException errors) throws Exception
        {
            getPageConfig().setFocusId("tsv");
            return new JspView<>("/org/labkey/study/view/importVisitAliases.jsp", null, errors);
        }

        @Override
        public boolean handlePost(VisitAliasesForm form, BindException errors) throws Exception
        {
            boolean hadCustomMapping = !StudyManager.getInstance().getCustomVisitImportMapping(getStudyThrowIfNull()).isEmpty();

            try
            {
                String tsv = form.getTsv();

                if (null == tsv)
                {
                    errors.reject(ERROR_MSG, "Please insert tab-separated data with two columns, Name and SequenceNum");
                    return false;
                }

                StudyManager.getInstance().importVisitAliases(getStudyThrowIfNull(), getUser(), new TabLoader(form.getTsv(), true));
            }
            catch (SQLException e)
            {
                if (SqlDialect.isConstraintException(e))
                {
                    errors.reject(ERROR_MSG, "The visit import mapping includes duplicate visit names: " + e.getMessage());
                    return false;
                }
                else
                {
                    throw e;
                }
            }
            catch (ValidationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            // TODO: Change to audit log
            _log.info("The visit import custom mapping was " + (hadCustomMapping ? "replaced" : "imported"));

            return true;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            return root.addChild("Import Visit Aliases");
        }
    }


    public static class VisitAliasesForm
    {
        private String _tsv;

        public String getTsv()
        {
            return _tsv;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setTsv(String tsv)
        {
            _tsv = tsv;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ClearVisitAliasesAction extends ConfirmAction
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
        {
            return new HtmlView("Are you sure you want to delete the visit import custom mapping for this study?");
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            StudyManager.getInstance().clearVisitAliases(getStudyThrowIfNull());
            // TODO: Change to audit log
            _log.info("The visit import custom mapping was cleared");

            return true;
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return new ActionURL(ShowVisitImportMappingAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(ReadPermission.class) @RequiresLogin
    public class ManageParticipantCategoriesAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/study/view/manageParticipantCategories.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                _appendManageStudy(root);
                root.addChild("Manage " + getStudyRedirectIfNull().getSubjectNounSingular() + " Groups");
            }
            catch (ServletException e)
            {
            }
            return root;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageAlternateIdsAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            ChangeAlternateIdsForm changeAlternateIdsForm = getChangeAlternateIdForm(getStudyRedirectIfNull());
            return new JspView<>("/org/labkey/study/view/manageAlternateIds.jsp", changeAlternateIdsForm);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                _appendManageStudy(root);
                String subjectNoun = getStudyRedirectIfNull().getSubjectNounSingular();
                root.addChild("Manage Alternate " + subjectNoun + " IDs and " + subjectNoun + " Aliases");
            }
            catch (ServletException e)
            {
            }
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SubjectListAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new SubjectsWebPart(true, 0);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseDataAction extends ApiAction<BrowseDataForm>
    {
        public ApiResponse execute(BrowseDataForm form, BindException errors) throws Exception
        {
            Map<String, Map<String, Object>> types = new TreeMap<>();
            ApiSimpleResponse response = new ApiSimpleResponse();

            Portal.WebPart webPart = Portal.getPart(getViewContext().getContainer(), form.getPageId(), form.getIndex());

            Map<String, String> props;
            if (webPart != null)
            {
                props = webPart.getPropertyMap();
                Map<String, String> webPartProps = new HashMap<>();
                webPartProps.put("name", webPart.getName());
                if (props.containsKey("webpart.title"))
                    webPartProps.put("title", props.get("webpart.title"));
                if (props.containsKey("webpart.height"))
                    webPartProps.put("height", props.get("webpart.height"));
                else
                    webPartProps.put("height", String.valueOf(700));
                response.put("webpart", new JSONObject(webPartProps));
            }
            else
            {
                props = resolveJSONProperties(form.getProps());
            }

            List<DataViewProvider.Type> visibleDataTypes = new ArrayList<>();
            for (DataViewProvider.Type type : DataViewService.get().getDataTypes(getContainer(), getUser()))
            {
                Map<String, Object> info = new HashMap<>();
                boolean visible = getCheckedState(type.getName(), props, type.isShowByDefault());

                info.put("name", type.getName());
                info.put("visible", visible);

                types.put(type.getName(), info);

                if (visible)
                    visibleDataTypes.add(type);
            }

            response.put("types", new JSONArray(types.values()));

            String dateFormat = StudyManager.getInstance().getDefaultDateFormatString(getViewContext().getContainer());
            if (dateFormat == null)
                dateFormat = DateUtil.getStandardDateFormatString();


            //The purpose of this flag is so LABKEY.Query.getDataViews() can omit additional information only used to render the
            //webpart.  this also leaves flexibility to change that metadata
            if (form.includeMetadata())
            {
                // visible columns
                Map<String, Map<String, Boolean>> columns = new LinkedHashMap<>();

                columns.put("Type", Collections.singletonMap("checked", getCheckedState("Type", props, false)));
                columns.put("Author", Collections.singletonMap("checked", getCheckedState("Author", props, false)));
                columns.put("Modified", Collections.singletonMap("checked", getCheckedState("Modified", props, false)));
                columns.put("Status", Collections.singletonMap("checked", getCheckedState("Status", props, false)));
                columns.put("Access", Collections.singletonMap("checked", getCheckedState("Access", props, true)));
                columns.put("Details", Collections.singletonMap("checked", getCheckedState("Details", props, true)));
                columns.put("Data Cut Date", Collections.singletonMap("checked", getCheckedState("Data Cut Date", props, false)));

                response.put("visibleColumns", columns);

                // provider editor information
                Map<String, Map<String, Object>> viewTypeProps = new HashMap<>();
                for (DataViewProvider.Type type : visibleDataTypes)
                {
                    DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                    DataViewProvider.EditInfo editInfo = provider.getEditInfo();
                    if (editInfo != null)
                    {
                        Map<String, Object> info = new HashMap<>();
                        for (String propName : editInfo.getEditableProperties(getContainer(), getUser()))
                        {
                            info.put(propName, true);
                        }
                        viewTypeProps.put(type.getName(), info);
                    }
                }
                response.put("editInfo", viewTypeProps);
                response.put("dateFormat", ExtUtil.toExtDateFormat(dateFormat));
            }

            if (form.includeData())
            {
                int startingDefaultDisplayOrder = 0;
                Set<String> defaultCategories = new TreeSet<>(new Comparator<String>(){
                    @Override
                    public int compare(String s1, String s2)
                    {
                        return s1.compareToIgnoreCase(s2);
                    }
                });

                getViewContext().put("returnUrl", form.getReturnUrl());

                // get the data view information from all visible providers
                List<DataViewInfo> views = new ArrayList<>();

                for (DataViewProvider.Type type : visibleDataTypes)
                {
                    DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                    views.addAll(provider.getViews(getViewContext()));
                }

                for (DataViewInfo info : views)
                {
                    ViewCategory category = info.getCategory();

                    if (category != null)
                    {
                        if (category.getDisplayOrder() != ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER)
                            startingDefaultDisplayOrder = Math.max(startingDefaultDisplayOrder, category.getDisplayOrder());
                        else
                            defaultCategories.add(category.getLabel());
                    }
                }

                // add the default categories after the explicit categories
                Map<String, Integer> defaultCategoryMap = new HashMap<>();
                for (Iterator<String> it = defaultCategories.iterator(); it.hasNext(); )
                {
                    defaultCategoryMap.put(it.next(), ++startingDefaultDisplayOrder);                    
                }

                for (DataViewInfo info : views)
                {
                    ViewCategory category = info.getCategory();

                    if (category != null)
                    {
                        if (category.getDisplayOrder() == ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER && defaultCategoryMap.containsKey(category.getLabel()))
                            category.setDisplayOrder(defaultCategoryMap.get(category.getLabel()));
                    }
                }
                response.put("data", DataViewService.get().toJSON(getContainer(), getUser(), views, dateFormat));
            }

            return response;
        }

        private Boolean getCheckedState(String prop, Map<String, String> propMap, boolean defaultState)
        {
            if (propMap.containsKey(prop))
            {
                return !propMap.get(prop).equals("0");
            }
            return defaultState;
        }

        private Map<String, String> resolveJSONProperties(Map<String, Object> formProps)
        {
            JSONObject jsonProps = (JSONObject) formProps;
            Map<String, String> props = new HashMap<>();
            boolean explicit = false;

            if (null != jsonProps && jsonProps.size() > 0)
            {
                try
                {
                    // Data Types Filter
                    JSONArray dataTypes = jsonProps.getJSONArray("dataTypes");
                    for (int i=0; i < dataTypes.length(); i++)
                    {
                        props.put((String) dataTypes.get(i), "on");
                        explicit = true;
                    }
                }
                catch (JSONException x)
                {
                    /* No-op */
                }

                if (explicit)
                {
                    for (ViewInfo.DataType t : ViewInfo.DataType.values())
                    {
                        if (!props.containsKey(t.name()))
                        {
                            props.put(t.name(), "0");
                        }
                    }
                }
            }

            return props;
        }
    }

    /**
     * This action is currently just an example. This would provide the proper configuration for a tree-based
     * layout of categorized data views. For now only dummy data is generated for rendering.
     * See 'asTree' in DataViewsPanel.js.
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseDataTreeAction extends ApiAction<BrowseDataForm>
    {
        private String dateFormat;

        @Override
        public ApiResponse execute(BrowseDataForm form, BindException errors) throws Exception
        {
            HttpServletResponse resp = getViewContext().getResponse();
            resp.setContentType("application/json");
            resp.getWriter().write(getTreeData(form).toString());

            return null;
        }

        private JSONObject getTreeData(BrowseDataForm form) throws Exception
        {
            List<DataViewProvider.Type> visibleDataTypes = getVisibleDataTypes(form);

            int startingDefaultDisplayOrder = 0;
            Set<String> defaultCategories = new TreeSet<>(new Comparator<String>(){
                @Override
                public int compare(String s1, String s2)
                {
                    return s1.compareToIgnoreCase(s2);
                }
            });

            if (null != form.getReturnUrl())
                getViewContext().put("returnUrl", form.getReturnUrl());

            // get the data view information from all visible providers
            List<DataViewInfo> views = new ArrayList<>();

            for (DataViewProvider.Type type : visibleDataTypes)
            {
                views.addAll(DataViewService.get().getProvider(type, getViewContext()).getViews(getViewContext()));
            }

            for (DataViewInfo info : views)
            {
                ViewCategory category = info.getCategory();

                if (category != null)
                {
                    if (category.getDisplayOrder() != ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER)
                    {
                        startingDefaultDisplayOrder = Math.max(startingDefaultDisplayOrder, category.getDisplayOrder());
                    }
                    else
                        defaultCategories.add(category.getLabel());
                }
            }

            // add the default categories after the explicit categories
            Map<String, Integer> defaultCategoryMap = new HashMap<>();
            for (String cat : defaultCategories)
            {
                defaultCategoryMap.put(cat, ++startingDefaultDisplayOrder);
            }

            for (DataViewInfo info : views)
            {
                ViewCategory category = info.getCategory();

                if (category != null)
                {
                    if (category.getDisplayOrder() == ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER && defaultCategoryMap.containsKey(category.getLabel()))
                        category.setDisplayOrder(defaultCategoryMap.get(category.getLabel()));
                }
            }

            dateFormat = StudyManager.getInstance().getDefaultDateFormatString(getViewContext().getContainer());
            if (dateFormat == null)
                dateFormat = DateUtil.getStandardDateFormatString();

            return buildTree(views);
        }

        private JSONObject buildTree(List<DataViewInfo> views)
        {
            Comparator<ViewCategory> t = new Comparator<ViewCategory>()
            {
                @Override
                public int compare(ViewCategory c1, ViewCategory c2)
                {
                    int order = ((Integer) c1.getDisplayOrder()).compareTo(c2.getDisplayOrder());
                    if (order == 0)
                        return c1.getLabel().compareToIgnoreCase(c2.getLabel());
                    else if (c1.getLabel().equalsIgnoreCase("Uncategorized"))
                        return 1;
                    else if (c2.getLabel().equalsIgnoreCase("Uncategorized"))
                        return -1;
                    else if (c1.getDisplayOrder() == 0)
                        return 1;
                    else if (c2.getDisplayOrder() == 0)
                        return -1;
                    return order;
                }
            };

            // Get all categories -- group views by them
            Map<Integer, List<DataViewInfo>> groups = new HashMap<>();
            Map<Integer, ViewCategory> categories = new HashMap<>();
            TreeSet<ViewCategory> order = new TreeSet<>(t);

            for (DataViewInfo view : views)
            {
                ViewCategory vc = view.getCategory();
                if (null != vc)
                {
                    if (!groups.containsKey(vc.getRowId()))
                    {
                        groups.put(vc.getRowId(), new ArrayList<DataViewInfo>());
                    }
                    groups.get(vc.getRowId()).add(view);
                    categories.put(vc.getRowId(), vc);
                    if (null == vc.getParent())
                    {
                        order.add(vc);
                    }
                    else if (!categories.containsKey(vc.getParent().getRowId()))
                    {
                        // Possible unreferenced parent
                        vc = vc.getParent();
                        if (!groups.containsKey(vc.getRowId()))
                        {
                            groups.put(vc.getRowId(), new ArrayList<DataViewInfo>());
                        }
                        categories.put(vc.getRowId(), vc);
                        order.add(vc);
                    }
                }
            }

            // Construct category tree
            Map<Integer, TreeSet<ViewCategory>> tree = new HashMap<>();

            for (Integer ckey : groups.keySet())
            {
                ViewCategory c = categories.get(ckey);

                if (!tree.containsKey(ckey))
                {
                    tree.put(ckey, new TreeSet<>(t));
                }

                ViewCategory p = c.getParent();
                if (null != p)
                {
                    if (!tree.containsKey(p.getRowId()))
                    {
                        tree.put(p.getRowId(), new TreeSet<>(t));
                    }
                    tree.get(p.getRowId()).add(c);
                }
            }

            // create output

            // Construct root node
            JSONObject root = new JSONObject();
            JSONArray rootChildren = new JSONArray();

            for (ViewCategory vc : order)
            {
                JSONObject category = new JSONObject();
                category.put("name", vc.getLabel());
                category.put("icon", false);
                category.put("expanded", true);
                category.put("cls", "dvcategory");
                category.put("children", processChildren(vc, groups, tree));

                rootChildren.put(category);
            }

            root.put("name", ".");
            root.put("expanded", true);
            root.put("children", rootChildren);

            return root;
        }

        private JSONArray processChildren(ViewCategory vc, Map<Integer, List<DataViewInfo>> groups, Map<Integer, TreeSet<ViewCategory>> tree)
        {
            JSONArray children = new JSONArray();

            // process other categories
            if (tree.get(vc.getRowId()).size() > 0)
            {
                // has it's own sub-categories
                for (ViewCategory v : tree.get(vc.getRowId()))
                {
                    JSONObject category = new JSONObject();
                    category.put("name", v.getLabel());
                    category.put("icon", false);
                    category.put("expanded", true);
                    category.put("cls", "dvcategory");
                    category.put("children", processChildren(v, groups, tree));

                    children.put(category);
                }
            }

            // process views
            for (DataViewInfo view : groups.get(vc.getRowId()))
            {
                JSONObject viewJson = DataViewService.get().toJSON(getContainer(), getUser(), view, dateFormat);
                viewJson.put("name", view.getName());
                viewJson.put("leaf", true);
                viewJson.put("icon", view.getIcon());
                children.put(viewJson);
            }

            return children;
        }

        private List<DataViewProvider.Type> getVisibleDataTypes(BrowseDataForm form)
        {
            List<DataViewProvider.Type> visibleDataTypes = new ArrayList<>();
            Map<String, String> props;
            Portal.WebPart webPart = getWebPart(form);

            if (null != webPart)
                props = webPart.getPropertyMap();
            else
                props = resolveJSONProperties(form.getProps());

            for (DataViewProvider.Type type : DataViewService.get().getDataTypes(getContainer(), getUser()))
            {
                boolean visible = getCheckedState(type.getName(), props, type.isShowByDefault());

                if (visible)
                    visibleDataTypes.add(type);
            }

            return visibleDataTypes;
        }

        @Nullable
        private Portal.WebPart getWebPart(BrowseDataForm form)
        {
            return Portal.getPart(getViewContext().getContainer(), form.getPageId(), form.getIndex());
        }

        private Boolean getCheckedState(String prop, Map<String, String> propMap, boolean defaultState)
        {
            if (propMap.containsKey(prop))
            {
                return !propMap.get(prop).equals("0");
            }
            return defaultState;
        }

        private Map<String, String> resolveJSONProperties(Map<String, Object> formProps)
        {
            JSONObject jsonProps = (JSONObject) formProps;
            Map<String, String> props = new HashMap<>();
            boolean explicit = false;

            if (null != jsonProps && jsonProps.size() > 0)
            {
                try
                {
                    // Data Types Filter
                    JSONArray dataTypes = jsonProps.getJSONArray("dataTypes");
                    for (int i=0; i < dataTypes.length(); i++)
                    {
                        props.put((String) dataTypes.get(i), "on");
                        explicit = true;
                    }
                }
                catch (JSONException x)
                {
                    /* No-op */
                }

                if (explicit)
                {
                    for (ViewInfo.DataType t : ViewInfo.DataType.values())
                    {
                        if (!props.containsKey(t.name()))
                        {
                            props.put(t.name(), "0");
                        }
                    }
                }
            }

            return props;
        }
    }

    public static class BrowseDataForm extends ReturnUrlForm implements CustomApiForm
    {
        private int index;
        private String pageId;
        private boolean includeData = true;
        private boolean includeMetadata = true;
        private int _parent = -2;
        Map<String, Object> _props;

        private ViewInfo.DataType[] _dataTypes = new ViewInfo.DataType[]{ViewInfo.DataType.reports, ViewInfo.DataType.datasets, ViewInfo.DataType.queries};

        public ViewInfo.DataType[] getDataTypes()
        {
            return _dataTypes;
        }

        public void setDataTypes(ViewInfo.DataType[] dataTypes)
        {
            _dataTypes = dataTypes;
        }

        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public boolean includeData()
        {
            return includeData;
        }

        public void setIncludeData(boolean includedata)
        {
            includeData = includedata;
        }

        public boolean includeMetadata()
        {
            return includeMetadata;
        }

        public void setIncludeMetadata(boolean includeMetadata)
        {
            this.includeMetadata = includeMetadata;
        }

        public void setParent(int parent)
        {
            _parent = parent;
        }

        public int getParent()
        {
            return _parent;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            _props = props;
        }

        public Map<String, Object> getProps()
        {
            return _props;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetCategoriesAction extends ApiAction<BrowseDataForm>
    {
        public ApiResponse execute(BrowseDataForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> categoryList = new ArrayList<>();

            List<ViewCategory> categoriesWithDisplayOrder = new ArrayList<>();
            List<ViewCategory> categoriesWithoutDisplayOrder = new ArrayList<>();

            ViewCategory[] categories;
            int parent = form.getParent();

            // Default, no parent specifically requested
            if (parent == -2)
            {
                categories = ViewCategoryManager.getInstance().getCategories(getContainer(), getUser());
            }
            else if (parent == 0)
            {
                // parent filter on non-existent category
                categories = new ViewCategory[0];
            }
            else
            {
                SimpleFilter filter;
                FieldKey field = FieldKey.fromParts("Parent");

                if (parent > 0)
                    filter = new SimpleFilter(field, parent);
                else
                    filter = new SimpleFilter(field, null, CompareType.ISBLANK);
                categories = ViewCategoryManager.getInstance().getCategories(getContainer(), getUser(), filter);
            }

            for (ViewCategory c : categories)
            {
                if (c.getDisplayOrder() != 0)
                    categoriesWithDisplayOrder.add(c);
                else
                    categoriesWithoutDisplayOrder.add(c);
            }

            Collections.sort(categoriesWithDisplayOrder, new Comparator<ViewCategory>(){
                @Override
                public int compare(ViewCategory c1, ViewCategory c2)
                {
                    return c1.getDisplayOrder() - c2.getDisplayOrder();
                }
            });

            if (!categoriesWithoutDisplayOrder.isEmpty())
            {
                Collections.sort(categoriesWithoutDisplayOrder, new Comparator<ViewCategory>(){
                    @Override
                    public int compare(ViewCategory c1, ViewCategory c2)
                    {
                        return c1.getLabel().compareToIgnoreCase(c2.getLabel());
                    }
                });
            }
            for (ViewCategory vc : categoriesWithDisplayOrder)
                categoryList.add(vc.toJSON(getUser()));

            // assign an order to all categories returned to the client
            int count = categoriesWithDisplayOrder.size() + 1;
            for (ViewCategory vc : categoriesWithoutDisplayOrder)
            {
                vc.setDisplayOrder(count++);
                categoryList.add(vc.toJSON(getUser()));
            }
            response.put("categories", categoryList);

            return response;
        }
    }

    public static class CategoriesForm implements CustomApiForm
    {
        List<ViewCategory> _categories = new ArrayList<>();

        public List<ViewCategory> getCategories()
        {
            return _categories;
        }

        public void setCategories(List<ViewCategory> categories)
        {
            _categories = categories;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            Object categoriesProp = props.get("categories");
            if (categoriesProp != null)
            {
                for (JSONObject categoryInfo : ((JSONArray) categoriesProp).toJSONObjectArray())
                {
                    _categories.add(ViewCategory.fromJSON(categoryInfo));
                }
            }
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SaveCategoriesAction extends MutatingApiAction<CategoriesForm>
    {
        public ApiResponse execute(CategoriesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try {
                scope.ensureTransaction();

                for (ViewCategory category : form.getCategories())
                    ViewCategoryManager.getInstance().saveCategory(getContainer(), getUser(), category);

                scope.commitTransaction();

                response.put("success", true);
                return response;
            }
            catch (Exception e) {
                response.put("success", false);
                response.put("message", e.getMessage());
            }
            finally
            {
                scope.closeConnection();
            }
            return response;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteCategoriesAction extends MutatingApiAction<CategoriesForm>
    {
        public ApiResponse execute(CategoriesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try {
                scope.ensureTransaction();

                for (ViewCategory category : form.getCategories())
                    ViewCategoryManager.getInstance().deleteCategory(getContainer(), getUser(), category);

                scope.commitTransaction();
                
                response.put("success", true);
                return response;
            }
            finally
            {
                scope.closeConnection();
            }
        }
    }

    public static class EditViewsForm
    {
        String _id;
        String _dataType;

        public String getId()
        {
            return _id;
        }

        public void setId(String id)
        {
            _id = id;
        }

        public String getDataType()
        {
            return _dataType;
        }

        public void setDataType(String dataType)
        {
            _dataType = dataType;
        }

        public Map<String, Object> getPropertyMap(PropertyValues pv, List<String> editableValues, Map<String, MultipartFile> files) throws ValidationException
        {
            Map<String, Object> map = new HashMap<>();

            for (PropertyValue value : pv.getPropertyValues())
            {
                if (editableValues.contains(value.getName()))
                    map.put(value.getName(), value.getValue());
            }

            for (String fileName : files.keySet())
            {
                if (editableValues.contains(fileName) && !files.get(fileName).isEmpty())
                {
                    try {
                        map.put(fileName, files.get(fileName).getInputStream());
                    }
                    catch(IOException e)
                    {
                        throw new ValidationException("Unable to read file: " + fileName);
                    }
                }
            }

            return map;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class EditViewAction extends MutatingApiAction<EditViewsForm>
    {
        private DataViewProvider _provider;
        private Map<String, Object> _propertiesMap;

        public EditViewAction()
        {
            super();
            //because this will typically be called from a hidden iframe
            //we must respond with a content-type of text/html or the
            //browser will prompt the user to save the response, as the
            //browser won't natively show application/json content-type            
            setContentTypeOverride("text/html");
        }

        @Override
        public void validateForm(EditViewsForm form, Errors errors)
        {
            DataViewProvider.Type type = DataViewService.get().getDataTypeByName(form.getDataType());
            if (type != null)
            {
                _provider = DataViewService.get().getProvider(type, getViewContext());
                DataViewProvider.EditInfo editInfo = _provider.getEditInfo();

                if (editInfo != null)
                {
                    List<String> editable = Arrays.asList(editInfo.getEditableProperties(getContainer(), getUser()));
                    try {
                        _propertiesMap = form.getPropertyMap(getPropertyValues(), editable, getFileMap());
                        editInfo.validateProperties(getContainer(), getUser(), form.getId(), _propertiesMap);
                    }
                    catch (ValidationException e)
                    {
                        for (ValidationError error : e.getErrors())
                            errors.reject(ERROR_MSG, error.getMessage());
                    }
                }
                else
                    errors.reject(ERROR_MSG, "This data view does not support editing");
            }
            else
                errors.reject(ERROR_MSG, "Unable to find the specified data view type");
        }

        public ApiResponse execute(EditViewsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DataViewProvider.EditInfo editInfo = _provider.getEditInfo();
            if (editInfo != null && _propertiesMap != null)
            {
                editInfo.updateProperties(getViewContext(), form.getId(), _propertiesMap);
                response.put("success", true);
            }
            else
                response.put("success", false);

            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseStudyScheduleAction extends ApiAction<BrowseStudyForm>
    {
        @Override
        public ApiResponse execute(BrowseStudyForm browseDataForm, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StudyManager manager = StudyManager.getInstance();
            Study study = manager.getStudy(getContainer());
            StudySchedule schedule = new StudySchedule();
            CohortImpl cohort = null;

            if (browseDataForm.getCohortId() != null)
            {
                cohort = manager.getCohortForRowId(getContainer(), getUser(), browseDataForm.getCohortId());
            }

            if (cohort == null && browseDataForm.getCohortLabel() != null)
            {
                cohort = manager.getCohortByLabel(getContainer(), getUser(), browseDataForm.getCohortLabel());
            }

            if (study != null)
            {
                schedule.setVisits(manager.getVisits(study, cohort, getUser(), Visit.Order.DISPLAY));
                schedule.setDatasets(
                        manager.getDataSetDefinitions(study, cohort, DataSet.TYPE_STANDARD, DataSet.TYPE_PLACEHOLDER),
                        DataViewService.get().getViews(getViewContext(), Collections.singletonList(DatasetViewProvider.TYPE)));

                response.put("schedule", schedule.toJSON(getUser()));
                response.put("success", true);

                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetStudyTimepointsAction extends ApiAction<BrowseStudyForm>
    {
        @Override
        public ApiResponse execute(BrowseStudyForm browseDataForm, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StudyManager manager = StudyManager.getInstance();
            Study study = manager.getStudy(getContainer());
            StudySchedule schedule = new StudySchedule();
            CohortImpl cohort = null;

            if (browseDataForm.getCohortId() != null)
            {
                cohort = manager.getCohortForRowId(getContainer(), getUser(), browseDataForm.getCohortId());
            }

            if (cohort == null && browseDataForm.getCohortLabel() != null)
            {
                cohort = manager.getCohortByLabel(getContainer(), getUser(), browseDataForm.getCohortLabel());
            }

            if (study != null)
            {
                schedule.setVisits(manager.getVisits(study, cohort, getUser(), Visit.Order.DISPLAY));

                response.put("schedule", schedule.toJSON(getUser()));
                response.put("success", true);

                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateStudyScheduleAction extends ApiAction<StudySchedule>
    {
        @Override
        public void validateForm(StudySchedule form, Errors errors)
        {
            if (form.getSchedule().size() <= 0)
                errors.reject(ERROR_MSG, "No study schedule records have been specified");
        }

        @Override
        public ApiResponse execute(StudySchedule form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = StudyManager.getInstance().getStudy(getContainer());

            if (study != null)
            {
                for (Map.Entry<Integer, List<VisitDataSet>> entry : form.getSchedule().entrySet())
                {
                    DataSet ds = StudyService.get().getDataSet(getContainer(), entry.getKey());
                    if (ds != null)
                    {
                        for (VisitDataSet visit : entry.getValue())
                        {
                            VisitDataSetType type = visit.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.NOT_ASSOCIATED;

                            StudyManager.getInstance().updateVisitDataSetMapping(getUser(), getContainer(),
                                    visit.getVisitRowId(), ds.getDataSetId(), type);
                        }
                    }
                }
                response.put("success", true);

                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    public static class BrowseStudyForm
    {
        private Integer _cohortId;
        private String _cohortLabel;

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }

        public String getCohortLabel()
        {
            return _cohortLabel;
        }

        public void setCohortLabel(String cohortLabel)
        {
            _cohortLabel = cohortLabel;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DefineDatasetAction extends ApiAction<DefineDatasetForm>
    {
        private StudyImpl _study;

        @Override
        public void validateForm(DefineDatasetForm form, Errors errors)
        {
            _study = StudyManager.getInstance().getStudy(getContainer());

            if (_study != null)
            {
                switch (form.getType())
                {
                    case defineManually:
                    case placeHolder:
                    case importFromFile:
                        if (StringUtils.isEmpty(form.getName()))
                            errors.reject(ERROR_MSG, "A Dataset name must be specified.");
                        else if (StudyManager.getInstance().getDataSetDefinitionByName(_study, form.getName()) != null)
                            errors.reject(ERROR_MSG, "A Dataset named: " + form.getName() + " already exists in this folder.");
                        break;

                    case linkToTarget:
                        if (form.getExpectationDataset() == null || form.getTargetDataset() == null)
                            errors.reject(ERROR_MSG, "An expectation Dataset and target Dataset must be specified.");
                        break;

                    case linkManually:
                        if (form.getExpectationDataset() == null)
                            errors.reject(ERROR_MSG, "An expectation Dataset must be specified.");
                        break;
                }
            }
            else
                errors.reject(ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(DefineDatasetForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DataSetDefinition def = null;

            DbScope scope =  StudySchema.getInstance().getSchema().getScope();
            scope.ensureTransaction();

            try {
                Integer categoryId = null;

                if (form.getCategory() != null)
                {
                    ViewCategory category = ViewCategoryManager.getInstance().ensureViewCategory(getContainer(), getUser(), form.getCategory().getLabel());
                    categoryId = category.getRowId();
                }

                switch (form.getType())
                {
                    case importFromFile:
                    case defineManually:
                        def = AssayPublishManager.getInstance().createAssayDataset(getUser(), _study, form.getName(),
                                null, null, false, DataSet.TYPE_STANDARD, categoryId, null);

                        if (def != null)
                        {
                            def.provisionTable();
                        }

                        ActionURL redirect;
                        if (form.getType() == DefineDatasetForm.Type.defineManually)
                            redirect = new ActionURL(EditTypeAction.class, getContainer()). addParameter(DataSetDefinition.DATASETKEY, def.getDataSetId());
                        else
                            redirect = new ActionURL(DatasetController.DefineAndImportDatasetAction.class, getContainer()).addParameter(DataSetDefinition.DATASETKEY, def.getDataSetId());

                        response.put("redirectUrl", redirect.getLocalURIString());
                        break;
                    case placeHolder:
                        def = AssayPublishManager.getInstance().createAssayDataset(getUser(), _study, form.getName(),
                                null, null, false, DataSet.TYPE_PLACEHOLDER, categoryId, null);
                        if (def != null)
                        {
                            def.provisionTable();
                        }
                        response.put("datasetId", def.getDataSetId());
                        break;

                    case linkManually:
                    case linkImport:
                        def = StudyManager.getInstance().getDataSetDefinition(_study, form.getExpectationDataset());
                        if (def != null)
                        {
                            def = def.createMutable();

                            def.setType(DataSet.TYPE_STANDARD);
                            def.save(getUser());

                            // add a cancel url to rollback either the manual link or import from file link
                            ActionURL cancelURL = new ActionURL(CancelDefineDatasetAction.class, getContainer()).addParameter("expectationDataset", form.getExpectationDataset());

                            if (form.getType() == DefineDatasetForm.Type.linkManually)
                                redirect = new ActionURL(EditTypeAction.class, getContainer()). addParameter(DataSetDefinition.DATASETKEY, form.getExpectationDataset());
                            else
                                redirect = new ActionURL(DatasetController.DefineAndImportDatasetAction.class, getContainer()).addParameter(DataSetDefinition.DATASETKEY, form.getExpectationDataset());

                            redirect.addParameter(ActionURL.Param.cancelUrl.name(), cancelURL.getLocalURIString());
                            response.put("redirectUrl", redirect.getLocalURIString());
                        }
                        else
                            throw new IllegalArgumentException("The expectation Dataset did not exist");
                        break;

                    case linkToTarget:
                        DataSetDefinition expectationDataset = StudyManager.getInstance().getDataSetDefinition(_study, form.getExpectationDataset());
                        DataSetDefinition targetDataset = StudyManager.getInstance().getDataSetDefinition(_study, form.getTargetDataset());

                        StudyManager.getInstance().linkPlaceHolderDataSet(_study, getUser(), expectationDataset, targetDataset);
                        break;
                }
                response.put("success", true);
                scope.commitTransaction();
            }
            finally
            {
                scope.closeConnection();
            }

            return response;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CancelDefineDatasetAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            // switch the dataset back to a placeholder type
            Study study = getStudy(getContainer());
            if (study != null)
            {
                String expectationDataset = getViewContext().getActionURL().getParameter("expectationDataset");
                if (NumberUtils.isDigits(expectationDataset))
                {
                    DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, NumberUtils.toInt(expectationDataset));
                    if (def != null)
                    {
                        def = def.createMutable();

                        def.setType(DataSet.TYPE_PLACEHOLDER);
                        def.save(getUser());
                    }
                }
            }
            throw new RedirectException(new ActionURL(StudyScheduleAction.class, getContainer()));
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class DefineDatasetForm implements CustomApiForm
    {
        enum Type {
            importFromFile,
            defineManually,
            placeHolder,
            linkToTarget,
            linkManually,
            linkImport,
        }

        private DefineDatasetForm.Type _type;
        private String _name;
        private ViewCategory _category;
        private Integer _expectationDataset;
        private Integer _targetDataset;

        public Type getType()
        {
            return _type;
        }

        public String getName()
        {
            return _name;
        }

        public ViewCategory getCategory()
        {
            return _category;
        }

        public Integer getExpectationDataset()
        {
            return _expectationDataset;
        }

        public Integer getTargetDataset()
        {
            return _targetDataset;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            Object categoryProp = props.get("category");
            if (categoryProp instanceof JSONObject)
            {
                _category = ViewCategory.fromJSON((JSONObject)categoryProp);
            }

            _name = (String)props.get("name");

            Object type = props.get("type");
            if (type instanceof String)
                _type = Type.valueOf((String)type);

            _expectationDataset = (Integer)props.get("expectationDataset");
            _targetDataset = (Integer)props.get("targetDataset");
        }
    }

    public static class ChangeAlternateIdsForm
    {
        private String _prefix = "";
        private int _numDigits = StudyManager.ALTERNATEID_DEFAULT_NUM_DIGITS;
        private int _aliasDatasetId = -1;
        private String _aliasColumn = "";
        private String _sourceColumn = "";

        public String getAliasColumn()
        {
            return _aliasColumn;
        }

        public void setAliasColumn(String aliasColumn)
        {
            this._aliasColumn = aliasColumn;
        }

        public String getSourceColumn()
        {
            return _sourceColumn;
        }

        public void setSourceColumn(String sourceColumn)
        {
            this._sourceColumn = sourceColumn;
        }

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }

        public int getNumDigits()
        {
            return _numDigits;
        }

        public void setNumDigits(int numDigits)
        {
            _numDigits = numDigits;
        }

        public int getAliasDatasetId()
        {
            return _aliasDatasetId;
        }
        public void setAliasDatasetId(int aliasDatasetId)
        {
            _aliasDatasetId = aliasDatasetId;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ChangeAlternateIdsAction extends ApiAction<ChangeAlternateIdsForm>
    {
        @Override
        public ApiResponse execute(ChangeAlternateIdsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null)
            {
                setAlternateIdProperties(study, form.getPrefix(), form.getNumDigits());
                StudyManager.getInstance().clearAlternateParticipantIds(study);
                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    public static class MapAliasIdsForm
    {
        private int _dataSetId;
        private String _aliasColumn = "";
        private String _sourceColumn = "";

        public int getDataSetId()
        {
            return _dataSetId;
        }

        public void setDataSetId(int dataSetId)
        {
            _dataSetId = dataSetId;
        }

        public String getAliasColumn()
        {
            return _aliasColumn;
        }

        public void setAliasColumn(String aliasColumn)
        {
            _aliasColumn = aliasColumn;
        }

        public String getSourceColumn()
        {
            return _sourceColumn;
        }

        public void setSourceColumn(String sourceColumn)
        {
            _sourceColumn = sourceColumn;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class MapAliasIdsAction extends ApiAction<MapAliasIdsForm>
    {
        @Override
        public ApiResponse execute(MapAliasIdsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null)
            {
                setAliasMappingProperties(study, form.getDataSetId(), form.getAliasColumn(), form.getSourceColumn());
                StudyManager.getInstance().clearAlternateParticipantIds(study);
                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ExportParticipantTransformsAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null)
            {
                // Ensure alternateIds are generated for all participants
                StudyManager.getInstance().generateNeededAlternateParticipantIds(study);

                TableInfo ti = StudySchema.getInstance().getTableInfoParticipant();
                List<ColumnInfo> cols = new ArrayList<>();
                cols.add(ti.getColumn("participantid"));
                cols.add(ti.getColumn("alternateid"));
                cols.add(ti.getColumn("dateoffset"));
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition(ti.getColumn("container"), getContainer());
                Results rs = QueryService.get().select(ti, cols, filter, new Sort("participantid"));

                TSVGridWriter writer = new TSVGridWriter(rs);
                writer.setApplyFormats(false);
                writer.setFilenamePrefix("ParticipantTransforms");
                writer.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
                writer.write(getViewContext().getResponse()); // NOTE: TSVGridWriter closes PrintWriter and ResultSet

                return null;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static ChangeAlternateIdsForm getChangeAlternateIdForm(StudyImpl study)
    {
        ChangeAlternateIdsForm changeAlternateIdsForm = new ChangeAlternateIdsForm();
        changeAlternateIdsForm.setPrefix(study.getAlternateIdPrefix());
        changeAlternateIdsForm.setNumDigits(study.getAlternateIdDigits());
        if(study.getParticipantAliasDatasetId() != null){
            changeAlternateIdsForm.setAliasDatasetId(study.getParticipantAliasDatasetId());
            changeAlternateIdsForm.setAliasColumn(study.getParticipantAliasProperty());
            changeAlternateIdsForm.setSourceColumn(study.getParticipantAliasSourceProperty());
        }

        return changeAlternateIdsForm;
    }

    private void setAlternateIdProperties(StudyImpl study, String prefix, int numDigits)
    {
        try
        {
            study = study.createMutable();
            study.setAlternateIdPrefix(prefix);
            study.setAlternateIdDigits(numDigits);
            StudyManager.getInstance().updateStudy(getUser(), study);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private void setAliasMappingProperties(StudyImpl study, int dataSetId, String aliasColumn, String sourceColumn)
    {
        try
        {
            study = study.createMutable();
            study.setParticipantAliasDatasetId(dataSetId);
            study.setParticipantAliasProperty(aliasColumn);
            study.setParticipantAliasSourceProperty(sourceColumn);
            StudyManager.getInstance().updateStudy(getUser(), study);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageLocationTypesAction extends SimpleViewAction<ManageLocationTypesForm>
    {
        public ModelAndView getView(ManageLocationTypesForm form, BindException errors) throws Exception
        {
            Study study = getStudyRedirectIfNull();
            form.setRepository(study.isAllowReqLocRepository());
            form.setClinic(study.isAllowReqLocClinic());
            form.setSal(study.isAllowReqLocSal());
            form.setEndpoint(study.isAllowReqLocEndpoint());
            return new JspView<>("/org/labkey/study/view/manageLocationTypes.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Location Types");
            return root;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SaveLocationsTypeSettingsAction extends ApiAction<ManageLocationTypesForm>
    {
        @Override
        public ApiResponse execute(ManageLocationTypesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null)
            {
                try
                {
                    study = study.createMutable();
                    study.setAllowReqLocRepository(form.isRepository());
                    study.setAllowReqLocClinic(form.isClinic());
                    study.setAllowReqLocSal(form.isSal());
                    study.setAllowReqLocEndpoint(form.isEndpoint());
                    StudyManager.getInstance().updateStudy(getUser(), study);
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    public static class ManageLocationTypesForm
    {
        private boolean _repository;
        private boolean _clinic;
        private boolean _sal;
        private boolean _endpoint;

        public boolean isRepository()
        {
            return _repository;
        }

        public void setRepository(boolean repository)
        {
            _repository = repository;
        }

        public boolean isClinic()
        {
            return _clinic;
        }

        public void setClinic(boolean clinic)
        {
            _clinic = clinic;
        }

        public boolean isSal()
        {
            return _sal;
        }

        public void setSal(boolean sal)
        {
            _sal = sal;
        }

        public boolean isEndpoint()
        {
            return _endpoint;
        }

        public void setEndpoint(boolean endpoint)
        {
            _endpoint = endpoint;
        }
    }

    @RequiresPermissionClass(ManageStudyPermission.class)
    public class ImportAlternateIdMappingAction extends AbstractQueryImportAction<IdForm>
    {
        private Study _study;
        private int _requestId = -1;

        public ImportAlternateIdMappingAction()
        {
            super(IdForm.class);
        }

        @Override
        protected void initRequest(IdForm form) throws ServletException
        {
            _requestId = form.getId();
            setHasColumnHeaders(true);
            if (null != getStudy())
            {
                _study = getStudy();
                setImportMessage("Upload a mapping of " + _study.getSubjectNounPlural() + " to Alternate IDs and date offsets from a TXT, CSV or Excel file or paste the mapping directly into the text box below. " +
                    "There must be a header row, which must contain ParticipantId and either AlternateId, DateOffset or both. Click the button below to export the current mapping.");
            }
            setTarget(StudySchema.getInstance().getTableInfoParticipant());
            setHideTsvCsvCombo(true);
            setSuccessMessageSuffix("uploaded");
        }

        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            _study = getStudyThrowIfNull();
            initRequest(form);
            return getDefaultImportView(form, errors);
        }

        @Override
        protected void validatePermission(User user, BindException errors)
        {
            checkPermissions();
        }

        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors) throws IOException
        {
            if (null == _study)
                return 0;
            return StudyManager.getInstance().setImportedAlternateParticipantIds(_study, dl, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Upload " + _study.getSubjectNounSingular() + " Mapping");
            return root;
        }

        @Override
        protected ActionURL getSuccessURL(IdForm form)
        {
            ActionURL actionURL = new ActionURL(ManageAlternateIdsAction.class, getContainer());
            return actionURL;
        }

    }
}
