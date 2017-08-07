/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.view.ViewContext;

/**
 * User: adam
 * Date: Mar 31, 2007
 * Time: 9:05:32 PM
 */
public class AttachmentParentEntity extends Entity implements AttachmentParent
{
    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return null;
    }

    @Override
    public SecurityPolicy getSecurityPolicy()
    {
        return null;
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return AttachmentType.UNKNOWN;
    }
}
