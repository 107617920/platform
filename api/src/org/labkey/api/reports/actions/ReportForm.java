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

package org.labkey.api.reports.actions;

import org.labkey.api.view.ViewForm;
import org.springframework.validation.BindException;

/**
 * User: Karl Lum
 * Date: Jan 30, 2008
 */
public class ReportForm extends ViewForm
{
    private String _tabId;
    protected BindException _errors;

    public String getTabId()
    {
        return _tabId;
    }

    public void setTabId(String tabId)
    {
        _tabId = tabId;
    }

    public BindException getErrors()
    {
        return _errors;
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }
}
