/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.api.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * User: rossb
 * Date: Oct 26, 2006
 */
public class ExceptionUtil
{
    private static final JobRunner _jobRunner = new JobRunner(1);
    private static final Logger _log = Logger.getLogger(ExceptionUtil.class);


    private ExceptionUtil()
    {
    }


    public static String renderStackTrace(StackTraceElement[] stackTrace)
    {
        StringBuilder trace = new StringBuilder();

        for (int i = 2; i < stackTrace.length; i++)
        {
            trace.append("\n\tat ");
            trace.append(stackTrace[i]);
        }

        return trace.toString();
    }


    @NotNull
    public static Throwable unwrapException(@NotNull Throwable ex)
    {
        Throwable cause=ex;

        while (null != cause)
        {
            ex = cause;
            cause = null;

            if (ex.getClass() == RuntimeException.class || ex.getClass() == RuntimeSQLException.class || ex instanceof InvocationTargetException || ex instanceof com.google.gwt.user.server.rpc.UnexpectedException)
            {
                cause = ex.getCause();
            }
            else if (ex.getClass() == ServletException.class && ((ServletException)ex).getRootCause() != null)
            {
                ex = ((ServletException)ex).getRootCause();
            }
            else if (ex instanceof BatchUpdateException)
            {
                cause = ((BatchUpdateException)ex).getNextException();
            }
        }

        return ex;
    }


    public static String renderException(Throwable e)
    {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = PageFlowUtil.filter(sw.toString());
        s = s.replaceAll(" ", "&nbsp;");
        s = s.replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
        return "<pre>\n" + s + "</pre>\n";
    }


    public static String getUnauthorizedMessage(ViewContext context)
    {
        return "<table width=\"100%\"><tr><td align=left>" +
                (context.getUser().isGuest() ? "Please sign in to see this data." : "You do not have permission to see this data.") +
                "</td></tr></table>";
    }


    public static ErrorView getErrorView(int responseStatus, String message, Throwable ex,
                                        HttpServletRequest request, boolean startupFailure)
    {
        ErrorRenderer renderer = getErrorRenderer(responseStatus, message, ex, request, false, startupFailure);
        return new ErrorView(renderer, startupFailure);
    }


    public static ErrorView getErrorView(int responseStatus, String message, Throwable ex,
                                        HttpServletRequest request, boolean startupFailure, boolean popup)
    {
        ErrorRenderer renderer = getErrorRenderer(responseStatus, message, ex, request, false, startupFailure);
        return new ErrorView(renderer, startupFailure, popup);
    }


    public static WebPartView getErrorWebPartView(int responseStatus, String message, Throwable ex,
                                                  HttpServletRequest request)
    {
        ErrorRenderer renderer = getErrorRenderer(responseStatus, message, ex, request, true, false);
        return new WebPartErrorView(renderer);
    }


    public static ErrorRenderer getErrorRenderer(int responseStatus, String message, Throwable ex,
                                                 HttpServletRequest request, boolean isPart, boolean isStartupFailure)
    {
        if (!isStartupFailure && responseStatus == HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            logExceptionToMothership(request, ex);

        if (isPart)
            return new WebPartErrorRenderer(responseStatus, message, ex, isStartupFailure);
        else
            return new ErrorRenderer(responseStatus, message, ex, isStartupFailure);
    }

    /** request may be null if this is coming from a background thread */
    public static void logExceptionToMothership(@Nullable HttpServletRequest request, Throwable ex)
    {
        Map<Enum,String> decorations = getExceptionDecorations(ex);

        ex = unwrapException(ex);

        if (ex == null ||
                null != decorations.get(ExceptionInfo.SkipMothershipLogging) ||
                ex instanceof SkipMothershipLogging ||
                isClientAbortException(ex) ||
                _jobRunner.getJobCount() > 10)
        {
            // Don't log these
            return;
        }

        _log.error("Exception detected and logged to mothership", ex);

        String originalURL = request == null ? null : (String) request.getAttribute(ViewServlet.ORIGINAL_URL_STRING);
        ExceptionReportingLevel level = AppProps.getInstance().getExceptionReportingLevel();

        if (level == ExceptionReportingLevel.NONE)
            return;

        // Need this extra check to make sure we're not in an infinite loop if there's
        // an exception when trying to submit an exception
        if (originalURL != null && MothershipReport.isMothershipExceptionReport(originalURL))
            return;

        try
        {
            MothershipReport report = new MothershipReport(MothershipReport.Type.ReportException);

            if (!decorations.isEmpty() && (level == ExceptionReportingLevel.MEDIUM || level == ExceptionReportingLevel.HIGH))
                report.addParam("exceptionMessage", getExtendedMessage(ex));

            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter, true);
            ex.printStackTrace(printWriter);
            if (ex instanceof ServletException && ((ServletException)ex).getRootCause() != null)
            {
                printWriter.println("Nested ServletException cause is:");
                ((ServletException)ex).getRootCause().printStackTrace(printWriter);
            }
            report.addParam("browser", request == null ? null : request.getHeader("User-Agent"));

            String stackTrace = stringWriter.getBuffer().toString();
            for (Throwable t = ex ; t != null ; t = t.getCause())
            {
                String sqlState = null;

                if (t instanceof RuntimeSQLException)
                {
                    // Unwrap RuntimeSQLExceptions
                    t = ((RuntimeSQLException)t).getSQLException();
                }

                if (t instanceof SQLException)
                {
                    SQLException sqlException = (SQLException) t;
                    if (sqlException.getMessage() != null && sqlException.getMessage().indexOf("terminating connection due to administrator command") != -1)
                    {
                        // Don't report exceptions from Postgres shutting down
                        return;
                    }
                    sqlState = sqlException.getSQLState();
                    String extraInfo = CoreSchema.getInstance().getSqlDialect().getExtraInfo(sqlException);
                    if (extraInfo != null)
                    {
                        stackTrace = stackTrace + "\n" + extraInfo;
                    }
                }

                if (sqlState != null)
                {
                    report.addParam("sqlState", sqlState);
                    break;
                }
            }

            report.addParam("stackTrace", stackTrace);

            report.addServerSessionParams();
            if (originalURL != null)
            {
                try
                {
                    ActionURL url = new ActionURL(originalURL);
                    report.addParam("pageflowName", url.getPageFlow());
                    report.addParam("pageflowAction", url.getAction());
                }
                catch (IllegalArgumentException x)
                {
                    // fall through
                }
            }
            if (level == ExceptionReportingLevel.MEDIUM || level == ExceptionReportingLevel.HIGH)
            {
                if (originalURL != null)
                {
                    report.addParam("requestURL", originalURL);
                    report.addParam("referrerURL", request == null ? null : request.getHeader("Referer"));
                }

                if (level == ExceptionReportingLevel.HIGH)
                {
                    User user = request == null ? null : (User) request.getUserPrincipal();
                    if (user == null)
                    {
                        report.addParam("username", "NOT SET");
                    }
                    else
                    {
                        report.addParam("username", user.getEmail() == null ? "Guest" : user.getEmail());
                    }
                }
            }

            _jobRunner.execute(report);
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    static class WebPartErrorView extends WebPartView
    {
        private final ErrorRenderer _renderer;

        WebPartErrorView(ErrorRenderer renderer)
        {
            super();
            _renderer = renderer;
        }

        @Override
        protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            PrintWriter out = response.getWriter();
            _renderer.renderStart(out);
            _renderer.renderContent(out, request, null);
            _renderer.renderEnd(out);
        }
    }


    public static boolean isClientAbortException(Throwable ex)
    {
        if (ex != null)
        {
            if (ex.getClass().getName().endsWith("ClientAbortException") ||
                ex.getClass().getName().endsWith("FileUploadException"))
            {
                return true;
            }
            if (ex.getClass().equals(IllegalStateException.class) && "Cannot create a session after the response has been committed".equals(ex.getMessage()))
            {
                return true;
            }
            if (ex.getCause() != ex)
            {
                return isClientAbortException(ex.getCause());
            }
        }
        return false;
    }

    // This is called by SpringActionController (to display unhandled exceptions) and called directly by AuthFilter.doFilter() (to display startup errors and bypass normal request handling)
    public static ActionURL handleException(HttpServletRequest request, HttpServletResponse response, Throwable ex, String message, boolean startupFailure)
    {
        DbScope.rollbackAllTransactions();

        // First, get rid of RuntimeException, InvocationTargetException, etc. wrappers
        ex = unwrapException(ex);

        if (isClientAbortException(ex))
        {
            // The client dropped the connection. We don't care about this case,
            // and don't need to send an error back to the browser either.
            return null;
        }

        int responseStatus = HttpServletResponse.SC_OK;

        if (response.isCommitted())
        {
            // if we can't reset(), flushing might make it slightly less likely to send half-written attributes etc
            try {response.getOutputStream().flush();} catch (Exception x) {}
            try {response.getWriter().flush();} catch (Exception x) {}
            try {response.flushBuffer();} catch (Exception x) {}
        }

        // Do redirects before response.reset() otherwise we'll lose cookies (e.g., login page)
        if (ex instanceof RedirectException)
        {
            String url = ((RedirectException) ex).getURL();
            doErrorRedirect(response, url);
            return null;
        }

        if (!response.isCommitted())
        {
            try
            {
                response.reset();
            }
            catch (IllegalStateException x)
            {
                // This is fine, just can't clear the existing reponse as its
                // been at least partially written back to the client
            }
        }

        boolean resetResponse = true;
        boolean isForbiddenProject = false;  // Hack

        ErrorView errorView;

        // check for unauthorized guest, go to login
        if (ex instanceof UnauthorizedException && !(ex instanceof CSRFException))
        {
            User user = (User) request.getUserPrincipal();

            // If user has not logged in or agreed to terms, not really unauthorized yet...
            if (user.isGuest() || ex instanceof TermsOfUseException)
            {
                if (ex instanceof RequestBasicAuthException)
                {
                    response.setHeader("WWW-Authenticate", "Basic realm=\"" + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDescription() + "\"");
                    responseStatus = HttpServletResponse.SC_UNAUTHORIZED;
                    message = "You must log in to view this content.";
                    resetResponse = false;
                    ex = null;
                }
                else if (request.getMethod().equalsIgnoreCase("GET"))
                {
                    UnauthorizedException uae = (UnauthorizedException)ex;

                    ActionURL redirect;

                    if (uae instanceof TermsOfUseException)
                    {
                        redirect = PageFlowUtil.urlProvider(LoginUrls.class).getAgreeToTermsURL(HttpView.getContextContainer(), HttpView.getContextURLHelper());
                    }
                    else
                    {
                        redirect = PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(HttpView.getContextContainer(), HttpView.getContextURLHelper());
                    }

                    return redirect;
                }
            }

            // Hack so we can set the right buttons in ErrorView -- ex gets nulled out before we have the view 
            if (ex instanceof ForbiddenProjectException)
            {
                isForbiddenProject = true;
            }
        }

        if (ex instanceof NotFoundException || ex instanceof UnauthorizedException)
        {
            if (ex instanceof NotFoundException)
            {
                responseStatus = HttpServletResponse.SC_NOT_FOUND;
                if (ex instanceof NotFoundException && ex.getMessage() != null)
                    message = ex.getMessage();
                else
                    message = responseStatus + ": Page not Found";

                URLHelper url = (URLHelper)request.getAttribute(ViewServlet.ORIGINAL_URL_URLHELPER);
                if (null != url && null != url.getParameter("_docid"))
                {
                    SearchService ss = ServiceRegistry.get(SearchService.class);
                    if (null != ss)
                        ss.notFound(url);
                }
            }
            else
            {
                responseStatus = HttpServletResponse.SC_UNAUTHORIZED;
                message = ex.getMessage();
            }
            ex = null;
        }
        else if (ex instanceof SQLException)
        {
            responseStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            message = SqlDialect.GENERIC_ERROR_MESSAGE;

            if (ex instanceof BatchUpdateException)
            {
                if (null != ((BatchUpdateException)ex).getNextException())
                    ex = ((BatchUpdateException)ex).getNextException();
            }
        }

        if (null == message && null != ex)
        {
            responseStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            message = responseStatus + ": Unexpected server error";
        }

        errorView = ExceptionUtil.getErrorView(responseStatus, message, ex, request, startupFailure);

        if (responseStatus == HttpServletResponse.SC_NOT_FOUND)
        {
            _log.warn(null == message ? "" : message, ex);
        }
        else if (responseStatus != HttpServletResponse.SC_UNAUTHORIZED) //don't log unauthorized (basic-auth challenge)
        {
            _log.error("Unhandled exception: " + (null == message ? "" : message), ex);
        }

        if (isForbiddenProject)
        {
            // Not allowed in the project... don't offer Home or Folder buttons, provide "Stop Impersonating" button 
            errorView.setIncludeHomeButton(false);
            errorView.setIncludeFolderButton(false);
            errorView.setIncludeStopImpersonatingButton(true);
        }

        if (response.isCommitted())
        {
            // This is fine, just can't clear the existing response as it has
            // been at least partially written back to the client

            if (ex != null)
            {
                try
                {
                    response.getWriter().println("\"> --></script>");
                    response.getWriter().println();
                    response.getWriter().println();
                    response.getWriter().println("<pre>");
                    ex.printStackTrace(response.getWriter());
                    response.getWriter().println("</pre>");
                }
                catch (IOException e)
                {
                    // Give up at this point
                }
                catch (IllegalStateException e)
                {
                    // Give up at this point
                }
            }
        }
        else
        {
            try
            {
                if (resetResponse)
                {
                    response.reset();
                }
                response.setContentType("text/html");
                response.setStatus(responseStatus);
                errorView.render(request, response);
                return null;
            }
            catch (IllegalStateException x)
            {
            }
            catch (Exception x)
            {
                _log.error("Global.handleException", x);
            }
        }

        return null;
    }


    public static void doErrorRedirect(HttpServletResponse response, String url)
    {
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        response.setDateHeader("Expires", 0);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Location", url);

        // backup strategy!
        try
        {
            PrintWriter out = response.getWriter();
            out.println("\"> --></script><script type=\"text/javascript\">");
            out.println("window.location = '" + url + "';");
            out.println("</script>");
        }
        catch (IOException x)
        {
            _log.error("doErrorRedirect", x);
        }
    }



    public enum ExceptionInfo
    {
        ResolveURL,     // suggestion for where to fix this e.g. sourceQuery.view
        ResolveText,    // text to go with the ResolveURL
        HelpURL,
        DialectSQL,
        LabkeySQL,
        QueryName,
        QuerySchema,
        SkipMothershipLogging
    }


    private final static WeakHashMap<Throwable, HashMap<Enum,String>> _exceptionDecorations = new WeakHashMap<Throwable, HashMap<Enum, String>>();
    
    public static boolean decorateException(Throwable t, Enum key, String value, boolean overwrite)
    {
        t = unwrapException(t);
        synchronized (_exceptionDecorations)
        {
            HashMap<Enum,String> m = _exceptionDecorations.get(t);
            if (null == m)
                _exceptionDecorations.put(t, m = new HashMap<Enum,String>());
            if (overwrite || !m.containsKey(key))
            {
                m.put(key,value);
                return true;
            }
        }
        return false;
    }


    @NotNull
    public static Map<Enum,String> getExceptionDecorations(Throwable start)
    {
        HashMap<Enum,String> collect = new HashMap<Enum,String>();
        LinkedList<Throwable> list = new LinkedList<Throwable>();

        Throwable next = unwrapException(start);
        while (null != next)
        {
            list.addFirst(next);
            next = getCause(next);
        }

        synchronized (_exceptionDecorations)
        {
            for (Throwable th : list)
            {
                HashMap<Enum,String> m = _exceptionDecorations.get(th);
                if (null != m)
                    collect.putAll(m);
            }
        }
        return collect;
    }


    @Nullable
    public static String  getExceptionDecoration(Throwable t, Enum e)
    {
        // could optimize...
        return getExceptionDecorations(t).get(e);
    }


    @NotNull
    public static String getExtendedMessage(Throwable t)
    {
        StringBuffer sb = new StringBuffer(t.toString());
        for (Map.Entry<Enum,String> e : getExceptionDecorations(t).entrySet())
            sb.append("\n").append(e.getKey()).append("=").append(e.getValue());
        return sb.toString();
    }


    @Nullable
    public static Throwable getCause(Throwable t)
    {
        Throwable cause;
        if (t instanceof RuntimeSQLException)
            cause = ((RuntimeSQLException)t).getSQLException();
        else if (t instanceof ServletException)
            cause = ((ServletException)t).getRootCause();
        else if (t instanceof BatchUpdateException)
            cause = ((BatchUpdateException)t).getNextException();
        else
            cause = t.getCause();
        return cause==t ? null : cause;
    }
}
