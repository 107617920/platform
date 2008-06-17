/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.pipeline.file;

import org.apache.log4j.Logger;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.pipeline.*;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * <code>AbstractFileAnalysisJob</code>
 */
abstract public class AbstractFileAnalysisJob extends PipelineJob implements FileAnalysisJobSupport
{
    private static Logger _log = Logger.getLogger(AbstractFileAnalysisJob.class);

    private Integer _experimentRunRowId;
    private String _protocolName;
    private String _baseName;
    private File _dirData;
    private File _dirAnalysis;
    private File _fileParameters;
    private File[] _filesInput;
    private FileType _inputType;

    private Map<String, String> _parametersDefaults;
    private Map<String, String> _parametersOverrides;

    private transient Map<String, String> _parameters;

    public AbstractFileAnalysisJob(AbstractFileAnalysisProtocol protocol,
                           String providerName,
                           ViewBackgroundInfo info,
                           String protocolName,
                           File fileParameters,
                           File filesInput[],
                           boolean append) throws SQLException, IOException
    {
        super(providerName, info);

        _filesInput = filesInput;
        _inputType = protocol.findInputType(filesInput[0]);
        _dirData = filesInput[0].getParentFile();
        _protocolName = protocolName;

        _fileParameters = fileParameters;
        _dirAnalysis = _fileParameters.getParentFile();

        // Load parameter files
        _parametersOverrides = getInputParameters().getInputParameters();

        // Check for explicitly set default parameters.  Otherwise use the default.
        String paramDefaults = _parametersOverrides.get("list path, default parameters");
        File fileDefaults;
        if (paramDefaults != null)
            fileDefaults = new File(getRootDir().toURI().resolve(paramDefaults));
        else
            fileDefaults = protocol.getFactory().getDefaultParametersFile(getRootDir());

        _parametersDefaults = getInputParameters(fileDefaults).getInputParameters();

        if (_log.isDebugEnabled())
        {
            logParameters("Defaults", fileDefaults, _parametersDefaults);
            logParameters("Overrides", fileParameters, _parametersOverrides);
        }

        _baseName = protocol.getBaseName(_filesInput.length > 1 ? _dirData : filesInput[0]);

        setLogFile(FT_LOG.newFile(_dirAnalysis, _baseName), append);
    }

    public AbstractFileAnalysisJob(AbstractFileAnalysisJob job, File fileInput)
    {
        super(job);

        // Copy some parameters from the parent job.
        _experimentRunRowId = job._experimentRunRowId;
        _protocolName = job._protocolName;
        _inputType = job._inputType;
        _dirData = job._dirData;
        _dirAnalysis = job._dirAnalysis;
        _fileParameters = job._fileParameters;
        _parameters = job._parameters;
        _parametersDefaults = job._parametersDefaults;
        _parametersOverrides = job._parametersOverrides;

        // Change parameters which are specific to the fraction job.
        _filesInput = new File[] { fileInput };
        _baseName = (_inputType == null ? fileInput.getName() : _inputType.getBaseName(fileInput));
        setLogFile(FT_LOG.newFile(_dirAnalysis, _baseName), false);
    }

    public boolean isSplittable()
    {
        return getInputFiles().length > 1;
    }

    public PipelineJob[] createSplitJobs()
    {
        if (getInputFiles().length == 1)
            return new AbstractFileAnalysisJob[] { this };

        ArrayList<AbstractFileAnalysisJob> jobs = new ArrayList<AbstractFileAnalysisJob>();
        for (File file : getInputFiles())
            jobs.add(createSingleFileJob(file));
        return jobs.toArray(new AbstractFileAnalysisJob[jobs.size()]);
    }

    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(getTaskPipelineId());
    }

    abstract public TaskId getTaskPipelineId();

    abstract public AbstractFileAnalysisJob createSingleFileJob(File file);

    public String getProtocolName()
    {
        return _protocolName;
    }

    public String getBaseName()
    {
        return _baseName;
    }

    public File getDataDirectory()
    {
        return _dirData;
    }

    public File getAnalysisDirectory()
    {
        return _dirAnalysis;
    }

    public File[] getInputFiles()
    {
        return _filesInput;
    }

    public FileType getInputType()
    {
        return _inputType;
    }

    public Integer getExperimentRunRowId()
    {
        return _experimentRunRowId;
    }

    public void setExperimentRunRowId(int rowId)
    {
        _experimentRunRowId = rowId;
    }

    public File getParametersFile()
    {
        return _fileParameters;
    }

    public Map<String, String> getParametersOverrides()
    {
        return _parametersOverrides;
    }

    public Map<String, String> getParameters()
    {
        if (_parameters == null)
        {
            _parameters = new HashMap<String, String>(_parametersDefaults);
            _parameters.putAll(_parametersOverrides);
        }

        return _parameters;
    }

    public ParamParser getInputParameters() throws IOException
    {
        return getInputParameters(_fileParameters);
    }

    public ParamParser getInputParameters(File parametersFile) throws IOException
    {
        BufferedReader inputReader = null;
        StringBuffer xmlBuffer = new StringBuffer();
        try
        {
            inputReader = new BufferedReader(new FileReader(parametersFile));
            String line;
            while ((line = inputReader.readLine()) != null)
                xmlBuffer.append(line).append("\n");
        }
        finally
        {
            if (inputReader != null)
            {
                try
                {
                    inputReader.close();
                }
                catch (IOException eio)
                {
                }
            }
        }

        ParamParser parser = createParamParser();
        parser.parse(xmlBuffer.toString());
        if (parser.getErrors() != null)
        {
            ParamParser.Error err = parser.getErrors()[0];
            if (err.getLine() == 0)
            {
                throw new IOException("Failed parsing input xml '" + parametersFile.getPath() + "'.\n" +
                        err.getMessage());
            }
            else
            {
                throw new IOException("Failed parsing input xml '" + parametersFile.getPath() + "'.\n" +
                        "Line " + err.getLine() + ": " + err.getMessage());
            }
        }
        return parser;
    }

    private void logParameters(String description, File file, Map<String, String> parameters)
    {
        _log.debug(description + " " + parameters.size() + " parameters (" + file + "):");
        for (Map.Entry<String, String> entry : new TreeMap<String, String>(parameters).entrySet())
            _log.debug(entry.getKey() + " = " + entry.getValue());
        _log.debug("");
    }

    public ParamParser createParamParser()
    {
        return PipelineJobService.get().createParamParser();
    }

    public String getDescription()
    {
        return getDataDescription(getDataDirectory(), getBaseName(), getProtocolName());
    }

    public ActionURL getStatusHref()
    {
        if (_experimentRunRowId != null)
        {
            ExpRun run = ExperimentService.get().getExpRun(_experimentRunRowId.intValue());
            if (run != null)
                return PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(run);
        }
        return null;
    }

    public static String getDataDescription(File dirData, String baseName, String protocolName)
    {
        String dataName = "";
        if (dirData != null)
        {
            dataName = dirData.getName();
            // Can't remember why we would ever need this.
            if ("xml".equals(dataName))
            {
                dirData = dirData.getParentFile();
                if (dirData != null)
                    dataName = dirData.getName();
            }
        }

        StringBuffer description = new StringBuffer(dataName);
        if (baseName != null && !baseName.equals(dataName) &&
                !"all".equals(baseName))   // For cluster
        {
            if (description.length() > 0)
                description.append("/");
            description.append(baseName);
        }
        description.append(" (").append(protocolName).append(")");
        return description.toString();
    }

/////////////////////////////////////////////////////////////////////////////
//  Experiment writing

    public String getXarPath(File f) throws IOException
    {
        return FileUtil.relativizeUnix(getAnalysisDirectory(), f);
    }

    public String getExperimentRunUniquifier() throws IOException
    {
        File fileUniquifier = getAnalysisDirectory();
        if (getInputFiles().length == 1)
            fileUniquifier = new File(fileUniquifier, getBaseName());
        String uniquifier = FileUtil.relativizeUnix(getRootDir(), fileUniquifier);
        return PageFlowUtil.encode(uniquifier).replaceAll("/", "%2F");
    }

    public String getInstanceDetailsSnippet(File f) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("                      <exp:InstanceDetails>\n");
        sb.append("                        <exp:InstanceInputs>\n");
        sb.append(getAutoFileLSID(f));
        sb.append(getAutoFileLSID(getParametersFile()));
        sb.append(getExtraDataSnippets());
        sb.append("                        </exp:InstanceInputs>\n");
        sb.append("                      </exp:InstanceDetails>\n");
        return sb.toString();
    }

    public String getAutoFileLSID(File f) throws IOException
    {
        return "                          <exp:DataLSID DataFileUrl=\"" + getXarPath(f) +
            "\">${AutoFileLSID}</exp:DataLSID>\n";
    }

    protected String getExtraDataSnippets() throws IOException
    {
        return "";
    }

    public String getStartingInputDataSnippet(File f) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("\t\t<exp:Data rdf:about=\"${AutoFileLSID}\">\n");
        sb.append("\t\t\t<exp:Name>");
        sb.append(f.getName());
        sb.append("</exp:Name>\n");
        sb.append("\t\t\t<exp:CpasType>Data</exp:CpasType>\n");
        sb.append("\t\t\t<exp:DataFileUrl>");
        sb.append(getXarPath(f));
        sb.append("</exp:DataFileUrl>\n");
        sb.append("\t\t</exp:Data>\n");
        return sb.toString();
    }
}
