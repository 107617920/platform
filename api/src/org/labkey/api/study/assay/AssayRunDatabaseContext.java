package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Information about an assay run that has already been imported into the database.
 * User: jeckels
 * Date: Oct 7, 2011
 */
public class AssayRunDatabaseContext implements AssayRunUploadContext
{
    protected final ExpRun _run;
    protected final User _user;
    protected final ExpProtocol _protocol;
    protected final AssayProvider _provider;
    private final HttpServletRequest _request;

    public AssayRunDatabaseContext(ExpRun run, User user, HttpServletRequest request)
    {
        _run = run;
        _user = user;
        _protocol = _run.getProtocol();
        _provider = AssayService.get().getProvider(_protocol);
        _request = request;
    }

    @NotNull
    @Override
    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    @Override
    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        return getProperties(_provider.getBatchDomain(_protocol), _run.getObjectProperties());
    }

    @Override
    public Map<DomainProperty, String> getBatchProperties()
    {
        ExpExperiment batch = AssayService.get().findBatch(_run);
        if (batch == null)
        {
            return Collections.emptyMap();
        }
        return getProperties(_provider.getBatchDomain(_protocol), batch.getObjectProperties());
    }

    protected Map<DomainProperty, String> getProperties(@Nullable Domain domain, Map<String, ObjectProperty> props)
    {
        if (domain == null)
        {
            return Collections.emptyMap();
        }
        Map<DomainProperty, String> result = new HashMap<DomainProperty, String>();
        for (DomainProperty dp : domain.getProperties())
        {
            ObjectProperty op = props.get(dp.getName());
            if (op != null && op.getPropertyId() == dp.getPropertyId())
            {
                result.put(dp, ConvertUtils.convert(op.getObjectValue()));
            }
        }
        return result;
    }

    @Override
    public String getComments()
    {
        return _run.getComments();
    }

    @Override
    public String getName()
    {
        return _run.getName();
    }

    @Override
    public User getUser()
    {
        return _user;
    }

    @Override
    public Container getContainer()
    {
        return _run.getContainer();
    }

    @Override
    public HttpServletRequest getRequest()
    {
        return _request;
    }

    @Override
    public ActionURL getActionURL()
    {
        return null;
    }

    @NotNull
    @Override
    public Map<String, File> getUploadedData() throws IOException, ExperimentException
    {
        Map<String, File> result = new HashMap<String, File>();
        for (ExpData data : _run.getOutputDatas(_provider.getDataType()))
        {
            File f = data.getFile();
            if (f == null || !NetworkDrive.exists(f))
            {
                throw new ExperimentException("Data file " + data.getName() + " is no longer available on the server's file system");
            }
            result.put(f.getName(), f);
        }
        return result;
    }

    @Override
    public AssayProvider getProvider()
    {
        return _provider;
    }

    @Override
    public Map<DomainProperty, Object> getDefaultValues(Domain domain, String scope) throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<DomainProperty, Object> getDefaultValues(Domain domain) throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveDefaultValues(Map<DomainProperty, String> values, String scope) throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveDefaultBatchValues() throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveDefaultRunValues() throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearDefaultValues(Domain domain) throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTargetStudy()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public TransformResult getTransformResult()
    {
        return DefaultTransformResult.createEmptyResult();
    }
}
