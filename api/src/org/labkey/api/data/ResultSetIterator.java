/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.labkey.api.collections.ResultSetRowMapFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;

public class ResultSetIterator implements Iterator<Map>
{
    private final ResultSet _rs;
    private ResultSetRowMapFactory _factory;

    public static ResultSetIterator get(ResultSet rs)
    {
        return new ResultSetIterator(rs);
    }

    public ResultSetIterator(ResultSet rs)
    {
        _rs = rs;

        if (!(_rs instanceof CachedRowSetImpl))
        {
            try
            {
                _factory = new ResultSetRowMapFactory(_rs);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    }

    public boolean hasNext()
    {
        try
        {
            return !_rs.isLast();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Map next()
    {
        try
        {
            _rs.next();

            if (_rs instanceof CachedRowSetImpl)
                return ((CachedRowSetImpl)_rs).getRowMap();
            else
                return _factory.getRowMap(_rs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void remove()
    {
        throw new UnsupportedOperationException("Can't remove row when iterating");
    }
} 
