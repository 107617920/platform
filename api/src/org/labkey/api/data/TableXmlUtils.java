/*
 * Copyright (c) 2005-2009 LabKey Corporation
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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.OntologyType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;

import java.io.InputStream;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * User: phussey
 * Date: Sep 19, 2005
 * Time: 11:09:11 PM
 */
public class TableXmlUtils
{
    private static Logger _log = Logger.getLogger(TableXmlUtils.class);

    public static TablesDocument getXmlDocumentFromMetaData(String dbSchemaName, boolean bFull) throws Exception
    {

        DbSchema dbSchema = DbSchema.createFromMetaData(dbSchemaName);
        TablesDocument xmlTablesDoc = TablesDocument.Factory.newInstance();
        TablesDocument.Tables xmlTables = xmlTablesDoc.addNewTables();

        for (SchemaTableInfo tableInfo : dbSchema.getTables())
        {
            TableType xmlTable = xmlTables.addNewTable();
            tableInfo.copyToXml(xmlTable, bFull);
        }

        return xmlTablesDoc;
    }

    public static void loadMapsFromTableXml(String dbSchemaName, Map tableNameMap, Map columnNameMap)
    {
        InputStream xmlStream = null;

        try
        {

            xmlStream = DbSchema.getSchemaXmlStream(dbSchemaName);
            if (null == xmlStream)
            {
                _log.debug("No xml tables doc found for " + dbSchemaName);
                return;
            }

            TablesDocument tablesDoc = TablesDocument.Factory.parse(xmlStream);
            // todo: cache these tableDocs so DbSchema.get doesn't have to reload them

            TableType[] ts = tablesDoc.getTables().getTableArray();
            ColumnType[] cs;

            Map ttempMap = new CaseInsensitiveHashMap(ts.length);
            Map ctempMap;
            for (TableType t : ts)
            {
                ttempMap.put(t.getTableName(), t.getTableName());
                cs = t.getColumns().getColumnArray();
                ctempMap = new CaseInsensitiveHashMap(cs.length);
                for (ColumnType c : cs)
                    ctempMap.put(c.getColumnName(), c.getColumnName());

                columnNameMap.putAll(ctempMap);
            }
            tableNameMap.putAll(ttempMap);
        }
        catch (Exception e)
        {
            _log.log(Level.ERROR, "Exception loading schema " + dbSchemaName, e);
        }
        finally
        {
            try
            {
                if (null != xmlStream) xmlStream.close();
            }
            catch (Exception x)
            {
            }
        }
    }

    public static String compareXmlToMetaData(String dbSchemaName, boolean bFull, boolean bCaseSensitive)
    {
        StringBuilder sbOut = new StringBuilder();

        InputStream xmlStream = null;
        try
        {
            TablesDocument tablesDocMetaData;
            TablesDocument tablesDocFromXml = null;

            tablesDocMetaData = getXmlDocumentFromMetaData(dbSchemaName, false);
            xmlStream = DbSchema.getSchemaXmlStream(dbSchemaName);
            if (null != xmlStream)
                tablesDocFromXml = TablesDocument.Factory.parse(xmlStream);

            if ((null != tablesDocMetaData) && (null != tablesDocFromXml))
            {
                compareTableDocuments(tablesDocMetaData, tablesDocFromXml, bFull, bCaseSensitive, null, sbOut);
            }
        }
        catch (Exception e)
        {
            _log.log(Priority.ERROR, "Exception loading schema " + dbSchemaName, e);
            return "+++ ERROR: Exception " + e.getMessage();
        }
        finally
        {
            try
            {
                if (null != xmlStream) xmlStream.close();
            }
            catch (Exception x)
            {
            }
        }

        return (0 == sbOut.length() ? null : sbOut.toString());
    }

    public static void compareTableDocuments(TablesDocument dbTablesDoc,
                                               TablesDocument xmlTablesDoc,
                                               boolean bFull,
                                               boolean bCaseSensitive,
                                               TablesDocument mergedTablesDoc,
                                               StringBuilder sbOut)
    {
        boolean merge = (null != mergedTablesDoc);
        boolean bCopyTargetNode;
        TableType[] dbTables;
        TableType[] xmlTables;
        TableType mt = null;
        ColumnType mc = null;
        String xmlTableName;
        String xmlTableType;
        String dbTableName;
        String dbColName;
        String xmlColName;
        int idt;
        int idc;
        ColumnType[] dbCols;
        ColumnType[] xmlCols;
        SortedMap<String, Integer> mXmlTableOrdinals;
        SortedMap<String, Integer> mXmlColOrdinals;
        SortedMap<String, Integer> mDbTableOrdinals;
        SortedMap<String, Integer> mDbColOrdinals;

        try
        {
            dbTables = dbTablesDoc.getTables().getTableArray();
            xmlTables = xmlTablesDoc.getTables().getTableArray();

            mXmlTableOrdinals = new TreeMap<String, Integer>();
            for (int i = 0; i < xmlTables.length; i++)
            {
                xmlTableName = xmlTables[i].getTableName();
                if (mXmlTableOrdinals.containsKey(xmlTableName.toLowerCase()))
                    sbOut.append("ERROR: TableName ").append(xmlTableName).append(" duplicated in Xml.<br>");
                else
                    mXmlTableOrdinals.put(xmlTableName.toLowerCase(), new Integer(i));
            }

            mDbTableOrdinals = new TreeMap<String, Integer>();
            for (int i = 0; i < dbTables.length; i++)
            {
                dbTableName = dbTables[i].getTableName();
                mDbTableOrdinals.put(dbTableName.toLowerCase(), new Integer(i));
            }

            for (TableType xmlTable : xmlTables)
            {
                xmlTableName = xmlTable.getTableName();
                xmlTableType = xmlTable.getTableDbType();

                if (!(mDbTableOrdinals.containsKey(xmlTableName.toLowerCase())))
                {
                    if (!xmlTableType.equals("NOT_IN_DB"))
                        sbOut.append("<br>ERROR: TableName ").append(xmlTableName).append(" type ").append(xmlTableType).append(" found in Xml but not in database.<br>");
                    else
                    {
                        if (merge)
                        {
                            //copy XML-only node to end of table array
                            int size = mergedTablesDoc.getTables().getTableArray().length;
                            mergedTablesDoc.getTables().addNewTable();
                            mt = (TableType) xmlTable.copy();
                            mergedTablesDoc.getTables().setTableArray(size, mt);
                        }
                    }
                    continue;
                }

                idt = mDbTableOrdinals.get(xmlTableName.toLowerCase()).intValue();

                if (merge)
                {
                    mt = mergedTablesDoc.getTables().addNewTable();
                    mt.addNewColumns();
                }

                TableType tt = dbTables[idt];
                compareStringProperty(tt.getTableName(), xmlTable.getTableName(), "TableName", sbOut, bCaseSensitive, true);
                if (merge)
                    mt.setTableName(xmlTable.getTableName());

                compareStringProperty(tt.getTableDbType(), xmlTable.getTableDbType(), "TableDbType", sbOut, true, true);
                if (merge)
                    mt.setTableDbType(xmlTable.getTableDbType());

                if (bFull)
                {
                    bCopyTargetNode = compareStringProperty(tt.getTableTitle(), xmlTable.getTableTitle(), "TableTitle", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTableTitle(xmlTable.getTableTitle());

                    bCopyTargetNode = compareStringProperty(tt.getTableGroup(), xmlTable.getTableGroup(), "TableGroup", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTableGroup(xmlTable.getTableGroup());

                    bCopyTargetNode = compareStringProperty(tt.getDbTableName(), xmlTable.getDbTableName(), "DbTableName", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setDbTableName(xmlTable.getDbTableName());

                    bCopyTargetNode = compareStringProperty(tt.getPkColumnName(), xmlTable.getPkColumnName(), "PkColumnName", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setPkColumnName(xmlTable.getPkColumnName());

                    bCopyTargetNode = compareStringProperty(tt.getVersionColumnName(), xmlTable.getVersionColumnName(), "VersionColumnName", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setVersionColumnName(xmlTable.getVersionColumnName());

                    bCopyTargetNode = compareStringProperty(tt.getTableUrl(), xmlTable.getTableUrl(), "TableUrl", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTableUrl(xmlTable.getTableUrl());

                    bCopyTargetNode = compareStringProperty(tt.getNextStep(), xmlTable.getNextStep(), "NextStep", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setNextStep(xmlTable.getNextStep());

                    bCopyTargetNode = compareStringProperty(tt.getTitleColumn(), xmlTable.getTitleColumn(), "TitleColumn", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTitleColumn(xmlTable.getTitleColumn());


                    bCopyTargetNode = compareBoolProperty((tt.isSetManageTableAllowed() ? tt.getManageTableAllowed() : null),
                            (xmlTable.isSetManageTableAllowed() ? xmlTable.getManageTableAllowed() : null),
                            "ManageTableAllowed", sbOut);
                    if (merge && bCopyTargetNode)
                        mt.setManageTableAllowed(xmlTable.getManageTableAllowed());

                }

                dbCols = tt.getColumns().getColumnArray();
                xmlCols = xmlTable.getColumns().getColumnArray();

                mXmlColOrdinals = new TreeMap<String, Integer>();
                mDbColOrdinals = new TreeMap<String, Integer>();

                for (int m = 0; m < xmlCols.length; m++)
                {
                    xmlColName = xmlCols[m].getColumnName();
                    if (mXmlColOrdinals.containsKey(xmlColName.toLowerCase()))
                        sbOut.append("ERROR: Table ").append(xmlTable.getTableName()).append(" Column ").append(xmlColName).append(" duplicated in Xml.<br>");
                    else
                        mXmlColOrdinals.put(xmlColName.toLowerCase(), new Integer(m));
                }

                for (int m = 0; m < dbCols.length; m++)
                {
                    dbColName = dbCols[m].getColumnName();
                    mDbColOrdinals.put(dbColName.toLowerCase(), new Integer(m));
                }

                for (ColumnType xmlCol : xmlCols)
                {
                    xmlColName = xmlCol.getColumnName();

                    if (!mDbColOrdinals.containsKey(xmlColName.toLowerCase()))
                    {
                        sbOut.append("ERROR: Table ").append(xmlTable.getTableName()).append(" Column ").append(xmlColName).append(" found in Xml but not in database.<br>");
                        continue;
                    }
                    idc = mDbColOrdinals.get(xmlColName.toLowerCase()).intValue();
                    ColumnType columnType = dbCols[idc];

                    compareStringProperty(columnType.getColumnName(), xmlCol.getColumnName(), "ColumnName", sbOut, bCaseSensitive, true);

                    if (merge)
                    {
                        mc = mt.getColumns().addNewColumn();
                        mc.setColumnName(xmlCol.getColumnName());
                    }

                    if (bFull)
                    {
                        StringBuilder sbTmp = new StringBuilder();

                        bCopyTargetNode = compareStringProperty(columnType.getDatatype(), xmlCol.getDatatype(), "Datatype", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDatatype(xmlCol.getDatatype());

                        bCopyTargetNode = compareStringProperty(columnType.getColumnTitle(), xmlCol.getColumnTitle(), "ColumnTitle", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setColumnTitle(xmlCol.getColumnTitle());

                        bCopyTargetNode = compareStringProperty(columnType.getDefaultValue(), xmlCol.getDefaultValue(), "DefaultValue", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDefaultValue(xmlCol.getDefaultValue());

                        bCopyTargetNode = compareStringProperty(columnType.getAutoFillValue(), xmlCol.getAutoFillValue(), "AutoFillValue", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setAutoFillValue(xmlCol.getAutoFillValue());

                        bCopyTargetNode = compareStringProperty(columnType.getInputType(), xmlCol.getInputType(), "InputType", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setInputType(xmlCol.getInputType());

                        bCopyTargetNode = compareStringProperty(columnType.getOnChange(), xmlCol.getOnChange(), "OnChange", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setOnChange(xmlCol.getOnChange());

                        bCopyTargetNode = compareStringProperty(columnType.getDescription(), xmlCol.getDescription(), "ColumnText", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDescription(xmlCol.getDescription());

                        bCopyTargetNode = compareStringProperty(columnType.getOptionlistQuery(), xmlCol.getOptionlistQuery(), "OptionlistQuery", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setOptionlistQuery(xmlCol.getOptionlistQuery());

                        bCopyTargetNode = compareStringProperty(columnType.getUrl(), xmlCol.getUrl(), "Url", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setUrl(xmlCol.getUrl());

                        bCopyTargetNode = compareStringProperty(columnType.getFormatString(), xmlCol.getFormatString(), "FormatString", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setFormatString(xmlCol.getFormatString());

                        bCopyTargetNode = compareStringProperty(columnType.getTextAlign(), xmlCol.getTextAlign(), "TextAlign", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setTextAlign(xmlCol.getTextAlign());

                        bCopyTargetNode = compareStringProperty(columnType.getPropertyURI(), xmlCol.getPropertyURI(), "PropertyURI", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setPropertyURI(xmlCol.getPropertyURI());

                        bCopyTargetNode = compareStringProperty(columnType.getDisplayWidth(), xmlCol.getDisplayWidth(), "DisplayWidth", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDisplayWidth(xmlCol.getDisplayWidth());

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetScale() ? columnType.getScale() : null),
                                (xmlCol.isSetScale() ? xmlCol.getScale() : null),
                                "Scale", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setScale(xmlCol.getScale());

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetPrecision() ? columnType.getPrecision() : null),
                                (xmlCol.isSetPrecision() ? xmlCol.getPrecision() : null),
                                "Precision", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setPrecision(xmlCol.getPrecision());

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetInputLength() ? columnType.getInputLength() : null),
                                (xmlCol.isSetInputLength() ? xmlCol.getInputLength() : null),
                                "InputLength", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setInputLength(xmlCol.getInputLength());

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetInputRows() ? columnType.getInputRows() : null),
                                (xmlCol.isSetInputRows() ? xmlCol.getInputRows() : null),
                                "InputRows", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setInputRows(xmlCol.getInputRows());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetNullable() ? columnType.getNullable() : null),
                                (xmlCol.isSetNullable() ? xmlCol.getNullable() : null),
                                "Nullable", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setNullable(xmlCol.getNullable());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsAutoInc() ? columnType.getIsAutoInc() : null),
                                (xmlCol.isSetIsAutoInc() ? xmlCol.getIsAutoInc() : null),
                                "IsAutoInc", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsAutoInc(xmlCol.getIsAutoInc());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsDisplayColumn() ? columnType.getIsDisplayColumn() : null),
                                (xmlCol.isSetIsDisplayColumn() ? xmlCol.getIsDisplayColumn() : null),
                                "IsDisplayColumn", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsDisplayColumn(xmlCol.getIsDisplayColumn());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsReadOnly() ? columnType.getIsReadOnly() : null),
                                (xmlCol.isSetIsReadOnly() ? xmlCol.getIsReadOnly() : null),
                                "IsReadOnly", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsReadOnly(xmlCol.getIsReadOnly());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsUserEditable() ? columnType.getIsUserEditable() : null),
                                (xmlCol.isSetIsUserEditable() ? xmlCol.getIsUserEditable() : null),
                                "IsUserEditable", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsUserEditable(xmlCol.getIsUserEditable());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsKeyField() ? columnType.getIsKeyField() : null),
                                (xmlCol.isSetIsKeyField() ? xmlCol.getIsKeyField() : null),
                                "IsKeyField", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsKeyField(xmlCol.getIsKeyField());

                        // check and merge FK property
                        ColumnType.Fk fk;
                        boolean declFk = false;
                        if ((null != columnType.getFk()))
                            declFk = true;

                        if (null != xmlCol.getFk())
                        {
                            compareStringProperty((declFk ? columnType.getFk().getFkColumnName() : null)
                                    , xmlCol.getFk().getFkColumnName()
                                    , "FkColumnName", sbTmp, bCaseSensitive);

                            compareStringProperty((declFk ? columnType.getFk().getFkTable() : null)
                                    , xmlCol.getFk().getFkTable()
                                    , "FkTable", sbTmp, bCaseSensitive);

                            bCopyTargetNode = compareStringProperty((declFk ? columnType.getFk().getFkDbSchema() : null)
                                    , xmlCol.getFk().getFkDbSchema()
                                    , "FkDbSchema", sbTmp, bCaseSensitive);

                            // if FK is declared in xml use it as a whole node, don't mix and match.
                            if (merge)
                            {
                                fk = mc.addNewFk();
                                fk.setFkColumnName(xmlCol.getFk().getFkColumnName());
                                fk.setFkTable(xmlCol.getFk().getFkTable());
                                if (bCopyTargetNode)
                                    fk.setFkDbSchema(xmlCol.getFk().getFkDbSchema());
                            }

                        }

                        // check and merge Ontology (assumed not from metadata)
                        if (null != columnType.getOntology())
                        {
                            sbTmp.append("ERROR: Table ").append(tt.getTableName()).append(" Unexpected Ontology node in dbTablesDoc xmldoc from metadata, ColName ").append(columnType).append("<br>");
                            continue;
                        }

                        if (merge && (null != xmlCol.getOntology()))
                        {
                            OntologyType o = mc.addNewOntology();
                            o.setStringValue(xmlCol.getOntology().getStringValue());
                            o.setRefId(xmlCol.getOntology().getRefId());
                            if (null != xmlCol.getOntology().getSource())
                                o.setSource(xmlCol.getOntology().getSource());
                        }

                        if (sbTmp.length() > 0)
                        {
                            sbOut.append("<br>Table ").append(tt.getTableName()).append(" Column ").append(columnType.getColumnName()).append(" errors and warnings<br>");
                            sbOut.append(sbTmp);
                        }
                    }

                    mDbColOrdinals.remove(xmlColName.toLowerCase());
                }
                // now check any extra columns in the db
                for (String dbCol : mDbColOrdinals.keySet())
                {
                    idc = mDbColOrdinals.get(dbCol).intValue();
                    sbOut.append("ERROR: Table ").append(tt.getTableName()).append(" Column ").append(dbCol).append(" missing from Xml.<br>");
                    if (merge)
                    {
                        mc = mt.getColumns().addNewColumn();
                        mc.setColumnName(tt.getColumns().getColumnArray(idc).getColumnName());
                    }
                }

                mDbTableOrdinals.remove(xmlTableName.toLowerCase());
            }
            // now check any extra tabledefs in the db
            for (String dbTab : mDbTableOrdinals.keySet())
            {
                if (dbTab.startsWith("_"))
                    continue;
                idt = mDbTableOrdinals.get(dbTab).intValue();
                TableType tt = dbTables[idt];
                sbOut.append("ERROR: Table ").append(dbTab).append(" missing from Xml.<br>");
                if (merge)
                {
                    //copy db node to end of table array
                    mt = mergedTablesDoc.getTables().addNewTable();
                    mt.setTableName(tt.getTableName());
                    mt.setTableDbType(tt.getTableDbType());
                    mt.addNewColumns();
                    for (int i=0;i<tt.getColumns().getColumnArray().length; i++)
                    {
                        mc=mt.getColumns().addNewColumn();
                        mc.setColumnName(tt.getColumns().getColumnArray(i).getColumnName());
                    }
                }
            }

        }
        catch (Exception e)
        {
            sbOut.append("ERROR: Exception in compare: ").append(e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean compareStringProperty(String refProp, String targetProp, String propName, StringBuilder sbOut, boolean bCaseSensitive)
    {
        return compareStringProperty(refProp, targetProp, propName, sbOut, bCaseSensitive, false);
    }

    private static boolean compareStringProperty(String refProp, String targetProp, String propName, StringBuilder sbOut, boolean bCaseSensitive, boolean reqd)
    {
        boolean bMatch;
        if (null == refProp)
        {
            return null != targetProp;
        }
        if (null == targetProp)
        {
            if (reqd)
                sbOut.append("ERROR: property ").append(propName).append(" value ").append(refProp).append("not found in XML:<br>");
            return false;
        }

        bMatch = refProp.equalsIgnoreCase(targetProp);
        if (bMatch)
        {
            if ((bCaseSensitive) && (!(bMatch = refProp.equals(targetProp))))
                sbOut.append("WARNING: (case mismatch) ");
        }
        else
            sbOut.append("WARNING   ");

        if (!bMatch)
        {
            sbOut.append("property ").append(propName).append(" value ").append(refProp).append(" doesn't match xml: ").append(targetProp).append(" ; Xml value used<br>");
            // mismatch who wins?  assume xmlDoc wins
            return true;
        }
        else if (!reqd)
            sbOut.append("WARNING: property ").append(propName).append(" value ").append(refProp).append(" unnecessary in xml:<br>");

        return false;
    }

    private static boolean compareIntegerProperty(Integer refProp, Integer targetProp, String propName, StringBuilder sbOut)
    {
        if (null == refProp)
        {
            return null != targetProp;
        }
        if (null == targetProp)
            return false;

        if (refProp.equals(targetProp))
        {
            sbOut.append("WARNING: property ").append(propName).append(" value ").append(refProp).append(" unnecessary in  xml:<br>");
            return false;
        }
        sbOut.append("WARNING: property ").append(propName).append(" value ").append(refProp).append(" doesn't match xml: ").append(targetProp).append(" ; Xml value used<br>");
        return true;
    }

    private static boolean compareBoolProperty(Boolean refProp, Boolean targetProp, String propName, StringBuilder sbOut)
    {
        if (null == refProp)
        {
            return null != targetProp;
        }
        if (null == targetProp)
            return false;       

        if (refProp.equals(targetProp))
        {
            sbOut.append("WARNING: property ").append(propName).append(" value ").append(refProp).append(" unnecessary in xml.<br>");
            return false;
        }
        sbOut.append("WARNING: property ").append(propName).append(" value ").append(refProp).append(" doesn't match xml: ").append(targetProp).append("  ; Xml value used<br>");
        return true;
    }
}
