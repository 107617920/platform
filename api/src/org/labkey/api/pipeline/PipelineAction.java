/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.labkey.api.view.NavTree;
import org.labkey.api.util.URLHelper;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;

/**
 * Represents an action that might be performed on a set of files in the pipeline.
 * User: jeckels
 * Date: Dec 16, 2009
 */
public class PipelineAction
{
    String _description;
    NavTree _links;
    File[] _files;
    boolean _allowMultiSelect;
    boolean _allowEmptySelect;

    /** Use NavTree to create a drop-down menu with submenus for the specified files */
    public PipelineAction(NavTree links, File[] files, boolean allowMultiSelect, boolean allowEmptySelect)
    {
        _links = links;
        _files = files;
        _allowMultiSelect = allowMultiSelect;
        _allowEmptySelect = allowEmptySelect;
    }

    /** Use a simple button for the specified files */
    public PipelineAction(String id, String label, URLHelper href, File[] files, boolean allowMultiSelect)
    {
        this(new NavTree(label, href), files, allowMultiSelect, false);
        _links.setId(id);
    }

    /** Use a simple button for the specified files */
    public PipelineAction(String id, String label, URLHelper href, File[] files, boolean allowMultiSelect, boolean allowEmptySelect)
    {
        this(new NavTree(label, href), files, allowMultiSelect, allowEmptySelect);
        _links.setId(id);
    }

    public String getLabel()
    {
        return _links.getText();
    }

    public File[] getFiles()
    {
        return _files;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public NavTree getLinks()
    {
        return _links;
    }

    public void setId(String id)
    {
        _links.setId(id);
    }
    
    public JSONObject toJSON()
    {
        JSONObject o = new JSONObject();

        o.put("description", _description==null ? "" : _description);

        JSONObject links = _links.toJSON();
        o.put("links", links);
        o.put("multiSelect", _allowMultiSelect);
        o.put("emptySelect", _allowEmptySelect);

        JSONArray files = new JSONArray();
        if (null != _files)
            for (File f : _files)
                files.put(f.getName());
        o.put("files", files);

        return o;
    }
}
