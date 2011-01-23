/*
 * Copyright (c) 2005-2011 LabKey Corporation
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
package org.labkey.pipeline;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.action.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.files.FileContentDefaultEmailPref;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.notification.EmailService;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.pipeline.api.GlobusKeyPairImpl;
import org.labkey.pipeline.api.PipeRootImpl;
import org.labkey.pipeline.api.PipelineEmailPreferences;
import org.labkey.pipeline.api.PipelineRoot;
import org.labkey.pipeline.api.PipelineServiceImpl;
import org.labkey.pipeline.status.StatusController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.*;

public class PipelineController extends SpringActionController
{
    private static DefaultActionResolver _resolver = new DefaultActionResolver(PipelineController.class);

    public enum RefererValues { portal, pipeline }

    public enum Params { referer, path, rootset, overrideRoot }

    private void saveReferer()
    {
        getViewContext().getRequest().getSession().setAttribute(Params.referer.toString(),
                getViewContext().getRequest().getParameter(Params.referer.toString()));
    }

    private String getSavedReferer()
    {
        return (String) getViewContext().getRequest().getSession().getAttribute(Params.referer.toString());
    }

    private static HelpTopic getHelpTopic(String topic)
    {
        return new HelpTopic(topic);
    }

    public PipelineController()
    {
        super();
        setActionResolver(_resolver);
    }

    public PageConfig defaultPageConfig()
    {
        PageConfig p = super.defaultPageConfig();
        p.setHelpTopic(getHelpTopic("pipeline"));
        return p;
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            return StatusController.urlShowList(getContainer(), false);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ReturnToRefererAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            if (RefererValues.pipeline.toString().equals(getSavedReferer()))
                return StatusController.urlShowList(getContainer(), true);
            else
                return PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(getContainer());
        }
    }

    public static ActionURL urlSetup(Container c)
    {
        return urlSetup(c, null);
    }
    
    public static ActionURL urlSetup(Container c, String referer)
    {
        return urlSetup(c, referer, false, false);
    }

    public static ActionURL urlSetup(Container c, String referer, boolean rootSet, boolean overrideRoot)
    {
        ActionURL url = new ActionURL(SetupAction.class, c);
        if (referer != null && referer.length() > 0)
            url.addParameter(Params.referer, referer);
        if (rootSet)
            url.addParameter(Params.rootset, "1");
        if (overrideRoot)
            url.addParameter(Params.overrideRoot, "1");
        return url;
    }

    @RequiresSiteAdmin
    public class SetupAction extends AbstractSetupAction<SetupForm>
    {
        protected SetupField getFormField()
        {
            return SetupField.path;
        }

        public void validateCommand(SetupForm target, Errors errors)
        {
        }

        public boolean handlePost(SetupForm form, BindException errors) throws Exception
        {
            return savePipelineSetup(getViewContext(), form, errors);
        }

        public ActionURL getSuccessURL(SetupForm form)
        {
            return urlSetup(getContainer(), getSavedReferer(), true, false);
        }
    }

    enum SetupField { path, email }

    private static URI validatePath(String path, BindException errors)
    {
        if (path == null)
        {
            return null;
        }
        
        if (path.startsWith("\\\\"))
        {
            errors.reject(ERROR_MSG, "UNC paths are not supported for pipeline roots. Consider creating a Network Drive configuration in the Admin Console under Site Settings.");
            return null;
        }
        File fileRoot = new File(path);
        try
        {
            // Try to make sure the path is the right case. getCanonicalPath() resolves symbolic
            // links on Unix so don't replace the path if it's pointing at a different location.
            if (fileRoot.getCanonicalPath().equalsIgnoreCase(fileRoot.getAbsolutePath()))
            {
                fileRoot = fileRoot.getCanonicalFile();
            }
        }
        catch (IOException e)
        {
            // OK, just use the path the user entered
        }
        if (!NetworkDrive.exists(fileRoot))
        {
            errors.reject(ERROR_MSG, "The directory '" + fileRoot + "' does not exist.");
            return null;
        }
        else if (!fileRoot.isDirectory())
        {
            errors.reject(ERROR_MSG, "The file '" + fileRoot + "' is not a directory.");
            return null;
        }

        URI result = fileRoot.toURI();
        if (URIUtil.resolve(result, result, "test") == null)
        {
            errors.reject(ERROR_MSG, "The pipeline root '" + fileRoot + "' is not valid.");
            return null;
        }
        return result;
    }

    public static boolean savePipelineSetup(ViewContext context, SetupForm form, BindException errors) throws Exception
    {
        String path = form.hasSiteDefaultPipelineRoot() ? null : form.getPath();
        URI root = validatePath(path, errors);
        String supplementalPath = form.hasSiteDefaultPipelineRoot() || form.getSupplementalPath() == null ? null : form.getSupplementalPath();
        URI supplementalRoot = supplementalPath == null ? null : validatePath(supplementalPath, errors);
        if (errors.hasErrors())
        {
            return false;
        }

        Map<String, MultipartFile> files = Collections.emptyMap();
        byte[] keyBytes = null;
        String keyPassword = form.getKeyPassword();
        byte[] certBytes = null;

        if (context.getRequest() instanceof MultipartHttpServletRequest)
            files = (Map<String, MultipartFile>)((MultipartHttpServletRequest)context.getRequest()).getFileMap();

        if (files.get("keyFile") != null)
        {
            keyBytes = files.get("keyFile").getBytes();
        }
        if (files.get("certFile") != null)
        {
            certBytes = files.get("certFile").getBytes();
        }
        GlobusKeyPair keyPair = null;
        if (!form.isUploadNewGlobusKeys())
        {
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(context.getContainer());
            if (pipeRoot != null)
            {
                keyPair = pipeRoot.getGlobusKeyPair();
            }
        }
        else if ((keyBytes != null && keyBytes.length > 0) || (certBytes != null && certBytes.length > 0) || keyPassword != null)
        {
            keyPair = new GlobusKeyPairImpl(keyBytes, keyPassword, certBytes);
            try
            {
                keyPair.validateMatch();
            }
            catch (GeneralSecurityException e)
            {
                errors.addError(new LabkeyError("Invalid Globus SSL configration: " + e.getMessage()));
                return false;
            }
        }

        if (supplementalRoot == null)
        {
            PipelineService.get().setPipelineRoot(context.getUser(), context.getContainer(), PipelineRoot.PRIMARY_ROOT, keyPair, form.isSearchable(), root);
        }
        else
        {
            PipelineService.get().setPipelineRoot(context.getUser(), context.getContainer(), PipelineRoot.PRIMARY_ROOT, keyPair, form.isSearchable(), root, supplementalRoot);
        }
        return true;
    }

    abstract public class AbstractSetupAction<FORM> extends FormViewAction<FORM>
    {
        abstract protected SetupField getFormField();

        protected void error(BindException errors, String message)
        {
            errors.rejectValue(getFormField().toString(), ERROR_MSG, message);
        }

        public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(getHelpTopic("pipelineSetup"));

            if (getViewContext().getRequest().getParameter(Params.overrideRoot.toString()) == null && !reshow)
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                if (root != null && !getContainer().equals(root.getContainer()))
                {
                    ActionURL urlOverride = getViewContext().cloneActionURL();
                    urlOverride.addParameter(Params.overrideRoot, "1");
                    ActionURL urlEditParent = urlSetup(root.getContainer());
                    StringBuilder html = new StringBuilder();
                    html.append("<p>This folder inherits its pipeline root settings from the folder '");
                    html.append(PageFlowUtil.filter(root.getContainer().getPath()));
                    html.append("'.<br>You can either ");
                    html.append(PageFlowUtil.textLink("override", urlOverride));
                    html.append(" the inherited settings in this folder,<br>or ");
                    html.append(PageFlowUtil.textLink("modify the setting for all folders", urlEditParent));
                    html.append(" by setting the value in the folder '");
                    html.append(PageFlowUtil.filter(root.getContainer().getPath()));
                    html.append("'.</p>");
                    return new HtmlView(html.toString());
                }
            }

            Container c = getContainer();
            VBox view = new VBox();

            if (!c.isRoot())
            {
                saveReferer();
                SetupForm bean = SetupForm.init(c);
                bean.setErrors(errors);

                PipeRoot pipeRoot = SetupForm.getPipelineRoot(c);

                if (pipeRoot != null)
                {
                    for (String errorMessage : pipeRoot.validate())
                    {
                        errors.addError(new LabkeyError(errorMessage));
                    }

                    if (!errors.hasErrors() && getViewContext().getRequest().getParameter(PipelineController.Params.rootset.toString()) != null)
                    {
                        bean.setConfirmMessage("The pipeline root was set to " + pipeRoot);
                    }
                }

                HBox main = new HBox();
                VBox leftBox = new VBox(PipelineService.get().getSetupView(bean));

                main.addView(leftBox);

                if (pipeRoot != null && !errors.hasErrors())
                {
                    main.addView(new PermissionView(SecurityManager.getPolicy(pipeRoot)));
                }
                main.setTitle("Data Processing Pipeline Setup");
                main.setFrame(WebPartView.FrameType.PORTAL);

                view.addView(main);

                if (!errors.hasErrors())
                {
                    Set<Module> activeModules = c.getActiveModules();
                    for (PipelineProvider provider : PipelineService.get().getPipelineProviders())
                    {
                        if (activeModules.contains(provider.getOwningModule()))
                        {
                            HttpView part = provider.getSetupWebPart(c);
                            if (part != null)
                                leftBox.addView(part);
                        }
                    }
                }
            }
            JspView<FORM> emailView = new JspView<FORM>("/org/labkey/pipeline/emailNotificationSetup.jsp", form);
            emailView.setFrame(WebPartView.FrameType.PORTAL);
            emailView.setTitle("Email Notification");
            view.addView(emailView);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Data Processing Pipeline Setup");
        }        
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseAction extends SimpleViewAction<PathForm>
    {
        public BrowseAction()
        {
        }

        public ModelAndView getView(PathForm pathForm, BindException errors) throws Exception
        {
            Path path = null;
            if (pathForm.getPath() != null)
            try { path = Path.parse(pathForm.getPath()); } catch (Exception x) { }
            BrowseWebPart wp = new BrowseWebPart(path);
            wp.getModelBean().setAutoResize(true);
            wp.setFrame(WebPartView.FrameType.NONE);
            return wp;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Manage files");
            return root;
        }
    }

    public static class BrowseWebPart extends FilesWebPart
    {
        public BrowseWebPart()
        {
            this(Path.rootPath);
        }

        public BrowseWebPart(Path startPath)
        {
            super(getContextContainer());

            FilesForm bean = getModelBean();
            ViewContext context = getViewContext();

            bean.setDirectory(startPath);
            //bean.setShowAddressBar(true);
            //bean.setShowFolderTree(true);
            bean.setFolderTreeCollapsed(false);
            bean.setShowDetails(true);
            bean.setAutoResize(false);
            bean.setStatePrefix(context.getContainer().getId() + "#fileContent");
            bean.setFileUploadCollapsed(true);

            // pipeline is always enabled
            bean.setEnabled(true);

            PipeRoot root = PipelineService.get().findPipelineRoot(context.getContainer());
            if (root != null)
            {
                bean.setRootPath(root.getWebdavURL());
                bean.setRootDirectory(root.getRootPath());
            }

            setTitle("Pipeline Files");
            setTitleHref(new ActionURL(BrowseAction.class, HttpView.getContextContainer()));
        }

        @Override
        protected SecurableResource getSecurableResource()
        {
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
            return pipeRoot == null ? getViewContext().getContainer() : pipeRoot;
        }

        @Override
        protected boolean canDisplayPipelineActions()
        {
            return true;
        }
    }

    public static class PipelineActionsForm extends PathForm
    {
        boolean _allActions;

        public boolean isAllActions()
        {
            return _allActions;
        }

        public void setAllActions(boolean allActions)
        {
            _allActions = allActions;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ActionsAction extends ApiAction<PipelineActionsForm>
    {
        public ApiResponse execute(PipelineActionsForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            PipeRootImpl pr = PipelineServiceImpl.get().findPipelineRoot(c);
            if (pr == null || !pr.isValid())
            {
                throw new NotFoundException("Pipeline root not set or does not exist on disk");
            }

            String relativePath = form.getPath();
            if (null == relativePath || "./".equals(relativePath))
                relativePath = "";
            if (relativePath.startsWith("/"))
                relativePath = relativePath.substring(1);

            File fileCurrent = pr.resolvePath(relativePath);
            if (!fileCurrent.exists())
                HttpView.throwNotFound("File not found: " + form.getPath());

            ActionURL browseURL = new ActionURL(BrowseAction.class, c);
            String browseParam = pr.relativePath(fileCurrent);
            if ("".equals(browseParam))
            {
                browseParam = "./";
            }
            browseURL.replaceParameter("path", browseParam);

            PipelineDirectoryImpl entry = new PipelineDirectoryImpl(pr, relativePath, browseURL);
            List<PipelineProvider> providers = PipelineService.get().getPipelineProviders();
            Set<Module> activeModules = c.getActiveModules();
            for (PipelineProvider provider : providers)
            {
                boolean showAllActions = form.isAllActions();
                if (provider.isShowActionsIfModuleInactive() || activeModules.contains(provider.getOwningModule()))
                    provider.updateFileProperties(getViewContext(), pr, entry, showAllActions);
            }

            // keep actions in consistent order for display
            entry.orderActions();
            JSONArray actions = new JSONArray();
            for (PipelineAction action : entry.getActions())
            {
                actions.put(action.toJSON());                
            }
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("actions", actions);

            return resp;
        }
    }

    public static class SaveOptionsForm implements CustomApiForm
    {
        private Map<String,Object> _props;

        public void bindProperties(Map<String, Object> props)
        {
            _props = props;
        }

        public Map<String,Object> getProps()
        {
            return _props;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdatePipelineActionConfigAction extends MutatingApiAction<SaveOptionsForm>
    {
        public ApiResponse execute(SaveOptionsForm form, BindException errors) throws Exception
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            FilesAdminOptions options = svc.getAdminOptions(getContainer());

            Map<String, Object> props = form.getProps();
            options.updateFromJSON(props);
            svc.setAdminOptions(getContainer(), options);

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetPipelineActionConfigAction extends ApiAction
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            Container container = getViewContext().getContainer();

            PipeRoot pr = PipelineService.get().findPipelineRoot(container);
            if (pr == null || !pr.isValid())
            {
                HttpView.throwNotFound("Pipeline root not set or does not exist on disk");
                return null;
            }
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            FilesAdminOptions options = svc.getAdminOptions(container);

            for (PipelineProvider provider : PipelineService.get().getPipelineProviders())
            {
                for (PipelineActionConfig config : provider.getDefaultActionConfig(container))
                    options.addDefaultPipelineConfig(config);
            }
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("config", options.toJSON());
            resp.putBeanList("fileProperties", getFileProperties(getContainer(), options.getFileConfig()));
            resp.put("success", true);

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetPipelineFilePropertiesAction extends ApiAction
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            FilesAdminOptions.fileConfig config = FilesAdminOptions.fileConfig.valueOf(getViewContext().getActionURL().getParameter("fileConfig"));

            resp.putBeanList("fileProperties", getFileProperties(getContainer(), config));
            resp.put("configOption", config.name());
            resp.put("success", true);

            return resp;
        }
    }

    private List<GWTPropertyDescriptor> getFileProperties(Container container, FilesAdminOptions.fileConfig config)
    {
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        List<GWTPropertyDescriptor> properties = new ArrayList<GWTPropertyDescriptor>();

        switch (config) {
            case useCustom:
                String uri = svc.getDomainURI(container, config);
                GWTDomain domain = DomainUtil.getDomainDescriptor(getUser(), uri, container);

                if (domain != null)
                {
                    for (Object o : domain.getFields())
                    {
                        if (o instanceof GWTPropertyDescriptor)
                            properties.add((GWTPropertyDescriptor)o);
                    }
                }
                break;
            case useDefault:
/*
                GWTPropertyDescriptor prop = new GWTPropertyDescriptor();

                prop.setLabel("Description");
                prop.setName("description");
                prop.setRangeURI("String");

                properties.add(prop);
*/
                break;
            case useParent:
                while (container != container.getProject())
                {
                    container = container.getParent();
                    FilesAdminOptions options = svc.getAdminOptions(container);

                    if (options.getFileConfig() != FilesAdminOptions.fileConfig.useParent)
                        return getFileProperties(container, options.getFileConfig());
                }
                FilesAdminOptions.fileConfig cfg = svc.getAdminOptions(container).getFileConfig();
                cfg = cfg != FilesAdminOptions.fileConfig.useParent ? cfg : FilesAdminOptions.fileConfig.useDefault;
                return getFileProperties(container, cfg);
        }
        return properties;
    }

    @RequiresSiteAdmin
    public class UpdateRootPermissionsAction extends RedirectAction<PermissionForm>
    {
        public ActionURL getSuccessURL(PermissionForm permissionForm)
        {
            return new ActionURL(SetupAction.class, getContainer());
        }

        public boolean doAction(PermissionForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            PipeRoot pipeRoot = getPipelineRoot(c);
            assert null == pipeRoot || pipeRoot.getContainer().getId().equals(c.getId());

            if (null != pipeRoot)
            {
                MutableSecurityPolicy policy = new MutableSecurityPolicy(pipeRoot);
                if (form.isEnable())
                {
                    Group[] groupsAll = SecurityManager.getGroups(c.getProject(), true);
                    Map<Integer,Group> map = new HashMap<Integer,Group>(groupsAll.length * 2);
                    for (Group g : groupsAll)
                        map.put(g.getUserId(),g);

                    int count = form.getSize();
                    for (int i=0 ; i<count ; i++)
                    {
                        Integer groupId = form.getGroups().get(i);
                        Group g = map.get(groupId);
                        if (null == g)
                            continue;
                        String roleName = form.getPerms().get(i);
                        if (roleName == null)
                            continue;
                        Role role = RoleManager.getRole(roleName);
                        if (null == role)
                            continue;
                        policy.addRoleAssignment(g, role);
                    }
                }

                // UNDONE: move setACL() to PipelineManager
                SecurityManager.savePolicy(policy);
                ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                        c, ContainerManager.Property.PipelineRoot, pipeRoot, pipeRoot);
                ContainerManager.firePropertyChangeEvent(evt);
            }

            return true;
        }

        public void validateCommand(PermissionForm target, Errors errors)
        {
        }
    }

    public static class PermissionForm
    {
        private ArrayList<Integer> groups = new FormArrayList<Integer>(Integer.class)
        {
            protected Integer newInstance() throws IllegalAccessException, InstantiationException
            {
                return Integer.valueOf(Integer.MIN_VALUE);
            }
        };
        private ArrayList<String> perms = new FormArrayList<String>(String.class);

        private boolean enable = false;

        public int getSize()
        {
            return Math.min(groups.size(),perms.size());
        }

        public boolean isEnable()
        {
            return enable;
        }

        public void setEnable(boolean enable)
        {
            this.enable = enable;
        }

        public ArrayList<Integer> getGroups()
        {
            return groups;
        }

        public void setGroups(ArrayList<Integer> groups)
        {
            this.groups = groups;
        }

        public ArrayList<String> getPerms()
        {
            return perms;
        }

        public void setPerms(ArrayList<String> perms)
        {
            this.perms = perms;
        }
    }

    public class PermissionView extends JspView<SecurityPolicy>
    {
        PermissionView(SecurityPolicy policy)
        {
            super(PipelineController.class, "permission.jsp", policy);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    //  Email notifications

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateEmailNotificationAction extends AbstractSetupAction<EmailNotificationForm>
    {
        protected SetupField getFormField()
        {
            // Note: This is never used because the <labkey:errors /> tag is used,
            //       and it does not allow for field errors.
            return SetupField.email;
        }

        public void validateCommand(EmailNotificationForm target, Errors errors)
        {
        }

        public boolean handlePost(EmailNotificationForm form, BindException errors) throws Exception
        {
            if (!form.getNotifyOnSuccess())
            {
                form.setNotifyOwnerOnSuccess(false);
                form.setNotifyUsersOnSuccess("");
                form.setSuccessNotifyInterval("");
                form.setSuccessNotifyStart("");
            }

            if (!form.getNotifyOnError())
            {
                form.setNotifyOwnerOnError(false);
                form.setNotifyUsersOnError("");
                form.setEscalationUsers("");
                form.setFailureNotifyInterval("");
                form.setFailureNotifyStart("");
            }

            validateStartTime(form.getSuccessNotifyStart(), errors);
            validateStartTime(form.getFailureNotifyStart(), errors);

            Container c = getContainer();
            PipelineEmailPreferences pref = PipelineEmailPreferences.get();
            pref.setNotifyOwnerOnSuccess(form.getNotifyOwnerOnSuccess(), c);
            pref.setNotifyUsersOnSuccess(getValidEmailList(form.getNotifyUsersOnSuccess(), errors), c);
            pref.setSuccessNotificationInterval(
                    form.getSuccessNotifyInterval(),
                    form.getSuccessNotifyStart(),
                    c);
            pref.setNotifyOwnerOnError(form.getNotifyOwnerOnError(), c);
            pref.setNotifyUsersOnError(getValidEmailList(form.getNotifyUsersOnError(), errors), c);
            pref.setEscalationUsers(getValidEmailList(form.getEscalationUsers(), errors), c);
            pref.setFailureNotificationInterval(
                    form.getFailureNotifyInterval(),
                    form.getFailureNotifyStart(),
                    c);

            return errors.getGlobalErrorCount() == 0;
        }

        public ActionURL getSuccessURL(EmailNotificationForm form)
        {
            return urlSetup(getContainer());
        }

        private void validateStartTime(String startTime, BindException errors)
        {
            try {
                if (!StringUtils.isEmpty(startTime))
                    DateUtil.parseDateTime(startTime, "H:mm");
            }
            catch (ParseException pe)
            {
                errors.reject(ERROR_MSG, "Invalid time format: " + startTime);
            }
        }

        private String getValidEmailList(String emailString, BindException errors)
        {
            String[] rawEmails = StringUtils.trimToEmpty(emailString).split("\n");
            List<String> invalidEmails = new ArrayList<String>();
            List<ValidEmail> emails = org.labkey.api.security.SecurityManager.normalizeEmails(rawEmails, invalidEmails);
            StringBuilder builder = new StringBuilder();

            for (ValidEmail email : emails)
            {
                builder.append(email.getEmailAddress());
                builder.append(';');
            }
            for (String rawEmail : invalidEmails)
            {
                String e = StringUtils.trimToNull(rawEmail);
                if (null != e)
                {
                    errors.reject(ERROR_MSG, "Invalid email address: " + e);
                }
            }
            return builder.toString();
        }
    }

    public static class EmailNotificationForm
    {
        boolean _notifyOnSuccess;
        boolean _notifyOnError;
        boolean _notifyOwnerOnSuccess;
        boolean _notifyOwnerOnError;
        String _notifyUsersOnSuccess;
        String _notifyUsersOnError;
        String _escalationUsers;
        String _successNotifyInterval;
        String _failureNotifyInterval;
        String _successNotifyStart;
        String _failureNotifyStart;

        public boolean getNotifyOnSuccess()
        {
            return _notifyOnSuccess;
        }

        public void setNotifyOnSuccess(boolean notifyOnSuccess)
        {
            _notifyOnSuccess = notifyOnSuccess;
        }

        public boolean getNotifyOnError()
        {
            return _notifyOnError;
        }

        public void setNotifyOnError(boolean notifyOnError)
        {
            _notifyOnError = notifyOnError;
        }

        public boolean getNotifyOwnerOnSuccess()
        {
            return _notifyOwnerOnSuccess;
        }

        public void setNotifyOwnerOnSuccess(boolean notifyOwnerOnSuccess)
        {
            _notifyOwnerOnSuccess = notifyOwnerOnSuccess;
        }

        public boolean getNotifyOwnerOnError()
        {
            return _notifyOwnerOnError;
        }

        public void setNotifyOwnerOnError(boolean notifyOwnerOnError)
        {
            _notifyOwnerOnError = notifyOwnerOnError;
        }

        public String getNotifyUsersOnSuccess()
        {
            return _notifyUsersOnSuccess;
        }

        public void setNotifyUsersOnSuccess(String notifyUsersOnSuccess)
        {
            _notifyUsersOnSuccess = notifyUsersOnSuccess;
        }

        public String getNotifyUsersOnError()
        {
            return _notifyUsersOnError;
        }

        public void setNotifyUsersOnError(String notifyUsersOnError)
        {
            _notifyUsersOnError = notifyUsersOnError;
        }

        public String getEscalationUsers()
        {
            return _escalationUsers;
        }

        public void setEscalationUsers(String escalationUsers)
        {
            _escalationUsers = escalationUsers;
        }

        public String getSuccessNotifyInterval()
        {
            return _successNotifyInterval;
        }

        public void setSuccessNotifyInterval(String interval)
        {
            _successNotifyInterval = interval;
        }

        public String getFailureNotifyInterval()
        {
            return _failureNotifyInterval;
        }

        public void setFailureNotifyInterval(String interval)
        {
            _failureNotifyInterval = interval;
        }

        public String getSuccessNotifyStart()
        {
            return _successNotifyStart;
        }

        public void setSuccessNotifyStart(String start)
        {
            _successNotifyStart = start;
        }

        public String getFailureNotifyStart()
        {
            return _failureNotifyStart;
        }

        public void setFailureNotifyStart(String start)
        {
            _failureNotifyStart = start;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ResetEmailNotificationAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            PipelineEmailPreferences.get().deleteAll(getContainer());

            return new ActionURL(SetupAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CompleteUserAction extends AjaxCompletionAction<CompleteUserForm>
    {
        public List<AjaxCompletion> getCompletions(CompleteUserForm form, BindException errors) throws Exception
        {
            return UserManager.getAjaxCompletions(form.getPrefix(), getViewContext().getUser());
        }
    }

    public static class CompleteUserForm
    {
        private String _prefix;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }
    }

/////////////////////////////////////////////////////////////////////////////
//  Direct access to the PipelineQueue

    public enum StatusParams { allcontainers }

    public static ActionURL urlStatus(Container container, boolean allContainers)
    {
        ActionURL url = new ActionURL(StatusAction.class, container);
        if (allContainers)
            url.addParameter(StatusParams.allcontainers, "1");
        return url;
    }

    /**
     * Use the current container and the current "allcontainers" value to
     * produce a URL for the status action.
     *
     * @return URL to the status action
     */
    private ActionURL urlStatus()
    {
        boolean allContainers = (getViewContext().getRequest().getParameter(StatusParams.allcontainers.toString()) != null);
        
        return urlStatus(getContainer(), allContainers);
    }

    @RequiresSiteAdmin
    public class StatusAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            // Job data is only available from the mini-pipeline.
            if (PipelineService.get().isEnterprisePipeline())
                return HttpView.throwNotFound();
            
            setHelpTopic(getHelpTopic("pipeline/status"));

            PipelineQueue queue = PipelineService.get().getPipelineQueue();
            return new JspView<StatusModel>("/org/labkey/pipeline/pipelineStatus.jsp",
                    new StatusModel(queue.getJobDataInMemory(getJobDataContainer())));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Pipeline Status");
        }
    }

    public class StatusModel
    {
        private PipelineJobData _jobData;

        private StatusModel(PipelineJobData jobData)
        {
            _jobData = jobData;
        }

        public PipelineJobData getJobData()
        {
            return _jobData;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class CancelJobAction extends SimpleRedirectAction<JobIdForm>
    {
        public ActionURL getRedirectURL(JobIdForm form) throws Exception
        {
            // This is not a valid URL for cancelling a job in the Enterprise
            // Pipeline, since it redirects to the mini-pipeline queue status
            // page.
            if (PipelineService.get().isEnterprisePipeline())
                HttpView.throwNotFound();

            PipelineQueue queue = PipelineService.get().getPipelineQueue();
            boolean success = queue.cancelJob(getJobDataContainer(), form.getJobId());
            return urlStatus();
        }
    }

    public static class JobIdForm
    {
        private String _jobId;

        public String getJobId()
        {
            return _jobId;
        }

        public void setJobId(String jobId)
        {
            _jobId = jobId;
        }
    }

    protected Container getJobDataContainer() throws Exception
    {
        if (getUser().isAdministrator() &&
                getViewContext().getRequest().getParameter(StatusParams.allcontainers.toString()) != null)
        {
            return null;
        }
        return getContainer();
    }

    protected PipeRoot getPipelineRoot(Container c)
    {
        PipeRoot p = PipelineService.get().findPipelineRoot(c);
        if (p != null && p.getContainer() != null && p.getContainer().getId().equals(c.getId()))
            return p;
        return null;
    }

/////////////////////////////////////////////////////////////////////////////
//  File download support

    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadAction extends SimpleViewAction<PathForm>
    {
        public ModelAndView getView(PathForm form, BindException errors) throws Exception
        {
            PipeRoot pipeRoot = getPipelineRoot(getContainer());
            if (null == pipeRoot || null == StringUtils.trimToNull(form.getPath()))
                return HttpView.throwNotFound();

            // check pipeline ACL
            if (!org.labkey.api.security.SecurityManager.getPolicy(pipeRoot).hasPermission(getUser(), ReadPermission.class))
                return HttpView.throwUnauthorized();

            File file = pipeRoot.resolvePath(form.getPath());
            if (!file.exists() || !file.isFile())
                return HttpView.throwNotFound();

            PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    /**
     * <code>PathForm</code> is heavily used in browsing a pipeline root.
     */
    public static class PathForm
    {
        // TODO: Action forms also depend on the path parameter.  Move it to API?
        enum Params { path }

        private String _path;

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetPipelineContainerAction extends ApiAction
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
            resp.put("containerPath", null != root ? root.getContainer().getPath() : null);
            resp.put("webDavURL", null != root ? root.getWebdavURL() : null);
            return resp;
        }
    }

/////////////////////////////////////////////////////////////////////////////
//  Public URL interface to this controller

    public static void registerAdminConsoleLinks()
    {
        ActionURL url = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(ContainerManager.getRoot());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "pipeline email notification", url);
    }

    public static class PipelineUrlsImp implements PipelineUrls
    {
        public ActionURL urlBrowse(Container container, String referer)
        {
            return urlBrowse(container, referer, null);
        }

        public ActionURL urlBrowse(Container container, String referer, @Nullable String path)
        {
            ActionURL url = new ActionURL(BrowseAction.class, container);
            url.addParameter(Params.referer, referer);
            if (path != null)
            {
                url.addParameter(Params.path, path);
            }
            return url;
        }

        public ActionURL urlReferer(Container container)
        {
            return new ActionURL(ReturnToRefererAction.class, container);
        }

        public ActionURL urlSetup(Container container)
        {
            return PipelineController.urlSetup(container);
        }

        public ActionURL urlBegin(Container container)
        {
            return PipelineController.urlBegin(container);
        }

        public ActionURL urlActions(Container container)
        {
            return new ActionURL(ActionsAction.class, container);
        }
    }

    public static ActionURL urlBegin(Container container)
    {
        return new ActionURL(BeginAction.class, container); 
    }
}
