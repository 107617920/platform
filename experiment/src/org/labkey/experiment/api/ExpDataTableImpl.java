/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.io.Writer;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExpDataTableImpl extends ExpTableImpl<ExpDataTable.Column> implements ExpDataTable
{
    protected ExpExperiment _experiment;
    protected boolean _runSpecified;
    protected ExpRun _run;
    protected DataType _type;
    private Domain _domain;

    public ExpDataTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoData(), schema, new ExpDataImpl(new Data()));
    }

    public void populate()
    {
        addColumn(Column.RowId).setHidden(true);
        addColumn(Column.Name);
        ExpSchema schema = getExpSchema();
        addColumn(Column.Run).setFk(schema.getRunIdForeignKey());
        ColumnInfo lsidColumn = addColumn(Column.LSID);
        lsidColumn.setHidden(true);
        addColumn(Column.DataFileUrl);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
        addColumn(Column.DownloadLink);
        addColumn(Column.ViewFileLink);
        addColumn(Column.ContentLink);
        addColumn(Column.Thumbnail);
        addColumn(Column.Flag);
        addColumn(Column.SourceProtocolApplication).setHidden(true);
        addColumn(Column.Protocol).setHidden(true);
        addContainerColumn(Column.Folder, null);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(Column.Name));
        defaultCols.add(FieldKey.fromParts(Column.Run));
        defaultCols.add(FieldKey.fromParts(Column.DataFileUrl));
        setDefaultVisibleColumns(defaultCols);

        setTitleColumn("Name");
        ActionURL detailsURL = new ActionURL(ExperimentController.ShowDataAction.class, _schema.getContainer());
        setDetailsURL(new DetailsURL(detailsURL, Collections.singletonMap("rowId", "RowId")));

        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        String domainURI = svc.getDomainURI(getContainer());
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainURI, getContainer());

        if (dd != null)
        {
            _domain = PropertyService.get().getDomain(dd.getDomainId());
            if (_domain != null)
            {
                for (DomainProperty prop : _domain.getProperties())
                {
                    // don't set container on property column so that inherited domain properties work
                    ColumnInfo projectColumn = new PropertyColumn(prop.getPropertyDescriptor(), lsidColumn, null, /*getContainer().getId(),*/ _schema.getUser());
                    addColumn(projectColumn);
                }
            }
        }
    }

    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        return svc.getFilePropsUpdateService(this, getContainer());
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder:
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case DataFileUrl:
                return wrapColumn(alias, _rootTable.getColumn("DataFileUrl"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Protocol:
                ExprColumn col = new ExprColumn(this, Column.Protocol.toString(), new SQLFragment(
                        "(SELECT ProtocolLSID FROM " + ExperimentServiceImpl.get().getTinfoProtocolApplication() + " pa " +
                        " WHERE pa.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId)"), JdbcType.VARCHAR, getColumn(Column.SourceProtocolApplication));
                col.setFk(getExpSchema().getProtocolForeignKey("LSID"));
                return col;
            case SourceProtocolApplication:
            {
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("SourceApplicationId"));
                columnInfo.setFk(getExpSchema().getProtocolApplicationForeignKey());
                return columnInfo;
            }
            case RowId:
            {
                ColumnInfo ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                ret.setFk(new RowIdForeignKey(ret));
                ret.setHidden(true);
                return ret;
            }
            case Run:
                return wrapColumn(alias, _rootTable.getColumn("RunId"));
            case Flag:
                return   createFlagColumn(alias);
            case DownloadLink:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new DownloadFileDataLinkColumn(colInfo);
                    }
                });
                result.setDescription("A link to download the file");
                return result;
            }
            case ViewFileLink:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ViewFileDataLinkColumn(colInfo);
                    }
                });
                result.setDescription("A link to view the file directly on the web site");
                return result;
            }
            case ContentLink:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ViewContentDataLinkColumn(colInfo);
                    }
                });
                result.setDescription("A link to view the imported contents of the file on the web site");
                return result;
            }
            case Thumbnail:
            {
                ColumnInfo result = wrapColumn(alias, _rootTable.getColumn("RowId"));
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ThumbnailDataLinkColumn(colInfo);
                    }
                });
                result.setDescription("A popup thumbnail of the file if it is an image");
                return result;
            }
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    public void setExperiment(ExpExperiment experiment)
    {
        if (getExperiment() != null)
            throw new IllegalArgumentException("Attempt to unset experiment");
        if (experiment == null)
            return;
        SQLFragment condition = new SQLFragment("exp.Data.RunId IN "
                + " ( SELECT ExperimentRunId FROM exp.RunList "
                + " WHERE ExperimentId = ? )");
        condition.add(experiment.getRowId());
        addCondition(condition);
        _experiment = experiment;
    }

    public ExpExperiment getExperiment()
    {
        return _experiment;
    }

    public void setRun(ExpRun run)
    {
        if (_runSpecified)
            throw new IllegalArgumentException("Cannot unset run");
        _runSpecified = true;
        _run = run;
        if (run == null)
        {
            addCondition(new SQLFragment("(RunId IS NULL)"), "RunId");
        }
        else
        {
            addCondition(_rootTable.getColumn("RunId"), run.getRowId());
        }
    }

    public ExpRun getRun()
    {
        return _run;
    }

    public ColumnInfo addDataInputColumn(String alias, String role)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(exp.datainput.dataid)" +
                "\nFROM exp.datainput" +
                "\nWHERE " + ExprColumn.STR_TABLE_ALIAS +  ".SourceApplicationId = exp.datainput.TargetApplicationId" +
                "\nAND ");
        if (role == null)
        {
            sql.append("1 = 0");
        }
        else
        {
            sql.append("exp.datainput.role = ?");
            sql.add(role);
        }
        sql.append(")");
        ExprColumn ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
        return doAdd(ret);
    }

    public ColumnInfo addMaterialInputColumn(String alias, SamplesSchema schema, String pdRole, final ExpSampleSet ss)
    {
        SQLFragment sql = new SQLFragment("(SELECT MIN(InputMaterial.RowId)" +
            "\nFROM exp.materialInput" +
            "\nINNER JOIN exp.material AS InputMaterial ON exp.materialInput.materialId = InputMaterial.RowId" +
            "\nWHERE " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId = exp.materialInput.TargetApplicationId");
        if (ss != null)
        {
            sql.append("\nAND InputMaterial.CPASType = ?");
            sql.add(ss.getLSID());
        }
        sql.append(")");
        ExprColumn ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
        ret.setFk(schema.materialIdForeignKey(ss));
        return doAdd(ret);
    }

    public DataType getDataType()
    {
        return _type;
    }

    public void setDataType(DataType type)
    {
        _type = type;
        getFilter().deleteConditions("LSID");
        if (_type != null)
        {
            addCondition(new SQLFragment("LSID LIKE " + getSqlDialect().concatenate("'urn:lsid:%:'", "?", "'%'"), _type.getNamespacePrefix()), "LSID");
        }
    }

    public String urlFlag(boolean flagged)
    {
        String ret = null;
        DataType type = getDataType();
        if (type != null)
            ret = type.urlFlag(flagged);
        if (ret != null)
            return ret;
        return super.urlFlag(flagged);
    }


    public ColumnInfo addInputRunCountColumn(String alias)
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(DISTINCT exp.ProtocolApplication.RunId) " +
                "FROM exp.ProtocolApplication INNER JOIN Exp.DataInput ON exp.ProtocolApplication.RowId = Exp.DataInput.TargetApplicationId " +
                "WHERE Exp.DataInput.DataId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId)");
        ColumnInfo ret = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
        return doAdd(ret);
    }

    private class ThumbnailDataLinkColumn extends ViewFileDataLinkColumn
    {
        public ThumbnailDataLinkColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        @Override
        protected void renderData(Writer out, ExpData data) throws IOException
        {
            renderThumbnailPopup(out, data, getURL(data));
        }
    }

    private class DownloadFileDataLinkColumn extends DataLinkColumn
    {
        public DownloadFileDataLinkColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        protected ActionURL getURL(ExpData data)
        {
            return ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(getContainer(), data, false);
        }
    }

    private class ViewFileDataLinkColumn extends DataLinkColumn
    {
        public ViewFileDataLinkColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        protected ActionURL getURL(ExpData data)
        {
            return ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(getContainer(), data, true);
        }
    }

    private class ViewContentDataLinkColumn extends DataLinkColumn
    {
        public ViewContentDataLinkColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        protected ActionURL getURL(ExpData data)
        {
            return data.findDataHandler().getContentURL(getContainer(), data);
        }
    }
}
