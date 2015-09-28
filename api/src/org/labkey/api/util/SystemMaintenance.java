/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.StatusAppender;
import org.labkey.api.action.StatusReportingRunnable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewServlet;

import javax.servlet.ServletContextEvent;
import java.io.File;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * User: adam
 * Date: Sep 29, 2006
 * Time: 2:18:53 PM
 */
public class SystemMaintenance extends TimerTask implements ShutdownListener, StatusReportingRunnable
{
    private static final Object _timerLock = new Object();
    private static final List<MaintenanceTask> _tasks = new CopyOnWriteArrayList<>();

    private static Timer _timer = null;
    private static SystemMaintenance _timerTask = null;

    private volatile static boolean _timerDisabled = false;

    private final @Nullable String _taskName;
    private final Logger _log;
    private final boolean _manualInvocation;
    private final AtomicBoolean _taskRunning = new AtomicBoolean(false);
    private final @Nullable User _user;

    private StatusAppender _appender;

    // Used by the standard timer invoked maintenance
    public SystemMaintenance()
    {
        this(false, null, null);
    }

    // Used for manual invocation of system maintenance tasks. Creates a logger that can report status back to request threads,
    // allows invocation of a single task, kicks off the pipeline job with the initiating user, and skips time checks.
    public SystemMaintenance(@Nullable String taskName, User user)
    {
        this(true, taskName, user);
        _appender = new StatusAppender();
        _log.addAppender(_appender);
    }

    private SystemMaintenance(boolean manualInvocation, @Nullable String taskName, @Nullable User user)
    {
        _log = Logger.getLogger(SystemMaintenance.class);
        _taskName = taskName;
        _user = user;
        _manualInvocation = manualInvocation;
    }

    public static void setTimer()
    {
        synchronized(_timerLock)
        {
            resetTimer();

            if (_timerDisabled)
                return;

            // Create daemon timer for daily maintenance task
            _timer = new Timer("SystemMaintenance", true);

            // Timer has a single task that simply kicks off a pipeline job that performs all the maintenance tasks. This ensures that
            // the maintenance tasks run serially and will allow (if we need to in the future) controlling the ordering (for example,
            // purge data first, then compact the database)
            _timerTask = new SystemMaintenance();
            ContextListener.addShutdownListener(_timerTask);
            _timer.scheduleAtFixedRate(_timerTask, getNextSystemMaintenanceTime(), DateUtils.MILLIS_PER_DAY);
        }
    }

    // Returns null if time can't be parsed in h:mm a or H:mm format
    public static @Nullable Date parseSystemMaintenanceTime(String time)
    {
        Date date = null;

        try
        {
            date = DateUtil.parseDateTime(time, "h:mm a");
        }
        catch(ParseException e)
        {
        }

        if (null == date)
        {
            try
            {
                return DateUtil.parseDateTime(time, "H:mm");
            }
            catch(ParseException e)
            {
            }
        }

        return date;
    }

    // Returns null if time is null
    public static String formatSystemMaintenanceTime(Date time)
    {
        return DateUtil.formatDateTime(time, "H:mm");
    }

    private static Date getNextSystemMaintenanceTime()
    {
        Calendar time = Calendar.getInstance();
        Date mt = getProperties().getSystemMaintenanceTime();
        time.setTime(mt);

        Calendar nextTime = Calendar.getInstance();

        nextTime.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
        nextTime.set(Calendar.MINUTE,  time.get(Calendar.MINUTE));
        nextTime.set(Calendar.SECOND, 0);
        nextTime.set(Calendar.MILLISECOND, 0);

        // If we're about to schedule this for the past then roll up to tomorrow
        if (nextTime.before(Calendar.getInstance()))
            nextTime.add(Calendar.DATE, 1);

        return nextTime.getTime();
    }

    private static void resetTimer()
    {
        if (null != _timer)
            _timer.cancel();

        if (null != _timerTask)
            ContextListener.removeShutdownListener(_timerTask);
    }

    public static void addTask(MaintenanceTask task)
    {
        if (task.getName().contains(","))
            throw new IllegalStateException("System maintenance task " + task.getClass().getSimpleName() + " has a comma in its name (" + task.getName() + ")");
        _tasks.add(task);
    }

    public static List<MaintenanceTask> getTasks()
    {
        return _tasks;
    }

    public static boolean isTimerDisabled()
    {
        return _timerDisabled;
    }

    public static void setTimeDisabled(boolean disable)
    {
        _timerDisabled = disable;
    }

    private final static String SET_NAME = "SystemMaintenance";
    private final static String TIME_PROPERTY_NAME = "MaintenanceTime";
    private final static String DISABLED_TASKS_PROPERTY_NAME = "DisabledTasks";

    public static SystemMaintenanceProperties getProperties()
    {
        Map<String, String> props = PropertyManager.getProperties(SET_NAME);

        return new SystemMaintenanceProperties(props);
    }

    public static void setProperties(Set<String> enabledTasks, String time)
    {
        PropertyManager.PropertyMap writableProps = PropertyManager.getWritableProperties(SET_NAME, true);

        Set<String> enabled = new HashSet<>(enabledTasks);
        Set<String> disabled = getTasks().stream()
            .filter(task -> task.canDisable() && !enabled.contains(task.getName()))
            .map(MaintenanceTask::getName)
            .collect(Collectors.toSet());

        writableProps.put(TIME_PROPERTY_NAME, time);
        writableProps.put(DISABLED_TASKS_PROPERTY_NAME, StringUtils.join(disabled, ","));

        writableProps.save();
        setTimer();
    }

    public static class SystemMaintenanceProperties
    {
        private Date _systemMaintenanceTime;
        private Set<String> _disabledTasks;

        private SystemMaintenanceProperties(Map<String, String> props)
        {
            Date time = SystemMaintenance.parseSystemMaintenanceTime(props.get(TIME_PROPERTY_NAME));
            _systemMaintenanceTime = (null == time ? SystemMaintenance.parseSystemMaintenanceTime("2:00") : time);

            String disabled = props.get(DISABLED_TASKS_PROPERTY_NAME);
            _disabledTasks = (null == disabled ? Collections.<String>emptySet() : new HashSet<>(Arrays.asList(disabled.split(","))));
        }

        public @NotNull Date getSystemMaintenanceTime()
        {
            return _systemMaintenanceTime;
        }

        public @NotNull Set<String> getDisabledTasks()
        {
            return _disabledTasks;
        }
    }

    // Start a separate thread to do all the work
    public void run()
    {
        if (!_manualInvocation && (System.currentTimeMillis() - scheduledExecutionTime()) > 2 * DateUtils.MILLIS_PER_HOUR)
        {
            _log.warn("Skipping system maintenance since it's two hours past the scheduled time");
        }
        else
        {
            _taskRunning.set(true);
            Set<String> disabledTasks = getProperties().getDisabledTasks();
            Collection<MaintenanceTask> tasksToRun = new LinkedList<>();

            for (MaintenanceTask task : _tasks)
            {
                if (null != _taskName && !_taskName.isEmpty())
                {
                    // If _taskName is set, then admin has invoked a single task from the UI... skip all the others
                    if (task.getName().equals(_taskName))
                    {
                        tasksToRun.add(task);
                        break;
                    }
                }
                else
                {
                    // If the task can't be disabled or isn't disabled now then include it
                    if (!task.canDisable() || !disabledTasks.contains(task.getName()))
                    {
                        tasksToRun.add(task);
                    }
                }
            }

            Container c = ContainerManager.getRoot();
            ViewBackgroundInfo vbi = new ViewBackgroundInfo(c, _user, null);
            PipeRoot root = PipelineService.get().findPipelineRoot(c);

            try
            {
                PipelineJob job = new MaintenancePipelineJob(_log, vbi, root, tasksToRun, _taskRunning);
                PipelineService.get().queueJob(job);
            }
            catch (PipelineValidationException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean isRunning()
    {
        return _taskRunning.get();
    }

    @Override
    public Collection<String> getStatus(@Nullable Integer offset)
    {
        return _appender.getStatus(offset); 
    }

    @Override
    public String getName()
    {
        return "System Maintenance";
    }

    public void shutdownPre(ServletContextEvent servletContextEvent)
    {
        
    }

    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        synchronized(_timerLock)
        {
            resetTimer();
        }
    }

    // Runs each MaintenanceTask in order
    private static class MaintenancePipelineJob extends PipelineJob
    {
        private final Collection<MaintenanceTask> _tasks;
        private final transient Logger _log;
        private final AtomicBoolean _taskRunning;

        public MaintenancePipelineJob(Logger log, ViewBackgroundInfo info, PipeRoot pipeRoot, Collection<MaintenanceTask> tasks, AtomicBoolean taskRunning)
        {
            super(null, info, pipeRoot);
            setLogFile(new File(pipeRoot.getRootPath(), FileUtil.makeFileNameWithTimestamp("system_maintenance", "log")));
            _tasks = tasks;
            _log = log;
            _taskRunning = taskRunning;
        }

        @Override
        public URLHelper getStatusHref()
        {
            return null;
        }

        @Override
        public String getDescription()
        {
            return "System Maintenance";
        }

        @Override
        public void run()
        {
            info("System maintenance started");

            for (MaintenanceTask task : _tasks)
            {
                setStatus("Running " + task.getName());
                if (ViewServlet.isShuttingDown())
                {
                    info("System maintenance is stopping due to server shut down");
                    break;
                }

                info(task.getDescription() + " started");
                long start = System.currentTimeMillis();

                try
                {
                    task.run();
                }
                catch (Exception e)
                {
                    // Log if one of these tasks throws... but continue with other tasks
                    ExceptionUtil.logExceptionToMothership(null, e);
                }

                long elapsed = System.currentTimeMillis() - start;
                info(task.getDescription() + " complete; elapsed time " + elapsed/1000 + " seconds");
            }

            info("System maintenance complete");
            setStatus(TaskStatus.complete);
            _taskRunning.set(false);
        }

        @Override
        public void info(String message)
        {
            // Log to both passed in Logger and pipeline log
            _log.info(message);
            super.info(message);
        }
    }

    public interface MaintenanceTask extends Runnable
    {
        // Description used in logging and UI
        String getDescription();

        // Short name used in forms and to persist disabled settings
        // Task name must be unique and cannot contain a comma
        String getName();

        // Can this task be disabled?
        boolean canDisable();

        // Hide this from the Admin page (because it will be controlled from elsewhere)
        boolean hideFromAdminPage();
    }
}
