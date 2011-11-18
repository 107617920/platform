/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.query.FieldKey;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
* User: adam
* Date: Sep 14, 2010
* Time: 1:09:32 PM
*/

// Wraps any DisplayColumn and causes it to render each value separately
public class MultiValuedDisplayColumn extends DisplayColumnDecorator
{
    private final Set<FieldKey> _fieldKeys = new HashSet<FieldKey>();

    public MultiValuedDisplayColumn(DisplayColumn dc)
    {
        this(dc, false);
    }

    /** @param boundColumnIsNotMultiValued true in the case when the bound column is the one that declares the multi-valued FK */
    public MultiValuedDisplayColumn(DisplayColumn dc, boolean boundColumnIsNotMultiValued)
    {
        super(dc);
        addQueryFieldKeys(_fieldKeys);
        assert _fieldKeys.contains(getColumnInfo().getFieldKey());
        if (boundColumnIsNotMultiValued)
        {
            // The bound column won't have multiple values, so don't put it in the set that should split the string
            // and iterate through individual values
            _fieldKeys.remove(getColumnInfo().getFieldKey());
        }
    }

    @Override         // TODO: Need similar for renderDetailsCellContents()
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        MultiValuedRenderContext mvCtx = new MultiValuedRenderContext(ctx, _fieldKeys);
        String sep = "";

        while (mvCtx.next())
        {
            out.append(sep);
            super.renderGridCellContents(mvCtx, out);
            sep = ", ";
        }

        // TODO: Call super in empty values case?
    }

    public Object getJsonValue(RenderContext ctx)
    {
        List<Object> values = new LinkedList<Object>();

        MultiValuedRenderContext mvCtx = new MultiValuedRenderContext(ctx, _fieldKeys);
        while (mvCtx.next())
        {
            Object v = super.getJsonValue(mvCtx);
            values.add(v);            
        }

        return values;
    }
}
