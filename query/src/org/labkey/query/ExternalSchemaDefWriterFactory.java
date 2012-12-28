/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.query;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.MultiTablesType;
import org.labkey.data.xml.externalSchema.ExportedSchemaType;
import org.labkey.data.xml.externalSchema.ExternalSchemaDocument;
import org.labkey.data.xml.externalSchema.ExternalSchemaType;
import org.labkey.data.xml.externalSchema.LinkedSchemaDocument;
import org.labkey.data.xml.externalSchema.LinkedSchemaType;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.query.persist.AbstractExternalSchemaDef;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.LinkedSchemaDef;
import org.labkey.query.persist.QueryManager;

import java.util.Arrays;
import java.util.List;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class ExternalSchemaDefWriterFactory implements FolderWriterFactory
{
    private static final String DEFAULT_DIRECTORY = "externalSchemas";
    public static final String EXTERNAL_SCHEMA_FILE_EXTENSION =  ".externalschema.xml";
    public static final String LINKED_SCHEMA_FILE_EXTENSION =  ".linkedschema.xml";

    @Override
    public FolderWriter create()
    {
        return new ExternalSchemaDefWriter();
    }

    public class ExternalSchemaDefWriter extends BaseFolderWriter
    {
        @Override
        public String getSelectionText()
        {
            return "External schema definitions";
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            List<ExternalSchemaDef> externalSchemas = Arrays.asList(QueryManager.get().getExternalSchemaDefs(c));
            List<LinkedSchemaDef> linkedSchemas = Arrays.asList(QueryManager.get().getLinkedSchemaDefs(c));
            if (!externalSchemas.isEmpty() || !linkedSchemas.isEmpty())
            {
                ctx.getXml().addNewExternalSchemas().setDir(DEFAULT_DIRECTORY);
                VirtualFile extSchemasDir = vf.getDir(DEFAULT_DIRECTORY);

                writeExternalSchemas(externalSchemas, extSchemasDir);
                writeLinkedSchemas(linkedSchemas, extSchemasDir);
            }
        }

        private void writeExternalSchemas(List<ExternalSchemaDef> defs, VirtualFile extSchemasDir) throws Exception
        {
            for (ExternalSchemaDef def : defs)
            {
                ExternalSchemaDocument defDoc = ExternalSchemaDocument.Factory.newInstance();
                ExternalSchemaType defXml = defDoc.addNewExternalSchema();

                defXml.setEditable(def.isEditable());
                defXml.setIndexable(def.isIndexable());
                defXml.setDataSource(def.getDataSource());

                addCommonProperties(defXml, def);

                extSchemasDir.saveXmlBean(def.getUserSchemaName() + EXTERNAL_SCHEMA_FILE_EXTENSION, defDoc);
            }
        }

        private void writeLinkedSchemas(List<LinkedSchemaDef> defs, VirtualFile extSchemasDir) throws Exception
        {
            for (LinkedSchemaDef def : defs)
            {
                LinkedSchemaDocument defDoc = LinkedSchemaDocument.Factory.newInstance();
                LinkedSchemaType defXml = defDoc.addNewLinkedSchema();

                // XXX: Should serialize container path or container id?
                Container sourceContainer = def.lookupSourceContainer();
                defXml.setSourceContainer(sourceContainer.getId());

                addCommonProperties(defXml, def);

                extSchemasDir.saveXmlBean(def.getUserSchemaName() + EXTERNAL_SCHEMA_FILE_EXTENSION, defDoc);
            }
        }

        private void addCommonProperties(ExportedSchemaType defXml, AbstractExternalSchemaDef def) throws XmlException
        {
            defXml.setUserSchemaName(def.getUserSchemaName());

            if (def.getSchemaTemplate() != null)
            {
                defXml.setSchemaTemplate(def.getSchemaTemplate());
            }
            else
            {
                defXml.setSourceSchemaName(def.getSourceSchemaName());

                ExternalSchemaType.Tables tablesXml = defXml.addNewTables();
                String tables = def.getTables();
                if (tables == null || tables.equals("*"))
                {
                    tablesXml.addTableName("*");
                }
                else
                {
                    for (String table : tables.split(","))
                    {
                        tablesXml.addTableName(table);
                    }
                }

                if (null != def.getMetaData())
                {
                    MultiTablesType metaDataXml = MultiTablesType.Factory.parse(def.getMetaData());
                    defXml.setMetadata(metaDataXml);
                }
            }

        }

        @Override
        public boolean supportsVirtualFile()
        {
            return true;
        }
    }
}
