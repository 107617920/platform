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
package org.labkey.api.pipeline.cmd;

/**
 * <code>BooleanToSwitch</code>
*/
public class BooleanToSwitch extends AbstractValueToNamedSwitch
{
    private String _default;
    private boolean _negative;

    public BooleanToSwitch()
    {
        setParamValidator(new ValidateBoolean());
    }

    public String getDefault()
    {
        return _default;
    }

    public void setDefault(String def)
    {
        _default = def;
    }

    public boolean isNegative()
    {
        return _negative;
    }

    public void setNegative(boolean negative)
    {
        _negative = negative;
    }

    public String[] toArgs(String value)
    {
        String[] params = getSwitchFormat().format(getSwitchName());
        if (value == null)
            value = _default;

        if ((!_negative && "yes".equalsIgnoreCase(value)) ||
                (_negative && "no".equalsIgnoreCase(value)))
            return params;

        return new String[0];
    }
}
