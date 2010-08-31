package org.labkey.api.nab;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.view.ActionURL;
import org.springframework.web.servlet.mvc.Controller;

/**
 * Copyright (c) 2010 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Aug 19, 2010 10:57:31 AM
 */
public interface NabUrls extends UrlProvider
{
    ActionURL getSampleXLSTemplateURL(Container container, ExpProtocol protocol);
}
