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

package org.labkey.core.admin;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.ViewServlet;
import org.springframework.web.servlet.mvc.Controller;

import java.util.*;

class ActionsHelper
{
    static Map<String, Map<String, Map<String, SpringActionController.ActionStats>>> getActionStatistics() throws InstantiationException, IllegalAccessException
    {
        Map<String, Map<String, Map<String, SpringActionController.ActionStats>>> moduleMap = new LinkedHashMap<String, Map<String, Map<String, SpringActionController.ActionStats>>>();

        List<Module> modules = ModuleLoader.getInstance().getModules();

        for (Module module : modules)
        {
            Map<String, Map<String, SpringActionController.ActionStats>> controllerMap = new LinkedHashMap<String, Map<String, SpringActionController.ActionStats>>();
            moduleMap.put(module.getName(), controllerMap);
            Map<String, Class<? extends Controller>> pageFlows = module.getPageFlowNameToClass();
            Set<Class> controllerClasses = new HashSet<Class>(pageFlows.values());

            for (Class controllerClass : controllerClasses)
            {
                if (SpringActionController.class.isAssignableFrom(controllerClass))
                {
                    Map<String, SpringActionController.ActionStats> actionMap = new LinkedHashMap<String, SpringActionController.ActionStats>();
                    controllerMap.put(controllerClass.getSimpleName(), actionMap);
                    SpringActionController controller = (SpringActionController) ViewServlet.getController(module, controllerClass);
                    SpringActionController.ActionResolver ar = controller.getActionResolver();
                    Set<SpringActionController.ActionDescriptor> set = new TreeSet<SpringActionController.ActionDescriptor>(comp);
                    set.addAll(ar.getActionDescriptors());

                    for (SpringActionController.ActionDescriptor ad : set)
                        actionMap.put(ad.getActionClass().getSimpleName(), ad.getStats());
                }
            }
        }

        return moduleMap;
    }


    private static Comparator<SpringActionController.ActionDescriptor> comp = new Comparator<SpringActionController.ActionDescriptor>(){
        public int compare(SpringActionController.ActionDescriptor ad1, SpringActionController.ActionDescriptor ad2)
        {
            return ad1.getActionClass().getSimpleName().compareTo(ad2.getActionClass().getSimpleName());
        }
    };
}