/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.api.action;

import org.springframework.web.servlet.mvc.Controller;

/*
* User: adam
* Date: Feb 24, 2011
* Time: 7:11:17 PM
*/
public class DeprecatedActionException extends RuntimeException
{
    public DeprecatedActionException(Class<? extends Controller> clazz)
    {
        super("This action (" + clazz.getName() + ") has been removed from LabKey Server. If you are still using this action, please contact LabKey Software at info@labkey.com and include this entire message.");
    }
}
