    /*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.*;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.*;
import org.labkey.api.view.template.AppBar;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:01:17 PM
*/
@RequiresPermissionClass(InsertPermission.class)
public class UploadWizardAction<FormType extends AssayRunUploadForm<ProviderType>, ProviderType extends AssayProvider> extends BaseAssayAction<FormType>
{
    protected ExpProtocol _protocol;

    private Map<String, StepHandler<FormType>> _stepHandlers = new HashMap<String, StepHandler<FormType>>();

    protected String _stepDescription;

    public UploadWizardAction()
    {
        this(AssayRunUploadForm.class);
    }

    public UploadWizardAction(Class formClass)
    {
        super(formClass);
        addStepHandler(getBatchStepHandler());
        addStepHandler(getRunStepHandler());
    }

    protected StepHandler<FormType> getBatchStepHandler()
    {
        return new BatchStepHandler();
    }

    protected RunStepHandler getRunStepHandler()
    {
        return new RunStepHandler();
    }

    protected void addStepHandler(StepHandler<FormType> stepHandler)
    {
        _stepHandlers.put(stepHandler.getName(), stepHandler);
    }

    public ModelAndView getView(FormType form, BindException errors) throws Exception
    {
        _protocol = form.getProtocol();
        String currentStep = form.getUploadStep();

        if (currentStep == null)
        {
            //FIX: 4014. ensure that the pipeline root path actually exists before starting the first
            //step of the wizard (if it doesn't, the upload will eventually fail)
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getContainer());
            File root = pipeRoot == null ? null : pipeRoot.getRootPath();
            if(root != null && !NetworkDrive.exists(root)) //NetworkDrive.exists() will ensure that a \\server\share path gets mounted
            {
                StringBuilder msg = new StringBuilder("<p class='labkey-error'>The pipeline directory (");
                msg.append(root.getAbsolutePath());
                msg.append(") previously set for this folder does not exist or cannot be reached at this time.");

                //if current user is an admin, include a link to the pipeline setup page
                if(getViewContext().getUser().isAdministrator())
                {
                    ActionURL urlhelper = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(getContainer());
                    msg.append("</p><p><a href='").append(urlhelper.getLocalURIString()).append("'>[Setup Pipeline]</a></p>");
                }
                else
                    msg.append(" Please contact your system administrator.</p>");

                return new HtmlView(msg.toString());
            } //pipe root does not exist
            else
                return getBatchPropertiesView(form, false, errors);
        } //first step

        StepHandler<FormType> handler = _stepHandlers.get(currentStep);
        if (handler == null)
        {
            throw new IllegalStateException("Unknown wizard post step: " + currentStep);
        }

        return handler.handleStep(form, errors);
    }

    protected ModelAndView afterRunCreation(FormType form, ExpRun run, BindException errors) throws ServletException
    {
        return runUploadComplete(form, errors);
    }

    protected ModelAndView runUploadComplete(FormType form, BindException errors)
            throws ServletException
    {
        if (form.isMultiRunUpload())
        {
            form.setSuccessfulUploadComplete(true);
            return getRunPropertiesView(form, false, false, errors);
        }
        else
        {
            HttpView.throwRedirect(getSummaryLink(_protocol));
            return null;
        }
    }

    protected InsertView createInsertView(TableInfo baseTable, String lsidCol, DomainProperty[] properties, boolean errorReshow, String uploadStepName, FormType form, BindException errors)
    {
        // First, find the domain from our domain properties.  We do this, rather than having the caller provide a domain,
        // to allow insert views with a subset a given domain's properties.
        Domain domain = null;
        if (properties.length > 0)
        {
            Set<Domain> domains = new HashSet<Domain>();
            for (DomainProperty property : properties)
                domains.add(property.getDomain());
            if (domains.size() > 1)
                throw new IllegalStateException("Insert views cannot be created over properties from multiple domains.");
            domain = domains.iterator().next();
        }

        InsertView view = new UploadWizardInsertView(createDataRegionForInsert(baseTable, lsidCol, properties, null), getViewContext(), errors);
        if (errorReshow)
            view.setInitialValues(getViewContext().getRequest().getParameterMap());
        else if (domain != null)
        {

            try
            {
                Map<String, Object> inputNameToValue = new HashMap<String, Object>();
                for (Map.Entry<DomainProperty, Object> entry : form.getDefaultValues(domain).entrySet())
                    inputNameToValue.put(getInputName(entry.getKey()), entry.getValue());
                view.setInitialValues(inputNameToValue);
            }
            catch (ExperimentException e)
            {
                errors.addError(new ObjectError("main", null, null, e.toString()));
            }
        }

        if (form.getBatchId() != null)
        {
            view.getDataRegion().addHiddenFormField("batchId", form.getBatchId().toString());
        }
        view.getDataRegion().addHiddenFormField("uploadStep", uploadStepName);
        view.getDataRegion().addHiddenFormField("multiRunUpload", "false");
        view.getDataRegion().addHiddenFormField("resetDefaultValues", "false");
        view.getDataRegion().addHiddenFormField("rowId", Integer.toString(_protocol.getRowId()));
        view.getDataRegion().addHiddenFormField("uploadAttemptID", form.getUploadAttemptID());

        DisplayColumn targetStudyCol = view.getDataRegion().getDisplayColumn(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
        if (targetStudyCol != null)
        {
            view.getDataRegion().replaceDisplayColumn(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME,
                    new StudyPickerColumn(targetStudyCol.getColumnInfo()));
        }

        DisplayColumn participantVisitResolverCol = view.getDataRegion().getDisplayColumn(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        if (participantVisitResolverCol != null)
        {
            view.getDataRegion().replaceDisplayColumn(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME,
                    new ParticipantVisitResolverChooser(participantVisitResolverCol.getName(), form.getProvider().getParticipantVisitResolverTypes(),
                            participantVisitResolverCol.getColumnInfo()));
        }

        return view;
    }

    private ModelAndView getBatchPropertiesView(FormType runForm, boolean errorReshow, BindException errors) throws ServletException
    {
        ExpProtocol protocol = getProtocol(runForm);
        AssayProvider provider = AssayService.get().getProvider(protocol);
        runForm.setProviderName(provider.getName());
        Domain uploadDomain = provider.getBatchDomain(protocol);
        if (!showBatchStep(runForm, uploadDomain))
        {
            ActionURL helper = getViewContext().cloneActionURL();
            helper.replaceParameter("uploadStep", BatchStepHandler.NAME);
            HttpView.throwRedirect(helper);
        }
        InsertView insertView = createBatchInsertView(runForm, errorReshow, errors);
        insertView.getDataRegion().setFormActionUrl(new ActionURL(UploadWizardAction.class, getContainer()));

        ButtonBar bbar = new ButtonBar();
        bbar.setStyle(ButtonBar.Style.separateButtons);
        addNextButton(bbar);
        addResetButton(runForm, insertView, bbar);
        addCancelButton(bbar);
        insertView.getDataRegion().setButtonBar(bbar, DataRegion.MODE_INSERT);

        insertView.setTitle("Batch Properties");

        _stepDescription = "Batch Properties";

        JspView<AssayRunUploadForm> headerView = new JspView<AssayRunUploadForm>("/org/labkey/study/assay/view/newUploadSet.jsp", runForm);
        return new VBox(headerView, insertView);
    }

    /**
     * Decide whether or not to show the batch properties step in the wizard.
     * @param form the form with posted values
     * @param batchDomain domain for the batch fields
     */
    protected boolean showBatchStep(FormType form, Domain batchDomain) throws ServletException
    {
        DomainProperty[] batchColumns = batchDomain.getProperties();
        return batchColumns != null && batchColumns.length != 0;
    }

    protected void addNextButton(ButtonBar bbar)
    {
        ActionButton newRunButton = new ActionButton(getViewContext().getActionURL().getAction() + ".view", "Next",
                DataRegion.MODE_INSERT, ActionButton.Action.POST);
        bbar.add(newRunButton);
    }

    protected void addCancelButton(ButtonBar bbar)
    {
        ActionButton cancelButton = new ActionButton("Cancel", getSummaryLink(_protocol));
        bbar.add(cancelButton);
    }

    protected void addResetButton(FormType newRunForm, InsertView insertView, ButtonBar bbar)
    {
        ActionButton resetDefaultsButton = new ActionButton(getViewContext().getActionURL().getAction() + ".view", "Reset Default Values");
        resetDefaultsButton.setScript("this.form.action=\"" + getViewContext().getActionURL().getAction() + ".view" + "\"; " + insertView.getDataRegion().getJavascriptFormReference(true) + ".resetDefaultValues.value = \"true\";");
        resetDefaultsButton.setActionType(ActionButton.Action.POST);
        bbar.add(resetDefaultsButton);
    }

    protected InsertView createRunInsertView(FormType newRunForm, boolean errorReshow, BindException errors)
    {
        Set<DomainProperty> propertySet = newRunForm.getRunProperties().keySet();
        DomainProperty[] properties = propertySet.toArray(new DomainProperty[propertySet.size()]);
        return createInsertView(ExperimentService.get().getTinfoExperimentRun(),
                "lsid", properties, errorReshow, RunStepHandler.NAME, newRunForm, errors);
    }

    protected InsertView createBatchInsertView(FormType runForm, boolean reshow, BindException errors)
    {
        Set<DomainProperty> propertySet = runForm.getBatchProperties().keySet();
        DomainProperty[] properties = propertySet.toArray(new DomainProperty[propertySet.size()]);
        return createInsertView(ExperimentService.get().getTinfoExperimentRun(),
                "lsid", properties, reshow, BatchStepHandler.NAME, runForm, errors);
    }

    protected ModelAndView getRunPropertiesView(FormType newRunForm, boolean errorReshow, boolean warnings, BindException errors)
    {
        if (!errorReshow && !newRunForm.isResetDefaultValues())
        {
            newRunForm.clearUploadedData();
        }
        InsertView insertView = createRunInsertView(newRunForm, errorReshow, errors);
        addHiddenBatchProperties(newRunForm, insertView);

        for (Map.Entry<DomainProperty, String> entry : newRunForm.getBatchProperties().entrySet())
        {
            if (entry.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                ParticipantVisitResolverType resolverType = AbstractAssayProvider.findType(entry.getValue(), newRunForm.getProvider().getParticipantVisitResolverTypes());
                resolverType.addHiddenFormFields(newRunForm, insertView);
                break;
            }
        }

        ExpRunTable table = AssayService.get().createRunTable(_protocol, getProvider(newRunForm), newRunForm.getUser(), newRunForm.getContainer());
        insertView.getDataRegion().addColumn(0, table.getColumn("Name"));
        insertView.getDataRegion().addColumn(1, table.getColumn("Comments"));

        addSampleInputColumns(getProtocol(newRunForm), insertView);
        insertView.getDataRegion().addDisplayColumn(new AssayDataCollectorDisplayColumn(newRunForm));

        if (warnings)
        {
            insertView.getDataRegion().addDisplayColumn(0, new AssayWarningsDisplayColumn(newRunForm));
        }

        ButtonBar bbar = new ButtonBar();
        bbar.setStyle(ButtonBar.Style.separateButtons);
        addRunActionButtons(newRunForm, insertView, bbar);
        addCancelButton(bbar);

        insertView.getDataRegion().setButtonBar(bbar, DataRegion.MODE_INSERT);
        insertView.setTitle("Run Properties");

        _stepDescription = "Run Properties and Data File";

        JspView<AssayRunUploadForm> headerView = new JspView<AssayRunUploadForm>("/org/labkey/api/study/actions/newRunProperties.jsp", newRunForm);
        return new VBox(headerView, insertView);
    }

    protected void addHiddenBatchProperties(AssayRunUploadForm newRunForm, InsertView insertView)
    {
        addHiddenProperties(newRunForm.getBatchProperties(), insertView);
    }

    protected void addHiddenRunProperties(AssayRunUploadForm newRunForm, InsertView insertView)
    {
        addHiddenProperties(newRunForm.getRunProperties(), insertView);
    }

    public static String getInputName(DomainProperty property, String disambiguationId)
    {
        if (disambiguationId != null)
            return ColumnInfo.propNameFromName(disambiguationId + "_" + property.getName());
        else
            return ColumnInfo.propNameFromName(property.getName());
    }

    public static String getInputName(DomainProperty property)
    {
        return getInputName(property, null);
    }

    protected void addHiddenProperties(Map<DomainProperty, String> properties, InsertView insertView)
    {
        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            String name = ColumnInfo.propNameFromName(entry.getKey().getName());
            String value = entry.getValue();
            insertView.getDataRegion().addHiddenFormField(name, value);
        }
    }

    protected void addHiddenProperties(Map<DomainProperty, String> properties, InsertView insertView, String disambiguationId)
    {
        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            String name = getInputName(entry.getKey(), disambiguationId);
            String value = entry.getValue();
            insertView.getDataRegion().addHiddenFormField(name, value);
        }
    }

    protected void addRunActionButtons(FormType newRunForm, InsertView insertView, ButtonBar bbar)
    {
        addFinishButtons(newRunForm, insertView, bbar);
        addResetButton(newRunForm, insertView, bbar);
    }

    protected void addFinishButtons(FormType newRunForm, InsertView insertView, ButtonBar bbar)
    {
        ActionButton saveFinishButton = new ActionButton(getViewContext().getActionURL().getAction() + ".view", "Save and Finish");
        saveFinishButton.setScript(insertView.getDataRegion().getJavascriptFormReference(true) + ".multiRunUpload.value = \"false\";");
        saveFinishButton.setActionType(ActionButton.Action.POST);
        bbar.add(saveFinishButton);

        List<AssayDataCollector> collectors = newRunForm.getProvider().getDataCollectors(Collections.<String, File>emptyMap(), newRunForm);
        for (AssayDataCollector collector : collectors)
        {
            AssayDataCollector.AdditionalUploadType t = collector.getAdditionalUploadType(newRunForm);
            if (t != AssayDataCollector.AdditionalUploadType.Disallowed)
            {
                ActionButton saveUploadAnotherButton = new ActionButton(getViewContext().getActionURL().getAction() + ".view", t.getButtonText());
                saveUploadAnotherButton.setScript(insertView.getDataRegion().getJavascriptFormReference(true) + ".multiRunUpload.value = \"true\";");
                saveUploadAnotherButton.setActionType(ActionButton.Action.POST);
                bbar.add(saveUploadAnotherButton);
                break;
            }
        }
    }

    protected void addSampleInputColumns(ExpProtocol protocol, InsertView insertView)
    {
        // Don't add any inputs in the base case
    }

    public NavTree appendNavTrail(NavTree root)
    {
        ActionURL helper = getSummaryLink(_protocol);
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        String finalChild = "Data Import";
        if (_stepDescription != null)
        {
            finalChild = finalChild + ": " + _stepDescription;
        }
        result.addChild(finalChild, helper);
        return result;
    }

    protected static class InputDisplayColumn extends SimpleDisplayColumn
    {
        protected String _inputName;

        public InputDisplayColumn(String caption, String inputName)
        {
            _inputName = inputName;
            setCaption(caption);
        }

        public boolean isEditable()
        {
            return true;
        }

        public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
        {
            out.write("<input type=\"text\" name=\"" + _inputName + "\" value=\"" + PageFlowUtil.filter(value) + "\">");
        }

        protected Object getInputValue(RenderContext ctx)
        {
            TableViewForm viewForm = ctx.getForm();
            return viewForm.getStrings().get(_inputName);
        }
    }

    public static class StudyPickerColumn extends InputDisplayColumn
    {
        ColumnInfo _colInfo;

        public StudyPickerColumn(ColumnInfo col)
        {
            super("Target Study", "targetStudy");
            _colInfo = col;
        }

        public void renderDetailsCaptionCell(RenderContext ctx, Writer out) throws IOException
        {
            if (null == _caption)
                return;

            out.write("<td class='labkey-form-label'>");
            renderTitle(ctx, out);
            int mode = ctx.getMode();
            if (mode == DataRegion.MODE_INSERT || mode == DataRegion.MODE_UPDATE)
            {
                if (_colInfo != null)
                {
                    String helpPopupText = ((_colInfo.getFriendlyTypeName() != null) ? "Type: " + _colInfo.getFriendlyTypeName() + "\n" : "") +
                                ((_colInfo.getDescription() != null) ? "Description: " + _colInfo.getDescription() + "\n" : "");
                    out.write(PageFlowUtil.helpPopup(_colInfo.getName(), helpPopupText));
                    if (!_colInfo.isNullable())
                        out.write(" *");
                }
            }
            out.write("</td>");
        }

        public void renderDetailsData(RenderContext ctx, Writer out, int span) throws IOException, SQLException
        {
            super.renderDetailsData(ctx, out, 1);
        }

        protected boolean isDisabledInput()
        {
            return getColumnInfo().getDefaultValueType() == DefaultValueType.FIXED_NON_EDITABLE;
        }

        public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
        {
            Map<Container, String> studies = AssayPublishService.get().getValidPublishTargets(ctx.getViewContext().getUser(), ReadPermission.class);

            boolean disabled = isDisabledInput();
            out.write("<select name=\"" + _inputName + "\"" + (disabled ? " DISABLED" : "") + ">\n");
            out.write("    <option value=\"\">[None]</option>\n");
            for (Map.Entry<Container, String> entry : studies.entrySet())
            {
                Container container = entry.getKey();
                out.write("    <option value=\"" + PageFlowUtil.filter(container.getId()) + "\"");
                if (container.getId().equals(value))
                    out.write(" SELECTED");
                out.write(">" + PageFlowUtil.filter(container.getPath() + " (" + entry.getValue()) + ")</option>\n");
            }
            out.write("</select>");
            if (disabled)
                out.write("<input type=\"hidden\" name=\"" +_inputName + "\" value=\"" + PageFlowUtil.filter(value) + "\">");
        }

        public ColumnInfo getColumnInfo()
        {
            return _colInfo;
        }

        public boolean isQueryColumn()
        {
            return true;
        }
    }

    public static boolean validatePostedProperties(Map<DomainProperty, String> properties, BindException errors)
    {
        for (ValidationError error : AbstractAssayProvider.validateProperties(properties))
            errors.reject(SpringActionController.ERROR_MSG, error.getMessage());

/*
        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            DomainProperty dp = entry.getKey();
            String value = entry.getValue();
            String label = dp.getPropertyDescriptor().getNonBlankCaption();
            PropertyType type = dp.getPropertyDescriptor().getPropertyType();
            boolean missing = (value == null || value.length() == 0);
            if (dp.isRequired() && missing)
            {
                errors.reject(SpringActionController.ERROR_MSG,
                        label + " is required and must be of type " + ColumnInfo.getFriendlyTypeName(type.getJavaType()) + ".");
            }
            else if (!missing)
            {
                try
                {
                    ConvertUtils.convert(value, type.getJavaType());
                }
                catch (ConversionException e)
                {
                    String message = label + " must be of type " + ColumnInfo.getFriendlyTypeName(type.getJavaType()) + ".";
                    message +=  "  Value \"" + value + "\" could not be converted";
                    if (e.getCause() instanceof ArithmeticException)
                        message +=  ": " + e.getCause().getLocalizedMessage();
                    else
                        message += ".";

                    errors.reject(SpringActionController.ERROR_MSG, message);
                }
            }
            List<ValidationError> validationErrors = new ArrayList<ValidationError>();
            for (IPropertyValidator validator : PropertyService.get().getPropertyValidators(dp.getPropertyDescriptor()))
            {
                //validator.validate(dp.getLabel() != null ? dp.getLabel() : dp.getName(), value, validationErrors);
            }

            for (ValidationError ve : validationErrors)
                errors.reject(SpringActionController.ERROR_MSG, ve.getMessage());
        }
*/
        return errors.getErrorCount() == 0;
    }

    public class BatchStepHandler extends StepHandler<FormType>
    {
        public static final String NAME = "BATCH";

        public ModelAndView handleStep(FormType form, BindException errors) throws ServletException
        {
            if (!form.isResetDefaultValues() && validatePostedProperties(form.getBatchProperties(), errors))
                return getRunPropertiesView(form, false, false, errors);
            else
                return getBatchPropertiesView(form, !form.isResetDefaultValues(), errors);
        }

        public String getName()
        {
            return NAME;
        }
    }

    public static abstract class StepHandler<StepFormClass extends AssayRunUploadForm>
    {
        public abstract ModelAndView handleStep(StepFormClass form, BindException error) throws ServletException, SQLException;

        public abstract String getName();
    }

    protected Set<String> getCompletedUploadAttemptIDs()
    {
        Set<String> result = (Set<String>)getViewContext().getRequest().getSession(true).getAttribute("COMPLETE_UPLOAD_ATTEMPT_IDS");
        if (result == null)
        {
            result = new HashSet<String>();
            getViewContext().getRequest().getSession(true).setAttribute("COMPLETE_UPLOAD_ATTEMPT_IDS", result);
        }
        return result;
    }

    public class RunStepHandler extends StepHandler<FormType>
    {
        public static final String NAME = "RUN";

        public ModelAndView handleStep(FormType form, BindException errors) throws ServletException, SQLException
        {
            if (getCompletedUploadAttemptIDs().contains(form.getUploadAttemptID()))
            {
                HttpView.throwRedirect(getViewContext().getActionURL());
            }

            if (!form.isResetDefaultValues() && validatePost(form, errors))
                return handleSuccessfulPost(form, errors);
            else
                return getRunPropertiesView(form, !form.isResetDefaultValues(), false, errors);
        }

        protected ModelAndView handleSuccessfulPost(FormType form, BindException errors) throws SQLException, ServletException
        {
            ExpRun run;
            try
            {
                run = saveExperimentRun(form);
            }
            catch (ValidationException e)
            {
                for (ValidationError error : e.getErrors())
                {
                    if (error instanceof PropertyValidationError)
                        errors.addError(new FieldError("AssayUploadForm", ((PropertyValidationError)error).getProperty(), null, false,
                                new String[]{SpringActionController.ERROR_MSG}, new Object[0], error.getMessage()));
                    else
                        errors.reject(SpringActionController.ERROR_MSG, error.getMessage());
                }
                return getRunPropertiesView(form, true, false, errors);
            }
            catch (ExperimentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                return getRunPropertiesView(form, true, false, errors);
            }

            return afterRunCreation(form, run, errors);
        }

        public ExpRun saveExperimentRun(FormType form) throws ExperimentException, ValidationException
        {
            ExpExperiment exp = null;
            if (form.getBatchId() != null)
            {
                exp = ExperimentService.get().getExpExperiment(form.getBatchId().intValue());
            }

            Pair<ExpRun, ExpExperiment> insertedValues = form.getProvider().saveExperimentRun(form, exp);

            ExpRun run = insertedValues.getKey();
            form.setBatchId(insertedValues.getValue().getRowId());

            form.saveDefaultBatchValues();
            form.saveDefaultRunValues();
            getCompletedUploadAttemptIDs().add(form.getUploadAttemptID());
            AssayDataCollector collector = form.getSelectedDataCollector();
            if (collector != null)
            {
                collector.uploadComplete(form);
            }
            form.resetUploadAttemptID();

            return run;
        }

        protected boolean validatePost(FormType form, BindException errors)
        {
            return validatePostedProperties(form.getRunProperties(), errors);
        }

        public String getName()
        {
            return NAME;
        }
    }

    protected ParticipantVisitResolverType getSelectedParticipantVisitResolverType(AssayProvider provider, AssayRunUploadForm<? extends AssayProvider> newRunForm)
    {
        String participantVisitResolverName = null;
        for (Map.Entry<DomainProperty, String> batchProperty : newRunForm.getBatchProperties().entrySet())
        {
            if (batchProperty.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                participantVisitResolverName = batchProperty.getValue();
                break;
            }
        }
        if (participantVisitResolverName != null)
            return AbstractAssayProvider.findType(participantVisitResolverName, provider.getParticipantVisitResolverTypes());
        return null;
    }

    public AppBar getAppBar()
    {
        return getAppBar(_protocol);
    }

    private static class UploadWizardInsertView extends InsertView
    {
        public UploadWizardInsertView(DataRegion dataRegion, ViewContext context, BindException errors)
        {
            super(dataRegion, new WizardRenderContext(context, errors));
        }

        @Override
        protected void _renderDataRegion(RenderContext ctx, Writer out) throws IOException, SQLException
        {
            // may want to just put this in a js file and include it in all the wizard pages
            out.write("<script type=\"text/javascript\">\n");
            out.write("    function showPopup(elem, txtTitle, txtMsg)\n" +
                    "      {\n" +
                    "        var win = new Ext.Window({\n" +
                    "           title: txtTitle,\n" +
                    "           border: false,\n" +
                    "           constrain: true,\n" +
                    "           html: txtMsg,\n" +
                    "           closeAction:'close',\n" +
                    "           autoScroll: true,\n" +
                    "           modal: true,\n" +
                    "           buttons: [{\n" +
                    "             text: 'Close',\n" +
                    "             id: 'btn_cancel',\n" +
                    "             handler: function(){win.close();}\n" +
                    "           }]\n" +
                    "        });\n" +
                    "        win.show(elem);\n" +
                    "      }");
            out.write("</script>\n");

            super._renderDataRegion(ctx, out);
        }
    }

    private static class WizardRenderContext extends RenderContext
    {
        private static final int MAX_ERRORS = 7;
        public WizardRenderContext(ViewContext context, BindException errors)
        {
            super(context, errors);
        }

        @Override
        public String getErrors(String paramName)
        {
            BindException errors = getErrors();
            if (errors != null && errors.getErrorCount() > MAX_ERRORS)
            {
                List list;
                if ("main".equals(paramName))
                    list = errors.getGlobalErrors();
                else
                    list = errors.getFieldErrors(paramName);
                if (list == null || list.size() == 0)
                    return "";

                StringBuilder sb = new StringBuilder();
                StringBuilder msgBox = new StringBuilder();
                String br = "<font class=\"labkey-error\">";
                int cnt = 0;
                for (Object m : list)
                {
                    if (cnt++ < MAX_ERRORS)
                    {
                        sb.append(br);
                        sb.append(getViewContext().getMessage((MessageSourceResolvable)m));
                        br = "<br>";
                    }
                    msgBox.append(getViewContext().getMessage((MessageSourceResolvable)m));
                    msgBox.append("<br>");
                }
                if (sb.length() > 0)
                    sb.append("</font>");

                if (list.size() > MAX_ERRORS)
                {
                    sb.append("<br><a id='extraErrors' href='#' onclick=\"showPopup('extraErrors', 'All Errors', ");
                    sb.append(PageFlowUtil.jsString(msgBox.toString()));
                    sb.append(");return false;\">Too many errors to display (click to show all).<a><br>");
                }
                return sb.toString();
            }
            else
                return super.getErrors(paramName);
        }
    }
}
