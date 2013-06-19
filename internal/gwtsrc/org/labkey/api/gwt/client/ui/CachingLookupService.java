/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* User: matthewb
* Date: Apr 27, 2010
* Time: 9:28:41 AM
*/
public class CachingLookupService implements LookupServiceAsync
{
    final LookupServiceAsync _impl;

    public CachingLookupService(LookupServiceAsync i)
    {
        _impl = i;
    }


    List<String> _containers = null;

    public void getContainers(final AsyncCallback<List<String>> async)
    {
        if (null != _containers)
        {
            async.onSuccess(_containers);
            return;
        }
        _impl.getContainers(new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                async.onFailure(caught);
            }

            public void onSuccess(List<String> result)
            {
                _containers = result;
                async.onSuccess(result);
            }
        });
    }


    Map<String,List<String>> schemas = new HashMap<String,List<String>>();

    public void getSchemas(final String containerId, final AsyncCallback<List<String>> async)
    {
        List<String> result = schemas.get(containerId);
        if (null != result)
        {
            async.onSuccess(result);
            return;
        }
        _impl.getSchemas(containerId, new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                async.onFailure(caught);
            }

            public void onSuccess(List<String> result)
            {
                schemas.put(containerId, result);
                async.onSuccess(result);
            }
        });
    }


    Map<String,Map<String, GWTPropertyDescriptor>> tables = new HashMap<String,Map<String, GWTPropertyDescriptor>>();

    public void getTablesForLookup(final String containerId, final String schemaName, final AsyncCallback<Map<String, GWTPropertyDescriptor>> async)
    {
        Map<String, GWTPropertyDescriptor> result = tables.get(containerId + "||" + schemaName);
        if (null != result)
        {
            async.onSuccess(result);
            return;
        }
        _impl.getTablesForLookup(containerId, schemaName, new AsyncCallback<Map<String,GWTPropertyDescriptor> >()
        {
            public void onFailure(Throwable caught)
            {
                async.onFailure(caught);
            }

            public void onSuccess(Map<String,GWTPropertyDescriptor> result)
            {
                tables.put(containerId + "||" + schemaName, result);
                async.onSuccess(result);
            }
        });
    }


    public Map<String, GWTPropertyDescriptor> getTablesForLookupCached(final String containerId, final String schemaName)
    {                                                                
        Map<String, GWTPropertyDescriptor> result = tables.get(containerId + "||" + schemaName);
        return result;
    }
}
