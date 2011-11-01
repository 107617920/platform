package org.labkey.study.view;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.resource.Resource;
import org.labkey.study.StudyModule;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * Copyright (c) 2010-2011 LabKey Corporation
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
 * Date: Oct 4, 2010 2:37:55 PM
 */
public class StudyViewLoader implements ModuleResourceLoader
{
    /*package*/ static final String VIEWS_DIR_NAME = "views";

    @Override
    public Set<String> getModuleDependencies(Module module, File explodedModuleDir)
    {
        // NOTE: Can't use Module's resource resolver yet since the module hasn't been initialized.
        File viewsDir = new File(explodedModuleDir, VIEWS_DIR_NAME);
        if (!StudyModule.MODULE_NAME.equalsIgnoreCase(module.getName()) && viewsDir.exists())
            return Collections.singleton(StudyModule.MODULE_NAME);
        return Collections.emptySet();
    }

    @Override
    public void loadResources(Module module, File explodedModuleDir) throws IOException, ModuleResourceLoadException
    {
        Resource viewsDir = module.getModuleResource(VIEWS_DIR_NAME);
        if (viewsDir != null && viewsDir.exists() && viewsDir.isCollection())
        {
            Resource participantView = viewsDir.find("participant.html");
            if (participantView != null && participantView.exists() && participantView.isFile())
                StudyManager.getInstance().registerParticipantView(module, participantView);
        }
    }
}
