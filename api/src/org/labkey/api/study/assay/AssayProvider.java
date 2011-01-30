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

package org.labkey.api.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.qc.DataValidator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.pipeline.PipelineProvider;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 9:59:49 AM
 */
public interface AssayProvider extends Handler<ExpProtocol>
{
    /** Get a schema for any additional TableInfos. */
    AssaySchema getProviderSchema(User user, Container container, ExpProtocol protocol);

    Domain getBatchDomain(ExpProtocol protocol);

    Domain getRunDomain(ExpProtocol protocol);

    Domain getResultsDomain(ExpProtocol protocol);

    /**
     * Creates a run, but does not persist it to the database. Creates the run only, no protocol applications, etc.
     */
    ExpRun createExperimentRun(String name, Container container, ExpProtocol protocol);

    Pair<ExpRun, ExpExperiment> saveExperimentRun(AssayRunUploadContext context, ExpExperiment batch) throws ExperimentException, ValidationException;

    List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles, AssayRunUploadForm context);

    String getName();

    /** Get the root resource name.  Usually this is the same as the AssayProvider name. */
    String getResourceName();

    AssayTableMetadata getTableMetadata();

    ExpProtocol createAssayDefinition(User user, Container container, String name, String description) throws ExperimentException;

    List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user);

    HttpView getDataDescriptionView(AssayRunUploadForm form);

    Pair<ExpProtocol.AssayDomainTypes, DomainProperty> findTargetStudyProperty(ExpProtocol protocol);

    Container getAssociatedStudyContainer(ExpProtocol protocol, Object dataId);

    /** @return the URL used to import data when the user still needs to upload data files */
    ActionURL getImportURL(Container container, ExpProtocol protocol);

    /** @return may return null if no results/data are tracked by this assay type */
    @Nullable
    ContainerFilterable createDataTable(AssaySchema schema, ExpProtocol protocol, boolean includeCopiedToStudyColumns);

    ExpRunTable createRunTable(AssaySchema schema, ExpProtocol protocol);

    /** TargetStudy may be null if each row in dataKeys has a non-null AssayPublishKey#getTargetStudy(). */
    ActionURL copyToStudy(ViewContext viewContext, ExpProtocol protocol, @Nullable Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors);

    boolean canCopyToStudy();

    List<ParticipantVisitResolverType> getParticipantVisitResolverTypes();

    List<Pair<Domain, Map<DomainProperty, Object>>> getDomains(ExpProtocol protocol);

    Set<String> getReservedPropertyNames(ExpProtocol protocol, Domain domain);

    Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer);

    Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer, ExpProtocol toCopy);

    boolean isFileLinkPropertyAllowed(ExpProtocol protocol, Domain domain);

    boolean isMandatoryDomainProperty(Domain domain, String propertyName);

    boolean allowDefaultValues(Domain domain);

    DefaultValueType[] getDefaultValueOptions(Domain domain);

    DefaultValueType getDefaultValueDefault(Domain domain);

    ResultsQueryView createResultsQueryView(ViewContext context, ExpProtocol protocol);

    RunListQueryView createRunQueryView(ViewContext context, ExpProtocol protocol);

    boolean hasCustomView(IAssayDomainType domainType, boolean details);

    ModelAndView createBeginView(ViewContext context, ExpProtocol protocol);

    ModelAndView createBatchesView(ViewContext context, ExpProtocol protocol);

    ModelAndView createBatchDetailsView(ViewContext context, ExpProtocol protocol, ExpExperiment batch);

    ModelAndView createRunsView(ViewContext context, ExpProtocol protocol);

    ModelAndView createRunDetailsView(ViewContext context, ExpProtocol protocol, ExpRun run);

    ModelAndView createResultsView(ViewContext context, ExpProtocol protocol);

    public ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object dataRowId);

    void deleteProtocol(ExpProtocol protocol, User user) throws ExperimentException;

    /**
     * Get the action that implements the assay designer for this type
     */
    Class<? extends Controller> getDesignerAction();

    /**
     * Returns the action that implements the data import action for this type when the
     * assay definition does not yet exist.
     */
    Class<? extends Controller> getDataImportAction();

    /**
     * Returns true if the given provider can display a useful details page for dataset data that has been copied.
     * If a provider is a simple GPAT, then it does not have a useful details page
     * @return
     */
    boolean hasUsefulDetailsPage();

    PipelineProvider getPipelineProvider();

    /** Upgrade from property store to hard table */
    void materializeAssayResults(User user, ExpProtocol protocol) throws SQLException;

    public enum Scope {
        ALL,
        ASSAY_TYPE,
        ASSAY_DEF,
    }

    public enum ScriptType
    {
        VALIDATION("ValidationScript"),
        TRANSFORM("TransformScript");

        private final String _uriSuffix;

        ScriptType(String uriSuffix)
        {
            _uriSuffix = uriSuffix;
        }

        public String getPropertyURI(ExpProtocol protocol)
        {
            return protocol.getLSID() + "#" + _uriSuffix;
        }
    }

    /**
     * File based QC and analysis scripts can be added to a protocol and invoked when the validate
     * method is called. Set to an empty list if no scripts exist.
     * @param protocol
     * @param scripts
     */
    void setValidationAndAnalysisScripts(ExpProtocol protocol, List<File> scripts, ScriptType type) throws ExperimentException;

    List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope, ScriptType type);

    /**
     * @return the data type that this run creates for its analyzed results
     */
    AssayDataType getDataType();

    /** @return a short description of this assay type - what kinds of data it can be used to analyze, etc */
    String getDescription();

    /**
     * Return the helper to handle data exchange between the server and external scripts.
     */
    DataExchangeHandler getDataExchangeHandler();

    DataTransformer getDataTransformer();
    DataValidator getDataValidator();
}
