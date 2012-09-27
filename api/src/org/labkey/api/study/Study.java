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

package org.labkey.api.study;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.security.User;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * User: kevink
 * Date: May 27, 2009
 */
public interface Study extends StudyEntity
{
    Visit[] getVisits(Visit.Order order);

    DataSet getDataSet(int id);

    List<? extends DataSet> getDataSets();

    List<? extends DataSet> getDataSetsByType(String[] types);

    Site[] getSites();

    Cohort[] getCohorts(User user);

    ParticipantCategory[] getParticipantCategories(User user);

    boolean isAdvancedCohorts();

    TimepointType getTimepointType();

    Date getStartDate();

    String getSubjectNounSingular();

    String getSubjectNounPlural();

    String getSubjectColumnName();

    String getSearchDisplayTitle();

    String getSearchKeywords();

    String getSearchBody();

    String getDescription();

    String getDescriptionRendererType();

    String getDescriptionHtml();

    String getInvestigator();

    String getGrant();

    void attachProtocolDocument(List<AttachmentFile> files , User user)  throws SQLException, IOException;

    void removeProtocolDocument(String name, User user)  throws SQLException, IOException;

    List<Attachment> getProtocolDocuments ();

    boolean isAncillaryStudy();

    boolean isSnapshotStudy();

    Study getSourceStudy();

    boolean isEmptyStudy();

    /**
     * Attempts to resolve an existing visit.
     * @param visitID is used for visit-based studies.
     * @param date used for date-based.
     * @param participantID used for date-based.
     * @param returnPotentialTimepoints return timepoints that have not been created yet, but would be automatically
     * created if such a dataset or specimen row existed
     */
    Visit getVisit(String participantID, Double visitID, Date date, boolean returnPotentialTimepoints);

    /** For date-based studies, the default duration for timepoints, in days */
    int getDefaultTimepointDuration();

    String getAlternateIdPrefix();

    int getAlternateIdDigits();

}
