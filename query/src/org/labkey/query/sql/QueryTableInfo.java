/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.query.sql;

import org.labkey.api.data.*;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.QueryService;

import java.util.Collection;
import java.util.Collections;

public class QueryTableInfo extends AbstractTableInfo implements ContainerFilterable
{
    QueryRelation _relation;
    private ContainerFilter _containerFilter;

    public QueryTableInfo(QueryRelation relation, String name)
    {
        super(relation._query.getSchema().getDbSchema());
        _relation = relation;
        setName(name);
    }

    public QueryRelation getQueryRelation()
    {
        return _relation;
    }

    @NotNull
    public SQLFragment getFromSQL()
    {
        throw new IllegalStateException();
    }


    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment f = new SQLFragment();
        SQLFragment sql = _relation.getSql();
        f.append("(").append(sql).append(") ").append(alias);
        return f;
    }


    @Override
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        Query query = _relation._query;
        Collection<QueryService.ParameterDecl> ret = query.getParameters();
        if (null == ret)
            return Collections.EMPTY_LIST;
        return ret;
    }

    @Override
    public boolean needsContainerClauseAdded()
    {
        // Let the underlying schemas do whatever filtering they need on the data, especially since
        // after columns are part of a query we lose track of what was on the base table and what's been joined in
        return false;
    }

    public void setContainerFilter(@NotNull ContainerFilter containerFilter)
    {
        _containerFilter = containerFilter;
    }

    public boolean hasDefaultContainerFilter()
    {
        return false;
    }

    @NotNull
    @Override
    public ContainerFilter getContainerFilter()
    {
        if (_containerFilter == null)
            return ContainerFilter.CURRENT;
        return _containerFilter;
    }
}
