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

/* comm-0.00-1.10.sql */

-- Create schema comm: tables for Announcements and Wiki

CREATE SCHEMA comm;

CREATE TABLE comm.Announcements
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Container ENTITYID NOT NULL,
    Parent ENTITYID,
    Title VARCHAR(255),
    Expires TIMESTAMP,
    Body TEXT,
    RendererType VARCHAR(50) NULL,       -- Updates to properties will result in NULL body and NULL render type
    EmailList VARCHAR(1000) NULL,        -- History of addresses that were notified
    Status VARCHAR(50) NULL,
    AssignedTo USERID NULL,
    DiscussionSrcIdentifier VARCHAR(100) NULL,
    DiscussionSrcURL VARCHAR(1000) NULL,

    CONSTRAINT PK_Announcements PRIMARY KEY (RowId),
    CONSTRAINT UQ_Announcements UNIQUE (Container, Parent, RowId)
);

CREATE INDEX IX_DiscussionSrcIdentifier ON comm.announcements(Container, DiscussionSrcIdentifier);

CREATE TABLE comm.Pages
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Owner USERID,
    Container ENTITYID NOT NULL,
    Name VARCHAR(255) NOT NULL,
    Parent INT NOT NULL,
    DisplayOrder REAL NOT NULL,
    PageVersionId INT4 NULL,
    ShowAttachments BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT PK_Pages PRIMARY KEY (EntityId),
    CONSTRAINT UQ_Pages UNIQUE (Container, Name)
);

/* comm-1.20-1.30.sql */

CREATE TABLE comm.PageVersions
(
    RowId SERIAL NOT NULL,
    PageEntityId ENTITYID NOT NULL,
    Created TIMESTAMP,
    CreatedBy USERID,
    Owner USERID,
    Version INT4 NOT NULL,
    Title VARCHAR(255),
    Body TEXT,
    RendererType VARCHAR(50) NOT NULL DEFAULT 'RADEOX',

    CONSTRAINT PK_PageVersions PRIMARY KEY (RowId),
    CONSTRAINT FK_PageVersions_Pages FOREIGN KEY (PageEntityId) REFERENCES comm.Pages(EntityId),
    CONSTRAINT UQ_PageVersions UNIQUE (PageEntityId, Version)
);

/* comm-1.30-1.40.sql */

ALTER TABLE comm.Pages
    ADD CONSTRAINT FK_Pages_PageVersions FOREIGN KEY (PageVersionId) REFERENCES comm.PageVersions (RowId);

/* comm-1.50-1.60.sql */

CREATE TABLE comm.EmailOptions
(
    EmailOptionId INT4 NOT NULL,
    EmailOption VARCHAR(50),

    CONSTRAINT PK_EmailOptions PRIMARY KEY (EmailOptionId)
);

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (0, 'No Email');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (1, 'All conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (2, 'My conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations');

CREATE TABLE comm.EmailFormats
(
    EmailFormatId INT4 NOT NULL,
    EmailFormat VARCHAR(20),

    CONSTRAINT PK_EmailFormats PRIMARY KEY (EmailFormatId)
);

INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat) VALUES (0, 'Plain Text');
INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat) VALUES (1, 'HTML');

CREATE TABLE comm.PageTypes
(
    PageTypeId INT4 NOT NULL,
    PageType VARCHAR(20),

    CONSTRAINT PK_PageTypes PRIMARY KEY (PageTypeId)
);

INSERT INTO comm.PageTypes (PageTypeId, PageType) VALUES (0, 'Message');
INSERT INTO comm.PageTypes (PageTypeId, PageType) VALUES (1, 'Wiki');

CREATE TABLE comm.EmailPrefs
(
    Container ENTITYID,
    UserId USERID,
    EmailOptionId INT4 NOT NULL,
    EmailFormatId INT4 NOT NULL,
    PageTypeId INT4 NOT NULL,
    LastModifiedBy USERID,

    CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId),
    CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId),
    CONSTRAINT FK_EmailPrefs_EmailOptions FOREIGN KEY (EmailOptionId) REFERENCES comm.EmailOptions (EmailOptionId),
    CONSTRAINT FK_EmailPrefs_EmailFormats FOREIGN KEY (EmailFormatId) REFERENCES comm.EmailFormats (EmailFormatId),
    CONSTRAINT FK_EmailPrefs_PageTypes FOREIGN KEY (PageTypeId) REFERENCES comm.PageTypes (PageTypeId)
);

/* comm-1.60-1.70.sql */

-- Discussions can be private, constrained to a certain subset of users (like a Cc: line)
CREATE TABLE comm.UserList
(
    MessageId INT NOT NULL,
    UserId USERID NOT NULL,

    CONSTRAINT PK_UserList PRIMARY KEY (MessageId, UserId)
);

-- Improve performance of user list lookups for permission checking
CREATE INDEX IX_UserList_UserId ON comm.UserList(UserId);

/* comm-9.30-10.10.sql */

ALTER TABLE comm.Pages ADD LastIndexed TIMESTAMP NULL;

ALTER TABLE comm.Announcements ADD LastIndexed TIMESTAMP NULL;

INSERT INTO comm.EmailOptions (EmailOptionID, EmailOption) VALUES (3, 'Broadcast only');