/*
 * Copyright (c) 2012 LabKey Corporation
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

-- add a new column to allow subscriptions to multiple forums within a single container
ALTER TABLE comm.EmailPrefs ADD SrcIdentifier NVARCHAR(100)
GO

UPDATE comm.EmailPrefs SET SrcIdentifier = Container;
ALTER TABLE comm.EmailPrefs ALTER COLUMN SrcIdentifier NVARCHAR(100) NOT NULL;

ALTER TABLE comm.EmailPrefs DROP CONSTRAINT pk_emailprefs;
ALTER TABLE comm.EmailPrefs ADD CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId, Type, SrcIdentifier);

UPDATE comm.Announcements SET DiscussionSrcIdentifier = Container WHERE DiscussionSrcIdentifier IS NULL AND Parent IS NULL;

