/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.pipeline.api;

import common.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.RserveScriptEngine;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.LogPrintWriter;
import org.labkey.pipeline.analysis.CommandTaskImpl;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * User: kevink
 * Date: 11/14/13
 *
 * Created via SimpleTaskFactory when parsing a task xml file.
 * Execute a script file or inline script fragment.
 */
public class ScriptTaskImpl extends CommandTaskImpl
{
    public static final Logger LOG = Logger.getLogger(ScriptTaskImpl.class);

    /* package */ ScriptTaskImpl(PipelineJob job, SimpleTaskFactory factory)
    {
        super(job, factory);
    }

    /**
     * Create the replacements map that will be used by the ExternalScriptEngine
     * to generate the script before executing it.  The replaced paths will be
     * resolved to paths in the work directory.
     */
    private Map<String, String> createReplacements(ScriptEngine engine) throws IOException
    {
        Map<String, String> replacements = new HashMap<>();

        // Input paths
        for (String key : _factory.getInputPaths().keySet())
        {
            String[] inputPaths = getProcessPaths(WorkDirectory.Function.input, key);
            if (inputPaths.length == 0)
            {
                // Replace empty file token with empty string
                replacements.put(key, "");
            }
            else if (inputPaths.length == 1)
            {
                if (inputPaths[0] == null)
                    replacements.put(key, "");
                else
                    replacements.put(key, Matcher.quoteReplacement(rewritePath(engine, inputPaths[0].replaceAll("\\\\", "/"))));
            }
            else
            {
                // CONSIDER: Add replacement for each file?  ${input[0].txt}, ${input[1].txt}, ${input[*].txt}
                // NOTE: The script parser matches ${input1.txt} to the first input file which isn't the same as ${input1[1].txt} which may be the 2nd file in the set of files represented by "input1.txt"
            }
        }

        // Output paths
        for (String key : _factory.getOutputPaths().keySet())
        {
            String[] outputPaths = getProcessPaths(WorkDirectory.Function.output, key);
            if (outputPaths.length == 0)
            {
                // Replace empty file token with empty string
                replacements.put(key, "");
            }
            else if (outputPaths.length == 1)
            {
                if (outputPaths[0] == null)
                    replacements.put(key, "");
                else
                    replacements.put(key, Matcher.quoteReplacement(rewritePath(engine, outputPaths[0].replaceAll("\\\\", "/"))));
            }
            else
            {
                // CONSIDER: Add replacement for each file?  ${input[0].txt}, ${input[1].txt}, ${input[*].txt}
            }
        }

        // Job parameters
        for (Map.Entry<String, String> entry : getJob().getParameters().entrySet())
        {
            replacements.put(entry.getKey(), Matcher.quoteReplacement(entry.getValue()));
        }

        // Job info replacement
        File jobInfoFile = getJobSupport().getJobInfoFile();
        replacements.put(PipelineJob.PIPELINE_JOB_INFO_PARAM, rewritePath(engine, jobInfoFile.getAbsolutePath()));

        return replacements;
    }

    // TODO: I believe script task can only run on the webserver since the paths the ExternalScriptEngine is configured to are local to the server.
    // TODO: RServe
    // TODO: Rhino engine.  A non-ExternalScriptEngine won't use the PARAM_REPLACEMENT_MAP binding.
    // CONSIDER: Use ScriptEngineReport to generate a script prolog
    @Override
    protected boolean runCommand(RecordedAction action) throws IOException, PipelineJobException
    {
        ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);
        if (mgr == null)
            throw new PipelineJobException("Script engine manager not available");

        ScriptTaskFactory factory = (ScriptTaskFactory)_factory;
        String extension = factory._scriptExtension;
        ScriptEngine engine = mgr.getEngineByName(extension);
        if (engine == null)
            engine = mgr.getEngineByExtension(extension);
        if (engine == null)
            throw new PipelineJobException("Script engine not found: " + extension);

        try
        {
            String scriptSource = null;
            if (factory._scriptInline != null)
            {
                scriptSource = factory._scriptInline;
            }
            else if (factory._scriptPath != null)
            {
                String[] paths = getProcessPaths(WorkDirectory.Function.module, factory._scriptPath.toString());
                if (paths.length != 1 || paths[0] == null)
                    throw new PipelineJobException("Script path not found: " + factory._scriptPath);

                String path = paths[0];
                File scriptFile = new File(path);

                scriptSource = FileUtils.readFileToString(scriptFile);
            }
            else
            {
                throw new PipelineJobException("Script path or inline script required");
            }

            // Tell the script engine where the script is and the working directory.
            // ExternalScriptEngine will copy script into working dir to perform replacements.
            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

            // UNDONE: Need ability to map to more than one remote pipeline path?  For now, assume everything is under the remote's pipeline root setting
            PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(getJob().getContainer());
            bindings.put(RserveScriptEngine.PIPELINE_ROOT, pipelineRoot.getRootPath());

            // UNDONE: For now, just use script source directly instead of passing SCRIPT_PATH to the engine
//            if (scriptFile != null)
//                bindings.put(ExternalScriptEngine.SCRIPT_PATH, rewritePath(engine, scriptFile.toString()));

            bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, _wd.getDir().getPath());

            Map<String, String> replacements = createReplacements(engine);
            bindings.put(ExternalScriptEngine.PARAM_REPLACEMENT_MAP, replacements);

            // Just output the replaced script, if debug mode is set.
            if (AppProps.getInstance().isDevMode() || _factory.isPreview())
            {
                // TODO: dump replaced script, for now just dump the replacements
                getJob().header("Replacements");
                for (Map.Entry<String, String> entry : replacements.entrySet())
                    getJob().info(entry.getKey() + ": " + entry.getValue());

                if (_factory.isPreview())
                    return false;
            }

            // Script console output will be redirected to the job's log file as it is produced
            getJob().header("Executing script");
            LogPrintWriter writer = new LogPrintWriter(getJob().getLogger(), Level.INFO);
            engine.getContext().setWriter(writer);
            writer.flush();

            // Execute the script
            Object o = engine.eval(scriptSource);

            if (_factory.isPipeToOutput())
            {
                TaskPath tpOut = _factory.getOutputPaths().get(WorkDirectory.Function.output.toString());
                assert !tpOut.isSplitFiles() : "Invalid attempt to pipe output to split files.";
                File fileOutput = _wd.newWorkFile(WorkDirectory.Function.output,
                        tpOut, getJobSupport().getBaseName());
                FileUtils.write(fileOutput, String.valueOf(o), "UTF-8");
            }

            File rewrittenScriptFile = null;
            if (bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE) instanceof File)
                rewrittenScriptFile = (File)bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE);
//            else
//                rewrittenScriptFile = scriptFile;

            // TODO: process output?
            // TODO: Perhaps signal to _wd that rewrittenScriptFile is a copied input so it can be deleted

            if (rewrittenScriptFile != null)
                action.addInput(rewrittenScriptFile, "Script File"); // CONSIDER: Add replacement script instead?

            return true;
        }
        catch (ScriptException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private String rewritePath(ScriptEngine engine, String path)
    {
        if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
        {
            // HACK: relativize to working directory
            File f = new File(path);
            if (!f.isAbsolute())
            {
                f = new File(_wd.getDir(), path);
                path = f.getAbsolutePath();
            }

            RserveScriptEngine rengine = (RserveScriptEngine) engine;
            return rengine.getRemotePipelinePath(path);
        }
        else
        {
            return path;
        }
    }
}


