/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.util.emailTemplate;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Karl Lum
 * Date: Jan 15, 2007
 */
public abstract class EmailTemplate
{
    private static Pattern scriptPattern = Pattern.compile("\\^(.*?)\\^");
    private static List<ReplacementParam> _replacements = new ArrayList<>();
    private static final String FORMAT_DELIMITER = "|";

    /**
     * Distinguishes between the types of email that might be sent. Additionally, used to ensure correct encoding
     * of substitutions when generating the full email.
     */
    public enum ContentType
    {
        Plain
        {
            @Override
            public String format(String sourceValue, ContentType sourceType)
            {
                if (sourceType != Plain)
                {
                    // We don't support converting from HTML to plain
                    throw new IllegalArgumentException("Unable to convert from " + sourceType + " to Plain");
                }
                return sourceValue;
            }
        },
        HTML
        {
            @Override
            public String format(String sourceValue, ContentType sourceType)
            {
                if (sourceType == HTML)
                {
                    return sourceValue;
                }
                return PageFlowUtil.filter(sourceValue, true, true);
            }
        };

        /** Render the given sourceValue into the target (this) type, based on the sourceType */
        public abstract String format(String sourceValue, ContentType sourceType);
    }

    public enum Scope
    {
        Site
        {
            @Override
            public boolean isEditableIn(Container c)
            {
                return c.isRoot();
            }
        },
        SiteOrFolder
        {
            @Override
            public boolean isEditableIn(Container c)
            {
                return true;
            }};

        public abstract boolean isEditableIn(Container c);
    }

    /** The format of the email to be generated */
    @NotNull private final ContentType _contentType;
    @NotNull private final String _name;
    private String _body;
    private String _subject;
    private String _description;
    private int _priority = 50;
    /** Scope is the locations in which the user should be able to edit this template. It should always be the same
     * for a given subclass, regardless of the instances */
    private Scope _scope = Scope.Site;
    /**
     * Container in which this template is stored. Null for the default templates defined in code, the root
     * container for site level templates, or a specific folder.
     */
    private Container _container = null;

    static
    {
        _replacements.add(new ReplacementParam("organizationName", "Organization name (look and feel settings)"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getCompanyName();}
        });
        _replacements.add(new ReplacementParam("siteShortName", "Header short name"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getShortName();}
        });
        _replacements.add(new ReplacementParam("contextPath", "Web application context path"){
            public String getValue(Container c) {return AppProps.getInstance().getContextPath();}
        });
        _replacements.add(new ReplacementParam("supportLink", "Page where users can request support"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getReportAProblemPath();}
        });
        _replacements.add(new ReplacementParam("systemDescription", "Header description"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getDescription();}
        });
        _replacements.add(new ReplacementParam("systemEmail", "From address for system notification emails"){
            public String getValue(Container c) {return LookAndFeelProperties.getInstance(c).getSystemEmailAddress();}
        });
        _replacements.add(new ReplacementParam("currentDateTime", "Current date and time of the server"){
            public Object getValue(Container c) {return new Date();}
        });
        _replacements.add(new ReplacementParam("folderName", "Name of the folder that generated the email, if it is scoped to a folder"){
            public String getValue(Container c) {return c.isRoot() ? null : c.getName();}
        });
        _replacements.add(new ReplacementParam("folderPath", "Full path of the folder that generated the email, if it is scoped to a folder"){
            public String getValue(Container c) {return c.isRoot() ? null : c.getPath();}
        });
        _replacements.add(new ReplacementParam("folderURL", "URL to the folder that generated the email, if it is scoped to a folder"){
            public String getValue(Container c) {return c.isRoot() ? null : PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c).getURIString();}
        });
        _replacements.add(new ReplacementParam("homePageURL", "The home page of this installation"){
            public String getValue(Container c) {
                return ActionURL.getBaseServerURL();   // TODO: Use AppProps.getHomePageUrl() instead?
            }
        });
    }

    public EmailTemplate(@NotNull String name)
    {
        this(name, "", "", "", ContentType.Plain);
    }

    public EmailTemplate(@NotNull String name, String subject, String body, String description)
    {
        this(name, subject, body, description, ContentType.Plain);
    }

    public EmailTemplate(@NotNull String name, String subject, String body, String description, @NotNull ContentType contentType)
    {
        _name = name;
        _subject = subject;
        _body = body;
        _description = description;
        _contentType = contentType;
    }

    @NotNull public String getName(){return _name;}
    public String getSubject(){return _subject;}
    public void setSubject(String subject){_subject = subject;}
    public String getBody(){return _body;}
    public void setBody(String body){_body = body;}
    public void setPriority(int priority){_priority = priority;}
    public int getPriority(){return _priority;}
    public String getDescription(){return _description;}
    public void setDescription(String description){_description = description;}
    public Scope getEditableScopes(){return _scope;}
    public void setEditableScopes(Scope scope){_scope = scope;}
    public Container getContainer(){return _container;}
    /* package */ void setContainer(Container c){_container = c;}

    public boolean isValid(String[] error)
    {
        try {
            _validate(_subject);
            _validate(_body);
            return true;
        }
        catch (Exception e)
        {
            if (error != null && error.length >= 1)
                error[0] = e.getMessage();
            return false;
        }
    }

    protected boolean _validate(String text) throws Exception
    {
        if (text != null)
        {
            Matcher m = scriptPattern.matcher(text);
            while (m.find())
            {
                String value = m.group(1);
                if (!isValidReplacement(value))
                    throw new IllegalArgumentException("Invalid template, the replacement parameter: " + value + " is unknown.");
            }
        }
        return true;
    }

    protected boolean isValidReplacement(String paramNameAndFormat)
    {
        String paramName = getParameterName(paramNameAndFormat);
        for (ReplacementParam param : getValidReplacements())
        {
            if (param.getName().equalsIgnoreCase(paramName))
                return true;
        }
        return false;
    }

    private String getParameterName(String paramNameAndFormat)
    {
        int i = paramNameAndFormat.indexOf(FORMAT_DELIMITER);
        if (i != -1)
        {
            return paramNameAndFormat.substring(0, i);
        }
        return paramNameAndFormat;
    }

    private String getFormat(String paramNameAndFormat)
    {
        int i = paramNameAndFormat.indexOf(FORMAT_DELIMITER);
        if (i != -1)
        {
            return paramNameAndFormat.substring(i + 1);
        }
        return null;
    }

    public String getReplacement(Container c, String paramNameAndFormat)
    {
        String paramName = getParameterName(paramNameAndFormat);
        for (ReplacementParam param : getValidReplacements())
        {
            if (param.getName().equalsIgnoreCase(paramName))
            {
                String formattedValue;
                Object value = param.getValue(c);
                if (value == null || "".equals(value))
                {
                    formattedValue = "";
                }
                else
                {
                    if (value instanceof String)
                    {
                        // This may not be quite right, but seems OK given all of our current parameters
                        // We format just the value itself, not any surrounding text, but we can only safely do
                        // this for String values. That is the overwhelming majority of values, and the non-strings
                        // are Dates which are unlikely to be rendered using a format that needs encoding.
                        value = _contentType.format((String) value, param.getContentType());
                    }

                    String format = getFormat(paramNameAndFormat);
                    if (format != null)
                    {
                        Formatter formatter = new Formatter();
                        formatter.format(format, value);
                        formattedValue = formatter.toString();
                    }
                    else
                    {
                        formattedValue = value.toString();
                    }
                }
                return formattedValue;
            }
        }
        return null;
    }

    public String renderSubject(Container c)
    {
        return render(c, getSubject());
    }

    public String renderBody(Container c)
    {
        return render(c, getBody());
    }

    protected String render(Container c, String text)
    {
        StringBuilder sb = new StringBuilder();
        Matcher m = scriptPattern.matcher(text);
        int start;
        int end = 0;
        while (m.find())
        {
            start = m.start();
            String value = m.group(1);
            sb.append(text.substring(end, start));
            sb.append(getReplacement(c, value));
            end = m.end();
        }
        sb.append(text.substring(end));
        return sb.toString();
    }

    public List<ReplacementParam> getValidReplacements()
    {
        return _replacements;
    }

    public static abstract class ReplacementParam implements Comparable<ReplacementParam>
    {
        @NotNull
        private final String _name;
        private final String _description;
        private final ContentType _contentType;

        public ReplacementParam(@NotNull String name, String description)
        {
            this(name, description, ContentType.Plain);
        }

        public ReplacementParam(@NotNull String name, String description, ContentType contentType)
        {
            _name = name;
            _description = description;
            _contentType = contentType;
        }

        @NotNull public String getName(){return _name;}
        public String getDescription(){return _description;}
        public abstract Object getValue(Container c);

        /** Sort alphabetically by parameter name */
        @Override
        public int compareTo(ReplacementParam o)
        {
            return _name.compareToIgnoreCase(o._name);
        }

        /** @return the formatting of the content - HTML, plaintext, etc */
        public ContentType getContentType()
        {
            return _contentType;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testPlainToPlain()
        {
            assertEquals("plain \n<>\"", ContentType.Plain.format("plain \n<>\"", ContentType.Plain));
        }

        @Test
        public void testPlainToHTML()
        {
            assertEquals("plain <br>\n&lt;&gt;&quot;", ContentType.HTML.format("plain \n<>\"", ContentType.Plain));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testHMLToPlain()
        {
            ContentType.Plain.format("plain <>\"", ContentType.HTML);
        }

        @Test
        public void testHMLToHTML()
        {
            assertEquals("plain <br/>&lt;&gt;&quot;", ContentType.HTML.format("plain <br/>&lt;&gt;&quot;", ContentType.HTML));
        }
    }
}
