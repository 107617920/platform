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

package org.labkey.list.model;

import java.util.Set;
import java.util.HashSet;

// Uniquify filenames by inserting _n immediately before the extension
public class FileNameUniquifier
{
    private Set<String> _previous = new HashSet<String>();

    public String uniquify(String name)
    {
        int i = 1;
        String candidateName = name;

        // Insert _2, _3, _4, _5, etc. before the extension until we have a unique identifier
        while (_previous.contains(candidateName))
        {
            i++;
            int dot = name.lastIndexOf('.');

            if (-1 == dot)
                candidateName = name + '_' + i;
            else
                candidateName = name.substring(0, dot) + '_' + i + name.substring(dot);
        }

        _previous.add(candidateName);

        return candidateName;
    }
}
