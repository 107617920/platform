/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.SimpleFilter.ColumnNameFormatter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.*;
import org.labkey.api.util.DateUtil;
import org.labkey.data.xml.queryCustomView.OperatorType;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * User: brittp
 * Date: Oct 10, 2006
 * Time: 4:44:45 PM
 */
public enum CompareType
{
    EQUAL("Equals", "eq", true, " = ?", "EQUAL", OperatorType.EQ)
        {
            @Override
            FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new EqualsCompareClause(fieldKey, this, value);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                if (value == null)
                {
                    return filterValues[0] == null;
                }
                // First try with no type conversion
                if (value.equals(filterValues[0]))
                {
                    return true;
                }
                // Then try converting to the same type
                return value.equals(convert(filterValues[0], value.getClass()));
            }
        },
    DATE_EQUAL("(Date) Equals", "dateeq", true, null, "DATE_EQUAL", OperatorType.DATEEQ)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new DateEqCompareClause(fieldKey, toDatePart(asDate(value)));
            }
        },
    DATE_NOT_EQUAL("(Date) Does Not Equal", "dateneq", true, null, "DATE_NOT_EQUAL", OperatorType.DATENEQ)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new DateNeqCompareClause(fieldKey, toDatePart(asDate(value)));
            }
        },
    NEQ_OR_NULL("Does Not Equal", "neqornull", true, " <> ?", "NOT_EQUAL_OR_MISSING", OperatorType.NEQORNULL)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new NotEqualOrNullClause(fieldKey, value);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                return value == null || !CompareType.EQUAL.meetsCriteria(value, filterValues);
            }
        },
    NEQ("Does Not Equal", "neq", true, " <> ?", "NOT_EQUAL", OperatorType.NEQ)
        {
            @Override
            FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new NotEqualsCompareClause(fieldKey, this, value);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                return !CompareType.EQUAL.meetsCriteria(value, filterValues);
            }
        },
    ISBLANK("Is Blank", "isblank", false, " IS NULL", "MISSING", OperatorType.ISBLANK)
        {
            public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return super.createFilterClause(fieldKey, null);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                return value == null;
            }
        },
    NONBLANK("Is Not Blank", "isnonblank", false, " IS NOT NULL", "NOT_MISSING", OperatorType.ISNONBLANK)
        {
            public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return super.createFilterClause(fieldKey, null);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                return value != null;
            }
        },
    DATE_GT("(Date) Is Greater Than", "dategt", true, " >= ?", "DATE_GREATER_THAN", OperatorType.GTE) // GT --> >= roundup(date)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new DateGtCompareClause(fieldKey, toDatePart(asDate(value)));
            }
        },
    GT("Is Greater Than", "gt", true, " > ?", "GREATER_THAN", OperatorType.GT)
        {
            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                if (value == null || !(value instanceof Comparable))
                {
                    return false;
                }
                Object filterValue = convert(filterValues[0], value.getClass());
                if (filterValue == null)
                {
                    return false;
                }
                return ((Comparable)value).compareTo(filterValue) > 0;
            }
        },
    DATE_LT("(Date) Is Less Than", "datelt", true, " < ?", "DATE_LESS_THAN", OperatorType.LT)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new DateLtCompareClause(fieldKey, toDatePart(asDate(value)));
            }
        },
    LT("Is Less Than", "lt", true, " < ?", "LESS_THAN", OperatorType.LT)
        {
            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                if (value == null || !(value instanceof Comparable))
                {
                    return false;
                }
                Object filterValue = convert(filterValues[0], value.getClass());
                if (filterValue == null)
                {
                    return false;
                }
                return ((Comparable)value).compareTo(filterValue) < 0;
            }
        },
    DATE_GTE("(Date) Is Greater Than or Equal To", "dategte", true, " >= ?", "DATE_GREATER_THAN_OR_EQUAL", OperatorType.GTE)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new DateGteCompareClause(fieldKey, toDatePart(asDate(value)));
            }
        },
    GTE("Is Greater Than or Equal To", "gte", true, " >= ?", "GREATER_THAN_OR_EQUAL", OperatorType.GTE)
        {
            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                if (value == null || !(value instanceof Comparable))
                {
                    return false;
                }
                Object filterValue = convert(filterValues[0], value.getClass());
                if (filterValue == null)
                {
                    return false;
                }
                return ((Comparable)value).compareTo(filterValue) >= 0;
            }
        },
    DATE_LTE("(Date) Is Less Than or Equal To", "datelte", true, " < ?", "DATE_LESS_THAN_OR_EQUAL", OperatorType.LT)  // LTE --> < roundup(date)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new DateLteCompareClause(fieldKey, toDatePart(asDate(value)));
            }
        },
    LTE("Is Less Than or Equal To", "lte", true, " <= ?", "LESS_THAN_OR_EQUAL", OperatorType.LTE)
        {
            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                if (value == null || !(value instanceof Comparable))
                {
                    return false;
                }
                Object filterValue = convert(filterValues[0], value.getClass());
                if (filterValue == null)
                {
                    return false;
                }
                return ((Comparable)value).compareTo(filterValue) <= 0;
            }
        },
    CONTAINS("Contains", "contains", true, null, "CONTAINS", OperatorType.CONTAINS)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new ContainsClause(fieldKey, value);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                return value != null && value.toString().indexOf((String)filterValues[0]) != -1;
            }
        },
    DOES_NOT_CONTAIN("Does Not Contain", "doesnotcontain", true, null, "DOES_NOT_CONTAIN", OperatorType.DOESNOTCONTAIN)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new DoesNotContainClause(fieldKey, value);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                return value == null || value.toString().indexOf((String)filterValues[0]) == -1;
            }
        },
    DOES_NOT_START_WITH("Does Not Start With", "doesnotstartwith", true, null, "DOES_NOT_START_WITH", OperatorType.DOESNOTSTARTWITH)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new DoesNotStartWithClause(fieldKey, value);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                return value == null || !value.toString().startsWith((String)filterValues[0]);
            }
        },
    STARTS_WITH("Starts With", "startswith", true, null, "STARTS_WITH", OperatorType.STARTSWITH)
        {
            public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new StartsWithClause(fieldKey, value);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] filterValues)
            {
                return value != null && value.toString().startsWith((String)filterValues[0]);
            }
        },
    IN("Equals One Of (e.g. 'a;b;c')", "in", true, null, "IN", OperatorType.IN)
        {
            // Each compare type uses CompareClause by default
            FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                if (value instanceof Collection)
                {
                    return new SimpleFilter.InClause(fieldKey, (Collection<?>)value, false);
                }
                else
                {
                    List<String> values = new ArrayList<>();
                    if (value != null)
                    {
                        if (value.toString().trim().equals(""))
                        {
                            values.add(null);
                        }
                        else
                        {
                            values.addAll(parseParams(value));
                        }
                    }
                    return new SimpleFilter.InClause(fieldKey, values, true);
                }
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] paramVals)
            {
                throw new UnsupportedOperationException("Should be handled inside of " + SimpleFilter.InClause.class);
            }
        },
    NOT_IN("Does Not Equal Any Of (e.g. 'a;b;c')", "notin", true, null, "NOT_IN", OperatorType.NOTIN)
        {
            // Each compare type uses CompareClause by default
            FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                if (value instanceof Collection)
                {
                    return new SimpleFilter.InClause(fieldKey, (Collection)value, false, true);
                }
                else
                {
                    List<String> values = new ArrayList<>();
                    if (value != null)
                    {
                        if (value.toString().trim().equals(""))
                        {
                            values.add(null);
                        }
                        else
                        {
                            values.addAll(parseParams(value));
                        }
                    }
                    return new SimpleFilter.InClause(fieldKey, values, true, true);
                }
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] paramVals)
            {
                throw new UnsupportedOperationException("Should be handled inside of " + SimpleFilter.InClause.class);
            }
        },
    CONTAINS_ONE_OF("Contains One Of (e.g. 'a;b;c')", "containsoneof", true, null, "CONTAINS_ONE_OF", OperatorType.CONTAINSONEOF)
        {
            // Each compare type uses CompareClause by default
            FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                if (value instanceof Collection)
                {
                    return new SimpleFilter.ContainsOneOfClause(fieldKey, (Collection)value, false);
                }
                else
                {
                    List<String> values = new ArrayList<>();
                    if (value != null && !value.toString().trim().equals(""))
                    {
                        values.addAll(parseParams(value));
                    }
                    return new SimpleFilter.ContainsOneOfClause(fieldKey, values, true, false);
                }
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] paramVals)
            {
                throw new UnsupportedOperationException("Should be handled inside of " + SimpleFilter.ContainsOneOfClause.class);
            }
        },
    CONTAINS_NONE_OF("Does Not Contain Any Of (e.g. 'a;b;c')", "containsnoneof", true, null, "CONTAINS_NONE_OF", OperatorType.CONTAINSNONEOF)
        {
            // Each compare type uses CompareClause by default
            FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                if (value instanceof Collection)
                {
                    return new SimpleFilter.ContainsOneOfClause(fieldKey, (Collection)value, false, true);
                }
                else
                {
                    Set<String> values = parseParams(value);

                    return new SimpleFilter.ContainsOneOfClause(fieldKey, values, false, true);
                }
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] paramVals)
            {
                throw new UnsupportedOperationException("Should be handled inside of " + SimpleFilter.ContainsOneOfClause.class);
            }
        },
    HAS_QC("Has An MV Indicator", new String[] { "hasmvvalue", "hasqcvalue" }, false, " has a missing value indicator", "MV_INDICATOR", OperatorType.HASMVVALUE)
        {
            @Override
            QcClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new QcClause(fieldKey, false);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] paramVals)
            {
                throw new UnsupportedOperationException("Conditional formatting not yet supported for MV indicators");
            }
        },
    NO_QC("Does Not Have An MV Indicator", new String[] { "nomvvalue", "noqcvalue" }, false, " does not have a missing value indicator", "NO_MV_INDICATOR", OperatorType.NOMVVALUE)
        {
            @Override
            QcClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new QcClause(fieldKey, true);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] paramVals)
            {
                throw new UnsupportedOperationException("Conditional formatting not yet supported for MV indicators");
            }
        },
    MEMBER_OF("Is Member Of", "memberof", true, " does not have a missing value indicator", "MEMBER_OF", OperatorType.MEMBEROF)
        {
            @Override
            MemberOfClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
            {
                return new MemberOfClause(fieldKey, value);
            }

            @Override
            public boolean meetsCriteria(Object value, Object[] paramVals)
            {
                throw new UnsupportedOperationException("Conditional formatting not yet supported for MEMBER_OF");
            }
        };


    private String _preferredURLKey;
    private final OperatorType.Enum _xmlType;
    private Set<String> _urlKeys = new CaseInsensitiveHashSet();
    private String _displayValue;
    private boolean _dataValueRequired;
    private String _sql;
    private String _scriptName;

    CompareType(String displayValue, String[] urlKeys, boolean dataValueRequired, String sql, String scriptName, OperatorType.Enum xmlType)
    {
        this(displayValue, urlKeys[0], dataValueRequired, sql, scriptName, xmlType);
        _urlKeys.addAll(Arrays.asList(urlKeys));
    }

    CompareType(String displayValue, String urlKey, boolean dataValueRequired, String sql, String scriptName, OperatorType.Enum xmlType)
    {
        _preferredURLKey = urlKey;
        _xmlType = xmlType;
        _urlKeys.add(urlKey);
        _displayValue = displayValue;
        _dataValueRequired = dataValueRequired;
        _sql = sql;
        _scriptName = scriptName;
    }


    public static List<CompareType> getValidCompareSet(ColumnInfo info)
    {
        List<CompareType> types = new ArrayList<>();

        if (info.isDateTimeType())
        {
            types.add(DATE_EQUAL);
            types.add(DATE_NOT_EQUAL);
        }
        else if (!info.isLongTextType())
        {
            types.add(EQUAL);
            types.add(NEQ);
            types.add(IN);
        }
        
        if (info.isNullable())
        {
            types.add(ISBLANK);
            types.add(NONBLANK);
        }

        if (info.isDateTimeType())
        {
            types.add(DATE_GT);
            types.add(DATE_LT);
            types.add(DATE_GTE);
            types.add(DATE_LTE);
        }
        else if (!info.isLongTextType() && !info.isBooleanType() )
        {
            types.add(GT);
            types.add(LT);
            types.add(GTE);
            types.add(LTE);
        }

        if (!info.isBooleanType() && !info.isDateTimeType())
        {
            types.add(STARTS_WITH);
            types.add(CONTAINS);
        }
        return types;
    }

    private static Set<String> parseParams(Object value)
    {
        Set<String> values = new HashSet<>();
        if (value != null && !value.toString().trim().equals(""))
        {
            String[] st = value.toString().split(";", -1);
            Collections.addAll(values, st);
        }
        return values;
    }

    private static <T> T convert(Object value, Class<T> targetClass)
    {
        if (value == null || targetClass.isInstance(value))
        {
            return (T)value;
        }

        Converter converter = ConvertUtils.lookup(targetClass);
        if (converter != null)
        {
            try
            {
                return (T)converter.convert(targetClass, value);
            }
            catch (ConversionException e) {}
        }
        return null;
    }

    public static CompareType getByURLKey(String urlKey)
    {
        for (CompareType type : values())
        {
            if (type.getUrlKeys().contains(urlKey))
                return type;
        }
        return null;
    }

    public String getDisplayValue()
    {
        return _displayValue;
    }

    public OperatorType.Enum getXmlType()
    {
        return _xmlType;
    }

    public String getPreferredUrlKey()
    {
        return _preferredURLKey;
    }

    public Set<String> getUrlKeys()
    {
        return _urlKeys;
    }

    public boolean isDataValueRequired()
    {
        return _dataValueRequired;
    }

    public String getSql()
    {
        return _sql;
    }

    public String getScriptName()
    {
        return _scriptName;
    }

    public boolean meetsCriteria(Object value, Object[] paramVals)
    {
        // if not implemented, but be implemented by the FilterClause
        throw new UnsupportedOperationException();
    }

    // Each compare type uses CompareClause by default
    FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        return new CompareClause(fieldKey, this, value);
    }

    public static class CompareClause extends FilterClause
    {
        @NotNull
        final FieldKey _fieldKey;
        
        CompareType _comparison;


        public CompareClause(@NotNull FieldKey fieldKey, CompareType comparison, Object value)
        {
            _fieldKey = fieldKey;

            _comparison = comparison;

            if (null == value)
                _paramVals = null;
            else
                _paramVals = new Object[]{value};
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + _comparison.getSql();
        }

        protected String substituteLabKeySqlParams(String sql, Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            JdbcType type = getColumnType(columnMap);
            if (type == null)
                throw new IllegalArgumentException("Column " + _fieldKey.toDisplayString() + " not found in column map.");
            Object[] params = getParamVals();
            if (params == null || params.length == 0)
                return sql;
            String[] parts = sql.split("\\?");
            StringBuilder substituted = new StringBuilder(parts[0]);
            int i;
            for (i = 0; i < params.length; i++)
            {
                substituted.append(escapeLabKeySqlValue(params[i], type));
                if (parts.length > i + 1)
                    substituted.append(parts[i + 1]);
            }
            return substituted.toString();
        }

        protected JdbcType getColumnType(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            return getColumnType(columnMap, null);
        }

        protected JdbcType getColumnType(Map<FieldKey, ? extends ColumnInfo> columnMap, JdbcType defaultType)
        {
            ColumnInfo col = null==columnMap ? null : columnMap.get(_fieldKey);
            return col != null ? col.getJdbcType() : defaultType;
        }

        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String comparisonSql = _comparison.getSql();
            if (comparisonSql == null)
                throw new IllegalStateException("This compare type must override getLabKeySQLWhereClause.");
            String sql = getLabKeySQLColName(_fieldKey) + _comparison.getSql();
            return substituteLabKeySqlParams(sql, columnMap);
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(_comparison.getSql());
        }

        protected boolean isNull(Object value)
        {
            if (value == null)
            {
                return true;
            }
            if (value instanceof Parameter.TypedValue)
            {
                value = ((Parameter.TypedValue)value)._value;
            }
            return value == null || (value instanceof String && ((String)value).length() == 0);
        }

        protected void appendColumnName(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append(formatter.format(_fieldKey));
        }

        @Deprecated // Use getFieldKeys() instead.
        public List<String> getColumnNames()
        {
            return Arrays.asList(_fieldKey.toString());
        }

        public List<FieldKey> getFieldKeys()
        {
            return Arrays.asList(_fieldKey);
        }


        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = columnMap != null ? columnMap.get(_fieldKey) : null;
            String alias = colInfo != null ? colInfo.getAlias() : _fieldKey.getName();

            SQLFragment fragment = new SQLFragment(toWhereClause(dialect, alias));
            if (colInfo == null || !needsTypeConversion() || getParamVals() == null)
            {
                fragment.addAll(getParamVals());
            }
            else
            {
                for (Object paramVal : getParamVals())
                {
                    fragment.add(convertParamValue(colInfo, paramVal));
                }
            }
            return fragment;
        }

        public CompareType getComparison()
        {
            return _comparison;
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            return getComparison().meetsCriteria(value, getParamVals());
        }
    }

    // Converts parameter value to the proper type based on the SQL type of the ColumnInfo
    public static Object convertParamValue(ColumnInfo colInfo, Object paramVal)
    {
        if (colInfo == null)
        {
            // No way to know what to convert it into
            return paramVal;
        }
        if (!(paramVal instanceof String))
            return paramVal;

        switch (colInfo.getSqlTypeInt())
        {
            case Types.INTEGER:
            case Types.TINYINT:
            case Types.SMALLINT:
            {
                // Treat the empty string as null
                String stringValue = (String)paramVal;
                stringValue = StringUtils.trimToNull(stringValue);
                if (stringValue == null)
                {
                    return new Parameter.TypedValue(null, JdbcType.INTEGER);
                }
                try
                {
                    return new Integer(stringValue);
                }
                catch (NumberFormatException e)
                {
                    throw new RuntimeSQLException(new SQLGenerationException("Could not convert '" + paramVal + "' to an integer for " + colInfo));
                }
            }

            case Types.BIGINT:
            {
                try
                {
                    return new Long((String) paramVal);
                }
                catch (NumberFormatException e)
                {
                    throw new RuntimeSQLException(new SQLGenerationException("Could not convert '" + paramVal + "' to a long for " + colInfo));
                }
            }

            case Types.BOOLEAN:
            case Types.BIT:
            {
                try
                {
                    // Treat the empty string as null
                    String stringValue = (String)paramVal;
                    stringValue = StringUtils.trimToNull(stringValue);
                    if (stringValue == null)
                    {
                        return new Parameter.TypedValue(null, JdbcType.BOOLEAN);
                    }
                    return ConvertUtils.convert(stringValue, Boolean.class);
                }
                catch (Exception e)
                {
                    throw new RuntimeSQLException(new SQLGenerationException("Could not convert '" + paramVal + "' to a boolean for " + colInfo));
                }
            }

            case Types.TIMESTAMP:
            case Types.DATE:
            case Types.TIME:
            {
                try
                {
                    return ConvertUtils.convert((String) paramVal, Date.class);
                }
                catch (ConversionException e)
                {
                    throw new RuntimeSQLException(new SQLGenerationException("Could not convert '" + paramVal + "' to a date for " + colInfo));
                }
            }

            //FALL THROUGH! (Decimal is better than nothing)
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.REAL:
            case Types.FLOAT:
            case Types.DOUBLE:
            {
                try
                {
                    // Treat the empty string as null
                    String stringValue = (String)paramVal;
                    stringValue = StringUtils.trimToNull(stringValue);
                    if (stringValue == null)
                    {
                        return new Parameter.TypedValue(null, JdbcType.DOUBLE);
                    }
                    return new Double(stringValue);
                }
                catch (NumberFormatException e)
                {
                    throw new RuntimeSQLException(new SQLGenerationException("Could not convert '" + paramVal + "' to a number for " + colInfo));
                }
            }
        }

        return paramVal;
    }


    public static Date asDate(Object v)
    {
        if (v instanceof Date)
            return (Date)v;
        if (v instanceof Calendar)
            return ((Calendar)v).getTime();
        String s = v.toString();

        if (!s.startsWith("-") && !s.startsWith("+"))
            return new Date(DateUtil.parseDateTime(s));

        boolean add = s.startsWith("+");
        s = s.substring(1);
        if (NumberUtils.isDigits(s))
            s = s + "d";
        if (add)
            return new Date(DateUtil.addDuration(System.currentTimeMillis(), s));
        else
            return new Date(DateUtil.subtractDuration(System.currentTimeMillis(), s));
    }


    public static Calendar toDatePart(Date d)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.clear(Calendar.MINUTE);
        return cal;
    }


    public static Calendar addOneDay(Calendar d)
    {
        assert d.get(Calendar.MILLISECOND) == 0;
        assert d.get(Calendar.SECOND) == 0;
        assert d.get(Calendar.MINUTE) == 0;
        assert d.get(Calendar.HOUR_OF_DAY) == 0;
        
        Calendar cal = (Calendar)d.clone();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal;
    }


    /**
     * Compare clause for date operators
     *
     * Note that for most date operators the logical operation does not match the
     * actual sql operation   EQ --> BETWEEN, LTE --> LT, etc.
     */
    private abstract static class DateCompareClause extends CompareClause
    {
        String _filterTextDate;
        String _filterTextOperator;

        DateCompareClause(FieldKey fieldKey, CompareType t, String op, Calendar date, Calendar param0)
        {
            super(fieldKey, t, param0.getTime());
            _filterTextDate = ConvertUtils.convert(date.getTime());
            _filterTextOperator = op;
        }

        DateCompareClause(FieldKey fieldKey, CompareType t, String op, Calendar date, Calendar param0, Calendar param1)
        {
            super(fieldKey, t, null);
            _filterTextDate = ConvertUtils.convert(date.getTime());
            _filterTextOperator = op;
            _paramVals = new Object[]{param0.getTime(), param1.getTime()};
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            return super.toWhereClause(dialect, alias);
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append("DATE(");
            appendColumnName(sb, formatter);
            sb.append(")");
            sb.append(_filterTextOperator);
            sb.append(_filterTextDate);
        }
    }


    static class DateEqCompareClause extends DateCompareClause
    {
        DateEqCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            super(fieldKey, DATE_EQUAL, " = ", startValue, startValue, addOneDay(startValue));
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            String selectName = dialect.getColumnSelectName(alias);
            return selectName + " >= ? AND " + selectName + " < ?";
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String selectName = getLabKeySQLColName(_fieldKey);
            String sql = selectName + " >= ? AND " + selectName + " < ?";
            return substituteLabKeySqlParams(sql, columnMap);
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date begin = asDate(getParamVals()[0]);
            Date end = asDate(getParamVals()[1]);
            return dateValue.compareTo(begin) >= 0 && dateValue.compareTo(end) < 0;
        }
    }


    static class DateNeqCompareClause extends DateCompareClause
    {
        DateNeqCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            super(fieldKey, DATE_NOT_EQUAL, " <> ", startValue, startValue, addOneDay(startValue));
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            String selectName = dialect.getColumnSelectName(alias);
            return selectName + " < ? OR " + selectName + " >= ?";
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String selectName = getLabKeySQLColName(_fieldKey);
            String sql = selectName + " < ? OR " + selectName + " >= ?";
            return substituteLabKeySqlParams(sql, columnMap);
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            if (value == null)
                return true;
            Date dateValue = asDate(value);
            Date begin = asDate(getParamVals()[0]);
            Date end = asDate(getParamVals()[1]);
            return !(dateValue.compareTo(begin) >= 0 && dateValue.compareTo(end) < 0);
        }
    }


    static class DateGtCompareClause extends DateCompareClause
    {
        DateGtCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            super(fieldKey, DATE_GT, " > ", startValue, addOneDay(startValue));
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date param = asDate(getParamVals()[0]);
            return dateValue.compareTo(param) >= 0;
        }
    }
    

    static class DateGteCompareClause extends DateCompareClause
    {
        DateGteCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            super(fieldKey, DATE_GTE, " >= ", startValue, startValue);
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date param = asDate(getParamVals()[0]);
            return dateValue.compareTo(param) >= 0;
        }
    }


    static class DateLtCompareClause extends DateCompareClause
    {
        DateLtCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            super(fieldKey, DATE_LT, " < ", startValue, startValue);
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date param = asDate(getParamVals()[0]);
            return dateValue.compareTo(param) < 0;
        }
    }


    static class DateLteCompareClause extends DateCompareClause
    {
        DateLteCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            super(fieldKey, DATE_LTE, " <= ", startValue, addOneDay(startValue));
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date param = asDate(getParamVals()[0]);
            return dateValue.compareTo(param) < 0;
        }
    }


    abstract private static class LikeClause extends CompareClause
    {
        static final private char[] charsToBeEscaped = new char[] { '%', '_', '[' };
        static final private char escapeChar = '!';

        private final String _unescapedValue;

        protected LikeClause(FieldKey fieldKey, CompareType compareType, Object value)
        {
            super(fieldKey, compareType, escapeLikePattern(Objects.toString(value, "")));
            _unescapedValue = Objects.toString(value, "");
        }

        static private String escapeLikePattern(String value)
        {
            String strEscape = new String(new char[] { escapeChar } );
            value = StringUtils.replace(value, strEscape, strEscape + strEscape);
            for (char ch : charsToBeEscaped)
            {
                if (ch == escapeChar)
                    continue;
                String strCh = new String(new char[] { ch});
                value = StringUtils.replace(value, strCh, strEscape + strCh);
            }
            return value;
        }

        protected String sqlEscape()
        {
            assert escapeChar != '\'';
            return " ESCAPE '" + escapeChar + "'";
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" LIKE ?");
        }

        @Override
        // Value has been escaped for LIKE SQL; use stashed unescaped value for display text instead
        protected void replaceParamValues(StringBuilder sb, int fromIndex)
        {
            int i = sb.indexOf("?", fromIndex);
            sb.replace(i, i + 1, _unescapedValue);
        }

        abstract String toWhereClause(SqlDialect dialect, String alias);
    }

    private static class StartsWithClause extends LikeClause
    {
        public StartsWithClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.STARTS_WITH, value);
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + " " + dialect.getCaseInsensitiveLikeOperator() + " " + dialect.concatenate("?", "'%'") + sqlEscape();
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            Object value = getParamVals()[0];
            return "STARTSWITH(" + getLabKeySQLColName(_fieldKey) + ", '" + escapeLabKeySqlValue(value, getColumnType(columnMap, JdbcType.VARCHAR), true) + "')";
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" STARTS WITH ?");
        }
    }

    private static class DoesNotStartWithClause extends LikeClause
    {
        public DoesNotStartWithClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.DOES_NOT_START_WITH, value);
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return "(" + dialect.getColumnSelectName(alias) + " IS NULL OR " + dialect.getColumnSelectName(alias) + " NOT " + dialect.getCaseInsensitiveLikeOperator() + " " + dialect.concatenate("?", "'%'") + sqlEscape() + ")";
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            Object value = getParamVals()[0];
            return "(" + getLabKeySQLColName(_fieldKey) + " IS NULL OR " + getLabKeySQLColName(_fieldKey) + " NOT LIKE '" + escapeLabKeySqlValue(value, getColumnType(columnMap, JdbcType.VARCHAR), true) + "%'" + ")";
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" DOES NOT START WITH ?");
        }
    }

    public static class EqualsCompareClause extends CompareClause
    {
        public EqualsCompareClause(@NotNull FieldKey fieldKey, CompareType comparison, Object value)
        {
            super(fieldKey, comparison, value);
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = columnMap.get(_fieldKey);
            assert getParamVals().length == 1;
            if (needsTypeConversion())
            {
                Object value = convertParamValue(colInfo, getParamVals()[0]);
                if (isNull(value))
                {
                    // Flip to treat this as an IS NULL comparison request
                    return ISBLANK.createFilterClause(_fieldKey, null).toSQLFragment(columnMap, dialect);
                }
            }

            return super.toSQLFragment(columnMap, dialect);
        }
    }

    public static class NotEqualsCompareClause extends CompareClause
    {
        public NotEqualsCompareClause(FieldKey fieldKey, CompareType comparison, Object value)
        {
            super(fieldKey, comparison, value);
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = columnMap.get(_fieldKey);
            assert getParamVals().length == 1;
            Object convertedValue = convertParamValue(colInfo, getParamVals()[0]);
            if (needsTypeConversion() && isNull(convertedValue))
            {
                // Flip to treat this as an IS NOT NULL comparison request
                return NONBLANK.createFilterClause(_fieldKey, null).toSQLFragment(columnMap, dialect);
            }

            return super.toSQLFragment(columnMap, dialect);
        }
    }

    public static class ContainsClause extends LikeClause
    {
        public ContainsClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.CONTAINS, value);
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + " " + dialect.getCaseInsensitiveLikeOperator() + " " + dialect.concatenate("'%'", "?", "'%'") + sqlEscape(); 
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String colName = getLabKeySQLColName(_fieldKey);
            return "LOWER(" + colName + ") LIKE LOWER('%" + escapeLabKeySqlValue(getParamVals()[0], getColumnType(columnMap, JdbcType.VARCHAR), true) + "%')";
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" CONTAINS ?");
        }
    }

    public static class DoesNotContainClause extends LikeClause
    {
        public DoesNotContainClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.DOES_NOT_CONTAIN, value);
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return "(" + dialect.getColumnSelectName(alias) + " IS NULL OR " + dialect.getColumnSelectName(alias) + " NOT " + dialect.getCaseInsensitiveLikeOperator() + " " + dialect.concatenate("'%'", "?", "'%'") + sqlEscape() + ")"; 
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String colName = getLabKeySQLColName(_fieldKey);
            return "(" + colName + " IS NULL OR LOWER(" + colName + ") NOT LIKE LOWER('%" + escapeLabKeySqlValue(getParamVals()[0], getColumnType(columnMap, JdbcType.VARCHAR), true) + "%'))";
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" DOES NOT CONTAIN ?");
        }
    }

    private static class NotEqualOrNullClause extends CompareClause
    {
        NotEqualOrNullClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.NEQ_OR_NULL, value);
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            String neq = CompareType.NEQ.getSql();
            String isNull = CompareType.ISBLANK.getSql();
            return "(" + dialect.getColumnSelectName(alias) + neq + " OR " + dialect.getColumnSelectName(alias) + isNull + ")";
        }
    }

    private static class QcClause extends CompareClause
    {
        private final boolean isNull;

        QcClause(FieldKey fieldKey, boolean isNull)
        {
            super(fieldKey, isNull ? CompareType.NO_QC : CompareType.HAS_QC, null);
            this.isNull = isNull;
        }

        @Override
        public List<String> getColumnNames()
        {
            List<String> names = new ArrayList<>();
            names.add(_fieldKey.toString());
            names.add(_fieldKey.toString() + MvColumn.MV_INDICATOR_SUFFIX);
            return names;
        }

        @Override
        public List<FieldKey> getFieldKeys()
        {
            List<FieldKey> names = new ArrayList<>();
            names.add(_fieldKey);
            names.add(new FieldKey(_fieldKey.getParent(), _fieldKey.getName() + MvColumn.MV_INDICATOR_SUFFIX));
            return names;
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            FieldKey mvFieldKey = new FieldKey(_fieldKey.getParent(), _fieldKey.getName() + MvColumn.MV_INDICATOR_SUFFIX);
            ColumnInfo qcColumn = columnMap.get(mvFieldKey);
            SQLFragment sql = new SQLFragment(qcColumn.getAlias() + " IS " + (isNull ? "" : "NOT ") + "NULL");
            return sql;
        }
    }

    private static class MemberOfClause extends CompareClause
    {
        MemberOfClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.MEMBER_OF, value);
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = columnMap != null ? columnMap.get(_fieldKey) : null;
            String alias = colInfo != null ? colInfo.getAlias() : _fieldKey.getName();

            if (getParamVals().length == 0)
            {
                return new SQLFragment("(1 = 2)");
            }

            return getMemberOfSQL(dialect, new SQLFragment(alias), new SQLFragment("?", convertParamValue(colInfo, getParamVals()[0])));
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            return "ISMEMBEROF(" + getParamVals()[0] + ", " + getLabKeySQLColName(_fieldKey) + ")";
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            // Try to resolve the parameter value to a Group object
            if (getParamVals().length > 0)
            {
                try
                {
                    Integer groupId = (Integer)ConvertUtils.convert(String.valueOf(getParamVals()[0]), Integer.class);
                    if (groupId != null)
                    {
                        Group group = org.labkey.api.security.SecurityManager.getGroup(groupId.intValue());
                        if (group != null)
                        {
                            sb.append("Is a member of the group '").append(group.getName()).append("'");
                            return;
                        }
                    }
                }
                catch (ConversionException ignored) {}
            }
            // Couldn't resolve the group for whatever reason
            sb.append("Invalid 'member of' filter");
        }
    }

    /** Generates SQL that checks if the given user id is a member of the group id */
    public static SQLFragment getMemberOfSQL(SqlDialect dialect, SQLFragment userIdSQL, SQLFragment groupIdSQL)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("(").append(groupIdSQL).append(") IN (");
        if (dialect.isPostgreSQL())
        {
            ret.append(
                "WITH RECURSIVE allmembers(userid, groupid) AS (\n" +
                "   SELECT userid, groupid FROM core.members WHERE userid = ").append(userIdSQL).append(
                "\nUNION\n" +
                "   SELECT a.groupid as userid, m.groupid as groupid FROM allmembers a, core.members m WHERE a.groupid=m.userid\n" +
                ")\n" +
                "SELECT groupid FROM allmembers"
            );
        }
        else
        {
            // nested WITH doesn't seem to work on SQL Server
            // ONLY WORKS 3 LEVELS DEEP!
            SQLFragment onelevel = new SQLFragment(), twolevel = new SQLFragment(), threelevel = new SQLFragment();
            onelevel.append("SELECT groupid FROM core.members _M1_ where _M1_.userid=(").append(userIdSQL).append(")");
            twolevel.append("SELECT groupid FROM core.members _M2_ WHERE _M2_.userid IN (").append(onelevel).append(")");
            threelevel.append("SELECT groupid FROM core.members _M3_ WHERE _M3_.userid IN (").append(twolevel).append(")");
            ret.append(onelevel).append(" UNION ").append(twolevel).append(" UNION ").append(threelevel);
        }

        ret.append(" UNION SELECT (").append(userIdSQL).append(")");
        ret.append(" UNION SELECT ").append(Group.groupGuests);
        ret.append(" UNION SELECT ").append(Group.groupUsers).append(" WHERE 0 < (").append(userIdSQL).append(")");
        ret.append(")");
        return ret;
    }
}
