/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.api.exp.query;

import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.*;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.StringExpression;

import java.util.*;

public class ExpSchema extends AbstractExpSchema
{
    public static final String EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME = "ExperimentsMembershipForRun";



    public enum TableType
    {
        Runs
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpRunTable ret = ExperimentService.get().createRunTable(Runs.toString(), expSchema);
                return expSchema.setupTable(ret);
            }
        },
        Data
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpDataTable ret = ExperimentService.get().createDataTable(Data.toString(), expSchema);
                return expSchema.setupTable(ret);
            }
        },
        DataInputs
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpDataInputTable ret = ExperimentService.get().createDataInputTable(DataInputs.toString(), expSchema);
                return expSchema.setupTable(ret);
            }
        },
        Materials
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpMaterialTable result = expSchema.getSamplesSchema().getSampleTable(null);
                return result;
            }
        },
        MaterialInputs
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpMaterialInputTable ret = ExperimentService.get().createMaterialInputTable(MaterialInputs.toString(), expSchema);
                return expSchema.setupTable(ret);
            }
        },
        Protocols
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpProtocolTable ret = ExperimentService.get().createProtocolTable(Protocols.toString(), expSchema);
                return expSchema.setupTable(ret);
            }
        },
        SampleSets
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpSampleSetTable ret = ExperimentService.get().createSampleSetTable(SampleSets.toString(), expSchema);
                return expSchema.setupTable(ret);
            }
        },
        RunGroups
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpExperimentTable ret = ExperimentService.get().createExperimentTable(RunGroups.toString(), expSchema);
                return expSchema.setupTable(ret);
            }
        },
        RunGroupMap
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpRunGroupMapTable ret = ExperimentService.get().createRunGroupMapTable(RunGroupMap.toString(), expSchema);
                return expSchema.setupTable(ret);
            }
        },
        ProtocolApplications
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpProtocolApplicationTable result = ExperimentService.get().createProtocolApplicationTable(ProtocolApplications.toString(), expSchema);
                return expSchema.setupTable(result);
            }
        },
        QCFlags
        {
            public TableInfo createTable(ExpSchema expSchema, String queryName)
            {
                ExpQCFlagTable result = ExperimentService.get().createQCFlagsTable(QCFlags.toString(), expSchema);
                return expSchema.setupTable(result);
            }
        };

        public abstract TableInfo createTable(ExpSchema expSchema, String queryName);
    }

    public ExpTable getTable(TableType tableType)
    {
        return (ExpTable)getTable(tableType.toString());
    }

    public ExpExperimentTable createExperimentsTableWithRunMemberships(ExpRun run)
    {
        ExpExperimentTable ret = ExperimentService.get().createExperimentTable(EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME, this);
        setupTable(ret);
        // Don't include exp.experiment rows that are assay batches
        ret.setBatchProtocol(null);
        if (_containerFilter != null)
            ret.setContainerFilter(_containerFilter);
        ret.getColumn(ExpExperimentTable.Column.RunCount).setHidden(true);

        ret.addExperimentMembershipColumn(run);
        List<FieldKey> defaultCols = new ArrayList<>(ret.getDefaultVisibleColumns());
        defaultCols.add(0, FieldKey.fromParts("RunMembership"));
        defaultCols.remove(FieldKey.fromParts(ExpExperimentTable.Column.RunCount.name()));
        ret.setDefaultVisibleColumns(defaultCols);

        return ret;
    }

    static private Set<String> tableNames = new LinkedHashSet<>();

    static
    {
        for (TableType type : TableType.values())
        {
            tableNames.add(type.toString());
        }
        tableNames = Collections.unmodifiableSet(tableNames);
    }

    public static final String SCHEMA_NAME = "exp";
    public static final String SCHEMA_DESCR = "Contains data about experiment runs, data files, materials, sample sets, etc.";

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new ExpSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public SamplesSchema getSamplesSchema()
    {
        SamplesSchema schema = new SamplesSchema(getUser(), getContainer());
        schema.setContainerFilter(_containerFilter);
        return schema;
    }

    public ExpSchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, ExperimentService.get().getSchema());
    }

    public Set<String> getTableNames()
    {
        return tableNames;
    }

    public TableInfo createTable(String name)
    {
        for (TableType tableType : TableType.values())
        {
            if (tableType.name().equalsIgnoreCase(name))
            {
                return tableType.createTable(this, tableType.name());
            }
        }

        // Support "Experiments" as a legacy name for the RunGroups table
        if ("Experiments".equalsIgnoreCase(name))
        {
            ExpExperimentTable ret = ExperimentService.get().createExperimentTable(name, this);
            return setupTable(ret);
        }
        if ("Experiments".equalsIgnoreCase(name))
        {
            // Support "Experiments" as a legacy name for the RunGroups table
            return TableType.RunGroups.createTable(this, name);
        }
        if ("Datas".equalsIgnoreCase(name))
        {
            /// Support "Datas" as a legacy name for the Data table
            return TableType.Data.createTable(this, name);
        }
        if (EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createExperimentsTableWithRunMemberships(null);
        }

        return null;
    }

    public ExpDataTable getDatasTable()
    {
        return (ExpDataTable)getTable(TableType.Data);
    }

    public ExpRunTable getRunsTable()
    {
        return (ExpRunTable)getTable(TableType.Runs);
    }

    public ForeignKey getProtocolApplicationForeignKey()
    {
        return new ExperimentLookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return getTable(TableType.ProtocolApplications);
            }
        };
    }

    public ForeignKey getProtocolForeignKey(String targetColumnName)
    {
        return new LookupForeignKey(targetColumnName)
        {
            public TableInfo getLookupTableInfo()
            {
                ExpProtocolTable protocolTable = (ExpProtocolTable)TableType.Protocols.createTable(ExpSchema.this, TableType.Protocols.toString());
                protocolTable.setContainerFilter(ContainerFilter.EVERYTHING);
                return protocolTable;
            }
        };
    }

    public ForeignKey getJobForeignKey()
    {
        return new LookupForeignKey("RowId", "RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return PipelineService.get().getJobsTable(getUser(), getContainer());
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                return getURL(parent, true);
            }
        };
    }

    public ForeignKey getRunIdForeignKey()
    {
        return new ExperimentLookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return getTable(TableType.Runs);
            }
        };
    }

    /** @param includeBatches if false, then filter out run groups of type batch when doing the join */
    public ForeignKey getRunGroupIdForeignKey(final boolean includeBatches)
    {
        return new ExperimentLookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpExperimentTable result = (ExpExperimentTable)getTable(TableType.RunGroups);
                result.setContainerFilter(new ContainerFilter.CurrentPlusProjectAndShared(getUser()));
                if (!includeBatches)
                {
                    result.setBatchProtocol(null);
                }
                return result;
            }
        };
    }

    public ForeignKey getDataIdForeignKey()
    {
        return new ExperimentLookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return getTable(TableType.Data);
            }
        };
    }

    public ForeignKey getMaterialIdForeignKey()
    {
        return new ExperimentLookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return getTable(TableType.Materials);
            }
        };
    }

    public abstract static class ExperimentLookupForeignKey extends LookupForeignKey
    {
        public ExperimentLookupForeignKey(String pkColumnName)
        {
            super(pkColumnName);
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return getURL(parent, true);
        }
    }
}
