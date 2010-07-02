/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

package org.labkey.bigiron;

import org.labkey.api.data.SqlDialect;
import org.labkey.bigiron.sas.*;
import org.labkey.bigiron.mssql.SqlDialectMicrosoftSQLServer;
import org.labkey.bigiron.mssql.SqlDialectMicrosoftSQLServer9;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.query.QueryView;

import java.util.Collection;
import java.util.Collections;

public class BigIronModule extends DefaultModule
{
    public static final String NAME = "BigIron";

    public String getName()
    {
        return "BigIron";
    }

    public double getVersion()
    {
        return 10.19;
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    public boolean hasScripts()
    {
        return false;
    }

    protected void init()
    {
        SqlDialect.register(new SqlDialectMicrosoftSQLServer());
        SqlDialect.register(new SqlDialectMicrosoftSQLServer9());
        SqlDialect.register(new SqlDialectSas91());
        SqlDialect.register(new SqlDialectSas92());
        QueryView.register(new SasExportScriptFactory());
    }

    public void startup(ModuleContext moduleContext)
    {
    }
}