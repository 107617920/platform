/*
 * Copyright (c) 2003-2011 Fred Hutchinson Cancer Research Center
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
package org.labkey.core.security;

import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.FormattedError;
import org.labkey.api.action.LabkeyError;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityMessage;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.DotRunner;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HBox;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ContainerUser;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.user.UserController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.PageFlowUtil.filter;

public class SecurityController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(SecurityController.class,
        SecurityApiActions.GetGroupPermsAction.class,
        SecurityApiActions.GetUserPermsAction.class,
        SecurityApiActions.GetGroupsForCurrentUserAction.class,
        SecurityApiActions.EnsureLoginAction.class,
        SecurityApiActions.GetRolesAction.class,
        SecurityApiActions.GetSecurableResourcesAction.class,
        SecurityApiActions.GetPolicyAction.class,
        SecurityApiActions.SavePolicyAction.class,
        SecurityApiActions.DeletePolicyAction.class,
        SecurityApiActions.CreateGroupAction.class,
        SecurityApiActions.DeleteGroupAction.class,
        SecurityApiActions.AddGroupMemberAction.class,
        SecurityApiActions.RemoveGroupMemberAction.class,
        SecurityApiActions.CreateNewUserAction.class,
        SecurityApiActions.RenameGroupAction.class);

    public SecurityController()
    {
        setActionResolver(_actionResolver);
    }

    public static class SecurityUrlsImpl implements SecurityUrls
    {
        public ActionURL getManageGroupURL(Container container, String groupName)
        {
            ActionURL url = new ActionURL(GroupAction.class, container);
            return url.addParameter("group", groupName);
        }

        public ActionURL getGroupPermissionURL(Container container, int id)
        {
            ActionURL url = new ActionURL(GroupPermissionAction.class, container);
            return url.addParameter("id", id);
        }

        public ActionURL getProjectURL(Container container)
        {
            return new ActionURL(ProjectAction.class, container);
        }

        public ActionURL getProjectURL(Container container, ActionURL returnURL)
        {
            ActionURL url = new ActionURL(ProjectAction.class, container);
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getContainerURL(Container container)
        {
            return new ActionURL(ProjectAction.class, container);
        }

        public String getCompleteUserURLPrefix(Container container)
        {
            ActionURL url = new ActionURL(CompleteUserAction.class, container);
            url.addParameter("prefix", "");
            return url.getLocalURIString();
        }

        public ActionURL getBeginURL(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        public ActionURL getShowRegistrationEmailURL(Container container, ValidEmail email, String mailPrefix)
        {
            ActionURL url = new ActionURL(ShowRegistrationEmailAction.class, container);
            url.addParameter("email", email.getEmailAddress());
            url.addParameter("mailPrefix", mailPrefix);

            return url;
        }

        public ActionURL getUpdateMembersURL(Container container, String groupPath, String deleteEmail, boolean quickUI)
        {
            ActionURL url = new ActionURL(UpdateMembersAction.class, container);

            if (quickUI)
                url.addParameter("quickUI", "1");

            url.addParameter("group", groupPath);
            url.addParameter("delete", deleteEmail);

            return url;
        }

        @Override
        public ActionURL getAddUsersURL()
        {
            return new ActionURL(AddUsersAction.class, ContainerManager.getRoot());
        }

        public ActionURL getFolderAccessURL(Container container)
        {
            return new ActionURL(FolderAccessAction.class, container);
        }
    }

    private static void ensureGroupInContainer(Group group, Container c)
            throws ServletException
    {
        if (group.getContainer() == null)
        {
            if (!c.isRoot())
            {
                throw new UnauthorizedException();
            }
        }
        else
        {
            if (!c.getId().equals(group.getContainer()))
            {
                throw new UnauthorizedException();
            }
        }
    }
    

    private static void ensureGroupInContainer(String group, Container c) throws ServletException
    {
        if (group.startsWith("/"))
            group = group.substring(1);
        if (!group.contains("/"))
        {
            if (!c.isRoot())
            {
                throw new UnauthorizedException();
            }
        }
        else
        {
            String groupContainer = group.substring(0, group.lastIndexOf("/"));
            if (c.isRoot())
            {
                throw new UnauthorizedException();
            }
            String projectContainer = c.getPath();
            if (projectContainer.startsWith("/"))
                projectContainer = projectContainer.substring(1);
            if (!projectContainer.equals(groupContainer))
            {
                throw new UnauthorizedException();
            }
        }
    }

    @RequiresNoPermission
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (null == getContainer() || getContainer().isRoot())
            {
                throw new RedirectException(new ActionURL(AddUsersAction.class, ContainerManager.getRoot()));
            }
            throw new RedirectException(new ActionURL(ProjectAction.class, getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ProjectAction extends SimpleViewAction<PermissionsForm>
    {
        public ModelAndView getView(PermissionsForm form, BindException errors) throws Exception
        {
            String resource = getContainer().getId();
            ActionURL doneURL = form.isWizard() ? getContainer().getFolderType().getStartURL(getContainer(), getUser()) : form.getReturnActionURL();

            FolderPermissionsView permsView = new FolderPermissionsView(resource, doneURL);

            getPageConfig().setTemplate(PageConfig.Template.Dialog);

            return permsView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            Container c = getContainer();
            String title;
            if (c.isRoot())
                title = "Site Permissions";
            else if (c.isProject())
                title = "Project Permissions";
            else
                title = "Folder Permissions";
            root.addChild(title + " for " + c.getPath());
            return root;
        }
    }


    public class FolderPermissionsView extends JspView<FolderPermissionsView>
    {
        public final String resource;
        public final ActionURL doneURL;
        
        FolderPermissionsView(String resource, ActionURL doneURL)
        {
            super(SecurityController.class, "FolderPermissions.jsp", null);
            this.setModelBean(this);
            this.setFrame(FrameType.NONE);
            this.resource = resource;
            this.doneURL = doneURL;
        }
    }


    public static class GroupForm
    {
        private String group = null;
        private int id = Integer.MIN_VALUE;

        public void setId(int id)
        {
            this.id = id;
        }

        public int getId()
        {
            return id;
        }

        public void setGroup(String name)
        {
            group = name;
        }

        public String getGroup()
        {
            return group;
        }

        // validates that group is visible from container c
        public Group getGroupFor(Container c) throws ServletException
        {
            if (id == Integer.MIN_VALUE)
            {
                Integer gid = SecurityManager.getGroupId(group);
                if (gid == null)
                    return null;
                id = gid.intValue();
            }
            Group group = SecurityManager.getGroup(id);
            Container p = c == null ? null : c.getProject();
            if (null != p)
            {
                if (group.getContainer() != null && !p.getId().equals(group.getContainer()))
                {
                    throw new UnauthorizedException();
                }
            }
            return group;
        }
    }

    @RequiresPermissionClass(AdminPermission.class) @CSRF
    public class StandardDeleteGroupAction extends FormHandlerAction<GroupForm>
    {
        public void validateCommand(GroupForm form, Errors errors) {}

        public boolean handlePost(GroupForm form, BindException errors) throws Exception
        {
            Group group = form.getGroupFor(getContainer());
            ensureGroupInContainer(group,getContainer());
            if (group != null)
            {
                SecurityManager.deleteGroup(group);
                addGroupAuditEvent(getViewContext(), group, "The group: " + group.getPath() + " was deleted.");
            }
            return true;
        }

        public ActionURL getSuccessURL(GroupForm form)
        {
            return new ActionURL(ProjectAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class GroupsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return getGroupsView(getContainer(), null, errors, Collections.<String>emptyList());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Groups");
        }
    }

    public static class GroupsBean
    {
        ViewContext _context;
        Container _container;
        Group[] _groups;
        String _expandedGroupPath;
        List<String> _messages;

        public GroupsBean(ViewContext context, Group expandedGroupPath, List<String> messages)
        {
            Container c = context.getContainer();
            if (null == c || c.isRoot())
            {
                _groups = SecurityManager.getGroups(null, false);
                _container = ContainerManager.getRoot();
            }
            else
            {
                _groups = SecurityManager.getGroups(c.getProject(), false);
                _container = c;
            }
            _expandedGroupPath = expandedGroupPath == null ? null : expandedGroupPath.getPath();
            _messages = messages != null ? messages : Collections.<String>emptyList();
        }

        public Container getContainer()
        {
            return _container.isRoot() ? _container : _container.getProject();
        }

        public Group[] getGroups()
        {
            return _groups;
        }

        public boolean isExpandedGroup(String groupPath)
        {
            return _expandedGroupPath != null && _expandedGroupPath.equals(groupPath);
        }

        public List<String> getMessages()
        {
            return _messages;
        }
    }

    private HttpView getGroupsView(Container container, Group expandedGroup, BindException errors, List<String> messages)
    {
        JspView<GroupsBean> groupsView = new JspView<GroupsBean>("/org/labkey/core/security/groups.jsp", new GroupsBean(getViewContext(), expandedGroup, messages), errors);
        if (null == container || container.isRoot())
            groupsView.setTitle("Site Groups");
        else
            groupsView.setTitle("Groups for project " + container.getProject().getName());
        return groupsView;
    }
    

    private ModelAndView renderContainerPermissions(Group expandedGroup, BindException errors, List<String> messages, boolean wizard) throws Exception
    {
        Container c = getContainer();
        Container project = c.getProject();

        // If we are working with the root container, project will be null.
        // This causes ContainersView() to fail, so handle specially.
        if (project == null)
            project = ContainerManager.getRoot();

        HBox body = new HBox();
        VBox projectViews = new VBox();
        //don't display folder tree if we are in root
        if (!project.isRoot())
        {
            projectViews.addView(new ContainersView(project));
        }

        // Display groups only if user has permissions in this project (or the root)
        if (project.hasPermission(getUser(), AdminPermission.class))
        {
            projectViews.addView(getGroupsView(c, expandedGroup, errors, messages));

            UserController.ImpersonateView impersonateView = new UserController.ImpersonateView(project, getUser(), false);

            if (impersonateView.hasUsers())
                projectViews.addView(impersonateView);
        }

        ActionURL startURL = c.getFolderType().getStartURL(c, getUser());
        projectViews.addView(new HtmlView(PageFlowUtil.generateButton("Done", startURL)));
        if(c.isRoot())
            body.addView(projectViews);
        else
        {
            body.addView(projectViews, "60%");
            body.addView(new PermissionsDetailsView(c, "container"), "40%");
        }
        if (wizard)
        {
            VBox outer = new VBox();
            String message = "Use this page to ensure that only appropriate users have permission to view and edit the new " + (c.isProject() ? "Project" : "folder");
            outer.addView(new HtmlView(message));
            outer.addView(body);
            return outer;
        }

        return body;
    }


    public static class PermissionsForm extends ReturnUrlForm
    {
        private boolean _wizard;
        private String _inherit;
        private String _objectId;

        public boolean isWizard()
        {
            return _wizard;
        }

        public void setWizard(boolean wizard)
        {
            _wizard = wizard;
        }

        public String getInherit()
        {
            return _inherit;
        }

        public void setInherit(String inherit)
        {
            _inherit = inherit;
        }

        public String getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(String objectId)
        {
            _objectId = objectId;
        }
    }


    public static class GroupResponse extends ApiSimpleResponse
    {
        GroupResponse(Group group)
        {
            Map<String, Object> map = ObjectFactory.Registry.getFactory(Group.class).toMap(group, new HashMap<String, Object>());
            List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(group.getUserId());
            map.put("members",members);
            put("success", true);
            put("group",map);
        }
    }


    @RequiresPermissionClass(AdminPermission.class) @CSRF
    public class NewGroupAction extends FormViewAction<NewGroupForm>
    {
        public ModelAndView getView(NewGroupForm form, boolean reshow, BindException errors) throws Exception
        {
            return renderContainerPermissions(null, errors, null, false);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Permissions");
        }
        
        public boolean handlePost(NewGroupForm form, BindException errors) throws Exception
        {
            // UNDONE: use form validation
            String name = form.getName();
            String error;
            if (name == null || name.length() == 0)
            {
                error = "Group name cannot be empty.";
            }
            else
            {
                error  = UserManager.validGroupName(name, Group.typeProject);
            }

            if (null == error)
            {
                try
                {
                    Group group = SecurityManager.createGroup(getContainer().getProject(), name);
                    addGroupAuditEvent(getViewContext(), group, "The group: " + name + " was created.");
                }
                catch (IllegalArgumentException e)
                {
                    error = e.getMessage();
                }
            }
            if (error != null)
            {
                errors.addError(new LabkeyError(error));
                return false;
            }
            return true;
        }

        public void validateCommand(NewGroupForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(NewGroupForm newGroupForm)
        {
            return new ActionURL(ProjectAction.class, getContainer());
        }
    }

    private void addGroupAuditEvent(ContainerUser context, Group group, String message)
    {
        AuditLogEvent event = new AuditLogEvent();

        event.setEventType(GroupManager.GROUP_AUDIT_EVENT);
        event.setCreatedBy(context.getUser());
        event.setIntKey2(group.getUserId());
        Container c = ContainerManager.getForId(group.getContainer());
        if (c != null && c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        else
            event.setProjectId(ContainerManager.getRoot().getId());
        event.setComment(message);
        event.setContainerId(context.getContainer().getId());

        AuditLogService.get().addEvent(event);
    }

    public static class NewGroupForm
    {
        private String _name;

        public void setName(String name)
        {
            _name = name;
        }

        public String getName()
        {
            return _name;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateMembersAction extends SimpleViewAction<UpdateMembersForm>
    {
        private Group _group;
        private boolean _showGroup;

        public ModelAndView getView(UpdateMembersForm form, BindException errors) throws Exception
        {
            // 1 - Global admins group cannot be empty
            // 2 - warn if you are deleting yourself from global or project admins
            // 3 - if user confirms delete, post to action again, with list of users to delete and confirmation flag.

            Container container = getContainer();

            if (!container.isRoot() && !container.isProject())
                container = container.getProject();

            _group = form.getGroupFor(getContainer());
            if (null == _group)
                throw new RedirectException(new ActionURL(ProjectAction.class, container));

            List<String> messages = new ArrayList<String>();

            //check for new users to add.
            String[] addNames = form.getNames() == null ? new String[0] : form.getNames().split("\n");

            // split the list of names to add into groups and users (emails)
            List<Group> addGroups = new ArrayList<Group>();
            List<String> emails = new ArrayList<String>();
            for (String name : addNames)
            {
                // check for the groupId in the global group list or in the project
                Integer gid = SecurityManager.getGroupId(null, StringUtils.trim(name), false);
                Integer pid = SecurityManager.getGroupId(container, StringUtils.trim(name), false);

                if (null != gid || null != pid)
                {
                    Group g = (gid != null ? SecurityManager.getGroup(gid) : SecurityManager.getGroup(pid));
                    addGroups.add(g);
                }
                else
                {
                    emails.add(name);
                }
            }

            List<String> invalidEmails = new ArrayList<String>();
            List<ValidEmail> addEmails = SecurityManager.normalizeEmails(emails, invalidEmails);

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                String e = StringUtils.trimToNull(rawEmail);
                if (null != e)
                    errors.reject(ERROR_MSG, "Could not add user " + filter(e) + ": Invalid email address");
            }

            String[] removeNames = form.getDelete();
            invalidEmails.clear();
            
            // delete group members by ID (can be both groups and users)
            List<UserPrincipal> removeIds = new ArrayList<UserPrincipal>();
            if (removeNames != null && removeNames.length > 0)
            {
                for (String removeName : removeNames)
                {
                    // first check if the member name is a site group, otherwise get principal based on this container
                    Integer id = SecurityManager.getGroupId(null, removeName, false);
                    if (null != id)
                        removeIds.add(SecurityManager.getGroup(id));
                    else
                        removeIds.add(SecurityManager.getPrincipal(removeName, container));
                }
            }

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                String e = StringUtils.trimToNull(rawEmail);
                if (null != e)
                    errors.reject(ERROR_MSG, "Could not remove user " + filter(e) + ": Invalid email address");
            }

            if (_group != null)
            {
                //check for users to delete
                if (removeNames != null)
                {
                    //if this is the site admins group and user is attempting to remove all site admins, display error.
                    if (_group.getUserId() == Group.groupAdministrators)
                    {
                        //get list of group members to determine how many there are
                        Set<UserPrincipal> userMembers = SecurityManager.getGroupMembers(_group, SecurityManager.GroupMemberType.Users);

                        if (removeNames.length == userMembers.size())
                            errors.addError(new LabkeyError("The Site Administrators group must always contain at least one member. You cannot remove all members of this group."));
                    }

                    //if this is site or project admins group and user is removing themselves, display warning.
                    else if (_group.getName().compareToIgnoreCase("Administrators") == 0
                            && Arrays.asList(removeNames).contains(getUser().getEmail())
                            && !form.isConfirmed())
                    {
                        //display warning form, including users to delete and add
                        HttpView<UpdateMembersBean> v = new JspView<UpdateMembersBean>("/org/labkey/core/security/deleteUser.jsp", new UpdateMembersBean());

                        UpdateMembersBean bean = v.getModelBean();
                        bean.addnames = addNames;
                        bean.removenames = removeNames;
                        bean.groupName = _group.getName();
                        bean.mailPrefix = form.getMailPrefix();

                        getPageConfig().setTemplate(PageConfig.Template.Dialog);
                        return v;
                    }
                    else
                    {
                        SecurityManager.deleteMembers(_group, removeIds);
                    }
                }

                if (addGroups.size() > 0 || addEmails.size() > 0)
                {
                    // add new users
                    List<User> addUsers = new ArrayList<User>(addEmails.size());
                    for (ValidEmail email : addEmails)
                    {
                        String addMessage = SecurityManager.addUser(getViewContext(), email, form.getSendEmail(), form.getMailPrefix(), null);
                        if (addMessage != null)
                            messages.add(addMessage);

                        // get the user and ensure that the user is still active
                        User user = UserManager.getUser(email);

                        // Null check since user creation may have failed, #8066
                        if (null != user)
                        {
                            if (!user.isActive())
                                errors.reject(ERROR_MSG, "You may not add the user '" + PageFlowUtil.filter(email)
                                    + "' to this group because that user account is currently deactivated." +
                                    " To re-activate this account, contact your system administrator.");
                            else
                                addUsers.add(user);
                        }
                    }

                    List<String> addErrors = SecurityManager.addMembers(_group, addGroups);
                    addErrors.addAll(SecurityManager.addMembers(_group, addUsers));

                    for (String error : addErrors)
                        errors.reject(ERROR_MSG, error);
                }
            }

            if (form.isQuickUI())
                return renderContainerPermissions(_group, errors, messages, false);
            else
            {
                _showGroup = true;
                return renderGroup(_group, errors, messages);
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_showGroup)
            {
                return addGroupNavTrail(root, _group);
            }
            else
            {
                root.addChild("Permissions");
                return root;
            }
        }
    }

    public static class UpdateMembersBean
    {
        public String[] addnames;
        public String[] removenames;
        public String groupName;
        public String mailPrefix;
    }

    public static class UpdateMembersForm extends GroupForm
    {
        private String names;
        private String[] delete;
        private boolean sendEmail;
        private boolean confirmed;
        private String mailPrefix;
        // flag to indicate whether this modification was made via the 'quick ui'
        // if so, we'll redirect to a different page when we're done.
        private boolean quickUI;

        public boolean isConfirmed()
        {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed)
        {
            this.confirmed = confirmed;
        }

        public void setNames(String names)
        {
            this.names = names;
        }

        public String getNames()
        {
            return this.names;
        }

        public String[] getDelete()
        {
            return delete;
        }

        public void setDelete(String[] delete)
        {
            this.delete = delete;
        }

        public boolean getSendEmail()
        {
            return sendEmail;
        }

        public void setSendEmail(boolean sendEmail)
        {
            this.sendEmail = sendEmail;
        }

        public String getMailPrefix()
        {
            return mailPrefix;
        }

        public void setMailPrefix(String messagePrefix)
        {
            this.mailPrefix = messagePrefix;
        }

        public boolean isQuickUI()
        {
            return quickUI;
        }

        public void setQuickUI(boolean quickUI)
        {
            this.quickUI = quickUI;
        }
    }


    private NavTree addGroupNavTrail(NavTree root, Group group)
    {
        root.addChild("Permissions", new ActionURL(ProjectAction.class, getContainer()));
        root.addChild("Manage Group");
        root.addChild(group.getName() + " Group");
        return root;
    }

    private ModelAndView renderGroup(Group group, BindException errors, List<String> messages) throws ServletException
    {
        // validate that group is in the current project!
        Container c = getContainer();
        ensureGroupInContainer(group, c);
        Set<UserPrincipal> members = SecurityManager.getGroupMembers(group, SecurityManager.GroupMemberType.Both);
        Map<UserPrincipal, String> redundantMembers = SecurityManager.getRedundantGroupMembers(group);
        VBox view = new VBox(new GroupView(group, members, redundantMembers, messages, group.isSystemGroup(), errors));

        if (getUser().isAdministrator())
        {
            AuditLogQueryView log = GroupAuditViewFactory.getInstance().createGroupView(getViewContext(), group.getUserId());
            log.setFrame(WebPartView.FrameType.TITLE);
            view.addView(log);
        }

        return view;
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class GroupAction extends SimpleViewAction<GroupForm>
    {
        private Group _group;

        public ModelAndView getView(GroupForm form, BindException errors) throws Exception
        {
            _group = form.getGroupFor(getContainer());
            if (null == _group)
                throw new RedirectException(new ActionURL(ProjectAction.class, getContainer()));
            return renderGroup(_group, errors, Collections.<String>emptyList());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return addGroupNavTrail(root, _group);
        }
    }

    public static class CompleteMemberForm
    {
        private String _prefix;
        private Integer _groupId;
        private Group _group;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }

        public Integer getGroupId()
        {
            return _groupId;
        }

        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }

        public Group getGroup()
        {
            if (_group == null && getGroupId() != null)
            {
                _group = SecurityManager.getGroup(getGroupId());
                if (_group == null)
                {
                    throw new NotFoundException("Could not find group for id " + getGroupId());
                }
            }

            return _group;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CompleteMemberAction extends SimpleViewAction<CompleteMemberForm>
    {
        public ModelAndView getView(CompleteMemberForm form, BindException errors) throws Exception
        {
            Group[] allGroups = SecurityManager.getGroups(getContainer().getProject(), true);
            Collection<Group> groups = new ArrayList<Group>();
            // dont' suggest groups that will results in errors (i.e. circulate relation, already member, etc.)
            for (Group group : allGroups)
            {
                if (null == SecurityManager.getAddMemberError(form.getGroup(), group))
                    groups.add(group);
            }

            User[] allUsers = UserManager.getActiveUsers();
            Collection<User> users = new ArrayList<User>();
            // dont' suggest users that will results in errors (i.e. already member, etc.)
            for (User user : allUsers)
            {
                if (null == SecurityManager.getAddMemberError(form.getGroup(), user))
                    users.add(user);
            }

            List<AjaxCompletion> completions = UserManager.getAjaxCompletions(form.getPrefix(), groups, users, getViewContext().getUser());
            PageFlowUtil.sendAjaxCompletions(getViewContext().getResponse(), completions);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
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

    @RequiresPermissionClass(AdminPermission.class)
    public class CompleteUserAction extends SimpleViewAction<CompleteUserForm>
    {
        public ModelAndView getView(CompleteUserForm form, BindException errors) throws Exception
        {
            List<AjaxCompletion> completions = UserManager.getAjaxCompletions(form.getPrefix(), getViewContext().getUser());
            PageFlowUtil.sendAjaxCompletions(getViewContext().getResponse(), completions);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class GroupExportAction extends ExportAction<GroupForm>
    {
        public void export(GroupForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            String group = form.getGroup();
            if (group.startsWith("/"))
                group = group.substring(1);
            // validate that group is in the current project!
            Container c = getContainer();
            ensureGroupInContainer(group, c);
            List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(group);

            DataRegion rgn = new DataRegion();
            List<ColumnInfo> columns = CoreSchema.getInstance().getTableInfoUsers().getColumns(UserController.getUserColumnNames(getUser(), c));
            rgn.setColumns(columns);
            RenderContext ctx = new RenderContext(getViewContext());
            List<Integer> userIds = new ArrayList<Integer>();
            final List<Pair<Integer, String>> memberGroups = new ArrayList<Pair<Integer, String>>();
            for (Pair<Integer, String> member : members)
            {
                Group g = SecurityManager.getGroup(member.getKey());
                if (null == g)
                {
                    userIds.add(member.getKey());
                }
                else
                {
                    memberGroups.add(member);
                }
            }
            SimpleFilter filter = new SimpleFilter();
            filter.addInClause("UserId", userIds);
            ctx.setBaseFilter(filter);
            ExcelWriter ew = new ExcelWriter(rgn.getResultSet(ctx), rgn.getDisplayColumns())
            {
                @Override
                public void renderGrid(Sheet sheet, List<ExcelColumn> visibleColumns) throws SQLException, MaxRowsExceededException
                {
                    RenderContext ctx = new RenderContext(HttpView.currentContext());
                    for (Pair<Integer, String> memberGroup : memberGroups)
                    {
                        Map<String, Object> row = new CaseInsensitiveHashMap<Object>();
                        row.put("displayName", memberGroup.getValue());
                        row.put("userId", memberGroup.getKey());
                        ctx.setRow(row);
                        renderGridRow(sheet, ctx, visibleColumns);
                    }
                    super.renderGrid(sheet, visibleColumns);
                }
            };
            ew.setAutoSize(true);
            ew.setSheetName(group + " Members");
            ew.setFooter(group + " Members");
            ew.write(response);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class GroupPermissionAction extends SimpleViewAction<GroupForm>
    {
        private Group _requestedGroup;

        public ModelAndView getView(GroupForm form, BindException errors) throws Exception
        {
            List<UserController.AccessDetailRow> rows = new ArrayList<UserController.AccessDetailRow>();
            _requestedGroup = form.getGroupFor(getContainer());
            if (_requestedGroup != null)
            {
                buildAccessDetailList(Collections.singletonList(getContainer().getProject()), rows, _requestedGroup, 0);
            }
            else
                throw new NotFoundException("Group not found");

            UserController.AccessDetail bean = new UserController.AccessDetail(rows, false);
            return new JspView<UserController.AccessDetail>("/org/labkey/core/user/userAccess.jsp", bean, errors);
        }

        private void buildAccessDetailList(List<Container> children, List<UserController.AccessDetailRow> rows, Group requestedGroup, int depth)
        {
            if (children == null || children.isEmpty())
                return;
            for (Container child : children)
            {
                if (child != null)
                {
                    SecurityPolicy policy = child.getPolicy();
                    String sep = "";
                    StringBuilder access = new StringBuilder();
                    Collection<Role> roles = policy.getEffectiveRoles(requestedGroup);
                    for(Role role : roles)
                    {
                        access.append(sep);
                        access.append(role.getName());
                        sep = ", ";
                    }

                    rows.add(new UserController.AccessDetailRow(child, requestedGroup, access.toString(), null, depth));
                    buildAccessDetailList(child.getChildren(), rows, requestedGroup, depth + 1);
                }
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Permissions", new ActionURL(ProjectAction.class, getContainer()));
            root.addChild("Group Permissions");
            root.addChild(_requestedGroup == null || _requestedGroup.isUsers() ? "Access Details: Site Users" : "Access Details: " + _requestedGroup.getName());
            return root;
        }
    }

    protected enum AuditChangeType
    {
        explicit,
        fromInherited,
        toInherited,
    }
   
    @RequiresPermissionClass(AdminPermission.class) @CSRF
    public class UpdatePermissionsAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors) {}

        private void addAuditEvent(User user, String comment, int groupId)
        {
            if (user != null)
                SecurityManager.addAuditEvent(getViewContext().getContainer(), user, comment, groupId);
        }

        // UNDONE move to SecurityManager
        private void addAuditEvent(Group group, SecurityPolicy newPolicy, SecurityPolicy oldPolicy, AuditChangeType changeType)
        {
            Role oldRole = RoleManager.getRole(NoPermissionsRole.class);
            if(null != oldPolicy)
            {
                List<Role> oldRoles = oldPolicy.getAssignedRoles(group);
                if(oldRoles.size() > 0)
                    oldRole = oldRoles.get(0);
            }

            Role newRole = RoleManager.getRole(NoPermissionsRole.class);
            if(null != newPolicy)
            {
                List<Role> newRoles = newPolicy.getAssignedRoles(group);
                if(newRoles.size() > 0)
                    newRole = newRoles.get(0);
            }

            switch (changeType)
            {
                case explicit:
                    addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s to %s",
                            group.getName(), oldRole.getName(), newRole.getName()), group.getUserId());
                    break;
                case fromInherited:
                    addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s (inherited) to %s",
                            group.getName(), oldRole.getName(), newRole.getName()), group.getUserId());
                    break;
                case toInherited:
                    addAuditEvent(getUser(), String.format("The permissions for group %s were changed from %s to %s (inherited)",
                            group.getName(), oldRole.getName(), newRole.getName()), group.getUserId());
                    break;
            }

        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            ViewContext ctx = getViewContext();
            Container c = getContainer();
            AuditChangeType changeType = AuditChangeType.explicit;

            // UNDONE: remove objectId from the form
            assert c.getId().equals(ctx.get("objectId"));

            boolean inherit = "on".equals(ctx.get("inheritPermissions"));

            if (c.isProject())
            {
                boolean newSubfoldersInherit = "on".equals(ctx.get("newSubfoldersInheritPermissions"));
                if (newSubfoldersInherit != SecurityManager.shouldNewSubfoldersInheritPermissions(c))
                {
                    SecurityManager.setNewSubfoldersInheritPermissions(c, getUser(), newSubfoldersInherit);
                }
            }

            if (inherit)
            {
                addAuditEvent(getUser(), String.format("Container %s was updated to inherit security permissions", c.getName()), 0);

                //get any existing policy specifically for this container (may return null)
                SecurityPolicy oldPolicy = SecurityManager.getPolicy(c, false);

                //delete if we found one
                if(null != oldPolicy)
                    SecurityManager.deletePolicy(c);

                //now get the nearest policy for this container so we can write to the
                //audit log how the permissions have changed
                SecurityPolicy newPolicy = SecurityManager.getPolicy(c);

                changeType = AuditChangeType.toInherited;

                for (Group g : SecurityManager.getGroups(c.getProject(), true))
                {
                    addAuditEvent(g, newPolicy, oldPolicy, changeType);
                }
            }
            else
            {
                MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(c);

                //get the current nearest policy for this container
                SecurityPolicy oldPolicy = SecurityManager.getPolicy(c);

                //if resource id is not the same as the current container
                //set change type to indicate we're moving from inherited
                if(!oldPolicy.getResourceId().equals(c.getResourceId()))
                    changeType = AuditChangeType.fromInherited;

                HttpServletRequest request = getViewContext().getRequest();
                Enumeration e = request.getParameterNames();
                while (e.hasMoreElements())
                {
                    try
                    {
                        String key = (String) e.nextElement();
                        if (!key.startsWith("group."))
                            continue;
                        int groupid = (int) Long.parseLong(key.substring(6), 16);
                        Group group = SecurityManager.getGroup(groupid);
                        if(null == group)
                            continue; //invalid group id

                        String roleName = request.getParameter(key);
                        Role role = RoleManager.getRole(roleName);
                        if(null == role)
                            continue; //invalid role name

                        newPolicy.addRoleAssignment(group, role);
                        addAuditEvent(group, newPolicy, oldPolicy, changeType);
                    }
                    catch (NumberFormatException x)
                    {
                        // continue;
                    }
                }

                SecurityManager.savePolicy(newPolicy);
            }
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            return new ActionURL("Security", getViewContext().getRequest().getParameter("view"), getContainer());
        }
    }

    public static class AddUsersForm extends ReturnUrlForm
    {
        private boolean sendMail;
        private String newUsers;
        private String _cloneUser;
        private boolean _skipProfile;

        public void setNewUsers(String newUsers)
        {
            this.newUsers = newUsers;
        }

        public String getNewUsers()
        {
            return this.newUsers;
        }

        public void setSendMail(boolean sendMail)
        {
            this.sendMail = sendMail;
        }

        public boolean getSendMail()
        {
            return this.sendMail;
        }

        public void setCloneUser(String cloneUser){_cloneUser = cloneUser;}
        public String getCloneUser(){return _cloneUser;}

        public boolean isSkipProfile()
        {
            return _skipProfile;
        }

        public void setSkipProfile(boolean skipProfile)
        {
            _skipProfile = skipProfile;
        }
    }


    @RequiresSiteAdmin @CSRF
    public class AddUsersAction extends FormViewAction<AddUsersForm>
    {
        public ModelAndView getView(AddUsersForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<Object>("/org/labkey/core/security/addUsers.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Site Users", PageFlowUtil.urlProvider(UserUrls.class).getSiteUsersURL()).addChild("Add Users");
        }

        public void validateCommand(AddUsersForm form, Errors errors) {}

        public boolean handlePost(AddUsersForm form, BindException errors) throws Exception
        {
            String[] rawEmails = form.getNewUsers() == null ? null : form.getNewUsers().split("\n");
            List<String> invalidEmails = new ArrayList<String>();
            List<ValidEmail> emails = SecurityManager.normalizeEmails(rawEmails, invalidEmails);
            User userToClone = null;

            final String cloneUser = form.getCloneUser();
            if (cloneUser != null && cloneUser.length() > 0)
            {
                try {
                    final ValidEmail emailToClone = new ValidEmail(cloneUser);
                    userToClone = UserManager.getUser(emailToClone);
                    if (userToClone == null)
                        errors.addError(new FormattedError("Failed to clone user permissions " + emailToClone + ": User email does not exist in the system"));
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.addError(new FormattedError("Failed to clone user permissions " + cloneUser.trim() + ": Invalid email address"));
                }
            }

            // don't attempt to create the users if the user to clone is invalid
            if (errors.getErrorCount() > 0)
            {
                return false;
            }

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                if (!"".equals(rawEmail.trim()))
                    errors.addError(new FormattedError("Failed to create user " + PageFlowUtil.filter(rawEmail.trim()) + ": Invalid email address"));
            }

            List<Pair<String, String>> extraParams = new ArrayList<Pair<String, String>>(2);
            if (form.isSkipProfile())
                extraParams.add(new Pair<String, String>("skipProfile", "1"));

            URLHelper returnURL = null;

            if (null != form.getReturnUrl())
            {
                extraParams.add(new Pair<String, String>(ActionURL.Param.returnUrl.name(), form.getReturnUrl().getSource()));
                returnURL = form.getReturnURLHelper();
            }

            for (ValidEmail email : emails)
            {
                String result = SecurityManager.addUser(getViewContext(), email, form.getSendMail(), null, extraParams.<Pair<String, String>>toArray(new Pair[extraParams.size()]));

                if (result == null)
                {
                    User user = UserManager.getUser(email);
                    ActionURL url = PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(getContainer(), user.getUserId(), returnURL);
                    result = email + " was already a registered system user.  Click <a href=\"" + url.getEncodedLocalURIString() + "\">here</a> to see this user's profile and history.";
                }
                else if (userToClone != null)
                {
                    clonePermissions(userToClone, email);
                }
                errors.addError(new FormattedError(result));
            }

            return false;
        }

        private void clonePermissions(User clone, ValidEmail userEmail) throws ServletException
        {
            // clone this user's permissions
            final User user = UserManager.getUser(userEmail);
            if (clone != null && user != null)
            {
                for (int groupId : clone.getGroups())
                {
                    if (!user.isInGroup(groupId))
                    {
                        final Group group = SecurityManager.getGroup(groupId);

                        if (group != null)
                        {
                            try
                            {
                                SecurityManager.addMember(group, user);
                            }
                            catch (InvalidGroupMembershipException e)
                            {
                                // Best effort... fail quietly
                            }
                        }
                    }
                }
            }
        }

        public ActionURL getSuccessURL(AddUsersForm addUsersForm)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class EmailForm extends ReturnUrlForm
    {
        private String _email;
        private String _mailPrefix;

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEmail(String email)
        {
            _email = email;
        }

        public String getMailPrefix()
        {
            return _mailPrefix;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setMailPrefix(String mailPrefix)
        {
            _mailPrefix = mailPrefix;
        }
    }


    private abstract class AbstractEmailAction extends SimpleViewAction<EmailForm>
    {
        protected abstract SecurityMessage createMessage(EmailForm form) throws Exception;

        public ModelAndView getView(EmailForm form, BindException errors) throws Exception
        {
            Writer out = getViewContext().getResponse().getWriter();
            String rawEmail = form.getEmail();

            try
            {
                ValidEmail email = new ValidEmail(rawEmail);

                SecurityMessage message = createMessage(form);

                if (SecurityManager.isVerified(email))
                {
                    out.write("Can't display " + message.getType().toLowerCase() + "; " + PageFlowUtil.filter(email) + " has already chosen a password.");
                }
                else
                {
                    String verification = SecurityManager.getVerification(email);
                    ActionURL verificationURL = SecurityManager.createVerificationURL(getContainer(), email, verification, null);
                    SecurityManager.renderEmail(getContainer(), getUser(), message, email.getEmailAddress(), verificationURL, out);
                }
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                out.write("Invalid email address: " + PageFlowUtil.filter(rawEmail));
            }

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresSiteAdmin
    public class ShowRegistrationEmailAction extends AbstractEmailAction
    {
        // TODO: Allow project admins to view verification emails for users they've added?
        protected SecurityMessage createMessage(EmailForm form) throws Exception
        {
            return SecurityManager.getRegistrationMessage(form.getMailPrefix(), false);
        }
    }


    @RequiresSiteAdmin
    public class ShowResetEmailAction extends AbstractEmailAction
    {
        protected SecurityMessage createMessage(EmailForm form) throws Exception
        {
            return SecurityManager.getResetMessage(false);
        }
    }


    @RequiresSiteAdmin
    public class AdminResetPasswordAction extends SimpleViewAction<EmailForm>
    {
        public ModelAndView getView(EmailForm form, BindException errors) throws Exception
        {
            User user = getUser();
            StringBuilder sbReset = new StringBuilder();

            String rawEmail = form.getEmail();

            sbReset.append("<p>").append(PageFlowUtil.filter(rawEmail));

            try
            {
                ValidEmail email = new ValidEmail(rawEmail);

                // We let admins create passwords (i.e., entries in the logins table) if they don't already exist.
                // This addresses SSO and LDAP scenarios, see #10374.
                boolean loginExists = SecurityManager.loginExists(email);
                String pastVerb = loginExists ? "reset" : "created";
                String infinitiveVerb = loginExists ? "reset" : "create";

                try
                {
                    String verification;

                    if (loginExists)
                    {
                        // Create a placeholder password that's impossible to guess and a separate email
                        // verification key that gets emailed.
                        verification = SecurityManager.createTempPassword();
                        SecurityManager.setPassword(email, SecurityManager.createTempPassword());
                        SecurityManager.setVerification(email, verification);
                    }
                    else
                    {
                        verification = SecurityManager.createLogin(email);
                    }

                    sbReset.append(": password ").append(pastVerb).append(".</p><p>");

                    ActionURL actionURL = new ActionURL(ShowResetEmailAction.class, getContainer()).addParameter("email", email.toString());
                    String url = actionURL.getLocalURIString();
                    String href = "<a href=" + url + " target=\"_blank\">here</a>";

                    try
                    {
                        Container c = getContainer();
                        ActionURL verificationURL = SecurityManager.createVerificationURL(c, email, verification, null);
                        SecurityManager.sendEmail(c, user, SecurityManager.getResetMessage(false), email.getEmailAddress(), verificationURL);

                        if (!user.getEmail().equals(email.getEmailAddress()))
                        {
                            SecurityMessage msg = SecurityManager.getResetMessage(true);
                            msg.setTo(email.getEmailAddress());
                            SecurityManager.sendEmail(c, user, msg, user.getEmail(), verificationURL);
                        }

                        sbReset.append("Email sent. ");
                        sbReset.append("Click ").append(href).append(" to see the email.");
                        UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " " + pastVerb + " the password.");
                    }
                    catch (MessagingException e)
                    {
                        sbReset.append("Failed to send email due to: <pre>").append(e.getMessage()).append("</pre>");
                        appendMailHelpText(sbReset, url);
                        UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " " + pastVerb + " the password, but sending the email failed.");
                    }
                }
                catch (SecurityManager.UserManagementException e)
                {
                    sbReset.append(": failed to reset password due to: ").append(e.getMessage());
                    UserManager.addToUserHistory(UserManager.getUser(email), user.getEmail() + " attempted to " + infinitiveVerb + " the password, but the " + infinitiveVerb + " failed: " + e.getMessage());
                }
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                sbReset.append(" failed: invalid email address.");
            }

            sbReset.append("</p>");
            sbReset.append(PageFlowUtil.generateButton("Done", form.getReturnURLHelper()));
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new HtmlView(sbReset.toString());
        }

        private void appendMailHelpText(StringBuilder sb, String mailHref)
        {
            sb.append("<p>You can attempt to resend this mail later by going to the Site Users link, clicking on the appropriate user from the list, and resetting their password.");
            if (mailHref != null)
            {
                sb.append(" Alternatively, you can copy the <a href=\"");
                sb.append(mailHref);
                sb.append("\" target=\"_blank\">contents of the message</a> into an email client and send it to the user manually.");
            }
            sb.append("</p>");
            sb.append("<p>For help on fixing your mail server settings, please consult the SMTP section of the <a href=\"");
            sb.append((new HelpTopic("cpasxml")).getHelpTopicLink());
            sb.append("\" target=\"_new\">LabKey Server documentation on modifying your configuration file</a>.</p>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }


    public static class GroupDiagramViewFactory implements SecurityManager.ViewFactory
    {
        @Override
        public HttpView createView(ViewContext context)
        {
            JspView view = new JspView("/org/labkey/core/security/groupDiagram.jsp");
            view.setTitle("Group Diagram");

            return view;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class GroupDiagramAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            List<Group> groups = Arrays.asList(SecurityManager.getGroups(getContainer().getProject(), false));
            String html;

            if (groups.isEmpty())
            {
                html = "This project has no security groups defined";
            }
            else
            {
                String dot = GroupManager.getGroupGraphSvg(groups, getUser());
                File dir = FileUtil.getTempDirectory();
                File svgFile = null;
                try
                {
                    svgFile = File.createTempFile("groups", ".svg", dir);
                    svgFile.deleteOnExit();
                    DotRunner runner = new DotRunner(dir, dot);
                    runner.addSvgOutput(svgFile);
                    runner.execute();
                    String svg = PageFlowUtil.getFileContentsAsString(svgFile);
                    html = svg.substring(svg.indexOf("<svg"));
                }
                finally
                {
                    if (null != svgFile)
                        svgFile.delete();
                }
            }

            return new ApiSimpleResponse("html", html);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class FolderAccessAction extends SimpleViewAction<FolderAccessForm>
    {
        @Override
        public ModelAndView getView(FolderAccessForm form, BindException errors) throws Exception
        {
            VBox view = new VBox();
            view.addView(new JspView<FolderAccessForm>("/org/labkey/core/user/userAccessHeaderLinks.jsp", form));

            List<UserController.AccessDetailRow> rows = new ArrayList<UserController.AccessDetailRow>();

            // todo: what should the default set of users be? project users? folder users?
            // todo: how should the users be sorted? email, displayname?
            List<User> projectUsers = SecurityManager.getProjectUsers(getContainer(), true);

            buildAccessDetailList(projectUsers, rows, form.showInactive());
            UserController.AccessDetail bean = new UserController.AccessDetail(rows, true, true);
            view.addView(new JspView<UserController.AccessDetail>("/org/labkey/core/user/userAccess.jsp", bean, errors));
            
            view.addView(GroupAuditViewFactory.getInstance().createFolderView(getViewContext(), getContainer()));
            return view;
        }

        private void buildAccessDetailList(List<User> projectUsers, List<UserController.AccessDetailRow> rows, boolean showInactive)
        {
            if (projectUsers.size() == 0)
                return;

            // add an AccessDetailRow for each user that has perm within the project
            for (User user : projectUsers)
            {
                if (!showInactive && !user.isActive())
                    continue;

                String sep = "";
                StringBuilder access = new StringBuilder();
                SecurityPolicy policy = SecurityManager.getPolicy(getContainer());
                Set<Role> effectiveRoles = policy.getEffectiveRoles(user);
                effectiveRoles.remove(RoleManager.getRole(NoPermissionsRole.class)); //ignore no perms
                for (Role role : effectiveRoles)
                {
                    access.append(sep);
                    access.append(role.getName());
                    sep = ", ";
                }

                // only need to continue if the user has some access within the given folder
                if (access.length() == 0)
                    continue;

                List<Group> relevantGroups = new ArrayList<Group>();
                if (effectiveRoles.size() > 0)
                {
                    Container project = getContainer().getProject();
                    Group[] groups = SecurityManager.getGroups(project, true);
                    for (Group group : groups)
                    {
                        if (user.isInGroup(group.getUserId()))
                        {
                            Collection<Role> groupRoles = policy.getAssignedRoles(group);
                            for (Role role : effectiveRoles)
                            {
                                if (groupRoles.contains(role))
                                    relevantGroups.add(group);
                            }
                        }
                    }
                }
                rows.add(new UserController.AccessDetailRow(getContainer(), user, access.toString(), relevantGroups, 0));
            }
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Permissions", new ActionURL(ProjectAction.class, getContainer()));
            root.addChild("Folder Permissions");
            return root.addChild("Access Details: " + getContainer().getPath());
        }
    }

    public static class FolderAccessForm
    {
        private boolean _showInactive;

        public boolean showInactive()
        {
            return _showInactive;
        }

        public void setShowInactive(boolean showInactive)
        {
            _showInactive = showInactive;
        }
    }

    public static class TestCase extends Assert
    {
        private Container c;

        @Test
        public void testAnnotations() throws Exception
        {
            //clean up users in case this failed part way through
            String[] cleanupUsers = {"guest@scjutc.com", "user@scjutc.com", "admin@scjutc.com"};
            for (String cleanupUser : cleanupUsers)
            {
                User oldUser = UserManager.getUser(new ValidEmail(cleanupUser));
                if (null != oldUser)
                    UserManager.deleteUser(oldUser.getUserId());
            }

            Container junit = ContainerManager.ensureContainer("/Shared/_junit");
            c = ContainerManager.createContainer(junit, "SecurityController-" + GUID.makeGUID());

            User site = TestContext.get().getUser();
            assertTrue(site.isAdministrator());

            User guest = SecurityManager.addUser(new ValidEmail("guest@scjutc.com")).getUser();
            User user = SecurityManager.addUser(new ValidEmail("user@scjutc.com")).getUser();
            User admin = SecurityManager.addUser(new ValidEmail("admin@scjutc.com")).getUser();

            MutableSecurityPolicy policy = new MutableSecurityPolicy(c, c.getPolicy());
            policy.addRoleAssignment(admin, RoleManager.getRole(SiteAdminRole.class));
            policy.addRoleAssignment(guest, RoleManager.getRole(ReaderRole.class));
            policy.addRoleAssignment(user, RoleManager.getRole(EditorRole.class));
            SecurityManager.savePolicy(policy);

            // @RequiresNoPermission
            assertPermission(guest, BeginAction.class);
            assertPermission(user, BeginAction.class);
            assertPermission(admin, BeginAction.class);
            assertPermission(site, BeginAction.class);

            // @RequiresPermissionClass(AdminPermission.class)
            assertNoPermission(guest, GroupsAction.class);
            assertNoPermission(user, GroupsAction.class);
            assertPermission(admin, GroupsAction.class);
            assertPermission(site, GroupsAction.class);

            // @RequiresSiteAdmin
            assertNoPermission(guest, AddUsersAction.class);
            assertNoPermission(user, AddUsersAction.class);
            assertNoPermission(admin, AddUsersAction.class);
            assertPermission(site, AddUsersAction.class);

            assertTrue(ContainerManager.delete(c, TestContext.get().getUser()));
            UserManager.deleteUser(admin.getUserId());
            UserManager.deleteUser(user.getUserId());
            UserManager.deleteUser(guest.getUserId());
        }


        private void assertPermission(User u, Class<? extends Controller> actionClass) throws Exception
        {
            try
            {
                BaseViewAction.checkActionPermissions(actionClass, makeContext(u), null);
            }
            catch (UnauthorizedException x)
            {
                fail("Should have permission");
            }
        }

        private void assertNoPermission(User u, Class<? extends Controller> actionClass) throws Exception
        {
            try
            {
                BaseViewAction.checkActionPermissions(actionClass, makeContext(u), null);
                fail("Should not have permission");
            }
            catch (UnauthorizedException x)
            {
                // expected
            }
        }

        private ViewContext makeContext(User u)
        {
            HttpServletRequest w = new HttpServletRequestWrapper(TestContext.get().getRequest()){
                @Override
                public String getParameter(String name)
                {
                    if (CSRFUtil.csrfName.equals(name))
                        return CSRFUtil.getExpectedToken(TestContext.get().getRequest());
                    return super.getParameter(name);
                }
            };
            ViewContext context = new ViewContext();
            context.setContainer(c);
            context.setUser(u);
            context.setRequest(w);
            return context;
        }


        private static class TestUser extends User
        {
            TestUser(String name, int id, Integer... groups)
            {
                super(name,id);
                _groups = new int[groups.length+1];
                for (int i=0 ; i<groups.length; i++)
                    _groups[i] = groups[i];
                _groups[groups.length] = id;
                Arrays.sort(_groups);
            }
        }
    }
}
