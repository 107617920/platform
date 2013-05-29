/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.study.Visit;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitDataSet;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.xml.DatasetType;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.VisitMapDocument;
import org.labkey.study.xml.VisitMapDocument.VisitMap;
import org.labkey.study.xml.VisitMapDocument.VisitMap.ImportAliases;
import org.labkey.study.xml.VisitMapDocument.VisitMap.ImportAliases.Alias;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:57:56 AM
 */
public class XmlVisitMapWriter implements Writer<StudyImpl, StudyExportContext>
{
    public static final String FILENAME = "visit_map.xml";

    public String getSelectionText()
    {
        return null;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws IOException, ImportException, SQLException
    {
        List<VisitImpl> visits = study.getVisits(Visit.Order.DISPLAY);
        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.Visits visitsXml = studyXml.addNewVisits();
        visitsXml.setFile(FILENAME);

        VisitMapDocument visitMapDoc = VisitMapDocument.Factory.newInstance();
        VisitMap visitMapXml = visitMapDoc.addNewVisitMap();

        Set<Integer> visitsToExport = ctx.getVisitIds();

        for (VisitImpl visit : visits)
        {
            if (visitsToExport == null || visitsToExport.contains(visit.getId()))
            {
                VisitMap.Visit visitXml = visitMapXml.addNewVisit();

                if (null != visit.getLabel())
                    visitXml.setLabel(visit.getLabel());

                if (null != visit.getTypeCode())
                    visitXml.setTypeCode(String.valueOf(visit.getTypeCode()));

                // Only set if false; default value is "true"
                if (!visit.isShowByDefault())
                    visitXml.setShowByDefault(visit.isShowByDefault());

                visitXml.setSequenceNum(visit.getSequenceNumMin());

                if (visit.getSequenceNumMin() != visit.getSequenceNumMax())
                    visitXml.setMaxSequenceNum(visit.getSequenceNumMax());

                if (null != visit.getCohort())
                    visitXml.setCohort(visit.getCohort().getLabel());

                if (null != visit.getVisitDateDatasetId() && ctx.isExportedDataset(visit.getVisitDateDatasetId()))
                    visitXml.setVisitDateDatasetId(visit.getVisitDateDatasetId());

                if (visit.getDisplayOrder() > 0)
                    visitXml.setDisplayOrder(visit.getDisplayOrder());

                if (visit.getChronologicalOrder() > 0)
                    visitXml.setChronologicalOrder(visit.getChronologicalOrder());

                if (null != visit.getSequenceNumHandling())
                    visitXml.setSequenceNumHandling(visit.getSequenceNumHandling());

                List<VisitDataSet> vds = visit.getVisitDataSets();

                if (!vds.isEmpty())
                {
                    VisitMap.Visit.Datasets datasetsXml = visitXml.addNewDatasets();

                    for (VisitDataSet vd : vds)
                    {
                        if (ctx.isExportedDataset(vd.getDataSetId()))
                        {
                            VisitMap.Visit.Datasets.Dataset datasetXml = datasetsXml.addNewDataset();
                            datasetXml.setId(vd.getDataSetId());
                            datasetXml.setType(vd.isRequired() ? DatasetType.REQUIRED : DatasetType.OPTIONAL);
                        }
                    }
                }
            }
        }

        Collection<StudyManager.VisitAlias> aliases = StudyManager.getInstance().getCustomVisitImportMapping(study);

        if (!aliases.isEmpty())
        {
            ImportAliases ia = visitMapXml.addNewImportAliases();

            for (StudyManager.VisitAlias alias : aliases)
            {
                Alias aliasXml = ia.addNewAlias();
                aliasXml.setName(alias.getName());
                aliasXml.setSequenceNum(alias.getSequenceNum());
            }
        }

        vf.saveXmlBean(FILENAME, visitMapDoc);
    }
}
