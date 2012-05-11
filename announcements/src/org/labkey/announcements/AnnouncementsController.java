/*
 * Copyright (c) 2004-2012 Fred Hutchinson Cancer Research Center
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

package org.labkey.announcements;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.announcements.config.AnnouncementEmailConfig;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.announcements.model.DailyDigestEmailPrefsSelector;
import org.labkey.announcements.model.DiscussionServiceImpl;
import org.labkey.announcements.model.IndividualEmailPrefsSelector;
import org.labkey.announcements.model.NormalMessageBoardPermissions;
import org.labkey.announcements.model.Permissions;
import org.labkey.announcements.model.SecureMessageBoardPermissions;
import org.labkey.api.action.AjaxCompletionAction;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.DiscussionService.Settings;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.Group;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GuidString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.GridView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Shows a set of announcementModels or bulletin board items with replies.
 * Sends email to subscribers.
 * Properties are stored under the following keys:
 *   user=user,container,Object="Announcements"
 *              key="email":0 (no email), 1 (email all entries), 2 (email responses to messages I've created or replied to)
 */
public class AnnouncementsController extends SpringActionController
{
    private static CommSchema _comm = CommSchema.getInstance();

    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(AnnouncementsController.class,
            SendMessageAction.class);

    public AnnouncementsController() throws Exception
    {
        setActionResolver(_actionResolver);
    }


    private DiscussionService.Settings getSettings()
    {
        return getSettings(getContainer());
    }


    public static DiscussionService.Settings getSettings(Container c)
    {
        try
        {
            return AnnouncementManager.getMessageBoardSettings(c);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);  // Not great... but this method is called from all over (webpart constructors, etc.)
        }
    }


    private Permissions getPermissions()
    {
        return getPermissions(getContainer(), getUser(), getSettings(getContainer()));
    }


    public static Permissions getPermissions(Container c, User user, DiscussionService.Settings settings)
    {
        if (settings.isSecure())
            return new SecureMessageBoardPermissions(c, user, settings);
        else
            return new NormalMessageBoardPermissions(c, user, settings);
    }


    protected ActionURL getActionURL()
    {
        return getViewContext().getActionURL();
    }


    protected HttpServletResponse getResponse()
    {
        return getViewContext().getResponse();
    }


    public static ActionURL getBeginURL(Container c)
    {
        return new ActionURL(BeginAction.class, c);
    }


    // Anyone with read permission can attempt to view the list.  AnnouncementWebPart will do further permission checking.  For example,
    //   in a secure message board, those without Editor permissions will only see messages when they are on the member list
    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Settings settings = getSettings();
            boolean displayAll = getActionURL().getController().equalsIgnoreCase("announcements");
            AnnouncementWebPart v = new AnnouncementWebPart(getContainer(), getActionURL(), getUser(), settings, displayAll, false);
            v.getModelBean().isPrint = isPrint();
            if (isPrint())
                v.setFrame(WebPartView.FrameType.NONE);
            else
                v.setFrame(WebPartView.FrameType.PORTAL);
            v.setShowTitle(false);
            getPageConfig().setRssProperties(new RssAction().getURL(), settings.getBoardName());

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(getSettings().getBoardName(), getBeginURL(getContainer()));
        }
    }


    private static ActionURL getListURL(Container c)
    {
        return new ActionURL(ListAction.class, c).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ListAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            AnnouncementListView view = new AnnouncementListView(getViewContext());
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setShowTitle(false);
            getPageConfig().setRssProperties(new RssAction().getURL(), getSettings().getBoardName());

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(getSettings().getBoardName() + " List", getListURL(getContainer()));
        }
    }

    public static ActionURL getAdminEmailURL(Container c, URLHelper returnURL)
    {
        ActionURL url = PageFlowUtil.urlProvider(AdminUrls.class).getFolderManagementURL(c);
        url.addParameter("tabId", "messages");
        url.addReturnURL(returnURL);
        return url;
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteThreadsAction extends RedirectAction
    {
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            if (!getPermissions().allowDeleteAnyThread())
            {
                throw new UnauthorizedException();
            }

            Container c = getContainer();

            Set<String> deleteRows = DataRegionSelection.getSelected(getViewContext(), true);
            if (deleteRows != null)
            {
                for (String deleteRow : deleteRows)
                {
                    int rowId = Integer.parseInt(deleteRow);
                    AnnouncementManager.deleteAnnouncement(c, rowId);
                }
            }

            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            return getListURL(getContainer());
        }

        public void validateCommand(Object target, Errors errors)
        {
        }
    }


    public abstract class DeleteMessageAction extends ConfirmAction<AnnouncementDeleteForm>
    {
        protected URLHelper _returnUrl;
        protected URLHelper _cancelUrl;

        public ModelAndView getConfirmView(AnnouncementDeleteForm form, BindException errors) throws Exception
        {
            Permissions perm = getPermissions();

            AnnouncementModel ann = null;
            if (null != form.getEntityId())
                ann = AnnouncementManager.getAnnouncement(getContainer(), form.getEntityId(), true);
            if (null == ann)
                ann = AnnouncementManager.getAnnouncement(getContainer(), form.getRowId(), AnnouncementManager.INCLUDE_RESPONSES);

            if (null == ann)
            {
                throw new NotFoundException();
            }
            if (!perm.allowDeleteMessage(ann))
            {
                throw new UnauthorizedException();
            }

            _returnUrl = form.getReturnURLHelper();

            if (null != form.getCancelUrl())
                _cancelUrl = new URLHelper(form.getCancelUrl());
            else
                _cancelUrl = _returnUrl;

            return new ConfirmDeleteView(ann, getWhat(), getSettings(getContainer()));
        }

        public URLHelper getSuccessURL(AnnouncementDeleteForm form)
        {
            return form.getReturnURLHelper();
        }

        public boolean handlePost(AnnouncementDeleteForm form, BindException errors) throws Exception
        {
            Permissions perm = getPermissions();
            Container c = getContainer();

            AnnouncementModel ann = null;
            if (null != form.getEntityId())
                ann = AnnouncementManager.getAnnouncement(c, form.getEntityId(), true);
            if (null == ann)
                ann = AnnouncementManager.getAnnouncement(c, form.getRowId(), AnnouncementManager.INCLUDE_RESPONSES);

            if (ann == null)
                throw new NotFoundException("Could not find response");
            if (!perm.allowDeleteMessage(ann))
            {
                throw new UnauthorizedException();
            }

            AnnouncementManager.deleteAnnouncement(c, ann.getRowId());

            return true;
        }

        public void validateCommand(AnnouncementDeleteForm announcementDeleteForm, Errors errors)
        {
        }

        @Override
        public String getConfirmText()
        {
            return "Delete";
        }

        abstract String getWhat();
    }


    public static class ConfirmDeleteView extends JspView<ConfirmDeleteView.DeleteBean>
    {
        public ConfirmDeleteView(AnnouncementModel ann, String what, Settings settings)
        {
            super("/org/labkey/announcements/confirmDelete.jsp", new DeleteBean(ann, what, settings));
        }

        public static class DeleteBean
        {
            public String title;
            public String what;
            public String conversationName;

            private DeleteBean(AnnouncementModel ann, String what, Settings settings)
            {
                title = ann.getTitle();
                this.what = what;
                conversationName = settings.getConversationName().toLowerCase();
            }
        }
    }


    @RequiresNoPermission  // Custom permission checking in base class to handle owner-delete
    public class DeleteThreadAction extends DeleteMessageAction
    {
        String getWhat()
        {
            return "entire";
        }
    }


    public static ActionURL getDeleteResponseURL(Container c, String entityId, URLHelper returnUrl)
    {
        ActionURL url = new ActionURL(DeleteResponseAction.class, c);
        url.addParameter("entityId", entityId);
        url.addReturnURL(returnUrl);

        return url;
    }


    @RequiresNoPermission  // Custom permission checking in base class to handle owner-delete
    public class DeleteResponseAction extends DeleteMessageAction
    {
        String getWhat()
        {
            return "response from the";
        }

        public URLHelper getCancelUrl()
        {
            return _returnUrl;
        }
    }


    @RequiresLogin @ActionNames("removeFromMemberList, confirmRemove")
    public class RemoveFromMemberListAction extends ConfirmAction<MemberListRemovalForm>
    {
        public ModelAndView getConfirmView(MemberListRemovalForm form, BindException errors) throws Exception
        {
            AnnouncementModel thread = validateAndGetThread(form, errors);

            if (errors.hasErrors())
                return new SimpleErrorView(errors);
            else
                return new RemoveUserView(thread, getUser().getEmail(), getSettings());
        }

        @Override
        public String getConfirmText()
        {
            return "Remove";
        }

        public ActionURL getSuccessURL(MemberListRemovalForm memberListRemovalForm)
        {
            return getBeginURL(getContainer());
        }

        @Override
        public ActionURL getCancelUrl()
        {
            return getBeginURL(getContainer());
        }

        public boolean handlePost(MemberListRemovalForm form, BindException errors) throws Exception
        {
            if (form.getUserId() != getUser().getUserId())
            {
                throw new UnauthorizedException();
            }

            // TODO: Make this insert a new message to get history?
            AnnouncementManager.deleteUserFromMemberList(getUser(), form.getMessageId());

            return true;
        }

        public void validateCommand(MemberListRemovalForm form, Errors errors)
        {
            validateAndGetThread(form, errors);
        }

        private AnnouncementModel validateAndGetThread(MemberListRemovalForm form, Errors errors)
        {
            User user = getUser();
            Settings settings = getSettings();

            AnnouncementModel thread = AnnouncementManager.getAnnouncement(getContainer(), form.getMessageId(), AnnouncementManager.INCLUDE_MEMBERLIST);

            if (form.getUserId() != user.getUserId())
            {
                User removeUser = UserManager.getUser(form.getUserId());

                if (null == removeUser)
                    errors.reject(ERROR_MSG, "User could not be found.");
                else
                    errors.reject(ERROR_MSG, "You need to be logged in as " + removeUser.getEmail() + ".");
            }
            else if (null == thread)
            {
                errors.reject(ERROR_MSG, settings.getConversationName() + " not found.");
            }
            else if (!thread.getMemberList().contains(getUser()))
            {
                errors.reject(ERROR_MSG, "You are not on the member list for this " + settings.getConversationName().toLowerCase() + ".");
            }

            return thread;
        }
    }


    public static class RemoveUserView extends JspView<RemoveUserView.RemoveUserBean>
    {
        public RemoveUserView(AnnouncementModel ann, String email, DiscussionService.Settings settings)
        {
            super("/org/labkey/announcements/confirmRemoveUser.jsp", new RemoveUserBean(ann, email, settings));
        }

        public static class RemoveUserBean
        {
            public String title;
            public String email;
            public String conversationName;

            private RemoveUserBean(AnnouncementModel ann, String email, Settings settings)
            {
                title = ann.getTitle();
                this.email = email;
                conversationName = settings.getConversationName().toLowerCase();
            }
        }
    }


    private AnnouncementModel getAnnouncement(AttachmentForm form)
    {
        AnnouncementModel ann = AnnouncementManager.getAnnouncement(getContainer(), form.getEntityId(), true);  // Force member list to be selected

        if (null == ann)
            throw createThreadNotFoundException(getContainer());

        return ann;
    }


    public abstract class AttachmentAction extends FormViewAction<AttachmentForm>
    {
        AttachmentAction()
        {
            super(AttachmentForm.class);
        }

        public ModelAndView getView(AttachmentForm form, boolean reshow, BindException errors) throws Exception
        {
            AnnouncementModel ann = getAnnouncement(form);
            verifyPermissions(ann);
            getPageConfig().setTemplate(PageConfig.Template.None);
            return getAttachmentView(form, ann);
        }

        public boolean handlePost(AttachmentForm attachmentForm, BindException errors) throws Exception
        {
            return true;
        }

        public ActionURL getSuccessURL(AttachmentForm attachmentForm)
        {
            return null;
        }

        public abstract ModelAndView getAttachmentView(AttachmentForm form, AttachmentParent parent) throws Exception;

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        public void validateCommand(AttachmentForm target, Errors errors)
        {
        }

        // Further permissions check (ensure non-editors are on the member list in secure board, handle owner-update, etc.)
        // Most actions require update permission
        protected void verifyPermissions(AnnouncementModel ann) throws ServletException
        {
            if (!getPermissions().allowUpdate(ann))
            {
                throw new UnauthorizedException();
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(final AttachmentForm form, final AttachmentParent parent) throws Exception
        {
            return new HttpView()
            {
                protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                {
                    AttachmentService.get().download(response, parent, form.getName());
                }
            };
        }

        // Override since this action only requires read permission
        @Override
        protected void verifyPermissions(AnnouncementModel ann) throws ServletException
        {
            if (!getPermissions().allowRead(ann))
            {
                throw new UnauthorizedException();
            }
        }
    }

    @RequiresNoPermission    // Permission checking done in verifyPermissions() to handle owner-update, etc.
    public class DeleteAttachmentAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(AttachmentForm form, AttachmentParent parent) throws Exception
        {
            return AttachmentService.get().delete(parent, form.getName(), getUser());
        }
    }


    public static class MemberListRemovalForm
    {
        private int _userId;
        private int _messageId;

        public int getMessageId()
        {
            return _messageId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setMessageId(int messageId)
        {
            _messageId = messageId;
        }

        public int getUserId()
        {
            return _userId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setUserId(int userId)
        {
            _userId = userId;
        }
    }


    public static ActionURL getCustomizeURL(Container c, URLHelper returnUrl)
    {
        ActionURL url = new ActionURL(CustomizeAction.class, c);
        url.addReturnURL(returnUrl);
        return url;
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class CustomizeAction extends FormViewAction<DiscussionService.Settings>
    {
        public URLHelper getSuccessURL(DiscussionService.Settings form)
        {
            return form.getReturnURLHelper();
        }

        public ModelAndView getView(DiscussionService.Settings form, boolean reshow, BindException errors) throws Exception
        {
            CustomizeBean bean = new CustomizeBean();

            bean.settings = getSettings();   // TODO: Just use form?
            bean.returnURL = form.getReturnURLHelper();
            bean.assignedToSelect = getAssignedToSelect(getContainer(), bean.settings.getDefaultAssignedTo(), "defaultAssignedTo", getUser());

            if (hasEditorPerm(Group.groupGuests))
                bean.securityWarning = "Warning: guests have been granted editor permissions in this folder.  As a result, any anonymous user will be able to view, create, and respond to posts, regardless of the security setting below.  You may want to change permissions in this folder.";
            else if (hasEditorPerm(Group.groupUsers))
                bean.securityWarning = "Warning: all site users have been granted editor permissions in this folder.  As a result, any logged in user will be able to view, create, and respond to posts, regardless of the security setting below.  You may want to change permissions in this folder.";

            setHelpTopic("adminMessages");
            return new JspView<CustomizeBean>("/org/labkey/announcements/customize.jsp", bean);
        }

        public boolean handlePost(DiscussionService.Settings form, BindException errors) throws Exception
        {
            AnnouncementManager.saveMessageBoardSettings(getContainer(), form);

            return true;
        }

        public void validateCommand(DiscussionService.Settings settings, Errors errors)
        {
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root)
                             .addChild("Customize " + getSettings().getBoardName());

            return root;
        }
    }


    public static class CustomizeBean
    {
        public DiscussionService.Settings settings;
        public URLHelper returnURL;    // TODO: Settings has a returnUrl
        public String securityWarning;
        public String assignedToSelect;
    }


    private boolean hasEditorPerm(int groupId) throws ServletException
    {
        Role editorRole = RoleManager.getRole(EditorRole.class);
        Group group = SecurityManager.getGroup(groupId);
        return null != group && getContainer().getPolicy().hasPermissions(group, editorRole.getPermissions());
    }


    @RequiresPermissionClass(InsertPermission.class)
    public abstract class BaseInsertAction extends FormViewAction<AnnouncementForm>
    {
        private URLHelper _returnURL;
        protected HttpView _attachmentErrorView;

        protected abstract ModelAndView getInsertUpdateView(AnnouncementForm announcementForm, boolean reshow, BindException errors) throws Exception;

        public ModelAndView getView(AnnouncementForm form, boolean reshow, BindException errors) throws Exception
        {
            if (null != _attachmentErrorView)
            {
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return _attachmentErrorView;
            }

            return getInsertUpdateView(form, reshow, errors);
        }

        public void validateCommand(AnnouncementForm form, Errors errors)
        {
            form.validate(errors);
        }

        public boolean handlePost(AnnouncementForm form, BindException errors) throws Exception
        {
            if (!getPermissions().allowInsert())
            {
                throw new UnauthorizedException();
            }

            User u = getUser();
            Container c = getContainer();

            List<AttachmentFile> files = getAttachmentFileList();

            AnnouncementModel insert = form.getBean();
            if (null == insert.getParent() || 0 == insert.getParent().length())
                insert.setParent(form.getParentId());

            if (getSettings().hasMemberList() && null == form.getMemberList())
                insert.setMemberList(Collections.<User>emptyList());  // Force member list to get deleted, bug #2484
            else
                insert.setMemberList(form.getMemberList());  // TODO: Do this in validate()?

            try
            {
                AnnouncementManager.insertAnnouncement(c, u, insert, files);
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, "Your changes have been saved, though some file attachments were not:");
                errors.reject(ERROR_MSG, e.getMessage() == null ? e.toString() : e.getMessage());
            }

            URLHelper returnURL = form.getReturnURLHelper();

            // Null in insert/update message case, since we want to redirect to thread view anchoring to new post
            if (null == returnURL)
            {
                AnnouncementModel thread = insert;
                if (null != insert.getParent())
                    thread = AnnouncementManager.getAnnouncement(getContainer(), insert.getParent(), true);

                if (form.isFromDiscussion() && null != thread.getDiscussionSrcIdentifier())
                {
                    returnURL = DiscussionServiceImpl.fromSaved(thread.getDiscussionSrcURL());
                    returnURL.addParameter("discussion.id", "" + thread.getRowId());
                    returnURL.addParameter("_anchor", "discussionArea");               // TODO: insert.getRowId() instead? -- target just inserted response
                }
                else
                {
                    String threadId = thread.getEntityId();
                    returnURL = getThreadURL(c, threadId, insert.getRowId());
                }
            }

            _attachmentErrorView = AttachmentService.get().getErrorView(files, errors, returnURL);
            _returnURL = returnURL;

            boolean success = (null == _attachmentErrorView);

            // Can't use getSuccessURL since this is a URLHelper, not an ActionURL
            if (success)
                throw new RedirectException(_returnURL);

            return false;
        }

        public ActionURL getSuccessURL(AnnouncementForm announcementForm)
        {
            throw new IllegalStateException("Shouldn't get here; post handler should have redirected.");
        }
    }


    public static ActionURL getInsertURL(Container c)
    {
        return new ActionURL(InsertAction.class, c);
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class InsertAction extends BaseInsertAction
    {
        public void validateCommand(AnnouncementForm form, Errors errors)
        {
            super.validateCommand(form, errors);

            if (form.isFromDiscussion() && !form.allowMultipleDiscussions())
            {
                if (DiscussionService.get().hasDiscussions(getContainer(), form.getBean().getDiscussionSrcIdentifier()))
                    errors.reject(ERROR_MSG, "Can't post a new discussion -- a discussion already exists and multiple discussions are not allowed");
            }
        }

        @Override
        public ModelAndView getInsertUpdateView(AnnouncementForm form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainer();
            DiscussionService.Settings settings = getSettings(c);
            Permissions perm = getPermissions(c, getUser(), settings);

            if (!perm.allowInsert())
            {
                throw new UnauthorizedException();
            }

            InsertMessageView insertView = new InsertMessageView(form, "New " + settings.getConversationName(), errors, reshow, form.getReturnURLHelper(), false, true);
            insertView.setShowTitle(false);

            getPageConfig().setFocusId("title");

            return insertView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root).addChild("New " + getSettings().getConversationName());
            return root;
        }
    }


    public static ActionURL getRespondURL(Container c)
    {
        return new ActionURL(RespondAction.class, c);
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class RespondAction extends BaseInsertAction
    {
        private AnnouncementModel _parent;

        public ModelAndView getInsertUpdateView(AnnouncementForm form, boolean reshow, BindException errors) throws Exception
        {
            Permissions perm = getPermissions();
            AnnouncementModel parent = null;
            Container c = getContainer();

            if (null != form.getParentId())
                parent = AnnouncementManager.getAnnouncement(c, form.getParentId(), true);

            if (null == parent)
            {
                throw createThreadNotFoundException(c);
            }

            if (!perm.allowResponse(parent))
            {
                throw new UnauthorizedException();
            }

            ThreadView threadView = new ThreadView(c, getActionURL(), parent, perm);
            threadView.setFrame(WebPartView.FrameType.DIV);

            HttpView respondView = new RespondView(c, parent, form, form.getReturnURLHelper(), errors, reshow, false);

            getPageConfig().setFocusId("body");
            _parent = parent;

            return new VBox(threadView, respondView);
        }


        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root)
                             .addChild(_parent.getTitle(), "thread.view?rowId=" + _parent.getRowId())
                             .addChild("Respond to " + getSettings().getConversationName());
            return root;
        }
    }


    private static String getStatusSelect(DiscussionService.Settings settings, String currentValue)
    {
        List<String> options = Arrays.asList(settings.getStatusOptions().split(";"));

        StringBuilder sb = new StringBuilder(options.size() * 30);
        sb.append("    <select name=\"status\">\n");

        for (String word : options)
        {
            sb.append("      <option");

            if (word.equals(currentValue))
                sb.append(" selected");

            sb.append(">");
            sb.append(PageFlowUtil.filter(word));
            sb.append("</option>\n");
        }
        sb.append("    </select>");

        return sb.toString();
    }


    // AssignedTo == null => assigned to no one.
    private static String getAssignedToSelect(Container c, Integer assignedTo, String name, final User currentUser)
    {
        Set<Class<? extends Permission>> perms = Collections.<Class<? extends Permission>>singleton(InsertPermission.class);
        List<User> possibleAssignedTo = SecurityManager.getUsersWithPermissions(c, perms);

        Collections.sort(possibleAssignedTo, new Comparator<User>()
        {
            public int compare(User u1, User u2)
            {
                return u1.getDisplayName(currentUser).compareToIgnoreCase(u2.getDisplayName(currentUser));
            }
        });

        // TODO: Should merge all this with IssuesManager.getAssignedToList()
        StringBuilder select = new StringBuilder("    <select name=\"" + name + "\">\n");
        select.append("      <option value=\"\"");
        select.append(null == assignedTo ? " selected" : "");
        select.append("></option>\n");

        for (User user : possibleAssignedTo)
        {
            select.append("      <option value=").append(user.getUserId());

            if (assignedTo != null && assignedTo == user.getUserId())
                select.append(" selected");

            select.append(">");
            select.append(user.getDisplayName(currentUser));
            select.append("</option>\n");
        }

        select.append("    </select>");

        return select.toString();
    }


    private static ActionURL getCompleteUserURL(Container c)
    {
        return new ActionURL(CompleteUserAction.class, c);
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class CompleteUserAction extends AjaxCompletionAction<AjaxCompletionForm>
    {
        public List<AjaxCompletion> getCompletions(AjaxCompletionForm form, BindException errors) throws Exception
        {
            // Limit member list lookup to those with read permissions in this container.
            Set<Class<? extends Permission>> perms = Collections.<Class<? extends Permission>>singleton(ReadPermission.class);
            List<User> completionUsers = SecurityManager.getUsersWithPermissions(getContainer(), perms);
            return UserManager.getAjaxCompletions(form.getPrefix(), completionUsers, getViewContext().getUser());
        }
    }


    // TODO: Move to /view and use/extend in other controllers
    public static class AjaxCompletionForm
    {
        String _prefix;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }
    }


    private static String getMemberListTextArea(User user, Container c, AnnouncementModel ann, String emailList)
    {
        String completeUserUrl = getCompleteUserURL(c).getLocalURIString();

        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"text/javascript\">LABKEY.requiresScript('completion.js');</script>");
        sb.append("<textarea name=\"emailList\" id=\"emailList\" cols=\"30\" rows=\"5\"" );
        sb.append(" onKeyDown=\"return ctrlKeyCheck(event);\"");
        sb.append(" onBlur=\"hideCompletionDiv();\"");
        sb.append(" autocomplete=\"off\"");
        sb.append(" onKeyUp=\"return handleChange(this, event, '");
        sb.append(completeUserUrl);
        sb.append("prefix=');\"");
        sb.append(">");

        if (emailList != null)
        {
            sb.append(emailList);
        }
        else if (null != ann)
        {
            List<User> users = ann.getMemberList();
            sb.append(StringUtils.join(users.iterator(), "\n"));
        }
        else if (!user.isGuest())
        {
            sb.append(user.getEmail());
        }

        sb.append("</textarea>");

        return sb.toString();
    }


    private static ActionURL getInsertURL(Container c, ActionURL returnURL)
    {
        return new ActionURL(InsertAction.class, c).addReturnURL(returnURL);
    }


    public abstract static class BaseInsertView extends JspView<BaseInsertView.InsertBean>
    {
        public BaseInsertView(String page, InsertBean bean, AnnouncementForm form, URLHelper cancelURL, String title, BindException errors, @Nullable AnnouncementModel latestPost, boolean reshow, boolean fromDiscussion)
        {
            super(page, bean, errors);
            setTitle(title);
            Container c = getViewContext().getContainer();

            // In reshow case we leave all form values as is so user can correct the errors.
            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            WikiRendererType currentRendererType;
            Integer assignedTo;

            DiscussionService.Settings settings = getSettings(c);

            if (reshow)
            {
                String rendererTypeName = (String) form.get("rendererType");

                if (null == rendererTypeName && null != wikiService)
                    currentRendererType = wikiService.getDefaultMessageRendererType();
                else
                    currentRendererType = WikiRendererType.valueOf(rendererTypeName);

                AnnouncementModel ann = form.getBean();
                assignedTo = ann.getAssignedTo();
            }
            else if (null == latestPost)
            {
                // New thread... set base defaults
                Calendar cal = new GregorianCalendar();
                cal.setTime(new Date());
                cal.add(Calendar.MONTH, 1);

                String expires = DateUtil.formatDate(cal.getTime());
                form.set("expires", expires);
                currentRendererType = null != wikiService ? wikiService.getDefaultMessageRendererType() : null;
                assignedTo = settings.getDefaultAssignedTo();
            }
            else
            {
                // Response... set values to match most recent properties on this thread
                assert null == form.get("title");
                assert null == form.get("expires");

                form.set("title", latestPost.getTitle());
                form.set("status", latestPost.getStatus());
                form.setTypedValue("expires", DateUtil.formatDate(latestPost.getExpires()));

                assignedTo = latestPost.getAssignedTo();
                currentRendererType = WikiRendererType.valueOf(latestPost.getRendererType());
            }

            bean.assignedToSelect = getAssignedToSelect(c, assignedTo, "assignedTo", getViewContext().getUser());
            bean.settings = settings;
            bean.statusSelect = getStatusSelect(settings, (String)form.get("status"));
            bean.memberList = getMemberListTextArea(form.getUser(), c, latestPost, (String)(reshow ? form.get("emailList") : null));
            bean.currentRendererType = currentRendererType;
            bean.renderers = WikiRendererType.values();
            bean.form = form;
            bean.cancelURL = cancelURL;
            bean.fromDiscussion = fromDiscussion;

            // If default email option is "all messages" (or "all messages daily digest") then gently warn
            // that a bunch of users are about to be emailed.
            int defaultEmailOption = AnnouncementManager.getDefaultEmailOption(c);

            if (AnnouncementManager.EMAIL_PREFERENCE_ALL == (defaultEmailOption & AnnouncementManager.EMAIL_PREFERENCE_MASK))
            {
                bean.emailUsers = new IndividualEmailPrefsSelector(c).getNotificationUsers(latestPost).size() + new DailyDigestEmailPrefsSelector(c).getNotificationUsers(latestPost).size();
            }
        }

        public static class InsertBean
        {
            public DiscussionService.Settings settings;
            public String assignedToSelect;
            public String statusSelect;
            public String memberList;
            public WikiRendererType[] renderers;
            public WikiRendererType currentRendererType;
            public AnnouncementForm form;
            public URLHelper cancelURL;
            public AnnouncementModel parentAnnouncementModel;   // Used by RespondView only... move to subclass?
            public boolean fromDiscussion;
            public boolean allowMultipleDiscussions = true;
            public Integer emailUsers = null;
        }
    }


    public static class InsertMessageView extends BaseInsertView
    {
        public InsertMessageView(AnnouncementForm form, String title, BindException errors, boolean reshow, URLHelper cancelURL, boolean fromDiscussion, boolean allowMultipleDiscussions)
        {
            super("/org/labkey/announcements/insert.jsp", new InsertBean(), form, cancelURL, title, errors, null, reshow, fromDiscussion);

            InsertBean bean = getModelBean();
            bean.allowMultipleDiscussions = allowMultipleDiscussions;
        }
    }


    public static class RespondView extends BaseInsertView
    {
        public RespondView(Container c, AnnouncementModel parent, AnnouncementForm form, URLHelper cancelURL, BindException errors, boolean reshow, boolean fromDiscussion)
        {
            super("/org/labkey/announcements/respond.jsp", new InsertBean(), form, cancelURL, "Response", errors, AnnouncementManager.getLatestPost(c, parent), reshow, fromDiscussion);

            getModelBean().parentAnnouncementModel = parent;
        }

        public RespondView(Container c, AnnouncementModel parent, URLHelper cancelURL, boolean fromDiscussion)
        {
            this(c, parent, new AnnouncementForm(), cancelURL, null, false, fromDiscussion);
        }
    }


    public static ActionURL getUpdateURL(Container c, String threadId, URLHelper returnUrl)
    {
        ActionURL url = new ActionURL(UpdateAction.class, c);
        url.addParameter("entityId", threadId);
        url.addReturnURL(returnUrl);
        return url;
    }


    @RequiresNoPermission   // Custom permission checking below to handle owner-update
    public class UpdateAction extends FormViewAction<AnnouncementForm>
    {
        private AnnouncementModel _ann;

        public ActionURL getSuccessURL(AnnouncementForm form)
        {
            throw new IllegalStateException("Shouldn't get here; post handler should have redirected.");
        }

        public ModelAndView getView(AnnouncementForm form, boolean reshow, BindException errors) throws Exception
        {
            AnnouncementModel ann = form.selectAnnouncement();
            if (null == ann)
            {
                throw new NotFoundException();
            }

            if (!getPermissions().allowUpdate(ann))
            {
                throw new UnauthorizedException();
            }

            _ann = ann;

            return new AnnouncementUpdateView(form, ann, errors);
        }

        public boolean handlePost(AnnouncementForm form, BindException errors) throws Exception
        {
            AnnouncementModel ann = form.selectAnnouncement();

            if (null == ann)
            {
                throw new NotFoundException("Announcement");
            }

            if (!getPermissions().allowUpdate(ann))
            {
                throw new UnauthorizedException();
            }

            Container c = getContainer();

            List<AttachmentFile> files = getAttachmentFileList();

            AnnouncementModel update = form.getBean();

            // TODO: What is this checking for?
            if (!c.getId().equals(update.getContainerId()))
            {
                throw new UnauthorizedException();
            }

            try
            {
                AnnouncementManager.updateAnnouncement(getUser(), update, files);
            }
            catch (AttachmentService.DuplicateFilenameException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            // Needs to support non-ActionURL (e.g., an HTML page using the client API with embedded discussion webpart)
            // so we can't use getSuccessURL()
            ReturnURLString url = form.getReturnUrl();
            if (null != url)
                throw new RedirectException(form.getReturnUrl());
            else
                throw new RedirectException(new ActionURL(ThreadAction.class, getContainer()).addParameter("rowId",ann.getRowId()));
        }

        public void validateCommand(AnnouncementForm form, Errors errors)
        {
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root)
                             .addChild(_ann.getTitle(), "thread.view?rowId=" + _ann.getRowId())
                             .addChild("Respond to " + getSettings().getConversationName());
            return root;
        }
    }


    public static ActionURL getThreadURL(Container c, String threadId, int rowId)
    {
        ActionURL url = new ActionURL(ThreadAction.class, c);
        url.addParameter("entityId", threadId);
        url.addParameter("_anchor", rowId);
        return url;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ThreadAction extends SimpleViewAction<AnnouncementForm>
    {
        private String _title;

        public ThreadView getView(AnnouncementForm form, BindException errors) throws Exception
        {
            ThreadView threadView = new ThreadView(form, getContainer(), getActionURL(), getPermissions(), isPrint());
            threadView.setFrame(WebPartView.FrameType.PORTAL);

            AnnouncementModel ann = threadView.getAnnouncement();
            _title = ann != null ? ann.getTitle() : "Error";

            String anchor = getActionURL().getParameter("_anchor");

            if (null != anchor)
                getPageConfig().setAnchor("row:" + anchor);

            return threadView;
        }

        @Override
        public ModelAndView getPrintView(AnnouncementForm form, BindException errors) throws Exception
        {
            ThreadView tv = getView(form, errors);
            // title is already in the thread view don't need to repeat it
            tv.setFrame(WebPartView.FrameType.NONE);
            tv.getModelBean().print = true;
            return tv;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root).addChild(_title, getActionURL());
            return root;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ThreadBareAction extends ThreadAction
    {
        @Override
        public ThreadView getView(AnnouncementForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            ThreadView tv = super.getView(form, errors);
            tv.setFrame(WebPartView.FrameType.NONE);
            tv.getModelBean().embedded = true;
            return tv;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class RssAction extends SimpleViewAction
    {
        // Support basic auth challenge #8520
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            setUseBasicAuthentication(true);
            super.checkPermissions();
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();

            // getFilter performs further permission checking on secure board (e.g., non-Editors only see threads where they're on the member list)
            SimpleFilter filter = getFilter(getSettings(), getPermissions(), true);

            // TODO: This only grabs announcementModels... add responses too?
            Pair<AnnouncementModel[], Boolean> pair = AnnouncementManager.getAnnouncements(c, filter, getSettings().getSort(), 100);

            ActionURL url = getThreadURL(c, "", 0).deleteParameters().addParameter("rowId", (String)null);

            WebPartView v = new RssView(pair.first, url.getURIString());

            getResponse().setContentType("text/xml");
            getPageConfig().setTemplate(PageConfig.Template.None);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        public ActionURL getURL()
        {
            return new ActionURL(RssAction.class, getContainer());
        }
    }


    public static class RssView extends JspView<RssView.RssBean>
    {
        private RssView(AnnouncementModel[] announcementModels, String url)
        {
            super("/org/labkey/announcements/rss.jsp", new RssBean(announcementModels, url));
            setFrame(WebPartView.FrameType.NONE);
        }

        public static class RssBean
        {
            public AnnouncementModel[] announcementModels;
            public String url;

            private RssBean(AnnouncementModel[] announcementModels, String url)
            {
                this.announcementModels = announcementModels;
                this.url = url;
            }
        }
    }


    @RequiresSiteAdmin
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int rows = ContainerUtil.purgeTable(_comm.getTableInfoAnnouncements(), null);
            return new HtmlView("deleted " + rows + " pages<br>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static ActionURL getEmailPreferencesURL(Container c, URLHelper srcUrl, String srcIdentifier)
    {
        ActionURL result = new ActionURL(EmailPreferencesAction.class, c);
        result.addParameter("srcIdentifier", srcIdentifier);
        return result.addParameter("srcUrl", srcUrl.getLocalURIString());
    }


    @RequiresLogin
    public class EmailPreferencesAction extends FormViewAction<EmailOptionsForm>
    {
        private String _message = null;

        public ActionURL getSuccessURL(EmailOptionsForm form)
        {
            return null;  // Reshow the page with success message
        }

        public ModelAndView getView(EmailOptionsForm form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainer();

            User user = getUser();
            List<User> projectUsers = SecurityManager.getProjectUsers(c, false);

            int emailOption = getEmailOptionIncludingInherited(c, user, form.getSrcIdentifier());

            form.setEmailOption(emailOption);

            setHelpTopic("createMessage");
            JspView view = new JspView("/org/labkey/announcements/emailPreferences.jsp");
            view.setFrame(WebPartView.FrameType.PORTAL);
            EmailPreferencesPage page = (EmailPreferencesPage)view.getPage();
            view.setTitle("Email Preferences");
            view.setShowTitle(false);

            Settings settings = getSettings();
            page.emailPreference = form.getEmailPreference();
            page.srcIdentifier = form.getSrcIdentifier();
            page.notificationType = form.getNotificationType();
            page.srcURL = form.getSrcUrl();
            page.message = _message;
            page.hasMemberList = settings.hasMemberList();
            page.conversationName = settings.getConversationName().toLowerCase();
            page.isProjectMember = projectUsers.contains(user);

            return view;
        }

        public boolean handlePost(EmailOptionsForm form, BindException errors) throws Exception
        {
            AnnouncementManager.saveEmailPreference(getUser(), getContainer(), form.getEmailOption(), form.getSrcIdentifier());

            _message = "Setting changed successfully.";

            return true;
        }

        public void validateCommand(EmailOptionsForm target, Errors errors)
        {
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root)
                             .addChild("Email Preferences");

            return root;
        }
    }

    /** Resolve the effective subscription level for the object. If not explicitly set, check for a user subscription
     * at the container level, and then fall back to the container default. */
    private static int getEmailOptionIncludingInherited(Container c, User user, String srcIdentifier)
    {
        int emailOption = AnnouncementManager.getUserEmailOption(c, user, srcIdentifier);
        if (emailOption == AnnouncementManager.EMAIL_PREFERENCE_DEFAULT)
        {
            if (!srcIdentifier.equals(c.getId()))
            {
                emailOption = AnnouncementManager.getUserEmailOption(c, user, c.getId());
            }
            if (emailOption == AnnouncementManager.EMAIL_PREFERENCE_DEFAULT)
            {
                emailOption = AnnouncementManager.getDefaultEmailOption(c);
            }
        }
        return emailOption;
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SetDefaultEmailOptionsAction extends RedirectAction<EmailDefaultSettingsForm>
    {
        public boolean doAction(EmailDefaultSettingsForm form, BindException errors) throws Exception
        {
            //save the default settings
            AnnouncementManager.saveDefaultEmailOption(getContainer(), form.getDefaultEmailOption());

            return true;
        }

        public ActionURL getSuccessURL(EmailDefaultSettingsForm form)
        {
            return getAdminEmailURL(getContainer(), form.getReturnURLHelper(null));
        }

        public void validateCommand(EmailDefaultSettingsForm target, Errors errors)
        {
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SetEmailDefault extends ApiAction<AnnouncementEmailConfig.EmailConfigForm>
    {
        @Override
        public ApiResponse execute(AnnouncementEmailConfig.EmailConfigForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            StringBuilder message = new StringBuilder("The current default has been updated to: ");

            //save the default settings
            AnnouncementManager.saveDefaultEmailOption(getContainer(), form.getDefaultEmailOption());

            for (MessageConfigService.NotificationOption option : AnnouncementManager.getEmailOptions())
            {
                if (option.getEmailOptionId() == form.getDefaultEmailOption())
                {
                    message.append(option.getEmailOption());
                    break;
                }
            }
            resp.put("success", true);
            resp.put("message", message.toString());

            return resp;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SetBulkEmailOptions extends ApiAction<AnnouncementEmailConfig.EmailConfigForm>
    {
        @Override
        public ApiResponse execute(AnnouncementEmailConfig.EmailConfigForm form, BindException errors) throws Exception
        {
            Set<String> selections = DataRegionSelection.getSelected(getViewContext(), form.getDataRegionSelectionKey(), true, true);
            ApiSimpleResponse resp = new ApiSimpleResponse();
            MessageConfigService.ConfigTypeProvider provider = form.getProvider();
            String srcIdentifier = getContainer().getId();

            if (!selections.isEmpty() && provider != null)
            {
                int newOption = form.getIndividualEmailOption();
                for (String user : selections)
                {

                    User projectUser = UserManager.getUser(Integer.parseInt(user));
                    MessageConfigService.UserPreference pref = provider.getPreference(getContainer(), projectUser, srcIdentifier);

                    int currentEmailOption = pref != null ? pref.getEmailOptionId() : -1;

                    //has this projectUser's option changed? if so, update
                    //creating new record in EmailPrefs table if there isn't one, or deleting if set back to folder default
                    if (currentEmailOption != newOption)
                    {
                        provider.savePreference(getUser(), getContainer(), projectUser, newOption, srcIdentifier);
                    }
                }
                resp.put("success", true);
            }
            else
            {
                resp.put("success", false);
                resp.put("message", "There were no users selected");
            }
            return resp;
        }
    }

    public static class NotifyOptionsForm
    {
        private String _type;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public MessageConfigService.ConfigTypeProvider getProvider()
        {
            return MessageConfigService.getInstance().getConfigType(getType());
        }
    }

    /**
     * Action to populate an Ext store with email notification options for admin settings
     */
    @RequiresPermissionClass(AdminPermission.class)
    public class GetEmailOptions extends ApiAction<NotifyOptionsForm>
    {
        @Override
        public ApiResponse execute(NotifyOptionsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            MessageConfigService.ConfigTypeProvider provider = form.getProvider();
            if (provider != null)
            {
                List<Map> options = new ArrayList<Map>();

                // if the list of options is not for the folder default, add an option to use the folder default
                if (getViewContext().get("isDefault") == null)
                    options.add(PageFlowUtil.map("id", -1, "label", "Folder default"));

                for (MessageConfigService.NotificationOption option : provider.getOptions())
                {
                    options.add(PageFlowUtil.map("id", option.getEmailOptionId(), "label", option.getEmailOption()));
                }
                resp.put("success", true);
                if (!options.isEmpty())
                    resp.put("options", options);
            }
            else
                resp.put("success", false);

            return resp;
        }
    }

    private static NotFoundException createThreadNotFoundException(Container c)
    {
        return new NotFoundException("Could not find " + getSettings(c).getConversationName().toLowerCase());
    }
    
    public static class AnnouncementDeleteForm extends ReturnUrlForm
    {
        private int _rowId;
        private String _entityId;
        private String _cancelUrl;

        public String getEntityId()
        {
            return _entityId;
        }

        public void setEntityId(String entityId)
        {
            _entityId = entityId;
        }

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getCancelUrl()
        {
            return _cancelUrl;
        }

        public void setCancelUrl(String cancelUrl)
        {
            _cancelUrl = cancelUrl;
        }
    }

    public static class AnnouncementForm extends BeanViewForm<AnnouncementModel>
    {
        AnnouncementModel _selectedAnnouncementModel = null;
        List<User> _memberList = null;

        public AnnouncementForm()
        {
            super(AnnouncementModel.class, null, Collections.<String, Class>singletonMap("parentid", GuidString.class));
        }

        // XXX: change return value to typed GuidString
        public String getParentId()
        {
            return _stringValues.get("parentid");
        }

        public List<User> getMemberList()
        {
            return _memberList;
        }

        public void setMemberList(List<User> memberList)
        {
            _memberList = memberList;
        }

        AnnouncementModel selectAnnouncement() throws SQLException
        {
            if (null == _selectedAnnouncementModel)
            {
                AnnouncementModel bean = getBean();
                if (null != bean.getEntityId())
                    _selectedAnnouncementModel = AnnouncementManager.getAnnouncement(getContainer(), bean.getEntityId(), true);  // Need member list
                if (null == _selectedAnnouncementModel)
                    _selectedAnnouncementModel = AnnouncementManager.getAnnouncement(getContainer(), bean.getRowId(), AnnouncementManager.INCLUDE_MEMBERLIST);
            }
            return _selectedAnnouncementModel;
        }

        public void validate(Errors errors)
        {
            Settings settings = getSettings(getContainer());
            AnnouncementModel bean = getBean();

            // Title can never be null.  If title is not editable, it will still be posted in a hidden field.
            if (StringUtils.trimToNull(bean.getTitle()) == null)
                errors.reject(ERROR_MSG, "Title must not be blank.");

            try
            {
                String expires = StringUtils.trimToNull((String) get("expires"));
                if (null != expires)
                    DateUtil.parseDateTime(expires);
            }
            catch (ConversionException x)
            {
                errors.reject(ERROR_MSG, "Expires must be blank or a valid date.");
            }

            String emailList = bean.getEmailList();
            List<User> memberList = Collections.emptyList();

            if (null != emailList)
            {
                String[] rawEmails = emailList.split("\n");
                List<String> invalidEmails = new ArrayList<String>();
                List<ValidEmail> emails = SecurityManager.normalizeEmails(rawEmails, invalidEmails);

                for (String rawEmail : invalidEmails)
                {
                    // Ignore lines of all whitespace, otherwise show an error.
                    if (!"".equals(rawEmail.trim()))
                        errors.reject(ERROR_MSG, rawEmail.trim() + ": Invalid email address");
                }

                memberList = new ArrayList<User>(emails.size());

                for (ValidEmail email : emails)
                {
                    User user = UserManager.getUser(email);

                    if (null == user)
                        errors.reject(ERROR_MSG, email.getEmailAddress() + ": Doesn't exist");
                    else if (!memberList.contains(user))
                        memberList.add(user);
                }

                // New up an announcementModel to check permissions for the member list
                AnnouncementModel ann = new AnnouncementModel();
                ann.setMemberList(memberList);

                for (User user : memberList)
                {
                    Permissions perm = getPermissions(getContainer(), user, settings);

                    if (!perm.allowRead(ann))
                        errors.reject(ERROR_MSG, "Can't add " + user.getEmail() + " to the member list: This user doesn't have permission to read the thread.");
                }

                setMemberList(memberList);
            }

            Integer assignedTo = bean.getAssignedTo();

            if (null != assignedTo)
            {
                User assignedToUser = UserManager.getUser(assignedTo);

                if (null == assignedToUser)
                {
                    errors.reject(ERROR_MSG, "Assigned to user " + assignedTo + ": Doesn't exist");
                }
                else
                {
                    Permissions perm = getPermissions(getContainer(), assignedToUser, settings);

                    // New up an announcementModel to check permissions for the assigned to user
                    AnnouncementModel ann = new AnnouncementModel();
                    ann.setMemberList(memberList);

                    if (!perm.allowRead(ann))
                        errors.reject(ERROR_MSG, "Can't assign to " + assignedToUser.getEmail() + ": This user doesn't have permission to read the thread.");
                }
            }

            if ("HTML".equals(bean.getRendererType()))
            {
                Collection<String> validateErrors = new LinkedList<String>();
                PageFlowUtil.validateHtml(bean.getBody(), validateErrors, getUser().isDeveloper());
                for (String err : validateErrors)
                    errors.reject(ERROR_MSG, err);
            }
        }

        public ActionURL getCancelUrl()
        {
            String urlString = StringUtils.trimToNull((String)get(ActionURL.Param.cancelUrl.name()));

            if (null == urlString)
                return null;
            else
                return new ActionURL(urlString);
        }

        public boolean isFromDiscussion()
        {
            String fromDiscussion = (String)get("fromDiscussion");

            return Boolean.parseBoolean(fromDiscussion);
        }

        public boolean allowMultipleDiscussions()
        {
            String fromDiscussion = (String)get("allowMultipleDiscussions");

            return Boolean.parseBoolean(fromDiscussion);
        }
    }


    public static class EmailDefaultSettingsForm extends ReturnUrlForm
    {
        private int _defaultEmailOption;
        private int _defaultEmailFormat;

        public int getDefaultEmailFormat()
        {
            return _defaultEmailFormat;
        }

        public void setDefaultEmailFormat(int defaultEmailFormat)
        {
            _defaultEmailFormat = defaultEmailFormat;
        }

        public int getDefaultEmailOption()
        {
            return _defaultEmailOption;
        }

        public void setDefaultEmailOption(int defaultEmailOption)
        {
            _defaultEmailOption = defaultEmailOption;
        }
    }

    public static class EmailOptionsForm extends ViewForm
    {
        private int _emailPreference = AnnouncementManager.EMAIL_PREFERENCE_NONE;
        private int _notificationType = 0;
        private String _srcIdentifier;
        private String _srcUrl = null;

        // Email option is a single int that contains the conversation preference AND a bit for digest vs. individual
        // This method splits them apart
        public void setEmailOption(int emailOption)
        {
            _emailPreference = emailOption & AnnouncementManager.EMAIL_PREFERENCE_MASK;
            _notificationType = emailOption & AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST;
        }

        public int getEmailOption()
        {
            // Form allows "no email" + "daily digest" -- change this to "no email" + "individual" since they are equivalent
            // and we don't want to deal with the former option in the database, with foreign keys, on the admin pages, etc. 
            if (_emailPreference == AnnouncementManager.EMAIL_PREFERENCE_NONE)
                _notificationType = 0;

            return _emailPreference | _notificationType;
        }

        public int getEmailPreference()
        {
            return _emailPreference;
        }

        public void setEmailPreference(int emailPreference)
        {
            _emailPreference = emailPreference;
        }

        public int getNotificationType()
        {
            return _notificationType;
        }

        public void setNotificationType(int notificationType)
        {
            _notificationType = notificationType;
        }

        public String getSrcUrl()
        {
            return _srcUrl;
        }

        public void setSrcUrl(String srcUrl)
        {
            _srcUrl = srcUrl;
        }

        public String getSrcIdentifier()
        {
            return _srcIdentifier == null ? getContainer().getEntityId().toString() : _srcIdentifier;
        }

        public void setSrcIdentifier(String srcIdentifier)
        {
            _srcIdentifier = srcIdentifier;
        }
    }


    public static class AnnouncementEmailDefaults extends JspView<AnnouncementEmailDefaults.EmailDefaultsBean>
    {
        public AnnouncementEmailDefaults(Container c, URLHelper returnURL) throws SQLException
        {
            super("/org/labkey/announcements/announcementEmailDefaults.jsp", new EmailDefaultsBean(c, returnURL));
        }

        public static class EmailDefaultsBean
        {
            public List<MessageConfigService.NotificationOption> emailOptionsList;
            public int defaultEmailOption;
            public URLHelper returnURL;

            private EmailDefaultsBean(Container c, URLHelper returnURL) throws SQLException
            {
                emailOptionsList = Arrays.asList(AnnouncementManager.getEmailOptions());
                defaultEmailOption = AnnouncementManager.getDefaultEmailOption(c);
                this.returnURL = returnURL;
            }
        }
    }


    public abstract static class LinkBarBean
    {
        public DiscussionService.Settings settings;
        public String filterText;
        public ActionURL customizeURL;
        public ActionURL emailPrefsURL;
        public ActionURL emailManageURL;
        public ActionURL insertURL;
        public boolean includeGroups;

        protected void init(Container c, ActionURL url, User user, DiscussionService.Settings settings, Permissions perm, boolean displayAll, boolean isFiltered, int rowLimit)
        {
            this.settings = settings;
            filterText = getFilterText(settings, displayAll, isFiltered, rowLimit);
            customizeURL = c.hasPermission(user, AdminPermission.class) ? getCustomizeURL(c, url) : null;
            emailPrefsURL   = user.isGuest() ? null : getEmailPreferencesURL(c, url, c.getId());
            emailManageURL = c.hasPermission(user, AdminPermission.class) ? getAdminEmailURL(c, url) : null;
            insertURL = perm.allowInsert() ? getInsertURL(c, url) : null;
            includeGroups = perm.includeGroups();
        }
    }


    public static class ListLinkBar extends JspView<ListLinkBar.ListBean>
    {
        private ListLinkBar(Container c, ActionURL url, User user, DiscussionService.Settings settings, Permissions perm, boolean displayAll)
        {
            super("/org/labkey/announcements/announcementListLinkBar.jsp", new ListBean(c, url, user, settings, perm, displayAll));

            ListBean bean = new ListBean(c, url, user, settings, perm, displayAll);
            NavTree menu = new NavTree("");
            
            if (bean.emailPrefsURL != null || bean.emailManageURL != null)
            {
                NavTree email = new NavTree("Email", "", getViewContext().getContextPath() + "/_images/email.png");
                if (bean.emailPrefsURL != null)
                    email.addChild("Preferences", bean.emailPrefsURL);
                if (bean.emailManageURL != null)
                    email.addChild("Administration", bean.emailManageURL);
                if (bean.customizeURL != null)
                    menu.addChild("Customize", bean.customizeURL);
                menu.addChild(email);
            }
            
            setNavMenu(menu);
        }

        public static class ListBean extends LinkBarBean
        {
            public ActionURL messagesURL;
            public String urlFilterText;

            private ListBean(Container c, ActionURL url, User user, DiscussionService.Settings settings, Permissions perm, boolean displayAll)
            {
                SimpleFilter urlFilter = new SimpleFilter(url, "Threads");
                boolean isFiltered = !urlFilter.getWhereParamNames().isEmpty();

                init(c, url, user, settings, perm, displayAll, isFiltered, 0);

                messagesURL = getBeginURL(c);
                urlFilterText = isFiltered ? urlFilter.getFilterText(
                    new SimpleFilter.ColumnNameFormatter()
                    {
                        @Override
                        public String format(String columnName)
                        {
                            return super.format(columnName).replaceFirst(".DisplayName", "");
                        }
                    }) : null;
            }
        }
    }


    public static class AnnouncementWebPart extends JspView<AnnouncementWebPart.MessagesBean>
    {
        public AnnouncementWebPart(Container c, ActionURL url, User user, Settings settings, boolean displayAll, boolean asWebPart) throws SQLException, ServletException
        {
            super("/org/labkey/announcements/announcementWebPartWithExpandos.jsp",
                new MessagesBean(c, url, user, settings, displayAll));
            setTitle(settings.getBoardName());
            setTitleHref(getBeginURL(c));

            MessagesBean bean = getModelBean(); 
            NavTree menu = new NavTree("");
            if (bean.insertURL != null)
                menu.addChild("New", bean.insertURL);
            if (bean.listURL != null)
                menu.addChild("View List", bean.listURL);

            if (bean.emailPrefsURL != null || bean.emailManageURL != null)
            {
                NavTree email = new NavTree("Email");
                if (bean.emailPrefsURL != null)
                    email.addChild("Preferences", bean.emailPrefsURL);
                if (bean.emailManageURL != null)
                    email.addChild("Administration", bean.emailManageURL);
                menu.addChild(email);
            }

            if (bean.customizeURL != null)
                setCustomize(new NavTree("", bean.customizeURL.toString()));

            setIsWebPart(asWebPart);
            setNavMenu(menu);
        }

        public AnnouncementWebPart(ViewContext ctx) throws SQLException, ServletException
        {
            this(ctx.getContainer(), getPageURL(ctx), ctx.getUser(), getSettings(ctx.getContainer()), false, true);
        }

        private static ActionURL getPageURL(ViewContext ctx)
        {
            // This is set to the outer page URL in the case of rendering a dynamic webpart; use it instead of
            // the getWebPart URL.
            String returnURL = (String)ctx.get(ActionURL.Param.returnUrl.name());

            if (null != returnURL)
                return new ActionURL(returnURL);
            else
                return ctx.getActionURL();
        }

        public static class MessagesBean extends LinkBarBean
        {
            public AnnouncementModel[] announcementModels;
            public ActionURL listURL;
            public boolean isPrint=false;

            private MessagesBean(Container c, ActionURL url, User user, Settings settings, boolean displayAll)
            {
                Permissions perm = getPermissions(c, user, settings);
                SimpleFilter filter = getFilter(settings, perm, displayAll);
                Pair<AnnouncementModel[], Boolean> pair = AnnouncementManager.getAnnouncements(c, filter, settings.getSort(), 100);

                init(c, url, user, settings, perm, displayAll, false, pair.second ? 100 : 0);
                
                announcementModels = pair.first;
                listURL = getListURL(c);
            }
        }
    }


    private static SimpleFilter getFilter(DiscussionService.Settings settings, Permissions perm, boolean displayAll)
    {
        // Filter out threads that this user can't read
        SimpleFilter filter = perm.getThreadFilter();

        if (!displayAll)
        {
            if (settings.hasExpires())
                filter.addWhereClause("Expires IS NULL OR Expires > ?", new Object[]{new Date(System.currentTimeMillis() - DateUtils.MILLIS_PER_DAY)});

            if (settings.hasStatus())
                filter.addCondition("Status", "Closed", CompareType.NEQ_OR_NULL);
        }

        return filter;
    }


    private static String getFilterText(DiscussionService.Settings settings, boolean displayAll, boolean isFiltered, int rowLimit)
    {
        StringBuilder sb = new StringBuilder();

        String separator = "";

        if (!displayAll)
        {
            if (settings.hasExpires())
            {
                sb.append("recent");
                separator = ", ";
            }

            if (settings.hasStatus())
            {
                sb.append(separator);
                sb.append("open");
                separator = ", ";
            }
        }

        if (isFiltered)
        {
            sb.append(separator);
            sb.append("filtered");
            separator = ", ";
        }

        if (rowLimit > 0)
        {
            sb.append(separator);
            sb.append("limited to ");
            sb.append(rowLimit);
        }

        if (sb.length() == 0)
            sb.append("all");

        sb.append(" ");
        sb.append(settings.getConversationName().toLowerCase());
        sb.append("s");

        return sb.toString();
    }


    public static class AnnouncementListWebPart extends WebPartView
    {
        private VBox _vbox;

        public AnnouncementListWebPart(ViewContext ctx) throws ServletException
        {
            this(ctx, false, true);
        }

        private AnnouncementListWebPart(ViewContext ctx, boolean displayAll, boolean asWebPart) throws ServletException
        {
            Container c = ctx.getContainer();
            User user = ctx.getUser();
            ActionURL url = ctx.getActionURL();

            DiscussionService.Settings settings = getSettings(c);
            Permissions perm = getPermissions(c, user, settings);
            DataRegion rgn = getDataRegion(perm, settings);

            setTitle(settings.getBoardName() + " List");
            setTitleHref(getListURL(c));

            TableInfo tinfo = _comm.getTableInfoThreads();
            DisplayColumn title = new DataColumn(tinfo.getColumn("Title"));
            rgn.addDisplayColumn(title);

            if (settings.hasStatus())
                rgn.addColumn(tinfo.getColumn("Status"));

            if (settings.hasAssignedTo())
            {
                DisplayColumn dc = new UserIdRenderer(tinfo.getColumn("AssignedTo"));
                rgn.addDisplayColumn(dc);
            }

            if (settings.hasExpires())
                rgn.addColumn(tinfo.getColumn("Expires"));

            ColumnInfo colCreatedBy = tinfo.getColumn("CreatedBy"); // TODO: setRenderClass?
            DisplayColumn dc = new UserIdRenderer(colCreatedBy);
            rgn.addDisplayColumn(dc);

            if (perm.includeGroups())
            {
                DisplayColumn createGroups = new GroupColumn(colCreatedBy);
                createGroups.setCaption("Groups");
                rgn.addDisplayColumn(createGroups);
            }

            rgn.addColumn(tinfo.getColumn("Created"));

            ColumnInfo colResponseCreatedBy = tinfo.getColumn("ResponseCreatedBy"); // TODO: setRenderClass?
            DisplayColumn lastDc = new UserIdRenderer(colResponseCreatedBy);
            rgn.addDisplayColumn(lastDc);

            if (perm.includeGroups())
            {
                DisplayColumn responseGroups = new GroupColumn(colResponseCreatedBy);
                responseGroups.setCaption("Groups");
                rgn.addDisplayColumn(responseGroups);
            }

            rgn.addColumn(tinfo.getColumn("ResponseCreated"));

            GridView gridView = new GridView(rgn, (BindException)null);
            gridView.setFrame(FrameType.PORTAL);  // Prevent double title
            gridView.setContainer(c);
            gridView.setSort(settings.getSort());

            SimpleFilter filter = getFilter(settings, perm, displayAll);
            gridView.setFilter(filter);

            ListLinkBar bar = new ListLinkBar(c, url, user, settings, perm, displayAll);
            setNavMenu(bar.getNavMenu());
            setIsWebPart(asWebPart);
            _vbox = new VBox(bar, gridView);
        }

        protected DataRegion getDataRegion(Permissions perm, DiscussionService.Settings settings)
        {
            QuerySettings qs = new QuerySettings(getViewContext(), "Announcements");
            DataRegion rgn = new DataRegion();
            rgn.setSettings(qs);
            rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
            rgn.setShadeAlternatingRows(true);
            return rgn;
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            include(_vbox);
        }

        private static class GroupColumn extends DataColumn
        {
            public GroupColumn(ColumnInfo column)
            {
                super(column);
            }


            @Override
            public String getFormattedValue(RenderContext ctx)
            {
                Integer userId = (Integer)getValue(ctx);

                if (null != userId)
                {
                    User user = UserManager.getUser(userId);

                    if (null != user)
                        return SecurityManager.getGroupList(ctx.getContainer(), user);
                }

                return "";
            }
        }
    }


    public static class AnnouncementListView extends AnnouncementListWebPart
    {
        public AnnouncementListView(ViewContext ctx) throws ServletException
        {
            super(ctx, true, false);
        }

        @Override
        protected DataRegion getDataRegion(Permissions perm, DiscussionService.Settings settings)
        {
            DataRegion rgn = super.getDataRegion(perm, settings);

            if (perm.allowDeleteAnyThread())
            {
                ButtonBar bb = new ButtonBar();
                rgn.setShowRecordSelectors(true);

                String conversation = settings.getConversationName().toLowerCase();
                String conversations = conversation + "s";
                ActionButton delete = new ActionButton(DeleteThreadsAction.class, "Delete");
                delete.setActionType(ActionButton.Action.GET);
                delete.setDisplayPermission(DeletePermission.class);
                delete.setRequiresSelection(true, "Are you sure you want to delete this " + conversation + "?", "Are you sure you want to delete these " + conversations + "?");
                bb.add(delete);

                rgn.setButtonBar(bb);
            }
            else
                rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

            return rgn;
        }
    }


    public static class ThreadViewBean
    {
        public AnnouncementModel announcementModel;
        public String message = "";
        public Permissions perm = null;
        public boolean isResponse = false;
        public DiscussionService.Settings settings;
        public ActionURL messagesURL;
        public ActionURL listURL;
        public URLHelper printURL;
        public URLHelper currentURL;
        public boolean print = false;
        public boolean includeGroups;
        public boolean embedded;
    }


    public static class ThreadView extends JspView<ThreadViewBean>
    {
        private ThreadView()
        {
            super("/org/labkey/announcements/announcementThread.jsp", new ThreadViewBean());
        }

        public ThreadView(Container c, URLHelper currentURL, User user, String rowId, String entityId) throws ServletException
        {
            this();
            init(c, findThread(c, rowId, entityId), currentURL, getPermissions(c, user, getSettings(c)), false, false);
        }

        public ThreadView(Container c, ActionURL url, AnnouncementModel ann, Permissions perm) throws ServletException
        {
            this();
            init(c, ann, url, perm, true, false);
        }
        
        public ThreadView(AnnouncementForm form, Container c, ActionURL url, Permissions perm, boolean print)
                throws ServletException
        {
            this();
            AnnouncementModel ann = findThread(c, (String)form.get("rowId"), (String)form.get("entityId"));
            init(c, ann, url, perm, false, print);
        }

        protected void init(Container c, AnnouncementModel ann, URLHelper currentURL, Permissions perm, boolean isResponse, boolean print)
                throws ServletException
        {
            if (null == c || !perm.allowRead(ann))
            {
                throw new UnauthorizedException();
            }

            if (ann instanceof AnnouncementManager.BareAnnouncementModel)
                throw new IllegalArgumentException("can't use getBareAnnoucements() with this view");

            ThreadViewBean bean = getModelBean();
            bean.announcementModel = ann;
            bean.currentURL = currentURL;
            bean.settings = getSettings(c);
            bean.message = null;
            bean.perm = perm;
            bean.isResponse = isResponse;
            bean.messagesURL = getBeginURL(c);  // TODO: Used as returnURL after delete thread... should be messages or list, as appropriate
            bean.listURL = getListURL(c);
            bean.printURL = null == currentURL ? null : currentURL.clone().replaceParameter("_print", "1");
            bean.print = print;
            bean.includeGroups = perm.includeGroups();
            bean.embedded = (ann != null && null != ann.getDiscussionSrcURL() && !getViewContext().getActionURL().getController().equalsIgnoreCase("announcements"));  // TODO: Should have explicit flag for discussion case

            if (!bean.print && !bean.embedded)
            {
                NavTree buttons = new NavTree();
                if (null != bean.listURL)
                {
                    buttons.addChild("view list", bean.listURL);
                }
                if (!bean.isResponse)
                {
                    buttons.addChild("print", bean.printURL);
                }

                // Create buttons to subscribe or unsubscribe from the forum and/or individual thread
                if (!getViewContext().getUser().isGuest())
                {
                    // First check if the user is subscribed to this specific thread
                    if (ann.getMemberList().contains(getViewContext().getUser()))
                    {
                        // Build up a link to unsubscribe from the thread
                        ActionURL url = new ActionURL(SubscribeThreadAction.class, c);
                        url.addParameter("threadId", ann.getParent() == null ? ann.getEntityId() : ann.getParent());
                        url.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().toString());
                        url.addParameter("unsubscribe", true);
                        buttons.addChild("unsubscribe", url);
                    }
                    else
                    {
                        // See if they're subscribed to the whole forum
                        int emailOption = getEmailOptionIncludingInherited(c, getViewContext().getUser(), ann.lookupSrcIdentifer());

                        // Or if they're subscribed because they've posted to this thread already
                        // Remember the emailOption is a bitmask, so don't use simple equality checks
                        boolean forumSubscription = (emailOption & AnnouncementManager.EMAIL_PREFERENCE_ALL) != 0 ||
                            ((emailOption & AnnouncementManager.EMAIL_PREFERENCE_MINE) != 0 && ann.getAuthors().contains(getViewContext().getUser()));
                        
                        if (forumSubscription)
                        {
                            // Give them a link to the forum level subscription UI
                            buttons.addChild("unsubscribe", getEmailPreferencesURL(c, getViewContext().getActionURL(), ann.lookupSrcIdentifer()));
                        }
                        else
                        {
                            // Otherwise, let them subscribe to either the forum or the specific thread
                            NavTree subscribeTree = new NavTree("subscribe");
                            subscribeTree.addChild("forum", getEmailPreferencesURL(c, getViewContext().getActionURL(), ann.lookupSrcIdentifer()));
                            ActionURL subscribeThreadURL = new ActionURL(SubscribeThreadAction.class, c);
                            subscribeThreadURL.addParameter("threadId", ann.getParent() == null ? ann.getEntityId() : ann.getParent());
                            subscribeThreadURL.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().toString());
                            subscribeTree.addChild("thread", subscribeThreadURL);
                            buttons.addChild(subscribeTree);
                        }
                    }
                }

                setNavMenu(buttons);
                setIsWebPart(false);
            }

            setTitle("View " + bean.settings.getConversationName());
        }

        public AnnouncementModel getAnnouncement()
        {
            return getModelBean().announcementModel;
        }
    }


    private static AnnouncementModel findThread(Container c, String rowIdVal, String entityId)
    {
        int rowId = 0;
        if (rowIdVal != null)
        {
            try
            {
                rowId = Integer.parseInt(rowIdVal);
            }
            catch(NumberFormatException e)
            {
                throw createThreadNotFoundException(c);
            }
        }

        if (0 != rowId)
            return AnnouncementManager.getAnnouncement(c, rowId, AnnouncementManager.INCLUDE_RESPONSES + AnnouncementManager.INCLUDE_MEMBERLIST);
        else if (null == entityId)
            throw createThreadNotFoundException(c);

        return AnnouncementManager.getAnnouncement(c, entityId, true);
    }

    public static class SubscriptionBean extends ReturnUrlForm
    {
        private String _threadId;
        private boolean _unsubscribe;

        public String getThreadId()
        {
            return _threadId;
        }

        public void setThreadId(String threadId)
        {
            _threadId = threadId;
        }

        public boolean isUnsubscribe()
        {
            return _unsubscribe;
        }

        public void setUnsubscribe(boolean unsubscribe)
        {
            _unsubscribe = unsubscribe;
        }
    }

    @RequiresLogin @RequiresPermissionClass(ReadPermission.class)
    public class SubscribeThreadAction extends RedirectAction<SubscriptionBean>
    {
        @Override
        public URLHelper getSuccessURL(SubscriptionBean subscriptionBean)
        {
            return subscriptionBean.getReturnActionURL(getBeginURL(getContainer()));
        }

        @Override
        public boolean doAction(SubscriptionBean bean, BindException errors) throws Exception
        {
            String id = bean.getThreadId();
            if (id == null)
            {
                throw new NotFoundException("No message thread specified");
            }
            // Don't filter by container to make it easier for client API users that are going cross-container
            AnnouncementModel ann = AnnouncementManager.getAnnouncement(null, id, true);
            if (ann == null)
            {
                throw new NotFoundException("No such message thread: " + id);
            }
            // Make sure they have permission to see the container for the specific message they're
            // requesting
            if (!ann.lookupContainer().hasPermission(getUser(), ReadPermission.class))
            {
                throw new UnauthorizedException();
            }

            // Remove or add the thread-level subscription from the database table
            if (bean.isUnsubscribe())
            {
                Table.execute(CommSchema.getInstance().getSchema(), "DELETE FROM comm.userlist WHERE UserId = ? AND MessageId = ?", getUser().getUserId(), ann.getRowId());
            }
            else if (!ann.getMemberList().contains(getUser()))
            {
                Table.execute(CommSchema.getInstance().getSchema(), "INSERT INTO comm.userlist (UserId, MessageId) VALUES (?, ?)", getUser().getUserId(), ann.getRowId());
            }
            return true;
        }

        @Override
        public void validateCommand(SubscriptionBean target, Errors errors)
        {

        }
    }

    
    public static class AnnouncementUpdateView extends JspView<AnnouncementUpdateView.UpdateBean>
    {
        public AnnouncementUpdateView(AnnouncementForm form, AnnouncementModel ann, BindException errors)
        {
            super("/org/labkey/announcements/update.jsp", null, errors);
            setModelBean(new UpdateBean(form, ann));

            if (ann.getParent() == null)
            {
                setTitle("Edit Message");
            }
            else
            {
                setTitle("Edit Response");
            }
        }

        public class UpdateBean
        {
            public AnnouncementModel annModel;
            public Settings settings;
            public String assignedToSelect;
            public String statusSelect;
            public String memberList;
            public WikiRendererType[] renderers;
            public WikiRendererType currentRendererType;
            public URLHelper returnURL;

            private UpdateBean(AnnouncementForm form, AnnouncementModel ann)
            {
                Container c = form.getContainer();
                String reshowEmailList = (String)form.get("emailList");

                this.annModel = ann;
                settings = getSettings(c);
                currentRendererType = WikiRendererType.valueOf(ann.getRendererType());
                renderers = WikiRendererType.values();
                memberList = getMemberListTextArea(form.getUser(), c, ann, null != reshowEmailList ? reshowEmailList : null);
                statusSelect = getStatusSelect(settings, ann.getStatus());
                assignedToSelect = getAssignedToSelect(c, ann.getAssignedTo(), "assignedTo", getViewContext().getUser());
                returnURL = form.getReturnURLHelper();
            }
        }
    }
}
