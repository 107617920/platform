/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.api.qc;

import org.labkey.api.util.DateUtil;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.exp.api.ExpProtocol;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Nov 17, 2009
 */
public class TsvDataSerializer implements DataExchangeHandler.DataSerializer
{
    public void exportRunData(ExpProtocol protocol, List<Map<String, Object>> data, File runDataFile) throws Exception
    {
        if (data.size() > 0)
        {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(runDataFile)));
            try {
                // write the column header
                List<String> columns = new ArrayList<String>(data.get(0).keySet());
                String sep = "";
                for (String name : columns)
                {
                    pw.append(sep);
                    pw.append(name);
                    sep = "\t";
                }
                pw.println();

                // write the rows
                for (Map<String, Object> row : data)
                {
                    sep = "";
                    for (String name : columns)
                    {
                        Object o = row.get(name);
                        pw.append(sep);
                        if (o != null)
                        {
                            if (Date.class.isAssignableFrom(o.getClass()))
                                pw.append(DateUtil.formatDateTime((Date)o));
                            else if (Collection.class.isAssignableFrom(o.getClass()))
                                pw.append(StringUtils.join((Collection)o, ","));
                            else if (Object[].class.isAssignableFrom(o.getClass()))
                                pw.append(StringUtils.join((Object[])o, ","));
                            else
                                pw.append(String.valueOf(o));
                        }
                        sep = "\t";
                    }
                    pw.println();
                }
            }
            finally
            {
                pw.close();
            }
        }
    }

    public List<Map<String, Object>> importRunData(ExpProtocol protocol, File runData) throws Exception
    {
        DataLoader<Map<String, Object>> loader = new TabLoader(runData, true);
        return loader.load();
    }
}
