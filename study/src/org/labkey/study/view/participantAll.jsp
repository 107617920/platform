<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
%>
<%@ page import="org.apache.commons.beanutils.ConvertUtils"%>
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashSet"%>
<%@ page import="org.labkey.api.collections.MultiValueMap"%>
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.data.DbSchema"%>
<%@ page import="org.labkey.api.data.Results"%>
<%@ page import="org.labkey.api.data.SQLFragment" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.Table" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.exp.LsidManager" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.ChartDesignerBean" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.ResultSetUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.StudySchema" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.QCState" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.reports.StudyChartQueryReport" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.TreeSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = HttpView.currentContext();
    org.labkey.study.query.StudyQuerySchema querySchema = (org.labkey.study.query.StudyQuerySchema)QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
    DbSchema dbSchema = querySchema.getDbSchema();
    String contextPath = request.getContextPath();
    JspView<StudyManager.ParticipantViewConfig> me = (JspView<StudyManager.ParticipantViewConfig>) HttpView.currentView();
    StudyManager.ParticipantViewConfig bean = me.getModelBean();

    ChartDesignerBean chartBean = new ChartDesignerBean();

    chartBean.setReportType(StudyChartQueryReport.TYPE);
    chartBean.setSchemaName(querySchema.getSchemaName());
    String currentUrl = bean.getRedirectUrl();
    if (currentUrl == null)
        currentUrl = context.getActionURL().getLocalURIString();

    ActionURL url = ReportUtil.getChartDesignerURL(context, chartBean);
    url.setAction(ReportsController.DesignChartAction.class);
    url.addParameter("returnUrl", currentUrl);
    url.addParameter("isParticipantChart", "true");
    url.addParameter("participantId", bean.getParticipantId());

    StudyManager manager = StudyManager.getInstance();
    StudyImpl study = manager.getStudy(context.getContainer());

    User user = (User) request.getUserPrincipal();
    DataSetDefinition[] allDatasets = manager.getDataSetDefinitions(study);
    ArrayList<DataSetDefinition> datasets = new ArrayList<DataSetDefinition>(allDatasets.length);
    for (DataSetDefinition def : allDatasets)
    {
        if (!def.canRead(user) || null == def.getStorageTableInfo() || def.isDemographicData())
                continue;
        datasets.add(def);
    }

    //
    // Create a resultset per dataset
    // A multiple SELECT query would make sense here, but it doesn't seem to work with our Postgres driver.
    //
    // First select from the StudyData UNION table to see which datasets we need to query individually
    // populate a VisitMultiMap while we're at it
    //

    ResultSet rs;

    Map<Pair<String,Double>,Integer> visitRowIdMap = new HashMap<Pair<String,Double>,Integer>();
    Map<Double, Date> ptidVisitDates = new TreeMap<Double, Date>();
    rs = Table.executeQuery(dbSchema,
            "SELECT VisitRowId, ParticipantId, SequenceNum, VisitDate\n" +
            "FROM " + StudySchema.getInstance().getTableInfoParticipantVisit() + "\n" +
            "WHERE Container = ?",
            new Object[]{study.getContainer()});
    while (rs.next())
    {
        int visitRowId = rs.getInt(1);
        String ptid = rs.getString(2);
        double sequenceNum = rs.getDouble(3);
        Date visitDate = rs.getDate(4);
        visitRowIdMap.put(new Pair(ptid,sequenceNum), visitRowId);
        if (bean.getParticipantId().equals(ptid))
            ptidVisitDates.put(sequenceNum, visitDate);
    }
    rs.close();

    VisitMultiMap visitSequenceMap = new VisitMultiMap();
    Map<Double, Integer> countKeysForSequence = new HashMap<Double, Integer>();
    Set<Integer> datasetSet = new HashSet<Integer>();
    SimpleFilter filter = new SimpleFilter(study.getSubjectColumnName(), bean.getParticipantId());
    Sort sort = new Sort("SequenceNum");
    SQLFragment f = new SQLFragment();
    f.append("SELECT ParticipantId, SequenceNum, DatasetId, COUNT(*) AS _RowCount FROM ");
    f.append(StudySchema.getInstance().getTableInfoStudyDataFiltered(study, datasets, user).getFromSQL("SD"));
    f.append("\n");
    f.append("GROUP BY ParticipantId, SequenceNum, DatasetId");
    rs = Table.executeQuery(dbSchema, f);
    while (rs.next())
    {
        String ptid = rs.getString(1);
        double s = rs.getDouble(2);
        Double sequenceNum = rs.wasNull() ? null : s;
        int datasetId = rs.getInt(3);
        int rowCount = ((Number)rs.getObject(4)).intValue();
        Integer visitRowId = visitRowIdMap.get(new Pair(ptid, sequenceNum));
        if (null != visitRowId && null != sequenceNum)
            visitSequenceMap.put(visitRowId, sequenceNum);
        datasetSet.add(datasetId);
        Integer count = countKeysForSequence.get(sequenceNum);
        if (null == count || count < rowCount)
            countKeysForSequence.put(sequenceNum, rowCount);
    }
    rs.close();


    // Now we have a list of datasets with 1 or more rows and
    // a visitMap to help with layout
    //
    // get the data


    VisitImpl[] allVisits = manager.getVisits(study, Visit.Order.DISPLAY);
    ArrayList<VisitImpl> visits = new ArrayList<VisitImpl>(visitSequenceMap.size());
    for (VisitImpl visit : allVisits)
    {
        if (visitSequenceMap.containsKey(visit.getRowId()))
            visits.add(visit);
    }

    Map<Integer, String> expandedMap = StudyController.getExpandedState(context, bean.getDatasetId());
    boolean updateAccess = study.getContainer().hasPermission(user, UpdatePermission.class);

    int totalSeqKeyCount = 0;
%>

<table class="labkey-data-region">

    <tr class="labkey-alternate-row">
        <td class="labkey-participant-view-header"><img alt="" width=180 height=1 src="<%=contextPath%>/_.gif"></td>
        <td class="labkey-participant-view-header"><img alt="" width=20 height=1 src="<%=contextPath%>/_.gif"></td><%

            for (VisitImpl visit : visits)
            {
                int seqKeyCount = 0;
                for (Double seqNum : visitSequenceMap.get(visit.getRowId()))
                {
                    Integer c = countKeysForSequence.get(seqNum);
                    seqKeyCount += c == null ? 1 : c;
                }
                totalSeqKeyCount += seqKeyCount;
                %><td class="labkey-participant-view-header" colspan="<%=seqKeyCount%>"><%= h(visit.getDisplayString()) %></td><%
            }
        %>
    </tr>

    <tr class="labkey-alternate-row">
        <td class="labkey-participant-view-header"><img alt="" width=1 height=1 src="<%=contextPath%>/_.gif"></td>
        <td class="labkey-participant-view-header"><img alt="" width=1 height=1 src="<%=contextPath%>/_.gif"></td><%

        for (VisitImpl visit : visits)
        {
            Collection<Double> sequences = visitSequenceMap.get(visit.getRowId());
            for (Double seqNum : sequences)
            {
                Date date = ptidVisitDates.get(seqNum);
                Integer keyCount = countKeysForSequence.get(seqNum);
                if (null == keyCount)
                    keyCount = 1;
                %><td class="labkey-participant-view-header" colspan="<%=keyCount%>"><%= null==date ? "&nbsp;" : ConvertUtils.convert(date) %></td><%
            }
        }
        %>
    </tr>

    <%
        response.flushBuffer();

        for (DataSetDefinition dataset : datasets)
        {
            // Do not display demographic data here. That goes in a separate web part,
            // the participant characteristics
            if (dataset.isDemographicData())
                continue;

            String typeURI = dataset.getTypeURI();
            if (null == typeURI)
                continue;

            int datasetId = dataset.getDataSetId();
            boolean expanded = false;
            if ("expand".equalsIgnoreCase(expandedMap.get(datasetId)))
                expanded = true;

            if (!dataset.canRead(user))
            {
                %><tr class="labkey-header"><th nowrap align="left" class="labkey-expandable-row-header"><%=h(dataset.getDisplayString())%></th><td colspan="<%=totalSeqKeyCount+1%>" nowrap align="left" class="labkey-expandable-row-header">(no access)</td></tr><%
                continue;
            }

            // get the data for this dataset and group rows by SequenceNum/Key
            TableInfo table = new org.labkey.study.query.DataSetTable(querySchema, dataset);
            Map<Double,Map<Object,Map>> seqKeyRowMap = new HashMap();
            String keyCol = dataset.getKeyPropertyName();

            if (!datasetSet.contains(datasetId))
                continue;
            Map<FieldKey,ColumnInfo> allColumns = getQueryColumns(table);
            ColumnInfo sourceLsidColumn = allColumns.get(new FieldKey(null, "sourceLsid"));
            Results dsResults = Table.select(table, allColumns.values(), filter, sort);
            int rowCount = dsResults.getSize();
            while (dsResults.next())
            {
                double sequenceNum = dsResults.getDouble("SequenceNum");
                Object key = null==keyCol ? "" : dsResults.getObject(keyCol);

                Map keyMap = seqKeyRowMap.get(sequenceNum);
                if (null == keyMap)
                    seqKeyRowMap.put(sequenceNum, keyMap = new HashMap());

                keyMap.put(key, dsResults.getRowMap());
            }
            ResultSetUtil.close(dsResults);
            if (rowCount == 0)
                continue;

            %>
            <tr class="labkey-header">
            <th nowrap align="left" class="labkey-expandable-row-header"><a title="Click to expand" href="<%=new ActionURL(StudyController.ExpandStateNotifyAction.class, study.getContainer()).addParameter("datasetId", Integer.toString(datasetId)).addParameter("id", Integer.toString(bean.getDatasetId()))%>" onclick="return collapseExpand(this, true);"><%=h(dataset.getDisplayString())%></a><%
            if (null != StringUtils.trimToNull(dataset.getDescription()))
            {
                %><%=PageFlowUtil.helpPopup(dataset.getDisplayString(), dataset.getDescription())%><%
            }
            %></th>
                <td class="labkey-expandable-row-header" style="text-align:right;"><%=rowCount%></td>
                <td class="labkey-expandable-row-header" colspan="<%=totalSeqKeyCount%>">&nbsp;</td>
            </tr>
            <%

            for (Report report : ReportService.get().getReports(user, study.getContainer(), Integer.toString(datasetId)))
            {
                if (updateAccess)
                {
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td><a href="<%=new ActionURL(ReportsController.DeleteReportAction.class, study.getContainer()).addParameter(ReportDescriptor.Prop.redirectUrl.name(), currentUrl).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId().toString())%>">[remove]</a></td>
                </tr>
                <%
                }
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td colspan="<%=totalSeqKeyCount%>"><img src="<%=ReportUtil.getPlotChartURL(context, report).addParameter("participantId", bean.getParticipantId()).toString()%>"></td>
                </tr>
                <%
            }

            if (updateAccess)
            {
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td colspan="<%=totalSeqKeyCount+1%>" class="labkey-alternate-row"><%=textLink("add chart",url.replaceParameter("queryName", dataset.getLabel()).replaceParameter("datasetId", String.valueOf(datasetId)))%></td>
                </tr>
                <%
            }
            int row = 0;
            String className = row % 2 == 0 ? "labkey-alternate-row" : "labkey-row";

            // display details link(s) only if we have a source lsid in at least one of the rows
            boolean hasSourceLsid = false;

            if (StudyManager.getInstance().showQCStates(context.getContainer()))
            {
                row++;
                %>
                <tr style="<%=expanded ? "" : "display:none"%>"><td class="<%= className %>" align="left" nowrap>QC State</td><td class="<%= className %>">&nbsp;</td>
                <%
                for (VisitImpl visit : visits)
                {
                    for (double seq : visitSequenceMap.get(visit.getRowId()))
                    {
                        Map keyMap = seqKeyRowMap.get(seq);
                        int countTD = 0;
                        if (null != keyMap)
                        {
                            for (Map.Entry<Object,Map> e : (Set<Map.Entry<Object,Map>>)keyMap.entrySet())
                            {
                                Integer id = (Integer)e.getValue().get("QCState");
                                QCState state = getQCState(study, id);
                                boolean hasDescription = state != null && state.getDescription() != null && state.getDescription().length() > 0;
                                %>
                                    <td class="<%=className%>">
                                        <%= state == null ? "Unspecified" : h(state.getLabel())%><%= hasDescription ? helpPopup("QC State: " + state.getLabel(), state.getDescription()) : "" %>
                                    </td>
                                <%
                                countTD++;
                            }
                        }
                        // do we need to pad?
                        int maxTD = countKeysForSequence.get(seq) == null ? 1 : countKeysForSequence.get(seq);
                        if (countTD < maxTD)
                        {
                            %><td class="<%=className%>" colspan="<%=maxTD-countTD%>">&nbsp;</td><%
                        }
                    }
                }
                %>
                </tr>
                <%
            }

                    // sort the properties so they appear in the same order as the grid view
//            PropertyDescriptor[] pds = sortProperties(StudyController.getParticipantPropsFromCache(HttpView.getRootContext(), typeURI), dataset, HttpView.getRootContext());
            ColumnInfo[] displayColumns = sortColumns(allColumns.values(), dataset, HttpView.getRootContext());

            for (ColumnInfo col : displayColumns)
            {
                if (col == null) continue;
                row++;
                className = row % 2 == 0 ? "labkey-alternate-row" : "labkey-row";
                String labelName = StringUtils.defaultString(col.getLabel(), col.getName());;
                %>
                <tr class="<%=className%>" style="<%=expanded ? "" : "display:none"%>"><td align="left" nowrap><%=h(labelName)%></td><td>&nbsp;</td><%
                for (VisitImpl visit : visits)
                {
                    for (double seq : visitSequenceMap.get(visit.getRowId()))
                    {
                        Map keyMap = seqKeyRowMap.get(seq);
                        int countTD = 0;
                        if (null != keyMap)
                        {
                            for (Map.Entry<Object,Map> e : (Set<Map.Entry<Object,Map>>)keyMap.entrySet())
                            {
                                Map propMap = e.getValue();
                                if (sourceLsidColumn.getValue(propMap) != null)
                                    hasSourceLsid = true;
                                Object value = col.getValue(propMap);
                                %><td><%= (null == value ? "&nbsp;" : h(ConvertUtils.convert(value), true))%></td><%
                                countTD++;
                            }
                        }
                        // do we need to pad?
                        int maxTD = countKeysForSequence.get(seq) == null ? 1 : countKeysForSequence.get(seq);
                        if (countTD < maxTD)
                        {
                            %><td colspan="<%=maxTD-countTD%>">&nbsp;</td><%
                        }
                    }
                }
            %></tr><%
            }
            if (hasSourceLsid) // Need to display a details link
            {
                row++;
                className = row % 2 == 0 ? "labkey-alternate-row" : "labkey-row";
                %>
                <tr class="<%=className%>" style="<%=expanded ? "" : "display:none"%>"><td align="left" nowrap>Details</td><td>&nbsp;</td><%
                for (VisitImpl visit : visits)
                {
                    for (double seq : visitSequenceMap.get(visit.getRowId()))
                    {
                        Map keyMap = seqKeyRowMap.get(seq);
                        int countTD = 0;
                        if (null != keyMap)
                        {
                            for (Map.Entry<Object,Map> e : (Set<Map.Entry<Object,Map>>)keyMap.entrySet())
                            {
                                String link = "&nbsp;";
                                Map propMap = e.getValue();
                                String sourceLsid = (String)sourceLsidColumn.getValue(propMap);

                                if (sourceLsid != null && LsidManager.get().hasPermission(sourceLsid, getViewContext().getUser(), ReadPermission.class))
                                {
                                    ActionURL sourceURL = new ActionURL(StudyController.DatasetItemDetailsAction.class, context.getContainer());
                                    sourceURL.addParameter("sourceLsid", sourceLsid);
                                    link = "[<a href=\"" + sourceURL.getLocalURIString() + "\">details</a>]";
                                }
                                %><td><%= link%></td><%
                                countTD++;
                            }
                        }
                        // do we need to pad?
                        int maxTD = countKeysForSequence.get(seq) == null ? 1 : countKeysForSequence.get(seq);
                        if (countTD < maxTD)
                        {
                            %><td colspan="<%=maxTD-countTD%>">&nbsp;</td><%
                        }
                    }
                }
            %></tr><%
            }
        }
    %>
</table>
<%!

Map<Integer,QCState> qcstates = null;

QCState getQCState(Study study, Integer id)
{
    if (null == qcstates)
    {
        QCState[] states = StudyManager.getInstance().getQCStates(study.getContainer());
        qcstates = new HashMap<Integer,QCState>(2*states.length);
        for (QCState state : states)
            qcstates.put(state.getRowId(), state);
    }
    return qcstates.get(id);
}


Map<FieldKey,ColumnInfo> getQueryColumns(TableInfo t)
{
    List<ColumnInfo> cols = t.getColumns();
    // Use all of the columns in the default view of the dataset, which might include columns from the assay side if
    // the data was linked
    Set<FieldKey> keys = new java.util.LinkedHashSet<FieldKey>(t.getDefaultVisibleColumns());
    for (ColumnInfo c : cols)
        keys.add(c.getFieldKey());
    return QueryService.get().getColumns(t, keys);
}


static CaseInsensitiveHashSet skipColumns = new CaseInsensitiveHashSet(
        "lsid","sourcelsid","sequencenum","qcstate","participantid",
        "visitrowid","dataset","participantsequencekey","created","modified","createdby","modifiedby","participantvisit");


ColumnInfo[] sortColumns(Collection<ColumnInfo> cols, DataSet dsd, ViewContext context)
{
    final Map<String, Integer> sortMap = StudyController.getSortedColumnList(context, dsd);
    if (sortMap != null && !sortMap.isEmpty())
    {
        ColumnInfo[] ret = new ColumnInfo[sortMap.size()];
        for (ColumnInfo col : cols)
        {
            if (sortMap.containsKey(col.getName()))
                ret[sortMap.get(col.getName())] = col;
        }
        List<ColumnInfo> results = new ArrayList<ColumnInfo>();
        for (ColumnInfo col : ret)
            if (col != null)
                results.add(col);
        return results.toArray(new ColumnInfo[results.size()]);
    }

    // default list
    String subjectcol = StudyService.get().getSubjectColumnName(context.getContainer());
    List<ColumnInfo> ret = new ArrayList<ColumnInfo>(cols.size());
    for (ColumnInfo col : cols)
    {
        if (skipColumns.contains(col.getName()))
            continue;
        if (subjectcol.equalsIgnoreCase(col.getName()))
            continue;
        ret.add(col);
    }
    return ret.toArray(new ColumnInfo[ret.size()]);
}


public static class VisitMultiMap extends MultiValueMap<Integer, Double>
{
    public VisitMultiMap()
    {
        super(new TreeMap<Integer, Collection<Double>>());
    }

    @Override
    protected Collection<Double> createValueCollection()
    {
        return new TreeSet<Double>();
    }
}

%>

