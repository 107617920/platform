/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

package org.labkey.study.assay;

import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.fhcrc.cpas.exp.xml.DomainDescriptorType;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.api.ExpProtocol.AssayDomainTypes;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.AssayPipelineProvider;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.study.assay.xml.DomainDocument;
import org.labkey.study.assay.xml.ProviderType;
import org.labkey.study.controllers.assay.AssayController;
import org.labkey.study.StudyModule;
import org.springframework.web.servlet.ModelAndView;

import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * User: kevink
 * Date: Dec 10, 2008 2:20:38 PM
 */
public class ModuleAssayProvider extends TsvAssayProvider
{
    public static class ModuleAssayException extends RuntimeException
    {
        public ModuleAssayException(String message)
        {
            super(message);
        }

        public ModuleAssayException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }

    private static final String UPLOAD_VIEW_FILENAME = "upload.html";
    private static final String BEGIN_VIEW_FILENAME = "begin.html";

    private Resource basePath;
    private Module module;
    private String name;
    private String description;
    private FileType[] inputFileSuffices = new FileType[0];

    private FieldKey participantIdKey;
    private FieldKey visitIdKey;
    private FieldKey dateKey;
    private FieldKey specimenIdKey;

    public static final DataType RAW_DATA_TYPE = new DataType("RawAssayData");

    public ModuleAssayProvider(String name, Module module, Resource basePath, ProviderType providerConfig)
    {
        super(name + "Protocol", name + "Run");
        _tableMetadata = new AssayTableMetadata(_tableMetadata.getSpecimenDetailParentFieldKey(), _tableMetadata.getRunFieldKeyFromResults(), _tableMetadata.getResultRowIdFieldKey(), _tableMetadata.getDatasetRowIdPropertyName())
        {
            @Override
            public FieldKey getParticipantIDFieldKey()
            {
                if (participantIdKey != null)
                    return participantIdKey;
                return super.getParticipantIDFieldKey();
            }

            @Override
            public FieldKey getVisitIDFieldKey(TimepointType timepointType)
            {
                if (timepointType == TimepointType.VISIT)
                {
                    if (visitIdKey != null)
                        return visitIdKey;
                }
                else
                {
                    if (dateKey != null)
                        return dateKey;
                }
                return super.getVisitIDFieldKey(timepointType);
            }

            @Override
            public FieldKey getSpecimenIDFieldKey()
            {
                if (specimenIdKey != null)
                    return specimenIdKey;
                return super.getSpecimenIDFieldKey();
            }
        };
        this.name = name;
        this.module = module;
        this.basePath = basePath;

        init(providerConfig);
    }

    protected void init(ProviderType providerConfig)
    {
        if (providerConfig == null)
            return;

        description = providerConfig.isSetDescription() ? providerConfig.getDescription() : getName();

        ProviderType.FieldKeys fieldKeys = providerConfig.getFieldKeys();
        if (fieldKeys != null)
        {
            if (fieldKeys.isSetParticipantId())
                participantIdKey = FieldKey.fromString(fieldKeys.getParticipantId());
            if (fieldKeys.isSetVisitId())
                visitIdKey = FieldKey.fromString(fieldKeys.getVisitId());
            if (fieldKeys.isSetDate())
                dateKey = FieldKey.fromString(fieldKeys.getDate());
            if (fieldKeys.isSetSpecimenId())
                specimenIdKey = FieldKey.fromString(fieldKeys.getSpecimenId());
        }

        inputFileSuffices = new FileType[providerConfig.getInputDataFileSuffixArray().length];
        for (int i = 0; i < inputFileSuffices.length; i++)
        {
            inputFileSuffices[i] = new FileType(providerConfig.getInputDataFileSuffixArray(i));
        }
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getResourceName()
    {
        return basePath.getName();
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    protected DomainDescriptorType parseDomain(IAssayDomainType domainType) throws IOException, XmlException
    {
        Resource domainFile = module.getModuleResolver().lookup(basePath.getPath().append(ModuleAssayLoader.DOMAINS_DIR_NAME, domainType.getName().toLowerCase() + ".xml"));
        if (domainFile == null || !domainFile.exists())
            return null;

        DomainDocument doc = DomainDocument.Factory.parse(domainFile.getInputStream());
        DomainDescriptorType xDomain = doc.getDomain();
        ArrayList<XmlError> errors = new ArrayList<XmlError>();
        XmlOptions options = new XmlOptions().setErrorListener(errors);
        if (xDomain != null && xDomain.validate(options))
        {
            if (!xDomain.isSetName())
                xDomain.setName(domainType.getName() + " Fields");

            if (!xDomain.isSetDomainURI())
                xDomain.setDomainURI(domainType.getLsidTemplate());

            return xDomain;
        }

        if (errors.size() > 0)
        {
            StringBuffer sb = new StringBuffer();
            while (errors.size() > 0)
            {
                XmlError error = errors.remove(0);
                sb.append(error.toString());
                if (errors.size() > 0)
                    sb.append("\n");
            }
            throw new XmlException(sb.toString());
        }

        return null;
    }

    /** @return a domain and its default values */
    protected Pair<Domain, Map<DomainProperty, Object>> createDomain(Container c, User user, IAssayDomainType domainType)
    {
        DomainDescriptorType xDomain;
        try
        {
            xDomain = parseDomain(domainType);
        }
        catch (Exception e)
        {
            throw new ModuleAssayException("Failed to load '" + domainType.getName().toLowerCase() + "' domain xml file:\n" + e.getMessage(), e);
        }

        if (xDomain != null)
        {
            return PropertyService.get().createDomain(c, xDomain);
        }
        return null;
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createBatchDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = createDomain(c, user, AssayDomainTypes.Batch);
        if (result != null)
            return result;
        return super.createBatchDomain(c, user);
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = createDomain(c, user, AssayDomainTypes.Run);
        if (result != null)
            return result;
        return super.createRunDomain(c, user);
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createResultDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = createDomain(c, user, AssayDomainTypes.Result);
        if (result != null)
            return result;
        return super.createResultDomain(c, user);
    }

    @Override
    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<IAssayDomainType, DomainDescriptorType> domainsDescriptors = new LinkedHashMap<IAssayDomainType, DomainDescriptorType>(AssayDomainTypes.values().length);
        for (IAssayDomainType domainType : AssayDomainTypes.values())
        {
            try
            {
                DomainDescriptorType xDomain = parseDomain(domainType);
                if (xDomain != null)
                    domainsDescriptors.put(domainType, xDomain);
            }
            catch (Exception e)
            {
                throw new ModuleAssayException("Failed to load '" + domainType.getName().toLowerCase() + "' domain xml file:\n" + e.getMessage(), e);
            }
        }

        Map<String, Set<String>> required = new HashMap<String, Set<String>>();
        for (Map.Entry<IAssayDomainType, DomainDescriptorType> domainDescriptor : domainsDescriptors.entrySet())
        {
            IAssayDomainType domainType = domainDescriptor.getKey();
            DomainDescriptorType xDescriptor = domainDescriptor.getValue();
            PropertyDescriptorType[] xProperties = xDescriptor.getPropertyDescriptorArray();
            LinkedHashSet<String> properties = new LinkedHashSet<String>(xProperties.length);
            for (PropertyDescriptorType xProp : xProperties)
                properties.add(xProp.getName());

            required.put(domainType.getPrefix(), properties);
        }
        return required;
    }

    protected String getViewResourcePath(IAssayDomainType domainType, boolean details)
    {
        String viewName = domainType.getName().toLowerCase();
        if (!details)
        {
            // pluralize
            if (domainType.equals(AssayDomainTypes.Batch))
                viewName += "es";
            else
                viewName += "s";
        }
        return viewName + ".html";
    }

    protected boolean hasCustomView(String viewResourceName)
    {
        return module.getModuleResolver().lookup(basePath.getPath().append("views", viewResourceName)) != null;
    }

    @Override
    public boolean hasCustomView(IAssayDomainType domainType, boolean details)
    {
        return hasCustomView(getViewResourcePath(domainType, details));
    }

    protected ModelAndView getCustomView(String viewResourceName)
    {
        Resource viewResource = module.getModuleResolver().lookup(basePath.getPath().append("views", viewResourceName));
        if (viewResource != null && viewResource.exists())
        {
            ModuleHtmlView view = new ModuleHtmlView(viewResource);
            view.setFrame(WebPartView.FrameType.NONE);
            return view;
        }
        return null;
    }

    // XXX: consider moving to TsvAssayProvider
    protected ModelAndView getCustomView(IAssayDomainType domainType, boolean details)
    {
        return getCustomView(getViewResourcePath(domainType, details));
    }

    public static class AssayPageBean
    {
        public ModuleAssayProvider provider;
        public ExpProtocol expProtocol;
    }

    protected ModelAndView createListView(IAssayDomainType domainType, ExpProtocol protocol)
    {
        ModelAndView nestedView = getCustomView(domainType, false);
        if (nestedView == null)
            return null;

        AssayPageBean bean = new AssayPageBean();
        bean.provider = this;
        bean.expProtocol = protocol;

        JspView<AssayPageBean> view = new JspView<AssayPageBean>("/org/labkey/study/assay/view/moduleAssayListView.jsp", bean);
        view.setView("nested", nestedView);
        return view;
    }

    @Override
    public ModelAndView createBeginView(ViewContext context, ExpProtocol protocol)
    {
        ModelAndView beginView = getCustomView(BEGIN_VIEW_FILENAME);
        if (beginView == null)
            return super.createBeginView(context, protocol);

        BatchDetailsBean bean = new BatchDetailsBean();
        bean.provider = this;
        bean.expProtocol = protocol;

        JspView<BatchDetailsBean> view = new JspView<BatchDetailsBean>("/org/labkey/study/assay/view/begin.jsp", bean);
        view.setView("nested", beginView);
        return view;
    }

    @Override
    public ModelAndView createBatchesView(ViewContext context, ExpProtocol protocol)
    {
        return createListView(AssayDomainTypes.Batch, protocol);
    }

    public static class BatchDetailsBean extends AssayPageBean
    {
        public ExpExperiment expExperiment;
    }

    @Override
    public ModelAndView createBatchDetailsView(ViewContext context, ExpProtocol protocol, ExpExperiment batch)
    {
        ModelAndView batchDetailsView = getCustomView(AssayDomainTypes.Batch, true);
        if (batchDetailsView == null)
            return super.createBatchDetailsView(context, protocol, batch);

        BatchDetailsBean bean = new BatchDetailsBean();
        bean.provider = this;
        bean.expProtocol = protocol;
        bean.expExperiment = batch;

        JspView<BatchDetailsBean> view = new JspView<BatchDetailsBean>("/org/labkey/study/assay/view/batchDetails.jsp", bean);
        view.setView("nested", batchDetailsView);
        return view;
    }

    @Override
    public ModelAndView createRunsView(ViewContext context, ExpProtocol protocol)
    {
        return createListView(AssayDomainTypes.Run, protocol);
    }

    public static class RunDetailsBean extends AssayPageBean
    {
        public ExpRun expRun;
    }

    @Override
    public ModelAndView createRunDetailsView(ViewContext context, ExpProtocol protocol, ExpRun run)
    {
        ModelAndView runDetailsView = getCustomView(AssayDomainTypes.Run, true);
        if (runDetailsView == null)
            return super.createRunDetailsView(context, protocol, run);

        RunDetailsBean bean = new RunDetailsBean();
        bean.provider = this;
        bean.expProtocol = protocol;
        bean.expRun = run;

        JspView<RunDetailsBean> view = new JspView<RunDetailsBean>("/org/labkey/study/assay/view/runDetails.jsp", bean);
        view.setView("nested", runDetailsView);
        return view;
    }

    @Override
    public ModelAndView createResultsView(ViewContext context, ExpProtocol protocol)
    {
        return createListView(AssayDomainTypes.Result, protocol);
    }

    public static class ResultDetailsBean extends AssayPageBean
    {
        public ExpData expData;
        public Integer objectId;
    }

    @Override
    public ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object objectId)
    {
        ModelAndView resultDetailsView = getCustomView(AssayDomainTypes.Result, true);
        if (resultDetailsView == null)
            return super.createResultDetailsView(context, protocol, data, objectId);

        ResultDetailsBean bean = new ResultDetailsBean();
        bean.provider = this;
        bean.expProtocol = protocol;
        bean.expData = data;
        if (objectId == null)
        {
            HttpView.throwNotFound();
        }
        if (objectId instanceof Number)
        {
            bean.objectId = ((Number)objectId).intValue();
        }
        else
        {
            try
            {
                bean.objectId = Integer.parseInt(objectId.toString());
            }
            catch (NumberFormatException e)
            {
                HttpView.throwNotFound();
            }
        }

        JspView<ResultDetailsBean> view = new JspView<ResultDetailsBean>("/org/labkey/study/assay/view/resultDetails.jsp", bean);
        view.setView("nested", resultDetailsView);
        return view;
    }

    @Override
    public ActionURL getImportURL(Container container, ExpProtocol protocol)
    {
        if (!hasUploadView())
            return super.getImportURL(container, protocol);
        return PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(container, protocol, AssayController.ModuleAssayUploadAction.class);
    }

    protected boolean hasUploadView()
    {
        return hasCustomView(UPLOAD_VIEW_FILENAME);
    }

    protected ModelAndView getUploadView()
    {
        return getCustomView(UPLOAD_VIEW_FILENAME);
    }

    public ModelAndView createUploadView(AssayRunUploadForm form)
    {
        ModelAndView uploadView = getUploadView();
        if (uploadView == null)
        {
            ActionURL url = form.getViewContext().cloneActionURL();
            url.setAction(UploadWizardAction.class);
            return HttpView.redirect(url);
        }

        JspView<AssayRunUploadForm> view = new JspView<AssayRunUploadForm>("/org/labkey/study/assay/view/moduleAssayUpload.jsp", form);
        view.setView("nested", uploadView);
        return view;
    }

    @Override
    public boolean hasUsefulDetailsPage()
    {
        return true;
    }

    @Override
    public List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope, ScriptType type)
    {
        List<File> validationScripts = new ArrayList<File>();

        if (scope == Scope.ASSAY_TYPE || scope == Scope.ALL)
        {
            // lazily get the validation scripts
            Resource scriptDir = module.getModuleResolver().lookup(basePath.getPath().append("scripts"));

            if (scriptDir != null && scriptDir.exists())
            {
                final ScriptEngineManager manager = ServiceRegistry.get().getService(ScriptEngineManager.class);

                Collection<? extends Resource> scripts = scriptDir.list();
                List<File> scriptFiles = new ArrayList<File>(scripts.size());
                for (Resource r : scripts)
                {
                    if (r instanceof FileResource)
                    {
                        String ext = r.getPath().extension();
                        if (manager.getEngineByExtension(ext) != null)
                            scriptFiles.add(((FileResource)r).getFile());
                    }
                }
                validationScripts.addAll(scriptFiles);
                Collections.sort(validationScripts, new Comparator<File>(){
                    public int compare(File o1, File o2)
                    {
                        return o1.getName().compareToIgnoreCase(o2.getName());
                    }
                });
            }
        }
        validationScripts.addAll(super.getValidationAndAnalysisScripts(protocol, scope, type));
        return validationScripts;
    }

    @Override
    public DataExchangeHandler getDataExchangeHandler()
    {
        return new ModuleDataExchangeHandler();
    }

    public PipelineProvider getPipelineProvider()
    {
        return new ModuleAssayPipelineProvider(StudyModule.class,
                new PipelineProvider.FileTypesEntryFilter(inputFileSuffices), this, "Import " + getName());
    }

    static class ModuleAssayPipelineProvider extends AssayPipelineProvider
    {
        public ModuleAssayPipelineProvider(Class<? extends Module> moduleClass, FileEntryFilter filter, AssayProvider assayProvider, String actionDescription)
        {
            super(moduleClass, filter, assayProvider, actionDescription);
        }

        @Override
        protected String getFilePropertiesId()
        {
            return super.getFilePropertiesId() + ':' + this.getName();
        }
    }
}
