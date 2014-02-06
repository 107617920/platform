/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
package org.labkey.announcements.model;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.announcements.AnnouncementsController.DownloadAction;
import org.labkey.announcements.AnnouncementsController.ThreadAction;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.DownloadURL;
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Transient;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Bean Class for AnnouncementModel.
 */
public class AnnouncementModel extends AttachmentParentEntity implements Serializable
{
    private int _rowId = 0;
    private String _parentId = null;
    private WikiRendererType _rendererType;
    private List<Integer> _memberListIds = null; // The list of userIds persisted/retrieved from the comm.userList table
    private List<String> _memberListDisplay = null; // The sanitized list displayed to the user, emails or display names dependent on permissions
    private String _memberListInput = null; // The value coming back on form submission
    private String _body = null;

    private Date _expires = null;
    private Integer _assignedTo = null;
    private String _status = null;
    private String _title = null;

    // for discussions
    private String _discussionSrcIdentifier = null;
    private String _discussionSrcURL = null;

    private int _responseCount = 0;

    private Collection<AnnouncementModel> _responses = new ArrayList<>();
    private Set<User> _authors;


    /**
     * Standard constructor.
     */
    public AnnouncementModel()
    {
    }

    /**
     * Returns the rowId
     *
     * @return the rowId
     */
    public int getRowId()
    {
        return _rowId;
    }


    /**
     * Sets the rowId
     *
     * @param rowId the new rowId value
     */
    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }


    /**
     * Returns the title
     *
     * @return the title
     */
    public String getTitle()
    {
        return _title;
    }


    /**
     * Sets the title
     *
     * @param title the new title value
     */
    public void setTitle(java.lang.String title)
    {
        _title = title;
    }


    /**
     * Returns the expires
     *
     * @return the expires
     */
    public Date getExpires()
    {
        return _expires;
    }


    /**
     * Sets the expires
     *
     * @param expires the new expires value
     */
    public void setExpires(java.util.Date expires)
    {
        _expires = expires;
    }


    /**
     * Returns the body
     *
     * @return the body
     */
    public String getBody()
    {
        return _body;
    }


    /**
     * Sets the body
     *
     * @param body the new body value
     */
    public void setBody(String body)
    {
        _body = body;
    }

    public String getParent()
    {
        return _parentId;
    }


    public void setParent(String parentId)
    {
        _parentId = parentId;
    }

    public String getCreatedByName(User currentUser)
    {
        return getCreatedByName(false, currentUser);
    }


    public String getCreatedByName(boolean includeGroups, User currentUser)
    {
        return getDisplayName(getCreatedBy(), includeGroups, currentUser);
    }


    private String getDisplayName(int userId, boolean includeGroups, User currentUser)
    {
        String name = UserManager.getDisplayNameOrUserId(userId, currentUser);

        if (includeGroups)
        {
            User user = UserManager.getUser(userId);

            if (null != user)
            {
                String groupList = SecurityManager.getGroupList(ContainerManager.getForId(getContainerId()), user);

                if (groupList.length() > 0)
                    return name + " (" + groupList + ")";
            }
        }

        return name;
    }


    public String getAssignedToName(User currentUser)
    {
        return UserManager.getDisplayNameOrUserId(getAssignedTo(), currentUser);
    }


    public Collection<Attachment> getAttachments()
    {
        return AttachmentService.get().getAttachments(this);
    }


    public Collection<AnnouncementModel> getResponses()
    {
        return _responses;
    }


    public void setResponses(Collection<AnnouncementModel> responses)
    {
        _responses = responses;
    }


    public ActionURL getThreadURL(Container container)
    {
        return new ActionURL(ThreadAction.class, container);
    }

    public String translateBody(Container container)
    {
        DownloadURL urlAttach = new DownloadURL(DownloadAction.class, container, getEntityId(), "");

        return getFormattedHtml(urlAttach.getLocalURIString());
    }

    //returns string corresponding to name of enum entry
    public String getRendererType()
    {
        if (_rendererType == null)
        {
            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            _rendererType = null != wikiService ? wikiService.getDefaultMessageRendererType() : null;
        }

        return null != _rendererType ? _rendererType.name() : "none";
    }

    public void setRendererType(String rendererType)
    {
        _rendererType = WikiRendererType.valueOf(rendererType);
    }

    @Transient
    public String getFormattedHtml()
    {
        return getFormattedHtml(null);
    }

    private String getFormattedHtml(@Nullable String attachPrefix)
    {
        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);

        if (null == wikiService)
            return null;

        if (_rendererType == null)
            _rendererType = wikiService.getDefaultMessageRendererType();

        if (null == attachPrefix)
            return wikiService.getFormattedHtml(_rendererType, _body);
        else
            return wikiService.getFormattedHtml(_rendererType, _body, attachPrefix, getAttachments());
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public Integer getAssignedTo()
    {
        return _assignedTo;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setAssignedTo(Integer assignedTo)
    {
        _assignedTo = assignedTo;
    }

    @NotNull
    public List<Integer> getMemberListIds()
    {
        if (_memberListIds == null)
        {
            AnnouncementManager.attachMemberLists(this);
        }
        return _memberListIds;
    }

    public void setMemberListIds(List<Integer> memberListIds)
    {
        _memberListIds = memberListIds;
    }

    public String getMemberListInput()
    {
        return _memberListInput;
    }

    public void setMemberListInput(String memberListInput)
    {
        _memberListInput = memberListInput;
    }

    public List<String> getMemberListDisplay(Container c, User currentUser)
    {
        if (_memberListDisplay == null)
        {
            _memberListDisplay = new ArrayList<>();
            boolean seeEmailAddresses = SecurityManager.canSeeEmailAddresses(c, currentUser);
            for (Integer userId : getMemberListIds())
            {
                if (seeEmailAddresses)
                    _memberListDisplay.add(UserManager.getUser(userId).getEmail());
                else
                    _memberListDisplay.add(UserManager.getUser(userId).getDisplayName(currentUser));
            }
        }
        return _memberListDisplay;
    }

    public String getMemberListDisplayString(Container c, User currentUser)
    {
        return StringUtils.join(getMemberListDisplay(c, currentUser), " ");
    }

    public String getDiscussionSrcIdentifier()
    {
        return _discussionSrcIdentifier;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setDiscussionSrcIdentifier(String discussionSrcIdentifier)
    {
        _discussionSrcIdentifier = discussionSrcIdentifier;
    }

    public String getDiscussionSrcURL()
    {
        return _discussionSrcURL;
    }

    public void setDiscussionSrcURL(String discussionSrcURL)
    {
        _discussionSrcURL = discussionSrcURL;
    }

    public int getResponseCount()
    {
        return _responseCount;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setResponseCount(int responseCount)
    {
        _responseCount = responseCount;
    }

    public Set<User> getAuthors()
    {
        if (null == _authors)
        {
            AnnouncementModel a = AnnouncementManager.getAnnouncement(lookupContainer(), getParent() == null ? getEntityId() : getParent(), true);

            if (a == null)
            {
                // We haven't been saved to the database yet, so don't stash a list of authors yet
                return Collections.emptySet();
            }

            Collection<AnnouncementModel> responses = a.getResponses();
            Set<User> responderSet = new HashSet<>();

            //add creator of each response to responder set
            for (AnnouncementModel response : responses)
            {
                //do we need to handle case where responder is not in a project group?
                User user = UserManager.getUser(response.getCreatedBy());
                //add to responder set, so we know who responders are
                responderSet.add(user);
            }

            //add creator of parent to responder set
            responderSet.add(UserManager.getUser(a.getCreatedBy()));
            //add creator of this message/response
            responderSet.add(UserManager.getUser(getCreatedBy()));

            _authors = responderSet;
        }

        return _authors;
    }

    /** Find the srcIdentifier for this thread. It's only stored at the parent level, so look there if needed */
    public String lookupSrcIdentifer()
    {
        if (getParent() == null)
        {
            return getDiscussionSrcIdentifier();
        }
        else
        {
            AnnouncementModel parent = AnnouncementManager.getAnnouncement(lookupContainer(), getParent());

            // Null check to avoid race condition (e.g., post message -> delete folder -> notification email
            // thread tries to access parent, a sequence that can happen in the tests)
            return null != parent ? parent.getDiscussionSrcIdentifier() : null;
        }
    }
}

