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
package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

/**
 * User: jgarms
 * Date: Oct 28, 2008
 */
public class ExportSchemaWizard extends DialogBox
{
    private final PropertiesEditor propertiesEditor;
    private TextArea schemaTsv;

    public ExportSchemaWizard(PropertiesEditor propertiesEditor)
    {
        super(false, true);

        this.propertiesEditor = propertiesEditor;

        VerticalPanel vPanel = new VerticalPanel();
        vPanel.setSpacing(5);

        Label caption = new Label("The schema can be copied from the text area below:");
        vPanel.add(caption);

        schemaTsv = new TextArea();
        schemaTsv.setCharacterWidth(80);
        schemaTsv.setHeight("300px");
        schemaTsv.setName("tsv");
        schemaTsv.setText(getTsv());
        DOM.setElementAttribute(schemaTsv.getElement(), "id", "schemaImportBox");
        vPanel.add(schemaTsv);

        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(5);

        buttonPanel.add(new ImageButton("Done", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                ExportSchemaWizard.this.hide();
            }
        }));
        vPanel.add(buttonPanel);

        HorizontalPanel mainPanel = new HorizontalPanel();
        mainPanel.setSpacing(10);
        mainPanel.add(vPanel);

        setWidget(mainPanel);
    }

    private String getTsv()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Property").append("\t");
        sb.append("Label").append("\t");
        sb.append("RangeURI").append("\t");
        sb.append("Format").append("\t");
        sb.append("NotNull").append("\t");
        sb.append("Description").append("\n");

        int numProps = propertiesEditor.getPropertyCount();

        for (int i=0; i<numProps; i++)
        {
            GWTPropertyDescriptor prop = propertiesEditor.getPropertyDescriptor(i);
            sb.append(getStringValue(prop.getName())).append("\t");
            sb.append(getStringValue(prop.getLabel())).append("\t");
            sb.append(getStringValue(prop.getRangeURI())).append("\t");
            sb.append(getStringValue(prop.getFormat())).append("\t");
            sb.append(getStringValue(prop.isRequired())).append("\t");
            sb.append(getStringValue(prop.getDescription())).append("\n");
        }

        return sb.toString();
    }

    private String getStringValue(Object value)
    {
        if (value == null)
            return "";
        if (value instanceof Boolean)
        {
            if (value.equals(Boolean.TRUE))
                return "TRUE";
            return "FALSE";
        }

        return value.toString();
    }
}
