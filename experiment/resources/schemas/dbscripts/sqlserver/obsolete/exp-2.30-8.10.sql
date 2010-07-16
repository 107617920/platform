/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
/* exp-2.30-2.31.sql */

ALTER TABLE exp.ExperimentRun ALTER COLUMN Name NVARCHAR(100)
GO

/* exp-2.31-2.32.sql */

-- Fix up sample sets that were incorrectly created in a folder other than their domain
-- https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=4976

UPDATE exp.materialsource SET container = (SELECT dd.container FROM exp.domaindescriptor dd WHERE exp.materialsource.lsid = dd.domainuri)
    WHERE rowid IN (SELECT ms.rowid FROM exp.materialsource ms, exp.domaindescriptor dd WHERE dd.domainuri = ms.lsid AND ms.container != dd.container)
GO

/* exp-2.32-2.33.sql */

-- Convert "FileLink" URIs to "Attachment" URIs in Lists
UPDATE exp.PropertyDescriptor SET RangeURI = 'http://www.labkey.org/exp/xml#attachment' WHERE PropertyId IN
(
	SELECT DISTINCT op.PropertyId FROM (SELECT ObjectId FROM exp.IndexInteger UNION SELECT ObjectId FROM exp.IndexVarchar) i INNER JOIN
		exp.ObjectProperty op ON i.ObjectId = op.ObjectId INNER JOIN
		exp.PropertyDescriptor pd ON op.PropertyId = pd.PropertyId
	WHERE RangeURI = 'http://cpas.fhcrc.org/exp/xml#fileLink'
)
GO

/* exp-2.33-2.34.sql */

CREATE INDEX IDX_Material_LSID ON exp.Material(LSID)
GO
