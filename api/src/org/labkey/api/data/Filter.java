/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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

import java.util.Set;
import java.util.Map;
import java.util.List;

/**
 * User: arauch
 * Date: Jan 11, 2005
 * Time: 8:01:17 AM
 */
public interface Filter
{
    public SQLFragment getSQLFragment(TableInfo tableInfo, @Nullable List<ColumnInfo> colInfos);
    public SQLFragment getSQLFragment(SqlDialect dialect, Map<FieldKey, ? extends ColumnInfo> columnMap);
    public boolean isEmpty();
    public Set<FieldKey> getWhereParamFieldKeys();

    /**
     * @return the SQL fragment with the parameter values substituted in.  Suitable for using as a key in a cache,
     * or pasting into an external SQL tool.
     */
    public String toSQLString(SqlDialect dialect);

    /**
     * toString() on a Filter doesn't do the correct thing.  Should use "substituteParameters" instead.
     * @return
     */
    @Deprecated
    public String toString();
}
