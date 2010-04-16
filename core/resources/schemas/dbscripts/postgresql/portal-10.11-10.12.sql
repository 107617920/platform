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

/* portal-10.11-10.12.sql */

ALTER TABLE Portal.PortalWebParts
    ADD COLUMN RowId SERIAL NOT NULL,
    DROP CONSTRAINT PK_PortalWebParts,
    ADD CONSTRAINT PK_PortalWebParts PRIMARY KEY (RowId);