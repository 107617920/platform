/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.Nullable;

/**
 * User: jeckels
 * Date: Oct 31, 2008
 */
public abstract class AbstractRunInput
{
    private final String _defaultRole;

    private int _targetApplicationId;
    protected String _role;

    protected AbstractRunInput(String defaultRole)
    {
        _defaultRole = defaultRole;
    }

    public int getTargetApplicationId()
    {
        return _targetApplicationId;
    }

    public void setTargetApplicationId(int targetApplicationId)
    {
        _targetApplicationId = targetApplicationId;
    }

    public String getRole()
    {
        return _role;
    }

    public void setRole(@Nullable String role)
    {
        if (role == null)
        {
            role = _defaultRole;
        }
        _role = role;
    }

    protected abstract int getInputKey();

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractRunInput input = (AbstractRunInput) o;

        return getInputKey() == input.getInputKey() &&
            ObjectUtils.equals(_role, input._role) &&
            _targetApplicationId == input._targetApplicationId;
    }

    @Override
    public int hashCode()
    {
        int result = getInputKey();
        result = 31 * result + _targetApplicationId;
        result = 31 * result + (_role == null ? 0 : _role.hashCode());
        return result;
    }
}