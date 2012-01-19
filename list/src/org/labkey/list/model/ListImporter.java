/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

package org.labkey.list.model;

import org.apache.log4j.Logger;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListDefinition.KeyType;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.Type;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 2:12:01 PM
*/
public class ListImporter
{
    private static final String TYPE_NAME_COLUMN = "ListName";

    public void process(File root, File listsDir, Container c, User user, List<String> errors, Logger log) throws Exception
    {
        File schemaFile = new File(listsDir, "lists.xml");

        TablesDocument tablesDoc;

        try
        {
            tablesDoc = TablesDocument.Factory.parse(schemaFile, XmlBeansUtil.getDefaultParseOptions());
            XmlBeansUtil.validateXmlDocument(tablesDoc);
        }
        catch (XmlValidationException xve)
        {
            // Note: different constructor than the one below
            throw new InvalidFileException(root, schemaFile, xve);
        }
        catch (Exception e)
        {
            throw new InvalidFileException(root, schemaFile, e);
        }

        TablesDocument.Tables tablesXml = tablesDoc.getTables();
        List<String> names = new LinkedList<String>();

        Map<String, ListDefinition> lists = ListService.get().getLists(c);

        for (TableType tableType : tablesXml.getTableArray())
        {
            String name = tableType.getTableName();
            names.add(name);

            ListDefinition def = lists.get(name);

            if (null != def)
                def.delete(user);

            try
            {
                createNewList(c, user, name, tableType, errors);
            }
            catch (ImportException e)
            {
                throw new ImportException("Error creating list \"" + name + "\": " + e.getMessage());
            }
        }

        lists = ListService.get().getLists(c);

        for (String name : names)
        {
            ListDefinition def = lists.get(name);

            if (null != def)
            {
                String legalName = FileUtil.makeLegalName(name);
                File tsv = new File(listsDir, legalName + ".tsv");

                if (tsv.exists())
                {
                    errors.addAll(def.insertListItems(user, DataLoader.getDataLoaderForFile(tsv), new File(listsDir, legalName), null));

                    // TODO: Error the entire job on import error?
                }
            }
        }

        log.info(names.size() + " list" + (1 == names.size() ? "" : "s") + " imported");
    }

    private void createNewList(Container c, User user, String listName, TableType listXml, List<String> errors) throws Exception
    {
        String keyName = listXml.getPkColumnName();

        if (null == keyName)
        {
            errors.add("List \"" + listName + "\": no pkColumnName set.");
        }

        KeyType pkType = getKeyType(listXml, keyName);

        ListDefinition list = ListService.get().createList(c, listName);
        list.setKeyName(keyName);
        list.setKeyType(pkType);
        list.setDescription(listXml.getDescription());

        if (listXml.isSetTitleColumn())
            list.setTitleColumn(listXml.getTitleColumn());

        list.save(user);

        // TODO: This code is largely the same as SchemaXmlReader -- should consolidate

        // Set up RowMap with all the keys that OntologyManager.importTypes() handles
        RowMapFactory<Object> mapFactory = new RowMapFactory<Object>(TYPE_NAME_COLUMN, "Property", "PropertyURI", "Label", "Description",
                "RangeURI", "NotNull", "ConceptURI", "Format", "InputType", "HiddenColumn", "MvEnabled", "LookupFolderPath",
                "LookupSchema", "LookupQuery", "URL", "ImportAliases", "ShownInInsertView", "ShownInUpdateView",
                "ShownInDetailsView", "Measure", "Dimension");
        List<Map<String, Object>> importMaps = new LinkedList<Map<String, Object>>();
        List<List<ConditionalFormat>> formats = new ArrayList<List<ConditionalFormat>>();

        for (ColumnType columnXml : listXml.getColumns().getColumnArray())
        {
            String columnName = columnXml.getColumnName();

            if (columnXml.getIsKeyField())
            {
                if (!columnName.equalsIgnoreCase(keyName))
                    throw new ImportException("More than one key specified: '" + keyName + "' and '" + columnName + "'");

                continue;  // Skip the key columns
            }

            String dataType = columnXml.getDatatype();
            Type t = Type.getTypeBySqlTypeName(dataType);

            if (t == null)
                t = Type.getTypeByLabel(dataType);

            if (t == null)
                throw new ImportException("Unknown property type \"" + dataType + "\" for property \"" + columnXml.getColumnName() + "\".");

            // Assume nullable if not specified
            boolean notNull = columnXml.isSetNullable() && !columnXml.getNullable();

            boolean mvEnabled = columnXml.isSetIsMvEnabled() ? columnXml.getIsMvEnabled() : null != columnXml.getMvColumnName();

            // These default to being visible if nothing's specified in the XML
            boolean shownInInsertView = !columnXml.isSetShownInInsertView() || columnXml.getShownInInsertView();
            boolean shownInUpdateView = !columnXml.isSetShownInUpdateView() || columnXml.getShownInUpdateView();
            boolean shownInDetailsView = !columnXml.isSetShownInDetailsView() || columnXml.getShownInDetailsView();

            boolean measure = columnXml.isSetMeasure() && columnXml.getMeasure();
            boolean dimension = columnXml.isSetDimension() && columnXml.getDimension();

            Set<String> importAliases = new LinkedHashSet<String>();
            if (columnXml.isSetImportAliases())
            {
                importAliases.addAll(Arrays.asList(columnXml.getImportAliases().getImportAliasArray()));
            }

            ColumnType.Fk fk = columnXml.getFk();

            Map<String, Object> map = mapFactory.getRowMap(new Object[]{
                listName,
                columnName,
                columnXml.getPropertyURI(),
                columnXml.getColumnTitle(),
                columnXml.getDescription(),
                t.getXsdType(),
                notNull,
                columnXml.getConceptURI(),
                columnXml.getFormatString(),
                columnXml.isSetInputType() ? columnXml.getInputType() : null,
                columnXml.getIsHidden(),
                mvEnabled,
                null != fk ? fk.getFkFolderPath() : null,
                null != fk ? fk.getFkDbSchema() : null,
                null != fk ? fk.getFkTable() : null,
                columnXml.getUrl(),
                ColumnRenderProperties.convertToString(importAliases),
                shownInInsertView,
                shownInUpdateView,
                shownInDetailsView,
                measure,
                dimension
            });
            formats.add(ConditionalFormat.convertFromXML(columnXml.getConditionalFormats()));

            importMaps.add(map);
        }

        final String typeURI = list.getDomain().getTypeURI();

        DomainURIFactory factory = new DomainURIFactory() {
            public String getDomainURI(String name)
            {
                return typeURI;
            }
        };

        PropertyDescriptor[] pds = OntologyManager.importTypes(factory, TYPE_NAME_COLUMN, importMaps, errors, c, true);

        if (!errors.isEmpty())
            return;

        assert pds.length == formats.size();

        for (int i = 0; i < pds.length; i++)
        {
            List<ConditionalFormat> pdFormats = formats.get(i);

            if (!pdFormats.isEmpty())
            {
                PropertyService.get().saveConditionalFormats(user, pds[i], pdFormats);
            }
        }
    }

    private KeyType getKeyType(TableType listXml, String keyName) throws ImportException
    {
        for (ColumnType columnXml : listXml.getColumns().getColumnArray())
        {
            if (columnXml.getColumnName().equals(keyName))
            {
                String datatype = columnXml.getDatatype();

                if (datatype.equalsIgnoreCase("varchar"))
                    return KeyType.Varchar;

                if (datatype.equalsIgnoreCase("integer"))
                    return columnXml.getIsAutoInc() ? KeyType.AutoIncrementInteger : KeyType.Integer;

                throw new ImportException("unknown key type \"" + datatype + "\" for key \"" + keyName + "\".");
            }
        }

        throw new ImportException("pkColumnName is set to \"" + keyName + "\" but column is not defined.");
    }
}
