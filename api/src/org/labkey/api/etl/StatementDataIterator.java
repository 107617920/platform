/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.api.etl;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.ResultSetUtil;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;


class StatementDataIterator extends AbstractDataIterator
{
    protected Parameter.ParameterMap _stmt;
    DataIterator _data;
    int _executeCount = 0;

    Triple[] _bindings = null;

    // NOTE all columns are pass through to the source iterator, except for key columns
    ArrayList<ColumnInfo> _keyColumnInfo;
    ArrayList<Object> _keyValues;
    Integer _rowIdIndex = null;
    Integer _objectIdIndex = null;

    protected StatementDataIterator(DataIterator data, Parameter.ParameterMap map, DataIteratorContext context)
    {
        super(context);
        _data = data;
        _stmt = map;

        _keyColumnInfo = new ArrayList<ColumnInfo>(Collections.nCopies(data.getColumnCount()+1,(ColumnInfo)null));
        _keyValues = new ArrayList<Object>(Collections.nCopies(data.getColumnCount()+1,null));
    }

    // configure columns returned by statement, e.g. rowid
    // index > 0 to override an existing column
    // index == -1 to append
    void setRowIdColumn(int index, ColumnInfo col)
    {
        if (-1 == index)
        {
            _keyColumnInfo.add(null);
            _keyValues.add(null);
            index = _keyColumnInfo.size()-1;
        }
        _keyColumnInfo.set(index,col);
        _rowIdIndex = index;
    }

    void setObjectIdColumn(int index, ColumnInfo col)
    {
        if (-1 == index)
        {
            _keyColumnInfo.add(null);
            _keyValues.add(null);
            index = _keyColumnInfo.size()-1;
        }
        _keyColumnInfo.set(index,col);
        _objectIdIndex = index;
    }

    void init()
    {
        // map from source to target
        ArrayList<Triple> bindings = new ArrayList<Triple>(_stmt.size());
        // by name
        for (int i=1 ; i<=_data.getColumnCount() ; i++)
        {
            ColumnInfo col = _data.getColumnInfo(i);
            Parameter to = null;
            if (to == null && null != col.getPropertyURI())
                to = _stmt.getParameter(col.getPropertyURI());
            if (to == null)
                to = _stmt.getParameter(col.getName());
            if (null != to)
            {
                FieldKey mvName = col.getMvColumnName();
                Parameter mv = null==mvName ? null : _stmt.getParameter(mvName.getName());
                bindings.add(new Triple(i, to, mv));
            }
        }
        _bindings = bindings.toArray(new Triple[bindings.size()]);
    }

    public int getExecuteCount()
    {
        return _executeCount;
    }

    private static class Triple
    {
        Triple(int from, Parameter to, Parameter mv)
        {
            this.fromIndex = from;
            this.to = to;
            this.mv = mv;
        }
        int fromIndex;
        Parameter to;
        Parameter mv;
    }

    @Override
    public int getColumnCount()
    {
        return _keyColumnInfo.size()-1;
    }


    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        if (null != _keyColumnInfo.get(i))
            return _keyColumnInfo.get(i);
        return _data.getColumnInfo(i);
    }

    private boolean _firstNext = true;

    protected void onFirst()
    {
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        if (_firstNext)
        {
            _firstNext = false;
            onFirst();
        }

        if (!_data.next())
            return false;

        ResultSet rs = null;
        try
        {
            _stmt.clearParameters();
            for (Triple binding : _bindings)
            {
                Object value = _data.get(binding.fromIndex);
                if (null == value)
                    continue;
                if (value instanceof MvFieldWrapper)
                {
                    if (null != binding.mv)
                        binding.mv.setValue(((MvFieldWrapper) value).getMvIndicator());
                    binding.to.setValue(((MvFieldWrapper) value).getValue());
                }
                else
                {
                    binding.to.setValue(value);
                }
            }

            checkShouldCancel();

            _stmt.execute();

            if (null != _rowIdIndex)
                _keyValues.set(_rowIdIndex, _stmt.getRowId());
            if (null != _objectIdIndex)
                _keyValues.set(_objectIdIndex, _stmt.getObjectId());

            _executeCount++;
            return true;
        }
        catch (SQLException x)
        {
            if (StringUtils.startsWith(x.getSQLState(), "22") || SqlDialect.isConstraintException(x))
            {
                getRowError().addGlobalError(x);
                return false;
            }
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    @Override
    public Object get(int i)
    {
        if (null != _keyColumnInfo.get(i))
            return _keyValues.get(i);
        return _data.get(i);
    }


    @Override
    public void close() throws IOException
    {
        _data.close();
        if (_stmt != null)
        {
            try {
                _stmt.close();
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
        _stmt = null;
    }

}
