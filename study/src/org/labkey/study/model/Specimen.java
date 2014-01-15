/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.study.StudyService;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: brittp
 * Date: Mar 15, 2006
 * Time: 4:26:07 PM
 */
public class Specimen extends AbstractStudyCachable<Specimen>
{
    private Map _rowMap;

    public Specimen()
    {
        _rowMap = new HashMap<String, Object>();
    }

    public Specimen(Map rowMap)
    {
        _rowMap = rowMap;
    }

    public Object get(String key)
    {
        return _rowMap.get(key);
    }

    public Map getRowMap()
    {
        return _rowMap;
    }

    public Integer getAdditiveTypeId()
    {
        return (Integer)get("additivetypeid");
    }

    public long getSpecimenId()
    {
        Long specimenId = (Long)get("specimenid");
        if (null != specimenId)
            return specimenId;
        return 0;
    }

    public Container getContainer()
    {
        return ContainerManager.getForId((String)get("container"));
    }

    public void setContainer(Container container)
    {
        verifyMutability();
        _rowMap.put("container", container.getId());
    }

    public Integer getDerivativeTypeId()
    {
        return (Integer)get("derivativetypeid");
    }

    public Date getDrawTimestamp()
    {
        return (Date)get("drawtimestamp");
    }

    public String getGlobalUniqueId()
    {
        return (String)get("globaluniqueid");
    }

    public Integer getPrimaryTypeId()
    {
        return (Integer)get("primarytypeid");
    }

    public String getPtid()
    {
        return (String)get("ptid");
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public long getRowId()
    {
        Long rowId = (Long)get("rowid");
        if (null != rowId)
            return rowId;
        return 0;
    }

    public void setRowId(long rowId)
    {
        verifyMutability();
        _rowMap.put("rowid", rowId);
    }

    public String getSubAdditiveDerivative()
    {
        return (String)get("subAdditiveDerivative");
    }

    public String getVisitDescription()
    {
        return (String)get("visitDescription");
    }

    public Double getVisitValue()
    {
        return (Double)get("visitvalue");
    }

    public Double getVolume()
    {
        return (Double)get("volume");
    }

    public void setVolume(Double volume)
    {
        verifyMutability();
        _rowMap.put("volume", volume);
    }

    public String getVolumeUnits()
    {
        return (String)get("volumeunits");
    }

    public Integer getOriginatingLocationId()
    {
        return (Integer)get("originatinglocationid");
    }

    public Integer getCurrentLocation()
    {
        return (Integer)get("currentlocation");
    }

    public String getSampleDescription()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Global ID ").append(getGlobalUniqueId());
        builder.append(", ").append(StudyService.get().getSubjectNounSingular(getContainer())).append(" ").append(getPtid());
        builder.append(", ").append(getVisitDescription()).append(" ").append(getVisitValue());
        return builder.toString();
    }

    public String getSpecimenHash()
    {
        return (String)get("specimenhash");
    }

    public Integer getProcessingLocation()
    {
        return (Integer)get("processinglocation");
    }

    public boolean isAtRepository()
    {
        Boolean available = (Boolean)get("atrepository");
        if (null != available)
            return available;
        return false;
    }

    public boolean isAvailable()
    {
        Boolean available = (Boolean)get("available");
        if (null != available)
            return available;
        return false;
    }

    public void setAvailable(boolean available)
    {
        _rowMap.put("available", available);
    }

    public void setLockedInRequest(boolean lockedInRequest)
    {
        _rowMap.put("lockedinrequest", lockedInRequest);
    }

    public void setRequestable(Boolean requestable)
    {
        _rowMap.put("requestable", requestable);
    }

    public String getFirstProcessedByInitials()
    {
        return (String)get("firstprocessedbyinitials");
    }

    public String getAvailabilityReason()
    {
        return (String)get("availabilityreason");
    }

    public String getLatestComments()
    {
        return (String)get("latestcomments");
    }

    public String getLatestQualityComments()
    {
        return (String)get("latestqualitycomments");
    }
}
