/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package gwt.client.org.labkey.specimen.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.BoundTextBox;
import org.labkey.api.gwt.client.ui.DirtyCallback;
import org.labkey.api.gwt.client.ui.PropertiesEditor;
import org.labkey.api.gwt.client.ui.SaveButtonBar;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpecimenDesignerMainPanel extends VerticalPanel implements Saveable<List<String>>, DirtyCallback
{
    private RootPanel _rootPanel;
    private SpecimenServiceAsync _testService;

    private GWTDomain<GWTPropertyDescriptor> domainEvent;
    private GWTDomain<GWTPropertyDescriptor> domainVial;
    private GWTDomain<GWTPropertyDescriptor> domainSpecimen;

    private boolean _dirty;
    private List<PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>> _domainEditors = new ArrayList<PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>>();
    private HTML _statusLabel = new HTML("<br/>");
    private static final String STATUS_SUCCESSFUL = "Save successful.<br/>";
    private final String _returnURL;
    private SaveButtonBar saveBarTop;
    private SaveButtonBar saveBarBottom;
    private HandlerRegistration _closeHandlerManager;
    private String _designerURL;

    private boolean _saveInProgress = false;

    public SpecimenDesignerMainPanel(RootPanel rootPanel)
    {
        _rootPanel = rootPanel;

        _designerURL = Window.Location.getHref();
        _returnURL = PropertyUtil.getReturnURL();
    }

    public void showAsync()
    {
        _rootPanel.clear();
        _rootPanel.add(new Label("Loading..."));

        getService().getDomainDescriptors(new AsyncCallback<List<GWTDomain<GWTPropertyDescriptor>>>()
        {
            public void onFailure(Throwable throwable)
            {
                addErrorMessage("Unable to load specimen properties definition: " + throwable.getMessage());
            }

            public void onSuccess(List<GWTDomain<GWTPropertyDescriptor>> domains)
            {
                domainEvent = domains.get(0);
                domainVial = domains.get(1);
                domainSpecimen = domains.get(2);
                show();
            }
        });
    }


    private SpecimenServiceAsync getService()
    {
        if (_testService == null)
        {
            _testService = GWT.create(SpecimenService.class);
            ServiceUtil.configureEndpoint(_testService, "service", "study-samples");
        }
        return _testService;
    }


    private void show()
    {
        _rootPanel.clear();
        _domainEditors.clear();
        saveBarTop = new SaveButtonBar(this);
        _rootPanel.add(saveBarTop);
        _rootPanel.add(_statusLabel);

        for (GWTDomain<GWTPropertyDescriptor> domain : Arrays.asList(domainEvent, domainVial, domainSpecimen))
        {
            _rootPanel.add(new HTML("<br/>"));

            PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> editor =
                    new PropertiesEditor.PD(_rootPanel, null, getService());
            editor.addChangeHandler(new ChangeHandler()
            {
                public void onChange(ChangeEvent e)
                {
                    setDirty(true);
                }
            });

            // Make sure required properties cannot be edited or moved
            for (GWTPropertyDescriptor property : domain.getFields())
                if (property.isRequired())
                {
                    property.setDisableEditing(true);
                    property.setPreventReordering(true);
                }

            editor.init(domain);
            _domainEditors.add(editor);

            VerticalPanel vPanel = new VerticalPanel();
            if (domain.getDescription() != null)
            {
                vPanel.add(new Label(domain.getDescription()));
            }
            vPanel.add(editor.getWidget());

            final WebPartPanel panel = new WebPartPanel(domain.getName(), vPanel);
            panel.setWidth("100%");
            _rootPanel.add(panel);
        }

        _rootPanel.add(new HTML("<br/>"));
        saveBarBottom = new SaveButtonBar(this);
        _rootPanel.add(saveBarBottom);

        _closeHandlerManager = Window.addWindowClosingHandler(new AssayCloseListener());
    }


    protected void addErrorMessage(String message)
    {
        VerticalPanel mainPanel = new VerticalPanel();
        mainPanel.add(new Label(message));
        _rootPanel.add(mainPanel);
    }

    public void setDirty(boolean dirty)
    {
        if (dirty && _statusLabel.getText().equalsIgnoreCase(STATUS_SUCCESSFUL))
            _statusLabel.setHTML("<br/>");

        setAllowSave(dirty);

        _dirty = dirty;
    }

    public boolean isDirty()
    {
        return _dirty;
    }

    private void setAllowSave(boolean dirty)
    {
        if (_saveInProgress)
        {
            if (saveBarTop != null)
                saveBarTop.disableAll();
            if (saveBarBottom != null)
                saveBarBottom.disableAll();
        }
        else
        {
            if (saveBarTop != null)
                saveBarTop.setAllowSave(dirty);
            if (saveBarBottom != null)
                saveBarBottom.setAllowSave(dirty);
        }
    }

    private boolean validate()
    {
//        List<String> errors = new ArrayList<String>();
//        String error = _nameBox.validate();
//        if (error != null)
//            errors.add(error);
//
//        int numProps = 0;
//
//        // Get the errors for each of the PropertiesEditors
//        for (PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> propeditor : _domainEditors)
//        {
//            List<String> domainErrors = propeditor.validate();
//            if (domainErrors.size() > 0)
//                errors.addAll(domainErrors);
//        }
//
//        if (_isPlateBased && _assay.getSelectedPlateTemplate() == null)
//            errors.add("You must select a plate template from the list, or create one first.");
//
//        if (_transformScriptTable != null)
//        {
//            for (int row = 0; row < _transformScriptTable.getRowCount(); row++)
//            {
//                BoundTextBox boundTextBox = getTransformScriptTextBox(row);
//                if (!boundTextBox.checkValid())
//                {
//                    errors.add(boundTextBox.validate());
//                }
//            }
//        }
//
//        if (errors.size() > 0)
//        {
//            String errorString = "";
//            for (int i = 0; i < errors.size(); i++)
//            {
//                if (i > 0)
//                    errorString += "\n";
//                errorString += errors.get(i);
//            }
//            Window.alert(errorString);
//            return false;
//        }
//        else
            return true;
    }

//    private BoundTextBox getTransformScriptTextBox(int row)
//    {
//        return (BoundTextBox) _transformScriptTable.getWidget(row, TRANSFORM_SCRIPT_PATH_COLUMN_INDEX);
//    }

    public String getCurrentURL()
    {
        return _designerURL;
    }

    public void save()
    {
        save(null);
    }

    public void save(final SaveListener<List<String>> listener)
    {
        saveAsync(new ErrorDialogAsyncCallback<List<String>>("Save failed")
        {
            @Override
            protected void handleFailure(String message, Throwable caught)
            {
                _saveInProgress = false;
                setDirty(true);
            }

            public void onSuccess(List<String> result)
            {
                _saveInProgress = false;
                setDirty(false);
                _statusLabel.setHTML(STATUS_SUCCESSFUL);
                show();

                if (listener != null)
                {
                    listener.saveSuccessful(result, _designerURL);
                }
            }
        });
        setAllowSave(false);
    }


    private void saveAsync(AsyncCallback<List<String>> callback)
    {
        if (validate())
        {
            List<GWTDomain<GWTPropertyDescriptor>> domains = new ArrayList<GWTDomain<GWTPropertyDescriptor>>();

            for (PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor> domainEditor : _domainEditors)
            {
                domains.add(domainEditor.getUpdates());
            }

            GWTDomain<GWTPropertyDescriptor> updateEvent = _domainEditors.get(0).getUpdates();
            GWTDomain<GWTPropertyDescriptor> updateVial = _domainEditors.get(1).getUpdates();
            GWTDomain<GWTPropertyDescriptor> updateSpecimen = _domainEditors.get(2).getUpdates();

            getService().updateDomainDescriptors(
                    updateEvent,
                    updateVial,
                    updateSpecimen,
                    callback
            );
        }
    }


    public void cancel()
    {
        // if the user has canceled, we don't need to run the dirty page checking
        if (_closeHandlerManager != null)
        {
            _closeHandlerManager.removeHandler();
            _closeHandlerManager = null;
        }
        String url = _returnURL;
        if (url == null)
        {
            url = PropertyUtil.getContextPath() + "/study" + PropertyUtil.getContainerPath() + "/manageStudy.view";
        }
        WindowUtil.setLocation(url);
    }


    public void finish()
    {
        final String doneLink;
        if (_returnURL != null)
            doneLink = _returnURL;
        else
            doneLink = PropertyUtil.getContextPath() + "/study" + PropertyUtil.getContainerPath() + "/manageStudy.view";

        if (!_dirty)
        {
            // No need to save
            WindowUtil.setLocation(doneLink);
        }
        else
        {
            saveAsync(new ErrorDialogAsyncCallback<List<String>>("Save failed")
            {
                @Override
                protected void handleFailure(String message, Throwable caught)
                {
                    _saveInProgress = false;
                    setDirty(true);
                }
                
                public void onSuccess(List<String> result)
                {
                    if (_closeHandlerManager != null)
                    {
                        _closeHandlerManager.removeHandler();
                        _closeHandlerManager = null;
                    }
                    WindowUtil.setLocation(doneLink);
                }
            });
        }
    }


    class AssayCloseListener implements Window.ClosingHandler
    {
        public void onWindowClosing(Window.ClosingEvent event)
        {
            boolean dirty = _dirty;
            for (int i = 0; i < _domainEditors.size() && !dirty; i++)
            {
                dirty = _domainEditors.get(i).isDirty();
            }
            if (dirty)
                event.setMessage("Changes have not been saved and will be discarded.");
        }
    }


    class ValidatorTextBox extends BoundTextBox
    {
        private BooleanProperty _debugMode;
        private boolean _allowSpacesInPath;

        public ValidatorTextBox(String caption, String id, String initialValue, WidgetUpdatable updatable,
                                DirtyCallback dirtyCallback, BooleanProperty debugMode, boolean allowSpacesInPath)
        {
            super(caption, id, initialValue, updatable, dirtyCallback);
            _debugMode = debugMode;
            _allowSpacesInPath = allowSpacesInPath;
        }

        @Override
        protected String validateValue(String text)
        {
            if (!_allowSpacesInPath && _debugMode.booleanValue())
            {
                if (text.contains(" "))
                    return _caption + ": The path to the script should not contain spaces when the Save Script Data check box is selected.";
            }
            return super.validateValue(text);
        }
    }
}
