/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.*;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.*;
import org.springframework.core.MethodParameter;
import org.springframework.validation.*;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.ServletRequestParameterPropertyValues;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.BaseCommandController;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * User: matthewb
 * Date: May 16, 2007
 * Time: 1:48:01 PM
 */
public abstract class BaseViewAction<FORM> extends BaseCommandController implements Controller, HasViewContext, HasPageConfig, Validator, PermissionCheckable
{
    ViewContext _context = null;
    PageConfig _pageConfig = null;
    PropertyValues _pvs;
    boolean _useBasicAuthentication = false;


    // shared construction code
    private void _BaseViewAction()
    {
        setValidator(this);
        setCommandName("form");
        setCacheSeconds(0);
    }


    protected BaseViewAction()
    {
        _BaseViewAction();

        String methodName = getCommandClassMethodName();

        if (null == methodName)
            return;

        // inspect to find form class
        Class typeBest = null;
        for (Method m : this.getClass().getMethods())
        {
            if (methodName.equals(m.getName()))
            {
                Class[] types = m.getParameterTypes();
                if (types.length < 1)
                    continue;
                Class typeCurrent = types[0];
                if (Object.class.equals(typeCurrent))
                    continue;
                assert null == getCommandClass() || typeCurrent.equals(getCommandClass());

                // Using templated classes to extend a base action can lead to multiple
                // versions of a method with acceptable types, so take the most extended
                // type we can find.
                if (typeBest == null || typeBest.isAssignableFrom(typeCurrent))
                    typeBest = typeCurrent;
            }
        }
        if (typeBest != null)
            setCommandClass(typeBest);
    }


    protected abstract String getCommandClassMethodName();


    protected BaseViewAction(Class<? extends FORM> commandClass)
    {
        _BaseViewAction();
        setCommandClass(commandClass);
    }


    protected void setUseBasicAuthentication(boolean use)
    {
        _useBasicAuthentication = use;
    }


    public void setProperties(PropertyValues pvs)
    {
        _pvs = pvs;
    }


    public void setProperties(Map m)
    {
        _pvs = new MutablePropertyValues(m);
    }

    /* Doesn't guarantee non-null, non-empty */
    public Object getProperty(String key, String d)
    {
        PropertyValue pv = _pvs.getPropertyValue(key);
        return pv == null ? d : pv.getValue();
    }

    public Object getProperty(Enum key)
    {
        PropertyValue pv = _pvs.getPropertyValue(key.name());
        return pv == null ? null : pv.getValue();
    }

    public Object getProperty(String key)
    {
        PropertyValue pv = _pvs.getPropertyValue(key);
        return pv == null ? null : pv.getValue();
    }
    

    public PropertyValues getPropertyValues()
    {
        return _pvs;
    }


    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (null == getPropertyValues())
            setProperties(new ServletRequestParameterPropertyValues(request));
        _context.setBindPropertyValues(getPropertyValues());
        return handleRequest();
    }


    public abstract ModelAndView handleRequest() throws Exception;


    public ViewContext getViewContext()
    {
        return _context;
    }


    public void setViewContext(ViewContext context)
    {
        _context = context;
    }


    public void setPageConfig(PageConfig page)
    {
        _pageConfig = page;
    }


    public PageConfig getPageConfig()
    {
        return _pageConfig;
    }


    public void setTitle(String title)
    {
        assert null != getPageConfig() : "action not initialized property";
        getPageConfig().setTitle(title);
    }


    public void setHelpTopic(String topicName)
    {
        setHelpTopic(new HelpTopic(topicName));
    }


    public void setHelpTopic(HelpTopic topic)
    {
        assert null != getPageConfig() : "action not initialized property";
        getPageConfig().setHelpTopic(topic);
    }


    protected Object newInstance(Class c)
    {
        try
        {
            return c == null ? null : c.newInstance();
        }
        catch (Exception x)
        {
            if (x instanceof RuntimeException)
                throw ((RuntimeException)x);
            else
                throw new RuntimeException(x);
        }
    }


    protected FORM getCommand(HttpServletRequest request) throws Exception
    {
        if (getCommandClass() == null)
        {
            return (FORM)new Object();
        }
        FORM command = (FORM)super.createCommand();

        if (command instanceof HasViewContext)
            ((HasViewContext)command).setViewContext(getViewContext());

        return command;
    }


    protected FORM getCommand() throws Exception
    {
        return getCommand(getViewContext().getRequest());
    }


    //
    // PARAMETER BINDING
    //
    // don't assume parameters always come from a request, use PropertyValues interface
    //

    public BindException defaultBindParameters(FORM form, PropertyValues params)
    {
        return defaultBindParameters(form, getCommandName(), params);
    }


    public static BindException defaultBindParameters(Object form, String commandName, PropertyValues params)
    {
        /* check for do-it-myself forms */
        if (form instanceof HasBindParameters)
        {
            return ((HasBindParameters)form).bindParameters(params);
        }
        
        /* 'regular' commandName handling */
        if (null != params.getPropertyValue(".oldValues"))
        {
            try
            {
                Object oldObject = PageFlowUtil.decodeObject((String)params.getPropertyValue(".oldValues").getValue());
                PropertyUtils.copyProperties(form, oldObject);
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }

        if (form instanceof DynaBean)
        {
            return simpleBindParameters(form, commandName, params);
        }
        else
        {
            return springBindParameters(form, commandName, params);
        }
    }


    public static BindException springBindParameters(Object command, String commandName, PropertyValues params)
    {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(command, commandName);
        ConvertHelper.getPropertyEditorRegistrar().registerCustomEditors(binder);
        BindingErrorProcessor defaultBEP = binder.getBindingErrorProcessor();
        binder.setBindingErrorProcessor(getBindingErrorProcessor(defaultBEP));
        binder.setFieldMarkerPrefix(SpringActionController.FIELD_MARKER);
        try
        {
            binder.bind(params);
            BindException errors = new NullSafeBindException(binder.getBindingResult());
            return errors;
        }
        catch (InvalidPropertyException x)
        {
            // Maybe we should propagate exception and return SC_BAD_REQUEST (in ExceptionUtil.handleException())
            // most POST handlers check erros.hasErrors(), but not all GET handlers do
            BindException errors = new BindException(command, commandName);
            errors.reject(SpringActionController.ERROR_MSG, "Error binding property: " + x.getPropertyName());
            return errors;
        }
    }


    static BindingErrorProcessor getBindingErrorProcessor(final BindingErrorProcessor defaultBEP)
    {
        return new BindingErrorProcessor()
        {
            public void processMissingFieldError(String missingField, BindingResult bindingResult)
            {
                defaultBEP.processMissingFieldError(missingField, bindingResult);
            }

            public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult)
            {
                Object newValue = ex.getPropertyChangeEvent().getNewValue();
                if (newValue instanceof String)
                    newValue = StringUtils.trimToNull((String)newValue);

                // convert NULL conversion errors to required errors
                if (null == newValue)
                    defaultBEP.processMissingFieldError(ex.getPropertyChangeEvent().getPropertyName(), bindingResult);
                else
                    defaultBEP.processPropertyAccessException(ex, bindingResult);
            }
        };
    }


    /*
     * This binder doesn't have much to offer over the standard spring data binding except that it will
     * handle DynaBeans.
     */
    public static BindException simpleBindParameters(Object command, String commandName, PropertyValues params)
    {
        //params = _fixupPropertyMap(params);

        BindException errors = new NullSafeBindException(command, "Form");

        // unfortunately ObjectFactory and BeanObjectFactory are not good about reporting errors
        // do this by hand
        for (PropertyValue pv : params.getPropertyValues())
        {
            String propertyName = pv.getName();
            Object value = pv.getValue();
            try
            {
                Object converted = value;
                Class propClass = PropertyUtils.getPropertyType(command, propertyName);
                if (null == propClass)
                    continue;
                if (value == null)
                {
                    /*  */
                }
                else if (propClass.isPrimitive())
                {
                    converted = ConvertUtils.convert(String.valueOf(value), propClass);
                }
                else if (propClass.isArray())
                {
                    if (value instanceof Collection)
                        value = ((Collection) value).toArray(new String[((Collection) value).size()]);
                    else if (!value.getClass().isArray())
                        value = new String[] {String.valueOf(value)};
                    converted = ConvertUtils.convert((String[])value, propClass);
                }
                PropertyUtils.setProperty(command, propertyName, converted);
            }
            catch (ConversionException x)
            {
                errors.addError(new FieldError(commandName, propertyName, value, true, new String[] {"ConversionError", "typeMismatch"}, null, "Could not convert to value: " + String.valueOf(value)));
            }
            catch (Exception x)
            {
                errors.addError(new ObjectError(commandName, new String[]{"Error"}, new Object[] {value}, x.getMessage()));
                Logger.getLogger(BaseViewAction.class).error("unexpected error", x);
            }
        }
        return errors;
    }

    protected Map<String,Object> _fixupPropertyMap(Map<String,Object> in)
    {
        Map<String,Object> out = new HashMap<String,Object>(in);

        /** see TableViewForm.setTypedValues() */
        for (Map.Entry<String,Object> entry : in.entrySet())
        {
            String propName = entry.getKey();
            Object o = entry.getValue();

            if (Character.isUpperCase(propName.charAt(0)))
            {
                out.remove(propName);
                propName = Introspector.decapitalize(propName);
                out.put(propName,o);
            }
        }

        out.remove("container");
        out.remove("user");
        return out;
    }

    public boolean supports(Class clazz)
    {
        return getCommandClass().isAssignableFrom(clazz);
    }


    /* for TableViewForm, uses BeanUtils to work with DynaBeans */
    static public class BeanUtilsPropertyBindingResult extends BeanPropertyBindingResult
    {
        public BeanUtilsPropertyBindingResult(Object target, String objectName)
        {
            super(target, objectName);
        }

        protected BeanWrapper createBeanWrapper()
        {
            return new BeanUtilsWrapperImpl((DynaBean)getTarget());
        }
    }

    static public class BeanUtilsWrapperImpl extends AbstractPropertyAccessor implements BeanWrapper
    {
        private Object object;

        public BeanUtilsWrapperImpl()
        {
            // registerDefaultEditors();
        }
        
        public BeanUtilsWrapperImpl(DynaBean target)
        {
            this();
            object = target;
        }

        public Object getPropertyValue(String propertyName) throws BeansException
        {
            try
            {
                return PropertyUtils.getProperty(object, propertyName);
            }
            catch (Exception e)
            {
                throw new NotReadablePropertyException(object.getClass(), propertyName);
            }
        }

        public void setPropertyValue(String propertyName, Object value) throws BeansException
        {
            try
            {
                PropertyUtils.setProperty(object, propertyName, value);
            }
            catch (Exception e)
            {
                throw new NotWritablePropertyException(object.getClass(), propertyName);
            }
        }

        public boolean isReadableProperty(String propertyName)
        {
            return true;
        }

        public boolean isWritableProperty(String propertyName)
        {
            return true;
        }

        public void setWrappedInstance(Object obj)
        {
            object = (DynaBean)obj;
        }

        public Object getWrappedInstance()
        {
            return object;
        }

        public Class getWrappedClass()
        {
            return object.getClass();
        }

        public PropertyDescriptor[] getPropertyDescriptors()
        {
            throw new UnsupportedOperationException();
        }

        public PropertyDescriptor getPropertyDescriptor(String propertyName) throws BeansException
        {
            throw new UnsupportedOperationException();
        }

        public Object convertIfNecessary(Object value, Class requiredType) throws TypeMismatchException
        {
            if (value == null)
                return null;
            return ConvertUtils.convert(String.valueOf(value), requiredType);
        }

        public Object convertIfNecessary(Object value, Class requiredType, MethodParameter methodParam) throws TypeMismatchException
        {
            return convertIfNecessary(value, requiredType);
        }
    }

    public void checkPermissions() throws TermsOfUseException, UnauthorizedException
    {
        checkPermissions(_useBasicAuthentication);
    }

    protected void checkPermissions(boolean useBasicAuth) throws TermsOfUseException, UnauthorizedException
    {
        // ideally, we should pass the bound FORM to getContextualRoles so that
        // actions can determine if the OwnerRole should apply, but this would require
        // a large amount of rework that is too much to consider now.
        //
        // If the action needs Basic Authentication.  Do the standard
        // permissions check, but ignore terms-of-use (non-web clients can't view/respond to terms of use page).  If user
        // is guest and unauthorized, then send a Basic Authentication request.
        try
        {
            checkPermissionsAndTermsOfUse(getClass(), getViewContext(), getContextualRoles());
        }
        catch (TermsOfUseException e)
        {
            if (useBasicAuth)
                return;
            throw e;
        }
        catch (UnauthorizedException e)
        {
            e.setUseBasicAuthentication(useBasicAuth);
            throw e;
        }
    }


    /**
     * Actions may provide a set of {@link Role}s used during permission checking
     * or null if no contextual roles apply.
     */
    @Nullable
    protected Set<Role> getContextualRoles()
    {
        return null;
    }

    public static void checkActionPermissions(Class<? extends Controller> actionClass, ViewContext context, Set<Role> contextualRoles) throws UnauthorizedException
    {
        String method = context.getRequest().getMethod();
        boolean isPOST = "POST".equals(method);

        Container c = context.getContainer();
        if (null == c)
        {
            String containerPath = context.getActionURL().getExtraPath();
            if (containerPath != null && containerPath.indexOf("/") != -1)
            {
                throw new NotFoundException("No such folder or workbook: " + containerPath);
            }
            else
            {
                throw new NotFoundException("No such project: " + containerPath);
            }
        }

        User user = context.getUser();
        if (c.isForbiddenProject(user))
            throw new ForbiddenProjectException();

        RequiresPermissionClass requiresPerm = actionClass.getAnnotation(RequiresPermissionClass.class);
        Set<Class<? extends Permission>> permissionsRequired = null;
        if (null != requiresPerm)
        {
            permissionsRequired = RoleManager.permSet(requiresPerm.value());
        }

        ContextualRoles rolesAnnotation = actionClass.getAnnotation(ContextualRoles.class);
        if (rolesAnnotation != null)
        {
            contextualRoles = RoleManager.mergeContextualRoles(context, rolesAnnotation.value(), contextualRoles);
        }
        
        // Special handling for admin console actions to support TroubleShooter role.  Only site admins can POST,
        // but those with AdminReadPermission (i.e., TroubleShooters) can GET. 
        boolean adminConsoleAction = actionClass.isAnnotationPresent(AdminConsoleAction.class);
        if (adminConsoleAction)
        {
            assert c.isRoot();  // TODO: HttpView.throwUnauthorized(); -- once we've done some testing with the assert

            if (isPOST)
            {
                if (!user.isAdministrator())
                {
                    throw new UnauthorizedException();
                }
            }
            else
            {
                permissionsRequired = RoleManager.permSet(AdminReadPermission.class);
            }
        }

        if (null != permissionsRequired)
        {
            SecurityPolicy policy = SecurityManager.getPolicy(c);
            if (!policy.hasOneOf(user, permissionsRequired, contextualRoles))
                throw new UnauthorizedException();
        }

        boolean requiresSiteAdmin = actionClass.isAnnotationPresent(RequiresSiteAdmin.class);
        if (requiresSiteAdmin && !user.isAdministrator())
        {
            throw new UnauthorizedException();
        }

        boolean requiresLogin = actionClass.isAnnotationPresent(RequiresLogin.class);
        if (requiresLogin && user.isGuest())
        {
            throw new UnauthorizedException();
        }

        CSRF csrfCheck = actionClass.getAnnotation(CSRF.class);
        if (null != csrfCheck && isPOST)
            CSRFUtil.validate(context.getRequest());

        boolean requiresNoPermission = actionClass.isAnnotationPresent(RequiresNoPermission.class);

        if (null == requiresPerm && !requiresSiteAdmin && !requiresLogin && !requiresNoPermission && !adminConsoleAction)
            throw new IllegalStateException("@RequiresPermissionClass, @RequiresSiteAdmin, @RequiresLogin, @RequiresNoPermission, or @AdminConsoleAction annotation is required on class " + actionClass.getName());

        // All permission checks have succeeded.  Now check for deprecated action.
        if (actionClass.isAnnotationPresent(DeprecatedAction.class))
            throw new DeprecatedActionException(actionClass);
    }

    public static void checkPermissionsAndTermsOfUse(Class<? extends Controller> actionClass, ViewContext context, Set<Role> contextualRoles)
            throws TermsOfUseException, UnauthorizedException
    {
        checkActionPermissions(actionClass, context, contextualRoles);

        boolean requiresTermsOfUse = !actionClass.isAnnotationPresent(IgnoresTermsOfUse.class);
        if (requiresTermsOfUse && !context.hasAgreedToTermsOfUse())
            throw new TermsOfUseException();
    }

    /**
     * @return a map from form element name to uploaded files
     */
    protected Map<String, MultipartFile> getFileMap()
    {
        if (getViewContext().getRequest() instanceof MultipartHttpServletRequest)
            return (Map<String, MultipartFile>)((MultipartHttpServletRequest)getViewContext().getRequest()).getFileMap();
        return Collections.emptyMap();
    }

    protected List<AttachmentFile> getAttachmentFileList()
    {
        return SpringAttachmentFile.createList(getFileMap());
    }
}
