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

package org.labkey.api.reports.report;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jul 12, 2007
 */
public class RReportDescriptor extends ScriptReportDescriptor
{
    public static final String TYPE = "rReportDescriptor";
    private KnitrFormat _knitrFormat = KnitrFormat.None;

    public enum KnitrFormat
    {
        None,
        Html,
        Markdown,
    }

    public enum Prop implements ReportProperty
    {
        knitrFormat
    }

    public static KnitrFormat getKnitrFormatFromString(String s)
    {
        if (s.equalsIgnoreCase(KnitrFormat.Html.name()))
            return KnitrFormat.Html;

        if (s.equalsIgnoreCase(KnitrFormat.Markdown.name()))
            return KnitrFormat.Markdown;

        return KnitrFormat.None;
    }

    public KnitrFormat getKnitrFormat()
    {
        return _knitrFormat;
    }

    public void setKnitrFormat(KnitrFormat knitrFormat)
    {
        _knitrFormat = knitrFormat;
    }

    public RReportDescriptor()
    {
        setDescriptorType(TYPE);
    }
}
