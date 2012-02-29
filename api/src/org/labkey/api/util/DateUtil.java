/*
 * Copyright (c) 2004-2012 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


public class DateUtil
{
    private DateUtil()
    {
    }

    private static final Map<Integer, TimeZone> tzCache = new ConcurrentHashMap<Integer, TimeZone>();
    private static final Locale _localeDefault = Locale.getDefault();
    private static final TimeZone _timezoneDefault = TimeZone.getDefault();
    private static final int currentYear = new GregorianCalendar().get(Calendar.YEAR);
    private static final int twoDigitCutoff = (currentYear - 80) % 100;
    private static final int defaultCentury = (currentYear - 80) - twoDigitCutoff;

    private static final String _standardDateFormatString = "yyyy-MM-dd";
    private static final String _standardDateTimeFormatString = "yyyy-MM-dd HH:mm";


    /**
     * GregorianCalendar is expensive because it calls computeTime() in setTimeInMillis()
     * (which is called in the constructor)
     */
    private static class _Calendar extends GregorianCalendar
    {
        public _Calendar(TimeZone tz, Locale locale)
        {
            super(tz, locale);
        }


        public _Calendar(TimeZone tz, Locale locale, int year, int mon, int mday, int hour, int min, int sec, int ms)
        {
            super(tz, locale);
            set(year, mon, mday, hour, min, sec);
            set(Calendar.MILLISECOND, ms);
        }

        public _Calendar(TimeZone tz, Locale locale, long l)
        {
            super(tz, locale);
            setTimeInMillis(l);
        }


        public void setTimeInMillis(long millis)
        {
            isTimeSet = true;
            time = millis;
            areFieldsSet = false;
        }
    }


    public static Calendar newCalendar(TimeZone tz, int year, int mon, int mday, int hour, int min, int sec)
    {
        return new _Calendar(tz, _localeDefault, year, mon, mday, hour, min, sec, 0);
    }

    // disallow date overflow arithmetic
    public static Calendar newCalendarStrict(TimeZone tz, int year, int mon, int mday, int hour, int min, int sec)
    {
        Calendar cal = new _Calendar(tz, _localeDefault, year, mon, mday, hour, min, sec, 0);
        if (cal.get(Calendar.YEAR) != year ||
            cal.get(Calendar.MONTH) != mon ||
            cal.get(Calendar.DAY_OF_MONTH) != mday ||
            cal.get(Calendar.HOUR) != hour ||
            cal.get(Calendar.MINUTE) != min ||
            cal.get(Calendar.SECOND) != sec)
            throw new IllegalArgumentException();
        return cal;
    }


    public static Calendar newCalendar(TimeZone tz)
    {
        return new _Calendar(tz, _localeDefault);
    }


    public static Calendar newCalendar()
    {
        return new _Calendar(_timezoneDefault, _localeDefault);
    }


    public static Calendar newCalendar(long l)
    {
        return new _Calendar(_timezoneDefault, _localeDefault, l);
    }


    public static Calendar now()
    {
        return new _Calendar(_timezoneDefault, _localeDefault);
    }


    public static String nowISO()
    {
        return toISO(System.currentTimeMillis());
    }


    public static String toISO(long l, boolean fFullISO)
    {
        StringBuilder sb = new StringBuilder("1999-12-31 23:59:59.999".length());
        Calendar c = newCalendar(l);
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH)+1;
        int day = c.get(Calendar.DAY_OF_MONTH);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);
        int sec = c.get(Calendar.SECOND);
        int ms = c.get(Calendar.MILLISECOND);

        if (year < 0)
            throw new IllegalArgumentException("BC date not supported");
        if (year < 1000)
        {
            sb.append('0');
            if (year < 100)
                sb.append('0');
            if (year < 10)
                sb.append('0');
        }
        sb.append(year);
        sb.append('-');
        if (month < 10)
            sb.append('0');
        sb.append(month);
        sb.append('-');
        if (day < 10)
            sb.append('0');
        sb.append(day);

        if (!fFullISO && hour==0 && min==0 && sec==0 && ms ==0)
            return sb.toString();

        sb.append(' ');
        if (hour < 10)
            sb.append('0');
        sb.append(hour);
        sb.append(':');
        if (min < 10)
            sb.append('0');
        sb.append(min);

        if (!fFullISO && sec==0 && ms==0)
            return sb.toString();

        sb.append(':');
        if (sec < 10)
            sb.append('0');
        sb.append(sec);

        if (!fFullISO && ms==0)
            return sb.toString();

        sb.append('.');
        if (ms < 100)
        {
            sb.append('0');
            if (ms < 10)
                sb.append('0');
        }
        sb.append(ms);
        return sb.toString();
    }

    public static String toISO(long l)
    {
        return toISO(l, true);
    }

    public static String toISO(java.util.Date d)
    {
        return toISO(d.getTime(), true);
    }


    /**
     * Javascript style parsing, assumes US locale
     *
     * Copied from RHINO (www.mozilla.org/rhino) and modified
     */

    enum Month
    {
        january(0),february(1),march(2),april(3),may(4),june(5),july(6),august(7),september(8),october(9),november(10),december(11);
        int month;
        Month(int i)
        {
            month = i;
        }
    }

    enum Weekday
    {
        monday,tuesday,wednesday,thursday,friday,saturday,sunday
    }

    enum AMPM
    {
        am, pm
    }

    enum TZ
    {
        gmt(0),ut(0),utc(0),est(5*60),edt(4*60),cst(6*60),cdt(5*60),mst(7*60),mdt(6*60),pst(8*60),pdt(7*60);
        int tzoffset;
        TZ(int tzoffset)
        {
            this.tzoffset = tzoffset;
        }
    }

    static Enum[] parts = null;
    static
    {
        ArrayList<Enum> list = new ArrayList<Enum>();
        list.addAll(Arrays.asList(AMPM.values()));
        list.addAll(Arrays.asList(Month.values()));
        list.addAll(Arrays.asList(Weekday.values()));
        list.addAll(Arrays.asList(TZ.values()));
        Collections.sort(list, new Comparator<Enum>() {public int compare(Enum e1, Enum e2){ return e1.name().compareTo(e2.name());}});
        parts = list.toArray(new Enum[list.size()]);
    }

    static Comparator compEnum = new Comparator<Object>() {public int compare(Object o1, Object o2){return ((Enum)o1).name().compareTo((String)o2);}};
    static Enum resolveDatePart(String sequence, int start, int end)
    {
        if (end-start < 2)
            return null;
        String s = sequence.substring(start,end).toLowerCase();
        int i = Arrays.binarySearch(parts, s, compEnum);
        if (i>=0)
            return parts[i];
        i = -(i+1);
        return i>parts.length-1 ? null : parts[i].name().startsWith(s) ? parts[i] : null;
    }


    private enum DateTimeOption
    {
        DateTime,
        DateOnly,
        TimeOnly
    }


    private static long parseDateTimeUS(String s, DateTimeOption option, boolean strict)
    {
        Month month = null; // set if month is specified using name
        int year = -1;
        int mon = -1;
        int mday = -1;
        int hour = -1;
        int min = -1;
        int sec = -1;
        char c = 0;
        char si = 0;
        int i = 0;
        int n, digits;
        int tzoffset = -1;
        char prevc = 0;
        int limit = 0;
        boolean seenplusminus = false;
        boolean monthexpected = false;

        limit = s.length();
        while (i < limit)
        {
            c = s.charAt(i);
            i++;
            if (c <= ' ' || c == ',' || c == '-')
            {
                if (i < limit)
                {
                    si = s.charAt(i);
                    if (c == '-' && '0' <= si && si <= '9')
                    {
                        prevc = c;
                    }
                }
                continue;
            }
            if (c == '(')
            {
                int depth = 1;
                while (i < limit)
                {
                    c = s.charAt(i);
                    i++;
                    if (c == '(')
                        depth++;
                    else if (c == ')')
                        if (--depth <= 0)
                            break;
                }
                continue;
            }
            if ('0' <= c && c <= '9')
            {
                n = c - '0';
                digits = 1;
                while (i < limit && '0' <= (c = s.charAt(i)) && c <= '9')
                {
                    digits++;
                    n = n * 10 + c - '0';
                    i++;
                }

                /* allow TZA before the year, so
                 * 'Wed Nov 05 21:49:11 GMT-0800 1997'
                 * works */

                /* uses of seenplusminus allow : in TZA, so Java
                 * no-timezone style of GMT+4:30 works
                 */
validNum:       {
                    if ((prevc == '+' || prevc == '-') && hour >= 0 /* && year>=0 */)
                    {
                        /* make ':' case below change tzoffset */
                        seenplusminus = true;

                        /* offset */
                        if (n < 24)
                            n = n * 60; /* EG. "GMT-3" */
                        else
                            n = n % 100 + n / 100 * 60; /* eg "GMT-0430" */
                        if (prevc == '+')       /* plus means east of GMT */
                            n = -n;
                    if (tzoffset != 0 && tzoffset != -1)
                            throw new ConversionException(s);
                        tzoffset = n;
                        break validNum;
                    }
                    if (digits > 3 || n >= 70 || ((prevc == '/' || prevc == '-') && mon >= 0 && mday >= 0 && year < 0))
                    {
                        if (year >= 0)
                            throw new ConversionException(s);
                        else if (c <= ' ' || c == ',' || c == '/' || c == '-' || i >= limit)
                        {
                            if (n >= 100 || digits > 3)
                                year = n;
                            else if (n > twoDigitCutoff)
                                year = n + defaultCentury;
                            else
                                year = n + defaultCentury + 100;
                        }
                        else
                            throw new ConversionException(s);
                        break validNum;
                    }
                    if (c == ':' || (hour < 0 && option == DateTimeOption.TimeOnly))
                    {
                        if (c == '/')
                            throw new ConversionException(s);
                        else if (hour < 0)
                            hour = n;
                        else if (min < 0)
                            min = n;
                        else
                            throw new ConversionException(s);
                        break validNum;
                    }
                    if (c == '/' || c == '-')
                    {
                        if (c == '/' && option == DateTimeOption.TimeOnly)
                            throw new ConversionException(s);
                        if (mon < 0)
                            mon = n - 1;
                        else if (mday < 0)
                            mday = n;
                        else
                            throw new ConversionException(s);
                        break validNum;
                    }
                    if (i < limit)
                    {
                        if (mday < 0 && -1 != "jfmasondJFMASOND".indexOf(c))
                        {
                            monthexpected = true;
                        }
                        else if (c != ',' && c > ' ' && c != '-')
                        {
                            throw new ConversionException(s);
                        }
                    }
                    if (seenplusminus && n < 60)
                    {  /* handle GMT-3:30 */
                        if (tzoffset < 0)
                            tzoffset -= n;
                        else
                            tzoffset += n;
                        break validNum;
                    }
                    if (hour >= 0 && min < 0)
                    {
                        min = n;
                        break validNum;
                    }
                    if (min >= 0 && sec < 0)
                    {
                        sec = n;
                        break validNum;
                    }
                    if (mday < 0)
                    {
                        mday = n;
                        break validNum;
                    }
                    else
                    {
                        throw new ConversionException(s);
                    }
                } // validNum: end of number handling
                prevc = 0;
            }
            else if (c == '/' || c == ':' || c == '+' || c == '-')
            {
                prevc = c;
            }
            else
            {
                int st = i - 1;
                while (i < limit)
                {
                    c = s.charAt(i);
                    if (!(('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')))
                        break;
                    i++;
                }
                if (i - st < 2)
                    throw new ConversionException(s);
                Enum dp = null;
                try
                {
                    dp = resolveDatePart(s,st,i);
                }
                catch (IllegalArgumentException x)
                {
                }
                if (null == dp)
                    throw new ConversionException(s);
                if (option != DateTimeOption.TimeOnly && monthexpected && !(dp instanceof Month))
                    throw new ConversionException(s);
                monthexpected = false;
                if (dp == AMPM.am || dp == AMPM.pm)
                {
                    /*
                     * AM/PM. Count 12:30 AM as 00:30, 12:30 PM as
                     * 12:30, instead of blindly adding 12 if PM.
                     */
                    if (hour > 12 || hour < 0)
                    {
                        throw new ConversionException(s);
                    }
                    else if (dp == AMPM.am)
                    {
                        // AM
                        if (hour == 12)
                            hour = 0;
                    }
                    else
                    {
                        // PM
                        if (hour != 12)
                            hour += 12;
                    }
                }
                else if (dp instanceof Weekday)
                {
                    // ignore week days
                }
                else if (dp instanceof Month)
                {
                    // month
                    if (mon < 0)
                    {
                        month = (Month)dp;
                        mon = month.month;
                    }
                    else if (mday < 0 && month == null)
                    {
                        // handle 01/Jan/2001 case (strange I know, the customer is always right)
                        month = (Month)dp;
                        mday = mon+1;
                        mon = month.month;
                    }
                    else
                    {
                        throw new ConversionException(s);
                    }
                    // handle "01Jan2001" or "01 Jan 2001" pretend we're seeing 01/Jan/2001
                    if (i < limit && year < 0)
                        prevc = '/';
                }
                else
                {
                    tzoffset = ((TZ)dp).tzoffset;
                }
            }
        }

        switch (option)
        {
            case DateOnly:
                if (hour >= 0 || min >= 0 || sec >= 0 || tzoffset >= 0)
                    throw new ConversionException(s);
                // fall through
            case DateTime:
                if (year < 0 || mon < 0 || mday < 0)
                    throw new ConversionException(s);
                break;
            case TimeOnly:
                if (year >= 0 || mon >= 0 || mday >= 0 || tzoffset >= 0)
                    throw new ConversionException(s);
                break;
        }

        if (sec < 0)
            sec = 0;
        if (min < 0)
            min = 0;
        if (hour < 0)
            hour = 0;

        if (option == DateTimeOption.TimeOnly)
        {
            if (strict)
            {
                if (hour >= 24 || min >= 60 || sec >= 60)
                    throw new ConversionException(s);
            }
            return 1000L * (hour * (60*60) + (min * 60) + sec);
        }
        
        //
        // This part is changed to work with Java
        //

        TimeZone tz;
        if (tzoffset == -1)
            tz = _timezoneDefault;
        else
        {
            tz = (TimeZone) tzCache.get(tzoffset);
            if (null == tz)
            {
                char sign = tzoffset < 0 ? '+' : '-'; // tzoffset seems to switched from TimeZone sense
                int mins = Math.abs(tzoffset);
                int hr = mins / 60;
                int mn = mins % 60;
                String tzString = "GMT" + sign + (hr / 10) + (hr % 10) + (mn / 10) + (mn % 10);
                tz = TimeZone.getTimeZone(tzString);
                tzCache.put(tzoffset, tz);
            }
        }

        try
        {
            Calendar cal = strict ?
                    newCalendarStrict(tz, year, mon, mday, hour, min, sec) :
                    newCalendar(tz, year, mon, mday, hour, min, sec);

            return cal.getTimeInMillis();
        }
        catch (IllegalArgumentException x)
        {
            throw new ConversionException(s);
        }
    }


    public static long parseStringJDBC(String s)
    {
        try
        {
            if (s.endsWith("Z"))
                s = s.substring(0, s.length()-1);
            int len = s.length();
            long ms;
            if (len <= 10)
            {
                java.sql.Date d = java.sql.Date.valueOf(s);
                ms = d.getTime();
            }
            else
            {
                if (len == 16 && s.charAt(13)==':') // no seconds 2001-02-03 00:00
                    s = s + ":00";
                if (s.charAt(10) == 'T')
                    s = s.substring(0, 10) + ' ' + s.substring(11, s.length());
                Timestamp ts = Timestamp.valueOf(s);
                ms = ts.getTime();
            }
            return ms;
        }
        catch (Exception x)
        {
            ;
        }
        throw new ConversionException(s);
    }


    public static long parseStringJava(String s)
    {
        try
        {
            return DateFormat.getInstance().parse(s).getTime();
        }
        catch (Exception x)
        {
            ;
        }
        try
        {
            //noinspection deprecation
            return Date.parse(s);
        }
        catch (Exception x)
        {
            ;
        }
        try
        {
            // java.util.Date.toString produces dates in the following format.  Try to
            // convert them here.  This is necessary to pass the DRT when running in a
            // non-US timezone:
            return parseDateTime(s, "EEE MMM dd HH:mm:ss zzz yyyy").getTime();
        }
        catch (Exception x)
        {
            ;
        }
        return parseJsonDateTime(s);
    }


    // Parse using a specific pattern... used where strict parsing or non-standard pattern is required
    // Note: SimpleDateFormat is not thread-safe, so we create a new one for every parse.
    public static Date parseDateTime(String s, String pattern) throws ParseException
    {
        if (null == s)
            throw new ParseException(s, 0);

        return new SimpleDateFormat(pattern).parse(s);
    }



    // Lenient parsing using a variety of standard formats
    public static long parseDateTime(String s)
    {
        try
        {
            // quick check for JDBC/ISO date
            if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-')
                return parseStringJDBC(s);
        }
        catch (ConversionException x)
        {
            ;
        }

        try
        {
            // strip off trailing decimal :00:00.000
            int ms = 0;
            int len = s.length();
            int period = s.lastIndexOf('.');
            if (period > 6 && period >= len - 4 && period < len - 1 &&
                    s.charAt(period - 3) == ':' &&
                    s.charAt(period - 6) == ':')
            {
                String m = s.substring(period + 1);
                ms = Integer.parseInt(m);
                if (m.length() == 1)
                    ms *= 100;
                else if (m.length() == 2)
                    ms *= 10;
                s = s.substring(0, period);
            }
            long time = parseDateTimeUS(s, DateTimeOption.DateTime, true);
            return time + ms;
        }
        catch (ConversionException x)
        {
            ;
        }

        return parseStringJava(s);
    }


    // Lenient parsing using a variety of standard formats
    public static long parseDate(String s)
    {
        try
        {
            // quick check for JDBC/ISO date
            if (s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-')
                return parseStringJDBC(s);
        }
        catch (ConversionException ignored) {}

        try
        {
            return parseDateTimeUS(s, DateTimeOption.DateOnly, true);
        }
        catch (ConversionException e)
        {
            try
            {
                // One final format to try - handles "2-3-01", "02-03-01", "02-03-2001", etc
                DateFormat format = new SimpleDateFormat("M-d-yy");
                format.setLenient(false);
                return format.parse(s).getTime();
            }
            catch (ParseException ignored) {}

            throw e;
        }
    }


    public static long parseTime(String s)
    {
        // strip off trailing decimal :00:00.000
        int ms = 0;
        int len = s.length();
        int period = s.lastIndexOf('.');
        if (period > 6 && period >= len - 4 && period < len - 1 &&
                s.charAt(period - 3) == ':' &&
                s.charAt(period - 6) == ':')
        {
            String m = s.substring(period + 1);
            ms = Integer.parseInt(m);
            if (m.length() == 1)
                ms *= 100;
            else if (m.length() == 2)
                ms *= 10;
            s = s.substring(0, period);
        }
        long time = parseDateTimeUS(s, DateTimeOption.TimeOnly, true);
        return time + ms;
    }


    public static String getStandardDateFormatString()
    {
        return _standardDateFormatString;
    }


    public static String getStandardDateTimeFormatString()
    {
        return _standardDateTimeFormatString;
    }


    // Format current date using standard pattern
    public static String formatDate()
    {
        return formatDate(new Date());
    }


    // Format date using standard date pattern
    public static String formatDate(Date date)
    {
        return formatDateTime(date, _standardDateFormatString);
    }


    // Format current date & time using standard date & time pattern
    public static String formatDateTime()
    {
        return formatDateTime(new Date());
    }


    // Format specified date using standard date & time pattern
    public static String formatDateTime(Date date)
    {
        return formatDateTime(date, _standardDateTimeFormatString);
    }


    // Format date & time using specified pattern
    // Note: This implementation is thread-safe and reuses formatters -- SimpleDateFormat is neither
    public static String formatDateTime(Date date, String pattern)
    {
        if (null == date)
            return null;
        else
            return FastDateFormat.getInstance(pattern).format(date);
    }


    static FastDateFormat jsonDateFormat = FastDateFormat.getInstance(JSONObject.JAVASCRIPT_DATE_FORMAT);

    public static String formatJsonDateTime(Date date)
    {
        return jsonDateFormat.format(date);
    }

    public static long parseJsonDateTime(String s)
    {
        try
        {
            return new SimpleDateFormat(JSONObject.JAVASCRIPT_DATE_FORMAT).parse(s).getTime();
        }
        catch (ParseException x)
        {
            throw new ConversionException(x);
        }
    }


    private static class _duration
    {
        int year = -1;
        int month = -1;
        int day = -1;
        int hour = -1;
        int min = -1;
        double sec = -1;
    }


    public static _duration _parseDuration(String s)
    {
        boolean period = false;
        boolean monthInPeriod = false;
        int year = -1;
        int month = -1;
        int day = -1;
        boolean time = false;
        int hour = -1;
        int min = -1;
        double sec = -1;

        int startField = 0;
        int i;
Parse:
        for (i=0 ; i<s.length() ; i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
            case 'P':
                if (i != 0)
                    break Parse;
                period = true;
                startField = i+1;
                break;
            case 'Y': case 'y':
                if (year != -1 || month != -1 || day != -1)
                    break Parse;
                period = true;
                year = Integer.parseInt(s.substring(startField,i));
                startField = i+1;
                break;
            case 'M': case 'm':
                if (!time && month == -1)
                {
                    month = Integer.parseInt(s.substring(startField,i));
                    monthInPeriod = period;
                }
                else
                {
                    if (min != -1 || sec != -1)
                        break Parse;
                    min = Integer.parseInt(s.substring(startField,i));
                }
                startField = i+1;
                break;
            case 'D': case 'd':
                if (day != -1 || hour != -1)
                    break Parse;
                time = true;
                day = Integer.parseInt(s.substring(startField,i));
                startField = i+1;
                break;
            case 'T': case 't':
                if (hour != -1 || min != -1 || sec != -1)
                    break Parse;
                time = true;
                startField = i+1;
                break;
            case 'H': case 'h':
                if (hour != -1 || min != -1)
                    break Parse;
                time = true;
                hour = Integer.parseInt(s.substring(startField,i));
                startField = i+1;
                break;
            case 'S': case 's':
                if (sec != -1 || i != s.length()-1)
                    break Parse;
                sec = Double.parseDouble(s.substring(startField,i));
                startField = i+1;
                break;
            case '0': case '1': case '2': case '3': case '4' : case '5': case '6': case '7': case'8': case '9':
                break;
            case '.':
                if (i == startField)
                    break Parse;
                break;
            default:
                break Parse;
            }
        }

        if (i < s.length())
            throw new ConversionException("Illegal duration: " + s);

        // check if month should have been minute
        // can only happen if there is no day or hour specified
        if ((month != -1 && min == -1) && !monthInPeriod && !time)
        {
            assert -1 == day && -1 == hour;
            min = month;
            month = -1;
        }

        _duration d = new _duration();
        d.year = Math.max(0,year);
        d.month = Math.max(0,month);
        d.day = Math.max(0,day);
        d.hour = Math.max(0,hour);
        d.min = Math.max(0,min);
        d.sec = Math.max(0,sec);
        return d;
    }


    public static long parseDuration(String s)
    {
        _duration d = _parseDuration(s);

        if (d.year != 0 || d.month != 0)
            throw new ConversionException("Year and month not supported: " + s);

        assert d.day >= 0 || d.day == -1;
        assert d.hour >= 0 || d.hour == -1;
        assert d.min >= 0 || d.min == -1;
        assert d.sec >= 0 || d.sec == -1;
        return  makeDuration(d.day, d.hour, d.min, d.sec);
    }


    /** handles year, and month **/
    private static long _addDuration(long start, String s, int sign)
    {
        _duration d = _parseDuration(s);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(start);

        if (d.year > 0)
            calendar.add(Calendar.YEAR, d.year*sign);
        if (d.month > 0)
            calendar.add(Calendar.MONTH, d.month*sign);
        if (d.day > 0)
            calendar.add(Calendar.DAY_OF_MONTH, d.day*sign);
        if (d.hour > 0)
            calendar.add(Calendar.HOUR_OF_DAY, d.hour*sign);
        if (d.min > 0)
            calendar.add(Calendar.MINUTE, d.min*sign);
        if (d.sec > 0)
            calendar.add(Calendar.MILLISECOND, (int)(1000*d.sec*sign));

        return calendar.getTimeInMillis();
    }


    private static long makeDuration(int day, int hour, int min, double sec)
    {
        return  day * DateUtils.MILLIS_PER_DAY +
                hour * DateUtils.MILLIS_PER_HOUR +
                min * DateUtils.MILLIS_PER_MINUTE +
                (int)(sec * DateUtils.MILLIS_PER_SECOND);
    }


    public static long addDuration(long d, String s)
    {
        return _addDuration(d, s, 1);
    }


    public static long subtractDuration(long d, String s)
    {
        return _addDuration(d, s, -1);
    }


    // how ISO8601 do we want to be (v. readable)
    public static String formatDuration(long duration)
    {
        if (duration < 0)
            throw new IllegalArgumentException("negative durations not supported");
        if (duration == 0)
            return "0s";

        StringBuilder s = new StringBuilder();
        long r = duration;

        long day = r / DateUtils.MILLIS_PER_DAY;
        r = r % DateUtils.MILLIS_PER_DAY;
        if (day != 0)
            s.append(String.valueOf(day)).append("d");
        if (r == 0)
            return s.toString();

        long hour = r / DateUtils.MILLIS_PER_HOUR;
        r = r % DateUtils.MILLIS_PER_HOUR;
        if (hour != 0 || s.length() > 0)
            s.append(String.valueOf(hour)).append("h");
        if (r == 0)
            return s.toString();

        long min = r / DateUtils.MILLIS_PER_MINUTE;
        r = r % DateUtils.MILLIS_PER_MINUTE;
        if (min != 0 || s.length() > 0)
            s.append(String.valueOf(min)).append("m");
        if (r == 0)
            return s.toString();

        long sec = r / DateUtils.MILLIS_PER_SECOND;
        long ms = r % DateUtils.MILLIS_PER_SECOND;

        s.append(String.valueOf(sec));
        if (ms != 0)
        {
            s.append('.');
            s.append(ms / 100);
            s.append((ms % 100) / 10);
            s.append(ms % 10);
        }
        s.append("s");
        return s.toString();
    }



    public static class TestCase extends Assert
    {
        void assertIllegalDate(String s)
        {
            try
            {
                parseDate(s);
                fail("Not a legal date: " + s);
            }
            catch (ConversionException x)
            {
                return;
            }
        }

        void assertIllegalDateTime(String s)
        {
            try
            {
                parseDateTime(s);
                fail("Not a legal datetime: " + s);
            }
            catch (ConversionException x)
            {
                return;
            }
        }

        void assertIllegalTime(String s)
        {
            try
            {
                parseTime(s);
                fail("Not a legal datetime: " + s);
            }
            catch (ConversionException x)
            {
                return;
            }
        }


        @Test
        public void testDateTime()
        {
            long datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 04:05:06").getTime();
            long dateExpected = java.sql.Date.valueOf("2001-02-03").getTime();

            // DateTime with time
            Date dt = new Date(datetimeExpected);
            assertEquals(datetimeExpected, DateUtil.parseDateTime(dt.toString()));
            assertEquals(datetimeExpected, DateUtil.parseDateTime(dt.toGMTString()));
            assertEquals(datetimeExpected, DateUtil.parseDateTime(dt.toLocaleString()));
            assertEquals(datetimeExpected, DateUtil.parseDateTime(ConvertUtils.convert(dt)));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03 04:05:06"));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03T04:05:06"));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2/3/01 4:05:06"));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2/3/2001 4:05:06"));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2/3/2001 4:05:06.000"));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("03-FEB-2001-04:05:06")); // FCS dates
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2-03-2001 4:05:06"));
            // illegal
            assertIllegalDateTime("2");
            assertIllegalDateTime("2/3");

            // DateTime without time
            Date d = new Date(dateExpected);
            assertEquals(dateExpected, DateUtil.parseDateTime(d.toString()));
            assertEquals(dateExpected, DateUtil.parseDateTime(d.toGMTString()));
            assertEquals(dateExpected, DateUtil.parseDateTime(d.toLocaleString()));
            assertEquals(dateExpected, DateUtil.parseDateTime(ConvertUtils.convert(d)));
            assertEquals(dateExpected, DateUtil.parseDateTime("2001-02-03"));
            assertEquals(dateExpected, DateUtil.parseDateTime("2001-2-03"));
            assertEquals(dateExpected, DateUtil.parseDateTime("2/3/01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3-Feb-01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3Feb01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3Feb2001"));
            assertEquals(dateExpected, DateUtil.parseDateTime("03Feb01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("03Feb2001"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3 Feb 01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3 Feb 2001"));
            assertEquals(dateExpected, DateUtil.parseDateTime("February 3, 2001"));

            // some zero testing
            datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 00:00:00.000").getTime();
            assertEquals(datetimeExpected, parseDateTime("2001-02-03 00:00:00.000"));
            assertEquals(datetimeExpected, parseDateTime("2001-02-03 00:00:00"));
            assertEquals(datetimeExpected, parseDateTime("2001-02-03 00:00"));
            assertEquals(datetimeExpected, parseDateTime("2001-02-03"));

            // dd/mmm/yy testing
            assertEquals(parseDateTime("3/Feb/01"), dateExpected);
            assertEquals(parseDateTime("3/FEB/01"), dateExpected);
            assertEquals(parseDateTime("3/FeB/2001"), dateExpected);
            assertEquals(parseDateTime("03/feb/2001"), dateExpected);
            assertEquals(parseDateTime("03/FEB/2001"), dateExpected);
            assertIllegalDateTime("Jan/Feb/2001");
        }


        @Test
        public void testDate()
        {
            long dateExpected = java.sql.Date.valueOf("2001-02-03").getTime();

            // Date
            assertEquals(dateExpected, DateUtil.parseDateTime("2001-02-03"));
            assertEquals(dateExpected, DateUtil.parseDateTime("2001-2-03"));
            assertEquals(dateExpected, DateUtil.parseDate("2/3/01"));
            assertEquals(dateExpected, DateUtil.parseDate("2-3-01"));
            assertEquals(dateExpected, DateUtil.parseDate("2-3-2001"));
            assertEquals(dateExpected, DateUtil.parseDate("2-03-2001"));
            assertEquals(dateExpected, DateUtil.parseDate("02-3-2001"));
            assertEquals(dateExpected, DateUtil.parseDate("2/3/2001"));
            assertEquals(dateExpected, DateUtil.parseDate("02/03/01"));
            assertEquals(dateExpected, DateUtil.parseDate("02-03-01"));
            assertEquals(dateExpected, DateUtil.parseDate("02/03/2001"));
            assertEquals(dateExpected, DateUtil.parseDate("02-03-2001"));
            assertEquals(dateExpected, DateUtil.parseDate("3-Feb-01"));
            assertEquals(dateExpected, DateUtil.parseDate("3Feb01"));
            assertEquals(dateExpected, DateUtil.parseDate("3Feb2001"));
            assertEquals(dateExpected, DateUtil.parseDate("03Feb01"));
            assertEquals(dateExpected, DateUtil.parseDate("03Feb2001"));
            assertEquals(dateExpected, DateUtil.parseDate("3 Feb 01"));
            assertEquals(dateExpected, DateUtil.parseDate("3 Feb 2001"));
            assertEquals(dateExpected, DateUtil.parseDate("Feb 03 2001"));
            assertEquals(dateExpected, DateUtil.parseDate("February 3, 2001"));
            assertIllegalDate("2");
            assertIllegalDate("2/3");
            assertIllegalDate("Feb/Mar/2001");
            assertIllegalDate("2/3/2001 0:00:00");
            assertIllegalDate("2/3/2001 12:00am");
            assertIllegalDate("2/3/2001 12:00pm");
        }


        @Test 
        public void testTime()
        {
            long hrs12 = TimeUnit.HOURS.toMillis(12);
            long timeSecExpected = TimeUnit.HOURS.toMillis(4) + TimeUnit.MINUTES.toMillis(5) + TimeUnit.SECONDS.toMillis(6);
            long timeMinExpected = TimeUnit.HOURS.toMillis(4) + TimeUnit.MINUTES.toMillis(5);
            long timeHrExpected = TimeUnit.HOURS.toMillis(4);
            assertEquals(timeHrExpected, parseTime("4"));
            assertEquals(timeHrExpected, parseTime("4 am"));
            assertEquals(timeHrExpected, parseTime("4AM"));
            assertEquals(timeHrExpected + hrs12, parseTime("4pm"));
            assertEquals(timeHrExpected + hrs12, parseTime("16"));
            assertEquals(timeHrExpected + hrs12, parseTime("16:00:00"));
            assertEquals(timeMinExpected, parseTime("4:05"));
            assertEquals(timeSecExpected, parseTime("4:05:06"));
            assertEquals(timeSecExpected, parseTime("4:05:06 am"));
            assertEquals(timeSecExpected, parseTime("4:05:06AM"));
            assertEquals(timeSecExpected, parseTime("4:05:06.0"));
            assertEquals(timeSecExpected, parseTime("4:05:06.00"));
            assertEquals(timeSecExpected, parseTime("4:05:06.000"));
            assertEquals(timeSecExpected+7, parseTime("4:05:06.007"));
            assertEquals(timeSecExpected+70, parseTime("4:05:06.07"));
            assertEquals(timeSecExpected+700, parseTime("4:05:06.7"));
            assertIllegalTime("2/3/2001 4:05:06");
            assertIllegalTime("4/05:06");
            assertIllegalTime("4:05/06");
            assertIllegalTime("28:05:06");
            assertIllegalTime("4:65:06");
            assertIllegalTime("4:65:66");
            assertIllegalTime("4.0");
        }


        @Test
        public void testTimezone()
        {
            // UNDONE
        }


        @Test
        public void testFormat()
        {
            long l = System.currentTimeMillis();
            for (int i=0 ; i<24 ; i++)
            {
                String ts = new java.sql.Timestamp(l).toString();
                String iso = toISO(l);
                assertEquals(ts.substring(0,20),iso.substring(0,20));
                l += 60*60*1000;
            }

            l = parseDateTime("1999-12-31 23:59:59.999");
            assertEquals(toISO(l, false).length(), "1999-12-31 23:59:59.999".length());
            l -= l % 1000;
            assertEquals(toISO(l, false).length(), "1999-12-31 23:59:59".length());
            l -= l % (60 * 1000);
            assertEquals(toISO(l, false).length(), "1999-12-31 23:59".length());
            l -= l % (60 * 60 * 1000);
            assertEquals(toISO(l, false).length(), "1999-12-31 23:00".length());
            Calendar c = newCalendar(l);
            c.set(Calendar.HOUR,0);
            l = c.getTimeInMillis();
            assertEquals(toISO(l, false).length(), "1999-12-31".length());
        }


        @Test
        public void testDuration()
        {
            assertEquals(61500L, makeDuration(0,0,1,1.5));
            assertEquals(makeDuration(0,0,0,5), parseDuration("5s"));
            assertEquals(makeDuration(0,0,0,5.001), parseDuration("5.001s"));
            assertEquals(makeDuration(0,0,1,0), parseDuration("1m"));
            assertEquals(makeDuration(0,0,1,0), parseDuration("60s"));
            assertEquals(makeDuration(0,0,2,20), parseDuration("2m20s"));
            assertEquals(makeDuration(0,0,2,20), parseDuration("1m80s"));
            assertEquals(makeDuration(0,1,2,3), parseDuration("1h2m3s"));
            assertEquals(makeDuration(1,2,3,4), parseDuration("1d2h3m4s"));
            assertEquals(makeDuration(1,2,3,4.5), parseDuration("1d2h3m4.500s"));
            try
            {
                parseDuration("1m2d3h");
                assertFalse("unsupported conversion", true);
            }
            catch (ConversionException x) {;}

            // one non-zero field
            assertEquals("1s", formatDuration(makeDuration(0,0,0,1)));
            assertEquals("1m", formatDuration(makeDuration(0,0,1,0)));
            assertEquals("1h", formatDuration(makeDuration(0,1,0,0)));
            assertEquals("1d", formatDuration(makeDuration(1,0,0,0)));

            // one zero field
            assertEquals("2h3m4s", formatDuration(makeDuration(0,2,3,4)));
            assertEquals("1d0h3m4s", formatDuration(makeDuration(1,0,3,4)));
            assertEquals("1d2h0m4s", formatDuration(makeDuration(1,2,0,4)));
            assertEquals("1d2h3m", formatDuration(makeDuration(1,2,3,0)));

            // misc and ms
            assertEquals("1d2h3m4s", formatDuration(makeDuration(1,2,3,4)));
            assertEquals("1h2m3.010s", formatDuration(makeDuration(0,1,2,3.010)));
            assertEquals("1h0m0.010s", formatDuration(makeDuration(0,1,0,0.010)));

            long start = parseStringJDBC("2010-01-31");
            assertEquals(parseDateTime("2011-01-31"), addDuration(start,"1y"));
            assertEquals(parseDateTime("2010-02-28"), addDuration(start,"P1m"));
            assertEquals(parseDateTime("2010-02-28"), addDuration(start,"1m0d"));
            assertEquals(parseDateTime("2010-02-28"), addDuration(start,"0y1m"));
            assertEquals(parseDateTime("2010-02-01"), addDuration(start,"1d"));
            assertEquals(parseDateTime("2010-01-31 01:00:00"), addDuration(start,"1h"));
            assertEquals(parseDateTime("2010-01-31 00:01:00"), addDuration(start,"1m"));
            assertEquals(parseDateTime("2010-01-31 00:01:00"), addDuration(start,"PT1m"));
            assertEquals(parseDateTime("2010-01-31 00:00:01"), addDuration(start,"1s"));

            assertEquals(parseDateTime("2009-01-31"), subtractDuration(start,"1y"));
            assertEquals(parseDateTime("2009-12-31"), subtractDuration(start,"P1m"));
            assertEquals(parseDateTime("2009-12-31"), subtractDuration(start,"1m0d"));
            assertEquals(parseDateTime("2009-12-31"), subtractDuration(start,"0y1m"));
            assertEquals(parseDateTime("2010-01-30"), subtractDuration(start,"1d"));
            assertEquals(parseDateTime("2010-01-30 23:00:00"), subtractDuration(start,"1h"));
            assertEquals(parseDateTime("2010-01-30 23:59:00"), subtractDuration(start,"1m"));
            assertEquals(parseDateTime("2010-01-30 23:59:00"), subtractDuration(start,"PT1m"));
            assertEquals(parseDateTime("2010-01-30 23:59:59"), subtractDuration(start,"1s"));
        }

        @Test
        public void testJSON()
        {
            Date datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 04:05:06");
            long msExpected = java.sql.Timestamp.valueOf("2001-02-03 04:05:06").getTime();

            assertEquals(msExpected, parseJsonDateTime(formatJsonDateTime(datetimeExpected)));
            assertEquals(msExpected, parseDateTime(formatJsonDateTime(datetimeExpected)));

            for (Locale l : DateFormat.getAvailableLocales())
            {
                try
                {
                    SimpleDateFormat f = new SimpleDateFormat(JSONObject.JAVASCRIPT_DATE_FORMAT, l);
                    String s = f.format(datetimeExpected);
                    assertEquals(l.getDisplayName(), msExpected, f.parse(s).getTime());
                }
                catch (ParseException x)
                {
                    fail(" locale test failed: " + l.getDisplayName());
                }
                catch (ConversionException x)
                {
                    fail(" locale test failed: " + l.getDisplayName());
                }
            }
        }
    }
}
