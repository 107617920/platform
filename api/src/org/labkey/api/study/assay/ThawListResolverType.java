/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.*;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.tools.ColumnDescriptor;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Sep 20, 2007
 */
public class ThawListResolverType extends AssayFileWriter implements ParticipantVisitResolverType
{
    public static final String THAW_LIST_TYPE_INPUT_NAME = "ThawListType";
    public static final String THAW_LIST_TEXT_AREA_INPUT_NAME = "ThawListTextArea";
    static final String THAW_LIST_LIST_DEFINITION_INPUT_NAME = "ThawListListDefinition";

    public static final String NAMESPACE_PREFIX = "ThawList";
    public static final String LIST_NAMESPACE_SUFFIX = "List";
    public static final String TEXT_NAMESPACE_SUFFIX = "Text";
    public static final String THAW_LIST_LIST_CONTAINER_INPUT_NAME = "ThawListList-Container";
    public static final String THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME = "ThawListList-SchemaName";
    public static final String THAW_LIST_LIST_QUERY_NAME_INPUT_NAME = "ThawListList-QueryName";

    public ParticipantVisitResolver createResolver(Collection<ExpMaterial> inputMaterials,
                                                   Collection<ExpData> inputDatas,
                                                   Collection<ExpMaterial> outputMaterials,
                                                   Collection<ExpData> outputDatas,
                                                   Container runContainer,
                                                   Container targetStudyContainer, User user) throws IOException, ExperimentException
    {
        ExpData thawListData = null;
        Lsid lsid = null;
        for (ExpData inputData : inputDatas)
        {
            lsid = new Lsid(inputData.getLSID());
            if (NAMESPACE_PREFIX.equals(lsid.getNamespacePrefix()))
            {
                thawListData = inputData;
                break;
            }
        }

        if (thawListData == null)
        {
            throw new ExperimentException("Could not find a thaw list for run");
        }

        ParticipantVisitResolver childResolver = new StudyParticipantVisitResolver(runContainer, targetStudyContainer);

        if (lsid.getNamespaceSuffix().startsWith(LIST_NAMESPACE_SUFFIX))
        {
            String objectId = lsid.getObjectId();
            int index = objectId.indexOf('.');
            if (index == -1)
            {
                throw new ExperimentException("Could not determine schema and query for data with LSID " + lsid);
            }
            String schemaName = objectId.substring(0, index);
            String queryName = objectId.substring(index + 1);
            Container listContainer = ContainerManager.getForPath(lsid.getVersion());
            if (listContainer == null)
            {
                throw new ExperimentException("Could not find container " + lsid.getVersion() + " for data with LSID " + lsid);
            }
            return new ThawListListResolver(runContainer, targetStudyContainer, listContainer, schemaName, queryName, user, childResolver);
        }
        else
        {
            File file = thawListData.getFile();
            if (file == null || !NetworkDrive.exists(file))
            {
                throw new ExperimentException("Could not find a thaw list for run");
            }

            Map<String, ParticipantVisit> values = new HashMap<String, ParticipantVisit>();
            TabLoader tabLoader = new TabLoader(file);
            ColumnDescriptor[] cols = tabLoader.getColumns();
            if (tabLoader.getSkipLines() == 0)
            {
                tabLoader = new TabLoader(file);
                tabLoader.setSkipLines(1);
                cols = tabLoader.getColumns();
            }

            for (Map<String, Object> data : (Map<String, Object>[]) tabLoader.load())
            {
                Object index = data.get("Index");
                Object specimenIDObject = data.get("SpecimenID");
                String specimenID = specimenIDObject == null ? null : specimenIDObject.toString();
                Object participantIDObject = data.get("ParticipantID");
                String participantID = participantIDObject == null ? null : participantIDObject.toString();
                Object visitIDObject = data.get("VisitID");
                if (visitIDObject != null && !(visitIDObject instanceof Number))
                {
                    throw new ExperimentException("The VisitID column in the thaw list must be a number.");
                }
                Double visitID = visitIDObject == null ? null : ((Number) visitIDObject).doubleValue();
                Object dateObject = data.get("Date");
                if (dateObject != null && !(dateObject instanceof Date))
                {
                    throw new ExperimentException("The Date column in the thaw list must be a date.");
                }
                Date date = (Date) dateObject;
                values.put(index == null ? null : index.toString(), new ParticipantVisitImpl(specimenID, participantID, visitID, date, runContainer));
            }
            return new ThawListFileResolver(childResolver, values, runContainer);
        }
    }

    public ParticipantVisitResolver createResolver(ExpRun run, Container targetStudyContainer, User user) throws IOException, ExperimentException
    {
        return createResolver(run.getMaterialInputs().keySet(),
                run.getDataInputs().keySet(),
                run.getMaterialOutputs(),
                run.getDataOutputs(), run.getContainer(),
                targetStudyContainer, user);
    }

    public String getName()
    {
        return "Lookup";
    }

    public String getDescription()
    {
        return "Sample indices, which map to a lookup.";
    }

    public void render(RenderContext ctx) throws Exception
    {
        Map<String, String> gwtProps = new HashMap<String, String>();
        gwtProps.put("dialogTitle", "Select a Thaw List");
        GWTView listChooser = new GWTView("org.labkey.assay.upload.ListChooser", gwtProps);
        listChooser.getModelBean().getProperties().put("pageFlow", "assay");
        JspView<ThawListBean> view = new JspView<ThawListBean>("/org/labkey/api/study/assay/thawListSelector.jsp", new ThawListBean(ctx, listChooser));
        view.render(ctx.getRequest(), ctx.getViewContext().getResponse());

        // hack for 4404 : Lookup picker performance is terrible when there are many containers
        ContainerManager.getAllChildren(ContainerManager.getRoot());
    }

    public void addHiddenFormFields(InsertView view, AssayRunUploadForm form)
    {
        view.getDataRegion().addHiddenFormField(THAW_LIST_TYPE_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_TYPE_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_LIST_DEFINITION_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_LIST_DEFINITION_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_TEXT_AREA_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_TEXT_AREA_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_LIST_CONTAINER_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_LIST_CONTAINER_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_LIST_QUERY_NAME_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_LIST_QUERY_NAME_INPUT_NAME));
        view.getDataRegion().addHiddenFormField(THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME, form.getRequest().getParameter(THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME));
    }


    public void configureRun(AssayRunUploadContext context, ExpRun run, Map<PropertyDescriptor, String> runProperties, Map<PropertyDescriptor, String> uploadSetProperties, Map<ExpData, String> inputDatas) throws ExperimentException
    {
        String type = context.getRequest().getParameter(THAW_LIST_TYPE_INPUT_NAME);

        InputStream in = null;

        ExpData thawListData = ExperimentService.get().createData(context.getContainer(), new DataType(NAMESPACE_PREFIX));
        String name;
        String dataLSID;

        if (TEXT_NAMESPACE_SUFFIX.equals(type))
        {
            try
            {
                String text = context.getRequest().getParameter(THAW_LIST_TEXT_AREA_INPUT_NAME);
                if (text != null)
                {
                    in = new ByteArrayInputStream(text.getBytes());
                }

                File uploadDir = ensureUploadDirectory(context.getProtocol(), context);
                File file = createFile(context.getProtocol(), uploadDir, "thawList");
                try
                {
                    writeFile(in, file);
                }
                catch (IOException e)
                {
                    throw new ExperimentException(e);
                }
                name = file.getName();
                dataLSID = new Lsid(NAMESPACE_PREFIX, TEXT_NAMESPACE_SUFFIX + ".Folder-" + context.getContainer().getRowId(),
                        name).toString();
                try
                {
                    thawListData.setDataFileUrl(file.getCanonicalFile().toURI().toString());
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
            finally
            {
                if (in != null) { try { in.close(); } catch (IOException e) {} }
            }
        }
        else if (LIST_NAMESPACE_SUFFIX.equals(type))
        {
            String containerName = context.getRequest().getParameter(THAW_LIST_LIST_CONTAINER_INPUT_NAME);
            String schemaName = context.getRequest().getParameter(THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME);
            String queryName = context.getRequest().getParameter(THAW_LIST_LIST_QUERY_NAME_INPUT_NAME);
            Container container;
            if (containerName == null || "".equals(containerName))
            {
                container = context.getContainer();
            }
            else
            {
                container = ContainerManager.getForPath(containerName);
            }

            if (container == null || !container.hasPermission(context.getUser(), ACL.PERM_READ))
            {
                throw new ExperimentException("Could not reference container " + containerName);
            }

            UserSchema schema = QueryService.get().getUserSchema(context.getUser(), container, schemaName);
            if (schema == null)
            {
                throw new ExperimentException("Could not find schema " + schemaName);
            }
            TableInfo table = schema.getTable(queryName, null);
            if (table == null)
            {
                throw new ExperimentException("Could not find table " + queryName);
            }

            name = schemaName + "." + queryName + " in " + container.getPath();
            Lsid lsid = new Lsid(NAMESPACE_PREFIX, LIST_NAMESPACE_SUFFIX + ".Folder-" + context.getContainer().getRowId(),
                    schemaName + "." + queryName);
            lsid.setVersion(container.getPath());
            dataLSID = lsid.toString();

            ExpData existingData = ExperimentService.get().getExpData(dataLSID);
            if (existingData != null)
            {
                thawListData = existingData;
            }
        }
        else
        {
            throw new IllegalArgumentException("Unsupported thaw list type: " + type);
        }

        thawListData.setName(name);
        thawListData.setLSID(dataLSID);
        inputDatas.put(thawListData, "ThawList");
    }

    public void putDefaultProperties(HttpServletRequest request, Map<String, String> properties)
    {
        String type = request.getParameter(THAW_LIST_TYPE_INPUT_NAME);
        properties.put(THAW_LIST_TYPE_INPUT_NAME, type);
        if (LIST_NAMESPACE_SUFFIX.equals(type))
        {
            properties.put(THAW_LIST_LIST_CONTAINER_INPUT_NAME, request.getParameter(THAW_LIST_LIST_CONTAINER_INPUT_NAME));
            properties.put(THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME, request.getParameter(THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME));
            properties.put(THAW_LIST_LIST_QUERY_NAME_INPUT_NAME, request.getParameter(THAW_LIST_LIST_QUERY_NAME_INPUT_NAME));
        }
    }

    public boolean collectPropertyOnUpload(String propertyName, AssayRunUploadContext uploadContext)
    {
        return !(propertyName.equals(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME) ||
                propertyName.equals(AbstractAssayProvider.VISITID_PROPERTY_NAME) ||
                propertyName.equals(AbstractAssayProvider.DATE_PROPERTY_NAME));
    }
}
