/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.settings;

import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.view.ThemeFont;
import org.labkey.api.view.WebThemeManager;
import org.labkey.api.util.FolderDisplayMode;

import java.sql.SQLException;

/**
 * User: adam
 * Date: Aug 1, 2008
 * Time: 9:35:40 PM
 */
public class LookAndFeelProperties extends AbstractWriteableSettingsGroup
{
    private static final String LOOK_AND_FEEL_SET_NAME = "LookAndFeel";

    protected static final String SYSTEM_DESCRIPTION_PROP = "systemDescription";
    protected static final String SYSTEM_SHORT_NAME_PROP = "systemShortName";
    protected static final String THEME_NAME_PROP = "themeName";
    protected static final String FOLDER_DISPLAY_MODE = "folderDisplayMode";
    protected static final String NAVIGATION_BAR_WIDTH = "navigationBarWidth";
    protected static final String LOGO_HREF_PROP = "logoHref";
    protected static final String THEME_FONT_PROP = "themeFont";
    protected static final String MENU_UI_ENABLED_PROP = "menuUIEnabled";
    protected static final String APP_BAR_UI_ENABLED_PROP = "appBarUIEnabled";

    protected static final String COMPANY_NAME_PROP = "companyName";
    protected static final String SYSTEM_EMAIL_ADDRESS_PROP = "systemEmailAddress";
    protected static final String REPORT_A_PROBLEM_PATH_PROP = "reportAProblemPath";

    private Container _c;

    protected String getType()
    {
        return "look and feel settings";
    }

    protected String getGroupName()
    {
        return LOOK_AND_FEEL_SET_NAME;
    }

    public static LookAndFeelProperties getInstance(Container c)
    {
        return new LookAndFeelProperties(c);
    }

    public static WriteableLookAndFeelProperties getWriteableInstance(Container c) throws SQLException
    {
        assert c.isProject() || c.isRoot();
        return new WriteableLookAndFeelProperties(c);
    }

    protected LookAndFeelProperties(Container c)
    {
        _c = getSettingsContainer(c);
    }

    public boolean hasProperties()
    {
        try
        {
            return getProperties(_c).size() > 0;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);            
        }
    }

    protected String lookupStringValue(String name, String defaultValue)
    {
        return lookupStringValue(_c, name, defaultValue);
    }

    protected String lookupStringValue(Container c, String name, String defaultValue)
    {
        if (c.isRoot())
            return super.lookupStringValue(c, name, defaultValue);

        String value = super.lookupStringValue(c, name, null);

        if (null == value)
            value = lookupStringValue(c.getParent(), name, defaultValue);

        return value;
    }

    public String getDescription()
    {
        return lookupStringValue(SYSTEM_DESCRIPTION_PROP, "");
    }

    public String getShortName()
    {
        return lookupStringValue(SYSTEM_SHORT_NAME_PROP, "LabKey Server");
    }

    public String getThemeName()
    {
        return lookupStringValue(THEME_NAME_PROP, WebThemeManager.DEFAULT_THEME.toString());
    }

    public String getThemeFont()
    {
        return lookupStringValue(THEME_FONT_PROP, ThemeFont.DEFAULT_THEME_FONT.getFriendlyName());
    }

    public FolderDisplayMode getFolderDisplayMode()
    {
        try
        {
            return FolderDisplayMode.fromString(lookupStringValue(FOLDER_DISPLAY_MODE, FolderDisplayMode.ALWAYS.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return FolderDisplayMode.ALWAYS;
        }
    }

    public String getNavigationBarWidth()
    {
        return lookupStringValue(NAVIGATION_BAR_WIDTH, "146");
    }

    public String getLogoHref()
    {
        return lookupStringValue(LOGO_HREF_PROP, AppProps.getInstance().getHomePageUrl());
    }

    public String getCompanyName()
    {
        return lookupStringValue(COMPANY_NAME_PROP, "Demo Installation");
    }

    public String getSystemEmailAddress()
    {
        return lookupStringValue(SYSTEM_EMAIL_ADDRESS_PROP, "cpas@fhcrc.org");
    }

    public String getUnsubstitutedReportAProblemPath()
    {
        return lookupStringValue(REPORT_A_PROBLEM_PATH_PROP, "${contextPath}/project" + Container.DEFAULT_SUPPORT_PROJECT_PATH + "/begin.view");
    }

    public String getReportAProblemPath()
    {
        String path = getUnsubstitutedReportAProblemPath();

        if ("/dev/issues".equals(path))
        {
            try
            {
                path = "${contextPath}/issues/dev/issues/insert.view";
                WriteableLookAndFeelProperties writeable = getWriteableInstance(_c);
                writeable.setReportAProblemPath(path);
                writeable.save();
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        return path.replace("${contextPath}", AppProps.getInstance().getContextPath());
    }

    public boolean isAppBarUIEnabled()
    {
        return lookupBooleanValue(APP_BAR_UI_ENABLED_PROP, false);
    }

    public boolean isMenuUIEnabled()
    {
        return lookupBooleanValue(MENU_UI_ENABLED_PROP, false);
    }
    
    public static Container getSettingsContainer(Container c)
    {
        if (c.isRoot())
            return c;
        else
            return c.getProject();
    }
}