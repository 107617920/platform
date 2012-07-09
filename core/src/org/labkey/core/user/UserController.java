/*
 * Copyright (c) 2003-2012 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.*;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.impersonation.ImpersonateRoleContextFactory;
import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.*;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.api.view.template.TemplateHeaderView;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.query.UserAuditViewFactory;
import org.labkey.core.security.SecurityController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;
import java.util.*;

public class UserController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(UserController.class);

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
        public ActionURL getSiteUsersURL()
        {
            return new ActionURL(ShowUsersAction.class, ContainerManager.getRoot());
            // TODO: Always add lastFilter?
        }

        public ActionURL getProjectUsersURL(Container container)
        {
            return new ActionURL(ShowUsersAction.class, container);
        }

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

        public ActionURL getUserDetailsURL(Container c, int userId, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DetailsAction.class, c);
            url.addParameter("userId", userId);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        public ActionURL getUserDetailsURL(Container c, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DetailsAction.class, c);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        public ActionURL getUserUpdateURL(URLHelper returnURL, int userId)
        {
            ActionURL url = new ActionURL(ShowUpdateAction.class, ContainerManager.getRoot());
            url.addReturnURL(returnURL);
            url.addParameter("userId", userId);
            return url;
        }

        public ActionURL getImpersonateGroupURL(Container c, int groupId, ActionURL returnURL)
        {
            ActionURL url = new ActionURL(ImpersonateGroupAction.class, c);
            url.addParameter("groupId", groupId);
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getImpersonateRoleURL(Container c, String uniqueRoleName, ActionURL returnURL)
        {
            ActionURL url = new ActionURL(ImpersonateRoleAction.class, c);
            url.addParameter("roleName", uniqueRoleName);
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getImpersonateUserURL(Container c)
        {
            return new ActionURL(ImpersonateUserAction.class, c);
        }
    }


    // Note: the column list is dynamic, changing based on the current user's permissions.
    public static String getUserColumnNames(User user, Container c)
    {
        String columnNames = "Email, DisplayName, FirstName, LastName, Phone, Mobile, Pager, IM, Description";

        if (user != null && (user.isAdministrator() || c.hasPermission(user, AdminPermission.class)))
            columnNames = columnNames + ", UserId, Created, LastLogin, Active";

        return columnNames;
    }

    public static String getDefaultUserColumnNames()
    {
        return getUserColumnNames(null, null);
    }

    private class SiteUserDataRegion extends DataRegion
    {
        protected boolean isRecordSelectorEnabled(RenderContext ctx)
        {
            int userId = ctx.getViewContext().getUser().getUserId();
            Integer rowId = (Integer) ctx.getRow().get("userId");
            return  (userId != rowId);
        }
    }

    private DataRegion getGridRegion(boolean isOwnRecord)
    {
        final User user = getUser();
        Container c = getContainer();
        ActionURL currentURL = getViewContext().getActionURL();
        boolean isSiteAdmin = user.isAdministrator();
        boolean isAnyAdmin = isSiteAdmin || c.hasPermission(user, AdminPermission.class);

        assert isOwnRecord || isAnyAdmin;

        SiteUserDataRegion rgn = new SiteUserDataRegion();

        List<ColumnInfo> cols = CoreSchema.getInstance().getTableInfoUsers().getColumns(getUserColumnNames(user, c));
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>();
        final String requiredFields = UserManager.getRequiredUserFields();

        for (ColumnInfo col : cols)
        {
            if (isColumnRequired(col.getName(), requiredFields))
            {
                final RequiredColumn required = new RequiredColumn(col);
                displayColumns.add(required.getRenderer());
            }
            else
            {
                displayColumns.add(col.getRenderer());
            }
        }

        rgn.setDisplayColumns(displayColumns);
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
        ActionButton showGrid = new ActionButton(showUsersURL, c.isRoot() ? "Show All Users" : "Show Project Users");
        showGrid.setActionType(ActionButton.Action.LINK);

        ButtonBar detailsButtonBar = new ButtonBar();
        if (isAnyAdmin)
            detailsButtonBar.add(showGrid);
        if (isOwnRecord || isSiteAdmin)
        {
            ActionButton edit = new ActionButton(ShowUpdateAction.class, "Edit");
            edit.setActionType(ActionButton.Action.GET);
            edit.addContextualRole(OwnerRole.class);
            detailsButtonBar.add(edit);
        }
        rgn.setButtonBar(detailsButtonBar, DataRegion.MODE_DETAILS);

        ButtonBar updateButtonBar = new ButtonBar();
        updateButtonBar.setStyle(ButtonBar.Style.separateButtons);
        ActionButton update = new ActionButton(ShowUpdateAction.class, "Submit");
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

        return rgn;
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

            ActionButton preferences = new ActionButton(ShowUserPreferencesAction.class, "Preferences");
            preferences.setActionType(ActionButton.Action.LINK);
            gridButtonBar.add(preferences);
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
    @RequiresPermissionClass(ReadPermission.class)
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
                Set<String> userIds = DataRegionSelection.getSelected(getViewContext(), true);
                if (null == userIds || userIds.size() == 0)
                    throw new RedirectException(new UserUrlsImpl().getSiteUsersURL().getLocalURIString());

                for (String userId : userIds)
                {
                    int id = Integer.parseInt(userId);
                    if (id != user.getUserId())
                        bean.addUser(UserManager.getUser(id));
                }
            }

            if (bean.getUsers().size() == 0)
                throw new RedirectException(bean.getRedirUrl().getLocalURIString());

            return new JspView<DeactivateUsersBean>("/org/labkey/core/user/deactivateUsers.jsp", bean, errors);
        }

        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if (null == form.getUserId())
                return false;

            User curUser = getViewContext().getUser();
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
            User user = getViewContext().getUser();

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
                Set<String> userIds = DataRegionSelection.getSelected(getViewContext(), true);
                if (null == userIds || userIds.size() == 0)
                    throw new RedirectException(siteUsersUrl);

                for (String userId : userIds)
                {
                    int id = Integer.parseInt(userId);
                    if (id != user.getUserId())
                        bean.addUser(UserManager.getUser(Integer.parseInt(userId)));
                }
            }

            if (bean.getUsers().size() == 0)
                throw new RedirectException(siteUsersUrl);

            return new JspView<DeleteUsersBean>("/org/labkey/core/user/deleteUsers.jsp", bean, errors);
        }

        public boolean handlePost(UserIdForm form, BindException errors) throws Exception
        {
            if (null == form.getUserId())
                return false;

            User curUser = getViewContext().getUser();

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

    private static boolean validateRequiredColumns(Map<String, Object> resultMap, TableInfo table)
    {
        final String requiredFields = UserManager.getRequiredUserFields();
        if (requiredFields == null || requiredFields.length() == 0)
            return true;

        for (String key : resultMap.keySet())
        {
            final ColumnInfo col = table.getColumn(key);
            if (col != null && isColumnRequired(col.getName(), requiredFields))
            {
                final Object val = resultMap.get(key);
                if (val == null || val.toString().trim().length() == 0)
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isColumnRequired(String column, String requiredColumns)
    {
        return requiredColumns != null && requiredColumns.toLowerCase().indexOf(column.toLowerCase()) != -1;
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

    @RequiresPermissionClass(AdminPermission.class)   // Root requires site admin; any other container requires PERM_ADMIN (see below)
    public class ShowUsersAction extends QueryViewAction<ShowUsersForm, QueryView>
    {
        private static final String DATA_REGION_NAME = "Users";

        public ShowUsersAction()
        {
            super(ShowUsersForm.class);
        }

        protected QueryView createQueryView(final ShowUsersForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            QuerySettings settings = new QuerySettings(getViewContext(), DATA_REGION_NAME, getContainer().isRoot() ? CoreQuerySchema.SITE_USERS_TABLE_NAME : CoreQuerySchema.USERS_TABLE_NAME);
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn("email");
            SimpleFilter filter = authorizeAndGetProjectMemberFilter();
            if (!form.isInactive())
                filter.addCondition("Active", true); //filter out active users by default
            settings.getBaseFilter().addAllClauses(filter);

            final boolean forExport2 = forExport;
            final boolean isSiteAdmin = getUser().isAdministrator();
            final boolean isProjectAdminOrBetter = isSiteAdmin || isProjectAdmin();

            QueryView queryView = new QueryView(new CoreQuerySchema(getUser(), getContainer()), settings, errors)
            {
                @Override
                protected void setupDataView(DataView ret)
                {
                    super.setupDataView(ret);

                    if (!forExport2 && isProjectAdminOrBetter)
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
            return queryView;
        }

        @Override
        protected ModelAndView getHtmlView(ShowUsersForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            ImpersonateView impersonateView = new ImpersonateView(c, getUser(), false);

            VBox users = new VBox();
            users.setTitle("Users");
            users.setFrame(WebPartView.FrameType.PORTAL);

            JspView<ShowUsersForm> toggleInactiveView = new JspView<ShowUsersForm>("/org/labkey/core/user/toggleInactive.jsp", form);

            users.addView(toggleInactiveView);
            users.addView(createQueryView(form, errors, false, "Users"));

            // Folder admins can't impersonate
            if (impersonateView.hasUsers() && (getUser().isAdministrator() || isProjectAdmin()))
            {
                return new VBox(impersonateView, users);
            }
            else
            {
                return users;
            }
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
        if (user.isAdministrator())
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
        requiresProjectOrSiteAdmin(getUser());
    }


    private void requiresProjectOrSiteAdmin(User user) throws UnauthorizedException
    {
        if (!(user.isAdministrator() || isProjectAdmin(user)))
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


    private SimpleFilter authorizeAndGetProjectMemberFilter() throws UnauthorizedException
    {
        return authorizeAndGetProjectMemberFilter("UserId");
    }


    private SimpleFilter authorizeAndGetProjectMemberFilter(String userIdColumnName) throws UnauthorizedException
    {
        Container c = getContainer();
        SimpleFilter filter = new SimpleFilter();

        if (c.isRoot())
        {
            if (!getUser().isAdministrator())
                throw new UnauthorizedException();
        }
        else
        {
            SQLFragment sql = SecurityManager.getProjectUsersSQL(c.getProject());
            sql.insert(0, userIdColumnName + " IN (SELECT members.UserId ");
            sql.append(")");

            filter.addWhereClause(sql.getSQL(), sql.getParamsArray());
        }

        return filter;
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ShowUserHistoryAction extends SimpleViewAction
    {
        @Override
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            super.checkPermissions();

            requiresProjectOrSiteAdmin();
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            SimpleFilter projectMemberFilter = authorizeAndGetProjectMemberFilter("IntKey1");
            return UserAuditViewFactory.getInstance().createUserHistoryView(getViewContext(), projectMemberFilter);
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


    private boolean isValidRequiredField(final String name)
    {
        return (!"Email".equals(name) && !"UserId".equals(name) &&
                !"LastLogin".equals(name) && !"DisplayName".equals(name));
    }


    @RequiresSiteAdmin
    public class ShowUserPreferencesAction extends FormViewAction<UserPreferenceForm>
    {
        public void validateCommand(UserPreferenceForm target, Errors errors)
        {
        }

        public ModelAndView getView(UserPreferenceForm userPreferenceForm, boolean reshow, BindException errors) throws Exception
        {
            List<String> columnNames = new ArrayList<String>();
            for (String name : getDefaultUserColumnNames().split(","))
            {
                name = name.trim();
                if (isValidRequiredField(name))
                    columnNames.add(name);
            }
            List<ColumnInfo> cols = CoreSchema.getInstance().getTableInfoUsers().getColumns(columnNames.toArray(new String[columnNames.size()]));
            UserPreference bean = new UserPreference(cols, UserManager.getRequiredUserFields());
            return new JspView<UserPreference>("/org/labkey/core/user/userPreferences.jsp", bean);
        }

        public boolean handlePost(UserPreferenceForm form, BindException errors) throws Exception
        {
            final StringBuilder sb = new StringBuilder();
            if (form.getRequiredFields().length > 0)
            {
                String sep = "";
                for (String field : form.getRequiredFields())
                {
                    sb.append(sep);
                    sb.append(field);
                    sep = ";";
                }
            }
            UserManager.setRequiredUserFields(sb.toString());
            return true;
        }

        public ActionURL getSuccessURL(UserPreferenceForm userPreferenceForm)
        {
            return new UserUrlsImpl().getSiteUsersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Site Users", new UserUrlsImpl().getSiteUsersURL());
            return root.addChild("User Preferences");
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

            Map<String, List<Group>> accessGroups = new TreeMap<String, List<Group>>();
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

            List<Group> allGroups = new ArrayList<Group>();
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

    public static class UserPreference
    {
        private List<ColumnInfo> _columns;
        private String _requiredFields;

        public UserPreference(List<ColumnInfo> columns, String requiredFields)
        {
            _columns = columns;
            _requiredFields = requiredFields;
        }

        public List<ColumnInfo> getColumns(){return _columns;}
        public String getRequiredFields(){return _requiredFields;}
    }

    public static class UserPreferenceForm
    {
        private String[] _requiredFields = new String[0];

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRequiredFields(String[] requiredFields)
        {
            _requiredFields = requiredFields;
        }

        public String[] getRequiredFields()
        {
            return _requiredFields;
        }
    }

    private void buildAccessDetailList(MultiMap<Container, Container> containerTree, Container parent,
                                       List<AccessDetailRow> rows, Set<Container> containersInList, User requestedUser,
                                       int depth, Map<Container, Group[]> projectGroupCache, boolean showAll)
    {
        if (requestedUser == null)
            return;
        Collection<Container> children = containerTree.get(parent);
        if (children == null || children.isEmpty())
            return;

        for (Container child : children)
        {
            Map<String, List<Group>> childAccessGroups = new TreeMap<String, List<Group>>();

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
                Group[] groups = projectGroupCache.get(project);
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

    @RequiresPermissionClass(AdminPermission.class)
    public class UserAccessAction extends SimpleViewAction<UserAccessForm>
    {
        private boolean _showNavTrail;
        private Integer _userId;

        @Override
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            super.checkPermissions();

            // Folder admins can't view permissions, #13465
            requiresProjectOrSiteAdmin();
        }

        public ModelAndView getView(UserAccessForm form, BindException errors) throws Exception
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
            view.addView(new JspView<SecurityController.FolderAccessForm>("/org/labkey/core/user/toggleShowAll.jsp", accessForm));

            List<AccessDetailRow> rows = new ArrayList<AccessDetailRow>();
            Set<Container> containersInList = new HashSet<Container>();
            Container c = getContainer();
            MultiMap<Container, Container> containerTree =  c.isRoot() ? ContainerManager.getContainerTree() : ContainerManager.getContainerTree(c.getProject());
            Map<Container, Group[]> projectGroupCache = new HashMap<Container, Group[]>();
            buildAccessDetailList(containerTree, c.isRoot() ? ContainerManager.getRoot() : null, rows, containersInList, requestedUser, 0, projectGroupCache, form.getShowAll());
            AccessDetail details = new AccessDetail(rows);
            details.setActive(requestedUser.isActive());
            JspView<AccessDetail> accessView = new JspView<AccessDetail>("/org/labkey/core/user/userAccess.jsp", details);
            view.addView(accessView);

            if (c.isRoot())
                view.addView(GroupAuditViewFactory.getInstance().createSiteUserView(getViewContext(), form.getUserId()));
            else
                view.addView(GroupAuditViewFactory.getInstance().createProjectMemberView(getViewContext(), form.getUserId()));

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
            if (getUser().isAdministrator())
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
    public class DetailsAction extends SimpleViewAction<UserForm>
    {
        private int _detailsUserId;

        public ModelAndView getView(UserForm form, BindException errors) throws Exception
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
            boolean isSiteAdmin = user.isAdministrator();
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

            DataRegion rgn = getGridRegion(isOwnRecord);
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

                ActionURL changeEmailURL = getViewContext().cloneActionURL().setAction(ChangeEmailAction.class);
                ActionButton changeEmail = new ActionButton(changeEmailURL, "Change Email");
                changeEmail.setActionType(ActionButton.Action.LINK);
                bb.add(changeEmail);

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
                ActionURL viewPermissionsURL = getViewContext().cloneActionURL().setAction(UserAccessAction.class);
                ActionButton viewPermissions = new ActionButton(viewPermissionsURL, "View Permissions");
                viewPermissions.setActionType(ActionButton.Action.LINK);
                bb.add(viewPermissions);
            }

            if (isOwnRecord)
            {
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

            DetailsView detailsView = new DetailsView(rgn, _detailsUserId);
            detailsView.getViewContext().addContextualRole(ReaderRole.class);

            VBox view = new VBox(detailsView);

            if (isProjectAdminOrBetter)
            {
                SimpleFilter filter = new SimpleFilter("IntKey1", _detailsUserId);
                filter.addCondition("EventType", UserManager.USER_AUDIT_EVENT);

                AuditLogQueryView queryView = AuditLogService.get().createQueryView(getViewContext(), filter, UserManager.USER_AUDIT_EVENT);
                queryView.setVisibleColumns(new String[]{"CreatedBy", "Date", "Comment"});
                queryView.setTitle("History:");
                queryView.setSort(new Sort("-Date"));

                view.addView(queryView);
            }

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(UserManager.getEmailForId(_detailsUserId));
        }
    }

    @RequiresLogin @CSRF
    public class ShowUpdateAction extends FormViewAction<UpdateForm>
    {
        Integer _userId;

        public void validateCommand(UpdateForm form, Errors errors)
        {
        }

        public ModelAndView getView(UpdateForm form, boolean reshow, BindException errors) throws Exception
        {
            User user = getUser();
            int userId = user.getUserId();
            if (null == form.getPkVal())
                form.setPkVal(userId);

            boolean isOwnRecord = ((Integer) form.getPkVal()) == userId;
            HttpView view;

            if (user.isAdministrator() || isOwnRecord)
            {
                form.setContainer(null);
                DataRegion rgn = getGridRegion(isOwnRecord);

                rgn.removeColumns("Active");

                String returnUrl = form.getStrings().get(ActionURL.Param.returnUrl.name());

                if (null != returnUrl)
                    rgn.addHiddenFormField(ActionURL.Param.returnUrl, returnUrl);

                view = new UpdateView(rgn, form, errors);
                view.getViewContext().addContextualRole(ReadPermission.class);
                view.getViewContext().addContextualRole(UpdatePermission.class);
            }
            else
            {
                throw new UnauthorizedException();
            }

            if (isOwnRecord)
                view =  new VBox(new HtmlView("Please enter your contact information."), view);

            _userId = (Integer)form.getPkVal();
            return view;
        }

        public boolean handlePost(UpdateForm form, BindException errors) throws Exception
        {
            User user = getUser();
            Map<String,Object> values = form.getTypedValues();
            UserManager.updateUser(user, values, form.getPkVal());
            return true;
        }

        public URLHelper getSuccessURL(UpdateForm form)
        {
            URLHelper returnURL = form.getReturnURLHelper();
            return new UserUrlsImpl().getUserDetailsURL(getContainer(), ((Integer)form.getPkVal()).intValue(), returnURL);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(root, _userId);
            root.addChild("Update");
            return root.addChild(UserManager.getEmailForId(_userId));
        }
    }

    /**
     * Checks to see if the specified user has required fields in their
     * info form that have not been filled.
     * @param user
     * @return
     */
    public static boolean requiresUpdate(User user) throws SQLException
    {
        final String required = UserManager.getRequiredUserFields();

        if (user != null && required != null && required.length() > 0)
        {
            DataRegion rgn = new DataRegion();
            List<ColumnInfo> columns = CoreSchema.getInstance().getTableInfoUsers().getColumns(getDefaultUserColumnNames());
            rgn.setColumns(columns);

            TableInfo info = rgn.getTable();
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("userId", user.getUserId(), CompareType.EQUAL);

			List<ColumnInfo> select = new ArrayList<ColumnInfo>(columns);
			select.add(CoreSchema.getInstance().getTableInfoUsers().getColumn("userId"));
            Table.TableResultSet trs = Table.select(info, select, filter, null);

            try
			{
                // this should really only return one row
                if (trs.next())
                    return !validateRequiredColumns(trs.getRowMap(), info);
            }
            finally
            {
                trs.close();
            }
        }

        return false;
    }

    @RequiresSiteAdmin @CSRF
    public class ChangeEmailAction extends FormViewAction<UserForm>
    {
        private int _userId;

        public void validateCommand(UserForm target, Errors errors)
        {
        }

        public ModelAndView getView(UserForm form, boolean reshow, BindException errors) throws Exception
        {
            _userId = form.getUserId();

            return new JspView<ChangeEmailBean>("/org/labkey/core/user/changeEmail.jsp", new ChangeEmailBean(_userId, form.getMessage()), errors);
        }

        public boolean handlePost(UserForm form, BindException errors) throws Exception
        {
            try
            {
                User user = UserManager.getUser(form.getUserId());

                String message = UserManager.changeEmail(user.getUserId(), user.getEmail(), new ValidEmail(form.getNewEmail()), getUser());

                if (null != message && message.length() > 0)
                    errors.reject(ERROR_MSG, message);
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, "Invalid email address");
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(UserForm form)
        {
            return new UserUrlsImpl().getUserDetailsURL(getContainer(), form.getUserId(), form.getReturnURLHelper());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            addUserDetailsNavTrail(root, _userId);
            return root.addChild("Change Email Address: " + UserManager.getEmailForId(_userId));
        }
    }


    public static class ChangeEmailBean
    {
        public Integer userId;
        public String currentEmail;
        public String message;

        private ChangeEmailBean(Integer userId, String message)
        {
            this.userId = userId;
            this.currentEmail = UserManager.getEmailForId(userId);
            this.message = message;
        }
    }


    public static class UpdateForm extends TableViewForm
    {
        public UpdateForm()
        {
            super(CoreSchema.getInstance().getTableInfoUsers());
        }

        // CONSIDER: implements HasValidator
        public void validateBind(BindException errors)
        {
            super.validateBind(errors);
            Integer userId = (Integer) getPkVal();

            if (userId == null)
            {
                errors.reject(SpringActionController.ERROR_MSG, "User Id cannot be null");
                return;
            }

            String displayName = (String) getTypedValue("DisplayName");

            if (displayName != null)
            {
                //ensure that display name is unique
                User user = UserManager.getUserByDisplayName(displayName);
                //if there's a user with this display name and it's not the user currently being edited
                if (user != null && user.getUserId() != userId)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "The value of the 'Display Name' field conflicts with another value in the database. Please enter a different value");
                }
            }

            String phoneNum = PageFlowUtil.formatPhoneNo((String) getTypedValue("phone"));

            if (phoneNum.length() > 64)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Phone number greater than 64 characters: " + phoneNum);
            }

            phoneNum = PageFlowUtil.formatPhoneNo((String) getTypedValue("mobile"));

            if (phoneNum.length() > 64)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Mobile number greater than 64 characters: " + phoneNum);
            }

            phoneNum = PageFlowUtil.formatPhoneNo((String) getTypedValue("pager"));

            if (phoneNum.length() > 64)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Pager number greater than 64 characters: " + phoneNum);
            }
        }

        public ColumnInfo getColumnByFormFieldName(String name)
        {
            ColumnInfo info = super.getColumnByFormFieldName(name);
            final String requiredFields = UserManager.getRequiredUserFields();

            if (isColumnRequired(name, requiredFields))
            {
                return new RequiredColumn(info);
            }

            return info;
        }
    }


    public static class UserForm extends ReturnUrlForm
    {
        private int _userId;
        private String _newEmail;
        private String _message = null;
        private boolean _renderInHomeTemplate = true;

        public String getNewEmail()
        {
            return _newEmail;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setNewEmail(String newEmail)
        {
            _newEmail = newEmail;
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
    }

    public static class UserAccessForm extends UserForm
    {
        private boolean _showAll = false;

        public boolean getShowAll()
        {
            return _showAll;
        }

        public void setShowAll(boolean showAll)
        {
            _showAll = showAll;
        }
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
    }

    @RequiresLogin
    @RequiresPermissionClass(ReadPermission.class)
    public class GetUsersAction extends ApiAction<GetUsersForm>
    {
        protected static final String PROP_USER_ID = "userId";
        protected static final String PROP_USER_NAME = "displayName";

        public ApiResponse execute(GetUsersForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            User currentUser = getUser();

            if (container.isRoot() && !currentUser.isAdministrator())
                throw new UnauthorizedException("Only site administrators may see users in the root container!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("container", container.getPath());

            Collection<User> users;
            List<Map<String,Object>> userResponseList = new ArrayList<Map<String,Object>>();

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
                    throw new RuntimeException("Could not get group for group id " + groupId);

                response.put("groupId", group.getUserId());
                response.put("groupName", group.getName());
                response.put("groupCaption", SecurityManager.getDisambiguatedGroupName(group));

                users = SecurityManager.getGroupMembers(group);
            }
            else
            {
                //special-case: if container is root, return all active users
                //else, return all users in the current project
                //we've already checked above that the current user is a system admin
                if (container.isRoot())
                    users = UserManager.getActiveUsers();
                else
                    users = SecurityManager.getProjectUsers(container, true);
            }

            if (null != users)
            {
                //trim name filter to empty so we are guaranteed a non-null string
                //and conver to lower-case for the compare below
                String nameFilter = StringUtils.trimToEmpty(form.getName()).toLowerCase();

                if (nameFilter.length() > 0)
                    response.put("name", nameFilter);
                
                for (User user : users)
                {
                    //according to the docs, startsWith will return true even if nameFilter is empty string
                    if (user.getEmail().toLowerCase().startsWith(nameFilter) || user.getDisplayName(null).toLowerCase().startsWith(nameFilter))
                    {
                        Map<String,Object> userInfo = new HashMap<String,Object>();
                        userInfo.put(PROP_USER_ID, user.getUserId());

                        //force sanitize of the display name, even for logged-in users
                        userInfo.put(PROP_USER_NAME, user.getDisplayName(currentUser));

                        //include email address (we now require login so no guests can see the response)
                        userInfo.put("email", user.getEmail());

                        userResponseList.add(userInfo);
                    }
                }
            }

            response.put("users", userResponseList);
            return response;
        }
    }


    public static class ImpersonateUserBean
    {
        public final List<String> emails;
        public final String title;
        public final String message;
        public final boolean isAdminConsole;

        public ImpersonateUserBean(Container c, User user, boolean isAdminConsole)
        {
            this.isAdminConsole = isAdminConsole;

            if (c.isRoot())
            {
                emails = UserManager.getActiveUserEmails();
                // Can't impersonate yourself, so remove current user
                emails.remove(user.getEmail());
                message = null;
                title = isAdminConsole ? "<b>Impersonate User</b>" : null;
            }
            else
            {
                // Filter to project users
                List<User> projectUsers = SecurityManager.getProjectUsers(c);
                emails = new ArrayList<String>(projectUsers.size());

                // Can't impersonate yourself, so remove current user
                for (User member : projectUsers)
                    if (!user.equals(member))
                        emails.add(member.getEmail());

                Collections.sort(emails);

                String instructions = PageFlowUtil.filter("While impersonating within this project, you will not be " +
                    "able to navigate outside the project and you will not inherit any of the user's site-level roles " +
                    "(e.g., Site Administrator, Developer).");

                if (user.isAdministrator())
                    instructions += "<br><br>" + PageFlowUtil.filter("As a site administrator, you can also impersonate " +
                        "from the Admin Console; when impersonating from there, you can access all the user's projects " +
                        "and you inherit the user's site-level roles.  This provides a more complete picture of the user's " +
                        "experience on the site .");

                message = instructions + "<br><br>";
                title = "Impersonate User Within Project " + c.getProject().getName();
            }
        }
    }


    public static class ImpersonateView extends JspView<ImpersonateUserBean>
    {
        public ImpersonateView(Container c, User user, boolean isAdminConsole)
        {
            super("/org/labkey/core/user/impersonate.jsp", new ImpersonateUserBean(c, user, isAdminConsole));

            if (!isAdminConsole)
            {
                if (c.isRoot())
                    setTitle("Impersonate User");
                else
                    setTitle("Impersonate User Within Project " + c.getProject().getName());
            }
        }

        public boolean hasUsers()
        {
            return !getModelBean().emails.isEmpty();
        }
    }


    public static class ImpersonateUserForm extends ReturnUrlForm
    {
        private String _email;

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEmail(String email)
        {
            _email = email;
        }
    }


    @RequiresPermissionClass(AdminPermission.class) @CSRF
    public class ImpersonateUserAction extends SimpleRedirectAction<ImpersonateUserForm>
    {
        public ActionURL getRedirectURL(ImpersonateUserForm form) throws Exception
        {
            if (getUser().isImpersonated())
                throw new UnauthorizedException("Can't impersonate; you're already impersonating");

            String rawEmail = form.getEmail();
            ValidEmail email = new ValidEmail(rawEmail);

            final User impersonatedUser = UserManager.getUser(email);

            if (null == impersonatedUser)
                throw new NotFoundException("User doesn't exist");

            SecurityManager.impersonateUser(getViewContext(), impersonatedUser, form.getReturnURLHelper());
            Container c = getContainer();

            if (c.isRoot())
                return AppProps.getInstance().getHomePageActionURL();
            else
                return PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
        }
    }


    public static class ImpersonateGroupForm extends ReturnUrlForm
    {
        private int _groupId;

        public int getGroupId()
        {
            return _groupId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setGroupId(int groupId)
        {
            _groupId = groupId;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ImpersonateGroupAction extends SimpleRedirectAction<ImpersonateGroupForm>
    {
        @Override
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            super.checkPermissions();
            requiresProjectOrSiteAdmin();
        }

        public ActionURL getRedirectURL(ImpersonateGroupForm form) throws Exception
        {
            if (getUser().isImpersonated())
                throw new UnauthorizedException("Can't impersonate; you're already impersonating");

            Group group = SecurityManager.getGroup(form.getGroupId());

            ActionURL returnURL = form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL());
            SecurityManager.impersonateGroup(getViewContext(), group, returnURL);

            return returnURL;
        }
    }


    public static class ImpersonateRoleForm extends ReturnUrlForm
    {
        private String _roleName;

        public String getRoleName()
        {
            return _roleName;
        }

        public void setRoleName(String roleName)
        {
            _roleName = roleName;
        }
    }


    @RequiresNoPermission  // Permissions are handled below
    public class ImpersonateRoleAction extends SimpleRedirectAction<ImpersonateRoleForm>
    {
        @Override
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            super.checkPermissions();

            User user = getUser();

            if (user.isImpersonated())
            {
                ImpersonationContext impersonationContext = user.getImpersonationContext();
                if (!(impersonationContext instanceof ImpersonateRoleContextFactory.ImpersonateRoleContext))
                    throw new UnauthorizedException("Can't impersonate; you're already impersonating");

                requiresProjectOrSiteAdmin(impersonationContext.getAdminUser());
            }
            else
            {
                requiresProjectOrSiteAdmin();
            }
        }

        public ActionURL getRedirectURL(ImpersonateRoleForm form) throws Exception
        {
            String roleName = StringUtils.trimToNull(form.getRoleName());

            if (null == roleName)
                throw new NotFoundException("roleName parameter is missing");

            Role role = RoleManager.getRole(form.getRoleName());

            if (null == role)
                throw new NotFoundException("Role not found");

            ActionURL returnURL = form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL());
            SecurityManager.impersonateRole(getViewContext(), role, returnURL);

            return returnURL;
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
