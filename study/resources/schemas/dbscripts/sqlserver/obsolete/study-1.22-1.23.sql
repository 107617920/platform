/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
CREATE TABLE study.Report
(
    ReportId INT IDENTITY NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ContainerId ENTITYID NOT NULL,
    Label NVARCHAR(100) NOT NULL,
    Params NVARCHAR(512) NULL,
    ReportType NVARCHAR(100) NOT NULL,
    Scope INT NOT NULL,

    CONSTRAINT PK_Report PRIMARY KEY (ReportId),
    CONSTRAINT UQ_Report UNIQUE (ContainerId, Label)
);