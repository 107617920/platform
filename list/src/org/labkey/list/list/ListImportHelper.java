/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.list.list;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.FileUtil;
import org.labkey.list.model.FileNameUniquifier;
import org.labkey.list.model.ListItemImpl;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ListImportHelper implements OntologyManager.ImportHelper
{
    private final User _user;
    private final ListDefinition _list;
    private final DomainProperty[] _properties;
    private final ColumnDescriptor _cdKey;
    @Nullable
    private final File _attachmentDir;
    private final Map<String, FileNameUniquifier> _uniquifiers = new HashMap<String, FileNameUniquifier>();

    public ListImportHelper(User user, ListDefinition list, DomainProperty[] properties, ColumnDescriptor cdKey, @Nullable File attachmentDir)
    {
        _user = user;
        _list = list;
        _properties = properties;
        _cdKey = cdKey;
        _attachmentDir = attachmentDir;
    }
    
    public String beforeImportObject(Map<String, Object> map) throws SQLException
    {
        try
        {
            Object key = (null == _cdKey ? null : map.get(_cdKey.name));  // Could be null in auto-increment case
            ListItem item = (null == key ? null : _list.getListItem(key));

            if (item == null)
            {
                item = _list.createListItem();
                item.setKey(key);
            }
            else
            {
                for (DomainProperty pd : _properties)
                {
                    item.setProperty(pd, null);
                }
            }

            String ret = ((ListItemImpl) item).ensureOntologyObject().getObjectURI();
            item.save(_user);

            List<AttachmentFile> attachmentFiles = new LinkedList<AttachmentFile>();

            for (DomainProperty pd : _list.getDomain().getProperties())
            {
                if (pd.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
                {
                    String columnName = pd.getName();
                    File file = (File)map.get(pd.getPropertyDescriptor().getPropertyURI());

                    if (null != file)
                    {
                        FileNameUniquifier uniquifier = _uniquifiers.get(columnName);

                        if (null == uniquifier)
                        {
                            uniquifier = new FileNameUniquifier();
                            _uniquifiers.put(columnName, uniquifier);
                        }

                        String filename = file.getName();
                        File aFile = new File(new File(_attachmentDir, FileUtil.makeLegalName(columnName)), uniquifier.uniquify(filename));
                        AttachmentFile attachmentFile = new FileAttachmentFile(aFile);
                        attachmentFile.setFilename(filename);
                        attachmentFiles.add(attachmentFile);
                    }
                }
            }

            if (!attachmentFiles.isEmpty())
                AttachmentService.get().addAttachments(_user, new ListItemAttachmentParent(item, _list.getContainer()), attachmentFiles);

            return ret;
        }
        catch (SQLException sqlException)
        {
            throw sqlException;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public void afterImportObject(String lsid, ObjectProperty[] props) throws SQLException
    {
    }
}
