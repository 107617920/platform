/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.util;

import org.labkey.api.data.Container;

import java.util.Map;

/**
 * User: matthewb
 * Date: Sep 27, 2009
 * Time: 3:10:17 PM
 *
 * This is a helper class for DetailsURL.  Rather than needing to subclass DetailsURL to provide a
 * container value, you may provide a ContainerContext instead.
 */
public interface ContainerContext
{
    public Container getContainer(Map context);
}
