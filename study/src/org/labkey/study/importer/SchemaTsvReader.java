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
package org.labkey.study.importer;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.DataSetDefinition;
import org.springframework.validation.BindException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

/**
 * User: adam
 * Date: May 26, 2009
 * Time: 10:31:36 AM
 */
public class SchemaTsvReader implements SchemaReader
{
    private final List<Map<String, Object>> _importMaps;
    private final Map<Integer, DataSetImportInfo> _datasetInfoMap;
    private final String _typeNameColumn;


    private SchemaTsvReader(Study study, TabLoader loader, String labelColumn, String typeNameColumn, String typeIdColumn, Map<String, DatasetImporter.DatasetImportProperties> extraImportProps, BindException errors) throws IOException
    {
        loader.setParseQuotes(true);
        List<Map<String, Object>> mapsLoad = loader.load();

        _importMaps = new ArrayList<Map<String, Object>>(mapsLoad.size());
        _datasetInfoMap = new HashMap<Integer, DataSetImportInfo>();
        _typeNameColumn = typeNameColumn;

        if (mapsLoad.size() > 0)
        {
            int missingTypeNames = 0;
            int missingTypeIds = 0;
            int missingTypeLabels = 0;

            for (Map<String, Object> props : mapsLoad)
            {
                props = new CaseInsensitiveHashMap<Object>(props);

                String typeName = (String) props.get(typeNameColumn);
                Object typeIdObj = props.get(typeIdColumn);
                String propName = (String) props.get("Property");

                if (typeName == null || typeName.length() == 0)
                {
                    missingTypeNames++;
                    continue;
                }

                if (!(typeIdObj instanceof Integer))
                {
                    missingTypeIds++;
                    continue;
                }

                Integer typeId = (Integer) typeIdObj;
                DatasetImporter.DatasetImportProperties extraProps = null != extraImportProps ? extraImportProps.get(typeName) : null;

                boolean isHidden;

                if (null != extraProps)
                {
                    isHidden = !extraProps.isShowByDefault();
                }
                else
                {
                    Boolean hidden = (Boolean) props.get("hidden");
                    isHidden = (null != hidden && hidden.booleanValue());
                }

                DataSetImportInfo info = _datasetInfoMap.get(typeId);

                if (info != null)
                {
                    if (!info.name.equals(typeName))
                    {
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " is associated with multiple type names ('" + typeName + "' and '" + info.name + "').");
                        return;
                    }
                    if (!info.isHidden == isHidden)
                    {
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " is set as both hidden and not hidden in different fields.");
                        return;
                    }
                }

                // we've got a good entry
                if (null == info)
                {
                    info = new DataSetImportInfo(typeName);
                    info.label = (String) props.get(labelColumn);
                    if (info.label == null || info.label.length() == 0)
                    {
                        missingTypeLabels++;
                        continue;
                    }

                    info.isHidden = isHidden;
                    _datasetInfoMap.put((Integer) typeIdObj, info);
                }

                // filter out the built-in types
                if (DataSetDefinition.isDefaultFieldName(propName, study))
                    continue;

                // look for visitdate column
                String conceptURI = (String)props.get("ConceptURI");
                if (null == conceptURI)
                {
                    String vtype = (String)props.get("vtype");  // datafax special case
                    if (null != vtype && vtype.toLowerCase().contains("visitdate"))
                        conceptURI = DataSetDefinition.getVisitDateURI();
                }

                if (DataSetDefinition.getVisitDateURI().equalsIgnoreCase(conceptURI))
                {
                    if (info.visitDatePropertyName == null)
                        info.visitDatePropertyName = propName;
                    else
                    {
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " has multiple visitdate fields (" + info.visitDatePropertyName + " and " + propName+").");
                        return;
                    }
                }

                // Deal with extra key field
                IntegerConverter intConverter = new IntegerConverter(0);
                Integer keyField = (Integer)intConverter.convert(Integer.class, props.get("key"));
                if (keyField != null && keyField.intValue() == 1)
                {
                    if (info.keyPropertyName == null)
                        info.keyPropertyName = propName;
                    else
                    {
                        // It's already been set
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " has multiple fields with key set to 1.");
                        return;
                    }
                }

                // Deal with managed key field
                String keyTypeName = props.get("AutoKey") == null ? null : props.get("AutoKey").toString();
                DataSetDefinition.KeyManagementType keyType = DataSet.KeyManagementType.findMatch(keyTypeName);
                if (keyType != DataSet.KeyManagementType.None)
                {
                    if (info.keyManagementType == DataSet.KeyManagementType.None)
                        info.keyManagementType = keyType;
                    else
                    {
                        // It's already been set
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " has multiple fields set to AutoKey.");
                        return;
                    }
                    // Check that our key is the key field as well
                    if (!propName.equals(info.keyPropertyName))
                    {
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " is set to AutoKey, but is not a key");
                        return;
                    }
                }

                // Category field
                String category = null != extraProps ? extraProps.getCategory() : (String)props.get("Category");

                if (category != null && !"".equals(category))
                {
                    if (info.category != null && !info.category.equalsIgnoreCase(category))
                    {
                        // It's changed from field to field within the same dataset
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " has multiple fields set with different categories");
                        return;
                    }
                    else
                    {
                        info.category = category;
                    }
                }

                _importMaps.add(props);
            }

            if (missingTypeNames > 0)
            {
                errors.reject("SchemaTsvReader", "Couldn't find type name in column " + typeNameColumn + " in " + missingTypeNames + " rows.");
                return;
            }

            if (missingTypeIds > 0)
            {
                errors.reject("SchemaTsvReader", "Couldn't find type id in column " + typeIdColumn + " in " + missingTypeIds + " rows.");
                return;
            }

            if (missingTypeLabels > 0)
            {
                errors.reject("SchemaTsvReader", "Couldn't find type label in column " + typeIdColumn + " in " + missingTypeLabels + " rows.");
                return;
            }
        }
    }

    public SchemaTsvReader(Study study, String tsv, String labelColumn, String typeNameColumn, String typeIdColumn, BindException errors) throws IOException
    {
        this(study, new TabLoader(tsv, true), labelColumn, typeNameColumn, typeIdColumn, null, errors);
    }

    public SchemaTsvReader(Study study, VirtualFile root, String tsvFileName, String labelColumn, String typeNameColumn, String typeIdColumn, Map<String, DatasetImporter.DatasetImportProperties> extraImportProps, BindException errors) throws IOException
    {
        this(study, new TabLoader(new BufferedReader(new InputStreamReader(root.getInputStream(tsvFileName))), true), labelColumn, typeNameColumn, typeIdColumn, extraImportProps, errors);
    }

    public List<Map<String, Object>> getImportMaps()
    {
        return _importMaps;
    }

    @Override
    public List<List<ConditionalFormat>> getConditionalFormats()
    {
        return null;
    }

    public Map<Integer, DataSetImportInfo> getDatasetInfo()
    {
        return _datasetInfoMap;
    }

    public String getTypeNameColumn()
    {
        return _typeNameColumn;
    }
}
