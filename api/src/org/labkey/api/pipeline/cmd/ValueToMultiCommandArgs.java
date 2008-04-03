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

import java.util.ArrayList;
import java.util.Arrays;

/**
 * <code>ValueToMultiCommandArgs</code>
*/
public class ValueToMultiCommandArgs extends ValueToCommandArgs
{
    private String _delimiter = ",";
    private ValueToCommandArgs _converter = new ValueInLine();

    public String getDelimiter()
    {
        return _delimiter;
    }

    public void setDelimiter(String delimiter)
    {
        _delimiter = delimiter;
    }

    public ValueToCommandArgs getConverter()
    {
        return _converter;
    }

    public void setConverter(ValueToCommandArgs converter)
    {
        _converter = converter;
        converter.setParent(this);
    }

    public String[] toArgs(String value)
    {
        if (value != null && value.length() > 0)
        {
            String[] valueParts = value.split(_delimiter);

            ArrayList<String> params = new ArrayList<String>();
            for (String part : valueParts)
                params.addAll(Arrays.asList(_converter.toArgs(part)));
            return params.toArray(new String[params.size()]);
        }

        return new String[0];
    }
}
