/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.test.pages.studydesigncontroller;

import org.jetbrains.annotations.Nullable;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.util.Ext4HelperWD;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;

/**
 * org.labkey.study.controllers.StudyDesignController#ManageAssayScheduleAction
 */
public class ManageAssayScheduleTester
{
    private BaseWebDriverTest _test;

    public ManageAssayScheduleTester(BaseWebDriverTest test)
    {
        _test = test;
        _test.waitForElement(Locator.id("AssaySpecimenConfigGrid"));
        _test._ext4Helper.waitForMaskToDisappear();
    }

    @LogMethod
    public void insertNewAssayConfiguration(@LoggedParam @Nullable String name, @Nullable String description, @Nullable String lab, @Nullable String sampleType)
    {
        Locator.XPathLocator assayConfigGrid = Locator.id("AssaySpecimenConfigGrid");
        Locator.XPathLocator insertNewConfigButton = assayConfigGrid.append(Locator.ext4Button("Insert New"));

        Locator.XPathLocator addAssayConfigWindow = Ext4HelperWD.Locators.window("Add Assay Configuration");

        _test.click(insertNewConfigButton);
        _test.waitForElement(addAssayConfigWindow);
        if (name != null) _test._ext4Helper.selectComboBoxItem(Ext4HelperWD.Locators.formItemWithInputNamed("AssayName"), true, name);
        if (description != null) _test.setFormElement(Locator.name("Description"), description);
        if (lab != null) _test._ext4Helper.selectComboBoxItem(Ext4HelperWD.Locators.formItemWithInputNamed("Lab"), true, lab);
        if (sampleType != null) _test._ext4Helper.selectComboBoxItem(Ext4HelperWD.Locators.formItemWithInputNamed("SampleType"), true, sampleType);

        _test.clickAndWait(addAssayConfigWindow.append(Locator.ext4Button("Submit")));
    }

    public void setAssayPlan(String assayPlan)
    {
        _test.setFormElement(Locator.name("assayPlan"), assayPlan);
        _test.clickButton("Save");
        _test._ext4Helper.waitForMaskToDisappear();
    }

    public static class Locators
    {
        public static Locator.XPathLocator assayScheduleGridCheckbox(String assay, String visit)
        {
            Locator.XPathLocator assayScheduleGrid = Locator.id("assaySpecimenVisitMappingTable");
            return assayScheduleGrid.append("/tbody/tr").withText(assay)
                    .append(Locator.xpath("/td/input[contains(@name, 'v"+visit+"')]"));
        }
    }
}
