/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.experiment.api;

import java.util.Date;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URI;

/**
 * User: migra
 * Date: Jun 14, 2005
 * Time: 3:12:17 PM
 */
public class Data extends ProtocolOutput
{
    private int rowId;
    private String _cpasType = "Data";
    private Integer sourceApplicationId;
    private String dataFileUrl;
    private Integer runId;
    private Date created;
    private String sourceProtocolLSID;
    private String container;
    public Data()
    {
    }

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getCpasType()
    {
        return _cpasType;
    }

    public void setCpasType(String cpasType)
    {
        _cpasType = cpasType;
    }

    public Integer getSourceApplicationId()
    {
        return sourceApplicationId;
    }

    public void setSourceApplicationId(Integer sourceApplicationId)
    {
        this.sourceApplicationId = sourceApplicationId;
    }

    public String getDataFileUrl()
    {
        return dataFileUrl;
    }

    public void setDataFileUrl(String dataFileUrl)
    {
        this.dataFileUrl = dataFileUrl;
    }

    public Integer getRunId()
    {
        return runId;
    }

    public void setRunId(Integer runId)
    {
        this.runId = runId;
    }

    public Date getCreated()
    {
        return created;
    }

    public void setCreated(Date created)
    {
        this.created = created;
    }

    public String getSourceProtocolLSID()
    {
        return this.sourceProtocolLSID;
    }

    public void setSourceProtocolLSID(String s)
    {
        this.sourceProtocolLSID = s;
    }

    public void setContainer(String parent)
    {
        this.container = parent;
    }

    public String getContainer()
    {
        return container;
    }

    public File getFile()
    {
        if (getDataFileUrl() == null)
        {
            return null;
        }
        
        try
        {
            return new File(new URI(getDataFileUrl()));
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Data data = (Data) o;

        return !(rowId == 0 || rowId != data.rowId);
    }

    public int hashCode()
    {
        return rowId;
    }
}
