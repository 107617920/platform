/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.qc.*;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.ValidationException;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.util.PageFlowUtil;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.Bindings;
import javax.script.ScriptContext;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 22, 2009
 */
public class DefaultDataTransformer implements DataTransformer, DataValidator
{
    public static final String RUN_INFO_REPLACEMENT = "runInfo";
    public static final String SRC_DIR_REPLACEMENT = "srcDirectory";
    public static final String R_JESSIONID_REPLACEMENT = "rLabkeySessionId";

    public void validate(AssayRunUploadContext context, ExpRun run) throws ValidationException
    {
        boolean isDefault = isDefault(context.getProtocol());
        for (File scriptFile : context.getProvider().getValidationAndAnalysisScripts(context.getProtocol(), AssayProvider.Scope.ALL, AssayProvider.ScriptType.VALIDATION))
        {
            // read the contents of the script file
            if (scriptFile.exists())
            {
                BufferedReader br = null;
                StringBuffer sb = new StringBuffer();
                try {
                    br = new BufferedReader(new FileReader(scriptFile));
                    String l;
                    while ((l = br.readLine()) != null)
                        sb.append(l).append('\n');
                }
                catch (Exception e)
                {
                    throw new ValidationException(e.getMessage());
                }
                finally
                {
                    if (br != null)
                        try {br.close();} catch(IOException ioe) {}
                }

                ScriptEngine engine = ServiceRegistry.get().getService(ScriptEngineManager.class).getEngineByExtension(FileUtil.getExtension(scriptFile));
                if (engine != null)
                {
                    File scriptDir = getScriptDir(context.getProtocol(), scriptFile, isDefault);
                    try
                    {
                        DataExchangeHandler dataHandler = context.getProvider().createDataExchangeHandler();
                        if (dataHandler != null)
                        {
                            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                            String script = sb.toString();
                            File runInfo = dataHandler.createValidationRunInfo(context, run, scriptDir);

                            bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, scriptDir.getAbsolutePath());
                            bindings.put(ExternalScriptEngine.SCRIPT_PATH, scriptFile.getAbsolutePath());

                            Map<String, String> paramMap = new HashMap<String, String>();

                            paramMap.put(RUN_INFO_REPLACEMENT, runInfo.getAbsolutePath().replaceAll("\\\\", "/"));
                            File srcDir = scriptFile.getParentFile();
                            if (srcDir != null && srcDir.exists())
                                paramMap.put(SRC_DIR_REPLACEMENT, srcDir.getAbsolutePath().replaceAll("\\\\", "/"));
                            paramMap.put(R_JESSIONID_REPLACEMENT, getSessionInfo(context));

                            bindings.put(ExternalScriptEngine.PARAM_REPLACEMENT_MAP, paramMap);

                            Object output = engine.eval(script);

                            // process any output from the validation script
                            dataHandler.processValidationOutput(runInfo);
                        }
                    }
                    catch (ValidationException e)
                    {
                        throw e;
                    }
                    catch (Exception e)
                    {
                        throw new ValidationException(e.getMessage());
                    }
                    finally
                    {
                        if (!isDefault)
                        {
                            // clean up temp directory
                            if (FileUtil.deleteDir(scriptDir))
                            {
                                File parent = scriptDir.getParentFile();
                                if (parent != null)
                                    parent.delete();
                            }
                        }
                    }
                }
                else
                {
                    // we may just want to log an error rather than fail the upload due to an engine config problem.
                    throw new ValidationException("A script engine implementation was not found for the specified QC script. " +
                            "Check configurations in the Admin Console.");
                }
            }
            else
            {
                throw new ValidationException("The validation script: " + scriptFile.getAbsolutePath() + " configured for this Assay does not exist. Please check " +
                        "the configuration for this Assay design.");
            }
        }
    }

    public TransformResult transform(AssayRunUploadContext context, ExpRun run) throws ValidationException
    {
        boolean isDefault = isDefault(context.getProtocol());
        TransformResult result = DefaultTransformResult.createEmptyResult();
        for (File scriptFile : context.getProvider().getValidationAndAnalysisScripts(context.getProtocol(), AssayProvider.Scope.ALL, AssayProvider.ScriptType.TRANSFORM))
        {
            // read the contents of the script file
            if (scriptFile.exists())
            {
                BufferedReader br = null;
                StringBuffer sb = new StringBuffer();
                try {
                    br = new BufferedReader(new FileReader(scriptFile));
                    String l;
                    while ((l = br.readLine()) != null)
                        sb.append(l).append('\n');
                }
                catch (Exception e)
                {
                    throw new ValidationException(e.getMessage());
                }
                finally
                {
                    if (br != null)
                        try {br.close();} catch(IOException ioe) {}
                }

                ScriptEngine engine = ServiceRegistry.get().getService(ScriptEngineManager.class).getEngineByExtension(FileUtil.getExtension(scriptFile));
                if (engine != null)
                {
                    File scriptDir = getScriptDir(context.getProtocol(), scriptFile, isDefault);
                    try
                    {
                        DataExchangeHandler dataHandler = context.getProvider().createDataExchangeHandler();
                        if (dataHandler != null)
                        {
                            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                            String script = sb.toString();
                            File runInfo = dataHandler.createTransformationRunInfo(context, run, scriptDir);

                            bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, scriptDir.getAbsolutePath());
                            bindings.put(ExternalScriptEngine.SCRIPT_PATH, scriptFile.getAbsolutePath());

                            Map<String, String> paramMap = new HashMap<String, String>();

                            paramMap.put(RUN_INFO_REPLACEMENT, runInfo.getAbsolutePath().replaceAll("\\\\", "/"));
                            File srcDir = scriptFile.getParentFile();
                            if (srcDir != null && srcDir.exists())
                                paramMap.put(SRC_DIR_REPLACEMENT, srcDir.getAbsolutePath().replaceAll("\\\\", "/"));
                            paramMap.put(R_JESSIONID_REPLACEMENT, getSessionInfo(context));

                            bindings.put(ExternalScriptEngine.PARAM_REPLACEMENT_MAP, paramMap);

                            Object output = engine.eval(script);

                            File rewrittenScriptFile;
                            if (bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE) instanceof File)
                            {
                                rewrittenScriptFile = (File)bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE);
                            }
                            else
                            {
                                rewrittenScriptFile = scriptFile;
                            }

                            // process any output from the transformation script
                            result = dataHandler.processTransformationOutput(context, runInfo, run, rewrittenScriptFile);
                        }
                    }
                    catch (ValidationException e)
                    {
                        throw e;
                    }
                    catch (Exception e)
                    {
                        throw new ValidationException(e.getMessage() == null ? e.toString() : e.getMessage());
                    }
                    finally
                    {
                        // clean up temp directory
                        if (!isDefault)
                        {
                            if (FileUtil.deleteDir(scriptDir))
                            {
                                File parent = scriptDir.getParentFile();
                                if (parent != null)
                                    parent.delete();
                            }
                        }
                    }
                }
                else
                {
                    // we may just want to log an error rather than fail the upload due to an engine config problem.
                    throw new ValidationException("A script engine implementation was not found for the specified QC script. " +
                            "Check configurations in the Admin Console.");
                }
            }
            else
            {
                throw new ValidationException("The validation script: " + scriptFile.getAbsolutePath() + " configured for this Assay does not exist. Please check " +
                        "the configuration for this Assay design.");
            }
        }
        return result;
    }

    protected File getScriptDir(ExpProtocol protocol, File scriptFile, boolean isDefault)
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File tempRoot = new File(tempDir, ExternalScriptEngine.DEFAULT_WORKING_DIRECTORY);

        if (isDefault && scriptFile.exists())
        {
            tempDir = scriptFile.getParentFile();
            tempRoot = new File(tempDir, "TransformAndValidationFiles");
        }

        if (!tempRoot.exists())
            tempRoot.mkdirs();

        File tempFolder = new File(tempRoot.getAbsolutePath() + File.separator + "AssayId_" + protocol.getRowId(), String.valueOf(Thread.currentThread().getId()));
        if (!tempFolder.exists())
            tempFolder.mkdirs();

        return tempFolder;
    }

    protected boolean isDefault(ExpProtocol protocol)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider != null)
            return provider.isSaveScriptFiles(protocol);
        return false;
    }

    /**
     * Creates the session information string
     */
    private String getSessionInfo(AssayRunUploadContext context)
    {
        StringBuilder sb = new StringBuilder();
        if (context.getRequest() != null)
        {
            sb.append("labkey.sessionCookieName = \"JSESSIONID\"\n");
            sb.append("labkey.sessionCookieContents = \"");
            sb.append(PageFlowUtil.getCookieValue(context.getRequest().getCookies(), "JSESSIONID", ""));
            sb.append("\"\n");
        }
        return sb.toString();
    }
}
