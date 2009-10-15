/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.apache.log4j.Logger;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.data.ColumnInfo;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;

public class UpdateView extends DataView
{
    private static Logger _log = Logger.getLogger(UpdateView.class);

    public UpdateView(DataRegion dataRegion, TableViewForm form, BindException errors)
    {
        super(dataRegion, form, errors);
    }

    public UpdateView(TableViewForm form, BindException errors)
    {
        super(form, errors);
    }

    protected boolean isColumnIncluded(ColumnInfo col)
    {
        return col.isShownInUpdateView();
    }


    protected void _renderDataRegion(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        if (null != getRenderContext().getForm())
        {
            getDataRegion().renderUpdateForm(ctx, out);
        }
        else
        {
            out.write("No values to _render");
            _log.info("No values or pk specified for data region " + getDataRegion().getName());
        }
    }
}
