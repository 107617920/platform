/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.jsp;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ExperimentalFeatureService;
import org.labkey.api.util.HasHtmlString;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JavaScriptFragment;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LabKeyJspWriter extends JspWriterWrapper
{
    private static final Multiset<String> CODE_POINT_COUNTING_SET = ConcurrentHashMultiset.create();
    private static final Multiset<String> FILE_COUNTING_SET = ConcurrentHashMultiset.create();
    private static final String EXPERIMENTAL_THROW_ON_WARNING = "labkeyJspWriterThrowOnWarning";
    private static final Logger LOGSTRING = LogManager.getLogger(LabKeyJspWriter.class.getName()+".string");

    public static void registerExperimentalFeature()
    {
        // Don't bother adding the flag in production mode since LabKeyJspWriter is registered only in development mode
        if (AppProps.getInstance().isDevMode())
        {
            AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_THROW_ON_WARNING,
                "Throw exceptions for JSP warnings",
                "Enables strict checking of JSP output. For example, calling print(String) results in an IllegalStateException.",
                false);
        }
    }

    LabKeyJspWriter(JspWriter jspWriter)
    {
        super(jspWriter);
    }

    @Override
    public void print(char[] s) throws IOException
    {
        throw new IllegalStateException("A JSP is attempting to render a character array!");
    }

    @Override
    public void print(String s) throws IOException
    {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement elementWeCareAbout = stackTrace[2];

        if (0 == CODE_POINT_COUNTING_SET.add(elementWeCareAbout.toString(), 1))
        {
            if (ExperimentalFeatureService.get().isFeatureEnabled(EXPERIMENTAL_THROW_ON_WARNING))
                throw new IllegalStateException("A JSP is printing a string!");

            // Shorten the stack trace to the first org.labkey.api.view.JspView.renderView()
            StringBuilder shortStackTrace = new StringBuilder("\njava.lang.Throwable");
            int i = 1;
            StackTraceElement ste;

            do
            {
                ste = stackTrace[i++];
                String line = String.valueOf(ste);
                shortStackTrace.append("\n\tat ");
                shortStackTrace.append(line);
            }
            while (i < stackTrace.length && !("org.labkey.api.view.JspView".equals(ste.getClassName()) && "renderView".equals(ste.getMethodName())));

            LOGSTRING.info("A JSP is printing a string!" + shortStackTrace.toString());
        }

        FILE_COUNTING_SET.add(elementWeCareAbout.getFileName(), 1);

        super.print(s);
    }

    @Override
    public void print(Object obj) throws IOException
    {
        if (!(obj instanceof HtmlString) && !(obj instanceof JavaScriptFragment))
        {
            if (obj instanceof HasHtmlString)
            {
                obj = ((HasHtmlString) obj).getHtmlString();
            }
            // Allow Number and Boolean for convenience -- no encoding needed for those. Also allow null, which is rendered
            // as "null" (useful when generating JavaScript).
            else if (null != obj && !(obj instanceof Number) && !(obj instanceof Boolean))
            {
                throw new IllegalStateException("A JSP is attempting to render an object of class " + obj.getClass().getName() + "!");
            }
        }

        super.print(obj);
    }

    public static void logStatistics()
    {
        if (AppProps.getInstance().isDevMode())
        {
            Set<Entry<String>> entrySet = CODE_POINT_COUNTING_SET.entrySet();
            LOGSTRING.info("Total print(String) occurrences: " + CODE_POINT_COUNTING_SET.size());
            LOGSTRING.info("Unique code points that invoke print(String): " + entrySet.size());

            if (!entrySet.isEmpty())
            {
                // Sorts entries first by count, then by key
                Comparator<Entry<String>> comparator = Comparator.comparingInt(Entry::getCount);
                comparator = comparator.reversed().thenComparing(Entry::getElement);

                List<Entry<String>> entries = new ArrayList<>(entrySet);
                entries.sort(comparator);
                LOGSTRING.info("All print(String) code points:\n   " +
                    entries.stream()
                        .map(e -> e.getElement() + "\t" + e.getCount())
                        .collect(Collectors.joining("\n   ")));

                List<Entry<String>> files = new ArrayList<>(FILE_COUNTING_SET.entrySet());
                files.sort(comparator);
                LOGSTRING.info("Problematic files (" + files.size() + "):\n" +
                    files.stream()
                        .map(e -> e.getElement() + "\t" + e.getCount())
                        .collect(Collectors.joining("\n")));
            }
        }
    }
}
