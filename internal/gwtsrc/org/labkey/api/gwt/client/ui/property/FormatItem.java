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
package org.labkey.api.gwt.client.ui.property;

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.TypePicker;
import org.labkey.api.gwt.client.ui.PropertyPane;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.DOM;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class FormatItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private static final String FORMAT_HELP_NUMBER = "The format string for numbers " +
    "must be compatible with the format that the java class <a href=\"http://java.sun.com/j2se/1.4.2/docs/api/java/text/DecimalFormat.html\" target=\"blank\"><code>DecimalFormat</code></a> accepts.<br/>" +
            "<table class=\"labkey-data-region labkey-show-borders\"><colgroup><col><col><col><col></colgroup>" +
            "<tr class=\"labkey-frame\"><th align=left>Symbol<th align=left>Location<th align=left>Localized?<th align=left>Meaning</tr>" +
            "<tr valign=top><td><code>0</code><td>Number<td>Yes<td>Digit</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>#</code><td>Number<td>Yes<td>Digit, zero shows as absent</tr>" +
            "<tr valign=top><td><code>.</code><td>Number<td>Yes<td>Decimal separator or monetary decimal separator</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>-</code><td>Number<td>Yes<td>Minus sign</tr>" +
            "<tr valign=top><td><code>,</code><td>Number<td>Yes<td>Grouping separator</tr></table><br/><br/>";
    private static final String FORMAT_HELP_DATE = "The format string for dates must be compatible with the format that the java class " +
    "<a href=\"http://java.sun.com/j2se/1.4.2/docs/api/java/text/SimpleDateFormat.html\" target=\"blank\"><code>SimpleDateFormat</code></a> accepts.<br/>" +
            "<table class=\"labkey-data-region labkey-show-borders\"><colgroup><col><col><col></colgroup>" +
            "<tr class=\"labkey-frame\"><th align=left>Letter<th align=left>Date or Time Component<th align=left>Examples</tr>" +
            "<tr><td><code>G</code><td>Era designator<td><code>AD</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>y</code><td>Year<td><code>1996</code>; <code>96</code></tr>" +
            "<tr><td><code>M</code><td>Month in year<td><code>July</code>; <code>Jul</code>; <code>07</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>w</code><td>Week in year<td><code>27</code></td></tr>" +
            "<tr><td><code>W</code><td>Week in month<td><code>2</code></td></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>D</code><td>Day in year<td><code>189</code></td></tr>" +
            "<tr><td><code>d</code><td>Day in month<td><code>10</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>F</code><td>Day of week in month<td><code>2</code></tr>" +
            "<tr><td><code>E</code><td>Day in week<td><code>Tuesday</code>; <code>Tue</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>a</code><td>Am/pm marker<td><code>PM</code></tr>" +
            "<tr><td><code>H</code><td>Hour in day (0-23)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>k</code><td>Hour in day (1-24)<td><code>24</code></tr>" +
            "<tr><td><code>K</code><td>Hour in am/pm (0-11)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>h</code><td>Hour in am/pm (1-12)<td><code>12</code></tr></table>";

    private TextBox _formatTextBox = new TextBox();
    private HelpPopup _formatHelpPopup = new HelpPopup("Format Strings", "");
    private boolean _canFormat;

    public FormatItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        _formatTextBox.addChangeListener(createChangeListener());
        _formatTextBox.addKeyboardListener(createKeyboardListener());

        FlowPanel labelPanel = new FlowPanel();
        labelPanel.add(new InlineLabel("Format"));
        labelPanel.add(_formatHelpPopup);
        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);

        DOM.setElementProperty(_formatTextBox.getElement(), "id", "propertyFormat");
        flexTable.setWidget(row, INPUT_COLUMN, _formatTextBox);

        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        if (_formatTextBox.isEnabled())
        {
            boolean changed = !nullSafeEquals(field.getFormat(), trimValue(_formatTextBox.getText()));
            field.setFormat(trimValue(_formatTextBox.getText()));
            return changed;
        }
        return false;
    }

    public void enabledChanged()
    {
        _formatTextBox.setEnabled(isEnabled());
    }

    @Override
    public boolean isEnabled()
    {
        return super.isEnabled() && _canFormat;
    }

    private boolean canFormat(String rangeURI)
    {
        // for now, we only support format strings for datetime and numeric types
        return TypePicker.xsdDateTime.equals(rangeURI) ||
                TypePicker.xsdDouble.equals(rangeURI) ||
                TypePicker.xsdInt.equals(rangeURI);
    }

    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        _formatTextBox.setText(field.getFormat());
        _canFormat = canFormat(field.getRangeURI());
        if (_canFormat)
        {
            _formatHelpPopup.setBody(field.getRangeURI().equals(TypePicker.xsdDateTime) ? FORMAT_HELP_DATE : FORMAT_HELP_NUMBER);
        }
        else
        {
            _formatTextBox.setText("<no format set>");
            _formatHelpPopup.setBody("Format can only be set for Integer, Number, and Date types.");
        }
        enabledChanged();
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        showPropertyDescriptor(null, field);
    }
}