/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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

package org.labkey.core.user;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.UserSchemaAction;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.impersonation.GroupImpersonationContextFactory;
import org.labkey.api.security.impersonation.RoleImpersonationContextFactory;
import org.labkey.api.security.impersonation.UnauthorizedImpersonationException;
import org.labkey.api.security.impersonation.UserImpersonationContextFactory;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.util.emailTemplate.UserOriginatedEmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.api.view.template.TemplateHeaderView;
import org.labkey.core.login.DbLoginAuthenticationProvider;
import org.labkey.core.login.LoginController;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.UserAuditProvider;
import org.labkey.core.query.UsersDomainKind;
import org.labkey.core.query.UsersTable;
import org.labkey.core.security.SecurityController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class UserController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(UserController.class);

    static
    {
        EmailTemplateService.get().registerTemplate(RequestAddressEmailTemplate.class);
        EmailTemplateService.get().registerTemplate(ChangeAddressEmailTemplate.class);
    }

    public UserController()
    {
        setActionResolver(_actionResolver);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig ret = super.defaultPageConfig();
        ret.setFrameOption(PageConfig.FrameOption.DENY);
        return ret;
    }

    public static class UserUrlsImpl implements UserUrls
    {
        @Override
        public ActionURL getSiteUsersURL()
        {
            return new ActionURL(ShowUsersAction.class, ContainerManager.getRoot());
            // TODO: Always add lastFilter?
        }

        @Override
        public ActionURL getProjectUsersURL(Container container)
        {
            return new ActionURL(ShowUsersAction.class, container);
        }

        @Override
        public ActionURL getUserAccessURL(Container container, int userId)
        {
            ActionURL url = getUserAccessURL(container);
            url.addParameter("userId", userId);
            return url;
        }

        public ActionURL getUserAccessURL(Container container)
        {
            return new ActionURL(UserAccessAction.class, container);
        }

        @Override
        public ActionURL getUserDetailsURL(Container c, int userId, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DetailsAction.class, c);
            url.addParameter("userId", userId);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public ActionURL getUserDetailsURL(Container c, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DetailsAction.class, c);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public ActionURL getUserUpdateURL(Container c, URLHelper returnURL, int userId)
        {
            ActionURL url = new ActionURL(ShowUpdateAction.class, c);
            url.addParameter("userId", userId);
            url.addParameter(QueryParam.schemaName.toString(), "core");
            url.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, CoreQuerySchema.USERS_TABLE_NAME);
            url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public boolean requiresProfileUpdate(User user)
        {
            return CoreQuerySchema.requiresProfileUpdate(user);
        }
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Configuration, "change user properties", new ActionURL(ShowUserPreferencesAction.class, ContainerManager.getRoot()));
    }

    private void setDataRegionButtons(DataRegion rgn, boolean isOwnRecord)
    {
        final User user = getUser();
        Container c = getContainer();
        ActionURL currentURL = getViewContext().getActionURL();
        boolean isSiteAdmin = user.isSiteAdmin();
        boolean isAnyAdmin = isSiteAdmin || c.hasPermission(user, AdminPermission.class);

        assert isOwnRecord || isAnyAdmin;

        SimpleDisplayColumn accountDetails = new UrlColumn(new UserUrlsImpl().getUserDetailsURL(c, currentURL) + "userId=${UserId}", "details");
        accountDetails.setDisplayModes(DataRegion.MODE_GRID);
        rgn.addDisplayColumn(0, accountDetails);

        if (isAnyAdmin)
        {
            SimpleDisplayColumn securityDetails = new UrlColumn(new UserUrlsImpl().getUserAccessURL(c) + "userId=${UserId}", "permissions");
            securityDetails.setDisplayModes(DataRegion.MODE_GRID);
            rgn.addDisplayColumn(1, securityDetails);
        }

        ButtonBar gridButtonBar = new ButtonBar();

        if (isSiteAdmin)
        {
            rgn.setShowRecordSelectors(true);
        }

        populateUserGridButtonBar(gridButtonBar, isSiteAdmin, isAnyAdmin);
        rgn.setButtonBar(gridButtonBar, DataRegion.MODE_GRID);

        ActionURL showUsersURL = new ActionURL(ShowUsersAction.class, c);
        showUsersURL.addParameter(DataRegion.LAST_FILTER_PARAM, true);
        ActionButton showGrid = new ActionButton(showUsersURL, c.isRoot() ? "Show Users" : "Show Project Users");
        showGrid.setActionType(ActionButton.Action.LINK);

        ButtonBar detailsButtonBar = new ButtonBar();
        if (isAnyAdmin)
            detailsButtonBar.add(showGrid);

        ActionURL editURL = new ActionURL(ShowUpdateAction.class, getContainer());
        editURL.addParameter(QueryParam.schemaName.toString(), "core");
        editURL.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, CoreQuerySchema.SITE_USERS_TABLE_NAME);
        editURL.addParameter("userId", NumberUtils.toInt(currentURL.getParameter("userId")));
        editURL.addReturnURL(currentURL);

        if (isOwnRecord || isSiteAdmin)
        {
            ActionButton edit = new ActionButton(editURL, "Edit");
            edit.setActionType(ActionButton.Action.LINK);
            edit.addContextualRole(OwnerRole.class);
            detailsButtonBar.add(edit);
        }
        rgn.setButtonBar(detailsButtonBar, DataRegion.MODE_DETAILS);

        ButtonBar updateButtonBar = new ButtonBar();
        updateButtonBar.setStyle(ButtonBar.Style.separateButtons);
        ActionButton update = new ActionButton(editURL, "Submit");
        if (isOwnRecord)
        {
            updateButtonBar.addContextualRole(OwnerRole.class);
            update.addContextualRole(OwnerRole.class);
        }
        //update.setActionType(ActionButton.Action.LINK);
        updateButtonBar.add(update);
        if (isSiteAdmin)
            updateButtonBar.add(showGrid);
        rgn.setButtonBar(updateButtonBar, DataRegion.MODE_UPDATE);
    }

    private void populateUserGridButtonBar(ButtonBar gridButtonBar, boolean siteAdmin, boolean isProjectAdminOrBetter)
    {
        if (siteAdmin && getContainer().isRoot())
        {
            ActionButton deactivate = new ActionButton(DeactivateUsersAction.class, "Deactivate");
            deactivate.setRequiresSelection(true);
            deactivate.setActionType(ActionButton.Action.POST);
            gridButtonBar.add(deactivate);

            ActionButton activate = new ActionButton(ActivateUsersAction.class, "Re-Activate");
            activate.setRequiresSelection(true);
            activate.setActionType(ActionButton.Action.POST);
            gridButtonBar.add(activate);

            ActionButton delete = new ActionButton(DeleteUsersAction.class, "Delete");
            delete.setRequiresSelection(true);
            delete.setActionType(ActionButton.Action.POST);
            gridButtonBar.add(delete);

            ActionButton insert = new ActionButton(PageFlowUtil.urlProvider(SecurityUrls.class).getAddUsersURL(), "Add Users");
            insert.setActionType(ActionButton.Action.LINK);
            gridButtonBar.add(insert);

            String domainURI = UsersDomainKind.getDomainURI("core", CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), getUser());
            Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);

            if (domain != null)
            {
                ActionURL url = domain.getDomainKind().urlEditDefinition(domain, getViewContext());
                ActionURL returnURL = getViewContext().getActionURL();
                if (returnURL != null)
                    url.addReturnURL(returnURL);

                ActionButton preferences = new ActionButton(url, "Change User Properties");
                preferences.setActionType(ActionButton.Action.LINK);
                gridButtonBar.add(preferences);
            }
        }

        if (isProjectAdminOrBetter)
        {
            if (AuditLogService.get().isViewable())
            {
                gridButtonBar.add(new ActionButton(ShowUserHistoryAction.class, "History",
                        DataRegion.MODE_ALL, ActionButton.Action.LINK));
            }
        }
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(new UserUrlsImpl().getSiteUsersURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class UserIdForm
    {
        private Integer[] _userId;
        private String _redirUrl;

        public Integer[] getUserId()
        {
            return _userId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setUserId(Integer[] userId)
        {
            _userId = userId;
        }

        // TODO: Switch to ReturnUrlForm and standard param
        public String getRedirUrl()
        {
            return _redirUrl;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRedirUrl(String redirUrl)
        {
            _redirUrl = redirUrl;
        }
    }

    public abstract class BaseActivateUsersAction extends FormViewAction<UserIdForm>
    {
        private boolean _active = true;

        protected BaseActivateUsersAction(boolean active)
        {
            _active = active;
        }

        public void validateCommand(UserIdForm form, Errors errors)
        {
        }

        public ModelAndView getView(UserIdForm form, boolean reshow, BindException errors) throws Exception
        {
            User user = getUser();
            DeactivateUsersBean bean = new DeactivateUsersBean(_active, null == form.getRedirUrl() ? null : new ActionURL(form.getRedirUrl()));
            if (null != form.getUserId())
            {
                for (Integer userId : form.getUserId())
                {
                    if (null != userId && userId != user.getUserId())
                        bean.addUser(UserManager.getUser(userId));
                }
            }
            else
            {
                //try to get a user selection list from the dataregion
                Set<Integer> userIds = DataRegionSelection.getSelectedIntegers(getViewContext(), true);
                if (userIds.isEmpty())
                    throw new RedirectException(new UserUrlsImpl().getSiteUsersURL().getLocalURIString());

                for (Integer id : userIds)
                {
                    if (id != user.getUserId())
                        bean.addUser(UserManager.getUser(id));
                }
            }

            if (bean.getUsers().size() == 0)
                throw new RedirectException(bean.getRedirUrl().getLocalURIString());

            return new JspView<>("/org/labkey/core/user/deactivateUsers.jsp", bean, errors);
        }

        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if (null == form.getUserId())
                return false;

            User curUser = getUser();
            for (Integer userId : form.getUserId())
            {
                if (null != userId && userId != curUser.getUserId())
                    UserManager.setUserActive(curUser, userId, _active);
            }
            return true;
        }

        public ActionURL getSuccessURL(UserIdForm form)
        {
            return null != form.getRedirUrl() ? new ActionURL(form.getRedirUrl())
                    : new UserUrlsImpl().getSiteUsersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
            String title = _active ? "Re-activate Users" : "Deactivate Users";
            return root.addChild(title);
        }
    }

    @RequiresSiteAdmin @CSRF
    public class DeactivateUsersAction extends BaseActivateUsersAction
    {
        public DeactivateUsersAction()
        {
            super(false);
        }
    }

    @RequiresSiteAdmin @CSRF
    public class ActivateUsersAction extends BaseActivateUsersAction
    {
        public ActivateUsersAction()
        {
            super(true);
        }
    }

    @RequiresSiteAdmin @CSRF
    public class DeleteUsersAction extends FormViewAction<UserIdForm>
    {
        public void validateCommand(UserIdForm target, Errors errors)
        {
        }

        public ModelAndView getView(UserIdForm form, boolean reshow, BindException errors) throws Exception
        {
            String siteUsersUrl = new UserUrlsImpl().getSiteUsersURL().getLocalURIString();
            DeleteUsersBean bean = new DeleteUsersBean();
            User user = getUser();

            if (null != form.getUserId())
            {
                for (Integer userId : form.getUserId())
                {
                    if (null != userId && userId != user.getUserId())
                        bean.addUser(UserManager.getUser(userId));
                }
            }
            else
            {
                //try to get a user selection list from the dataregion
                Set<Integer> userIds = DataRegionSelection.getSelectedIntegers(getViewContext(), true);
                if (userIds.isEmpty())
                    throw new RedirectException(siteUsersUrl);

                for (Integer id : userIds)
                {
                    if (id != user.getUserId())
                        bean.addUser(UserManager.getUser(id));
                }
            }

            if (bean.getUsers().size() == 0)
                throw new RedirectException(siteUsersUrl);

            return new JspView<>("/org/labkey/core/user/deleteUsers.jsp", bean, errors);
        }

        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if (null == form.getUserId())
                return false;

            User curUser = getUser();

            for (Integer userId : form.getUserId())
            {
                if (null != userId && userId != curUser.getUserId())
                    UserManager.deleteUser(userId);
            }
            return true;
        }

        public ActionURL getSuccessURL(UserIdForm userIdForm)
        {
            return new UserUrlsImpl().getSiteUsersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
            return root.addChild("Delete Users");
        }
    }

    public static class ShowUsersForm extends QueryViewAction.QueryExportForm
    {
        private boolean _inactive;

        public boolean isInactive()
        {
            return _inactive;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setInactive(boolean inactive)
        {
            _inactive = inactive;
        }
    }

    @RequiresPermission(AdminPermission.class)   // Root requires site admin; any other container requires PERM_ADMIN (see below)
    public class ShowUsersAction extends QueryViewAction<ShowUsersForm, QueryView>
    {
        private static final String DATA_REGION_NAME = "Users";

        public ShowUsersAction()
        {
            super(ShowUsersForm.class);
        }

        protected QueryView createQueryView(final ShowUsersForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), SchemaKey.fromParts(CoreQuerySchema.NAME));
            QuerySettings settings = schema.getSettings(getViewContext(), DATA_REGION_NAME, getContainer().isRoot() ? CoreQuerySchema.SITE_USERS_TABLE_NAME : CoreQuerySchema.USERS_TABLE_NAME);
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn("email");

            if (!form.isInactive())
            {
                //filter out inactive users by default
                settings.getBaseFilter().addAllClauses(new SimpleFilter(FieldKey.fromString("Active"), true));
            }

            final boolean isSiteAdmin = getUser().isSiteAdmin();
            final boolean isProjectAdminOrBetter = isSiteAdmin || isProjectAdmin();

            QueryView queryView = new QueryView(schema, settings, errors)
            {
                @Override
                protected void setupDataView(DataView ret)
                {
                    super.setupDataView(ret);

                    if (!forExport && isProjectAdminOrBetter)
                    {
                        ActionURL permissions = new UserUrlsImpl().getUserAccessURL(getContainer());
                        permissions.addParameter("userId", "${UserId}");
                        SimpleDisplayColumn securityDetails = new UrlColumn(StringExpressionFactory.createURL(permissions), "permissions");
                        ret.getDataRegion().addDisplayColumn(1, securityDetails);
                    }
                }

                @Override
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    super.populateButtonBar(view, bar);
                    populateUserGridButtonBar(bar, isSiteAdmin, isProjectAdminOrBetter);
                }
            };
            queryView.setUseQueryViewActionExportURLs(true);
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            queryView.setShowDetailsColumn(true);
            queryView.setFrame(WebPartView.FrameType.NONE);
            queryView.disableContainerFilterSelection();
            queryView.setShowUpdateColumn(false);
            queryView.setShowInsertNewButton(false);
            queryView.setShowImportDataButton(false);
            queryView.setShowDeleteButton(false);
            return queryView;
        }

        @Override
        protected ModelAndView getHtmlView(ShowUsersForm form, BindException errors) throws Exception
        {
            VBox users = new VBox();
            users.setTitle("Users");
            users.setFrame(WebPartView.FrameType.PORTAL);

            JspView<ShowUsersForm> toggleInactiveView = new JspView<>("/org/labkey/core/user/toggleInactive.jsp", form);

            users.addView(toggleInactiveView);
            users.addView(createQueryView(form, errors, false, "Users"));

            return users;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (getContainer().isRoot())
            {
                setHelpTopic(new HelpTopic("manageUsers"));
                return root.addChild("Site Users");
            }
            else
            {
                setHelpTopic(new HelpTopic("manageProjectMembers"));
                return root.addChild("Project Users");
            }
        }
    }

    // Site admins can act on any user
    // Project admins can only act on users who are project users
    private void authorizeUserAction(Integer targetUserId, String action, boolean allowFolderAdmins) throws UnauthorizedException
    {
        User user = getUser();

        // Site admin can do anything
        if (user.isSiteAdmin())
            return;

        Container c = getContainer();

        if (c.isRoot())
        {
            // Only site admin can view at the root (all users)
            throw new UnauthorizedException();
        }
        else
        {
            if (!allowFolderAdmins)
                requiresProjectOrSiteAdmin();

            // ...and user must be a project user
            if (!SecurityManager.getProjectUsersIds(c.getProject()).contains(targetUserId))
                throw new UnauthorizedException("You can only " + action + " project users");
        }
    }


    private void requiresProjectOrSiteAdmin() throws UnauthorizedException
    {
        User user = getUser();

        if (!(user.isSiteAdmin() || isProjectAdmin(user)))
            throw new UnauthorizedException();
    }


    private boolean isProjectAdmin()
    {
        return isProjectAdmin(getUser());
    }


    private boolean isProjectAdmin(User user)
    {
        Container project = getContainer().getProject();
        return (null != project && project.hasPermission(user, AdminPermission.class));
    }


    @RequiresPermission(AdminPermission.class)
    public class ShowUserHistoryAction extends SimpleViewAction
    {
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            super.checkPermissions();

            requiresProjectOrSiteAdmin();
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
            if (schema != null)
            {
                QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                SimpleFilter projectMemberFilter = UsersTable.authorizeAndGetProjectMemberFilter(getContainer(), getUser(), UserAuditProvider.COLUMN_NAME_USER);

                settings.setBaseFilter(projectMemberFilter);
                settings.setQueryName(UserManager.USER_AUDIT_EVENT);
                return schema.createView(getViewContext(), settings, errors);
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (getContainer().isRoot())
            {
                root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
                return root.addChild("Site Users History");
            }
            else
            {
                root.addChild("Project Users", new UserUrlsImpl().getProjectUsersURL(getContainer()));
                return root.addChild("Project Users History");
            }
        }
    }

    @RequiresSiteAdmin
    public class ShowUserPreferencesAction extends RedirectAction<Object>
    {
        ActionURL _successUrl;

        @Override
        public URLHelper getSuccessURL(Object form)
        {
            return _successUrl;
        }

        @Override
        public boolean doAction(Object form, BindException errors) throws Exception
        {
            String domainURI = UsersDomainKind.getDomainURI("core", CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), getUser());
            Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);

            if (domain != null)
            {
                _successUrl = domain.getDomainKind().urlEditDefinition(domain, getViewContext());
                _successUrl.addReturnURL(getViewContext().getActionURL());

                return true;
            }
            return false;
        }

        @Override
        public void validateCommand(Object form, Errors errors)
        {
        }
    }

    @RequiresLogin @CSRF
    public class ShowUpdateAction extends UserSchemaAction
    {
        Integer _userId;
        Integer _pkVal;

        public ModelAndView getView(QueryUpdateForm form, boolean reshow, BindException errors) throws Exception
        {
            User user = getUser();
            _userId = user.getUserId();
            if (null == form.getPkVal())
                form.setPkVal(_userId);

            _pkVal = NumberUtils.toInt(form.getPkVal().toString());
            boolean isOwnRecord = _pkVal.equals(_userId);
            HttpView view;

            if (user.isSiteAdmin() || isOwnRecord)
            {
                ButtonBar bb = createSubmitCancelButtonBar(form);
                bb.addContextualRole(OwnerRole.class);
                for (DisplayElement button : bb.getList())
                    button.addContextualRole(OwnerRole.class);
                view = new UpdateView(form, errors);

                DataRegion rgn = ((UpdateView)view).getDataRegion();
                rgn.setButtonBar(bb);

                TableInfo table = ((UpdateView) view).getTable();
                if (table instanceof UsersTable)
                    ((UsersTable)table).setMustCheckPermissions(false);
/*
                view.getViewContext().addContextualRole(ReadPermission.class);
                view.getViewContext().addContextualRole(UpdatePermission.class);
*/
            }
            else
            {
                throw new UnauthorizedException();
            }

            if (isOwnRecord)
                view =  new VBox(new HtmlView("Please enter your contact information."), view);

            return view;
        }

        @Override
        protected QueryForm createQueryForm(ViewContext context)
        {
            QueryForm form = new UserQueryForm();

            form.setViewContext(context);
            form.bindParameters(context.getBindPropertyValues());

            return form;
        }

        @Override
        public void validateCommand(QueryUpdateForm form, Errors errors)
        {
            TableInfo table = form.getTable();

            if (table instanceof UsersTable)
            {
                for (Map.Entry<String, Object> entry : form.getTypedColumns().entrySet())
                {
                    if (entry.getValue() != null)
                    {
                        ColumnInfo col = table.getColumn(FieldKey.fromParts(entry.getKey()));
                        try
                        {
                            ColumnValidators.validate(col, null, 1, entry.getValue());
                        }
                        catch (ValidationException e)
                        {
                            errors.reject(ERROR_MSG, e.getMessage());
                        }
                    }
                }
            }

            String userId = form.getPkVal().toString();
            if (userId == null)
            {
                errors.reject(SpringActionController.ERROR_MSG, "User Id cannot be null");
                return;
            }

            User user = UserManager.getUser(NumberUtils.toInt(userId));
            if (null == user)
                throw new NotFoundException("User not found :" + userId);
            String userEmailAddress = user.getEmail();
            String displayName = (String)form.getTypedColumns().get("DisplayName");

            if (displayName != null)
            {
                if (displayName.contains("@"))
                {
                    if (!displayName.equalsIgnoreCase(userEmailAddress))
                        errors.reject(SpringActionController.ERROR_MSG, "The value of the 'Display Name' should not contain '@'.");
                }

                //ensure that display name is unique
                User existingUser = UserManager.getUserByDisplayName(displayName);
                //if there's a user with this display name and it's not the user currently being edited
                if (existingUser != null && !existingUser.equals(user))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "The value of the 'Display Name' field conflicts with another value in the database. Please enter a different value");
                }
            }
        }

        public boolean handlePost(QueryUpdateForm form, BindException errors) throws Exception
        {
            User user = getUser();
            _userId = user.getUserId();
            boolean isOwnRecord = NumberUtils.toInt(form.getPkVal().toString()) == _userId;

            if (user.isSiteAdmin() || isOwnRecord)
            {
                TableInfo table = form.getTable();
                if (table instanceof UsersTable)
                    ((UsersTable)table).setMustCheckPermissions(false);
                doInsertUpdate(form, errors, false);
            }
            return 0 == errors.getErrorCount();
        }

        @Override
        public ActionURL getSuccessURL(QueryUpdateForm form)
        {
            return form.getReturnActionURL(PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(getContainer(), NumberUtils.toInt(form.getPkVal().toString()), null));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(root, _pkVal);
            root.addChild("Update");
            return root.addChild(UserManager.getEmailForId(_pkVal));
        }
    }

    private static class UserQueryForm extends QueryForm
    {
        private int _userId;

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }

        @Override
        public UserSchema getSchema()
        {
            int userId = getUserId();
            boolean checkPermission = mustCheckPermissions(getUser(), userId);

            return new CoreQuerySchema(getViewContext().getUser(), getViewContext().getContainer(), checkPermission);
        }

        private boolean mustCheckPermissions(User user, int userRecordId)
        {
            if (user.isSiteAdmin())
                return false;

            return user.getUserId() != userRecordId;
        }
    }

    public static class AccessDetail
    {
        private List<AccessDetailRow> _rows;
        private boolean _showGroups;
        private boolean _showUserCol;
        private boolean _active = true;

        public AccessDetail(List<AccessDetailRow> rows)
        {
            this(rows, true);
        }
        public AccessDetail(List<AccessDetailRow> rows, boolean showGroups)
        {
            this(rows, showGroups, false);
        }
        public AccessDetail(List<AccessDetailRow> rows, boolean showGroups, boolean showUserCol)
        {
            _rows = rows;
            _showGroups = showGroups;
            _showUserCol = showUserCol;
        }

        public List<AccessDetailRow> getRows()
        {
            return _rows;
        }
        public boolean showGroups()
        {
            return _showGroups;
        }
        
        public boolean showUserCol()
        {
            return _showUserCol;
        }

        public boolean isActive()
        {
            return _active;
        }

        public void setActive(boolean active)
        {
            _active = active;
        }
    }

    public static class AccessDetailRow implements Comparable<AccessDetailRow>
    {
        private ViewContext _viewContext;
        private Container _container;
        private UserPrincipal _userPrincipal;
        private Map<String, List<Group>> _accessGroups;
        private int _depth;

        public AccessDetailRow(ViewContext viewContext, Container container, UserPrincipal userPrincipal, List<Role> roles, int depth)
        {
            _viewContext = viewContext;
            _container = container;
            _userPrincipal = userPrincipal;
            _depth = depth;

            Map<String, List<Group>> accessGroups = new TreeMap<>();
            for (Role role : roles)
                accessGroups.put(role.getName(), new ArrayList<Group>());
            _accessGroups = accessGroups;

        }
        public AccessDetailRow(ViewContext viewContext,Container container, UserPrincipal userPrincipal, Map<String, List<Group>> accessGroups, int depth)
        {
            _viewContext = viewContext;
            _container = container;
            _userPrincipal = userPrincipal;
            _accessGroups = accessGroups;
            _depth = depth;
        }

        public String getAccess()
        {
            if (null == _accessGroups || _accessGroups.size() == 0)
                return "";

            String sep = "";
            StringBuilder access = new StringBuilder();
            for (String roleName : _accessGroups.keySet())
            {
                access.append(sep);
                access.append(roleName);
                sep = ", ";
            }
            return access.toString();
        }

        public Container getContainer()
        {
            return _container;
        }

        public UserPrincipal getUser()
        {
            return _userPrincipal;
        }

        public int getDepth()
        {
            return _depth;
        }

        public List<Group> getGroups()
        {
            if (null == _accessGroups || _accessGroups.size() == 0)
                return Collections.emptyList();

            List<Group> allGroups = new ArrayList<>();
            for (List<Group> groups : _accessGroups.values())
            {
                allGroups.addAll(groups);
            }
            return allGroups;
        }

        public Map<String, List<Group>> getAccessGroups()
        {
            return _accessGroups;
        }

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public boolean isInheritedAcl()
        {
            return _container.isInheritedAcl();
        }

        @Override
        public int compareTo(AccessDetailRow o)
        {
            // if both UserPrincipals are Users, compare based on the DisplayName
            User thisUser = UserManager.getUser(this.getUser().getUserId());
            User thatUser = UserManager.getUser(o.getUser().getUserId());
            if (null != thisUser && null != thatUser)
                return thisUser.getDisplayName(getViewContext().getUser()).compareTo(thatUser.getDisplayName(getViewContext().getUser()));
            else
                return this.getUser().getName().compareTo(o.getUser().getName());
        }
    }

    private void buildAccessDetailList(MultiMap<Container, Container> containerTree, Container parent,
                                       List<AccessDetailRow> rows, Set<Container> containersInList, User requestedUser,
                                       int depth, Map<Container, List<Group>> projectGroupCache, boolean showAll)
    {
        if (requestedUser == null)
            return;
        Collection<Container> children = containerTree.get(parent);
        if (children == null || children.isEmpty())
            return;

        for (Container child : children)
        {
            // Skip workbooks as they don't have directly assigned permissions (they inherit from their parent)
            if (child.isWorkbook())
                continue;

            Map<String, List<Group>> childAccessGroups = new TreeMap<>();

            SecurityPolicy policy = SecurityPolicyManager.getPolicy(child);
            Set<Role> effectiveRoles = policy.getEffectiveRoles(requestedUser);
            effectiveRoles.remove(RoleManager.getRole(NoPermissionsRole.class)); //ignore no perms
            for (Role role : effectiveRoles)
            {
                childAccessGroups.put(role.getName(), new ArrayList<Group>());
            }

            if (effectiveRoles.size() > 0)
            {
                Container project = child.getProject();
                List<Group> groups = projectGroupCache.get(project);
                if (groups == null)
                {
                    groups = SecurityManager.getGroups(project, true);
                    projectGroupCache.put(project, groups);
                }
                for (Group group : groups)
                {
                    if (requestedUser.isInGroup(group.getUserId()))
                    {
                        Collection<Role> groupRoles = policy.getAssignedRoles(group);
                        for (Role role : effectiveRoles)
                        {
                            if (groupRoles.contains(role))
                                childAccessGroups.get(role.getName()).add(group);
                        }
                    }
                }
            }

            if (showAll || effectiveRoles.size() > 0)
            {
                int index = rows.size();
                rows.add(new AccessDetailRow(getViewContext(), child, requestedUser, childAccessGroups, depth));
                containersInList.add(child);

                //Ensure parents of any accessible folder are in the tree. If not add them with no access info
                int newDepth = depth;
                while (parent != null && !parent.isRoot() && !containersInList.contains(parent))
                {
                    rows.add(index, new AccessDetailRow(getViewContext(), parent, requestedUser, Collections.<String, List<Group>>emptyMap(), --newDepth));
                    containersInList.add(parent);
                    parent = parent.getParent();
                }
            }

            buildAccessDetailList(containerTree, child, rows, containersInList, requestedUser, depth + 1, projectGroupCache, showAll);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class UserAccessAction extends QueryViewAction<UserAccessForm, QueryView>
    {
        private boolean _showNavTrail;
        private Integer _userId;

        public UserAccessAction()
        {
            super(UserAccessForm.class);
        }

        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            super.checkPermissions();

            // Folder admins can't view permissions, #13465
            requiresProjectOrSiteAdmin();
        }

        @Override
        protected ModelAndView getHtmlView(UserAccessForm form, BindException errors) throws Exception
        {
            String email = form.getNewEmail();

            if (email != null)
            {
                try
                {
                    User user = UserManager.getUser(new ValidEmail(email));
                    if (user != null)
                    {
                        _userId = user.getUserId();
                    }
                    else
                    {
                        throw new NotFoundException();
                    }
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    throw new NotFoundException();
                }
            }

            if (_userId == null)
            {
                _userId = form.getUserId();
            }

            User requestedUser = UserManager.getUser(_userId);

            if (requestedUser == null)
                throw new NotFoundException("User not found");

            VBox view = new VBox();
            SecurityController.FolderAccessForm accessForm = new SecurityController.FolderAccessForm();
            accessForm.setShowAll(form.getShowAll());
            accessForm.setShowCaption("show all folders");
            accessForm.setHideCaption("hide unassigned folders");
            view.addView(new JspView<>("/org/labkey/core/user/toggleShowAll.jsp", accessForm));

            List<AccessDetailRow> rows = new ArrayList<>();
            Set<Container> containersInList = new HashSet<>();
            Container c = getContainer();
            MultiMap<Container, Container> containerTree =  c.isRoot() ? ContainerManager.getContainerTree() : ContainerManager.getContainerTree(c.getProject());
            Map<Container, List<Group>> projectGroupCache = new HashMap<>();
            buildAccessDetailList(containerTree, c.isRoot() ? ContainerManager.getRoot() : null, rows, containersInList, requestedUser, 0, projectGroupCache, form.getShowAll());
            AccessDetail details = new AccessDetail(rows);
            details.setActive(requestedUser.isActive());
            JspView<AccessDetail> accessView = new JspView<>("/org/labkey/core/user/userAccess.jsp", details);
            view.addView(accessView);
            view.addView(createInitializedQueryView(form, errors, false, QueryView.DATAREGIONNAME_DEFAULT));

            if (form.getRenderInHomeTemplate())
            {
                _showNavTrail = true;
                return view;
            }
            else
            {
                return new PrintTemplate(view);
            }
        }

        @Override
        protected QueryView createQueryView(UserAccessForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            if (getContainer().isRoot())
            {
                return GroupAuditProvider.createSiteUserView(getViewContext(), form.getUserId(), errors);
            }
            else
            {
                return GroupAuditProvider.createProjectMemberView(getViewContext(), form.getUserId(), getContainer().getProject(), errors);
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_showNavTrail)
            {
                addUserDetailsNavTrail(root, _userId);
                root.addChild("Permissions");
                return root.addChild("User Access Details: " + UserManager.getEmailForId(_userId));
            }
            return null;
        }
    }


    private void addUserDetailsNavTrail(NavTree root, Integer userId)
    {
        Container c = getContainer();
        if (c.isRoot())
        {
            if (getUser().isSiteAdmin())
                root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
        }
        else
        {
            if (c.hasPermission(getUser(), AdminPermission.class))
                root.addChild("Project Users", new UserUrlsImpl().getProjectUsersURL(c));
        }

        if (null == userId)
            root.addChild("User Details");
        else
            root.addChild("User Details", new UserUrlsImpl().getUserDetailsURL(c, getViewContext().getActionURL()).addParameter("userId", userId));
    }


    @RequiresLogin
    public class DetailsAction extends SimpleViewAction<UserQueryForm>
    {
        private int _detailsUserId;

        public ModelAndView getView(UserQueryForm form, BindException errors) throws Exception
        {
            User user = getUser();
            int userId = user.getUserId();
            _detailsUserId = form.getUserId();
            User detailsUser = UserManager.getUser(_detailsUserId);

            boolean isOwnRecord = (_detailsUserId == userId);

            // Anyone can view their own record; otherwise, make sure current user can view the details of this user
            if (!isOwnRecord)
                authorizeUserAction(_detailsUserId, "view details of", true);

            if (null == detailsUser || detailsUser.isGuest())
                throw new NotFoundException("User does not exist");

            Container c = getContainer();
            boolean isSiteAdmin = user.isSiteAdmin();
            boolean isProjectAdminOrBetter = isSiteAdmin || isProjectAdmin();

            ValidEmail detailsEmail = null;
            boolean loginExists = false;

            try
            {
                detailsEmail = new ValidEmail(detailsUser.getEmail());
                loginExists = SecurityManager.loginExists(detailsEmail);
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                // Allow display and edit of users with invalid email addresses so they can be fixed, #12276.
            }

            UserSchema schema = form.getSchema();
            if (schema == null)
                throw new NotFoundException(CoreQuerySchema.NAME + " schema");

            TableInfo table = schema.getTable(CoreQuerySchema.SITE_USERS_TABLE_NAME);
            if (table == null)
                throw new NotFoundException(CoreQuerySchema.SITE_USERS_TABLE_NAME + " table");

            QueryUpdateForm quf = new QueryUpdateForm(table, getViewContext());
            DetailsView detailsView = new DetailsView(quf);
            DataRegion rgn = detailsView.getDataRegion();

            setDataRegionButtons(rgn, isOwnRecord);
            ButtonBar bb = rgn.getButtonBar(DataRegion.MODE_DETAILS);
            bb.setStyle(ButtonBar.Style.separateButtons);

            if (isOwnRecord && loginExists)
            {
                ActionButton changePasswordButton = new ActionButton(PageFlowUtil.urlProvider(LoginUrls.class).getChangePasswordURL(c, user, getViewContext().getActionURL(), null), "Change Password");
                changePasswordButton.setActionType(ActionButton.Action.LINK);
                changePasswordButton.addContextualRole(OwnerRole.class);
                bb.add(changePasswordButton);
            }

            if (isSiteAdmin)
            {
                // Always display "Reset/Create Password" button (even for LDAP and OpenSSO users)... except for admin's own record.
                if (!isOwnRecord && null != detailsEmail)
                {
                    // Allow admins to create a logins entry if it doesn't exist.  Addresses scenario of user logging
                    // in with SSO and later needing to use database authentication.  Also allows site admin to have
                    // an alternate login, in case LDAP server goes down (this happened recently on one of our
                    // production installations).
                    ActionURL resetURL = new ActionURL(SecurityController.AdminResetPasswordAction.class, c);
                    resetURL.addParameter("email", detailsEmail.getEmailAddress());
                    resetURL.addReturnURL(getViewContext().getActionURL());
                    ActionButton reset = new ActionButton(resetURL, loginExists ? "Reset Password" : "Create Password");
                    reset.setActionType(ActionButton.Action.LINK);

                    String message;

                    if (loginExists)
                        message = "You are about to clear the user's current password, send the user a reset password email, and force the user to pick a new password to access the site.";
                    else
                        message = "You are about to send the user a reset password email, letting the user pick a password to access the site.";

                    reset.setScript("return confirm(" + PageFlowUtil.jsString(message) + ");", true);

                    bb.add(reset);
                }

                bb.add(makeChangeEmailButton(c, detailsUser));

                if (!isOwnRecord)
                {
                    ActionURL deactivateUrl = new ActionURL(detailsUser.isActive() ? DeactivateUsersAction.class : ActivateUsersAction.class, c);
                    deactivateUrl.addParameter("userId", _detailsUserId);
                    deactivateUrl.addParameter("redirUrl", getViewContext().getActionURL().getLocalURIString());
                    bb.add(new ActionButton(detailsUser.isActive() ? "Deactivate" : "Re-Activate", deactivateUrl));

                    ActionURL deleteUrl = new ActionURL(DeleteUsersAction.class, c);
                    deleteUrl.addParameter("userId", _detailsUserId);
                    bb.add(new ActionButton("Delete", deleteUrl));
                }
            }

            if (isProjectAdminOrBetter)
            {
                ActionURL viewPermissionsURL = new UserUrlsImpl().getUserAccessURL(c, _detailsUserId);
                ActionButton viewPermissions = new ActionButton(viewPermissionsURL, "View Permissions");
                viewPermissions.setActionType(ActionButton.Action.LINK);
                bb.add(viewPermissions);
            }

            if (isOwnRecord)
            {
                if (!isSiteAdmin  // site admin already had this link added above
                        && loginExists  // only show link to users where LabKey manages the password
                        && AuthenticationManager.isSelfServiceEmailChangesEnabled())
                {
                    bb.add(makeChangeEmailButton(c, detailsUser));
                }

                ActionButton doneButton;
                if (null != form.getReturnUrl())
                {
                    doneButton = new ActionButton("Done", form.getReturnURLHelper());
                    rgn.addHiddenFormField(ActionURL.Param.returnUrl, form.getReturnUrl());
                }
                else
                {
                    Container doneContainer = c.getProject();

                    // Root or no permission means redirect to home, #12947
                    if (null == doneContainer || !doneContainer.hasPermission(user, ReadPermission.class))
                        doneContainer = ContainerManager.getHomeContainer();

                    ActionURL doneURL = doneContainer.getStartURL(user);
                    doneButton = new ActionButton(doneURL, "Go to " + doneContainer.getName());
                    doneButton.setActionType(ActionButton.Action.LINK);
                }

                doneButton.addContextualRole(OwnerRole.class);
                bb.add(doneButton);
                bb.addContextualRole(OwnerRole.class);
            }

            VBox view = new VBox(detailsView);

            if (isProjectAdminOrBetter)
            {
                UserSchema auditLogSchema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
                if (auditLogSchema != null)
                {
                    QuerySettings settings = new QuerySettings(getViewContext(), "auditHistory");
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(UserAuditProvider.COLUMN_NAME_USER), _detailsUserId);
                    if (getContainer().isRoot())
                        settings.setContainerFilterName(ContainerFilter.Type.AllFolders.name());


                    List<FieldKey> columns = new ArrayList<>();

                    columns.add(FieldKey.fromParts(UserAuditProvider.COLUMN_NAME_CREATED));
                    columns.add(FieldKey.fromParts(UserAuditProvider.COLUMN_NAME_CREATED_BY));
                    columns.add(FieldKey.fromParts(UserAuditProvider.COLUMN_NAME_COMMENT));

                    settings.setFieldKeys(columns);
                    settings.setBaseFilter(filter);
                    settings.setQueryName(UserManager.USER_AUDIT_EVENT);

                    QueryView auditView = auditLogSchema.createView(getViewContext(), settings, errors);
                    auditView.setTitle("History:");

                    view.addView(auditView);
                }
            }

            return view;
        }

        private ActionButton makeChangeEmailButton(Container c, User user)
        {
            ActionURL changeEmailURL = getChangeEmailAction(c, user);
            changeEmailURL.addParameter("isChangeEmailRequest", true);
            ActionButton changeEmail = new ActionButton(changeEmailURL, "Change Email");
            changeEmail.setActionType(ActionButton.Action.LINK);
            return changeEmail;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(UserManager.getEmailForId(_detailsUserId));
        }
    }

    private static ActionURL getChangeEmailAction(Container c, User user)
    {
        ActionURL url = new ActionURL(ChangeEmailAction.class, c);
        url.addParameter("userId", user.getUserId());

        return url;
    }

    @RequiresLogin @CSRF
    public class ChangeEmailAction extends FormViewAction<UserForm>
    {
        private String _currentEmailFromDatabase;
        private String _requestedEmailFromDatabase;
        private int _urlUserId;
        private boolean _isPasswordPrompt = false;
        private ValidEmail _validRequestedEmail;

        public void validateCommand(UserForm target, Errors errors)
        {
            if(target.getIsChangeEmailRequest())
            {
                String requestedEmail = target.getRequestedEmail();
                String requestedEmailConfirmation = target.getRequestedEmailConfirmation();

                if (!requestedEmail.equals(requestedEmailConfirmation))
                {
                    errors.reject(ERROR_MSG, "The email addresses you have entered do not match. Please verify your email addresses below.");
                }
                else  // email addresses match
                {
                    try
                    {
                        ValidEmail validRequestedEmail = new ValidEmail(requestedEmail);

                        if (UserManager.userExists(validRequestedEmail))
                        {
                            errors.reject(ERROR_MSG, requestedEmail + " already exists in the system. Please choose another email.");
                        }
                    }
                    catch (ValidEmail.InvalidEmailException e)
                    {
                        errors.reject(ERROR_MSG, "Invalid email address.");
                    }
                }
            }
        }

        public ModelAndView getView(UserForm form, boolean reshow, BindException errors) throws Exception
        {
            boolean isSiteAdmin = getUser().isSiteAdmin();
            if(!isSiteAdmin)
            {
                if(!AuthenticationManager.isSelfServiceEmailChangesEnabled())  // uh oh, shouldn't be here
                {
                    throw new UnauthorizedException("User attempted to access self-service email change, but this function is disabled.");
                }
                _urlUserId = getUser().getUserId();
            }
            else  // site admin, could be another user's ID
            {
                _urlUserId = form.getUserId();
            }

            if(form.getIsFromVerifiedLink())
            {
                validateVerification(form, errors);
                if(errors.getErrorCount() == 0)
                {
                    User user = getUser();
                    int userId = user.getUserId();

                    // update email in database
                    UserManager.changeEmail(isSiteAdmin, userId, _currentEmailFromDatabase, _requestedEmailFromDatabase, form.getVerificationToken(), getUser());
                    // post-verification email to old account
                    Container c = getContainer();
                    MimeMessage m = getChangeEmailMessage(_currentEmailFromDatabase, _requestedEmailFromDatabase);
                    MailHelper.send(m, user, c);
                    form.setIsFromVerifiedLink(false);  // will still be true in URL, though, confusingly
                    form.setIsVerified(true);
                    form.setOldEmail(_currentEmailFromDatabase);  // for benefit of the page display, since it has changed
                    form.setRequestedEmail(_requestedEmailFromDatabase);  // for benefit of the page display
                }
            }

            return new JspView<>("/org/labkey/core/user/changeEmail.jsp", form, errors);
        }

        void validateVerification(UserForm target, Errors errors)
        {
            boolean isSiteAdmin = getUser().isSiteAdmin();

            try
            {
                User loggedInUser = getUser();
                String loggedInUserEmail = loggedInUser.getEmail();
                ValidEmail validUserEmail = new ValidEmail(loggedInUser.getEmail());  // use logged-in email throughout

                String verificationToken = target.getVerificationToken();
                UserManager.VerifyEmail verifyEmail = UserManager.getVerifyEmail(validUserEmail);
                String currentEmailFromDatabase = verifyEmail.getEmail();
                String requestedEmailFromDatabase = verifyEmail.getRequestedEmail();

                boolean isVerified = SecurityManager.verify(validUserEmail, verificationToken);
                LoginController.checkVerificationErrors(isVerified, loggedInUser, validUserEmail, verificationToken, errors);

                if(errors.getErrorCount() == 0)  // verified
                {
                    if(!(currentEmailFromDatabase.equals(loggedInUserEmail)))
                    {
                        errors.reject(ERROR_MSG, "The current user is not the same user that initiated this request.  Please log in with the account you used to make " +
                                "this email change request.");
                    }
                    else
                    {
                        Instant verificationTimeoutInstant = verifyEmail.getVerificationTimeout().toInstant();
                        if (Instant.now().isAfter(verificationTimeoutInstant))
                        {
                            if(!isSiteAdmin)  // don't bother auditing admin password link clicks
                            {
                                UserManager.auditEmailTimeout(loggedInUser.getUserId(), validUserEmail.getEmailAddress(), requestedEmailFromDatabase, verificationToken, getUser());
                            }
                            errors.reject(ERROR_MSG, "This verification link has expired.  Please try to change your email address again.");
                        }
                        else
                        {
                            // everything is good, update database emails and return for update
                            _currentEmailFromDatabase = currentEmailFromDatabase;
                            _requestedEmailFromDatabase = requestedEmailFromDatabase;
                            return;
                        }
                    }
                }
                else
                {
                    if ((verificationToken == null) || (verificationToken.length() != SecurityManager.tempPasswordLength))
                    {
                        if(!isSiteAdmin)  // don't bother auditing admin password link clicks
                        {
                            UserManager.auditBadVerificationToken(loggedInUser.getUserId(), validUserEmail.getEmailAddress(), verifyEmail.getRequestedEmail(), verificationToken, loggedInUser);
                        }
                        errors.reject(ERROR_MSG, "Verification was incorrect.  Make sure you've copied the entire link into your browser's address bar.");  // double error, to better explain to user
                    }
                    else if(!(verificationToken.equals(verifyEmail.getVerification())))
                    {
                        if(!isSiteAdmin)  // don't bother auditing admin password link clicks
                        {
                            UserManager.auditBadVerificationToken(loggedInUser.getUserId(), validUserEmail.getEmailAddress(), verifyEmail.getRequestedEmail(), verificationToken, loggedInUser);
                        }
                        errors.reject(ERROR_MSG, "The current user is not the same user that initiated this request.  Please log in with the account you used to make " +
                                "this email change request.");  // double error, to better explain to user
                    }
                    else  // not sure if/how this can happen
                    {
                        errors.reject(ERROR_MSG, "Unknown verification error.  Make sure you've copied the entire link into your browser's address bar.");
                    }
                }
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, "Invalid current email address.");
            }
        }

        public boolean handlePost(UserForm form, BindException errors) throws Exception
        {
            boolean isSiteAdmin = getUser().isSiteAdmin();
            User user;
            int userId;

            if (!isSiteAdmin)
            {
                user = getUser();
                userId = user.getUserId();
            }
            else  // admin, so use form user ID, which may be different from admin's
            {
                userId = form.getUserId();
                user = UserManager.getUser(userId);
            }

            if (!isSiteAdmin)  // need to verify email before changing if not site admin
            {
                if(!AuthenticationManager.isSelfServiceEmailChangesEnabled())  // uh oh, shouldn't be here
                {
                    throw new UnauthorizedException("User attempted to access self-service email change, but this function is disabled.");
                }

                if(form.getIsChangeEmailRequest())
                {
                    // do nothing, validation happened earlier
                }
                else if(form.getIsPasswordPrompt() || _isPasswordPrompt)
                {
                    _isPasswordPrompt = true; // in case we get an error and need to come back to this action again

                    ValidEmail validRequestedEmail;
                    try
                    {
                        if(form.getRequestedEmail() != null)
                        {
                            validRequestedEmail = new ValidEmail(form.getRequestedEmail());  // validate in case user altered this in the URL parameter
                            _validRequestedEmail = validRequestedEmail;
                        }
                        else
                        {
                            validRequestedEmail = _validRequestedEmail;  // try and get it from internal variable, in case we're re-trying here
                        }
                    }
                    catch (ValidEmail.InvalidEmailException e)
                    {
                        errors.reject(ERROR_MSG, "Invalid requested email address.");
                        return false;
                    }

                    String userEmail = user.getEmail();
                    boolean isAuthenticated = authenticate(userEmail, form.getPassword(), getViewContext().getActionURL().getReturnURL(), errors);
                    if (isAuthenticated)
                    {
                        String verificationToken = SecurityManager.createTempPassword();
                        UserManager.requestEmailChange(userId, userEmail, validRequestedEmail.getEmailAddress(), verificationToken, getUser());
                        // verification email
                        Container c = getContainer();

                        ActionURL verifyLinkUrl = getChangeEmailAction(c, user);  // note that user ID added to URL here will only be used if an admin clicks the link, and the link won't work
                        verifyLinkUrl.addParameter("verificationToken", verificationToken);
                        verifyLinkUrl.addParameter("isFromVerifiedLink", true);

                        SecurityManager.sendEmail(c, user, getRequestEmailMessage(userEmail, validRequestedEmail.getEmailAddress()), userEmail, verifyLinkUrl);
                    }
                    else
                    {
                        errors.reject(ERROR_MSG, "Incorrect password.");
                        Container c = getContainer();
                        ActionURL passwordPromptRetryUrl = getChangeEmailAction(c, user);
                        passwordPromptRetryUrl.addParameter("isPasswordPrompt", true);
                        passwordPromptRetryUrl.addParameter("requestedEmail", form.getRequestedEmail());
                        //form.setReturnUrl(passwordPromptRetryUrl.);
                    }
                }
                else  // something strange happened
                {
                    throw new IllegalStateException("Unknown page state after change email POST.");
                }
            }
            else  // site admin, so make change directly
            {
                // use "ADMIN" as verification token for debugging, but should never be checked/used
                UserManager.changeEmail(isSiteAdmin, userId, user.getEmail(), form.getRequestedEmail(), "ADMIN", getUser());
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(UserForm form)
        {
            boolean isSiteAdmin = getUser().isSiteAdmin();

            if(!isSiteAdmin)
            {
                User user = getUser();

                if (form.getIsChangeEmailRequest())
                {
                    Container c = getContainer();
                    ActionURL passwordPromptUrl = getChangeEmailAction(c, user);
                    passwordPromptUrl.addParameter("isPasswordPrompt", true);
                    passwordPromptUrl.addParameter("requestedEmail", form.getRequestedEmail());
                    return passwordPromptUrl;
                }
                else if (form.getIsPasswordPrompt())
                {
                    Container c = getContainer();
                    ActionURL verifyRedirectUrl = getChangeEmailAction(c, user);
                    verifyRedirectUrl.addParameter("isVerifyRedirect", true);
                    return verifyRedirectUrl;
                }
                else  // actually making self-service email change, so redirect to user details page
                {
                    return new UserUrlsImpl().getUserDetailsURL(getContainer(), user.getUserId(), form.getReturnURLHelper());
                }
            }
            else  // admin email change, so redirect to user details page
            {
                return new UserUrlsImpl().getUserDetailsURL(getContainer(), form.getUserId(), form.getReturnURLHelper());
            }
        }

        boolean authenticate(String email, String password, URLHelper returnUrlHelper, BindException errors)
        {
            try
            {
                DbLoginAuthenticationProvider loginProvider = (DbLoginAuthenticationProvider) AuthenticationManager.getProvider("Database");
                return loginProvider.authenticate(email, password, returnUrlHelper).isAuthenticated();
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                String defaultDomain = ValidEmail.getDefaultDomain();
                StringBuilder sb = new StringBuilder();
                sb.append("Please sign in using your full email address, for example: ");
                if (defaultDomain != null && defaultDomain.length() > 0)
                {
                    sb.append("employee@");
                    sb.append(defaultDomain);
                    sb.append(" or ");
                }
                sb.append("employee@domain.com");
                errors.reject(ERROR_MSG, sb.toString());
            }

            return false;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(root, _urlUserId);
            return root.addChild("Change Email Address: " + UserManager.getEmailForId(_urlUserId));
        }
    }

    public static class UserForm extends ReturnUrlForm
    {
        private int _userId;
        private String _password;
        private String _oldEmail;
        private String _requestedEmail;
        private String _requestedEmailConfirmation;
        private String _message = null;
        private boolean _isChangeEmailRequest = false;
        private boolean _isPasswordPrompt = false;
        private boolean _isVerifyRedirect = false;
        private boolean _isFromVerifiedLink = false;
        private boolean _isVerified = false;
        private String _verificationToken = null;

        public int getUserId()
        {
            return _userId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setUserId(int userId)
        {
            _userId = userId;
        }

        public String getPassword()
        {
            return _password;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPassword(String password)
        {
            _password = password;
        }

        public String getOldEmail()
        {
            return _oldEmail;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setOldEmail(String oldEmail)
        {
            _oldEmail = oldEmail;
        }

        public String getRequestedEmail()
        {
            return _requestedEmail;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRequestedEmail(String requestedEmail)
        {
            _requestedEmail = requestedEmail;
        }

        public String getRequestedEmailConfirmation()
        {
            return _requestedEmailConfirmation;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRequestedEmailConfirmation(String requestedEmailConfirmation)
        {
            _requestedEmailConfirmation = requestedEmailConfirmation;
        }

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }

        public boolean getIsChangeEmailRequest()
        {
            return _isChangeEmailRequest;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsChangeEmailRequest(boolean isChangeEmailRequest)
        {
            _isChangeEmailRequest = isChangeEmailRequest;
        }

        public boolean getIsPasswordPrompt()
        {
            return _isPasswordPrompt;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsPasswordPrompt(boolean isPasswordPrompt)
        {
            _isPasswordPrompt = isPasswordPrompt;
        }

        public boolean getIsVerifyRedirect()
        {
            return _isVerifyRedirect;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsVerifyRedirect(boolean isVerifyRedirect)
        {
            _isVerifyRedirect = isVerifyRedirect;
        }

        public boolean getIsFromVerifiedLink()
        {
            return _isFromVerifiedLink;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsFromVerifiedLink(boolean isFromVerifiedLink)
        {
            _isFromVerifiedLink = isFromVerifiedLink;
        }

        public boolean getIsVerified()
        {
            return _isVerified;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIsVerified(boolean isVerified)
        {
            _isVerified = isVerified;
        }

        public String getVerificationToken()
        {
            return _verificationToken;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setVerificationToken(String verificationToken)
        {
            _verificationToken = verificationToken;
        }
    }

    public static class UserAccessForm extends QueryViewAction.QueryExportForm
    {
        private boolean _showAll = false;
        private int _userId;
        private String _newEmail;
        private String _message = null;
        private boolean _renderInHomeTemplate = true;

        public String getNewEmail()
        {
            return _newEmail;
        }

        public void setNewEmail(String newEmail)
        {
            _newEmail = newEmail;
        }

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }

        public boolean getRenderInHomeTemplate()
        {
            return _renderInHomeTemplate;
        }

        public void setRenderInHomeTemplate(boolean renderInHomeTemplate)
        {
            _renderInHomeTemplate = renderInHomeTemplate;
        }

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }

        public boolean getShowAll()
        {
            return _showAll;
        }

        public void setShowAll(boolean showAll)
        {
            _showAll = showAll;
        }
    }

    private static SecurityMessage getRequestEmailMessage(String currentEmailAddress, String requestedEmailAddress) throws Exception
    {
        SecurityMessage sm = new SecurityMessage();
        RequestAddressEmailTemplate et = EmailTemplateService.get().getEmailTemplate(RequestAddressEmailTemplate.class);
        et.setCurrentEmailAddress(currentEmailAddress);
        et.setRequestedEmailAddress(requestedEmailAddress);

        sm.setEmailTemplate(et);
        sm.setType("Request email address");

        return sm;
    }

    public static class RequestAddressEmailTemplate extends SecurityManager.SecurityEmailTemplate
    {
        static final String DEFAULT_SUBJECT =
                "Verification link for ^organizationName^ ^siteShortName^ Web Site email change";
        static final String DEFAULT_BODY =
                "You recently requested a change to the email address associated with your account on the " +
                "^organizationName^ ^siteShortName^ Web Site.  If you did not issue this request, you can ignore this email.\n\n" +
                "To complete the process of changing your account's email address from ^currentEmailAddress^ to ^newEmailAddress^, " +
                "simply click the link below or copy it to your browser's address bar.\n\n" +
                "^verificationURL^\n\n" +
                "Note: The link above should appear on one line, starting with 'http' and ending with 'true'.  Some " +
                "email systems may break this link into multiple lines, causing the verification to fail.  If this happens, " +
                "you'll need to paste the parts into the address bar of your browser to form the full link.\n\n" +
                "This step confirms that you are the owner of the new email account.  After you click the link, you will need " +
                "to use the new email address when logging into the server.";
        String _currentEmailAddress;
        String _requestedEmailAddress;
        List<EmailTemplate.ReplacementParam> _replacements = new ArrayList<>();

        @SuppressWarnings("UnusedDeclaration") // Constructor called via reflection
        public RequestAddressEmailTemplate()
        {
            this("Request email address");
        }

        RequestAddressEmailTemplate(String name)
        {
            super(name);
            setSubject(DEFAULT_SUBJECT);
            setBody(DEFAULT_BODY);
            setDescription("Sent to the user and administrator when a user requests to change their email address.");
            setPriority(1);
            _replacements.add(new ReplacementParam<String>("currentEmailAddress", String.class, "Current email address for the current user"){
                public String getValue(Container c) {return _currentEmailAddress;}
            });
            _replacements.add(new ReplacementParam<String>("newEmailAddress", String.class, "Requested email address for the current user"){
                public String getValue(Container c) {return _requestedEmailAddress;}
            });
            _replacements.addAll(super.getValidReplacements());
        }

        void setCurrentEmailAddress(String currentEmailAddress) { _currentEmailAddress = currentEmailAddress; }
        void setRequestedEmailAddress(String requestedEmailAddress) { _requestedEmailAddress = requestedEmailAddress; }
        @Override
        public List<ReplacementParam> getValidReplacements(){ return _replacements; }
    }

    private MimeMessage getChangeEmailMessage(String oldEmailAddress, String newEmailAddress) throws Exception
    {
        MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();

        ChangeAddressEmailTemplate et = EmailTemplateService.get().getEmailTemplate(ChangeAddressEmailTemplate.class);
        et.setOldEmailAddress(oldEmailAddress);
        et.setNewEmailAddress(newEmailAddress);

        Container c = getContainer();
        m.setTemplate(et, c);
        m.addFrom(new Address[]{et.renderFrom(c, LookAndFeelProperties.getInstance(c).getSystemEmailAddress())});
        m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(oldEmailAddress + ";"));

        return m;
    }

    public static class ChangeAddressEmailTemplate extends UserOriginatedEmailTemplate
    {
        static final String DEFAULT_SUBJECT =
                "Notification that ^organizationName^ ^siteShortName^ Web Site email has changed";
        static final String DEFAULT_BODY =
                "The email address associated with your account on the ^organizationName^ ^siteShortName^ Web Site has been updated, " +
                "from ^oldEmailAddress^ to ^newEmailAddress^.\n\n" +
                "If you did not request this change, please contact the server administrator immediately.  Otherwise, no further action " +
                "is required, and you may use the new email address when logging into the server going forward.";
        String _oldEmailAddress;
        String _newEmailAddress;
        List<EmailTemplate.ReplacementParam> _replacements = new ArrayList<>();

        @SuppressWarnings("UnusedDeclaration") // Constructor called via reflection
        public ChangeAddressEmailTemplate()
        {
            this("Change email address");
        }

        ChangeAddressEmailTemplate(String name)
        {
            super(name);
            setSubject(DEFAULT_SUBJECT);
            setBody(DEFAULT_BODY);
            setDescription("Sent to the user and administrator when a user has changed their email address.");
            setPriority(1);
            _replacements.add(new ReplacementParam<String>("oldEmailAddress", String.class, "Old email address for the current user"){
                public String getValue(Container c) {return _oldEmailAddress;}
            });
            _replacements.add(new ReplacementParam<String>("newEmailAddress", String.class, "New email address for the current user"){
                public String getValue(Container c) {return _newEmailAddress;}
            });
            _replacements.addAll(super.getValidReplacements());
        }

        void setOldEmailAddress(String oldEmailAddress) { _oldEmailAddress = oldEmailAddress; }
        void setNewEmailAddress(String newEmailAddress) { _newEmailAddress = newEmailAddress; }
        @Override
        public List<ReplacementParam> getValidReplacements(){ return _replacements; }
    }

    /**
     * Wrapper to create required columninfos
     */
    private static class RequiredColumn extends ColumnInfo
    {
        public RequiredColumn(ColumnInfo col)
        {
            super(col, col.getParentTable());
        }

        public boolean isNullable()
        {
            return false;
        }
    }

    public static class GetUsersForm
    {
        private String _group;
        private Integer _groupId;
        private String _name;
        private boolean _allMembers;
        private boolean _active;
        private Permission[] _permissions;

        public String getGroup()
        {
            return _group;
        }

        public void setGroup(String group)
        {
            _group = group;
        }

        public Integer getGroupId()
        {
            return _groupId;
        }

        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public boolean isAllMembers()
        {
            return _allMembers;
        }

        public void setAllMembers(boolean allMembers)
        {
            _allMembers = allMembers;
        }

        public Permission[] getPermissions()
        {
            return _permissions;
        }

        public void setPermissions(Permission[] permission)
        {
            _permissions = permission;
        }

        public boolean isActive()
        {
            return _active;
        }

        public void setActive(boolean active)
        {
            _active = active;
        }

    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public class GetUsersAction extends ApiAction<GetUsersForm>
    {
        protected static final String PROP_USER_ID = "userId";
        protected static final String PROP_USER_NAME = "displayName";

        public ApiResponse execute(GetUsersForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            User currentUser = getUser();

            if (container.isRoot() && !currentUser.isSiteAdmin())
                throw new UnauthorizedException("Only site administrators may see users in the root container!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("container", container.getPath());

            Collection<User> users;
            List<Map<String,Object>> userResponseList = new ArrayList<>();

            //if requesting users in a specific group...
            if (null != StringUtils.trimToNull(form.getGroup()) || null != form.getGroupId())
            {
                Container project = container.getProject();

                //get users in given group/role name
                Integer groupId = form.getGroupId();

                if (null == groupId)
                    groupId = SecurityManager.getGroupId(container.getProject(), form.getGroup(), false);

                if (null == groupId)
                    throw new IllegalArgumentException("The group '" + form.getGroup() + "' does not exist in the project '"
                            + project.getPath() + "'");

                Group group = SecurityManager.getGroup(groupId);

                if (null == group)
                    throw new NotFoundException("Cannot find group with id " + groupId);

                response.put("groupId", group.getUserId());
                response.put("groupName", group.getName());
                response.put("groupCaption", SecurityManager.getDisambiguatedGroupName(group));

                MemberType<User> userMemberType;
                if (form.isActive())
                    userMemberType = MemberType.ACTIVE_USERS;
                else
                    userMemberType = MemberType.ACTIVE_AND_INACTIVE_USERS;

                // if the allMembers flag is set, then recurse and if group is users then return all site users
                if (form.isAllMembers())
                    users = SecurityManager.getAllGroupMembers(group, userMemberType, group.isUsers());
                else
                    users = SecurityManager.getGroupMembers(group, userMemberType);
            }
            else
            {
                //special-case: if container is root, return all active users
                //else, return all users in the current project
                //we've already checked above that the current user is a system admin
                if (container.isRoot())
                    users = UserManager.getActiveUsers();
                else
                    users = SecurityManager.getProjectUsers(container, form.isAllMembers());
            }

            if (null != users)
            {
                //trim name filter to empty so we are guaranteed a non-null string
                //and conver to lower-case for the compare below
                String nameFilter = StringUtils.trimToEmpty(form.getName()).toLowerCase();

                if (nameFilter.length() > 0)
                    response.put("name", nameFilter);

                boolean includeEmail = SecurityManager.canSeeEmailAddresses(getContainer(), currentUser);
                boolean userHasPermission;

                for (User user : users)
                {
                    // TODO: consider performance here
                    // if permissions passed, then validate the user has all of such permissions
                    if (form.getPermissions() != null)
                    {
                        userHasPermission = true;
                        for (Permission permission : form.getPermissions())
                        {
                            if (!container.hasPermission(user, permission.getClass()))
                            {
                                userHasPermission = false;
                                break;
                            }
                        }
                        if (!userHasPermission)
                            continue;
                    }

                    //according to the docs, startsWith will return true even if nameFilter is empty string
                    if (user.getEmail().toLowerCase().startsWith(nameFilter) || user.getDisplayName(null).toLowerCase().startsWith(nameFilter))
                    {
                        Map<String,Object> userInfo = new HashMap<>();
                        userInfo.put(PROP_USER_ID, user.getUserId());

                        //force sanitize of the display name, even for logged-in users
                        userInfo.put(PROP_USER_NAME, user.getDisplayName(currentUser));

                        //include email address, if user is allowed to see them
                        if (includeEmail)
                            userInfo.put("email", user.getEmail());

                        userResponseList.add(userInfo);
                    }
                }
            }

            response.put("users", userResponseList);
            return response;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class GetImpersonationUsersAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            User currentUser = getUser();
            Container project = currentUser.isSiteAdmin() ? null : getContainer().getProject();
            Collection<User> users = UserImpersonationContextFactory.getValidImpersonationUsers(project, getUser());

            Collection<Map<String, Object>> responseUsers = new LinkedList<>();

            for (User user : users)
            {
                Map<String, Object> map = new HashMap<>();
                map.put("userId", user.getUserId());
                map.put("displayName", user.getEmail() + " (" + user.getDisplayName(currentUser) + ")");
                responseUsers.add(map);
            }

            response.put("users", responseUsers);

            return response;
        }
    }


    // Need returnUrl because we stash the current URL in session and return to it after impersonation is complete
    public static class ImpersonateUserForm extends ReturnUrlForm
    {
        private Integer _userId = null;

        public Integer getUserId()
        {
            return _userId;
        }

        public void setUserId(Integer userId)
        {
            _userId = userId;
        }
    }


    // All three impersonate API actions have the same form
    private abstract class ImpersonateApiAction<FORM> extends MutatingApiAction<FORM>
    {
        protected String getCommandClassMethodName()
        {
            return "impersonate";
        }

        @Override
        public ApiResponse execute(FORM form, BindException errors) throws Exception
        {
            String error = impersonate(form);

            if (null != error)
            {
                errors.reject(null, error);
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }

        // Non-null return value means send back an error message
        public abstract @Nullable String impersonate(FORM form);
    }


    @RequiresPermission(AdminPermission.class) @CSRF
    public class ImpersonateUserAction extends ImpersonateApiAction<ImpersonateUserForm>
    {
        @Override
        public @Nullable String impersonate(ImpersonateUserForm form)
        {
            if (getUser().isImpersonated())
                return "Can't impersonate; you're already impersonating";

            Integer userId = form.getUserId();

            if (null == userId)
                return "Must specify a user ID";

            User impersonatedUser = UserManager.getUser(userId);

            if (null == impersonatedUser)
                return "User doesn't exist";

            try
            {
                SecurityManager.impersonateUser(getViewContext(), impersonatedUser, form.getReturnURLHelper());
            }
            catch (UnauthorizedImpersonationException uie)
            {
                return uie.getMessage();
            }

            return null;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class GetImpersonationGroupsAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Collection<Group> groups = GroupImpersonationContextFactory.getValidImpersonationGroups(getContainer(), getUser());
            Collection<Map<String, Object>> responseGroups = new LinkedList<>();

            for (Group group : groups)
            {
                Map<String, Object> map = new HashMap<>();
                map.put("groupId", group.getUserId());
                map.put("displayName", (group.isProjectGroup() ? "" : "Site: ") + group.getName());
                responseGroups.add(map);
            }

            response.put("groups", responseGroups);

            return response;
        }
    }


    public static class ImpersonateGroupForm extends ReturnUrlForm
    {
        private Integer _groupId = null;

        public Integer getGroupId()
        {
            return _groupId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }
    }


    // TODO: Better instructions
    // TODO: Messages for no groups, no users
    @RequiresPermission(AdminPermission.class) @CSRF
    public class ImpersonateGroupAction extends ImpersonateApiAction<ImpersonateGroupForm>
    {
        @Override
        public @Nullable String impersonate(ImpersonateGroupForm form)
        {
            if (getUser().isImpersonated())
                return "Can't impersonate; you're already impersonating";

            Integer groupId = form.getGroupId();

            if (null == groupId)
                return "Must specify a group ID";

            Group group = SecurityManager.getGroup(groupId);

            if (null == group)
                return "Group doesn't exist";

            ActionURL returnURL = form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL());

            try
            {
                SecurityManager.impersonateGroup(getViewContext(), group, returnURL);
            }
            catch (UnauthorizedImpersonationException uie)
            {
                return uie.getMessage();
            }

            return null;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class GetImpersonationRolesAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Collection<Role> roles = RoleImpersonationContextFactory.getValidImpersonationRoles(getContainer());
            Collection<Map<String, Object>> responseRoles = new LinkedList<>();

            for (Role role : roles)
            {
                Map<String, Object> map = new HashMap<>();
                map.put("displayName", role.getName());
                map.put("roleName", role.getUniqueName());
                map.put("hasRead", role.getPermissions().contains(ReadPermission.class));
                responseRoles.add(map);
            }

            response.put("roles", responseRoles);

            return response;
        }
    }


    public static class ImpersonateRolesForm extends ReturnUrlForm
    {
        private String[] _roleNames;

        public String[] getRoleNames()
        {
            return _roleNames;
        }

        public void setRoleNames(String[] roleNames)
        {
            _roleNames = roleNames;
        }
    }


    @RequiresPermission(AdminPermission.class) @CSRF
    public class ImpersonateRolesAction extends ImpersonateApiAction<ImpersonateRolesForm>
    {
        @Nullable
        @Override
        public String impersonate(ImpersonateRolesForm form)
        {
            if (getUser().isImpersonated())
                return "Can't impersonate; you're already impersonating";

            String[] roleNames = form.getRoleNames();

            if (ArrayUtils.isEmpty(roleNames))
                return "Must provide roles";

            Collection<Role> roles = new LinkedList<>();

            for (String roleName : roleNames)
            {
                Role role = RoleManager.getRole(roleName);
                if (null == role)
                    return "Role not found: " + roleName;
                roles.add(role);
            }

            ActionURL returnURL = form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL());
            SecurityManager.impersonateRoles(getViewContext(), roles, returnURL);

            return null;
        }
    }


    public static class ShowWarningMessagesForm
    {
        private String _action;
        private boolean _showMessages = true;

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }

        public boolean isShowMessages()
        {
            return _showMessages;
        }

        public void setShowMessages(boolean showMessages)
        {
            _showMessages = showMessages;
        }
    }

    @RequiresNoPermission
    public class SetShowWarningMessagesAction extends ApiAction<ShowWarningMessagesForm>
    {
        public ApiResponse execute(ShowWarningMessagesForm form, BindException errors) throws Exception
        {
            if (form.getAction() != null && !form.getAction().equals("")) // Fix for 13926
                getViewContext().getSession().setAttribute(form.getAction(), form.isShowMessages());
            else
                getViewContext().getSession().setAttribute(TemplateHeaderView.SHOW_WARNING_MESSAGES_SESSION_PROP, form.isShowMessages());
            return new ApiSimpleResponse("success", true);
        }
    }
}
