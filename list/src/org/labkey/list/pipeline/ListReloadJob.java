package org.labkey.list.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.list.model.ListImportContext;
import org.labkey.list.model.ListImporter;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class ListReloadJob extends PipelineJob
{

    private File _dataFile;
    private ListImportContext _importContext;

    @JsonCreator
    protected ListReloadJob(@JsonProperty("_dataFile") File dataFile, @JsonProperty("_importContext") ListImportContext importContext)
    {
        _dataFile = dataFile;
        _importContext = importContext;
    }

    public ListReloadJob(ViewBackgroundInfo info, @NotNull PipeRoot root, File dataFile, File logFile, @NotNull ListImportContext importContext)
    {
        super(null, info, root);
        _dataFile = dataFile;
        _importContext = importContext;
        setLogFile(logFile);
    }

    @Override
    public String getDescription()
    {
        return "Reloading list";
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public void run()
    {
        setStatus("RELOADING", "Job started at: " + DateUtil.nowISO());
        ListImporter importer = new ListImporter(_importContext);

        getLogger().info("Loading " + _dataFile.getName());

        // Issue 35420: Filewatcher data reload fails when the audit log attempts to record lookup-validation query on row insertion
        // CONSIDER: removing these if ComplianceQueryLoggingProfilerListener can ignore passed in user/container and get them somewhere else (like QueryLogging)
        // See 33016 for more information
        QueryService.get().setEnvironment(QueryService.Environment.USER, getInfo().getUser());
        QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, getInfo().getContainer());

        List<String> errors = new LinkedList<>();
        try
        {
            if (!importer.processSingle(new FileSystemFile(_dataFile.getParentFile()), _dataFile.getName(), getPipeRoot().getContainer(), getInfo().getUser(), errors, getLogger()))
            {
                error("Job failed.");
            }
        }
        catch (Exception e)
        {
            error("Job failed: " + e.getMessage());
        }

        for (String error : errors)
            getLogger().error(error);

        getLogger().info("Done importing " + getDescription());
    }
}
