/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.dataset.client;

import com.google.gwt.user.client.rpc.RemoteService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupService;
import org.labkey.study.dataset.client.model.GWTDataset;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:34:56 PM
 */
public interface DatasetService extends LookupService
{
    public GWTDataset getDataset(int id);

    /**
     * @param ds  Dataset this domain belongs to
     * @param orig Unchanged domain
     * @param dd New Domain
     * @return List of errors
     */
    public List<String> updateDatasetDefinition(GWTDataset ds, GWTDomain orig, GWTDomain dd);
    public List<String> updateDatasetDefinition(GWTDataset ds, GWTDomain orig, String schema);
    public GWTDomain getDomainDescriptor(String typeURI, String domainContainerId);
    public GWTDomain getDomainDescriptor(String typeURI);
}
