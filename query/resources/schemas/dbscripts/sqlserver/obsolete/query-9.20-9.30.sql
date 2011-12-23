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

/* query-9.20-9.21.sql */

-- Support other DataSources in external schemas (e.g., SAS, other PostgreSQL servers, etc.)

-- First, add DataSource as nullable
ALTER TABLE query.DbUserSchema ADD DataSource NVARCHAR(50) NULL
GO

-- Populate all existing rows with jndi name of the core DataSource (requires code)
EXEC core.executeJavaUpgradeCode 'populateDataSourceColumn'
GO

-- Column should be completely populated -- make it non-nullable
ALTER TABLE query.DbUserSchema ALTER COLUMN DataSource NVARCHAR(50) NOT NULL
GO

/* query-9.21-9.22.sql */

-- Remove jdbc/ prefix from datasources -- NOT needed in the consolidated 9.20-9.30 script
UPDATE query.DbUserSchema SET DataSource = RIGHT(DataSource, LEN(DataSource) - 5) WHERE DataSource LIKE 'jdbc/%'
GO

/* query-9.22-9.23.sql */

-- Remove unused column
ALTER TABLE query.DbUserSchema DROP COLUMN DbContainer
GO