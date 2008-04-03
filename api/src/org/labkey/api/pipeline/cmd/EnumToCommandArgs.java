/*
 * Copyright (c) 2008 LabKey Software Foundation
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
package org.labkey.api.pipeline.cmd;

import java.util.Map;
import java.util.Set;
import java.io.IOException;

/**
 * <code>EnumToCommandArgs</code>
*/
public class EnumToCommandArgs extends JobParamToCommandArgs
{
    private String _default;
    private Map<String, TaskToCommandArgs> _converters;

    public String getDefault()
    {
        return _default;
    }

    public void setDefault(String aDefault)
    {
        _default = aDefault;
    }

    public Map<String, TaskToCommandArgs> getConverters()
    {
        return _converters;
    }

    public void setConverters(Map<String, TaskToCommandArgs> converters)
    {
        _converters = converters;
        for (TaskToCommandArgs converter : converters.values())
            converter.setParent(this);
    }

    public TaskToCommandArgs getConverter(String value)
    {
        return _converters.get(value);
    }

    public String[] toArgsInner(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException
    {
        String keyConverter = getValue(task.getJob());
        if (keyConverter == null)
            keyConverter = getDefault();

        if (keyConverter != null)
        {
            TaskToCommandArgs converter = getConverter(keyConverter);
            if (converter != null)
                return converter.toArgs(task, visited);
        }

        return new String[0];
    }
}
