/*
 * Copyright (c) 2005-2014 LabKey Corporation
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

import javax.servlet.ServletContextEvent;

/**
 * User: brittp
 * Date: Dec 3, 2005
 * Time: 5:15:42 PM
 */
public interface ShutdownListener
{
    /**
     * called first, should be used only for non-blocking operations (set _shuttingDown=true, interrupt threads)
     * also possible to launch an async shutdown task here!
     */
    void shutdownPre(ServletContextEvent servletContextEvent);

    /**
     * perform shutdown tasks
     */
    void shutdownStarted(ServletContextEvent servletContextEvent);
}
