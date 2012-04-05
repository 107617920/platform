/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.announcements.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 19, 2011
 * Time: 5:08:44 PM
 */
public class MessageConfigManager
{
    private static final CommSchema _comm = CommSchema.getInstance();
    private static final int EMAIL_PREFERENCE_DEFAULT = -1;
    private static final int EMAIL_FORMAT_HTML = 1;
    private static final int PAGE_TYPE_MESSAGE = 0;

    public static EmailPref getUserEmailPrefRecord(Container c, User user, String type, String srcIdentifier)
    {
        EmailPref[] prefs = _getUserEmailPrefRecord(c, user, type, srcIdentifier);

        if (prefs != null)
            return prefs[0];
        else
            return null;
    }

    private static EmailPref[] _getUserEmailPrefRecord(Container c, User user, String type, String srcIdentifier)
    {
        if (srcIdentifier == null)
        {
            throw new IllegalArgumentException("srcIdentifier must not be null");
        }

        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("Container", c.getId());
            filter.addCondition("UserId", user.getUserId());
            filter.addCondition("SrcIdentifier", srcIdentifier);

            if (type != null)
                filter.addCondition("Type", type);

            //return records only for those users who have explicitly set a preference for this container.
            EmailPref[] emailPrefs = Table.select(
                    _comm.getTableInfoEmailPrefs(),
                    Table.ALL_COLUMNS,
                    filter,
                    null,
                    EmailPref.class
                    );

            if (emailPrefs.length == 0)
                return null;
            else
                return emailPrefs;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    // Returns email preferences for all active users with read permissions to this container.  A user could have multiple
    // preferences (one for each srcIdentifier).  If a user hasn't expressed any preferences they'll still get one preference
    // representing the container default.
    public static EmailPref[] getUserEmailPrefs(Container c, String type)
    {
        SQLFragment sql = new SQLFragment();
        List<EmailPref> prefs = new ArrayList<EmailPref>();

        sql.append("SELECT u.userId, email, firstName, lastName, displayName, emailOptionId, emailFormatId, type, lastModifiedBy, container, COALESCE(srcIdentifier, ?) AS srcIdentifier FROM ");
        sql.append(CoreSchema.getInstance().getTableInfoUsers(), "u").append(" LEFT JOIN ");
        sql.append(_comm.getTableInfoEmailPrefs(), "prefs").append(" ON u.userId = prefs.userId ");
        sql.append("AND type = ? AND container = ?");
        sql.add(c);
        sql.add(type);
        sql.add(c);

        for (EmailPref ep : new SqlSelector(_comm.getSchema(), sql).getCollection(EmailPref.class))
        {
            User user = ep.getUser();
            if (c.hasPermission(user, ReadPermission.class) && user.isActive())
                prefs.add(ep);
        }

        return prefs.toArray(new EmailPref[prefs.size()]);
    }

    public static void saveEmailPreference(User currentUser, Container c, User projectUser, String type, int emailPreference, String srcIdentifier)
    {
        //determine whether user has record in EmailPrefs table.
        EmailPref emailPref = getUserEmailPrefRecord(c, projectUser, type, srcIdentifier);

        // Pull the container level user preference too if it's different
        EmailPref containerEmailPref = srcIdentifier.equals(c.getId()) ? null : getUserEmailPrefRecord(c, projectUser, type, c.getId());

        try
        {
            //insert new if user preference record does not yet exist, and if it's a duplicate of an existing container level preference
            if (null == emailPref && emailPreference != EMAIL_PREFERENCE_DEFAULT && !matches(containerEmailPref, emailPreference))
            {
                emailPref = new EmailPref();
                emailPref.setContainer(c.getId());
                emailPref.setUserId(projectUser.getUserId());
                emailPref.setEmailFormatId(EMAIL_FORMAT_HTML);
                emailPref.setEmailOptionId(emailPreference);
                emailPref.setPageTypeId(PAGE_TYPE_MESSAGE);
                emailPref.setLastModifiedBy(currentUser.getUserId());
                emailPref.setType(type);
                emailPref.setSrcIdentifier(srcIdentifier);
                Table.insert(currentUser, _comm.getTableInfoEmailPrefs(), emailPref);
            }
            else
            {
                if (emailPreference == EMAIL_PREFERENCE_DEFAULT || matches(containerEmailPref, emailPreference))
                {
                    //if preference has been set back to default (either the default for the container, or the user's container
                    // level preference, delete user's email pref record
                    SimpleFilter filter = new SimpleFilter();
                    filter.addCondition("UserId", projectUser.getUserId());
                    filter.addCondition("Container", c.getId());
                    filter.addCondition("Type", type);
                    filter.addCondition("SrcIdentifier", srcIdentifier);
                    Table.delete(_comm.getTableInfoEmailPrefs(), filter);
                }
                else if (!matches(containerEmailPref, emailPreference))
                {
                    //otherwise update if it already exists
                    emailPref.setEmailOptionId(emailPreference);
                    emailPref.setLastModifiedBy(currentUser.getUserId());
                    Table.update(currentUser, _comm.getTableInfoEmailPrefs(), emailPref,
                            new Object[]{c.getId(), projectUser.getUserId(), type, srcIdentifier});
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /** Check if the subscription option is the same as an existing row */
    private static boolean matches(EmailPref emailPref, int emailPreference)
    {
        if (emailPref == null)
        {
            // No preference to compare against, so no match
            return false;
        }
        // Simple check to see if the email option is the same
        return emailPref.getEmailOptionId() != null && emailPreference == emailPref.getEmailOptionId().intValue();
    }

    //delete all user records regardless of container
    public static void deleteUserEmailPref(User user, List<Container> containerList)
    {
        try
        {
            if (containerList == null)
            {
                Table.delete(_comm.getTableInfoEmailPrefs(),
                        new SimpleFilter("UserId", user.getUserId()));
            }
            else
            {
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition("UserId", user.getUserId());
                StringBuilder whereClause = new StringBuilder("Container IN (");
                for (int i = 0; i < containerList.size(); i++)
                {
                    Container c = containerList.get(i);
                    whereClause.append("'");
                    whereClause.append(c.getId());
                    whereClause.append("'");
                    if (i < containerList.size() - 1)
                        whereClause.append(", ");
                }
                whereClause.append(")");
                filter.addWhereClause(whereClause.toString(), null);

                Table.delete(_comm.getTableInfoEmailPrefs(), filter);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static EmailOption[] getEmailOptions(@NotNull String type)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("Type", type);
            return Table.select(_comm.getTableInfoEmailOptions(), Table.ALL_COLUMNS, filter, new Sort("EmailOptionId"), EmailOption.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static EmailOption getEmailOption(int optionId)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("EmailOptionId", optionId);
            EmailOption[] options = Table.select(_comm.getTableInfoEmailOptions(), Table.ALL_COLUMNS, filter, null, EmailOption.class);

            assert (options.length <= 1);
            if (options.length == 0)
                return null;
            else
                return options[0];
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static class EmailPref implements MessageConfigService.UserPreference
    {
        String _container;
        int _userId;
        String _email;
        String _firstName;
        String _lastName;
        String _displayName;
        Integer _emailOptionId;
        Integer _emailFormatId;
        String _emailOption;
        Integer _lastModifiedBy;
        String _lastModifiedByName;
        String _srcIdentifier;
        String _type;

        boolean _projectMember;

        int _pageTypeId;

        public String getLastModifiedByName()
        {
            return _lastModifiedByName;
        }

        public void setLastModifiedByName(String lastModifiedByName)
        {
            _lastModifiedByName = lastModifiedByName;
        }

        public Integer getLastModifiedBy()
        {
            return _lastModifiedBy;
        }

        public void setLastModifiedBy(Integer lastModifiedBy)
        {
            _lastModifiedBy = lastModifiedBy;
        }

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings({"UnusedDeclaration"}) public void setEmail(String email)
        {
            _email = email;
        }

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
        }

        public String getEmailOption()
        {
            return _emailOption;
        }

        public void setEmailOption(String emailOption)
        {
            _emailOption = emailOption;
        }

        public String getFirstName()
        {
            return _firstName;
        }

        @SuppressWarnings({"UnusedDeclaration"}) public void setFirstName(String firstName)
        {
            _firstName = firstName;
        }

        public String getLastName()
        {
            return _lastName;
        }

        @SuppressWarnings({"UnusedDeclaration"}) public void setLastName(String lastName)
        {
            _lastName = lastName;
        }

        public String getDisplayName()
        {
            return _displayName;
        }

        @SuppressWarnings({"UnusedDeclaration"}) public void setDisplayName(String displayName)
        {
            _displayName = displayName;
        }

        public Integer getEmailFormatId()
        {
            return _emailFormatId;
        }

        public void setEmailFormatId(Integer emailFormatId)
        {
            _emailFormatId = emailFormatId;
        }

        public Integer getEmailOptionId()
        {
            return _emailOptionId;
        }

        public void setEmailOptionId(Integer emailOptionId)
        {
            _emailOptionId = emailOptionId;
        }

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }

        public int getPageTypeId()
        {
            return _pageTypeId;
        }

        public void setPageTypeId(int pageTypeId)
        {
            _pageTypeId = pageTypeId;
        }

        public boolean isProjectMember()
        {
            return _projectMember;
        }

        public void setProjectMember(boolean projectMember)
        {
            _projectMember = projectMember;
        }

        public User getUser()
        {
            return UserManager.getUser(_userId);
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public String getSrcIdentifier()
        {
            return _srcIdentifier;
        }

        public void setSrcIdentifier(String srcIdentifier)
        {
            _srcIdentifier = srcIdentifier;
        }

        @Override
        public String toString()
        {
            return getEmail() + " " + _emailOptionId + " " + _srcIdentifier;
        }
    }


    public static class EmailOption implements MessageConfigService.NotificationOption
    {
        int _emailOptionId;
        String _emailOption;
        String _type;

        public String getEmailOption()
        {
            return _emailOption;
        }

        public void setEmailOption(String emailOption)
        {
            _emailOption = emailOption;
        }

        public int getEmailOptionId()
        {
            return _emailOptionId;
        }

        public void setEmailOptionId(int emailOptionId)
        {
            _emailOptionId = emailOptionId;
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }
    }
}
