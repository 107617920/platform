/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.api.exp.list;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.view.ActionURL;

import java.util.Map;

public class ListService
{
    private static Interface instance;

    public static Interface get()
    {
        return instance;
    }

    public static void setInstance(Interface i)
    {
        instance = i;
    }

    public interface Interface
    {
        Map<String, ListDefinition> getLists(Container container);
        boolean hasLists(Container container);
        ListDefinition createList(Container container, String name);
        ListDefinition getList(Container container, int id);
        @Nullable ListDefinition getList(Container container, String name);
        ListDefinition getList(Domain domain);
        ActionURL getManageListsURL(Container container);
    }
}
