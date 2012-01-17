/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.study.InvalidFileException;
import org.labkey.api.study.StudyImportException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.CohortManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.CohortMode;
import org.labkey.study.xml.CohortType;
import org.labkey.study.xml.CohortsDocument;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:26:29 PM
 */
public class CohortImporter implements InternalStudyImporter
{
    public String getDescription()
    {
        return "cohort settings";
    }

    public void process(StudyImpl study, ImportContext ctx, VirtualFile root, BindException errors) throws IOException, SQLException, ServletException, StudyImportException
    {
        StudyDocument.Study.Cohorts cohortsXml = ctx.getStudyXml().getCohorts();

        if (null != cohortsXml)
        {
            CohortType.Enum cohortType = cohortsXml.getType();
            CohortMode.Enum cohortMode = cohortsXml.getMode();

            if (cohortType == CohortType.AUTOMATIC)
            {
                ctx.getLogger().info("Loading automatic cohort settings");
                Integer dataSetId = cohortsXml.getDatasetId();
                String dataSetProperty = cohortsXml.getDatasetProperty();
                CohortManager.getInstance().setAutomaticCohortAssignment(study, ctx.getUser(), dataSetId, dataSetProperty,
                        cohortMode == CohortMode.ADVANCED, true);
            }
            else
            {
                String cohortFileName = cohortsXml.getFile();
                ctx.getLogger().info("Loading manual cohort assignments from " + root.getRelativePath(cohortFileName));
                CohortsDocument cohortAssignmentXml;

                try
                {
                    XmlObject xml = root.getXmlBean(cohortFileName);
                    if (xml instanceof CohortsDocument)
                    {
                        cohortAssignmentXml = (CohortsDocument)xml;
                        XmlBeansUtil.validateXmlDocument(cohortAssignmentXml);
                    }
                    else
                        throw new StudyImportException("Unable to get an instance of CohortsDocument");
                }
                catch (XmlValidationException e)
                {
                    throw new InvalidFileException(root.getRelativePath(cohortFileName), e);
                }

                Map<String, Integer> p2c = new HashMap<String, Integer>();
                CohortsDocument.Cohorts.Cohort[] cohortXmls = cohortAssignmentXml.getCohorts().getCohortArray();

                for (CohortsDocument.Cohorts.Cohort cohortXml : cohortXmls)
                {
                    String label = cohortXml.getLabel();
                    CohortImpl cohort = CohortManager.getInstance().ensureCohort(study, ctx.getUser(), label);

                    for (String ptid : cohortXml.getIdArray())
                        p2c.put(ptid, cohort.getRowId());
                }

                CohortManager.getInstance().setManualCohortAssignment(study, ctx.getUser(), p2c);
            }
        }
    }
}
