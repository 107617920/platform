package org.labkey.core.thumbnail;

import org.apache.log4j.Logger;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.security.User;
import org.labkey.api.thumbnails.DynamicThumbnailProvider;
import org.labkey.api.thumbnails.StaticThumbnailProvider;
import org.labkey.api.thumbnails.Thumbnail;
import org.labkey.api.thumbnails.ThumbnailService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ShutdownListener;

import javax.servlet.ServletContextEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 9:22 AM
 */
public class ThumbnailServiceImpl implements ThumbnailService
{
    private static final Logger LOG = Logger.getLogger(ThumbnailServiceImpl.class);
    private static final BlockingQueue<DynamicThumbnailProvider> QUEUE = new LinkedBlockingQueue<DynamicThumbnailProvider>(1000);
    private static final ThumbnailGeneratingThread THREAD = new ThumbnailGeneratingThread();

    static
    {
        THREAD.start();
    }

    public ThumbnailServiceImpl()
    {
    }

    @Override
    public CacheableWriter getThumbnailWriter(StaticThumbnailProvider provider)
    {
        if (provider instanceof DynamicThumbnailProvider)
            return ThumbnailCache.getThumbnailWriter((DynamicThumbnailProvider)provider);
        else
            return ThumbnailCache.getThumbnailWriter(provider);
    }

    @Override
    public void queueThumbnailRendering(DynamicThumbnailProvider provider)
    {
        QUEUE.offer(provider);
    }


    private static class ThumbnailGeneratingThread extends Thread implements ShutdownListener
    {
        private ThumbnailGeneratingThread()
        {
            setDaemon(true);
            setName(ThumbnailGeneratingThread.class.getSimpleName());
            ContextListener.addShutdownListener(this);
        }

        @Override
        public void run()
        {
            try
            {
                //noinspection InfiniteLoopStatement
                while (!interrupted())
                {
                    DynamicThumbnailProvider provider = QUEUE.take();
                    // TODO: Real ViewContext
                    Thumbnail thumnail = provider.generateDynamicThumbnail(null);
                    AttachmentFile file = new InputStreamAttachmentFile(thumnail.getInputStream(), THUMBNAIL_FILENAME, thumnail.getContentType());

                    try
                    {
                        // TODO: Delete thumbnail attachment first? Or does add do a replace?
                        AttachmentService.get().addAttachments(provider, Collections.singletonList(file), User.guest);
                        ThumbnailCache.remove(provider);
                    }
                    catch (IOException e)
                    {
                        ExceptionUtil.logExceptionToMothership(null, e);
                    }
                }
            }
            catch (InterruptedException e)
            {
                LOG.debug(getClass().getSimpleName() + " is terminating due to interruption");
            }
        }

        @Override
        public void shutdownPre(ServletContextEvent servletContextEvent)
        {
            interrupt();
        }

        @Override
        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
        }
    }
}
