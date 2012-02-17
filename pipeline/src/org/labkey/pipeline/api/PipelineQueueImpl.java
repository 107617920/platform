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
package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobData;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineQueue;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JobRunner;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 */
public class PipelineQueueImpl implements PipelineQueue
{
    private static Logger _log = Logger.getLogger(PipelineQueueImpl.class);
    private int MAX_RUNNING_JOBS = 10;

    List<PipelineJob> _pending = new ArrayList<PipelineJob>();
    List<PipelineJob> _running = new ArrayList<PipelineJob>();

    // This is the list of jobs that have been submitted to JobRunner-- they
    // may be either running or pending.
    HashSet<PipelineJob> _submitted = new HashSet<PipelineJob>();

    JobRunner _runner = new JobRunner(MAX_RUNNING_JOBS);

    public synchronized void addJob(PipelineJob job)
    {
        if (null == job)
            throw new NullPointerException();
        _logDebug("PENDING:   " + job.toString());

        // Make sure status file path and Job ID are in synch.
        File logFile = job.getLogFile();
        try
        {
            if (logFile != null)
            {
                PipelineStatusFileImpl pipelineStatusFile = PipelineStatusManager.getStatusFile(logFile.getAbsolutePath());
                if (pipelineStatusFile == null)
                {
                    PipelineStatusManager.setStatusFile(job, job.getUser(), PipelineJob.WAITING_STATUS, null, true);
                }

                PipelineStatusManager.resetJobId(job.getLogFile().getAbsolutePath(), job.getJobGUID());
            }

            if (job.setQueue(this, PipelineJob.WAITING_STATUS))
            {
                _pending.add(job);
                submitJobs();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public boolean isLocal()
    {
        // Only place for this queue is local server memory.
        return true;
    }

    public boolean isTransient()
    {
        // Only place for this queue is local server memory.
        return true;
    }


    public synchronized void starting(PipelineJob job, Thread thread)
    {
        // WARNING: This method is for pipeline maintenance only.  Do not put
        //          important functionality side-effects in here, since this
        //          function is not supported in the Enterprise Pipeline.
        _logDebug("RUNNING:   " + job.toString());
        boolean removed = _pending.remove(job);
        assert removed;
        _running.add(job);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
    }


    public synchronized void done(PipelineJob job)
    {
        // WARNING: This method is for pipeline maintenance only.  Do not put
        //          important functionality side-effects in here, since this
        //          function is not supported in the Enterprise Pipeline.
        _logDebug("COMPLETED: " + job.toString());
        ConnectionWrapper.dumpLeaksForThread(Thread.currentThread());
        boolean removed = _running.remove(job);
        assert removed;
        removed = _submitted.remove(job);
        assert removed;
        submitJobs();
    }

    /**
     * Look through the pending jobs and see if there are any that can be submitted to the runner right now.
     * Some jobs are single threaded.  There can be only one single threaded job in the queue at any time.
     * Multi-threaded jobs are allowed to be run one per container.
     *
     * We do not submit a job to the JobRunner unless it is ok to run it right now.
     * The JobRunner takes care of limiting the simultaneous jobs to {@link #MAX_RUNNING_JOBS}
     */
    private synchronized void submitJobs()
    {
        if (_pending.size() == 0)
            return;
        HashSet<String> containers = new HashSet<String>();
        boolean singleThreadedJobFound = false;
        for (PipelineJob job : _submitted)
        {
            containers.add(job.getContainerId());
            if (!job.allowMultipleSimultaneousJobs())
            {
                singleThreadedJobFound = true;
            }
        }
        for (PipelineJob job : _pending.toArray(new PipelineJob[_pending.size()]))
        {
            if (_submitted.contains(job))
                continue;
            if (!job.allowMultipleSimultaneousJobs() && singleThreadedJobFound)
                continue;
            if (containers.contains(job.getContainerId()))
                continue;
            _submitted.add(job);
            containers.add(job.getContainerId());
            _runner.execute(job);
            job.setSubmitted();
            if (!job.allowMultipleSimultaneousJobs())
            {
                singleThreadedJobFound = true;
            }
        }
    }


    boolean inContainer(Container c, PipelineJob job)
    {
        // We use null to mean "all containers"
        if (c == null)
            return true;
        return c.getId().equals(job.getContainerId());
    }

    public synchronized boolean cancelJob(Container c, PipelineStatusFile statusFile)
    {
        // Go through the list of queued (but not running jobs) and remove the requested job, if found
        for (ListIterator<PipelineJob> it = _pending.listIterator(); it.hasNext();)
        {
            PipelineJob job = it.next();
            if (job.getJobGUID().equals(statusFile.getJobId()) && inContainer(c, job))
            {
                if (job.cancel(false))
                {
                    it.remove();
                    // It should already be set to CANCELLING. Set to CANCELLED to indicate that it's dead.
                    statusFile.setStatus(PipelineJob.CANCELLED_STATUS);
                    statusFile.save();
                    return true;
                }
            }
        }
        for (ListIterator<PipelineJob> it = _running.listIterator(); it.hasNext();)
        {
            PipelineJob job = it.next();
            if (job.getJobGUID().equals(statusFile.getJobId()) && inContainer(c, job))
            {
                if (job.interrupt())
                {
                    return true;
                }
            }
        }
        submitJobs();
        return false;
    }

    public List<PipelineJob> findJobs(String location)
    {
        String locationDefault = PipelineJobService.get().getDefaultExecutionLocation();

        // For the mini-pipeline the only location is the default location.
        // Just return an empty list for any other location that is requested.
        List<PipelineJob> result = new ArrayList<PipelineJob>();
        if (location.equals(locationDefault))
        {
            for (PipelineJob job : _pending)
                result.add(job);
            for (PipelineJob job : _running)
                result.add(job);
        }
        return result;
    }

    private boolean statusFileMatches(PipelineJob job, String statusFile)
    {
        File fileCompare = job.getLogFile();
        if (fileCompare == null)
            return false;
        String compare = PipelineJobService.statusPathOf(fileCompare.toString());
        return new File(compare).equals(new File(statusFile));
    }

    public PipelineJob findJobInMemory(Container c, String statusFile)
    {
        PipelineJobData jd = getJobDataInMemory(c);
        statusFile = PipelineJobService.statusPathOf(statusFile);
        for (PipelineJob job : jd.getRunningJobs())
        {
            if (statusFileMatches(job, statusFile))
                return job;
        }
        for (PipelineJob job : jd.getPendingJobs())
        {
            if (statusFileMatches(job, statusFile))
                return job;
        }
        return null;
    }

    public synchronized PipelineJobData getJobDataInMemory(Container c)
    {
        PipelineJobData ret = new PipelineJobData();

        for (PipelineJob job : _running)
        {
            if (inContainer(c, job))
                ret.addRunningJob(job);
        }
        for (PipelineJob job : _pending)
        {
            if (inContainer(c, job))
                ret.addPendingJob(job);
        }
        return ret;
    }

    private void _logDebug(String s)
    {
        _log.debug(s);
        //System.err.println(s);
    }

    //
    // JUNIT
    //

    private static class TestJob extends PipelineJob
    {
        AtomicInteger _counter;

        TestJob(Container c, AtomicInteger counter) throws SQLException
        {
            super(null, new ViewBackgroundInfo(c, null, null), PipelineService.get().findPipelineRoot(c));
            _counter = counter;
        }

        public void run()
        {
            long til = System.currentTimeMillis() + 1000;
            double[] a = new double[10000];
            while (til > System.currentTimeMillis())
            {
                for (int i = 0; i < a.length; i++)
                    a[i] = Math.random();
                Arrays.sort(a);
                Thread.yield();
            }
            _counter.incrementAndGet();
        }

        public String getDescription()
        {
            return "test job";
        }

        public ActionURL getStatusHref()
        {
            return null;
        }
    }

    static class FakeContainer extends Container
    {
        FakeContainer(String name, Container parent)
        {
            super(parent, name, GUID.makeGUID(), 1, 0, new Date(), false);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testPipeline() throws Exception
        {
            Container root = new FakeContainer(null, null);
            Container containerA = new FakeContainer("A", root);
            Container containerB = new FakeContainer("B", root);

            PipelineQueueImpl queue = new PipelineQueueImpl();
            PipelineJobData data;
            AtomicInteger counter = new AtomicInteger();

            TestJob[] jobs = new TestJob[]
                    {
                            new TestJob(containerA, counter),
                            new TestJob(containerB, counter),
                            new TestJob(containerA, counter),
                            new TestJob(containerB, counter),
                            new TestJob(containerA, counter),
                            new TestJob(containerB, counter),
                            new TestJob(containerA, counter),
                            new TestJob(containerB, counter),
                    };

            // Add four jobs
            for (int i = 0; i < 4; i++)
                queue.addJob(jobs[i]);
            Thread.sleep(1);
            data = queue.getJobDataInMemory(containerA);
            //assertEquals(2, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());
            data = queue.getJobDataInMemory(containerB);
            //assertEquals(2, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());

            // wait a bit
            Thread.sleep(100);
            data = queue.getJobDataInMemory(null);
            //assertEquals(4, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());

            // add remaining jobs
            for (int i = 4; i < jobs.length; i++)
                queue.addJob(jobs[i]);
            Thread.sleep(1);
            data = queue.getJobDataInMemory(null);
            //assertEquals(jobs.length, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());

            // wait for last submitted job to finish
            PipelineJob last = jobs[jobs.length - 1];
            last.get();
            assertTrue(last.isDone());
            data = queue.getJobDataInMemory(null);
            //assertEquals(jobs.length, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());
            assertFalse(data.getPendingJobs().contains(last));
            //assertTrue(data.getCompletedJobs().contains(last) || data.getRunningJobs().contains(last));

            for (TestJob job : jobs)
            {
                job.get();
                assertTrue(job.isDone());
            }
            data = queue.getJobDataInMemory(null);
            Thread.sleep(10);
            data = queue.getJobDataInMemory(null);

            data = queue.getJobDataInMemory(containerA);
            data = queue.getJobDataInMemory(null);

            data = queue.getJobDataInMemory(null);


            assertEquals(0, queue._runner.getJobCount());
            assertEquals(jobs.length, counter.get());
        }
    }
}
