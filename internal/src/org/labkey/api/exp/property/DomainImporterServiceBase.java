/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.api.exp.property;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.gwt.client.ui.domain.InferencedColumn;
import org.labkey.api.gwt.client.ui.domain.ImportStatus;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.util.SessionTempFileHolder;
import org.labkey.api.view.ViewContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 * Date: Nov 4, 2008
 */
public abstract class DomainImporterServiceBase extends DomainEditorServiceBase implements DomainImporterService
{
    /** The number of sample data rows to return **/
    private static final int NUM_SAMPLE_ROWS = 5;

    protected File _importFile;
    private int _numSampleRows = NUM_SAMPLE_ROWS;

    public DomainImporterServiceBase(ViewContext context)
    {
        super(context);
        setImportFile(context);
    }

    protected void setImportFile(ViewContext context)
    {
        // Retrieve the file from the session now, so we can delete it later.  If we're importing in a background thread,
        // we won't have a session at the end of import.
        HttpSession session = getViewContext().getSession();
        SessionTempFileHolder fileHolder = (SessionTempFileHolder)session.getAttribute("org.labkey.domain.tempFile");
        
        _importFile = (null == fileHolder ? null : fileHolder.getFile());
    }

    public List<InferencedColumn> inferenceColumns() throws ImportException
    {
        DataLoader loader = getDataLoader();

        try
        {
            return getColumns(loader);
        }
        finally
        {
            loader.close();
        }
    }

    @NotNull
    private File getImportFile() throws ImportException
    {
        if (null == _importFile)
            throw new ImportException("No import file uploaded");

        return _importFile;
    }

    protected void deleteImportFile()
    {
        try
        {
            //noinspection ResultOfMethodCallIgnored
            getImportFile().delete();
            HttpSession session = getViewContext().getSession();

            // No session if we're running in a background thread
            if (null != session)
                session.removeAttribute("org.labkey.domain.tempFile");
        }
        catch (ImportException ie)
        {
            // Nothing to do here -- we don't care if we couldn't find the file
        }
    }

    @NotNull
    protected DataLoader getDataLoader() throws ImportException
    {
        try
        {
            return DataLoader.getDataLoaderForFile(getImportFile(), getContainer());
        }
        catch (ServletException e)
        {
            throw new ImportException(e.getMessage());
        }
        catch (IOException e)
        {
            throw new ImportException(e.getMessage());
        }
    }

    public int getNumSampleRows()
    {
        return _numSampleRows;
    }

    public void setNumSampleRows(int numSampleRows)
    {
        _numSampleRows = numSampleRows;
    }

    private List<InferencedColumn> getColumns(DataLoader loader) throws ImportException
    {
        List<InferencedColumn> result = new ArrayList<InferencedColumn>();

        try
        {
            ColumnDescriptor[] columns = loader.getColumns();

            String[][] data = loader.getFirstNLines(_numSampleRows + 1); // also need the header
            int numRows = data.length;

            for (int colIndex=0; colIndex<columns.length; colIndex++)
            {
                ColumnDescriptor column = columns[colIndex];
                GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
                String name = column.name;
                if (name.length() > 2 && name.startsWith("\"") && name.endsWith("\""))
                    name = name.substring(1, name.length()-1);
                prop.setName(name);
                prop.setRangeURI(column.getRangeURI());
                prop.setMvEnabled(column.isMvEnabled());

                List<String> columnData = new ArrayList<String>();
                for (int rowIndex=1; rowIndex<numRows; rowIndex++)
                {
                    String datum = "";
                    if (data[rowIndex].length > colIndex) // Not guaranteed that every row has every column
                        datum = data[rowIndex][colIndex];
                    columnData.add(datum);
                }

                InferencedColumn infColumn = new InferencedColumn(prop, columnData);

                result.add(infColumn);
            }
        }
        catch (IOException e)
        {
            throw new ImportException(e.getMessage());
        }

        return result;
    }

    public GWTDomain getDomainDescriptor(String typeURI)
    {
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, getContainer());
    }

    public abstract ImportStatus importData(GWTDomain domain, Map<String, String> mappedColumnNames) throws ImportException;

    public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update)
    {
        try
        {
            return DomainUtil.updateDomainDescriptor(orig, update, getContainer(), getUser());
        }
        catch (ChangePropertyDescriptorException e)
        {
            // for GWT serialization don't use Collections.singleList()
            return new ArrayList<String>(Arrays.asList(e.getMessage()));
        }
    }

    
}
