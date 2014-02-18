/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: adam
 * Date: 6/13/13
 * Time: 2:18 PM
 */
public class JavaScriptDisplayColumn extends DataColumn
{
    private final LinkedHashSet<ClientDependency> _dependencies;
    private final StringExpressionFactory.FieldKeyStringExpression _eventExpression;

    public JavaScriptDisplayColumn(ColumnInfo col, Collection<String> dependencies, String javaScriptEvents)
    {
        super(col);

        _dependencies = new LinkedHashSet<>();
        for (String dependency : dependencies)
            _dependencies.add(ClientDependency.fromFilePath(dependency));
        _eventExpression = StringExpressionFactory.FieldKeyStringExpression.create(javaScriptEvents, false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.OutputNull);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);

        if (null != o)
        {
            out.write("<a href=\"#\" tabindex=\"-1\" ");
            out.write(_eventExpression.eval(ctx));
            out.write(">");
            out.write(getFormattedValue(ctx));
            out.write("</a>");
        }
        else
            out.write("&nbsp;");
    }

    @Override
    public Set<ClientDependency> getClientDependencies()
    {
        return _dependencies;
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);

        if (_eventExpression != null)
        {
            keys.addAll(_eventExpression.getFieldKeys());
        }
    }
}
