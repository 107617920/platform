/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.study;


import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;

public class DataspaceStudyFolderType extends StudyFolderType
{
    public static final String NAME = StudyService.DATASPACE_FOLDERTYPE_NAME;

    DataspaceStudyFolderType(StudyModule module)
    {
        super(NAME,
                "Work with all shared studies within the project.",
                module);
    }

    @Override
    public void configureContainer(Container container, User user)
    {
        super.configureContainer(container, user);
        StudyImpl study = new StudyImpl(container, container.getTitle());
        study.setTimepointType(TimepointType.VISIT);
//        s.setStartDate(new Date(DateUtil.parseDateTime(container, "2014-01-01")));
        study.setSubjectColumnName("SubjectID");
        study.setSubjectNounPlural("Subjects");
        study.setSubjectNounSingular("Subject");
        study.setShareDatasetDefinitions(Boolean.TRUE);
        StudyManager.getInstance().createStudy(user, study);
    }

    @Override
    public void unconfigureContainer(Container container, User user)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (null != study)
        {
            try
            {
                StudyManager.getInstance().deleteAllStudyData(container, user);     // TODO: should we retain study data?
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        super.unconfigureContainer(container, user);
    }

}
