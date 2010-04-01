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

package gwt.client.org.labkey.plate.designer.client;

/**
 * User: jeckels
 * Date: Apr 19, 2007
 */
public class PlatePropertyPanel extends PropertyPanel
{
    public PlatePropertyPanel(TemplateView templateView)
    {
        super(templateView);
        redraw(templateView.getPlate().getPlateProperties());
    }
}
