/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gwt.client.org.labkey.assay.designer.client;

import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.gwt.client.ui.domain.InferencedColumn;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Dec 23, 2010
 * Time: 1:40:28 PM
 */
public interface AssayImporterService extends DomainImporterService
{
    // get the inferred column list of the server side file specified
    public List<InferencedColumn> getInferenceColumns(String path, String file) throws ImportException;

    /**
     * Optional action to perform server side validation on the file, using the specified column
     * descriptors. The entire or part of the file can be checked.
     */
    public Boolean validateColumns(List<InferencedColumn> columns, String path, String file) throws ImportException;
    
    /**
     * Create a new assay instance for the specified provider and assay name
     * @throws ImportException
     */
    public GWTProtocol createProtocol(String providerName, String assayName) throws ImportException;

    /**
     * Returns the domain URI to create columns based on the imported file(s)
     * @param protocol
     * @return
     * @throws ImportException
     */
    public String getDomainImportURI(GWTProtocol protocol) throws ImportException;

    /**
     * Returns the protocol specific import wizard action to import the uploaded file
     */
    public String getImportURL(GWTProtocol protocol, String directoryPath, String file) throws ImportException;
    public String getDesignerURL(GWTProtocol protocol, String directoryPath, String file) throws ImportException;
}
