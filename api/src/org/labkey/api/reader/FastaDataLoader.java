/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.reader;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 14, 2012
 */
public class FastaDataLoader extends DataLoader
{
    public static FileType FILE_TYPE = new FileType(Arrays.asList("fna", "fasta"), "fna");
    static {
        FILE_TYPE.setExtensionsMutuallyExclusive(false);
    }

    public static class Factory extends AbstractDataLoaderFactory
    {
        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new FastaDataLoader(file, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            File f = File.createTempFile("import", ".fasta");
            f.deleteOnExit();
            IOUtils.copy(is, new FileOutputStream(f));

            return new FastaDataLoader(f, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public FileType getFileType() { return FILE_TYPE; }
    }

    GenericFastaLoader _loader;

    public FastaDataLoader(File inputFile, Boolean hasColumnHeaders) throws IOException
    {
        this(inputFile, hasColumnHeaders, null);
    }

    public FastaDataLoader(File inputFile, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setSource(inputFile);
        setHasColumnHeaders(hasColumnHeaders);

        _loader = new GenericFastaLoader(inputFile);
    }

    @Override
    public String[][] getFirstNLines(int n) throws IOException
    {
        GenericFastaLoader.GenericIterator it = _loader.iterator();
        try
        {
            List<String[]> lineFields = new ArrayList<String[]>(n);
            int count = 0;

            while (it.hasNext() && count < n)
            {
                Map<String, Object> row = it.next();

                // add the internally generated column header
                if (count == 0)
                    lineFields.add(row.keySet().toArray(new String[0]));

                lineFields.add(row.values().toArray(new String[0]));

                count++;
            }

            if (lineFields.isEmpty())
                return new String[0][];

            return lineFields.toArray(new String[count][]);
        }
        finally
        {
            it.close();
        }
    }

    @Override
    public CloseableIterator<Map<String, Object>> iterator()
    {
        return _loader.iterator();
    }

    @Override
    public void close()
    {
        _loader.iterator().close();
    }


    private static class GenericFastaLoader extends FastaLoader<Map<String, Object>>
    {
        public GenericFastaLoader(File fastaFile)
        {
            super(fastaFile, new FastaIteratorElementFactory<Map<String, Object>>()
            {
                public Map<String, Object> createNext(String header, byte[] body)
                {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();

                    parseHeader(header, row);
                    row.put("sequence", new String(body));

                    return row;
                }

                /**
                 * Attempt to parse out any information from the header, some fasta formats may
                 * contain key/value pairs in the header that we can use to create field/value pairs.
                 * Otherwise the default is to return the entire header.
                 * @param header
                 * @param rowMap
                 */
                private void parseHeader(String header, Map<String, Object> rowMap)
                {
                    if (header != null)
                    {
                        StringBuilder sb = new StringBuilder();
                        String delim = "";

                        for (String part : header.split("\\s"))
                        {
                            String field[] = part.split("=");

                            if (field.length == 2)
                            {
                                if (field[0].startsWith("[") && field[1].endsWith("]") ||
                                    field[0].startsWith("(") && field[1].endsWith(")"))
                                    rowMap.put(field[0].substring(1), field[1].substring(0, field[1].length()-1));
                                else
                                    rowMap.put(field[0], field[1]);
                            }
                            else
                            {
                                sb.append(delim).append(part);
                                delim = " ";
                            }
                        }

                        if (sb.length() > 0)
                            rowMap.put("header", sb.toString());
                    }
                }
            });
        }

        @Override
        public GenericIterator iterator()
        {
            return new GenericIterator();
        }

        public class GenericIterator extends FastaIterator implements CloseableIterator<Map<String, Object>>
        {
            private GenericIterator()
            {
                super();
            }
        }
    }
}
