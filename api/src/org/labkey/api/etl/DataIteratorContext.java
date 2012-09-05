package org.labkey.api.etl;

import org.labkey.api.query.BatchValidationException;

/**
 * User: matthewb
 * Date: 2012-08-27
 * Time: 5:58 PM
 */
public class DataIteratorContext
{
    final BatchValidationException _errors;
    boolean _forImport = false;
    boolean _failFast = true;
    boolean _verbose = false;   // allow more than one error per field (across all rows)
    int _maxRowErrors = 1;

    public DataIteratorContext()
    {
        _errors = new BatchValidationException();
    }

    public DataIteratorContext(BatchValidationException errors)
    {
        _errors = errors;
    }

    public boolean isForImport()
    {
        return _forImport;
    }

    public void setForImport(boolean forImport)
    {
        _forImport = forImport;
    }

    public boolean isFailFast()
    {
        return _failFast;
    }

    public void setFailFast(boolean failFast)
    {
        _failFast = failFast;
        if (!_failFast && _maxRowErrors == 1)
            _maxRowErrors = 1000;
    }

    public int getMaxRowErrors()
    {
        return _maxRowErrors;
    }

    public void setMaxRowErrors(int maxRowErrors)
    {
        _maxRowErrors = maxRowErrors;
    }

    public boolean isVerbose()
    {
        return _verbose;
    }

    public void setVerbose(boolean verbose)
    {
        _verbose = verbose;
    }

    public BatchValidationException getErrors()
    {
        return _errors;
    }

    /** if this etl should be killed, will execute <code>throw _errors;</code> */
    public void checkShouldCancel() throws BatchValidationException
    {
        if (!_errors.hasErrors())
            return;
        if (_failFast || _errors.getRowErrors().size() > _maxRowErrors)
            throw _errors;
    }
}
