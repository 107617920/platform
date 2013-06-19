/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.data.*;
import org.labkey.api.util.ContainerContext;

import java.util.*;

/**
 * User: jeckels
 * Date: Dec 18, 2009
 */
public class PipelineQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "pipeline";

    public static final String JOB_TABLE_NAME = "Job";

    private static final Set<String> TABLE_NAMES;

    static
    {
        Set<String> names = new TreeSet<>();
        names.add(JOB_TABLE_NAME);
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public static void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new PipelineQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public PipelineQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Contains data about pipeline jobs", user, container, PipelineSchema.getInstance().getSchema());
    }

    protected TableInfo createTable(String name)
    {
        if (JOB_TABLE_NAME.equalsIgnoreCase(name))
        {
            FilteredTable table = new FilteredTable<PipelineQuerySchema>(PipelineSchema.getInstance().getTableInfoStatusFiles(), this)
            {
                @Override
                public FieldKey getContainerFieldKey()
                {
                    return FieldKey.fromParts("Folder");
                }
            };
            table.wrapAllColumns(true);
            table.removeColumn(table.getColumn("Container"));
            table.setName(JOB_TABLE_NAME);
            ColumnInfo folderColumn = table.wrapColumn("Folder", table.getRealTable().getColumn("Container"));
            folderColumn.setFk(new ContainerForeignKey(this));
            table.addColumn(folderColumn);
            String urlExp = "/pipeline-status/details.view?rowId=${rowId}";
            table.setDetailsURL(DetailsURL.fromString(urlExp));
            table.setDescription("Contains one row per pipeline job");

            if (getContainer().isRoot())
            {
                table.setContainerFilter(new ContainerFilter.AllFolders(getUser()));
            }

            table.getColumn("RowId").setURL(DetailsURL.fromString(urlExp));
            table.getColumn("Status").setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    DataColumn result = new DataColumn(colInfo);
                    result.setNoWrap(true);
                    return result;
                }
            });

            table.getColumn("Description").setDisplayColumnFactory(new DisplayColumnFactory()
            {
                @Override
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        @Override
                        public void addQueryFieldKeys(Set<FieldKey> keys)
                        {
                            super.addQueryFieldKeys(keys);
                            keys.add(getURLFieldKey());
                        }

                        private FieldKey getURLFieldKey()
                        {
                            return new FieldKey(getBoundColumn().getFieldKey().getParent(), "DataUrl");
                        }

                        @Override
                        public String renderURL(RenderContext ctx)
                        {
                            return ctx.get(getURLFieldKey(), String.class);
                        }
                    };
                }
            });
            table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(getUser(), getContainer()));
            table.getColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(getUser(), getContainer()));
            table.getColumn("JobParent").setFk(new LookupForeignKey("Job", "Description")
            {
                public TableInfo getLookupTableInfo()
                {
                    return getTable(JOB_TABLE_NAME);
                }
            });

            List<FieldKey> defaultCols = new ArrayList<>();
            defaultCols.add(FieldKey.fromParts("Status"));
            defaultCols.add(FieldKey.fromParts("Created"));
            if (getContainer().isRoot())
            {
                defaultCols.add(FieldKey.fromParts("FilePath"));
            }
            else
            {
                defaultCols.add(FieldKey.fromParts("Description"));
            }
            table.setDefaultVisibleColumns(defaultCols);
            table.setTitleColumn("Description");
            return table;
        }
        
        return null;
    }

    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }

}
