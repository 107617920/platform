/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.core.login;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Project;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.module.AllowedBeforeInitialUserIsSet;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.Group;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityMessage;
import org.labkey.api.security.TokenAuthentication;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.core.admin.AdminController;
import org.labkey.core.user.UserController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: Nov 25, 2007
 * Time: 8:22:37 PM
 */
public class LoginController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(LoginController.class);
    private static final ActionResolver _actionResolver = new DefaultActionResolver(LoginController.class);
    private static final int secondsPerYear = 60 * 60 * 24 * 365;

    public LoginController()
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

    public static class LoginUrlsImpl implements LoginUrls
    {
        public NavTree appendAuthenticationNavTrail(NavTree root)
        {
            root.addChild("Admin Console", AdminController.getShowAdminURL()).addChild("Authentication", getConfigureURL());
            return root;
        }

        public ActionURL getConfigureURL()
        {
            return new ActionURL(ConfigureAction.class, ContainerManager.getRoot());
        }

        public ActionURL getConfigureDbLoginURL()
        {
            return LoginController.getConfigureDbLoginURL(false);
        }

        public ActionURL getInitialUserURL()
        {
            return new ActionURL(InitialUserAction.class, ContainerManager.getRoot());
        }

        public ActionURL getVerificationURL(Container c, ValidEmail email, String verification, @Nullable Pair<String, String>[] extraParameters)
        {
            //FIX: 6021, use project container for this URL so it remains short but maintains the project look & feel settings 
            ActionURL url = new ActionURL(SetPasswordAction.class, LookAndFeelProperties.getSettingsContainer(c));
            url.addParameter("verification", verification);
            url.addParameter("email", email.getEmailAddress());

            if (null != extraParameters)
                url.addParameters(extraParameters);

            return url;
        }

        public ActionURL getChangePasswordURL(Container c, User user, URLHelper returnURL, @Nullable String message)
        {
            ActionURL url = new ActionURL(ChangePasswordAction.class, LookAndFeelProperties.getSettingsContainer(c));
            url.addParameter("email", user.getEmail());     // TODO: seems peculiar... why not user id?

            if (null != message)
                url.addParameter("message", message);

            url.addReturnURL(returnURL);

            return url;
        }

        public ActionURL getLoginURL()
        {
            return getLoginURL(HttpView.getContextContainer(), HttpView.getContextURLHelper());
        }

        public ActionURL getLoginURL(URLHelper returnURL)
        {
            // Use root as placeholder; extra path of returnURL determines the real login URL path
            ActionURL url = new ActionURL(LoginAction.class, ContainerManager.getRoot());

            if (null == returnURL)
                returnURL = AppProps.getInstance().getHomePageActionURL();

            if (returnURL instanceof ActionURL)
                url.setExtraPath(((ActionURL)returnURL).getExtraPath());

            url.addReturnURL(returnURL);
            return url;
        }


        public ActionURL getLoginURL(Container c, URLHelper returnURL)
        {
            ActionURL url = new ActionURL(LoginAction.class, c);
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getLogoutURL(Container c)
        {
            return new ActionURL(LogoutAction.class, c);
        }

        public ActionURL getLogoutURL(Container c, URLHelper returnURL)
        {
            ActionURL url = getLogoutURL(c);
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getStopImpersonatingURL(Container c, @Nullable URLHelper returnURL)
        {
            if (null != returnURL)
                return getLogoutURL(c, returnURL);
            else
                return getLogoutURL(c);
        }

        public ActionURL getAgreeToTermsURL(Container c, URLHelper returnURL)
        {
            ActionURL url = new ActionURL(AgreeToTermsAction.class, c);
            url.addReturnURL(returnURL);
            return url;
        }
    }


    private static LoginUrlsImpl getUrls()
    {
        return new LoginUrlsImpl();
    }


    @RequiresNoPermission
    @ActionNames("login, showLogin")
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    // @CSRF don't need CSRF for actions that require a password
    public class LoginAction extends FormViewAction<LoginForm>
    {
        private User _user = null;

        public void validateCommand(LoginForm form, Errors errors)
        {
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            HttpServletResponse response = getViewContext().getResponse();

            // If we're reshowing, the user must have entered incorrect credentials.
            // Set the response code accordingly
            if (reshow)
            {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }

            // If user is already logged in, then redirect immediately.  This is necessary because of Excel's link
            // behavior (see #9246).  Next, check to see if any authentication credentials already exist (passed as URL
            // param, in a cookie, etc.)
            if (!reshow && (isLoggedIn() || authenticate(form, request, response, false)))
                return HttpView.redirect(getSuccessURL(form));
            else
                return showLogin(form, request, getPageConfig());
        }

        public boolean handlePost(LoginForm form, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            HttpServletResponse response = getViewContext().getResponse();

            Project termsProject = getTermsOfUseProject(form);

            if (null != termsProject)
            {
                if (!isTermsOfUseApproved(form))
                {
                    form.errorHtml = "To use the " + termsProject.getName() +  " project, you must log in and approve the terms of use.";
                    return false;
                }
            }

            boolean success = authenticate(form, request, response, true);

            if (success)
            {
                // Terms of use are approved only if we've posted from the login page.  In SSO case, we will attempt
                // to access the page and will get a TermsOfUseException if terms of use approval is required.
                if (null != termsProject)
                    SecurityManager.setTermsOfUseApproved(getViewContext(), termsProject, true);

                // Login page is container qualified, but we need to store the cookie at /labkey/login/ or /cpas/login/ or /login/
                String search = "/login/";
                String url = getViewContext().getActionURL().getLocalURIString();
                int index = url.indexOf(search);
                assert index > -1;
                String path = url.substring(0, index + search.length());

                if (form.isRemember())
                {
                    // Write cookies to save email.
                    // Starting in LabKey 9.1, the cookie value is URL encoded to allow for special characters like @.  See #6736.
                    String unencodedValue = form.getEmail();
                    String encodedValue = PageFlowUtil.encode(unencodedValue);
                    Cookie emailCookie = new Cookie("email", encodedValue);
                    emailCookie.setMaxAge(secondsPerYear);
                    emailCookie.setPath(path);
                    response.addCookie(emailCookie);
                }
                else
                {
                    // Clear the cookie
                    Cookie emailCookie = new Cookie("email", "");
                    emailCookie.setMaxAge(0);
                    emailCookie.setPath(path);
                    response.addCookie(emailCookie);
                }
            }

            return success;
        }

        private boolean isLoggedIn()
        {
            if (getUser().isGuest())
                return false;

            _user = getUser();
            return true;
        }

        private boolean authenticate(LoginForm form, HttpServletRequest request, HttpServletResponse response, boolean logFailures)
        {
            try
            {
                // Attempt authentication with all registered providers
                AuthenticationManager.AuthenticationResult result = AuthenticationManager.authenticate(request, response, form.getEmail(), form.getPassword(), form.getReturnURLHelper(), logFailures);

                switch (result.getStatus())
                {
                    case Success:
                        _user = result.getUser();
                        SecurityManager.setAuthenticatedUser(request, _user);
                        return true;
                    case InactiveUser:
                        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(getContainer());
                        form.errorHtml = "Your account has been deactivated. Please <a href=\"mailto:" + PageFlowUtil.filter(laf.getSystemEmailAddress()) + "\">contact a system administrator</a> if you need to reactivate this account.";
                        break;
                    case BadCredentials:
                        if (null != form.getEmail() || null != form.getPassword())
                        {
                            // Email & password were specified, but authentication failed... display either invalid email address error or generic "couldn't authenticate" message
                            new ValidEmail(form.getEmail());
                            form.errorHtml = "The e-mail address and password you entered did not match any accounts on file.<br><br>\n" +
                                    "Note: Passwords are case sensitive; make sure your Caps Lock is off.";
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown authentication status: " + result.getStatus());
                }
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
                form.errorHtml = sb.toString();
            }

            return false;
        }

        public URLHelper getSuccessURL(LoginForm form)
        {
            return getAfterLoginURL(form, _user, form.getSkipProfile());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    protected HttpView showLogin(LoginForm form, HttpServletRequest request, PageConfig page) throws Exception
    {
        String email = form.getEmail();
        boolean remember = false;

        // If email is null, check to see if email is saved in cookie
        // If email is already filled in (e.g., from setPassword), use it instead of the cookie

        if (null == email)
        {
            email = getEmailFromCookie(request);
            form.setEmail(email);
        }

        if (null != email)
            remember = true;

        VBox vBox = new VBox();

        if (ModuleLoader.getInstance().isUpgradeRequired() || ModuleLoader.getInstance().isUpgradeInProgress())
        {
            HtmlView updateMessageView = new HtmlView("Server upgrade in progress",
                    "This server is being upgraded to a new version of LabKey Server.<br/>" +
                    "Only Site Administrators are permitted to log in during the upgrade process.");
            vBox.addView(updateMessageView);
        }
        else if (ModuleLoader.getInstance().isAdminOnlyMode())
        {
            String content = "The site is currently undergoing maintenance.";
            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            if (null != wikiService)
            {
                content = wikiService.getFormattedHtml(WikiRendererType.RADEOX, ModuleLoader.getInstance().getAdminOnlyMessage());
            }
            HtmlView adminMessageView = new HtmlView("The site is currently undergoing maintenance", content);
            vBox.addView(adminMessageView);
        }

        page.setTemplate(PageConfig.Template.Dialog);
        page.setIncludeLoginLink(false);
        page.setFocusId(null != form.getEmail() ? "password" : "email");
        page.setTitle("Sign In");

        LoginView view = new LoginView(form, remember, form.isApprovedTermsOfUse());
        vBox.addView(view);

        return vBox;
    }


    private URLHelper getAfterLoginURL(ReturnUrlForm form, @Nullable User user, boolean skipProfile)
    {
        Container current = getContainer();
        Container c = (null == current || current.isRoot() ? ContainerManager.getHomeContainer() : getContainer());

        // Default redirect if returnURL is not specified
        URLHelper defaultURL =
                null==current || current.isRoot() ? getWelcomeURL() :
                null != user ? c.getStartURL(user) :
                AppProps.getInstance().getHomePageActionURL();

        // After successful login (and possibly profile update) we'll end up here
        URLHelper returnURL = form.getReturnURLHelper(defaultURL);

        try
        {
            // If this is user's first log in or some required field isn't filled in then go to update page first
            if (!skipProfile && null != user && (user.isFirstLogin() || UserController.requiresUpdate(user)))
            {
                returnURL = PageFlowUtil.urlProvider(UserUrls.class).getUserUpdateURL(returnURL, user.getUserId());
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return returnURL;
    }


    @Nullable
    private String getEmailFromCookie(HttpServletRequest request)
    {
        String email = null;
        Cookie[] cookies = request.getCookies();

        if (null != cookies)
        {
            // Starting in LabKey 9.1, the cookie value is URL encoded to allow for special characters like @.  See #6736.
            String encodedEmail = PageFlowUtil.getCookieValue(cookies, "email", null);

            if (null != encodedEmail)
                email = PageFlowUtil.decode(encodedEmail);
        }

        return email;
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @CSRF
    public class AgreeToTermsAction extends FormViewAction<LoginForm>
    {
        public void validateCommand(LoginForm target, Errors errors)
        {
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors) throws Exception
        {
            AgreeToTermsView view = new AgreeToTermsView(form);

            PageConfig page = getPageConfig();

            page.setTemplate(PageConfig.Template.Dialog);
            page.setTitle("Terms Of Use");
            page.setIncludeLoginLink(false);

            return view;
        }

        public boolean handlePost(LoginForm form, BindException errors) throws Exception
        {
            Project project = getTermsOfUseProject(form);

            if (null != project)
            {
                if (!form.isApprovedTermsOfUse())
                {
                    form.errorHtml = "To use the " + project.getName() +  " project, you must check the box to approve the terms of use.";
                    return false;
                }

                SecurityManager.setTermsOfUseApproved(getViewContext(), project, true);
            }

            return true;
        }

        public URLHelper getSuccessURL(LoginForm form)
        {
            return form.getReturnURLHelper();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private abstract class BaseLoginView extends JspView<LoginBean>
    {
        private BaseLoginView(LoginForm form, boolean agreeOnly, boolean remember, boolean termsOfUseChecked)
        {
            super("/org/labkey/core/login/login.jsp", new LoginBean(form, agreeOnly, remember, termsOfUseChecked));
            setFrame(FrameType.NONE);
        }
    }


    public class LoginBean
    {
        public LoginForm form;
        public boolean agreeOnly;
        public boolean remember;
        public String termsOfUseHTML = null;
        public boolean termsOfUseChecked;

        private LoginBean(LoginForm form, boolean agreeOnly, boolean remember, boolean termsOfUseChecked)
        {
            this.form = form;
            this.agreeOnly = agreeOnly;
            this.remember = remember;
            this.termsOfUseChecked = termsOfUseChecked;

            try
            {
                Project project = getTermsOfUseProject(form);

                // Display the terms of use if this is the terms-of-use page or user hasn't already approved them. #4684
                if (agreeOnly || !SecurityManager.isTermsOfUseApproved(getViewContext(), project))
                    termsOfUseHTML = SecurityManager.getTermsOfUseHtml(project);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }


    private class LoginView extends BaseLoginView
    {
        private LoginView(LoginForm form, boolean remember, boolean termsOfUseChecked)
        {
            super(form, false, remember, termsOfUseChecked);
        }
    }


    private class AgreeToTermsView extends BaseLoginView
    {
        private AgreeToTermsView(LoginForm form)
        {
            super(form, true, false, false);
        }
    }


    @Nullable
    private Project getTermsOfUseProject(LoginForm form) throws ServletException
    {
        Container termsContainer = null;

        URLHelper returnURL = form.getReturnURLHelper(null);

        if (null != returnURL)
        {
            try
            {
                Container redirContainer = ContainerManager.getForPath(new ActionURL(returnURL.getLocalURIString()).getExtraPath());
                if (null != redirContainer)
                    termsContainer = redirContainer.getProject();
            }
            catch (IllegalArgumentException iae)
            {
                // the redirect URL isn't an action url, so we can't get the container. Ignore.
            }
        }

        if (null == termsContainer)
        {
            Container c = getContainer();

            if (c.isRoot())
                return null;
            else
                termsContainer = c.getProject();
        }

        return new Project(termsContainer);
    }


    private boolean isTermsOfUseApproved(LoginForm form) throws ServletException, URISyntaxException
    {
        Project termsProject = getTermsOfUseProject(form);
        return termsProject == null  || form.isApprovedTermsOfUse() || !SecurityManager.isTermsOfUseRequired(termsProject) || SecurityManager.isTermsOfUseApproved(getViewContext(), termsProject);
    }


    private static abstract class AbstractLoginForm extends ReturnUrlForm
    {
        private boolean _skipProfile = false;

        public boolean getSkipProfile()
        {
            return _skipProfile;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSkipProfile(boolean skipProfile)
        {
            _skipProfile = skipProfile;
        }
    }


    public static class LoginForm extends AbstractLoginForm
    {
        private String errorHtml = "";
        private boolean remember;
        private String email;
        private String password;
        private boolean approvedTermsOfUse;

        public void setEmail(String email)
        {
            this.email = email;
        }

        public String getEmail()
        {
            return this.email;
        }

        public String getPassword()
        {
            return password;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPassword(String password)
        {
            this.password = password;
        }

        public boolean isRemember()
        {
            return this.remember;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRemember(boolean remember)
        {
            this.remember = remember;
        }

        public String getErrorHtml()
        {
            return this.errorHtml;
        }

        public void setErrorHtml(String errorHtml)
        {
            this.errorHtml = errorHtml;
        }

        public boolean isApprovedTermsOfUse()
        {
            return approvedTermsOfUse;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setApprovedTermsOfUse(boolean approvedTermsOfUse)
        {
            this.approvedTermsOfUse = approvedTermsOfUse;
        }
    }


    private static URLHelper getWelcomeURL()
    {
        // goto "/" and let the default redirect happen
        return new URLHelper(true);
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class LogoutAction extends RedirectAction<ReturnUrlForm>
    {
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            // Override allows logout from any folder, even when impersonating within a project
        }

        public URLHelper getSuccessURL(ReturnUrlForm form)
        {
            return form.getReturnURLHelper(getWelcomeURL());
        }

        public boolean doAction(ReturnUrlForm form, BindException errors) throws Exception
        {
            User user = getUser();

            if (user.isImpersonated())
                SecurityManager.stopImpersonating(getViewContext(), user);
            else
                SecurityManager.logoutUser(getViewContext().getRequest(), user);

            return true;
        }

        public void validateCommand(ReturnUrlForm form, Errors errors)
        {
        }
    }

    public static final String PASSWORD1_TEXT_FIELD_NAME = "password";
    public static final String PASSWORD2_TEXT_FIELD_NAME = "password2";

    private abstract class AbstractSetPasswordAction extends FormViewAction<SetPasswordForm>
    {
        protected ValidEmail _email = null;
        private boolean _skipProfile = true;  // In most set password scenarios we skip the profile page
        protected boolean _unrecoverableError = false;

        public void validateCommand(SetPasswordForm form, Errors errors)
        {
            ActionURL currentUrl = getViewContext().getActionURL();
            String rawEmail = form.getEmail();

            // Some plain text email clients get confused by the encoding... explicitly look for encoded name
            if (null == rawEmail)
                rawEmail = currentUrl.getParameter("amp;email");

            ValidEmail email;

            try
            {
                email = new ValidEmail(rawEmail);
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject("setPassword", "Invalid email address: " + (null == rawEmail ? "" : rawEmail));
                _unrecoverableError = true;
                return;
            }

            verify(form, email, errors);
        }

        protected void verifyBeforeView(SetPasswordForm form, boolean reshow, BindException errors)
        {
            if (!reshow)
                validateCommand(form, errors);

            if (errors.hasErrors())
            {
                if (_unrecoverableError)
                    _log.warn("Verification failed: " + form.getEmail() + " " + form.getVerification());
                else
                    _log.warn("Password entry error: " + form.getEmail());
            }
        }

        protected String getEmailForForm(SetPasswordForm form)
        {
            return null != _email ? _email.getEmailAddress() : form.getEmail();
        }

        public ModelAndView getView(SetPasswordForm form, boolean reshow, BindException errors) throws Exception
        {
            verifyBeforeView(form, reshow, errors);

            NamedObjectList nonPasswordInputs = getNonPasswordInputs(form);
            NamedObjectList passwordInputs = getPasswordInputs(form);
            String buttonText = getButtonText();
            SetPasswordBean bean = new SetPasswordBean(form, getEmailForForm(form), _unrecoverableError, getMessage(form), nonPasswordInputs, passwordInputs, getClass(), isCancellable(form), buttonText);
            HttpView view = new JspView<SetPasswordBean>("/org/labkey/core/login/setPassword.jsp", bean, errors);

            PageConfig page = getPageConfig();
            page.setTemplate(PageConfig.Template.Dialog);
            page.setTitle(getTitle());
            page.setIncludeLoginLink(false);

            // If we're going to display the form, then set focus on the first input.
            if (!_unrecoverableError)
            {
                String firstInput = (String)(nonPasswordInputs.isEmpty() ? passwordInputs.get(0) : nonPasswordInputs.get(0));
                page.setFocusId(firstInput);
            }

            return view;
        }

        public boolean handlePost(SetPasswordForm form, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();

            // Pull straight from the request to minimize logging of passwords (in Spring, bean utils, etc.)
            String password = request.getParameter("password");
            String password2 = request.getParameter("password2");

            Collection<String> messages = new LinkedList<String>();
            User user = UserManager.getUser(_email);

            if (!DbLoginManager.getPasswordRule().isValidToStore(password, password2, user, messages))
            {
                for (String message : messages)
                    errors.reject("password", message);

                return false;
            }

            try
            {
                SecurityManager.setPassword(_email, password);
            }
            catch (SecurityManager.UserManagementException e)
            {
                errors.reject("password", "Setting password failed: " + e.getMessage() + ".  Contact the " + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName() + " team.");
                return false;
            }

            afterPasswordSet(errors, user);

            if (errors.hasErrors())
                return false;

            // Should log user in only for initial user, choose password, and forced change password scenarios, but not for scenarios
            // where a user is already logged in (normal change password, admins initializing another user's password, etc.)
            if (getUser().isGuest())
            {
                AuthenticationManager.AuthenticationResult result = AuthenticationManager.authenticate(request, getViewContext().getResponse(), _email.getEmailAddress(), password, form.getReturnURLHelper(), true);

                if (result.getStatus() == AuthenticationManager.AuthenticationStatus.Success)
                {
                    // Log the user into the system
                    User authenticatedUser = result.getUser();
                    SecurityManager.setAuthenticatedUser(request, authenticatedUser);
                    getViewContext().setUser(authenticatedUser);
                    _skipProfile = form.getSkipProfile();
                }
            }

            return true;
        }

        public URLHelper getSuccessURL(SetPasswordForm form)
        {
            return getAfterLoginURL(form, getUser(), _skipProfile);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        protected NamedObjectList getNonPasswordInputs(SetPasswordForm form)
        {
            return new NamedObjectList();
        }

        protected String getTitle()
        {
            return "Choose a Password";
        }

        protected String getButtonText()
        {
            return "Set Password";
        }

        protected abstract void verify(SetPasswordForm form, ValidEmail email, Errors errors);
        protected abstract String getMessage(SetPasswordForm form);
        protected abstract NamedObjectList getPasswordInputs(SetPasswordForm form);
        protected abstract void afterPasswordSet(BindException errors, User user) throws SQLException;
        protected abstract boolean isCancellable(SetPasswordForm form);
    }


    @RequiresNoPermission
    @AllowedDuringUpgrade
    @CSRF
    public class SetPasswordAction extends AbstractSetPasswordAction
    {
        @Override
        protected void verify(SetPasswordForm form, ValidEmail email, Errors errors)
        {
            try
            {
                String verification = form.getVerification();

                if (SecurityManager.verify(email, verification))
                {
                    _email = email;

                    if (UserManager.getUser(_email) == null)
                    {
                        errors.reject("setPassword", "This user doesn't exist.  Make sure you've copied the entire link into your browser's address bar.");
                        _unrecoverableError = true;
                    }
                }
                else
                {
                    if (!SecurityManager.loginExists(email))
                        errors.reject("setPassword", "This email address doesn't exist.  Make sure you've copied the entire link into your browser's address bar.");
                    else if (SecurityManager.isVerified(email))
                        errors.reject("setPassword", "This email address has already been verified.");
                    else if (verification.length() < SecurityManager.tempPasswordLength)
                        errors.reject("setPassword", "Make sure you've copied the entire link into your browser's address bar.");
                    else
                        // Incorrect verification string
                        errors.reject("setPassword", "Verification failed.  Make sure you've copied the entire link into your browser's address bar.");

                    _unrecoverableError = true;
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected String getMessage(SetPasswordForm form)
        {
            return "Choose a password you'll use to access this server.";
        }

        @Override
        protected boolean isCancellable(SetPasswordForm form)
        {
            return false;
        }

        @Override
        protected NamedObjectList getPasswordInputs(SetPasswordForm form)
        {
            NamedObjectList list = new NamedObjectList();
            list.put(new SimpleNamedObject("Password", PASSWORD1_TEXT_FIELD_NAME));
            list.put(new SimpleNamedObject("Confirm Password", PASSWORD2_TEXT_FIELD_NAME));

            return list;
        }

        @Override
        public void afterPasswordSet(BindException errors, User user) throws SQLException
        {
            try
            {
                SecurityManager.setVerification(_email, null);
                UserManager.addToUserHistory(user, "Verified and chose a password.");
            }
            catch (SecurityManager.UserManagementException e)
            {
                errors.reject("password", "Resetting verification failed.  Contact the " + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName() + " team.");
            }
        }
    }


    @RequiresNoPermission
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public class InitialUserAction extends AbstractSetPasswordAction
    {
        @Override
        protected void verify(SetPasswordForm form, ValidEmail email, Errors errors)
        {
            if (!UserManager.hasNoUsers())
                errors.reject(ERROR_MSG, "Initial user has already been created.");

            _email = email;
            _unrecoverableError = false;
        }

        @Override
        protected void verifyBeforeView(SetPasswordForm form, boolean reshow, BindException errors)
        {
            verify(form, null, errors);
        }

        @Override
        protected String getMessage(SetPasswordForm form)
        {
            return "Welcome! We see that this is your first time logging in. This wizard will guide you through " +
                    "creating a Site Administrator account that has full control over this server, installing the modules " +
                    "required to use LabKey Server, and setting some basic configuration.";
        }

        @Override
        protected NamedObjectList getNonPasswordInputs(SetPasswordForm form)
        {
            NamedObjectList list = new NamedObjectList();
            list.put(new SimpleNamedObject("Email Address", "email", form.getEmail()));

            return list;
        }

        @Override
        protected NamedObjectList getPasswordInputs(SetPasswordForm form)
        {
            NamedObjectList list = new NamedObjectList();
            list.put(new SimpleNamedObject("Password", PASSWORD1_TEXT_FIELD_NAME));
            list.put(new SimpleNamedObject("Retype Password", PASSWORD2_TEXT_FIELD_NAME));

            return list;
        }

        @Override
        protected void afterPasswordSet(BindException errors, User user) throws SQLException
        {
            // Put it here to get ordering right... "Added to the system" gets logged before first login
            UserManager.addToUserHistory(user, "Added to the system via the initial user page.");
        }

        @Override
        public ModelAndView getView(SetPasswordForm form, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView result = super.getView(form, reshow, errors);
            getPageConfig().setNavTrail(AdminController.getInstallUpgradeWizardSteps());
            getPageConfig().setTitle("Account Setup");
            getPageConfig().setTemplate(PageConfig.Template.Wizard);
            return result;
        }

        @Override
        public boolean handlePost(SetPasswordForm form, BindException errors) throws Exception
        {
            boolean success = false;
            DbScope scope = CoreSchema.getInstance().getSchema().getScope();

            try
            {
                ValidEmail email = new ValidEmail(form.getEmail());

                // All initial user creation steps need to be transacted
                scope.beginTransaction();

                // Add the initial user
                SecurityManager.NewUserStatus newUserBean = SecurityManager.addUser(email);
                // Set the password
                success = super.handlePost(form, errors);

                // If successful, add audit event, make site admin, set some properties based on email domain, and commit
                if (success)
                {
                    SecurityManager.addMember(SecurityManager.getGroup(Group.groupAdministrators), newUserBean.getUser());

                    //set default "from" address for system emails to first registered user
                    WriteableLookAndFeelProperties laf = WriteableLookAndFeelProperties.getWriteableInstance(ContainerManager.getRoot());
                    laf.setSystemEmailAddress(newUserBean.getEmail());
                    laf.save();

                    //set default domain and default LSID authority to user email domain
                    String userEmailAddress = newUserBean.getEmail().getEmailAddress();
                    int atSign = userEmailAddress.indexOf("@");

                    //did user most likely enter a valid email address? if so, set default domain
                    if (atSign > 0 && atSign < userEmailAddress.length() - 1)
                    {
                        String defaultDomain = userEmailAddress.substring(atSign + 1, userEmailAddress.length());
                        WriteableAppProps appProps = AppProps.getWriteableInstance();
                        appProps.setDefaultDomain(defaultDomain);
                        appProps.setDefaultLsidAuthority(defaultDomain);
                        appProps.save();
                    }

                    scope.commitTransaction();
                }
            }
            catch (SecurityManager.UserManagementException e)
            {
                errors.reject(ERROR_MSG, "Unable to create user '" + PageFlowUtil.filter(e.getEmail()) + "': " + e.getMessage());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.rejectValue("email", ERROR_MSG, "The string '" + PageFlowUtil.filter(form.getEmail()) + "' is not a valid email address.  Please enter an email address in this form: user@domain.tld");
            }
            finally
            {
                scope.closeConnection();
            }

            return success;
        }

        @Override
        protected boolean isCancellable(SetPasswordForm form)
        {
            return false;
        }

        @Override
        protected String getTitle()
        {
            return "Register First User";
        }

        @Override
        protected String getButtonText()
        {
            return "Next";
        }

        @Override
        protected String getEmailForForm(SetPasswordForm form)
        {
            return null;
        }
    }


    // Attributions to Spring and Apache used to be displayed on the initial user page... not sure why.  If we actually
    // need them, throw this view onto the appropriate page.
    private static class AttributionView extends JspView<List<String>>
    {
        private AttributionView()
        {
            super("/org/labkey/core/login/attribution.jsp", getAttributions());
        }

        private static List<String> getAttributions()
        {
            List<String> attributions = new ArrayList<String>();

            for (Module module : ModuleLoader.getInstance().getModules())
            {
                attributions.addAll(module.getAttributions());
            }

            return attributions;
        }
    }


    @RequiresNoPermission
    @RequiresLogin
    @AllowedDuringUpgrade
    // @CSRF don't need CSRF for actions that require a password
    public class ChangePasswordAction extends AbstractSetPasswordAction
    {
        @Override
        protected void verify(SetPasswordForm form, ValidEmail email, Errors errors)
        {
            if (!SecurityManager.loginExists(email))
            {
                errors.reject("setPassword", "This email address doesn't exist.");
                _unrecoverableError = true;
            }
            else
            {
                _email = email;
            }
        }

        @Override
        protected String getMessage(SetPasswordForm form)
        {
            return null != form.getMessage() ? form.getMessage() : "Choose a new password.";
        }

        @Override
        protected boolean isCancellable(SetPasswordForm form)
        {
            // No message => user clicked "change password" button -- allow cancel in this case
            return null == form.getMessage();
        }

        @Override
        protected NamedObjectList getPasswordInputs(SetPasswordForm form)
        {
            NamedObjectList list = new NamedObjectList();
            list.put(new SimpleNamedObject("Old Password", "oldPassword"));
            list.put(new SimpleNamedObject("New Password", PASSWORD1_TEXT_FIELD_NAME));
            list.put(new SimpleNamedObject("Retype New Password", PASSWORD2_TEXT_FIELD_NAME));

            return list;
        }

        @Override
        public boolean handlePost(SetPasswordForm form, BindException errors) throws Exception
        {
            // Verify the old password on post
            HttpServletRequest request = getViewContext().getRequest();
            String oldPassword = request.getParameter("oldPassword");

            String hash = SecurityManager.getPasswordHash(new ValidEmail(form.getEmail()));

            if (!SecurityManager.matchPassword(oldPassword, hash))
            {
                errors.reject("password", "Incorrect old password.");
                return false;
            }

            return super.handlePost(form, errors);
        }

        @Override
        public void afterPasswordSet(BindException errors, User user) throws SQLException
        {
            UserManager.addToUserHistory(user, "Changed password.");
        }
    }


    public static class SetPasswordBean
    {
        public final String email;
        public final SetPasswordForm form;
        public final boolean unrecoverableError;
        public final String message;
        public final NamedObjectList nonPasswordInputs;
        public final NamedObjectList passwordInputs;
        public final String actionName;
        public final boolean cancellable;
        public final String buttonText;

        private SetPasswordBean(SetPasswordForm form, @Nullable String emailForForm, boolean unrecoverableError, String message, NamedObjectList nonPasswordInputs, NamedObjectList passwordInputs, Class<? extends AbstractSetPasswordAction> clazz, boolean cancellable, String buttonText)
        {
            this.form = form;
            this.email = emailForForm;
            this.unrecoverableError = unrecoverableError;
            this.message = message;
            this.nonPasswordInputs = nonPasswordInputs;
            this.passwordInputs = passwordInputs;
            this.actionName = getActionName(clazz);
            this.cancellable = cancellable;
            this.buttonText = buttonText;
        }
    }


    public static class SetPasswordForm extends AbstractLoginForm
    {
        private String _verification;
        private String _email;
        private String _message;

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEmail(String email)
        {
            _email = email;
        }

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setVerification(String verification)
        {
            _verification = verification;
        }

        public String getVerification()
        {
            return _verification;
        }

        public String getMessage()
        {
            return _message;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setMessage(String message)
        {
            _message = message;
        }
    }


    @RequiresNoPermission
    @AllowedDuringUpgrade
    @CSRF
    public class ResetPasswordAction extends FormViewAction<LoginForm>
    {
        private ValidEmail _email = null;
        private User _user = null;
        private HttpView _finishView = null;

        public void validateCommand(LoginForm form, Errors errors)
        {
            final String rawEmail = form.getEmail();

            if (null == rawEmail)
            {
                errors.reject("reset", "You must enter an email address");
                return;
            }

            try
            {
                _email = new ValidEmail(rawEmail);

                if (SecurityManager.isLdapEmail(_email))
                    // ldap authentication users must reset through their ldap administrator
                    errors.reject("reset", "Reset Password failed: " + _email + " is an LDAP email address. Please contact your LDAP administrator to reset the password for this account.");
                else if (!SecurityManager.loginExists(_email))
                    errors.reject("reset", "Reset Password failed: " + _email + " does not have a password.");
                else
                {
                    _user = UserManager.getUser(_email);

                    // We've validated that a login exists, so the user better not be null... but crashweb #8379 indicates this can happen.
                    if (null == _user)
                        errors.reject("reset", "This account does not exist.");
                    else if (!_user.isActive())
                        errors.reject("reset", "The password for this account may not be reset because this account has been deactivated. Please contact your administrator to re-activate this account.");
                }
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject("reset", "Reset Password failed: " + rawEmail + " is not a valid email address.");
            }
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);

            if (null != _finishView)
                return _finishView;

            JspView view = new JspView<LoginForm>("/org/labkey/core/login/resetPassword.jsp", form, errors);

            if (null == form.getEmail())
            {
                form.setEmail(getEmailFromCookie(getViewContext().getRequest()));
            }

            return view;
        }

        public boolean handlePost(LoginForm form, BindException errors) throws Exception
        {
            StringBuilder sbReset = new StringBuilder();

            try
            {
                // Create a placeholder password that's impossible to guess and a separate email
                // verification key that gets emailed.
                String verification = SecurityManager.createTempPassword();

                SecurityManager.setVerification(_email, verification);

                try
                {
                    Container c = getContainer();
                    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
                    final SecurityMessage message = SecurityManager.getResetMessage(false);
                    ActionURL verificationURL = SecurityManager.createVerificationURL(c, _email, verification, null);

                    final User system = new User(laf.getSystemEmailAddress(), 0);
                    system.setFirstName(laf.getCompanyName());
                    SecurityManager.sendEmail(c, system, message, _email.getEmailAddress(), verificationURL);

                    if (!_user.getEmail().equals(_email.getEmailAddress()))
                    {
                        final SecurityMessage adminMessage = SecurityManager.getResetMessage(true);
                        message.setTo(_email.getEmailAddress());
                        SecurityManager.sendEmail(c, _user, adminMessage, _user.getEmail(), verificationURL);
                    }
                    sbReset.append("An email has been sent to you with instructions for how to reset your password. ");
                    UserManager.addToUserHistory(UserManager.getUser(_email), _email + " reset the password.");
                }
                catch (MessagingException e)
                {
                    sbReset.append("Failed to send email due to: <pre>").append(e.getMessage()).append("</pre>");
                    UserManager.addToUserHistory(UserManager.getUser(_email), _email + " reset the password, but sending the email failed.");
                }
            }
            catch (SecurityManager.UserManagementException e)
            {
                sbReset.append(": failed to reset password due to: ").append(e.getMessage());
                UserManager.addToUserHistory(UserManager.getUser(_email), _email + " attempted to reset the password, but the reset failed: " + e.getMessage());
            }

            _finishView = new JspView<String>("/org/labkey/core/login/finishResetPassword.jsp", sbReset.toString());

            return false;
        }

        public ActionURL getSuccessURL(LoginForm loginForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresLogin
    public class CreateTokenAction extends SimpleViewAction<TokenAuthenticationForm>
    {
        public ModelAndView getView(TokenAuthenticationForm form, BindException errors) throws Exception
        {
            URLHelper returnUrl = form.getValidReturnUrl();

            if (null == returnUrl)
            {
                PageConfig page = getPageConfig();
                page.setTemplate(PageConfig.Template.Dialog);
                page.setTitle("Token Authentication Error");
                return new HtmlView("Error: a valid returnUrl was not specified.");
            }

            String token = TokenAuthentication.createToken(getViewContext().getRequest(), getUser());
            returnUrl.addParameter("labkeyToken", token);
            returnUrl.addParameter("labkeyEmail", getUser().getEmail());

            getViewContext().getResponse().sendRedirect(returnUrl.getURIString());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    public class VerifyTokenAction extends SimpleViewAction<TokenAuthenticationForm>
    {
        public ModelAndView getView(TokenAuthenticationForm form, BindException errors) throws Exception
        {
            String message = null;
            User user = null;

            if (null == form.getLabkeyToken())
            {
                message = "Token was not specified";
            }
            else
            {
                user = TokenAuthentication.getUserForToken(form.getLabkeyToken());

                if (null == user)
                    message = "Unknown token";
            }

            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("text/xml");
            PrintWriter out = response.getWriter();

            if (null != user)
            {
                out.print("<TokenAuthentication success=\"true\" ");
                out.print("token=\"" + form.getLabkeyToken() + "\" ");
                out.print("email=\"" + user.getEmail() + "\" ");
                out.print("permissions=\"" + getContainer().getPolicy().getPermsAsOldBitMask(user) + "\"/>");
            }
            else
            {
                out.print("<TokenAuthentication success=\"false\" ");
                out.print("message=\"" + message + "\"/>");
            }

            out.close();
            response.flushBuffer();

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresNoPermission
    public class InvalidateTokenAction extends SimpleViewAction<TokenAuthenticationForm>
    {
        public ModelAndView getView(TokenAuthenticationForm form, BindException errors) throws Exception
        {
            if (null != form.getLabkeyToken())
                TokenAuthentication.invalidateToken(form.getLabkeyToken());

            URLHelper returnUrl = form.getValidReturnUrl();

            if (null != returnUrl)
                getViewContext().getResponse().sendRedirect(returnUrl.getURIString());

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class TokenAuthenticationForm
    {
        String _labkeyToken;
        ReturnURLString _returnUrl;

        public ReturnURLString getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(ReturnURLString returnUrl)
        {
            _returnUrl = returnUrl;
        }

        public String getLabkeyToken()
        {
            return _labkeyToken;
        }

        public void setLabkeyToken(String labkeyToken)
        {
            _labkeyToken = labkeyToken;
        }

        public URLHelper getValidReturnUrl()
        {
            URLHelper returnUrl = null;

            if (null != getReturnUrl())
            {
                try
                {
                    returnUrl = new URLHelper(getReturnUrl());
                }
                catch (URISyntaxException e)
                {
                    // Bad URL case -- return null
                }
            }

            return returnUrl;
        }
    }


    private static abstract class ConfigURLFactory implements AuthenticationManager.URLFactory
    {
        public ActionURL getActionURL(AuthenticationProvider provider)
        {
            ActionURL url = new ActionURL(getActionClass(), ContainerManager.getRoot());
            url.addParameter("name", provider.getName());
            return url;
        }

        protected abstract Class<? extends Controller> getActionClass();
    }


    private static class EnableURLFactory extends ConfigURLFactory
    {
        private EnableURLFactory()
        {
            super();
        }

        protected Class<? extends Controller> getActionClass()
        {
            return EnableAction.class;
        }
    }


    private static class DisableURLFactory extends ConfigURLFactory
    {
        protected Class<? extends Controller> getActionClass()
        {
            return DisableAction.class;
        }
    }



    @AdminConsoleAction
    public class ConfigureAction extends SimpleViewAction<ReturnUrlForm>
    {
        public ModelAndView getView(ReturnUrlForm form, BindException errors) throws Exception
        {
            return AuthenticationManager.getConfigurationView(new EnableURLFactory(), new DisableURLFactory());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("authenticationModule"));
            return getUrls().appendAuthenticationNavTrail(root);
        }
    }


    @RequiresSiteAdmin
    public class EnableAction extends RedirectAction<ProviderConfigurationForm>
    {
        public ActionURL getSuccessURL(ProviderConfigurationForm form)
        {
            return getUrls().getConfigureURL();
        }

        public boolean doAction(ProviderConfigurationForm form, BindException errors) throws Exception
        {
            AuthenticationManager.enableProvider(form.getName());
            return true;
        }

        public void validateCommand(ProviderConfigurationForm target, Errors errors)
        {
        }
    }


    @RequiresSiteAdmin
    public class DisableAction extends RedirectAction<ProviderConfigurationForm>
    {
        public ActionURL getSuccessURL(ProviderConfigurationForm form)
        {
            return getUrls().getConfigureURL();
        }

        public boolean doAction(ProviderConfigurationForm form, BindException errors) throws Exception
        {
            AuthenticationManager.disableProvider(form.getName());
            return true;
        }

        public void validateCommand(ProviderConfigurationForm target, Errors errors)
        {
        }
    }


    public static class ProviderConfigurationForm
    {
        String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }


    public static ActionURL getConfigureDbLoginURL(boolean reshow)
    {
        ActionURL url = new ActionURL(ConfigureDbLoginAction.class, ContainerManager.getRoot());

        if (reshow)
            url.addParameter("reshow", "1");

        return url;
    }


    @AdminConsoleAction
    public class ConfigureDbLoginAction extends FormViewAction<Config>
    {
        public ModelAndView getView(Config form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<Config>("/org/labkey/core/login/configureDbLogin.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure Database Authentication");
            setHelpTopic(new HelpTopic("configDbLogin"));
            return root;
        }

        public void validateCommand(Config form, Errors errors)
        {
        }

        public boolean handlePost(Config form, BindException errors) throws Exception
        {
            DbLoginManager.saveProperties(form);
            return true;
        }

        public ActionURL getSuccessURL(Config form)
        {
            return getConfigureDbLoginURL(true);  // Redirect to same action -- want to reload props from database
        }
    }


    @RequiresNoPermission
    public static class WhoAmIAction extends MutatingApiAction // require POST
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            User user = getViewContext().getUser();
            ApiSimpleResponse res = new ApiSimpleResponse();
            res.put("id", user.getUserId());
            res.put("displayName", user.getDisplayName(user));
            res.put("email", user.getEmail());
            res.put("success", true);
            return res;
        }
    }


    public static class Config extends ReturnUrlForm
    {
        public String helpLink = PageFlowUtil.textLink("More information about database authentication", new HelpTopic("configDbLogin").getHelpTopicLink(), null, null, Collections.singletonMap("target", "_new"));
        public PasswordRule currentRule = DbLoginManager.getPasswordRule();
        public PasswordExpiration currentExpiration = DbLoginManager.getPasswordExpiration();
        public boolean reshow = false;

        private String strength = "Weak";
        private String expiration = "Never";

        public boolean isReshow()
        {
            return reshow;
        }

        public void setReshow(boolean reshow)
        {
            this.reshow = reshow;
        }

        public String getStrength()
        {
            return strength;
        }

        public void setStrength(String strength)
        {
            this.strength = strength;
        }

        public String getExpiration()
        {
            return expiration;
        }

        public void setExpiration(String expiration)
        {
            this.expiration = expiration;
        }
    }
}
