package org.labkey.test.pages.StudyDesignController;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.util.Ext4HelperWD;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;

/**
 * org.labkey.study.controllers.StudyDesignController#ManageStudyProductsAction
 */
public class ManageStudyProductsTester
{
    private BaseWebDriverTest _test;

    public ManageStudyProductsTester(BaseWebDriverTest test)
    {
        _test = test;
        _test.waitForElement(Locator.id("immunogens-grid"));
        _test._ext4Helper.waitForMaskToDisappear();
    }

    @LogMethod
    public void insertNewImmunogen(@LoggedParam String label, String type)
    {
        Locator.XPathLocator immunogensGrid = Locator.id("immunogens-grid");
        Locator.XPathLocator insertNewImmunogenButton = immunogensGrid.append(Locator.ext4Button("Insert New"));

        _test.click(insertNewImmunogenButton);
        _test.waitForElement(Ext4HelperWD.Locators.window("Insert Immunogen"));
        _test.setFormElement(Locator.name("Label"), label);
        _test._ext4Helper.selectComboBoxItem(Ext4HelperWD.Locators.formItemWithLabel("Type:"), true, type);
        _test.clickButton("Submit", 0);
        _test._ext4Helper.waitForMaskToDisappear();
    }

    public void editAntigens(String immunogen)
    {
        int antigenColumnNumber = 3;
        _test.doubleClick(Locator.tag("tr").withPredicate(Locator.xpath("td[1]").withText(immunogen)).append("/td[" + antigenColumnNumber + "]"));
        _test.waitForElement(Ext4HelperWD.Locators.window("Edit HIV Antigens for " + immunogen));
    }

    @LogMethod
    public void insertNewAntigen(@LoggedParam String immunogen, String gene, String subType, String genBankId, String sequence)
    {
        _test.click(Ext4HelperWD.Locators.window("Edit HIV Antigens for " + immunogen).append(Locator.ext4Button("Insert New")));
        if (gene != null) _test._ext4Helper.selectComboBoxItem(Locators.antigenComboBox("Gene"), true, gene);
        if (subType != null) _test._ext4Helper.selectComboBoxItem(Locators.antigenComboBox("SubType"), true, subType);
        _test.click(Locator.ext4Button("Update"));
    }

    public void submitAntigens(String immunogen)
    {
        _test.click(Ext4HelperWD.Locators.window("Edit HIV Antigens for " + immunogen).append(Locator.ext4Button("Submit")));
        _test._ext4Helper.waitForMaskToDisappear();
    }

    @LogMethod
    public void insertNewAdjuvant(@LoggedParam String label)
    {
        Locator.XPathLocator adjuvantGrid = Locator.id("adjuvants-grid");
        Locator.XPathLocator insertNewAdjuvantButton = adjuvantGrid.append(Locator.ext4Button("Insert New"));

        Locator.XPathLocator insertAdjuvantWindow = Ext4HelperWD.Locators.window("Insert Adjuvant");

        _test.click(insertNewAdjuvantButton);
        _test.waitForElement(insertAdjuvantWindow);
        _test.setFormElement(Locator.name("Label"), label);
        _test.click(insertAdjuvantWindow.append(Locator.ext4ButtonEnabled("Submit")));
        _test._ext4Helper.waitForMaskToDisappear();
    }

    public static class Locators
    {
        public static Locator.XPathLocator antigenComboBox(String antigenField)
        {
            return Locator.tag("*").withClass("x4-form-item").withDescendant(Locator.tagWithName("input", antigenField)).notHidden();
        }
    }
}

