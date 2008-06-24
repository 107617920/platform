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

import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskFactorySettings;

import java.util.List;
import java.util.ArrayList;

/**
 * <code>ConvertTaskFactorySettings</code>
 */
public class ConvertTaskFactorySettings extends AbstractTaskFactorySettings
    implements TaskFactorySettings.Provider
{
    private String _cloneName;
    private String _statusName;
    private String _outputExt;
    private Object[] _commands;

    public ConvertTaskFactorySettings(String name)
    {
        this(ConvertTaskId.class, name);
    }

    public ConvertTaskFactorySettings(Class namespaceClass, String name)
    {
        super(namespaceClass, name);
    }

    public TaskId getCloneId()
    {
        return new TaskId(ConvertTaskId.class, _cloneName);
    }

    public String getCloneName()
    {
        return _cloneName;
    }

    public void setCloneName(String cloneName)
    {
        _cloneName = cloneName;
    }

    public String getStatusName()
    {
        return _statusName;
    }

    public void setStatusName(String statusName)
    {
        _statusName = statusName;
    }

    public String getOutputExt()
    {
        return _outputExt;
    }

    public void setOutputExt(String outputExt)
    {
        _outputExt = outputExt;
    }

    public Object[] getCommands()
    {
        return _commands;
    }

    public void setCommands(Object[] commands)
    {
        _commands = commands;
    }

    public TaskId[] getCommandIds()
    {
        if (_commands == null)
            return null;
        
        TaskId[] commandIds = new TaskId[_commands.length];
        for (int i = 0; i < _commands.length; i++)
        {
            assert _commands[i] instanceof TaskId :
                    "Invalid command type " + _commands[i].getClass() + " (call getSettings?)";
            commandIds[i] = (TaskId) _commands[i];
        }
        return commandIds;
    }

    public TaskFactorySettings[] getSettings()
    {
        if (_commands == null)
            return new TaskFactorySettings[0];
        
        ArrayList<TaskFactorySettings> settingsList = new ArrayList<TaskFactorySettings>();
        for (int i = 0; i < _commands.length; i++)
        {
            Object o = _commands[i];
            if (o instanceof TaskFactorySettings)
            {
                TaskFactorySettings settings =
                        (TaskFactorySettings) o;
                settingsList.add(settings);
                _commands[i] = settings.getId();
            }
        }
        return settingsList.toArray(new TaskFactorySettings[settingsList.size()]);
    }
}
