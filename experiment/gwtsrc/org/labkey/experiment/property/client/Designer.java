/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.experiment.property.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowCloseListener;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.PropertiesEditor;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.DefaultValueType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 4, 2007
 * Time: 3:21:44 PM
 */
public class Designer implements EntryPoint, Saveable<GWTDomain>
{
    private String _returnURL;
    private boolean _allowFileLinkProperties;
    private boolean _allowAttachmentProperties;
    private boolean _showDefaultValueSettings;

    private GWTDomain _domain;

    // UI bits
    private RootPanel _root = null;
    private CellPanel _buttons = null;
    private Label _loading = null;
    private PropertiesEditor _propTable = null;
    boolean _saved = false;
    SubmitButton _submitButton = new SubmitButton();

    public void onModuleLoad()
    {
        String typeURI = PropertyUtil.getServerProperty("typeURI");
        _returnURL = PropertyUtil.getServerProperty("returnURL");
        _allowFileLinkProperties = "true".equals(PropertyUtil.getServerProperty("allowFileLinkProperties"));
        _allowAttachmentProperties = "true".equals(PropertyUtil.getServerProperty("allowAttachmentProperties"));
        _showDefaultValueSettings = "true".equals(PropertyUtil.getServerProperty("showDefaultValueSettings"));

        _root = RootPanel.get("org.labkey.experiment.property.Designer-Root");

        _loading = new Label("Loading...");

        _propTable = new PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>(_root, this, getService(), null);

        _buttons = new HorizontalPanel();
        _buttons.add(_submitButton);
        _buttons.add(new HTML("&nbsp;"));
        _buttons.add(new CancelButton());

/*
        FlexTable form = new FlexTable();
        form.setText(0,0,"Name");
        form.setWidget(0,1,new TextBox());
        form.setText(1,0,"Description");
        form.setWidget(1,1,new TextBox());
        _root.add(form);
*/
        _root.add(_loading);

/*        Button b = new Button("show", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                String s = "";
                for (int i=0 ; i<_propTable.getPropertyCount(); i++)
                {
                    GWTPropertyDescriptor p = _propTable.getPropertyDescriptor(i);
                    s += p.debugString() + "\n";
                }
                Window.alert(s);
            }
        });
        _root.add(b); */

        asyncGetDomainDescriptor(typeURI);

        Window.addWindowCloseListener(new WindowCloseListener()
        {
            public void onWindowClosed()
            {
            }

            public String onWindowClosing()
            {
                if (isDirty())
                    return "Changes have not been saved and will be discarded.";
                else
                    return null;
            }
        });
    }


    public boolean isDirty()
    {
        return !_saved && _propTable.isDirty();
    }


    public void setDomain(GWTDomain d)
    {
        if (null == _root)
            return;
        _domain = d;

        _propTable.init(new GWTDomain(d));

        showUI();
    }


    private void showUI()
    {
        if (null != _domain)
        {
            _root.remove(_loading);
            _root.add(_buttons);
            _root.add(_propTable.getWidget());
        }
    }


    class SubmitButton extends ImageButton
    {
        SubmitButton()
        {
            super("Save");
        }

        public void onClick(Widget sender)
        {
            setEnabled(false);
            finish();
        }
    }


    class CancelButton extends ImageButton
    {
        CancelButton()
        {
            super("Cancel");
        }

        public void onClick(Widget sender)
        {
            cancel();
        }
    }

    public void save(final SaveListener<GWTDomain> listener)
    {
        List errors = _propTable.validate();
        if (null != errors && !errors.isEmpty())
        {
            String s = "";
            for (Object error : errors)
                s += error + "\n";
            Window.alert(s);
            _submitButton.setEnabled(true);
            return;
        }

        GWTDomain edited = _propTable.getUpdates();
        getService().updateDomainDescriptor(_domain, edited, new ErrorDialogAsyncCallback<List<String>>("Save failed")
        {
            public void onSuccess(List<String> errors)
            {
                if (null == errors)
                {
                    _saved = true;  // avoid popup warning
                    if (listener != null)
                    {
                        listener.saveSuccessful(_domain, PropertyUtil.getCurrentURL());
                    }
                }
                else
                {
                    String s = "";
                    for (String error : errors)
                        s += error + "\n";
                    Window.alert(s);
                    _submitButton.setEnabled(true);
                }
            }
        });
    }

    public void save()
    {
        save(null);
    }

    public void cancel()
    {
        back();
    }

    public void finish()
    {
        save(new SaveListener<GWTDomain>()
        {
            public void saveSuccessful(GWTDomain domain, String designerUrl)
            {
                if (null == _returnURL || _returnURL.length() == 0)
                    cancel();
                else
                    navigate(_returnURL);
            }
        });

    }

    public static native void navigate(String url) /*-{
      $wnd.location.href = url;
    }-*/;


    public static native void back() /*-{
        $wnd.history.back();
    }-*/;


    /*
     * SERVER CALLBACKS
     */

    private PropertyServiceAsync _service = null;

    private PropertyServiceAsync getService()
    {
        if (_service == null)
        {
            _service = (PropertyServiceAsync) GWT.create(PropertyService.class);
            ServiceUtil.configureEndpoint(_service, "propertyService");
        }
        return _service;
    }


    void asyncGetDomainDescriptor(final String domainURI)
    {
        if (!domainURI.equals("testURI#TYPE"))
        {
            getService().getDomainDescriptor(domainURI, new ErrorDialogAsyncCallback<GWTDomain>()
            {
                    public void handleFailure(String message, Throwable caught)
                    {
                        _loading.setText("ERROR: " + message);
                    }

                    public void onSuccess(GWTDomain domain)
                    {
                        if (domain == null)
                        {
                            String message = "Could not find " + domainURI;
                            Window.alert(message);
                            _loading.setText("ERROR: " + message);
                            return;
                        }

                        domain.setAllowFileLinkProperties(_allowFileLinkProperties);
                        domain.setAllowAttachmentProperties(_allowAttachmentProperties);
                        if (!_showDefaultValueSettings)
                            domain.setDefaultValueOptions(new DefaultValueType[0], null);
                        setDomain(domain);
                    }
            });
        }
        else
        {
            GWTDomain domain = new GWTDomain();
            domain.setDomainURI(domainURI);
            domain.setName("DEM");
            domain.setDescription("I'm a description");

            List<GWTPropertyDescriptor> list = new ArrayList<GWTPropertyDescriptor>();

            GWTPropertyDescriptor p = new GWTPropertyDescriptor();
            p.setName("ParticipantID");
            p.setPropertyURI(domainURI + "." + p.getName());
            p.setRangeURI("xsd:double");
            p.setRequired(true);
            list.add(p);

            p = new GWTPropertyDescriptor();
            p.setName("SequenceNum");
            p.setPropertyURI(domainURI + "." + p.getName());
            p.setRangeURI("xsd:double");
            p.setRequired(true);
            list.add(p);

            p = new GWTPropertyDescriptor();
            p.setPropertyId(2);
            p.setName("DEMsex");
            p.setPropertyURI(domainURI + "." + p.getName());
            p.setRangeURI("xsd:int");
            list.add(p);

            p = new GWTPropertyDescriptor();
            p.setPropertyId(3);
            p.setName("DEMhr");
            p.setPropertyURI(domainURI + "." + p.getName());
            p.setRangeURI("xsd:int");
            list.add(p);

            domain.setFields(list);
            setDomain(domain);
        }
    }
}
