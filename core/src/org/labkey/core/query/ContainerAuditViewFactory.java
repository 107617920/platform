/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.core.query;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 2, 2007
 *
 * Event field documentation:
 *
 * created - Timestamp
 * createdBy - User who created the record
 * impersonatedBy - user who was impersonating the user (or null)
 * comment - record description
 * projectId - the project id
 * container - container id of the domain event
 *
 */
public class ContainerAuditViewFactory extends SimpleAuditViewFactory
{
    private static final ContainerAuditViewFactory _instance = new ContainerAuditViewFactory();

    public static ContainerAuditViewFactory getInstance()
    {
        return _instance;
    }

    private ContainerAuditViewFactory(){}

    public String getEventType()
    {
        return ContainerManager.CONTAINER_AUDIT_EVENT;
    }

    public String getName()
    {
        return "Project and Folder events";
    }

    @Override
    public String getDescription()
    {
        return "Information about project and folder modifications.";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter("EventType", ContainerManager.CONTAINER_AUDIT_EVENT);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        
        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }
}
