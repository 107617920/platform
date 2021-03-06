/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.WebPartFrame.FrameConfig;
import org.labkey.api.view.WebPartView.FrameType;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PageConfig.Template;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by matthew on 12/11/15.
 */
public interface ViewService
{
    static @NotNull ViewService get()
    {
        return ServiceRegistry.get().getService(ViewService.class);
    }

    static void setInstance(ViewService impl)
    {
        ServiceRegistry.get().registerService(ViewService.class, impl);
    }

    @Nullable HttpView<PageConfig> getTemplate(Template t, ViewContext context, ModelAndView body, PageConfig page);
    WebPartFrame getFrame(FrameType frameType, ViewContext context, FrameConfig config);
}
