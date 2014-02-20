/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Nov 3, 2008
 */
public abstract class ContainerFilter
{
    /**
     * Users of ContainerFilter should use getSQLFragment() or createFilterClause(), not build up their own SQL using
     * the IDs.
     * @return null if no filtering should be done, otherwise the set of valid container ids
     */
    @Nullable
    protected abstract Collection<String> getIds(Container currentContainer);

    /**
     * May return null if the ContainerFilter has no corresponding ContainerFilter.Type.
     */
    @Nullable
    public abstract Type getType();

    /**
     * If we can't find the name, we default to CURRENT
     */
    public static ContainerFilter getContainerFilterByName(String name, User user)
    {
        Type type = Type.Current;
        try
        {
            type = Type.valueOf(name);
        }
        catch (IllegalArgumentException e)
        {
            // Revert to Current
        }
        return type.create(user);
    }

    /**
     * The standard ContainerFilter SQL includes data from workbooks if the parent is already in the list via a join.
     * Therefore, we can filter out any workbooks from the list so that we don't need to pass as many Ids in the SQL.
     * This is important for servers that have lots and lots of workbooks, like the O'Conner server which has more than
     * 10,000.
     */
    protected Collection<Container> removeWorkbooks(Collection<Container> containers)
    {
        Set<Container> result = new HashSet<>(containers.size());
        for (Container c : containers)
        {
            if (!c.isWorkbook())
            {
                result.add(c);
            }
        }
        return result;
    }

    /** Create a FilterClause that restricts based on the containers that meet the filter */
    public SimpleFilter.FilterClause createFilterClause(DbSchema schema, FieldKey containerFilterColumn, Container container)
    {
        return new ContainerClause(schema, containerFilterColumn, this, container);
    }

    /** Create a FilterClause that restricts based on the containers that meet the filter and user that meets the permission*/
    public SimpleFilter.FilterClause createFilterClause(DbSchema schema, FieldKey containerFilterColumn, Container container, Class<? extends Permission> permission)
    {
        return new ContainerClause(schema, containerFilterColumn, this, container, permission);
    }


    /** Create an expression for a WHERE clause */
    public SQLFragment getSQLFragment(DbSchema schema, FieldKey containerColumnFieldKey, Container container, Map<FieldKey, ? extends ColumnInfo> columnMap)
    {
        ColumnInfo columnInfo = columnMap.get(containerColumnFieldKey);
        SQLFragment sql;
        if (columnInfo != null)
        {
            sql = new SQLFragment(columnInfo.getSelectName());
        }
        else
        {
            sql = new SQLFragment(containerColumnFieldKey.toString());
        }
        return getSQLFragment(schema, sql, container);
    }

    /** Create an expression for a WHERE clause */
    public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container)
    {
        return getSQLFragment(schema, containerColumnSQL, container, true, true);
    }

    /**
     * Create an expression for a WHERE clause
     * @param useJDBCParameters whether or not to use JDBC parameters for the container ids, or embed them directly.
     * Generally parameters are preferred, but can cause perf problems in certain cases
     * @param allowNulls - if looking at ALL rows, whether to allow nulls in the Container column
     */
    public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container, boolean useJDBCParameters, boolean allowNulls)
    {
        SecurityLogger.indent("ContainerFilter");
        Collection<String> ids = getIds(container);
        SecurityLogger.outdent();
        return getSQLFragment(schema, containerColumnSQL, ids, useJDBCParameters, allowNulls);
    }

    // instances of ContainerFilterWithUser will call this getSQLFragment after GetIds with a specific permission to check against the user
    protected SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Collection<String> ids, boolean useJDBCParameters, boolean allowNulls)
    {
        if (ids == null)
        {
            if (allowNulls)
            {
                return new SQLFragment("1 = 1");
            }
            else
            {
                SQLFragment result = new SQLFragment(containerColumnSQL);
                result.append(" IS NOT NULL");
                return result;
            }
        }

        if (ids.isEmpty())
        {
            return new SQLFragment("1 = 0");
        }

        if (ids.size() == 1)
        {
            Container first = ContainerManager.getForId(ids.iterator().next());
            if (null == first)
                return new SQLFragment("1 = 0");
            if (!first.hasWorkbookChildren())
                return new SQLFragment(containerColumnSQL).append("=").append("'").append(first.getId()).append("'");
        }

        SQLFragment result = new SQLFragment(containerColumnSQL);
        result.append(" IN (SELECT c.EntityId FROM ");
        result.append(CoreSchema.getInstance().getTableInfoContainers(), "c");
        result.append(" INNER JOIN (SELECT CAST(x.Id AS ");
        result.append(schema.getSqlDialect().getGuidType());
        result.append(") AS Id FROM (");
        String separator = "";
        for (String containerId : ids)
        {
            result.append(separator);
            separator = " UNION\n\t\t";
            result.append("SELECT ");
            // Need to add casts to make Postgres happy
            if (useJDBCParameters)
            {
                result.append("?");
                result.add(containerId);
            }
            else
            {
                result.append("'");
                result.append(containerId);
                result.append("'");
            }
            result.append(" AS Id");
        }
        // Filter based on the container's ID, or the container is a child of the ID and of type workbook
        result.append(") x) x ON c.EntityId = x.Id OR (c.Parent = x.Id AND c.Type = 'workbook'))");
        return result;
    }

    public enum Type
    {
        Current("Current folder")
        {
            public ContainerFilter create(User user)
            {
                return CURRENT;
            }
        },
        CurrentAndFirstChildren("Current folder and first children that are not workbooks")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentAndFirstChildren(user);
            }
        },
        CurrentAndSubfolders("Current folder and subfolders")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentAndSubfolders(user);
            }
        },
        CurrentAndSiblings("Current folder and siblings")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentAndSiblings(user);
            }
        },
        CurrentOrParentAndWorkbooks("Current folder and/or parent if the current folder is a workbook, plus all workbooks in this series")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentOrParentAndWorkbooks(user);
            }
        },
        CurrentPlusProject("Current folder and project")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentPlusProject(user);
            }
        },
        CurrentAndParents("Current folder and parent folders")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentAndParents(user);
            }
        },
        CurrentPlusProjectAndShared("Current folder, project, and Shared project")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentPlusProjectAndShared(user);
            }
        },
        WorkbookAssay("Current folder, project, and Shared project")
        {
            public ContainerFilter create(User user)
            {
                return new WorkbookAssay(user);
            }
        },
        WorkbookAndParent("Current workbook and parent")
        {
            public ContainerFilter create(User user)
            {
                return new WorkbookAndParent(user);
            }
        },
        StudyAndSourceStudy("Current study and its source/parent study")
        {
            public ContainerFilter create(User user)
            {
                return new StudyAndSourceStudy(user, false);
            }
        },
        AllFolders("All folders")
        {
            public ContainerFilter create(User user)
            {
                return new AllFolders(user);
            }
        };


        private final String _description;

        private Type(String description)
        {
            _description = description;
        }

        @Override
        public String toString()
        {
            return _description;
        }

        public abstract ContainerFilter create(User user);
    }

    public static final ContainerFilter CURRENT = new ContainerFilter()
    {
        public Collection<String> getIds(Container currentContainer)
        {
            return Collections.singleton(currentContainer.getId());
        }

        @Override
        public String toString()
        {
            return "Current Folder";
        }

        public Type getType()
        {
            return Type.Current;
        }
    };

    /** Use this with extreme caution - it doesn't check permissions */
    public static final ContainerFilter EVERYTHING = new ContainerFilter()
    {
        public Collection<String> getIds(Container currentContainer)
        {
            return null;
        }

        public Type getType()
        {
            return null;
        }
    };

    public static abstract class ContainerFilterWithUser extends ContainerFilter
    {
        protected final User _user;

        public ContainerFilterWithUser(User user)
        {
            _user = user;
        }

        public SQLFragment getSQLFragment(DbSchema schema, FieldKey containerColumnFieldKey, Container container, Class<? extends Permission> permission)
        {
            return getSQLFragment(schema, new SQLFragment(containerColumnFieldKey.toString()), container, permission);
        }

        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container, Class<? extends Permission> permission)
        {
            return getSQLFragment(schema, containerColumnSQL, container, permission, true, true);
        }

        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container, Class<? extends Permission> permission, boolean useJDBCParameters, boolean allowNulls)
        {
            SecurityLogger.indent("ContainerFilter");
            Collection<String> ids = getIds(container, permission);
            SecurityLogger.outdent();
            return getSQLFragment(schema, containerColumnSQL, ids, useJDBCParameters, allowNulls);
        }

        public abstract Collection<String> getIds(Container currentContainer, Class<? extends Permission> permission);

        // If a permission is not explicitly passed, then use ReadPermission by default.  Otherwise, subclasses
        // of ContainerFilterWithUser should override.
        public Collection<String> getIds(Container currentContainer)
        {
            return getIds(currentContainer, ReadPermission.class);
        }
    }

    public static class SimpleContainerFilter extends ContainerFilter
    {
        private final Collection<String> _ids;

        public SimpleContainerFilter(Collection<Container> containers)
        {
            _ids = toIds(containers);
        }

        public Collection<String> getIds(Container currentContainer)
        {
            return _ids;
        }

        public Type getType()
        {
            return null;
        }
    }

    public static class CurrentPlusExtras extends ContainerFilterWithUser
    {
        private final Container[] _extraContainers;

        public CurrentPlusExtras(User user, Container... extraContainers)
        {
            super(user);
            _extraContainers = extraContainers;
        }

        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Set<Container> containers = new HashSet<>();
            containers.add(currentContainer);
            for (Container extraContainer : _extraContainers)
            {
                if (extraContainer.hasPermission(_user, perm))
                {
                    containers.add(extraContainer);
                }
            }
            return toIds(containers);
        }

        public Type getType()
        {
            return null;
        }
    }

    public static class CurrentAndFirstChildren extends ContainerFilterWithUser
    {
        public CurrentAndFirstChildren(User user)
        {
            super(user);
        }

        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Set<Container> containers = new HashSet<>();
            for(Container c : ContainerManager.getChildren(currentContainer, _user, perm))
            {
                if(!c.isWorkbook() && c.hasPermission(_user, perm))
                {
                    containers.add(c);
                }
            }
            containers.add(currentContainer);
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentAndFirstChildren;
        }
    }

    public static class CurrentAndSubfolders extends ContainerFilterWithUser
    {
        public CurrentAndSubfolders(User user)
        {
            super(user);
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container, Class<? extends Permission> permission, boolean useJDBCParameters, boolean allowNulls)
        {
            if (_user.isSiteAdmin() && container.isRoot())
                return new SQLFragment("1 = 1");
            return super.getSQLFragment(schema,containerColumnSQL,container, permission, useJDBCParameters,allowNulls);
        }

        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            List<Container> containers = new ArrayList<>(removeWorkbooks(ContainerManager.getAllChildren(currentContainer, _user, perm)));
            containers.add(currentContainer);
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentAndSubfolders;
        }
    }

    public static class CurrentPlusProject extends ContainerFilterWithUser
    {
        public CurrentPlusProject(User user)
        {
            super(user);
        }

        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Set<Container> containers = new HashSet<>();
            containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null && project.hasPermission(_user, perm))
            {
                containers.add(project);
            }
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentPlusProject;
        }
    }

    public static class CurrentAndParents extends ContainerFilterWithUser
    {
        public CurrentAndParents(User user)
        {
            super(user);
        }

        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Set<Container> containers = new HashSet<>();
            do
            {
                if (currentContainer.hasPermission(_user, perm))
                {
                    containers.add(currentContainer);
                }
                currentContainer = currentContainer.getParent();
            }
            while (currentContainer != null && !currentContainer.isRoot());
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentAndParents;
        }
    }

    public static class WorkbookAssay extends CurrentPlusProjectAndShared
    {
        public WorkbookAssay(User user)
        {
            super(user);
        }

        @Override
        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Collection<String> result = super.getIds(currentContainer, perm);
            if (result == null)
            {
                return null;
            }
            if (currentContainer.isWorkbook() && currentContainer.getParent().hasPermission(_user, perm))
            {
                result.add(currentContainer.getParent().getId());
            }
            return result;
        }

        @Override
        public Type getType()
        {
            return Type.WorkbookAssay;
        }
    }

    public static class WorkbookAndParent extends ContainerFilterWithUser
    {
        public WorkbookAndParent(User user)
        {
            super(user);
        }

        @Override
        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Set<String> result = new HashSet<>();
            if (currentContainer.hasPermission(_user, perm))
            {
                result.add(currentContainer.getId());
            }
            if (currentContainer.isWorkbook() && currentContainer.getParent().hasPermission(_user, perm))
            {
                result.add(currentContainer.getParent().getId());
            }
            return result;
        }

        @Override
        public Type getType()
        {
            return Type.WorkbookAndParent;
        }
    }

    public static class CurrentOrParentAndWorkbooks extends ContainerFilterWithUser
    {
        public CurrentOrParentAndWorkbooks(User user)
        {
            super(user);
        }

        @Override
        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Set<String> result = new HashSet<>();
            if (currentContainer.hasPermission(_user, perm))
                result.add(currentContainer.getId());

            if (currentContainer.isWorkbook())
            {
                if(currentContainer.getParent().hasPermission(_user, perm))
                    result.add(currentContainer.getParent().getId());
            }

            return result;
        }

        @Override
        public Type getType()
        {
            return Type.CurrentOrParentAndWorkbooks;
        }
    }

    public static class CurrentAndSiblings extends ContainerFilterWithUser
    {
        public CurrentAndSiblings(User user)
        {
            super(user);
        }

        @Override
        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Set<String> result = new HashSet<>();

            if (currentContainer.isRoot() && currentContainer.hasPermission(_user, perm))
                result.add(currentContainer.getId());  //if not root, we will add the current container below

            Container parent = currentContainer.getParent();
            if(parent != null)
            {
                for(Container c : parent.getChildren())
                {
                    if (c.hasPermission(_user, perm))
                    {
                        result.add(c.getId());
                    }
                }
            }

            return result;
        }

        @Override
        public Type getType()
        {
            return Type.CurrentAndSiblings;
        }
    }

    public static class StudyAndSourceStudy extends ContainerFilterWithUser
    {
        private boolean _skipPermissionChecks;

        public StudyAndSourceStudy(User user, boolean skipPermissionChecks)
        {
            super(user);
            _skipPermissionChecks = skipPermissionChecks;
        }

        @Override
        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Set<String> result = new HashSet<>();
            if (_skipPermissionChecks || currentContainer.hasPermission(_user, perm))
            {
                result.add(currentContainer.getId());
            }
            Study study = StudyService.get().getStudy(currentContainer);
            if (study != null && study.isAncillaryStudy())
            {
                Study sourceStudy = study.getSourceStudy();
                if (sourceStudy != null && (_skipPermissionChecks || sourceStudy.getContainer().hasPermission(_user, perm)))
                {
                    result.add(sourceStudy.getContainer().getId());
                }
            }
            return result;
        }

        @Override
        public Type getType()
        {
            return Type.StudyAndSourceStudy;
        }
    }


    public static class CurrentPlusProjectAndShared extends ContainerFilterWithUser
    {
        public CurrentPlusProjectAndShared(User user)
        {
            super(user);
        }

        @Override
        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Set<Container> containers = new HashSet<>();
            containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null && project.hasPermission(_user, perm))
            {
                containers.add(project);
            }
            Container shared = ContainerManager.getSharedContainer();
            if (shared.hasPermission(_user, perm))
            {
                containers.add(shared);
            }
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentPlusProjectAndShared;
        }
    }

    public static class AllInProject extends ContainerFilterWithUser
    {
        public AllInProject(User user)
        {
            super(user);
        }

        @Override
        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            Container project = currentContainer.isProject() ? currentContainer : currentContainer.getProject();
            if (project == null)
            {
                // Don't allow anything
                return Collections.emptySet();
            }
            Set<Container> containers = new HashSet<>(removeWorkbooks(ContainerManager.getAllChildren(project, _user, perm)));
            containers.add(project);
            return toIds(containers);
        }

        public Type getType()
        {
            return null;
        }
    }

    public static class AllFolders extends ContainerFilterWithUser
    {
        public AllFolders(User user)
        {
            super(user);
        }

        @Override
        public Collection<String> getIds(Container currentContainer, Class<? extends Permission> perm)
        {
            if (_user.isSiteAdmin())
            {
                // Don't bother filtering, the user can see everything
                return null;
            }
            List<Container> containers = ContainerManager.getAllChildren(ContainerManager.getRoot(), _user, perm);
            // To reduce the number of ids that need to be passed around, filter out workbooks. They'll get included
            // automatically because we always add them via the SQL that we generate
            Set<String> ids = new HashSet<>();
            for (Container container : containers)
            {
                if (!container.isWorkbook())
                {
                    ids.add(container.getId());
                }
            }
            if (ContainerManager.getRoot().hasPermission(_user, perm))
            {
                ids.add(ContainerManager.getRoot().getId());
            }
            return ids;
        }

        public Type getType()
        {
            return Type.AllFolders;
        }
    }

    public static Set<String> toIds(Collection<Container> containers)
    {
        Set<String> ids = new HashSet<>();
        for (Container container : containers)
        {
            ids.add(container.getId());
        }
        return ids;
    }

    public static class ContainerClause extends SimpleFilter.FilterClause
    {
        private final DbSchema _schema;
        private final FieldKey _fieldKey;
        private final ContainerFilter _filter;
        private final Container _container;
        private final Class<? extends Permission> _permission;

        public ContainerClause(DbSchema schema, FieldKey fieldKey, ContainerFilter filter, Container container)
        {
            this(schema, fieldKey, filter, container, null);
        }

        public ContainerClause(DbSchema schema, FieldKey fieldKey, ContainerFilter filter, Container container, Class<? extends Permission> permission)
        {
            _schema = schema;
            _fieldKey = fieldKey;
            _filter = filter;
            _container = container;
            _permission = (permission != null) ? permission : ReadPermission.class;
        }

        @Override
        @Deprecated // Use getFieldKeys() instead.
        public List<String> getColumnNames()
        {
            return Collections.singletonList(_fieldKey.toString());
        }

        @Override
        public List<FieldKey> getFieldKeys()
        {
            return Collections.singletonList(_fieldKey);
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            if (_filter instanceof ContainerFilterWithUser)
            {
                ContainerFilterWithUser filter = (ContainerFilterWithUser) _filter;
                return filter.getSQLFragment(_schema, _fieldKey, _container, _permission);
            }
            return _filter.getSQLFragment(_schema, _fieldKey, _container, columnMap);
        }
    }
}
