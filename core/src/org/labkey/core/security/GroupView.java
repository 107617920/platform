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

package org.labkey.core.security;

import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.view.JspView;
import org.labkey.api.util.Pair;
import org.springframework.validation.BindException;

import java.util.List;

/**
 * User: jeckels
* Date: May 1, 2008
*/
public class GroupView extends JspView<GroupView.GroupBean>
{
    public GroupView(Group group, List<UserPrincipal> members, List<String> messages, boolean systemGroup, BindException errors)
    {
        super("/org/labkey/core/security/group.jsp", new GroupBean(), errors);

        GroupBean bean = getModelBean();

        bean.group = group;
        bean.groupName = group.getPath();
        bean.members = members;
        bean.messages = messages;
        bean.isSystemGroup = systemGroup;
        bean.ldapDomain = AuthenticationManager.getLdapDomain();
    }

    public static class GroupBean
    {
        public Group group;
        public String groupName;
        public List<UserPrincipal> members;
        public List<String> messages;
        public boolean isSystemGroup;
        public String ldapDomain;
    }
}
