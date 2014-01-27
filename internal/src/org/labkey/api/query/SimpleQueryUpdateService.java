/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.SimpleUserSchema.SimpleTable;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * User: kevink
 */
public class SimpleQueryUpdateService extends DefaultQueryUpdateService
{
    public SimpleQueryUpdateService(final SimpleTable queryTable, TableInfo dbTable)
    {
        this(queryTable, dbTable, new SimpleDomainUpdateHelper(queryTable));
    }

    public SimpleQueryUpdateService(final SimpleTable queryTable, TableInfo dbTable, DomainUpdateHelper helper)
    {
        super(queryTable, dbTable, helper);
    }


    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<Enum,Object> configParameters, Map<String, Object> extraScriptContext)
            throws SQLException
    {
        return _importRowsUsingETL(user, container, rows, null,  getDataIteratorContext(errors, InsertOption.IMPORT), extraScriptContext);
    }


    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<String, Object> extraScriptContext)
            throws SQLException
    {
        return _importRowsUsingETL(user, container, rows, null,  getDataIteratorContext(errors, InsertOption.MERGE), extraScriptContext);
    }


    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super._insertRowsUsingETL(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT), extraScriptContext);
        return result;
    }


    @Override
    protected SimpleTable getQueryTable()
    {
        return (SimpleTable)super.getQueryTable();
    }

    public DataIteratorBuilder createImportETL(User user, Container container, final DataIteratorBuilder data, DataIteratorContext context)
    {
        // Create object uri if column is present and domain is not empty.
        final ColumnInfo objectUriColumn = getQueryTable().getObjectUriColumn();
        final Domain domain = getQueryTable().getDomain();

        DataIteratorBuilder ret = data;
        if (objectUriColumn != null && domain != null && domain.getProperties().length > 0)
        {
            ret = new DataIteratorBuilder()
            {
                @Override
                public DataIterator getDataIterator(DataIteratorContext context)
                {
                    DataIterator it = data.getDataIterator(context);

                    Map<String,Integer> colMap = DataIteratorUtil.createColumnNameMap(it);
                    Integer objectUriIndex = colMap.get(objectUriColumn.getName());
                    SimpleTranslator out = new SimpleTranslator(it, context);
                    Callable call = new Callable()
                    {
                        @Override
                        public Object call() throws Exception
                        {
                            return getQueryTable().createObjectURI();
                        }
                    };
                    for (int i=1 ; i<=it.getColumnCount() ; i++)
                    {
                        if (null != objectUriIndex && i == objectUriIndex)
                            out.addCoaleseColumn(objectUriColumn.getName(), i, call);
                        else
                            out.addColumn(i);
                    }
                    if (null == objectUriIndex)
                        out.addColumn(objectUriColumn, call);
                    return LoggingDataIterator.wrap(out);
                }
            };
        }

        return super.createImportETL(user, container, ret, context);
    }


    // TODO kill DomainUpdateHelper! Should be replaced by ETL
    public static class SimpleDomainUpdateHelper implements DomainUpdateHelper
    {
        SimpleTable _queryTable;

        public SimpleDomainUpdateHelper(SimpleTable queryTable)
        {
            _queryTable = queryTable;
        }

        @Override
        public Domain getDomain()
        {
            return _queryTable.getDomain();
        }

        @Override
        public ColumnInfo getObjectUriColumn()
        {
            return _queryTable.getObjectUriColumn();
        }

        @Override
        public String createObjectURI()
        {
            return _queryTable.createObjectURI();
        }

        @Override
        public Iterable<PropertyColumn> getPropertyColumns()
        {
            return _queryTable.getPropertyColumns();
        }

        @Override
        public Container getDomainContainer(Container c)
        {
            return c;
        }

    }
}
