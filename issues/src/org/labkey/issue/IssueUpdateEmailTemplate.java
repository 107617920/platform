/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.issue;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.model.Issue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: Aug 3, 2010
 */
public class IssueUpdateEmailTemplate extends EmailTemplate
{
    protected static final String DEFAULT_SUBJECT =
            "Issue #^issueId^, \"^title^,\" has been ^action^";
    protected static final String DEFAULT_BODY =
            "You can review this issue here: ^detailsURL^\n" +
            "Modified by: ^user^\n" +
            "^modifiedFields^\n" +
            "^comment^";
    private List<ReplacementParam> _replacements = new ArrayList<ReplacementParam>();
    private Issue _newIssue;
    private ActionURL _detailsURL;
    private String _change;
    private String _comment;
    private String _fieldChanges;
    private String _recipients;

    public IssueUpdateEmailTemplate()
    {
        super("Issue update");
        setSubject(DEFAULT_SUBJECT);
        setBody(DEFAULT_BODY);
        setDescription("Sent to the users based on issue notification rules and settings after an issue has been edited or inserted.");
        setPriority(10);
        setEditableScopes(Scope.SiteOrFolder);

        _replacements.add(new ReplacementParam("issueId", "Unique id for the issue")
        {
            public String getValue(Container c) {return _newIssue == null ? null : Integer.toString(_newIssue.getIssueId());}
        });
        _replacements.add(new ReplacementParam("detailsURL", "URL to get the details view for the issue")
        {
            public String getValue(Container c) {return _detailsURL == null ? null : _detailsURL.getURIString();}
        });
        _replacements.add(new ReplacementParam("action", "Description of the type of action, like 'opened' or 'resolved'")
        {
            public String getValue(Container c) {return _change;}
        });
        _replacements.add(new UserIdReplacementParam("user", "The display name of the user performing the operation")
        {
            public Integer getUserId(Container c)
            {
                return _newIssue.getModifiedBy();
            }
        });
        _replacements.add(new ReplacementParam("comment", "The comment that was just added")
        {
            public String getValue(Container c)
            {
                return _comment;
            }
        });
        _replacements.add(new ReplacementParam("recipients", "All of the recipients of the email notification")
        {
            public String getValue(Container c)
            {
                return _recipients == null ? "user@domain.com" : _recipients;
            }
        });
        _replacements.add(new HStringReplacementParam("title", "The current title of the issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getTitle();
            }
        });
        _replacements.add(new HStringReplacementParam("status", "The current status of the issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getStatus();
            }
        });
        _replacements.add(new HStringReplacementParam("type", "The current type of the issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getType();
            }
        });
        _replacements.add(new HStringReplacementParam("area", "The current area of the issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getArea();
            }
        });
        _replacements.add(new ReplacementParam("priority", "The current priority of the issue")
        {
            @Override
            public String getValue(Container c)
            {
                if (_newIssue == null || _newIssue.getPriority() == null)
                {
                    return null;
                }
                return _newIssue.getPriority().toString(); 
            }
        });
        _replacements.add(new HStringReplacementParam("milestone", "The current milestone of the issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getMilestone();
            }
        });
        _replacements.add(new UserIdReplacementParam("openedBy", "The user that opened the issue")
        {
            public Integer getUserId(Container c)
            {
                return _newIssue.getCreatedBy();
            }
        });
        _replacements.add(new ReplacementParam("opened", "The date that the issue was opened")
        {
            public String getValue(Container c)
            {
                return _newIssue == null ? null : DateUtil.formatDate(_newIssue.getCreated());
            }
        });
        _replacements.add(new ReplacementParam("resolved", "The date that the issue was last resolved")
        {
            public String getValue(Container c)
            {
                return _newIssue == null || _newIssue.getResolved() == null ? null : DateUtil.formatDate(_newIssue.getResolved());
            }
        });
        _replacements.add(new UserIdReplacementParam("resolvedBy", "The user who last resolved this issue")
        {
            public Integer getUserId(Container c)
            {
                return _newIssue.getResolvedBy();
            }
        });
        _replacements.add(new HStringReplacementParam("resolution", "The resolution type that was last used for this issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getResolution();
            }
        });
        _replacements.add(new ReplacementParam("closed", "The date that the issue was last closed")
        {
            public String getValue(Container c)
            {
                return _newIssue == null || _newIssue.getClosed() == null ? null : DateUtil.formatDate(_newIssue.getClosed());
            }
        });
        _replacements.add(new UserIdReplacementParam("closedBy", "The user who last closed this issue")
        {
            public Integer getUserId(Container c)
            {
                return _newIssue.getClosedBy();
            }
        });
        _replacements.add(new HStringReplacementParam("notifyList", "The current notification list for this issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getNotifyList();
            }
        });
        _replacements.add(new ReplacementParam("int1", "The first admin-configurable integer field this issue")
        {
            public String getValue(Container c)
            {
                return _newIssue == null || _newIssue.getInt1() == null ? null : _newIssue.getInt1().toString();
            }
        });
        _replacements.add(new ReplacementParam("int2", "The second admin-configurable integer field this issue")
        {
            public String getValue(Container c)
            {
                return _newIssue == null || _newIssue.getInt2() == null ? null : _newIssue.getInt2().toString();
            }
        });
        _replacements.add(new HStringReplacementParam("string1", "The first admin-configurable string field this issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getString1();
            }
        });
        _replacements.add(new HStringReplacementParam("string2", "The second admin-configurable string field this issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getString2();
            }
        });
        _replacements.add(new HStringReplacementParam("string3", "The third admin-configurable string field this issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getString3();
            }
        });
        _replacements.add(new HStringReplacementParam("string4", "The fourth admin-configurable string field this issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getString4();
            }
        });
        _replacements.add(new HStringReplacementParam("string5", "The fifth admin-configurable string field this issue")
        {
            public HString getHStringValue(Container c)
            {
                return _newIssue.getString5();
            }
        });
        _replacements.add(new ReplacementParam("modifiedFields", "Summary of all changed fields with before and after values")
        {
            public String getValue(Container c)
            {
                return _fieldChanges;
            }
        });

        // modifiedFields

        _replacements.addAll(super.getValidReplacements());
    }

    private abstract class HStringReplacementParam extends ReplacementParam
    {
        public HStringReplacementParam(String name, String description)
        {
            super(name, description);
        }

        @Override
        public final String getValue(Container c)
        {
            if (_newIssue == null)
            {
                return null;
            }
            HString string = getHStringValue(c);
            return string == null ? null : string.getSource();
        }

        protected abstract HString getHStringValue(Container c);
    }

    private abstract class UserIdReplacementParam extends ReplacementParam
    {
        public UserIdReplacementParam(String name, String description)
        {
            super(name, description);
        }

        @Override
        public final String getValue(Container c)
        {
            if (_newIssue == null)
            {
                return null;
            }
            Integer userId = getUserId(c);
            if (userId == null)
            {
                return null;
            }
            User user = UserManager.getUser(userId.intValue());
            return user == null ? "(unknown)" : user.getFriendlyName();
        }

        protected abstract Integer getUserId(Container c);
    }

    public void init(Issue newIssue, ActionURL detailsURL, String change, String comment, String fieldChanges, Set<String> recipients)
    {
        _newIssue = newIssue;
        _detailsURL = detailsURL;
        _change = change;
        _comment = comment;
        _fieldChanges = fieldChanges;

        StringBuilder sb = new StringBuilder();
        String separator = "";
        for (String address : recipients)
        {
            sb.append(separator);
            separator = ", ";
            sb.append(address);
        }
        _recipients = sb.toString();
    }

    public List<ReplacementParam> getValidReplacements(){return _replacements;}
}
