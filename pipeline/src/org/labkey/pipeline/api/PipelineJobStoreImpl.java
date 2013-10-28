/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import com.thoughtworks.xstream.converters.ConversionException;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.NoSuchJobException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implements serialization of a <code>PipelineJob</code> to and from XML,
 * and storage and retrieval from the <code>PipelineJobStatusManager</code>. 
 */
public class PipelineJobStoreImpl extends PipelineJobMarshaller
{
    public PipelineJob fromXML(String xml)
    {
        PipelineJob job = super.fromXML(xml);
        job.restoreQueue(PipelineService.get().getPipelineQueue());
        return job;
    }

    @Nullable
    public PipelineJob getJob(String jobId)
    {
        return fromStatus(PipelineStatusManager.retrieveJob(jobId));
    }

    @Nullable
    public PipelineJob getJob(int rowId)
    {
        return fromStatus(PipelineStatusManager.retrieveJob(rowId));
    }

    public void retry(String jobId) throws IOException, NoSuchJobException
    {
        retry(PipelineStatusManager.getJobStatusFile(jobId));
    }

    public void retry(PipelineStatusFile sf) throws IOException, NoSuchJobException
    {
        try
        {
            PipelineJob job = fromStatus(sf.getJobStore());
            if (job == null)
                throw new NoSuchJobException("Job checkpoint does not exist.");

            // If the job is being retried from a non-error status, then don't
            // increment error and retry counts.  This happens when a server restart
            // causes all previously queued jobs to be requeued.
            if (PipelineJob.ERROR_STATUS.equals(sf.getStatus()))
                job.retryUpdate();

            String oldJobId = sf.getJobId();
            PipelineService.get().getPipelineQueue().addJob(job);

            job.getLogger().info("Retrying job. Old Job ID: " + oldJobId +
                    (Objects.equals(sf.getJobId(), job.getJobGUID()) ? "" : ", new Job ID: " + job.getJobGUID()));
        }
        catch (ConversionException e)
        {
            throw new IOException("Failed to restore the checkpoint from the database.", e);
        }
        catch (PipelineValidationException e)
        {
            throw new IOException("Invalid job parameters", e);
        }
    }

    @Nullable
    private PipelineJob fromStatus(String xml)
    {
        if (xml == null || xml.length() == 0)
            return null;
        
        PipelineJob job = fromXML(xml);

        // If it was stored, then it can't be on a queue.
        job.clearQueue();
        
        return job;
    }

    public void storeJob(PipelineJob job) throws NoSuchJobException
    {
        PipelineStatusManager.storeJob(job.getJobGUID(), toXML(job));
    }

    // Synchronize all spliting and joining to avoid SQL deadlocks.  Splitting
    // and joining currently only touches a single table, but it can do so a
    // fair number of times, which has caused deadlocks.  SQL indexes have been
    // added in an effort to prevent the deadlocks on the database side, but
    // this seems like the safest fix with only one server accessing the database.
    private static final ReentrantLock SPLIT_LOCK = new ReentrantLock();

    // The split record was created in an effort to reduce the SQL round-trips
    // for a split that triggers re-joining on the same thread stack.  It seems
    // to work, but it is not totally clear that the resulting complexity is
    // worth the savings, especially now that all splitting and joining is
    // synchronized to avoid SQL deadlocks.
    private ThreadLocal<SplitRecord> _splitRecord = new ThreadLocal<>();

    public void split(PipelineJob job) throws IOException
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        boolean active = scope.isTransactionActive();
        try (DbScope.Transaction transaction = scope.ensureTransaction(SPLIT_LOCK))
        {
            PipelineStatusManager.enforceLockOrder(job.getJobGUID(), active);

            // Make sure the join job has an existing status record before creating
            // the rows for the split jobs.  Just to ensure a consistent creation order.
            if (PipelineStatusManager.getJobStatusFile(job.getJobGUID()) == null)
                job.setStatus(PipelineJob.SPLIT_STATUS);

            PipelineJob[] jobs = job.createSplitJobs();

            beginSplit(job, jobs);

            // Queue all the split jobs.
            for (PipelineJob jobSplit : jobs)
            {
                try
                {
                    PipelineService.get().queueJob(jobSplit);
                }
                catch (PipelineValidationException e)
                {
                    throw new IOException(e);
                }
            }

            // If there were any split jobs left incomplete, then store the job, and
            // wait for them to complete.
            if (getIncompleteSplitCount(job.getJobGUID(), job.getContainer()) > 0)
            {
                job.setStatus(PipelineJob.SPLIT_STATUS);
            }
            transaction.commit();
        }
        finally
        {
            endSplit();
        }
    }

    public void join(PipelineJob job) throws IOException, NoSuchJobException
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        boolean active = scope.isTransactionActive();
        try (DbScope.Transaction transaction = scope.ensureTransaction(SPLIT_LOCK))
        {
            TaskId tid = job.getActiveTaskId();

            PipelineStatusManager.enforceLockOrder(job.getJobGUID(), active);

            int count = getIncompleteSplitCount(job.getParentGUID(), job.getContainer());
            setCompleteSplit(job);

            PipelineJob jobJoin = getJoinJob(job.getParentGUID());
            if (jobJoin == null)
            {
                throw new NoSuchJobException("Could not find parent job with ID '" + job.getParentGUID() + "', it may have been resubmitted and given an new ID");
            }

            jobJoin.mergeSplitJob(job);
            if (count == 1)
            {
                // All split jobs are complete
                if (tid == null)
                    jobJoin.setActiveTaskId(null);  // Complete the parent
                else
                {
                    // begin running the joined job again
                    jobJoin.setActiveTaskId(tid, false);
                    try
                    {
                        PipelineService.get().queueJob(jobJoin);
                    }
                    catch (PipelineValidationException e)
                    {
                        throw new IOException(e);
                    }
                }
            }
            else
            {
                // More split jobs left; store the join job until they complete
                storeJoinJob(jobJoin);
            }

            transaction.commit();
        }
    }

    private static class SplitRecord
    {
        private PipelineJob _joinJob;
        private List<PipelineJob> _splitJobs;

        public SplitRecord(PipelineJob job, PipelineJob[] splitJobs)
        {
            _joinJob = job;
            // Need list with editable content.
            _splitJobs = new ArrayList<>(Arrays.asList(splitJobs));
        }

        public boolean isJoinJob(String jobId)
        {
            return jobId.equals(_joinJob.getJobGUID());
        }

        public PipelineJob getJoinJob()
        {
            return _joinJob;
        }

        public int getIncompleteCount()
        {
            return _splitJobs.size();
        }

        public boolean complete(PipelineJob job)
        {
            return _splitJobs.remove(job);
        }
    }

    private void beginSplit(PipelineJob job, PipelineJob[] splitJobs)
    {
        _splitRecord.set(new SplitRecord(job, splitJobs));
    }

    private void endSplit()
    {
        _splitRecord.set(null);
    }

    @Nullable
    private PipelineJob getJoinJob(String parentJobId)
    {
        SplitRecord rec = _splitRecord.get();
        if (rec != null && rec.isJoinJob(parentJobId))
            return rec.getJoinJob();

        return getJob(parentJobId);
    }

    private int getIncompleteSplitCount(String parentJobId, Container container)
    {
        SplitRecord rec = _splitRecord.get();
        if (rec != null && rec.isJoinJob(parentJobId))
            return rec.getIncompleteCount();

        // Check the database.
        return PipelineStatusManager.getIncompleteStatusFileCount(parentJobId, container);
    }

    private void storeJoinJob(PipelineJob job) throws NoSuchJobException
    {
        SplitRecord rec = _splitRecord.get();
        if (rec != null && rec.isJoinJob(job.getJobGUID()))
            return;

        storeJob(job);
    }

    private void setCompleteSplit(PipelineJob job)
    {
        SplitRecord rec = _splitRecord.get();
        if (rec != null && rec.isJoinJob(job.getParentGUID()))
            rec.complete(job);

        job.setStatus(PipelineJob.COMPLETE_STATUS);
    }
}
