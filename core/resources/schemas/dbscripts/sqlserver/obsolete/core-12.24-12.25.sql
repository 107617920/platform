/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

-- More specific name
EXEC sp_rename 'portal.Pages','PortalPages';

-- Move "portal" tables to "core" schema
ALTER SCHEMA core TRANSFER portal.PortalPages;
ALTER SCHEMA core TRANSFER portal.PortalWebParts;
DROP SCHEMA portal;

-- End of the line for "portal" scripts... going forward, all changes to these tables should be in "core" scripts;
