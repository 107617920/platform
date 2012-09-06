/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.reports;

import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import org.rosuda.REngine.Rserve.RConnection;

//
// This object and its held RConnection will outlive the underlying ScriptEngine
//
public class RConnectionHolder implements HttpSessionBindingListener
{
    RConnection _connection;
    boolean _inUse;

    public RConnectionHolder()  {}

    protected void finalize()
    {
        close();
    }

    public void valueBound(HttpSessionBindingEvent httpSessionBindingEvent)
    {
        // Do nothing
    }

    public void valueUnbound(HttpSessionBindingEvent httpSessionBindingEvent)
    {
        close();
    }

    public RConnection getConnection()
    {
        return _connection;
    }

    public void setConnection(RConnection connection)
    {
        if (_connection != connection)
        {
            close();
            _connection = connection;
        }
    }

    public boolean isInUse()
    {
        return _inUse;
    }

    public void setInUse(boolean value)
    {
        _inUse = value;
    }

    private void close()
    {
        if (_connection != null)
        {
            if (_connection.isConnected())
            {
                _connection.close();
            }
            _connection = null;
        }
    }
}