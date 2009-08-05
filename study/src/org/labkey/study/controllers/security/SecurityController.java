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
package org.labkey.study.controllers.security;

import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.roles.*;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.util.*;

/**
 * User: Matthew
 * Date: Apr 24, 2006
 * Time: 5:31:02 PM
 */
public class SecurityController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SecurityController.class);

    public SecurityController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("studySecurity", HelpTopic.Area.STUDY));
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (null == study)
                return HttpView.redirect(new ActionURL(StudyController.BeginAction.class, getContainer()));

            return new Overview(study);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Study Security");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class SaveStudyPermissionsAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            HttpServletRequest request = getViewContext().getRequest();
            Group[] groups = SecurityManager.getGroups(study.getContainer().getProject(), true);
            HashSet<Integer> set = new HashSet<Integer>(groups.length*2);
            for (Group g : groups)
                set.add(g.getUserId());

            MutableSecurityPolicy policy = policyFromPost(request, set, study);

            // Explicitly give site admins read permission, so they can never be locked out
            Group siteAdminGroup = SecurityManager.getGroup(Group.groupAdministrators);
            policy.clearAssignedRoles(siteAdminGroup);
            policy.addRoleAssignment(siteAdminGroup, ReaderRole.class);
            
            study.savePolicy(policy);
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            String redirect = (String)getViewContext().get("redirect");
            if (redirect != null)
                return new ActionURL(redirect);

            return new ActionURL(SecurityController.BeginAction.class, getContainer());
        }

        private MutableSecurityPolicy policyFromPost(HttpServletRequest request, HashSet<Integer> set, Study study)
        {
            MutableSecurityPolicy policy = new MutableSecurityPolicy(study);
            Enumeration i = request.getParameterNames();
            while (i.hasMoreElements())
            {
                String name = (String)i.nextElement();
                if (!name.startsWith("group."))
                    continue;
                String s = name.substring("group.".length());
                int groupid;
                try
                {
                    groupid = Integer.parseInt(s);
                }
                catch (NumberFormatException x)
                {
                    continue;
                }
                if (!set.contains(groupid))
                    continue;
                Group group = SecurityManager.getGroup(groupid);
                if(null == group)
                    continue;

                s = request.getParameter(name);
                if (s.equals("UPDATE"))
                    policy.addRoleAssignment(group, EditorRole.class);
                else if (s.equals("READ"))
                    policy.addRoleAssignment(group, ReaderRole.class);
                else if (s.equals("READOWN"))
                    policy.addRoleAssignment(group, RestrictedReaderRole.class);
                else
                    policy.addRoleAssignment(group, NoPermissionsRole.class);
            }
            return policy;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ApplyDatasetPermissionsAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            Group[] groups = SecurityManager.getGroups(study.getContainer().getProject(), true);
            HashSet<Integer> groupsInProject = new HashSet<Integer>(groups.length*2);
            for (Group g : groups)
                groupsInProject.add(g.getUserId());

            for (DataSet dsDef : study.getDataSets())
            {
                // Data that comes back is a list of permissions and groups separated by underscores.
                // e.g. "NONE_1182" or "READ_-1"
                List<String> permsAndGroups = getViewContext().getList("dataset." + dsDef.getDataSetId());
                Map<Integer,String> group2Perm = convertToGroupsAndPermissions(permsAndGroups);

                if (group2Perm != null)
                {
                    SecurityManager.savePolicy(policyFromPost(group2Perm, groupsInProject, dsDef));
                }
            }
            return true;
        }

        /**
         * convert list of "perm_groupid" strings to a map of groupid -> perm
         */
        private Map<Integer,String> convertToGroupsAndPermissions(List<String> permsAndGroups)
        {
            if (permsAndGroups == null)
                return null;
            Map<Integer,String> groupToPermission = new HashMap<Integer,String>();
            for (String permAndGroup : permsAndGroups)
            {
                int underscoreIndex = permAndGroup.indexOf("_");

                if (underscoreIndex <= 0 || underscoreIndex == permAndGroup.length() - 1)
                    continue;

                String perm = permAndGroup.substring(0, underscoreIndex);

                String gIdString = permAndGroup.substring(underscoreIndex + 1);
                int gid;
                try
                {
                    gid = Integer.parseInt(gIdString);
                }
                catch (NumberFormatException nfe)
                {
                    continue;
                }
                
                groupToPermission.put(gid,perm);
            }
            return groupToPermission;
        }

        private MutableSecurityPolicy policyFromPost(Map<Integer,String> group2Perm, HashSet<Integer> groupsInProject, DataSet dsDef)
        {
            MutableSecurityPolicy policy = new MutableSecurityPolicy(dsDef);

            for (Map.Entry<Integer,String> entry : group2Perm.entrySet())
            {
                int gid = entry.getKey().intValue();
                if (groupsInProject.contains(gid))
                {
                    Group group = SecurityManager.getGroup(gid);
                    if(null == group)
                        continue;

                    String perm = entry.getValue();
                    if ("READ".equals(perm))
                    {
                        policy.addRoleAssignment(group, ReaderRole.class);
                    }
                    else if ("WRITE".equals(perm))
                    {
                        policy.addRoleAssignment(group, EditorRole.class);
                    }
                }
            }
            return policy;
        }

        public ActionURL getSuccessURL(Object o)
        {
            String redirect = (String)getViewContext().get("redirect");
            if (redirect != null)
                return new ActionURL(redirect);

            return new ActionURL(SecurityController.BeginAction.class, getContainer());
        }
    }

    private static class ReportPermissionsTabStrip extends TabStripView
    {
        private PermissionsForm _bean;

        public ReportPermissionsTabStrip(PermissionsForm bean)
        {
            _bean = bean;
        }

        public List<NavTree> getTabList()
        {
            List<NavTree> tabs = new ArrayList<NavTree>(2);

            tabs.add(new TabInfo("Permissions", SecurityController.TAB_REPORT, getViewContext().getActionURL()));
            tabs.add(new TabInfo("Study Security", SecurityController.TAB_STUDY, getViewContext().getActionURL()));

            return tabs;
        }

        public HttpView getTabView(String tabId) throws Exception
        {
            if (TAB_STUDY.equals(tabId))
            {
                StudyImpl study = StudyManager.getInstance().getStudy(getViewContext().getContainer());
                if (null == study)
                {
                    HttpView.throwRedirect(new ActionURL(StudyController.BeginAction.class, getViewContext().getContainer()));
                    return null;
                }
                return new Overview(study, getViewContext().getActionURL());
            }
            else
            {
                ReportIdentifier reportId = _bean.getReportId();
                if (reportId != null)
                    return new JspView<Report>("/org/labkey/study/view/reportPermission.jsp", reportId.getReport());
                else
                {
                    HttpView.throwNotFound();
                    return null;
                }
            }
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ReportPermissionsAction extends FormViewAction<PermissionsForm>
    {
        public ModelAndView getView(PermissionsForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("reportPermissions", HelpTopic.Area.STUDY));
            return new ReportPermissionsTabStrip(form);
        }

        public void validateCommand(PermissionsForm target, Errors errors)
        {
        }

        public boolean handlePost(PermissionsForm form, BindException errors) throws Exception
        {
            Report report = null;
            if (form.getReportId() != null)
                report = form.getReportId().getReport();

            if (null == report)
            {
                HttpView.throwNotFound();
                return false;
            }

            MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityManager.getPolicy(report.getDescriptor()));
            Integer owner = null;

            if (form.getPermissionType().equals(PermissionType.privatePermission.toString()))
            {
                policy = new MutableSecurityPolicy(report.getDescriptor());
                owner = getUser().getUserId();
            }
            else if (form.getPermissionType().equals(PermissionType.defaultPermission.toString()))
            {
                policy = new MutableSecurityPolicy(report.getDescriptor());
            }
            else
            {
                // modify one at a time
                if (form.getRemove() != 0 || form.getAdd() != 0)
                {
                    if (form.getRemove() != 0)
                    {
                        Group group = SecurityManager.getGroup(form.getRemove());
                        if(null != group)
                            policy.addRoleAssignment(group, RoleManager.getRole(NoPermissionsRole.class));
                    }
                    if (form.getAdd() != 0)
                    {
                        Group group = SecurityManager.getGroup(form.getAdd());
                        if(null != group)
                            policy.addRoleAssignment(group, RoleManager.getRole(ReaderRole.class));
                    }
                }
                // set all at once
                else
                {
                    policy = new MutableSecurityPolicy(report.getDescriptor());
                    if (form.getGroups() != null)
                        for (int gid : form.getGroups())
                        {
                            Group group = SecurityManager.getGroup(gid);
                            if(null != group)
                                policy.addRoleAssignment(group, RoleManager.getRole(ReaderRole.class));
                        }
                }
            }
            SecurityManager.savePolicy(policy);
            report.getDescriptor().setOwner(owner);
            ReportService.get().saveReport(getViewContext(), report.getDescriptor().getReportKey(), report);
            return true;
        }

        public ActionURL getSuccessURL(PermissionsForm saveReportForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try {
                Study study = StudyManager.getInstance().getStudy(getContainer());
                root.addChild(study.getLabel(), new ActionURL(StudyController.OverviewAction.class, getContainer()));

                if (getUser().isAdministrator())
                    root.addChild("Manage Views",
                        new ActionURL(ReportsController.ManageReportsAction.class, getContainer()).getLocalURIString());
            }
            catch (Exception e)
            {
                return root.addChild("Report and View Permissions");
            }
            return root.addChild("Report and View Permissions");
        }
    }

    private static class StudySecurityForm
    {
        private SecurityType _securityType;

        public SecurityType getSecurityType() {return _securityType;}

        public void setSecurityString(String s)
        {
            _securityType = SecurityType.valueOf(s);
        }

        public String getSecurityString()
        {
            return _securityType == null ? null : _securityType.name();
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class StudySecurityAction extends FormHandlerAction<StudySecurityForm>
    {
        public void validateCommand(StudySecurityForm target, Errors errors)
        {
        }

        public boolean handlePost(StudySecurityForm form, BindException errors) throws Exception
        {
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null && form.getSecurityType() != study.getSecurityType())
            {
                StudyImpl updated = study.createMutable();
                updated.setSecurityType(form.getSecurityType());
                StudyManager.getInstance().updateStudy(getUser(), updated);
            }
            return true;
        }

        public ActionURL getSuccessURL(StudySecurityForm studySecurityForm)
        {
            String redirect = (String)getViewContext().get("redirect");
            if (redirect != null)
                return new ActionURL(redirect);

            return new ActionURL(SecurityController.BeginAction.class, getContainer());
        }
    }

    public enum PermissionType
    {
        defaultPermission,
        explicitPermission,
        privatePermission,
    }

    public static final String TAB_REPORT = "tabReport";
    public static final String TAB_STUDY = "tabStudy";

    public static class PermissionsForm
    {
        private ReportIdentifier reportId;
        private Integer remove = 0;
        private Integer add = 0;
        private Set<Integer> groups = null;
        private String _permissionType;
        private String _tabId;

        // Not used, but needed for spring binding
        public int[] getGroup()
        {
            return null;
        }

        // use group (multi values) to set acl all at once
        public void setGroup(int[] groupArray)
        {
            groups = new TreeSet<Integer>();

            for (int group : groupArray)
                groups.add(group);
        }

        public Set<Integer> getGroups()
        {
            return groups;
        }

        public ReportIdentifier getReportId()
        {
            return reportId;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            this.reportId = reportId;
        }

        /* add and remove can be used to add/remove single principal */

        public int getRemove()
        {
            return remove == null ? 0 : remove.intValue();
        }

        public void setRemove(Integer remove)
        {
            this.remove = remove;
        }

        public int getAdd()
        {
            return add == null ? 0 : remove.intValue();
        }

        public void setAdd(Integer add)
        {
            this.add = add;
        }
        public void setPermissionType(String type){_permissionType = type;}
        public String getPermissionType(){return _permissionType;}

        public void setTabId(String id){_tabId = id;}
        public String getTabId(){return _tabId;}
    }

    static class Overview extends WebPartView
    {
        HttpView impl;

        Overview(StudyImpl study)
        {
            this(study, null);
        }

        Overview(StudyImpl study, ActionURL redirect)
        {
            JspView<StudyImpl> studySecurityView = new JspView<StudyImpl>("/org/labkey/study/security/studySecurity.jsp", study);
            JspView<StudyImpl> studyView = new JspView<StudyImpl>("/org/labkey/study/security/study.jsp", study);
            studyView.setTitle("Study Security");
            if (redirect != null)
                studyView.addObject("redirect", redirect.getLocalURIString());
            JspView<StudyImpl> dsView = new JspView<StudyImpl>("/org/labkey/study/security/datasets.jsp", study);
            dsView.setTitle("Per Dataset Permissions");
            if (redirect != null)
                dsView.addObject("redirect", redirect.getLocalURIString());
            JspView<StudyImpl> siteView = new JspView<StudyImpl>("/org/labkey/study/security/sites.jsp", study);
            siteView.setTitle("Restricted Dataset Permissions (per Site)");

            VBox v = new VBox();
            v.addView(studySecurityView);
            if (study.getSecurityType() == SecurityType.ADVANCED_READ || study.getSecurityType() == SecurityType.ADVANCED_WRITE)
            {
                v.addView(studyView);
                v.addView(dsView);
            }
            impl = v;
        }


        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            include(impl, out);
        }
    }


    public static class StudySecurityViewFactory implements SecurityManager.ViewFactory
    {
        public HttpView createView(ViewContext context)
        {
            if (StudyManager.getInstance().getStudy(context.getContainer()) != null)
                return new StudySecurityPermissionsView();
            else
                return null;
        }
    }


    private static class StudySecurityPermissionsView extends WebPartView
    {
        private StudySecurityPermissionsView()
        {
            setTitle("Study Security");
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            ActionURL urlStudy = new ActionURL(BeginAction.class, getViewContext().getContainer());
            out.print("<br>Click here to manage permissions for the Study module.<br>");
            out.print(PageFlowUtil.generateButton("Study Security", urlStudy));
        }
    }
}
