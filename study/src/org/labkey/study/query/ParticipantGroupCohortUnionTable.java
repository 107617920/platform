/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.study.query;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyManager;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 11/16/12
 */
public class ParticipantGroupCohortUnionTable extends BaseStudyTable
{
    public ParticipantGroupCohortUnionTable(final StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getSchema().getTable(StudyQuerySchema.PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME));

        addContainerColumn();
        addWrapParticipantColumn("ParticipantId");

        ColumnInfo groupIdColumn = new AliasedColumn(this, "GroupId", _rootTable.getColumn("GroupId"));
        groupIdColumn.setFk(new QueryForeignKey(_schema, StudyService.get().getSubjectGroupTableName(getContainer()), "RowId", "Label"));
        addColumn(groupIdColumn);

        if (StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser()))
        {
            ColumnInfo currentCohortColumn = new AliasedColumn(this, "Cohort", _rootTable.getColumn("CohortId"));
            currentCohortColumn.setFk(new LookupForeignKey("RowId")
            {
                public TableInfo getLookupTableInfo()
                {
                    return new CohortTable(_schema);
                }
            });
            addColumn(currentCohortColumn);
        }

        ColumnInfo col = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("id")));
        col.setHidden(true);
        col.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            private StudyQuerySchema _schema = schema;

            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    @Override
                    public Object getDisplayValue(RenderContext ctx)
                    {
                        return _getValue(ctx);
                    }

                    @Override
                    public String getFormattedValue(RenderContext ctx)
                    {
                        return _getValue(ctx) + "<br>";
                    }

                    private String _getValue(RenderContext ctx)
                    {
                        String value = getValue(ctx).toString();

                        if (value != null)
                        {
                            String[] parts = value.split("-");

                            if (parts != null && parts.length == 2)
                            {
                                if ("cohort".equalsIgnoreCase(parts[1]))
                                {
                                    int rowId = NumberUtils.toInt(parts[0]);
                                    CohortImpl cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), _schema.getUser(), rowId);
                                    if (cohort != null)
                                        return cohort.getLabel();
                                }
                                else
                                {
                                    int rowId = NumberUtils.toInt(parts[0]);
                                    ParticipantGroup group = ParticipantGroupManager.getInstance().getParticipantGroupFromGroupRowId(getContainer(), _schema.getUser(), rowId);
                                    if (group != null)
                                        return group.getLabel();
                                }
                            }
                        }
                        return "";
                    }
                };
            }
        });
    }
}
