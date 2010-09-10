/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.demo;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.demo.model.DemoManager;
import org.labkey.demo.model.Person;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import org.springframework.web.servlet.mvc.SimpleFormController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.util.*;


/**
 * View action is like MultiActionController, but each action is a class not a method
 */
public class DemoController extends SpringActionController
{
    private final static Logger _log = Logger.getLogger(DemoController.class);
    private final static DefaultActionResolver _actionResolver = new DefaultActionResolver(DemoController.class);

    public DemoController()
    {
        setActionResolver(_actionResolver);
    }

    public PageConfig defaultPageConfig()
    {
        return new PageConfig();
    }


    /**
     * This is a minimal Spring controller, so it doesn't have getViewContext()
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends AbstractController implements NavTrailAction
    {
        public ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
        {                                                                    
            GridView gridView = new GridView(getDataRegion(), (BindException)null);
            gridView.setSort(new Sort("LastName"));
            return gridView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Demo", getURL());
        }

        public ActionURL getURL()
        {
            return new ActionURL(BeginAction.class, getContainer());
        }
    }



    /**
     * Here is another Spring controller using a Spring base class
     */

    @RequiresPermissionClass(InsertPermission.class)
    public class InsertAction extends SimpleFormController implements NavTrailAction, Validator
    {
        public InsertAction()
        {
            setCommandClass(Person.class);
            setValidator(this);
        }

        public boolean supports(Class clazz)
        {
            return Person.class.isAssignableFrom(clazz);
        }

        public void validate(Object target, Errors errors)
        {
            DemoManager.validate((Person)target, errors);
        }

        protected ModelAndView showForm(HttpServletRequest request, HttpServletResponse response, BindException errors, Map controlModel) throws Exception
        {
            return new InsertView(getDataRegion(), errors);
        }

        protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, Object command, BindException errors) throws Exception
        {
            Person person = (Person)command;

            try
            {
                DemoManager.getInstance().insertPerson(getViewContext().getContainer(), getViewContext().getUser(), person);
                return HttpView.redirect(new BeginAction().getURL());
            }
            catch (SQLException x)
            {
                errors.addError(new ObjectError("main", null, null, "Insert failed: " + x.getMessage()));
            }
            return showForm(request, response, errors, null);
        }


        public NavTree appendNavTrail(NavTree root)
        {
            (new BeginAction()).appendNavTrail(root).addChild("Insert Person", getURL());
            return root;
        }

        public ActionURL getURL()
        {
            return new ActionURL(InsertAction.class, getContainer());
        }
    }


    /**
     * This is uses my own home-brewed base class FormViewAction
     *
     * Using PersonForm (rather than Person) to make UpdateView reshow work  
     */
    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateAction extends FormViewAction<PersonForm>
    {
        Person _person = null;
        
        public boolean handlePost(PersonForm form, BindException errors) throws Exception
        {
            // Pass in timestamp for optimistic concurrency
            Object ts = null; // ((Map)form.getOldValues()).get("_ts");
            ViewContext context = getViewContext();

            try
            {
                Person person = form.getBean();
                DemoManager.getInstance().updatePerson(context.getContainer(), context.getUser(), person, ts);
                return true;
            }
            catch (Table.OptimisticConflictException x)
            {
                errors.addError(new ObjectError("main", new String[]{"Error"}, new Object[]{x}, x.getMessage()));
                if (x.getErrorCode() == Table.ERROR_ROWVERSION)
                    setReshow(false);
                return false;
            }
        }

        public HttpView getView(PersonForm form, boolean reshow, BindException errors) throws Exception
        {
            // handles case where handlePost wants to force reselect
            if (!reshow)
                form.forceReselect();

            // get Person to generate nav trail later
            if (!form.isDataLoaded())
                form.refreshFromDb();
            _person = form.getBean();

            return new UpdateView(form, errors);
        }

        public ActionURL getSuccessURL(PersonForm personForm)
        {
            return new BeginAction().getURL();
        }

        public void validateCommand(PersonForm personForm, Errors errors)
        {
            DemoManager.validate(personForm.getBean(), errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new BeginAction()).appendNavTrail(root)
                    .addChild(getPageTitle(), getURL(_person));
        }

        public String getPageTitle()
        {
            String name = "";
            if (_person != null)
                name = " -- " + _person.getLastName() + ", " + _person.getFirstName();
            return "Update Record" + name;
        }

        public ActionURL getURL(Person p)
        {
            return new ActionURL(UpdateAction.class, getContainer()).addParameter("rowId", ""+p.getRowId());
        }
    }


    /*
     * Another FormViewAction.
     *
     * Note this returns a true ModelAndView.  bulkUpdate.jsp looks like a typical Spring jsp
     */
    @RequiresPermissionClass(UpdatePermission.class)
    public class BulkUpdateAction extends FormViewAction<BulkUpdateForm> implements NavTrailAction
    {
        public ModelAndView getView(BulkUpdateForm form, boolean reshow, BindException errors) throws Exception
        {
            List<Person> people = Arrays.asList(DemoManager.getInstance().getPeople(getViewContext().getContainer()));
            ModelAndView mv = new ModelAndView("/org/labkey/demo/view/bulkUpdate.jsp");
            mv.addObject("people", people);
            mv.addObject("errors", errors);
            return mv;
        }

        public boolean handlePost(BulkUpdateForm form, BindException errors) throws Exception
        {
            int[] rowIds = form.getRowId();
            String[] firstNames = form.getFirstName();
            String[] lastNames = form.getLastName();
            String[] ageStrings = form.getAge();

            for (int i = 0; i < rowIds.length; i++)
            {
                int rowId = rowIds[i];
                String firstName = firstNames[i];
                String lastName = lastNames[i];
                Integer age = StringUtils.trimToNull(ageStrings[i]) == null ? null : Integer.parseInt(ageStrings[i]);

                Person old = DemoManager.getInstance().getPerson(getViewContext().getContainer(), rowId);
                Person update = new Person(firstName, lastName, age);

                if (!update.equals(old))
                {
                    update.setRowId(old.getRowId());
                    DemoManager.getInstance().updatePerson(getViewContext().getContainer(), getViewContext().getUser(), update, null);
                }
            }
            return true;
        }

        public void validateCommand(BulkUpdateForm form, Errors errors)
        {
            form.validate(errors);
        }

        public ActionURL getSuccessURL(BulkUpdateForm o)
        {
            return new BeginAction().getURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new BeginAction()).appendNavTrail(root).addChild("Bulk Update", getURL());
        }

        public ActionURL getURL()
        {
            return new ActionURL(BulkUpdateAction.class, getContainer());
        }
    }


    /**
     * This pattern is actually a little different than a typical form.  Should have a ConfirmViewAction
     * base class
     */
    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteAction extends FormViewAction
    {
        public HttpView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            HttpView.throwRedirect(getSuccessURL(o));
            return null;
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Set<String> personIds = DataRegionSelection.getSelected(getViewContext(), true);
            if (personIds != null)
            {
                for (String userId : personIds)
                    DemoManager.getInstance().deletePerson(getViewContext().getContainer(), Integer.parseInt(userId));
            }
            return true;
        }

        public void validateCommand(Object o, Errors errors)
        {
        }

        public ActionURL getSuccessURL(Object o)
        {
            return new BeginAction().getURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private DataRegion getDataRegion()
    {
        DataRegion rgn = new DataRegion();
        TableInfo tableInfo = DemoSchema.getInstance().getTableInfoPerson();
        rgn.setColumns(tableInfo.getColumns("RowId, FirstName, LastName, Age"));

        DisplayColumn col = rgn.getDisplayColumn("RowId");
        col.setURL("update.view?rowId=${RowId}");
        col.setDisplayPermission(UpdatePermission.class);

        ButtonBar gridButtonBar = new ButtonBar();
        rgn.setShowRecordSelectors(true);

        ActionButton delete = new ActionButton(DeleteAction.class, "Delete");
        delete.setActionType(ActionButton.Action.POST);
        delete.setDisplayPermission(DeletePermission.class);
        gridButtonBar.add(delete);

        ActionButton insert = new ActionButton(InsertAction.class, "Add Person");
        insert.setURL(new InsertAction().getURL().getLocalURIString());
        insert.setDisplayPermission(InsertPermission.class);
        gridButtonBar.add(insert);

        ActionButton update = new ActionButton(new BulkUpdateAction().getURL(), "Bulk Update");
        update.setDisplayPermission(UpdatePermission.class);
        gridButtonBar.add(update);

        rgn.setButtonBar(gridButtonBar, DataRegion.MODE_GRID);
        return rgn;
    }


    public static class PersonForm extends BeanViewForm<Person>
    {
        public PersonForm()
        {
            super(Person.class, DemoSchema.getInstance().getTableInfoPerson());
        }


        public PersonForm(int rowid)
        {
            this();
            set("rowid", String.valueOf(rowid));
        }


        // spring
        public void validate(BindException errors)
        {
            // assume bind errors were taken care of already
            DemoManager.validate(getBean(), errors);
        }
    }


    public static class BulkUpdateForm
    {
        private String[] _firstName;
        private String[] _lastName;
        private int[] _rowId;
        private String[] _age;
        private Integer[] _validatedAge;

        public String[] getAge()
        {
            return _age;
        }

        public void setAge(String[] age)
        {
            _age = age;
        }

        public String[] getFirstName()
        {
            return _firstName;
        }

        public void setFirstName(String[] firstName)
        {
            _firstName = firstName;
        }

        public String[] getLastName()
        {
            return _lastName;
        }

        public void setLastName(String[] lastName)
        {
            _lastName = lastName;
        }

        public int[] getRowId()
        {
            return _rowId;
        }

        public void setRowId(int[] rowId)
        {
            _rowId = rowId;
        }

        Person getPerson(int i)
        {
            Person p = new Person(_firstName[i], _lastName[i], _validatedAge[i]);
            p.setRowId(_rowId[i]);
            return p;
        }

        public void validate(Errors errors)
        {
            _validatedAge = new Integer[_rowId.length];
            for (int i = 0; i < _rowId.length; i++)
            {
                try
                {
                    if (null != _age[i])
                        _validatedAge[i] = Integer.parseInt(_age[i]);
                }
                catch (NumberFormatException x)
                {
                    //errors.addError(new ObjectError("age", new String[]{"ConversionError"}, new Object[]{x}, null));
                    errors.rejectValue("age", "ConversionError", new Object[]{x}, null);
                }
            }

            for (int i = 0; i < _rowId.length; i++)
            {
                Person p = getPerson(i);
                DemoManager.validate(p, errors);
            }
        }
    }

    /*
     * This code is for testing various form binding scenarios 
     */


    public static class SubBean
    {
        private int x;
        private String s;
        private double y;

        public int getX()
        {
            return x;
        }

        public void setX(int x)
        {
            this.x = x;
        }

        public String getS()
        {
            return s;
        }

        public void setS(String s)
        {
            this.s = s;
        }

        public double getY()
        {
            return y;
        }

        public void setY(double y)
        {
            this.y = y;
        }
    }


    public static class BindActionBean
    {
        private String[] multiString;
        private int i;
        private int j = -1;
        private Integer k;
        private Integer l;
        private String s;
        private Date d;
        private SubBean sub;
        private ArrayList<String> indexString = new ArrayList<String>();
        private ArrayList<String> listString = new ArrayList<String>();
        private ArrayList<SubBean> listBean = new FormArrayList<SubBean>(SubBean.class);

        public int getI()
        {
            return i;
        }

        public void setI(int i)
        {
            this.i = i;
        }

        public int getJ()
        {
            return j;
        }

        public void setJ(int j)
        {
            this.j = j;
        }

        public Integer getK()
        {
            return k;
        }

        public void setK(Integer k)
        {
            this.k = k;
        }

        public Integer getL()
        {
            return l;
        }

        public void setL(Integer l)
        {
            this.l = l;
        }

        public String getS()
        {
            return s;
        }

        public void setS(String s)
        {
            this.s = s;
        }

        public SubBean getSub()
        {
            if (sub == null)
                sub = new SubBean();
            return sub;
        }

        public void setSub(SubBean sub)
        {
            this.sub = sub;
        }

        public Date getD()
        {
            return d;
        }

        public void setD(Date d)
        {
            this.d = d;
        }

        public String[] getMultiString()
        {
            return multiString;
        }

        public void setMultiString(String[] multiString)
        {
            this.multiString = multiString;
        }

        public void setIndexString(int i, String s)
        {
            while (indexString.size() <= i)
                indexString.add(null);
            indexString.set(i,s);
        }

        public String getIndexString(int i)
        {
            return i < indexString.size() ? indexString.get(i) : null;
        }

        public ArrayList<String> getListString()
        {
            return listString;
        }

        public void setListString(ArrayList<String> listString)
        {
            this.listString = listString;
        }

        public ArrayList<SubBean> getListBean()
        {
            return listBean;
        }

        public void setListBean(ArrayList<SubBean> listBean)
        {
            this.listBean = listBean;
        }
    }


    /** Test action not related to demo data */
    @RequiresPermissionClass(ReadPermission.class)
    public class BindAction extends SimpleViewAction<BindActionBean>
    {
        public BindAction()
        {
            super(BindActionBean.class);
        }

        public ModelAndView handleRequest() throws Exception
        {
            // if no values were posted, use test parameters
            if (null == getPropertyValues() || getPropertyValues().getPropertyValues().length == 0)
            {
                MutablePropertyValues m = new MutablePropertyValues();

                // simple properties
                m.addPropertyValue("i", "100");
                //m.addPropertyValue("j", null);
                m.addPropertyValue("k", "100");
                //m.addPropertyValue("l", null);
                m.addPropertyValue("s", "Ob La Di");
                m.addPropertyValue("d", "4 Jul 2007");

                // array properties
                m.addPropertyValue("multiString", Arrays.asList("Desmond", "Molly", "Jones"));

                // sub properties
                m.addPropertyValue("sub.x", "1");
                m.addPropertyValue("sub.s", "one");
                m.addPropertyValue("sub.y", "1.4");

                // list property
                m.addPropertyValue("listString[2]", "Happy ever after in the marketplace");

                // list property
                m.addPropertyValue("listBean[0].x", "5");
                m.addPropertyValue("listBean[0].s", "five");
                m.addPropertyValue("listBean[0].y", "5.1");

                // indexed property (String) DOES NOT WORK
                m.addPropertyValue("indexString[2]", "Molly is a singer in the band");

                setProperties(m);
            }
            return super.handleRequest();
        }

        public ModelAndView getView(BindActionBean form, BindException errors) throws Exception
        {
            ModelAndView mv = new ModelAndView("/org/labkey/demo/view/bindTest.jsp");
            mv.addObject("form", form);
            mv.addObject("errors", errors);
            return mv;
        }


        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(getPageTitle());
        }


        public String getPageTitle()
        {
            return "Form Binding Test";
        }
    }
}
