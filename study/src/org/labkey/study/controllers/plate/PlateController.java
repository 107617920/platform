/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.study.controllers.plate;

import org.apache.log4j.Logger;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.view.*;
import org.labkey.api.study.*;
import org.labkey.api.study.assay.PlateUrls;
import org.labkey.api.gwt.client.util.ColorGenerator;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.study.plate.PlateDataServiceImpl;
import org.labkey.study.plate.PlateManager;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.*;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Jan 29, 2007
 * Time: 3:53:57 PM
 */
public class PlateController extends SpringActionController
{
    private static final SpringActionController.DefaultActionResolver _actionResolver = new DefaultActionResolver(PlateController.class);
    private static final Logger _log = Logger.getLogger(PlateController.class);

    public PlateController()
    {
        super();
        setActionResolver(_actionResolver);
    }

    public static class PlateUrlsImpl implements PlateUrls
    {
        public ActionURL getPlateTemplateListURL(Container c)
        {
            return new ActionURL(PlateTemplateListAction.class, c);
        }

        public ActionURL getPlateDetailsURL(Container c)
        {
            return new ActionURL(PlateDetailsAction.class, c);
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(new ActionURL(PlateTemplateListAction.class, getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class PlateTemplateListForm
    {
        private ReturnURLString _returnURL;

        public ReturnURLString getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(ReturnURLString returnURL)
        {
            _returnURL = returnURL;
        }
    }

    public static class PlateTemplateListBean
    {
        private PlateTemplate[] _templates;
        public PlateTemplateListBean(PlateTemplate[] templates)
        {
            _templates = templates;
        }

        public PlateTemplate[] getTemplates()
        {
            return _templates;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class PlateTemplateListAction extends SimpleViewAction<PlateTemplateListForm>
    {
        public ModelAndView getView(PlateTemplateListForm plateTemplateListForm, BindException errors) throws Exception
        {
            PlateTemplate[] plateTemplates = PlateService.get().getPlateTemplates(getContainer());
            return new JspView<PlateTemplateListBean>("/org/labkey/study/plate/view/plateTemplateList.jsp",
                    new PlateTemplateListBean(plateTemplates));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Plate Templates");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DesignerServiceAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            PlateDataServiceImpl service = new PlateDataServiceImpl(getViewContext());
            service.doPost(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class RowIdForm
    {
        private int _rowId;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class PlateDetailsAction extends SimpleViewAction<RowIdForm>
    {
        public ModelAndView getView(RowIdForm form, BindException errors) throws Exception
        {
            Plate plate = PlateService.get().getPlate(getContainer(), form.getRowId());
            if (plate == null)
                return HttpView.throwNotFound("Plate " + form.getRowId() + " does not exist.");
            ActionURL url = PlateManager.get().getDetailsURL(plate);
            if (url == null)
                return HttpView.throwNotFound("Details URL has not been configured for plate type " + plate.getName() + ".");

            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public class TemplateViewBean
    {
        private PlateTemplate _template;
        private ColorGenerator _colorGenerator;
        private WellGroup.Type _type;

        public TemplateViewBean(PlateTemplate template, WellGroup.Type type)
        {
            _template = template;
            _type = type;
            _colorGenerator = new ColorGenerator();
        }

        public ColorGenerator getColorGenerator()
        {
            return _colorGenerator;
        }

        public PlateTemplate getTemplate()
        {
            return _template;
        }

        public WellGroup.Type getType()
        {
            return _type;
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class DesignerAction extends SimpleViewAction<NameForm>
    {
        public ModelAndView getView(NameForm form, BindException errors) throws Exception
        {
            Map<String, String> properties = new HashMap<String, String>();
            if (form.getTemplateName() != null)
            {
                properties.put("copyTemplate", Boolean.toString(form.isCopy()));
                properties.put("templateName", form.getTemplateName());
                if (form.isCopy())
                    properties.put("defaultPlateName", getUniqueName(getContainer(), form.getTemplateName()));
                else
                    properties.put("defaultPlateName", form.getTemplateName());
            }
            if (form.getAssayType() != null)
            {
                properties.put("assayTypeName", form.getAssayType());
            }

            if (form.getTemplateType() != null)
            {
                properties.put("templateTypeName", form.getTemplateType());
            }

            PlateTemplate[] templates = PlateService.get().getPlateTemplates(getContainer());
            for (int i = 0; i < templates.length; i++)
            {
                PlateTemplate template = templates[i];
                properties.put("templateName[" + i + "]", template.getName());
            }
            return new GWTView("org.labkey.plate.designer.TemplateDesigner", properties);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Plate Template Editor");
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class DeleteAction extends SimpleViewAction<NameForm>
    {
        public ModelAndView getView(NameForm form, BindException errors) throws Exception
        {
            PlateTemplate[] templates = PlateService.get().getPlateTemplates(getContainer());
            if (templates != null && templates.length > 1)
            {
                PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), form.getTemplateName());
                if (template != null)
                    PlateService.get().deletePlate(getContainer(), template.getRowId());
            }
            return HttpView.redirect(new ActionURL(BeginAction.class, getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class CopyTemplateBean
    {
        private String _treeHtml;
        private String _templateName;
        private String _selectedDestination;
        private PlateTemplate[] _destinationTemplates;

        public CopyTemplateBean(final Container container, final User user, final String templateName, final String selectedDestination)
        {
            _templateName = templateName;
            _selectedDestination = selectedDestination;
            ContainerTree tree = new ContainerTree("/", user, ACL.PERM_INSERT, null)
            {
                @Override
                protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
                {
                    ActionURL copyURL = new ActionURL(CopyTemplateAction.class, container);
                    copyURL.addParameter("templateName", templateName);
                    copyURL.addParameter("destination", c.getPath());
                    boolean selected = c.getPath().equals(selectedDestination);
                    if (selected)
                        html.append("<span class=\"labkey-nav-tree-selected\">");
                    html.append("<a href=\"");
                    html.append(copyURL.getEncodedLocalURIString());
                    html.append("\">");
                    html.append(PageFlowUtil.filter(c.getName()));
                    html.append("</a>");
                    if (selected)
                        html.append("</span>");
                }
            };

            if (_selectedDestination != null)
            {
                Container dest = ContainerManager.getForPath(_selectedDestination);
                if (dest != null)
                {
                    try
                    {
                        _destinationTemplates = PlateService.get().getPlateTemplates(dest);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                }
            }

            _treeHtml = tree.render().toString();
        }

        public String getSelectedDestination()
        {
            return _selectedDestination;
        }

        public String getTreeHtml()
        {
            return _treeHtml;
        }

        public String getTemplateName()
        {
            return _templateName;
        }

        public PlateTemplate[] getDestinationTemplates()
        {
            return _destinationTemplates;
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class CopyTemplateAction extends FormViewAction<CopyForm>
    {
        public void validateCommand(CopyForm form, Errors errors)
        {
        }

        public ModelAndView getView(CopyForm form, boolean reshow, BindException errors) throws Exception
        {
            if (form.getTemplateName() == null || form.getTemplateName().length() == 0)
                return HttpView.redirect(new ActionURL(BeginAction.class, getContainer()));

            return new JspView<CopyTemplateBean>("/org/labkey/study/plate/view/copyTemplate.jsp",
                    new CopyTemplateBean(getContainer(), getUser(), form.getTemplateName(), form.getDestination()), errors);
        }

        public boolean handlePost(CopyForm form, BindException errors) throws Exception
        {
            return true;
        }

        public ActionURL getSuccessURL(CopyForm copyForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Select Copy Destination");
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class HandleCopyAction extends CopyTemplateAction
    {
        public void validateCommand(CopyForm form, Errors errors)
        {
            Container destination = ContainerManager.getForPath(form.getDestination());
            if (destination == null || !destination.hasPermission(getUser(), ACL.PERM_INSERT))
                errors.reject("copyForm", "Destination container does not exist or permission is denied.");

            PlateTemplate destinationTemplate = null;
            try
            {
                destinationTemplate = PlateService.get().getPlateTemplate(destination, form.getTemplateName());
            }
            catch (SQLException e)
            {
                _log.error("Failure checking for template in destination container", e);
                errors.reject("copyForm", "Unable to validate destination directory: " + e.getMessage());
            }

            if (destinationTemplate != null)
                errors.reject("copyForm", "A plate template with the same name already exists in the destination folder.");
        }

        public boolean handlePost(CopyForm form, BindException errors) throws Exception
        {
            Container destination = ContainerManager.getForPath(form.getDestination());
            // earlier validation should prevent a null or inaccessible destination container:
            if (destination == null || !destination.hasPermission(getUser(), ACL.PERM_INSERT))
            {
                errors.reject("copyForm", "The destination is invalid or you do not have INSERT privileges on the specified container");
                return false;
            }
            // earlier validation should prevent a missing source template:
            PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), form.getTemplateName());
            if (template == null)
            {
                errors.reject("copyForm", "Plate " + form.getTemplateName() + " does not exist in source container.");
                return false;
            }

            // earlier validation should prevent an already-existing destination template:
            PlateTemplate destinationTemplate = PlateService.get().getPlateTemplate(destination, form.getTemplateName());
            if (destinationTemplate != null)
            {
                errors.reject("copyForm", "Plate " + form.getTemplateName() + " already exists in destination container.");
                return false;
            }
            PlateService.get().copyPlateTemplate(template, getUser(), destination);
            return true;
        }

        public ActionURL getSuccessURL(CopyForm copyForm)
        {
            return new ActionURL(PlateTemplateListAction.class, getContainer());
        }
    }

    private String getUniqueName(Container container, String originalName) throws SQLException
    {
        PlateTemplate[] templates = PlateService.get().getPlateTemplates(container);
        Set<String> existing = new HashSet<String>();
        for (PlateTemplate template : templates)
            existing.add(template.getName());
        String baseUniqueName;
        if (!originalName.startsWith("Copy of "))
            baseUniqueName = "Copy of " + originalName;
        else
            baseUniqueName = originalName;
        String uniqueName = baseUniqueName;
        int idx = 2;
        while (existing.contains(uniqueName))
            uniqueName = baseUniqueName + " " + idx++;
        return uniqueName;
    }

    public static class NameForm
    {
        private String _templateName;
        private String _assayType;
        private String _templateType;
        private boolean _copy;

        public String getTemplateName()
        {
            return _templateName;
        }

        public void setTemplateName(String templateName)
        {
            _templateName = templateName;
        }

        public String getAssayType()
        {
            return _assayType;
        }

        public void setAssayType(String assayType)
        {
            _assayType = assayType;
        }

        public String getTemplateType()
        {
            return _templateType;
        }

        public void setTemplateType(String templateType)
        {
            _templateType = templateType;
        }

        public boolean isCopy()
        {
            return _copy;
        }

        public void setCopy(boolean copy)
        {
            _copy = copy;
        }
    }

    public static class CopyForm
    {
        private String _destination;
        private String _templateName;

        public String getDestination()
        {
            return _destination;
        }

        public void setDestination(String destination)
        {
            _destination = destination;
        }

        public String getTemplateName()
        {
            return _templateName;
        }

        public void setTemplateName(String templateName)
        {
            _templateName = templateName;
        }
    }
}
