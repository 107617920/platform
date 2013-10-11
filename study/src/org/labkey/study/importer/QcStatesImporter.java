/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.study.importer;

import com.drew.lang.annotations.Nullable;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.qcStates.StudyqcDocument;
import org.labkey.study.xml.studyViews.ViewsDocument;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:36:25 PM
 */
public class QcStatesImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "QC States Importer";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer());
        StudyDocument.Study.QcStates qcStates = ctx.getXml().getQcStates();

        if (null != qcStates)
        {
            ctx.getLogger().info("Loading QC states");
            StudyController.ManageQCStatesForm qcForm = new StudyController.ManageQCStatesForm();
            StudyqcDocument doc = getSettingsFile(ctx, root);

            // if the import provides a study qc document (new in 13.3), parse it for the qc states, else
            // revert back to the previous behavior where we just set the default data visibility
            if (doc != null)
            {
                StudyqcDocument.Studyqc qcXml = doc.getStudyqc();
                StudyqcDocument.Studyqc.Qcstates states = qcXml.getQcstates();
                Map<String, Integer> stateMap = new HashMap<>();

                if (states != null)
                {
                    for (StudyqcDocument.Studyqc.Qcstates.Qcstate state : states.getQcstateArray())
                    {
                        QCState newState = new QCState();
                        newState.setContainer(ctx.getContainer());
                        newState.setLabel(state.getName());
                        newState.setDescription(state.getDescription());
                        newState.setPublicData(state.getPublic());

                        newState = StudyManager.getInstance().insertQCState(ctx.getUser(), newState);
                        stateMap.put(newState.getLabel(), newState.getRowId());
                    }
                }

                // make the default qc state assignments for dataset inserts/updates
                String pipelineDefault = qcXml.getPipelineImportDefault();
                if (stateMap.containsKey(pipelineDefault))
                    qcForm.setDefaultPipelineQCState(stateMap.get(pipelineDefault));

                String assayDefault = qcXml.getAssayDataDefault();
                if (stateMap.containsKey(assayDefault))
                    qcForm.setDefaultAssayQCState(stateMap.get(assayDefault));

                String datasetDefault = qcXml.getInsertUpdateDefault();
                if (stateMap.containsKey(datasetDefault))
                    qcForm.setDefaultDirectEntryQCState(stateMap.get(datasetDefault));

                qcForm.setShowPrivateDataByDefault(qcXml.getShowPrivateDataByDefault());
                qcForm.setBlankQCStatePublic(qcXml.getBlankQCStatePublic());

                StudyController.updateQcState(study, ctx.getUser(), qcForm);
            }
            else
            {
                qcForm.setShowPrivateDataByDefault(qcStates.getShowPrivateDataByDefault());
                StudyController.updateQcState(study, ctx.getUser(), qcForm);
            }
        }
    }

    @Nullable
    private StudyqcDocument getSettingsFile(StudyImportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study.QcStates qcXml  = ctx.getXml().getQcStates();

        if (qcXml != null)
        {
            String fileName = qcXml.getFile();

            if (fileName != null)
            {
                XmlObject doc = root.getXmlBean(fileName);
                if (doc instanceof StudyqcDocument)
                    return (StudyqcDocument)doc;
            }
        }
        return null;
    }
}
