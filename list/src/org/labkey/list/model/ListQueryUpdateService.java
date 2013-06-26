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
package org.labkey.list.model;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.Pump;
import org.labkey.api.etl.WrapperDataIterator;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.list.view.ListItemAttachmentParent;

import java.io.File;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* User: Nick Arnold
* Date: June 5, 2012
* Time: 10:27:30 AM
*/

/**
 * Implementation of QueryUpdateService for Lists
 */
public class ListQueryUpdateService extends DefaultQueryUpdateService
{
    ListDefinitionImpl _list = null;
    private static String ID = "entityId";
    private VirtualFile _att = null;

    public ListQueryUpdateService(ListTable queryTable, TableInfo dbTable, ListDefinition list)
    {
        super(queryTable, dbTable);
        _list = (ListDefinitionImpl) list;
    }

    @Override
    protected DataIteratorContext getDataIteratorContext(BatchValidationException errors, InsertOption insertOption)
    {
        DataIteratorContext context = super.getDataIteratorContext(errors, insertOption);
        if (insertOption.batch)
        {
            context.setMaxRowErrors(100);
            context.setFailFast(false);
        }
        return context;
    }


    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> listRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> ret = null;

        if (null != listRow)
        {
            SimpleFilter keyFilter = getKeyFilter(listRow);

            if (null != keyFilter)
            {
                Map<String, Object> raw = new TableSelector(getQueryTable(), keyFilter, null).getMap();

                if (raw.size() > 0)
                {
                    ret = new CaseInsensitiveHashMap<>();

                    // EntityId
                    ret.put("EntityId", raw.get("entityid"));

                    for (DomainProperty prop : _list.getDomain().getProperties())
                    {
                        Object value = getField(raw, prop.getName());

                        if (null != value)
                            ret.put(prop.getName(), value);
                    }
                }
            }
        }

        return ret;
    }


    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new IllegalStateException("Method not used by ListQueryUpdateService");
    }


    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.INSERT);
        List<Map<String, Object>> result = super._insertRowsUsingETL(user, container, rows, context, extraScriptContext);

        if (null != result)
        {
            ListManager mgr = ListManager.get();

            for (Map row : result)
            {
                if (null != row.get(ID))
                {
                    // Audit each row
                    String entityId = (String) row.get(ID);
                    String newRecord = mgr.formatAuditItem(_list, user, row);

                    mgr.addAuditEvent(_list, user, "A new list record was inserted", entityId, null, newRecord);
                }
            }

            if (result.size() > 0 && !errors.hasErrors())
                mgr.indexList(_list);
        }

        return result;
    }

    @Override
    public DataIteratorBuilder createImportETL(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
        DataIteratorBuilder dib = super.createImportETL(user, container, data, context);
        return getAttachmentDataIteratorBuilder(dib, user, context.getInsertOption() == InsertOption.IMPORT ? getAttachmentDirectory(): null);
    }

    /**
     * TODO: Make attachmentDirs work for other QueryUpdateServices. This is private to list for now.
     */
    public int insertETL(DataLoader loader, User user, BatchValidationException errors, @Nullable VirtualFile attachmentDir, @Nullable ListImportProgress progress, boolean supportAutoIncrementKey)
    {
        DataIteratorContext context = new DataIteratorContext(errors);
        context.setFailFast(false);
        context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);    // this method is used by ListImporter and BackgroundListImporter
        context.setSupportAutoIncrementKey(supportAutoIncrementKey);
        setAttachmentDirectory(attachmentDir);

        DataIteratorBuilder dib = createImportETL(user, _list.getContainer(), loader, context);

        if (context.getErrors().hasErrors())
            return 0;                           // if there are errors dib may be returned as null (bug #17286)

        TableInfo ti = _list.getTable(user);

        try (DbScope.Transaction transaction = ti.getSchema().getScope().ensureTransaction())
        {
            Pump p = new Pump(dib, context);
            p.run();
            int inserted = p.getRowCount();

            if (!errors.hasErrors())
            {
                if (inserted > 0)
                    ListManager.get().addAuditEvent(_list, user, "Bulk inserted " + inserted + " rows to list.");
                transaction.commit();
                ListManager.get().indexList(_list);
                return inserted;
            }

            return 0;
        }
    }


    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws SQLException
    {
        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.IMPORT);
        int count = super._importRowsUsingETL(user, container, rows, null, context, extraScriptContext);
        if (count > 0 && !errors.hasErrors())
            ListManager.get().indexList(_list);
        return count;
    }


    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        // TODO: Check for equivalency so that attachments can be deleted etc.

        Map<String, DomainProperty> dps = new HashMap<>();
        for (DomainProperty dp : _list.getDomain().getProperties())
        {
            dps.put(dp.getPropertyURI(), dp);
        }

        ValidatorContext validatorCache = new ValidatorContext(_list.getContainer(), user);
        ListItem item = new ListItemImpl(_list);

        if (item.getProperties() != null)
        {
            List<ValidationError> errors = new ArrayList<>();
            for (Map.Entry<String, DomainProperty> entry : dps.entrySet())
            {
                Object value = row.get(entry.getValue().getName());
                validateProperty(entry.getValue(), value, row, errors, validatorCache);
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);
        }

        // MVIndicators
        Map<String, Object> rowCopy = new CaseInsensitiveHashMap<>();

        TableInfo qt = getQueryTable();
        for (Map.Entry<String, Object> r : row.entrySet())
        {
            ColumnInfo mvColumn = qt.getColumn(FieldKey.fromParts(r.getKey()));
            if (null != mvColumn && mvColumn.isMvIndicatorColumn())
                rowCopy.put(mvColumn.getMetaDataName(), r.getValue());
            else
                rowCopy.put(r.getKey(), r.getValue());
        }

        // Attempt to include key from oldRow if not found in row (As stated in the QUS Interface)
        if (!rowCopy.containsKey(_list.getKeyName()) && oldRow.containsKey(_list.getKeyName()))
            rowCopy.put(_list.getKeyName(), oldRow.get(_list.getKeyName()));

        Map<String, Object> result = super.updateRow(user, container, rowCopy, oldRow);

        if (null != result)
        {
            result = getRow(user, container, result);

            if (null != result.get(ID))
            {
                ListManager mgr = ListManager.get();
                String entityId = (String) result.get(ID);
                String oldRecord = mgr.formatAuditItem(_list, user, oldRow);
                String newRecord = mgr.formatAuditItem(_list, user, result);

                // Audit
                mgr.addAuditEvent(_list, user, "An existing list record was modified", entityId, oldRecord, newRecord);
            }
        }

        return result;
    }

    private boolean validateProperty(DomainProperty prop, Object value, Map<String, Object> newRow, List<ValidationError> errors, ValidatorContext validatorCache)
    {
        //check for isRequired
        if (prop.isRequired())
        {
            // for mv indicator columns either an indicator or a field value is sufficient
            boolean hasMvIndicator = prop.isMvEnabled() && (value instanceof ObjectProperty && ((ObjectProperty)value).getMvIndicator() != null);
            if (!hasMvIndicator && (null == value || (value instanceof ObjectProperty && ((ObjectProperty)value).value() == null)))
            {
                if (newRow.containsKey(prop.getName()) && newRow.get(prop.getName()) == null)
                {
                    errors.add(new PropertyValidationError("The field '" + prop.getName() + "' is required.", prop.getName()));
                    return false;
                }
            }
        }

        if (null != value)
        {
            for (IPropertyValidator validator : prop.getValidators())
            {
                if (!validator.validate(prop.getPropertyDescriptor(), value, errors, validatorCache))
                    return false;
            }
        }

        return true;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> result = super.deleteRow(user, container, oldRowMap);

        if (null != result)
        {
            if (null != result.get(ID))
            {
                ListManager mgr = ListManager.get();
                String entityId = (String) result.get(ID);
                String deletedRecord = mgr.formatAuditItem(_list, user, result);

                // Audit
                mgr.addAuditEvent(_list, user, "An existing list record was deleted", entityId, deletedRecord, null);

                // Remove discussions
                DiscussionService.get().deleteDiscussions(container, user, entityId);

                // Remove attachments
                AttachmentService.get().deleteAttachments(new ListItemAttachmentParent(entityId, container));

                // Clean up Search indexer
                if (result.size() > 0)
                    mgr.deleteItemIndex(_list, entityId);
            }
        }

        return result;
    }


    @Nullable
    public SimpleFilter getKeyFilter(Map<String, Object> map) throws InvalidKeyException
    {
        String keyName = _list.getKeyName();
        ListDefinition.KeyType type = _list.getKeyType();

        Object key = getField(map, _list.getKeyName());

        if (null == key)
        {
            // Auto-increment lists might not provide a key so allow them to pass through
            if (type.equals(ListDefinition.KeyType.AutoIncrementInteger))
                return null;
            throw new InvalidKeyException("No " + keyName + " provided for list \"" + _list.getName() + "\"");
        }

        // Check the type of the list to ensure proper casting of the key type
        if (type.equals(ListDefinition.KeyType.Integer) || type.equals(ListDefinition.KeyType.AutoIncrementInteger))
        {
            if (key instanceof Integer)
                return new SimpleFilter(keyName, key);
            return new SimpleFilter(keyName, Integer.valueOf(key.toString()));
        }

        return new SimpleFilter(keyName, key.toString());
    }

    @Nullable
    private Object getField(Map<String, Object> map, String key)
    {
        Object value = map.get(key);

        if (null == value)
            value = map.get("_" + key);

        if (null == value)
            value = map.get(key.replaceAll("\\s", "_"));

        return value;
    }

    private static class _AttachmentUploadHelper
    {
        _AttachmentUploadHelper(int i, DomainProperty dp)
        {
            index=i;
            domainProperty = dp;
        }
        final int index;
        final DomainProperty domainProperty;
        final FileNameUniquifier uniquifier = new FileNameUniquifier();
    }


    private DataIteratorBuilder getAttachmentDataIteratorBuilder(final DataIteratorBuilder builder, final User user, @Nullable final VirtualFile attachmentDir)
    {
        return new DataIteratorBuilder()
        {
            @Override
            public DataIterator getDataIterator(DataIteratorContext context)
            {
                DataIterator it = builder.getDataIterator(context);

                // find attachment columns
                int entityIdIndex = 0;
                final ArrayList<_AttachmentUploadHelper> attachmentColumns = new ArrayList<>();

                for (int c = 1; c <= it.getColumnCount(); c++)
                {
                    try
                    {
                        ColumnInfo col = it.getColumnInfo(c);

                        if (StringUtils.equalsIgnoreCase(ID, col.getName()))
                            entityIdIndex = c;

                        // Don't seem to have attachment information in the ColumnInfo, so we need to lookup the DomainProperty
                        // UNDONE: PropertyURI is not propagated, need to use name
                        DomainProperty domainProperty = _list.getDomain().getPropertyByName(col.getName());
                        if (null == domainProperty || domainProperty.getPropertyDescriptor().getPropertyType() != PropertyType.ATTACHMENT)
                            continue;

                        attachmentColumns.add(new _AttachmentUploadHelper(c,domainProperty));
                    }
                    catch (IndexOutOfBoundsException e) // Until issue is resolved between StatementDataIterator.getColumnCount() and SimpleTranslator.getColumnCount()
                    {
                        continue;
                    }
                }

                if (!attachmentColumns.isEmpty() && 0 != entityIdIndex)
                    return new AttachmentDataIterator(it, context.getErrors(), user, attachmentDir, entityIdIndex, attachmentColumns, context.getInsertOption());

                return it;
            }
        };
    }


    class AttachmentDataIterator extends WrapperDataIterator
    {
        final VirtualFile attachmentDir;
        final BatchValidationException errors;
        final int entityIdIndex;
        final ArrayList<_AttachmentUploadHelper> attachmentColumns;
        final InsertOption insertOption;
        final User user;

        AttachmentDataIterator(DataIterator insertIt, BatchValidationException errors,
                               User user,
                               VirtualFile attachmentDir,
                               int entityIdIndex,
                               ArrayList<_AttachmentUploadHelper> attachmentColumns,
                               InsertOption insertOption)
        {
            super(insertIt);
            this.attachmentDir = attachmentDir;
            this.errors = errors;
            this.entityIdIndex = entityIdIndex;
            this.attachmentColumns = attachmentColumns;
            this.insertOption = insertOption;
            this.user = user;
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            try
            {
                boolean ret = super.next();
                if (!ret)
                    return false;
                ArrayList<AttachmentFile> attachmentFiles = null;
                for (_AttachmentUploadHelper p : attachmentColumns)
                {
                    Object attachmentValue = get(p.index);
                    String filename = null;
                    AttachmentFile attachmentFile;

                    if (null == attachmentValue)
                        continue;
                    else if (attachmentValue instanceof String)
                    {
                        if (null == attachmentDir)
                        {
                            errors.addRowError(new ValidationException("Row " + get(0) + ": " + "Can't upload to field " + p.domainProperty.getName() + " with type " + p.domainProperty.getType().getLabel() + "."));
                            return false;
                        }
                        filename = (String) attachmentValue;
                        InputStream aIS = attachmentDir.getDir(p.domainProperty.getName()).getInputStream(p.uniquifier.uniquify(filename));
                        attachmentFile = new InputStreamAttachmentFile(aIS, filename);
                        attachmentFile.setFilename(filename);
                    }
                    else if (attachmentValue instanceof AttachmentFile)
                    {
                        attachmentFile = (AttachmentFile) attachmentValue;
                        filename = attachmentFile.getFilename();
                    }
                    else if (attachmentValue instanceof File)
                    {
                        attachmentFile = new FileAttachmentFile((File) attachmentValue);
                        filename = attachmentFile.getFilename();
                    }
                    else
                    {
                        errors.addRowError(new ValidationException("Row " + get(0) + ": " + "Unable to create attachament file."));
                        return false;
                    }

                    if (null == filename)
                        continue;

                    if (null == attachmentFiles)
                        attachmentFiles = new ArrayList<>();
                    attachmentFiles.add(attachmentFile);
                }

                if (null != attachmentFiles && !attachmentFiles.isEmpty())
                {
                    String entityId = String.valueOf(get(entityIdIndex));
                    AttachmentService.get().addAttachments(new ListItemAttachmentParent(entityId, _list.getContainer()), attachmentFiles, user);
                }
                return ret;
            }
            catch (AttachmentService.DuplicateFilenameException e)
            {
                errors.addRowError(new ValidationException(e.getMessage()));
                return false;
            }
            catch (Exception x)
            {
                throw UnexpectedException.wrap(x);
            }
        }
    }

    private void setAttachmentDirectory(VirtualFile att)
    {
        _att = att;
    }

    private VirtualFile getAttachmentDirectory()
    {
        return _att;
    }
}
