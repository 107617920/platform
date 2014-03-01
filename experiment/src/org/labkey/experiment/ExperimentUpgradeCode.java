/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.experiment;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.ExceptionUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * User: adam
 * Date: 4/9/13
 * Time: 7:12 PM
 */
@SuppressWarnings("UnusedDeclaration")
public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final String LIST_SEQUENCE_NAME = "org.labkey.list.Lists";    // Matches name in ListManager
    private static final Logger LOG = Logger.getLogger(ExperimentUpgradeCode.class);

    public void createListSequences(ModuleContext moduleContext)
    {
        String sql = "SELECT Container, MAX(ListId) AS Max FROM exp.List GROUP BY Container";
        new SqlSelector(ExperimentService.get().getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                Container c = ContainerManager.getForId(rs.getString(1));

                if (null != c)
                {
                    int max = rs.getInt(2);
                    DbSequence sequence = DbSequenceManager.get(c, LIST_SEQUENCE_NAME);
                    sequence.ensureMinimum(max);
                }
            }
        });
    }

    /** Used for 13.31->14.1 **/
    public void resyncDomainProjects(ModuleContext context)
    {
        /*
            Attempt to repair domain and property descriptors whose projects are incorrect.
            This was a possible state upon moving containers (see #18968). This would leave lists, studies, etc,
            in an unusable and nondeletable state. Fixing at least lists are necessary for the List 13.30 -> 14.1 upgrade.
         */

        List<DomainDescriptor> dds = new TableSelector(OntologyManager.getTinfoDomainDescriptor()).getArrayList(DomainDescriptor.class);

        HashMap<String, DomainDescriptor> okDomains = new HashMap<>();
        List<DomainDescriptor> problemDomains = new ArrayList<>();
        for (DomainDescriptor dd : dds)
        {
            Container containerProject = dd.getContainer().getProject();
            if (containerProject == null)
                containerProject = ContainerManager.getRoot();
            if (containerProject.equals(dd.getProject()))
            {
                // These domain descriptors are likely OK. However, in some cases (studies and report properties), we've
                // seen cases where we wound up with two domain descriptor records for the same DomainURI, one with correct
                // // project, one with incorrect. We'll need to process these in a later step.
                okDomains.put(dd.getDomainURI(), dd);
            }
            else
            {
                problemDomains.add(dd);
            }
        }

        for (DomainDescriptor dd : problemDomains)
        {
            LOG.info("Resyncing domain project for domainId: " + dd.getDomainId());

            DomainDescriptor conflictingDomain = okDomains.get(dd.getDomainURI());
            if (conflictingDomain != null)
            {
                // This is one of the domainURI's with multiple domain descriptor records. From investigation on labkey.org,
                // it appears that the one with the correct project is actually the bad record that should be moved aside,
                // as it isn't properly wired to any property descriptors (and therefore the columns in the corresponding storage table are incorrect)
                if (!moveConflictingDomain(conflictingDomain))
                {
                    String listMsg = "list".equals(conflictingDomain.getStorageSchemaName()) ? " and it is a list. This will block the list schema upgrade." : "";
                    LOG.error("Can't fix conflicting domainid: " + conflictingDomain.getDomainId() + listMsg);
                    // Send this to mothership so we can see if this situation exists in the wild and do something about it if this was domain descriptor
                    // for a list.
                    ExceptionUtil.logExceptionToMothership(null, new Exception("Error resyncing domain for uri: " + conflictingDomain.getDomainURI()));
                    continue;
                }
            }
            repairProblemDomain(dd);
        }
    }

    private void repairProblemDomain(DomainDescriptor dd)
    {
        Container containerProject = dd.getContainer().getProject();
        if (containerProject == null)
            containerProject = ContainerManager.getRoot();
        dd.setProject(containerProject);
        Collection<Integer> propIds = getPropertyIds(dd);
        SimpleFilter filter = new SimpleFilter();
        SimpleFilter.InClause inClause = new SimpleFilter.InClause(FieldKey.fromParts("propertyId"), propIds);
        filter.addClause(inClause);
        List<PropertyDescriptor> pds = new TableSelector(OntologyManager.getTinfoPropertyDescriptor(), filter, null).getArrayList(PropertyDescriptor.class);
        for (PropertyDescriptor pd : pds)
        {
            pd.setProject(containerProject);
        }
        try
        {
            for (PropertyDescriptor pd : pds)
            {
                OntologyManager.updatePropertyDescriptor(pd);
            }
            OntologyManager.updateDomainDescriptor(dd);
        }
        catch (Exception e)
        {
            LOG.error("Exception resyncing domain project for domainId: " + dd.getDomainId(), e);
        }
    }

    private Collection<Integer> getPropertyIds(DomainDescriptor dd)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("domainId"), dd.getDomainId());
        return new TableSelector(OntologyManager.getTinfoPropertyDomain().getColumn(FieldKey.fromParts("propertyId")), filter, null).getCollection(Integer.class);
    }

    private boolean moveConflictingDomain(DomainDescriptor conflictingDomain)
    {
        Collection<Integer> propIds = getPropertyIds(conflictingDomain);
        // On labkey.org, this conflcting record has 0 matching records in exp.propertyDomain for all but one case.
        // If some other server in the wild is different, we don't know how to handle it.
        if (propIds.size() == 0)
        {
            conflictingDomain.setDomainURI(conflictingDomain.getDomainURI() + "_BAD");
            try
            {
                OntologyManager.updateDomainDescriptor(conflictingDomain);
            }
            catch (Exception e)
            {
                LOG.error("Exception moving conflicting domain aside, domainId: " + conflictingDomain.getDomainId(), e);
                return false;
            }
            return true;
        }
        else return false;
    }
}
