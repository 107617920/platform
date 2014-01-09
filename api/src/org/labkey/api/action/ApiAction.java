/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.api.action;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Base class for API actions.
 *
 * User: Dave
 * Date: Feb 8, 2008
 * Time: 1:14:43 PM
 */
public abstract class ApiAction<FORM> extends BaseViewAction<FORM>
{
    private ApiResponseWriter.Format _reqFormat = null;
    private ApiResponseWriter.Format _respFormat = ApiResponseWriter.Format.JSON;
    private String _contentTypeOverride = null;
    private double _requestedApiVersion = -1;

    protected enum CommonParameters
    {
        apiVersion
    }

    public ApiAction()
    {
        setUseBasicAuthentication(true);
    }

    public ApiAction(Class<? extends FORM> formClass)
    {
        super(formClass);
        setUseBasicAuthentication(true);
    }

    protected String getCommandClassMethodName()
    {
        return "execute";
    }


    protected boolean isPost()
    {
        return "POST".equals(getViewContext().getRequest().getMethod());
    }


    public ModelAndView handleRequest() throws Exception
    {
        if (isPost())
            return handlePost();
        else
            return handleGet();
    }


    protected ModelAndView handleGet() throws Exception
    {
        return handlePost();
    }
    
    
    @SuppressWarnings("TryWithIdenticalCatches")
    public ModelAndView handlePost() throws Exception
    {
        getViewContext().getResponse().setHeader("X-Robots-Tag", "noindex");

        FORM form = null;
        BindException errors = null;

        try
        {
            String contentType = getViewContext().getRequest().getContentType();
            if (null != contentType && contentType.contains(ApiJsonWriter.CONTENT_TYPE_JSON))
            {
                _reqFormat = ApiResponseWriter.Format.JSON;
                JSONObject jsonObj;
                try
                {
                    jsonObj = getJsonObject();
                }
                catch (SocketTimeoutException x)
                {
                    ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
                    throw x;
                }
                catch (JSONException x)
                {
                    getViewContext().getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST, x.getMessage());
                    return null;
                }
                saveRequestedApiVersion(getViewContext().getRequest(), jsonObj);

                form = getCommand();
                errors = populateForm(jsonObj, form);
            }
            else
            {
                saveRequestedApiVersion(getViewContext().getRequest(), null);

                if (null != getCommandClass())
                {
                    errors = defaultBindParameters(getCommand(), getPropertyValues());
                    form = (FORM)errors.getTarget();
                }
            }

            if ("xml".equalsIgnoreCase(getViewContext().getRequest().getParameter("respFormat")))
            {
                _respFormat = ApiResponseWriter.Format.XML;
            }
            else if ("json_compact".equalsIgnoreCase(getViewContext().getRequest().getParameter("respFormat")))
            {
                _respFormat = ApiResponseWriter.Format.JSON_COMPACT;
            }

            //validate the form
            validate(form, errors);

            //if we had binding or validation errors,
            //return them without calling execute.
            if(isFailure(errors))
                createResponseWriter().write((Errors)errors);
            else
            {
                ApiResponse response = execute(form, errors);
                if (isFailure(errors))
                    createResponseWriter().write((Errors)errors);
                else if (null != response)
                    createResponseWriter().write(response);
            }
        }
        catch (BindException e)
        {
            createResponseWriter().write((Errors)e);
        }
        //don't log exceptions that result from bad inputs
        catch (BatchValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            createResponseWriter().write(e);
        }
        catch (ValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            createResponseWriter().write(e);
        }
        catch (QueryException | IllegalArgumentException |
                NotFoundException | InvalidKeyException | ApiUsageException e)
        {
            createResponseWriter().write(e);
        }
        catch (UnauthorizedException e)
        {
            e.setUseBasicAuthentication(_useBasicAuthentication);
            throw e;
        }
        catch (Exception e)
        {
            ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
            Logger.getLogger(ApiAction.class).warn("ApiAction exception: ", e);

            createResponseWriter().write(e);
        }

        return null;
    } //handleRequest()

    protected boolean isFailure(BindException errors)
    {
        return null != errors && errors.hasErrors();
    }

    protected double getApiVersion()
    {
        ApiVersion version = this.getClass().getAnnotation(ApiVersion.class);
        //default version is 8.3, since we made several changes in core code
        //to properly support API clients
        return null != version ? version.value() : 8.3;
    }


    boolean _empty(Object o)
    {
        return null == o || (o instanceof String && ((String)o).isEmpty());
    }


    private double saveRequestedApiVersion(HttpServletRequest request, JSONObject jsonObj)
    {
        Object o = null;
        
        if (null != jsonObj && jsonObj.has(CommonParameters.apiVersion.name()))
            o = jsonObj.get(CommonParameters.apiVersion.name());
        if (_empty(o))
            o = getProperty(CommonParameters.apiVersion.name());
        if (_empty(o))
            o = request.getHeader("LABKEY-" + CommonParameters.apiVersion.name());

        try
        {
            if (null == o)
                _requestedApiVersion = 0;
            else if (o instanceof Number)
                _requestedApiVersion = ((Number)o).doubleValue();
            else
                _requestedApiVersion = Double.parseDouble(o.toString());
        }
        catch (NumberFormatException x)
        {
            _requestedApiVersion = 0;
        }
        
        return _requestedApiVersion;
    }


    public double getRequestedApiVersion()
    {
        assert _requestedApiVersion >= 0;
        return _requestedApiVersion < 0 ? 0 : _requestedApiVersion;
    }


    protected JSONObject getJsonObject() throws Exception
    {
        //read the JSON into a buffer
        //unfortunately the json.org classes can't read directly from a stream!
        char[] buf = new char[2048];
        int chars;
        StringBuilder json = new StringBuilder();
        BufferedReader reader = getViewContext().getRequest().getReader();

        while((chars = reader.read(buf)) > 0)
            json.append(buf, 0, chars);

        String jsonString = json.toString();
        if(jsonString.isEmpty())
            return null;

        //deserialize the JSON
        return new JSONObject(jsonString);
    }

    protected BindException populateForm(JSONObject jsonObj, FORM form) throws Exception
    {
        if (null == jsonObj)
            return new NullSafeBindException(form, "form");

        if (form instanceof CustomApiForm)
        {
            ((CustomApiForm)form).bindProperties(jsonObj);
            return new NullSafeBindException(form, "form");
        }
        else
        {
            JsonPropertyValues values = new JsonPropertyValues(jsonObj);
            return defaultBindParameters(form, values);
        }
    }

    public static class JsonPropertyValues extends MutablePropertyValues
    {
        public JsonPropertyValues(JSONObject jsonObj) throws JSONException
        {
            addPropertyValues(jsonObj);
        }

        private void addPropertyValues(JSONObject jsonObj) throws JSONException
        {
            for (String key : jsonObj.keySet())
            {
                Object value = jsonObj.get(key);

                if (value instanceof JSONArray)
                {
                    value = ((JSONArray) value).toArray();
                }
                else if (value instanceof JSONObject)
                    throw new IllegalArgumentException("Nested objects and arrays are not supported at this time.");
                addPropertyValue(key, value);
            }
        }
    }

    public final void validate(Object form, Errors errors)
    {
        validateForm((FORM)form, errors);
    }

    /**
     * Override to validate the form bean and populate the Errors collection as necessary.
     * The default implementation does nothing, so override this method to perform validation.
     *
     * @param form The form bean
     * @param errors The errors collection
     */
    public void validateForm(FORM form, Errors errors)
    {
    }

    public ApiResponseWriter createResponseWriter() throws IOException
    {
        // Let the response format dictate how we write the response. Typically JSON, but not always.
        return _respFormat.createWriter(getViewContext().getResponse(), getContentTypeOverride());
    }

    public ApiResponseWriter.Format getResponseFormat()
    {
        return _respFormat;
    }

    public ApiResponseWriter.Format getRequestFormat()
    {
        return _reqFormat;
    }

    public String getContentTypeOverride()
    {
        return _contentTypeOverride;
    }

    public void setContentTypeOverride(String contentTypeOverride)
    {
        _contentTypeOverride = contentTypeOverride;
    }

    /**
     * Used to determine if the request originated from the client or server. Server-side scripts
     * use a mock request to invoke the action...
     */
    public boolean isServerSideRequest()
    {
        return getViewContext().getRequest() instanceof MockHttpServletRequest;
    }

    public abstract ApiResponse execute(FORM form, BindException errors) throws Exception;
}
