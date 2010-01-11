/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.search.model;

import org.labkey.api.search.SearchService;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;

public abstract class AbstractIndexTask implements SearchService.IndexTask
{
    final private String _description;
    protected long _start;
    protected long _complete = 0;
    protected boolean _cancelled = false;
    protected boolean _isReady = false;
    final private AtomicInteger _estimate = new AtomicInteger();
    final private AtomicInteger _indexed = new AtomicInteger();
    final private AtomicInteger _failed = new AtomicInteger();
    final protected Map<Object,Object> _subtasks = Collections.synchronizedMap(new IdentityHashMap<Object,Object>());
    final StringWriter _sw = new StringWriter();
    final PrintWriter _out = new PrintWriter(_sw);

    final Object _completeEvent = new Object(){public String toString() {return "complete event";}};

    
    public AbstractIndexTask(String description)
    {
        _description = description;
        _start = System.currentTimeMillis();
    }


    public String getDescription()
    {
        return _description;
    }


    public int getDocumentCountEstimate()
    {
        return _estimate.get();
    }


    public int getIndexedCount()
    {
        return _indexed.get();
    }


    public int getFailedCount()
    {
        return _failed.get();
    }


    public long getStartTime()
    {
        return _start;
    }


    public long getCompleteTime()
    {
        return _complete;
    }


    protected void addItem(Object item)
    {
        _subtasks.put(item,item);
    }


    public void log(String message)
    {
        synchronized (_sw)
        {
            _out.println(message);
        }
    }


    public Reader getLog()
    {
        synchronized (_sw)
        {
            return new StringReader(_sw.getBuffer().toString());
        }
    }


    public void addToEstimate(int i)
    {
        _estimate.addAndGet(i);
    }


    // indicates that caller is done adding Resources to this task
    public void setReady()
    {
        _isReady = true;
        if (_subtasks.isEmpty())
            checkDone();
    }


    protected void completeItem(Object item, boolean success)
    {
        if (_cancelled)
            return;
        if (success)
            _indexed.incrementAndGet();
        else
            _failed.incrementAndGet();
        boolean empty;
        synchronized (_subtasks)
        {
            Object remove =  _subtasks.remove(item);
            assert null == remove || remove == item;
            empty = _subtasks.isEmpty();
        }
        if (empty && checkDone())
        {
            _complete = System.currentTimeMillis();
            synchronized (_completeEvent)
            {
                _completeEvent.notifyAll();
            }
        }
    }

    public boolean isCancelled()
    {
        return _cancelled;
    }

    public boolean cancel(boolean mayInterruptIfRunning)
    {
        _cancelled = true;
        return true;
    }

    public boolean isDone()
    {
        return _complete != 0 || _cancelled;
    }

    public SearchService.IndexTask get() throws InterruptedException, ExecutionException
    {
        _completeEvent.wait();
        return this;
    }

    public SearchService.IndexTask get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        _completeEvent.wait(unit.toMillis(timeout));
        return this;
    }

    protected abstract boolean checkDone();
}