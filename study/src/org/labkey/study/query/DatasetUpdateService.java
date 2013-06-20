/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/*
* User: Dave
* Date: Jun 13, 2008
* Time: 4:15:51 PM
*/

/**
 * QueryUpdateService implementation for Study datasets.
 * <p>
 * Since datasets are of an unpredictable shape, this class just implements
 * the QueryUpdateService directly, working with <code>Map&lt;String,Object&gt;</code>
 * collections for the row data.
 */
public class DatasetUpdateService extends AbstractQueryUpdateService
{
    private final DataSetDefinition _dataset;
    private Set<String> _potentiallyNewParticipants = new HashSet<>();
    private Set<String> _potentiallyDeletedParticipants = new HashSet<>();
    private boolean _participantVisitResyncRequired = false;

    public DatasetUpdateService(DataSetTableImpl table)
    {
        super(table);
        _dataset = table.getDatasetDefinition();
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        String lsid = keyFromMap(keys);
        return StudyService.get().getDatasetRow(user, container, _dataset.getDataSetId(), lsid);
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws SQLException
    {
        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.IMPORT);
        int count = super._importRowsUsingETL(user, container, rows, null, context, extraScriptContext);
        if (count > 0)
        {
            StudyManager.dataSetModified(_dataset, user, true);
            resyncStudy(user, container);
        }
        return count;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.INSERT);
        List<Map<String, Object>> result = super._insertRowsUsingETL(user, container, rows, context, extraScriptContext);
        if (null != result && result.size() > 0)
        {
            for (Map<String, Object> row : result)
            {
                if (!isBulkLoad())
                    StudyServiceImpl.addDatasetAuditEvent(user, container, _dataset, null, row);

                try
                {
                    String participantID = getParticipant(row, user, container);
                    _potentiallyNewParticipants.add(participantID);
                }
                catch (ValidationException e)
                {
                    throw new QueryUpdateServiceException(e);
                }
            }

            _participantVisitResyncRequired = true; // 13717 : Study failing to resync() on dataset insert
            StudyManager.dataSetModified(_dataset, user, true);
            resyncStudy(user, container);
        }
        return result;
    }


    @Override
    public DataIteratorBuilder createImportETL(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
        QCState defaultQCState = StudyManager.getInstance().getDefaultQCState(_dataset.getStudy());
        DataIteratorBuilder insert = _dataset.getInsertDataIterator(user, data, null, true, context, defaultQCState, false);
//        TableInfo table = _dataset.getTableInfo(user, false);
//        DataIteratorBuilder insert = ((UpdateableTableInfo)table).persistRows(dsIterator, errors);
        return insert;
    }


    @Override
    protected int _pump(DataIteratorBuilder etl, final ArrayList<Map<String, Object>> rows, DataIteratorContext context)
    {
        try
        {
            boolean hasRowId = _dataset.getKeyManagementType() == DataSet.KeyManagementType.RowId;

            if (!hasRowId)
            {
                return super._pump(etl, rows, context);
            }

            synchronized (_dataset.getManagedKeyLock())
            {
                return super._pump(etl, rows, context);
            }
        }
        catch (RuntimeSQLException e)
        {
            String translated = _dataset.translateSQLException(e);
            if (translated != null)
            {
                context.getErrors().addRowError(new ValidationException(translated));
                return 0;
            }
            throw e;
        }
    }


    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new IllegalStateException();
//        List<String> errors = new ArrayList<String>();
//        String newLsid = StudyService.get().insertDatasetRow(user, container, _dataset.getDataSetId(), row, errors);
//
//        if(errors.size() > 0)
//        {
//            ValidationException e = new ValidationException();
//            for(String err : errors)
//                e.addError(new SimpleValidationError(err));
//            throw e;
//        }
//
//        //update the lsid
//        row.put("lsid", newLsid);
//        _potentiallyNewParticipants.add(getParticipant(row, user, container));
//        _participantVisitResyncRequired = true;
//
//        return row;
    }

    private @NotNull String getParticipant(Map<String, Object> row, User user, Container container) throws ValidationException, QueryUpdateServiceException, SQLException
    {
        String columnName = _dataset.getStudy().getSubjectColumnName();
        Object participant = row.get(columnName);
        if (participant == null)
        {
            participant = row.get("ParticipantId");
        }
        if (participant == null)
        {
            try
            {
                // This may be an update or delete where the user specified the LSID as the key, but didn't bother
                // sending the participant, so look it up
                Map<String, Object> originalRow = getRow(user, container, row);
                participant = originalRow == null ? null : originalRow.get(columnName);
                if (participant == null)
                {
                    participant = originalRow.get("ParticipantId");
                }
            }
            catch (InvalidKeyException e)
            {
                throw new QueryUpdateServiceException(e);
            }
        }
        if (participant == null)
        {
            throw new ValidationException("All dataset rows must include a value for " + columnName);
        }
        return participant.toString();
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.updateRows(user, container, rows, oldKeys, extraScriptContext);
        resyncStudy(user, container);
        return result;
    }

    private void resyncStudy(User user, Container container)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);

        StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.singletonList(_dataset), _potentiallyNewParticipants, _potentiallyDeletedParticipants, _participantVisitResyncRequired);
        _participantVisitResyncRequired = false;
        _potentiallyNewParticipants.clear();
        _potentiallyDeletedParticipants.clear();
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<String> errors = new ArrayList<>();
        String lsid = keyFromMap(oldRow);
        // Make sure we've found the original participant before doing the update
        String oldParticipant = getParticipant(oldRow, user, container);
        String newLsid = StudyService.get().updateDatasetRow(user, container, _dataset.getDataSetId(), lsid, row, errors);
        //update the lsid and return
        row.put("lsid", newLsid);
        if(errors.size() > 0)
        {
            ValidationException e = new ValidationException();
            for(String err : errors)
                e.addError(new SimpleValidationError(err));
            throw e;
        }

        row = getRow(user, container, row);

        String newParticipant = getParticipant(row, user, container);
        if (!oldParticipant.equals(newParticipant))
        {
            // Participant has changed - might be a reference to a new participant, or removal of the last reference to
            // the old participant
            _potentiallyNewParticipants.add(newParticipant);
            _potentiallyDeletedParticipants.add(oldParticipant);

            // Need to resync the ParticipantVisit table too
            _participantVisitResyncRequired = true;
        }
        else if (!_participantVisitResyncRequired)
        {
            // Check if the visit has changed, but only if we don't already know we need to resync
            Object oldSequenceNum = oldRow.get("SequenceNum");
            Object newSequenceNum = row.get("SequenceNum");
            if (!Objects.equals(oldSequenceNum, newSequenceNum))
            {
                _participantVisitResyncRequired = true;
            }
        }

        return row;
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.deleteRows(user, container, keys, extraScriptContext);
        resyncStudy(user, container);
        return result;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException, ValidationException
    {
        // Make sure we've found the original participant before doing the delete
        String participant = getParticipant(oldRow, user, container);
        StudyService.get().deleteDatasetRow(user, container, _dataset.getDataSetId(), keyFromMap(oldRow));
        _potentiallyDeletedParticipants.add(participant);
        _participantVisitResyncRequired = true;
        return oldRow;
    }

    public String keyFromMap(Map<String, Object> map) throws InvalidKeyException
    {
        Object lsid = map.get("lsid");
        if (lsid != null)
            return lsid.toString();
        lsid = map.get("LSID");
        if (lsid != null)
            return lsid.toString();
        
        boolean isDemographic = _dataset.isDemographicData();

        // if there was no explicit lsid and KeyManagementType == None, there is no non-lsid key that is unique by itself.
        // Unless of course it is a demographic table.
        if (!isDemographic && _dataset.getKeyManagementType() == DataSetDefinition.KeyManagementType.None)
        {
            throw new InvalidKeyException("No lsid, and no KeyManagement");
        }

        String keyPropertyName = isDemographic ? _dataset.getStudy().getSubjectColumnName() : _dataset.getKeyPropertyName();
        Object id = map.get(keyPropertyName);

        if (null == id)
        {
           id = map.get("Key");
        }

        // if there was no other type of key, this query is invalid
        if (null == id)
        {
            throw new InvalidKeyException(String.format("key needs one of 'lsid', '%s' or 'Key', none of which were found in %s", keyPropertyName, map));
        }

        // now look up lsid
        // if one is found, return that
        // if 0, it's legal to return null
        // if > 1, there is an integrity problem that should raise alarm
        String[] lsids = new TableSelector(getQueryTable().getColumn("LSID"), new SimpleFilter(keyPropertyName, id), null).getArray(String.class);

        if (lsids.length == 0)
        {
            return null;
        }
        else if (lsids.length > 1)
        {
            throw new IllegalStateException("More than one row matched for key '" + id + "' in column " +
                    _dataset.getKeyPropertyName() + " in dataset " +
                    _dataset.getName() + " in folder " +
                    _dataset.getContainer().getPath());
        }
        else return lsids[0];
    }
}
