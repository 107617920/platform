package org.labkey.api.reports.model;

import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.reports.report.ReportIdentifier;

import java.util.Date;

/**
* Created by IntelliJ IDEA.
* User: klum
* Date: Mar 16, 2012
*/
public class DataViewEditForm extends ReturnUrlForm
{
    ReportIdentifier _reportId;
    String _viewName;
    String _entityId;
    String _category;
    String _description;
    boolean _hidden;
    ViewInfo.DataType _dataType;
    int _author;
    ViewInfo.Status _status;
    Date _refreshDate;
    Date _modifiedDate;

    public String getViewName()
    {
        return _viewName;
    }

    public void setViewName(String viewName)
    {
        _viewName = viewName;
    }

    public Date getRefreshDate()
    {
        return _refreshDate;
    }

    public void setRefreshDate(Date refreshDate)
    {
        _refreshDate = refreshDate;
    }

    public Date getModifiedDate()
    {
        return _modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate)
    {
        _modifiedDate = modifiedDate;
    }

    public ReportIdentifier getReportId()
    {
        return _reportId;
    }

    public void setReportId(ReportIdentifier reportId)
    {
        _reportId = reportId;
    }

    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
    {
        _category = category;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public void setHidden(boolean hidden)
    {
        _hidden = hidden;
    }

    public ViewInfo.DataType getDataType()
    {
        return _dataType;
    }

    public void setDataType(ViewInfo.DataType dataType)
    {
        _dataType = dataType;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    public int getAuthor()
    {
        return _author;
    }

    public void setAuthor(int author)
    {
        _author = author;
    }

    public ViewInfo.Status getStatus()
    {
        return _status;
    }

    public void setStatus(ViewInfo.Status status)
    {
        _status = status;
    }
}
