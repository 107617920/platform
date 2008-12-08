/*
 * Copyright (c) 2008 LabKey Corporation
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

-- Migrate from using just the SVN revision to tracking the revision and URL.

-- Make sure that we're a real FK to container
DELETE FROM mothership.softwarerelease WHERE Container NOT IN (SELECT EntityId FROM core.containers);

ALTER TABLE mothership.softwarerelease ADD CONSTRAINT FK_SoftwareRelease_Container
    FOREIGN KEY (Container) REFERENCES core.containers(EntityId);

-- Make sure that we have a release entry for every report we've gotten
INSERT INTO mothership.SoftwareRelease (Container, SVNRevision, Description)
    SELECT DISTINCT si.Container, ss.SVNRevision, ss.SVNRevision::VARCHAR(40)
        FROM mothership.ServerSession ss, mothership.ServerInstallation si
        WHERE si.ServerInstallationId = ss.ServerInstallationId AND SVNRevision NOT IN
            (SELECT SVNRevision FROM mothership.SoftwareRelease sr WHERE sr.Container = si.Container);

-- Handle null revisions, which happens when building from a source distribution instead of SVN
ALTER TABLE mothership.SoftwareRelease ALTER COLUMN SVNRevision DROP NOT NULL;

INSERT INTO mothership.SoftwareRelease (Container, SVNRevision, Description)
    VALUES ((SELECT MIN(Container) FROM mothership.SoftwareRelease), NULL, 'NotSVN');

-- Change the PK
ALTER TABLE mothership.ServerSession ADD SoftwareReleaseId INT;
ALTER TABLE mothership.SoftwareRelease ADD SoftwareReleaseId SERIAL;
ALTER TABLE mothership.SoftwareRelease DROP COLUMN ReleaseId;
ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT pk_softwarerelease PRIMARY KEY (SoftwareReleaseId);

-- Point to the row in the release table
UPDATE mothership.ServerSession SET SoftwareReleaseId =
    (SELECT sr.SoftwareReleaseId FROM mothership.SoftwareRelease sr
        WHERE (sr.SVNRevision = mothership.ServerSession.SVNRevision OR (sr.SVNRevision IS NULL AND mothership.ServerSession.SVNRevision IS NULL)) AND
            mothership.ServerSession.Container = sr.Container);

ALTER TABLE mothership.ServerSession ALTER COLUMN SoftwareReleaseId SET NOT NULL;

ALTER TABLE mothership.SoftwareRelease ADD SVNURL VARCHAR(200);

ALTER TABLE mothership.SoftwareRelease DROP CONSTRAINT uq_softwarerelease;
ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT uq_softwarerelease UNIQUE (container, svnrevision, svnurl);

ALTER TABLE mothership.ServerSession DROP COLUMN SVNRevision;

ALTER TABLE mothership.serversession ADD CONSTRAINT FK_ServerSession_SoftwareRelease FOREIGN KEY (SoftwareReleaseId)
    REFERENCES mothership.SoftwareRelease(SoftwareReleaseId);