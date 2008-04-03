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

import java.util.Set;
import java.io.IOException;

/**
 * <code>BooleanToCommandArgs</code>
*/
public class BooleanToCommandArgs extends JobParamToCommandArgs
{
    private TaskToCommandArgs _if;
    private TaskToCommandArgs _else;

    public BooleanToCommandArgs()
    {
        setParamValidator(new ValidateBoolean());
    }

    public TaskToCommandArgs getIf()
    {
        return _if;
    }

    public void setIf(TaskToCommandArgs ifConverter)
    {
        _if = ifConverter;
        ifConverter.setParent(this);
    }

    public TaskToCommandArgs getElse()
    {
        return _else;
    }

    public void setElse(TaskToCommandArgs elseConverter)
    {
        _else = elseConverter;
        elseConverter.setParent(this);
    }

    public String[] toArgsInner(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException
    {
        String value = getValue(task.getJob());
        if ("yes".equalsIgnoreCase(value))
            return getIf().toArgs(task, visited);
        else
            return getElse().toArgs(task, visited);
    }
}
