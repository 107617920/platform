/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.*;
import org.apache.log4j.spi.HierarchyEventListener;
import org.apache.log4j.spi.LoggerFactory;
import org.apache.log4j.spi.LoggerRepository;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.security.User;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.*;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

abstract public class PipelineJob extends Job implements Serializable
{
    public static final FileType FT_LOG = new FileType(".log");

    private static Logger _log = Logger.getLogger(PipelineJob.class);
    // Send start/stop messages to a separate logger because the default logger for this class is set to
    // only write ERROR level events to the system log
    private static Logger _logJobStopStart = Logger.getLogger(Job.class);

    public static Logger getJobLogger(Class clazz)
    {
        return Logger.getLogger(PipelineJob.class.getName() + ".." + clazz.getName());
    }

    public RecordedActionSet getActionSet()
    {
        return _actionSet;
    }

    /**
     * Clear out the set of recorded actions
     * @param run run that represents the previous set of recorded actions
     */
    public void clearActionSet(ExpRun run)
    {
        _actionSet = new RecordedActionSet();
    }

    public enum TaskStatus
    {
        waiting
        {
            public boolean isActive() { return true; }
        },
        running
        {
            public boolean isActive() { return true; }
        },
        complete
        {
            public boolean isActive() { return false; }
        },
        error
        {
            public boolean isActive() { return false; }
        };

        public abstract boolean isActive();
    }
    
    /**
     * <code>Task</code> implements a runnable to complete a part of the
     * processing associated with a particular <code>PipelineJob</code>.
     */
    abstract static public class Task<FactoryType extends TaskFactory>
    {
        private PipelineJob _job;
        protected FactoryType _factory;

        public Task(FactoryType factory, PipelineJob job)
        {
            _job = job;
            _factory = factory;
        }

        public PipelineJob getJob()
        {
            return _job;
        }

        public abstract RecordedActionSet run() throws PipelineJobException;
    }

    /*
     * Status strings
     */
    public static final String WAITING_STATUS = TaskStatus.waiting.toString().toUpperCase();
    public static final String COMPLETE_STATUS = TaskStatus.complete.toString().toUpperCase();
    public static final String ERROR_STATUS = TaskStatus.error.toString().toUpperCase();
    public static final String CANCELLED_STATUS = "CANCELLED";
    public static final String INTERRUPTED_STATUS = "INTERRUPTED";
    public static final String SPLIT_STATUS = "SPLIT WAITING";

    /*
     * JMS message header names
     */
    private static String HEADER_PREFIX = "LABKEY_";
    public static final String LABKEY_JOBTYPE_PROPERTY = HEADER_PREFIX + "JOBTYPE";
    public static final String LABKEY_JOBID_PROPERTY = HEADER_PREFIX + "JOBID";
    public static final String LABKEY_CONTAINERID_PROPERTY = HEADER_PREFIX + "CONTAINERID";
    public static final String LABKEY_TASKPIPELINE_PROPERTY = HEADER_PREFIX + "TASKPIPELINE";
    public static final String LABKEY_TASKID_PROPERTY = HEADER_PREFIX + "TASKID";
    public static final String LABKEY_TASKSTATUS_PROPERTY = HEADER_PREFIX + "TASKSTATUS";

    private String _provider;
    private ViewBackgroundInfo _info;
    private String _jobGUID;
    private String _parentGUID;
    private TaskId _activeTaskId;
    private TaskStatus _activeTaskStatus;
    private int _activeTaskRetries;
    private PipeRoot _pipeRoot;
    private File _logFile;
    private boolean _interrupted;
    private boolean _submitted;
    private int _errors;
    private RecordedActionSet _actionSet = new RecordedActionSet();

    private String _loggerLevel = Level.DEBUG.toString();
    protected transient Logger _logger;

    // Don't save these
    private transient boolean _settingStatus;
    private transient PipelineQueue _queue;

    /** Although having a null provider is legal, it is recommended that one be used
     * so that it can respond to events as needed */ 
    public PipelineJob(@Nullable String provider, ViewBackgroundInfo info, PipeRoot root)
    {
        _info = info;
        _provider = provider;
        _jobGUID = GUID.makeGUID();
        _activeTaskStatus = TaskStatus.waiting;

        _pipeRoot = root;

        _actionSet = new RecordedActionSet();
    }

    public PipelineJob(PipelineJob job)
    {
        // Not yet queued
        _queue = null;

        // New ID
        _jobGUID = GUID.makeGUID();

        // Copy everything else
        _info = job._info;
        _provider = job._provider;
        _parentGUID = job._jobGUID;
        _pipeRoot = job._pipeRoot;
        _logFile = job._logFile;
        _interrupted = job._interrupted;
        _submitted = job._submitted;
        _errors = job._errors;
        _loggerLevel = job._loggerLevel;
        _logger = job._logger;

        _activeTaskId = job._activeTaskId;
        _activeTaskStatus = job._activeTaskStatus;

        _actionSet = new RecordedActionSet(job.getActionSet());

    }

    public String getProvider()
    {
        return _provider;
    }

    @Deprecated
    public void setProvider(String provider)
    {
        _provider = provider;
    }

    public int getErrors()
    {
        return _errors;
    }

    public void setErrors(int errors)
    {
        if (errors > 0)
            _activeTaskStatus = TaskStatus.error;
        
        _errors = errors;
    }

    /**
     * This job has been restored from a checkpoint for the purpose of
     * a retry.  Record retry information before it is checkpointed again.
     */
    public void retryUpdate()
    {
        _errors++;
        _activeTaskRetries++;
    }

    public Map<String, String> getParameters()
    {
        return new HashMap<String, String>();
    }

    public String getJobGUID()
    {
        return _jobGUID;
    }

    public String getParentGUID()
    {
        return _parentGUID;
    }

    public TaskId getActiveTaskId()
    {
        return _activeTaskId;
    }

    public boolean setActiveTaskId(TaskId activeTaskId)
    {
        return setActiveTaskId(activeTaskId, true);
    }
    
    public boolean setActiveTaskId(TaskId activeTaskId, boolean updateStatus)
    {
        if (activeTaskId == null || !activeTaskId.equals(_activeTaskId))
        {
            _activeTaskId = activeTaskId;
            _activeTaskRetries = 0;
        }
        if (_activeTaskId == null)
            _activeTaskStatus = TaskStatus.complete;
        else
            _activeTaskStatus = TaskStatus.waiting;

        if (updateStatus)
            return updateStatusForTask();
        return true;
    }

    public TaskStatus getActiveTaskStatus()
    {
        return _activeTaskStatus;
    }

    /** @return whether or not the status was set successfully */
    public boolean setActiveTaskStatus(TaskStatus activeTaskStatus)
    {
        _activeTaskStatus = activeTaskStatus;
        return updateStatusForTask();
    }

    public TaskFactory getActiveTaskFactory()
    {
        if (getActiveTaskId() == null)
            return null;
        
        return PipelineJobService.get().getTaskFactory(getActiveTaskId());
    }

    protected PipeRoot getPipeRoot()
    {
        return _pipeRoot;
    }

    public void setLogFile(File fileLog)
    {
        _logFile = fileLog;
        _logger = null;

        // Intentionally leave any existing log output in the file
    }

    public File getLogFile()
    {
        return _logFile;
    }

    public static File getSerializedFile(File statusFile)
    {
        if (statusFile == null)
        {
            return null;
        }

        String name = statusFile.getName();

        // Assume the status file's extension has a single period (e.g. .status or .log),
        // and remove that extension.
        int index = name.lastIndexOf('.');
        if (index != -1)
        {
            name = name.substring(0, index);
        }
        return new File(statusFile.getParentFile(), name + ".job.xml");
    }

    public static PipelineJob readFromFile(File file) throws IOException
    {
        InputStream fIn = null;
        StringBuilder xml = new StringBuilder();
        try
        {
            fIn = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fIn));
            String line;
            while ((line = reader.readLine()) != null)
            {
                xml.append(line);
            }
        }
        finally
        {
            if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
        }
        return PipelineJobService.get().getJobStore().fromXML(xml.toString());
    }


    public void writeToFile(File file) throws IOException
    {
        File newFile = new File(file.getPath() + ".new");
        File origFile = new File(file.getPath() + ".orig");

        String xml = PipelineJobService.get().getJobStore().toXML(this);

        FileOutputStream fOut = null;
        try
        {
            fOut = new FileOutputStream(newFile);
            PrintWriter writer = new PrintWriter(fOut);
            writer.write(xml);
            writer.flush();
        }
        finally
        {
            if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
        }

        if (NetworkDrive.exists(file))
        {
            file.renameTo(origFile);
            newFile.renameTo(file);
            origFile.delete();
        }
        else
        {
            newFile.renameTo(file);
        }
        PipelineJobService.get().getWorkDirFactory().setPermissions(file);
    }

    public boolean updateStatusForTask()
    {
        TaskFactory factory = getActiveTaskFactory();
        TaskStatus status = getActiveTaskStatus();

        if (factory != null && !TaskStatus.error.equals(status))
            return setStatus(factory.getStatusName() + " " + status.toString().toUpperCase(), null);
        else
            return setStatus(status.toString().toUpperCase(), null);
    }

    public boolean setStatus(String status)
    {
        return setStatus(status, null);
    }

    public boolean setStatus(String status, String info)
    {
        if (_settingStatus)
            return true;
        
        _settingStatus = true;
        try
        {
            return PipelineJobService.get().getStatusWriter().setStatusFile(this, status, info);
        }
        catch (RuntimeException e)
        {
            File f = this.getLogFile();
            error("Failed to set status to '" + status + "' for '" +
                    (f == null ? "" : f.getPath()) + "'.", e);
            throw e;
        }
        catch (Exception e)
        {
            File f = this.getLogFile();
            error("Failed to set status to '" + status + "' for '" +
                    (f == null ? "" : f.getPath()) + "'.", e);
        }
        finally
        {
            _settingStatus = false;
        }
        return false;
    }

    public void restoreQueue(PipelineQueue queue)
    {
        // Recursive split and join combinations may cause the queue
        // to be restored to a job with a queue already.  Would be good
        // to have better safe-guards against double-queueing of jobs.
        if (queue == _queue)
            return;
        if (null != _queue)
            throw new IllegalStateException();
        _queue = queue;
    }
    
    public boolean setQueue(PipelineQueue queue, String initialState)
    {
        restoreQueue(queue);
        
        // Initialize the task pipeline
        if (getTaskPipeline() != null)
        {
            // Save the current job state marshalled to XML, in case of error.
            String xml = PipelineJobService.get().getJobStore().toXML(this);

            // Note runStateMachine returns false, if the job cannot be run locally.
            // The job may still need to be put on a JMS queue for remote processing.
            // Therefore, the return value cannot be used to determine whether the
            // job should be queued.
            runStateMachine();

            // If an error occurred trying to find the first runnable state, then
            // store the original job state to allow retry.
            if (getActiveTaskStatus() == TaskStatus.error)
            {
                try
                {
                    PipelineJobService.get().getJobStore().fromXML(xml).store();
                }
                catch (Exception e)
                {
                    warn("Failed to checkpoint '" + getDescription() + "' job.", e);
                }
                return false;
            }

            // If initialization put this job into a state where it is
            // waiting, then it should not be put on the queue.
            return !isSplitWaiting();
        }
        // Initialize status for non-task pipline jobs.
        else if (_logFile != null)
        {
            setStatus(initialState);
            try
            {
                store();
            }
            catch (Exception e)
            {
                warn("Failed to checkpoint '" + getDescription() + "' job before queuing.", e);
            }
        }

        return true;
    }

    public void clearQueue()
    {
        _queue = null;
    }

    abstract public URLHelper getStatusHref();

    abstract public String getDescription();

    public String toString()
    {
        return super.toString() + " " + StringUtils.trimToEmpty(getDescription());
    }

    public <T> T getJobSupport(Class<T> inter)
    {
        if (inter.isInstance(this))
            return (T) this;
        
        throw new UnsupportedOperationException("Job type " + getClass().getName() +
                " does not implement " + inter.getName());
    }

    /**
     * Override to provide a <code>TaskPipeline</code> with the option of
     * running some tasks remotely. Override the <code>run()</code> function
     * to implement the job as a single monolithic task.
     *
     * @return a task pipeline to run for this job
     */
    public TaskPipeline getTaskPipeline()
    {
        return null;
    }

    public boolean isActiveTaskLocal()
    {
        TaskFactory factory = getActiveTaskFactory();
        return (factory != null &&
                TaskFactory.WEBSERVER.equals(factory.getExecutionLocation()));
    }

    public void runActiveTask() throws IOException, PipelineJobException
    {
        TaskFactory factory = getActiveTaskFactory();
        if (factory == null)
            return;

        if (!factory.isJobComplete(this))
        {
            Task<?> task = factory.createTask(this);
            if (task == null)
                return; // Bad task key.

            if (!setActiveTaskStatus(TaskStatus.running))
            {
                // The user has deleted (cancelled) the job.
                // Throwing this exception will cause the job to go to the ERROR state and stop running
                throw new PipelineJobException("Job no longer in database - aborting");
            }

            WorkDirectory workDirectory = null;
            RecordedActionSet actions;

            boolean success = false;
            try
            {
                _logJobStopStart.info("Starting to run task '" + factory.getId() + "' for job '" + toString() + "' with log file " + getLogFile());
                getLogger().info("Starting to run task '" + factory.getId() + "' at location '" + factory.getExecutionLocation() + "'");
                if (task instanceof WorkDirectoryTask)
                {
                    workDirectory = factory.createWorkDirectory(getJobGUID(), getJobSupport(FileAnalysisJobSupport.class), getLogger());
                    ((WorkDirectoryTask)task).setWorkDirectory(workDirectory);
                }
                actions = task.run();
                success = true;
            }
            finally
            {
                getLogger().info((success ? "Successfully completed" : "Failed to complete") + " task '" + factory.getId() + "'");
                _logJobStopStart.info((success ? "Successfully completed" : "Failed to complete") + " task '" + factory.getId() + "' for job '" + toString() + "' with log file " + getLogFile());
                try
                {
                    if (workDirectory != null)
                    {
                        workDirectory.remove(success);
                        ((WorkDirectoryTask)task).setWorkDirectory(null);
                    }
                }
                catch (IOException e)
                {
                    // Don't let this cleanup error mask an original error that causes the job to fail
                    if (success)
                    {
                        // noinspection ThrowFromFinallyBlock
                        throw e;
                    }
                    else
                    {
                        if (e.getMessage() != null)
                        {
                            error(e.getMessage());
                        }
                        else
                        {
                            error("Failed to clean up work directory after error condition, see full error information below.", e);
                        }
                    }
                }
            }
            _actionSet.add(actions);

            // An error occurred running the task. Do not complete.
            if (TaskStatus.error.equals(getActiveTaskStatus()))
                return;
        }

        setActiveTaskStatus(TaskStatus.complete);
    }

    public boolean runStateMachine()
    {
        TaskPipeline pipeline = getTaskPipeline();

        if (pipeline == null)
        {
            assert false : "Either override getTaskPipeline() or run() for " + getClass();

            // Best we can do is to complete the job.
            setActiveTaskId(null);
            return false;
        }

        TaskId[] progression = pipeline.getTaskProgression();
        int i = 0;
        if (_activeTaskId != null)
        {
            i = indexOfActiveTask(progression);
            if (i == -1)
            {
                error("Active task " + _activeTaskId + " not found in task pipeline.");
                return false;                
            }
        }

        switch (_activeTaskStatus)
        {
            case waiting:
                return findRunnableTask(progression, i);

            case complete:
                // See if the job has already completed.
                if (_activeTaskId == null)
                    return false;
                
                return findRunnableTask(progression, i + 1);

            case error:
                // Make sure the status is in error state, so that any auto-retry that
                // may occur will record the error.  And, if no retry occurs, then this
                // job must be in error state.
                try
                {
                    PipelineJobService.get().getStatusWriter().ensureError(this);
                }
                catch (Exception e)
                {
                    warn("Failed to ensure error status on task error.");
                }

                // Run auto-retry, and retry if appropriate.
                autoRetry();
                return false;
            
            case running:
            default:
                return false;   // Do not run the active task.
        }
    }

    private int indexOfActiveTask(TaskId[] progression)
    {
        for (int i = 0; i < progression.length; i++)
        {
            TaskFactory factory = PipelineJobService.get().getTaskFactory(progression[i]);
            if (factory.getId().equals(_activeTaskId) ||
                    factory.getActiveId(this).equals(_activeTaskId))
                return i;
        }
        return -1;
    }

    private boolean findRunnableTask(TaskId[] progression, int i)
    {
        // Search for next task that is not already complete
        TaskFactory factory = null;
        while (i < progression.length)
        {
            try
            {
                factory = PipelineJobService.get().getTaskFactory(progression[i]);
                // Stop, if this task requires a change in join state
                if ((factory.isJoin() && isSplitJob()) || (!factory.isJoin() && isSplittable()))
                    break;
                // Stop, if this task is part of processing this job, and not complete
                if (factory.isParticipant(this) && !factory.isJobComplete(this))
                    break;
            }
            catch (IOException e)
            {
                error(e.getMessage());
                return false;
            }
            catch (SQLException e)
            {
                error(e.getMessage());
                return false;
            }

            i++;
        }

        if (i < progression.length)
        {
            assert factory != null : "Factory not found.";

            if (factory.isJoin() && isSplitJob())
            {
                setActiveTaskId(factory.getId(), false);   // ID is just a marker for state machine
                join();
                return false;
            }
            else if (!factory.isJoin() && isSplittable())
            {
                setActiveTaskId(factory.getId(), false);   // ID is just a marker for state machine
                split();
                return false;
            }

            // Set next task to be run
            if (!setActiveTaskId(factory.getActiveId(this)))
            {
                return false;
            }

            // If it is local, then it can be run
            return isActiveTaskLocal();
        }
        else
        {
            // Job is complete
            if (isSplitJob())
            {
                setActiveTaskId(null, false);
                join();
            }
            else
            {
                setActiveTaskId(null);
            }
            return false;
        }
    }

    public boolean isAutoRetry()
    {
        TaskFactory factory = getActiveTaskFactory();
        return null != factory && _activeTaskRetries < factory.getAutoRetry() && factory.isAutoRetryEnabled(this);
    }

    public boolean autoRetry()
    {
        try
        {
            if (isAutoRetry())
            {
                info("Attempting to auto-retry");
                PipelineJobService.get().getJobStore().retry(getJobGUID());
                // Retry has been queued
                return true;
            }
        }
        catch (IOException e)
        {
            warn("Failed to start automatic retry.", e);
        }
        catch (SQLException e)
        {
            warn("Failed to start automatic retry.", e);
        }
        return false;
    }

    public void run()
    {
        try
        {
            // The act of queueing the job runs the state machine for the first time.
            do
            {
                try
                {
                    runActiveTask();
                }
                catch (IOException e)
                {
                    error(e.getMessage(), e);
                }
                catch (PipelineJobException e)
                {
                    error(e.getMessage(), e);
                }
            }
            while (runStateMachine());
        }
        catch (RuntimeException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
            // Rethrow to let the standard Mule exception handler fire and deal with the job state
            throw e;
        }
    }

    /**
     * Override and return true for job that may be split.  Also, override
     * the <code>createSplitJobs()</code> method to return the sub-jobs.
     *
     * @return true if the job may be split
     */
    public boolean isSplittable()
    {
        return false;
    }

    /**
     * @return true if this is a split job, as determined by whether it has a parent.
     */
    public boolean isSplitJob()
    {
        return getParentGUID() != null;
    }

    /**
     * @return true if this is a join job waiting for split jobs to complete.
     */
    public boolean isSplitWaiting()
    {
        // Return false, if this job cannot be split.
        if (!isSplittable())
            return false;

        // A join job with an active task that is not a join task,
        // is waiting for a split to complete.
        TaskFactory factory = getActiveTaskFactory();
        return (factory != null && !factory.isJoin());
    }

    /**
     * Override and return instances of sub-jobs for a splittable job.
     *
     * @return sub-jobs requiring separate processing
     */
    public PipelineJob[] createSplitJobs()
    {
        return new PipelineJob[] { this };
    }

    /**
     * Handles merging accumulated changes from split jobs into this job, which
     * is a joined job.
     *
     * @param job the split job that has run to completion
     */
    public void mergeSplitJob(PipelineJob job)
    {
        // Add experiment actions recorded.
        _actionSet.add(job.getActionSet());

        // Add any errors that happened in the split job.
        _errors += job._errors;
    }

    public void store() throws IOException, SQLException
    {
        PipelineJobService.get().getJobStore().storeJob(this);
    }

    private void split()
    {
        try
        {
            PipelineJobService.get().getJobStore().split(this);
        }
        catch (IOException e)
        {
            error(e.getMessage(), e);
        }
        catch (SQLException e)
        {
            error(e.getMessage(), e);            
        }
    }
    
    private void join()
    {
        try
        {
            PipelineJobService.get().getJobStore().join(this);
        }
        catch (IOException e)
        {
            error(e.getMessage(), e);
        }
        catch (SQLException e)
        {
            error(e.getMessage(), e);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Support for running processes
    
    public void runSubProcess(ProcessBuilder pb, File dirWork) throws PipelineJobException
    {
        runSubProcess(pb, dirWork, null, 0);
    }

    public void runSubProcess(ProcessBuilder pb, File dirWork, File outputFile, int logLineInterval)
            throws PipelineJobException
    {
        Process proc;

        String commandName = pb.command().get(0);
        commandName = commandName.substring(
                Math.max(commandName.lastIndexOf('/'), commandName.lastIndexOf('\\')) + 1);
        header(commandName + " output");

        PrintWriter fileWriter = null;
        try
        {
            try
            {
                if(outputFile != null)
                {
                    fileWriter = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
                }
            }
            catch(IOException e)
            {
                throw new PipelineJobException("Could not create the " + outputFile + " file.", e);
            }

            // Update PATH environment variable to make sure all files in the tools
            // directory and the directory of the executable or on the path.
            String toolDir = PipelineJobService.get().getAppProperties().getToolsDirectory();
            if (toolDir != null && !"".equals(toolDir))
            {
                String path = System.getenv("PATH");
                if (path == null)
                {
                    path = toolDir;
                }
                else
                {
                    path = toolDir + File.pathSeparatorChar + path;
                }

                // If the command has a path, then prepend its parent directory to the PATH
                // environment variable as well.
                String exePath = pb.command().get(0);
                if (exePath != null && !"".equals(exePath) && exePath.indexOf(File.separatorChar) != -1)
                {
                    File fileExe = new File(exePath);
                    String exeDir = fileExe.getParent();
                    if (!exeDir.equals(toolDir) && fileExe.exists())
                        path = fileExe.getParent() + File.pathSeparatorChar + path;
                }

                pb.environment().put("PATH", path);
            }

            // tell more modern TPP tools to run headless (so no perl calls etc) bpratt 4-14-09
            pb.environment().put("XML_ONLY", "1");
            // tell TPP tools not to mess with tmpdirs, we handle this at higher level
            pb.environment().put("WEBSERVER_TMP","");

            try
            {
                pb.directory(dirWork);

                // TODO: Errors should go to log even when output is redirected to a file.
                pb.redirectErrorStream(true);

                info("Working directory is " + dirWork.getAbsolutePath());
                info("running: " + StringUtils.join(pb.command().iterator(), " "));

                proc = pb.start();
            }
            catch (SecurityException se)
            {
                throw new PipelineJobException("Failed starting process '" + pb.command() + "'. Permissions do not allow execution.", se);
            }
            catch (IOException eio)
            {
                Map<String, String> env = pb.environment();
                String path = env.get("PATH");
                if(path == null) path = env.get("Path");
                throw new PipelineJobException("Failed starting process '" + pb.command() + "'", eio);
            }

            BufferedReader procReader = null;

            try
            {
                procReader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()));
                String line;
                int count = 0;
                while ((line = procReader.readLine()) != null)
                {
                    count++;
                    if(fileWriter == null)
                        info(line);
                    else
                    {
                        fileWriter.println(line);
                        if (logLineInterval > 0 && (count % logLineInterval == 0))
                            info(count + " lines");
                    }
                }
                if (fileWriter != null)
                    info(count + " lines written total");
            }
            catch (IOException eio)
            {
                throw new PipelineJobException("Failed writing output for process in '" + dirWork.getPath() + "'.", eio);
            }
            finally
            {
                if (procReader != null)
                {
                    try
                    {   procReader.close(); }
                    catch (IOException eio)
                    { }
                }
            }
        }
        finally
        {
            if (fileWriter != null)
                fileWriter.close();
        }

        try
        {
            int result = proc.waitFor();
            if (result != 0)
            {
                throw new ToolExecutionException("Failed running " + pb.command().get(0) + ", exit code " + result, result);
            }
        }
        catch (InterruptedException ei)
        {
            throw new PipelineJobException("Interrupted process for '" + dirWork.getPath() + "'.", ei);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    //  Logging
    
    /**
     * Log4J Logger subclass to allow us to create loggers that are not cached
     * in the Log4J repository for the life of the webapp.  This logger also
     * logs to the weblog for the PipelineJob class, allowing administrators
     * to collect whatever level of logging they want from PipelineJobs.
     */
    private class OutputLogger extends Logger
    {
        private boolean _isSettingStatus;

        protected OutputLogger(String name)
        {
            super(name);

            repository = new OutputLoggerRepository(name, this);
        }

        public void debug(Object message)
        {
            debug(message, null);
        }

        public void debug(Object message, Throwable t)
        {
            getClassLogger().debug(getSystemLogMessage(message), t);
            super.debug(message, t);
        }

        public void info(Object message)
        {
            info(message, null);
        }

        public void info(Object message, Throwable t)
        {
            getClassLogger().info(getSystemLogMessage(message), t);
            super.info(message, t);
        }

        public void warn(Object message)
        {
            warn(message, null);
        }

        public void warn(Object message, Throwable t)
        {
            getClassLogger().warn(getSystemLogMessage(message), t);
            super.warn(message, t);
        }

        public void error(Object message)
        {
            error(message, null);
        }

        public void error(Object message, Throwable t)
        {
            getClassLogger().error(getSystemLogMessage(message), t);
            super.error(message, t);
            setErrorStatus(message);
        }

        public void fatal(Object message)
        {
            fatal(message, null);
        }

        public void fatal(Object message, Throwable t)
        {
            getClassLogger().fatal(getSystemLogMessage(message), t);
            super.fatal(message, t);
            setErrorStatus(message);
        }

        private String getSystemLogMessage(Object message)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("(from pipeline job log file ");
            sb.append(getLogFile().getPath());
            if (message != null)
            {
                sb.append(": ");
                sb.append(message);
            }
            sb.append(")");
            return sb.toString();
        }

        public void setErrorStatus(Object message)
        {
            if (_isSettingStatus)
                return;

            _isSettingStatus = true;
            try
            {
                setStatus(PipelineJob.ERROR_STATUS, message.toString());
            }
            finally
            {
                _isSettingStatus = false;
            }
        }
    }

    private static class OutputLoggerRepository implements LoggerRepository
    {
        private String _name;
        private OutputLogger _outputLogger;

        protected OutputLoggerRepository(String name, OutputLogger logger)
        {
            _name = name;
            _outputLogger = logger;
        }

        public void addHierarchyEventListener(HierarchyEventListener listener)
        {
        }

        public boolean isDisabled(int level)
        {
            return false;
        }

        public void setThreshold(Level level)
        {
        }

        public void setThreshold(String val)
        {
        }

        public void emitNoAppenderWarning(Category cat)
        {
        }

        public Level getThreshold()
        {
            return null;
        }

        public Logger getLogger(String name)
        {
            if (_name.equals(name))
                return _outputLogger;
            return null;
        }

        public Logger getLogger(String name, LoggerFactory factory)
        {
            throw new UnsupportedOperationException();
        }

        public Logger getRootLogger()
        {
            return _outputLogger;
        }

        public Logger exists(String name)
        {
            if (_name.equals(name))
                return _outputLogger;
            return null;
        }

        public void shutdown()
        {
        }

        public Enumeration getCurrentLoggers()
        {
            Vector<OutputLogger> v = new Vector<OutputLogger>();
            v.add(_outputLogger);
            return v.elements();
        }

        public Enumeration getCurrentCategories()
        {
            return getCurrentLoggers();
        }

        public void fireAddAppenderEvent(Category logger, Appender appender)
        {
        }

        public void resetConfiguration()
        {
        }
    }

    public String getLogLevel()
    {
        return _loggerLevel;
    }

    public void setLogLevel(String level)
    {
        if (!_loggerLevel.equals(level))
        {
            _loggerLevel = level;
            _logger = null; // Reset the logger
        }
    }

    public Logger getClassLogger()
    {
        return _log;
    }

    public Logger getLogger()
    {
        if (_logger == null)
        {
            // Create appending logger.
            _logger = new OutputLogger(PipelineJob.class.getSimpleName() + ".Logger." + _logFile);
            _logger.removeAllAppenders();
            SafeFileAppender appender = new SafeFileAppender(_logFile);
            appender.setLayout(new PatternLayout("%d{DATE} %-5p: %m%n"));
            _logger.addAppender(appender);
            _logger.setLevel(Level.toLevel(_loggerLevel));
        }

        return _logger;
    }

    public void error(String message)
    {
        error(message, null);
    }

    public void error(String message, Throwable t)
    {
        setErrors(getErrors() + 1);
        if (getLogger() != null)
            getLogger().error(message, t);
    }

    public void debug(String message)
    {
        debug(message, null);
    }

    public void debug(String message, Throwable t)
    {
        if (getLogger() != null)
            getLogger().debug(message, t);
    }

    public void warn(String message)
    {
        warn(message, null);
    }

    public void warn(String message, Throwable t)
    {
        if (getLogger() != null)
            getLogger().warn(message, t);
    }

    public void info(String message)
    {
        info(message, null);
    }

    public void info(String message, Throwable t)
    {
        if (getLogger() != null)
            getLogger().info(message, t);
    }

    public void header(String message)
    {
        info(message);
        info("=======================================");
    }

    /////////////////////////////////////////////////////////////////////////
    //  ViewBackgroundInfo access
    //      WARNING: Some access of ViewBackgroundInfo is not supported when
    //               the job is running outside the LabKey Server.

    /**
     * Gets the container ID from the <code>ViewBackgroundInfo</code>.
     *
     * @return the ID for the container in which the job was started
     */
    public String getContainerId()
    {
        return getInfo().getContainerId();
    }

    /**
     * Gets the <code>User</code> instance from the <code>ViewBackgroundInfo</code>.
     * WARNING: Not supported if job is not running in the LabKey Server.
     *
     * @return the user who started the job
     */
    public User getUser()
    {
        return getInfo().getUser();
    }

    /**
     * Gets the <code>Container</code> instance from the <code>ViewBackgroundInfo</code>.
     * WARNING: Not supported if job is not running in the LabKey Server.
     *
     * @return the container in which the job was started
     */
    public Container getContainer()
    {
        return getInfo().getContainer();
    }

    /**
     * Gets the <code>ActionURL</code> instance from the <code>ViewBackgroundInfo</code>.
     * WARNING: Not supported if job is not running in the LabKey Server.
     *
     * @return the URL of the request that started the job
     */
    public ActionURL getActionURL()
    {
        return getInfo().getURL();
    }

    /**
     * Gets the <code>ViewBackgroundInfo</code> associated with this job in its contstructor.
     * WARNING: Although this function is supported outside the LabKey Server, certain
     *          accessors on the <code>ViewBackgroundInfo</code> itself are not.
     *
     * @return information from the starting request, for use in background processing
     */
    public ViewBackgroundInfo getInfo()
    {
        return _info;
    }

    /////////////////////////////////////////////////////////////////////////
    // Scheduling interface
    //      TODO: Figure out how these apply to the Enterprise Pipeline
    
    protected boolean canInterrupt()
    {
        return false;
    }

    public synchronized boolean interrupt()
    {
        if (!canInterrupt())
            return false;
        _interrupted = true;
        return true;
    }

    public synchronized boolean checkInterrupted()
    {
        return _interrupted;
    }

    public boolean allowMultipleSimultaneousJobs()
    {
        return false;
    }

    synchronized public void setSubmitted()
    {
        _submitted = true;
        notifyAll();
    }

    synchronized private boolean isSubmitted()
    {
        return _submitted;
    }

    synchronized private void waitUntilSubmitted()
    {
        while (!_submitted)
        {
            try
            {
                wait();
            }
            catch (InterruptedException e)
            {
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // JobRunner.Job interface
    
    public Object get() throws InterruptedException, ExecutionException
    {
        waitUntilSubmitted();
        return super.get();
    }

    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        return get();
    }

    protected void starting(Thread thread)
    {
        _queue.starting(this, thread);
    }

    public boolean cancel(boolean mayInterruptIfRunning)
    {
        if (isSubmitted())
            return super.cancel(mayInterruptIfRunning);
        return true;
    }

    public boolean isDone()
    {
        if (!isSubmitted())
            return false;
        return super.isDone();
    }

    public boolean isCancelled()
    {
        if (!isSubmitted())
            return false;
        return super.isCancelled();
    }

    @Override
    protected void done(Throwable throwable)
    {
        if (null != throwable)
        {
            try
            {
                error("Uncaught exception in PiplineJob: " + this.toString(), throwable);
            }
            catch (Exception x)
            {
            }
        }
        _queue.done(this);
    }
}
