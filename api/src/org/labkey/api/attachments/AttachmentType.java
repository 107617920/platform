/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.SQLFragment;

/**
 * Tags {@link Attachment} objects based on their intended use and what they're attached to. Does not
 * necessarily indicate that they are a file of a particular type/format.
 */
public interface AttachmentType
{
    AttachmentType UNKNOWN = new AttachmentType()
    {
        @NotNull
        @Override
        public String getUniqueName()
        {
            return "UnknownAttachmentType";
        }

        @Override
        public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
        {
            sql.append("0 = 1");
        }
    };

    @NotNull String getUniqueName();

    /**
     * Append to the where clause of a query that wants to select attachments of the implementing type
     * @param sql Implementers MUST append a valid where clause to this SQLFragment
     * @param parentColumn Column identifier for use in where clause. Usually represents 'core.Documents.Parent'
     * @param documentNameColumn Column identifier for use in where clause. Usually represents 'core.Documents.DocumentName'
     */
    void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn);
}
