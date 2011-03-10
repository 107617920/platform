/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.query;

import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A collection of ValidationExceptions, one for each row.
 * Each ValidationException may in turn have multiple field or global errors.
 *
 * User: kevink
 * Date: Mar 9, 2011
 */
public class BatchValidationException extends Exception
{
    Map<String, Object> extraContext;
    List<ValidationException> rowErrors;

    public BatchValidationException()
    {
        super();
        this.rowErrors = new ArrayList<ValidationException>();
    }

    public BatchValidationException(List<ValidationException> rowErrors, Map<String, Object> extraContext)
    {
        super();
        this.rowErrors = rowErrors;
        this.extraContext = extraContext;
    }

    public void addRowError(ValidationException vex)
    {
        rowErrors.add(vex);
    }

    public boolean hasErrors()
    {
        return rowErrors.size() > 0;
    }

    public List<ValidationException> getRowErrors()
    {
        return rowErrors;
    }

    public void addToErrors(Errors errors)
    {
        for (ValidationException vex : getRowErrors())
            vex.addToErrors(errors);
    }

    public void setExtraContext(Map<String, Object> extraContext)
    {
        this.extraContext = extraContext;
    }
    
    public Map<String, Object> getExtraContext()
    {
        return extraContext;
    }
}
