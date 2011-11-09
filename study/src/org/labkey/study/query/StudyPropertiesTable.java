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
package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.wiki.WikiRendererDisplayColumn;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 * Date: Aug 7, 2008
 * Time: 4:23:44 PM
 */
public class StudyPropertiesTable extends BaseStudyTable
{
    private Domain _domain;
    private List<FieldKey> _visibleColumns = new ArrayList<FieldKey>();

    public StudyPropertiesTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudy());

        ColumnInfo labelColumn = addRootColumn("label", true, true);
        DetailsURL detailsURL = new DetailsURL(PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(schema.getContainer()));
        detailsURL.setContainerContext(new ContainerContext()
        {
            @Override
            public Container getContainer(Map context)
            {
                //Container is the key so should always be selected.
                return ContainerManager.getForId((String) context.get("container"));
            }
        });
        labelColumn.setURL(detailsURL);
        addRootColumn("startDate");

        ColumnInfo containerColumn = addRootColumn("container", false, false);
        containerColumn.setKeyField(true);

        ColumnInfo timepointTypeColumn = addRootColumn("timepointType", false, false);
        addRootColumn("subjectNounSingular", false, true);
        addRootColumn("subjectNounPlural", false, true);
        addRootColumn("subjectColumnName", false, true);
        addRootColumn("studyGrant", true, true);
        addRootColumn("investigator", true, true);
        ColumnInfo descriptionColumn = addRootColumn("description", true, true);
        final ColumnInfo descriptionRendererTypeColumn = addRootColumn("descriptionRendererType", false, false);
        descriptionColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new WikiRendererDisplayColumn(colInfo, descriptionRendererTypeColumn, WikiRendererType.TEXT_WITH_LINKS);
            }
        });


        String bTRUE = getSchema().getSqlDialect().getBooleanTRUE();
        String bFALSE = getSchema().getSqlDialect().getBooleanFALSE();

        ColumnInfo dateBasedColumn = new ExprColumn(this, "DateBased", new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".timepointType != 'VISIT' THEN " + bTRUE + " ELSE " + bFALSE + " END)"), JdbcType.BOOLEAN, timepointTypeColumn);
        dateBasedColumn.setUserEditable(false);
        dateBasedColumn.setHidden(true);
        dateBasedColumn.setDescription("Deprecated.  Use 'timepointType' column instead.");
        addColumn(dateBasedColumn);

        ColumnInfo lsidColumn = addRootColumn("LSID", false, false);
        lsidColumn.setHidden(true);

        String domainURI = StudyImpl.DOMAIN_INFO.getDomainURI(schema.getContainer());

        _domain = PropertyService.get().getDomain(schema.getContainer(), domainURI);
        if (_domain != null)
        {
            for (ColumnInfo extraColumn : _domain.getColumns(this, lsidColumn, schema.getUser()))
            {
                safeAddColumn(extraColumn);
                _visibleColumns.add(FieldKey.fromParts(extraColumn.getName()));
            }
        }

        setDefaultVisibleColumns(_visibleColumns);
    }

    private ColumnInfo addRootColumn(String columnName)
    {
        return addRootColumn(columnName, true, false);
    }

    private ColumnInfo addRootColumn(String columnName, boolean visible, boolean userEditable)
    {
        ColumnInfo columnInfo = addWrapColumn(_rootTable.getColumn(columnName));
        columnInfo.setUserEditable(userEditable);
        if (visible)
            _visibleColumns.add(columnInfo.getFieldKey());
        return columnInfo;
    }

    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    @Override
    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        if (perm == UpdatePermission.class)
            return getContainer().hasPermission(user, AdminPermission.class);
        if (perm == ReadPermission.class)
            return getContainer().hasPermission(user, ReadPermission.class);
        return false;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        User user = _schema.getUser();
        if (!getContainer().hasPermission(user, AdminPermission.class))
            return null;
        return new StudyPropertiesUpdateService(this);
    }
}
