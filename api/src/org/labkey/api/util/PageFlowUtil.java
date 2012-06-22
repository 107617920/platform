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
package org.labkey.api.util;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryParam;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.settings.TemplateResourceHandler;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebTheme;
import org.labkey.api.view.WebThemeManager;
import org.labkey.api.view.template.ClientDependency;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.WebUtils;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;


public class PageFlowUtil
{
    public enum TransformFormat
    {
        html,
        xml
    }

    private static Logger _log = Logger.getLogger(PageFlowUtil.class);
    private static final String _newline = System.getProperty("line.separator");

    private static final Pattern urlPatternStart = Pattern.compile("((http|https|ftp|mailto)://\\S+).*");

    /**
     * Default parser class.
     */
    protected static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";

    static public final String NONPRINTING_ALTCHAR = "~";

    static public String filterXML(String s)
    {
        return filter(s,false,false);
    }

    /** HTML encode a string */
    static public HString filter(HString s)
    {
        if (null == s)
            return HString.EMPTY;

        return new HString(filter(s.getSource()), false);
    }


    /** HTML encode a string */
    static public HString filter(HStringBuilder s)
    {
        if (null == s)
            return HString.EMPTY;

        return new HString(filter(s.getSource()), false);
    }


    /** HTML encode a string */
    static public String filter(String s, boolean encodeSpace, boolean encodeLinks)
    {
        if (null == s || 0 == s.length())
            return "";

        int len = s.length();
        StringBuilder sb = new StringBuilder(2 * len);
        boolean newline = false;

        for (int i = 0; i < len; ++i)
        {
            char c = s.charAt(i);

            if (!Character.isWhitespace(c))
                newline = false;
            else if ('\r' == c || '\n' == c)
                newline = true;

            switch (c)
            {
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#039;");    // works for xml and html
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '\n':
                    if (encodeSpace)
                        sb.append("<br>\n");
                    else
                        sb.append(c);
                    break;
                case '\r':
                    break;
                case '\t':
                    if (!encodeSpace)
                        sb.append(c);
                    else if (newline)
                        sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                    else
                        sb.append("&nbsp; &nbsp; ");
                    break;
                case ' ':
                    if (encodeSpace && newline)
                        sb.append("&nbsp;");
                    else
                        sb.append(' ');
                    break;
                case 'f':
                case 'h':
                case 'm':
                    if (encodeLinks)
                    {
                        String sub = s.substring(i);
                        if ((c == 'f' || c == 'h' || c == 'm') && StringUtilsLabKey.startsWithURL(sub))
                        {
                            Matcher m = urlPatternStart.matcher(sub);
                            if (m.find())
                            {
                                String href = m.group(1);
                                if (href.endsWith("."))
                                    href = href.substring(0, href.length() - 1);
                                // for html/xml careful of " and "> and "/>
                                int lastQuote = Math.max(href.lastIndexOf("\""),href.lastIndexOf("\'"));
                                if (lastQuote >= href.length()-3)
                                    href = href.substring(0, lastQuote);
                                String filterHref = filter(href, false, false);
                                sb.append("<a href=\"").append(filterHref).append("\">").append(filterHref).append("</a>");
                                i += href.length() - 1;
                                break;
                            }
                        }
                    }
                    sb.append(c);
                    break;
                default:
                    if (c >= ' ')
                        sb.append(c);
                    else
                    {
                        if (c == 0x08) // backspace (e.g. xtandem output)
                            break;
                        sb.append(NONPRINTING_ALTCHAR);
                    }
                    break;
            }
        }

        return sb.toString();
    }

    /** HTML encode an object (using toString()) */
    public static String filter(Object o)
    {
        return filter(o == null ? null : o.toString());
    }

    /**
     * HTML encode a string
     */
    public static String filter(String s)
    {
        return filter(s, false, false);
    }


    /** HTML encode a string */
    static public String filter(String s, boolean translateWhiteSpace)
    {
        return filter(s, translateWhiteSpace, false);
    }


    /**
     * put quotes around a JavaScript string, and HTML encode that.
     */
    public static String filterQuote(Object value)
    {
        if (value == null)
            return "null";
        String ret = PageFlowUtil.filter("\"" + PageFlowUtil.groovyString(value.toString()) + "\"");
        ret = ret.replace("&#039;", "\\&#039;");
        return ret;
    }

    /**
     * Creates a JavaScript string literal of an HTML escaped value.
     *
     * Ext, for example, will use the 'id' config parameter as an attribute value in an XTemplate.
     * The string value is inserted directly into the dom and so should be HTML encoded.
     *
     * @param s String to escaped
     * @return The JavaScript string literal of the HTML escaped value.
     */
    // For example, given the string: "\"'>--></script><script type=\"text/javascript\">alert(\"8(\")</script>"
    // the method will return: "'&quot;&#039;&gt;--&gt;&lt;/script&gt;&lt;script type=&quot;text/javascript&quot;&gt;alert(&quot;8(&quot;)&lt;/script&gt;'"
    public static String qh(String s)
    {
        return PageFlowUtil.jsString(PageFlowUtil.filter(s));
    }

    static public String jsString(CharSequence cs)
    {
        if (cs == null)
            return "''";

        String s;
        if (cs instanceof HString)
            s = ((HString)cs).getSource();
        else
            s = cs.toString();

        // UNDONE: what behavior do we want for tainted strings? IllegalArgumentException()?
        if (cs instanceof Taintable && ((Taintable)cs).isTainted())
        {
            if (s.toLowerCase().contains("<script"))
                return "''";
        }
        return jsString(s);
    }


    static public String jsString(String s)
    {
        if (s == null)
            return "''";

        StringBuilder js = new StringBuilder(s.length() + 10);
        js.append("'");
        int len = s.length();
        for (int i = 0 ; i<len ; i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '\\':
                    js.append("\\\\");
                    break;
                case '\n':
                    js.append("\\n");
                    break;
                case '\r':
                    js.append("\\r");
                    break;
                case '<':
                    js.append("\\x3C");
                    break;
                case '>':
                    js.append("\\x3E");
                    break;
                case '\'':
                    js.append("\\'");
                    break;
                case '\"':
                    js.append("\\\"");
                    break;
                default:
                    js.append(c);
                    break;
            }
        }
        js.append("'");
        return js.toString();
    }

    //used to output strings from Java in Groovy script.
    static public String groovyString(String s)
    {
        //replace single backslash
        s = s.replaceAll("\\\\", "\\\\\\\\");
        //replace double quote
        s = s.replaceAll("\"", "\\\\\"");
        return s;
    }

    @SuppressWarnings({"unchecked"})
    static Pair<String, String>[] _emptyPairArray = new Pair[0];   // Can't delare generic array

    public static Pair<String, String>[] fromQueryString(String query)
    {
        return fromQueryString(query, "UTF-8");
    }

    public static Pair<String, String>[] fromQueryString(String query, String encoding)
    {
        if (null == query || 0 == query.length())
            return _emptyPairArray;

        if (null == encoding)
            encoding = "UTF-8";

        List<Pair> parameters = new ArrayList<Pair>();
        if (query.startsWith("?"))
            query = query.substring(1);
        String[] terms = query.split("&");

        try
        {
            for (String term : terms)
            {
                if (0 == term.length())
                    continue;

                // NOTE: faster to decode entire term all at once, but key may contain '=' char
                int ind = term.indexOf('=');
                String key;
                String val;
                if (ind == -1)
                {
                    key = URLDecoder.decode(term.trim(), encoding);
                    val = "";
                }
                else
                {
                    key = URLDecoder.decode(term.substring(0, ind).trim(), encoding);
                    val = URLDecoder.decode(term.substring(ind + 1).trim(), encoding);
                }

                parameters.add(new Pair<String,String>(key, val));
            }
        }
        catch (UnsupportedEncodingException x)
        {
            throw new IllegalArgumentException(encoding, x);
        }

        //noinspection unchecked
        return parameters.toArray(new Pair[parameters.size()]);
    }


    public static Map<String, String> mapFromQueryString(String queryString)
    {
        Pair<String, String>[] pairs = fromQueryString(queryString);
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (Pair<String, String> p : pairs)
            m.put(p.getKey(), p.getValue());

        return m;
    }


    public static String toQueryString(Collection<? extends Map.Entry<?,?>> c)
    {
        return toQueryString(c, false);
    }


    public static String toQueryString(Collection<? extends Map.Entry<?,?>> c, boolean allowSubstSyntax)
    {
        if (null == c || c.isEmpty())
            return null;
        String strAnd = "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?,?> entry : c)
        {
            sb.append(strAnd);
            Object key = entry.getKey();
            if (null == key)
                continue;
            Object v = entry.getValue();
            String value = v == null ? "" : String.valueOf(v);
            sb.append(encode(String.valueOf(key)));
            sb.append('=');
            if (allowSubstSyntax && value.length()>3 && value.startsWith("${") && value.endsWith("}"))
                sb.append(value);
            else
                sb.append(encode(value));
            strAnd = "&";
        }
        return sb.toString();
    }


    public static String toQueryString(PropertyValues pvs)
    {
        if (null == pvs || pvs.isEmpty())
            return null;
        String strAnd = "";
        StringBuilder sb = new StringBuilder();
        for (PropertyValue entry : pvs.getPropertyValues())
        {
            Object key = entry.getName();
            if (null == key)
                continue;
            String encKey = encode(String.valueOf(key));
            Object v = entry.getValue();
            if (v == null || v instanceof String || !v.getClass().isArray())
            {
                sb.append(strAnd);
                sb.append(encKey);
                sb.append('=');
                sb.append(encode(v==null?"":String.valueOf(v)));
                strAnd = "&";
            }
            else
            {
                Object[] a = (Object[])v;
                for (Object o : a)
                {
                    sb.append(strAnd);
                    sb.append(encKey);
                    sb.append('=');
                    sb.append(encode(o==null?"":String.valueOf(o)));
                    strAnd = "&";
                }
            }
        }
        return sb.toString();
    }


    public static <T> Map<T, T> map(T... args)
    {
        HashMap<T, T> m = new HashMap<T, T>();
        for (int i = 0; i < args.length; i += 2)
            m.put(args[i], args[i + 1]);
        return m;
    }


    public static Map<String, Object> mapInsensitive(Object... args)
    {
        Map<String,Object> m = new CaseInsensitiveHashMap<Object>();
        for (int i = 0; i < args.length; i += 2)
            m.put(String.valueOf(args[i]), args[i + 1]);
        return m;
    }


    public static <T> Set<T> set(T... args)
    {
        HashSet<T> s = new HashSet<T>();

        if (null != args)
            s.addAll(Arrays.asList(args));

        return s;
    }


    public static ArrayList pairs(Object... args)
    {
        ArrayList<Pair> list = new ArrayList<Pair>();
        for (int i = 0; i < args.length; i += 2)
            list.add(new Pair<Object,Object>(args[i], args[i + 1]));
        return list;
    }


    private static final Pattern pattern = Pattern.compile("\\+");


    /**
     * URL Encode string.
     * NOTE! this should be used on parts of a url, not an entire url
     */
    public static String encode(String s)
    {
        if (null == s)
            return "";
        try
        {
            return pattern.matcher(URLEncoder.encode(s, "UTF-8")).replaceAll("%20");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    /**
     * URL decode a string.
     */
    public static String decode(String s)
    {
        try
        {
            return null==s ? "" : URLDecoder.decode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    /**
     * Encode path URL parts, preserving path separators.
     * @param path The raw path to encode.
     * @return An encoded version of the path parameter.
     */
    public static String encodePath(String path)
    {
        String[] parts = path.split("/");
        String ret = "";
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
                ret += "/";
            ret += encode(parts[i]);
        }
        return ret;
    }

    // Cookie helper function -- loops through Cookie array and returns matching value (or defaultValue if not found)
    public static String getCookieValue(Cookie[] cookies, String cookieName, @Nullable String defaultValue)
    {
        if (null != cookies)
            for (Cookie cookie : cookies)
            {
                if (cookieName.equals(cookie.getName()))
                    return (cookie.getValue());
            }
        return (defaultValue);
    }


    /**
     * boolean controlling whether or not we compress {@link ObjectOutputStream}s when we render them in HTML forms.
     *
     */
    static private final boolean COMPRESS_OBJECT_STREAMS = true;
    static public String encodeObject(Object o) throws IOException
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStream osCompressed;
        if (COMPRESS_OBJECT_STREAMS)
        {
            osCompressed = new DeflaterOutputStream(byteArrayOutputStream);
        }
        else
        {
            osCompressed = byteArrayOutputStream;
        }
        ObjectOutputStream oos = new ObjectOutputStream(osCompressed);
        oos.writeObject(o);
        oos.close();
        osCompressed.close();
        return new String(Base64.encodeBase64(byteArrayOutputStream.toByteArray(), true));
    }


    public static Object decodeObject(String s) throws IOException
    {
        s = StringUtils.trimToNull(s);
        if (null == s)
            return null;

        try
        {
            byte[] buf = Base64.decodeBase64(s.getBytes());
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buf);
            InputStream isCompressed;

            if (COMPRESS_OBJECT_STREAMS)
            {
                isCompressed = new InflaterInputStream(byteArrayInputStream);
            }
            else
            {
                isCompressed = byteArrayInputStream;
            }
            ObjectInputStream ois = new ObjectInputStream(isCompressed);
            return ois.readObject();
        }
        catch (ClassNotFoundException x)
        {
            throw new IOException(x.getMessage());
        }
    }


    public static int[] toInts(Collection<String> strings)
    {
        return toInts(strings.toArray(new String[strings.size()]));
    }


    public static int[] toInts(String[] strings)
    {
        int[] result = new int[strings.length];
        for (int i = 0; i < strings.length; i++)
        {
            result[i] = Integer.parseInt(strings[i]);
        }
        return result;
    }


    private static MimeMap _mimeMap = new MimeMap();

    public static String getContentTypeFor(String filename)
    {
        String contentType = _mimeMap.getContentTypeFor(filename);
        if (null == contentType)
        {
            contentType = "application/octet-stream";
        }
        return contentType;
    }

    public static String getContentTypeFor(File file)
    {
        MediaType type = getMediaTypeFor(file);
        if (type == null || type.toString() == null)
        {
            return "application/octet-stream";
        }
        return type.toString();
    }

    /**
     * Uses Tika to examine the contents of a file to detect the content type
     * of a file.
     * @return MediaType object
     */
    public static MediaType getMediaTypeFor(File file)
    {
        try {
            DefaultDetector detector = new DefaultDetector();
            Metadata metaData = new Metadata();

            // use the metadata to hint at the type for a faster lookup
            metaData.add(Metadata.RESOURCE_NAME_KEY, file.getName());
            metaData.add(Metadata.CONTENT_TYPE, PageFlowUtil.getContentTypeFor(file.getName()));

            return detector.detect(TikaInputStream.get(file), new Metadata());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets up the response to stream back a file. The content type is inferred by the filename extension.
     */
    public static void prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, String filename, boolean asAttachment)
    {
        if (filename == null)
            throw new IllegalArgumentException("filename cannot be null");

        _prepareResponseForFile(response, responseHeaders, getContentTypeFor(filename), filename, asAttachment);
    }

    /**
     * Sets up the response to stream back a file. The content type is detected by the file contents.
     */
    public static void prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, File file, boolean asAttachment)
    {
        if (file == null)
            throw new IllegalArgumentException("file cannot be null");

        String fileName = file.getName();
        MediaType mediaType = getMediaTypeFor(file);
        String contentType = getContentTypeFor(fileName);

        if (mediaType != null && mediaType.compareTo(MediaType.parse(contentType)) != 0)
        {
            try
            {
                MimeType mimeType = MimeTypes.getDefaultMimeTypes().forName(mediaType.toString());
                contentType = mediaType.toString();

                // replace the extension of the filename with one that matches the content type
                String ext = FileUtil.getExtension(fileName);
                if (ext != null && mimeType != null)
                {
                    fileName = fileName.substring(0, fileName.length() - (ext.length() + 1)) + mimeType.getExtension();
                }
            }
            catch (MimeTypeException e)
            {
                throw new RuntimeException(e);
            }
        }
        _prepareResponseForFile(response, responseHeaders, contentType, fileName, asAttachment);
    }

    private static void _prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, String fileContentType, String fileName, boolean asAttachment)
    {
        String contentType = responseHeaders.get("Content-Type");
        if (null == contentType && null != fileContentType)
            contentType = fileContentType;
        response.reset();
        response.setContentType(contentType);
        if (asAttachment)
        {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        }
        else
        {
            response.setHeader("Content-Disposition", "filename=\"" + fileName + "\"");
        }
        for (Map.Entry<String, String> entry : responseHeaders.entrySet())
            response.setHeader(entry.getKey(), entry.getValue());
    }

    /**
     * Read the file and stream it to the browser through the response.
     *
     * @param detectContentType If set to true, then the content type is detected, else it is inferred from the extension
     * of the file name.
     * @throws IOException
     */
    public static void streamFile(HttpServletResponse response, File file, boolean asAttachment, boolean detectContentType) throws IOException
    {
        if (detectContentType)
            streamFile(response, Collections.<String, String>emptyMap(), file, asAttachment);
        else
        {
            try
            {
                streamFile(response, Collections.<String, String>emptyMap(), file.getName(), new FileInputStream(file), asAttachment);
            }
            catch (FileNotFoundException e)
            {
                throw new NotFoundException(file.getName());
            }
        }
    }

    public static void streamFile(HttpServletResponse response, File file, boolean asAttachment) throws IOException
    {
        streamFile(response, file, asAttachment, false);
    }


    /**
     * Read the file and stream it to the browser through the response. The content type of the file is detected
     * from the contents of the file.
     *
     * @throws IOException
     */
    public static void streamFile(HttpServletResponse response, @NotNull Map<String, String> responseHeaders, File file, boolean asAttachment) throws IOException
    {
        InputStream is = new FileInputStream(file);
        try
        {
            prepareResponseForFile(response, responseHeaders, file, asAttachment);
            ServletOutputStream out = response.getOutputStream();
            FileUtil.copyData(is, out);
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }

    /**
     * Read the file and stream it to the browser through the response. The content type of the file is detected
     * from the file name extension.
     *
     * @throws IOException
     */
    public static void streamFile(HttpServletResponse response, @NotNull Map<String, String> responseHeaders, String name, InputStream is, boolean asAttachment) throws IOException
    {
        try
        {
            prepareResponseForFile(response, responseHeaders, name, asAttachment);
            ServletOutputStream out = response.getOutputStream();
            FileUtil.copyData(is, out);
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }


    public static void streamFileBytes(HttpServletResponse response, String filename, byte[] bytes, boolean asAttachment) throws IOException
    {
        prepareResponseForFile(response, Collections.<String, String>emptyMap(), filename, asAttachment);
        response.getOutputStream().write(bytes);
    }


    // Fetch the contents of a text file, and return it in a String.
    public static String getFileContentsAsString(File aFile)
    {
        StringBuilder contents = new StringBuilder();
        BufferedReader input = null;

        try
        {
            input = new BufferedReader(new FileReader(aFile));
            String line;
            while ((line = input.readLine()) != null)
            {
                contents.append(line);
                contents.append(_newline);
            }
        }
        catch (FileNotFoundException e)
        {
            _log.error(e);
            contents.append("File not found");
            contents.append(_newline);
        }
        catch (IOException e)
        {
            _log.error(e);
        }
        finally
        {
            IOUtils.closeQuietly(input);
        }
        return contents.toString();
    }


    public static class Content
    {
        public Content(String s)
        {
            this(s, null, System.currentTimeMillis());
        }

        public Content(String s, @Nullable byte[] e, long m)
        {
            content = s;
            encoded = e;
            if (null == e && null != s)
                encoded = s.getBytes();
            modified = m;
        }

        public Content copy()
        {
            Content ret = new Content(content, encoded, modified);
            ret.dependencies = dependencies;
            ret.compressed = compressed;
            return ret;
        }

        public Object dependencies;
        public String content;
        public byte[] encoded;
        public byte[] compressed;
        public long modified;

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Content content1 = (Content) o;

            if (modified != content1.modified) return false;
            if (content != null ? !content.equals(content1.content) : content1.content != null) return false;
            if (dependencies != null ? !dependencies.equals(content1.dependencies) : content1.dependencies != null)
                return false;
            if (!Arrays.equals(encoded, content1.encoded)) return false;
            //if (!Arrays.equals(compressed, content1.compressed)) return false;
            return true;
        }

        @Override
        public int hashCode()
        {
            int result = dependencies != null ? dependencies.hashCode() : 0;
            result = 31 * result + (content != null ? content.hashCode() : 0);
            result = 31 * result + (encoded != null ? Arrays.hashCode(encoded) : 0);
            //result = 31 * result + (compressed != null ? Arrays.hashCode(compressed) : 0);
            result = 31 * result + (int) (modified ^ (modified >>> 32));
            return result;
        }
    }


    // Marker class for caching absence of content -- can't use a single marker object because of dependency handling.
    public static class NoContent extends Content
    {
        public NoContent(Object dependsOn)
        {
            super(null);
            dependencies = dependsOn;
        }
    }


    public static Content getViewContent(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        final StringWriter writer = new StringWriter();
        HttpServletResponse sresponse = new HttpServletResponseWrapper(response)
            {
                public PrintWriter getWriter()
                {
                    return new PrintWriter(writer);
                }
            };
        mv.getView().render(mv.getModel(), request, sresponse);
        String sheet = writer.toString();
        return new Content(sheet);
    }


    public static void sendContent(HttpServletRequest request, HttpServletResponse response, Content content, String contentType) throws IOException
    {
        // TODO content.getContentType()
        response.setContentType(contentType);
        response.setDateHeader("Expires", HeartBeat.currentTimeMillis() + TimeUnit.DAYS.toMillis(35));
        response.setHeader("Cache-Control", "private");
        response.setHeader("Pragma", "cache");
        response.setDateHeader("Last-Modified", content.modified);

        if (!checkIfModifiedSince(request, content.modified))
        {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        if (StringUtils.trimToEmpty(request.getHeader("Accept-Encoding")).contains("gzip") && null != content.compressed)
        {
            response.setHeader("Content-Encoding", "gzip");
            response.getOutputStream().write(content.compressed);
        }
        else
        {
            response.getOutputStream().write(content.encoded);
        }
    }


    /**
     * TODO: This code needs to be shared with DavController.checkModifiedSince
     *
     * CONSIDER: implementing these actions directly via WebdavResolver using something
     * like the SymbolicLink class.
     *
     * ref 10499
     */
    private static boolean checkIfModifiedSince(HttpServletRequest request, long lastModified)
    {
        try
        {
            long headerValue = request.getDateHeader("If-Modified-Since");
            if (headerValue != -1)
            {
                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null))
                {
                    if (lastModified < headerValue + 1000)
                    {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    return false;
                    }
                }
            }
        }
        catch (IllegalArgumentException illegalArgument)
        {
            return true;
        }
        return true;
    }


	// UNDONE: Move to FileUtil
    // Fetch the contents of an input stream, and return in a String.
    public static String getStreamContentsAsString(InputStream is)
    {
		return getReaderContentsAsString(new BufferedReader(new InputStreamReader(is)));
    }


	public static String getReaderContentsAsString(BufferedReader reader)
	{
		StringBuilder contents = new StringBuilder();
		String line;
		try
		{
			while ((line = reader.readLine()) != null)
			{
				contents.append(line);
				contents.append(_newline);
			}
		}
		catch (IOException e)
		{
			_log.error("getStreamContentsAsString", e);
		}
		finally
		{
			IOUtils.closeQuietly(reader);
		}
		return contents.toString();
	}


    // Fetch the contents of an input stream, and return it in a list.
    public static List<String> getStreamContentsAsList(InputStream is) throws IOException
    {
        return getStreamContentsAsList(is, false);
    }


    // Fetch the contents of an input stream, and return it in a list, skipping comment lines is skipComments == true.
    public static List<String> getStreamContentsAsList(InputStream is, boolean skipComments) throws IOException
    {
        List<String> contents = new ArrayList<String>();
        BufferedReader input = new BufferedReader(new InputStreamReader(is));

        try
        {
            String line;
            while ((line = input.readLine()) != null)
                if (!skipComments || !line.startsWith("#"))
                    contents.add(line);
        }
        finally
        {
            IOUtils.closeQuietly(input);
        }

        return contents;
    }

    public static boolean empty(String str)
    {
        return null == str || str.trim().length() == 0;
    }


    static Pattern patternPhone = Pattern.compile("((1[\\D]?)?\\(?(\\d\\d\\d)\\)?[\\D]*)?(\\d\\d\\d)[\\D]?(\\d\\d\\d\\d)");

    public static String formatPhoneNo(String s)
    {
        s = StringUtils.trimToNull(s);
        if (null == s)
            return "";
        Matcher m = patternPhone.matcher(s);
        if (!m.find())
            return s;
        //for (int i=0 ; i<=m.groupCount() ; i++) System.err.println(i + " " + m.group(i));
        StringBuffer sb = new StringBuffer(20);
        m.appendReplacement(sb, "");
        String area = m.group(3);
        String exch = m.group(4);
        String num = m.group(5);
        if (null != area && 0 < area.length())
            sb.append("(").append(area).append(") ");
        sb.append(exch).append("-").append(num);
        m.appendTail(sb);
        return sb.toString();
    }


    // Generates JavaScript that redirects to a new location when Enter is pressed.  Use this on pages that have
    // button links but don't submit a form.
    public static String generateRedirectOnEnter(ActionURL url)
    {
        return "\n<script type=\"text/javascript\">\n" +
                "document.onkeydown = keyListener;\n" +
                "function keyListener(e)" +
                "{\n" +
                "   if (!e)\n" +
                "   {\n" +
                "      //for IE\n" +
                "      e = window.event;\n" +
                "   }\n" +
                "   if (13 == e.keyCode)\n" +
                "   {\n" +
                "      document.location = \"" + PageFlowUtil.filter(url) + "\";\n" +
                "   }\n" +
                "}\n" +
                "</script>\n";
    }


    public static String generateBackButton()
    {
        return generateBackButton("Back");
    }

    public static String generateBackButton(String text)
    {
        return generateButton(text, "#", "window.history.back(); return false;");
    }

    /*
     * Renders a span wrapped in a link (<a>)
     * Consider: is there any way to name this method in such a way as to
     * make the order of parameters unambiguous?
     */
    public static String generateButton(String text, String href)
    {
        return generateButton(text, href, null);
    }

    public static String generateButton(String text, String href, @Nullable String onClick)
    {
        return generateButton(text, href, onClick, "");
    }

    public static String generateButton(String text, String href, String onClick, String attributes)
    {
        return generateButtonHtml(filter(text), href, onClick, attributes);
    }
    
    public static String generateButtonHtml(String html, String href, String onClick, String attributes)
    {
        return "<a class=\"labkey-button\" href=\"" + filter(href) + "\"" +
                " onClick=\"if (this.className.indexOf('labkey-disabled-button') != -1) return false; " + (onClick == null ? "" : filter(onClick)) + "\"" +
                (attributes != null ? " " + attributes : "") +
                "><span>" + html + "</span></a>";
    }

    public static String generateButton(String text, URLHelper href)
    {
        return generateButton(text, href, null);
    }

    public static String generateButton(String text, URLHelper href, @Nullable String onClick)
    {
        // 11525 : NPE caused by generateButton.
        if (href == null)
            return generateButton(text, "", onClick);
        return generateButton(text, href.toString(), onClick);
    }

    public static String generateButton(String text, URLHelper href, @Nullable String onClick, String attributes)
    {
        // 11525 : NPE caused by generateButton.
        if (href == null)
            return generateButton(text, "", onClick, attributes);
        return generateButton(text, href.toString(), onClick, attributes);
    }

    /* Renders an input of type submit wrapped in a span */
    public static String generateSubmitButton(String text)
    {
        return generateSubmitButton(text, null);
    }

    public static String generateSubmitButton(String text, @Nullable String onClickScript)
    {
        return generateSubmitButton(text, onClickScript, null);
    }

    public static String generateSubmitButton(String text, String onClick, @Nullable String attributes)
    {
        return generateSubmitButton(text, onClick, attributes, true);
    }

    public static String generateSubmitButton(String text, @Nullable String onClick, @Nullable String attributes, boolean enabled)
    {
        return generateSubmitButton(text, onClick, attributes, enabled, false);
    }

    public static String generateSubmitButton(String text, @Nullable String onClick, @Nullable String attributes, boolean enabled, boolean disableOnClick)
    {
        String id = GUID.makeGUID();
        char quote = getUsedQuoteSymbol(onClick); // we're modifying the javascript, so need to use whatever quoting the caller used

        String checkDisabled = "if (this.className.indexOf(" + quote + "labkey-disabled-button" + quote + ") != -1) return false; ";
        String submitCode = "submitForm(document.getElementById(" + quote + id + quote + ").form); return false;";

        String onClickMethod;

        if (disableOnClick)
        {
            String replaceClass = "Ext.get(this).replaceClass(" + quote + "labkey-button" + quote + ", " + quote + "labkey-disabled-button" + quote + ");";
            onClick = onClick != null ? onClick + ";" + replaceClass : replaceClass;
        }

        if (onClick == null || "".equals(onClick))
            onClickMethod = checkDisabled + submitCode;
        else
            onClickMethod = checkDisabled + "this.form = document.getElementById(" + quote + id + quote + ").form; if (isTrueOrUndefined(function() {" + onClick + "}.call(this))) " +  submitCode;

        StringBuilder sb = new StringBuilder();

        sb.append("<input type=\"submit\" style=\"display: none;\" id=\"");
        sb.append(id);
        sb.append("\">");

        if (enabled)
            sb.append("<a class=\"labkey-button\"");
        else
            sb.append("<a class=\"labkey-disabled-button\"");

        sb.append(" href=\"#\"");

        sb.append(" onclick=\"").append(filter(onClickMethod)).append("\"");

        if (attributes != null)
            sb.append(" ").append(" ").append(attributes);

        sb.append("><span>").append(filter(text)).append("</span></a>");

        return sb.toString();
    }

    /* Renders a span and a drop down arrow image wrapped in a link */
    public static String generateDropDownButton(String text, String href, String onClick, @Nullable Map<String, String> attributes)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<a class=\"labkey-menu-button\" href=\"").append(filter(href)).append("\"");
        sb.append(" onClick=\"if (this.className.indexOf('labkey-disabled-button') != -1) return false; ").append(onClick == null ? "" : filter(onClick)).append("\"");
        if (attributes != null)
        {
            for (String attribute : attributes.keySet())
            {
                String value = attributes.get(attribute);
                sb.append(filter(attribute)).append("=\"").append(filter(value)).append("\"");
            }
        }
        sb.append("><span>");
        sb.append(filter(text));
        sb.append("</span></a>");

        return sb.toString();
    }

    /* Renders a span and a drop down arrow image wrapped in a link */
    public static String generateDropDownButton(String text, String href, String onClick)
    {
        return generateDropDownButton(text, href, onClick, null);
    }

    /* Renders text and a drop down arrow image wrapped in a link not of type labkey-button */
    public static String generateDropDownTextLink(String text, String href, String onClick, boolean bold, String offset, String id)
    {
        return "<a class=\"labkey-menu-text-link\" style=\"" + (bold ? "font-weight: bold;" : "") + "\" href=\"" + filter(href) + "\"" +
                " onClick=\"if (this.className.indexOf('labkey-disabled-button') != -1) return false; " + (onClick == null ? "" : filter(onClick)) + "\"" +
                (id == null ? "" : " id=\"" + filter(id) + "PopupLink\"") + "><span" +
                (id == null ? "" : " id=\"" + filter(id) + "PopupText\"") + ">" + text + "</span>&nbsp;<img src=\"" + HttpView.currentView().getViewContext().getContextPath() +
                "/_images/text_link_arrow.gif\" style=\"position:relative; background-color:transparent; width:10px; height:auto; top:" + offset +"px; right:0;\"></a>";
    }

    /* Renders image and a drop down wrapped in an unstyled link */
    public static String generateDropDownImage(String text, String href, String onClick, String imageSrc, String imageId, Integer imageHeight, Integer imageWidth)
    {
        return "<a href=\"" + filter(href) +"\"" +
            " onClick=\"if (this.className.indexOf('labkey-disabled-button') != -1) return false; " + (onClick == null ? "" : filter(onClick)) + "\"" +
            "><img id=\"" + imageId + "\" title=\"" + text + "\"src=\"" + imageSrc + "\" " +
            (imageHeight == null ? "" : " height=\"" + imageHeight + "\"") + (imageWidth == null ? "" : " width=\"" + imageWidth + "\"") + "/></a>";
    }

    /* Renders a lightly colored inactive button, or in other words, a disabled span wrapped in a link of type labkey-disabled-button */
    public static String generateDisabledButton(String text)
    {
        return "<a class=\"labkey-disabled-button\" disabled><span>" + filter(text) + "</span></a>";
    }

    /* Renders a lightly colored inactive button */
    public static String generateDisabledSubmitButton(String text, String onClick, String attributes)
    {
        return generateSubmitButton(text, onClick, attributes, false);
    }

    /**
     * If the provided text uses ", return '. If it uses ', return ".
     * This is useful to quote javascript.
     */
    public static char getUnusedQuoteSymbol(String text)
    {
        if (text == null || text.equals(""))
            return '"';

        int singleQuote = text.indexOf('\'');
        int doubleQuote = text.indexOf('"');
        if (doubleQuote == -1 || (singleQuote != -1 && singleQuote <= doubleQuote))
            return '"';
        return '\'';
    }

    public static char getUsedQuoteSymbol(String text)
    {
        char c = getUnusedQuoteSymbol(text);
        if (c == '"')
            return '\'';
        return '"';
    }

    public static String textLink(String text, String href, String id)
    {
        return textLink(text, href, null, id);
    }

    public static String textLink(String text, String href)
    {
        return textLink(text, href, null, null);
    }

    public static String textLink(String text, HString href, String onClickScript, String id)
    {
        return "<a class='labkey-text-link' href=\"" + filter(href) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + filter(text) + "</a>";
    }

    @Deprecated
    public static String textLink(String text, String href, @Nullable String onClickScript, @Nullable String id)
    {
        return textLink(text, href, onClickScript, id, Collections.<String, String>emptyMap());
    }

    public static String textLink(String text, ActionURL url, String onClickScript, String id)
    {
        return textLink(text, url, onClickScript, id, Collections.<String, String>emptyMap());
    }

    @Deprecated
    public static String textLink(String text, String href, @Nullable String onClickScript, @Nullable String id, Map<String, String> properties)
    {
        String additions = "";

        for (Map.Entry<String, String> entry : properties.entrySet())
        {
            additions += entry.getKey() + "=\"" + entry.getValue() + "\" ";
        }

        return "<a class='labkey-text-link' " + additions + "href=\"" + filter(href) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + filter(text) + "</a>";
    }

    public static String textLink(String text, ActionURL url, String onClickScript, String id, Map<String, String> properties)
    {
        String additions = "";

        for (Map.Entry<String, String> entry : properties.entrySet())
        {
            additions += entry.getKey() + "=\"" + entry.getValue() + "\" ";
        }

        return "<a class='labkey-text-link' " + additions + "href=\"" + filter(url) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + filter(text) + "</a>";
    }

    public static String textLink(String text, ActionURL url)
    {
        return textLink(text, url.getLocalURIString(), null, null);
    }

    public static String textLink(String text, ActionURL url, String id)
    {
        return textLink(text, url.getLocalURIString(), null, id);
    }

    public static String helpPopup(String title, String helpText)
    {
        return helpPopup(title, helpText, false);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText)
    {
        return helpPopup(title, helpText, htmlHelpText, 0);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, int width)
    {
        String questionMarkHtml = "<span class=\"labkey-help-pop-up\">?</span>";
        return helpPopup(title, helpText, htmlHelpText, questionMarkHtml, width);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, 0, null);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, String onClickScript)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, 0, onClickScript);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, int width)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, width, null);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, int width, String onClickScript)
    {
        if (title == null && !htmlHelpText)
        {
            // use simple tooltip
            if (onClickScript == null)
                onClickScript = "return false";

            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" onClick=\"").append(onClickScript).append("\" title=\"");
            link.append(filter(helpText));
            link.append("\">").append(linkHtml).append("</a>");
            return link.toString();
        }
        else
        {
            StringBuilder showHelpDivArgs = new StringBuilder("this, ");
            showHelpDivArgs.append(filter(jsString(filter(title)), true)).append(", ");
            // The value of the javascript string literal is used to set the innerHTML of an element.  For this reason, if
            // it is text, we escape it to make it HTML.  Then, we have to escape it to turn it into a javascript string.
            // Finally, since this is script inside of an attribute, it must be HTML escaped again.
            showHelpDivArgs.append(filter(jsString(htmlHelpText ? helpText : filter(helpText, true))));
            if (width != 0)
                showHelpDivArgs.append(", ").append(filter(jsString(filter(String.valueOf(width) + "px"))));
            if (onClickScript == null)
            {
                onClickScript = "return showHelpDiv(" + showHelpDivArgs + ");";
            }
            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" onClick=\"");
            link.append(onClickScript);
            link.append("\" onMouseOut=\"return hideHelpDivDelay();\" onMouseOver=\"return showHelpDivDelay(");
            link.append(showHelpDivArgs).append(");\"");
            link.append(">").append(linkHtml).append("</a>");
            return link.toString();
        }
    }


    /**
     * helper for script validation
     */
    public static String convertHtmlToXml(String html, Collection<String> errors)
    {
        return tidy(html, true, errors);
    }


    static Pattern scriptPattern = Pattern.compile("(<script.*?>)(.*?)(</script>)", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);

    public static Document convertHtmlToDocument(final String html, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setDropEmptyParas(false); // radeox wikis use <p/> -- don't remove them
        tidy.setTrimEmptyElements(false); // keeps tidy from converting <p/> to <br><br>

        // TIDY does not property parse the contents of script tags!
        // see bug 5007
        // CONSIDER: fix jtidy see ParserImpl$ParseScript
        Map<String,String> scriptMap = new HashMap<String,String>();
        StringBuffer stripped = new StringBuffer(html.length());
        Matcher scriptMatcher = scriptPattern.matcher(html);
        int unique = html.hashCode();
        int count = 0;

        while (scriptMatcher.find())
        {
            count++;
            String key = "{{{" + unique + ":::" + count + "}}}";
            String match = scriptMatcher.group(2);
            scriptMap.put(key,match);
            scriptMatcher.appendReplacement(stripped, "$1" + key + "$3");
        }
        scriptMatcher.appendTail(stripped);

        StringWriter err = new StringWriter();
        try
        {
            // parse wants to use streams
            tidy.setErrout(new PrintWriter(err));
            Document doc = tidy.parseDOM(new ByteArrayInputStream(stripped.toString().getBytes("UTF-8")), null);

            // fix up scripts
            if (null != doc && null != doc.getDocumentElement())
            {
                NodeList nl = doc.getDocumentElement().getElementsByTagName("script");
                for (int i=0 ; i<nl.getLength() ; i++)
                {
                    Node script = nl.item(i);
                    NodeList childNodes = script.getChildNodes();
                    if (childNodes.getLength() != 1)
                        continue;
                    Node child = childNodes.item(0);
                    if (!(child instanceof CharacterData))
                        continue;
                    String contents = ((CharacterData)child).getData();
                    String replace = scriptMap.get(contents);
                    if (null == replace)
                        continue;
                    doc.createTextNode(replace);
                    script.removeChild(childNodes.item(0));
                    script.appendChild(doc.createTextNode(replace));
                }
            }

            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return doc;
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static String convertNodeToHtml(Node node) throws TransformerException, IOException
    {
        return convertNodeToString(node, TransformFormat.html);
    }

    public static String convertNodeToXml(Node node) throws TransformerException, IOException
    {
        return convertNodeToString(node, TransformFormat.xml);
    }

    public static String convertNodeToString(Node node, TransformFormat format) throws TransformerException, IOException
    {
        try
        {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.METHOD, format.toString());
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            t.transform(new DOMSource(node), new StreamResult(out));
            out.close();

            return new String(out.toByteArray(), "UTF-8").trim();
        }
        catch (TransformerFactoryConfigurationError e)
        {
            throw new RuntimeException("There was a problem creating the XML transformer factory." +
                    " If you specified a class name in the 'javax.xml.transform.TransformerFactory' system property," +
                    " please ensure that this class is included in the classpath for web application.", e);
        }
    }


    public static String tidy(final String html, boolean asXML, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        if (asXML)
            tidy.setXHTML(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8"); // utf8
        tidy.setOutputEncoding("UTF-8"); // utf8
        tidy.setDropEmptyParas(false); // allow <p/> in html wiki pages

        StringWriter err = new StringWriter();

        try
        {
            // parse wants to use streams
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tidy.setErrout(new PrintWriter(err));
            tidy.parse(new ByteArrayInputStream(html.getBytes("UTF-8")), out);
            tidy.getErrout().close();
            String errorString = err.toString();

            for (String error : errorString.split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }

            // Provide a generic error when JTidy flips out and doesn't report the actual error
            String genericError = "This document has errors that must be fixed";

            if (errors.isEmpty() && errorString.contains(genericError))
                errors.add(genericError);

            return new String(out.toByteArray(), "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static String tidyXML(final String xml, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        tidy.setXmlOut(true);
        tidy.setXmlTags(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8"); // utf8
        tidy.setOutputEncoding("UTF-8"); // utf8

        StringWriter err = new StringWriter();

        try
        {
            // parse wants to use streams
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tidy.setErrout(new PrintWriter(err));
            tidy.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")), out);
            tidy.getErrout().close();

            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }

            return new String(out.toByteArray(), "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    private static void parserSetFeature(XMLReader parser, String feature, boolean b)
    {
        try
        {
            parser.setFeature(feature, b);
        }
        catch (SAXNotSupportedException e)
        {
            _log.error("parserSetFeature", e);
        }
        catch (SAXNotRecognizedException e)
        {
            _log.error("parserSetFeature", e);
        }
    }


    public static String getStandardIncludes(Container c)
    {
        return getStandardIncludes(c, null, null);
    }

    public static String getStandardIncludes(Container c, @Nullable String userAgent)
    {
        return getStandardIncludes(c, userAgent, null);
    }

    // UNDONE: use a user-agent parsing library
    public static String getStandardIncludes(Container c, @Nullable String userAgent, LinkedHashSet<ClientDependency> resources)
    {
        StringBuilder sb = getFaviconIncludes(c);
        sb.append(getStylesheetIncludes(c, userAgent, resources));
        sb.append(getLabkeyJS(resources));
        sb.append(getJavaScriptIncludes(resources));
        return sb.toString();
    }


    public static StringBuilder getFaviconIncludes(Container c)
    {
        StringBuilder sb = new StringBuilder();

        ResourceURL faviconURL = TemplateResourceHandler.FAVICON.getURL(c);

        sb.append("    <link rel=\"shortcut icon\" href=\"");
        sb.append(PageFlowUtil.filter(faviconURL));
        sb.append("\">\n");

        sb.append("    <link rel=\"icon\" href=\"");
        sb.append(PageFlowUtil.filter(faviconURL));
        sb.append("\">\n");

        return sb;
    }


    public static String getStylesheetIncludes(Container c)
    {
        return getStylesheetIncludes(c, null);
    }

    public static String getStylesheetIncludes(Container c, @Nullable String userAgent)
    {
        return getStylesheetIncludes(c, userAgent, null);
    }

    /** */
    public static String getStylesheetIncludes(Container c, @Nullable String userAgent, @Nullable LinkedHashSet<ClientDependency> resources)
    {
        boolean useLESS = null != HttpView.currentRequest().getParameter("less");
        WebTheme theme = WebThemeManager.getTheme(c);

        CoreUrls coreUrls = urlProvider(CoreUrls.class);
        StringBuilder sb = new StringBuilder();

        Formatter F = new Formatter(sb);
        String link = useLESS ? "    <link href=\"%s\" type=\"text/x-less\" rel=\"stylesheet\">\n" : "    <link href=\"%s\" type=\"text/css\" rel=\"stylesheet\">\n";

        F.format(link, AppProps.getInstance().getContextPath() + "/" + extJsRoot + "/resources/css/ext-all.css");
        F.format(link, Path.parse(AppProps.getInstance().getContextPath() + resolveExtThemePath(c)));
        F.format(link, PageFlowUtil.filter(new ResourceURL(theme.getStyleSheet(), ContainerManager.getRoot())));

        ActionURL rootCustomStylesheetURL = coreUrls.getCustomStylesheetURL();

        if (!c.isRoot())
        {
            /* Add the themeStylesheet */
            if (coreUrls.getThemeStylesheetURL(c) != null)
                F.format(link, PageFlowUtil.filter(coreUrls.getThemeStylesheetURL(c)));
            else
            {
                /* In this case a themeStylesheet was not found in a subproject to default to the root */
                if (coreUrls.getThemeStylesheetURL() != null)
                    F.format(link, PageFlowUtil.filter(coreUrls.getThemeStylesheetURL()));
            }
            ActionURL containerCustomStylesheetURL = coreUrls.getCustomStylesheetURL(c);

            /* Add the customStylesheet */
            if (null != containerCustomStylesheetURL)
                F.format(link, PageFlowUtil.filter(containerCustomStylesheetURL));
            else
            {
                if (null != rootCustomStylesheetURL)
                    F.format(link, PageFlowUtil.filter(rootCustomStylesheetURL));
            }
        }
        else
        {
            /* Add the root themeStylesheet */
            if (coreUrls.getThemeStylesheetURL() != null)
                F.format(link, PageFlowUtil.filter(coreUrls.getThemeStylesheetURL()));

            /* Add the root customStylesheet */
            if (null != rootCustomStylesheetURL)
                F.format(link, PageFlowUtil.filter(rootCustomStylesheetURL));
        }

        ResourceURL printStyleURL = new ResourceURL("printStyle.css", ContainerManager.getRoot());
        sb.append("    <link href=\"");
        sb.append(filter(printStyleURL));
        sb.append("\" type=\"text/css\" rel=\"stylesheet\" media=\"print\">\n");

        if (resources != null)
        {
            for (ClientDependency r : resources)
            {
                for (String script : (r.getCssPaths(AppProps.getInstance().isDevMode())))
                {
                    sb.append("<link href=\"");
                    sb.append(AppProps.getInstance().getContextPath() + "/");
                    sb.append(filter(script));
                    sb.append("\" type=\"text/css\" rel=\"stylesheet\">");
                }
            }
        }
        return sb.toString();
    }

    static final String extJsRoot = "ext-3.4.0";
    static final String extDebug = extJsRoot + "/ext-all-debug.js";
    static final String extMin = extJsRoot + "/ext-all.js";
    static final String extBaseDebug = extJsRoot + "/adapter/ext/ext-base-debug.js";
    static final String extBase = extJsRoot + "/adapter/ext/ext-base.js";
    static final String ext4ThemeRoot = "labkey-ext-theme";

    static String clientDebug = "clientapi/clientapi.js";
    static String clientMin = "clientapi/clientapi.min.js";

    public static String extJsRoot()
    {
        return extJsRoot;
    }

    public static String ext4ThemeRoot()
    {
        return ext4ThemeRoot;
    }

    public static String resolveExtThemePath(Container container)
    {
        String path = "/" + ext4ThemeRoot() + "/resources/css/ext";
        String themeName = WebTheme.DEFAULT.getFriendlyName();

        WebTheme theme = WebThemeManager.getTheme(container);

        // Custom Theme -- TODO: Should have a better way to lookup built-in themes
        if (!theme.isEditable())
        {
            themeName = theme.getFriendlyName();
        }

        // Each built-in theme must have a corresponging labkey-ext-theme-<theme name>.scss
        path += "-" + themeName.toLowerCase() + ".css";
        return path;
    }

    private static void explodedExtPaths(Map<String, JSONObject> packages, String pkgDep, Set<String> scripts)
    {
        JSONObject dependency = packages.get(pkgDep);
        if (dependency == null)
            return;

        // Remove package so it won't be included twice.
        packages.put(pkgDep, null);

        if (dependency.has("pkgDeps"))
        {
            JSONArray array = dependency.getJSONArray("pkgDeps");
            for (int i = 0; i < array.length(); i++)
                explodedExtPaths(packages, array.getString(i), scripts);
        }

        for (JSONObject fileInclude : dependency.getJSONArray("fileIncludes").toJSONObjectArray())
        {
            scripts.add(extJsRoot + "/" + fileInclude.getString("path") + fileInclude.getString("text"));
        }
    }

    /**
     * Returns the default scripts included on all pages.  ClientDependency will handle dev/production
     * mode differences
     */
    public static LinkedHashSet<ClientDependency> getDefaultJavaScriptPaths()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromString("Ext3.lib.xml"));
        resources.add(ClientDependency.fromString("clientapi.lib.xml"));
        resources.add(ClientDependency.fromString("util.js"));
        return resources;
    }

    /**
     * Used by CombinedJavascriptAction only - it's possible this can be depreciated
     */
    public static void getJavaScriptPaths(Set<String> scripts, Set<String> included)
    {
        boolean explodedExt = AppProps.getInstance().isDevMode() && false;
        boolean explodedClient = AppProps.getInstance().isDevMode();

        LinkedHashSet<ClientDependency> resources = getDefaultJavaScriptPaths();
        if (resources != null)
        {
            for (ClientDependency r : resources) {
                if(AppProps.getInstance().isDevMode())
                {
                    scripts.addAll(r.getJsPaths(true));
                    included.addAll(r.getJsPaths(true));
                }
                else
                {
                    scripts.addAll(r.getJsPaths(false));
                    //include both production and devmode scripts for requiresScript()
                    included.addAll(r.getJsPaths(true));
                    included.addAll(r.getJsPaths(false));
                }
            }
        }

    }

//    private static String[] getClientExploded()
//    {
//        List<String> files = new ArrayList<String>();
//        WebdavResource dir = WebdavService.get().getRootResolver().lookup(Path.parse("/clientapi"));
//
//        FileType js = new FileType(".js", FileType.gzSupportLevel.NO_GZ);
//        for (WebdavResource r : dir.list())
//        {
//            File f = r.getFile();
//            if(js.isType(f) && !f.getName().startsWith("clientapi"))
//                files.add("clientapi/" + f.getName());
//        }
//        return files.toArray(new String[files.size()]);
//    }

    public static String getLabkeyJS()
    {
        return getLabkeyJS(new LinkedHashSet<ClientDependency>());
    }

    public static String getLabkeyJS(LinkedHashSet<ClientDependency> resources)
    {
        String contextPath = AppProps.getInstance().getContextPath();
        String serverHash = getServerSessionHash();

        StringBuilder sb = new StringBuilder();

        sb.append("    <script src=\"").append(contextPath).append("/labkey.js?").append(serverHash).append("\" type=\"text/javascript\"></script>\n");
        sb.append("    <script type=\"text/javascript\">\n");
        sb.append("        LABKEY.init(").append(jsInitObject(resources)).append(");\n");
        sb.append("    </script>\n");

        // Include client-side error reporting scripts only if necessary and as early as possible.
        if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP) &&
            AppProps.getInstance().getExceptionReportingLevel() != ExceptionReportingLevel.NONE)
        {
            sb.append("    <script src=\"").append(contextPath).append("/stacktrace-0.3.js").append("\" type=\"text/javascript\"></script>\n");
            sb.append("    <script src=\"").append(contextPath).append("/mothership.js?").append(serverHash).append("\" type=\"text/javascript\"></script>\n");
        }

        return sb.toString();
    }

    public static String getJavaScriptIncludes(LinkedHashSet<ClientDependency> extraResources)
    {
        String contextPath = AppProps.getInstance().getContextPath();
        String serverHash = getServerSessionHash();

        /**
          * scripts: the scripts that should be explicitly included
          * included: the scripts that are implicitly included, which will include the component scripts on a minified library.
          */
        LinkedHashSet<String> scripts = new LinkedHashSet<String>();
        LinkedHashSet<String> includes = new LinkedHashSet<String>();

        LinkedHashSet<ClientDependency> resources = getDefaultJavaScriptPaths();
        if (extraResources != null)
            resources.addAll(extraResources);

        if (resources != null)
        {
            for (ClientDependency r : resources) {
                if(AppProps.getInstance().isDevMode())
                {
                    scripts.addAll(r.getJsPaths(true));
                    includes.addAll(r.getJsPaths(true));
                }
                else
                {
                    scripts.addAll(r.getJsPaths(false));
                    //include both production and devmode scripts for requiresScript()
                    includes.addAll(r.getJsPaths(true));
                    includes.addAll(r.getJsPaths(false));
                }
            }
        }
        StringBuilder sb = new StringBuilder();

        sb.append("    <script type=\"text/javascript\">\n        LABKEY.loadedScripts(");
        String comma = "";
        for (String s : includes)
        {
            sb.append(comma).append(jsString(s));
            comma = ",";
        }
        sb.append(");\n");
        sb.append("    </script>\n");

        for (String s : scripts)
            sb.append("    <script src=\"").append(contextPath).append("/").append(filter(s)).append("?").append(serverHash).append("\" type=\"text/javascript\"></script>\n");

        sb.append("    <script type=\"text/javascript\">\n");
        sb.append("        Ext.Ajax.timeout = 5 * 60 * 1000; // Default to 5 minute timeout\n");
        sb.append("    </script>\n");
        return sb.toString();
    }


    // TODO: Delete after moving these tags to labkey.org <head>
    public static String getSearchIncludes()
    {
        String contextPath = AppProps.getInstance().getContextPath();

        return "    <link rel=\"search\" type=\"application/opensearchdescription+xml\" href=\"" + contextPath + "/plugins/labkey_documentation.xml\" title=\"LabKey Documentation\">\n" +
               "    <link rel=\"search\" type=\"application/opensearchdescription+xml\" href=\"" + contextPath + "/plugins/labkey_issues.xml\" title=\"LabKey Issues\">\n";
    }


    /** use this version if you don't care which errors are html parsing errors and which are safety warnings */
    public static String validateHtml(String html, Collection<String> errors, boolean scriptAsErrors)
    {
        return validateHtml(html, errors, scriptAsErrors ? null : errors);
    }


    /** validate an html fragment */
    public static String validateHtml(String html, Collection<String> errors, Collection<String> scriptWarnings)
    {
        if (errors.size() > 0 || (null != scriptWarnings && scriptWarnings.size() > 0))
            throw new IllegalArgumentException("empty errors collection expected");

        if (StringUtils.trimToEmpty(html).length() == 0)
            return "";

        // UNDONE: use convertHtmlToDocument() instead of tidy() to avoid double parsing
        String xml = tidy(html, true, errors);
        if (errors.size() > 0)
            return null;

        if (null != scriptWarnings)
        {
            try
            {
                XMLReader parser = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
                parserSetFeature(parser, "http://xml.org/sax/features/namespaces", false);
                parserSetFeature(parser, "http://xml.org/sax/features/namespace-prefixes", false);
                parserSetFeature(parser, "http://xml.org/sax/features/validation", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/dynamic", false);
                parserSetFeature(parser, "http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                parserSetFeature(parser, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/schema", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/schema-full-checking", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/dynamic", false);
                parserSetFeature(parser, "http://apache.org/xml/features/continue-after-fatal-error", false);

                parser.setContentHandler(new ValidateHandler(scriptWarnings));
                parser.parse(new InputSource(new StringReader(xml)));
            }
            catch (UnsupportedEncodingException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
            catch (IOException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
            catch (SAXException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
        }

        if (errors.size() > 0 || (null != scriptWarnings && scriptWarnings.size() > 0))
            return null;

        // let's return html not xhtml
        String tidy = tidy(html, false, errors);
        //FIX: 4528: old code searched for "<body>" but the body element can have attributes
        //and Word includes some when saving as HTML (even Filtered HTML).
        int beginOpenBodyIndex = tidy.indexOf("<body");
        int beginCloseBodyIndex = tidy.lastIndexOf("</body>");
        assert beginOpenBodyIndex != -1 && beginCloseBodyIndex != -1: "Tidied HTML did not include a body element!";
        int endOpenBodyIndex = tidy.indexOf('>', beginOpenBodyIndex);
        assert endOpenBodyIndex != -1 : "Could not find closing > of open body element!";

        tidy = tidy.substring(endOpenBodyIndex + 1, beginCloseBodyIndex).trim();
        return tidy;
    }



    static Integer serverHash = null;

    public static JSONObject jsInitObject()
    {
        return jsInitObject(new LinkedHashSet<ClientDependency>());
    }

    public static JSONObject jsInitObject(LinkedHashSet<ClientDependency> resources)
    {
        String contextPath = AppProps.getInstance().getContextPath();
        JSONObject json = new JSONObject();
        json.put("experimentalContainerRelativeURL", AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_CONTAINER_RELATIVE_URL));
        json.put("contextPath", contextPath);
        json.put("imagePath", contextPath + "/_images");
        json.put("extJsRoot", extJsRoot);
        json.put("devMode", AppProps.getInstance().isDevMode());
        json.put("homeContainer", ContainerManager.getHomeContainer().getName());
        json.put("sharedContainer", ContainerManager.getSharedContainer().getName());
        json.put("hash", getServerSessionHash());

        //TODO: these should be passed in by callers
        ViewContext context = HttpView.currentView().getViewContext();
        Container container = context.getContainer();
        User user = HttpView.currentView().getViewContext().getUser();
        HttpServletRequest request = context.getRequest();

        if(container != null)
            json.put("moduleContext", getModuleClientContext(container, user, resources));

        JSONObject userProps = new JSONObject();

        userProps.put("id", user.getUserId());
        userProps.put("displayName", user.getDisplayName(user));
        userProps.put("email", user.getEmail());
        userProps.put("phone", user.getPhone());
        userProps.put("sessionid", request == null ? null : getSessionId(request));

        userProps.put("canInsert", null != container && container.hasPermission(user, InsertPermission.class));
        userProps.put("canUpdate", null != container && container.hasPermission(user, UpdatePermission.class));
        userProps.put("canUpdateOwn", null != container && container.hasPermission(user, ACL.PERM_UPDATEOWN));
        userProps.put("canDelete", null != container && container.hasPermission(user, DeletePermission.class));
        userProps.put("canDeleteOwn", null != container && container.hasPermission(user, ACL.PERM_DELETEOWN));
        userProps.put("isAdmin", null != container && container.hasPermission(user, AdminPermission.class));
        userProps.put("isSystemAdmin", user.isAdministrator());
        userProps.put("isGuest", user.isGuest());
        json.put("user", userProps);

        if (null != container)
        {
            JSONObject containerProps = new JSONObject();

            // This is by contract the unencoded container path -- see LABKEY.ActionURL.getContainer
            containerProps.put("path", container.getPath());

            containerProps.put("id", container.getId());
            containerProps.put("name", container.getName());
            containerProps.put("type", container.getContainerNoun());
            Container parent = container.getParent();
            containerProps.put("parentPath", parent==null ? null : parent.getPath());
            containerProps.put("parentId", parent==null ? null : parent.getId());

            json.put("container", containerProps);
            json.put("demoMode", DemoMode.isDemoMode(container, user));
        }

        Container project = (null == container || container.isRoot()) ? null : container.getProject();

        if (null != project)
        {
            JSONObject projectProps = new JSONObject();

            projectProps.put("id", project.getId());
            projectProps.put("path", project.getPath());
            projectProps.put("name", project.getName());
            json.put("project", projectProps);
        }

        json.put("serverName", StringUtils.isNotEmpty(AppProps.getInstance().getServerName()) ? AppProps.getInstance().getServerName() : "Labkey Server");
        json.put("versionString", AppProps.getInstance().getLabKeyVersionString());
        if (request != null)
        {
            if ("post".equalsIgnoreCase(request.getMethod()))
                json.put("postParameters", request.getParameterMap());
            String tok = CSRFUtil.getExpectedToken(request, null);
            if (null != tok)
                json.put("CSRF", tok);
        }

        // Include a few server-generated GUIDs/UUIDs
        json.put("uuids", Arrays.asList(GUID.makeGUID(), GUID.makeGUID(), GUID.makeGUID()));

        return json;
    }

    public static String getServerSessionHash()
    {
        if (null == serverHash)
            serverHash = 0x7fffffff & AppProps.getInstance().getServerSessionGUID().hashCode();
        return Integer.toString(serverHash);
    }


    private static class ValidateHandler extends org.xml.sax.helpers.DefaultHandler
    {
        static HashSet<String> _illegalElements = new HashSet<String>();

        static
        {
            _illegalElements.add("link");
            _illegalElements.add("style");
            _illegalElements.add("script");
            _illegalElements.add("object");
            _illegalElements.add("applet");
            _illegalElements.add("form");
            _illegalElements.add("input");
            _illegalElements.add("button");
            _illegalElements.add("frame");
            _illegalElements.add("frameset");
            _illegalElements.add("iframe");
            _illegalElements.add("embed");
            _illegalElements.add("plaintext");
        }

        Collection<String> _errors;
        HashSet<String> _reported = new HashSet<String>();


        ValidateHandler(Collection<String> errors)
        {
            _errors = errors;
        }


        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
        {
            String e = qName.toLowerCase();
            if ((e.startsWith("?") || _illegalElements.contains(e)) && !_reported.contains(e))
            {
                _reported.add(e);
                _errors.add("Illegal element <" + qName + ">. For permissions to use this element, contact your system administrator.");
            }

            for (int i = 0; i < attributes.getLength(); i++)
            {
                String a = attributes.getQName(i).toLowerCase();
                String value = attributes.getValue(i).toLowerCase();

                if ((a.startsWith("on") || a.startsWith("behavior")) && !_reported.contains(a))
                {
                    _reported.add(a);
                    _errors.add("Illegal attribute '" + attributes.getQName(i) + "' on element <" + qName + ">.");
                }
                if ("href".equals(a))
                {
                    if (value.contains("script") && value.indexOf("script") < value.indexOf(":") && !_reported.contains("href"))
                    {
                        _reported.add("href");
                        _errors.add("Script is not allowed in 'href' attribute on element <" + qName + ">.");
                    }
                }
                if ("style".equals(a))
                {
                    if ((value.contains("behavior") || value.contains("url") || value.contains("expression")) && !_reported.contains("style"))
                    {
                        _reported.add("style");
                        _errors.add("Style attribute cannot contain behaviors, expresssions, or urls. Error on element <" + qName + ">.");
                    }
                }
            }
        }

        @Override
        public void warning(SAXParseException e) throws SAXException
        {
        }

        @Override
        public void error(SAXParseException e) throws SAXException
        {
            _errors.add(e.getMessage());
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException
        {
            _errors.add(e.getMessage());
        }
    }




    public static boolean isRobotUserAgent(String userAgent)
    {
        if (StringUtils.isEmpty(userAgent))
            return true;
        userAgent = userAgent.toLowerCase();

        /* our big crawlers are... */
        // Google
        if (userAgent.contains("googlebot"))
            return true;
        // Yahoo
        if (userAgent.contains("yahoo! slurp"))
            return true;
        // Microsoft
        if (userAgent.contains("bingbot") || userAgent.contains("msnbot"))
            return true;
        if (userAgent.contains("msiecrawler"))  // client site-crawler
            return false;
        // Pingdom
        if (userAgent.contains("pingdom.com_bot"))
            return true;
        // a bot
        if (userAgent.contains("rpt-httpclient"))
            return true;

        // just about every bot contains "bot", "crawler" or "spider"
        // including yandexbot, ahrefsbot, mj12bot, ezooms.bot, gigabot, voilabot, exabot
        if (userAgent.contains("bot") || userAgent.contains("crawler") || userAgent.contains("spider"))
            return true;

        return false;
    }



    //
    // TestCase
    //


    public static class TestCase extends Assert
    {
        @Test
        public void testPhone()
        {
            assertEquals(formatPhoneNo("5551212"), "555-1212");
            assertEquals(formatPhoneNo("2065551212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("12065551212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("206.555.1212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("1-206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1-206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1(206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1 (206)555.1212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("(206)-555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("work (206)555.1212"), "work (206) 555-1212");
            assertEquals(formatPhoneNo("206.555.1212 x0001"), "(206) 555-1212 x0001");
        }


        @Test
        public void testFilter()
        {
            assertEquals(filter("this is a test"), "this is a test");
            assertEquals(filter("<this is a test"), "&lt;this is a test");
            assertEquals(filter("this is a test<"), "this is a test&lt;");
            assertEquals(filter("'t'&his is a test\""), "&#039;t&#039;&amp;his is a test&quot;");
            assertEquals(filter("<>\"&"), "&lt;&gt;&quot;&amp;");
        }


        @Test
        public void testRobot()
        {
            List<String> bots = Arrays.asList(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html",
                "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
                "Pingdom.com_bot_version_1.4_(http://www.pingdom.com/)",
                "Googlebot-Image/1.0",
                "Mozilla/5.0 (compatible; AhrefsBot/2.0; +http://ahrefs.com/robot/)",
                "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)",
                "Gigabot/3.0 (http://www.gigablast.com/spider.html)",
                "msnbot-media/1.1 (+http://search.msn.com/msnbot.htm)",
                "Mozilla/5.0 (compatible; Ezooms/1.0; ezooms.bot@gmail.com)",
                "Mozilla/5.0 (iPhone; U; CPU iPhone OS 4_1 like Mac OS X; en-us) AppleWebKit/532.9 (KHTML, like Gecko) Version/4.0.5 Mobile/8B117 Safari/6531.22.7 (compatible; Googlebot-Mobile/2.1; +http://www.google.com/bot.html)",
                "Mozilla/5.0 (compatible; MJ12bot/v1.4.0; http://www.majestic12.co.uk/bot.php?+)",
                "Mozilla/5.0 (Windows; U; Windows NT 5.1; fr; rv:1.8.1) VoilaBot BETA 1.2 (support.voilabot@orange-ftgroup.com)",
                "Mozilla/5.0 (compatible; Exabot/3.0; +http://www.exabot.com/go/robot)",
                "Yeti/1.0 (NHN Corp.; http://help.naver.com/robots/)",
                "DoCoMo/2.0 N905i(c100;TB;W24H16) (compatible; Googlebot-Mobile/2.1; +http://www.google.com/bot.html)",
                "SAMSUNG-SGH-E250/1.0 Profile/MIDP-2.0 Configuration/CLDC-1.1 UP.Browser/6.2.3.3.c.1.101 (GUI) MMP/2.0 (compatible; Googlebot-Mobile/2.1; +http://www.google.com/bot.html)",
                "Mozilla/5.0 (compatible; AhrefsBot/1.0; +http://ahrefs.com/robot/)",
                "SETOOZBOT/5.0 ( compatible; SETOOZBOT/0.30 ; http://www.setooz.com/bot.html )",
                "Mozilla/5.0 (compatible; bnf.fr_bot; +http://www.bnf.fr/fr/outils/a.dl_web_capture_robot.html)",
                "AdMedia bot",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 5_0_1 like Mac OS X) (compatible; Yeti-Mobile/0.1; +http://help.naver.com/robots/)",
                "Mozilla/5.0 (compatible; Dow Jones Searchbot)");
            List<String> nots = Arrays.asList(
                "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.7 (KHTML, like Gecko) Chrome/16.0.912.36 Safari/535.7",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:9.0a2) Gecko/20111101 Firefox/9.0a2",
                "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; WOW64; Trident/6.0)",
                "Opera/9.80 (Windows NT 6.1; U; es-ES) Presto/2.9.181 Version/12.00",
                "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_8; de-at) AppleWebKit/533.21.1 (KHTML, like Gecko) Version/5.0.5 Safari/533.21.1"
            );
            for (String ua : bots)
                assertTrue(isRobotUserAgent(ua));
            for (String ua : nots)
                assertFalse(isRobotUserAgent(ua));
        }
    }


    public static void sendAjaxCompletions(HttpServletResponse response, List<AjaxCompletion> completions) throws IOException
    {
        response.setContentType("text/xml");
        response.setHeader("Cache-Control", "no-store");
        Writer writer = response.getWriter();
        writer.write("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n");
        writer.write("<completions>");
        for (AjaxCompletion completion : completions)
        {
            writer.write("<completion>\n");
            writer.write("    <display>" + filter(completion.getKey()) + "</display>");
            writer.write("    <insert>" + filter(completion.getValue()) + "</insert>");
            writer.write("</completion>\n");
        }
        writer.write("</completions>");
    }


    /**
     * Returns a specified <code>UrlProvider</code> interface implementation, for use
     * in writing URLs implemented in other modules.
     *
     * @param inter interface extending UrlProvider
     * @return an implementation of the interface.
     */
    static public <P extends UrlProvider> P urlProvider(Class<P> inter)
    {
        return ModuleLoader.getInstance().getUrlProvider(inter);
    }

    static private String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    static public <T> String strSelect(String selectName, Map<T,String> map, T current)
    {
        return strSelect(selectName, map.keySet(), map.values(), current);
    }

    static public String strSelect(String selectName, Collection<?> values, Collection<String> labels, Object current)
    {
        if (values.size() != labels.size())
            throw new IllegalArgumentException();
        StringBuilder ret = new StringBuilder();
        ret.append("<select name=\"");
        ret.append(h(selectName));
        ret.append("\">");
        boolean found = false;
        Iterator itValue;
        Iterator<String> itLabel;
        for (itValue  = values.iterator(), itLabel = labels.iterator();
             itValue.hasNext() && itLabel.hasNext();)
        {
            Object value = itValue.next();
            String label = itLabel.next();
            boolean selected = !found && ObjectUtils.equals(current, value);
            ret.append("\n<option value=\"");
            ret.append(h(value));
            ret.append("\"");
            if (selected)
            {
                ret.append(" SELECTED");
                found = true;
            }
            ret.append(">");
            ret.append(h(label));
            ret.append("</option>");
        }
        ret.append("</select>");
        return ret.toString();
    }

    static public void close(Closeable closeable)
    {
        if (closeable == null)
            return;
        try
        {
            closeable.close();
        }
        catch (IOException e)
        {
            _log.error("Error in close", e);
        }
    }

    static public String _gif(int height, int width)
    {
        StringBuilder ret = new StringBuilder();
        ret.append("<img src=\"");
        ret.append(AppProps.getInstance().getContextPath());
        ret.append("/_.gif\" height=\"");
        ret.append(height);
        ret.append("\" width=\"");
        ret.append(width);
        ret.append("\">");
        return ret.toString();
    }

    /**
     * CONSOLIDATE ALL .lastFilter handling
     * scope is not fully supported
     */
    public static void saveLastFilter(ViewContext context, ActionURL url, String scope)
    {
        boolean lastFilter = ColumnInfo.booleanFromString(url.getParameter(scope + DataRegion.LAST_FILTER_PARAM));
        if (lastFilter)
            return;
        ActionURL clone = url.clone();

        // Don't store offset. It's especially bad because there may not be that many rows the next time you
        // get to a URL that uses the .lastFilter
        for (String paramName : clone.getParameterMap().keySet())
        {
            if (paramName.endsWith("." + QueryParam.offset))
            {
                clone.deleteParameter(paramName);
            }
        }

        clone.deleteParameter(scope + DataRegion.LAST_FILTER_PARAM);
        HttpSession session = context.getRequest().getSession(false);
        // We should already have a session at this point, but check anyway - see bug #7761
        if (session != null)
        {
            try
            {
                session.setAttribute(url.getPath() + "#" + scope + DataRegion.LAST_FILTER_PARAM, clone);
            }
            catch (IllegalStateException ignored)
            {
                // Session may have been invalidated elsewhere, but there's no way to check
            }
        }
    }

    public static ActionURL getLastFilter(ViewContext context, ActionURL url)
    {
        ActionURL ret = (ActionURL) context.getSession().getAttribute(url.getPath() + "#" + DataRegion.LAST_FILTER_PARAM);
        return ret != null ? ret.clone() : url.clone();
    }

    public static ActionURL addLastFilterParameter(ActionURL url)
    {
        return url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }


    public static ActionURL addLastFilterParameter(ActionURL url, String scope)
    {
        return url.addParameter(scope + DataRegion.LAST_FILTER_PARAM, "true");
    }

    public static String getSessionId(HttpServletRequest request)
    {
        return WebUtils.getSessionId(request);
    }

    /**
     * Stream the text back to the browser as a PNG
     */
    public static void streamTextAsImage(HttpServletResponse response, String text, int width, int height, Color textColor) throws IOException
    {
        Font font = new Font("SansSerif", Font.PLAIN, 12);

        BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = buffer.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(textColor);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        int fontHeight = metrics.getHeight();
        int spaceWidth = metrics.stringWidth(" ");

        int x = 5;
        int y = fontHeight + 5;

        StringTokenizer st = new StringTokenizer(text, " ");
        // Line wrap to fit
        while (st.hasMoreTokens())
        {
            String token = st.nextToken();
            int tokenWidth = metrics.stringWidth(token);
            if (x != 5 && tokenWidth + x > width)
            {
                x = 5;
                y += fontHeight;
            }
            g2.drawString(token, x, y);
            x += tokenWidth + spaceWidth;
        }

        response.setContentType("image/png");
        EncoderUtil.writeBufferedImage(buffer, ImageFormat.PNG, response.getOutputStream());
    }

    public static JSONObject getModuleClientContext(Container c, User u, LinkedHashSet<ClientDependency> resources)
    {
        JSONObject ret = new JSONObject();
        if (resources != null)
        {
            Set<Module> modules = new HashSet<Module>();
            for (ClientDependency cd : resources)
            {
                modules.addAll(cd.getRequiredModuleContexts());
            }

            for (Module m : modules)
            {
                ret.put(m.getName().toLowerCase(), m.getPageContextJson(u, c));
            }
        }
        return ret;
    }
}
