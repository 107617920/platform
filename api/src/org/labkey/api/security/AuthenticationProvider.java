/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.annotations.RefactorIn16_3;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:49:05 PM
 */
public interface AuthenticationProvider
{
    // All the AuthenticationProvider interfaces. This list is used by AuthenticationProviderCache to filter collections of providers.
    List<Class<? extends AuthenticationProvider>> ALL_PROVIDER_INTERFACES = Arrays.asList(
        AuthenticationProvider.class,
            PrimaryAuthenticationProvider.class,
                LoginFormAuthenticationProvider.class,
                SSOAuthenticationProvider.class,
                RequestAuthenticationProvider.class,
            SecondaryAuthenticationProvider.class,
            ResetPasswordProvider.class
    );

    @Nullable ActionURL getConfigurationLink();
    @NotNull String getName();
    @NotNull String getDescription();

    default void activate()
    {
        // TODO: block activation if provider hasn't been configured... add isConfigured()?
    }

    default void deactivate()
    {
    }

    default boolean isPermanent()
    {
        return false;
    }

    @Deprecated
    @RefactorIn16_3
    // This makes no sense for secondary auth, reset password, etc., so it's been moved to PrimaryAuthenticationProvider. But
    // leaving it here temporarily to avoid breaking in-flight feature branches. TODO: remove in 16.3
    default void logout(HttpServletRequest request)
    {
    }

    interface PrimaryAuthenticationProvider extends AuthenticationProvider
    {
        default void logout(HttpServletRequest request)
        {
        }
    }

    interface LoginFormAuthenticationProvider extends PrimaryAuthenticationProvider
    {
        // id and password will not be blank (not null, not empty, not whitespace only)
        AuthenticationResponse authenticate(@NotNull String id, @NotNull String password, URLHelper returnURL) throws InvalidEmailException;
    }

    interface SSOAuthenticationProvider extends PrimaryAuthenticationProvider
    {
        /**
         * Return the external service's URL.
         * @return The redirect URL
         */
        URLHelper getURL(String secret);
        LinkFactory getLinkFactory();
    }

    interface RequestAuthenticationProvider extends PrimaryAuthenticationProvider
    {
        AuthenticationResponse authenticate(@NotNull HttpServletRequest request);
    }

    interface ResetPasswordProvider extends AuthenticationProvider
    {
        /**
         * Returns the base url (excluding verification, email and extra params) for AddUsersAction or ResetPasswordApiAction
         * @param isAddUser true for adding user, otherwise for resetting pw for existing user
         */
        ActionURL getAPIVerificationURL(Container c, boolean isAddUser);

        /**
         * Allow module to send custom email for ResetPasswordApiAction.
         * @param user the user requesting password reset
         * @param isAdminCopy true for sending admin a copy of reset password email
         */
        @Nullable SecurityMessage getAPIResetPasswordMessage(User user, boolean isAdminCopy) throws Exception;

    }

    interface SecondaryAuthenticationProvider extends AuthenticationProvider
    {
        /**
         *  Initiate secondary authentication process for the specified user. candidate has been authenticated via one of the primary providers,
         *  but isn't officially authenticated until user successfully validates with all enabled SecondaryAuthenticationProviders as well.
         */
        ActionURL getRedirectURL(User candidate, Container c);

        /**
         * Bypass authentication from this provider. Might be configured via labkey.xml parameter to
         * temporarily not require secondary authentication if this has been misconfigured or a 3rd
         * party service provider is unavailable.
         *
         */
        boolean bypass();
    }

    class AuthenticationResponse
    {
        private final @Nullable ValidEmail _email;
        private final @Nullable FailureReason _failureReason;
        private final @Nullable ActionURL _redirectURL;

        private AuthenticationResponse(@NotNull ValidEmail email)
        {
            _email = email;
            _failureReason = null;
            _redirectURL = null;
        }

        private AuthenticationResponse(@NotNull FailureReason failureReason, @Nullable ActionURL redirectURL)
        {
            _email = null;
            _failureReason = failureReason;
            _redirectURL = redirectURL;
        }

        public static AuthenticationResponse createSuccessResponse(ValidEmail email)
        {
            return new AuthenticationResponse(email);
        }

        public static AuthenticationResponse createFailureResponse(FailureReason failureReason)
        {
            return new AuthenticationResponse(failureReason, null);
        }

        public static AuthenticationResponse createFailureResponse(FailureReason failureReason, @Nullable ActionURL redirectURL)
        {
            return new AuthenticationResponse(failureReason, redirectURL);
        }

        public boolean isAuthenticated()
        {
            return null != _email;
        }

        public @NotNull FailureReason getFailureReason()
        {
            assert null != _failureReason && null == _email;

            return _failureReason;
        }

        public @NotNull ValidEmail getValidEmail()
        {
            assert null != _email;

            return _email;
        }

        public @Nullable ActionURL getRedirectURL()
        {
            return _redirectURL;
        }
    }

    // FailureReasons are only reported to administrators (in the audit log and/or server log), NOT to users (and potential
    // hackers).  We try to be as specific as possible.
    enum FailureReason
    {
        userDoesNotExist(ReportType.onFailure, "user does not exist"),
        badPassword(ReportType.onFailure, "incorrect password"),
        badCredentials(ReportType.onFailure, "invalid credentials"),  // Use for cases where we can't distinguish between userDoesNotExist and badPassword
        complexity(ReportType.onFailure, "password does not meet the complexity requirements"),
        expired(ReportType.onFailure, "password has expired"),
        configurationError(ReportType.always, "configuration problem"),
        notApplicable(ReportType.never, "not applicable");

        private final ReportType _type;
        private final String _message;

        FailureReason(ReportType type, String message)
        {
            _type = type;
            _message = message;
        }

        public ReportType getReportType()
        {
            return _type;
        }

        public String getMessage()
        {
            return _message;
        }
    }

    enum ReportType
    {
        always, onFailure, never
    }
}
