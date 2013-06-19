/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.api.action;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* User: adam
* Date: Jun 27, 2010
* Time: 12:17:25 AM
*/
// An appender that can be added to any log4j Logger to help accumulate and report status; meant to be used within a
// StatusReportingRunnable to report status to StatusReportingRunnableAction.  The appender will accumulate and report
// all log statements sent to the Logger.  If you add this appender, you will most likely want to create a new Logger
// on each invocation of your task, otherwise the appender will accumulate status across multiple invocations.
public class StatusAppender extends AppenderSkeleton
{
    private final List<String> _status = Collections.synchronizedList(new ArrayList<String>());

    @Override
    protected void append(LoggingEvent event)
    {
        _status.add(event.getMessage().toString());
    }

    @Override
    public void close()
    {
    }

    @Override
    public boolean requiresLayout()
    {
        return false;
    }

    public Collection<String> getStatus(@Nullable Integer offset)
    {
        int firstIndex = 0;

        // Synchronize since we're iterating the sublist and to ensure size() is consistent
        synchronized (_status)
        {
            if (null != offset && offset.intValue() > 0)
                firstIndex = Math.min(_status.size(), offset.intValue());

            return new LinkedList<>(_status.subList(firstIndex, _status.size()));
        }
    }
}
