/*
 * Copyright (c) 2011 LabKey Corporation
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

/* mothership-0.00-2.10.sql */

EXEC sp_addapprole 'mothership', 'password'
GO

CREATE TABLE mothership.ExceptionStackTrace
(
    ExceptionStackTraceId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
    StackTrace TEXT NOT NULL,
    StackTraceHash VARCHAR(50) NOT NULL,
    AssignedTo USERID,
    BugNumber INT,

    CONSTRAINT PK_ExceptionStackTrace PRIMARY KEY (ExceptionStackTraceId),
    CONSTRAINT UQ_ExceptionStackTraceId_StackTraceHashContainer UNIQUE (StackTraceHash, Container),
    CONSTRAINT FK_ExceptionStackTrace_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_ExceptionStackTrace_AssignedTo FOREIGN KEY (AssignedTo) REFERENCES core.Usersdata(UserId)
)
GO


CREATE TABLE mothership.ServerInstallation
(
    ServerInstallationId INT IDENTITY(1,1) NOT NULL,
    ServerInstallationGUID ENTITYID NOT NULL,
    Note VARCHAR(100),
    Container ENTITYID NOT NULL,
    SystemDescription VARCHAR(200),
    LogoLink VARCHAR(200),
    OrganizationName VARCHAR(200),
    SystemShortName VARCHAR(200),
    ServerIP VARCHAR(20),

    CONSTRAINT PK_ServerInstallation PRIMARY KEY (ServerInstallationId),
    CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID UNIQUE (ServerInstallationGUID),
    CONSTRAINT FK_ServerInstallation_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
)
GO

CREATE TABLE mothership.ServerSession
(
    ServerSessionId INT IDENTITY(1,1) NOT NULL,
    ServerInstallationId INT,
    ServerSessionGUID ENTITYID NOT NULL,
    EarliestKnownTime DATETIME NOT NULL,
    LastKnownTime DATETIME NOT NULL,
    Container ENTITYID NOT NULL,
    DatabaseProductName VARCHAR(200),
    DatabaseProductVersion VARCHAR(200),
    DatabaseDriverName VARCHAR(200),
    DatabaseDriverVersion VARCHAR(200),
    RuntimeOS VARCHAR(100),
    SVNRevision INT,

    CONSTRAINT PK_ServerSession PRIMARY KEY (ServerSessionId),
    CONSTRAINT UQ_ServerSession_ServerSessionGUID UNIQUE (ServerSessionGUID),
    CONSTRAINT FK_ServerSession_ServerInstallation FOREIGN KEY (ServerInstallationId) REFERENCES mothership.ServerInstallation(ServerInstallationId),
    CONSTRAINT FK_ServerSession_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
)
GO

CREATE TABLE mothership.ExceptionReport
(
    ExceptionReportId INT IDENTITY(1,1) NOT NULL,
    ExceptionStackTraceId INT,
    Created DATETIME DEFAULT GETDATE(),
    URL VARCHAR(512),
    ServerSessionId INT NOT NULL,
    Username VARCHAR(50),
    Browser VARCHAR(100),

    CONSTRAINT PK_ExceptionReport PRIMARY KEY (ExceptionReportId),
    CONSTRAINT FK_ExceptionReport_ExceptionStackTrace FOREIGN KEY (ExceptionStackTraceId) REFERENCES mothership.ExceptionStackTrace(ExceptionStackTraceId),
    CONSTRAINT FK_ExceptionReport_ServerSessionId FOREIGN KEY (ServerSessionId) REFERENCES mothership.ServerSession(ServerSessionId)
)
GO

ALTER TABLE mothership.ExceptionReport ADD ReferrerURL VARCHAR(512)
GO
ALTER TABLE mothership.ServerInstallation ADD ServerHostName VARCHAR(256)
GO
ALTER TABLE mothership.ExceptionStackTrace ADD Comments TEXT
GO

CREATE TABLE mothership.SoftwareRelease
(
    ReleaseId INT IDENTITY(1,1) NOT NULL,
    SVNRevision INT NOT NULL,
    Description VARCHAR(50) NOT NULL,
    Container ENTITYID NOT NULL,

    CONSTRAINT PK_SoftwareRelease PRIMARY KEY (ReleaseId),
    CONSTRAINT UQ_SoftwareRelease UNIQUE (Container, SVNRevision)
)
GO

ALTER TABLE mothership.ExceptionReport ADD PageflowName VARCHAR(30)
GO

ALTER TABLE mothership.ExceptionReport ADD PageflowAction VARCHAR(40)
GO

CREATE INDEX IX_ServerSession_ServerInstallationId ON mothership.serversession(serverinstallationid)
GO
CREATE INDEX IX_ExceptionReport_ExceptionStackTraceId ON mothership.exceptionreport(exceptionstacktraceid)
GO
CREATE INDEX IX_ExceptionReport_ServerSessionId ON mothership.exceptionreport(serversessionid)
GO

CREATE INDEX IX_ServerInstallation_Container ON mothership.ServerInstallation(container)
GO
CREATE INDEX IX_ExceptionStackTrace_Container ON mothership.ExceptionStackTrace(container)
GO

/* mothership-2.10-2.20.sql */

ALTER TABLE mothership.ExceptionStackTrace DROP CONSTRAINT UQ_ExceptionStackTraceId_StackTraceHashContainer
GO

/* mothership-2.20-2.30.sql */

ALTER TABLE mothership.ServerSession ADD JavaVersion VARCHAR(100)
GO

ALTER TABLE mothership.ServerSession ADD UserCount INT
GO
ALTER TABLE mothership.ServerSession ADD ActiveUserCount INT
GO
ALTER TABLE mothership.ServerSession ADD ProjectCount INT
GO
ALTER TABLE mothership.ServerSession ADD ContainerCount INT
GO

ALTER TABLE mothership.ServerSession ADD AdministratorEmail NVARCHAR(100)
GO

/* mothership-2.30-8.10.sql */

ALTER TABLE mothership.ServerSession ADD EnterprisePipelineEnabled BIT
GO

ALTER TABLE mothership.ServerSession ADD LDAPEnabled BIT
GO

ALTER TABLE mothership.ExceptionReport ADD SQLState VARCHAR(100)
GO

/* mothership-8.30-9.10.sql */

-- Migrate from using just the SVN revision to tracking the revision and URL.
ALTER TABLE mothership.softwarerelease ADD CONSTRAINT FK_SoftwareRelease_Container
    FOREIGN KEY (Container) REFERENCES core.containers(EntityId)
GO

-- Handle null revisions, which happens when building from a source distribution instead of SVN
ALTER TABLE mothership.SoftwareRelease ALTER COLUMN SVNRevision INT NULL
GO

INSERT INTO mothership.SoftwareRelease (Container, SVNRevision, Description)
    SELECT TOP 1 Container, NULL as Revision, 'NotSVN' as Description FROM mothership.ServerSession
GO

-- Change the PK
ALTER TABLE mothership.ServerSession ADD SoftwareReleaseId INT
GO
ALTER TABLE mothership.SoftwareRelease DROP CONSTRAINT pk_softwarerelease
GO
ALTER TABLE mothership.SoftwareRelease DROP COLUMN ReleaseId
GO
ALTER TABLE mothership.SoftwareRelease ADD SoftwareReleaseId INT IDENTITY
GO
ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT pk_softwarerelease PRIMARY KEY (SoftwareReleaseId)
GO

ALTER TABLE mothership.ServerSession ALTER COLUMN SoftwareReleaseId INT NOT NULL
GO

ALTER TABLE mothership.SoftwareRelease ADD SVNURL NVARCHAR(200)
GO

ALTER TABLE mothership.SoftwareRelease DROP CONSTRAINT uq_softwarerelease
GO
ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT uq_softwarerelease UNIQUE (container, svnrevision, svnurl)
GO

ALTER TABLE mothership.ServerSession DROP COLUMN SVNRevision
GO

ALTER TABLE mothership.serversession ADD CONSTRAINT FK_ServerSession_SoftwareRelease FOREIGN KEY (SoftwareReleaseId)
    REFERENCES mothership.SoftwareRelease(SoftwareReleaseId)
GO