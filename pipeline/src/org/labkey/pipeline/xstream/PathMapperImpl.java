/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
package org.labkey.pipeline.xstream;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.pipeline.file.PathMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PathMapper class
 * <p/>
 * Created: Oct 4, 2007
 *
 * @author bmaclean
 */
public class PathMapperImpl implements PathMapper
{
    private static Logger _log = Logger.getLogger(PathMapperImpl.class);

    /**
     * Prefix mappings:
     * <ul>
     * <li>file:/C:/root -> file:/home/root
     * <li>file:/C:/projects/root1 -> file:/home/user/projects/root1
     * <li>file:/C:/projects/root2 -> file:/home/user/projects/root2
     * <ul>
     */
    private Map<String, String> _pathMap;
    private boolean _remoteIgnoreCase;
    private boolean _localIgnoreCase;

    public PathMapperImpl()
    {
    }

    public PathMapperImpl(Map<String, String> pathMap, boolean remoteIgnoreCase, boolean localIgnoreCase)
    {
        _pathMap = pathMap;
        _remoteIgnoreCase = remoteIgnoreCase;
        _localIgnoreCase = localIgnoreCase;
    }

    public Map<String, String> getPathMap()
    {
        LinkedHashMap m = new LinkedHashMap();
        return _pathMap;
    }

    public void setPathMap(Map<String, String> pathMap)
    {
        _pathMap = pathMap;
    }

    public boolean isRemoteIgnoreCase()
    {
        return _remoteIgnoreCase;
    }

    public void setRemoteIgnoreCase(boolean remoteIgnoreCase)
    {
        _remoteIgnoreCase = remoteIgnoreCase;
    }

    public boolean isLocalIgnoreCase()
    {
        return _localIgnoreCase;
    }

    public void setLocalIgnoreCase(boolean localIgnoreCase)
    {
        _localIgnoreCase = localIgnoreCase;
    }

    /**
     * If there are any prefix matches, map from remote system path
     * to local path.
     *
     * @param path remote path
     * @return local path
     */
    public String remoteToLocal(String path)
    {
        Map.Entry<String, String> bestEntry = null;
        if (_pathMap != null && path != null)
        {
            for (Map.Entry<String, String> e : _pathMap.entrySet())
            {
                String prefix = e.getKey();
                if (match(prefix, path, _remoteIgnoreCase) && (bestEntry == null || prefix.length() > bestEntry.getKey().length()))
                {
                    bestEntry = e;
                }
            }
        }
        if (bestEntry != null)
        {
            return bestEntry.getValue() + path.substring(bestEntry.getKey().length());
        }
        return capitalizeDriveLetter(path);
    }

    private static final int DRIVE_LETTER_INDEX = "file:/T".length() - 1;
    private static final int COLON_LETTER_INDEX = DRIVE_LETTER_INDEX + 1;

    private String capitalizeDriveLetter(String path)
    {
        if (path == null)
        {
            return null;
        }
        
        if (path.startsWith("file:/") && path.length() > COLON_LETTER_INDEX && path.charAt(COLON_LETTER_INDEX) == ':' && Character.isLowerCase(path.charAt(DRIVE_LETTER_INDEX)))
        {
            path = path.substring(0, "file:/".length()) + Character.toUpperCase(path.charAt(DRIVE_LETTER_INDEX)) + path.substring(COLON_LETTER_INDEX);
        }
        return path;
    }

    /**
     * If there are any prefix matches, map from local path
     * to remote system path.
     *
     * @param path remote path
     * @return local path
     */
    public String localToRemote(String path)
    {
        Map.Entry<String, String> bestEntry = null;
        if (_pathMap != null && path != null)
        {
            for (Map.Entry<String, String> e : _pathMap.entrySet())
            {
                String prefix = e.getValue();
                if (match(prefix, path, _localIgnoreCase) && (bestEntry == null || prefix.length() > bestEntry.getKey().length()))
                {
                    bestEntry = e;
                }
            }
        }

        if (bestEntry != null)
        {
            return bestEntry.getKey() + path.substring(bestEntry.getValue().length());
        }
        return path;
    }

    /**
     * Check if a path starts with a given path prefix.  If the prefix
     * itself does not end with '/', then the next character in the path
     * must be a '/'.  It is assumed that the strings are URIs like
     * 'file:/C:/root'.
     *
     * @param prefix a URI prefix like 'file:/C:/root'
     * @param path a URL path like 'file:/C:/root/subdir1/subdir2/file.txt'
     * @param ignoreCase True if match should be done case insensitive
     * @return true if the path starts with the prefix.
     */
    private boolean match(String prefix, String path, boolean ignoreCase)
    {
        final int lenPath = path.length();
        final int lenPrefix = prefix.length();
        if (lenPath < lenPrefix)
            return false;

        String start = path.substring(0, prefix.length());

        boolean ret;
        if (ignoreCase)
            ret = start.equalsIgnoreCase(prefix);
        else
            ret = start.equals(prefix);

        return ret &&
            (lenPath == lenPrefix ||
             prefix.charAt(lenPrefix - 1) == '/' ||
             path.charAt(lenPrefix) == '/');
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testCaseSensitiveMapping()
        {
            Map<String, String> m = new HashMap<String, String>();
            m.put("file:/T:/edi", "file:/home/edi");
            m.put("file:/T:/data", "file:/data");
            PathMapper mapper = new PathMapperImpl(m, false, false);

            assertEquals(mapper.localToRemote("file:/home/edi/testFile.txt"), "file:/T:/edi/testFile.txt");
            assertEquals(mapper.localToRemote("file:/data/testFile.txt"), "file:/T:/data/testFile.txt");
            assertEquals(mapper.localToRemote("file:/Data/testFile.txt"), "file:/Data/testFile.txt");

            assertEquals(mapper.remoteToLocal("file:/T:/edi/testFile.txt"), "file:/home/edi/testFile.txt");
            assertEquals(mapper.remoteToLocal("file:/T:/data/testFile.txt"), "file:/data/testFile.txt");
            assertEquals(mapper.remoteToLocal("file:/t:/data/testFile.txt"), "file:/T:/data/testFile.txt");
            assertEquals(mapper.remoteToLocal("file:/e:/data/testFile.txt"), "file:/E:/data/testFile.txt");
        }

        @Test
        public void testCaseInsensitiveMapping()
        {
            Map<String, String> m = new HashMap<String, String>();
            m.put("file:/T:/edi", "file:/home/edi");
            m.put("file:/T:/data", "file:/data");
            PathMapper mapper = new PathMapperImpl(m, true, true);

            assertEquals("file:/T:/edi/testFile.txt", mapper.localToRemote("file:/home/edi/testFile.txt"));
            assertEquals("file:/T:/data/testFile.txt", mapper.localToRemote("file:/data/testFile.txt"));
            assertEquals("file:/T:/data/testFile.txt", mapper.localToRemote("file:/Data/testFile.txt"));

            assertEquals("file:/home/edi/testFile.txt", mapper.remoteToLocal("file:/T:/edi/testFile.txt"));
            assertEquals("file:/data/testFile.txt", mapper.remoteToLocal("file:/T:/data/testFile.txt"));
            assertEquals("file:/data/testFile.txt", mapper.remoteToLocal("file:/t:/data/testFile.txt"));
        }

        @Test
        public void testLongestPrefixMapping()
        {
            Map<String, String> m = new LinkedHashMap<String, String>();
            m.put("file:/T:", "file:/home/stedi");
            m.put("file:/T:/data", "file:/data");
            m.put("file:/T:/edi", "file:/home/edi");
            PathMapper mapper = new PathMapperImpl(m, false, false);

            assertEquals("file:/T:/edi/testFile.txt", mapper.localToRemote("file:/home/edi/testFile.txt"));
            assertEquals("file:/T:/data/testFile.txt", mapper.localToRemote("file:/data/testFile.txt"));
            assertEquals("file:/T:/testFile.txt", mapper.localToRemote("file:/home/stedi/testFile.txt"));

            assertEquals("file:/home/stedi/testFile.txt", mapper.remoteToLocal("file:/T:/testFile.txt"));
            assertEquals("file:/data/testFile.txt", mapper.remoteToLocal("file:/T:/data/testFile.txt"));
            assertEquals("file:/home/edi/testFile.txt", mapper.remoteToLocal("file:/T:/edi/testFile.txt"));
        }

        @Test
        public void testCapitalizeDriveLetter()
        {
            PathMapperImpl impl = new PathMapperImpl();
            assertEquals("file:/T:/test", impl.capitalizeDriveLetter("file:/t:/test"));
            assertEquals("file:/T:/test", impl.capitalizeDriveLetter("file:/T:/test"));
            assertEquals("file:/test", impl.capitalizeDriveLetter("file:/test"));
            assertEquals("", impl.capitalizeDriveLetter(""));
            assertEquals("file:/", impl.capitalizeDriveLetter("file:/"));
            assertEquals("f", impl.capitalizeDriveLetter("f"));
        }
    }
}
