/*
 * Copyright (c) 2005-2011 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections15.IteratorUtils;
import org.apache.log4j.Logger;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;


/**
 * User: mbellew
 * Date: Nov 29, 2005
 * Time: 12:52:06 PM
 */
public class CachedResultSet implements ResultSet, Table.TableResultSet
{
    private static final Logger _log = Logger.getLogger(CachedResultSet.class);

    // metadata
    private ResultSetMetaData _md;
    private HashMap<String, Integer> _columns;

    // data
    ArrayListMap<String, Object>[] _arrayListMaps;
    private Map<String, Object>[] _maps;
    private boolean _isComplete = true;
    private boolean _wasClosed = false;

    private StackTraceElement[] _stackTrace = null;
    private String _url = null;
    private String _threadName = null;

    // state
    private int _row = -1;
    private int _direction = 1;
    private int _fetchSize = 1;
    private Object _lastObject = null;


    private CachedResultSet()
    {
        assert MemTracker.put(this);
    }


    public CachedResultSet(ResultSet rs, boolean cacheMetaData, int maxRows) throws SQLException
    {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        if (maxRows == Table.ALL_ROWS)
            maxRows = Integer.MAX_VALUE;

        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

        // Note: we check in this order to avoid consuming the "extra" row used to detect complete vs. not
        while (list.size() < maxRows && rs.next())
            list.add(factory.getRowMap(rs));

        // If we have another row, then we're not complete
        boolean isComplete = !rs.next();

        init(rs.getMetaData(), cacheMetaData, list, isComplete);
    }


    public CachedResultSet(ResultSetMetaData md, boolean cacheMetaData, List<Map<String, Object>> maps, boolean isComplete)
    {
        this();
        init(md, cacheMetaData, maps, isComplete);
    }


    private void init(ResultSetMetaData md, boolean cacheMetaData, List<Map<String, Object>> maps, boolean isComplete)
    {
        // TODO: consider moving to ArrayList internally to avoid this array nonsense
        // HACK
        if (maps.size() > 0 && maps.get(0) instanceof ArrayListMap)
            //noinspection unchecked
            init(md, cacheMetaData, maps.toArray((Map<String, Object>[])new ArrayListMap[maps.size()]), isComplete);
        else
            //noinspection unchecked
            init(md, cacheMetaData, maps.toArray((Map<String, Object>[])new Map[maps.size()]), isComplete);

        // Stash stack trace that created this CachedRowSet
        if (AppProps.getInstance().isDevMode())
        {
            _stackTrace = Thread.currentThread().getStackTrace();

            if (HttpView.getStackSize() > 0)
            {
                try
                {
                    _url = ViewServlet.getOriginalURL();
                }
                catch (Exception x)
                {
                    // we might not be in a view thread...
                }
            }

            _threadName = Thread.currentThread().getName();
        }
    }


    private void init(ResultSetMetaData md, boolean cacheMetaData, Map<String, Object>[] maps, boolean isComplete)
    {
        try
        {
            if (maps.length > 0 && maps[0] instanceof ArrayListMap)
                _arrayListMaps = (ArrayListMap<String, Object>[]) maps;

            _md = cacheMetaData ? new CachedResultSetMetaData(md) : md;
            _maps = maps;
            _isComplete = isComplete;
            _columns = new HashMap<String, Integer>(_md.getColumnCount() * 2);

            for (int col = _md.getColumnCount(); col >= 1; col--)
            {
                String colName = _md.getColumnName(col).toLowerCase();
                assert !_columns.containsKey(colName) : "Duplicate column name: " + colName;
                _columns.put(colName, col);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    // Set an alternate stack trace -- good for async queries, to indicate original creation stack trace
    public void setStackTrace(StackTraceElement[] stackTrace)
    {
        _stackTrace = stackTrace;
    }


    //
    // ResultSet
    //

    public void setFetchDirection(int direction) throws SQLException
    {
        //UNDONE: does this affect next()/prev() or not???
        _direction = direction == FETCH_REVERSE ? -1 : 1;
    }

    public int getFetchDirection() throws SQLException
    {
        return _direction == 1 ? FETCH_FORWARD : FETCH_REVERSE;
    }

    public void setFetchSize(int rows) throws SQLException
    {
        _fetchSize = rows;
    }

    public int getFetchSize() throws SQLException
    {
        return _fetchSize;
    }

    public int getType() throws SQLException
    {
        return TYPE_SCROLL_INSENSITIVE;
    }

    public int getConcurrency() throws SQLException
    {
        return CONCUR_READ_ONLY;
    }

    public boolean next() throws SQLException
    {
        return relative(_direction);
    }

    public void close() throws SQLException
    {
        _wasClosed = true;
    }

    public boolean wasNull() throws SQLException
    {
        return _lastObject == null;
    }

    //
    //  getDATA()
    //

    public String getString(int columnIndex) throws SQLException
    {
        return _string(getObject(columnIndex));
    }

    public String getString(String columnName) throws SQLException
    {
        return _string(getObject(columnName));
    }

    @SuppressWarnings({"UNUSED_THROWS"})
    private String _string(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof String)
            return (String) o;
        return ConvertUtils.convert(o);
    }

    public boolean getBoolean(int columnIndex) throws SQLException
    {
        return _boolean(getObject(columnIndex));
    }

    public boolean getBoolean(String columnName) throws SQLException
    {
        return _boolean(getObject(columnName));
    }

    private boolean _boolean(Object o) throws SQLException
    {
        if (null == o)
            return false;
        if (o instanceof Boolean)
            return (Boolean) o;
        if (o instanceof Number)
            return ((Number) o).intValue() != 0;
        throwConversionError();
        return false;
    }

    public byte getByte(int columnIndex) throws SQLException
    {
        return _byte(getObject(columnIndex));
    }

    public byte getByte(String columnName) throws SQLException
    {
        return _byte(getObject(columnName));
    }

    public byte _byte(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Byte)
            return (Byte) o;
        throwConversionError();
        return 0;
    }

    public short getShort(int columnIndex) throws SQLException
    {
        return _short(getObject(columnIndex));
    }

    public short getShort(String columnName) throws SQLException
    {
        return _short(getObject(columnName));
    }

    public short _short(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Short || o instanceof Byte)
            return ((Number) o).shortValue();
        throwConversionError();
        return 0;
    }

    public int getInt(int columnIndex) throws SQLException
    {
        return _int(getObject(columnIndex));
    }

    public int getInt(String columnName) throws SQLException
    {
        return _int(getObject(columnName));
    }

    public int _int(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Integer || o instanceof Short || o instanceof Byte)
            return ((Number) o).intValue();
        throwConversionError();
        return 0;
    }

    public long getLong(int columnIndex) throws SQLException
    {
        return _long(getObject(columnIndex));
    }

    public long getLong(String columnName) throws SQLException
    {
        return _long(getObject(columnName));
    }

    public long _long(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Long || o instanceof Short || o instanceof Byte)
            return ((Number) o).longValue();
        throwConversionError();
        return 0;
    }

    public float getFloat(int columnIndex) throws SQLException
    {
        return _float(getObject(columnIndex));
    }

    public float getFloat(String columnName) throws SQLException
    {
        return _float(getObject(columnName));
    }

    public float _float(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Number)
            return ((Number) o).floatValue();
        throwConversionError();
        return 0;
    }

    public double getDouble(int columnIndex) throws SQLException
    {
        return _double(getObject(columnIndex));
    }

    public double getDouble(String columnName) throws SQLException
    {
        return _double(getObject(columnName));
    }

    public double _double(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Number)
        {
            double value = ((Number) o).doubleValue();
            return ResultSetUtil.mapDatabaseDoubleToJavaDouble(value);
        }
        throwConversionError();
        return 0;
    }

    @Deprecated
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException
    {
        return _decimal(getObject(columnIndex), scale);
    }

    @Deprecated
    public BigDecimal getBigDecimal(String columnName, int scale) throws SQLException
    {
        return _decimal(getObject(columnName), scale);
    }

    public BigDecimal _decimal(Object o, int scale) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof BigDecimal)
        {
            BigDecimal d = (BigDecimal) o;
            if (d.scale() == scale)
                return d;
            return d.setScale(scale);
        }
        throwConversionError();
        return null;
    }

    public byte[] getBytes(int columnIndex) throws SQLException
    {
        return _bytes(getObject(columnIndex));
    }

    public byte[] getBytes(String columnName) throws SQLException
    {
        return _bytes(getObject(columnName));
    }

    public byte[] _bytes(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof byte[])
            return (byte[]) o;
        throwConversionError();
        return null;
    }

    public Date getDate(int columnIndex) throws SQLException
    {
        return _date(getObject(columnIndex));
    }

    public Date getDate(String columnName) throws SQLException
    {
        return _date(getObject(columnName));
    }

    public java.sql.Date _date(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof java.sql.Date)
            return (java.sql.Date) o;
        if (o instanceof Timestamp)
            return new java.sql.Date(((Timestamp)o).getTime());
        throwConversionError();
        return null;
    }

    public Time getTime(int columnIndex) throws SQLException
    {
        return _time(getObject(columnIndex));
    }

    public Time getTime(String columnName) throws SQLException
    {
        return _time(getObject(columnName));
    }

    public Time _time(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof Time)
            return (Time) o;
        throwConversionError();
        return null;
    }

    public Timestamp getTimestamp(int columnIndex) throws SQLException
    {
        return _timestamp(getObject(columnIndex));
    }

    public Timestamp getTimestamp(String columnName) throws SQLException
    {
        return _timestamp(getObject(columnName));
    }

    public Timestamp _timestamp(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof Timestamp)
            return (Timestamp) o;
        if (o instanceof java.util.Date)
            return new Timestamp(((java.util.Date) o).getTime());
        if (o instanceof Long)
            return new Timestamp((Long) o);
        throwConversionError();
        return null;
    }

    public InputStream getAsciiStream(int columnIndex) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    public InputStream getAsciiStream(String columnName) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    @Deprecated
    public InputStream getUnicodeStream(int columnIndex) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    public InputStream getUnicodeStream(String columnName) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    public InputStream getBinaryStream(int columnIndex) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    public InputStream getBinaryStream(String columnName) throws SQLException
    {
        return (InputStream) throwNYI();
    }


    public SQLWarning getWarnings() throws SQLException
    {
        return (SQLWarning) throwNYI();
    }

    public void clearWarnings() throws SQLException
    {
    }

    public String getCursorName() throws SQLException
    {
        return (String) throwNYI();
    }

    public ResultSetMetaData getMetaData() throws SQLException
    {
        return _md;
    }

    public Object getObject(int columnIndex) throws SQLException
    {
        if (_row < 0 || _row >= _maps.length)
            throw new SQLException("No current row");

        if (null != _arrayListMaps)
            _lastObject = _arrayListMaps[_row].get(columnIndex);
        else
            _lastObject = _maps[_row].get(_md.getColumnName(columnIndex));

        if (_lastObject instanceof Double)
            _lastObject = ResultSetUtil.mapDatabaseDoubleToJavaDouble((Double) _lastObject);

        return _lastObject;
    }

    public Object getObject(String columnName) throws SQLException
    {
        _lastObject = _maps[_row].get(columnName);
        // check for no illegal column name
        if (_lastObject == null)
            findColumn(columnName);

        if (_lastObject instanceof Double)
            _lastObject = ResultSetUtil.mapDatabaseDoubleToJavaDouble((Double) _lastObject);

        return _lastObject;
    }


    // careful! this does no error checking
    public void _setObject(int columnIndex, Object o) throws SQLException
    {
        if (null != _arrayListMaps)
            _arrayListMaps[_row].set(columnIndex,o);
        else
            _maps[_row].put(_md.getColumnName(columnIndex),o);
    }


    public int findColumn(String columnName) throws SQLException
    {
        Integer i = _columns.get(columnName.toLowerCase());
        if (null == i)
            throw new SQLException("No such column: " + columnName);
        return i;
    }


    public Reader getCharacterStream(int columnIndex) throws SQLException
    {
        return null;
    }

    public Reader getCharacterStream(String columnName) throws SQLException
    {
        return null;
    }

    public BigDecimal getBigDecimal(int columnIndex) throws SQLException
    {
        return null;
    }

    public BigDecimal getBigDecimal(String columnName) throws SQLException
    {
        return null;
    }

    public boolean isBeforeFirst() throws SQLException
    {
        return _row == -1;
    }

    public boolean isAfterLast() throws SQLException
    {
        return _row == _maps.length;
    }

    public boolean isFirst() throws SQLException
    {
        return _maps.length > 0 && _row == 0;
    }

    public boolean isLast() throws SQLException
    {
        return _maps.length > 0 && _row == _maps.length - 1;
    }

    public void beforeFirst() throws SQLException
    {
        _row = -1;
    }

    public void afterLast() throws SQLException
    {
        _row = _maps.length;
    }

    protected void finalize() throws Throwable
    {
        if (!_wasClosed)
        {
            close();

            if (AppProps.getInstance().isDevMode())
            {
                StringBuilder error = new StringBuilder("CachedResultSetImpl was not closed.");
                if (null != _url)
                    error.append("\nURL: ").append(_url);
                else if (_threadName != null)
                    error.append("\nthreadName: ").append(_threadName);
                error.append("\nStack trace from the creation:");
                error.append(ExceptionUtil.renderStackTrace(_stackTrace));

                _log.error(error);
            }
        }
        super.finalize();
    }

    public boolean first() throws SQLException
    {
        return absolute(1);
    }

    public boolean last() throws SQLException
    {
        return absolute(-1);
    }

    public int getRow() throws SQLException
    {
        // adjust to 1-based
        return _row >= 0 && _row < _maps.length ? _row + 1 : 0;
    }

    public boolean absolute(int row) throws SQLException
    {
        if (row >= 0)
            beforeFirst();
        else
            afterLast();
        return relative(row);
    }

    public boolean relative(int rows) throws SQLException
    {
        _row = max(-1, min(_maps.length, _row + rows));
        return getRow() != 0;
    }

    public boolean previous() throws SQLException
    {
        return relative(-1 * _direction);
    }

    public boolean rowUpdated() throws SQLException
    {
        return false;
    }

    public boolean rowInserted() throws SQLException
    {
        return false;
    }

    public boolean rowDeleted() throws SQLException
    {
        return false;
    }

    public void updateNull(int columnIndex) throws SQLException
    {
        throwNYI();
    }

    public void updateBoolean(int columnIndex, boolean x) throws SQLException
    {
        throwNYI();
    }

    public void updateByte(int columnIndex, byte x) throws SQLException
    {
        throwNYI();
    }

    public void updateShort(int columnIndex, short x) throws SQLException
    {
        throwNYI();
    }

    public void updateInt(int columnIndex, int x) throws SQLException
    {
        throwNYI();
    }

    public void updateLong(int columnIndex, long x) throws SQLException
    {
        throwNYI();
    }

    public void updateFloat(int columnIndex, float x) throws SQLException
    {
        throwNYI();
    }

    public void updateDouble(int columnIndex, double x) throws SQLException
    {
        throwNYI();
    }

    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException
    {
        throwNYI();
    }

    public void updateString(int columnIndex, String x) throws SQLException
    {
        throwNYI();
    }

    public void updateBytes(int columnIndex, byte x[]) throws SQLException
    {
        throwNYI();
    }

    public void updateDate(int columnIndex, Date x) throws SQLException
    {
        throwNYI();
    }

    public void updateTime(int columnIndex, Time x) throws SQLException
    {
        throwNYI();
    }

    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException
    {
        throwNYI();
    }

    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException
    {
        throwNYI();
    }

    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException
    {
        throwNYI();
    }

    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException
    {
        throwNYI();
    }

    public void updateObject(int columnIndex, Object x, int scale) throws SQLException
    {
        throwNYI();
    }

    public void updateObject(int columnIndex, Object x) throws SQLException
    {
        throwNYI();
    }

    public void updateNull(String columnName) throws SQLException
    {
        throwNYI();
    }

    public void updateBoolean(String columnName, boolean x) throws SQLException
    {
        throwNYI();
    }

    public void updateByte(String columnName, byte x) throws SQLException
    {
        throwNYI();
    }

    public void updateShort(String columnName, short x) throws SQLException
    {
        throwNYI();
    }

    public void updateInt(String columnName, int x) throws SQLException
    {
        throwNYI();
    }

    public void updateLong(String columnName, long x) throws SQLException
    {
        throwNYI();
    }

    public void updateFloat(String columnName, float x) throws SQLException
    {
        throwNYI();
    }

    public void updateDouble(String columnName, double x) throws SQLException
    {
        throwNYI();
    }

    public void updateBigDecimal(String columnName, BigDecimal x) throws SQLException
    {
        throwNYI();
    }

    public void updateString(String columnName, String x) throws SQLException
    {
        throwNYI();
    }

    public void updateBytes(String columnName, byte x[]) throws SQLException
    {
        throwNYI();
    }

    public void updateDate(String columnName, Date x) throws SQLException
    {
        throwNYI();
    }

    public void updateTime(String columnName, Time x) throws SQLException
    {
        throwNYI();
    }

    public void updateTimestamp(String columnName, Timestamp x) throws SQLException
    {
        throwNYI();
    }

    public void updateAsciiStream(String columnName, InputStream x, int length) throws SQLException
    {
        throwNYI();
    }

    public void updateBinaryStream(String columnName, InputStream x, int length) throws SQLException
    {
        throwNYI();
    }

    public void updateCharacterStream(String columnName, Reader reader, int length) throws SQLException
    {
        throwNYI();
    }

    public void updateObject(String columnName, Object x, int scale) throws SQLException
    {
        throwNYI();
    }

    public void updateObject(String columnName, Object x) throws SQLException
    {
        throwNYI();
    }

    public void insertRow() throws SQLException
    {
        throwNYI();
    }

    public void updateRow() throws SQLException
    {
        throwNYI();
    }

    public void deleteRow() throws SQLException
    {
        throwNYI();
    }

    public void refreshRow() throws SQLException
    {
        throwNYI();
    }

    public void cancelRowUpdates() throws SQLException
    {
        throwNYI();
    }

    public void moveToInsertRow() throws SQLException
    {
        throwNYI();
    }

    public void moveToCurrentRow() throws SQLException
    {
        throwNYI();
    }

    public Statement getStatement() throws SQLException
    {
        return (Statement) throwNYI();
    }

    public Object getObject(int i, Map<String, Class<?>> map) throws SQLException
    {
        return throwNYI();
    }

    public Ref getRef(int i) throws SQLException
    {
        return (Ref) throwNYI();
    }

    public Blob getBlob(int i) throws SQLException
    {
        return (Blob) throwNYI();
    }

    public Clob getClob(int i) throws SQLException
    {
        return (Clob) throwNYI();
    }

    public Array getArray(int i) throws SQLException
    {
        return (Array) throwNYI();
    }

    public Object getObject(String colName, Map<String, Class<?>> map) throws SQLException
    {
        return throwNYI();
    }

    public Ref getRef(String colName) throws SQLException
    {
        return (Ref) throwNYI();
    }

    public Blob getBlob(String colName) throws SQLException
    {
        return (Blob) throwNYI();
    }

    public Clob getClob(String colName) throws SQLException
    {
        return (Clob) throwNYI();
    }

    public Array getArray(String colName) throws SQLException
    {
        return (Array) throwNYI();
    }

    public Date getDate(int columnIndex, Calendar cal) throws SQLException
    {
        return (Date) throwNYI();
    }

    public Date getDate(String columnName, Calendar cal) throws SQLException
    {
        return (Date) throwNYI();
    }

    public Time getTime(int columnIndex, Calendar cal) throws SQLException
    {
        return (Time) throwNYI();
    }

    public Time getTime(String columnName, Calendar cal) throws SQLException
    {
        return (Time) throwNYI();
    }

    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException
    {
        return (Timestamp) throwNYI();
    }

    public Timestamp getTimestamp(String columnName, Calendar cal) throws SQLException
    {
        return (Timestamp) throwNYI();
    }

    public URL getURL(int columnIndex) throws SQLException
    {
        return (URL) throwNYI();
    }

    public URL getURL(String columnName) throws SQLException
    {
        return (URL) throwNYI();
    }

    public void updateRef(int columnIndex, Ref x) throws SQLException
    {
        throwNYI();
    }

    public void updateRef(String columnName, Ref x) throws SQLException
    {
        throwNYI();
    }

    public void updateBlob(int columnIndex, Blob x) throws SQLException
    {
        throwNYI();
    }

    public void updateBlob(String columnName, Blob x) throws SQLException
    {
        throwNYI();
    }

    public void updateClob(int columnIndex, Clob x) throws SQLException
    {
        throwNYI();
    }

    public void updateClob(String columnName, Clob x) throws SQLException
    {
        throwNYI();
    }

    public void updateArray(int columnIndex, Array x) throws SQLException
    {
        throwNYI();
    }

    public void updateArray(String columnName, Array x) throws SQLException
    {
        throwNYI();
    }

    //
    // Table.TableResultSet
    //

    public boolean isComplete()
    {
        return _isComplete;
    }

    public String getTruncationMessage(int maxRows)
    {
        return "Displaying only the first " + maxRows + " rows.";
    }

//    @Override
    public boolean supportsGetRowMap()
    {
        return true;
    }

    public Map<String, Object> getRowMap() throws SQLException
    {
        if (_row >= _maps.length)
            throw new SQLException("No current row");
        return _maps[_row];
    }


    public Iterator<Map<String, Object>> iterator()
    {
        Iterator<Map<String, Object>> it = IteratorUtils.arrayIterator(_maps);
        return IteratorUtils.unmodifiableIterator(it);
    }

    public int getSize()
    {
        return _maps.length;
    }

    //
    // helpers
    //

    private static Object throwNYI() throws SQLException
    {
        throw new SQLException("NYI");
    }


    private static Object throwConversionError() throws SQLException
    {
        throw new SQLException("Conversion error");
    }


    // The following methods are "implemented" to allow compiling and running on JDK/JRE 6.0 while still supporting
    // JDK/JRE 5.0.  If/when we require JDK/JRE 6.0, these methods should delegate to the wrapped resultset.


    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public int getHoldability() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public Reader getNCharacterStream(int columnIndex) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public Reader getNCharacterStream(String columnLabel) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public NClob getNClob(int columnIndex) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public NClob getNClob(String columnLabel) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public String getNString(int columnIndex) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public String getNString(String columnLabel) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public RowId getRowId(int columnIndex) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public RowId getRowId(String columnLabel) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public SQLXML getSQLXML(int columnIndex) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public SQLXML getSQLXML(String columnLabel) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public boolean isClosed() throws SQLException
    {
        return _wasClosed;
    }

    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateClob(int columnIndex, Reader reader) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateClob(String columnLabel, Reader reader) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNClob(int columnIndex, NClob nClob) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNClob(int columnIndex, Reader reader) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNClob(String columnLabel, NClob nClob) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNClob(String columnLabel, Reader reader) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNString(int columnIndex, String nString) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateNString(String columnLabel, String nString) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateRowId(int columnIndex, RowId x) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateRowId(String columnLabel, RowId x) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    // JDBC 4.1 methods below must be here so we compile on JDK 7; implement once we require JRE 7.

    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    private class DataIteratorAdapter implements DataIterator
    {
        @Override
        public int getColumnCount()
        {
            return getColumnCount();
        }

        @Override
        public ColumnInfo getColumnInfo(int i)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public Object get(int i)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean isScrollable()
        {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void beforeFirst()
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void close() throws IOException
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}
