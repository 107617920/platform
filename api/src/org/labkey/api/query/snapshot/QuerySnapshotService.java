/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.api.query.snapshot;

import org.labkey.api.data.DisplayColumn;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.util.HString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * User: Karl Lum
 * Date: Jul 8, 2008
 * Time: 1:57:12 PM
 */

public class QuerySnapshotService
{
    static private Map<String, I> _providers = new HashMap<String, I>();
    public static final String TYPE = "Query Snapshot";

    static public synchronized I get(String schema)
    {
        // todo: add the default provider
        return _providers.get(schema);
    }

	static public synchronized I get(HString schema)
	{
		// todo: add the default provider
		return _providers.get(schema.getSource());
	}

    static public synchronized void registerProvider(String schema, I provider)
    {
        if (_providers.containsKey(schema))
            throw new IllegalStateException("A snapshot provider for schema :" + schema + " has already been registered");

        _providers.put(schema, provider);
    }

    public interface I
    {
        public String getName();
        public String getDescription();

        public ActionURL getCreateWizardURL(QuerySettings settings, ViewContext context);

        public ActionURL getEditSnapshotURL(QuerySettings settings, ViewContext context);

        public ActionURL createSnapshot(QuerySnapshotForm form, BindException errors) throws Exception;

        /**
         * Regenerates the snapshot data, may be invoked either as the result of a manual or
         * automatic update. The implementation is responsible for logging its own audit event.
         */
        public ActionURL updateSnapshot(QuerySnapshotForm form, BindException errors) throws Exception;

        public ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def, BindException errors) throws Exception;

        /**
         * Returns the audit history view for a snapshot.
         */
        public HttpView createAuditView(QuerySnapshotForm form) throws Exception;

        /**
         * Returns the list of valid display columns for a specified QueryForm. The list of columns
         * is used during the creation and editing of a snapshot, and in any UI where the list of columns
         * pertaining to a snapshot can be modified.  
         */
        public List<DisplayColumn> getDisplayColumns(QueryForm queryForm, BindException errors) throws Exception;
    }

    public interface AutoUpdateable {
    }
}
