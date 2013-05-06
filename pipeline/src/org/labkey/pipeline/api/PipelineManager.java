/*
 * Copyright (c) 2005-2013 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Filter;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.GlobusKeyPair;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.WebdavService;
import org.labkey.pipeline.PipelineWebdavProvider;
import org.labkey.pipeline.status.StatusController;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 */
public class PipelineManager
{
    private static final Logger _log = Logger.getLogger(PipelineManager.class);
    private static final PipelineSchema pipeline = PipelineSchema.getInstance();
    private static final BlockingStringKeyCache<PipelineRoot> CACHE = CacheManager.getBlockingStringKeyCache(10000, CacheManager.DAY, "Pipeline roots", new CacheLoader<String, PipelineRoot>()
    {
        @Override
        public PipelineRoot load(String key, @Nullable Object argument)
        {
            return new TableSelector(pipeline.getTableInfoPipelineRoots(), (Filter)argument, null).getObject(PipelineRoot.class);
        }
    });

    protected static PipelineRoot getPipelineRootObject(Container container, String type)
    {
        SimpleFilter filter = new SimpleFilter("Container", container.getId());
        filter.addCondition("Type", type);

        return CACHE.get(getCacheKey(container, type), filter);
    }

    private static String getCacheKey(Container c, @Nullable String type)
    {
        return c.getId() + "/" + StringUtils.trimToEmpty(type);
    }

    public static PipelineRoot findPipelineRoot(Container container)
    {
        while (container != null && !container.isRoot())
        {
            PipelineRoot pipelineRoot = getPipelineRootObject(container, PipelineRoot.PRIMARY_ROOT);
            if (null != pipelineRoot)
                return pipelineRoot;
            container = container.getParent();
        }
        return null;
    }


    static public PipelineRoot[] getPipelineRoots(String type)
    {
        SimpleFilter filter = new SimpleFilter("Type", type);

        return new TableSelector(pipeline.getTableInfoPipelineRoots(), filter, null).getArray(PipelineRoot.class);
    }

    static public void setPipelineRoot(User user, Container container, URI[] roots, String type,
                                       GlobusKeyPair globusKeyPair, boolean searchable) throws SQLException
    {
        PipelineRoot oldValue = getPipelineRootObject(container, type);
        PipelineRoot newValue = null;

        try
        {
            if (roots == null || roots.length == 0 || (roots.length == 1 && roots[0] == null))
            {
                if (oldValue != null)
                {
                    Table.delete(PipelineSchema.getInstance().getTableInfoPipelineRoots(), oldValue.getPipelineRootId());
                }
            }
            else
            {
                if (oldValue == null)
                {
                    newValue = new PipelineRoot();
                }
                else
                {
                    newValue = new PipelineRoot(oldValue);
                }
                newValue.setPath(roots[0].toString());
                newValue.setSupplementalPath(roots.length > 1 ? roots[1].toString() : null);
                newValue.setContainerId(container.getId());
                newValue.setType(type);
                newValue.setKeyBytes(globusKeyPair == null ? null : globusKeyPair.getKeyBytes());
                newValue.setCertBytes(globusKeyPair == null ? null : globusKeyPair.getCertBytes());
                newValue.setKeyPassword(globusKeyPair == null ? null : globusKeyPair.getPassword());
                newValue.setSearchable(searchable);
                if (oldValue == null)
                {
                    Table.insert(user, pipeline.getTableInfoPipelineRoots(), newValue);
                }
                else
                {
                    Table.update(user, pipeline.getTableInfoPipelineRoots(), newValue, newValue.getPipelineRootId());
                }

                Path davPath = WebdavService.getPath().append(container.getParsedPath()).append(PipelineWebdavProvider.PIPELINE_LINK);
                SearchService ss = ServiceRegistry.get().getService(SearchService.class);
                if (null != ss)
                    ss.addPathToCrawl(davPath, null);
            }
        }
        finally
        {
            CACHE.remove(getCacheKey(container, type));
        }

        ContainerManager.firePropertyChangeEvent(new ContainerManager.ContainerPropertyChangeEvent(
                container, user, ContainerManager.Property.PipelineRoot, oldValue, newValue));
    }

    static public void purge(Container container)
    {
        try
        {
            StringBuilder sql = new StringBuilder();
            List<Object> params = new ArrayList<>();
            sql.append("UPDATE ").append(ExperimentService.get().getTinfoExperimentRun())
                .append(" SET JobId = NULL ")
                .append("WHERE JobId IN (SELECT RowId FROM " + pipeline.getTableInfoStatusFiles() + " p ")
                .append(" WHERE container = ? ) ");
                params.add(container.getId());

            sql.append(" AND Container = ?");
            params.add(container.getId());

            ExperimentService.get().ensureTransaction();

            try
            {
                DbCache.clear(ExperimentService.get().getTinfoExperimentRun());
                Table.execute(PipelineSchema.getInstance().getSchema(), sql.toString(), params.toArray());

                // TODO: purge methods aren't called in reverse module depedency order
                DbSchema di = null;
                try {di = DbSchema.get("dataintegration");} catch (Exception x) {}
                if (null != di)
                {
                    TableInfo t =  di.getTable("TransformRun");
                    if (null != t)
                        ContainerUtil.purgeTable(t , container, null);
                }

                ContainerUtil.purgeTable(pipeline.getTableInfoStatusFiles(), container, "Container");

                ExperimentService.get().commitTransaction();
            }
            finally
            {
                ExperimentService.get().closeTransaction();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        try
        {
            ContainerUtil.purgeTable(pipeline.getTableInfoPipelineRoots(), container, "Container");
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            CACHE.remove(getCacheKey(container, null));
        }
    }

    static void setPipelineProperty(Container container, String name, String value)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(container, "pipelineRoots", true);
        if (value == null)
            props.remove(name);
        else
            props.put(name, value);
        PropertyManager.saveProperties(props);
    }

    static String getPipelineProperty(Container container, String name)
    {
        Map<String, String> props = PropertyManager.getProperties(container, "pipelineRoots");
        return props.get(name);
    }

    public static void sendNotificationEmail(PipelineStatusFileImpl statusFile, Container c)
    {
        PipelineMessage message;
        if (statusFile.getStatus().equals(PipelineJob.COMPLETE_STATUS))
        {
            String interval = PipelineEmailPreferences.get().getSuccessNotificationInterval(c);
            if (!"0".equals(interval) && interval != null) return;

            message = createPipelineMessage(c, statusFile,
                    EmailTemplateService.get().getEmailTemplate(PipelineJobSuccess.class),
                    PipelineEmailPreferences.get().getNotifyOwnerOnSuccess(c),
                    PipelineEmailPreferences.get().getNotifyUsersOnSuccess(c));
        }
        else
        {
            String interval = PipelineEmailPreferences.get().getFailureNotificationInterval(c);
            if (!"0".equals(interval) && interval != null)
            {
                _log.info("Deciding not to send error notification email based on interval " + interval);
                return;
            }

            _log.info("Creating error notification email");
            message = createPipelineMessage(c, statusFile,
                    EmailTemplateService.get().getEmailTemplate(PipelineJobFailed.class),
                    PipelineEmailPreferences.get().getNotifyOwnerOnError(c),
                    PipelineEmailPreferences.get().getNotifyUsersOnError(c));
            if (message == null)
            {
                _log.info("Did not create a message for error notification email");
            }
        }

        try
        {
            if (message != null)
            {
                Message m = message.createMessage();
                MailHelper.send(m, null, c);
            }
        }
        catch (ConfigurationException me)
        {
            _log.error("Failed sending an email notification message for a pipeline job", me);
        }
    }

    public static void sendNotificationEmail(PipelineStatusFileImpl[] statusFiles, Container c, Date min, Date max, boolean isSuccess)
    {
        PipelineDigestTemplate template = isSuccess ?
                EmailTemplateService.get().getEmailTemplate(PipelineDigestJobSuccess.class) :
                EmailTemplateService.get().getEmailTemplate(PipelineDigestJobFailed.class);

        PipelineDigestMessage[] messages = createPipelineDigestMessage(c, statusFiles, template,
                PipelineEmailPreferences.get().getNotifyOwnerOnSuccess(c),
                PipelineEmailPreferences.get().getNotifyUsersOnSuccess(c),
                min, max);

        try {
            if (messages != null)
            {
                for (PipelineDigestMessage msg : messages)
                {
                    Message m = msg.createMessage();
                    MailHelper.send(m, null, c);
                }
            }
        }
        catch (ConfigurationException me)
        {
            _log.error("Failed sending an email notification message for a pipeline job", me);
        }
    }

    private static PipelineMessage createPipelineMessage(Container c, PipelineStatusFileImpl statusFile,
                                                        PipelineEmailTemplate template,
                                                        boolean notifyOwner, String notifyUsers)
    {
        if (notifyOwner || !StringUtils.isEmpty(notifyUsers))
        {
            StringBuilder sb = new StringBuilder();

            if (notifyOwner && !StringUtils.isEmpty(statusFile.getEmail()))
            {
                sb.append(statusFile.getEmail());
                sb.append(';');
            }

            if (!StringUtils.isEmpty(notifyUsers))
                sb.append(notifyUsers);

            if (sb.length() > 0)
            {
                PipelineMessage message = new PipelineMessage(c, template, statusFile);
                message.setRecipients(sb.toString());
                return message;
            }
        }
        return null;
    }

    private static PipelineDigestMessage[] createPipelineDigestMessage(Container c, PipelineStatusFileImpl[] statusFiles,
                                                        PipelineDigestTemplate template,
                                                        boolean notifyOwner, String notifyUsers,
                                                        Date min, Date max)
    {
        if (notifyOwner || !StringUtils.isEmpty(notifyUsers))
        {
            Map<String, StringBuilder> recipients = new HashMap<>();
            for (PipelineStatusFileImpl sf : statusFiles)
            {
                if (notifyOwner && !StringUtils.isEmpty(sf.getEmail()))
                {
                    if (!recipients.containsKey(sf.getEmail()))
                    {
                        StringBuilder sb = new StringBuilder();
                        sb.append(sf.getEmail());
                        sb.append(';');

                        if (!StringUtils.isEmpty(notifyUsers))
                            sb.append(notifyUsers);

                        recipients.put(sf.getEmail(), sb);
                    }
                }
            }

            if (recipients.isEmpty() && !StringUtils.isEmpty(notifyUsers))
            {
                StringBuilder sb = new StringBuilder();
                sb.append(notifyUsers);

                recipients.put("notifyUsers", sb);
            }

            List<PipelineDigestMessage> messages = new ArrayList<>();
            for (StringBuilder sb : recipients.values())
            {
                PipelineDigestMessage message = new PipelineDigestMessage(c, template, statusFiles, min, max, sb.toString());
                messages.add(message);
            }
            return messages.toArray(new PipelineDigestMessage[messages.size()]);
        }
        return null;
    }

    private static class PipelineMessage
    {
        private Container _c;
        private PipelineEmailTemplate _template;
        private PipelineStatusFileImpl _statusFile;
        private String _recipients;

        public PipelineMessage(Container c, PipelineEmailTemplate template, PipelineStatusFileImpl statusFile)
        {
            _c = c;
            _template = template;
            _statusFile = statusFile;
        }
        //public void setTemplate(PipelineEmailTemplate template){_template = template;}
        //public void setStatusFiles(PipelineStatusFileImpl[] statusFiles){_statusFiles = statusFiles;}
        public void setRecipients(String recipients){_recipients = recipients;}

        public MimeMessage createMessage()
        {
            try
            {
                MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();

                ActionURL url = StatusController.urlDetails(_statusFile); 

                _template.setDataUrl(url.getURIString());
                _template.setJobDescription(_statusFile.getDescription());
                _template.setStatus(_statusFile.getStatus());
                _template.setTimeCreated(_statusFile.getCreated().toString());

                final String body = _template.renderBody(_c);
                m.setBodyContent(body, "text/plain");
                m.setBodyContent(PageFlowUtil.filter(body, true, true), "text/html");

                m.setSubject(_template.renderSubject(_c));

                m.addFrom(new Address[]{new InternetAddress(LookAndFeelProperties.getInstance(_c).getSystemEmailAddress())});
                m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(_recipients));

                return m;
            }
            catch (Exception e)
            {
                _log.error("Failed creating an email notification message for a pipeline job", e);
            }
            return null;
        }
    }

    private static class PipelineDigestMessage
    {
        private Container _c;
        private PipelineDigestTemplate _template;
        private PipelineStatusFileImpl[] _statusFiles;
        private String _recipients;
        private Date _min;
        private Date _max;

        public PipelineDigestMessage(Container c, PipelineDigestTemplate template, PipelineStatusFileImpl[] statusFiles,
                                     Date min, Date max, String recipients)
        {
            _c = c;
            _template = template;
            _statusFiles = statusFiles;
            _min = min;
            _max = max;
            _recipients = recipients;
        }

        public MimeMessage createMessage()
        {
            try
            {
                MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();

                _template.setStatusFiles(_statusFiles);
                _template.setStartTime(_min.toString());
                _template.setEndTime(_max.toString());

                final String body = _template.renderBody(_c);
                m.setBodyContent(body, "text/plain");
                m.setBodyContent(body, "text/html");
                m.setSubject(_template.renderSubject(_c));

                m.addFrom(new Address[]{new InternetAddress(LookAndFeelProperties.getInstance(_c).getSystemEmailAddress())});
                m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(_recipients));

                return m;
            }
            catch (Exception e)
            {
                _log.error("Failed creating an email notification message for a pipeline job", e);
            }
            return null;
        }
    }

    public static abstract class PipelineEmailTemplate extends EmailTemplate
    {
        protected String _dataUrl;
        protected String _jobDescription;
        protected String _timeCreated;
        protected String _status;
        private List<ReplacementParam> _replacements = new ArrayList<>();

        protected static final String DEFAULT_BODY = "Job description: ^jobDescription^\n" +
                "Created: ^timeCreated^\n" +
                "Status: ^status^\n" +
                "Additional details for this job can be obtained by navigating to this link:\n\n^dataURL^";

        protected PipelineEmailTemplate(String name)
        {
            super(name);

            _replacements.add(new ReplacementParam("dataURL", "Link to the job details for this pipeline job"){
                public String getValue(Container c) {return _dataUrl;}
            });
            _replacements.add(new ReplacementParam("jobDescription", "The job description"){
                public String getValue(Container c) {return _jobDescription;}
            });
            _replacements.add(new ReplacementParam("timeCreated", "The date and time this job was created"){
                public String getValue(Container c) {return _timeCreated;}
            });
            _replacements.add(new ReplacementParam("status", "The job status"){
                public String getValue(Container c) {return _status;}
            });
            _replacements.addAll(super.getValidReplacements());
        }
        public void setDataUrl(String dataUrl){_dataUrl = dataUrl;}
        public void setJobDescription(String description){_jobDescription = description;}
        public void setTimeCreated(String timeCreated){_timeCreated = timeCreated;}
        public void setStatus(String status){_status = status;}
        public List<ReplacementParam> getValidReplacements(){return _replacements;}
    }

    public static class PipelineJobSuccess extends PipelineEmailTemplate
    {
        public PipelineJobSuccess()
        {
            super("Pipeline job succeeded");
            setSubject("The pipeline job: ^jobDescription^ has completed successfully");
            setBody(DEFAULT_BODY);
            setDescription("Sent to users who have been configured to receive notifications when a pipeline job completes successfully");
            setPriority(10);
        }
    }

    public static class PipelineJobFailed extends PipelineEmailTemplate
    {
        public PipelineJobFailed()
        {
            super("Pipeline job failed");
            setSubject("The pipeline job: ^jobDescription^ did not complete successfully");
            setBody(DEFAULT_BODY);
            setDescription("Sent to users who have been configured to receive notifications when a pipeline job fails");
            setPriority(11);
        }
    }

    public static abstract class PipelineDigestTemplate extends EmailTemplate
    {
        private List<ReplacementParam> _replacements = new ArrayList<>();
        private PipelineStatusFileImpl[] _statusFiles;
        private String _startTime;
        private String _endTime;

        protected static final String DEFAULT_BODY = "The following jobs have completed between the time of: ^startTime^ " +
                "and the end time of: ^endTime^:\n\n^pipelineJobs^";

        protected PipelineDigestTemplate(String name)
        {
            super(name);

            _replacements.add(new ReplacementParam("pipelineJobs", "The list of all pipeline jobs that have completed for this notification period"){
                public String getValue(Container c) {return getJobStatus();}
            });
            _replacements.add(new ReplacementParam("startTime", "The start of the time period for job completion"){
                public String getValue(Container c) {return _startTime;}
            });
            _replacements.add(new ReplacementParam("endTime", "The end of the time period for job completion"){
                public String getValue(Container c) {return _endTime;}
            });
            _replacements.addAll(super.getValidReplacements());
        }
        public void setStatusFiles(PipelineStatusFileImpl[] statusFiles){_statusFiles = statusFiles;}
        public void setStartTime(String startTime){_startTime = startTime;}
        public void setEndTime(String endTime){_endTime = endTime;}
        public List<ReplacementParam> getValidReplacements(){return _replacements;}

        private String getJobStatus()
        {
            if (_statusFiles != null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("<table>");
                sb.append("<tr><td>Description</td><td>Created</td><td>Status</td><td>Details</td></tr>");
                for (PipelineStatusFileImpl sf : _statusFiles)
                {
                    ActionURL url = StatusController.urlDetails(sf);
                    sb.append("<tr>");
                    sb.append("<td>").append(PageFlowUtil.filter(sf.getDescription())).append("</td>");
                    sb.append("<td>").append(PageFlowUtil.filter(sf.getCreated())).append("</td>");
                    sb.append("<td>").append(PageFlowUtil.filter(sf.getStatus())).append("</td>");
                    sb.append("<td><a href=\"").append(url.getURIString()).append("\">").append(url.getURIString()).append("</a></td>");
                    sb.append("</tr>");
                    sb.append("<tr><td colspan=4><hr/></td></tr>");
                }
                sb.append("</table>");
                return sb.toString();
            }
            return null;
        }
    }

    public static class PipelineDigestJobSuccess extends PipelineDigestTemplate
    {
        public PipelineDigestJobSuccess()
        {
            super("Pipeline jobs succeeded (digest)");
            setSubject("The pipeline jobs have completed successfully");
            setBody(DEFAULT_BODY);
            setDescription("Sent for pipeline jobs that have completed successfully during a configured time period");
            setPriority(20);
        }
    }

    public static class PipelineDigestJobFailed extends PipelineDigestTemplate
    {
        public PipelineDigestJobFailed()
        {
            super("Pipeline jobs failed (digest)");
            setSubject("The pipeline jobs did not complete successfully");
            setBody(DEFAULT_BODY);
            setDescription("Sent for pipeline jobs that have not completed successfully during a configured time period");
            setPriority(21);
        }
    }
}
