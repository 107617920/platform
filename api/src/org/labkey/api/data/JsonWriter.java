/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.util.ExtUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Created: Feb 11, 2008 12:57:07 PM
 */
public class JsonWriter
{
    public static List<Map<String,Object>> getNativeColProps(TableInfo tinfo, Collection<FieldKey> fields, FieldKey fieldKeyPrefix, boolean includeDomainFormat)
    {
        List<ColumnInfo> columns = new ArrayList<ColumnInfo>(tinfo.getColumns());
        LinkedHashMap<FieldKey, ColumnInfo> allColumns = QueryService.get().getColumns(tinfo, fields, columns);
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>();
        for (ColumnInfo cinfo : allColumns.values())
        {
            displayColumns.add(cinfo.getDisplayColumnFactory().createRenderer(cinfo));
        }

        return getNativeColProps(displayColumns, fieldKeyPrefix, includeDomainFormat);
    }

    public static List<Map<String,Object>> getNativeColProps(Collection<DisplayColumn> columns, FieldKey fieldKeyPrefix, boolean includeDomainFormat)
    {
        List<Map<String,Object>> colProps = new ArrayList<Map<String,Object>>();
        for (DisplayColumn column : columns)
        {
            colProps.add(JsonWriter.getMetaData(column, fieldKeyPrefix, true, true, includeDomainFormat));
        }
        return colProps;
    }

    public static Map<String, Object> getMetaData(DisplayColumn dc, FieldKey fieldKeyPrefix, boolean useFriendlyAsType, boolean includeLookup, boolean includeDomainFormat)
    {
        ColumnInfo cinfo = dc.getColumnInfo();
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        JSONObject ext = new JSONObject();
        props.put("ext",ext);

        // Some DisplayColumns aren't backed by a ColumnInfo, so handle null when determining metadata
        String name = cinfo == null ? dc.getName() : cinfo.getName();
        props.put("name", (null != fieldKeyPrefix ? FieldKey.fromString(fieldKeyPrefix, name) : name));
        if (cinfo != null && null != cinfo.getDescription())
            props.put("description", cinfo.getDescription());

        String friendlyTypeName = cinfo == null ? ColumnInfo.getFriendlyTypeName(dc.getDisplayValueClass()) : cinfo.getFriendlyTypeName();
        props.put("friendlyType", friendlyTypeName);
        props.put("type", useFriendlyAsType ? friendlyTypeName : dc.getJsonTypeName());
        props.put("jsonType", dc.getJsonTypeName());
        props.put("sqlType", cinfo == null ? null : cinfo.getSqlTypeName());
        props.put("defaultValue", cinfo == null ? null : cinfo.getDefaultValue());

        if (includeDomainFormat)
        {
            PropertyType propertyType = PropertyType.getFromClass(dc.getValueClass());
            if (propertyType != null)
            {
                props.put("typeName", propertyType.getXmlName());
                props.put("typeURI", propertyType.getTypeUri());
            }
        }

        FieldKey fieldKey;
        if (cinfo != null && null != cinfo.getFieldKey())
            fieldKey = cinfo.getFieldKey();
        else
            fieldKey = new FieldKey(null, name);

        props.put("fieldKey", fieldKey.toString());
        props.put("fieldKeyArray", new JSONArray(fieldKey.getParts()));
        props.put("fieldKeyPath", FieldKey.fromParts(fieldKeyPrefix, fieldKey).toString());

        // Duplicate booleans with alternate property name for backwards compatibility
        boolean autoIncrement = cinfo != null && cinfo.isAutoIncrement();
        props.put("isAutoIncrement", autoIncrement);
        props.put("autoIncrement", autoIncrement);
        boolean hidden = cinfo != null && cinfo.isHidden();
        props.put("isHidden", hidden);
        props.put("hidden", hidden);
        boolean keyField = cinfo != null && cinfo.isKeyField();
        props.put("isKeyField", keyField);
        props.put("keyField", keyField);
        boolean mvEnabled = cinfo != null && cinfo.isMvEnabled();
        props.put("isMvEnabled", mvEnabled);
        props.put("mvEnabled", mvEnabled);
        boolean nullable = cinfo != null && cinfo.isNullable();
        props.put("isNullable", nullable);
        props.put("nullable", nullable);
        if (includeDomainFormat)
            props.put("required", !nullable);
        boolean readOnly = cinfo != null && cinfo.isReadOnly();
        props.put("isReadOnly", readOnly);
        props.put("readOnly", readOnly);
        boolean userEditable = cinfo != null && cinfo.isUserEditable();
        props.put("isUserEditable", userEditable);
        props.put("userEditable", userEditable);
        boolean versionColumn = cinfo != null && cinfo.isVersionColumn();
        props.put("isVersionField", versionColumn);
        props.put("versionField", versionColumn);
        boolean selectable = cinfo != null && !cinfo.isUnselectable();
        props.put("isSelectable", selectable);  //avoid double-negative boolean name
        props.put("selectable", selectable);    //avoid double-negative boolean name

        // These fields are new and don't need to have the "is" prefix for backwards compatibility
        props.put("shownInInsertView", cinfo != null && cinfo.isShownInInsertView());
        props.put("shownInUpdateView", cinfo != null && cinfo.isShownInUpdateView());
        props.put("shownInDetailsView", cinfo == null || cinfo.isShownInDetailsView());
        props.put("dimension", cinfo != null && cinfo.isDimension());
        props.put("measure", cinfo != null && cinfo.isMeasure());

        props.put("protected", cinfo != null && cinfo.isProtected());

        ColumnInfo displayField = cinfo != null ? cinfo.getDisplayField() : null;
        if (displayField != null && displayField != cinfo)
        {
            props.put("displayField", displayField.getFieldKey().toString());
            props.put("displayFieldSqlType", displayField.getSqlTypeName());
            DisplayColumn renderer = displayField.getRenderer();
            props.put("displayFieldJsonType", renderer.getJsonTypeName());
            //String displayFriendlyTypeName = displayField == null ? ColumnInfo.getFriendlyTypeName(renderer.getDisplayValueClass()) : displayField.getFriendlyTypeName();
            //props.put("displayFieldType", displayFriendlyTypeName);
        }

        if (cinfo != null)
        {

            if (!cinfo.getImportAliasSet().isEmpty())
            {
                props.put("importAliases", new ArrayList<String>(cinfo.getImportAliasSet()));
            }

            if (cinfo.getTsvFormatString() != null)
            {
                props.put("tsvFormat", cinfo.getTsvFormatString());
            }
            if (cinfo.getFormat() != null)
            {
                String format = cinfo.getFormat();
                props.put("format", format);
                if (includeDomainFormat)
                    props.put("formatString", format);
                String extFormat = null;
                String extFormatFn = null;
                if (cinfo.getJdbcType().isDateOrTime())
                {
                    extFormat = ExtUtil.toExtDateFormat(format);
                    extFormatFn = ExtUtil.toExtDateFormatFn(format);
                }
                else if (cinfo.getJdbcType().isNumeric())
                {
                    extFormat = ExtUtil.toExtNumberFormat(format);
                    extFormatFn = ExtUtil.toExtNumberFormatFn(format);
                }
                if (null != extFormatFn)
                    props.put("extFormatFn", extFormatFn);
                if (null != extFormat)
                    props.put("extFormat", extFormat);
            }
            if (cinfo.getExcelFormatString() != null)
            {
                props.put("excelFormat", cinfo.getExcelFormatString());
            }

            props.put("inputType", cinfo.getInputType());
            // UNDONE ext info for other field typesxtype: checkbox, combo, datefield, field, hidden, htmleditor, numberfield, radio, textarea, textfield, timefield
            //fmdata.put("xtype","");
            if ("textarea".equals(cinfo.getInputType()))
            {
                if (dc instanceof DataColumn)
                {
                    int cols = ((DataColumn)dc).getInputLength();
                    if (cols > 0)
                        props.put("cols", Math.min(1000,cols));
                    int rows = ((DataColumn)dc).getInputRows();
                    if (rows > 0)
                        props.put("rows", Math.min(1000,rows));
                }
                ext.put("xtype","textarea");
            }

            props.put("shortCaption", cinfo.getShortLabel());

            if (cinfo.getFacetingBehaviorType() != null)
                props.put("facetingBehaviorType", cinfo.getFacetingBehaviorType().name());

            if (cinfo.getCrosstabColumnMember() != null)
            {
                CrosstabMember member = cinfo.getCrosstabColumnMember();
                Map<String, Object> jsonMember = new HashMap<String, Object>();
                jsonMember.put("value", String.valueOf(member.getValue()));
                jsonMember.put("caption", member.getCaption());
                jsonMember.put("dimensionFieldKey", new JSONArray(member.getDimensionFieldKey().getParts()));
                props.put("crosstabColumnMember", jsonMember);

                props.put("crosstabColumnDimension", cinfo.getCrosstabColumnDimension());
            }
        }
        else
        {
            props.put("shortCaption", dc.getCaption());
        }

        props.put("caption", dc.getCaption());
        if (includeDomainFormat)
            props.put("label", dc.getCaption());

        if (includeLookup && cinfo != null)
        {
            Map<String, Object> lookupJSON = getLookupInfo(cinfo, includeDomainFormat);
            if (lookupJSON != null)
            {
                props.put("lookup", lookupJSON);
            }
        }

        return props;
    }
    
    public static JSONObject getLookupInfo(ColumnInfo columnInfo, boolean includeDomainFormat)
    {
        ForeignKey fk = columnInfo.getFk();

        //lookup info
        TableInfo lookupTable = columnInfo.getFkTableInfo();
        if (null != fk
                && null != lookupTable
                && (!(fk instanceof RowIdForeignKey) || !(((RowIdForeignKey)fk).getOriginalColumn().equals(columnInfo))))
        {
            JSONObject lookupInfo = new JSONObject();
            if (null != fk.getLookupContainerId())
            {
                Container fkContainer = ContainerManager.getForId(fk.getLookupContainerId());
                if (null != fkContainer)
                {
                    lookupInfo.put("containerPath", fkContainer.getPath());
                    if (includeDomainFormat)
                        lookupInfo.put("container", fkContainer.getPath());
                }
            }

            boolean isPublic = lookupTable.isPublic() && null != lookupTable.getPublicName() && null != lookupTable.getPublicSchemaName();
            // Duplicate with alternate property name for backwards compatibility
            lookupInfo.put("isPublic", isPublic);
            lookupInfo.put("public", isPublic);
            String queryName;
            String schemaName;
            if (isPublic)
            {
                queryName = lookupTable.getPublicName();
                schemaName = lookupTable.getPublicSchemaName();
            }
            else
            {
                queryName = lookupTable.getName();
                schemaName = lookupTable.getSchema().getName();
            }
            // Duplicate info with different property names for backwards compatibility
            lookupInfo.put("queryName", queryName);
            lookupInfo.put("table", queryName);
            lookupInfo.put("schemaName", schemaName);
            lookupInfo.put("schema", schemaName);

            ColumnInfo displayColumn = fk.createLookupColumn(columnInfo, null);
            if (displayColumn != null && displayColumn.getFieldKey() != null && displayColumn.getFieldKey().getParts().size() > columnInfo.getFieldKey().getParts().size())
            {
                lookupInfo.put("displayColumn", displayColumn.getFieldKey().getName());
            }
            else
            {
                // In this case, we likely won't be able to resolve the column when executing the query, but
                // it's the best guess that we have
                lookupInfo.put("displayColumn", lookupTable.getTitleColumn());
            }
            String key = null;
            List<String> pks = lookupTable.getPkColumnNames();
            if (null != pks && pks.size() > 0)
                key = pks.get(0);
            if (null != pks && pks.size() == 2 && ("container".equalsIgnoreCase(key) || "containerid".equalsIgnoreCase(key)))
                key = pks.get(1);
            lookupInfo.put("keyColumn", key);

            if (fk instanceof MultiValuedForeignKey)
            {
                MultiValuedForeignKey mvfk = (MultiValuedForeignKey)fk;
                String junctionLookup = mvfk.getJunctionLookup();
                lookupInfo.put("multiValued", junctionLookup != null ? "junction" : "value");
                if (junctionLookup != null)
                    lookupInfo.put("junctionLookup", junctionLookup);
            }

            return lookupInfo;
        }

        return null;
    }
}
