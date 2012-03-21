package org.labkey.study.samples.report.request;

import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.study.Site;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.sql.SQLException;

/**
 * User: brittp
 * Created: Jan 24, 2008 1:38:40 PM
 */
public class RequestSiteReportFactory extends BaseRequestReportFactory
{
    private Integer _siteId;

    public Integer getSiteId()
    {
        return _siteId;
    }

    public void setSiteId(Integer siteId)
    {
        _siteId = siteId;
    }

    public boolean allowsAvailabilityFilter()
    {
        return false;
    }

    public String getLabel()
    {
        try
        {
            Site site = _siteId != null ? StudyManager.getInstance().getSite(getContainer(), _siteId) : null;
            return "By Requesting Location" + (site != null ? ": " + site.getLabel() : "");
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        try
        {
            SiteImpl[] sites;
            if (getSiteId() != null)
                sites = new SiteImpl[] { StudyManager.getInstance().getSite(getContainer(), getSiteId()) };
            else
                sites = SampleManager.getInstance().getSitesWithRequests(getContainer());
            if (sites == null)
                return Collections.emptyList();
            List<SpecimenVisitReport> reports = new ArrayList<SpecimenVisitReport>();
            VisitImpl[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
            for (SiteImpl site : sites)
            {
                SimpleFilter filter = new SimpleFilter();
                Object[] params;
                if (isCompletedRequestsOnly())
                    params = new Object[] { Boolean.TRUE, Boolean.TRUE, site.getRowId(), getContainer().getId()};
                else
                    params = new Object[] { Boolean.TRUE, site.getRowId(), getContainer().getId()};
                filter.addWhereClause("globaluniqueid IN\n" +
                        "(\n" +
                        "     SELECT specimenglobaluniqueid FROM study.samplerequestspecimen WHERE samplerequestid IN\n" +
                        "     (\n" +
                        "          SELECT r.rowid FROM study.SampleRequest r JOIN study.SampleRequestStatus rs ON\n" +
                        "           r.StatusId = rs.RowId\n" +
                        "           AND rs.SpecimensLocked = ?\n" +
                        (isCompletedRequestsOnly() ? "           AND rs.FinalState = ?\n" : "") +
                        "           WHERE destinationsiteid = ? AND r.container = ?\n" +
                        "     )\n" +
                        ")", params);
                addBaseFilters(filter);
                reports.add(new RequestSiteReport(site.getLabel(), filter, this, visits, site.getRowId(), isCompletedRequestsOnly()));
            }
            return reports;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.RequestSiteReportAction.class;
    }

    public List<Pair<String, String>> getAdditionalFormInputHtml()
    {
        try
        {
            StringBuilder builder = new StringBuilder();
            builder.append("<select name=\"siteId\">\n" +
                    "<option value=\"\">All Requesting Locations</option>\n");
            for (SiteImpl site : SampleManager.getInstance().getSitesWithRequests(getContainer()))
            {
                builder.append("<option value=\"").append(site.getRowId()).append("\"");
                if (_siteId != null && site.getRowId() == _siteId)
                    builder.append(" SELECTED");
                builder.append(">");
                builder.append(PageFlowUtil.filter(site.getLabel()));
                builder.append("</option>\n");
            }
            builder.append("</select>");
            List<Pair<String, String>> inputs = new ArrayList<Pair<String, String>>(super.getAdditionalFormInputHtml());
            inputs.add(new Pair<String, String>("Requesting location(s)", builder.toString()));
            return inputs;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }
}