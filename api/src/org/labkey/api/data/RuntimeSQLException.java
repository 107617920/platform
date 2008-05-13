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

package org.labkey.api.data;

import java.sql.SQLException;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * User: mbellew
 * Date: Mar 23, 2005
 * Time: 9:58:08 AM
 */
public class RuntimeSQLException extends RuntimeException
{
    // don't want to use cause, I want to impersonate the cause
    SQLException sqlx;

    public RuntimeSQLException(SQLException x)
    {
        sqlx = x;
    }

    public String getMessage()
    {
        return sqlx.getMessage();
    }

    public String getLocalizedMessage()
    {
        return sqlx.getLocalizedMessage();
    }

    public Throwable getCause()
    {
        return sqlx.getCause();
    }

    public synchronized Throwable initCause(Throwable cause)
    {
        return sqlx.initCause(cause);
    }

    public String toString()
    {
        return sqlx.toString();
    }

    public void printStackTrace()
    {
        sqlx.printStackTrace();
    }

    public void printStackTrace(PrintStream s)
    {
        sqlx.printStackTrace(s);
    }

    public void printStackTrace(PrintWriter s)
    {
        sqlx.printStackTrace(s);
    }

    public synchronized Throwable fillInStackTrace()
    {
        return super.fillInStackTrace();
    }

    public StackTraceElement[] getStackTrace()
    {
        return sqlx.getStackTrace();
    }

    public void setStackTrace(StackTraceElement[] stackTrace)
    {
        sqlx.setStackTrace(stackTrace);
    }
}
