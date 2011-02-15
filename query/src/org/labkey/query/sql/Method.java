/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.Group;
import org.labkey.query.QueryServiceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

public abstract class Method
{
    static HashMap<String,Method> labkeyMethod = new HashMap<String,Method>();

    static
    {
        labkeyMethod.put("abs", new JdbcMethod("abs", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("acos", new JdbcMethod("acos", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("atan", new JdbcMethod("atan", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("atan2", new JdbcMethod("atan2", JdbcType.DOUBLE, 2, 2));
        labkeyMethod.put("ceiling", new JdbcMethod("ceiling", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("cos", new JdbcMethod("cos", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("cot", new JdbcMethod("cot", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("degrees", new JdbcMethod("degrees", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("exp", new JdbcMethod("exp", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("floor", new JdbcMethod("floor", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("log", new JdbcMethod("log", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("log10", new JdbcMethod("log10", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("mod", new JdbcMethod("mod", JdbcType.DOUBLE, 2, 2));
        labkeyMethod.put("pi", new JdbcMethod("pi", JdbcType.DOUBLE, 0, 0));
        labkeyMethod.put("power", new JdbcMethod("power", JdbcType.DOUBLE, 2, 2));
        labkeyMethod.put("radians", new JdbcMethod("radians", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("rand", new JdbcMethod("rand", JdbcType.DOUBLE, 0, 1));
        labkeyMethod.put("round", new Method("round", JdbcType.DOUBLE, 1, 2)
			{
				@Override
				public MethodInfo getMethodInfo()
				{
					return new RoundInfo();
				}
			});
        labkeyMethod.put("sign", new JdbcMethod("sign", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("sin", new JdbcMethod("sin", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("sqrt", new JdbcMethod("sqrt", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("tan", new JdbcMethod("tan", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("truncate", new JdbcMethod("truncate", JdbcType.DOUBLE, 2, 2));


        labkeyMethod.put("lcase", new JdbcMethod("lcase", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("lower", new JdbcMethod("lcase", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("left", new JdbcMethod("left", JdbcType.VARCHAR, 2, 2));
        labkeyMethod.put("locate", new Method("locate", JdbcType.INTEGER, 2, 3)
            {
                public MethodInfo getMethodInfo()
                {
                    return new AbstractMethodInfo(_jdbcType)
                    {
                        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
                        {
                            assert arguments.length == 2 || arguments.length == 3;
                            if (arguments.length == 2)
                                return schema.getSqlDialect().sqlLocate(arguments[0], arguments[1]);
                            else
                                return schema.getSqlDialect().sqlLocate(arguments[0], arguments[1], arguments[2]);
                        }
                    };
                }
            });
        labkeyMethod.put("ltrim", new JdbcMethod("ltrim", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("repeat", new JdbcMethod("repeat", JdbcType.VARCHAR, 2, 2));
        labkeyMethod.put("rtrim", new JdbcMethod("rtrim", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("substring", new JdbcMethod("substring", JdbcType.VARCHAR, 2, 3));
        labkeyMethod.put("ucase", new JdbcMethod("ucase", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("upper", new JdbcMethod("ucase", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("length", new JdbcMethod("length", JdbcType.INTEGER, 1, 1));


        labkeyMethod.put("curdate", new JdbcMethod("curdate", JdbcType.DATE, 0, 0));
        labkeyMethod.put("curtime", new JdbcMethod("curtime", JdbcType.DATE, 0, 0));
        labkeyMethod.put("dayofmonth", new JdbcMethod("dayofmonth", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("dayofweek", new JdbcMethod("dayofweek", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("dayofyear", new JdbcMethod("dayofyear", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("hour", new JdbcMethod("hour", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("minute", new JdbcMethod("minute", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("month", new JdbcMethod("month", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("monthname", new JdbcMethod("monthname", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("now", new JdbcMethod("now", JdbcType.TIMESTAMP, 0, 0));
        labkeyMethod.put("quarter", new JdbcMethod("quarter", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("second", new JdbcMethod("second", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("week", new JdbcMethod("week", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("year", new JdbcMethod("year", JdbcType.INTEGER, 1, 1));
	    labkeyMethod.put("timestampadd", new Method("timestampadd", JdbcType.TIMESTAMP, 3, 3)
			{
				@Override
				public MethodInfo getMethodInfo()
				{
					return new TimestampInfo(this);
				}
			});
        labkeyMethod.put("timestampdiff", new Method("timestampdiff", JdbcType.INTEGER, 3, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new TimestampInfo(this);
                }
            });
        labkeyMethod.put("age_in_months", new Method(JdbcType.INTEGER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeInMonthsMethodInfo();
                }
            });
        labkeyMethod.put("age", new Method(JdbcType.INTEGER, 2, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeMethodInfo();
                }

                @Override
                public void validate(CommonTree fn, List<QNode> args, List<Exception> parseErrors)
                {
                    super.validate(fn, args, parseErrors);
                    // only YEAR, MONTH supported
                    if (args.size() == 3)
                    {
                        QNode nodeInterval = args.get(2);
                        TimestampDiffInterval i = TimestampDiffInterval.parse(nodeInterval.getTokenText());
                        if (!(i == TimestampDiffInterval.SQL_TSI_MONTH || i == TimestampDiffInterval.SQL_TSI_YEAR))
                        {
                            parseErrors.add(new QueryParseException("AGE function supports SQL_TSI_YEAR or SQL_TSI_MONTH", null,
                                    nodeInterval.getLine(), nodeInterval.getColumn()));
                        }
                    }
                }
            });
        labkeyMethod.put("age_in_years", new Method(JdbcType.INTEGER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeInYearsMethodInfo();
                }
            });


        labkeyMethod.put("ifnull", new JdbcMethod("ifnull", JdbcType.OTHER, 2, 2));
        labkeyMethod.put("isequal", new Method("isequal", JdbcType.BOOLEAN, 2, 2){
            @Override
            public MethodInfo getMethodInfo()
            {
                return new IsEqualInfo();
            }
        });
        labkeyMethod.put("cast", new Method("convert", JdbcType.OTHER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new ConvertInfo();
                }
            });
        labkeyMethod.put("convert", new Method("convert", JdbcType.OTHER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new ConvertInfo();
                }
            });
        labkeyMethod.put("coalesce", new Method("coalesce", JdbcType.OTHER, 0, Integer.MAX_VALUE)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new PassthroughInfo("coalesce", JdbcType.OTHER);
                }
            });

        // special functions

        labkeyMethod.put("userid", new Method("userid", JdbcType.INTEGER, 0, 0) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new UserIdInfo();
            }
        });

        labkeyMethod.put("ismemberof", new Method("ismemberof", JdbcType.BOOLEAN, 1, 2) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new IsMemberInfo();
            }
        });

        // USERID() is handled by SqlParser, converted to "@@USERID"
    }


    final JdbcType _jdbcType;
    final String _name;
    final int _minArgs;
    final int _maxArgs;
    
    Method(JdbcType jdbcType, int min, int max)
    {
        this("#UNDEF#", jdbcType, min, max);
    }

    Method(String name, JdbcType jdbcType, int min, int max)
    {
        _name = name;
        _jdbcType = jdbcType;
        _minArgs = min;
        _maxArgs = max;
    }

    abstract public MethodInfo getMethodInfo();


    public void validate(CommonTree fn, List<QNode> args, List<Exception> parseErrors)
    {
        int count = args.size();
        if (count < _minArgs || count > _maxArgs)
        {
            if (_minArgs == _maxArgs)
                parseErrors.add(new QueryParseException(_name.toUpperCase() + " function expects " + _minArgs + " argument" + (_minArgs==1?"":"s"), null, fn.getLine(), fn.getCharPositionInLine()));
            else
                parseErrors.add(new QueryParseException(_name.toUpperCase() + " function expects " + _minArgs + " to " + _maxArgs + " arguments", null, fn.getLine(), fn.getCharPositionInLine()));
        }
    }


    class JdbcMethodInfoImpl extends AbstractMethodInfo
    {
        String _name;

        public JdbcMethodInfoImpl(String name, JdbcType jdbcType)
        {
            super(jdbcType);
            _name = name;
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append("{fn ");
            ret.append(_name);
            ret.append("(");
            String comma = "";
            for (SQLFragment argument : arguments)
            {
                ret.append(comma);
                comma = ",";
                ret.append(argument);
            }
            ret.append(")}");
            return ret;
        }
    }


    class TimestampInfo extends JdbcMethodInfoImpl
    {
        public TimestampInfo(Method method)
        {
            super(method._name, method._jdbcType);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] argumentsIN)
        {
            SQLFragment[] arguments = argumentsIN.clone();
            if (arguments.length >= 1)
            {
                TimestampDiffInterval i = TimestampDiffInterval.parse(arguments[0].getSQL());
                if (i != null)
                    arguments[0] = new SQLFragment(i.name());
            }
            return super.getSQL(schema, arguments);
        }
    }


    enum TimestampDiffInterval
    {
        SQL_TSI_FRAC_SECOND,
        SQL_TSI_SECOND,
        SQL_TSI_MINUTE,
        SQL_TSI_HOUR,
        SQL_TSI_DAY,
        SQL_TSI_WEEK,
        SQL_TSI_MONTH,
        SQL_TSI_QUARTER,
        SQL_TSI_YEAR;


        static TimestampDiffInterval parse(String s)
        {
            String interval = StringUtils.trimToEmpty(s).toUpperCase();
            if (interval.length() >= 2 && interval.startsWith("'") && interval.endsWith("'"))
                interval = interval.substring(1,interval.length()-1);
            if (!interval.startsWith("SQL_TSI"))
                interval = "SQL_TSI_" + interval;
            try
            {
                TimestampDiffInterval i = TimestampDiffInterval.valueOf(interval);
                return i;
            }
            catch (IllegalArgumentException x)
            {
                return null;
            }
        }
    }


    class ConvertInfo extends AbstractMethodInfo
    {
        public ConvertInfo()
        {
            super(JdbcType.OTHER);
        }

        @Override
        public ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
        {
            SQLFragment[] fragments = getSQLFragments(arguments);
            JdbcType jdbcType = _jdbcType;
            if (fragments.length >= 2)
            {
                try
                {
                    String sqlEscapeTypeName = getTypeArgument(fragments);
                    jdbcType = ConvertType.valueOf(sqlEscapeTypeName).jdbcType;
                }
                catch (IllegalArgumentException x)
                {
                    /* */
                }
            }
            return new ExprColumn(parentTable, alias, getSQL(parentTable.getSchema(), fragments), jdbcType.sqlType);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] fragments)
        {
            JdbcType jdbcType = _jdbcType;
            if (fragments.length >= 2)
            {
                String sqlEscapeTypeName = getTypeArgument(fragments);
                String typeName = sqlEscapeTypeName;
                try
                {
                    jdbcType = ConvertType.valueOf(sqlEscapeTypeName).jdbcType;
                    typeName = schema.getSqlDialect().sqlTypeNameFromSqlType(jdbcType.sqlType);
                    fragments = new SQLFragment[] {fragments[0], new SQLFragment(typeName)};
                }
                catch (IllegalArgumentException x)
                {
                    /* */
                }
            }

            SQLFragment ret = new SQLFragment();
            ret.append("CAST(");
            if (fragments.length > 0)
                ret.append(fragments[0]);
            if (fragments.length > 1)
            {
                ret.append(" AS ");
                ret.append(fragments[1]);
            }
            ret.append(")");                            
            return ret;
        }

        String getTypeArgument(SQLFragment[] argumentsIN) throws IllegalArgumentException
        {
            if (argumentsIN.length < 2)
                return "VARCHAR";
            String typeName = StringUtils.trimToEmpty(argumentsIN[1].getSQL());
            if (typeName.length() >= 2 && typeName.startsWith("'") && typeName.endsWith("'"))
                typeName = typeName.substring(1,typeName.length()-1);
            if (typeName.startsWith("SQL_"))
                typeName = typeName.substring(4);
            return typeName;
        }
    }

    static class PassthroughInfo extends AbstractMethodInfo
    {
        String _name;

        public PassthroughInfo(String method, JdbcType jdbcType)
        {
            super(jdbcType);
            _name = method;
        }

        @Override
        protected JdbcType getSqlType(ColumnInfo[] arguments)
        {
            JdbcType jdbcType = _jdbcType;
            if (jdbcType == JdbcType.OTHER)
                jdbcType = arguments.length > 0 ? arguments[0].getJdbcType() : JdbcType.VARCHAR;
            return jdbcType;
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append(_name).append("(");
            String comma = "";
            for (SQLFragment arg : arguments)
            {
                ret.append(comma);
                ret.append(arg);
                comma = ",";
            }
            ret.append(")");
            return ret;
        }
    }

	class RoundInfo extends JdbcMethodInfoImpl
	{
		RoundInfo()
		{
			super("round", JdbcType.DOUBLE);
		}

		// https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=7078
        // Even though we are generating {fn ROUND()}, SQL Server requires 2 arguments
        // while Postgres requires 1 argument (for doubles)
		public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
		{
            boolean supportsRoundDouble = schema.getSqlDialect().supportsRoundDouble();
            boolean unitRound = arguments.length == 1 || (arguments.length==2 && arguments[1].getSQL().equals("0"));
            if (unitRound)
            {
                if (supportsRoundDouble)
                    return super.getSQL(schema, new SQLFragment[] {arguments[0], new SQLFragment("0")});
                else
                    return super.getSQL(schema, new SQLFragment[] {arguments[0]});
            }

            int i = Integer.MIN_VALUE;
            try
            {
                i = Integer.parseInt(arguments[1].getSQL());
            }
            catch (NumberFormatException x)
            {
            }

            if (supportsRoundDouble || i == Integer.MIN_VALUE)
                return super.getSQL(schema, arguments);

            // fall back, only supports simple integer
            SQLFragment scaled = new SQLFragment();
            scaled.append("(");
            scaled.append(arguments[0]);
            scaled.append(")*").append(Math.pow(10,i));
            SQLFragment ret = super.getSQL(schema, new SQLFragment[] {scaled});
            ret.append("/");
            ret.append(Math.pow(10,i));
            return ret;
		}
	}


    class AgeMethodInfo extends AbstractMethodInfo
    {
        AgeMethodInfo()
        {
            super(JdbcType.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            if (arguments.length == 2)
                return new AgeInYearsMethodInfo().getSQL(schema, arguments);
            TimestampDiffInterval i = TimestampDiffInterval.parse(arguments[2].getSQL());
            if (i == TimestampDiffInterval.SQL_TSI_YEAR)
                return new AgeInYearsMethodInfo().getSQL(schema, arguments);
            if (i == TimestampDiffInterval.SQL_TSI_MONTH)
                return new AgeInMonthsMethodInfo().getSQL(schema, arguments);
            if (null == i)
                throw new IllegalArgumentException("AGE(" + arguments[2].getSQL() + ")");
            else
                throw new IllegalArgumentException("AGE only supports YEAR and MONTH");
        }
    }


    class AgeInYearsMethodInfo extends AbstractMethodInfo
    {
        AgeInYearsMethodInfo()
        {
            super(JdbcType.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            MethodInfo year = labkeyMethod.get("year").getMethodInfo();
            MethodInfo month = labkeyMethod.get("month").getMethodInfo();
            MethodInfo dayofmonth = labkeyMethod.get("dayofmonth").getMethodInfo();

            SQLFragment ret = new SQLFragment();
            SQLFragment yearA = year.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment monthA = month.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment dayA = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment yearB = year.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment monthB = month.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment dayB = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[1]});

            ret.append("(CASE WHEN (")
                    .append(monthA).append(">").append(monthB).append(" OR ")
                    .append(monthA).append("=").append(monthB).append(" AND ")
                    .append(dayA).append(">").append(dayB)
                    .append(") THEN (")
                    .append(yearB).append("-").append(yearA).append("-1")
                    .append(") ELSE (")
                    .append(yearB).append("-").append(yearA)
                    .append(") END)");
            return ret;
        }
    }


    class AgeInMonthsMethodInfo extends AbstractMethodInfo
    {
        AgeInMonthsMethodInfo()
        {
            super(JdbcType.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            MethodInfo year = labkeyMethod.get("year").getMethodInfo();
            MethodInfo month = labkeyMethod.get("month").getMethodInfo();
            MethodInfo dayofmonth = labkeyMethod.get("dayofmonth").getMethodInfo();

            SQLFragment ret = new SQLFragment();
            SQLFragment yearA = year.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment monthA = month.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment dayA = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment yearB = year.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment monthB = month.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment dayB = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[1]});

            ret.append("(CASE WHEN (")
                    .append(dayA).append(">").append(dayB)
                    .append(") THEN (")
                    .append("12*(").append(yearB).append("-").append(yearA).append(")")
                    .append("+")
                    .append(monthB).append("-").append(monthA).append("-1")
                    .append(") ELSE (")
                    .append("12*(").append(yearB).append("-").append(yearA).append(")")
                    .append("+")
                    .append(monthB).append("-").append(monthA)
                    .append(") END)");
            return ret;
        }
    }


    class IsEqualInfo extends AbstractMethodInfo
    {
        IsEqualInfo()
        {
            super(JdbcType.BOOLEAN);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            SQLFragment a = arguments[0];
            SQLFragment b = arguments[1];

            ret.append("(");
            ret.append("(").append(a).append(")=(").append(b).append(")");
            ret.append(" OR (");
            ret.append("(").append(a).append(") IS NULL AND (").append(b).append(") IS NULL");
            ret.append("))");

            return ret;
        }
    }

    class UserIdInfo extends AbstractMethodInfo
    {
        UserIdInfo()
        {
            super(JdbcType.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment("?");
            ret.add(new Callable(){
                @Override
                public Object call() throws Exception
                {
                    return QueryServiceImpl.get().getEnvironment(QueryService.Environment.USERID);
                }
            });
            return ret;
        }
    }

    class IsMemberInfo extends AbstractMethodInfo
    {
        IsMemberInfo()
        {
            super(JdbcType.BOOLEAN);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            SQLFragment group = arguments[0];
            SQLFragment user;

            if (arguments.length > 1)
                user = arguments[1];
            else
                user = new UserIdInfo().getSQL(schema, null);

            ret.append("(").append(group).append(") IN (SELECT groupid FROM core.members _M_ where _M_.userid=(").append(user).append(")");
            ret.append(" UNION SELECT (").append(user).append(")");
            ret.append(" UNION SELECT ").append(Group.groupGuests);
            ret.append(" UNION SELECT ").append(Group.groupUsers).append(" WHERE 0 < (").append(user).append(")");
            ret.append(")");
            return ret;
            /*
            NOTE: the recursive version would look something like

            WITH  Groups(principal) AS
            (
                SELECT <<userid>> UNION SELECT -2 UNION SELECT -3
                UNION ALL
                SELECT groupid
                FROM core.Members INNER JOIN Groups ON Members.userid=Groups.principal INNER JOIN core.Principals ON Members.GroupId=Principals.userid
                WHERE Principals.type='g'
            )
            SELECT * FROM Groups
            */
        }
    }


    private static class JdbcMethod extends Method
    {
        JdbcMethod(String name, JdbcType jdbcType, int min, int max)
        {
            super(name, jdbcType, min, max);
        }

        @Override
        public MethodInfo getMethodInfo()
        {
            return new JdbcMethodInfoImpl(_name, _jdbcType);
        }
    }

    private static class PassthroughMethod extends Method
    {
        PassthroughMethod(String name, JdbcType jdbcType, int min, int max)
        {
            super(name, jdbcType, min, max);
        }

        @Override
        public MethodInfo getMethodInfo()
        {
            return new PassthroughInfo(_name, _jdbcType);
        }
    }


    public static Method valueOf(String name)
    {
        return resolve(null, name);
    }
    
    public static Method resolve(SqlDialect d, String name)
    {
        name = name.toLowerCase();
        // UNDONE
        Method m = labkeyMethod.get(name);
        if (null != m)
            return m;
        if (null != d )
        {
            if (name.startsWith("::"))
                name = name.substring(2);
            if (d.isPostgreSQL())
                m = postgresMethods.get(name);
            else if (d.isSqlServer())
                m = mssqlMethods.get(name);
            if (null != m)
                return m;
        }
        throw new IllegalArgumentException(name);
    }


    static CaseInsensitiveHashMap<Method> postgresMethods = new CaseInsensitiveHashMap<Method>();
    static
    {
        postgresMethods.put("ascii",new PassthroughMethod("ascii",JdbcType.INTEGER,1,1));
        postgresMethods.put("btrim",new PassthroughMethod("btrim",JdbcType.VARCHAR,1,2));
        postgresMethods.put("char_length",new PassthroughMethod("char_length",JdbcType.INTEGER,1,1));
        postgresMethods.put("character_length",new PassthroughMethod("character_length",JdbcType.INTEGER,1,1));
        postgresMethods.put("chr",new PassthroughMethod("chr",JdbcType.VARCHAR,1,1));
        postgresMethods.put("decode",new PassthroughMethod("decode",JdbcType.VARCHAR,2,2));
        postgresMethods.put("encode",new PassthroughMethod("encode",JdbcType.VARCHAR,2,2));
        postgresMethods.put("initcap",new PassthroughMethod("initcap",JdbcType.VARCHAR,1,1));
        postgresMethods.put("lpad",new PassthroughMethod("lpad",JdbcType.VARCHAR,2,3));
        postgresMethods.put("md5",new PassthroughMethod("md5",JdbcType.VARCHAR,1,1));
        postgresMethods.put("octet_length",new PassthroughMethod("octet_length",JdbcType.INTEGER,1,1));
        postgresMethods.put("quote_ident",new PassthroughMethod("quote_ident",JdbcType.VARCHAR,1,1));
        postgresMethods.put("quote_literal",new PassthroughMethod("quote_literal",JdbcType.VARCHAR,1,1));
        postgresMethods.put("regexp_replace",new PassthroughMethod("regexp_replace",JdbcType.VARCHAR,3,4));
        postgresMethods.put("repeat",new PassthroughMethod("repeat",JdbcType.VARCHAR,2,2));
        postgresMethods.put("replace",new PassthroughMethod("replace",JdbcType.VARCHAR,3,3));
        postgresMethods.put("rpad",new PassthroughMethod("rpad",JdbcType.VARCHAR,2,3));
        postgresMethods.put("split_part",new PassthroughMethod("split_part",JdbcType.VARCHAR,3,3));
        postgresMethods.put("strpos",new PassthroughMethod("strpos",JdbcType.VARCHAR,2,2));
        postgresMethods.put("substr",new PassthroughMethod("substr",JdbcType.VARCHAR,2,3));
        postgresMethods.put("to_ascii",new PassthroughMethod("to_ascii",JdbcType.VARCHAR,1,2));
        postgresMethods.put("to_hex",new PassthroughMethod("to_hex",JdbcType.VARCHAR,1,1));
        postgresMethods.put("translate",new PassthroughMethod("translate",JdbcType.VARCHAR,3,3));
        postgresMethods.put("to_char",new PassthroughMethod("to_char",JdbcType.VARCHAR,2,2));
        postgresMethods.put("to_date",new PassthroughMethod("to_date",JdbcType.DATE,2,2));
        postgresMethods.put("to_timestamp",new PassthroughMethod("to_timestamp",JdbcType.TIMESTAMP,2,2));
        postgresMethods.put("to_number",new PassthroughMethod("to_number",JdbcType.DECIMAL,2,2));
    }

    static CaseInsensitiveHashMap<Method> mssqlMethods = new CaseInsensitiveHashMap<Method>();
    static
    {
        mssqlMethods.put("ascii",new PassthroughMethod("ascii",JdbcType.INTEGER,1,1));
        mssqlMethods.put("char",new PassthroughMethod("char",JdbcType.VARCHAR,1,1));
        mssqlMethods.put("charindex",new PassthroughMethod("charindex",JdbcType.INTEGER,2,3));
        mssqlMethods.put("difference",new PassthroughMethod("difference",JdbcType.INTEGER,2,2));
        mssqlMethods.put("len",new PassthroughMethod("len",JdbcType.INTEGER,1,1));
        mssqlMethods.put("patindex",new PassthroughMethod("patindex",JdbcType.INTEGER,2,2));
        mssqlMethods.put("quotename",new PassthroughMethod("quotename",JdbcType.VARCHAR,1,2));
        mssqlMethods.put("replace",new PassthroughMethod("replace",JdbcType.VARCHAR,3,3));
        mssqlMethods.put("replicate",new PassthroughMethod("replicate",JdbcType.VARCHAR,2,2));
        mssqlMethods.put("reverse",new PassthroughMethod("reverse",JdbcType.VARCHAR,1,1));
        mssqlMethods.put("right",new PassthroughMethod("right",JdbcType.VARCHAR,2,2));
        mssqlMethods.put("soundex",new PassthroughMethod("soundex",JdbcType.VARCHAR,1,1));
        mssqlMethods.put("space",new PassthroughMethod("space",JdbcType.VARCHAR,1,1));
        mssqlMethods.put("str",new PassthroughMethod("str",JdbcType.VARCHAR,1,3));
        mssqlMethods.put("stuff",new PassthroughMethod("stuff",JdbcType.VARCHAR,4,4));
    }
}
