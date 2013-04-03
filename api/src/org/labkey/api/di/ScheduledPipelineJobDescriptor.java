/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.di;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.writer.ContainerUser;
import org.quartz.ScheduleBuilder;

import java.util.concurrent.Callable;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-03-18
 * Time: 3:21 PM
 */

public interface ScheduledPipelineJobDescriptor<C extends ContainerUser>
{
    String getId();     // globally unique id (perhaps a path)
    String getName();
    String getDescription();
    String getModuleName();
    int getVersion();

    public ScheduleBuilder getScheduleBuilder();
    public String getScheduleDescription();

    // these are used to create a job that can be scheduled in Quartz
    Class<? extends org.quartz.Job> getJobClass();
    C getJobContext(Container c, User user);

    // these methods actually implement the Job
    Callable<Boolean> getChecker(C context);
    PipelineJob getPipelineJob(C context) throws PipelineJobException;
}
