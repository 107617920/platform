/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.query.QueryDocument;
import org.labkey.data.xml.query.QueryType;
import org.labkey.query.persist.QueryManager;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:21:56 PM
 */
public class QueryImporter implements FolderImporter
{
    public String getDescription()
    {
        return "queries";
    }

    public void process(PipelineJob job, ImportContext ctx, VirtualFile root) throws ServletException, XmlException, IOException, SQLException, ImportException
    {
        VirtualFile queriesDir = ctx.getDir("queries");

        if (null != queriesDir)
        {
            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());

            // get the list of files and split them into sql and xml file name arrays
            String[] queryFileNames = queriesDir.list();
            ArrayList<String> sqlFileNames = new ArrayList<String>();
            Map<String, QueryDocument> metaFilesMap = new HashMap<String, QueryDocument>();
            for (String fileName : queryFileNames)
            {
                if (fileName.endsWith(QueryWriter.FILE_EXTENSION))
                {
                    // make sure a SQL file/input stream exists before adding it to the array
                    if (null != queriesDir.getInputStream(fileName))
                        sqlFileNames.add(fileName);
                }
                else if (fileName.endsWith(QueryWriter.META_FILE_EXTENSION))
                {
                    // make sure the XML file is valid before adding it to the map
                    XmlObject metaXml = queriesDir.getXmlBean(fileName);
                    try
                    {
                        if (metaXml instanceof QueryDocument)
                        {
                            QueryDocument queryDoc = (QueryDocument)metaXml;
                            XmlBeansUtil.validateXmlDocument(queryDoc, fileName);
                            metaFilesMap.put(fileName, queryDoc);
                        }
                        else
                            throw new ImportException("Unable to get an instance of QueryDocument from " + fileName);
                    }
                    catch (XmlValidationException e)
                    {
                        throw new InvalidFileException(queriesDir.getRelativePath(fileName), e);
                    }
                }
            }

            // Map of new and updated queries by schema
            Map<SchemaKey, List<String>> createdQueries = new LinkedHashMap<>();
            Map<SchemaKey, List<QueryChangeListener.QueryPropertyChange>> changedQueries = new LinkedHashMap<>();

            for (String sqlFileName : sqlFileNames)
            {
                String baseFilename = sqlFileName.substring(0, sqlFileName.length() - QueryWriter.FILE_EXTENSION.length());
                String metaFileName = baseFilename + QueryWriter.META_FILE_EXTENSION;
                QueryDocument queryDoc = metaFilesMap.get(metaFileName);

                if (null == queryDoc)
                    throw new ServletException("QueryImport: SQL file \"" + sqlFileName + "\" has no corresponding meta data file.");

                String sql = PageFlowUtil.getStreamContentsAsString(queriesDir.getInputStream(sqlFileName));

                QueryType queryXml = queryDoc.getQuery();

                String queryName = queryXml.getName();
                String schemaName = queryXml.getSchemaName();
                SchemaKey schemaKey = SchemaKey.fromString(schemaName);

                // Reuse the existing queryDef so created or change events will be fired appropriately.
                QueryDefinition queryDef = QueryService.get().getQueryDef(ctx.getUser(), ctx.getContainer(), schemaName, queryName);
                if (queryDef != null)
                {
                    List<QueryChangeListener.QueryPropertyChange> changes = changedQueries.get(schemaKey);
                    if (changes == null)
                        changedQueries.put(schemaKey, changes = new ArrayList<>());
                    changes.add(new QueryChangeListener.QueryPropertyChange(queryDef));
                }
                else
                {
                    queryDef = QueryService.get().createQueryDef(ctx.getUser(), ctx.getContainer(), schemaKey, queryName);
                    List<String> created = createdQueries.get(schemaKey);
                    if (created == null)
                        createdQueries.put(schemaKey, created = new ArrayList<>());
                    created.add(queryName);
                }

                queryDef.setSql(sql);
                queryDef.setDescription(queryXml.getDescription());

                if (null != queryXml.getMetadata())
                    queryDef.setMetadataXml(queryXml.getMetadata().xmlText());

                queryDef.save(ctx.getUser(), ctx.getContainer(), false);

                metaFilesMap.remove(metaFileName);
            }

            // fire query created events
            for (Map.Entry<SchemaKey, List<String>> entry : createdQueries.entrySet())
            {
                SchemaKey schemaKey = entry.getKey();
                List<String> queries = entry.getValue();
                QueryManager.get().fireQueryCreated(ctx.getContainer(), null, schemaKey, queries);
            }

            // fire query changed events
            for (Map.Entry<SchemaKey, List<QueryChangeListener.QueryPropertyChange>> entry : changedQueries.entrySet())
            {
                SchemaKey schemaKey = entry.getKey();
                List<QueryChangeListener.QueryPropertyChange> changes = entry.getValue();
                QueryManager.get().fireQueryChanged(ctx.getContainer(), null, schemaKey, null, changes);
            }

            ctx.getLogger().info(sqlFileNames.size() + " quer" + (1 == sqlFileNames.size() ? "y" : "ies") + " imported");
            ctx.getLogger().info("Done importing " + getDescription());

            // check to make sure that each meta xml file was used
            if (metaFilesMap.size() > 0)
                throw new ImportException("Not all query meta xml files had corresponding sql.");
        }
    }

    public Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root) throws Exception
    {
        List<PipelineJobWarning> warnings = new ArrayList<PipelineJobWarning>();

        //validate all queries in all schemas in the container
        ctx.getLogger().info("Post-processing " + getDescription());
        ctx.getLogger().info("Validating all queries in all schemas...");
        Container container = ctx.getContainer();
        User user = ctx.getUser();
        DefaultSchema defSchema = DefaultSchema.get(user, container);

        ValidateQueriesVisitor validator = new ValidateQueriesVisitor(true);
        Boolean valid = validator.visitTop(defSchema, ctx.getLogger());

        ctx.getLogger().info("Finished validating queries.");
        if (valid != null && valid)
        {
            assert validator.getTotalCount() == validator.getValidCount();
            ctx.getLogger().info(String.format("Finished validating queries: All %d passed validation.", validator.getTotalCount()));
        }
        else
        {
            ctx.getLogger().info(String.format("Finished validating queries: %d of %d failed validation.", validator.getInvalidCount(), validator.getTotalCount()));
        }


        for (Pair<String, Throwable> warn : validator.getWarnings())
        {
            warnings.add(new PipelineJobWarning(warn.first, warn.second));
        }

        ctx.getLogger().info("Done post-processing " + getDescription());
        return warnings;
    }
    
    @Override
    public boolean supportsVirtualFile()
    {
        return false;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        public FolderImporter create()
        {
            return new QueryImporter();
        }
    }
}
