/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.ScrollableDataIterator;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-09-09
 * Time: 4:21 PM
 */
public abstract class WrapperDataIterator implements DataIterator, ScrollableDataIterator
{
    protected final DataIterator _delegate;
    protected String _debugName;

    protected WrapperDataIterator(DataIterator di)
    {
        _delegate = di;
    }

    public void setDebugName(String name)
    {
        _debugName = name;
    }

    @Override
    public String getDebugName()
    {
        return StringUtils.defaultString(_debugName, getClass().getSimpleName());
    }

    @Override
    public int getColumnCount()
    {
        return _delegate.getColumnCount();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _delegate.getColumnInfo(i);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        return _delegate.next();
    }

    @Override
    public Object get(int i)
    {
        return _delegate.get(i);
    }

    @Override
    public boolean isScrollable()
    {
        return _delegate instanceof ScrollableDataIterator && ((ScrollableDataIterator)_delegate).isScrollable();
    }

    @Override
    public void beforeFirst()
    {
        ((ScrollableDataIterator)_delegate).beforeFirst();
    }

    @Override
    public void close() throws IOException
    {
        _delegate.close();
    }
}
