/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.study.importer;

import org.apache.commons.lang.ArrayUtils;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.study.Visit;
import org.labkey.study.model.VisitImpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.StringTokenizer;


/**
 * User: brittp
 * Date: Jan 7, 2006
 * Time: 3:11:37 PM
 */
class VisitMapRecord
{
    private int _visitRowId = -1;

    private final double _sequenceNumberMin;
    private final double _sequenceNumberMax;
    private final boolean _showByDefault;
    private final Visit.Type _visitType;
    private final String _visitLabel;
    private final int _visitDatePlate;
    private final String _visitDateField;
    private final int _visitDueDay;
    private final int _visitOverdueAllowance;
    private final int[] _requiredPlates;
    private final int[] _optionalPlates;
    private final int _missedNotificationPlate;
    private final String _terminationWindow;
    private final String _cohort;
    private final int _displayOrder;
    private final int _chronologicalOrder;

    public VisitMapRecord(double sequenceNumberMin, double sequenceNumberMax, String visitType, String visitLabel,
                          String cohort, int visitDatePlate, int[] requiredPlates, int[] optionalPlates, boolean showByDefault,
                          int displayOrder, int chronologicalOrder)
    {
        _sequenceNumberMin = sequenceNumberMin;
        _sequenceNumberMax = sequenceNumberMax;
        _visitType = getType(visitType);
        _visitLabel = visitLabel;
        _cohort = cohort;
        _visitDatePlate = visitDatePlate;
        _requiredPlates = requiredPlates;
        _optionalPlates = optionalPlates;
        _showByDefault = showByDefault;
        _displayOrder = displayOrder;
        _chronologicalOrder = chronologicalOrder;

        // These are not currently used
        _visitDateField = null;
        _visitDueDay =  -1;
        _visitOverdueAllowance = -1;
        _missedNotificationPlate = -1;
        _terminationWindow = null;
    }

    private VisitMapRecord(Map record)
    {
        String range = (String)record.get("sequenceRange");
        if (null == range)
            throw new IllegalArgumentException("Sequence range is required");

        String split[] = range.split("[\\-\\~]");
        _sequenceNumberMin = VisitImpl.parseSequenceNum(split[0]);
        if (split.length > 1)
            _sequenceNumberMax = VisitImpl.parseSequenceNum(split[1]);
        else
            _sequenceNumberMax = _sequenceNumberMin;

        _visitType = getType((String)record.get("visitType"));
        _visitLabel = (String)record.get("visitLabel");
        _cohort = (String)record.get("cohort");
        _visitDatePlate = defaultInt((Integer)record.get("visitDatePlate"), -1);
        _visitDateField = (String)record.get("visitDateField");
        _visitDueDay =  defaultInt((Integer)record.get("visitDueDay"), -1);
        _visitOverdueAllowance = defaultInt((Integer)record.get("visitDueAllowance"), -1);
        _requiredPlates = toIntArray((String) record.get("requiredPlates"));
        _optionalPlates = toIntArray((String) record.get("optionalPlates"));
        _missedNotificationPlate = defaultInt((Integer)record.get("missedNotificationPlate"), -1);
        _terminationWindow = (String)record.get("terminationWindow");
        _showByDefault = true;

        _displayOrder = defaultInt((Integer)record.get("displayOrder"), 0);
        _chronologicalOrder = defaultInt((Integer)record.get("chronologicalOrder"), 0);
    }

    private static int defaultInt(Integer i, int defaultInt)
    {
        return null == i ? defaultInt : i;
    }


    public double getSequenceNumMin()           { return _sequenceNumberMin; }
    public double getSequenceNumMax()           { return _sequenceNumberMax; }
    public int getMissedNotificationPlate() { return _missedNotificationPlate; }
    public int[] getOptionalPlates()        { return _optionalPlates; }
    public int[] getRequiredPlates()        { return _requiredPlates; }
    public String getTerminationWindow()    { return _terminationWindow; }
    public String getVisitDateField()       { return _visitDateField; }
    public int getVisitDatePlate()          { return _visitDatePlate; }
    public int getVisitDueDay()             { return _visitDueDay; }
    public String getVisitLabel()           { return _visitLabel; }
    public int getVisitOverdueAllowance()   { return _visitOverdueAllowance; }
    public Visit.Type getVisitType()        { return _visitType; }
    public String getCohort()               { return _cohort; }
    public boolean isShowByDefault()        { return _showByDefault; }
    public int getDisplayOrder()            { return _displayOrder; }
    public int getChronologicalOrder()      { return _chronologicalOrder; }

/*    private int toInt(String str, int defaultValue)
    {
        if (str.length() == 0)
            return defaultValue;
        try
        {
            return Integer.parseInt(str.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    } */

    private int toInt(String str)
    {
        return Integer.parseInt(str.trim());
    }

    private static final int[] emptyIntArray = new int[0];

    private int[] toIntArray(String list)
    {
        if (null == list)
            return emptyIntArray;

        StringTokenizer st = new StringTokenizer(list, ", \t;");
        ArrayList<Integer> values = new ArrayList<Integer>(st.countTokens());

        while (st.hasMoreTokens())
        {
            String s = st.nextToken();
            int index = s.indexOf('~');
            if (index == -1)
            {
                values.add(toInt(s));
            }
            else
            {
                int a = toInt(s.substring(0,index));
                int b = toInt(s.substring(index+1));
                for (int i=a ; i<=b ; i++)
                    values.add(i);
            }
        }

        return ArrayUtils.toPrimitive(values.toArray(new Integer[values.size()]));
    }

    private Visit.Type getType(String str)
    {
        if (null == str)
            return null;

        str = str.trim();
        if (str.length() != 1)
            return null;
        else
            return Visit.Type.getByCode(str.charAt(0));
    }


    // set by visit importer
    public void setVisitRowId(int rowId)
    {
        _visitRowId = rowId;
    }

    public int getVisitRowId()
    {
        return _visitRowId;
    }


    static
    {
        ObjectFactory.Registry.register(VisitMapRecord.class, new VisitMapRecordFactory());
    }

    // UNDONE: should have BaseObjectFactory to implement handle in terms of fromMap()
    private static class VisitMapRecordFactory implements ObjectFactory<VisitMapRecord>
    {
        public VisitMapRecord fromMap(Map<String, ?> m)
        {
            return new VisitMapRecord(m);
        }

        public Map<String, Object> toMap(VisitMapRecord bean, Map m)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        public VisitMapRecord[] handleArray(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }

        public VisitMapRecord handle(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }
    }
}
