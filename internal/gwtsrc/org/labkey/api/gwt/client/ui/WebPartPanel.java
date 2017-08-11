/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Widget;

/**
 * User: jeckels
 * Date: Jun 13, 2007
 */
public class WebPartPanel extends FlexTable
{
    public WebPartPanel(String title, Widget contents)
    {
        setStyleName("labkey-wp");
        setText(0, 0, title);

        getRowFormatter().setStyleName(0, "labkey-wp-header");
        getCellFormatter().setStyleName(0, 0, "labkey-wp-title-left");
        getCellFormatter().getElement(0, 0).setTitle(title);

        getCellFormatter().setStyleName(1, 0, "labkey-wp-body");
        setWidget(1, 0, contents);
    }

    public void setContent(Widget content)
    {
        getCellFormatter().setStyleName(1, 0, "labkey-wp-body");
        setWidget(1, 0, content);
    }
}
