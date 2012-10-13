/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.study.controllers;

import org.apache.commons.collections15.CollectionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 30, 2011
 * Time: 2:58:38 PM
 */
public class ParticipantGroupController extends BaseStudyController
{
    enum GroupType {
        participantGroup,
        cohort,
        participantCategory,
    }

    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(ParticipantGroupController.class);

    public ParticipantGroupController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CreateParticipantCategory extends MutatingApiAction<ParticipantCategorySpecification>
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            form.setContainer(getContainer().getId());

            ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form, form.getParticipantIds(), form.getFilters(), form.getDescription());

            resp.put("success", true);
            resp.put("category", category.toJSON());

            return resp;
        }
    }

    /**
     * Bean used to create and update participant categories
     */
    public static class ParticipantCategorySpecification extends ParticipantCategoryImpl
    {
        private String[] _participantIds = new String[0];
        private String _filters;
        private String _description;

        public String[] getParticipantIds()
        {
            return _participantIds;
        }

        public void setParticipantIds(String[] participantIds)
        {
            _participantIds = participantIds;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getFilters()
        {
            return _filters;
        }

        public void setFilters(String filters)
        {
            _filters = filters;
        }

        public void fromJSON(JSONObject json)
        {
            super.fromJSON(json);

            if (json.has("participantIds"))
            {
                JSONArray ptids = json.getJSONArray("participantIds");
                String[] ids = new String[ptids.length()];

                for (int i=0; i < ptids.length(); i++)
                {
                    ids[i] = ptids.getString(i);
                }
                setParticipantIds(ids);
            }

            if (json.has("participantFilters"))
            {
                JSONArray filters = json.getJSONArray("participantFilters");
                setFilters(filters.toString());
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class UpdateParticipantCategory extends MutatingApiAction<ParticipantCategorySpecification>
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.isNew())
            {
                throw new IllegalArgumentException("The specified category does not exist, you must pass in the RowId");
            }

            SimpleFilter filter = new SimpleFilter("RowId", form.getRowId());
            ParticipantCategoryImpl[] defs = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), filter);
            if (defs.length == 1)
            {
                form.copySpecialFields(defs[0]);
                ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form, form.getParticipantIds(), form.getFilters(), form.getDescription());

                resp.put("success", true);
                resp.put("category", category.toJSON());

                return resp;
            }
            else
                throw new RuntimeException("Unable to update the category with rowId: " + form.getRowId());
        }
    }

    enum Modification {ADD, REMOVE};

    private abstract class ModifyCategoryParticipants extends MutatingApiAction<ParticipantCategorySpecification>
    {
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors, Modification modification) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.isNew())
            {
                throw new IllegalArgumentException("The specified category does not exist, you must pass in the RowId");
            }

            SimpleFilter filter = new SimpleFilter("RowId", form.getRowId());
            ParticipantCategoryImpl[] defs = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), filter);
            if (defs.length == 1)
            {
                ParticipantCategoryImpl def = defs[0];
                form.copySpecialFields(def);

                ParticipantCategoryImpl category;

                if (modification == Modification.ADD)
                    category = ParticipantGroupManager.getInstance().addCategoryParticipants(getContainer(), getUser(), def, form.getParticipantIds());
                else
                    category = ParticipantGroupManager.getInstance().removeCategoryParticipants(getContainer(), getUser(), def, form.getParticipantIds());

                resp.put("success", true);
                resp.put("category", category.toJSON());

                return resp;
            }
            else
                throw new RuntimeException("Unable to update the category with rowId: " + form.getRowId());
        }

    }

    @RequiresPermissionClass(ReadPermission.class)
    public class AddParticipantsToCategory extends ModifyCategoryParticipants
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            return super.execute(form, errors, Modification.ADD);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RemoveParticipantsFromCategory extends ModifyCategoryParticipants
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            return super.execute(form, errors, Modification.REMOVE);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class GetParticipantCategory extends ApiAction<ParticipantCategoryImpl>
    {
        @Override
        public ApiResponse execute(ParticipantCategoryImpl form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), form.getLabel());

            resp.put("success", true);
            resp.put("category", category.toJSON());

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetParticipantCategories extends ApiAction<GetParticipantCategoriesForm>
    {
        @Override
        public ApiResponse execute(GetParticipantCategoriesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            ParticipantCategoryImpl[] categories;
            if (form.getCategoryType() != null && form.getCategoryType().equals("manual"))
            {
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition("type", form.getCategoryType());
                categories = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), filter);
            }
            else
            {
                categories = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser());
            }

            JSONArray defs = new JSONArray();

            for (ParticipantCategoryImpl pc : categories)
            {
                defs.put(pc.toJSON());
            }
            resp.put("success", true);
            resp.put("categories", defs);

            return resp;
        }
    }

    public static class GetParticipantCategoriesForm
    {
        private String _categoryType;

        public String getCategoryType()
        {
            return _categoryType;
        }

        public void setCategoryType(String categoryType)
        {
            _categoryType = categoryType;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DeleteParticipantCategory extends MutatingApiAction<ParticipantCategoryImpl>
    {
        @Override
        public ApiResponse execute(ParticipantCategoryImpl form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantCategoryImpl category = form;

            if (form.isNew())
            {
                // try to match a single category by label/container
                SimpleFilter filter = new SimpleFilter("Container", getContainer());
                filter.addCondition("Label", form.getLabel());

                ParticipantCategoryImpl[] defs = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), filter);
                if (defs.length == 1)
                    category = defs[0];
            }
            else
            {
                SimpleFilter filter = new SimpleFilter("RowId", form.getRowId());
                ParticipantCategoryImpl[] defs = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), filter);
                if (defs.length == 1)
                    category = defs[0];
            }

            ParticipantGroupManager.getInstance().deleteParticipantCategory(getContainer(), getUser(), category);
            resp.put("success", true);

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetParticipantsFromSelectionAction extends MutatingApiAction<ParticipantSelection>
    {
        @Override
        public ApiResponse execute(ParticipantSelection form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            try
            {
                Set<String> ptids = new LinkedHashSet<String>();

                QuerySettings settings = form.getQuerySettings();
                settings.setMaxRows(Table.ALL_ROWS);

                UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
                if (schema == null)
                {
                    errors.reject(ERROR_MSG, "schema not found");
                    return null;
                }

                QueryView view = schema.createView(getViewContext(), settings, errors);
                if (view == null)
                {
                    errors.reject(ERROR_MSG, "view not found");
                    return null;
                }

                if (form.isSelectAll())
                {
                    for (String ptid : StudyController.generateParticipantList(view))
                        ptids.add(ptid);
                }
                else
                {
                    List<String> participants = ParticipantGroupManager.getInstance().getParticipantsFromSelection(getContainer(), view, Arrays.asList(form.getSelections()));
                    ptids.addAll(participants);
                }
                resp.put("ptids", ptids);
                resp.put("success", true);
            }
            catch (SQLException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
            return resp;
        }
    }

    public static class ParticipantSelection extends QueryForm
    {
        private String[] _selections;
        private boolean _selectAll;
        private String _requestURL;

        public String getRequestURL()
        {
            return _requestURL;
        }

        public void setRequestURL(String requestURL)
        {
            _requestURL = requestURL;
        }

        public String[] getSelections()
        {
            return _selections;
        }

        public void setSelections(String[] selections)
        {
            _selections = selections;
        }

        public boolean isSelectAll()
        {
            return _selectAll;
        }

        public void setSelectAll(boolean selectAll)
        {
            _selectAll = selectAll;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseParticipantGroups extends ApiAction<BrowseGroupsForm>
    {
        private StudyImpl _study;
        private String[] _allParticipants;

        @Override
        public void validateForm(BrowseGroupsForm browseGroupsForm, Errors errors)
        {
            _study = StudyManager.getInstance().getStudy(getContainer());

            if (_study == null)
                errors.reject(ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(BrowseGroupsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            List<JSONObject> groups = new ArrayList<JSONObject>();

            for (String type : form.getType())
            {
                GroupType groupType = GroupType.valueOf(type);
                Set<String> selectedParticipants = new HashSet<String>();
                switch(groupType)
                {
                    case participantGroup:
                        // the api will support either requesting a specific participant category or all of
                        // the categories (and groups)
                        if (form.getCategoryId() != -1)
                        {
                            ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), form.getCategoryId());
                            addCategory(form, category, groups);
                        }
                        else
                        {
                            for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser()))
                                addCategory(form, category, groups);
                        }
                        break;
                    case participantCategory:
                        for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser()))
                        {
                            // omit orphaned categories
                            if (category.getGroups().length > 0)
                                groups.add(category.toJSON());
                        }
                        break;
                    case cohort:
                        for (CohortImpl cohort : StudyManager.getInstance().getCohorts(getContainer(), getUser()))
                        {
                            selectedParticipants.addAll(cohort.getParticipantSet());
                            JSONGroup jsonGroup = new JSONGroup(cohort);
                            if (form.includeParticipantIds())
                                jsonGroup.setParticipantIds(cohort.getParticipantSet());

                            groups.add(jsonGroup.toJSON(getViewContext()));
                        }

                        if (form.isIncludeUnassigned() && hasUnassignedParticipants(selectedParticipants))
                            groups.add(new JSONGroup(-1, -1, "Not in any cohort", GroupType.cohort).toJSON(getViewContext()));
                        break;
                }
            }
            resp.put("success", true);
            resp.put("groups", groups);

            return resp;
        }

        private void addCategory(BrowseGroupsForm form, ParticipantCategoryImpl category, List<JSONObject> groups)
        {
            if (form.isIncludePrivateGroups() || category.isShared())
            {
                Set<String> selectedParticipants = new HashSet<String>();

                for (ParticipantGroup group : category.getGroups())
                {
                    selectedParticipants.addAll(group.getParticipantSet());
                    JSONGroup jsonGroup = new JSONGroup(group, category);
                    if (form.includeParticipantIds())
                        jsonGroup.setParticipantIds(group.getParticipantSet());

                    groups.add(jsonGroup.toJSON(getViewContext()));
                }
                String[] unassigned = StudyManager.getInstance().getParticipantIdsNotInGroupCategory(_study, getUser(), category.getRowId());
                if (form.isIncludeUnassigned() && (unassigned.length > 0))
                    groups.add(new JSONGroup(-1, category.getRowId(), "Not in any group", GroupType.participantGroup).toJSON(getViewContext()));
            }
        }

        /**
         * Determines if the specified set of participants represents all available participants for this folder
         * @param selectedParticipants
         * @return
         */
        private boolean hasUnassignedParticipants(Set<String> selectedParticipants)
        {
            if (_allParticipants == null)
                _allParticipants = StudyManager.getInstance().getParticipantIds(_study);

            Set<String> participants = new HashSet<String>();
            participants.addAll(Arrays.asList(_allParticipants));

            return !CollectionUtils.isEqualCollection(selectedParticipants, Arrays.asList(_allParticipants));
        }
    }

    static class JSONGroup
    {
        private int _groupId;
        private String _label;
        private boolean _enrolled = true;
        private GroupType _type;
        private int _categoryId;
        private String _filters;
        private String _description;
        private Set<String> _participantIds = new HashSet<String>();
        private ParticipantCategoryImpl _category;
        private Integer _createdBy;
        private Integer _modifiedBy;

        public JSONGroup(ParticipantGroup group, ParticipantCategoryImpl category)
        {
            _groupId = group.getRowId();
            _categoryId = group.getCategoryId();
            _label = group.getLabel();
            _filters = group.getFilters();
            _description = group.getDescription();
            _createdBy = group.getCreatedBy();
            _modifiedBy = group.getModifiedBy();
            _category = category;
            _type = GroupType.participantGroup;
        }

        public JSONGroup(CohortImpl cohort)
        {
            _groupId = cohort.getRowId();
            _categoryId = cohort.getRowId();
            _label = cohort.getLabel();
            _enrolled = cohort.isEnrolled();
            _type = GroupType.cohort;
        }

        public JSONGroup(int groupId, int categoryId, String label, GroupType type)
        {
            _groupId = groupId;
            _categoryId = categoryId;
            _label = label;
            _type = type;
        }

        public void setParticipantIds(Set<String> participantIds)
        {
            _participantIds = participantIds;
        }

        public JSONObject toJSON(ViewContext context)
        {
            JSONObject json = new JSONObject();

            json.put("id", _groupId);
            json.put("label", _label);
            json.put("enrolled", _enrolled);
            json.put("type", _type);
            json.put("categoryId", _categoryId);
            if (_filters != null)
                json.put("filters", _filters);
            if (_description != null)
                json.put("description", _description);
            json.put("participantIds", _participantIds);
            if (_category != null)
                json.put("category", _category.toJSON());
            if (_createdBy != null)
                json.put("createdBy", getUserJSON(context, _createdBy));
            if (_modifiedBy != null)
                json.put("modifiedBy", getUserJSON(context, _modifiedBy));

            return json;
        }

        private JSONObject getUserJSON(ViewContext context, int id)
        {
            JSONObject json = new JSONObject();
            User currentUser = context.getUser();
            User user = UserManager.getUser(id);
            json.put("value", id);
            json.put("displayValue", user != null ? user.getDisplayName(currentUser) : null);

            return json;
        }
    }

    public static class BrowseGroupsForm
    {
        private String[] _type;
        private boolean _includeParticipantIds = false;
        private boolean _includePrivateGroups = true;
        private boolean _includeUnassigned = true;
        private int _categoryId = -1;

        public boolean isIncludePrivateGroups()
        {
            return _includePrivateGroups;
        }

        public void setIncludePrivateGroups(boolean includePrivateGroups)
        {
            _includePrivateGroups = includePrivateGroups;
        }

        public String[] getType()
        {
            return _type;
        }

        public void setType(String[] type)
        {
            _type = type;
        }

        public boolean includeParticipantIds()
        {
            return _includeParticipantIds;
        }

        public void setIncludeParticipantIds(boolean includeParticipantIds)
        {
            _includeParticipantIds = includeParticipantIds;
        }

        public int getCategoryId()
        {
            return _categoryId;
        }

        public void setCategoryId(int categoryId)
        {
            _categoryId = categoryId;
        }

        public boolean isIncludeUnassigned()
        {
            return _includeUnassigned;
        }

        public void setIncludeUnassigned(boolean includeUnassigned)
        {
            _includeUnassigned = includeUnassigned;
        }
    }

    public static class GroupsForm implements CustomApiForm
    {
        private List<Group> _groups = new ArrayList<Group>();

        public List<Group> getGroups()
        {
            return _groups;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            Object groups = props.get("groups");

            if (groups instanceof JSONArray)
            {
                JSONArray groupArr = (JSONArray)groups;

                for (int i=0; i < groupArr.length(); i++)
                {
                    JSONObject group = groupArr.getJSONObject(i);

                    GroupType type = GroupType.valueOf(group.getString("type"));

                    int categoryId = group.getInt("categoryId");
                    int id = group.getInt("id");

                    _groups.add(new Group(type, id, categoryId));
                }
            }
        }

        static class Group
        {
            GroupType type;
            int id;
            int categoryId;

            public Group(GroupType type, int id, int categoryId)
            {
                this.type = type;
                this.id = id;
                this.categoryId = categoryId;
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetSubjectsFromGroups extends ApiAction<GroupsForm>
    {
        private StudyImpl _study;

        @Override
        public void validateForm(GroupsForm groupsForm, Errors errors)
        {
            _study = StudyManager.getInstance().getStudy(getContainer());

            if (_study == null)
                errors.reject(ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(GroupsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            Map<String, Set<String>> categoryToSubjectMap = new HashMap<String, Set<String>>();

            for (GroupsForm.Group group : form.getGroups())
            {
                String key = group.type.name();
                if (group.type != GroupType.cohort)
                    key = group.type.name() + "|" + group.categoryId;

                if (!categoryToSubjectMap.containsKey(key))
                    categoryToSubjectMap.put(key, new HashSet<String>());

                Set<String> participants = categoryToSubjectMap.get(key);

                switch (group.type)
                {
                    case participantGroup:
                        if (group.id == -1)
                            participants.addAll(Arrays.asList(StudyManager.getInstance().getParticipantIdsNotInGroupCategory(_study, getUser(), group.categoryId)));
                        else
                        {
                            ParticipantGroup pg = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), group.id);
                            if (pg != null)
                                participants.addAll(pg.getParticipantSet());
                        }
                        break;

                    case cohort:
                        if (group.id == -1)
                            participants.addAll(Arrays.asList(StudyManager.getInstance().getParticipantIdsNotInCohorts(_study, getUser())));
                        else
                            participants.addAll(Arrays.asList(StudyManager.getInstance().getParticipantIdsForCohort(_study, group.id, -1)));
                        break;
                }
            }

            // we want to OR subjects within a category and AND them across categories
            Collection<String> subjects = new ArrayList<String>();
            for (Set<String> participants : categoryToSubjectMap.values())
            {
                if (!subjects.isEmpty())
                {
                    subjects = CollectionUtils.intersection(subjects, participants);
                    if (subjects.isEmpty())
                        break;
                }
                else
                    subjects.addAll(participants);
            }

            List<String> sortedSubjects = new ArrayList<String>();
            sortedSubjects.addAll(subjects);

            Collections.sort(sortedSubjects);

            resp.put("success", true);
            resp.put("subjects", sortedSubjects);

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SaveParticipantGroup extends MutatingApiAction<ParticipantGroupSpecification>
    {
        ParticipantGroup _prevGroup;

        @Override
        public void validateForm(ParticipantGroupSpecification form, Errors errors)
        {
            form.setContainerId(getContainer().getId());
            if(!form.getParticipantCategorySpecification().isNew())
            {
                ParticipantGroup[] participantGroups  = ParticipantGroupManager.getInstance().getParticipantGroups(getContainer(), getUser(), form.getParticipantCategorySpecification());
                Set<String> formParticipants = new HashSet<String>(Arrays.asList(form.getParticipantIds()));
                
                for(ParticipantGroup group : participantGroups)
                {
                    if (group.getRowId() != form.getRowId())
                    {
                        String[] participants = group.getParticipantIds();
                        for(String ptid : participants)
                        {
                            if (formParticipants.contains(ptid))
                            {
                               errors.reject(ERROR_MSG, "The group " + group.getLabel() + " already contains " + ptid + ". Participants can only be in one group within a category.");
                            }
                        }
                    }
                }
            }

            if (form.getRowId() != 0)
            {
                // updating an existing group
                //                
                _prevGroup = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), form.getRowId());
                if (_prevGroup == null)
                    errors.reject(ERROR_MSG, "The group " + form.getLabel() + " no longer exists in the sytem, update failed.");
            }
        }

        @Override
        public ApiResponse execute(ParticipantGroupSpecification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            form.setContainer(getContainer().getId());

            ParticipantCategoryImpl category;
            ParticipantCategoryImpl oldCategory;
            ParticipantGroup group;

            if (!form.isNew())
                form.copySpecialFields(_prevGroup);

            if (form.getCategoryId() == 0)
            {
                if (form.getCategoryType().equals("list"))
                {
                    // No category selected, create new category with type 'list'.
                    if (form.isNew())
                    {
                        category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form.getParticipantCategorySpecification(), form.getParticipantIds(), form.getFilters(), form.getDescription());
                        group = category.getGroups()[0];
                    }
                    else
                    {
                        DbScope scope = StudySchema.getInstance().getSchema().getScope();

                        scope.ensureTransaction();

                        category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form.getParticipantCategorySpecification());
                        form.setCategoryId(category.getRowId());
                        group = ParticipantGroupManager.getInstance().setParticipantGroup(getContainer(), getUser(), form);

                        scope.commitTransaction();
                    }
                }
                else
                {
                    // New category specified. Create category with type 'manual' and create new participant group.
                    DbScope scope = StudySchema.getInstance().getSchema().getScope();

                    scope.ensureTransaction();
                    Integer oldCategoryId = null;
                    if (!form.isNew())
                    {
                        oldCategoryId = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), form.getRowId()).getCategoryId();
                    }

                    category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form.getParticipantCategorySpecification());
                    form.setCategoryId(category.getRowId());
                    group = ParticipantGroupManager.getInstance().setParticipantGroup(getContainer(), getUser(), form);

                    if (oldCategoryId != null)
                    {
                        oldCategory = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), oldCategoryId);
                        if (oldCategory != null && oldCategory.getType().equals("list") && !category.getType().equals("list"))
                        {
                            ParticipantGroupManager.getInstance().deleteParticipantCategory(getContainer(), getUser(), oldCategory);
                        }
                    }

                    scope.commitTransaction();
                }
            }
            else
            {
                DbScope scope = StudySchema.getInstance().getSchema().getScope();

                scope.ensureTransaction();
                Integer oldCategoryId = null;
                if (!form.isNew())
                {
                    oldCategoryId = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), form.getRowId()).getCategoryId();
                }

                group = ParticipantGroupManager.getInstance().setParticipantGroup(getContainer(), getUser(), form);
                category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), group.getCategoryId());

                if(form.getCategoryShared() != category.isShared()){
                    category.setShared(form.getCategoryShared());
                    ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), category);
                }

                if (oldCategoryId != null)
                {
                    oldCategory = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), oldCategoryId);
                    if (oldCategory.getType().equals("list") && !category.getType().equals("list"))
                    {
                        ParticipantGroupManager.getInstance().deleteParticipantCategory(getContainer(), getUser(), oldCategory);
                    }
                }
                
                scope.commitTransaction();
            }
            
            resp.put("success", true);
            resp.put("group", group.toJSON());
            resp.put("category", category.toJSON());

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DeleteParticipantGroup extends MutatingApiAction<ParticipantGroup>
    {
        @Override
        public ApiResponse execute(ParticipantGroup form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            ParticipantGroup group = ParticipantGroupManager.getInstance().getParticipantGroupFromGroupRowId(getContainer(), getUser(), form.getRowId());
            ParticipantGroupManager.getInstance().deleteParticipantGroup(getContainer(), getUser(), group);
            return resp;
        }
    }

    public static class ParticipantGroupSpecification extends ParticipantGroup
    {
        private String[] _participantIds = new String[0];
        private String _filters;
        private String _description;
        private int _categoryId;
        private String _categoryLabel;
        private String _categoryType;
        private Boolean _categoryShared;

        public Boolean getCategoryShared()
        {
            return _categoryShared;
        }

        public void setCategoryShared(Boolean shared)
        {
            _categoryShared = shared;
        }

        public String getCategoryType()
        {
            return _categoryType;
        }

        public void setCategoryType(String categoryType)
        {
            _categoryType = categoryType;
        }

        public String getCategoryLabel()
        {
            return _categoryLabel;
        }

        public void setCategoryLabel(String categoryLabel)
        {
            _categoryLabel = categoryLabel;
        }

        public String[] getParticipantIds()
        {
            return _participantIds;
        }

        public void setParticipantIds(String[] participantIds)
        {
            _participantIds = participantIds;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getFilters()
        {
            return _filters;
        }

        public void setFilters(String filters)
        {
            _filters = filters;
        }

        public void setCategoryId(int id)
        {
            _categoryId = id;
        }

        public int getCategoryId(){
            return _categoryId;
        }

        public ParticipantCategorySpecification getParticipantCategorySpecification()
        {
            ParticipantCategorySpecification category = new ParticipantCategorySpecification();

            category.setRowId(getCategoryId());
            category.setParticipantIds(getParticipantIds());
            category.setFilters(getFilters());
            if (getCategoryLabel() == null)
            {
                category.setLabel(getLabel());
            }
            else
            {
                category.setLabel(getCategoryLabel());
            }
            category.setType(getCategoryType());
            category.setShared(getCategoryShared());
            category.setDescription(getDescription());
            category.setContainerId(getContainerId());

            return category;
        }
    }
}
