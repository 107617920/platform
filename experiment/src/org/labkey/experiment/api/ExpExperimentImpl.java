/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.util.URLHelper;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExpExperimentImpl extends ExpIdentifiableEntityImpl<Experiment> implements ExpExperiment
{
    public ExpExperimentImpl(Experiment experiment)
    {
        super(experiment);
    }

    public Container getContainer()
    {
        return _object.getContainer();
    }

    public URLHelper detailsURL()
    {
        return null;
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public ExpRunImpl[] getRuns()
    {
        try
        {
            String sql = "SELECT ER.* FROM " + ExperimentServiceImpl.get().getTinfoExperiment() + " E "
                    + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoRunList()  + " RL ON (E.RowId = RL.ExperimentId) "
                    + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoExperimentRun()  + " ER ON (ER.RowId = RL.ExperimentRunId) "
                    + " WHERE E.LSID = ? ORDER BY ER.RowId" ;

            return ExpRunImpl.fromRuns(Table.executeQuery(ExperimentServiceImpl.get().getExpSchema(), sql, new Object[] { getLSID() }, ExperimentRun.class));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpRun[] getRuns(@Nullable ExpProtocol parentProtocol, ExpProtocol childProtocol)
    {
        try
        {
            SQLFragment sql = new SQLFragment(" SELECT ER.* "
                        + " FROM exp.ExperimentRun ER "
                        + " INNER JOIN exp.RunList RL ON ( ER.RowId = RL.ExperimentRunId ) "
                        + " WHERE RL.ExperimentId = ? ");
            sql.add(getRowId());
            if (parentProtocol != null)
            {
                sql.append("\nAND ER.ProtocolLSID = ?");
                sql.add(parentProtocol.getLSID());
            }
            if (childProtocol != null)
            {
                sql.append("\nAND ER.RowId IN (SELECT PA.RunId "
                    + " FROM exp.ProtocolApplication PA "
                    + " WHERE PA.ProtocolLSID = ? ) ");
                sql.add(childProtocol.getLSID());
            }
            ExperimentRun[] runs = Table.executeQuery(ExperimentService.get().getSchema(), sql.getSQL(), sql.getParams().toArray(new Object[sql.getParams().size()]), ExperimentRun.class);
            return ExpRunImpl.fromRuns(runs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpProtocol getBatchProtocol()
    {
        if (_object.getBatchProtocolId() == null)
        {
            return null;
        }
        return ExperimentService.get().getExpProtocol(_object.getBatchProtocolId().intValue());
    }

    public void setBatchProtocol(ExpProtocol protocol)
    {
        _object.setBatchProtocolId(protocol == null ? null : protocol.getRowId());
    }

    public List<ExpProtocol> getAllProtocols()
    {
        try
        {
            String sql = "SELECT p.* FROM " + ExperimentServiceImpl.get().getTinfoProtocol() + " p, " + ExperimentServiceImpl.get().getTinfoExperimentRun() + " r WHERE p.LSID = r.ProtocolLSID AND r.RowId IN (SELECT ExperimentRunId FROM " + ExperimentServiceImpl.get().getTinfoRunList() + " WHERE ExperimentId = ?)";
            return Arrays.<ExpProtocol>asList(ExpProtocolImpl.fromProtocols(Table.executeQuery(ExperimentServiceImpl.get().getSchema(), sql, new Object[] { getRowId() }, Protocol.class)));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void removeRun(User user, ExpRun run) throws Exception
    {
        ExperimentServiceImpl.get().dropRunsFromExperiment(getLSID(), run.getRowId());
    }

    public void addRuns(User user, ExpRun... newRuns)
    {
        try
        {
            ExperimentServiceImpl.get().getExpSchema().getScope().ensureTransaction();

            ExpRun[] existingRunIds = getRuns();
            Set<Integer> newRunIds = new HashSet<Integer>();
            for (ExpRun run : newRuns)
            {
                if (_object.getBatchProtocolId() != null && run.getProtocol().getRowId() != _object.getBatchProtocolId().intValue())
                {
                    throw new IllegalArgumentException("Attempting to add a run of a different protocol to a batch.");
                }
                newRunIds.add(new Integer(run.getRowId()));
            }

            for (ExpRun er : existingRunIds)
            {
                newRunIds.remove(er.getRowId());
            }

            String sql = " INSERT INTO " + ExperimentServiceImpl.get().getTinfoRunList() + " ( ExperimentId, ExperimentRunId )  VALUES ( ? , ? ) ";
            for (Integer runId : newRunIds)
            {
                Table.execute(ExperimentServiceImpl.get().getExpSchema(), sql, getRowId(), runId);
            }

            ExperimentServiceImpl.get().getExpSchema().getScope().commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ExperimentServiceImpl.get().getExpSchema().getScope().closeConnection();
        }
    }

    public void save(User user)
    {
        save(user, ExperimentServiceImpl.get().getTinfoExperiment());
    }

    public void delete(User user)
    {
        try
        {
            if (!getContainer().hasPermission(user, DeletePermission.class))
            {
                throw new IllegalStateException("Not permitted");
            }

            try
            {
                ExperimentServiceImpl.get().getExpSchema().getScope().ensureTransaction();

                // If we're a batch, delete all the runs too
                if (_object.getBatchProtocolId() != null)
                {
                    for (ExpRunImpl expRun : getRuns())
                    {
                        expRun.delete(user);
                    }
                }

                String sql = "DELETE FROM " + ExperimentServiceImpl.get().getTinfoRunList()
                        + " WHERE ExperimentId IN ("
                        + " SELECT E.RowId FROM " + ExperimentServiceImpl.get().getTinfoExperiment() + " E "
                        + " WHERE E.RowId = " + getRowId()
                        + " AND E.Container = ? ); ";
                Table.execute(ExperimentServiceImpl.get().getExpSchema(), sql, getContainer().getId());

                OntologyManager.deleteOntologyObjects(getContainer(), getLSID());

                sql = "  DELETE FROM " + ExperimentServiceImpl.get().getTinfoExperiment()
                        + " WHERE RowId = " + getRowId()
                        + " AND Container = ? ";
                Table.execute(ExperimentServiceImpl.get().getExpSchema(), sql, getContainer().getId());

                ExperimentServiceImpl.get().getExpSchema().getScope().commitTransaction();
            }
            finally
            {
                ExperimentServiceImpl.get().getExpSchema().getScope().closeConnection();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void setHidden(boolean hidden)
    {
        _object.setHidden(hidden);
    }
    
    public boolean isHidden()
    {
        return _object.isHidden();
    }

    public void setContainer(Container container)
    {
        _object.setContainer(container);
    }

    public String getComments()
    {
        return _object.getComments();
    }

    public void setComments(String comments)
    {
        _object.setComments(comments);
    }

    public static ExpExperimentImpl[] fromExperiments(Experiment[] experiments)
    {
        ExpExperimentImpl[] result = new ExpExperimentImpl[experiments.length];
        for (int i = 0; i < experiments.length; i++)
        {
            result[i] = new ExpExperimentImpl(experiments[i]);
        }
        return result;
    }
}
