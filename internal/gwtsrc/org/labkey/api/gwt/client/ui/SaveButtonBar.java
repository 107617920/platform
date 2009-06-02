/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import com.google.gwt.user.client.ui.*;

/**
 * User: jgarms
 * Date: Jun 2, 2008
 * Time: 1:46:00 PM
 */
public class SaveButtonBar extends HorizontalPanel
{
    private final Saveable owner;

    private final ButtonBase finishButton;
    private final ButtonBase saveButton;
    private final ButtonBase cancelButton;

    public SaveButtonBar(Saveable s)
    {
        super();
        owner = s;

        finishButton = new ImageButton("Save & Close", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                owner.finish();
            }
        });

        add(finishButton);

        saveButton = new ImageButton("Save", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                owner.save();
            }
        });
        add(saveButton);


        cancelButton = new ImageButton("Cancel", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                owner.cancel();
            }
        });

        add(cancelButton);
    }

    public void setAllowSave(boolean dirty)
    {
        saveButton.setEnabled(dirty);
    }

}
