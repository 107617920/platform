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

package org.labkey.study.controllers;

import gwt.client.org.labkey.study.StudyApplication;
import gwt.client.org.labkey.study.dataset.client.Designer;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.AbstractQueryImportAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.AbstractReportIdentifier;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyImportException;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.GridView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.ZipFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.study.CohortFilter;
import org.labkey.study.SampleManager;
import org.labkey.study.StudyFolderType;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.dataset.DatasetSnapshotProvider;
import org.labkey.study.importer.DatasetImportUtils;
import org.labkey.study.importer.SchemaReader;
import org.labkey.study.importer.SchemaTsvReader;
import org.labkey.study.importer.StudyImportJob;
import org.labkey.study.importer.StudyReload;
import org.labkey.study.importer.StudyReload.ReloadStatus;
import org.labkey.study.importer.StudyReload.ReloadTask;
import org.labkey.study.importer.VisitMapImporter;
import org.labkey.study.model.CohortManager;
import org.labkey.study.model.CustomParticipantView;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.DatasetReorderer;
import org.labkey.study.model.Participant;
import org.labkey.study.model.ParticipantListManager;
import org.labkey.study.model.QCState;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.UploadLog;
import org.labkey.study.model.VisitDataSet;
import org.labkey.study.model.VisitDataSetType;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.VisitMapKey;
import org.labkey.study.pipeline.DatasetFileReader;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.query.DataSetQueryView;
import org.labkey.study.query.PublishedRecordQueryView;
import org.labkey.study.query.StudyPropertiesQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.reports.StudyReportUIProvider;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.security.permissions.ManageStudyPermission;
import org.labkey.study.view.StudyGWTView;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.visitmanager.VisitManager.VisitStatistic;
import org.labkey.study.writer.StudyExportContext;
import org.labkey.study.writer.StudyWriter;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.labkey.api.util.PageFlowUtil.filter;

/**
 * User: Karl Lum
 * Date: Nov 28, 2007
 */
public class StudyController extends BaseStudyController
{
    private static final Logger _log = Logger.getLogger(StudyController.class);

    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(StudyController.class);
    private static final String PARTICIPANT_CACHE_PREFIX = "Study_participants/participantCache";
    private static final String EXPAND_CONTAINERS_KEY = StudyController.class.getName() + "/expandedContainers";

    private static final String DATASET_DATAREGION_NAME = "Dataset";
    public static final String DATASET_REPORT_ID_PARAMETER_NAME = "Dataset.reportId";
    public static final String DATASET_VIEW_NAME_PARAMETER_NAME = "Dataset.viewName";

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
            _study = getStudy(true);

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
			setView("right",right);
		}
		
		@Override
		protected void renderInternal(Object model, PrintWriter out) throws Exception
		{
			out.print("<table width=100%><tr><td align=left valign=top class=labkey-body-panel><img height=1 width=400 src=\""+getViewContext().getContextPath()+"\"/_.gif\"><br>");
			include(getBody());
			out.print("</td><td align=left valign=top class=labkey-side-panel><img height=1 width=240 src=\""+getViewContext().getContextPath()+"/_.gif\"><br>");
			include(getView("right"));
			out.print("</td></tr></table>");
		}
	}
	

    @RequiresPermissionClass(AdminPermission.class)
    public class DefineDatasetTypeAction extends FormViewAction<ImportTypeForm>
    {
        private DataSet _def;
        public ModelAndView getView(ImportTypeForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<ImportTypeForm>(getStudy(false), "importDataType.jsp", form, errors);
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
                String typeURI = AssayPublishManager.getInstance().getDomainURIString(StudyManager.getInstance().getStudy(getContainer()), form.getTypeName());
                DomainDescriptor dd = OntologyManager.getDomainDescriptor(typeURI, getContainer());
                if (null != dd)
                {
                    errors.reject("defineDatasetType", "There is a dataset named " + form.getTypeName() + " already defined in this folder.");
                }
                else
                {
                    // Check if a dataset, query or table exists with the same name
                    StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                    StudyQuerySchema studySchema = new StudyQuerySchema(study, getUser(), true);
                    if (null != studySchema.getDataSetDefinitionByName(form.getTypeName())
                            || studySchema.getTableNames().contains(form.getTypeName())
                            || QueryService.get().getQueryDef(getUser(), getContainer(), "study", form.getTypeName()) != null)
                    {
                        errors.reject("defineDatasetType", "There is a dataset or query named " + form.getTypeName() + " already defined in this folder.");
                    }
                }
            }

        }

        public boolean handlePost(ImportTypeForm form, BindException derrors) throws Exception
        {
            Integer datasetId = form.getDataSetId();

            if (form.autoDatasetId)
                _def = AssayPublishManager.getInstance().createAssayDataset(getUser(), getStudy(), form.getTypeName(), null, null, false, null);
            else
                _def = AssayPublishManager.getInstance().createAssayDataset(getUser(), getStudy(), form.getTypeName(), null, datasetId, false, null);


            if (_def != null)
            {
                String domainURI = _def.getTypeURI();
                DomainDescriptor newDomainDescriptor = OntologyManager.ensureDomainDescriptor(domainURI, form.getTypeName(), getContainer());
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
    public class EditTypeAction extends SimpleViewAction<DataSetForm>
    {
        private DataSet _def;
        public ModelAndView getView(DataSetForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudy();
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
        @Override
        protected Set<Role> getContextualRoles()
        {
            if (getViewContext().getUser() == User.getSearchUser())
                return Collections.singleton(RoleManager.getRole(ReaderRole.class));
            return null;
        }

        private DataSetDefinition _def;
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            _def = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getId());
            if (_def == null)
            {
                throw new NotFoundException("Invalid Dataset ID");
            }
            return  new StudyJspView<DataSetDefinition>(StudyManager.getInstance().getStudy(getContainer()),
                    "datasetDetails.jsp", _def, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrailDatasetAdmin(root).addChild(_def.getLabel() + " Dataset Properties");
        }
    }

    public static class DatasetFilterForm extends QueryViewAction.QueryExportForm implements HasViewContext
    {
        private String _cohortFilterType;
        private Integer _cohortId;
        private String _qcState;
        private ViewContext _viewContext;

        public Integer getCohortId()
        {
            return _cohortId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCohortId(Integer cohortId)
        {
            if (StudyManager.getInstance().showCohorts(HttpView.currentContext().getContainer(), HttpView.currentContext().getUser()))
                _cohortId = cohortId;
        }

        public String getQCState()
        {
            return _qcState;
        }

        public void setQCState(String qcState)
        {
            _qcState = qcState;
        }

        public String getCohortFilterType()
        {
            return _cohortFilterType;
        }

        public void setCohortFilterType(String cohortFilterType)
        {
            _cohortFilterType = cohortFilterType;
        }

        public CohortFilter getCohortFilter()
        {
            if (_cohortId == null)
                return null;
            CohortFilter.Type type = _cohortFilterType != null ? CohortFilter.Type.valueOf(_cohortFilterType) : null;
            return new CohortFilter(type, getCohortId());
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
            _study = getStudy();
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
                bean.cohortFilter = CohortFilter.getFromURL(getViewContext().getActionURL());

            VisitManager visitManager = StudyManager.getInstance().getVisitManager(bean.study);
            bean.visitMapSummary = visitManager.getVisitSummary(bean.cohortFilter, bean.qcStates, bean.stats);

            return new StudyJspView<OverviewBean>(getStudy(), "overview.jsp", bean, errors);
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
                    _report = identifier.getReport();
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
                    _report = identifier.getReport();
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
            DataSet def = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);

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


    // TODO I don't think this is quite correct, however this is the the check currently used by import and delete
    // moved here to call it out instead of embedding it
    private static boolean canWrite(DataSetDefinition def, User user)
    {
        return def.canWrite(user) && def.getContainer().getPolicy().hasPermission(user, UpdatePermission.class);
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DatasetAction extends QueryViewAction<DatasetFilterForm, QueryView>
    {
        private CohortFilter _cohortFilter;
//        private int _datasetId;
        private int _visitId;
        private String _encodedQcState;
        private DataSetDefinition _def;
        private Study _study;

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
                        _def = StudyManager.getInstance().getDataSetDefinition(getStudy(), id);
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
                        _def = StudyManager.getInstance().getDataSetDefinitionByEntityId(getStudy(), entityId);
                }
            }
            if (null == _def)
                throw new NotFoundException();
            return _def;
        }
        

        private Study getStudy() throws ServletException
        {
            if (null == _study)
                _study = StudyController.this.getStudy();
            return _study;
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
                    QueryService.get().getCustomView(getUser(), getContainer(), StudyManager.getSchemaName(), def.getLabel(), viewName) == null)
                {
                    ReportIdentifier reportId = AbstractReportIdentifier.fromString(viewName);
                    if (reportId != null && reportId.getReport() != null)
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

            Study study = getStudy();
            _cohortFilter = CohortFilter.getFromURL(getViewContext().getActionURL());
            _encodedQcState = form.getQCState();
            QCStateSet qcStateSet = null;
            if (StudyManager.getInstance().showQCStates(getContainer()))
                qcStateSet = QCStateSet.getSelectedStates(getContainer(), form.getQCState());
            ViewContext context = getViewContext();

            String export = StringUtils.trimToNull(context.getActionURL().getParameter("export"));

            String viewName = (String)context.get(DATASET_VIEW_NAME_PARAMETER_NAME);
//            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, _datasetId);
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
                visit = StudyManager.getInstance().getVisitForRowId(getStudy(), _visitId);
                if (null == visit)
                    throw new NotFoundException();
            }

            final StudyQuerySchema querySchema = new StudyQuerySchema((StudyImpl)study, getUser(), true);
            QuerySettings qs = querySchema.getSettings(context, DataSetQueryView.DATAREGION);
            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getLabel());
            DataSetQueryView queryView = new DataSetQueryView(def, querySchema, qs, visit, _cohortFilter, qcStateSet);
            queryView.setForExport(export != null);
            queryView.disableContainerFilterSelection();
            boolean showEditLinks = !QueryService.get().isQuerySnapshot(getContainer(), StudyManager.getSchemaName(), def.getLabel()) &&
                !def.isAssayData();
            queryView.setShowEditLinks(showEditLinks);

            final ActionURL url = context.getActionURL();
            setColumnURL(url, queryView, querySchema, def);

            // clear the property map cache and the sort map cache
            getParticipantPropsMap(context).clear();
            getDatasetSortColumnMap(context).clear();

            final TableInfo table = queryView.getTable();
            if (table != null)
            {
                addParticipantListToCache(context, def.getDataSetId(), viewName, generateParticipantList(queryView), _cohortFilter, form.getQCState());
                getExpandedState(context, def.getDataSetId()).clear();

                queryView.setShowSourceLinks(hasSourceLsids(table));
            }

            if (null != export)
            {
                if ("tsv".equals(export))
                    queryView.exportToTsv(context.getResponse());
                else if ("xls".equals(export))
                    queryView.exportToExcel(context.getResponse());
                return null;
            }

            List<ActionButton> buttonBar = new ArrayList<ActionButton>();
            populateButtonBar(buttonBar, def, queryView, _cohortFilter, qcStateSet);
            queryView.setButtons(buttonBar);

            StringBuffer sb = new StringBuffer();
            if (def.getDescription() != null && def.getDescription().length() > 0)
                sb.append(PageFlowUtil.filter(def.getDescription(), true, true)).append("<br/>");
            sb.append("<br/><span><b>View :</b> ").append(filter(getViewName())).append("</span>");
            if (_cohortFilter != null)
                sb.append("<br/><span><b>Cohort :</b> ").append(filter(_cohortFilter.getDescription(getContainer(), getUser()))).append("</span>");
            if (qcStateSet != null)
                sb.append("<br/><span><b>QC States:</b> ").append(filter(qcStateSet.getLabel())).append("</span>");
            HtmlView header = new HtmlView(sb.toString());

            HttpView view = new VBox(header, queryView);
            Report report = queryView.getSettings().getReportView();
            if (report != null && !ReportManager.get().canReadReport(getUser(), getContainer(), report))
            {
                return new HtmlView("User does not have read permission on this report.");
            }
            else if (report == null && !def.canRead(getUser()))
            {
                return new HtmlView("User does not have read permission on this dataset.");
            }
            return view;
        }

        protected QueryView createQueryView(DatasetFilterForm datasetFilterForm, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            QuerySettings qs = new QuerySettings(getViewContext(), DATASET_DATAREGION_NAME);
            Report report = qs.getReportView();
            if (report instanceof QueryReport)
            {
                return ((QueryReport)report).getQueryViewGenerator().generateQueryView(getViewContext(), report.getDescriptor());
            }
            return null;
        }

        private String getViewName()
        {
            QuerySettings qs = new QuerySettings(getViewContext(), DATASET_DATAREGION_NAME);
            if (qs.getViewName() != null)
                return qs.getViewName();
            else
            {
                Report report = qs.getReportView();
                if (report != null)
                    return report.getDescriptor().getReportName();
                else
                    return "default";
            }
        }

        private void populateButtonBar(List<ActionButton> buttonBar, DataSetDefinition def, DataSetQueryView queryView,
                                       CohortFilter cohortFilter, QCStateSet currentStates) throws ServletException
        {
            createViewButton(buttonBar, queryView);
            createCohortButton(buttonBar, cohortFilter);
            createParticipantListButton(buttonBar, queryView.getDataRegionName());
            if (StudyManager.getInstance().showQCStates(queryView.getContainer()))
                createQCStateButton(queryView, buttonBar, currentStates);

            buttonBar.add(queryView.createExportButton(false));

            buttonBar.add(queryView.createPageSizeMenuButton());

            User user = getUser();
            boolean canWrite = canWrite(def, user);
            boolean isSnapshot = QueryService.get().isQuerySnapshot(getContainer(), StudyManager.getSchemaName(), def.getLabel());
            boolean isAssayDataset = def.isAssayData();
            ExpProtocol protocol = null;

            if (isAssayDataset)
            {
                protocol = def.getAssayProtocol();
                if (protocol == null)
                    isAssayDataset = false;
            }

            if (!isSnapshot && canWrite && !isAssayDataset)
            {
                ActionButton insertButton = queryView.createInsertButton();
                if (insertButton != null)
                {
                    buttonBar.add(insertButton);
                }
            }

            if (!isSnapshot)
            {
                if (!isAssayDataset) // admins always get the import and manage buttons
                {
                    if ((user.isAdministrator() || canWrite))
                    {
                        // manage dataset
                        ActionButton manageButton = new ActionButton(new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", getDataSetDefinition().getDataSetId()), "Manage Dataset");
                        manageButton.setDisplayModes(DataRegion.MODE_GRID);
                        manageButton.setActionType(ActionButton.Action.LINK);
                        manageButton.setDisplayPermission(InsertPermission.class);
                        buttonBar.add(manageButton);

                        // bulk import
                        ActionURL importURL = new ActionURL(StudyController.ImportAction.class, def.getContainer());
                        importURL.addParameter(DataSetDefinition.DATASETKEY, def.getDataSetId());
                        ActionButton uploadButton = new ActionButton(importURL, "Import Data", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                        uploadButton.setDisplayPermission(InsertPermission.class);
                        buttonBar.add(uploadButton);
                    }

                    if (canWrite)
                    {
                        TableInfo tableInfo = new StudyQuerySchema(StudyController.this.getStudy(), getUser(), false).getTable(def.getLabel());
                        ActionURL deleteRowsURL = tableInfo.getDeleteURL(getContainer());
                        if (deleteRowsURL != AbstractTableInfo.LINK_DISABLER_ACTION_URL)
                        {
                            if (deleteRowsURL == null)
                            {
                                deleteRowsURL = new ActionURL(DeleteDatasetRowsAction.class, getContainer());
                            }
                            ActionButton deleteRows = new ActionButton(deleteRowsURL, "Delete");
                            deleteRows.setRequiresSelection(true, "Delete selected row from this dataset?", "Delete selected rows from this dataset?");
                            deleteRows.setActionType(ActionButton.Action.POST);
                            deleteRows.setDisplayPermission(DeletePermission.class);
                            buttonBar.add(deleteRows);
                        }
                    }
                }
                else if (isAssayDataset)
                {
                    List<ActionButton> buttons = AssayService.get().getImportButtons(protocol, getUser(), getContainer(), true);
                    buttonBar.addAll(buttons);

                    if (user.isAdministrator() || canWrite)
                    {
                        ActionURL deleteRowsURL = new ActionURL(DeletePublishedRowsAction.class, getContainer());
                        deleteRowsURL.addParameter("protocolId", protocol.getRowId());
                        ActionButton deleteRows = new ActionButton(deleteRowsURL, "Recall");
                        deleteRows.setRequiresSelection(true, "Recall selected row of this dataset?", "Recall selected rows of this dataset?");
                        deleteRows.setActionType(ActionButton.Action.POST);
                        deleteRows.setDisplayPermission(DeletePermission.class);
                        buttonBar.add(deleteRows);
                    }
                }
            }

            ActionURL viewSamplesURL = new ActionURL(SpecimenController.SelectedSamplesAction.class, getContainer());
            ActionButton viewSamples = new ActionButton(viewSamplesURL, "View Specimens");
            viewSamples.setRequiresSelection(true);
            viewSamples.setActionType(ActionButton.Action.POST);
            viewSamples.setDisplayPermission(ReadPermission.class);
            buttonBar.add(viewSamples);

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
                    buttonBar.add(viewAssayButton);
                }
            }
        }

        private void createViewButton(List<ActionButton> buttonBar, DataSetQueryView queryView)
        {
            MenuButton button = queryView.createViewButton(StudyReportUIProvider.getItemFilter());
            button.addMenuItem("Set Default View", getViewContext().cloneActionURL().setAction(ViewPreferencesAction.class));

            buttonBar.add(button);
        }

        private void createCohortButton(List<ActionButton> buttonBar, CohortFilter currentCohortFilter) throws ServletException
        {
            ActionButton cohortButton = CohortManager.getInstance().createCohortButton(getViewContext(), currentCohortFilter);
            if (cohortButton != null)
                buttonBar.add(cohortButton);
        }

        private void createParticipantListButton(List<ActionButton> buttonBar, String dataRegionName)
        {
            ActionButton listButton = ParticipantListManager.getInstance().createParticipantListButton(getViewContext(), getContainer(), dataRegionName);
            if (null != listButton)
                buttonBar.add(listButton);
        }

        private void createQCStateButton(DataSetQueryView view, List<ActionButton> buttonBar, QCStateSet currentSet)
        {
            List<QCStateSet> stateSets = QCStateSet.getSelectableSets(getContainer());
            MenuButton button = new MenuButton("QC State");

            for (QCStateSet set : stateSets)
            {
                NavTree setItem = new NavTree(set.getLabel(), getViewContext().cloneActionURL().replaceParameter(SharedFormParameters.QCState, set.getFormValue()).toString());
                setItem.setId("QCState:" + set.getLabel());
                if (set.equals(currentSet))
                    setItem.setSelected(true);
                button.addMenuItem(setItem);
            }
            if (getContainer().hasPermission(getUser(), AdminPermission.class))
            {
                button.addSeparator();
                ActionURL updateAction = new ActionURL(UpdateQCStateAction.class, getContainer());
                NavTree updateItem = button.addMenuItem("Update state of selected rows", "#", "if (verifySelected(document.forms[\"" +
                        view.getDataRegionName() + "\"], \"" + updateAction.getLocalURIString() + "\", \"post\", \"rows\")) document.forms[\"" +
                        view.getDataRegionName() + "\"].submit()");
                updateItem.setId("QCState:updateSelected");

                button.addMenuItem("Manage states", new ActionURL(ManageQCStatesAction.class,
                        getContainer()).addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString()));
            }
            buttonBar.add(button);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
            return _appendNavTrail(root, getDataSetDefinition().getDataSetId(), _visitId,  _cohortFilter, _encodedQcState);
            }
            catch (ServletException x)
            {
                return root;
            }
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
        protected Set<Role> getContextualRoles()
        {
            if (getViewContext().getUser() == User.getSearchUser())
                return Collections.singleton(RoleManager.getRole(ReaderRole.class));
            return null;
        }

        @Override
        public ModelAndView getView(ParticipantForm form, BindException errors) throws Exception
        {
            _form = form;
            _study = getStudy();
            if (null == _form.getParticipantId() || null == _study)
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
        ParticipantForm _bean;
        private CohortFilter _cohortFilter;

        public ModelAndView getView(ParticipantForm form, BindException errors) throws Exception
        {
            Study study = getStudy();
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

            _cohortFilter = CohortFilter.getFromURL(getViewContext().getActionURL());
            // display the next and previous buttons only if we have a cached participant index
            if (_cohortFilter != null && !StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                throw new UnauthorizedException("User does not have permission to view cohort information");

            List<String> participants = getParticipantListFromCache(getViewContext(), form.getDatasetId(), viewName, _cohortFilter, form.getQCState());
            if (participants != null)
            {
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
            return new StudyJspView<Object>(getStudy(), "uploadVisitMap.jsp", null, errors);
        }

        public void validateCommand(TSVForm target, Errors errors)
        {
        }

        public boolean handlePost(TSVForm form, BindException errors) throws Exception
        {
            VisitMapImporter importer = new VisitMapImporter();
            List<String> errorMsg = new LinkedList<String>();
            if (!importer.process(getUser(), getStudy(), form.getContent(), VisitMapImporter.Format.DataFax, errorMsg, _log))
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
            if (null != getStudy(true))
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
            return new StudyJspView<StudyPropertiesForm>(null, "createStudy.jsp", form, errors);
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
            createStudy(getStudy(true), getContainer(), getUser(), form);
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
            createStudy(getStudy(true), getContainer(), getUser(), form);
            updateRepositorySettings(getContainer(), form.isSimpleRepository());
            return true;
        }

        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            return new ActionURL(ManageStudyAction.class, getContainer());
        }
    }

    public static void createStudy(StudyImpl study, Container c, User user, StudyPropertiesForm form) throws SQLException, ServletException
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
            StudyManager.getInstance().createStudy(user, study);
        }
    }

    public static void updateRepositorySettings(Container c, boolean simple) throws SQLException
    {
        RepositorySettings reposSettings = SampleManager.getInstance().getRepositorySettings(c);
        reposSettings.setSimple(simple);
        reposSettings.setEnableRequests(!simple);
        SampleManager.getInstance().saveRepositorySettings(c, reposSettings);
    }

    @RequiresPermissionClass(ManageStudyPermission.class)
    public class ManageStudyAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            StudyPropertiesQueryView propView = new StudyPropertiesQueryView(getUser(), getStudy(), HttpView.currentContext(), true);

            return new StudyJspView<StudyPropertiesQueryView>(getStudy(true), "manageStudy.jsp", propView, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                Study study = getStudy();
                if (study != null)
                    root.addChild(study.getLabel(), new ActionURL(BeginAction.class, getContainer()));
            }
            catch (ServletException e)
            {
            }
            return root.addChild("Manage Study");
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
            return new StudyJspView<Object>(getStudy(), "confirmDeleteStudy.jsp", null, errors);
        }

        public boolean handlePost(DeleteStudyForm form, BindException errors) throws Exception
        {
            StudyManager.getInstance().deleteAllStudyData(getContainer(), getUser(), true, false);
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


    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateStudyPropertiesAction extends FormHandlerAction<StudyPropertiesForm>
    {
        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.getTimepointType() == TimepointType.DATE && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");

            target.setLabel(StringUtils.trimToNull(target.getLabel()));
            if (null == target.getLabel())
                errors.reject(ERROR_MSG, "Please supply a label");
        }

        public boolean handlePost(StudyPropertiesForm form, BindException errors) throws Exception
        {
            if (getStudy(true) != null)
            {
                StudyImpl updated = getStudy().createMutable();
                updated.setLabel(form.getLabel());
                StudyManager.getInstance().updateStudy(getUser(), updated);
            }
            return true;
        }

        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            try {
                if (getStudy(true) == null)
                    return new ActionURL(CreateStudyAction.class, getContainer());
            }
            catch (ServletException e){}

            return new ActionURL(ManageStudyAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageStudyPropertiesAction extends SimpleViewAction<StudyPropertiesForm>
    {
        public ModelAndView getView(StudyPropertiesForm form, BindException errors) throws Exception
        {
            Study study = getStudy(true);
            if (null == study)
            {
                CreateStudyAction action = (CreateStudyAction)initAction(this, new CreateStudyAction());
                return action.getView(form, false, errors);
            }
            return new StudyJspView<StudyPropertiesForm>(getStudy(), "manageStudyProperties.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Study Properties");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageVisitsAction extends FormViewAction<StudyPropertiesForm>
    {
        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.getTimepointType() == TimepointType.DATE && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");
        }

        public ModelAndView getView(StudyPropertiesForm form, boolean reshow, BindException errors) throws Exception
        {
            Study study = getStudy(true);
            if (null == study)
            {
                CreateStudyAction action = (CreateStudyAction)initAction(this, new CreateStudyAction());
                return action.getView(form, false, errors);
            }
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                return new HtmlView("<span class='labkey-error'>Unsupported operation for continuous study</span>");

            return new StudyJspView<StudyPropertiesForm>(getStudy(), _jspName(), form, errors);
        }

        public boolean handlePost(StudyPropertiesForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudy().createMutable();
            study.setStartDate(form.getStartDate());
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

        private String _jspName() throws ServletException
        {
            assert getStudy().getTimepointType() != TimepointType.CONTINUOUS;
            return getStudy().getTimepointType() == TimepointType.DATE ? "manageTimepoints.jsp" : "manageVisits.jsp";
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
            return new StudyJspView<ManageTypesAction>(getStudy(), "manageTypes.jsp", this, errors);
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
    public class ManageSitesAction extends FormViewAction<BulkEditForm>
    {
        public void validateCommand(BulkEditForm target, Errors errors)
        {
        }

        public ModelAndView getView(BulkEditForm bulkEditForm, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<StudyImpl>(getStudy(), "manageSites.jsp", getStudy(), errors);
            return view;
        }

        public boolean handlePost(BulkEditForm form, BindException errors) throws Exception
        {
            int[] ids = form.getIds();
            if (ids != null && ids.length > 0)
            {
                String[] labels = form.getLabels();
                Map<Integer, String> labelLookup = new HashMap<Integer, String>();
                for (int i = 0; i < ids.length; i++)
                    labelLookup.put(ids[i], labels[i]);

                boolean emptyLabel = false;
                for (SiteImpl site : getStudy().getSites())
                {
                    String label = labelLookup.get(site.getRowId());
                    if (label == null)
                        emptyLabel = true;
                    else if (!label.equals(site.getLabel()))
                    {
                        site = site.createMutable();
                        site.setLabel(label);
                        StudyManager.getInstance().updateSite(getUser(), site);
                    }
                }
                if (emptyLabel)
                {
                    errors.reject("manageSites", "Some site labels could not be updated: empty labels are not allowed.");
                }

            }
            if (form.getNewId() != null || form.getNewLabel() != null)
            {
                if (form.getNewId() == null)
                    errors.reject("manageSites", "Unable to create site: an ID is required for all sites.");
                else if (form.getNewLabel() == null)
                    errors.reject("manageSites", "Unable to create site: a label is required for all sites.");
                else
                {
                    try
                    {
                        SiteImpl site = new SiteImpl();
                        site.setLabel(form.getNewLabel());
                        site.setLdmsLabCode(Integer.parseInt(form.getNewId()));
                        site.setContainer(getContainer());
                        StudyManager.getInstance().createSite(getUser(), site);
                    }
                    catch (NumberFormatException e)
                    {
                        errors.reject("manageSites", "Unable to create site: ID must be an integer.");
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
            return root.addChild("Manage Sites");
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
                StudyImpl study = getStudy();
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
            StudyImpl study = getStudy();
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

            return new StudyJspView<VisitSummaryBean>(study, getVisitJsp("edit"), visitSummary, errors);
        }

        public boolean handlePost(VisitForm form, BindException errors) throws Exception
        {
            VisitImpl postedVisit = form.getBean();
            if (!getContainer().getId().equals(postedVisit.getContainer().getId()))
                throw new UnauthorizedException();

            // UNDONE: how do I get struts to handle this checkbox?
            postedVisit.setShowByDefault(null != StringUtils.trimToNull((String)getViewContext().get("showByDefault")));

            // UNDONE: reshow is broken for this form, but we have to validate
            TreeMap<Double, VisitImpl> visits = StudyManager.getInstance().getVisitManager(getStudy()).getVisitSequenceMap();
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

            HashMap<Integer,VisitDataSetType> visitTypeMap = new HashMap<Integer,VisitDataSetType>();
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
            return root.addChild(_v.getLabel());
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

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteVisitAction extends FormHandlerAction<IdForm>
    {
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        public boolean handlePost(IdForm form, BindException errors) throws Exception
        {
            int visitId = form.getId();
            Study study = getStudy();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            VisitImpl visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
            if (visit != null)
            {
                StudyManager.getInstance().deleteVisit(getStudy(), visit, getUser());
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
            StudyImpl study = getStudy();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            _visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
            if (null == _visit)
                throw new NotFoundException();

            ModelAndView view = new StudyJspView<VisitImpl>(study, "confirmDeleteVisit.jsp", _visit, errors);
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
                StudyImpl study = getStudy();
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
            StudyImpl study = getStudy();

            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            return new StudyJspView<VisitForm>(study, getVisitJsp("create"), form, errors);
        }

        public boolean handlePost(VisitForm form, BindException errors) throws Exception
        {
            VisitImpl visit = form.getBean();
            if (visit != null)
                StudyManager.getInstance().createVisit(getStudy(), getUser(), visit);
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
    public class UpdateDatasetVisitMappingAction extends FormViewAction<DataSetForm>
    {
        DataSetDefinition _def;

        public void validateCommand(DataSetForm form, Errors errors)
        {
            if (form.getDatasetId() < 1)
                errors.reject(SpringActionController.ERROR_MSG, "DatasetId must be greater than zero.");
        }

        public ModelAndView getView(DataSetForm form, boolean reshow, BindException errors) throws Exception
        {
            _def = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());

            if (_def == null)
            {
                BeginAction action = (BeginAction)initAction(this, new BeginAction());
                return action.getView(form, errors);
            }

            return new JspView<DataSetDefinition>("/org/labkey/study/view/updateDatasetVisitMapping.jsp", _def, errors);
        }

        public boolean handlePost(DataSetForm form, BindException errors) throws Exception
        {
            DataSetDefinition original = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
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

        public ActionURL getSuccessURL(DataSetForm dataSetForm)
        {
            return new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", dataSetForm.getDatasetId());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            if (_def != null)
            {
                try
                {
                    VisitManager visitManager = StudyManager.getInstance().getVisitManager(getStudy());
                    return root.addChild("Edit " + _def.getLabel() + " " + visitManager.getPluralLabel());
                }
                catch (ServletException se)
                {
                    throw new UnexpectedException(se);
                }
            }
            return root;
        }
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class ShowImportDatasetAction extends FormViewAction<ImportDataSetForm>
    {
        public void validateCommand(ImportDataSetForm target, Errors errors)
        {
        }

        @SuppressWarnings("deprecation")
        public ModelAndView getView(ImportDataSetForm form, boolean reshow, BindException errors) throws Exception
        {
            Study study = getStudy();
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, form.getDatasetId());

            if (null == def || def.getTypeURI() == null)
            {
                return new HtmlView("Error",
                        "Dataset is not yet defined. <a href=\"datasetDetails.view?id=%d\">Show Dataset Details</a>", form.getDatasetId());
            }

            if (null == PipelineService.get().findPipelineRoot(getContainer()))
                return new RequirePipelineView(getStudy(), true, errors);

            form.setTypeURI(StudyManager.getInstance().getDatasetType(getContainer(), form.getDatasetId()));

            if (form.getTypeURI() == null)
                throw new NotFoundException();

            form.setKeys(StringUtils.join(def.getDisplayKeyNames(), ", "));

            return new JspView<ImportDataSetForm>("/org/labkey/study/view/importDataset.jsp", form, errors);
        }

        public boolean handlePost(ImportDataSetForm form, BindException errors) throws Exception
        {
            String tsvData = form.getTsv();
            if (tsvData == null)
            {
                errors.reject("showImportDataset", "Form contains no data");
                return false;
            }
            String[] keys = new String[]{"ParticipantId", "SequenceNum"};

            if (null != PipelineService.get().findPipelineRoot(getContainer()))
            {
                String formKeys = StringUtils.trimToEmpty(form.getKeys());

                if (formKeys != null && formKeys.length() > 0)
                {
                    String[] keysPOST = formKeys.split(",");
                    if (keysPOST.length >= 1)
                        keys[0] = keysPOST[0];
                    if (keysPOST.length >= 2)
                        keys[1] = keysPOST[1];
                }

                Map<String,String> columnMap = new CaseInsensitiveHashMap<String>();
                columnMap.put(keys[0], DataSetDefinition.getParticipantIdURI());
                columnMap.put(keys[1], DataSetDefinition.getSequenceNumURI());
                // 2379
                // see DatasetBatch.prepareImport()
                columnMap.put("visit", DataSetDefinition.getSequenceNumURI());

                if (getStudy().getTimepointType() != TimepointType.VISIT)
                    columnMap.put("date", DataSetDefinition.getVisitDateURI());
                columnMap.put("ptid", DataSetDefinition.getParticipantIdURI());
                columnMap.put("qcstate", DataSetDefinition.getQCStateURI());
                columnMap.put("dfcreate", DataSetDefinition.getCreatedURI());     // datafax field name
                columnMap.put("dfmodify", DataSetDefinition.getModifiedURI());    // datafax field name
                List<String> errorList = new LinkedList<String>();

                DataSetDefinition dsd = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
                DataLoader dl = new TabLoader(tsvData, true);
                FileStream f = new FileStream.StringFileStream(tsvData);
                Pair<List<String>, UploadLog> result = AssayPublishManager.getInstance().importDatasetTSV(getUser(), getStudy(), dsd, dl, f, columnMap, errorList);

                if (!result.getKey().isEmpty())
                {
                    // Log the import
                    String comment = "Dataset data imported. " + result.getKey().size() + " rows imported";
                    StudyServiceImpl.addDatasetAuditEvent(
                            getUser(), getContainer(), dsd, comment, result.getValue());
                }

                for (String error : errorList)
                {
                    errors.reject("showImportDataset", error);
                }

                return errorList.isEmpty();
            }
            return false;
        }

        public ActionURL getSuccessURL(ImportDataSetForm form)
        {
            ActionURL url = new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
            if (StudyManager.getInstance().showQCStates(getContainer()))
                url.addParameter(SharedFormParameters.QCState, QCStateSet.getAllStates(getContainer()).getFormValue());
            return url;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Import Dataset");
        }
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class ImportAction extends AbstractQueryImportAction<ImportDataSetForm>
    {
        ImportDataSetForm _form = null;
        Study _study = null;
        DataSetDefinition _def = null;

        public ImportAction()
        {
            super(ImportDataSetForm.class);
        }

        @Override
        protected void initRequest(ImportDataSetForm form) throws ServletException
        {
            _form = form;
            _study = getStudy();
            if (null == _study)
                throw new NotFoundException("Container does not contain a study.");

            _def = StudyManager.getInstance().getDataSetDefinition(_study, form.getDatasetId());
            if (null == _def)
               throw new NotFoundException("Dataset not found");
            if (null == _def.getTypeURI())
                return;

            User user = getViewContext().getUser();
            TableInfo t = new StudyQuerySchema((StudyImpl)_study, user, true).getDataSetTable(_def);
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
        protected int importData(DataLoader dl, FileStream file, BatchValidationException errors) throws IOException
        {
            if (null == PipelineService.get().findPipelineRoot(getContainer()))
            {
                errors.addRowError(new ValidationException("Pipeline file system is not setup."));
                return -1;
            }

            Map<String,String> columnMap = new CaseInsensitiveHashMap<String>();
            // 2379
            // see DatasetBatch.prepareImport()
            columnMap.put("visit", DataSetDefinition.getSequenceNumURI());

            if (_study.getTimepointType() != TimepointType.VISIT)
                columnMap.put("date", DataSetDefinition.getVisitDateURI());
            columnMap.put("ptid", DataSetDefinition.getParticipantIdURI());
            columnMap.put("qcstate", DataSetDefinition.getQCStateURI());
            columnMap.put("dfcreate", DataSetDefinition.getCreatedURI());     // datafax field name
            columnMap.put("dfmodify", DataSetDefinition.getModifiedURI());    // datafax field name
            List<String> errorList = new LinkedList<String>();


            Pair<List<String>, UploadLog> result;
            try
            {
                result = AssayPublishManager.getInstance().importDatasetTSV(getUser(), (StudyImpl)_study, _def, dl, file, columnMap, errorList);
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

            for (String error : errorList)
            {
                errors.addRowError(new ValidationException(error));
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
            ActionURL overviewURL = getStudyOverviewURL();
            root.addChild("Study Overview", overviewURL);
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
            return new StudyJspView<BulkImportTypesForm>(getStudy(), "bulkImportDataTypes.jsp", form, errors);
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
            
            SchemaReader reader = new SchemaTsvReader(getStudy(), form.tsv, form.getLabelColumn(), form.getTypeNameColumn(), form.getTypeIdColumn(), errors);
            return StudyManager.getInstance().importDatasetSchemas(getStudy(), getUser(), reader, errors);
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
        String _datasetName;

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

            SimpleFilter filter = new SimpleFilter("container", getContainer().getId());
            if (form.getId() != 0)
            {
                filter.addCondition(DataSetDefinition.DATASETKEY, form.getId());
                _datasetName = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getId()).getLabel();
            }

            gv.setFilter(filter);
            return gv;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Upload History" + (null != _datasetName ? " for " + _datasetName : ""));
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class DownloadTsvAction extends SimpleViewAction<IdForm>
    {
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            UploadLog ul = AssayPublishManager.getInstance().getUploadLog(getContainer(), form.getId());
            PageFlowUtil.streamFile(getViewContext().getResponse(), ul.getFilePath(), true);

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
            final StudyImpl study = getStudy();
            final ViewContext context = getViewContext();

            VBox view = new VBox();

            int datasetId = NumberUtils.toInt((String)context.get(DataSetDefinition.DATASETKEY), -1);
            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

            if (def != null)
            {
                final StudyQuerySchema querySchema = new StudyQuerySchema(study, getUser(), true);
                QuerySettings qs = querySchema.getSettings(context, DataSetQueryView.DATAREGION, def.getLabel());

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
                    ActionButton deleteRows = new ActionButton(deleteURL, "Recall Rows");

                    deleteRows.setRequiresSelection(true, "Recall selected row of this dataset?", "Recall selected rows of this dataset?");
                    deleteRows.setActionType(ActionButton.Action.POST);
                    deleteRows.setDisplayPermission(DeletePermission.class);

                    PublishedRecordQueryView qv = new PublishedRecordQueryView(def, querySchema, qs, sourceLsid,
                            NumberUtils.toInt(protocolId), NumberUtils.toInt(recordCount));
                    qv.setButtons(Collections.singletonList(deleteRows));
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
            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
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
            MultiMap<String,String> sourceLsid2datasetLsid = new MultiHashMap<String,String>();


            if (originalSourceLsid != null)
            {
                sourceLsid2datasetLsid.putAll(originalSourceLsid, allLsids);
            }
            else
            {
                Map<String,Object>[] data = StudyService.get().getDatasetRows(getUser(), getContainer(), form.getDatasetId(), allLsids);
                for (Map<String,Object> row : data)
                {
                    sourceLsid2datasetLsid.put(row.get("sourcelsid").toString(), row.get("lsid").toString());
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

                    AuditLogEvent event = new AuditLogEvent();

                    event.setCreatedBy(getUser());
                    event.setEventType(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);
                    event.setContainerId(sourceContainer.getId());
                    event.setKey1(getContainer().getId());

                    String assayName = def.getLabel();
                    ExpProtocol protocol = def.getAssayProtocol();
                    if (protocol != null)
                        assayName = protocol.getName();

                    event.setIntKey1(NumberUtils.toInt(protocolId));
                    Collection<String> lsids = entry.getValue();
                    event.setComment(lsids.size() + " row(s) were recalled to the assay: " + assayName);

                    Map<String,Object> dataMap = Collections.<String,Object>singletonMap(DataSetDefinition.DATASETKEY, form.getDatasetId());

                    AssayAuditViewFactory.getInstance().ensureDomain(getUser());
                    AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT));
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
            StudyImpl study = getStudy();
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
                List<Map<String, Object>> keys = new ArrayList<Map<String, Object>>(lsids.size());
                for (String lsid : lsids)
                    keys.add(Collections.<String, Object>singletonMap("lsid", lsid));

                StudyQuerySchema schema = new StudyQuerySchema(study, getViewContext().getUser(), true);
                TableInfo datasetTable = schema.getDataSetTable((DataSetDefinition)dataset);

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
                              final UserSchema querySchema, final DataSet def)
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
        for (DisplayColumn col : columns)
        {
            String subjectColName = StudyService.get().getSubjectColumnName(def.getContainer());
            if (subjectColName.equalsIgnoreCase(col.getName()))
            {
                col.getColumnInfo().setFk(new QueryForeignKey(querySchema, StudyService.get().getSubjectTableName(def.getContainer()),
                        subjectColName, subjectColName)
                {
                    public StringExpression getURL(ColumnInfo parent)
                    {
                        ActionURL base = new ActionURL(ParticipantAction.class, querySchema.getContainer());
                        base.addParameter(DataSetDefinition.DATASETKEY, Integer.toString(def.getDataSetId()));

                        // push any filter, sort params, and viewname
                        for (Pair<String, String> param : url.getParameters())
                        {
                            if ((param.getKey().contains(".sort")) ||
                                (param.getKey().contains("~")) ||
                                (CohortFilter.isCohortFilterParameterName(param.getKey())) ||
                                (SharedFormParameters.QCState.name().equals(param.getKey())) ||
                                (DATASET_VIEW_NAME_PARAMETER_NAME.equals(param.getKey())))
                            {
                                base.addParameter(param.getKey(), param.getValue());
                            }
                        }
                        return new DetailsURL(base, "participantId", parent.getFieldKey());
                    }
                });
                return;
            }
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

    private static final String PARTICIPANT_PROPS_CACHE = "Study_participants/propertyCache";
    private static final String DATASET_SORT_COLUMN_CACHE = "Study_participants/datasetSortColumnCache";
    @SuppressWarnings("unchecked")
    private static Map<String, PropertyDescriptor[]> getParticipantPropsMap(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, PropertyDescriptor[]> map = (Map<String, PropertyDescriptor[]>) session.getAttribute(PARTICIPANT_PROPS_CACHE);
        if (map == null)
        {
            map = new HashMap<String, PropertyDescriptor[]>();
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
            map = new HashMap<String, Map<String, Integer>>();
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
            QueryDefinition qd = QueryService.get().getQueryDef(context.getUser(), dsd.getContainer(), "study", dsd.getLabel());
            if (qd == null)
            {
                UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
                qd = schema.getQueryDefForTable(dsd.getLabel());
            }
            CustomView cview = qd.getCustomView(context.getUser(), context.getRequest(), null);
            if (cview != null)
            {
                sortMap = new HashMap<String, Integer>();
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
        return new CaseInsensitiveHashMap<Integer>(sortMap);
    }

    private static String getParticipantListCacheKey(int dataset, String viewName, CohortFilter cohortFilter, String encodedQCState)
    {
        String key = Integer.toString(dataset);
        // if there is also a view associated with the dataset, incorporate it into the key as well
        if (viewName != null && !StringUtils.isEmpty(viewName))
            key = key + viewName;
        if (cohortFilter != null)
            key = key + "cohort" + cohortFilter.getType().name()  + cohortFilter.getCohortId();
        if (encodedQCState != null)
            key = key + "qcState" + encodedQCState;
        return key;
    }

    public static void addParticipantListToCache(ViewContext context, int dataset, String viewName, List<String> participants, CohortFilter cohortFilter, String encodedQCState)
    {
        Map<String, List<String>> map = getParticipantMapFromCache(context);
        map.put(getParticipantListCacheKey(dataset, viewName, cohortFilter, encodedQCState), participants);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> getParticipantMapFromCache(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, List<String>> map = (Map<String, List<String>>) session.getAttribute(PARTICIPANT_CACHE_PREFIX);
        if (map == null)
        {
            map = new HashMap<String, List<String>>();
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
            map = new HashMap<Integer, Map<Integer, String>>();
            session.setAttribute(EXPAND_CONTAINERS_KEY, map);
        }

        Map<Integer, String> expandedMap = map.get(datasetId);
        if (expandedMap == null)
        {
            expandedMap = new HashMap<Integer, String>();
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
        try {
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
            VisitImpl visit = null;
            if (visitRowId != 0)
            {
                visit = studyMgr.getVisitForRowId(study, visitRowId);
                if (null == visit)
                    return Collections.emptyList();
            }
            StudyQuerySchema querySchema = new StudyQuerySchema(study, context.getUser(), true);
            QuerySettings qs = querySchema.getSettings(context, DataSetQueryView.DATAREGION, def.getLabel());
            qs.setViewName(viewName);
            DataSetQueryView queryView = new DataSetQueryView(def, querySchema, qs, visit, cohortFilter, qcStateSet);

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
            Set<String> participantSet = new LinkedHashSet<String>();
            FieldKey ptidKey = new FieldKey(null,StudyService.get().getSubjectColumnName(queryView.getContainer()));
            ResultSet rs = null;

            try
            {
                Results r = queryView.getResults(ShowRows.PAGINATED);
                rs = r.getResultSet();
                ColumnInfo ptidColumnInfo = r.getFieldMap().get(ptidKey);
                int ptidIndex = (null != ptidColumnInfo) ? rs.findColumn(ptidColumnInfo.getAlias()) : 0;
                while (rs.next() && ptidIndex > 0)
                {
                    String ptid = rs.getString(ptidIndex);
                    participantSet.add(ptid);
                }
                return new ArrayList<String>(participantSet);
            }
            catch (Exception e)
            {
                throw new UnexpectedException(e);
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
            // for date-based studies, values can be negative, but it's not allowed in visit-based studies
            if ((visit.getSequenceNumMin() < 0 || visit.getSequenceNumMax() < 0) && study.getTimepointType() == TimepointType.VISIT)
                errors.reject(null, "Sequence numbers must be greater than or equal to zero.");
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
            return new JspView<ManageQCStatesBean>("/org/labkey/study/view/manageQCStates.jsp", 
                    new ManageQCStatesBean(getStudy(), manageQCStatesForm.getReturnUrl()), errors);
        }

        public void validateCommand(ManageQCStatesForm form, Errors errors)
        {
            Set<String> labels = new HashSet<String>();
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
                Set<Integer> publicDataSet = new HashSet<Integer>();
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

            updateQcState(getStudy(), getUser(), form);

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
            StudyImpl study = getStudy();
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
            QuerySettings qs = new QuerySettings(getViewContext(), DataSetQueryView.DATAREGION);
            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getLabel());
            qs.setMaxRows(Table.ALL_ROWS);
            final Set<String> finalLsids = lsids;
            DataSetQueryView queryView = new DataSetQueryView(def, querySchema, qs, null, null, null)
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
                    filter.addInClause("lsid", new ArrayList<String>(finalLsids));
                    return view;
                }
            };
            queryView.setShowDetailsColumn(false);
            queryView.setShowSourceLinks(false);
            queryView.setShowEditLinks(false);
            updateQCForm.setQueryView(queryView);
            updateQCForm.setDataRegionSelectionKey(DataRegionSelection.getSelectionKeyFromRequest(getViewContext()));
            return new JspView<UpdateQCStateForm>("/org/labkey/study/view/updateQCState.jsp", updateQCForm, errors);
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
        protected BaseRemoteService createService()
        {
            try
            {
                return new DatasetServiceImpl(getViewContext(), getStudy(), StudyManager.getInstance());
            }
            catch (ServletException se)
            {
                throw new UnexpectedException(se);
            }
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
        Study study;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            study = getStudy();
            return new StudyJspView<Object>(getStudy(), "manageUndefinedTypes.jsp", o, errors);
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
            Study study = getStudy();
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

            Set<String> ignoreColumns = new CaseInsensitiveHashSet("createdby", "modifiedby", "lsid", "participantsequencekey", "datasetid", "visitdate", "sourcelsid", "created", "modified", "visitrowid", "day", "qcstate", "dataset");
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
                    ignoreColumns.add(col.getMvColumnName());
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
            study = getStudy();


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
                    return new StudyJspView<ViewPrefsBean>(study, "viewPreferences.jsp", bean, errors);
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

            File lockFile = StudyPipeline.lockForDataset(getStudy(), definitionFile);

            if (!definitionFile.canRead())
                errors.reject("importStudyBatch", "Can't read dataset file: " + path);
            if (lockFile.exists())
                errors.reject("importStudyBatch", "Lock file exists.  Delete file before running import. " + lockFile.getName());

            DatasetFileReader reader = new DatasetFileReader(definitionFile, getStudy());

            if (!errors.hasErrors())
            {
                List<String> parseErrors = new ArrayList<String>();
                reader.validate(parseErrors);
                for (String error : parseErrors)
                    errors.reject("importStudyBatch", error);
            }

            return new StudyJspView<ImportStudyBatchBean>(
                    getStudy(), "importStudyBatch.jsp", new ImportStudyBatchBean(reader, path), errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                root.addChild(getStudy().getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
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
            Study study = getStudy();
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
                DatasetImportUtils.submitStudyBatch(study, f, c, getUser(), getViewContext().getActionURL(), root);
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
    public class ImportStudyAction extends FormViewAction<Object>
    {
        private ActionURL _redirect;
        private boolean _reload;

        public void validateCommand(Object o, Errors errors)
        {
        }

        public ModelAndView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainer();
            Study study = StudyManager.getInstance().getStudy(c);
            setHelpTopic(new HelpTopic("importExportStudy"));
            _reload = null != study;

            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
            {
                return new PipelineSetupView((_reload ? "reloading" : "importing") + " a study");
            }
            else
            {
                return new JspView<Boolean>("/org/labkey/study/view/importStudy.jsp", _reload, errors);
            }
        }

        // This handles the case of posting a .zip archive directly (not using the pipeline)
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();

            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
            {
                return false;   // getView() will show an appropriate message in this case
            }

            // Assuming success starting the import process, redirect to pipeline status
            _redirect = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);

            Map<String, MultipartFile> map = getFileMap();

            if (map.isEmpty())
            {
                errors.reject("studyImport", "You must select a .study.zip file to import.");
            }
            else if (map.size() > 1)
            {
                errors.reject("studyImport", "Only one file is allowed.");
            }
            else
            {
                MultipartFile file = map.values().iterator().next();

                if (0 == file.getSize() || StringUtils.isBlank(file.getOriginalFilename()))
                {
                    errors.reject("studyImport", "You must select a .study.zip file to import.");
                }
                else
                {
                    InputStream is = file.getInputStream();

                    File zipFile = File.createTempFile("study", ".zip");
                    FileUtil.copyData(is, zipFile);
                    importStudy(errors, zipFile, file.getOriginalFilename());
                }
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(Object o)
        {
            return _redirect;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild((_reload ? "Reload" : "Import") + " Study");
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

            boolean success = importStudy(errors, studyFile, studyFile.getName());

            if (success && !errors.hasErrors())
            {
                return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);
            }
            else
            {
                ObjectError firstError = (ObjectError)errors.getAllErrors().get(0);
                throw new StudyImportException(firstError.getDefaultMessage());
            }
        }

        @Override
        protected ModelAndView getErrorView(Exception e, BindException errors) throws Exception
        {
            try
            {
                throw e;
            }
            catch (StudyImportException sie)
            {
                errors.reject("studyImport", e.getMessage());
                return new SimpleErrorView(errors);
            }
        }
    }


    public boolean importStudy(BindException errors, File studyFile, String originalFilename) throws ServletException, SQLException, IOException, ParserConfigurationException, SAXException, XmlException
    {
        Container c = getContainer();
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

        User user = getUser();
        ActionURL url = getViewContext().getActionURL();

        PipelineService.get().queueJob(new StudyImportJob(c, user, url, studyXml, originalFilename, errors, pipelineRoot));

        return !errors.hasErrors();
    }


    private static class PipelineSetupView extends JspView<String>
    {
        private PipelineSetupView(String actionDescription)
        {
            super("/org/labkey/study/view/pipelineSetup.jsp", actionDescription);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ExportStudyAction extends FormViewAction<ExportForm>
    {
        private ActionURL _successURL = null;

        public ModelAndView getView(ExportForm form, boolean reshow, BindException errors) throws Exception
        {
            // In export-to-browser case, base action will attempt to reshow the view since we returned null as the success
            // URL; returning null here causes the base action to stop pestering the action. 
            if (reshow)
                return null;

            if (PipelineService.get().hasValidPipelineRoot(getContainer()))
                return new JspView<ExportForm>("/org/labkey/study/view/exportStudy.jsp", form, errors);
            else
                return new PipelineSetupView("exporting a study");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Export Study");
        }

        public void validateCommand(ExportForm form, Errors errors)
        {
        }

        public boolean handlePost(ExportForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudy();
            StudyWriter writer = new StudyWriter();
            StudyExportContext ctx = new StudyExportContext(getStudy(), getUser(), getContainer(), "old".equals(form.getFormat()), PageFlowUtil.set(form.getTypes()), Logger.getLogger(StudyWriter.class));

            switch(form.getLocation())
            {
                case 0:
                {
                    PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                    if (root == null || !root.isValid())
                    {
                        throw new NotFoundException("No valid pipeline root found");
                    }
                    File exportDir = root.resolvePath("export");
                    writer.write(study, ctx, new FileSystemFile(exportDir));
                    _successURL = new ActionURL(ManageStudyAction.class, getContainer());
                    break;
                }
                case 1:
                {
                    PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                    if (root == null || !root.isValid())
                    {
                        throw new NotFoundException("No valid pipeline root found");
                    }
                    File exportDir = root.resolvePath("export");
                    exportDir.mkdir();
                    ZipFile zip = new ZipFile(exportDir, FileUtil.makeFileNameWithTimestamp(study.getLabel(), "study.zip"));
                    writer.write(study, ctx, zip);
                    zip.close();
                    _successURL = new ActionURL(ManageStudyAction.class, getContainer());
                    break;
                }
                case 2:
                {

                    ZipFile zip = new ZipFile(getViewContext().getResponse(), FileUtil.makeFileNameWithTimestamp(study.getLabel(), "study.zip"));
                    writer.write(study, ctx, zip);
                    zip.close();
                    break;
                }
            }

            return true;
        }

        public ActionURL getSuccessURL(ExportForm form)
        {
            return _successURL;
        }
    }

    public static class ExportForm
    {
        private String[] _types;
        private int _location;
        private String _format;

        public String[] getTypes()
        {
            return _types;
        }

        public void setTypes(String[] types)
        {
            _types = types;
        }

        public int getLocation()
        {
            return _location;
        }

        public void setLocation(int location)
        {
            _location = location;
        }

        public String getFormat()
        {
            return _format;
        }

        public void setFormat(String format)
        {
            _format = format;
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
                DataSetDefinition dataset = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
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
                    int numRowsDeleted = StudyManager.getInstance().purgeDataset(getStudy(), dataset, getUser());
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
            return new StudyJspView<StudyImpl>(getStudy(), "typeNotFound.jsp", null, errors);
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
            StudyManager.getInstance().getVisitManager(getStudy()).updateParticipantVisits(getUser(), getStudy().getDataSets());

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
            return new StudyJspView<Object>(getStudy(), "visitOrder.jsp", reorderForm, errors);
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
                order = new HashMap<Integer, Integer>();
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

        private Map<Integer, Integer> getVisitIdToZeroMap(VisitImpl[] visits) throws ServletException
        {
            Map<Integer, Integer> order = new HashMap<Integer, Integer>();
            for (VisitImpl visit : visits)
                order.put(visit.getRowId(), 0);
            return order;
        }

        public boolean handlePost(VisitReorderForm form, BindException errors) throws Exception
        {
            Map<Integer, Integer> displayOrder = null;
            Map<Integer, Integer> chronologicalOrder = null;
            VisitImpl[] visits = StudyManager.getInstance().getVisits(getStudy(), Visit.Order.SEQUENCE_NUM);

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
            if (getStudy().isAdvancedCohorts())
                CohortManager.getInstance().updateParticipantCohorts(getUser(), getStudy());
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
            return new StudyJspView<Object>(getStudy(), "visitVisibility.jsp", visitPropertyForm, errors);
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
            Set<Integer> visible = new HashSet<Integer>(visibleIds.length);
            for (int id : visibleIds)
                visible.add(id);
            if (allIds.length != form.getLabel().length)
                throw new IllegalStateException("Arrays must be the same length.");
            for (int i = 0; i < allIds.length; i++)
            {
                VisitImpl def = StudyManager.getInstance().getVisitForRowId(getStudy(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String label = form.getLabel()[i];
                String typeStr = form.getExtraData()[i];
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
    public class DataSetVisibilityAction extends FormViewAction<DatasetPropertyForm>
    {
        public ModelAndView getView(DatasetPropertyForm form, boolean reshow, BindException errors) throws Exception
        {
            Map<Integer, DatasetVisibilityData> bean = new HashMap<Integer,DatasetVisibilityData>();
            for (DataSet def : getStudy().getDataSets())
            {
                DatasetVisibilityData data = new DatasetVisibilityData();
                data.label = def.getLabel();
                data.category = def.getCategory();
                data.cohort = def.getCohortId();
                data.visible = def.isShowByDefault();
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
                Set<Integer> visible = new HashSet<Integer>(visibleIds.length);
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
                    data.label = labels[i];
                    data.category = categories[i];
                    data.cohort = cohorts[i] == -1 ? null : cohorts[i];
                    data.visible = visible.contains(id);
                }
            }
            return new StudyJspView<Map<Integer,StudyController.DatasetVisibilityData>>(
                    getStudy(), "dataSetVisibility.jsp", bean, errors);
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

            Set<String> labels = new HashSet<String>();
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
            Set<Integer> visible = new HashSet<Integer>(visibleIds.length);
            for (int id : visibleIds)
                  visible.add(id);
            for (int i = 0; i < allIds.length; i++)
            {
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String[] extraData = form.getExtraData();
                String category = extraData == null ? null : extraData[i];
                Integer cohortId = null;
                if (form.getCohort() != null)
                    cohortId = form.getCohort()[i];
                if (cohortId != null && cohortId.intValue() == -1)
                    cohortId = null;
                String label = form.getLabel()[i];
                if (def.isShowByDefault() != show || !nullSafeEqual(category, def.getCategory()) || !nullSafeEqual(label, def.getLabel()) || !BaseStudyController.nullSafeEqual(cohortId, def.getCohortId()))
                {
                    def = def.createMutable();
                    def.setShowByDefault(show);
                    def.setCategory(category);
                    def.setCohortId(cohortId);
                    def.setLabel(label);
                    StudyManager.getInstance().updateDataSetDefinition(getUser(), def);
                }
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
        public String category;
        public Integer cohort; // null for none
        public boolean visible;
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DataSetDisplayOrderAction extends FormViewAction<DatasetReorderForm>
    {
        public ModelAndView getView(DatasetReorderForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudy(), "dataSetDisplayOrder.jsp", form, errors);
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

            if (order != null && order.length() > 0)
            {
                String[] ids = order.split(",");
                List<Integer> orderedIds = new ArrayList<Integer>(ids.length);

                for (String id : ids)
                    orderedIds.add(Integer.parseInt(id));

                DatasetReorderer reorderer = new DatasetReorderer(getStudy(), getUser());
                reorderer.reorderDatasets(orderedIds);
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
            DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getId());
            if (null == ds)
                redirectTypeNotFound(form.getId());

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try
            {
                scope.ensureTransaction();
                StudyManager.getInstance().deleteDataset(getStudy(), getUser(), ds);
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
            Study study = getStudy();
            CustomParticipantView view = StudyManager.getInstance().getCustomParticipantView(study);
            if (view != null)
            {
                form.setCustomScript(view.getBody());
                form.setUseCustomView(view.isActive());
                form.setEditable(!view.isModuleParticipantView());
            }

            return new JspView<CustomizeParticipantViewForm>("/org/labkey/study/view/customizeParticipantView.jsp", form);
        }

        public boolean handlePost(CustomizeParticipantViewForm form, BindException errors) throws Exception
        {
            Study study = getStudy();
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
            studyFolder.setFolderType(ModuleLoader.getInstance().getFolderType(StudyFolderType.NAME));

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

            List<DataSetDefinition> datasets = new ArrayList<DataSetDefinition>();

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
                List<Portal.WebPart> webParts = new ArrayList<Portal.WebPart>();
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
                Portal.saveParts(studyFolder, studyFolder.getId(), webParts.toArray(new Portal.WebPart[webParts.size()]));
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
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName().toString(), name);
                if (def != null)
                {
                    errors.reject("snapshotQuery.error", "A Snapshot with the same name already exists");
                    return;
                }

                // check for a dataset with the same name unless it's one that we created
                DataSet dataset = StudyManager.getInstance().getDataSetDefinition(StudyManager.getInstance().getStudy(getContainer()), name);
                if (dataset != null)
                {
                    if (dataset.getDataSetId() != form.getSnapshotDatasetId())
                        errors.reject("snapshotQuery.error", "A Dataset with the same name already exists");
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
                StudyImpl study = getStudy();
                DataSet dsDef = StudyManager.getInstance().getDataSetDefinition(study, form.getSnapshotName());

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
                    StudyManager.getInstance().deleteDataset(study, getUser(), dsDef);
                    form.setSnapshotDatasetId(-1);
                }
            }
        }

        private void createDataset(StudySnapshotForm form, BindException errors) throws Exception
        {
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            DataSet dsDef = StudyManager.getInstance().getDataSetDefinition(study, form.getSnapshotName());

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

                    String domainURI = def.getTypeURI();
                    OntologyManager.ensureDomainDescriptor(domainURI, form.getSnapshotName(), form.getViewContext().getContainer());
                    Domain d = PropertyService.get().getDomain(form.getViewContext().getContainer(), domainURI);

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
            DbSchema schema = StudyManager.getSchema();

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
                form.init(QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName().toString(), form.getSnapshotName()), getUser());

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
                    StudyImpl study = getStudy();
                    DataSet dsDef = StudyManager.getInstance().getDataSetDefinition(study, form.getSnapshotName());

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
                    HttpView view = new StudyGWTView(StudyApplication.GWTModule.DatasetDesigner, props);

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
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName().toString(), form.getSnapshotName());
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

        public String getOrder() {return order;}

        public void setOrder(String order) {this.order = order;}
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
        Map<String, String> viewMap = PropertyManager.getProperties(context.getUser().getUserId(),
                context.getContainer().getId(), DEFAULT_DATASET_VIEW);

        final String key = Integer.toString(datasetId);
        if (viewMap.containsKey(key))
        {
            return viewMap.get(key);
        }
        return "";
    }

    private void setDefaultView(ViewContext context, int datasetId, String view)
    {
        Map<String, String> viewMap = PropertyManager.getWritableProperties(context.getUser().getUserId(),
                context.getContainer().getId(), DEFAULT_DATASET_VIEW, true);

        viewMap.put(Integer.toString(datasetId), view);
        PropertyManager.saveProperties(viewMap);
    }

    private String getVisitLabel()
    {
        try
        {
            return StudyManager.getInstance().getVisitManager(getStudy()).getLabel();
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
            return StudyManager.getInstance().getVisitManager(getStudy()).getPluralLabel();
        }
        catch (ServletException e)
        {
            return "Visits";
        }
    }

    private String getVisitJsp(String prefix) throws ServletException
    {
        assert getStudy().getTimepointType() != TimepointType.CONTINUOUS;
        return prefix + (getStudy().getTimepointType() == TimepointType.DATE ? "Timepoint" : "Visit") + ".jsp";
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

        public String getParticipantId(){return participantId;}

        public void setParticipantId(String participantId){this.participantId = participantId;}

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

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
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

            if (_showCustomizeLink && c.hasPermission(getViewContext().getUser(), AdminPermission.class))
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

    public static class DataSetForm
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
            try
            {
                Study study = getStudy();
                if (study != null)
                    root.addChild(study.getLabel(), new ActionURL(BeginAction.class, getContainer()));
            }
            catch (ServletException e)
            {
            }
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
            try
            {
                Study study = getStudy();
                if (study != null)
                    root.addChild(study.getLabel(), new ActionURL(BeginAction.class, getContainer()));
            }
            catch (ServletException e)
            {
            }
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
            return new StudyJspView<ReloadForm>(getStudy(), "manageReload.jsp", form, errors);
        }

        public boolean handlePost(ReloadForm form, final BindException errors) throws Exception
        {
            StudyImpl study = getStudy();

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
            ReloadTask task = new ReloadTask(getContainer().getId());
            String message;

            try
            {
                ReloadStatus status = task.attemptReload();

                if (status.isReloadQueued() && form.isUi())
                    return HttpView.redirect(PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer()));

                message = status.getMessage();
            }
            catch (StudyImportException e)
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



    @RequiresPermissionClass(AdminPermission.class)
    public class TestUpgradeAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DataSetDefinition.upgradeAll();
            return new HtmlView("OK");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
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
            return new JspView<ImportMappingBean>("/org/labkey/study/view/visitImportMapping.jsp", new ImportMappingBean(getStudy()));
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

        public ImportMappingBean(Study study) throws SQLException
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
            return new JspView<Object>("/org/labkey/study/view/importVisitAliases.jsp", null, errors);
        }

        @Override
        public boolean handlePost(VisitAliasesForm form, BindException errors) throws Exception
        {
            boolean hadCustomMapping = !StudyManager.getInstance().getCustomVisitImportMapping(getStudy()).isEmpty();

            try
            {
                StudyManager.getInstance().importVisitAliases(getStudy(), getUser(), new TabLoader(form.getTsv(), true));
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

            // TODO: Change to audit log
            _log.info("The visit import custom mapping was " + (hadCustomMapping ? "replaced" : "imported"));

            return true;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailVisitAdmin(root);
            return root.addChild("Import Visit Alises");
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
            StudyManager.getInstance().clearVisitAliases(getStudy());
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

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageParticipantClassificationsAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/study/view/manageParticipantClassifications.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                _appendManageStudy(root);
                root.addChild("Manage " + getStudy().getSubjectNounSingular() + " Classifications");
            }
            catch (ServletException e)
            {
            }
            return root;
        }
    }
}
