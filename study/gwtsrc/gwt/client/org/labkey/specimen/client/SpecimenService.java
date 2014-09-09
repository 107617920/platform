/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package gwt.client.org.labkey.specimen.client;

import com.google.gwt.user.client.rpc.SerializableException;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTContainer;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.LookupService;

import java.util.List;

/**
 * User: brittp
* Date: June 20, 2007
* Time: 2:37:12 PM
*/
public interface SpecimenService extends LookupService
{
    List<GWTDomain<GWTPropertyDescriptor>> getDomainDescriptors() throws SerializableException;

    List<String> updateDomainDescriptors(
            GWTDomain<GWTPropertyDescriptor> updateEvent,
            GWTDomain<GWTPropertyDescriptor> updateVial,
            GWTDomain<GWTPropertyDescriptor> updateSpecimen
    );

    List<List<String>> checkRollups(
            List<GWTPropertyDescriptor> eventFields,
            List<GWTPropertyDescriptor> vialFields,
            List<GWTPropertyDescriptor> specimenFields
    );
}
