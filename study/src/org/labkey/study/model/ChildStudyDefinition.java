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
package org.labkey.study.model;

/**
 * User: klum
 * Date: Aug 25, 2011
 * Time: 3:51:31 PM
 */
public class ChildStudyDefinition
{
    private String _name;
    private String _description;
    private String _srcPath;
    private String _dstPath;
    private boolean _removeProtectedColumns;
    private boolean _shiftDates;
    private boolean _useAlternateParticipantIds;
    private boolean _includeSpecimens;
    private boolean _specimenRefresh = false;
    private Integer[] _visits;
    private Integer[] _lists;
    private String[] _views;
    private String[] _reports;
    private int[] _datasets = new int[0];
    private int _updateDelay;

    private int[] _groups = new int[0];
    private boolean _copyParticipantGroups;
    private boolean _publish;

    private Integer _requestId;  // RowId of a specimen request
    private Specimen[] _specimens = null;

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getSrcPath()
    {
        return _srcPath;
    }

    public void setSrcPath(String srcPath)
    {
        _srcPath = srcPath;
    }

    public String getDstPath()
    {
        return _dstPath;
    }

    public void setDstPath(String dstPath)
    {
        _dstPath = dstPath;
    }

    public int[] getDatasets()
    {
        return _datasets;
    }

    public void setDatasets(int[] datasets)
    {
        _datasets = datasets;
    }

    public int[] getGroups()
    {
        return _groups;
    }

    public void setGroups(int[] groups)
    {
        _groups = groups;
    }

    public int getUpdateDelay()
    {
        return _updateDelay;
    }

    public void setUpdateDelay(int updateDelay)
    {
        _updateDelay = updateDelay;
    }

    public boolean isCopyParticipantGroups()
    {
        return _copyParticipantGroups;
    }

    public void setCopyParticipantGroups(boolean copyParticipantGroups)
    {
        _copyParticipantGroups = copyParticipantGroups;
    }

    public boolean isRemoveProtectedColumns(){
        return _removeProtectedColumns;
    }

    public void setRemoveProtectedColumns(boolean removeProtectedColumns){
        _removeProtectedColumns = removeProtectedColumns;
    }

    public boolean isShiftDates(){
        return _shiftDates;
    }

    public void setShiftDates(boolean shiftDates){
        _shiftDates = shiftDates;
    }

    public boolean isUseAlternateParticipantIds(){
        return _useAlternateParticipantIds;
    }

    public void setUseAlternateParticipantIds(boolean useAlternateParticipantIds){
        _useAlternateParticipantIds = useAlternateParticipantIds;
    }

    public Integer[] getVisits(){
        return _visits;
    }

    public void setVisits(Integer[] visits){
        _visits = visits;
    }

    public Integer[] getLists(){
        return _lists;
    }

    public void setLists(Integer[] lists){
        _lists = lists;
    }

    public String[] getReports(){
        return _reports;
    }

    public void setReports(String[] views){
        _reports = views;
    }

    public String[] getViews(){
        return _views;
    }

    public void setViews(String[] views){
        _views = views;
    }

    public boolean isPublish()
    {
        return _publish;
    }

    public void setPublish(boolean publish)
    {
        _publish = publish;
    }

    public boolean isIncludeSpecimens()
    {
        return _includeSpecimens;
    }

    public void setIncludeSpecimens(boolean includeSpecimens)
    {
        _includeSpecimens = includeSpecimens;
    }

    public boolean isSpecimenRefresh()
    {
        return _specimenRefresh;
    }

    public void setSpecimenRefresh(boolean specimenRefresh)
    {
        _specimenRefresh = specimenRefresh;
    }

    public Integer getRequestId()
    {
        return _requestId;
    }

    public void setRequestId(Integer requestId)
    {
        _requestId = requestId;
    }

    public Specimen[] getSpecimens()
    {
        return _specimens;
    }

    public void setSpecimens(Specimen[] specimens)
    {
        _specimens = specimens;
    }
}
