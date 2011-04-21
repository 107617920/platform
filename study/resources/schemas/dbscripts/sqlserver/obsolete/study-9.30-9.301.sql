/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

EXEC core.fn_dropifexists 'Study', 'study', 'DEFAULT', 'DateBased'
GO
ALTER TABLE study.Study ALTER COLUMN DateBased NVARCHAR(15)
GO
UPDATE study.Study SET DateBased = CASE WHEN DateBased = '1' THEN 'RELATIVE_DATE' ELSE 'VISIT' END
GO
ALTER TABLE study.Study ALTER COLUMN DateBased NVARCHAR(15) NOT NULL
GO
EXEC sp_RENAME 'study.Study.DateBased', 'TimepointType', 'COLUMN'
GO

