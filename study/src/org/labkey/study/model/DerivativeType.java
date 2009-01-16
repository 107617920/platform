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
package org.labkey.study.model;

import org.labkey.api.data.Container;/*
 * User: brittp
 * Date: Dec 18, 2008
 * Time: 12:48:03 PM
 */

public class DerivativeType extends AbstractStudyCachable<DerivativeType>
{
    private long _rowId; // serial NOT NULL,
    private Container _container; // entityid NOT NULL,
    private String _ldmsDerivativeCode; // character varying(5),
    private String _labwareDerivativeCode; // character varying(5),
    private String _derivative; // character varying(100),

    public Object getPrimaryKey()
    {
        return _rowId;
    }

    public long getRowId()
    {
        return _rowId;
    }

    public void setRowId(long rowId)
    {
        _rowId = rowId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getLdmsDerivativeCode()
    {
        return _ldmsDerivativeCode;
    }

    public void setLdmsDerivativeCode(String ldmsDerivativeCode)
    {
        _ldmsDerivativeCode = ldmsDerivativeCode;
    }

    public String getLabwareDerivativeCode()
    {
        return _labwareDerivativeCode;
    }

    public void setLabwareDerivativeCode(String labwareDerivativeCode)
    {
        _labwareDerivativeCode = labwareDerivativeCode;
    }

    public String getDerivative()
    {
        return _derivative;
    }

    public void setDerivative(String derivative)
    {
        _derivative = derivative;
    }
}